# Mock Interview — Rapid Fire (answer before reading)

Rules: cover each question out loud, from memory, before reading the answer below it. If you hesitate more than a few seconds, that's a signal to re-read the relevant doc from today, not to peek immediately.

---

## Java

**Q: Explain the JVM memory model at a high level.**
**A:** Class Loader Subsystem loads/links/initializes `.class` files; Runtime Data Areas hold the actual memory in use — Heap (shared, generational: Young/Old) for objects, per-thread Stack for method call frames, Metaspace (native memory) for class metadata, plus the per-thread PC Register and Native Method Stack; the Execution Engine (interpreter, JIT, GC) runs the bytecode against those areas. Full picture: [02-java-jvm-gc-string-pool.md](02-java-jvm-gc-string-pool.md), building on [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md).

**Q: Why are Strings immutable?**
**A:** Four reasons converge: safe pooling (a shared literal can't be mutated out from under other holders), thread safety (no synchronization needed to share something that can't change), security (prevents time-of-check/time-of-use bugs on strings like class names/paths), and safe hashcode caching (`String.hashCode()` caches its result once, which is only valid if content can never change — critical since strings are common `HashMap` keys). Full answer: [02-java-jvm-gc-string-pool.md](02-java-jvm-gc-string-pool.md#part-3--interview-questions-todays-round).

**Q: What is Metaspace?**
**A:** Native-memory (off-heap) region storing class metadata (definitions, method bytecode, constant pool structure) — replaced PermGen (which lived in the fixed-size heap and caused frequent OOMs under class-loading churn) since Java 8, growing dynamically by default instead of hitting a small fixed ceiling.

**Q: Explain G1 GC at a high level.**
**A:** Divides the heap into many equal-sized regions instead of one contiguous Young/Old space, dynamically labeling each region Eden/Survivor/Old as needed, and on each collection picks the garbage-heaviest regions first (hence "Garbage First") to hit a configurable max pause-time goal — an evolution of the generational Young/Old split, not a replacement of it. Full answer: [02-java-jvm-gc-string-pool.md](02-java-jvm-gc-string-pool.md#part-3--interview-questions-todays-round).

---

## Spring

**Q: What is the Persistence Context?**
**A:** Hibernate's per-transaction tracked set of managed entities — a mandatory first-level cache (same ID within one context always returns the same object reference, no repeat `SELECT`) that also enables automatic dirty checking: field changes on a managed entity are diffed against a loaded snapshot and turned into `UPDATE`s at flush time, with no explicit save call needed for the update itself. Full answer: [03-springboot-jpa-persistence-context.md](03-springboot-jpa-persistence-context.md).

**Q: What causes the N+1 query problem?**
**A:** Fetching a list of parent entities in one query, then separately, lazily triggering one additional query per parent when a lazy association is accessed on each one in a loop — `N` extra queries on top of the original 1. Switching everything to `EAGER` isn't a real fix; it just forces that join cost onto every query touching the entity. The real fix: `JOIN FETCH` or `@EntityGraph` on the specific queries that actually need the association eagerly. Full answer: [03-springboot-jpa-persistence-context.md](03-springboot-jpa-persistence-context.md#the-n1-query-problem).

**Q: Explain lazy loading.**
**A:** A `LAZY`-fetched association isn't queried as part of the original `SELECT` — Hibernate returns a proxy, and the real query fires the first time that association is actually accessed. If that access happens after the persistence context/session has closed (e.g. in a controller after the `@Transactional` service method returned), it throws `LazyInitializationException`, a very common real-world Spring bug.

---

## JavaScript

**Q: Explain the event loop.**
**A:** JS runs on one call stack; slow operations are handed to the runtime, which queues completion callbacks — macrotasks (`setTimeout`, I/O) in one queue, microtasks (Promise callbacks, `await` continuations) in another. The loop runs the stack to empty, then fully drains the *entire* microtask queue (including new microtasks queued while draining), then takes exactly one macrotask and repeats. Full answer: [04-javascript-async.md](04-javascript-async.md#the-event-loop--the-actual-algorithm).

**Q: Difference between a Promise and `async`/`await`?**
**A:** Same underlying mechanism — `async`/`await` is syntax sugar over Promises, not a separate model. An `async function` always returns a Promise, and `await` is mechanically equivalent to putting the rest of the function in a `.then()` callback. The benefit is purely readability: sequential async steps read like sync code, and error handling becomes ordinary `try`/`catch`. Full answer: [04-javascript-async.md](04-javascript-async.md#part-3--interview-questions-todays-round).

**Q: What are microtasks?**
**A:** The queue Promise-related callbacks land in (`.then`/`.catch`/`.finally`, `await` continuations, `queueMicrotask()`). The event loop drains this queue **completely** after every macrotask (and after the initial sync run) before even considering the next macrotask — which is why a resolved Promise's `.then` always beats a `setTimeout(fn, 0)`.

---

## React

**Q: When does `useEffect` run?**
**A:** After React renders the component and commits the result to the actual DOM — never during rendering. Whether it runs *again* on a later render depends on its dependency array: omitted = every render, `[]` = once after the first render only, `[a, b]` = after the first render and again whenever `a` or `b` changes (compared via `Object.is`). Full answer: [05-react-hooks.md](05-react-hooks.md#part-3--interview-questions-todays-round).

**Q: Why does changing state trigger a re-render?**
**A:** Calling a `useState` setter tells React the value this component should render with has changed, so React schedules a re-run of the component function and diffs the resulting Virtual DOM against the previous tree to patch only what changed in the real DOM. Directly mutating a value in place — without going through the setter — never triggers this, which is why state updates always create new references.

**Q: What's the difference between props and state?**
**A:** Props flow **into** a component from its parent and are read-only to the receiver; state is data a component **owns and can change itself**. Without state, a component could only ever reflect exactly what its parent passed down — nothing could represent the component's own changing view (today's `loading`/`error`/`employees`, none of which come from a parent).

---

## Self-grading

For each answer, ask: did I state the *mechanism* (what actually happens internally, and why), or did I just restate the definition? "N+1 is when you make too many queries" is a definition. "N+1 happens because a lazily-loaded association triggers a separate query per parent row in a loop, and switching to EAGER doesn't fix it because it just moves the same join cost onto every query touching that entity" is the mechanism — that's the level to aim for.
