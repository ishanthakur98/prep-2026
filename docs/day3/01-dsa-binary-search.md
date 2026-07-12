# DSA — Binary Search Patterns

## Part 1 — Concepts (read this first)

Binary search is not "find an element in a sorted array." That's one instance of a much more general tool: **eliminate half the search space on every step because you can prove the answer can't be on the side you're throwing away.** Once you see it that way, four different-looking problem families turn out to be the same 10 lines of code.

### The invariant that makes it work (proof, not recipe)

Binary search is correct whenever the search space can be split by a **monotonic predicate** — a boolean function `f(x)` such that once it flips from `false` to `true` (or `true` to `false`), it never flips back. Picture the space as:

```
false false false false | true true true true
                         ^
                    the boundary you want
```

At every step you pick `mid`, evaluate `f(mid)`:
- If `f(mid)` is `false`, the boundary is strictly to the right — everything at or before `mid` is provably not the answer, discard it (`lo = mid + 1`).
- If `f(mid)` is `true`, `mid` *might* be the boundary, but nothing right of the boundary matters — discard the right side, keep `mid` as a candidate (`hi = mid`, or `hi = mid - 1` if you don't need to re-examine `mid`).

**Why this terminates correctly**: each iteration strictly shrinks `[lo, hi]` (by at least one element), and the invariant "the boundary always lies inside `[lo, hi]`" is preserved by construction — we only ever discard sides we've *proven* can't contain it. When `lo == hi`, the remaining single element must be the boundary. This is why binary search is correct — not "because it's a known algorithm," but because the loop maintains a shrinking range that provably always contains the answer.

For a plain sorted-array search, the predicate is `f(x) = (array[x] >= target)` — everything before the target is `false`, the target and everything after is `true`. Finding "first index where `f` is true" **is** the search.

### Basic Binary Search
Find whether `target` exists in a sorted array.
```java
int lo = 0, hi = arr.length - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;   // avoids (lo+hi) overflow
    if (arr[mid] == target) return mid;
    else if (arr[mid] < target) lo = mid + 1;
    else hi = mid - 1;
}
return -1; // not found
```
`lo + (hi - lo) / 2` instead of `(lo + hi) / 2` is a real interview detail — for huge arrays `lo + hi` can overflow a 32-bit `int` before the division happens.

### Lower Bound (first index where `arr[i] >= target`)
```java
int lo = 0, hi = arr.length; // hi is exclusive/one-past-end
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] >= target) hi = mid;      // mid could be the answer, look left
    else lo = mid + 1;                     // mid is provably too small, discard it
}
return lo; // first index with arr[lo] >= target (== arr.length if none)
```
This is the `f(x) = arr[x] >= target` predicate from above, applied directly. `lo == hi` at the end **is** the boundary.

### Upper Bound (first index where `arr[i] > target`)
Identical shape, predicate changes to `arr[mid] > target`:
```java
int lo = 0, hi = arr.length;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (arr[mid] > target) hi = mid;
    else lo = mid + 1;
}
return lo;
```
**Why bother with both**: `lowerBound` and `upperBound` together give you the exact `[start, end)` range of a value in a sorted array with duplicates in O(log n) — e.g. LeetCode's "Find First and Last Position of Element in Sorted Array."

### Search on Answer
This is the pattern that trips people up because there's no array being searched at all — the "array" is the **range of possible answers**, and you binary search over *that*, using a predicate `canDoIt(x)` that's monotonic (once `x` is "good enough," every larger `x` stays good enough).

Recipe:
1. Identify what you're actually searching for (a rate, a capacity, a time, a minimum/maximum value) — call it `x`.
2. Find the range `[lo, hi]` that `x` could possibly be in.
3. Write `boolean canDoIt(x)` (or `isFeasible(x)`) that answers "if the answer were `x`, would it work?" — this must be **monotonic**: true for `x` implies true for all larger (or all smaller) `x`.
4. Binary search on `[lo, hi]` for the boundary where `canDoIt` flips, same as lower/upper bound above.

### Rotated Array
A sorted array rotated at an unknown pivot (`[4,5,6,7,0,1,2]`) is no longer globally sorted, but **at every `mid`, at least one of the two halves *is* still sorted**. The trick: identify which half is sorted by comparing `arr[lo]` to `arr[mid]`, then check if `target` falls inside that sorted half's range — if yes, recurse into it; if no, it must be in the other half.

---

## Part 2 — Problems

### 1. Search Insert Position
**Problem**: Given a sorted array and a target, return the index if found; otherwise return the index where it would be inserted to keep the array sorted.

```java
public int searchInsert(int[] nums, int target) {
    int lo = 0, hi = nums.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] >= target) hi = mid;
        else lo = mid + 1;
    }
    return lo;
}
```
**Why it works**: this is exactly lower bound. "Where would target go" and "first index where `nums[i] >= target`" are the same question — if target exists, that's its index; if it doesn't, that's exactly where it should be inserted.
**Complexity**: O(log n) time, O(1) space.
**Pattern used**: lower bound.

---

### 2. First Bad Version
**Problem**: `isBadVersion(version)` is an API that returns `true` once a version and every version after it is bad (monotonic). Find the first bad version, minimizing calls to the API.

```java
public int firstBadVersion(int n) {
    int lo = 1, hi = n;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2; // careful: NOT (lo+hi)/2, that overflows for large n here specifically
        if (isBadVersion(mid)) hi = mid;       // mid could be the first bad one, look left
        else lo = mid + 1;                     // mid is provably good, discard it
    }
    return lo;
}
```
**Why it works**: `isBadVersion` is literally the monotonic predicate `f(x)` from Part 1 — `false false ... false true true ... true` — and "first bad version" is precisely "first index where `f` is true," i.e. lower bound over the predicate instead of over array values.
**Complexity**: O(log n) calls to `isBadVersion` instead of O(n) checking every version — this is the whole point of the problem (minimizing an expensive/rate-limited API call).
**Pattern used**: binary search on a predicate (this *is* "search on answer," just with a boolean answer instead of a numeric one).

---

### 3. Search in Rotated Sorted Array ⭐⭐⭐⭐
**Problem**: Search `target` in a sorted array that's been rotated at an unknown pivot, in O(log n), no duplicates.

```java
public int search(int[] nums, int target) {
    int lo = 0, hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) return mid;

        if (nums[lo] <= nums[mid]) {
            // left half [lo..mid] is sorted
            if (nums[lo] <= target && target < nums[mid]) {
                hi = mid - 1;       // target in the sorted left half
            } else {
                lo = mid + 1;       // target must be in the right half
            }
        } else {
            // right half [mid..hi] is sorted
            if (nums[mid] < target && target <= nums[hi]) {
                lo = mid + 1;       // target in the sorted right half
            } else {
                hi = mid - 1;       // target must be in the left half
            }
        }
    }
    return -1;
}
```
**Why it works**: a rotation creates at most one "break point," so cutting the array anywhere leaves at least one of the two halves fully sorted. `nums[lo] <= nums[mid]` tells you *which* half is sorted (if the left half were broken, `nums[lo]` would be greater than `nums[mid]`). Once you know a half is sorted, a plain range check (`nums[lo] <= target < nums[mid]`) tells you whether `target` can possibly be in it — if not, it must be in the other, unsorted-looking half, which you now recurse into exactly like a normal binary search.
**Complexity**: O(log n) time, O(1) space.
**Common mistake**: using `<=` vs `<` inconsistently in the range checks, or forgetting the `nums[mid] == target` early return. Also: this exact approach breaks with duplicates (`nums[lo] == nums[mid]` no longer tells you which half is sorted) — the follow-up "with duplicates" variant needs a fallback `lo++` when `nums[lo] == nums[mid] == nums[hi]`, degrading worst case to O(n).
**Pattern used**: rotated array (identify the sorted half, then treat it as a normal bounded search).

---

### 4. Find Peak Element
**Problem**: Given an array where `nums[-1] = nums[n] = -∞` conceptually, find any index `i` such that `nums[i] > nums[i-1]` and `nums[i] > nums[i+1]`, in O(log n).

```java
public int findPeakElement(int[] nums) {
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] > nums[mid + 1]) {
            hi = mid;        // descending here -> a peak is at mid or to its left
        } else {
            lo = mid + 1;    // ascending here -> a peak must be to the right
        }
    }
    return lo;
}
```
**Why it works**: this is "search on answer" in disguise. The predicate is `f(x) = nums[x] > nums[x+1]` ("we're on a downslope by index x"). Because the array's virtual boundaries are `-∞`, there is *guaranteed* to be at least one peak, and the sequence of "am I on a downslope yet" values is effectively monotonic enough that comparing `nums[mid]` to its right neighbor always tells you which half still contains a peak — you never need to look at the whole array, only the local slope at `mid`.
**Complexity**: O(log n) time, O(1) space — the O(n) linear scan is the "obvious" answer; the interviewer wants you to justify why binary search still applies even though the array *isn't* globally sorted.
**Pattern used**: search on answer / binary search on a local monotonic signal, not a sorted array.

---

### 5. Koko Eating Bananas ⭐⭐⭐⭐⭐
**Problem**: `piles[i]` bananas in pile `i`. Koko eats at speed `k` bananas/hour; each hour she picks one pile and eats up to `k` from it (if the pile has fewer than `k`, she finishes it and doesn't start another that hour). Find the **minimum** integer `k` such that she can eat all piles within `h` hours.

```java
public int minEatingSpeed(int[] piles, int h) {
    int lo = 1, hi = 0;
    for (int p : piles) hi = Math.max(hi, p);   // max possible useful speed: largest pile

    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (hoursNeeded(piles, mid) <= h) {
            hi = mid;          // speed `mid` works -> maybe we can go slower, look left
        } else {
            lo = mid + 1;      // speed `mid` too slow (needs too many hours) -> must go faster
        }
    }
    return lo;
}

private long hoursNeeded(int[] piles, int k) {
    long hours = 0;
    for (int p : piles) hours += (p + k - 1) / k;  // ceil(p / k) without floating point
    return hours;
}
```

### Why this is "search on answer" and not "search an array"
There is no array of speeds sitting in memory to binary search over — `k` ranges over the plain integers `[1, max(piles)]`. What makes binary search *legal* here is proving the predicate `canFinish(k) = (hoursNeeded(piles, k) <= h)` is **monotonic**:
- If speed `k` is fast enough to finish in `h` hours, then any speed `k' > k` is *also* fast enough — eating faster never increases the hours needed (`ceil(p/k)` is non-increasing as `k` grows).
- So the true/false sequence over `k = 1, 2, 3, ...` looks like `false false false ... true true true` — exactly the monotonic shape binary search requires.

That's the whole justification: **you don't need a sorted array, you need a monotonic yes/no function over a bounded numeric range.** Once you have that, "find the minimum `k` where `canFinish(k)` is true" is *identical in code* to lower bound from Part 1 — only the predicate changed from `arr[mid] >= target` to `hoursNeeded(piles, mid) <= h`.

**Complexity**: O(n log(max(piles))) — for each of the O(log(max(piles))) candidate speeds tried, `hoursNeeded` does an O(n) pass over all piles.
**Common mistakes**:
- Using `p / k` instead of ceiling division `(p + k - 1) / k` — a pile of 7 at speed 3 takes 3 hours (`3+3+1`), not 2.
- Setting `hi` too low (must be at least `max(piles)`, since eating the largest pile in one hour is always achievable and is the fastest speed that could ever matter — nothing faster helps).
- Setting `lo = 0` instead of `1` — speed 0 is nonsensical (never finishes) and also breaks the division in `hoursNeeded`.

**Pattern used**: search on answer (the flagship example — internalize this one; it's the template for "minimize the maximum" / "maximize the minimum" problems like Capacity To Ship Packages Within D Days, Split Array Largest Sum, Minimum Number of Days to Make m Bouquets).

---

## Part 3 — Interview Questions (today's round)

**Q: Prove why binary search works. Not the algorithm — the proof.**

**A:** Binary search is correct whenever you can define a boolean predicate `f` over an ordered search space that is **monotonic** — `false, false, ..., false, true, true, ..., true` (never flips back to false once true). The loop maintains the invariant "the boundary between false and true lies within `[lo, hi]`." Each iteration:
- evaluates `f(mid)`,
- and discards a side of the range **only when it's been proven, not assumed**, that the boundary cannot be there (`f(mid) == false` proves the boundary is > mid, since everything ≤ mid is false; `f(mid) == true` proves the boundary is ≤ mid, since mid itself is a valid "true" candidate).

Because every iteration strictly shrinks `[lo, hi]` by at least one element while never discarding the boundary, the loop must terminate with `lo == hi` pointing exactly at the boundary — by induction, the invariant holds initially (the boundary is somewhere in the full array) and is preserved every step, so it holds at termination too. This is also *why* a broken (non-monotonic) predicate silently gives wrong answers instead of erroring — the proof's premise (monotonicity) simply doesn't hold, so the invariant isn't preserved, but the loop still terminates and returns something.

**Q: Why does Koko Eating Bananas use binary search when there's no sorted array?**

**A:** Because binary search doesn't require a sorted array — it requires a monotonic predicate over an ordered range. Here the range is "possible eating speeds" `[1, max(piles)]`, and the predicate `canFinish(k)` = "can Koko finish within `h` hours at speed `k`" is monotonic because increasing speed never increases the hours required. That monotonicity is what licenses binary search; the "array" framing is a red herring left over from how most people first learn the algorithm.

**Q: When does binary search on a rotated array break, and how do you fix it?**

**A:** It breaks when duplicates make `nums[lo] == nums[mid] == nums[hi]` — you can no longer tell which half is sorted from that comparison alone (e.g. `[1,1,1,0,1]` vs `[1,0,1,1,1]` — same lo/mid/hi values, different sorted half). The fix is a fallback: when that ambiguous case hits, shrink the range by one (`lo++`, or `hi--`) and retry — this can degrade the worst case to O(n) (e.g. an array of all-identical values with one different element), but it's the only correct fallback since the ambiguity is real, not a bug in the logic.
