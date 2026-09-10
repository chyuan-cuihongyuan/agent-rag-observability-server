package cn.chyuan.ai.observability.domain.evaluate.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalDatasetEntity {
    private String datasetId;
    private String datasetName;
    private String description;
    private Integer itemCount;
    private String itemsJson;
    private String createTime;
    private String updateTime;

    // ========== 新增：版本化 + 三池 + 来源 + 冻结（工单 0134 R2） ==========
    /** 版本号（同 datasetName 递增，快照复制产生新版本） */
    private Integer version;
    /** 样本池：golden 黄金集 / challenge 挑战集 / wrong 错题集；NULL=未分类（存量兼容） */
    private String pool;
    /** 来源标记：trace 沉淀 / manual 人工录入 / seed 种子 */
    private String source;
    /** 版本冻结位：true 后该版本条目不可改（不变式） */
    private Boolean frozen;
}
