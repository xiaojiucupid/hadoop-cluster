package com.cev.movie.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>集中声明 Mapper 扫描路径，保持启动类只负责应用启动。</p>
 */
@Configuration
@MapperScan("com.cev.movie.infrastructure.persistence.mapper")
public class MyBatisPlusConfig {
}
