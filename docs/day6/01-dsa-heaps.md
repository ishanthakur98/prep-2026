# DSA — Heaps (Priority Queue)

## Part 1 — Concepts (read this first)

### Heap vs Binary Tree
A heap **is** a binary tree, with two extra constraints a general binary tree doesn't have:
1. **Shape property** — it's a **complete** binary tree: every level is fully filled except possibly the last, which fills strictly left-to-right, no gaps. This is what makes an **array** a valid, efficient underlying representation (see below) — a general binary tree needs actual node/pointer objects because it has no such shape guarantee.
2. **Heap property** — every parent is ordered relative to its children: **Min Heap** (parent ≤ both children) or **Max Heap** (parent ≥ both children), enforced *only* between parent and child, not across siblings or full root-to-leaf paths.

That second point is the one that trips people up coming from BSTs: a heap is **not sorted** and is **not a BST**. A BST guarantees left < node < right at every subtree, which lets you binary-search it. A heap only guarantees parent-vs-children, so you can't binary-search a heap, and an inorder traversal of a heap is *not* sorted order — the only thing a heap promises efficiently is "give me the min (or max) fast."

### Array representation (why no `Node`/`left`/`right` needed)
Because a heap is always a *complete* tree, it can be stored densely in a plain array with pure index arithmetic — no pointers:
```
index i (0-based):
  parent(i)      = (i - 1) / 2
  leftChild(i)   = 2*i + 1
  rightChild(i)  = 2*i + 2
```
This is exactly what backs Java's `PriorityQueue` internally (see the interview section below) — an `Object[]` array, not a linked node structure.

### Min Heap / Max Heap
- **Min Heap**: root is always the smallest element. `peek()`/`poll()` give you the minimum in O(1)/O(log n).
- **Max Heap**: root is always the largest. Java's `PriorityQueue<Integer>` is a **min-heap by default**; get a max-heap by supplying a reversed comparator: `new PriorityQueue<>(Collections.reverseOrder())` (see [`LastStoneWeight.java`](../../src/main/java/org/prep/day6/LastStoneWeight.java)).

### Heapify — sift up / sift down
Two operations restore the heap property after it's been locally violated, and they only ever move an element **along one path** (root-to-leaf or leaf-to-root), never touch the whole tree:

- **Sift up (bubble up)** — used after **insert**. Append the new element at the next free array slot (end of the array — this preserves the shape property automatically), then repeatedly compare it to its parent and swap upward while it violates the heap property. Stops as soon as the parent is smaller (min-heap) or hits the root.
- **Sift down (bubble down / heapify-down)** — used after **removing the root**. Move the *last* array element into the now-empty root slot (keeps the shape property intact), then repeatedly swap it downward with its smaller child (min-heap) while it violates the heap property, until it's smaller than both children or hits a leaf.

Both are bounded by the tree's **height**, `O(log n)`, because a complete tree with `n` nodes has height `⌊log₂ n⌋` — this single fact is the source of every heap operation's complexity below.

**Building a heap from `n` elements**: naively sifting-up `n` elements one at a time is `O(n log n)`. Repeatedly sifting **down** starting from the last non-leaf node backward to the root is `O(n)` — a classic, non-obvious result. Intuition: most nodes in a complete tree are near the bottom (a leaf-heavy tree), and sift-down's cost is bounded by *that node's distance to a leaf*, not the full tree height — summing "count of nodes at height h" × "h" across all levels converges to `O(n)`, not `O(n log h)`. `PriorityQueue(Collection)`'s bulk constructor uses this `O(n)` heapify rather than `n` individual `offer()` calls.

### Top K pattern
The recurring shape behind most of today's problems: **"find the k largest/smallest/most-frequent things in a stream or array."** The trick is which heap type to pick — it's the opposite of what people guess on first instinct:

- **To find the k *largest* elements, keep a MIN-heap of size k.** Push everything; whenever the heap exceeds size k, pop the min. The min-heap's root is always the *smallest of the current top-k*, so popping it evicts the correct candidate, and after processing everything the heap's root is exactly the kth largest.
- **To find the k *smallest* elements, keep a MAX-heap of size k**, symmetric reasoning.

Why not just keep a heap of *all n* elements? Because bounding the heap at size `k` bounds every `offer`/`poll` at `O(log k)` instead of `O(log n)` — when `k << n` (e.g. "top 10 out of a billion-row stream"), this is the entire point of the pattern, not a minor optimization.

### Complexity summary
| Operation | Time | Why |
|---|---|---|
| `peek()` / find min (or max) | O(1) | Always sitting at index 0 |
| `offer()` / insert | O(log n) | Append + sift up, bounded by tree height |
| `poll()` / remove root | O(log n) | Move last element to root + sift down, bounded by tree height |
| Build heap from `n` elements | O(n) | Bottom-up sift-down, see above |
| Search for an arbitrary value | O(n) | No structural guarantee beyond parent/child — must scan |

---

## Part 2 — Problems

All solutions are committed and runnable under [`src/main/java/org/prep/day6/`](../../src/main/java/org/prep/day6/).

### Easy — Kth Largest Element in a Stream
[`KthLargestInStream.java`](../../src/main/java/org/prep/day6/KthLargestInStream.java) — classic Top K setup: a min-heap capped at size `k`; `add()` offers the new value then evicts the min if the heap overflows past `k`. The root is always the answer. `add()`: O(log k).

### Medium — Kth Largest Element in an Array
[`KthLargestElement.java`](../../src/main/java/org/prep/day6/KthLargestElement.java) — same min-heap-of-size-k idea applied to a fixed array instead of a stream. O(n log k) overall, O(k) space. (A **Quickselect** partition-based approach gets this to average O(n) — worth naming as the follow-up optimization interviewers sometimes push for, at the cost of worst-case O(n²) and destroying the input array's order.)

### Medium — Top K Frequent Elements
[`TopKFrequentElements.java`](../../src/main/java/org/prep/day6/TopKFrequentElements.java) — count frequencies with a `HashMap` first (O(n)), then run the same min-heap-of-size-k pattern over the `(value, frequency)` entries, ordered by frequency instead of value. O(n log k) total.

### Medium — Last Stone Weight
[`LastStoneWeight.java`](../../src/main/java/org/prep/day6/LastStoneWeight.java) — simulation problem: repeatedly need "the two current largest values," which is exactly what a max-heap gives you in O(log n) per access, versus O(n log n) if you re-sorted the array every round. O(n log n) total across all smashes.

### Medium — Find K Closest Elements
[`FindKClosestElements.java`](../../src/main/java/org/prep/day6/FindKClosestElements.java) — Top K pattern again, but "closest to `x`" (smallest `|val - x|`) is the ranking key instead of raw value, so evict the *farthest* (max-heap by distance) whenever the heap exceeds size `k`. O(n log k).

**Worth knowing as the more-optimal alternative** (interviewers often ask this as a follow-up specifically because the input is sorted): binary search for the left edge of a size-`k` sliding window directly, comparing `x - arr[mid]` vs `arr[mid+k] - x` at each step — O(log(n-k) + k), no heap at all. The heap approach here is the general-purpose one that doesn't need sorted input; the binary-search one exploits that this problem's input happens to be sorted.

---

## Part 3 — Interview Discussion

**Q: Why use a Heap instead of just sorting?**
**A:** Sorting the full input is `O(n log n)` and gives you *everything* in order, which is more work than most "top k" or "streaming min/max" problems actually need. A heap gives `O(1)` access to just the min/max, `O(log n)` insert/remove, and — critically for the Top K pattern — can be **bounded to size k**, making every operation `O(log k)` instead of `O(log n)`. For a live stream where new elements keep arriving (Kth Largest in a Stream), sorting isn't even a one-time cost — you'd have to re-sort (or maintain a sorted structure) on every insert; a size-k heap handles each new element in `O(log k)` and never re-touches the rest.

**Q: What's the time complexity of insert and delete, and why?**
**A:** Both are `O(log n)`. Insert appends to the end of the array (`O(1)`, preserves the shape property automatically) then sifts up along a single root-to-leaf path — bounded by the tree's height, `⌊log₂ n⌋`, since it's a complete tree. Delete (of the root, the only delete a heap directly supports efficiently) moves the last array element into the root slot then sifts it down along one path — same height bound. Neither operation ever has to touch more than one root-to-leaf path, which is exactly what the height bound buys you.

**Q: How is Java's `PriorityQueue` actually implemented internally?**
**A:** A **binary min-heap backed by a resizable `Object[]` array** (`transient Object[] queue`), using the index arithmetic above (`parent = (i-1)/2`, `children = 2i+1, 2i+2`) — no `Node`/pointer objects at all. `offer()` appends then calls `siftUp`; `poll()` swaps the last element into the root slot, shrinks the logical size, and calls `siftDown`. Ordering is natural (`Comparable`) by default, or a supplied `Comparator` if the constructor is given one — that's the whole mechanism behind flipping it into a max-heap with `Collections.reverseOrder()`. It's **not thread-safe** (`PriorityBlockingQueue` is the concurrent equivalent, relevant given today's Java concurrency topic covers `BlockingQueue` implementations). One subtlety worth naming: **iterating** a `PriorityQueue` (`for (int x : pq)`) does **not** visit elements in sorted/priority order — only repeated `poll()` does, since the array only satisfies the parent/child heap property, not full linear ordering.
