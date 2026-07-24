# System Design — Database Indexing

## Part 1 — Concepts (read this first)

### Why an index speeds up reads at all
Without an index, finding rows matching a `WHERE` condition means a **full table scan** — the database reads every single row, checking each one against the condition, `O(n)` in table size no matter how selective the condition is. An index is a **separate, ordered data structure** (see B-Tree below) that maps column values directly to the row locations that hold them, so a lookup becomes "traverse a shallow tree to find the value, then jump straight to the matching rows" — close to `O(log n)` to find the starting point, instead of scanning everything. This is the entire value proposition: an index trades **extra storage and write cost** (below) for dramatically cheaper reads on the columns it covers.

### B-Tree basics
The default index structure in virtually every relational database (Postgres, MySQL/InnoDB, SQL Server) for general-purpose indexes is a **B-Tree** (technically a B+Tree in most implementations) — a balanced, sorted tree where:
- Every leaf is at the **same depth** — no lookup is ever "unlucky" and deeper than any other, unlike an unbalanced binary tree.
- Each node holds **many** keys (not just one, unlike a binary tree) and many children — this high "fan-out" is deliberately tuned to match the database's disk-page size, so one node read corresponds to one disk I/O, minimizing the number of I/O round-trips needed to reach a leaf. A table with a billion rows might still only need 3–4 B-Tree levels to reach any row.
- Leaves are **linked together in sorted order** (B+Tree specifically), which is what makes **range queries** (`WHERE salary BETWEEN 50000 AND 80000`, `ORDER BY hire_date`) efficient too, not just exact-match lookups — once the scan finds the starting leaf, it walks the linked leaves forward instead of re-searching the tree for each row.

This is also why a B-Tree index only helps a query whose condition is **sargable** — a predicate the index can be searched (walked) against directly. [Today's SQL doc](06-sql-practice-round.md#2-employees-hired-in-the-last-30-days) covered the concrete failure mode: wrapping the indexed column in a function (`DATEDIFF(...)`, `LOWER(email) = ...` without a matching functional index) forces the database to evaluate that function per-row, defeating the tree traversal entirely and falling back to a full scan even though an index exists on the column.

### Clustered vs Non-clustered Index
- **Clustered index** — the table's **actual row data is physically stored in the index's order**. There can be only **one** per table (the data can only be physically sorted one way at a time) — typically the primary key by default (Postgres actually has no true clustered index by default; MySQL/InnoDB always clusters on the primary key). A lookup by the clustered key goes straight to the row — no extra step.
- **Non-clustered index** — a **separate structure** from the table's physical storage, holding indexed-column values plus a pointer (or, in InnoDB, the primary key value) back to the actual row. Looking up via a non-clustered index means: traverse the index to find the pointer, then a **second lookup** to fetch the actual row from the table (or from the clustered index, in InnoDB's case) — this second hop is called a **bookmark lookup** (or "key lookup"), and it's the reason non-clustered index lookups are inherently one step more expensive than clustered ones, even though both are still vastly cheaper than a full scan.
- A table can have many non-clustered indexes, but they all pay this extra-hop cost relative to the one clustered index.

### Composite Index
An index built on **multiple columns together**, e.g. `CREATE INDEX idx_dept_salary ON Employee (dept_id, salary)`. The **column order matters enormously** — a composite index is only searchable as a prefix, the same way a phone book sorted by (last name, first name) lets you jump straight to "Smith, John" but is useless for finding everyone named "John" regardless of last name:
- `WHERE dept_id = 3 AND salary > 50000` → uses the index fully (both columns match the index's leading prefix).
- `WHERE dept_id = 3` → uses the index (a valid prefix — just the first column).
- `WHERE salary > 50000` (no `dept_id` predicate) → **cannot** use this index at all, since `salary` isn't the index's leading column — the database would have to scan the whole index anyway, no better than not having it for this query.

**Column order rule of thumb**: put the column used for **equality** checks first, range/inequality columns after — an equality predicate narrows to a small, contiguous slice of the tree immediately, letting any trailing columns in that same index still be useful within that slice; leading with a range column instead means the "slice" is already the bulk of the table, so a trailing equality column adds little benefit.

### Covering Index
A composite index that includes **every column a specific query needs** — not just the columns in its `WHERE`/`ORDER BY`, but also everything in its `SELECT` list. When every needed column is present in the index itself, the database can answer the query **entirely from the index**, skipping the bookmark-lookup hop back to the actual table row described above — this is called an **index-only scan**, and it's meaningfully faster than even a normal non-clustered index lookup specifically because it eliminates that second hop entirely.
```sql
-- query: SELECT name, salary FROM Employee WHERE dept_id = 3;
CREATE INDEX idx_covering ON Employee (dept_id, name, salary); -- name, salary "covered" via INCLUDE/trailing columns
```
Worth the storage tradeoff specifically for **hot, narrow, frequently-run queries** (a dashboard endpoint hit constantly) — indiscriminately widening every index to "cover everything just in case" reintroduces the storage/write cost problem below at a larger scale for little real benefit on queries that aren't actually hot.

### Why indexes speed up reads but slow down writes
Every `INSERT`, `UPDATE` (of an indexed column), or `DELETE` has to keep **every index on that table** — not just the table's own row storage — up to date and correctly sorted. A table with five indexes means a single `INSERT` does five extra B-Tree insertions (each potentially triggering node splits to keep the tree balanced), on top of the one actual row write. This is a **real, direct cost**, not a rounding error on a write-heavy table — it's the entire reason indexing isn't "just add indexes everywhere" (below), and why write-heavy tables (an audit log, a high-frequency event stream) are often deliberately kept sparsely indexed, accepting slower ad-hoc reads in exchange for fast, unencumbered writes.

---

## Part 2 — Applying this to the Employee table

Schema, per [today's SQL doc](06-sql-practice-round.md):
```sql
Employee(emp_id PK, name, salary, dept_id, email, manager_id, hire_date)
```

**Why doesn't every column have an index?** Directly follows from the write-cost tradeoff above, plus two more concrete costs worth naming specifically: (1) **storage** — every index is its own B-Tree copy of (at minimum) the indexed column's values plus row pointers, which for a wide or frequently-indexed table can multiply total storage well beyond the raw table data itself; (2) **query planner confusion** — a database's query planner has to *choose* which index (if any) is most selective for a given query, and an excessive number of indexes, especially overlapping or rarely-useful ones, gives the planner more (sometimes worse) options to weigh, occasionally leading it to pick a suboptimal plan. The deciding question for any candidate index should always be **"is this column actually filtered/joined/sorted on by real, frequent queries?"** — indexing `email` (looked up on every login) is obviously worth it; indexing a column nobody ever filters on is pure cost with no offsetting benefit.

**How would you choose indexes for this Employee table specifically?**
- **`emp_id`** — already the primary key, indexed automatically (and clustered, in InnoDB) by definition; no decision needed here.
- **`email`** — unique per employee, and the natural lookup key for "find this specific person" (directory search, an admin looking up an employee to edit). Note this is the `Employee` table's own `email`, distinct from the separate `User.username` credential used for [today's login endpoint](07-mini-project-plan.md#part-2--login-endpoint-and-jwt-issuance) — the two tables serve different concerns (HR record vs. auth identity), and each has its own natural unique lookup column worth indexing for the same reason. A `UNIQUE INDEX` here does double duty: enforces the uniqueness constraint at the DB level *and* makes that lookup an `O(log n)` tree traversal instead of a full scan.
- **`dept_id`** — filtered on constantly (every "employees in department X" query, every `JOIN` against `Department`), and it's exactly this kind of **foreign-key-shaped, high-selectivity-per-department, frequently-joined** column that's a standard indexing candidate. A composite `(dept_id, salary)` index, specifically, would also directly serve [today's "highest salary per department" query](06-sql-practice-round.md#4-highest-salary-per-department-using-window-functions) and any "employees in dept X sorted/filtered by salary" query, per the composite-index column-order rule above (equality on `dept_id` first, range/sort on `salary` second).
- **`manager_id`** — indexed if the org-chart self-join from [Day 5's doc](../day5/06-sql-self-join-cte.md#self-join) runs often enough in the real application to matter; a smaller/rarely-queried table might reasonably skip this one, since every added index is a real cost, not a free safety net.
- **`hire_date`** — worth indexing specifically because of [today's "hired in the last 30 days" query](06-sql-practice-round.md#2-employees-hired-in-the-last-30-days) and any onboarding/reporting dashboard that filters or sorts by it — but only if that query pattern is genuinely a real, recurring access pattern in the application, not merely "we have a date column, dates are often indexed."
- **`salary`** alone (not composite) — a weaker candidate on its own: salary range queries are common, but rarely *without* also filtering by department in practice, which is exactly why the composite `(dept_id, salary)` index above is likely the better real-world choice than a standalone `salary` index — one index serving both the department-scoped case (its natural use) and, as a side benefit, the plain department-list ordering case, rather than paying for two separate indexes that mostly overlap in purpose.

**The general method, worth stating as the interview answer**: don't index speculatively — start from the application's actual (or realistically anticipated) query patterns, index the columns that show up in `WHERE`/`JOIN`/`ORDER BY` for **frequent, performance-sensitive** queries, prefer one well-chosen composite index over several overlapping single-column ones when queries naturally filter on more than one column together, and treat every index as a deliberate, justified write-cost tradeoff rather than a default.
