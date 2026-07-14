package org.prep.day2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupAnagram {
    
    public List<List<String>> groupAnagrams(String[] strs) {
        
        Map<String,List<String>> map = new HashMap<>();

        for(String s:strs){
            char[] c = s.toCharArray();
            Arrays.sort(c);
            String sorted = new String(c);
            List<String> li = map.getOrDefault(sorted, new ArrayList<>());
            li.add(s);
            map.put(sorted, li);
        }
        List<List<String>> list = new ArrayList<>();

        for(List<String> e:map.values()){
            list.add(e);
        }
        return list;
    }
}
