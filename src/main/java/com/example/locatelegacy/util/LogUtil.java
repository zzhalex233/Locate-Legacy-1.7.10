package com.example.locatelegacy.util;

public final class LogUtil {

    private static final String PREFIX = "[LocateLegacy] ";

    private LogUtil() {}

    public static void warn(String message, Throwable t) {
        log("WARN", message, t);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message, t);
    }

    private static void log(String level, String message, Throwable t) {
        String msg = message == null ? "" : message;
        System.err.println(PREFIX + level + ": " + msg);
        if (t != null) t.printStackTrace(System.err);
    }
}
