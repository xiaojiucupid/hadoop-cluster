<template>
  <div class="page-shell recommendation-page" v-loading="loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">Recommendation System</p>
        <h2>推荐系统</h2>
        <span>数据源：/api/recommendations/users/{userId}、/api/recommendation-dashboard、/api/movie-insights</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="queryRecommendations">查询推荐</el-button>
    </section>

    <el-card shadow="never" class="data-card">
      <el-form :inline="true" :model="query" class="filter-form" @submit.prevent>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" clearable placeholder="输入用户 ID" @keyup.enter="queryRecommendations" />
        </el-form-item>
        <el-form-item label="算法">
          <el-select v-model="query.algorithm" style="width: 210px">
            <el-option v-for="item in algorithms" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-slider v-model="query.limit" :min="5" :max="30" :step="5" style="width: 220px" />
        </el-form-item>
      </el-form>
    </el-card>

    <section class="recommendation-layout">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Personalized Results</p>
              <h3>推荐结果</h3>
            </div>
            <el-tag>{{ pagedRecommendations.length }}/{{ recommendations.length }}</el-tag>
          </div>
        </template>
        <div v-if="pagedRecommendations.length" class="recommendation-card-grid">
          <article v-for="movie in pagedRecommendations" :key="`${movie.movieId}-${movie.algorithmType}`" class="recommendation-result-card">
            <div class="poster-placeholder small">{{ movie.movieTitle?.slice(0, 1) || '影' }}</div>
            <strong>{{ movie.movieTitle }}</strong>
            <span>{{ movie.algorithmType }} · 推荐分 {{ movie.recommendScore }}</span>
            <small>{{ movie.reason || '推荐理由来自后端算法结果，后续可由 Agent 进一步补充解释。' }}</small>
          </article>
        </div>
        <el-empty v-else description="暂无推荐结果，请输入有效用户 ID 查询" />
        <el-pagination
          v-if="recommendations.length"
          v-model:current-page="page"
          :page-size="pageSize"
          :total="recommendations.length"
          layout="prev, pager, next"
          class="table-pagination"
        />
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Top Samples</p>
              <h3>推荐样例</h3>
            </div>
          </div>
        </template>
        <div class="action-list compact">
          <div v-for="item in topRecommendations" :key="`${item.userKey}-${item.movieName}`" class="action-card">
            <strong>{{ item.movieName }}</strong>
            <span>{{ item.userKey }} · {{ item.score }}</span>
            <small>{{ item.reason }}</small>
          </div>
          <el-empty v-if="!topRecommendations.length" description="暂无推荐样例" />
        </div>
      </el-card>
    </section>

    <section class="dashboard-chart-grid">
      <ChartCard title="各算法推荐数量" eyebrow="Algorithm Count" :option="algorithmCountOption" :empty="!algorithmStats.length" />
      <ChartCard title="算法平均评分" eyebrow="Average Score" :option="algorithmScoreOption" :empty="!algorithmStats.length" />
    </section>

    <el-card shadow="never" class="data-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Pipeline</p>
            <h3>推荐系统流程</h3>
          </div>
          <el-tag type="success">MySQL → HDFS → MapReduce → 推荐表 → API</el-tag>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item v-for="step in pipelineSteps" :key="step.title" :timestamp="step.stage" placement="top">
          <el-card shadow="never" class="timeline-card">
            <strong>{{ step.title }}</strong>
            <p>{{ step.desc }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import ChartCard from '../components/ChartCard.vue';
import { getApiData } from '../services/http';
import { useDashboardStore } from '../stores/dashboard';

const store = useDashboardStore();
const loading = ref(false);
const recommendations = ref([]);
const page = ref(1);
const pageSize = 6;
const query = reactive({ userId: 1, algorithm: 'HYBRID', limit: 10 });
const algorithms = ['HOT', 'TAG_PREFERENCE', 'HYBRID', 'USER_CF', 'ITEM_CF', 'QUALITY_BASED'];

const insights = computed(() => store.insights || {});
const recommendationDashboard = computed(() => store.recommendationDashboard || {});
const algorithmStats = computed(() => insights.value.recommendationMetrics || recommendationDashboard.value.algorithmScores || []);
const topRecommendations = computed(() => recommendationDashboard.value.topRecommendations || []);
const pagedRecommendations = computed(() => recommendations.value.slice((page.value - 1) * pageSize, page.value * pageSize));

const algorithmCountOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{ type: 'pie', radius: ['42%', '72%'], data: algorithmStats.value.map((item) => ({ name: item.algorithm || item.name, value: item.resultCount || item.precision || 0 })) }],
}));

const algorithmScoreOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 54, right: 20, top: 20, bottom: 36 },
  xAxis: { type: 'category', data: algorithmStats.value.map((item) => item.algorithm || item.name) },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: algorithmStats.value.map((item) => Number(item.avgScore || item.precision || 0)), itemStyle: { color: '#6366f1', borderRadius: [8, 8, 0, 0] } }],
}));

const pipelineSteps = [
  { stage: '01', title: 'MySQL 分析表', desc: '读取 dim_movie、fact_rating、bridge_movie_tag 等分析表，形成推荐候选数据。' },
  { stage: '02', title: 'HDFS 候选数据', desc: '通过导出脚本写入 /movie/mysql，为 Hadoop 任务提供输入。' },
  { stage: '03', title: 'MapReduce 推荐计算', desc: '运行 Hot、TagPreference、Hybrid、CF 等推荐任务。' },
  { stage: '04', title: '推荐结果落库', desc: '推荐结果写入 rec_user_movie_topn 或 recommendation_result。' },
  { stage: '05', title: 'API 服务输出', desc: '前端通过 /api/recommendations/users/{userId} 查询推荐结果。' },
];

async function queryRecommendations() {
  if (!query.userId) {
    ElMessage.warning('请输入用户 ID');
    return;
  }
  loading.value = true;
  try {
    recommendations.value = await getApiData(`/recommendations/users/${query.userId}`, {
      params: { algorithm: query.algorithm, limit: query.limit },
    });
    page.value = 1;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([
    store.fetchInsights(false, 8),
    store.fetchRecommendationDashboard(false),
  ]);
  queryRecommendations();
});
</script>
