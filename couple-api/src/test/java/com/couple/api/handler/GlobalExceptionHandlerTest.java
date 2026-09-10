package com.couple.api.handler;

import com.couple.common.exception.BusinessException;
import com.couple.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 验证全局异常处理器的状态码、响应体和敏感信息保护行为。 */
class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    /** 构建只包含测试控制器和全局异常处理器的轻量 MVC 环境。 */
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 验证业务异常的 HTTP 状态码与统一响应体 code 保持一致。 */
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 405, 409, 429, 500, 501, 503})
    void businessStatusMatchesBody(int code) throws Exception {
        mvc.perform(get("/test/business").param("status", String.valueOf(code)))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("具体错误提示"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /** 缺少必填请求参数时应返回 400。 */
    @Test
    void missingParameterIs400() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    /** 不支持的请求方法应返回 405，并保留 Allow 响应头。 */
    @Test
    void unsupportedMethodIs405AndKeepsAllowHeader() throws Exception {
        mvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value(405));
    }

    /** 未知异常对外只暴露通用提示，不泄露内部异常详情。 */
    @Test
    void unknownExceptionDoesNotExposeDetails() throws Exception {
        mvc.perform(get("/test/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    /** 失败响应只允许使用错误状态码。 */
    @Test
    void failureRequiresErrorStatus() {
        assertThrows(IllegalArgumentException.class, () -> ApiResponse.failure(HttpStatus.OK));
        assertEquals(401, ApiResponse.failure(HttpStatus.UNAUTHORIZED).getCode());
    }

    /** 提供测试用接口，模拟业务异常和未处理异常。 */
    @RestController
    static class TestController {
        /** 根据请求参数抛出指定状态的业务异常。 */
        @GetMapping("/test/business")
        public void business(@RequestParam("status") int status) {
            throw new BusinessException(HttpStatus.valueOf(status), "具体错误提示");
        }

        /** 抛出未预期异常，验证兜底处理逻辑。 */
        @GetMapping("/test/unknown")
        public void unknown() {
            throw new IllegalStateException("private database details");
        }
    }
}
