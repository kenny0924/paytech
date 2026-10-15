# Platform Support 通知内部 HTTP 接口需求

> 状态：阶段 1 已确认（2026-10-15），阶段 2+3 方案见 [technique.md](./technique.md)。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

[Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md)只开放了模块内入口 `SubmitNotificationProcessor`，并约定"其他内部服务的 HTTP 接口待强服务身份合同确定后再开放，不信任自报 X-Caller-Service"。[SES 接入](../../20261014/platform-support-邮件AmazonSES接入/requirement.md)完成后，Platform 已经能真实发信，但 Payment、Commerce、CCT 等系统仍然调不到，需求目录中的 PLT-I09"请求发送通知（全部业务系统）"无法落地。

强服务身份（mTLS / Workload Identity）目前没有方案。2026-10-15 用户确认：**通知内部 HTTP 接口沿用 Vault V1 已接受的过渡信任方式先开放**，即私网部署限制 + `X-Caller-Service` 自报身份 + 静态白名单。Platform 内已有同样做法的先例：`OnboardingFileVerificationController` 只接受 `client-contract-service`。

### 1.1 决定变更

| 原决定 | 新决定 | 接受的风险 |
|---|---|---|
| HTTP 接口等强服务身份后开放，不信任自报 `X-Caller-Service` | 先开放；`X-Caller-Service` 只用于按白名单限定调用方可用的模板，部署必须把入口限制在私网 | 私网内任何服务都能冒充白名单中的调用方，用其已授权的模板向任意邮箱发信。影响限于"多发几封合法模板邮件"，不涉及读取数据或密钥 |

强服务身份到位后只替换身份识别这一层，接口合同不变。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 内部业务系统（Payment、Commerce、CCT、Finance、Risk 等） | 在本地事务提交后，经私网 HTTP 提交普通通知，拿到受理结果 |
| Platform | 只为已登记的调用方受理其被授权的模板；来源系统就是调用方服务名，不照搬请求体 |
| 运维/部署 | 在部署配置里登记调用方和可用模板，不改代码 |

## 3. 本期范围

1. **内部 HTTP 接口**：在 `platform-support-api` 新增轻量 `@HttpExchange` 合同，在 `platform-support-web` 新增 Controller，委托现有 `SubmitNotificationProcessor`。只走私网，不经过 Gateway 和 BFF，不注册公开 Route。
2. **调用方白名单**：部署配置登记"调用方服务名 → 可用模板编码"。未登记的调用方或越权模板一律拒绝，且在 Vault 保护邮箱之前就拒绝。
3. **来源系统就是调用方服务名**：`source_system` 直接保存通过白名单的 `X-Caller-Service` 值（如 `payment-orchestration-service`），不做映射，请求体不提供这个字段。`tenantId` 由调用方给出，Platform 在过渡信任下照单接受。
4. **稳定的错误合同**：参数错误、重复提交、模板/路由/账号未启用、Vault 不可用等，按全系统 RFC 9457 Problem 返回稳定的错误码。邮箱、参数值、正文不进入错误正文和日志。
5. **重复提交语义**：同一调用方的同一 `sourceEventKey` 再次提交时返回冲突（见 Q-03）。调用方超时后用原 `sourceEventKey` 重试，就能知道之前那次已经受理。
6. **测试与文档**：从 HTTP 入口跑真实链路的 auto 测试；同步 Delivery 规划文档、PLT-I09 需求目录、Platform README 和当前台账。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| 强服务身份（mTLS、Workload Identity、签名） | 后续；本期只做过渡信任 |
| 任何业务系统接入调用 | 本期只做 Platform 提供方；首个调用方另立需求 |
| 通知状态查询接口 | 见 Q-04，建议后续 |
| 认证邮件、SMS、取消/重发、Operations 管理页面 | 后续 |
| Gateway Route、Idempotency-Key | 内部调用不经过 Gateway；去重靠业务身份 `sourceEventKey` |
| 白名单存数据库、管理页面 | 本期用部署配置（见 Q-01） |
| test/生产部署 | 本期只做本地实现与验证 |

## 5. 验收标准

| 编号 | 验收标准 |
|---|---|
| AC-01 | 白名单中的调用方用被授权的模板提交，返回 `requestId` 与 `acceptedCount`；落库结果和模块内入口一致，`source_system` 等于调用方服务名 |
| AC-02 | 缺少 `X-Caller-Service`、调用方不在白名单、模板不在该调用方的授权范围：分别返回稳定错误码；不调用 Vault，不写库 |
| AC-03 | 请求体无法携带 `sourceSystem`；即使传入同名字段也被拒绝或忽略（按阶段 3 合同），不影响落库值 |
| AC-04 | 同一调用方同一 `sourceEventKey` 重复提交返回冲突错误码，不产生第二个 Request；不同调用方使用相同 `sourceEventKey` 互不冲突 |
| AC-05 | 参数校验失败返回 422 及字段错误；模板/路由/账号未启用、缺少所需语言返回稳定业务错误码；Vault 不可用返回 503 且不写库 |
| AC-06 | 错误正文、普通日志中不出现邮箱、参数值、正文；该路径的请求/响应日志只记录元数据 |
| AC-07 | `notification.enabled=false` 时接口不暴露；白名单配置不合法（重复调用方、非法编码、空模板列表）时启动失败 |
| AC-08 | 全服务 `./mvnw -o verify` 测试通过；Delivery 与新增入口相关覆盖率不低于既有水平 |
| AC-09 | Delivery 规划（HTTP 开放时机与信任方式）、PLT-I09、Platform README、当前台账同步更新，决定变更有记录 |

## 6. 问题与结论（2026-10-15 已确认）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q-01 | 调用方能用哪些模板，放在哪里登记？ | 部署配置（`notification.http.callers`），与"先配置"的做法一致；不给模板表加归属字段，不做 DDL |
| Q-02 | 来源系统编码怎么来？ | **已定**：直接用调用方服务名（如 `payment-orchestration-service`），不映射；请求体不提供 |
| Q-03 | 重复提交时返回什么？ | 保持现有语义：409 冲突加稳定错误码，不重放旧响应。调用方把它视为"已受理过" |
| Q-04 | 本期是否加状态查询接口？ | 不加。现在没有调用方需要它，等首个业务调用方的需求再定 |
| Q-05 | 本期适用哪些阶段？ | 阶段 2+3 合并；4、5 照常；6–7 不适用；8 提交需单独授权 |
