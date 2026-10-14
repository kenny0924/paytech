# PayerMax 退款交付记录

## 1. 阶段划分（2026-10-14 用户确认）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与技术方案：[需求](./requirement.md)、[技术方案](./technique.md) | **已确认**（2026-10-14） |
| 2 | 配置与本机合成 PayerMax 验证：`configure_refund.py`、合成渠道退款接口、`verify_mock_refund.py` | **已确认**（2026-10-14） |
| 3 | PayerMax UAT：经 Operations 渠道沙箱验证运行退款用例（需用户在 UAT 收银台付款一笔） | 进行中：在 mor-test 执行，两个无需付款的用例已通过，待付款 |

写入 mor-test、提交与推送不在上述阶段内，需另行授权。

## 2. 阶段 1 结果（2026-10-14）

- 用户要求：“先帮我把 payermax 的退款做了”。
- 协议来源：PayerMax 官方退款申请、退款查询、退款集成说明与错误码页面（链接见需求第 1 节），只读取未存档。
- 已读：PayerMax 接入需求、技术方案、交付记录与 `configure.py`；Payment Execution `ActionType`、`ActionStatus` 与 `t_channel_refund` 设计。
- 结论：现有引擎能力（RSA 签名/验签、锁定请求体、协议时间、数字金额）足够，**不改代码与数据库结构**，只新增标准 API、原子 API、流程配置与 harness。
- 未修改代码、配置或数据库；未提交。

## 3. 阶段 1 确认（2026-10-14）

用户回复“按照你的建议吧”：R1～R6 全部按技术方案建议执行，进入阶段 2。

## 4. 阶段 2 结果（2026-10-14，待确认）

**harness：**
- 新增 [configure_refund.py](./assets/harness/configure_refund.py)、[verify_mock_refund.py](./assets/harness/verify_mock_refund.py)，复用接入 harness 的 `common.py` 与 `configure.py`（字段、外层、签名/验签脚本、账号初始化）；退款原子 API 外层 `version` 固定 `1.1`。
- [PayerMaxMock.java](../platform-support-PayerMax接入/assets/harness/PayerMaxMock.java) 增加 `/refund`、`/refundQuery`（按退款号末位返回场景，外层 `version=1.1`，检查 `refundAmount` 为 JSON 数字与必填字段）；运行目录已有密钥与端口时沿用。日志字段 `totalAmountIsNumber` 改名 `amountValid`，`verify_mock.py` 同步。

**运行（本机，`mor_platform_external_api`；Web 代理 → Gateway → BFF → Platform）：**
- 合成渠道原密钥只在进程内存中，本次改为可沿用前需重新生成一次，因此按顺序重启了合成渠道、Platform、BFF、Gateway（Web 代理保留）；端口不变，未改出站地址。
- 原 `verify_mock.py` 回归 **12 项 PASS**。
- `configure_refund.py PAYERMAX_MOCK` 首次 6 项创建、重跑 6 项按版本修改，均通过。
- `verify_mock_refund.py` **14 项 PASS**：输入缺字段 422、账号身份不一致 503（零渠道请求）；受理 `PENDING`/`SUCCEEDED`/`FAILED`（返回 `channelRefundRef`）；`REFUND_FAILED`、`ORDER_NOT_EXIST` → FAIL + `FAILED`；`SYSTEM_ERROR`、响应错误私钥签名 → ERROR；省略可选 `reason` 仍受理；查询 `PENDING`/`SUCCEEDED`（整数金额 → `"5"`）/`FAILED`（带失败原因）与不存在 → FAIL + `NOT_FOUND`。合成渠道收到 12 个请求，签名、外层、`requestTime` 与金额检查全部有效。
- Platform/BFF/Gateway/合成渠道日志、调用记录与回执中两把合成私钥片段出现次数为 0。回执：[stage2-receipt.json](./assets/stage2-receipt.json)。

未改生产代码、未执行 SQL、未提交。本机服务保持运行，供阶段 3 使用。

## 5. 写入 mor-test（2026-10-14）

用户要求“帮我维护到 test 环境”（视同确认阶段 2，并授权经管理接口写入 mor-test 配置；阶段 3 改在 mor-test 执行）。不涉及镜像、SQL、Nginx、Vault 或 Nacos 变更。

- **核对：** mor-test 16 个容器 healthy，入口 `http://localhost:28080` 200。Platform（`b211ce16…`）与 Web（`7455978a…`）已由客户入驻发布替换，Gateway（`1cddfeab…`）、BFF（`f73da253…`）与上次发布相同；接入方 `PAYERMAX`、出站地址沙箱标记与验证账号 `UAT_MAIN` 仍在。
- **写入：** 管理员口令只从不提交的 `test-environment/private/access.md` 读入 scratchpad 0700 运行目录（未输出）；经 Nginx → Gateway → BFF → Platform 执行 `configure_refund.py PAYERMAX`，创建标准 API `REFUND`、`REFUND_QUERY`，接入方 `PAYERMAX` 下原子 API `REFUND`、`REFUND_QUERY` 与两条流程（6 项创建，全部校验通过）。`PAY`/`PAY_QUERY`、脚本、出站地址与账号未改。
- **UAT 验收（渠道沙箱验证，[uat_refund_cases.py](./assets/harness/uat_refund_cases.py)）：**
  - `UAT_REFUND_NOT_EXIST` **PASSED**：PayerMax 返回 `ORDER_NOT_EXIST` → FAIL，`refundStatus=FAILED`。
  - `UAT_REFUND_QUERY_NOT_FOUND` **PASSED**：`ORDER_NOT_EXIST` → FAIL，`refundStatus=NOT_FOUND`。
  - 两次请求 PayerMax 均验签通过（未返回 `SIGN_VERIFY_FAILED`），Platform 对两个 HTTP 200 响应验签通过（否则为 ERROR）；外层 `version=1.1` 被 UAT 接受（R1 成立）。
  - 实测文字：退款接口原订单不存在时 `msg` 为 “The order does not exist.”，查询退款不存在时为 “The orginal order does not exist.”，与文档两处描述相反，不影响判断（只按 `code`）。
- **待付款：** 已在 mor-test 运行 `UAT_PAY_THEN_QUERY` 下单一笔（订单号 `PMXV456542980737`，10.00 USD，PENDING），收银台地址只交给用户，不记录。付款后执行 `uat_refund_cases.py PMXV456542980737 <金额>` 运行 `UAT_REFUND_THEN_QUERY`。
- 回执：[test-uat-receipt.json](./assets/test-uat-receipt.json)（不含凭据与收银台地址）。未提交。
