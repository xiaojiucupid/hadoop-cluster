import { defineStore } from 'pinia';
import { getApiData, mergeWithFallback } from '../services/http';
import { fallbackDashboard, fallbackInsights, fallbackRecommendationDashboard } from '../fallbackData';

const EXPIRE_MS = 5 * 60 * 1000;

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    dashboard: fallbackDashboard,
    insights: fallbackInsights,
    recommendationDashboard: fallbackRecommendationDashboard,
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
        this.dashboard = mergeWithFallback(await getApiData('/movie-dashboard', undefined, fallbackDashboard), fallbackDashboard);
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
        this.insights = mergeWithFallback(await getApiData('/movie-insights', { params: { limit } }, fallbackInsights), fallbackInsights);
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
      this.recommendationDashboard = await getApiData('/recommendation-dashboard', undefined, fallbackRecommendationDashboard);
      this.fetchedAt = Date.now();
      return this.recommendationDashboard;
    },
  },
});
