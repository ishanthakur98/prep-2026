package org.prep.day5;

import java.util.ArrayDeque;
import java.util.Deque;

public class KthSmallestInBST {

    private static int count;
    private static int result;

    // Recursive inorder, stop early once the k-th node is found.
    public static int kthSmallest(TreeNode root, int k) {
        count = 0;
        result = -1;
        inorder(root, k);
        return result;
    }

    private static void inorder(TreeNode node, int k) {
        if (node == null || count >= k) return;
        inorder(node.left, k);
        count++;
        if (count == k) {
            result = node.val;
            return;
        }
        inorder(node.right, k);
    }

    // Iterative: explicit stack walking the left spine, same early-stop benefit.
    public static int kthSmallestIterative(TreeNode root, int k) {
        Deque<TreeNode> stack = new ArrayDeque<>();
        TreeNode curr = root;
        while (curr != null || !stack.isEmpty()) {
            while (curr != null) {
                stack.push(curr);
                curr = curr.left;
            }
            curr = stack.pop();
            if (--k == 0) return curr.val;
            curr = curr.right;
        }
        throw new IllegalArgumentException("k is out of range");
    }

    public static void main(String[] args) {
        TreeNode root = new TreeNode(5,
                new TreeNode(3, new TreeNode(2, new TreeNode(1), null), new TreeNode(4)),
                new TreeNode(6));

        System.out.println(kthSmallest(root, 3));           // 3
        System.out.println(kthSmallestIterative(root, 3));   // 3
        System.out.println(kthSmallest(root, 1));            // 1
    }
}
