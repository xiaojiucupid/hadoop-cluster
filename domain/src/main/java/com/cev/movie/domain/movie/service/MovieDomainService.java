package com.cev.movie.domain.movie.service;

import com.cev.movie.domain.movie.model.Movie;
import com.cev.movie.domain.movie.repository.MovieRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 影视领域服务。
 *
 * <p>负责承载与影视相关的核心业务逻辑，例如热门影片查询、影片详情校验、
 * 推荐候选集过滤等。当前先提供基础框架，后续功能逐步扩展。</p>
 */
@Service
public class MovieDomainService {

    private static final int DEFAULT_HOT_LIMIT = 10;

    private final MovieRepository movieRepository;

    public MovieDomainService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * 查询热门影片。
     *
     * @param limit 返回数量，小于等于 0 时使用默认值
     * @return 热门影片列表
     */
    public List<Movie> listHotMovies(int limit) {
        int queryLimit = limit > 0 ? limit : DEFAULT_HOT_LIMIT;
        return movieRepository.findHotMovies(queryLimit);
    }
}
