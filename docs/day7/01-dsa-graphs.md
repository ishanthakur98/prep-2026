# DSA — Graphs

## Part 1 — Concepts (read this first)

### What a graph actually is
A graph is just **nodes (vertices) + connections between them (edges)** — no shape guarantee at all, which is exactly what makes graphs feel harder than trees. A tree *is* a graph (connected, no cycles, exactly one path between any two nodes); a general graph drops all three of those guarantees — it can be disconnected, it can have cycles, and there can be multiple paths (or zero) between two nodes. Every algorithm below exists specifically to handle what a tree's shape would otherwise rule out for free: cycles (need a `visited` set to avoid infinite loops) and disconnection (need to restart traversal from every unvisited node to see the whole graph).

**Directed vs undirected**: an edge `(a, b)` in an undirected graph means `a` and `b` reach each other; in a directed graph it means `a -> b` only, not necessarily the reverse (e.g. "course B requires course A" — you can't flip that). [`CourseSchedule.java`](../../src/main/java/org/prep/day7/CourseSchedule.java) below is directed; the rest of today's problems are undirected.

### Representation — Adjacency List vs Adjacency Matrix
| | Adjacency List | Adjacency Matrix |
|---|---|---|
| Structure | `array/map of node -> list of neighbors` | `n x n` 2D array, `matrix[i][j] = 1` if edge exists |
| Space | O(V + E) | O(V²), regardless of how few edges actually exist |
| "Are a,b connected?" | O(degree of a) — scan a's neighbor list | O(1) — direct lookup |
| "List all of a's neighbors" | O(degree of a) — exactly the list | O(V) — scan a's whole row |
| Best for | **Sparse graphs** (E << V²) — the overwhelmingly common case in interviews | **Dense graphs**, or when O(1) edge-existence checks matter more than space |

**Default to adjacency list** unless the problem specifically hands you a dense graph or repeated O(1) edge-lookups are the bottleneck — it's what every problem below uses. In Java, `List<List<Integer>>` or `Map<Integer, List<Integer>>` for a general graph; a 2D `char[][]`/`int[][]` **grid** (see Number of Islands) is a special case of an adjacency list where the neighbor relationship (up/down/left/right) is implicit in the coordinates rather than stored explicitly — no separate adjacency structure needs to be built at all.

### BFS (Breadth-First Search)
Explore **level by level** using a `Queue`: visit all of a node's direct neighbors before moving to any neighbor's neighbors. This is what guarantees BFS finds the **shortest path in an unweighted graph** — the first time you reach a node, you've reached it by the fewest possible edges, since every closer node was necessarily dequeued first.
```
bfs(start):
    queue = [start], visited = {start}
    while queue not empty:
        node = queue.dequeue()
        process(node)
        for neighbor in adjacency[node]:
            if neighbor not in visited:
                visited.add(neighbor)   // mark on enqueue, not dequeue
                queue.enqueue(neighbor)
```
**Mark `visited` at enqueue time, not dequeue time** — a common bug: if you wait until dequeue to mark visited, the same node can be enqueued multiple times by different neighbors before it's ever processed, wasting work and in the worst case blowing up the queue size.

### DFS (Depth-First Search)
Explore **as deep as possible down one path** before backtracking, using either explicit recursion (the call stack *is* the stack) or an explicit `Stack`. Simpler to write recursively, and the natural choice when you need to explore an entire connected region (flood fill) rather than find a shortest path.
```
dfs(node, visited):
    if node in visited: return
    visited.add(node)
    process(node)
    for neighbor in adjacency[node]:
        dfs(neighbor, visited)
```

### BFS vs DFS — which one, when
- **Shortest path (unweighted)** → BFS. DFS can find *a* path but has no reason to find the *shortest* one — it commits depth-first and only backtracks after exhausting a branch.
- **"Does a path exist at all" / "is this all one connected region" / cycle detection** → either works, same O(V+E) complexity; pick whichever is more natural to write for the problem's shape (grid flood-fill reads more naturally as DFS; layer-by-layer problems read more naturally as BFS).
- **Memory footprint** → DFS's recursion stack is bounded by the graph's **depth** (can be O(V) worst case on a long chain, e.g. a linked-list-shaped graph); BFS's queue is bounded by the **widest level**, which can also be O(V) worst case (e.g. a star graph). Neither is uniformly cheaper — it depends on the graph's shape.
- **Topological sort** → BFS specifically (Kahn's algorithm, below) is the version worth knowing cold, though a DFS-with-postorder version also exists.

### Connected Components
A **connected component** is a maximal set of nodes where every node can reach every other node in the set, with no edges at all to nodes outside it. Counting them: run BFS/DFS from any unvisited node (marks that entire component visited), increment a counter, repeat from the next unvisited node until every node has been visited. [`NumberOfConnectedComponents.java`](../../src/main/java/org/prep/day7/NumberOfConnectedComponents.java) solves this with **Union-Find** instead — see the dedicated section below for why that's often the better tool for this specific shape of problem.

### Cycle Detection
- **Undirected graph**: during BFS/DFS, if you reach an already-`visited` neighbor that **isn't the node you just came from** (its immediate parent in the traversal), that's a cycle — an undirected edge always reflects back to its own source, so you have to explicitly exclude "the edge I just walked" or every single edge falsely looks like a cycle.
- **Directed graph**: a back-edge to a node that's still **on the current recursion path** (not just visited *ever*, but actively an ancestor in this DFS branch) is a cycle. This needs two visited-states, not one: "globally visited" (fully explored, safe) vs. "currently in progress on this path" (a `recursionStack` set, or equivalently the in-degree bookkeeping Kahn's algorithm below uses instead of recursion state). [`CourseSchedule.java`](../../src/main/java/org/prep/day7/CourseSchedule.java) is exactly this problem — "can all courses finish" is "does the prerequisite graph have a cycle."

### Topological Sort (high level)
An ordering of a **directed acyclic graph's (DAG)** nodes such that for every edge `a -> b`, `a` comes before `b` in the ordering — "do the prerequisites before the thing that depends on them." Only defined when the graph has **no cycle** (a cycle would mean two nodes each need to come before the other — no valid order exists), which is exactly why topological sort and cycle detection are the same underlying algorithm viewed two ways.

**Kahn's algorithm (BFS-based)**, used in [`CourseSchedule.java`](../../src/main/java/org/prep/day7/CourseSchedule.java):
```
1. Compute in-degree (count of incoming edges) for every node.
2. Queue up every node with in-degree 0 (no unmet prerequisites).
3. Repeatedly dequeue a node, append it to the result order, and decrement
   the in-degree of each of its neighbors -- if a neighbor's in-degree hits
   0, its last prerequisite was just satisfied, so enqueue it.
4. If every node was eventually dequeued -> valid topological order exists (no cycle).
   If some nodes are left with in-degree > 0 -> they're stuck in a cycle,
   permanently unable to reach in-degree 0.
```
This is *why* Course Schedule doesn't need a separate cycle-detection pass — counting how many nodes got dequeued **is** the cycle check, for free, as a side effect of building the order.

### Union-Find (Disjoint Set Union) — worth knowing alongside BFS/DFS
Not a traversal at all — a different data structure for the same underlying question ("are these two nodes connected"), built around two operations:
- **`find(x)`** — which component (represented by a "root") does `x` currently belong to.
- **`union(a, b)`** — merge `a`'s and `b`'s components into one.

With two optimizations — **path compression** (every `find` call flattens the chain it walks, so future lookups on those nodes are ~O(1)) and **union by size/rank** (always attach the smaller tree under the larger, keeping trees shallow) — both operations run in **O(α(n))** amortized, where α is the inverse Ackermann function: for any n that fits in memory, α(n) ≤ 4, so this is effectively constant time. [`NumberOfConnectedComponents.java`](../../src/main/java/org/prep/day7/NumberOfConnectedComponents.java) implements exactly this.

---

## Part 2 — Problems

All solutions are committed and runnable under [`src/main/java/org/prep/day7/`](../../src/main/java/org/prep/day7/).

### Easy — Find if Path Exists in Graph
[`FindIfPathExistsInGraph.java`](../../src/main/java/org/prep/day7/FindIfPathExistsInGraph.java) — build an adjacency list from the edge list, then BFS from `source` looking for `destination`. Time: O(V+E), Space: O(V+E).
- *Why BFS instead of DFS?* No real reason here — pure reachability, not shortest path, so either is O(V+E) and equally correct. BFS was picked to introduce the queue-based pattern before the grid/DFS problems below.
- *Could Union-Find solve this?* Yes — union every edge, then check `find(source) == find(destination)`. Arguably a better fit than either BFS or DFS if this exact query needs to be answered repeatedly against a graph that's still being built (edges arriving incrementally) — Union-Find answers each query in ~O(1) amortized without redoing a full traversal each time.

### Medium — Number of Islands ⭐⭐⭐⭐
[`NumberOfIslands.java`](../../src/main/java/org/prep/day7/NumberOfIslands.java) — the grid *is* the graph; DFS flood-fills each connected land region, sinking every visited cell so it's never recounted. Time: O(rows·cols), Space: O(rows·cols) worst case (recursion stack on an all-land grid).
- *Why DFS instead of BFS?* Either works identically well (this is the connected-components pattern, not shortest-path) — DFS is picked here because recursive flood-fill is the more natural way to write "consume this whole connected blob," and needs no explicit `Queue`.
- *Could Union-Find solve this?* Yes, and it's a legitimate alternative solution: union every land cell with its land neighbors while scanning the grid once, then the answer is the number of distinct roots among land cells — worth naming as the alternative if asked to avoid recursion (no stack depth risk on a huge grid).
- *Time complexity:* O(rows·cols) either way — every cell is visited and sunk exactly once.

### Medium — Clone Graph ⭐⭐⭐⭐
[`CloneGraph.java`](../../src/main/java/org/prep/day7/CloneGraph.java) — DFS with a `HashMap<originalNode, clonedNode>` serving double duty: visited-check (handles cycles, since an undirected graph's neighbor lists point back at each other) and lookup for wiring already-created clones together. Time: O(V+E), Space: O(V).
- *Why DFS instead of BFS?* No strong reason — BFS with the same map works identically. DFS reads slightly more naturally here because the recursive "clone this node, then recursively clone+link each neighbor" mirrors the problem statement directly.
- *Could Union-Find solve this?* No — Union-Find answers connectivity questions ("are these in the same component"), not "construct a deep copy of this exact structure." Wrong tool for this problem's actual goal.
- *The one bug this problem is designed to catch:* putting the new clone into the map **before** recursing into its neighbors, not after — otherwise a cycle (A points to B, B points back to A) recurses forever, since the map is the only thing that lets the recursion detect "I've already started cloning this node."

### Medium — Course Schedule ⭐⭐⭐⭐⭐
[`CourseSchedule.java`](../../src/main/java/org/prep/day7/CourseSchedule.java) — Kahn's algorithm: peel off in-degree-0 nodes level by level; if all `numCourses` get peeled off, the graph's a DAG (no cycle) and every course can finish. Time: O(V+E), Space: O(V+E).
- *Why BFS (Kahn's) instead of DFS?* Both detect a directed cycle correctly, but Kahn's BFS version is generally considered easier to get right under interview pressure — it needs only one visited concept (in-degree reaching 0), where the DFS version needs **two** visited states (globally-done vs. currently-on-this-recursion-path) to avoid a false positive on a node visited via a different, non-cyclic path. Kahn's also hands you the actual topological order as a side effect, which the DFS version needs an explicit postorder-reversal step to produce.
- *Could Union-Find solve this?* No — Union-Find is fundamentally for **undirected** connectivity; it has no notion of edge direction, so it can't distinguish a genuine directed cycle from a harmless pair of nodes that are merely connected. Wrong tool here.
- *Time complexity:* O(V+E) — every node enqueued/dequeued once, every edge relaxed (in-degree decremented) once.

### Medium — Number of Connected Components
[`NumberOfConnectedComponents.java`](../../src/main/java/org/prep/day7/NumberOfConnectedComponents.java) — solved with Union-Find specifically (rather than the BFS/DFS-loop-over-unvisited-nodes approach) to give a concrete side-by-side contrast. Time: O(V+E·α(V)) ≈ O(V+E), Space: O(V).
- *Why Union-Find instead of BFS/DFS here specifically?* Both are O(V+E) for a one-shot count. Union-Find pulls ahead the moment edges arrive **incrementally** (e.g. "how many components after each of these union operations, in order") — a traversal-based approach would need to re-run the entire O(V+E) scan from scratch after every new edge, while Union-Find updates the component count in ~O(1) amortized per edge as it's added. For a single, one-time count on a fully-known graph, either approach is a legitimate choice.
- *What a BFS/DFS version looks like instead*: loop over every node 0..n-1; on each unvisited node, run a full BFS/DFS marking its entire component visited and increment a counter — same O(V+E), same answer, no `parent[]`/`size[]` arrays needed.

---

## Part 3 — Interview Discussion

**Q: Why BFS instead of DFS, in general — how do you decide?**
**A:** Default to BFS specifically when you need the **shortest path** in an unweighted graph — its level-by-level guarantee is the only one of the two that provides that for free. For everything else (reachability, connected components, cycle detection, flood-fill), either is O(V+E) and correct; pick DFS when recursion naturally mirrors the problem (flood-fill, "clone/copy this structure") and BFS when the problem is explicitly layer/distance-oriented (shortest path, "minimum number of steps," Kahn's topological sort).

**Q: Could Union-Find solve this? (as a general filter to apply to any graph problem)**
**A:** Union-Find is the right tool specifically for **undirected connectivity questions answered incrementally** — "are a and b connected," "how many components," as edges keep arriving. It is the *wrong* tool the moment the problem needs: **edge direction** (Course Schedule — directed cycles), **path length/shortest path** (Union-Find has no notion of distance), or **structure reconstruction** (Clone Graph — Union-Find only tracks group membership, not the actual shape of connections). The filter: "am I only asking whether two things are in the same group, with no ordering or path-length involved?" — yes → Union-Find is on the table; no → traversal.

**Q: What's the time complexity of BFS/DFS, and why?**
**A:** O(V+E) for both, always. Every vertex is enqueued/visited exactly once (`O(V)`), and every edge is examined exactly once per endpoint it's checked from — once for an undirected edge in each direction, or once for a directed edge (`O(E)`). This holds regardless of the graph's shape (dense, sparse, disconnected) because the `visited` set is what prevents ever revisiting a node or re-walking a fully-explored region.

**Q: How do you detect a cycle in a directed vs. undirected graph — why is the check different?**
**A:** Undirected: any edge to an already-visited node that isn't the immediate parent is a cycle, because an undirected edge is inherently bidirectional — you have to explicitly exclude "the edge I just arrived via" or every single traversed edge would falsely register as one. Directed: visiting a node that's still **on the current DFS recursion path** (not merely visited at some earlier point via a different branch) is a cycle — a directed graph can have a node reachable by two completely different, individually acyclic paths, so "ever visited" isn't sufficient; only "currently an ancestor in this exact path" proves a genuine back-edge. Kahn's algorithm (BFS) sidesteps needing this distinction entirely by using in-degree bookkeeping instead of recursion-path tracking.
