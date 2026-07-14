# Mock Interview — Rapid Fire (answer before reading)

Rules: cover each question out loud, from memory, before reading the answer below it. If you hesitate more than a few seconds, that's a signal to re-read the relevant doc from today, not to peek immediately.

---

## Java

**Q: Why do we use thread pools?**
**A:** Creating an OS thread per task is expensive (stack allocation, kernel scheduling overhead), and unbounded thread creation under load can exhaust memory or thrash the scheduler with context switching. A thread pool creates a bounded, reusable set of worker threads once and hands tasks to them via a queue — amortizing creation cost and capping concurrency, which is what actually keeps a service stable under load. Full answer: [02-java-concurrency.md](02-java-concurrency.md#part-3--interview-questions-todays-round).

**Q: Why is `volatile` not enough for an increment operation?**
**A:** `volatile` only guarantees visibility of the latest write across threads — it doesn't make a compound read-modify-write (`count++` = read, add, write-back) atomic. Two threads can both read the same value before either writes back, silently losing an increment. Needs `synchronized` around the whole operation, or `AtomicInteger`'s CAS-based `incrementAndGet()`. Full answer: [02-java-concurrency.md](02-java-concurrency.md#part-3--interview-questions-todays-round).

---

## Spring

**Q: Walk me through what happens when a REST request reaches your application.**
**A:** Tomcat accepts the connection and parses the request, the servlet filter chain runs first (CORS, security), then `DispatcherServlet` — the single front controller — asks `HandlerMapping` which controller method matches, runs interceptors' `preHandle()`, has `HandlerAdapter` invoke the method (resolving `@PathVariable`/`@RequestBody` via `HttpMessageConverter`), the controller delegates to service → repository → database, and the result flows back up and is serialized to JSON on the way out for a `@RestController`. Exceptions anywhere in that chain are caught by `@ControllerAdvice`. Full answer with diagram: [03-springboot-mvc.md](03-springboot-mvc.md#part-2--interview-questions-todays-round).

---

## JavaScript

**Q: What is a closure?**
**A:** A function bundled with references to the variables from the scope it was defined in, so it can still read/write them after that outer scope has returned — a natural consequence of lexical scoping plus functions being first-class values. Used for private state that persists across calls (`makeCounter`'s `count`, `debounce`'s `timeoutId`). Full answer + the classic `var` vs `let` loop gotcha: [04-javascript-fundamentals.md](04-javascript-fundamentals.md#closures-very-important).

**Q: Difference between `==` and `===`?**
**A:** `===` compares value and type with no conversion; `==` coerces operands to a common type first, following rules that produce well-known surprises (`"" == 0` is `true`). Default to `===` always. Full answer: [04-javascript-fundamentals.md](04-javascript-fundamentals.md#part-3--interview-questions-todays-round).

**Q: Explain the event loop at a high level.**
**A:** JS is single-threaded; the call stack runs sync code, async work (timers, I/O) is handed to the runtime, and completed callbacks queue up — microtasks (Promises) and macrotasks (`setTimeout`) in separate queues. The event loop pushes the next queued task onto the stack only once the stack is empty, and fully drains microtasks before the next macrotask — which is why a resolved Promise's `.then` fires before a `setTimeout(0)`. Full answer: [04-javascript-fundamentals.md](04-javascript-fundamentals.md#part-3--interview-questions-todays-round).

---

## React

**Q: Why do we need state if we already have props?**
**A:** Props flow data **in** from a parent and are read-only to the receiving component; state is data a component **owns** and can change itself in response to events. Without state, nothing could represent a component's own changing view (typed input, toggled UI) without pushing that data all the way up to whoever owns it. Full answer: [05-react-fundamentals.md](05-react-fundamentals.md#part-3--interview-questions-todays-round).

**Q: What causes a component to re-render?**
**A:** Its own state changing, receiving new props, or its parent re-rendering (children re-render by default even with unchanged props, unless wrapped in `React.memo`). Note: mutating state in place and calling the setter with the *same reference* does **not** trigger a re-render — React bails out via an `Object.is` check, which is why you always create new objects/arrays for state updates instead of mutating in place. Full answer: [05-react-fundamentals.md](05-react-fundamentals.md#part-3--interview-questions-todays-round).

---

## DSA

**Q: What's the actual structural difference between Subsets and Permutations backtracking?**
**A:** Subsets loops forward from a `start` index, never revisiting earlier indices, because order doesn't matter and each element is used at most once — each call operates on a strictly smaller remaining set. Permutations loops from index `0` every call but skips `used` indices, because order matters and every position must consider every not-yet-placed element. Full answer: [01-dsa-recursion-backtracking.md](01-dsa-recursion-backtracking.md#part-3--interview-questions-todays-round).

---

## Self-grading

For each answer, ask: did I state the *mechanism* (what actually happens internally, and why), or did I just restate the definition? "Thread pools improve performance" is a definition. "Thread pools amortize the cost of expensive OS thread creation across many tasks and cap concurrency, which is what actually prevents an unbounded flood of tasks from exhausting memory or thrashing the scheduler" is the mechanism — that's the level to aim for.
