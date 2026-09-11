package cn.chyuan.ai.observability.domain.mining.model.valobj;

/**
 * Case 候选状态（工单 0138 S2）：PENDING 待处置 → PROMOTED 已回填错题集 / IGNORED 已忽略。
 */
public enum CaseStatus {

    PENDING("PENDING"),
    PROMOTED("PROMOTED"),
    IGNORED("IGNORED");

    private final String code;

    CaseStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CaseStatus fromCode(String code) {
        for (CaseStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return PENDING;
    }
}
