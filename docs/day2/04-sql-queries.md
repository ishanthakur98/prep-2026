# SQL — Employee Table Queries

## Part 1 — Concepts (read this first)

Assumed schema for these exercises:

```sql
CREATE TABLE Employee (
    emp_id   INT PRIMARY KEY,
    name     VARCHAR(100),
    salary   DECIMAL(10,2),
    dept_id  INT NULL,          -- nullable: some employees have no department
    email    VARCHAR(100)
);

CREATE TABLE Department (
    dept_id   INT PRIMARY KEY,
    dept_name VARCHAR(100)
);
```

Key tools you'll lean on:
- **`LIMIT`/`OFFSET`** (or `FETCH`/`OFFSET` in standard SQL) — pull the Nth row after sorting.
- **Window functions** (`RANK()`, `DENSE_RANK()`, `ROW_NUMBER()` with `OVER (PARTITION BY ... ORDER BY ...)`) — rank/number rows without collapsing them via `GROUP BY`. `DENSE_RANK` is usually what you want for "Nth highest **distinct** salary" since it doesn't skip numbers after ties.
- **`GROUP BY` + `HAVING`** — aggregate then filter on the aggregate (`WHERE` can't reference aggregates directly).
- **`LEFT JOIN ... WHERE right.key IS NULL`** — the standard "find rows in A with no match in B" pattern.

---

## Part 2 — Queries

### 1. Second Highest Salary

**Subquery approach** (works everywhere, including older MySQL):
```sql
SELECT MAX(salary) AS second_highest_salary
FROM Employee
WHERE salary < (SELECT MAX(salary) FROM Employee);
```
*Why*: the inner query finds the max; the outer query finds the max of everything **below** that — i.e., the second distinct highest. Handles ties correctly (if three people share the top salary, they're all excluded together).

**Window function approach** (cleaner, generalizes to Nth):
```sql
SELECT DISTINCT salary AS second_highest_salary
FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM Employee
) ranked
WHERE rnk = 2;
```

### 2. Nth Highest Salary (parameterized by N)

```sql
SELECT DISTINCT salary
FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM Employee
) ranked
WHERE rnk = :N;
```
*Why `DENSE_RANK` and not `ROW_NUMBER`*: `ROW_NUMBER` gives every row a unique number even if salaries tie (so two people on the same salary get ranks 1 and 2, corrupting "Nth highest distinct value"). `DENSE_RANK` gives tied rows the *same* rank and doesn't skip the next rank — exactly "Nth distinct salary" semantics. (`RANK()` also gives ties the same value, but then *skips* the next rank number, e.g. 1,1,3 — wrong for this use case.)

MySQL-only alternative without window functions:
```sql
SELECT salary FROM Employee
ORDER BY salary DESC
LIMIT 1 OFFSET :N-1;   -- e.g. OFFSET 1 for 2nd highest
```
*Caveat*: this doesn't dedupe salaries — if the top two employees tie, `OFFSET 1` still gives you the same salary again instead of skipping to the true "next distinct" value. Use the `DENSE_RANK` version when duplicates matter.

### 3. Department-wise Highest Salary

```sql
SELECT d.dept_name, MAX(e.salary) AS highest_salary
FROM Employee e
JOIN Department d ON e.dept_id = d.dept_id
GROUP BY d.dept_name;
```

If you also need the **employee(s)** who earn that max (not just the number):
```sql
SELECT e.dept_id, d.dept_name, e.name, e.salary
FROM Employee e
JOIN Department d ON e.dept_id = d.dept_id
WHERE e.salary = (
    SELECT MAX(e2.salary)
    FROM Employee e2
    WHERE e2.dept_id = e.dept_id
);
```
Or with a window function (avoids the correlated subquery, generally faster on large tables):
```sql
SELECT dept_id, dept_name, name, salary
FROM (
    SELECT e.dept_id, d.dept_name, e.name, e.salary,
           RANK() OVER (PARTITION BY e.dept_id ORDER BY e.salary DESC) AS rnk
    FROM Employee e
    JOIN Department d ON e.dept_id = d.dept_id
) ranked
WHERE rnk = 1;
```
*Why `RANK()` here*: ties at the top (two people sharing the department's max salary) should both show up — `RANK()` gives them the same rank 1, so both rows survive the `WHERE rnk = 1` filter.

### 4. Duplicate Records

Assuming "duplicate" means same `name` + `email` (adjust columns to your definition of duplicate):
```sql
SELECT name, email, COUNT(*) AS occurrences
FROM Employee
GROUP BY name, email
HAVING COUNT(*) > 1;
```
*Why `HAVING` not `WHERE`*: `WHERE` filters rows before aggregation; `COUNT(*)` doesn't exist yet at that stage. `HAVING` filters *after* grouping, when the aggregate is available.

To get the actual **duplicate rows** (not just the count) — useful before deleting duplicates, keeping one copy:
```sql
SELECT *
FROM (
    SELECT e.*,
           ROW_NUMBER() OVER (PARTITION BY name, email ORDER BY emp_id) AS rn
    FROM Employee e
) t
WHERE rn > 1;   -- these are the "extra" duplicate rows, safe to DELETE
```

### 5. Employees Without a Department

```sql
SELECT emp_id, name
FROM Employee
WHERE dept_id IS NULL;
```

If "without a department" instead means "dept_id doesn't match any row in Department" (orphaned foreign key rather than a NULL):
```sql
SELECT e.emp_id, e.name
FROM Employee e
LEFT JOIN Department d ON e.dept_id = d.dept_id
WHERE d.dept_id IS NULL;
```
*Why `LEFT JOIN ... IS NULL` and not `NOT IN`*: `NOT IN` with a subquery silently returns **zero rows** if that subquery's result contains even a single `NULL` — a classic SQL gotcha. `LEFT JOIN` (or `NOT EXISTS`) doesn't have this trap and is generally the safer, more performant default for "find A rows with no matching B".

---

## Part 3 — Things to say if the interviewer pushes further
- **Indexing**: `GROUP BY`/`ORDER BY`/`JOIN` columns (like `dept_id`, `salary`) benefit from indexes — mention you'd check `EXPLAIN` before assuming a query is efficient on a large table.
- **`RANK()` vs `DENSE_RANK()` vs `ROW_NUMBER()`** is a very common follow-up — know the three-way distinction cold (shown above in Query 2).
- **`NOT IN` vs `NOT EXISTS` vs `LEFT JOIN ... IS NULL`** for anti-joins — know the `NULL` trap with `NOT IN`.
