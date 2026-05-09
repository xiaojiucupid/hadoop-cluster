package com.cev.movie.trigger.http;

import com.cev.movie.api.agent.MovieAgentDTO;
import com.cev.movie.api.response.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电影推荐与数据分析 Agent 接口。
 *
 * <p>该模块不在前端写死结论，而是先从 MySQL 业务表、MapReduce 推荐结果表中计算结构化参数，
 * 再把这些参数交给轻量规则 Agent 生成面向业务的分析结论、推荐策略和行动建议。</p>
 */
@RestController
@RequestMapping("/api/movie-agent")
public class MovieAgentController {

    private final JdbcTemplate jdbcTemplate;

    public MovieAgentController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 生成全局或指定用户的电影推荐与数据分析报告。
     */
    @GetMapping("/analyze")
    public ApiResponse<MovieAgentDTO> analyze(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "请分析当前电影数据表现并给出推荐策略") String question,
            @RequestParam(defaultValue = "8") Integer limit) {
        int topLimit = Math.max(limit == null ? 8 : limit, 1);
        MovieAgentDTO.AgentContextDTO context = buildContext(userId, topLimit);
        MovieAgentDTO.AgentAnswerDTO answer = generateAnswer(question, context);
        return ApiResponse.success(new MovieAgentDTO(question, context, answer));
    }

    /**
     * 只返回 Agent 入参，便于前端或外部大模型检查后端实际计算出的指标。
     */
    @GetMapping("/context")
    public ApiResponse<MovieAgentDTO.AgentContextDTO> context(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "8") Integer limit) {
        return ApiResponse.success(buildContext(userId, Math.max(limit == null ? 8 : limit, 1)));
    }

    private MovieAgentDTO.AgentContextDTO buildContext(Long userId, int limit) {
        String userMd5 = resolveUserMd5(userId);
        return new MovieAgentDTO.AgentContextDTO(
                userId,
                userMd5,
                buildMetrics(),
                buildRecommendations(userId, limit),
                buildTagPreferences(userId, limit),
                buildRatingDistribution(),
                buildRegionDistribution(limit)
        );
    }

    private Map<String, Object> buildMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        long movieCount = countExistingTable("dim_movie");
        long userCount = countExistingTable("dim_user");
        long ratingCount = countExistingTable("fact_rating");
        long commentCount = countExistingTable("fact_comment");
        long recCount = countRecommendationResults();
        BigDecimal avgMovieScore = decimalOrZero(tableExists("dim_movie")
                ? "SELECT COALESCE(AVG(douban_score), 0) FROM dim_movie WHERE douban_score IS NOT NULL"
                : null).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgUserRating = decimalOrZero(tableExists("fact_rating")
                ? "SELECT COALESCE(AVG(rating), 0) FROM fact_rating WHERE rating IS NOT NULL"
                : null).setScale(2, RoundingMode.HALF_UP);
        BigDecimal highScoreRate = movieCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(singleLong(
                "SELECT COUNT(*) FROM dim_movie WHERE COALESCE(douban_score, 0) >= 8", true))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(movieCount), 2, RoundingMode.HALF_UP);

        metrics.put("movieCount", movieCount);
        metrics.put("userCount", userCount);
        metrics.put("ratingCount", ratingCount);
        metrics.put("commentCount", commentCount);
        metrics.put("recommendationCount", recCount);
        metrics.put("avgMovieScore", avgMovieScore);
        metrics.put("avgUserRating", avgUserRating);
        metrics.put("highScoreMovieRate", highScoreRate);
        metrics.put("recommendationSource", recommendationSource());
        return metrics;
    }

    private List<MovieAgentDTO.MovieRecommendationParamDTO> buildRecommendations(Long userId, int limit) {
        if (tableExists("rec_user_movie_topn") && countExistingTable("rec_user_movie_topn") > 0) {
            String sql = userId == null
                    ? """
                    SELECT movie_id, movie_name, recommend_score, reason, 'MAPREDUCE_HYBRID' AS algorithm_type
                    FROM rec_user_movie_topn
                    ORDER BY recommend_score DESC
                    LIMIT ?
                    """
                    : """
                    SELECT movie_id, movie_name, recommend_score, reason, 'MAPREDUCE_HYBRID' AS algorithm_type, rank_no
                    FROM rec_user_movie_topn
                    WHERE user_id = ?
                    ORDER BY rank_no ASC, recommend_score DESC
                    LIMIT ?
                    """;
            Object[] args = userId == null ? new Object[]{limit} : new Object[]{userId, limit};
            return jdbcTemplate.query(sql, (rs, rowNum) -> new MovieAgentDTO.MovieRecommendationParamDTO(
                    rs.getLong("movie_id"),
                    rs.getString("movie_name"),
                    rs.getBigDecimal("recommend_score"),
                    rs.getString("reason"),
                    rs.getString("algorithm_type"),
                    userId == null ? rowNum + 1 : rs.getInt("rank_no")
            ), args);
        }
        if (tableExists("recommendation_result") && countExistingTable("recommendation_result") > 0) {
            String sql = userId == null
                    ? """
                    SELECT movie_id, movie_title AS movie_name, recommend_score, reason, algorithm_type, rank_no
                    FROM recommendation_result
                    ORDER BY recommend_score DESC
                    LIMIT ?
                    """
                    : """
                    SELECT movie_id, movie_title AS movie_name, recommend_score, reason, algorithm_type, rank_no
                    FROM recommendation_result
                    WHERE user_id = ?
                    ORDER BY rank_no ASC, recommend_score DESC
                    LIMIT ?
                    """;
            Object[] args = userId == null ? new Object[]{limit} : new Object[]{userId, limit};
            return jdbcTemplate.query(sql, (rs, rowNum) -> new MovieAgentDTO.MovieRecommendationParamDTO(
                    rs.getLong("movie_id"),
                    rs.getString("movie_name"),
                    rs.getBigDecimal("recommend_score"),
                    rs.getString("reason"),
                    rs.getString("algorithm_type"),
                    rs.getInt("rank_no")
            ), args);
        }
        if (!tableExists("dim_movie")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT movie_id, name AS movie_name,
                       ROUND(COALESCE(douban_score, 0) * 10 + LOG10(COALESCE(douban_votes, 0) + 10), 2) AS recommend_score,
                       CONCAT('高质量冷启动推荐：豆瓣 ', COALESCE(douban_score, 0), ' 分，投票 ', COALESCE(douban_votes, 0)) AS reason,
                       'QUALITY_FALLBACK' AS algorithm_type
                FROM dim_movie
                WHERE name IS NOT NULL
                ORDER BY recommend_score DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new MovieAgentDTO.MovieRecommendationParamDTO(
                        rs.getLong("movie_id"),
                        rs.getString("movie_name"),
                        rs.getBigDecimal("recommend_score"),
                        rs.getString("reason"),
                        rs.getString("algorithm_type"),
                        rowNum + 1
                ),
                limit
        );
    }

    private List<MovieAgentDTO.NameValueParamDTO> buildTagPreferences(Long userId, int limit) {
        if (!tableExists("bridge_movie_tag")) {
            return List.of();
        }
        if (userId != null && tableExists("fact_rating")) {
            List<MovieAgentDTO.NameValueParamDTO> rows = jdbcTemplate.query(
                    """
                    SELECT bmt.tag_name AS name, COUNT(*) AS value
                    FROM fact_rating fr
                    JOIN bridge_movie_tag bmt ON bmt.movie_id = fr.movie_id
                    WHERE fr.user_id = ? AND fr.rating >= 4 AND bmt.tag_name IS NOT NULL AND bmt.tag_name <> ''
                    GROUP BY bmt.tag_name
                    ORDER BY value DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new MovieAgentDTO.NameValueParamDTO(rs.getString("name"), rs.getBigDecimal("value")),
                    userId,
                    limit
            );
            if (!rows.isEmpty()) {
                return rows;
            }
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
                (rs, rowNum) -> new MovieAgentDTO.NameValueParamDTO(rs.getString("name"), rs.getBigDecimal("value")),
                limit
        );
    }

    private List<MovieAgentDTO.NameValueParamDTO> buildRatingDistribution() {
        if (!tableExists("fact_rating")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT CONCAT(CAST(rating AS CHAR), '星') AS name, COUNT(*) AS value
                FROM fact_rating
                WHERE rating IS NOT NULL
                GROUP BY rating
                ORDER BY rating ASC
                """,
                (rs, rowNum) -> new MovieAgentDTO.NameValueParamDTO(rs.getString("name"), rs.getBigDecimal("value"))
        );
    }

    private List<MovieAgentDTO.NameValueParamDTO> buildRegionDistribution(int limit) {
        if (!tableExists("dim_movie")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT region AS name, COUNT(*) AS value
                FROM dim_movie
                WHERE region IS NOT NULL AND region <> ''
                GROUP BY region
                ORDER BY value DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new MovieAgentDTO.NameValueParamDTO(rs.getString("name"), rs.getBigDecimal("value")),
                limit
        );
    }

    private MovieAgentDTO.AgentAnswerDTO generateAnswer(String question, MovieAgentDTO.AgentContextDTO context) {
        Map<String, Object> metrics = context.metrics();
        long movieCount = asLong(metrics.get("movieCount"));
        long ratingCount = asLong(metrics.get("ratingCount"));
        long recCount = asLong(metrics.get("recommendationCount"));
        BigDecimal avgScore = asBigDecimal(metrics.get("avgMovieScore"));
        BigDecimal highScoreRate = asBigDecimal(metrics.get("highScoreMovieRate"));
        String topTag = context.tagPreferences().isEmpty() ? "暂无标签" : context.tagPreferences().get(0).name();
        String topRegion = context.regionDistribution().isEmpty() ? "暂无地区" : context.regionDistribution().get(0).name();
        String topMovie = context.recommendations().isEmpty() ? "暂无推荐电影" : context.recommendations().get(0).movieName();

        String summary = "已基于 " + movieCount + " 部电影、" + ratingCount + " 条评分和 " + recCount
                + " 条推荐/MapReduce 结果生成分析。当前平均电影分为 " + avgScore
                + "，高分电影占比 " + highScoreRate + "%，首推影片为《" + topMovie + "》。";

        List<String> insights = List.of(
                "内容供给侧：" + topRegion + " 是当前电影数量最高的地区，适合作为区域内容分析入口。",
                "兴趣偏好侧：" + topTag + " 标签热度最高，可作为推荐召回、榜单分组和运营专题的核心特征。",
                recCount > 0 ? "推荐链路侧：已检测到推荐结果表，可直接使用 MapReduce/离线推荐结果驱动个性化推荐。" : "推荐链路侧：暂未检测到推荐结果表，当前使用高质量电影兜底推荐。",
                avgScore.compareTo(BigDecimal.valueOf(7)) >= 0 ? "质量侧：平均电影分较高，适合突出高口碑推荐。" : "质量侧：平均电影分偏低，应加强质量过滤和低分内容降权。"
        );
        List<String> strategies = List.of(
                "冷启动用户优先使用热门高分电影 + 全局标签热度召回。",
                "活跃用户使用用户高分电影标签偏好，结合 MapReduce TopN 结果进行混合排序。",
                "深度用户增加协同过滤权重，并过滤已评分电影，提升推荐新颖性。",
                "运营分析侧按地区、标签、评分桶拆分电影池，识别高口碑高热度内容。"
        );
        List<String> actions = List.of(
                "定时运行 HotMovieRecommendationJob、TagPreferenceRecommendationJob、HybridRecommendationJob 并写入 rec_user_movie_topn。",
                "前端推荐模块调用 /api/movie-agent/analyze 获取 Agent 结论，电影列表调用 /api/recommendations/users/{userId} 获取明细。",
                "如果 recommendationCount 为 0，优先检查 MapReduce 写库配置和 rec_user_movie_topn 表结构。",
                "将 tagPreferences、ratingDistribution、regionDistribution 作为 Agent 入参，避免前端写死分析结论。"
        );
        List<String> evidence = List.of(
                "question=" + question,
                "recommendationSource=" + metrics.get("recommendationSource"),
                "topTag=" + topTag,
                "topRegion=" + topRegion,
                "topRecommendation=" + topMovie
        );
        return new MovieAgentDTO.AgentAnswerDTO(summary, insights, strategies, actions, evidence);
    }

    private String resolveUserMd5(Long userId) {
        if (userId == null || !tableExists("dim_user")) {
            return null;
        }
        List<String> rows = jdbcTemplate.query("SELECT user_md5 FROM dim_user WHERE user_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("user_md5"), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countRecommendationResults() {
        if (tableExists("rec_user_movie_topn")) {
            return countExistingTable("rec_user_movie_topn");
        }
        if (tableExists("recommendation_result")) {
            return countExistingTable("recommendation_result");
        }
        return 0L;
    }

    private String recommendationSource() {
        if (tableExists("rec_user_movie_topn") && countExistingTable("rec_user_movie_topn") > 0) {
            return "rec_user_movie_topn(MapReduce)";
        }
        if (tableExists("recommendation_result") && countExistingTable("recommendation_result") > 0) {
            return "recommendation_result";
        }
        return "dim_movie quality fallback";
    }

    private long countExistingTable(String tableName) {
        if (!tableExists(tableName)) {
            return 0L;
        }
        return singleLong("SELECT COUNT(*) FROM " + tableName, false);
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

    private long singleLong(String sql, boolean requireDimMovie) {
        if (requireDimMovie && !tableExists("dim_movie")) {
            return 0L;
        }
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal decimalOrZero(String sql) {
        if (sql == null || sql.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
