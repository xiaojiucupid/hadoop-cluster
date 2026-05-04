package com.cev.movie.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cev.movie.domain.recommendation.model.Recommendation;
import com.cev.movie.domain.recommendation.model.RecommendationAlgorithmType;
import com.cev.movie.domain.recommendation.repository.RecommendationRepository;
import com.cev.movie.infrastructure.persistence.mapper.RecommendationMapper;
import com.cev.movie.infrastructure.persistence.po.RecommendationPO;
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

    public RecommendationRepositoryImpl(RecommendationMapper recommendationMapper) {
        this.recommendationMapper = recommendationMapper;
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
    public List<Recommendation> findByUserIdAndAlgorithms(Long userId, List<RecommendationAlgorithmType> algorithmTypes, int limitPerAlgorithm) {
        return algorithmTypes.stream()
                .flatMap(algorithmType -> findByUserId(userId, algorithmType, limitPerAlgorithm).stream())
                .sorted(Comparator.comparing(Recommendation::getRecommendScore).reversed())
                .toList();
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
