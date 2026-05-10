<template>
  <el-container class="app-layout">
    <el-aside class="app-sidebar" :width="collapsed ? '72px' : '248px'">
      <div class="brand" :class="{ collapsed }">
        <div class="brand-logo">M</div>
        <div v-if="!collapsed">
          <strong>Movie Analytics</strong>
          <span>影视推荐分析平台</span>
        </div>
      </div>
      <el-menu
        :default-active="route.path"
        class="sidebar-menu"
        router
        :collapse="collapsed"
        background-color="transparent"
        text-color="#475569"
        active-text-color="#4f46e5"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-button text class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <div>
            <h1>{{ route.meta.title || '影视数据分析' }}</h1>
            <p>数据库 / MapReduce / Agent 驱动的动态推荐分析系统</p>
          </div>
        </div>
        <div class="header-right">
          <el-tag type="success" effect="light">数据服务在线</el-tag>
          <span class="clock">{{ currentTime }}</span>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const collapsed = ref(false);
const now = ref(new Date());
let timer = null;

const menus = [
  { path: '/dashboard', title: '总览仪表盘', icon: 'DataBoard' },
  { path: '/insights', title: '电影洞察', icon: 'TrendCharts' },
  { path: '/recommendation', title: '推荐系统', icon: 'MagicStick' },
  { path: '/users', title: '用户画像', icon: 'User' },
  { path: '/group-buy', title: '拼团业务', icon: 'ShoppingCart' },
  { path: '/agent', title: 'AI 分析报告', icon: 'Cpu' },
  { path: '/movie-qa', title: '电影智能问答', icon: 'ChatDotRound' },
  { path: '/pipeline', title: '数据管道', icon: 'Connection' }, 
];

const currentTime = computed(() => now.value.toLocaleString('zh-CN', { hour12: false }));

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date();
  }, 1000);
});

onBeforeUnmount(() => {
  window.clearInterval(timer);
});
</script>
