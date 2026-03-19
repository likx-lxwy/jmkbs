import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 8000
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('mk_token');
  if (token) {
    config.headers['X-Auth-Token'] = token;
  }
  return config;
});

export const fetchCategories = async () => {
  const res = await api.get('/api/categories');
  return res.data;
};

export const fetchProducts = async (categoryId) => {
  const res = await api.get('/api/products', { params: categoryId ? { categoryId } : {} });
  return res.data;
};

export const submitOrder = async (payload) => {
  const res = await api.post('/api/orders', payload);
  return res.data;
};

export const login = async (payload) => {
  const res = await api.post('/api/auth/login', payload);
  return res.data;
};

export const fetchLoginCaptcha = async () => {
  const res = await api.get('/api/auth/captcha');
  return res.data;
};

export const logout = async () => {
  await api.post('/api/auth/logout');
};

export const register = async (payload) => {
  const res = await api.post('/api/auth/register', payload);
  return res.data;
};

export const sendRegisterEmailCode = async (email) => {
  const res = await api.post('/api/auth/email-code', { email });
  return res.data;
};

export const createProduct = async (payload) => {
  const res = await api.post('/api/products', payload);
  return res.data;
};

export const updateProduct = async (id, payload) => {
  const res = await api.put(`/api/products/${id}`, payload);
  return res.data;
};

export const deleteProduct = async (id) => {
  await api.delete(`/api/products/${id}`);
};

export const fetchAdminOverview = async () => {
  const res = await api.get('/api/admin/overview');
  return res.data;
};

export const fetchAdminMerchants = async (status) => {
  const res = await api.get('/api/admin/merchants', { params: status ? { status } : {} });
  return res.data;
};

export const updateMerchantStatus = async (id, payload) => {
  const res = await api.post(`/api/admin/merchants/${id}/status`, payload);
  return res.data;
};

export const fetchAdminOrders = async () => {
  const res = await api.get('/api/admin/orders');
  return res.data;
};

export const fetchPendingOrders = async () => {
  const res = await api.get('/api/admin/orders/pending');
  return res.data;
};

export const approveOrder = async (id) => {
  await api.post(`/api/admin/orders/${id}/approve`);
};

export const rejectOrder = async (id) => {
  await api.post(`/api/admin/orders/${id}/reject`);
};

export const getAiKey = async () => {
  const res = await api.get('/api/admin/ai/key');
  return res.data;
};

export const saveAiKey = async (payload) => {
  const res = await api.post('/api/admin/ai/key', payload);
  return res.data;
};

export const aiRecommend = async (query) => {
  const res = await api.post('/api/ai/recommend', { query });
  return res.data;
};

export const approveRefundAdmin = async (id) => {
  await api.post(`/api/admin/orders/${id}/refund/approve`);
};

export const rejectRefundAdmin = async (id) => {
  await api.post(`/api/admin/orders/${id}/refund/reject`);
};

export const getSettings = async () => {
  const res = await api.get('/api/admin/settings');
  return res.data;
};

export const setApprovalLevel = async (level) => {
  const res = await api.post('/api/admin/settings/approval', { orderApprovalLevel: level });
  return res.data;
};

export const getWallet = async () => {
  const res = await api.get('/api/wallet/me');
  return res.data;
};

export const recharge = async (amount) => {
  const res = await api.post('/api/wallet/recharge', { amount });
  return res.data;
};

export const paySubscription = async () => {
  const res = await api.post('/api/wallet/subscribe');
  return res.data;
};

export const adminWallets = async () => {
  const res = await api.get('/api/wallet/admin/users');
  return res.data;
};

export const adminAdjustWallet = async (id, amount) => {
  const res = await api.post(`/api/wallet/admin/users/${id}/wallet`, { amount });
  return res.data;
};

export const adminResetPassword = async (id, password) => {
  await api.post(`/api/wallet/admin/users/${id}/password`, { password });
};

export const fetchAdminRevenue = async () => {
  const res = await api.get('/api/admin/revenue');
  return res.data;
};

export const fetchLoginLogs = async () => {
  const res = await api.get('/api/admin/login-logs');
  return res.data;
};

export const fetchSystemLogs = async (lines = 200) => {
  const res = await api.get('/api/admin/system-logs', { params: { lines } });
  return res.data;
};

export const fetchChat = async (productId, targetId) => {
  const res = await api.get('/api/chat', { params: { productId, targetId } });
  return res.data;
};

export const sendChat = async (payload) => {
  const res = await api.post('/api/chat', payload);
  return res.data;
};

export const fetchRecentChat = async () => {
  const res = await api.get('/api/chat/recent');
  return res.data;
};

export const runTerminal = async (command, password, signature) => {
  const res = await api.post('/api/admin/terminal/run', { command, password, signature });
  return res.data;
};

export const fetchPaymentLogs = async () => {
  const res = await api.get('/api/payments/mine');
  return res.data;
};

export const fetchMyOrders = async () => {
  const res = await api.get('/api/orders/mine');
  return res.data;
};

export const refundOrder = async (id, payload = {}) => {
  await api.post(`/api/orders/${id}/refund`, payload);
};

export const createAlipayPayUrl = async (orderId) => {
  const res = await api.post('/api/payments/alipay/pay', { orderId });
  return res.data;
};

export const uploadImage = async (file) => {
  const form = new FormData();
  form.append('file', file);
  const res = await api.post('/api/upload/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return res.data;
};

export const fetchMyProducts = async () => {
  const res = await api.get('/api/products/mine');
  return res.data;
};

export const uploadVideo = async (file) => {
  const form = new FormData();
  form.append('file', file);
  const res = await api.post('/api/upload/video', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return res.data;
};

export const fetchComments = async (productId) => {
  const res = await api.get(`/api/products/${productId}/comments`);
  return res.data;
};

export const addComment = async (productId, content) => {
  const res = await api.post(`/api/products/${productId}/comments`, { content });
  return res.data;
};

export const fetchProductIntro = async (productId) => {
  const res = await api.get(`/api/ai/product/${productId}/intro`);
  return res.data;
};

export const reverseGeocode = async (lat, lng) => {
  const res = await api.get('/api/location/reverse', { params: { lat, lng } });
  return res.data;
};

export const getLocationConfig = async () => {
  const res = await api.get('/api/location/config');
  return res.data;
};

// 地址簿
export const fetchAddresses = async () => {
  const res = await api.get('/api/addresses');
  return res.data;
};

export const createAddress = async (payload) => {
  const res = await api.post('/api/addresses', payload);
  return res.data;
};

export const updateAddress = async (id, payload) => {
  const res = await api.put(`/api/addresses/${id}`, payload);
  return res.data;
};

export const deleteAddress = async (id) => {
  await api.delete(`/api/addresses/${id}`);
};

export const setDefaultAddress = async (id) => {
  await api.post(`/api/addresses/${id}/default`);
};

export const changeMyPassword = async (oldPassword, newPassword) => {
  await api.post('/api/auth/password', { oldPassword, newPassword });
};

export default api;
