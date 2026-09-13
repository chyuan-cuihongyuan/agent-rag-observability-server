package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.lineage.service.LineageEventService;
import cn.chyuan.ai.observability.domain.lineage.service.LineageEventService.LineageRun;
import cn.chyuan.ai.observability.domain.lineage.service.LineageGraphOps.LineageEdge;
import cn.chyuan.ai.observability.infrastructure.dao.ILineageDao;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 血缘运行与边仓储实现（工单 0287 AK3 / 0286 AK2）：实现 {@link LineageEventService.LineageRunStore} 端口，
 * 经 MyBatis 落 lineage_run / lineage_edge 表（双方言公共子集 SQL）。
 *
 * @author chyuan
 */
@Repository
public class LineageRunRepository implements LineageEventService.LineageRunStore {

    @Resource
    private ILineageDao dao;

    @Override
    public boolean insertRunIfAbsent(LineageRun run) {
        if (dao.selectRunByEventKey(run.eventKey()) != null) {
            return false;
        }
        dao.insertRun(run.eventKey(), run.eventType(), run.jobUrn(),
                toJson(run.inputUrns()), toJson(run.outputUrns()), run.atMs(), run.error());
        return true;
    }

    @Override
    public int addEdgeIfAbsent(String fromUrn, String toUrn, String source) {
        if (dao.selectEdge(fromUrn, toUrn, source) != null) {
            return 0;
        }
        dao.insertEdge(fromUrn, toUrn, source);
        return 1;
    }

    @Override
    public List<LineageEdge> listEdges() {
        return dao.selectEdges().stream().map(LineageRunRepository::toEdge).toList();
    }

    /** URN 集合 → JSON 数组文本（urn 由 AssetUrn 校验保证安全字符集） */
    private static String toJson(Set<String> urns) {
        return urns.stream()
                .map(urn -> "\"" + urn.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static LineageEdge toEdge(Map<String, Object> row) {
        return new LineageEdge(((Number) row.get("id")).longValue(), (String) row.get("fromUrn"),
                (String) row.get("toUrn"), (String) row.get("source"));
    }
}
