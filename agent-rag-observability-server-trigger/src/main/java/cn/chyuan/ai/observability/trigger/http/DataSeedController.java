package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.observe.service.DataSeedService;
import cn.chyuan.ai.observability.types.response.Response;
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

    public DataSeedController(DataSeedService dataSeedService) {
        this.dataSeedService = dataSeedService;
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
            return Response.fail(cn.chyuan.ai.observability.types.response.ResponseCode.ILLEGAL_PARAMETER,
                    "天数范围：1~90");
        }
        if (countPerDay < 1 || countPerDay > 500) {
            return Response.fail(cn.chyuan.ai.observability.types.response.ResponseCode.ILLEGAL_PARAMETER,
                    "每天 trace 数量范围：1~500");
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
}
