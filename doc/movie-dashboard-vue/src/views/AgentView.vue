<template>
  <div class="page-shell agent-page" v-loading="agentStore.loading">
    <section class="page-hero">
      <div>
        <p class="eyebrow">AI Analysis Report</p>
        <h2>AI 分析报告</h2>
        <span>基于电影画像、用户行为与推荐策略生成结构化 AI 分析报告</span>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refresh">刷新报告</el-button>
    </section>

    <el-card shadow="never" class="data-card hero-summary-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Agent Summary</p>
            <h3>分析结论</h3>
          </div>
          <el-tag type="success">{{ report?.context?.metrics?.recommendationSource || '规则 Agent' }}</el-tag>
        </div>
      </template>
      <p class="agent-summary-text">{{ report?.answer?.summary || '暂无 Agent 总结。' }}</p>
    </el-card>

    <section class="dashboard-chart-grid">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Insights</p>
              <h3>核心洞察</h3>
            </div>
          </div>
        </template>
        <div class="action-list">
          <div v-for="(item, index) in insights" :key="item" class="insight-card">
            <div class="insight-index">{{ index + 1 }}</div>
            <div>
              <strong>{{ item }}</strong>
              <small>洞察来自后端结构化参数计算与规则 Agent 归纳。</small>
            </div>
          </div>
          <el-empty v-if="!insights.length" description="暂无核心洞察" />
        </div>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Strategies</p>
              <h3>推荐策略</h3>
            </div>
          </div>
        </template>
        <el-steps direction="vertical" :active="strategies.length">
          <el-step v-for="(item, index) in strategies" :key="item" :title="`策略 ${index + 1}`" :description="item" />
        </el-steps>
      </el-card>
    </section>

    <section class="dashboard-chart-grid lower">
      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Actions</p>
              <h3>行动建议</h3>
            </div>
          </div>
        </template>
        <el-timeline>
          <el-timeline-item v-for="(item, index) in actions" :key="item" :timestamp="`建议 ${index + 1}`">
            <el-card shadow="never" class="timeline-card">
              <strong>{{ item }}</strong>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!actions.length" description="暂无行动建议" />
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="eyebrow">Evidence</p>
              <h3>证据链</h3>
            </div>
          </div>
        </template>
        <el-table :data="evidenceRows" stripe height="320" empty-text="暂无证据链">
          <el-table-column prop="key" label="参数" min-width="140" />
          <el-table-column prop="value" label="值" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-card>
    </section>

    <el-card shadow="never" class="data-card">
      <template #header>
        <div class="card-header">
          <div>
            <p class="eyebrow">Agent Context</p>
            <h3>上下文参数面板</h3>
          </div>
        </div>
      </template>
      <el-collapse>
        <el-collapse-item title="查看 Agent 输入参数 JSON" name="context-json">
          <pre class="json-panel">{{ prettyContext }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import { fallbackAgentContext, fallbackAgentReport } from '../fallbackData';
import { mergeWithFallback } from '../services/http';
import { useAgentStore } from '../stores/agent';

const agentStore = useAgentStore();
const report = computed(() => mergeWithFallback(agentStore.report, fallbackAgentReport));
const context = computed(() => mergeWithFallback(agentStore.context || report.value.context, fallbackAgentContext));
const insights = computed(() => report.value.answer?.insights || []);
const strategies = computed(() => report.value.answer?.recommendationStrategies || []);
const actions = computed(() => report.value.answer?.actions || []);

const evidenceRows = computed(() => {
  const raw = report.value.answer?.evidence || [];
  return raw.map((item, index) => {
    const [key, ...rest] = String(item).split('=');
    return { key: key || `evidence_${index + 1}`, value: rest.join('=') || item };
  });
});

const prettyContext = computed(() => JSON.stringify(context.value, null, 2));

async function refresh() {
  await Promise.all([
    agentStore.fetchReport(true, 6),
    agentStore.fetchContext(true, 6),
  ]);
}

onMounted(() => {
  Promise.all([
    agentStore.fetchReport(false, 6),
    agentStore.fetchContext(false, 6),
  ]);
});
</script>
