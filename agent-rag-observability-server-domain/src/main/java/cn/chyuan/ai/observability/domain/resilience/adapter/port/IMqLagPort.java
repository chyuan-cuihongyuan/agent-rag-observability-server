package cn.chyuan.ai.observability.domain.resilience.adapter.port;

import cn.chyuan.ai.observability.domain.resilience.service.LagSnapshot;

import java.util.List;

/**
 * MQ 消费延迟端口（工单 0220 AD1）— 生产由 MQ 管理面适配；测试用 mock。
 *
 * @author chyuan
 */
public interface IMqLagPort {

    /** topic → 当前积压条数 */
    java.util.Map<String, Long> lagByTopic();
}
