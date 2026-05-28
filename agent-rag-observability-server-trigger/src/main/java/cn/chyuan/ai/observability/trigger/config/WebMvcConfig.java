package cn.chyuan.ai.observability.trigger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthKeyInterceptor authKeyInterceptor;

    public WebMvcConfig(AuthKeyInterceptor authKeyInterceptor) {
        this.authKeyInterceptor = authKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authKeyInterceptor)
                .addPathPatterns("/api/v1/collect/**")
                .addPathPatterns("/api/v1/eval/**");
    }
}
