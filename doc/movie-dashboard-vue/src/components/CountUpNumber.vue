<template>
  <span>{{ formattedValue }}</span>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  decimals: { type: Number, default: 0 },
  duration: { type: Number, default: 700 },
});

const display = ref(0);
let frame = 0;

const numericValue = computed(() => Number(String(props.value).replace(/[^0-9.-]/g, '')) || 0);
const formattedValue = computed(() => {
  const value = display.value.toLocaleString('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals,
  });
  return `${props.prefix}${value}${props.suffix}`;
});

function animate(to) {
  cancelAnimationFrame(frame);
  const from = display.value;
  const start = performance.now();
  const tick = (now) => {
    const progress = Math.min((now - start) / props.duration, 1);
    display.value = from + (to - from) * (1 - Math.pow(1 - progress, 3));
    if (progress < 1) {
      frame = requestAnimationFrame(tick);
    } else {
      display.value = to;
    }
  };
  frame = requestAnimationFrame(tick);
}

watch(numericValue, (value) => animate(value), { immediate: true });

onBeforeUnmount(() => {
  cancelAnimationFrame(frame);
});
</script>
