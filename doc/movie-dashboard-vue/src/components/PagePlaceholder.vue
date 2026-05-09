<template>
  <div class="page-shell">
    <el-card shadow="never" class="placeholder-card">
      <template #header>
        <div class="placeholder-header">
          <div>
            <h2>{{ title }}</h2>
            <p>{{ description }}</p>
          </div>
          <el-tag type="info">阶段一骨架</el-tag>
        </div>
      </template>
      <el-empty :description="emptyText">
        <template #image>
          <el-icon class="placeholder-icon"><DataAnalysis /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
  emptyText: { type: String, default: '该页面将在下一阶段接入后端数据并完成图表与表格。' },
});
</script>
