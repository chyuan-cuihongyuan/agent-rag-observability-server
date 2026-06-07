package cn.chyuan.ai.observability.infrastructure.adapter.eval;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 答案来源 Provider 路由 — 根据配置选择在线回放或离线复用。
 * 实现时优先使用在线回放，确保评测能执行；离线作为可选降级。
 * 标记 @Primary 使其成为 EvalExecutionService 注入的默认实现。
 */
@Component
@Primary
public class AnswerSourceProviderRouter implements IAnswerSourceProvider {

    @Value("${observability.eval.provider.online.enabled:true}")
    private boolean onlineEnabled;

    @Resource(name = "onlineReplayAnswerProvider")
    private IAnswerSourceProvider onlineProvider;

    @Resource(name = "offlineTraceAnswerProvider")
    private IAnswerSourceProvider offlineProvider;

    @Override
    public AnswerSample fetch(String query, String agentId) {
        if (onlineEnabled) {
            AnswerSample sample = onlineProvider.fetch(query, agentId);
            if (sample != null) {
                return sample;
            }
        }
        // 降级到离线复用
        return offlineProvider.fetch(query, agentId);
    }
}
