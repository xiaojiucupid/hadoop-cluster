package com.cev.movie.api.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * 电影分析与拼团业务看板 DTO。
 *
 * <p>数据由后端从 movie_analytics 数据库或 MapReduce 结果表聚合后返回，避免前端写死展示值。</p>
 */
public record MovieDashboardDTO(
        DashboardSummaryDTO dashboardSummary,
        List<NameValueDTO> ratingDistribution,
        List<NameValueDTO> genreRanking,
        List<NameValueDTO> regionRanking,
        List<GroupTrendDTO> groupTrend,
        List<HotMovieDTO> hotMovies,
        List<GroupActivityDTO> groupActivities
) {

    public record DashboardSummaryDTO(
            Long movieCount,
            Long userCount,
            Long ratingCount,
            Long commentCount,
            BigDecimal groupRevenue,
            BigDecimal successRate
    ) {
    }

    public record NameValueDTO(String name, Long value) {
    }

    public record GroupTrendDTO(String date, Long groups, Long success, BigDecimal amount) {
    }

    public record HotMovieDTO(Long id, String name, BigDecimal score, Long votes, String genre, Long sales) {
    }

    public record GroupActivityDTO(
            String no,
            String name,
            String status,
            BigDecimal groupPrice,
            BigDecimal singlePrice,
            Integer target,
            Integer stock
    ) {
    }
}
