package cn.chyuan.ai.observability.domain.insight.service;

import cn.chyuan.ai.observability.domain.insight.adapter.repository.ITraceAnnotationRepository;
import cn.chyuan.ai.observability.domain.insight.model.entity.TraceAnnotationEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 人工评分注解服务（工单 0150 U4）— 评分离散校验（1-5）、操作留痕、upsert 重评即改判。
 */
@Slf4j
@Service
public class TraceAnnotationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ITraceAnnotationRepository traceAnnotationRepository;

    public TraceAnnotationService(ITraceAnnotationRepository traceAnnotationRepository) {
        this.traceAnnotationRepository = traceAnnotationRepository;
    }

    /**
     * 保存注解：score 必须为 1-5 整数（IllegalArgumentException 拒绝越界/空），
     * operator 空归 "unknown"；同 trace+operator 覆盖更新（重评即改判）。
     */
    public TraceAnnotationEntity save(String traceId, Integer score, String note, String operator) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        if (score == null || score < 1 || score > 5) {
            throw new IllegalArgumentException("score 必须为 1-5 整数: " + score);
        }
        String now = FMT.format(LocalDateTime.now());
        String op = operator == null || operator.isBlank() ? "unknown" : operator.trim();
        TraceAnnotationEntity existing = traceAnnotationRepository.queryByTraceAndOperator(traceId.trim(), op);
        TraceAnnotationEntity entity = TraceAnnotationEntity.builder()
                .id(existing == null ? null : existing.getId())
                .traceId(traceId.trim())
                .score(score)
                .note(note)
                .operator(op)
                .createTime(existing == null ? now : existing.getCreateTime())
                .updateTime(now)
                .build();
        traceAnnotationRepository.upsert(entity);
        return entity;
    }

    public TraceAnnotationEntity queryByTraceAndOperator(String traceId, String operator) {
        return traceAnnotationRepository.queryByTraceAndOperator(traceId, operator);
    }

    /** 注解列表：score 传 null 不过滤；page/size 钳制 */
    public List<TraceAnnotationEntity> queryList(Integer score, int page, int size) {
        if (score != null && (score < 1 || score > 5)) {
            throw new IllegalArgumentException("score 筛选必须为 1-5 整数: " + score);
        }
        return traceAnnotationRepository.queryList(score, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }
}
