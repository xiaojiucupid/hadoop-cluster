# Movie Analytics Vue Frontend

这是放在 `doc` 目录下的 Vue 3 + Vite 前端示例，用于展示电影分析和拼团运营数据。

## 功能页面

- 推荐系统总览指标：覆盖用户、离线召回电影、推荐点击率、MapReduce 作业数
- ECharts 推荐算法多指标雷达图：Precision、Recall、Coverage、Latency
- ECharts 推荐召回漏斗：全量电影池、质量过滤、协同召回、标签召回、混合排序、TopN 推荐
- ECharts 用户标签偏好权重：基于 `bridge_movie_tag`、`fact_rating`、`fact_comment` 的用户画像
- ECharts Hadoop/MapReduce 推荐链路图：从 `stg_*`、`fact_*`、`bridge_*` 到 `rec_user_movie_topn`
- 推荐效果周趋势：ItemCF、TagPreference、HybridRank 对比
- Hadoop 推荐作业产出列表
- 个性化 TopN 推荐结果样例
- 拼团场景推荐运营联动

当前使用 `src/data/mockDashboard.js` 和 `src/data/recommendationDashboard.js` 中的模拟数据，后续可以替换为后端接口。

## 启动方式

```bash
cd doc/movie-analytics-vue
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

## 后端接口代理

`vite.config.js` 已配置 `/api` 代理到：

```text
http://localhost:8080
```

如果你的 Java 后端端口不是 `8080`，修改 `vite.config.js` 中的 `target` 即可。

## 建议后端接口

后续可以让 Java 后端提供这些接口：

- `GET /api/recommendation/overview`
- `GET /api/recommendation/algorithm-metrics`
- `GET /api/recommendation/recall-funnel`
- `GET /api/recommendation/tag-preferences?userMd5=xxx`
- `GET /api/recommendation/topn?userMd5=xxx`
- `GET /api/recommendation/hadoop/jobs`
- `GET /api/group-buy/activities/recommended`

这些接口可以基于 `doc/mysql` 中创建的 `dim_*`、`fact_*`、`bridge_*`、`gb_*` 表，以及 Hadoop 离线任务输出的 `rec_*` 结果表查询。

## Hadoop 推荐算法建议

可以在 Hadoop 层补充这些离线任务：

1. `MovieRatingNormalizeJob`：从 `stg_ratings` 或 `fact_rating` 生成用户-电影评分矩阵。
2. `MovieSimilarityJob`：用 ItemCF 计算电影之间的共现相似度，输出 `rec_movie_similarity`。
3. `UserPreferenceVectorJob`：结合评分、评论、电影标签、类型、地区，生成 `rec_user_profile`。
4. `TagPreferenceRecommendationJob`：基于用户标签偏好召回候选电影，输出 `rec_tag_preference_candidate`。
5. `HybridRankRecommendationJob`：融合 ItemCF、标签偏好、电影热度、拼团活动权重，输出 `rec_user_movie_topn`。

前端当前的 ECharts 看板就是围绕这些推荐任务和结果表设计的。
