# SQL — Practice Round

Same schema as [Day 6's aggregate/window-functions doc](../day6/06-sql-aggregate-window-functions.md), with one addition — `hire_date`, needed for today's "hired in the last 30 days" problem, added the same way [Day 5 added `manager_id`](../day5/06-sql-self-join-cte.md#part-1--concepts-read-this-first):
```sql
CREATE TABLE Employee (
    emp_id     INT PRIMARY KEY,
    name       VARCHAR(100),
    salary     DECIMAL(10,2),
    dept_id    INT NULL,
    email      VARCHAR(100)
);

CREATE TABLE Department (
    dept_id    INT PRIMARY KEY,
    dept_name  VARCHAR(100)
);

ALTER TABLE Employee ADD COLUMN manager_id INT NULL REFERENCES Employee(emp_id); -- from Day 5
ALTER TABLE Employee ADD COLUMN hire_date  DATE NOT NULL DEFAULT CURRENT_DATE;   -- new today
```

No new concepts today — pure practice round, deliberately mixing a `GROUP BY`/`HAVING` problem, a date-filter problem, and two window-function problems (Nth-highest and per-group-highest) back to back, the way an actual interview round jumps between SQL topics rather than staying on one.

## Part 1 — Solve

### 1. Find duplicate employees
"Duplicate" here means two or more rows sharing the same `email` — a data-quality problem (e.g. a broken signup flow that allowed a repeat insert), the classic shape of LeetCode 182 ("Duplicate Emails"):
```sql
SELECT email, COUNT(*) AS occurrences
FROM Employee
GROUP BY email
HAVING COUNT(*) > 1;
```
**Why `HAVING`, not `WHERE`**: `WHERE` filters individual rows *before* grouping happens; `COUNT(*) > 1` is a property of the *group*, which doesn't exist yet at the point `WHERE` would run. `HAVING` filters groups *after* `GROUP BY` has produced them — this ordering (`FROM` → `WHERE` → `GROUP BY` → `HAVING` → `SELECT`) is exactly why `COUNT(*)` can't be referenced in a `WHERE` clause at all.

**Natural follow-up** (a real interview escalation — "now delete all but one of each duplicate"), solved with a window function instead of `GROUP BY`, since a delete needs to identify *specific rows*, not just group summaries:
```sql
WITH ranked AS (
    SELECT emp_id,
           ROW_NUMBER() OVER (PARTITION BY email ORDER BY emp_id) AS rn
    FROM Employee
)
DELETE FROM Employee
WHERE emp_id IN (SELECT emp_id FROM ranked WHERE rn > 1);
```
`ROW_NUMBER() OVER (PARTITION BY email ORDER BY emp_id)` numbers each email-group's rows 1, 2, 3... in `emp_id` order; keeping `rn = 1` (the lowest `emp_id`, i.e. the original row) and deleting everything with `rn > 1` removes every duplicate but the first, deterministically.

### 2. Employees hired in the last 30 days
```sql
SELECT emp_id, name, hire_date
FROM Employee
WHERE hire_date >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY hire_date DESC;
```
**Why `hire_date >= CURRENT_DATE - INTERVAL '30 days'`, not `DATEDIFF(CURRENT_DATE, hire_date) <= 30`** (or any function wrapping the column): applying a function *to the column itself* in a `WHERE` clause is **non-sargable** — the index on `hire_date` (see [today's indexing doc](10-system-design-indexing.md)) can't be used, because the database would have to compute `DATEDIFF(...)` for every single row before it can know which ones match, rather than seeking directly into the index for rows `>=` a computed boundary value. Rewriting the filter so the column stands alone on one side of the comparison (`hire_date >= <computed constant>`) keeps the predicate **sargable** — an index range scan is possible again. This is a real, checkable performance difference on a large table, not just a style preference.

### 3. Third highest salary
Same `DENSE_RANK` pattern as [Day 5's second-highest-salary problem](../day5/06-sql-self-join-cte.md#2-second-highest-salary--two-approaches), threshold changed to 3 and restated here for direct comparison against approach 1 below:
```sql
SELECT salary AS third_highest_salary
FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS salary_rank
    FROM Employee
) ranked
WHERE salary_rank = 3;
```
**Why `DENSE_RANK`, not `RANK` or `ROW_NUMBER`, restated for `= 3` instead of `<= 3`**: `DENSE_RANK` assigns **consecutive** ranks with no gaps after a tie (`100, 100, 90` → ranks `1, 1, 2`), so "3rd highest" correctly means "3rd *distinct* salary value," matching how a person would naturally answer the question. `RANK` leaves a gap after ties (`100, 100, 90, 80` → ranks `1, 1, 3, 4`) — under `RANK`, `salary_rank = 3` would return `90`, silently skipping past the fact that two people are tied for 1st, which is very likely *not* what "third highest salary" means to whoever's asking. `ROW_NUMBER` breaks ties arbitrarily and would treat one of the two `100`s as "2nd," which is outright wrong for a *distinct-value* ranking question.

**No-window-function fallback** (worth knowing cold, since it's the more classic version of this exact question):
```sql
SELECT DISTINCT salary AS third_highest_salary
FROM Employee e1
WHERE 3 = (SELECT COUNT(DISTINCT salary) FROM Employee e2 WHERE e2.salary >= e1.salary);
```
Reads as: "keep a salary if exactly 2 distinct salaries are greater than or equal to it, other than itself" — i.e., exactly 2 distinct values sit at or above it before it, making it the 3rd distinct value from the top. `DISTINCT` in the outer `SELECT` matters here too — without it, every *row* sharing the 3rd-highest value would each independently satisfy the correlated subquery and appear once per row, not once per distinct value.

### 4. Highest salary per department, using window functions
Two legitimate window-function approaches — worth knowing both, since interviewers sometimes explicitly ask for "without a `GROUP BY`/self-join":

**Approach 1 — rank-and-filter**, same shape as [Day 6's top-3-per-department](../day6/06-sql-aggregate-window-functions.md#top-3-salaries-in-each-department), threshold now 1:
```sql
SELECT dept_id, name, salary
FROM (
    SELECT dept_id, name, salary,
           DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS salary_rank
    FROM Employee
) ranked
WHERE salary_rank = 1;
```

**Approach 2 — `MAX() OVER (PARTITION BY ...)`, compare in place, no subquery needed**:
```sql
SELECT dept_id, name, salary
FROM (
    SELECT dept_id, name, salary,
           MAX(salary) OVER (PARTITION BY dept_id) AS dept_max_salary
    FROM Employee
) with_max
WHERE salary = dept_max_salary;
```
**The difference between the two, worth being able to state**: `DENSE_RANK` produces a *rank number* per row, so `WHERE salary_rank = 1` is really "keep whoever's ranked first" — it naturally keeps **every** tied top-earner in a department, same as `DENSE_RANK`'s tie behavior in problem 3. `MAX() OVER (PARTITION BY dept_id)` instead broadcasts the *department's actual maximum salary value* onto every row in that partition, and the outer `WHERE salary = dept_max_salary` keeps rows whose own salary equals that broadcast value — functionally identical tie behavior (ties both survive), but conceptually simpler when the actual need is "compare this row against its group's aggregate" rather than "give me this row's rank." `MAX() OVER (...)` (no `ORDER BY` inside the window) computes over the *entire partition* for every row, unlike the running-total pattern from Day 6 which needed `ORDER BY` + an explicit frame specifically to stop at the current row instead.

---

## Part 2 — Self-check
For problems 3 and 4, write both the window-function version and the no-window-function fallback from memory, and be able to say out loud *why* `DENSE_RANK` (not `RANK`/`ROW_NUMBER`) is the correct choice whenever the question is really "the Nth **distinct** value" rather than "the Nth **row**." For problem 2, be able to explain sargability without looking — it's one of the most common "why is this simple query slow" real-world debugging answers.
