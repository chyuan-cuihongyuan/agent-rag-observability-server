package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.api.dto.evaluate.BacktestRequestDTO;
import cn.chyuan.ai.observability.domain.evaluate.service.BacktestService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import cn.chyuan.ai.observability.trigger.http.support.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 回测触发控制器（工单 0136 R4）— 变更门控闭环入口：
 * POST /api/v1/eval/backtest（datasetId 或 pool + gateId）→ 创建回测任务
 * （trials 取门禁配置，复用 Pass@k 异步执行链）→ 立即返回 taskId；
 * 门禁判定在任务完成回写处挂接（GateJudgeService），结论落 eval_gate_record。
 * <p>
 * CI/CD 接入形态（curl 轮询两跳拿结论）见 docs/02-agent-rag-observability-server/12-回测与分层门禁.md。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * 触发回测：创建任务并异步执行（@Async 不可同步等待结论），立即返回 taskId。
     * 调用方（CI/CD）轮询任务状态，终态后查门禁记录拿 PASS/BLOCK。
     */
    @PostMapping("/api/v1/eval/backtest")
    public Response<Map<String, String>> trigger(@RequestBody BacktestRequestDTO dto) {
        if (dto == null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "请求体不能为空");
        }
        String gateError = RequestValidator.validateId("gateId", dto.getGateId());
        if (gateError != null) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, gateError);
        }
        boolean noDataset = dto.getDatasetId() == null || dto.getDatasetId().isBlank();
        boolean noPool = dto.getPool() == null || dto.getPool().isBlank();
        if (noDataset && noPool) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "datasetId 与 pool 必须提供其一");
        }
        if (!noDataset) {
            String datasetError = RequestValidator.validateId("datasetId", dto.getDatasetId());
            if (datasetError != null) {
                return Response.fail(ResponseCode.ILLEGAL_PARAMETER, datasetError);
            }
        }
        try {
            Map<String, String> result = backtestService.triggerBacktest(
                    dto.getDatasetId(), dto.getPool(), dto.getGateId(), dto.getEvalType(),
                    dto.getModelVersion(), dto.getRagStrategyVersion());
            return Response.success(result);
        } catch (IllegalArgumentException e) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
    }
}
