package com.cev.movie.api.recommendation;

import java.math.BigDecimal;

/**
 * 个性化推荐接口 DTO。
 *
 * <p>api 层只定义对外传输字段，避免前端直接感知 domain 模型或数据库 PO。</p>
 */
public record RecommendationDTO(
        Long userId,
        Long movieId,
        String movieTitle,
        BigDecimal recommendScore,
        String reason,
        String algorithmType
) {
}
