package cn.chyuan.ai.observability.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 文档装配（D06）
 *
 * <p>提供 /v3/api-docs 与 /swagger-ui.html 两组端点；按控制器分包定义文档分组，
 * 访问密钥走 OpenAPI bearer 风格声明（observability.auth-key 请求头由网关注入，
 * 文档侧仅描述契约，不做校验）。</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI observabilityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agent RAG Observability API")
                        .description("Agent+RAG 全链路监控评估服务：Trace 采集/查询、质量评测、监控仪表盘")
                        .version("1.0")
                        .contact(new Contact().name("chyuan").url("https://github.com/chyuan-cuihongyuan"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes("observability-auth-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("auth-key")
                                        .description("网关上报通道访问密钥（observability.auth-key 契约，collect/eval 写接口强制）")));
    }
}
