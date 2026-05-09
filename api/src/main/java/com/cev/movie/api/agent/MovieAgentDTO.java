package com.cev.movie.api.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 电影推荐与数据分析 Agent DTO。
 *
 * <p>后端先从 MySQL/MapReduce 结果表计算指标参数，再把结构化参数交给 Agent 生成分析结论、推荐策略和可执行建议。</p>
 */
public record MovieAgentDTO(
        String question,
        AgentContextDTO context,
        AgentAnswerDTO answer
) {

    public record AgentContextDTO(
            Long userId,
            String userMd5,
            Map<String, Object> metrics,
            List<MovieRecommendationParamDTO> recommendations,
            List<NameValueParamDTO> tagPreferences,
            List<NameValueParamDTO> ratingDistribution,
            List<NameValueParamDTO> regionDistribution
    ) {
    }

    public record MovieRecommendationParamDTO(
            Long movieId,
            String movieName,
            BigDecimal score,
            String reason,
            String algorithmType,
            Integer rankNo
    ) {
    }

    public record NameValueParamDTO(String name, BigDecimal value) {
    }

    public record AgentAnswerDTO(
            String summary,
            List<String> insights,
            List<String> recommendationStrategies,
            List<String> actions,
            List<String> evidence
    ) {
    }
}
