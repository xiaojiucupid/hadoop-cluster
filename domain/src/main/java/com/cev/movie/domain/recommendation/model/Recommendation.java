package com.cev.movie.domain.recommendation.model;

import java.math.BigDecimal;

/**
 * 推荐结果领域模型。
 *
 * <p>该模型表达“系统为什么向某个用户推荐某部影片”的业务含义，
 * 不直接依赖 MySQL 表结构或 Spark 离线结果字段。</p>
 */
public class Recommendation {

    private final Long userId;
    private final Long movieId;
    private final String movieTitle;
    private final BigDecimal recommendScore;
    private final String reason;
    private final RecommendationAlgorithmType algorithmType;

    public Recommendation(
            Long userId,
            Long movieId,
            String movieTitle,
            BigDecimal recommendScore,
            String reason,
            RecommendationAlgorithmType algorithmType) {
        this.userId = userId;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.recommendScore = recommendScore;
        this.reason = reason;
        this.algorithmType = algorithmType;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMovieId() {
        return movieId;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public BigDecimal getRecommendScore() {
        return recommendScore;
    }

    public String getReason() {
        return reason;
    }

    public RecommendationAlgorithmType getAlgorithmType() {
        return algorithmType;
    }
}
