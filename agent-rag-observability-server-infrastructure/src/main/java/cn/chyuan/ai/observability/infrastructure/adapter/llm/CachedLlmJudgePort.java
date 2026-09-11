package cn.chyuan.ai.observability.infrastructure.adapter.llm;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.ILlmJudgePort;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IJudgeCacheRepository;
import cn.chyuan.ai.observability.domain.evaluate.service.JudgeCacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 带缓存的 LLM judge 装饰器（工单 0176 X7）— eval.judge-cache-enabled=true 时以 @Primary
 * 覆盖原 judge 适配器成为注入实现：先查缓存（命中 +1 并直接返回），未命中调底层并回写。
 * RubricExecutionEngine 零改动获得缓存能力（装饰器模式）。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "eval.judge-cache-enabled", havingValue = "true")
public class CachedLlmJudgePort implements ILlmJudgePort {

    private final ILlmJudgePort delegate;
    private final IJudgeCacheRepository cacheRepository;

    public CachedLlmJudgePort(@Qualifier("llmJudgeAdapter") ILlmJudgePort delegate,
                              IJudgeCacheRepository cacheRepository) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public boolean available() {
        return delegate.available();
    }

    /** 综合评判口径不做缓存（输入含检索 chunks 组合空间大，命中率低），直接透传 */
    @Override
    public cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict judge(
            String query, String standardAnswer, String actualAnswer, java.util.List<String> retrievedChunks) {
        return delegate.judge(query, standardAnswer, actualAnswer, retrievedChunks);
    }

    @Override
    public String complete(String prompt) {        String key = JudgeCacheKeys.cacheKey(prompt);
        try {
            String cached = cacheRepository.lookup(key);
            if (cached != null) {
                cacheRepository.incrementHit(key);
                log.debug("judge 缓存命中: key={}", key.substring(0, 8));
                return cached;
            }
        } catch (Exception e) {
            log.warn("judge 缓存读取失败（降级直调）: {}", e.getMessage());
        }
        String output = delegate.complete(prompt);
        if (output != null && !output.isBlank()) {
            try {
                cacheRepository.insert(key, output, null);
            } catch (Exception e) {
                log.warn("judge 缓存写入失败（忽略）: {}", e.getMessage());
            }
        }
        return output;
    }
}
