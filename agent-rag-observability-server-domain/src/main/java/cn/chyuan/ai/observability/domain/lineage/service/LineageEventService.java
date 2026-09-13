package cn.chyuan.ai.observability.domain.lineage.service;

import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.LineageEdge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 血缘事件接入（工单 0287 AK3，借鉴 OpenLineage run event 模型）—
 * 作业 run 事件（RUN_START/RUN_COMPLETE/RUN_FAIL）→ lineage_run 落档；
 * RUN_COMPLETE 自动补 lineage_edge（auto 来源，幂等去重）；重复事件按 eventKey 幂等。
 * 存储经 {@link LineageRunStore} 端口（lineage_run 观测库第 29 表 + lineage_edge 第 28 表）。
 */
public class LineageEventService {

    public static final String EVENT_START = "RUN_START";
    public static final String EVENT_COMPLETE = "RUN_COMPLETE";
    public static final String EVENT_FAIL = "RUN_FAIL";

    /** 血缘运行事件（job URN + 输入输出资产 URN 集） */
    public record LineageRunEvent(String eventKey, String eventType, String jobUrn,
            Set<String> inputUrns, Set<String> outputUrns, long atMs, String error) {

        public LineageRunEvent {
            if (eventKey == null || eventKey.isBlank()) {
                throw new IllegalArgumentException("eventKey 不能为空（幂等键）");
            }
            if (!java.util.Set.of(EVENT_START, EVENT_COMPLETE, EVENT_FAIL).contains(eventType)) {
                throw new IllegalArgumentException("非法事件类型: " + eventType);
            }
            inputUrns = inputUrns == null ? Set.of() : Set.copyOf(inputUrns);
            outputUrns = outputUrns == null ? Set.of() : Set.copyOf(outputUrns);
        }
    }

    /** 血缘运行记录 */
    public record LineageRun(long id, String eventKey, String eventType, String jobUrn,
            Set<String> inputUrns, Set<String> outputUrns, long atMs, String error) {
    }

    /** 血缘运行与边存储端口（lineage_run 第 29 表 + lineage_edge 第 28 表） */
    public interface LineageRunStore {

        /** 事件键幂等：已存在返回 false 不落档 */
        boolean insertRunIfAbsent(LineageRun run);

        /** 自动补边（幂等：同 from+to+source=auto 已存在则跳过），返回实际新增边数 */
        int addEdgeIfAbsent(String fromUrn, String toUrn, String source);

        List<LineageEdge> listEdges();
    }

    private final LineageRunStore store;

    public LineageEventService(LineageRunStore store) {
        this.store = store;
    }

    /**
     * 事件处理：START/COMPLETE/FAIL 全部落档（幂等）；COMPLETE 自动补边
     * （每个 input→output 组合一条 auto 边）。返回是否首次处理（重复事件 false）。
     */
    public boolean ingest(LineageRunEvent event) {
        LineageRun run = new LineageRun(0, event.eventKey(), event.eventType(), event.jobUrn(),
                event.inputUrns(), event.outputUrns(), event.atMs(), event.error());
        if (!store.insertRunIfAbsent(run)) {
            return false;
        }
        if (EVENT_COMPLETE.equals(event.eventType())) {
            for (String input : event.inputUrns()) {
                for (String output : event.outputUrns()) {
                    store.addEdgeIfAbsent(input, output, "auto");
                }
            }
        }
        return true;
    }

    /** 当前血缘边集（查询端点与影响分析共用） */
    public List<LineageEdge> currentEdges() {
        return store.listEdges();
    }

    /** 事件输入输出规范化（容错：去空去重保序） */
    static Set<String> normalize(Set<String> urns) {
        Set<String> out = new LinkedHashSet<>();
        for (String urn : urns) {
            if (urn != null && !urn.isBlank()) {
                out.add(urn.trim());
            }
        }
        return out;
    }
}
