package com.donutmoney.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugLog {
    private static final Map<String, Long> lastLogAt = new ConcurrentHashMap<>();

    private DebugLog() {}

    public static boolean enabled() {
        try {
            return ModConfig.get() != null && ModConfig.get().debugLog;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean rateLimit(String key, long intervalMs) {
        if (!enabled()) return false;
        long now = System.currentTimeMillis();
        Long last = lastLogAt.get(key);
        if (last != null && now - last < intervalMs) return false;
        lastLogAt.put(key, now);
        return true;
    }
}
