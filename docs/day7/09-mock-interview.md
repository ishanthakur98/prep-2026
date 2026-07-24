# Mock Interview — Rapid Fire (answer before reading)

Rules: cover each question out loud, from memory, before reading the answer below it. If you hesitate more than a few seconds, that's a signal to re-read the relevant doc from today, not to peek immediately.

---

## Java

**Q: Explain the JVM class-loading process.**
**A:** Three phases, the first time a class is actively used: **Loading** (read the `.class` bytes, create a `Class` object) → **Linking** — verify (bytecode is structurally safe), prepare (allocate static fields, default values), resolve (symbolic references → real ones) → **Initialization** (static initializers run, top to bottom, source order). Loaded lazily, via three delegating loaders (Bootstrap → Platform → Application), each asking its parent first. Full answer: [../day5/02-java-jvm-gc-string-pool.md](../day5/02-java-jvm-gc-string-pool.md#class-loader).

**Q: What causes a memory leak in Java?**
**A:** The GC only reclaims unreachable objects — a leak is always an unintentional **strong reference** that outlives an object's real useful lifetime: an ever-growing static collection, listeners registered but never unregistered, a `ThreadLocal` not cleared on a pooled thread, or an unclosed native resource (file handle, connection). Full answer: [02-java-jvm-memory-leaks-performance.md](02-java-jvm-memory-leaks-performance.md#how-a-garbage-collected-language-still-leaks-memory).

**Q: When would you use a `WeakHashMap`?**
**A:** When the correct entry lifetime is "as long as something else still holds this key" — not a fixed size or TTL. It auto-evicts an entry once its key becomes otherwise unreachable, with no explicit cleanup code. Not a substitute for a real bounded/TTL cache (Caffeine/Guava) when size or time, not key-reachability, is the actual constraint. Full answer: [02-java-jvm-memory-leaks-performance.md](02-java-jvm-memory-leaks-performance.md#where-weakhashmap-fits).

---

## Spring

**Q: Why does `@Transactional` fail during self-invocation?**
**A:** `@Transactional` is implemented via a **proxy** wrapping the bean; the proxy only intercepts calls that come in from *outside* the bean. `this.otherMethod()` (or an implicit `otherMethod()`) inside the same class calls the raw object directly, entirely bypassing the proxy — so no transaction begins, joins, or commits, regardless of what propagation was declared. Fix: inject the bean into itself and call through that reference, or move the method to a separate bean. Full answer: [03-springboot-transactions.md](03-springboot-transactions.md#part-2--interview-questions-todays-round).

**Q: Explain propagation with an example.**
**A:** Propagation decides what an inner `@Transactional` call does when called from within an already-active transaction. `REQUIRED` (default) joins the existing one. `REQUIRES_NEW` suspends it and starts an independent transaction with its own commit/rollback — e.g. an audit-log write via `REQUIRES_NEW` still commits even if the calling business operation later fails and rolls back. `SUPPORTS` joins if one exists, otherwise runs non-transactionally. Full answer with a worked `REQUIRES_NEW` example: [03-springboot-transactions.md](03-springboot-transactions.md#propagation--what-happens-when-a-transactional-method-calls-another-transactional-method).

**Q: What is proxy-based AOP?**
**A:** Spring's mechanism for adding cross-cutting behavior (transactions, security checks, logging) **around** a method call without touching that method's own code — a `BeanPostProcessor` wraps the bean in a proxy (JDK dynamic proxy if it implements an interface, CGLIB-generated subclass otherwise) that intercepts external calls, runs "before" logic, delegates to the real method, then runs "after" logic (commit/rollback, for `@Transactional`). The tradeoff is exactly what causes the self-invocation and private-method gaps above: only calls that actually go through the proxy get the behavior. Full answer: [03-springboot-transactions.md](03-springboot-transactions.md#the-proxy-mechanism--how-transactional-is-actually-implemented).

---

## JavaScript

**Q: Why do Promises execute before `setTimeout()` callbacks?**
**A:** They're on different queues with different priority — `Promise`/`await` continuations go on the **microtask** queue, `setTimeout` goes on the **macrotask** queue, and the event loop fully drains the *entire* microtask queue (including new microtasks spawned while draining) before it's even allowed to look at the macrotask queue. `setTimeout(fn, 0)` means "as soon as possible after all microtasks," never "immediately." Full answer: [04-javascript-event-loop.md](04-javascript-event-loop.md#part-3--interview-discussion).

**Q: Explain the event loop from memory.**
**A:** JS runs on one thread with a call stack. Async work (timers, network, DOM events) is handed off to the host environment (browser/Node), not the JS engine — when it completes, its callback is queued, never pushed directly onto the stack. The loop: run synchronous code to completion, then fully drain the microtask queue (Promise `.then`, `await` continuations, `queueMicrotask`), then run exactly one macrotask (`setTimeout`, I/O, DOM events) off the callback queue, then drain microtasks again, repeat forever. Full breakdown of each piece: [04-javascript-event-loop.md](04-javascript-event-loop.md#part-1--concepts-read-this-first).

---

## React

**Q: Why does `useEffect` sometimes run more than once in development?**
**A:** React 18+ Strict Mode deliberately mounts, unmounts, and remounts every component once in development only — setup, cleanup, setup again — specifically to surface effects whose cleanup doesn't fully undo the setup (an uncancelled subscription, a listener left attached). It's a deliberate correctness check, not a bug, and never happens in production. Full answer: [05-react-lifecycle.md](05-react-lifecycle.md#why-useeffect-sometimes-runs-twice-in-development).

**Q: How do you prevent memory leaks in React components?**
**A:** Every effect that starts something with a lifetime beyond one render — a subscription, `setInterval`/`setTimeout`, an event listener, an in-flight fetch — needs a matching teardown in that effect's cleanup function: `clearInterval`, `removeEventListener`, unsubscribe, or a `cancelled` flag guarding a late-arriving async response from calling a setter after unmount. Same root cause as any reference-based leak: something outliving the thing that created it. Full answer: [05-react-lifecycle.md](05-react-lifecycle.md#part-3--interview-questions-todays-round).

---

## DSA (Graphs)

**Q: Why BFS instead of DFS?**
**A:** Default to BFS specifically when the shortest path in an unweighted graph is needed — its level-by-level order guarantees the first arrival at any node is via the fewest edges, which DFS doesn't guarantee at all. For plain reachability, connected components, or flood-fill, either is O(V+E) and correct — pick whichever reads more naturally for the problem's shape. Full answer: [01-dsa-graphs.md](01-dsa-graphs.md#part-3--interview-discussion).

---

## Self-grading
For each answer, ask: did I state the *mechanism* (what actually happens internally, and why), or did I just restate the definition? "Strict Mode double-invokes effects in dev" is a definition. "Strict Mode double-invokes effects in dev specifically to catch cleanup functions that don't fully undo their setup, before that gap shows up as a real leak in production, and it never fires in a production build" is the mechanism, with the reason and the boundary condition both named — that's the level to aim for.
