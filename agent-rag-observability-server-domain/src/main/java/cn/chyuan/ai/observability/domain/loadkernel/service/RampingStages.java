package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.ArrayList;
import java.util.List;

/**
 * ramping-vus 阶段序列（工单 1145 EY3，k6 思想）。
 * 阶段 start→target 序列/虚拟时钟按阶段时长推进/非单调允许但降容不执行/空阶段拒绝。
 */
public final class RampingStages {

    /** 阶段：时长 + 目标 VU */
    public record Stage(int durationTicks, int targetVus) {
    }

    private final List<Stage> stages = new ArrayList<>();

    /** 追加阶段：时长与目标非负、时长为正；空阶段（零时长）拒绝 */
    public void add(int durationTicks, int targetVus) {
        if (durationTicks <= 0 || targetVus < 0) {
            throw new IllegalArgumentException("阶段时长须为正且目标非负: " + durationTicks + "/" + targetVus);
        }
        stages.add(new Stage(durationTicks, targetVus));
    }

    public List<Stage> stages() {
        return List.copyOf(stages);
    }

    /** 某 tick 的目标 VU：虚拟时钟按阶段累计时长推进定位 */
    public int targetAt(long tick) {
        long cursor = 0;
        for (Stage stage : stages) {
            cursor += stage.durationTicks();
            if (tick < cursor) {
                return stage.targetVus();
            }
        }
        return stages.get(stages.size() - 1).targetVus();
    }

    /** 全程迭代数：Σ 每 tick 活跃 VU（每 VU 每 tick 一次迭代；降容 tick 起新目标生效，被降 VU 不再执行） */
    public long totalIterations(VirtualUsers pool, int durationTicks) {
        long total = 0;
        for (long tick = 0; tick < durationTicks; tick++) {
            pool.scaleTo(targetAt(tick));
            total += pool.activeCount();
        }
        return total;
    }
}
