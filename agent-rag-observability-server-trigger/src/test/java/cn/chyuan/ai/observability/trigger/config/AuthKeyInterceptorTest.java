package cn.chyuan.ai.observability.trigger.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthKeyInterceptor 鉴权契约测试（工单 1140）：
 * 配置为空一律拒绝；请求头 auth-key 与配置精确匹配才放行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthKeyInterceptor 鉴权契约")
class AuthKeyInterceptorTest {

    @Mock
    private Object handler;

    private AuthKeyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthKeyInterceptor();
    }

    private MockHttpServletRequest request(String uri, String authKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        if (authKey != null) {
            request.addHeader("auth-key", authKey);
        }
        return request;
    }

    @Test
    @DisplayName("auth-key 未配置时一律 401 拒绝")
    void missingConfigRejectsAll() {
        ReflectionTestUtils.setField(interceptor, "expectedAuthKey", "");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(request("/api/v1/collect", "anything"), response, handler);

        assertThat(pass).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("请求头与配置精确匹配时放行")
    void matchingKeyPasses() {
        ReflectionTestUtils.setField(interceptor, "expectedAuthKey", "dev-secret-key");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(request("/api/v1/collect", "dev-secret-key"), response, handler);

        assertThat(pass).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("请求头错误时 401 拒绝")
    void wrongKeyRejects() {
        ReflectionTestUtils.setField(interceptor, "expectedAuthKey", "dev-secret-key");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(request("/api/v1/collect", "wrong-key"), response, handler);

        assertThat(pass).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("缺少 auth-key 请求头时 401 拒绝")
    void missingHeaderRejects() {
        ReflectionTestUtils.setField(interceptor, "expectedAuthKey", "dev-secret-key");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(request("/api/v1/collect", null), response, handler);

        assertThat(pass).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
