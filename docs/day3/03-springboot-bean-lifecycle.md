# Spring Boot — Bean Lifecycle + Container Internals

> Builds on [../springboot/architecture-di-beans.md](../springboot/architecture-di-beans.md) (bean lifecycle basics, `@PostConstruct`/`@PreDestroy`, constructor vs field injection) and [../day2/03-springboot-core.md](../day2/03-springboot-core.md) (request lifecycle, autowiring resolution order, bean scopes). This file goes one level deeper: the container hierarchy (`BeanFactory` vs `ApplicationContext`) and what `SpringApplication.run()` actually does internally.

## Part 1 — Concepts (read this first)

### The lifecycle, restated as a pipeline
```
BeanDefinition registered (from @Component scan or @Bean method)
        ↓
Instantiation                  — constructor called (dependencies for constructor
        ↓                         injection resolved first, recursively)
Populate properties            — setter/field injection applied
        ↓
Aware interfaces               — BeanNameAware, BeanFactoryAware,
        ↓                         ApplicationContextAware, etc. — bean is told
        ↓                         about its own name / the container itself
BeanPostProcessor (before)     — postProcessBeforeInitialization runs for every bean
        ↓
@PostConstruct                 — (or InitializingBean.afterPropertiesSet(), or a
        ↓                         custom init-method) — bean's own init logic
BeanPostProcessor (after)      — postProcessAfterInitialization — THIS is where
        ↓                         proxies get created (@Transactional, @Async,
        ↓                         @Cacheable all wrap the bean here)
Bean ready                     — fully constructed, injected, initialized, possibly
        ↓                         proxied — available for use
        ⋮                        (app runs)
@PreDestroy                    — on container shutdown (singleton scope only —
                                   see the day2 doc for why prototype beans don't
                                   get this callback automatically)
```

**The one detail worth memorizing beyond the diagram**: the object your code receives when it's `@Autowired` is often **not** the raw bean produced by `@PostConstruct` — it's the object handed back by the *last* `BeanPostProcessor.postProcessAfterInitialization` call. If any post-processor swaps in a proxy (e.g. Spring AOP's proxy for `@Transactional`), everyone downstream gets the proxy, not the original instance. This is *why* calling a `@Transactional` method from another method **within the same class** doesn't get transactional behavior — that call goes through `this`, the raw object, bypassing the proxy entirely (a very common real-world gotcha and a very common interview follow-up).

### `BeanFactory`
The **root interface** of Spring's IoC container — the minimal contract: `getBean()`, bean definition storage, dependency resolution. It's intentionally bare-bones and **lazy**: beans are only created the first time they're requested (`getBean()`), not at startup. Almost nobody uses `BeanFactory` directly in application code today — it exists as the foundational abstraction that `ApplicationContext` builds on.

### `ApplicationContext`
A **sub-interface of `BeanFactory`** that adds everything a real application needs on top of the bare container contract:
- **Eager singleton instantiation** — by default, all singleton beans are created at *startup*, not on first use (fail-fast: a misconfigured bean breaks the app at boot, not three days later on some rarely-hit code path).
- Event publishing (`ApplicationEventPublisher`, `@EventListener`).
- Internationalization (`MessageSource`).
- Environment/property abstraction (`Environment`, `@Value`, profiles).
- AOP integration (auto-registers `BeanPostProcessor`s that create proxies for `@Transactional`, `@Async`, etc.).
- Convenient access to resources (`ResourceLoader`).

**Rule of thumb for the interview**: `BeanFactory` = the raw DI engine (lazy, minimal). `ApplicationContext` = `BeanFactory` + eager singleton init + enterprise features (events, AOP, i18n, environment). Every Spring Boot app uses an `ApplicationContext` (specifically `AnnotationConfigServletWebServerApplicationContext` for a typical web app) — you'd only reach for a raw `BeanFactory` in something extremely resource-constrained.

### Singleton Registry
`ApplicationContext` implements `SingletonBeanRegistry` (via `AbstractBeanFactory`), which is the actual **cache** — a `ConcurrentHashMap<String, Object>` internally (`singletonObjects`) — that guarantees "one instance per bean name per container" for singleton-scoped beans. `getBean("foo")` first checks this registry; only on a cache miss does the container go through the full instantiation pipeline above, then stores the result back into the registry before returning it. This is also the mechanism that resolves **circular dependencies** for singletons: the registry actually has three tiers (`singletonObjects`, `earlySingletonObjects`, `singletonFactories`) — when bean A needs bean B which needs bean A back, the container publishes a not-yet-fully-initialized "early reference" to A into `earlySingletonObjects` so B can grab it, breaking the cycle. This only works for **setter/field injection**; **constructor injection circular dependencies cannot be resolved this way** and fail fast at startup with `BeanCurrentlyInCreationException` — because there's no way to hand out a reference to an object whose constructor hasn't returned yet.

---

## Part 2 — What `SpringApplication.run()` actually does

```
SpringApplication.run(MyApp.class, args)
        ↓
1. Determine application type (SERVLET / REACTIVE / NONE) from classpath contents
        ↓
2. Load & run ApplicationContextInitializers and prepare the Environment
   (property sources: application.properties/yml, env vars, command-line args,
   profile-specific overrides — merged in a defined precedence order)
        ↓
3. Create the ApplicationContext instance
   (AnnotationConfigServletWebServerApplicationContext for a typical web app)
        ↓
4. "Prepare" the context: register the primary @SpringBootApplication class,
   attach the Environment, run ApplicationContextInitializers
        ↓
5. refresh() — THE core method. This is where the real work happens:
     a. Invoke BeanFactoryPostProcessors (incl. @Configuration class processing —
        this is where @ComponentScan actually runs and BeanDefinitions get
        registered for every scanned class, and where auto-configuration
        classes from META-INF/.../AutoConfiguration.imports get evaluated
        against @Conditional checks)
     b. Register BeanPostProcessors (found by type, sorted by @Order/Ordered)
     c. Initialize MessageSource, ApplicationEventMulticaster
     d. Instantiate ALL remaining singleton beans eagerly (this is where the
        Part 1 lifecycle pipeline runs, once per bean, in dependency order)
     e. Start the embedded web server (Tomcat/Jetty/Undertow) as part of
        onRefresh() for a web application context — but note: the server
        starts listening only AFTER all singleton beans above are already
        constructed, so no HTTP request can ever arrive at a half-initialized bean
     f. Publish ContextRefreshedEvent
        ↓
6. Run any ApplicationRunner / CommandLineRunner beans
        ↓
7. Publish ApplicationReadyEvent — app is up
```

**Key things to say out loud in an interview** (this is what separates "I read the diagram once" from real understanding):
- `@ComponentScan` doesn't run at class-load time or magically — it's literally one step inside `refresh()`, executed by a specific `BeanFactoryPostProcessor` (`ConfigurationClassPostProcessor`) that parses `@Configuration` classes and registers `BeanDefinition`s for everything it finds.
- Auto-configuration (`spring-boot-autoconfigure`) isn't special magic either — it's just more `@Configuration` classes, conditionally included via `@ConditionalOnClass`/`@ConditionalOnMissingBean`/etc., evaluated during that same phase. "Why did Spring Boot create a `DataSource` for me?" → because a JDBC driver was on the classpath and no `DataSource` bean already existed, so `DataSourceAutoConfiguration`'s conditions passed.
- The **embedded server starts after** singleton beans are constructed, specifically so the app never accepts traffic into a container that isn't fully wired — this is why a `@PostConstruct` that throws prevents the app from ever binding to its port at all (fail-fast, not "starts serving broken responses").
- `BeanFactoryPostProcessor` runs **before** any bean is instantiated (it operates on `BeanDefinition` metadata) — `BeanPostProcessor` runs **around each bean's instantiation** (before/after init). Confusing these two is a very common mistake; the "Factory" one is a metadata-time hook, the other is an instance-time hook.

---

## Part 3 — Interview Questions (today's round)

**Q: How does Spring create beans, end to end?**

**A:** Starting point is a `BeanDefinition` — metadata (class, scope, constructor args, property values) registered either via component scanning (`@Component`/`@Service`/`@Repository` found by `@ComponentScan`) or explicit `@Bean` methods in `@Configuration` classes, all resolved during `refresh()`'s `BeanFactoryPostProcessor` phase before any actual object exists. For each singleton `BeanDefinition`, the container: resolves constructor dependencies (recursively creating or fetching them from the singleton registry first), instantiates the object via reflection, populates remaining properties (setter/field injection), invokes `Aware` callbacks, runs `BeanPostProcessor.postProcessBeforeInitialization`, invokes `@PostConstruct`, runs `BeanPostProcessor.postProcessAfterInitialization` (where AOP proxies get woven in if needed), then stores the finished object in the singleton registry (`ConcurrentHashMap`) keyed by bean name — so future `getBean()` calls for that name are a cache hit, not a re-creation.

**Q: What happens internally after the application starts (i.e., after `main()` returns from `SpringApplication.run()`)?**

**A:** By the time `run()` returns, `refresh()` has already completed — meaning every singleton bean in the context has been fully instantiated, wired, and initialized, `BeanFactoryPostProcessor`s and `BeanPostProcessor`s have all run, auto-configuration has been evaluated and applied, and (for a web app) the embedded servlet container is already listening on its port. Any `CommandLineRunner`/`ApplicationRunner` beans then execute, and `ApplicationReadyEvent` is published — any listener for that event (health checks, startup logging, cache warming) fires at this point. From here the app is steady-state: the `ApplicationContext`'s singleton registry just serves already-built beans for the lifetime of the process, until `context.close()` triggers `@PreDestroy` callbacks on shutdown.

**Q: Difference between `ApplicationContext` and `BeanFactory`?**

**A:** `BeanFactory` is the root DI contract — lazy bean creation (only on `getBean()`), no built-in events/AOP/i18n. `ApplicationContext` extends it and is what every real Spring Boot app actually uses: it **eagerly** instantiates all singletons at startup (fail-fast configuration errors), and layers on event publishing, environment/property abstraction, message source (i18n), and automatic `BeanPostProcessor` registration that powers AOP features like `@Transactional`. In practice, "the Spring container" in conversation almost always means `ApplicationContext`; `BeanFactory` comes up mainly as the theoretical foundation, in an interview, or in extremely memory-constrained environments where eager initialization of everything isn't affordable.

**Q: Why doesn't Spring use `new` everywhere?**

**A:** Because `new Foo()` hard-codes both *which* implementation is used and *when/how* it's constructed, directly inside the calling class — that's tight coupling, and it means every class is responsible for knowing how to build its own entire dependency graph. Spring's IoC container inverts that: your class just declares *what* it needs (a constructor parameter of type `PaymentGateway`), and the container — which already knows how to build the whole graph, resolve scopes, apply proxies, and order initialization — decides *how* and *when* to satisfy it. This is what makes dependencies swappable (test doubles, different implementations per profile), lets the container control singleton lifecycle and proxying (impossible if you `new` an object yourself — you'd get the raw object, no AOP), and centralizes configuration instead of scattering `new` calls with hard-coded concrete types throughout the codebase.
