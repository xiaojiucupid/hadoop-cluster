# MySQL 表结构说明

本文档说明 `doc/shell` 目录下 SQL 脚本创建的 MySQL 表结构、每张表的职责、核心字段含义以及表之间的关系，方便你后续做：
- 数据导入
- 业务分析
- 可视化报表
- 拼团业务扩展

当前主要覆盖两部分：
1. 影视分析数据库表
2. 拼团业务相关表

---

# 一、数据库整体设计思路

数据库名默认是：`movie_analytics`

整体分为 4 层：

## 1. 暂存层（Staging）
这一层主要用于承接原始 CSV 数据，尽量保持和源文件字段一致，不在这里做太多业务逻辑。

特点：
- 便于直接 `LOAD DATA` 导入
- 便于排查原始数据问题
- 便于后续重复跑清洗逻辑

## 2. 维度层（Dimension）
这一层存放相对稳定、可复用的实体对象，比如电影、人物、用户、类型、语言、地区、标签等。

特点：
- 适合做查询条件和筛选项
- 适合给事实表提供关联维度
- 更适合 BI 报表系统理解

## 3. 事实层（Fact）
这一层存放交易、行为、评论、评分等“发生过的事件”或“统计主体关系”。

特点：
- 数据量通常较大
- 用来做统计、聚合、趋势分析
- 是可视化报表的核心数据来源

## 4. 桥接/汇总层（Bridge / Snapshot / View）
这一层用于解决多对多关系，或者承接每日快照、统一指标视图。

特点：
- 便于把复杂字符串字段拆开
- 便于后续查询性能优化
- 便于统一口径输出指标

---

# 二、影视分析相关表说明

这些表主要由以下文件创建或填充：
- `init_movie_analytics.sql`
- `load_movie_analytics.sql`

---

## 1. `seq_256`

## 表作用
通用序列表，用于把一列中的多值字符串拆分成多行。

例如：
- `genres` 里可能是 `剧情/爱情/悬疑`
- `tags` 里可能是 `经典/成长/青春`
- `actor_ids` 里可能是多个演员组合

MySQL 本身不像某些数据库那样天然有很强的字符串拆表函数，所以这里通过 `seq_256` 提供一个 1 到 256 的辅助数字表，配合 `SUBSTRING_INDEX` 做拆分。

## 使用场景
- 拆分 `genres`
- 拆分 `languages`
- 拆分 `regions`
- 拆分 `tags`
- 拆分 `actor_ids`
- 拆分 `director_ids`

## 为什么需要它
如果没有这个表，多值字段只能一直保存在一列字符串里，不利于：
- 类型统计
- 标签统计
- 交叉分析
- 多对多关系查询

---

## 2. `stg_movies`

## 表作用
电影原始暂存表，用于接收 `movies.csv` 的原始数据。

## 主要职责
- 接收电影基础信息
- 保留电影的多值字段原貌
- 给后续维表、桥接表、人物关系表提供原始输入

## 典型字段说明
- `movie_id`：电影唯一标识，后续几乎所有电影相关表都依赖它
- `name`：电影名称
- `alias`：别名、又名
- `actors`：演员名称字符串，通常是展示字段
- `directors`：导演名称字符串，通常是展示字段
- `douban_score`：豆瓣评分
- `douban_votes`：豆瓣评分人数
- `genres`：电影类型，通常是多值字段
- `languages`：语言，多值字段
- `regions`：地区，多值字段
- `tags`：标签，多值字段
- `actor_ids`：演员关联信息，后续会拆到人物关系表
- `director_ids`：导演关联信息，后续会拆到人物关系表

## 这一层为什么不直接分析
因为这里的数据还是“原始形态”，很多字段：
- 没有拆分
- 没有规范化
- 可能带空格、空字符串
- 不适合直接做多维聚合

所以它更像 ETL 的输入层。

---

## 3. `stg_persons`

## 表作用
人物原始暂存表，用于接收 `person.csv` 的原始人物数据。

## 主要职责
- 保存演员、导演等人物基础信息
- 给人物维表 `dim_person` 提供原始来源

## 典型字段说明
- `person_id`：人物唯一标识
- `name`：人物名称
- `sex`：性别
- `name_en`：英文名
- `name_zh`：中文名
- `birth`：出生日期原始值
- `birthplace`：出生地
- `constellatory`：星座
- `profession`：职业
- `biography`：简介

## 适合的分析方向
后续基于 `dim_person` 和电影关系表，可以分析：
- 演员参演电影数
- 导演作品数
- 某演员高分电影分布
- 某导演不同类型影片分布

---

## 4. `stg_users`

## 表作用
用户原始暂存表，用于接收 `users.csv` 数据。

## 主要职责
- 保存原始用户标识
- 作为评论、评分关联用户的基础表

## 典型字段说明
- `user_md5`：用户匿名标识，通常是脱敏后的唯一键
- `user_nickname`：用户昵称

## 说明
因为评论和评分一般都要落到用户维度上，所以这个表最终会进入 `dim_user`。

---

## 5. `stg_comments`

## 表作用
评论原始暂存表，用于接收 `comment.csv` 数据。

## 主要职责
- 保存每一条用户评论
- 提供评论内容、点赞数、评论时间等原始行为数据
- 后续进入 `fact_comment`

## 典型字段说明
- `comment_id`：评论唯一标识
- `user_md5`：评论用户
- `movie_id`：评论所属电影
- `content`：评论正文
- `votes`：评论点赞数或有用数
- `comment_time`：评论时间原始值
- `rating`：评论中附带的评分

## 适合的分析方向
- 评论数量趋势
- 热门电影评论量排行
- 高互动评论分析
- 活跃评论用户排行

---

## 6. `stg_ratings`

## 表作用
评分原始暂存表，用于接收 `ratings.csv` 数据。

## 主要职责
- 保存用户对电影的评分行为
- 为评分统计、平均分统计提供基础数据
- 后续进入 `fact_rating`

## 典型字段说明
- `rating_id`：评分记录唯一标识
- `user_md5`：评分用户
- `movie_id`：被评分电影
- `rating`：评分值
- `rating_time`：评分时间原始值

## 适合的分析方向
- 用户评分分布
- 不同年份电影的平均用户评分
- 电影热度与评分关系分析

---

## 7. `dim_movie`

## 表作用
电影维表，是整个影视分析模型里的核心维度表。

## 主要职责
- 存放经过清洗后的电影主数据
- 为评论、评分、拼团活动等业务提供统一的电影主键
- 作为 BI 分析里的核心筛选维度

## 典型字段说明
- `movie_sk`：内部自增代理键
- `movie_id`：外部业务唯一标识
- `name`：电影名
- `alias`：别名
- `cover`：封面图地址
- `imdb_id`：IMDb 标识
- `douban_score`：豆瓣评分
- `douban_votes`：豆瓣评分人数
- `mins`：片长
- `official_site`：官网
- `release_date_raw`：原始上映日期
- `release_year`：上映年份，便于按年分析
- `slug`：URL 友好的标识
- `storyline`：剧情简介
- `source_loaded_at`：数据装载时间

## 这个表为什么重要
几乎所有业务都会围绕“电影”展开，例如：
- 评论分析
- 评分分析
- 类型分析
- 拼团活动分析

所以它是一个标准的“主题中心维度表”。

---

## 8. `dim_person`

## 表作用
人物维表，存放演员、导演等人物主数据。

## 主要职责
- 保存清洗后的标准人物信息
- 和 `fact_movie_person` 一起构成电影与人物之间的关系分析基础

## 适合的分析场景
- 某演员参演电影数量
- 某导演平均评分
- 人物与电影类型偏好分析

---

## 9. `dim_user`

## 表作用
用户维表，存放用户主信息。

## 主要职责
- 给评论事实表和评分事实表提供统一用户维度
- 支持用户行为分析

## 适合的分析场景
- 活跃用户识别
- 用户评论偏好
- 用户评分分布
- 用户参与拼团行为关联

---

## 10. `dim_genre`

## 表作用
电影类型维表。

## 主要职责
- 保存拆分后的电影类型值，例如剧情、动作、爱情、悬疑等
- 给报表系统提供标准类型字典

## 为什么单独建表
如果类型始终保存在 `stg_movies.genres` 的字符串里，就不方便做：
- 类型排行
- 类型筛选
- 类型交叉统计

所以需要把它单独抽成维表。

---

## 11. `dim_language`

## 表作用
语言维表。

## 主要职责
- 保存电影语言标准值
- 支持按语言做聚合分析

## 适合的分析场景
- 不同语言电影数量分布
- 不同语言电影平均评分
- 语言和地区的组合分析

---

## 12. `dim_region`

## 表作用
地区维表。

## 主要职责
- 保存电影所属国家/地区标准值
- 支持地区分布分析

## 适合的分析场景
- 各地区电影数量统计
- 各地区高分电影占比
- 地区与类型、语言的交叉分析

---

## 13. `dim_tag`

## 表作用
标签维表。

## 主要职责
- 保存拆分后的标签值
- 支持更细粒度的内容画像分析

## 适合的分析场景
- 热门标签统计
- 高分电影常见标签
- 标签与评论热度的关系分析

---

## 14. `fact_movie_person`

## 表作用
电影人物关联事实表，用来表达“某电影和某人物之间是什么关系”。

## 主要职责
- 连接电影和人物
- 标识人物在电影中的角色类型
- 保存原始顺序信息

## 典型字段说明
- `movie_id`：电影 ID
- `person_id`：人物 ID
- `role_type`：角色类型，当前设计为 `ACTOR` 或 `DIRECTOR`
- `display_name`：展示名称
- `source_rank`：原始顺序，例如演员表中的先后顺序

## 为什么它很重要
这是一个典型的多对多关系表：
- 一部电影有多个演员/导演
- 一个演员/导演也可能对应多部电影

## 适合的分析场景
- 演员作品数排行
- 导演作品数排行
- 高分演员/导演分析
- 电影主创与评分表现关系分析

---

## 15. `bridge_movie_genre`

## 表作用
电影与类型的桥接表。

## 主要职责
把一部电影的多个类型拆成多行存储。

例如：
- 一部电影是 `剧情/爱情`
- 会拆成两条记录

## 为什么需要桥接表
这是标准的多对多建模方式，便于：
- 按类型筛选电影
- 做类型统计
- 做电影与类型的交叉分析

---

## 16. `bridge_movie_language`

## 表作用
电影与语言的桥接表。

## 主要职责
把一部电影的多个语言属性拆成多行。

## 适合的分析场景
- 多语言影片占比
- 不同语言电影评论热度对比

---

## 17. `bridge_movie_region`

## 表作用
电影与地区的桥接表。

## 主要职责
把电影所属地区拆成标准多行关系。

## 适合的分析场景
- 地区电影产量统计
- 地区与类型交叉分析
- 地区与评分表现分析

---

## 18. `bridge_movie_tag`

## 表作用
电影与标签的桥接表。

## 主要职责
把标签拆分为标准多对多关系，便于做标签分析。

## 适合的分析场景
- 标签热度排行
- 标签与评分关系
- 标签与评论量关系

---

## 19. `fact_comment`

## 表作用
评论事实表，保存用户对电影的评论行为。

## 主要职责
- 承接评论业务事实
- 作为评论统计分析的数据基础
- 支撑内容热度分析

## 典型字段说明
- `comment_id`：评论 ID
- `user_md5`：评论用户
- `movie_id`：所属电影
- `content`：评论内容
- `votes`：评论互动值
- `comment_time_raw`：评论时间原始值
- `rating`：评论附带评分
- `source_loaded_at`：装载时间

## 适合的分析场景
- 每部电影评论数
- 评论点赞数排行
- 活跃评论用户排行
- 高热度影片识别

---

## 20. `fact_rating`

## 表作用
评分事实表，保存用户评分行为。

## 主要职责
- 作为评分统计的核心事实表
- 支撑电影口碑分析
- 支撑用户评分偏好分析

## 典型字段说明
- `rating_id`：评分记录 ID
- `user_md5`：评分用户
- `movie_id`：被评分电影
- `rating`：评分值
- `rating_time_raw`：评分时间原始值
- `source_loaded_at`：装载时间

## 适合的分析场景
- 电影评分分布
- 高分/低分电影统计
- 用户评分行为画像

---

## 21. `vw_movie_core_metrics`

## 表作用
电影核心指标视图。

## 主要职责
把一部电影最常用的核心分析指标汇总到一起，减少重复 SQL 编写。

## 当前聚合指标
- 电影 ID
- 电影名称
- 上映年份
- 豆瓣评分
- 豆瓣评分人数
- 用户评分数
- 用户平均评分
- 评论数
- 评论总点赞数

## 适合的使用场景
- 首页总览报表
- 热门电影排行
- 电影综合指标看板
- 可视化工具直接连表/视图展示

---

# 三、拼团相关表说明

这些表主要由以下文件创建：
- `init_group_buy_tables.sql`

这部分是围绕“电影也可以被当成一个拼团商品”来设计的。也就是说：
- `movie_id` 在拼团场景下可理解为商品 ID
- 同一个电影可配置一个或多个拼团活动
- 用户可开团、参团、支付、退款、查看统计结果

---

## 22. `gb_activity`

## 表作用
拼团活动表，是拼团业务的配置中心。

## 主要职责
- 定义某个电影/商品的拼团活动规则
- 保存活动价格、库存、时间范围、成团人数等信息
- 作为后续拼团主单、交易订单、快照统计的上游基础

## 典型字段说明
- `activity_no`：活动编号，业务唯一标识
- `activity_name`：活动名称
- `movie_id`：关联电影/商品 ID
- `activity_status`：活动状态
- `group_price`：拼团价
- `single_price`：单买价
- `target_group_size`：目标成团人数
- `stock_total`：总库存
- `stock_locked`：锁定库存
- `start_time` / `end_time`：活动时间窗口
- `description`：活动说明

## 业务理解
这张表更偏“活动配置”，它不记录谁参团，而是定义“可以发起怎样的拼团”。

## 适合的分析场景
- 活动数量统计
- 活动生命周期管理
- 不同电影的拼团配置分析
- 拼团价与单买价对比分析

---

## 23. `gb_group_order`

## 表作用
拼团主单表，表示一次具体的“开团行为”。

## 主要职责
- 记录某次开团是谁发起的
- 记录这次团当前处于什么状态
- 记录当前成员数、目标人数、成团时间、关闭时间等

## 典型字段说明
- `group_order_no`：拼团单号
- `activity_no`：所属活动编号
- `movie_id`：关联商品/电影
- `leader_user_md5`：团长用户
- `group_status`：拼团状态
- `current_member_count`：当前团内人数
- `target_group_size`：目标人数
- `expire_time`：过期时间
- `success_time`：成团时间
- `close_time`：关闭时间

## 业务理解
一条记录就代表一个“团”。

例如：
- 用户 A 发起一个《某电影联名周边》3 人团
- 这个团从创建到成团/失败的整个生命周期都记录在这里

## 适合的分析场景
- 开团数
- 成团率
- 平均成团耗时
- 团长行为分析

---

## 24. `gb_group_member`

## 表作用
拼团成员表，记录每个团里有哪些用户参加。

## 主要职责
- 表示用户和团之间的关系
- 标记用户是团长还是团员
- 记录用户支付、退款、取消等状态

## 典型字段说明
- `group_order_no`：所属拼团单
- `user_md5`：参团用户
- `join_role`：角色，1-团长，2-团员
- `join_status`：参团状态
- `pay_amount`：支付金额
- `pay_time`：支付时间
- `refund_time`：退款时间

## 业务理解
这是一张典型的“拼团参与明细表”。

如果 `gb_group_order` 是“一个团”，那 `gb_group_member` 就是“团里面每个人”。

## 适合的分析场景
- 参团用户数
- 团长/团员结构分析
- 退款人数统计
- 用户拼团参与频次分析

---

## 25. `gb_trade_order`

## 表作用
拼团交易订单表，记录每个用户的真实支付订单。

## 主要职责
- 表示用户因参加拼团而产生的一笔交易
- 承接订单金额、优惠金额、实付金额、支付状态等数据
- 为 GMV、支付转化、退款分析提供基础

## 典型字段说明
- `trade_order_no`：交易订单号
- `group_order_no`：所属拼团单号
- `activity_no`：所属活动编号
- `user_md5`：下单用户
- `movie_id`：商品/电影 ID
- `order_status`：订单状态
- `order_amount`：订单原价
- `discount_amount`：优惠金额
- `payable_amount`：实付金额
- `pay_time`：支付时间
- `finish_time`：完成时间
- `close_time`：关闭时间

## 业务理解
- `gb_group_order` 关注“团是否成功”
- `gb_group_member` 关注“谁参加了团”
- `gb_trade_order` 关注“每个人到底付了多少钱、订单状态如何”

这张表是财务和营收分析最关键的一张表。

## 适合的分析场景
- 拼团 GMV
- 已支付订单数
- 退款金额统计
- 用户付费转化分析
- 单活动营收分析

---

## 26. `gb_group_snapshot`

## 表作用
拼团日汇总快照表，用于按天沉淀活动级统计指标。

## 主要职责
- 按天记录每个活动的拼团表现
- 减少每次报表都从明细表实时聚合的压力
- 为看板系统提供更快的查询入口

## 典型字段说明
- `stat_date`：统计日期
- `activity_no`：活动编号
- `movie_id`：关联电影/商品
- `launched_group_count`：开团数
- `success_group_count`：成团数
- `failed_group_count`：失败团数
- `participant_count`：参团人数
- `paid_order_count`：支付订单数
- `paid_amount`：支付总金额
- `refund_amount`：退款总金额

## 业务理解
这是一个“按日汇总表”，适合看趋势，不适合看单条用户行为。

## 适合的分析场景
- 每日成团趋势
- 每日 GMV 趋势
- 每日退款趋势
- 活动运营效果日报

---

# 四、表之间的核心关系

---

## 1. 影视分析主链路

### 电影主线
- `stg_movies` -> `dim_movie`
- `dim_movie` 是电影主维表

### 人物主线
- `stg_persons` -> `dim_person`
- `dim_movie` + `dim_person` -> `fact_movie_person`

### 用户主线
- `stg_users` -> `dim_user`

### 评论主线
- `stg_comments` -> `fact_comment`
- `fact_comment.movie_id` -> `dim_movie.movie_id`
- `fact_comment.user_md5` 可关联 `dim_user.user_md5`

### 评分主线
- `stg_ratings` -> `fact_rating`
- `fact_rating.movie_id` -> `dim_movie.movie_id`
- `fact_rating.user_md5` 可关联 `dim_user.user_md5`

### 多值维度拆分主线
- `stg_movies.genres` -> `dim_genre` + `bridge_movie_genre`
- `stg_movies.languages` -> `dim_language` + `bridge_movie_language`
- `stg_movies.regions` -> `dim_region` + `bridge_movie_region`
- `stg_movies.tags` -> `dim_tag` + `bridge_movie_tag`

---

## 2. 拼团业务主链路

### 活动配置
- `gb_activity.movie_id` -> `dim_movie.movie_id`

### 开团主单
- `gb_group_order.activity_no` -> `gb_activity.activity_no`
- `gb_group_order.movie_id` -> `dim_movie.movie_id`
- `gb_group_order.leader_user_md5` -> `dim_user.user_md5`

### 参团成员
- `gb_group_member.group_order_no` -> `gb_group_order.group_order_no`
- `gb_group_member.user_md5` -> `dim_user.user_md5`

### 交易订单
- `gb_trade_order.group_order_no` -> `gb_group_order.group_order_no`
- `gb_trade_order.activity_no` -> `gb_activity.activity_no`
- `gb_trade_order.user_md5` -> `dim_user.user_md5`
- `gb_trade_order.movie_id` -> `dim_movie.movie_id`

### 每日汇总
- `gb_group_snapshot.activity_no` -> `gb_activity.activity_no`
- `gb_group_snapshot.movie_id` -> `dim_movie.movie_id`

---

# 五、如果你后面要做可视化，建议怎么用

## 影视分析看板建议优先使用
- `dim_movie`
- `fact_comment`
- `fact_rating`
- `fact_movie_person`
- `bridge_movie_genre`
- `bridge_movie_region`
- `bridge_movie_language`
- `vw_movie_core_metrics`

## 拼团运营看板建议优先使用
- `gb_activity`
- `gb_group_order`
- `gb_group_member`
- `gb_trade_order`
- `gb_group_snapshot`

---

# 六、常见分析问题对应推荐表

## 1. 哪些电影最热门？
推荐表：
- `vw_movie_core_metrics`
- `fact_comment`
- `fact_rating`

## 2. 哪些类型的电影最多？
推荐表：
- `bridge_movie_genre`
- `dim_genre`

## 3. 哪些演员参演作品最多？
推荐表：
- `fact_movie_person`
- `dim_person`

## 4. 哪些用户最活跃？
推荐表：
- `fact_comment`
- `fact_rating`
- `dim_user`

## 5. 哪些拼团活动效果最好？
推荐表：
- `gb_activity`
- `gb_group_snapshot`
- `gb_trade_order`

## 6. 拼团成团率如何？
推荐表：
- `gb_group_order`
- `gb_group_snapshot`

## 7. 哪些用户最喜欢参加拼团？
推荐表：
- `gb_group_member`
- `gb_trade_order`
- `dim_user`

---

# 七、后续可以继续扩展的方向

如果你后面还想继续完善，我建议可以补这些内容：

## 1. 时间维表
例如：
- `dim_date`
- `dim_month`

方便做：
- 日趋势
- 周趋势
- 月报
- 同比环比

## 2. 拼团活动商品扩展表
如果后面不只是一部电影参与拼团，而是：
- 电影票
- 周边商品
- 联名套餐

可以继续补：
- 商品表
- SKU 表
- 库存流水表

## 3. 评论情感分析结果表
如果后面接 AI 或 NLP：
- 正向/负向情感标签
- 评论关键词抽取
- 主题聚类结果

## 4. 宽表或物化汇总表
如果后面报表很多、查询很重，可以加：
- 电影分析宽表
- 活动效果宽表
- 用户拼团行为宽表

---

# 八、总结

当前这套表设计已经能覆盖两大方向：

## 1. 影视数据分析
核心目标是：
- 让原始 CSV 可导入
- 让多值字段可拆分
- 让电影、人物、用户、评论、评分形成完整分析链路

## 2. 拼团业务建模
核心目标是：
- 支持活动配置
- 支持开团、参团、下单、退款
- 支持按天做运营统计

如果你愿意，我下一步还可以继续帮你补：
- `mysql.md` 的 ER 图说明版
- 每张表对应的示例查询 SQL
- 一份“建表顺序 + 导入顺序 + 查询顺序”的操作文档
- 一份“拼团看板指标口径说明文档”
