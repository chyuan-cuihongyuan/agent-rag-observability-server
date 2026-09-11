package cn.chyuan.ai.observability.trigger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 入口 traceId 过滤器（D08，借鉴 OTel W3C trace-context 的头透传语义）
 *
 * <p>每个请求：优先透传上游 X-Trace-Id 头（网关/调用方已建链路），否则自生成；
 * 写入 MDC 供日志 pattern 输出（%X{traceId}），并回写响应头供调用方关联。
 * 请求结束清理 MDC，防止线程池复用导致的串号。</p>
 *
 * <p>与 MQ 消费端（TraceMessageConsumer 的 MDC_TRACE_ID）使用同一 MDC key "traceId"，
 * HTTP 与 MQ 两条入口的日志自此可用同一 traceId 检索。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
    private static final int MAX_TRACE_ID_LEN = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(HEADER_TRACE_ID));
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /**
     * 上游头只放行字母数字与短横线，防日志注入（换行/控制字符污染日志行）
     */
    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_TRACE_ID_LEN) {
            trimmed = trimmed.substring(0, MAX_TRACE_ID_LEN);
        }
        return trimmed.matches("[A-Za-z0-9\\-]+") ? trimmed : null;
    }
}
