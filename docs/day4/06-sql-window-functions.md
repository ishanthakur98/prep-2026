# SQL — Window Functions

## Part 1 — Concepts (read this first)

Same schema as [../day2/04-sql-queries.md](../day2/04-sql-queries.md):
```sql
CREATE TABLE Employee (
    emp_id   INT PRIMARY KEY,
    name     VARCHAR(100),
    salary   DECIMAL(10,2),
    dept_id  INT NULL,
    email    VARCHAR(100)
);

CREATE TABLE Department (
    dept_id   INT PRIMARY KEY,
    dept_name VARCHAR(100)
);
```

A **window function** computes a value across a set of rows related to the current row (its "window"), **without collapsing rows** the way `GROUP BY` does — every input row still appears in the output, just with an extra computed column. The general shape:
```sql
function() OVER (PARTITION BY col ORDER BY col)
```
- **`PARTITION BY`** — splits rows into independent groups (like `GROUP BY`, but without merging rows); the function resets/recomputes separately within each partition. Omit it to treat the whole result set as one partition.
- **`ORDER BY`** (inside `OVER`) — defines the order rows are processed in *within* each partition, which is what ranking/running-total functions depend on.

### `ROW_NUMBER()` vs `RANK()` vs `DENSE_RANK()`
Given salaries `[500, 500, 400, 300]` ordered descending:

| Salary | `ROW_NUMBER()` | `RANK()` | `DENSE_RANK()` |
|---|---|---|---|
| 500 | 1 | 1 | 1 |
| 500 | 2 | 1 | 1 |
| 400 | 3 | 3 | 2 |
| 300 | 4 | 4 | 3 |

- **`ROW_NUMBER()`** — a unique, strictly sequential number per row within the partition, **ties broken arbitrarily** (by whatever the `ORDER BY` doesn't fully determine) — never repeats a number.
- **`RANK()`** — ties get the **same** rank, but the **next** rank **skips** ahead by the number of tied rows (`1, 1, 3` — skips `2` because two rows already claimed rank 1). Matches "Olympic ranking" intuition — two golds means no silver.
- **`DENSE_RANK()`** — ties get the same rank, and the next rank does **not** skip (`1, 1, 2`) — this is what you want for "distinct Nth value" questions (Nth highest salary, etc. — see [../day2/04-sql-queries.md](../day2/04-sql-queries.md#2-nth-highest-salary-parameterized-by-n)), since it counts *distinct* rank levels, not row count.

**Rule of thumb for picking one**: need a unique row per position regardless of ties (pagination, deduplication) → `ROW_NUMBER()`. Need "how many rows are strictly better" semantics → `RANK()`. Need "which distinct tier is this value in" → `DENSE_RANK()`.

---

## Part 2 — Problems

### 1. Top 2 highest-paid employees per department

```sql
SELECT dept_id, name, salary
FROM (
    SELECT
        e.dept_id,
        e.name,
        e.salary,
        DENSE_RANK() OVER (PARTITION BY e.dept_id ORDER BY e.salary DESC) AS salary_rank
    FROM Employee e
) ranked
WHERE salary_rank <= 2;
```
**Why this needs a window function and not just `GROUP BY` + `LIMIT`**: `GROUP BY dept_id` can only give you one aggregate row per department (e.g. `MAX(salary)`) — it can't return "the top 2 individual employee rows" per group, because grouping collapses the individual rows entirely. `PARTITION BY dept_id` keeps every row but tags each with its rank *within its department*, so filtering `salary_rank <= 2` afterward gives the actual top-2 employee rows per department, ties included (two employees tied for #1 in a department both get `salary_rank = 1`, and both survive the filter — this is why `DENSE_RANK`, not `ROW_NUMBER`, is the right choice here: with `ROW_NUMBER` a tie would arbitrarily keep one and cut the other).
**Why the subquery/CTE wrapper is required**: window function results can't be referenced directly in the same query's `WHERE` clause (`WHERE salary_rank <= 2` right after computing it in `SELECT` isn't legal — `WHERE` is evaluated before window functions are computed in SQL's logical execution order). Wrapping it in a subquery (or `WITH ranked AS (...)`) computes the ranks first, then filters on them.

### 2. Running total of salary within each department, ordered by hire order (`emp_id` as a stand-in for hire order)

```sql
SELECT
    dept_id,
    emp_id,
    name,
    salary,
    SUM(salary) OVER (
        PARTITION BY dept_id
        ORDER BY emp_id
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_total
FROM Employee
ORDER BY dept_id, emp_id;
```
**Why this works**: `SUM(...) OVER (... ORDER BY emp_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)` computes, for each row, the sum of `salary` over every row from the start of its partition up to and including itself — a classic running/cumulative total, reset independently per department via `PARTITION BY dept_id`. This is the same "aggregate without collapsing rows" idea as Problem 1, just with `SUM` instead of a ranking function.
**Note on the frame clause**: `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` is actually the **default** frame whenever an `ORDER BY` is present inside `OVER (...)` for most databases (PostgreSQL, MySQL 8+) — it's written explicitly here for clarity, but you'll often see the same query without it, relying on the default. Omitting `ORDER BY` entirely inside `OVER` instead makes the frame the *whole partition*, which would give every row in a department the *same* total (not a running total) — this default-frame subtlety is a common source of "why is my running total wrong" bugs.

---

## Part 3 — Things to say if the interviewer pushes further
- **Logical execution order** matters for why window functions can't be filtered in the same `SELECT`'s `WHERE`: SQL evaluates roughly `FROM/JOIN → WHERE → GROUP BY → HAVING → SELECT (window functions computed here) → ORDER BY`. Window functions are computed *after* `WHERE`/`GROUP BY`/`HAVING`, which is exactly why filtering on one requires wrapping in a subquery/CTE and filtering in an outer `WHERE`.
- **Performance**: window functions typically require a sort per partition (for the `ORDER BY` inside `OVER`) — an index on `(dept_id, salary)` for Problem 1, or `(dept_id, emp_id)` for Problem 2, lets the database use the index's existing order instead of sorting at query time.
- Full `RANK`/`DENSE_RANK`/`ROW_NUMBER` three-way comparison with worked example: [../day2/04-sql-queries.md](../day2/04-sql-queries.md#2-nth-highest-salary-parameterized-by-n).
