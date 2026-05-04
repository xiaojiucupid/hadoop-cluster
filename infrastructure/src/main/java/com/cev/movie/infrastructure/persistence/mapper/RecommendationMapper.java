package com.cev.movie.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cev.movie.infrastructure.persistence.po.RecommendationPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推荐结果 Mapper。
 *
 * <p>由 MyBatis-Plus 生成基础 CRUD 能力，复杂 SQL 后续可在 XML 或注解中扩展。</p>
 */
@Mapper
public interface RecommendationMapper extends BaseMapper<RecommendationPO> {
}
