package com.cev.movie.domain.recommendation.service;

import com.cev.movie.domain.recommendation.model.Recommendation;
import com.cev.movie.domain.recommendation.model.RecommendationAlgorithmType;
import com.cev.movie.domain.recommendation.repository.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐领域服务。
 *
 * <p>用于承载个性化推荐相关业务逻辑，例如推荐结果过滤、兜底策略、
 * 推荐理由生成与多路召回融合。</p>
 */
@Service
public class RecommendationDomainService {

    private static final int DEFAULT_RECOMMEND_LIMIT = 10;

    private final RecommendationRepository recommendationRepository;

    public RecommendationDomainService(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    /**
     * 获取用户个性化推荐列表。
     *
     * @param userId 用户 ID
     * @param algorithm 算法类型编码，为空时默认返回 HYBRID 融合推荐
     * @param limit 返回数量，小于等于 0 时使用默认数量
     * @return 推荐列表
     */
    public List<Recommendation> listUserRecommendations(Long userId, String algorithm, int limit) {
        int queryLimit = limit > 0 ? limit : DEFAULT_RECOMMEND_LIMIT;
        RecommendationAlgorithmType algorithmType = RecommendationAlgorithmType.fromCode(algorithm);
        if (RecommendationAlgorithmType.HYBRID.equals(algorithmType)) {
            return listHybridRecommendations(userId, queryLimit);
        }
        return recommendationRepository.findByUserId(userId, algorithmType, queryLimit);
    }

    private List<Recommendation> listHybridRecommendations(Long userId, int limit) {
        List<Recommendation> offlineHybrid = recommendationRepository.findByUserId(userId, RecommendationAlgorithmType.HYBRID, limit);
        if (!offlineHybrid.isEmpty()) {
            return offlineHybrid;
        }

        List<RecommendationAlgorithmType> recallAlgorithms = List.of(
                RecommendationAlgorithmType.ITEM_CF,
                RecommendationAlgorithmType.GENRE_PREFERENCE,
                RecommendationAlgorithmType.TAG_PREFERENCE,
                RecommendationAlgorithmType.QUALITY_SCORE,
                RecommendationAlgorithmType.SEGMENT_POPULARITY,
                RecommendationAlgorithmType.RECENCY_DECAY,
                RecommendationAlgorithmType.USER_CF,
                RecommendationAlgorithmType.HOT_SCORE
        );
        List<Recommendation> recalls = recommendationRepository.findByUserIdAndAlgorithms(userId, recallAlgorithms, limit);
        return mergeRecalls(recalls, limit);
    }

    private List<Recommendation> mergeRecalls(List<Recommendation> recalls, int limit) {
        Map<Long, RecommendationAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (Recommendation recommendation : recalls) {
            double weight = weightOf(recommendation.getAlgorithmType());
            RecommendationAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    recommendation.getMovieId(),
                    movieId -> new RecommendationAccumulator(recommendation)
            );
            accumulator.add(recommendation, weight);
        }
        return accumulatorMap.values().stream()
                .sorted(Comparator.comparing(RecommendationAccumulator::score).reversed())
                .limit(limit)
                .map(RecommendationAccumulator::toHybridRecommendation)
                .toList();
    }

    private double weightOf(RecommendationAlgorithmType algorithmType) {
        return switch (algorithmType) {
            case ITEM_CF -> 0.26D;
            case GENRE_PREFERENCE -> 0.18D;
            case TAG_PREFERENCE -> 0.14D;
            case QUALITY_SCORE -> 0.12D;
            case SEGMENT_POPULARITY -> 0.10D;
            case RECENCY_DECAY -> 0.10D;
            case USER_CF -> 0.06D;
            case HOT_SCORE -> 0.04D;
            case HYBRID -> 1.00D;
        };
    }

    private static final class RecommendationAccumulator {
        private final Long userId;
        private final Long movieId;
        private final String movieTitle;
        private BigDecimal score = BigDecimal.ZERO;
        private final List<String> reasons = new ArrayList<>();
        private final List<String> sources = new ArrayList<>();

        private RecommendationAccumulator(Recommendation recommendation) {
            this.userId = recommendation.getUserId();
            this.movieId = recommendation.getMovieId();
            this.movieTitle = recommendation.getMovieTitle();
        }

        private void add(Recommendation recommendation, double weight) {
            BigDecimal weightedScore = recommendation.getRecommendScore()
                    .multiply(BigDecimal.valueOf(weight))
                    .setScale(4, RoundingMode.HALF_UP);
            this.score = this.score.add(weightedScore);
            String source = recommendation.getAlgorithmType().name();
            if (!sources.contains(source)) {
                sources.add(source);
            }
            if (recommendation.getReason() != null && !recommendation.getReason().isBlank() && reasons.size() < 2) {
                reasons.add(recommendation.getReason());
            }
        }

        private BigDecimal score() {
            return score;
        }

        private Recommendation toHybridRecommendation() {
            String reason = "后端融合推荐来源：" + String.join("+", sources);
            if (!reasons.isEmpty()) {
                reason += "。" + String.join("；", reasons);
            }
            return new Recommendation(
                    userId,
                    movieId,
                    movieTitle,
                    score,
                    reason,
                    RecommendationAlgorithmType.HYBRID
            );
        }
    }
}
