package com.cev.movie.trigger.http;

import com.cev.movie.api.recommendation.RecommendationDTO;
import com.cev.movie.api.response.ApiResponse;
import com.cev.movie.domain.recommendation.model.Recommendation;
import com.cev.movie.domain.recommendation.service.RecommendationDomainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐 HTTP 接口入口。
 *
 * <p>trigger 层用于接收外部请求，并把领域模型转换为 api 层 DTO。</p>
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationDomainService recommendationDomainService;

    public RecommendationController(RecommendationDomainService recommendationDomainService) {
        this.recommendationDomainService = recommendationDomainService;
    }

    /**
     * 查询指定用户的个性化推荐列表。
     */
    @GetMapping("/users/{userId}")
    public ApiResponse<List<RecommendationDTO>> listUserRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "HYBRID") String algorithm,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<RecommendationDTO> recommendations = recommendationDomainService.listUserRecommendations(userId, algorithm, limit)
                .stream()
                .map(this::toDTO)
                .toList();
        return ApiResponse.success(recommendations);
    }

    private RecommendationDTO toDTO(Recommendation recommendation) {
        return new RecommendationDTO(
                recommendation.getUserId(),
                recommendation.getMovieId(),
                recommendation.getMovieTitle(),
                recommendation.getRecommendScore(),
                recommendation.getReason(),
                recommendation.getAlgorithmType().name()
        );
    }
}
