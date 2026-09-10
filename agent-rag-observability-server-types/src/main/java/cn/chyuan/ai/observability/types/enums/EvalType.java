package cn.chyuan.ai.observability.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测类型枚举 — 与 EvalExecutionService.computeOverall 引擎 switch 保持同步
 * （工单 0134：补 CONTEXT_QUALITY / TOOL_CALL，修复枚举与引擎不同步的已知缺陷）。
 * DB 存的是 code 字符串，新增枚举值无序列化兼容问题。
 */
@Getter
@AllArgsConstructor
public enum EvalType {
    RAG_RETRIEVAL("RAG_RETRIEVAL", "RAG检索评测"),
    ANSWER_QUALITY("ANSWER_QUALITY", "答案质量评测"),
    CONTEXT_QUALITY("CONTEXT_QUALITY", "上下文质量评测"),
    TOOL_CALL("TOOL_CALL", "工具调用评测"),
    AGENT_DECISION("AGENT_DECISION", "Agent决策评测");

    private final String code;
    private final String desc;
}
