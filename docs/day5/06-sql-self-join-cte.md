# SQL — Self Join & CTEs

Builds on the schema and window-function tooling from [../day2/04-sql-queries.md](../day2/04-sql-queries.md) and [../day3/06-sql-window-functions.md](../day3/06-sql-window-functions.md). Today adds a **self-referencing** column to the `Employee` table (every employee optionally reports to another employee), which is what makes both of today's tools — self join and recursive CTE — actually necessary rather than academic.

## Part 1 — Concepts (read this first)

Schema addition for today:
```sql
ALTER TABLE Employee ADD COLUMN manager_id INT NULL REFERENCES Employee(emp_id);
-- NULL manager_id = no manager (e.g. the CEO / org root)
```

### Self Join
A join where a table is joined **to itself** — necessary whenever a row needs to be compared against *another row of the same table*, like an employee against their manager (also a row in `Employee`). The mechanism is identical to any other join; the only difference is you alias the same table twice so SQL (and you) can tell "this occurrence" from "that occurrence" apart:
```sql
SELECT e.name AS employee, m.name AS manager
FROM Employee e
JOIN Employee m ON e.manager_id = m.emp_id;
```
Read it as: `e` = "the employee row," `m` = "the manager row, which happens to live in the exact same table." Everything else (join type, `ON` condition, filtering) works exactly like joining two different tables.

### Common Table Expressions (CTEs)
A `WITH name AS (subquery)` block defines a named, temporary result set that the main query can reference like a table — scoped to just that one query. Two reasons to reach for a CTE over a nested subquery:
1. **Readability** — a chain of nested subqueries (`SELECT ... FROM (SELECT ... FROM (SELECT ...))`) reads inside-out and gets hard to follow past two levels; a CTE reads top-to-bottom in the order you'd explain it out loud.
2. **Reuse within one query** — a CTE can be referenced **multiple times** in the same query (e.g. joined against itself, or referenced from two different parts of the main query) without repeating the subquery's SQL or re-running it as a separate nested block each time (databases generally materialize or otherwise optimize a CTE referenced more than once, though this is implementation-dependent).
```sql
WITH ranked_salaries AS (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM Employee
)
SELECT DISTINCT salary
FROM ranked_salaries
WHERE rnk = 2;
```
This is the exact same logical query as [day2's window-function second-highest-salary query](../day2/04-sql-queries.md#1-second-highest-salary), rewritten with `WITH` instead of a nested `FROM (subquery)` — same execution, purely a readability choice once queries get more than one level deep.

### Recursive CTE — the idea
A CTE that **references itself**, used for traversing hierarchical/recursive structures a fixed join depth can't express — an org chart of arbitrary depth, a bill-of-materials tree, a folder structure. Structure:
```sql
WITH RECURSIVE org_chart AS (
    -- anchor member: the base case, runs once
    SELECT emp_id, name, manager_id, 1 AS depth
    FROM Employee
    WHERE manager_id IS NULL              -- the root(s) of the hierarchy

    UNION ALL

    -- recursive member: joins the CTE to itself, one level deeper each pass
    SELECT e.emp_id, e.name, e.manager_id, oc.depth + 1
    FROM Employee e
    JOIN org_chart oc ON e.manager_id = oc.emp_id
)
SELECT * FROM org_chart ORDER BY depth;
```
**Why this maps directly to recursion on a tree** ([today's DSA topic](01-dsa-trees.md#recursion-on-trees)): the **anchor member** is the base case (the root(s) — rows with no manager), and the **recursive member** is the recursive call — each pass joins the *previous* result set back against `Employee` to find "the next level down," exactly like a tree-recursive function calling itself on `node.left`/`node.right`. The engine keeps re-running the recursive member against the *most recently produced* rows until a pass produces zero new rows (the recursive equivalent of hitting `null` and returning) — that's the termination condition, and just like unbounded tree recursion, a **cyclic** `manager_id` relationship (impossible in a real org chart, but not impossible in bad data) would make this loop forever without a safeguard, which is why production recursive CTEs often add an explicit depth cap or cycle guard.

---

## Part 2 — Problems

### 1. Employees earning more than their manager
```sql
SELECT e.name AS employee, e.salary AS employee_salary,
       m.name AS manager, m.salary AS manager_salary
FROM Employee e
JOIN Employee m ON e.manager_id = m.emp_id
WHERE e.salary > m.salary;
```
**Why `JOIN` and not `LEFT JOIN`**: an employee with no manager (`manager_id IS NULL`) has nothing to compare against, so they should be excluded outright, not appear with `NULL` manager columns — an inner join does exactly that by naturally dropping rows where the `ON` condition can't match.

### 2. Second-highest salary — two approaches
**Approach 1 — subquery, no window functions** (see [day2](../day2/04-sql-queries.md#1-second-highest-salary) for the full walkthrough of *why* this handles ties correctly):
```sql
SELECT MAX(salary) AS second_highest_salary
FROM Employee
WHERE salary < (SELECT MAX(salary) FROM Employee);
```

**Approach 2 — CTE + `DENSE_RANK`** (today's version of the same window-function idea, written as a CTE instead of a nested nested `FROM`):
```sql
WITH ranked AS (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM Employee
)
SELECT DISTINCT salary AS second_highest_salary
FROM ranked
WHERE rnk = 2;
```
**When each is the right call**: the subquery approach needs no window-function support at all and is trivial to read for exactly "second highest," but doesn't generalize — asking for the 5th highest means rewriting the whole query with 5 levels of nested `MAX(salary) < (...)`. The CTE/`DENSE_RANK` approach generalizes to "Nth highest" by changing one literal (`WHERE rnk = :N`, see [day2's Nth-highest query](../day2/04-sql-queries.md#2-nth-highest-salary-parameterized-by-n)), and correctly treats tied salaries as one rank (three people tied for 1st still means the *next* distinct salary is rank 2) — the reason `DENSE_RANK` specifically, not `ROW_NUMBER` (which would give ties different numbers) or `RANK` (which would skip ranks after a tie, e.g. 1,1,1,4).

---

## Part 3 — Interview Questions (today's round)

**Q: What is a self join, mechanically — is it a different kind of join?**
**A:** No — it's an ordinary join (inner, left, whichever fits) where both sides happen to be the same table, aliased twice so each occurrence can be referred to independently (`Employee e JOIN Employee m ON e.manager_id = m.emp_id`). Nothing about the join mechanics changes; the only thing a self join requires that a two-table join doesn't is the aliasing, since `FROM Employee JOIN Employee` without aliases would be ambiguous.

**Q: Why use a CTE instead of a subquery — is there a real difference, or just style?**
**A:** Logically, a CTE referenced exactly once is equivalent to the same nested subquery — same query plan in most engines. The real advantages appear as queries grow: readability (a `WITH` block reads top-to-bottom in the order you'd narrate it, versus nested subqueries reading inside-out), and reuse — a CTE can be referenced multiple times in the same query (e.g. joined against itself, or used in two different branches of the main query) without duplicating the SQL, whereas a subquery would need to be repeated verbatim (and potentially re-executed) everywhere it's needed.

**Q: Explain the idea behind a recursive CTE — what are the anchor and recursive members, and what stops it from running forever?**
**A:** The anchor member is the base case — it runs once and seeds the initial rows (e.g. the org chart's roots, employees with no manager). The recursive member re-joins the CTE's own name back against the base table, producing "one level deeper" each pass, and the engine keeps executing it against only the *most recently added* rows until a pass produces zero new rows — that's the natural termination condition, directly analogous to a recursive function hitting its base case and stopping. Just like unbounded/cyclic recursion can blow the call stack, a cyclic relationship in the data (a manager loop) would make a recursive CTE loop forever without an explicit depth limit or cycle guard, since there'd never be a pass that produces zero new rows.

**Q: Why is `DENSE_RANK` specifically the right window function for "Nth highest distinct salary," over `RANK` or `ROW_NUMBER`?**
**A:** `ROW_NUMBER` assigns a strictly increasing number even to tied values, so three people tied for the top salary would occupy ranks 1, 2, 3 — collapsing "second highest" into someone who's actually tied for first. `RANK` correctly gives ties the same rank but then **skips** the next rank(s) (1, 1, 1, 4), so "rank = 2" would return nothing if there's a 3-way tie for first. `DENSE_RANK` gives ties the same rank **without** skipping the next one (1, 1, 1, 2) — so "the next distinct salary value after the ties" always lands on a predictable, gapless rank number, which is exactly what "Nth highest distinct value" means.
