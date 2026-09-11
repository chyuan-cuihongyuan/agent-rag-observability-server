package cn.chyuan.ai.observability.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void openApiMetadataDescribesServiceAndAuthContract() {
        OpenAPI openApi = new OpenApiConfig().observabilityOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Agent RAG Observability API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0");
        assertThat(openApi.getInfo().getDescription()).contains("监控评估");

        // 鉴权契约：头名必须与 AuthKeyInterceptor 实际读取的一致（auth-key）
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("observability-auth-key");
        assertThat(scheme).isNotNull();
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
        assertThat(scheme.getName()).isEqualTo("auth-key");
        assertThat(scheme.getIn()).isEqualTo(SecurityScheme.In.HEADER);
    }
}
