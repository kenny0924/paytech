# 卡 BIN-C：operations-console-bff 与 operations-console-web

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin-ops`（嵌套在主工作区内）
- 公共规则：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/payment-3DS全链路/tasks/00-common.md`（必须先读）
- 设计权威：[technique.md](../technique.md) 3.5.1（Card 运营接口合同）、3.5.3（Platform 文件接口）、3.5.7（BFF 路由）、3.7（页面规格与原型说明）
- 原型：`operations-console-web/docs/operations-console.pen` 中 5 张“平台配置 · 卡 BIN 库 01—05”画板（y=85500）。用 Pencil MCP 的只读能力查看（`mcp__pencil__execute` 里只用 `Get` / `TakeScreenshot`，**不得 Insert / Update / Delete**）；或按 technique.md 3.7 文字实现。
- 报告：`tasks/C-console-report.md`

## 范围

1. ops-bff：`CardClientConfiguration`（Nacos 发现、严格 Problem 解码、Owner 错误码登记，pom 加 `paytech-card-api`）；9 条 `/card-admin/v1/bin-library/**` 路由；Platform 内部文件接口上传开始 / 完成 / 查询转发（`X-Caller-Service: operations-console-bff`）；操作人头透传。Card API 模块由卡 BIN-A 新增——本卡先按 technique.md 3.5.1 合同在 `paytech/card/paytech-card-api` 写出接口与 DTO（与 BIN-A 同名同包，合并时以 BIN-A 为准、对齐差异），测试用 DTO Mock 端点。
2. ops-web：`src/card/` 模型、`src/pages/CardBinLibrary.tsx`（主从布局、BIN 查询、版本表、详情面板三态、导入三步对话框含本地表头预检与直传进度、激活 / 回切确认含 409）；导航登记（`pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts`、`menu.test.ts`）；`src/commercial/capabilityConditions.ts` 的 `ISSUER_REGION` 建议值改为 6 个区域、`FUNDING_TYPE` 补 CHARGE、`CARD_BRAND` 补 DINERS。
3. 前端测试：`npm test`、`tsc`、`npm run build` 通过；不做浏览器验收（阶段 5）。

## 本机 SQL

无。

## 不在本卡

card 服务实现、platform-support、payment、risk。
