package org.prep.day5;

public class LowestCommonAncestorBST {

    public static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        TreeNode node = root;
        while (node != null) {
            if (p.val < node.val && q.val < node.val) {
                node = node.left;
            } else if (p.val > node.val && q.val > node.val) {
                node = node.right;
            } else {
                return node;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        TreeNode two = new TreeNode(2);
        TreeNode four = new TreeNode(4);
        TreeNode three = new TreeNode(3, two, four);
        TreeNode seven = new TreeNode(7);
        TreeNode nine = new TreeNode(9);
        TreeNode eightSubtree = new TreeNode(8, seven, nine);
        TreeNode root = new TreeNode(6, three, eightSubtree);

        System.out.println(lowestCommonAncestor(root, two, four).val);   // 3
        System.out.println(lowestCommonAncestor(root, two, eightSubtree).val); // 6
        System.out.println(lowestCommonAncestor(root, seven, nine).val); // 8
    }
}
