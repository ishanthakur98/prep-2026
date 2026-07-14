# Day 4 (4.5–5 Hours) — Study Guide Index

Same structure as Day 2/Day 3: **concept teaching first**, then **interview questions with full answers** at the end of each file. No source code was added under `src/` for this day — all code shown is illustrative, inline in these docs (recursion/backtracking solutions, the producer-consumer demo, and the React dashboard are all reference implementations to read and type out yourself, not committed sources).

| # | Topic | File | Time |
|---|-------|------|------|
| 1 | DSA — Recursion + Backtracking | [01-dsa-recursion-backtracking.md](01-dsa-recursion-backtracking.md) | 90 min |
| 2 | Java — Concurrency (threads, executors, volatile/synchronized, producer-consumer) | [02-java-concurrency.md](02-java-concurrency.md) | 60 min |
| 3 | Spring Boot — Spring MVC Request Lifecycle | [03-springboot-mvc.md](03-springboot-mvc.md) | 45 min |
| 4 | JavaScript — Foundations (variables, functions, scope, closures, `this`) | [04-javascript-fundamentals.md](04-javascript-fundamentals.md) | 45 min |
| 5 | React — Fundamentals + Employee Dashboard build | [05-react-fundamentals.md](05-react-fundamentals.md) | 45 min |
| 6 | SQL — Window Functions (`ROW_NUMBER`, `RANK`, `DENSE_RANK`) | [06-sql-window-functions.md](06-sql-window-functions.md) | 20 min |
| 7 | Mock Interview — Java / Spring / JS / React / DSA rapid fire | [07-mock-interview.md](07-mock-interview.md) | 20 min |
| 8 | Mini Project — Employee Management API roadmap | [08-mini-project-plan.md](08-mini-project-plan.md) | 30 min |

## How to use this guide

For every topic:
1. Read the **Concept** section slowly — don't skip to code.
2. Solve/answer without looking, then check yourself.
3. For DSA problems, after solving ask: *Why does this work? What's the decision tree at each node? What pattern did I use?*
4. The **Interview Questions** section at the bottom of each file is the "today's interview round" simulation — treat it like the real thing before reading the answer.

## Code

No files were added to `src/main/java/` for Day 4 — the Java (recursion/backtracking, producer-consumer), JavaScript, and React snippets in this day's docs are meant to be typed out and run yourself (e.g. in a scratch file, REPL, or a `jshell`/browser console) rather than committed as tracked solutions.

See also:
- [../day3/01-dsa-binary-search.md](../day3/01-dsa-binary-search.md) for the binary-search-on-answer pattern that pairs well with today's recursion/backtracking practice.
- [../day3/02-java-hashmap-internals.md](../day3/02-java-hashmap-internals.md) and [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md#stack) for the HashMap/thread-stack background today's concurrency doc builds on.
- [../day2/03-springboot-core.md](../day2/03-springboot-core.md) for the fuller Spring Boot request lifecycle diagram (filters, interceptors, exception handling) today's MVC doc recaps and narrows.
- [../day2/04-sql-queries.md](../day2/04-sql-queries.md) for the `RANK`/`DENSE_RANK`/`ROW_NUMBER` three-way comparison and the Nth-highest-salary problem today's SQL doc builds on.

## This week's goal

By the end of Day 7 you should be able to:
- Draw a backtracking recursion tree and explain the decision at each node, cold, for any of Subsets/Permutations/Combination Sum/Letter Combinations.
- Explain the full Spring MVC request lifecycle **and** a producer-consumer concurrency example without hesitation.
- Explain closures and re-render triggers well enough to debug a real React bug caused by either.

Today (recursion/backtracking + concurrency + MVC lifecycle + JS closures + React fundamentals) is the broadest single day so far — it's intentionally a survey across the whole stack before Day 5+ goes deeper on each piece and the mini project starts taking real shape.
