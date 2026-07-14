# DSA — Recursion + Backtracking

## Part 1 — Concepts (read this first)

### Recursion tree
Every recursive call can be drawn as a node in a tree: the arguments at that call are the node's label, and each recursive call it makes is a child node. The **base case** is a leaf. Solving a recursion problem is really just describing this tree precisely: what does a node look like, when does it stop branching, and what does it hand back up to its parent.

For backtracking specifically, the tree *is* the search space — each root-to-node path is a partially-built candidate, and each leaf (or each node, depending on the problem) is a complete candidate to record. "Backtracking" is just depth-first traversal of that tree with **undo**: try a choice, recurse, then undo the choice before trying the next sibling, so the same mutable buffer can be reused across the whole traversal instead of allocating a new list per branch.

```
choose(nums, start=0, path=[])
                    []
         /          |          \
       [1]         [2]         [3]          <- each edge = "include this element"
      /   \          |
   [1,2] [1,3]     [2,3]
     |
  [1,2,3]
```

### Base case
The condition that stops recursion and returns directly instead of recursing further. Two things must be true for recursion to terminate:
1. Every recursive call must move **strictly closer** to a base case (smaller `start` index, smaller remaining target, shorter remaining string, etc.).
2. The base case must actually be reachable from every path through the tree — if a branch can loop back to the same state, recursion never bottoms out (infinite recursion → `StackOverflowError`).

### Stack frames
Each recursive call pushes a new **stack frame** onto the thread's call stack — see [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md#stack) for the full JVM memory picture. The frame holds that call's local variables, parameters, and the return address. Depth of recursion = number of stack frames alive at once = the deepest root-to-leaf path in the recursion tree. This is *why* recursion has a real memory cost that iteration doesn't: an iterative loop reuses one frame; a recursive call chain of depth `n` needs `n` frames simultaneously on the stack.

### Time complexity
For a recursion tree, total work = **number of nodes** × **work done per node** (excluding the cost of children, which is counted separately as their own nodes). For backtracking specifically:
- **Subsets**: each element is either in or out → `2^n` leaves, `O(2^n)` nodes total → `O(2^n)` time (times `O(n)` if you copy each subset into the result).
- **Permutations**: `n!` leaves, and building each one costs `O(n)` → `O(n! * n)`.
- **Combination Sum** (with reuse): bounded by how many ways sums can be composed from candidates — not a clean closed form, but still exponential in the worst case; the target/candidate values bound the actual branching in practice.

The habit to build: don't just say "it's exponential" — count what a leaf represents (a subset, a permutation, a combination) and multiply the **number of leaves** by the **cost to materialize one leaf**.

### Recursion vs iteration
| | Recursion | Iteration |
|---|---|---|
| State | Implicit, held in stack frames | Explicit, held in variables/data structures you manage |
| Natural fit | Problems with a tree/nested structure (subsets, permutations, tree traversal, divide & conquer) | Problems with a linear, flat structure |
| Memory | O(depth) extra space on the call stack | O(1) extra space (unless you build your own explicit stack) |
| Risk | `StackOverflowError` on deep/unbounded recursion | None from depth, but state management bugs are more visible/manual |

Any recursive algorithm can be rewritten iteratively using an explicit stack that mimics the call stack — this is a common interview follow-up ("can you do this without recursion?"). It's mechanical: whatever you'd pass as arguments to the next recursive call, push onto your own stack instead.

---

## Part 2 — Problems

### 1. Subsets
**Problem**: Given a set of distinct integers, return all possible subsets (the power set).

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> result) {
    result.add(new ArrayList<>(path));   // every node in the tree is a valid subset, not just leaves
    for (int i = start; i < nums.length; i++) {
        path.add(nums[i]);                       // choose
        backtrack(nums, i + 1, path, result);     // explore
        path.remove(path.size() - 1);             // un-choose (backtrack)
    }
}
```
**Decision tree**: at each call, decide "which of the remaining elements (from `start` onward) do I add next, if any." Recording `path` on *entry* to every call (not just at a base case) is what makes this "subsets" rather than "permutations of a fixed length" — every partial path is itself a valid answer.
**Why `new ArrayList<>(path)`**: `path` is one mutable buffer reused across the whole traversal. If you added `path` itself to `result`, every entry in `result` would be a reference to the *same* list, and it would end up empty (or wrong) once backtracking pops everything back off. Copying snapshots the state at that exact node.
**Complexity**: `O(2^n)` subsets, `O(n)` to copy each → `O(n * 2^n)` time, `O(n)` extra space for the recursion depth (excluding output).

---

### 2. Permutations
**Problem**: Given distinct integers, return all possible orderings.

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, new ArrayList<>(), new boolean[nums.length], result);
    return result;
}

private void backtrack(int[] nums, List<Integer> path, boolean[] used, List<List<Integer>> result) {
    if (path.size() == nums.length) {           // base case: a full-length ordering
        result.add(new ArrayList<>(path));
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;                   // can't reuse an element already placed
        used[i] = true;
        path.add(nums[i]);
        backtrack(nums, path, used, result);
        path.remove(path.size() - 1);            // backtrack
        used[i] = false;                          // backtrack
    }
}
```
**Decision tree difference from Subsets**: Subsets loops from `start` forward (order doesn't matter, no reuse of earlier indices), so it only *shrinks* the choice set going down. Permutations loops from `0` every time but skips whatever's already `used` — every position considers *every* not-yet-placed element, because order matters (`[1,2]` and `[2,1]` are different results).
**Complexity**: `O(n!)` leaves, `O(n)` work to copy each → `O(n! * n)` time, `O(n)` extra space.

---

### 3. Combination Sum
**Problem**: Given distinct candidates (each reusable unlimited times) and a target, return all unique combinations that sum to target.

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> result = new ArrayList<>();
    Arrays.sort(candidates);                     // enables early pruning below
    backtrack(candidates, target, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] candidates, int remaining, int start,
                        List<Integer> path, List<List<Integer>> result) {
    if (remaining == 0) {
        result.add(new ArrayList<>(path));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > remaining) break;     // sorted -> everything after is too big too, prune the rest
        path.add(candidates[i]);
        backtrack(candidates, remaining - candidates[i], i, path, result);  // note: i, not i+1 -> reuse allowed
        path.remove(path.size() - 1);
    }
}
```
**Why `i` and not `i + 1` in the recursive call**: this is the one line that distinguishes Combination Sum from Subsets. Passing `i` allows the same candidate to be picked again in the next recursive level (unlimited reuse); passing `i + 1` would forbid reuse. Passing `start` forward (never going back to indices `< start`) is what prevents duplicate combinations in different orders (e.g. `[2,3]` and `[3,2]` are the same combination — only generated once because we never look backward).
**Why sort first**: lets you `break` (not just `continue`) the moment a candidate exceeds `remaining` — since the array is sorted ascending, every candidate after it is also too large, so the rest of the loop is provably useless. This is real pruning, not just a style choice.
**Complexity**: exponential in the worst case, bounded by `target` and the candidate values — no clean closed form, but every leaf still costs `O(target/min(candidates))` depth at most.

---

### 4. Letter Combinations of a Phone Number
**Problem**: Given a string of digits `2`–`9`, return all letter combinations the digits could represent (like an old T9 keypad).

```java
private static final String[] KEYPAD = {
    "", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"
};

public List<String> letterCombinations(String digits) {
    List<String> result = new ArrayList<>();
    if (digits.isEmpty()) return result;
    backtrack(digits, 0, new StringBuilder(), result);
    return result;
}

private void backtrack(String digits, int index, StringBuilder path, List<String> result) {
    if (index == digits.length()) {               // base case: one letter chosen per digit
        result.add(path.toString());
        return;
    }
    String letters = KEYPAD[digits.charAt(index) - '0'];
    for (char c : letters.toCharArray()) {
        path.append(c);                            // choose
        backtrack(digits, index + 1, path, result); // explore
        path.deleteCharAt(path.length() - 1);       // un-choose
    }
}
```
**Decision tree**: depth = `digits.length()` (one level per digit position, not per candidate letter), branching factor at each level = however many letters that digit maps to (3 or 4). This is the cleanest example of "recursion depth is fixed by position in the input, branching is fixed by choices at that position" — contrast with Subsets/Permutations where depth itself varies.
**Complexity**: `O(4^n * n)` worst case (`n` = number of digits, up to 4 letters per digit like `7`/`9`, `n` to build each string) — `4^n` leaves is the true worst case, `3^n`-ish on average since most digits map to 3 letters.

---

## Part 3 — Interview Questions (today's round)

**Q: What's the actual difference between the recursive structure of Subsets vs Permutations?**
**A:** Subsets loops forward from a `start` index and never revisits earlier indices — each recursive call operates on a strictly smaller remaining choice set, because *order doesn't matter* and each element is used at most once. Permutations loops from index `0` every call but skips indices marked `used` — every position must consider every not-yet-placed element, because *order matters*, so `[1,2]` and `[2,1]` need to both be generated. The `used[]` array (or equivalent) is what makes "revisit every index, minus what's taken" possible without an explicit `start` bound.

**Q: Why does Combination Sum pass `i` instead of `i + 1` to the recursive call, and why does that matter?**
**A:** Passing `i` allows the current candidate to be chosen again at the next recursion level, which is required because the problem allows unlimited reuse of each candidate. Passing `start` (rather than always `0`, like Permutations does) still prevents *duplicate combinations in different orders* — once you've moved past index `i`, you never look backward, so `[2,3]` can only ever be built as `2` then `3`, never also as `3` then `2`.

**Q: Why can recursion cause a `StackOverflowError` and how would you avoid it for a problem with very deep input?**
**A:** Each recursive call keeps its own stack frame alive until it returns, so recursion depth `n` requires `n` simultaneous frames on the thread's (fixed-size) call stack — see [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md#stack). If `n` is large enough (deep recursion on a huge input, or a missing/broken base case), the stack is exhausted. Fixes: convert to iteration with an explicit heap-allocated stack (heap has much more room than a thread stack), restructure the recursion to be tail-recursive if the language/JVM optimizes that (the JVM does **not** guarantee tail-call optimization, so this isn't a real fix in Java specifically), or increase the thread's stack size (`-Xss`) as a stopgap, not a real fix.

**Q: How would you estimate the time complexity of a backtracking solution before writing any code?**
**A:** Multiply the number of leaves in the recursion tree (how many complete candidates exist — `2^n` for include/exclude decisions, `n!` for orderings, `k^n` for `n` independent choices from `k` options each) by the cost to produce and record one leaf (usually `O(n)` to copy the path). Pruning (like the sorted `break` in Combination Sum) reduces the *practical* number of nodes visited but doesn't change the theoretical worst case unless you can prove a tighter bound on how many nodes survive the prune.
