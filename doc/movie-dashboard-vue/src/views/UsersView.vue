<template>
  <div class="page-shell users-page" v-loading="store.loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">User Profiles</p>
        <h2>用户画像</h2>
        <span>当前使用 movie-insights 用户偏好画像聚合展示，预留 /api/users/search 接口扩展。</span>
      </div>
      <el-input v-model="keyword" clearable placeholder="搜索用户（当前为前端筛选）" style="width: 280px" />
    </section>

    <section class="metric-grid-v2 user-summary-grid">
      <MetricCard label="用户总数" :value="formatNumber(summary.userCount)" icon="User" tips="movie-dashboard.dashboardSummary" growth="+4.1%" />
      <MetricCard label="画像用户" :value="formatNumber(userProfiles.length)" icon="UserFilled" tips="movie-insights.userPreferences" growth="样例" />
      <MetricCard label="评分总数" :value="formatNumber(insights.overview?.ratingCount)" icon="Star" tips="fact_rating" growth="+12.5%" />
      <MetricCard label="平均评分" :value="Number(insights.overview?.avgRating ?? 0).toFixed(2)" icon="TrendCharts" tips="用户平均评分" growth="+2.1%" />
    </section>

    <section class="user-card-grid">
      <el-card v-for="user in filteredUsers" :key="user.userKey" shadow="never" class="user-card" @click="goDetail(user)">
        <div class="user-avatar">{{ user.userKey.slice(2, 4).toUpperCase() }}</div>
        <strong>{{ user.userKey }}</strong>
        <span>{{ user.segment }} · 偏好 {{ user.favoriteTag }}</span>
        <el-progress :percentage="Math.min(Number(user.avgRating || 0) * 20, 100)" />
        <small>评分 {{ formatNumber(user.ratingCount) }} 条 · 平均 {{ user.avgRating }}</small>
      </el-card>
      <el-empty v-if="!filteredUsers.length" description="暂无用户画像数据" />
    </section>

    <section class="dashboard-chart-grid">
      <ChartCard title="用户分层分布" eyebrow="Segments" :option="segmentOption" :empty="!userProfiles.length" />
      <ChartCard title="偏好标签分布" eyebrow="Preference Tags" :option="tagOption" :empty="!userProfiles.length" />
    </section>

    <el-alert
      title="扩展说明"
      type="info"
      show-icon
      :closable="false"
      description="用户搜索、用户分页列表、评分历史目前后端暂未提供独立接口。后续建议新增 /api/users/search、/api/users/{id}/ratings、/api/users/{id}/profile。"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import ChartCard from '../components/ChartCard.vue';
import MetricCard from '../components/MetricCard.vue';
import { useDashboardStore } from '../stores/dashboard';

const router = useRouter();
const store = useDashboardStore();
const keyword = ref('');
const dashboard = computed(() => store.dashboard || {});
const insights = computed(() => store.insights || {});
const summary = computed(() => dashboard.value.dashboardSummary || {});
const userProfiles = computed(() => insights.value.userPreferences || []);
const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value ?? 0));

const filteredUsers = computed(() => {
  if (!keyword.value) return userProfiles.value;
  return userProfiles.value.filter((user) => `${user.userKey}${user.favoriteTag}${user.segment}`.includes(keyword.value));
});

const segmentOption = computed(() => {
  const map = new Map();
  userProfiles.value.forEach((user) => map.set(user.segment, (map.get(user.segment) || 0) + 1));
  return {
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: ['40%', '72%'], data: Array.from(map, ([name, value]) => ({ name, value })) }],
  };
});

const tagOption = computed(() => {
  const map = new Map();
  userProfiles.value.forEach((user) => map.set(user.favoriteTag, (map.get(user.favoriteTag) || 0) + 1));
  const rows = Array.from(map, ([name, value]) => ({ name, value }));
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 70, right: 18, top: 20, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: rows.map((item) => item.name).reverse() },
    series: [{ type: 'bar', data: rows.map((item) => item.value).reverse(), itemStyle: { color: '#14b8a6', borderRadius: [0, 8, 8, 0] } }],
  };
});

function goDetail(user) {
  const fallbackId = Number(String(user.userKey).replace(/\D/g, '').slice(0, 6)) || 1;
  router.push(`/users/${fallbackId}`);
}

onMounted(() => {
  store.fetchDashboard();
  store.fetchInsights(false, 12);
});
</script>
