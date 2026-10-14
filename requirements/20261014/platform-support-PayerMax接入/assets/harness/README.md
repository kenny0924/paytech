# PayerMax 接入本机联调 harness

仅手工验证使用，不进入生产应用。配置经真实入口（`web.mjs` 代理 → Gateway → BFF → Platform 管理接口）保存；执行直连 Platform 内部接口 `POST /platform-support/standard-apis/v1/{PAY|PAY_QUERY}/execute`（不经 Gateway）。

| 文件 | 作用 |
|---|---|
| `start.py` | 启动 `payermax-mock`、`platform`、`bff`、`gateway`；Web 入口沿用[外部 API 2e 的 web.mjs](../../../20260914/platform-support-外部API配置与执行/assets/stage2e/harness/) |
| `PayerMaxMock.java` | 本机合成 PayerMax：真实 SHA256withRSA 请求验签与响应签名，检查外层字段、`requestTime` 时效与金额为 JSON 数字；按订单号（退款按退款号）末位返回各场景；首次启动生成两对合成密钥写入运行目录，之后重启沿用密钥与端口。退款接口供 [PayerMax 退款 harness](../../../platform-support-PayerMax退款/assets/harness/README.md) 使用 |
| `PlatformPayerMaxE2e.java` | 真实 Platform（测试装配的 Vault Mock，去掉测试固定时钟）；登记 mock 账号，若设置 `PAYERMAX_UAT_ACCOUNT_FILE` 另登记 uat 账号 |
| `configure.py` | 保存或按版本更新标准 API `PAY`、`PAY_QUERY` 与一个接入方的全部配置（[技术方案第 4 节](../../technique.md#4-payermax-配置阶段-3)） |
| `verify_mock.py` | 对 `PAYERMAX_MOCK` 跑全部场景并写回执 |
| `common.py` | 管理接口与执行接口调用 |

- **数据库：** 沿用外部 API 2e 的本机独立库 `mor_platform_external_api`，不触碰测试库 `mor_platform`；配置只经管理接口写入。
- **运行目录：** `E2E_RUNTIME` 指向仓库外 0700 目录，存放端口、合成管理员口令、Vault Token、合成密钥与日志。
- **UAT 账号文件（阶段 4）：** 仓库外 0600 JSON，键为 `appId`、`merchantNo`、`keyVersion`、`merchantPrivateKey`（PKCS8 Base64，无首尾行）、`payermaxPublicKey`（X.509 Base64）。只由 Platform 启动器读入 Vault Mock 内存。

步骤（仓库根目录；先载入 `local-environment/.env.local` 中的 `MOR_PLATFORM_DB_PASSWORD`、`MOR_REDIS_PASSWORD`，不输出内容）：

1. `E2E_RUNTIME=<私有目录> node requirements/20260914/platform-support-外部API配置与执行/assets/stage2e/harness/web.mjs`
2. 依次 `python3 <本目录>/start.py payermax-mock`、`platform`、`bff`、`gateway`，每次等待对应 runtime JSON 生成（UAT 联调时启动 platform 前设置 `PAYERMAX_UAT_ACCOUNT_FILE`）。
3. 在本目录执行 `python3 configure.py PAYERMAX_MOCK http://127.0.0.1:<payermax-mock 端口>/aggregate-pay/api/gateway`，再执行 `python3 verify_mock.py`。
4. 结束时停止各进程。

阶段 3 结果见 [stage3-receipt.json](../stage3-receipt.json)。
