import { defineStore } from 'pinia';
import { fallbackAgentContext, fallbackAgentReport } from '../fallbackData';
import { getApiData } from '../services/http';

const EXPIRE_MS = 5 * 60 * 1000;

export const useAgentStore = defineStore('agent', {
  state: () => ({
    report: fallbackAgentReport,
    context: fallbackAgentContext,
    loading: false,
    fetchedAt: 0,
  }),
  getters: {
    isExpired: (state) => Date.now() - state.fetchedAt > EXPIRE_MS,
  },
  actions: {
    async fetchReport(force = false, limit = 6) {
      if (!force && this.report && !this.isExpired) {
        return this.report;
      }
      this.loading = true;
      try {
        this.report = await getApiData('/movie-agent/analyze', { params: { limit } }, fallbackAgentReport);
        this.fetchedAt = Date.now();
        return this.report;
      } finally {
        this.loading = false;
      }
    },
    async fetchContext(force = false, limit = 6) {
      if (!force && this.context && !this.isExpired) {
        return this.context;
      }
      this.loading = true;
      try {
        this.context = await getApiData('/movie-agent/context', { params: { limit } }, fallbackAgentContext);
        this.fetchedAt = Date.now();
        return this.context;
      } finally {
        this.loading = false;
      }
    },
  },
});
