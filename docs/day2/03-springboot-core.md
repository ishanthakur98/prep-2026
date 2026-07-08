# Spring Boot — Core Concepts + Request Lifecycle

> See also the deeper writeup: [../springboot/architecture-di-beans.md](../springboot/architecture-di-beans.md) for full detail on architecture, DI, bean lifecycle, and stereotype annotations. This file recaps those briefly and focuses on scope/autowiring + the end-to-end request flow.

## Part 1 — Concepts (read this first)

### IoC (Inversion of Control)
Normally, your code controls object creation (`new Foo()`). With IoC, control is inverted: a **container** (the Spring `ApplicationContext`) creates, configures, and wires objects for you. Your class just declares *what* it needs (via constructor parameters, typically); it never decides *how* to obtain it.

### Dependency Injection (DI)
The mechanism IoC uses to satisfy those declared needs — the container looks up or creates the required bean and passes it in (constructor, setter, or field). See the [architecture doc](../springboot/architecture-di-beans.md#2-dependency-injection-di) for full examples.

### Bean Scope
Defines **how many instances** of a bean the container creates and how long they live:
| Scope | Instances | Typical use |
|---|---|---|
| `singleton` (default) | One per container | Stateless services, repositories |
| `prototype` | New instance every injection/lookup | Stateful, non-thread-safe objects |
| `request` | One per HTTP request (web apps only) | Request-scoped data |
| `session` | One per HTTP session | User session state |

### Prototype vs Singleton
- **Singleton**: created once at container startup (eagerly, by default), cached, and the *same instance* is returned/injected everywhere. Full bean lifecycle (including `@PreDestroy`) is managed by the container.
- **Prototype**: a **new instance** is created every time it's requested from the container. The container hands it off after construction and initialization (`@PostConstruct` still runs) — but does **not** track it afterward, so `@PreDestroy` is **never called automatically**; the client code owns cleanup.
- **Gotcha**: injecting a `prototype` bean into a `singleton` naively (as a normal field) only resolves it *once*, at singleton creation — you'd keep reusing the same "prototype" instance forever. Fix with `ObjectProvider<T>` / `@Lookup` method injection to get a fresh instance on each call.

### Autowiring
Spring's mechanism for automatically resolving `@Autowired` dependencies:
1. **By type** first — if exactly one bean matches the required type, inject it.
2. If **multiple candidates** match, resolve by **bean name** (matching the field/parameter name), or explicitly via `@Qualifier("beanName")`.
3. `@Primary` on a bean definition makes it the default tie-breaker when multiple candidates exist and no qualifier is given.
4. If no candidate is found and the dependency isn't marked optional, throws `NoSuchBeanDefinitionException` at startup — fail-fast.

### Bean Lifecycle
Recap (full detail in the [architecture doc](../springboot/architecture-di-beans.md#3-bean-lifecycle)): instantiate → populate dependencies → aware callbacks → `BeanPostProcessor` (before) → `@PostConstruct` → `BeanPostProcessor` (after, proxies applied here) → bean ready → `@PreDestroy` on shutdown.

---

## Part 2 — What happens from HTTP request to response (the big one)

```
Client
  │  HTTP request
  ▼
Embedded Servlet Container (Tomcat)
  │  accepts the socket connection, parses the HTTP request
  ▼
Servlet Filter Chain
  │  e.g. CharacterEncodingFilter, CORS filter, Spring Security filter chain
  │  (each filter can inspect/reject/modify the request before it reaches the servlet)
  ▼
DispatcherServlet   ← the single front controller for all Spring MVC requests
  │
  ├─► 1. HandlerMapping
  │       Finds which controller method matches this URL + HTTP method + headers
  │       (RequestMappingHandlerMapping looks up @RequestMapping/@GetMapping etc.)
  │
  ├─► 2. HandlerInterceptor.preHandle()
  │       Runs any registered interceptors before the controller (auth checks, logging)
  │
  ├─► 3. HandlerAdapter invokes the controller method
  │       Arguments are resolved by HandlerMethodArgumentResolvers:
  │         @PathVariable, @RequestParam  → parsed from URL
  │         @RequestBody                  → deserialized from JSON via an
  │                                          HttpMessageConverter (Jackson's
  │                                          MappingJackson2HttpMessageConverter)
  │
  ├─► 4. Controller delegates to Service layer → Repository layer → Database
  │       (business logic executes here; this is "your code")
  │
  ├─► 5. Return value handling
  │       If @ResponseBody / @RestController: the returned object is serialized
  │       back to JSON by the same HttpMessageConverter machinery, written to the
  │       response body, with the response's Content-Type set (e.g. application/json)
  │
  ├─► 6. HandlerInterceptor.postHandle() then afterCompletion()
  │       Post-processing/cleanup/logging after the handler ran
  │
  └─► 7. If an exception was thrown anywhere above:
          @ExceptionHandler / @ControllerAdvice intercepts it and produces an
          error response instead (e.g. mapping a custom exception to HTTP 404)
  │
  ▼
Filter chain unwinds (in reverse)
  ▼
Tomcat writes the HTTP response back over the socket
  ▼
Client receives response
```

**Key things to say out loud in an interview** (this is what separates a "textbook" answer from one that shows real understanding):
- `DispatcherServlet` is a single **Front Controller** — every request funnels through it; it doesn't handle business logic itself, it *delegates and orchestrates*.
- Argument resolution and response serialization both go through the same abstraction: `HttpMessageConverter`. This is *why* switching `@RequestBody`/response types (JSON, XML, etc.) is configuration, not code change.
- Filters (Servlet spec, container-level) run **outside** Spring MVC; Interceptors (Spring-level) run **inside** it, after a handler has been matched — this is the distinction most people blur.
- Exception handling is centralized via `@ControllerAdvice`, so controllers stay free of repetitive try/catch.

---

## Part 3 — Interview Questions (today's round)

**Q: Why constructor injection?**
**A:** It makes dependencies **explicit and mandatory** — the object literally cannot be constructed without them, so there's no "half-initialized" state. It enables `final` fields (immutability), supports plain unit testing with `new Foo(mockBar)` and no Spring context at all, and it makes circular dependencies fail loudly at startup rather than being silently papered over. It's also the Spring team's own official recommendation.

**Q: Why is field injection bad?**
**A:** It hides dependencies (you must read every field to know what a class needs), prevents `final` fields, makes unit testing harder (you need reflection or a Spring context just to set a private field), and lets a bean exist momentarily with `null` dependencies before Spring populates them. It also masks circular dependencies instead of failing fast.

**Q: Can Spring manage static objects?**
**A:** No — not in the DI sense. Dependency injection assigns values to **instance** fields on beans the container creates and owns; `static` fields belong to the *class*, not to any bean instance, so `@Autowired` on a static field is not supported/reliable (Spring will not inject it through normal bean lifecycle). The common workaround — assign a static field inside a `@PostConstruct` method by copying from an injected instance field — technically works but fights the framework: it reintroduces global mutable state, breaks testability (tests can't easily swap it), and defeats the entire purpose of DI. If you find yourself doing this, it's usually a sign the design needs an instance-scoped dependency instead.

**Q: How does `@Autowired` work?**
**A:** It's processed by a `BeanPostProcessor` — specifically `AutowiredAnnotationBeanPostProcessor` — which runs during bean creation, before the bean is fully initialized. It scans the target class for `@Autowired`-annotated constructors, fields, and setter methods, then resolves each dependency by **type** against the `ApplicationContext`'s registered beans, breaking ties using `@Qualifier`/`@Primary`/field name. For constructors specifically, if there's exactly one constructor, `@Autowired` is optional (implicit since Spring 4.3).

**Q: How are beans discovered?**
**A:** Two main mechanisms:
1. **Component scanning** — `@ComponentScan` (implied by `@SpringBootApplication`) scans the specified base package(s) for classes annotated with `@Component` and its specializations (`@Service`, `@Repository`, `@Controller`), registering a `BeanDefinition` for each.
2. **Explicit `@Bean` methods** inside `@Configuration` classes — for beans you don't own the source of (third-party classes) or need custom construction logic for.
Additionally, Spring Boot's **auto-configuration** contributes conditionally-registered beans (e.g., a `DataSource`) based on classpath contents and `@Conditional` checks, listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
