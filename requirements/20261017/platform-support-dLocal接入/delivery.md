# dLocal 接入交付记录

## 1. 阶段划分

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md)；沙箱直连探测 | **已确认**（2026-10-17） |
| 2 | mor-test 配置与全部沙箱用例（含强制付款的已付款链路）；其他渠道回归 | **已确认**（2026-10-17） |
| 3 | 文档回写与提交 | **已完成**（提交 `ee8ec88`，未推送） |

## 2. 阶段 1 结果（2026-10-17）

- **已读：** 对接包全部文件（凭据只按结构读取，未输出）；dLocal 官方测试付款与退款状态页；引擎 `ScriptFunctions`、`ChannelRequestBuilder`、`HttpWork`、原子 API 校验器；mor-test 现行 5 个标准 API 的字段与已有实现。
- **结论：** 现有引擎函数够用，不需要改 Platform 代码或标准 API。
- **沙箱直连探测**（本机 Python，未经 Platform；脚本在 scratchpad，凭据进程内读取）：结果见需求第 2 节。探测单 `MORPROBE101`～`106` 均为终态（`PAID`、`REJECTED`、`CANCELLED`），`102` 未创建。
- **未执行：** 未改代码、mor-test 配置与数据库；未提交。

## 3. 阶段 1 确认（2026-10-17）

用户：“可以，继续”。D1～D6 按建议，进入阶段 2。

## 4. 阶段 2 结果（2026-10-17）

- **写入**（[configure.py](./assets/harness/configure.py)，经 Nginx → Gateway → BFF → Platform 管理接口；不改代码、标准 API 与 SQL）：
  - 接入方 `DLOCAL`，出站地址 `DLOCAL_API` = `https://sandbox.dlocal.com`（标记沙箱）。
  - 脚本 `DLOCAL_SIGN_JSON`（锁定请求体后签名，带 `X-Idempotency-Key`）、`DLOCAL_SIGN_EMPTY`（GET 与无 body 的 POST，签名原文 body 为空串）、`DLOCAL_EMPTY_PAYER`、`DLOCAL_PICK_REFUND`。
  - 原子 API `CREATE_PAYMENT`、`GET_ORDER`、`CANCEL_PAYMENT`、`CREATE_REFUND`、`GET_PAYMENT_REFUNDS`；流程 `PAY`、`PAY_QUERY`、`PAY_CANCEL`、`REFUND`、`REFUND_QUERY`，全部校验通过。
- **与技术方案的差异：** 函数参数不能嵌套函数，订单金额按币种精度补齐（`1.0` → `1.00`）分两步：`GET_ORDER` 输出 `MONEY_MINOR_UNITS` 的最小单位，流程再用 `MONEY_MAJOR_UNITS` 转回。`PAY_CANCEL` 在查单已是 `CANCELLED`/`REJECTED`/`EXPIRED` 时不调用撤销，直接 `CLOSED`。
- **用例**（[sandbox_cases.py](./assets/harness/sandbox_cases.py)，验证账号 `SBX_MAIN`，材料从对接包读取，不打印）7 个全部 PASSED，首次运行即通过：
  - `SBX_PAY_QUERY_NOT_FOUND`、`SBX_REFUND_NOT_EXIST`、`SBX_REFUND_QUERY_NOT_FOUND`：查单 `404 / 4000` → `NOT_FOUND`、`FAILED`、`NOT_FOUND`，`failureCode=4000`。
  - `SBX_PAY_PENDING_CANCEL`：USD 1.00 / BR 下单 → `PENDING` + `REDIRECT`；查询 `PENDING`，金额 `1.00 USD`；退款 `400 / 5002` → `FAILED`；退款查询 → `NOT_FOUND`/`REFUND_NOT_EXIST`；撤销 → `CLOSED`；重复撤销 → `CLOSED`（不再调撤销接口）；查询 → `FAILED`（`CANCELLED`）。
  - 已付款链路：`SBX_PAID_ORDER`（`PQ` 下单）→ harness 强制 `PAID` → `SBX_PAID_REFUND`（查询 `SUCCEEDED`；撤销 → `ALREADY_PAID`；部分退款 0.50 → `PROCESSING`）→ harness 强制退款 `SUCCESS` → `SBX_PAID_REFUND_QUERY`（`SUCCEEDED`）。
  - 回执：[test-sandbox-receipt.json](./assets/test-sandbox-receipt.json)（不含凭据、跳转 URL、渠道单号）。
- **凭据核对：** 以值比对（不输出）确认 `xTransKey`、`secretKey` 不在回执、7 次运行详情与近 30 分钟 Platform 容器日志中。它们只出现在验证账号详情接口的 `credentials` 中，这是渠道沙箱验证已确认的设计（沙箱凭据明文保存与回显，技术方案 S2、V02），与 Alipay+、PayerMax 验证账号相同。
- **测试单：** 两轮 `SBX_PAY_PENDING_CANCEL` 的订单已撤销（`CANCELLED`），已付款链路订单为 `PAID` 且退款 `SUCCESS`，均为终态，无需清理。
- **回归：** PayBy 5 个 UAT 用例、PayerMax 4 个 UAT 用例（含下单后查询）、Alipay+ 3 个不需付款用例全部 PASSED。
- **未执行：** `CURRENT_IMPLEMENTATION.md` 回写与提交（阶段 3）。

## 5. 阶段 3（2026-10-17）

- **授权：** 用户“可以”（阶段 2 通过，回写并提交，不推送）。
- **回写：** `CURRENT_IMPLEMENTATION.md` 新增 dLocal 小节（Designed、mor-test 配置 Deployed / Sandbox Verified）。
- **提交：** `ee8ec88`（私有暂存区，只含本需求目录与 `CURRENT_IMPLEMENTATION.md` 的 dLocal 小节；该文件中其他会话的未提交改动保留在工作区）；暂存差异经值比对不含凭据。未推送。
- **验证：** `npm --prefix requirements-site test` 127 通过、10 失败，失败项与改动前相同（Commerce、Payment、开发规范等既有文档断言），与本需求无关。
