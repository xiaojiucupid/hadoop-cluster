# 基于 Hadoop 生态的影视智能分析与个性化推荐系统

## 1. 启动方式

> 本项目后端使用 Spring Boot 3，要求 **JDK 17+**。如果本机 `mvn -version` 仍显示 Java 8，请先把 `JAVA_HOME` 和 `Path` 切换到 JDK 17。

### 1.1 修改配置

后端数据库与 Hadoop 配置写在：

```text
app/src/main/resources/application.yml
```

主要配置项：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://139.155.136.111:13306/movie_analytics?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: 123456

movie:
  hadoop:
    hdfs-uri: hdfs://localhost:9000
    raw-data-path: /movie/raw
    clean-data-path: /movie/clean
    analytics-result-path: /movie/result
```

前端开发服务器和后端代理配置写在：

```text
doc/movie-dashboard-vue/vite.config.js
doc/movie-analytics-vue/vite.config.js
```

当前前端代理规则：

```js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}
```

如果后端不是运行在 `localhost:8080`，需要同步修改这里。

---

### 1.2 初始化 MySQL 表结构与数据

SQL 脚本位于：

```text
doc/mysql/
```

常用脚本：

```text
doc/mysql/init_movie_analytics.sql        # 初始化电影分析表结构
doc/mysql/load_movie_analytics.sql        # 从 stg_* 表加工 dim_*/fact_*/bridge_* 表
doc/mysql/init_group_buy_tables.sql       # 初始化拼团业务表
doc/mysql/init_recommendation_tables.sql  # 初始化推荐结果表
```

如果已经把原始数据导入到了 `stg_movies`、`stg_persons`、`stg_users`、`stg_comments`、`stg_ratings`，可以执行：

```bash
python doc/python/load_from_staging_and_group_buy.py \
  --host 139.155.136.111 \
  --port 13306 \
  --user root \
  --password 123456 \
  --database movie_analytics
```

这个脚本会：

1. 初始化电影分析表；
2. 初始化拼团表；
3. 从 `stg_*` 表生成 `dim_*`、`fact_*`、`bridge_*`；
4. 生成拼团演示数据；
5. 输出各表记录数。

---

### 1.3 启动后端

在项目根目录执行：

```bash
mvn -DskipTests clean package
```

然后启动 Spring Boot 应用：

```bash
mvn -pl app spring-boot:run
```

或者直接运行启动类：

```text
app/src/main/java/com/cev/movie/MovieAnalyticsApplication.java
```

后端默认端口：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/actuator/health
```

---

### 1.4 启动电影推荐与分析看板前端

主看板位于：

```text
doc/movie-dashboard-vue/
```

启动方式：

```bash
cd doc/movie-dashboard-vue
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

构建方式：

```bash
npm run build
```

---

### 1.5 启动另一个电影分析前端

另一个前端原型位于：

```text
doc/movie-analytics-vue/
```

启动方式：

```bash
cd doc/movie-analytics-vue
npm install
npm run dev
```

默认端口同样是：

```text
http://localhost:5173
```

注意：两个 Vue 项目不要同时占用同一个端口。如果同时启动，需要修改其中一个项目的 `vite.config.js`。

---

### 1.6 执行 MapReduce 推荐任务

MapReduce 模块位于：

```text
mapreduce/
```

先打包：

```bash
mvn -pl mapreduce -am -DskipTests package
```

推荐流水线脚本位于：

```text
doc/shell/run_hadoop_recommendation_pipeline.sh
```

在 Hadoop 环境中执行：

```bash
bash doc/shell/run_hadoop_recommendation_pipeline.sh
```

脚本会：

1. 从 MySQL 导出推荐候选数据到 HDFS；
2. 执行热门电影推荐；
3. 执行标签偏好推荐；
4. 执行混合推荐；
5. 将推荐结果写回 MySQL。

脚本中的默认数据库和 HDFS 参数可以通过环境变量覆盖，例如：

```bash
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3306 \
MYSQL_USER=root \
MYSQL_PASSWORD=123456 \
MYSQL_DATABASE=movie_analytics \
HDFS_INPUT_DIR=/movie/mysql \
HDFS_RESULT_DIR=/movie/result \
RECOMMEND_TOP_N=20 \
bash doc/shell/run_hadoop_recommendation_pipeline.sh
```

---

## 2. 配置文件位置说明

### 2.1 后端配置

```text
app/src/main/resources/application.yml
```

包含：

- Spring Boot 服务端口；
- MySQL 数据源；
- MyBatis-Plus 配置；
- Hadoop/HDFS 路径；
- Actuator 暴露配置。

Hadoop 配置绑定类：

```text
app/src/main/java/com/cev/movie/config/HadoopProperties.java
```

### 2.2 前端配置

主看板：

```text
doc/movie-dashboard-vue/vite.config.js
```

另一个分析前端：

```text
doc/movie-analytics-vue/vite.config.js
```

包含：

- Vite 端口；
- `/api` 代理到后端地址。

### 2.3 Maven 模块配置

根 Maven 配置：

```text
pom.xml
```

子模块配置：

```text
api/pom.xml
app/pom.xml
domain/pom.xml
infrastructure/pom.xml
trigger/pom.xml
mapreduce/pom.xml
types/pom.xml
```

根模块声明了 Java 版本和模块结构：

```xml
<java.version>17</java.version>
```

### 2.4 数据库脚本配置

```text
doc/mysql/
```

主要用于初始化表结构、装载分析宽表、初始化推荐结果表和拼团表。

### 2.5 数据导入与推荐流水线脚本

Python 数据处理脚本：

```text
doc/python/
```

Shell 推荐流水线：

```text
doc/shell/run_hadoop_recommendation_pipeline.sh
```

---

## 3. 项目架构

项目采用多模块 Maven 架构，整体分为 API 层、领域层、基础设施层、接口触发层、启动模块、MapReduce 离线计算模块和前端看板模块。

```text
hadoop-cluster
├── api/                         # API DTO、统一响应对象
├── domain/                      # 领域模型、领域服务、仓储接口
├── infrastructure/              # 仓储实现、数据库访问、PO 对象
├── trigger/                     # HTTP Controller，提供 REST API
├── app/                         # Spring Boot 启动入口与配置
├── mapreduce/                   # Hadoop MapReduce 离线推荐任务
├── types/                       # 通用类型定义
├── doc/
│   ├── mysql/                   # MySQL 初始化与加工 SQL
│   ├── python/                  # 数据导入、HDFS 导出、结果导入脚本
│   ├── shell/                   # Hadoop 推荐任务流水线脚本
│   ├── movie-dashboard-vue/     # 推荐与数据分析看板 Vue 前端
│   └── movie-analytics-vue/     # 电影分析 Vue 前端原型
└── README.md
```

---

## 4. 后端模块说明

### 4.1 `api` 模块

存放对外接口 DTO 和统一响应对象。

典型文件：

```text
api/src/main/java/com/cev/movie/api/response/ApiResponse.java
api/src/main/java/com/cev/movie/api/dashboard/MovieDashboardDTO.java
api/src/main/java/com/cev/movie/api/analytics/MovieInsightDTO.java
api/src/main/java/com/cev/movie/api/agent/MovieAgentDTO.java
api/src/main/java/com/cev/movie/api/recommendation/RecommendationDTO.java
```

### 4.2 `domain` 模块

存放推荐领域模型、推荐算法类型、领域服务和仓储接口。

典型文件：

```text
domain/src/main/java/com/cev/movie/domain/recommendation/model/Recommendation.java
domain/src/main/java/com/cev/movie/domain/recommendation/model/RecommendationAlgorithmType.java
domain/src/main/java/com/cev/movie/domain/recommendation/service/RecommendationDomainService.java
domain/src/main/java/com/cev/movie/domain/recommendation/repository/RecommendationRepository.java
```

### 4.3 `infrastructure` 模块

存放基础设施实现，包括 MySQL 查询、推荐结果表读取、PO 对象等。

典型文件：

```text
infrastructure/src/main/java/com/cev/movie/infrastructure/repository/RecommendationRepositoryImpl.java
infrastructure/src/main/java/com/cev/movie/infrastructure/persistence/po/RecommendationPO.java
```

### 4.4 `trigger` 模块

存放 HTTP Controller，是前端调用后端的主要入口。

典型接口：

```text
trigger/src/main/java/com/cev/movie/trigger/http/MovieDashboardController.java
trigger/src/main/java/com/cev/movie/trigger/http/MovieInsightController.java
trigger/src/main/java/com/cev/movie/trigger/http/MovieAgentController.java
trigger/src/main/java/com/cev/movie/trigger/http/RecommendationController.java
trigger/src/main/java/com/cev/movie/trigger/http/RecommendationDashboardController.java
```

### 4.5 `app` 模块

Spring Boot 启动模块，负责加载配置并启动整个后端应用。

典型文件：

```text
app/src/main/java/com/cev/movie/MovieAnalyticsApplication.java
app/src/main/resources/application.yml
```

### 4.6 `mapreduce` 模块

实现 Hadoop MapReduce 离线推荐任务。

典型任务：

```text
mapreduce/src/main/java/com/cev/movie/mapreduce/MovieAnalyticsMapReduceApp.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/HotMovieRecommendationJob.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/TagPreferenceRecommendationJob.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/HybridRecommendationJob.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/UserCollaborativeFilteringJob.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/ItemCollaborativeFilteringJob.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/QualityBasedRecommendationJob.java
mapreduce/src/main/java/com/cev/movie/mapreduce/job/UserGenrePreferenceJob.java
```

---

## 5. 已实现的核心功能

### 5.1 电影数据分析看板

后端接口：

```text
GET /api/movie-dashboard
```

返回内容包括：

- 电影总数；
- 用户总数；
- 评分记录数；
- 评论记录数；
- 拼团营收；
- 成团率；
- 评分分布；
- 类型排行；
- 地区排行；
- 拼团趋势；
- 热门电影；
- 拼团活动。

前端页面：

```text
doc/movie-dashboard-vue/src/App.vue
```

### 5.2 电影推荐与分析指标接口

后端接口：

```text
GET /api/movie-insights?limit=6
```

返回内容包括：

- 概览指标；
- 标签热度；
- 地区热度；
- 评分区间；
- 质量评分 Top 电影；
- 用户偏好画像；
- 推荐算法结果统计；
- 数据源接入情况。

接口实现：

```text
trigger/src/main/java/com/cev/movie/trigger/http/MovieInsightController.java
```

### 5.3 电影推荐 Agent

后端接口：

```text
GET /api/movie-agent/analyze?limit=6
GET /api/movie-agent/context?limit=6
```

`/analyze` 会返回 Agent 分析结论；`/context` 只返回 Agent 输入参数，方便调试或后续接入真实大模型。

Agent 输入参数来自：

- MySQL 电影维表；
- MySQL 用户维表；
- 评分事实表；
- 评论事实表；
- 标签桥接表；
- MapReduce 推荐结果表；
- 推荐兜底计算结果。

Agent 输出包括：

- 总结；
- 核心洞察；
- 推荐策略；
- 行动建议；
- 证据参数。

接口实现：

```text
trigger/src/main/java/com/cev/movie/trigger/http/MovieAgentController.java
api/src/main/java/com/cev/movie/api/agent/MovieAgentDTO.java
```

### 5.4 个性化推荐接口

后端接口：

```text
GET /api/recommendations/users/{userId}?algorithm=HYBRID&limit=10
```

用于查询指定用户的推荐列表。

接口实现：

```text
trigger/src/main/java/com/cev/movie/trigger/http/RecommendationController.java
```

### 5.5 推荐看板接口

项目中还包含推荐看板相关 Controller：

```text
trigger/src/main/java/com/cev/movie/trigger/http/RecommendationDashboardController.java
```

用于展示推荐系统整体运行情况和推荐结果统计。

### 5.6 拼团业务数据分析

项目中包含拼团相关数据表和看板展示字段：

- 拼团活动；
- 拼团订单；
- 拼团成员；
- 支付订单；
- 拼团快照统计；
- 拼团趋势；
- 成团率；
- 拼团营收。

SQL 初始化脚本：

```text
doc/mysql/init_group_buy_tables.sql
```

演示数据生成脚本：

```text
doc/python/load_from_staging_and_group_buy.py
```

---

## 6. 数据链路

### 6.1 数据从 MySQL 到看板

```text
MySQL stg_* 原始表
  -> load_movie_analytics.sql
  -> dim_*/fact_*/bridge_* 分析表
  -> Spring Boot Controller 聚合查询
  -> Vue 前端 ECharts 展示
```

### 6.2 推荐结果链路

```text
MySQL 分析表
  -> export_mysql_to_hdfs.py
  -> HDFS 推荐候选数据
  -> Hadoop MapReduce 推荐任务
  -> rec_user_movie_topn / recommendation_result
  -> Spring Boot 推荐接口
  -> Vue 前端推荐展示
  -> Movie Agent 生成推荐解释与策略
```

### 6.3 Agent 分析链路

```text
MySQL / MapReduce 结果表
  -> 后端计算结构化参数
  -> MovieAgentController 规则 Agent
  -> summary / insights / strategies / actions / evidence
  -> 前端展示
```

---

## 7. 主要接口清单

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/movie-dashboard` | GET | 电影数据分析与拼团业务看板 |
| `/api/movie-insights` | GET | 电影推荐与分析指标 |
| `/api/movie-agent/analyze` | GET | Agent 分析结论 |
| `/api/movie-agent/context` | GET | Agent 输入参数 |
| `/api/recommendations/users/{userId}` | GET | 指定用户推荐列表 |
| `/actuator/health` | GET | 后端健康检查 |

---

## 8. 前端说明

主前端：

```text
doc/movie-dashboard-vue/
```

核心文件：

```text
doc/movie-dashboard-vue/src/App.vue
doc/movie-dashboard-vue/src/styles.css
doc/movie-dashboard-vue/vite.config.js
```

该前端会动态请求后端接口，不再使用写死数据。页面启动后会请求：

```text
/api/movie-dashboard
/api/movie-insights?limit=6
/api/movie-agent/analyze?limit=6
```

展示内容包括：

- 电影核心指标；
- 评分、类型、地区图表；
- 拼团趋势；
- 推荐与分析 Agent；
- 推荐电影列表；
- Agent 行动建议；
- 标签热度；
- 质量评分电影；
- 用户偏好画像；
- 拼团活动。

---

## 9. 数据库表说明

### 9.1 原始暂存表

```text
stg_movies
stg_persons
stg_users
stg_comments
stg_ratings
```

### 9.2 分析维表与事实表

```text
dim_movie
dim_person
dim_user
dim_genre
dim_language
dim_region
dim_tag
fact_movie_person
fact_comment
fact_rating
bridge_movie_genre
bridge_movie_language
bridge_movie_region
bridge_movie_tag
```

### 9.3 推荐结果表

```text
recommendation_result
rec_user_movie_topn
```

### 9.4 拼团业务表

```text
gb_activity
gb_group_order
gb_group_member
gb_trade_order
gb_group_snapshot
```

---

## 10. 环境要求

### 10.1 后端

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Spring Boot 3.2.5

### 10.2 前端

- Node.js 18+
- npm
- Vue 3
- Vite 5
- ECharts

### 10.3 大数据环境

- Hadoop 3.3.6
- HDFS
- YARN 可选
- Linux 或 Hadoop 可运行环境

---

## 11. 常见问题

### 11.1 Maven 使用了 Java 8 怎么办？

执行：

```bash
mvn -version
```

如果看到：

```text
Java version: 1.8.x
```

需要把 `JAVA_HOME` 切换到 JDK 17，然后重新打开终端。

### 11.2 前端请求接口失败怎么办？

检查：

1. 后端是否启动在 `8080`；
2. 前端 `vite.config.js` 中 `/api` 代理是否指向正确后端；
3. 后端 `application.yml` 数据库连接是否正确；
4. MySQL 中是否存在 `movie_analytics` 数据库和对应表。

### 11.3 看板有图表但没有推荐数据怎么办？

检查：

1. 是否初始化了推荐结果表；
2. 是否执行了 MapReduce 推荐任务；
3. `rec_user_movie_topn` 或 `recommendation_result` 是否有数据；
4. 如果推荐表为空，系统会使用 `dim_movie` 质量分兜底推荐。

### 11.4 MapReduce 脚本无法运行怎么办？

检查：

1. 是否已安装 Hadoop；
2. `hadoop` 命令是否可用；
3. 是否已执行 `mvn -pl mapreduce -am -DskipTests package`；
4. HDFS 目录和 MySQL 连接参数是否正确。

---

## 12. 项目定位总结

本项目以电影数据分析和电影推荐为核心，使用 MySQL 保存结构化数据，使用 Hadoop MapReduce 生成离线推荐结果，使用 Spring Boot 对外提供推荐、分析和 Agent 接口，使用 Vue + ECharts 展示动态可视化看板。

项目重点不是前端静态展示，而是完整打通：

```text
数据库 / MapReduce
  -> 后端计算参数
  -> 推荐接口与分析接口
  -> Agent 生成策略与解释
  -> 前端动态展示
```

因此前端展示值应始终来自后端接口，后端接口应始终从数据库或 MapReduce 结果表计算得到。
