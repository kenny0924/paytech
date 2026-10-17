# Toss Pay 接入交付记录

## 1. 阶段划分（2026-10-17 确认）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md)；直连探测 | 已确认（2026-10-17） |
| 2 | T1 引擎“请求体凭据字段”与 `auto` 测试；提交、发布 mor-test 各自授权 | 已确认（2026-10-17），提交 `fe5a0f3`，mor-test 已发布 |
| 3 | mor-test 配置与不需付款的用例；既有渠道回归 | 已确认（2026-10-17） |
| 4 | （可选）有 Toss 账号时跑已付款链路 | 跳过（2026-10-17 用户确认） |
| 5 | 文档回写与提交 | 已完成（2026-10-17） |

## 2. 阶段 1 结果（2026-10-17）

- **已读：** 对接包全部文件（凭据只按结构读取，未输出；记录 1、2 的生产 key 已被对接包移除）；官方建单、查单、退款、测试与 FAQ 页及 API 索引；引擎请求组装、JSON 序列化、轨迹、校验器与脚本函数源码。
- **结论：** 引擎不能把凭据写进请求体，需要 T1；其余能力（整数金额、数组匹配、后缀判断）已有。
- **直连探测**（本机 Python，`sk_test_` key，进程内读取；共约 12 次调用）：结果见需求第 2 节。测试单 `MORPROBE201`～`203` 为 `PAY_STANDBY`，Toss 没有撤销接口，留待到期（到期后状态仍显示 `PAY_STANDBY`，测试 key 不发生资金移动）。
- **风险：** 已付款链路需要买家本人手机的 Toss App（韩国账号）认证付款，没有公开的测试账号。
- **未执行：** 未改代码、mor-test 配置与数据库；未提交。

## 3. 阶段 2 结果（2026-10-17，本机）

- **引擎（T1）：** 原子 API 新增可选 `credentialFields`。凭据字段只接受受控句柄（流程以 PATH 绑定 `resources.<初始化项>.credentials.<名称>`），只在 JSON 发送字节中展开；轨迹与验证证据为 `[REDACTED]`；脚本把句柄写到其他请求体位置、或请求体含凭据时调用 `sealJsonBody`，节点 ERROR 且不发请求。保存校验问题码 `CREDENTIAL_FIELD_*`、`CREDENTIAL_BINDING_INVALID`。`JsonValues.CardRendering` 更名为 `SecretRendering`（卡值与受控句柄共用）。
- **Operations 原子 API 编辑页：** 保留并可编辑 `credentialFields`（否则保存会丢失该属性）。
- **合同文档：** [原子 API 结构](../../20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md)第 5.4 节、[流程结构](../../20260914/platform-support-外部API配置与执行/flow-definition-contract.md)第 6 节。
- **测试：** 新增 `CredentialFieldRulesTest`（4）、`StandardApiBodyCredentialTest`（4，经标准 API 执行入口与 Mock 渠道：渠道收到明文、响应与日志无明文、两种 ERROR 不发请求、验证证据与库中轨迹遮蔽）。Platform 全量 `./mvnw -o -pl platform-support-test -am test` **729/729**；前端 `npm test` 1061/1061 与构建通过。
- **未执行：** 未提交、未发布 mor-test（阶段 3 执行）。

## 4. 阶段 3 结果（2026-10-17，mor-test）

- **授权：** 用户“可以”（阶段 2 通过；提交、发布 mor-test）。发布命令由用户“你执行吧”授权执行。
- **提交：** `fe5a0f3`（私有暂存区，只含本需求 30 个文件，未推送）。
- **发布：** 从干净提交 `fe5a0f3` 构建 Platform 镜像 `sha256:629cccf9…`（tag `mor-test/platform-support-service:toss-pay-20261017`，jar SHA-256 前缀 `1ea061f01a521920`），按 [release/compose-candidate.yaml](./release/compose-candidate.yaml) 只替换 Platform，健康检查通过。替换前是 Webhook 管理发布的 `efeb1d9e…`，其 Platform 代码已在 `3170984` 提交，新镜像包含；SES 投递事件会话未提交的 Platform 改动不在镜像中。回退用 Webhook 管理的 compose-candidate.yaml。
- **配置：** [harness](./assets/harness/README.md) 经真实管理接口保存 `TOSS_PAY`：出站地址 `TOSS_API`=`https://pay.toss.im`（沙箱）、脚本 `TOSS_PICK_REFUND`、原子 API `CREATE_PAYMENT`、`GET_STATUS`、`GET_STATUS_REFUND`、`CREATE_REFUND`（均声明 `credentialFields`），流程 `PAY`、`PAY_QUERY`、`REFUND`、`REFUND_QUERY`，校验全部通过。
- **验证账号：** `SBX_MAIN`（`channelAccountId` 900000000000000006，`UAT01`），apiKey 来自对接包记录 0（`sk_test_`）。
- **用例（5/5 PASSED，首轮通过）：** `SBX_PAY_QUERY_NOT_FOUND`、`SBX_REFUND_NOT_EXIST`、`SBX_REFUND_QUERY_NOT_FOUND`（均 `COMMON_INVALID_PARAMETER` 且 msg 以 `orderNo` 结尾，说明 apiKey 被接受）；`SBX_PAY_PENDING`（KRW 1000 建单 → REDIRECT；查询 `PAY_STANDBY` → `PENDING`、1000 KRW；未付款退款 `COMMON_REFUND_ERROR` → `FAILED`；退款查询 `REFUND_NOT_EXIST`）；`SBX_PAY_NOT_KRW`（USD → ERROR，未发请求）。共 9 次渠道调用。回执见 [test-sandbox-receipt.json](./assets/test-sandbox-receipt.json)。
- **凭据检查（按值比对）：** 5 次运行详情、Platform 容器日志、harness 输出均不含 apiKey；轨迹中请求体 apiKey 均为 `[REDACTED]`。
- **回归（16/16 PASSED）：** PayBy 5、PayerMax 4（含下单后查询）、Alipay+ 3、dLocal 4（含下单、撤销）。
- **测试单：** `SBX_PAY_PENDING` 新建 1 笔 `PAY_STANDBY` 单，与探测单一样无法撤销，留待到期；测试 key 不发生资金移动。
- **未执行：** 已付款链路（阶段 4，需要 Toss App 账号）；`CURRENT_IMPLEMENTATION.md` 回写与本阶段文件提交（阶段 5）。

## 5. 阶段 5（2026-10-17）

- **授权：** 用户“可以，跳过第 4 阶段”（阶段 3 通过；回写并提交，不推送）。
- **回写：** `CURRENT_IMPLEMENTATION.md` 新增 Toss Pay 小节（Designed、Implemented / Verified、Platform 与配置 Deployed / Sandbox Verified）。
- **提交：** 私有暂存区，只含本需求阶段 3、5 文件与 `CURRENT_IMPLEMENTATION.md` 的 Toss Pay 小节（该文件中其他会话的未提交改动保留在工作区）；暂存差异经值比对不含 apiKey。未推送。
- **遗留：** D5（退款同步成功）与已付款链路未实测；接 Toss 回调、自动结算另立需求。
