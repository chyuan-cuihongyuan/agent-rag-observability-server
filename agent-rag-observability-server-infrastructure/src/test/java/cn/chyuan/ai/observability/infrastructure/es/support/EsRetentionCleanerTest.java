package cn.chyuan.ai.observability.infrastructure.es.support;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ES 保留期判定纯函数（SELFLOOP3 loop-327，工单 0452/0453）
 */
class EsRetentionCleanerTest {

    private static final YearMonth CUTOFF = YearMonth.of(2025, 9);

    @Test
    void monthSuffixIsParsed() {
        assertThat(EsRetentionCleaner.monthOf("chat_result_log-2026.09")).isEqualTo(YearMonth.of(2026, 9));
        assertThat(EsRetentionCleaner.monthOf("tool_call_log-2025.01")).isEqualTo(YearMonth.of(2025, 1));
    }

    @Test
    void strictlyBeforeCutoffIsExpired() {
        assertThat(EsRetentionCleaner.isExpired("chat_result_log-2025.08", CUTOFF)).isTrue();
        // 同月边界：cutoff 月本身保留（严格早于才删）
        assertThat(EsRetentionCleaner.isExpired("chat_result_log-2025.09", CUTOFF)).isFalse();
        assertThat(EsRetentionCleaner.isExpired("chat_result_log-2025.10", CUTOFF)).isFalse();
    }

    @Test
    void nonDateOrForeignIndexIsSkipped() {
        assertThat(EsRetentionCleaner.monthOf("chat_result_log")).isNull();
        assertThat(EsRetentionCleaner.monthOf("chat_result_log-2026.9")).isNull(); // 非两位月
        assertThat(EsRetentionCleaner.monthOf("other_index-2020.01")).isNull(); // 非清理域前缀
        assertThat(EsRetentionCleaner.monthOf(null)).isNull();
        assertThat(EsRetentionCleaner.isExpired("other_index-2020.01", CUTOFF)).isFalse();
    }

    @Test
    void invalidMonthValueIsSkipped() {
        assertThat(EsRetentionCleaner.monthOf("chat_result_log-2026.13")).isNull();
    }
}
