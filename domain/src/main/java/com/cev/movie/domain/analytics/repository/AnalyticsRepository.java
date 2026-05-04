package com.cev.movie.domain.analytics.repository;

import com.cev.movie.domain.analytics.model.GenreStat;

import java.util.List;

/**
 * 数据分析仓储接口。
 *
 * <p>用于读取离线计算产生的统计结果，当前主要面向 MySQL 结果表，后续也可扩展为
 * Hive、ClickHouse 或 Elasticsearch。</p>
 */
public interface AnalyticsRepository {

    /**
     * 查询题材分布统计结果。
     */
    List<GenreStat> listGenreStats();
}
