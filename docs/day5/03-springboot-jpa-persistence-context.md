# Spring Boot — Spring Data JPA & the Persistence Context

Builds on [../day3/03-springboot-bean-lifecycle.md](../day3/03-springboot-bean-lifecycle.md) (container/bean internals) and [../day4/03-springboot-mvc.md](../day4/03-springboot-mvc.md) (request lifecycle) — today goes one layer down, into what actually happens between a `@Repository` call and a row in Postgres.

## Part 1 — Concepts (read this first)

### What is Hibernate?
**Hibernate** is the default **JPA (Jakarta Persistence API) provider** Spring Boot wires up automatically. JPA is a *specification* (a set of interfaces/annotations: `@Entity`, `EntityManager`, JPQL, etc.) — Hibernate is the concrete implementation that actually does the object-relational mapping (turning Java objects into SQL rows and back) and manages the persistence context described below. **Spring Data JPA** sits one layer above both: it's a Spring library that generates repository implementations (`findById`, `save`, custom query-method-name parsing) on top of JPA/Hibernate, so you write an interface and Spring provides the implementation at startup via a dynamic proxy.

```
Your code  →  Spring Data JPA (repository proxies)  →  JPA (spec: EntityManager, JPQL)  →  Hibernate (impl)  →  JDBC  →  Postgres
```

### Entity lifecycle
Every JPA entity instance is in exactly one of four states at any time:

| State | Meaning |
|---|---|
| **Transient** | Just created with `new`, not yet associated with any persistence context — Hibernate doesn't know it exists. |
| **Managed (Persistent)** | Associated with an active persistence context (via `save()`, `find()`/`getById()`, or a query result). Hibernate tracks every field change and will sync it to the DB. |
| **Detached** | Was managed, but the persistence context that tracked it has closed (e.g. the transaction/request ended). Changes to a detached entity are **not** tracked or persisted automatically. |
| **Removed** | Marked for deletion within an active persistence context; the `DELETE` is issued when the context flushes. |

### Persistence Context — the first-level cache
The **persistence context** is Hibernate's per-`EntityManager` (in Spring, effectively per-transaction) session: the set of all entity instances it's currently *managing*. This is also called the **first-level cache**, and it's mandatory/always-on (unlike the optional second-level cache) — you cannot disable it.

Two direct, testable consequences:
1. **Identity within a context**: fetching the same row twice by ID within the same persistence context returns the exact same Java object reference the second time, no second `SELECT` issued — Hibernate checks the context first before hitting the DB.
2. **Dirty checking**: Hibernate keeps a snapshot of each managed entity's field values as they were when loaded. At flush time (end of transaction, or an explicit/auto flush before a query that needs current data), it compares the entity's *current* field values against that snapshot — any managed entity whose fields changed gets an `UPDATE` generated **automatically**, with no explicit `save()` call needed for the update itself. This is why calling a setter on a managed entity inside a `@Transactional` method is enough to persist the change — no `repository.save(entity)` required, though calling it is harmless (see below).

### Lazy vs Eager loading
Controls **when** a related entity/collection is actually fetched from the DB, declared via `fetch = FetchType.LAZY` or `FetchType.EAGER` on `@OneToMany`/`@ManyToOne`/etc. (defaults differ: `@ManyToOne`/`@OneToOne` default to `EAGER`, `@OneToMany`/`@ManyToMany` default to `LAZY`).
- **Eager**: the association is fetched immediately, as part of the original query (typically via a `JOIN`) — simpler, but pays the cost every time even when the caller never touches that field.
- **Lazy**: the association is fetched only the first time it's actually accessed, via a Hibernate-generated proxy that triggers a separate query on first access. If that access happens **after** the persistence context/session has closed (e.g. outside the `@Transactional` method, in a controller or view layer), it throws `LazyInitializationException` — a very common real-world Spring bug, and the direct segue into the N+1 problem below.

### The N+1 query problem
Fetching a list of `N` parent entities (1 query), then lazily accessing a related collection/entity on *each* of them individually triggers **one additional query per parent** — `N` extra queries, `N + 1` total, instead of one query (or one join query) that could have fetched everything up front.
```java
List<Department> depts = departmentRepository.findAll();     // 1 query
for (Department d : depts) {
    System.out.println(d.getEmployees().size());               // N queries -- one per department, lazy-loaded
}
```
**Fixes**, in order of how they're usually reached for:
- `JOIN FETCH` in a JPQL query (`SELECT d FROM Department d JOIN FETCH d.employees`) — one query, explicitly.
- `@EntityGraph` on the repository method — declarative way to say "eager-fetch this association for this specific query," without changing the entity's default fetch type globally.
- Batch fetching (`@BatchSize` or Hibernate's `default_batch_fetch_size`) — turns `N` individual lazy-load queries into `ceil(N/batchSize)` `WHERE id IN (...)` queries; doesn't eliminate the extra round trips entirely, but collapses them.

The trap to name out loud in an interview: **`EAGER` doesn't actually fix N+1** — it just moves the problem to *every* query that touches that entity, whether or not that particular caller needed the association, and can turn one simple query into an expensive multi-join for callers who never wanted the extra data. The real fix is fetching lazily by default and being explicit (`JOIN FETCH`/`@EntityGraph`) only on the specific queries that actually need the association eagerly.

---

## Part 2 — Questions

**Q: What actually happens when you call `repository.save(entity)`?**
**A:** Depends on whether the entity has an ID already set (specifically, whether Spring Data JPA's `SimpleJpaRepository` considers it "new," which by default means the ID field is `null`):
- **New entity (no ID)**: Hibernate calls `EntityManager.persist(entity)` — the entity becomes **managed**, and (depending on the ID generation strategy) an `INSERT` may be issued immediately (`IDENTITY` strategy needs the DB to generate the ID right away) or deferred until flush (`SEQUENCE`/`TABLE` strategies can batch).
- **Existing entity (has an ID)**: Hibernate calls `EntityManager.merge(entity)` — this **copies the passed-in entity's state onto a managed entity** (fetching it first if it isn't already in the persistence context) and returns *that* managed instance, which is why `save()`'s return value, not the object you passed in, is the one you should keep using afterward.

In neither case does `save()` necessarily hit the database immediately — the actual `INSERT`/`UPDATE` SQL is typically deferred until the persistence context **flushes** (end of transaction, or automatically before a query whose results would otherwise be stale), unless the ID strategy forces immediate execution.

**Q: Difference between `save()` and `saveAndFlush()`?**
**A:** `save()` persists/merges the entity into the persistence context but leaves the actual `INSERT`/`UPDATE` SQL to be issued at the next natural flush point (usually transaction commit). `saveAndFlush()` does the same, then **immediately forces a flush** — the SQL is sent to the DB synchronously before the method returns. Reach for `saveAndFlush()` specifically when subsequent code in the *same transaction* needs to see the row's DB-generated/DB-computed state right away (e.g. a native query, or a DB trigger/default that only takes effect on actual write) — using it everywhere "just in case" defeats the batching/performance benefit of Hibernate's automatic flush scheduling.

**Q: What is the Persistence Context, in one sentence you could say out loud in an interview?**
**A:** It's Hibernate's per-transaction tracked set of managed entities — acting as a mandatory first-level cache (same ID within one context always returns the same object, no repeat `SELECT`) and as the mechanism behind automatic dirty checking (field changes on a managed entity are diffed against a loaded snapshot and turned into `UPDATE`s at flush time, without an explicit save call).

**Q: What causes the N+1 query problem, and what's the actual fix — is switching everything to `EAGER` a real fix?**
**A:** It's caused by fetching a list of parents in one query, then separately, lazily triggering one additional query per parent when a lazy association is accessed on each of them in a loop. Switching to `EAGER` is not a real fix — it just forces that same join cost onto *every* query touching the entity, including ones that never needed the association, often turning a cheap query into an expensive multi-join by default. The real fix is keeping lazy as the default and being explicit only where needed: `JOIN FETCH` in JPQL, or `@EntityGraph` on the specific repository method that actually needs the association eagerly for that one query.
