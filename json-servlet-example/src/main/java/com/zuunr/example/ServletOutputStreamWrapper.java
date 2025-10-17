package com.zuunr.example;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.springframework.lang.NonNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public class ServletOutputStreamWrapper extends ServletOutputStream
{

    private final DataOutputStream dataOutputStream;

    public ServletOutputStreamWrapper(OutputStream outputStream) {
        this.dataOutputStream = new DataOutputStream(outputStream);
    }

    @Override
    public void write(int b) throws IOException {
        dataOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);

        for (int i = 0; i < len; i++) {
            this.write(b[off + i]);
        }
    }

    @Override
    public void write(@NonNull byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        /*
         * For non-blocking IO.
         */
    }

}
