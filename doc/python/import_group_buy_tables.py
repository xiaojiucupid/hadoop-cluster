#!/usr/bin/env python3
"""Generate and import demo data for group-buy tables.

The group-buy schema in `doc/mysql/init_group_buy_tables.sql` does not have
corresponding CSV files under `doc/datasource`. This script creates deterministic
sample records from already-loaded `dim_movie` and `dim_user` rows, then inserts
those records into:

- gb_activity
- gb_group_order
- gb_group_member
- gb_trade_order
- gb_group_snapshot

Run `doc/mysql/init_group_buy_tables.sql` before this script, and make sure
`dim_movie` / `dim_user` have been populated first.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal

try:
    import pymysql
except ImportError as exc:  # pragma: no cover - runtime dependency guard
    raise SystemExit(
        "Missing dependency: pymysql. Install it with `pip install pymysql`."
    ) from exc


@dataclass(frozen=True)
class Movie:
    movie_id: int
    name: str | None
    douban_score: Decimal | None


@dataclass(frozen=True)
class User:
    user_md5: str


TABLES_IN_DELETE_ORDER = (
    "gb_group_snapshot",
    "gb_trade_order",
    "gb_group_member",
    "gb_group_order",
    "gb_activity",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate demo records for group-buy tables."
    )
    parser.add_argument("--host", default="139.155.136.111")
    parser.add_argument("--port", type=int, default=13306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--database", default="movie_analytics")
    parser.add_argument("--charset", default="utf8mb4")
    parser.add_argument("--activity-count", type=int, default=30)
    parser.add_argument("--groups-per-activity", type=int, default=4)
    parser.add_argument("--max-members-per-group", type=int, default=4)
    parser.add_argument(
        "--keep-existing",
        action="store_true",
        help="Append data instead of clearing existing group-buy rows first.",
    )
    return parser.parse_args()


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
    cursor.execute(
        """
        SELECT user_md5
        FROM dim_user
        ORDER BY user_md5
        LIMIT %s
        """,
        (limit,),
    )
    return [User(user_md5=row[0]) for row in cursor.fetchall()]


def clear_group_buy_tables(cursor) -> None:
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table in TABLES_IN_DELETE_ORDER:
        cursor.execute(f"TRUNCATE TABLE `{table}`")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")


def price_for_movie(movie: Movie, index: int) -> tuple[Decimal, Decimal]:
    base = Decimal("19.90") + Decimal(index % 8) * Decimal("3.00")
    if movie.douban_score is not None and movie.douban_score > 0:
        base += Decimal(str(movie.douban_score)).quantize(Decimal("0.1"))
    single_price = base.quantize(Decimal("0.01"))
    group_price = (single_price * Decimal("0.75")).quantize(Decimal("0.01"))
    return group_price, single_price


def insert_activities(cursor, movies: list[Movie], now: datetime) -> list[dict]:
    activities = []
    sql = """
        INSERT INTO gb_activity (
            activity_no, activity_name, movie_id, activity_status, group_price,
            single_price, target_group_size, stock_total, stock_locked,
            start_time, end_time, description, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """

    rows = []
    for idx, movie in enumerate(movies, start=1):
        activity_no = f"GB{idx:06d}"
        target_group_size = 2 + (idx % 3)
        stock_total = 80 + idx * 5
        stock_locked = 0
        start_time = now - timedelta(days=idx % 10)
        end_time = now + timedelta(days=20 + idx % 15)
        status = 1 if start_time <= now <= end_time else 0
        group_price, single_price = price_for_movie(movie, idx)
        movie_name = movie.name or f"电影{movie.movie_id}"

        rows.append(
            (
                activity_no,
                f"{movie_name} 拼团观影活动"[:128],
                movie.movie_id,
                status,
                group_price,
                single_price,
                target_group_size,
                stock_total,
                stock_locked,
                start_time,
                end_time,
                f"基于电影 `{movie_name}` 自动生成的拼团演示活动"[:500],
                now,
                now,
            )
        )
        activities.append(
            {
                "activity_no": activity_no,
                "movie_id": movie.movie_id,
                "target_group_size": target_group_size,
                "group_price": group_price,
            }
        )

    cursor.executemany(sql, rows)
    return activities


def insert_groups_members_orders(
    cursor,
    activities: list[dict],
    users: list[User],
    groups_per_activity: int,
    max_members_per_group: int,
    now: datetime,
) -> tuple[list[dict], int, int]:
    group_sql = """
        INSERT INTO gb_group_order (
            group_order_no, activity_no, movie_id, leader_user_md5, group_status,
            current_member_count, target_group_size, expire_time, success_time,
            close_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    member_sql = """
        INSERT INTO gb_group_member (
            group_order_no, user_md5, join_role, join_status, pay_amount,
            pay_time, refund_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    trade_sql = """
        INSERT INTO gb_trade_order (
            trade_order_no, group_order_no, activity_no, user_md5, movie_id,
            order_status, order_amount, discount_amount, payable_amount,
            pay_time, finish_time, close_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """

    group_rows = []
    member_rows = []
    trade_rows = []
    group_records = []
    user_index = 0

    for activity_index, activity in enumerate(activities, start=1):
        for group_index in range(1, groups_per_activity + 1):
            group_no = f"GO{activity_index:04d}{group_index:04d}"
            target_size = activity["target_group_size"]
            member_count = min(
                target_size if group_index % 4 != 0 else max(1, target_size - 1),
                max_members_per_group,
            )
            group_status = 2 if member_count >= target_size else 1
            create_time = now - timedelta(days=(activity_index + group_index) % 14, hours=group_index)
            expire_time = create_time + timedelta(hours=24)
            success_time = create_time + timedelta(hours=2) if group_status == 2 else None
            close_time = None

            leader = users[user_index % len(users)].user_md5
            user_index += 1

            group_rows.append(
                (
                    group_no,
                    activity["activity_no"],
                    activity["movie_id"],
                    leader,
                    group_status,
                    member_count,
                    target_size,
                    expire_time,
                    success_time,
                    close_time,
                    create_time,
                    now,
                )
            )
            group_records.append(
                {
                    "group_order_no": group_no,
                    "activity_no": activity["activity_no"],
                    "movie_id": activity["movie_id"],
                    "group_status": group_status,
                    "member_count": member_count,
                    "pay_amount": activity["group_price"],
                }
            )

            for member_index in range(member_count):
                if member_index == 0:
                    user_md5 = leader
                    join_role = 1
                else:
                    user_md5 = users[user_index % len(users)].user_md5
                    user_index += 1
                    join_role = 2

                pay_time = create_time + timedelta(minutes=5 + member_index * 8)
                member_rows.append(
                    (
                        group_no,
                        user_md5,
                        join_role,
                        1,
                        activity["group_price"],
                        pay_time,
                        None,
                        create_time,
                        now,
                    )
                )
                trade_rows.append(
                    (
                        f"TO{activity_index:04d}{group_index:04d}{member_index + 1:03d}",
                        group_no,
                        activity["activity_no"],
                        user_md5,
                        activity["movie_id"],
                        4 if group_status == 2 else 1,
                        activity["group_price"],
                        Decimal("0.00"),
                        activity["group_price"],
                        pay_time,
                        success_time,
                        None,
                        create_time,
                        now,
                    )
                )

    cursor.executemany(group_sql, group_rows)
    cursor.executemany(member_sql, member_rows)
    cursor.executemany(trade_sql, trade_rows)
    return group_records, len(member_rows), len(trade_rows)


def insert_snapshots(cursor, group_records: list[dict], now: datetime) -> int:
    sql = """
        INSERT INTO gb_group_snapshot (
            stat_date, activity_no, movie_id, launched_group_count,
            success_group_count, failed_group_count, participant_count,
            paid_order_count, paid_amount, refund_amount, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    grouped: dict[tuple[str, int], dict] = {}
    for record in group_records:
        key = (record["activity_no"], record["movie_id"])
        item = grouped.setdefault(
            key,
            {
                "launched": 0,
                "success": 0,
                "failed": 0,
                "participants": 0,
                "paid_orders": 0,
                "paid_amount": Decimal("0.00"),
            },
        )
        item["launched"] += 1
        item["success"] += 1 if record["group_status"] == 2 else 0
        item["failed"] += 1 if record["group_status"] == 3 else 0
        item["participants"] += record["member_count"]
        item["paid_orders"] += record["member_count"]
        item["paid_amount"] += record["pay_amount"] * record["member_count"]

    rows = []
    stat_date = date.today()
    for (activity_no, movie_id), item in grouped.items():
        rows.append(
            (
                stat_date,
                activity_no,
                movie_id,
                item["launched"],
                item["success"],
                item["failed"],
                item["participants"],
                item["paid_orders"],
                item["paid_amount"],
                Decimal("0.00"),
                now,
                now,
            )
        )

    cursor.executemany(sql, rows)
    return len(rows)


def main() -> int:
    args = parse_args()
    required_users = args.activity_count * args.groups_per_activity * args.max_members_per_group
    now = datetime.now().replace(microsecond=0)

    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset=args.charset,
        autocommit=False,
    )

    try:
        with connection.cursor() as cursor:
            cursor.execute(f"SET NAMES {args.charset}")

            movies = fetch_movies(cursor, args.activity_count)
            users = fetch_users(cursor, required_users)
            if not movies:
                raise RuntimeError("No rows found in dim_movie. Import movie analytics data first.")
            if not users:
                raise RuntimeError("No rows found in dim_user. Import movie analytics data first.")

            if not args.keep_existing:
                clear_group_buy_tables(cursor)

            activities = insert_activities(cursor, movies, now)
            groups, member_count, trade_count = insert_groups_members_orders(
                cursor,
                activities,
                users,
                args.groups_per_activity,
                args.max_members_per_group,
                now,
            )
            snapshot_count = insert_snapshots(cursor, groups, now)
            connection.commit()

        print("Group-buy demo data import completed successfully.")
        print(f"gb_activity: {len(activities)} rows")
        print(f"gb_group_order: {len(groups)} rows")
        print(f"gb_group_member: {member_count} rows")
        print(f"gb_trade_order: {trade_count} rows")
        print(f"gb_group_snapshot: {snapshot_count} rows")
        return 0
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
