<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, nextTick } from 'vue';
import {
  fetchCategories,
  fetchProducts,
  fetchProductDetail,
  submitOrderBatch,
  fetchLoginCaptcha,
  login,
  logout,
  createProduct,
  register,
  sendRegisterEmailCode,
  fetchAdminOverview,
  fetchAdminMerchants,
  updateMerchantStatus,
  fetchAdminUsers,
  updateAdminUserAccountStatus,
  deleteAdminUser,
  getMerchantProfile,
  saveMerchantProfile,
  fetchAdminOrders,
  uploadImage,
  fetchMyProducts,
  deleteProduct,
  updateProduct,
  approveRefundMerchant,
  rejectRefundMerchant,
  uploadVideo,
  fetchComments,
  addComment,
  getWallet,
  recharge,
  adminResetPassword,
  fetchAdminRevenue,
  fetchChat,
  sendChat,
  fetchRecentChat,
  fetchRefundChat,
  sendRefundChat,
  fetchPaymentLogs,
  fetchMyOrders,
  confirmReceiptOrder,
  submitMerchantOrderReview,
  refundOrder,
  reverseGeocode,
  getLocationConfig,
  fetchAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
  changeMyPassword,
  createAlipayPayUrl,
  confirmAlipayReturn
} from './api';

const ALIPAY_PENDING_STORAGE_KEY = 'mk_alipay_pending';

const categories = ref([]);
const products = ref([]);
const activeCategory = ref('all');
const selectedStoreId = ref('all');
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
const merchantProfileSubmitting = ref(false);
const merchantProfileLoading = ref(false);
const merchantProfile = reactive({
  storeName: '',
  contactName: '',
  contactPhone: '',
  businessAddress: '',
  licenseNumber: '',
  description: ''
});

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
const showRechargeModal = ref(false);
const rechargeAmount = ref(0);
const paymentLogs = ref([]);
const paymentLogsLoading = ref(false);
const adminUsers = ref([]);
const adminUsersLoading = ref(false);
const adminUserSearch = ref('');
const adminUserStatusFilter = ref('ALL');
const adminRevenueLogs = ref([]);
const recentChats = ref([]);
const pwdModalUser = ref(null);
const pwdModalValue = ref('');
const myOrders = ref([]);
const myOrdersLoading = ref(false);
const myOrderSearch = ref('');
const myOrderStatusFilter = ref('ALL');
const myOrderPage = ref(1);
const myOrderPageSize = ref(6);
const refundModal = reactive({
  orderId: null,
  reason: ''
});
const showRefundModal = ref(false);
const refundSubmitting = ref(false);
const refundReviewSubmitting = ref(false);
const receiptSubmitting = ref(false);
const reviewModal = reactive({
  orderId: null,
  orderNumber: '',
  merchantName: '',
  rating: 5,
  content: ''
});
const showReviewModal = ref(false);
const reviewSubmitting = ref(false);
const reviewError = ref('');
const repayOrderId = ref(null);
const refundError = ref('');
const useGeoLoading = ref(false);
const showMapModal = ref(false);
const mapJsKey = ref('');
const mapJsSec = ref('');
const mapPickerLoading = ref(false);
const mapSelectedPoint = ref(null);
const mapSelectedAddress = ref('');
const mapApplyTarget = ref('order'); // 'order' | 'profile'
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
const actionDialog = reactive({
  visible: false,
  title: '',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  showCancel: true,
  variant: 'info'
});
let actionDialogResolver = null;
const currentChat = reactive({
  mode: 'product',
  productId: null,
  orderId: null,
  targetId: null,
  title: '',
  subtitle: '',
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
const orderSearch = ref('');
const lowStock = ref([]);
const lowStockLoading = ref(false);
const lowStockPage = ref(1);
const lowStockPageSize = ref(6);
const lowStockThreshold = ref(5);
const uploadingImage = ref(false);
const editingProductId = ref(null);
const productFormVideoFile = ref(null);
const detailProduct = ref(null);
const detailComments = ref([]);
const detailLoading = ref(false);
const detailCommentInput = ref('');
const toastState = reactive({ visible: false, message: '', type: 'info' });

const isLoggedIn = computed(() => !!auth.token);
const isMerchantApproved = computed(() => auth.role === 'MERCHANT' && auth.merchantStatus === 'APPROVED');
const isMerchantBanned = computed(() => auth.role === 'MERCHANT' && auth.merchantStatus === 'BANNED');
const isMerchantPendingReview = computed(() =>
  auth.role === 'MERCHANT' && ['UNREVIEWED', 'PENDING'].includes((auth.merchantStatus || '').toUpperCase())
);
const hasPendingMerchantBadge = computed(() => isAdmin.value && Number(adminOverview.pendingMerchants || 0) > 0);
const isAdmin = computed(() => auth.role === 'ADMIN');
const isBuyerOrderView = computed(() => auth.role !== 'MERCHANT');
const hasUserWalletUi = computed(() => auth.role !== 'USER' && !isMerchantPendingReview.value);
const merchantStatusLabel = computed(() => {
  switch (auth.merchantStatus) {
    case 'APPROVED':
      return '已审核';
    case 'PENDING':
      return '待审核';
    case 'UNREVIEWED':
      return '待提交资料';
    case 'BANNED':
      return '已封禁';
    default:
      return '普通用户';
  }
});

const cartCount = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0));
const cartTotal = computed(() =>
  cart.value.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0).toFixed(2)
);
const cartMerchantGroups = computed(() => {
  const groups = new Map();
  cart.value.forEach((item) => {
    const ownerId = item.owner?.id || `merchant-${item.id}`;
    const ownerName = item.owner?.username || '商家';
    if (!groups.has(ownerId)) {
      groups.set(ownerId, {
        ownerId,
        ownerName,
        items: [],
        count: 0,
        subtotal: 0
      });
    }
    const group = groups.get(ownerId);
    group.items.push(item);
    group.count += Number(item.quantity || 0);
    group.subtotal += Number(item.price || 0) * Number(item.quantity || 0);
  });
  return Array.from(groups.values()).map((group) => ({
    ...group,
    subtotalText: group.subtotal.toFixed(2),
    previewText: group.items
      .map((item) => `${item.name}${item.sizeLabel ? ` / ${item.sizeLabel}` : ''}`)
      .slice(0, 3)
      .join('、')
  }));
});
const cartMerchantCount = computed(() => cartMerchantGroups.value.length);
const checkoutPreviewAddress = computed(() =>
  addressList.value.find((addr) => addr.id === selectedAddressId.value)
  || addressList.value.find((addr) => addr.default)
  || addressList.value[0]
  || null
);
const selectedPayAddress = computed(() =>
  addressList.value.find((addr) => addr.id === payModalAddressId.value) || null
);
const selectedPayMethodMeta = computed(() => ({
  label: '支付宝沙箱支付',
  desc: cartMerchantCount.value > 1
    ? `将拆分为 ${cartMerchantCount.value} 笔商家订单，并合并拉起一次支付宝沙箱支付`
    : '支付成功后订单进入平台托管，确认收货后再结算给商家'
}));
const sizeTableStock = computed(() => {
  const rows = normalizeSizeRows();
  if (rows.length) {
    return rows.reduce((sum, s) => sum + Number(s.stock || 0), 0);
  }
  return Number(productForm.stock || 0);
});

const catalogStoreOptions = computed(() => {
  const stores = new Map();
  products.value.forEach((product) => {
    const ownerId = product?.owner?.id;
    if (!ownerId) return;
    const key = String(ownerId);
    if (!stores.has(key)) {
      stores.set(key, {
        id: key,
        name: resolveStoreName(product),
        rating: Number(product?.merchantRatingAverage || 0),
        productCount: 0
      });
    }
    stores.get(key).productCount += 1;
  });
  return Array.from(stores.values()).sort((a, b) =>
    a.rating - b.rating || a.name.localeCompare(b.name, 'zh-CN')
  );
});
const filteredCatalogProducts = computed(() => {
  if (selectedStoreId.value === 'all') {
    return products.value;
  }
  return products.value.filter((product) => String(product?.owner?.id || '') === String(selectedStoreId.value));
});
const pagedProducts = computed(() => {
  const start = (productPage.value - 1) * productPageSize.value;
  return filteredCatalogProducts.value.slice(start, start + productPageSize.value);
});
const productTotalPages = computed(() => Math.max(1, Math.ceil(filteredCatalogProducts.value.length / productPageSize.value || 1)));
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
  return adminMerchants.value;
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
  if (auth.role === 'MERCHANT' && currentPage.value === 'profile') {
    return '商家资料';
  }
  const map = {
    catalog: '商品',
    checkout: '结算',
    profile: '个人中心',
    orders: '订单',
    walletUser: '钱包',
    chat: '聊天',
    merchantUpload: '上架商品',
    myShop: '我的店铺',
    merchantStock: '库存告警',
    adminOverview: '概览',
    adminUsers: '用户',
    adminMerchants: '商家审核',
    adminOrders: '订单管理',
    adminRevenue: '收益'
  };
  return map[currentPage.value] || '欢迎';
});
const topAuthHint = computed(() => {
  if (auth.role === 'USER') {
    return '仅支持支付宝沙箱下单';
  }
  if (auth.role === 'MERCHANT' && !isMerchantApproved.value) {
    return `审核状态：${merchantStatusLabel.value}`;
  }
  if (isAdmin.value) {
    return `平台余额 ￥${Number(walletBalance.value).toFixed(2)}`;
  }
  return `余额 ￥${Number(walletBalance.value).toFixed(2)}`;
});
const supportedPages = new Set([
  'catalog',
  'checkout',
  'profile',
  'orders',
  'walletUser',
  'chat',
  'merchantUpload',
  'myShop',
  'merchantStock',
  'adminOverview',
  'adminUsers',
  'adminMerchants',
  'adminOrders',
  'adminRevenue'
]);
const myOrdersSorted = computed(() =>
  [...myOrders.value].sort((a, b) => {
    const timeA = a?.createdAt ? Number(new Date(a.createdAt).getTime()) || 0 : 0;
    const timeB = b?.createdAt ? Number(new Date(b.createdAt).getTime()) || 0 : 0;
    return timeB - timeA;
  })
);
const myOrderStatusTabs = computed(() => {
  const options = [
    { key: 'ALL', label: '全部订单' },
    { key: 'PENDING_ACTION', label: isBuyerOrderView.value ? '待我处理' : '待商家处理' },
    { key: 'ESCROW', label: '托管中' },
    { key: 'REFUND', label: '退款相关' },
    { key: 'DONE', label: '已完成' }
  ];
  return options.map((option) => ({
    ...option,
    count: myOrdersSorted.value.filter((order) => matchesMyOrderFilter(order, option.key)).length
  }));
});
const filteredMyOrders = computed(() => {
  const q = myOrderSearch.value.trim().toLowerCase();
  return myOrdersSorted.value.filter((order) => {
    if (!matchesMyOrderFilter(order, myOrderStatusFilter.value)) {
      return false;
    }
    if (!q) {
      return true;
    }
    const haystack = [
      order?.orderNumber,
      formatOrderStatus(order?.status),
      order?.refundReason,
      formatPayMethodShort(order?.payMethod),
      formatOrderItemPreview(order)
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(q);
  });
});
const pagedMyOrders = computed(() => {
  const start = (myOrderPage.value - 1) * myOrderPageSize.value;
  return filteredMyOrders.value.slice(start, start + myOrderPageSize.value);
});
const myOrderTotalPages = computed(() => Math.max(1, Math.ceil(filteredMyOrders.value.length / myOrderPageSize.value || 1)));
const myOrderSummary = computed(() => {
  const totalCount = myOrdersSorted.value.length;
  const totalAmount = myOrdersSorted.value.reduce((sum, order) => sum + Number(order?.totalAmount || 0), 0);
  const pendingActionCount = myOrdersSorted.value.filter((order) =>
    isBuyerOrderView.value ? needsBuyerAction(order?.status) : needsMerchantAction(order?.status)
  ).length;
  const escrowAmount = myOrdersSorted.value
    .filter((order) => isEscrowOrder(order?.status))
    .reduce((sum, order) => sum + Number(order?.totalAmount || 0), 0);
  const refundCount = myOrdersSorted.value.filter((order) =>
    ['REFUND_REQUESTED', 'REFUNDED', 'REJECTED'].includes(normalizeOrderStatus(order?.status))
  ).length;
  return {
    totalCount,
    totalAmount: totalAmount.toFixed(2),
    pendingActionCount,
    pendingActionLabel: isBuyerOrderView.value ? '待我处理' : '待商家处理',
    escrowAmount: escrowAmount.toFixed(2),
    refundCount
  };
});
const visiblePaymentLogs = computed(() => paymentLogs.value);
const filteredAdminUsers = computed(() => {
  const query = adminUserSearch.value.trim().toLowerCase();
  return adminUsers.value.filter((user) => {
    const normalizedStatus = normalizeAccountStatus(user?.accountStatus);
    if (adminUserStatusFilter.value !== 'ALL' && normalizedStatus !== adminUserStatusFilter.value) {
      return false;
    }
    if (!query) {
      return true;
    }
    const haystack = [
      user?.username,
      user?.email,
      user?.role,
      user?.merchantStatus,
      user?.merchantStoreName,
      renderAccountStatusLabel(user?.accountStatus)
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(query);
  });
});

onMounted(() => {
  const nextPage = normalizePage(currentPage.value);
  if (nextPage !== currentPage.value) {
    currentPage.value = nextPage;
    localStorage.setItem('mk_page', nextPage);
  }
  loadMapConfig();
  loadCategories();
  loadProducts();
  if (auth.role === 'MERCHANT' && isMerchantApproved.value) {
    loadMyProducts();
    if (currentPage.value === 'merchantStock') {
      loadLowStock();
    }
  }
  if (isAdmin.value) {
    loadAdminAll();
    if (currentPage.value === 'adminUsers') {
      loadAdminUsers();
    }
    if (currentPage.value === 'adminRevenue') {
      loadAdminRevenue();
    }
  }
  if (isLoggedIn.value) {
    refreshPaymentSideData();
    if (!isMerchantPendingReview.value) {
      loadRecentChats();
    }
    if (auth.role === 'MERCHANT') {
      loadMerchantProfile();
    } else {
      loadAddresses();
    }
  }
  window.addEventListener('focus', handleWindowFocus);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  syncPendingAlipayState();
});

onBeforeUnmount(() => {
  clearRegisterEmailTimer();
  window.removeEventListener('focus', handleWindowFocus);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

watch(isAdmin, (value) => {
  if (value) {
    currentPage.value = 'adminOverview';
    loadAdminAll();
    loadAdminUsers();
  } else if (currentPage.value.startsWith('admin')) {
    currentPage.value = 'catalog';
  }
  localStorage.setItem('mk_page', currentPage.value);
});

watch(() => auth.merchantStatus, () => {
  const nextPage = normalizePage(currentPage.value);
  if (nextPage !== currentPage.value) {
    currentPage.value = nextPage;
    localStorage.setItem('mk_page', nextPage);
  }
});

watch([myOrderSearch, myOrderStatusFilter, myOrders], () => {
  myOrderPage.value = 1;
});

async function loadMapConfig() {
  try {
    const cfg = await getLocationConfig();
    mapJsKey.value = cfg.mapJsKey || '';
    mapJsSec.value = cfg.mapJsSec || '';
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
    if (selectedStoreId.value !== 'all' && !products.value.some((product) => String(product?.owner?.id || '') === String(selectedStoreId.value))) {
      selectedStoreId.value = 'all';
    }
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
  await Promise.all([loadAdminOverview(), loadAdminMerchants(adminFilters.merchantStatus), loadAdminOrders(), loadWallet()]);
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

async function reviewRefundByMerchant(order, pass) {
  const orderId = order?.orderId || order?.id;
  if (!orderId || refundReviewSubmitting.value) return;
  const actionText = pass ? '同意' : '驳回';
  const ok = await openActionDialog({
    title: `${actionText}退款申请`,
    message: pass
      ? `确认同意订单 ${order.orderNumber} 的退款申请吗？审核通过后系统会按订单状态执行退款。`
      : `确认驳回订单 ${order.orderNumber} 的退款申请吗？驳回后订单会恢复为原状态。`,
    confirmText: actionText,
    cancelText: '取消',
    variant: pass ? 'warning' : 'info'
  });
  if (!ok) {
    return;
  }

  refundReviewSubmitting.value = true;
  try {
    if (pass) {
      await approveRefundMerchant(orderId);
      setNotice('success', '已同意退款，系统已完成退款');
    } else {
      await rejectRefundMerchant(orderId);
      setNotice('info', '已驳回退款申请');
    }
    await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
  } catch (err) {
    const msg = err?.response?.data?.message || '操作失败';
    setNotice('error', msg);
  } finally {
    refundReviewSubmitting.value = false;
  }
}

async function loadAdminUsers() {
  if (!isAdmin.value) return;
  adminUsersLoading.value = true;
  try {
    adminUsers.value = await fetchAdminUsers();
  } catch (err) {
    setNotice('error', '用户列表加载失败');
  } finally {
    adminUsersLoading.value = false;
  }
}

async function loadWallet() {
  if (!hasUserWalletUi.value) {
    walletBalance.value = 0;
    return;
  }
  try {
    const res = await getWallet();
    walletBalance.value = res.balance || 0;
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
  if (auth.role === 'USER') {
    setNotice('warning', '普通用户下单仅支持支付宝沙箱支付，暂不提供钱包充值');
    return;
  }
  if (!rechargeAmount.value || Number(rechargeAmount.value) <= 0) {
    setNotice('warning', '请输入大于 0 的金额');
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

async function resetUserPassword(userId, password) {
  try {
    await adminResetPassword(userId, password);
    setNotice('success', '密码已修改');
    await loadAdminUsers();
  } catch (err) {
    const msg = err?.response?.data?.message || '修改失败';
    setNotice('error', msg);
  }
}

async function toggleAdminUserBan(user) {
  if (!canManageAdminUser(user)) return;
  const nextStatus = normalizeAccountStatus(user.accountStatus) === 'BANNED' ? 'ACTIVE' : 'BANNED';
  const actionText = nextStatus === 'BANNED' ? '封禁' : '解封';
  const ok = await openActionDialog({
    title: `${actionText}账号`,
    message: nextStatus === 'BANNED'
      ? `确认封禁用户 ${user.username} 吗？封禁后该账号将无法登录和访问系统。`
      : `确认解封用户 ${user.username} 吗？解封后该账号可以重新登录。`,
    confirmText: actionText,
    cancelText: '取消',
    variant: nextStatus === 'BANNED' ? 'warning' : 'success'
  });
  if (!ok) return;

  try {
    await updateAdminUserAccountStatus(user.id, nextStatus);
    setNotice('success', `账号已${actionText}`);
    await refreshAdminUserManagement();
  } catch (err) {
    const msg = err?.response?.data?.message || `${actionText}失败`;
    setNotice('error', msg);
  }
}

async function removeAdminUser(user) {
  if (!canManageAdminUser(user)) return;
  const ok = await openActionDialog({
    title: '删除账号',
    message: `确认删除用户 ${user.username} 吗？该操作会将账号标记为已删除，并阻止其继续登录。`,
    confirmText: '删除',
    cancelText: '取消',
    variant: 'error'
  });
  if (!ok) return;

  try {
    await deleteAdminUser(user.id);
    setNotice('success', '账号已删除');
    await refreshAdminUserManagement();
  } catch (err) {
    const msg = err?.response?.data?.message || '删除失败';
    setNotice('error', msg);
  }
}

async function loadAdminRevenue() {
  if (!isAdmin.value) return;
  try {
    const [logs] = await Promise.all([fetchAdminRevenue(), loadWallet()]);
    adminRevenueLogs.value = logs || [];
  } catch (err) {
    setNotice('error', '收益记录加载失败');
  }
}

async function loadLowStock() {
  lowStockLoading.value = true;
  try {
    const data = auth.role === 'MERCHANT' ? await fetchMyProducts() : await fetchProducts();
    const threshold = Number(lowStockThreshold.value || 0);
    if (auth.role === 'MERCHANT') {
      myProducts.value = data || [];
    }
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
      return '待提交资料';
    case 'BANNED':
      return '已封禁';
    default:
      return '普通';
  }
}

function normalizeAccountStatus(status) {
  return (status || 'ACTIVE').toUpperCase();
}

function renderAccountStatusLabel(status) {
  switch (normalizeAccountStatus(status)) {
    case 'BANNED':
      return '已封禁';
    case 'DELETED':
      return '已删除';
    default:
      return '正常';
  }
}

function canManageAdminUser(user) {
  if (!user) return false;
  if ((user.username || '') === auth.username) return false;
  if ((user.role || '').toUpperCase() === 'ADMIN') return false;
  return normalizeAccountStatus(user.accountStatus) !== 'DELETED';
}

async function refreshAdminUserManagement() {
  await Promise.all([
    loadAdminUsers(),
    loadAdminOverview(),
    loadAdminMerchants(adminFilters.merchantStatus)
  ]);
}

async function refreshPaymentSideData() {
  if (hasUserWalletUi.value) {
    await Promise.all([loadWallet(), loadPaymentLogs()]);
    return;
  }
  walletBalance.value = 0;
  paymentLogs.value = [];
  paymentLogsLoading.value = false;
}

function markPendingAlipayRedirect() {
  sessionStorage.setItem(ALIPAY_PENDING_STORAGE_KEY, String(Date.now()));
}

function clearPendingAlipayRedirect() {
  sessionStorage.removeItem(ALIPAY_PENDING_STORAGE_KEY);
}

function hasPendingAlipayRedirect() {
  const startedAt = Number(sessionStorage.getItem(ALIPAY_PENDING_STORAGE_KEY) || 0);
  return Number.isFinite(startedAt) && startedAt > 0 && (Date.now() - startedAt) < 30 * 60 * 1000;
}

function hasAlipayReturnParams() {
  const params = new URLSearchParams(window.location.search || '');
  return params.has('out_trade_no') && (params.has('trade_status') || params.has('sign'));
}

function readAlipayReturnParams() {
  const params = new URLSearchParams(window.location.search || '');
  return Object.fromEntries(params.entries());
}

function clearCurrentSearch() {
  if (!window.location.search) return;
  const nextUrl = `${window.location.pathname}${window.location.hash || ''}`;
  window.history.replaceState({}, document.title, nextUrl);
}

async function handleAlipayReturnFromUrl() {
  if (!hasAlipayReturnParams()) {
    return false;
  }

  currentPage.value = 'orders';
  localStorage.setItem('mk_page', 'orders');

  const payload = readAlipayReturnParams();
  const tradeStatus = normalizeOrderStatus(payload.trade_status);

  try {
    if (tradeStatus && !['TRADE_SUCCESS', 'TRADE_FINISHED'].includes(tradeStatus)) {
      if (isLoggedIn.value) {
        await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
      }
      setNotice('info', '支付未完成，可在订单页继续支付。');
      return true;
    }

    const result = await confirmAlipayReturn(payload);
    if (isLoggedIn.value) {
      await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
    }
    if (result?.success) {
      setNotice('success', '支付成功，订单状态已更新，已可申请售后或确认收货。');
    } else {
      setNotice('warning', '已返回站点，但支付结果未完成自动校验，请刷新订单列表确认。');
    }
  } catch (err) {
    const msg = err?.response?.data?.message || '支付结果同步失败，请刷新订单列表后重试。';
    if (isLoggedIn.value) {
      await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
    }
    setNotice('warning', msg);
  } finally {
    clearPendingAlipayRedirect();
    clearCurrentSearch();
  }

  return true;
}

async function syncPendingAlipayState() {
  const handledReturn = await handleAlipayReturnFromUrl();
  if (handledReturn || !hasPendingAlipayRedirect() || !isLoggedIn.value) {
    return;
  }

  try {
    await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
  } finally {
    clearPendingAlipayRedirect();
  }
}

function handleWindowFocus() {
  syncPendingAlipayState();
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    syncPendingAlipayState();
  }
}

function normalizeOrderStatus(status) {
  return (status || '').toUpperCase();
}

function isEscrowOrder(status) {
  return ['PLACED', 'PENDING_ADMIN'].includes(normalizeOrderStatus(status));
}

function needsBuyerAction(status) {
  return ['PENDING_PAYMENT', 'PLACED', 'PENDING_ADMIN'].includes(normalizeOrderStatus(status));
}

function needsMerchantAction(status) {
  return normalizeOrderStatus(status) === 'REFUND_REQUESTED';
}

function getOrderStatusVariant(status) {
  switch (normalizeOrderStatus(status)) {
    case 'PENDING_PAYMENT':
      return 'PENDING_PAYMENT';
    case 'PLACED':
    case 'PENDING_ADMIN':
      return 'PLACED';
    case 'APPROVED':
      return 'APPROVED';
    case 'REFUND_REQUESTED':
      return 'REFUND_REQUESTED';
    case 'REFUNDED':
      return 'REFUNDED';
    case 'REJECTED':
      return 'REJECTED';
    default:
      return 'DEFAULT';
  }
}

function formatPayMethodShort(payMethod) {
  switch ((payMethod || '').toUpperCase()) {
    case 'ALIPAY':
      return '支付宝沙箱支付';
    case 'WALLET':
      return '历史钱包支付';
    default:
      return payMethod || '未知方式';
  }
}

function countOrderItems(order) {
  return (order?.items || []).reduce((sum, item) => sum + Number(item?.quantity || 0), 0);
}

function formatOrderItemPreview(order) {
  const items = order?.items || [];
  return items
    .map((item) => `${item.productName || '商品'} ×${Number(item.quantity || 0)}${item.sizeLabel ? ` / ${item.sizeLabel}` : ''}`)
    .join(' ');
}

function getOrderProgressMeta(status) {
  switch (normalizeOrderStatus(status)) {
    case 'PENDING_PAYMENT':
      return {
        percent: 24,
        label: '等待支付完成',
        desc: '订单已创建，支付成功后资金会先进入平台托管。'
      };
    case 'PLACED':
    case 'PENDING_ADMIN':
      return {
        percent: 68,
        label: '已支付，等待买家确认收货',
        desc: '订单已支付，当前资金托管在平台；如需售后可直接发起退款申请。'
      };
    case 'APPROVED':
      return {
        percent: 100,
        label: '订单已完成',
        desc: '买家已确认收货，平台已完成抽佣并结算给商家。'
      };
    case 'REFUND_REQUESTED':
      return {
        percent: 78,
        label: '退款申请待审核',
        desc: '商家审核通过后，系统会按订单状态执行退款。'
      };
    case 'REFUNDED':
      return {
        percent: 100,
        label: '订单已退款',
        desc: '退款已完成，相关资金已按托管或结算状态回滚。'
      };
    case 'REJECTED':
      return {
        percent: 90,
        label: '订单处理结束',
        desc: '当前订单已被拒绝或恢复原状态，请以最新状态为准。'
      };
    default:
      return {
        percent: 40,
        label: '订单处理中',
        desc: '订单状态已更新，请按当前提示继续操作。'
      };
  }
}

function matchesMyOrderFilter(order, filterKey) {
  const status = normalizeOrderStatus(order?.status);
  switch (filterKey) {
    case 'ALL':
      return true;
    case 'PENDING_ACTION':
      return isBuyerOrderView.value ? needsBuyerAction(status) : needsMerchantAction(status);
    case 'ESCROW':
      return isEscrowOrder(status);
    case 'REFUND':
      return ['REFUND_REQUESTED', 'REFUNDED', 'REJECTED'].includes(status);
    case 'DONE':
      return status === 'APPROVED';
    default:
      return status === filterKey;
  }
}


function formatOrderStatus(status) {
  switch ((status || '').toUpperCase()) {
    case 'PENDING_PAYMENT':
      return '待支付';
    case 'PLACED':
    case 'PENDING_ADMIN':
      return '已支付';
    case 'APPROVED':
      return '已确认收货';
    case 'REFUND_REQUESTED':
      return '退款待商家审核';
    case 'REJECTED':
      return '已拒绝';
    case 'REFUNDED':
      return '已退款';
    default:
      return status || '-';
  }
}

function formatOrderStatusWithPayMethod(status, payMethod) {
  const normalizedStatus = (status || '').toUpperCase();
  const normalizedPayMethod = (payMethod || '').toUpperCase();
  const payMethodLabel = normalizedPayMethod === 'ALIPAY'
    ? '支付宝沙箱支付'
    : normalizedPayMethod === 'WALLET'
      ? '历史钱包支付'
      : normalizedStatus === 'PENDING_PAYMENT'
        ? '支付宝沙箱支付'
        : '未知支付方式';
  const suffix = `（${payMethodLabel}）`;

  switch (normalizedStatus) {
    case 'PENDING_PAYMENT':
      return `待支付${suffix}`;
    case 'PLACED':
    case 'PENDING_ADMIN':
      return `已支付，待确认收货，资金托管中${suffix}`;
    case 'APPROVED':
      return `已确认收货，已结算${suffix}`;
    case 'REFUND_REQUESTED':
      return `退款待商家审核${suffix}`;
    case 'REJECTED':
      return `已拒绝${suffix}`;
    case 'REFUNDED':
      return `已退款${suffix}`;
    default:
      return status ? `${status}${suffix}` : '-';
  }
}

function canRefund(status) {
  return ['PENDING_ADMIN', 'PLACED', 'APPROVED'].includes((status || '').toUpperCase());
}

function hasMerchantReview(order) {
  return Boolean(order?.merchantReviewed || Number(order?.merchantRating || 0) > 0 || String(order?.merchantReview || '').trim());
}

function canReviewMerchant(order) {
  return isBuyerOrderView.value && normalizeOrderStatus(order?.status) === 'APPROVED' && !hasMerchantReview(order);
}

function renderMerchantRating(rating) {
  const normalized = Math.max(0, Math.min(5, Number(rating || 0)));
  return `${'★'.repeat(normalized)}${'☆'.repeat(5 - normalized)}`;
}

function renderAverageRating(rating) {
  return Number(rating || 0).toFixed(1);
}

function renderAverageRatingStars(rating) {
  return renderMerchantRating(Math.round(Number(rating || 0)));
}

function resolveStoreName(product) {
  return product?.owner?.merchantStoreName || product?.owner?.username || '店铺';
}

function canReviewRefund(status) {
  return (status || '').toUpperCase() === 'REFUND_REQUESTED';
}

function canOpenRefundChat(order) {
  if (!order) return false;
  const status = normalizeOrderStatus(order.status);
  return status === 'REFUND_REQUESTED' || status === 'REFUNDED' || Number(order.refundChatCount || 0) > 0;
}

function getRefundChatLabel(order) {
  const count = Number(order?.refundChatCount || 0);
  return count > 0 ? `售后沟通 (${count})` : '售后沟通';
}

function canConfirmReceipt(status) {
  return ['PLACED', 'PENDING_ADMIN'].includes((status || '').toUpperCase());
}

function submitAlipayForm(payResp) {
  if (!payResp || ((!payResp.gateway || !payResp.params) && !payResp.redirectUrl)) {
    setNotice('error', '未获取到支付参数');
    openActionDialog({
      title: '支付参数缺失',
      message: '订单已创建，但暂时没有拿到支付宝支付参数。你可以稍后到订单页继续支付。',
      confirmText: '知道了',
      showCancel: false,
      variant: 'error'
    });
    return false;
  }

  if (payResp?.gateway && payResp?.params) {
    markPendingAlipayRedirect();
    const form = document.createElement('form');
    form.method = 'POST';
    form.acceptCharset = 'UTF-8';
    form.enctype = 'application/x-www-form-urlencoded';
    form.target = '_self';
    form.style.display = 'none';
    form.action = payResp.gateway;
    Object.entries(payResp.params).forEach(([k, v]) => {
      if (v == null) return;
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = k;
      input.value = String(v);
      form.appendChild(input);
    });
    document.body.appendChild(form);
    form.submit();
    window.setTimeout(() => {
      if (form.parentNode) {
        form.parentNode.removeChild(form);
      }
    }, 1000);
    return true;
  }

  const redirectUrl = typeof payResp?.redirectUrl === 'string' ? payResp.redirectUrl.trim() : '';
  if (redirectUrl) {
    markPendingAlipayRedirect();
    window.location.assign(redirectUrl);
    return true;
  }

  return false;
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
    case 'BATCH':
      return '支付批次';
    case 'INCOME':
      return '收入';
    case 'REFUND':
      return '退款';
    case 'ESCROW':
      return '托管';
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

function selectStore(storeId) {
  selectedStoreId.value = storeId || 'all';
  productPage.value = 1;
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
    setNotice('warning', sizeList.length ? '该尺码暂时无库存' : '库存不足');
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
  showToast(`已加入购物车：${product.name}`, 'success');
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

function showToast(message, type = 'info') {
  toastState.message = message;
  toastState.type = type;
  toastState.visible = true;
  setTimeout(() => {
    toastState.visible = false;
  }, 2000);
}

async function openProductDetail(product) {
  detailProduct.value = product;
  detailComments.value = [];
  detailLoading.value = true;
  detailCommentInput.value = '';
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
    const [productDetail, comments] = await Promise.all([
      fetchProductDetail(product.id).catch(() => product),
      fetchComments(product.id).catch(() => [])
    ]);
    detailProduct.value = productDetail || product;
    detailComments.value = comments || [];
  } catch (e) {
    // ignore
  } finally {
    detailLoading.value = false;
  }
}

function closeProductDetail() {
  detailProduct.value = null;
  detailComments.value = [];
  detailCommentInput.value = '';
}

function normalizePage(page) {
  if (!isLoggedIn.value) {
    return 'catalog';
  }
  if (isAdmin.value) {
    if (!supportedPages.has(page) || !String(page).startsWith('admin')) {
      return 'adminOverview';
    }
    return page;
  }
  if (!supportedPages.has(page)) {
    return auth.role === 'MERCHANT' && isMerchantPendingReview.value ? 'profile' : 'catalog';
  }
  if (String(page).startsWith('admin')) {
    return 'catalog';
  }
  if (auth.role === 'MERCHANT' && isMerchantPendingReview.value && page !== 'profile') {
    return 'profile';
  }
  if (auth.role === 'USER' && page === 'walletUser') {
    return 'orders';
  }
  return page;
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
    await openActionDialog({
      title: '还没选择收货地址',
      message: '请先在地址列表中选择一个收货地址，再继续提交订单。',
      confirmText: '知道了',
      showCancel: false,
      variant: 'warning'
    });
    return;
  }
  const addr = addressList.value.find((a) => a.id === payModalAddressId.value);
  if (!addr) {
    setNotice('warning', '所选地址无效，请刷新后重试');
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
    setNotice('warning', '请输入有效的中国大陆手机号');
    await openActionDialog({
      title: '手机号格式不正确',
      message: '请填写 11 位中国大陆手机号，例如 13800138000。',
      confirmText: '重新填写',
      showCancel: false,
      variant: 'warning'
    });
    return;
  }

  submitting.value = true;
  let createdOrders = [];
  let paymentStarted = false;
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
    const res = await submitOrderBatch(payload);
    createdOrders = res?.orders || [];
    if (!createdOrders.length) {
      throw new Error('未创建任何订单');
    }
    cart.value = [];
    await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
    setNotice('success', `已创建 ${createdOrders.length} 笔订单，正在跳转支付宝沙箱支付`);
    const payResp = await createAlipayPayUrl({
      orderIds: createdOrders.map((order) => order.orderId || order.id)
    });
    paymentStarted = submitAlipayForm(payResp);
  } catch (err) {
    const fallback = createdOrders.length
      ? '订单已创建，可在订单页继续支付'
      : '下单失败，请稍后重试';
    const msg = err?.response?.data?.message || err?.message || fallback;
    setNotice('error', msg);
  } finally {
    submitting.value = false;
    if (!paymentStarted) {
      showPayModal.value = false;
    }
  }
}

function canPayPending(order) {
  return isBuyerOrderView.value
    && (order?.status || '').toUpperCase() === 'PENDING_PAYMENT'
    && (order?.payMethod || '').toUpperCase() === 'ALIPAY';
}

async function payPendingOrder(order) {
  const orderId = order?.orderId || order?.id;
  if (!orderId || repayOrderId.value) return;
  repayOrderId.value = orderId;
  try {
    const payResp = await createAlipayPayUrl(orderId);
    submitAlipayForm(payResp);
  } catch (err) {
    const msg = err?.response?.data?.message || '拉起支付失败';
    setNotice('error', msg);
  } finally {
    repayOrderId.value = null;
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

function resetMerchantProfile() {
  merchantProfile.storeName = '';
  merchantProfile.contactName = '';
  merchantProfile.contactPhone = '';
  merchantProfile.businessAddress = '';
  merchantProfile.licenseNumber = '';
  merchantProfile.description = '';
}

function applyMerchantProfile(data) {
  merchantProfile.storeName = data?.storeName || '';
  merchantProfile.contactName = data?.contactName || '';
  merchantProfile.contactPhone = data?.contactPhone || '';
  merchantProfile.businessAddress = data?.businessAddress || '';
  merchantProfile.licenseNumber = data?.licenseNumber || '';
  merchantProfile.description = data?.description || '';
  if (data?.merchantStatus) {
    auth.merchantStatus = data.merchantStatus;
    localStorage.setItem('mk_merchant_status', auth.merchantStatus);
  }
}

async function loadMerchantProfile() {
  if (!isLoggedIn.value || auth.role !== 'MERCHANT') return;
  merchantProfileLoading.value = true;
  try {
    const data = await getMerchantProfile();
    applyMerchantProfile(data);
  } catch (err) {
    const msg = err?.response?.data?.message || '无法加载商家资料';
    setNotice('error', msg);
  } finally {
    merchantProfileLoading.value = false;
  }
}

async function submitMerchantProfile(submitForReview = false) {
  if (merchantProfileSubmitting.value || auth.role !== 'MERCHANT') return;
  if (
    submitForReview
    && (!merchantProfile.storeName.trim()
      || !merchantProfile.contactName.trim()
      || !merchantProfile.contactPhone.trim()
      || !merchantProfile.businessAddress.trim()
      || !merchantProfile.licenseNumber.trim())
  ) {
    setNotice('warning', '请先填写完整的商家基本信息再提交审核');
    return;
  }
  merchantProfileSubmitting.value = true;
  try {
    const data = await saveMerchantProfile({
      storeName: merchantProfile.storeName,
      contactName: merchantProfile.contactName,
      contactPhone: merchantProfile.contactPhone,
      businessAddress: merchantProfile.businessAddress,
      licenseNumber: merchantProfile.licenseNumber,
      description: merchantProfile.description,
      submitForReview
    });
    applyMerchantProfile(data);
    const nextPage = normalizePage(currentPage.value);
    currentPage.value = nextPage;
    localStorage.setItem('mk_page', nextPage);
    setNotice('success', submitForReview ? '资料已提交，等待管理员审核' : '商家资料已保存');
  } catch (err) {
    const msg = err?.response?.data?.message || (submitForReview ? '提交审核失败' : '保存资料失败');
    setNotice('error', msg);
  } finally {
    merchantProfileSubmitting.value = false;
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

function closeActionDialog(result = false) {
  actionDialog.visible = false;
  if (actionDialogResolver) {
    actionDialogResolver(result);
    actionDialogResolver = null;
  }
}

function openActionDialog({
  title,
  message,
  confirmText = '确定',
  cancelText = '取消',
  showCancel = true,
  variant = 'info'
}) {
  if (actionDialogResolver) {
    actionDialogResolver(false);
    actionDialogResolver = null;
  }
  actionDialog.title = title || '提示';
  actionDialog.message = message || '';
  actionDialog.confirmText = confirmText;
  actionDialog.cancelText = cancelText;
  actionDialog.showCancel = showCancel;
  actionDialog.variant = variant;
  actionDialog.visible = true;
  return new Promise((resolve) => {
    actionDialogResolver = resolve;
  });
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
    loginNotice.value = '验证码加载失败，请点击刷新后重试';
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
    const shouldGoProfile = await openActionDialog({
      title: '还没有收货地址',
      message: '先到个人中心添加收货地址，再继续下单。',
      confirmText: '去添加',
      cancelText: '稍后',
      variant: 'warning'
    });
    if (shouldGoProfile) {
      go('profile');
    }
    return;
  }
  const targetId = selectedAddressId.value
    || addressList.value.find((addr) => addr.default)?.id
    || addressList.value[0]?.id;
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
    setNotice('success', '欢迎回来，' + res.username);
    loginNotice.value = '';
    closeLoginModal();
    if (auth.role === 'MERCHANT') {
      await loadMerchantProfile();
      if (isMerchantApproved.value) {
        await loadMyProducts();
      }
    }
    currentPage.value = normalizePage(auth.role === 'MERCHANT' ? 'profile' : currentPage.value);
    localStorage.setItem('mk_page', currentPage.value);
    await refreshPaymentSideData();
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
  resetMerchantProfile();
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
  adminUsers.value = [];
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
    showToast('已选择视频文件', 'info');
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
    setNotice('success', registerForm.role === 'MERCHANT' ? '注册成功，请先完善商家资料并提交审核' : '注册成功');
    registerNotice.value = '';
    closeRegisterModal();
    closeLoginModal();
    if (auth.role === 'MERCHANT') {
      await loadMerchantProfile();
    }
    currentPage.value = normalizePage(auth.role === 'MERCHANT' ? 'profile' : currentPage.value);
    localStorage.setItem('mk_page', currentPage.value);
    await refreshPaymentSideData();
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
    setNotice('success', registerForm.role === 'MERCHANT' ? '注册成功，请先完善商家资料并提交审核' : '注册成功');
    registerNotice.value = '';
    registerForm.emailCode = '';
    closeRegisterModal();
    closeLoginModal();
    if (auth.role === 'MERCHANT') {
      await loadMerchantProfile();
    }
    currentPage.value = normalizePage(auth.role === 'MERCHANT' ? 'profile' : currentPage.value);
    localStorage.setItem('mk_page', currentPage.value);
    await refreshPaymentSideData();
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
  page = normalizePage(page);
  currentPage.value = page;
  localStorage.setItem('mk_page', page);
  if (isAdmin.value) {
    if (page === 'adminMerchants' && !adminMerchants.value.length) {
      loadAdminMerchants(adminFilters.merchantStatus);
    }
    if (page === 'adminUsers' && !adminUsers.value.length) {
      loadAdminUsers();
    }
    if (page === 'adminOrders' && !adminOrders.value.length) {
      loadAdminOrders();
    }
    if (page === 'adminRevenue') {
      loadAdminRevenue();
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
  } else if (auth.role === 'MERCHANT' && page === 'merchantStock') {
    loadLowStock();
  } else if (page === 'chat') {
    loadRecentChats();
  } else if (page === 'orders') {
    loadMyOrders();
  } else if (page === 'walletUser') {
    if (hasUserWalletUi.value) {
      loadWallet();
    }
    loadPaymentLogs();
  } else if (page === 'checkout') {
    if (isLoggedIn.value) {
      loadAddresses();
    }
  } else if (page === 'profile') {
    if (auth.role === 'MERCHANT') {
      loadMerchantProfile();
    } else {
      loadAddresses();
    }
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

function changeMyOrderPage(delta) {
  updatePage(myOrderPage, myOrderTotalPages, delta);
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
  const ok = await openActionDialog({
    title: '删除商品',
    message: '删除后商品会从店铺列表中移除，确定继续吗？',
    confirmText: '删除',
    cancelText: '取消',
    variant: 'error'
  });
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
  currentChat.mode = 'product';
  currentChat.productId = product.id;
  currentChat.orderId = null;
  currentChat.targetId = product.owner.id;
  currentChat.title = product.name;
  currentChat.targetName = product.owner.username || '商家';
  currentChat.subtitle = `与 ${currentChat.targetName} 沟通`;
  currentChat.messages = [];
  currentChat.content = '';
  showChatPanel.value = true;
  await loadChatMessages();
}

async function openChatFromRecent(msg) {
  const other = msg.sender?.username === auth.username ? msg.receiver : msg.sender;
  currentChat.mode = 'product';
  currentChat.productId = msg.product?.id;
  currentChat.orderId = null;
  currentChat.title = msg.product?.name || '商品';
  currentChat.targetId = other?.id;
  currentChat.targetName = other?.username || '用户';
  currentChat.subtitle = `与 ${currentChat.targetName} 沟通`;
  currentChat.messages = [];
  currentChat.content = '';
  showChatPanel.value = true;
  await loadChatMessages();
}

async function openRefundChat(order) {
  if (!isLoggedIn.value) {
    setNotice('warning', '请先登录');
    return;
  }
  const orderId = order?.orderId || order?.id;
  if (!orderId) {
    setNotice('error', '订单信息缺失');
    return;
  }
  currentChat.mode = 'refund';
  currentChat.orderId = orderId;
  currentChat.productId = null;
  currentChat.targetId = null;
  currentChat.targetName = auth.role === 'MERCHANT'
    ? (order?.buyerName || '买家')
    : (order?.merchantName || '商家');
  currentChat.title = `售后单 ${order?.orderNumber || `#${orderId}`}`;
  currentChat.subtitle = `与 ${currentChat.targetName} 沟通退款事宜`;
  currentChat.messages = [];
  currentChat.content = '';
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
  try {
    if (currentChat.mode === 'refund') {
      if (!currentChat.orderId) return;
      currentChat.messages = await fetchRefundChat(currentChat.orderId);
      return;
    }
    if (!currentChat.productId || !currentChat.targetId) return;
    currentChat.messages = await fetchChat(currentChat.productId, currentChat.targetId);
  } catch (err) {
    // ignore
  }
}

async function sendChatMessage() {
  if (!currentChat.content.trim()) return;
  try {
    if (currentChat.mode === 'refund') {
      await sendRefundChat(currentChat.orderId, currentChat.content.trim());
    } else {
      await sendChat({
        productId: currentChat.productId,
        targetId: currentChat.targetId,
        content: currentChat.content
      });
    }
    currentChat.content = '';
    await loadChatMessages();
    if (currentChat.mode === 'refund') {
      await loadMyOrders();
    }
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

function openRefundModal(order) {
  if (!order) return;
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

function openReviewModal(order) {
  if (!order || !canReviewMerchant(order)) return;
  reviewModal.orderId = order.orderId || order.id;
  reviewModal.orderNumber = order.orderNumber || '';
  reviewModal.merchantName = order.merchantName || '';
  reviewModal.rating = 5;
  reviewModal.content = '';
  reviewError.value = '';
  showReviewModal.value = true;
}

function closeReviewModal() {
  reviewModal.orderId = null;
  reviewModal.orderNumber = '';
  reviewModal.merchantName = '';
  reviewModal.rating = 5;
  reviewModal.content = '';
  reviewError.value = '';
  showReviewModal.value = false;
}

function fillAddressFromGeo(target = 'order') {
  if (!navigator.geolocation) {
    openActionDialog({
      title: '当前浏览器不支持定位',
      message: '请手动填写地址，或使用地图选点功能。',
      confirmText: '知道了',
      showCancel: false,
      variant: 'warning'
    });
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
      openActionDialog({
        title: '定位失败',
        message: '请检查浏览器定位权限，或改用地图选点 / 手动填写地址。',
        confirmText: '知道了',
        showCancel: false,
        variant: 'warning'
      });
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
  mapSelectedAddress.value = '正在解析地址...';
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
    openActionDialog({
      title: '还没有选点',
      message: '请先在地图上点击一个位置，再填入地址。',
      confirmText: '知道了',
      showCancel: false,
      variant: 'warning'
    });
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

async function confirmReceipt(order) {
  const orderId = order?.orderId || order?.id;
  if (!orderId || receiptSubmitting.value) return;
  const ok = await openActionDialog({
    title: '确认收货',
    message: `确认订单 ${order.orderNumber} 已收货吗？确认后平台会将托管金额结算给商家，并扣除 5% 佣金。`,
    confirmText: '确认收货',
    cancelText: '再等等',
    variant: 'warning'
  });
  if (!ok) {
    return;
  }

  receiptSubmitting.value = true;
  try {
    await confirmReceiptOrder(orderId);
    await Promise.all([loadMyOrders(), refreshPaymentSideData()]);
    const confirmedOrder = myOrders.value.find((item) => (item.orderId || item.id) === orderId);
    setNotice('success', '已确认收货，订单已结算，现在可以评价商家');
    if (confirmedOrder && canReviewMerchant(confirmedOrder)) {
      openReviewModal(confirmedOrder);
    }
  } catch (err) {
    const msg = err?.response?.data?.message || '确认收货失败';
    setNotice('error', msg);
  } finally {
    receiptSubmitting.value = false;
  }
}


async function submitRefund() {
  if (!refundModal.orderId) {
    await openActionDialog({
      title: '无法提交退款',
      message: '订单信息缺失，请刷新订单列表后重试。',
      confirmText: '知道了',
      showCancel: false,
      variant: 'error'
    });
    return;
  }
  if (refundSubmitting.value) return;
  const submittedOrderId = refundModal.orderId;
  refundSubmitting.value = true;
  try {
    setNotice('info', '正在提交退款申请...');
    await refundOrder(submittedOrderId, refundModal.reason ? { reason: refundModal.reason } : {});
    setNotice('success', '退款申请已提交，待商家审核');
    closeRefundModal();
    await loadMyOrders();
    const submittedOrder = myOrders.value.find((order) => (order.orderId || order.id) === submittedOrderId);
    await openActionDialog({
      title: '退款申请已提交',
      message: '商家审核通过后，系统会按当前订单状态执行退款。你也可以继续在售后沟通中和商家确认退款细节。',
      confirmText: '知道了',
      showCancel: false,
      variant: 'success'
    });
    if (submittedOrder) {
      await openRefundChat(submittedOrder);
    }
  } catch (err) {
    const msg = err?.response?.data?.message || '退款失败';
    setNotice('error', msg);
    refundError.value = msg;
  } finally {
    refundSubmitting.value = false;
  }
}

async function submitMerchantReview() {
  if (!reviewModal.orderId) {
    reviewError.value = '订单信息缺失，请刷新后重试';
    return;
  }
  if (reviewSubmitting.value) return;

  const content = String(reviewModal.content || '').trim();
  if (!reviewModal.rating || Number(reviewModal.rating) < 1 || Number(reviewModal.rating) > 5) {
    reviewError.value = '请选择 1 到 5 星评分';
    return;
  }
  if (!content) {
    reviewError.value = '请输入评价内容';
    return;
  }

  reviewSubmitting.value = true;
  reviewError.value = '';
  try {
    await submitMerchantOrderReview(reviewModal.orderId, {
      rating: Number(reviewModal.rating),
      content
    });
    closeReviewModal();
    setNotice('success', '商家评价已提交');
    await loadMyOrders();
  } catch (err) {
    const msg = err?.response?.data?.message || '评价提交失败';
    reviewError.value = msg;
    setNotice('error', msg);
  } finally {
    reviewSubmitting.value = false;
  }
}

</script>

<template>
  <div
    v-if="isMerchantBanned"
    class="blocked-page"
    data-variant="BANNED"
  >
    <div class="blocked-card">
      <div class="blocked-icon">X</div>
      <h2 class="blocked-title blocked-title--danger">您的商家账号已被封禁</h2>
      <p class="blocked-desc">请联系管理员了解详情。</p>
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
          <button :class="['nav-link', currentPage === 'adminUsers' && 'nav-link--active']" @click="go('adminUsers')">用户</button>
          <button
            :class="['nav-link', 'nav-link--with-badge', currentPage === 'adminMerchants' && 'nav-link--active']"
            @click="go('adminMerchants')"
          >
            商家审核
            <span v-if="hasPendingMerchantBadge" class="dot-badge"></span>
          </button>
          <button :class="['nav-link', currentPage === 'adminOrders' && 'nav-link--active']" @click="go('adminOrders')">订单</button>
          <button :class="['nav-link', currentPage === 'adminRevenue' && 'nav-link--active']" @click="go('adminRevenue')">收益</button>
        </template>
        <template v-else>
          <template v-if="auth.role === 'MERCHANT'">
            <button :class="['nav-link', currentPage === 'profile' && 'nav-link--active']" @click="go('profile')">商家资料</button>
            <template v-if="isMerchantApproved">
              <button :class="['nav-link', currentPage === 'merchantUpload' && 'nav-link--active']" @click="go('merchantUpload')">上架商品</button>
              <button :class="['nav-link', currentPage === 'myShop' && 'nav-link--active']" @click="go('myShop')">我的店铺</button>
              <button :class="['nav-link', currentPage === 'merchantStock' && 'nav-link--active']" @click="go('merchantStock')">库存告警</button>
              <button :class="['nav-link', currentPage === 'chat' && 'nav-link--active']" @click="go('chat')">聊天</button>
              <button :class="['nav-link', currentPage === 'walletUser' && 'nav-link--active']" @click="go('walletUser')">钱包</button>
              <button :class="['nav-link', currentPage === 'orders' && 'nav-link--active']" @click="go('orders')">订单</button>
            </template>
          </template>
          <template v-else-if="auth.role === 'USER'">
            <button :class="['nav-link', currentPage === 'catalog' && 'nav-link--active']" @click="go('catalog')">商品</button>
            <button :class="['nav-link', currentPage === 'profile' && 'nav-link--active']" @click="go('profile')">个人主页</button>
            <button :class="['nav-link', currentPage === 'chat' && 'nav-link--active']" @click="go('chat')">聊天</button>
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
        <span class="muted">{{ topAuthHint }}</span>
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
          <h3>待审核商家</h3>
          <p class="muted">这里只处理商家入驻审核，不包含封禁、批量管理等其他操作。</p>
        </div>
        <div class="filters">
          <button
            :class="['chip', adminFilters.merchantStatus === 'UNREVIEWED' && 'chip--active']"
            @click="merchantPage = 1; loadAdminMerchants('UNREVIEWED')"
          >
            待补资料
          </button>
          <button
            :class="['chip', adminFilters.merchantStatus === 'PENDING' && 'chip--active']"
            @click="merchantPage = 1; loadAdminMerchants('PENDING')"
          >
            待审核
          </button>
        </div>
      </div>
      <div v-if="adminLoading.merchants" class="loading">正在拉取商家...</div>
      <div v-else class="admin-table">
        <div class="admin-table__head">
          <span>商家资料</span>
          <span>审核状态</span>
          <span>操作</span>
        </div>
        <div v-if="!filteredMerchants.length" class="empty">当前没有待处理的商家审核</div>
        <div v-else v-for="merchant in pagedMerchants" :key="merchant.id" class="admin-table__row">
          <div>
            <strong>{{ merchant.storeName || merchant.username }}</strong>
            <p class="muted">账号：{{ merchant.username }} · ID {{ merchant.id }}</p>
            <p class="muted">邮箱：{{ merchant.email || '未填写' }}</p>
            <p class="muted">联系人：{{ merchant.contactName || '未填写' }} / {{ merchant.contactPhone || '未填写' }}</p>
            <p class="muted">经营地址：{{ merchant.businessAddress || '未填写' }}</p>
            <p class="muted">证照编号：{{ merchant.licenseNumber || '未填写' }}</p>
            <p v-if="merchant.description" class="muted">说明：{{ merchant.description }}</p>
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
            <button class="ghost" :disabled="adminLoading.updating" @click="changeMerchantStatus(merchant.id, 'UNREVIEWED')">
              退回补充
            </button>
          </div>
        </div>
      </div>
        <div v-if="filteredMerchants.length" class="pager">
          <button class="ghost" type="button" :disabled="merchantPage === 1" @click="changeMerchantPage(-1)">上一页</button>
          <span class="muted">第 {{ merchantPage }} / {{ merchantTotalPages }} 页</span>
          <button class="ghost" type="button" :disabled="merchantPage === merchantTotalPages" @click="changeMerchantPage(1)">下一页</button>
        </div>
    </section>

    <section v-if="isAdmin && currentPage === 'adminUsers'" class="admin-console">
      <div class="page-header-simple">
        <h1>用户管理</h1>
      </div>
      <div class="admin-panel">
        <div class="panel__header">
          <div>
            <h3>用户列表</h3>
            <p class="muted">查看账号信息，并执行改密、封禁、删除等操作。</p>
          </div>
          <button class="ghost" type="button" @click="loadAdminUsers">刷新</button>
        </div>
        <div class="filters filters--wrap">
          <input class="search" v-model="adminUserSearch" type="search" placeholder="搜索用户名 / 邮箱 / 角色 / 店铺" />
          <div class="row-inline" style="gap:8px; flex-wrap:wrap;">
            <button :class="['chip', adminUserStatusFilter === 'ALL' && 'chip--active']" @click="adminUserStatusFilter = 'ALL'">全部</button>
            <button :class="['chip', adminUserStatusFilter === 'ACTIVE' && 'chip--active']" @click="adminUserStatusFilter = 'ACTIVE'">正常</button>
            <button :class="['chip', adminUserStatusFilter === 'BANNED' && 'chip--active']" @click="adminUserStatusFilter = 'BANNED'">已封禁</button>
            <button :class="['chip', adminUserStatusFilter === 'DELETED' && 'chip--active']" @click="adminUserStatusFilter = 'DELETED'">已删除</button>
          </div>
        </div>
        <div v-if="adminUsersLoading" class="loading">正在加载用户...</div>
        <div v-else-if="!filteredAdminUsers.length" class="empty">暂无符合条件的用户</div>
        <div v-else class="admin-table admin-table--users">
          <div class="admin-table__head" style="grid-template-columns: 1.2fr 0.9fr 1.1fr 1.2fr 0.8fr 1.5fr;">
            <span>用户</span>
            <span>账号状态</span>
            <span>角色信息</span>
            <span>商家信息</span>
            <span>余额</span>
            <span>操作</span>
          </div>
          <div
            v-for="user in filteredAdminUsers"
            :key="user.id"
            class="admin-table__row"
            style="grid-template-columns: 1.2fr 0.9fr 1.1fr 1.2fr 0.8fr 1.5fr;"
          >
            <div>
              <strong>{{ user.username }}</strong>
              <p class="muted">ID {{ user.id }}</p>
              <p class="muted">{{ user.email || '未填写邮箱' }}</p>
            </div>
            <div>
              <span class="status-badge" :data-variant="normalizeAccountStatus(user.accountStatus)">
                {{ renderAccountStatusLabel(user.accountStatus) }}
              </span>
            </div>
            <div>
              <p><strong>{{ user.role }}</strong></p>
              <p v-if="user.role === 'MERCHANT'" class="muted">商家审核：{{ renderStatusLabel(user.merchantStatus) }}</p>
              <p v-else class="muted">普通账号</p>
            </div>
            <div>
              <p><strong>{{ user.merchantStoreName || '-' }}</strong></p>
              <p class="muted">{{ user.role === 'MERCHANT' ? '商家资料账号' : '非商家账号' }}</p>
            </div>
            <div>￥{{ Number(user.walletBalance || 0).toFixed(2) }}</div>
            <div class="admin-table__actions">
              <button class="ghost" type="button" :disabled="normalizeAccountStatus(user.accountStatus) === 'DELETED'" @click="openPwdModal(user)">改密码</button>
              <button class="ghost" type="button" :disabled="!canManageAdminUser(user)" @click="toggleAdminUserBan(user)">
                {{ normalizeAccountStatus(user.accountStatus) === 'BANNED' ? '解封' : '封禁' }}
              </button>
              <button class="ghost danger" type="button" :disabled="!canManageAdminUser(user)" @click="removeAdminUser(user)">删除</button>
            </div>
          </div>
        </div>
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
          <input class="search" v-model="orderSearch" type="search" placeholder="搜索订单号 / 客户" />
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
        </div>
      </div>
        <div v-if="filteredOrders.length" class="pager">
          <button class="ghost" type="button" :disabled="orderPage === 1" @click="changeOrderPage(-1)">上一页</button>
          <span class="muted">第 {{ orderPage }} / {{ orderTotalPages }} 页</span>
          <button class="ghost" type="button" :disabled="orderPage === orderTotalPages" @click="changeOrderPage(1)">下一页</button>
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
              <small class="muted">支持图片文件，建议 2MB 以内</small>
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

    <section v-if="isAdmin && currentPage === 'adminRevenue'" class="admin-console">
      <div class="page-header-simple">
        <h1>收益</h1>
      </div>
        <div class="admin-panel">
          <div class="panel__header">
            <div>
              <h3>平台资金流水</h3>
              <p class="muted">当前平台余额 ￥{{ Number(walletBalance || 0).toFixed(2) }}</p>
            </div>
            <button class="ghost" type="button" @click="loadAdminRevenue">刷新</button>
          </div>
        <div v-if="!adminRevenueLogs.length" class="empty">暂无收益记录</div>
        <div v-else class="admin-table admin-table--orders">
          <div class="admin-table__head" style="grid-template-columns: 1fr 0.8fr 0.9fr 1.4fr 0.9fr;">
            <span>时间</span>
            <span>金额</span>
            <span>类型</span>
            <span>备注/关联</span>
            <span>当时余额</span>
          </div>
          <div
            v-for="log in adminRevenueLogs"
            :key="log.id"
            class="admin-table__row"
            style="grid-template-columns: 1fr 0.8fr 0.9fr 1.4fr 0.9fr;"
          >
            <span>{{ formatDateTime(log.createdAt) }}</span>
            <span>￥{{ Number(log.amount || 0).toFixed(2) }}</span>
            <span>{{ formatPaymentType(log.type) }}</span>
            <span>{{ log.remark || '-' }} {{ log.orderNumber ? '（订单号：' + log.orderNumber + '）' : '' }}</span>
            <span>￥{{ Number(log.balanceAfter || 0).toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'merchantStock'" class="content content--full">
      <div class="content__main">
        <div class="page-header-simple" style="align-items:flex-start;">
          <div>
            <p class="eyebrow">库存告警</p>
            <h2>我的低库存商品</h2>
            <p class="muted">这里只显示当前商家名下库存低于阈值的商品，方便及时补货。</p>
          </div>
          <div class="row-inline" style="gap:10px; flex-wrap:wrap;">
            <label class="muted" style="display:flex; align-items:center; gap:8px;">
              阈值
              <input v-model.number="lowStockThreshold" type="number" min="0" style="width:88px;" />
            </label>
            <button class="ghost" type="button" @click="loadLowStock" :disabled="lowStockLoading">刷新</button>
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
          <div v-if="!lowStock.length" class="empty">当前没有低库存商品</div>
          <div v-else v-for="item in pagedLowStock" :key="item.id" class="admin-table__row">
            <div>
              <strong>{{ item.name }}</strong>
              <p class="muted">ID {{ item.id }}</p>
            </div>
            <div>
              <span class="status-badge" data-variant="BANNED">库存 {{ item.stock }}</span>
            </div>
            <div>{{ item.category?.name || '未分类' }}</div>
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
      </div>
    </section>

    <section v-else-if="currentPage === 'catalog'" id="catalog" class="content">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">新品列表</p>
            <h2>MK 男士服装</h2>
          </div>
          <div class="catalog-toolbar-actions">
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
            <label class="catalog-store-filter">
              <span class="muted">店铺筛选</span>
              <select :value="selectedStoreId" @change="selectStore($event.target.value)">
                <option value="all">全部店铺</option>
                <option v-for="store in catalogStoreOptions" :key="store.id" :value="store.id">
                  {{ store.name }} · {{ renderAverageRating(store.rating) }} 分 · {{ store.productCount }} 件商品
                </option>
              </select>
            </label>
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
              <div class="price">￥{{ Number(product.price).toFixed(2) }}</div>
              </div>
              <div class="catalog-store-line">
                <strong>{{ resolveStoreName(product) }}</strong>
                <span class="muted">店铺评分 {{ renderAverageRating(product.merchantRatingAverage) }} / 5</span>
              </div>
              <div class="catalog-metrics">
                <span class="tag tag--soft">销量 {{ Number(product.salesCount || 0) }}</span>
                <span class="tag tag--soft">店铺评价 {{ Number(product.merchantRatingCount || 0) }}</span>
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
                  {{ sz.label }} / 库存{{ sz.stock }}
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
        <div v-if="!loadingProducts && !filteredCatalogProducts.length" class="empty">当前筛选条件下暂无商品</div>
        <div v-if="filteredCatalogProducts.length" class="pager">
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
          <div class="cart-merchant-overview">
            <div class="cart-merchant-overview__metric">
              <strong>{{ cartCount }}</strong>
              <span>件商品</span>
            </div>
            <div class="cart-merchant-overview__metric">
              <strong>{{ cartMerchantCount }}</strong>
              <span>家商家</span>
            </div>
            <div class="cart-merchant-overview__metric">
              <strong>￥{{ cartTotal }}</strong>
              <span>待结算</span>
            </div>
          </div>
          <div class="merchant-group-list">
            <div v-for="group in cartMerchantGroups" :key="group.ownerId" class="merchant-group-card">
              <div class="row-inline" style="justify-content:space-between;">
                <strong>{{ group.ownerName }}</strong>
                <span class="tag">{{ group.count }} 件</span>
              </div>
              <p class="muted">{{ group.previewText }}</p>
              <strong>￥{{ group.subtotalText }}</strong>
            </div>
          </div>
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
          <p class="muted">审核通过后即可上架商品，成交后平台按订单金额自动收取 5% 佣金。</p>
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
                <div class="price">￥{{ Number(product.price).toFixed(2) }}</div>
              </div>
              <p class="muted">{{ product.description }}</p>
              <div v-if="product.sizesDetail?.length" class="size-chips">
                <span v-for="sz in product.sizesDetail" :key="sz.label" class="tag">
                  {{ sz.label }} / {{ sz.stock }}
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
                {{ sz.label }} / {{ sz.stock }}
              </span>
            </div>
            <p class="muted" v-else>库存：{{ editingProductOriginal.stock }}</p>
            <p class="muted">价格：￥{{ Number(editingProductOriginal.price || 0).toFixed(2) }}</p>
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
              <small class="muted">支持图片文件，建议 2MB 以内</small>
            </label>
            <label>
              商品视频（可选）
              <div class="upload-row">
                <input type="file" accept="video/*" @change="onVideoChange" />
                <span v-if="productForm.videoUrl" class="muted">已上传或已设置</span>
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
              <p class="muted">尺码：{{ item.sizeLabel || '默认' }} / 库存{{ item.stock }}</p>
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
          <div class="checkout-insight">
            <div class="checkout-insight__header">
              <div>
                <p class="eyebrow">下单预览</p>
                <h4>{{ cartMerchantCount }} 家商家 · {{ cartCount }} 件商品</h4>
              </div>
              <span class="tag">{{ cartMerchantCount > 1 ? '系统自动拆单' : '单商家订单' }}</span>
            </div>
            <p class="muted">
              {{ cartMerchantCount > 1 ? `系统会按商家拆成 ${cartMerchantCount} 笔订单，但仍可一次完成支付。` : '本次下单将生成 1 笔订单，支付后进入平台托管。' }}
            </p>
            <div class="merchant-group-list merchant-group-list--compact">
              <div v-for="group in cartMerchantGroups" :key="group.ownerId" class="merchant-group-card merchant-group-card--compact">
                <div class="row-inline" style="justify-content:space-between;">
                  <strong>{{ group.ownerName }}</strong>
                  <span class="muted">{{ group.count }} 件</span>
                </div>
                <p class="muted">{{ group.previewText }}</p>
                <strong>￥{{ group.subtotalText }}</strong>
              </div>
            </div>
            <div v-if="checkoutPreviewAddress" class="checkout-address-preview">
              <span class="tag">常用地址</span>
              <div>
                <strong>{{ checkoutPreviewAddress.recipientName }} {{ checkoutPreviewAddress.phone }}</strong>
                <p class="muted">{{ checkoutPreviewAddress.address }}</p>
              </div>
            </div>
            <p v-else class="muted">提交后将在弹窗里选择收货地址与支付方式。</p>
          </div>
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
        <template v-if="auth.role === 'MERCHANT'">
          <div class="page-header-simple" style="align-items:flex-start;">
            <div>
              <p class="eyebrow">商家资料</p>
              <h2>{{ isMerchantApproved ? '店铺资料' : '完善入驻资料' }}</h2>
              <p class="muted">
                {{ isMerchantApproved ? '审核已通过，可继续维护店铺资料。' : '审核通过前仅可填写商家基本信息并提交管理员审核。' }}
              </p>
            </div>
            <div class="status-pill" :data-variant="auth.merchantStatus || 'NONE'">
              审核状态：{{ merchantStatusLabel }}
            </div>
          </div>
          <div class="profile-grid">
            <div class="profile-card">
              <div class="panel__header" style="margin-bottom:12px;">
                <div>
                  <h3>基本信息</h3>
                  <small class="muted">管理员会根据这些资料完成商家审核</small>
                </div>
                <button class="ghost" type="button" :disabled="merchantProfileLoading" @click="loadMerchantProfile">
                  {{ merchantProfileLoading ? '刷新中...' : '刷新资料' }}
                </button>
              </div>
              <form class="auth-form" @submit.prevent="submitMerchantProfile(false)">
                <label>
                  店铺名称
                  <input v-model="merchantProfile.storeName" type="text" maxlength="120" placeholder="请输入店铺名称" />
                </label>
                <label>
                  联系人
                  <input v-model="merchantProfile.contactName" type="text" maxlength="80" placeholder="请输入联系人姓名" />
                </label>
                <label>
                  联系电话
                  <input v-model="merchantProfile.contactPhone" type="text" maxlength="30" placeholder="请输入联系电话" />
                </label>
                <label>
                  经营地址
                  <input v-model="merchantProfile.businessAddress" type="text" maxlength="255" placeholder="请输入经营地址" />
                </label>
                <label>
                  证照编号
                  <input v-model="merchantProfile.licenseNumber" type="text" maxlength="80" placeholder="请输入营业执照或统一社会信用代码" />
                </label>
                <label>
                  店铺说明
                  <textarea v-model="merchantProfile.description" maxlength="500" placeholder="补充主营品类、经营说明等（可选）"></textarea>
                </label>
                <div class="auth-status">
                  <p class="muted">资料要求</p>
                  <p v-if="auth.merchantStatus === 'UNREVIEWED'" class="muted">请先填写完整资料，再点击“提交审核”。</p>
                  <p v-else-if="auth.merchantStatus === 'PENDING'" class="muted">资料已提交，管理员审核前你仍可修改并重新提交。</p>
                  <p v-else class="muted">商家已审核通过，保存后将直接更新店铺资料。</p>
                </div>
                <div class="row-inline" style="justify-content:flex-end; gap:10px;">
                  <button class="ghost" type="submit" :disabled="merchantProfileSubmitting || merchantProfileLoading">
                    {{ merchantProfileSubmitting ? '提交中...' : '保存资料' }}
                  </button>
                  <button
                    v-if="!isMerchantApproved"
                    class="primary"
                    type="button"
                    :disabled="merchantProfileSubmitting || merchantProfileLoading"
                    @click="submitMerchantProfile(true)"
                  >
                    {{ merchantProfileSubmitting ? '提交中...' : auth.merchantStatus === 'PENDING' ? '重新提交审核' : '提交审核' }}
                  </button>
                </div>
              </form>
            </div>
            <div v-if="isMerchantApproved" class="profile-card">
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
                  <input v-model="passwordForm.confirm" type="password" placeholder="请再一次输入新密码" />
                </label>
                <div class="row-inline" style="justify-content:flex-end; gap:10px;">
                  <button class="primary" type="button" :disabled="passwordSubmitting" @click="changePassword">
                    {{ passwordSubmitting ? '提交中...' : '保存密码' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="page-header-simple" style="align-items:flex-start;">
            <div>
              <p class="eyebrow">个人主页</p>
              <h2>账户与地址</h2>
              <p class="muted">修改密码 / 管理常用收货地址</p>
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
                  <input v-model="passwordForm.confirm" type="password" placeholder="请再一次输入新密码" />
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
        </template>
      </div>
    </section>

    <section v-else-if="currentPage === 'walletUser' && auth.role !== 'USER'" class="content content--full">
      <div class="content__main">
        <div class="toolbar">
          <div>
            <p class="eyebrow">钱包</p>
            <h2>余额 ￥{{ Number(walletBalance).toFixed(2) }}</h2>
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
          <div v-else-if="!visiblePaymentLogs.length" class="empty">暂无记录</div>
          <div v-else class="admin-table admin-table--orders">
            <div class="admin-table__head" style="grid-template-columns: 1fr 1fr 1fr 1.2fr;">
              <span>时间</span>
              <span>类型</span>
              <span>金额</span>
              <span>关联</span>
            </div>
            <div
              v-for="p in visiblePaymentLogs"
              :key="p.id"
              class="admin-table__row"
              style="grid-template-columns: 1fr 1fr 1fr 1.2fr;"
            >
              <span>{{ formatDateTime(p.createdAt) }}</span>
              <span>{{ formatPaymentType(p.type) }}</span>
              <span>￥{{ Number(p.amount || 0).toFixed(2) }}</span>
              <span>{{ p.orderNumber || p.remark || '-' }}</span>
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
            <h2>{{ isBuyerOrderView ? '我的订单' : '店铺订单' }}</h2>
          </div>
          <button class="ghost" type="button" @click="loadMyOrders">刷新</button>
        </div>
        <div v-if="notice.message" class="notice" :data-variant="notice.type">
          {{ notice.message }}
        </div>
        <div v-if="myOrdersLoading" class="loading">加载中...</div>
        <div v-else-if="!myOrders.length" class="empty">
          <p>暂无订单</p>
          <small class="muted">{{ isBuyerOrderView ? '浏览商品并完成下单后，订单会出现在这里。' : '店铺收到订单后，会在这里集中查看和处理。' }}</small>
        </div>
        <div v-else class="orders-shell">
          <div class="orders-summary-grid">
            <div class="orders-summary-card" data-variant="accent">
              <small>订单总览</small>
              <h3>{{ myOrderSummary.totalCount }}</h3>
              <p class="muted">累计金额 ￥{{ myOrderSummary.totalAmount }}</p>
            </div>
            <div class="orders-summary-card">
              <small>{{ myOrderSummary.pendingActionLabel }}</small>
              <h3>{{ myOrderSummary.pendingActionCount }}</h3>
              <p class="muted">{{ isBuyerOrderView ? '包含待支付、待确认收货' : '当前待处理的退款申请数量' }}</p>
            </div>
            <div class="orders-summary-card">
              <small>托管中金额</small>
              <h3>￥{{ myOrderSummary.escrowAmount }}</h3>
              <p class="muted">平台暂存，确认收货后才会结算</p>
            </div>
            <div class="orders-summary-card">
              <small>退款相关</small>
              <h3>{{ myOrderSummary.refundCount }}</h3>
              <p class="muted">含退款申请、已退款、已拒绝订单</p>
            </div>
          </div>

          <div class="orders-toolbar">
            <input
              class="search order-search"
              v-model="myOrderSearch"
              type="search"
              :placeholder="isBuyerOrderView ? '搜索订单号 / 商品 / 退款原因' : '搜索订单号 / 商品 / 买家退款原因'"
            />
            <div class="orders-filter-chips">
              <button
                v-for="tab in myOrderStatusTabs"
                :key="tab.key"
                :class="['chip', myOrderStatusFilter === tab.key && 'chip--active']"
                type="button"
                @click="myOrderStatusFilter = tab.key"
              >
                {{ tab.label }} · {{ tab.count }}
              </button>
            </div>
          </div>

          <div v-if="!filteredMyOrders.length" class="empty">
            <p>没有匹配的订单</p>
            <small class="muted">可以切换筛选条件，或清空搜索词后重试。</small>
          </div>

          <div v-else class="order-card-list">
            <article v-for="o in pagedMyOrders" :key="o.orderId || o.id" class="order-card">
              <div class="order-card__top">
                <div>
                  <p class="eyebrow">订单号</p>
                  <h3 class="order-card__number">{{ o.orderNumber }}</h3>
                  <p class="muted">{{ formatDateTime(o.createdAt) }}</p>
                </div>
                <div class="order-card__top-meta">
                  <span class="status-badge" :data-variant="getOrderStatusVariant(o.status)">
                    {{ formatOrderStatusWithPayMethod(o.status, o.payMethod) }}
                  </span>
                  <strong class="order-card__amount">￥{{ Number(o.totalAmount || 0).toFixed(2) }}</strong>
                </div>
              </div>

              <div class="order-progress">
                <div class="order-progress__track">
                  <span
                    class="order-progress__fill"
                    :data-variant="getOrderStatusVariant(o.status)"
                    :style="{ width: `${getOrderProgressMeta(o.status).percent}%` }"
                  ></span>
                </div>
                <div class="order-progress__meta">
                  <strong>{{ getOrderProgressMeta(o.status).label }}</strong>
                  <span class="muted">{{ getOrderProgressMeta(o.status).desc }}</span>
                </div>
              </div>

              <div class="order-card__body">
                <div class="order-card__section">
                  <small class="muted">商品清单</small>
                  <div v-if="o.items?.length" class="order-item-tags">
                    <span v-for="(item, idx) in o.items.slice(0, 4)" :key="`${o.orderId || o.id}-${idx}`" class="tag tag--soft">
                      {{ item.productName || '商品' }} ×{{ Number(item.quantity || 0) }}<template v-if="item.sizeLabel"> / {{ item.sizeLabel }}</template>
                    </span>
                    <span v-if="o.items.length > 4" class="tag tag--soft">+{{ o.items.length - 4 }} 件</span>
                  </div>
                  <p v-else class="muted">暂无商品明细</p>
                  <p class="muted">共 {{ countOrderItems(o) }} 件商品</p>
                </div>

                <div class="order-card__section">
                  <small class="muted">支付与流程</small>
                  <strong>{{ formatPayMethodShort(o.payMethod) }}</strong>
                  <p class="muted">
                    {{
                      isBuyerOrderView
                        ? (canConfirmReceipt(o.status)
                          ? '订单已支付，平台正在托管资金。你可以确认收货，也可以直接发起售后退款。'
                          : canReviewMerchant(o)
                            ? '订单已完成，现在可以给商家打分并填写评价；如需售后仍可继续发起退款。'
                            : hasMerchantReview(o)
                              ? '订单已完成，你提交的商家评价已保存；如需售后仍可继续发起退款。'
                          : canRefund(o.status)
                            ? '如需售后，可直接发起退款申请并等待商家审核。'
                            : getOrderProgressMeta(o.status).desc)
                        : (canReviewRefund(o.status)
                          ? '当前有退款申请待你审核，系统会在通过后自动退款。'
                          : '商家端可在这里持续跟进订单处理和售后状态。')
                    }}
                  </p>
                </div>

                <div
                  v-if="o.refundReason"
                  class="order-card__section order-card__section--notice"
                  :data-variant="normalizeOrderStatus(o.status) === 'REFUND_REQUESTED' ? 'warning' : 'neutral'"
                >
                  <small class="muted">{{ isBuyerOrderView ? '退款说明' : '买家退款原因' }}</small>
                  <p>{{ o.refundReason }}</p>
                </div>

                <div v-if="hasMerchantReview(o)" class="order-card__section order-card__section--review">
                  <div class="order-review__header">
                    <small class="muted">{{ isBuyerOrderView ? '我的商家评价' : `${o.buyerName || '买家'} 的评价` }}</small>
                    <strong class="order-review__stars">{{ renderMerchantRating(o.merchantRating) }}</strong>
                  </div>
                  <p>{{ o.merchantReview || '已提交评分' }}</p>
                  <span v-if="o.merchantReviewedAt" class="muted">{{ formatDateTime(o.merchantReviewedAt) }}</span>
                </div>
              </div>

              <div class="admin-table__actions order-card__actions">
                <button
                  v-if="canPayPending(o)"
                  class="ghost"
                  type="button"
                  :disabled="repayOrderId === (o.orderId || o.id)"
                  @click="payPendingOrder(o)"
                >
                  {{ repayOrderId === (o.orderId || o.id) ? '跳转中...' : '继续支付' }}
                </button>
                <button
                  v-if="isBuyerOrderView && canConfirmReceipt(o.status)"
                  class="primary"
                  type="button"
                  :disabled="receiptSubmitting"
                  @click="confirmReceipt(o)"
                >
                  {{ receiptSubmitting ? '处理中...' : '确认收货' }}
                </button>
                <button
                  v-if="isBuyerOrderView && canReviewMerchant(o)"
                  class="primary"
                  type="button"
                  :disabled="reviewSubmitting && reviewModal.orderId === (o.orderId || o.id)"
                  @click="openReviewModal(o)"
                >
                  {{ reviewSubmitting && reviewModal.orderId === (o.orderId || o.id) ? '提交中...' : '评价商家' }}
                </button>
                <button
                  v-if="isBuyerOrderView"
                  class="ghost danger"
                  type="button"
                  :disabled="!canRefund(o.status)"
                  :style="!canRefund(o.status) ? 'pointer-events:none;opacity:0.5;' : ''"
                  @click="openRefundModal(o)"
                >
                  申请退款
                </button>
                <button
                  v-if="canOpenRefundChat(o)"
                  class="ghost"
                  type="button"
                  @click="openRefundChat(o)"
                >
                  {{ getRefundChatLabel(o) }}
                </button>
                <button
                  v-if="!isBuyerOrderView && canReviewRefund(o.status)"
                  class="primary"
                  type="button"
                  :disabled="refundReviewSubmitting"
                  @click="reviewRefundByMerchant(o, true)"
                >
                  {{ refundReviewSubmitting ? '处理中...' : '同意退款' }}
                </button>
                <button
                  v-if="!isBuyerOrderView && canReviewRefund(o.status)"
                  class="ghost danger"
                  type="button"
                  :disabled="refundReviewSubmitting"
                  @click="reviewRefundByMerchant(o, false)"
                >
                  驳回退款
                </button>
              </div>
            </article>
          </div>

          <div v-if="filteredMyOrders.length" class="pager">
            <button class="ghost" type="button" :disabled="myOrderPage === 1" @click="changeMyOrderPage(-1)">上一页</button>
            <span class="muted">第 {{ myOrderPage }} / {{ myOrderTotalPages }} 页</span>
            <button class="ghost" type="button" :disabled="myOrderPage === myOrderTotalPages" @click="changeMyOrderPage(1)">下一页</button>
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
          <h3>充值余额</h3>
          <button class="ghost" @click="showRechargeModal = false">×</button>
        </div>
        <div class="auth-form">
          <label>
            金额（元）            <input v-model="rechargeAmount" type="number" min="0.01" step="0.01" />
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
        <div class="pay-layout">
          <div class="auth-form pay-layout__main">
            <label>
              选择地址
              <div class="address-list">
                <label
                  v-for="addr in addressList"
                  :key="addr.id"
                  :class="['address-card', 'address-card--selectable', payModalAddressId === addr.id && 'address-card--active']"
                >
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
              <div class="pay-method-grid">
                <label class="pay-method-card pay-method-card--active">
                  <input type="radio" value="ALIPAY" :checked="true" disabled />
                  <div>
                    <strong>支付宝沙箱支付</strong>
                    <p class="muted">当前站点下单仅保留支付宝沙箱支付，支持一次拉起合并支付</p>
                  </div>
                </label>
              </div>
              <p class="muted pay-method-desc">{{ selectedPayMethodMeta.desc }}</p>
            </label>
          </div>
          <aside class="pay-summary-card">
            <p class="eyebrow">支付预览</p>
            <h4>{{ cartMerchantCount }} 家商家 · {{ cartCount }} 件商品</h4>
            <div class="pay-summary-card__metrics">
              <div>
                <span class="muted">支付方式</span>
                <strong>{{ selectedPayMethodMeta.label }}</strong>
              </div>
              <div>
                <span class="muted">实付金额</span>
                <strong>￥{{ cartTotal }}</strong>
              </div>
            </div>
            <div class="merchant-group-list merchant-group-list--compact">
              <div v-for="group in cartMerchantGroups" :key="group.ownerId" class="merchant-group-card merchant-group-card--compact">
                <div class="row-inline" style="justify-content:space-between;">
                  <strong>{{ group.ownerName }}</strong>
                  <span class="muted">{{ group.count }} 件</span>
                </div>
                <p class="muted">{{ group.previewText }}</p>
                <strong>￥{{ group.subtotalText }}</strong>
              </div>
            </div>
            <div v-if="selectedPayAddress" class="checkout-address-preview checkout-address-preview--tight">
              <span class="tag">送达地址</span>
              <div>
                <strong>{{ selectedPayAddress.recipientName }} {{ selectedPayAddress.phone }}</strong>
                <p class="muted">{{ selectedPayAddress.address }}</p>
              </div>
            </div>
            <div class="summary">
              <span>合计</span>
              <strong>￥{{ cartTotal }}</strong>
            </div>
          </aside>
          <div class="row-inline pay-footer-actions" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="showPayModal = false">取消</button>
            <button class="primary" type="button" :disabled="submitting || !payModalAddressId" @click="checkout">
              {{ submitting ? '提交中...' : '确认支付' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="actionDialog.visible" class="modal-backdrop" @click.self="closeActionDialog(false)">
      <div class="modal action-dialog" :data-variant="actionDialog.variant">
        <div class="modal__header modal__header--stack">
          <p class="eyebrow">系统提示</p>
          <h3>{{ actionDialog.title }}</h3>
        </div>
        <p class="action-dialog__message">{{ actionDialog.message }}</p>
        <div class="row-inline action-dialog__actions">
          <button v-if="actionDialog.showCancel" class="ghost" type="button" @click="closeActionDialog(false)">
            {{ actionDialog.cancelText }}
          </button>
          <button class="primary" type="button" @click="closeActionDialog(true)">
            {{ actionDialog.confirmText }}
          </button>
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
              收货人              <input v-model="addressForm.recipientName" type="text" placeholder="姓名" />
            </label>
            <label>
              手机号              <input v-model="addressForm.phone" type="text" placeholder="手机号" />
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
            退款理由            <textarea v-model="refundModal.reason" placeholder="请输入退款原因"></textarea>
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

    <div v-if="showReviewModal" class="modal-backdrop" @click.self="closeReviewModal">
      <div class="modal">
        <div class="modal__header">
          <h3>评价商家</h3>
          <button class="ghost" @click="closeReviewModal">×</button>
        </div>
        <div class="auth-form">
          <div class="auth-block">
            <small class="muted">订单 {{ reviewModal.orderNumber || '-' }}</small>
            <strong>{{ reviewModal.merchantName || '当前商家' }}</strong>
          </div>
          <label>
            评分
            <div class="review-rating-input">
              <button
                v-for="score in 5"
                :key="score"
                class="ghost"
                type="button"
                :class="{ 'review-rating-input__item--active': score === reviewModal.rating }"
                @click="reviewModal.rating = score"
              >
                <span>{{ score <= reviewModal.rating ? '★' : '☆' }}</span>
                <small>{{ score }} 星</small>
              </button>
            </div>
          </label>
          <label>
            评价内容
            <textarea
              v-model="reviewModal.content"
              maxlength="160"
              placeholder="说说商家的发货、服务和商品情况"
            ></textarea>
          </label>
          <div class="review-char-count">{{ reviewModal.content.trim().length }}/160</div>
          <div v-if="reviewError" class="inline-notice" data-variant="error">{{ reviewError }}</div>
          <div class="row-inline" style="justify-content:flex-end; gap:10px;">
            <button class="ghost" type="button" @click="closeReviewModal">取消</button>
            <button class="primary" type="button" :disabled="reviewSubmitting" @click="submitMerchantReview">
              {{ reviewSubmitting ? '提交中...' : '提交评价' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showLoginModal" class="modal-backdrop" @click.self="closeLoginModal">
      <div class="modal">
        <div class="modal__header">
          <h3>登录账号</h3>
          <button class="ghost" @click="closeLoginModal">×</button>
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
            图片验证码            <div class="captcha-row">
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
            没有账号？            <button class="ghost" type="button" @click="openRegisterModal">前往注册</button>
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
          <strong>{{ currentChat.title }}</strong>
          <p class="muted">{{ currentChat.subtitle || `与 ${currentChat.targetName} 沟通` }}</p>
        </div>
        <button class="ghost" @click="showChatPanel = false">×</button>
      </div>
      <div class="chat-panel__body">
        <div v-if="!currentChat.messages.length" class="empty">
          {{ currentChat.mode === 'refund' ? '暂无售后消息，先和对方确认退款细节。' : '暂无聊天记录' }}
        </div>
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
      <div class="toast" v-if="toastState.visible" :data-variant="toastState.type">
        {{ toastState.message }}
      </div>
    </Teleport>

    <div v-if="detailProduct" class="detail-overlay">
      <div class="detail-modal">
        <button class="ghost detail-close" @click="closeProductDetail">×</button>
        <div class="detail-main">
          <div class="detail-media">
            <template v-if="detailProduct.videoUrl">
              <video :src="formatImg(detailProduct.videoUrl)" controls playsinline preload="metadata"></video>
            </template>
            <template v-else>
              <img :src="formatImg(detailProduct.imageUrl)" :alt="detailProduct.name" />
            </template>
          </div>
          <div class="detail-side">
            <h2>{{ detailProduct.name }}</h2>
            <p class="muted">￥{{ Number(detailProduct.price || 0).toFixed(2) }}</p>
            <p class="muted">分类：{{ detailProduct.category?.name || '未分类' }}</p>
            <p class="muted">店铺：{{ resolveStoreName(detailProduct) }}</p>
            <div v-if="detailProduct.sizesDetail?.length" class="size-chips">
              <button
                v-for="sz in detailProduct.sizesDetail"
                :key="sz.label"
                class="ghost"
                :class="selectedSizes[detailProduct.id] === sz.label && 'chip--active'"
                type="button"
                @click="selectSize(detailProduct.id, sz.label)"
              >
                {{ sz.label }} / 库存{{ sz.stock }}
              </button>
            </div>
            <p class="muted" v-else>库存：{{ detailProduct.stock }}</p>
            <p>{{ detailProduct.description }}</p>
            <div class="row-inline" style="gap:8px;">
              <button class="primary" type="button" @click="addToCart(detailProduct, selectedSizes[detailProduct.id])">加入购物车</button>
              <button class="ghost" type="button" @click="openChat(detailProduct)">咨询</button>
            </div>
            <div class="detail-merchant-card">
              <div class="detail-merchant-card__header">
                <div>
                  <small class="muted">商家评分</small>
                  <strong>{{ renderAverageRating(detailProduct.merchantRatingAverage) }} / 5</strong>
                </div>
                <div class="detail-merchant-score">
                  <span class="detail-merchant-score__stars">{{ renderAverageRatingStars(detailProduct.merchantRatingAverage) }}</span>
                  <small class="muted">{{ Number(detailProduct.merchantRatingCount || 0) }} 条店铺评价</small>
                </div>
              </div>
              <div class="detail-merchant-meta">
                <span class="tag tag--soft">商品销量 {{ Number(detailProduct.salesCount || 0) }}</span>
                <span class="tag tag--soft">商品所属店铺 {{ resolveStoreName(detailProduct) }}</span>
              </div>
              <div v-if="detailLoading" class="loading loading--compact">正在加载商家评价...</div>
              <div v-else class="detail-merchant-reviews">
                <div v-if="!detailProduct.merchantReviews?.length" class="empty">该店铺暂无买家评价</div>
                <div
                  v-else
                  v-for="review in detailProduct.merchantReviews"
                  :key="`${review.orderNumber || 'review'}-${review.createdAt || ''}`"
                  class="comment-item"
                >
                  <div class="comment-meta">
                    <strong>{{ review.buyerUsername || '买家' }}</strong>
                    <span class="tag tag--soft">{{ renderMerchantRating(review.rating) }}</span>
                    <small class="muted">{{ formatDateTime(review.createdAt) }}</small>
                  </div>
                  <div class="comment-body">{{ review.content || '已提交评分，暂未填写文字评价' }}</div>
                </div>
              </div>
            </div>
            <div class="divider"></div>
            <h3>商品评论</h3>
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
          <button class="ghost" @click="closeRegisterModal">×</button>
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
            <input v-model="registerForm.password" type="password" placeholder="请设置登录密码" />
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
              <option value="MERCHANT">商家（完善资料并审核后上架）</option>
            </select>
          </label>
          <button class="primary block" type="submit">注册并登录</button>
          <small class="muted">商家注册后先补充基本资料，再提交管理员审核；审核通过后才可使用完整功能。</small>
        </form>
      </div>
    </div>

    <button
      v-if="cart.length && currentPage !== 'checkout'"
      class="floating-cart"
      type="button"
      @click="go('checkout')"
    >
      <span class="floating-cart__count">{{ cartCount }}</span>
      <div class="floating-cart__meta">
        <strong>去结算</strong>
        <p>{{ cartMerchantCount > 1 ? `${cartMerchantCount} 家商家 · ￥${cartTotal}` : `￥${cartTotal}` }}</p>
      </div>
    </button>
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

.orders-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.orders-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.orders-summary-card {
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 14px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.orders-summary-card[data-variant='accent'] {
  background: linear-gradient(135deg, rgba(16, 163, 127, 0.14), rgba(16, 163, 127, 0.04));
  border-color: rgba(16, 163, 127, 0.28);
}

.orders-summary-card h3 {
  margin: 6px 0 4px;
}

.orders-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.orders-filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.order-search {
  max-width: 380px;
}

.order-card-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card {
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--panel);
  box-shadow: var(--shadow);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.order-card__top-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.order-card__number {
  margin: 6px 0 4px;
  font-size: 18px;
}

.order-card__amount {
  font-size: 20px;
  color: var(--accent);
}

.order-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-progress__track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.order-progress__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(120deg, #10a37f, #0b8a6d);
  transition: width 180ms ease;
}

.order-progress__fill[data-variant='PENDING_PAYMENT'],
.order-progress__fill[data-variant='REFUND_REQUESTED'] {
  background: linear-gradient(120deg, #f59e0b, #d97706);
}

.order-progress__fill[data-variant='REFUNDED'],
.order-progress__fill[data-variant='REJECTED'] {
  background: linear-gradient(120deg, #64748b, #475569);
}

.order-progress__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.order-card__body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.order-card__section {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px;
  background: var(--panel-muted);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-card__section p {
  margin: 0;
}

.order-card__section--notice[data-variant='warning'] {
  border-color: rgba(245, 158, 11, 0.35);
  background: rgba(245, 158, 11, 0.08);
}

.order-card__section--notice[data-variant='neutral'] {
  border-color: rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.9);
}

.order-card__section--review {
  border-color: rgba(16, 163, 127, 0.2);
  background: linear-gradient(180deg, rgba(16, 163, 127, 0.08), rgba(255, 255, 255, 0.96));
}

.order-item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.order-card__actions {
  padding-top: 2px;
}

.order-review__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.order-review__stars {
  color: #f59e0b;
  letter-spacing: 1px;
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

.status-badge[data-variant='ACTIVE'] {
  border-color: #10a37f;
  background: rgba(16, 163, 127, 0.1);
  color: #0f5132;
}

.status-badge[data-variant='PENDING_PAYMENT'] {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.12);
  color: #92400e;
}

.status-badge[data-variant='PLACED'] {
  border-color: #0ea5e9;
  background: rgba(14, 165, 233, 0.1);
  color: #075985;
}

.status-badge[data-variant='REFUND_REQUESTED'] {
  border-color: #f97316;
  background: rgba(249, 115, 22, 0.1);
  color: #9a3412;
}

.status-badge[data-variant='REFUNDED'],
.status-badge[data-variant='REJECTED'] {
  border-color: #64748b;
  background: rgba(100, 116, 139, 0.12);
  color: #334155;
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

.status-badge[data-variant='DELETED'] {
  border-color: #64748b;
  background: rgba(100, 116, 139, 0.12);
  color: #334155;
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

.cart-merchant-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.cart-merchant-overview__metric {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: linear-gradient(135deg, rgba(16, 163, 127, 0.08), rgba(16, 163, 127, 0.02));
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cart-merchant-overview__metric strong {
  font-size: 18px;
}

.merchant-group-list {
  display: grid;
  gap: 10px;
}

.merchant-group-list--compact {
  gap: 8px;
}

.merchant-group-card {
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: #fff;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.merchant-group-card--compact {
  padding: 10px 12px;
  border-radius: 12px;
  box-shadow: none;
  background: var(--panel-muted);
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

.catalog-toolbar-actions {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.catalog-store-filter {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: min(280px, 100%);
}

.catalog-store-filter select {
  min-width: 240px;
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

.catalog-store-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.catalog-metrics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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

.tag--soft {
  background: rgba(16, 163, 127, 0.08);
  color: #0f5132;
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

.checkout-insight {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(16, 163, 127, 0.08), rgba(255, 255, 255, 0.92));
}

.checkout-insight__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.checkout-insight__header h4 {
  margin: 6px 0 0;
}

.checkout-address-preview {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.checkout-address-preview--tight {
  background: var(--panel-muted);
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

.address-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px;
}

.address-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--panel);
  padding: 12px;
  box-shadow: var(--shadow);
}

.address-card--selectable {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.address-card--selectable:hover {
  transform: translateY(-1px);
}

.address-card--selectable input {
  margin-top: 2px;
}

.address-card--active {
  border-color: #10a37f;
  box-shadow: 0 14px 28px rgba(16, 163, 127, 0.12);
  background: linear-gradient(180deg, rgba(16, 163, 127, 0.08), #fff);
}

.address-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.pay-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 360px);
  gap: 16px;
}

.pay-layout__main {
  min-width: 0;
}

.pay-summary-card {
  border: 1px solid var(--border);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(16, 163, 127, 0.08), rgba(255, 255, 255, 0.96));
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pay-summary-card h4 {
  margin: 0;
}

.pay-summary-card__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.pay-summary-card__metrics > div {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pay-method-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.pay-method-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: #fff;
  cursor: pointer;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.pay-method-card:hover {
  transform: translateY(-1px);
}

.pay-method-card--active {
  border-color: #10a37f;
  box-shadow: 0 14px 28px rgba(16, 163, 127, 0.12);
  background: linear-gradient(180deg, rgba(16, 163, 127, 0.08), #fff);
}

.pay-method-card input {
  margin-top: 3px;
}

.pay-method-desc {
  margin-top: 2px;
}

.pay-footer-actions {
  grid-column: 1 / -1;
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

.review-rating-input {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.review-rating-input__item--active {
  border-color: #10a37f !important;
  background: linear-gradient(180deg, rgba(16, 163, 127, 0.1), #fff) !important;
  color: #0f5132;
}

.review-rating-input .ghost {
  min-height: 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.review-rating-input .ghost span {
  font-size: 20px;
  color: #f59e0b;
}

.review-char-count {
  align-self: flex-end;
  font-size: 12px;
  color: var(--muted);
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

.modal__header--stack {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.modal__header--stack h3 {
  margin: 0;
}

.action-dialog {
  width: min(520px, 100%);
}

.action-dialog[data-variant='warning'] {
  border-color: rgba(245, 158, 11, 0.4);
}

.action-dialog[data-variant='error'] {
  border-color: rgba(239, 68, 68, 0.35);
}

.action-dialog[data-variant='success'] {
  border-color: rgba(34, 197, 94, 0.35);
}

.action-dialog__message {
  margin: 0;
  line-height: 1.7;
}

.action-dialog__actions {
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
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

.floating-cart {
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 19;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 196px;
  padding: 12px 14px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: linear-gradient(135deg, #0f172a, #10a37f);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.28);
  cursor: pointer;
}

.floating-cart__count {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  font-weight: 700;
}

.floating-cart__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.floating-cart__meta p {
  margin: 0;
  color: rgba(255, 255, 255, 0.76);
  font-size: 12px;
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

  .orders-toolbar,
  .order-card__top,
  .order-card__top-meta,
  .order-progress__meta {
    align-items: flex-start;
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

  .pay-layout {
    grid-template-columns: 1fr;
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

  .cart-merchant-overview,
  .pay-summary-card__metrics {
    grid-template-columns: 1fr;
  }

  .orders-summary-grid,
  .order-card__body {
    grid-template-columns: 1fr;
  }

  .checkout-insight__header,
  .checkout-address-preview {
    flex-direction: column;
  }

  .floating-cart {
    left: 12px;
    right: 12px;
    bottom: 12px;
    min-width: 0;
    border-radius: 18px;
    justify-content: flex-start;
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





