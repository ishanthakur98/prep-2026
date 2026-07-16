# JavaScript — Async JavaScript

[Day 4's JS doc](../day4/04-javascript-fundamentals.md) briefly named the event loop in passing; today is the full picture — the mechanism, then Promises/`async`-`await` as the syntax built on top of it.

## Part 1 — Concepts (read this first)

### The problem async JS solves
JavaScript runs on a **single thread** — one call stack, one thing executing at a time. But a browser/Node still needs to handle slow operations (network requests, timers, file I/O) without freezing everything else while waiting. The answer isn't multiple threads — it's handing slow work off to the **runtime** (browser Web APIs, or Node's libuv), letting the single thread keep executing other code, and running a callback later when that work finishes. The **event loop** is the mechanism that decides *when* "later" actually is.

### Call Stack
The single stack of function calls currently executing — same LIFO structure as any call stack (see [day 4's recursion doc](../day4/01-dsa-recursion-backtracking.md#stack-frames) for the general concept). Synchronous code runs here, one frame at a time, top to bottom. As long as the call stack has anything on it, the event loop does **nothing** — this is "JS is single-threaded" in concrete terms: a long-running synchronous function genuinely blocks everything else, including UI rendering and any pending async callbacks, until it returns.

### Callback Queue (macrotasks) and Microtask Queue
When an async operation (started via a Web API/runtime API — `setTimeout`, a network request, a file read) completes, its callback doesn't jump straight onto the call stack — it's placed on a queue, waiting for the call stack to be empty:
- **Callback Queue / macrotask queue**: `setTimeout`/`setInterval` callbacks, I/O callbacks, UI event callbacks.
- **Microtask queue**: Promise `.then`/`.catch`/`.finally` callbacks, `queueMicrotask()`, `async`/`await` continuations (an `await` is sugar over a `.then`, so it queues a microtask too).

### The Event Loop — the actual algorithm
Repeats forever:
1. Run everything currently on the **call stack** until it's empty (one synchronous "task" fully to completion).
2. Drain the **entire microtask queue** — run every microtask, including any *new* microtasks queued by ones that just ran, until the microtask queue is completely empty.
3. Take **one** task from the macrotask/callback queue, push it onto the call stack, run it to completion (which may queue more microtasks).
4. Go back to step 2 (drain microtasks again) before taking the next macrotask.

**The critical, interview-favorite detail**: microtasks are drained **completely** between every single macrotask — not "one microtask per loop tick." This is exactly why a resolved Promise's `.then` always runs before a `setTimeout(fn, 0)`, even a zero-delay one: the Promise callback is a microtask (runs before the next macrotask is even considered), while `setTimeout` — no matter how short its delay — always queues a macrotask.
```js
console.log("1: sync");

setTimeout(() => console.log("2: setTimeout"), 0);        // macrotask

Promise.resolve().then(() => console.log("3: promise"));   // microtask

console.log("4: sync");

// Output: 1, 4, 3, 2
// Sync code (call stack) always finishes first: "1", "4".
// Then microtasks drain fully before the next macrotask: "3".
// Only then does the macrotask queue get its turn: "2".
```

### Promises
An object representing the eventual result of an async operation, in one of three states: **pending** → **fulfilled** (resolved with a value) or **rejected** (failed with a reason) — a settled Promise can never change state again.
```js
function fetchMockUser(id) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (id <= 0) {
        reject(new Error("invalid id"));
      } else {
        resolve({ id, name: "Alice" });
      }
    }, 100);
  });
}

fetchMockUser(1)
  .then((user) => {
    console.log("got user:", user);
    return user.name;                          // value returned from .then becomes the next .then's input
  })
  .then((name) => console.log("name was:", name))
  .catch((err) => console.error("failed:", err.message))  // catches a rejection from ANY earlier link in the chain
  .finally(() => console.log("done, either way"));
```
Each `.then()` returns a **new** Promise, which is what makes chaining work — the value/Promise returned inside a `.then` callback becomes the resolved value the *next* `.then` receives. A single `.catch` at the end of a chain catches a rejection from **any** earlier step, not just the immediately preceding one — rejections propagate down the chain, skipping `.then` handlers, until a `.catch` (or a rejection handler passed as `.then`'s second argument) handles it.

### `async`/`await`
Syntax sugar over Promises that lets async code *read* like synchronous code, without changing the underlying mechanism — an `async function` always returns a Promise, and `await` pauses that function's execution (not the whole thread — other code keeps running) until the awaited Promise settles.
```js
async function loadUserName(id) {
  const user = await fetchMockUser(id);   // pauses here until the promise settles, unwraps the resolved value
  return user.name;                        // wrapped automatically in the Promise this async function returns
}
```
Nothing here is a different execution model from Promises — `await somePromise` is mechanically equivalent to putting the rest of the function in a `.then()` callback. The entire benefit is readability: avoiding a pyramid of nested `.then()` calls for sequential async steps, especially once error handling and conditionals are involved.

### Error handling with `try`/`catch`
Because `await` unwraps a Promise's resolved value or **throws** its rejection reason as a regular JS exception, ordinary `try`/`catch` works around `await` exactly like it would around any synchronous throwing call:
```js
async function loadUserNameSafe(id) {
  try {
    const user = await fetchMockUser(id);
    return user.name;
  } catch (err) {
    console.error("failed to load user:", err.message);
    return null;
  }
}
```
This is the main day-to-day advantage over raw Promise chains: one `try`/`catch` block reads naturally around several sequential `await`s, instead of a `.catch()` bolted onto the end of a `.then()` chain where it's less visually obvious which step could have failed.

---

## Part 2 — Practice: fetch mock user data

**Promise-based version**:
```js
function fetchUser(id) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (id === 404) {
        reject(new Error(`user ${id} not found`));
      } else {
        resolve({ id, name: `User${id}`, email: `user${id}@example.com` });
      }
    }, 200);
  });
}

function loadAndPrintUser(id) {
  fetchUser(id)
    .then((user) => {
      console.log("Loaded:", user);
      return user;
    })
    .catch((err) => {
      console.error("Error loading user:", err.message);
    });
}
```

**Same thing, rewritten with `async`/`await`**:
```js
async function loadAndPrintUserAsync(id) {
  try {
    const user = await fetchUser(id);
    console.log("Loaded:", user);
    return user;
  } catch (err) {
    console.error("Error loading user:", err.message);
  }
}

// Sequential vs parallel awaits -- a common follow-up:
async function loadTwoUsersSequential(id1, id2) {
  const u1 = await fetchUser(id1);   // waits fully before starting the next fetch
  const u2 = await fetchUser(id2);
  return [u1, u2];                    // total time ~= sum of both fetches
}

async function loadTwoUsersParallel(id1, id2) {
  const [u1, u2] = await Promise.all([fetchUser(id1), fetchUser(id2)]); // both start immediately
  return [u1, u2];                    // total time ~= the slower of the two fetches
}
```
**Why `Promise.all` matters here**: awaiting two independent fetches one after another (`loadTwoUsersSequential`) needlessly serializes work that doesn't depend on each other — each `await` blocks that function's continuation until its Promise settles, so the second fetch doesn't even *start* until the first finishes. `Promise.all` starts both operations immediately and waits for all of them together, which is strictly faster whenever the operations are independent. This distinction (accidentally-sequential vs intentionally-parallel `await`s) is one of the most common real-world async performance bugs.

---

## Part 3 — Interview Questions (today's round)

**Q: Explain the event loop at a high level.**
**A:** JS runs on a single call stack. Slow operations are handed off to the runtime (browser Web APIs / Node's libuv), which queues their completion callbacks rather than running them immediately — macrotasks (`setTimeout`, I/O) in one queue, microtasks (Promise callbacks, `async`/`await` continuations) in another. The event loop's job: run the call stack to empty, then fully drain the microtask queue (including any new microtasks queued while draining), then take exactly one macrotask, run it, and repeat — draining microtasks again before the next macrotask.

**Q: Difference between a Promise and `async`/`await`?**
**A:** They're the same underlying mechanism — `async`/`await` is syntax sugar over Promises, not a separate execution model. An `async function` always returns a Promise, and `await` is mechanically equivalent to registering the rest of the function as a `.then()` callback on the awaited Promise. The practical difference is readability: sequential async steps read like synchronous code with `await`, instead of a chain (or nested pyramid) of `.then()` calls — and error handling becomes ordinary `try`/`catch` instead of a bolted-on `.catch()`.

**Q: What are microtasks, and why does a resolved Promise's `.then` run before a `setTimeout(fn, 0)`?**
**A:** Microtasks are the queue Promise callbacks (`.then`/`.catch`/`.finally`, `await` continuations, `queueMicrotask()`) land in. The event loop fully drains the *entire* microtask queue after every macrotask (and after the initial synchronous run), before it even considers the next macrotask. `setTimeout`, regardless of delay, always queues a macrotask — so a `Promise.resolve().then(...)` registered even after a `setTimeout(fn, 0)` still runs first, because all pending microtasks are drained before that (or any) macrotask gets its turn.

**Q: If you `await` two independent async calls back to back, what's wrong with that, and how do you fix it?**
**A:** Each `await` pauses that function's continuation until its own Promise settles before the *next* line even starts — so two independent operations end up serialized (total time = sum of both), even though nothing about them actually depends on each other. Starting both immediately and awaiting them together with `Promise.all([...])` runs them concurrently, so total time is bounded by the slower one instead of the sum — the fix whenever the operations don't depend on each other's results.
