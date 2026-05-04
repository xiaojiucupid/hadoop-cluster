package com.cev.movie.api.movie;

import java.math.BigDecimal;

/**
 * 影视摘要 DTO。
 *
 * <p>用于列表页、推荐结果、热门榜单等轻量级展示场景，避免直接暴露数据库实体。</p>
 */
public record MovieSummaryDTO(
        Long id,
        String title,
        String genre,
        BigDecimal score,
        Integer releaseYear
) {
}
