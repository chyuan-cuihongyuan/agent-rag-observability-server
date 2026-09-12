package cn.chyuan.ai.observability.trigger.config;

import cn.chyuan.ai.observability.domain.resilience.adapter.port.IResilienceStore;
import cn.chyuan.ai.observability.domain.resilience.service.InMemoryResilienceStore;
import cn.chyuan.ai.observability.domain.resilience.service.ResilienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 调度韧性装配（五期 AD 簇 0220-0223）—
 * 存储端口缺省回退内存实现（单机/测试）；水位阈值配置化。
 *
 * @author chyuan
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public ResilienceService resilienceService(@Autowired(required = false) IResilienceStore store,
            @Value("${resilience.lag.warn-threshold:1000}") long warnThreshold,
            @Value("${resilience.lag.critical-threshold:10000}") long criticalThreshold) {
        return new ResilienceService(store == null ? new InMemoryResilienceStore() : store,
                warnThreshold, criticalThreshold);
    }
}
