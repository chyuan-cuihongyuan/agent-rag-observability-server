package cn.chyuan.ai.observability.domain.observe.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话概要（工单 0147 U1）— 近期会话列表行（sessionId + 轮次 + 末次时间）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionSummary {
    private String sessionId;
    /** 该会话的轮次（trace）数 */
    private long traceCount;
    /** 会话末次活动时间 */
    private String lastTime;
}
