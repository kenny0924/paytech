# Platform Support 普通邮件接入 Amazon SES 交付记录

## 适用阶段（评审稿）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 本次交付，等待确认 |
| 2+3. 现状差异与方案设计 | 建议合并：只读分析已完成大部分差异，下一步直接给出方案、合同修订与测试计划 |
| 4. 研发实现 | 确认方案后进行 |
| 5. 综合验证与本地验收 | 本机 `./mvnw test`、覆盖率；manual 真实 SES 用例视 Q-03 而定 |
| 6–7. test 发布准备与部署 | 不适用：本期不部署，AWS 账号与域名由用户侧准备 |
| 8. 提交 | 需单独授权 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-14 | 选型前讨论 | 接受普通通知“至少发一次”；供应商由 Claude 推荐 | 比较供应商 |
| 2026-10-14 | 选型 | “可以，先 Amazon 接入一下” | 登记需求并交付阶段 1 |
| 2026-10-14 | 1 | “可以”：接受范围、AC-01—AC-10，Q-01—Q-04 按建议（10 分钟窗口；SES 受理即 `SENT` 完成；加 manual 真实 SES 用例；初始化 DML 不纳入），阶段 2+3 合并 | 阶段 2+3 方案设计 |
| 2026-10-14 | 2+3 | “可以”：接受 technique.md 方案 | 阶段 4 研发（Platform 三模块代码与文档，不执行 SQL、不提交） |
| 2026-10-14 | 4 | “可以”：接受阶段 4 实现 | 阶段 5 综合验证（无真实 SES 环境，manual 不执行） |
| 2026-10-15 | 变更 | 讨论密钥管理：“用 IAM 密钥”“可以直接保存到 Vault”，不建统一账号表，“那就暂时先配置吧”（Token 放部署配置） | 回到阶段 4 实施 [变更 6.1](./requirement.md)，重跑阶段 5 受影响验证 |
| 2026-10-15 | 5 → 8 | “可以，提交吧”：接受阶段 5 结果并授权本地提交 | 阶段 8 本地提交（只含本需求改动，不推送、不部署） |

## 当前阶段与下一确认点

阶段 5 已确认（含 2026-10-15 密钥管理变更），阶段 8 已本地提交（未推送）。阶段 6–7 不适用（本期不部署）；推送与部署需单独授权。实现偏差见 [technique.md 第 8 节](./technique.md)。

## 阶段 5 综合验证（2026-10-14）

| 检查 | 结果 |
|---|---|
| 全服务测试 | JDK 25 `./mvnw -o verify`：518/518 通过；全服务 90% 聚合门禁既有失败（指令 0.49、分支 0.33） |
| Delivery 覆盖率 | 指令 96.61%、分支 90.95% |
| 生产依赖 | Reactor 内 `platform-support-web` 运行期含 `sesv2`、`url-connection-client`，不含 apache/netty 客户端、test-support、Mockito |
| 制品隔离 | `platform-support-web-1.0.0-SNAPSHOT-exec.jar` 含 SES 适配器三类；不含 SesMock/MockEmailProvider/test-support |
| 默认配置 | `notification.enabled=false`、`notification.ses.enabled=false` 时不创建 AWS 客户端（配置测试） |
| 代码自查 | Handler/Service 差异核对：窗口重发与查询路径各自受 Delivery 版本 CAS 保护；支持查询的 Provider 仅“当前尝试改读最新”一处行为变化 |
| requirements-site | 128/137，9 项失败与改动前完全相同，均不涉及本需求文件 |
| manual 真实 SES | 未执行：无 AWS 账号/已验证身份 |

### 变更验证（2026-10-15，密钥存 Vault）

| 检查 | 结果 |
|---|---|
| 全服务测试 | `./mvnw -o verify`：527/527 通过；全服务门禁既有失败（0.49/0.33） |
| 通知相关 | 6 类 123 例通过；Delivery 指令 96.30%、分支 90.34% |
| AC-11 | `test_acceptedCompletesWithoutQuery`：每次发送 Reveal 一次，SES 请求以 Vault 中的 Access Key 签名 |
| AC-12 | `test_credentialUnavailableDefersWithoutAttempt`、`test_malformedCredentialDefersWithoutAttempt`（4 类）：不登记尝试、不调用 SES；日志无密钥与 Token |
| AC-13 | `test_sesErrorClassification` 新增 3 类凭据错误 → `SES_CREDENTIAL_REJECTED`（可重试） |
| 配置 | `SesEmailConfigurationTest`：Token 格式、缺 VaultClient 拒绝启动、Token 不出现在 toString |
| 待办 | Vault 策略变更清单见 [technique.md §8.1](./technique.md)，不在 Platform 范围，未执行 |

注意：单独构建 `platform-support-web`（不带 `-am`）会使用 `~/.m3` 中旧的 delivery SNAPSHOT POM、缺少 `sesv2`；部署构建须在 Platform 根目录走 Reactor，或先 install delivery。

## 验收映射（阶段 4 开发检查）

| AC | 实现 / 证据 | 状态 |
|---|---|---|
| AC-01 | `SesEmailConfiguration` + `SesEmailProperties`；`SesEmailConfigurationTest` 装配、开关与 15 类非法/缺失配置 | 通过 |
| AC-02/03 | `SesNotificationDeliveryTest.test_acceptedCompletesWithoutQuery`、`test_sentReentryDoesNotCallSes` | 通过 |
| AC-04 | `test_sesErrorClassification`（10 类）、`test_throttledRetriesOnFallbackAccount`；既有发送上限用例 | 通过 |
| AC-05 | `test_timeoutResendsOnlyAfterWindow`、`test_unknownExhaustedStopsTask`、`test_interruptedSendResendsAfterWindow`、`test_interruptedSendAtAttemptLimitStops` | 通过 |
| AC-06 | 既有 `NotificationDeliveryHandlerTest` 查询类用例及 `queryCapableUnknownAtAttemptLimitKeepsQuerying` | 通过 |
| AC-07 | 错误映射用例断言日志无 SES 原文标记、只存结果码 | 通过 |
| AC-08 | 补偿开关解耦；关闭时无 AWS Bean（配置测试）。通知开而补偿关的启动失败依赖缺少 Registrar Bean，未单独测试 | 部分 |
| AC-09 | 全服务 518/518；Delivery 分支 90.95%；全服务聚合门禁既有失败 | 通过（模块） |
| AC-10 | Delivery technique 修订、TECHNOLOGY_STACK、Platform/测试 README、当前台账 | 通过 |

## 测试证据

- 全量：JDK 25 `./mvnw -o verify`，518/518；日志 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/127a8113-989d-4dae-925f-99aec229b7de/scratchpad/platform-verify-final.log`（临时目录）。
- 覆盖率：`platform-support-service/platform-support-test/target/site/jacoco-phase-a/jacoco.xml`。
- manual：未对真实 SES 执行。

## 证据、SQL、提交

- SQL：不涉及 DDL/DML，未创建 `db/` 目录。
- 提交：2026-10-15 本地提交（未推送），只含本需求文件及三份共享文档中本需求的行。
- 实现状态见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
