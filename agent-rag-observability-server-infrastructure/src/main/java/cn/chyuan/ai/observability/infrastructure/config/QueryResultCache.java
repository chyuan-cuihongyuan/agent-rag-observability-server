package cn.chyuan.ai.observability.infrastructure.config;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * 查询结果 Redis 缓存
 * <p>
 * key 格式：query:{sha256hash}
 * TTL：5 分钟
 * 用于缓存高频查询结果，减轻数据库压力。
 */
@Slf4j
@Component
public class QueryResultCache {

    private static final String KEY_PREFIX = "query:";
    private static final long TTL_MINUTES = 5;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取缓存的查询结果
     *
     * @param queryKey 原始查询标识（如 SQL + 参数拼接）
     * @param clazz    结果类型
     * @param <T>      结果泛型
     * @return 缓存结果，未命中返回 null
     */
    public <T> T get(String queryKey, Class<T> clazz) {
        try {
            String redisKey = buildKey(queryKey);
            String json = stringRedisTemplate.opsForValue().get(redisKey);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            log.debug("query cache get failed, key={}", queryKey, e);
            return null;
        }
    }

    /**
     * 写入查询结果缓存
     *
     * @param queryKey 原始查询标识
     * @param value    查询结果
     * @param <T>      结果泛型
     */
    public <T> void put(String queryKey, T value) {
        if (value == null) {
            return;
        }
        try {
            String redisKey = buildKey(queryKey);
            String json = JSON.toJSONString(value);
            stringRedisTemplate.opsForValue().set(redisKey, json, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("query cache put failed, key={}", queryKey, e);
        }
    }

    /**
     * 删除指定查询缓存
     *
     * @param queryKey 原始查询标识
     */
    public void evict(String queryKey) {
        try {
            String redisKey = buildKey(queryKey);
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.debug("query cache evict failed, key={}", queryKey, e);
        }
    }

    /**
     * 构建 Redis key：query:{sha256hash}
     */
    private String buildKey(String queryKey) {
        return KEY_PREFIX + sha256(queryKey);
    }

    /**
     * SHA-256 哈希，生成固定长度 key，避免 key 过长
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
