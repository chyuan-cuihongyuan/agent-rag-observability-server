package cn.chyuan.ai.observability.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BranchType {
    RAG("RAG", "RAG检索"),
    DIRECT_ANSWER("DIRECT_ANSWER", "直接回答"),
    TOOL_CALL("TOOL_CALL", "工具调用"),
    REJECT("REJECT", "拒绝回答");

    private final String code;
    private final String desc;
}
