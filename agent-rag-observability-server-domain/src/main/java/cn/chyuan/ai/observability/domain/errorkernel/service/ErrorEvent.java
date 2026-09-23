package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 事件归一（工单 0695 CE1，sentry 思想）。
 * 异常事件（类型/值/栈帧序列/标签/时间戳/事件 id）/栈帧规范化
 * （模块+函数+行，去相邻重复、截断上限）/缺字段补默认与非法拒绝。
 */
public final class ErrorEvent {

    public record Frame(String module, String function, int line) {
    }

    /** 归一后事件：不可变 */
    public record Event(String eventId, String type, String value, List<Frame> frames,
                        Map<String, String> tags, long timestampMs, String release) {
    }

    public static final int MAX_FRAMES = 100;
    public static final int MAX_VALUE_LENGTH = 512;

    private ErrorEvent() {
    }

    /** 归一入口：清洗值/栈帧、补默认、非法拒绝 */
    public static Event normalize(String type, String value, List<Frame> rawFrames,
                                  Map<String, String> tags, long timestampMs, String release, String eventId) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("异常类型不得为空");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("异常值不得为空");
        }
        if (timestampMs <= 0) {
            throw new IllegalArgumentException("时间戳必须为正");
        }
        String cleanValue = value.trim();
        if (cleanValue.length() > MAX_VALUE_LENGTH) {
            cleanValue = cleanValue.substring(0, MAX_VALUE_LENGTH);
        }
        List<Frame> frames = new ArrayList<>();
        Frame last = null;
        if (rawFrames != null) {
            for (Frame frame : rawFrames) {
                if (frame == null) {
                    continue;
                }
                Frame normalized = new Frame(
                        frame.module() == null ? "unknown" : frame.module().trim(),
                        frame.function() == null ? "unknown" : frame.function().trim(),
                        frame.line());
                if (normalized.equals(last)) {
                    continue;
                }
                frames.add(normalized);
                last = normalized;
                if (frames.size() >= MAX_FRAMES) {
                    break;
                }
            }
        }
        return new Event(
                eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId,
                type.trim(),
                cleanValue,
                List.copyOf(frames),
                tags == null ? Map.of() : Map.copyOf(tags),
                timestampMs,
                release == null || release.isBlank() ? "unknown" : release.trim());
    }

    /** 顶层帧函数（指纹默认材料） */
    public static String topFrameFunction(Event event) {
        return event.frames().isEmpty() ? "unknown" : event.frames().get(event.frames().size() - 1).function();
    }
}
