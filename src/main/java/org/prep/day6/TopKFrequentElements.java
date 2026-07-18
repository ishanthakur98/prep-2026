package org.prep.day6;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * LeetCode 347 - Top K Frequent Elements.
 * Count frequencies with a HashMap, then keep a min-heap of size k ordered by frequency
 * so the heap only ever holds the current top-k candidates.
 * Time: O(n log k). Space: O(n) for the frequency map.
 */
public class TopKFrequentElements {

    public int[] topKFrequent(int[] nums, int k) {
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int num : nums) {
            frequency.merge(num, 1, Integer::sum);
        }

        // min-heap ordered by frequency -- the least frequent of the current top-k sits at the root
        PriorityQueue<Map.Entry<Integer, Integer>> minHeap =
                new PriorityQueue<>((a, b) -> a.getValue() - b.getValue());

        for (Map.Entry<Integer, Integer> entry : frequency.entrySet()) {
            minHeap.offer(entry);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }

        int[] result = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            result[i] = minHeap.poll().getKey();
        }
        return result;
    }

    public static void main(String[] args) {
        TopKFrequentElements solution = new TopKFrequentElements();
        System.out.println(java.util.Arrays.toString(
                solution.topKFrequent(new int[]{1, 1, 1, 2, 2, 3}, 2))); // [2, 1]
    }
}
