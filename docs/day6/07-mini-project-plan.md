# Mini Project — Update/Delete, Global Exception Handling, DTO Validation (Plan)

Still **plan-only**, same convention as [Day 4's](../day4/08-mini-project-plan.md) and [Day 5's](../day5/07-mini-project-plan.md) mini-project docs — no code goes into `src/` yet. Day 5's plan set the target architecture (Java 21, Spring Boot 3, Postgres, Redis, Kafka, JWT+RBAC, React) and a 13-phase build-out; today's instructions (Update/Delete Employee, `@ControllerAdvice`, `@Valid`, consistent response format) map directly onto **Phase 2 (Core CRUD)** and **Phase 3 (Validation & error handling)** of [that plan](../day5/07-mini-project-plan.md#part-3--phased-build-out-still-incremental--dont-skip-ahead) — worth naming explicitly, since today's instructions read as if Create/Read already exist as real code, and they don't yet in this repo. This doc describes what those two phases look like concretely, so the first real scaffolding session can implement them directly instead of re-deriving the design from scratch.

## Update Employee — `PUT /employees/{id}`
```java
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<EmployeeResponse>> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateEmployeeRequest request) {
    EmployeeResponse updated = employeeService.update(id, request);
    return ResponseEntity.ok(ApiResponse.success(updated));
}
```
- **`PUT` vs `PATCH`**: `PUT` replaces the full resource representation (the request body must carry every updatable field, even unchanged ones); `PATCH` applies a partial update (only fields present in the body change). Decide this explicitly rather than defaulting — `PUT` is simpler to implement and reason about (no "was this field omitted or explicitly nulled" ambiguity), `PATCH` is friendlier for clients that only want to change one field. Plan default: `PUT` for Phase 2, since it's the simpler contract to get the layered flow right first; `PATCH` can be added later once the base CRUD flow works.
- **Service layer responsibility**: look up the existing entity (404 via a custom `EmployeeNotFoundException` if missing — see below), apply the changes, persist, map to a response DTO. The controller never touches the entity directly (per [Day 5's DTO-from-Phase-1 rule](../day5/07-mini-project-plan.md#part-4--package-structure-for-phase-1s-scaffold)).

## Delete Employee — `DELETE /employees/{id}`
```java
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    employeeService.delete(id);
    return ResponseEntity.noContent().build(); // 204, no body
}
```
- **204 No Content**, not 200 — a successful delete has nothing meaningful to return; `ResponseEntity.noContent()` is the correct REST-idiomatic response (see [Day 5's REST best-practices doc](../day5/09-rest-api-best-practices.md) for the status-code reference this leans on).
- **Idempotency question worth being able to answer**: deleting an already-deleted (or never-existent) ID — return `404` (informative, "there was nothing to delete") is the more common choice for this project; some APIs deliberately return `204` even on a repeat delete specifically to make the *operation* idempotent (same result no matter how many times it's called) rather than reporting on resource existence. Either is defensible — the point is choosing deliberately and being able to justify it, not that one is objectively correct.

## Global exception handling — `@ControllerAdvice`
One class, centralizing every error-to-HTTP-status mapping instead of scattering try/catch blocks across every controller method:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EmployeeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", fieldErrors));
    }

    @ExceptionHandler(Exception.class) // catch-all, last resort
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
```
- **`@RestControllerAdvice`** (`@ControllerAdvice` + `@ResponseBody` combined) intercepts exceptions thrown from **any** `@RestController` in the application — the alternative (a try/catch in every controller method individually) duplicates the same mapping logic everywhere and virtually guarantees inconsistency (one method returns a different error shape than another because someone forgot to match the pattern).
- **Specific handlers before the catch-all**: Spring matches `@ExceptionHandler` methods by exception type specificity, but ordering them from specific to a final `Exception.class` catch-all makes the intent explicit and guards against ever leaking an unhandled stack trace/500 with no useful body to a client.
- **Custom exception, minimal**:
  ```java
  public class EmployeeNotFoundException extends RuntimeException {
      public EmployeeNotFoundException(Long id) {
          super("Employee not found with id: " + id);
      }
  }
  ```
  A `RuntimeException` subclass (unchecked) — thrown from the service layer, caught centrally in the advice, never requiring every calling method up the stack to declare or catch it.

## DTO validation — `@Valid`
```java
public record CreateEmployeeRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "title is required") String title,
        @Email(message = "email must be valid") String email,
        @Positive(message = "salary must be positive") BigDecimal salary,
        Long deptId
) {}
```
```java
@PostMapping
public ResponseEntity<ApiResponse<EmployeeResponse>> create(
        @Valid @RequestBody CreateEmployeeRequest request) {
    // @Valid triggers Bean Validation before this method body ever runs;
    // a failure throws MethodArgumentNotValidException, caught by the advice above
    ...
}
```
- **Where validation belongs**: on the **request DTO** (`CreateEmployeeRequest`/`UpdateEmployeeRequest`), not the entity — the entity may have fields (`id`, audit timestamps) that are never part of a create/update request payload and shouldn't carry request-shaped validation annotations; this is one more reason DTOs exist from Phase 1 rather than exposing entities directly (see [Day 5's plan](../day5/07-mini-project-plan.md#part-4--package-structure-for-phase-1s-scaffold)).
- **`@Valid` vs `@Validated`**: `@Valid` (standard Bean Validation, `jakarta.validation`) triggers validation of the annotated argument itself, including nested objects if they're also annotated; `@Validated` (Spring's own annotation) additionally enables validation *groups* (different rule sets for create vs. update, e.g.) and method-level parameter validation outside `@RequestBody`. Plan default: plain `@Valid` is sufficient until a concrete need for validation groups shows up.
- **Where the failure is actually caught**: `@Valid` failing throws `MethodArgumentNotValidException` **before the controller method body runs at all** — the `GlobalExceptionHandler.handleValidation` above is what turns that into a structured 400 response listing every failed field, rather than each controller needing to manually check a `BindingResult` argument.

## Consistent API response format
```java
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Object errors        // null on success; validation field-errors map, or null, on failure
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> error(String message, Object errors) {
        return new ApiResponse<>(false, null, message, errors);
    }
}
```
- **Why one shared envelope for every endpoint** rather than each endpoint returning its raw DTO (or raw error) directly: a client integrating against this API (the React frontend, Phase 9) can write **one** response-handling code path (`if (response.success) { use response.data } else { show response.message }`) instead of a bespoke shape per endpoint — this is the same "contract stability" argument [Day 5's plan made for OpenAPI](../day5/07-mini-project-plan.md#part-2--why-this-stack-specifically-interview-narrative) and for DTOs generally: predictable, uniform shape at the API boundary, decoupled from whatever's happening internally.
- **What stays out of the envelope**: HTTP status codes themselves (`404`, `400`, `500`) — the envelope's `success`/`message`/`errors` fields are a *complement* to the status code, not a replacement for it; a client (or monitoring tooling) should still be able to tell success/failure from the status code alone, per [Day 5's REST best-practices doc](../day5/09-rest-api-best-practices.md), with the envelope adding structured detail on top.

## What this doesn't change
This is still a design for **Phase 2/3 of Day 5's already-decided target architecture** — Java 21, layered `controller/service/repository/entity/dto/exception` packages, Postgres via Spring Data JPA, Testcontainers-backed tests from the start. Nothing here revises that; it's the concrete shape Phase 2/3 takes when the first real Spring Boot module is actually scaffolded.
