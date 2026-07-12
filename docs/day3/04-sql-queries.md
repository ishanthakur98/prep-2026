# SQL — Top-N, Running Total, Dense Rank, Above-Average Queries

> Same schema as [../day2/04-sql-queries.md](../day2/04-sql-queries.md) — reused here so queries are directly comparable:

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

## Part 1 — Concepts (read this first)

Everything today is a **window function** problem. The one idea to hold onto: window functions let you compute a value **per row** that depends on *other* rows (a rank, a running sum, a group average) **without collapsing rows** the way `GROUP BY` does.

```sql
<window_function>() OVER (
    PARTITION BY <grouping columns>   -- optional: restart the window per group
    ORDER BY <ordering columns>       -- required for ranking/running total
    [ROWS/RANGE BETWEEN ... ]         -- optional: explicit frame, default is
                                       -- "start of partition to current row" when ORDER BY is present
)
```
- No `PARTITION BY` → the window is the whole result set.
- `PARTITION BY dept_id` → the window resets per department, independently.
- `RANK`/`DENSE_RANK`/`ROW_NUMBER` need `ORDER BY` inside `OVER(...)` to know what "rank" means.
- A running total needs `ORDER BY` inside `OVER(...)` **and** relies on the *default frame* (`RANGE UNBOUNDED PRECEDING AND CURRENT ROW`), which is exactly "sum everything from the start of the partition up to this row."

---

## Part 2 — Queries

### 1. Top 3 Salaries (per company, and per department)

**Overall top 3 distinct salaries:**
```sql
SELECT emp_id, name, salary
FROM (
    SELECT emp_id, name, salary,
           DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM Employee
) ranked
WHERE rnk <= 3;
```
*Why `DENSE_RANK`*: "top 3" should mean the top 3 **distinct salary values** — if two people tie for 2nd, both should appear, and there's no gap before 3rd. `ROW_NUMBER` would arbitrarily pick only one of the tied employees; `RANK` would correctly include both tied employees but then skip straight to rank 4, silently excluding the true 3rd-place salary. `DENSE_RANK` is the only one of the three that matches the plain-English meaning of "top 3 salaries."

**Top 3 salaries within each department** (a very common follow-up):
```sql
SELECT emp_id, name, dept_id, salary
FROM (
    SELECT emp_id, name, dept_id, salary,
           DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rnk
    FROM Employee
) ranked
WHERE rnk <= 3;
```
*Why this generalizes so easily*: adding `PARTITION BY dept_id` is the only change needed — the ranking now restarts independently inside each department instead of running over the whole table. This is the core reason window functions beat correlated subqueries for "top N per group": one query, one pass of logic, trivially adjustable scope.

---

### 2. Running Total

**Running total of salary, ordered by `emp_id` (e.g. cumulative payroll as you scan through employee IDs):**
```sql
SELECT emp_id, name, salary,
       SUM(salary) OVER (ORDER BY emp_id) AS running_total
FROM Employee;
```
*Why it works*: with `ORDER BY` inside `OVER(...)` and no explicit frame, the default frame is `RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` — meaning "sum every row from the start of the (implicit whole-table) partition up to and including the current row." Each row's `running_total` is therefore the sum of every salary seen "so far" in `emp_id` order.

**Running total per department (resets at each department boundary):**
```sql
SELECT emp_id, name, dept_id, salary,
       SUM(salary) OVER (PARTITION BY dept_id ORDER BY emp_id) AS dept_running_total
FROM Employee;
```
*Why `PARTITION BY` here too*: without it, the running total would keep accumulating across department boundaries instead of restarting at zero for each new department — partitioning scopes the "start of the window" to each group independently, same as in the top-3 query.

---

### 3. Dense Rank (salary ranking with proper tie handling)

```sql
SELECT emp_id, name, salary,
       DENSE_RANK() OVER (ORDER BY salary DESC) AS salary_rank
FROM Employee;
```

**The three-way distinction, worth having memorized cold** (given a table with salaries `100, 90, 90, 80`):

| Function | Ranks produced | Behavior on ties |
|---|---|---|
| `ROW_NUMBER()` | `1, 2, 3, 4` | Ties get **arbitrary distinct** numbers (order among ties is undefined unless you add a tiebreaker column) |
| `RANK()` | `1, 2, 2, 4` | Ties get the **same** rank, but the **next rank number is skipped** by the count of tied rows |
| `DENSE_RANK()` | `1, 2, 2, 3` | Ties get the **same** rank, and the **next rank is not skipped** — always consecutive |

Pick `DENSE_RANK` whenever "rank" should mean "how many distinct values are at or above mine" (leaderboards, "Nth highest distinct salary"). Pick `RANK` when a genuine competition-style rank matters (Olympic medal placing: two golds means the next place really is bronze, i.e. 3rd, not 2nd). Pick `ROW_NUMBER` when you need a truly unique sequence number regardless of ties (e.g. pagination, or picking exactly one row per group via `WHERE rn = 1`).

---

### 4. Employees Earning Above Their Department Average

**Correlated subquery approach** (most portable, works everywhere):
```sql
SELECT e.emp_id, e.name, e.dept_id, e.salary
FROM Employee e
WHERE e.salary > (
    SELECT AVG(e2.salary)
    FROM Employee e2
    WHERE e2.dept_id = e.dept_id
);
```
*Why it works*: the subquery re-runs once **per outer row**, each time scoped to that row's own department (`e2.dept_id = e.dept_id`), computing that department's average fresh — a genuinely correlated subquery, not a constant.

**Window function approach** (avoids re-running the subquery per row — generally faster on larger tables since the average is computed once per partition, not once per outer row):
```sql
SELECT emp_id, name, dept_id, salary
FROM (
    SELECT emp_id, name, dept_id, salary,
           AVG(salary) OVER (PARTITION BY dept_id) AS dept_avg
    FROM Employee
) t
WHERE salary > dept_avg;
```
*Why no `ORDER BY` in this `OVER(...)`*: `AVG(salary) OVER (PARTITION BY dept_id)` with no `ORDER BY` means "average over the **entire partition**, for every row" — not a running average. Omitting `ORDER BY` here is deliberate: adding it would change the frame default and turn this into a running average up to the current row, which is not what's wanted.

---

## Part 3 — Things to say if the interviewer pushes further

- **`RANK`/`DENSE_RANK`/`ROW_NUMBER` need `ORDER BY` inside `OVER(...)`; a plain aggregate window function (`SUM`, `AVG`) doesn't strictly need one** — but adding `ORDER BY` to an aggregate window function silently changes its default frame from "whole partition" to "start of partition through current row" (i.e. turns it into a running total/average). This is a genuinely common bug: someone adds `ORDER BY` to a window `AVG` intending to "sort the output" and inadvertently converts a group average into a running average.
- **Window functions run logically *after* `WHERE`/`GROUP BY`/`HAVING` but *before* the final `ORDER BY`/`LIMIT`** — which is exactly why you can't reference a window function's alias directly in the same query's `WHERE` clause (`WHERE salary_rank <= 3` fails) and need the subquery/CTE wrapper shown throughout this file.
- **Performance**: window functions typically require a sort (for `PARTITION BY`/`ORDER BY`) — an index on `(dept_id, salary)` helps both the department-scoped queries and any query partitioning/ordering by those columns; mention checking `EXPLAIN` before assuming.
