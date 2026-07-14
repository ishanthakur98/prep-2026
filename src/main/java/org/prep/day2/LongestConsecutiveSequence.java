package org.prep.day2;

import java.util.HashSet;
import java.util.Set;

public class LongestConsecutiveSequence {
    
    public int longestConsecutive(int[] nums) {
        
        Set<Integer> set = new HashSet<>();
        for(int num:nums) set.add(num);
        int longest = 0;

        for(int curr:set){
            if(!set.contains(curr - 1)){
                int cnt = 1;
                while(set.contains(curr + cnt)) cnt++;
                longest = Math.max(cnt, longest);
            }
        }
        return longest;
    }
}
