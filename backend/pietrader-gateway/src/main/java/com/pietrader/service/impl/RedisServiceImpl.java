package com.pietrader.service.impl;

import com.pietrader.service.IRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements IRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        return clazz.cast(value);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
