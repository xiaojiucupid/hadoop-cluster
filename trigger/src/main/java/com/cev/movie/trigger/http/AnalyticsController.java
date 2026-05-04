package com.cev.movie.trigger.http;

import com.cev.movie.api.analytics.GenreStatDTO;
import com.cev.movie.api.response.ApiResponse;
import com.cev.movie.domain.analytics.model.GenreStat;
import com.cev.movie.domain.analytics.service.AnalyticsDomainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据分析 HTTP 接口入口。
 *
 * <p>为 Web 可视化大屏、Android 首页图表等客户端提供统计分析结果。</p>
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsDomainService analyticsDomainService;

    public AnalyticsController(AnalyticsDomainService analyticsDomainService) {
        this.analyticsDomainService = analyticsDomainService;
    }

    /**
     * 查询影视题材分布统计。
     */
    @GetMapping("/genres")
    public ApiResponse<List<GenreStatDTO>> listGenreStats() {
        List<GenreStatDTO> stats = analyticsDomainService.listGenreStats().stream()
                .map(this::toDTO)
                .toList();
        return ApiResponse.success(stats);
    }

    private GenreStatDTO toDTO(GenreStat stat) {
        return new GenreStatDTO(stat.getGenre(), stat.getMovieCount());
    }
}
