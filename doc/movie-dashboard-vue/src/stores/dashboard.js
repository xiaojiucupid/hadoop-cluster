import { defineStore } from 'pinia';
import { getApiData } from '../services/http';

const EXPIRE_MS = 5 * 60 * 1000;

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    dashboard: null,
    insights: null,
    recommendationDashboard: null,
    loading: false,
    fetchedAt: 0,
  }),
  getters: {
    isExpired: (state) => Date.now() - state.fetchedAt > EXPIRE_MS,
  },
  actions: {
    async fetchDashboard(force = false) {
      if (!force && this.dashboard && !this.isExpired) {
        return this.dashboard;
      }
      this.loading = true;
      try {
        this.dashboard = await getApiData('/movie-dashboard');
        this.fetchedAt = Date.now();
        return this.dashboard;
      } finally {
        this.loading = false;
      }
    },
    async fetchInsights(force = false, limit = 6) {
      if (!force && this.insights && !this.isExpired) {
        return this.insights;
      }
      this.loading = true;
      try {
        this.insights = await getApiData('/movie-insights', { params: { limit } });
        this.fetchedAt = Date.now();
        return this.insights;
      } finally {
        this.loading = false;
      }
    },
    async fetchRecommendationDashboard(force = false) {
      if (!force && this.recommendationDashboard && !this.isExpired) {
        return this.recommendationDashboard;
      }
      try {
        this.recommendationDashboard = await getApiData('/recommendation-dashboard');
        this.fetchedAt = Date.now();
        return this.recommendationDashboard;
      } catch (error) {
        // 后端当前可能未提供该接口，页面侧会回退到 movie-insights 的推荐统计字段。
        this.recommendationDashboard = null;
        return null;
      }
    },
  },
});
