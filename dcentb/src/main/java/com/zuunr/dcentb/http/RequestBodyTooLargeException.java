package com.zuunr.dcentb.http;

/**
 * @author Niklas Eldberger
 */
public class RequestBodyTooLargeException extends RuntimeException {

    public RequestBodyTooLargeException(String message) {
        super(message);
    }
}
