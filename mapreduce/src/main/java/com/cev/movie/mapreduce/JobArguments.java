package com.cev.movie.mapreduce;

import org.apache.hadoop.conf.Configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * MapReduce 命令行参数解析器。
 *
 * <p>参数格式统一为 {@code --key=value}，方便在云服务器 Shell 脚本中传入
 * HDFS 路径、MySQL 地址、用户名密码和需要执行的 Job 类型。</p>
 */
public class JobArguments {

    private final Map<String, String> values;

    private JobArguments(Map<String, String> values) {
        this.values = values;
    }

    public static JobArguments parse(String[] args) {
        Map<String, String> parsed = new HashMap<>();
        if (args == null) {
            return new JobArguments(Collections.emptyMap());
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            int splitIndex = arg.indexOf('=');
            if (splitIndex <= 2) {
                parsed.put(arg.substring(2), "true");
            } else {
                parsed.put(arg.substring(2, splitIndex), arg.substring(splitIndex + 1));
            }
        }
        return new JobArguments(parsed);
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public void applyTo(Configuration configuration) {
        values.forEach((key, value) -> configuration.set("movie.analytics." + key, value));
    }
}
