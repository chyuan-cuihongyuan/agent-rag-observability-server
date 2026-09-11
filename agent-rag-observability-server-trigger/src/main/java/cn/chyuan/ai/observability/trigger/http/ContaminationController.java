package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.service.ContaminationCheckService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 池间污染检查控制器（工单 0173）—
 * GET /api/v1/eval/contamination?poolA=&poolB=&blockThreshold=。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/contamination")
public class ContaminationController {

    private final ContaminationCheckService contaminationCheckService;

    public ContaminationController(ContaminationCheckService contaminationCheckService) {
        this.contaminationCheckService = contaminationCheckService;
    }

    @GetMapping
    public Response<Map<String, Object>> check(@RequestParam(defaultValue = "challenge") String poolA,
                                               @RequestParam(defaultValue = "golden") String poolB,
                                               @RequestParam(defaultValue = "0.05") double blockThreshold) {
        String a = poolA.trim().toLowerCase();
        String b = poolB.trim().toLowerCase();
        if (!(("golden".equals(a) || "challenge".equals(a) || "wrong".equals(a)))
                || !("golden".equals(b) || "challenge".equals(b) || "wrong".equals(b))) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "池名合法值 golden/challenge/wrong");
        }
        double threshold = Math.min(Math.max(blockThreshold, 0.0), 1.0);
        return Response.success(contaminationCheckService.analyze(
                contaminationCheckService.poolQueries(a),
                contaminationCheckService.poolQueries(b),
                threshold));
    }
}
