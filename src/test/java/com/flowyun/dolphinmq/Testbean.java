package com.flowyun.dolphinmq;

import lombok.Data;

@Data
public class Testbean {
    private String name;
    private int age;

    public Testbean(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public Testbean() {

    }
}