<template>
  <div class="page-shell insights-page" v-loading="store.loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">Movie Insights</p>
        <h2>电影洞察</h2>
        <span>数据来源：GET /api/movie-insights?limit=6</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refresh">刷新洞察</el-button>
    </section>

    <section class="metric-grid-v2 insight-overview-grid">
      <MetricCard v-for="item in overviewMetrics" :key="item.label" v-bind="item" />
    </section>

    <section class="dashboard-chart-grid">
      <ChartCard title="标签热度" eyebrow="Tag Heat" :option="tagHeatOption" :empty="!genreHeat.length" />
      <ChartCard title="地区热度" eyebrow="Region Heat" :option="regionHeatOption" :empty="!regionHeat.length" />
    </section>

    <section class="dashboard-chart-grid lower">
      <ChartCard title="评分区间分布" eyebrow="Score Intervals" :option="scoreBucketOption" :empty="!scoreBuckets.length" />
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Quality Top</p>
              <h3>质量评分 Top 电影</h3>
            </div>
          </div>
        </template>
        <el-table :data="qualityMovies" height="320" stripe empty-text="暂无质量评分数据">
          <el-table-column type="index" label="#" width="56" />
          <el-table-column prop="name" label="电影" min-width="160" show-overflow-tooltip />
          <el-table-column prop="qualityScore" label="质量分" sortable />
          <el-table-column prop="score" label="豆瓣" sortable />
          <el-table-column prop="comments" label="评论" sortable>
            <template #default="{ row }">{{ formatNumber(row.comments) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>

    <section class="analysis-detail-grid">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">User Profiles</p>
              <h3>用户偏好画像</h3>
            </div>
          </div>
        </template>
        <div v-if="userPreferences.length" class="profile-grid">
          <article v-for="user in userPreferences.slice(0, 3)" :key="user.userKey" class="profile-card">
            <strong>{{ user.userKey }}</strong>
            <span>{{ user.segment }}</span>
            <el-progress :percentage="Math.min(Number(user.avgRating || 0) * 20, 100)" />
            <small>偏好 {{ user.favoriteTag }} · 平均评分 {{ user.avgRating }} · {{ formatNumber(user.ratingCount) }} 条</small>
          </article>
        </div>
        <el-empty v-else description="暂无用户画像数据" />
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Data Sources</p>
              <h3>数据源接入状态</h3>
            </div>
          </div>
        </template>
        <div class="source-status-list">
          <div v-for="source in dataSources" :key="source" class="source-status-item">
            <el-badge is-dot type="success" />
            <span>{{ source }}</span>
          </div>
          <div class="source-status-item muted">
            <el-badge is-dot type="warning" />
            <span>HDFS 状态：后端暂未提供独立 HDFS 健康接口，后续建议新增 /api/data-source/health。</span>
          </div>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import ChartCard from '../components/ChartCard.vue';
import MetricCard from '../components/MetricCard.vue';
import { useDashboardStore } from '../stores/dashboard';

const store = useDashboardStore();
const insights = computed(() => store.insights || {});
const overview = computed(() => insights.value.overview || {});
const genreHeat = computed(() => insights.value.genreHeat || []);
const regionHeat = computed(() => insights.value.regionHeat || []);
const scoreBuckets = computed(() => insights.value.scoreBuckets || []);
const qualityMovies = computed(() => insights.value.qualityMovies || []);
const userPreferences = computed(() => insights.value.userPreferences || []);
const dataSources = computed(() => insights.value.dataSources || []);

const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value ?? 0));

const overviewMetrics = computed(() => [
  { label: '电影总数', value: Number(overview.value.movieCount ?? 0), icon: 'Film', tips: 'dim_movie', growth: '+6.8%' },
  { label: '评分记录', value: Number(overview.value.ratingCount ?? 0), icon: 'Star', tips: 'fact_rating', growth: '+12.5%' },
  { label: '评论记录', value: Number(overview.value.commentCount ?? 0), icon: 'ChatDotRound', tips: 'fact_comment', growth: '+9.7%' },
  { label: '平均电影分', value: Number(overview.value.avgScore ?? 0), icon: 'Medal', tips: 'douban_score', growth: '+1.4%', decimals: 2 },
  { label: '平均用户评分', value: Number(overview.value.avgRating ?? 0), icon: 'UserFilled', tips: 'rating', growth: '+2.1%', decimals: 2 },
  { label: '推荐结果数', value: Number(overview.value.recommendationCount ?? 0), icon: 'Connection', tips: 'MapReduce result', growth: '+15.6%' },
]);

const tagHeatOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 72, right: 18, top: 18, bottom: 30 },
  xAxis: { type: 'value' },
  yAxis: { type: 'category', data: genreHeat.value.map((item) => item.name).reverse() },
  series: [{ type: 'bar', data: genreHeat.value.map((item) => item.value).reverse(), itemStyle: { color: '#8b5cf6', borderRadius: [0, 8, 8, 0] } }],
}));

const regionHeatOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{ type: 'pie', roseType: 'radius', radius: ['28%', '76%'], data: regionHeat.value, color: ['#06b6d4', '#6366f1', '#84cc16', '#f97316', '#ec4899', '#64748b'] }],
}));

const scoreBucketOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 0 },
  grid: { left: 44, right: 18, top: 44, bottom: 30 },
  xAxis: { type: 'category', data: scoreBuckets.value.map((item) => item.bucket) },
  yAxis: { type: 'value' },
  series: [
    { name: '电影数', type: 'bar', stack: 'total', data: scoreBuckets.value.map((item) => item.movieCount), itemStyle: { color: '#0ea5e9' } },
    { name: '评分数', type: 'bar', stack: 'total', data: scoreBuckets.value.map((item) => item.ratingCount), itemStyle: { color: '#f97316' } },
  ],
}));

async function refresh() {
  await store.fetchInsights(true, 6);
}

onMounted(() => {
  store.fetchInsights(false, 6);
});
</script>
