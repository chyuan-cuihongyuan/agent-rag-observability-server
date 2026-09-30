package cn.chyuan.ai.observability.domain.loadkernel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 压测编排实现（工单 1150 EY8，k6 思想）。
 * 每场景独立 VU 池；run 时收口校验并按执行器推进虚拟时钟。
 */
public final class LoadEngine implements LoadPort {

    private final Scenarios scenarios = new Scenarios();
    private final Map<String, VirtualUsers> pools = new LinkedHashMap<>();
    private final Thresholds thresholds = new Thresholds();
    private final Checks checks = new Checks();
    private final Metrics metrics = new Metrics();
    private Scenarios.Scenario pending;

    private VirtualUsers pool(String scenario) {
        return pools.computeIfAbsent(scenario, k -> new VirtualUsers());
    }

    @Override
    public LoadPort scenario(String name, String type) {
        pending = scenarios.open(name, type);
        return this;
    }

    @Override
    public LoadPort vus(int vus) {
        requirePending().vus = vus;
        return this;
    }

    @Override
    public LoadPort duration(int ticks) {
        requirePending().durationTicks = ticks;
        return this;
    }

    @Override
    public LoadPort stage(int durationTicks, int targetVus) {
        requirePending().stages.add(new RampingStages.Stage(durationTicks, targetVus));
        return this;
    }

    @Override
    public LoadPort rate(int rate) {
        requirePending().rate = rate;
        return this;
    }

    @Override
    public LoadPort timeUnit(int ticks) {
        requirePending().timeUnitTicks = ticks;
        return this;
    }

    @Override
    public LoadPort preAllocatedVus(int vus) {
        requirePending().preAllocatedVus = vus;
        return this;
    }

    @Override
    public LoadPort iterationTicks(int ticks) {
        requirePending().iterationTicks = ticks;
        return this;
    }

    @Override
    public LoadPort threshold(String metric, String agg, String op, double bound) {
        String scenario = requirePending().name;
        thresholds.add(scenario, new Thresholds.Threshold(metric, agg, op, bound));
        return this;
    }

    @Override
    public long run(String name) {
        Scenarios.Scenario scenario = scenarios.get(name);
        if (!scenario.sealed) {
            scenarios.seal(scenario);
        }
        VirtualUsers users = pool(name);
        long iterations;
        switch (scenario.type) {
            case CONSTANT_VUS -> {
                users.scaleTo(scenario.vus);
                iterations = (long) scenario.vus * scenario.durationTicks;
            }
            case RAMPING_VUS -> {
                RampingStages stages = new RampingStages();
                for (RampingStages.Stage stage : scenario.stages) {
                    stages.add(stage.durationTicks(), stage.targetVus());
                }
                iterations = stages.totalIterations(users, scenario.durationTicks);
            }
            default -> {
                long interval = ArrivalRate.intervalTicks(scenario.rate, scenario.timeUnitTicks);
                ArrivalRate.admitPreAllocated(scenario.preAllocatedVus, interval, scenario.iterationTicks);
                iterations = ArrivalRate.launches(scenario.rate, scenario.timeUnitTicks, scenario.durationTicks);
                users.scaleTo(scenario.preAllocatedVus);
            }
        }
        return iterations;
    }

    @Override
    public List<String> scenarios() {
        return scenarios.names();
    }

    @Override
    public int vuCount(String name) {
        scenarios.get(name);
        return pool(name).activeCount();
    }

    @Override
    public void interrupt(String name, int count) {
        scenarios.get(name);
        pool(name).interrupt(count);
    }

    @Override
    public void check(String checkName, String tag, boolean ok) {
        checks.record(checkName, tag, ok);
    }

    @Override
    public double checkRate(String checkName) {
        return checks.rate(checkName);
    }

    @Override
    public double checkRateByTag(String checkName, String tag) {
        return checks.rateByTag(checkName, tag);
    }

    @Override
    public void metric(String name, String kind) {
        metrics.declare(name, kind);
    }

    @Override
    public void sample(String name, double value) {
        metrics.sample(name, value);
    }

    @Override
    public double agg(String name, String agg) {
        return metrics.aggregate(name, agg);
    }

    @Override
    public boolean breached(String name) {
        scenarios.get(name);
        return thresholds.breached(name, (metric, agg) -> {
            try {
                return metrics.aggregate(metric, agg);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    @Override
    public List<String> samplesShape() {
        return List.of("timestamp", "value");
    }

    private Scenarios.Scenario requirePending() {
        if (pending == null) {
            throw new IllegalStateException("须先 scenario(name, type) 开声明");
        }
        return pending;
    }
}
