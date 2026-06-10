package cn.chyuan.ai.observability.trigger.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthKeyInterceptor authKeyInterceptor;

    /**
     * 是否对 eval 接口启用鉴权。
     * 默认 true（生产保持拦截）；dev 环境显式设为 false 放行，便于前端联调。
     * collect 接口为网关真实上报通道，始终强制鉴权，不受此开关影响。
     */
    @Value("${observability.auth.eval-enabled:true}")
    private boolean evalAuthEnabled;

    public WebMvcConfig(AuthKeyInterceptor authKeyInterceptor) {
        this.authKeyInterceptor = authKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> patterns = new ArrayList<>();
        patterns.add("/api/v1/collect/**");
        if (evalAuthEnabled) {
            patterns.add("/api/v1/eval/**");
        } else {
            log.warn("eval 接口鉴权已关闭（observability.auth.eval-enabled=false），仅限非生产环境使用");
        }
        registry.addInterceptor(authKeyInterceptor)
                .addPathPatterns(patterns.toArray(new String[0]));
    }
}
