package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.EvalGateRecordMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.EvalGateRecordPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 门禁记录仓储实现（工单 0136 R4）— 判定结论落账与查询（最新/历史/按任务）。
 */
@Repository
public class GateRecordRepository implements IGateRecordRepository {

    @Resource
    private EvalGateRecordMapper evalGateRecordMapper;

    @Override
    public void insert(GateRecordEntity entity) {
        evalGateRecordMapper.insert(toPo(entity));
    }

    @Override
    public GateRecordEntity queryLatestByGateId(String gateId) {
        return toEntity(evalGateRecordMapper.selectLatestByGateId(gateId));
    }

    @Override
    public List<GateRecordEntity> queryList(String gateId, int page, int size) {
        return evalGateRecordMapper.selectList(gateId, (page - 1) * size, size).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public GateRecordEntity queryByTaskId(String taskId) {
        return toEntity(evalGateRecordMapper.selectByTaskId(taskId));
    }

    private EvalGateRecordPO toPo(GateRecordEntity e) {
        return EvalGateRecordPO.builder()
                .id(e.getId())
                .recordId(e.getRecordId())
                .gateId(e.getGateId())
                .taskId(e.getTaskId())
                .result(e.getResult())
                .triggerDetail(e.getTriggerDetail())
                .createTime(e.getCreateTime())
                .build();
    }

    private GateRecordEntity toEntity(EvalGateRecordPO po) {
        if (po == null) {
            return null;
        }
        return GateRecordEntity.builder()
                .id(po.getId())
                .recordId(po.getRecordId())
                .gateId(po.getGateId())
                .taskId(po.getTaskId())
                .result(po.getResult())
                .triggerDetail(po.getTriggerDetail())
                .createTime(po.getCreateTime())
                .build();
    }
}
