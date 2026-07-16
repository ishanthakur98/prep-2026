# Day 5 (4.5–5 Hours) — Study Guide Index

Same structure as Day 2/3/4: **concept teaching first**, then **interview questions with full answers** at the end of each file.

| # | Topic | File | Time |
|---|-------|------|------|
| 1 | DSA — Trees (DFS/BFS, BST) | [01-dsa-trees.md](01-dsa-trees.md) | 90 min |
| 2 | Java — JVM Architecture, GC, String Pool | [02-java-jvm-gc-string-pool.md](02-java-jvm-gc-string-pool.md) | 75 min |
| 3 | Spring Boot — Spring Data JPA / Persistence Context | [03-springboot-jpa-persistence-context.md](03-springboot-jpa-persistence-context.md) | 45 min |
| 4 | JavaScript — Async JavaScript (event loop, Promises, async/await) | [04-javascript-async.md](04-javascript-async.md) | 45 min |
| 5 | React — Hooks (`useState`, `useEffect`) | [05-react-hooks.md](05-react-hooks.md) | 45 min |
| 6 | SQL — Self Join, CTEs, Recursive CTE | [06-sql-self-join-cte.md](06-sql-self-join-cte.md) | 20 min |
| 7 | Mini Project — Revised roadmap (interview-grade stack) | [07-mini-project-plan.md](07-mini-project-plan.md) | 45 min |
| 8 | Mock Interview — Java / Spring / JS / React rapid fire | [08-mock-interview.md](08-mock-interview.md) | — |
| 9 | Reading — REST API Design Best Practices | [09-rest-api-best-practices.md](09-rest-api-best-practices.md) | 15 min |

## How to use this guide

For every topic:
1. Read the **Concept** section slowly — don't skip to code.
2. Solve/answer without looking, then check yourself.
3. For DSA problems, after solving ask: *Why does this work? What's the invariant? What pattern did I use?*
4. The **Interview Questions** section at the bottom of each file is the "today's interview round" simulation — treat it like the real thing before reading the answer.

## Code

Worked Java solutions for the Trees topic (Part 1) and the JVM string-pool/boxing demos (Part 2) live in [`src/main/java/org/prep/day5/`](../../src/main/java/org/prep/day5/):
- `TreeNode.java` — shared binary tree node used by every solution below
- `MaximumDepth.java` — recursive and iterative (BFS level-count)
- `SameTree.java`
- `InvertBinaryTree.java`
- `LevelOrderTraversal.java`
- `ValidateBST.java` — range-based and inorder-strictly-increasing approaches
- `LowestCommonAncestorBST.java` — iterative, BST-ordering-based
- `KthSmallestInBST.java` — recursive and iterative (explicit-stack inorder)
- `StringPoolDemo.java` — string pool identity, `equals()` vs `==`, `Integer` caching

No code was added for the Spring Data JPA, JavaScript, React, or SQL topics this day — those snippets are meant to be typed out and run yourself (a scratch Spring Boot project, browser/Node console, or a local Postgres), same convention as [Day 4](../day4/README.md#code). The Mini Project doc (`07`) is plan-only, same convention as Day 4's — real Spring Boot scaffolding starts on a later day once this revised plan is confirmed.

See also:
- [../day3/01-dsa-binary-search.md](../day3/01-dsa-binary-search.md) for the "halve the search space" idea Trees' BST operations reuse in a data-structure-shaped form.
- [../day4/01-dsa-recursion-backtracking.md](../day4/01-dsa-recursion-backtracking.md) for the general recursion-tree/stack-frame background today's tree recursion and recursive-CTE sections both lean on.
- [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md) for the Heap/Stack/Metaspace/String Pool mechanics today's JVM doc builds on (Class Loader + G1 specifics + the *why* behind String immutability are what's new today).
- [../day3/03-springboot-bean-lifecycle.md](../day3/03-springboot-bean-lifecycle.md) and [../day4/03-springboot-mvc.md](../day4/03-springboot-mvc.md) for the container/bean and request-lifecycle layers today's Persistence Context doc sits below.
- [../day4/04-javascript-fundamentals.md](../day4/04-javascript-fundamentals.md) for closures/scope/`this`, which today's async doc assumes as background.
- [../day4/05-react-fundamentals.md](../day4/05-react-fundamentals.md) for the Employee Dashboard (Header/EmployeeList/EmployeeCard/Footer + hardcoded data) today's hooks doc extends with real data loading.
- [../day2/04-sql-queries.md](../day2/04-sql-queries.md) and [../day3/06-sql-window-functions.md](../day3/06-sql-window-functions.md) for the second-highest-salary/window-function groundwork today's CTE doc rewrites and extends.
- [../day4/08-mini-project-plan.md](../day4/08-mini-project-plan.md) for the original CRUD-first roadmap today's mini-project doc revises into the full interview-grade target stack.

## This week's goal

By the end of Day 7 you should be able to:
- Draw a BST and explain, cold, why inorder traversal is sorted, why local parent-child checks don't validate a BST, and why BST search/LCA are `O(h)` instead of `O(n)`.
- Explain the full JVM picture (class loading through GC) and defend *why* Strings are immutable with all four reasons, not just "because Java said so."
- Explain the Persistence Context/dirty-checking/N+1 trio well enough to debug a real slow-endpoint bug caused by any of them.
- Explain the event loop precisely enough to predict the output order of a mixed `setTimeout`/Promise snippet without running it.
- Explain what actually controls whether a `useEffect` re-runs, and diagnose a stale-closure bug from a missing dependency.

Today was the deepest single-topic day yet on JVM internals and Trees specifically — Day 6+ should start turning the revised mini-project plan ([07-mini-project-plan.md](07-mini-project-plan.md)) into an actual running Phase 1 scaffold.
