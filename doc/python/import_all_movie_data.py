#!/usr/bin/env python3
# 指定解释器为 python3，允许在支持 shebang 的系统上直接执行本脚本

"""One-click importer for doc/mysql schemas and doc/datasource datasets.

Pipeline:
1. Run doc/mysql/init_movie_analytics.sql.
2. Import doc/datasource CSV files into stg_* tables.
3. Run doc/mysql/load_movie_analytics.sql.
4. Run doc/mysql/init_group_buy_tables.sql.
5. Generate group-buy demo data into gb_* tables.
6. Print record counts for key tables.
"""
# 脚本文档字符串，描述主要流程

from __future__ import annotations  # 允许在类型注解中使用尚未定义的类型（前向引用）

import argparse                     # 解析命令行参数
import csv                          # 读取 CSV 文件
from dataclasses import dataclass   # 定义数据类（只用于存储规格和少量模型）
from datetime import date, datetime, timedelta  # 处理日期时间，生成拼团示例数据
from decimal import Decimal         # 高精度小数，处理金额
from pathlib import Path            # 面向对象的文件系统路径
from typing import Any, Iterable, Sequence  # 类型标注支持

try:
    import pymysql                  # MySQL 数据库驱动（纯 Python 实现）
except ImportError as exc:          # pragma: no cover
    # 如果未安装依赖，直接退出并给出提示
    raise SystemExit("Missing dependency: pymysql. Install it with `pip install pymysql`.") from exc


# ---------- 数据类定义 ----------

@dataclass(frozen=True)
class ColumnSpec:
    """定义 CSV 列与数据库表的映射规格，不可变对象"""
    name: str               # 列名（与数据库表字段一致）
    kind: str = "str"       # 数据类型简化表示：int / decimal / str（字符串）
    max_length: int | None = None  # 字符串最大长度（超长会被截断）
    required: bool = False  # 是否必填，若该列缺失则跳过整行


@dataclass(frozen=True)
class StagingSpec:
    """描述一个 staging 导入表的完整规格"""
    table: str              # 目标 staging 表名
    filename: str           # CSV 文件名（不含路径）
    encoding: str           # 文件编码
    columns: tuple[ColumnSpec, ...]  # 列规格元组
    encoding_errors: str = "strict"  # 遇到编码错误时的处理策略

    @property
    def column_names(self) -> tuple[str, ...]:
        """提取所有列名，便于生成 SQL"""
        return tuple(column.name for column in self.columns)


@dataclass(frozen=True)
class Movie:
    """用于拼团活动的电影基础信息"""
    movie_id: int
    name: str | None
    douban_score: Decimal | None


@dataclass(frozen=True)
class User:
    """用于拼团活动的用户标识"""
    user_md5: str


# ---------- 常量定义 ----------

# 五个 staging 表的规格（按 CSV 文件顺序）
STAGING_TABLES: tuple[StagingSpec, ...] = (
    StagingSpec(
        table="stg_movies",
        filename="movies.csv",
        encoding="utf-8-sig",   # 带 BOM 的 UTF-8
        columns=(
            ColumnSpec("movie_id", "int", required=True),
            ColumnSpec("name", max_length=255),
            ColumnSpec("alias", max_length=500),
            ColumnSpec("actors"),                        # 无限制的文本
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
    StagingSpec(
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
    StagingSpec(
        table="stg_users",
        filename="users.csv",
        encoding="utf-8-sig",
        columns=(
            ColumnSpec("user_md5", max_length=32, required=True),
            ColumnSpec("user_nickname", max_length=255),
        ),
    ),
    StagingSpec(
        table="stg_comments",
        filename="comment.csv",
        encoding="gb18030",             # 评论文件使用 GB18030 编码
        columns=(
            ColumnSpec("comment_id", "int", required=True),
            ColumnSpec("user_md5", max_length=32),
            ColumnSpec("movie_id", "int"),
            ColumnSpec("content"),      # 评论文本内容
            ColumnSpec("votes", "int"),
            ColumnSpec("comment_time", max_length=64),
            ColumnSpec("rating", "int"),
        ),
        encoding_errors="replace",      # 遇到无法解码的字符替换为 �
    ),
    StagingSpec(
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

# 删除拼团相关表时的顺序（DEFAULT 删除顺序，先删依赖表）
GROUP_BUY_TABLES_IN_DELETE_ORDER = (
    "gb_group_snapshot",
    "gb_trade_order",
    "gb_group_member",
    "gb_group_order",
    "gb_activity",
)

# 最后需要输出行数统计的所有表
SUMMARY_TABLES = (
    "stg_movies",
    "stg_persons",
    "stg_users",
    "stg_comments",
    "stg_ratings",
    "dim_movie",
    "dim_person",
    "dim_user",
    "fact_comment",
    "fact_rating",
    "gb_activity",
    "gb_group_order",
    "gb_group_member",
    "gb_trade_order",
    "gb_group_snapshot",
)


# ---------- 命令行解析 ----------

def parse_args() -> argparse.Namespace:
    """解析命令行参数，返回命名空间对象"""
    # 默认 doc-dir 为脚本所在目录的父目录
    default_doc_dir = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Import all movie analytics and group-buy data.")
    parser.add_argument("--host", default="139.155.136.111")        # MySQL 主机
    parser.add_argument("--port", type=int, default=13306)          # MySQL 端口
    parser.add_argument("--user", default="root")                   # 用户名
    parser.add_argument("--password", default="123456")             # 密码
    parser.add_argument("--database", default="movie_analytics")    # 数据库名
    parser.add_argument("--doc-dir", default=str(default_doc_dir))  # SQL 和 CSV 所在根目录
    parser.add_argument("--batch-size", type=int, default=2000)     # 批量插入的每批次行数
    parser.add_argument("--activity-count", type=int, default=30)   # 要生成的拼团活动数量
    parser.add_argument("--groups-per-activity", type=int, default=4)  # 每个活动下的拼团数
    parser.add_argument("--max-members-per-group", type=int, default=4) # 每个拼团最大成员数
    parser.add_argument("--skip-csv", action="store_true", help="Skip staging CSV import.")
    parser.add_argument("--skip-group-buy", action="store_true", help="Skip gb_* demo data generation.")
    return parser.parse_args()


# ---------- SQL 工具函数 ----------

def split_sql_statements(sql: str) -> list[str]:
    """将包含多条语句的 SQL 文本按分号分割为独立的语句列表，正确处理字符串和注释"""
    statements: list[str] = []
    current: list[str] = []
    in_single = False          # 是否在单引号字符串内
    in_double = False          # 是否在双引号字符串内
    in_backtick = False        # 是否在反引号标识符内
    in_line_comment = False    # 是否在行注释（--）内
    in_block_comment = False   # 是否在块注释（/* */）内
    index = 0

    while index < len(sql):
        char = sql[index]
        next_char = sql[index + 1] if index + 1 < len(sql) else ""

        # 处理行注释
        if in_line_comment:
            current.append(char)
            if char in "\r\n":          # 遇到换行符结束
                in_line_comment = False
            index += 1
            continue

        # 处理块注释
        if in_block_comment:
            current.append(char)
            if char == "*" and next_char == "/":
                current.append(next_char)
                in_block_comment = False
                index += 2
            else:
                index += 1
            continue

        # 不在字符串/标识符内时检查注释开始
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

        # 切换引号状态
        if char == "'" and not in_double and not in_backtick:
            in_single = not in_single
        elif char == '"' and not in_single and not in_backtick:
            in_double = not in_double
        elif char == "`" and not in_single and not in_double:
            in_backtick = not in_backtick

        # 如果在所有引号外遇到分号，则切出一个语句
        if char == ";" and not in_single and not in_double and not in_backtick:
            statement = "".join(current).strip()
            if statement:                   # 过滤空语句
                statements.append(statement)
            current = []
        else:
            current.append(char)

        index += 1

    # 处理最后一条可能没有分号的语句
    statement = "".join(current).strip()
    if statement:
        statements.append(statement)
    return statements


def run_sql_file(cursor, sql_path: Path) -> None:
    """读取 SQL 文件并按顺序执行每一条语句"""
    sql = sql_path.read_text(encoding="utf-8-sig")   # 自动去掉 BOM
    for statement in split_sql_statements(sql):
        cursor.execute(statement)


# ---------- CSV 值处理 ----------

def clean_text(value: str | None) -> str | None:
    """清洗文本：去除两端空白，空字符串视为 None"""
    if value is None:
        return None
    value = value.strip()
    return value if value else None


def coerce_value(value: str | None, column: ColumnSpec) -> Any:
    """根据 ColumnSpec 的类型约束将字符串转换为目标 Python 类型，并截断超长文本"""
    value = clean_text(value)
    if value is None:
        return None

    if column.kind == "int":
        try:
            # 先转为浮点数再取整，兼容 "1.0" 这样的输入
            return int(float(value))
        except ValueError:
            return None

    if column.kind == "decimal":
        try:
            return float(value)   # 转为浮点数，后续插入时由 PyMySQL 转换为 Decimal
        except ValueError:
            return None

    # 字符串类型：若指定了最大长度且超长，则截断
    if column.max_length is not None and len(value) > column.max_length:
        return value[: column.max_length]

    return value


def normalize_headers(headers: Sequence[str | None]) -> dict[str, str]:
    """将 CSV 表头规范化为小写 → 原始表头的映射，忽略 None 和空白头"""
    return {header.strip().lower(): header for header in headers if header is not None and header.strip()}


def repair_row(spec: StagingSpec, row: list[str]) -> list[str]:
    """修复 CSV 行长度与表规格不一致的问题：
    - 列数不足则用空字符串补齐
    - 特定文件（comment.csv, movies.csv）可能因转义缺陷导致分隔符出现在字段内，尝试将多余列合并到对应字段
    """
    expected = len(spec.columns)
    if len(row) == expected:
        return row
    if len(row) < expected:
        return row + [""] * (expected - len(row))
    # 对已知问题文件进行特定修复
    if spec.filename == "comment.csv":
        # 预期结构：前3列正常，中间 content 可能含逗号，最后3列正常
        return row[:3] + [",".join(row[3:-3])] + row[-3:]
    if spec.filename == "movies.csv":
        # 预期结构：前16列正常，中间 storyline 可能含逗号，最后4列正常
        return row[:16] + [",".join(row[16:-4])] + row[-4:]
    # 其他情况直接截断到预期列数
    return row[:expected]


def iter_rows(spec: StagingSpec, csv_path: Path) -> Iterable[tuple[Any, ...]]:
    """读取 CSV 文件，逐行生成经过清洗、转换的元组，跳过必填列缺失的行"""
    with csv_path.open("r", encoding=spec.encoding, errors=spec.encoding_errors, newline="") as f:
        reader = csv.reader(f, delimiter=",", quotechar='"')
        try:
            headers = next(reader)   # 第一行为表头
        except StopIteration:
            return                   # 文件为空

        # 检查表头是否包含所有必需的列
        header_map = normalize_headers(headers)
        missing = [column.name for column in spec.columns if column.name.lower() not in header_map]
        if missing:
            found = ", ".join(headers)
            raise ValueError(f"{spec.filename} missing columns: {', '.join(missing)}; found headers: {found}")

        # 处理数据行
        for row in reader:
            row = repair_row(spec, row)   # 修复列数不匹配的行
            values = tuple(
                coerce_value(row[index] if index < len(row) else None, column)
                for index, column in enumerate(spec.columns)
            )
            # 如果必填列缺失则跳过该行
            if any(column.required and values[index] is None for index, column in enumerate(spec.columns)):
                continue
            yield values


# ---------- 数据库操作 ----------

def truncate_tables(cursor, tables: Sequence[str]) -> None:
    """在关闭外键检查后清空指定的多个表"""
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table in tables:
        cursor.execute(f"TRUNCATE TABLE `{table}`")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")


def insert_staging_batch(cursor, spec: StagingSpec, rows: Sequence[tuple[Any, ...]]) -> None:
    """批量插入 rows 到指定的 staging 表"""
    columns_sql = ", ".join(f"`{name}`" for name in spec.column_names)
    placeholders = ", ".join(["%s"] * len(spec.columns))
    cursor.executemany(f"INSERT INTO `{spec.table}` ({columns_sql}) VALUES ({placeholders})", rows)


def load_staging_table(cursor, spec: StagingSpec, csv_path: Path, batch_size: int) -> int:
    """将单个 CSV 文件分批次导入对应的 staging 表，返回导入总行数"""
    total = 0
    batch: list[tuple[Any, ...]] = []
    for row in iter_rows(spec, csv_path):
        batch.append(row)
        if len(batch) >= batch_size:
            insert_staging_batch(cursor, spec, batch)
            total += len(batch)
            batch.clear()
    if batch:                       # 处理最后不足一批次的数据
        insert_staging_batch(cursor, spec, batch)
        total += len(batch)
    return total


# ---------- 拼团示例数据生成 ----------

def fetch_movies(cursor, limit: int) -> list[Movie]:
    """从 dim_movie 中按豆瓣投票数降序获取指定数量的电影，用于生成拼团活动"""
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
    """从 dim_user 中获取指定数量的用户，用于拼团成员分配"""
    cursor.execute("SELECT user_md5 FROM dim_user ORDER BY user_md5 LIMIT %s", (limit,))
    return [User(user_md5=row[0]) for row in cursor.fetchall()]


def price_for_movie(movie: Movie, index: int) -> tuple[Decimal, Decimal]:
    """根据电影评分和索引计算拼团价和单人价"""
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
    """生成拼团活动、拼团订单、成员、交易记录及每日快照，并插入数据库，返回各表插入行数"""
    required_users = activity_count * groups_per_activity * max_members_per_group
    now = datetime.now().replace(microsecond=0)
    movies = fetch_movies(cursor, activity_count)
    users = fetch_users(cursor, required_users)
    if not movies:
        raise RuntimeError("dim_movie is empty after load_movie_analytics.sql.")
    if not users:
        raise RuntimeError("dim_user is empty after load_movie_analytics.sql.")

    # 清空已有拼团数据
    truncate_tables(cursor, GROUP_BUY_TABLES_IN_DELETE_ORDER)

    activity_rows = []
    activities = []   # 存储活动简化信息，供后续拼团使用
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
                1,                    # 活动状态：进行中
                group_price,
                single_price,
                target_group_size,
                80 + idx * 5,         # 总库存
                0,                    # 锁定库存
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
    snapshots: dict[tuple[str, int], dict[str, Any]] = {}  # (activity_no, movie_id) 聚合信息
    user_index = 0

    # 为每个活动生成多个拼团
    for activity_index, (activity_no, movie_id, target_size, group_price) in enumerate(activities, start=1):
        for group_index in range(1, groups_per_activity + 1):
            group_no = f"GO{activity_index:04d}{group_index:04d}"
            # 每第4个拼团少一人，模拟不同成团状态
            member_count = min(target_size if group_index % 4 != 0 else max(1, target_size - 1), max_members_per_group)
            group_status = 2 if member_count >= target_size else 1  # 2=成功，1=进行中
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

            # 统计快照
            snap = snapshots.setdefault(
                (activity_no, movie_id),
                {"launched": 0, "success": 0, "failed": 0, "participants": 0, "orders": 0, "amount": Decimal("0.00")},
            )
            snap["launched"] += 1
            snap["success"] += 1 if group_status == 2 else 0
            snap["participants"] += member_count
            snap["orders"] += member_count
            snap["amount"] += group_price * member_count

            # 生成拼团成员和交易记录
            for member_index in range(member_count):
                if member_index == 0:
                    user_md5 = leader
                    join_role = 1   # 团长
                else:
                    user_md5 = users[user_index % len(users)].user_md5
                    user_index += 1
                    join_role = 2   # 团员
                pay_time = create_time + timedelta(minutes=5 + member_index * 8)
                member_rows.append((group_no, user_md5, join_role, 1, group_price, pay_time, None, create_time, now))
                trade_rows.append(
                    (
                        f"TO{activity_index:04d}{group_index:04d}{member_index + 1:03d}",
                        group_no,
                        activity_no,
                        user_md5,
                        movie_id,
                        4 if group_status == 2 else 1,  # 已完成或进行中
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

    # 批量插入拼团订单
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
    # 批量插入拼团成员
    cursor.executemany(
        """
        INSERT INTO gb_group_member (
            group_order_no, user_md5, join_role, join_status, pay_amount,
            pay_time, refund_time, create_time, update_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        member_rows,
    )
    # 批量插入交易订单
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

    # 生成每日快照
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


# ---------- 辅助输出 ----------

def print_summary(cursor) -> None:
    """打印所有关键表的记录数"""
    print("\nRecord counts:")
    for table in SUMMARY_TABLES:
        try:
            cursor.execute(f"SELECT COUNT(*) FROM `{table}`")
            print(f"{table}: {cursor.fetchone()[0]}")
        except pymysql.MySQLError as exc:
            print(f"{table}: unavailable ({exc.args[1]})")


# ---------- 主流程 ----------

def main() -> int:
    args = parse_args()
    doc_dir = Path(args.doc_dir)
    mysql_dir = doc_dir / "mysql"
    datasource_dir = doc_dir / "datasource"

    # 检查所有必需文件是否存在
    required_paths = [
        mysql_dir / "init_movie_analytics.sql",
        mysql_dir / "load_movie_analytics.sql",
        mysql_dir / "init_group_buy_tables.sql",
    ]
    required_paths.extend(datasource_dir / spec.filename for spec in STAGING_TABLES)
    missing = [str(path) for path in required_paths if not path.exists()]
    if missing:
        raise FileNotFoundError("Missing required files:\n" + "\n".join(missing))

    # 连接到 MySQL
    connection = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=False,           # 手动控制事务
        local_infile=True,          # 允许 LOAD DATA LOCAL INFILE（虽然本脚本用 Python 逐行插入）
    )

    try:
        with connection.cursor() as cursor:
            cursor.execute("SET NAMES utf8mb4")

            # 步骤 1：初始化电影分析 schema
            print("[1/6] Initializing movie analytics schema...")
            run_sql_file(cursor, mysql_dir / "init_movie_analytics.sql")
            connection.commit()
            cursor.execute(f"USE `{args.database}`")

            # 步骤 2：导入 CSV 数据到 staging 表
            if not args.skip_csv:
                print("[2/6] Loading datasource CSV files into staging tables...")
                truncate_tables(cursor, [spec.table for spec in STAGING_TABLES])
                for spec in STAGING_TABLES:
                    total = load_staging_table(cursor, spec, datasource_dir / spec.filename, args.batch_size)
                    connection.commit()
                    print(f"  {spec.table} <- {spec.filename}: {total} rows")
            else:
                print("[2/6] Skipping CSV staging import.")

            # 步骤 3：转换 staging 数据为维度表和事实表
            print("[3/6] Transforming staging data into analytics tables...")
            run_sql_file(cursor, mysql_dir / "load_movie_analytics.sql")
            connection.commit()

            # 步骤 4：初始化拼团相关表结构
            print("[4/6] Initializing group-buy schema...")
            run_sql_file(cursor, mysql_dir / "init_group_buy_tables.sql")
            connection.commit()
            cursor.execute(f"USE `{args.database}`")

            # 步骤 5：生成拼团演示数据
            if not args.skip_group_buy:
                print("[5/6] Generating group-buy demo data...")
                counts = insert_group_buy_data(cursor, args.activity_count, args.groups_per_activity, args.max_members_per_group)
                connection.commit()
                for table, total in counts.items():
                    print(f"  {table}: {total} rows")
            else:
                print("[5/6] Skipping group-buy data generation.")

            # 步骤 6：打印最终统计
            print("[6/6] Verifying table counts...")
            print_summary(cursor)

        print("\nAll imports completed successfully.")
        return 0
    except Exception:
        connection.rollback()   # 发生任何异常则回滚
        raise
    finally:
        connection.close()      # 确保关闭连接


if __name__ == "__main__":
    # 脚本入口：调用 main() 并以 main 的返回值作为进程退出码
    raise SystemExit(main())