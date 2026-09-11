package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.service.EvalReportService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测报告控制器（工单 0175 X6）—
 * GET /api/v1/eval/report?taskId=&gateId=（data 返回 markdown 报告）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/report")
public class EvalReportController {

    private final EvalReportService evalReportService;

    public EvalReportController(EvalReportService evalReportService) {
        this.evalReportService = evalReportService;
    }

    @GetMapping
    public Response<String> report(@RequestParam(required = false) String taskId,
                                   @RequestParam(required = false) String gateId) {
        try {
            String markdown = evalReportService.generate(taskId, gateId, EvalReportService.Sections.all());
            return Response.success(markdown);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("评测报告生成失败", e);
            return Response.fail(ResponseCode.ERROR, "报告生成失败: " + e.getMessage());
        }
    }
}
