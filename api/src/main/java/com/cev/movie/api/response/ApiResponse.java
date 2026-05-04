package com.cev.movie.api.response;

import com.cev.movie.types.common.ResultCode;

/**
 * REST 接口统一响应体。
 *
 * <p>Web 端、Android 端和后续 AI Agent 工具调用都通过该结构获取结果，
 * 便于前端统一处理成功、失败和异常提示。</p>
 *
 * @param code 业务状态码
 * @param message 响应提示信息
 * @param data 业务数据
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
