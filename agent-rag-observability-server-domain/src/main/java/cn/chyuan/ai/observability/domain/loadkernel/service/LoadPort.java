package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.List;

/**
 * 压测编排端口（工单 1150 EY8，k6 思想）。
 * scenario·iterate·threshold 入口统一编排：场景定义·VU 生命周期·ramping 阶段·
 * 到达率·阈值判定·check·指标聚合组合管线/tskernel 样本形状只读联动
 * （形状键与 tskernel Sample 字段对齐，不 import tskernel）/
 * load-kernel.enabled 默认关（开启才改变行为）。
 */
public interface LoadPort {

    /** 开始场景声明（EY1） */
    LoadPort scenario(String name, String type);

    LoadPort vus(int vus);

    LoadPort duration(int ticks);

    LoadPort stage(int durationTicks, int targetVus);

    LoadPort rate(int rate);

    LoadPort timeUnit(int ticks);

    LoadPort preAllocatedVus(int vus);

    LoadPort iterationTicks(int ticks);

    /** 挂阈值：metric 聚合口径 op 界值（EY5） */
    LoadPort threshold(String metric, String agg, String op, double bound);

    /** 执行场景至 duration 尽：返回迭代数（EY2-EY4） */
    long run(String name);

    List<String> scenarios();

    /** 当前活跃 VU 数（EY2） */
    int vuCount(String name);

    /** 中断 graceful 降容（EY2） */
    void interrupt(String name, int count);

    /** 记 check 并查通过率（EY6） */
    void check(String checkName, String tag, boolean ok);

    double checkRate(String checkName);

    double checkRateByTag(String checkName, String tag);

    /** 声明指标/采样/聚合（EY7） */
    void metric(String name, String kind);

    void sample(String name, double value);

    double agg(String name, String agg);

    /** 场景阈值判定：任一越界即失败（EY5） */
    boolean breached(String name);

    /** tskernel 样本形状只读联动（Sample: timestamp/value） */
    List<String> samplesShape();

    static LoadPort inMemory() {
        return new LoadEngine();
    }
}
