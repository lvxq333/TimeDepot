package com.couple.api.handler;

import com.couple.common.exception.BusinessException;
import com.couple.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.stream.Collectors;

/**
 * MVC 请求的全局异常处理入口，统一实际 HTTP 状态与 ApiResponse.code。
 * <p>处理自定义业务异常、参数校验异常及 Spring MVC 框架异常；
 * 未知异常返回固定的 500 提示，详细堆栈只写入服务端日志。
 * 不负责捕获后台任务或 MVC 处理链之外的异常。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 BusinessException 及其子类，保留业务代码提供的状态和提示。
     * @param ex 业务层或拦截器抛出的可预期异常
     * @return 状态码与响应体 code 一致的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex) {
        return failure(ex.getStatus(), ex.getMessage(), new HttpHeaders());
    }

    /**
     * 处理 Bean Validation 约束异常，例如方法参数违反 NotBlank、Min 等约束。
     * <p>提取属性路径和校验提示，不输出被拒绝的参数值，返回 HTTP 400。
     * 请求 DTO 的 MethodArgumentNotValidException 由 MVC 异常入口处理。</p>
     * @param ex 包含各项约束违规信息的异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + "：" + v.getMessage())
                .sorted().collect(Collectors.joining("；"));
        return failure(HttpStatus.BAD_REQUEST, message, new HttpHeaders());
    }

    /**
     * 覆盖父类的 MVC 异常响应构建入口。
     * <p>常见情况包括参数缺失/类型错误/JSON 错误（400）、资源不存在（404）、
     * 方法不支持（405）、媒体类型不支持（415）。保留框架状态和响应头，
     * 并对 DTO 校验异常提取具体字段提示，替换框架默认响应体。</p>
     * @param ex Spring MVC 已识别的异常
     * @param body 框架提供的默认响应体，此实现使用 ApiResponse 替换
     * @param headers 框架要求保留的响应头，例如 405 的 Allow
     * @param statusCode 框架确定的 HTTP 状态
     * @param request 当前请求上下文
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = switch (status) {
            case BAD_REQUEST -> "请求参数或请求体格式错误";
            case NOT_FOUND -> "资源不存在";
            case METHOD_NOT_ALLOWED -> "请求方法不支持";
            case NOT_ACCEPTABLE -> "不支持请求的响应格式";
            case UNSUPPORTED_MEDIA_TYPE -> "请求内容类型不支持";
            case SERVICE_UNAVAILABLE -> "服务暂不可用";
            default -> status.is5xxServerError() ? "服务器内部错误" : status.getReasonPhrase();
        };
        if (ex instanceof MethodArgumentNotValidException validation) {
            message = validation.getBindingResult().getAllErrors().stream()
                    .map(error -> error instanceof org.springframework.validation.FieldError field
                            ? field.getField() + "：" + field.getDefaultMessage()
                            : error.getDefaultMessage())
                    .collect(Collectors.joining("；"));
        }
        if (status.is5xxServerError()) log.error("MVC 请求处理失败", ex);
        return failure(status, message, headers);
    }

    /**
     * 兜底处理未被更具体处理器接管的异常，例如空指针或未处理的数据访问错误。
     * <p>记录完整堆栈，客户端只收到“服务器内部错误”，避免暴露内部信息。</p>
     * @param ex 未预期的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnknown(Exception ex) {
        log.error("未处理的服务器异常", ex);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", new HttpHeaders());
    }

    /**
     * 统一构建失败响应，同时设置 HTTP 状态、响应头和 JSON 响应体。
     * @param status 标准 HTTP 错误状态；401 会补充 Bearer 认证挑战头
     * @param message 可安全展示给用户的提示
     * @param headers 需要保留的响应头，复制后修改，不改变传入对象
     */
    private ResponseEntity<Object> failure(HttpStatus status, String message, HttpHeaders headers) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        if (status == HttpStatus.UNAUTHORIZED) {
            responseHeaders.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return new ResponseEntity<>(ApiResponse.failure(status, message), responseHeaders, status);
    }
}
