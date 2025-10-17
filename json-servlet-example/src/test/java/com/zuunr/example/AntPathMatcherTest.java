package com.zuunr.example;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

public class AntPathMatcherTest {

    @Test
    void test(){
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        antPathMatcher.getPatternComparator("/");



    }
}
