# Java — JVM Memory Leaks & Performance

Class loading (Loading → Linking → Initialization) was already covered in full in [Day 5's JVM doc](../day5/02-java-jvm-gc-string-pool.md#class-loader) — a quick recap sits below since it's on today's list, but the new ground today is **how a garbage-collected language still leaks memory**, and what the JIT/HotSpot actually do to make Java fast despite being interpreted-and-compiled.

## Part 1 — Concepts (read this first)

### Class Loading — recap
Three phases, in order, the first time a class is actively used (not merely referenced): **Loading** (read the `.class` bytes, create a `Class` object) → **Linking** — *Verify* (bytecode is structurally safe) → *Prepare* (allocate static fields, zero/null/false defaults) → *Resolve* (symbolic references → real ones) → **Initialization** (static initializers actually run, top to bottom, source order). Full write-up with the parent-delegation model: [Day 5's doc](../day5/02-java-jvm-gc-string-pool.md#class-loader).

### How a garbage-collected language still leaks memory
The core, slightly counterintuitive fact: **the GC only reclaims objects that are unreachable.** It cannot reclaim an object that's still reachable from a GC root (a static field, an active thread's stack, a live object graph), no matter how obviously "done" that object is from a human reading of the code. A Java "memory leak" is therefore never a dangling-pointer bug like in C — it's always an **unintentional strong reference** that outlives the object's actual useful lifetime, keeping something reachable (and therefore un-collectible) long after the program has stopped needing it. Every pattern below is a different concrete shape of that one root cause.

### Static collections
```java
public class Cache {
    private static final Map<String, Object> cache = new HashMap<>(); // static -> lives for the JVM's entire lifetime

    public static void put(String key, Object value) {
        cache.put(key, value); // nothing ever removes an entry
    }
}
```
A `static` field is reachable from a **class**, not an instance — and a loaded class is itself reachable for as long as its `ClassLoader` is alive, which for application classes is typically the entire run of the program. A static `Map`/`List`/`Set` that only ever grows (entries added, nothing ever evicted or expired) leaks by construction: every value ever inserted stays strongly reachable forever, whether or not the caller who inserted it still cares about it. Fix: bound the collection (an LRU cache, a `Map` with an eviction policy — e.g. Caffeine/Guava's `CacheBuilder.maximumSize()`), or use `WeakHashMap` (below) if the right lifetime is actually "as long as the key is still referenced elsewhere."

### Listeners / observers not unregistered
```java
public class Button {
    private final List<ClickListener> listeners = new ArrayList<>();
    public void addListener(ClickListener l) { listeners.add(l); }
    // no removeListener() ever called by callers -- or it exists but nobody calls it
}
```
A classic pattern: a long-lived publisher (a UI component, an event bus, a Spring singleton bean) holds a list of subscriber callbacks. Every subscriber that registers and is later logically "done" (a short-lived screen closes, a request-scoped object finishes) but never explicitly **unregisters** stays strongly referenced by the long-lived publisher indefinitely — the publisher has no way to know the subscriber is done unless told. This is the single most common real-world Java leak pattern, because it requires a *symmetric* discipline (every `add` needs a matching `remove` on the correct lifecycle event) that's easy to get half-right. Fixes: enforce unregistration on the subscriber's own cleanup path (a `close()`/`@PreDestroy`), or hold the listeners with `WeakReference`s so a subscriber that's otherwise unreachable can be collected even if nobody explicitly unregistered it.

### `ThreadLocal` not cleared
```java
private static final ThreadLocal<byte[]> buffer = ThreadLocal.withInitial(() -> new byte[1024 * 1024]);
// ... used inside a request handler, never buffer.remove()'d
```
Each `Thread` has its own `ThreadLocalMap` holding entries for every `ThreadLocal` ever `set()` on it. This is a genuine leak risk specifically in **thread-pooled** environments (every real server — Tomcat, a `ThreadPoolExecutor`) because pooled threads are **reused**, not destroyed, after a request/task finishes — so a `ThreadLocal` value set during one request and never `.remove()`'d stays attached to that pooled thread, invisibly retained, for the thread's entire lifetime (which is the pool's lifetime), across every unrelated future task that happens to land on that same thread. The fix is a strict `try { ... } finally { threadLocal.remove(); }` around every `set()`, always, no exceptions — this is exactly the pattern `SecurityContextHolder` (from [Day 6's JWT doc](../day6/03-springboot-security-jwt.md)) and most request-scoped frameworks enforce internally.

### Unclosed resources
```java
FileInputStream in = new FileInputStream("data.txt");
// ... work with in ...
// no in.close() -- leaked file handle, and its associated native/OS resources
```
Not a *heap* memory leak in the strict sense — the `FileInputStream` object itself is still GC-eligible once unreachable — but the **native OS resource** it wraps (a file descriptor, a socket handle, a DB connection from a pool) is not managed by the Java heap or GC at all, and isn't released until `close()` runs. Left unclosed at scale, this exhausts a genuinely finite OS-level resource (file descriptor limits, connection pool capacity) well before the JVM heap itself would ever show a problem — a different failure mode than a heap leak, but grouped with "memory leaks" because the fix category (own the resource's lifecycle explicitly) is the same discipline. Fix: **try-with-resources**, always, for anything implementing `AutoCloseable` — the compiler-generated `finally` block calls `close()` even if an exception is thrown mid-block, which a bare `try/finally` written by hand is easy to get subtly wrong (e.g. forgetting the `finally`, or closing before an exception has a chance to propagate correctly).
```java
try (FileInputStream in = new FileInputStream("data.txt")) {
    // ... work with in ...
} // in.close() guaranteed here, even on exception
```

### Where `WeakHashMap` fits
`WeakHashMap<K, V>` holds its **keys** via `WeakReference` — an entry is automatically removed once its key becomes otherwise unreachable, with no explicit eviction code needed. This is the correct built-in tool specifically when the right lifetime for a cached value is *"exactly as long as something else is still holding onto this key anyway"* — e.g. caching a per-object computed value keyed by object identity, where you never want the cache itself to be the reason that object stays alive. It is **not** a general-purpose cache replacement: it doesn't bound *size* or *time*, only *key reachability* — a cache that needs a size or TTL bound (the static-collection case above) still needs a real eviction policy (Caffeine/Guava), not `WeakHashMap`.

### Performance — Escape Analysis
A JIT optimization that determines whether an object created inside a method **"escapes"** that method — gets stored somewhere reachable after the method returns (a field, a returned value, passed to another thread) — or provably stays entirely local.
```java
public int sumSquares(int a, int b) {
    Point p = new Point(a, b); // does `p` ever leave this method?
    return p.x() * p.x() + p.y() * p.y();
}
```
If the JIT proves `p` never escapes `sumSquares` (never assigned to a field, never returned, never passed anywhere that could retain it), it can legally skip heap-allocating it entirely: **scalar replacement** breaks the object into its individual primitive fields, living purely as CPU registers/stack values, with zero heap allocation and — critically — nothing for the GC to ever have to trace or collect. It's also what makes **stack allocation** and **lock elision** possible: a `synchronized` block guarding an object provably confined to one thread's stack can have its locking overhead removed entirely, since no other thread could ever contend for it. This is why writing small, short-lived, per-call helper objects in a hot path is often *not* the allocation cost it looks like on paper — the JIT frequently erases it completely, provided the object genuinely never escapes.

### JIT Compiler and HotSpot
Java bytecode starts out **interpreted** — the JVM (specifically, Oracle/OpenJDK's **HotSpot** implementation) reads and executes bytecode instructions one at a time, which is slower than native machine code but starts running immediately with no compile pause. HotSpot's name comes from what it does next: it profiles execution at runtime, and once a method or loop crosses an invocation-count threshold (a "hot spot"), the **JIT (Just-In-Time) compiler** compiles that specific hot code path directly to native machine code, which then runs at (near-)native speed for the rest of the program's life.

Two tiers, working together (**tiered compilation**, the default since Java 8):
- **C1 (client compiler)** — compiles quickly, with lighter optimization, favoring fast warm-up. Good for short-lived processes/CLI tools where startup latency matters more than peak throughput.
- **C2 (server compiler)** — compiles more slowly but far more aggressively (inlining, escape analysis, loop unrolling), favoring long-running peak throughput. Good for long-lived server processes.

Tiered compilation runs code interpreted first, promotes it to C1 once it's warm, and further promotes genuinely hot code to C2 if it stays hot — the practical effect is a JVM that starts up reasonably fast (interpreter, no compile wait) and still reaches native-level peak performance for the code that actually matters (the small fraction of hot loops/methods that dominate runtime, per the standard 80/20 profiling observation). This tiered, profile-guided approach is also *why* Java can, for genuinely hot code, outperform naively-compiled-once native code: the JIT has **runtime profile information** (actual observed branch frequencies, actual call-site targets) a static ahead-of-time compiler never had at compile time, enabling optimizations like aggressive inlining of the *actually*-common call target rather than a statically-guessed one.

### Strong / Weak / Soft / Phantom References
The four `java.lang.ref` reference strengths, in decreasing order of how hard they keep an object alive — the mechanism `WeakHashMap` above, and every cache/leak-avoidance pattern discussed today, is actually built on:

| Type | Kept alive? | Collected when | Typical use |
|---|---|---|---|
| **Strong** (a plain `Object o = new Object()`) | Always, while reachable | Never, while any strong reference exists | The default — and the source of every leak pattern above |
| **Weak** (`WeakReference<T>`) | No | Next GC cycle, as soon as no strong refs remain | `WeakHashMap` keys, canonical-instance/interning maps, listener registries that shouldn't pin subscribers alive |
| **Soft** (`SoftReference<T>`) | Only under memory pressure | The JVM is specifically allowed to defer clearing these until it's close to `OutOfMemoryError` | Memory-sensitive caches — "keep this around as long as there's room to spare, but never let it cause an OOM" |
| **Phantom** (`PhantomReference<T>`) | No — `get()` always returns `null` | Enqueued to a `ReferenceQueue` only *after* the object is already finalized and about to be reclaimed | Reliable post-mortem cleanup (closing a native handle) — the modern, safe replacement for the deprecated `Object.finalize()` |

Runnable side-by-side demo: [`ReferenceTypesDemo.java`](../../src/main/java/org/prep/day7/ReferenceTypesDemo.java). One subtlety worth stating out loud: `System.gc()` is only a **hint** — no reference type's collection is contractually guaranteed by calling it, which is exactly why the demo's Soft reference reliably survives a `System.gc()` call with no real memory pressure present, while the Weak reference reliably doesn't.

---

## Part 2 — Coding

[`ReferenceTypesDemo.java`](../../src/main/java/org/prep/day7/ReferenceTypesDemo.java) — runnable, demonstrates all four:
- **Strong** — a plain reference; shown only as the baseline (never collected while held).
- **Weak** — `WeakReference`, cleared after the strong reference is dropped and `System.gc()` runs.
- **Soft** — `SoftReference`, *not* cleared by the same `System.gc()` call, since there's no real memory pressure — the demo's comment explains this is expected, not a bug.
- **Phantom** — `PhantomReference` + `ReferenceQueue`; `get()` is shown always returning `null`, and the reference is shown being enqueued after the target becomes unreachable and GC runs.

Actual output from running it:
```
Strong reference held: true
Weak reference cleared after gc(): true
Soft reference still alive (expected, no memory pressure): true
phantomRef.get() is always null: true
Phantom reference enqueued after gc(): true
```

---

## Part 3 — Interview Questions (today's round)

**Q: If Java has a garbage collector, how can it still leak memory?**
**A:** The GC only reclaims objects that are **unreachable** — it can't and won't collect anything still reachable from a GC root, no matter how logically "done" that object is. A Java memory leak is always an unintentional *strong reference* that outlives the object's real useful lifetime: an ever-growing static collection, listeners registered but never unregistered, a `ThreadLocal` never cleared on a pooled thread, or (a related-but-distinct case) a native resource left unclosed. In every case, fixing it means finding and removing (or weakening) the reference that's keeping the object alive longer than intended — never a "the GC is broken" situation.

**Q: When would you use a `WeakHashMap`?**
**A:** When the correct lifetime for a cache entry is *"exactly as long as something else still holds the key,"* not a fixed size or TTL — e.g. attaching computed metadata to objects by identity, where you specifically don't want the cache to be the reason those objects stay alive. It's not a general-purpose bounded cache: it has no size or time limit, only key-reachability-triggered eviction, so a cache that needs an actual capacity/TTL bound still needs a real eviction policy (Caffeine/Guava `CacheBuilder`), not `WeakHashMap`.

**Q: Explain the JIT compiler and HotSpot's tiered compilation.**
**A:** Java bytecode starts interpreted (no compile delay, immediate execution); HotSpot profiles method/loop invocation counts at runtime and JIT-compiles genuinely hot code to native machine code once it crosses a threshold. Tiered compilation runs C1 (fast to compile, lighter optimization) first for quick warm-up, then promotes code that stays hot to C2 (slower to compile, far more aggressive optimization — inlining, escape analysis, loop unrolling) for peak long-running throughput. The practical payoff: reasonable startup latency plus native-level peak performance for the small fraction of code that actually dominates runtime — and, because the JIT compiles using real runtime profile data (actual branch frequencies, actual call targets) rather than a static compiler's compile-time guesses, hot Java code can in some cases outperform naive ahead-of-time-compiled native code.

**Q: What is escape analysis, and why does it matter for performance?**
**A:** A JIT optimization that determines whether an object created inside a method ever becomes reachable outside it (stored in a field, returned, passed across a thread boundary). If it provably doesn't, the JIT can skip heap allocation entirely via **scalar replacement** (the object's fields become plain stack/register values, with nothing for the GC to ever trace) and can elide `synchronized` locking on an object provably confined to one thread. It matters because it means small, short-lived helper objects in a hot path frequently cost far less than they appear to on paper — the allocation, and the eventual GC work, may simply never happen.
