# Spring Boot — Spring MVC Request Lifecycle

> See also [../day2/03-springboot-core.md](../day2/03-springboot-core.md#part-2--what-happens-from-http-request-to-response-the-big-one) for the fuller diagram (filters, interceptors, exception handling) this file recaps and focuses down to the core question layer.

## Part 1 — Concepts (read this first)

### The full path

```
Client
  │  HTTP request (e.g. GET /employees/5)
  ▼
DispatcherServlet          <- single front controller, entry point for every Spring MVC request
  │
  ▼
Handler Mapping             <- finds which @Controller method matches this URL + HTTP method
  │
  ▼
Controller                  <- resolves method args (@PathVariable, @RequestBody, etc.),
  │                             calls into the service layer, has NO business logic itself
  ▼
Service                     <- business logic lives here (validation, orchestration,
  │                             transactions via @Transactional)
  ▼
Repository                  <- data access abstraction (Spring Data JPA, JdbcTemplate, etc.)
  │
  ▼
Database
  │
  ▼  (result flows back up the same chain)
Repository -> Service -> Controller
  │
  ▼
Response                    <- return value serialized to JSON (via HttpMessageConverter)
  │                             by DispatcherServlet, written to the HTTP response
  ▼
Client
```

### What is `DispatcherServlet`?
A single Servlet (implements the classic **Front Controller** pattern) that Spring Boot auto-registers and maps to `/` by default — literally every incoming HTTP request for the application passes through it first. It does not contain business logic; its job is pure **orchestration**:
1. Ask `HandlerMapping` which controller method matches this request's URL + HTTP method + headers.
2. Run any `HandlerInterceptor`s' `preHandle()`.
3. Ask `HandlerAdapter` to actually invoke that controller method, resolving its arguments (`@PathVariable`, `@RequestParam`, `@RequestBody` via an `HttpMessageConverter`) along the way.
4. Take the controller's return value and, for a `@RestController`, serialize it back to the response body (again via `HttpMessageConverter`, e.g. Jackson for JSON).
5. Run interceptors' `postHandle()`/`afterCompletion()`.
6. If anything threw, hand off to `@ControllerAdvice`/`@ExceptionHandler` to produce an error response instead.

Without `DispatcherServlet`, you'd need to manually map every URL to a raw `HttpServlet` yourself (this is literally what pre-Spring / plain Java EE servlet code looked like) — the front controller centralizes routing, argument binding, and response serialization into one reusable pipeline instead of duplicating that plumbing in every endpoint.

### Why use `@RestController`?
`@RestController` = `@Controller` + `@ResponseBody` applied to every method in the class. It tells Spring: "the return value of every handler method here is the response **body** itself (serialized to JSON/XML), not the logical name of a view template to render." This is the standard shape for a REST API, where every endpoint returns data, not HTML.

### Difference between `@Controller` and `@RestController`
| | `@Controller` | `@RestController` |
|---|---|---|
| Default return value meaning | Logical **view name** (e.g. `"employeeList"`) — resolved by a `ViewResolver` to a template (Thymeleaf/JSP), which is rendered as HTML | The response **body** directly (via `HttpMessageConverter`) |
| Use case | Server-rendered web pages | REST/JSON APIs |
| Getting JSON out of `@Controller` | Must add `@ResponseBody` explicitly on each method (or the whole class) | Built in — every method behaves as if `@ResponseBody` were already applied |

`@RestController` is a convenience: it's `@Controller` + `@ResponseBody` bundled as one meta-annotation, so a REST API class doesn't need to repeat `@ResponseBody` on every single method.

### How does `@RequestMapping` work?
It's the base annotation that maps an HTTP request (URL pattern + optionally method, headers, params, content-type) to a controller or a specific handler method. At startup, `RequestMappingHandlerMapping` scans all `@Controller`-annotated beans, reads their `@RequestMapping` (and shortcut variants: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` — each is just `@RequestMapping(method = ...)` pre-set) and builds an internal registry: URL pattern → handler method. At request time, `DispatcherServlet` asks this registry "which handler matches this request," and it does pattern matching (including path variables like `/employees/{id}`) to find the best match, throwing a `404` (no match) or `405` (path matches, method doesn't) if nothing fits.

```java
@RestController
@RequestMapping("/employees")           // class-level base path
public class EmployeeController {

    @GetMapping("/{id}")                // full path: GET /employees/{id}
    public Employee getById(@PathVariable Long id) {
        return employeeService.findById(id);
    }

    @PostMapping
    public Employee create(@RequestBody Employee employee) {   // JSON body -> Employee via Jackson
        return employeeService.save(employee);
    }
}
```
Class-level `@RequestMapping("/employees")` + method-level `@GetMapping("/{id}")` compose into the full path `/employees/{id}` — this is a common source of confusion when reading unfamiliar controllers: always check the class annotation for the base path first.

---

## Part 2 — Interview Questions (today's round)

**Q: Walk me through what happens when a REST request reaches your application.**
**A:** The embedded servlet container (Tomcat) accepts the TCP connection and parses the raw HTTP request, then passes it through the servlet filter chain (CORS, security filters, etc.) before it reaches `DispatcherServlet` — the single front controller for the whole app. `DispatcherServlet` asks `HandlerMapping` which controller method matches the URL/HTTP method, runs any `HandlerInterceptor.preHandle()`, then has `HandlerAdapter` invoke that method — resolving `@PathVariable`/`@RequestParam` from the URL and `@RequestBody` by deserializing the JSON body via an `HttpMessageConverter` (Jackson). The controller delegates to the service layer for business logic, which calls the repository layer for data access, which talks to the database. The result flows back up; if the controller is a `@RestController`, the returned object is serialized back to JSON by the same `HttpMessageConverter` machinery and written to the response body. Interceptors' `postHandle()`/`afterCompletion()` run, the filter chain unwinds, and Tomcat writes the HTTP response back over the socket. Anywhere in this chain, if an exception is thrown, `@ControllerAdvice`/`@ExceptionHandler` intercepts it and produces an error response instead of letting it propagate raw to the client.

**Q: Why does the controller not talk to the database directly?**
**A:** Layering: the controller's only job is HTTP concerns (parsing the request, choosing status codes, shaping the response) — mixing in persistence logic (SQL, transaction boundaries) would couple your API contract to your storage details and make the controller untestable without a real database. The service layer owns business logic and transaction boundaries (`@Transactional`); the repository layer owns data access. This separation is what lets you unit test the service layer with a mocked repository, and swap the persistence technology without touching controllers.

**Q: What's the difference between a filter and an interceptor in this pipeline?**
**A:** Filters (`javax.servlet.Filter`/`jakarta.servlet.Filter`) are part of the Servlet spec — they run **before** `DispatcherServlet` even starts routing, so they have no knowledge of which controller method will handle the request (used for cross-cutting concerns like CORS, request logging, character encoding, security). Interceptors (`HandlerInterceptor`) are Spring MVC-specific and run **inside** the `DispatcherServlet` pipeline, after a handler has already been matched — so `preHandle()` knows exactly which controller method is about to run and can inspect/short-circuit based on that (e.g. auth checks scoped to specific endpoints, or reading `@RequestMapping` metadata).
