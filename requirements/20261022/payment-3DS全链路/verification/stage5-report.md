# 阶段 5 报告：3DS 全链路 + 卡 BIN 库 本机端到端验收

> 任务卡：[tasks/E-stage5.md](../tasks/E-stage5.md)。执行日期 2026-10-22（UTC），开始于 15:15:01Z（收到 GO e2e）。
> 代码：只读集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-int`（main `1d39cd94` + 六张卡合并结果）。JAR 只从该目录 `./mvnw -o -q package -DskipTests` 打包，未 install，未提交 / 推送，未改代码（S5-01 由主会话修复）。
> 主会话补充裁决：T1（payment / risk-decision 空库建 canonical schema、脚本直连 Payment）获批；T2（acquiring 层）不做；回调直接 POST 本机 Gateway 18080，不经 Nginx；Platform 本机改为 internal-auth DUAL（方案 1）。

## 1. 结论

- **3DS：AC-01、02、04—11 通过；AC-03 部分通过（THREE_DS_CHALLENGE 如实传递；REDIRECT 与 acquiring 层以自动测试为证）；AC-12 未执行（本机无 PayerMax UAT 凭据）。**
- **BIN：AC-01—AC-12 全部通过**（AC-07 已存卡经 Card 接口直连验证，acquiring / counterparty 透出以自动测试为证）。
- 回归：统一对外渠道出口第二段回归集合全部只有已解释差异（第 7 节）。
- 缺陷：S5-01（中，BIN 页面并发 429）与 S5-02（低，BIN 页面默认选中项）均**已由主会话修复并复验通过**。
- 结束时新版本保持运行，Health 全部 UP；Platform 保持 DUAL（与 mor-test 一致）。

## 2. 执行时间线（UTC，详见 `timeline.txt`）

| 时间 | 动作 |
|---|---|
| 15:15:01 | GO；`run-service.sh status all` 记录现状（`status-before.txt`） |
| 15:16:10 | 停 ops-web、ops-bff、gateway、card、channel-connector、platform、payermax-mock（只停本启动器 PID） |
| 15:16:11 | 整库 mysqldump 5 个库（第 3 节） |
| 15:16:41—15:17:27 | SQL：payment / risk-decision 空库建表；card、channel-connector、platform 增量 |
| 15:17:37 | 换 JAR（旧 JAR 改名 `*.pre-card3ds`） |
| 15:18:00—15:19:14 | 依次启动 platform（工作流启动器参数）、risk-decision、payment、channel-connector、card、ops-bff、gateway、PayerMax3dsMock、ops-web（集成目录） |
| 15:19:20 | 激活块 132 / 133；Gateway 快照 416 → 426 路由、targets 3 |
| 15:19:57 | `configure_3ds.py PAYERMAX_MOCK`（第二次运行无修改） |
| 15:21:01 | Risk 测试发布 ACTIVE（点版本 220003） |
| 15:23—15:30 | 3DS 场景；MID 路由场景 |
| 15:31—15:33 | 回归集合；路由配置停用 |
| 15:37:05 | 新建存储写入路由（第 4 节） |
| 15:39:01 | Platform 改 DUAL 重启并核对 |
| 15:40—15:42 | BIN 导入、激活、并发、回切 |
| 15:42:13 | ops-web 以 S5-01 修复版本重启；15:43 冷却后截图与复验 |
| 15:47:59 | 终版泄漏检索，删除临时 MQ 捕获队列，恢复原合成 PayerMax |

## 3. SQL 与备份（`stage5_sql.sh`、`stage5-sql-log.txt`）

备份：`local-environment/e2e/backup/20261022T151611Z-card3ds-stage5/`（mor_card / payment / channel_connector / risk_decision / platform `_v2e2e` 整库，SHA-256 前缀见日志）。

| 库 | 执行 | 结果 |
|---|---|---|
| mor_payment_v2e2e | （T1）集成目录 `payment-schema.sql`，执行前确认 0 表 | 17 表 / 0 行；需求 DDL（消费表 2 张、Token 卡属性 8 列）已包含，只核对 |
| mor_risk_decision_v2e2e | （T1）`risk-decision-schema.sql` + `risk-decision-initial-data.sql` | 54 表 / 65 行；3 列、220003、变量 93014—93016 已包含，只核对（服务仓库增量文件不再执行） |
| mor_card_v2e2e | BIN `db/card/ddl.sql`、`dml.sql` | 4 表；种子 v1 ACTIVE、17 号段、1 激活记录 |
| mor_channel_connector_v2e2e | 3DS `db/channel-connector/ddl.sql`、`dml.sql` | 新列 + 2 处注释；回调定义 **1 行**（本机只有 PAYERMAX_MOCK，无 PAYERMAX；草案预期 2 行） |
| mor_platform_v2e2e | BIN `db/platform-support/ddl.sql`、`dml.sql`（国家区域 249、CARD_BIN_CSV、块 132）；3DS 块 133 | 249 国家 / 策略 1 / 块 132 路由 9、头 34、guard 54 / 块 133 目标 1、路由 1、头 2、guard 4（与集成目录 initial-data 行集逐行一致） |
| mor_platform_v2e2e | 激活：块 132 授权 2、菜单 2、路由 9；块 133 路由 1（各块注释中的激活语句） | 共 10 条路由 ACTIVE；块 127、130 数据未改 |

## 4. JAR 与环境改动

| 服务 | 旧 JAR（保留为 `*.pre-card3ds`） | 新 JAR SHA-256 前 12 位 |
|---|---|---|
| card | cabf63edd0d6 | 75a267ababec |
| channel-connector | d46d38ed4cf1 | e8566840eefd |
| platform-support | 34a0a3a42a3b | b3e81954d0b4 |
| operations-console-bff | 86aa96b25d0c | c56de541c16b |
| payment / risk-decision / api-gateway | 运行目录原无副本（gateway 进程运行的副本已不在目录中） | 694d62e992d3 / c80d48ba0faa / 470dbade49ba |
| acquiring / counterparty | 原无 | a50d6f7feea7 / 3c3063c7193e（**已复制未启动**：T2 不做，库为空） |

环境改动（均经主会话批准）：
- payment、card 启用本机 XXL 执行器（Admin 地址为不可达占位、本机随机 Token，端口 19993 / 19991），按 XXL Admin 方式 `POST /run` 触发 `compensationEventScan`。
- payment `PAYMENT_CHANNEL_CALLBACK_BASE_URL=https://channel-callback.local.test`（只用于核对 notifyUrl 透传）。
- card `JAVA_TOOL_OPTIONS` 指向 `local-environment/e2e/runtime/card3ds/card-truststore.p12`（JDK cacerts + 本机 MinIO 证书）。
- 合成 PayerMax 扩展副本 `harness/PayerMax3dsMock.java`（do3DS → 挑战、挑战单默认 PENDING、`/__control/query` 设置查单结果、do3DS 请求不按末位制造错误）；验收后已恢复原 `PayerMaxMock.java`。
- ops-web 从集成目录运行（`operations-console-web/node_modules` → 主仓库 node_modules 的符号链接）。
- 存储写入路由：`POST /api/operations/storage-admin/v1/routes` `{sourceSystem: operations-console-bff, instanceCode: MINIO, bucketName: mor-platform-files, scanPolicyCode: CARD_BIN_CSV, maxUploadSeconds: 900}` → objectId **239049429963771904**（既有实例 / 桶 / `*` 路由未改）。
- **Platform internal-auth DUAL**：只重启 Platform，工作流启动器参数不变，另加 `SPRING_APPLICATION_JSON={"internal-auth":{"mode":"DUAL","jwks-uri":"http://127.0.0.1:18106/platform-support/platform/v1/internal-auth/jwks"}}`。原因：OFF 下 `/internal/files/v1` 永远没有调用身份（401 INTERNAL_TOKEN_INVALID，ops-bff 外显 502）。重启后核对（`dual-checks.txt`）：Gateway 快照每 5 秒刷新成功、运营登录、工作流定义 / 实例 / 动作模板列表 200；数据查询在本机 e2e 前后都没有表与运营路由（工作流启动器注释已说明，非本次回退）。
- Risk 测试发布（`stage5-risk-setup.json`）：拦截规则集（发卡国 NG → 审核）、认证规则集（标记金额 333 USD 或发卡国 GB → 放行 + THREE_DS_REQUIRED），发布 239045324600000512 ACTIVE，点版本 220003。
- 路由配置（`stage5_routing.py`）：新 MID2 `PMX-S5-MC`（239047412893679616）+ MASTERCARD 能力；MID1 追加 MASTERCARD 行 ACTION.PAY=false；场景后 MID2 SUSPENDED、两条能力 DISABLED。

## 5. 3DS 验收（`stage5_3ds.py` → `stage5-3ds.json`、`stage5_routing.py` → `stage5-routing.json`）

链路：脚本以 acquiring-service 身份直连 Payment（建 Intent → 申请卡工具 → pay → 提交查询 / 刷新）；Card、risk-decision、channel-connector、合成 PayerMax 为真实进程。

| AC | 结果 | 证据 |
|---|---|---|
| AC-01 | 通过 | GB 发卡卡：Risk 评估快照 GB/VISA/DEBIT、`THREE_DS_REQUIRED`；pay 202 `THREE_DS_CHALLENGE` + 跳转地址；合成 PayerMax 收到 `dynamic3DS=do3DS`、notifyUrl 存在；动作只有 PAY_RISK、PAY，无 AUTHENTICATE |
| AC-02 | 通过 | MASTERCARD 只剩未声明 3DS 的 MID2：PAY FAILED `THREE_DS_CHANNEL_UNAVAILABLE`，合成 PayerMax 0 请求，冻结 MID2 |
| AC-03 | 部分 | `THREE_DS_CHALLENGE` 经 channel-connector → Payment → 提交查询如实返回（`interaction.type`）；REDIRECT 未覆盖（PayerMax 流程类型为常量），acquiring 两种类型接受与收银台接口以 acquiring 自动测试为证 |
| AC-04 | 通过 | 不发通知：等到退避到期（11.8 秒）触发扫描 → PAY / Attempt / Intent SUCCEEDED，PaymentSucceeded 发布 |
| AC-05 | 通过 | 查单 FAILED → 刷新得到 FAILED（`PAYMENT_FAILED`），Intent OPEN、无活动 Attempt；同一 Intent 新提交被接受 |
| AC-06 | 通过 | 5 秒内 3 次刷新只多 1 次查单；窗口后刷新得到 SUCCEEDED |
| AC-07 | 通过 | 签名通知 → Gateway 18080 → channel-connector 验签（VERIFIED/PROCESSED/MAPPED）→ MQ `ChannelPaymentNotified` → Payment 监听器触发确认；应答 `{"code":"SUCCESS","msg":"Success"}`；篡改报文 401 `CHANNEL_CALLBACK_SIGNATURE_INVALID`、记 REJECTED，交易不受影响 |
| AC-08 | 通过 | 字节相同的通知 ×2 + 刷新 + 退避扫描并发：回调行 1 条、终态写一次、PaymentSucceeded 每个 Attempt 只发布 1 次（MQ 捕获 9 条成功事件，`maxPerAttempt=1`，`stage5-mq-capture.json`） |
| AC-09 | 通过 | 交互过期且仍处理中：保持 PROCESSING；拨时 > 24 小时后扫描：任务 FAILED `PAY_RESULT_UNRESOLVED`、Attempt 仍 PROCESSING（转人工，不自动判失败）。注：拨时后 1 秒内的首次触发未取到任务，约 15 秒后再次触发生效 |
| AC-10 | 通过 | 过期后提交查询 PROCESSING 且无 interaction（确认中），新卡工具申请被拒 400「支付意图已有进行中或成功的支付尝试」；CLOSED → FAILED `CHANNEL_ORDER_CLOSED` 后可重提；无此交易 10 分钟内不推进、超过后 FAILED `CHANNEL_TRANSACTION_NOT_FOUND` |
| AC-11 | 通过 | `stage5-leak-scan.json`：8 个服务日志、MQ（17 条）、补偿表、回调表、动作表中跳转主机 / `acsSecret=` / 测试卡号均 0 命中；MQ 中 3 个 Luhn 形长数字为脚本生成的 15 位 orderId / sourceSubmissionId，不是卡号；Payment 补偿事件 Payload 为空（只存键） |
| AC-12 | 未执行 | 本机 e2e 无 PAYERMAX 渠道与 UAT 商户凭据（只在 mor-test Vault，按裁决不读取） |

附带观察：
- UNKNOWN 恢复（R3）：合成 PayerMax 对末位 4 的订单用错误私钥签名，PAY 结果未知（pay 202 PROCESSING），随后的查单把 Action 推进为 SUCCEEDED。
- 金额不一致分支：合成 PayerMax 重启丢失下单记录后，对 333.00 的挑战单查单返回 10 → 任务 FAILED `PAY_RESULT_AMOUNT_MISMATCH`、不推进（Attempt 239046430629593088 保持 PROCESSING，属本机残留）。
- 首轮因合成 PayerMax 末位规则得到的结果单独保存为 `*_run1_*`，原因见键名。

## 6. 卡 BIN 库验收（`stage5_bin.py` → `stage5-bin.json`；截图 `stage5-bin-screens/`）

| AC | 结果 | 证据 |
|---|---|---|
| AC-01 | 通过 | ops 上传（Platform 直传 MinIO）→ 完成（PENDING_SCAN，`/files/v1/…`）→ 创建 v2 201 VALIDATING → VALID（8 行）；重复提交同一文件 200 返回原版本；未激活时查询仍为 v1 |
| AC-02 | 通过 | 并发竞争后 v5 生效：新工具快照 VISA/CREDIT/PLATINUM/AE/GCC、版本 5、8 位；激活前创建的工具仍为 v1 GB/DEBIT |
| AC-03 | 通过 | `40128888` 与 `401288` 同时存在时命中 8 位（v1、v5）；`4111111`（7 位）优先于 `411111` |
| AC-04 | 通过 | 未识别 BIN：快照全空、版本号有值；Risk 不带卡属性；路由只命中 `*`；交易成功 |
| AC-05 | 通过 | v3：9 行错误（重复前缀两行均记、非法卡组织、缺发卡国、未登记国家 XX、超长前缀、字母、列数、非法借贷记），INVALID 激活 400；v4 表头错误为文件级失败原因 |
| AC-06 | 通过 | 回切 v1（类型 ROLLBACK、操作人、previousVersionNo 5）后新工具为 v1 属性，v5 工具不变 |
| AC-07 | 通过 | 运营 BIN 查询返回快照 + 发卡行；建工具快照见上；已存卡：Card `GET /card/v1/stored-cards`（acquiring-service、counterparty-service 两种调用方）返回 `cardBrand=MASTERCARD`；acquiring / counterparty 外层透出以自动测试为证 |
| AC-08 | 通过 | Token 8 列冻结；Risk 评估快照 `issuer_country_code / card_brand / funding_type`；NG 发卡命中拦截规则 → REVIEW，Attempt 失败、无渠道请求；GB 发卡命中认证规则 |
| AC-09 | 通过 | MASTERCARD 卡选中只配 MASTERCARD 行的 MID2；VISA / 未识别只命中 `*`（MID1） |
| AC-10 | 通过 | PaymentSucceeded `schemaVersion=2` 追加 `cardBrand / fundingType / cardLevel`，未识别时为 null |
| AC-11 | 通过 | 导入错误明细不回显 16 位卡号形前缀（valueExcerpt 为 null）；日志 / MQ / 补偿表见上 |
| AC-12 | 通过（S5-01 修复后） | 页面：摘要、BIN 查询（8 位命中 / 未识别 / 非数字即时提示）、各状态版本详情（校验失败横幅与错误明细）、导入弹窗、激活与回切确认弹窗（号段对比、影响说明、二次确认）、状态筛选；页面 HTTP 错误 0，控制台只有 antd 弃用警告 3 条 |
| 409 并发 | 通过 | 过期 expectedActiveVersionNo → 409「生效版本已被他人修改」；两个运营请求同时激活 v2 / v5 → v5 200、v2 409「当前资源正在执行」 |

## 7. 回归（统一对外渠道出口第二段回归集合，`stage5-regression-after.json`、`stage5-regression-compare.json`）

以上一阶段 `stage5p2-after.json` 为基线、复用其比较逻辑（`stage5_regression_compare.py`，原文件不改）：
- 一致：候选查询、REFUND、合成票据 PAY、输入缺字段 NOT_SENT、真实读卡 PAY、读卡调用方收紧（Platform 身份直读 404、票据未用）、沙箱运行（label c3ds）。
- 预期差异：①「QUERY 标准 API 不存在」用例变为 SUCCESS——本需求把 QUERY 映射为 PAY_QUERY（technique 3.3.2）；② 标准 API PAY 与 PayerMax ORDER_AND_PAY / PAY 流程的字段与版本 +1（configure_3ds）；③ 沙箱用例变为 STALE（标准 API 变更所致，按设计需重新验证）；④ S5P2_ECHO 标准 API、MID2 等本机测试对象增长；⑤ 账号详情中的标签替换差异（比较脚本只替换旧标签）。
- 第一次回归用了旧标签 `after` 导致账号编码重复 400，改用 `c3ds` 重跑，结果以重跑为准。

## 8. 缺陷

| 编号 | 严重度 | 描述 | 状态 |
|---|---|---|---|
| S5-01 | 中 | BIN 页面加载时 4 个 `/versions` 并发，超过路由 132401 每账号并发 2，必有 2 个 429，摘要显示“当前生效版本 无” | 主会话已修复（`CardBinLibrary.tsx` 摘要三次请求串行）；**复验通过**：冷却后首次加载 4 次请求、ops-bff 侧峰值并发 2、0 个 429、摘要与版本表完整（`s5-01-recheck.txt`、`final-01-main.png`） |
| S5-02 | 低 | 版本详情默认选中在数据到达前取 `items[0]`：首次加载选中最新 v5 而不是生效 v1；从“校验失败”切到“生效中”后详情仍显示不在列表中的 v4（`final-08-filter-active.png`） | 主会话已修复（自动选中等待列表与生效版本都加载、只在当前筛选结果中选）；**复验通过**（ops-web 重启后冷却 60 秒）：首次加载详情为生效 v1（`s502-01-main.png`），校验失败 → 生效中后详情切到 v1（`s502-08-filter-invalid.png`、`s502-08-filter-active.png`），页面 HTTP 错误 0 |

非缺陷记录：ops-bff 把 Platform 401 外显为 502 `UPSTREAM_INVALID_RESPONSE`（OFF 模式下出现，属环境配置）；BIN 页面导入人 / 操作人显示账号 ID（阶段 4 已知偏离 D9b）。

## 9. 证据边界（真实 / 合成）

- 真实：card、payment、channel-connector、risk-decision、platform、gateway、ops-bff、ops-web（集成目录 dev server）、MySQL、Redis、RabbitMQ（vhost /mor-e2e）、MinIO、Vault 容器。
- 合成：PayerMax（本机扩展副本）；3DS 持卡人验证由脚本设置查单结果模拟；渠道通知按 PayerMax 规则用合成密钥签名，**直接 POST Gateway 18080 并自带 Nginx 会写的 `X-Entry-Channel: CHANNEL_CALLBACK` / `X-Real-IP`，未经 Nginx**；BIN CSV 为合成号段。
- 未经 acquiring（T2 不做）：AC-03 的 acquiring 侧、AC-06 / AC-10 的收银台轮询入口、BIN AC-07 的 acquiring / counterparty 透出以阶段 4 自动测试为证。
- 时间相关场景（过期、10 分钟、24 小时）以 SQL 拨回本次测试 Action 的时间模拟；XXL 由脚本按 Admin 协议触发。
- AC-12 与真实 PayerMax 通知签名原文、`dynamic3DS` 实际行为未验证（需 UAT 凭据）。

## 10. 环境残留（验收后保留）

- 新版本 9 个 JAR（acquiring、counterparty 未运行）；旧 JAR `*.pre-card3ds`。
- payment / risk-decision e2e 库已建表并有测试数据；Risk 测试发布 ACTIVE。
- Platform **internal-auth DUAL**（与 mor-test 一致；`SPRING_APPLICATION_JSON` 只在本次启动的进程环境中，按启动器重启会回到 OFF）。
- 存储写入路由 239049429963771904；BIN 库 v2—v5（v1 生效）；块 132 / 133 已激活。
- MID2 SUSPENDED、两条 MASTERCARD 能力 DISABLED；PAYERMAX_MOCK 的标准 API / 流程已按 configure_3ds 修改。
- payment、card 的 XXL 执行器配置只在当前进程；`runtime/card3ds/`（XXL Token、信任库、配置副本）。
- ops-web 保持从集成目录运行（vite dev server，含 S5-01 / S5-02 修复），供用户复核；集成目录 `operations-console-web/node_modules` 符号链接按主会话要求保留（未被 .gitignore 匹配，显示为未跟踪；提交前须删除）。
- 若干停在处理中的本机测试 Attempt 及其 PAY_RESULT_QUERY 任务（5 条：ERROR 3、FAILED 2）。
- 已删除：临时 MQ 捕获队列 `s5-card3ds-capture`；已恢复原合成 PayerMax。

## 11. requirements-site 测试

主工作区 `npm --prefix requirements-site test`：137 例，127 通过、10 失败；对 HEAD `3a4574c7` 的干净导出运行同样 137 / 127 / 10，失败用例集合完全相同，均为既有失败，与本需求无关。

## 12. 产物

`stage5_sql.sh`、`stage5_risk_setup.py`、`stage5_3ds.py`、`stage5_routing.py`、`stage5_bin.py`、`stage5_leak_scan.py`、`stage5_bin_screens.mjs`、`stage5_regression_compare.py`、`harness/PayerMax3dsMock.java`、`harness/run_configure_3ds.py`；结果 `stage5-*.json`、`timeline.txt`、`status-before.txt`、`jars.txt`、`dual-checks.txt`、`storage-route.txt`、`s5-01-recheck.txt`、`configure-3ds-PAYERMAX_MOCK.json`、`stage5-bin-screens/`。
