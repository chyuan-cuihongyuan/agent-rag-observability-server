package cn.chyuan.ai.observability.trigger.config;

import cn.chyuan.ai.observability.types.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常 → HTTP 状态 + code 契约测试（SELFLOOP2 loop-202）。
 * 用抛错探针 controller 触发各异常类型，只断言外部可见行为（status/code/info）——
 * 前端依赖的 E4xx/E5xx 错误码族由本测试锁定，映射表一旦变化即红。
 */
@DisplayName("GlobalExceptionHandler 异常映射契约")
class GlobalExceptionHandlerContractTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/illegal")
        Response<String> illegal() {
            throw new IllegalArgumentException("非法参数");
        }

        @GetMapping("/probe/unexpected")
        Response<String> unexpected() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/probe/body")
        Response<String> body(@RequestBody java.util.Map<String, Object> body) {
            return Response.success(String.valueOf(body));
        }

        @GetMapping("/probe/type-mismatch/{id}")
        Response<String> typeMismatch(@PathVariable int id) {
            return Response.success(String.valueOf(id));
        }

        @GetMapping("/probe/missing-param")
        Response<String> missingParam(@RequestParam("required") String value,
                                      @RequestHeader(value = "X-Ignore", required = false) String ignore) {
            return Response.success(value);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 + E400 + 原始消息透传")
    void illegalArgument_mapsTo400() throws Exception {
        mvc.perform(get("/probe/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E400"))
                .andExpect(jsonPath("$.info").value("非法参数"));
    }

    @Test
    @DisplayName("畸形 JSON 请求体 → 400 + E400（HttpMessageNotReadable）")
    void malformedBody_mapsTo400() throws Exception {
        mvc.perform(post("/probe/body").contentType("application/json").content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E400"))
                .andExpect(jsonPath("$.info").value("请求体格式错误或不可读"));
    }

    @Test
    @DisplayName("路径参数类型不匹配 → 400 + E400（TypeMismatch）")
    void typeMismatch_mapsTo400() throws Exception {
        mvc.perform(get("/probe/type-mismatch/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E400"));
    }

    @Test
    @DisplayName("缺少必填参数 → 400 + E400 + 参数名提示")
    void missingParam_mapsTo400() throws Exception {
        mvc.perform(get("/probe/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E400"))
                .andExpect(jsonPath("$.info").value(matchesPattern("缺少必填参数: required.*")));
    }

    @Test
    @DisplayName("方法不支持 → 405 + E405")
    void methodNotSupported_mapsTo405() throws Exception {
        mvc.perform(post("/probe/illegal"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("E405"));
    }

    @Test
    @DisplayName("媒体类型不支持 → 415 + E415")
    void mediaTypeNotSupported_mapsTo415() throws Exception {
        mvc.perform(post("/probe/body").contentType("text/plain").content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("E415"));
    }

    @Test
    @DisplayName("未知异常 → 500 + E500（兜底不变，消息不泄露内部细节）")
    void unexpected_mapsTo500() throws Exception {
        mvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("E500"))
                .andExpect(jsonPath("$.info").value("服务内部错误"));
    }

    @Test
    @DisplayName("NoResourceFound → E404 + 资源不存在（直接调用；@ResponseStatus 注解锁定 404）")
    void notFound_mapsTo404() throws NoSuchMethodException {
        // standalone MockMvc 对未知路径不抛 NoResourceFoundException（容器行为），
        // 故直接调用 handler 断言响应体，HTTP 404 由方法上的 @ResponseStatus 注解保证。
        Response<String> resp = new GlobalExceptionHandler()
                .handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/missing", "not found"));
        assertEquals("E404", resp.getCode());
        assertEquals("资源不存在", resp.getInfo());
        ResponseStatus rs = GlobalExceptionHandler.class
                .getMethod("handleNotFound", NoResourceFoundException.class)
                .getAnnotation(ResponseStatus.class);
        assertEquals(HttpStatus.NOT_FOUND, rs.value());
    }
}
