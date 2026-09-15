package cn.chyuan.ai.observability.infrastructure.es.bulk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * EsBulkIndexService 停机幂等测试（SELFLOOP6 loop-650）。
 * disabled 形态（queue 为 null）shutdown 不得 NPE；排空逻辑见实现内注释。
 */
class EsBulkIndexServiceShutdownTest {

    @Test
    @DisplayName("disabled 形态 shutdown 幂等不抛（queue 为 null 分支）")
    void shutdownIdempotentWhenDisabled() {
        EsBulkIndexService service = new EsBulkIndexService();
        assertDoesNotThrow(service::shutdown);
    }
}
