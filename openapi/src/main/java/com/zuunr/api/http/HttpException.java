package com.zuunr.api.http;

public class HttpException extends RuntimeException {

    public final int statusCode;
    public HttpException(int statusCode) {
        this.statusCode = statusCode;
    }
}
