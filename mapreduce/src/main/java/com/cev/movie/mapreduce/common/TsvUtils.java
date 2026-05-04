package com.cev.movie.mapreduce.common;

/**
 * TSV 文本处理工具。
 *
 * <p>Sqoop 或 MySQL 导出 HDFS 时建议统一使用制表符分隔字段，MapReduce
 * 通过该工具解析和生成中间文本，避免重复处理空值、转义和数组越界。</p>
 */
public final class TsvUtils {

    private TsvUtils() {
    }

    public static String[] split(String line) {
        return line == null ? new String[0] : line.split("\\t", -1);
    }

    public static String value(String[] fields, int index) {
        if (fields == null || index < 0 || index >= fields.length) {
            return "";
        }
        return fields[index] == null ? "" : fields[index].trim();
    }

    public static long parseLong(String value, long defaultValue) {
        try {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static double parseDouble(String value, double defaultValue) {
        try {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
