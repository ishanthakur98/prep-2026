# Mock Interview — Rapid Fire (answer before reading)

Rules: cover each question out loud, from memory, before reading the answer below it. If you hesitate more than a few seconds, that's a signal to re-read the relevant doc from today, not to peek immediately.

---

## Java

**Q: Why is `HashMap` not thread safe?**
**A:** No synchronization guards its mutable state (the `table` array, bucket chain pointers). Concurrent `put()`s can race on appending to the same bucket, silently losing one thread's write; concurrent resizes in pre-Java-8 versions could corrupt a bucket's chain into a cycle, making `get()` loop forever. Full answer: [02-java-hashmap-internals.md](02-java-hashmap-internals.md#part-4--interview-questions-todays-round).

**Q: Why does `HashMap` resize?**
**A:** To keep average bucket chain length short (bounded by load factor 0.75), preserving O(1) average lookup — as `size` grows, unbounded chains degrade lookups toward O(n) per bucket. Full answer: [02-java-hashmap-internals.md](02-java-hashmap-internals.md#part-4--interview-questions-todays-round).

**Q: What is rehashing?**
**A:** Recomputing each entry's bucket index after a resize, since `index = (n-1) & hash` depends on table size. Java 8 avoids recomputing the hash itself by exploiting that capacity always doubles — one extra bit determines whether an entry stays at the same index or moves to `oldIndex + oldCapacity`. Full answer: [02-java-hashmap-internals.md](02-java-hashmap-internals.md#rehashing--the-java-8-optimization).

**Q: Difference between `HashMap`, `Hashtable`, `ConcurrentHashMap`?**
**A:** `HashMap` — unsynchronized, allows one null key. `Hashtable` — fully synchronized (one lock for the whole table, a throughput bottleneck), no nulls, legacy. `ConcurrentHashMap` — fine-grained thread safety (CAS on empty bins, `synchronized` per non-empty bin in Java 8+), no nulls (ambiguity under concurrency), weakly consistent iterators. Full table: [02-java-hashmap-internals.md](02-java-hashmap-internals.md#part-4--interview-questions-todays-round).

---

## Spring

**Q: Why doesn't Spring use `new` everywhere?**
**A:** `new Foo()` hard-codes a concrete implementation and pushes dependency-graph construction responsibility onto every class individually — tight coupling, no swappability, no centralized lifecycle control, and (critically) no way for the container to hand back a proxy instead of the raw object for things like `@Transactional`. Spring's IoC container centralizes "how to build the graph" so classes only declare "what they need." Full answer: [03-springboot-bean-lifecycle.md](03-springboot-bean-lifecycle.md#part-3--interview-questions-todays-round).

**Q: Difference between `ApplicationContext` and `BeanFactory`?**
**A:** `BeanFactory` is the minimal root container contract — lazy bean creation on first `getBean()`. `ApplicationContext` extends it with eager singleton instantiation at startup (fail-fast), events, i18n, environment/profile support, and the `BeanPostProcessor` machinery that powers AOP proxies. Every real Spring Boot app uses `ApplicationContext`. Full answer: [03-springboot-bean-lifecycle.md](03-springboot-bean-lifecycle.md#applicationcontext).

---

## DSA

**Q: Explain why Binary Search works — not the algorithm, the proof.**
**A:** It's correct whenever the search space admits a monotonic boolean predicate (`false...false true...true`). The loop invariant "the boundary lies within `[lo, hi]`" holds initially and is preserved every iteration, because a side is only ever discarded once it's been *proven* (not assumed) that the boundary can't be there — `f(mid) == false` proves the boundary is strictly greater than `mid`; `f(mid) == true` proves it's at or before `mid`. Since `[lo, hi]` strictly shrinks every step while never losing the boundary, termination (`lo == hi`) necessarily lands exactly on it. Full proof with worked implications: [01-dsa-binary-search.md](01-dsa-binary-search.md#the-invariant-that-makes-it-work-proof-not-recipe).

---

## Self-grading

For each answer, ask: did I state the mechanism (what actually happens internally), or did I just restate the definition? "HashMap resizes to stay fast" is a definition. "HashMap resizes because average chain length grows past what keeps get/put near O(1), and Java 8 makes the rehash cheap by exploiting that capacity doubling only ever adds one new high bit to the mask" is the mechanism — that's the level to aim for.
