# 电影分析与拼团数据看板 Vue 前端

这是放在 `doc` 目录下的 Vue + Vite 前端原型，用于展示 `movie_analytics` 数据库中的电影分析、评分评论和拼团业务指标。

## 运行方式

```bash
cd doc/movie-dashboard-vue
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

## 构建

```bash
npm run build
npm run preview
```

## 目录说明

```text
movie-dashboard-vue
├── index.html
├── package.json
├── vite.config.js
└── src
    ├── App.vue
    ├── main.js
    ├── mockData.js
    └── styles.css
```

## 后端接口建议

当前页面使用 `src/mockData.js` 中的模拟数据。后续如果接 Java/Spring Boot 后端，可以提供以下接口并替换 mock 数据：

- `GET /api/dashboard/summary`：核心指标
- `GET /api/movies/hot`：热门电影排行
- `GET /api/ratings/distribution`：评分分布
- `GET /api/genres/ranking`：电影类型排行
- `GET /api/regions/ranking`：地区分布
- `GET /api/group-buy/trend`：拼团趋势
- `GET /api/group-buy/activities`：拼团活动列表

`vite.config.js` 已配置 `/api` 代理到 `http://localhost:8080`。
