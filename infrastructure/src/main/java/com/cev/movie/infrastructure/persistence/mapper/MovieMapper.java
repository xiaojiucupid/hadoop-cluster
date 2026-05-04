package com.cev.movie.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cev.movie.infrastructure.persistence.po.MoviePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 影视 MyBatis-Plus Mapper。
 *
 * <p>仅负责数据库访问，不承载业务规则。</p>
 */
@Mapper
public interface MovieMapper extends BaseMapper<MoviePO> {
}
