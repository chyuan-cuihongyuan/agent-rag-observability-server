package cn.chyuan.ai.observability.domain.alert.service;

import cn.chyuan.ai.observability.domain.alert.adapter.repository.IConfigChangeEventRepository;
import cn.chyuan.ai.observability.domain.alert.model.entity.ConfigChangeEventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置漂移审计服务（工单 0182 Y6，借鉴 GitOps 留痕思想）—
 * 关键配置表 update 前后快照做字段级 diff（from→to），敏感键脱敏，落 config_change_event。
 * diff 为纯函数；记录动作异常不阻断业务更新（审计失败不连坐）。
 */
@Slf4j
@Service
public class ConfigDriftAuditor {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 敏感键（值脱敏为 ***，长度留痕） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "token", "apikey", "api_key", "api-key", "privatekey");

    private final IConfigChangeEventRepository configChangeEventRepository;

    public ConfigDriftAuditor(IConfigChangeEventRepository configChangeEventRepository) {
        this.configChangeEventRepository = configChangeEventRepository;
    }

    /**
     * diff 纯函数：只输出变化的字段；双方同值/同 null 不算变化；
     * 一方 null 一方空串算变化（口径：null 与 "" 视为不同状态）。
     */
    public List<Map<String, String>> diff(Map<String, String> before, Map<String, String> after) {
        List<Map<String, String>> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }
        Set<String> fields = new java.util.LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        for (String field : fields) {
            String b = before.get(field);
            String a = after.get(field);
            if (java.util.Objects.equals(b, a)) {
                continue;
            }
            Map<String, String> change = new LinkedHashMap<>();
            change.put("field", field);
            change.put("from", mask(field, b));
            change.put("to", mask(field, a));
            changes.add(change);
        }
        return changes;
    }

    /** 记录配置变更（异常吞掉记 warn，审计不阻断业务） */
    public void record(String tableName, String bizKey, Map<String, String> before,
                       Map<String, String> after, String operator) {
        try {
            List<Map<String, String>> changes = diff(before, after);
            if (changes.isEmpty()) {
                return;
            }
            configChangeEventRepository.insert(ConfigChangeEventEntity.builder()
                    .tableName(tableName)
                    .bizKey(bizKey)
                    .changesJson(cn.chyuan.ai.observability.domain.alert.service.ConfigDriftAuditor.toJson(changes))
                    .operator(operator == null || operator.isBlank() ? "unknown" : operator.trim())
                    .createTime(FMT.format(LocalDateTime.now()))
                    .build());
        } catch (Exception e) {
            log.warn("配置变更审计落库失败（不阻断业务）: table={}, err={}", tableName, e.getMessage());
        }
    }

    public List<ConfigChangeEventEntity> queryList(String tableName, String operator, int page, int size) {
        return configChangeEventRepository.queryList(tableName, operator,
                Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    static String mask(String field, String value) {
        if (value == null) {
            return null;
        }
        String lower = field == null ? "" : field.toLowerCase();
        boolean sensitive = SENSITIVE_KEYS.stream().anyMatch(lower::contains);
        return sensitive ? "***(" + value.length() + ")" : value;
    }

    static String toJson(List<Map<String, String>> changes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < changes.size(); i++) {
            Map<String, String> c = changes.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"field\":\"").append(escape(c.get("field")))
                    .append("\",\"from\":").append(jsonValue(c.get("from")))
                    .append(",\"to\":").append(jsonValue(c.get("to"))).append("}");
        }
        return sb.append("]").toString();
    }

    private static String jsonValue(String v) {
        return v == null ? "null" : "\"" + escape(v) + "\"";
    }

    private static String escape(String s) {
        return s == null ? null : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
