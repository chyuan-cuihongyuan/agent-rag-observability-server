package cn.chyuan.ai.observability.infrastructure.redis;

import cn.chyuan.ai.observability.domain.observe.adapter.cache.ICachePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DashboardCacheService implements ICachePort {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "observe:dashboard:";
    private static final long DEFAULT_TTL_MINUTES = 5;

    public void cache(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + key, value, DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("redis cache set error", e);
        }
    }

    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception e) {
            log.debug("redis cache get error", e);
            return null;
        }
    }

    @Override
    public void increment(String counterKey) {
        try {
            stringRedisTemplate.opsForValue().increment(KEY_PREFIX + "counter:" + counterKey);
        } catch (Exception e) {
            log.debug("redis increment error", e);
        }
    }
}
