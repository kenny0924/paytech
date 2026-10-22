# Cybersource（NI）接入交付记录

## 1. 阶段划分（建议，待确认）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求评审与范围确认 | **已确认**（2026-10-22） |
| 2 | 现状差异；签名按 `test-vectors.json` 本机复算；用只读查单在 apitest 验证凭据 | **已确认**（2026-10-22） |
| 3 | 接入设计：沙箱写探测（单号前缀 `PTTEST`）、脚本函数设计、字段映射、用例清单 | **已确认**（2026-10-22） |
| 4 | channel-connector 代码：3 个脚本函数和自动测试 | **已确认**（2026-10-22） |
| 5 | mor-test：channel-connector 镜像发布、渠道配置、沙箱用例、回归 | **已确认**（2026-10-22） |
| 6 | 文档回写与本地提交（不推送） | 已完成（本地提交，未推送） |

提交、mor-test 部署各自需要单独授权。

## 2. 阶段 1（2026-10-22）

- **已读：** 对接包 README、notes.md、凭据文件结构（只看字段名，未输出值）；channel-connector 的 `ScriptFunctions`、`HttpWork`、`ChannelHttpClient`。
- **产出：** [requirement.md](./requirement.md)（评审稿）。
- **未执行：** 未调用任何 Cybersource 接口，未改代码与 mor-test。

## 3. 阶段 1 确认（2026-10-22）

用户：“可以”。按评审稿执行：6 个标准 API，不含 3DS。

## 4. 阶段 2（2026-10-22）

- **签名复算：** 本机 Python 复算 6 个样本，全部逐字节一致。
- **apitest 只读探测：** 共 7 次 Transaction Search，查询不存在的单号。SAR / USD 一组和 PF 一组有效，其余 5 组返回 401。没有创建交易。
- **引擎核对：**
  - JDK 25 允许设置 `Date` 头；
  - 脚本读不到 URL 和 host；
  - 现有函数缺少 3 项；
  - 数组可以沿用 RESPONSE 脚本的有界 `for` 处理；
  - 发现 `sealJsonBody` 会把含卡号的发送文本返回给脚本（技术方案 1.4），不在本需求范围。
- **分析与待确认决定：** [技术方案第 1 节](./technique.md#1-现状差异与影响阶段-2)，决定 E1～E5。
- **未执行：** 未改代码与 mor-test，未提交。

## 5. 阶段 2 确认（2026-10-22）

用户：“可以”。E1～E5 按建议执行，授权沙箱写探测。

## 6. 阶段 3（2026-10-22）

- **沙箱写探测：** 共 2 轮，含下单、重发、预授权、2 种拒付、退款、请款、超次请款、`id` 不存在、Search 延迟，以及退款挂授权 `id`。结果见[技术方案第 2 节](./technique.md#2-沙箱写探测结果阶段-32026-10-22)。新增交易都已是终态或待批量，不需要清理。
- **设计：** [技术方案第 3～6 节](./technique.md#3-设计决定)，决定 C1～C13；新增函数规格、6 条流程、2 个验证账号、5 个用例。
- **与评审稿的差异：**
  - 请款只做单次（C9）；
  - 退款和请款同步 `PENDING` 判为成功（C6）；
  - `merchantKeyId` 改为账号参数（C3）。
- **未执行：** 代码、mor-test、提交。

## 7. 阶段 3 确认（2026-10-22）

用户：“可以”。设计 C1～C13 按技术方案执行，进入阶段 4。

## 8. 阶段 4（2026-10-22）

- 任务卡：[tasks/S4-script-functions.md](./tasks/S4-script-functions.md)。执行 agent 在独立 worktree 中实现，基线 main `1d39cd94`；完成后由主会话复核 diff 与测试结果。
- **实现**（worktree `.claude/worktrees/agent-aa9f8ff73f0661310`，基线 `1d39cd94`，未提交）：
  - `ScriptFunctions` 新增 `sha256Base64`、`hmacSha256Base64DecodedKey`、`nowRfc1123`；
  - `RegisteredFunctionCatalog` 登记这 3 个函数；
  - `ExpressionEngine` 只改了一行注释。
  - 已有函数未改动。
- **测试：**
  - `ScriptSigningFunctionsTest` 新增 4 个测试，扩展 1 个：
    - 用测试专用样本逐字节核对摘要和签名；
    - RFC 1123 格式和日期补零；
    - 非法 Base64 密钥、普通字符串密钥被拒绝，异常消息不带值；
    - 目录登记。
  - `StandardApiSignedChannelTest.test_cybersourceHttpSignature`：从标准 API 执行入口经真实出站客户端发到 Mock 渠道，渠道独立重算 `Digest` 和 `Signature` 并逐字节一致；日志与执行结果不含密钥。
- **结果：** channel-connector auto 共 381 个测试，0 失败、0 错误、0 跳过（基线 376 + 新增 5）。主会话复核了 diff 和测试日志，日志中不含测试密钥。
- **与设计的细节差异：** 测试脚本把 `requestTarget` 参数作为完整的 `post <path>` 传入（含方法名），mor-test 配置沿用这一写法。

## 9. 阶段 5（2026-10-22）

用户：“可以”（授权发布 mor-test channel-connector 并写入渠道配置）。

**镜像发布**（[release](./release/)）：
- 源码：干净 worktree `Code-MoR-cybs-rel`，main `1d39cd94` 加阶段 4 diff；只构建 channel-connector（离线，`-DskipTests`）。
- 与运行镜像 `798940ea` 的 JAR 逐条目（含嵌套 JAR）比对：只差 `ScriptFunctions`（含内部类）和 `RegisteredFunctionCatalog`。
- 镜像 Entrypoint、User、WorkingDir、Env 与运行镜像相同；渲染出的 27 个环境变量与运行容器逐项相同。
- 发布前通知 4 个在线会话，均回复无冲突。
- 12:49:38–12:50:09Z 替换为 `5f22b55b…`（tag `cybs-20261022`）：healthy，停机约 20 秒，启动日志无 ERROR；编辑器能力已列出 3 个新函数。
- 回滚文件 `release/compose-rollback.yaml`（`798940ea`）。

**渠道配置：**
- 渠道 `I_CYBS_NI`（PAYMENT，DRAFT，id `239008232893423616`），端点 `CYBS_API` = apitest（沙箱）；
- 脚本 2 个，原子 API 4 个，流程 6 条，全部校验通过并保存；
- 验证账号 `SBX_APPROVED`、`SBX_DECLINED`。

**沙箱运行与发现（F1）：**
- 签名在 mor-test 上全部被 Cybersource 接受。
- 下单、拒付、预授权、请款（含第二次请款被拒）、`DUPLICATE_REQUEST`、不存在的单，均按设计映射。
- 但 Cybersource 的**读接口最终一致**：
  - 刚下的单在 Search 中 1～3 秒不可见；
  - 连续两次 Search 结果可以不一致（前一次查到，紧接着的一次查不到），像是多副本索引；
  - 按 `id` 的 `GET /tss/v2/transactions/{id}` 和 `GET /pts/v2/payments/{id}` 在下单后约 2 秒内也返回 404；
  - 写接口（按 `id` 退款、请款）下单后立即可用。
- 因此“先 Search 取原单 `id`、再退款或请款”（C8）和按单号回查（C7）在紧接着下单时会偶发查不到。查询流程已改为最多连查 4 次（返回出口上限 16，不允许跨可选节点读取）；本地用例有时通过、有时失败。
- 已做的同范围调整：
  - 查询和回查改为检索链；
  - 拒付记录没有金额时，金额换算用占位值，失败分支不输出金额；
  - 用例在支付后插入“查不存在的单”作为间隔。
- 待用户决定 F1（见交付说明）。

**F1 决定（2026-10-22）：** 用户选择“加可选渠道引用”，见[技术方案第 7 节](./technique.md#7-阶段-5-决定-f1-与实施调整2026-10-22)。

**F1 实施后：**
- **标准 API：** `REFUND` 和 `CAPTURE` 各追加可选字段 `paymentChannelRef`，服务端复核通过。
- **流程：** Cybersource 的 6 条流程重新保存，全部通过校验。
- **用例：** 删除 3 个旧的链式用例（`SBX_PAY_REFUND_CHAIN`、`SBX_PAY_REPLAY`、`SBX_AUTH_CAPTURE_CHAIN`）。

**沙箱验证：** 7 个用例全部 PASSED（4 个写用例，等待 10 秒后 3 个读用例），连续两轮结果一致。回执：[test-sandbox-receipt.json](./assets/test-sandbox-receipt.json)。

| 用例 | 结果 |
|---|---|
| `SBX_PAY_REFUND` | 支付 `SUCCEEDED`/`AUTHORIZED` → 按渠道引用退款 4.00 `SUCCEEDED`/`PENDING` |
| `SBX_PAY_DECLINED` | 过期卡 → SUCCESS/`FAILED`、`EXPIRED_CARD` |
| `SBX_AUTH_CAPTURE` | 预授权 `SUCCEEDED` → 请款 6.00 `SUCCEEDED` → 第二次请款 FAIL/`INVALID_DATA` → 按授权引用退款 2.00 `SUCCEEDED` |
| `SBX_NOT_FOUND` | 查询、退款查询 `NOT_FOUND`；按不存在的渠道引用退款、请款 FAIL/`INVALID_DATA` |
| `SBX_PAY_REFUND_READ` | 支付查询 `SUCCEEDED`、10.00 USD → 退款查询 `SUCCEEDED`、4.00 → 重发退款：渠道 `DUPLICATE_REQUEST`，回查后 `SUCCEEDED` → 用支付单号查退款 `NOT_A_REFUND` → 重发支付：回查后 `SUCCEEDED` |
| `SBX_PAY_DECLINED_READ` | 查询 `FAILED`（`DCARDEXPIRED`） |
| `SBX_AUTH_CAPTURE_READ` | 预授权单查询 `NOT_A_PAYMENT` → 重发第一笔请款：回查后 `SUCCEEDED` |

**回归：** MPGS 8 个用例和其他渠道 8 个用例全部 PASSED，均在新镜像和新版 REFUND / CAPTURE 标准 API 上运行。

**凭据检查**（用值比对，只看命中次数）：
- 全部商户的密钥原值：运行详情、需求目录、channel-connector 与 ops BFF 近 60 分钟日志中均为 0 次；两个服务的 ERROR 日志也为 0。
- 测试卡号：运行详情与日志中 0 次；需求目录中只出现在设计文档里（公开测试卡）。
- 商户号、密钥编号：属于账号参数，会出现在运行详情的请求头中，回执里已替换为 `<merchant>`。
- 运行轨迹中的 `Signature` 头没有遮蔽（每次请求的 HMAC 结果，由脚本写入的普通头），与 dLocal、PayerMax 等脚本签名渠道相同。

**沙箱交易：** 新增的支付、预授权、请款、退款都已受理，退款和请款等待批量；不需要清理。

**未执行：** 提交（阶段 6）。

## 10. 阶段 6（2026-10-22）

用户：“可以”（确认阶段 5，进入阶段 6）。

- **文档回写：**
  - `agent-docs/CURRENT_IMPLEMENTATION.md` 新增 Cybersource 一节；
  - `requirements/README.md` 需求索引新增一行。
  - 这两个文件里其他会话尚未提交的改动没有带进本次提交。
- **提交：** 使用私有暂存区本地提交，包含：
  - channel-connector 的 3 个函数与测试，与 mor-test 镜像的源码一致；
  - 本需求目录；
  - 上面两个文件中本需求的部分。
  - 未推送。
- **收尾：** 提交后删除发布 worktree `Code-MoR-cybs-rel` 和执行 agent 的 worktree。
