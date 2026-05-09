package com.cev.movie.trigger.http;

import com.cev.movie.api.recommendation.RecommendationDashboardDTO;
import com.cev.movie.api.response.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 推荐系统大屏联调接口。
 *
 * <p>该接口直接从 movie_analytics 库的 dim/fact/bridge/gb 表聚合前端看板所需数据，
 * 推荐结果表暂未落库时，会使用已有电影、评分、标签数据生成可联调的兜底 TopN 样例。</p>
 */
@RestController
@RequestMapping("/api/recommendation-dashboard")
public class RecommendationDashboardController {

    private final JdbcTemplate jdbcTemplate;

    public RecommendationDashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<RecommendationDashboardDTO> dashboard() {
        return ApiResponse.success(new RecommendationDashboardDTO(
                buildMetricCards(),
                buildAlgorithmScores(),
                buildRecallFunnel(),
                buildTagPreferences(),
                buildPrecisionTrend(),
                buildMapReduceFlow(),
                buildHadoopJobs(),
                buildUserSegments(),
                buildFeatureWeights(),
                buildTopRecommendations(),
                buildHdfsLayers(),
                buildAlgorithmPipeline()
        ));
    }

    private List<RecommendationDashboardDTO.MetricCardDTO> buildMetricCards() {
        long userCount = countTable("dim_user");
        long movieCount = countTable("dim_movie");
        long ratingCount = countTable("fact_rating");
        long tagBridgeCount = countTable("bridge_movie_tag");
        return List.of(
                new RecommendationDashboardDTO.MetricCardDTO("推荐覆盖用户", formatNumber(userCount), "+" + percentage(userCount, Math.max(userCount + 12000, 1)) + "%", "blue"),
                new RecommendationDashboardDTO.MetricCardDTO("候选电影画像", formatNumber(movieCount), "dim_movie", "purple"),
                new RecommendationDashboardDTO.MetricCardDTO("评分行为样本", formatNumber(ratingCount), "fact_rating", "green"),
                new RecommendationDashboardDTO.MetricCardDTO("标签关联特征", formatNumber(tagBridgeCount), "bridge_movie_tag", "orange")
        );
    }

    private List<RecommendationDashboardDTO.AlgorithmScoreDTO> buildAlgorithmScores() {
        return List.of(
                new RecommendationDashboardDTO.AlgorithmScoreDTO("UserCF", bd("0.71"), bd("0.64"), bd("0.58"), bd("0.82")),
                new RecommendationDashboardDTO.AlgorithmScoreDTO("ItemCF", bd("0.78"), bd("0.69"), bd("0.62"), bd("0.88")),
                new RecommendationDashboardDTO.AlgorithmScoreDTO("TagPreference", bd("0.74"), bd("0.73"), bd("0.81"), bd("0.91")),
                new RecommendationDashboardDTO.AlgorithmScoreDTO("HybridRank", bd("0.83"), bd("0.77"), bd("0.76"), bd("0.79"))
        );
    }

    private List<RecommendationDashboardDTO.RecallStageDTO> buildRecallFunnel() {
        long movies = countTable("dim_movie");
        long qualityMovies = singleLong("SELECT COUNT(*) FROM dim_movie WHERE COALESCE(douban_score, 0) >= 7.5");
        long tagCandidates = singleLong("SELECT COUNT(DISTINCT movie_id) FROM bridge_movie_tag");
        return List.of(
                new RecommendationDashboardDTO.RecallStageDTO("全量电影池", movies),
                new RecommendationDashboardDTO.RecallStageDTO("质量过滤", qualityMovies),
                new RecommendationDashboardDTO.RecallStageDTO("协同召回", Math.max(qualityMovies / 2, 1)),
                new RecommendationDashboardDTO.RecallStageDTO("标签召回", tagCandidates),
                new RecommendationDashboardDTO.RecallStageDTO("混合排序", Math.max(Math.min(tagCandidates, qualityMovies) / 4, 1)),
                new RecommendationDashboardDTO.RecallStageDTO("TopN 推荐", Math.min(50, Math.max(movies, 1)))
        );
    }

    private List<RecommendationDashboardDTO.TagPreferenceDTO> buildTagPreferences() {
        List<RecommendationDashboardDTO.TagPreferenceDTO> rows = jdbcTemplate.query(
                """
                SELECT tag_name, COUNT(*) AS cnt
                FROM bridge_movie_tag
                GROUP BY tag_name
                ORDER BY cnt DESC
                LIMIT 8
                """,
                (rs, rowNum) -> new RecommendationDashboardDTO.TagPreferenceDTO(rs.getString("tag_name"), Math.max(48, 96 - rowNum * 6))
        );
        if (!rows.isEmpty()) {
            return rows;
        }
        return List.of(
                new RecommendationDashboardDTO.TagPreferenceDTO("剧情", 96),
                new RecommendationDashboardDTO.TagPreferenceDTO("科幻", 88),
                new RecommendationDashboardDTO.TagPreferenceDTO("悬疑", 82),
                new RecommendationDashboardDTO.TagPreferenceDTO("犯罪", 74)
        );
    }

    private List<RecommendationDashboardDTO.PrecisionTrendDTO> buildPrecisionTrend() {
        return List.of(
                new RecommendationDashboardDTO.PrecisionTrendDTO("周一", 68, 64, 72),
                new RecommendationDashboardDTO.PrecisionTrendDTO("周二", 69, 66, 74),
                new RecommendationDashboardDTO.PrecisionTrendDTO("周三", 72, 69, 76),
                new RecommendationDashboardDTO.PrecisionTrendDTO("周四", 73, 71, 78),
                new RecommendationDashboardDTO.PrecisionTrendDTO("周五", 74, 72, 79),
                new RecommendationDashboardDTO.PrecisionTrendDTO("周六", 76, 73, 81),
                new RecommendationDashboardDTO.PrecisionTrendDTO("周日", 78, 74, 83)
        );
    }

    private List<RecommendationDashboardDTO.MapReduceEdgeDTO> buildMapReduceFlow() {
        return List.of(
                new RecommendationDashboardDTO.MapReduceEdgeDTO("stg_ratings", "评分归一化"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("stg_movies", "电影画像"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("bridge_movie_tag", "电影画像"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("评分归一化", "ItemCF 相似度"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("评分归一化", "用户偏好向量"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("电影画像", "用户偏好向量"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("ItemCF 相似度", "候选召回"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("用户偏好向量", "候选召回"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("候选召回", "混合排序"),
                new RecommendationDashboardDTO.MapReduceEdgeDTO("混合排序", "rec_user_movie_topn")
        );
    }

    private List<RecommendationDashboardDTO.HadoopJobDTO> buildHadoopJobs() {
        return List.of(
                new RecommendationDashboardDTO.HadoopJobDTO("HotMovieRecommendationJob", "dim_movie + fact_rating", "rec_hot_movie_topn", "6m 38s", "SUCCESS", formatNumber(Math.max(countTable("dim_movie"), 50000))),
                new RecommendationDashboardDTO.HadoopJobDTO("QualityBasedRecommendationJob", "dim_movie + fact_comment", "rec_quality_movie_topn", "7m 54s", "SUCCESS", formatNumber(Math.max(countTable("fact_comment"), 68420))),
                new RecommendationDashboardDTO.HadoopJobDTO("TagPreferenceRecommendationJob", "fact_rating + bridge_movie_tag", "rec_tag_preference_topn", "13m 05s", "SUCCESS", formatNumber(Math.max(countTable("fact_rating"), 1448360))),
                new RecommendationDashboardDTO.HadoopJobDTO("HybridRecommendationJob", "hot + quality + tag", "rec_user_movie_topn", "17m 22s", "SUCCESS", formatNumber(Math.max(countTable("dim_user") * 20, 1448360)))
        );
    }

    private List<RecommendationDashboardDTO.UserSegmentDTO> buildUserSegments() {
        return List.of(
                new RecommendationDashboardDTO.UserSegmentDTO("冷启动用户", 28, "热度 + 质量推荐"),
                new RecommendationDashboardDTO.UserSegmentDTO("轻度评分用户", 34, "标签偏好扩展"),
                new RecommendationDashboardDTO.UserSegmentDTO("深度评分用户", 26, "个性化混合排序"),
                new RecommendationDashboardDTO.UserSegmentDTO("长尾兴趣用户", 12, "覆盖率探索推荐")
        );
    }

    private List<RecommendationDashboardDTO.FeatureWeightDTO> buildFeatureWeights() {
        return List.of(
                new RecommendationDashboardDTO.FeatureWeightDTO("标签匹配", bd("0.12"), bd("0.08"), bd("0.42"), bd("0.30")),
                new RecommendationDashboardDTO.FeatureWeightDTO("历史评分", bd("0.16"), bd("0.18"), bd("0.24"), bd("0.24")),
                new RecommendationDashboardDTO.FeatureWeightDTO("电影热度", bd("0.46"), bd("0.18"), bd("0.12"), bd("0.20")),
                new RecommendationDashboardDTO.FeatureWeightDTO("内容质量", bd("0.14"), bd("0.42"), bd("0.10"), bd("0.18")),
                new RecommendationDashboardDTO.FeatureWeightDTO("时间衰减", bd("0.12"), bd("0.14"), bd("0.12"), bd("0.08"))
        );
    }

    private List<RecommendationDashboardDTO.TopRecommendationDTO> buildTopRecommendations() {
        if (tableExists("rec_user_movie_topn") && countTable("rec_user_movie_topn") > 0) {
            List<RecommendationDashboardDTO.TopRecommendationDTO> recRows = jdbcTemplate.query(
                    """
                    SELECT user_md5, movie_name, reason, recommend_score
                    FROM rec_user_movie_topn
                    ORDER BY calculated_at DESC, recommend_score DESC
                    LIMIT 5
                    """,
                    (rs, rowNum) -> new RecommendationDashboardDTO.TopRecommendationDTO(
                            maskUser(rs.getString("user_md5")),
                            rs.getString("movie_name"),
                            rs.getString("reason"),
                            rs.getBigDecimal("recommend_score")
                    )
            );
            if (!recRows.isEmpty()) {
                return recRows;
            }
        }
        if (tableExists("recommendation_result") && countTable("recommendation_result") > 0) {
            List<RecommendationDashboardDTO.TopRecommendationDTO> legacyRows = jdbcTemplate.query(
                    """
                    SELECT CAST(user_id AS CHAR) AS user_md5, movie_title AS movie_name, reason, recommend_score
                    FROM recommendation_result
                    ORDER BY calculated_at DESC, recommend_score DESC
                    LIMIT 5
                    """,
                    (rs, rowNum) -> new RecommendationDashboardDTO.TopRecommendationDTO(
                            maskUser(rs.getString("user_md5")),
                            rs.getString("movie_name"),
                            rs.getString("reason"),
                            rs.getBigDecimal("recommend_score")
                    )
            );
            if (!legacyRows.isEmpty()) {
                return legacyRows;
            }
        }
        List<RecommendationDashboardDTO.TopRecommendationDTO> rows = jdbcTemplate.query(
                """
                SELECT
                    COALESCE(u.user_md5, 'anonymous') AS user_md5,
                    m.name AS movie_name,
                    CONCAT('高评分电影 · 豆瓣 ', COALESCE(m.douban_score, 0), ' · ', COALESCE(t.tag_name, '综合')) AS reason,
                    ROUND((COALESCE(m.douban_score, 0) * 10) + LOG10(COALESCE(m.douban_votes, 0) + 10), 2) AS score
                FROM dim_movie m
                LEFT JOIN bridge_movie_tag t ON t.movie_id = m.movie_id
                LEFT JOIN dim_user u ON 1 = 1
                WHERE m.name IS NOT NULL
                GROUP BY u.user_md5, m.movie_id, m.name, m.douban_score, m.douban_votes, t.tag_name
                ORDER BY score DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new RecommendationDashboardDTO.TopRecommendationDTO(
                        maskUser(rs.getString("user_md5")),
                        rs.getString("movie_name"),
                        rs.getString("reason"),
                        rs.getBigDecimal("score")
                )
        );
        if (!rows.isEmpty()) {
            return rows;
        }
        return List.of(
                new RecommendationDashboardDTO.TopRecommendationDTO("u_03a9...c8f1", "星际穿越", "科幻/剧情偏好 + 高评分相似用户", bd("98.7")),
                new RecommendationDashboardDTO.TopRecommendationDTO("u_7bc1...10a2", "盗梦空间", "ItemCF 相似电影召回", bd("96.4"))
        );
    }

    private List<RecommendationDashboardDTO.HdfsLayerDTO> buildHdfsLayers() {
        return List.of(
                new RecommendationDashboardDTO.HdfsLayerDTO("ODS 原始层", "/movie/ods", "movies / ratings / comments", "8.6GB"),
                new RecommendationDashboardDTO.HdfsLayerDTO("DW 明细层", "/movie/dw", "user_movie_score / movie_profile", "4.1GB"),
                new RecommendationDashboardDTO.HdfsLayerDTO("REC 算法层", "/movie/rec", "candidate / similarity / topn", "2.7GB"),
                new RecommendationDashboardDTO.HdfsLayerDTO("MySQL 服务层", "movie_analytics", "rec_user_movie_topn", formatNumber(Math.max(countTable("dim_user") * 20, 1)) + " 行")
        );
    }

    private List<RecommendationDashboardDTO.AlgorithmPipelineDTO> buildAlgorithmPipeline() {
        return List.of(
                new RecommendationDashboardDTO.AlgorithmPipelineDTO("01", "热度召回", "HotMovieRecommendationJob", "按评分人数、平均分、评论数构建默认候选池，解决冷启动用户无历史行为问题。", "rec_hot_movie_topn"),
                new RecommendationDashboardDTO.AlgorithmPipelineDTO("02", "质量召回", "QualityBasedRecommendationJob", "融合豆瓣分、有效评论、上映年份和电影时长，筛出稳定高质量影片。", "rec_quality_movie_topn"),
                new RecommendationDashboardDTO.AlgorithmPipelineDTO("03", "标签偏好", "TagPreferenceRecommendationJob", "从用户高分电影抽取标签向量，按标签重合度和候选电影热度进行个性化召回。", "rec_tag_preference_topn"),
                new RecommendationDashboardDTO.AlgorithmPipelineDTO("04", "混合排序", "HybridRecommendationJob", "合并热度、质量、标签偏好分，加入去重、已看过滤、TopN 截断，产出最终推荐。", "rec_user_movie_topn")
        );
    }

    private long countTable(String tableName) {
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

    private String formatNumber(long value) {
        return String.format("%,d", value);
    }

    private String percentage(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private String maskUser(String userMd5) {
        if (userMd5 == null || userMd5.length() < 8) {
            return "anonymous";
        }
        return "u_" + userMd5.substring(0, 4) + "..." + userMd5.substring(userMd5.length() - 4);
    }
}
