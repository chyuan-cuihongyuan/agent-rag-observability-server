package cn.chyuan.ai.observability.domain.mining.model.valobj;

/**
 * Case 归因四分层（工单 0139 S3，判定指引见 docs/02-agent-rag-observability-server/15）：
 * <ul>
 *   <li>PLANNING — 规划错：意图识别/分支选择/任务拆解走偏（模型决策面）</li>
 *   <li>TOOL — 工具错：调错工具/参数组装错/工具时序错（工具编排面）</li>
 *   <li>ENVIRONMENT — 环境错：上游超时/外部服务故障/数据源异常（非模型非代码）</li>
 *   <li>SKILL — 知识错：知识缺失/检索空/命中不相关（知识与检索面）</li>
 * </ul>
 */
public enum CaseAttribution {

    PLANNING("PLANNING"),
    TOOL("TOOL"),
    ENVIRONMENT("ENVIRONMENT"),
    SKILL("SKILL");

    private final String code;

    CaseAttribution(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 非法值返回 null（由服务层拒绝，不静默归类） */
    public static CaseAttribution fromCode(String code) {
        for (CaseAttribution a : values()) {
            if (a.code.equals(code)) {
                return a;
            }
        }
        return null;
    }
}
