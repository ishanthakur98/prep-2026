package org.prep.day2;

public class LongestSubstringWithoutRepeatingCharacters {
    
    public int lengthOfLongestSubstring(String s) {
        
        int[] c = new int[26];
        int i = 0;
        int  j = 1;
        int longest = 1;
        int cnt = 1;
        c[s.charAt(i) - 'a']++;

        while(i<j && j < s.length()){

            while(c[s.charAt(j) - 'a'] != 0){
                c[s.charAt(i) - 'a']--;
                i--;
                cnt--;
            }
            c[s.charAt(j) - 'a']++;
            j++;
            cnt++;
            longest = Math.max(longest, cnt);
        }
        return longest;
    }
}
