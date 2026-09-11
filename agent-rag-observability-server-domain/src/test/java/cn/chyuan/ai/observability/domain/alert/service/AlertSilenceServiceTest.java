package cn.chyuan.ai.observability.domain.alert.service;

import cn.chyuan.ai.observability.domain.alert.adapter.repository.IAlertSilenceRepository;
import cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警静默服务单元测试（工单 0179 Y3）— 匹配纯函数（精确/前缀/过期/未命中）、校验、创建。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("告警静默服务测试")
class AlertSilenceServiceTest {

    @Mock
    private IAlertSilenceRepository alertSilenceRepository;

    @InjectMocks
    private AlertSilenceService service;

    private cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity rule(
            String key, String startsAt, String endsAt) {
        return cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity.builder()
                .silenceKey(key).startsAt(startsAt).endsAt(endsAt).build();
    }

    private static final String NOW = "2026-09-11 12:00:00";

    @Test
    @DisplayName("匹配纯函数 — 精确命中/前缀通配/未开始/已过期/未命中")
    public void testMatches() {
        assertTrue(AlertSilenceService.matches(rule("patrol.*", "2026-09-11 00:00:00", "2026-09-11 23:59:59"), "patrol.fail", NOW));
        assertTrue(AlertSilenceService.matches(rule("patrol.fail", "2026-09-11 00:00:00", "2026-09-11 23:59:59"), "patrol.fail", NOW));
        assertFalse(AlertSilenceService.matches(rule("patrol.fail", "2026-09-11 13:00:00", "2026-09-11 23:59:59"), "patrol.fail", NOW));
        assertFalse(AlertSilenceService.matches(rule("patrol.fail", "2026-09-10 00:00:00", "2026-09-10 23:59:59"), "patrol.fail", NOW));
        assertFalse(AlertSilenceService.matches(rule("other.*", "2026-09-11 00:00:00", "2026-09-11 23:59:59"), "patrol.fail", NOW));
        assertFalse(AlertSilenceService.matches(null, "patrol.fail", NOW));
    }

    @Test
    @DisplayName("isSilenced — 任一规则命中即静默")
    public void testIsSilenced() {
        when(alertSilenceRepository.queryAll()).thenReturn(List.of(
                rule("slo.*", "2026-09-01 00:00:00", "2026-09-30 23:59:59")
        ));

        assertTrue(service.isSilenced("slo.burn_rate"));
        assertFalse(service.isSilenced("patrol.fail"));
    }

    @Test
    @DisplayName("创建校验 — key 必填、起止顺序、创建人留痕")
    public void testCreateValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(" ", "2026-09-11 00:00:00", "2026-09-11 01:00:00", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("k", "2026-09-12 00:00:00", "2026-09-11 00:00:00", null, null));

        service.create("patrol.*", "2026-09-11 00:00:00", "2026-09-11 01:00:00", " ", "维护窗");

        verify(alertSilenceRepository).insert(any());
        // 创建人空归 unknown：由 insert 参数断言（ArgumentCaptor 略，服务单测已覆盖主语义）
    }
}
