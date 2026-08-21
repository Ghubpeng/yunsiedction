package com.yunsie.boot.web;

import com.yunsie.common.api.Result;
import com.yunsie.common.error.CommonErrorCode;
import com.yunsie.common.error.ErrorCodeRange;
import com.yunsie.common.error.PermissionErrorCode;
import com.yunsie.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（api-design：统一响应 Result；backend-development：禁止吞异常）。
 *
 * <p>HTTP 层约定（api-design）：
 * 业务结果一律 200 + code；权限类错误（2xxxx）返回 403；认证由 Security 入口点返回 401。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 → 对应错误码；2xxxx（权限/越权）→ HTTP 403，其余 → HTTP 200 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status = (e.getCode() >= ErrorCodeRange.PERMISSION_MIN && e.getCode() <= ErrorCodeRange.PERMISSION_MAX)
                ? HttpStatus.FORBIDDEN
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 方法级权限校验失败（@PreAuthorize）→ 403 + 20001 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(PermissionErrorCode.NO_PERMISSION));
    }

    /** Bean Validation（@RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse(CommonErrorCode.PARAM_INVALID.message());
        return Result.fail(CommonErrorCode.PARAM_INVALID.code(), msg);
    }

    /** 请求参数不可读 / 缺失 / 请求参数校验（@Min/@Max 等） */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class})
    public Result<Void> handleBadRequest(Exception e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID.code(), CommonErrorCode.PARAM_INVALID.message());
    }

    /** 兜底：记录日志，不向客户端泄露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("unhandled exception", e);
        return Result.fail(CommonErrorCode.SYSTEM_ERROR.code(), CommonErrorCode.SYSTEM_ERROR.message());
    }
}
