<template>
  <div class="page-shell pipeline-page" v-loading="loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">Data Pipeline</p>
        <h2>数据管道</h2>
        <span>数据源：/actuator/health、/api/movie-insights?limit=6，部分数据预留后端接口扩展</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refresh">刷新状态</el-button>
    </section>

    <section class="metric-grid-v2">
      <MetricCard v-for="item in pipelineMetrics" :key="item.label" v-bind="item" />
    </section>

    <el-card shadow="never" class="data-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Pipeline Overview</p>
            <h3>管道状态总览</h3>
          </div>
        </div>
      </template>
      <el-steps :active="4" finish-status="success" simple>
        <el-step title="MySQL" :description="mysqlStatus" />
        <el-step title="HDFS" description="待补 /api/data-source/health" />
        <el-step title="MapReduce" description="任务状态需后端日志接口" />
        <el-step title="API Service" :description="healthStore.status" />
      </el-steps>
    </el-card>

    <section class="dashboard-chart-grid lower">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Table Stats</p>
              <h3>数据库表统计</h3>
            </div>
          </div>
        </template>
        <el-table :data="tableStats" stripe height="320">
          <el-table-column prop="table" label="表名" min-width="180" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="note" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
        <p class="panel-note">当前后端未提供 `/api/data-stats`，此处先基于 `movie-insights.dataSources` 生成接入状态展示，并在代码中保留扩展位。</p>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Import Records</p>
              <h3>数据导入记录</h3>
            </div>
          </div>
        </template>
        <div class="action-list">
          <div v-for="record in importRecords" :key="record.title" class="action-card">
            <strong>{{ record.title }}</strong>
            <span>{{ record.status }}</span>
            <small>{{ record.desc }}</small>
          </div>
        </div>
      </el-card>
    </section>

    <section class="dashboard-chart-grid">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Job Timeline</p>
              <h3>推荐任务执行历史</h3>
            </div>
          </div>
        </template>
        <el-timeline>
          <el-timeline-item v-for="job in jobs" :key="job.title" :timestamp="job.time" placement="top">
            <el-card shadow="never" class="timeline-card">
              <strong>{{ job.title }}</strong>
              <p>{{ job.desc }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Source Status</p>
              <h3>数据源状态</h3>
            </div>
          </div>
        </template>
        <div class="source-status-list">
          <div v-for="source in dataSources" :key="source" class="source-status-item">
            <el-badge is-dot type="success" />
            <span>{{ source }}</span>
          </div>
          <div class="source-status-item muted">
            <el-badge is-dot :type="healthStore.isHealthy ? 'success' : 'danger'" />
            <span>后端健康状态：{{ healthStore.status }}</span>
          </div>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import MetricCard from '../components/MetricCard.vue';
import { useDashboardStore } from '../stores/dashboard';
import { useHealthStore } from '../stores/health';

const dashboardStore = useDashboardStore();
const healthStore = useHealthStore();
const loading = ref(false);
let refreshTimer = null;

const insights = computed(() => dashboardStore.insights || {});
const dataSources = computed(() => insights.value.dataSources || []);
const mysqlStatus = computed(() => (dataSources.value.length ? '已连接' : '未知'));

const pipelineMetrics = computed(() => [
  { label: '后端健康', value: healthStore.status, icon: 'CircleCheck', tips: 'actuator/health', growth: healthStore.isHealthy ? 'UP' : 'DOWN' },
  { label: '数据源接入数', value: dataSources.value.length, icon: 'Connection', tips: 'movie-insights.dataSources', growth: '+0', iconBg: 'rgba(20, 184, 166, 0.13)', iconColor: '#0f766e' },
  { label: 'MapReduce 状态', value: '待接入', icon: 'Operation', tips: '需任务日志接口', growth: 'TODO', iconBg: 'rgba(245, 158, 11, 0.16)', iconColor: '#b45309' },
  { label: 'HDFS 状态', value: '待接入', icon: 'FolderOpened', tips: '需 HDFS 健康接口', growth: 'TODO', iconBg: 'rgba(14, 165, 233, 0.14)', iconColor: '#0369a1' },
  { label: '导入记录', value: 3, icon: 'Upload', tips: '模拟导入记录', growth: '+1', iconBg: 'rgba(239, 68, 68, 0.12)', iconColor: '#b91c1c' },
  { label: '推荐任务', value: 4, icon: 'DataBoard', tips: '模拟任务历史', growth: '+0', iconBg: 'rgba(124, 58, 237, 0.14)', iconColor: '#6d28d9' },
]);

const tableStats = computed(() => {
  const defaults = ['stg_movies', 'stg_users', 'dim_movie', 'fact_rating', 'bridge_movie_tag', 'rec_user_movie_topn'];
  const sourceRows = dataSources.value.map((item) => ({
    table: String(item).split('(')[1]?.replace(')：已接入', '') || item,
    status: '已接入',
    note: item,
  }));
  const merged = [...sourceRows];
  defaults.forEach((name) => {
    if (!merged.some((row) => row.table === name)) {
      merged.push({ table: name, status: '待统计', note: '当前缺少 /api/data-stats，建议后端补充表记录数接口。' });
    }
  });
  return merged;
});

const jobs = [
  { title: 'HotMovieRecommendationJob', time: '今天 09:10', desc: '热门电影推荐任务执行完成，后续建议接入真实任务日志接口。' },
  { title: 'TagPreferenceRecommendationJob', time: '今天 09:24', desc: '标签偏好推荐执行完成，当前页面使用模拟任务时间线展示。' },
  { title: 'HybridRecommendationJob', time: '今天 09:47', desc: '混合排序任务执行完成，结果落库到 rec_user_movie_topn。' },
  { title: 'Import HDFS Pipeline', time: '今天 08:40', desc: 'MySQL 导出到 HDFS 的脚本执行成功。' },
];

const importRecords = [
  { title: 'stg_* 原始表导入', status: '成功', desc: '原始电影、用户、评分、评论数据已装载到 staging 表。' },
  { title: 'dim/fact/bridge 加工', status: '成功', desc: '通过 load_movie_analytics.sql 生成分析层表。' },
  { title: 'HDFS 推荐候选导出', status: '待确认', desc: '需要后端补充导出执行记录接口，当前为占位说明。' },
];

async function refresh() {
  loading.value = true;
  try {
    await Promise.all([
      dashboardStore.fetchInsights(true, 6),
      healthStore.fetchHealth(true),
    ]);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  refresh();
  refreshTimer = window.setInterval(refresh, 30000);
});

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
});
</script>
