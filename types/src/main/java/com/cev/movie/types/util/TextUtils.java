package com.cev.movie.types.util;

/**
 * 文本工具类。
 *
 * <p>只放置不依赖 Spring 容器、可跨模块复用的轻量级通用方法。</p>
 */
public final class TextUtils {

    private TextUtils() {
        // 工具类不允许实例化。
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断文本
     * @return null、空字符串或纯空白字符均返回 true
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
