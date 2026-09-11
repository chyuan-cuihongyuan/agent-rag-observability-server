package cn.chyuan.ai.observability.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * judge 判定缓存表 PO（工单 0176 X7）— uk_judge_key (cache_key)。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeCachePO implements Serializable {
    private Long id;
    private String cacheKey;
    private String output;
    private String rubricId;
    private Long hitCount;
    private String updateTime;
}
