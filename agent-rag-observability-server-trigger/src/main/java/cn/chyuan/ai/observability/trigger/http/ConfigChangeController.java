package cn.chyuan.ai.observability.trigger.http;

import cn.chyuan.ai.observability.domain.alert.model.entity.ConfigChangeEventEntity;
import cn.chyuan.ai.observability.domain.alert.service.ConfigDriftAuditor;
import cn.chyuan.ai.observability.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置漂移审计控制器（工单 0182 Y6）—
 * GET /api/v1/config-changes?tableName=&operator=&page=&size=。
 */
@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"})
@RequestMapping("/api/v1/config-changes")
public class ConfigChangeController {

    private final ConfigDriftAuditor configDriftAuditor;

    public ConfigChangeController(ConfigDriftAuditor configDriftAuditor) {
        this.configDriftAuditor = configDriftAuditor;
    }

    @GetMapping
    public Response<List<ConfigChangeEventEntity>> list(@RequestParam(required = false) String tableName,
                                                        @RequestParam(required = false) String operator,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Response.success(configDriftAuditor.queryList(tableName, operator, page, size));
    }
}
