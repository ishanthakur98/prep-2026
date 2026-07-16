# DSA — Trees

## Part 1 — Concepts (read this first)

### Binary Tree vs Binary Search Tree (BST)
A **binary tree** is just a tree where every node has at most two children (`left`, `right`) — no ordering rule. A **BST** is a binary tree with one extra invariant: for every node, everything in its **left** subtree is smaller, everything in its **right** subtree is larger (no duplicates, or a consistent tie-breaking rule if duplicates are allowed). That single invariant is what makes search/insert/delete `O(log n)` on a balanced BST — at every node you can discard one whole subtree instead of looking at it, the same "halve the search space" idea as binary search on arrays (see [../day3/01-dsa-binary-search.md](../day3/01-dsa-binary-search.md)), except the halving is baked into the data structure's shape instead of computed on an index range each time.

The BST invariant is **global**, not just "left child < node < right child" — every node in the left subtree, however deep, must still be less than the root. This is exactly the trap [Validate Binary Search Tree](#5-validate-binary-search-tree) below is built to catch: checking only immediate children misses violations further down.

If a BST is allowed to grow unbalanced (e.g. inserting `1,2,3,4,5` in order produces a straight line, not a tree), it degrades to a linked list and all operations become `O(n)`. Self-balancing variants (AVL, Red-Black trees — used inside `TreeMap`/`TreeSet`) rebalance on insert/delete to guarantee `O(log n)` height; that's a study topic on its own, not needed to solve today's problems, but worth knowing *why* `TreeMap` gives you sorted-order guarantees with `O(log n)` operations.

### DFS traversal orders
All three are the same recursive shape — visit left subtree, visit right subtree, visit the node itself — just in a different order relative to "visit the node":

| Order | Sequence | Typical use |
|---|---|---|
| **Preorder** | node → left → right | Copying/serializing a tree (you see the root before its children, so you can reconstruct top-down) |
| **Inorder** | left → node → right | **On a BST, inorder traversal visits nodes in sorted ascending order** — this is the single most important fact about inorder traversal, and the basis of [Kth Smallest](#7-kth-smallest-element-in-bst) below |
| **Postorder** | left → right → node | Anything where children must be fully processed before the parent (deleting a tree node-by-node, computing subtree sizes/heights bottom-up) |

```
        4
       / \
      2   6
     / \ / \
    1  3 5  7

Preorder:  4 2 1 3 6 5 7   (node first)
Inorder:   1 2 3 4 5 6 7   (sorted! because it's a BST)
Postorder: 1 3 2 5 7 6 4   (node last)
```

### BFS (Level Order)
Visits the tree level by level, left to right within a level, instead of diving deep first. DFS uses the (implicit, recursive) **call stack**; BFS uses an explicit **queue** — enqueue a node, then when you dequeue it, enqueue its children. This is the same stack-vs-queue distinction that separates DFS and BFS on graphs generally: a stack naturally goes "deep before wide," a queue naturally goes "wide before deep." Level order is the standard way to reconstruct a tree level-by-level or to answer "shortest path in an unweighted tree/graph" style questions.

### Recursion on trees
Nearly every tree problem has the same skeleton:
```java
ReturnType solve(TreeNode node) {
    if (node == null) return baseCaseValue;       // base case: empty subtree
    ReturnType left = solve(node.left);            // recurse left
    ReturnType right = solve(node.right);           // recurse right
    return combine(node.val, left, right);          // combine into this node's answer
}
```
The recursion tree here *is* the actual tree — no need to construct anything, unlike backtracking's implicit search-space tree (see [../day4/01-dsa-recursion-backtracking.md](../day4/01-dsa-recursion-backtracking.md)). Depth of recursion = height of the tree, so a balanced tree of `n` nodes recurses `O(log n)` deep, but a degenerate (linked-list-shaped) tree recurses `O(n)` deep — worth naming out loud in an interview since it affects both time and the `StackOverflowError` risk from [day 4's recursion doc](../day4/01-dsa-recursion-backtracking.md#stack-frames).

---

## Part 2 — Problems

Shared node definition used by every solution below ([`TreeNode.java`](../../src/main/java/org/prep/day5/TreeNode.java)):
```java
public class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}
```

### 1. Maximum Depth of Binary Tree
**Problem**: Return the number of nodes along the longest path from root to a leaf.

**Recursive**:
```java
public static int maxDepth(TreeNode root) {
    if (root == null) return 0;                        // base case: empty tree has depth 0
    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
}
```
**Iterative (BFS, count levels)**:
```java
public static int maxDepthIterative(TreeNode root) {
    if (root == null) return 0;
    Queue<TreeNode> queue = new LinkedList<>();
    queue.add(root);
    int depth = 0;
    while (!queue.isEmpty()) {
        int levelSize = queue.size();                  // snapshot: exactly this many nodes are "this level"
        for (int i = 0; i < levelSize; i++) {
            TreeNode node = queue.poll();
            if (node.left != null) queue.add(node.left);
            if (node.right != null) queue.add(node.right);
        }
        depth++;                                        // one full level processed
    }
    return depth;
}
```
**Why snapshot `levelSize`**: the queue is being appended to *while* you drain it (children go in as you poll parents). Capturing `queue.size()` before the inner loop is what lets you process "exactly one level" per outer iteration instead of an arbitrary, shifting boundary.
**Complexity**: `O(n)` time (visit every node once), `O(h)` recursive / `O(w)` iterative extra space, where `h` = height and `w` = max width of the tree.

---

### 2. Same Tree
**Problem**: Given two binary trees, return whether they are structurally identical with the same node values.

```java
public static boolean isSameTree(TreeNode p, TreeNode q) {
    if (p == null && q == null) return true;            // both empty at this position -> match
    if (p == null || q == null) return false;            // exactly one is empty -> shapes differ
    if (p.val != q.val) return false;                    // values differ at this position
    return isSameTree(p.left, q.left) && isSameTree(p.right, q.right);
}
```
**Decision tree**: three ways to fail (one side null, one side not; values differ) and one way to keep going (both non-null, values equal — recurse into both children). The `&&` short-circuits, so a mismatch anywhere stops further recursion immediately.
**Complexity**: `O(min(n, m))` — stops as soon as a mismatch is found, worst case visits every node of the smaller tree.

---

### 3. Invert Binary Tree
**Problem**: Mirror a binary tree (swap every node's left and right children, recursively).

```java
public static TreeNode invertTree(TreeNode root) {
    if (root == null) return null;
    TreeNode left = invertTree(root.left);               // invert left subtree first
    TreeNode right = invertTree(root.right);              // invert right subtree first
    root.left = right;                                    // then swap at this node
    root.right = left;
    return root;
}
```
**Why it doesn't matter that this is technically postorder-ish**: the two recursive calls don't depend on each other's result (inverting the left subtree doesn't need to know anything about the right), so the order of the two recursive calls is irrelevant — only the final swap needs both results ready.
**Complexity**: `O(n)` time, one visit per node, `O(h)` space for the recursion stack.

---

### 4. Binary Tree Level Order Traversal
**Problem**: Return node values grouped by level, top to bottom.

```java
public static List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.add(root);
    while (!queue.isEmpty()) {
        int levelSize = queue.size();
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < levelSize; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) queue.add(node.left);
            if (node.right != null) queue.add(node.right);
        }
        result.add(level);
    }
    return result;
}
```
This is the direct generalization of the iterative `maxDepth` above — same level-by-level BFS skeleton, except each level's values are collected into a list instead of just counted.
**Complexity**: `O(n)` time, `O(w)` space (widest level held in the queue at once, plus `O(n)` for the output).

---

### 5. Validate Binary Search Tree
**Problem**: Given a binary tree, determine if it is a valid BST.

**The trap**: checking only `node.left.val < node.val < node.right.val` at every node is **wrong** — it misses violations further down (e.g. a right-grandchild that's smaller than the root but bigger than its immediate parent). The BST invariant is global, not local — every node in a subtree must respect the bounds inherited from *every* ancestor, not just its direct parent.

**Correct approach — pass down a valid `(min, max)` range**:
```java
public static boolean isValidBST(TreeNode root) {
    return validate(root, null, null);
}

private static boolean validate(TreeNode node, Integer lower, Integer upper) {
    if (node == null) return true;                                    // empty subtree is trivially valid
    if (lower != null && node.val <= lower) return false;              // violates an ancestor's lower bound
    if (upper != null && node.val >= upper) return false;              // violates an ancestor's upper bound
    return validate(node.left, lower, node.val)                       // left subtree: still > lower, but now < node.val
        && validate(node.right, node.val, upper);                     // right subtree: still < upper, but now > node.val
}
```
**Alternative approach — inorder traversal must be strictly increasing** (uses the fact from Part 1 that inorder-on-a-BST is sorted): traverse inorder while tracking the previous value visited; if any value isn't strictly greater than the previous one, it's not a valid BST. Both approaches are `O(n)` — the range approach is usually cleaner to reason about and explain out loud.
**Complexity**: `O(n)` time, `O(h)` space.

---

### 6. Lowest Common Ancestor of a BST
**Problem**: Given a BST and two nodes `p`, `q` known to exist in it, find their lowest common ancestor (the deepest node that has both `p` and `q` as descendants, a node counted as its own descendant).

```java
public static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    TreeNode node = root;
    while (node != null) {
        if (p.val < node.val && q.val < node.val) {
            node = node.left;                    // both targets are smaller -> LCA must be in the left subtree
        } else if (p.val > node.val && q.val > node.val) {
            node = node.right;                   // both targets are larger -> LCA must be in the right subtree
        } else {
            return node;                          // p and q split (or one equals node) -> this is the LCA
        }
    }
    return null;                                  // unreachable if p, q are guaranteed to exist in the tree
}
```
**Why this only works because it's a BST**: the ordering invariant tells you *which direction* to go without ever looking at the actual subtree contents — "both smaller" means both must be on the left, full stop. A general binary tree (no ordering) needs a different algorithm (recurse both sides, return the node where `p` and `q` are found in different subtrees), which is `O(n)` instead of `O(h)`.
**Complexity**: `O(h)` time (no need to recurse into both children — one direction is eliminated at every step, the same halving idea as BST search itself), `O(1)` space (iterative, no recursion stack).

---

### 7. Kth Smallest Element in BST
**Problem**: Given a BST and integer `k`, return the `k`-th smallest value (1-indexed).

**Uses the core inorder fact directly**: inorder traversal of a BST visits values in ascending order, so the `k`-th value visited during an inorder traversal *is* the answer — no sorting needed.

**Recursive (count as you go)**:
```java
private static int count = 0;
private static int result = -1;

public static int kthSmallest(TreeNode root, int k) {
    count = 0;
    result = -1;
    inorder(root, k);
    return result;
}

private static void inorder(TreeNode node, int k) {
    if (node == null || count >= k) return;             // prune: stop once found, don't keep traversing
    inorder(node.left, k);
    count++;
    if (count == k) { result = node.val; return; }
    inorder(node.right, k);
}
```
**Iterative (explicit stack, stop early)** — avoids visiting the whole tree when `k` is small relative to `n`:
```java
public static int kthSmallestIterative(TreeNode root, int k) {
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    while (curr != null || !stack.isEmpty()) {
        while (curr != null) {                            // push left spine
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();                               // visit: this is inorder's "node" step
        if (--k == 0) return curr.val;
        curr = curr.right;                                 // then descend right
    }
    throw new IllegalArgumentException("k is out of range");
}
```
**Why the iterative version is worth knowing**: it can stop as soon as it finds the answer without ever building a full call stack for the untouched right side of the tree — the classic "convert recursion to an explicit stack" pattern from [day 4](../day4/01-dsa-recursion-backtracking.md#recursion-vs-iteration), applied to inorder traversal specifically (push down the left spine, pop-visit-go right, repeat).
**Complexity**: `O(h + k)` time in the iterative version (only descends as needed), `O(n)` worst case if `k` is close to `n`; `O(h)` space for the stack.

---

## Part 3 — Interview Questions (today's round)

**Q: Why does inorder traversal of a BST produce sorted order, and why does no other traversal order guarantee that?**
**A:** The BST invariant says everything in a node's left subtree is smaller and everything in its right subtree is larger. Inorder visits left subtree → node → right subtree, which — applied recursively at every node — means "everything smaller than this node, then this node, then everything larger than this node," which is exactly the definition of sorted order. Preorder (node first) and postorder (node last) don't respect that "smaller side, then me, then larger side" sequencing, so they don't produce any consistent ordering relative to value.

**Q: Why is checking `node.left.val < node.val < node.right.val` at every node not sufficient to validate a BST?**
**A:** The BST invariant is global (every descendant of the left subtree must be less than the node, however many levels down), not just about immediate children. A tree can satisfy the local parent-child check at every node while still having, say, a right-child's-left-grandchild that's smaller than the root — locally fine at each step, globally invalid. The fix is threading a valid `(min, max)` range down through recursion (or checking that a full inorder traversal is strictly increasing), so every node is checked against *all* its ancestors' constraints, not just its direct parent.

**Q: Why is BST lookup/insert `O(log n)` only if the tree is balanced, and what's the worst case otherwise?**
**A:** Each step down a BST discards one whole subtree — that's only a real "halving" of the search space if the two subtrees are roughly equal in size, i.e. the tree is balanced (height `O(log n)`). If nodes are inserted in already-sorted order with no rebalancing, every node ends up with only one child, producing a straight-line tree of height `O(n)` — at that point every operation degrades to `O(n)`, identical to a linked list. Self-balancing trees (AVL, Red-Black — used internally by `TreeMap`/`TreeSet`) rebalance on insert/delete specifically to keep height at `O(log n)` regardless of insertion order.

**Q: Walk me through both ways to find the LCA of two nodes in a BST, and why the BST-specific one is faster.**
**A:** The general binary-tree algorithm recurses into both children looking for `p` and `q`, and returns the node where they're found in different subtrees (or the node itself if it equals one of them) — this is `O(n)` because it may need to visit every node. The BST-specific algorithm uses the ordering invariant: starting at the root, if both `p` and `q` are smaller than the current node, the LCA must be in the left subtree (no need to ever look right); if both are larger, it must be in the right subtree; the moment they're on different sides (or one equals the current node), that node is the LCA. This eliminates one whole subtree at every step without inspecting it, so it's `O(h)` instead of `O(n)` — the same search-space-halving idea as BST lookup itself.
