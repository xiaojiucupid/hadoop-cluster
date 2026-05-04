package com.cev.movie.types.common;

import com.cev.movie.types.exception.ErrorCode;

/**
 * 统一结果码枚举。
 *
 * <p>该枚举用于在接口响应、业务异常、日志追踪中保持统一的状态表达，
 * 避免各层直接散落魔法数字和不可维护的错误文案。</p>
 */
public enum ResultCode implements ErrorCode {

    /** 请求处理成功。 */
    SUCCESS("0000", "处理成功"),

    /** 请求参数不符合业务或格式要求。 */
    BAD_REQUEST("0400", "请求参数错误"),

    /** 用户未登录或登录态无效。 */
    UNAUTHORIZED("0401", "用户未认证"),

    /** 服务端发生未知异常。 */
    SYSTEM_ERROR("0500", "系统繁忙，请稍后再试");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
