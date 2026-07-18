# SQL — Aggregate Functions & Window Functions (Practice Round)

Same schema as [../day2/04-sql-queries.md](../day2/04-sql-queries.md) and [../day4/06-sql-window-functions.md](../day4/06-sql-window-functions.md):
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

Today's two problems (top 3 per department, running total) are the **same shape** as [Day 4's window functions problems](../day4/06-sql-window-functions.md#part-2--problems) (top 2 per department, running total) — deliberately, as spaced-repetition recall. This doc is short: a quick aggregate-functions primer (not covered as its own topic before), then the two solutions with what's changed.

## Part 1 — Concepts (read this first)

### Aggregate functions
Collapse **multiple rows into a single value**: `COUNT()`, `SUM()`, `AVG()`, `MIN()`, `MAX()`. Used two ways:
- **No `GROUP BY`** — one aggregate value for the *entire* result set:
  ```sql
  SELECT COUNT(*) AS total_employees, AVG(salary) AS avg_salary FROM Employee;
  ```
- **With `GROUP BY`** — one aggregate value **per group**, and every non-aggregated column in `SELECT` must appear in `GROUP BY` (or be functionally dependent on it):
  ```sql
  SELECT dept_id, COUNT(*) AS headcount, AVG(salary) AS avg_salary
  FROM Employee
  GROUP BY dept_id;
  ```
`COUNT(*)` counts rows (including `NULL`s in any column); `COUNT(column)` counts only rows where that specific column is non-`NULL` — a real, checkable difference when a column is nullable (e.g. `COUNT(dept_id)` on the schema above undercounts total employees if any have `dept_id IS NULL`).

### The core difference from window functions, stated precisely
This is the fact [Day 4's doc](../day4/06-sql-window-functions.md#part-1--concepts-read-this-first) is built around, worth restating as the one-sentence answer: **`GROUP BY` collapses rows down to one row per group; a window function (`... OVER (PARTITION BY ...)`) keeps every row, just attaching a per-row computed value based on its group.** `GROUP BY` answers "what's the total per department" (department-level questions); a window function answers "what's this employee's rank/running-total *within* their department" (row-level questions that still need group-relative context) — today's two problems are both the second kind, which is exactly why `GROUP BY` alone can't solve either (see Day 4's note on why the top-N problem specifically needs a window function, not just `GROUP BY` + `LIMIT`).

---

## Part 2 — Solve

### Top 3 salaries in each department
Same pattern as [Day 4's top-2 solution](../day4/06-sql-window-functions.md#1-top-2-highest-paid-employees-per-department), `DENSE_RANK` for the same tie-handling reason, threshold changed to 3:
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
WHERE salary_rank <= 3
ORDER BY dept_id, salary_rank;
```
*Why `DENSE_RANK` and not `ROW_NUMBER` here, restated*: if two employees in a department tie for the 3rd-highest salary, both should show up as "top 3" (they're both legitimately in 3rd place) — `DENSE_RANK` gives them the same rank and both survive `WHERE salary_rank <= 3`. `ROW_NUMBER` would arbitrarily break the tie and could cut one of them, silently dropping someone who's genuinely tied for 3rd. If the requirement were instead "exactly 3 rows per department, no more, ties broken arbitrarily," `ROW_NUMBER` would be the *correct* choice instead — know which one the actual requirement calls for, don't default to one without checking.

### Running total of employee salaries by department
Identical structure to [Day 4's running-total solution](../day4/06-sql-window-functions.md#2-running-total-of-salary-within-each-department-ordered-by-hire-order-emp_id-as-a-stand-in-for-hire-order):
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
The mechanics here are exactly Day 4's — `PARTITION BY dept_id` resets the running sum independently per department, `ORDER BY emp_id` establishes the accumulation order (hire order, as a stand-in), and the explicit frame (`ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`) makes "sum from the start of this partition through the current row" explicit rather than relying on the engine's default frame — see [Day 4's frame-clause note](../day4/06-sql-window-functions.md#2-running-total-of-salary-within-each-department-ordered-by-hire-order-emp_id-as-a-stand-in-for-hire-order) for why omitting `ORDER BY` inside `OVER` entirely would silently break this into a per-partition total instead of a running one.

---

## Part 3 — Self-check
If Day 4's two problems felt easy to redo here without re-reading, that's the point of the repetition — the goal isn't a new pattern, it's fluency: being able to write `DENSE_RANK() OVER (PARTITION BY ... ORDER BY ... DESC)` wrapped in a subquery, and a running-total `SUM() OVER (...)` with an explicit frame, from memory, in an actual interview, without needing to reconstruct the logic from scratch each time.
