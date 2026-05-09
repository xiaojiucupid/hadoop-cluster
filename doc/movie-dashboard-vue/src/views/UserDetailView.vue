<template>
  <div class="page-shell user-detail-page" v-loading="loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">User Detail</p>
        <h2>用户详情</h2>
        <span>当前基于 movie-insights 与 /api/recommendations/users/{id} 聚合展示，评分历史接口待后端补充。</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadData">刷新用户详情</el-button>
    </section>

    <section class="dashboard-chart-grid lower">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Profile</p>
              <h3>用户基本信息</h3>
            </div>
          </div>
        </template>
        <div class="profile-card detail">
          <div class="user-avatar large">{{ displayUser.userKey?.slice(2, 3) || 'U' }}</div>
          <strong>{{ displayUser.userKey || `u_${route.params.id}` }}</strong>
          <span>{{ displayUser.segment || '画像生成用户' }}</span>
          <small>偏好 {{ displayUser.favoriteTag || '综合' }} · 平均评分 {{ displayUser.avgRating || 0 }} · 评分 {{ formatNumber(displayUser.ratingCount) }} 条</small>
        </div>
      </el-card>

      <ChartCard title="偏好标签雷达图" eyebrow="Preference Radar" :option="radarOption" :empty="!radarValues.length" />
    </section>

    <section class="dashboard-chart-grid lower">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Rating History</p>
              <h3>评分历史</h3>
            </div>
          </div>
        </template>
        <el-table :data="mockRatings" stripe height="320">
          <el-table-column prop="movie" label="电影" min-width="180" />
          <el-table-column prop="tag" label="标签" min-width="120" />
          <el-table-column prop="score" label="评分" width="100" />
          <el-table-column prop="time" label="时间" min-width="140" />
        </el-table>
        <p class="panel-note">当前后端缺少 `/api/users/{id}/ratings`，此处根据用户偏好和推荐结果生成演示列表。</p>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Recommendations</p>
              <h3>推荐电影列表</h3>
            </div>
          </div>
        </template>
        <div class="recommendation-card-grid">
          <article v-for="movie in recommendations" :key="`${movie.movieId}-${movie.algorithmType}`" class="recommendation-result-card">
            <div class="poster-placeholder small">{{ movie.movieTitle?.slice(0, 1) || '影' }}</div>
            <strong>{{ movie.movieTitle }}</strong>
            <span>{{ movie.algorithmType }} · 推荐分 {{ movie.recommendScore }}</span>
            <small>{{ movie.reason || '推荐理由由后端推荐算法返回。' }}</small>
          </article>
        </div>
        <el-empty v-if="!recommendations.length" description="暂无推荐电影" />
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { Refresh } from '@element-plus/icons-vue';
import ChartCard from '../components/ChartCard.vue';
import { getApiData } from '../services/http';
import { useDashboardStore } from '../stores/dashboard';

const route = useRoute();
const store = useDashboardStore();
const loading = ref(false);
const recommendations = ref([]);

const userPreferences = computed(() => store.insights?.userPreferences || []);
const displayUser = computed(() => userPreferences.value.find((item, index) => String(index + 1) === String(route.params.id)) || userPreferences.value[0] || {});
const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value ?? 0));

const radarValues = computed(() => {
  const favorite = Number(displayUser.value.avgRating || 0) * 20;
  return [favorite, Math.min(Number(displayUser.value.ratingCount || 0) / 2, 100), 68, 76, 82];
});

const radarOption = computed(() => ({
  tooltip: {},
  radar: {
    indicator: [
      { name: '评分热度', max: 100 },
      { name: '活跃度', max: 100 },
      { name: '剧情偏好', max: 100 },
      { name: '类型丰富度', max: 100 },
      { name: '推荐匹配度', max: 100 },
    ],
  },
  series: [{ type: 'radar', data: [{ value: radarValues.value, name: displayUser.value.userKey || '用户画像' }], areaStyle: { color: 'rgba(99, 102, 241, 0.25)' }, lineStyle: { color: '#6366f1' } }],
}));

const mockRatings = computed(() => {
  const tag = displayUser.value.favoriteTag || '综合';
  return [
    { movie: `偏好影片 A (${tag})`, tag, score: displayUser.value.avgRating || 4.2, time: '2026-05-01' },
    { movie: `偏好影片 B (${tag})`, tag, score: displayUser.value.avgRating || 4.0, time: '2026-04-28' },
    { movie: `偏好影片 C (${tag})`, tag, score: Math.max(Number(displayUser.value.avgRating || 4.1) - 0.2, 3.5), time: '2026-04-15' },
  ];
});

async function loadData() {
  loading.value = true;
  try {
    await store.fetchInsights(false, 12);
    recommendations.value = await getApiData(`/recommendations/users/${route.params.id}`, {
      params: { algorithm: 'HYBRID', limit: 10 },
    });
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>
