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

CREATE TABLE IF NOT EXISTS dashboard_metric_cache (
  metric_key VARCHAR(64) NOT NULL COMMENT '指标键，例如 user_count/movie_count/rating_count',
  metric_value BIGINT NOT NULL DEFAULT 0 COMMENT '指标值',
  metric_label VARCHAR(128) DEFAULT NULL COMMENT '指标说明',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐看板轻量指标缓存表';

CREATE TABLE IF NOT EXISTS dashboard_tag_preference_cache (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  tag_name VARCHAR(128) NOT NULL COMMENT '标签名称',
  weight INT NOT NULL DEFAULT 0 COMMENT '前端展示权重',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_name (tag_name),
  KEY idx_sort_weight (sort_no, weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐看板标签偏好缓存表';

CREATE TABLE IF NOT EXISTS dashboard_top_recommendation_cache (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_label VARCHAR(64) NOT NULL COMMENT '脱敏用户展示标识',
  movie_name VARCHAR(255) NOT NULL COMMENT '电影名称',
  reason VARCHAR(500) DEFAULT NULL COMMENT '推荐理由',
  recommend_score DECIMAL(14,4) NOT NULL DEFAULT 0 COMMENT '推荐分',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (id),
  KEY idx_sort_score (sort_no, recommend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐看板 TopN 推荐缓存表';

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

-- ---------------------------------------------------------------------------
-- 推荐看板缓存刷新 SQL。
-- 大表聚合请在离线导入或 MapReduce 流水线结束后执行一次，前端接口只读缓存表，避免每次请求扫描大表。
-- 如果线上库暂时没有部分基础表，可先手工向 dashboard_*_cache 写入演示数据。
-- ---------------------------------------------------------------------------

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'user_count', COUNT(*), '推荐覆盖用户', NOW() FROM dim_user;

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'movie_count', COUNT(*), '候选电影画像', NOW() FROM dim_movie;

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'rating_count', COUNT(*), '评分行为样本', NOW() FROM fact_rating;

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'comment_count', COUNT(*), '评论行为样本', NOW() FROM fact_comment;

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'tag_bridge_count', COUNT(*), '标签关联特征', NOW() FROM bridge_movie_tag;

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'quality_movie_count', COUNT(*), '高质量电影数', NOW()
FROM dim_movie
WHERE COALESCE(douban_score, 0) >= 7.5;

REPLACE INTO dashboard_metric_cache (metric_key, metric_value, metric_label, calculated_at)
SELECT 'tag_candidate_count', COUNT(DISTINCT movie_id), '标签候选电影数', NOW()
FROM bridge_movie_tag;

TRUNCATE TABLE dashboard_tag_preference_cache;
INSERT INTO dashboard_tag_preference_cache (tag_name, weight, sort_no, calculated_at)
SELECT tag_name, GREATEST(48, 96 - (rn - 1) * 6) AS weight, rn AS sort_no, NOW()
FROM (
  SELECT tag_name, ROW_NUMBER() OVER (ORDER BY cnt DESC, tag_name ASC) AS rn
  FROM (
    SELECT tag_name, COUNT(*) AS cnt
    FROM bridge_movie_tag
    GROUP BY tag_name
  ) t
) ranked
WHERE rn <= 8;

TRUNCATE TABLE dashboard_top_recommendation_cache;
INSERT INTO dashboard_top_recommendation_cache (user_label, movie_name, reason, recommend_score, sort_no, calculated_at)
SELECT
  CONCAT('u_', LEFT(user_md5, 4), '...', RIGHT(user_md5, 4)) AS user_label,
  movie_name,
  reason,
  recommend_score,
  ROW_NUMBER() OVER (ORDER BY calculated_at DESC, recommend_score DESC) AS sort_no,
  NOW()
FROM rec_user_movie_topn
ORDER BY calculated_at DESC, recommend_score DESC
LIMIT 5;
