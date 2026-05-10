import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const ACTUATOR_BASE_URL = import.meta.env.VITE_ACTUATOR_BASE_URL || '';

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 3000,
});

http.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(error),
);

function isPlainObject(value) {
  return Object.prototype.toString.call(value) === '[object Object]';
}

function isEmptyValue(value, fallback) {
  if (value === null || value === undefined || value === '') return true;
  if (typeof fallback === 'number' && Number(value) === 0 && fallback !== 0) return true;
  if (Array.isArray(value)) return value.length === 0;
  if (isPlainObject(value)) return Object.keys(value).length === 0;
  return false;
}

export function mergeWithFallback(value, fallback) {
  if (fallback === undefined) return value;
  if (isEmptyValue(value, fallback)) return fallback;

  if (Array.isArray(fallback)) {
    return Array.isArray(value) && value.length > 0 ? value : fallback;
  }

  if (isPlainObject(fallback)) {
    const source = isPlainObject(value) ? value : {};
    const merged = { ...source };
    Object.entries(fallback).forEach(([key, fallbackValue]) => {
      merged[key] = mergeWithFallback(source[key], fallbackValue);
    });
    return merged;
  }

  return value;
}

export async function getApiData(url, config, fallbackData) {
  try {
    const response = await http.get(url, config);
    const result = response.data;
    let data;

    if (result && Object.prototype.hasOwnProperty.call(result, 'code')) {
      if (result.code !== '0000') {
        throw new Error(result.message || '后端返回失败');
      }
      data = result.data;
    } else if (result && Object.prototype.hasOwnProperty.call(result, 'success')) {
      if (!result.success) {
        throw new Error(result.message || '后端返回失败');
      }
      data = result.data;
    } else {
      data = result;
    }

    return mergeWithFallback(data, fallbackData);
  } catch (error) {
    if (fallbackData !== undefined) {
      return fallbackData;
    }
    throw error;
  }
}

export async function getHealth() {
  const response = await axios.get(`${ACTUATOR_BASE_URL}/actuator/health`, { timeout: 8000 });
  return response.data;
}
