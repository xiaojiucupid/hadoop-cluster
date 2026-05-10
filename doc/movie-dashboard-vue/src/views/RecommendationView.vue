<template>
  <div class="page-shell recommendation-page" v-loading="loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">Recommendation System</p>
        <h2>推荐系统</h2>
        <span>按用户画像、推荐算法和返回数量生成差异化个性推荐结果。</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="queryRecommendations">生成推荐结果</el-button>
    </section>

    <el-card shadow="never" class="data-card">
      <el-form :inline="true" :model="query" class="filter-form" @submit.prevent>
        <el-form-item label="用户标识">
          <el-select v-model="query.userId" style="width: 280px" @change="queryRecommendations">
            <el-option v-for="item in userOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="算法">
          <el-select v-model="query.algorithm" style="width: 250px" @change="queryRecommendations">
            <el-option v-for="item in algorithmOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-select v-model="query.limit" style="width: 140px" @change="queryRecommendations">
            <el-option :value="5" label="5 条" />
            <el-option :value="10" label="10 条" />
            <el-option :value="15" label="15 条" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <section class="metric-grid-v2">
      <MetricCard label="当前用户" :value="`用户 ${query.userId}`" icon="User" :tips="currentUser.segment" growth="画像匹配" />
      <MetricCard label="推荐算法" :value="query.algorithm" icon="MagicStick" :tips="currentAlgorithm.subtitle" growth="已选择" />
      <MetricCard label="推荐数量" :value="recommendations.length" icon="DataBoard" tips="TopN 推荐结果" growth="实时生成" />
      <MetricCard label="平均推荐分" :value="combinationSummary.avgScore" icon="TrendCharts" tips="综合排序结果" growth="高匹配" decimals="1" />
    </section>

    <el-card shadow="never" class="data-card hero-summary-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Combination Analysis</p>
            <h3>{{ combinationSummary.title }}</h3>
          </div>
          <el-tag type="success">{{ combinationSummary.firstMovie }}</el-tag>
        </div>
      </template>
      <p class="agent-summary-text">{{ combinationSummary.desc }}</p>
      <div class="source-status-list">
        <div class="source-status-item"><el-badge is-dot type="success" /><span>用户画像：{{ currentUser.profile }}</span></div>
        <div class="source-status-item"><el-badge is-dot type="success" /><span>近期行为：{{ currentUser.behavior }}</span></div>
        <div class="source-status-item"><el-badge is-dot type="success" /><span>标签覆盖：{{ combinationSummary.coverage }}</span></div>
      </div>
    </el-card>

    <section class="recommendation-layout">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Personalized Results</p>
              <h3>推荐结果</h3>
            </div>
            <el-tag>{{ recommendations.length }} 条</el-tag>
          </div>
        </template>
        <div class="recommendation-card-grid rich-grid">
          <article v-for="movie in recommendations" :key="`${movie.movieId}-${movie.algorithmType}-${query.userId}`" class="recommendation-result-card clickable rich-card" @click="goMovieDetail(movie)">
            <div class="rank-badge">#{{ movie.rank }}</div>
            <strong>{{ movie.movieTitle }}</strong>
            <span>{{ movie.year }} · {{ movie.genre }}</span>
            <small>{{ movie.reason }}</small>
            <div class="tag-row mini-tags">
              <el-tag v-for="tag in movie.tags.slice(0, 4)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
            <em>推荐分 {{ movie.recommendScore }}</em>
          </article>
        </div>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Algorithm Profile</p>
              <h3>{{ currentAlgorithm.name }}</h3>
            </div>
            <el-button text type="primary" @click="goAlgorithmDetail(query.algorithm)">参数详情</el-button>
          </div>
        </template>
        <p class="panel-note">{{ currentAlgorithm.goal }}</p>
        <pre class="json-panel formula-panel">{{ currentAlgorithm.formula }}</pre>
        <div class="action-list compact">
          <div v-for="param in currentAlgorithm.parameters" :key="param.key" class="action-card">
            <strong>{{ param.key }}</strong>
            <span>{{ param.value }}</span>
            <small>{{ param.desc }}</small>
          </div>
        </div>
      </el-card>
    </section>

    <section class="dashboard-chart-grid">
      <ChartCard title="推荐分布" eyebrow="Score Distribution" :option="scoreOption" :empty="!recommendations.length" />
      <ChartCard title="标签覆盖" eyebrow="Tag Coverage" :option="tagOption" :empty="!tagRows.length" />
    </section>

    <el-card shadow="never" class="data-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Recommendation Pipeline</p>
            <h3>当前组合生成链路</h3>
          </div>
          <el-tag type="success">{{ query.algorithm }} × 用户 {{ query.userId }}</el-tag>
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
import { useRouter } from 'vue-router';
import { Refresh } from '@element-plus/icons-vue';
import ChartCard from '../components/ChartCard.vue';
import MetricCard from '../components/MetricCard.vue';
import {
  algorithmOptions,
  buildCombinationSummary,
  buildStaticRecommendations,
  findAlgorithm,
  getUserProfile,
  userOptions,
} from '../staticRecommendationData';

const router = useRouter();
const loading = ref(false);
const recommendations = ref([]);
const query = reactive({ userId: 1, algorithm: 'HYBRID', limit: 10 });

const currentUser = computed(() => getUserProfile(query.userId));
const currentAlgorithm = computed(() => findAlgorithm(query.algorithm));
const combinationSummary = computed(() => buildCombinationSummary(query.userId, query.algorithm, query.limit));

const tagRows = computed(() => {
  const map = new Map();
  recommendations.value.forEach((movie) => {
    movie.tags.forEach((tag) => map.set(tag, (map.get(tag) || 0) + 1));
  });
  return Array.from(map, ([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value).slice(0, 10);
});

const scoreOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 46, right: 22, top: 32, bottom: 72 },
  xAxis: { type: 'category', data: recommendations.value.map((item) => item.movieTitle), axisLabel: { rotate: 35 } },
  yAxis: { type: 'value', min: 70, max: 100 },
  series: [{ type: 'bar', data: recommendations.value.map((item) => item.recommendScore), itemStyle: { color: '#6366f1', borderRadius: [8, 8, 0, 0] } }],
}));

const tagOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{ type: 'pie', radius: ['38%', '72%'], data: tagRows.value, color: ['#6366f1', '#14b8a6', '#f97316', '#ec4899', '#84cc16', '#06b6d4', '#8b5cf6'] }],
}));

const pipelineSteps = computed(() => [
  { stage: '01', title: '读取用户画像', desc: `识别用户 ${query.userId} 为“${currentUser.value.segment}”，核心偏好为 ${currentUser.value.tags.join('、')}。` },
  { stage: '02', title: '选择算法策略', desc: `当前使用 ${currentAlgorithm.value.name}，排序目标是：${currentAlgorithm.value.goal}` },
  { stage: '03', title: '生成候选电影池', desc: `根据用户偏好和算法策略生成 ${query.limit} 条候选结果，优先覆盖 ${combinationSummary.value.coverage}。` },
  { stage: '04', title: '计算推荐分', desc: `对候选电影执行标签匹配、质量分、热度分和用户行为加权，平均分为 ${combinationSummary.value.avgScore}。` },
  { stage: '05', title: '输出可解释结果', desc: '每部影片生成独立推荐理由，可点击进入详情页查看电影介绍与分析。' },
]);

function queryRecommendations() {
  loading.value = true;
  recommendations.value = buildStaticRecommendations(query.userId, query.algorithm, query.limit);
  window.setTimeout(() => {
    loading.value = false;
  }, 120);
}

function goMovieDetail(movie) {
  router.push({ path: `/recommendation/movies/${movie.movieId}`, query: { algorithm: query.algorithm, userId: query.userId, limit: query.limit } });
}

function goAlgorithmDetail(algorithm) {
  router.push(`/recommendation/algorithms/${algorithm}`);
}

onMounted(queryRecommendations);
</script>
