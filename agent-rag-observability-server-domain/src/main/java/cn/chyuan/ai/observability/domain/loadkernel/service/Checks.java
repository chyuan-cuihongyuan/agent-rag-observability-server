package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * check 检查项（工单 1148 EY6，k6 思想）。
 * check 通过失败计数/通过率统计/按 tag 聚合/check 不影响迭代继续。
 */
public final class Checks {

    /** 计数：通过 + 失败 */
    static final class Tally {
        long pass;
        long fail;

        long total() {
            return pass + fail;
        }

        double rate() {
            return total() == 0 ? 0 : (double) pass / total();
        }
    }

    /** key：检查名 ⊕ tag */
    private final Map<String, Tally> tallies = new LinkedHashMap<>();

    private static String key(String name, String tag) {
        return name + "@" + (tag == null ? "" : tag);
    }

    /** 记一次 check：ok 与否均计数，不影响调用方流程 */
    public void record(String name, String tag, boolean ok) {
        Tally tally = tallies.computeIfAbsent(key(name, tag), k -> new Tally());
        if (ok) {
            tally.pass++;
        } else {
            tally.fail++;
        }
    }

    /** 通过率（按检查名聚合全 tag） */
    public double rate(String name) {
        long pass = 0;
        long total = 0;
        for (Map.Entry<String, Tally> entry : tallies.entrySet()) {
            if (entry.getKey().startsWith(name + "@")) {
                pass += entry.getValue().pass;
                total += entry.getValue().total();
            }
        }
        return total == 0 ? 0 : (double) pass / total;
    }

    /** 按 tag 通过率 */
    public double rateByTag(String name, String tag) {
        Tally tally = tallies.get(key(name, tag));
        return tally == null ? 0 : tally.rate();
    }

    public int distinctKeys() {
        return tallies.size();
    }
}
