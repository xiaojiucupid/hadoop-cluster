<script setup>
import * as echarts from 'echarts';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { genreStats, groupBuyRows, topMovies } from './data/mockDashboard';
import {
  algorithmPipeline,
  algorithmScores,
  featureWeights,
  hadoopJobs,
  hdfsLayers,
  mapReduceFlow as initialMapReduceFlow,
  precisionTrend as initialPrecisionTrend,
  recallFunnel as initialRecallFunnel,
  recommendationCards,
  recommendationSegments,
  tagPreferenceData,
  topRecommendations,
} from './data/recommendationDashboard';

const dashboard = reactive({
  recommendationCards,
  algorithmScores,
  recallFunnel: initialRecallFunnel,
  tagPreferenceData,
  precisionTrend: initialPrecisionTrend,
  mapReduceFlow: initialMapReduceFlow,
  hadoopJobs,
  recommendationSegments,
  featureWeights,
  topRecommendations,
  hdfsLayers,
  algorithmPipeline,
});
const loading = ref(true);
const apiError = ref('');

const algorithmRadar = ref(null);
const recallFunnel = ref(null);
const tagPreference = ref(null);
const precisionTrend = ref(null);
const mapReduceFlow = ref(null);
const featureWeightHeatmap = ref(null);
const userSegmentPie = ref(null);
const chartInstances = [];

const maxGenreValue = computed(() => Math.max(...genreStats.map((item) => item.value)));

const chartTheme = {
  textStyle: { color: '#cbd5e1' },
  grid: { left: 36, right: 24, top: 42, bottom: 34 },
};

function mountChart(el, option) {
  if (!el) return;
  const chart = echarts.init(el);
  chart.setOption(option);
  chartInstances.push(chart);
}

function resizeCharts() {
  chartInstances.forEach((chart) => chart.resize());
}

function buildMapReduceGraph() {
  const names = [...new Set(dashboard.mapReduceFlow.flatMap((item) => [item.source, item.target]))];
  return {
    nodes: names.map((name) => ({
      name,
      symbolSize: name.startsWith('rec_') ? 58 : name.startsWith('stg_') || name.startsWith('bridge_') ? 48 : 54,
      category: name.startsWith('stg_') || name.startsWith('bridge_') ? 0 : name.startsWith('rec_') ? 2 : 1,
    })),
    links: dashboard.mapReduceFlow,
  };
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

async function fetchDashboard() {
  loading.value = true;
  apiError.value = '';
  try {
    const response = await fetch(`${API_BASE_URL}/recommendation-dashboard`);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const result = await response.json();
    if (result.code !== '0000') {
      throw new Error(result.message || '接口返回失败');
    }
    const data = result.data;
    dashboard.recommendationCards = data.metricCards || dashboard.recommendationCards;
    dashboard.algorithmScores = data.algorithmScores || dashboard.algorithmScores;
    dashboard.recallFunnel = data.recallFunnel || dashboard.recallFunnel;
    dashboard.tagPreferenceData = data.tagPreferences || dashboard.tagPreferenceData;
    dashboard.precisionTrend = data.precisionTrend || dashboard.precisionTrend;
    dashboard.mapReduceFlow = data.mapReduceFlow || dashboard.mapReduceFlow;
    dashboard.hadoopJobs = data.hadoopJobs || dashboard.hadoopJobs;
    dashboard.recommendationSegments = data.userSegments || dashboard.recommendationSegments;
    dashboard.featureWeights = data.featureWeights || dashboard.featureWeights;
    dashboard.topRecommendations = data.topRecommendations || dashboard.topRecommendations;
    dashboard.hdfsLayers = data.hdfsLayers || dashboard.hdfsLayers;
    dashboard.algorithmPipeline = data.algorithmPipeline || dashboard.algorithmPipeline;
  } catch (error) {
    apiError.value = `后端接口暂不可用，当前展示本地模拟数据：${error.message}`;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await fetchDashboard();
  await nextTick();
  mountChart(algorithmRadar.value, {
    ...chartTheme,
    tooltip: {},
    legend: { bottom: 0, textStyle: { color: '#cbd5e1' } },
    radar: {
      radius: '66%',
      indicator: [
        { name: 'Precision', max: 1 },
        { name: 'Recall', max: 1 },
        { name: 'Coverage', max: 1 },
        { name: 'Latency', max: 1 },
      ],
      axisName: { color: '#bfdbfe' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } },
      splitArea: { areaStyle: { color: ['rgba(56, 189, 248, 0.04)', 'rgba(168, 85, 247, 0.04)'] } },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.2)' } },
    },
    series: [{
      type: 'radar',
      data: dashboard.algorithmScores.map((item) => ({
        name: item.name,
        value: [item.precision, item.recall, item.coverage, item.latency],
      })),
      areaStyle: { opacity: 0.12 },
    }],
  });

  mountChart(chartRefs.recallFunnel.value, {
    ...chartTheme,
    tooltip: { trigger: 'item' },
    series: [{
      name: '推荐漏斗',
      type: 'funnel',
      left: '6%',
      top: 18,
      bottom: 16,
      width: '88%',
      minSize: '8%',
      maxSize: '92%',
      sort: 'descending',
      label: { color: '#e5edf8', formatter: '{b}: {c}' },
      itemStyle: { borderColor: 'rgba(15, 23, 42, 0.9)', borderWidth: 2 },
      data: dashboard.recallFunnel.map((item) => ({ name: item.stage, value: item.value })),
    }],
  });

  mountChart(chartRefs.tagPreference.value, {
    ...chartTheme,
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value', axisLine: { lineStyle: { color: '#475569' } }, splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.12)' } } },
    yAxis: { type: 'category', data: dashboard.tagPreferenceData.map((item) => item.tag), axisLine: { lineStyle: { color: '#475569' } } },
    series: [{
      type: 'bar',
      data: dashboard.tagPreferenceData.map((item) => item.weight),
      barWidth: 14,
      itemStyle: { borderRadius: 8, color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#38bdf8' }, { offset: 1, color: '#a78bfa' }]) },
    }],
  });

  mountChart(chartRefs.precisionTrend.value, {
    ...chartTheme,
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 8, textStyle: { color: '#cbd5e1' } },
    xAxis: { type: 'category', data: dashboard.precisionTrend.map((item) => item.day), axisLine: { lineStyle: { color: '#475569' } } },
    yAxis: { type: 'value', min: 50, max: 90, axisLabel: { formatter: '{value}%' }, splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.12)' } } },
    series: [
      { name: 'ItemCF', type: 'line', smooth: true, data: dashboard.precisionTrend.map((item) => item.itemCf), symbolSize: 8 },
      { name: 'TagPreference', type: 'line', smooth: true, data: dashboard.precisionTrend.map((item) => item.tagPreference), symbolSize: 8 },
      { name: 'HybridRank', type: 'line', smooth: true, data: dashboard.precisionTrend.map((item) => item.hybrid), symbolSize: 8 },
    ],
  });

  const graph = buildMapReduceGraph();
  mountChart(chartRefs.mapReduceFlow.value, {
    ...chartTheme,
    tooltip: {},
    legend: [{ top: 0, textStyle: { color: '#cbd5e1' }, data: ['ODS/DW 输入', 'MapReduce 算法', '推荐结果'] }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      categories: [{ name: 'ODS/DW 输入' }, { name: 'MapReduce 算法' }, { name: '推荐结果' }],
      force: { repulsion: 360, edgeLength: 105 },
      label: { show: true, color: '#e5edf8', fontSize: 11 },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 8,
      lineStyle: { color: '#60a5fa', opacity: 0.42, width: 2, curveness: 0.08 },
      data: graph.nodes,
      links: graph.links,
    }],
  });

  mountChart(chartRefs.featureWeightHeatmap.value, {
    ...chartTheme,
    tooltip: {
      formatter: ({ data }) => `${dashboard.featureWeights[data[1]].feature}<br/>${['Hot', 'Quality', 'Tag', 'Hybrid'][data[0]]}: ${(data[2] * 100).toFixed(0)}%`,
    },
    xAxis: {
      type: 'category',
      data: ['Hot', 'Quality', 'Tag', 'Hybrid'],
      axisLine: { lineStyle: { color: '#475569' } },
    },
    yAxis: {
      type: 'category',
      data: dashboard.featureWeights.map((item) => item.feature),
      axisLine: { lineStyle: { color: '#475569' } },
    },
    visualMap: {
      min: 0,
      max: 0.5,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      textStyle: { color: '#cbd5e1' },
      inRange: { color: ['#0f172a', '#38bdf8', '#a78bfa'] },
    },
    series: [{
      type: 'heatmap',
      data: dashboard.featureWeights.flatMap((item, yIndex) => [
        [0, yIndex, item.hot],
        [1, yIndex, item.quality],
        [2, yIndex, item.tag],
        [3, yIndex, item.hybrid],
      ]),
      label: {
        show: true,
        formatter: ({ data }) => `${(data[2] * 100).toFixed(0)}%`,
        color: '#e2e8f0',
      },
    }],
  });

  mountChart(chartRefs.userSegmentPie.value, {
    ...chartTheme,
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#cbd5e1' } },
    series: [{
      name: '用户分层',
      type: 'pie',
      radius: ['42%', '74%'],
      center: ['50%', '44%'],
      label: { color: '#e5edf8', formatter: '{b}\n{d}%' },
      data: dashboard.recommendationSegments.map((item) => ({ name: item.name, value: item.value })),
    }],
  });

  window.addEventListener('resize', resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts);
  chartInstances.forEach((chart) => chart.dispose());
});
</script>

<template>
  <main class="dashboard-shell">
    <section class="hero-card recommendation-hero">
      <div>
        <p class="eyebrow">Recommendation System · Hadoop + Vue + ECharts</p>
        <h1>影视推荐算法与离线计算看板</h1>
        <p class="hero-desc">
          用 Hadoop/MapReduce 处理评分、评论、标签、电影画像等大规模离线数据，沉淀 ItemCF、标签偏好、混合排序的推荐结果，并在前端通过 ECharts 展示召回、排序、覆盖率和作业链路。
        </p>
        <div class="hero-actions">
          <button class="primary-btn">查看推荐结果</button>
          <button class="ghost-btn">查看 Hadoop 作业</button>
        </div>
      </div>
      <div class="hero-visual pipeline-visual">
        <div class="pipeline-card">
          <div class="pipeline-step source">
            <span>01</span>
            <strong>HDFS</strong>
            <small>离线数据湖</small>
          </div>
          <div class="pipeline-arrow">→</div>
          <div class="pipeline-step mr">
            <span>02</span>
            <strong>MapReduce</strong>
            <small>推荐算法计算</small>
          </div>
          <div class="pipeline-arrow">→</div>
          <div class="pipeline-step result">
            <span>03</span>
            <strong>TopN</strong>
            <small>推荐结果服务</small>
          </div>
        </div>
        <div class="glass-panel rec-panel">
          <span>HybridRank Precision@20</span>
          <strong>83.0%</strong>
          <small>ItemCF 相似度 + 标签偏好 + 热度惩罚综合排序</small>
        </div>
      </div>
    </section>

    <div v-if="loading || apiError" class="api-status" :class="{ warning: apiError }">
      {{ loading ? '正在从后端加载推荐看板数据...' : apiError }}
    </div>

    <section class="overview-grid">
      <article v-for="card in dashboard.recommendationCards" :key="card.label" class="metric-card" :class="`tone-${card.tone}`">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <em>{{ card.trend }}</em>
      </article>
    </section>

    <section class="content-grid recommendation-grid">
      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Algorithm Evaluation</p>
            <h2>推荐算法多指标对比</h2>
          </div>
          <span class="pill">Precision / Recall / Coverage / Latency</span>
        </div>
        <div ref="algorithmRadar" class="echart-box large"></div>
      </article>

      <article class="panel">
        <div class="panel-header compact">
          <div>
            <p class="eyebrow">Recall Funnel</p>
            <h2>候选集召回漏斗</h2>
          </div>
        </div>
        <div ref="recallFunnel" class="echart-box"></div>
      </article>

      <article class="panel">
        <div class="panel-header compact">
          <div>
            <p class="eyebrow">User Profile</p>
            <h2>用户标签偏好权重</h2>
          </div>
        </div>
        <div ref="tagPreference" class="echart-box"></div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Hadoop Flow</p>
            <h2>离线推荐 MapReduce 链路</h2>
          </div>
          <span class="pill">stg / fact / bridge → rec_user_movie_topn</span>
        </div>
        <div ref="mapReduceFlow" class="echart-box graph-box"></div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Precision Trend</p>
            <h2>推荐效果周趋势</h2>
          </div>
          <span class="pill">离线评估指标</span>
        </div>
        <div ref="precisionTrend" class="echart-box medium"></div>
      </article>

      <article class="panel">
        <div class="panel-header compact">
          <div>
            <p class="eyebrow">Hadoop Jobs</p>
            <h2>推荐相关作业产出</h2>
          </div>
        </div>
        <div class="job-list">
          <div v-for="job in dashboard.hadoopJobs" :key="job.name" class="job-card">
            <div>
              <strong>{{ job.name }}</strong>
              <span>{{ job.input }} → {{ job.output }}</span>
            </div>
            <b>{{ job.records }}</b>
            <em>{{ job.duration }} · {{ job.status }}</em>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-header compact">
          <div>
            <p class="eyebrow">User Segments</p>
            <h2>推荐策略用户分层</h2>
          </div>
        </div>
        <div ref="userSegmentPie" class="echart-box"></div>
        <div class="segment-list">
          <div v-for="segment in dashboard.recommendationSegments" :key="segment.name" class="segment-item">
            <strong>{{ segment.name }}</strong>
            <span>{{ segment.strategy }}</span>
          </div>
        </div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Feature Weight</p>
            <h2>混合推荐特征权重热力图</h2>
          </div>
          <span class="pill">Hot / Quality / Tag / Hybrid</span>
        </div>
        <div ref="featureWeightHeatmap" class="echart-box medium"></div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">TopN Result</p>
            <h2>个性化推荐结果样例</h2>
          </div>
          <span class="pill">rec_user_movie_topn</span>
        </div>
        <div class="table-card rec-table">
          <div class="table-row table-head recommendation-row">
            <span>用户</span>
            <span>推荐电影</span>
            <span>推荐理由</span>
            <span>得分</span>
          </div>
          <div v-for="row in dashboard.topRecommendations" :key="`${row.user}-${row.movie}`" class="table-row recommendation-row">
            <span>{{ row.user }}</span>
            <strong>{{ row.movie }}</strong>
            <span>{{ row.reason }}</span>
            <b>{{ row.score }}</b>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-header compact">
          <div>
            <p class="eyebrow">Movie Signals</p>
            <h2>可用于推荐的电影信号</h2>
          </div>
        </div>
        <div class="movie-list compact-list">
          <div v-for="movie in topMovies.slice(0, 4)" :key="movie.rank" class="movie-row signal-row">
            <b>{{ movie.rank }}</b>
            <div class="movie-main">
              <strong>{{ movie.name }}</strong>
              <span>{{ movie.genre }} · 评分 {{ movie.score }}</span>
            </div>
            <div class="heat-bar"><i :style="{ width: `${movie.heat}%` }"></i></div>
          </div>
        </div>
        <p class="panel-note">这些信号可以来自 dim_movie、fact_rating、fact_comment、bridge_movie_tag，用于构建电影画像和候选召回。</p>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">HDFS Layers</p>
            <h2>Hadoop 推荐数据分层</h2>
          </div>
          <span class="pill">ODS / DW / REC / Serving</span>
        </div>
        <div class="hdfs-grid">
          <div v-for="layer in dashboard.hdfsLayers" :key="layer.name" class="hdfs-card">
            <strong>{{ layer.name }}</strong>
            <code>{{ layer.path }}</code>
            <span>{{ layer.files }}</span>
            <b>{{ layer.size }}</b>
          </div>
        </div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Algorithm Pipeline</p>
            <h2>Hadoop 离线推荐算法设计</h2>
          </div>
          <span class="pill">MapReduce Jobs</span>
        </div>
        <div class="pipeline-steps">
          <div v-for="item in dashboard.algorithmPipeline" :key="item.step" class="pipeline-step-card">
            <b>{{ item.step }}</b>
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.job }} → {{ item.output }}</span>
              <p>{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Group Buy + Recommendation</p>
            <h2>拼团场景推荐运营联动</h2>
          </div>
          <span class="pill">gb_activity 推荐转化</span>
        </div>
        <div class="table-card">
          <div class="table-row table-head">
            <span>活动编号</span>
            <span>电影</span>
            <span>状态</span>
            <span>规则</span>
            <span>支付金额</span>
            <span>成团率</span>
          </div>
          <div v-for="row in groupBuyRows" :key="row.activityNo" class="table-row">
            <span>{{ row.activityNo }}</span>
            <strong>{{ row.movie }}</strong>
            <em>{{ row.status }}</em>
            <span>{{ row.groupSize }}</span>
            <b>{{ row.paidAmount }}</b>
            <span>{{ row.successRate }}</span>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>
