package com.cev.movie.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cev.movie.domain.recommendation.model.Recommendation;
import com.cev.movie.domain.recommendation.model.RecommendationAlgorithmType;
import com.cev.movie.domain.recommendation.repository.RecommendationRepository;
import com.cev.movie.infrastructure.persistence.mapper.RecommendationMapper;
import com.cev.movie.infrastructure.persistence.po.RecommendationPO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

/**
 * 推荐仓储 MySQL 实现。
 *
 * <p>负责读取离线推荐结果表，并转换为 domain 层使用的推荐领域模型。</p>
 */
@Repository
public class RecommendationRepositoryImpl implements RecommendationRepository {

    private final RecommendationMapper recommendationMapper;
    private final JdbcTemplate jdbcTemplate;

    public RecommendationRepositoryImpl(RecommendationMapper recommendationMapper, JdbcTemplate jdbcTemplate) {
        this.recommendationMapper = recommendationMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Recommendation> findByUserId(Long userId, RecommendationAlgorithmType algorithmType, int limit) {
        LambdaQueryWrapper<RecommendationPO> queryWrapper = new LambdaQueryWrapper<RecommendationPO>()
                .eq(RecommendationPO::getUserId, userId)
                .eq(RecommendationPO::getAlgorithmType, algorithmType.name())
                .orderByDesc(RecommendationPO::getRecommendScore)
                .last("LIMIT " + limit);
        return recommendationMapper.selectList(queryWrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Recommendation> findByUserKey(String userKey, RecommendationAlgorithmType algorithmType, int limit) {
        if (userKey != null && userKey.matches("\\d+")) {
            List<Recommendation> legacyResults = findByUserId(Long.valueOf(userKey), algorithmType, limit);
            if (!legacyResults.isEmpty()) {
                return legacyResults;
            }
        }
        if (!tableExists("rec_user_movie_topn")) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT
                    movie_id,
                    movie_name,
                    recommend_score,
                    reason,
                    algorithm_type
                FROM rec_user_movie_topn
                WHERE user_md5 = ? AND algorithm_type = ?
                ORDER BY recommend_score DESC, rank_no ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new Recommendation(
                        null,
                        rs.getLong("movie_id"),
                        rs.getString("movie_name"),
                        rs.getBigDecimal("recommend_score"),
                        rs.getString("reason"),
                        RecommendationAlgorithmType.fromCode(rs.getString("algorithm_type"))
                ),
                userKey,
                algorithmType.name(),
                limit
        );
    }

    @Override
    public List<Recommendation> findByUserIdAndAlgorithms(Long userId, List<RecommendationAlgorithmType> algorithmTypes, int limitPerAlgorithm) {
        return findByUserKeyAndAlgorithms(String.valueOf(userId), algorithmTypes, limitPerAlgorithm);
    }

    @Override
    public List<Recommendation> findByUserKeyAndAlgorithms(String userKey, List<RecommendationAlgorithmType> algorithmTypes, int limitPerAlgorithm) {
        return algorithmTypes.stream()
                .flatMap(algorithmType -> findByUserKey(userKey, algorithmType, limitPerAlgorithm).stream())
                .sorted(Comparator.comparing(Recommendation::getRecommendScore).reversed())
                .toList();
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

    private Recommendation toDomain(RecommendationPO po) {
        return new Recommendation(
                po.getUserId(),
                po.getMovieId(),
                po.getMovieTitle(),
                po.getRecommendScore(),
                po.getReason(),
                RecommendationAlgorithmType.fromCode(po.getAlgorithmType())
        );
    }
}
