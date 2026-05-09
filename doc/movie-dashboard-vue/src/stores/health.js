import { defineStore } from 'pinia';
import { getHealth } from '../services/http';

const EXPIRE_MS = 30 * 1000;

export const useHealthStore = defineStore('health', {
  state: () => ({
    status: 'UNKNOWN',
    details: null,
    fetchedAt: 0,
    loading: false,
  }),
  getters: {
    isExpired: (state) => Date.now() - state.fetchedAt > EXPIRE_MS,
    isHealthy: (state) => state.status === 'UP',
  },
  actions: {
    async fetchHealth(force = false) {
      if (!force && this.details && !this.isExpired) {
        return this.details;
      }
      this.loading = true;
      try {
        this.details = await getHealth();
        this.status = this.details?.status || 'UNKNOWN';
        this.fetchedAt = Date.now();
        return this.details;
      } catch (error) {
        this.status = 'DOWN';
        this.details = { status: 'DOWN' };
        this.fetchedAt = Date.now();
        return this.details;
      } finally {
        this.loading = false;
      }
    },
  },
});
