package com.in6222.litsync.ai;

import android.util.LruCache;

public class AiResponseCache {

    private static final AiResponseCache INSTANCE = new AiResponseCache();

    private final LruCache<String, String> cache = new LruCache<>(60);

    private AiResponseCache() {
    }

    public static AiResponseCache getInstance() {
        return INSTANCE;
    }

    public synchronized String get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, String value) {
        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            return;
        }
        cache.put(key, value);
    }

    public synchronized void remove(String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        cache.remove(key);
    }
}
