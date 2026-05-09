#!/usr/bin/env python3
"""Load non-staging movie analytics tables from existing stg_* data.

Use this script when these staging tables have already been imported:

- stg_movies
- stg_persons
- stg_users
- stg_comments
- stg_ratings

The script does not read CSV files from doc/datasource and does not truncate
staging tables. It only:

1. Ensures movie analytics / group-buy schemas exist.
2. Runs doc/mysql/load_movie_analytics.sql to rebuild dim_*, fact_* and bridge_*.
3. Optionally generates demo records for gb_* tables from dim_movie and dim_user.
4. Prints record counts for verification.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Sequence

try:
    import pymysql
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Missing dependency: pymysql. Install it with `pip install pymysql`.") from exc


@dataclass(frozen=True)
class Movie:
    movie_id: int
    name: str | None
    douban_score: Decimal | None


@dataclass(frozen=True)
class User:
    user_md5: str


STAGING_TABLES = (
    "stg_movies",
    "stg_persons",
    "stg_users",
    "stg_comments",
    "stg_ratings",
)

GROUP_BUY_TABLES_IN_DELETE_ORDER = (
    "gb_group_snapshot",
    "gb_trade_order",
    "gb_group_member",
    "gb_group_order",
    "gb_activity",
)

SUMMARY_TABLES = (
    "stg_movies",
    "stg_persons",
    "stg_users",
    "stg_comments",
    "stg_ratings",
    "dim_movie",
    "dim_person",
    "dim_user",
    "dim_genre",
    "dim_language",
    "dim_region",
    "dim_tag",
    "fact_movie_person",
    "bridge_movie_genre",
    "bridge_movie_language",
    "bridge_movie_region",
    "bridge_movie_tag",
    "fact_comment",
    "fact_rating",
    "gb_activity",
    "gb_group_order",
    "gb_group_member",
    "gb_trade_order",
    "gb_group_snapshot",
)


def parse_args() -> argparse.Namespace:
    default_doc_dir = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Build analytics and group-buy tables from already-loaded stg_* tables."
    )
    parser.add_argument("--host", default="139.155.136.111")
    parser.add_argument("--port", type=int, default=13306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--database", default="movie_analytics")
    parser.add_argument("--doc-dir", default=str(default_doc_dir))
    parser.add_argument("--activity-count", type=int, default=30)
    parser.add_argument("--groups-per-activity", type=int, default=4)
    parser.add_argument("--max-members-per-group", type=int, default=4)
    parser.add_argument(
        "--skip-init",
        action="store_true",
        help="Do not run init_movie_analytics.sql / init_group_buy_tables.sql.",
    )
    parser.add_argument(
        "--skip-analytics",
        action="store_true",
        help="Do not run load_movie_analytics.sql.",
    )
    parser.add_argument(
        "--skip-group-buy",
        action="store_true",
        help="Do not generate gb_* demo data.",
    )
    parser.add_argument(
        "--allow-empty-staging",
        action="store_true",
        help="Continue even if one or more stg_* tables are empty.",
    )
    return parser.parse_args()


def split_sql_statements(sql: str) -> list[str]:
    statements: list[str] = []
    current: list[str] = []
    in_single = False
    in_double = False
    in_backtick = False
    in_line_comment = False
    in_block_comment = False
    index = 0

    while index < len(sql):
        char = sql[index]
        next_char = sql[index + 1] if index + 1 < len(sql) else ""

        if in_line_comment:
            current.append(char)
            if char in "\r\n":
                in_line_comment = False
            index += 1
            continue

        if in_block_comment:
            current.append(char)
            if char == "*" and next_char == "/":
                current.append(next_char)
                in_block_comment = False
                index += 2
            else:
                index += 1
            continue

        if not in_single and not in_double and not in_backtick:
            if char == "-" and next_char == "-":
                in_line_comment = True
                current.append(char)
                index += 1
                continue
            if char == "/" and next_char == "*":
                in_block_comment = True
                current.append(char)
                current.append(next_char)
                index += 2
                continue

        if char == "'" and not in_double and not in_backtick:
            in_single = not in_single
        elif char == '"' and not in_single and not in_backtick:
            in_double = not in_double
        elif char == "`" and not in_single and not in_double:
            in_backtick = not in_backtick

        if char == ";" and not in_single and not in_double and not in_backtick:
            statement = "".join(current).strip()
            if statement:
                statements.append(statement)
            current = []
        else:
            current.append(char)

        index += 1

    statement = "".join(current).strip()
    if statement:
        statements.append(statement)
    return statements


def run_sql_file(cursor, sql_path: Path) -> None:
    sql = sql_path.read_text(encoding="utf-8-sig")
    for statement in split_sql_statements(sql):
        cursor.execute(statement)


def require_files(paths: Sequence[Path]) -> None:
    missing = [str(path) for path in paths if not path.exists()]
    if missing:
        raise FileNotFoundError("Missing required files:\n" + "\n".join(missing))


def fetch_table_counts(cursor, tables: Sequence[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for table in tables:
        cursor.execute(f"SELECT COUNT(*) FROM `{table}`")
        counts[table] = int(cursor.fetchone()[0])
    return counts


def validate_staging_tables(cursor, allow_empty: bool) -> None:
    counts = fetch_table_counts(cursor, STAGING_TABLES)
    print("Staging table counts:")
    for table, total in counts.items():
        print(f"  {table}: {total}")

    empty_tables = [table for table, total in counts.items() if total == 0]
    if empty_tables and not allow_empty:
        raise RuntimeError(
            "These staging tables are empty: "
            + ", ".join(empty_tables)
            + ". Re-run with --allow-empty-staging if this is expected."
        )


def truncate_tables(cursor, tables: Sequence[str]) -> None:
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table in tables:
        cursor.execute(f"TRUNCATE TABLE `{table}`")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")


def fetch_movies(cursor, limit: int) -> list[Movie]:
    cursor.execute(
        """
        SELECT movie_id, name, douban_score
        FROM dim_movie
        ORDER BY COALESCE(douban_votes, 0) DESC, movie_id
        LIMIT %s
        """,
        (limit,),
    )
    return [Movie(movie_id=row[0], name=row[1], douban_score=row[2]) for row in cursor.fetchall()]


def fetch_users(cursor, limit: int) -> list[User]:
    cursor.execute("SELECT user_md5 FROM dim_user ORDER BY user_md5 LIMIT %s", (limit,))
    return [User(user_md5=row[0]) for row in cursor.fetchall()]


def price_for_movie(movie: Movie, index: int) -> tuple[Decimal, Decimal]:
    base = Decimal("19.90") + Decimal(index % 8) * Decimal("3.00")
    if movie.douban_score is not None and movie.douban_score > 0:
        base += Decimal(str(movie.douban_score)).quantize(Decimal("0.1"))
    single_price = base.quantize(Decimal("0.01"))
    group_price = (single_price * Decimal("0.75")).quantize(Decimal("0.01"))
    return group_price, single_price


def insert_group_buy_data(
    cursor,
    activity_count: int,
    groups_per_activity: int,
    max_members_per_group: int,
) -> dict[str, int]:
    required_users = activity_count * groups_per_activity * max_members_per_group
    now = datetime.now().replace(microsecond=0)
    movies = fetch_movies(cursor, activity_count)
    users = fetch_users(cursor, required_users)
    if not movies:
        raise RuntimeError("dim_movie is empty. Run analytics load before generating group-buy data.")
    if not users:
        raise RuntimeError("dim_user is empty. Run analytics load before generating group-buy data.")

    truncate_tables(cursor, GROUP_BUY_TABLES_IN_DELETE_ORDER)

    activity_rows = []
    activities = []
    for idx, movie in enumerate(movies, start=1):
        activity_no = f"GB{idx:06d}"
        target_group_size = 2 + (idx % 3)
        group_price, single_price = price_for_movie(movie, idx)
        movie_name = movie.name or f"电影{movie.movie_id}"
        start_time = now - timedelta(days=idx % 10)
        end_time = now + timedelta(days=20 + idx % 15)
        activity_rows.append(
            (
                activity_no,
                f"{movie_name} 拼团观影活动"[:128],
                movie.movie_id,
                1,
                group_price,
                single_price,
                target_group_size,
                80 + idx * 5,
                0,
                start_time,
                end_time,
                f"基于电影 `{movie_name}` 自动生成的拼团演示活动"[:500],
                now,
                now,
            )
        )
        activities.append((activity_no, movie.movie_id, target_group_size, group_price))

    cursor.executemany(
        """
        INSERT INTO gb_activity (
            activity_no, activity_name, movie_id, activity_status, group_price,
            single_price, target_group_size, stock_total, stock_locked,
            start_time, end_time, description, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        activity_rows,
    )

    group_rows = []
    member_rows = []
    trade_rows = []
    snapshots: dict[tuple[str, int], dict[str, Any]] = {}
    user_index = 0

    for activity_index, (activity_no, movie_id, target_size, group_price) in enumerate(activities, start=1):
        for group_index in range(1, groups_per_activity + 1):
            group_no = f"GO{activity_index:04d}{group_index:04d}"
            member_count = min(target_size if group_index % 4 != 0 else max(1, target_size - 1), max_members_per_group)
            group_status = 2 if member_count >= target_size else 1
            create_time = now - timedelta(days=(activity_index + group_index) % 14, hours=group_index)
            expire_time = create_time + timedelta(hours=24)
            success_time = create_time + timedelta(hours=2) if group_status == 2 else None
            leader = users[user_index % len(users)].user_md5
            user_index += 1

            group_rows.append(
                (
                    group_no,
                    activity_no,
                    movie_id,
                    leader,
                    group_status,
                    member_count,
                    target_size,
                    expire_time,
                    success_time,
                    None,
                    create_time,
                    now,
                )
            )

            snap = snapshots.setdefault(
                (activity_no, movie_id),
                {"launched": 0, "success": 0, "failed": 0, "participants": 0, "orders": 0, "amount": Decimal("0.00")},
            )
            snap["launched"] += 1
            snap["success"] += 1 if group_status == 2 else 0
            snap["failed"] += 1 if group_status != 2 and expire_time < now else 0
            snap["participants"] += member_count
            snap["orders"] += member_count
            snap["amount"] += group_price * member_count

            for member_index in range(member_count):
                if member_index == 0:
                    user_md5 = leader
                    join_role = 1
                else:
                    user_md5 = users[user_index % len(users)].user_md5
                    user_index += 1
                    join_role = 2
                pay_time = create_time + timedelta(minutes=5 + member_index * 8)
                member_rows.append((group_no, user_md5, join_role, 1, group_price, pay_time, None, create_time, now))
                trade_rows.append(
                    (
                        f"TO{activity_index:04d}{group_index:04d}{member_index + 1:03d}",
                        group_no,
                        activity_no,
                        user_md5,
                        movie_id,
                        4 if group_status == 2 else 1,
                        group_price,
                        Decimal("0.00"),
                        group_price,
                        pay_time,
                        success_time,
                        None,
                        create_time,
                        now,
                    )
                )

    cursor.executemany(
        """
        INSERT INTO gb_group_order (
            group_order_no, activity_no, movie_id, leader_user_md5, group_status,
            current_member_count, target_group_size, expire_time, success_time,
            close_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        group_rows,
    )
    cursor.executemany(
        """
        INSERT INTO gb_group_member (
            group_order_no, user_md5, join_role, join_status, pay_amount,
            pay_time, refund_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        member_rows,
    )
    cursor.executemany(
        """
        INSERT INTO gb_trade_order (
            trade_order_no, group_order_no, activity_no, user_md5, movie_id,
            order_status, order_amount, discount_amount, payable_amount,
            pay_time, finish_time, close_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        trade_rows,
    )

    snapshot_rows = []
    for (activity_no, movie_id), snap in snapshots.items():
        snapshot_rows.append(
            (
                date.today(),
                activity_no,
                movie_id,
                snap["launched"],
                snap["success"],
                snap["failed"],
                snap["participants"],
                snap["orders"],
                snap["amount"],
                Decimal("0.00"),
                now,
                now,
            )
        )
    cursor.executemany(
        """
        INSERT INTO gb_group_snapshot (
            stat_date, activity_no, movie_id, launched_group_count,
            success_group_count, failed_group_count, participant_count,
            paid_order_count, paid_amount, refund_amount, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        snapshot_rows,
    )

    return {
        "gb_activity": len(activity_rows),
        "gb_group_order": len(group_rows),
        "gb_group_member": len(member_rows),
        "gb_trade_order": len(trade_rows),
        "gb_group_snapshot": len(snapshot_rows),
    }


def print_summary(cursor) -> None:
    print("\nRecord counts:")
    for table in SUMMARY_TABLES:
        try:
            cursor.execute(f"SELECT COUNT(*) FROM `{table}`")
            print(f"{table}: {cursor.fetchone()[0]}")
        except pymysql.MySQLError as exc:
            message = exc.args[1] if len(exc.args) > 1 else str(exc)
            print(f"{table}: unavailable ({message})")


def main() -> int:
    args = parse_args()
    doc_dir = Path(args.doc_dir)
    mysql_dir = doc_dir / "mysql"
    require_files(
        [
            mysql_dir / "init_movie_analytics.sql",
            mysql_dir / "load_movie_analytics.sql",
            mysql_dir / "init_group_buy_tables.sql",
        ]
    )

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

            if not args.skip_init:
                print("[1/5] Ensuring movie analytics schema exists...")
                run_sql_file(cursor, mysql_dir / "init_movie_analytics.sql")
                connection.commit()
                cursor.execute(f"USE `{args.database}`")

                print("[2/5] Ensuring group-buy schema exists...")
                run_sql_file(cursor, mysql_dir / "init_group_buy_tables.sql")
                connection.commit()
                cursor.execute(f"USE `{args.database}`")
            else:
                print("[1/5] Skipping schema initialization.")
                print("[2/5] Skipping group-buy schema initialization.")

            print("[3/5] Checking existing staging tables...")
            validate_staging_tables(cursor, args.allow_empty_staging)

            if not args.skip_analytics:
                print("[4/5] Loading dim_*, fact_* and bridge_* from stg_*...")
                run_sql_file(cursor, mysql_dir / "load_movie_analytics.sql")
                connection.commit()
            else:
                print("[4/5] Skipping analytics load.")

            if not args.skip_group_buy:
                print("[5/5] Generating group-buy demo data...")
                counts = insert_group_buy_data(
                    cursor,
                    args.activity_count,
                    args.groups_per_activity,
                    args.max_members_per_group,
                )
                connection.commit()
                for table, total in counts.items():
                    print(f"  {table}: {total} rows")
            else:
                print("[5/5] Skipping group-buy data generation.")

            print_summary(cursor)

        print("\nLoad completed successfully.")
        return 0
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
