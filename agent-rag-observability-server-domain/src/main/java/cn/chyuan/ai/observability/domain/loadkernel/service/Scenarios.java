package cn.chyuan.ai.observability.domain.loadkernel.service;

/**
 * 场景定义（工单 1143 EY1，k6 思想）。
 * executors 声明 vus·duration 参数/负值零值拒绝/多场景并存/名称唯一。
 */
public final class Scenarios {

    /** 执行器类型 */
    public enum Type { CONSTANT_VUS, RAMPING_VUS, CONSTANT_ARRIVAL_RATE }

    /** 场景：类型 + 全量参数 */
    public static final class Scenario {
        public final String name;
        public final Type type;
        public int vus;
        public int durationTicks;
        public int startVus = -1;
        public final java.util.List<RampingStages.Stage> stages = new java.util.ArrayList<>();
        public int rate;
        public int timeUnitTicks;
        public int preAllocatedVus;
        public int iterationTicks = 1;
        boolean sealed;

        Scenario(String name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    private final java.util.Map<String, Scenario> scenarios = new java.util.LinkedHashMap<>();

    /** 开声明：名称唯一 */
    public Scenario open(String name, String type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("场景名不能为空");
        }
        if (scenarios.containsKey(name)) {
            throw new IllegalArgumentException("重复场景名: " + name);
        }
        Type resolved = switch (type == null ? "" : type) {
            case "constant-vus" -> Type.CONSTANT_VUS;
            case "ramping-vus" -> Type.RAMPING_VUS;
            case "constant-arrival-rate" -> Type.CONSTANT_ARRIVAL_RATE;
            default -> throw new IllegalArgumentException("未知执行器: " + type);
        };
        Scenario scenario = new Scenario(name, resolved);
        scenarios.put(name, scenario);
        return scenario;
    }

    /** 收口校验：参数负值零值拒绝；各执行器必填齐备 */
    public void seal(Scenario scenario) {
        if (scenario.durationTicks <= 0) {
            throw new IllegalArgumentException("duration 必须为正: " + scenario.name);
        }
        switch (scenario.type) {
            case CONSTANT_VUS -> {
                if (scenario.vus <= 0) {
                    throw new IllegalArgumentException("vus 必须为正: " + scenario.name);
                }
            }
            case RAMPING_VUS -> {
                if (scenario.stages.isEmpty()) {
                    throw new IllegalArgumentException("ramping-vus 须至少一个阶段: " + scenario.name);
                }
            }
            case CONSTANT_ARRIVAL_RATE -> {
                if (scenario.rate <= 0 || scenario.timeUnitTicks <= 0 || scenario.preAllocatedVus < 0) {
                    throw new IllegalArgumentException("rate/timeUnit/preAllocatedVUs 不合法: " + scenario.name);
                }
            }
        }
        scenario.sealed = true;
    }

    public Scenario get(String name) {
        Scenario scenario = scenarios.get(name);
        if (scenario == null) {
            throw new IllegalArgumentException("未知场景: " + name);
        }
        return scenario;
    }

    public java.util.List<String> names() {
        return java.util.List.copyOf(scenarios.keySet());
    }
}
