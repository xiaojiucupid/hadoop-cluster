#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <mysql_host> <mysql_port> <mysql_user> <mysql_password>"
  exit 1
fi

MYSQL_HOST="$1"
MYSQL_PORT="$2"
MYSQL_USER="$3"
MYSQL_PASSWORD="$4"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

MYSQL_CMD=("$MYSQL_BIN" -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 -D movie_analytics)

echo "== Top 10 movies by user rating count =="
"${MYSQL_CMD[@]}" -e "SELECT m.name, COUNT(*) AS rating_count, ROUND(AVG(r.rating),2) AS avg_rating FROM fact_rating r JOIN dim_movie m ON m.movie_id = r.movie_id GROUP BY m.movie_id, m.name ORDER BY rating_count DESC, avg_rating DESC LIMIT 10;"

echo "== Top 10 genres by movie count =="
"${MYSQL_CMD[@]}" -e "SELECT genre_name, COUNT(*) AS movie_count FROM bridge_movie_genre GROUP BY genre_name ORDER BY movie_count DESC, genre_name ASC LIMIT 10;"

echo "== Top 10 active users by comments =="
"${MYSQL_CMD[@]}" -e "SELECT COALESCE(u.user_nickname, c.user_md5) AS user_name, COUNT(*) AS comment_count, COALESCE(SUM(c.votes),0) AS total_votes FROM fact_comment c LEFT JOIN dim_user u ON u.user_md5 = c.user_md5 GROUP BY COALESCE(u.user_nickname, c.user_md5) ORDER BY comment_count DESC, total_votes DESC LIMIT 10;"

echo "== Movie core metrics view sample =="
"${MYSQL_CMD[@]}" -e "SELECT * FROM vw_movie_core_metrics ORDER BY rating_count DESC, comment_count DESC LIMIT 10;"
