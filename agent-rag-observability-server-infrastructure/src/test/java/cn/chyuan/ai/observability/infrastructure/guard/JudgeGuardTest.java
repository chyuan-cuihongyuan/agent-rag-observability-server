package cn.chyuan.ai.observability.infrastructure.guard;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;
import cn.chyuan.ai.observability.infrastructure.adapter.llm.LlmJudgeAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Judge 链路守卫样例测试 — 借鉴 buzhou-guard InjectionDefenseUnitTest 的断言形态，
 * 纯 JUnit5 + Mockito + AssertJ，不起 Spring 上下文。
 */
class JudgeGuardTest {

    private static final String FIXED_CANARY = "OBS-CANARY-a1b2c3d4e5f6";
    private static final String FIXED_TAG = "ab12cd34";

    private JudgeGuard fixedGuard() {
        return new JudgeGuard(FIXED_CANARY, FIXED_TAG);
    }

    @Test
    @DisplayName("spotlight 包裹含定界符、横幅与交织标记，去标记后无损还原")
    void spotlight_wrapsWithDelimiterBannerAndDatamarking() {
        JudgeGuard guard = fixedGuard();
        String wrapped = guard.spotlight("订单查询返回：CPU 使用率 92%");

        assertThat(wrapped)
                .contains("<<<OBS-DATA-" + FIXED_TAG + "-BEGIN>>>")
                .contains("<<<OBS-DATA-" + FIXED_TAG + "-END>>>")
                .contains(Spotlighting.BANNER)
                .contains(String.valueOf(Spotlighting.MARK_CHAR));

        // 去除交织标记后应能无损还原数据正文（截取 BEGIN/BEND 之间的部分）
        int begin = wrapped.indexOf(">>>") + 3;
        int end = wrapped.lastIndexOf("<<<OBS-DATA-" + FIXED_TAG + "-END>>>");
        String body = wrapped.substring(begin, end).replaceFirst("^\\n", "").replaceFirst("\\n$", "");
        int bannerEnd = body.indexOf('\n') + 1;
        String marked = body.substring(bannerEnd);
        assertThat(Spotlighting.stripMark(marked)).isEqualTo("订单查询返回：CPU 使用率 92%");
    }

    @Test
    @DisplayName("beforeModel 在 prompt 头部注入安全密语指令")
    void beforeModel_injectsCanaryInstruction() {
        JudgeGuard guard = fixedGuard();
        String augmented = guard.beforeModel("请评估以下答案…");

        assertThat(augmented)
                .startsWith("[安全密语机制]")
                .contains(FIXED_CANARY)
                .endsWith("请评估以下答案…");
    }

    @Test
    @DisplayName("输出泄漏密语原文：拦截替换、记入拒识记忆、发出 guard.canary.leaked 事件")
    void canaryLeak_isInterceptedAndRecorded() {
        JudgeGuard guard = fixedGuard();
        JudgeGuard.GuardResult result = guard.afterModel("评分 0.8，密语：" + FIXED_CANARY);

        assertThat(result.passed()).isFalse();
        assertThat(result.value()).contains("已拦截");
        assertThat(guard.events()).containsExactly("guard.canary.leaked");
        assertThat(guard.rejectedSamples()).hasSize(1);
    }

    @Test
    @DisplayName("输出泄漏密语变形（插空格/标点/大小写变化）：同样被拦截，发出 variant.blocked 事件")
    void canaryVariant_isBlockedViaSkeletonMatching() {
        JudgeGuard guard = fixedGuard();
        String variant = "O B S - C a n a r y - A1B2C3D4E5F6";
        JudgeGuard.GuardResult result = guard.afterModel("泄露变形：" + variant);

        assertThat(result.passed()).isFalse();
        assertThat(guard.events()).containsExactly("guard.canary.variant.blocked");
    }

    @Test
    @DisplayName("干净输出原样透传，不产生任何 guard 事件")
    void cleanOutput_passesThrough() {
        JudgeGuard guard = fixedGuard();
        JudgeGuard.GuardResult result = guard.afterModel("0.8");

        assertThat(result.passed()).isTrue();
        assertThat(result.value()).isEqualTo("0.8");
        assertThat(guard.events()).isEmpty();
        assertThat(guard.rejectedSamples()).isEmpty();
    }

    @Test
    @DisplayName("接入真实调用链：发给模型的 prompt 含密语指令与 spotlight 包裹")
    void judgeCall_promptIsGuardedOnRealPath() {
        JudgeGuard guard = fixedGuard();
        ChatModel chatModel = mockModelReturning("0.8");
        LlmJudgeAdapter adapter = wiredAdapter(chatModel, guard);

        JudgeVerdict verdict = adapter.judge("如何扩容", "标准答案", "实际答案", List.of("chunk-1"));

        assertThat(verdict.isDegraded()).isFalse();
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.atLeastOnce()).call(captor.capture());
        String sentPrompt = captor.getValue().getContents();
        assertThat(sentPrompt)
                .startsWith("[安全密语机制]")
                .contains("<<<OBS-DATA-" + FIXED_TAG + "-BEGIN>>>")
                .contains(Spotlighting.BANNER);
    }

    @Test
    @DisplayName("泄漏输出触发 REFRAIN 降级：评分回落默认分，不解析被污染输出")
    void leakedOutput_degradesToDefaultScore() {
        JudgeGuard guard = fixedGuard();
        ChatModel chatModel = mockModelReturning("泄漏：" + FIXED_CANARY);
        LlmJudgeAdapter adapter = wiredAdapter(chatModel, guard);

        JudgeVerdict verdict = adapter.judge("如何扩容", "标准答案", "实际答案", List.of("chunk-1"));

        // callLLMForScore 捕获 GuardInterceptedException 后返回默认分 0.5
        assertThat(verdict.getFaithfulness()).isEqualTo(0.5);
        assertThat(guard.events()).contains("guard.canary.leaked");
    }

    @Test
    @DisplayName("guard 关闭（null）时行为与原路径一致：无守卫痕迹、评分正常解析")
    void guardDisabled_behavesLikeOriginalPath() {
        ChatModel chatModel = mockModelReturning("0.8");
        LlmJudgeAdapter adapter = wiredAdapter(chatModel, null);

        JudgeVerdict verdict = adapter.judge("如何扩容", "标准答案", "实际答案", List.of("chunk-1"));

        assertThat(verdict.isDegraded()).isFalse();
        assertThat(verdict.getFaithfulness()).isEqualTo(0.8);
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.atLeastOnce()).call(captor.capture());
        assertThat(captor.getValue().getContents())
                .doesNotContain("[安全密语机制]")
                .doesNotContain("<<<OBS-DATA-");
    }

    private LlmJudgeAdapter wiredAdapter(ChatModel chatModel, JudgeGuard guard) {
        LlmJudgeAdapter adapter = new LlmJudgeAdapter();
        ReflectionTestUtils.setField(adapter, "chatModel", chatModel);
        ReflectionTestUtils.setField(adapter, "enabled", true);
        ReflectionTestUtils.setField(adapter, "judgeGuard", guard);
        return adapter;
    }

    private ChatModel mockModelReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(model.call(any(Prompt.class))).thenReturn(response);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        return model;
    }
}
