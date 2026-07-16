# React — Hooks

Builds directly on [../day4/05-react-fundamentals.md](../day4/05-react-fundamentals.md), which built the Employee Dashboard (`App` → `Header`, `EmployeeList` → many `EmployeeCard`, `Footer`) with a hardcoded `EMPLOYEES` array and introduced `useState` for a simple counter. Today: real data loading, with `useEffect`, a loading indicator, and error handling — turning that hardcoded array into something that behaves like a real app talking to a real (mock) API.

## Part 1 — Concepts (read this first)

### `useState` recap
Declares a piece of state local to a component and a setter function to update it: `const [value, setValue] = useState(initialValue)`. Calling the setter schedules a re-render with the new value — it does not mutate `value` in place (see [day 4's re-render question](../day4/05-react-fundamentals.md#part-3--interview-questions-todays-round) for why creating new references matters for objects/arrays specifically).

### `useEffect`
Runs a function **after** React commits a render to the DOM — the hook for anything that isn't pure rendering: fetching data, subscribing to something external, manually touching the DOM, logging, starting a timer. Signature:
```jsx
useEffect(() => {
  // effect body -- runs after render
  return () => {
    // optional cleanup -- runs before the NEXT effect run, and on unmount
  };
}, [dependencies]);
```
The core idea: rendering (computing JSX from props/state) must stay a **pure** description of the UI — it can't have side effects, because React may call a component function multiple times, out of order, or throw away a render entirely (e.g. in Strict Mode's intentional double-invoke in development, specifically to surface effects that aren't idempotent). `useEffect` is the escape hatch for anything that needs to reach outside that pure description — network calls, subscriptions, DOM APIs — and it's guaranteed to run only after the DOM actually reflects the current render.

### The dependency array — what actually controls when an effect re-runs
| Dependency array | Runs |
|---|---|
| Omitted entirely | After **every** render (rarely what you want) |
| `[]` (empty array) | **Once**, after the first render only — the closest functional equivalent to class components' `componentDidMount` |
| `[a, b]` | After the first render, and again any time `a` or `b` changes value between renders |

React compares each dependency to its previous-render value with `Object.is` (same equality check that governs whether a state update triggers a re-render at all — see [day 4](../day4/05-react-fundamentals.md#part-3--interview-questions-todays-round)). This is exactly why an object or array literal created fresh inside the render body (`{ id }` or `[item]`) as a dependency causes the effect to re-run on *every* render even though its "contents" look unchanged — it's a new reference every time, so `Object.is` says "different," every single render.

**Why the dependency array must be honest** (not just "whatever makes warnings go away"): every value from component scope that the effect body actually reads should be listed. Omitting one means the effect can run holding onto a **stale closure** — an old value captured from a previous render — which is a real, hard-to-diagnose class of React bugs, not a style nitpick. The ESLint `react-hooks/exhaustive-deps` rule exists specifically to catch this.

### Component lifecycle in functional components
Class components had named lifecycle methods (`componentDidMount`, `componentDidUpdate`, `componentWillUnmount`); functional components express the same three moments through `useEffect` + its dependency array + its cleanup return:

| Class lifecycle | Functional equivalent |
|---|---|
| `componentDidMount` | `useEffect(() => { ... }, [])` — empty array, runs once after first render |
| `componentDidUpdate` | `useEffect(() => { ... }, [dep])` — runs after first render **and** whenever a listed dependency changes |
| `componentWillUnmount` | The function **returned** from inside `useEffect` — runs as cleanup right before the component is removed (or right before the effect re-runs due to a dependency change, cleaning up the *previous* run before starting the next) |

---

## Part 2 — Build: Employee Dashboard with data loading

Same component tree as yesterday (`App` → `Header`, `EmployeeList` → `EmployeeCard`s, `Footer`), but `App` now loads data instead of using a hardcoded constant, and tracks three states explicitly: loading, error, and the loaded data.

```jsx
// api.js -- a mock "API" standing in for a real backend call
export function fetchEmployees() {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const failed = Math.random() < 0.2;         // simulate an occasional real-world failure
      if (failed) {
        reject(new Error("Failed to load employees"));
      } else {
        resolve([
          { id: 1, name: "Alice Johnson", title: "Software Engineer", department: "Engineering" },
          { id: 2, name: "Bob Smith", title: "Product Manager", department: "Product" },
          { id: 3, name: "Carol Lee", title: "Designer", department: "Design" },
        ]);
      }
    }, 800);
  });
}
```

```jsx
// App.jsx
import { useState, useEffect } from "react";
import { fetchEmployees } from "./api";

function App() {
  const [employees, setEmployees] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;                          // guards against setting state after unmount

    setLoading(true);
    setError(null);
    fetchEmployees()
      .then((data) => {
        if (!cancelled) setEmployees(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;                              // cleanup: if App unmounts mid-fetch, ignore the late response
    };
  }, []);                                             // [] -- run once, on mount, like componentDidMount

  if (loading) {
    return (
      <div className="app">
        <Header />
        <p className="loading">Loading employees...</p>
        <Footer />
      </div>
    );
  }

  if (error) {
    return (
      <div className="app">
        <Header />
        <p className="error">Error: {error}</p>
        <Footer />
      </div>
    );
  }

  return (
    <div className="app">
      <Header />
      <EmployeeList employees={employees} />
      <Footer />
    </div>
  );
}

export default App;
```

**Why the `cancelled` flag in cleanup**: `fetchEmployees()` is already in flight when `App` might unmount (route change, parent stops rendering it, etc.). Without the guard, the `.then`/`.catch` callback could still fire *after* unmount and call `setEmployees`/`setError` on a component that no longer exists — React logs a warning for this ("can't perform a state update on an unmounted component") because it's usually a real bug (a memory leak, or state changing for a component the user can no longer see). The cleanup function runs before the *next* effect invocation or on unmount, so flipping `cancelled = true` there is the standard pattern to make the in-flight response a no-op if it arrives too late.

**Why three separate state variables instead of one "status" object**: `loading`, `error`, and `employees` change somewhat independently and each maps directly to a distinct render branch above. Some teams prefer collapsing them into one reducer-managed state object (`{ status: "loading" | "error" | "success", data, error }`) specifically to make invalid combinations (e.g. `loading: true` and `error: "..."` simultaneously) structurally impossible — worth naming as a known alternative, not required for an app this size.

---

## Part 3 — Interview Questions (today's round)

**Q: When does `useEffect` run, relative to rendering?**
**A:** After React has rendered the component **and** committed the resulting changes to the actual DOM — never during rendering itself. This ordering guarantee is why effects can safely read the current DOM or start something that depends on the UI already being on screen. What actually triggers an effect to run again is its dependency array: omitted means every render, `[]` means once after the first render only, `[a, b]` means after the first render and again whenever `a` or `b` changes value (compared via `Object.is` against their previous-render values).

**Q: Why does changing state trigger a re-render?**
**A:** Calling a `useState` setter tells React "the value this component should render with has changed" — React schedules that component (and by default its children) to re-run its function body and produce new JSX, then diffs the result against the previous Virtual DOM tree to patch only what actually changed in the real DOM (see [day 4's Virtual DOM section](../day4/05-react-fundamentals.md#virtual-dom)). It's specifically the setter call that triggers this, not directly mutating a `let`/object in place — React only knows to re-render because the setter function itself performed the update through React's own bookkeeping.

**Q: What's the difference between props and state?**
**A:** Props flow **into** a component from its parent and are read-only from the receiving component's side — the child cannot change its own props. State is data a component **owns and manages itself**, and can update via its own setter in response to events, timers, or (as in today's build) an async fetch resolving. A component with only props and no state can only ever reflect what its parent handed it; state is what lets a component represent its *own* changing view — like today's `loading`/`error`/`employees` triple, none of which come from a parent.

**Q: What happens if you forget to include a value the effect reads in its dependency array?**
**A:** The effect can capture a **stale closure** — it keeps referencing whatever that value was on the render when the effect was originally set up, even after the "real" value (in props/state) has since changed. This is a genuine bug class, not a lint formality: e.g. an effect reading a `userId` prop that isn't listed as a dependency will keep fetching data for the *original* `userId` forever, even after the prop changes and the component re-renders with a new one. The `react-hooks/exhaustive-deps` ESLint rule flags exactly this by checking that every externally-scoped value the effect body reads is present in the array.
