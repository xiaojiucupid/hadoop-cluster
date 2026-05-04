package com.cev.movie.mapreduce.common;

import org.apache.hadoop.conf.Configuration;

/**
 * MySQL 写入配置。
 *
 * <p>MapReduce Reducer 在云服务器上通过 JDBC 将离线计算结果写回 MySQL，
 * 后端 Spring Boot 再直接查询这些结果表提供接口。</p>
 */
public record MysqlConfig(String url, String username, String password) {

    public static MysqlConfig from(Configuration configuration) {
        return new MysqlConfig(
                configuration.get("movie.analytics.mysqlUrl", "jdbc:mysql://localhost:3306/movie_analytics?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"),
                configuration.get("movie.analytics.mysqlUser", "root"),
                configuration.get("movie.analytics.mysqlPassword", "root")
        );
    }
}
