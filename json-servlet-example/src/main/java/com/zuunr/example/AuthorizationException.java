package com.zuunr.example;

import com.zuunr.json.JsonObject;

public class AuthorizationException extends RuntimeException {

    public final JsonObject validation;
    public AuthorizationException(JsonObject validation){
        this.validation = validation;
    }

}
