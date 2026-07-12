package org.prep.day3;

public class BinarySearchBasics {

    // Classic binary search: return index of target, or -1 if not found.
    public static int search(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] == target) return mid;
            else if (arr[mid] < target) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }

    // Lower bound: first index where arr[i] >= target (arr.length if none).
    public static int lowerBound(int[] arr, int target) {
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] >= target) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    // Upper bound: first index where arr[i] > target (arr.length if none).
    public static int upperBound(int[] arr, int target) {
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (arr[mid] > target) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    public static void main(String[] args) {
        int[] arr = {1, 3, 3, 3, 5, 7, 9};

        System.out.println(search(arr, 7));        // 5
        System.out.println(search(arr, 4));         // -1

        System.out.println(lowerBound(arr, 3));      // 1 (first index with value >= 3)
        System.out.println(lowerBound(arr, 4));      // 4 (first index with value >= 4)
        System.out.println(lowerBound(arr, 10));     // 7 (nothing >= 10)

        System.out.println(upperBound(arr, 3));      // 4 (first index with value > 3)
        System.out.println(upperBound(arr, 9));      // 7 (nothing > 9)

        // [lowerBound, upperBound) is the exact range of value 3 -> indices [1, 4)
        System.out.println(lowerBound(arr, 3) + ".." + upperBound(arr, 3));
    }
}
