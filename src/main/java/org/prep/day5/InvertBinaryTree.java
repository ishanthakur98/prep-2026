package org.prep.day5;

public class InvertBinaryTree {

    public static TreeNode invertTree(TreeNode root) {
        if (root == null) return null;
        TreeNode left = invertTree(root.left);
        TreeNode right = invertTree(root.right);
        root.left = right;
        root.right = left;
        return root;
    }

    private static String preorder(TreeNode node) {
        if (node == null) return ".";
        return node.val + "(" + preorder(node.left) + "," + preorder(node.right) + ")";
    }

    public static void main(String[] args) {
        //       4
        //      / \
        //     2   7
        //    / \ / \
        //   1  3 6  9
        TreeNode root = new TreeNode(4,
                new TreeNode(2, new TreeNode(1), new TreeNode(3)),
                new TreeNode(7, new TreeNode(6), new TreeNode(9)));

        System.out.println(preorder(root));                // before
        System.out.println(preorder(invertTree(root)));     // after: mirrored
    }
}
