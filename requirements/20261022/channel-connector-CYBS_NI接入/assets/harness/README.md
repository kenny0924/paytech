# Cybersource（NI）接入 harness

经 mor-test 的 Nginx → Gateway → ops BFF → channel-connector 管理接口保存配置并运行渠道沙箱验证。登录与 TLS 复用 [e2e_http.py](../../../../20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py)（`E2E_TARGET=test`，管理员口令运行时读 `test-environment/private/access.md`）。

| 脚本 | 作用 |
|---|---|
| `mortest.py` | 登录与管理接口调用（写命令带 `Idempotency-Key`，429 按 `Retry-After` 重试） |
| `configure.py [base URL]` | 标准 API REFUND / CAPTURE 追加可选 `paymentChannelRef`（已存在则跳过）；建支付渠道 `I_CYBS_NI` 与沙箱端点；保存脚本 `CYBS_HTTP_SIGNATURE` / `CYBS_PICK`、原子 API `PAYMENT` / `REFUND` / `CAPTURE` / `SEARCH` 与六条流程（存在则按版本修改，流程先校验） |
| `sandbox_cases.py` | 录入验证账号 `SBX_APPROVED` / `SBX_DECLINED`（材料从仓库外对接包读取，测试卡为 Cybersource 公布的 Visa 测试卡），先跑 4 个写用例，全部通过后等待 10 秒再跑 3 个读用例 |

运行：`CYBS_RUNTIME=<仓库外 0700 目录> python3 <脚本>`。运行详情原文只写到 `CYBS_RUNTIME`；仓库内回执 [test-sandbox-receipt.json](../test-sandbox-receipt.json) 不含凭据、卡号、渠道交易 id 与商户号。回归复用 [MPGS harness](../../../../20261021/channel-connector-MPGS_NI接入/assets/harness/README.md) 的 `sandbox_cases.py` 与 `regression.py`。
