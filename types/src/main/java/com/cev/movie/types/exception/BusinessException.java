package com.cev.movie.types.exception;

import com.cev.movie.types.common.ResultCode;

/**
 * 业务异常基类。
 *
 * <p>domain 层在发现业务规则不满足时抛出该异常，trigger 层统一捕获后
 * 转换为标准 API 响应，从而保证业务逻辑与 HTTP 表达解耦。</p>
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public String getCode() {
        return code;
    }
}
