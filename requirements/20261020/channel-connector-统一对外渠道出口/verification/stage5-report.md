# 阶段 5 报告：第一段本机端到端与数据迁移演练

> 任务卡 `tasks/P1-stage5.md`；依据技术方案第 3.3–3.5、4、8 节与 `tasks/P1-report.md`。执行日期 2026-10-21（UTC）。
> 顺序：A 基线（旧 JAR，03:18–03:19Z）→ B 迁移演练（03:21–03:22Z）→ C 新版本（JAR 替换 03:23Z，对照 03:24–03:26Z）。基线跑完之前没有改动库结构或 JAR。
> 未 install、未提交、未推送；只执行了 B 节授权的 SQL；只停启了 card、channel-connector、platform、ops-bff 四个本机 e2e 进程。

## 1. 结论

- **新版本与基线一致。** 链路 5 项、沙箱用例、管理读取快照逐项对照，差异只有卡片允许的三类：ID / 编码 / 时间，Vault Reveal 与读卡调用方由 `platform-support-service` 变为 `channel-connector-service`，Platform 直接访问 `/external-api-admin` 返回 404。
- **迁移数据一致。** 10 张表逐表行数与 `CHECKSUM TABLE` 一致；全部 15 个 JSON / 脚本源码列逐行 SHA-256 一致；新版本启动后、写入之前的 34 个管理 GET 与基线逐字段相同（含 ID）。详见 `stage5-migration.md`。
- **未发现本需求代码缺陷**，没有修改 worktree 或主工作区的代码。
- **阻塞（部分）：读卡只验证到调用方准入，未验证真实读卡与读卡审计。** 本机 `mor_card_v2e2e` 存在但没有任何表，给它建表不在本卡 SQL 授权内。处理见第 3.2 节与第 7 节。
- 新版本保持运行（card、channel-connector、platform、ops-bff 为新 JAR；gateway、nginx、payermax-mock 未动）。

## 2. 执行内容

| 步骤 | 内容 | 产物 |
|---|---|---|
| A1 | `mor_channel_connector_v2e2e` 原有渠道数据已被清理（0 渠道）。经运营控制台（Nginx 18444 → Gateway → ops-bff → channel-connector）登记渠道 `PAYERMAX_MOCK`（激活前置为 Platform 同编码接入方）、AE001 技术账号（真实 Vault Protect，合成 PayerMax 密钥）、MID、USD 能力组（PAY / REFUND / 金额上下限 / 3DS）。用旧 JAR 启动 card | `local-environment/e2e/runtime/ccu-stage5-channel.json` |
| A2–A4 | `stage5_e2e.py run baseline2`：候选查询；按 `PaymentChannelClientImpl` 组装请求，经 `/channel-connector/v1/operations` 发 REFUND、PAY（含读卡）、两个 NOT_SENT；沙箱新建验证账号与用例并运行一次；34 个管理 GET 快照。另加 card 调用方探针（`cardgate`） | `stage5-baseline.json` |
| B | 备份、DDL、导出导入、核对、删表语法试跑、auto 测试库 DDL | `stage5-migration.md`、`stage5-sql-log.txt`、`stage5_migrate.sh` |
| C1 | worktree `/Users/kenny/Desktop/Workspace/Code-MoR-ccu/paytech` 执行 `./mvnw -o -q -pl platform-support-app, channel-connector-app, card-app, operations-console-bff-app -am package -DskipTests`，成功。channel-connector JAR 含 `paytech-channel-connector-protocol`，platform JAR 不再含 integration 模块 | — |
| C2 | 旧 JAR 改名为 `*.pre-ccu`，复制新 JAR；用 `verification/local-e2e/run-service.sh`（副本）依次重启 card → channel-connector → platform → ops-bff，全部 Health UP | `verification/local-e2e/run-service.sh` |
| C3 | 先取只读快照（写入前），再 `run after` 重跑 A2–A4，`stage5_compare.py` 对照 | `stage5-after-readonly-snapshot.json`、`stage5-after.json`、`stage5-compare.json` |

启动器副本只改两处，原脚本未改：Platform 去掉已不存在的 `--ps.integration.execution.allowed-schemes`；channel-connector 以环境变量提供 `CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES=https,http`（合成 PayerMax 为本机 http），以及 `CHANNELCONNECTOR_PROTOCOL_EXECUTION_CARDCONTEXTPATH=`（card 无 context path）。Vault 客户端沿用新 `application.yml` 的 `vault.client.*`，其中 `caller-service=channel-connector-service`；card 与 vault 经同一 Nacos 命名空间发现。

## 3. 基线与新版本对照

### 3.1 链路（A2）

| 项 | 基线（旧版本） | 新版本 | 结论 |
|---|---|---|---|
| 候选查询 `/mid-candidates` | 200，1 个候选：控制台登记的 Channel / 账号 / MID，`[PAY, REFUND]`，0.01–100000 | 相同 | 一致 |
| REFUND（ORIGINAL_TRANSACTION） | 200 `SUCCESS`，channelStatus `REFUND_SUCCESS`；合成 PayerMax 流水 1 条：refund / `APPLY_SUCCESS`，签名、外层、requestTime、金额均有效 | 相同 | 一致 |
| ↳ Vault Reveal（`CHANNEL_ACCOUNT_CONFIG_V1` / `CHANNEL_EXECUTION`） | 1 次 SUCCESS，调用方 `platform-support-service` | 1 次 SUCCESS，调用方 `channel-connector-service` | 允许差异（调用方） |
| PAY（NEW_PAYMENT，含读卡） | 200 `NOT_SENT` / `INTEGRATION_INITIALIZATION_FAILED`，渠道流水 0 条。账号 Reveal 1 次（Platform）；card 收到读卡请求，返回 500 | 相同；账号 Reveal 1 次（channel-connector）；card 收到读卡请求，返回 500 | 一致，调用方为允许差异 |
| NOT_SENT：标准 API 不存在（QUERY） | 200 `NOT_SENT` / `STANDARD_API_NOT_FOUND`，流水 0，无 Reveal | 相同 | 一致 |
| NOT_SENT：输入不满足请求字段（REFUND 缺 `refund.refundId`） | 200 `NOT_SENT` / `VALIDATION_ERROR`，流水 0，无 Reveal | 相同 | 一致 |

### 3.2 读卡调用方（card 探针）

两侧都是 PAY 走到 PAYMENT_CARD 初始化项、card 收到请求并返回 500，结论均为 `INTEGRATION_INITIALIZATION_FAILED`。500 的原因是 `mor_card_v2e2e` 没有表：card 首个请求才初始化连接池，并以“敏感请求未处理系统异常”返回 500；出于敏感路径规则，card 不记录堆栈。这条原因是推断。为确认调用方准入，直接对 card 读卡接口发合成票据，分别用三种调用方请求：

| `X-Caller-Service` | 旧 card | 新 card | 说明 |
|---|---|---|---|
| platform-support-service | 500（越过调用方校验，读票据时失败） | 500 | 第一段过渡期仍接受 |
| channel-connector-service | **404**（拒绝） | **500**（越过调用方校验） | 允许差异：新 card 接受 channel-connector |
| other-service | 404 | 404 | 一致 |

结论：新 channel-connector 以 `channel-connector-service` 身份读卡，且被新 card 接受；旧 card 拒绝该身份，因此必须先部署 card（技术方案第 3.5 节步骤 1）。**真实读卡成功与读卡审计未验证**，card 无表，没有可读的票据和审计表，见第 7 节第 1 项。

### 3.3 沙箱用例（A3）

| 项 | 基线（`S5_BASELINE2_*`） | 新版本（`S5_AFTER_*`） | 结论 |
|---|---|---|---|
| 新建验证账号 / 用例 | 200，version 1 / 200，version 1 | 相同 | 一致 |
| 发起运行 → 结论 | RUNNING → `PASSED` | RUNNING → `PASSED` | 一致 |
| 步骤 1（PAY） | `PASSED`，执行 `SUCCESS`，输出 paymentStatus `SUCCEEDED` / channelStatus `SUCCESS` / channelTransactionRef 存在，5 条断言全过 | 相同 | 一致 |
| 证据卡号 | `444433******1111`，证据全文无卡号原值 | 相同 | 一致 |
| 合成 PayerMax 流水 | 1 条 orderAndPay / `APPLY_SUCCESS`，签名有效、卡字段有效 | 相同 | 一致 |
| 运行详情（快照） | — | 与基线本轮运行详情逐字段相同，只忽略编码、运行号、账号 ID、渠道订单号、签名、HTTP Date | 一致 |
| 执行位置 | Platform（运行行在 `mor_platform_v2e2e`） | channel-connector（运行 `238502870792601600` 只在 `mor_channel_connector_v2e2e`） | 预期迁移 |

### 3.4 管理读取快照（A4）

| 项 | 结果 |
|---|---|
| GET 覆盖 | 34 个：编辑能力、标准 API 列表与 4 个详情、接入方列表与详情、原子 API 列表与 4 个详情、脚本列表与 2 个详情、实现列表与 4 个详情、4 个流程编辑上下文，以及验证账号、用例、运行的列表与详情。新版本多 3 个，是本次新建对象的详情。全部 200 |
| 迁移后、写入前快照 vs 基线 | **0 处差异**，比较时保留 ID |
| 新版本运行后快照，去掉本次新建的 3 个对象后 vs 基线 | 0 处差异 |
| 新建验证账号、用例详情 vs 基线本轮 | 一致（只差编码、名称与 ID） |

### 3.5 新版本附加核对（C3）

| 项 | 结果 |
|---|---|
| Platform 直接访问 `GET /platform-support/external-api-admin/v1/standard-apis`、`.../integrations/PAYERMAX_MOCK` | 404 `NOT_FOUND` |
| Platform 原执行入口 `POST /platform-support/standard-apis/v1/PAY/execute` | 404 |
| `t_channel_config_change` 新写入 | `VERIFY_ACCOUNT`、`VERIFY_CASE` 各 1 条 CREATE，操作者为会话管理员 `236033080844881920`。`change_detail` 含 objectCode、before/afterVersion 0→1、5 个字段：编码、显示名、渠道、参数摘要与定义摘要。只有名称、编码和 SHA-256，没有参数或凭据值。A1 在旧版本写的 8 条渠道配置记录 `change_detail` 为 NULL，符合预期 |
| Vault Reveal 调用方 | `channel-connector-service`（REFUND、PAY 各 1 次，`CHANNEL_ACCOUNT_CONFIG_V1` / `CHANNEL_EXECUTION` / SUCCESS），依据 `mor_vault.t_vault_access_event` |
| 读卡审计调用方 | 未验证，card 无表，见 3.2 |
| Platform 10 张原表 | 未删除；迁移后没有新写入（行数不变，最大 `create_time` 仍为基线时刻） |
| 新版本日志 | channel-connector、platform、ops-bff 无 ERROR。WARN 有三类：启动期常规提示；PAY 读卡初始化失败（预期）；Platform 3 次 404 探针 |

## 4. 迁移核对结果

见 `stage5-migration.md`：

- 10 表行数与 `CHECKSUM TABLE` 全部一致。
- 15 个 JSON 与脚本源码列逐行 SHA-256 一致，含 `definition_json` ×4、`flow_definition_json` ×4、`source_code` ×2 的逐行摘要。
- `drop-integration.sql` 在临时库上试跑：10 张表 → 0 张，临时库已删除。
- auto 测试库确认只缺 10 表与 `change_detail` 后已补齐，现为 28 张表。

## 5. 执行过的 SQL（全部在本机 `mor-local-mysql-1`，root）

| UTC 时间 | 库 | SQL |
|---|---|---|
| 03:21:38 | `mor_platform_v2e2e`、`mor_channel_connector_v2e2e` | `mysqldump` 备份 10 表与 `t_channel_config_change`（只读） |
| 03:21:43 | `mor_channel_connector_v2e2e` | `2026-10-20-protocol-tables.sql`：ALTER `t_channel_config_change`（`change_detail` + 注释），CREATE 10 表 |
| 03:21:49 | `mor_channel_connector_v2e2e` | 导入 `mysqldump --no-create-info --skip-triggers --complete-insert` 导出的 10 表数据（另加 `--single-transaction --set-gtid-purged=OFF --no-tablespaces`，理由见迁移文档第 3 节） |
| 03:21:59 | `mor_ccu_stage5_droptrial` | CREATE DATABASE |
| 03:22:00 | `mor_ccu_stage5_droptrial` | CREATE 10 张空表 → `drop-integration.sql`（10 个 DROP）→ DROP DATABASE |
| 03:22:08 | `mor_channel_connector` | `2026-10-20-protocol-tables.sql`（同上，不导入数据） |

其余数据库访问都是只读 SELECT，或经应用接口写入：核对、`CHECKSUM TABLE`、`information_schema`、`mor_vault` 访问事件查询。A1 的渠道数据，以及 A3 / C3 的验证账号、用例和运行，都是经运营控制台接口写入的，不是 SQL。

## 6. 备份位置与回滚方法

- 备份：`/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/backup/20261021T032138Z-ccu-stage5/`，包括 Platform 10 表结构与数据、`t_channel_config_change` 迁移前结构与数据，以及导入用的纯数据导出。
- 旧 JAR：`local-environment/e2e/jars/paytech-{card,channel-connector,platform-support,operations-console-bff}-app-exec.jar.pre-ccu`。
- **回滚**：
  1. 用新副本 `verification/local-e2e/run-service.sh stop card channel-connector platform ops-bff`，逐个执行。
  2. 把 4 个 `*.pre-ccu` 改回 `*-exec.jar`。
  3. 用**原** `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh` 依次 start。旧 Platform 需要原脚本中的 `--ps.integration.execution.allowed-schemes`。
  4. 以 root 对 `mor_channel_connector_v2e2e` 执行 `TRUNCATE` 10 张表：t_standard_api、t_api_field、t_integration、t_integration_api、t_integration_script、t_integration_standard_api、t_integration_verify_account、t_integration_verify_case、t_integration_verify_run、t_integration_verify_run_step。
  5. Platform 原表未动，回滚后即恢复基线状态。新版本期间写入的 `S5_AFTER_*` 与对应变更记录会随之丢弃。
  6. `change_detail` 列与空表对旧代码无影响，可以保留。需要完全回退结构时，按增量 DDL 文件头的回退语句执行。

## 7. 偏差与遗留

1. **读卡阻塞（部分）**：`mor_card_v2e2e` 只有库、没有表。为它建表不在本卡 SQL 授权内，因此只用合成票据验证了调用方准入（3.2），没有验证真实读卡成功（含 CVV 领取）和 card 侧读卡审计调用方。补齐需要单独授权：
   - 对 `mor_card_v2e2e` 执行 card 测试 DDL 或 schema；
   - 经 card 建卡并以 Payment 身份签发票据（或起 Payment / acquiring 走 Hosted Checkout）。

   card 在本机以空库运行，读卡请求返回 500，并记录“敏感请求未处理系统异常”的 ERROR。这是本机环境状态，不是代码问题。
2. **基线第一次运行作废**：第一次 `run baseline` 已执行链路和沙箱，但写盘前的私密值扫描把合成账号的 appId / merchantNo 识别为密钥，没有写出结果。这两项是标识，不是密钥。改为写盘前替换为占位符；真正的密钥、口令和 Vault Token 仍然命中即中止。随后以 `baseline2` 重跑并作为基线。因此作废运行的 `S5_BASELINE_ACCT` / `S5_BASELINE_CASE` 与运行 `238501320716193792` 也留在数据中。它们已随迁移导入，两次快照都包含，不影响对照。
3. **基线 PAY 的 card 日志字段**：基线运行时，card 日志提取规则没有匹配到被遮蔽的 URI，所以 `chain.payWithCardRead.cardLog` 为空。修正后用 `cardgate` 对基线运行时间窗重新提取，得到 `cardLogDuringRun=["POST 500"]`，与新版本一致。对照时链路比较不含该字段。
4. **迁移导出追加选项**：`--single-transaction --set-gtid-purged=OFF --no-tablespaces`。GTID 开启时必须加 `--set-gtid-purged=OFF`，否则导入会尝试设置 `GTID_PURGED`；三项都不改变数据内容。mor-test 执行时同样需要。
5. **文档小缺陷（未改）**：worktree `card-api` 的 `ChannelCardMaterialApi` JavaDoc 仍写“Platform 执行……时”“只接受 platform-support-service”。实现已同时接受两个调用方，建议合入时一并修正。这不影响行为，也不属于本次对照发现的不一致，因此没有修改。
6. 新版本 `ChannelConfigChangeApi` 的 targetType 白名单仍未开放 `VERIFY_*` 等 7 个新类型（P1 D9）。本次直接查库核对了 `change_detail`。
7. 本机 Nacos `mor-local` 中，card-service 现由空库的新 card 实例注册。如果其他会话的本机全链路依赖 card，需要知道它当前不可用，与本次之前 card 未运行的状态等价。
8. 主工作区本次只新增 `verification/` 下的文件：
   - `stage5_e2e.py`、`stage5_migrate.sh`、`stage5_compare.py`、`local-e2e/{run-service.sh,nginx.conf.template}`；
   - `stage5-baseline.json`、`stage5-after-readonly-snapshot.json`、`stage5-after.json`、`stage5-compare.json`；
   - `stage5-migration.md`、`stage5-sql-log.txt` 与本报告。

   所有文件已逐个扫描：不含合成密钥、管理员口令、账号 Vault Token、appId 或 merchantNo。

## 8. 真实读卡 PAY（补充）

> 主会话于 03:29:47Z 在本机 `mor_card_v2e2e` 执行了 `card-schema.sql`（0 → 8 张表），随后补齐第 7 节偏差 1。
> 本节授权只包括两项：重启 card；经 card API 对 `mor_card_v2e2e` 做业务写入。没有执行任何 SQL。
> 脚本 `verification/stage5_card_pay.py`，结果 `verification/stage5-card-pay.json`。只验证新版本，不做旧版本对照。

### 链路

1. 用副本启动器重启 card（新 JAR，有表库），Health UP。
2. 经 card 现行合同建手输卡工具：`POST /card/v1/instruments`，调用方 `payment-service`。
   - 卡为 PayerMax 公布的 UAT 测试卡，属于合成数据。
   - 不保存卡，工具期限 30 分钟。
3. 签发读卡票据：`POST /card/v1/material-tickets`，调用方 `payment-service`，用途 `CHANNEL_EXECUTION`，有效 90 秒。
4. 按 `PaymentChannelClientImpl` 组装 PAY 请求（NEW_PAYMENT，`card.token` 为该票据），经 `/channel-connector/v1/operations` 发起。

### 结果

| 项 | 结果 |
|---|---|
| 建工具 | 200，`instrumentRef=238504392603504640`，掩码 `444433******1111` |
| 签发票据 | 200 |
| PAY | 200，`outcome=SUCCESS`，`channelStatus=SUCCESS`，`paymentStatus=SUCCEEDED`，有渠道流水号 |
| 合成 PayerMax 流水 | +1 条：orderAndPay，`APPLY_SUCCESS`。签名、外层、requestTime、金额均有效 |
| 卡直付卡字段 | `cardValid=true`，即渠道请求中携带了 12–19 位完整卡号与 CVV。这是对渠道请求的预期行为 |
| 票据 | `t_card_material_ticket` 中该票据 `used_at` 已写入；签发调用方 `payment-service`，用途 `CHANNEL_EXECUTION`；attempt、action 与请求一致 |
| 同一票据再次读卡 | 调用方 channel-connector-service，返回 400 `BUSINESS_CHECK_FAILED`（已使用），不返回卡数据 |
| Vault 事件 | card-service PROTECT `PAYMENT_CARD_DEK` / `CARD_STORAGE`（建工具）→ channel-connector-service REVEAL `CHANNEL_ACCOUNT_CONFIG_V1` / `CHANNEL_EXECUTION` → card-service REVEAL `PAYMENT_CARD_DEK` / `CARD_USE`（读卡解密）。三条均为 SUCCESS |
| 卡号 / CVV 是否出现 | 以下位置均未出现：card、channel-connector、platform、ops-bff 本时段日志；合成 PayerMax 流水（它只记卡字段是否有效）；本报告与全部输出文件 |

### 读卡调用方

card 不落库记录读卡调用方：`t_card_material_ticket.caller` 是签发方，访问日志只记方法、遮蔽 URI 与状态。下面三条证据可以确认读卡来自 channel-connector-service：

- card 访问日志中读卡请求（POST 200）的 requestId `9818e8be-…`，与 channel-connector 这次 PAY 的 requestId 相同。
- 新 card 只接受 channel-connector-service 与 platform-support-service，其他调用方返回 404（见 3.2）。同一时段 Platform 没有执行入口（404），也没有相关请求日志。
- protocol `PaymentCardFunction.CALLER = CardApiConstants.CHANNEL_CONNECTOR_SERVICE`。

因此本节结论是：读卡调用方为 channel-connector-service。card 侧**没有专门的读卡审计记录**。如果需要可查询的审计，属于 card 合同的增量，建议另行评估。

### JavaDoc 修正

`paytech/card/paytech-card-api/.../material/ChannelCardMaterialApi.java`：

- 修改前用 diff 确认主工作区与 worktree 两边文件一致；修改后再 diff，仍一致。
- 类注释的执行方由 Platform 改为 channel-connector。
- `caller` 参数改为“接受 channel-connector-service 与 platform-support-service（统一对外出口第一段过渡，第二段收回 platform-support-service）”。
- worktree 中 `-pl card/paytech-card-api compile` 通过。

第 7 节第 1 项（读卡阻塞）与第 5 项（JavaDoc）至此关闭。
