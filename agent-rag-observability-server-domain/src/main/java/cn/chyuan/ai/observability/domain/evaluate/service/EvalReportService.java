package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IGateRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.GateRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalTaskEntity;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalTaskRepository;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 评测报告生成服务（工单 0175 X6）— markdown 报告拼装：
 * 任务对比 + 门禁最新判定 + 归因分布 + 巡检最近轮，小节开关可配（缺省全开）。
 * 拼装为纯函数（输入各小节的数据，输出 markdown），空数据小节优雅降级为「暂无」。
 */
@Slf4j
@Service
public class EvalReportService {

    private final IEvalTaskRepository evalTaskRepository;
    private final IEvalResultRepository evalResultRepository;
    private final IGateRecordRepository gateRecordRepository;
    private final PatrolSummaryProvider patrolSummaryProvider;

    public EvalReportService(IEvalTaskRepository evalTaskRepository,
                             IEvalResultRepository evalResultRepository,
                             IGateRecordRepository gateRecordRepository,
                             PatrolSummaryProvider patrolSummaryProvider) {
        this.evalTaskRepository = evalTaskRepository;
        this.evalResultRepository = evalResultRepository;
        this.gateRecordRepository = gateRecordRepository;
        this.patrolSummaryProvider = patrolSummaryProvider;
    }

    /** 报告小节开关 */
    public record Sections(boolean task, boolean gate, boolean attribution, boolean patrol) {
        public static Sections all() {
            return new Sections(true, true, true, true);
        }
    }

    /**
     * 拼装报告（纯函数核心）：各小节输入由触发方装配；null 小节数据输出「暂无」。
     */
    public String build(String title, String taskSection, String gateSection,
                        String attributionSection, String patrolSection, Sections sections) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title == null ? "评测报告" : title).append("\n\n");
        if (sections.task()) {
            md.append("## 1. 任务对比\n\n").append(orNone(taskSection)).append("\n");
        }
        if (sections.gate()) {
            md.append("## 2. 门禁结论\n\n").append(orNone(gateSection)).append("\n");
        }
        if (sections.attribution()) {
            md.append("## 3. 归因分布\n\n").append(orNone(attributionSection)).append("\n");
        }
        if (sections.patrol()) {
            md.append("## 4. 巡检状态\n\n").append(orNone(patrolSection)).append("\n");
        }
        return md.toString();
    }

    /** 表格行转义：竖线与换行（markdown 表格语义） */
    public static String mdCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    /**
     * 装配并生成完整报告（端点入口）：task + gate 最新判定 + 归因 + 巡检。
     */
    public String generate(String taskId, String gateId, Sections sections) {
        String taskSection = null;
        if (taskId != null && !taskId.isBlank()) {
            EvalTaskEntity task = evalTaskRepository.queryByTaskId(taskId);
            if (task != null) {
                var results = evalResultRepository.queryByTaskId(taskId, null, 1, 1000);
                double avg = results.stream()
                        .filter(r -> r.getOverallScore() != null)
                        .mapToDouble(EvalResultEntity::getOverallScore).average().orElse(0.0);
                taskSection = "| 任务 | 状态 | 样本数 | 平均分 |\n|---|---|---|---|\n| "
                        + mdCell(task.getTaskName()) + " | " + mdCell(task.getStatus()) + " | "
                        + results.size() + " | " + String.format("%.4f", avg) + " |";
            }
        }
        String gateSection = null;
        if (gateId != null && !gateId.isBlank()) {
            GateRecordEntity latest = gateRecordRepository.queryLatestByGateId(gateId);
            if (latest != null) {
                gateSection = "结论 **" + mdCell(latest.getResult()) + "**（门禁 " + mdCell(latest.getGateId())
                        + "，任务 " + mdCell(latest.getTaskId()) + "，" + mdCell(latest.getCreateTime()) + "）";
            }
        }
        return build("评测报告" + (taskId == null ? "" : " - " + taskId),
                taskSection, gateSection,
                sections.attribution() ? patrolSummaryProvider.attributionSummary() : null,
                sections.patrol() ? patrolSummaryProvider.patrolSummary() : null,
                sections);
    }

    private String orNone(String section) {
        return section == null || section.isBlank() ? "暂无数据" : section;
    }
}
