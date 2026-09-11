package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IJudgeCacheRepository;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.JudgeCacheMapper;
import cn.chyuan.ai.observability.infrastructure.dao.po.JudgeCachePO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * judge 判定缓存仓储实现（工单 0176 X7）。
 */
@Slf4j
@Repository
public class JudgeCacheRepository implements IJudgeCacheRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private JudgeCacheMapper judgeCacheMapper;

    @Override
    public String lookup(String cacheKey) {
        JudgeCachePO po = judgeCacheMapper.selectByKey(cacheKey);
        return po == null ? null : po.getOutput();
    }

    @Override
    public void insert(String cacheKey, String output, String rubricId) {
        judgeCacheMapper.upsert(JudgeCachePO.builder()
                .cacheKey(cacheKey).output(output).rubricId(rubricId)
                .hitCount(0L)
                .updateTime(FMT.format(LocalDateTime.now()))
                .build());
    }

    @Override
    public void incrementHit(String cacheKey) {
        judgeCacheMapper.incrementHit(cacheKey);
    }

    @Override
    public int clearByRubric(String rubricId) {
        return judgeCacheMapper.clearByRubric(rubricId);
    }
}
