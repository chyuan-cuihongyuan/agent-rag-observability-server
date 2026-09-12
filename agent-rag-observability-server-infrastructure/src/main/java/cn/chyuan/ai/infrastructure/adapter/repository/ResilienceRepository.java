package cn.chyuan.ai.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.resilience.adapter.port.IResilienceStore;
import cn.chyuan.ai.observability.domain.resilience.service.BackfillJob;
import cn.chyuan.ai.observability.domain.resilience.service.LagSnapshot;
import cn.chyuan.ai.observability.domain.resilience.service.QualityRule;
import cn.chyuan.ai.observability.domain.resilience.service.QualityRunner;
import cn.chyuan.ai.observability.domain.resilience.service.SlaMiss;
import cn.chyuan.ai.infrastructure.dao.IResilienceDao;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 调度韧性仓储实现（工单 0220-0223 AD 簇）：实现 {@link IResilienceStore} 端口，
 * 经 MyBatis 落 lag_snapshot/quality_rule/quality_result/backfill_job/sla_miss 五表
 * （第 22-26 表双方言 DDL；行以 map 承载，JSON 列存 params/样本/已完成分片）。
 *
 * @author chyuan
 */
@Repository
public class ResilienceRepository implements IResilienceStore {

    @Resource
    private IResilienceDao dao;

    @Override
    public void saveLag(LagSnapshot snapshot) {
        dao.insertLag(snapshot.topic(), snapshot.lag(), snapshot.level());
    }

    @Override
    public List<LagSnapshot> recentLags(int limit) {
        return dao.selectRecentLags(limit).stream().map(ResilienceRepository::toLag).toList();
    }

    private static LagSnapshot toLag(Map<String, Object> row) {
        return new LagSnapshot(str(row, "topic"), longOf(row, "lag"), str(row, "level"),
                timeOf(row, "sampledAt"));
    }

    @Override
    public void upsertRule(QualityRule rule) {
        String paramsJson = rule.params().isEmpty() ? null
                : com.alibaba.fastjson.JSON.toJSONString(rule.params());
        if (dao.selectQualityRule(rule.name()) == null) {
            dao.insertQualityRule(rule.name(), rule.target(), rule.field(), rule.type(),
                    paramsJson, rule.enabled());
        } else {
            dao.updateQualityRule(rule.name(), rule.target(), rule.field(), rule.type(),
                    paramsJson, rule.enabled());
        }
    }

    @Override
    public void deleteRule(String ruleName) {
        dao.deleteQualityRule(ruleName);
    }

    @Override
    public QualityRule findRule(String ruleName) {
        return toRule(dao.selectQualityRule(ruleName));
    }

    @Override
    public List<QualityRule> listRules() {
        return dao.selectQualityRules().stream().map(ResilienceRepository::toRule).toList();
    }

    private static QualityRule toRule(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Map<String, String> params = new java.util.LinkedHashMap<>();
        String paramsJson = str(row, "paramsJson");
        if (paramsJson != null && !paramsJson.isBlank()) {
            com.alibaba.fastjson.JSONObject parsed = com.alibaba.fastjson.JSON.parseObject(paramsJson);
            for (Map.Entry<String, Object> e : parsed.entrySet()) {
                params.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        boolean enabled = !"0".equals(String.valueOf(row.get("enabled")));
        Long id = row.get("id") == null ? null : Long.valueOf(String.valueOf(row.get("id")));
        return new QualityRule(id, str(row, "name"), str(row, "target"), str(row, "field"),
                str(row, "type"), params, enabled);
    }

    @Override
    public void saveResult(QualityRunner.QualityResult result) {
        dao.insertQualityResult(result.ruleName(), result.pass(), result.checked(),
                result.violated(), com.alibaba.fastjson.JSON.toJSONString(result.failureSamples()));
    }

    @Override
    public List<QualityRunner.QualityResult> recentResults(int limit) {
        return dao.selectRecentQualityResults(limit).stream().map(ResilienceRepository::toResult).toList();
    }

    private static QualityRunner.QualityResult toResult(Map<String, Object> row) {
        List<String> samples = new ArrayList<>();
        String samplesJson = str(row, "samplesJson");
        if (samplesJson != null && !samplesJson.isBlank()) {
            samples.addAll(com.alibaba.fastjson.JSON.parseArray(samplesJson, String.class));
        }
        return new QualityRunner.QualityResult(str(row, "ruleName"),
                !"0".equals(String.valueOf(row.get("pass"))),
                (int) longOf(row, "checked"), (int) longOf(row, "violated"),
                List.copyOf(samples), timeOf(row, "ranAt"));
    }

    @Override
    public void upsertBackfill(BackfillJob job) {
        String completedJson = job.completedShards().isEmpty() ? null
                : com.alibaba.fastjson.JSON.toJSONString(new ArrayList<>(job.completedShards()));
        if (dao.selectBackfill(job.id()) == null) {
            dao.insertBackfill(job.id(), job.name(), job.rangeStart(), job.rangeEnd(),
                    job.shardCount(), completedJson, job.status());
        } else {
            dao.updateBackfill(job.id(), completedJson, job.status());
        }
    }

    @Override
    public BackfillJob findBackfill(String id) {
        Map<String, Object> row = dao.selectBackfill(id);
        if (row == null) {
            return null;
        }
        TreeSet<Integer> completed = new TreeSet<>();
        String completedJson = str(row, "completedJson");
        if (completedJson != null && !completedJson.isBlank()) {
            List<Integer> indexes = com.alibaba.fastjson.JSON.parseArray(completedJson, Integer.class);
            completed.addAll(indexes);
        }
        long now = System.currentTimeMillis();
        return new BackfillJob(str(row, "jobId"), str(row, "name"),
                longOf(row, "rangeStart"), longOf(row, "rangeEnd"),
                (int) longOf(row, "shardCount"), completed, str(row, "status"), now, now);
    }

    @Override
    public void saveSlaMiss(SlaMiss miss) {
        dao.insertSlaMiss(miss.task(), miss.expectedMs(), miss.actualMs(), miss.overdueMs());
    }

    @Override
    public List<SlaMiss> recentSlaMisses(int limit) {
        return dao.selectRecentSlaMisses(limit).stream().map(row -> new SlaMiss(str(row, "task"),
                longOf(row, "expectedMs"), longOf(row, "actualMs"), longOf(row, "overdueMs"),
                timeOf(row, "detectedAt"))).toList();
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static long longOf(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number number) {
            return number.longValue();
        }
        return v == null ? 0 : Long.parseLong(String.valueOf(v));
    }

    private static long timeOf(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof java.util.Date date) {
            return date.getTime();
        }
        if (v instanceof Number number) {
            return number.longValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return java.sql.Timestamp.valueOf(String.valueOf(v)).getTime();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
