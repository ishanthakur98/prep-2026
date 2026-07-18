package org.prep.day6;

import java.util.PriorityQueue;

/**
 * LeetCode 215 - Kth Largest Element in an Array.
 * Min-heap capped at size k: O(n log k) time, O(k) space -- beats a full O(n log n) sort
 * whenever k is small relative to n, since the heap never holds more than k elements.
 */
public class KthLargestElement {

    public int findKthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        return minHeap.peek();
    }

    public static void main(String[] args) {
        KthLargestElement solution = new KthLargestElement();
        System.out.println(solution.findKthLargest(new int[]{3, 2, 1, 5, 6, 4}, 2));       // 5
        System.out.println(solution.findKthLargest(new int[]{3, 2, 3, 1, 2, 4, 5, 5, 6}, 4)); // 4
    }
}
