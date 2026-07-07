package org.prep.arrays;

import java.util.Arrays;

public class StockBuyAndSell {

    // Time complexity = O(n)
    // Space complexity = O(1)
    public static int maxProfit(int[] prices) {

        if(prices.length == 0) return 0;
        int min = prices[0];
        int maxProf = 0;

        for(int price:prices){
            int prof = price - min;
            min = Math.min(min,price);
            maxProf = Math.max(maxProf,prof);
        }
        return maxProf;
    }

    // Time Complexity O(n2)
    // Space Complexity O(1)
    public static int maxProfitBrute(int[] prices) {

        int maxProfit = 0;

        for(int i = 0;i<prices.length-1;i++ ){
            for(int j = i+1;j<prices.length;j++){
                if(prices[j] - prices[i] > maxProfit){
                    maxProfit = prices[j] - prices[i];
                }
            }
        }
        return maxProfit;
    }

    static void main() {
        runTest(new int[]{7, 1, 5, 3, 6, 4}, 5);
        runTest(new int[]{7, 6, 4, 3, 1}, 0);
        runTest(new int[]{1, 2, 3, 4, 5}, 4);
        runTest(new int[]{2, 4, 1}, 2);
        runTest(new int[]{3, 2, 6, 5, 0, 3}, 4);
        runTest(new int[]{1}, 0);
        runTest(new int[]{2, 1, 2, 1, 0, 1, 2}, 2);
        runTest(new int[]{3, 3, 3, 3}, 0);
    }

    private static void runTest(int[] prices, int expected) {
        int actual = maxProfitBrute(prices);
        System.out.println(
                "prices=" + Arrays.toString(prices) +
                        " expected=" + expected +
                        " actual=" + actual +
                        (expected == actual ? " PASS" : " FAIL")
        );
    }
}
