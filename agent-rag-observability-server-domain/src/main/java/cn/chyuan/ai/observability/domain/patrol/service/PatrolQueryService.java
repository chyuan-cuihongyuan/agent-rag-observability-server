package cn.chyuan.ai.observability.domain.patrol.service;

import cn.chyuan.ai.observability.domain.patrol.adapter.repository.IPatrolRecordRepository;
import cn.chyuan.ai.observability.domain.patrol.model.entity.PatrolRecordEntity;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolRoundSummary;
import cn.chyuan.ai.observability.domain.patrol.model.valobj.PatrolStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 巡检查询与汇总领域服务（工单 0137 S1）— 端点查询面：历史分页 + 最近一轮汇总。
 * 汇总逻辑为纯函数（由记录列表推导），与执行服务解耦。
 */
@Slf4j
@Service
public class PatrolQueryService {

    private final IPatrolRecordRepository patrolRecordRepository;

    public PatrolQueryService(IPatrolRecordRepository patrolRecordRepository) {
        this.patrolRecordRepository = patrolRecordRepository;
    }

    /** 拨测记录历史（分页） */
    public List<PatrolRecordEntity> queryRecords(int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        return patrolRecordRepository.queryList(p, s);
    }

    /**
     * 最近一轮汇总：取最近一条记录的 roundId，再拉同轮全部明细推导计数。
     * 无任何记录时返回 roundId=null 的空汇总（前端展示「从未巡检」）。
     */
    public PatrolRoundSummary queryLatestRound() {
        PatrolRecordEntity latest = patrolRecordRepository.queryLatest();
        if (latest == null) {
            return PatrolRoundSummary.builder()
                    .roundId(null).total(0).success(0).fail(0).timeout(0)
                    .avgScore(null).finishedAt(null).build();
        }
        List<PatrolRecordEntity> records = patrolRecordRepository.queryByRoundId(latest.getRoundId());
        return summarize(latest.getRoundId(), records);
    }

    /** 纯函数：由一轮明细推导汇总（时间窗语义以「该轮全部记录」为准，天然闭合） */
    public PatrolRoundSummary summarize(String roundId, List<PatrolRecordEntity> records) {
        int success = 0;
        int fail = 0;
        int timeout = 0;
        double scoreSum = 0.0;
        int scoreCount = 0;
        String finishedAt = null;
        for (PatrolRecordEntity r : records) {
            PatrolStatus st = r.getStatus() == null ? PatrolStatus.FAIL : r.getStatus();
            switch (st) {
                case SUCCESS -> success++;
                case TIMEOUT -> timeout++;
                default -> fail++;
            }
            if (r.getScore() != null) {
                scoreSum += r.getScore();
                scoreCount++;
            }
            if (finishedAt == null || (r.getCreateTime() != null && r.getCreateTime().compareTo(finishedAt) > 0)) {
                finishedAt = r.getCreateTime();
            }
        }
        return PatrolRoundSummary.builder()
                .roundId(roundId)
                .total(records.size())
                .success(success)
                .fail(fail)
                .timeout(timeout)
                .avgScore(scoreCount == 0 ? null : scoreSum / scoreCount)
                .finishedAt(finishedAt)
                .build();
    }
}
