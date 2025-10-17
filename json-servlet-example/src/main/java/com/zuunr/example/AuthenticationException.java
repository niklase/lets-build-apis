package com.zuunr.example;

import com.zuunr.json.JsonObject;

public class AuthenticationException extends RuntimeException {
    public final JsonObject validation;
    public AuthenticationException(JsonObject validation){
        this.validation = validation;
    }
}
