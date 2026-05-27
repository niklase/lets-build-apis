package com.zuunr.dcentb.rest.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class BackendTime {

    private static final SimpleDateFormat zuluDateTimeFormat;

    static long diffIntoFuture = 0;


    static {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Z"));
        zuluDateTimeFormat = simpleDateFormat;
    }

    public static String dateTime(long millis) {
        return zuluDateTimeFormat.format(new Date(millis));
    }

    public static String dateTimeNow() {
        return zuluDateTimeFormat.format(now());
    }

    public static Date now() {
        return new Date(System.currentTimeMillis() + diffIntoFuture);
    }

    public static void setNow(Date date) {
        diffIntoFuture = date.getTime() - now().getTime();
    }

    public static void setNow(long currentTimeMillis) {
        setNow(new Date(currentTimeMillis));
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis() + diffIntoFuture;
    }

}
