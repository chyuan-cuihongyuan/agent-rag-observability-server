package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IPairwiseRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.PairwiseRecordEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.PairwiseRecordMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.PairwiseRecordPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * pairwise 对局记录仓储实现（工单 0170 X1）。
 */
@Slf4j
@Repository
public class PairwiseRecordRepository implements IPairwiseRecordRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private PairwiseRecordMapper pairwiseRecordMapper;

    @Override
    public void insert(String taskA, String taskB, String datasetId, String query, String outcome, int pairNo) {
        pairwiseRecordMapper.insert(PairwiseRecordPO.builder()
                .taskA(taskA).taskB(taskB).datasetId(datasetId).query(query)
                .outcome(outcome).pairNo(pairNo)
                .createTime(FMT.format(LocalDateTime.now()))
                .build());
    }

    @Override
    public List<PairwiseRecordEntity> queryList(String taskA, String taskB, int limit) {
        return pairwiseRecordMapper.selectList(taskA, taskB, Math.min(Math.max(limit, 1), 5000)).stream()
                .<PairwiseRecordEntity>map(po -> PairwiseRecordEntity.builder()
                        .id(po.getId())
                        .taskA(po.getTaskA())
                        .taskB(po.getTaskB())
                        .datasetId(po.getDatasetId())
                        .query(po.getQuery())
                        .outcome(po.getOutcome())
                        .pairNo(po.getPairNo())
                        .createTime(po.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }
}
