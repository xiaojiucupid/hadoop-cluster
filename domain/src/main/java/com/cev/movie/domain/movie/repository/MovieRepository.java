package com.cev.movie.domain.movie.repository;

import com.cev.movie.domain.movie.model.Movie;

import java.util.List;
import java.util.Optional;

/**
 * 影视仓储接口。
 *
 * <p>domain 层只依赖该抽象，不关心底层是 MySQL、Hive 还是缓存，具体实现放在
 * infrastructure 层。</p>
 */
public interface MovieRepository {

    /**
     * 根据影片 ID 查询影视信息。
     */
    Optional<Movie> findById(Long id);

    /**
     * 查询热门影片列表。
     */
    List<Movie> findHotMovies(int limit);
}
