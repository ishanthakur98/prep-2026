package org.prep.day3;

public class SearchInsertPosition {

    // Return index of target if found, else the index it would be inserted at
    // to keep the array sorted. This is exactly "lower bound".
    public static int searchInsert(int[] nums, int target) {
        int lo = 0, hi = nums.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (nums[mid] >= target) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    public static void main(String[] args) {
        int[] nums = {1, 3, 5, 6};

        System.out.println(searchInsert(nums, 5));  // 2 (found)
        System.out.println(searchInsert(nums, 2));  // 1 (insert between 1 and 3)
        System.out.println(searchInsert(nums, 7));  // 4 (insert at end)
        System.out.println(searchInsert(nums, 0));  // 0 (insert at start)
    }
}
