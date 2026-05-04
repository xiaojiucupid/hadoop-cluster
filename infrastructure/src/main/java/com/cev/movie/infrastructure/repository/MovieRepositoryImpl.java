package com.cev.movie.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cev.movie.domain.movie.model.Movie;
import com.cev.movie.domain.movie.repository.MovieRepository;
import com.cev.movie.infrastructure.persistence.mapper.MovieMapper;
import com.cev.movie.infrastructure.persistence.po.MoviePO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 影视仓储 MySQL 实现。
 *
 * <p>负责将数据库 PO 转换为 domain 层可理解的领域模型。</p>
 */
@Repository
public class MovieRepositoryImpl implements MovieRepository {

    private final MovieMapper movieMapper;

    public MovieRepositoryImpl(MovieMapper movieMapper) {
        this.movieMapper = movieMapper;
    }

    @Override
    public Optional<Movie> findById(Long id) {
        return Optional.ofNullable(movieMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Movie> findHotMovies(int limit) {
        LambdaQueryWrapper<MoviePO> queryWrapper = new LambdaQueryWrapper<MoviePO>()
                .orderByDesc(MoviePO::getHotScore)
                .last("LIMIT " + limit);
        return movieMapper.selectList(queryWrapper).stream().map(this::toDomain).toList();
    }

    private Movie toDomain(MoviePO po) {
        return new Movie(po.getId(), po.getTitle(), po.getGenre(), po.getScore(), po.getReleaseYear());
    }
}
