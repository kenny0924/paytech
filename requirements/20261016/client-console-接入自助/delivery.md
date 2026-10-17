# Client Console 接入自助 — 交付记录

## 适用阶段与授权说明

完整 8 阶段。用户 2026-10-16 夜：“帮我分析一下【接入（API 凭证、Webhook）】的需求……等租户与工作台的任务开发完后立马做这个……不用等我授权。”

Claude 的理解与边界：阶段 1—5 不停下等待确认，各阶段待决项按建议决定并在本文件与正文标注，用户醒来可推翻；**提交、推送、部署 mor-test、对共享环境执行 SQL 不在夜间执行**，留待用户明确授权。

## 确认记录

| 阶段 | 日期 | 成果 | 依据 | 下一步 |
|---|---|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-16 夜 | [requirement.md](./requirement.md)（决定 N1—N12 按建议） | 用户夜间授权；2026-10-17 复核“全部确认” | 阶段 2 |
| 2. 现状差异与影响分析 | 2026-10-16 夜 | [technique.md](./technique.md) 第 1—4 节（决定 S1—S3） | 用户夜间授权；2026-10-17 复核“全部确认” | 阶段 3 |
| 3. 方案、接口与数据设计 | 2026-10-16 夜 | [technique.md](./technique.md) 第 5—11 节（决定 T1—T4） | 用户夜间授权；2026-10-17 复核“全部确认” | 阶段 4（待租户与工作区研发完成后启动） |
| 4—5. 研发与本机验证 | 2026-10-16 夜 | 见下文 | 用户醒后要求“发布test”，视为接受阶段 4—5 结果 | 阶段 6—7 |
| 6—7. test 发布与验收 | 2026-10-16 | [发布记录](./release/release-20261016.md) | “发布test” + “授权提交并部署” | 用户验收、推送授权 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 本机代码修改、本机测试、本机全链路验证 | 夜间授权范围内 |
| 本机 Vault / 本机库注册数据 | 夜间授权范围内（仅本机，记录执行内容） |
| 本地提交、部署 mor-test、test SQL、test 路由登记 | 2026-10-16 用户“发布test”并选择“授权提交并部署”（不推送） |
| 推送 | 未授权 |

## 当前阶段

阶段 6—7 已完成（2026-10-16，用户“发布test”）：提交 `9ae668a`（2026-10-17 已推送），mor-test 已部署并验收，见[发布记录](./release/release-20261016.md)。test 验收：工作区 53/53，开发者 78/78。夜间决定（N1—N13、S1—S3、T1—T4）用户尚未逐条复核。下一步：用户验收，再决定推送和阶段 8 收尾。

## 本机 SQL 执行记录（阶段 4，2026-10-16 夜，access-platform）

| 时间 | 目标 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-16 01:3x +04 | 本机 `127.0.0.1:13306/mor_platform`（自动测试库） | [db/ddl.sql](./db/ddl.sql) | 执行前 `credential_name` 列不存在 → 新增；首次经 docker exec 默认字符集写入的注释乱码，随即用 utf8mb4 连接 `MODIFY COLUMN` 修正，核对注释正确 |
| 同上 | 本机 `mor_platform_e2e`（全链路库） | [db/ddl.sql](./db/ddl.sql) | 同上 |
| 同上 | 本机 `mor_platform_e2e` | [db/dml.sql](./db/dml.sql) | 插入 4 个权限码；为 2 个已开通工作区的 TENANT_OWNER 补录 8 行角色权限 |

`mor_platform` 是自动测试独占库（权限表须为空），不执行 dml.sql；mor-test 均未执行。

## 阶段 4 研发结果（2026-10-16 夜，未提交）

| 系统 | 结果 | 验证 |
|---|---|---|
| Platform | `ClientAuthorizationApi`（CLIENT 权限判断）、`ClientApiCredentialApi`（6 个操作，密钥前缀 `ck-` + 20 位 base62，256 位 secret，Argon2id）、`ClientWebhookApi`（9 个操作，按 ID 先校验租户归属，客户视图脱敏）；首次 ACTIVE 投影授予 OWNER 4 个开发者权限；`t_api_credential.credential_name` | 新增 11 项；全量测试 662/662，verify 被全库覆盖率门禁拦截（指令 62%、分支 43%，此前 60% / 42%） |
| Client BFF | `ClientDeveloperController` 15 个转发；拦截器（租户上下文 + 权限判断 + no-store）；W02 `capabilities`；Platform 404 正确透传 | 全量 57/57（新增 10 项） |
| Client Web | 开发者分组、API 凭证页、Webhook 页、一次性密钥弹窗 | `npm test` 48/48、构建通过；对外 Webhook 路径已与 BFF 登记对齐（`/webhook-endpoints`、`/webhook-deliveries`） |
| 登记计划 | 新增 D01—D15 共 15 条路由（只改脚本，未执行登记） | 生成差异只含新增 |

偏离设计：密钥前缀 `ck-`（现有前缀规则不允许下划线）；凭证创建 / 轮换、Webhook 轮换 / 重发返回 201；投递查询默认最近 30 天、跨度 ≤ 31 天；`expectedVersion` 缺失或为 0 返回 422；并发冲到上限未单测。

## 阶段 5 本机验证（2026-10-16 夜，e2e-access，未经用户确认）

**环境与制品**：Platform、Client BFF 从当前工作区打包（JDK 25，`-DskipTests -Djacoco.skip=true`），复制到 scratchpad `e2e-artifacts-access/`，经 `E2E_JAR_DIR` 启动；CCT 未变；Client Web 为在线 Vite 开发服务器。

**本机环境变更（均为本机，未触及 mor-test）**

| 项 | 内容 |
|---|---|
| `verification/local-e2e/run-service.sh`（入驻需求目录） | Platform 启动参数新增 `--webhook.enabled=true --webhook.allowed-schemes=https,http --webhook.trusted-networks=127.0.0.1/32 --webhook.http.callers.commerce-service=order.paid`（注释说明仅本机验收） |
| 本机 Vault（容器 `mor-local-vault-service-1`，管理接口） | 新增数据类型 `WEBHOOK_SIGNING_SECRET_V1`（REFERENCE、SECRET、REGEX `^whsec_[A-Za-z0-9_-]{43}$`、禁止人工加解密）；新增规则 `platform-support-service / PROTECT` 与 `REVEAL`，用途 `WEBHOOK_SIGNING` |
| 本机 `mor_platform_e2e` | 执行 Webhook 会话已评审的 `platform-support-service/docs/database/sql/changes/2026-10-15-webhook-delivery.sql`（sha256 前缀 `d7e75352ca8536b2`，只建 5 张空表）；再次执行本需求 `db/dml.sql`（幂等），为工作区验收后新开通的 3 个工作区补录 12 行 OWNER 授权；验收夹具写入 1 行 `t_api_scope`（`orders.read`，ID 9300000000000001） |

**API 验收**：新增 `verification/local-e2e/developer_flow.py`，经 Nginx → Gateway → BFF → Platform，**83/83 通过**：capabilities、Scope 目录、创建（一次性密钥、`no-store`）、PLT-I03 校验通过且身份为本工作区、篡改密钥 401、列表无密钥、立即轮换（旧 401 / 新 200）、宽限轮换（新旧都 200）、撤销 401 与重复撤销、上限 10、Webhook 新建（一次性密钥、`no-store`）、测试事件送达且签名可用密钥校验、模拟 Commerce 提交 `order.paid` 送达并验签、投递列表 / 详情（无运营字段）、重发（新投递、事件不变）、停用、跨工作区凭证 / 投递 / 端点 404、账号上下文 403。三份服务日志检索凭证密钥与 `whsec_` 均为 0 条。本机未登记 Client API（凭证）路由，凭证可用性以 Gateway 认证使用的 PLT-I03 私网接口验证。

**浏览器验收**：新增 `verification/local-e2e/developer_browser.mjs`（无头 Chromium），**10/12 通过**：登录进入工作区、开发者分组、新建凭证与一次性密钥弹窗（未勾选不能关闭、Esc 无效、关闭后页面与浏览器存储无密钥）、列表、轮换、撤销、凭证页 390px、发送测试事件、投递列表与详情、Webhook 页 390px。截图在 scratchpad `browser-dev/shots/`。两项失败见下。

**发现的问题**

| # | 问题 | 证据 | 处理 |
|---|---|---|---|
| 1 | Web 发送测试事件与重发时带 `{}` 请求体，BFF 要求空 Body → 400 `当前操作不接受 Body` | API 复现 | **已修正 Web**：`DeveloperPages.tsx` 两处改为不带请求体（同“提交申请”写法）；`npm test` 48/48、构建通过 |
| 2 | Platform 新建端点复用修改请求体，要求 `expectedVersion`（新建无意义）；Web 新建不发 → 400 `INVALID_REQUEST`，**页面无法新建端点** | 浏览器第 8 步；带 `expectedVersion:1` 时 201 | 未修，需拆分 Platform 新建请求体（或 Web 补字段，不建议） |
| 3 | 新建 / 修改端点弹窗中订阅事件复选框被撑成大方块，布局错乱 | 截图 `08-新建_Webhook_端点…png` | 未修（样式） |
| 4 | 页面主按钮（新建凭证、新建端点、重新发送等）为浏览器默认样式 | 截图 04、12 | 未修（样式） |
| 5 | 测试事件同步发送、不生成投递记录；需求 AC-09 写“进入投递列表” | API 实测 | 记为需求与实现偏差，需确认以哪个为准 |
| 6 | “等待重试”的投递显示“重新发送”按钮，点击后 400，提示只有“业务校验未通过” | 浏览器第 11 步 | 未修（交互建议：非终态隐藏按钮或给出明确原因） |

**遗留**：本机验收账号 `workspace-*`、`browser-ws-*`、`scenario-*` 及其已批准申请、凭证、Webhook 端点与投递记录未清理；`mor_platform_e2e` 的 `orders.read` 目录行保留。

## 阶段 5 缺陷修复（2026-10-16 夜，未提交）

| 缺陷 | 处理 |
|---|---|
| Web 测试事件 / 重发带 `{}` 请求体被 BFF 拒绝 | 已在阶段 5 中修复（不带请求体） |
| Platform 新建端点要求 `expectedVersion`，页面新建 400 | 新增 `ClientCreateWebhookEndpointRequestBody`（url、eventTypes），修改继续用 `ClientSaveWebhookEndpointRequestBody`（`expectedVersion` 改为 `@Positive`，删除控制器内重复校验）；BFF D08 改用新请求体；严格 JSON 下新建不再接受 `expectedVersion` |
| 订阅事件复选框被全局 `input` 样式撑成大方块 | `style.css` 增加 `input[type=checkbox],input[type=radio]` 规则（宽高 auto、无内边距） |
| 主操作按钮为浏览器默认样式 | 新建凭证、创建、新建端点、保存、发送测试事件、重新发送、关闭密钥弹窗改用现有 `primary compact`，复制按钮用 `secondary compact` |
| 等待重试的投递也显示“重新发送”，点击后只提示“业务校验未通过” | 只对 Platform 允许的已结束状态（SUCCEEDED、DEAD、CANCELLED，见 `WebhookDeliveryStatus.isTerminal`）显示重发；重发被拒（400）时显示具体可能原因（Foundation HTTP 客户端不向下游透传上游业务原因，Web 按 Platform 规则给出说明） |
| 原 AC-09 要求测试事件进入投递记录 | 需求改为同步显示结果（决定 N13） |

验证：
- Platform `ClientWebhookSelfServiceTest`、`ClientApiCredentialSelfServiceTest`、`ClientWorkspaceContextTest` 14/14；`platform-support-api` 已重新安装。
- Client BFF `verify` 57/57（D08 测试改为 Web 实际请求体）。
- Client Web `npm test` 49/49（新增重发状态规则用例）、构建通过。
- 本机 Platform / Client BFF 用新 jar（`scratchpad/e2e-artifacts-fix`）重启；`developer_flow.py` **83/83**（新建端点去掉 `expectedVersion` 兼容分支）；`developer_browser.mjs` **12/12**（新增“等待重试的投递不显示重新发送”；新建端点改为必过步骤）。
- 浏览器未覆盖：已结束投递在页面上重发——Web 只允许 HTTPS 地址而本机无可信 HTTPS 接收端，且本机未运行 XXL-Job，到点重试不会执行、停用端点后投递不会转为已取消；重发由 `developer_flow.py`（HTTP 接收端、已成功投递）覆盖。
- 本次新建本机验收账号 3 个（`browser-ws-*@example.test`），未清理。
