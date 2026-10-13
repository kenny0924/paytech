# Platform 读取渠道账号配置决定

- Decision status: Confirmed
- Implementation status: Not started
- Deployment status: Not deployed（Vault 策略未变更）
- Decision date: 2026-10-13
- 用户决定：渠道账号材料交接采用方案 C。Payment 只传 Vault 引用 Token 与账号身份，Platform 直接向 Vault Reveal；其余按建议。
- 详细合同：[渠道账号材料交接](../../requirements/20260914/platform-support-外部API配置与执行/material-handoff.md)
- 实施与部署状态：[当前台账](../CURRENT_IMPLEMENTATION.md#platform-standard-api-flow-20261013)

## 1. 决定

在 [2026-09-21 Channel 决定](./2026-09-21-channel-account-mid-capability-queries.md)批准的 `CHANNEL_ACCOUNT_CONFIG_V1` 专项例外内，新增一条 Reveal 授权：

| Caller | Action | Data Type | Purpose |
|---|---|---|---|
| `platform-support-service` | REVEAL | `CHANNEL_ACCOUNT_CONFIG_V1` | `CHANNEL_EXECUTION` |

- Platform 仅在标准 API 执行的初始化函数 `CHANNEL_ACCOUNT_CONFIG` 中使用该授权，在数据库事务外读取，结果只存在于本次调用内存。
- Payment 继续负责冻结 Channel/Account/MID 与用途的业务校验，再把 `channelCode`、`channelAccountId`、`entityNo`、`accountVaultToken` 传给 Platform。Platform 核对还原内容与传入身份一致，用于拒绝错绑 Token。
- `credentials` 在 Platform 内只以受控句柄交给注册函数，保存配置时拒绝其他用法。
- 沿用 `CHANNEL_EXECUTION` 用途，不新增用途。

## 2. 不变的边界

- Payment 仍是渠道账号与 Vault 引用的 Owner；Platform 不保存 Token 或明文，不读 Payment 数据库。
- Payment 现有 Reveal 权限与 `getChannelExecutionConfig` 暂时保留，接入 Platform 后再评估回收。
- 不扩大卡数据、登录密码、KMS/Vault 根密钥及其他系统 Secret 的边界；Vault 不解析业务含义，不增加业务表。
- 请求体中的引用 Token 会进入现行完整 Body 日志，归入用户决定暂缓的敏感日志统一处理，作为已知遗留风险。

## 3. 精确取代范围

- 补充 2026-09-21 Channel 决定第 2 节“Caller 为 payment-orchestration-service”：Caller 增加 `platform-support-service`，仅限本决定的用途。
- 落实 [2026-09-21 Platform Integration 决定](./2026-09-21-platform-integration-http-execution.md)第 2 节“新增跨进程运行材料交接需要单独明确，不能从 HTTP 执行归属推导新的 Reveal 权限”的要求：本决定即为该单独明确。
- [Vault 架构](../VAULT_SERVICE_ARCHITECTURE.md)顶部的 Payment 账号专项例外按本决定扩展 Caller。

## 4. 后续实施

Vault 策略合并部署、Platform 初始化函数实现、Payment “只校验不 Reveal”查询与调用改造，分别在获准的实施阶段进行；策略片段见需求目录 [vault-policy-fragment.json](../../requirements/20260914/platform-support-外部API配置与执行/vault-policy-fragment.json)。
