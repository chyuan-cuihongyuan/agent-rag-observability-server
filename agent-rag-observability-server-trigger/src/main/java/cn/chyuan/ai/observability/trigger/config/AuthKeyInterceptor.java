package cn.chyuan.ai.observability.trigger.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AuthKeyInterceptor implements HandlerInterceptor {

    @Value("${observability.auth-key}")
    private String expectedAuthKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (expectedAuthKey.isEmpty()) {
            return true;
        }

        String authKey = request.getHeader("auth-key");
        if (expectedAuthKey.equals(authKey)) {
            return true;
        }

        log.warn("auth-key 校验失败, uri={}, remote={}", request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(401);
        return false;
    }
}
