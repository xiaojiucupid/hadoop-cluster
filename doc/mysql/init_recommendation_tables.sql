-- ============================================================================
-- 推荐系统结果表初始化脚本
-- 功能：
--   1. 创建 Hadoop/MapReduce 推荐算法结果表。
--   2. 创建面向 Spring Boot/Vue 看板的统一推荐结果视图。
--   3. 保留 recommendation_result 兼容项目现有 MapReduce 写入逻辑。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `movie_analytics`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `movie_analytics`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS rec_hot_movie_topn (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_name VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  hot_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '热度分',
  rank_no INT NOT NULL COMMENT '排名',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank_no (rank_no),
  KEY idx_movie_id (movie_id),
  KEY idx_hot_score (hot_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hadoop 热门电影 TopN 推荐表';

CREATE TABLE IF NOT EXISTS rec_quality_movie_topn (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_name VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  quality_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '质量分',
  rank_no INT NOT NULL COMMENT '排名',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank_no (rank_no),
  KEY idx_movie_id (movie_id),
  KEY idx_quality_score (quality_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hadoop 高质量电影 TopN 推荐表';

CREATE TABLE IF NOT EXISTS rec_tag_preference_topn (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_md5 CHAR(32) NOT NULL COMMENT '用户标识',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_name VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  recommend_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '推荐分',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  rank_no INT NOT NULL COMMENT '用户内排名',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_movie (user_md5, movie_id),
  KEY idx_user_rank (user_md5, rank_no),
  KEY idx_score (recommend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hadoop 用户标签偏好推荐表';

CREATE TABLE IF NOT EXISTS rec_user_cf_topn (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_md5 CHAR(32) NOT NULL COMMENT '用户标识',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_name VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  recommend_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '推荐分',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  rank_no INT NOT NULL COMMENT '用户内排名',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_movie (user_md5, movie_id),
  KEY idx_user_rank (user_md5, rank_no),
  KEY idx_score (recommend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hadoop UserCF 推荐表';

CREATE TABLE IF NOT EXISTS rec_item_cf_topn (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_md5 CHAR(32) NOT NULL COMMENT '用户标识',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_name VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  recommend_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '推荐分',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  rank_no INT NOT NULL COMMENT '用户内排名',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_movie (user_md5, movie_id),
  KEY idx_user_rank (user_md5, rank_no),
  KEY idx_score (recommend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hadoop ItemCF 推荐表';

CREATE TABLE IF NOT EXISTS rec_user_movie_topn (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_md5 CHAR(32) NOT NULL COMMENT '用户标识',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_name VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  recommend_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '综合推荐分',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  algorithm_type VARCHAR(32) NOT NULL DEFAULT 'HYBRID' COMMENT '算法类型',
  rank_no INT NOT NULL COMMENT '用户内排名',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_movie_algo (user_md5, movie_id, algorithm_type),
  KEY idx_user_algo_rank (user_md5, algorithm_type, rank_no),
  KEY idx_score (recommend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hadoop 混合推荐最终 TopN 表';

CREATE TABLE IF NOT EXISTS recommendation_result (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '兼容旧版 MapReduce 的数值用户ID',
  movie_id BIGINT NOT NULL COMMENT '电影ID',
  movie_title VARCHAR(255) DEFAULT NULL COMMENT '电影名称',
  recommend_score DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '推荐分',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  algorithm_type VARCHAR(32) NOT NULL DEFAULT 'GENRE_PREFERENCE' COMMENT '算法类型',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_movie_algo (user_id, movie_id, algorithm_type),
  KEY idx_user_algo_score (user_id, algorithm_type, recommend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='兼容旧版 MapReduce 个性化推荐结果表';

DROP VIEW IF EXISTS v_recommendation_dashboard_topn;
CREATE VIEW v_recommendation_dashboard_topn AS
SELECT
  user_md5,
  movie_id,
  movie_name,
  recommend_score,
  reason,
  algorithm_type,
  rank_no,
  calculated_at
FROM rec_user_movie_topn
UNION ALL
SELECT
  CAST(user_id AS CHAR(32)) AS user_md5,
  movie_id,
  movie_title AS movie_name,
  recommend_score,
  reason,
  algorithm_type,
  ROW_NUMBER() OVER (PARTITION BY user_id, algorithm_type ORDER BY recommend_score DESC) AS rank_no,
  calculated_at
FROM recommendation_result;
