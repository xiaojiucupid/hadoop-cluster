export const recommendationCards = [
  { label: '推荐覆盖用户', value: '72,418', trend: '+16.8%', tone: 'blue' },
  { label: '离线召回电影', value: '418,620', trend: '+21.4%', tone: 'purple' },
  { label: '推荐点击率', value: '13.7%', trend: '+2.9%', tone: 'green' },
  { label: 'MapReduce 作业', value: '8', trend: 'T+1 稳定', tone: 'orange' },
];

export const algorithmScores = [
  { name: 'UserCF', precision: 0.71, recall: 0.64, coverage: 0.58, latency: 0.82 },
  { name: 'ItemCF', precision: 0.78, recall: 0.69, coverage: 0.62, latency: 0.88 },
  { name: 'TagPreference', precision: 0.74, recall: 0.73, coverage: 0.81, latency: 0.91 },
  { name: 'HybridRank', precision: 0.83, recall: 0.77, coverage: 0.76, latency: 0.79 },
];

export const hadoopJobs = [
  { name: 'HotMovieRecommendationJob', input: 'dim_movie + fact_rating', output: 'rec_hot_movie_topn', duration: '6m 38s', status: 'SUCCESS', records: '50,000' },
  { name: 'QualityBasedRecommendationJob', input: 'dim_movie + fact_comment', output: 'rec_quality_movie_topn', duration: '7m 54s', status: 'SUCCESS', records: '68,420' },
  { name: 'TagPreferenceRecommendationJob', input: 'fact_rating + bridge_movie_tag', output: 'rec_tag_preference_topn', duration: '13m 05s', status: 'SUCCESS', records: '1,448,360' },
  { name: 'HybridRecommendationJob', input: 'hot + quality + tag', output: 'rec_user_movie_topn', duration: '17m 22s', status: 'SUCCESS', records: '1,448,360' },
];

export const topRecommendations = [
  { user: 'u_03a9...c8f1', movie: '星际穿越', reason: '科幻/剧情偏好 + 高评分相似用户', score: 98.7 },
  { user: 'u_7bc1...10a2', movie: '盗梦空间', reason: 'ItemCF 相似电影召回', score: 96.4 },
  { user: 'u_e922...77bd', movie: '看不见的客人', reason: '悬疑标签权重提升', score: 94.8 },
  { user: 'u_51dd...a30f', movie: '寻梦环游记', reason: '家庭/动画场景化推荐', score: 92.5 },
  { user: 'u_64ab...89e3', movie: '疯狂动物城', reason: '近邻用户共同高分电影', score: 90.9 },
];

export const tagPreferenceData = [
  { tag: '剧情', weight: 96 },
  { tag: '科幻', weight: 88 },
  { tag: '悬疑', weight: 82 },
  { tag: '犯罪', weight: 74 },
  { tag: '动画', weight: 68 },
  { tag: '爱情', weight: 62 },
  { tag: '冒险', weight: 58 },
  { tag: '喜剧', weight: 54 },
];

export const recallFunnel = [
  { stage: '全量电影池', value: 12438 },
  { stage: '质量过滤', value: 8350 },
  { stage: '协同召回', value: 3200 },
  { stage: '标签召回', value: 2600 },
  { stage: '混合排序', value: 500 },
  { stage: 'TopN 推荐', value: 50 },
];

export const mapReduceFlow = [
  { source: 'stg_ratings', target: '评分归一化' },
  { source: 'stg_movies', target: '电影画像' },
  { source: 'bridge_movie_tag', target: '电影画像' },
  { source: '评分归一化', target: 'ItemCF 相似度' },
  { source: '评分归一化', target: '用户偏好向量' },
  { source: '电影画像', target: '用户偏好向量' },
  { source: 'ItemCF 相似度', target: '候选召回' },
  { source: '用户偏好向量', target: '候选召回' },
  { source: '候选召回', target: '混合排序' },
  { source: '混合排序', target: 'rec_user_movie_topn' },
];

export const precisionTrend = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'].map((day, index) => ({
  day,
  itemCf: [68, 69, 72, 73, 74, 76, 78][index],
  tagPreference: [64, 66, 69, 71, 72, 73, 74][index],
  hybrid: [72, 74, 76, 78, 79, 81, 83][index],
}));

export const algorithmPipeline = [
  { step: '01', title: '热度召回', job: 'HotMovieRecommendationJob', desc: '按评分人数、平均分、评论数构建默认候选池，解决冷启动用户无历史行为问题。', output: 'rec_hot_movie_topn' },
  { step: '02', title: '质量召回', job: 'QualityBasedRecommendationJob', desc: '融合豆瓣分、有效评论、上映年份和电影时长，筛出稳定高质量影片。', output: 'rec_quality_movie_topn' },
  { step: '03', title: '标签偏好', job: 'TagPreferenceRecommendationJob', desc: '从用户高分电影抽取标签向量，按标签重合度和候选电影热度进行个性化召回。', output: 'rec_tag_preference_topn' },
  { step: '04', title: '混合排序', job: 'HybridRecommendationJob', desc: '合并热度、质量、标签偏好分，加入去重、已看过滤、TopN 截断，产出最终推荐。', output: 'rec_user_movie_topn' },
];

export const hdfsLayers = [
  { name: 'ODS 原始层', path: '/movie/ods', files: 'movies / ratings / comments', size: '8.6GB' },
  { name: 'DW 明细层', path: '/movie/dw', files: 'user_movie_score / movie_profile', size: '4.1GB' },
  { name: 'REC 算法层', path: '/movie/rec', files: 'candidate / similarity / topn', size: '2.7GB' },
  { name: 'MySQL 服务层', path: 'movie_analytics', files: 'rec_user_movie_topn', size: '144万行' },
];

export const featureWeights = [
  { feature: '标签匹配', hot: 0.12, quality: 0.08, tag: 0.42, hybrid: 0.3 },
  { feature: '历史评分', hot: 0.16, quality: 0.18, tag: 0.24, hybrid: 0.24 },
  { feature: '电影热度', hot: 0.46, quality: 0.18, tag: 0.12, hybrid: 0.2 },
  { feature: '内容质量', hot: 0.14, quality: 0.42, tag: 0.1, hybrid: 0.18 },
  { feature: '时间衰减', hot: 0.12, quality: 0.14, tag: 0.12, hybrid: 0.08 },
];

export const recommendationSegments = [
  { name: '冷启动用户', value: 28, strategy: '热度 + 质量推荐' },
  { name: '轻度评分用户', value: 34, strategy: '标签偏好扩展' },
  { name: '深度评分用户', value: 26, strategy: '个性化混合排序' },
  { name: '长尾兴趣用户', value: 12, strategy: '覆盖率探索推荐' },
];
