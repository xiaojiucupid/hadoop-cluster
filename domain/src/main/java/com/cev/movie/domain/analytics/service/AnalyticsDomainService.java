package com.cev.movie.domain.analytics.service;

import com.cev.movie.domain.analytics.model.GenreStat;
import com.cev.movie.domain.analytics.repository.AnalyticsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据分析领域服务。
 *
 * <p>封装影视统计分析相关业务逻辑，trigger 层通过该服务获取统计结果。</p>
 */
@Service
public class AnalyticsDomainService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsDomainService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * 获取题材分布统计。
     */
    public List<GenreStat> listGenreStats() {
        return analyticsRepository.listGenreStats();
    }
}
