package com.cev.movie.domain.recommendation.model;

/**
 * 推荐算法类型。
 *
 * <p>用于区分离线推荐结果来源，方便后端按算法查看、对比与做多路召回融合。</p>
 */
public enum RecommendationAlgorithmType {

    /** 基于用户偏好题材的内容推荐 */
    GENRE_PREFERENCE,

    /** 基于全局热度的热门推荐 */
    HOT_SCORE,

    /** 基于用户平均评分偏好的相似用户推荐 */
    USER_CF,

    /** 基于物品相似度与用户历史高分行为的推荐 */
    ITEM_CF,

    /** 基于用户历史高分影片标签的内容推荐 */
    TAG_PREFERENCE,

    /** 基于贝叶斯均值和热门度的高质量影片推荐 */
    QUALITY_SCORE,

    /** 基于用户所在评分层级的分群推荐 */
    SEGMENT_POPULARITY,

    /** 基于近期行为时间衰减的推荐 */
    RECENCY_DECAY,

    /** 多路推荐融合 */
    HYBRID;

    public static RecommendationAlgorithmType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return HYBRID;
        }
        for (RecommendationAlgorithmType value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return HYBRID;
    }
}
