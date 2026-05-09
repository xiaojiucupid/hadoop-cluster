#!/usr/bin/env python3
"""Import Hadoop recommendation output files into MySQL rec_* tables.

Expected TSV formats:
- user recommendation tables: user_md5, movie_id, movie_name, recommend_score, reason, rank_no
- hot/quality tables: movie_id, movie_name, score, rank_no, reason

The script can pull files from HDFS first, or import existing local files.
"""

from __future__ import annotations

import argparse
import csv
import subprocess
from pathlib import Path
from typing import Any, Iterable

try:
    import pymysql
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Missing dependency: pymysql. Install it with `pip install pymysql`.") from exc


USER_REC_TABLES = {
    "tag": "rec_tag_preference_topn",
    "usercf": "rec_user_cf_topn",
    "itemcf": "rec_item_cf_topn",
    "hybrid": "rec_user_movie_topn",
}
GLOBAL_REC_TABLES = {
    "hot": ("rec_hot_movie_topn", "hot_score"),
    "quality": ("rec_quality_movie_topn", "quality_score"),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import Hadoop recommendation output into MySQL.")
    parser.add_argument("--host", default="139.155.136.111")
    parser.add_argument("--port", type=int, default=13306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--database", default="movie_analytics")
    parser.add_argument("--algorithm", choices=[*USER_REC_TABLES.keys(), *GLOBAL_REC_TABLES.keys()], default="hybrid")
    parser.add_argument("--local-file", help="Local Hadoop part file. If omitted, --hdfs-file will be downloaded first.")
    parser.add_argument("--hdfs-file", help="HDFS output file, for example /movie/result/hybrid/part-r-00000")
    parser.add_argument("--work-dir", default="./movie_hdfs_result")
    parser.add_argument("--hdfs-bin", default="hdfs")
    parser.add_argument("--batch-size", type=int, default=1000)
    parser.add_argument("--truncate", action="store_true", help="Truncate target table before import.")
    return parser.parse_args()


def fetch_from_hdfs(args: argparse.Namespace) -> Path:
    if not args.hdfs_file:
        raise ValueError("Either --local-file or --hdfs-file is required.")
    work_dir = Path(args.work_dir).resolve()
    work_dir.mkdir(parents=True, exist_ok=True)
    local_path = work_dir / f"{args.algorithm}_part.tsv"
    subprocess.run([args.hdfs_bin, "dfs", "-get", "-f", args.hdfs_file, str(local_path)], check=True)
    return local_path


def clean(value: str | None) -> str | None:
    if value is None:
        return None
    value = value.strip()
    return value if value else None


def as_int(value: str | None, default: int = 0) -> int:
    try:
        return int(float(clean(value) or default))
    except ValueError:
        return default


def as_float(value: str | None, default: float = 0.0) -> float:
    try:
        return float(clean(value) or default)
    except ValueError:
        return default


def iter_user_rec_rows(path: Path, algorithm: str) -> Iterable[tuple[Any, ...]]:
    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.reader(file, delimiter="\t")
        for index, row in enumerate(reader, start=1):
            if not row or row[0].startswith("#"):
                continue
            user_md5 = clean(row[0] if len(row) > 0 else None)
            movie_id = as_int(row[1] if len(row) > 1 else None, -1)
            if not user_md5 or movie_id < 0:
                continue
            yield (
                user_md5[:32],
                movie_id,
                clean(row[2] if len(row) > 2 else None),
                as_float(row[3] if len(row) > 3 else None),
                clean(row[4] if len(row) > 4 else None),
                algorithm.upper(),
                as_int(row[5] if len(row) > 5 else None, index),
            )


def iter_global_rec_rows(path: Path) -> Iterable[tuple[Any, ...]]:
    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.reader(file, delimiter="\t")
        for index, row in enumerate(reader, start=1):
            if not row or row[0].startswith("#"):
                continue
            movie_id = as_int(row[0] if len(row) > 0 else None, -1)
            if movie_id < 0:
                continue
            yield (
                movie_id,
                clean(row[1] if len(row) > 1 else None),
                as_float(row[2] if len(row) > 2 else None),
                as_int(row[3] if len(row) > 3 else None, index),
                clean(row[4] if len(row) > 4 else None),
            )


def batched(rows: Iterable[tuple[Any, ...]], size: int) -> Iterable[list[tuple[Any, ...]]]:
    batch: list[tuple[Any, ...]] = []
    for row in rows:
        batch.append(row)
        if len(batch) >= size:
            yield batch
            batch = []
    if batch:
        yield batch


def import_user_rec(cursor, table: str, rows: Iterable[tuple[Any, ...]], batch_size: int) -> int:
    sql = f"""
        INSERT INTO {table} (user_md5, movie_id, movie_name, recommend_score, reason, algorithm_type, rank_no)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          movie_name = VALUES(movie_name),
          recommend_score = VALUES(recommend_score),
          reason = VALUES(reason),
          rank_no = VALUES(rank_no),
          calculated_at = CURRENT_TIMESTAMP
    """
    total = 0
    for batch in batched(rows, batch_size):
        cursor.executemany(sql, batch)
        total += len(batch)
    return total


def import_global_rec(cursor, table: str, score_column: str, rows: Iterable[tuple[Any, ...]], batch_size: int) -> int:
    sql = f"""
        INSERT INTO {table} (movie_id, movie_name, {score_column}, rank_no, reason)
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          movie_id = VALUES(movie_id),
          movie_name = VALUES(movie_name),
          {score_column} = VALUES({score_column}),
          reason = VALUES(reason),
          calculated_at = CURRENT_TIMESTAMP
    """
    total = 0
    for batch in batched(rows, batch_size):
        cursor.executemany(sql, batch)
        total += len(batch)
    return total


def main() -> int:
    args = parse_args()
    local_file = Path(args.local_file).resolve() if args.local_file else fetch_from_hdfs(args)
    if not local_file.exists():
        raise FileNotFoundError(local_file)

    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )

    try:
        with connection.cursor() as cursor:
            if args.algorithm in USER_REC_TABLES:
                table = USER_REC_TABLES[args.algorithm]
                if args.truncate:
                    cursor.execute(f"TRUNCATE TABLE {table}")
                total = import_user_rec(cursor, table, iter_user_rec_rows(local_file, args.algorithm), args.batch_size)
            else:
                table, score_column = GLOBAL_REC_TABLES[args.algorithm]
                if args.truncate:
                    cursor.execute(f"TRUNCATE TABLE {table}")
                total = import_global_rec(cursor, table, score_column, iter_global_rec_rows(local_file), args.batch_size)
            connection.commit()
            print(f"imported {total} rows into {table} from {local_file}")
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
