package cn.chyuan.ai.observability.domain.pipekernel.service;

import java.util.HashSet;
import java.util.Set;

/**
 * ack 水位（工单 0981 EF6，vector 思想）。
 * sink 确认推进/至少一次不丢/乱序确认取最小连续。
 */
public final class AckWatermark {

    private long base = 0;
    private final Set<Long> ahead = new HashSet<>();

    /** 确认一个序号：连续前缀推进，乱序暂存 */
    public void ack(long seq) {
        if (seq <= base || !ahead.add(seq)) {
            throw new IllegalArgumentException("重复或已过时确认: " + seq);
        }
        while (ahead.remove(base + 1)) {
            base++;
        }
    }

    /** 水位：最小连续已确认前缀 */
    public long watermark() {
        return base;
    }

    /** 暂存确认数（乱序未成连续部分） */
    public int aheadCount() {
        return ahead.size();
    }
}
