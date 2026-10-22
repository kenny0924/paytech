# Cybersource（Network International）卡收单接入（mor-test 联调沙箱）

- 日期：2026-10-22
- 状态：**范围已确认**（2026-10-22，6 个标准 API，不含 3DS）；阶段 2 现状差异见 [technique.md](./technique.md)，进度见 [delivery.md](./delivery.md)
- 主入口 / Owner：channel-connector（脚本函数、标准 API、渠道协议配置与沙箱验证）
- 关联：[MPGS（NI）接入](../../20261021/channel-connector-MPGS_NI接入/requirement.md)（同一收单方、同一组标准 API）、[统一对外渠道出口决定](../../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)

## 1. 背景

用户：“/Users/kenny/Desktop/channels/I_CYBS_NI 帮我维护一下这个渠道”。

旧网关渠道 `I_CYBS_NI`（Visa Cybersource 网关，收单方 Network International，中东卡收单）已整理为对接包（仓库外 `~/Desktop/channels/I_CYBS_NI`，含测试库真实凭据，不入库）。按 MPGS 的先例，把它接入 mor-test 的渠道执行引擎并用沙箱验证。

- Cybersource 只有服务端卡直付（REST JSON，全部 POST），没有托管跳转页，也没有支付或退款 Webhook，结果全靠查询。
- 每个请求都要做 **HMAC HTTP Signature**：
  - `Digest: SHA-256=<Base64>`；
  - RFC 1123 格式的 `Date`；
  - 5 行签名串，HMAC-SHA256 的密钥要先做 Base64 解码；
  - 拼出 `Signature` 头。

## 2. 已知事实（阅读对接包与代码，未调用任何接口）

| 项 | 内容 |
|---|---|
| 地址 | 沙箱 `https://apitest.cybersource.com`（官方地址；旧 test 库只接内部 mock，从未连过）；生产 `https://api.cybersource.com` |
| 凭据 | 9 条记录中有 7 个币种商户（AED、BHD、EGP、KWD、SAR、USD、ZAR；SAR 与 USD 共用一组），记录 1 缺凭据不可用，记录 0 为 PF 模式。对接包说明：格式上分不出是测试还是生产凭据，须先用 apitest 查单验证 |
| 接口 | 下单 `POST /pts/v2/payments`（`capture=true` 一步扣款，`false` 只授权）、退款 `POST /pts/v2/payments/{id}/refunds`（路径要用渠道支付 `id`，不是我方单号）、支付与退款查询 `POST /tss/v2/searches`（按 `clientReferenceInformation.code` 检索） |
| 状态 | 下单 `AUTHORIZED` 为成功；`DECLINED`、`AUTHORIZED_RISK_DECLINED`、`INVALID_REQUEST`（DUPLICATE 除外）为失败；退款申请只返回受理（`PENDING`），终态靠查询 |
| 3DS | Cardinal DDC + Enrollment + Step-Up + Validate，需要浏览器中间页和回跳，复杂度 C |
| 对接包标出的旧系统缺陷 | 退款查询缺 `sort`、灰度导致退款长期 PENDING、MC 3DS 丢 UCAF、Date 依赖 JVM locale、商户名截断保留了末尾等 |

**引擎现状**（`ScriptFunctions`）：已有 `hmacSha256Base64`（密钥按 UTF-8 文本使用）、`sha256Hex`、`base64Encode`（只能编码 UTF-8 文本）、`nowRfc3339`。缺少三项：

1. 密钥先做 Base64 解码的 HMAC；
2. 输出 Base64 的 SHA-256；
3. RFC 1123 时间。

这三项都不能用现有函数在脚本里拼出来，**必须改 channel-connector 代码**，这一点与 MPGS（只改配置）不同。

另有两处细节，阶段 2 核对：
- 签名串中的路径和 host 不在脚本可读的请求对象里，需要通过脚本参数传入；
- `Host` 头由传输层按地址自动发送。

## 3. 目标（建议，待确认）

在 mor-test 新增渠道 `I_CYBS_NI`（出站地址指向 apitest，标记沙箱），实现与 MPGS 相同的 6 个标准 API，并用渠道沙箱验证跑通：

| 标准 API | Cybersource 接口 |
|---|---|
| `PAY`（卡直付，不含 3DS） | `POST /pts/v2/payments`，`capture=true` |
| `PAY_QUERY` | `POST /tss/v2/searches`，`clientReferenceInformation.code:<attemptId>` |
| `REFUND` | 先查询取得渠道支付 `id`，再 `POST /pts/v2/payments/{id}/refunds` |
| `REFUND_QUERY` | `POST /tss/v2/searches`（补上 `sort`，按退款单号匹配） |
| `AUTHORIZE` | `POST /pts/v2/payments`，`capture=false` |
| `CAPTURE` | 先查询取得授权 `id`，再 `POST /pts/v2/payments/{id}/captures`（旧系统没有这个接口，按官方文档新增） |

代码改动只有一处：channel-connector 新增上述 3 个通用脚本函数并补自动测试，供 Cybersource 系列渠道共用（对接包提到 CYBS、I_CYBS_FISERV、I_CYBS_GEIDEA、I_CYBS_KSAACQ 预计共用同一签名模板）。

## 4. 不做（建议）

- 3DS 全流程（DDC、Enrollment、Step-Up、Validate、回跳）。另一会话的 3DS 全链路需求目前也不含 NI 渠道。
- Apple Pay、Google Pay、网络 token、MIT、PF 模式（`aggregatorInformation`、PF ID）、`VOID`、`PAY_CANCEL`。
- 建渠道账号、MID 和能力，以及 Payment 侧调用（与 MPGS 相同）。
- 旧系统缺陷不照抄，修正方式在设计阶段逐条列出。
- 生产环境、推送。

## 5. 风险与待确认

1. **凭据：** 阶段 2 实测 apitest 上只有 SAR / USD 一组和 PF 一组有效，其余 5 个币种返回 401（[技术方案 1.1](./technique.md#11-本机核对2026-10-22)）。
2. **代码改动要部署到 mor-test**：需要构建并替换 mor-test 的 channel-connector 镜像。该服务另一个会话（统一出口）刚发布过，需要错开并以最新 main 为基线。
3. **Transaction Search 有索引延迟**：刚下的单可能查不到，查询和先查后退款的流程要按“处理中”处理。阶段 3 用写探测确认延迟大小。
4. **测试卡**：用 Cybersource 公布的测试卡（如 Visa 4111 1111 1111 1111）；沙箱的拒付触发方式在阶段 3 确认。

## 6. 验收标准（草案）

1. AC1：channel-connector 新增 3 个脚本函数，自动测试覆盖 `test-vectors.json` 中的 `auth.*` 样本（Digest、签名串、Signature 头逐字节一致）；函数不暴露密钥原值。
2. AC2：mor-test 上 `I_CYBS_NI` 渠道、脚本、原子 API 与 6 条流程保存成功并通过静态校验。
3. AC3：沙箱用例全部 PASSED：卡支付成功 → 查询 → 部分退款 → 退款查询；卡被拒；预授权 → 请款；查询、退款、请款的单不存在。
4. AC4：凭据和卡号原值不出现在仓库、回执、日志与响应中。
5. AC5：已接入渠道（含 MPGS）的沙箱用例回归通过。
6. AC6：`CURRENT_IMPLEMENTATION.md` 回写，需求索引登记。
