# Coding Task — Custom HashMap (array + linked list, no resizing)

Full worked implementation lives in [`src/main/java/org/prep/day3/CustomHashMap.java`](../../src/main/java/org/prep/day3/CustomHashMap.java). This file explains the design decisions; read [02-java-hashmap-internals.md](02-java-hashmap-internals.md) first — this is that theory turned into code you write yourself.

## Part 1 — Design

Requirements: `put()`, `get()`, `remove()`, using **array + linked list**, **ignoring resizing** — the point of the exercise is to internalize bucket indexing and collision chaining, not to reimplement Java's full resize/treeify machinery.

```
CustomHashMap<K, V>
  table: Node<K,V>[] (fixed size, e.g. 16)

  Node<K,V>
    key, value
    next: Node<K,V>   ← singly linked list for collision chaining
```

### Index calculation
```java
private int indexFor(K key) {
    int h = (key == null) ? 0 : key.hashCode();
    h ^= (h >>> 16);              // same spreading trick as real HashMap — see 02-java-hashmap-internals.md
    return (h & 0x7fffffff) % table.length;
    // masking off the sign bit before % avoids a negative array index,
    // since Java's % can return negative for negative operands
}
```
A real `HashMap` uses `(n-1) & hash` and *requires* a power-of-two table size for that to be correct (see the Part 1 proof in the internals doc). This implementation uses `% table.length` instead specifically so the table size **doesn't** have to be a power of two — a deliberate simplification for a from-scratch exercise, at the cost of the AND-trick's performance benefit. (An alternative faithful-to-the-real-thing version would fix `table.length` at a power of two and use the AND mask — both are defensible; the accompanying code uses `%` for clarity.)

### `put(key, value)`
1. Compute the bucket index.
2. Walk the chain at that index. If a node with an equal key (`Objects.equals`, so it also works for `null` keys) is found, overwrite its value and return — no duplicate keys.
3. If no match found, prepend (or append) a new node to the chain.

### `get(key)`
1. Compute the bucket index.
2. Walk the chain, comparing keys with `.equals()`. Return the value on a match, or a sentinel (`null`, or throw, depending on API contract — real `HashMap.get` returns `null` for "absent," which is the ambiguity `ConcurrentHashMap` refuses to allow, discussed in the internals doc) if the chain is exhausted with no match.

### `remove(key)`
1. Compute the bucket index.
2. Walk the chain **keeping track of the previous node**, since removing from a singly linked list requires relinking `prev.next = current.next` (or updating `table[index]` directly if the match is the head node).

---

## Part 2 — Why this makes HashMap internals "crystal clear"

Writing this by hand forces you to confront, concretely, the exact things that are otherwise abstract bullet points:
- **Index collision is not an edge case** — with a small fixed table and no resizing, you will hit collisions constantly during testing, and the linked-list walk in `get`/`remove` stops being theoretical.
- **Equality vs identity** — using `.equals()` (not `==`) for key comparison is what makes `new String("a")` match a previously-stored `"a"` key; get this wrong and the map silently "loses" keys that look equal but aren't the same object.
- **Why real HashMap resizes** — without resizing, pushing far more entries than `table.length` into this implementation makes every bucket's chain long, and `get`/`put` visibly degrade toward O(n) — the exact motivation for load factor and resize in the real implementation.
- **Null handling** — deciding what `get()` on a missing key returns, and whether `null` keys are allowed at all, is the same ambiguity that made `ConcurrentHashMap` ban nulls outright (see [02-java-hashmap-internals.md](02-java-hashmap-internals.md), Part 3).

## Part 3 — Self-check questions

- What breaks if you compare keys with `==` instead of `.equals()`?
- What happens to `get()`'s correctness (not performance) if you never resize and keep adding entries — does it still return the right answer, just slower? (Yes — chaining means correctness is independent of load; only performance degrades. This is worth stating explicitly, since it's a common point of confusion.)
- If you were asked to add resizing, what would trigger it, and what's the cheapest way to move entries into a bigger table? (Answer: this is literally load factor + rehashing from [02-java-hashmap-internals.md](02-java-hashmap-internals.md) — try implementing it as a stretch goal.)
