package cn.chyuan.ai.observability.domain.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度任务注册表（工单 0189 Z6）— 各定时任务运行后上报最近一次运行状态，
 * /api/v1/schedulers/status 统一输出（内存态；进程重启即清零，属「最近一轮」语义）。
 */
@Slf4j
@Service
public class SchedulerRunRegistry {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** task → [lastRunAt, lastResult] */
    private final ConcurrentHashMap<String, String[]> runs = new ConcurrentHashMap<>();

    /** 任务上报（结果：OK / 错误摘要） */
    public void report(String task, boolean ok, String detail) {
        runs.put(task, new String[]{
                FMT.format(LocalDateTime.now()),
                (ok ? "OK" : "FAIL") + (detail == null || detail.isBlank() ? "" : " - " + detail)
        });
    }

    /** 快照（未运行过的任务不出现；由调用方注册预期任务清单） */
    public List<Map<String, Object>> snapshot(List<String> expectedTasks) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String task : expectedTasks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task", task);
            String[] run = runs.get(task);
            row.put("lastRunAt", run == null ? null : run[0]);
            row.put("lastResult", run == null ? "NEVER" : run[1]);
            rows.add(row);
        }
        return rows;
    }
}
