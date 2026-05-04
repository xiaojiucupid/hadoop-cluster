package com.cev.movie.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 题材统计持久化对象。
 *
 * <p>对应 Spark 离线任务写入 MySQL 的统计结果表。</p>
 */
@TableName("ads_genre_stat")
public class GenreStatPO {

    private String genre;
    private Long movieCount;

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Long getMovieCount() {
        return movieCount;
    }

    public void setMovieCount(Long movieCount) {
        this.movieCount = movieCount;
    }
}
