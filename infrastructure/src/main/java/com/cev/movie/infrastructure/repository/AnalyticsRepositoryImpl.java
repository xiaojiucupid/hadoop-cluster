package com.cev.movie.infrastructure.repository;

import com.cev.movie.domain.analytics.model.GenreStat;
import com.cev.movie.domain.analytics.repository.AnalyticsRepository;
import com.cev.movie.infrastructure.persistence.mapper.GenreStatMapper;
import com.cev.movie.infrastructure.persistence.po.GenreStatPO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 数据分析仓储 MySQL 实现。
 */
@Repository
public class AnalyticsRepositoryImpl implements AnalyticsRepository {

    private final GenreStatMapper genreStatMapper;

    public AnalyticsRepositoryImpl(GenreStatMapper genreStatMapper) {
        this.genreStatMapper = genreStatMapper;
    }

    @Override
    public List<GenreStat> listGenreStats() {
        return genreStatMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    private GenreStat toDomain(GenreStatPO po) {
        return new GenreStat(po.getGenre(), po.getMovieCount());
    }
}
