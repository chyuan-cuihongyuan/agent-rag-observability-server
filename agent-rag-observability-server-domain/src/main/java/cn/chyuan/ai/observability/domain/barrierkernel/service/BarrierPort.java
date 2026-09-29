package cn.chyuan.ai.observability.domain.barrierkernel.service;

import java.util.List;
import java.util.Map;

import cn.chyuan.ai.observability.domain.barrierkernel.service.Barriers.Barrier;
import cn.chyuan.ai.observability.domain.barrierkernel.service.CheckpointMode.Mode;

/**
 * 检查点屏障端口（工单 1052 EN8，flink 思想）。
 * inject·align·snapshot 入口统一编排/与 pipekernel 事件流作事件形状只读联动（泛型形状串不 import）/
 * barrier-kernel.enabled 默认关（开启才改变行为）。
 */
public interface BarrierPort {

    /** 装配拓扑：通道数/注入间隔/对齐超时/模式（重复装配拒绝） */
    void configure(int channels, long intervalTicks, long timeoutTicks, Mode mode);

    /** 算子状态写入 */
    void putState(String operator, String key, Object value);

    /** 时钟步进：注入到点产出 barrier；超时判定失败释放缓冲 */
    List<Barrier> tick();

    /** barrier 到达通道：对齐齐触发快照并开启 ack；重复 id 拒绝 */
    boolean onBarrier(int channel, long checkpointId);

    /** 数据到达：exactly-once 对齐缓冲/at-least-once 直通 */
    boolean onData(int channel, String item);

    /** 快照落定后释放对齐缓冲 */
    List<String> releaseBuffered();

    /** 算子 ack：收齐完成登记快照 */
    boolean ack(long checkpointId, String operator);

    boolean completed(long checkpointId);

    /** 恢复最近完成快照；无快照拒绝；恢复后 checkpointId 递增 */
    long restore();

    long nextCheckpointId();

    /** 对齐缓冲积压量 */
    int buffered();

    /** pipekernel 事件流形态只读联动：事件形状串（形状数据不 import pipekernel） */
    String eventShape(String item);

    static BarrierPort inMemory() {
        return new CheckpointCoordinator();
    }
}
