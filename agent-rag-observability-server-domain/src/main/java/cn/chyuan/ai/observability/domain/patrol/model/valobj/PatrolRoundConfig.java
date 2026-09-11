package cn.chyuan.ai.observability.domain.patrol.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一轮巡检的执行配置（工单 0137 S1）— 由触发方（定时调度器/手动端点）从配置面装配，
 * 领域服务不直接读配置，保证可测试性。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatrolRoundConfig {

    /** 单次拨测超时预算（毫秒）；超时按 TIMEOUT 记录，不再等待底层调用 */
    private long timeoutMs;

    /** 目标智能体 ID（空则由在线回放 provider 使用其默认 agent） */
    private String agentId;

    /** 固定拨测查询集（优先级高于 golden 池种子；为空时回退 datasetId） */
    private java.util.List<String> queries;

    /** golden 池数据集 ID（从其 itemsJson 抽取 query 作为拨测种子） */
    private String datasetId;
}
