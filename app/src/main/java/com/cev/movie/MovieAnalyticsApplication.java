package com.cev.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端服务启动入口。
 *
 * <p>app 模块作为最终可运行模块，组合 trigger、domain、infrastructure、api、types
 * 等子模块，向 Web 端和 Android 端提供 REST 服务。</p>
 */
@SpringBootApplication(scanBasePackages = "com.cev.movie")
public class MovieAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieAnalyticsApplication.class, args);
    }
}
