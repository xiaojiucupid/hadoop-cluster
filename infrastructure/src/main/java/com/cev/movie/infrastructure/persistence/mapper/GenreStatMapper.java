package com.cev.movie.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cev.movie.infrastructure.persistence.po.GenreStatPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题材统计 MyBatis-Plus Mapper。
 */
@Mapper
public interface GenreStatMapper extends BaseMapper<GenreStatPO> {
}
