# Platform Support Client Webhook 投递交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 合并；已确认 |
| 4. 研发实现 | 已确认 |
| 5. 综合验证与本地验收 | 已确认 |
| 6–7. test 发布准备与部署 | 2026-10-16 用户要求部署 test；阶段 6 评审中（[发布清单](./release/release.md)） |
| 8. 提交 | 已本地提交 `ee469e0`（未推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 讨论 | “URL 不需要存 vault token；入口先做 A；密钥存储按建议；轮换宽限 1 小时；其余你定” | 登记需求并交付阶段 1 评审稿 |
| 2026-10-15 | 1 | 讨论与 Notification 的关系后“可以，继续吧”：接受需求、范围、验收标准与阶段安排；两者无代码复用与数据交叉，失败告警/轮换提醒邮件本期不做 | 阶段 2+3 现状差异与方案设计（只写文档，不改代码、不执行 SQL） |
| 2026-10-15 | 2+3 | “继续”：接受 technique.md 方案（5 表、10 接口、补偿等比退避、Commerce 改同步 HTTP） | 阶段 4 研发（Platform 代码、模型、SQL 文件、文档；本地测试库执行 SQL 另行授权；不提交） |
| 2026-10-15 | 4 | “没问题”：接受阶段 4 实现、偏差与验证结果 | 阶段 5 综合验证与本地验收（不提交、不部署） |
| 2026-10-16 | 5 | “确认”：接受阶段 5 验证结果 | 阶段 8 提交需单独授权 |
| 2026-10-16 | 8 | “提交”：授权本地提交，代补的通知清单与模型一并提交 | 已本地提交 `ee469e0`；推送、部署需另行授权 |
| 2026-10-16 | 6 | “部署test吧”：要求部署 mor-test | 阶段 6 发布清单评审（只读核对，未改环境） |
| 2026-10-16 | 7 | “可以”：D1=B 临时接收端，D2 合成租户 | 已部署 mor-test，验收 24/24，临时放行与验收数据已清理；见[发布记录](./release/release.md#6-执行记录2026-10-16-阶段-7用户可以d1b-临时接收端d2-合成租户) |

## 当前阶段与下一确认点

已本地提交 `ee469e0`（未推送）；2026-10-16 已部署 mor-test（镜像 `63ae4cde…`，验收 24/24）。mor-test 未配置 XXL-Job 补偿扫描，进入 RETRY_WAIT 的投递不会自动重试。Commerce 生产端、Operations 页面与 Gateway 路由未实现。偏差见 [technique.md 第 9 节](./technique.md#9-实现偏差阶段-4)。

## 证据、SQL、提交

- **本机 SQL（2026-10-15，用户授权“可以，继续”）**：在 `mor-local-mysql-1` 的 `mor_platform` 执行 `platform-support-service/docs/database/sql/changes/2026-10-15-webhook-delivery.sql`，执行前确认 5 张表均不存在，执行后核对 5 张表、56 个字段。权限注册 DML 未在任何库执行（测试自行插入）。其他环境未执行。
- **协调记录**：应用户要求，代“通知发送账号维护”会话把 `t_notification_provider` 的 6 个字段补进 PDManer 清单并重新生成；`provider_type` 注释按生成规则补“取值来源…非固定枚举”，同步到该需求的 4 个 SQL 文件（建表 SQL 尚未执行，只改注释）。
- **并行测试**：本地 `mor_platform` 被另一会话的 Platform 测试同时使用时会互相清理数据，验证改为等对方结束后串行运行。
- **阶段 4 验证（2026-10-15，本机）：** `./mvnw -o verify` 测试 645/645 通过（Webhook 26 例）；全服务聚合覆盖率门禁为既有未达标（Phase A 指令 58%、分支 39%），Webhook 相关类指令 91.62%、行 90.29%、分支 66.84%。PDManer/Schema/场景测试 52/54（2 项既有失败）。`npm --prefix requirements-site test` 127/137，10 项失败均为既有或其他会话的台账/Commerce/规范文字断言，与本需求文件无关。

## 阶段 5：验收标准对照

| AC | 证据 |
|---|---|
| AC-01 受理、按订阅分发、无订阅也受理 | `WebhookEventApiTest.test_normalFlow`、`test_noSubscription` |
| AC-02 重复与冲突 | `WebhookEventApiTest.test_duplicateSameContent`、`test_duplicateDifferentContent`、`test_sameEventIdFromAnotherCaller` |
| AC-03 调用方、Tenant、Envelope、摘要拒绝且不写库 | `WebhookEventApiTest.test_callerNotAllowed`、`test_invalidParameter`、`test_noDataFound` |
| AC-04 Body 为规范字节、`t` 为发送时刻、签名可验 | `WebhookEventApiTest.test_normalFlow`（测试侧独立 HMAC 验签） |
| AC-05 非 2xx/3xx/超时/断连重试、上限后 DEAD、每次有尝试记录 | `WebhookDeliveryHandlerTest.test_retryUntilDead`、`test_retryThenSucceed`、`test_timeout` |
| AC-06 并发与中断恢复 | `WebhookDeliveryHandlerTest.test_concurrentExecution`（两个执行者同时推进只发一次）、`test_interruptedRecovery`、`test_invalidTaskIdentity` |
| AC-07 轮换双签宽限、立即作废、明文只出现一次 | `WebhookEndpointManagementTest.test_rotateAndTestEndpoint`、`test_normalFlow`；`WebhookDeliveryHandlerTest.test_graceSecretDualSignature`、`test_expiredGraceSecret` |
| AC-08 非公网地址保存与发送均拒绝、不跟随重定向 | `WebhookEndpointManagementTest.test_invalidParameter`、`test_businessRuleViolation`；`WebhookDeliveryHandlerTest.test_blockedAddress`、`test_retryUntilDead`（302 记失败） |
| AC-09 Replay 与停用取消 | `WebhookDeliveryManagementTest.test_normalFlow`、`test_businessRuleViolation`；`WebhookDeliveryHandlerTest.test_disabledEndpointCancels` |
| AC-10 测试事件不落库 | `WebhookEndpointManagementTest.test_rotateAndTestEndpoint` |
| AC-11 事务内无外部调用；密钥不进日志/错误/补偿 Payload | 代码核对（Vault/DNS/HTTP 均在事务外）；管理接口前缀 `/webhook-admin` 已加入正式与测试配置的 `web.logging.metadata-only-path-prefixes`（测试控制台日志不输出结构化字段，无法用输出断言，靠配置核对）；补偿事件只含投递 ID（`buildEvent`）；`WebhookDeliveryHandlerTest.test_secretUnavailable`（Vault 不可用不外发） |
| AC-12 模型/SQL/映射一致，verify 通过 | PDManer/Schema/场景测试；全量 `./mvnw -o verify`（结果见下） |
| AC-13 决定与文档同步 | 决定记录、Platform 数据库与场景文档、Vault/Commerce/物理决定取代说明、README、测试 README、台账 |

## 阶段 5 结果（2026-10-16，本机）

- 全量 `./mvnw -o verify`：测试 **647/647** 通过（Webhook 28 例，新增并发推进与非法任务身份 2 例）；全服务聚合 90% 覆盖率门禁仍为既有未达标（Phase A 指令 58%、分支 39%）。Webhook 相关 119 个类：指令 91.91%、行 90.78%、分支 67.63%。
- 本阶段修正：测试字面量改为引用生产枚举与常量（Handler 两个失败代码改为公开常量）；测试配置补 `/webhook-admin` 元数据日志前缀；有界转换器方法补 JavaDoc。
- 运行时制品：`platform-support-web` 依赖树含 `httpclient5 5.5.2` 与 `paytech-json-canonicalization`；生产模块不含测试 Mock 或测试分支。
- 日志边界：测试控制台日志不输出结构化字段，无法用输出断言验证，改为核对正式与测试配置均含 `/webhook-admin` 前缀。
- 并行测试：一次单类重跑时另一会话的 Platform 测试同时在运行，可能影响了对方该轮结果；全量 verify 前确认无其他测试进程后串行执行。
