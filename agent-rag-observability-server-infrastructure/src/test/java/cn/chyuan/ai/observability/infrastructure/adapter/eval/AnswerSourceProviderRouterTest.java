package cn.chyuan.ai.observability.infrastructure.adapter.eval;

import cn.chyuan.ai.observability.domain.evaluate.adapter.port.IAnswerSourceProvider;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnswerSourceProviderRouter 路由契约测试（工单 1140）：
 * 在线回放优先、在线未命中或关闭时降级离线复用。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerSourceProviderRouter 路由契约")
class AnswerSourceProviderRouterTest {

    @Mock
    private IAnswerSourceProvider onlineProvider;

    @Mock
    private IAnswerSourceProvider offlineProvider;

    private AnswerSourceProviderRouter router;

    @BeforeEach
    void setUp() {
        router = new AnswerSourceProviderRouter();
        ReflectionTestUtils.setField(router, "onlineProvider", onlineProvider);
        ReflectionTestUtils.setField(router, "offlineProvider", offlineProvider);
        ReflectionTestUtils.setField(router, "onlineEnabled", true);
    }

    private AnswerSample sampleOf(String answer) {
        AnswerSample sample = new AnswerSample();
        sample.setActualAnswer(answer);
        sample.setRetrievedChunks(List.of("chunk-1"));
        return sample;
    }

    @Test
    @DisplayName("在线开启且命中 → 直接返回在线结果")
    void onlineHitShortCircuits() {
        AnswerSample online = sampleOf("online answer");
        when(onlineProvider.fetch(anyString(), anyString())).thenReturn(online);

        AnswerSample out = router.fetch("query", "agent-1");

        assertThat(out).isSameAs(online);
        verify(offlineProvider, never()).fetch(anyString(), anyString());
    }

    @Test
    @DisplayName("在线开启但未命中 → 降级离线复用")
    void onlineMissFallsBackToOffline() {
        when(onlineProvider.fetch(anyString(), anyString())).thenReturn(null);
        AnswerSample offline = sampleOf("offline answer");
        when(offlineProvider.fetch(anyString(), anyString())).thenReturn(offline);

        AnswerSample out = router.fetch("query", "agent-1");

        assertThat(out).isSameAs(offline);
    }

    @Test
    @DisplayName("在线开关关闭 → 不触达在线 Provider")
    void onlineDisabledSkipsOnlineProvider() {
        ReflectionTestUtils.setField(router, "onlineEnabled", false);
        AnswerSample offline = sampleOf("offline answer");
        when(offlineProvider.fetch(anyString(), anyString())).thenReturn(offline);

        AnswerSample out = router.fetch("query", "agent-1");

        assertThat(out).isSameAs(offline);
        verify(onlineProvider, never()).fetch(anyString(), anyString());
    }
}
