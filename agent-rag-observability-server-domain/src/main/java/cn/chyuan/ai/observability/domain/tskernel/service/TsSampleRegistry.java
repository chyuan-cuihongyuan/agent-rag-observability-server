package cn.chyuan.ai.observability.domain.tskernel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时序样本表登记器（工单 0486 BF7）。
 * 样本批次登记（序列 id+时间戳+值+批号，同批同键幂等）与查询。
 * ts-kernel.enabled 默认关。持久化面 = 第 34 表 ts_sample。
 */
public class TsSampleRegistry {

    /** 登记行（对应 ts_sample 表行） */
    public record SampleRow(long seriesId, long timestamp, double value, String batchId) {
    }

    private final Map<String, SampleRow> rows = new ConcurrentHashMap<>();

    /** 登记样本（同批次同序列同时间戳幂等跳过），返回是否新入 */
    public synchronized boolean add(long seriesId, long timestamp, double value, String batchId) {
        String key = batchId + '/' + seriesId + '/' + timestamp;
        return rows.putIfAbsent(key, new SampleRow(seriesId, timestamp, value, batchId)) == null;
    }

    /** 批次登记：返回新入条数（同批次重放幂等 = 0） */
    public synchronized int addBatch(long seriesId, List<TimeBlocker.Sample> samples, String batchId) {
        int added = 0;
        for (TimeBlocker.Sample sample : samples) {
            if (add(seriesId, sample.timestamp(), sample.value(), batchId)) {
                added++;
            }
        }
        return added;
    }

    /** 序列样本（时间戳升序） */
    public synchronized List<SampleRow> bySeries(long seriesId) {
        return rows.values().stream()
                .filter(row -> row.seriesId() == seriesId)
                .sorted(java.util.Comparator.comparingLong(SampleRow::timestamp))
                .toList();
    }

    public synchronized int size() {
        return rows.size();
    }
}
