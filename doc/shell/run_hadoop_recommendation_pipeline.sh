#!/usr/bin/env bash
set -euo pipefail

# Hadoop 推荐算法端到端执行脚本。
# 建议在云服务器 Hadoop 环境中执行。

PROJECT_DIR="${PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
MYSQL_HOST="${MYSQL_HOST:-139.155.136.111}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-movie_analytics}"
HDFS_INPUT_DIR="${HDFS_INPUT_DIR:-/movie/mysql}"
HDFS_RESULT_DIR="${HDFS_RESULT_DIR:-/movie/result}"
RECOMMEND_TOP_N="${RECOMMEND_TOP_N:-20}"
JAR_PATH="${JAR_PATH:-$PROJECT_DIR/mapreduce/target/mapreduce-1.0.0-SNAPSHOT.jar}"
MAIN_CLASS="com.cev.movie.mapreduce.MovieAnalyticsMapReduceApp"
MYSQL_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"

cd "$PROJECT_DIR"

python doc/python/export_mysql_to_hdfs.py \
  --host "$MYSQL_HOST" \
  --port "$MYSQL_PORT" \
  --user "$MYSQL_USER" \
  --password "$MYSQL_PASSWORD" \
  --database "$MYSQL_DATABASE" \
  --hdfs-dir "$HDFS_INPUT_DIR"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "MapReduce jar not found: $JAR_PATH" >&2
  echo "Please build it first: mvn -pl mapreduce -am -DskipTests package" >&2
  exit 1
fi

hadoop jar "$JAR_PATH" "$MAIN_CLASS" \
  --job=hotRecommend \
  --hotRecommendInput="$HDFS_INPUT_DIR/hot_recommend_candidates" \
  --recommendTopN="$RECOMMEND_TOP_N" \
  --mysqlUrl="$MYSQL_URL" \
  --mysqlUsername="$MYSQL_USER" \
  --mysqlPassword="$MYSQL_PASSWORD"

hadoop jar "$JAR_PATH" "$MAIN_CLASS" \
  --job=recommend \
  --recommendInput="$HDFS_INPUT_DIR/tag_preference_candidates" \
  --recommendTopN="$RECOMMEND_TOP_N" \
  --mysqlUrl="$MYSQL_URL" \
  --mysqlUsername="$MYSQL_USER" \
  --mysqlPassword="$MYSQL_PASSWORD"

hadoop jar "$JAR_PATH" "$MAIN_CLASS" \
  --job=hybridRecommend \
  --hybridInput="$HDFS_INPUT_DIR/tag_preference_candidates" \
  --recommendTopN="$RECOMMEND_TOP_N" \
  --mysqlUrl="$MYSQL_URL" \
  --mysqlUsername="$MYSQL_USER" \
  --mysqlPassword="$MYSQL_PASSWORD"

echo "Hadoop recommendation pipeline completed."
