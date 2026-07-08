package org.prep.oops;

import java.util.Objects;

final class Student {

    final int id;
    final String name;

    Student(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        return id == student.id && Objects.equals(name, student.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id , name);
    }

    static void main() {
        Student s1 = new Student(1, "asha");

        Student s2 = new Student(1,"asha");

        System.out.println(s1.equals(s2));
    }
}
