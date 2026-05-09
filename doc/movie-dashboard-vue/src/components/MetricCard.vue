<template>
  <el-card shadow="never" class="metric-card-v2">
    <div class="metric-icon" :style="{ background: iconBg, color: iconColor }">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div class="metric-body">
      <span>{{ label }}</span>
      <strong>
        <CountUpNumber v-if="animate" :value="value" :prefix="prefix" :suffix="suffix" :decimals="decimals" />
        <template v-else>{{ value ?? 0 }}</template>
      </strong>
      <small>
        <el-tag size="small" type="success">{{ growth }}</el-tag>
        <em>{{ tips }}</em>
      </small>
    </div>
  </el-card>
</template>

<script setup>
import CountUpNumber from './CountUpNumber.vue';

defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: 0 },
  icon: { type: String, default: 'DataAnalysis' },
  tips: { type: String, default: '来自后端接口' },
  growth: { type: String, default: '+8.2%' },
  iconBg: { type: String, default: 'rgba(99, 102, 241, 0.12)' },
  iconColor: { type: String, default: '#4f46e5' },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  decimals: { type: Number, default: 0 },
  animate: { type: Boolean, default: true },
});
</script>
