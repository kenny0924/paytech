# Platform 邮件投递事件（Amazon SES）：交付记录

## 阶段

| 阶段 | 状态 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（[requirement.md](./requirement.md)，Q-01～Q-10 按建议） |
| 2. 现状差异与影响分析 | 已确认（[analysis.md](./analysis.md)，R-01～R-04 按建议） |
| 3. 方案、接口与数据设计 | 已确认（[technique.md](./technique.md)、[ddl.sql](./db/platform-support-service/ddl.sql)） |
| 4. 研发实现 | 已确认（见下方阶段 4 记录；偏差见 technique.md 第 7 节；分支覆盖率低于基线 0.41 个百分点，未要求补齐） |
| 5. 综合验证与本地验收 | 已确认（见下方阶段 5 记录） |
| 6–7. test 发布准备与部署 | 已确认（D1 A、D2 A；[release/release.md](./release/release.md) 第 7 节：真实链路 8/8、页面 5/5） |
| 8. 代码提交与交付归档 | 完成，已推送（代码 `762b785`、归档 `b59ec6b`，见下方阶段 8 记录） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-17 | 立项 | 看到"接收 SES 投递事件"建议后回复"开始做下一个需求" | 阶段 1 需求评审 |
| 2026-10-17 | 1→2 | "可以"：确认需求范围、AC-01～AC-13 与 Q-01～Q-10 全部按建议 | 阶段 2 现状差异与影响分析（只读核对，产出 analysis.md） |
| 2026-10-17 | 2→3 | "继续"：确认缺口判断、影响范围与 R-01（Spring 定时拉取取代 Q-08 的 XXL-Job）、R-02（联网下载 SQS SDK）、R-03（旧尝试事件规则）、R-04（运行状态不推进版本） | 阶段 3 方案、接口、数据与页面设计；不写业务代码、不执行 SQL |
| 2026-10-17 | 3→4 | "继续"：确认方案、接口、数据与页面设计；授权联网下载 SQS SDK、对本机 `mor_platform`、`mor_platform_e2e` 执行 ddl.sql | 阶段 4 研发；不碰 test 环境、不提交 |
| 2026-10-17 | 4→5 | "继续"：确认阶段 4 结果与偏差；同意按所列范围进入阶段 5。随后选择"等 Webhook 会话用完再换"本机 Platform、BFF | 阶段 5：本机重新打包并重启 Platform、BFF、Web，开启事件拉取；在 `mor_platform_e2e` 写入合成事件数据验收页面，结束后清理；不碰 test 环境、不提交 |
| 2026-10-17 | 5→6 | "进入"：确认阶段 5 | 阶段 6 发布准备：只读核对 mor-test 与发布清单，不执行 test 变更 |
| 2026-10-17 | 6→7 | "继续"：确认发布清单，D1、D2 按建议（A、A） | 阶段 7：等 Risk、Gateway 幂等会话发布并提交后，本地提交本需求（不推送）；备份、构建、`mor_platform` DDL、Compose 开启事件拉取、同批替换 Platform / BFF / Web；拿到队列 URL 后配置 `SES_PRIMARY`；临时调用方 + 模拟地址验收后清理；不碰生产 |
| 2026-10-17 | 7→8 | "可以"：确认阶段 7；授权本地提交 Compose 修改、发布记录、test 验收脚本与截图、交付记录与台账 | 本地提交；不推送 |
| 2026-10-17 | 8 | 用户自行推送 `b59ec6b`（连同 `84e2c4c`）后回复"我推送了，你看下"；核对后回复"做" | 把交付记录与台账中的推送状态改为已推送并本地提交；不推送 |

## 阶段 4 研发记录（2026-10-17）

- **依赖与 SQL**：经本机 Nexus 下载 `software.amazon.awssdk:sqs:2.31.50`；ddl.sql 已在本机 `mor_platform`、`mor_platform_e2e` 执行（执行前确认新列不存在；两库各新增 provider 4 列、attempt 5 列）。
- **Platform**：DataObject / Mapper / Repository / PDManer / schema 同步；`SesEventParser`、`SesEventQueue`、`SesEventReceiver`、`SesEventProperties`、`SesEventConfiguration`（集成层）；`NotificationEventService(Impl)`；`PollNotificationEventsProcessor`；`ext/job/NotificationEventPollJob`；账号新建 / 编辑接收 `eventQueueUrl`，列表与发送记录详情输出新字段；审计字段白名单加 `EVENT_QUEUE_URL`；配置 `notification.ses.events.*`（默认关闭）。
- **BFF / Web**：模型加字段；发送账号编辑弹窗加「事件队列 URL」、列表加「投递事件」列；发送记录尝试行显示投递结果与投诉。
- **验证**：Platform 全量 698/698（全服务覆盖率门禁既有未达标，指令 0.63、分支 0.44）；之后新增的 `SesEventContractTest` 20 项与拉取用例 3 项在通知相关子集中运行，257/257。Delivery 通知类覆盖率 96.05% / 89.33%（基线 95.96% / 89.74%，分支低 0.41 个百分点，未覆盖的是防御分支）；新增类 96.70% / 88.94%。BFF 全量 1378/1378；Web 1060/1060、类型检查通过；需求站 127/137（10 项为其他会话既有失败）。

## 阶段 5 本机验收记录（2026-10-17）

- **环境**：等 Webhook、Risk 会话先后用完本机 Platform 和运营 BFF，11:13 起由本会话接管。Platform 与 BFF 在同一次 Maven 构建中打包（临时聚合 POM，`package -DskipTests`，不把 `platform-support-api` 安装进本地仓库），构建基于当时的工作区，包含 Webhook、Risk 会话未提交的改动（BFF 保留 Risk 的 featureCatalogs）。运营 Web 是 Vite 开发服务，直接使用工作区源码，未重启。
- **验收期间的 Platform 启动参数**：在本机标准参数之外加 `notification.ses.enabled=true`、`notification.ses.events.enabled=true`、`poll-interval=10s`；SES 发信和 SQS 的 endpoint 都指向 `http://127.0.0.1:9`，不访问真实 AWS。
- **发现**：首次启动只开了 `notification.ses.events.enabled`，拉取任务没有注册。原因是事件拉取按设计还要求 `notification.ses.enabled=true`。这不是代码缺陷，但阶段 6 配置 mor-test 时，两个开关要同时打开（mor-test 已开发信，只需加事件开关）。
- **数据**：在 `mor_platform_e2e` 执行 [acceptance-fixture-local.sql](./db/platform-support-service/acceptance-fixture-local.sql)，共 3 个静态状态账号，以及 1 条记录（4 个投递、5 次尝试）。验收脚本还经接口新建了 `E2E_SES_EVT_LIVE`，录入的是合成密钥。
- **接口验收**（[local_acceptance.py](./verification/local_acceptance.py)，经 Nginx → Gateway → BFF → Platform）20/20 通过：
  - 队列 URL：非 SQS 地址和纯空格返回 422 并指向该字段；空串清空后版本 +1，审计记下修改前后值。
  - 拉取：无密钥的账号不拉取；有密钥但 endpoint 不可达时，经真实拉取链路记为 `SQS_UNAVAILABLE`；多轮拉取后账号版本不变；响应中不含密钥。
  - 状态与记录：3 种静态状态正确；记录状态计数正确，「已送达」筛选生效；尝试明细显示送达、硬退信分类、软退信与投诉，经事件确认的旧尝试来源为 `PROVIDER_EVENT`；响应中不含地址 Token、参数值和消息标识。
  - 第一轮有 3 项失败，都是脚本问题：审计应查详情接口；AC-09 误匹配了业务键；拉取任务没启动的原因见上面的「发现」。修正后整轮重跑通过。
- **浏览器验收**（[local_browser.py](./verification/local_browser.py)）14/14 通过，截图 `verification/stage5-*.png`：
  - 账号列表「投递事件」列的 4 种状态：接收正常、等待首次拉取、未配置、拉取失败。
  - 编辑弹窗回填队列 URL，格式错误当场提示，清空后提示消失。
  - 发送记录详情显示投递结果、退信分类、投诉，来源显示「投递事件」。
  - 全程无控制台错误，无失败请求。
- **清理**：执行 [acceptance-cleanup-local.sql](./db/platform-support-service/acceptance-cleanup-local.sql) 后，`E2E_SES` 账号、合成记录与模板均为 0。审计事件按约定保留；LIVE 账号的合成密钥留在本机 Vault（假值）。Platform 已按标准参数重启（事件拉取、SES 发信关闭），包仍用本次构建，与 BFF 配套。
- **未覆盖**：真实 SES → SNS → SQS 链路（送达、退信、投诉事件真正到达并回写），要等阶段 6–7 在 mor-test 配好 AWS 资源后验收。

## 阶段 8 归档记录（2026-10-17）

- **代码提交：** `762b785`（阶段 7 前按 D1 A 经私有索引提交，80 个文件，只含本需求）。该提交已随 Client Console 会话推送 `84e2c4c` 时进入 `origin/main`（非本会话推送）。
- **归档提交：** `b59ec6b`：Compose 事件开关、发布记录与 test 覆盖文件、test 验收脚本与截图、交付记录、`agent-docs/CURRENT_IMPLEMENTATION.md` 台账；用户 2026-10-17 自行推送，已在 `origin/main`。

## 遗留项

- SES 账户仍在沙箱：mor-test 普通收件人收不到认证 / 邀请邮件；如需真实投递，需在 SES 申请生产访问。
- 生产环境上线须按发布清单第 3 节另建 AWS 资源，且发信策略同时放行默认 Configuration Set。
- Delivery 分支覆盖率比基线低 0.41 个百分点（防御分支），未补。
