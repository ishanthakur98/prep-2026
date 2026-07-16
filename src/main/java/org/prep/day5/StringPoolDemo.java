package org.prep.day5;

public class StringPoolDemo {

    private static void stringPoolBehavior() {
        String a = "hello";
        String b = "hello";
        String c = new String("hello");
        String d = c.intern();

        System.out.println("a == b: " + (a == b));               // true
        System.out.println("a == c: " + (a == c));               // false
        System.out.println("a.equals(c): " + a.equals(c));        // true
        System.out.println("a == d (interned): " + (a == d));     // true
    }

    private static void equalsVsDoubleEquals() {
        String x = new String("java");
        String y = new String("java");

        System.out.println("x == y: " + (x == y));               // false
        System.out.println("x.equals(y): " + x.equals(y));        // true
    }

    private static void integerCaching() {
        Integer i1 = 100;
        Integer i2 = 100;
        System.out.println("100 == 100 (boxed): " + (i1 == i2));  // true, cached

        Integer i3 = 200;
        Integer i4 = 200;
        System.out.println("200 == 200 (boxed): " + (i3 == i4));  // false, not cached

        Integer i5 = Integer.valueOf(100);
        System.out.println("i1 == Integer.valueOf(100): " + (i1 == i5)); // true
    }

    public static void main(String[] args) {
        stringPoolBehavior();
        System.out.println("---");
        equalsVsDoubleEquals();
        System.out.println("---");
        integerCaching();
    }
}
