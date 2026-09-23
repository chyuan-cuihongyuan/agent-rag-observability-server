package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 指纹分组（工单 0696 CE2，sentry 思想）。
 * 变参消息模板化（数字/UUID/路径/引号串占位归一）/默认指纹（类型+顶层帧函数）/
 * 自定义指纹函数注入/同指纹同组稳定。
 */
public final class FingerprintGrouper {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern UUIDS = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PATHS = Pattern.compile("(?:/[A-Za-z0-9_.\\-]+)+");
    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"");

    private final java.util.function.Function<ErrorEvent.Event, String> fingerprintFn;

    public FingerprintGrouper() {
        this.fingerprintFn = null;
    }

    /** 自定义指纹函数注入 */
    public FingerprintGrouper(java.util.function.Function<ErrorEvent.Event, String> fingerprintFn) {
        this.fingerprintFn = fingerprintFn == null ? null : fingerprintFn;
    }

    /** 消息模板化：UUID→U、路径→P、引号串→S、数字→N */
    public static String template(String value) {
        if (value == null) {
            return "";
        }
        String out = value;
        out = UUIDS.matcher(out).replaceAll("U");
        out = PATHS.matcher(out).replaceAll("P");
        out = QUOTED.matcher(out).replaceAll("S");
        out = DIGITS.matcher(out).replaceAll("N");
        return out;
    }

    /** 指纹：默认 type + 顶层帧函数 + 模板化值；自定义函数可覆盖 */
    public String fingerprint(ErrorEvent.Event event) {
        if (fingerprintFn != null) {
            String custom = fingerprintFn.apply(event);
            if (custom == null || custom.isBlank()) {
                throw new IllegalArgumentException("自定义指纹不得为空");
            }
            return custom;
        }
        return ErrorEvent.topFrameFunction(event) + "|" + event.type() + "|" + template(event.value());
    }

    /** 稳定散列（FNV-1a 32），作组 id */
    public static int stableHash(String text) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    /** 分组入口：指纹→组键（指纹散列十六进制） */
    public String groupKey(ErrorEvent.Event event) {
        String fingerprint = fingerprint(event);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fingerprint", fingerprint);
        return String.format("%08x", stableHash(fingerprint));
    }
}
