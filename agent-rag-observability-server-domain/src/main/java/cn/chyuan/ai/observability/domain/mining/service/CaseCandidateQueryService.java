package cn.chyuan.ai.observability.domain.mining.service;

import cn.chyuan.ai.observability.domain.mining.adapter.repository.ICaseCandidateRepository;
import cn.chyuan.ai.observability.domain.mining.model.entity.CaseCandidateEntity;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseSource;
import cn.chyuan.ai.observability.domain.mining.model.valobj.CaseStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Case 候选查询服务（工单 0138 S2）— 端点查询面：来源/状态过滤 + 分页钳制。
 */
@Service
public class CaseCandidateQueryService {

    private final ICaseCandidateRepository caseCandidateRepository;

    public CaseCandidateQueryService(ICaseCandidateRepository caseCandidateRepository) {
        this.caseCandidateRepository = caseCandidateRepository;
    }

    /** 候选列表：source/status 传 null 不过滤；page/size 钳制 [1,100] */
    public List<CaseCandidateEntity> queryList(CaseSource source, CaseStatus status, int page, int size) {
        return caseCandidateRepository.queryList(source, status, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }
}
