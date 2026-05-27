package cn.chyuan.ai.observability.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SourceService {
    AGENT("agent", "后端Agent服务"),
    GATEWAY("gateway", "MCP网关服务"),
    BUSINESS("business", "业务服务");

    private final String code;
    private final String desc;
}
