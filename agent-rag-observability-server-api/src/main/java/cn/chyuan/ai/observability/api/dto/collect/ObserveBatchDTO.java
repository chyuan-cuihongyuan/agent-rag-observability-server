package cn.chyuan.ai.observability.api.dto.collect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ObserveBatchDTO {
    private AgentDecisionDTO agentDecision;
    private RagRetrievalDTO ragRetrieval;
    private ChatResultDTO chatResult;
}
