package cn.chyuan.ai.observability.infrastructure.adapter.repository;

import cn.chyuan.ai.observability.domain.insight.service.RetentionService;
import cn.chyuan.ai.observability.infrastructure.dao.mapper.RetentionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 保留清理执行器（工单 0152 U6）— 桥接 domain 端口与 MyBatis mapper（databaseId 分叉在 XML）。
 */
@Slf4j
@Component
public class RetentionExecutorAdapter implements RetentionService.RetentionExecutor {

    @Resource
    private RetentionMapper retentionMapper;

    @Override
    public int purgeBatch(String table, String beforeTime, int batchSize) {
        return retentionMapper.purgeBatch(table, beforeTime, batchSize);
    }

    @Override
    public long countPurge(String table, String beforeTime) {
        return retentionMapper.countPurge(table, beforeTime);
    }
}
