package org.prep.arrays;

import java.util.HashMap;
import java.util.Map;

public class TwoSum {

    // time complexity O(n2)
    // space complexity 0(1)
    public int[] twoSum(int[] nums, int target) {
        for(int  i =0;i<nums.length-1;i++){
            for(int j = i+1;j<nums.length;j++){
                if(nums[i] + nums[j] == target){
                    return new int[]{i,j};
                }
            }
        }
        return new int[0];

    }
    // Time complexity O(n)
    // Space complexity O(n)

    static int[] twoSumOptimized(int[] nums, int target) {
        Map<Integer,Integer> map = new HashMap<>();
        for(int  i =0;i<nums.length;i++){
            if(map.containsKey(target - nums[i])){
                return new int[]{map.get(target - nums[i]) , i};
            }
            map.put(nums[i],i);
        }
        return new int[0];

    }

    static void main() {
        System.out.println(java.util.Arrays.toString(twoSumOptimized(new int[]{2, 7, 11, 15}, 9)));
        System.out.println(java.util.Arrays.toString(twoSumOptimized(new int[]{3, 2, 4}, 6)));
        System.out.println(java.util.Arrays.toString(twoSumOptimized(new int[]{3, 3}, 6)));
        System.out.println(java.util.Arrays.toString(twoSumOptimized(new int[]{1, 5, 1, 5}, 10)));
        System.out.println(java.util.Arrays.toString(twoSumOptimized(new int[]{-1, -2, -3, -4, -5}, -8)));
        System.out.println(java.util.Arrays.toString(twoSumOptimized(new int[]{1, 2, 3}, 7)));
    }


}
