package cn.chyuan.ai.observability.domain.catalog.service;

import cn.chyuan.ai.observability.domain.catalog.CatalogServiceEntry;
import cn.chyuan.ai.observability.domain.health.HealthAggregator;
import cn.chyuan.ai.observability.domain.health.HealthProbe;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力目录与计分卡服务（工单 0184 Z1，借鉴 Backstage catalog + Tech Insights）—
 * 静态能力清单（catalog.json 配置注入：{services:[{name,description,port,capabilities,components}]}）
 * × 动态健康面（探针结果）→ scorecard。
 * 计分纯函数：依赖组件 DOWN 时——db 关键依赖记 0（DOWN），其余记 40（DEGRADED）；无探针不惩罚。
 */
@Slf4j
@Service
public class CapabilityCatalogService {

    @Value("${catalog.json:{\"services\":[{\"name\":\"agent-rag-observability-server\",\"description\":\"监控评估后端\",\"port\":8092,\"capabilities\":[\"trace\",\"eval\",\"patrol\",\"mining\",\"dlq\",\"slo\",\"catalog\"],\"components\":[\"db\",\"es\",\"mq\"]},{\"name\":\"mcp-gateway-agent\",\"description\":\"AI 治理网关\",\"port\":8099,\"capabilities\":[\"mcp-proxy\",\"llm-compat\",\"guardrail\",\"cost\",\"breaker\",\"flags\"],\"components\":[\"db\"]},{\"name\":\"aggregation-support-agent\",\"description\":\"AIOps 智能体后端\",\"port\":8091,\"capabilities\":[\"rag\",\"hybrid-search\",\"rerank\",\"rewrite\",\"quota\",\"citation\"],\"components\":[\"db\",\"vector\"]}]}")
    private String catalogJson;

    private final List<HealthProbe> probes;

    public CapabilityCatalogService(List<HealthProbe> probes) {
        this.probes = probes;
    }

    /** 解析目录配置 JSON（catalog.services 数组；解析失败按空目录） */
    public List<CatalogServiceEntry> parseCatalog(String json) {
        try {
            Map<String, Object> root = JSON.parseObject(json);
            Object services = root == null ? null : root.get("services");
            if (services == null) {
                return List.of();
            }
            return JSON.parseArray(JSON.toJSONString(services), CatalogServiceEntry.class);
        } catch (Exception e) {
            log.warn("能力目录配置解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 计分卡纯函数：每个能力项按其依赖组件健康给分（0-100）；
     * 组件无探针结果视为健康（不惩罚可选组件）。
     */
    public List<Map<String, Object>> scorecard(List<CatalogServiceEntry> entries,
                                               Map<String, HealthProbe.ProbeResult> healthByComponent) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CatalogServiceEntry e : entries) {
            long score = 100;
            String status = "UP";
            List<String> deps = e.getComponents() == null ? List.of() : e.getComponents();
            for (String c : deps) {
                HealthProbe.ProbeResult r = healthByComponent.get(c);
                if (r == null || r.up()) {
                    continue;
                }
                // 该组件 DOWN：关键依赖（db）直接 0，其余 40
                score = "db".equals(c) ? 0 : Math.min(score, 40);
            }
            if (score < 100) {
                status = score == 0 ? "DOWN" : "DEGRADED";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("service", e.getName());
            row.put("description", e.getDescription());
            row.put("capabilities", e.getCapabilities());
            row.put("components", deps);
            row.put("status", status);
            row.put("score", score);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 计分卡 v2（工单 0245 AF9，借鉴 Backstage Tech Insights）——能力 × 质量门加权：
     * 每能力四门：tests(40)/docs(30)/contract(20)/defaultOff(10)，
     * quality 由 catalog.json 每服务 quality 字段注入
     * （{能力名: {tests:true,docs:"docs/..",contract:true,defaultOff:true}}，缺门记 0）。
     * 服务级 v2 分 = 能力 v2 分均值；纯函数可测。
     */
    public Map<String, Object> scorecardV2(List<CatalogServiceEntry> entries,
                                           Map<String, Map<String, Map<String, Object>>> qualityByService) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CatalogServiceEntry e : entries) {
            List<String> capabilities = e.getCapabilities() == null ? List.of() : e.getCapabilities();
            Map<String, Map<String, Object>> quality =
                    qualityByService.getOrDefault(e.getName(), Map.of());
            List<Map<String, Object>> capabilityRows = new ArrayList<>();
            long capabilitySum = 0;
            for (String capability : capabilities) {
                Map<String, Object> gates = quality.getOrDefault(capability, Map.of());
                long tests = truthy(gates.get("tests")) ? 40 : 0;
                long docs = truthy(gates.get("docs")) ? 30 : 0;
                long contract = truthy(gates.get("contract")) ? 20 : 0;
                long defaultOff = truthy(gates.get("defaultOff")) ? 10 : 0;
                long total = tests + docs + contract + defaultOff;
                capabilitySum += total;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("capability", capability);
                row.put("gates", Map.of("tests", tests, "docs", docs,
                        "contract", contract, "defaultOff", defaultOff));
                row.put("score", total);
                capabilityRows.add(row);
            }
            long serviceScore = capabilities.isEmpty() ? 0 : capabilitySum / capabilities.size();
            Map<String, Object> serviceRow = new LinkedHashMap<>();
            serviceRow.put("service", e.getName());
            serviceRow.put("capabilities", capabilityRows);
            serviceRow.put("scoreV2", serviceScore);
            rows.add(serviceRow);
        }
        long overall = rows.isEmpty() ? 0
                : rows.stream().mapToLong(r -> (Long) r.get("scoreV2")).sum() / rows.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("services", rows);
        out.put("overallScoreV2", overall);
        return out;
    }

    /** 质量门取值判定：Boolean true / 字符串非空非 "false" 即达标 */
    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"false".equalsIgnoreCase(s);
        }
        return false;
    }

    /** 端点装配：探测 + 计分 + 总分 */
    public Map<String, Object> buildScorecard() {
        Map<String, HealthProbe.ProbeResult> health = new LinkedHashMap<>();
        for (HealthProbe probe : probes) {
            try {
                health.put(probe.name(), probe.probe());
            } catch (Exception e) {
                health.put(probe.name(), HealthProbe.ProbeResult.fail(probe.name(), 0, e.getMessage()));
            }
        }
        List<Map<String, Object>> rows = scorecard(parseCatalog(catalogJson), health);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("services", rows);
        result.put("health", health.values().stream().map(HealthAggregator::toMap).toList());
        result.put("overallScore", rows.isEmpty() ? 0L
                : rows.stream().mapToLong(r -> (Long) r.get("score")).sum() / rows.size());
        return result;
    }
}
