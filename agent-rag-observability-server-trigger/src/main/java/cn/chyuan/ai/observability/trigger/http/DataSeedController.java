package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.service.EvalDataSeedService;
import cn.chyuan.ai.observability.domain.observe.service.DataSeedService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据填充控制器
 * 用于开发/演示环境，向 ES 写入模拟数据以展示仪表盘效果
 * 生产环境应通过配置关闭此接口
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/seed")
public class DataSeedController {

    private final DataSeedService dataSeedService;
    private final EvalDataSeedService evalDataSeedService;

    public DataSeedController(DataSeedService dataSeedService, EvalDataSeedService evalDataSeedService) {
        this.dataSeedService = dataSeedService;
        this.evalDataSeedService = evalDataSeedService;
    }

    /**
     * 生成模拟数据
     * POST /api/v1/seed/data?days=7&countPerDay=50
     *
     * @param days        天数（默认 7 天）
     * @param countPerDay 每天生成的 trace 数量（默认 50）
     * @return 生成的 trace 总数
     */
    @PostMapping("/data")
    public Response<Map<String, Object>> seedData(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "50") int countPerDay) {

        // 参数校验
        if (days < 1 || days > 90) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "天数范围：1~90");
        }
        if (countPerDay < 1 || countPerDay > 500) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "每天 trace 数量范围：1~500");
        }

        log.info("开始生成模拟数据：days={}, countPerDay={}", days, countPerDay);
        long startTime = System.currentTimeMillis();
        int totalTraces = dataSeedService.seedData(days, countPerDay);
        long costMs = System.currentTimeMillis() - startTime;

        Map<String, Object> result = Map.of(
                "totalTraces", totalTraces,
                "days", days,
                "countPerDay", countPerDay,
                "costTimeMs", costMs
        );
        log.info("模拟数据生成完成：totalTraces={}, costTimeMs={}ms", totalTraces, costMs);
        return Response.success(result);
    }

    /**
     * 生成评测种子数据 — 供主页「RAG 质量概览」面板展示。
     * POST /api/v1/seed/eval?taskCount=3&itemsPerTask=18
     *
     * @param taskCount    生成几个评测任务（默认 3，覆盖 RAG_RETRIEVAL/ANSWER_QUALITY/CONTEXT_QUALITY）
     * @param itemsPerTask 每个任务多少条评测结果（默认 18）
     * @return 生成的评测结果总数
     */
    @PostMapping("/eval")
    public Response<Map<String, Object>> seedEval(
            @RequestParam(defaultValue = "3") int taskCount,
            @RequestParam(defaultValue = "18") int itemsPerTask) {

        if (taskCount < 1 || taskCount > 20) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "任务数量范围：1~20");
        }
        if (itemsPerTask < 1 || itemsPerTask > 100) {
            return Response.fail(ResponseCode.ILLEGAL_PARAMETER, "每任务条目数范围：1~100");
        }

        log.info("开始生成评测种子数据：taskCount={}, itemsPerTask={}", taskCount, itemsPerTask);
        long startTime = System.currentTimeMillis();
        int totalResults = evalDataSeedService.seedEvalData(taskCount, itemsPerTask);
        long costMs = System.currentTimeMillis() - startTime;

        Map<String, Object> result = Map.of(
                "totalResults", totalResults,
                "taskCount", taskCount,
                "itemsPerTask", itemsPerTask,
                "costTimeMs", costMs
        );
        log.info("评测种子数据生成完成：totalResults={}, costTimeMs={}ms", totalResults, costMs);
        return Response.success(result);
    }
}
