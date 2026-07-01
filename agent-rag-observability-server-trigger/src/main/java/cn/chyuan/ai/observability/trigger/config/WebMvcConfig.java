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
        // 仪表盘只读聚合查询（quality_overview / result 对比 / task & dataset 列表）无敏感写操作，
        // 任何 profile 下均豁免鉴权，保证前端仪表盘正常展示；
        // seed/eval、collect、task 创建/执行等写操作仍受鉴权保护。
        List<String> excludes = new ArrayList<>();
        excludes.add("/api/v1/eval/quality_overview");
        excludes.add("/api/v1/eval/result/compare");
        registry.addInterceptor(authKeyInterceptor)
                .addPathPatterns(patterns.toArray(new String[0]))
                .excludePathPatterns(excludes.toArray(new String[0]));
    }
}
