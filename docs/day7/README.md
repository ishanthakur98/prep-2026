# Day 7 (5–5.5 Hours) — Study Guide Index

Same structure as Days 2–6: **concept teaching first**, then **interview questions with full answers** at the end of each file.

| # | Topic | File | Time |
|---|-------|------|------|
| 1 | DSA — Graphs (representation, BFS/DFS, connected components, cycle detection, topo sort) | [01-dsa-graphs.md](01-dsa-graphs.md) | 90 min |
| 2 | Java — JVM Memory Leaks & Performance (leak patterns, escape analysis, JIT/HotSpot, reference types) | [02-java-jvm-memory-leaks-performance.md](02-java-jvm-memory-leaks-performance.md) | 75 min |
| 3 | Spring Boot — Transactions (`@Transactional`, propagation, isolation, proxy mechanism) | [03-springboot-transactions.md](03-springboot-transactions.md) | 60 min |
| 4 | JavaScript — Event Loop (deep dive: call stack, Web APIs, microtask/macrotask queues) | [04-javascript-event-loop.md](04-javascript-event-loop.md) | 45 min |
| 5 | React — Lifecycle (Mounting/Updating/Unmounting, cleanup, Employee Details page + Router) | [05-react-lifecycle.md](05-react-lifecycle.md) | 45 min |
| 6 | SQL — Practice Round (duplicates, date filters, Nth-highest salary, per-group max via window function) | [06-sql-practice-round.md](06-sql-practice-round.md) | 20 min |
| 7 | Mini Project — JWT auth, login endpoint, BCrypt, RBAC, secured CRUD (plan) | [07-mini-project-plan.md](07-mini-project-plan.md) | 60 min |
| 8 | Low-Level Design — Factory, Builder, Strategy (payment system) | [08-lld-design-patterns.md](08-lld-design-patterns.md) | 30 min |
| 9 | Mock Interview — Java / Spring / JS / React / DSA rapid fire | [09-mock-interview.md](09-mock-interview.md) | 30 min |
| 10 | System Design — Database Indexing (clustered/non-clustered, composite, covering, B-Tree) | [10-system-design-indexing.md](10-system-design-indexing.md) | 30 min |

## How to use this guide

For every topic:
1. Read the **Concept** section slowly — don't skip to code.
2. Solve/answer without looking, then check yourself.
3. For DSA problems, after solving ask: *Why BFS instead of DFS? Could Union-Find solve this? What's the time complexity?* — the exact questions today's instructions call out, answered per-problem in [01-dsa-graphs.md](01-dsa-graphs.md#part-2--problems).
4. The **Interview Questions** section at the bottom of each file is the "today's interview round" simulation — treat it like the real thing before reading the answer.

## Code

Worked Java solutions for the Graphs topic (Part 1) and the Java memory-model coding exercise (Part 2) live in [`src/main/java/org/prep/day7/`](../../src/main/java/org/prep/day7/):
- `FindIfPathExistsInGraph.java` — adjacency list + BFS reachability (Easy)
- `NumberOfIslands.java` — DFS flood-fill on a grid (Medium)
- `CloneGraph.java` — DFS + `HashMap<original, clone>` for cycle-safe deep copy (Medium)
- `CourseSchedule.java` — Kahn's algorithm (BFS topological sort) for directed-cycle detection (Medium)
- `NumberOfConnectedComponents.java` — Union-Find (Disjoint Set Union) with path compression + union by size (Medium)
- `ReferenceTypesDemo.java` — Strong/Weak/Soft/Phantom references side by side, with actual observed output in [the doc](02-java-jvm-memory-leaks-performance.md#part-2--coding)

The Strategy pattern's new example (payment system) lives alongside Day 4's `NotificationService` example in [`src/main/java/org/prep/designPattern/strategy/`](../../src/main/java/org/prep/designPattern/strategy/): `PaymentStrategy.java` (interface), `CreditCardPayment.java`, `UpiPayment.java`, `NetBankingPayment.java`, `PaymentService.java` — see [08-lld-design-patterns.md](08-lld-design-patterns.md#strategy-pattern) for the full walkthrough and the if/else comparison.

No code was committed for the Spring Transactions, JavaScript, React, or SQL topics this day — those snippets are meant to be typed out and run yourself (a scratch Spring Boot project, browser/Node console, or a local Postgres), same convention as [Day 4](../day4/README.md#code)/[Day 5](../day5/README.md#code)/[Day 6](../day6/README.md#code). Factory and Builder (Topic 8) are concept-only by design this time — the point is recognizing the pattern's *shape*, not another committed example; Strategy gets the code since fluency there specifically means being able to reproduce the same shape from scratch on a second, independent example. The Mini Project doc (`07`) is plan-only, same convention as prior days — it maps concretely onto **Phase 5 (Authentication & RBAC)** of [Day 5's phased build-out](../day5/07-mini-project-plan.md#part-3--phased-build-out-still-incremental--dont-skip-ahead); real Spring Boot scaffolding still hasn't started in this repo.

## See also
- [../day6/01-dsa-heaps.md](../day6/01-dsa-heaps.md) for the heap/priority-queue material today's graph doc doesn't depend on, but pairs with as the two non-tree, non-linear DSA structures covered so far.
- [../day5/02-java-jvm-gc-string-pool.md](../day5/02-java-jvm-gc-string-pool.md) for the Class Loading and GC fundamentals today's memory-leaks doc recaps briefly and builds past.
- [../day3/03-springboot-bean-lifecycle.md](../day3/03-springboot-bean-lifecycle.md#beanfactory) for the `BeanPostProcessor`/AOP-proxy mention today's transactions doc unpacks in full.
- [../day5/03-springboot-jpa-persistence-context.md](../day5/03-springboot-jpa-persistence-context.md) for the persistence context, whose lifetime today's `@Transactional` doc points out is identical to a transaction's own lifetime.
- [../day4/04-javascript-fundamentals.md](../day4/04-javascript-fundamentals.md#q-explain-the-event-loop-at-a-high-level) for the event loop's first-pass summary today's doc goes a full level deeper on, piece by piece.
- [../day5/05-react-hooks.md](../day5/05-react-hooks.md) for the `useEffect`/dependency-array mechanics today's lifecycle doc assumes as background before adding React Router and the Strict Mode double-invoke explanation.
- [../day6/06-sql-aggregate-window-functions.md](../day6/06-sql-aggregate-window-functions.md) and [../day5/06-sql-self-join-cte.md](../day5/06-sql-self-join-cte.md) for the schema and the `DENSE_RANK`/window-function groundwork today's SQL doc extends with a 3rd-highest-salary variant and a `MAX() OVER (PARTITION BY ...)` alternative.
- [../day6/03-springboot-security-jwt.md](../day6/03-springboot-security-jwt.md) for the JWT/filter-chain concepts today's mini-project doc turns into concrete `User`/`JwtProvider`/`SecurityConfig` code.
- [../../src/main/java/org/prep/designPattern/strategy/NotificationService.java](../../src/main/java/org/prep/designPattern/strategy/NotificationService.java) for Day 4's original Strategy example, the one today's payment system deliberately mirrors.

## Weekend assignment

Alongside the daily work, [07-mini-project-plan.md's Part 5](07-mini-project-plan.md#part-5--weekend-assignment-tie-in) maps this weekend's four build-out items (pagination/sorting, search by name, connecting the React frontend via Axios, Docker Compose for both services) onto Phases 8/9/11 of [Day 5's already-decided 13-phase plan](../day5/07-mini-project-plan.md#part-3--phased-build-out-still-incremental--dont-skip-ahead) — not new, undecided scope, just the next slices of the same target architecture.

## This week's goal

By the end of Day 7 you should be able to explain, cold, everything [Day 6's goal list](../day6/README.md#this-weeks-goal) already named, plus:
- **Graph traversal** — when BFS beats DFS (and when neither matters), how Union-Find offers a genuinely different tool for connectivity questions, and why cycle detection differs between directed and undirected graphs ([01-dsa-graphs.md](01-dsa-graphs.md#part-3--interview-discussion)).
- **Why a garbage-collected language still leaks memory** — static collections, unregistered listeners, uncleared `ThreadLocal`s on pooled threads, and the four reference strengths (`Strong`/`Weak`/`Soft`/`Phantom`) that sit underneath `WeakHashMap` and every leak-avoidance pattern ([02-java-jvm-memory-leaks-performance.md](02-java-jvm-memory-leaks-performance.md)).
- **`@Transactional`'s proxy mechanism** — specifically *why* private methods and self-invocation silently don't get transactional behavior, not just that they don't ([03-springboot-transactions.md](03-springboot-transactions.md#part-2--interview-questions-todays-round)).
- **The event loop, piece by piece** — call stack, Web APIs as the host environment's job, and the strict microtask-fully-drains-before-next-macrotask rule, fluently enough to predict any of today's ten snippets cold ([04-javascript-event-loop.md](04-javascript-event-loop.md)).
- **Strict Mode's double-invoke** — why `useEffect` running twice in development is a deliberate correctness check, not a bug, and the cleanup-function discipline it's designed to enforce ([05-react-lifecycle.md](05-react-lifecycle.md)).
- **Sargability** — why wrapping an indexed column in a function silently defeats the index, tying directly into *why* an index speeds up reads in the first place ([10-system-design-indexing.md](10-system-design-indexing.md), [06-sql-practice-round.md](06-sql-practice-round.md#2-employees-hired-in-the-last-30-days)).

Practice explaining these aloud, as if teaching someone — not just recalling the answer silently. Today added graphs as the last major "shape" of DSA problem, the mechanics behind two things every prior day's material referenced but never fully unpacked (`@Transactional`'s proxy, and *why* GC'd languages leak), and the first system-design topic (indexing) grounded directly in this week's own SQL problems rather than in the abstract.
