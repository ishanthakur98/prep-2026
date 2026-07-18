package org.prep.day6;

import java.util.Collections;
import java.util.PriorityQueue;

/**
 * LeetCode 1046 - Last Stone Weight.
 * Repeatedly smash the two heaviest stones -- a max-heap gives O(log n) access to both
 * on every round instead of re-scanning/re-sorting the array each time.
 * Time: O(n log n) overall (n smashes, each O(log n)). Space: O(n).
 */
public class LastStoneWeight {

    public int lastStoneWeight(int[] stones) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        for (int stone : stones) {
            maxHeap.offer(stone);
        }

        while (maxHeap.size() > 1) {
            int heaviest = maxHeap.poll();
            int secondHeaviest = maxHeap.poll();
            int remainder = heaviest - secondHeaviest;
            if (remainder > 0) {
                maxHeap.offer(remainder);
            }
        }

        return maxHeap.isEmpty() ? 0 : maxHeap.peek();
    }

    public static void main(String[] args) {
        LastStoneWeight solution = new LastStoneWeight();
        System.out.println(solution.lastStoneWeight(new int[]{2, 7, 4, 1, 8, 1})); // 1
    }
}
