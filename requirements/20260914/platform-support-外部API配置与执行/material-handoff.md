# 渠道账号材料交接

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。用户选择方案 C，其余按建议；授权扩大见[决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)。Vault 策略未部署，Payment 与 Platform 源码未修改。
- 覆盖待决项：[技术说明第 6 节](./technique.md#6-待决事项)第 5 项。
- 不覆盖：原始卡数据。需要卡原值的渠道仍按 [Card 专项](../../20260912/payment-自建CardVault/requirement.md)与[交易级工具修订](../../20260913/payment-Execution设计/instrument-token-amendment.md)另行设计。

## 1. 现状

| 事实 | 来源 |
|---|---|
| 渠道账号参数与 API 凭据整体存入 Vault，数据类型 `CHANNEL_ACCOUNT_CONFIG_V1`，Token 模式 `REFERENCE` | [账号与 MID 查询实施合同第 4 节](../../20260921/payment-渠道账号与MID能力查询/implementation-contract.md#4-vault-账号配置) |
| Payment `t_channel_account.account_vault_token` 只保存引用 Token | 同上 |
| 还原后结构：`channelCode`、`channelAccountId`、`entityNo`、`parameters`（字符串键值）、`credentials`（字符串键值） | [账号配置 Schema](../../20260921/payment-渠道账号与MID能力查询/channel-account-config.schema.json) |
| 目前只有 `payment-orchestration-service` 可按 `CHANNEL_EXECUTION` 用途 Reveal | [Vault 策略片段](../../20260921/payment-渠道账号与MID能力查询/vault-policy-fragment.json) |
| `GetChannelExecutionConfigProcessor` 按冻结的 Channel/Account/MID 与用途（新支付、原交易、恢复）校验状态，再 Reveal 并返回明文配置 | [需求第 4.3 节](../../20260921/payment-渠道账号与MID能力查询/requirement.md#43-getchannelexecutionconfig) |
| 2026-09-21 决定：Platform 执行不自动获得新的 Reveal 权限，材料交接须单独明确 | [Platform 执行修订第 3 节](./platform-http-execution-amendment.md#3-支付事实与运行材料) |

现在执行方是 Platform，凭据必须以某种方式进入 Platform 进程。

## 2. 方案比较

| 方案 | 做法 | 凭据经过的路径 | 问题 |
|---|---|---|---|
| A | Payment Reveal 后，把明文凭据放进标准 API 请求 `input` | Vault → Payment → **内部 HTTP 请求体** → Platform | 明文凭据出现在请求体中；现行 MVC 日志完整记录 Body（W5 暂缓处理），凭据会进入 Platform 日志 |
| B | Platform 初始化时回调 Payment 新接口取明文配置 | Vault → Payment → **内部 HTTP 响应体** → Platform | 同 A 的日志问题；另外 Payment 正同步等待 Platform，形成 Payment → Platform → Payment 循环 |
| **C（建议）** | Payment 校验冻结账号后，只把 Vault 引用 Token 与账号身份传给 Platform；Platform 初始化函数直接向 Vault Reveal | Vault → **Platform 内存** | 需要给 Platform 新增 Vault Reveal 权限；Token 本身会出现在请求体日志中 |

C 让明文凭据只出现在 Vault 到 Platform 这一跳，与 Payment 现在的读取方式相同。代价是扩大 Vault 授权，用户已确认（M1）。

## 3. 方案 C 的交接流程

```text
Payment Execution（事务外）
  1. 校验冻结的 Channel/Account/MID 与用途（沿用 getChannelExecutionConfig 的校验规则，但不 Reveal）
  2. 得到 channelCode、channelAccountId、entityNo、accountVaultToken
  3. 调用 Platform 标准 API，input 中带上述四项
        ↓
Platform 标准 API 执行
  4. 初始化项调用注册函数 CHANNEL_ACCOUNT_CONFIG
  5. 函数以 Caller=platform-support-service、Purpose=CHANNEL_EXECUTION 向 Vault Reveal
  6. 校验还原结果的 channelCode/channelAccountId/entityNo 与 input 一致，不一致即失败
  7. 结果写入 resources.<id>：parameters 为普通值，credentials 为受控句柄
  8. 流程节点通过 params 把受控句柄交给脚本中的注册函数（签名、认证 Header 等）
```

- 账号状态、冻结关系与用途合法性仍由 Payment 判断；Platform 只核对 Vault 内容与传入身份一致，用于拒绝错绑 Token，不代替 Payment 的业务校验。
- 第 4～6 步任何失败都是必需初始化失败，返回 503 Problem `INTEGRATION_INITIALIZATION_FAILED`，保证没有发出渠道请求（[内部接口第 3.2 节](./internal-api.md#32-problem没有发出任何渠道请求)）。
- 不缓存 Reveal 结果；每次调用都重新读取，配置修改对下一次调用生效。

## 4. 注册函数 CHANNEL_ACCOUNT_CONFIG（拟定）

| 项 | 内容 |
|---|---|
| 参数 | `vaultToken`（STRING）、`channelCode`、`channelAccountId`、`entityNo`，全部必填 |
| 返回 | `parameters`：键值均为 STRING；`credentials`：键为 STRING，值为 `PROTECTED_HANDLE` |
| Vault 调用 | Caller `platform-support-service`，Purpose `CHANNEL_EXECUTION`，数据类型 `CHANNEL_ACCOUNT_CONFIG_V1`；在数据库事务外执行 |
| 失败 | Vault 拒绝、Token 失效、结构错误、身份不一致、超时：函数失败，不返回部分结果 |

流程示例：

```json
{
  "id": "channelAccount",
  "function": "CHANNEL_ACCOUNT_CONFIG",
  "args": {
    "vaultToken": {"kind":"PATH","path":"input.channelAccount.accountVaultToken"},
    "channelCode": {"kind":"PATH","path":"input.channelAccount.channelCode"},
    "channelAccountId": {"kind":"PATH","path":"input.channelAccount.channelAccountId"},
    "entityNo": {"kind":"PATH","path":"input.channelAccount.entityNo"}
  },
  "required": true
}
```

节点使用：`"params": {"signingKey": {"kind":"PATH","path":"resources.channelAccount.credentials.apiKey"}}`；`outletRef` 等普通参数直接用 `resources.channelAccount.parameters.outletRef` 赋给请求字段。

## 5. 凭据在 Platform 内的边界

- `credentials` 的值只以受控句柄存在。保存流程与原子 API 时静态拒绝：把 `resources.*.credentials.*` 用于请求字段、`fixedValues`、连线条件、表达式、节点输出或出口输出；只允许绑定到类型为 `PROTECTED_HANDLE` 的原子 API `params`，再交给脚本中的注册函数。
- 需要把凭据放进报文（如 `Authorization`）时，由注册函数在 REQUEST 脚本中写入 Header；脚本只能传递句柄，不能读取明文。
- 句柄与明文只存在于本次调用内存；对象不实现输出明文的 `toString`，异常信息只用固定文字；调用结束即丢弃，不写数据库、缓存、MQ。
- 发往渠道的请求本身含凭据（例如认证 Header）。Platform 自有日志不记录请求 Header 与 Body 原文，沿用[内部接口第 6 节](./internal-api.md#6-日志与数据处理)。

## 6. 评审点与确认记录

2026-10-13 用户确认：M1 采用方案 C，其余按建议。策略片段见 [vault-policy-fragment.json](./vault-policy-fragment.json)，供环境维护者合并审查，不是完整策略文件。

| 编号 | 问题 | 结论 |
|---|---|---|
| M1 | 采用哪个交接方案 | **方案 C**：Payment 只传 Vault 引用 Token 与账号身份，Platform 直接向 Vault Reveal。这需要在 Vault 策略中新增 `platform-support-service` 对 `CHANNEL_ACCOUNT_CONFIG_V1` 的 Reveal 权限（用途 `CHANNEL_EXECUTION`）。确认后补一份决定记录，说明这次授权扩大 |
| M2 | Payment 需要怎样改 | 新增一个“只校验、不 Reveal”的执行配置查询，返回四项身份与 Token；现有会 Reveal 的 `getChannelExecutionConfig` 及 Payment 的 Reveal 权限暂时保留，等 Payment 接入 Platform 后再评估回收。本期只记录改造需求，不改源码 |
| M3 | 引用 Token 会随请求体进入 MVC 完整 Body 日志 | 归入已决定暂缓的敏感日志统一处理（W5），作为已知遗留风险。Token 单独不能读取明文（Vault 还会校验 Caller 与 Purpose），风险低于明文凭据 |
| M4 | Vault 读取用途 | 沿用 `CHANNEL_EXECUTION`，含义相同（执行渠道调用），不新增用途 |
| M5 | 凭据使用限制 | 按第 5 节：凭据只能以受控句柄交给注册函数，静态校验拒绝其他用法 |
