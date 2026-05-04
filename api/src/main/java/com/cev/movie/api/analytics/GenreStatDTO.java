package com.cev.movie.api.analytics;

/**
 * 影视题材统计 DTO。
 *
 * <p>承载离线分析后写入 MySQL 的题材分布结果，供可视化图表接口返回。</p>
 */
public record GenreStatDTO(String genre, Long movieCount) {
}
