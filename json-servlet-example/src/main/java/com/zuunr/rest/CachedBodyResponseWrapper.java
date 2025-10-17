package com.zuunr.rest;

import com.zuunr.example.ServletOutputStreamWrapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;

public class CachedBodyResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream byteArrayOutputStream;
    private ServletOutputStreamWrapper servletOutputStreamWrapper;

    public CachedBodyResponseWrapper( HttpServletResponse httpServletResponse) {
        super(httpServletResponse);
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (servletOutputStreamWrapper == null) {
            servletOutputStreamWrapper = new ServletOutputStreamWrapper(byteArrayOutputStream);
        }
        return servletOutputStreamWrapper;
    }


    public byte[] getDataStream() {
        return byteArrayOutputStream.toByteArray();
    }

}
