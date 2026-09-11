package cn.chyuan.ai.observability.domain.dlq.service;

import cn.chyuan.ai.observability.domain.dlq.adapter.repository.IDeadLetterRepository;
import cn.chyuan.ai.observability.domain.dlq.model.entity.DeadLetterEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 死信记录与重放服务（工单 0180 Y4，借鉴 RocketMQ/Kafka DLQ 生态）—
 * 消费失败消息留痕；重放=按 tag 分派回处理端口，成功删记录、失败计数+1。
 */
@Slf4j
@Service
public class DeadLetterService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IDeadLetterRepository deadLetterRepository;
    private final DeadLetterHandler deadLetterHandler;

    public DeadLetterService(IDeadLetterRepository deadLetterRepository, DeadLetterHandler deadLetterHandler) {
        this.deadLetterRepository = deadLetterRepository;
        this.deadLetterHandler = deadLetterHandler;
    }

    /** 重放处理端口（infrastructure 按tag分派回采集链路） */
    public interface DeadLetterHandler {
        boolean handle(String topicTag, String payload);
    }

    /** 记录消费失败消息（payload 截断 8KB 防巨行） */
    public void record(String topicTag, String payload, String error) {
        try {
            deadLetterRepository.insert(DeadLetterEntity.builder()
                    .topicTag(topicTag)
                    .payload(truncate(payload, 8192))
                    .retryCount(0)
                    .lastError(truncate(error, 480))
                    .status("PENDING")
                    .createTime(FMT.format(LocalDateTime.now()))
                    .updateTime(FMT.format(LocalDateTime.now()))
                    .build());
        } catch (Exception e) {
            log.warn("死信落库失败（不阻断消费链）: {}", e.getMessage());
        }
    }

    /** 重放：成功删除记录，失败计数+1 留痕 */
    public Map<String, Object> replay(long id) {
        DeadLetterEntity entity = deadLetterRepository.queryById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity == null) {
            result.put("replayed", false);
            result.put("error", "死信记录不存在");
            return result;
        }
        boolean ok;
        try {
            ok = deadLetterHandler.handle(entity.getTopicTag(), entity.getPayload());
        } catch (Exception e) {
            ok = false;
            deadLetterRepository.markRetryFailed(id, e.getMessage());
            result.put("replayed", false);
            result.put("error", e.getMessage());
            return result;
        }
        if (ok) {
            deadLetterRepository.delete(id);
            result.put("replayed", true);
        } else {
            deadLetterRepository.markRetryFailed(id, "handle 返回失败");
            result.put("replayed", false);
        }
        return result;
    }

    /** 统计：PENDING 数与总重放次数 */
    public Map<String, Object> stats() {
        List<DeadLetterEntity> pending = deadLetterRepository.queryList("PENDING", 1, 1);
        List<DeadLetterEntity> all = deadLetterRepository.queryList(null, 1, 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingPage", pending);
        result.put("totalTracked", all.size());
        return result;
    }

    public List<DeadLetterEntity> list(String status, int page, int size) {
        return deadLetterRepository.queryList(status, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
