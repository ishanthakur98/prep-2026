# Day 6 (5 Hours) — Study Guide Index

Same structure as Days 2-5: **concept teaching first**, then **interview questions with full answers** at the end of each file.

| # | Topic | File | Time |
|---|-------|------|------|
| 1 | DSA — Heaps (Priority Queue), Top K pattern | [01-dsa-heaps.md](01-dsa-heaps.md) | 90 min |
| 2 | Java — Concurrency, deeper (locks, wait/notify, concurrent collections, CAS) | [02-java-concurrency-deep.md](02-java-concurrency-deep.md) | 75 min |
| 3 | Spring Boot — Spring Security + JWT | [03-springboot-security-jwt.md](03-springboot-security-jwt.md) | 45 min |
| 4 | JavaScript — Objects & Prototypes | [04-javascript-objects-prototypes.md](04-javascript-objects-prototypes.md) | 45 min |
| 5 | React — State Management (`useMemo`/`useCallback`/`React.memo`) | [05-react-state-management.md](05-react-state-management.md) | 45 min |
| 6 | SQL — Aggregate + Window Functions (practice round) | [06-sql-aggregate-window-functions.md](06-sql-aggregate-window-functions.md) | 20 min |
| 7 | Mini Project — Update/Delete, `@ControllerAdvice`, `@Valid`, response format (plan) | [07-mini-project-plan.md](07-mini-project-plan.md) | 45 min |
| 8 | Mock Interview — Java / Spring / JS / React / DSA rapid fire | [08-mock-interview.md](08-mock-interview.md) | — |
| 9 | System Design — Caching (cache-aside, Redis) | [09-system-design-caching.md](09-system-design-caching.md) | 30 min |

## How to use this guide

For every topic:
1. Read the **Concept** section slowly — don't skip to code.
2. Solve/answer without looking, then check yourself.
3. For DSA problems, after solving ask: *Why does this work? What's the invariant? What pattern did I use?*
4. The **Interview Questions** section at the bottom of each file is the "today's interview round" simulation — treat it like the real thing before reading the answer.

## Code

Worked Java solutions for the Heaps topic (Part 1) and the Concurrency topic's coding exercises (Part 2) live in [`src/main/java/org/prep/day6/`](../../src/main/java/org/prep/day6/):
- `KthLargestInStream.java` — min-heap capped at size k (Easy)
- `KthLargestElement.java` — same pattern, fixed array instead of a stream (Medium)
- `TopKFrequentElements.java` — frequency map + min-heap-of-size-k by frequency (Medium)
- `LastStoneWeight.java` — max-heap simulation (Medium)
- `FindKClosestElements.java` — max-heap-of-size-k by distance from `x` (Medium)
- `ProducerConsumerDemo.java` — `BlockingQueue`-based producer/consumer
- `AtomicCounterDemo.java` — `AtomicInteger` thread-safe counter vs. a racy plain `int`, side by side

No code was committed for the Spring Security/JWT, JavaScript, React, or SQL topics this day — those snippets are meant to be typed out and run yourself (a scratch Spring Boot project, browser/Node console, or a local Postgres), same convention as [Day 4](../day4/README.md#code)/[Day 5](../day5/README.md#code). The Mini Project doc (`07`) is plan-only, same convention as Day 4's and Day 5's — it now maps concretely onto Phase 2/3 of [Day 5's phased build-out](../day5/07-mini-project-plan.md#part-3--phased-build-out-still-incremental--dont-skip-ahead); real Spring Boot scaffolding still hasn't started in this repo.

See also:
- [../day5/01-dsa-trees.md](../day5/01-dsa-trees.md) for the tree fundamentals (traversal, recursion over a tree structure) today's heap doc assumes as background, plus the array-vs-pointer representation contrast that makes a heap's array backing notable.
- [../day4/02-java-concurrency.md](../day4/02-java-concurrency.md) for threads, `ExecutorService`, `volatile`, `synchronized`, deadlock, and race conditions — today's concurrency doc goes one level deeper into the locking toolkit, thread communication primitives, and CAS that Day 4 didn't cover.
- [../day2/03-springboot-core.md](../day2/03-springboot-core.md) for the Servlet Filter Chain / `DispatcherServlet` ordering today's Security doc builds directly on (Spring Security's chain runs *before* `DispatcherServlet`).
- [../day4/04-javascript-fundamentals.md](../day4/04-javascript-fundamentals.md) for closures and the `this` call-site rules today's prototypes doc extends into shared-method/prototype-chain territory.
- [../day4/05-react-fundamentals.md](../day4/05-react-fundamentals.md) and [../day5/05-react-hooks.md](../day5/05-react-hooks.md) for the Employee Dashboard (now with `useEffect`-driven data loading) today's state-management doc adds a search box and render-optimization on top of.
- [../day2/04-sql-queries.md](../day2/04-sql-queries.md) and [../day4/06-sql-window-functions.md](../day4/06-sql-window-functions.md) for the `RANK`/`DENSE_RANK`/`ROW_NUMBER` groundwork and the near-identical top-N/running-total problems today's SQL doc deliberately repeats at a slightly higher N, as spaced-repetition practice.
- [../day5/07-mini-project-plan.md](../day5/07-mini-project-plan.md) for the full 13-phase target architecture today's mini-project doc (`07`) fills in Phase 2/3 of.

## This week's goal

By the end of Day 7 you should be able to explain, cold:
- **JVM architecture** from memory — class loading through GC, why Strings are immutable ([../day5/02-java-jvm-gc-string-pool.md](../day5/02-java-jvm-gc-string-pool.md)).
- **Spring request lifecycle** — Filter Chain (including Spring Security's place in it, today's addition) → `DispatcherServlet` → handler → response ([../day2/03-springboot-core.md](../day2/03-springboot-core.md), [03-springboot-security-jwt.md](03-springboot-security-jwt.md)).
- **HashMap internals** — bucket/chain structure, resizing, why it's not thread-safe, and why `ConcurrentHashMap` is faster ([../day3/02-java-hashmap-internals.md](../day3/02-java-hashmap-internals.md), [02-java-concurrency-deep.md](02-java-concurrency-deep.md)).
- **JWT authentication flow** — token structure, what happens when a request arrives, why the signature matters, and the revocation tradeoff ([03-springboot-security-jwt.md](03-springboot-security-jwt.md)).
- **Event loop** in JavaScript — call stack, microtask vs macrotask queues ([../day4/04-javascript-fundamentals.md](../day4/04-javascript-fundamentals.md#part-3--interview-questions-todays-round)).
- **React rendering lifecycle** — what triggers a re-render, the Virtual DOM diff, and now *why* a re-render was unnecessary and how `useMemo`/`useCallback`/`React.memo` actually prevent it ([../day4/05-react-fundamentals.md](../day4/05-react-fundamentals.md), [05-react-state-management.md](05-react-state-management.md)).

Practice explaining these aloud, as if teaching someone — not just recalling the answer silently. Today added the concurrency toolkit beyond the basics, real authentication mechanics, prototype-based inheritance, and the specific hooks that make React state changes cheap — Day 7 should be able to draw on all of it without re-deriving from scratch.
