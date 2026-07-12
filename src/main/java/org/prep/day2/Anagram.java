package org.prep.day2;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Anagram {
    
    public static boolean isAnagram(String s, String t) {
        long start = System.nanoTime();
        if(s.length() != t.length()) return false;
        char[] a = s.toCharArray();
        char[] b = t.toCharArray();

        Arrays.sort(a);
        Arrays.sort(b);
        
        String first = new String(a);
        String second = new String(b);
        System.out.println("Time taken "+ (System.nanoTime() - start));
        return first.equals(second);
    }

    public static boolean isAnagramOpt(String s, String t) {
        long start = System.nanoTime();
        if(s.length() != t.length()) return false;
        
        int[] cnt = new int[26];

        for(int i=0;i<s.length();i++){
            cnt[s.charAt(i) - 'a']++;
            cnt[t.charAt(i) - 'a']--;
        }
        for(int a:cnt){
            if(a>0) return false;
        }
        System.out.println("Time taken "+ (System.nanoTime() - start));
        return true;
    }

    public static void main(String[] args) {
    System.out.println(isAnagramOpt("anagram", "nagaram"));   // true
    System.out.println(isAnagramOpt("rat", "car"));           // false
    System.out.println(isAnagramOpt("listen", "silent"));     // true
    System.out.println(isAnagramOpt("hello", "bello"));       // false
    System.out.println(isAnagramOpt("a", "a"));               // true
    System.out.println(isAnagramOpt("a", "b"));               // false
    System.out.println(isAnagramOpt("", ""));                 // true
    System.out.println(isAnagramOpt("abc", "ab"));            // false
    System.out.println(isAnagramOpt("abc", "cba"));           // true
    System.out.println(isAnagramOpt("aabbcc", "abcabc"));     // true
    System.out.println(isAnagramOpt("aabbcc", "aabbcd"));     // false
    System.out.println(isAnagramOpt("xxyyzz", "zzyyxx"));     // true
}
}
