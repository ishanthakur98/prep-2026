# JavaScript — Foundations (before React)

## Part 1 — Concepts (read this first)

### `var` vs `let` vs `const`
| | `var` | `let` | `const` |
|---|---|---|---|
| Scope | Function-scoped | Block-scoped | Block-scoped |
| Hoisting behavior | Hoisted + initialized to `undefined` | Hoisted but **not** initialized (temporal dead zone) | Hoisted but **not** initialized (temporal dead zone) |
| Redeclaration | Allowed | Error | Error |
| Reassignment | Allowed | Allowed | Error (but object/array *contents* are still mutable — `const` freezes the binding, not the value) |

```js
if (true) {
  var x = 1;
  let y = 2;
}
console.log(x); // 1 — var leaked out of the block, it's function-scoped
console.log(y); // ReferenceError — y doesn't exist outside its block
```
Default to `const` everywhere; use `let` only when a variable must be reassigned; avoid `var` in new code — its function-scoping (rather than block-scoping) is the source of most classic "why did my loop variable do that" JS bugs (see the closures example below).

### Functions: declaration vs expression vs arrow
```js
// Function declaration — hoisted fully (can call before the line it's defined on)
function add(a, b) { return a + b; }

// Function expression — NOT hoisted (the variable is hoisted, but stays undefined until this line runs)
const subtract = function (a, b) { return a - b; };

// Arrow function — concise, and does NOT have its own `this` (see below)
const multiply = (a, b) => a * b;
```
Practical difference beyond syntax: arrow functions don't get their own `this`, `arguments`, or `super` — they close over the enclosing scope's, which is exactly why they're preferred for callbacks inside methods/classes (see `this` section below).

### Scope
Three layers, checked innermost-to-outermost when resolving a variable name (**lexical scoping** — determined by where code is *written*, not where it's *called from*):
- **Global**: declared outside any function/block — accessible everywhere, and pollutes the global object in non-module scripts.
- **Function**: declared with `var` inside a function — visible anywhere in that function, regardless of nested blocks.
- **Block**: declared with `let`/`const` inside `{ }` (an `if`, `for`, or bare block) — visible only within that block.

### Hoisting
Before executing a scope's code, the JS engine does a pass that registers all `var` and `function` declarations in that scope, making the *names* available from the top — but the *behavior* differs sharply:
- `function foo() {}` (declaration) — the entire function is hoisted, usable before its textual position.
- `var x` — the name is hoisted and initialized to `undefined`; reading it before its assignment line gives `undefined`, not an error.
- `let`/`const` — the name is hoisted but left **uninitialized**; reading it before the declaration line throws `ReferenceError: Cannot access 'x' before initialization` (the "temporal dead zone"). This is stricter/safer than `var`'s silent `undefined`.

### Closures (very important)
A closure is a function that **remembers the variables from the scope it was created in**, even after that outer scope has finished executing. This isn't a special syntax — it's just how JS functions always work (lexical scoping), but it becomes visible/useful specifically when an inner function outlives its outer function's execution (returned, passed as a callback, stored somewhere).

```js
function makeCounter() {
  let count = 0;                 // captured by the closure
  return function () {
    count++;                     // reads/writes the SAME count on every call
    return count;
  };
}

const counter = makeCounter();
console.log(counter()); // 1
console.log(counter()); // 2 — count persisted between calls, private to this counter instance
```
`makeCounter()`'s stack frame is technically "gone" once it returns, but `count` stays alive because the returned inner function still references it — the JS engine keeps it alive via the closure instead of garbage-collecting it. Each call to `makeCounter()` creates a **new, independent** `count` — closures capture variables, not values, and each invocation gets its own scope.

**Classic interview gotcha — `var` in a loop**:
```js
for (var i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 0);
}
// prints 3, 3, 3 — all three closures share the SAME function-scoped `i`,
// which has already finished looping (i === 3) by the time the callbacks run

for (let j = 0; j < 3; j++) {
  setTimeout(() => console.log(j), 0);
}
// prints 0, 1, 2 — `let` creates a NEW block-scoped binding of `j` per iteration,
// so each closure captures its own snapshot
```
This is the single most common closures interview question — know *why* `let` fixes it (per-iteration binding), not just *that* it does.

### `this` keyword
`this` is determined by **how a function is called**, not where it's defined — with one big exception: arrow functions.
| Call style | `this` refers to |
|---|---|
| `obj.method()` | `obj` |
| Plain function call `fn()` | `undefined` in strict mode / modules (was the global object in non-strict sloppy mode) |
| `new Fn()` | The newly created instance |
| `fn.call(ctx)` / `fn.apply(ctx)` / `fn.bind(ctx)` | Explicitly `ctx` |
| Arrow function | Whatever `this` was in the **enclosing lexical scope** at the point the arrow was defined — arrows never have their own `this` |

```js
const obj = {
  name: "Alice",
  greetRegular: function () {
    setTimeout(function () {
      console.log(this.name); // undefined — plain function loses obj's `this`
    }, 0);
  },
  greetArrow: function () {
    setTimeout(() => {
      console.log(this.name); // "Alice" — arrow captures the enclosing method's `this`
    }, 0);
  },
};
```
This is exactly why arrow functions are the default choice for callbacks inside class methods/objects — they don't need `.bind(this)` gymnastics to keep the right `this`.

---

## Part 2 — Practice programs

### Reverse a string
```js
function reverseString(str) {
  return str.split("").reverse().join("");
}
// or without built-ins, to show you understand the mechanics:
function reverseStringManual(str) {
  let result = "";
  for (let i = str.length - 1; i >= 0; i--) {
    result += str[i];
  }
  return result;
}
console.log(reverseString("hello")); // "olleh"
```

### Find duplicates
```js
function findDuplicates(arr) {
  const seen = new Set();
  const duplicates = new Set();
  for (const item of arr) {
    if (seen.has(item)) duplicates.add(item);
    else seen.add(item);
  }
  return [...duplicates];
}
console.log(findDuplicates([1, 2, 3, 2, 4, 1])); // [2, 1]
```
`Set` lookups are O(1) average, same reasoning as using a `HashMap`/`HashSet` in Java for this same problem — `O(n)` total instead of the `O(n^2)` of nested-loop comparison.

### Implement debounce (basic)
```js
function debounce(fn, delayMs) {
  let timeoutId;
  return function (...args) {
    clearTimeout(timeoutId);                 // cancel any pending call
    timeoutId = setTimeout(() => fn.apply(this, args), delayMs);
  };
}

// usage: only fires 300ms after the user STOPS typing, not on every keystroke
const debouncedSearch = debounce((query) => console.log("Searching:", query), 300);
```
**Why this is a closures example**: `timeoutId` is captured by the returned function and persists across calls — exactly the `makeCounter` pattern above, just used to track "is there a pending call I should cancel" instead of a counter. This is the standard mechanism behind search-as-you-type inputs, resize handlers, and button double-click prevention.

### Flatten an array
```js
function flatten(arr) {
  return arr.reduce((flat, item) => {
    return flat.concat(Array.isArray(item) ? flatten(item) : item);
  }, []);
}
console.log(flatten([1, [2, [3, 4], 5], 6])); // [1, 2, 3, 4, 5, 6]
// built-in equivalent for arbitrary depth: arr.flat(Infinity)
```
Recursion connects directly to today's DSA topic ([01-dsa-recursion-backtracking.md](01-dsa-recursion-backtracking.md)): the base case is "not an array" (return the item as-is), the recursive case is "flatten this nested array and merge it in."

---

## Part 3 — Interview Questions (today's round)

**Q: What is a closure?**
**A:** A function bundled together with references to the variables from the lexical scope it was defined in, such that it can still read/write those variables even after the outer function has returned. It's not a special mechanism — it's the natural consequence of JS's lexical scoping combined with functions being first-class values that can be returned or passed around. The practical use is **private state** (like `makeCounter`'s `count`, or `debounce`'s `timeoutId`) that persists across calls without being exposed globally.

**Q: Difference between `==` and `===`?**
**A:** `===` (strict equality) compares value **and type**, no conversion — `1 === "1"` is `false`. `==` (loose equality) performs **type coercion** before comparing if the operands have different types, following a set of conversion rules that are notoriously easy to get wrong (`"" == 0` is `true`, `null == undefined` is `true` but `null == 0` is `false`, `[] == false` is `true`). Default to `===` always; the coercion rules of `==` are a well-known source of subtle bugs and are rarely what you actually want.

**Q: Explain the event loop at a high level.**
**A:** JavaScript runs on a **single thread**, so it can't literally do two things simultaneously — the event loop is what lets it *appear* asynchronous without blocking. Synchronous code runs on the **call stack** immediately. Async operations (timers, network I/O, DOM events) are handed off to the browser/Node runtime (not the JS engine itself), and when they complete, their callback is placed on a **queue** — the **microtask queue** (Promise `.then`/`.catch`, `queueMicrotask`) or the **macrotask/callback queue** (`setTimeout`, `setInterval`, I/O). The event loop's job is simple: continuously check "is the call stack empty?" — if yes, pull the next task off a queue and push it onto the stack to run. Microtasks are always fully drained before the next macrotask runs, which is why a `Promise.resolve().then(...)` fires before a `setTimeout(..., 0)` even though both are "async" — they queue into different queues with different priority.

---

## Practice separately (not covered above, worth doing before Day 5)
- Implement `debounce`'s sibling, `throttle` (fires at most once per interval, instead of waiting for a quiet period).
- Implement a basic `Promise` chain manually (`.then` returning a new promise) to solidify how microtasks compose.
