package org.prep.day5;

import java.util.LinkedList;
import java.util.Queue;

public class MaximumDepth {

    // Recursive: depth of a node = 1 + deeper of its two subtrees.
    public static int maxDepth(TreeNode root) {
        if (root == null) return 0;
        return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
    }

    // Iterative: BFS level by level, count levels processed.
    public static int maxDepthIterative(TreeNode root) {
        if (root == null) return 0;
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(root);
        int depth = 0;
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                TreeNode node = queue.poll();
                if (node.left != null) queue.add(node.left);
                if (node.right != null) queue.add(node.right);
            }
            depth++;
        }
        return depth;
    }

    public static void main(String[] args) {
        //         3
        //        / \
        //       9  20
        //          / \
        //         15  7
        TreeNode root = new TreeNode(3,
                new TreeNode(9),
                new TreeNode(20, new TreeNode(15), new TreeNode(7)));

        System.out.println(maxDepth(root));            // 3
        System.out.println(maxDepthIterative(root));    // 3
        System.out.println(maxDepth(null));             // 0
    }
}
