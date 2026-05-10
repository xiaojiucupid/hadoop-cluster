<template>
  <div class="page-shell group-page" v-loading="store.loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">Group Buy</p>
        <h2>拼团业务</h2>
        <span>当前复用 /api/movie-dashboard 中拼团相关字段，漏斗与快照数据预留独立接口。</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refresh">刷新业务数据</el-button>
    </section>

    <section class="metric-grid-v2">
      <MetricCard label="拼团营收" :value="formatCurrency(summary.groupRevenue)" icon="Money" tips="gb_trade_order" growth="+18.2%" />
      <MetricCard label="成团率" :value="`${Number(summary.successRate ?? 0)}%`" icon="TrendCharts" tips="gb_group_snapshot" growth="+3.4%" />
      <MetricCard label="活跃活动数" :value="formatNumber(groupActivities.length)" icon="Tickets" tips="dashboard.groupActivities" growth="活跃" />
      <MetricCard label="参与用户数" :value="formatNumber(estimatedParticipants)" icon="User" tips="前端聚合估算" growth="估算" />
    </section>

    <section class="dashboard-chart-grid">
      <ChartCard title="拼团趋势" eyebrow="Trend" :option="trendOption" :empty="!groupTrend.length" />
      <ChartCard title="转化漏斗" eyebrow="Conversion" :option="funnelOption" :empty="false" />
    </section>

    <section class="dashboard-chart-grid lower">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Activities</p>
              <h3>拼团活动列表</h3>
            </div>
          </div>
        </template>
        <el-table :data="groupActivities" height="340" stripe empty-text="暂无拼团活动数据">
          <el-table-column prop="name" label="活动名" min-width="180" />
          <el-table-column prop="target" label="成团人数" width="110" />
          <el-table-column prop="groupPrice" label="拼团价" width="110">
            <template #default="{ row }">¥{{ row.groupPrice }}</template>
          </el-table-column>
          <el-table-column prop="singlePrice" label="单买价" width="110">
            <template #default="{ row }">¥{{ row.singlePrice }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Snapshots</p>
              <h3>拼团快照统计</h3>
            </div>
          </div>
        </template>
        <div class="snapshot-grid">
          <article v-for="item in snapshotCards" :key="item.title" class="snapshot-card">
            <strong>{{ item.title }}</strong>
            <span>{{ item.value }}</span>
            <small>{{ item.desc }}</small>
          </article>
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="说明"
          description="当前后端未提供独立 group_snapshot 接口，页面先基于 dashboard.groupTrend 和 groupActivities 聚合近似快照。后续建议新增 /api/group-buy/snapshot。"
        />
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import ChartCard from '../components/ChartCard.vue';
import MetricCard from '../components/MetricCard.vue';
import { fallbackDashboard } from '../fallbackData';
import { mergeWithFallback } from '../services/http';
import { useDashboardStore } from '../stores/dashboard';

const store = useDashboardStore();
const dashboard = computed(() => mergeWithFallback(store.dashboard, fallbackDashboard));
const summary = computed(() => dashboard.value.dashboardSummary || {});
const groupTrend = computed(() => dashboard.value.groupTrend || []);
const groupActivities = computed(() => dashboard.value.groupActivities || []);

const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value ?? 0));
const formatCurrency = (value) => `¥${Number(value ?? 0).toFixed(2)}`;
const estimatedParticipants = computed(() => groupActivities.value.reduce((sum, item) => sum + Number(item.target || 0), 0));

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 0 },
  grid: { left: 42, right: 20, top: 42, bottom: 32 },
  xAxis: { type: 'category', data: groupTrend.value.map((item) => item.date) },
  yAxis: { type: 'value' },
  series: [
    { name: '开团数', type: 'line', smooth: true, data: groupTrend.value.map((item) => item.groups), lineStyle: { width: 3, color: '#6366f1' } },
    { name: '成团数', type: 'line', smooth: true, data: groupTrend.value.map((item) => item.success), lineStyle: { width: 3, color: '#22c55e' } },
  ],
}));

const funnelOption = computed(() => {
  const launched = groupTrend.value.reduce((sum, item) => sum + Number(item.groups || 0), 0) || 100;
  const joined = Math.round(launched * 0.72);
  const paid = Math.round(joined * 0.83);
  const success = Math.round(paid * ((Number(summary.value.successRate || 0) || 60) / 100));
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'funnel',
      left: '10%',
      top: 20,
      bottom: 20,
      width: '80%',
      label: { show: true, position: 'inside' },
      data: [
        { value: launched, name: '浏览' },
        { value: joined, name: '参团' },
        { value: paid, name: '支付' },
        { value: success, name: '成团' },
      ],
    }],
  };
});

const snapshotCards = computed(() => [
  { title: '近 7 天开团', value: formatNumber(groupTrend.value.reduce((sum, item) => sum + Number(item.groups || 0), 0)), desc: '来自 groupTrend 聚合' },
  { title: '近 7 天成团', value: formatNumber(groupTrend.value.reduce((sum, item) => sum + Number(item.success || 0), 0)), desc: '来自 groupTrend 聚合' },
  { title: '活动均价', value: formatCurrency(groupActivities.value.reduce((sum, item) => sum + Number(item.groupPrice || 0), 0) / Math.max(groupActivities.value.length, 1)), desc: '基于活动列表估算' },
  { title: '预估参与人数', value: formatNumber(estimatedParticipants.value), desc: '基于活动 target 聚合' },
]);

async function refresh() {
  await store.fetchDashboard(true);
}

onMounted(() => {
  store.fetchDashboard();
});
</script>
