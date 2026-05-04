SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `movie_analytics`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `movie_analytics`;

CREATE TABLE IF NOT EXISTS gb_activity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  activity_no VARCHAR(64) NOT NULL COMMENT '拼团活动编号',
  activity_name VARCHAR(128) NOT NULL COMMENT '拼团活动名称',
  movie_id BIGINT NOT NULL COMMENT '关联电影ID/商品ID',
  activity_status TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态 0-待上线 1-进行中 2-已结束 3-已关闭',
  group_price DECIMAL(10,2) NOT NULL COMMENT '拼团价',
  single_price DECIMAL(10,2) NOT NULL COMMENT '单买价',
  target_group_size INT NOT NULL DEFAULT 2 COMMENT '成团人数',
  stock_total INT NOT NULL DEFAULT 0 COMMENT '活动总库存',
  stock_locked INT NOT NULL DEFAULT 0 COMMENT '锁定库存',
  start_time DATETIME NOT NULL COMMENT '活动开始时间',
  end_time DATETIME NOT NULL COMMENT '活动结束时间',
  description VARCHAR(500) DEFAULT NULL COMMENT '活动说明',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_no (activity_no),
  KEY idx_movie_id (movie_id),
  KEY idx_activity_status (activity_status),
  KEY idx_activity_time (start_time, end_time),
  CONSTRAINT fk_gb_activity_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团活动表';

CREATE TABLE IF NOT EXISTS gb_group_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  group_order_no VARCHAR(64) NOT NULL COMMENT '拼团单号',
  activity_no VARCHAR(64) NOT NULL COMMENT '拼团活动编号',
  movie_id BIGINT NOT NULL COMMENT '关联电影ID/商品ID',
  leader_user_md5 CHAR(32) NOT NULL COMMENT '团长用户标识',
  group_status TINYINT NOT NULL DEFAULT 0 COMMENT '拼团状态 0-待支付 1-拼团中 2-已成团 3-拼团失败 4-已取消',
  current_member_count INT NOT NULL DEFAULT 1 COMMENT '当前参团人数',
  target_group_size INT NOT NULL COMMENT '目标成团人数',
  expire_time DATETIME NOT NULL COMMENT '拼团过期时间',
  success_time DATETIME DEFAULT NULL COMMENT '成团时间',
  close_time DATETIME DEFAULT NULL COMMENT '关闭时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_order_no (group_order_no),
  KEY idx_activity_no (activity_no),
  KEY idx_movie_id (movie_id),
  KEY idx_leader_user_md5 (leader_user_md5),
  KEY idx_group_status (group_status),
  CONSTRAINT fk_gb_group_order_activity FOREIGN KEY (activity_no) REFERENCES gb_activity(activity_no),
  CONSTRAINT fk_gb_group_order_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id),
  CONSTRAINT fk_gb_group_order_user FOREIGN KEY (leader_user_md5) REFERENCES dim_user(user_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团主单表';

CREATE TABLE IF NOT EXISTS gb_group_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  group_order_no VARCHAR(64) NOT NULL COMMENT '拼团单号',
  user_md5 CHAR(32) NOT NULL COMMENT '用户标识',
  join_role TINYINT NOT NULL DEFAULT 1 COMMENT '参团角色 1-团长 2-团员',
  join_status TINYINT NOT NULL DEFAULT 0 COMMENT '参团状态 0-待支付 1-已参团 2-已退款 3-已取消',
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
  pay_time DATETIME DEFAULT NULL COMMENT '支付时间',
  refund_time DATETIME DEFAULT NULL COMMENT '退款时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_user (group_order_no, user_md5),
  KEY idx_user_md5 (user_md5),
  KEY idx_join_status (join_status),
  CONSTRAINT fk_gb_group_member_order FOREIGN KEY (group_order_no) REFERENCES gb_group_order(group_order_no),
  CONSTRAINT fk_gb_group_member_user FOREIGN KEY (user_md5) REFERENCES dim_user(user_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团成员表';

CREATE TABLE IF NOT EXISTS gb_trade_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  trade_order_no VARCHAR(64) NOT NULL COMMENT '交易订单号',
  group_order_no VARCHAR(64) NOT NULL COMMENT '拼团单号',
  activity_no VARCHAR(64) NOT NULL COMMENT '活动编号',
  user_md5 CHAR(32) NOT NULL COMMENT '下单用户',
  movie_id BIGINT NOT NULL COMMENT '关联电影ID/商品ID',
  order_status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态 0-待支付 1-已支付 2-已退款 3-已关闭 4-已完成',
  order_amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  payable_amount DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  pay_time DATETIME DEFAULT NULL COMMENT '支付时间',
  finish_time DATETIME DEFAULT NULL COMMENT '完成时间',
  close_time DATETIME DEFAULT NULL COMMENT '关闭时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trade_order_no (trade_order_no),
  KEY idx_group_order_no (group_order_no),
  KEY idx_activity_no (activity_no),
  KEY idx_user_md5 (user_md5),
  KEY idx_order_status (order_status),
  CONSTRAINT fk_gb_trade_order_group FOREIGN KEY (group_order_no) REFERENCES gb_group_order(group_order_no),
  CONSTRAINT fk_gb_trade_order_activity FOREIGN KEY (activity_no) REFERENCES gb_activity(activity_no),
  CONSTRAINT fk_gb_trade_order_user FOREIGN KEY (user_md5) REFERENCES dim_user(user_md5),
  CONSTRAINT fk_gb_trade_order_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团交易订单表';

CREATE TABLE IF NOT EXISTS gb_group_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  stat_date DATE NOT NULL COMMENT '统计日期',
  activity_no VARCHAR(64) NOT NULL COMMENT '活动编号',
  movie_id BIGINT NOT NULL COMMENT '关联电影ID/商品ID',
  launched_group_count INT NOT NULL DEFAULT 0 COMMENT '开团数',
  success_group_count INT NOT NULL DEFAULT 0 COMMENT '成团数',
  failed_group_count INT NOT NULL DEFAULT 0 COMMENT '失败团数',
  participant_count INT NOT NULL DEFAULT 0 COMMENT '参团人数',
  paid_order_count INT NOT NULL DEFAULT 0 COMMENT '支付订单数',
  paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '支付总金额',
  refund_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '退款总金额',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_activity (stat_date, activity_no),
  KEY idx_movie_id (movie_id),
  CONSTRAINT fk_gb_group_snapshot_activity FOREIGN KEY (activity_no) REFERENCES gb_activity(activity_no),
  CONSTRAINT fk_gb_group_snapshot_movie FOREIGN KEY (movie_id) REFERENCES dim_movie(movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团日汇总快照表';

SET FOREIGN_KEY_CHECKS = 1;
