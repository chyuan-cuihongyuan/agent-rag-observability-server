package cn.chyuan.ai.observability.domain.evaluate.model.valobj;

/**
 * pairwise 单题判定结果（工单 0170 X1，借鉴 Chatbot Arena 对比思想）。
 */
public enum PairwiseOutcome {

    A_WIN("A_WIN"),
    B_WIN("B_WIN"),
    TIE("TIE");

    private final String code;

    PairwiseOutcome(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PairwiseOutcome fromCode(String code) {
        for (PairwiseOutcome o : values()) {
            if (o.code.equals(code)) {
                return o;
            }
        }
        return null;
    }
}
