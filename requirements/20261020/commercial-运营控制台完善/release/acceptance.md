# commercial 运营控制台完善（第二切片）：mor-test 验收清单（草案）

> 给阶段 7 验收 agent 使用。这份文件只列检查项，不含脚本。可以复用首切片的 [stage7_acceptance.py](../../../20261019/commercial-六层产品定价模型/verification/stage7_acceptance.py) 和 [stage7_regression.py](../../../20261019/commercial-六层产品定价模型/verification/stage7_regression.py)，复制到本需求的 `verification/` 后改写。
> 前提：[release.md](./release.md) 第 4 节第 1–6 步已完成，三个容器已是候选镜像，test-activate 已提交，Gateway 快照已刷新。

## 0. 约定

- **入口：** `http://localhost:28080/api/operations/commercial-admin/v1/**`，经 Gateway、ops-bff 到 commercial。辅助模块是 `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py`，设置 `E2E_TARGET=test`。不要打印 Token 或口令。
- **账号 A：** `test-environment/private/access.md` 的 PLATFORM_ADMIN。
- **账号 B：** 经 `POST /iam/v1/roles` 和 `POST /iam/v1/accounts` 新建的合成审批人。合成角色只给 COMMERCIAL_CONTRACT_READ（`900000000000095101`）、COMMERCIAL_PRICING_READ（`…95103`）和 COMMERCIAL_PRICING_APPROVE（`…95105`）。B 首次登录后要改口令，验收结束停用 B 并清空合成角色，做法同首切片。不用 SQL 授权。
- **写请求：** 带 `Idempotency-Key`。价格试算 `POST …/commercial-prices/simulate` 是只读查询，路由 `idempotency_mode=NONE`，不带这个头。
- **既有真实数据：** 合同 `CL-F1105E`，agreement `238132079668301824`，client `237610139979456513`，OE `AE001`，状态 ACTIVE。**只读**，不得修改或删除。
- **合成数据：** 统一带 RUN 前缀，例如 `G8_<RUN>_*` 和 `G8-CLIENT-<RUN>`。所有 ID 写入运行记录 `local-environment/e2e/runtime/commercial-ops2-stage7-<ts>.json`，供清理使用。
- **合成数据准备：** 按首切片的做法完成以下步骤：
  1. 画像三个：具体的 `CARD`（VISA / CREDIT / DOMESTIC）、兜底 `CARD`（无条件）、`APPLE_PAY`（无条件），全部激活；
  2. 一份 CLIENT 合同 `G8-CLIENT-<RUN>`（OE `AE001`，clientId 用合成值，不用 CL-F1105E 的 client），完成签署并 activate；
  3. 产品 `[ACQUIRING]`；
  4. 开通 `CARD_VISA`（CURRENCY AED、USD）和 `APPLE_PAY`（CURRENCY AED）；
  5. 方案 `G8-CLIENT-PRICING-<RUN>`，版本 V1 只给 CARD 价格：AED / USD × 具体 / 兜底，共 4 条。

  后续步骤再在这个基础上构造 V2。
- 每一项记录 HTTP 状态码与关键字段，写入 `verification/stage7-acceptance.md`。

## 1. F1 合同列表与搜索

| # | 请求 | 期望 |
|---|---|---|
| F1-1 | A `GET /agreements?clientId=237610139979456513` | 200，`items` 含 `CL-F1105E`（ACTIVE、AE001），`total` ≥ 1 |
| F1-2 | A `GET /agreements?agreementNo=CL-F11` | 200，按前缀命中 `CL-F1105E` |
| F1-3 | A `GET /agreements?status=ACTIVE&agreementType=CLIENT&entityNo=AE001&page=1&size=10` | 200，`page=1`、`size=10`，`items` 不超过 10 条且全部为 ACTIVE / CLIENT / AE001，`total` ≥ 2（真实加合成）；分页从 1 起，`size` 只允许 10、20、50、100、200（`page=0` 或 `size=1` 为 400） |
| F1-4 | A `GET /agreements?status=DRAFT&status=PENDING_SIGNATURE` | 200，可重复 `status` 参数被接受，结果状态只在这两个值中 |
| F1-5 | A `GET /agreements?size=201` | 400，`INVALID_REQUEST`（`size` 上限 200） |
| F1-6 | A `GET /agreements?foo=1` | 400：Gateway 或 BFF 的查询参数白名单拒绝未知参数 |
| F1-7 | 列表行的 `agreementId` 进入 `GET /agreements/{id}` | 200，与列表行一致 |

## 2. F2 审批工作台

| # | 请求 | 期望 |
|---|---|---|
| F2-1 | A `GET /dashboard`（准备数据前） | 200，`metrics` 恰好 5 项：`PENDING_APPROVAL_VERSIONS`、`SCHEDULED_VERSIONS`、`DRAFT_AGREEMENTS`、`PENDING_SIGNATURE_AGREEMENTS`、`ACTIVE_AGREEMENTS`。`ACTIVE_AGREEMENTS` ≥ 1，因为有 CL-F1105E |
| F2-2 | A submit V1 后再读 `GET /dashboard` | `PENDING_APPROVAL_VERSIONS` 比 F2-1 多 1；`approvalQueue` 含 V1，带 maker 和 `effectiveFrom` 字段 |
| F2-3 | B `POST /commercial-terms/versions/{V1}/approve` | 200，APPROVED；`PENDING_APPROVAL_VERSIONS` 回到 F2-1 的值 |
| F2-4 | V2（见 F3）submit 后，B `POST …/versions/{V2}/reject` | 200，状态按 commercial 状态机变化（记录实际值，通常回到可编辑状态）；`approvalQueue` 不再含 V2。I2-gaps G1：驳回接口目前不接收原因，页面只确认，不记为失败 |
| F2-5 | V2 重新 submit，B approve，A schedule（生效 = 当前 + 30 秒）后读 `GET /dashboard` | `SCHEDULED_VERSIONS` 多 1，队列或卡片中可以看到 V2 的生效时间 |
| F2-6 | 只有 COMMERCIAL_PRICING_READ 的会话读 `GET /dashboard` | 403。工作台绑定 COMMERCIAL_CONTRACT_READ。可选：需要再建一个只读合成角色 |

## 3. F3 价目总览与版本对比

| # | 请求 | 期望 |
|---|---|---|
| F3-1 | A `GET /commercial-terms/plans?agreementId=<合成合同>` | 200，含合成方案；方案行带当前生效或已排期的版本 ID |
| F3-2 | A `GET /commercial-terms/versions?planId=<方案>&status=APPROVED&status=SCHEDULED&page=1&size=50` | 200，含 V1（APPROVED）；版本行带 maker / checker、`effectiveFrom` 和 `priceCount=4` |
| F3-3 | 构造 V2：`POST /commercial-terms/plans/{planId}/versions`，`copyFromVersionId=V1`；`PUT …/versions/{V2}/prices` 内容为原 4 条加 AED × APPLE_PAY 1 条，并把具体 AED 的 `CAPTURE_PCT` 由 0.025 改成 0.022 | 200 / 200 |
| F3-4 | A `GET /commercial-terms/versions/{V1}/diff/{V2}` | 200。`added` 有 1 条（AE001 / AED / APPLE_PAY 画像）；`changed` 有 1 条（具体 AED 的 CAPTURE_PCT 0.025 → 0.022）；`removed` 为 0；`unchanged` 为 3；`versionDifferences` 只比较 roundingMode / pricingModel / feeTaxTreatment，复制版本未改口径时为空数组 `[]` |
| F3-5 | A `GET /commercial-terms/versions/{V2}/diff/{V1}` | 200，与 F3-4 方向相反：`removed` 1、`added` 0 |
| F3-6 | A `GET /commercial-terms/versions/{V1}/diff/<不存在的 ID>` | 404 或 400，带 Owner detail（见 F9），不是 500 |

## 4. F4 产品目录

数据与 `commercial-initial-data.sql` 一致。

| # | 请求 | 期望 |
|---|---|---|
| F4-1 | A `GET /products` | 200，7 个产品：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM |
| F4-2 | A `GET /products/capabilities`；再加 `?productCode=ACQUIRING` | 200，共 11 项；ACQUIRING 下 6 项，`CARD_VISA` 带 `CARD_BRAND=VISA` |
| F4-3 | A `GET /products/fee-events`；再加 `?productCode=ACQUIRING&termType=CLIENT_PRICING` | 200，共 21 项；带过滤时为 AUTH、CAPTURE、SALE、REFUND、CHARGEBACK、TOKEN_STORED 共 6 项 |
| F4-4 | 只有 COMMERCIAL_PRICING_* 而没有 CONTRACT_READ 的会话读 `GET /products` | 403（可选，同 F2-6）。I2-gaps 已注明：这种情况下价格抽屉退化为手输 |

## 5. F5 变更历史

| # | 请求 | 期望 |
|---|---|---|
| F5-1 | A `GET /business-changes?aggregateRootType=AGREEMENT&aggregateRootId=<合成合同>&page=1&size=50` | 200，条目按 `versionNo` 倒序，每条带 `createBy`（A 的 subject）、`createTime`、`operationType` 和 `meta`。相邻版本有 `changedFields`（例如 status），首版把全部字段视为变化 |
| F5-2 | A `GET /business-changes?aggregateRootType=COMMERCIAL_TERM_PLAN&aggregateRootId=<方案>` | 200，含 V1 / V2 版本记录的 submit、approve、reject、schedule、activate 各步；`previousVersionNo` 和 `previousData` 与上一快照一致 |
| F5-3 | A `GET /business-changes?aggregateRootType=AGREEMENT&aggregateRootId=238132079668301824` | 200，CL-F1105E 的历史只读可见 |
| F5-4 | A `GET /business-changes`（缺 `aggregateRootId`） | 400 |

## 6. F6 费率卡预览

| # | 请求 | 期望 |
|---|---|---|
| F6-1 | A `GET /rate-cards?clientId=237610139979456513&asOf=<now>&currency=USD` | 200。CL-F1105E 没有条款版本时 `termVersions` 为空，合同块仍在；记录实际值 |
| F6-2 | V2 激活后（F2-5 到点，A `POST …/versions/{V2}/activate`），A `GET /rate-cards?clientId=<合成 client>&asOf=<now>&currency=AED` | 200。`termVersions` 含 V2（ACTIVE、CURRENT）。CARD_VISA / AED 有具体和兜底两个画像，APPLE_PAY / AED 有一个画像，组件按计费事件分组 |
| F6-3 | A `GET /rate-cards?clientId=<合成 client>&asOf=<now>&bad=1` | 400：查询参数白名单只有 clientId、asOf、currency |

## 7. F7 价格试算

| # | 请求 | 期望 |
|---|---|---|
| F7-1 | A `POST /commercial-prices/simulate`，不带 Idempotency-Key。body：`termVersionId=V2`、`entityNo=AE001`、`agreementId`、`termType=CLIENT_PRICING`、`currency=USD`、`clientId`、`contextAsOfTime=<now>`、`paymentFacts={paymentMethodCode:CARD, dimensions:{CARD_BRAND:VISA, FUNDING_TYPE:CREDIT, ISSUER_REGION:DOMESTIC}}`、`amount="100.00"` | 200。`price.pricingProfileCode` 为具体画像；`illustration` 带 amount、currency、`fractionDigits=2` 和 `feeEvents` 示例费用，例如 CAPTURE = 100 × 0.025 + 1.00 |
| F7-2 | 同上，但去掉 `FUNDING_TYPE` | 422，`PRICING_FACTS_INCOMPLETE`，detail 为「缺少定价事实: FUNDING_TYPE」 |
| F7-3 | 同上，但 `CARD_BRAND=AMEX` | 200，命中兜底画像 |
| F7-4 | body 超过 8192 字节 | 413 或 400，由 Gateway 的 body 上限拦截 |

## 8. F8 导航与仪表盘

| # | 请求 | 期望 |
|---|---|---|
| F8-1 | A `GET /api/operations/authorization/v1/navigation` | 200。「商务」下有 8 个菜单，按 sort 排序：工作台 5、合同 10、价目 20、费率预览 22、价格试算 24、定价画像 30、产品目录 40、变更历史 50。另有首切片的 4 个按钮 |
| F8-2 | B 的导航 | 只含 B 权限可见的菜单：工作台、合同、产品目录、变更历史（CONTRACT_READ）；价目、费率预览、价格试算、定价画像（PRICING_READ）；以及审批按钮。不含维护按钮 |
| F8-3 | Web `GET /` | `index.html` 引用新入口资源（发布时记录名字）。`/commercial/workbench`、`/commercial/rate-cards`、`/commercial/price-simulator`、`/commercial/catalog`、`/commercial/history` 都返回 200 |

## 9. F9 错误信息透传

| # | 请求 | 期望 |
|---|---|---|
| F9-1 | V1 已 APPROVED，开通项含 APPLE_PAY / AED 但 V1 没有它的价格。A `POST /commercial-terms/versions/{V1}/schedule`（生效 = 当前 + 60 秒） | 400，`BUSINESS_CHECK_FAILED`。Problem `detail` 必须是 Owner 原文，含「激活校验」和 `AE001/APPLE_PAY/AED`，不是通用的「请求参数不正确」或「业务校验失败」 |
| F9-2 | A 提交 V2 后，A 自己 `POST …/versions/{V2}/approve` | 400，detail 含「审批人不能是提交人」 |
| F9-3 | 用过期的 `expectedChangeVersion` 调用任一写命令，例如对已 ACTIVE 的合成画像以 `expectedChangeVersion=5`（≠ 当前版本-1）再 activate。注意 `expectedChangeVersion=当前版本-1` 会被 `ActivatePricingProfileProcessor` 视为原请求重放而返回 200，不是过期 | 409，`CONCURRENT_REQUEST`，detail 为 commercial 原文 |
| F9-4 | 非商务路由的业务拒绝，例如以过期版本号对任一既有运营写接口 | detail 仍是 BFF 的通用文案，证明开关只在商务工厂打开。找不到无副作用的请求时，只记录「代码核对：`withOwnerProblemDetail()` 只在 `CommercialClientConfiguration` 中调用」 |

## 10. 直连与运行观察

- commercial 直连 `GET :19108/actuator/health` 返回 UP。用一次性探针容器接入 `mor-test-backend`，做法同首切片。
- 自替换起，commercial、ops-bff、ops-web、Gateway、Platform 的日志 ERROR 都是 0，并且没有管理员口令或 Bearer Token 明文。
- `mor_commercial.t_compensation_event` 最终为 0 行：合同状态与版本激活事件已发布。
- XXL 任务 16（版本激活）和 17（补偿扫描）在验收期间的定时调度都是 200 / 200。

## 11. 页面只读回归

- 用首切片的 `stage7_regression.py`，复制到本需求 `verification/`，把截图清单加上新商务五页。它从**脚本所在仓库**的 `operations-console-web/src/navigation/pageCapabilities.ts` 读取页面列表，所以必须在含发布源码的树里运行，也就是 rebase 后的 slice2 或合并后的 main。
- 期望 **42/42**，即首切片的 37 页加商务新五页；如果 rebase 时 main 增加了页面，以实际计数为准。没有 ≥ 400 响应、失败请求或控制台错误。每页间隔 7 秒，避开登录上下文限流。
- 截图放到 `assets/stage7/`，至少包括工作台、合同列表（含 CL-F1105E）、价目总览、版本对比（V1 对 V2）、产品目录、变更历史、费率预览、价格试算，以及 F9-1 的错误提示。

## 12. 清理

- 按运行记录中的 ID，用 `verification/acceptance-cleanup-test.sql` 删除合成数据：先 SELECT 核对，再在同一事务内 DELETE。删除范围包括画像 3 个及其条件、合成合同及其参与方 / 文件 / 产品 / 开通 / 约束、方案、V1 / V2 版本、价格、组件，以及这些聚合根的 BCR 记录和版本。完成后 `mor_commercial` 应只剩目录数据和 CL-F1105E 及其 BCR，与备份前的行数一致。
- 合成审批人员要停用，合成角色要清空权限；Platform 审计不回滚。
