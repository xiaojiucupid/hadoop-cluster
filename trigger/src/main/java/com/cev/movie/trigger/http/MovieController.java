package com.cev.movie.trigger.http;

import com.cev.movie.api.movie.MovieSummaryDTO;
import com.cev.movie.api.response.ApiResponse;
import com.cev.movie.domain.movie.model.Movie;
import com.cev.movie.domain.movie.service.MovieDomainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 影视 HTTP 接口入口。
 *
 * <p>trigger 层只负责协议适配、参数接收和 DTO 转换，不直接访问数据库。</p>
 */
@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieDomainService movieDomainService;

    public MovieController(MovieDomainService movieDomainService) {
        this.movieDomainService = movieDomainService;
    }

    /**
     * 查询热门影视榜单。
     */
    @GetMapping("/hot")
    public ApiResponse<List<MovieSummaryDTO>> listHotMovies(@RequestParam(defaultValue = "10") Integer limit) {
        List<MovieSummaryDTO> movies = movieDomainService.listHotMovies(limit).stream()
                .map(this::toDTO)
                .toList();
        return ApiResponse.success(movies);
    }

    private MovieSummaryDTO toDTO(Movie movie) {
        return new MovieSummaryDTO(movie.getId(), movie.getTitle(), movie.getGenre(), movie.getScore(), movie.getReleaseYear());
    }
}
