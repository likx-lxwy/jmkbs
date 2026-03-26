# MK Menswear

男装电商示例项目，包含用户端、商家端、管理员端。

## 技术栈

- 后端：Spring Boot 3、MyBatis、MySQL
- 前端：Vue 3、Vite

## 启动

- 后端：`cd demo && ./mvnw spring-boot:run`
- 前端：`cd front_vue && npm install && npm run dev -- --host --port 4173`
- 生产构建：`cd front_vue && npm run build`

默认后端接口地址为 `http://localhost:8080`。

## 主要能力

- 商品：分类、上架、编辑、删除、图片/视频、尺码库存
- 订单：下单、支付、确认收货、退款申请、退款审核
- 售后：退款订单内的买家/商家沟通聊天
- 钱包：余额、充值、订阅、管理员查看用户钱包
- 聊天：商品咨询、最近会话
- 地址：地址簿、默认地址、定位与地图选点
- 管理端：概览、商家审核、订单管理、收益、库存预警、钱包管理

## 核心接口

- 认证：`/api/auth/login`、`/api/auth/register`、`/api/auth/logout`、`/api/auth/password`
- 分类：`/api/categories`
- 商品：`/api/products`、`/api/products/mine`
- 评论：`/api/products/{id}/comments`
- 订单：`/api/orders/mine`、`/api/orders/batch`、`/api/orders/{id}/refund`
- 售后聊天：`/api/orders/{id}/refund-chat`
- 普通聊天：`/api/chat`、`/api/chat/recent`
- 钱包：`/api/wallet/me`、`/api/wallet/recharge`、`/api/wallet/subscribe`
- 支付：`/api/payments/mine`、`/api/payments/alipay/pay`
- 地址：`/api/addresses`
- 位置：`/api/location/reverse`、`/api/location/config`
- 管理端：`/api/admin/overview`、`/api/admin/merchants`、`/api/admin/orders`、`/api/admin/revenue`

## 目录

```text
jmkbs/
|- demo/       # Spring Boot 后端
|- front_vue/  # Vue 3 前端
```

## 说明

- 历史废弃功能入口及对应接口已移除。
- 如需初始化数据库，请使用仓库中的 SQL 脚本，并按实际环境补充地图与支付配置。
