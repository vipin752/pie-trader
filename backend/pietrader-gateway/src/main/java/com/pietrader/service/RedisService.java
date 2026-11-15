package com.pietrader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value, long ttlSeconds) {
	redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public void set(String key, Object value) {
	redisTemplate.opsForValue().set(key, value);
    }

    public <T> T get(String key, Class<T> clazz) {
	Object value = redisTemplate.opsForValue().get(key);
	if (value == null) return null;
	return clazz.cast(value);
    }

    public boolean exists(String key) {
	return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String key) {
	redisTemplate.delete(key);
    }
}
