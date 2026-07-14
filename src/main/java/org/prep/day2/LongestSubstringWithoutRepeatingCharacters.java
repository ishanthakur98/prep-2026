package org.prep.day2;

public class LongestSubstringWithoutRepeatingCharacters {
    
    public int lengthOfLongestSubstring(String s) {
        if(s.length() == 0) return 0;
        int[] c = new int[128];
        int i = 0;
        int  j = 0;
        int longest = 0;
        int cnt = 0;

        while(j < s.length()){

            while(c[s.charAt(j)] != 0){
                c[s.charAt(i)]--;
                i++;
                cnt--;
            }
            c[s.charAt(j)]++;
            j++;
            cnt++;
            longest = Math.max(longest, cnt);
        }
        return longest;
    }
}
