package org.prep.day5;

public class SameTree {

    public static boolean isSameTree(TreeNode p, TreeNode q) {
        if (p == null && q == null) return true;
        if (p == null || q == null) return false;
        if (p.val != q.val) return false;
        return isSameTree(p.left, q.left) && isSameTree(p.right, q.right);
    }

    public static void main(String[] args) {
        TreeNode a = new TreeNode(1, new TreeNode(2), new TreeNode(3));
        TreeNode b = new TreeNode(1, new TreeNode(2), new TreeNode(3));
        TreeNode c = new TreeNode(1, new TreeNode(2), null);

        System.out.println(isSameTree(a, b)); // true
        System.out.println(isSameTree(a, c)); // false
    }
}
