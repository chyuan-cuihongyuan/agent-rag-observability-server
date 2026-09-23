package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布健康（工单 0700 CE6，sentry 思想）。
 * release 维度错误计数/新版本对前版回归判定（计数比阈值）/版本回归清单。
 */
public final class ReleaseHealth {

    public record ReleaseCount(String release, long count) {
    }

    public record Regression(String release, long current, long previous, double ratio, double threshold) {
    }

    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();

    /** 记录 release 维度事件 */
    public void record(String release, long atMs) {
        if (release == null || release.isBlank()) {
            throw new IllegalArgumentException("release 不得为空");
        }
        counts.merge(release, 1L, Long::sum);
        if (!order.contains(release)) {
            order.add(release);
        }
    }

    public List<ReleaseCount> countsByRecent() {
        List<ReleaseCount> out = new ArrayList<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            String release = order.get(i);
            out.add(new ReleaseCount(release, counts.get(release)));
        }
        return out;
    }

    /**
     * 回归判定：最近 release 对其前一版的计数比 ≥ threshold（前版计数为 0 时
     * 视为已回归）。返回回归清单（含比率）。
     */
    public List<Regression> regressions(double threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("回归阈值必须为正");
        }
        List<Regression> out = new ArrayList<>();
        List<ReleaseCount> recent = countsByRecent();
        for (int i = 0; i + 1 < recent.size(); i++) {
            ReleaseCount current = recent.get(i);
            ReleaseCount previous = recent.get(i + 1);
            double ratio = previous.count() == 0
                    ? Double.POSITIVE_INFINITY
                    : (double) current.count() / previous.count();
            if (ratio >= threshold) {
                out.add(new Regression(current.release(), current.count(), previous.count(), ratio, threshold));
            }
        }
        return out;
    }
}
