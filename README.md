# MK Menswear 平台说明

本项目为男装电商平台，包含用户、商家、管理员三端。后端为 Spring Boot 3 + JPA + MySQL，前端为 Vite + Vue 3。

## 快速运行
- 后端：`cd demo && ./mvnw spring-boot:run`（默认 8080）
- 前端：`cd front_vue && npm install && npm run dev -- --host --port 4173`；生产构建：`npm run build`
- 接口基址：前端默认 `http://localhost:8080`，可用环境变量 `VITE_API_BASE` 调整

## 架构与模块
- 分层：Controller（Web/API）→ Service（业务）→ Repository（JPA）→ MySQL；全局拦截器做 Token 鉴权与会话续期，统一异常处理。
- 存储：商品/订单/钱包/聊天/日志均在 MySQL；上传的图片/视频存储于后端 `uploads/` 并以 `/uploads/` 暴露。
- 角色：未登录仅可浏览商品；用户（USER）、商家（MERCHANT）、管理员（ADMIN）。

## 鉴权
- 登录 `POST /api/auth/login` 获取 token，后续请求携带 `X-Auth-Token`（或 `Authorization: Bearer` 兼容）。
- 未登录访问受保护接口返回 `{"message":"未登录或会话已过期"}`。

## 主要业务要点
- 商品：支持图片/视频、AI 补充介绍、尺码库存（按尺码扣减），删除会级联删除关联聊天。
- 订单与支付：下单可选站内钱包（“金币”）或支付宝沙箱（托管）。钱包支付即时扣款；支付宝成功回调后落账并更新订单状态。订单状态展示“由支付宝托管”用于托管场景。
- 钱包：充值、收支流水、订阅开店费（500 元/月，未订阅成交抽 5% 给管理员）。管理员可查看并调整用户钱包。
- 退款：用户可发起退款，按全局审批档位（低档自动/高档管理员同意）处理。
- 聊天：商品咨询客服式对话，商品详情页进入，商家/管理员带可见标签。
- AI：Kimi 接入，“AI 帮你选”与商品 AI 介绍，需管理员配置 API Key。
- 位置：支持地图选点与反查具体地址，用于收货地址与下单。
- 终端：管理员 HMAC-SHA256 验证后可执行命令（需密钥）。

## 特殊逻辑
- 商家状态：新注册为待审核，未通过/封禁时不展示侧边栏，整屏提示“未被审核”（黄）或“已被封禁”（红+警示），并提供“退出登录”按钮。管理员侧边栏“商家审核”有小红点提示待审核数量。
- 上架/编辑商品：支持上传图片/视频、尺码库存录入（鞋/帽子特殊尺码），操作成功在大屏弹出无阻塞提示。
- 侧边栏与顶部：功能集中在侧边栏；顶部保留登录/注册，固定不随滚动消失。

## 核心 API 摘要
- Auth：`POST /api/auth/login`、`/register`、`/logout`、`POST /api/auth/password`（改密）
- 分类：`GET /api/categories`
- 商品：`GET /api/products`、`GET /api/products/{id}`、`GET /api/products/mine`、`POST /api/products`、`PUT /api/products/{id}`、`DELETE /api/products/{id}`
- 评论：`GET /api/products/{id}/comments`、`POST /api/products/{id}/comments`
- 订单：`POST /api/orders`（含地址与支付方式）`GET /api/orders/mine`、`POST /api/orders/{id}/refund`
- 支付：`GET /api/payments/mine`；支付宝沙箱下单 `POST /api/payments/alipay/pay`；支付宝沙箱充值（当前已回退为站内充值）
- 钱包：`GET /api/wallet/me`、`POST /api/wallet/recharge`、`POST /api/wallet/subscribe`、管理员钱包 `GET /api/wallet/admin/users`、`POST /api/wallet/admin/users/{id}/wallet`、`POST /api/wallet/admin/users/{id}/password`
- 聊天：`GET /api/chat`、`GET /api/chat/recent`、`POST /api/chat`
- 上传：`POST /api/upload/image`（自动裁剪 400x400）、`POST /api/upload/video`
- AI：`POST /api/ai/recommend`、`GET /api/ai/product/{id}/intro`；Key 管理 `GET/POST /api/admin/ai/key`
- 管理端：概览 `/api/admin/overview`，商家 `/api/admin/merchants` + 状态变更，订单 `/api/admin/orders` + 待审 `/api/admin/orders/pending` + 审批、退款审批、收益 `/api/admin/revenue`、登录/系统日志、交易审批档位 `/api/admin/settings/approval`
- 位置：`GET /api/location/reverse`，`GET /api/location/config`
- 终端：`POST /api/admin/terminal/run`（需密码+HMAC 签名）

## 目录
```
C:\Java Projects
├─ demo/        # 后端 Spring Boot
└─ front_vue/   # 前端 Vue3 + Vite
```

## 注意
- 生产部署请修改数据库、上传目录、日志路径、跨域与密钥配置。
- 未经授权请勿推送到远端仓库。当前任务未进行任何远端提交。
