# reconciliation 对账控制台详情页与渠道流水：阶段 5 本机验收报告

> 2026-10-21（UTC 约 05:10–06:10），只在本机执行；未碰 mor-test，未提交 / 推送 / 部署，未改生产源码。需求 [requirement.md](../requirement.md)，方案 [technique.md](../technique.md)。

## 1. 环境

| 项 | 值 |
|---|---|
| 代码 | `main` 工作区当前源码（含本需求未提交改动） |
| 构建 | JDK 25，`./mvnw -B -o`：`reconciliation/paytech-reconciliation-{api,core,app} install -DskipTests`；`-f operations-console-bff/pom.xml -pl paytech-operations-console-bff-app -am install -DskipTests`。核对 JAR 内含 `ChannelStatementLineController`、`ReconciliationApiErrorConfiguration`、`ChannelLineSearch`、`BankStatementLineDetailView`；reconciliation 源码无晚于 core JAR 的文件 |
| 私有栈（本次启动，已停止） | [stack.sh](./stack.sh)（沿用切片 1，改为状态目录 `scratchpad/recon6`、临时 vhost `/mor-recon6`、Redis 命名空间 `local-recon6:*`）：reconciliation 18115（`mor_reconciliation_v2e2e`，XXL 关闭）、ops-bff 18211（Nacos 只发现不注册）、Gateway 18090（SimpleDiscovery → platform 18106 / ops-bff 18211）、Nginx 18454 |
| 共享（他人进程，只读使用，未重启） | platform 18106（`mor_platform_v2e2e`，块 123、126 均 ACTIVE：资源 126301、路由 126401–126403）、Vite 18301（cwd 为主工作区 `operations-console-web`，即当前源码）、MySQL 13306、Redis 16379 db 3、RabbitMQ、Nacos `mor-local`、Vault 容器 |
| 浏览器 | Playwright 无头 Chromium，1512×900，经 https://127.0.0.1:18454 真实登录 |

**说明：** 共享 platform JAR（`local-environment/e2e/jars/paytech-platform-support-app-exec.jar`，07:23 构建）里的 `console-resource-capabilities.json` 尚无 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`；菜单与路由取自数据库块 126，不受影响（菜单正常显示）。能力清单只影响 Platform 配置页，本阶段未涉及。

## 2. 脚本与种子数据

| 文件 | 作用 |
|---|---|
| [recon5_common.py](./recon5_common.py)、[BillingFactPublisher.java](./BillingFactPublisher.java) | 沿用切片 1（只改状态目录 / vhost / 观察队列名） |
| [seed.py](./seed.py) | 经 IAM 新建 MAKER（对账读 + 操作 + 财务读）、CHECKER（对账读 + 审批 + 财务读）；银行账户 1 个（Maker 提交、Checker 批准）；清算账户 CA1、CA2（不挂 MID，未建渠道）；正式发布器发 7 条 `BillingFactRecorded`；渠道账单 A（S1 形态，调整行 Maker/Checker 归类 → 对平，再经银行匹配 → 已到账）、B（自动对平）、C（已匹配 + 未匹配 + 调整行异常，匹配中）、D（匹配中 → 经接口作废）、E（匹配中，留给浏览器作废）；银行对账单 3 行（67.55 → A 建议并经接口确认、60.00 → B 建议、12.34 无匹配）。35/35 通过 |
| [seed_more.py](./seed_more.py) | 账单 F（CA2，25 行无平台流水的收款，匹配中），用于账单行分页、`?line=` 跨页高亮、渠道流水第 2 页 |
| [api_checks.py](./api_checks.py) | 3 个新接口经 Gateway 的接口核对 |
| [browser_detail.py](./browser_detail.py) | 浏览器验收（`SECTIONS=` 可只跑指定段；含 `pace()` 节流，见 §5 观察 O-1） |
| [cleanup.py](./cleanup.py) | 沿用切片 1，本次未建渠道时跳过 channel-connector |

## 3. 接口核对（经 Nginx → Gateway 权限点 → BFF → reconciliation）：27/27 通过

| 编号 | 结果 |
|---|---|
| `GET /channel-statement-lines` 默认 | 200，共 9 行（A3 + B1 + C3 + E2），不含作废账单 D；23 个字段齐全；交易日倒序、同日 `lineId` 倒序；行带批次号、账单状态（A 为 `CASH_CONFIRMED`）、清算账户编码、平台流水 ID、实际费 0.45 |
| 筛选 | CA2 → 1（B；D 作废排除）；`statementStatus=VOIDED` → 2（D）；`matchStatus=UNMATCHED` 全为未匹配；`lineType=ADJUSTMENT` → 3；外部交易号精确 → 1、前缀 → 0；交易号类型 + 交易号 → 1；交易号类型单独 → 1；交易日两端含 → 5 |
| 分页 / 校验 | `page=2&size=10` → 空页、回执 page 2 size 10；`size=200`、`size=30` → 400 `INVALID_REQUEST`；未登记参数 → 400 `GATEWAY_INVALID_REQUEST`；未登录 → 401 |
| `GET /bank-statements/{id}` | 200，18 个字段齐全（IMPORTED、期初 1000.00、期末 1139.89、3 行）；不存在 → **404 `BANK_STATEMENT_NOT_FOUND`**（detail「银行对账单不存在: …」） |
| `GET /bank-statement-lines/{id}` | 行 1：`line / statement / matches / openExceptions`，匹配 CONFIRMED，预期流水 `CHANNEL_SETTLEMENT`、`sourceId` = 账单 A；行 2：一条 PROPOSED；行 3：无匹配；不存在 → **404 `BANK_STATEMENT_LINE_NOT_FOUND`**；非数字主键 → 400 |
| 既有接口 | `GET /channel-statements/{不存在}` 仍为 400 `BUSINESS_CHECK_FAILED`「渠道账单不存在: …」（按决定不改合同） |

（首轮 26/27：脚本把 A 的状态期望写成 RECONCILED，实为银行匹配确认后的 CASH_CONFIRMED；修正期望后复跑 27/27。）

## 4. 浏览器验收（截图在 [screenshots/](./screenshots/)）

按段运行；下表为每段最后一次运行结果，合计 **83 项功能检查，82 项通过，1 项失败（W-1）**。

| AC | 结果 | 证据 |
|---|---|---|
| AC-01 | **通过** | 6 类详情（清算账户、银行账户、渠道账单、银行对账单、银行流水、对账异常）各 6 项：列表点行进入、刷新仍在详情、浏览器后退回列表且查询串完全一致（如 `currency=USD&size=20`、`clearingAccountId=…&size=20`、`account=…&tab=statements`、`account=…&matchStatus=`（清空缺省筛选）、`statementId=…`）、「返回列表」同样恢复、另一已登录会话直接输入地址可打开（36/36）。渠道流水第 2 页 → 点行进详情 → 后退回第 2 页、首行一致。未找到：既有 400（清算账户、银行账户、渠道账单、对账异常）、新 404（银行对账单、银行流水）、非法主键 `abc` 均显示「未找到该…」与返回按钮且无错误通知；「返回银行对账」回列表（`16-not-found-old-400`、`16-not-found-new-404`） |
| AC-02 | **通过** | 账单 F 详情「共 25 行」、第 1 页 20 行、第 2 页 5 行；`?line=23` 自动翻到第 2 页并高亮第 23 行（`20-…`）；匹配中账单 C 门禁三项标红；行筛选「未匹配」→ 行 2、「调整」→ 行 3（`21-…`）；账单 E 详情点「作废」→ 库 `VOIDED`、页面「已作废」、作废按钮消失（`60-statement-voided`），渠道流水默认由 34 变 32 |
| AC-03 | **通过** | 银行对账单详情显示期初 / 期末与全部 3 条流水，点流水进流水详情；流水 2 点「确认」→ 匹配 `CONFIRMED`、页面「已确认」（`40-`、`41-`）；未匹配流水 3 显示「人工多选匹配」区（列出可选预期流水、带符号合计、剩余，`42-`；本次未提交人工匹配，见说明）；「开异常」→ 跳到新异常详情「银行流水未匹配」（`43-`），来源「查看银行流水」跳回，流水详情「未结异常（1）」 |
| AC-04 | **通过** | 异常（C 行 3 调整行待归类）来源「查看渠道账单行」→ `/statements/C?line=3` 并高亮；MAKER「提交处置」抽屉（归类 · CHANNEL_FEE_OTHER）→ 抽屉关闭、处置记录出现「待复核」（`50-`、`51-`）；CHECKER 直接打开该异常，「复核处置」抽屉显示提交内容 → 批准并应用 → 抽屉关闭、详情「已解决 / 已应用」，库 `RESOLVED`（`52-`、`53-`） |
| AC-05 | **14/15**（W-1） | 菜单进入渠道流水；默认 34 条、不含作废账单 D、「共 34 条，按交易日期从新到旧」、交易日倒序（`02-`）；页大小只有 10/20/50/100；筛选清算账户 8、未匹配 26、调整 3、账单状态=已作废 2（全为 D）、外部交易号精确 1、交易日区间 5（`04-`），筛选写入 URL；点 PAY-C 行 → 账单 C 详情 `?line=1` 高亮（`05-`）。**失败：** 外部交易号前缀查询结果 0 条正确，但空状态文案错误（W-1，`03-channel-lines-empty`） |
| AC-V1 | **通过** | 6 类详情面包屑均为「财务 / 对账 / 列表名 / 当前对象」，标题区有「返回列表」 |
| AC-V2 | **通过** | 6 类列表点行均进入详情路由、无抽屉；提交处置、复核仍为抽屉 |
| AC-V3 | **通过**（空状态见 W-1） | 菜单「对账」分组顺序：清算账户、银行账户、渠道账单、平台流水、**渠道流水**、银行对账、对账异常、在途汇总（`01-`）；页面风格与平台流水一致 |
| 跨链接 | **通过** | 账单 A 行平台流水链接 → 平台流水页（`clearingAccountId + externalTransactionId`，1 条，`30-`）；平台流水「渠道流水」链接 → 渠道流水页（1 条，账单 A，`31-`）；银行流水已确认匹配「账单 …」→ 账单 A 详情（`32-`） |

**未覆盖 / 说明：** 拒绝建议、提交人工多选匹配本次未点击（任务只要求至少一项操作，已验证确认建议与开异常；人工匹配提交在切片 1 验收过、本次组件未改逻辑）；清算账户详情的 MID 挂 / 摘未测（本次未建渠道 / MID）；银行账户详情的复核 / 申请关闭只做了页面打开（`11-cash-detail`）。

## 5. 缺陷与观察

| 编号 | 级别 | 现象 | 位置 | 复现 | 期望 vs 实际 |
|---|---|---|---|---|---|
| W-1 | 低（文案） | 渠道流水页在页内表单加筛选查询、结果为空时，显示未筛选时的文案「还没有渠道流水。上传渠道账单后…」 | `operations-console-web/src/reconciliation/useReconListQuery.ts` 返回 `filters: initial.filters`（挂载时 URL 的筛选，之后不更新）；`src/pages/ReconChannelLines.tsx` 第 41 行 `const filtered = Object.keys(page.filters).length > 0` 据此选文案 | 打开 `/finance/reconciliation/channel-lines` → 外部交易号填不存在的值 → 查询 | 期望「没有符合条件的渠道流水，可放宽筛选…」；实际为未筛选文案。反向同理：带筛选地址打开后点「重置」得到空结果时会误显示「没有符合条件」。只有渠道流水页用 `page.filters` 选空状态文案，其他列表只把它当表单初值，不受影响 |
| W-2 | 低（控制台警告） | 银行流水详情每次渲染打印 antd 警告「Descriptions: Sum of column `span` in a line not match `column`」 | `src/reconciliation/detail/BankLineDetail.tsx` 第 55–65 行：`column={3}` 的 8 个字段后接 `span: 3` 的「附言」，第 3 行合计 5 | 打开任一银行流水详情 | 期望无警告（如把附言改为 `span: 1` 或让前面字段凑满整行）；实际有警告，页面显示正常（截图 `14-` 中附言被挤在第 3 行右侧） |
| O-1 | 观察 | 验收脚本首轮快速连续筛选时 Gateway 返回 429，导致部分页面加载失败（首轮 7 项"失败"均为此或脚本问题，非产品缺陷） | 块 123 / 126 GET 路由限流：IP 30 次/分钟、主体 30 次/分钟、并发 2–3 | 1 分钟内对渠道流水查询 30 次以上 | 加 2.5 s 节流后复跑 0 次 429、全部通过。人工高频筛选（例如 1 分钟内连点 30 次查询）也会遇到，可在发布前评估渠道流水路由的限额 |
| O-2 | 观察 | 金额与币种之间无空格（如「12.34USD」） | 银行流水详情「金额」 | — | 与切片 1 报告观察 ③ 同类，纯展示 |

## 6. 清理

先 `stack.sh stop`（只停本次启动的 reconciliation / ops-bff / Gateway / Nginx；18115、18211、18090、18454 已释放，他人进程未动），再运行 `cleanup.py`：

| 对象 | 处理 |
|---|---|
| `mor_reconciliation_v2e2e`（验收前 15 张表均 0 行） | 删除：银行匹配 2、银行流水 3、银行对账单 1、预期流水 2、处置 2、异常 5、账单行 36、账单 6、平台流水 7、清算账户 2、银行账户 1、消费去重 7 + 7 → **15 张表全部 0** |
| `mor_channel_connector_v2e2e` | 本次未建渠道 / MID / 技术账号，无数据 |
| `mor_platform_v2e2e` | 删除 2 名合成人员（MAKER、CHECKER）与 2 个合成角色：刷新令牌 17、会话 17、口令 2、成员角色 2、成员 2、账号 2、角色授权 6、角色 2；审计事件按先例留痕不删。块 126 注册与激活状态未动 |
| `mor_vault` | 按先例只计数不删：经真实接口新建银行账户多出 1 条 `CASH_ACCOUNT_NUMBER_V1`（现共 5 条） |
| RabbitMQ | 临时 vhost `/mor-recon6`（含观察队列）整体删除（204） |
| Redis db 3 | `local-recon6:*` 76 个键已删除 |
| Nacos | reconciliation 临时实例随进程停止注销；ops-bff 未注册 |

运行记录（含合成人员口令）只在会话 scratchpad `recon6/`，未写入仓库。
