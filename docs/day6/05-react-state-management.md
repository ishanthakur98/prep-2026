# React — State Management

Builds directly on the Employee Dashboard from [../day4/05-react-fundamentals.md](../day4/05-react-fundamentals.md) (`App` → `Header`, `EmployeeList` → many `EmployeeCard`, `Footer`) and [../day5/05-react-hooks.md](../day5/05-react-hooks.md) (which added `useEffect`-driven data loading with `loading`/`error`/`employees` state in `App`). Today adds a **search box** on top of that loaded data, and the specific hooks (`useMemo`, `useCallback`, `React.memo`) that exist to keep the resulting re-renders cheap.

## Part 1 — Concepts (read this first)

### Lifting state up
When two sibling components need to share or coordinate around the same piece of state, that state can't live in either sibling — it has to live in their **closest common parent**, which then passes it down as props (the data) and a setter/callback (the way to change it). This isn't a special API, just a direct consequence of the one-way data flow [Day 4](../day4/05-react-fundamentals.md#props) already established: data only flows down, so anything two siblings both need has to be owned by whoever is above both of them.

Today's concrete case: a search box (some component) needs to filter the list `EmployeeList` renders — they're siblings (or the search box is new), so the `searchQuery` state has to live in `App`, with the search box receiving `(searchQuery, setSearchQuery)` as props and `EmployeeList` receiving the *filtered* list computed from that same state.

```
App (owns: employees, loading, error, searchQuery)
 ├── Header
 ├── SearchBox (props: value, onChange)         -- lifted state's consumer #1
 ├── EmployeeList (props: employees=filteredEmployees) -- lifted state's consumer #2
 └── Footer
```

### Controlled vs uncontrolled components
- **Controlled**: the input's value is driven entirely by React state — the DOM input has no memory of its own; React re-renders it with whatever `value` prop it's given, and every keystroke goes through `onChange` back into state first.
```jsx
function SearchBox({ value, onChange }) {
  return <input type="text" value={value} onChange={(e) => onChange(e.target.value)} />;
}
```
- **Uncontrolled**: the DOM manages the input's value itself; React reads it out on demand via a `ref` (`inputRef.current.value`) instead of tracking every keystroke in state.
```jsx
function UncontrolledSearchBox({ onSubmit }) {
  const inputRef = useRef(null);
  return (
    <input type="text" ref={inputRef} onBlur={() => onSubmit(inputRef.current.value)} />
  );
}
```
**Why controlled is the default for search-as-you-type specifically**: filtering *as the user types* requires React to know the current value on every keystroke to recompute the filtered list — that's inherently a controlled pattern. Uncontrolled inputs are a reasonable choice when you only need the value at a single point (form submission) and want to avoid a re-render on every keystroke — not the case here.

### `useMemo`
Caches the **result of a computation** across re-renders, only recomputing when a listed dependency actually changes:
```jsx
const filteredEmployees = useMemo(() => {
  return employees.filter((e) =>
    e.name.toLowerCase().includes(searchQuery.toLowerCase())
  );
}, [employees, searchQuery]);
```
Without `useMemo`, this `.filter()` would re-run on **every** render of `App` — including renders triggered by something totally unrelated to the search (e.g. a future "toggle dark mode" state) — recomputing the same filtered array from the same inputs for no reason. `useMemo` skips the recomputation and returns the previously cached array whenever `employees` and `searchQuery` are both unchanged (`Object.is` comparison, [same mechanism as the dependency array](../day5/05-react-hooks.md#the-dependency-array--what-actually-controls-when-an-effect-re-runs)).

**When it's actually worth it**: `useMemo` itself has a small cost (storing the cached value, comparing dependencies every render) — for a cheap computation (filtering a few dozen items), skipping `useMemo` entirely is often *faster* in practice than the memoization overhead. It earns its keep for **expensive** computations (large lists, heavy transforms) or, just as importantly, when the *result* needs a **stable reference** for something downstream that depends on referential equality — see `React.memo` below, where a new array reference every render defeats memoization even if the array's *contents* are identical.

### `useCallback`
Same idea as `useMemo`, but caches a **function reference** instead of a computed value:
```jsx
const handleSearchChange = useCallback((value) => {
  setSearchQuery(value);
}, []); // no dependencies -- setSearchQuery from useState is guaranteed stable across renders
```
Without `useCallback`, `App` re-rendering creates a **brand-new function object** for `handleSearchChange` every single time — even though it does the exact same thing every time. That new-reference-every-render is invisible for a plain DOM element (`<input onChange={handleSearchChange}>` doesn't care that the reference changed), but it matters the moment that function is passed as a prop to a child wrapped in `React.memo` (below) — a "new" prop reference on every render defeats the memoization, forcing the child to re-render anyway despite nothing meaningful having changed.

### `React.memo`
Wraps a component so React **skips re-rendering it** if its props are shallow-equal to what they were last render (shallow `Object.is` comparison per prop, not deep equality):
```jsx
const EmployeeCard = React.memo(function EmployeeCard({ employee }) {
  console.log("rendering", employee.name); // add temporarily to observe skipped renders
  return (
    <div className="employee-card">
      <h3>{employee.name}</h3>
      <p>{employee.title}</p>
    </div>
  );
});
```
Recall from [Day 4](../day4/05-react-fundamentals.md#part-3--interview-questions-todays-round): by default, **every child re-renders whenever its parent re-renders**, regardless of whether that child's own props actually changed. `React.memo` is the explicit opt-out — but it only helps if the props passed in are actually the *same reference* (primitives compare by value and are fine automatically; objects/arrays/functions need `useMemo`/`useCallback` upstream to keep their references stable, otherwise `React.memo` still sees "different props" every render and re-renders anyway, making the wrapping pure overhead with zero benefit).

**The three work together, not independently**: `React.memo` on `EmployeeCard` is useless if `App` passes it a freshly-filtered `employees` array (new reference every render) without `useMemo`, or a freshly-defined inline callback without `useCallback` — this is the single most common way people add `React.memo` and see no actual improvement, then conclude (wrongly) that it "doesn't work."

---

## Part 2 — Build: Employee app search + filter + optimization

Extending [Day 5's `App`](../day5/05-react-hooks.md#part-2--build-employee-dashboard-with-data-loading) (which already has `employees`/`loading`/`error` state from `useEffect`-driven fetching):

```jsx
// SearchBox.jsx
import { memo } from "react";

const SearchBox = memo(function SearchBox({ value, onChange }) {
  return (
    <input
      type="text"
      className="search-box"
      placeholder="Search employees by name..."
      value={value}
      onChange={(e) => onChange(e.target.value)}
    />
  );
});

export default SearchBox;
```

```jsx
// EmployeeCard.jsx
import { memo } from "react";

const EmployeeCard = memo(function EmployeeCard({ employee }) {
  return (
    <div className="employee-card">
      <h3>{employee.name}</h3>
      <p>{employee.title}</p>
      <p>{employee.department}</p>
    </div>
  );
});

export default EmployeeCard;
```

```jsx
// App.jsx
import { useState, useEffect, useMemo, useCallback } from "react";
import { fetchEmployees } from "./api";
import SearchBox from "./SearchBox";
import EmployeeList from "./EmployeeList";

function App() {
  const [employees, setEmployees] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetchEmployees()
      .then((data) => { if (!cancelled) setEmployees(data); })
      .catch((err) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // recompute only when employees or the query actually change -- not on every App render
  const filteredEmployees = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return employees;
    return employees.filter((e) => e.name.toLowerCase().includes(query));
  }, [employees, searchQuery]);

  // stable reference across renders -- keeps React.memo(SearchBox) actually effective
  const handleSearchChange = useCallback((value) => {
    setSearchQuery(value);
  }, []);

  if (loading) return <p className="loading">Loading employees...</p>;
  if (error) return <p className="error">Error: {error}</p>;

  return (
    <div className="app">
      <Header />
      <SearchBox value={searchQuery} onChange={handleSearchChange} />
      <EmployeeList employees={filteredEmployees} />
      <Footer />
    </div>
  );
}

export default App;
```

**Where each optimization is actually load-bearing here**: `useMemo` on `filteredEmployees` means typing in the search box only re-filters when `employees`/`searchQuery` change (not on unrelated re-renders); `useCallback` on `handleSearchChange` gives `SearchBox` a stable `onChange` reference so `React.memo(SearchBox)` can actually skip re-rendering it when something *else* in `App` changes; `React.memo` on `EmployeeCard` means that as `filteredEmployees` narrows down while typing, cards for employees that were *already* visible and still match don't re-render just because the array they're in got recomputed — only cards actually entering/leaving the filtered set change what's rendered on screen.

**How to verify it's actually working**: temporarily add `console.log("rendering", employee.name)` inside `EmployeeCard` (as shown above) and type into the search box — without `React.memo` + the memoized callback/filtered-array references, every keystroke logs every card re-rendering; with them in place, only the cards whose presence in the filtered list actually changed should log.

---

## Part 3 — Interview Questions (today's round)

**Q: Why do we "lift state up" instead of letting each component keep its own local state?**
**A:** React's data flow is one-directional — a component can only read `props` handed to it by a parent, never reach sideways into a sibling's state. When two components (a search box and a filtered list, here) both need to react to the same value, that value structurally *cannot* live in either one individually — it has to live in their common parent, which then hands it down as props to both: the raw value (and a setter) to the input, and the derived/filtered result to the list. It's not an optimization technique, it's the only way the one-way-data-flow model can express "these two components are coordinating around the same state" at all.

**Q: What's the difference between controlled and uncontrolled components, and when would you actually pick uncontrolled?**
**A:** A controlled input's value lives in React state — the DOM element is just a rendering of that state, and every change goes through `onChange` back into state before the UI updates. An uncontrolled input lets the DOM manage its own value, read out on demand via a `ref` rather than tracked on every keystroke. Controlled is the default because it's what makes derived, real-time behavior (search-as-you-type filtering, live validation, syncing multiple inputs) possible — you need to know the current value on every change to react to it. Uncontrolled is a reasonable, deliberate choice when you only need the value at one moment (e.g. reading it on form submit) and want to skip a state update (and the resulting re-render) on every single keystroke — a real, if usually minor, perf/simplicity tradeoff in the other direction.

**Q: When is `useMemo` actually worth using — and when does it not help, or even hurt?**
**A:** `useMemo` is worth it when either the computation itself is genuinely expensive (large-list filtering/sorting/transforms) or — often the more common real reason — when the *result* needs a stable reference across renders for something downstream to correctly skip work (e.g. `React.memo`'d children, or another hook's dependency array). It's *not* worth it for cheap computations run on components that re-render rarely — the bookkeeping cost of memoization (storing the cached value, running the dependency comparison every render) can exceed just redoing a trivial computation, making `useMemo` a net negative in that case. Rule of thumb: default to not using it, add it once you've identified an actual expensive recomputation or a stale-reference problem it fixes — not preemptively on every derived value.

**Q: What specifically causes unnecessary re-renders in React, and how does `React.memo` address it?**
**A:** By default, when a component re-renders, **every child re-renders too**, unconditionally — regardless of whether that specific child's props changed at all. This is React's default because checking "did anything actually change" has its own cost, and for most components re-rendering is cheap enough not to matter. `React.memo` opts a specific component out of that default: React does a shallow comparison of its new props against last render's props, and skips re-rendering (and re-running its function body) entirely if every prop is `Object.is`-equal to before. The catch: primitives compare by value fine automatically, but objects/arrays/functions compare by **reference** — a parent that recreates a new array or a new inline callback on every render defeats `React.memo` immediately, since the "same-looking" prop is actually a new reference every time. That's exactly why `React.memo` is normally paired with `useMemo`/`useCallback` upstream in the parent — they're what keep the props' *references* stable, which is the actual precondition `React.memo`'s shallow comparison depends on.
