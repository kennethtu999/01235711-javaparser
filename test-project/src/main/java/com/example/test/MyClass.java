package com.example.test;

import java.util.List;
import java.util.ArrayList;

public class MyClass {
    private String name;
    private int value;

    public MyClass(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public static List<String> createList(String item) {
        List<String> list = new ArrayList<>();
        list.add(item);
        return list;
    }
}