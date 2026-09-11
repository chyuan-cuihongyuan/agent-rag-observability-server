package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.evaluate.model.entity.EvalDatasetEntity;
import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IEvalDatasetRepository;
import cn.chyuan.ai.observability.domain.evaluate.service.DatasetQualityService;
import cn.chyuan.ai.observability.types.response.Response;
import cn.chyuan.ai.observability.types.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据集质量守护控制器（工单 0172 X3）—
 * GET /api/v1/eval/dataset/{datasetId}/quality（重复/长度分布/覆盖率报告）。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/eval/dataset")
public class DatasetQualityController {

    private final IEvalDatasetRepository evalDatasetRepository;
    private final DatasetQualityService datasetQualityService;

    public DatasetQualityController(IEvalDatasetRepository evalDatasetRepository,
                                    DatasetQualityService datasetQualityService) {
        this.evalDatasetRepository = evalDatasetRepository;
        this.datasetQualityService = datasetQualityService;
    }

    @GetMapping("/{datasetId}/quality")
    public Response<Map<String, Object>> quality(@PathVariable String datasetId) {
        EvalDatasetEntity dataset = evalDatasetRepository.queryByDatasetId(datasetId);
        if (dataset == null) {
            return Response.fail(ResponseCode.ERROR, "数据集不存在: " + datasetId);
        }
        return Response.success(datasetQualityService.analyze(dataset));
    }
}
