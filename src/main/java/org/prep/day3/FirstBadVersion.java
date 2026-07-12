package org.prep.day3;

public class FirstBadVersion {

    // In the real LeetCode problem isBadVersion() is a given API you can't modify.
    // Here it's simulated with a configurable threshold so main() can test it.
    private static int firstBad;

    private static boolean isBadVersion(int version) {
        return version >= firstBad;
    }

    // Binary search on the boolean predicate isBadVersion(): find the first
    // version where it flips from false to true. Same shape as lowerBound.
    public static int firstBadVersion(int n) {
        int lo = 1, hi = n;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2; // avoid overflow, not (lo + hi) / 2
            if (isBadVersion(mid)) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    public static void main(String[] args) {
        firstBad = 4;
        System.out.println(firstBadVersion(10)); // 4

        firstBad = 1;
        System.out.println(firstBadVersion(5));  // 1 (everything is bad)

        firstBad = 10;
        System.out.println(firstBadVersion(10)); // 10 (only the last one is bad)
    }
}
