# Toss Pay 接入 harness

经 mor-test 的 Nginx → Gateway → BFF → Platform 管理接口保存配置并运行渠道沙箱验证。管理员登录与运行目录沿用
[PayerMax harness](../../../../20261014/platform-support-PayerMax接入/assets/harness/README.md) 的 `common.py`（`E2E_RUNTIME` 指向仓库外 0700 目录）。

| 脚本 | 作用 |
|---|---|
| `configure.py TOSS_PAY https://pay.toss.im` | 保存接入方、1 个脚本、4 个原子 API（`credentialFields=["BODY:apiKey"]`）与 4 条流程（存在则按版本修改），每条流程先校验 |
| `sandbox_cases.py` | 录入验证账号 `SBX_MAIN`（apiKey 从仓库外对接包 records[0] 读取，必须是 `sk_test_`），运行 5 个不需付款的用例 |

输出写入 `$E2E_RUNTIME`：`configure-TOSS_PAY.json`、`toss-sandbox-cases.json`。仓库内回执 [test-sandbox-receipt.json](../test-sandbox-receipt.json) 不含 apiKey、付款页 URL 与 payToken。
已付款链路需要买家手机上的 Toss App（韩国账号）认证付款，本期未运行。
