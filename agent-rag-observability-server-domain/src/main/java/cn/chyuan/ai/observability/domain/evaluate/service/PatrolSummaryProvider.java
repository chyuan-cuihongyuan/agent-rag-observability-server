package cn.chyuan.ai.observability.domain.evaluate.service;

/**
 * 报告摘要提供者端口（工单 0175 X6）— 巡检/归因小节的文本装配桥，
 * 由 mining 域实现（避免 evaluate 直接依赖 patrol/mining 内部结构）。
 */
public interface PatrolSummaryProvider {

    /** 巡检最近一轮摘要（markdown 一行；从未巡检返回 null） */
    String patrolSummary();

    /** 归因四层分布摘要（markdown 一行；无标注返回 null） */
    String attributionSummary();
}
