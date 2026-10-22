# MPGS（NI）接入 harness

经 mor-test 的 Nginx → Gateway → ops BFF → channel-connector 管理接口（`/api/operations/external-api-admin/v1/**`）保存配置并运行渠道沙箱验证。登录与 TLS 复用 [e2e_http.py](../../../../20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py)（`E2E_TARGET=test`，管理员口令运行时读 `test-environment/private/access.md`）。

| 脚本 | 作用 |
|---|---|
| `mortest.py` | 登录与管理接口调用（写命令带 `Idempotency-Key`，429 按 `Retry-After` 重试） |
| `configure.py [base URL]` | PAY 标准 API 追加可选卡字段，新增标准 API `AUTHORIZE` / `CAPTURE`（已存在则跳过）；确认渠道 `I_MPGS_NI` 已存在（统一出口第二段后接入方即渠道，需先在运营控制台建好并配置端点）；保存脚本 `MPGS_BASIC_AUTH`、原子 API `PAY` / `RETRIEVE` / `REFUND` / `AUTHORIZE` / `CAPTURE` 与六条流程（存在则按版本修改，流程先校验） |
| `sandbox_cases.py [CASE ...]` | 录入验证账号 `SBX_APPROVED` / `SBX_DECLINED`（材料从仓库外对接包读取，测试卡为 MPGS 公布的测试卡），运行 8 个用例（含预授权与请款 3 个） |
| `regression.py` | 其他接入方 8 个既有用例重跑 |

运行：`MPGS_RUNTIME=<仓库外 0700 目录> python3 <脚本>`。运行详情原文只写到 `MPGS_RUNTIME`；仓库内回执 [test-sandbox-receipt.json](../test-sandbox-receipt.json) 不含凭据、卡号、渠道交易引用（RRN）与商户号。
