package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalGateMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalGatePO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门禁规则仓储实现（工单 0136 R4）— PO/实体互转；
 * safetyDims/scoreThresholds JSON 原文落库，读侧惰性解析（解析失败仅告警置空，门控从严由判定层兜底）。
 */
@Slf4j
@Repository
public class GateRepository implements IGateRepository {

    @Resource
    private EvalGateMapper evalGateMapper;

    @Override
    public void insert(GateEntity entity) {
        evalGateMapper.insert(toPo(entity));
    }

    @Override
    public GateEntity queryByGateId(String gateId) {
        return toEntity(evalGateMapper.selectByGateId(gateId));
    }

    @Override
    public GateEntity queryByName(String name) {
        return toEntity(evalGateMapper.selectByName(name));
    }

    @Override
    public List<GateEntity> queryList(int page, int size) {
        return evalGateMapper.selectList((page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean update(GateEntity entity) {
        return evalGateMapper.update(toPo(entity)) > 0;
    }

    private EvalGatePO toPo(GateEntity e) {
        // JSON 原文优先；缺失时由已解析表序列化兜底
        String safetyJson = e.getSafetyDimsJson() != null ? e.getSafetyDimsJson()
                : (e.getSafetyDims() == null || e.getSafetyDims().isEmpty() ? null : JSON.toJSONString(e.getSafetyDims()));
        String scoreJson = e.getScoreThresholdsJson() != null ? e.getScoreThresholdsJson()
                : (e.getScoreThresholds() == null || e.getScoreThresholds().isEmpty() ? null : JSON.toJSONString(e.getScoreThresholds()));
        return EvalGatePO.builder()
                .id(e.getId())
                .gateId(e.getGateId())
                .name(e.getName())
                .safetyDims(safetyJson)
                .scoreThresholds(scoreJson)
                .trials(e.getTrials() == null ? 1 : e.getTrials())
                .enabled(Boolean.FALSE.equals(e.getEnabled()) ? 0 : 1)
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    private GateEntity toEntity(EvalGatePO po) {
        if (po == null) {
            return null;
        }
        return GateEntity.builder()
                .id(po.getId())
                .gateId(po.getGateId())
                .name(po.getName())
                .safetyDimsJson(po.getSafetyDims())
                .scoreThresholdsJson(po.getScoreThresholds())
                .safetyDims(parse(po.getSafetyDims()))
                .scoreThresholds(parse(po.getScoreThresholds()))
                .trials(po.getTrials() == null ? 1 : po.getTrials())
                .enabled(po.getEnabled() == null || po.getEnabled() == 1)
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private Map<String, Double> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("解析门禁规则 JSON 失败（视为未配置）: {} - {}", json, e.getMessage());
            return null;
        }
    }
}
