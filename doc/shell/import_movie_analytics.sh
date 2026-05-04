#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Movie Analytics 数据导入脚本
# 用途：将 CSV 格式的原始电影数据导入 MySQL，并建立分析模型
# 依赖：mysql 客户端，CSV 文件（以 tab 分隔），初始化/转换 SQL 脚本
# 注意：运行前请确保 MySQL 连接参数正确，且 CSV 文件路径有效
# -----------------------------------------------------------------------------
set -euo pipefail

# --- 参数检查 ---
#if [[ $# -lt 4 ]]; then
#  echo "Usage: $0 <mysql_host> <mysql_port> <mysql_user> <mysql_password> [csv_dir] [script_dir]"
#  echo "  csv_dir     : directory containing CSV files (default: script directory)"
#  echo "  script_dir  : directory containing SQL scripts (default: script directory)"
#  echo "Example: $0 139.155.136.111 13306 root 123456"
#  exit 1
#fi

# 命令行参数赋值
MYSQL_HOST="139.155.136.111"
MYSQL_PORT="13306"
MYSQL_USER="root"
MYSQL_PASSWORD="123456"
CSV_DIR="."           # 存放 movies.csv, person.csv, comment.csv, ratings.csv, users.csv 的目录
SCRIPT_DIR="."        # 存放 init_movie_analytics.sql 和 load_movie_analytics.sql 的目录
# 可选的 mysql 客户端路径，若未设置则使用系统默认的 `mysql`
MYSQL_BIN="${MYSQL_BIN:-mysql}"

# 关键 SQL 文件路径
INIT_SQL="${SCRIPT_DIR}/init_movie_analytics.sql"
LOAD_SQL="${SCRIPT_DIR}/load_movie_analytics.sql"

# --- 前置检查：确保所有必需文件均存在 ---
for file in "$INIT_SQL" "$LOAD_SQL" \
            "$CSV_DIR/movies.csv" "$CSV_DIR/person.csv" "$CSV_DIR/comment.csv" "$CSV_DIR/ratings.csv" "$CSV_DIR/users.csv"; do
  if [[ ! -f "$file" ]]; then
    echo "Required file not found: $file"
    exit 1
  fi
done

# 构建 MySQL 连接命令数组，方便后续统一调用
MYSQL_CMD=("$MYSQL_BIN" -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --local-infile=1 --default-character-set=utf8mb4)

# ===================================================
# 第 1 步：初始化数据库和表结构
# ===================================================
echo "[1/4] Initializing database and schema..."
"${MYSQL_CMD[@]}" < "$INIT_SQL"

# ===================================================
# 第 2 步：将 CSV 文件数据加载到 staging 表
# 执行前清空 staging 表，保证幂等性
# ===================================================
echo "[2/4] Loading CSV files into staging tables..."
"${MYSQL_CMD[@]}" <<SQL
USE movie_analytics;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;          -- 暂时关闭外键检查以加速导入

-- 清空所有 staging 表（避免重复导入造成数据重复）
TRUNCATE TABLE stg_movies;
TRUNCATE TABLE stg_persons;
TRUNCATE TABLE stg_users;
TRUNCATE TABLE stg_comments;
TRUNCATE TABLE stg_ratings;

-- 导入电影主数据
LOAD DATA LOCAL INFILE '${CSV_DIR}/movies.csv'
INTO TABLE stg_movies
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'           -- CSV 使用 tab 分隔
OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES                      -- 跳过标题行
(movie_id, name, alias, actors, cover, directors, douban_score, douban_votes, genres, imdb_id, languages, mins, official_site, regions, release_date, slug, storyline, tags, year, actor_ids, director_ids);

-- 导入人物数据
LOAD DATA LOCAL INFILE '${CSV_DIR}/person.csv'
INTO TABLE stg_persons
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(person_id, name, sex, name_en, name_zh, birth, birthplace, constellatory, profession, biography);

-- 导入用户数据
LOAD DATA LOCAL INFILE '${CSV_DIR}/users.csv'
INTO TABLE stg_users
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(user_md5, user_nickname);

-- 导入评论数据
LOAD DATA LOCAL INFILE '${CSV_DIR}/comment.csv'
INTO TABLE stg_comments
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(comment_id, user_md5, movie_id, content, votes, comment_time, rating);

-- 导入评分数据
LOAD DATA LOCAL INFILE '${CSV_DIR}/ratings.csv'
INTO TABLE stg_ratings
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(rating_id, user_md5, movie_id, rating, rating_time);

SET FOREIGN_KEY_CHECKS = 1;         -- 恢复外键检查
SQL

# ===================================================
# 第 3 步：执行数据转换，将 staging 数据清洗并加载到维度/事实表
# ===================================================
echo "[3/4] Transforming staging data into analytics tables..."
"${MYSQL_CMD[@]}" < "$LOAD_SQL"

# ===================================================
# 第 4 步：汇总各分析表的记录数，快速验证导入结果
# ===================================================
echo "[4/4] Import summary"
"${MYSQL_CMD[@]}" -e "USE movie_analytics; SELECT 'dim_movie' AS table_name, COUNT(*) AS total FROM dim_movie UNION ALL SELECT 'dim_person', COUNT(*) FROM dim_person UNION ALL SELECT 'dim_user', COUNT(*) FROM dim_user UNION ALL SELECT 'fact_comment', COUNT(*) FROM fact_comment UNION ALL SELECT 'fact_rating', COUNT(*) FROM fact_rating UNION ALL SELECT 'fact_movie_person', COUNT(*) FROM fact_movie_person;"

echo "Done. Database 'movie_analytics' is ready for analysis and visualization."