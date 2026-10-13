# 外部 API 第 2 步 2e 本机端到端启动器

仅手工验证使用，不进入生产应用。链路：生产 Web 构建 → `web.mjs` 本机入口代理 → 真实 Gateway → 真实 BFF → 真实 Platform → `local-environment` MySQL/Redis。

- **数据库：** 只连接本机独立库 `mor_platform_external_api`（准备、启用、回滚 SQL 见 [local-e2e](../../../db/platform-support-service/local-e2e/)），不触碰测试同用的 `mor_platform`。
- **Platform：** `PlatformE2e` 以测试模块装配启动真实应用（开发签名 Key 与 Vault Mock HTTP 来自测试配置），去掉测试固定时钟改用生产 UTC 时钟，插入一个平台管理员与一个只有 `EXTERNAL_API_READ` 的只读人员（合成账号，写入独立库，不清理）。
- **BFF / Gateway：** 与前序验收相同，test Profile 只为允许 localhost HTTP Origin；服务发现指向本机监听地址。
- **运行文件：** 写入 `E2E_RUNTIME` 指定的 0700 私有目录，含合成账号与临时令牌，不归档到仓库。

步骤（在仓库根目录载入本机私有环境，不输出内容）：

1. `E2E_RUNTIME=<私有目录> node web.mjs`，生成 Web Origin。
2. 依次 `python3 start.py platform`、`bff`、`gateway`，每次等待对应 runtime JSON 生成。
3. `python3 verify.py`：经真实入口执行 30 项接口检查，数据保留供页面走查。
4. `python3 browse.py <截图目录>`：无头浏览器以管理员与只读人员走查各页面，记录截图与浏览器控制台错误。
5. 结束时依次停止 Web、Gateway、BFF、Platform；如需撤销启用执行 `deactivate.sql`。

本次结果见 [e2e-receipt.json](../e2e-receipt.json) 与 [screens](../screens/)。
