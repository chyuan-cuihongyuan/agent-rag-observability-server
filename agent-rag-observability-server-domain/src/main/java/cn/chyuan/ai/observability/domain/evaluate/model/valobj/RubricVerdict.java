package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

/**
 * Rubric 维度二元断言判定（工单 0133 R1）。
 * <ul>
 *   <li>PASS：断言通过，计 1 分</li>
 *   <li>FAIL：断言不通过，计 0 分</li>
 *   <li>UNKNOWN：无法判定（LLM 输出非法 / 缺证据 / 评判不可用）——「标准不清晰」本身可度量</li>
 * </ul>
 */
public enum RubricVerdict {
    PASS(1),
    FAIL(0),
    UNKNOWN(-1);

    private final int code;

    RubricVerdict(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
