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

export const fetchProductDetail = async (id) => {
  const res = await api.get(`/api/products/${id}`);
  return res.data;
};

export const submitOrder = async (payload) => {
  const res = await api.post('/api/orders', payload);
  return res.data;
};

export const submitOrderBatch = async (payload) => {
  const res = await api.post('/api/orders/batch', payload);
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

export const getMerchantProfile = async () => {
  const res = await api.get('/api/merchant/profile');
  return res.data;
};

export const saveMerchantProfile = async (payload) => {
  const res = await api.put('/api/merchant/profile', payload);
  return res.data;
};

export const fetchAdminOrders = async () => {
  const res = await api.get('/api/admin/orders');
  return res.data;
};

export const fetchAdminUsers = async () => {
  const res = await api.get('/api/admin/users');
  return res.data;
};

export const updateAdminUserAccountStatus = async (id, status) => {
  const res = await api.post(`/api/admin/users/${id}/account-status`, { status });
  return res.data;
};

export const deleteAdminUser = async (id) => {
  await api.delete(`/api/admin/users/${id}`);
};

export const approveRefundMerchant = async (id) => {
  await api.post(`/api/orders/${id}/refund/approve`);
};

export const rejectRefundMerchant = async (id) => {
  await api.post(`/api/orders/${id}/refund/reject`);
};

export const getWallet = async () => {
  const res = await api.get('/api/wallet/me');
  return res.data;
};

export const recharge = async (amount) => {
  const res = await api.post('/api/wallet/recharge', { amount });
  return res.data;
};

export const adminResetPassword = async (id, password) => {
  await api.post(`/api/wallet/admin/users/${id}/password`, { password });
};

export const fetchAdminRevenue = async () => {
  const res = await api.get('/api/admin/revenue');
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

export const fetchRefundChat = async (orderId) => {
  const res = await api.get(`/api/orders/${orderId}/refund-chat`);
  return res.data;
};

export const sendRefundChat = async (orderId, content) => {
  await api.post(`/api/orders/${orderId}/refund-chat`, { content });
};

export const fetchPaymentLogs = async () => {
  const res = await api.get('/api/payments/mine');
  return res.data;
};

export const fetchMyOrders = async () => {
  const res = await api.get('/api/orders/mine');
  return res.data;
};

export const confirmReceiptOrder = async (id) => {
  await api.post(`/api/orders/${id}/confirm-receipt`);
};

export const submitMerchantOrderReview = async (id, payload) => {
  await api.post(`/api/orders/${id}/review`, payload);
};

export const refundOrder = async (id, payload = {}) => {
  await api.post(`/api/orders/${id}/refund`, payload);
};

export const createAlipayPayUrl = async (payload) => {
  const requestBody = typeof payload === 'object' && payload !== null
    ? payload
    : { orderId: payload };
  const res = await api.post('/api/payments/alipay/pay', requestBody);
  return res.data;
};

export const confirmAlipayReturn = async (payload) => {
  const res = await api.post('/api/payments/alipay/confirm', payload);
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
