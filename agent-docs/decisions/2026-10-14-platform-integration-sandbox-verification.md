# 渠道沙箱验证证据与验证账号决定

- Decision status: Confirmed
- Implementation status: In progress（数据库设计阶段）
- Deployment status: Not deployed
- Decision date: 2026-10-14
- 用户决定：在 Operations 页面上对“接入方 × 标准 API”做沙箱实测并保存验证结果；证据保存脱敏后的渠道报文；验证用的沙箱密钥不是敏感信息，直接存 Platform 数据库、不写入 Vault；技术方案评审点 S1～S8 按建议。
- 详细设计：[渠道沙箱验证技术方案](../../requirements/20261014/platform-support-渠道沙箱验证/technique.md)
- 实施与部署状态：[当前台账](../CURRENT_IMPLEMENTATION.md#channel-sandbox-verification-20261014)

## 1. 决定

1. **验证运行可以保存报文与轨迹（对 [2026-10-13 标准 API 与流程决定](./2026-10-13-platform-standard-api-flow.md)“不持久化报文与运行上下文”的窄例外）：** 只有 Operations 发起的沙箱验证运行在 `t_integration_verify_run_step` 保存实际输入、结果、期望核对、逐节点轨迹与脱敏后的渠道请求/响应原文。认证类 Header 与值来自受控句柄的 Header 记为 `[REDACTED]`，签名类 Header 保留，单段报文最多 64 KiB。运行记录不修改、不删除。
2. **验证账号由 Platform 保存（对 [2026-10-13 Platform 读取渠道账号决定](./2026-10-13-platform-channel-account-reveal.md)“Platform 不保存 Token 或明文”的窄例外）：** `t_integration_verify_account` 明文保存沙箱账号参数与凭据，不写入 Vault。执行时凭据仍包装为受控句柄，只能交给注册函数。
3. **使用边界：** 验证账号只供验证运行使用，正式执行接口仍只从 Vault 读取账号；验证运行只能对接入方中标记为 `sandbox: true` 的出站地址发起请求。

## 2. 不变的边界

- 正式执行接口 `POST /standard-apis/v1/{code}/execute` 的请求、响应、Problem 与“不持久化报文”的行为不变；验证与正式执行共用同一套执行主体。
- 生产渠道账号仍由 Payment 持有，Vault 引用交接方式不变；不得把生产凭据录入验证账号。
- 沙箱标记依赖人工维护；误标生产地址会产生真实交易，页面需二次确认并展示目标地址。
- 账号接口的请求与响应进入现行完整 Body 日志（W5 遗留），按本决定第 2 条视为非敏感沙箱材料。
