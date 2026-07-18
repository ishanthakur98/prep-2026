# Mock Interview — Rapid Fire (answer before reading)

Rules: cover each question out loud, from memory, before reading the answer below it. If you hesitate more than a few seconds, that's a signal to re-read the relevant doc from today, not to peek immediately.

---

## Java

**Q: Why is `ConcurrentHashMap` faster than `Hashtable`?**
**A:** `Hashtable` uses one lock for the entire table — every operation on any bucket serializes behind it, so throughput doesn't improve with more threads. `ConcurrentHashMap` locks at a much finer grain (per-bin, only when needed; CAS for the common empty-bin case), so writes to different bins proceed in true parallel with zero contention between them. Full answer: [02-java-concurrency-deep.md](02-java-concurrency-deep.md#part-3--interview-questions-todays-round).

**Q: Explain CAS.**
**A:** Compare-And-Swap: a single hardware-atomic instruction that sets a memory location to a new value only if it still holds an expected value, reporting success/failure. Atomic classes like `AtomicInteger` loop on it (read, compute, `compareAndSet`, retry on failure) instead of blocking — lock-free. Full answer: [02-java-concurrency-deep.md](02-java-concurrency-deep.md#atomic-classes-and-cas).

**Q: Difference between `AtomicInteger` and `synchronized`?**
**A:** `AtomicInteger` is lock-free (CAS retry, no blocking) but only covers one variable's atomicity. `synchronized` blocks losing threads but can protect an arbitrary multi-field critical section. Single counter/flag → atomic class; multi-field invariant → a lock. Full answer: [02-java-concurrency-deep.md](02-java-concurrency-deep.md#part-3--interview-questions-todays-round).

**Q: What is thread starvation?**
**A:** A thread that's technically able to eventually proceed but keeps getting passed over indefinitely (e.g. an unfair lock repeatedly favoring newer arrivals). Different from deadlock, where threads can *never* proceed because of a circular resource wait. Full answer: [02-java-concurrency-deep.md](02-java-concurrency-deep.md#part-3--interview-questions-todays-round).

---

## Spring

**Q: Explain the Spring Security filter chain.**
**A:** Spring Security registers as one Servlet Filter in the container's chain, which internally delegates through its own ordered sequence of security filters (CORS, CSRF, a custom JWT auth filter, exception translation, the final authorization decision) — all running *before* `DispatcherServlet`/any controller is reached. A request failing any check short-circuits to a 401/403 without the controller ever running. Full answer: [03-springboot-security-jwt.md](03-springboot-security-jwt.md#the-spring-security-filter-chain).

**Q: Difference between authentication and authorization?**
**A:** Authentication answers "who are you" (verifying identity — login, or a valid token). Authorization answers "what are you allowed to do" (given a known identity, is *this* request permitted). Authentication always happens first and produces the identity authorization then decides against. Full answer: [03-springboot-security-jwt.md](03-springboot-security-jwt.md#authentication-vs-authorization).

**Q: How would you secure a REST API?**
**A:** Stateless JWT authentication (a filter validates the token's signature + expiry on every request, populating `SecurityContextHolder`) plus RBAC for authorization (`@PreAuthorize`/endpoint rules deciding what an authenticated identity can do). No server-side session — every request is independently verifiable via the token's signature. Full picture: [03-springboot-security-jwt.md](03-springboot-security-jwt.md).

---

## JavaScript

**Q: Explain the prototype chain.**
**A:** Every object holds an internal `[[Prototype]]` link to another object; a property lookup that misses on the object itself walks up that chain until found or `null`. This is how instances of the same constructor/class **share** methods (one function on `Constructor.prototype`) instead of each instance carrying its own copy. `class` syntax is sugar over this exact mechanism. Full answer: [04-javascript-objects-prototypes.md](04-javascript-objects-prototypes.md#the-prototype-chain).

**Q: Difference between `call`, `apply`, and `bind`?**
**A:** All three explicitly set `this` inside a function call. `call`/`apply` invoke immediately (args individually vs. as an array); `bind` returns a new function with `this` permanently fixed, to be called later — the standard way to pass an object method as a callback without losing its `this`. Full answer: [04-javascript-objects-prototypes.md](04-javascript-objects-prototypes.md#call-apply-bind).

**Q: What does `this` refer to in different contexts?**
**A:** Determined by **how** a function is called, not where it's defined: `obj.method()` → `obj`; a plain `fn()` call → `undefined` in strict mode; `new Fn()` → the new instance; `.call`/`.apply`/`.bind` → explicitly whatever's passed. The one exception: arrow functions never have their own `this` — they close over the enclosing lexical scope's. Full table: [../day4/04-javascript-fundamentals.md](../day4/04-javascript-fundamentals.md#this-keyword).

---

## React

**Q: Why use `useMemo`?**
**A:** To cache an expensive computation's *result* across re-renders, recomputing only when its listed dependencies actually change — worth it for genuinely expensive computations, or when the result needs a stable reference for something downstream (like a `React.memo`'d child) to correctly skip work. Not worth it for cheap computations, where the memoization bookkeeping can cost more than just redoing the work. Full answer: [05-react-state-management.md](05-react-state-management.md#part-3--interview-questions-todays-round).

**Q: When is `useCallback` useful?**
**A:** When a function reference is passed as a prop to a `React.memo`'d child (or into another hook's dependency array) — without it, a parent re-render creates a brand-new function object every time, which defeats the child's shallow-prop-equality memoization even though the function behaves identically. Full answer: [05-react-state-management.md](05-react-state-management.md#usecallback).

**Q: What causes unnecessary re-renders?**
**A:** By default, every child re-renders whenever its parent does, regardless of whether that child's own props actually changed. `React.memo` opts a component out via a shallow props comparison — but that only helps if the props themselves have **stable references**, which is exactly what `useMemo`/`useCallback` provide upstream; without them, "unchanged-looking" object/array/function props are actually new references every render, and `React.memo` re-renders anyway. Full answer: [05-react-state-management.md](05-react-state-management.md#part-3--interview-questions-todays-round).

---

## DSA (Heaps)

**Q: Why a heap instead of sorting, for a "top k" problem?**
**A:** Sorting is `O(n log n)` and gives you everything ordered, which is more than a top-k question needs. A heap bounded to size `k` gives `O(log k)` insert/evict per element instead of `O(log n)`, and — for a live stream — never has to re-sort anything as new elements keep arriving. Full answer: [01-dsa-heaps.md](01-dsa-heaps.md#part-3--interview-discussion).

---

## Self-grading
For each answer, ask: did I state the mechanism (what actually happens internally), or did I just restate the definition? "JWT is stateless" is a definition. "JWT is stateless because the token itself carries the signed claims, so any server instance can verify it independently with zero shared session lookup, at the cost of needing a separate mitigation — short expiry plus refresh tokens, or a denylist — for revoking one before it naturally expires" is the mechanism, with the tradeoff named — that's the level to aim for.
