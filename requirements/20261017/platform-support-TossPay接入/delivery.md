# Toss Pay 接入交付记录

## 1. 阶段划分（2026-10-17 确认）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md)；直连探测 | 已确认（2026-10-17） |
| 2 | T1 引擎“请求体凭据字段”与 `auto` 测试；提交、发布 mor-test 各自授权 | **已交付，待确认**（未提交、未发布） |
| 3 | mor-test 配置与不需付款的用例；既有渠道回归 | 待定 |
| 4 | （可选）有 Toss 账号时跑已付款链路 | 待定 |
| 5 | 文档回写与提交 | 待定 |

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
- **未执行：** 未提交、未发布 mor-test。
