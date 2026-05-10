package com.cev.movie.domain.recommendation.repository;

import com.cev.movie.domain.recommendation.model.Recommendation;
import com.cev.movie.domain.recommendation.model.RecommendationAlgorithmType;

import java.util.List;

/**
 * 推荐仓储接口。
 *
 * <p>domain 层只声明业务需要的数据访问能力，具体从 MySQL、Hive、Redis
 * 还是离线推荐结果表读取，由 infrastructure 层实现。</p>
 */
public interface RecommendationRepository {

    /**
     * 查询指定用户的推荐列表。
     *
     * @param userId 用户 ID
     * @param algorithmType 算法类型，空时由实现层决定默认策略
     * @param limit 返回数量
     * @return 推荐结果列表
     */
    List<Recommendation> findByUserId(Long userId, RecommendationAlgorithmType algorithmType, int limit);

    /**
     * 查询指定用户标识的推荐列表，兼容 rec_user_movie_topn.user_md5。
     *
     * @param userKey 用户数值 ID 或 MD5 标识
     * @param algorithmType 算法类型
     * @param limit 返回数量
     * @return 推荐结果列表
     */
    List<Recommendation> findByUserKey(String userKey, RecommendationAlgorithmType algorithmType, int limit);

    /**
     * 查询指定用户在多种算法下的推荐结果，用于后端做融合、兜底和多路召回编排。
     *
     * @param userId 用户 ID
     * @param algorithmTypes 算法类型集合
     * @param limitPerAlgorithm 每种算法最多返回条数
     * @return 推荐结果列表
     */
    List<Recommendation> findByUserIdAndAlgorithms(Long userId, List<RecommendationAlgorithmType> algorithmTypes, int limitPerAlgorithm);

    /**
     * 查询指定用户标识在多种算法下的推荐结果，兼容 rec_user_movie_topn.user_md5。
     *
     * @param userKey 用户数值 ID 或 MD5 标识
     * @param algorithmTypes 算法类型集合
     * @param limitPerAlgorithm 每种算法最多返回条数
     * @return 推荐结果列表
     */
    List<Recommendation> findByUserKeyAndAlgorithms(String userKey, List<RecommendationAlgorithmType> algorithmTypes, int limitPerAlgorithm);
}
