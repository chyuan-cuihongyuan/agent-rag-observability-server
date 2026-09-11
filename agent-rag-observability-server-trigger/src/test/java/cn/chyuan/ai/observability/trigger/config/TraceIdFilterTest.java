package cn.chyuan.ai.observability.trigger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void propagatesUpstreamTraceIdHeaderIntoMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query/traces");
        request.addHeader(TraceIdFilter.HEADER_TRACE_ID, "upstream-trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcInsideChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID)));

        assertThat(mdcInsideChain.get()).isEqualTo("upstream-trace-001");
        assertThat(response.getHeader(TraceIdFilter.HEADER_TRACE_ID)).isEqualTo("upstream-trace-001");
        // 请求结束必须清理，防线程池串号
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void generatesTraceIdWhenUpstreamHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query/traces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcInsideChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID)));

        assertThat(mdcInsideChain.get()).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.HEADER_TRACE_ID)).isEqualTo(mdcInsideChain.get());
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void rejectsMalformedHeaderAndGeneratesInstead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query/traces");
        // 含换行的头视为日志注入尝试：丢弃并自生成
        request.addHeader(TraceIdFilter.HEADER_TRACE_ID, "evil\ninjection");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcInsideChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID)));

        assertThat(mdcInsideChain.get()).isNotBlank().doesNotContain("\n");
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void mdcClearedEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/query/traces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new RuntimeException("boom");
            });
        } catch (Exception expected) {
            // 过滤器不吞业务异常，只保证清理
        }
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }
}
