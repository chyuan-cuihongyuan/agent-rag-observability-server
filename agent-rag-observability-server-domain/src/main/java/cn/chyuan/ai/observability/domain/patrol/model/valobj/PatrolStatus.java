package cn.chyuan.ai.observability.domain.patrol.model.valobj;

/**
 * 巡检拨测单次结果三态（工单 0137 S1）：
 * <ul>
 *   <li>SUCCESS — 拨测调用成功返回答案</li>
 *   <li>FAIL — 调用失败/异常/返回不可用（provider 返回 null 或抛异常）</li>
 *   <li>TIMEOUT — 超出单次拨测超时预算（completion future 超时口径）</li>
 * </ul>
 */
public enum PatrolStatus {

    SUCCESS("SUCCESS"),
    FAIL("FAIL"),
    TIMEOUT("TIMEOUT");

    private final String code;

    PatrolStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 落库字符串反解；未知值按 FAIL 处理（从严口径，避免脏数据被当成功统计） */
    public static PatrolStatus fromCode(String code) {
        for (PatrolStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return FAIL;
    }
}
