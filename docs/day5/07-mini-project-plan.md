# Mini Project — Interview-Grade Employee Management System (Revised Plan)

[Day 4's plan](../day4/08-mini-project-plan.md) sketched a CRUD-first roadmap that added JWT, pagination, Docker, and Kubernetes as later, separate steps. Today revises that: the target architecture from day one is the **full interview-grade stack** — Java 21, Spring Boot 3, Postgres, Redis, Kafka, Docker, Kubernetes, GitHub Actions, JWT + RBAC, OpenAPI, unit/integration tests, and a React frontend — decided up front rather than bolted on later. This is still a **plan-only** document, same as Day 4's — no code goes into `src/` yet; the current repo (`pom.xml`, Java 17, plain JUnit) stays untouched until the first implementation slice actually begins.

## Part 0 — What changes from Day 4's plan, and why that's fine
Day 4's roadmap wasn't wrong, just sequenced differently (CRUD → auth → pagination → ... → Docker → Kubernetes → CI/CD, each layered on once the previous one worked). Deciding the *target* architecture up front instead doesn't mean building everything at once — it changes what "Phase 1" looks like from "just CRUD" to "CRUD, built against the folder structure, config layering, and test setup the final system needs," so later phases add capability without restructuring what already exists. The phased build-out in Part 3 is still strictly incremental; only the end-state being aimed at has moved.

## Part 1 — Stack, and the one blocking version note

**Version note to resolve before Phase 1 starts**: the current `pom.xml` targets **Java 17**; this plan targets **Java 21**. That bump needs to happen (`maven.compiler.source`/`target` → `21`, plus confirming the local JDK installed matches) before the Spring Boot module is scaffolded, since Spring Boot 3.2+ / virtual threads (one of Java 21's headline features, relevant if this project ever demonstrates high-concurrency request handling) assume 21 is available.

### Backend
- **Java 21** — current LTS; virtual threads (Project Loom) are the one feature worth explicitly demonstrating if the project ever adds a concurrency-heavy endpoint, plus pattern matching for `switch` and records reduce DTO/entity boilerplate versus Java 17.
- **Spring Boot 3** — REST API, layered controller → service → repository, built on [Jakarta EE namespaces](../day4/03-springboot-mvc.md) (the `jakarta.*` migration from `javax.*` that came with Spring Boot 3, worth knowing as a fact if asked "what changed in Spring Boot 3").
- **PostgreSQL** — primary data store, via Spring Data JPA (see [03-springboot-jpa-persistence-context.md](03-springboot-jpa-persistence-context.md) for the persistence-context/N+1 mechanics this all sits on).
- **Redis** — caching layer for frequently-read, infrequently-changed lookups (employee-by-id, department list), and a natural fit for JWT refresh-token/blacklist storage once auth is added.
- **Kafka** — asynchronous eventing: e.g. publish an `EmployeeCreated`/`EmployeeUpdated` event on writes, consumed by a separate (even if small/demo) consumer that maintains an audit log table — demonstrates decoupling a side-effect (audit logging) from the request path instead of doing it inline in the same transaction.
- **JWT** — stateless authentication; the API issues a signed token on login, and every subsequent request is authenticated by verifying the token's signature, no server-side session store required.
- **RBAC (Role-Based Access Control)** — at least two roles (`ADMIN`, `EMPLOYEE`, plus optionally `MANAGER`), enforced via Spring Security method/endpoint security (`@PreAuthorize("hasRole('ADMIN')")`) — e.g. only `ADMIN` can delete an employee or view salary data for others.
- **OpenAPI/Swagger** (`springdoc-openapi`) — auto-generated, browsable, and (importantly) always-accurate API documentation, generated from the actual annotated controllers rather than hand-maintained separately.
- **Unit & Integration tests** — JUnit 5 + Mockito for unit tests (service layer logic in isolation); **Testcontainers** for integration tests that run real queries against a real (containerized) Postgres instance rather than an in-memory substitute — catching the class of bug where a query works against H2 but not real Postgres.

### Frontend
- **React** (Vite, Axios, React Router, Context API) — see [../day4/05-react-fundamentals.md](../day4/05-react-fundamentals.md) and [05-react-hooks.md](05-react-hooks.md) for the fundamentals this builds on. Adds a login page (JWT stored appropriately — see the security note below), role-aware UI (hide/disable actions a `EMPLOYEE` role can't perform), and the employee list/detail views from Day 4's plan.

### Platform / delivery
- **Docker** — containerize the API, and Compose the API + Postgres + Redis + Kafka (+ Zookeeper, unless using a Zookeeper-less Kafka mode) together for one-command local dev.
- **Kubernetes** — Deployment + Service + ConfigMap/Secret manifests running the same containers built for Compose, demonstrating the same app in an orchestrated environment.
- **GitHub Actions** — CI pipeline (build, test, lint) on every push/PR; CD stage (build + push image, and eventually deploy) once the K8s target environment exists.

---

## Part 2 — Why this stack, specifically (interview narrative)

Each piece exists to demonstrate a specific, commonly-interviewed concern — the point isn't "uses many technologies," it's "each technology answers a question an interviewer would actually ask":
- **Layered Spring Boot architecture** → separation of concerns, not just "an endpoint that works."
- **Postgres + Redis together** → knowing when to hit the source of truth vs. a cache, and having a real answer for the cache-invalidation problem that comes with it (not just "we added Redis").
- **Kafka** → understanding asynchronous, decoupled architecture and eventual consistency, distinct from a synchronous REST call — a common senior-level differentiator.
- **JWT + RBAC** → stateless auth understanding (vs. session-based) *and* authorization modeling (roles/permissions), which is a different concern from authentication and often conflated by candidates who've only implemented one.
- **OpenAPI** → API-as-contract thinking; docs that can't drift from the code because they're generated from it.
- **Unit vs. integration tests, with Testcontainers specifically** → knowing the difference between "tested" and "tested against something that behaves like production" — an H2-backed test suite can pass while a Postgres-specific query (e.g. a native query using a Postgres-only function) fails in production.
- **Docker → Kubernetes → GitHub Actions** → deployment/ops literacy: containerization, orchestration, and an automated path from commit to running system, not just "code that runs on my machine."
- **React + Context API before reaching for a heavier state library** → understanding *why* a state management tool becomes necessary before reaching for one (this was already the reasoning in [Day 4's plan](../day4/08-mini-project-plan.md#part-2--why-this-stack-specifically); it's unchanged today).

---

## Part 3 — Phased build-out (still incremental — don't skip ahead)

Each phase should work end-to-end before starting the next; "interview-grade" is the destination, not the starting point for Phase 1.

1. **Project scaffold** — new Spring Boot 3 / Java 21 Maven project (`spring-initializr` with Web, Data JPA, PostgreSQL Driver, Validation, Lombok, DevTools), package structure below, `docker-compose.yml` bringing up Postgres from day one (not a locally-installed DB) so the "no install friction" property holds from the very first commit.
2. **Core CRUD** — `Employee` entity, controller/service/repository, Create / Get All / Get by ID (matching today's literal Day 5 instructions), no auth yet — but built inside the final package structure and with the Testcontainers test setup already in place, so Phase 1's tests are already integration tests against real Postgres, not thrown away later.
3. **Validation & error handling** — `@Valid` request DTOs, a global `@ControllerAdvice` mapping validation failures and not-found cases to proper HTTP status codes (see [09-rest-api-best-practices.md](09-rest-api-best-practices.md) for the status-code reference this leans on).
4. **OpenAPI** — add `springdoc-openapi`; every endpoint added from here on is documented as part of writing it, not retrofitted.
5. **Authentication & RBAC** — JWT login/register, `ADMIN`/`EMPLOYEE` roles, protected write endpoints (decide read-endpoint policy explicitly and be able to justify it).
6. **Redis caching** — cache employee-by-id and department lookups; implement and be able to explain the invalidation strategy (write-through on update, or TTL-based, chosen deliberately, not defaulted into).
7. **Kafka eventing** — publish `EmployeeCreated`/`EmployeeUpdated`/`EmployeeDeleted` events; a consumer (can live in the same app initially) writes them to an audit log table, demonstrating the decoupling even before a second physical service exists.
8. **Pagination, search, sorting** — `Pageable` list endpoints; query-param-driven filtering (Spring Data `Specification` or `@Query`); sortable columns end to end (API → frontend).
9. **React frontend** — login page, role-aware UI, employee list/detail/create views wired to the real API (replacing the mock-fetch dashboard from [05-react-hooks.md](05-react-hooks.md) with the real backend).
10. **Testing pass** — unit tests for service-layer logic (Mockito-mocked repository), integration tests for every controller endpoint against a real Testcontainers Postgres (and, once added, a way to test the Kafka producer/consumer path — an embedded/test Kafka broker).
11. **Docker Compose, full stack** — API + Postgres + Redis + Kafka all brought up with one `docker compose up`.
12. **Kubernetes manifests** — Deployment, Service, ConfigMap/Secret for the same containers.
13. **GitHub Actions CI/CD** — build + test on every push/PR; image build/push (and eventually deploy) once the K8s target exists.

## Part 4 — Package structure (for Phase 1's scaffold)

```
com.prep.employeeapi
├── controller      -- REST endpoints, request/response mapping only, no business logic
├── service          -- business logic, transaction boundaries (@Transactional)
├── repository        -- Spring Data JPA interfaces
├── entity            -- @Entity classes
├── dto                -- request/response DTOs (never expose entities directly over the API)
├── config             -- Security config, OpenAPI config, Redis/Kafka config beans
├── exception           -- custom exceptions + @ControllerAdvice global handler
├── event                -- Kafka event payload classes + producer/consumer components (added Phase 7)
├── security               -- JWT filter, token provider, RBAC annotations/config (added Phase 5)
└── util                    -- shared helpers
```
**Why DTOs from Phase 1, even before auth exists**: exposing `@Entity` classes directly over the API couples the wire format to the JPA mapping (adding a lazy-loaded association can suddenly break serialization, or leak data that shouldn't be public) — starting with DTOs from the first endpoint avoids a disruptive refactor later once real clients (the React frontend, Phase 9) depend on the response shape.

## Part 5 — What "portfolio-quality" means here (unchanged goal, bigger evidence)
By the end, this project should be able to answer, concretely, with your own code as evidence:
- "Walk me through your architecture" → layered backend, JWT + RBAC, Redis caching, Kafka eventing, containerized, React frontend.
- "How do you handle a slow endpoint?" → the Redis caching layer, and the specific invalidation strategy chosen and why.
- "How do you decouple a side effect from the main request path?" → the Kafka event + consumer for audit logging.
- "How would you deploy this?" → Dockerfile(s), Compose setup, Kubernetes manifests, and the GitHub Actions pipeline.
- "How do you know your tests actually prove anything against a real database?" → the Testcontainers-backed integration suite, and what it would have caught that an H2-backed suite wouldn't.
- "Show me a non-trivial query" → the search/sort/pagination combination (Phase 8), same reasoning as [Day 4's plan](../day4/08-mini-project-plan.md#part-4--what-portfolio-quality-means-here) already laid out.
