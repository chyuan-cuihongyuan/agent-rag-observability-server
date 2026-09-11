package cn.chyuan.ai.observability.infrastructure.guard;

/**
 * Guard 拦截异常 — 借鉴 buzhou on_fail 词汇表中 REFRAIN（保守降级）档位的落地形态：
 * 输出被判定为密语泄漏/变体泄漏时不外流，由调用方捕获后走降级路径（默认分/降级文案），
 * 而不是把被污染的输出喂给下游解析。
 */
public class GuardInterceptedException extends RuntimeException {

    public GuardInterceptedException(String message) {
        super(message);
    }
}
