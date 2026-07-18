# JavaScript — Objects & Prototypes

Builds on [../day4/04-javascript-fundamentals.md](../day4/04-javascript-fundamentals.md), which already introduced `this` (call-site-determined, with arrow functions as the lexical exception) and closures. Today goes deeper: **how JS objects actually share behavior** (prototypes, since JS has no real "classes" underneath), and turns yesterday's `this` table into full working command of `call`/`apply`/`bind`.

## Part 1 — Concepts (read this first)

### Objects, recap
A JS object is a mutable, unordered (well — insertion-ordered for string keys in practice) collection of key/value pairs, where values can be primitives, other objects, or functions:
```js
const employee = {
  name: "Alice",
  title: "Engineer",
  greet() {                       // shorthand method syntax
    return `Hi, I'm ${this.name}`;
  },
};
```
There's no separate "class" construct at the runtime level — even `class Foo { ... }` syntax (ES6+) is **syntactic sugar** over the exact same prototype mechanism below; it doesn't introduce a genuinely different object model, just a friendlier syntax over it.

### The Prototype Chain
Every JS object has an internal, hidden link — `[[Prototype]]`, accessible via `Object.getPrototypeOf(obj)` or (legacy, avoid in real code) `obj.__proto__` — pointing to **another object** it delegates to. When you read `obj.someProperty`, the engine:
1. Checks if `obj` has `someProperty` as an **own property**.
2. If not, follows `obj`'s `[[Prototype]]` link and checks *that* object.
3. Repeats, walking up the chain, until either the property is found or the chain ends at `null` (`Object.prototype`'s own prototype is `null` — the chain always terminates there).

```
myEmployee  --[[Prototype]]-->  Employee.prototype  --[[Prototype]]-->  Object.prototype  --[[Prototype]]--> null
```
This is **how method sharing works without copying**: every object created via a given constructor/class shares **one** prototype object holding the methods, rather than each instance carrying its own copy of every method — a real memory and creation-time saving once you have many instances.

```js
function Employee(name, title) {
  this.name = name;              // own property, unique per instance
  this.title = title;
}
Employee.prototype.greet = function () {   // ONE shared function, not copied per instance
  return `Hi, I'm ${this.name}, a ${this.title}`;
};

const alice = new Employee("Alice", "Engineer");
const bob = new Employee("Bob", "Designer");
console.log(alice.greet === bob.greet);    // true -- same function object, found via the prototype chain
```

**`new` keyword, precisely what it does** (this is a very common "explain it" interview question):
1. Creates a brand-new empty object.
2. Sets that new object's `[[Prototype]]` to `Employee.prototype`.
3. Calls `Employee` with `this` bound to the new object (`Employee.call(newObj, name, title)`).
4. Returns the new object (unless the constructor explicitly returns its own object, which overrides this).

### `class` syntax is prototype sugar
```js
class Employee {
  constructor(name, title) {
    this.name = name;
    this.title = title;
  }
  greet() {                       // ends up on Employee.prototype, exactly like above
    return `Hi, I'm ${this.name}, a ${this.title}`;
  }
}
```
`typeof Employee === "function"` — a class *is* a function under the hood, and instance methods defined inside it are installed onto `Employee.prototype`, identical to the manual version above. `extends`/`super` walk the same `[[Prototype]]` chain (a subclass's `prototype`'s `[[Prototype]]` points at the parent class's `prototype`) rather than being a separate inheritance mechanism.

### `Object.create()`
Builds an object with an explicitly chosen prototype, bypassing constructor functions entirely — the most direct way to see that prototypal inheritance doesn't require classes or constructors at all:
```js
const employeeMethods = {
  greet() { return `Hi, I'm ${this.name}`; },
};
const alice = Object.create(employeeMethods);
alice.name = "Alice";
alice.greet(); // "Hi, I'm Alice" -- found via the prototype chain, no constructor involved
```

### `this`, recap + why prototypes need it
[Day 4's table](../day4/04-javascript-fundamentals.md#this-keyword) covers the call-site rules. The prototype chain is *exactly why* shared methods need `this` at all: `greet()` lives on `Employee.prototype`, **one object shared by every instance** — the only way it can return the *correct* instance's name is by looking at `this` (bound fresh per call, based on how it was invoked — `alice.greet()` binds `this` to `alice`), rather than closing over a specific name at definition time the way a closure would.

### `call()`, `apply()`, `bind()`
All three exist to **explicitly control what `this` is** inside a function call, overriding the normal call-site rule:
```js
function greet(greeting) {
  return `${greeting}, I'm ${this.name}`;
}
const alice = { name: "Alice" };
const bob = { name: "Bob" };

greet.call(alice, "Hi");        // "Hi, I'm Alice" -- args passed individually
greet.apply(bob, ["Hello"]);    // "Hello, I'm Bob" -- args passed as an array
const greetAsAlice = greet.bind(alice);  // returns a NEW function, permanently bound to alice
greetAsAlice("Hey");            // "Hey, I'm Alice" -- can be called later, this is locked in
```
| | Invokes immediately? | Args | Returns |
|---|---|---|---|
| `call(thisArg, a, b, ...)` | Yes | Listed individually | The function's return value |
| `apply(thisArg, [a, b, ...])` | Yes | As a single array | The function's return value |
| `bind(thisArg, a, b, ...)` | **No** | Listed individually (partial application) | A **new function**, `this` (and any given args) permanently fixed, to be called whenever |

**Why `bind` matters beyond just fixing `this`**: it's how you pass an object method as a callback without losing its `this` in pre-arrow-function code — `setTimeout(obj.method.bind(obj), 1000)` — because a plain function reference (`setTimeout(obj.method, ...)`) loses the object context entirely (per Day 4's plain-function-call rule: `this` becomes `undefined`). Arrow functions solved this more ergonomically for *new* code, but `bind`/`call`/`apply` remain essential for working with existing function references (borrowing an array method for an array-like object, partial application, etc.) and are a near-guaranteed interview topic regardless.

---

## Part 2 — Practice

### A simple object with shared methods
```js
const employeePrototype = {
  greet() {
    return `Hi, I'm ${this.name}, a ${this.title}`;
  },
  raiseSalary(percent) {
    this.salary = Math.round(this.salary * (1 + percent / 100));
    return this.salary;
  },
};

function createEmployee(name, title, salary) {
  const employee = Object.create(employeePrototype);  // shared methods via the prototype chain
  employee.name = name;
  employee.title = title;
  employee.salary = salary;
  return employee;
}

const alice = createEmployee("Alice", "Engineer", 100000);
const bob = createEmployee("Bob", "Designer", 90000);
console.log(alice.greet());              // "Hi, I'm Alice, a Engineer"
console.log(alice.raiseSalary(10));      // 110000
console.log(alice.greet === bob.greet);  // true -- one shared function, not duplicated per instance
```

### Debounce (attempt cold, then compare)
Already covered in [Day 4](../day4/04-javascript-fundamentals.md#implement-debounce-basic) — today's instruction is to write it again **without looking**, as a recall check, then diff against your memory of the closures reasoning. Reference implementation:
```js
function debounce(fn, delayMs) {
  let timeoutId;
  return function (...args) {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn.apply(this, args), delayMs);
  };
}
```
Note the `fn.apply(this, args)` inside — this is today's `call`/`apply` material actually earning its keep: the returned wrapper function needs to forward *both* whatever `this` it was itself called with, and the original arguments, on to `fn`, exactly once the delay elapses. If `debounce` wraps an object method (`obj.search = debounce(obj.search, 300)`), losing `this` here would silently break `search`'s access to `obj`'s own fields — `apply` is what preserves it.

---

## Part 3 — Interview Questions (today's round)

**Q: What is the prototype chain, and why does JavaScript use it instead of classical inheritance?**
**A:** Every object holds an internal link (`[[Prototype]]`) to another object; reading a property that isn't found directly on the object falls through to its prototype, then *that* object's prototype, and so on until `null`. This is **delegation**, not copying — instances of the same constructor/class share one prototype object holding their methods, rather than each instance carrying a private copy of every method. JavaScript was designed this way (prototypal, not classical/class-based like Java) because it's a simpler, more dynamic model — objects can be created and given behavior directly (`Object.create`) without needing a class blueprint to exist first, and prototypes can even be reassigned or extended at runtime. `class` syntax (ES6+) is sugar over exactly this mechanism, not a different one underneath.

**Q: Walk through exactly what the `new` keyword does.**
**A:** Given `new Employee(name, title)`: (1) a new, empty object is created; (2) its `[[Prototype]]` is set to `Employee.prototype`, which is how the new object gets access to shared methods; (3) `Employee` is invoked with `this` bound to that new object, so `this.name = name` assigns onto it; (4) the new object is returned automatically, unless the constructor function explicitly returns a different object itself (returning a primitive from a constructor is ignored; returning an object overrides the default `this`). Skipping `new` on an old-style constructor function silently runs it as a plain function call instead — `this` then follows the normal call-site rule (`undefined` in strict mode) instead of being the new instance, a classic old-JS footgun that `class` syntax fixes by throwing if called without `new`.

**Q: Difference between `call()`, `apply()`, and `bind()`?**
**A:** All three explicitly set what `this` is inside a function call, overriding the normal call-site-determined rule. `call`/`apply` both invoke the function **immediately**, differing only in how extra arguments are passed (individually for `call`, as one array for `apply`). `bind` does **not** invoke immediately — it returns a brand-new function with `this` (and optionally some leading arguments, via partial application) permanently fixed, to be called whenever later. The practical use case for `bind` specifically: passing an object's method as a callback (to `setTimeout`, an event listener, `Array.map`) without it losing its `this` when called later as a bare function reference.

**Q: What does `this` refer to inside a method defined on a shared prototype, and why does that even work given the method is shared by every instance?**
**A:** `this` is resolved fresh at **call time** based on how the method was invoked (`alice.greet()` → `this` is `alice`), not at the time the method was defined — this is precisely what makes one shared prototype method correct for every instance: `Employee.prototype.greet` is a single function object, but every call to it supplies its own `this` based on which instance it was called *on* (`obj.method()` binds `this` to `obj`, per [Day 4's rule table](../day4/04-javascript-fundamentals.md#this-keyword)). If methods instead closed over a specific instance the way a closure captures a variable, one shared method could never correctly serve multiple different instances — `this` being calculated per-call is the mechanism that makes prototype-based method sharing work at all.
