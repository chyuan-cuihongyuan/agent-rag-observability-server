package cn.chyuan.ai.observability.api.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceQueryDTO {
    private String tenantId;
    private String ownerUserId;
    private String sessionId;
    private String agentId;
    private String branchType;
    private String agentStatus;
    private String sourceService;
    private String startTime;
    private String endTime;
    private Integer page;
    private Integer size;
    /** 游标分页游标（SELFLOOP3 loop-344）：上一页 nextCursor，首页不传 */
    private String cursor;
}
