# Java — JVM Architecture, Garbage Collection, String Pool

This builds directly on [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md), which already covers Heap/Stack/Metaspace/String Pool and the generational GC picture — read that first if it's not fresh. Today fills in the piece that doc didn't cover (the **Class Loader**, and where it sits relative to everything else), goes one level deeper on **G1** specifically, and turns String Pool theory into runnable code.

## Part 1 — Concepts (read this first)

### JVM Architecture, end to end
Three big pieces, in the order a `.class` file passes through them:

1. **Class Loader Subsystem** — finds, loads, links, and initializes `.class` files into the JVM.
2. **Runtime Data Areas** — the memory regions the running program actually uses (Heap, Stack, Metaspace, PC Register, Native Method Stack — the subject of [day2's doc](../day2/07-jvm-memory.md) plus PC Register/Native Stack below).
3. **Execution Engine** — actually runs the bytecode (interpreter, JIT compiler, garbage collector live here).

```
 .class files
      │
      ▼
┌─────────────────────┐
│  Class Loader        │  Loading -> Linking (verify/prepare/resolve) -> Initialization
│  Subsystem            │
└─────────┬────────────┘
          ▼
┌──────────────────────────────────────────────────────────┐
│                Runtime Data Areas                         │
│  Heap | Metaspace | (per-thread) Stack, PC Register,       │
│  Native Method Stack                                       │
└─────────┬────────────────────────────────────────────────┘
          ▼
┌──────────────────────────────────────────────────────────┐
│  Execution Engine: Interpreter, JIT Compiler, GC            │
└──────────────────────────────────────────────────────────┘
```

### Class Loader
Loads classes **on demand** (lazily, the first time a class is actually referenced), not all up front. Three built-in loaders, delegating upward (each loader asks its parent first — **parent delegation model**):
- **Bootstrap Class Loader** — loads core JDK classes (`java.lang.*`, `java.util.*`) from the JDK's own runtime, written in native code, has no parent.
- **Platform/Extension Class Loader** — loads JDK extension classes.
- **Application (System) Class Loader** — loads your application's classes from the classpath.

**Why parent delegation matters**: a request to load `java.lang.String` always resolves to the Bootstrap loader's version, even if a malicious or accidental classpath entry defines its own `java.lang.String` — the request is delegated up before the current loader ever tries to load it itself, so the trusted JDK version always wins. This is a real security/correctness mechanism, not an implementation detail.

Three phases once a loader has the bytes:
1. **Loading** — reads the `.class` bytes, creates a `Class` object representing it.
2. **Linking** — **Verify** (bytecode is structurally valid, doesn't violate JVM safety rules) → **Prepare** (allocate memory for static fields, set them to default values — `0`/`null`/`false`) → **Resolve** (symbolic references to other classes, like `SomeOtherClass.method()`, resolved to actual memory references).
3. **Initialization** — static initializer blocks and static field initializers actually run, top to bottom, in source order. This is why a `static { ... }` block or `static int x = compute();` runs exactly once, the first time the class is actively used (not merely referenced in a type position).

### Runtime Data Areas — the two this doc adds to day2's
[Day 2's doc](../day2/07-jvm-memory.md) covers Heap, Stack, Metaspace, and the String Pool in detail. Two more per-thread regions complete the picture:
- **PC (Program Counter) Register** — one per thread, holds the address of the JVM instruction currently executing for that thread. Lets a thread resume exactly where it left off after a context switch. Undefined (not used) while executing a native method.
- **Native Method Stack** — one per thread, same role as the regular Stack but for native (non-Java, e.g. JNI/C) method calls instead of Java bytecode frames.

### Garbage Collection — Minor vs Major, and G1 specifically
[Day 2's doc](../day2/07-jvm-memory.md#garbage-collection) already covers the core Minor GC (Young Gen) / Major GC (Old Gen) split and the weak generational hypothesis behind it. **G1 (Garbage First)**, the default collector since Java 9, is worth one level deeper because it's the one you'll actually be asked about:

- Instead of one contiguous Young space and one contiguous Old space, G1 divides the heap into many equal-sized **regions** (each region is independently, dynamically labeled Eden, Survivor, or Old as the collector needs).
- On a collection, G1 picks the regions with the **most garbage first** (hence the name) to reclaim, rather than always collecting one fixed generation wholesale — this is what lets it hit a target pause time instead of pausing proportional to a whole generation's size.
- It targets a configurable **max pause time goal** (`-XX:MaxGCPauseMillis`, default 200ms) and tries to do the most reclamation work it can within that budget, region by region, rather than guaranteeing to fully collect a generation every cycle.
- Still has Minor GCs (Young regions) and, when needed, mixed collections that also reclaim some Old regions — it's an evolution of the generational idea, not a replacement of it.

The one-sentence version for an interview: *"G1 breaks the heap into regions instead of one big Young/Old space, and picks the garbage-heaviest regions to collect first within a pause-time budget, instead of always collecting a whole fixed generation."*

### String Pool — why it exists, `intern()`, and immutability
[Day 2's doc](../day2/07-jvm-memory.md#string-pool) covers the pool mechanics (`"abc" == "abc"` vs `new String("abc")`). The *why* behind both design choices:

**Why the pool exists**: strings are extremely common (literals, class names, map keys, config values) and, critically, **immutable** — two variables can safely share one physical `String` object because neither can ever change what the other sees. Pooling literals is a pure memory-savings play that's only safe *because* immutability guarantees no aliasing bugs.

**Why Strings are immutable** (several independent reasons, all real):
1. **Safe sharing / the pool above** — only possible because a shared string can't be mutated out from under other holders of the same reference.
2. **Thread safety** — an immutable object needs no synchronization to be shared safely across threads; there's no "another thread changed it while I was reading it" race to guard against.
3. **Security** — strings are used for things like class names, file paths, network hosts, DB URLs, often passed to trusted APIs like `ClassLoader.loadClass(name)` — if `String` were mutable, code could pass a validated value, then mutate it after the check but before it's used, a classic time-of-check/time-of-use bug the JVM avoids entirely by making that mutation impossible.
4. **Safe hashcode caching** — `String.hashCode()` caches its result the first time it's computed (a `private int hash` field), which is only correct if the string's contents can never change afterward. This matters concretely because strings are extremely common `HashMap` keys — a cached, stable hash code makes every subsequent lookup with that key cheaper.

**`intern()`**: `someString.intern()` returns the pooled reference for that string's content — adding it to the pool if not already present, or returning the existing pooled instance if it is. Useful when you've built a `String` via concatenation/`new String(...)` (which lives outside the pool) and want to deliberately get back onto the shared pooled instance, e.g. to make `==` comparisons valid or to deduplicate many repeated values parsed from a large input.

---

## Part 2 — Coding

All three below are runnable as-is; see [`StringPoolDemo.java`](../../src/main/java/org/prep/day5/StringPoolDemo.java) for the full, run-and-read version.

### String pool behavior
```java
String a = "hello";              // literal -> pool
String b = "hello";              // same literal -> same pooled reference
String c = new String("hello");  // forces a new heap object, outside the pool
String d = c.intern();           // fetches the pooled reference for "hello"

System.out.println(a == b);        // true  -- both point at the one pooled "hello"
System.out.println(a == c);        // false -- c is a distinct object on the heap
System.out.println(a.equals(c));   // true  -- same content, equals() compares value
System.out.println(a == d);        // true  -- intern() returned the pooled reference
```

### `equals()` vs `==`
```java
String x = new String("java");
String y = new String("java");

System.out.println(x == y);        // false -- two distinct objects
System.out.println(x.equals(y));   // true  -- String overrides equals() to compare char-by-char
```
`==` always compares references (identity) for objects; only primitives compare by value with `==`. `String` (like any object) must override `.equals()` to get value comparison — the pool coincidentally makes `==` *look* like it works for literals, which is exactly the trap that makes this an interview favorite: it only holds for literals/interned strings, never for anything built at runtime (`new String(...)`, concatenation of non-constant values, `.substring()`, etc.).

### Autoboxing and Integer caching
```java
Integer i1 = 100;
Integer i2 = 100;
System.out.println(i1 == i2);      // true  -- both in the cached range [-128, 127]

Integer i3 = 200;
Integer i4 = 200;
System.out.println(i3 == i4);      // false -- outside the cached range, two distinct objects

Integer i5 = Integer.valueOf(100); // same cache as autoboxing -- valueOf() is what autoboxing calls
System.out.println(i1 == i5);      // true
```
`Integer.valueOf(int)` — which is what the compiler inserts for autoboxing (`Integer i = 100;` compiles to `Integer i = Integer.valueOf(100);`) — caches and reuses `Integer` objects for values **-128 to 127** (`IntegerCache`, sized this way by default, JVM-tunable via `-XX:AutoBoxCacheMax`). Outside that range, every autoboxing creates a genuinely new object. This is a real, recurring bug source: `==` on boxed `Integer`s appears to work for small numbers in testing/demos and then silently breaks the moment values exceed 127 in production data — always use `.equals()` (or unbox to `int`) for boxed numeric comparison, never `==`.

---

## Part 3 — Interview Questions (today's round)

**Q: Walk me through what happens between a `.class` file existing on disk and its static fields being ready to use.**
**A:** The Class Loader Subsystem finds and reads the bytes (**Loading**), producing a `Class` object. **Linking** then verifies the bytecode is structurally safe, **prepares** memory for static fields set to their type's default values (`0`, `null`, `false` — not yet the source-code initial values), and **resolves** symbolic references to other classes into real ones. Finally **Initialization** runs static initializer blocks and static field initializers in source order — this is the first point where `static int x = compute();` actually calls `compute()`. All of this happens lazily, the first time the class is actively used, not when it's merely referenced in a type declaration.

**Q: Why does the JVM use a parent-delegation model for class loading instead of just letting each loader load whatever it's asked to load?**
**A:** A load request is delegated up to the parent first, and only handled locally if the parent can't find the class. This guarantees core classes (`java.lang.String`, etc.) always resolve to the trusted Bootstrap loader's version, even if the application classpath happens to contain a class with the same fully-qualified name — without delegation, a rogue or accidental classpath entry could shadow a core JDK class, which is both a correctness and a security problem.

**Q: What is Metaspace, and why did it replace PermGen?**
**A:** Metaspace stores class metadata (class definitions, method bytecode, the runtime constant pool's structure). It replaced PermGen (which lived inside the fixed-size heap, causing frequent `OutOfMemoryError: PermGen space` in apps that loaded/unloaded many classes, e.g. app servers redeploying WARs) by moving into **native memory**, which grows dynamically by default instead of hitting a small fixed ceiling — though it can still be bounded with `-XX:MaxMetaspaceSize`. Full picture: [day2's doc](../day2/07-jvm-memory.md#metaspace).

**Q: Explain G1 GC at a high level — what makes it different from a purely generational collector?**
**A:** G1 divides the heap into many equal-sized regions instead of one contiguous Young space and one contiguous Old space; each region is dynamically labeled Eden/Survivor/Old as needed. On a collection cycle, it picks the regions containing the *most garbage* to reclaim first (hence "Garbage First"), working toward a configurable max pause-time goal rather than always collecting one whole fixed generation. It still does Minor GCs on Young regions and mixed collections that also reclaim some Old regions, so it's an evolution of the Young/Old generational split, not a replacement of the underlying idea.

**Q: Why are Java Strings immutable, and what would break if they weren't?**
**A:** Four independent reasons converge: safe sharing (the String Pool only works because a shared literal can't be mutated by one holder and silently change for everyone else), thread safety (no synchronization needed to share an object that can never change), security (prevents time-of-check/time-of-use bugs where a validated string, e.g. a class name or path, is mutated after validation but before use), and safe hashcode caching (`String.hashCode()` caches its result once, which is only correct if the content can never change afterward — critical since strings are extremely common `HashMap` keys). If Strings were mutable, all four would break: pooled literals could corrupt each other, concurrent readers would need locks around every string, validated inputs could be swapped post-check, and cached hash codes would go stale, corrupting `HashMap` bucket placement for any map already holding that string as a key.
