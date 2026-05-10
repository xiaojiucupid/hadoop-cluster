import axios from 'axios';
import { ElMessage } from 'element-plus';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || '接口请求失败';
    ElMessage.error(message);
    return Promise.reject(error);
  },
);

export async function getApiData(url, config) {
  const result = await http.get(url, config);

  if (result && Object.prototype.hasOwnProperty.call(result, 'code')) {
    if (result.code !== '0000') {
      throw new Error(result.message || '接口返回失败');
    }
    return result.data;
  }

  if (result?.success === false) {
    throw new Error(result.message || '接口返回失败');
  }
  return result?.data ?? result;
}

export default http;
