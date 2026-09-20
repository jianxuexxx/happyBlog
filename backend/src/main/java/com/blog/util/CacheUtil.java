package com.blog.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 读写封装（设计规格 §8「显式优先」）。
 * 键名由调用方显式传入并逐字对齐设计规格 §9，不做任何前缀拼接，
 * 这样 redis-cli 里看到的键名与规格文档一致，排查时可直接对照。
 */
@Component
public class CacheUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheUtil(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** 未命中返回 null。 */
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, type);
    }

    /** 未命中返回 null（而非空列表），使调用方能区分「无缓存」与「缓存了空集」。 */
    public <T> List<T> getList(String key, Class<T> elementType) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(
                value,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
