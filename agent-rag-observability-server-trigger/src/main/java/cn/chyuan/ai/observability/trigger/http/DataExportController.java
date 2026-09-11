package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalResultRepository;
import cn.chyuan.ai.observability.domain.insight.service.DataExportService;
import cn.chyuan.ai.observability.domain.observe.adapter.repository.IChatResultRepository;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据导出控制器（工单 0151 U5）— text/csv 直出：
 * GET /api/v1/export/chat_results?startTime=&endTime=（时间窗必填）、
 * GET /api/v1/export/eval_results?taskId=（按任务分页迭代取全量）。
 * 行数上限 export.max-rows（默认 10000）超限拒绝；导出动作记 EXPORT_DATA 日志标记。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/export")
public class DataExportController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IChatResultRepository chatResultRepository;
    private final IEvalResultRepository evalResultRepository;
    private final DataExportService dataExportService;

    public DataExportController(IChatResultRepository chatResultRepository,
                                IEvalResultRepository evalResultRepository,
                                DataExportService dataExportService) {
        this.chatResultRepository = chatResultRepository;
        this.evalResultRepository = evalResultRepository;
        this.dataExportService = dataExportService;
    }

    @GetMapping("/chat_results")
    public void exportChatResults(@RequestParam String startTime,
                                  @RequestParam String endTime,
                                  HttpServletResponse response) throws IOException {
        if (isBlank(startTime) || isBlank(endTime)) {
            writeError(response, "startTime/endTime 不能为空");
            return;
        }
        List<cn.chyuan.ai.observability.domain.observe.model.entity.ChatResultEntity> rows =
                chatResultRepository.queryForExport(startTime.trim(), endTime.trim(), dataExportService.maxRows());
        if (rows.size() >= dataExportService.maxRows()) {
            writeError(response, "导出行数达到上限 " + dataExportService.maxRows() + "，请缩小时间窗");
            return;
        }
        log.info("EXPORT_DATA type=chat_results rows={} window={}..{}", rows.size(), startTime, endTime);
        writeCsv(response, "chat_results", dataExportService.buildChatResultsCsv(rows));
    }

    @GetMapping("/eval_results")
    public void exportEvalResults(@RequestParam String taskId, HttpServletResponse response) throws IOException {
        if (isBlank(taskId)) {
            writeError(response, "taskId 不能为空");
            return;
        }
        // 分页迭代取全量（页大小 500，最多 maxRows 行）
        List<cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity> all = new ArrayList<>();
        int page = 1;
        int size = 500;
        while (all.size() < dataExportService.maxRows()) {
            List<cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalResultEntity> batch =
                    evalResultRepository.queryByTaskId(taskId.trim(), null, page, size);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < size) {
                break;
            }
            page++;
        }
        if (all.size() >= dataExportService.maxRows()) {
            writeError(response, "导出行数达到上限 " + dataExportService.maxRows() + "，请改用 API 分页拉取");
            return;
        }
        log.info("EXPORT_DATA type=eval_results taskId={} rows={}", taskId, all.size());
        writeCsv(response, "eval_results_" + taskId.trim(), dataExportService.buildEvalResultsCsv(all));
    }

    private void writeCsv(HttpServletResponse response, String name, String csv) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".csv\"");
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + ResponseCode.ILLEGAL_PARAMETER + "\",\"info\":\""
                + message + "\",\"data\":null}");
        response.flushBuffer();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
