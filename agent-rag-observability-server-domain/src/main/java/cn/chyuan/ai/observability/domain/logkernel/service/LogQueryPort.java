package cn.chyuan.ai.observability.domain.logkernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 日志查询端口+组合管线（工单 0580 BQ8）。
 * LogQueryPort（摄入→流选择→管道解析→行过滤→时间聚合确定性输出）+
 * 组合管线（多流摄入→选择器+管道查询→窗口聚合）+与 tskernel 指标只读联动
 * （聚合结果可作指标样本输入形态，不改 tskernel）/
 * log-kernel.enabled 默认关（开启才改变行为）。
 */
public interface LogQueryPort {

    /** 聚合结果行 */
    record QueryResult(long windowStart, String group, double value) {
    }

    /** 摄入一条日志（标签集+时间戳+行） */
    void ingest(Map<String, String> labels, long ts, String line);

    /**
     * 查询：流选择→行解析→过滤→窗口聚合（确定性输出按窗口升序）。
     * aggField=null 时 COUNT 计数聚合。
     */
    List<QueryResult> query(StreamSelector selector, List<ParserStages.Stage> stages,
            List<LineFilter.Expression> filters, WindowAgg agg, String aggField,
            String groupLabel, boolean fillGaps);

    /** 与 tskernel 只读联动：聚合行→指标样本（[windowStart, value] 对，tskernel 样本输入形态） */
    List<long[]> toMetricSamples(List<QueryResult> results);

    /** 内存假实现 */
    class InMemoryLogEngine implements LogQueryPort {

        private final LogStreams.StreamSet streams = new LogStreams.StreamSet();
        private final LogStreamRegistry registry = new LogStreamRegistry();

        @Override
        public void ingest(java.util.Map<String, String> labels, long ts, String line) {
            LogStreams.Stream stream = streams.ingest(labels, ts, line);
            registry.ingest(stream.key(), 1, line.length(), ts, ts);
        }

        @Override
        public synchronized List<QueryResult> query(StreamSelector selector, List<ParserStages.Stage> stages,
                List<LineFilter.Expression> filters, WindowAgg agg, String aggField,
                String groupLabel, boolean fillGaps) {
            ParserStages parser = new ParserStages();
            LineFilter filter = new LineFilter();
            List<QueryResult> results = new ArrayList<>();
            for (String streamKey : selector.select(streamLabelsByStream())) {
                LogStreams.Stream stream = streams.stream(streamKey);
                List<String> lines = new ArrayList<>();
                List<Long> timestamps = new ArrayList<>();
                for (LogStreams.Entry entry : stream.entries()) {
                    lines.add(entry.line());
                    timestamps.add(entry.ts());
                }
                List<ParserStages.ParsedLine> parsed = parser.run(lines, stages);
                parsed = filter.filter(parsed, filters);
                List<WindowAgg.Measurement> measurements = new ArrayList<>();
                for (int i = 0; i < parsed.size(); i++) {
                    ParserStages.ParsedLine line = parsed.get(i);
                    String group = groupLabel == null ? null
                            : streamLabels(streamKey).get(groupLabel);
                    WindowAgg.Measurement measurement = aggField == null
                            ? new WindowAgg.Measurement(timestamps.get(i), group, 1.0d)
                            : WindowAgg.measurement(line, timestamps.get(i), group, aggField);
                    if (measurement != null) {
                        measurements.add(measurement);
                    }
                }
                for (WindowAgg.AggRow row : agg.aggregate(measurements,
                        aggField == null ? WindowAgg.Func.COUNT : WindowAgg.Func.SUM,
                        groupLabel == null ? null : "", fillGaps)) {
                    results.add(new QueryResult(row.windowStart(), row.group(), row.value()));
                }
            }
            results.sort((a, b) -> a.windowStart() != b.windowStart()
                    ? Long.compare(a.windowStart(), b.windowStart())
                    : a.group().compareTo(b.group()));
            return List.copyOf(results);
        }

        @Override
        public List<long[]> toMetricSamples(List<QueryResult> results) {
            List<long[]> samples = new ArrayList<>(results.size());
            for (QueryResult result : results) {
                samples.add(new long[]{result.windowStart(), (long) result.value()});
            }
            return List.copyOf(samples);
        }

        private Map<String, Map<String, String>> streamLabelsByStream() {
            Map<String, Map<String, String>> out = new TreeMap<>();
            for (String key : streams.keys()) {
                out.put(key, streamLabels(key));
            }
            return out;
        }

        private Map<String, String> streamLabels(String streamKey) {
            Map<String, String> labels = new TreeMap<>();
            if (streamKey.length() > 2 && streamKey.endsWith("}")) {
                String body = streamKey.substring(1, streamKey.length() - 1);
                for (String pair : body.split(",")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        labels.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                }
            }
            return labels;
        }

        public LogStreamRegistry registry() {
            return registry;
        }
    }
}
