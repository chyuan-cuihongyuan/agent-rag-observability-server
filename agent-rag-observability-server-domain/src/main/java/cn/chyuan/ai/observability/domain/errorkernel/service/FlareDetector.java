package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * flare 突增检测（工单 0699 CE5，sentry 思想）。
 * 滑动窗口计数与基线均值+k 倍阈值/flare 事件产出（组/窗口/计数）/
 * 静默期抑制重复告警。
 */
public final class FlareDetector {

    public record Flare(String groupKey, long windowStartMs, long windowEndMs, long count) {
    }

    private final long windowMs;
    private final int baselineWindows;
    private final double k;
    private final long silenceMs;
    private final Map<String, Map<Long, Long>> windowCounts = new HashMap<>();
    private final Map<String, Long> lastAlertMs = new HashMap<>();

    public FlareDetector(long windowMs, int baselineWindows, double k, long silenceMs) {
        if (windowMs <= 0 || baselineWindows <= 0 || k <= 0 || silenceMs < 0) {
            throw new IllegalArgumentException("flare 参数非法");
        }
        this.windowMs = windowMs;
        this.baselineWindows = baselineWindows;
        this.k = k;
        this.silenceMs = silenceMs;
    }

    private long windowIndex(long atMs) {
        return Math.floorDiv(atMs, windowMs);
    }

    /** 记录事件（组键+时间），返回所属窗口起点 */
    public long record(String groupKey, long atMs) {
        long index = windowIndex(atMs);
        windowCounts.computeIfAbsent(groupKey, key -> new HashMap<>()).merge(index, 1L, Long::sum);
        return index * windowMs;
    }

    /**
     * 检测：当前窗口计数对比前 baselineWindows 个窗口（含空窗计 0）的
     * 均值与标准差；count &gt; 均值 + k×标准差 且距上次告警超过静默期 → flare。
     */
    public List<Flare> detect(String groupKey, long nowMs) {
        Map<Long, Long> counts = windowCounts.get(groupKey);
        List<Flare> out = new ArrayList<>();
        if (counts == null || counts.isEmpty()) {
            return out;
        }
        long currentIndex = windowIndex(nowMs);
        long currentCount = counts.getOrDefault(currentIndex, 0L);
        if (currentCount == 0) {
            return out;
        }
        double sum = 0;
        double sumSq = 0;
        for (int i = 1; i <= baselineWindows; i++) {
            long c = counts.getOrDefault(currentIndex - i, 0L);
            sum += c;
            sumSq += (double) c * c;
        }
        double mean = sum / baselineWindows;
        double stddev = Math.sqrt(Math.max(0, sumSq / baselineWindows - mean * mean));
        if (currentCount > mean + k * stddev) {
            Long last = lastAlertMs.get(groupKey);
            if (last == null || nowMs - last >= silenceMs) {
                lastAlertMs.put(groupKey, nowMs);
                out.add(new Flare(groupKey, currentIndex * windowMs, (currentIndex + 1) * windowMs, currentCount));
            }
        }
        return out;
    }
}
