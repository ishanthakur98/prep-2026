# React — Lifecycle

[Day 5's hooks doc](../day5/05-react-hooks.md) already covered `useEffect` mechanics, the dependency array, and the class-lifecycle-equivalent table in enough depth to build on directly — today names the three lifecycle *phases* explicitly (Mounting/Updating/Unmounting), goes deeper on cleanup timing and Strict Mode's double-invoke behavior, then extends the Employee Dashboard with React Router: a details page, fetched by ID, with its own loading/error states.

## Part 1 — Concepts (read this first)

### The three phases
| Phase | What happens | Functional-component trigger |
|---|---|---|
| **Mounting** | Component is created and inserted into the DOM for the first time | First render + first commit. `useEffect(() => {...}, [])` runs once, right after this commit. |
| **Updating** | Component re-renders because its own state changed, its props changed, or a parent/context it subscribes to re-rendered | Any `useState` setter call, new props from a parent, or a subscribed context value changing. `useEffect(() => {...}, [dep])` re-runs only if a listed dependency actually changed value (`Object.is` comparison, per [Day 5](../day5/05-react-hooks.md#the-dependency-array--what-actually-controls-when-an-effect-re-runs)). |
| **Unmounting** | Component is removed from the DOM entirely — conditionally stopped being rendered, its route navigated away from, its parent list item removed, etc. | Every effect's **cleanup function** (the function returned from inside `useEffect`) runs once, right before removal. |

**Render phase vs. commit phase** (the underlying split that phases 1 and 2 both go through): rendering is React calling the component function to compute *what the JSX should look like* — pure, side-effect-free, and possibly thrown away or run twice (see Strict Mode below) without any visible consequence. **Committing** is React actually applying the computed changes to the real DOM. `useEffect` callbacks are guaranteed to run only **after commit** — never during render — which is the whole reason effects can safely read the live DOM or kick off anything that assumes the UI is already on screen.

### Cleanup functions — when they actually run, not just "on unmount"
A common misconception is that a `useEffect` cleanup only matters for unmounting. It actually runs at **two** distinct moments:
1. **Right before the *next* run of that same effect**, if a dependency changed (Updating phase) — cleaning up the *previous* run's side effect before the new one starts.
2. **Right before unmount** (final cleanup, no next run follows).
```jsx
useEffect(() => {
  const timer = setInterval(() => console.log('tick for', employeeId), 1000);
  return () => clearInterval(timer); // runs before the NEXT effect (new employeeId) AND on unmount
}, [employeeId]);
```
If `employeeId` changes (navigating from one employee's page to another, reusing the same mounted component — see the routing gotcha below), the *old* interval is cleared **before** the new one is created — without this, every navigation would leave the previous interval silently still running, compounding with each navigation. This is the concrete mechanism behind "cleanup functions prevent memory leaks in React components": any subscription, timer, or event listener started in an effect needs a matching teardown in that effect's cleanup, or it outlives the render that created it — the exact same "unintentional strong reference" root cause as [today's Java memory-leak doc](02-java-jvm-memory-leaks-performance.md#how-a-garbage-collected-language-still-leaks-memory), just at the DOM/subscription level instead of the heap.

### Why `useEffect` sometimes runs twice in development
React 18+ **Strict Mode** (`<React.StrictMode>`, on by default in `create-react-app`/Vite dev templates) intentionally **mounts, immediately unmounts, then remounts** every component once in development only — running each effect's setup, then its cleanup, then its setup again. This is deliberate, not a bug: it's designed to surface effects that aren't properly idempotent/cleaned-up — if an effect's cleanup function doesn't correctly undo everything the setup did (an uncancelled subscription, a listener added but not removed), the double-invoke makes that bug visible immediately in development instead of manifesting later as a subtle production leak. **This never happens in a production build** — only in development, and only as a deliberate correctness check, which is exactly why "my `useEffect` ran twice and I don't know why" is such a common early-React confusion.

### `useEffect` dependency recap (from Day 5, load-bearing for the routing section below)
Omitted array → every render. `[]` → once, after mount only. `[dep]` → after mount, and again whenever `dep` changes. Full explanation, including the stale-closure risk of an incomplete array: [Day 5's doc](../day5/05-react-hooks.md#the-dependency-array--what-actually-controls-when-an-effect-re-runs).

---

## Part 2 — Build: Employee Details page with React Router

### Routing setup
```jsx
// main.jsx
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App";
import EmployeeDetail from "./EmployeeDetail";

function Root() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/employees/:id" element={<EmployeeDetail />} />
      </Routes>
    </BrowserRouter>
  );
}
```
```jsx
// EmployeeCard.jsx -- link each card to its detail page
import { Link } from "react-router-dom";

function EmployeeCard({ employee }) {
  return (
    <Link to={`/employees/${employee.id}`} className="employee-card">
      <h3>{employee.name}</h3>
      <p>{employee.title}</p>
    </Link>
  );
}
```

### Fetch employee by ID, with loading/error states
```jsx
// api.js -- add alongside Day 5's fetchEmployees()
export function fetchEmployeeById(id) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const employee = MOCK_EMPLOYEES.find((e) => e.id === Number(id));
      if (employee) resolve(employee);
      else reject(new Error(`No employee found with id ${id}`));
    }, 500);
  });
}
```
```jsx
// EmployeeDetail.jsx
import { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { fetchEmployeeById } from "./api";

function EmployeeDetail() {
  const { id } = useParams();              // route param, e.g. "3" from /employees/3
  const [employee, setEmployee] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError(null);
    setEmployee(null);                      // clear stale data from the PREVIOUS id before fetching the new one

    fetchEmployeeById(id)
      .then((data) => { if (!cancelled) setEmployee(data); })
      .catch((err) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [id]);                                 // re-run whenever the route param changes -- see the gotcha below

  if (loading) return <p className="loading">Loading employee...</p>;
  if (error) return <p className="error">Error: {error}</p>;

  return (
    <div className="employee-detail">
      <Link to="/">&larr; Back to all employees</Link>
      <h2>{employee.name}</h2>
      <p>{employee.title} &mdash; {employee.department}</p>
    </div>
  );
}

export default EmployeeDetail;
```

**The routing-specific gotcha `[id]` exists to prevent**: navigating from `/employees/1` to `/employees/2` via the `<Link>` above does **not** unmount and remount `EmployeeDetail` — React Router reuses the *same* component instance for the same route pattern, only the `id` param changes. If the dependency array were `[]` instead of `[id]`, the fetch would only ever run once, on the very first mount, and clicking through to a different employee would silently keep showing the *first* employee's data forever, with `useParams()` reporting the new `id` but nothing ever re-fetching against it — a stale-closure bug in exactly the shape [Day 5's doc](../day5/05-react-hooks.md#part-3--interview-questions-todays-round) already named, just triggered by client-side routing instead of a prop change. Clearing `employee` to `null` back to a loading state at the top of the effect (rather than leaving the previous employee's data on screen while the new fetch is in flight) avoids a more subtle bug: briefly rendering the *previous* employee's name/title under the *new* URL before the new data arrives.

---

## Part 3 — Interview Questions (today's round)

**Q: Why does `useEffect` sometimes run more than once in development?**
**A:** React 18+ Strict Mode deliberately mounts, unmounts, and remounts every component once in development (running each effect's setup, then cleanup, then setup again) specifically to surface effects whose cleanup doesn't correctly undo everything the setup did — a subscription or listener that isn't properly torn down shows up immediately as a visible bug in development instead of silently leaking in production. It's a real, deliberate correctness check, not a bug in React, and it never happens in a production build.

**Q: How do you prevent memory leaks in React components?**
**A:** Every effect that starts something with a lifetime beyond a single render — a subscription, a timer/interval, an event listener, an in-flight async request — needs a matching teardown in that effect's returned cleanup function: `clearInterval`/`clearTimeout`, `removeEventListener`, unsubscribing, or (for async fetches specifically) a `cancelled` flag guard so a late-arriving response can't call a setter on an unmounted component. The mechanism is the same root cause as any reference-based memory leak elsewhere ([today's Java doc](02-java-jvm-memory-leaks-performance.md#how-a-garbage-collected-language-still-leaks-memory)): something outliving the component that started it, still strongly referencing/affecting it after it's gone.

**Q: What's the difference between the render phase and the commit phase?**
**A:** Render is React calling the component function to compute what the JSX *should* look like — required to be pure, side-effect-free, and safe to call more than once or discard (which is exactly what Strict Mode exploits to catch effect bugs). Commit is React actually writing the computed changes to the real DOM. `useEffect` callbacks are guaranteed to run only after commit, never during render, which is why they can safely assume the DOM already reflects the current render.

**Q: In the Employee Details page, why is `[id]` in the dependency array instead of `[]`?**
**A:** Because React Router reuses the same mounted `EmployeeDetail` instance across navigations between different `:id` values on the same route — it doesn't remount the component just because the URL param changed. An empty array would mean the fetch effect only ever runs once, on the very first mount, so clicking from one employee to another would update the URL and `useParams()`'s return value, but never trigger a new fetch — a stale-closure bug, the same category [Day 5's doc](../day5/05-react-hooks.md#part-3--interview-questions-todays-round) covers for any dependency array that omits a value the effect actually reads.
