package org.prep.day7;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LeetCode 133 - Clone Graph.
 * DFS from the given node, using a HashMap<original, clone> both as the
 * "have I visited this node" check (handles cycles -- an undirected graph's
 * adjacency naturally has them, e.g. A-B-A) and as the lookup for wiring up
 * already-created clones' neighbor lists.
 * Time: O(V + E). Space: O(V) for the map + recursion stack.
 */
public class CloneGraph {

    public static class Node {
        public int val;
        public List<Node> neighbors;

        public Node(int val) {
            this.val = val;
            this.neighbors = new ArrayList<>();
        }
    }

    private final Map<Node, Node> visited = new HashMap<>();

    public Node cloneGraph(Node node) {
        if (node == null) {
            return null;
        }
        if (visited.containsKey(node)) {
            return visited.get(node); // already cloned -- return the existing clone, don't recurse again
        }

        Node clone = new Node(node.val);
        visited.put(node, clone); // put BEFORE recursing into neighbors, or a cycle recurses forever

        for (Node neighbor : node.neighbors) {
            clone.neighbors.add(cloneGraph(neighbor));
        }
        return clone;
    }

    public static void main(String[] args) {
        // 1 - 2
        // |   |
        // 4 - 3
        Node n1 = new Node(1);
        Node n2 = new Node(2);
        Node n3 = new Node(3);
        Node n4 = new Node(4);
        n1.neighbors.add(n2);
        n1.neighbors.add(n4);
        n2.neighbors.add(n1);
        n2.neighbors.add(n3);
        n3.neighbors.add(n2);
        n3.neighbors.add(n4);
        n4.neighbors.add(n1);
        n4.neighbors.add(n3);

        Node cloned = new CloneGraph().cloneGraph(n1);
        System.out.println(cloned.val + " neighbors: " + cloned.neighbors.size()); // 1 neighbors: 2
        System.out.println(cloned != n1); // true -- a genuinely new object, not the same reference
    }
}
