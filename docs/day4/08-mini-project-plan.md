# Mini Project — Employee Management API

Starting today, we build one portfolio-quality project **incrementally** over the coming weeks, rather than a series of disconnected daily exercises. Everything from here on (auth, pagination, Docker, CI/CD) layers onto this same codebase.

## Part 1 — Stack

### Backend
- **Spring Boot** — REST API, layered controller → service → repository (see [03-springboot-mvc.md](03-springboot-mvc.md) for the request lifecycle this is built on).
- **PostgreSQL** — primary data store.
- **Docker** — containerize the app and its dependencies (Postgres, Redis) for reproducible local dev.
- **Redis** — caching layer (e.g. cache frequently-read employee/department lookups).
- **JWT** — stateless authentication for the API.
- **Swagger** (springdoc-openapi) — auto-generated, browsable API documentation.

### Frontend
- **React** — component-based UI (see [05-react-fundamentals.md](05-react-fundamentals.md) for the fundamentals this builds on).
- **Vite** — dev server/build tool (fast HMR, replaces older tooling like Create React App).
- **Axios** — HTTP client for talking to the backend API.
- **React Router** — client-side routing between pages (employee list, employee detail, login).
- **Context API** — shared state across components without prop-drilling (e.g. the logged-in user, auth token).

## Part 2 — Why this stack, specifically

This isn't an arbitrary tool list — each piece exists to demonstrate a specific, commonly-interviewed concern:
- **Spring Boot + layered architecture** — shows you understand separation of concerns (today's MVC lifecycle doc), not just "make an endpoint work."
- **PostgreSQL + Redis together** — shows you understand when to hit the source of truth vs a cache, and the invalidation problem that comes with caching.
- **JWT** — shows stateless auth understanding (vs session-based), relevant to any API-driven system.
- **Docker + (later) Kubernetes + CI/CD** — shows deployment/ops literacy, not just "code that runs on my machine."
- **React + Context API (before reaching for a heavier state library)** — shows you understand *why* a state management tool is needed before reaching for one, which is a stronger signal than knowing Redux/Zustand syntax without the underlying motivation.

## Part 3 — Incremental feature roadmap

Each item builds on the last; don't skip ahead until the current layer works end-to-end.

1. **CRUD operations** — `Employee` entity, full create/read/update/delete via REST, backed by PostgreSQL through Spring Data JPA repositories.
2. **Authentication** — JWT-based login/register; protect write endpoints, leave read endpoints open (or protect everything, decide and justify it).
3. **Pagination** — list endpoints return paged results (`Pageable` in Spring Data) instead of dumping the whole table; frontend adds page controls.
4. **Search** — filter employees by name/department/etc. via query params, translated into a dynamic query (Spring Data `Specification` or a `@Query`).
5. **Sorting** — sortable columns on the frontend, mapped to `Sort` params on the backend list endpoint.
6. **Docker Compose** — one command (`docker compose up`) brings up the API, Postgres, and Redis together for local dev — no more "install Postgres locally" friction.
7. **Kubernetes deployment** — package the same containers into K8s manifests (Deployment, Service, ConfigMap/Secret for env config) — demonstrates the same app running in an orchestrated environment, not just Compose.
8. **CI/CD** — pipeline (GitHub Actions or similar) that builds, tests, and (eventually) deploys on push — closes the loop from "code" to "running system" the way a real team would operate.

## Part 4 — What "portfolio-quality" means here

By the end, this project should be able to answer, concretely and with your own code as evidence:
- "Walk me through your architecture" → layered backend + React frontend, JWT auth, Redis caching, containerized.
- "How do you handle a slow endpoint?" → point at the Redis caching layer and explain the invalidation strategy you chose.
- "How would you deploy this?" → point at the Dockerfile(s), Compose setup, and (once built) the K8s manifests and CI/CD pipeline.
- "Show me a non-trivial query" → the search/sort/pagination combination, and why you built it the way you did (dynamic query construction, index considerations from [06-sql-window-functions.md](06-sql-window-functions.md) and [../day2/04-sql-queries.md](../day2/04-sql-queries.md)).

Today's scope is just the **plan** — no code goes into `src/` yet. Implementation starts once the plan is confirmed and the first slice (CRUD) is scaffolded on a later day.
