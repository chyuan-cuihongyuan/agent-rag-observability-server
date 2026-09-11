package cn.chyuan.ai.observability.domain.mining.model.valobj;

/**
 * Case 候选来源（工单 0138 S2）— 挖掘枢纽的三来源：
 * <ul>
 *   <li>EVAL_LOW_SCORE — 低分评测结果（eval_result 明细 overall_score 低于阈值）</li>
 *   <li>TRACE_FAIL — 失败/超时线上链路（chat_result_log FAIL/TIMEOUT）</li>
 *   <li>PATROL_FAIL — 巡检失败信号（patrol_record 非 SUCCESS，依赖 0137 表结构）</li>
 * </ul>
 */
public enum CaseSource {

    EVAL_LOW_SCORE("EVAL_LOW_SCORE"),
    TRACE_FAIL("TRACE_FAIL"),
    PATROL_FAIL("PATROL_FAIL");

    private final String code;

    CaseSource(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CaseSource fromCode(String code) {
        for (CaseSource s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
