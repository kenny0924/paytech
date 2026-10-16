# dLocal 接入 harness

经 mor-test 的 Nginx → Gateway → BFF → Platform 管理接口保存配置并运行渠道沙箱验证。管理员登录与运行目录沿用
[PayerMax harness](../../../../20261014/platform-support-PayerMax接入/assets/harness/README.md) 的 `common.py`（`E2E_RUNTIME` 指向仓库外 0700 目录）。

| 脚本 | 作用 |
|---|---|
| `configure.py DLOCAL https://sandbox.dlocal.com` | 保存接入方、4 个脚本、5 个原子 API 与 5 条流程（存在则按版本修改），每条流程先校验 |
| `sandbox_cases.py` | 录入验证账号 `SBX_MAIN`（材料从仓库外对接包读取），运行 7 个用例；已付款链路由 harness 直连 dLocal 沙箱工具强制 `PAID` / 退款 `SUCCESS` |

输出写入 `$E2E_RUNTIME`：`configure-DLOCAL.json`、`dlocal-sandbox-cases.json`。仓库内回执 [test-sandbox-receipt.json](../test-sandbox-receipt.json) 不含凭据、跳转 URL 与渠道单号。
