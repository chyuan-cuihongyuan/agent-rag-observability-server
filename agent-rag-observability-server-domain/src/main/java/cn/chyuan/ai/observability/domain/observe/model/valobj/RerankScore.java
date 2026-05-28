package cn.chyuan.ai.observability.domain.observe.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RerankScore implements Serializable {
    private String chunkId;
    private Double score;
    private Integer rank;
}
