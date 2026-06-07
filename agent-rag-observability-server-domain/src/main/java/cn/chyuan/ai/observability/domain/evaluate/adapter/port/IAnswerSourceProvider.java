package cn.chyuan.ai.observability.domain.evaluate.adapter.port;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.AnswerSample;

/**
 * 评测答案来源端口 — 给定一条评测查询，产出被打分的实际答案与检索内容。
 * 由 infrastructure 提供两种实现：在线回放上游 Agent / 离线复用已采集 Trace；通过配置路由选择。
 */
public interface IAnswerSourceProvider {

    /**
     * 获取一条查询对应的实际答案样本。
     *
     * @param query   评测查询
     * @param agentId 目标智能体（在线回放时可用，离线可为空）
     * @return 答案样本；无法获取时返回 null，调用方按失败处理
     */
    AnswerSample fetch(String query, String agentId);
}
