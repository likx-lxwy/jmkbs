<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, nextTick } from 'vue';
import {
  fetchCategories,
  fetchProducts,
  submitOrder,
  fetchLoginCaptcha,
  login,
  logout,
  createProduct,
  register,
  sendRegisterEmailCode,
  fetchAdminOverview,
  fetchAdminMerchants,
  updateMerchantStatus,
  fetchAdminOrders,
  uploadImage,
  fetchMyProducts,
  deleteProduct,
  updateProduct,
  fetchPendingOrders,
  approveOrder,
  rejectOrder,
  approveRefundAdmin,
  rejectRefundAdmin,
  uploadVideo,
  fetchComments,
  addComment,
  fetchProductIntro,
  getAiKey,
  saveAiKey,
  aiRecommend,
  getSettings,
  setApprovalLevel,
  getWallet,
  recharge,
  adminWallets,
  adminAdjustWallet,
  adminResetPassword,
  fetchAdminRevenue,
  fetchLoginLogs,
  fetchSystemLogs,
  fetchChat,
  sendChat,
  fetchRecentChat,
  runTerminal,
  fetchPaymentLogs,
  fetchMyOrders,
  refundOrder,
  paySubscription,
  reverseGeocode,
  getLocationConfig,
  fetchAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
  changeMyPassword,
  createAlipayPayUrl
} from './api';

const categories = ref([]);
const products = ref([]);
const activeCategory = ref('all');
const loadingProducts = ref(false);
const cart = ref([]);
const submitting = ref(false);

const notice = reactive({
  type: 'info',
  message: ''
});

const showLoginModal = ref(false);
const showRegisterModal = ref(false);

const orderForm = reactive({
  customerName: '',
  phone: '',
  address: ''
});

const auth = reactive({
  token: localStorage.getItem('mk_token') || '',
  username: localStorage.getItem('mk_username') || '',
  role: localStorage.getItem('mk_role') || '',
  merchantStatus: localStorage.getItem('mk_merchant_status') || ''
});

const loginForm = reactive({
  username: '',
  password: '',
  captchaCode: ''
});

const loginCaptcha = reactive({
  token: '',
  imageData: '',
  loading: false
});

const registerForm = reactive({
  username: '',
  password: '',
  email: '',
  emailCode: '',
  role: 'USER'
});

const registerEmailSending = ref(false);
const registerEmailCooldown = ref(0);
let registerEmailTimer = null;

const productForm = reactive({
  name: '',
  description: '',
  sizes: '',
  price: '',
  imageUrl: '',
  videoUrl: '',
  stock: 0,
  categoryId: '',
  file: null
});
// 结构化尺码库存
const productSizeRows = ref([]);
const selectedSizes = reactive({});
const sizePresets = {
  apparel: ['S', 'M', 'L', 'XL'],
  shoes: ['40', '41', '42'],
  hats: ['小码', '标准', '大码']
};
resetProductSizeRows();

const adminOverview = reactive({
  totalUsers: 0,
  totalMerchants: 0,
  pendingMerchants: 0,
  approvedMerchants: 0,
  totalOrders: 0,
  totalRevenue: 0,
  productCount: 0,
  lowStockCount: 0
});

function linePoints(values = []) {
  if (!values.length) return '0,40 100,40';
  const maxY = 40;
  return values
    .map((v, idx) => {
      const x = (idx / Math.max(1, values.length - 1)) * 100;
      const y = maxY - (Math.min(100, Math.max(0, v)) / 100) * maxY;
      return `${x},${y}`;
    })
    .join(' ');
}

const adminInsights = computed(() => {
  const totalMerchants = Number(adminOverview.totalMerchants || 0);
  const pending = Number(adminOverview.pendingMerchants || 0);
  const approved = Number(adminOverview.approvedMerchants || 0);
  const totalProducts = Number(adminOverview.productCount || 0);
  const lowStock = Number(adminOverview.lowStockCount || 0);
  const orders = Number(adminOverview.totalOrders || 0);
  const gmv = Number(adminOverview.totalRevenue || 0);
  const avgOrder = orders ? gmv / orders : 0;
  const pendingRate = totalMerchants ? Math.round((pending / totalMerchants) * 100) : 0;
  const approvedRate = totalMerchants ? Math.round((approved / totalMerchants) * 100) : 0;
  const lowStockRate = totalProducts ? Math.round((lowStock / totalProducts) * 100) : 0;

  return [
    {
      label: 'GMV',
      display: `￥${gmv.toFixed(2)}`,
      desc: `订单 ${orders}，客单价约 ￥${avgOrder.toFixed(2)}`,
      percent: Math.min(100, Math.round(Math.min(gmv, 100000) / 1000)),
      points: linePoints([0, Math.min(100, Math.round(Math.min(gmv, 100000) / 1000))])
    },
    {
      label: '商家审核通过率',
      display: `${approvedRate}%`,
      desc: `已通过 ${approved} / 总商家 ${totalMerchants}`,
      percent: approvedRate,
      points: linePoints([approvedRate * 0.6, approvedRate])
    },
    {
      label: '待审核商家占比',
      display: `${pendingRate}%`,
      desc: `待审核 ${pending} / 总商家 ${totalMerchants}`,
      percent: pendingRate,
      points: linePoints([pendingRate * 0.5, pendingRate])
    },
    {
      label: '库存风险占比',
      display: `${lowStockRate}%`,
      desc: `低库存 ${lowStock} / 商品 ${totalProducts}`,
      percent: lowStockRate,
      points: linePoints([lowStockRate * 0.5, lowStockRate])
    }
  ];
});

const adminMerchants = ref([]);
const adminOrders = ref([]);
const adminLoading = reactive({
  overview: false,
  merchants: false,
  orders: false,
  updating: false
});

const adminFilters = reactive({
  merchantStatus: 'PENDING'
});

const currentPage = ref(localStorage.getItem('mk_page') || 'catalog');
const sidebarCollapsed = ref(localStorage.getItem('mk_sidebar_collapsed') === '1');
const productPage = ref(1);
const productPageSize = ref(6);
const myProducts = ref([]);
const myProductPage = ref(1);
const myProductPageSize = ref(6);
const showEditModal = ref(false);
const editingProductOriginal = ref(null);
const walletBalance = ref(0);
const subscriptionUntil = ref(null);
const showRechargeModal = ref(false);
const rechargeAmount = ref(0);
const paymentLogs = ref([]);
const paymentLogsLoading = ref(false);
const pendingOrders = ref([]);
const approvalLevel = ref('LOW');
const adminWalletUsers = ref([]);
const adminRevenueLogs = ref([]);
const loginLogs = ref([]);
const loginLogPage = ref(1);
const loginLogSize = ref(10);
const loginLogTotal = computed(() => Math.max(1, Math.ceil(loginLogs.value.length / loginLogSize.value)));
const pagedLoginLogs = computed(() => {
  const start = (loginLogPage.value - 1) * loginLogSize.value;
  return loginLogs.value.slice(start, start + loginLogSize.value);
});
const systemLogs = ref('');
const systemLogLines = ref(200);
const recentChats = ref([]);
const showTerminalModal = ref(false);
const terminalCommand = ref('');
const terminalPassword = ref('');
const terminalSignature = ref('');
const terminalOutput = ref('');
const terminalUnlocked = ref(false);
const terminalPrompt = 'admin@mk:~$';
const pwdModalUser = ref(null);
const pwdModalValue = ref('');
const TERMINAL_SECRET = '962e9179607aa152eb7bd0598381bc9d440f9006289d3c6c0f16ce95ba92c58f';
const myOrders = ref([]);
const myOrdersLoading = ref(false);
const refundModal = reactive({
  orderId: null,
  reason: ''
});
const showRefundModal = ref(false);
const refundSubmitting = ref(false);
const refundError = ref('');
const useGeoLoading = ref(false);
const showMapModal = ref(false);
const mapApiKey = ref('');
const mapJsKey = ref('');
const mapJsSec = ref('');
const mapPickerLoading = ref(false);
const mapSelectedPoint = ref(null);
const mapSelectedAddress = ref('');
const mapApplyTarget = ref('order'); // 'order' | 'profile'
const alipayAppId = ref('');
const alipayPrivateKey = ref('');
const alipayPublicKey = ref('');
const alipayGateway = ref('https://openapi.alipaydev.com/gateway.do');
const alipayReturnUrl = ref('');
const alipayNotifyUrl = ref('');
let amapInitPromise = null;
let amapLoaded = false;
let amapMap = null;
let amapMarker = null;
let amapGeocoder = null;
const addressList = ref([]);
const addressForm = reactive({
  id: null,
  recipientName: '',
  phone: '',
  address: '',
  isDefault: false
});
const addressModal = ref(false);
const addressSubmitting = ref(false);
const selectedAddressId = ref(null);
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirm: ''
});
const passwordSubmitting = ref(false);
const showPayModal = ref(false);
const payModalAddressId = ref(null);
const payModalPayMethod = ref('ALIPAY');
const currentChat = reactive({
  productId: null,
  targetId: null,
  productName: '',
  targetName: '',
  messages: [],
  content: ''
});
const showChatPanel = ref(false);
const merchantPage = ref(1);
const merchantPageSize = ref(8);
const orderPage = ref(1);
const orderPageSize = ref(8);
const loginNotice = ref('');
const registerNotice = ref('');
const merchantSearch = ref('');
const orderSearch = ref('');
const lowStock = ref([]);
const lowStockLoading = ref(false);
const lowStockPage = ref(1);
const lowStockPageSize = ref(6);
const lowStockThreshold = ref(5);
const selectedMerchants = ref([]);
const uploadingImage = ref(false);
const editingProductId = ref(null);
const productFormVideoFile = ref(null);
const detailProduct = ref(null);
const detailComments = ref([]);
const detailIntro = ref('');
const detailLoading = ref(false);
const detailAiLoading = ref(false);
const detailCommentInput = ref('');
const detailShowAi = ref(false);
const aiInput = ref('');
const aiResult = ref('');
const aiLoading = ref(false);
const aiKey = ref('');
const aiError = ref('');
const aiMessages = ref([]);
const aiToast = reactive({ visible: false, message: '', type: 'info' });

const isLoggedIn = computed(() => !!auth.token);
const isMerchantApproved = computed(() => auth.role === 'MERCHANT' && auth.merchantStatus === 'APPROVED');
const isMerchantBlocked = computed(() => auth.role === 'MERCHANT' && auth.merchantStatus !== 'APPROVED');
const merchantBlockVariant = computed(() => (auth.merchantStatus === 'BANNED' ? 'BANNED' : 'PENDING'));
const hasPendingMerchantBadge = computed(() => isAdmin.value && Number(adminOverview.pendingMerchants || 0) > 0);
const isAdmin = computed(() => auth.role === 'ADMIN');
const merchantStatusLabel = computed(() => {
  switch (auth.merchantStatus) {
    case 'APPROVED':
      return '已审核';
    case 'PENDING':
      return '待审核';
    case 'UNREVIEWED':
      return '未审核';
    case 'BANNED':
      return '被封禁';
    default:
      return '普通用户';
  }
});

const cartCount = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0));
const cartTotal = computed(() =>
  cart.value.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0).toFixed(2)
);
const sizeTableStock = computed(() => {
  const rows = normalizeSizeRows();
  if (rows.length) {
    return rows.reduce((sum, s) => sum + Number(s.stock || 0), 0);
  }
  return Number(productForm.stock || 0);
});

const merchantSubActive = computed(() => {
  if (!subscriptionUntil.value) return false;
  return new Date(subscriptionUntil.value) >= new Date();
});
const pagedProducts = computed(() => {
  const start = (productPage.value - 1) * productPageSize.value;
  return products.value.slice(start, start + productPageSize.value);
});
const productTotalPages = computed(() => Math.max(1, Math.ceil(products.value.length / productPageSize.value || 1)));
const pagedMyProducts = computed(() => {
  const start = (myProductPage.value - 1) * myProductPageSize.value;
  return myProducts.value.slice(start, start + myProductPageSize.value);
});
const myProductTotalPages = computed(() => Math.max(1, Math.ceil(myProducts.value.length / myProductPageSize.value || 1)));

const pagedMerchants = computed(() => {
  const start = (merchantPage.value - 1) * merchantPageSize.value;
  return filteredMerchants.value.slice(start, start + merchantPageSize.value);
});
const merchantTotalPages = computed(() => Math.max(1, Math.ceil(filteredMerchants.value.length / merchantPageSize.value || 1)));

const pagedOrders = computed(() => {
  const start = (orderPage.value - 1) * orderPageSize.value;
  return filteredOrders.value.slice(start, start + orderPageSize.value);
});
const orderTotalPages = computed(() => Math.max(1, Math.ceil(filteredOrders.value.length / orderPageSize.value || 1)));
const filteredMerchants = computed(() => {
  const q = merchantSearch.value.trim().toLowerCase();
  if (!q) return adminMerchants.value;
  return adminMerchants.value.filter((m) => (m.username || '').toLowerCase().includes(q));
});
const filteredOrders = computed(() => {
  const q = orderSearch.value.trim().toLowerCase();
  if (!q) return adminOrders.value;
  return adminOrders.value.filter((o) => (o.orderNumber || '').toLowerCase().includes(q) || (o.customerName || '').toLowerCase().includes(q));
});
const pagedLowStock = computed(() => {
  const start = (lowStockPage.value - 1) * lowStockPageSize.value;
  return lowStock.value.slice(start, start + lowStockPageSize.value);
});
const lowStockTotalPages = computed(() => Math.max(1, Math.ceil(lowStock.value.length / lowStockPageSize.value || 1)));
const currentPageLabel = computed(() => {
  const map = {
    catalog: '商品',
    profile: '个人主页',
    orders: '订单',
    walletUser: '钱包',
    ai: 'AI 帮你选',
    chat: '聊天',
    merchantUpload: '上架商品',
    myShop: '我的店铺',
    adminOverview: '概览',
    adminMerchants: '商家审核',
    adminOrders: '订单管理',
    adminRevenue: '收益',
    adminStock: '库存告警',
    adminApproval: '交易审核',
    adminWallet: '钱包',
    adminLogs: '日志',
    adminTerminal: '终端',
    adminAi: 'AI 配置'
  };
  return map[currentPage.value] || '欢迎';
});

onMounted(() => {
  if (!isLoggedIn.value) {
    currentPage.value = 'catalog';
    localStorage.setItem('mk_page', 'catalog');
  }
  loadMapConfig();
  loadCategories();
  loadProducts();
  if (auth.role === 'MERCHANT') {
    loadMyProducts();
  }
  if (isAdmin.value) {
    loadAdminAll();
    loadApprovalSettings();
    loadPendingOrders();
    loadAdminWallets();
    loadLoginLogs();
    loadSystemLogs();
  }
  if (isLoggedIn.value) {
    loadWallet();
    loadPaymentLogs();
    loadRecentChats();
    loadAddresses();
  }
  if (isAdmin.value) {
    loadAiKey();
  }
});

onBeforeUnmount(() => {
  clearRegisterEmailTimer();
});

watch(isAdmin, (value) => {
  if (value) {
    currentPage.value = 'adminOverview';
    loadAdminAll();
  } else if (currentPage.value.startsWith('admin')) {
    currentPage.value = 'catalog';
  }
  localStorage.setItem('mk_page', currentPage.value);
});

async function loadMapConfig() {
  try {
    const cfg = await getLocationConfig();
    mapApiKey.value = cfg.mapApiKey || '';
    mapJsKey.value = cfg.mapJsKey || '';
    mapJsSec.value = cfg.mapJsSec || '';
    alipayAppId.value = cfg.alipayAppId || '';
    alipayPrivateKey.value = cfg.alipayPrivateKey || '';
    alipayPublicKey.value = cfg.alipayPublicKey || '';
    alipayGateway.value = cfg.alipayGateway || 'https://openapi.alipaydev.com/gateway.do';
    alipayReturnUrl.value = cfg.alipayReturnUrl || '';
    alipayNotifyUrl.value = cfg.alipayNotifyUrl || '';
  } catch (e) {
    // ignore
  }
}

async function loadCategories() {
  try {
    categories.value = await fetchCategories();
  } catch (err) {
    setNotice('error', '无法加载分类，请检查后端服务');
  }
}

async function loadProducts(categoryId = null) {
  loadingProducts.value = true;
  try {
    products.value = await fetchProducts(categoryId || undefined);
  } catch (err) {
    setNotice('error', '无法加载商品列表');
  } finally {
    loadingProducts.value = false;
  }
}

async function loadMyProducts() {
  if (!isLoggedIn.value || auth.role !== 'MERCHANT') return;
  try {
    myProducts.value = await fetchMyProducts();
    myProductPage.value = 1;
  } catch (err) {
    setNotice('error', '无法加载我的商品');
  }
}

async function loadAdminAll() {
  await Promise.all([loadAdminOverview(), loadAdminMerchants(adminFilters.merchantStatus), loadAdminOrders()]);
}

async function loadAdminOverview() {
  adminLoading.overview = true;
  try {
    const data = await fetchAdminOverview();
    Object.assign(adminOverview, data || {});
  } catch (err) {
    setNotice('error', '管理员概览加载失败');
  } finally {
    adminLoading.overview = false;
  }
}

async function loadAdminMerchants(status = adminFilters.merchantStatus) {
  adminLoading.merchants = true;
  try {
    const list = await fetchAdminMerchants(status);
    adminMerchants.value = list || [];
    adminFilters.merchantStatus = status || '';
  } catch (err) {
    setNotice('error', '商家列表加载失败');
  } finally {
    adminLoading.merchants = false;
  }
}

async function loadAdminOrders() {
  adminLoading.orders = true;
  try {
    adminOrders.value = await fetchAdminOrders();
    orderPage.value = 1;
  } catch (err) {
    setNotice('error', '订单列表加载失败');
  } finally {
    adminLoading.orders = false;
  }
}

async function loadPendingOrders() {
  try {
    pendingOrders.value = await fetchPendingOrders();
  } catch (err) {
    setNotice('error', '待审核订单加载失败');
  }
}

async function loadApprovalSettings() {
  try {
    const s = await getSettings();
    approvalLevel.value = (s?.orderApprovalLevel || 'LOW').toUpperCase();
  } catch (err) {
    approvalLevel.value = 'LOW';
  }
}

async function saveApprovalLevel(level) {
  try {
    const s = await setApprovalLevel(level);
    approvalLevel.value = (s?.orderApprovalLevel || level).toUpperCase();
    setNotice('success', '审核档位已更新');
    await loadPendingOrders();
  } catch (err) {
    setNotice('error', '保存审核档位失败');
  }
}

async function approvePending(id, pass) {
  try {
    if (pass) {
      await approveOrder(id);
      setNotice('success', '已通过');
    } else {
      await rejectOrder(id);
      setNotice('info', '已拒绝并退款');
    }
    await Promise.all([loadPendingOrders(), loadAdminOrders()]);
  } catch (err) {
    const msg = err?.response?.data?.message || '操作失败';
    setNotice('error', msg);
  }
}

async function approveRefundAdminAction(id, pass) {
  try {
    if (pass) {
      await approveRefundAdmin(id);
      setNotice('success', '退款已同意');
    } else {
      await rejectRefundAdmin(id);
      setNotice('info', '已驳回退款');
    }
    await loadAdminOrders();
  } catch (err) {
    const msg = err?.response?.data?.message || '操作失败';
    setNotice('error', msg);
  }
}

async function loadWallet() {
  try {
    const res = await getWallet();
    walletBalance.value = res.balance || 0;
    subscriptionUntil.value = res.subscriptionPaidUntil || null;
  } catch (err) {
    // ignore
  }
}

async function loadPaymentLogs() {
  if (!isLoggedIn.value) return;
  paymentLogsLoading.value = true;
  try {
    paymentLogs.value = await fetchPaymentLogs();
  } catch (err) {
    setNotice('error', '支付记录加载失败');
  } finally {
    paymentLogsLoading.value = false;
  }
}

async function loadMyOrders() {
  if (!isLoggedIn.value) return;
  myOrdersLoading.value = true;
  try {
    myOrders.value = await fetchMyOrders();
  } catch (e) {
    setNotice('error', '订单加载失败');
  } finally {
    myOrdersLoading.value = false;
  }
}

async function doRecharge() {
  if (!rechargeAmount.value || Number(rechargeAmount.value) <= 0) {
    setNotice('warning', '请输入大于0的金额');
    return;
  }
  try {
    await recharge(Number(rechargeAmount.value));
    setNotice('success', '充值成功');
    await Promise.all([loadWallet(), loadPaymentLogs()]);
    showRechargeModal.value = false;
    rechargeAmount.value = 0;
  } catch (err) {
    const msg = err?.response?.data?.message || '充值失败';
    setNotice('error', msg);
  }
}

async function loadAdminWallets() {
  if (!isAdmin.value) return;
  try {
    adminWalletUsers.value = await adminWallets();
  } catch (err) {
    // ignore
  }
}

async function adjustUserWallet(userId, delta) {
  try {
    await adminAdjustWallet(userId, Number(delta));
    setNotice('success', '余额已调整');
    await loadAdminWallets();
  } catch (err) {
    const msg = err?.response?.data?.message || '调整失败';
    setNotice('error', msg);
  }
}

async function resetUserPassword(userId, password) {
  try {
    await adminResetPassword(userId, password);
    setNotice('success', '密码已修改');
  } catch (err) {
    const msg = err?.response?.data?.message || '修改失败';
    setNotice('error', msg);
  }
}

async function loadAdminRevenue() {
  if (!isAdmin.value) return;
  try {
    adminRevenueLogs.value = await fetchAdminRevenue();
  } catch (err) {
    setNotice('error', '收益记录加载失败');
  }
}

async function payMerchantSubscription() {
  try {
    const res = await paySubscription();
    walletBalance.value = res.balance || walletBalance.value;
    subscriptionUntil.value = res.subscriptionPaidUntil || subscriptionUntil.value;
    setNotice('success', '开店费已缴纳，期限已更新');
  } catch (err) {
    const msg = err?.response?.data?.message || '缴费失败';
    setNotice('error', msg);
  }
}

async function loadLoginLogs() {
  if (!isAdmin.value) return;
  try {
    loginLogs.value = await fetchLoginLogs();
    loginLogPage.value = 1;
  } catch (e) {
    // ignore
  }
}

async function loadSystemLogs() {
  if (!isAdmin.value) return;
  try {
    systemLogs.value = await fetchSystemLogs(systemLogLines.value || 200);
  } catch (e) {
    systemLogs.value = '读取失败';
  }
}

function downloadSystemLogs() {
  if (!isAdmin.value) return;
  fetchSystemLogs(systemLogLines.value || 200)
    .then((text) => {
      const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `system-logs-${Date.now()}.log`;
      a.click();
      URL.revokeObjectURL(url);
    })
    .catch(() => {
      setNotice('error', '下载失败，可能未登录或无权限');
    });
}
async function loadLowStock() {
  lowStockLoading.value = true;
  try {
    const data = await fetchProducts();
    const threshold = Number(lowStockThreshold.value || 0);
    lowStock.value = (data || []).filter((p) => Number(p.stock || 0) <= threshold);
    lowStockPage.value = 1;
  } catch (err) {
    setNotice('error', '库存告警加载失败');
  } finally {
    lowStockLoading.value = false;
  }
}

async function changeMerchantStatus(id, status) {
  if (!isAdmin.value) return;
  adminLoading.updating = true;
  try {
    await updateMerchantStatus(id, { status });
    setNotice('success', '商家状态已更新');
    await Promise.all([loadAdminOverview(), loadAdminMerchants(adminFilters.merchantStatus)]);
  } catch (err) {
    const msg = err?.response?.data?.message || '更新失败';
    setNotice('error', msg);
  } finally {
    adminLoading.updating = false;
  }
}

function renderStatusLabel(status) {
  switch ((status || '').toUpperCase()) {
    case 'APPROVED':
      return '已审核';
    case 'PENDING':
      return '待审核';
    case 'UNREVIEWED':
      return '未审核';
    case 'BANNED':
      return '被封禁';
    default:
      return '普通';
  }
}

function formatOrderStatus(status) {
  switch ((status || '').toUpperCase()) {
    case 'PENDING_PAYMENT':
      return '由支付宝托管';
    case 'PLACED':
      return '已下单（站内钱包）';
    case 'PENDING_ADMIN':
      return '待管理员审批（站内钱包）';
    case 'APPROVED':
      return '已完成（站内钱包）';
    case 'REFUND_REQUESTED':
      return '退款待审核（站内钱包）';
    case 'REJECTED':
      return '已拒绝（站内钱包）';
    case 'REFUNDED':
      return '已退款（站内钱包）';
    default:
      return status ? `${status}（站内钱包）` : '-';
  }
}

function formatOrderStatusWithPayMethod(status, payMethod) {
  const normalizedStatus = (status || '').toUpperCase();
  const normalizedPayMethod = (payMethod || '').toUpperCase();
  const payMethodLabel = normalizedPayMethod === 'ALIPAY'
    ? '支付宝支付'
    : normalizedPayMethod === 'WALLET'
      ? '站内钱包支付'
      : normalizedStatus === 'PENDING_PAYMENT'
        ? '支付宝支付'
        : '未知支付方式';
  const suffix = `（${payMethodLabel}）`;

  switch (normalizedStatus) {
    case 'PENDING_PAYMENT':
      return `待支付${suffix}`;
    case 'PLACED':
      return `已下单${suffix}`;
    case 'PENDING_ADMIN':
      return `待管理员审批${suffix}`;
    case 'APPROVED':
      return `已完成${suffix}`;
    case 'REFUND_REQUESTED':
      return `退款待审核${suffix}`;
    case 'REJECTED':
      return `已拒绝${suffix}`;
    case 'REFUNDED':
      return `已退款${suffix}`;
    default:
      return status ? `${status}${suffix}` : '-';
  }
}

function canRefund(status) {
  const s = (status || '').toUpperCase();
  // 仅禁止已进入退款流程或已拒绝/已退款的单，其余允许弹窗申请
  return !['REFUND_REQUESTED', 'REFUNDED', 'REJECTED'].includes(s);
}

function submitAlipayForm(payResp) {
  if (!payResp || !payResp.gateway || !payResp.params) {
    alert('未获取到支付参数');
    return;
  }
  const form = document.createElement('form');
  form.method = 'POST';
  const gateway = payResp.gateway.includes('?') ? payResp.gateway : `${payResp.gateway}?_input_charset=utf-8`;
  form.action = gateway;
  Object.entries(payResp.params).forEach(([k, v]) => {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = k;
    input.value = v;
    form.appendChild(input);
  });
  document.body.appendChild(form);
  form.submit();
  document.body.removeChild(form);
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function formatPaymentType(type) {
  switch ((type || '').toUpperCase()) {
    case 'PAY':
      return '支付';
    case 'INCOME':
      return '收入';
    case 'REFUND':
      return '退款';
    case 'RECHARGE':
      return '充值';
    case 'ADJUST':
      return '调整';
    default:
      return type || '-';
  }
}

function selectCategory(categoryId) {
  activeCategory.value = categoryId ?? 'all';
  productPage.value = 1;
  loadProducts(categoryId === 'all' ? null : categoryId);
}

function addToCart(product, explicitSize) {
  if (!product) return;
  const sizeList = product.sizesDetail || [];
  let sizeLabel = explicitSize ?? selectedSizes[product.id] ?? '';
  if (sizeList.length && !sizeLabel) {
    const firstAvail = sizeList.find((s) => Number(s.stock || 0) > 0) || sizeList[0];
    sizeLabel = firstAvail?.label || '';
  }
  if (sizeList.length && !sizeLabel) {
    setNotice('warning', '请选择尺码后再加入购物车');
    openProductDetail(product);
    return;
  }
  const availableStock = sizeList.length ? sizeStockOf(product, sizeLabel) : Number(product.stock || 0);
  if (availableStock <= 0) {
    setNotice('warning', sizeList.length ? '该尺码暂无库存' : '库存不足');
    return;
  }
  const key = `${product.id}__${sizeLabel || '默认'}`;
  const existing = cart.value.find((item) => item.key === key);
  if (existing) {
    if (existing.quantity < availableStock) {
      existing.quantity += 1;
    } else {
      setNotice('warning', '已达库存上限');
    }
  } else {
    cart.value.push({ ...product, quantity: 1, sizeLabel, key, stock: availableStock });
  }
}

function updateQuantity(key, delta) {
  const item = cart.value.find((i) => i.key === key);
  if (!item) return;
  const next = item.quantity + delta;
  if (next <= 0) {
    cart.value = cart.value.filter((i) => i.key !== key);
  } else if (next > item.stock) {
    setNotice('warning', '已达库存上限');
  } else {
    item.quantity = next;
  }
}

function removeFromCart(key) {
  cart.value = cart.value.filter((i) => i.key !== key);
}

function addProductFromAi(productId) {
  const pid = Number(productId);
  if (Number.isNaN(pid)) {
    setNotice('warning', '商品编号无效');
    return;
  }
  const product = products.value.find((p) => p.id === pid);
  if (!product) {
    setNotice('warning', `未找到商品 #${pid}`);
    return;
  }
  const defaultSize = (product.sizesDetail || []).find((s) => Number(s.stock || 0) > 0)?.label;
  addToCart(product, defaultSize);
  showAiToast(`已加入购物车：${product.name}`, 'success');
}

function findProductsInContent(content) {
  if (!content) return [];
  const ids = [];
  const reg = /编号#(\d+)/g;
  let m;
  while ((m = reg.exec(content)) !== null) {
    const id = Number(m[1]);
    if (!Number.isNaN(id) && !ids.includes(id)) {
      ids.push(id);
    }
  }
  return ids
    .map((id) => products.value.find((p) => p.id === id))
    .filter(Boolean);
}

const aiSuggestions = computed(() => {
  const msgs = [...aiMessages.value].reverse();
  const lastAssistant = msgs.find((m) => m.role === 'assistant');
  if (!lastAssistant) return [];
  return findProductsInContent(lastAssistant.content);
});

function showAiToast(message, type = 'info') {
  aiToast.message = message;
  aiToast.type = type;
  aiToast.visible = true;
  setTimeout(() => {
    aiToast.visible = false;
  }, 2000);
}

async function openProductDetail(product) {
  detailProduct.value = product;
  detailComments.value = [];
  detailIntro.value = '';
  detailLoading.value = true;
  detailShowAi.value = !product.videoUrl;
   if (product?.sizesDetail?.length) {
    const first = product.sizesDetail.find((s) => Number(s.stock || 0) > 0) || product.sizesDetail[0];
    if (!selectedSizes[product.id]) {
      selectedSizes[product.id] = first?.label || '';
    }
  }
  if (!products.value.length) {
    await loadProducts();
  }
  try {
    detailComments.value = await fetchComments(product.id);
  } catch (e) {
    // ignore
  }
  if (!product.videoUrl) {
    detailAiLoading.value = true;
    try {
      const res = await fetchProductIntro(product.id);
      detailIntro.value = res.result || '';
    } catch (e) {
      detailIntro.value = 'AI 介绍暂不可用';
    } finally {
      detailAiLoading.value = false;
    }
  }
  detailLoading.value = false;
}

function closeProductDetail() {
  detailProduct.value = null;
  detailComments.value = [];
  detailIntro.value = '';
  detailShowAi.value = false;
}

async function refreshAiIntro() {
  if (!detailProduct.value) return;
  detailAiLoading.value = true;
  try {
    const res = await fetchProductIntro(detailProduct.value.id);
    detailIntro.value = res.result || '';
  } catch (e) {
    detailIntro.value = 'AI 介绍暂不可用';
  } finally {
    detailAiLoading.value = false;
  }
}

async function addProductComment() {
  if (!detailProduct.value || !detailProduct.value.id) return;
  if (!isLoggedIn.value) {
    setNotice('warning', '请先登录');
    return;
  }
  if (!detailCommentInput.value.trim()) {
    setNotice('warning', '请输入评论内容');
    return;
  }
  try {
    const res = await addComment(detailProduct.value.id, detailCommentInput.value.trim());
    detailComments.value = [...detailComments.value, res];
    detailCommentInput.value = '';
  } catch (e) {
    const msg = e?.response?.data?.message || '发表评论失败';
    setNotice('error', msg);
  }
}

async function checkout() {
  if (!cart.value.length) {
    setNotice('warning', '请先选择商品');
    return;
  }
  if (!isLoggedIn.value) {
    setNotice('warning', '请先登录后下单');
    return;
  }
  if (!addressList.value.length) {
    await loadAddresses();
  }
  if (!payModalAddressId.value) {
    setNotice('warning', '请先选择收货地址');
    alert('请在地址簿中选择地址后再下单');
    return;
  }
  const addr = addressList.value.find((a) => a.id === payModalAddressId.value);
  if (!addr) {
    setNotice('warning', '所选地址无效，请刷新');
    return;
  }
  selectedAddressId.value = addr.id;
  orderForm.customerName = addr.recipientName;
  orderForm.phone = addr.phone;
  orderForm.address = addr.address;
  const name = (orderForm.customerName || '').trim();
  const phone = (orderForm.phone || '').trim();
  const address = (orderForm.address || '').trim();
  if (!name || !phone || !address) {
    setNotice('warning', '请填写收货人、电话和地址');
    return;
  }
  const cnMobile = /^1[3-9]\d{9}$/;
  if (!cnMobile.test(phone)) {
    setNotice('warning', '请输入有效的大陆手机号（11位数字，以1开头）');
    alert('请输入有效的大陆手机号（11位数字，以1开头）');
    return;
  }

  submitting.value = true;
  try {
    if (!addressList.value.length) {
      await loadAddresses();
    }
    if (!addressList.value.length) {
      throw new Error('请先添加收货地址');
    }
    const payload = {
      customerName: name,
      phone,
      address,
      payMethod: payModalPayMethod.value,
      addressId: selectedAddressId.value,
      items: cart.value.map((item) => ({
        productId: item.id,
        quantity: item.quantity,
        sizeLabel: item.sizeLabel || ''
      }))
    };
    const res = await submitOrder(payload);
    if (payModalPayMethod.value === 'ALIPAY') {
      setNotice('success', `订单已创建，正在跳转支付宝支付`);
      const payResp = await createAlipayPayUrl(res.orderId || res.id);
      submitAlipayForm(payResp);
    } else {
      setNotice('success', `钱包支付成功，订单号 ${res.orderNumber}`);
    }
    cart.value = [];
    await Promise.all([loadMyOrders(), loadWallet(), loadPaymentLogs()]);
  } catch (err) {
    const msg = err?.response?.data?.message || '下单失败，请稍后重试';
    setNotice('error', msg);
  } finally {
    submitting.value = false;
    showPayModal.value = false;
  }
}

function openAddressModal(address = null) {
  if (address) {
    addressForm.id = address.id;
    addressForm.recipientName = address.recipientName;
    addressForm.phone = address.phone;
    addressForm.address = address.address;
    addressForm.isDefault = address.default;
  } else {
    addressForm.id = null;
    addressForm.recipientName = '';
    addressForm.phone = '';
    addressForm.address = '';
    addressForm.isDefault = addressList.value.length === 0;
  }
  addressModal.value = true;
}

async function saveAddress() {
  if (addressSubmitting.value) return;
  addressSubmitting.value = true;
  try {
    const payload = {
      recipientName: addressForm.recipientName,
      phone: addressForm.phone,
      address: addressForm.address,
      default: addressForm.isDefault
    };
    if (addressForm.id) {
      await updateAddress(addressForm.id, payload);
    } else {
      await createAddress(payload);
    }
    setNotice('success', '地址已保存');
    addressModal.value = false;
    await loadAddresses();
  } catch (err) {
    const msg = err?.response?.data?.message || '保存地址失败';
    setNotice('error', msg);
  } finally {
    addressSubmitting.value = false;
  }
}

async function removeAddress(id) {
  if (!id) return;
  try {
    await deleteAddress(id);
    await loadAddresses();
    setNotice('success', '已删除地址');
  } catch (err) {
    const msg = err?.response?.data?.message || '删除失败';
    setNotice('error', msg);
  }
}

async function markDefaultAddress(id) {
  try {
    await setDefaultAddress(id);
    await loadAddresses();
    setNotice('success', '已设为默认地址');
  } catch (err) {
    const msg = err?.response?.data?.message || '设置失败';
    setNotice('error', msg);
  }
}

function chooseAddress(id) {
  selectedAddressId.value = id;
  const addr = addressList.value.find((a) => a.id === id);
  if (addr) {
    orderForm.customerName = addr.recipientName;
    orderForm.phone = addr.phone;
    orderForm.address = addr.address;
  }
}

async function changePassword() {
  if (passwordSubmitting.value) return;
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    setNotice('warning', '请填写旧密码和新密码');
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirm) {
    setNotice('warning', '两次输入的新密码不一致');
    return;
  }
  passwordSubmitting.value = true;
  try {
    await changeMyPassword(passwordForm.oldPassword, passwordForm.newPassword);
    setNotice('success', '密码已修改，请使用新密码登录');
    passwordForm.oldPassword = '';
    passwordForm.newPassword = '';
    passwordForm.confirm = '';
  } catch (err) {
    const msg = err?.response?.data?.message || '修改密码失败';
    setNotice('error', msg);
  } finally {
    passwordSubmitting.value = false;
  }
}

async function loadAddresses() {
  if (!isLoggedIn.value) return;
  try {
    addressList.value = await fetchAddresses();
    if (addressList.value.length) {
      const def = addressList.value.find((a) => a.default) || addressList.value[0];
      selectedAddressId.value = def.id;
      orderForm.customerName = def.recipientName;
      orderForm.phone = def.phone;
      orderForm.address = def.address;
    } else {
      selectedAddressId.value = null;
    }
  } catch (err) {
    setNotice('error', '无法加载地址簿');
  }
}

function setNotice(type, message) {
  notice.type = type;
  notice.message = message;
  setTimeout(() => {
    if (notice.message === message) {
      notice.message = '';
    }
  }, 2600);
}

async function loadLoginCaptcha() {
  loginCaptcha.loading = true;
  try {
    const res = await fetchLoginCaptcha();
    loginCaptcha.token = res.captchaToken || '';
    loginCaptcha.imageData = res.imageData || '';
    loginForm.captchaCode = '';
  } catch (err) {
    loginCaptcha.token = '';
    loginCaptcha.imageData = '';
    loginNotice.value = '验证码加载失败，请点击换一张重试';
  } finally {
    loginCaptcha.loading = false;
  }
}

async function openLoginModal() {
  showRegisterModal.value = false;
  registerNotice.value = '';
  loginNotice.value = '';
  showLoginModal.value = true;
  await loadLoginCaptcha();
}

function closeLoginModal() {
  showLoginModal.value = false;
  loginNotice.value = '';
  loginForm.captchaCode = '';
  loginCaptcha.token = '';
  loginCaptcha.imageData = '';
}

function openRegisterModal() {
  showLoginModal.value = false;
  loginNotice.value = '';
  registerNotice.value = '';
  registerForm.emailCode = '';
  showRegisterModal.value = true;
}

async function openPayModal() {
  if (!addressList.value.length) {
    await loadAddresses();
  }
  if (!addressList.value.length) {
    setNotice('warning', '请先添加收货地址');
    return;
  }
  const targetId = selectedAddressId.value || addressList.value[0]?.id;
  payModalAddressId.value = targetId;
  payModalPayMethod.value = 'ALIPAY';
  if (targetId) {
    selectedAddressId.value = targetId;
    chooseAddress(targetId);
  }
  showPayModal.value = true;
}

function closeRegisterModal() {
  showRegisterModal.value = false;
  registerNotice.value = '';
  registerForm.emailCode = '';
  clearRegisterEmailTimer();
  registerEmailCooldown.value = 0;
}

function clearRegisterEmailTimer() {
  if (registerEmailTimer) {
    clearInterval(registerEmailTimer);
    registerEmailTimer = null;
  }
}

function startRegisterEmailCooldown(seconds = 60) {
  clearRegisterEmailTimer();
  registerEmailCooldown.value = seconds;
  registerEmailTimer = setInterval(() => {
    registerEmailCooldown.value = Math.max(0, registerEmailCooldown.value - 1);
    if (registerEmailCooldown.value <= 0) {
      clearRegisterEmailTimer();
    }
  }, 1000);
}

async function requestRegisterCode() {
  const email = (registerForm.email || '').trim().toLowerCase();
  const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  if (!emailPattern.test(email)) {
    registerNotice.value = '请输入有效的邮箱地址';
    return;
  }
  if (registerEmailCooldown.value > 0 || registerEmailSending.value) {
    return;
  }
  registerEmailSending.value = true;
  try {
    const res = await sendRegisterEmailCode(email);
    registerForm.email = email;
    registerForm.emailCode = '';
    registerNotice.value = '';
    setNotice('success', '邮箱验证码已发送，请注意查收');
    startRegisterEmailCooldown(Number(res?.cooldownSeconds || 60));
  } catch (err) {
    const msg = err?.response?.data?.message || '邮箱验证码发送失败';
    registerNotice.value = msg;
  } finally {
    registerEmailSending.value = false;
  }
}

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    loginNotice.value = '请输入账号和密码';
    return;
  }
  if (!loginForm.captchaCode) {
    loginNotice.value = '请输入图片验证码';
    return;
  }
  if (!loginCaptcha.token) {
    loginNotice.value = '验证码已失效，请刷新后重试';
    await loadLoginCaptcha();
    return;
  }
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password,
      captchaToken: loginCaptcha.token,
      captchaCode: loginForm.captchaCode
    });
    auth.token = res.token;
    auth.username = res.username;
    auth.role = res.role;
    auth.merchantStatus = res.merchantStatus || '';
    localStorage.setItem('mk_token', res.token);
    localStorage.setItem('mk_username', res.username);
    localStorage.setItem('mk_role', res.role);
    localStorage.setItem('mk_merchant_status', auth.merchantStatus);
    setNotice('success', `欢迎回来，${res.username}`);
    loginNotice.value = '';
    closeLoginModal();
    if (auth.role === 'MERCHANT') {
      await loadMyProducts();
    }
    await loadWallet();
    await loadPaymentLogs();
  } catch (err) {
    const msg = err?.response?.data?.message || '登录失败';
    loginNotice.value = msg;
    await loadLoginCaptcha();
  }
}

async function handleLogout() {
  try {
    await logout();
  } catch (e) {
    // ignore network errors on logout
  }
  auth.token = '';
  auth.username = '';
  auth.role = '';
  auth.merchantStatus = '';
  subscriptionUntil.value = null;
  myProducts.value = [];
  myProductPage.value = 1;
  // 清空管理端数据缓存
  Object.assign(adminOverview, {
    totalUsers: 0,
    totalMerchants: 0,
    pendingMerchants: 0,
    approvedMerchants: 0,
    totalOrders: 0,
    totalRevenue: 0,
    productCount: 0,
    lowStockCount: 0
  });
  adminMerchants.value = [];
  adminOrders.value = [];
  adminFilters.merchantStatus = 'PENDING';
  localStorage.removeItem('mk_token');
  localStorage.removeItem('mk_username');
  localStorage.removeItem('mk_role');
  localStorage.removeItem('mk_merchant_status');
  setNotice('info', '已退出登录');
  walletBalance.value = 0;
  paymentLogs.value = [];
  myOrders.value = [];
  window.location.href = '/';
}

async function saveProduct() {
  if (!isLoggedIn.value) {
    setNotice('warning', '请先登录');
    return;
  }
  if (!isMerchantApproved.value && !isAdmin.value) {
    setNotice('warning', '仅管理员或已审核商家可操作');
    return;
  }
  const normalizedSizes = normalizeSizeRows();
  const hasSizeDetail = normalizedSizes.length > 0;
  const computedStock = hasSizeDetail
    ? normalizedSizes.reduce((sum, s) => sum + Number(s.stock || 0), 0)
    : Number(productForm.stock || 0);
  if (
    !productForm.name.trim() ||
    productForm.price === '' ||
    Number(productForm.price) < 0 ||
    Number.isNaN(Number(productForm.price)) ||
    Number.isNaN(computedStock) ||
    computedStock < 0 ||
    !productForm.categoryId
  ) {
    setNotice('warning', '请填写名称、价格、库存并选择分类');
    return;
  }
  if (!productForm.imageUrl && !productForm.file) {
    setNotice('warning', '请上传商品图片');
    return;
  }
  try {
    if (productForm.file) {
      uploadingImage.value = true;
      const res = await uploadImage(productForm.file);
      productForm.imageUrl = res.url;
    }
    if (productFormVideoFile.value) {
      const resVideo = await uploadVideo(productFormVideoFile.value);
      productForm.videoUrl = resVideo.url;
    }
    const payload = {
      name: productForm.name,
      description: productForm.description,
      sizes: productForm.sizes,
      price: Number(productForm.price || 0),
      imageUrl: productForm.imageUrl,
      videoUrl: productForm.videoUrl,
      stock: computedStock,
      categoryId: Number(productForm.categoryId),
      sizesDetail: normalizedSizes
    };
    if (editingProductId.value) {
      await updateProduct(editingProductId.value, payload);
      setNotice('success', '商品已更新');
    } else {
      await createProduct(payload);
      setNotice('success', '商品已保存');
    }
    productForm.name = '';
    productForm.description = '';
    productForm.sizes = '';
    productForm.price = '';
    productForm.imageUrl = '';
    productForm.videoUrl = '';
    productForm.stock = 0;
    productForm.categoryId = '';
    productForm.file = null;
    productFormVideoFile.value = null;
    editingProductId.value = null;
    productForm.videoUrl = '';
    resetProductSizeRows();
    showEditModal.value = false;
    await loadProducts(activeCategory.value === 'all' ? null : activeCategory.value);
    await loadMyProducts();
  } catch (err) {
    const msg = err?.response?.data?.message || '保存失败';
    setNotice('error', msg);
  } finally {
    uploadingImage.value = false;
  }
}

function onVideoChange(event) {
  const file = event.target.files?.[0];
  if (file) {
    productFormVideoFile.value = file;
    showAiToast('已选择视频文件', 'info');
  }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.password) {
    registerNotice.value = '请输入账号和密码';
    return;
  }
  try {
    const res = await register({
      username: registerForm.username,
      password: registerForm.password,
      role: registerForm.role
    });
    auth.token = res.token;
    auth.username = res.username;
    auth.role = res.role;
    auth.merchantStatus = res.merchantStatus || '';
    localStorage.setItem('mk_token', res.token);
    localStorage.setItem('mk_username', res.username);
    localStorage.setItem('mk_role', res.role);
    localStorage.setItem('mk_merchant_status', auth.merchantStatus);
    setNotice('success', registerForm.role === 'MERCHANT' ? '注册成功，待审核后可上架' : '注册成功');
    registerNotice.value = '';
    closeRegisterModal();
    closeLoginModal();
    if (auth.role === 'MERCHANT') {
      await loadMyProducts();
    }
    await loadWallet();
    await loadPaymentLogs();
  } catch (err) {
    const msg = err?.response?.data?.message || '注册失败';
    registerNotice.value = msg;
  }
}

async function handleRegisterWithEmail() {
  if (!registerForm.username || !registerForm.password) {
    registerNotice.value = '请输入账号和密码';
    return;
  }
  if (!registerForm.email) {
    registerNotice.value = '请输入邮箱地址';
    return;
  }
  if (!registerForm.emailCode) {
    registerNotice.value = '请输入邮箱验证码';
    return;
  }
  try {
    const res = await register({
      username: registerForm.username,
      password: registerForm.password,
      email: registerForm.email,
      emailCode: registerForm.emailCode,
      role: registerForm.role
    });
    auth.token = res.token;
    auth.username = res.username;
    auth.role = res.role;
    auth.merchantStatus = res.merchantStatus || '';
    localStorage.setItem('mk_token', res.token);
    localStorage.setItem('mk_username', res.username);
    localStorage.setItem('mk_role', res.role);
    localStorage.setItem('mk_merchant_status', auth.merchantStatus);
    setNotice('success', registerForm.role === 'MERCHANT' ? '注册成功，待审核后可上架' : '注册成功');
    registerNotice.value = '';
    registerForm.emailCode = '';
    closeRegisterModal();
    closeLoginModal();
    if (auth.role === 'MERCHANT') {
      await loadMyProducts();
    }
    await loadWallet();
    await loadPaymentLogs();
  } catch (err) {
    const msg = err?.response?.data?.message || '注册失败';
    registerNotice.value = msg;
  }
}

function go(page) {
  if (!isLoggedIn.value && page !== 'catalog') {
    setNotice('warning', '请先登录后再访问其他功能');
    currentPage.value = 'catalog';
    return;
  }
  currentPage.value = page;
  localStorage.setItem('mk_page', page);
  if (isAdmin.value) {
    if (page === 'adminMerchants' && !adminMerchants.value.length) {
      loadAdminMerchants(adminFilters.merchantStatus);
    }
    if (page === 'adminOrders' && !adminOrders.value.length) {
      loadAdminOrders();
    }
    if (page === 'adminStock' && !lowStock.value.length) {
      loadLowStock();
    }
    if (page === 'adminApproval') {
      loadApprovalSettings();
      loadPendingOrders();
    }
    if (page === 'adminWallet') {
      loadAdminWallets();
    }
    if (page === 'adminLogs') {
      loadLoginLogs();
      loadSystemLogs();
    }
    if (page === 'adminRevenue') {
      loadAdminRevenue();
    }
    if (page === 'adminTerminal') {
      terminalOutput.value = '';
      terminalCommand.value = '';
      terminalPassword.value = '';
      terminalSignature.value = '';
    }
  } else if (auth.role === 'MERCHANT' && page === 'merchantUpload') {
    editingProductId.value = null;
    productForm.name = '';
    productForm.description = '';
    productForm.sizes = '';
    productForm.price = '';
    productForm.imageUrl = '';
    productForm.videoUrl = '';
    productForm.stock = 0;
    productForm.categoryId = '';
    productForm.file = null;
    productFormVideoFile.value = null;
    resetProductSizeRows();
  } else if (auth.role === 'MERCHANT' && page === 'myShop') {
    if (!myProducts.value.length) {
      loadMyProducts();
    }
    loadWallet();
  } else if (page === 'chat') {
    loadRecentChats();
  } else if (page === 'orders') {
    loadMyOrders();
  } else if (page === 'walletUser') {
    loadWallet();
    loadMyOrders();
    loadPaymentLogs();
  } else if (page === 'profile') {
    loadAddresses();
  } else if (page === 'ai') {
    aiResult.value = '';
    aiError.value = '';
    if (!products.value.length) {
      loadProducts();
    }
  } else if (page === 'adminAi') {
    loadAiKey();
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  localStorage.setItem('mk_sidebar_collapsed', sidebarCollapsed.value ? '1' : '0');
}

function changeProductPage(delta) {
  updatePage(productPage, productTotalPages, delta);
}

function changeMyProductPage(delta) {
  updatePage(myProductPage, myProductTotalPages, delta);
}

function changeMerchantPage(delta) {
  updatePage(merchantPage, merchantTotalPages, delta);
}

function changeOrderPage(delta) {
  updatePage(orderPage, orderTotalPages, delta);
}

function changeLowStockPage(delta) {
  updatePage(lowStockPage, lowStockTotalPages, delta);
}

function updatePage(stateRef, totalRef, delta) {
  const max = totalRef.value || 1;
  const next = stateRef.value + delta;
  if (next >= 1 && next <= max) {
    stateRef.value = next;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}

function toggleSelectAllMerchants(checked) {
  const ids = pagedMerchants.value.map((m) => m.id);
  if (checked) {
    selectedMerchants.value = Array.from(new Set([...selectedMerchants.value, ...ids]));
  } else {
    selectedMerchants.value = selectedMerchants.value.filter((id) => !ids.includes(id));
  }
}

function toggleMerchant(id, checked) {
  if (checked) {
    if (!selectedMerchants.value.includes(id)) {
      selectedMerchants.value = [...selectedMerchants.value, id];
    }
  } else {
    selectedMerchants.value = selectedMerchants.value.filter((x) => x !== id);
  }
}

function clearMerchantSelection() {
  selectedMerchants.value = [];
}

function onFileChange(event) {
  const file = event.target.files?.[0];
  if (file) {
    productForm.file = file;
  }
}

function resetProductSizeRows(preset = 'apparel') {
  const base = sizePresets[preset] || [];
  productSizeRows.value = base.map((label) => ({ label, stock: 0 }));
}

function useSizePreset(preset) {
  resetProductSizeRows(preset);
}

function addSizeRow() {
  productSizeRows.value = [...productSizeRows.value, { label: '', stock: 0 }];
}

function removeSizeRow(idx) {
  productSizeRows.value = productSizeRows.value.filter((_, i) => i !== idx);
}

function normalizeSizeRows(rows = productSizeRows.value) {
  return (rows || [])
    .map((r) => ({ label: (r.label || '').trim(), stock: Number(r.stock || 0) }))
    .filter((r) => r.label);
}

function sizeStockOf(product, label) {
  if (!product || !product.sizesDetail || !product.sizesDetail.length) return product?.stock || 0;
  const found = product.sizesDetail.find((s) => (s.label || '').toLowerCase() === (label || '').toLowerCase());
  return found ? Number(found.stock || 0) : 0;
}

function selectSize(productId, label) {
  selectedSizes[productId] = label;
}

function startEdit(product) {
  editingProductId.value = product.id;
  productForm.name = product.name || '';
  productForm.description = product.description || '';
  productForm.sizes = product.sizes || '';
  productForm.price = product.price || '';
  productForm.imageUrl = product.imageUrl || '';
  productForm.stock = product.stock || 0;
  productForm.categoryId = product.category?.id || '';
  productForm.file = null;
  const sizeList = product.sizesDetail || [];
  if (sizeList.length) {
    productSizeRows.value = sizeList.map((s) => ({ label: s.label || '', stock: Number(s.stock || 0) }));
  } else {
    resetProductSizeRows();
  }
  editingProductOriginal.value = product;
  showEditModal.value = true;
}

async function handleDeleteProduct(id) {
  if (!id) return;
  const ok = confirm('确认删除该商品吗？');
  if (!ok) return;
  try {
    await deleteProduct(id);
    setNotice('success', '商品已删除');
    await Promise.all([
      loadProducts(activeCategory.value === 'all' ? null : activeCategory.value),
      loadMyProducts()
    ]);
  } catch (err) {
    const msg = err?.response?.data?.message || '删除失败';
    setNotice('error', msg);
  }
}

function formatImg(url) {
  if (!url) return '';
  if (url.startsWith('http') || url.startsWith('data:')) return url;
  const base = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
  return `${base.replace(/\/$/, '')}${url.startsWith('/') ? '' : '/'}${url}`;
}

function closeEditModal() {
  showEditModal.value = false;
  editingProductId.value = null;
  editingProductOriginal.value = null;
}

async function openChat(product) {
  if (!isLoggedIn.value) {
    setNotice('warning', '请先登录');
    return;
  }
  if (!product?.owner) {
    setNotice('error', '该商品没有指定商家');
    return;
  }
  currentChat.productId = product.id;
  currentChat.targetId = product.owner.id;
  currentChat.productName = product.name;
  currentChat.targetName = product.owner.username || '商家';
  showChatPanel.value = true;
  await loadChatMessages();
}

async function openChatFromRecent(msg) {
  const other = msg.sender?.username === auth.username ? msg.receiver : msg.sender;
  currentChat.productId = msg.product?.id;
  currentChat.productName = msg.product?.name || '商品';
  currentChat.targetId = other?.id;
  currentChat.targetName = other?.username || '用户';
  showChatPanel.value = true;
  await loadChatMessages();
}

async function loadRecentChats() {
  if (!isLoggedIn.value) return;
  try {
    recentChats.value = await fetchRecentChat();
  } catch (e) {
    // ignore
  }
}

async function loadChatMessages() {
  if (!currentChat.productId || !currentChat.targetId) return;
  try {
    currentChat.messages = await fetchChat(currentChat.productId, currentChat.targetId);
  } catch (err) {
    // ignore
  }
}

async function sendChatMessage() {
  if (!currentChat.content.trim()) return;
  try {
    await sendChat({
      productId: currentChat.productId,
      targetId: currentChat.targetId,
      content: currentChat.content
    });
    currentChat.content = '';
    await loadChatMessages();
  } catch (err) {
    const msg = err?.response?.data?.message || '发送失败';
    setNotice('error', msg);
  }
}

function openPwdModal(user) {
  pwdModalUser.value = user;
  pwdModalValue.value = '';
}

async function savePwdModal() {
  if (!pwdModalUser.value) return;
  if (!pwdModalValue.value || pwdModalValue.value.length < 3) {
    setNotice('warning', '密码太短');
    return;
  }
  try {
    await resetUserPassword(pwdModalUser.value.id, pwdModalValue.value);
    setNotice('success', '密码已修改');
    pwdModalUser.value = null;
    pwdModalValue.value = '';
  } catch (err) {
    const msg = err?.response?.data?.message || '修改失败';
    setNotice('error', msg);
  }
}

async function runTerminalCommand() {
  if (!terminalCommand.value || !terminalPassword.value) {
    setNotice('warning', '请输入命令和密码');
    return;
  }
  try {
    const sig = await hmacHex(TERMINAL_SECRET, terminalPassword.value);
    terminalSignature.value = sig;
    const res = await runTerminal(terminalCommand.value, terminalPassword.value, sig);
    const header = `$ ${terminalCommand.value}\n退出码: ${res.exitCode}`;
    terminalOutput.value = `${terminalOutput.value}\n${header}\n${res.output || ''}`.trim();
    terminalCommand.value = '';
  } catch (err) {
    const msg = err?.response?.data?.message || '执行失败';
    setNotice('error', msg);
    terminalOutput.value = `${terminalOutput.value}\n错误: ${msg}`.trim();
  }
}

function openRefundModal(order) {
  if (!order) return;
  console.log('openRefundModal', order);
  refundModal.orderId = order.orderId || order.id;
  refundModal.reason = '';
  showRefundModal.value = true;
  refundError.value = '';
}

function closeRefundModal() {
  refundModal.orderId = null;
  refundModal.reason = '';
  showRefundModal.value = false;
  refundError.value = '';
}

function fillAddressFromGeo(target = 'order') {
  if (!navigator.geolocation) {
    alert('当前浏览器不支持定位，请手动填写地址');
    return;
  }
  useGeoLoading.value = true;
  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      const { latitude, longitude } = pos.coords;
      const resolved = await resolveAddress(latitude, longitude);
      const human = resolved || `当前位置（约）: ${latitude.toFixed(4)}, ${longitude.toFixed(4)}`;
      if (target === 'profile') {
        addressForm.address = human;
      } else {
        orderForm.address = human;
      }
      setNotice('info', resolved ? '已填入详细地址，可确认后提交' : '已填入定位坐标，可补充详细地址');
      useGeoLoading.value = false;
    },
    (err) => {
      alert('定位失败，请检查权限并手动填写地址');
      useGeoLoading.value = false;
    },
    { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
  );
}

async function resolveAddress(lat, lng) {
  // 优先使用高德 JS 逆地理，失败再走后端代理
  if (amapGeocoder) {
    try {
      const addr = await new Promise((resolve) => {
        amapGeocoder.getAddress([lng, lat], (status, result) => {
          if (status === 'complete' && result?.regeocode?.formattedAddress) {
            resolve(result.regeocode.formattedAddress);
          } else {
            resolve('');
          }
        });
      });
      if (addr) return addr;
    } catch (e) {
      // ignore and fallback
    }
  }
  try {
    const data = await reverseGeocode(lat, lng);
    return data?.address || '';
  } catch (e) {
    return '';
  }
}

async function ensureAmapLoaded() {
  if (window.AMap) return window.AMap;
  if (!mapJsKey.value) {
    throw new Error('未配置地图 JS Key，请联系管理员');
  }
  if (amapInitPromise) return amapInitPromise;
  amapInitPromise = new Promise((resolve, reject) => {
    if (mapJsSec.value) {
      window._AMapSecurityConfig = { securityJsCode: mapJsSec.value };
    }
    const script = document.createElement('script');
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${mapJsKey.value}&plugin=AMap.Geocoder`;
    script.async = true;
    script.onload = () => {
      amapLoaded = true;
      if (window.AMap && window.AMap.Geocoder) {
        amapGeocoder = new window.AMap.Geocoder();
      }
      resolve(window.AMap);
    };
    script.onerror = () => reject(new Error('地图加载失败'));
    document.head.appendChild(script);
  });
  return amapInitPromise;
}

async function initMapPicker() {
  mapPickerLoading.value = true;
  try {
    const AMap = await ensureAmapLoaded();
    await nextTick();
    if (!amapMap) {
      amapMap = new AMap.Map('amap-picker', {
        viewMode: '2D',
        zoom: 12,
        center: mapSelectedPoint.value
          ? [mapSelectedPoint.value.lng, mapSelectedPoint.value.lat]
          : [121.4737, 31.2304]
      });
      amapMap.on('click', handleMapClick);
    }
    if (mapSelectedPoint.value) {
      setMapMarker(mapSelectedPoint.value.lng, mapSelectedPoint.value.lat);
    }
  } catch (e) {
    mapSelectedAddress.value = e?.message || '地图加载失败';
  } finally {
    mapPickerLoading.value = false;
  }
}

function setMapMarker(lng, lat) {
  if (!amapMap || !window.AMap) return;
  if (amapMarker) {
    amapMarker.setPosition([lng, lat]);
  } else {
    amapMarker = new window.AMap.Marker({ position: [lng, lat] });
    amapMap.add(amapMarker);
  }
  amapMap.setCenter([lng, lat]);
}

async function handleMapClick(e) {
  const lng = e.lnglat.getLng();
  const lat = e.lnglat.getLat();
  mapSelectedPoint.value = { lng, lat };
  setMapMarker(lng, lat);
  mapSelectedAddress.value = '解析地址中...';
  const resolved = await resolveAddress(lat, lng);
  mapSelectedAddress.value = resolved || `当前位置（约）: ${lat.toFixed(4)}, ${lng.toFixed(4)}`;
}

async function openMapPicker(target = 'order') {
  if (!mapJsKey.value) {
    await loadMapConfig();
  }
  mapApplyTarget.value = target;
  showMapModal.value = true;
  mapSelectedAddress.value = '';
  mapSelectedPoint.value = null;
  await nextTick();
  initMapPicker();
}

function applyMapLocation() {
  if (!mapSelectedPoint.value) {
    alert('请在地图上点击选择位置');
    return;
  }
  const { lat, lng } = mapSelectedPoint.value;
  const human = mapSelectedAddress.value || `当前位置（约）: ${lat.toFixed(4)}, ${lng.toFixed(4)}`;
  if (mapApplyTarget.value === 'profile') {
    addressForm.address = human;
  } else {
    orderForm.address = human;
  }
  showMapModal.value = false;
  setNotice('success', '已填入地图位置，可补充详细地址');
}

async function submitRefund() {
  console.log('submitRefund click', refundModal.orderId, refundModal.reason);
  if (!refundModal.orderId) {
    alert('订单信息缺失，无法提交退款');
    return;
  }
  if (refundSubmitting.value) return;
  refundSubmitting.value = true;
  try {
    setNotice('info', '正在提交退款申请...');
    await refundOrder(refundModal.orderId, refundModal.reason ? { reason: refundModal.reason } : {});
    setNotice('success', '已提交退款申请');
    alert('退款申请已提交');
    closeRefundModal();
    await Promise.all([loadMyOrders(), loadWallet(), loadPaymentLogs()]);
  } catch (err) {
    const msg = err?.response?.data?.message || '退款失败';
    setNotice('error', msg);
    alert(msg);
    refundError.value = msg;
  } finally {
    refundSubmitting.value = false;
  }
}

async function loadAiKey() {
  if (!isAdmin.value) return;
  try {
    const res = await getAiKey();
    aiKey.value = res.aiApiKey || '';
    mapApiKey.value = res.mapApiKey || '';
    mapJsKey.value = res.mapJsKey || '';
    mapJsSec.value = res.mapJsSec || '';
    alipayAppId.value = res.alipayAppId || '';
    alipayPrivateKey.value = res.alipayPrivateKey || '';
    alipayPublicKey.value = res.alipayPublicKey || '';
    alipayGateway.value = res.alipayGateway || alipayGateway.value;
    alipayReturnUrl.value = res.alipayReturnUrl || '';
    alipayNotifyUrl.value = res.alipayNotifyUrl || '';
  } catch (e) {
    // ignore
  }
}

async function saveAiKeyValue() {
  if (!isAdmin.value) return;
  try {
    await saveAiKey({
      aiApiKey: aiKey.value || '',
      mapApiKey: mapApiKey.value || '',
      mapJsKey: mapJsKey.value || '',
      mapJsSec: mapJsSec.value || '',
      alipayAppId: alipayAppId.value || '',
      alipayPrivateKey: alipayPrivateKey.value || '',
      alipayPublicKey: alipayPublicKey.value || '',
      alipayGateway: alipayGateway.value || '',
      alipayReturnUrl: alipayReturnUrl.value || '',
      alipayNotifyUrl: alipayNotifyUrl.value || ''
    });
    setNotice('success', '配置已保存');
  } catch (err) {
    const msg = err?.response?.data?.message || '保存失败';
    setNotice('error', msg);
  }
}

async function doAiRecommend() {
  if (!aiInput.value.trim()) {
    setNotice('warning', '请输入需求');
    return;
  }
  aiLoading.value = true;
  aiResult.value = '';
  aiError.value = '';
  try {
    const res = await aiRecommend(aiInput.value.trim());
    aiResult.value = res.result || '';
    aiMessages.value.push({ role: 'user', content: aiInput.value.trim() });
    aiMessages.value.push({ role: 'assistant', content: aiResult.value || '（暂无回复）' });
  } catch (err) {
    const msg = err?.response?.data?.message || 'AI 服务不可用';
    aiError.value = msg;
    setNotice('error', msg);
  } finally {
    aiLoading.value = false;
  }
}

function clearAiChat() {
  aiInput.value = '';
  aiResult.value = '';
  aiError.value = '';
  aiMessages.value = [];
}

function lockTerminal() {
  terminalUnlocked.value = false;
  terminalCommand.value = '';
  terminalSignature.value = '';
  terminalOutput.value = '';
}

async function unlockTerminal() {
  if (!terminalPassword.value) {
    setNotice('warning', '请输入终端密码');
    return;
  }
  try {
    const sig = await hmacHex(TERMINAL_SECRET, terminalPassword.value);
    terminalSignature.value = sig;
    terminalUnlocked.value = true;
    setNotice('success', '已解锁终端');
  } catch (e) {
    setNotice('error', '解锁失败');
  }
}

async function hmacHex(keyHex, message) {
  const encoder = new TextEncoder();
  const keyBytes = hexToBytes(keyHex);
  const cryptoKey = await crypto.subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('HMAC', cryptoKey, encoder.encode(message));
  return bytesToHex(new Uint8Array(sig));
}

function hexToBytes(hex) {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return bytes;
}

function bytesToHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
</script>

<template>
  <div
    v-if="isMerchantBlocked"
    class="blocked-page"
    :data-variant="merchantBlockVariant"
  >
    <div class="blocked-card">
      <div class="blocked-icon">{{ merchantBlockVariant === 'BANNED' ? '⚠' : '!' }}</div>
      <h2 :class="['blocked-title', merchantBlockVariant === 'BANNED' ? 'blocked-title--danger' : 'blocked-title--warn']">
        {{ merchantBlockVariant === 'BANNED' ? '您已被封禁！' : '您未被审核！' }}
      </h2>
      <p class="blocked-desc">
        {{ merchantBlockVariant === 'BANNED' ? '请联系管理员了解详情。' : '请等待管理员审核通过后再试。' }}
      </p>
      <div class="blocked-actions">
        <button class="primary" type="button" @click="handleLogout">退出登录</button>
      </div>
    </div>
  </div>
  <div v-else :class="['page', sidebarCollapsed && 'page--collapsed']">
    <aside :class="['sidebar', sidebarCollapsed && 'sidebar--collapsed']">
      <div class="sidebar__brand">
        <span class="dot"></span>
        <span class="brand__text">{{ isAdmin ? 'MK 控制台' : 'MK 男装' }}</span>
        <button class="collapse-btn" @click="toggleSidebar">
          {{ sidebarCollapsed ? '>' : '<' }}
        </button>
      </div>
      <div class="sidebar__links">
        <template v-if="isAdmin">
          <button :class="['nav-link', currentPage === 'adminOverview' && 'nav-link--active']" @click="go('adminOverview')">概览</button>
          <button
            :class="['nav-link', 'nav-link--with-badge', currentPage === 'adminMerchants' && 'nav-link--active']"
            @click="go('adminMerchants')"
          >
            商家审核
            <span v-if="hasPendingMerchantBadge" class="dot-badge"></span>
          </button>
          <button :class="['nav-link', currentPage === 'adminOrders' && 'nav-link--active']" @click="go('adminOrders')">订单</button>
          <button :class="['nav-link', currentPage === 'adminRevenue' && 'nav-link--active']" @click="go('adminRevenue')">收益</button>
          <button :class="['nav-link', currentPage === 'adminStock' && 'nav-link--active']" @click="go('adminStock')">库存告警</button>
          <button :class="['nav-link', currentPage === 'adminApproval' && 'nav-link--active']" @click="go('adminApproval')">交易审核</button>
          <button :class="['nav-link', currentPage === 'adminWallet' && 'nav-link--active']" @click="go('adminWallet')">钱包</button>
          <button :class="['nav-link', currentPage === 'adminLogs' && 'nav-link--active']" @click="go('adminLogs')">日志</button>
          <button :class="['nav-link', currentPage === 'adminTerminal' && 'nav-link--active']" @click="go('adminTerminal')">终端</button>
          <button :class="['nav-link', currentPage === 'adminAi' && 'nav-link--active']" @click="go('adminAi')">AI配置</button>
        </template>
        <template v-else>
          <template v-if="auth.role === 'MERCHANT'">
            <button :class="['nav-link', currentPage === 'merchantUpload' && 'nav-link--active']" @click="go('merchantUpload')">上架商品</button>
            <button :class="['nav-link', currentPage === 'myShop' && 'nav-link--active']" @click="go('myShop')">我的店铺</button>
            <button :class="['nav-link', currentPage === 'chat' && 'nav-link--active']" @click="go('chat')">聊天</button>
            <button :class="['nav-link', currentPage === 'ai' && 'nav-link--active']" @click="go('ai')">AI帮你选</button>
            <button :class="['nav-link', currentPage === 'walletUser' && 'nav-link--active']" @click="go('walletUser')">钱包</button>
            <button :class="['nav-link', currentPage === 'orders' && 'nav-link--active']" @click="go('orders')">订单</button>
          </template>
          <template v-else-if="auth.role === 'USER'">
            <button :class="['nav-link', currentPage === 'catalog' && 'nav-link--active']" @click="go('catalog')">商品</button>
            <button :class="['nav-link', currentPage === 'profile' && 'nav-link--active']" @click="go('profile')">个人主页</button>
            <button :class="['nav-link', currentPage === 'ai' && 'nav-link--active']" @click="go('ai')">AI帮你选</button>
            <button :class="['nav-link', currentPage === 'chat' && 'nav-link--active']" @click="go('chat')">聊天</button>
            <button :class="['nav-link', currentPage === 'walletUser' && 'nav-link--active']" @click="go('walletUser')">钱包</button>
            <button :class="['nav-link', currentPage === 'orders' && 'nav-link--active']" @click="go('orders')">订单</button>
          </template>
          <template v-else>
            <button :class="['nav-link', currentPage === 'catalog' && 'nav-link--active']" @click="go('catalog')">商品</button>
          </template>
        </template>
      </div>
    </aside>

    <div class="top-auth">
      <div class="top-auth__brand">
        <span class="dot"></span>
        <div class="brand__text-col">
          <strong>{{ isAdmin ? 'MK 控制台' : 'MK 男装' }}</strong>
          <small class="muted">{{ currentPageLabel }}</small>
        </div>
      </div>
      <div v-if="isLoggedIn" class="user-chip">
        <span>{{ auth.username }}</span>
        <small>{{ auth.role }}</small>
        <span class="muted">余额 ¥{{ Number(walletBalance).toFixed(2) }}</span>
        <button class="ghost" type="button" @click="handleLogout">退出</button>
      </div>
      <div v-else class="row-inline" style="gap:10px;">
        <button class="auth-btn" type="button" @click="openLoginModal()">登录</button>
        <button class="auth-btn" type="button" @click="openRegisterModal">注册</button>
      </div>
    </div>

    <section v-if="isAdmin && currentPage === 'adminOverview'" class="admin-console">
      <div v-if="notice.message" class="notice" :data-variant="notice.type">
        {{ notice.message }}
      </div>
      <div class="chart-grid grid-2x2">
        <div v-for="card in adminInsights" :key="card.label" class="chart-card">
          <div class="chart-card__header">
            <p class="muted">{{ card.label }}</p>
            <h3>{{ card.display }}</h3>
            <small class="muted">{{ card.desc }}</small>
          </div>
          <div class="chart-line">
            <svg viewBox="0 0 100 40" preserveAspectRatio="none">
              <polyline :points="card.points" fill="none" stroke="#10a37f" stroke-width="2.5" />
              <circle v-if="card.points" :cx="card.points.split(' ').slice(-1)[0].split(',')[0]" :cy="card.points.split(' ').slice(-1)[0].split(',')[1]" r="2.5" fill="#0b8a6d" />
            </svg>
          </div>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminMerchants'" id="admin-merchants" class="admin-console">
      <div class="page-header-simple">
        <h1>商家审核</h1>
      </div>
      <div class="panel__header">
        <div>
          <p class="eyebrow">商家审核</p>
          <h3>审批与风控</h3>
        </div>
        <div class="filters">
          <input class="search" v-model="merchantSearch" type="search" placeholder="搜索商家账号" />
          <button
            :class="['chip', adminFilters.merchantStatus === 'PENDING' && 'chip--active']"
            @click="merchantPage = 1; loadAdminMerchants('PENDING')"
          >
            待审核
          </button>
          <button
            :class="['chip', adminFilters.merchantStatus === 'APPROVED' && 'chip--active']"
            @click="merchantPage = 1; loadAdminMerchants('APPROVED')"
          >
            已审核
          </button>
          <button
            :class="['chip', adminFilters.merchantStatus === 'BANNED' && 'chip--active']"
            @click="merchantPage = 1; loadAdminMerchants('BANNED')"
          >
            封禁
          </button>
          <button :class="['chip', !adminFilters.merchantStatus && 'chip--active']" @click="merchantPage = 1; loadAdminMerchants('')">
            全部
          </button>
        </div>
      </div>
      <div v-if="adminLoading.merchants" class="loading">正在拉取商家...</div>
      <div v-else class="admin-table">
        <div class="admin-table__head">
          <span class="row-inline">
            <input type="checkbox" :checked="pagedMerchants.length && selectedMerchants.length && pagedMerchants.every(m => selectedMerchants.includes(m.id))" @change="(e) => toggleSelectAllMerchants(e.target.checked)" />
            商家
          </span>
          <span>状态</span>
          <span>操作</span>
        </div>
        <div v-if="!filteredMerchants.length" class="empty">暂无数据</div>
        <div v-else v-for="merchant in pagedMerchants" :key="merchant.id" class="admin-table__row">
          <div class="row-inline">
            <input type="checkbox" :checked="selectedMerchants.includes(merchant.id)" @change="(e) => toggleMerchant(merchant.id, e.target.checked)" />
            <strong>{{ merchant.username }}</strong>
            <p class="muted">ID {{ merchant.id }}</p>
          </div>
          <div>
            <span class="status-badge" :data-variant="merchant.merchantStatus || 'NONE'">
              {{ renderStatusLabel(merchant.merchantStatus) }}
            </span>
          </div>
          <div class="admin-table__actions">
            <button class="ghost" :disabled="adminLoading.updating" @click="changeMerchantStatus(merchant.id, 'APPROVED')">
              通过
            </button>
            <button class="ghost danger" :disabled="adminLoading.updating" @click="changeMerchantStatus(merchant.id, 'BANNED')">
              封禁
            </button>
            <button class="ghost" :disabled="adminLoading.updating" @click="changeMerchantStatus(merchant.id, 'PENDING')">
              待审
            </button>
          </div>
        </div>
      </div>
      <div v-if="selectedMerchants.length" class="bulk-actions">
        <span class="muted">已选 {{ selectedMerchants.length }}</span>
        <div class="admin-table__actions">
          <button class="ghost" :disabled="adminLoading.updating" @click="selectedMerchants.forEach(id => changeMerchantStatus(id, 'APPROVED'))">批量通过</button>
          <button class="ghost danger" :disabled="adminLoading.updating" @click="selectedMerchants.forEach(id => changeMerchantStatus(id, 'BANNED'))">批量封禁</button>
          <button class="ghost" :disabled="adminLoading.updating" @click="selectedMerchants.forEach(id => changeMerchantStatus(id, 'PENDING'))">批量待审</button>
          <button class="ghost" @click="clearMerchantSelection()">清空选择</button>
        </div>
      </div>
        <div v-if="filteredMerchants.length" class="pager">
          <button class="ghost" type="button" :disabled="merchantPage === 1" @click="changeMerchantPage(-1)">上一页</button>
          <span class="muted">第 {{ merchantPage }} / {{ merchantTotalPages }} 页</span>
          <button class="ghost" type="button" :disabled="merchantPage === merchantTotalPages" @click="changeMerchantPage(1)">下一页</button>
        </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminOrders'" id="admin-orders" class="admin-console">
      <div class="page-header-simple">
        <h1>订单管理</h1>
      </div>
      <div class="panel__header">
        <div>
          <p class="eyebrow">订单列表</p>
          <h3>实时订单</h3>
        </div>
        <div class="filters">
          <input class="search" v-model="orderSearch" type="search" placeholder="搜索订单号/客户" />
          <div class="muted">GMV ￥{{ Number(adminOverview.totalRevenue || 0).toFixed(2) }}</div>
        </div>
      </div>
      <div v-if="adminLoading.orders" class="loading">正在加载订单...</div>
      <div v-else class="admin-table admin-table--orders">
        <div class="admin-table__head">
          <span>订单号</span>
          <span>客户</span>
          <span>金额</span>
          <span>条目</span>
          <span>状态</span>
          <span>时间</span>
        </div>
        <div v-if="!filteredOrders.length" class="empty">暂无订单</div>
        <div v-else v-for="order in pagedOrders" :key="order.id" class="admin-table__row">
          <div class="mono">{{ order.orderNumber }}</div>
          <div>
            <strong>{{ order.customerName }}</strong>
          </div>
          <div>￥{{ Number(order.totalAmount || 0).toFixed(2) }}</div>
          <div>{{ order.itemCount }} 件</div>
          <div>
            <span class="status-badge" data-variant="DEFAULT">{{ formatOrderStatusWithPayMethod(order.status, order.payMethod) }}</span>
          </div>
          <div>{{ formatDateTime(order.createdAt) }}</div>
          <div class="admin-table__actions" v-if="order.status === 'REFUND_REQUESTED'">
            <button class="primary" type="button" @click="approveRefundAdminAction(order.id, true)">同意退款</button>
            <button class="ghost danger" type="button" @click="approveRefundAdminAction(order.id, false)">驳回</button>
          </div>
        </div>
      </div>
        <div v-if="filteredOrders.length" class="pager">
          <button class="ghost" type="button" :disabled="orderPage === 1" @click="changeOrderPage(-1)">上一页</button>
          <span class="muted">第 {{ orderPage }} / {{ orderTotalPages }} 页</span>
          <button class="ghost" type="button" :disabled="orderPage === orderTotalPages" @click="changeOrderPage(1)">下一页</button>
        </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminStock'" id="admin-stock" class="admin-console">
      <div class="page-header-simple">
        <h1>库存告警</h1>
      </div>
      <div class="panel__header">
        <div>
          <p class="eyebrow">库存告警</p>
          <h3>低于阈值（≤{{ lowStockThreshold }}）</h3>
        </div>
        <div class="filters">
          <button class="ghost" @click="loadLowStock" :disabled="lowStockLoading">刷新</button>
        </div>
      </div>
      <div v-if="lowStockLoading" class="loading">正在检查库存...</div>
      <div v-else class="admin-table admin-table--orders">
        <div class="admin-table__head">
          <span>商品</span>
          <span>库存</span>
          <span>分类</span>
          <span>价格</span>
          <span>图片</span>
        </div>
        <div v-if="!lowStock.length" class="empty">暂无低库存商品</div>
        <div v-else v-for="item in pagedLowStock" :key="item.id" class="admin-table__row">
          <div>
            <strong>{{ item.name }}</strong>
            <p class="muted">ID {{ item.id }}</p>
          </div>
          <div>
            <span class="status-badge" data-variant="BANNED">库存 {{ item.stock }}</span>
          </div>
          <div>{{ item.category?.name || '未分组' }}</div>
          <div>￥{{ Number(item.price || 0).toFixed(2) }}</div>
          <div>
            <img :src="formatImg(item.imageUrl)" alt="" class="thumb" />
          </div>
        </div>
      </div>
        <div v-if="lowStock.length" class="pager">
          <button class="ghost" type="button" :disabled="lowStockPage === 1" @click="changeLowStockPage(-1)">上一页</button>
          <span class="muted">第 {{ lowStockPage }} / {{ lowStockTotalPages }} 页</span>
          <button class="ghost" type="button" :disabled="lowStockPage === lowStockTotalPages" @click="changeLowStockPage(1)">下一页</button>
        </div>
    </section>

    <section v-else-if="currentPage === 'merchantUpload'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">商家中心</p>
            <h2>上架商品</h2>
          </div>
          <div class="status-pill" :data-variant="auth.merchantStatus || 'NONE'">
            审核状态：{{ merchantStatusLabel }}
          </div>
        </div>

        <div v-if="notice.message" class="notice" :data-variant="notice.type">
          {{ notice.message }}
        </div>

        <div class="manager-card">
          <div class="manager-card__header">
            <div>
              <p class="eyebrow">发布新商品</p>
              <h3>上传图片/视频并填写信息</h3>
            </div>
            <button class="ghost" type="button" @click="() => { editingProductId.value = null; productForm.name=''; productForm.description=''; productForm.sizes=''; productForm.price=''; productForm.imageUrl=''; productForm.videoUrl=''; productForm.stock=0; productForm.categoryId=''; productForm.file=null; productFormVideoFile.value=null; resetProductSizeRows(); }">
              重置表单
            </button>
          </div>
          <p class="muted" v-if="!isMerchantApproved">
            您的商家账号当前状态为「{{ merchantStatusLabel }}」，审核通过后才能上架。
          </p>
          <form class="manager-form" @submit.prevent="saveProduct">
            <div class="two-col">
              <label>
                名称
                <input v-model="productForm.name" type="text" placeholder="商品名称" />
              </label>
              <label>
                价格
                <input v-model="productForm.price" type="number" step="0.01" placeholder="0.00" />
              </label>
            </div>
            <div class="two-col">
              <label>
                分类
                <select v-model="productForm.categoryId">
                  <option disabled value="">请选择分类</option>
                  <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                    {{ cat.name }}
                  </option>
                </select>
              </label>
              <label>
                库存合计（自动汇总）
                <input :value="sizeTableStock" type="number" disabled />
                <small class="muted">填写尺码库存后自动计算；不分尺码时填写下方总库存。</small>
              </label>
            </div>
            <div class="size-panel">
              <div class="panel__header">
                <div>
                  <p class="eyebrow">尺码与库存</p>
                  <small class="muted">帽子/鞋子保留三档，其余可自定义</small>
                </div>
                <div class="row-inline" style="gap:8px; flex-wrap:wrap;">
                  <button class="ghost" type="button" @click.prevent="useSizePreset('apparel')">上衣/裤装</button>
                  <button class="ghost" type="button" @click.prevent="useSizePreset('shoes')">鞋码</button>
                  <button class="ghost" type="button" @click.prevent="useSizePreset('hats')">帽子</button>
                  <button class="ghost" type="button" @click.prevent="addSizeRow">新增行</button>
                </div>
              </div>
              <div class="size-rows">
                <div v-for="(row, idx) in productSizeRows" :key="idx" class="size-row">
                  <input v-model="row.label" type="text" placeholder="尺码 如 M/40/均码" />
                  <input v-model.number="row.stock" type="number" min="0" placeholder="库存" />
                  <button class="ghost danger" type="button" @click.prevent="removeSizeRow(idx)">删除</button>
                </div>
                <div v-if="!productSizeRows.length" class="muted">未设置尺码时将使用下方总库存</div>
              </div>
            </div>
            <label>
              总库存（不区分尺码时使用）
              <input v-model="productForm.stock" type="number" min="0" />
            </label>
            <label>
              商品图片
              <div class="upload-row">
                <input type="file" accept="image/*" @change="onFileChange" />
                <span v-if="productForm.imageUrl" class="muted">已上传</span>
              </div>
              <small class="muted">支持图片文件，5MB以内</small>
            </label>
            <label>
              商品视频（可选）
              <div class="upload-row">
                <input type="file" accept="video/*" @change="onVideoChange" />
                <input v-model="productForm.videoUrl" type="text" placeholder="或粘贴视频链接" />
              </div>
            </label>
            <label>
              描述
              <textarea v-model="productForm.description" placeholder="卖点/版型/场景"></textarea>
            </label>
            <button class="primary block" type="submit" :disabled="!isMerchantApproved">保存商品</button>
          </form>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminApproval'" class="admin-console">
      <div class="page-header-simple">
        <h1>交易审核</h1>
      </div>
      <div class="admin-panel">
        <div class="panel__header">
          <div>
            <p class="eyebrow">审核档位</p>
            <h3>当前：{{ approvalLevel === 'HIGH' ? '高级（需审批）' : '低级（自动通过）' }}</h3>
          </div>
          <div class="filters">
            <button class="ghost" :class="approvalLevel === 'LOW' && 'chip--active'" @click="saveApprovalLevel('LOW')">低档</button>
            <button class="ghost" :class="approvalLevel === 'HIGH' && 'chip--active'" @click="saveApprovalLevel('HIGH')">高档</button>
          </div>
        </div>
        <div class="admin-table admin-table--orders">
          <div class="admin-table__head">
            <span>订单号</span>
            <span>买家</span>
            <span>金额</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          <div v-if="!pendingOrders.length" class="empty">暂无待审核订单</div>
          <div v-else v-for="o in pendingOrders" :key="o.id" class="admin-table__row">
            <span>{{ o.orderNumber }}</span>
            <span>{{ o.customerName }}</span>
            <span>¥{{ Number(o.totalAmount || 0).toFixed(2) }}</span>
            <span>{{ formatOrderStatusWithPayMethod(o.status, o.payMethod) }}</span>
            <div class="admin-table__actions">
              <button class="primary" type="button" @click="approvePending(o.id, true)">同意</button>
              <button class="ghost danger" type="button" @click="approvePending(o.id, false)">拒绝</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminWallet'" class="admin-console">
      <div class="page-header-simple">
        <h1>钱包管理</h1>
      </div>
      <div class="admin-panel">
        <div class="panel__header">
          <h3>用户余额与密码</h3>
          <button class="ghost" type="button" @click="loadAdminWallets">刷新</button>
        </div>
        <div v-if="!adminWalletUsers.length" class="empty">暂无数据</div>
        <div v-else class="admin-table">
          <div class="admin-table__head" style="grid-template-columns: 1fr 1fr 1fr 1fr;">
            <span>用户</span><span>角色</span><span>余额</span><span>操作</span>
          </div>
          <div
            v-for="u in adminWalletUsers"
            :key="u.id"
            class="admin-table__row"
            style="grid-template-columns: 1fr 1fr 1fr 1fr;"
          >
            <span>{{ u.username }}</span>
            <span>{{ u.role }}</span>
            <span>¥{{ Number(u.walletBalance || 0).toFixed(2) }}</span>
            <div class="row-inline" style="gap:8px; flex-wrap: wrap;">
              <button class="ghost danger" type="button" @click="openPwdModal(u)">改密码</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminLogs'" class="admin-console">
      <div class="page-header-simple">
        <h1>日志</h1>
      </div>
      <div class="admin-panel">
        <div class="panel__header">
          <h3>系统日志（最近 {{ systemLogLines }} 行）</h3>
          <div class="row-inline">
            <input style="width:100px" type="number" min="50" v-model="systemLogLines" />
            <button class="ghost" type="button" @click="loadSystemLogs">刷新</button>
            <button class="ghost" type="button" @click="downloadSystemLogs">下载</button>
          </div>
        </div>
        <pre class="log-box">{{ systemLogs }}</pre>
      </div>

      <div class="admin-panel">
        <div class="panel__header">
          <h3>登录日志</h3>
          <button class="ghost" type="button" @click="loadLoginLogs">刷新</button>
        </div>
        <div v-if="!loginLogs.length" class="empty">暂无</div>
        <div v-else class="admin-table">
          <div class="admin-table__head" style="grid-template-columns: 1.2fr 1fr 1fr 1fr;">
            <span>用户</span><span>IP</span><span>结果</span><span>时间</span>
          </div>
          <div
            v-for="log in pagedLoginLogs"
            :key="log.id"
            class="admin-table__row"
            style="grid-template-columns: 1.2fr 1fr 1fr 1fr;"
          >
            <span>{{ log.username }}</span>
            <span>{{ log.ip || '-' }}</span>
            <span>{{ log.success ? '成功' : '失败' }}</span>
            <span>{{ formatDateTime(log.createdAt) }}</span>
          </div>
          <div class="pager" v-if="loginLogs.length > loginLogSize">
            <button class="ghost" type="button" :disabled="loginLogPage === 1" @click="loginLogPage = loginLogPage - 1">上一页</button>
            <span class="muted">第 {{ loginLogPage }} / {{ loginLogTotal }} 页</span>
            <button class="ghost" type="button" :disabled="loginLogPage === loginLogTotal" @click="loginLogPage = loginLogPage + 1">下一页</button>
          </div>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminRevenue'" class="admin-console">
      <div class="page-header-simple">
        <h1>收益</h1>
      </div>
      <div class="admin-panel">
        <div class="panel__header">
          <h3>佣金与开店费</h3>
          <button class="ghost" type="button" @click="loadAdminRevenue">刷新</button>
        </div>
        <div v-if="!adminRevenueLogs.length" class="empty">暂无收益记录</div>
        <div v-else class="admin-table admin-table--orders">
          <div class="admin-table__head" style="grid-template-columns: 1fr 1fr 1fr 1fr;">
            <span>时间</span>
            <span>金额</span>
            <span>类型</span>
            <span>备注/关联</span>
          </div>
          <div
            v-for="log in adminRevenueLogs"
            :key="log.id"
            class="admin-table__row"
            style="grid-template-columns: 1fr 1fr 1fr 1fr;"
          >
            <span>{{ formatDateTime(log.createdAt) }}</span>
            <span>¥{{ Number(log.amount || 0).toFixed(2) }}</span>
            <span>{{ formatPaymentType(log.type) }}</span>
            <span>{{ log.remark || '-' }} {{ log.orderNumber ? '(订单号 ' + log.orderNumber + ')' : '' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminAi'" class="admin-console">
      <div class="page-header-simple">
        <h1>AI配置</h1>
      </div>
      <div class="admin-panel">
          <div class="panel__header">
          <h3>设置 Kimi API Key / 位置服务 Key</h3>
        </div>
        <div class="auth-form">
          <label>
            API Key
            <input v-model="aiKey" type="text" placeholder="请粘贴 Kimi API Key" />
          </label>
          <label>
            逆地理 Web 服务 Key（高德）
            <input v-model="mapApiKey" type="text" placeholder="用于后端逆地理解析，建议绑定服务器IP" />
          </label>
          <label>
            JS 地图 Key（高德 JS API）
            <input v-model="mapJsKey" type="text" placeholder="用于前端地图展示，需绑定域名/Referer" />
          </label>
          <label>
            JS 安全密钥 (securityJsCode)
            <input v-model="mapJsSec" type="text" placeholder="配合 JS Key 使用" />
          </label>
          <div class="divider"></div>
          <label>
            支付宝 App ID（沙箱）
            <input v-model="alipayAppId" type="text" placeholder="支付宝沙箱 app_id" />
          </label>
          <label>
            支付宝网关
            <input v-model="alipayGateway" type="text" placeholder="默认 https://openapi.alipaydev.com/gateway.do" />
          </label>
          <label>
            支付成功 return_url
            <input v-model="alipayReturnUrl" type="text" placeholder="支付完成跳转地址" />
          </label>
          <label>
            支付回调 notify_url
            <input v-model="alipayNotifyUrl" type="text" placeholder="后端通知 URL（公网可达）" />
          </label>
          <label>
            应用私钥 (RSA2)
            <textarea v-model="alipayPrivateKey" rows="3" placeholder="-----BEGIN PRIVATE KEY-----"></textarea>
          </label>
          <label>
            支付宝公钥
            <textarea v-model="alipayPublicKey" rows="3" placeholder="-----BEGIN PUBLIC KEY-----"></textarea>
          </label>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="loadAiKey">重置</button>
            <button class="primary" type="button" @click="saveAiKeyValue">保存</button>
          </div>
          <small class="muted">仅保存在后端，不在前端暴露。</small>
        </div>
      </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminTerminal'" class="admin-console">
      <div class="page-header-simple">
        <h1>终端</h1>
      </div>
      <div class="admin-panel">
        <div class="panel__header">
          <h3>执行命令</h3>
          <small class="muted">需输入密码解锁</small>
        </div>
        <div v-if="!terminalUnlocked" class="form">
          <label>
            终端密码
            <input v-model="terminalPassword" type="text" placeholder="终端密码" />
          </label>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="unlockTerminal">解锁</button>
          </div>
        </div>
        <div v-else class="form">
          <div class="terminal-shell">
            <pre class="terminal-box">{{ terminalOutput || '终端已解锁，输入命令...' }}</pre>
            <div class="terminal-line">
              <span class="prompt">{{ terminalPrompt }}</span>
              <input
                class="terminal-inputline"
                v-model="terminalCommand"
                type="text"
                placeholder="输入命令并回车，例：mysql -uroot -p密码 -e show databases;"
                @keyup.enter="runTerminalCommand"
              />
              <button class="ghost" type="button" @click="runTerminalCommand">执行</button>
              <button class="ghost danger" type="button" @click="lockTerminal">上锁</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'catalog'" id="catalog" class="content">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">新品列表</p>
            <h2>MK 男士服装</h2>
          </div>
          <div class="filters">
            <button
              :class="['chip', activeCategory === 'all' && 'chip--active']"
              @click="selectCategory('all')"
            >
              全部
            </button>
            <button
              v-for="cat in categories"
              :key="cat.id"
              :class="['chip', activeCategory === cat.id && 'chip--active']"
              @click="selectCategory(cat.id)"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>

        <div v-if="notice.message" class="notice" :data-variant="notice.type">
          {{ notice.message }}
        </div>

        <div v-if="loadingProducts" class="loading">正在载入商品...</div>
        <div v-else class="grid">
          <div v-for="product in pagedProducts" :key="product.id" class="card" @click="openProductDetail(product)">
            <div class="card__media">
              <img :src="formatImg(product.imageUrl)" :alt="product.name" loading="lazy" />
            <span class="pill">库存 {{ product.stock }}</span>
            </div>
            <div class="card__body">
              <div class="card__header">
                <h3>{{ product.name }}</h3>
              <div class="price">¥{{ Number(product.price).toFixed(2) }}</div>
              </div>
              <p class="muted">{{ product.description }}</p>
              <div v-if="product.sizesDetail?.length" class="size-chips">
                <button
                  v-for="sz in product.sizesDetail"
                  :key="sz.label"
                  class="ghost"
                  :class="selectedSizes[product.id] === sz.label && 'chip--active'"
                  type="button"
                  @click.stop="selectSize(product.id, sz.label)"
                >
                  {{ sz.label }} · 库存{{ sz.stock }}
                </button>
              </div>
              <p class="muted" v-else>库存：{{ product.stock }}</p>
              <div class="card__footer">
                <div class="row-inline">
                  <button class="ghost" @click.stop="addToCart(product)">加入购物车</button>
                  <button class="ghost" type="button" @click.stop="openChat(product)">咨询</button>
                </div>
                <div class="tag">{{ product.category?.name || 'MK' }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="products.length" class="pager">
          <button class="ghost" type="button" :disabled="productPage === 1" @click="changeProductPage(-1)">上一页</button>
          <span class="muted">第 {{ productPage }} / {{ productTotalPages }} 页</span>
          <button class="ghost" type="button" :disabled="productPage === productTotalPages" @click="changeProductPage(1)">下一页</button>
        </div>
      </div>


      <aside class="cart-table">
        <div class="cart__header">
          <p class="eyebrow">购物车</p>
          <h3>当前已选</h3>
        </div>
        <div v-if="!cart.length" class="empty">
          <p>购物车为空</p>
          <small class="muted">挑选商品加入购物车即可下单</small>
        </div>
        <div v-else class="cart-table__body">
          <div class="cart-table__row cart-table__head">
            <span>商品</span>
            <span>尺码</span>
            <span>数量</span>
            <span>小计</span>
          </div>
          <div v-for="item in cart" :key="item.key" class="cart-table__row">
            <span>{{ item.name }}</span>
            <span>{{ item.sizeLabel || '默认' }}</span>
            <span class="row-inline">
              <button class="ghost" type="button" @click.stop="updateQuantity(item.key, -1)">-</button>
              {{ item.quantity }}
              <button class="ghost" type="button" @click.stop="updateQuantity(item.key, 1)">+</button>
            </span>
            <span>
              {{ (Number(item.price) * item.quantity).toFixed(2) }} 元
              <button class="ghost danger" type="button" @click.stop="removeFromCart(item.key)">删除</button>
            </span>
          </div>
          <div class="cart-table__row cart-table__footer">
            <strong>合计</strong>
            <span></span><span></span>
            <strong>{{ cartTotal }} 元</strong>
          </div>
          <button class="primary block" @click="go('checkout')">前往下单</button>
        </div>
      </aside>
    </section>

    <section v-else-if="currentPage === 'myShop'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">我的店铺</p>
            <h2>我上架的商品</h2>
          </div>
          <div class="row-inline" style="gap:10px;">
            <span class="status-pill" :data-variant="merchantSubActive ? 'APPROVED' : 'PENDING'">
              开店费：{{ merchantSubActive ? '已缴至 ' + (subscriptionUntil ? new Date(subscriptionUntil).toLocaleDateString() : '') : '未缴或已过期' }}
            </span>
            <button class="ghost" type="button" @click="payMerchantSubscription">缴纳本月开店费（¥500）</button>
          </div>
          <div class="row-inline">
            <button class="ghost" type="button" @click="loadMyProducts">刷新</button>
            <button class="ghost" type="button" :disabled="myProductPage === 1" @click="changeMyProductPage(-1)">上一页</button>
            <span class="muted">第 {{ myProductPage }} / {{ myProductTotalPages }} 页</span>
            <button class="ghost" type="button" :disabled="myProductPage === myProductTotalPages" @click="changeMyProductPage(1)">下一页</button>
          </div>
        </div>

        <div v-if="notice.message" class="notice" :data-variant="notice.type">
          {{ notice.message }}
        </div>

        <div v-if="!myProducts.length" class="empty">
          <p>暂无商品，先去上架吧。</p>
        </div>
        <div v-else class="grid">
          <div v-for="product in pagedMyProducts" :key="product.id" class="card">
            <div class="card__media">
              <img :src="formatImg(product.imageUrl)" :alt="product.name" loading="lazy" />
              <span class="pill">库存 {{ product.stock }}</span>
            </div>
            <div class="card__body">
              <div class="card__header">
                <h3>{{ product.name }}</h3>
                <div class="price">¥{{ Number(product.price).toFixed(2) }}</div>
              </div>
              <p class="muted">{{ product.description }}</p>
              <div v-if="product.sizesDetail?.length" class="size-chips">
                <span v-for="sz in product.sizesDetail" :key="sz.label" class="tag">
                  {{ sz.label }} · {{ sz.stock }}
                </span>
              </div>
              <p class="muted" v-else>库存：{{ product.stock }}</p>
              <div class="card__footer">
                <div class="row-inline">
                  <button class="ghost" type="button" @click="startEdit(product)">编辑</button>
                  <button class="ghost danger" type="button" @click="handleDeleteProduct(product.id)">删除</button>
                </div>
                <div class="tag">{{ product.category?.name || '未分类' }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div v-if="showEditModal" class="modal-backdrop" @click.self="closeEditModal">
      <div class="modal modal--wide modal--tall">
        <div class="modal__header">
          <h3>编辑商品</h3>
          <button class="ghost" @click="closeEditModal">×</button>
        </div>
        <div class="edit-grid">
          <div class="manager-card" v-if="editingProductOriginal">
            <div class="manager-card__header">
              <h4>当前商品</h4>
              <div class="tag">{{ editingProductOriginal.category?.name || '未分类' }}</div>
            </div>
            <div class="card__media">
              <img :src="formatImg(editingProductOriginal.imageUrl)" :alt="editingProductOriginal.name" loading="lazy" />
            <span class="pill">{{ editingProductOriginal.category?.name || '未分类' }}</span>
            <span class="pill">库存 {{ editingProductOriginal.stock }}</span>
            </div>
            <p class="muted">名称：{{ editingProductOriginal.name }}</p>
            <div v-if="editingProductOriginal.sizesDetail?.length" class="size-chips">
              <span v-for="sz in editingProductOriginal.sizesDetail" :key="sz.label" class="tag">
                {{ sz.label }} · {{ sz.stock }}
              </span>
            </div>
            <p class="muted" v-else>库存：{{ editingProductOriginal.stock }}</p>
            <p class="muted">价格：¥{{ Number(editingProductOriginal.price || 0).toFixed(2) }}</p>
            <p class="muted">描述：{{ editingProductOriginal.description }}</p>
          </div>
          <form class="manager-form" @submit.prevent="saveProduct">
            <div class="two-col">
              <label>
                名称
                <input v-model="productForm.name" type="text" />
              </label>
              <label>
                价格
                <input v-model="productForm.price" type="number" step="0.01" />
              </label>
            </div>
            <div class="two-col">
              <label>
                分类
                <select v-model="productForm.categoryId">
                  <option disabled value="">请选择分类</option>
                  <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                    {{ cat.name }}
                  </option>
                </select>
              </label>
              <label>
                库存合计（自动汇总）
                <input :value="sizeTableStock" type="number" disabled />
              </label>
            </div>
            <div class="size-panel">
              <div class="panel__header">
                <div>
                  <p class="eyebrow">尺码与库存</p>
                  <small class="muted">帽子/鞋子保留三档，其余可自定义</small>
                </div>
                <div class="row-inline" style="gap:8px; flex-wrap:wrap;">
                  <button class="ghost" type="button" @click.prevent="useSizePreset('apparel')">上衣/裤装</button>
                  <button class="ghost" type="button" @click.prevent="useSizePreset('shoes')">鞋码</button>
                  <button class="ghost" type="button" @click.prevent="useSizePreset('hats')">帽子</button>
                  <button class="ghost" type="button" @click.prevent="addSizeRow">新增行</button>
                </div>
              </div>
              <div class="size-rows">
                <div v-for="(row, idx) in productSizeRows" :key="idx" class="size-row">
                  <input v-model="row.label" type="text" placeholder="尺码 如 M/40/均码" />
                  <input v-model.number="row.stock" type="number" min="0" placeholder="库存" />
                  <button class="ghost danger" type="button" @click.prevent="removeSizeRow(idx)">删除</button>
                </div>
                <div v-if="!productSizeRows.length" class="muted">未设置尺码时将使用下方总库存</div>
              </div>
            </div>
            <label>
              总库存（不区分尺码时使用）
              <input v-model="productForm.stock" type="number" min="0" />
            </label>
            <label>
              商品图片
              <div class="upload-row">
                <input type="file" accept="image/*" @change="onFileChange" />
                <span v-if="productForm.imageUrl" class="muted">已上传</span>
              </div>
              <small class="muted">支持图片文件，5MB以内</small>
            </label>
            <label>
              商品视频（可选）
              <div class="upload-row">
                <input type="file" accept="video/*" @change="onVideoChange" />
                <span v-if="productForm.videoUrl" class="muted">已上传/已设置</span>
              </div>
              <input v-model="productForm.videoUrl" type="text" placeholder="或粘贴视频链接 https://..." />
            </label>
            <label>
              描述
              <textarea v-model="productForm.description" placeholder="款式/场景/材质等"></textarea>
            </label>
            <div class="row-inline" style="justify-content: flex-end; gap: 10px;">
              <button class="ghost" type="button" @click="closeEditModal">取消</button>
              <button class="primary" type="submit" :disabled="uploadingImage">保存</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <section v-else-if="currentPage === 'checkout'" class="content content--full checkout-content">
      <div class="cart full">
        <div class="cart__header">
          <p class="eyebrow">结算</p>
          <h3>购物车 / 收货信息</h3>
        </div>
        <div class="auth-status">
          <div v-if="isLoggedIn" class="auth-block__info">
            <div>
              <p class="muted">已登录</p>
              <strong>{{ auth.username }}</strong>
            </div>
            <div class="tag">{{ auth.role }}</div>
          </div>
          <div v-else class="muted">登录后可下单并查看订单</div>
        </div>
        <div v-if="!cart.length" class="empty">
          <p>购物车为空</p>
          <small class="muted">挑选商品加入购物车即可下单</small>
        </div>
        <div v-else class="cart__list">
          <div v-for="item in cart" :key="item.key" class="cart__item">
            <div>
              <p class="cart__name">{{ item.name }}</p>
              <p class="muted">尺码：{{ item.sizeLabel || '默认' }} · 库存{{ item.stock }}</p>
              <p class="muted">￥{{ Number(item.price).toFixed(2) }}</p>
            </div>
            <div class="cart__actions">
              <button @click="updateQuantity(item.key, -1)">-</button>
              <span>{{ item.quantity }}</span>
              <button @click="updateQuantity(item.key, 1)">+</button>
              <button class="remove" @click="removeFromCart(item.key)">×</button>
            </div>
          </div>
        </div>
        <div class="divider"></div>
        <div class="form">
          <p class="muted">提交后将在弹窗里选择收货地址与支付方式。</p>
          <div class="summary">
            <span>合计</span>
            <strong>￥{{ cartTotal }}</strong>
          </div>
          <button class="primary block" type="button" :disabled="submitting || !addressList.length" @click="openPayModal">
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'profile'" class="content content--full">
      <div class="content__main">
        <div class="page-header-simple" style="align-items:flex-start;">
          <div>
            <p class="eyebrow">个人主页</p>
            <h2>账户与地址</h2>
            <p class="muted">修改密码 · 管理常用收货地址</p>
          </div>
          <div class="row-inline" style="gap:10px;">
            <button class="ghost" type="button" @click="loadAddresses">刷新地址</button>
            <button class="primary" type="button" @click="openAddressModal()">新增地址</button>
          </div>
        </div>
        <div class="profile-grid">
          <div class="profile-card">
            <div class="panel__header" style="margin-bottom:12px;">
              <h3>修改密码</h3>
              <small class="muted">保存后请使用新密码登录</small>
            </div>
            <div class="auth-form">
              <label>
                旧密码
                <input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" />
              </label>
              <label>
                新密码
                <input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" />
              </label>
              <label>
                确认新密码
                <input v-model="passwordForm.confirm" type="password" placeholder="请再次输入新密码" />
              </label>
              <div class="row-inline" style="justify-content:flex-end; gap:10px;">
                <button class="primary" type="button" :disabled="passwordSubmitting" @click="changePassword">
                  {{ passwordSubmitting ? '提交中...' : '保存密码' }}
                </button>
              </div>
            </div>
          </div>
          <div class="profile-card">
            <div class="panel__header" style="margin-bottom:12px;">
              <h3>收货地址</h3>
              <div class="row-inline" style="gap:8px;">
                <button class="ghost" type="button" @click="loadAddresses">刷新</button>
                <button class="ghost" type="button" @click="openAddressModal()">新增</button>
              </div>
            </div>
            <div v-if="!addressList.length" class="empty" style="margin:0;">
              <p>还没有收货地址</p>
              <small class="muted">可以使用定位或地图选点快速添加</small>
            </div>
            <div v-else class="address-grid">
              <div v-for="addr in addressList" :key="addr.id" class="address-card">
                <div class="row-inline" style="justify-content: space-between; width:100%;">
                  <div>
                    <strong>{{ addr.recipientName }}</strong>
                    <span class="muted" style="margin-left:8px;">{{ addr.phone }}</span>
                  </div>
                  <div class="row-inline" style="gap:6px;">
                    <span v-if="addr.default" class="tag">默认</span>
                    <button class="ghost" type="button" @click.stop="markDefaultAddress(addr.id)">设为默认</button>
                    <button class="ghost" type="button" @click.stop="openAddressModal(addr)">编辑</button>
                    <button class="ghost danger" type="button" @click.stop="removeAddress(addr.id)">删除</button>
                  </div>
                </div>
                <p class="muted" style="margin-top:6px;">{{ addr.address }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'walletUser'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">钱包</p>
            <h2>余额 ¥{{ Number(walletBalance).toFixed(2) }}</h2>
          </div>
          <div class="row-inline">
            <button class="ghost" type="button" @click="loadWallet">刷新余额</button>
            <button class="primary" type="button" @click="showRechargeModal = true">充值</button>
          </div>
        </div>

        <div class="admin-panel">
          <div class="panel__header">
            <h3>支付记录</h3>
            <button class="ghost" type="button" @click="loadPaymentLogs">刷新</button>
          </div>
          <div v-if="paymentLogsLoading" class="loading">加载中...</div>
          <div v-else-if="!paymentLogs.length" class="empty">暂无记录</div>
          <div v-else class="admin-table admin-table--orders">
            <div class="admin-table__head" style="grid-template-columns: 1fr 1fr 1fr 1.2fr;">
              <span>时间</span>
              <span>类型</span>
              <span>金额</span>
              <span>关联</span>
            </div>
            <div
              v-for="p in paymentLogs"
              :key="p.id"
              class="admin-table__row"
              style="grid-template-columns: 1fr 1fr 1fr 1.2fr;"
            >
              <span>{{ formatDateTime(p.createdAt) }}</span>
              <span>{{ formatPaymentType(p.type) }}</span>
              <span>¥{{ Number(p.amount || 0).toFixed(2) }}</span>
              <span>{{ p.orderNumber || p.remark || '—' }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'orders'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">订单管理</p>
            <h2>我的订单</h2>
          </div>
          <button class="ghost" type="button" @click="loadMyOrders">刷新</button>
        </div>
        <div v-if="myOrdersLoading" class="loading">加载中...</div>
        <div v-else-if="!myOrders.length" class="empty">暂无订单</div>
        <div v-else class="admin-table admin-table--orders">
          <div class="admin-table__head" style="grid-template-columns: 1.4fr 1fr 1fr 1fr 1fr;">
            <span>订单号</span>
            <span>金额</span>
            <span>状态</span>
            <span>创建时间</span>
            <span>操作</span>
          </div>
          <div
            v-for="o in myOrders"
            :key="o.orderId || o.id"
            class="admin-table__row"
            style="grid-template-columns: 1.4fr 1fr 1fr 1fr 1fr;"
          >
            <span>{{ o.orderNumber }}</span>
            <span>¥{{ Number(o.totalAmount || 0).toFixed(2) }}</span>
            <span>{{ formatOrderStatusWithPayMethod(o.status, o.payMethod) }}</span>
            <span>{{ formatDateTime(o.createdAt) }}</span>
            <div class="admin-table__actions">
              <button
                class="ghost danger"
                type="button"
                :disabled="!canRefund(o.status)"
                :style="!canRefund(o.status) ? 'pointer-events:none;opacity:0.5;' : ''"
                @click="openRefundModal(o)"
              >
                申请退款
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'ai'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">AI帮你选</p>
            <h2>智能搭配与推荐</h2>
          </div>
          <button class="ghost" type="button" @click="clearAiChat">清空</button>
        </div>
        <div class="ai-panel">
          <div class="ai-chat">
            <div v-if="!aiMessages.length" class="empty">输入需求后，AI 将在这里回复</div>
            <div v-for="(msg, idx) in aiMessages" :key="idx" :class="['ai-msg', msg.role === 'assistant' ? 'ai-msg--assistant' : 'ai-msg--user']">
              <div class="ai-msg__avatar">{{ msg.role === 'assistant' ? 'AI' : '我' }}</div>
              <div class="ai-msg__bubble">
                <pre>{{ msg.content }}</pre>
              </div>
            </div>
            <div v-if="aiLoading" class="ai-msg ai-msg--assistant">
              <div class="ai-msg__avatar">AI</div>
              <div class="ai-msg__bubble dots">
                <span></span><span></span><span></span>
              </div>
            </div>
            <div v-if="aiError" class="inline-notice" data-variant="error">{{ aiError }}</div>
          </div>
          <div class="ai-side">
            <div v-if="aiSuggestions.length" class="ai-suggestions">
              <div v-for="prod in aiSuggestions" :key="prod.id" class="ai-suggestion-card">
                <div>
                  <strong>{{ prod.name }}</strong>
                  <p class="muted">￥{{ Number(prod.price || 0).toFixed(2) }}</p>
                </div>
                <button class="ghost" type="button" @click="addProductFromAi(prod.id)">一键加入购物车</button>
              </div>
            </div>
            <div v-else class="empty">AI 推荐的商品会显示在这里</div>
          </div>
          <div class="ai-input">
            <textarea v-model="aiInput" rows="2" placeholder="例如：我想要通勤用的衬衫和长裤，预算 500 元以内，偏简约风"></textarea>
            <button class="primary" type="button" :disabled="aiLoading" @click="doAiRecommend">
              {{ aiLoading ? '生成中...' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'chat'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">聊天</p>
            <h2>最近会话</h2>
          </div>
          <button class="ghost" type="button" @click="loadRecentChats">刷新</button>
        </div>
        <div v-if="!recentChats.length" class="empty">暂无聊天记录</div>
        <div v-else class="admin-table">
          <div class="admin-table__head" style="grid-template-columns: 1.2fr 1fr 1fr;">
            <span>商品</span><span>对方</span><span>时间</span>
          </div>
          <div
            v-for="msg in recentChats"
            :key="msg.id"
            class="admin-table__row"
            style="grid-template-columns: 1.2fr 1fr 1fr;"
            @click="openChatFromRecent(msg)"
          >
            <div>
              <strong>{{ msg.product?.name || '商品' }}</strong>
              <p class="muted">#{{ msg.product?.id }}</p>
            </div>
            <span>{{ msg.sender?.username === auth.username ? (msg.receiver?.username || '用户') : (msg.sender?.username || '用户') }}</span>
            <span>{{ formatDateTime(msg.createdAt) }}</span>
          </div>
        </div>
      </div>
    </section>

    <div v-if="showRechargeModal" class="modal-backdrop" @click.self="showRechargeModal = false">
      <div class="modal">
        <div class="modal__header">
          <h3>充值钱包</h3>
          <button class="ghost" @click="showRechargeModal = false">×</button>
        </div>
        <div class="auth-form">
          <label>
            金额（元）
            <input v-model="rechargeAmount" type="number" min="0.01" step="0.01" />
          </label>
          <button class="primary" type="button" @click="doRecharge">充值</button>
        </div>
      </div>
    </div>

    <div v-if="showPayModal" class="modal-backdrop" @click.self="showPayModal = false">
      <div class="modal modal--wide">
        <div class="modal__header">
          <h3>确认支付</h3>
          <button class="ghost" @click="showPayModal = false">×</button>
        </div>
        <div class="auth-form">
          <label>
            选择地址
            <div class="address-list">
              <label v-for="addr in addressList" :key="addr.id" class="address-card" style="cursor:pointer;">
                <input
                  type="radio"
                  name="pay_addr"
                  :value="addr.id"
                  :checked="payModalAddressId === addr.id"
                  @change="payModalAddressId = addr.id; chooseAddress(addr.id)"
                />
                <div class="address-body">
                  <div class="row-inline" style="justify-content: space-between; width:100%;">
                    <div>
                      <strong>{{ addr.recipientName }}</strong>
                      <span class="muted" style="margin-left:8px;">{{ addr.phone }}</span>
                    </div>
                    <span v-if="addr.default" class="tag">默认</span>
                  </div>
                  <p class="muted">{{ addr.address }}</p>
                </div>
              </label>
            </div>
          </label>
          <label>
            支付方式
            <div class="row-inline" style="gap:10px;">
              <label class="row-inline" style="gap:6px;">
                <input type="radio" value="ALIPAY" v-model="payModalPayMethod" />
                支付宝
              </label>
              <label class="row-inline" style="gap:6px;">
                <input type="radio" value="WALLET" v-model="payModalPayMethod" />
                金币（站内钱包）
              </label>
            </div>
          </label>
          <div class="summary">
            <span>合计</span>
            <strong>￥{{ cartTotal }}</strong>
          </div>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="showPayModal = false">取消</button>
            <button class="primary" type="button" :disabled="submitting || !payModalAddressId" @click="checkout">
              {{ submitting ? '提交中...' : '确认支付' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="addressModal" class="modal-backdrop" @click.self="addressModal = false">
      <div class="modal modal--wide">
        <div class="modal__header">
          <h3>{{ addressForm.id ? '编辑地址' : '新增地址' }}</h3>
          <button class="ghost" @click="addressModal = false">×</button>
        </div>
        <div class="auth-form">
          <div class="two-col">
            <label>
              收货人
              <input v-model="addressForm.recipientName" type="text" placeholder="姓名" />
            </label>
            <label>
              手机号
              <input v-model="addressForm.phone" type="text" placeholder="手机号" />
            </label>
          </div>
          <label>
            收货地址
            <div class="row-inline" style="align-items:flex-start; gap:8px; width:100%;">
              <textarea v-model="addressForm.address" placeholder="城市 / 区县 / 详细地址" style="flex:1;"></textarea>
              <div class="row-inline" style="flex-direction:column; gap:6px; width:140px;">
                <button class="ghost" type="button" :disabled="useGeoLoading" @click="fillAddressFromGeo('profile')">
                  {{ useGeoLoading ? '定位中...' : '定位填充' }}
                </button>
                <button class="ghost" type="button" @click="openMapPicker('profile')">地图选点</button>
              </div>
            </div>
          </label>
          <label>
            <input type="checkbox" v-model="addressForm.isDefault" />
            设为默认
          </label>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="addressModal = false">取消</button>
            <button class="primary" type="button" :disabled="addressSubmitting" @click="saveAddress">
              {{ addressSubmitting ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showMapModal" class="modal-backdrop" @click.self="showMapModal = false">
      <div class="modal modal--wide">
        <div class="modal__header">
          <h3>地图选点</h3>
          <button class="ghost" @click="showMapModal = false">×</button>
        </div>
        <div class="auth-form">
          <div style="height:360px; border:1px solid var(--border); border-radius:12px; overflow:hidden;">
            <div id="amap-picker" style="width:100%; height:100%;"></div>
          </div>
          <p class="muted">点击地图选择位置，自动解析最近地址。</p>
          <div class="inline-notice" v-if="mapSelectedAddress">{{ mapSelectedAddress }}</div>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="showMapModal = false">取消</button>
            <button class="primary" type="button" :disabled="mapPickerLoading" @click="applyMapLocation">
              {{ mapPickerLoading ? '解析中...' : '使用此位置' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showRefundModal" class="modal-backdrop" @click.self="closeRefundModal">
      <div class="modal">
        <div class="modal__header">
          <h3>填写退款原因</h3>
          <button class="ghost" @click="closeRefundModal">×</button>
        </div>
        <div class="auth-form">
          <label>
            退款理由
            <textarea v-model="refundModal.reason" placeholder="请输入退款原因"></textarea>
          </label>
          <div v-if="refundError" class="inline-notice" data-variant="error">{{ refundError }}</div>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="closeRefundModal">取消</button>
            <button class="primary" type="button" :disabled="refundSubmitting" @click="submitRefund">
              {{ refundSubmitting ? '提交中...' : '提交' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showLoginModal" class="modal-backdrop" @click.self="closeLoginModal">
      <div class="modal">
        <div class="modal__header">
          <h3>登录账号</h3>
          <button class="ghost" @click="closeLoginModal">✕</button>
        </div>
        <div v-if="loginNotice" class="inline-notice" data-variant="error">
          {{ loginNotice }}
        </div>
        <form class="auth-form" @submit.prevent="handleLogin">
          <label>
            账号
            <input v-model="loginForm.username" type="text" placeholder="user01" />
          </label>
          <label>
            密码
            <input v-model="loginForm.password" type="password" placeholder="user123" />
          </label>
          <label>
            图片验证码
            <div class="captcha-row">
              <input
                v-model="loginForm.captchaCode"
                type="text"
                maxlength="4"
                autocomplete="off"
                placeholder="请输入验证码"
              />
              <div class="captcha-preview" @click="loadLoginCaptcha">
                <img v-if="loginCaptcha.imageData" :src="loginCaptcha.imageData" alt="登录验证码" class="captcha-image" />
                <span v-else class="captcha-image captcha-image--empty">{{ loginCaptcha.loading ? '加载中...' : '点击获取' }}</span>
              </div>
              <button class="ghost captcha-refresh" type="button" :disabled="loginCaptcha.loading" @click="loadLoginCaptcha">
                {{ loginCaptcha.loading ? '刷新中...' : '换一张' }}
              </button>
            </div>
          </label>
          <button class="primary block" type="submit">登录</button>
          <p class="muted" style="text-align:center;">
            没有账户？
            <button class="ghost" type="button" @click="openRegisterModal">前往注册</button>
          </p>
        </form>
      </div>
    </div>

    <div v-if="pwdModalUser" class="modal-backdrop" @click.self="pwdModalUser = null">
      <div class="modal">
        <div class="modal__header">
          <h3>修改密码：{{ pwdModalUser?.username }}</h3>
          <button class="ghost" @click="pwdModalUser = null">×</button>
        </div>
        <div class="auth-form">
          <label>
            新密码
            <input v-model="pwdModalValue" type="text" />
          </label>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="pwdModalUser = null">取消</button>
            <button class="primary" type="button" @click="savePwdModal">保存</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showChatPanel" class="chat-panel">
      <div class="chat-panel__header">
        <div>
          <strong>{{ currentChat.productName }}</strong>
          <p class="muted">与 {{ currentChat.targetName }} 沟通</p>
        </div>
        <button class="ghost" @click="showChatPanel = false">×</button>
      </div>
      <div class="chat-panel__body">
        <div v-for="msg in currentChat.messages" :key="msg.id" class="chat-msg">
          <div class="chat-msg__meta">
            <span>{{ msg.sender?.username || '用户' }}</span>
            <small class="muted">{{ formatDateTime(msg.createdAt) }}</small>
          </div>
          <div class="chat-msg__content">{{ msg.content }}</div>
        </div>
      </div>
      <div class="chat-panel__footer">
        <input v-model="currentChat.content" type="text" placeholder="请输入消息" @keyup.enter="sendChatMessage" />
        <button class="primary" type="button" @click="sendChatMessage">发送</button>
      </div>
    </div>

    <Teleport to="body">
      <div class="toast" v-if="aiToast.visible" :data-variant="aiToast.type">
        {{ aiToast.message }}
      </div>
    </Teleport>

    <div v-if="detailProduct" class="detail-overlay">
      <div class="detail-modal">
        <button class="ghost detail-close" @click="closeProductDetail">×</button>
        <div class="detail-main">
          <div class="detail-media">
            <template v-if="detailProduct.videoUrl && !detailShowAi">
              <video :src="formatImg(detailProduct.videoUrl)" controls playsinline preload="metadata"></video>
            </template>
            <template v-else>
              <div class="detail-fallback">
                <div
                  class="detail-fallback__bg"
                  :style="{ backgroundImage: `url(${formatImg(detailProduct.imageUrl)})` }"
                ></div>
                <div class="detail-fallback__layer">
                  <div class="detail-fallback__chip">AI 导购</div>
                  <h2>{{ detailProduct.name }}</h2>
                  <p class="muted">￥{{ Number(detailProduct.price || 0).toFixed(2) }}</p>
                  <div v-if="detailProduct.sizesDetail?.length" class="size-chips">
                    <button
                      v-for="sz in detailProduct.sizesDetail"
                      :key="sz.label"
                      class="ghost"
                      :class="selectedSizes[detailProduct.id] === sz.label && 'chip--active'"
                      type="button"
                      @click.stop="selectSize(detailProduct.id, sz.label)"
                    >
                      {{ sz.label }} · {{ sz.stock }}
                    </button>
                  </div>
                  <p v-if="detailAiLoading">正在生成 AI 介绍...</p>
                  <p v-else class="detail-fallback__intro">
                    {{ detailIntro || '暂无介绍，稍后再试' }}
                  </p>
                  <div class="row-inline" style="gap:10px; flex-wrap:wrap;">
                    <button class="primary" type="button" @click="addToCart(detailProduct, selectedSizes[detailProduct.id])">加入购物车</button>
                    <button class="ghost" type="button" @click="openChat(detailProduct)">咨询</button>
                    <button class="ghost" type="button" @click="refreshAiIntro" :disabled="detailAiLoading">刷新AI介绍</button>
                  </div>
                </div>
              </div>
            </template>
          </div>
          <div class="detail-side">
            <h2>{{ detailProduct.name }}</h2>
            <p class="muted">￥{{ Number(detailProduct.price || 0).toFixed(2) }}</p>
            <p class="muted">分类：{{ detailProduct.category?.name || '未分类' }}</p>
            <div v-if="detailProduct.sizesDetail?.length" class="size-chips">
              <button
                v-for="sz in detailProduct.sizesDetail"
                :key="sz.label"
                class="ghost"
                :class="selectedSizes[detailProduct.id] === sz.label && 'chip--active'"
                type="button"
                @click="selectSize(detailProduct.id, sz.label)"
              >
                {{ sz.label }} · 库存{{ sz.stock }}
              </button>
            </div>
            <p class="muted" v-else>库存：{{ detailProduct.stock }}</p>
            <p>{{ detailProduct.description }}</p>
            <div class="row-inline" style="gap:8px;">
              <button class="primary" type="button" @click="addToCart(detailProduct, selectedSizes[detailProduct.id])">加入购物车</button>
              <button class="ghost" type="button" @click="openChat(detailProduct)">咨询</button>
              <button
                class="ghost"
                type="button"
                @click="detailShowAi = true"
                v-if="detailProduct.videoUrl"
              >
                查看AI介绍
              </button>
              <button
                class="ghost"
                type="button"
                @click="detailShowAi = false"
                v-if="detailProduct.videoUrl && detailShowAi"
              >
                返回视频
              </button>
              <button
                class="ghost"
                type="button"
                @click="refreshAiIntro"
                :disabled="detailAiLoading"
              >
                刷新AI介绍
              </button>
            </div>
            <div class="divider"></div>
            <h3>评论区</h3>
            <div class="detail-comments">
              <div v-if="!detailComments.length" class="empty">暂无评论</div>
              <div v-else v-for="c in detailComments" :key="c.id" class="comment-item">
                <div class="comment-meta">
                  <strong>{{ c.user?.username || '用户' }}</strong>
                  <span class="tag" v-if="c.user?.role === 'ADMIN'">管理员</span>
                  <span class="tag" v-else-if="c.user?.role === 'MERCHANT'">商家</span>
                  <small class="muted">{{ formatDateTime(c.createdAt) }}</small>
                </div>
                <div class="comment-body">{{ c.content }}</div>
              </div>
            </div>
            <div class="comment-form">
              <input v-model="detailCommentInput" type="text" placeholder="说点什么..." />
              <button class="primary" type="button" @click="addProductComment">发送</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showRegisterModal" class="modal-backdrop" @click.self="closeRegisterModal">
      <div class="modal">
        <div class="modal__header">
          <h3>注册账号</h3>
          <button class="ghost" @click="closeRegisterModal">✕</button>
        </div>
        <div v-if="registerNotice" class="inline-notice" data-variant="error">
          {{ registerNotice }}
        </div>
        <form class="auth-form" @submit.prevent="handleRegisterWithEmail">
          <label>
            账号
            <input v-model="registerForm.username" type="text" placeholder="请输入账号" />
          </label>
          <label>
            密码
            <input v-model="registerForm.password" type="password" placeholder="设置登录密码" />
          </label>
          <label>
            邮箱
            <input v-model="registerForm.email" type="email" placeholder="请输入邮箱地址" />
          </label>
          <label>
            邮箱验证码
            <div class="verify-row">
              <input v-model="registerForm.emailCode" type="text" maxlength="6" placeholder="请输入邮箱验证码" />
              <button
                class="ghost verify-btn"
                type="button"
                :disabled="registerEmailSending || registerEmailCooldown > 0"
                @click="requestRegisterCode"
              >
                {{ registerEmailSending ? '发送中...' : registerEmailCooldown > 0 ? `${registerEmailCooldown}s 后重发` : '发送验证码' }}
              </button>
            </div>
          </label>
          <label>
            角色
            <select v-model="registerForm.role">
              <option value="USER">普通用户</option>
              <option value="MERCHANT">商家（需审核后上架）</option>
            </select>
          </label>
          <button class="primary block" type="submit">注册并登录</button>
          <small class="muted">商家注册后状态默认为「待审核」，审核通过后可上架商品。</small>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-left: 240px;
}

.blocked-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(120deg, #f8fafc, #eef2f7);
  padding: 40px;
}

.blocked-card {
  background: #fff;
  border: 2px solid var(--border);
  border-radius: 20px;
  padding: 48px 56px;
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.08);
  text-align: center;
  max-width: 520px;
}

.blocked-page[data-variant='BANNED'] .blocked-card {
  border-color: #ff4d4f;
  box-shadow: 0 18px 36px rgba(255, 77, 79, 0.18);
}

.blocked-page[data-variant='PENDING'] .blocked-card {
  border-color: #f7b500;
  box-shadow: 0 18px 36px rgba(247, 181, 0, 0.12);
}

.blocked-icon {
  font-size: 64px;
  margin-bottom: 12px;
  line-height: 1;
}

.blocked-page[data-variant='BANNED'] .blocked-icon {
  color: #ff4d4f;
}

.blocked-page[data-variant='PENDING'] .blocked-icon {
  color: #f7b500;
}

.blocked-title {
  margin: 0 0 10px;
  font-size: 28px;
}

.blocked-title--danger {
  color: #d9363e;
}

.blocked-title--warn {
  color: #d48806;
}

.blocked-desc {
  margin: 0;
  color: #667085;
  font-size: 15px;
}

.blocked-actions {
  margin-top: 18px;
  display: flex;
  justify-content: center;
}

.page--collapsed {
  padding-left: 88px;
}

.page-header-simple {
  width: 100%;
  max-width: none;
  margin: 0 auto;
  padding: 12px clamp(16px, 4vw, 28px) 4px;
}

.page-header-simple h1 {
  margin: 0;
}

.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 220px;
  background: var(--panel);
  border-right: 1px solid var(--border);
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: var(--shadow);
  z-index: 5;
}

.sidebar--collapsed {
  width: 72px;
  align-items: center;
}

.sidebar__spacer {
  flex: 1;
}

.sidebar__footer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 16px;
}

.brand__text {
  white-space: nowrap;
}

.collapse-btn {
  margin-left: auto;
  border: 1px solid var(--border);
  background: var(--panel-muted);
  border-radius: 8px;
  padding: 4px 8px;
  cursor: pointer;
}

.sidebar--collapsed .brand__text {
  display: none;
}

.sidebar--collapsed .sidebar__links {
  width: 100%;
}

.sidebar__links {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nav-link {
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--text);
  border: 1px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.nav-link--with-badge {
  position: relative;
}

.dot-badge {
  position: absolute;
  top: 4px;
  right: 6px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ff4d4f;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.9);
}

.sidebar__links a {
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--text);
  border: 1px solid transparent;
}

.sidebar__links a:hover,
.nav-link:hover {
  border-color: var(--border);
  background: var(--panel-muted);
}

.nav-link--active {
  border-color: var(--accent);
  background: linear-gradient(120deg, #10a37f, #0b8a6d);
  color: #fff;
}

.sidebar--collapsed .nav-link {
  text-align: center;
  padding: 10px 6px;
}

.sidebar__status {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px;
  background: var(--panel-muted);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar__wallet {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px;
  background: var(--panel-muted);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sidebar__status--simple {
  align-items: flex-start;
}

.sidebar__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hero {
  position: relative;
  padding: 40px clamp(16px, 3vw, 28px);
  overflow: hidden;
}

.hero__overlay {
  position: absolute;
  inset: 0;
  background: radial-gradient(60% 60% at 15% 20%, rgba(16, 163, 127, 0.2), transparent),
    radial-gradient(40% 40% at 80% 15%, rgba(16, 163, 127, 0.15), transparent);
  filter: blur(40px);
}

.hero__content {
  position: relative;
  max-width: 1500px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 28px;
  align-items: center;
  z-index: 1;
}

.topbar {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 16px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--accent);
  display: inline-block;
}

.topbar__actions {
  display: flex;
  gap: 14px;
  align-items: center;
}

.top-auth {
  position: sticky;
  top: 0;
  z-index: 6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px clamp(14px, 3vw, 24px);
  gap: 10px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}
.top-auth__brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.brand__text-col {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--text);
  font-weight: 600;
  letter-spacing: 0.5px;
}

h1 {
  margin: 16px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
}

.subtext {
  color: var(--muted);
  max-width: 540px;
  line-height: 1.6;
}

.hero__actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.primary {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  padding: 12px 20px;
  border-radius: 12px;
  background: linear-gradient(120deg, #10a37f, #0b8a6d);
  border: none;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow);
}

.primary.block {
  width: 100%;
}

.metric {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 12px;
  background: var(--panel);
  border: 1px solid var(--border);
}

.metric__value {
  font-weight: 700;
  font-size: 18px;
}

.metric__label {
  color: var(--muted);
  font-size: 14px;
}

.hero__card {
  background: var(--panel);
  border: 1px solid var(--border);
  padding: 18px;
  border-radius: 18px;
  box-shadow: var(--shadow);
}

.card__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.card__hint {
  color: var(--muted);
  margin: 6px 0 14px;
}

.card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.card__chips span {
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--panel-muted);
  color: var(--text);
  font-size: 13px;
}

.admin-hero {
  position: relative;
  padding: 40px clamp(16px, 3vw, 28px);
  overflow: hidden;
}

.admin-hero__backdrop {
  position: absolute;
  inset: 0;
  background: radial-gradient(80% 80% at 20% 30%, rgba(16, 163, 127, 0.16), transparent),
    radial-gradient(60% 60% at 80% 10%, rgba(16, 163, 127, 0.12), transparent);
  filter: blur(30px);
}

.admin-hero__content {
  position: relative;
  width: 100%;
  max-width: none;
  margin: 0 auto;
  z-index: 1;
}

.admin-hero__grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
  align-items: center;
  margin-top: 10px;
}

.admin-hero__stats {
  display: grid;
  gap: 10px;
}

.admin-stat-card {
  padding: 14px;
  border-radius: 16px;
  background: var(--panel);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}

.admin-stat-card h3 {
  margin: 6px 0 2px;
}

.admin-hero__actions {
  flex-wrap: wrap;
}

.admin-hero__chips {
  gap: 10px;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 4;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px clamp(14px, 3vw, 24px);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}

.overview-grid {
  width: 100%;
  max-width: none;
  margin: 0 auto 20px;
  padding: 16px clamp(16px, 4vw, 32px);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 18px;
}

.overview-card {
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 18px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.overview-card[data-variant='accent'] {
  background: linear-gradient(135deg, rgba(16, 163, 127, 0.15), rgba(16, 163, 127, 0.05));
  border-color: rgba(16, 163, 127, 0.3);
}

.overview-card h2 {
  margin: 6px 0;
}

.overview-card small {
  color: var(--muted);
}

.admin-console {
  padding: 10px clamp(14px, 4vw, 28px) 30px;
  width: 100%;
  max-width: none;
  margin: 0 auto 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 12px;
}

.summary-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.summary-card h3 {
  margin: 4px 0;
}

.summary-card small {
  color: var(--muted);
}

.admin-panels {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 14px;
  align-items: start;
}

.admin-panel {
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 14px;
  background: var(--panel);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.admin-table__head,
.admin-table__row {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1.2fr;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
}

.admin-table__head {
  color: var(--muted);
  font-size: 13px;
  border-bottom: 1px solid var(--border);
}

.admin-table__row {
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--panel-muted);
}

.admin-table__actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.admin-table--orders .admin-table__head,
.admin-table--orders .admin-table__row {
  grid-template-columns: 1.4fr 1fr 0.8fr 0.8fr 0.9fr 1.2fr;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #fff;
  font-size: 12px;
}

.status-badge[data-variant='APPROVED'] {
  border-color: #10a37f;
  background: rgba(16, 163, 127, 0.1);
  color: #0f5132;
}

.status-badge[data-variant='PENDING'],
.status-badge[data-variant='UNREVIEWED'] {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.12);
  color: #7c4a03;
}

.status-badge[data-variant='BANNED'] {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.12);
  color: #991b1b;
}

.status-badge[data-variant='NONE'],
.status-badge[data-variant='DEFAULT'] {
  color: var(--muted);
}

.ghost.danger {
  border-color: #ef4444;
  color: #b91c1c;
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
}

.search {
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #fff;
  min-width: 160px;
}

.row-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: space-between;
  padding: 8px 10px;
  border: 1px dashed var(--border);
  border-radius: 12px;
  background: var(--panel);
}

.thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--border);
}

.pager {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
  margin: 10px 0;
}

.single-column {
  max-width: 100%;
  margin: 0 auto 32px;
  padding: 0 clamp(14px, 4vw, 28px);
}

.cart.full {
  width: 100%;
}

.checkout-content {
  display: block;
  max-width: 1600px;
  width: 100%;
  margin: 0 auto 32px;
  padding: 0 clamp(16px, 4vw, 32px);
}

.cart-table {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 12px;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cart-table__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cart-table__row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--panel-muted);
}

.cart-table__head {
  background: var(--panel);
  border-style: dashed;
}

.cart-table__footer {
  background: #e8f5e9;
  border-color: #22c55e;
  color: #0f5132;
}

.content {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(360px, 1fr);
  gap: 24px;
  padding: 0 clamp(14px, 4vw, 28px) 40px;
  max-width: 1600px;
  margin: 0 auto 40px;
  align-items: start;
}

.content.content--full {
  grid-template-columns: minmax(0, 1fr);
  max-width: none;
  width: 100%;
  padding: 0 clamp(12px, 3vw, 32px) 32px;
  margin: 0 auto 32px;
}

.content__main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.eyebrow {
  color: var(--muted);
  margin: 0;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  font-size: 13px;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
}

.chip--active {
  border-color: var(--accent);
  color: #fff;
  background: linear-gradient(120deg, #10a37f, #0b8a6d);
}

.notice {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--panel);
  box-shadow: var(--shadow);
}

.notice[data-variant='error'] {
  border-color: #ef4444;
  color: #b91c1c;
}

.notice[data-variant='success'] {
  border-color: #22c55e;
  color: #15803d;
}

.notice[data-variant='warning'] {
  border-color: #f59e0b;
  color: #b45309;
}

.inline-notice {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #ef4444;
  background: #fef2f2;
  color: #b91c1c;
  margin-bottom: 8px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow);
}

.card__media {
  position: relative;
  width: 100%;
  padding-top: 70%;
  overflow: hidden;
}

.card__media img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pill {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
}

.card__body {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.price {
  font-weight: 700;
  color: var(--accent);
}

.muted {
  color: var(--muted);
  margin: 0;
}

.card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: var(--shadow);
}
.auth-btn {
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid var(--accent);
  background: linear-gradient(120deg, rgba(16, 163, 127, 0.12), rgba(16, 163, 127, 0.05));
  color: #0b8a6d;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow);
}
.auth-btn:hover {
  background: linear-gradient(120deg, rgba(16, 163, 127, 0.18), rgba(16, 163, 127, 0.08));
}

.tag {
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--panel-muted);
  color: var(--accent);
  font-size: 12px;
}

.cart {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 24px;
  align-self: start;
  box-shadow: var(--shadow);
}

.cart__header h3 {
  margin: 6px 0 0;
}

.empty {
  padding: 18px;
  border-radius: 12px;
  background: var(--panel-muted);
  border: 1px dashed var(--border);
}

.cart__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cart__item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-radius: 12px;
  background: var(--panel-muted);
}

.cart__name {
  margin: 0 0 4px;
}

.cart__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cart__actions button {
  background: #fff;
  color: var(--text);
  border: none;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid var(--border);
}

.remove {
  background: #fee2e2 !important;
  color: #b91c1c !important;
}

.divider {
  height: 1px;
  background: var(--border);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
}

input,
textarea {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  font-size: 14px;
}

textarea {
  resize: vertical;
  min-height: 70px;
}

select {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  background: var(--panel-muted);
}

.manager-card {
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 14px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.manager-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.manager-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
  max-height: 70vh;
  overflow: auto;
  padding-right: 4px;
}

.size-panel {
  border: 1px dashed var(--border);
  border-radius: 12px;
  padding: 10px;
  background: var(--panel-muted);
}

.size-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.size-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr auto;
  gap: 8px;
  align-items: center;
  background: #fff;
  padding: 6px;
  border-radius: 8px;
}

.size-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.size-chips .ghost {
  border-color: var(--border);
}

.size-chips .chip--active,
.size-chips .ghost.chip--active {
  background: linear-gradient(120deg, #10a37f, #0b8a6d);
  color: #fff;
  border-color: #0b8a6d;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.auth-block {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
  background: var(--panel);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-panel {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: 16px;
  align-items: start;
}

.ai-chat {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.ai-msg__bubble pre {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-suggestions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-input {
  grid-column: 1 / -1;
  display: flex;
  gap: 10px;
  width: 100%;
  margin-top: 6px;
}

.ai-input textarea {
  flex: 1;
  min-height: 80px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.grid-2x2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.chart-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px;
  background: var(--panel);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 280px;
}

.chart-card__header h3 {
  margin: 0;
}

.chart-line {
  width: 100%;
  height: 80px;
}

.chart-line svg {
  width: 100%;
  height: 100%;
}

.auth-block__info {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 8px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.captcha-row input {
  flex: 1;
  min-width: 0;
}

.captcha-preview {
  width: 132px;
  min-width: 132px;
  height: 44px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-image {
  width: 100%;
  height: 100%;
  display: block;
}

.captcha-image--empty {
  color: var(--muted);
  font-size: 12px;
}

.captcha-refresh {
  white-space: nowrap;
}

.verify-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.verify-row input {
  flex: 1;
  min-width: 0;
}

.verify-btn {
  white-space: nowrap;
}

.loading {
  padding: 16px;
  border-radius: 12px;
  background: var(--panel);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  text-align: center;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--panel-muted);
  border: 1px solid var(--border);
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 12px;
  font-size: 12px;
  border: 1px solid var(--border);
  background: var(--panel-muted);
}

.status-pill[data-variant='APPROVED'] {
  border-color: #10a37f;
  background: rgba(16, 163, 127, 0.08);
  color: #0f5132;
}

.status-pill[data-variant='PENDING'],
.status-pill[data-variant='UNREVIEWED'] {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.08);
  color: #7c4a03;
}

.status-pill[data-variant='BANNED'] {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
  color: #991b1b;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.35), rgba(15, 23, 42, 0.75));
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 20;
  padding: 16px;
  backdrop-filter: blur(6px);
}

.modal {
  width: min(460px, 100%);
  background: radial-gradient(circle at 8% 10%, rgba(16, 163, 127, 0.08), transparent 40%),
    radial-gradient(circle at 90% 15%, rgba(16, 163, 127, 0.06), transparent 50%),
    var(--panel);
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 20px;
  padding: 18px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.25);
  animation: fadeIn 160ms ease, lift 200ms ease;
}

.modal--wide {
  width: min(980px, 100%);
}
.modal--tall {
  min-height: 750px; /* 适度拉长 */
  max-height: 90vh;
  overflow-y: auto;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.edit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.chat-panel {
  position: fixed;
  bottom: 12px;
  right: 12px;
  width: min(420px, 90vw);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  z-index: 13001;
}

.chat-panel__header,
.chat-panel__footer {
  padding: 10px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chat-panel__body {
  max-height: 260px;
  overflow: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-msg {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 8px;
  background: var(--panel-muted);
}

.chat-msg__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-panel__footer input {
  flex: 1;
}

.terminal-shell {
  background: #0f172a;
  color: #e5e7eb;
  border: 1px solid var(--border);
  border-radius: 12px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}

.terminal-box {
  min-height: 220px;
  padding: 10px;
  white-space: pre-wrap;
  overflow: auto;
  background: transparent;
}

.terminal-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0 0;
}

.terminal-inputline {
  flex: 1;
  background: #0b1221;
  color: #e5e7eb;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 8px 10px;
}

.prompt {
  color: #22c55e;
  font-weight: 700;
}

.log-box {
  max-height: none;
  width: 100%;
  min-height: 260px;
  overflow: auto;
  background: #0f172a;
  color: #e5e7eb;
  padding: 10px;
  border-radius: 10px;
  border: 1px solid var(--border);
  white-space: pre-wrap;
}

.auth-status {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px;
  background: var(--panel-muted);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes lift {
  from {
    transform: translateY(8px);
  }
  to {
    transform: translateY(0);
  }
}

@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .hero__content,
  .content {
    grid-template-columns: 1fr;
  }

  .admin-hero__grid,
  .admin-panels {
    grid-template-columns: 1fr;
  }

  .admin-table__head,
  .admin-table__row,
  .admin-table--orders .admin-table__head,
  .admin-table--orders .admin-table__row {
    grid-template-columns: 1fr;
  }

  .cart {
    position: relative;
    top: 0;
  }

  .hero__actions {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 720px) {
  .captcha-row,
  .verify-row {
    flex-wrap: wrap;
  }

  .captcha-preview,
  .captcha-refresh,
  .verify-btn {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .card__footer {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }
  .page {
    padding-left: 0;
  }
}
</style>
