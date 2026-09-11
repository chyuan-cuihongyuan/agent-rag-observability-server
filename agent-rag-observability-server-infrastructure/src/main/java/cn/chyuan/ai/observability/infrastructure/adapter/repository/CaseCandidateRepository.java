package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.mining.adapter.repository.ICaseCandidateRepository;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseAttribution;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.CaseCandidateMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.CaseCandidatePO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Case 候选仓储实现（工单 0138 S2）— PO/实体互转；幂等预检 + DDL 唯一键双层防重。
 */
@Slf4j
@Repository
public class CaseCandidateRepository implements ICaseCandidateRepository {

    @Resource
    private CaseCandidateMapper caseCandidateMapper;

    @Override
    public boolean existsBySourceRef(CaseSource source, String sourceRef) {
        return caseCandidateMapper.selectBySourceRef(source.getCode(), sourceRef) != null;
    }

    @Override
    public void insert(CaseCandidateEntity entity) {
        caseCandidateMapper.insert(toPo(entity));
    }

    @Override
    public List<CaseCandidateEntity> queryList(CaseSource source, CaseStatus status, int page, int size) {
        return caseCandidateMapper.selectList(
                        source == null ? null : source.getCode(),
                        status == null ? null : status.getCode(),
                        (page - 1) * size, size)
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<CaseCandidateEntity> queryByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return caseCandidateMapper.selectByIds(ids).stream()
                .map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public int updateStatus(List<Long> ids, CaseStatus status, String promotedDatasetId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return caseCandidateMapper.updateStatus(ids, status.getCode(), promotedDatasetId);
    }

    @Override
    public boolean updateAttribution(long id, CaseAttribution attribution, String note, String by, String at) {
        return caseCandidateMapper.updateAttribution(id, attribution.getCode(), note, by, at) > 0;
    }

    @Override
    public List<CaseCandidateEntity> queryAttributed(String startTime, String endTime, CaseSource source, int limit) {
        return caseCandidateMapper.selectAttributed(startTime, endTime,
                        source == null ? null : source.getCode(), Math.min(Math.max(limit, 1), 5000))
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    private CaseCandidatePO toPo(CaseCandidateEntity e) {
        return CaseCandidatePO.builder()
                .id(e.getId())
                .source(e.getSource() == null ? null : e.getSource().getCode())
                .sourceRef(e.getSourceRef())
                .traceId(e.getTraceId())
                .query(e.getQuery())
                .answerSummary(e.getAnswerSummary())
                .hitDocCount(e.getHitDocCount())
                .toolList(e.getToolList())
                .reason(e.getReason())
                .status(e.getStatus() == null ? null : e.getStatus().getCode())
                .promotedDatasetId(e.getPromotedDatasetId())
                .createTime(e.getCreateTime())
                .attribution(e.getAttribution() == null ? null : e.getAttribution().getCode())
                .attributionNote(e.getAttributionNote())
                .attributionBy(e.getAttributionBy())
                .attributionAt(e.getAttributionAt())
                .build();
    }

    private CaseCandidateEntity toEntity(CaseCandidatePO po) {
        if (po == null) {
            return null;
        }
        return CaseCandidateEntity.builder()
                .id(po.getId())
                .source(CaseSource.fromCode(po.getSource()))
                .sourceRef(po.getSourceRef())
                .traceId(po.getTraceId())
                .query(po.getQuery())
                .answerSummary(po.getAnswerSummary())
                .hitDocCount(po.getHitDocCount())
                .toolList(po.getToolList())
                .reason(po.getReason())
                .status(CaseStatus.fromCode(po.getStatus()))
                .promotedDatasetId(po.getPromotedDatasetId())
                .createTime(po.getCreateTime())
                .attribution(CaseAttribution.fromCode(po.getAttribution()))
                .attributionNote(po.getAttributionNote())
                .attributionBy(po.getAttributionBy())
                .attributionAt(po.getAttributionAt())
                .build();
    }
}
