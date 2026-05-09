import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '总览仪表盘' } },
  { path: '/insights', name: 'Insights', component: () => import('../views/InsightsView.vue'), meta: { title: '电影洞察' } },
  { path: '/recommendation', name: 'Recommendation', component: () => import('../views/RecommendationView.vue'), meta: { title: '推荐系统' } },
  { path: '/users', name: 'Users', component: () => import('../views/UsersView.vue'), meta: { title: '用户画像' } },
  { path: '/users/:id', name: 'UserDetail', component: () => import('../views/UserDetailView.vue'), meta: { title: '用户详情' } },
  { path: '/group-buy', name: 'GroupBuy', component: () => import('../views/GroupBuyView.vue'), meta: { title: '拼团业务' } },
  { path: '/agent', name: 'Agent', component: () => import('../views/AgentView.vue'), meta: { title: 'AI 分析报告' } },
  { path: '/pipeline', name: 'Pipeline', component: () => import('../views/PipelineView.vue'), meta: { title: '数据管道' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.afterEach((to) => {
  document.title = `${to.meta.title || '影视数据分析'} - Movie Analytics`;
});

export default router;
