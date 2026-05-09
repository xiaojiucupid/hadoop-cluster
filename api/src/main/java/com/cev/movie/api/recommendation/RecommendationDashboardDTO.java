package com.cev.movie.api.recommendation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐系统看板聚合 DTO。
 *
 * <p>面向 Vue + ECharts 前端，一次性返回推荐指标、算法评估、Hadoop 作业链路和 TopN 推荐样例。</p>
 */
public record RecommendationDashboardDTO(
        List<MetricCardDTO> metricCards,
        List<AlgorithmScoreDTO> algorithmScores,
        List<RecallStageDTO> recallFunnel,
        List<TagPreferenceDTO> tagPreferences,
        List<PrecisionTrendDTO> precisionTrend,
        List<MapReduceEdgeDTO> mapReduceFlow,
        List<HadoopJobDTO> hadoopJobs,
        List<UserSegmentDTO> userSegments,
        List<FeatureWeightDTO> featureWeights,
        List<TopRecommendationDTO> topRecommendations,
        List<HdfsLayerDTO> hdfsLayers,
        List<AlgorithmPipelineDTO> algorithmPipeline
) {

    public record MetricCardDTO(String label, String value, String trend, String tone) {
    }

    public record AlgorithmScoreDTO(String name, BigDecimal precision, BigDecimal recall, BigDecimal coverage, BigDecimal latency) {
    }

    public record RecallStageDTO(String stage, Long value) {
    }

    public record TagPreferenceDTO(String tag, Integer weight) {
    }

    public record PrecisionTrendDTO(String day, Integer itemCf, Integer tagPreference, Integer hybrid) {
    }

    public record MapReduceEdgeDTO(String source, String target) {
    }

    public record HadoopJobDTO(String name, String input, String output, String duration, String status, String records) {
    }

    public record UserSegmentDTO(String name, Integer value, String strategy) {
    }

    public record FeatureWeightDTO(String feature, BigDecimal hot, BigDecimal quality, BigDecimal tag, BigDecimal hybrid) {
    }

    public record TopRecommendationDTO(String user, String movie, String reason, BigDecimal score) {
    }

    public record HdfsLayerDTO(String name, String path, String files, String size) {
    }

    public record AlgorithmPipelineDTO(String step, String title, String job, String desc, String output) {
    }
}
