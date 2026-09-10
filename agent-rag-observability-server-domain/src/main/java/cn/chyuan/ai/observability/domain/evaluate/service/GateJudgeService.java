package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.TaskEvalSummary;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 门禁判定服务（工单 0136 R4）— 回测任务完成后的门禁判定与落账。
 * <p>
 * 挂接方式（Resolution 裁定）：不引入轮询/消息，直接在 {@link EvalExecutionService}
 * 执行完成（COMPLETED/FAILED 置位）处回调本服务——回测任务（eval_task.gate_id 非空）
 * 创建后立即返回 taskId，门禁判定随任务完成同步发生，结论落 eval_gate_record，
 * CI/CD 通过「查任务状态 + 查门禁记录」两跳拿到结论。该方式最小侵入（执行链只加一处
 * 回调）且天然可测（mock 仓储验证判定→落账）。
 * <ul>
 *   <li>任务 COMPLETED：汇总 → GateDecisionEngine.judge → 落记录（PASS/BLOCK）</li>
 *   <li>任务 FAILED：summary 传 null → 引擎按 BLOCK 处理（明细注明任务失败，防止回测挂掉静默放行）</li>
 *   <li>gate 不存在或已停用：跳过判定（不落记录），只告警日志——停用门禁等价于解绑</li>
 *   <li>判定/落账异常：吞掉并告警（不回滚任务状态），记录缺失即「未判定」，CI 侧可见</li>
 * </ul>
 */
@Slf4j
@Service
public class GateJudgeService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IGateRepository gateRepository;
    private final IGateRecordRepository gateRecordRepository;
    private final GateDecisionEngine decisionEngine;

    public GateJudgeService(IGateRepository gateRepository,
                            IGateRecordRepository gateRecordRepository,
                            GateDecisionEngine decisionEngine) {
        this.gateRepository = gateRepository;
        this.gateRecordRepository = gateRecordRepository;
        this.decisionEngine = decisionEngine;
    }

    /**
     * 任务完成后判定并落账。
     *
     * @param task    评测任务（gateId 非空才调用）
     * @param summary 任务级汇总；任务 FAILED 时传 null（引擎按 BLOCK 处理）
     */
    public void judgeAndRecord(EvalTaskEntity task, TaskEvalSummary summary) {
        if (task == null || task.getGateId() == null || task.getGateId().isBlank()) {
            return;
        }
        try {
            GateEntity gate = gateRepository.queryByGateId(task.getGateId());
            if (gate == null) {
                log.warn("门禁判定跳过：gate 不存在, taskId={}, gateId={}", task.getTaskId(), task.getGateId());
                return;
            }
            if (Boolean.FALSE.equals(gate.getEnabled())) {
                log.info("门禁判定跳过：gate 已停用, taskId={}, gateId={}", task.getTaskId(), gate.getGateId());
                return;
            }
            GateDecisionEngine.GateDecision decision = decisionEngine.judge(summary, gate);
            GateRecordEntity record = GateRecordEntity.builder()
                    .recordId(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .gateId(gate.getGateId())
                    .taskId(task.getTaskId())
                    .result(decision.result())
                    .triggerDetail(JSON.toJSONString(decision.triggers()))
                    .createTime(LocalDateTime.now().format(FMT))
                    .build();
            gateRecordRepository.insert(record);
            log.info("门禁判定完成, taskId={}, gateId={}, 结论={}, 触发规则数={}",
                    task.getTaskId(), gate.getGateId(), decision.result(), decision.triggers().size());
        } catch (Exception e) {
            // 判定/落账异常不回滚任务状态；记录缺失即「未判定」，CI 查询侧可见
            log.error("门禁判定失败, taskId={}, gateId={}", task.getTaskId(), task.getGateId(), e);
        }
    }
}
