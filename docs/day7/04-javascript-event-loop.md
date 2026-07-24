# JavaScript — Event Loop (Deep Dive)

[Day 4's fundamentals doc](../day4/04-javascript-fundamentals.md#q-explain-the-event-loop-at-a-high-level) already covers the event loop at a high level (call stack, queues, "microtasks before the next macrotask"). Today goes one level deeper into each piece individually, then drills it with ten predict-the-output snippets — the single most common way this topic gets tested in an actual interview.

## Part 1 — Concepts (read this first)

### Call Stack
The single-threaded LIFO stack of function calls currently executing. JS can only ever do **one thing at a time** on this stack — there is no true parallelism inside the JS engine itself. A function call pushes a frame; returning pops it. "Blocking the event loop" always means: something is on the call stack that won't finish/pop (an infinite loop, a huge synchronous computation) — while anything is on the stack, **nothing** else (no queued callback, no re-render, no UI update) can run, because the loop's very first rule is "only pull from a queue when the stack is empty."

### Web APIs (the browser/Node runtime, not the JS engine)
`setTimeout`, DOM events, `fetch`, file I/O — none of these are part of the JavaScript language itself; they're provided by the **host environment** (the browser, or Node's libuv). When your code calls `setTimeout(fn, 1000)`, the JS engine doesn't sit there waiting — it hands the timer off to the host environment (a genuinely separate, often multi-threaded facility) and immediately continues executing the next line. The host environment tracks the timer independently and, only once it fires, hands the callback **back** to JS by placing it on a queue — never by pushing it directly onto the call stack itself, which would violate single-threadedness. This handoff is the entire mechanism that makes JS "async without being multi-threaded" — the concurrency lives in the host environment, not in the JS engine.

### Callback Queue (a.k.a. Macrotask Queue)
Where completed Web API work waits to actually run: `setTimeout`/`setInterval` callbacks, DOM event callbacks (click, etc.), I/O completion callbacks in Node. **FIFO** — first task queued (once its timer/event has actually fired) is the first one run. Critically: **one macrotask runs to completion, then the entire microtask queue is fully drained, before the next macrotask starts** — this ordering rule is the source of nearly every "surprising" event-loop output.

### Microtask Queue (a.k.a. Promise Queue — same queue, two common names)
Where `Promise` continuations (`.then`/`.catch`/`.finally`) and `queueMicrotask()` callbacks wait. Also FIFO, but with **strictly higher priority** than the macrotask queue: after every single task (the initial synchronous script, or any one macrotask) finishes, the event loop **fully drains the microtask queue** — including any *new* microtasks that queued themselves while earlier ones were running — before it's even allowed to look at the macrotask queue again. This is why a chain of `.then().then().then()` all finishes before a single `setTimeout(..., 0)` fires, no matter how deeply chained.

### The loop itself
```
loop forever:
    if call stack is empty:
        drain the entire microtask queue (run each one; if it queues more, run those too, fully)
        if call stack is empty and macrotask queue is non-empty:
            dequeue ONE macrotask, push it onto the call stack, let it run to completion
            (then immediately loop back to the top: drain microtasks again)
```
The single sentence version: **synchronous code, then drain all microtasks, then one macrotask, then drain all microtasks again, then the next macrotask — forever.**

### `setTimeout(fn, delay)`
Schedules `fn` as a macrotask, **no sooner than** `delay` milliseconds from now — `delay` is a *minimum*, not a guarantee, since the callback still has to wait for both the timer to fire *and* the call stack to be empty *and* its turn in the macrotask queue. `setTimeout(fn, 0)` does **not** mean "run immediately" — it means "run as soon as possible after all currently-queued synchronous code and all currently-pending microtasks have finished," which is routinely a very different thing.

### `Promise.then()` and `queueMicrotask()`
`promise.then(cb)` schedules `cb` as a microtask **the moment the promise settles** (immediately, if it's already settled when `.then` is called — which is why `Promise.resolve().then(cb)` still queues `cb` asynchronously rather than running it synchronously right there). `queueMicrotask(cb)` does the same thing directly, with no promise involved at all — useful when you specifically want "run this after the current synchronous code, but before any macrotask," without the ceremony of wrapping it in a `Promise`.

### `async`/`await` — sugar over the same queues
An `async function` always returns a `Promise`. Code before the first `await` runs **synchronously**, exactly like any normal function call. `await somePromise` is where the function actually **suspends** — execution returns to the caller immediately, and everything *after* the `await` becomes a microtask-queued continuation, scheduled to resume once the awaited promise settles. `await` on an already-resolved value still yields at least one microtask tick — it never continues synchronously past an `await`, even if there's technically nothing to "wait" for.

---

## Part 2 — Practice: predict the output (answer before reading)

Cover each answer, read the snippet, write down the exact printed order, then check yourself.

### 1
```js
console.log('1');
setTimeout(() => console.log('2'), 0);
console.log('3');
```
<details><summary>Answer</summary>

`1, 3, 2` — synchronous code always finishes before any macrotask gets a turn, regardless of the `0`ms delay.
</details>

### 2
```js
console.log('1');
setTimeout(() => console.log('2'), 0);
Promise.resolve().then(() => console.log('3'));
console.log('4');
```
<details><summary>Answer</summary>

`1, 4, 3, 2` — sync first (`1`, `4`), then the microtask queue drains (`3`), then the one pending macrotask runs (`2`).
</details>

### 3
```js
console.log('A');
Promise.resolve().then(() => console.log('B')).then(() => console.log('C'));
Promise.resolve().then(() => console.log('D'));
console.log('E');
```
<details><summary>Answer</summary>

`A, E, B, D, C` — sync first. Both first-level `.then`s (`B`'s and `D`'s) are already queued by the time sync code finishes, in that order, so they run `B` then `D`. Only once `B` actually *finishes* does its chained `.then` (`C`) get appended to the *end* of the microtask queue — so `C` runs last, after `D`, not right after `B`.
</details>

### 4
```js
setTimeout(() => {
  console.log('timeout1');
  Promise.resolve().then(() => console.log('promise inside timeout'));
}, 0);

Promise.resolve().then(() => {
  console.log('promise1');
  setTimeout(() => console.log('timeout inside promise'), 0);
});

console.log('sync');
```
<details><summary>Answer</summary>

`sync, promise1, timeout1, promise inside timeout, timeout inside promise`

Sync code finishes first (`sync`). Microtask queue drains: `promise1` runs, and while running it *schedules a new macrotask* (`timeout inside promise`) — but scheduling a macrotask doesn't let it jump the queue; the microtask queue is empty now, so the loop moves to macrotasks. First macrotask in the queue is the original one (`timeout1`), which runs and itself queues a new microtask (`promise inside timeout`) — that microtask queue is fully drained (`promise inside timeout` runs) *before* the second macrotask (`timeout inside promise`, queued later) gets its turn.
</details>

### 5
```js
async function foo() {
  console.log('foo start');
  await null;
  console.log('foo end');
}

console.log('script start');
foo();
console.log('script end');
```
<details><summary>Answer</summary>

`script start, foo start, script end, foo end` — `foo()` runs synchronously up to `await null`; the `await` suspends and returns control to the caller immediately (even though there's nothing to actually wait on), so `script end` logs before `foo` resumes as a queued microtask.
</details>

### 6
```js
console.log('1');
setTimeout(() => console.log('2'), 0);

async function asyncFunc() {
  console.log('3');
  await new Promise(resolve => setTimeout(resolve, 0));
  console.log('4');
}
asyncFunc();

console.log('5');
```
<details><summary>Answer</summary>

`1, 3, 5, 2, 4`

`asyncFunc()` runs synchronously up to the `await`, logging `3`; the `new Promise(...)`'s executor runs synchronously too, registering a *second* `setTimeout` (to resolve the awaited promise) as a macrotask. Sync code finishes with `5`. Two macrotasks are now queued in registration order: the original one (logs `2`) and the resolve-the-promise one. The first runs, logging `2`. The second runs, which *resolves* the awaited promise — that resolution schedules `asyncFunc`'s continuation as a microtask, which runs immediately after (logging `4`).
</details>

### 7
```js
console.log('start');
queueMicrotask(() => console.log('microtask'));
setTimeout(() => console.log('timeout'), 0);
Promise.resolve().then(() => console.log('promise'));
console.log('end');
```
<details><summary>Answer</summary>

`start, end, microtask, promise, timeout` — `queueMicrotask` and `Promise.then` both land on the *same* microtask queue, in the order they were called (`microtask` before `promise`), and both run before the one macrotask (`timeout`).
</details>

### 8
```js
setTimeout(() => console.log('timeout'), 0);
Promise.resolve().then(() => {
  console.log('promise1');
  Promise.resolve().then(() => {
    console.log('promise2');
    Promise.resolve().then(() => console.log('promise3'));
  });
});
console.log('sync');
```
<details><summary>Answer</summary>

`sync, promise1, promise2, promise3, timeout` — each callback schedules the next one as a new microtask while the queue is being drained, and the drain keeps going until the queue is genuinely empty, however many levels deep. The macrotask (`timeout`) only gets a turn once no microtasks remain at all.
</details>

### 9
```js
console.log('start');
setTimeout(() => console.log('A'), 0);
setTimeout(() => console.log('B'), 0);
Promise.resolve().then(() => console.log('C'));
setTimeout(() => console.log('D'), 0);
console.log('end');
```
<details><summary>Answer</summary>

`start, end, C, A, B, D` — the microtask (`C`) runs before any macrotask. The three `setTimeout`s all share the same delay, so they run strictly in the order they were **registered** (`A, B, D`) — the macrotask queue is FIFO by queue-entry order, not re-sorted by anything once delays are equal.
</details>

### 10
```js
async function fetchData() {
  try {
    console.log('trying');
    await Promise.reject(new Error('fail'));
    console.log('after await'); // never reached
  } catch (err) {
    console.log('caught: ' + err.message);
  }
}

console.log('before');
fetchData();
console.log('after');
```
<details><summary>Answer</summary>

`before, trying, after, caught: fail` — `fetchData()` runs synchronously to the `await`, logging `trying`. `await`ing an already-rejected promise still suspends and yields to the caller (logging `after`) before the rejection is delivered back into the function as a microtask — where it's caught by the surrounding `try/catch`, exactly like a synchronous `throw` would be, just deferred to the microtask queue.
</details>

---

## Part 3 — Interview Discussion

**Q: Why do Promises execute before `setTimeout()` callbacks, even with `setTimeout(fn, 0)`?**
**A:** They're on two different queues with different priority: `setTimeout` always lands on the macrotask queue, `Promise.then`/`await` continuations always land on the microtask queue, and the event loop's rule is to fully drain the *entire* microtask queue — including any new microtasks that get added while draining — before it's even allowed to look at the macrotask queue for its next task. A `0`ms `setTimeout` is not "run immediately"; it's "queue on the lower-priority queue, to be considered only once every microtask, current and newly-spawned, has already run."

**Q: What would happen if a microtask kept scheduling more microtasks, forever?**
**A:** The macrotask queue — and with it, essentially everything else (UI rendering in a browser, any timer, any I/O callback) — would **starve indefinitely**, because the loop's rule is to fully drain the microtask queue before ever touching a macrotask, with no fairness mechanism forcing it to eventually yield. This is a real, documented footgun (an infinitely recursive `.then()` chain or `queueMicrotask` call) distinct from a plain infinite `while` loop, and worth naming as a "the event loop isn't magic, it has an actual starvation failure mode" answer.

**Q: Is `async/await` doing anything the Promise/microtask model doesn't already do?**
**A:** No — it's syntactic sugar over exactly the same `Promise` + microtask machinery described above. `await expr` desugars to roughly `expr.then(continueHere)`, where `continueHere` is everything in the function after the `await`, scheduled as a microtask once `expr` settles. Nothing about the underlying queue behavior changes; `async/await` only changes how that control flow *reads*, letting it look sequential instead of nested in `.then()` callbacks.
