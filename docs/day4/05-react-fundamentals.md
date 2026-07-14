# React — Fundamentals

## Part 1 — Concepts (read this first)

### What is React?
A JavaScript library (not a full framework) for building UIs out of **components** — small, reusable, composable pieces that each own their own markup and logic. Its core idea: describe **what** the UI should look like for a given state (`UI = f(state)`), and let React figure out **how** to update the actual DOM to match, instead of you manually writing imperative `document.getElementById(...).innerHTML = ...` calls to mutate it yourself.

### Virtual DOM
Direct DOM manipulation is slow relative to plain JS object operations, and naively re-rendering the whole page on every state change would be wasteful. React keeps a lightweight in-memory representation of the UI tree — the **Virtual DOM** — and on every state change:
1. Builds a new Virtual DOM tree reflecting the new state.
2. **Diffs** it against the previous Virtual DOM tree (the "reconciliation" algorithm).
3. Computes the minimal set of real DOM mutations needed to bring the actual DOM in line with the new tree, and applies only those.

This is why React feels declarative — you never write "update this specific DOM node," you just describe what the UI should look like *now*, and React works out the delta. It's a performance strategy, not magic: it trades "diff a JS object tree" (cheap) for "avoid touching the real DOM more than necessary" (expensive), since real DOM writes (layout, reflow) are the actual bottleneck.

### Components
The basic unit of a React UI — a function (or, historically, a class) that takes input (`props`) and returns what should be rendered (JSX). Components compose: a `Header`, `EmployeeList`, and `Footer` component can each be simple in isolation, and a parent `App` component assembles them into a full page. This mirrors the same decomposition instinct as breaking a backend into layers (controller/service/repository) — each piece has one responsibility and is independently testable/reasonable-about.

### JSX
A syntax extension that lets you write HTML-like markup directly inside JavaScript:
```jsx
const element = <h1>Hello, {name}</h1>;
```
This isn't a template string — `{ }` embeds a real JavaScript expression. Under the hood, a build tool (Babel/the framework's compiler) transforms JSX into plain function calls:
```js
const element = React.createElement("h1", null, "Hello, ", name);
```
JSX is why you can freely mix markup and logic (`{items.map(item => <li key={item.id}>{item.name}</li>)}`) — it's just JavaScript with a more readable syntax for describing tree structures, compiled away before the browser ever sees it.

### Props
Short for "properties" — how a **parent** passes data down into a **child** component. Props are **read-only** from the child's perspective: a component must never mutate its own `props` (this is a hard rule, not a style preference — React relies on props being immutable inputs to reason about when to re-render).
```jsx
function EmployeeCard({ name, title }) {   // destructured from props
  return (
    <div className="card">
      <h3>{name}</h3>
      <p>{title}</p>
    </div>
  );
}
// usage: <EmployeeCard name="Alice" title="Engineer" />
```
This is directly analogous to method parameters in Java — data flows one direction, in, and the callee doesn't mutate the caller's copy.

### State
Data that a component **owns and can change over time**, which — unlike props — triggers a re-render when updated. Declared with the `useState` hook in functional components:
```jsx
import { useState } from "react";

function Counter() {
  const [count, setCount] = useState(0);   // [currentValue, setterFunction], initial value 0
  return (
    <button onClick={() => setCount(count + 1)}>
      Clicked {count} times
    </button>
  );
}
```
Calling `setCount(...)` doesn't mutate `count` in place — it tells React "here's the new value, please re-render this component (and its children) with it." State is local/private to the component that declares it unless explicitly passed down as props to children, or lifted up to a shared parent (see the re-render interview question below).

### Functional components
Modern React components are plain JavaScript functions that return JSX, using **hooks** (`useState`, `useEffect`, etc. — functions starting with `use`) to add state and other capabilities that used to require class components (`this.state`, `componentDidMount`, etc.). Functional components + hooks are now the standard; class components are legacy but still appear in older codebases.

---

## Part 2 — Build: Employee Dashboard (hardcoded data)

Structure: `App` (root) → `Header`, `EmployeeList` (which renders many `EmployeeCard`s), `Footer`.

```jsx
// Header.jsx
function Header() {
  return (
    <header>
      <h1>Employee Dashboard</h1>
    </header>
  );
}

// EmployeeCard.jsx
function EmployeeCard({ employee }) {
  return (
    <div className="employee-card">
      <h3>{employee.name}</h3>
      <p>{employee.title}</p>
      <p>{employee.department}</p>
    </div>
  );
}

// EmployeeList.jsx
function EmployeeList({ employees }) {
  return (
    <div className="employee-list">
      {employees.map((emp) => (
        <EmployeeCard key={emp.id} employee={emp} />   // `key` must be stable + unique per item
      ))}
    </div>
  );
}

// Footer.jsx
function Footer() {
  return (
    <footer>
      <p>&copy; 2026 Employee Dashboard</p>
    </footer>
  );
}

// App.jsx
const EMPLOYEES = [                                     // hardcoded data for now
  { id: 1, name: "Alice Johnson", title: "Software Engineer", department: "Engineering" },
  { id: 2, name: "Bob Smith", title: "Product Manager", department: "Product" },
  { id: 3, name: "Carol Lee", title: "Designer", department: "Design" },
];

function App() {
  return (
    <div className="app">
      <Header />
      <EmployeeList employees={EMPLOYEES} />
      <Footer />
    </div>
  );
}

export default App;
```

**Why `key={emp.id}` and not `key={index}`**: React uses `key` to match items between the old and new Virtual DOM tree during a re-render — it's how React knows "this list item is the same one as before, just possibly moved/updated" versus "this is a brand new item." Using the array index as `key` breaks this matching if the list is ever reordered, filtered, or has items inserted/removed in the middle (React can misattribute state/DOM nodes to the wrong logical item). A stable unique ID from the data itself (`emp.id`) avoids that entirely — this is a real bug source, not a lint nitpick, once the data stops being hardcoded and starts being editable.

**Data flow in this app**: `EMPLOYEES` lives in `App`, flows down as `props` to `EmployeeList`, which passes each individual `employee` down as `props` to `EmployeeCard`. Nothing here uses `state` yet because nothing changes at runtime — hardcoded data is pure props flow. State enters the picture the moment you add something interactive (search/filter/sort, discussed as a later mini-project feature in [08-mini-project-plan.md](08-mini-project-plan.md)).

---

## Part 3 — Interview Questions (today's round)

**Q: Why do we need state if we already have props?**
**A:** Props are how data flows **into** a component from its parent — the component receiving them can't change them (they're owned by whoever passed them down). State is data a component **owns itself** and can change over time in response to events (clicks, input, timers). Without state, a component could only ever render whatever its parent handed it — there'd be no way to represent "this component's own view has changed" (a toggled dropdown, a typed search query, a counter) without going all the way up to whoever owns that data. State is the mechanism for a component's *own* changing data; props are the mechanism for *receiving* data from above.

**Q: What causes a component to re-render?**
**A:** Three triggers: (1) its own **state** changes (`setState`/the setter from `useState`), (2) it receives new **props** from its parent re-rendering, or (3) its parent re-renders for any reason (by default, all children re-render when a parent does, whether or not their own props actually changed — this is what `React.memo` exists to short-circuit). Critically, calling a state setter with the **same value** it already has (`setCount(count)` where `count` is unchanged) does not trigger a re-render — React does an equality check (`Object.is`) and bails out if nothing actually changed. This is also why mutating state in place (`someArray.push(x); setState(someArray)`) is broken — same reference in, same reference out, so React thinks nothing changed and skips the re-render even though the underlying data did mutate. Always create a **new** object/array (`setState([...someArray, x])`) so the reference itself changes.

**Q: What's the difference between the Virtual DOM and the real DOM?**
**A:** The real DOM is the browser's actual live representation of the page — mutating it is comparatively expensive because it can trigger layout recalculation and repainting. The Virtual DOM is React's lightweight in-memory JS object tree describing what the UI *should* look like. On a state change, React builds a new Virtual DOM tree, diffs it against the previous one, and applies only the minimal necessary changes to the real DOM — turning "re-render everything" into "patch only what actually changed," which is the whole performance argument for the abstraction.
