# Java — Collections Deep Dive

## Part 1 — Concepts (read this first)

### ArrayList
- **Internal structure**: backed by a plain `Object[] elementData`.
- **Resize mechanism**: when full, grows to `oldCapacity + (oldCapacity >> 1)` — i.e. **1.5×** the old capacity — then copies all elements into the new array (`Arrays.copyOf`).
- **Complexity**:
  | Operation | Complexity | Why |
  |---|---|---|
  | `get(i)` / `set(i, x)` | O(1) | direct array index |
  | `add(x)` (at end) | O(1) amortized | occasional O(n) resize, spread over many O(1) adds |
  | `add(i, x)` / `remove(i)` | O(n) | shifts subsequent elements |
  | `contains(x)` | O(n) | linear scan |

### LinkedList
- **Structure**: a **doubly linked list** — each node holds `prev`, `item`, `next`.
- **Random access vs insertion**:
  - `get(i)`: O(n) — must walk from head or tail (whichever is closer).
  - Insertion/removal **at a known node** (e.g., via a `ListIterator`): O(1) — just relink pointers, no shifting.
  - Insertion/removal by index still costs O(n) overall, because finding that index is O(n) even though the actual splice is O(1).
- Rarely the right default choice in Java — `ArrayList` usually wins even for "frequent insertion" workloads because of cache locality; `LinkedList` mainly makes sense when you already hold a reference to the node (e.g., implementing an LRU cache's usage list) or need `Deque` semantics.

### HashMap
- **Buckets**: an array of `Node<K,V>` (or, post-treeification, `TreeNode<K,V>`).
- **Hashing**: `index = (table.length - 1) & hash(key)`, where `hash(key) = key.hashCode() ^ (key.hashCode() >>> 16)`.
- **Treeification (Java 8+)**: once a single bucket accumulates ≥ 8 entries **and** the table has ≥ 64 buckets, that bucket's linked list converts into a red-black tree, dropping worst-case lookup from O(n) to O(log n) for that bucket. If the table is small (< 64 buckets), Java resizes instead of treeifying.
- **Load factor**: default **0.75** — the map resizes (doubles) once `size > capacity × loadFactor`. Lower load factor = more space, fewer collisions; higher = less space, more collisions.
- **Initial capacity**: default **16**, always a power of two (so the `& (length-1)` bit-mask trick works instead of a slower `%` modulo).

### HashSet
- Internally **is** a `HashMap<E, Object>` — every element you add becomes a key, mapped to a shared dummy value (`PRESENT`).
- **Why duplicates aren't allowed**: `add(x)` calls `map.put(x, PRESENT)`, and `HashMap.put` on an existing key (same `hashCode()` + `equals()`) overwrites the value rather than creating a new entry — so a "duplicate" add is a silent no-op that returns `false`.

### TreeMap
- Backed by a **Red-Black Tree** (a self-balancing BST).
- Guarantees **O(log n)** for `get`, `put`, `remove`, plus keeps keys in sorted order (via `Comparable` or a supplied `Comparator`) — enabling range operations like `firstKey()`, `higherKey()`, `subMap()`.
- Trade-off vs `HashMap`: slower (O(log n) vs O(1) average) but ordered.

### PriorityQueue
- Backed by a **binary heap**, stored compactly in an array (no tree nodes/pointers).
- **Default is a min-heap** — `poll()` returns the smallest element per natural ordering / provided `Comparator`.
- **Max-heap**: pass `Collections.reverseOrder()` or `(a, b) -> b - a` as the comparator.
- `offer`/`poll`: O(log n) (sift up/down). `peek`: O(1).

---

## Part 2 — Coding: LRU Cache

**Design**: need O(1) `get` and `put`, and to evict the *least recently used* entry when capacity is exceeded. Combine a `HashMap` (O(1) key lookup) with a **doubly linked list** (O(1) reordering to mark "recently used" and O(1) eviction from the tail).

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0); // dummy head (most recently used side)
    private final Node tail = new Node(0, 0); // dummy tail (least recently used side)

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        remove(node);
        insertAtFront(node); // touched -> most recently used
        return node.value;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            remove(map.get(key));
        }
        Node node = new Node(key, value);
        map.put(key, node);
        insertAtFront(node);

        if (map.size() > capacity) {
            Node lru = tail.prev;      // least recently used
            remove(lru);
            map.remove(lru.key);
        }
    }

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertAtFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private static class Node {
        int key, value;
        Node prev, next;
        Node(int key, int value) { this.key = key; this.value = value; }
    }
}
```

**Why this design**: the `HashMap` gives O(1) access to *any* node by key; the doubly linked list gives O(1) removal/insertion *without shifting* (unlike an `ArrayList`), because we already hold the node reference from the map — no need to search for it. Sentinel `head`/`tail` nodes remove null-checks at the boundaries.

Java shortcut: `LinkedHashMap` (with `accessOrder = true`) implements this exact structure internally and can be used directly by overriding `removeEldestEntry`. Worth mentioning in an interview, but implementing it manually (as above) shows you understand *why* it works.

---

## Part 3 — Interview Questions (today's round)

**Q: Why does HashMap become O(n)?**
**A:** O(1) relies on keys spreading evenly across buckets. It degrades to O(n) when many keys collide into the *same* bucket — e.g., a poor or malicious `hashCode()` implementation (all objects returning the same hash), or adversarial input designed to collide. In that case the bucket becomes one long linked list, and both `get` and `put` must scan it linearly. Since Java 8, this specific worst case is capped at O(log n) instead of O(n) once a bucket treeifies (≥ 8 entries, table ≥ 64 buckets) — but that only helps if keys implement `Comparable`; otherwise it still falls back to a linear scan even as a tree (Java compares by class name / identity hash as a tiebreaker).

**Q: Difference between ConcurrentHashMap and HashMap?**
**A:**
| | `HashMap` | `ConcurrentHashMap` |
|---|---|---|
| Thread safety | None — concurrent modification can corrupt internal state or infinite-loop (pre-Java 8) | Thread-safe by design |
| Locking | N/A | Java 8+: no bucket-level locks for reads; writes use `synchronized` on the head node of a bin, or CAS for empty bins — much finer-grained than the old single-lock/segment approach in Java 7 |
| Null keys/values | Allowed (one null key, many null values) | **Not allowed** — throws `NullPointerException`, because null would make `containsKey` ambiguous under concurrent access |
| Iterators | **Fail-fast** — throws `ConcurrentModificationException` if modified during iteration | **Weakly consistent** — never throws; may or may not reflect updates made during iteration |

**Q: Why is String immutable?**
**A:** Several reinforcing reasons:
- **String pool / interning**: literals can safely be shared (`"abc" == "abc"`) only if no one can mutate one and affect the other.
- **Hashcode caching**: `String.hashCode()` is computed once and cached — used heavily as `HashMap` keys; mutability would invalidate that cache silently and corrupt any map it was a key in.
- **Security**: strings are used for class names, file paths, network hosts, DB URLs; if mutable, code could pass a string for validation and mutate it afterward (TOCTOU-style attack).
- **Thread safety**: immutable objects are inherently safe to share across threads with no synchronization.

**Q: Explain fail-fast and fail-safe iterators.**
**A:**
- **Fail-fast** (`ArrayList`, `HashMap`, `HashSet` iterators): these track a `modCount` on the collection. If the collection is structurally modified (other than through the iterator's own `remove()`) while iterating, the next `next()` call throws `ConcurrentModificationException`. It's a best-effort bug-detection mechanism, not a hard guarantee.
- **Fail-safe** (`CopyOnWriteArrayList`, `ConcurrentHashMap` iterators): these iterate over a **snapshot** (or a structure designed for concurrent traversal) so concurrent modification never throws — but the iterator may not reflect changes made after it was created. Trade-off: safety and no exceptions, at the cost of possibly-stale views and (for `CopyOnWriteArrayList`) the cost of copying the array on every write.

**Q: What happens when you call `new ArrayList<>(100)`?**
**A:** It eagerly allocates a backing `Object[]` array of **capacity 100** (`elementData = new Object[100]`), while `size` remains 0. This is purely a capacity hint to avoid repeated resizes if you already know you'll add ~100 elements — no resize is needed until the 101st `add()`. Contrast with `new ArrayList<>()` (no-arg constructor): in modern Java this starts with an internal **empty shared array**, and only allocates a real array of capacity 10 lazily, on the *first* `add()`.
