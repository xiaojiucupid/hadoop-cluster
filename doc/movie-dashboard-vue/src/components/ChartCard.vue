<template>
  <el-card shadow="never" class="chart-card">
    <template #header>
      <div class="card-header">
        <div>
          <p class="eyebrow">{{ eyebrow }}</p>
          <h3>{{ title }}</h3>
        </div>
        <slot name="extra" />
      </div>
    </template>
    <div ref="chartRef" class="chart-box" :style="{ height }"></div>
    <el-empty v-if="empty" class="chart-empty" description="暂无后端数据" />
  </el-card>
</template>

<script setup>
import * as echarts from 'echarts';
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  title: { type: String, required: true },
  eyebrow: { type: String, default: 'Chart' },
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
  empty: { type: Boolean, default: false },
});

const chartRef = ref(null);
let chart;

function render() {
  if (!chartRef.value || props.empty) {
    return;
  }
  if (!chart) {
    chart = echarts.init(chartRef.value);
  }
  chart.setOption(props.option, true);
}

function resize() {
  chart?.resize();
}

onMounted(async () => {
  await nextTick();
  render();
  window.addEventListener('resize', resize);
});

watch(() => props.option, async () => {
  await nextTick();
  render();
}, { deep: true });

watch(() => props.empty, async () => {
  await nextTick();
  render();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  chart?.dispose();
});
</script>
