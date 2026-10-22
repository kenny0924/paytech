# 3DS 全链路 + 卡 BIN 库 · mor-test 验收计划（阶段 6 编写，阶段 7 执行）

> 发布清单见 [release.md](./release.md)。本计划参照[阶段 5 报告](../verification/stage5-report.md)的 AC 映射，只列 mor-test 能验证的内容；mor-test 做不到的项注明沿用的阶段 5 / 阶段 4 证据。
> 验收脚本在阶段 7 由主会话按本计划从阶段 5 脚本改写（`stage5_3ds.py`、`stage5_bin.py`、`stage5_bin_screens.mjs`、`stage5_leak_scan.py`），写到 `release/acceptance/`；本阶段没有写脚本、没有对 mor-test 做任何写入。

## 1. 前提与 mor-test 的限制

| 限制 | 影响 | 处理 |
|---|---|---|
| mor-test 没有合成 PayerMax（无 `PAYERMAX_MOCK` 渠道、无 mock 容器） | 挑战、查单结果不能由脚本控制 | 3DS 正向链路只能用 PayerMax UAT 真实沙箱（AC-12，需要决定 N2）；其余 3DS 分支沿用阶段 5 证据 |
| `DEMO_NI` 只有演示数据：没有任何标准 API 实现，端点为 `demo-ni.example.test` | 走到渠道调用就会失败 | 只用它验证“不发渠道请求”的分支（AC-02） |
| mor-test 没有公网入口，Nginx 没有回调 location | PayerMax 真实通知到不了 mor-test | AC-07 正向（真实签名通知）为遗留；mor-test 只验 Gateway 安全链与验签拒绝（反向），探针容器直连 `api-gateway:18080` 并自带 Nginx 应写的 `X-Entry-Channel: CHANNEL_CALLBACK`、`X-Real-IP` |
| `mor_payment` 当前为空；Payment / BIN / Risk 的验收数据都没有业务删除接口 | 验收会留下合成数据 | 第 4 节列出保留项；全部用合成标识（`c3ds-<run>` 前缀） |
| 支付点 ACTIVE 发布为 release 11（R-11：金额 > 500 → THREE_DS_REQUIRED） | 新版本上线后，金额 > 500 且 MID 未声明 `SECURITY.3DS_MODE` 的支付会失败 | 见需要决定 N5；验收按现状利用这一规则（AC-01、AC-02） |

驱动方式：
- Payment 链路：沿用阶段 5 T1 做法，在接入 `mor-test-backend` 的一次性探针容器（`E2E_PROBE_IMAGE=<Platform 候选镜像>`，`PrivateHttpProbe`）内以 `X-Caller-Service: acquiring-service` 直连 Payment 内部接口（建 Intent → 申请卡工具 → pay → 提交查询 / 刷新）。mor-test 的 internal-auth 除 Platform 外都是 OFF，与阶段 5 一致。
- 运营页面：`http://localhost:28080`，`operator@example.test`（口令见 `test-environment/private/access.md`，不入库）。
- 只读核对：`scripts/apply_sql.sh <备份目录> postcheck` 与按需的只读 SELECT。

## 2. 3DS 全链路

| AC | mor-test 做法 | 期望 | 不做时的证据 |
|---|---|---|---|
| AC-01 | AC-12 那笔 PayerMax UAT 交易（金额 > 500 USD 触发 R-11） | Risk 评估快照 `THREE_DS_REQUIRED`；PAY 请求带 `threeDsRequired=true`、渠道请求 `dynamic3DS=do3DS`；动作只有 PAY_RISK、PAY，无 AUTHENTICATE | 阶段 5 AC-01 通过 |
| AC-02 | 探针：在 `DEMO_NI`（两个 MID 都没有 `SECURITY.3DS_MODE`）下建 600.00 USD 的卡支付 | PAY FAILED `THREE_DS_CHANNEL_UNAVAILABLE`；`t_channel_interaction` 无新增（0 渠道请求） | 阶段 5 AC-02 |
| AC-03 | AC-12：PayerMax 返回的交互类型经 Payment → 提交查询如实返回 | `interaction.type` 为 PayerMax 实际返回值（预计 THREE_DS_CHALLENGE），跳转地址存在 | REDIRECT 与收银台层：acquiring auto 测试（阶段 4） |
| AC-04 | AC-12：完成验证后不调用刷新，等待 `PAY_RESULT_QUERY`（XXL `compensationEventScan` 每 30 秒） | Action / Attempt / Intent SUCCEEDED，`payment.events` 发出 PaymentSucceeded 1 条 | 阶段 5 AC-04 |
| AC-05 | 不做（UAT 不可控地制造失败；可选：UAT 挑战页选择失败 / 放弃，若 PayerMax 提供） | — | 阶段 5 AC-05 |
| AC-06 | 可选：AC-12 完成验证后改用刷新接口（5 秒内调用 3 次） | 只多 1 次 PAY_QUERY（`t_channel_interaction` 计数） | 阶段 5 AC-06 |
| AC-07 | 反向：探针向 `POST /api/channel-callbacks/v1/PAYERMAX/{channelAccountId}` 发一条随意签名的 PayerMax 形通知 | Gateway 放行（非 403）、channel-connector 401 `CHANNEL_CALLBACK_SIGNATURE_INVALID`；`t_channel_callback` 记 REJECTED；无 MQ 发布；交易不变。正向（真实签名通知）为遗留 | 阶段 5 AC-07 正向（合成密钥签名） |
| AC-08 | 不做（需要可控的通知与查单并发） | — | 阶段 5 AC-08 |
| AC-09、AC-10 | 不做（需要回拨时间） | — | 阶段 5 AC-09、AC-10 |
| AC-11 | AC-12 之后检索十个服务 `docker logs --since <发布开始>`、`payment.events` / `channel-connector.events` 捕获、补偿表、回调表、动作表 | 跳转主机、`acsSecret=` 类参数、UAT 测试卡号 0 命中 | 阶段 5 AC-11 |
| AC-12 | PayerMax UAT 测试卡人工走通一笔 3DS：探针建单 → 浏览器打开跳转地址完成验证 → 回到 returnUrl → 刷新 / 等待查单 | Attempt SUCCEEDED；同时记录：`dynamic3DS` 未开通时 PayerMax 的实际行为、查单返回的状态字段 | 无（阶段 5 未执行）。前置见需要决定 N2 |

## 3. 卡 BIN 库

| AC | mor-test 做法 | 期望 |
|---|---|---|
| AC-01 | 运营页「卡 BIN 库」导入合成 CSV（8 行，含 `40128888` 8 位与 `401288` 6 位号段，发卡国含 AE / GB / NG） | Platform 直传 MinIO → 扫描 `CARD_BIN_CSV` → v2 VALIDATING → VALID；查询仍为 v1 |
| AC-05 | 导入含错误行的 CSV 与表头错误 CSV | v3 INVALID（行错误明细，类卡号前缀不回显）、v4 文件级失败；激活 400 |
| AC-02、AC-03 | 激活 v2；探针建卡工具 | 新工具快照为 v2 属性与版本号；8 位优先；激活前的工具快照不变 |
| AC-04 | 探针用未识别 BIN 建工具 | 快照属性为空、版本号有值；交易不因此失败（停在 AC-02 的 3DS 失败或渠道失败前） |
| AC-06 | 回切 v1 | 激活记录类型 ROLLBACK、操作人；新工具为 v1 属性 |
| AC-07 | Card 交易工具响应；已存卡列表（若 mor-test 有已存卡则只读查看，否则沿用阶段 5） | 返回卡属性快照 / `cardBrand` |
| AC-08 | 探针支付后查 `t_payment_instrument_token` 8 列与 `t_transaction_risk_assessment` 3 列 | 冻结值与 BIN 库一致。“规则按发卡国命中”需要新 Risk 发布（需要决定 N7），默认不做，沿用阶段 5 |
| AC-09 | 默认不做（要改 MID 能力 `scheme_code`，mor-test 现有 3 条能力全为 `*`）；若 N2 建了 PayerMax MID，可只读核对路由候选 | 沿用阶段 5 AC-09 |
| AC-10 | AC-12 或 AC-04 之外的成功支付发出的 PaymentSucceeded | `schemaVersion=2`，追加 `cardBrand / fundingType / cardLevel` |
| AC-11 | 同 3DS AC-11 的检索，另查导入错误明细 | 无完整卡号 |
| AC-12 | 浏览器冒烟（改写 `stage5_bin_screens.mjs`）：摘要、BIN 查询、版本详情、导入弹窗、激活 / 回切确认弹窗、筛选 | 控制台错误 0、4xx/5xx 0（含 S5-01：首次加载 0 个 429）；截图写 `release/acceptance/screens/` |
| 并发 | 两个运营请求同时激活 | 一成一败（409） |
| BIN 页面布局（N6 裁决 B） | 阶段 7 新增检查：ops-web 候选带上数据查询页面体验调整后的 `dataQuery.css`，BIN 页面引用该样式（阶段 5 验收的是旧样式）。AC-12 浏览器冒烟时逐屏查看摘要、BIN 查询表、版本详情、导入 / 激活 / 回切弹窗，并顺带打开数据查询「数据源」「查询模板」页 | BIN 页面布局在新数据查询样式下正常（无错位、溢出、遮挡，按钮与筛选可用）；数据查询两页与现网 `5a9eb12d` 一致；截图写 `release/acceptance/screens/` |

## 4. 回归

- 运营台：登录、导航、工作流 4 页、存储管理、数据查询、渠道管理列表与渠道详情（含 CYBS / MPGS 标准 API 页，确认 `paymentChannelRef` 仍在）、Gateway 配置页。
- 客户控制台：登录与入驻文件上传 / 下载（文件多存储第二段链路，Platform 被替换）。
- channel-connector：沙箱用例在 `configure_3ds.py` 修改标准 API PAY 后会变为“需重新验证”（预期）；I_CYBS_NI / I_MPGS_NI 的 AUTHORIZE / CAPTURE 沙箱各重跑一条（code-mor-46 回归），结果与发布前一致。
- 十个服务 healthy、重启 0、验收期间日志 ` ERROR ` 0（已知可解释的除外，逐条记录）。

## 5. 合成数据与清理

| 数据 | 处理 |
|---|---|
| BIN 库 v2—v4 版本、导入错误明细、激活记录 | 保留（没有删除接口）；验收结束时回切到种子 v1，使后续交易回到种子属性 |
| 上传的 CSV（Platform 文件、MinIO 对象） | 保留（文件服务不提供删除） |
| `mor_payment` 合成 Intent / Attempt / Action / Token、`mor_risk_decision` 评估快照、`t_channel_interaction` | 保留；全部带 `c3ds-<run>` 合成标识，运行记录写 `~/Code-MoR-backups/card3ds-stage7/run-*.json`（0600） |
| AC-07 反向用的 REJECTED 回调行 | 保留（审计意义） |
| AC-12 的 PayerMax UAT 订单 | 保留；UAT 沙箱无资金影响 |
| N2 新建的 PayerMax 技术账号 / MID / 能力 | 验收后 MID 置 SUSPENDED、能力 DISABLED（与阶段 5 做法一致），账号保留 |
| 运营台 ops-bff 写入路由（CARD_BIN_CSV） | 保留，属于正式配置 |
