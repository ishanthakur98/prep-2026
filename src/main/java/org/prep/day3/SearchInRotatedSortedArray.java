package org.prep.day3;

public class SearchInRotatedSortedArray {

    // Search target in a sorted array rotated at an unknown pivot, no duplicates.
    // At every mid, at least one half is provably still sorted -- use that half's
    // range to decide whether target could be in it.
    public static int search(int[] nums, int target) {
        int lo = 0, hi = nums.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (nums[mid] == target) return mid;

            if (nums[lo] <= nums[mid]) {
                // left half [lo..mid] is sorted
                if (nums[lo] <= target && target < nums[mid]) {
                    hi = mid - 1;
                } else {
                    lo = mid + 1;
                }
            } else {
                // right half [mid..hi] is sorted
                if (nums[mid] < target && target <= nums[hi]) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] nums = {4, 5, 6, 7, 0, 1, 2};

        System.out.println(search(nums, 0));  // 4
        System.out.println(search(nums, 3));  // -1
        System.out.println(search(nums, 4));  // 0
        System.out.println(search(nums, 2));  // 6

        int[] notRotated = {1, 2, 3, 4, 5};
        System.out.println(search(notRotated, 3)); // 2
    }
}
