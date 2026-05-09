import axios from 'axios';
import { ElMessage } from 'element-plus';

const http = axios.create({
  baseURL: '/api',
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
  if (result?.success === false) {
    throw new Error(result.message || '接口返回失败');
  }
  return result?.data ?? result;
}

export default http;
