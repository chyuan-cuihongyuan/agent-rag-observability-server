package cn.chyuan.ai.observability.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentStatus {
    SUCCESS("SUCCESS", "成功"),
    FAIL("FAIL", "失败"),
    LOOP("LOOP", "循环调用"),
    TIMEOUT("TIMEOUT", "超时");

    private final String code;
    private final String desc;
}
