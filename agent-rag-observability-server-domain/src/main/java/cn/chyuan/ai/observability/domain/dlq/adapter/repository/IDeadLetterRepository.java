package cn.chyuan.ai.observability.domain.dlq.adapter.repository;

import cn.chyuan.ai.observability.domain.dlq.model.entity.DeadLetterEntity;

import java.util.List;

/**
 * 死信记录仓储端口（工单 0180 Y4）。
 */
public interface IDeadLetterRepository {

    void insert(cn.chyuan.ai.observability.domain.dlq.model.entity.DeadLetterEntity entity);

    /** 列表：status 传 null 不过滤（按 create_time 降序分页） */
    List<DeadLetterEntity> queryList(String status, int page, int size);

    DeadLetterEntity queryById(long id);

    boolean delete(long id);

    /** 重放失败后：retry_count+1 并更新 last_error */
    boolean markRetryFailed(long id, String lastError);
}
