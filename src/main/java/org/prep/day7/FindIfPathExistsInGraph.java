package org.prep.day7;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * LeetCode 1971 - Find if Path Exists in Graph.
 * Undirected graph given as an edge list; build an adjacency list, then BFS from
 * source looking for destination. Any traversal (BFS or DFS) works equally well
 * here since we only care about reachability, not shortest path or ordering.
 * Time: O(V + E). Space: O(V + E) for the adjacency list + visited array.
 */
public class FindIfPathExistsInGraph {

    public boolean validPath(int n, int[][] edges, int source, int destination) {
        List<List<Integer>> adjacency = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adjacency.add(new ArrayList<>());
        }
        for (int[] edge : edges) {
            adjacency.get(edge[0]).add(edge[1]);
            adjacency.get(edge[1]).add(edge[0]); // undirected: add both directions
        }

        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new ArrayDeque<>();
        queue.offer(source);
        visited[source] = true;

        while (!queue.isEmpty()) {
            int node = queue.poll();
            if (node == destination) {
                return true;
            }
            for (int neighbor : adjacency.get(node)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true; // mark on enqueue, not on dequeue -- avoids duplicate enqueues
                    queue.offer(neighbor);
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        FindIfPathExistsInGraph solution = new FindIfPathExistsInGraph();
        int[][] edges = {{0, 1}, {1, 2}, {2, 0}};
        System.out.println(solution.validPath(3, edges, 0, 2)); // true
        int[][] disconnected = {{0, 1}, {2, 3}};
        System.out.println(solution.validPath(4, disconnected, 0, 3)); // false
    }
}
