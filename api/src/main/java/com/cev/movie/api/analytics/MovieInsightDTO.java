package com.cev.movie.api.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * 电影数据分析聚合 DTO。
 *
 * <p>用于把数据库与 MapReduce 结果表中的画像、偏好、质量、交易和推荐指标统一返回给前端或 Agent。</p>
 */
public record MovieInsightDTO(
        OverviewDTO overview,
        List<NameValueDTO> genreHeat,
        List<NameValueDTO> regionHeat,
        List<ScoreBucketDTO> scoreBuckets,
        List<MovieQualityDTO> qualityMovies,
        List<UserPreferenceDTO> userPreferences,
        List<RecommendationMetricDTO> recommendationMetrics,
        List<String> dataSources
) {

    public record OverviewDTO(
            Long movieCount,
            Long ratingCount,
            Long commentCount,
            BigDecimal avgScore,
            BigDecimal avgRating,
            Long recommendationCount,
            Long mapReduceResultCount
    ) {
    }

    public record NameValueDTO(String name, BigDecimal value) {
    }

    public record ScoreBucketDTO(String bucket, Long movieCount, Long ratingCount) {
    }

    public record MovieQualityDTO(Long movieId, String name, BigDecimal score, Long votes, Long comments, BigDecimal qualityScore) {
    }

    public record UserPreferenceDTO(String userKey, String favoriteTag, BigDecimal avgRating, Long ratingCount, String segment) {
    }

    public record RecommendationMetricDTO(String algorithm, Long resultCount, BigDecimal avgScore, BigDecimal coverageRate) {
    }
}
