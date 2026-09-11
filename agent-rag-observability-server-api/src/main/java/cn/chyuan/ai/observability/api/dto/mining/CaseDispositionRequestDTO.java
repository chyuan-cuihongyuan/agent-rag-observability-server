package cn.chyuan.ai.observability.api.dto.mining;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Case 批量处置请求 DTO（工单 0138 S2）— 回填错题集 / 忽略共用。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaseDispositionRequestDTO {
    /** 候选 ID 列表 */
    private List<Long> ids;
    /** 目标错题集名称（空则用默认「错题本」；仅回填时生效） */
    private String datasetName;
}
