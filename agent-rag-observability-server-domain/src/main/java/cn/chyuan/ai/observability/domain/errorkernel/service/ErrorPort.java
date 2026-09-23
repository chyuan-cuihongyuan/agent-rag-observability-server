package cn.chyuan.ai.observability.domain.errorkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误聚合端口+组合管线（工单 0702 CE8）。
 * ingest→group→aggregate→flare→health 全链；与 logkernel 只读联动
 * （日志行作事件源形态，泛型入参不 import logkernel，不改其任何类）/
 * error-kernel.enabled 默认关（开启才改变行为）。
 */
public interface ErrorPort {

    record IngestResult(int ingested, int groups, List<String> topKeys) {
    }

    record Snapshot(List<ErrorGroupStore.Group> topProblems, List<FlareDetector.Flare> flares,
                    List<ReleaseHealth.Regression> regressions) {
    }

    /** 事件摄取：归一→指纹分组→组统计（相似兜底可选） */
    IngestResult ingest(List<ErrorEvent.Event> events);

    /** 聚合快照：top 问题/flare/发布回归 */
    Snapshot snapshot(int topLimit, double regressionThreshold);

    /**
     * 与 logkernel 只读联动：日志行「[level] type: value (module.function:line)」
     * 解析为事件源形态（泛型入参不 import logkernel）。
     */
    List<ErrorEvent.Event> eventsFromLogLines(List<String> logLines, long baseTimestampMs);

    /** 事件摄取（含日志行联动入口） */
    IngestResult ingestLogLines(List<String> logLines, long baseTimestampMs);

    /** 内存假实现：归一+指纹+相似兜底+组统计+flare+发布健康全链 */
    class InMemoryErrorAggregator implements ErrorPort {

        private final FingerprintGrouper grouper = new FingerprintGrouper();
        private final ErrorGroupStore store = new ErrorGroupStore();
        private final SimilarityGrouper similarity = new SimilarityGrouper(0.6d, 64);
        private final FlareDetector flares = new FlareDetector(60_000L, 3, 3.0d, 0L);
        private final ReleaseHealth health = new ReleaseHealth();
        private final Map<String, String> fallbackTitles = new LinkedHashMap<>();
        private int ingested;

        @Override
        public synchronized IngestResult ingest(List<ErrorEvent.Event> events) {
            if (events == null) {
                throw new IllegalArgumentException("事件清单不得为 null");
            }
            for (ErrorEvent.Event event : events) {
                String groupKey = grouper.groupKey(event);
                String title = event.type() + ": " + event.value();
                ErrorGroupStore.Group group = store.record(groupKey, grouper.fingerprint(event), title, event.timestampMs());
                String assigned = similarity.assignNearest(title, fallbackTitles, fallbackTitles.size());
                if (assigned == null && !fallbackTitles.containsKey(groupKey)) {
                    fallbackTitles.put(groupKey, title);
                } else if (assigned != null) {
                    group.retitle(fallbackTitles.get(assigned));
                }
                flares.record(groupKey, event.timestampMs());
                health.record(event.release(), event.timestampMs());
                ingested++;
            }
            return new IngestResult(ingested, store.size(), store.topProblems(Math.min(3, store.size()))
                    .stream().map(ErrorGroupStore.Group::groupKey).toList());
        }

        @Override
        public synchronized Snapshot snapshot(int topLimit, double regressionThreshold) {
            return new Snapshot(store.topProblems(topLimit), new ArrayList<>(), health.regressions(regressionThreshold));
        }

        @Override
        public List<ErrorEvent.Event> eventsFromLogLines(List<String> logLines, long baseTimestampMs) {
            if (logLines == null) {
                throw new IllegalArgumentException("日志行不得为 null");
            }
            List<ErrorEvent.Event> events = new ArrayList<>();
            long offset = 0;
            for (String line : logLines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String body = line.startsWith("[") ? line.substring(line.indexOf(']') + 1).trim() : line;
                int colon = body.indexOf(':');
                String type = colon > 0 ? body.substring(0, colon).trim() : "LogError";
                String rest = colon > 0 ? body.substring(colon + 1).trim() : body;
                String function = "log";
                String value = rest;
                int paren = rest.lastIndexOf('(');
                if (paren > 0 && rest.endsWith(")")) {
                    String loc = rest.substring(paren + 1, rest.length() - 1);
                    int at = loc.indexOf(':');
                    if (at > 0) {
                        function = loc.substring(0, at);
                        value = rest.substring(0, paren).trim();
                    }
                }
                events.add(ErrorEvent.normalize(type.isBlank() ? "LogError" : type, value,
                        List.of(new ErrorEvent.Frame("log", function, 0)), Map.of(),
                        baseTimestampMs + offset * 1000, "unknown", null));
                offset++;
            }
            return events;
        }

        @Override
        public IngestResult ingestLogLines(List<String> logLines, long baseTimestampMs) {
            return ingest(eventsFromLogLines(logLines, baseTimestampMs));
        }

        FlareDetector flareDetector() {
            return flares;
        }

        ReleaseHealth releaseHealth() {
            return health;
        }

        ErrorGroupStore groupStore() {
            return store;
        }
    }
}
