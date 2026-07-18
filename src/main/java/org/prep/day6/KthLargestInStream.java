package org.prep.day6;

import java.util.PriorityQueue;

/**
 * LeetCode 703 - Kth Largest Element in a Stream.
 * Maintain a min-heap of size k: its root is always the kth largest seen so far.
 * add(): O(log k). Space: O(k).
 */
public class KthLargestInStream {

    private final int k;
    private final PriorityQueue<Integer> minHeap; // root = smallest of the k largest kept

    public KthLargestInStream(int k, int[] nums) {
        this.k = k;
        this.minHeap = new PriorityQueue<>(); // natural ordering -> min-heap
        for (int num : nums) {
            add(num);
        }
    }

    public int add(int val) {
        minHeap.offer(val);
        if (minHeap.size() > k) {
            minHeap.poll(); // evict the smallest, it can't be the kth largest anymore
        }
        return minHeap.peek();
    }

    public static void main(String[] args) {
        KthLargestInStream kthLargest = new KthLargestInStream(3, new int[]{4, 5, 8, 2});
        System.out.println(kthLargest.add(3));  // 4
        System.out.println(kthLargest.add(5));  // 5
        System.out.println(kthLargest.add(10)); // 5
        System.out.println(kthLargest.add(9));  // 8
        System.out.println(kthLargest.add(4));  // 8
    }
}
