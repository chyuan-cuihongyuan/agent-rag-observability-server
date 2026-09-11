package cn.chyuan.ai.observability.domain.alert.service;

import cn.chyuan.ai.observability.domain.alert.adapter.repository.IAlertSilenceRepository;
import cn.chyuan.ai.observability.domain.alert.model.entity.AlertSilenceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 告警静默服务（工单 0179 Y3，借鉴 Alertmanager silence）—
 * 匹配纯函数：silence_key 精确匹配，或前缀通配（键以 * 结尾匹配前缀）；
 * 窗口判定 starts_at ≤ now ≤ ends_at（过期规则惰性失效，不依赖清理任务）。
 * 命中静默的告警不投递，仅记 SUPPRESSED 计数（打点由基础设施端口承担）。
 */
@Slf4j
@Service
public class AlertSilenceService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IAlertSilenceRepository alertSilenceRepository;

    public AlertSilenceService(IAlertSilenceRepository alertSilenceRepository) {
        this.alertSilenceRepository = alertSilenceRepository;
    }

    /** 匹配纯函数：给定静默规则与告警键、当前时间，判断是否被静默 */
    public static boolean matches(AlertSilenceEntity rule, String alertKey, String now) {
        if (rule == null || rule.getSilenceKey() == null || alertKey == null) {
            return false;
        }
        boolean inWindow;
        if (rule.getStartsAt() != null && now.compareTo(rule.getStartsAt()) < 0) {
            return false;
        }
        inWindow = rule.getEndsAt() == null || now.compareTo(rule.getEndsAt()) <= 0;
        if (!inWindow) {
            return false;
        }
        String key = rule.getSilenceKey();
        return key.endsWith("*") ? alertKey.startsWith(key.substring(0, key.length() - 1))
                : key.equals(alertKey);
    }

    /** 告警键是否被任一有效规则静默 */
    public boolean isSilenced(String alertKey) {
        String now = FMT.format(LocalDateTime.now());
        List<AlertSilenceEntity> rules = alertSilenceRepository.queryAll();
        if (rules == null) {
            return false;
        }
        return rules.stream().anyMatch(r -> matches(r, alertKey, now));
    }

    /** 新建静默窗口（时间格式/起止顺序校验） */
    public AlertSilenceEntity create(String silenceKey, String startsAt, String endsAt,
                                     String createdBy, String reason) {
        if (silenceKey == null || silenceKey.isBlank()) {
            throw new IllegalArgumentException("silenceKey 不能为空");
        }
        if (startsAt == null || endsAt == null || endsAt.compareTo(startsAt) < 0) {
            throw new IllegalArgumentException("时间窗非法：endsAt 必须不早于 startsAt");
        }
        AlertSilenceEntity entity = AlertSilenceEntity.builder()
                .silenceKey(silenceKey.trim())
                .startsAt(startsAt.trim())
                .endsAt(endsAt.trim())
                .createdBy(createdBy == null || createdBy.isBlank() ? "unknown" : createdBy.trim())
                .reason(reason)
                .createTime(FMT.format(LocalDateTime.now()))
                .build();
        alertSilenceRepository.insert(entity);
        return entity;
    }

    public List<AlertSilenceEntity> list() {
        return alertSilenceRepository.queryAll();
    }

    public boolean delete(long id) {
        return alertSilenceRepository.deleteById(id);
    }
}
