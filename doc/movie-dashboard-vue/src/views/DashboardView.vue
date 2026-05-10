<template>
  <div class="page-shell dashboard-page" v-loading="store.loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">Overview Dashboard</p>
        <h2>总览仪表盘</h2>
        <span>数据湖 / MapReduce / Agent 驱动的动态电影分析看板</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refresh">刷新数据</el-button>
    </section>

    <section class="metric-grid-v2">
      <MetricCard v-for="item in metrics" :key="item.label" v-bind="item" />
    </section>

    <section class="dashboard-chart-grid">
      <ChartCard class="chart-wide" title="评分分布" eyebrow="Ratings" :option="ratingOption" :empty="!ratingDistribution.length" />
      <ChartCard title="类型 Top10" eyebrow="Genres" :option="genreOption" :empty="!genreRanking.length" />
    </section>

    <section class="dashboard-chart-grid lower">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Regions</p>
              <h3>地区排行榜</h3>
            </div>
          </div>
        </template>
        <el-table :data="regionRanking" height="320" empty-text="暂无地区排行数据" stripe>
          <el-table-column type="index" label="#" width="64" />
          <el-table-column prop="name" label="地区" sortable />
          <el-table-column prop="value" label="电影数" sortable>
            <template #default="{ row }">{{ formatNumber(row.value) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
      <ChartCard title="拼团趋势" eyebrow="Group Buy" :option="groupTrendOption" :empty="!groupTrend.length" />
    </section>

    <el-card shadow="never" class="data-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Hot Movies</p>
            <h3>热门电影</h3>
          </div>
          <el-tag type="success">动态接口数据</el-tag>
        </div>
      </template>
      <div v-if="hotMovies.length" class="hot-movie-scroll">
        <article v-for="movie in hotMovies" :key="movie.id" class="hot-movie-card">
          <div class="poster-placeholder">{{ movie.name?.slice(0, 1) }}</div>
          <strong>{{ movie.name }}</strong>
          <span>评分 {{ movie.score }} · 投票 {{ formatNumber(movie.votes) }}</span>
          <el-tag size="small">{{ movie.genre || '未分类' }}</el-tag>
        </article>
      </div>
      <el-empty v-else description="暂无热门电影数据" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import ChartCard from '../components/ChartCard.vue';
import MetricCard from '../components/MetricCard.vue';
import { fallbackDashboard } from '../fallbackData';
import { mergeWithFallback } from '../services/http';
import { useDashboardStore } from '../stores/dashboard';

const store = useDashboardStore();
let refreshTimer = null;

const dashboard = computed(() => mergeWithFallback(store.dashboard, fallbackDashboard));
const summary = computed(() => dashboard.value.dashboardSummary || {});
const ratingDistribution = computed(() => dashboard.value.ratingDistribution || []);
const genreRanking = computed(() => (dashboard.value.genreRanking || []).slice(0, 10));
const regionRanking = computed(() => dashboard.value.regionRanking || []);
const groupTrend = computed(() => dashboard.value.groupTrend || []);
const hotMovies = computed(() => dashboard.value.hotMovies || []);

const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value ?? 0));

const metrics = computed(() => [
  { label: '电影总数', value: Number(summary.value.movieCount ?? 0), icon: 'Film', tips: 'dim_movie', growth: '+6.8%' },
  { label: '用户总数', value: Number(summary.value.userCount ?? 0), icon: 'User', tips: 'dim_user', growth: '+4.1%', iconBg: 'rgba(20, 184, 166, 0.13)', iconColor: '#0f766e' },
  { label: '评分总数', value: Number(summary.value.ratingCount ?? 0), icon: 'Star', tips: 'fact_rating', growth: '+12.5%', iconBg: 'rgba(245, 158, 11, 0.16)', iconColor: '#b45309' },
  { label: '评论总数', value: Number(summary.value.commentCount ?? 0), icon: 'ChatDotRound', tips: 'fact_comment', growth: '+9.7%', iconBg: 'rgba(14, 165, 233, 0.14)', iconColor: '#0369a1' },
  { label: '拼团营收', value: Number(summary.value.groupRevenue ?? 0), icon: 'Money', tips: 'gb_trade_order', growth: '+18.2%', iconBg: 'rgba(239, 68, 68, 0.12)', iconColor: '#b91c1c', prefix: '¥', decimals: 2 },
  { label: '成团率', value: Number(summary.value.successRate ?? 0), icon: 'TrendCharts', tips: 'gb_group_snapshot', growth: '+3.4%', iconBg: 'rgba(124, 58, 237, 0.14)', iconColor: '#6d28d9', suffix: '%', decimals: 1 },
]);

const ratingOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 42, right: 20, top: 28, bottom: 32 },
  xAxis: { type: 'category', data: ratingDistribution.value.map((item) => item.name) },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: ratingDistribution.value.map((item) => item.value), itemStyle: { color: '#6366f1', borderRadius: [8, 8, 0, 0] } }],
}));

const genreOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 70, right: 20, top: 18, bottom: 28 },
  xAxis: { type: 'value' },
  yAxis: { type: 'category', data: genreRanking.value.map((item) => item.name).reverse() },
  series: [{ type: 'bar', data: genreRanking.value.map((item) => item.value).reverse(), itemStyle: { color: '#14b8a6', borderRadius: [0, 8, 8, 0] } }],
}));

const groupTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 0 },
  grid: { left: 42, right: 20, top: 42, bottom: 32 },
  xAxis: { type: 'category', data: groupTrend.value.map((item) => item.date) },
  yAxis: { type: 'value' },
  series: [
    { name: '开团数', type: 'line', smooth: true, data: groupTrend.value.map((item) => item.groups), lineStyle: { width: 3, color: '#6366f1' } },
    { name: '成团数', type: 'line', smooth: true, data: groupTrend.value.map((item) => item.success), lineStyle: { width: 3, color: '#10b981' } },
    { name: '支付金额/千', type: 'bar', data: groupTrend.value.map((item) => Math.round(Number(item.amount ?? 0) / 1000)), itemStyle: { color: '#f97316', borderRadius: [8, 8, 0, 0] } },
  ],
}));

async function refresh() {
  await store.fetchDashboard(true);
}

onMounted(() => {
  store.fetchDashboard();
  refreshTimer = window.setInterval(() => {
    store.fetchDashboard(true);
  }, 30000);
});

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
});
</script>
