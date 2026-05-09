<script setup>
import * as echarts from 'echarts';
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  tag: { type: String, default: '' },
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
});

const chartRef = ref(null);
const chart = shallowRef(null);

const renderChart = () => {
  if (!chartRef.value) {
    return;
  }
  if (!chart.value) {
    chart.value = echarts.init(chartRef.value);
  }
  chart.value.setOption(props.option, true);
};

const resizeChart = () => {
  chart.value?.resize();
};

onMounted(() => {
  renderChart();
  window.addEventListener('resize', resizeChart);
});

watch(() => props.option, renderChart, { deep: true });

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart);
  chart.value?.dispose();
});
</script>

<template>
  <article class="panel chart-panel">
    <div class="panel-header">
      <div>
        <p class="eyebrow">{{ subtitle }}</p>
        <h2>{{ title }}</h2>
      </div>
      <span v-if="tag" class="pill">{{ tag }}</span>
    </div>
    <div ref="chartRef" class="echart-box" :style="{ height }"></div>
  </article>
</template>
