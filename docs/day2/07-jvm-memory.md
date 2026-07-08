# Bonus — JVM Memory Layout

## Part 1 — Concept

### Stack
- **Per-thread** — each thread gets its own stack when created.
- Stores **stack frames**, one per method call in progress: local variables, method parameters, and the return address.
- Primitives and **object references** (not the objects themselves) live here.
- Automatically reclaimed when a method returns — no GC involved.
- `StackOverflowError` when a thread's stack exceeds its size (typically from unterminated/too-deep recursion).

### Heap
- **Shared across all threads** — the single largest memory region, where all objects (via `new`) and arrays live.
- Divided generationally, because most objects die young (weak generational hypothesis):
  - **Young Generation**: `Eden` (where new objects are first allocated) + two `Survivor` spaces (`S0`, `S1`). Minor GC runs frequently here; surviving objects get copied between survivor spaces and their "age" incremented each time.
  - **Old Generation (Tenured)**: objects that survive enough minor GCs get **promoted** here. Major/Full GC runs less often but is more expensive (scans a much bigger region).
- `OutOfMemoryError: Java heap space` when the heap is exhausted and GC can't reclaim enough.

### Metaspace
- Replaced **PermGen** since Java 8.
- Stores **class metadata**: class definitions, method bytecode, runtime constant pool structure (not the constants' *values* — those live in the heap since Java 7).
- Lives in **native memory** (outside the JVM heap), so it grows dynamically by default instead of hitting a fixed PermGen-style ceiling — though it can still be bounded via `-XX:MaxMetaspaceSize`.
- `OutOfMemoryError: Metaspace` typically signals a classloader leak (e.g., repeatedly redeploying an app without unloading old classes).

### String Pool
- A special region (part of the **heap** since Java 7, was PermGen before) holding **interned** string literals.
- `String s1 = "abc";` and `String s2 = "abc";` → both `s1` and `s2` point to the **same** pooled object (`s1 == s2` is `true`).
- `String s3 = new String("abc");` forces a **new** object on the heap outside the pool (`s3 == s1` is `false`, even though `s3.equals(s1)` is `true`).
- `.intern()` manually adds/retrieves a string from the pool.
- Exists because strings are immutable and extremely common (class names, keys, literals) — sharing them saves significant memory.

### Garbage Collection
- Reclaims heap memory occupied by objects no longer reachable from GC roots (stack references, static fields, etc.).
- **Minor GC**: cleans the Young Generation (Eden + Survivors) — frequent, fast.
- **Major/Full GC**: cleans the Old Generation (and typically Young too) — rarer, much more expensive (can cause noticeable pause times, "stop-the-world").
- Common collectors: **Serial** (single-threaded, small apps), **Parallel** (multi-threaded, throughput-focused, old default), **CMS** (concurrent, low-pause, deprecated), **G1** (region-based, default since Java 9, balances throughput and pause time), **ZGC/Shenandoah** (very low pause, for huge heaps).

---

## Part 2 — Draw this from memory

```
                         JVM MEMORY LAYOUT
 ┌───────────────────────────────────────────────────────────┐
 │                          HEAP (shared)                    │
 │  ┌───────────────────────────┐   ┌───────────────────┐    │
 │  │     Young Generation      │   │   Old Generation   │   │
 │  │ ┌───────┐ ┌────┐ ┌────┐   │   │     (Tenured)      │   │
 │  │ │ Eden  │ │ S0 │ │ S1 │   │──►│                     │   │
 │  │ └───────┘ └────┘ └────┘   │   │  long-lived objects │   │
 │  │  (Minor GC happens here)  │   │  (Major/Full GC)    │   │
 │  └───────────────────────────┘   └───────────────────┘    │
 │              String Pool (interned literals)               │
 └───────────────────────────────────────────────────────────┘

 ┌───────────────────────────────────────────────────────────┐
 │   METASPACE (native memory, not on the heap)               │
 │   class metadata, method bytecode, runtime constant pool   │
 │   structure                                                │
 └───────────────────────────────────────────────────────────┘

 Thread 1 Stack     Thread 2 Stack     Thread 3 Stack   ...
 ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │ frame: main()│   │ frame: run() │   │ frame: run() │
 │ frame: foo() │   │              │   │ frame: bar() │
 │ local vars,  │   │ local vars,  │   │ local vars,  │
 │ obj refs     │   │ obj refs     │   │ obj refs     │
 └──────────────┘   └──────────────┘   └──────────────┘
 (one stack PER THREAD — not shared)
```

**Flow to narrate while drawing**: object created with `new` → allocated in **Eden**. Survives a minor GC → copied to a **Survivor** space, age++. Survives enough minor GCs (tenuring threshold) → **promoted to Old Gen**. Old Gen fills up → **Major GC** runs. Meanwhile, the *reference* to that object sits in whichever thread's **stack frame** created it, and the object's **class definition** (not the object itself) lives in **Metaspace**. String literals bypass all of this generational dance and go straight into the shared **String Pool**.
