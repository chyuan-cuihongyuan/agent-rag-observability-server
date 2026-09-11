package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.alert.service.ConfigDriftAuditor;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.GateDimensionKeys;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 门禁规则服务（工单 0136 R4）— 分层门禁配置的 CRUD 与校验：
 * <ul>
 *   <li>JSON 结构校验：safetyDims/scoreThresholds 必须是 {key: number} 对象，非法 JSON/非数值结构化拒绝</li>
 *   <li>维度存在性校验：safetyDims 的 key 必须是 Rubric 维度 key（GateDimensionKeys.DIMENSION_KEYS）；
 *       scoreThresholds 额外允许 overall/passRate 两个任务级汇总 key</li>
 *   <li>trials 校验：1-20（上限防误配导致 LLM judge 成本线性爆炸）</li>
 *   <li>门禁记录查询：最新/历史/按任务</li>
 * </ul>
 */
@Slf4j
@Service
public class GateService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** trials 上限（k 次 trial 的 LLM judge 调用与耗时随 k 线性放大，防止误配） */
    private static final int MAX_TRIALS = 20;

    private final IGateRepository gateRepository;
    private final IGateRecordRepository gateRecordRepository;
    private final ConfigDriftAuditor configDriftAuditor;

    public GateService(IGateRepository gateRepository, IGateRecordRepository gateRecordRepository,
                       ConfigDriftAuditor configDriftAuditor) {
        this.gateRepository = gateRepository;
        this.gateRecordRepository = gateRecordRepository;
        this.configDriftAuditor = configDriftAuditor;
    }

    // ===== CRUD =====

    /** 新建门禁：校验通过后落库；重名拒绝 */
    public GateEntity create(GateEntity entity) {
        if (entity == null || isBlank(entity.getName())) {
            throw new IllegalArgumentException("Gate name 不能为空");
        }
        Map<String, Double> safety = parseAndValidate(entity.getSafetyDimsJson(), true);
        Map<String, Double> thresholds = parseAndValidate(entity.getScoreThresholdsJson(), false);
        if (safety.isEmpty() && thresholds.isEmpty()) {
            throw new IllegalArgumentException("safetyDims 与 scoreThresholds 不能同时为空（门禁至少配置一条规则）");
        }
        validateTrials(entity.getTrials());
        if (gateRepository.queryByName(entity.getName()) != null) {
            throw new IllegalArgumentException("Gate 名称已存在: " + entity.getName());
        }
        entity.setGateId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entity.setSafetyDims(safety);
        entity.setScoreThresholds(thresholds);
        entity.setSafetyDimsJson(safety.isEmpty() ? null : JSON.toJSONString(safety));
        entity.setScoreThresholdsJson(thresholds.isEmpty() ? null : JSON.toJSONString(thresholds));
        entity.setTrials(entity.getTrials() == null ? 1 : entity.getTrials());
        entity.setEnabled(entity.getEnabled() == null || entity.getEnabled());
        String now = LocalDateTime.now().format(FMT);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        gateRepository.insert(entity);
        return entity;
    }

    /** 更新门禁：校验同新建 */
    public void update(GateEntity entity) {
        if (entity == null || isBlank(entity.getGateId())) {
            throw new IllegalArgumentException("gateId 不能为空");
        }
        GateEntity existing = gateRepository.queryByGateId(entity.getGateId());
        if (existing == null) {
            throw new IllegalArgumentException("Gate 不存在: " + entity.getGateId());
        }
        Map<String, Double> safety = parseAndValidate(
                isBlank(entity.getSafetyDimsJson()) ? existing.getSafetyDimsJson() : entity.getSafetyDimsJson(), true);
        Map<String, Double> thresholds = parseAndValidate(
                isBlank(entity.getScoreThresholdsJson()) ? existing.getScoreThresholdsJson() : entity.getScoreThresholdsJson(), false);
        if (safety.isEmpty() && thresholds.isEmpty()) {
            throw new IllegalArgumentException("safetyDims 与 scoreThresholds 不能同时为空（门禁至少配置一条规则）");
        }
        validateTrials(entity.getTrials());
        // name 变更时查重
        String newName = isBlank(entity.getName()) ? existing.getName() : entity.getName();
        if (!newName.equals(existing.getName())) {
            GateEntity byName = gateRepository.queryByName(newName);
            if (byName != null && !byName.getGateId().equals(existing.getGateId())) {
                throw new IllegalArgumentException("Gate 名称已存在: " + newName);
            }
        }
        entity.setName(newName);
        entity.setSafetyDims(safety);
        entity.setScoreThresholds(thresholds);
        entity.setSafetyDimsJson(safety.isEmpty() ? null : JSON.toJSONString(safety));
        entity.setScoreThresholdsJson(thresholds.isEmpty() ? null : JSON.toJSONString(thresholds));
        entity.setTrials(entity.getTrials() == null ? existing.getTrials() : entity.getTrials());
        entity.setEnabled(entity.getEnabled() == null ? existing.getEnabled() : entity.getEnabled());
        entity.setUpdateTime(LocalDateTime.now().format(FMT));
        // 配置漂移审计（工单 0182 Y6）：更新前后快照 diff 留痕（审计失败不阻断业务）
        configDriftAuditor.record("eval_gate", entity.getGateId(), snapshot(existing), snapshot(entity), "gate-update");
        gateRepository.update(entity);
    }

    /** 配置快照（脱敏由审计器负责） */
    private Map<String, String> snapshot(GateEntity e) {
        Map<String, String> snap = new LinkedHashMap<>();
        snap.put("name", e.getName());
        snap.put("safetyDimsJson", e.getSafetyDimsJson());
        snap.put("scoreThresholdsJson", e.getScoreThresholdsJson());
        snap.put("trials", String.valueOf(e.getTrials()));
        snap.put("enabled", String.valueOf(e.getEnabled()));
        return snap;
    }

    /** 删除门禁：以停用代替删除（历史门禁记录仍可追溯） */
    public void delete(String gateId) {
        GateEntity existing = gateRepository.queryByGateId(gateId);
        if (existing == null) {
            throw new IllegalArgumentException("Gate 不存在: " + gateId);
        }
        existing.setEnabled(false);
        existing.setUpdateTime(LocalDateTime.now().format(FMT));
        gateRepository.update(existing);
    }

    public GateEntity query(String gateId) {
        return gateRepository.queryByGateId(gateId);
    }

    public List<GateEntity> queryList(int page, int size) {
        return gateRepository.queryList(page, size);
    }

    // ===== 校验 =====

    /**
     * 解析并校验规则 JSON 对象：
     * <ul>
     *   <li>空/空白 → 空表（该层规则不配置）</li>
     *   <li>非法 JSON / 非对象 / value 非数值或不在 [0,1] → 结构化拒绝</li>
     *   <li>维度存在性：safetyDims 的 key ∈ Rubric 维度 key；
     *       scoreThresholds 额外允许 overall/passRate</li>
     * </ul>
     */
    public Map<String, Double> parseAndValidate(String json, boolean safety) {
        Map<String, Double> parsed = new LinkedHashMap<>();
        if (isBlank(json)) {
            return parsed;
        }
        Map<String, Object> raw;
        try {
            raw = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException((safety ? "safetyDims" : "scoreThresholds") + " 非法 JSON: " + e.getMessage());
        }
        if (raw == null) {
            return parsed;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (safety) {
                if (!GateDimensionKeys.isDimensionKey(key)) {
                    throw new IllegalArgumentException("safetyDims 维度不存在: " + key
                            + "（须为 Rubric 维度 key，如 hallucination/faithfulness/f1）");
                }
            } else {
                if (!GateDimensionKeys.isDimensionKey(key) && !GateDimensionKeys.isSummaryKey(key)) {
                    throw new IllegalArgumentException("scoreThresholds 指标不存在: " + key
                            + "（须为 Rubric 维度 key 或任务级汇总 key overall/passRate）");
                }
            }
            Object value = entry.getValue();
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException((safety ? "safetyDims" : "scoreThresholds")
                        + "[" + key + "] 阈值必须是数值: " + value);
            }
            double v = number.doubleValue();
            if (v < 0 || v > 1) {
                throw new IllegalArgumentException((safety ? "safetyDims" : "scoreThresholds")
                        + "[" + key + "] 阈值非法（需 0-1）: " + v);
            }
            parsed.put(key, v);
        }
        return parsed;
    }

    private void validateTrials(Integer trials) {
        if (trials != null && (trials < 1 || trials > MAX_TRIALS)) {
            throw new IllegalArgumentException("trials 非法（需 1-" + MAX_TRIALS + "，LLM judge 成本随 k 线性放大）: " + trials);
        }
    }

    // ===== 门禁记录查询 =====

    public GateRecordEntity queryLatestRecord(String gateId) {
        return gateRecordRepository.queryLatestByGateId(gateId);
    }

    public List<GateRecordEntity> queryRecordList(String gateId, int page, int size) {
        return gateRecordRepository.queryList(gateId, page, size);
    }

    public GateRecordEntity queryRecordByTaskId(String taskId) {
        return gateRecordRepository.queryByTaskId(taskId);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
