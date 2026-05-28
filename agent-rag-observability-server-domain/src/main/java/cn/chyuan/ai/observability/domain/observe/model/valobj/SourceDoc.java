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
public class SourceDoc implements Serializable {
    private String documentId;
    private String chunkId;
    private Double score;
    private String retrievalType;
    private String snippet;
}
