package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.service.RobustnessService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 扰动鲁棒性控制器（工单 0174 X5）—
 * POST /api/v1/eval/robustness/generate?datasetId=（生成扰动副本数据集）、
 * GET /api/v1/eval/robustness/compare?taskOriginal=&taskPerturbed=（两任务成绩差值）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/robustness")
public class RobustnessController {

    private final RobustnessService robustnessService;

    public RobustnessController(RobustnessService robustnessService) {
        this.robustnessService = robustnessService;
    }

    @PostMapping("/generate")
    public Response<EvalDatasetEntity> generate(@RequestParam String datasetId) {
        try {
            return Response.success(robustnessService.generatePerturbedDataset(datasetId.trim()));
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }

    @GetMapping("/compare")
    public Response<Map<String, Object>> compare(@RequestParam String taskOriginal,
                                                 @RequestParam String taskPerturbed) {
        return Response.success(robustnessService.compare(taskOriginal.trim(), taskPerturbed.trim()));
    }
}
