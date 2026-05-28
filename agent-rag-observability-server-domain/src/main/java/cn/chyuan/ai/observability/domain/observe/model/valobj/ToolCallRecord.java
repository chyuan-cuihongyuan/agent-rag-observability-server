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
public class ToolCallRecord implements Serializable {
    private String toolName;
    private String toolCallId;
    private String arguments;
    private Integer callOrder;
}
