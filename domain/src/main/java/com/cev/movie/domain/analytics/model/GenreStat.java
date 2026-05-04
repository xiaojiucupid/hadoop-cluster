package com.cev.movie.domain.analytics.model;

/**
 * 题材统计领域模型。
 *
 * <p>该对象表示 Spark/Hive 离线分析后的业务统计结果，可用于图表展示和推荐解释。</p>
 */
public class GenreStat {

    private final String genre;
    private final Long movieCount;

    public GenreStat(String genre, Long movieCount) {
        this.genre = genre;
        this.movieCount = movieCount;
    }

    public String getGenre() {
        return genre;
    }

    public Long getMovieCount() {
        return movieCount;
    }
}
