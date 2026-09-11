package cn.chyuan.ai.observability.domain.observe.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话聚合视图（工单 0147 U1）— 把同一 sessionId 的多轮 trace 拼成一次会话的汇总形态。
 * 全部字段由 trace 列表推导（纯函数），空会话产出 totalCostMs=null 等「无信号」值而非造假默认。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionRollup {
    private String sessionId;
    /** 轮次数（trace 条数） */
    private int rounds;
    /** 首轮时间 */
    private String firstTime;
    /** 末轮时间 */
    private String lastTime;
    /** 总耗时（毫秒，各轮 costTimeMs 求和；全空则 null） */
    private Long totalCostMs;
    private int successCount;
    private int failCount;
    /** 涉及的 agentId 列表（去重，保序） */
    private List<String> agents;
    /** 每轮 traceId（按时间升序，重放会话顺序） */
    private List<String> traceIds;
}
