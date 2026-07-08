# DSA — HashMap + HashSet + Sliding Window

## Part 1 — Concepts (read this first)

### HashMap internals
A `HashMap` stores entries in an array of **buckets** (`Node<K,V>[] table`). To find a bucket for a key:

```
index = hash(key) & (table.length - 1)
```

`hash(key)` isn't just `key.hashCode()` — Java XORs the hash with its upper 16 bits (`h ^ (h >>> 16)`) to spread out bits and reduce collisions when table size is small (a power of two).

Each bucket holds a **linked list** of entries that hashed to the same index. On `get`/`put`, Java computes the index, then walks that bucket's list comparing keys with `equals()`.

### Hash collisions
A collision happens when two different keys hash to the same bucket index. Java handles this by chaining — the bucket becomes a linked list (or, since Java 8, a red-black tree once a bucket has ≥ 8 entries **and** the table has ≥ 64 buckets — "treeification"). Collisions are normal; the goal is just to keep each bucket's list short.

### Why HashMap average lookup is O(1)
With a good hash function and a reasonable **load factor** (default 0.75), keys spread evenly across buckets, so each bucket holds close to 0–1 entries on average. Looking up a key is then: compute hash (O(1)) → jump to bucket (O(1)) → scan a near-empty list (O(1) amortized). Worst case (everything collides into one bucket) degrades to O(n) — or O(log n) post-treeification in Java 8+.

### When HashSet is better than List
| Operation | `ArrayList` | `HashSet` |
|---|---|---|
| `contains(x)` | O(n) — scans every element | O(1) average |
| Add unique-only | manual check needed | automatic (no duplicates) |
| Order preserved | yes | no (use `LinkedHashSet` if you need insertion order) |

Rule of thumb: if you're repeatedly asking "have I seen this before?", use a `HashSet`, not a `List`.

### Frequency counting pattern
Use a `Map<T, Integer>` to count occurrences in one pass:
```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.merge(c, 1, Integer::sum);
}
```
This underlies anagram checks, "top K frequent", and duplicate detection.

### Sliding window pattern
Maintain a **window** `[left, right]` over an array/string, expanding `right` and shrinking `left` when a constraint is violated, using a `Map`/`Set` to track window contents in O(1):
```java
int left = 0;
for (int right = 0; right < n; right++) {
    // add arr[right] to window state
    while (/* window invalid */) {
        // remove arr[left] from window state
        left++;
    }
    // update answer using current window [left, right]
}
```
This turns an O(n²) brute force (try every subarray) into O(n) — each pointer moves forward at most n times total.

---

## Part 2 — Problems

### 1. Valid Anagram
**Problem**: Given two strings `s` and `t`, return true if `t` is an anagram of `s`.

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;
    int[] count = new int[26];
    for (int i = 0; i < s.length(); i++) {
        count[s.charAt(i) - 'a']++;
        count[t.charAt(i) - 'a']--;
    }
    for (int c : count) if (c != 0) return false;
    return true;
}
```
**Why it works**: an anagram has identical character frequencies. Incrementing for `s` and decrementing for `t` means a true anagram nets every counter back to 0.
**Complexity**: O(n) time, O(1) space (fixed 26-size array — better than a `HashMap<Character,Integer>` which would be O(k) for k distinct chars).
**Pattern used**: frequency counting.

---

### 2. Group Anagrams ⭐⭐⭐
**Problem**: Group strings that are anagrams of each other.

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();
    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```
**Why it works**: anagrams produce the *same string* once sorted — that sorted string is a canonical key that naturally buckets equivalent words in a `HashMap`.
**Complexity**: O(n · k log k) time (n words, k = max word length for sorting), O(n · k) space.
**Optimize space/time**: skip sorting — use a 26-length count array turned into a string (`"1a0b2c..."`) as the key. That's O(n · k) time instead of O(n · k log k).
**Pattern used**: frequency counting used as a hash key ("canonical signature").

---

### 3. Longest Consecutive Sequence ⭐⭐⭐
**Problem**: Given an unsorted array, find the length of the longest run of consecutive integers, in O(n).

```java
public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int n : nums) set.add(n);

    int longest = 0;
    for (int n : set) {
        if (!set.contains(n - 1)) { // only start counting from the beginning of a sequence
            int length = 1;
            while (set.contains(n + length)) length++;
            longest = Math.max(longest, length);
        }
    }
    return longest;
}
```
**Why it works**: the `!set.contains(n - 1)` check ensures each sequence is only ever counted starting from its smallest element — so the total work across all sequences is still O(n), even though there's a nested `while`.
**Complexity**: O(n) time (each number is visited at most twice), O(n) space.
**Common mistake**: sorting first gives O(n log n) — misses the point of the exercise, which is proving you can do it in O(n) using a set for O(1) existence checks.
**Pattern used**: HashSet for O(1) lookups + "only start from sequence heads".

---

### 4. Longest Substring Without Repeating Characters ⭐⭐⭐⭐
**Problem**: Find the length of the longest substring without repeating characters.

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int left = 0, maxLen = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1; // jump left past the previous occurrence
        }
        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```
**Why it works**: instead of shrinking the window one character at a time, we jump `left` directly past the duplicate's last position — this is the "optimized" sliding window (each pointer still only moves forward, so it's O(n), not O(n²)).
**Complexity**: O(n) time, O(min(n, charset size)) space.
**Optimize further**: if the charset is known (ASCII), replace the `HashMap` with an `int[128]` array for a constant-factor speedup.
**Pattern used**: sliding window + last-seen-index map (a variant of frequency counting).

---

### 5. Top K Frequent Elements
**Problem**: Return the k most frequent elements in an array.

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    // Min-heap of size k, ordered by frequency
    PriorityQueue<Integer> heap = new PriorityQueue<>((a, b) -> freq.get(a) - freq.get(b));
    for (int key : freq.keySet()) {
        heap.offer(key);
        if (heap.size() > k) heap.poll(); // evict the least frequent
    }

    int[] result = new int[k];
    for (int i = k - 1; i >= 0; i--) result[i] = heap.poll();
    return result;
}
```
**Why it works**: a min-heap capped at size `k` always keeps exactly the k largest-frequency elements seen so far — anything smaller than the heap's minimum gets evicted immediately.
**Complexity**: O(n log k) time — better than sorting all n elements (O(n log n)) when k is small. Space O(n) for the frequency map.
**Alternative**: bucket sort by frequency (index = frequency, value = list of numbers) gives O(n) time, trading a bit more space.
**Pattern used**: frequency counting + heap ("top-K" pattern).

---

## Part 3 — Interview Questions (today's round)

**Q: Solve "Longest Substring Without Repeating Characters" in 20 minutes.**

**A:** Walk through it out loud like this:
1. *Brute force first* — check every substring for uniqueness: O(n³) (O(n²) substrings × O(n) to verify). State this, then say you'll optimize.
2. *Recognize the pattern* — "longest/shortest substring/subarray satisfying a constraint" is a sliding window signal.
3. *Pick the window state* — you need to know, for the current window, whether a character repeats. A `HashMap<Character, Integer>` mapping character → last index seen gives O(1) duplicate checks.
4. *Two pointers* — `right` expands the window every step; `left` jumps forward only when a duplicate is found *inside* the current window (guarded by `lastSeen.get(c) >= left`, since a stale index outside the window shouldn't trigger a jump — this is the bug most candidates introduce).
5. *Track the answer* — `maxLen = max(maxLen, right - left + 1)` on every iteration.
6. *State complexity*: O(n) time since each pointer only moves forward, O(min(n, Σ)) space for the map.

The interviewer is watching for: do you go straight to brute force and reason your way to the optimization, do you catch the "stale index" bug, and can you state complexity correctly.
