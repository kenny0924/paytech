# Webhook 端点地址错误码交付记录

## 适用阶段（评审稿）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2—3. 现状与方案 | 已确认 |
| 4. 研发实现 | 已确认 |
| 5. 本地验收 | 已确认 |
| 6—7. test 发布 | 已确认 |
| 8. 提交 | 已本地提交（不推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-17 | 立项 | 处理 Operations Webhook 管理的遗留项，选择"只给 Webhook 加专用错误码" | 登记需求，交付阶段 1 评审稿 |
| 2026-10-17 | 1 | "继续"：按建议确认 Q-01 三个错误码、Q-02 只改 Operations 路径、Q-03 阶段 2—3 合并且不出画板 | 阶段 2—3 现状与方案（只写文档） |
| 2026-10-17 | 2—3 | "可以"：接受评审稿 | 阶段 4 研发（Platform、BFF、Web 代码与测试；不改环境、不提交） |
| 2026-10-17 | 4→5 | "可以"：确认阶段 4，授权本机重启 BFF 与 Platform 并验收 | 阶段 5 本机验收；不改 mor-test、不提交 |
| 2026-10-17 | 5→6 | "可以"：确认阶段 5 | 阶段 6 mor-test 发布清单（只读核对，不改环境） |
| 2026-10-17 | 6→7 | "可以"：接受发布清单 | 按清单在 mor-test 备份、构建、替换 BFF → Platform → Web、验收与清理；不推送、不提交 |
| 2026-10-17 | 7→8 | "可以"：确认阶段 7，授权本地提交 | 私有索引提交本需求 7 个代码文件、需求目录与台账中本需求部分；不推送 |

## 阶段 4 研发结果（2026-10-17）

| 系统 | 改动 | 开发检查 |
|---|---|---|
| Platform | 新增 `platform-support-api` `WebhookEndpointErrorCode`（3 个错误码，写明消费者）；`WebhookManagementSupport.requirePublicUrl` 的语法 / 协议拒绝与 DNS / 内网拒绝改抛 `FailException(错误码, 安全说明, 原因)`；`WebhookManagementApi` 新建 / 修改接口说明补充错误码。Client Console 的 `ClientWebhookSupport` 未改 | `WebhookEndpointManagementTest` **6/6**（新建：ftp 与带用户信息 → NOT_ALLOWED，内网 → NOT_PUBLIC，`.invalid` 主机 → UNRESOLVABLE；修改：内网 → NOT_PUBLIC）；`ClientWebhookSelfServiceTest` **3/3**（Client 路径不变）；`WebhookClientSearchTest` 2/2 |
| Operations BFF | `PlatformClientConfiguration#webhookManagementApi` 改用单独 proxy 并登记 `WebhookEndpointErrorCode.values()`（仍开启字段校验投影） | `WebhookManagementBrowserAutoTest` **31/31**（新增 3 例：新建 / 修改的 3 个错误码转为 400、`detail` 为枚举说明、`requestId` 为本次请求、无上游说明与上游请求标识）；用临时聚合 POM 与工作区 `platform-support-api` 同 reactor 构建 |
| Operations Web | `webhook.ts` `urlErrorMessage` 改为按错误码查文案（删除按 `detail` 匹配的正则）；命中时 `useWebhookCommand` 已有逻辑把提示放在地址字段下且不弹通用通知 | `npm test` **1066/1066**；`npm run build` 通过 |

## 阶段 5 本机验收（2026-10-17）

| 动作 | 结果 |
|---|---|
| 协调 | Payment（1e）与 Client Console（76）同意重启本机 Platform / BFF；完成后已通知 1e |
| 构建与重启 | 从工作区构建 Platform 与 BFF jar（临时聚合 POM，同 reactor 含 `platform-support-api`），复制到 `E2E_JAR_DIR`；按 BFF → Platform 顺序用 `run-service.sh` 重启（Platform 06:41:11Z 启动），日志无 ERROR。Web 为 Vite 开发服务器，自动加载 |
| 接口 | [local_errcode.py](./verification/local_errcode.py) 经 `https://127.0.0.1:18444`：新建内网 / `.invalid` / ftp 分别返回 400 `WEBHOOK_URL_NOT_PUBLIC` / `WEBHOOK_URL_UNRESOLVABLE` / `WEBHOOK_URL_NOT_ALLOWED`，`detail` 为固定说明；修改为内网地址同样返回；未开通 Client 的拒绝仍是 `BUSINESS_CHECK_FAILED`，说明为"业务校验未通过" |
| 页面 | "修改端点"弹窗依次填入四种地址：前三种（内网、无法解析、带用户信息的 https）在地址字段下显示对应说明，弹窗保持打开，不弹通用通知；ftp 被前端格式校验先拦下（不发请求），字段下显示前端提示。截图见 [assets/stage5](./assets/stage5/) |
| 合计 | **15/15**；合成数据已清理。首次运行时页面 ftp 用例按服务端说明断言而失败，原因是前端格式校验先拦截，属于预期行为；改用带用户信息的地址覆盖协议类拒绝后重跑通过 |

## 当前阶段与下一确认点

阶段 1—8 已完成：mor-test 已部署（验收 13/13），已本地提交（未推送）。
