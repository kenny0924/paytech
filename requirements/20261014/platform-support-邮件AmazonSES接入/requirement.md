# Platform Support 普通邮件接入 Amazon SES 需求

> 状态：阶段 1 评审稿（2026-10-14）。确认记录见 [delivery.md](./delivery.md)；技术方案在阶段 2/3 写入 [technique.md](./technique.md)。

## 1. 背景与来源

[Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md)已实现普通 Email 的受理、投递状态机、补偿执行和测试 Mock，但生产代码没有任何 `EmailProvider` 实现，`notification.enabled=true` 时所有发送都会返回 503，也就是失败关闭。现行执行合同还要求 Provider 支持“按原请求号查询结果”，主流邮件服务和 SMTP 都满足不了这一点。

2026-10-14 用户确认了两项决定：

1. 普通通知接受“至少发一次”：结果无法确认时允许重发，偶尔会产生重复邮件。
2. 首个真实供应商选 **Amazon SES**（API v2），部署区域优先 `me-central-1`（阿联酋）。

选型依据：成本最低，有中东区域，日志和邮件元数据可以留在区域内，合规资质齐全；投递事件后续可以经 SNS/EventBridge 接入。Resend 支持幂等键，留作以后的备用 Provider 候选，本期不接入。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Platform 内部模块调用方 | 通过现有 `SubmitNotificationProcessor` 提交普通通知后，邮件确实经 SES 发出 |
| 运维/部署 | 只靠部署配置（区域、凭据、发件人）就能启用 SES，不改代码 |
| 收件人 | 收到发件人身份正确的纯文本邮件；偶尔重复可以接受，不能静默丢失 |

## 3. 本期范围

1. **SES 适配器**：在 `platform-support-delivery` 的外部集成层新增 `EmailProvider` 的 SES 实现，使用 AWS SDK for Java v2（`sesv2`，版本跟仓库现有的 `awssdk` BOM 一致）。每个收件人发一封纯文本邮件，发件人、区域、可选的 Configuration Set 都按 `provider_code` 从部署配置读取。
2. **执行语义修订（至少发一次）**：
   - Provider 声明自己是否支持结果查询。SES 声明为不支持。
   - 发送被 SES 明确受理（返回 MessageId）时，投递直接记为 `SENT`，作为本期的完成态，不再无限轮询“是否送达”。
   - 结果不确定（`UNKNOWN`）时，如果 Provider 不支持查询，等待安全窗口后允许新建一次发送，计入 `maximum-send-attempts`，并沿用现有主备规则。
   - SES 返回的明确错误按错误类型映射为可重试拒绝、永久拒绝或未知。
3. **启用开关解耦**：通知要能独立开启补偿执行，不再依赖 `files.onboarding.enabled`。
4. **配置与凭据**：生产用 AWS 默认凭据链（IAM 角色），本机或测试允许成对的静态凭据；凭据不进日志、不进数据库、不进版本库。
5. **测试**：按仓库测试规范，用测试 HTTP 端点返回 SES API 响应，经真实的 SDK Client 调用；覆盖受理、各类拒绝、超时/未知、等待后重发、主备切换和意外调用。
6. **文档同步**：修订 Delivery 执行合同、`TECHNOLOGY_STACK.md`（新增 `sesv2` 模块）、Platform README 与测试说明。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| SES 投递/退信/投诉事件回调（SNS、EventBridge） | 后续需求；本期 `SENT` 就是完成态，不产生 `DELIVERED` |
| 认证邮件迁移 | 不迁。认证邮件带一次性秘密，继续由现有 SMTP 链路在提交后直发，不持久化秘密 |
| 正式模板、语言、账号、路由的初始化 DML | 需要业务配置审批（原需求 Q-06），本期只在测试里创建 Fixture；是否纳入见 Q-04 |
| 首个业务调用方、内部 HTTP 入口 | 继续等强服务身份合同 |
| Resend 等第二供应商、SMS | 后续 |
| 取消、人工重试、Operations 管理 | 后续 |
| AWS 账号、域名 SPF/DKIM/DMARC、解除 SES 沙箱 | 由用户侧准备；本期代码不依赖它们，只作为上线前提记录 |
| test/生产部署 | 本期只做本地实现与验证，阶段 6–7 不适用（见 delivery.md） |

## 5. 验收标准

| 编号 | 验收标准 |
|---|---|
| AC-01 | `notification.enabled=true` 并配置一个 SES 账号时，应用能装配出唯一的 SES 适配器；`provider_code` 对应的配置缺失或无效时启动失败，不回退成 Mock |
| AC-02 | 一个收件人提交后经补偿执行调用 SES `SendEmail`：发件人、收件人、主题和正文正确，只有一个收件人；SES 返回 MessageId 后 Attempt 为 `ACCEPTED`、Delivery 为 `SENT`，并保存 MessageId |
| AC-03 | `SENT` 投递在补偿重入时不再调用 SES，任务按成功结束 |
| AC-04 | 节流、服务端 5xx 这类可重试错误，以及发件人未验证、收件地址被拒、账号暂停这类永久错误，分别映射为 `RETRYABLE_REJECTED` / `REJECTED`；重试上限、等待时间和主备切换按现有规则执行 |
| AC-05 | 连接超时、读超时或响应无法解析时记为 `UNKNOWN`；安全窗口内不重发，过了窗口新建 Attempt 重发，`attempt_count` 加 1，总次数不超过上限 |
| AC-06 | 支持查询的 Provider（测试 Mock）保持原行为：`UNKNOWN` 只查询原请求，不重发，不切备用 |
| AC-07 | 邮箱、正文、凭据和 SES 原始错误文本不进入普通日志、异常消息、补偿 Payload 或数据库；只保存有界结果码和 MessageId |
| AC-08 | 通知启用不再依赖文件入驻开关；两个开关都关闭时不创建 AWS Client |
| AC-09 | 测试只经测试 HTTP 端点模拟 SES，不连真实 AWS；有意外调用即失败，每个用例结束后清理；全服务 `./mvnw test` 通过，Delivery 模块分支覆盖率达到 90% |
| AC-10 | Delivery 执行合同、技术栈文档、README、测试说明与当前台账同步，写明“至少发一次”语义和本期边界 |

## 6. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 结果不确定后的安全窗口多长 | **10 分钟**：SDK 超时后 SES 端通常早已结束处理；窗口太短重复率高，太长延迟大 |
| Q-02 | SES 受理是否作为本期完成态（`SENT` 不再等送达） | **是**；送达和退信等事件回调需求再说 |
| Q-03 | 是否增加 `manual` 测试，用真实 SES 沙箱发一封邮件 | **加一个 manual 用例**，默认不跑，你有 AWS 账号和已验证地址时手动执行 |
| Q-04 | 正式模板/账号/路由初始化 DML 是否纳入本期 | **不纳入**，等首个业务调用方确定时一起审批 |

## 6.1 变更记录（2026-10-15）

用户决定：SES 使用 **IAM 用户密钥**，密钥对保存在 Vault，Platform 只在部署配置 `notification.ses.credential-token` 中保存 Vault Token；暂不新增统一的非支付外部账号表，也不改 `t_notification_provider`。由此：

- 删除 `notification.ses.access-key` / `secret-key`；Token 为空时走 AWS 默认凭据链（本机与 manual 用 `AWS_PROFILE` 等）。
- 新增 Vault 数据类型 `NOTIFICATION_PROVIDER_CREDENTIAL_V1`（密钥类、REFERENCE），值为 `{"accessKeyId","secretAccessKey"}`；策略 `platform-support-service` REVEAL / `NOTIFICATION_DELIVERY`，运营手动 Protect / `NOTIFICATION_PROVIDER_SETUP`。Vault 策略变更不在 Platform 范围，由用户或 Vault 负责方执行。
- 每次发送前在事务外 Reveal，不缓存（VaultClient 合同禁止缓存）；Reveal 失败不登记发送尝试。
- SES 凭据错误（如 `UnrecognizedClientException`、`InvalidClientTokenId`、`SignatureDoesNotMatch`、`AccessDeniedException`）改按可重试处理，避免换密钥期间把投递判为永久失败。
- 换密钥：AWS 新建密钥 → Protect 得新 Token → 更新配置并重启 → 确认后在 AWS 停用旧密钥。

新增验收：
| 编号 | 验收标准 |
|---|---|
| AC-11 | 配置 Token 时每次 SES 发送前以 `NOTIFICATION_DELIVERY` Reveal 一次，SES 请求使用 Vault 中的 Access Key 签名，不回退到默认凭据链 |
| AC-12 | Vault 不可用或值格式错误时不创建发送尝试、不调用 SES，任务稍后重试；凭据与 Token 不进入日志或数据库 |
| AC-13 | SES 凭据类错误映射为 `SES_CREDENTIAL_REJECTED`（可重试） |

后续事项：出现非通知、非支付外部服务的正式账号需求时，再评估 Integration 模块的统一账号表。

## 7. 依据

- 现行领域需求与合同：[requirement](../../20260920/platform-support-Delivery领域规划/requirement.md)、[technique](../../20260920/platform-support-Delivery领域规划/technique.md)；本需求批准后，替换 technique 第 6 节和第 8.2 节中“UNKNOWN 只能查询、不能重发”的规则，仅限声明为不支持查询的 Provider。
- 技术栈：[TECHNOLOGY_STACK.md](../../../agent-docs/TECHNOLOGY_STACK.md)：AWS SDK 只能出现在外部集成适配器。
- 外部 API 执行引擎（[2026-09-21 决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)）管的是业务系统委托 Platform 调用支付渠道等外部 API；邮件 Provider 是 Platform 自己的出站依赖，不走该引擎。
