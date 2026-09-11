package cn.chyuan.ai.observability.infrastructure.config;

import cn.chyuan.ai.observability.infrastructure.guard.JudgeGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Judge 链路守卫装配 — 默认关闭，通过 observability.eval.guard.enabled 打开。
 * 关闭时 LlmJudgeAdapter 的 judgeGuard 为 null，行为与引入样例前完全一致。
 */
@Configuration
@ConditionalOnProperty(name = "observability.eval.guard.enabled", havingValue = "true")
public class JudgeGuardConfig {

    @Bean
    public JudgeGuard judgeGuard() {
        return new JudgeGuard();
    }
}
