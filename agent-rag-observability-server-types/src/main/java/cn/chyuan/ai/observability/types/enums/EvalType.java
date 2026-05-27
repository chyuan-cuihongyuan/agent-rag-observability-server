package cn.chyuan.ai.observability.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EvalType {
    RAG_RETRIEVAL("RAG_RETRIEVAL", "RAG检索评测"),
    ANSWER_QUALITY("ANSWER_QUALITY", "答案质量评测"),
    AGENT_DECISION("AGENT_DECISION", "Agent决策评测");

    private final String code;
    private final String desc;
}
