# MPGS（Network International）接入交付记录

## 1. 阶段划分

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求评审与范围确认 | **已确认**（2026-10-21） |
| 2 | 现状差异与影响分析；沙箱只读连通性探测 | **已确认**（2026-10-21） |
| 3 | 接入设计（沙箱直连写探测、字段映射、用例清单） | **已确认**（2026-10-21） |
| 4 | mor-test 配置与沙箱用例；其他渠道回归 | **已确认**（2026-10-22，随 4A 一并） |
| 4A | 追加：预授权与请款的沙箱探测与设计 | **已确认**（2026-10-22） |
| 4B | 追加：预授权与请款的 mor-test 配置与沙箱用例 | **已确认**（2026-10-22） |
| 5 | 文档回写与提交（含追加部分） | 已完成（本地提交，未推送） |

阶段划分按[技术方案 1.3](./technique.md#13-阶段调整建议)，已确认（D5）。

## 2. 阶段 1（2026-10-21）

- **已读：** 对接包全部文件（凭据只读结构，不输出）、channel-connector README、执行引擎相关源码、dLocal / PayerMax 接入记录。
- **用户确认：** “可以，只做到和现在已有渠道部分差不多就行了”。范围为配置级接入加沙箱验证；API 只做 v100、只接普通商户，按评审稿建议执行。

## 3. 阶段 2（2026-10-21）

- **沙箱只读探测**（本机 Python 直连，脚本在会话 scratchpad，凭据进程内读取）：每个商户用不存在的单号 `MORPROBE0001` 发一次 `GET` Retrieve Transaction，24 个商户 × 2 个测试地址。结果见[需求第 2 节](./requirement.md#2-已实测事实2026-10-21本机直连沙箱未经-channel-connector)。未创建任何沙箱交易。
- **分析：** [技术方案第 1 节](./technique.md#1-现状差异与影响阶段-2)。
- **未执行：** 未改代码、mor-test 配置与数据库；未提交。

## 4. 阶段 2 确认（2026-10-21）

用户：“单号前缀就PTTEST”。D1～D5 按建议执行，沙箱写探测单号前缀改为 `PTTEST`。

- **D1：** 用 `JOLLYMAX_AED` 商户。写探测证实沙箱接受 USD，所以用例仍用 USD。
- **D2：** 不建渠道、账号、MID。
- **D3：** mor-test 的 `PAY` 标准 API 增加可选卡字段。
- **D4：** 授权沙箱写探测。
- **D5：** 按 5 个阶段推进。

## 5. 阶段 3（2026-10-21）

- **沙箱写探测：** 共 3 笔支付（批准、被拒、USD）、1 笔部分退款和 4 次查询，另含 2 次预期失败的请求。结果见[技术方案第 2 节](./technique.md#2-沙箱写探测结果阶段-32026-10-21)，全部交易已是终态。
- **设计：** [技术方案第 3～6 节](./technique.md#3-设计决定)，包括设计决定 M1～M10、原子 API 与流程、验证账号与 5 个用例、实施步骤与恢复方式。
- **未执行：** mor-test 配置、标准 API 修改；未提交。

## 6. 阶段 3 确认（2026-10-21）

用户：“可以”。设计按技术方案第 3～6 节执行，授权 mor-test 配置写入。

## 7. 阶段 4（2026-10-21）

**写入**（[harness](./assets/harness/README.md)）：经 Nginx → Gateway → ops BFF → channel-connector 管理接口写入。不改代码、SQL、镜像、Nacos，不建渠道、账号、MID。

- **标准 API `PAY`：** 版本 2 → 3。请求字段从 18 个增加到 22 个，新增 `card`、`card.token`、`payment.clientIp`、`payment.userAgent`，均为可选。服务端复核通过，ALIPAY_PLUS、DLOCAL、PAYERMAX、TOSS_PAY 的流程保持不变。
- **接入方 `I_MPGS_NI`：** 出站端点 `MPGS_API` = `https://test-gateway.mastercard.com`，标记为沙箱。
- **脚本与原子 API：** 脚本 `MPGS_BASIC_AUTH`；原子 API `PAY`、`RETRIEVE`、`REFUND`。
- **流程：** `PAY`、`PAY_QUERY`、`REFUND`、`REFUND_QUERY` 全部通过校验。

**与设计的差异：** 无业务差异。第一次保存 PAY 流程时校验报 `REQUEST_FIELD_UNCOVERED`，原因是中间的对象节点被声明成了必填。改为可选后通过，与 PayerMax 卡直付的做法相同。

**沙箱验证：** 5 个用例首次运行全部 PASSED，验证账号为 `SBX_APPROVED` 和 `SBX_DECLINED`，商户 `JOLLYMAX_AED`，币种 USD。回执：[test-sandbox-receipt.json](./assets/test-sandbox-receipt.json)。

| 用例 | 结果 |
|---|---|
| `SBX_PAY_REFUND_CHAIN` | 支付 `SUCCEEDED`/`APPROVED` → 查询 `SUCCEEDED`，10.00 USD → 部分退款 4.00 `SUCCEEDED` → 退款查询 `SUCCEEDED`，4.00 USD → 用支付交易号查退款得到 `NOT_FOUND`/`NOT_A_REFUND` → 退款后支付查询仍为 `SUCCEEDED`（M6） |
| `SBX_PAY_DECLINED` | 支付 SUCCESS/`FAILED`，`failureCode=DECLINED`，`failureMessage=05 Do not honour` → 查询 `FAILED` |
| `SBX_PAY_QUERY_NOT_FOUND` | FAIL/`NOT_FOUND`，`INVALID_REQUEST` |
| `SBX_REFUND_NOT_EXIST` | FAIL/`FAILED`，`INVALID_REQUEST` |
| `SBX_REFUND_QUERY_NOT_FOUND` | FAIL/`NOT_FOUND`，`INVALID_REQUEST` |

**报文核对（AC3）：**
- Basic 认证算法用对接包 `auth.basicHeader` 样本重算，逐字节一致；沙箱接受认证（返回 201/200）。
- 退款请求体结构与 `refund.v100.request` 一致。
- 支付请求体覆盖 `paymentApply.v100.card.request` 的全部字段，另带设计约定的 `order.reference` 和 `device`；年份为 2 位（M5）。
- URL 为 `/api/rest/version/100/merchant/{merchantId}/order/{attemptId}/transaction/{attemptId 或 refundId}`。

**凭据检查（AC4）：** 用值比对，只看命中次数，不输出原值。
- `apiPassword`、`notificationSecret`、Basic 头原值、测试卡号原值，在 5 次运行详情和 channel-connector、ops BFF 近 90 分钟日志中均为 0 次；两个服务的 ERROR 日志也为 0。
- 运行轨迹中 Authorization 为 `[REDACTED]`，卡号只保留前 6 后 4，其余卡字段为 `[CARD]`。
- 验证账号详情的 `credentials` 回显明文和测试卡，这是渠道沙箱验证已确认的设计，与 PayerMax 卡直付相同。
- 仓库文件按全部 24 个商户的密码和通知密钥检索，0 命中。

**回归（AC5）：** 8 个用例全部 PASSED：
- PAYERMAX 3 个查询或退款不存在用例；
- PAYBY、DLOCAL、ALIPAY_PLUS、TOSS_PAY 各 1 个查询不存在用例；
- DLOCAL `SBX_PAY_PENDING_CANCEL`：在新版 PAY 标准 API 上下单、查询、退款被拒、撤销，共 7 步。

**沙箱交易：** MPGS 侧新增 2 笔 `PTTEST` 支付，分别为已部分退款和被拒；dLocal 侧新增 1 笔已撤销订单。全部是终态，不需要清理。

**未执行：** `CURRENT_IMPLEMENTATION.md` 回写、需求索引登记、提交（阶段 5）。

## 8. 追加：预授权与请款（2026-10-22）

用户：“帮我增加auth和capture的标准接口，把NI的加上”。阶段 4 的结果用户没有单独确认；追加部分插在阶段 5 之前，作为 4A（探测与设计）和 4B（配置与验证）。

**阶段 4A：**
- **现状：** mor-test 共 7 个标准 API，没有 `AUTHORIZE` 和 `CAPTURE`。代码中的 `ChannelOperation` 和能力定义已有这两个操作。
- **沙箱探测**（本机直连，脚本在会话 scratchpad）：做了 2 笔预授权（批准、被拒）、3 次请款尝试（成功 2 次、超额被拒 1 次）、1 次重发和 2 次预期失败的请款，另有 3 次查询。结果见[技术方案 7.1](./technique.md#71-沙箱探测)，新增交易都是终态。
- **设计：** [技术方案 7.2～7.5](./technique.md#72-设计决定)，决定 A1～A5。
- **未执行：** mor-test 配置、提交。

**阶段 4A 确认（2026-10-22）：** 用户：“可以”。按技术方案 7.2～7.5 执行，授权 mor-test 配置写入。

**阶段 4B（2026-10-22）：**
- **环境变化：** 统一出口第二段已在 mor-test 上线，接入方并入渠道，旧的 `/integrations` 维护接口已删除。`I_MPGS_NI` 已随迁移成为渠道，原子 API、流程、验证账号都在。`configure.py` 改为先确认渠道存在，不再创建或修改接入方。
- **写入：**
  - 新增标准 API `AUTHORIZE`（请求 22 个字段，与 `PAY` 相同；响应 8 个字段）和 `CAPTURE`（请求 11 个字段、响应 5 个字段），已有标准 API 未改。
  - `I_MPGS_NI` 新增原子 API `AUTHORIZE`、`CAPTURE`，以及同名两条流程，都先通过校验再保存。
  - 脚本整体重跑时，已有的脚本、3 个原子 API 和 4 条流程按原内容重新保存一次，版本号加 1，内容不变。
- **沙箱验证：** MPGS 8 个用例全部 PASSED，含新增的 3 个，回执已更新：[test-sandbox-receipt.json](./assets/test-sandbox-receipt.json)。

| 用例 | 结果 |
|---|---|
| `SBX_AUTH_CAPTURE_CHAIN` | 预授权 `SUCCEEDED`/`APPROVED` → 请款 6.00 `SUCCEEDED` → 超额请款 5.00 FAIL/`FAILED`、`INVALID_REQUEST` → 请款 4.00 `SUCCEEDED` → 重发第一笔请款：渠道先返回 400，回查到 `CAPTURE`/`APPROVED`，返回 `SUCCEEDED`（A4）→ 退款 2.00 `SUCCEEDED` |
| `SBX_AUTH_DECLINED` | 预授权 SUCCESS/`FAILED`、`DECLINED` → 请款 FAIL/`FAILED`、`INVALID_REQUEST`（回查不到请款，返回原拒绝原因） |
| `SBX_CAPTURE_NOT_EXIST` | FAIL/`FAILED`、`INVALID_REQUEST` |
| 原有 5 个用例 | 全部 PASSED |

- **回归：** 其他渠道 8 个用例全部 PASSED。
- **凭据检查**（用值比对，只看命中次数）：
  - 全部商户的密码和通知密钥、Basic 头原值，在本次运行详情、需求目录、channel-connector 和 ops BFF 近 40 分钟的日志中都是 0 次；两个服务的 ERROR 日志也是 0。
  - 测试卡号只出现在设计文档和 harness 中，这是 MPGS 公布的测试卡；回执、运行详情和日志里为 0 次。
- **沙箱交易：** 新增 2 笔预授权，一笔已全额请款并部分退款，另一笔被拒；原有用例新增 2 笔支付。全部是终态。
- **未执行：** 提交（阶段 5）。

## 9. 阶段 5（2026-10-22）

用户：“可以”（确认 4B，进入阶段 5）。

- **文档回写：**
  - `agent-docs/CURRENT_IMPLEMENTATION.md` 新增 MPGS 一节；
  - `requirements/README.md` 需求索引新增一行。
  - 这两个文件里其他会话尚未提交的改动没有带进本次提交。
- **提交：** 使用私有暂存区本地提交，只包含本需求目录和上面两个文件中本需求的部分，未推送。
