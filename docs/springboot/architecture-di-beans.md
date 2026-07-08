# Spring Boot Architecture, DI, and Bean Lifecycle

## 1. Spring Boot Architecture

Spring Boot is built as a layer on top of the Spring Framework that removes boilerplate configuration. At a high level, a typical application has these layers:

```
Client (HTTP request)
      │
      ▼
Presentation Layer      → @Controller / @RestController
      │
      ▼
Service Layer           → @Service (business logic)
      │
      ▼
Data Access Layer       → @Repository (DB access, JPA/Hibernate)
      │
      ▼
Database
```

Key architectural pieces:

- **Auto-configuration**: Spring Boot scans the classpath and automatically configures beans it thinks you need (e.g., a `DataSource` if a JDBC driver is on the classpath). Controlled via `@EnableAutoConfiguration` (included in `@SpringBootApplication`).
- **Starter dependencies**: Curated dependency bundles (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, etc.) that pull in compatible versions of related libraries.
- **Embedded server**: Tomcat/Jetty/Undertow is embedded in the jar, so the app is self-contained and runnable via `java -jar`.
- **IoC Container (ApplicationContext)**: The core of Spring — it creates, wires, and manages the lifecycle of all beans. Everything (DI, bean scanning, lifecycle callbacks) revolves around this container.
- **`@SpringBootApplication`**: A meta-annotation combining `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`.

## 2. Dependency Injection (DI)

DI is the pattern where an object's dependencies are provided ("injected") by an external container rather than the object creating them itself. This is Spring's implementation of the **Inversion of Control (IoC)** principle.

Without DI:
```java
public class OrderService {
    private PaymentGateway gateway = new StripeGateway(); // tightly coupled
}
```

With DI:
```java
@Service
public class OrderService {
    private final PaymentGateway gateway;

    public OrderService(PaymentGateway gateway) { // injected by Spring
        this.gateway = gateway;
    }
}
```

Benefits:
- Loose coupling — depend on abstractions (interfaces), not concrete implementations.
- Easier unit testing — mocks/stubs can be injected in place of real beans.
- Centralized object creation and configuration.

Spring supports three injection styles: **constructor injection**, **field injection**, and **setter injection** (see comparison below).

## 3. Bean Lifecycle

A "bean" is any object managed by the Spring `ApplicationContext`. The container controls its full lifecycle:

1. **Instantiation** — container creates the bean instance (via constructor).
2. **Populate properties** — dependencies are injected (constructor/setter/field).
3. **Aware interfaces invoked** (if implemented) — e.g., `BeanNameAware`, `ApplicationContextAware`.
4. **`BeanPostProcessor.postProcessBeforeInitialization`** — runs for all beans.
5. **`@PostConstruct`** method invoked (or `InitializingBean.afterPropertiesSet()`, or custom `init-method`).
6. **`BeanPostProcessor.postProcessAfterInitialization`** — runs after init (this is where proxies, e.g. for `@Transactional`, get created).
7. **Bean is ready** — available in the container for use.
8. **`@PreDestroy`** method invoked (or `DisposableBean.destroy()`, or custom `destroy-method`) when the context is closed.

```java
@Component
public class CacheWarmer {

    @PostConstruct
    public void init() {
        // called once bean is fully constructed and dependencies injected
        System.out.println("Warming cache...");
    }

    @PreDestroy
    public void cleanup() {
        // called just before bean is destroyed (context shutdown)
        System.out.println("Releasing cache resources...");
    }
}
```

**Bean scopes** affect how many times this lifecycle runs:
- `singleton` (default) — one instance per container, lifecycle runs once.
- `prototype` — new instance every time it's requested; `@PreDestroy` is **not** called by the container (caller must clean up manually).
- `request`, `session`, `application` — web-aware scopes.

## 4. `@Component`, `@Service`, `@Repository`

All three are specializations of the base `@Component` annotation, and all are picked up by `@ComponentScan` and registered as beans. They're functionally similar but carry semantic meaning and, in some cases, extra behavior:

| Annotation    | Purpose / Layer                    | Extra behavior |
|---------------|-------------------------------------|----------------|
| `@Component`  | Generic stereotype, any Spring-managed class | None — base annotation |
| `@Service`    | Business/service logic layer        | Purely semantic marker (no extra framework behavior), improves readability |
| `@Repository` | Data access layer (DAOs)            | Spring wraps methods with **exception translation** — converts JDBC/Hibernate-specific exceptions into Spring's unchecked `DataAccessException` hierarchy |
| `@Controller` / `@RestController` | Web layer (not asked, but part of the family) | Handles HTTP requests; `@RestController` = `@Controller` + `@ResponseBody` |

Why use the specific ones instead of always `@Component`?
- **Readability/intent** — a reviewer instantly knows the class's role.
- **Tooling** — IDEs and static analysis tools use these to enforce layering rules.
- **`@Repository`'s exception translation** is a real functional difference, not just semantic.

## 5. Constructor Injection vs Field Injection

### Field Injection
```java
@Service
public class OrderService {
    @Autowired
    private PaymentGateway gateway;
}
```

### Constructor Injection
```java
@Service
public class OrderService {
    private final PaymentGateway gateway;

    // @Autowired is optional here if there's only one constructor (Spring 4.3+)
    public OrderService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

### Comparison

| Aspect | Constructor Injection | Field Injection |
|---|---|---|
| Immutability | Fields can be `final` | Fields cannot be `final` |
| Mandatory dependencies | Enforced at compile time — object can't exist without them | Not enforced — object can be constructed in an incomplete state |
| Testability | Easy — just call `new OrderService(mockGateway)`, no Spring needed | Hard — requires reflection or a Spring context to set the field |
| Circular dependency detection | Fails fast at startup (throws `BeanCurrentlyInCreationException`) | Silently "works" (Spring can resolve it via early reference), often hiding a design smell |
| Null safety | Guarantees dependency is non-null once constructed | Bean may briefly exist with `null` dependencies before injection completes |
| Verbosity | More boilerplate (constructor, esp. with many deps — a sign to consider refactoring) | Very concise |

**Recommendation**: Prefer **constructor injection**. It's the officially recommended approach (Spring team's own guidance) because it makes dependencies explicit, supports immutability, and makes classes trivially testable in plain unit tests without Spring. Field injection is discouraged in production code — it's mainly convenient for quick demos or legacy code.

Setter injection (not explicitly asked, but worth noting) sits in between — used mainly for **optional** dependencies that can be reconfigured after construction.
