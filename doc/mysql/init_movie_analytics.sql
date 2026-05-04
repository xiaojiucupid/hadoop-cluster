-- ============================================================================
-- Movie Analytics 数据库初始化脚本 (init_movie_analytics.sql)
-- 功能：
--   1. 创建数据库 movie_analytics，使用 utf8mb4 字符集以支持全部 Unicode 字符（包括 emoji）。
--   2. 创建数字辅助表 seq_256，用于将多值字段（如演员列表、类型列表）拆分为多行。
--   3. 创建临时暂存表 (stg_*)，用于接收 CSV 原始数据，实现 ETL 的 “E” (Extract) 层。
--   4. 创建维度表 (dim_*)、事实表 (fact_*) 和桥接表 (bridge_*)，
--      构成星型模型，支撑后续分析查询。
--   5. 创建视图 vw_movie_core_metrics，快速展示电影核心指标。
-- 执行顺序：本脚本应先于 load_movie_analytics.sql 执行。
-- ============================================================================

-- 设置客户端与服务器通信的字符集为 utf8mb4，确保中文字符正确传输
SET NAMES utf8mb4;
-- 暂时关闭外键检查，避免建表时因依赖顺序问题报错
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------
-- 1. 创建数据库
--    - 字符集 utf8mb4：支持全部 Unicode 字符，包括特殊符号和 emoji。
--    - 排序规则 utf8mb4_0900_ai_ci：
--        * 基于 Unicode 9.0 标准（MySQL 8.0 默认）。
--        * ai (Accent Insensitive)：不区分重音（如 ü 和 u 视为相同）。
--        * ci (Case Insensitive)：不区分大小写。
--    - 如果数据库已存在，不执行任何操作（IF NOT EXISTS）。
-- ------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `movie_analytics`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 切换到目标数据库
USE `movie_analytics`;

-- ------------------------------------------------------------------------
-- 2. 数字辅助表 seq_256
--    用途：
--      - 在多值字段拆分时，配合 SUBSTRING_INDEX() 等函数，
--        将逗号分隔的演员ID、类型等拆成一列多行。
--      - 包含数字 1 到 256，通常足够拆分任何一部电影的演员或标签数量。
--    方法：
--      - 使用递归 CTE 生成 1~256 的序列。
--      - INSERT IGNORE 保证即使脚本被重复执行也不会插入重复数据。
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seq_256 (
                                       n INT NOT NULL PRIMARY KEY         -- 序号，唯一值
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='通用拆分序列表，提供数字序列 1~256';

-- 填充数字 1~256（如果表已存在且包含数据，则忽略重复键）
INSERT IGNORE INTO seq_256 (n)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 256
)
SELECT n FROM seq;

-- ------------------------------------------------------------------------
-- 3. 暂存表 (Staging Tables)
--    作用：
--      - 作为 CSV 文件的 1:1 映射，直接存储原始文件内容。
--      - 暂存表中的列名和顺序与 CSV 列完全一致。
--      - 后续由 load_movie_analytics.sql 进行清洗、转换、拆分，
--        加载到最终的维度表和事实表。
--    设计要点：
--      - 主键采用业务 ID（movie_id, person_id 等），保证唯一。
--      - 字段大多定义为 VARCHAR 或 TEXT 类型，避免因格式错误导致导入失败。
--      - 字符集与数据库一致。
-- ------------------------------------------------------------------------

-- 电影原始数据暂存表
CREATE TABLE IF NOT EXISTS stg_movies (
                                          movie_id BIGINT NOT NULL,               -- 电影业务ID，唯一标识
                                          name VARCHAR(255) NULL,                 -- 电影名称
                                          alias VARCHAR(500) NULL,                -- 别名/又名，多个用 / 分隔
                                          actors TEXT NULL,                        -- 演员名单（原始文本）
                                          cover VARCHAR(1000) NULL,               -- 封面图片URL
                                          directors TEXT NULL,                     -- 导演名单（原始文本）
                                          douban_score DECIMAL(3,1) NULL,         -- 豆瓣评分（如 8.5）
                                          douban_votes INT NULL,                  -- 豆瓣评分人数
                                          genres VARCHAR(500) NULL,               -- 类型，多个用 / 分隔
                                          imdb_id VARCHAR(64) NULL,               -- IMDB ID
                                          languages VARCHAR(500) NULL,            -- 语言，多个用 / 分隔
                                          mins INT NULL,                          -- 片长（分钟）
                                          official_site VARCHAR(1000) NULL,       -- 官方网站
                                          regions VARCHAR(500) NULL,              -- 制片国家/地区，多个用 / 分隔
                                          release_date VARCHAR(64) NULL,          -- 上映日期原始字符串（如 "2020-01-25(中国大陆)"）
                                          slug VARCHAR(128) NULL,                 -- URL 友好的电影标识
                                          storyline LONGTEXT NULL,                -- 剧情简介
                                          tags VARCHAR(1000) NULL,                -- 标签，多个用 / 分隔
                                          year INT NULL,                          -- 年份
                                          actor_ids LONGTEXT NULL,                -- 演员 ID 列表（逗号分隔）
                                          director_ids LONGTEXT NULL,             -- 导演 ID 列表（逗号分隔）
                                          PRIMARY KEY (movie_id)                  -- 使用业务主键，确保每部电影只有一条暂存记录
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影原始暂存表，1:1 映射 CSV 列';

-- 人物原始数据暂存表
CREATE TABLE IF NOT EXISTS stg_persons (
                                           person_id BIGINT NOT NULL,              -- 人物业务ID
                                           name VARCHAR(255) NULL,                 -- 姓名（通常为中文名）
                                           sex VARCHAR(16) NULL,                   -- 性别
                                           name_en VARCHAR(255) NULL,              -- 英文名
                                           name_zh VARCHAR(255) NULL,              -- 中文名（可能与 name 重复）
                                           birth VARCHAR(64) NULL,                 -- 出生日期原始字符串
                                           birthplace VARCHAR(255) NULL,           -- 出生地
                                           constellatory VARCHAR(64) NULL,         -- 星座
                                           profession VARCHAR(255) NULL,           -- 职业（如演员、导演等）
                                           biography LONGTEXT NULL,                -- 人物简介
                                           PRIMARY KEY (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='人物原始暂存表';

-- 用户原始数据暂存表
CREATE TABLE IF NOT EXISTS stg_users (
                                         user_md5 CHAR(32) NOT NULL,             -- 用户唯一标识（MD5 哈希值）
                                         user_nickname VARCHAR(255) NULL,        -- 用户昵称
                                         PRIMARY KEY (user_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='用户原始暂存表';

-- 评论原始数据暂存表
CREATE TABLE IF NOT EXISTS stg_comments (
                                            comment_id BIGINT NOT NULL,             -- 评论唯一ID
                                            user_md5 CHAR(32) NULL,                 -- 评论者用户 MD5
                                            movie_id BIGINT NULL,                   -- 被评论的电影 ID
                                            content LONGTEXT NULL,                  -- 评论正文
                                            votes INT NULL,                         -- 评论被点赞数
                                            comment_time VARCHAR(64) NULL,          -- 评论时间原始字符串
                                            rating INT NULL,                        -- 评论时附带的对电影的评分（1~5星）
                                            PRIMARY KEY (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='评论原始暂存表';

-- 评分原始数据暂存表
CREATE TABLE IF NOT EXISTS stg_ratings (
                                           rating_id BIGINT NOT NULL,              -- 评分的唯一ID
                                           user_md5 CHAR(32) NULL,                 -- 评分用户 MD5
                                           movie_id BIGINT NULL,                   -- 被评分的电影 ID
                                           rating INT NULL,                        -- 评分值（通常 1~5）
                                           rating_time VARCHAR(64) NULL,           -- 评分时间原始字符串
                                           PRIMARY KEY (rating_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='评分原始暂存表';

-- ------------------------------------------------------------------------
-- 4. 维度表 (Dimension Tables)
--    设计思路：
--      - 使用代理键 (SK) 作为主键，屏蔽业务键的潜在变化。
--      - 保留业务键，并建立唯一索引，确保数据完整性。
--      - 添加 source_loaded_at 列记录数据加载时间，追踪数据变更。
--      - 数据分析时通过代理键关联事实表，提高 JOIN 效率。
-- ------------------------------------------------------------------------

-- 电影维表
CREATE TABLE IF NOT EXISTS dim_movie (
                                         movie_sk BIGINT NOT NULL AUTO_INCREMENT,  -- 代理键，自增主键，用于关联事实表
                                         movie_id BIGINT NOT NULL,                 -- 业务主键，来自源系统
                                         name VARCHAR(255) NULL,
                                         alias VARCHAR(500) NULL,                  -- 经过清洗的别名（保留原始 / 分隔形式）
                                         cover VARCHAR(1000) NULL,
                                         imdb_id VARCHAR(64) NULL,
                                         douban_score DECIMAL(3,1) NULL,           -- 豆瓣评分（数值）
                                         douban_votes INT NULL,
                                         mins INT NULL,
                                         official_site VARCHAR(1000) NULL,
                                         release_date_raw VARCHAR(64) NULL,        -- 原始上映日期字符串
                                         release_year INT NULL,                    -- 提取出的上映年份，用于年度统计
                                         slug VARCHAR(128) NULL,
                                         storyline LONGTEXT NULL,
                                         source_loaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 本条维度记录载入时间
                                         PRIMARY KEY (movie_sk),                   -- 使用代理键作为主键
                                         UNIQUE KEY uk_movie_id (movie_id),        -- 业务键唯一约束，防止重复载入同一部电影
                                         KEY idx_release_year (release_year),      -- 按年份筛选的查询索引
                                         KEY idx_douban_score (douban_score)       -- 按豆瓣评分排序/筛选索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影维表，存放清洗后的电影基本信息';

-- 人物维表
CREATE TABLE IF NOT EXISTS dim_person (
                                          person_sk BIGINT NOT NULL AUTO_INCREMENT,
                                          person_id BIGINT NOT NULL,                -- 业务键
                                          name VARCHAR(255) NULL,
                                          sex VARCHAR(16) NULL,
                                          name_en VARCHAR(255) NULL,
                                          name_zh VARCHAR(255) NULL,
                                          birth_raw VARCHAR(64) NULL,               -- 原始出生日期字符串（后续可进一步标准化）
                                          birthplace VARCHAR(255) NULL,
                                          constellatory VARCHAR(64) NULL,
                                          profession VARCHAR(255) NULL,
                                          biography LONGTEXT NULL,
                                          source_loaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          PRIMARY KEY (person_sk),
                                          UNIQUE KEY uk_person_id (person_id),
                                          KEY idx_name (name)                       -- 按姓名查询的索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='人物维表，存储演员、导演等个人资料';

-- 用户维表
CREATE TABLE IF NOT EXISTS dim_user (
                                        user_sk BIGINT NOT NULL AUTO_INCREMENT,
                                        user_md5 CHAR(32) NOT NULL,               -- 使用 MD5 作为业务键，保护用户隐私
                                        user_nickname VARCHAR(255) NULL,
                                        source_loaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        PRIMARY KEY (user_sk),
                                        UNIQUE KEY uk_user_md5 (user_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='用户维表，存储匿名化后的用户标识及昵称';

-- 类型维表
CREATE TABLE IF NOT EXISTS dim_genre (
                                         genre_sk BIGINT NOT NULL AUTO_INCREMENT,
                                         genre_name VARCHAR(128) NOT NULL,         -- 类型名称（如 "动作", "喜剧"）
                                         PRIMARY KEY (genre_sk),
                                         UNIQUE KEY uk_genre_name (genre_name)     -- 保证类型名称唯一，避免存储重复类型
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='类型维表，标准化电影分类类型';

-- 语言维表
CREATE TABLE IF NOT EXISTS dim_language (
                                            language_sk BIGINT NOT NULL AUTO_INCREMENT,
                                            language_name VARCHAR(128) NOT NULL,       -- 语言名称（如 "英语", "普通话"）
                                            PRIMARY KEY (language_sk),
                                            UNIQUE KEY uk_language_name (language_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='语言维表';

-- 地区维表
CREATE TABLE IF NOT EXISTS dim_region (
                                          region_sk BIGINT NOT NULL AUTO_INCREMENT,
                                          region_name VARCHAR(128) NOT NULL,         -- 地区名称（如 "中国大陆", "美国"）
                                          PRIMARY KEY (region_sk),
                                          UNIQUE KEY uk_region_name (region_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='地区维表';

-- 标签维表
CREATE TABLE IF NOT EXISTS dim_tag (
                                       tag_sk BIGINT NOT NULL AUTO_INCREMENT,
                                       tag_name VARCHAR(128) NOT NULL,            -- 标签名称（如 "黑色幽默", "励志"）
                                       PRIMARY KEY (tag_sk),
                                       UNIQUE KEY uk_tag_name (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='标签维表';

-- ------------------------------------------------------------------------
-- 5. 事实表与桥接表
--    事实表存储业务过程中产生的“事件”或“度量”。
--    桥接表用于处理多对多关系（如一部电影对应多个类型），
--    避免在维度表中存储逗号分隔串，提高查询灵活性和规范性。
-- ------------------------------------------------------------------------

-- 电影-人物关联事实表
-- 记录电影与人物之间的“出演”或“导演”关系，是一种无度量事实表（至少有角色类型）。
CREATE TABLE IF NOT EXISTS fact_movie_person (
                                                 movie_id BIGINT NOT NULL,                   -- 关联电影（业务键，必须已在 dim_movie 中存在）
                                                 person_id BIGINT NOT NULL,                  -- 关联人物（业务键，必须已在 dim_person 中存在）
                                                 role_type ENUM('ACTOR','DIRECTOR') NOT NULL, -- 角色类型：演员 或 导演
                                                 display_name VARCHAR(255) NULL,             -- 显示名称（通常与 dim_person.name 一致，但可独立）
                                                 source_rank INT NULL,                       -- 在原始列表中的排序位置（如第一个演员 rank=1）
                                                 PRIMARY KEY (movie_id, person_id, role_type), -- 联合主键，防止同一人重复添加同一角色
                                                 KEY idx_person_role (person_id, role_type), -- 按人物和角色查询该人参与的电影
    -- 外键约束，保证引用完整性：插入前 dim_movie 和 dim_person 必须已有对应记录
                                                 CONSTRAINT fk_fmp_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id),
                                                 CONSTRAINT fk_fmp_person FOREIGN KEY (person_id) REFERENCES dim_person(person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影人物关联事实表，存储演员和导演的参与关系';

-- 电影-类型桥接表
CREATE TABLE IF NOT EXISTS bridge_movie_genre (
                                                  movie_id BIGINT NOT NULL,
                                                  genre_name VARCHAR(128) NOT NULL,           -- 使用名称而非代理键，降低 JOIN 复杂度（也可关联 dim_genre）
                                                  PRIMARY KEY (movie_id, genre_name),         -- 同一电影同一类型不可重复
                                                  KEY idx_genre_name (genre_name),            -- 按类型查找电影
                                                  CONSTRAINT fk_bmg_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影类型桥接表，实现电影与类型的多对多关系';

-- 电影-语言桥接表
CREATE TABLE IF NOT EXISTS bridge_movie_language (
                                                     movie_id BIGINT NOT NULL,
                                                     language_name VARCHAR(128) NOT NULL,
                                                     PRIMARY KEY (movie_id, language_name),
                                                     KEY idx_language_name (language_name),
                                                     CONSTRAINT fk_bml_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影语言桥接表';

-- 电影-地区桥接表
CREATE TABLE IF NOT EXISTS bridge_movie_region (
                                                   movie_id BIGINT NOT NULL,
                                                   region_name VARCHAR(128) NOT NULL,
                                                   PRIMARY KEY (movie_id, region_name),
                                                   KEY idx_region_name (region_name),
                                                   CONSTRAINT fk_bmr_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影地区桥接表';

-- 电影-标签桥接表
CREATE TABLE IF NOT EXISTS bridge_movie_tag (
                                                movie_id BIGINT NOT NULL,
                                                tag_name VARCHAR(128) NOT NULL,
                                                PRIMARY KEY (movie_id, tag_name),
                                                KEY idx_tag_name (tag_name),
                                                CONSTRAINT fk_bmt_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='电影标签桥接表';

-- 评论事实表
-- 每条评论是一个独立事实，包含评论内容、点赞数、评论时间以及附带的评分。
CREATE TABLE IF NOT EXISTS fact_comment (
                                            comment_id BIGINT NOT NULL,                 -- 评论的业务 ID，来自源系统
                                            user_md5 CHAR(32) NULL,                     -- 评论用户（业务键，对应 dim_user.user_md5）
                                            movie_id BIGINT NOT NULL,                   -- 被评论的电影
                                            content LONGTEXT NULL,                      -- 评论正文
                                            votes INT NULL,                             -- 点赞数
                                            comment_time_raw VARCHAR(64) NULL,          -- 原始评论时间字符串
                                            rating INT NULL,                            -- 该评论附带的评分（1~5 或 NULL）
                                            source_loaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            PRIMARY KEY (comment_id),
                                            KEY idx_comment_movie (movie_id),           -- 按电影查询评论
                                            KEY idx_comment_user (user_md5),            -- 按用户查询评论
                                            CONSTRAINT fk_comment_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='评论事实表，记录用户对电影的评论';

-- 评分事实表
-- 与评论独立存储，因为一次评分不一定伴随评论。
CREATE TABLE IF NOT EXISTS fact_rating (
                                           rating_id BIGINT NOT NULL,                  -- 评分记录的业务 ID
                                           user_md5 CHAR(32) NULL,                     -- 评分用户
                                           movie_id BIGINT NOT NULL,                   -- 被评分的电影
                                           rating INT NOT NULL,                        -- 评分值（1~5，一般不允许为 NULL）
                                           rating_time_raw VARCHAR(64) NULL,           -- 评分时间字符串
                                           source_loaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           PRIMARY KEY (rating_id),
                                           KEY idx_rating_movie (movie_id),
                                           KEY idx_rating_user (user_md5),
                                           KEY idx_rating_value (rating),              -- 按评分值筛选（如查找所有 5 星评分）
                                           CONSTRAINT fk_rating_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='评分事实表，记录用户对电影的单独评分';

-- ------------------------------------------------------------------------
-- 6. 分析视图
--    预计算电影的核心指标，简化上层分析查询。
--    使用 LEFT JOIN 确保即使电影没有评分或评论也会保留记录。
-- ------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_movie_core_metrics AS
SELECT
    m.movie_id,
    m.name,
    m.release_year,
    m.douban_score,
    m.douban_votes,
    COUNT(DISTINCT r.rating_id) AS rating_count,          -- 评分数量
    ROUND(AVG(r.rating), 2) AS avg_user_rating,           -- 平均用户评分（保留两位小数）
    COUNT(DISTINCT c.comment_id) AS comment_count,        -- 评论数量
    COALESCE(SUM(c.votes), 0) AS total_comment_votes      -- 评论的总点赞数（无评论时为 0）
FROM dim_movie m
         LEFT JOIN fact_rating r ON r.movie_id = m.movie_id
         LEFT JOIN fact_comment c ON c.movie_id = m.movie_id
GROUP BY m.movie_id, m.name, m.release_year, m.douban_score, m.douban_votes;

-- 恢复外键检查，确保后续操作的数据完整性
SET FOREIGN_KEY_CHECKS = 1;