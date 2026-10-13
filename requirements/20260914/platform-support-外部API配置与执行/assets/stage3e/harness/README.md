# 外部 API 第 3 步 3e 本机端到端

仅手工验证使用，不进入生产应用。配置经真实入口（`web.mjs` 代理 → Gateway → BFF → Platform）保存；执行直连 Platform 内部接口 `POST /platform-support/standard-apis/v1/{code}/execute`（该接口不经 Gateway）。

- **数据库：** 沿用 2e 的本机独立库 `mor_platform_external_api`，不触碰测试同用的 `mor_platform`。
- **Platform：** 本目录 `PlatformE2e` 在 2e 启动器基础上，为三个合成支付尝试在测试装配的 Vault Mock 中登记 `CHANNEL_EXECUTION` 渠道账号；API Key 每次随机生成，只写入 0600 运行文件。测试 mock 配置允许出站 http。
- **渠道：** `channel.py` 是本机合成渠道：校验 HMAC 签名，按订单号末位返回 CAPTURED / DECLINED / 504，查询接口返回 CAPTURED；日志只记方法、路径、状态与验签结果。
- **BFF / Gateway / Web：** 沿用 [2e 启动器](../../stage2e/harness/)。

步骤（仓库根目录，载入本机私有环境但不输出内容）：

1. `E2E_RUNTIME=<私有目录> node ../../stage2e/harness/web.mjs`（已有 Web 入口可复用）。
2. 依次 `python3 start.py platform`、`channel`、`bff`、`gateway`，每次等待对应 runtime JSON 生成。
3. `python3 verify.py`：经真实入口保存 PAY 实现（含补查分支），再执行成功、拒绝、补查三条路径及前置 Problem。
4. 结束时停止各进程。

结果见 [e2e-receipt.json](../e2e-receipt.json)。
