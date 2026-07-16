package org.prep.day5;

public class ValidateBST {

    public static boolean isValidBST(TreeNode root) {
        return validate(root, null, null);
    }

    private static boolean validate(TreeNode node, Integer lower, Integer upper) {
        if (node == null) return true;
        if (lower != null && node.val <= lower) return false;
        if (upper != null && node.val >= upper) return false;
        return validate(node.left, lower, node.val) && validate(node.right, node.val, upper);
    }

    // Alternative: inorder traversal must be strictly increasing on a valid BST.
    private static Integer prev;

    public static boolean isValidBSTInorder(TreeNode root) {
        prev = null;
        return inorderCheck(root);
    }

    private static boolean inorderCheck(TreeNode node) {
        if (node == null) return true;
        if (!inorderCheck(node.left)) return false;
        if (prev != null && node.val <= prev) return false;
        prev = node.val;
        return inorderCheck(node.right);
    }

    public static void main(String[] args) {
        TreeNode valid = new TreeNode(5,
                new TreeNode(3, new TreeNode(1), new TreeNode(4)),
                new TreeNode(8, new TreeNode(7), new TreeNode(9)));
        System.out.println(isValidBST(valid));          // true
        System.out.println(isValidBSTInorder(valid));    // true

        // Locally looks fine at every parent-child pair, but 6 is in 5's right
        // subtree and smaller than 5's right child 10's left descendant check fails.
        TreeNode invalid = new TreeNode(5,
                new TreeNode(3),
                new TreeNode(10, new TreeNode(6), new TreeNode(15)));
        System.out.println(isValidBST(invalid));         // false
        System.out.println(isValidBSTInorder(invalid));  // false
    }
}
