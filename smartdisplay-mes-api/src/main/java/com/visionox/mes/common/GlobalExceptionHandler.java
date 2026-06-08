package com.visionox.mes.common;

import com.visionox.mes.system.audit.AuditFailureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditFailureService auditFailureService;

    /**
     * 业务异常处理。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {}", e.getMessage());
        auditFailureService.record(request, "BusinessException", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 表单参数校验异常处理。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String errorMsg = validationMessage(e);
        log.warn("参数校验失败: {}", errorMsg);
        auditFailureService.record(request, "BindException", 400, errorMsg);
        return Result.fail(400, "参数校验失败: " + errorMsg);
    }

    /**
     * JSON请求体参数校验异常处理。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMsg = validationMessage(e);
        log.warn("请求体校验失败: {}", errorMsg);
        auditFailureService.record(request, "MethodArgumentNotValidException", 400, errorMsg);
        return Result.fail(400, "参数校验失败: " + errorMsg);
    }

    /**
     * 其他异常处理。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: ", e);
        auditFailureService.record(request, e.getClass().getSimpleName(), 500, e.getMessage());
        return Result.fail("系统异常: " + e.getMessage());
    }

    private String validationMessage(BindException e) {
        return e.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining("; "));
    }

    private String validationMessage(MethodArgumentNotValidException e) {
        return e.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining("; "));
    }
}
