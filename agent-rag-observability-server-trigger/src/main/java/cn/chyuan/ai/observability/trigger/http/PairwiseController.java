package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.PairwiseRequestDTO;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IPairwiseRecordRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.PairwiseRecordEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.PairwiseJudgeService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * pairwise 对比判定控制器（工单 0170 X1）—
 * POST /api/v1/eval/pairwise（taskA/taskB/datasetId 触发对比）、
 * GET /api/v1/eval/pairwise/records?taskA=&taskB=（对局记录）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/pairwise")
public class PairwiseController {

    private final PairwiseJudgeService pairwiseJudgeService;
    private final IPairwiseRecordRepository pairwiseRecordRepository;

    public PairwiseController(PairwiseJudgeService pairwiseJudgeService,
                              IPairwiseRecordRepository pairwiseRecordRepository) {
        this.pairwiseJudgeService = pairwiseJudgeService;
        this.pairwiseRecordRepository = pairwiseRecordRepository;
    }

    @PostMapping
    public Response<Map<String, Object>> compare(@RequestBody PairwiseRequestDTO request) {
        if (request == null || isBlank(request.getTaskA()) || isBlank(request.getTaskB())) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "taskA/taskB 不能为空");
        }
        try {
            return Response.success(pairwiseJudgeService.compare(
                    request.getTaskA().trim(), request.getTaskB().trim(),
                    request.getDatasetId() == null ? null : request.getDatasetId().trim()));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("pairwise 对比失败", e);
            return Response.fail(ResponseCode.ERROR, "对比失败: " + e.getMessage());
        }
    }

    @GetMapping("/records")
    public Response<List<PairwiseRecordEntity>> records(@RequestParam(required = false) String taskA,
                                                        @RequestParam(required = false) String taskB,
                                                        @RequestParam(defaultValue = "500") int limit) {
        return Response.success(pairwiseRecordRepository.queryList(
                blankToNull(taskA), blankToNull(taskB), limit));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
