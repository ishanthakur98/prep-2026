package org.prep.practice;

import java.util.HashMap;
import java.util.Map;

public class Demo {
    

    public static void main(String[] args) {
        String str = "Hello World";
        Map<Character,Integer> map = new HashMap<>();
        for(char c: str.toCharArray()){
            map.merge(c, 1, Integer::sum);
        }
        System.out.println(map);
    }
}
