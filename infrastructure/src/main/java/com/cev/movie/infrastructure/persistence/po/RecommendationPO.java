package com.cev.movie.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐结果持久化对象。
 *
 * <p>对应 MySQL 中保存 Spark/Hadoop 离线推荐结果的表。PO 只服务于持久化层，
 * 进入 domain 前需要转换为领域模型。</p>
 */
@TableName("recommendation_result")
public class RecommendationPO {

    @TableId
    private Long id;
    private Long userId;
    private Long movieId;
    private String movieTitle;
    private BigDecimal recommendScore;
    private String reason;
    private String algorithmType;
    private LocalDateTime calculatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMovieId() {
        return movieId;
    }

    public void setMovieId(Long movieId) {
        this.movieId = movieId;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public void setMovieTitle(String movieTitle) {
        this.movieTitle = movieTitle;
    }

    public BigDecimal getRecommendScore() {
        return recommendScore;
    }

    public void setRecommendScore(BigDecimal recommendScore) {
        this.recommendScore = recommendScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(String algorithmType) {
        this.algorithmType = algorithmType;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
