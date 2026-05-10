package com.cev.movie.trigger.http;

import com.cev.movie.api.analytics.MovieInsightDTO;
import com.cev.movie.api.response.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 电影分析与推荐指标接口。
 *
 * <p>统一从 MySQL 维表、事实表和 MapReduce 推荐结果表中提取参数，
 * 为前端看板和 Agent 分析提供结构化输入。</p>
 */
@RestController
@RequestMapping("/api/movie-insights")
public class MovieInsightController {

    private final JdbcTemplate jdbcTemplate;

    public MovieInsightController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<MovieInsightDTO> insights(@RequestParam(defaultValue = "5") Integer limit) {
        int topLimit = Math.max(limit == null ? 5 : limit, 1);
        return ApiResponse.success(new MovieInsightDTO(
                buildOverview(),
                buildGenreHeat(topLimit),
                buildRegionHeat(topLimit),
                buildScoreBuckets(),
                buildQualityMovies(topLimit),
                buildUserPreferences(topLimit),
                buildRecommendationMetrics(),
                buildDataSources()
        ));
    }

    private MovieInsightDTO.OverviewDTO buildOverview() {
        long movieCount = countExistingTable("dim_movie");
        long ratingCount = countExistingTable("fact_rating");
        long commentCount = countExistingTable("fact_comment");
        BigDecimal avgScore = decimalOrZero(tableExists("dim_movie")
                ? "SELECT COALESCE(AVG(douban_score), 0) FROM dim_movie WHERE douban_score IS NOT NULL"
                : null);
        BigDecimal avgRating = decimalOrZero(tableExists("fact_rating")
                ? "SELECT COALESCE(AVG(rating), 0) FROM fact_rating WHERE rating IS NOT NULL"
                : null);
        long recommendationCount = countRecommendationResults();
        long mapReduceResultCount = recommendationCount;
        return new MovieInsightDTO.OverviewDTO(
                movieCount,
                ratingCount,
                commentCount,
                avgScore.setScale(2, RoundingMode.HALF_UP),
                avgRating.setScale(2, RoundingMode.HALF_UP),
                recommendationCount,
                mapReduceResultCount
        );
    }

    private List<MovieInsightDTO.NameValueDTO> buildGenreHeat(int limit) {
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
                LIMIT ?
                """,
                (rs, rowNum) -> new MovieInsightDTO.NameValueDTO(rs.getString("name"), rs.getBigDecimal("value")),
                limit
        );
    }

    private List<MovieInsightDTO.NameValueDTO> buildRegionHeat(int limit) {
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
                LIMIT ?
                """,
                (rs, rowNum) -> new MovieInsightDTO.NameValueDTO(rs.getString("name"), rs.getBigDecimal("value")),
                limit
        );
    }

    private List<MovieInsightDTO.ScoreBucketDTO> buildScoreBuckets() {
        if (!tableExists("dim_movie") && !tableExists("fact_rating")) {
            return List.of();
        }
        return List.of(
                buildScoreBucket("0-6", 0, 6),
                buildScoreBucket("6-7", 6, 7),
                buildScoreBucket("7-8", 7, 8),
                buildScoreBucket("8-9", 8, 9),
                buildScoreBucket("9-10", 9, 10)
        );
    }

    private MovieInsightDTO.ScoreBucketDTO buildScoreBucket(String label, int min, int max) {
        long movieCount = tableExists("dim_movie")
                ? singleLong(
                "SELECT COUNT(*) FROM dim_movie WHERE COALESCE(douban_score, 0) >= ? AND COALESCE(douban_score, 0) < ?",
                BigDecimal.valueOf(min),
                BigDecimal.valueOf(max)
        ) : 0L;
        long ratingCount = tableExists("fact_rating")
                ? singleLong(
                "SELECT COUNT(*) FROM fact_rating WHERE COALESCE(rating, 0) >= ? AND COALESCE(rating, 0) < ?",
                BigDecimal.valueOf(min),
                BigDecimal.valueOf(max)
        ) : 0L;
        return new MovieInsightDTO.ScoreBucketDTO(label, movieCount, ratingCount);
    }

    private List<MovieInsightDTO.MovieQualityDTO> buildQualityMovies(int limit) {
        if (!tableExists("dim_movie")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    m.movie_id AS movieId,
                    m.name,
                    COALESCE(m.douban_score, 0) AS score,
                    COALESCE(m.douban_votes, 0) AS votes,
                    COALESCE(fc.comment_count, 0) AS comments,
                    ROUND(COALESCE(m.douban_score, 0) * 0.6 + LOG10(COALESCE(m.douban_votes, 0) + 10) * 8 + LOG10(COALESCE(fc.comment_count, 0) + 10) * 4, 2) AS qualityScore
                FROM dim_movie m
                LEFT JOIN (
                    SELECT movie_id, COUNT(*) AS comment_count
                    FROM fact_comment
                    GROUP BY movie_id
                ) fc ON fc.movie_id = m.movie_id
                WHERE m.name IS NOT NULL
                ORDER BY qualityScore DESC, score DESC, votes DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new MovieInsightDTO.MovieQualityDTO(
                        rs.getLong("movieId"),
                        rs.getString("name"),
                        rs.getBigDecimal("score"),
                        rs.getLong("votes"),
                        rs.getLong("comments"),
                        rs.getBigDecimal("qualityScore")
                ),
                limit
        );
    }

    private List<MovieInsightDTO.UserPreferenceDTO> buildUserPreferences(int limit) {
        if (!tableExists("fact_rating") || !tableExists("dim_user")) {
            return List.of();
        }
        String favoriteTagSelect = tableExists("bridge_movie_tag")
                ? """
                  (
                      SELECT bmt.tag_name
                      FROM bridge_movie_tag bmt
                      JOIN fact_rating fr2 ON fr2.movie_id = bmt.movie_id
                      WHERE fr2.user_md5 = fr.user_md5
                        AND fr2.rating >= 4
                        AND bmt.tag_name IS NOT NULL
                        AND bmt.tag_name <> ''
                      GROUP BY bmt.tag_name
                      ORDER BY COUNT(*) DESC, bmt.tag_name ASC
                      LIMIT 1
                  )
                  """
                : "'综合'";
        return jdbcTemplate.query(
                """
                SELECT
                    COALESCE(u.user_md5, fr.user_md5) AS userKey,
                    %s AS favoriteTag,
                    ROUND(AVG(fr.rating), 2) AS avgRating,
                    COUNT(*) AS ratingCount,
                    CASE
                        WHEN COUNT(*) >= 50 THEN '深度用户'
                        WHEN COUNT(*) >= 15 THEN '活跃用户'
                        ELSE '轻度用户'
                    END AS segment
                FROM fact_rating fr
                LEFT JOIN dim_user u ON u.user_md5 = fr.user_md5
                WHERE fr.rating IS NOT NULL
                GROUP BY fr.user_md5, u.user_md5
                ORDER BY ratingCount DESC, avgRating DESC
                LIMIT ?
                """.formatted(favoriteTagSelect),
                (rs, rowNum) -> new MovieInsightDTO.UserPreferenceDTO(
                        maskUser(rs.getString("userKey")),
                        rs.getString("favoriteTag"),
                        rs.getBigDecimal("avgRating"),
                        rs.getLong("ratingCount"),
                        rs.getString("segment")
                ),
                limit
        );
    }

    private List<MovieInsightDTO.RecommendationMetricDTO> buildRecommendationMetrics() {
        if (tableExists("recommendation_result")) {
            List<MovieInsightDTO.RecommendationMetricDTO> rows = jdbcTemplate.query(
                    """
                    SELECT
                        algorithm_type AS algorithm,
                        COUNT(*) AS resultCount,
                        ROUND(AVG(recommend_score), 4) AS avgScore
                    FROM recommendation_result
                    GROUP BY algorithm_type
                    ORDER BY resultCount DESC
                    """,
                    (rs, rowNum) -> new MovieInsightDTO.RecommendationMetricDTO(
                            rs.getString("algorithm"),
                            rs.getLong("resultCount"),
                            rs.getBigDecimal("avgScore"),
                            coverageRate(rs.getLong("resultCount"))
                    )
            );
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        if (tableExists("rec_user_movie_topn")) {
            long total = countExistingTable("rec_user_movie_topn");
            return List.of(new MovieInsightDTO.RecommendationMetricDTO(
                    "MAPREDUCE_TOPN",
                    total,
                    decimalOrZero("SELECT COALESCE(AVG(recommend_score), 0) FROM rec_user_movie_topn"),
                    coverageRate(total)
            ));
        }
        return List.of();
    }

    private List<String> buildDataSources() {
        return List.of(
                describeSource("dim_movie", "电影维度表"),
                describeSource("fact_rating", "评分事实表"),
                describeSource("fact_comment", "评论事实表"),
                describeSource("bridge_movie_tag", "电影标签桥接表"),
                describeSource("recommendation_result", "推荐结果表"),
                describeSource("rec_user_movie_topn", "MapReduce TopN 结果表")
        ).stream().filter(source -> !source.endsWith("未接入")).toList();
    }

    private String describeSource(String tableName, String displayName) {
        return displayName + "(" + tableName + ")：" + (tableExists(tableName) ? "已接入" : "未接入");
    }

    private long countRecommendationResults() {
        if (tableExists("recommendation_result")) {
            return countExistingTable("recommendation_result");
        }
        if (tableExists("rec_user_movie_topn")) {
            return countExistingTable("rec_user_movie_topn");
        }
        return 0L;
    }

    private BigDecimal coverageRate(long resultCount) {
        long userCount = countExistingTable("dim_user");
        long movieCount = countExistingTable("dim_movie");
        if (userCount <= 0 || movieCount <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(resultCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(userCount * movieCount), 4, RoundingMode.HALF_UP);
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

    private long singleLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private BigDecimal decimalOrZero(String sql, Object... args) {
        if (sql == null || sql.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String maskUser(String userKey) {
        if (userKey == null || userKey.length() < 8) {
            return "anonymous";
        }
        return "u_" + userKey.substring(0, 4) + "..." + userKey.substring(userKey.length() - 4);
    }
}
