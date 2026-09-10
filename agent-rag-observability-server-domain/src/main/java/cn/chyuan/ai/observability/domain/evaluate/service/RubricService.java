package cn.chyuan.ai.observability.domain.evaluate.service;

import cn.chyuan.ai.observability.domain.evaluate.adapter.repository.IRubricRepository;
import cn.chyuan.ai.observability.domain.evaluate.model.entity.RubricEntity;
import cn.chyuan.ai.observability.domain.evaluate.model.valobj.RubricDimension;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rubric 服务（工单 0133 R1）— 评判标准配置化的 CRUD 与解析：
 * <ul>
 *   <li>校验：维度 key 唯一、权重和=1（±1e-6）、dimensions 非法 JSON 结构化拒绝</li>
 *   <li>内置种子：9 个收编 prompt 组成 5 份内置 Rubric，懒加载 ensureBuiltin 幂等（name 冲突跳过）</li>
 *   <li>权重解析：evalType → 启用中 Rubric 的维度权重表（仓储异常时回退内置兜底口径）</li>
 * </ul>
 */
@Slf4j
@Service
public class RubricService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double WEIGHT_EPSILON = 1e-6;

    private final IRubricRepository rubricRepository;

    /** 内置种子是否已在本进程尝试过（懒加载，只跑一次；失败下次调用重试） */
    private volatile boolean seeded = false;
    /** 评判模板缓存（key → {{}} 模板），Rubric 变更时失效 */
    private volatile Map<String, String> templateCache;

    public RubricService(IRubricRepository rubricRepository) {
        this.rubricRepository = rubricRepository;
    }

    // ===== CRUD =====

    /** 新建 Rubric：校验通过后落库；重名拒绝 */
    public RubricEntity create(RubricEntity entity) {
        if (entity == null || isBlank(entity.getName())) {
            throw new IllegalArgumentException("Rubric name 不能为空");
        }
        if (entity.getBuiltin() != null && entity.getBuiltin()) {
            throw new IllegalArgumentException("builtin 为内置种子保留，不允许自建内置 Rubric");
        }
        List<RubricDimension> dims = parseAndValidateDimensions(entity.getDimensionsJson());
        if (rubricRepository.queryByName(entity.getName()) != null) {
            throw new IllegalArgumentException("Rubric 名称已存在: " + entity.getName());
        }
        entity.setRubricId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entity.setDimensions(dims);
        entity.setDimensionsJson(serialize(dims));
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion());
        entity.setEnabled(entity.getEnabled() == null || entity.getEnabled());
        entity.setBuiltin(false);
        String now = LocalDateTime.now().format(FMT);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        rubricRepository.insert(entity);
        invalidateCache();
        return entity;
    }

    /** 更新 Rubric：内置种子不可改；校验同新建 */
    public void update(RubricEntity entity) {
        if (entity == null || isBlank(entity.getRubricId())) {
            throw new IllegalArgumentException("rubricId 不能为空");
        }
        RubricEntity existing = rubricRepository.queryByRubricId(entity.getRubricId());
        if (existing == null) {
            throw new IllegalArgumentException("Rubric 不存在: " + entity.getRubricId());
        }
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("内置 Rubric 不可修改: " + existing.getName());
        }
        List<RubricDimension> dims = parseAndValidateDimensions(entity.getDimensionsJson());
        // name 变更时查重
        String newName = isBlank(entity.getName()) ? existing.getName() : entity.getName();
        if (!newName.equals(existing.getName())) {
            RubricEntity byName = rubricRepository.queryByName(newName);
            if (byName != null && !byName.getRubricId().equals(existing.getRubricId())) {
                throw new IllegalArgumentException("Rubric 名称已存在: " + newName);
            }
        }
        entity.setName(newName);
        entity.setDimensions(dims);
        entity.setDimensionsJson(serialize(dims));
        entity.setEvalType(isBlank(entity.getEvalType()) ? existing.getEvalType() : entity.getEvalType());
        entity.setEnabled(entity.getEnabled() == null ? existing.getEnabled() : entity.getEnabled());
        entity.setUpdateTime(LocalDateTime.now().format(FMT));
        rubricRepository.update(entity);
        invalidateCache();
    }

    public void delete(String rubricId) {
        RubricEntity existing = rubricRepository.queryByRubricId(rubricId);
        if (existing == null) {
            throw new IllegalArgumentException("Rubric 不存在: " + rubricId);
        }
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("内置 Rubric 不可删除: " + existing.getName());
        }
        // 仓储无 delete 语句语义时以停用代替删除（内置权重兜底不受影响）
        existing.setEnabled(false);
        existing.setUpdateTime(LocalDateTime.now().format(FMT));
        rubricRepository.update(existing);
        invalidateCache();
    }

    public RubricEntity query(String rubricId) {
        return rubricRepository.queryByRubricId(rubricId);
    }

    public List<RubricEntity> queryList(int page, int size) {
        return rubricRepository.queryList(page, size);
    }

    // ===== 维度校验 =====

    /**
     * 解析并校验 dimensions JSON：
     * <ul>
     *   <li>非法 JSON / 非数组 / 元素缺 key → 结构化拒绝（带原因的 IllegalArgumentException）</li>
     *   <li>维度 key 重复 → 拒绝</li>
     *   <li>权重和 ≠ 1（±1e-6）→ 拒绝</li>
     * </ul>
     */
    public List<RubricDimension> parseAndValidateDimensions(String dimensionsJson) {
        if (isBlank(dimensionsJson)) {
            throw new IllegalArgumentException("dimensions 不能为空（JSON 数组 [{key,label,weight,judgePrompt,binary}]）");
        }
        List<RubricDimension> dims;
        try {
            dims = JSON.parseArray(dimensionsJson, RubricDimension.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("dimensions 非法 JSON: " + e.getMessage());
        }
        if (dims == null || dims.isEmpty()) {
            throw new IllegalArgumentException("dimensions 不能为空数组");
        }
        Set<String> keys = new HashSet<>();
        double sum = 0.0;
        for (int i = 0; i < dims.size(); i++) {
            RubricDimension d = dims.get(i);
            if (d == null || isBlank(d.getKey())) {
                throw new IllegalArgumentException("dimensions[" + i + "] 缺少 key");
            }
            if (!keys.add(d.getKey())) {
                throw new IllegalArgumentException("维度 key 重复: " + d.getKey());
            }
            if (d.getWeight() == null || d.getWeight() < 0 || d.getWeight() > 1) {
                throw new IllegalArgumentException("维度 " + d.getKey() + " 权重非法（需 0-1）: " + d.getWeight());
            }
            sum += d.getWeight() == null ? 0.0 : d.getWeight();
        }
        if (Math.abs(sum - 1.0) > WEIGHT_EPSILON) {
            throw new IllegalArgumentException("维度权重和必须为 1，实际为: " + sum);
        }
        return dims;
    }

    private String serialize(List<RubricDimension> dims) {
        return JSON.toJSONString(dims);
    }

    // ===== 内置种子（懒加载幂等） =====

    /** 懒加载内置种子：本进程首次访问时执行一次，name 已存在自动跳过（幂等） */
    public synchronized void ensureBuiltin() {
        if (seeded) {
            return;
        }
        int seededCount = 0;
        for (RubricEntity builtin : BuiltinRubrics.builtinRubrics()) {
            try {
                String now = LocalDateTime.now().format(FMT);
                builtin.setCreateTime(now);
                builtin.setUpdateTime(now);
                builtin.setDimensionsJson(serialize(builtin.getDimensions()));
                if (rubricRepository.seedBuiltinIfAbsent(builtin)) {
                    seededCount++;
                }
            } catch (Exception e) {
                // 库不可用/表未建时降级为内置兜底口径，不阻断评测主链路
                log.warn("内置 Rubric 种子跳过 {}: {}", builtin.getName(), e.getMessage());
            }
        }
        seeded = true;
        if (seededCount > 0) {
            log.info("内置 Rubric 已种子 {} 份（幂等，已存在自动跳过）", seededCount);
        }
    }

    // ===== 权重/模板解析（评测执行链消费） =====

    /**
     * evalType → 综合分维度权重表：
     * 启用中 Rubric（用户自建优先于内置，版本高者优先）→ 仓储异常回退内置 v2 兜底口径。
     */
    public Map<String, Double> resolveWeights(String evalType) {
        try {
            ensureBuiltin();
            RubricEntity rubric = rubricRepository.queryEnabledByEvalType(evalType);
            if (rubric != null && rubric.getDimensions() != null && !rubric.getDimensions().isEmpty()) {
                Map<String, Double> weights = new LinkedHashMap<>();
                rubric.getDimensions().forEach(d -> {
                    if (d.getWeight() != null) {
                        weights.put(d.getKey(), d.getWeight());
                    }
                });
                return weights;
            }
        } catch (Exception e) {
            log.warn("Rubric 权重解析失败，回退内置兜底口径: {}", e.getMessage());
        }
        return BuiltinRubrics.defaultWeights(evalType);
    }

    /**
     * 9 个 LLM 评判维度的 prompt 模板（key → {{}} 模板）。
     * 来源：启用中 Rubric 的带 prompt 维度，仓储缺失的 key 用内置模板补齐（保证 9 维始终可评）。
     * 结果缓存，Rubric 变更时失效。
     */
    public Map<String, String> resolveJudgePromptTemplates() {
        Map<String, String> cache = templateCache;
        if (cache != null) {
            return cache;
        }
        Map<String, String> templates = new LinkedHashMap<>();
        try {
            ensureBuiltin();
            for (RubricEntity rubric : rubricRepository.queryList(1, 200)) {
                if (rubric.getDimensions() == null || !Boolean.TRUE.equals(rubric.getEnabled())) {
                    continue;
                }
                for (RubricDimension d : rubric.getDimensions()) {
                    if (!isBlank(d.getJudgePrompt())) {
                        templates.putIfAbsent(d.getKey(), d.getJudgePrompt());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Rubric 评判模板加载失败，使用内置模板兜底: {}", e.getMessage());
        }
        // 内置模板兜底补齐（用户禁用某维度时仍保底可评，口径不缺维）
        BuiltinRubrics.JUDGE_TEMPLATES.forEach(templates::putIfAbsent);
        templateCache = templates;
        return templates;
    }

    private void invalidateCache() {
        templateCache = null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 便捷构造（测试用）：从维度列表构造 dimensions JSON */
    public static String dimensionsJsonOf(List<RubricDimension> dims) {
        return JSON.toJSONString(dims);
    }
}
