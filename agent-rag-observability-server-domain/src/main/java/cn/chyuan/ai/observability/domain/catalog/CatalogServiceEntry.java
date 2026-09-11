package cn.chyuan.ai.observability.domain.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 能力目录配置（工单 0184 Z1，借鉴 Backstage software catalog）— 静态清单由配置 JSON 注入
 * （catalog.json 结构：services[].{name, description, endpoints[], components[]}），
 * 动态健康面由探针聚合，scorecard = 静态能力 × 实时状态。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogServiceEntry {

    private String name;
    private String description;
    /** 对外端口（展示用） */
    private Integer port;
    /** 能力端点簇（如 eval/patrol/dlq） */
    private List<String> capabilities;
    /** 依赖组件（db/es/mq/vector…，与 HealthProbe.name 对应） */
    private List<String> components;
}
