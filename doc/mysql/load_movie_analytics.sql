-- ============================================================================
-- Movie Analytics 数据转换脚本 (load_movie_analytics.sql)
-- 功能：
--   1. 清空所有维度、事实、桥接表，保证每次导入的幂等性。
--   2. 将暂存表 (stg_*) 的数据清洗后插入维度表 (dim_*)。
--   3. 利用 seq_256 辅助表拆分多值字段（/ 分隔），填充类型、语言、地区、标签维表。
--   4. 建立多对多桥接表，关联电影与类型/语言/地区/标签。
--   5. 拆分演员/导演 ID 列表，填充电影-人物关联事实表。
--   6. 将评论和评分数据导入事实表，仅保留电影维度中实际存在的记录。
-- 前提：本脚本需在 init_movie_analytics.sql 执行之后运行，
--       且 stg_* 暂存表中已通过 LOAD DATA 加载了原始 CSV 数据。
-- ============================================================================

USE `movie_analytics`;
-- 设置客户端字符集，确保中文字符在本次会话中正常处理
SET NAMES utf8mb4;
-- 暂时关闭外键约束检查，以便按任意顺序清空/填充表数据，避免外键冲突
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、清空所有分析表
--    目的：保证脚本的幂等性，每次执行都从干净状态开始重建数据。
--    清空顺序：先事实表/桥接表（依赖维度表），再维度表，避免外键错误。
--    注意：TRUNCATE TABLE 无法在有外键引用的表上执行，除非外键已禁用。
-- ============================================================================
TRUNCATE TABLE fact_comment;
TRUNCATE TABLE fact_rating;
TRUNCATE TABLE fact_movie_person;
TRUNCATE TABLE bridge_movie_genre;
TRUNCATE TABLE bridge_movie_language;
TRUNCATE TABLE bridge_movie_region;
TRUNCATE TABLE bridge_movie_tag;
TRUNCATE TABLE dim_genre;
TRUNCATE TABLE dim_language;
TRUNCATE TABLE dim_region;
TRUNCATE TABLE dim_tag;
TRUNCATE TABLE dim_user;
TRUNCATE TABLE dim_person;
TRUNCATE TABLE dim_movie;

-- ============================================================================
-- 二、填充电影维表 (dim_movie)
--    从 stg_movies 中提取清洗后的数据，将空字符串或无效值转为 NULL。
--    清洗规则：
--      - TRIM(s.column)：去除首尾空格。
--      - NULLIF(..., '')：空字符串转为 NULL（避免存储无意义的空串）。
--      - NULLIF(score/votes/mins, 0)：数值0对分析无意义，转为NULL。
--      - release_year 根据 year 字段推断，若值在合理范围(1800~2100)外则置NULL。
-- ============================================================================
INSERT INTO dim_movie (
    movie_id, name, alias, cover, imdb_id, douban_score, douban_votes,
    mins, official_site, release_date_raw, release_year, slug, storyline
)
SELECT
    s.movie_id,
    NULLIF(TRIM(s.name), ''),               -- 电影名
    NULLIF(TRIM(s.alias), ''),              -- 别名
    NULLIF(TRIM(s.cover), ''),              -- 封面URL
    NULLIF(TRIM(s.imdb_id), ''),            -- IMDB ID
    NULLIF(s.douban_score, 0),             -- 豆瓣评分（0视为缺失）
    NULLIF(s.douban_votes, 0),             -- 评分人数
    NULLIF(s.mins, 0),                     -- 片长
    NULLIF(TRIM(s.official_site), ''),     -- 官网
    NULLIF(TRIM(s.release_date), ''),      -- 原始上映日期
    CASE
        WHEN s.year IS NOT NULL AND s.year BETWEEN 1800 AND 2100 THEN s.year
        ELSE NULL
        END,                                     -- 上映年份（仅保留合理值）
    NULLIF(TRIM(s.slug), ''),              -- 短标签
    NULLIF(TRIM(s.storyline), '')          -- 剧情简介
FROM stg_movies s;

-- ============================================================================
-- 三、填充人物维表 (dim_person)
--    清洗规则同电影维表：去除空格，空字符串转 NULL。
--    保留原始日期字符串，后续可在分析层进一步标准化。
-- ============================================================================
INSERT INTO dim_person (
    person_id, name, sex, name_en, name_zh, birth_raw, birthplace, constellatory, profession, biography
)
SELECT
    s.person_id,
    NULLIF(TRIM(s.name), ''),
    NULLIF(TRIM(s.sex), ''),
    NULLIF(TRIM(s.name_en), ''),
    NULLIF(TRIM(s.name_zh), ''),
    NULLIF(TRIM(s.birth), ''),
    NULLIF(TRIM(s.birthplace), ''),
    NULLIF(TRIM(s.constellatory), ''),
    NULLIF(TRIM(s.profession), ''),
    NULLIF(TRIM(s.biography), '')
FROM stg_persons s;

-- ============================================================================
-- 四、填充用户维表 (dim_user)
--    使用 MD5 值作为业务键，可以保护原始用户ID隐私。
--    清洗昵称，空字符串转为 NULL。
-- ============================================================================
INSERT INTO dim_user (user_md5, user_nickname)
SELECT
    s.user_md5,
    NULLIF(TRIM(s.user_nickname), '')
FROM stg_users s;

-- ============================================================================
-- 五、填充分类维表（genre, language, region, tag）
--    这些字段在 stg_movies 中都是以斜杠 '/' 分隔的多值字符串（如 "动作/科幻/冒险"）。
--    利用 seq_256 表的数字序列，配合 SUBSTRING_INDEX 函数将其拆分为独立的条目，
--    并插入到对应的维表中（去重）。
--    拆分原理（以 genres 为例）：
--      - JOIN seq_256 seq: 对每个可能的序号进行迭代。
--      - JOIN 条件: seq.n <= 多值字段中的分隔符数量 + 1。
--        分隔符数量 = 原字符串长度 - 移除'/'后的长度。
--      - SUBSTRING_INDEX(SUBSTRING_INDEX(genres, '/', seq.n), '/', -1):
--        先取前 seq.n 个由'/'分隔的部分，再取最后一部分，即第 seq.n 个元素。
--      - TRIM 去除元素两端的空格。
--      - 最外层 SELECT DISTINCT 且过滤掉空字符串，保证维表中的值唯一且有效。
-- ============================================================================

-- 5.1 类型维表
INSERT INTO dim_genre (genre_name)
SELECT DISTINCT token
FROM (
         SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(s.genres, '/', seq.n), '/', -1)) AS token
         FROM stg_movies s
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(s.genres, '')) - LENGTH(REPLACE(COALESCE(s.genres, ''), '/', ''))
     ) t
WHERE token <> '';

-- 5.2 语言维表
INSERT INTO dim_language (language_name)
SELECT DISTINCT token
FROM (
         SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(s.languages, '/', seq.n), '/', -1)) AS token
         FROM stg_movies s
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(s.languages, '')) - LENGTH(REPLACE(COALESCE(s.languages, ''), '/', ''))
     ) t
WHERE token <> '';

-- 5.3 地区维表
INSERT INTO dim_region (region_name)
SELECT DISTINCT token
FROM (
         SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(s.regions, '/', seq.n), '/', -1)) AS token
         FROM stg_movies s
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(s.regions, '')) - LENGTH(REPLACE(COALESCE(s.regions, ''), '/', ''))
     ) t
WHERE token <> '';

-- 5.4 标签维表
INSERT INTO dim_tag (tag_name)
SELECT DISTINCT token
FROM (
         SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(s.tags, '/', seq.n), '/', -1)) AS token
         FROM stg_movies s
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(s.tags, '')) - LENGTH(REPLACE(COALESCE(s.tags, ''), '/', ''))
     ) t
WHERE token <> '';

-- ============================================================================
-- 六、填充桥接表（多对多关系）
--    同样使用拆分技术，但这次需要保留 movie_id，构建电影与分类的关联。
--    桥接表直接使用分类名称（genre_name 等）而不通过代理键，
--    降低查询复杂度（但也可通过名称关联到相应的维表）。
--    DISTINCT 确保同一部电影的同一分类只出现一次。
--    WHERE token <> '' 过滤掉因拆分产生的空元素。
-- ============================================================================

-- 6.1 电影-类型桥接
INSERT INTO bridge_movie_genre (movie_id, genre_name)
SELECT DISTINCT s.movie_id, token
FROM (
         SELECT movie_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(genres, '/', seq.n), '/', -1)) AS token
         FROM stg_movies
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(genres, '')) - LENGTH(REPLACE(COALESCE(genres, ''), '/', ''))
     ) s
WHERE token <> '';

-- 6.2 电影-语言桥接
INSERT INTO bridge_movie_language (movie_id, language_name)
SELECT DISTINCT s.movie_id, token
FROM (
         SELECT movie_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(languages, '/', seq.n), '/', -1)) AS token
         FROM stg_movies
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(languages, '')) - LENGTH(REPLACE(COALESCE(languages, ''), '/', ''))
     ) s
WHERE token <> '';

-- 6.3 电影-地区桥接
INSERT INTO bridge_movie_region (movie_id, region_name)
SELECT DISTINCT s.movie_id, token
FROM (
         SELECT movie_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(regions, '/', seq.n), '/', -1)) AS token
         FROM stg_movies
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(regions, '')) - LENGTH(REPLACE(COALESCE(regions, ''), '/', ''))
     ) s
WHERE token <> '';

-- 6.4 电影-标签桥接
INSERT INTO bridge_movie_tag (movie_id, tag_name)
SELECT DISTINCT s.movie_id, token
FROM (
         SELECT movie_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(tags, '/', seq.n), '/', -1)) AS token
         FROM stg_movies
                  JOIN seq_256 seq
                       ON seq.n <= 1 + LENGTH(COALESCE(tags, '')) - LENGTH(REPLACE(COALESCE(tags, ''), '/', ''))
     ) s
WHERE token <> '';

-- ============================================================================
-- 七、填充电影-人物关联事实表 (fact_movie_person)
--    stg_movies 的 actor_ids 和 director_ids 字段格式为：
--      "演员名:person_id|演员名:person_id|..."
--    其中 '|' 是分隔符，':' 前是显示名称，':' 后是人物 ID。
--    分别处理演员（ACTOR）和导演（DIRECTOR），拆分后提取 person_id 和 display_name。
--    使用 seq_256 拆分 '|' 分隔的元素，再通过 SUBSTRING_INDEX 按 ':' 切分。
--    source_rank 记录该人物在原列表中的序号。
--    过滤条件：token 不为空，且包含 ':' 且切出的 person_id 不为空。
-- ============================================================================

-- 7.1 插入演员关系
INSERT INTO fact_movie_person (movie_id, person_id, role_type, display_name, source_rank)
SELECT
    p.movie_id,
    p.person_id,
    'ACTOR' AS role_type,
    p.display_name,
    MIN(p.source_rank) AS source_rank
FROM (
         SELECT
             movie_id,
             CAST(SUBSTRING_INDEX(token, ':', -1) AS UNSIGNED) AS person_id,
             NULLIF(TRIM(SUBSTRING_INDEX(token, ':', 1)), '') AS display_name,
             source_rank
         FROM (
                  SELECT
                      movie_id,
                      TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(actor_ids, '|', seq.n), '|', -1)) AS token,
                      seq.n AS source_rank
                  FROM stg_movies
                           JOIN seq_256 seq
                                ON seq.n <= 1 + LENGTH(COALESCE(actor_ids, '')) - LENGTH(REPLACE(COALESCE(actor_ids, ''), '|', ''))
              ) raw_actor
         WHERE token <> ''
           AND INSTR(token, ':') > 0
           AND NULLIF(TRIM(SUBSTRING_INDEX(token, ':', -1)), '') IS NOT NULL
     ) p
GROUP BY p.movie_id, p.person_id, p.display_name;

-- 7.2 插入导演关系（逻辑与演员完全相同，只是字段改为 director_ids，角色为 DIRECTOR）
INSERT INTO fact_movie_person (movie_id, person_id, role_type, display_name, source_rank)
SELECT
    p.movie_id,
    p.person_id,
    'DIRECTOR' AS role_type,
    p.display_name,
    MIN(p.source_rank) AS source_rank
FROM (
         SELECT
             movie_id,
             CAST(SUBSTRING_INDEX(token, ':', -1) AS UNSIGNED) AS person_id,
             NULLIF(TRIM(SUBSTRING_INDEX(token, ':', 1)), '') AS display_name,
             source_rank
         FROM (
                  SELECT
                      movie_id,
                      TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(director_ids, '|', seq.n), '|', -1)) AS token,
                      seq.n AS source_rank
                  FROM stg_movies
                           JOIN seq_256 seq
                                ON seq.n <= 1 + LENGTH(COALESCE(director_ids, '')) - LENGTH(REPLACE(COALESCE(director_ids, ''), '|', ''))
              ) raw_director
         WHERE token <> ''
           AND INSTR(token, ':') > 0
           AND NULLIF(TRIM(SUBSTRING_INDEX(token, ':', -1)), '') IS NOT NULL
     ) p
GROUP BY p.movie_id, p.person_id, p.display_name;

-- ============================================================================
-- 八、填充评论事实表 (fact_comment)
--    从 stg_comments 中提取，仅保留那些 movie_id 已存在于 dim_movie 的记录，
--    避免因孤立数据导致外键约束失败（虽然外键已禁用，但保证数据一致性）。
--    votes 字段用 COALESCE 确保默认值为 0 而非 NULL。
--    清洗 user_md5、content、comment_time 的空格和空字符串。
-- ============================================================================
INSERT INTO fact_comment (
    comment_id, user_md5, movie_id, content, votes, comment_time_raw, rating
)
SELECT
    s.comment_id,
    NULLIF(TRIM(s.user_md5), ''),
    s.movie_id,
    NULLIF(TRIM(s.content), ''),
    COALESCE(s.votes, 0),                -- 点赞数缺失时归0
    NULLIF(TRIM(s.comment_time), ''),
    s.rating
FROM stg_comments s
         JOIN dim_movie m ON m.movie_id = s.movie_id;  -- 内连接，仅保留电影维表中存在的电影评论

-- ============================================================================
-- 九、填充评分事实表 (fact_rating)
--    逻辑与评论类似，注意 rating 不允许为 NULL（表定义中 NOT NULL），
--    故信任源数据有其值或由清洗环节保证。此处同样只保留有效电影的评分。
-- ============================================================================
INSERT INTO fact_rating (
    rating_id, user_md5, movie_id, rating, rating_time_raw
)
SELECT
    s.rating_id,
    NULLIF(TRIM(s.user_md5), ''),
    s.movie_id,
    s.rating,                            -- 直接使用源评分值（通常 1~5）
    NULLIF(TRIM(s.rating_time), '')
FROM stg_ratings s
         JOIN dim_movie m ON m.movie_id = s.movie_id;

-- ============================================================================
-- 十、恢复外键检查
--     数据填充完成，重新开启外键约束，确保后续操作的数据完整性。
-- ============================================================================
SET FOREIGN_KEY_CHECKS = 1;