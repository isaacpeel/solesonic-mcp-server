package com.solesonic.mcp.jira.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple fixed-window rate limiter per key.
 */
public class RateLimiter {
    private static class Window {
        Instant start;
        int count;
        Window(Instant start) { this.start = start; this.count = 0; }
    }

    private final int limit;
    private final Duration window;
    private final Map<String, Window> map = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    public boolean allow(String key) {
        Window rateWindow = map.computeIfAbsent(key, _ -> new Window(Instant.now()));
        synchronized (rateWindow) {
            Instant now = Instant.now();
            if (now.isAfter(rateWindow.start.plus(window))) {
                rateWindow.start = now;
                rateWindow.count = 0;
            }
            if (rateWindow.count >= limit) {
                return false;
            }
            rateWindow.count++;
            return true;
        }
    }
}
