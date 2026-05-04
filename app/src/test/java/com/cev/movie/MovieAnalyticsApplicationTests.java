package com.cev.movie;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文启动测试。
 *
 * <p>用于验证 Spring Bean 装配、模块依赖和基础配置是否正确。</p>
 */
@SpringBootTest
class MovieAnalyticsApplicationTests {

    @Test
    void contextLoads() {
        // Spring Boot 上下文能正常启动即代表基础框架装配通过。
    }
}
