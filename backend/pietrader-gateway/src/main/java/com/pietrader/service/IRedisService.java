package com.pietrader.service;

/**
 * Generic Redis wrapper — set/get/exists/delete with optional TTL.
 * Impl: RedisServiceImpl
 */
public interface IRedisService {
    void            set(String key, Object value, long ttlSeconds);
    void            set(String key, Object value);
    <T> T           get(String key, Class<T> clazz);
    boolean         exists(String key);
    void            delete(String key);
}
