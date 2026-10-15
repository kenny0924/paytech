# Platform Support 通知内部 HTTP 接口交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 合并；已确认 |
| 4. 研发实现 | 已确认；不涉及 DDL/DML |
| 5. 综合验证与本地验收 | 已确认 |
| 6–7. test 发布准备与部署 | 不适用：本期不部署 |
| 8. 提交 | 2026-10-15 已本地提交（未推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | 真实 SES 测试先不管；"可以，走 HTTP 接口"：接受沿用 Vault V1 的过渡信任方式（私网 + `X-Caller-Service` + 静态白名单），改变原"等强服务身份"的决定 | 登记需求并交付阶段 1 |
| 2026-10-15 | 1 | "是不是不用映射？payment-orchestration-service 就好了，其余的按照你的来"：Q-02 改为直接用服务名作来源系统，其余按建议 | 阶段 2+3 方案设计 |
| 2026-10-15 | 2+3 | "按照你的来"：接受 technique.md 方案，并加请求体 1 MB 上限 | 阶段 4 研发（Platform 代码、配置与文档；不执行 SQL、不提交） |
| 2026-10-15 | 4 | "可以"：接受阶段 4 实现 | 阶段 5 综合验证（依赖与制品检查、测试复核；不提交、不部署） |
| 2026-10-15 | 5 → 8 | "可以，提交吧"：接受阶段 5 结果并授权本地提交 | 阶段 8 本地提交（只含本需求改动，不推送、不部署） |

## 当前阶段与下一确认点

阶段 5 已确认，阶段 8 已本地提交（未推送）。推送与部署需单独授权。实现偏差见 [technique.md 第 9 节](./technique.md)。

## 证据、SQL、提交

- SQL：不涉及 DDL/DML；表 `t_notification_request` 的唯一键 `uk_notify_source (source_system, source_event_key)` 已能区分不同调用方。
- 提交：2026-10-15 本地提交（未推送），只含本需求文件及共享文档中本需求的部分。

## 阶段 5 综合验证（2026-10-15）

| 检查 | 结果 |
|---|---|
| 全服务测试 | 阶段 4 最终一轮 JDK 25 `./mvnw -o verify` 543/543；阶段 5 未改代码，沿用该结果 |
| Reactor 打包 | `./mvnw -o package -DskipTests` 成功 |
| exec jar 内容 | 含 `NotificationController`、`NotificationCallerProperties`、`NotificationRequestConfiguration`；内嵌 delivery JAR 的 `sourceSystem` 校验为服务名格式；`application.yml` 含 `/notifications` 日志前缀与 `notification.http.callers: {}`；无测试 Mock 类 |
| API JAR | 含 `NotificationCommandApi` 及两个 Body；依赖树未新增依赖（common-core、spring-web、reactor-core、Jackson、validation-api），对现有消费方是纯新增 |
| 需求站点 | 128/137，9 项失败与改动前相同 |
| 未做 | 无真实调用方联调；未部署；manual 不适用 |

## 验收映射（阶段 4 开发检查）

| AC | 实现 / 证据 | 状态 |
|---|---|---|
| AC-01 | `NotificationController` → `SubmitNotificationProcessor`；`test_normalFlow`、`test_platformNotificationWithoutTenant`：200，`source_system` 为调用方服务名，补偿发送完成 | 通过 |
| AC-02 | `NotificationCallerProperties.authorize`；`test_missingCaller`（400 `INVALID_REQUEST`）、`test_callerOrTemplateNotAllowed`（400 `BUSINESS_CHECK_FAILED`），均无 Vault 调用、无落库 | 通过 |
| AC-03 | `test_sourceSystemInBodyIgnored`：Body 带 `sourceSystem` 被忽略 | 通过 |
| AC-04 | `test_duplicateSourceEventKey`：同调用方 409 `CONCURRENT_REQUEST`，另一调用方同 key 200 | 通过 |
| AC-05 | `test_invalidBody`（422 `VALIDATION_ERROR` + `errors[]`、租户越界 400）、`test_bodyTooLarge`（413）、`test_templateDisabledOrVaultUnavailable`（400；Vault 不可达 503 `DEPENDENCY_UNAVAILABLE` 且无落库） | 通过 |
| AC-06 | 每次调用断言响应不含邮箱与参数值；`CapturedOutput` 断言日志不含；`/notifications` 只记元数据 | 通过 |
| AC-07 | `NotificationCallerPropertiesTest`：非法服务名/模板/重复/空列表/空值启动失败；通知关闭时无 Controller、白名单与 Body 配置 Bean | 通过 |
| AC-08 | 全服务 543/543；Delivery 指令 96.30%、分支 90.34%（与改动前持平）；全服务聚合门禁既有失败 | 通过（模块） |
| AC-09 | Delivery 规划 requirement/api/technique 修订说明、Platform README、测试 README、当前台账；PLT-I09 描述与实现一致，未改 | 通过 |

## 测试证据

- 全量：JDK 25 `./mvnw -o verify`，543/543；日志 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/127a8113-989d-4dae-925f-99aec229b7de/scratchpad/platform-verify-http.log`（临时目录）。
- `npm --prefix requirements-site test`：128/137，9 项失败与改动前相同（Checkout、Commerce、Payment 退款、规范文字断言等），与本需求无关。
