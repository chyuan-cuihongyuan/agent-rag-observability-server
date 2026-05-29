package cn.chyuan.ai.observability.trigger.http.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

public final class RequestValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2}:\\d{2})?$"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> DASHBOARD_INTERVALS = Set.of("hour", "day");

    private RequestValidator() {
    }

    public static String validateId(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return fieldName + "不能为空";
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            return fieldName + "格式非法";
        }
        return null;
    }

    public static String validateOptionalId(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateId(fieldName, value);
    }

    public static String validatePage(int page, int size) {
        if (page < 1 || page > 10000) {
            return "page必须在1到10000之间";
        }
        if (size < 1 || size > 200) {
            return "size必须在1到200之间";
        }
        return null;
    }

    public static String validateDays(int days) {
        if (days < 1 || days > 90) {
            return "days必须在1到90之间";
        }
        return null;
    }

    public static String validateDashboardInterval(String interval) {
        if (interval == null || !DASHBOARD_INTERVALS.contains(interval)) {
            return "interval仅支持hour或day";
        }
        return null;
    }

    public static String validateTimeRange(String startTime, String endTime) {
        String startError = validateOptionalDateTime("startTime", startTime);
        if (startError != null) {
            return startError;
        }
        String endError = validateOptionalDateTime("endTime", endTime);
        if (endError != null) {
            return endError;
        }
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            return "startTime不能晚于endTime";
        }
        return null;
    }

    private static String validateOptionalDateTime(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!DATETIME_PATTERN.matcher(value).matches()) {
            return fieldName + "格式非法";
        }
        if (parseDateTime(value) == null) {
            return fieldName + "不是有效日期时间";
        }
        return null;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value, DATE_FORMATTER).atStartOfDay();
            }
            return LocalDateTime.parse(value.replace('T', ' '), DATETIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
