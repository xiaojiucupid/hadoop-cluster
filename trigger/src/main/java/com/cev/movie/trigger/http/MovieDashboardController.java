package com.cev.movie.trigger.http;

import com.cev.movie.api.dashboard.MovieDashboardDTO;
import com.cev.movie.api.response.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 电影分析与拼团业务看板接口。
 *
 * <p>前端展示数据统一由该接口从 movie_analytics 数据库或 MapReduce 落库结果聚合得到，
 * 前端只负责渲染，不再维护 mockData 死值。</p>
 */
@RestController
@RequestMapping("/api/movie-dashboard")
public class MovieDashboardController {

    private final JdbcTemplate jdbcTemplate;

    public MovieDashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<MovieDashboardDTO> dashboard() {
        return ApiResponse.success(new MovieDashboardDTO(
                buildDashboardSummary(),
                buildRatingDistribution(),
                buildGenreRanking(),
                buildRegionRanking(),
                buildGroupTrend(),
                buildHotMovies(),
                buildGroupActivities()
        ));
    }

    private MovieDashboardDTO.DashboardSummaryDTO buildDashboardSummary() {
        long movieCount = countExistingTable("dim_movie");
        long userCount = countExistingTable("dim_user");
        long ratingCount = countExistingTable("fact_rating");
        long commentCount = countExistingTable("fact_comment");
        BigDecimal revenue = decimalOrZero("""
                SELECT COALESCE(SUM(payable_amount), 0)
                FROM gb_trade_order
                WHERE order_status IN (1, 4)
                """);
        BigDecimal successRate = buildSuccessRate();
        return new MovieDashboardDTO.DashboardSummaryDTO(movieCount, userCount, ratingCount, commentCount, revenue, successRate);
    }

    private BigDecimal buildSuccessRate() {
        if (!tableExists("gb_group_snapshot")) {
            return BigDecimal.ZERO;
        }
        BigDecimal success = decimalOrZero("""
                SELECT COALESCE(SUM(success_group_count), 0)
                FROM gb_group_snapshot
                """);
        BigDecimal total = decimalOrZero("""
                SELECT COALESCE(SUM(launched_group_count), 0)
                FROM gb_group_snapshot
                """);
        if (BigDecimal.ZERO.compareTo(total) == 0) {
            return BigDecimal.ZERO;
        }
        return success.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private List<MovieDashboardDTO.NameValueDTO> buildRatingDistribution() {
        if (!tableExists("fact_rating")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT CONCAT(rating_star, '星') AS name, COUNT(*) AS value
                FROM (
                    SELECT LEAST(5, GREATEST(1, CEIL(rating))) AS rating_star
                    FROM fact_rating
                    WHERE rating IS NOT NULL
                ) t
                GROUP BY rating_star
                ORDER BY rating_star
                """,
                (rs, rowNum) -> new MovieDashboardDTO.NameValueDTO(rs.getString("name"), rs.getLong("value"))
        );
    }

    private List<MovieDashboardDTO.NameValueDTO> buildGenreRanking() {
        if (!tableExists("bridge_movie_tag")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT tag_name AS name, COUNT(*) AS value
                FROM bridge_movie_tag
                WHERE tag_name IS NOT NULL AND tag_name <> ''
                GROUP BY tag_name
                ORDER BY value DESC
                LIMIT 8
                """,
                (rs, rowNum) -> new MovieDashboardDTO.NameValueDTO(rs.getString("name"), rs.getLong("value"))
        );
    }

    private List<MovieDashboardDTO.NameValueDTO> buildRegionRanking() {
        if (!tableExists("bridge_movie_region")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT region_name AS name, COUNT(DISTINCT movie_id) AS value
                FROM bridge_movie_region
                WHERE region_name IS NOT NULL AND region_name <> ''
                GROUP BY region_name
                ORDER BY value DESC
                LIMIT 6
                """,
                (rs, rowNum) -> new MovieDashboardDTO.NameValueDTO(rs.getString("name"), rs.getLong("value"))
        );
    }

    private List<MovieDashboardDTO.GroupTrendDTO> buildGroupTrend() {
        if (!tableExists("gb_group_snapshot")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    DATE_FORMAT(stat_date, '%m-%d') AS date,
                    COALESCE(SUM(launched_group_count), 0) AS group_count,
                    COALESCE(SUM(success_group_count), 0) AS success_count,
                    COALESCE(SUM(paid_amount), 0) AS amount
                FROM gb_group_snapshot
                WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
                GROUP BY stat_date, DATE_FORMAT(stat_date, '%m-%d')
                ORDER BY stat_date
                """,
                (rs, rowNum) -> new MovieDashboardDTO.GroupTrendDTO(
                        rs.getString("date"),
                        rs.getLong("group_count"),
                        rs.getLong("success_count"),
                        rs.getBigDecimal("amount")
                )
        );
    }

    private List<MovieDashboardDTO.HotMovieDTO> buildHotMovies() {
        if (!tableExists("dim_movie")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    m.movie_id AS id,
                    m.name,
                    COALESCE(m.douban_score, 0) AS score,
                    COALESCE(m.douban_votes, 0) AS votes,
                    COALESCE(GROUP_CONCAT(DISTINCT t.tag_name ORDER BY t.tag_name SEPARATOR ' / '), '-') AS genre,
                    COALESCE(COUNT(o.id), 0) AS sales
                FROM dim_movie m
                LEFT JOIN bridge_movie_tag t ON t.movie_id = m.movie_id
                LEFT JOIN gb_trade_order o ON o.movie_id = m.movie_id AND o.order_status IN (1, 4)
                WHERE m.name IS NOT NULL
                GROUP BY m.movie_id, m.name, m.douban_score, m.douban_votes
                ORDER BY score DESC, votes DESC, sales DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new MovieDashboardDTO.HotMovieDTO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("score"),
                        rs.getLong("votes"),
                        rs.getString("genre"),
                        rs.getLong("sales")
                )
        );
    }

    private List<MovieDashboardDTO.GroupActivityDTO> buildGroupActivities() {
        if (!tableExists("gb_activity")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    activity_no AS no,
                    activity_name AS name,
                    CASE activity_status
                        WHEN 0 THEN '待上线'
                        WHEN 1 THEN '进行中'
                        WHEN 2 THEN '已结束'
                        WHEN 3 THEN '已关闭'
                        ELSE '未知'
                    END AS status,
                    group_price,
                    single_price,
                    target_group_size,
                    GREATEST(stock_total - stock_locked, 0) AS stock
                FROM gb_activity
                ORDER BY update_time DESC, create_time DESC
                LIMIT 4
                """,
                (rs, rowNum) -> new MovieDashboardDTO.GroupActivityDTO(
                        rs.getString("no"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getBigDecimal("group_price"),
                        rs.getBigDecimal("single_price"),
                        rs.getInt("target_group_size"),
                        rs.getInt("stock")
                )
        );
    }

    private long countExistingTable(String tableName) {
        if (!tableExists(tableName)) {
            return 0L;
        }
        return singleLong("SELECT COUNT(*) FROM " + tableName);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private long singleLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal decimalOrZero(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}
