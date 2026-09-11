package cn.chyuan.ai.observability.domain.mining.adapter.repository;

import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;

import java.util.List;

/**
 * Case 候选仓储端口（工单 0138 S2）— 幂等键 (source, sourceRef) 防重复入池/重复回填。
 */
public interface ICaseCandidateRepository {

    /** 幂等判定：同 source+sourceRef 已存在即跳过（无论状态） */
    boolean existsBySourceRef(CaseSource source, String sourceRef);

    void insert(CaseCandidateEntity entity);

    /** 候选列表：source/status 传 null 表示不过滤（按 create_time 降序分页） */
    List<CaseCandidateEntity> queryList(CaseSource source, CaseStatus status, int page, int size);

    List<CaseCandidateEntity> queryByIds(List<Long> ids);

    /** 批量处置：仅 PENDING 生效（幂等，已处置的候选不被覆盖） */
    int updateStatus(List<Long> ids, CaseStatus status, String promotedDatasetId);
}
