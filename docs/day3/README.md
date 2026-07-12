# Day 3 (4 Hours) — Study Guide Index

Same structure as Day 2: **concept teaching first**, then **interview questions with full answers** at the end of each file.

| # | Topic | File | Time |
|---|-------|------|------|
| 1 | DSA — Binary Search Patterns | [01-dsa-binary-search.md](01-dsa-binary-search.md) | 90 min |
| 2 | Java — HashMap Internals + ConcurrentHashMap | [02-java-hashmap-internals.md](02-java-hashmap-internals.md) | 75 min |
| 3 | Spring Boot — Bean Lifecycle + Container Internals | [03-springboot-bean-lifecycle.md](03-springboot-bean-lifecycle.md) | 45 min |
| 4 | SQL — Top-N, Running Total, Dense Rank, Above Dept Average | [04-sql-queries.md](04-sql-queries.md) | 30 min |
| 5 | LLD — SOLID Deep Dive + Notification Strategy Pattern | [05-lld-solid-strategy.md](05-lld-solid-strategy.md) | 30 min |
| 6 | Coding Task — Custom HashMap (array + linked list) | [06-coding-custom-hashmap.md](06-coding-custom-hashmap.md) | 30 min |
| 7 | Mock Interview — Java / Spring / DSA rapid fire | [07-mock-interview.md](07-mock-interview.md) | — |
| 8 | Bonus — Amazon Leadership Principles + STAR prep | [08-bonus-amazon-lp.md](08-bonus-amazon-lp.md) | 30 min |

## How to use this guide

For every topic:
1. Read the **Concept** section slowly — don't skip to code.
2. Solve/answer without looking, then check yourself.
3. For DSA problems, after solving ask: *Why does this work? What's the invariant? What pattern did I use?*
4. The **Interview Questions** section at the bottom of each file is the "today's interview round" simulation — treat it like the real thing before reading the answer.

## Code

Worked Java solutions for Part 1 and Part 6 live in [`src/main/java/org/prep/day3/`](../../src/main/java/org/prep/day3/):
- `BinarySearchBasics.java` — basic search, lower bound, upper bound
- `SearchInsertPosition.java`
- `FirstBadVersion.java`
- `SearchInRotatedSortedArray.java`
- `FindPeakElement.java`
- `KokoEatingBananas.java`
- `CustomHashMap.java` — array + linked-list bucket HashMap (no resizing), `put`/`get`/`remove`

See also:
- [../day2/01-dsa-hashmap-sliding-window.md](../day2/01-dsa-hashmap-sliding-window.md) and [../day2/02-java-collections.md](../day2/02-java-collections.md) for the HashMap bucket/collision basics this day builds on.
- [../springboot/architecture-di-beans.md](../springboot/architecture-di-beans.md) for the DI/bean-lifecycle writeup this day's Spring Boot topic builds on.

## This week's goal

By the end of Day 7 you should be able to:
- Explain **HashMap internally** for 20–25 minutes without hesitation.
- Explain **how Spring Boot starts** for 15 minutes.

That's SDE II-level depth — today (HashMap internals + bean lifecycle) is the core of getting there.
