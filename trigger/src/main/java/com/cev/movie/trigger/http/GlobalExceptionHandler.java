package com.cev.movie.trigger.http;

import com.cev.movie.api.response.ApiResponse;
import com.cev.movie.types.common.ResultCode;
import com.cev.movie.types.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>统一拦截 Controller 抛出的异常，避免异常堆栈直接暴露给前端。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        LOGGER.warn("Business exception handled: code={}, message={}", exception.getCode(), exception.getMessage(), exception);
        return ApiResponse.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理未知系统异常。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        LOGGER.error("Unhandled system exception", exception);
        return ApiResponse.fail(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
    }
}
