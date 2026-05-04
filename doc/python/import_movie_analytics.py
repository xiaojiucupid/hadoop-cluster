#!/usr/bin/env python3
"""Import Movie Analytics CSV files into MySQL staging tables.

This importer is tailored to the files under doc/datasource and the staging
schema in doc/mysql/init_movie_analytics.sql.

Observed datasource facts:
- movies.csv, person.csv, users.csv and ratings.csv are UTF-8/UTF-8-BOM CSV files.
- comment.csv is GB18030 encoded CSV.
- All five files are comma-delimited with a header row.
- CSV headers are uppercase; MySQL staging columns are lowercase.
"""

from __future__ import annotations

import argparse
import csv
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

try:
    import pymysql
except ImportError as exc:  # pragma: no cover - runtime dependency guard
    raise SystemExit("Missing dependency: pymysql. Install it with `pip install pymysql`.") from exc


@dataclass(frozen=True)
class ColumnSpec:
    name: str
    kind: str = "str"
    max_length: int | None = None
    required: bool = False


@dataclass(frozen=True)
class TableSpec:
    table: str
    filename: str
    encoding: str
    columns: tuple[ColumnSpec, ...]
    encoding_errors: str = "strict"

    @property
    def column_names(self) -> tuple[str, ...]:
        return tuple(column.name for column in self.columns)


TABLES: tuple[TableSpec, ...] = (
    TableSpec(
        table="stg_movies",
        filename="movies.csv",
        encoding="utf-8-sig",
        columns=(
            ColumnSpec("movie_id", "int", required=True),
            ColumnSpec("name", max_length=255),
            ColumnSpec("alias", max_length=500),
            ColumnSpec("actors"),
            ColumnSpec("cover", max_length=1000),
            ColumnSpec("directors"),
            ColumnSpec("douban_score", "decimal"),
            ColumnSpec("douban_votes", "int"),
            ColumnSpec("genres", max_length=500),
            ColumnSpec("imdb_id", max_length=64),
            ColumnSpec("languages", max_length=500),
            ColumnSpec("mins", "int"),
            ColumnSpec("official_site", max_length=1000),
            ColumnSpec("regions", max_length=500),
            ColumnSpec("release_date", max_length=64),
            ColumnSpec("slug", max_length=128),
            ColumnSpec("storyline"),
            ColumnSpec("tags", max_length=1000),
            ColumnSpec("year", "int"),
            ColumnSpec("actor_ids"),
            ColumnSpec("director_ids"),
        ),
    ),
    TableSpec(
        table="stg_persons",
        filename="person.csv",
        encoding="utf-8-sig",
        columns=(
            ColumnSpec("person_id", "int", required=True),
            ColumnSpec("name", max_length=255),
            ColumnSpec("sex", max_length=16),
            ColumnSpec("name_en", max_length=255),
            ColumnSpec("name_zh", max_length=255),
            ColumnSpec("birth", max_length=64),
            ColumnSpec("birthplace", max_length=255),
            ColumnSpec("constellatory", max_length=64),
            ColumnSpec("profession", max_length=255),
            ColumnSpec("biography"),
        ),
    ),
    TableSpec(
        table="stg_users",
        filename="users.csv",
        encoding="utf-8-sig",
        columns=(
            ColumnSpec("user_md5", max_length=32, required=True),
            ColumnSpec("user_nickname", max_length=255),
        ),
    ),
    TableSpec(
        table="stg_comments",
        filename="comment.csv",
        encoding="gb18030",
        columns=(
            ColumnSpec("comment_id", "int", required=True),
            ColumnSpec("user_md5", max_length=32),
            ColumnSpec("movie_id", "int"),
            ColumnSpec("content"),
            ColumnSpec("votes", "int"),
            ColumnSpec("comment_time", max_length=64),
            ColumnSpec("rating", "int"),
        ),
        encoding_errors="replace",
    ),
    TableSpec(
        table="stg_ratings",
        filename="ratings.csv",
        encoding="utf-8-sig",
        columns=(
            ColumnSpec("rating_id", "int", required=True),
            ColumnSpec("user_md5", max_length=32),
            ColumnSpec("movie_id", "int"),
            ColumnSpec("rating", "int"),
            ColumnSpec("rating_time", max_length=64),
        ),
    ),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load Movie Analytics CSV files into MySQL staging tables.")
    parser.add_argument("--host", default="139.155.136.111")
    parser.add_argument("--port", type=int, default=13306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--database", default="movie_analytics")
    parser.add_argument(
        "--csv-dir",
        default=str(Path(__file__).resolve().parents[1] / "datasource"),
        help="Directory containing movies.csv, person.csv, users.csv, comment.csv and ratings.csv.",
    )
    parser.add_argument("--batch-size", type=int, default=2000)
    parser.add_argument("--dry-run", action="store_true", help="Read and validate CSV files without writing to MySQL.")
    return parser.parse_args()


def clean_text(value: str | None) -> str | None:
    if value is None:
        return None
    value = value.strip()
    return value if value else None


def coerce_value(value: str | None, column: ColumnSpec) -> Any:
    value = clean_text(value)
    if value is None:
        return None

    if column.kind == "int":
        try:
            return int(float(value))
        except ValueError:
            return None

    if column.kind == "decimal":
        try:
            return float(value)
        except ValueError:
            return None

    if column.max_length is not None and len(value) > column.max_length:
        return value[: column.max_length]

    return value


def normalize_headers(headers: Sequence[str | None]) -> dict[str, str]:
    return {header.strip().lower(): header for header in headers if header is not None and header.strip()}


def repair_row(spec: TableSpec, row: list[str]) -> list[str]:
    expected = len(spec.columns)
    if len(row) == expected:
        return row

    if len(row) < expected:
        return row + [""] * (expected - len(row))

    if spec.filename == "comment.csv":
        # comment.csv contains unquoted commas in CONTENT. Keep the stable prefix
        # and suffix, then merge the middle cells back into CONTENT.
        return row[:3] + [",".join(row[3:-3])] + row[-3:]

    if spec.filename == "movies.csv":
        # movies.csv occasionally has malformed STORYLINE text that expands into
        # multiple CSV cells. Columns up to SLUG and the last 4 analytical fields
        # are stable, so merge the middle cells back into STORYLINE.
        return row[:16] + [",".join(row[16:-4])] + row[-4:]

    return row[:expected]


def iter_rows(spec: TableSpec, csv_path: Path) -> Iterable[tuple[Any, ...]]:
    with csv_path.open("r", encoding=spec.encoding, errors=spec.encoding_errors, newline="") as f:
        reader = csv.reader(f, delimiter=",", quotechar='"')
        try:
            headers = next(reader)
        except StopIteration:
            return

        header_map = normalize_headers(headers)
        missing = [column.name for column in spec.columns if column.name.lower() not in header_map]
        if missing:
            found = ", ".join(headers)
            raise ValueError(f"{spec.filename} missing columns: {', '.join(missing)}; found headers: {found}")

        for row in reader:
            row = repair_row(spec, row)
            values = tuple(coerce_value(row[index] if index < len(row) else None, column) for index, column in enumerate(spec.columns))
            if any(column.required and values[index] is None for index, column in enumerate(spec.columns)):
                continue
            yield values


def truncate_tables(cursor, tables: Sequence[str]) -> None:
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table in tables:
        cursor.execute(f"TRUNCATE TABLE `{table}`")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")


def insert_batch(cursor, spec: TableSpec, rows: Sequence[tuple[Any, ...]]) -> None:
    columns_sql = ", ".join(f"`{name}`" for name in spec.column_names)
    placeholders = ", ".join(["%s"] * len(spec.columns))
    sql = f"INSERT INTO `{spec.table}` ({columns_sql}) VALUES ({placeholders})"
    cursor.executemany(sql, rows)


def dry_run(spec: TableSpec, csv_path: Path) -> int:
    total = 0
    for _ in iter_rows(spec, csv_path):
        total += 1
    return total


def load_table(cursor, spec: TableSpec, csv_path: Path, batch_size: int) -> int:
    total = 0
    batch: list[tuple[Any, ...]] = []
    for row in iter_rows(spec, csv_path):
        batch.append(row)
        if len(batch) >= batch_size:
            insert_batch(cursor, spec, batch)
            total += len(batch)
            batch.clear()

    if batch:
        insert_batch(cursor, spec, batch)
        total += len(batch)

    return total


def validate_files(csv_dir: Path) -> list[str]:
    return [spec.filename for spec in TABLES if not (csv_dir / spec.filename).is_file()]


def main() -> int:
    args = parse_args()
    csv_dir = Path(args.csv_dir)

    missing = validate_files(csv_dir)
    if missing:
        print(f"Missing CSV files in {csv_dir}: {', '.join(missing)}", file=sys.stderr)
        return 1

    if args.dry_run:
        print(f"Reading CSV files from: {csv_dir}")
        for spec in TABLES:
            count = dry_run(spec, csv_dir / spec.filename)
            print(f"{spec.table} <- {spec.filename}: {count} rows")
        return 0

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
            cursor.execute("SET NAMES utf8mb4")
            truncate_tables(cursor, [spec.table for spec in TABLES])

            totals: list[tuple[str, int]] = []
            for spec in TABLES:
                inserted = load_table(cursor, spec, csv_dir / spec.filename, args.batch_size)
                connection.commit()
                totals.append((spec.table, inserted))
                print(f"{spec.table}: {inserted} rows")

        print("Load completed successfully.")
        print("Next step: run doc/mysql/load_movie_analytics.sql to populate analytics tables.")
        return 0
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
