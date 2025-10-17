package com.zuunr.rest;

import com.zuunr.example.ServletInputStreamWrapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyRequestWrapper( HttpServletRequest httpServletRequest) throws IOException {
        super(httpServletRequest);

        this.cachedBody = StreamUtils.copyToByteArray(httpServletRequest.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream()  {
        return new ServletInputStreamWrapper(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody)));
    }
}
