package com.cev.movie.domain.movie.model;

import java.math.BigDecimal;

/**
 * 影视领域模型。
 *
 * <p>领域模型用于表达业务含义，不直接绑定数据库表结构。后续可在这里加入
 * 评分合法性、影片上下架、推荐权重等业务规则。</p>
 */
public class Movie {

    private Long id;
    private String title;
    private String genre;
    private BigDecimal score;
    private Integer releaseYear;

    public Movie(Long id, String title, String genre, BigDecimal score, Integer releaseYear) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.score = score;
        this.releaseYear = releaseYear;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getGenre() {
        return genre;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }
}
