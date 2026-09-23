package cn.chyuan.ai.observability.domain.regexkernel.service;

/**
 * 编译期与运行期安全（工单 0640 BX7，ripgrep 线性保证思想）。
 * 模式长度上限/NFA 指令数上限/重复展开计数上限（{m,n} 展开爆炸拒绝）/
 * 运行期步数上限熔断（无回溯引擎本质线性，上限防指令级膨胀）。
 */
public final class RegexSafety {

    private RegexSafety() {
    }

    /** 模式长度上限 */
    public static final int MAX_PATTERN_LENGTH = 4096;
    /** NFA 指令数上限 */
    public static final int MAX_INSTRUCTIONS = 100_000;
    /** 单个重复上界 */
    public static final int MAX_REPEAT = 1000;
    /** 运行期步数上限（Pike VM 线程步进计数） */
    public static final long MAX_STEPS = 20_000_000L;

    /** 编译期规模校验 */
    public static void checkProgramSize(int instructionCount) {
        if (instructionCount > MAX_INSTRUCTIONS) {
            throw new IllegalArgumentException("NFA 状态数超限：" + instructionCount
                    + " > " + MAX_INSTRUCTIONS + "（模式拒绝）");
        }
    }

    /** 运行期步数超限 */
    public static RuntimeException stepLimitExceeded(long steps) {
        return new IllegalArgumentException("匹配步数超限：" + steps + " > " + MAX_STEPS);
    }
}
