package cn.chyuan.ai.observability.domain.promqlkernel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * 指标模型与选择器（工单 0899-0900 DB1·DB2，prometheus 思想）。
 * metric+labels 序列与样本/label matcher 四算符 = != =~ !~/序列唯一性。
 */
public final class PromModel {

    /** 样本：毫秒时间戳与值 */
    public record Sample(long ts, double value) {
    }

    /** 时间序列：metric + labels 唯一确定；样本按时间升序 */
    public static final class Series {
        public final String metric;
        public final Map<String, String> labels;
        public final List<Sample> samples = new ArrayList<>();

        public Series(String metric, Map<String, String> labels) {
            this.metric = metric;
            this.labels = Map.copyOf(labels);
        }

        public void add(long ts, double value) {
            if (!samples.isEmpty() && ts < samples.get(samples.size() - 1).ts()) {
                throw new IllegalArgumentException("样本时间倒退");
            }
            samples.add(new Sample(ts, value));
        }

        /** 序列唯一键：metric + 排序 labels */
        public String key() {
            Map<String, String> sorted = new java.util.TreeMap<>(labels);
            return metric + sorted;
        }
    }

    /** 指标存储：同键序列拒绝（唯一性） */
    public static final class Store {
        private final Map<String, Series> series = new LinkedHashMap<>();

        public Store add(Series s) {
            if (series.containsKey(s.key())) {
                throw new IllegalArgumentException("重复序列: " + s.key());
            }
            series.put(s.key(), s);
            return this;
        }

        public List<Series> all() {
            return List.copyOf(series.values());
        }
    }

    public enum MatchOp { EQ, NEQ, RE, NRE }

    /** label 匹配器（DB2） */
    public record Matcher(String label, MatchOp op, String value) {
        public Matcher {
            if (op == MatchOp.RE || op == MatchOp.NRE) {
                try {
                    java.util.regex.Pattern.compile(value);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("非法正则: " + value);
                }
            }
        }

        public boolean matches(Map<String, String> labels) {
            String actual = labels.getOrDefault(label, "");
            return switch (op) {
                case EQ -> actual.equals(value);
                case NEQ -> !actual.equals(value);
                case RE -> java.util.regex.Pattern.compile(value).matcher(actual).matches();
                case NRE -> !java.util.regex.Pattern.compile(value).matcher(actual).matches();
            };
        }
    }

    /** 序列选择器：metric + 匹配器集合 */
    public record Selector(String metric, List<Matcher> matchers, long lookbackMs) {
        public Selector {
            matchers = List.copyOf(matchers);
        }

        public List<Series> select(Store store) {
            List<Series> out = new ArrayList<>();
            for (Series s : store.all()) {
                if (!s.metric.equals(metric)) {
                    continue;
                }
                boolean ok = true;
                for (Matcher m : matchers) {
                    if (!m.matches(s.labels)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    out.add(s);
                }
            }
            return out;
        }
    }
}
