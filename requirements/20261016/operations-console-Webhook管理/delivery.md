# Operations Webhook 管理交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2. 现状差异与影响分析 | 已确认 |
| 3. 方案、接口与数据设计 | 已确认（Pencil 10 个画板） |
| 4. 研发实现 | 已确认；SQL 只写不执行 |
| 5. 综合验证与本地验收 | 已确认 |
| 6–7. test 发布准备与部署 | 已确认 |
| 8. 提交 | 已本地提交（不推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-16 | 立项 | "我载test没看到webhook的菜单"；"开始帮我做到operations上面吧" | 登记需求并交付阶段 1 评审稿 |
| 2026-10-16 | 1 | "按建议"：确认范围与 AC-01—14；Q-01 选 B（Platform 新增 Client 搜索）、Q-02 一菜单两标签、Q-03 默认 24 小时、Q-04 允许编辑 Operations BFF/Web 并在 Platform 初始化 SQL 追加独立块、Q-05 阶段 1—7 适用 | 阶段 2 现状差异与影响分析（只读分析与文档） |
| 2026-10-16 | 2 | "可以，继续阶段3"：接受 [analysis.md](./analysis.md) 缺口判断、影响范围与第 5 节建议（Client 搜索接口形态、ID 块 `97xxx`、菜单与按钮） | 阶段 3 方案、接口、注册 SQL 清单与 Pencil 页面设计（只写文档与设计） |
| 2026-10-16 | 3 | "确认"：接受 technique.md、10 个画板、ID 块改用 `9000000000000903xx—0907xx`、投递列表不显示下次重试时间 | 阶段 4 研发（Platform、BFF、Web 代码与测试；SQL 只写不执行；不提交） |
| 2026-10-17 | 4 / 5 动作授权 | "可以"：接受阶段 4（含测试事件路由补 `content-type`，注册 128 行）；授权本机 `mor_platform_e2e` 执行注册块与本机启用 SQL、重新打包重启本机 Platform/BFF/Web、合成数据浏览器验收并清理 | 阶段 5 本机联调；不涉及 mor-test、不提交 |
| 2026-10-17 | 5 | "可以"：确认阶段 5 结果；业务错误原因不透出按现状接受（BFF 全局错误投影另行决定） | 阶段 6 mor-test 发布准备（只读核对与发布清单，不改环境） |
| 2026-10-17 | 6→7 | "按建议"：D1=A（`20677f2` + 本需求差异）、D2=A（不发送测试事件、不提交事件，用 example.com）；授权按发布清单在 mor-test 备份、构建、执行注册与启用 SQL、替换 Platform/BFF/Web、验收与清理 | 执行阶段 7；不推送、不碰生产、不提交 |
| 2026-10-17 | 7→8 | "继续"：确认阶段 7 结果，进入阶段 8 | 本地提交本需求改动（私有索引，共享文件只取本需求片段；不推送）；`design.pen` 未在 Pencil 保存，不纳入 |

## 当前阶段与下一确认点

阶段 1—8 已完成：mor-test 已部署并验收（接口 22/22、浏览器 13/13、30 页回归，见[发布记录](./release/release.md#6-执行记录2026-10-17-阶段-7用户按建议d1ad2a)），已本地提交（未推送）。`design.pen` 的 10 个画板已在 Pencil 保存并单独提交（`a469fc9`，与通知管理画板一并）。遗留：BFF 业务错误原因不透出（AC-04 就地显示）、mor-test 补偿扫描调度、只读账号按钮隐藏未在浏览器验证。

## 阶段 4 研发结果（2026-10-17）

| 系统 | 改动 | 开发检查 |
|---|---|---|
| Platform | API `WebhookManagementApi#clients` + `WebhookClientResponseBody`；Access Control `ClientTenantQueryService#searchClients`、`ClientTenant`；Delivery `WebhookQueryService#endpointCounts`、`WebhookEndpointRepository#countTenants`（Mapper XML）、`WebhookEndpointTenantCount`；Web `ListWebhookClientsProcessor`、Request/Response、Controller 与 Converter 方法；`console-resource-capabilities.json` 追加页面；初始化 SQL 追加注册块 128 行（`9000000000000903xx—0907xx`）；测试 `WebhookClientSearchTest`（2 例）与 `WebhookTestMapper#renameTenant` | 本机 Webhook 5 个测试类 **30/30** 通过（含新增 2 例）；全量 verify 留阶段 5 |
| Operations BFF | `WebhookManagementController`（10 路由）、`PlatformWebhookManagementClient(Impl)`、`WebhookManagementModels`；共享文件：`PlatformClientConfiguration`、`ConfigurationManagementRequestConfiguration`（路径与模型白名单、Webhook 无 Body 路由拒绝请求体、写入体积 8192）、`application.yml`（`/webhook-admin` 元数据日志）、`OperationsConsoleBffApplicationAutoTest`、`OperationsHttpBodyNamingAutoTest`（List 元素类型规则）、README | `./mvnw -o verify` **1378/1378** 通过（`WebhookManagementBrowserAutoTest` 28 例）；BFF 无 JaCoCo 门禁 |
| Operations Web | `pages/WebhookManagement.tsx`；`src/webhook/`（模型与解码、命令 Hook、Client 选择、弹窗、投递列表与详情抽屉、css、测试）；共享文件：`pageCapabilities.ts`（+1 页）、`ConsolePage.tsx`、`auditView.ts`、`configurationModel.test.ts`（29→30） | `npm test` **1060/1060**（新增 8 例）；`npm run build` 通过 |

偏差见 [technique.md 第 7 节](./technique.md#7-实现偏差阶段-4)，以及：BFF 原先只拒绝 GET 的请求体，本次为 Webhook 无 Body 路由（测试事件）增加拒绝；该路由仍需 `Content-Type: application/json`（BFF 写请求统一要求），Web 已按此发送；BFF 公共分页转换上限 100，Webhook Client 本地放宽到 200 与 Platform 一致；Platform 代理丢弃未知字段（与通知先例一致），BFF 测试改为断言多余字段不投影。Web 交互细节：测试事件点击即发送；已停用端点不显示轮换；"重发自"显示在 Event ID 下；Client 从 URL 恢复时按 ID 精确匹配。

未做：浏览器联调（阶段 5，需本机执行注册与激活 SQL，届时单独授权）。

## 阶段 5 本机验证（2026-10-17）

**本机执行的动作（已授权）**

| 动作 | 结果 |
|---|---|
| 协调 | 本机 Platform 原为 Client Console 会话所起，经其同意替换；Payment 会话（code-mor-1e）同意后执行，完成后通知 |
| `mor_platform_e2e`：初始化 DML 的 Webhook 权限块（4 行）与 Operations Webhook 管理块（128 行），先回滚试跑核对计数再执行 | 权限 2、授权 2、菜单/按钮 2、路由 10、Header 44、Query 12、限流 60 |
| [activation-local.sql](./db/platform-support-service/activation-local.sql) | 10 / 2 / 2 / 1 行 |
| [acceptance-fixture-local.sql](./db/platform-support-service/acceptance-fixture-local.sql)（合成租户 `880000000000079001` 正常、`79002` 安全暂停） | 验收后经 [acceptance-cleanup-local.sql](./db/platform-support-service/acceptance-cleanup-local.sql) 清理为 0 行（审计与 Vault 合成密文保留） |
| 从工作区构建并以 `E2E_JAR_DIR` 重启本机 Platform（2 次，第 2 次带下方修正）与 Operations BFF；Gateway 未重启（路由经快照生效）；Web 为 Vite 开发服务器 | 启动日志无 ERROR |

**验收结果**

- 接口验收 [local_acceptance.py](./verification/local_acceptance.py)：经 `https://127.0.0.1:18444`（Nginx → Gateway → BFF → Platform）**28/28** 通过，覆盖 AC-01～AC-09（导航、Client 搜索与暂停状态、新建 201 + no-store、列表无密钥、内网 / 非 http(s) / 空订阅 / 第 6 个端点被拒、409、宽限与立即轮换、测试事件双签与 503、commerce-service 提交后送达、投递筛选 / 详情 / Replay、超 31 天 400、三处日志无密钥、写操作审计）。
- 浏览器验收 [local_browser.py](./verification/local_browser.py)：**22/22** 通过，截图在 [assets/stage5](./assets/stage5/)（对应画板 01—10；密钥弹窗截图前已遮盖明文）；进入 Webhook 管理后仅有内网地址用例预期的 400。
- 测试复核：Platform 全量 `clean verify` 671 例中 668 通过，3 例错误均为 `NotificationRecordQueryTest`（工作区内其他会话未提交的通知改动导致插入列数不符，非本需求）；Webhook 6 个测试类全部通过（含新增 Client 搜索 2 例、测试事件审计断言）。BFF 1378/1378（阶段 4）；Web `npm test` 1060/1060、`npm run build` 通过。

**发现与修正**

| # | 问题 | 处理 |
|---|---|---|
| 1 | 发送测试事件不写审计：记录在事务外，审计组件只在观察到事务时落库（Webhook 投递需求遗留） | 发送后另开短事务重检权限并记录（同通知测试发送做法）；`WebhookEndpointManagementTest` 加审计断言 |
| 2 | Web 表单在前端只允许 https，导致本机 / test 已有 http 端点无法修改 | 前端只校验 URL 格式，协议与公网地址由 Platform 按配置校验 |
| 3 | 测试事件 Gateway 路由缺 `content-type`（阶段 4 已改） | 本机经 Gateway 实测通过 |

**遗留（需你决定）**

- **业务错误原因不透出**：BFF 对所有 Owner 业务错误统一返回公共说明"业务校验未通过"（BFF 既有边界，`OperationsBrowserBoundaryAutoTest` 断言），页面只能显示错误通知（code + requestId），不能在地址字段显示"指向内网"等具体原因。AC-04"错误就地显示"对业务错误未满足；字段格式错误（422）可就地显示。改变需调整 BFF 全局错误投影，建议另行决定。
- 只读账号隐藏按钮未在浏览器验证（本机无只读运营账号）；接口 403 由 Platform 自动化测试覆盖。
