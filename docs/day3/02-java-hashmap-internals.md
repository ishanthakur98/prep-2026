# Java — HashMap Internals (You Should Know Everything)

> Builds on [../day2/01-dsa-hashmap-sliding-window.md](../day2/01-dsa-hashmap-sliding-window.md) and [../day2/02-java-collections.md](../day2/02-java-collections.md), which cover the bucket/collision/load-factor basics. This file goes end-to-end: the full chain from `hashCode()` to resize, then `ConcurrentHashMap`.

## Part 1 — The full chain

```
hashCode()  →  spread (h ^ (h >>> 16))  →  index = (n-1) & hash  →  bucket
   ↓
bucket empty?          → new Node, done
bucket has entries?     → walk the chain, compare hash then equals()
   ↓
key matches an existing node? → overwrite value, return old value
key matches nothing?          → append new Node to the end of the chain
   ↓
chain length ≥ 8 AND table capacity ≥ 64?  → treeify (linked list → red-black tree)
   ↓
size > capacity × loadFactor (0.75)?  → resize (double capacity) → rehash every entry
```

### `hashCode()` → spread
`HashMap` never uses `key.hashCode()` raw. It computes:
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
This XORs the hash with its own upper 16 bits shifted down. **Why**: `index = (n-1) & hash` only ever looks at the *low* bits of `hash` (see below), so if a key's hashCode differs only in high bits (common for poorly-distributed hashCodes, e.g. object identity hashes that increment in a pattern), those differences would be invisible to indexing without spreading them down first. This is a cheap way to "fold" high-bit entropy into the low bits that actually matter.

### Index calculation: `(n-1) & hash`, not `hash % n`
```java
index = (table.length - 1) & hash
```
**Why not `hash % n`**: functionally, `hash % n` gives the same result *only when `n` is a power of two* — but bitwise AND is a single fast CPU instruction, while integer modulo (`%`) is a division operation, meaningfully slower at the scale HashMap operates. AND is also branch-free and trivially correct for negative hashes when `n` is a power of two (whereas `%` can return negative values needing extra handling for negative `hash` in general, though the mask trick avoids that entirely).

### Why capacity must be a power of two
Two independent reasons, both required for `(n-1) & hash` to work correctly:
1. **Correctness**: when `n` is a power of two, `n-1` in binary is all 1-bits (e.g. `16-1 = 15 = 0b1111`), so `(n-1) & hash` is exactly equivalent to `hash % n` — it masks off everything except the low `log2(n)` bits, uniformly distributing indices across `[0, n-1]` for any hash. If `n` weren't a power of two, `n-1` would have 0-bits mixed in, and the AND would silently produce a biased, non-uniform, and incorrect subset of possible indices.
2. **Resize consistency**: doubling the capacity (power of two → power of two) means the mask just gains one more 1-bit, which is exactly what makes the O(1)-per-entry rehash optimization below possible.

### Bucket, collision, linked list
Each slot in the backing `Node<K,V>[] table` is a **bucket**. A **collision** is when two different keys compute the same index. Java resolves this with **separate chaining** — the bucket holds a singly linked list (`Node.next`) of every entry that landed there. `get`/`put` compute the index, then walk that list comparing `hash` first (fast int comparison, cheap pre-filter) and only calling `equals()` when hashes match.

### TreeNode / treeification
Since Java 8, if a single bucket's chain grows to **≥ 8 entries** *and* the table has **≥ 64 buckets** total, that bucket converts from a linked list to a **red-black tree** (`TreeNode<K,V>`, which extends `LinkedHashMap.Entry`, itself extending `Node`). This caps worst-case lookup within that bucket at **O(log n)** instead of O(n). If the table has fewer than 64 buckets, Java prefers to **resize** instead of treeifying — treeifying a small table is a sign the real fix is more buckets, not a smarter bucket. Trees revert back to linked lists (**untreeify**) if the bucket shrinks below 6 entries (during removal), to avoid tree overhead for small chains.
Note: treeification only helps if keys implement `Comparable` (or the tree falls back to comparing class names / `System.identityHashCode` as a tiebreaker) — it's an optimization for the common case, not a hard guarantee against pathological hashCodes.

### Resize
Triggered when `size > threshold`, where `threshold = capacity × loadFactor`. On resize, **capacity doubles** (16 → 32 → 64 → ...) and every entry must be **rehashed** into the new, larger table (since `index = (n-1) & hash` depends on `n`).

### Rehashing — the Java 8 optimization
Naively, resizing means recomputing `(newCapacity - 1) & hash` for every single entry from scratch — O(n) work with n hash computations. Java 8 avoids recomputing anything: because capacity always **doubles**, the new mask (`newCapacity - 1`) is the old mask with exactly **one extra high bit** set. For any given entry, that one new bit in `hash` is either 0 or 1:
- If 0: the entry's index **doesn't change** — it stays in the same bucket position.
- If 1: the entry's new index is **exactly `oldIndex + oldCapacity`**.

So Java splits each old bucket's chain into two chains ("low" and "high") using a single bit test (`hash & oldCapacity`) per entry, and relinks them directly — no hash recomputation, no rehash function called again. This is why resize, while still O(n) overall (every entry is touched once), is much cheaper per-entry than a naive full rehash.

### Load factor
Default **0.75** — the ratio of `size` to `capacity` at which a resize triggers. It's a tradeoff:
- Lower (e.g. 0.5): more empty buckets, fewer collisions, faster lookups, more wasted memory.
- Higher (e.g. 0.9): less wasted memory, more collisions, slower lookups.
0.75 is empirically the sweet spot Java's designers chose balancing time and space cost for a general-purpose default.

### Capacity
Default initial capacity is **16**, always kept a power of two (even if you pass a non-power-of-two to the constructor — `HashMap` rounds it up to the next power of two internally via `tableSizeFor`).

---

## Part 2 — Interview Questions: internals walkthrough

**Q: Explain `new HashMap<>()` internally.**

**A:** The no-arg constructor does almost nothing eagerly — it just sets `loadFactor = 0.75` (the default) and leaves the backing `table` array **null**. No array of 16 buckets is allocated yet. Capacity 16 and the first `threshold` (12 = 16 × 0.75) are only established lazily, on the **first `put()`** call, inside `resize()` (which doubles as both "grow" and "initial lazy allocation" — if `table == null`, it allocates a fresh 16-slot array instead of doubling one). This lazy allocation matters if you create many `HashMap`s that end up empty (e.g. one per request that short-circuits) — you don't pay for the backing array until it's actually used.

**Q: What happens, step by step, after `map.put("ABC", 1)`?**

**A:**
1. Compute `hash("ABC")` via `key.hashCode()` (String's hashCode is `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]`, cached after first computation), then spread it: `h ^ (h >>> 16)`.
2. If `table` is `null` (first put ever on this map), call `resize()` to lazily allocate the initial 16-bucket array.
3. Compute `index = (table.length - 1) & hash`.
4. If `table[index]` is empty, place a new `Node("ABC", 1, hash, null)` there directly. Done.
5. If `table[index]` already has a chain, walk it:
   - If a node's `hash` matches *and* (`key == existingKey` or `key.equals(existingKey)`), that's an update — overwrite the value, return the old value, **no new node created**.
   - Otherwise reach the end of the chain and append a new node.
6. If, after insertion, that bucket's chain has grown to ≥ 8 nodes and `table.length >= 64`, treeify that bucket.
7. Increment `size`. If `size > threshold` (capacity × 0.75), call `resize()` — capacity doubles, every existing entry is redistributed using the one-bit split trick described above.
8. Return `null` (no previous value existed for `"ABC"`) — or the old value if step 5 hit an update.

**Q: Why is capacity a power of two — restate concretely for `"ABC"`.**

**A:** Say `hash("ABC")` (after spreading) is some 32-bit int `H`. With capacity 16, `index = 15 & H` — this keeps only the low 4 bits of `H`, giving a uniform value in `[0,15]` for any `H`, because `1111` in binary has no gaps. If capacity were, say, 12 (not a power of two), `11 & H` (`1011` in binary) has a 0 in the second-lowest bit position — meaning index `2`, `6`, `10`, `14`, etc. could *never* be produced by the AND, no matter what `H` is, silently wasting a chunk of the table and clustering everything into the remaining slots. Power-of-two capacity is what guarantees the bitmask covers every index in range uniformly.

---

## Part 3 — ConcurrentHashMap

### The core problem
`HashMap` is not thread-safe: concurrent `put()`s can race on the same bucket's linked-list pointers. In pre-Java-8 `HashMap`, a race during resize could even corrupt the list into a **cycle**, causing `get()` to infinite-loop (a documented, real production failure mode — not a hypothetical). `ConcurrentHashMap` (CHM) exists to give map semantics with genuine concurrent throughput, not just a `synchronized` wrapper around every method (which `Collections.synchronizedMap(new HashMap<>())` already does, at the cost of serializing *every* operation behind one lock).

### Pre-Java-8: Segment locking
`ConcurrentHashMap` internally partitioned the table into a fixed number of **segments** (default 16), each an independently lockable mini hash table with its own lock. A `put()` only needed to lock the **one segment** the key's hash fell into — so up to 16 threads could write concurrently, one per segment, as long as they hit different segments. Reads (`get`) were mostly lock-free (volatile reads), only rarely needing to acquire a segment lock (e.g. mid-resize consistency edge cases). This was a big improvement over one global lock, but concurrency was capped at the segment count, fixed at creation time.

### Java 8+: finer-grained, no fixed segment count
Java 8 dropped the `Segment` architecture entirely in favor of locking at the level of **individual bucket bins** (i.e. as fine-grained as `HashMap`'s own buckets), using a combination of:
- **CAS (Compare-And-Swap)** for the common case: inserting into an **empty bucket** is done via `Unsafe.compareAndSwapObject` (no lock at all) — if the CAS succeeds, done; if it fails (another thread beat you to it), retry.
- **`synchronized` on the bucket's first node** for the case where the bucket is **non-empty**: only that one bin's head node is locked while appending to the chain or treeifying, so writes to *different* bins never contend at all — concurrency now scales with table size, not a fixed segment count.
- Reads (`get`) are still effectively lock-free — table array and `Node.val`/`Node.next` are `volatile`, so a reader always sees a consistent, published state without needing a lock; readers *never* block writers and vice versa.
- Resize is cooperative and concurrent: multiple threads can help transfer bins to the new table simultaneously (via a `transfer` method with a `ForwardingNode` marker left in already-migrated bins), rather than one thread stopping the world.

### Thread safety guarantees
- `size()` is an approximation under heavy concurrent modification (computed via a striped `LongAdder`-style counter, not a lock over the whole map) — treat exact size as unreliable if writes are ongoing.
- Iterators are **weakly consistent**: they never throw `ConcurrentModificationException`, and may or may not reflect concurrent modifications made during iteration — but they will never repeat or skip an entry that isn't concurrently modified.
- **No null keys or values** — unlike `HashMap`. Reason: in a concurrent map, `map.get(key) == null` is ambiguous (does the key not exist, or does it map to `null`?). In single-threaded `HashMap` you can disambiguate with a follow-up `containsKey()` check; under concurrency, another thread could remove the entry between your `get()` and `containsKey()`, so the check is inherently racy — CHM's designers eliminated the ambiguity at the source by disallowing `null` outright.

---

## Part 4 — Interview Questions (today's round)

**Q: Difference between `HashMap`, `Hashtable`, and `ConcurrentHashMap`.**

**A:**
| | `HashMap` | `Hashtable` | `ConcurrentHashMap` |
|---|---|---|---|
| Thread safety | None | Fully synchronized (every method) | Thread-safe, fine-grained |
| Locking | N/A | One lock for the entire table — every read/write serializes | Java 8+: CAS on empty bins, `synchronized` per-bin on non-empty bins |
| Null keys/values | 1 null key, many null values allowed | **Not allowed** (throws NPE) | **Not allowed** (throws NPE) |
| Performance under concurrency | Unsafe — don't use concurrently | Poor — single lock is a bottleneck | Good — contention only on the same bin |
| Iterators | Fail-fast | Fail-fast (`Enumeration` is not even fail-fast) | Weakly consistent, never throws |
| Legacy status | Modern default for single-threaded use | Legacy (predates the Collections framework); effectively superseded | Modern default for concurrent use |

**Q: Why is `HashMap` not thread-safe?**

**A:** Every mutation (`put`, `resize`) touches shared mutable state — the `table` array and each bucket's linked-list pointers — with **no synchronization at all**. Two threads calling `put()` concurrently can race on appending to the same bucket's chain (one thread's write can be silently lost), and worse, two threads triggering a `resize()` concurrently in pre-Java-8 `HashMap` could corrupt the chain into a **cycle** (each thread partially relinks nodes assuming exclusive access), so a subsequent `get()` on that bucket loops forever — this was a real, documented production incident pattern, not a theoretical concern. Java 8 changed the resize algorithm (see rehashing above) which happens to make the cycle bug specifically far less likely, but `HashMap` is still fundamentally unsynchronized and unsafe for concurrent modification.

**Q: Why does `HashMap` resize?**

**A:** To keep average bucket occupancy low, which is the entire basis for O(1) average lookup. As `size` grows past `capacity × loadFactor` (0.75), buckets start accumulating longer chains, degrading lookup toward O(n) per bucket. Doubling capacity roughly halves average chain length back down, trading one O(n) rehash pass for restoring O(1) amortized performance on all future operations — the same amortization argument as `ArrayList` growth.

**Q: What is rehashing?**

**A:** The process of recomputing each existing entry's bucket index after a resize, since `index = (n-1) & hash` depends on the table size `n` — growing `n` changes where every entry belongs. Naively this means calling the hash function again and recomputing the index for every entry; Java 8's optimization (described above) avoids recomputing the hash by exploiting that capacity always doubles — each entry either stays in the same bucket index or moves to exactly `oldIndex + oldCapacity`, decided by a single extra bit, so rehashing becomes a cheap bucket split rather than a full recompute.
