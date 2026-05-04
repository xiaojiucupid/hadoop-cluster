package com.cev.movie.types.exception;

/**
 * 业务错误码抽象。
 *
 * <p>不同业务枚举只要实现该接口，就可以被统一异常和统一响应复用。</p>
 */
public interface ErrorCode {

    /**
     * 获取错误码。
     */
    String getCode();

    /**
     * 获取错误提示。
     */
    String getMessage();
}
