package org.prep.day6;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * LeetCode 658 - Find K Closest Elements.
 * Heap approach (today's pattern): a max-heap of size k ordered by distance from x keeps
 * exactly the k closest elements seen so far, evicting the farthest whenever it overflows.
 * Time: O(n log k). Space: O(k).
 *
 * A sorted-input-aware binary search on the window's left edge does this in O(log(n-k) + k)
 * without a heap at all -- worth knowing as the "actually optimal" answer, see the doc.
 */
public class FindKClosestElements {

    public List<Integer> findClosestElements(int[] arr, int k, int x) {
        // max-heap ordered by distance from x (farthest at the root, so it's evicted first);
        // ties broken by larger value first, matching the "prefer smaller" tie-break needed
        // at the end when we sort the survivors.
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> {
            int distanceCompare = Math.abs(b - x) - Math.abs(a - x);
            if (distanceCompare != 0) return distanceCompare;
            return b - a;
        });

        for (int num : arr) {
            maxHeap.offer(num);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }

        List<Integer> result = new ArrayList<>(maxHeap);
        Collections.sort(result);
        return result;
    }

    public static void main(String[] args) {
        FindKClosestElements solution = new FindKClosestElements();
        System.out.println(solution.findClosestElements(new int[]{1, 2, 3, 4, 5}, 4, 3)); // [1, 2, 3, 4]
        System.out.println(solution.findClosestElements(new int[]{1, 2, 3, 4, 5}, 4, -1)); // [1, 2, 3, 4]
    }
}
