package cn.chyuan.ai.observability.api.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionTraceDTO implements Serializable {
    private String sessionId;
    private String ownerUserId;
    private String tenantId;
    private Integer traceCount;
    private String lastTraceTime;
}
