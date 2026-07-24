package org.prep.day7;

/**
 * LeetCode 323 - Number of Connected Components in an Undirected Graph.
 * Solved here with Union-Find (Disjoint Set Union) instead of DFS/BFS, specifically
 * to contrast the two approaches: start with n separate components, union() each
 * edge's endpoints, and count how many distinct roots remain at the end.
 * Union by size + path compression keep both operations effectively O(1) amortized
 * (technically O(alpha(n)), the inverse Ackermann function -- constant for any n
 * that fits in memory), so this whole approach is O(V + E * alpha(V)), essentially O(V + E).
 * A DFS/BFS approach (count how many times a full traversal starts from an unvisited
 * node) is equally valid here and the same O(V + E) -- Union-Find is the better choice
 * specifically when edges arrive incrementally/online and "how many components right
 * now" needs answering after each one, which a from-scratch traversal can't do cheaply.
 */
public class NumberOfConnectedComponents {

    private final int[] parent;
    private final int[] size;
    private int components;

    public NumberOfConnectedComponents(int n) {
        parent = new int[n];
        size = new int[n];
        components = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i; // every node starts as its own root
            size[i] = 1;
        }
    }

    private int find(int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]]; // path compression: flatten the chain as we go
            node = parent[node];
        }
        return node;
    }

    private void union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        if (rootA == rootB) {
            return; // already in the same component -- this edge doesn't merge anything new
        }
        if (size[rootA] < size[rootB]) { // union by size: attach the smaller tree under the larger
            int temp = rootA;
            rootA = rootB;
            rootB = temp;
        }
        parent[rootB] = rootA;
        size[rootA] += size[rootB];
        components--; // two components just merged into one
    }

    public int countComponents(int n, int[][] edges) {
        NumberOfConnectedComponents dsu = new NumberOfConnectedComponents(n);
        for (int[] edge : edges) {
            dsu.union(edge[0], edge[1]);
        }
        return dsu.components;
    }

    public static void main(String[] args) {
        NumberOfConnectedComponents solution = new NumberOfConnectedComponents(5);
        int[][] edges = {{0, 1}, {1, 2}, {3, 4}};
        System.out.println(solution.countComponents(5, edges)); // 2: {0,1,2} and {3,4}
    }
}
