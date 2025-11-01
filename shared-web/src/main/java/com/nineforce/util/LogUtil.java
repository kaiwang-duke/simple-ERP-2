package com.nineforce.util;

import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small logging helpers. */
public final class LogUtil {
    private LogUtil() {}

    /** Logs a clear “header → stacktrace → footer” block. */
    public static void exceptionBlock(Logger log, String title, Map<String, ?> context, Throwable t) {
        final String bar = "=".repeat(80);
        log.error("\n\n{}\n{} — context: {}\n{}", bar, title, context, bar);
        log.error("{} — STACKTRACE ↓↓↓", title, t);
        log.error("\n{}\nEND {}\n{}\n\n", bar, title, bar);
    }

    public static void exceptionBlock(Logger log, String title, Throwable t, Object... kvPairs) {
        exceptionBlock(log, title, toMap(kvPairs), t);
    }

    private static Map<String, Object> toMap(Object... kvPairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            m.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return m;
    }
}