package cn.chyuan.ai.observability.domain.evaluate.adapter.port;

import cn.chyuan.ai.observability.domain.evaluate.model.valobj.JudgeVerdict;

import java.util.List;

/**
 * LLM-as-judge 端口 — 由 infrastructure 层用具体大模型（DeepSeek 等）实现。
 * domain 层只依赖此接口，不感知任何 AI 框架。
 */
public interface ILlmJudgePort {

    /**
     * 评判一次答案的质量。
     *
     * @param query           用户查询
     * @param standardAnswer  标准答案（可为空）
     * @param actualAnswer    实际答案
     * @param retrievedChunks 实际检索内容
     * @return 评判结果；实现不可用时应返回 degraded=true 的兜底结果而非抛异常
     */
    JudgeVerdict judge(String query, String standardAnswer, String actualAnswer, List<String> retrievedChunks);

    /** 当前是否具备真实 LLM 评判能力（false 表示仅降级） */
    boolean available();
}
