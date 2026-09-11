package cn.chyuan.ai.observability.domain.dlq.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 死信记录实体（工单 0180 Y4）— MQ 消费失败的消息留痕与重放。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeadLetterEntity {

    private Long id;
    /** 消息标签（decision/retrieval/chat_result/tool_call/memory_recall） */
    private String topicTag;
    /** 消息原文（重放输入） */
    private String payload;
    /** 重放次数 */
    private Integer retryCount;
    /** 最近一次失败原因 */
    private String lastError;
    /** 状态：PENDING 待重放 / REPLAYED 已成功重放 */
    private String status;
    private String createTime;
    private String updateTime;
}
