#!/usr/bin/env python3
"""Export movie analytics MySQL tables to local TSV files and optionally upload them to HDFS.

This script prepares Hadoop/MapReduce input datasets from the MySQL `movie_analytics`
database. It is designed to run either on the Hadoop cloud server or locally with
Hadoop CLI configured.
"""

from __future__ import annotations

import argparse
import csv
import subprocess
from pathlib import Path
from typing import Iterable

try:
    import pymysql
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Missing dependency: pymysql. Install it with `pip install pymysql`.") from exc


EXPORT_QUERIES: dict[str, str] = {
    "movies.tsv": """
        SELECT movie_id, COALESCE(name, ''), COALESCE(douban_score, 0), COALESCE(douban_votes, 0),
               COALESCE(release_year, 0), COALESCE(mins, 0)
        FROM dim_movie
        ORDER BY movie_id
    """,
    "ratings.tsv": """
        SELECT COALESCE(user_md5, ''), movie_id, COALESCE(rating, 0), COALESCE(rating_time_raw, '')
        FROM fact_rating
        WHERE user_md5 IS NOT NULL AND movie_id IS NOT NULL
        ORDER BY user_md5, movie_id
    """,
    "comments.tsv": """
        SELECT COALESCE(user_md5, ''), movie_id, COALESCE(votes, 0), COALESCE(rating, 0), COALESCE(comment_time_raw, '')
        FROM fact_comment
        WHERE movie_id IS NOT NULL
        ORDER BY movie_id
    """,
    "movie_tags.tsv": """
        SELECT movie_id, COALESCE(tag_name, '')
        FROM bridge_movie_tag
        ORDER BY movie_id, tag_name
    """,
    "movie_genres.tsv": """
        SELECT movie_id, COALESCE(genre_name, '')
        FROM bridge_movie_genre
        ORDER BY movie_id, genre_name
    """,
    "hot_recommend_candidates.tsv": """
        SELECT
            CAST(CONV(SUBSTRING(r.user_md5, 1, 15), 16, 10) AS UNSIGNED) AS user_id,
            r.movie_id AS rated_movie_id,
            m.movie_id AS candidate_movie_id,
            COALESCE(m.name, '') AS candidate_title,
            ROUND((COALESCE(m.douban_score, 0) * 10) + LOG10(COALESCE(m.douban_votes, 0) + 10), 4) AS candidate_hot_score
        FROM (
            SELECT DISTINCT user_md5, movie_id
            FROM fact_rating
            WHERE user_md5 IS NOT NULL AND user_md5 <> ''
            LIMIT 5000
        ) r
        JOIN (
            SELECT movie_id, name, douban_score, douban_votes
            FROM dim_movie
            ORDER BY COALESCE(douban_votes, 0) DESC, COALESCE(douban_score, 0) DESC
            LIMIT 200
        ) m ON 1 = 1
        ORDER BY user_id, candidate_hot_score DESC
    """,
    "tag_preference_candidates.tsv": """
        SELECT
            CAST(CONV(SUBSTRING(r.user_md5, 1, 15), 16, 10) AS UNSIGNED) AS user_id,
            r.movie_id AS rated_movie_id,
            COALESCE(rt.tag_name, '') AS liked_tag,
            m.movie_id AS candidate_movie_id,
            COALESCE(m.name, '') AS candidate_title,
            COALESCE(ct.tag_name, '') AS candidate_tag,
            ROUND((COALESCE(m.douban_score, 0) * 10) + LOG10(COALESCE(m.douban_votes, 0) + 10), 4) AS candidate_score
        FROM fact_rating r
        JOIN bridge_movie_tag rt ON rt.movie_id = r.movie_id
        JOIN bridge_movie_tag ct ON ct.tag_name = rt.tag_name
        JOIN dim_movie m ON m.movie_id = ct.movie_id
        WHERE r.user_md5 IS NOT NULL AND r.user_md5 <> '' AND COALESCE(r.rating, 0) >= 4
        LIMIT 500000
    """,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export MySQL analytics data to Hadoop-friendly TSV files.")
    parser.add_argument("--host", default="139.155.136.111")
    parser.add_argument("--port", type=int, default=13306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--database", default="movie_analytics")
    parser.add_argument("--output-dir", default="./movie_hdfs_export")
    parser.add_argument("--hdfs-dir", default="/movie/mysql")
    parser.add_argument("--skip-hdfs-upload", action="store_true", help="Only export local TSV files, do not run hdfs dfs -put.")
    parser.add_argument("--hdfs-bin", default="hdfs", help="Hadoop HDFS command, default: hdfs")
    return parser.parse_args()


def write_tsv(path: Path, rows: Iterable[tuple]) -> int:
    count = 0
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_MINIMAL)
        for row in rows:
            writer.writerow(["" if value is None else value for value in row])
            count += 1
    return count


def upload_to_hdfs(hdfs_bin: str, local_file: Path, hdfs_dir: str, target_name: str) -> None:
    subprocess.run([hdfs_bin, "dfs", "-mkdir", "-p", hdfs_dir], check=True)
    subprocess.run([hdfs_bin, "dfs", "-put", "-f", str(local_file), f"{hdfs_dir.rstrip('/')}/{target_name}"], check=True)


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.SSCursor,
    )

    try:
        with connection.cursor() as cursor:
            for filename, sql in EXPORT_QUERIES.items():
                local_path = output_dir / filename
                cursor.execute(sql)
                total = write_tsv(local_path, cursor)
                print(f"exported {filename}: {total} rows -> {local_path}")
                if not args.skip_hdfs_upload:
                    hdfs_name = filename.removesuffix(".tsv")
                    upload_to_hdfs(args.hdfs_bin, local_path, args.hdfs_dir, hdfs_name)
                    print(f"uploaded {filename} -> {args.hdfs_dir.rstrip('/')}/{hdfs_name}")
    finally:
        connection.close()

    print("export completed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
