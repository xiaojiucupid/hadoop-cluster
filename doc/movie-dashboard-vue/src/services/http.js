import axios from 'axios';
import { ElMessage } from 'element-plus';

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error.message || '接口请求失败';
    ElMessage.error(message);
    return Promise.reject(error);
  },
);

export async function getApiData(url, config) {
  const response = await http.get(url, config);
  const result = response.data;
  if (result && Object.prototype.hasOwnProperty.call(result, 'success')) {
    if (!result.success) {
      throw new Error(result.message || '后端返回失败');
    }
    return result.data;
  }
  return result;
}

export async function getHealth() {
  const response = await axios.get('/actuator/health', { timeout: 8000 });
  return response.data;
}
