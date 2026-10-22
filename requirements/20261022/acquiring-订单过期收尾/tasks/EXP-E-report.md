# EXP-E 报告：验收脚本过期场景与文档

> 执行代理报告，2026-10-22。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached HEAD，基线 `42879401` + EXP-A/B/C 未提交改动，EXP-D 并行中）。未提交、未推送、未建分支；未执行 Maven；未连接 mor-test、未执行任何 SQL、未部署。未触碰 EXP-D 的 Handler、配置类、`application.yml` 与 acquiring 测试模块。

## 1. 结论

- `order.expired` 验收场景已写入**现行 V2 验收脚本**，`python3 -m py_compile` 通过，未对 mor-test 运行。
- 文档三处已改：Commerce 数据库原因目录、requirements-site COM-05、acquiring README 的 Webhook 段。
- requirements-site 测试 137 例中 127 通过、10 例失败，数量与 3DS 需求记录的既有基线一致，失败断言均不涉及本卡改动（见第 5 节）。
- 最需要主会话知道的偏离：任务指定的 V1 脚本在导入时即 `raise SystemExit`（mor-test 已切到 V2），所以场景写在 V2 副本，V1 未改。

## 2. 已读路径

- `requirements/20261022/acquiring-订单过期收尾/tasks/cards.md`（EXP-E 行、1.1 裁决）
- 同目录 `technique.md` 2.1 R7、2.2、2.4 D5、3.3.4、3.3.5、3.6；`requirement.md` B5、B6、AC-12、Q3、Q4
- `tasks/EXP-C-report.md`（载荷字段、`EXPIRY_REACHED`、`expiredAt = expires_at`、本地收尾分支条件）
- `requirements/20261017/commerce-ClientAPI测试环境联通/verification/client_api_flow.py`（V1，已停用）
- `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/client_api_flow.py` 与 `webhook_flow.py`（`create_checkout`、`order_row`、`tasks`、`received`、`check_delivery`、`compare`、`safety`、`gap`、`summary`）
- `agent-docs/database/COMMERCE_DATABASE.md` 5.1
- `requirements-site/content/requirements.ts` COM-05
- `docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md` 第 13 节
- `paytech/acquiring/README.md`「Client Webhook 业务事件」；`api/openapi/client-v1.yaml`（无 Webhook 事件描述，不改）
- 核对用源码：`GetOrderResponseBody`（收银台地址字段名为 `checkout.url`）、`GetOrderProcessor`（EXPIRED 不返回地址、无投影为 `NOT_STARTED`）、`CreateCheckoutProcessor`（期限按受理时刻须在 5 分钟到 24 小时）、`ClientWebhookEvent.orderCancelled / orderExpired`、Platform `WebhookEventType`（`order.expired` 已可订阅）、`test-environment/compose.applications.yaml`（acquiring-service 调用方已允许 `order.expired`）

## 3. 改动文件

| 文件 | 改动 |
|---|---|
| `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/client_api_flow.py` | 新增过期场景与三个辅助函数；端点订阅加 `order.expired`；`main()` 接入；接收端总数 test 为 4、本机仍为 3；文件头补场景与前置 |
| `agent-docs/database/COMMERCE_DATABASE.md` | 5.1 `commerce_order` 段后新增 Status Reason Catalog 表：`CLIENT_REQUESTED`、`EXPIRY_REACHED`，各自终态与写入时机 |
| `requirements-site/content/requirements.ts` | 只在 COM-05 文案末尾追加过期行为一句，编号与其它条目未动 |
| `paytech/acquiring/README.md` | Webhook 段补 `order.expired` 的写入位置、cancelled / expired 事件表（Event ID、载荷字段）、过期语义；顺带修正两处陈旧文字（见第 6 节第 3 项） |

## 4. 脚本场景

场景只在 `E2E_TARGET=test` 运行。本机没有 XXL-Job 调度中心，本机运行时记一条 GAP，不计入通过数。位置在 `wf.flow_cancel` 之后、支付场景之前。

**步骤**

1. 用 `checkout_body("expired")` 加 `expiresAt` 建结账。期限为当前时刻加 5 分钟再加 60 秒余量，余量原因见第 6 节第 2 项。
2. `rewind_expires_at`：用 `wf.acquiring_sql` 执行下面这一句，按 `order_no` 定位，只改 `expires_at`，不改版本、状态和 `update_time`。函数注释写明这是 mor-test 验收专用。

   ```sql
   UPDATE t_commerce_order SET expires_at=UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE
   WHERE order_no='<orderNo>' AND order_status IN ('CREATED','OPEN'); SELECT ROW_COUNT();
   ```

3. 每 5 秒 GET 一次订单，最长 90 秒，直到 `orderStatus == EXPIRED`。
4. 断言与核对，见下表。
5. 调 Cancel，等 3 秒后核对没有副作用。

**断言清单**（`record` 计入现有汇总，`wf.summary` 输出合计）

| 步骤名 | 判断 |
|---|---|
| 过期：建结账 | HTTP 201 且有 `orderNo` |
| 过期：回拨 expires_at | `ROW_COUNT() = 1` |
| AC-12 GET 为 EXPIRED | `orderStatus=EXPIRED`、`checkout.url` 为 null、`checkout.status=EXPIRED`、`paymentStatus=NOT_STARTED`；证据含等待秒数 |
| AC-12 库内状态 | 订单 `EXPIRED`、`status_reason_code=EXPIRY_REACHED`、会话 `EXPIRED` |
| `wf.check_delivery`（复用，6 步） | 送达 1 次；端点密钥签名校验通过；`eventId == evt_<orderNo>_expired`、`eventType`、顶层键；acquiring 任务行已删除；Platform 事件唯一且来源 `acquiring-service`、投递 SUCCEEDED；接收内容等于 Platform 冻结 Envelope 且 `sourceVersion = object.version`；载荷无内部 ID 与个人信息 |
| AC-12 order.expired data.object 一致 | `orderNo`、`clientOrderId`、`orderStatus`、`checkoutStatus` 与 GET 一致；`expiredAt` 与 GET `expiresAt` 为同一时刻；`version` 与库内订单版本一致 |
| AC-12 原因码与 expiredAt | `reasonCode=EXPIRY_REACHED`，`expiredAt` 等于 `expires_at` |
| AC-12 过期后取消 | HTTP 409 且 `code=ORDER_NOT_CANCELLABLE` |
| AC-12 取消无副作用 | 订单仍 EXPIRED、版本不变；无 `evt_<orderNo>_cancelled` 任务与送达；`order.expired` 仍只送达 1 次 |
| 接收端总数 | test 为 4（paid、cancelled、expired、refund.created），本机为 3 |

`version` 与库内订单比对，因为 GET 订单响应没有 `version` 字段。这与现有 `order.cancelled` 场景的做法相同。

**语法检查**

```text
python3 -m py_compile client_api_flow.py   -> 通过
```

## 5. requirements-site 测试

工作目录没有 `node_modules`。我临时链接了主工作区的 `requirements-site/node_modules` 来运行 `npm --prefix requirements-site test`，运行后已删除链接和构建产物（`.next`、`.vinext`、`.wrangler`、`dist`、`next-env.d.ts`）。

```text
ℹ tests 137
ℹ pass 127
ℹ fail 10
```

3DS 需求 `delivery.md` 记录的基线是 137 例中 10 例失败，并且在干净的 `3a4574c7` 上同样失败。本次失败数量相同。我逐一查看了失败断言的位置，没有一处读取 COM-05 文案或新增的原因目录表：

- `checkout renders the immutable order context…`：domain-decisions 第 63 行，断言 Session 置 OPEN 文案。
- `Payment owns refund capacity…`：domain-decisions 第 273 行。
- `Commerce Client API V1 freezes exactly ten…`：断言决定文件中的 10 条路由列表。
- `Commerce authority freezes the generated 15-table…`：表标题检查已通过，失败在第 903 行，断言 `CURRENT_IMPLEMENTATION.md` 中的 Commerce 统计行。
- 其余 6 例在 site-content 第 923、947、954、1108、1596、1674 行，分别断言 CURRENT_IMPLEMENTATION、PROJECT_CONTEXT、导航、测试指南与开发规范文本。

判定：10 例均为既有失败，没有新增失败。

## 6. 偏离

1. **场景写在 V2 脚本，V1 未改。** 任务指定的 `requirements/20261017/commerce-ClientAPI测试环境联通/verification/client_api_flow.py` 在第 31 行无条件退出，提示改用 V2 同名脚本。V1 还读 `mor_commerce` 并按 commerce-service 登记，不能再对 mor-test 运行。V2 脚本就是 2026-10-18 起的现行 mor-test 验收入口（`E2E_TARGET=test`）。
2. **期限多加 60 秒余量。** 服务端按受理时刻要求期限至少 5 分钟。客户端算出的“当前加 5 分钟”到达服务端时会略少于 5 分钟，可能被 400 拒绝。期限之后会被 SQL 回拨，所以余量不影响验收时长。
3. **README 两处陈旧文字顺带修正。** “Platform 在 `webhook.http.callers.commerce-service` 中允许上述三种事件”改为 `acquiring-service` 与“上述事件”。这与现行 compose 一致，调用方在 V2 已改名。“`order.expired` 未实现”一句已删去。
4. **收银台地址字段名。** 任务写的是 `checkout.checkoutUrl`，实际 GET 字段是 `checkout.url`，脚本按实际字段断言。
5. **API 文档位置。** `api/openapi/client-v1.yaml` 和 `docs/api` 都没有 Webhook 事件描述，`order.cancelled` 只在 README 的 Webhook 段描述，所以 `order.expired` 也补在那里。设计规格第 13.5 节仍写“2026-10-17 已实现三种”，属于权威设计中的实现状态句，本卡未改。建议主会话在阶段 8 决定是否同步一句。

## 7. 运行前置（阶段 7 用）

- acquiring、payment 为含本需求改动的镜像。
- XXL-Job 已为 acquiring-service 执行器登记 `orderExpiryScan` 任务，每分钟一次，分片广播，并已启用。场景等待上限 90 秒依赖“每分钟一次”。
- 验收接收端已启动：`compose.sh -f compose.acceptance.yaml --profile acceptance up -d webhook-receiver`。
- Platform 侧无需改动：`WebhookEventType.ORDER_EXPIRED` 可订阅，compose 中 acquiring-service 调用方已允许 `order.expired`。仍需在部署前核对 mor-test 运行中的 Platform 环境变量确实包含该值。
- 该场景让 mor-test 运行时长增加约 1—2 分钟。

## 8. CURRENT_IMPLEMENTATION 台账条目草稿

未直接修改该文件。EXP-D 的测试数在本报告完成时尚未产出，用占位表示。

```markdown
## acquiring 订单与会话过期收尾（2026-10-22，未部署，未提交）

- **范围：** [需求](<../requirements/20261022/acquiring-订单过期收尾/requirement.md>)、[技术方案](<../requirements/20261022/acquiring-订单过期收尾/technique.md>)、[交付记录](<../requirements/20261022/acquiring-订单过期收尾/delivery.md>)。无 DDL、无 Platform 路由注册；过期终态时刻取 `expires_at`，原因码 `EXPIRY_REACHED` 登记到 [Commerce Order Status Reason Catalog](./database/COMMERCE_DATABASE.md)。
- **Implemented：** payment 终止支付意图增加原因（`ORDER_CANCELLED` / `OPERATING_ENTITY_CLOSED` / `ORDER_EXPIRED`，按原因落 CANCELLED / EXPIRED），新增按来源查询支付意图接口；acquiring `PaymentTerminationClientImpl` 取代固定实现，Cancel 传 intentId 与原因、ALREADY_PAID 返回 409；到期扫描 `ScanExpiredOrdersProcessor`（游标分页、按 `order_id` 取模分片）与单单收尾 `ExpireOrderProcessor`（订单锁 TRY_LOCK，与支付提交同锁）：无 Intent 且无接受证据本地收尾，否则以 `ORDER_EXPIRED` 终止，TERMINATED / ALREADY_TERMINATED / 404 后同一事务把订单、会话、投影置 EXPIRED 并登记 `order.expired`（`evt_<orderNo>_expired`），PENDING 保持 OPEN 并在超过 <EXP-D 配置值，默认 24 小时> 后 WARN，ALREADY_PAID / REVERSAL_REQUIRED 不改写，Payment 503 下轮重试；XXL Handler `orderExpiryScan`（`OrderExpiryJobHandler`，分片参数，`OrderExpiryProperties`）。验收脚本 V2 `client_api_flow.py` 新增过期场景（仅 `E2E_TARGET=test`）。
- **Verified：** payment `auto` <EXP-A 数>（含 `TerminateIntentTest`、`GetIntentBySourceTest`）；acquiring `auto` <EXP-D 终值>（含 `ExpireOrderTest`、`ClientWebhookOrderExpiredTest`）；全 Reactor `test-compile` <结果>；合并门禁 arch-rules <数>、messaging <数>；requirements-site 137 例中 10 例失败（既有）。mor-test 验收：<待阶段 7>。
- **Deployed：** 未部署。部署需 acquiring、payment 新镜像，并在 XXL-Job 为 acquiring-service 登记启用 `orderExpiryScan`（每分钟、分片广播）。
- **Pending / 遗留：** 提交、推送、部署另行授权；扫描查询生产数据量下的执行计划未验证（按 `order_id` 排序加 `expires_at` 范围过滤）；设计规格第 13.5 节“已实现三种”状态句待同步。
```

## 9. 未完成项

- 卡内工作已全部完成。
- 场景没有在 mor-test 上实际运行，按要求留给阶段 7。
- 台账条目中的测试数待 EXP-D 与合并门禁结果回填。
