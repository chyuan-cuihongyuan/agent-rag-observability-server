package cn.chyuan.ai.observability.domain.catalogkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 反熵收敛（工单 1096 ES7，consul 思想）。
 * 目录与成员表比对/差异补齐收敛（缺增多删）/收敛幂等（二次收敛无差异）/
 * 收敛路径不触发 watch 通知（由调用方不碰阻塞查询保证）。
 */
public final class AntiEntropies {

    private AntiEntropies() {
    }

    /** 收敛：目录向成员表对齐，返回差异清单（+增 -删）；幂等 */
    public static List<String> converge(Catalogs catalog, List<String> memberPairs) {
        Set<String> members = new LinkedHashSet<>(memberPairs);
        Set<String> current = new LinkedHashSet<>(catalog.snapshot());
        List<String> diff = new ArrayList<>();
        for (String member : members) {
            if (!current.contains(member)) {
                String[] parts = member.split("/", 2);
                catalog.register(parts[0], parts[1]);
                diff.add("+" + member);
            }
        }
        for (String existing : current) {
            if (!members.contains(existing)) {
                String[] parts = existing.split("/", 2);
                catalog.deregisterService(parts[0], parts[1]);
                diff.add("-" + existing);
            }
        }
        return diff;
    }
}
