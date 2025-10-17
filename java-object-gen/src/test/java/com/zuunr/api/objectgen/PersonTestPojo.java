package com.zuunr.api.objectgen;

import java.util.List;

public class PersonTestPojo {
    public String name;
    public Integer age;
    public List<String> friends;

    public String getName(){
        return name;
    }

    public Integer getAge(){
        return age;
    }

    public List<String> getFriends(){
        return friends;
    }
}
