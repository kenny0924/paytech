# Code-MoR 当前实现状态

最后核对：2026-09-16（Client 本地提交前复核）

状态：Current status ledger。本页只汇总实现、验证、部署和已决定待迁移事项；架构与业务规则仍由 [`DOCUMENT_GOVERNANCE.md`](./DOCUMENT_GOVERNANCE.md) 路由的主题 Owner 定义。

## 1. 状态解释

- `Implemented`：当前主工作区存在代码或资产，不代表已经通过全部门禁。
- `Verified`：列出的检查曾针对对应实现和环境通过，不外推到其他工作区或环境。
- `Deployed`：只表示明确写出的环境，不代表生产部署。
- `Pending`：结论已确认，但对应代码、模型、物理库、验证或部署仍未完成。

## operations 明细账运维（2026-10-30，已部署 mor-test，已本地提交 `a1f3d627`，未推送）

- **范围：** [需求](<../requirements/20261030/operations-明细账运维/requirement.md>)、[技术方案](<../requirements/20261030/operations-明细账运维/technique.md>)、[交付记录](<../requirements/20261030/operations-明细账运维/delivery.md>)、[运行卡](<../requirements/20261030/operations-明细账运维/release/runbook.md>)、[阶段 5 报告](<../requirements/20261030/operations-明细账运维/verification/stage5/stage5-report.md>)、[阶段 6 报告](<../requirements/20261030/operations-明细账运维/release/stage6-report.md>)、[阶段 7 验收](<../requirements/20261030/operations-明细账运维/release/stage7-acceptance.md>)。
- **Implemented：** ledger 新增账簿入账进度查询 `PostingProgressQueryApi`（关账第 3 步改用同一判定服务，行为不变）；ledger 读 account / billing 的响应上限由 8192 B 改为 4 MiB / 8 MiB（追平每页 200 批与大事实读取，影响 ledger 全部经 HTTP 的读取）；account 重建响应新增 `changedAccountCount`；Operations BFF 4 个接口（进度含卡住批次、批次浏览、追平、重建）；运营 Web"财务 → 明细账运维"页与批次浏览子页（结果条含时间与操作人）；Platform 能力清单与注册块 148（查看沿用 `FINANCE_ACCOUNTING_READ`，执行新增 `FINANCE_SUBLEDGER_MANAGE`）。无新表、无 DDL。
- **Verified（2026-10-30）：** 本机综合验证（合成 OE ZQ344：改余额 → 重建、ledger 落后 600 批 → 追平、规则拒绝 → 补回重放、并发 409、分段不可用、zh-CN / en × 1440 / 390，截图 199 张），200 批追平 8 个样本中位数约 5.2 s（保留 200）；发布树 ledger 341 / 0、account 73 / 0、Platform 受影响 6 类 55 / 0、Operations BFF 2573 / 0、arch-rules 70 / 0、ops-web `tsc` / 1702 单测 / build；mor-test 验收 V-0—V-8 全部通过。本机 `mor_ledger_v2e2e` 补执行 2026-10-19 科目分级迁移（经授权）。
- **Deployed：** mor-test 2026-10-30 17:22—17:28Z（tag `plops-20261030a`）：ledger `b65223c1…`、account `6ad6a22b…`、Platform `1bfed883…`、Operations BFF `4bdfce6e…`、运营 Web `5784fa81…`；块 148 激活（ACTIVE 路由 671 → 675）；运营语言包 #30。验收合成只读账号已停用、角色权限已撤销。
- **Pending：** 未推送；200 批追平耗时未在 mor-test 复测（需制造落后，按 S-03 未做）；新标签页在 ledger 从未可用时账簿下拉仍显示主键（已知限制）。

## payout 收款账户与余额解释（S1，2026-10-30，已部署 mor-test，已本地提交 `b92c0fde`，未推送）

- **范围：** [需求](<../requirements/20261030/payout-收款账户与余额解释/requirement.md>)、[技术方案](<../requirements/20261030/payout-收款账户与余额解释/technique.md>)、[交付记录](<../requirements/20261030/payout-收款账户与余额解释/delivery.md>)、[运行卡](<../requirements/20261030/payout-收款账户与余额解释/release/runbook.md>)、[阶段 6 报告](<../requirements/20261030/payout-收款账户与余额解释/release/stage6-report.md>)、[阶段 7 报告](<../requirements/20261030/payout-收款账户与余额解释/release/stage7-report.md>)；依据 [2026-10-30 出款模型决定](./decisions/2026-10-30-payout-settlement-payout-model.md)。
- **Implemented：** 新服务 `paytech/payout`（api / core / app / test，`payout-service` 18117 / 19117）；`mor_payout` 三表 `t_beneficiary_account`（31 列含生成列 `active_key`）、`t_beneficiary_account_event`、`t_compensation_event`（PDManer 为 9 表目标模型，S1 只建 3 表；`initialize.py` 不对 payout 自动执行 Schema）；接口 P1—P12（提交、Maker / Checker 四眼核验、24 h 冷却、设默认、停用 / 恢复 / 关闭、Reveal、核验队列、字段要求、余额解释）；Vault REFERENCE + STABLE Token、跨 Owner 共用提示；核验结果经补偿事件调用 Platform 通知。account 新增 Owner 余额 positions 接口；Platform 通知新增租户权限收件人（`tenantRecipients`）、Client 角色加 3 项 payout 权限、运营能力清单 6 项；Operations BFF `/payout-admin/v1` 12 个接口、Client BFF `/client-payout/v1` 7 个接口；运营 Web 两页（财务 → 出款：收款账户核验、Owner 账户与余额）与 `payout` 命名空间；注册块 147；test-environment 新服务落位（IP `172.30.73.44`、Worker 16、探针、build / prepare / initialize、base compose）。
- **Verified（2026-10-30）：** 候选树 payout 33 / 0、account 72 / 0、Platform 受影响 6 类 41 / 0、Client BFF 108 / 0、Operations BFF 2543 / 0、arch-rules 70 / 0、ops-web `tsc` / 1686 单测 / build；本机阶段 5 联调与浏览器走查；mor-test 离线演练块 147（652 → 671 路由）；mor-test 运营侧验收 api 64 / 0、AC-09 运营侧 9 / 0、数据检查 6 / 0、浏览器 zh-CN / en × 1440 / 390 通过。
- **Deployed：** mor-test 2026-10-30 14:01—14:08Z（tag `payout-s1-20261030a`）：payout `75a6337c…`、account `8dfd1060…`、Platform `0ea9cc2d…`、Client BFF `24d3aa05…`、Operations BFF `626a74a7…`、运营 Web `acb33e94…`；`mor_payout` 库与 `payout_app` 账号、Vault 策略版本 22、块 147 激活、运营语言包 #29、XXL 分组 19 / 任务 29。验收合成数据已清理。
- **Pending：** Client 门户页面未做（Client 原型暂缓）；Client 侧验收（Client 入口、设默认、Client 余额页、TENANT_VIEWER 分支、同键重放）与 AC-09 实际送达待 Client 所有者凭据；Owner 按名称搜索无 client-master 合同；未推送。

## risk-decision 限额档位（REQ-04，2026-10-29—30，已部署 mor-test，已本地提交 `b369e9b6`，未推送）

- **范围：** [需求](<../requirements/20261029/risk-decision-限额档位/requirement.md>)、[技术方案](<../requirements/20261029/risk-decision-限额档位/technique.md>)、[交付记录](<../requirements/20261029/risk-decision-限额档位/delivery.md>)、[运行卡](<../requirements/20261029/risk-decision-限额档位/release/runbook.md>)、[阶段 6 报告](<../requirements/20261029/risk-decision-限额档位/release/stage6-report.md>)；依据 [2026-10-28 客户上线决定](./decisions/2026-10-28-client-go-live-orchestration.md)（Proposed）。
- **Implemented：** `mor_risk_decision` 新增 `t_client_limit_tier`（每 Client 一行，档位 VERIFICATION / OBSERVATION / STANDARD / RESTRICTED，来源 UNDERWRITING_INIT / CHANGE_REQUEST / MIGRATION）、`t_client_limit_tier_change`（申请与四眼，每 Client 至多一个待审批）、`t_client_allowed_daily_total`（按 Client × 币种 × UTC 日的放行汇总）；四张 TIER 名单为档位投影（同事务同步，禁止手工成员）；任意承保批准时为无档位 Client 初始化 VERIFICATION（F-1 (a)）；四权限 READ / REQUEST / APPROVE / APPROVE_ELEVATED，按冻结 CRR 分级（LOW 普通，其余高级）；新累计方式 `DAILY_SUM_AMOUNT`（日 = UTC 自然日，近 30 天 = 含当天 30 个 UTC 日）与放行事件 `PAYMENT_ATTEMPT_ALLOWED` / `CHECKOUT_ALLOWED`；迁移内部接口 L12（白名单，默认关闭）。额度为风控常量 `TIER_{TIER}_{CCY}_{SINGLE|DAY|MONTH}`（MONEY 固定币种，评估时读当前值，改值即时生效、无四眼）。结账点只做档位 / 受限 / 币种集合，额度在支付尝试点判定。ops-bff / ops-web 风控 → 限额档位（列表统计与筛选、档位页额度与已用、上线复核清单、申请 / 审批 / 撤回、历史）；Platform 注册块 145（4 权限、4 资源、11 路由）与能力清单；data-query 模板 `GOLIVE_LIMIT_TIER`。api-gateway Platform Snapshot 响应上限 1 MiB → 4 MiB（D-9：块 145 激活后 1,049,930 字节超限）。
- **Verified：** risk-decision 1839、ops-bff 2531、arch-rules 70（提交树复测 70）、ops-web 1672、api-gateway 1313；本机 e2e 与浏览器走查（zh-CN / en × 1440 / 390）；离线演练（DDL、块 145 编译与 Snapshot 字节 / Gateway 解码复核、风控配置）；F-4 实测档位流程每次评估约 205 条 SQL，4 并发 25.5% 超时。
- **本机：** auto 库与 `mor_risk_decision_v2e2e` 已前滚；`mor_platform_v2e2e` 块 145 激活；e2e risk-decision 为本需求构建（`*.pre-rq04`）；ops-bff 现为 payout 会话的 main + REQ-04 + payout 合并构建（`*.pre-payout`；`*.pre-rq04` 保留）；运营 Vite 18315；本机语言包 #20。
- **mor-test（2026-10-30 09:38—10:30Z，`rq04-20261030b`）：** risk-decision-service `1414384e…`、operations-console-bff `4bd4c3df…`、platform-support-service `af13e77a…`（含 `1c8341a2`）、operations-console-web `2f7f8780…`、api-gateway `d51daee3…`；3 表 + TIER 名单；块 145 激活（652 路由）；语言包 operations-console #28；`t_client_limit_tier` 表级 GRANT 与模板 `GOLIVE_LIMIT_TIER`；风控 48 变量、7 规则集、2 流程，`PAYMENT_ATTEMPT_RISK` 发布 `241866473545502720` 与 `CHECKOUT_PREPARE` 发布 `241866478381535232` ACTIVE（回滚基线 `236518950932942848` / `237117104682274816`，先 R13 再回滚镜像）；4 个存量 Client 迁移为 STANDARD，验收 Client L / H 为 VERIFICATION；4 个 `RQ04_ACCEPTANCE_*` 账号。**档位门禁已生效：mor-test 无档位 Client 的结账与支付被拒。** 验收：冒烟 5/5、接口 39/39、一致性 12/13（失败项为重启窗口与两次已知 504 的历史 ERROR）、浏览器 10/10。之后发布这五个服务以本需求为基线。
- **Known gaps：** 额度默认值（Q-1）待风控确认；**REQ-04c 风控配置缓存是生产激活档位发布的前提**；审批面板只在本机走查覆盖；payout 检查点不读档位（用户 2026-10-30 决定暂不做）；单 Client 个性化额度与额度变更四眼未做；Gateway 旧镜像无法加载块 145 激活后的 Snapshot（回滚须先停用块 145）。

## platform-support 多语言发布变更记录（2026-10-30，已部署 mor-test，已本地提交 `1c8341a2`，未推送）

- **范围：** [需求](<../requirements/20261030/platform-support-多语言发布变更记录/requirement.md>)、[技术方案](<../requirements/20261030/platform-support-多语言发布变更记录/technique.md>)、[交付记录](<../requirements/20261030/platform-support-多语言发布变更记录/delivery.md>)、[发布手册](<../requirements/20261030/platform-support-多语言发布变更记录/release/runbook.md>)。来源：发布预检逐个读取线上语言包（每包一次 Vault REVEAL + 新建存储客户端），mor-test 5—10 s、偶发 504。
- **Implemented：** 新表 `t_i18n_release_change`（每次发布相对上一发布号的 ADDED / CHANGED / REMOVED 文案，发布 1 为全量基线）与 `t_i18n_release.change_count`（NULL = 未生成明细）；发布在登记事务内写变化行（每批 ≤ 1000 行分配 Snowflake 主键）；预检、发布摘要、版本差异按明细还原发布内容（一条 SQL 取每个 key 不大于目标发布号的最后一行）后按 ReleaseDiff 比较，不读语言包、不调 Vault，接口合同不变；任一不大于目标号的发布未补录时报「发布变更明细缺失」；删除读包端口 `ReleaseBundleReader`。一次性补录脚本 `release/scripts/backfill_release_changes.py`（`plan` 生成可审阅 SQL、按发布单事务且以 `change_count IS NULL` 抢占、幂等；`verify` 逐发布与语言包核对）。
- **Verified：** 多语言 HTTP 与模块测试 69（含 `ReleaseChangeApiTest` T1—T9、4205 行大基线），与联合查询合并后多语言 + 数据查询 143；本机 e2e API 12/12、浏览器 zh-CN / en × 1440 / 390；mor-test 修复前后同一脚本：ops 预检 504 / 4.5—7.0 s → 0.5—1.05 s，常规差异 2.4—8.9 s → 0.09—0.58 s，存储凭据解密 958 → 0，摘要与独立基准一致，写入验收（改译文并还原、client-console #9 相同内容发布 `change_count=0`）通过；联合查询 c 版发布 ops #27 记录 2 行，`verify` 36/36。
- **本机：** auto 库 `mor_platform` 与 e2e `mor_platform_v2e2e` 已执行 DDL，v2e2e 已补录；e2e Platform 为联合查询 + 本需求合并构建（pid 14518，旧 JAR `*.pre-i18nchg`）。
- **mor-test（2026-10-30 08:55—09:05Z）：** `mor_platform` DDL 与补录（26,620 行，34 个发布）；platform-support-service `a3003ef6…`（`i18nchg-20261030b` = 联合查询 `c82b6f34` 只替换应用 JAR），回滚 `c82b6f34`（旧代码不读新表，DDL 可保留），备份 `Code-MoR-backups/i18nchg-20261030/mortest-20261030T085512Z`；client-console 语言包 #9 为验收发布。
- **Known gaps：** 之后的 Platform 构建必须包含 `1c8341a2`（`69f87bbf`）；不含它的 Platform 发布的版本没有明细，换回后预检报「发布变更明细缺失」，重跑补录脚本即可。AC-06 最大差异（#19 对 #1，19,618 条、2.65 MB 响应）13 次中 2 次 1.1—1.2 s（中位数 0.53 s），已接受为遗留。

## platform-support 联合查询（2026-10-29—30，已部署 mor-test，已本地提交 `d673817f`，未推送）

- **范围：** [需求](<../requirements/20261029/platform-support-联合查询/requirement.md>)、[技术方案](<../requirements/20261029/platform-support-联合查询/technique.md>)、[交付记录](<../requirements/20261029/platform-support-联合查询/delivery.md>)、[运行卡](<../requirements/20261029/platform-support-联合查询/release/runbook.md>)；依据 [2026-10-29 联合查询决定](./decisions/2026-10-29-data-query-joint-query.md)，讨论稿 [design-discuss/joint-query](../design-discuss/joint-query/design.md)。
- **Implemented：** data-query 新增键类型注册表 `t_query_key_type`（分组 `group_name`、默认角色、入口正则与提示、排序），模板版本新增 `joint_json`（行主体键类型、键参数、列标注，与参数 / 列声明分开）；保存校验与参与评估、键类型图（不缓存）与可达预览、按跳分步执行（单跳 8 秒预算、并发 8、单跳 20 次、`rowCap`、`EXECUTOR_BUSY`）；8 个接口（键类型 K1—K5、联合查询 J1—J3，J3 含 `loadedSubjects` / `alreadyLoaded`：“按自身 ID 查”的模板跳过已加载对象），模板接口增 `joint` / `jointQuery`；注册块 144（8 路由、2 菜单、29 个键类型）。ops-bff 同路径转发；ops-web 联合查询页（链路总览 + 节点明细、未查全汇总与重试、按客户筛选、以此查询、覆盖范围抽屉、390 折叠卡片）、键类型页（按分组拖动排序）、模板编辑器“联合查询”页签；页面按对象合并显示（同一对象经多路径只显示一次，注明“也可经 … 到达”），被跳过视图可按需查看。
- **Verified：** 发布树（main `6eaf1bd3` + 本需求）data-query 48、workflow 71、arch-rules 70、platform-test 132、ops-bff 2519、ops-web 1654 / tsc / build；本机 e2e API 41/41、浏览器 zh-CN / en × 1440 / 390 87 项；mor-test 离线演练（块 144 编译 633 → 641 → 633）；mor-test 验收：首发 API 22/22、浏览器 88 步；修订 b API 32/33（跨路径重复由修订 c 解决）、浏览器 94 步；修订 c API 15/15、浏览器 114 步 0 失败（含税务 / 承保 / 模板回归）。
- **本机：** `mor_platform` 与 `mor_platform_v2e2e` 前滚 DDL；v2e2e 块 144 激活与本机专用 `60-data-query-local.sql`（执行授权、块 131 其余路由与菜单组）；`mor_data_query_fixture` 7 张 `t_jq_*` 链路表；本机语言包 #19；e2e ops-bff 为联合查询 + 税务构建（pid 73742），e2e Platform 已被多语言发布变更记录会话在本需求 Platform 上叠加其提交（pid 14518）。
- **mor-test（2026-10-30，现行）：** platform-support-service `a3003ef6…`（`i18nchg-20261030b` = 本需求修订 b `c82b6f34` + 多语言发布变更记录 69f87bbf）、operations-console-bff `2452aaa7…`（`jq-20261030b`）、operations-console-web `6f8161c2…`（`jq-20261030c`）；`mor_platform` 前滚 DDL；块 144 激活（routeCount 641）；键类型 29 个（3 个子行键类型经运营 API 新增）；`data_query_demo_reader` 增 `mor_payment` / `mor_billing` / `mor_account` SELECT；语言包 operations-console #25 / #26 / #27；数据源 MOR_PAYMENT / BILLING / ACCOUNT_REPLICA 新建、MOR_CHANNEL_CONNECTOR_REPLICA 启用，34 个 JQ_* 演示模板保留（3 个子行模板为修订 c 版本）。发布历程：07:00Z 首发 `jq-20261030a` → 08:41Z 修订 b（已加载对象跳过）→ 09:07Z 修订 c（页面按对象合并、子行键类型）。回滚：ops-web `d236f1d5…`（b）/ `da81d4b0…`（a），ops-bff `34ce01c1…`（a），Platform 由 1b 协调（`c82b6f34` / `9444b483`），备份 `Code-MoR-backups/jq-20261030/mortest-20261030T065816Z`。
- **Known gaps：** mor-test 无完整真实链路（只有 AE001、无 SAAS_AUTOMATED 订单、计费事实支付引用为合成 ID），验收以 5 个入口组合覆盖 6 个泳道，支付泳道多为 0 行节点；“一个模板支持多个键参数”为后续优化；本机 MinIO 语言包在 Vite 18316 下跨域失败（既有）。

## tax 结账算税与事件登记（2026-10-29—30，已部署 mor-test，已本地提交 `fa21d5ce`，未推送）

- **范围：** [需求](<../requirements/20261029/tax-结账算税与事件登记/requirement.md>)、[技术方案](<../requirements/20261029/tax-结账算税与事件登记/technique.md>)、[交付记录](<../requirements/20261029/tax-结账算税与事件登记/delivery.md>)、[运行卡](<../requirements/20261029/tax-结账算税与事件登记/release/runbook.md>)、[阶段 7 报告](<../requirements/20261029/tax-结账算税与事件登记/release/stage7-report.md>)；依据 [2026-10-29 tax 决定](./decisions/2026-10-29-tax-domain-fact-service.md)（Confirmed，含第 17 条首期由平台运营代操作）。
- **Implemented：** `mor_tax` 重建为 16 表（准入、登记、规则包、计算、正式交易与 OUTPUT 底稿、税票序列与单据、补偿事件等）；tax 提供 CalculateTax 等 3 个内部接口，消费 `OrderPaymentSucceeded`（`tax.acquiring.p0—p3`）登记正式事实并开具税票（经 Platform 文件服务存 PDF）。acquiring 建单暂估、消费者资料提交后重算为最终、BLOCKED 不可付（新表 + 3 表改动，固定地址 `ACQUIRING_TAX_SERVICE_URL` 调 tax）；billing 新增 `SELLER_TAX` 行（本金减税，收方 TAX_AUTHORITY），account 一处映射，ledger 合并为 `TAX_PAYABLE`；billing 不调用、不等待 tax。ops-bff / ops-web 新增"财务 → 税务查询"（订单税务事实与税票下载，`TAX_VIEW`）。CLIENT 来源订单 NOT_MANAGED，未知税类 BLOCKED。
- **Verified：** tax 62、acquiring 916、billing 142、account 68、ledger 332、ops-bff 2481、messaging 73、arch-rules 7、ops-web 1631；本机 e2e S1—S5 与 AC-10（tax 停机补登记）通过、浏览器 zh-CN / en × 1440 / 390；mor-test 离线演练（块 146 编译 631 → 633 → 631，SQL 前滚 / 回滚 / 拒绝路径）；mor-test 验收 A1—A4、A7 通过（合成事件驱动 billing → account → ledger，SELLER_TAX 15.00 = TAX_PAYABLE 15.00）。
- **本机：** auto 库 `mor_tax` 重建、`mor_acquiring` 迁移；e2e `mor_tax_v2e2e`（AE001 Party `900000000001380102`）、`mor_acquiring_v2e2e` 迁移；e2e tax（18116）首次运行，acquiring / billing / account 为本需求构建（`*.pre-tax`），acquiring 已切 REAL；ops-bff 为 REQ-03 + tax 合并构建；本机 payment JAR 更新到 `e3e0c12f`；本机语言包 #17 / #18。
- **mor-test（2026-10-30 05:49Z）：** tax-service `a5616e20…`、billing-service `adfd0817…`、account-service `8a21dab8…`、acquiring-service `843382e3…`、operations-console-bff `e243d57b…`、operations-console-web `748ca7b9…`（`tax-20261029a`，源码 = `fa21d5ce`）；`mor_tax` 16 表 + 初始化（AE001 Party `880000000702`）、`mor_acquiring` 迁移、billing 注释；块 146 激活（routeCount 633）；XXL 分组 18 / 任务 28；语言包 operations-console #24。Platform 未随发（能力清单缺 `OPERATIONS_FINANCE_TAX`，只影响菜单资源管理页的编辑）。
- **Known gaps：** mor-test 上非 AE / SA / EG 或税类非 `SAAS_AUTOMATED` 的 MoR 订单一律 BLOCKED（D-1 已接受，他人脚本需改）；验收 S1 订单在 acquiring 未付、tax / billing 已按合成事件记为已付；汇率源未接（底稿 PENDING `FX_RATE_MISSING`）；阿拉伯语税票待补；Partner 门户操作未做（首期运营代操作）；tax 上传失败日志无原因；acquiring 迁移有新单后只能前滚修复。

## risk-decision 承保记录（REQ-03，2026-10-29，已部署 mor-test，已本地提交 `9d272ae6`）

- **范围：** [需求](<../requirements/20261029/risk-decision-承保记录/requirement.md>)、[技术方案](<../requirements/20261029/risk-decision-承保记录/technique.md>)、[交付记录](<../requirements/20261029/risk-decision-承保记录/delivery.md>)、[运行卡](<../requirements/20261029/risk-decision-承保记录/release/runbook.md>)；依据 [2026-10-28 客户上线决定](./decisions/2026-10-28-client-go-live-orchestration.md)（Proposed）。归属 risk-decision（非 compliance）。
- **Implemented：** `mor_risk_decision` 新增 `t_client_underwriting`（每版本一行、进行中 / 生效两槽、冻结输入快照与来源坐标）、`t_underwriting_rule_version`、`t_underwriting_rule_threshold`（规则 v1、USD 50,000，排除清单空）；状态 DRAFT → SUBMITTED → APPROVED / DECLINED，修订使旧 APPROVED SUPERSEDED；自动承保（CRR LOW、流水 ≤ 币种阈值、MCC 非卡组织高风险且不在排除清单，8 项固定检查）为页面一键、按固定标准模板、来源 `AUTO_RULE`；HIGH 四眼（复核独立路由 + 复核人 ≠ 提交人）；估算敞口 = 月流水 × 风险天数 / 30（参考值）；保证金只记要求（执行归 REQ-18）；接口 `/risk-underwriting/v1` U1—U12（U1"全部"每 Client 最新版本、状态页签含旧版本），日志只记元数据；REQ-04 进程内读取 `ClientUnderwritingQueryService.findEffective`。ops-bff 承保工作台聚合（每次命令重取 compliance / client-master / Platform 输入）与转发；ops-web 风控 → 承保列表与承保页、客户详情 / 入驻审核入口；Platform 注册块 143（3 权限、菜单 + 2 按钮、12 路由）与 `console-resource-capabilities.json`；data-query 表级授权 + `MOR_RISK_DECISION_REPLICA` + `GOLIVE_UNDERWRITING_APPROVED`。角色组合：风控承保员 = `RISK_DECISION_CONFIG_READ` + `RISK_UNDERWRITING_READ` + `RISK_UNDERWRITING_MANAGE`，风控负责人再加 `RISK_UNDERWRITING_APPROVE`（"风控管理"目录由 `RISK_DECISION_CONFIG_READ` 控制）。
- **Verified：** risk-decision 1801、ops-bff 2459、arch-rules 70、ops-web 1617；本机 e2e 接口 44 PASS、浏览器 24 步 × 4；mor-test 发布前离线演练（DDL、块 143 编译 619 → 631 → 619）。
- **本机：** auto 库与 `mor_risk_decision_v2e2e` 已执行前滚；`mor_platform_v2e2e` 块 143 激活；e2e risk-decision 为本需求构建（`*.pre-rq03`），ops-bff 现为 tax 会话的 REQ-03 + tax 合并构建（`*.pre-tax` = 本需求构建）；运营 Vite 18314；本机语言包 operations-console #15 / #16；3 个测试角色加 `RISK_DECISION_CONFIG_READ`；本机 compliance 一个案件 CRR 置空（有回滚脚本）。
- **mor-test（2026-10-29 18:46Z）：** risk-decision-service `8934f65a…`、operations-console-bff `4f08f1e6…`、platform-support-service `49f71011…`、operations-console-web `2f7722da…`（`rq03-20261029a`）；3 表 + 规则 v1；块 143 激活（631 路由）；语言包 operations-console #23；表级 GRANT 与数据源 / 模板；未建常设业务角色（`PLATFORM_ADMIN` 经块 143 有三权限）。验收批准了测试案件 L（240686862329565184）、H（241049829650845696），两 Client ACTIVE；3 个 `RQ03_ACCEPTANCE_*` 账号；API 与浏览器（33 步 × 4）通过。之后发布这四个服务以本需求为基线。
- **Known gaps：** 规则数值待风控确认；REQ-03b 承保建议策略（待办，标准模板 T+N 为空）；mor-test 验收 Client 无交付方式，敞口"无法估算"；`platform-support-initial-data.sql` 全新库第 7987 行中断（存量）；能力清单另缺 `OPERATIONS_EXTERNAL_INTEGRATIONS` 与风控测试候选两个按钮（存量）。

## client-master 业务信息补采（REQ-12，2026-10-28—29，已部署 mor-test，已本地提交 `88453366`）

- **范围：** [需求](<../requirements/20261028/client-master-业务信息补采/requirement.md>)、[技术方案](<../requirements/20261028/client-master-业务信息补采/technique.md>)、[交付记录](<../requirements/20261028/client-master-业务信息补采/delivery.md>)、[运行卡](<../requirements/20261028/client-master-业务信息补采/release/runbook.md>)；依据 [2026-10-28 客户上线决定](./decisions/2026-10-28-client-go-live-orchestration.md)（Proposed）。
- **Implemented：** `t_client_business_profile` 新增 `expected_payment_methods`（规范化逗号串，编码为 commercial `capability_code`）、`refund_policy_url`（https）、`fulfillment_type`（INSTANT / PRE_ORDER / SUBSCRIPTION / SERVICE）、`fulfillment_days`（PRE_ORDER 时 1—365）；入驻 Schema v3（三辖区 9 个，v2 退役；增量 `paytech/client-master/docs/database/sql/changes/2026-10-29-business-info-collection{,-ddl}{,-rollback}.sql`，ID 段 `92812000…`）：期望支付方式 `MULTI_SELECT` + 外部目录 `COMMERCIAL_PAYMENT_METHOD`（client-master 事务外读 commercial `ProductCatalogApi#listCapabilities` 的 ACTIVE PAYMENT_METHOD，不可用时 client-master 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`、门户经 BFF 为 503 `SERVICE_UNAVAILABLE`）、退款政策机构 EVENTUALLY / 个人 OPTIONAL（EVENTUALLY 不阻断批准，B6）、交付方式目录 `FULFILLMENT_TYPE`、天数 `visible_when fulfillmentType=PRE_ORDER`（改离 PRE_ORDER 时服务端清空）；草稿 `Kind.CODE_LIST / HTTPS_URL / INTEGER`；四项进入批准时 `t_party_profile.content_hash`。门户多选控件（390 底部面板）与校验；运营审核"申请资料"与 Client 详情展示（四项全空"未采集"、部分为空"未填写"），值解码上限 2048。两个 BFF、commercial、Gateway 无代码改动。
- **Verified：** client-master 176、client-console-bff 102、client-console-web 153、ops-web 1601；本机 e2e 接口 179 PASS、浏览器 60 步；mor-test 发布前离线演练 16 步。
- **本机：** auto 库 `mor_client_master` 已执行本需求 DDL 与补执行 2026-10-27 法务修订 DDL；e2e 库 `mor_client_master_v2e2e` 已补跑法务修订 DDL / DML 并执行本需求 DDL + v3 DML；e2e client-master 与 client-console-bff 为本需求构建（`*.pre-rq12`，一起回滚）；门户 Vite 18312、运营 Vite 18313；本机语言包 client-console #5、operations-console #14。
- **mor-test（2026-10-29 13:01Z）：** client-master-service `2b12c2e2…`、client-console-web `e17a9a0a…`、operations-console-web `0b456b8a…`（`rq12-20261029a`）；`mor_client_master` DDL + v3 ACTIVE（13:03:54Z）；语言包 client-console #8、operations-console #22。验收 API 全 PASS、浏览器 76 张通过；未提交任何申请（验收草稿已撤回）。v3 已有申请后 DML 回滚会拒绝，只能前滚修复。
- **Known gaps：** `t_client_business_profile.content_hash` 无写入路径而 Checkout 分类要求它（F-07，未立项）；compliance 登记体不含三项（随 REQ-03）；commercial 故障时出站 GET 重发约 1 秒多后 503（接受）；运营入驻配置页与 REQ-05 动作目录页 390 宽横向滚动（既有）；同一 Client 业务资料单行，撤回草稿值带到新草稿（既有设计）。

## platform-support workflow 按钮发起与数据验证（REQ-05，2026-10-28—29，已部署 mor-test，已本地提交 `af972805`，未推送）

- **范围：** [需求](<../requirements/20261028/platform-support-workflow按钮发起与数据验证/requirement.md>)、[技术方案](<../requirements/20261028/platform-support-workflow按钮发起与数据验证/technique.md>)、[交付记录](<../requirements/20261028/platform-support-workflow按钮发起与数据验证/delivery.md>)、[运行卡](<../requirements/20261028/platform-support-workflow按钮发起与数据验证/release/runbook.md>)；依据 [2026-10-28 客户上线决定](./decisions/2026-10-28-client-go-live-orchestration.md)（Proposed）。
- **Implemented：** 按流程编码发起（`definitionCode` 与 `definitionId + versionNo` 二选一，同定义同 `business_key` 已有 RUNNING 实例时返回该实例 `created=false`；`t_workflow_instance.active_slot` + `uk_workflow_inst_active`、`business_key` 改 `utf8mb4_bin`）；`DATA_QUERY` 动作（引用 data-query 模板，参数映射、输出按声明类型写入 `<actionKey>.*` 变量；系统检查满足即系统完成并级联推进，提交人为空；不能人工完成）；检查状态存 `t_workflow_action_run` 5 列 + `idx_workflow_action_check`；首查经补偿事件 `WORKFLOW_DATA_QUERY_CHECK`，XXL `workflowDataQueryRecheck` 每 5 分钟重查（最小间隔 4 分钟），"立即检查" `POST /workflow/v1/action-runs/{id}/data-query-check`；模板选择 `GET /workflow-admin/v1/data-query-templates`（带 `versionNo` / `timeoutSeconds`）；配置校验 `POST /workflow-admin/v1/action-templates/validation`（只校验不保存，问题按 code + itemKey 定位；保存仍为 400 合并文案）；data-query 新增公开执行入口 `RunDataQueryTemplateProcessor`（core `ExecuteDataQueryProcessor` 委托之）；ops-bff 三条转发，工作流运行时读超时 9 秒；ops-web 动作目录、流程编辑器、我的待办、实例详情（原型 17—20）；注册块 142（3 路由，`platform-support-initial-data.sql` 末尾为 DISABLED，另有激活 / 回滚脚本）。增量 DDL `paytech/platform-support/docs/database/sql/changes/2026-10-29-workflow-data-query{,-rollback}.sql`。
- **Verified：** Platform 测试模块 971（data-query 48、workflow 71）、ops-bff 2450、ops-web 1596；本机 e2e（阶段 5）API 38/38、浏览器 60 张、本机网关编译 510→513；mor-test 发布前离线真实编译 616→619 与回滚演练。偏差 D-1—D-9 见交付记录。
- **本机：** auto 库 `mor_platform` 与 e2e 库 `mor_platform_v2e2e` 已执行增量 DDL；`mor_platform_v2e2e` 块 142 已激活、块 131 数据查询管理路由已启用（本机脚本 `verification/stage5/40-*`）、只读账号 `golive_e2e_reader`、本机语言包 #12 / #13；e2e Platform / ops-bff / card 为本需求构建（`*.pre-wfdq` 可回滚，card 与 Platform 须一起回滚），18311 为本需求 ops-web。
- **mor-test（2026-10-29 09:42Z）：** platform-support-service `7671724696de…`、operations-console-bff `6833f484994b…`、operations-console-web `96992aba5774…`（`wfdq-20261029a`）；`mor_platform` 增量 DDL；块 142 激活（routeCount 619）；XXL 任务 27（分组 2）；operations-console 语言包 #21；data-query 模板 `GOLIVE_AGREEMENT_DRAFTED` / `GOLIVE_AGREEMENT_ACTIVE` / `GOLIVE_COLLECTION_CAPABILITIES_ENABLED`（数据源 `MOR_COMMERCIAL_REPLICA`）、动作模板 `REQ05_*`、验收定义 `ACC_REQ05_GOLIVE_COMMERCIAL`，保留 3 个 RUNNING 验收实例。验收 setup 10、check 4、scenario 14、recheck 3、浏览器 62 张通过。之后发布这三个服务以本需求为基线。
- **Known gaps：** PSP 分支、查询超时、检查中终止、权限 403 只在本机验证（mor-test 无数据 / 受限账号）；活动记录"已完成 drafted / active / collection"显示动作结果原值未翻译；arch-rules 模块清单仍未含 workflow（既有）；客户上线正式流程定义留待 REQ-00。

## commercial 开通项前置门禁（REQ-01，2026-10-28—29，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261028/commercial-开通项前置门禁/requirement.md>)、[技术方案](<../requirements/20261028/commercial-开通项前置门禁/technique.md>)、[交付记录](<../requirements/20261028/commercial-开通项前置门禁/delivery.md>)、[运行卡](<../requirements/20261028/commercial-开通项前置门禁/release/runbook.md>)；依据 [2026-10-28 客户上线决定](./decisions/2026-10-28-client-go-live-orchestration.md)（Proposed）与 [design-discuss/client-activation](<../design-discuss/client-activation/requirement-plan.md>)。
- **Implemented：** `t_agreement_capability.status` 改为成员资格 ACTIVE / DISABLED（定价覆盖看它），新增 `execution_status`（PENDING_PREREQUISITES / ENABLED / SUSPENDED_BY_PREREQUISITE / SUSPENDED_MANUALLY，Checkout 只认 ENABLED）；CLIENT 合同开通项经门禁生效：①资格（复用 `QualificationEvaluationApi`，Client + OE）②合同与价格（合同 ACTIVE、开通项生效期、ACTIVE 价格版本覆盖，`PRICE_NOT_COVERED` 带 `currencies`）④渠道身份（本期固定 SKIPPED，REQ-02 接入）；触发：新增 / 恢复 / 重新纳入 / 合同激活 / 价格激活登记补偿任务、`QualificationChanged` 消费（commercial 首个 MQ 消费者）、XXL 重查、运营「检查前置」；新接口 suspension / resumption / prerequisite-checks；`AgreementCapabilityStatusChanged` 按合同版本发布。Operations BFF / Web：执行状态徽标、"还差什么"、下一步卡片、暂停 / 恢复弹窗、编辑抽屉只能纳入 / 关闭、暂停人显示名；查看权限可检查前置，写操作需维护权限。
- **Verified：** commercial auto 247、operations-console-bff 2442、messaging 目录 12、operations-console-web 1581 + build / i18n、全 reactor test-compile 121 模块、arch-rules 70 + 5；本机 e2e 浏览器 zh-CN / en × 1440 / 390；mor-test 迁移、注册块离线编译 6 次演练；mor-test 验收 API 13 / 14（1 项为演示数据缺价格的预期暂停）+ 浏览器 en / zh-CN 1440 与回归页。
- **Deployed：** mor-test 2026-10-28 20:09—20:18Z（tag `gate-20261029a`）：commercial `806043a3…`、operations-console-bff `eca2df82…`、operations-console-web `7a053544…`，20:33Z 再换 `8aec3df8…`（`gate-20261029b`，去掉区头重复的新增开通项按钮）；`mor_commercial` 迁移（15 行 → ACTIVE + ENABLED）；`mor_platform` 注册块 141（3 路由，ACTIVE）；XXL 任务 25（scope=PENDING，每 5 分钟）、26（scope=ALL，每日 03:00 UTC）；语言包 operations-console #20；MQ `commercial.compliance.p0..p3`。回滚目标：commercial `e5c1ee6b…`、ops-bff `769edbce…`、ops-web `45ee11cb…`（须连同迁移回滚、注册块回滚与 XXL 停用，见运行卡第 5 节）。
- **Pending / 遗留：** 推送另行授权；注册块 141 未写入 `platform-support-initial-data.sql`；data-query 数据源（复用 `MOR_COMMERCIAL_REPLICA`）与 3 条 `GOLIVE_*` 模板未登记（随 REQ-05）；演示合同 CL-F1105E、DEMO-CL-002、DEMO2-CL-102、DEMO2-CL-901 缺生效价格版本、DEMO2-CL-103 的 GOOGLE_PAY / STC_PAY 缺 AED 价格，门禁已 / 将暂停（用户接受）；本机 e2e 仍运行本需求 JAR、worktree ops-web 18311 与 MinIO 跨域（含 18311，用户决定保留）。

## client-master 国家包法务修订（2026-10-28，已部署 mor-test，已提交 `ac3145f4` 并推送）

- **范围：** [需求](<../requirements/20261027/client-master-国家包法务修订/requirement.md>)、[技术方案](<../requirements/20261027/client-master-国家包法务修订/technique.md>)、[交付记录](<../requirements/20261027/client-master-国家包法务修订/delivery.md>)、[运行卡](<../requirements/20261027/client-master-国家包法务修订/release/runbook.md>)；依据 [法源研究 26 项](<../design-discuss/client-onboarding/legal-research-26-items-2026-10-27.md>)，结论回写 [design.md 9.5a](<../design-discuss/client-onboarding/design.md>)。
- **Implemented：** client-master AE / SA / EG 国家包增量 DML 与采集 Schema v2（v1 RETIRED，存量申请继续按 v1）；声明绑定资料版本 `draft_revision` 与条款版本，资料改动后声明失效并拒绝提交（`DECLARATION_STALE`，`draft_revision` 为 NULL 的存量声明按 0 处理仍有效）；受益人门槛比较方式（GTE / GT）；规则作用域 `DECLARATION` / `ATTRIBUTE`、`DATE_NOT_PAST`；修复 `RelationRules.uboSatisfied` 认定依据为空时的空指针。Client BFF / Web 声明阅读、逐项重新确认与失效提示；Operations BFF / Web 规则视图（是 / 否）、v2 声明区块与存量声明。
- **Verified：** 基于 `e2185c1b`：client-master 164、client-console-bff 101、operations-console-bff 2433、client-console-web 141、operations-console-web 1576；SQL 在 mor-test 只读导出副本试跑（DDL → DML → 重跑 → 回滚拒绝分支 → 回滚 → 再应用），与全新建库逐表一致；本机 e2e 浏览器 5 种主体 en / zh-CN × 1440 / 390 通过；mor-test 运营 Playwright 44 / 45 / 44 / 45、卡 BIN 页回归 4 组通过，参考数据会话复核通过。
- **Deployed：** mor-test 2026-10-28 05:40—06:00Z（tag `legal-20261028a`）：client-master `c9bb7b8e…`、client-console-bff `8fe7f24c…`、operations-console-bff `769edbce…`、client-console-web `3db62571…`、operations-console-web `45ee11cb…`；DDL（2 列）+ DML 已执行；语言包 client-console #7、operations-console #19。回滚目标：client-master `64e515c6…`、client-console-bff `be79550f…`、operations-console-bff `4bd7574c…`、client-console-web `c09af02a…`、operations-console-web `aec066e9…`；出现 v2 申请后 DML 回滚脚本拒绝执行，client-master 只能向前修复。
- **后续发布（2026-10-29）：** commercial 开通项前置门禁替换 operations-console-bff `eca2df82…`、operations-console-web `7a053544…`（含本需求）；这两个服务不再以本需求候选文件发布，回滚基线以该需求为准。2026-10-29 09:42Z 起 REQ-05 workflow 按钮发起与数据验证再次替换 operations-console-bff / operations-console-web（含本需求与开通项前置门禁）。
- **Pending / 遗留：** mor-test 客户门户 v2 表单与声明阅读 / 失效已于 2026-10-29 用测试账号补验通过（草稿已撤回）；自由职业文件到期日"不得早于今天"只在服务端校验；本期之外法务项见需求第 7 节（"待决定 / 待确认"项在生产发布前处理）。

## platform-support 参考数据客户端组件（2026-10-28，已部署 mor-test，已提交 `e2185c1b` 并推送）

- **范围：** [需求](<../requirements/20261027/platform-support-参考数据客户端组件/requirement.md>)、[技术方案](<../requirements/20261027/platform-support-参考数据客户端组件/technique.md>)、[交付记录](<../requirements/20261027/platform-support-参考数据客户端组件/delivery.md>)、[运行卡](<../requirements/20261027/platform-support-参考数据客户端组件/release/runbook.md>)。
- **Implemented：** 新模块 `paytech-platform-support-reference-data-client`（starter，配置前缀 `platform-support.reference-data.client`：按 `kinds` 加载 CURRENCY / COUNTRY / SUBDIVISION / INDUSTRY / MCC 到不可变内存快照，原子替换；全量轮询默认 PT1H，首次加载失败每 PT10S 重试；`ReferenceData` / `ReferenceCatalog` 的 require / requireActive / find / list 只读内存，查不到抛 `ReferenceDataNotFoundException`；MCC 每页 200 并核对总数；自带 1 MiB 上限 HTTP 客户端，容忍未知字段；始终发送 `X-Caller-Service`；不配 `base-url` 时经服务发现访问 `platform-support-service` / `/platform-support`；就绪组含 `referenceData` 健康项；`ReferenceDataRefresher.refreshNow()`）与 `-client-test-support`；arch-rules 新增 CLIENT / CLIENT_TEST_SUPPORT 角色与 R16。消费方：ledger、billing 删除复制的币种快照实现（ledger 查不到为可重投系统异常，billing 保持原 `BizCheckFailException` → 400 `BUSINESS_CHECK_FAILED`），card 国家核对、client-master 参考数据校验改用客户端，ops-bff 财务币种下拉改读 `/reference-data/currencies`。Platform 删除旧接口 `/platform/v1/currency-specs`、`/platform/v1/country-regions` 及其查询实现。0 表 / 0 DML / 0 注册 / 0 页面。
- **Verified：** worktree `Code-MoR-refclient`（基于 `5e1f16ab`）：客户端 53、arch-rules 70 + reactor 5、ledger 331、billing 131、card 67、client-master 154、ops-bff 2424、Platform 801；在 `c4ff0e20` 上重建后（`Code-MoR-refclient-rel2`）复跑 ops-bff 2433、card 82、Platform 921；本机 e2e（AC-02 / AC-03 停启 Platform、条数、调用方、旧接口 404）；mor-test 验收全部 PASS（ledger 余额 + 试算、billing 精度 / 未登记币种、client-master 76 / 0 / 4 跳过、财务下拉逐字节一致）。
- **Deployed：** mor-test 2026-10-28 04:00—04:08Z（tag `refclient-20261028b`，源 = `c4ff0e20` + 本需求）：ledger `51a57a3b…`、billing `cf580cfb…`、card `32d6f27a…`、client-master `64e515c6…`、operations-console-bff `4bd7574c…`、platform `1e54f349…`（最后发布）；Gateway 仍 613 条路由。回滚目标见 `release/rollback-targets.tsv` 与 `release/compose-rollback.*.yaml`；Platform 发布后任何消费方回滚须先回滚 Platform。
- **Pending / 遗留：** 健康明细默认不暴露（`show-details: never`），覆盖环境变量名为 `PLATFORMSUPPORT_REFERENCEDATA_CLIENT_*`；本机库残留 card INVALID 版本 6 验收记录；国家包法务修订已基于本提交重建并于 2026-10-28 部署 mor-test（client-master / ops-bff 回滚目标为本需求镜像 `64e515c6` / `4bd7574c`），部署后复核参考数据加载、财务下拉与旧接口 404 均正常。

## card BIN 库页面重设计与号段列表（2026-10-27，已部署 mor-test，已提交 `8a441c90` / `c4ff0e20` 并推送）

- **范围：** [需求](<../requirements/20261027/card-BIN库页面重设计与号段列表/requirement.md>)、[技术方案](<../requirements/20261027/card-BIN库页面重设计与号段列表/technique.md>)、[交付记录](<../requirements/20261027/card-BIN库页面重设计与号段列表/delivery.md>)、[运行卡](<../requirements/20261027/card-BIN库页面重设计与号段列表/release/runbook.md>)。
- **Implemented：** card `GET /card/v1/bin-library/versions/{versionNo}/ranges`（前缀 / 卡组织 / 借贷记 / 发卡国筛选、公共分页，只允许 ops-bff）；Platform 内部 `GET /operations-accounts/v1/labels`（登录名或脱敏邮箱，只允许 ops-bff，无公开路由）；ops-bff 号段转发与版本 / 激活记录 `createdByLabel` / `operatorLabel`（Platform 失败降级为 null）；ops-web 卡 BIN 库页面重做（号段数据 / 版本两个页签、筛选 chip、最长前缀匹配条、390 卡片列表）；块 140（号段列表路由）。
- **Verified：** worktree `Code-MoR-binlist`（基于 `5e1f16ab`）：card 18、platform-support 19、ops-bff 91、ops-web 1571；本机 e2e 浏览器 en / zh-CN × 1440 / 390 各 16 项；mor-test 接口 45 PASS（1 项为验收脚本默认分页期望错误：公共分页默认 10，前端显式传 50）、浏览器 4 组 0 失败（中文文案与国旗正常）。
- **Deployed：** mor-test 2026-10-27 20:12—20:19Z（tag `binlist-20261027a`）：platform `186347bc…`、card `380064fe…`、operations-console-bff `25f6a869…`、operations-console-web `aec066e9…`（源 = `c4b99e2d` + 本需求 ops-web 文件）；块 140 激活（Gateway 612 → 613）；语言包 operations-console #18（card 命名空间 +121 / 改 7 / 废弃 68）。回滚目标见运行卡第 6 节与 `release/compose-rollback.*.yaml`。
- **后续发布（2026-10-28）：** 参考数据客户端组件（`refclient-20261028b`：platform `1e54f349…`、card `32d6f27a…`、operations-console-bff `4bd7574c…`）与国家包法务修订（operations-console-bff 再次替换、operations-console-web `45ee11cb…`）均基于本需求提交重建上线，mor-test 复核卡 BIN 页接口 44 PASS（另 2 项为脚本分页期望与宿主机睡眠导致的 Platform 事务超时，非缺陷）、浏览器 4 组全部通过。上面 Deployed 中的镜像不再是回滚目标：回滚任一服务前须与对应需求会话确认。
- **Pending：** 无。

## platform-support 参考数据目录与 client-master 迁移（2026-10-27，已部署 mor-test，已提交 `ab27bd76` / `554072a5` 并推送）

- **范围：** [需求](<../requirements/20261027/platform-support-参考数据目录与client-master迁移/requirement.md>)、[技术方案](<../requirements/20261027/platform-support-参考数据目录与client-master迁移/technique.md>)、[交付记录](<../requirements/20261027/platform-support-参考数据目录与client-master迁移/delivery.md>)、[运行卡](<../requirements/20261027/platform-support-参考数据目录与client-master迁移/release/runbook.md>)；依据 [参考数据决定](./decisions/2026-10-27-platform-reference-data.md)。
- **Implemented：** Platform `t_currency_spec` → `t_ref_currency`、`t_country_region` → `t_ref_country`（补列），新建 `t_ref_country_subdivision`、`t_ref_industry`（两级）、`t_ref_mcc`；5 个只读接口 `/platform/v1/reference-data/*`（旧 `/currency-specs`、`/country-regions` 响应冻结）；币种维护加数字码 / 名称 / 符号 / 图标与冻结规则；client-master 移出 `t_catalog_item` 的 INDUSTRY / CURRENCY / STATE_OR_PROVINCE（48 行），`REF_*` 目录码、提交时事务外核对国家 / 币种 / 行政区、分类两级行业 + MCC 校验、删除 `/classification-options`、管理端辖区返回 `addressComponents`；client-console-bff 自有 form-schema 模型补全参考数据；两个 Web 的国家（国旗）/ 省州 / 币种控件、分类页、参考数据页、币种页；块 139；`refdata` / `reference-data` 语言包（429 键 / 应用，中英全覆盖）。
- **Verified：** worktree `Code-MoR-refdata`（基于 `aba76a1a`）：Platform 参考数据 45 + 回归 47、arch-rules 68、client-master 152、client-console-bff 100、client-console-web 129、operations-console-bff 2421、operations-console-web 1565；本机 e2e 接口 231 + 98、浏览器 Client 84 / 运营 29×2；mor-test 接口 220 PASS / 0 FAIL、浏览器 Client 21×4、运营 27×2。
- **Deployed：** mor-test 2026-10-27 15:33—15:56Z（tag `refdata-20261027a`）：platform `aec53586…`、client-master `80257737…`、client-console-bff `be79550f…`、operations-console-bff `48113289…`、client-console-web `c09af02a…`、operations-console-web `b1309a45…`；块 139 激活（Gateway 607 → 612）；语言包 client-console #6、operations-console #13；Platform 中断约 50 秒、client-master 约 40 秒。回滚目标见运行卡第 6 节与 `release/compose-rollback.yaml`。
- **Deployed（合并币种页）：** mor-test 2026-10-27 17:12Z：operations-console-web `f3da39ad…`（币种页并入参考数据页第一个页签，侧栏不再单列"币种"，`/currency-specs` 跳转），语言包 operations-console #14；未提交。
- **Deployed（国旗）：** mor-test 2026-10-27 16:05—16:46Z：249 个国旗 PNG 经运营公开上传并回填 `t_ref_country.logo_file_address`（期间两条上传路由限流临时放宽到 500 / 分钟，已恢复）。
- **Pending / 遗留：** 批量导入图标应做成正式能力（内部接口，不走面向人工的上传入口）；中文 MCC 名、行业说明、行政区英文名与高风险 MCC 清单生产前需合规审核；CLDR 阿拉伯文行政区名为 provisional；arch-rules 表归属生成脚本与手工维护不一致（运行会丢 17 张表）；本机 e2e 的 ledger / billing JAR 文件已不在磁盘（仅运行进程持有）；Platform 多语言重导入不更新译文对应的英文版本（需批量确认）；运营语言包发布在 Gateway 超时内未返回（504，后台完成）。`.pen` 原型（operations-console.pen y≈94400/95600 九张、client-console.pen C5）待与其他会话改动一并提交。

## client-master / compliance 全球合规入驻与资格切片 1（2026-10-27，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261027/client-master-全球合规入驻与资格切片1/requirement.md>)、[技术方案](<../requirements/20261027/client-master-全球合规入驻与资格切片1/technique.md>)、[交付记录](<../requirements/20261027/client-master-全球合规入驻与资格切片1/delivery.md>)、[发布运行卡](<../requirements/20261027/client-master-全球合规入驻与资格切片1/release/runbook.md>)；设计 [design-discuss/client-onboarding](<../design-discuss/client-onboarding/design.md>)，决定 [2026-10-27](<./decisions/2026-10-27-client-onboarding-global-kyc-kyb.md>)（Proposed）。AE / SA / EG 国家包、Schema 驱动入驻、PayFac 入驻码与责任方、证件 Vault Token、合规程序 / 筛查 Mock / CRR / 四眼 / 需求级退回、资格事件。
- **Implemented：** client-master 新表 13、改表 11（`schema_id` 可空），CM-1—CM-13（`api.onboarding` / `api.onboardingadmin`），证件 `LEGAL_IDENTIFIER_V1` STABLE Token 作唯一键（只回掩码），入驻码、撤回（DRAFT / RETURNED）、需求清单合并 compliance 退回错误与期限、`ClientReviewDecided` v1/v2 与 `QualificationChanged` 消费（REVOKED → SUSPENDED）、GAP-PROFILE 修复、旧申请（`schema_id` 空）只读；compliance 新表 4、改表 14，CP-1—CP-6、I01 v2 与重提串联复用、I03 撤回、筛查经 channel-connector `SCREENING_SEARCH`（补偿 `CASE_SCREENING`）、人工检查 / 命中处置 / CRR / 推荐与四眼（含拒绝）、退回逾期 XXL `clientReviewReturnExpiryScan`、决定冻结、程序只读列表；两端 BFF 与 Web（Client 门户认证 + 入驻全量 v2、运营案件工作台 / 入驻配置 / Client / 入驻码）；Vault DML；Platform 块 138（OPERATIONS 18 + CLIENT 5 路由，停用 c03 form-options）与运营资源能力清单；Mock 筛查容器与渠道登记脚本；多语言命名空间 `onboarding-schema`（后端 labelKey 保持 `onboardingSchema.*`，前端映射）。
- **Verified：** worktree `Code-MoR-onboard`（基于 `ea0272f3`）：client-master 141、compliance 133、client-console-bff 92、operations-console-bff 2393、client-console-web 117 + build、operations-console-web 1551 + tsc + build、arch-rules TableOwnership 7/7、messaging catalog 12/12。全部发布 SQL 在 mor-test 与本机 dump 断网副本试跑；块 138 离线门禁两环境 11 步全过。本机 e2e（`_v2e2e` 库）验收 256 + 105 PASS、0 FAIL；mor-test 验收 242 + SE 重跑 10 PASS、0 FAIL（[脚本](<../requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance/README.md>)，结果见 verification/）。
- **Deployed：** mor-test 2026-10-27 03:47—04:05Z（tag `onboard-20261027a`）：client-master `0af5b792…`、compliance `f5a9b13a…`、client-console-bff `83386ff5…`、operations-console-bff `c5ed58df…`、client-console-web `34d3d6e3…`、operations-console-web `88ab111a…`（浏览器冒烟后补丁，见 delivery）、platform-support `cad6dc14…`；channel-connector 同镜像加两项放行；screening-mock 172.30.73.43（挂载 onboarding worktree 脚本）；Vault / client-master / compliance SQL 已执行（PENDING Client 17 清理，ACTIVE 37 保留并脱敏旧护照号）；XXL job 24；块 138 已激活（Gateway 585 → 607）；语言包 client-console #5、operations-console #12。浏览器冒烟（ui-smoke）发现并修复 3 个 P0 与 15 项 P1/P2，AE 机构与个人经页面提交通过。备份 `~/Code-MoR-backups/20261027T034703Z-onboarding-slice1-mor-test`，回滚文件 `release/compose-rollback.yaml`。本机 e2e 同步替换（旧 JAR `*.pre-onboard`）。
- **Pending / 遗留：** C05 缺 submittedAt / withdrawnAt；运营布尔值显示 true/false；mor-test 未经页面验 RETURNED 页与验证邮件（SES）。提交 / 推送另行授权（`paytech/.mvn/maven.config` 只用于 worktree，提交时排除）。AC-08 四眼批准与 AC-09 资格撤销只在本机用合成复核账号验收（mor-test 未建运营账号）；CLOSED 辖区由 auto 测试覆盖。Foundation HTTP 客户端对无请求体的写命令丢弃 Problem `errors[]`（正式提交 422 只到错误码，前端以重新核对兜底）。国家包 26 项待法务确认（生产执行前改 DRAFT）。后端 labelKey 与多语言命名空间不一致（`onboardingSchema.*` → `onboarding-schema.*`，前端映射），宜在后续切片统一。真实筛查供应商、整数分值 DECIMAL 校验未验；入驻码作废无原因、Client 详情无激活 / 挂起时间、目录与入驻码列表过滤在 BFF；PayFac 下拉上限 100。mor-test 与本机 Vault 的 Nacos 注册曾丢失（与本需求无关，重启恢复），断线后不自动重注册，需另行排查。Platform 初始数据块 135 在全新库失败（既有问题）。Mock 运行期间 onboarding worktree 不得删除。

## commercial 商业关系快照与真实解析接入（2026-10-23—25，已部署 mor-test，已本地提交 `4b661830`，未推送）

- **范围：** [需求](<../requirements/20261023/commercial-商业关系快照与真实解析接入/requirement.md>)、[技术方案](<../requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md>)、[交付记录](<../requirements/20261023/commercial-商业关系快照与真实解析接入/delivery.md>)、[发布记录](<../requirements/20261023/commercial-商业关系快照与真实解析接入/release/release.md>)。依据 [Platform 技术服务商与 PayFac 定位决定](decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md)（Proposed）D13 快照；本轮只 C1 / C2。OE 来源值 `PLATFORM` 全系统删除（只剩 `CLIENT` / `PARTNER`），AE001 迁到"我方 PayFac" Partner。
- **Implemented：** commercial 商业关系快照推导与组合约束、解析顺序、写入时 Party 校验、快照开关 `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED`（默认 false），修复 `CapabilityConditions` 缺 equals 的既有缺陷；compliance 资格表 DataObject / Mapper、评估接口、4 个运营接口、入驻审核投影（`role_id` 改字符型存 entity_no / 十进制 ID）；client-master 多角色、OE 收单资质 `acquiring_qualification`、3 张新表、7 个运营接口；acquiring 真实解析客户端与错误映射、开关 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE`（MOCK / REAL）、快照冻结表与三列；risk-decision 删除 PLATFORM 来源；ops-bff 11 个接口；ops-web 资格登记、经营主体、Local Partner 三页和 2 个命名空间；Platform 注册块 137。
- **Verified：** 基于 main `8a86a31c`：compliance 437、client-master 100（arch-rules 68）、commercial 211、acquiring 897、risk-decision 相关 188、ops-bff 95、ops-web 1519 + tsc + build 全部通过；本地联调 44/44、浏览器 30/30（英文 + 中文）。requirements-site 137 个中 10 个失败、Platform 初始数据脚本测试失败，均与 HEAD 相同的既有失败。
- **Deployed：** mor-test 2026-10-25 13:38—14:35Z（tag `relsnap-20261025a`）：compliance `ca158545…`、commercial `e5c1ee6b…`、client-master `eeed4e63…`、acquiring `e0a1f618…`、risk-decision `288b0ff6…`、ops-bff `feefe73f…`、ops-web `4f44725e…`；5 个库 SQL 已执行，块 137 已激活（Gateway 574 → 585 条路由），语言包 #8（8813 key）；PayFac Partner 合同 + PARTNER_SHARE / PLATFORM_SERVICE_CHARGE 两个 0 费率方案已生效。AC-11 窗口 14:09—14:12 临时 REAL 只对 DEMO-CL-001 验收通过，之后 acquiring 回 MOCK、快照开关回 false；浏览器 31/32（Z1 仅因 Client 业务名称为英文）。
- **Pending / 遗留：** 推送另行授权。GAP-PROFILE：client-master 入驻审核通过不清 `t_party_profile.draft_application_id`、不写 `content_hash`，全部 37 个已通过 Client 在 REAL 下会被拒，须另立 client-master 需求，修好前 mor-test 保持 MOCK。S-4（Apple Pay 真实支付 → billing 按 Partner 计费）未验。重建 mor-test compliance 必须带本次 `compose-candidate.yaml`（主 compose 仍指旧镜像）。仓库 `operations-console-web/i18n/zh-CN/nav.json` 侧栏 3 个 key 仍为英文（线上为中文），另行修正。client-master 与 channel-connector 共用 Snowflake worker 26（既有问题）。验收截图未入库，留在 relsnap worktree。

## operations 运营控制台多语言迁移（2026-10-23，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261023/operations-控制台多语言迁移/requirement.md>)、[技术方案](<../requirements/20261023/operations-控制台多语言迁移/technique.md>)、[交付记录](<../requirements/20261023/operations-控制台多语言迁移/delivery.md>)、[发布记录](<../requirements/20261023/operations-控制台多语言迁移/release/release.md>)、[英文术语表](<../requirements/20261023/operations-控制台多语言迁移/glossary.md>)。多语言管理 R9 的"另立需求"：operations-console-web 全部既有页面接入；服务端报错文案、业务内容多语言不在范围。
- **Implemented：** ops-web 全部界面文案改为 key（21 个命名空间、8523 key，`en` 基准 + `zh-CN` 种子逐字等于原中文）；默认英文、页头 / 登录页语言切换（整页刷新）、本地缓存 manifest 与语言包（启动直接用缓存、后台核对新发布、断网可用）；antd / dayjs / `<html lang>` 跟随；菜单、面包屑、页面标题、权限树按 `nav.resource.<资源编码>`，缺 key 回退 Platform `display_name`；租户访问原因按编码翻译；全量禁中文守卫测试（白名单仅 `navigation/pageCapabilities.ts`）。ops-bff 三处：权限树节点带 `code`（"其他权限"为 `OTHER_PERMISSIONS`）、审计详情去掉中文 `label`、价格试算说明改为 `notes[{code, params}]`。开发约定第 10 节与决定 `2026-10-22-ui-text-localization.md` 已更新。
- **Verified：** 基于 main `9f210b5c`：ops-web 1481/1481、tsc、build；ops-bff 2345/0；本地中文零变化比对 70 页 + 48 个详情无迁移差异；缓存 / 断网 / 发布刷新 10/10；英文逐词断行检测与 104 张截图人工审阅（加宽 11 列）。
- **Deployed：** mor-test 2026-10-23 19:54—20:45Z：ops-bff `3751638e…`、ops-web `d228e3c8…`（tag `opsi18n-20261023a`）；Platform `operations-console` 默认语言 `en`、21 个命名空间、发布 #3（#4 为 AC-07 临时版本）。2026-10-23 21:50Z 发布后修复：翻译页一次取完全部 key 触发列表路由限流（30 次 / 分钟），改为服务端分页与按命名空间核对批量粘贴，ops-web 换为 `4c1f1ec3…`（`opsi18n-20261024b`），语言包发布 #5（8525 key）；接口 65/65、浏览器 28 + 1 预期、AC-07 11/11、中文零变化复核通过。
- **Pending / 遗留：** 推送另行授权。Foundation `SnowflakeIdGenerator` 同毫秒序列（4096）用尽即抛 `ID_GENERATION_ERROR`，本次 8114 个 key 以累积分批导入规避，公共库修正另立。只在 Platform 注册而前端未登记的新资源在英文界面显示中文名。阶段 4 的 218 张英文走查截图未入库，存本机备份 `~/Code-MoR-backups/20261023-ops-i18n/local-en-screenshots`。

## platform-support 多语言管理（前端界面文案，2026-10-22—23，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261022/platform-support-多语言管理/requirement.md>)、[技术方案](<../requirements/20261022/platform-support-多语言管理/technique.md>)、[交付记录](<../requirements/20261022/platform-support-多语言管理/delivery.md>)、[发布记录](<../requirements/20261022/platform-support-多语言管理/release/release.md>)、[决定](./decisions/2026-10-22-ui-text-localization.md)；前置为文件公开桶。只处理第一类文案（前端界面文案）；服务端报错文案、业务内容多语言另立。
- **Implemented：** Platform 新模块 `paytech-platform-support-localization`：7 张 `t_i18n_*` 表、24 个内部接口（Locale、应用、命名空间、key 导入、翻译、发布、切回、manifest），ICU4J 校验，发布三步（事务外生成并以 PUBLIC 上传语言包 → 短事务登记 → 补偿事件在文件检查通过后上线）；file-document 增加 `application/json` 格式检查与进程内上传；Operations BFF 23 个管理转发 + 2 个导入上传 + 匿名 manifest，Client BFF 匿名 manifest；块 135（权限 3、控制台资源 11、路由 27）与初始数据；operations-console-web 新增“多语言”三页五弹窗（只有这些页面接入），client-console-web 全量接入（8 个命名空间 608 个 key，语言切换只列已发布语言）；两个 web 共用同一份 `src/i18n/` 运行时（`intl-messageformat`）。
- **Verified：** 基于 main `bfb17bc9`：Platform localization / files / architecture / configuration 81/81、localization 36/36、arch-rules 68/68、ops-bff 2344/2344、client-bff 91/91、ops-web 1451/1451、client-web 80/80 与构建；阶段 7 修复后 localization HTTP 25/25。本机 `mor_platform` 已执行本需求 DDL。
- **Deployed：** mor-test 2026-10-23 08:22—09:00Z：platform `48032999…`（i18n-20261023b）、ops-bff `e4d913e0…`、client-bff `9033b8fa…`、client-web `280a2f38…`、ops-web `fc3abaa7…`（源 main `ffdf872e` + 本需求，ops-web 另含 code-mor-55 的 10 个未提交数据查询文件；09:01Z 起被 code-mor-78 的 `9fb809df…` 取代，即同一构建 + `37173818`）；DDL、块 135 已执行并激活（ACTIVE 路由 569）；`operations-console-bff` × PRIVATE 路由改指 `OPERATIONS_BFF_IMPORT`（csv + json，code-mor-6b 同意，卡 BIN 回归通过）；`platform-support` × PUBLIC → `MINIO/mor-platform-public`；首批语言包 operations-console 409 key、client-console 608 key 已上线。阶段 7 发现并修复：块 135 守卫超 Gateway 编译上限导致快照刷新中断约 5 分钟（已回撤重做）、`manifest?locale=en` 503、语言与应用页并行请求触发 429。
- **Pending / 遗留：** 推送另行授权；服务端 Problem `detail` 仍为中文；人员显示账号 ID；发布失败遗留的孤儿语言包不清理；语言包无长期 `Cache-Control`（留待生产 CDN）；其他机器需信任测试 CA 才能加载语言包；operations-console-web 既有页面未迁移；checkout / partner 控制台只登记应用。

## billing 支付事实自动登记（计费需求 B 首切片，2026-10-22—23，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261022/billing-支付事实自动登记/requirement.md>)、[技术方案](<../requirements/20261022/billing-支付事实自动登记/technique.md>)、[交付记录](<../requirements/20261022/billing-支付事实自动登记/delivery.md>)、[发布记录](<../requirements/20261022/billing-支付事实自动登记/release/release.md>)。首次自动登记 `PAYMENT` 计费事实并跑通 billing → account → ledger → reconciliation。决定：S13 billing（领域层）不调 acquiring（流程层），改由 acquiring 发布新事件 `OrderPaymentSucceeded`（schema 1，聚合 ORDER，订单冻结商业字段 + 支付事实 34 字段，消费方 billing）；Q16 `PaymentSucceeded` 仍为 v2，末尾追加可空 `channelTransactionRefId` / `channelReferenceType`；S11 税务只记 `TAX_SNAPSHOT` 引用；S14 Σ CLIENT_FEE = 0 拒绝；S15 billing 补 `AE_BUSINESS_DAYS` 日历。
- **Implemented：** payment `PaymentSucceededPayload/Result/Handler` +2 字段；acquiring `OrderPaymentServiceImpl.applySucceeded` 事务内登记补偿事件 `ORDER_PAYMENT_SUCCEEDED`，`ext/job/OrderPaymentSucceededHandler` 提交后发布到 `acquiring.events`（`unroutable-policy: FAIL`，`PAYTECH_INSTANCE_ID`），`OrderContextRepository.findCommercialContext`；billing `ext/mq/OrderPaymentSucceededBillingHandler`（HANDLER_MANAGED，队列 `billing.acquiring.p0..p3`）+ `OrderPaymentSucceededEventReader` → `RecordPaymentFactProcessor`（无锁，`uk_fact_source` 冲突 → `ConcurrentRequestException`）→ `PaymentFactAssembler`（commercial 价格命中与组件计算、channel-connector 成本命中、reconciliation 清算账户、ledger Finance Entity 四个事务外客户端，4xx 转 `ErrorException` 重试）、`SettlementTimeCalculator`（T+N 按价格 `settlement_timezone` / 日历 / cutoff）、`BillingFactService.recordAutoFact`、`BillingFactInvariants.checkAutoPayment`；行：每个 Client 价格组件一行 CLIENT_FEE（CHANNEL → OE）、CLIENT_PRINCIPAL（CHANNEL → CLIENT）、CHANNEL_COST（OE → CHANNEL）、Partner 来源另有 PARTNER_SHARE / PLATFORM_SERVICE_CHARGE（OE → FE）；account `FactBalanceMappingRule` 白名单 CHANNEL 可作 CLIENT_PRINCIPAL / CLIENT_FEE 付方；ledger 新增 `GET /finance-entities/v1/by-party/{legalPartyId}`（ACTIVE 唯一，404 `FINANCE_ENTITY_NOT_FOUND`）；foundation `events.yaml` 登记 `OrderPaymentSucceeded`，`PaymentSucceeded` 消费方回到 [acquiring, card]；billing `billing-initial-data.sql` + `changes/2026-10-23-business-calendar-ae.sql` 增加 `AE_BUSINESS_DAYS`（Asia/Dubai）；test-environment compose 增 billing 四个上游 URL 与 acquiring `PAYTECH_INSTANCE_ID`。
- **Verified：** 工作树基于 main `d3d39e40`：payment 111/111、acquiring 853/853、ledger 344/344、account 67/67、billing 144/144（5 个 MQ 入口测试类：登记 / 定价 / T+N / 重试 / 拒绝 + 不变量）、messaging 73/73、arch-rules 68/68、全 reactor test-compile；acquiring 载荷 ↔ billing 读取器 34 字段静态交叉核对；独立评审无阻塞项。本机 `mor_billing` 已执行日历 DML；本机 `mor_account` 补执行 2026-10-19 `owner-account-no.sql` 第 1、2 段（此前缺失）。
- **Deployed（mor-test，2026-10-23 07:45—07:49Z）：** billing `53450948…`、account `17831407…`、ledger `3819ff28…`（源 `22ede435` + 本需求）、payment `cf9097ea…`、acquiring `27c36f4e…`（源 `d3d39e40` + 本需求）；`mor_billing` 日历 DML 已执行；channel-connector 成本方案 `DEMO_NI_COST` v1 ACTIVE（MID `238508278684950528` / AE001 / AE / USD / APPLE_PAY / PAY，0.5%，IAM 合成复核人 `239292995902455808` 批准）；合成 `OrderPaymentSucceeded`（PLATFORM / APPLE_PAY / 100.00 USD）全链通过：事实 `239295097391407104`（2.70 + 0.30 / 97.00 / 0.50，到期 2026-10-27 23:59:59Z）→ account PENDING 贷 97.00 → ledger PAYMENT_SUCCEEDED 分录 100.50 → reconciliation 平台流水 OPEN；保留为演示数据。
- **Pending / 遗留：** 推送另行授权。**P1（需求 D）**：acquiring 商业事实仍是固定 Mock（`CustomerCheckoutIntegrationConfiguration`），真实收银台支付成功会发出假版本 ID 的事件，billing 对 commercial 404 重试后进 `billing.acquiring.dlq`（预期噪声，勿重放）；CARD 价格画像依赖 `ISSUER_REGION` 等发卡维度而事件不携带（GAP-ISSUER）；DEMO-PT-001 只有 CARD 价格，PARTNER 场景与双账簿成对过账未在 mor-test 验收（GAP-PARTNER）；对账四类事件消费另立需求 C。**F1**：运营控制台「调整 → 计费事实」详情抽屉对 AUTO 事实报 `INVALID_RESPONSE`（ops-web `decodeBillingFact` 要求 `sourceSystem` 大写，billing 写 `acquiring`；列表与其余财务页正常）——已按用户决定放宽 ops-web 解码（main `37173818`），mor-test ops-web 于 09:01Z 补发为 `9fb809df…`（基线 = 多语言管理 `fc3abaa7…` 逐字节复现 + 两文件；回滚目标 `fc3abaa7…`），控制台复验 11 / 11。`billing.acquiring.dlq` 首发被拒消息已 purge（08:24Z）。回滚顺序 acquiring → payment → billing → account → ledger（account 回滚会拒绝之后所有 PAYMENT 事实）。

## platform-support 文件公开桶、同地址替换、检查策略与文件记录（2026-10-22—23，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261022/platform-support-文件公开桶/requirement.md>)、[技术方案](<../requirements/20261022/platform-support-文件公开桶/technique.md>)、[交付记录](<../requirements/20261022/platform-support-文件公开桶/delivery.md>)、[发布记录](<../requirements/20261022/platform-support-文件公开桶/release/release.md>)；前置为文件多存储并存。
- **Implemented：** 桶区分 `PRIVATE` / `PUBLIC`（公开桶登记 `public_base_url`，桶检查分别核对匿名不可读 / 对外地址可读）；写入路由键改为来源系统 + 访问级别，不跨级别回落（无同级别路由返回 503）；上传声明 `accessLevel`（缺省私有）；公开文件检查通过后返回 `publicUrl` / `versionedUrl`。同地址替换 `POST /internal/files/v1/files/{fileId}/replacements`：新表 `t_file_version`，`t_file_object` 保留文件身份并镜像当前版本，扫描按版本运行，候选通过后切换；同一文件同时一个替换；按"比基准新的版本恰好 1 个"核对（不依赖条件写）；公开文件补偿事件 `FILE_PUBLIC_SYNC` 在替换未通过 / 作废 / 过期后把当前版本复制回最新。检查策略管理 8 个接口（修订、激活、废弃、停用；允许类型取自格式检查器，现为 pdf / png / jpeg / csv），文件记录 3 个接口（分页查询、详情、按版本下载并写 `FILE_DOWNLOAD_AUTHORIZE` 审计）。`mor_platform`：新表 1，5 表加 10 列、3 个唯一键变宽、3 个索引；注册块 134（157 行），块 130 写入规则编辑 / 删除路径加 `/{accessLevel}`。Operations BFF 同路径转发，Web 存储管理新增「检查策略」页签、桶与写入规则按访问级别改造，新增「文件记录」页。已有响应新增字段为可空类型；内部 HTTP 客户端严格反序列化，**发布须先换消费方（card、client-master、client-console-bff、ops-bff）再换 Platform**。
- **Verified：** 基于 main `92d2d1db` 合入后：全 Reactor `test-compile` 通过；Platform `auto` 242/242（文件 / 存储 / 替换 / 公开同步 / 策略冻结 / 文件记录 / 工作流 / 权限 / Gateway / 架构）；ops-bff 2272/2272；ops-web 1407/1407 与构建；arch-rules TableOwnership 通过；PDManer 失败项与 `92d2d1db` 基线一致。本机 `mor_platform` 已执行本需求 DDL / DML。
- **Deployed：** mor-test 2026-10-23 05:22—05:30Z（窗口 05:23:57—05:25:27Z）：card `810b6b72…`、client-master `2e495b40…`、client-console-bff `b8ecaaea…`、ops-bff `06d850d1…`、platform `68ba631f…`、ops-web `f8b5dc32…`（源 `22ede435` + 本需求，ops-web 另含当时 16 个未提交文件）；DDL、回填 70 行、块 130 / 134 已执行并激活；MinIO 公开测试桶 `mor-platform-public` 已登记为 PUBLIC 并保留；postcheck 23/23、验收 87/89（2 项为脚本问题，已补验 / 修正）。
- **Pending / 遗留：** 推送另行授权；真实 AWS S3 公开桶与 CDN 未实测（需公开桶与 `S3_TEST` 复制权限）；`local-environment/minio/init/init.sh` 未纳入公开桶（重跑 minio-init 会覆盖 `platform-files` 策略）；检查策略表「状态」列在窄屏被详情面板遮挡；公开文件被外部改写且无待执行同步任务时，后续替换返回 409 需人工恢复。

## acquiring 订单到期后的支付结果确认（2026-10-23，已部署 mor-test，已本地提交 `a2b7f85a`，未推送；提交重建 JAR 与运行仅差多语言 API 新增类）

- **范围：** [需求](<../requirements/20261022/acquiring-到期后支付结果确认/requirement.md>)、[技术方案](<../requirements/20261022/acquiring-到期后支付结果确认/technique.md>)、[交付记录](<../requirements/20261022/acquiring-到期后支付结果确认/delivery.md>)、[发布记录](<../requirements/20261022/acquiring-到期后支付结果确认/release/release.md>)。来源为订单过期收尾 F1；参照 Stripe（提交后会话不再过期）。无 DDL、无 Platform 注册、无配置项；[Client API v1 设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)第 6 节 Token 校验与第 8.2 节已按本需求修订。
- **Implemented：** acquiring `GetCustomerPaymentProcessor`：到期检查从入口移到“无支付记录”与“Payment 未找到该提交”两个分支（到期 410 `CHECKOUT_EXPIRED`，未到期 404 `PAYMENT_SUBMISSION_NOT_FOUND`），删除响应前复查；已提交支付在订单到期（含已 EXPIRED）后仍刷新 Payment 并返回真实结果，Payment 不可用降级为处理中。写操作与查看收银台到期规则不变。
- **Verified：** acquiring `auto` 847/847（main `98210e0b` + 本需求）；本机 e2e S1—S5（3DS 挑战中到期后成功 / 失败、5 秒限频、未提交 410、日志泄漏 0）；mor-test P-A1 / P-A2（D1）、P-B 合成 600.00 USD 订单自然到期后 200 FAILED、扫描 EXPIRED 后仍 200 FAILED。3DS 成功、处理中限频只在本机验证（mor-test 无 3DS 渠道）。
- **Deployed：** mor-test 2026-10-23 10:03Z：acquiring `f6d55ade…`（JAR `dca404ec`，现网镜像覆盖 JAR，源 main `a816bd14` + 本需求；JAR 相对前版另含 `f707f2da` / `be688db7` 两个公共库变更，acquiring 未使用），回滚目标 `27c36f4e…`（billing B 发布）。
- **Pending / 遗留：** 推送另行授权；查看收银台到期后的只读页面留给 `checkout-web` 需求（前端跳回后先查支付结果）；Payment 已终止 Intent 后渠道晚到成功的自动退款另立需求（前置 Payment 退款链路）。

## acquiring 订单与会话过期收尾（2026-10-23，已部署 mor-test，已本地提交 `623916ac`，未推送；提交重建 JAR 与运行一致）

- **范围：** [需求](<../requirements/20261022/acquiring-订单过期收尾/requirement.md>)、[技术方案](<../requirements/20261022/acquiring-订单过期收尾/technique.md>)、[交付记录](<../requirements/20261022/acquiring-订单过期收尾/delivery.md>)、[发布记录](<../requirements/20261022/acquiring-订单过期收尾/release/release.md>)。无 DDL、无 Platform 路由注册；过期终态时刻取 `expires_at`，原因码 `EXPIRY_REACHED` 登记到 [Commerce Order Status Reason Catalog](./database/COMMERCE_DATABASE.md)。
- **Implemented：** payment 终止支付意图增加原因 `ORDER_EXPIRED`（`ORDER_CANCELLED` / `OPERATING_ENTITY_CLOSED` 落 CANCELLED，`ORDER_EXPIRED` 落 EXPIRED），新增 `GET /payment-execution/v1/intents/by-source/{sourceType}/{sourceId}`；acquiring `PaymentTerminationClientImpl` 取代固定实现（无投影时先按来源定位，只有 404 视为 Intent 不存在），Cancel 对 ALREADY_PAID 返回 409 `REVERSAL_REQUIRED`；XXL Handler `orderExpiryScan`（`OrderExpiryJobHandler`，分片取模，`acquiring.order-expiry.*` 默认 200 / 50 / 24h）→ `ScanExpiredOrdersProcessor` → `ExpireOrderProcessor`（订单锁 TRY_LOCK，与支付提交同锁）：无 Intent 且无接受证据本地收尾；否则先写终止请求，再以 `ORDER_EXPIRED` 终止，TERMINATED / ALREADY_TERMINATED / 404 后同一事务把订单、会话、投影置 EXPIRED 并登记 `order.expired`（`evt_<orderNo>_expired`）；PENDING 保持 OPEN、超阈值 WARN；ALREADY_PAID / REVERSAL_REQUIRED 不改写；Payment 不可用或锁冲突下轮重试。验收脚本 V2 `client_api_flow.py` 新增 `flow_expired`（仅 `E2E_TARGET=test`）；本机启动器 Platform Webhook 调用方加入 `order.expired`。
- **Verified：** 基于 `22ede435`（含 3DS 合入）：payment `auto` 111/111、acquiring `auto` 839/839（两次）、arch-rules / messaging 等通过、全 Reactor `test-compile` 通过；requirements-site 137 例中 10 例失败（既有）。本机 e2e（`*_v2e2e`）：无 Intent 本地过期、3DS 挑战中到期保持 OPEN → 挑战失败后过期（Intent EXPIRED）、已支付不受影响、日志泄漏 0。mor-test：Payment 探针 11/11；`client_api_flow.py` 91 PASS（AC-12 签名送达、Envelope 与 GET 一致、过期后取消 409）；3DS AC-02、BIN 冻结、健康、泄漏检索通过。脚本修复后 10-23 复跑 105/105 PASS（含退款签名、GET 比对与重放）。
- **Deployed：** mor-test 2026-10-22 20:39—20:41Z：payment `f3c48d89…`（JAR `621ff256`）、acquiring `55a644fd…`（JAR `de02f36b`），回滚目标 `4cf16f15…` / `3958983e…`；XXL 任务 23 `orderExpiryScan`（acquiring-service，`0 * * * * ?`，分片广播）启用；首轮收尾 16 笔历次验收遗留订单（Platform 16 行 `order.expired`，投递 0）。
- **Pending / 遗留：** 推送另行授权；F1：订单到期后收银台与轮询先返回 `CHECKOUT_EXPIRED`、不再触发 Payment 刷新，挂起 3DS 结果只靠 Payment 退避查单或渠道通知（归收银台前端需求）；扫描查询生产数据量下的执行计划未验证；F1 已登记为需求 `requirements/20261022/acquiring-到期后支付结果确认/`（阶段 1 评审中）。

## payment 3DS 全链路 + card BIN 库与卡属性（2026-10-22，已部署 mor-test，已提交 `de4f7ba4` 并推送）

- **范围：** [3DS 需求](<../requirements/20261022/payment-3DS全链路/requirement.md>)、[3DS 技术方案](<../requirements/20261022/payment-3DS全链路/technique.md>)、[Execution 3DS 修订](<../requirements/20261022/payment-3DS全链路/execution-3ds-amendment.md>)、[3DS 交付记录](<../requirements/20261022/payment-3DS全链路/delivery.md>)；[BIN 需求](<../requirements/20261022/card-BIN库与卡属性/requirement.md>)、[BIN 技术方案](<../requirements/20261022/card-BIN库与卡属性/technique.md>)、[BIN 交付记录](<../requirements/20261022/card-BIN库与卡属性/delivery.md>)；两项合并发布 [release.md](<../requirements/20261022/payment-3DS全链路/release/release.md>)。
- **Implemented：**
  - **3DS：** 卡流程不再进入 AUTHENTICATE，风控要求 3DS 时 PAY 带 `threeDsRequired`（渠道内 3DS）；路由优先支持 3DS 的 MID，冻结 MID 不支持时 `THREE_DS_CHANNEL_UNAVAILABLE`；`ConfirmPayResultProcessor` 统一确认结果，入口为补偿 `PAY_RESULT_QUERY`（退避，24 小时转人工）、刷新接口 `POST …/submissions/{sid}/refresh`（5 秒限频）与 MQ `ChannelPaymentNotified`；channel-connector 查单映射 `PAY_QUERY`（CLOSED / NOT_FOUND）、候选 `threeDsMode`、回调入站 `POST /channel-callbacks/v1/{channelCode}/{channelAccountId}`（RSA_SHA256 验签、去重、发布事件）；Gateway `CHANNEL_CALLBACK` 安全链；acquiring 轮询触发刷新并接受 REDIRECT；Platform 块 133（8 行）。
  - **BIN：** card 新增 BIN 库 4 表、7 个运营接口（只允许 ops-bff）、`CARD_BIN_LIBRARY_IMPORT` 两遍导入、激活 / 回切（锁 + `active_slot` + 409）、最长前缀查询；交易工具返回卡属性快照，已存卡返回卡组织；Payment Token 冻结 8 列、Risk 输入 `issuerCountryCode` / `cardBrand` / `fundingType`（合同 v1，新点版本 220003）、路由与执行带 `schemeCode`、`PaymentSucceeded` v2 追加 3 个可空字段；Platform 识别 `text/csv`、`t_country_region` 249 行与查询接口、`CARD_BIN_CSV` 扫描策略、块 132；运营台「平台配置 → 卡 BIN 库」。文件地址按 [文件地址原则](./decisions/2026-10-22-file-upload-address-principles.md) 用 `FileAddress`。
- **Verified：** 基线 `42879401` 上（`Code-MoR-card3ds-rel2`）：platform-support 803、workflow 64、data-query 48、ops-bff 2236、ops-web 1344；同一改动集在 `73a2460d` 上：payment 96、channel-connector 395、acquiring 806、risk-decision 1738、counterparty 22、commercial 196、api-gateway 1313、client-master 78、arch-rules 68、messaging 73。既有失败：card 2 例（`StoredCardHttpTest.test_payWithStoredCard`、`CardMaterialTicketTest.test_securityCodeClaimedRefused`，干净基线同样失败）、PDManer node 18 个文件（干净基线同样失败）。本机端到端（阶段 5，[报告](<../requirements/20261022/payment-3DS全链路/verification/stage5-report.md>)）：3DS AC-01—11（AC-03 部分）、BIN AC-01—12 通过，缺陷 S5-01 / S5-02 已修。
- **Deployed（mor-test，2026-10-22 17:58—18:44Z）：** 十个服务（risk `d69ca678`、payment `4cf16f15`、acquiring `3958983e`、counterparty `6586bd73`、platform `96da579b`、card `c39a3990`、channel-connector `23679d45`、api-gateway `270c8ba2`、ops-bff `9c382db3`、ops-web `8bdbdd44`，ops-web 含当时已上线的 16 个他人未提交文件）；五库增量 SQL、块 132 / 133 激活、存储写入路由 `239087906591948800`；备份 `~/Code-MoR-backups/20261022T175834Z-card3ds-mor-test/`。验收：3DS AC-01/02/03/04/07（反向）/11/12 通过（AC-12 为 PayerMax UAT 真实 3DS，用户完成 ACS），BIN AC-01—06/08/11/12 与并发 409 通过，工作流内嵌预览与 CYBS / MPGS 沙箱回归通过。环境变更：payment 加 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW=PT5M`（S7-01）；标准 API PAY v5（`threeDsRequired`、`payment.subject` 改可选，S7-04）；PAYERMAX 渠道 ACTIVE、收银台 PAY 流程删除改为卡直付，验收后 MID SUSPENDED、能力 DISABLED。
- **Pending / 遗留：** PayerMax 真实异步通知（AC-07 正向）需公网入口与 Nginx `CHANNEL_CALLBACK` location；收银台前端（checkout-web）与 commercial 二期执行链路另立需求；billing 写入卡属性三列依赖计费需求 B；Payment 不传 `subject`（另议）；mor-test 运营口令曾出现在验收代理工具输出中，建议更换；acquiring DLQ 1 条验收探针消息已于 2026-10-22 清理；S7-02 工作流定义页并发 429（既有）。

## platform-support 工作流（paytech-platform-support-workflow，2026-10-21—23，已部署 mor-test，已提交并推送 `1d39cd94` / `42879401`；体验优化本地待发布）

- **范围：** [需求](<../requirements/20261021/platform-support-工作流/requirement.md>)、[技术方案](<../requirements/20261021/platform-support-工作流/technique.md>)（实现期调整见 2.1、5.1）、[交付记录](<../requirements/20261021/platform-support-工作流/delivery.md>)、[并入 Platform 决定](./decisions/2026-10-22-workflow-into-platform-support.md)。`paytech/workflow-approval` 占位已删除。
- **Implemented：** `mor_platform` 新增 10 张表（PDManer 图组 workflow，增量 `changes/2026-10-22-workflow.sql` 与 `changes/2026-10-22-workflow-comment.sql`）；workflow 模块（发布校验、条件、导航、运行引擎、审批含退回修改 / 驳回 / 转交、通知与业务回调经 `compensation_event` 与 MQ `platform-support.events` / `WorkflowCallbackRaised`）；32 个接口（含附件上传、完成、下载授权、重新验证；附件 Processor 在 core `service/processor/workflowattachment`）；业务数据中文件引用存地址 `/files/v1/{fileId}`（`FileAddress`）；ops-bff 同路径转发；ops-web 我的待办、流程实例、流程定义、动作目录四页；注册块 127（7 权限、10 资源、32 路由，默认休眠）。Platform 启动需要 RabbitMQ。
- **Verified：** 主工作区（均 `-am`）workflow 核心 64、Platform 工作流 38、ops-bff 全量 2199（工作流 77）、arch-rules 68 + Reactor 4、ops-web tsc + 1325。本机 auto 库 `mor_platform` 已执行两份增量 DDL（备份 `Code-MoR-backups/mor_platform-local-20261022-*`）。
- **本机端到端（阶段 5，2026-10-22）：** `mor_platform_v2e2e` 已执行 10 表、块 127 与激活、工作流文件路由增量（`verification/local-e2e/30-activate-workflow.sql`、`40-workflow-file-routes.sql`），并为带文件验收执行了文件存储 DDL、块 130 与激活；`mor_vault` 已执行文件存储 Vault DML；存储（MINIO、桶 `mor-platform-files`、默认路由）经运营存储管理登记。e2e platform / ops-bff 为主工作区构建（旧 JAR `*.pre-workflow`），启动器副本 `verification/local-e2e/run-service.sh`（files.enabled、入驻上传关闭）。主流程、并行 / 驳回 / 退回修改、文件上传与下载鉴权、拒绝情形均通过（脚本与截图在 `verification/local-e2e/`）。
- **Deployed（mor-test，2026-10-22，[发布记录](<../requirements/20261021/platform-support-工作流/release/release.md>)）：** 源码 `1d39cd94`；platform `sha256:b9301714…`、ops-bff `sha256:f1fbcea1…`、ops-web `sha256:c7646648…`（回滚目标 d50f2886… / 0abb662a… / 9eaa8521…）；mor_platform 已执行 10 表、注释、块 127 并激活（PLATFORM_ADMIN 版本 44）；Platform 新增 5 个 RabbitMQ 环境变量，`platform-support.events` 已声明；冒烟 14/14。
- **体验优化（2026-10-22—23，[需求](<../requirements/20261022/platform-support-工作流体验优化/requirement.md>)，已提交 `f707f2da` + `92d2d1db`，2026-10-23 已部署 mor-test：[发布记录](<../requirements/20261022/platform-support-工作流体验优化/release/release.md>)，platform `76587ed4…` / ops-bff `c9a70b8d…` / ops-web `a8bb58d7…`，源码 `be688db7` + 10 个数据查询前端未提交文件）：** 我的待办收件箱（按实例节点分组、固定操作栏、显式批量）、新页「我发起的」（菜单 900000000000127311）、动作目录列表 + 独立编辑页、画布为主的流程编辑器与就地新建动作、发布确认；去掉动作验证与提交校验（只保留必填；`ActionRunStatus` 为 PENDING / COMPLETED / CANCELLED；必选全部完成后才进审批，同意时再查；删除重新验证 #20 与路由 127429；不再写 `t_workflow_verification`）；发起与完成的字段错误 422 带 `startValues.*` / `output.*`；模板列表加 `referencedDefinitionCount`；Foundation `HttpRequestValidationException.remoteFieldErrors()`（新增）。块 127 为 405 行。本机 `mor_platform_v2e2e` 已执行 `sql/10-action-run-status.sql`、`sql/20-registration.sql`；mor-test 2026-10-23 已执行（状态 4 行、注册 1 / 6 / 6 / 1 / 1，角色 510 版本 47）。
- **Pending / 遗留：** 推送另行授权；mor-test 写数据冒烟已通过（流程 WF_SMOKE_20261022_FLOW）；附件内嵌预览跟进已完成并本机 e2e 验证（#28 可选 Query `disposition`，块 127 追加 query_param `900000000000127030`，前端改用完成响应 `fileAddress`），2026-10-22 已上 mor-test（[发布记录](<../requirements/20261021/platform-support-工作流/release-inline/release.md>)：platform `ac0b4845…`、ops-bff `798d5e4c…`、ops-web `11f1d24e…`，mor_platform 已加该行）；扫描中发起被拒与重新验证仅自动化覆盖；本地无 XXL-Job 唤醒，通知自动重试未在 e2e 观察；本地无邮件服务商，邮件通知未实际送达。

## platform-support 币种主数据维护与读取缓存（2026-10-20—21，已部署 mor-test，已本地提交，未推送）

- **决定与需求：** [决定记录](./decisions/2026-10-20-currency-spec-platform-snapshot-read.md)（取代 D41 副本部分）；[需求](<../requirements/20261020/platform-support-币种主数据维护与读取缓存/requirement.md>)、[技术方案](<../requirements/20261020/platform-support-币种主数据维护与读取缓存/technique.md>)、[交付记录](<../requirements/20261020/platform-support-币种主数据维护与读取缓存/delivery.md>)；阶段 1—8 完成。
- **Implemented：**
  - **Platform：** `t_currency_spec` 加 `version`。新增维护接口 `/currency-admin/v1/currency-specs`：新建草稿、修改草稿、启停。实现上用资源锁加 `CURRENCY_SPEC_MANAGE` 二次授权、行锁、版本 CAS，并写统一审计。只有 DRAFT 可改，启用后冻结。块 125：权限 2、资源 2、路由 4。
  - **ledger、billing：** 删除本库 `t_currency_spec` 副本与仓储，改为经 `CurrencySpecQueryApi` 读进程内全量快照。启动即加载，首次成功前就绪探针为 DOWN；每 60 秒刷新，失败保留上次快照；事务内只读内存。查不到币种时报可重试异常并请求刷新。ledger 删除 `/currencies/v1`，billing 新增出站 HTTP。
  - **Operations：** BFF 新增币种转发，财务币种列表改读 Platform；Web 新增「平台配置 → 币种」页。
  - **规则：** arch-rules 表归属 step-removed ledger、billing 各 1 张。
- **Verified：**
  - **本机：** Platform 115、ledger 340、billing 99、ops-bff 1964、Web 1199、arch-rules 62、PDManer 12；含商务内容的发布树 BFF 2009、Web 1230。
  - **mor-test：** 验收 32/34 + 补验 4/4，两项失败为脚本缺陷；AC-08 中 ledger 43.7 秒、billing 59.6 秒内读到新币种；浏览器只读回归通过（[发布记录](<../requirements/20261020/platform-support-币种主数据维护与读取缓存/release/release.md>) 第 8 节）。
- **Deployed（mor-test，2026-10-21 05:00—05:16Z）：**
  - **镜像：** platform-support `sha256:b2a63ff4…`、ops-bff `sha256:fddb1993…`（带对账的 `OPERATIONS_RECONCILIATION_BASE_URL`）、ledger `sha256:fa21520d…`、billing `sha256:90d7401a…`、ops-web `sha256:366f8b95…`。05:2xZ 起 ops-web 被商务 `46227f3d…` 取代，其源码包含本需求。
  - **数据库：** `mor_platform` 块 125 已激活（PLATFORM_ADMIN v39）；`mor_ledger`、`mor_billing` 的 `t_currency_spec` 已删除，旧 ledger、billing 镜像不可再回滚使用。
  - **验收数据与备份：** 验收数据（XTS、合成人员）已清理。备份 `~/Code-MoR-backups/20261021T050027Z-currency-spec-mor-test/`。
- **Known gaps：**
  - ledger 读取 Platform 币种响应的严格 JSON 上限是 8 KB，约 60 个币种会超限（billing 为 64 KB）。
  - 本机 `local-environment` 的 `mor_ledger`、`mor_billing` 仍保留副本表，等其他会话的旧代码不再使用后再删。
  - 本地 Flink 演示管道仍指向不存在的 `mor_finance.t_currency_spec`。`data-service` 骨架与 CDC 白名单已删除，并入 Platform data-query（已提交 `9a209e6f`、已部署 mor-test，见[需求目录](<../requirements/20261022/platform-support-数据查询服务/requirement.md>)与[决定](./decisions/2026-10-22-data-query-into-platform-support.md)）。

## reconciliation 对账控制台详情页与渠道流水（2026-10-21，已部署 mor-test，已本地提交，未推送）

- **需求：** [需求](<../requirements/20261021/reconciliation-对账控制台详情页与渠道流水/requirement.md>)、[技术方案](<../requirements/20261021/reconciliation-对账控制台详情页与渠道流水/technique.md>)、[交付记录](<../requirements/20261021/reconciliation-对账控制台详情页与渠道流水/delivery.md>)；切片 1 后续，阶段 1—8 完成。
- **Implemented：** reconciliation 新增只读接口 `GET /reconciliation/v1/channel-statement-lines`（跨账单查渠道账单行，缺省排除 `VOIDED` / `REJECTED` / `RECEIVING` 账单，`size` ≤ 100）、`GET /bank-statements/{id}`、`GET /bank-statement-lines/{id}`（不存在 → 404 `BANK_STATEMENT_NOT_FOUND` / `BANK_STATEMENT_LINE_NOT_FOUND`）；BFF 三条转发；Web 对账 6 类详情由抽屉改为子路由详情页（录入表单仍用抽屉）、列表筛选保存在 URL，新增「财务 → 对账 → 渠道流水」页；Platform 块 126（菜单 1、路由 3，沿用 `FINANCE_RECONCILIATION_READ`）。不改表、不改事件。
- **Verified：** 本机 reconciliation 100、BFF 对账相关 152、Web 1242 + tsc + build、arch-rules 63；本机浏览器 82/83（W-1 / W-2 已修）、接口 27/27；mor-test 冒烟与浏览器只读 66 项（[阶段 5](<../requirements/20261021/reconciliation-对账控制台详情页与渠道流水/verification/stage5-report.md>)、[发布记录](<../requirements/20261021/reconciliation-对账控制台详情页与渠道流水/release/release.md>) 第 8 节）。
- **Deployed（mor-test，2026-10-21 05:53—06:1xZ，tag `recon-detail-20261021`）：** reconciliation `sha256:b31a853d…`、platform-support `sha256:3d8bc375…`、ops-bff `sha256:e822301c…`、ops-web `sha256:70829a3a…`；`mor_platform` 块 126 已激活（PLATFORM_ADMIN v40）。备份 `~/Code-MoR-backups/20261021T055349Z-recon-detail-mor-test/`。
- **Known gaps：** 渠道流水沿用块 123 GET 限流（每人每路由 30 次/分钟），快速连续筛选可能 429；只按外部交易号或不带清算账户查询会扫描账单行表。推送另行授权。

## reconciliation 渠道资金对账切片 1（2026-10-21，已部署 mor-test，已本地提交，未推送）

- **决定与需求：** [决定记录](./decisions/2026-10-20-reconciliation-facts-via-billing.md)（Confirmed，两轮；实施修订：库内 15 张 = 13 业务表 + 2 张消息去重表）；[需求](<../requirements/20261020/reconciliation-渠道资金对账切片1/requirement.md>)、[技术方案](<../requirements/20261020/reconciliation-渠道资金对账切片1/technique.md>)（§2.10 实施决定 I-01—I-18）、[交付记录](<../requirements/20261020/reconciliation-渠道资金对账切片1/delivery.md>)；阶段 1—8 完成。
- **Implemented（main `2f5f37f7`，父 `4ab205e0`，566 个文件，未推送）：** `paytech/reconciliation` 由空壳改为完整服务：`mor_reconciliation` 18 → 15 表（PDManer 脱离 Finance 生成器，`reconciliation-model.test.mjs`）；清算账户与 MID（channel-connector 核对）、解析接口；消费 `BillingFactRecorded`（PAYMENT / REFUND）生成平台流水；渠道账单分段 JSON 导入（Gateway 64 KiB 上限，D-01）、批量匹配（2 万行约 5 秒）、跨账单查重、作废、对平门禁；银行账户（Vault `CASH_ACCOUNT_NUMBER_V1`，复核）、银行对账单导入与连续性检查、一对一建议与手工多选匹配；异常与三种处置（`RE_MATCH` / `ACCEPT_TIMING_DIFFERENCE` / `CLASSIFY`，Maker ≠ Checker）；四类事件 `ActualChannelCostConfirmed`、`ChannelBatchReconciled`、`ChannelSettlementConfirmed`、`ReconAdjustmentApproved`（目录 `consumers: []`，未路由 `FAIL`）；XXL `compensationEventScan`、`channelLineWaitExpiry`。billing 合同：`FactRefType` + `CHANNEL_TRANSACTION_REF` / `RECON_RESOLUTION`、`t_billing_fact_ref` 两列（ledger 读取同步）。Operations BFF `/finance/reconciliation/v1/**` 38 路由、Web「财务 → 对账」7 页、Platform 块 123。
- **Verified：** 本机 reconciliation 93、billing 81、ledger 319、BFF 对账相关 117（全模块 1939）、Web 1194 + tsc、arch-rules 10、PDManer 24；本机全链路 78 + 6、浏览器 AC-V1—V6；mor-test 验收 69/69、浏览器只读 27/27（[阶段 5](<../requirements/20261020/reconciliation-渠道资金对账切片1/verification/stage5-report.md>)、[发布记录](<../requirements/20261020/reconciliation-渠道资金对账切片1/release/release.md>)）。
- **Deployed（mor-test，2026-10-21 03:11—03:3xZ，tag `recon-slice1-20261021`）：** reconciliation `sha256:f181a04c…`、ledger `sha256:b84f8f97…`、billing `sha256:b773d2cd…`、platform-support `sha256:79e33d43…`、ops-bff `sha256:9520a545…`、ops-web `sha256:edfb6628…`（03:25Z 起被商务会话 `333e7f2b…` 取代，源码含本需求）；`mor_reconciliation` 15 表、`mor_billing` 引用表 7 列、`mor_vault` 3 行、`mor_platform` 块 123 激活（PLATFORM_ADMIN v38）；XXL 分组 17、任务 20 / 21；验收数据已清理。备份 `~/Code-MoR-backups/20261021T031139Z-recon-slice1-mor-test/`。
- **Pending / 遗留：** billing 需求 B（PAYMENT / REFUND 自动事实、接收四类对账事件、ledger 归类码映射）未开始，在此之前四类事件无订阅方、发布留存 FAILED；切片 2（超期检测、借方暂记、拒付与重复扣款）、切片 3（出款 / 缴税）未开始；D-2 补偿超时只中断不等待（Foundation）；requirements-site FIN-12 / FIN-24 / E2E-08 旧口径待改；本机 `mor_account` 缺 `t_owner_account_no_sequence`（环境）。推送另行授权。

<a id="channel-connector-phase2-20261022"></a>
## channel-connector Cybersource（Network International）卡收单接入（2026-10-22，channel-connector 已部署 mor-test，渠道配置已部署，沙箱已验证，已本地提交，未推送）

- **Designed：** [需求](<../requirements/20261022/channel-connector-CYBS_NI接入/requirement.md>)、[技术方案](<../requirements/20261022/channel-connector-CYBS_NI接入/technique.md>)、[交付记录](<../requirements/20261022/channel-connector-CYBS_NI接入/delivery.md>)经用户逐阶段确认。
  - 渠道 `I_CYBS_NI`（Cybersource REST，HMAC HTTP Signature）实现 `PAY`、`AUTHORIZE`、`PAY_QUERY`、`REFUND`、`REFUND_QUERY`、`CAPTURE`，不含 3DS、钱包、网络 token、PF。
  - 查询走 Transaction Search；`DUPLICATE_REQUEST` 时回查原交易。
  - 一笔授权只能请款一次（不带分次参数）。
  - 退款、请款同步 `PENDING` 即受理成功。
  - 决定 F1：Cybersource 读接口最终一致（下单后 1～3 秒查不到，结果可能前后不一致），所以退款、请款按渠道引用直接调用。
- **Implemented（本地提交）：** channel-connector `ScriptFunctions` 新增 `sha256Base64`、`hmacSha256Base64DecodedKey`（密钥在受控句柄内做 Base64 解码）、`nowRfc1123`（`Locale.US`），并在 `RegisteredFunctionCatalog` 登记；不改表、接口、配置。
- **Verified：**
  - channel-connector auto 381/381，含用测试专用样本逐字节核对摘要和签名，以及一条端到端签名用例。
  - 8 个签名样本本机复算一致。
- **Deployed（mor-test，2026-10-22 12:49Z）：**
  - channel-connector `sha256:5f22b55b…`（tag `cybs-20261022`，基线 main `1d39cd94` 加本需求），回滚目标 `798940ea…`，发布文件见[release](<../requirements/20261022/channel-connector-CYBS_NI接入/release/>)。
  - 与原镜像 JAR 比对只差两个类；环境变量逐项相同。
- **mor-test 配置 Deployed / Sandbox Verified：**
  - 标准 API `REFUND`、`CAPTURE` 各追加可选字段 `paymentChannelRef`，服务端复核其他接入方流程，不受影响。
  - 渠道 `I_CYBS_NI`（PAYMENT，DRAFT），出站地址为 apitest（沙箱）；脚本 2 个、原子 API 4 个、流程 6 条；验证账号 `SBX_APPROVED` / `SBX_DECLINED`（商户 `PMAE01_CYBS_USD`）。
  - 7 个沙箱用例 PASSED（4 个写用例，等待 10 秒后 3 个读用例），[回执](<../requirements/20261022/channel-connector-CYBS_NI接入/assets/test-sandbox-receipt.json>)；MPGS 8/8 与其他渠道回归 8/8 PASSED。
  - 密钥与卡号按值核对：运行详情、日志、仓库均为 0 次。
- **Pending / 遗留：**
  - 推送另行授权。
  - 对接包 AED、BHD、EGP、KWD、ZAR 五组凭据在 apitest 返回 401。
  - Payment 调用 Cybersource 的退款、请款必须传 `paymentChannelRef`（缺失时为 NOT_SENT）；`PAY_QUERY` 在提交后几秒内返回 `NOT_FOUND` 不能视为终态。
  - 运行轨迹中的 `Signature` 头没有遮蔽，与其他脚本签名渠道相同。
  - `sealJsonBody` 会把含卡号的发送文本返回给脚本（技术方案 1.4），建议另行加固。
  - 未做：3DS、分次请款、`VOID`、PF。

## channel-connector MPGS（Network International）卡收单接入（2026-10-21—22，mor-test 配置已部署，沙箱已验证，已本地提交，未推送）

- **Designed：** [需求](<../requirements/20261021/channel-connector-MPGS_NI接入/requirement.md>)、[技术方案](<../requirements/20261021/channel-connector-MPGS_NI接入/technique.md>)、[交付记录](<../requirements/20261021/channel-connector-MPGS_NI接入/delivery.md>)经用户逐阶段确认。
  - 接入方（第二段后为渠道）`I_MPGS_NI`，MPGS REST v100，HTTP Basic（凭据另存 `apiUser = merchant.<merchantId>`）。
  - 实现 `PAY`（卡直付，不含 3DS）、`PAY_QUERY`、`REFUND`、`REFUND_QUERY`，2026-10-22 追加 `AUTHORIZE`、`CAPTURE`。
  - 状态取交易层 `result` + `gatewayCode` + `transaction.type`，修正旧系统原单部分退款后支付查询被判为处理中的问题；渠道引用取 RRN。
  - 预授权与请款的流程在渠道拒绝时回查本笔交易，避免重发被报成失败。
- **mor-test 配置 Deployed / Sandbox Verified（2026-10-21—22）：** 经管理接口写入，无代码、SQL、镜像、Nacos 变更，未建渠道账号与 MID。
  - 标准 API：`PAY` v2 → v3，只追加 4 个可选字段（`card`、`card.token`、`payment.clientIp`、`payment.userAgent`），其他 4 家接入方的流程经服务端复核不受影响；新增标准 API `AUTHORIZE`（请求字段同 `PAY`）和 `CAPTURE`。
  - `I_MPGS_NI`：脚本 `MPGS_BASIC_AUTH`；原子 API `PAY`、`RETRIEVE`、`REFUND`、`AUTHORIZE`、`CAPTURE`；6 条流程；验证账号 `SBX_APPROVED` / `SBX_DECLINED`（AED 商户 `JOLLYMAX_AED`，沙箱接受 USD）。
  - 8 个沙箱用例 PASSED（[回执](<../requirements/20261021/channel-connector-MPGS_NI接入/assets/test-sandbox-receipt.json>)），其他渠道回归 8/8 PASSED。运行详情、channel-connector 与 ops BFF 日志按值核对，不含凭据、认证头与卡号。
  - 测试交易均为终态。配置脚本见 [harness](<../requirements/20261021/channel-connector-MPGS_NI接入/assets/harness/README.md>)，凭据只从仓库外对接包读取。
- **Pending / 遗留：**
  - 推送另行授权。
  - 对接包中 USD 商户和 3 个 PF 商户的凭据已失效（401）。
  - 未做：3DS、Webhook、PF 模式、`PAY_CANCEL` / `VOID`、预授权与请款的查询标准 API。
  - Payment 尚未调用 `AUTHORIZE`、`CAPTURE`、`REFUND`；Payment 卡直付在 mor-test 上未与 MPGS 联调。
  - 已上线的 MPGS `REFUND` 流程在同一订单多笔退款后重发会被报成失败，Payment 接入退款时需按同样方式加回查。
  - 生产商户币种以 NI 开通为准。

## channel-connector 统一对外渠道出口 第二段：渠道分类、服务渠道通用入口与调用授权（2026-10-22，已部署 mor-test，已本地提交，未推送）

- **范围：** [技术方案](<../requirements/20261020/channel-connector-统一对外渠道出口/technique.md>)、[交付记录](<../requirements/20261020/channel-connector-统一对外渠道出口/delivery.md>)、[P2 报告](<../requirements/20261020/channel-connector-统一对外渠道出口/tasks/P2-report.md>)、[P2-web 报告](<../requirements/20261020/channel-connector-统一对外渠道出口/tasks/P2-web-report.md>)；画板 `operations-console-web/docs/operations-console.pen`「渠道 · 第二段 01–12」2026-10-22 确认（服务渠道凭据复用技术账号表、授权只停用不删除、激活只要求出站地址、调用方服务名格式 `[a-z][a-z0-9-]{1,63}`）。
- **Implemented（worktree `Code-MoR-ccu2`，基于 `281a217d`，2026-10-22 合入主工作区）：**
  - 数据库：`t_payment_channel` 与 `t_integration` 合并为 `t_channel`（新增 `channel_type` PAYMENT / SERVICE、`description`、`endpoints`、`version`、`created_by`、`updated_by`，`channel_code` 改 ascii_bin）；7 张 `t_integration_*` 改名 `t_channel_api`、`t_channel_script`、`t_channel_standard_api`、`t_channel_verify_*`，`integration_id` 改 `channel_id`；新增 `t_channel_call_grant`；`t_channel_account.entity_no` 改可空；配置变更目标类型 `INTEGRATION_*` 改 `CHANNEL_*` 并新增 `CHANNEL_CALL_GRANT`。`mor_channel_connector` 仍为 28 张表。
  - channel-connector：服务渠道通用入口 `POST /channel-connector/v1/service-executions`（校验调用方 × 渠道 × 标准 API 授权）；授权接口 `/channel-connector/v1/channel-call-grants` 与 `/{id}/status`；`PUT /channel-connector/v1/channels/{id}/endpoints`；支付 / 服务执行分舱（服务舱满返回 503 `CHANNEL_EXECUTION_BUSY`，支付舱满返回 NOT_SENT）；出站地址守卫（私网与 localhost 拒绝，`trusted-networks` 放行）与 `channel.outbound.access` 日志。
  - card 读卡只接受 `channel-connector-service`；arch-rules 新增 R15 与[对外出口例外清单](./decisions/2026-10-22-unified-outbound-exception-list.md)；ops-bff 转发新接口并删除 5 个接入方增删改接口；ops-web 渠道列表类型分段、新建渠道选类型、详情按类型分页签（协议配置、沙箱验证、凭据、调用授权矩阵、出站地址编辑），旧 `/external-api/integrations/**` 跳到渠道详情，删除接入方页。
  - Platform 注册块 129（`platform-support-initial-data.sql` 末尾与 [phase2-registration.sql](<../requirements/20261020/channel-connector-统一对外渠道出口/db/platform-support-service/phase2-registration.sql>)）：新权限 `CHANNEL_CALL_GRANT_MANAGE`、按钮 129301（页面 92302 改名「渠道」）、路由 129401–129404；停用 71303、71305 与路由 71006–71010，保留 71304 标准 API 页与权限 71102。
- **Verified（隔离库 `mor_*_ccu2`，仍保留）：** channel-connector 375/375、card 9/9、platform-support 745（148 例失败为通知两表缺列的预存漂移，补表后该 20 个类 210/210）、ops-bff 2025/2025、arch-rules 68/68、ops-web tsc + 1251/1251 + build；迁移在 `mor_channel_connector_v2e2e` 副本上演练（含 MATCHED / INTEGRATION_ONLY / CHANNEL_ONLY 合成行），行数、映射与完整性一致，结构含注释与 `channel-connector-schema.sql` 一致。
- **本机端到端（阶段 5，2026-10-22，[报告](<../requirements/20261020/channel-connector-统一对外渠道出口/verification/stage5p2-report.md>)）：** `mor_channel_connector_v2e2e` 已执行第二段迁移，`mor_platform_v2e2e` 已执行块 129 注册与激活（块 71 未重放），本机 e2e 运行第二段 card / channel-connector / platform / ops-bff（旧 JAR `*.pre-ccu2`，备份 `local-environment/e2e/backup/20261022T042107Z-ccu2-stage5/`）；回归与第一段基线一致，新能力 43 项通过；修复 2 个缺陷（出站地址变更摘要前后不可比、渠道列表并发 GET 触发 Gateway 429）并复验。
- **Deployed（mor-test，2026-10-22 06:47—07:12Z，[发布清单与执行记录](<../requirements/20261020/channel-connector-统一对外渠道出口/release/phase2-release.md>)）：** 源 main `4b5426e5`（数据查询 + 第二段 `654cb14f` + 文件存储 `346a1822`）+ D-3 修正；card `sha256:542a16f0…`、channel-connector `sha256:798940ea…`、platform `sha256:d50f2886…`、ops-bff `sha256:0abb662a…`、ops-web `sha256:9eaa8521…`（tag `ccu2-20261022` / `ccu2-20261022b`）；回滚 card `aee93799…`、channel-connector `1571ce18…`、platform `985d00ea…`、ops-bff `d221817d…`、ops-web `bcadc589…`。`mor_channel_connector` 第二段迁移（8 个渠道：DEMO_NI 合并、6 个接入方迁为 DRAFT 支付渠道、AAAA；channel-connector 停机约 58 秒），`mor_platform` 块 129 注册并激活（块 71 未重放，PLATFORM_ADMIN 42→43）。验收：快照差异全部可解释、DEMO_NI 链路与 7 个沙箱用例前后一致、服务渠道 18/18（授权 403/201/停用后 403、私网 422、支付渠道走通用入口 400）、冒烟 31/31、浏览器 13/13；验收中发现并修正 D-3（渠道详情同一路由并发 3 个请求触发 Gateway 429）。验收渠道 S7P2_SVC 已 CLOSED，测试标准 API S7P2_PING 保留。备份 `~/Code-MoR-backups/20261022T063410Z-ccu2-mor-test/`。
- **SQL：** 本机 auto 库 `mor_channel_connector` 已执行第二段 DDL（2026-10-22 05:40Z）；mor-test 已执行迁移与块 129 注册（见上）；channel-connector [phase2-10-precheck / 20-migrate / 30-verify 与回退说明](<../requirements/20261020/channel-connector-统一对外渠道出口/db/channel-connector/>)；Platform 块 129 注册 SQL。
- **Pending / 遗留：** 推送另行授权；第一段迁入的 `UpdateImplementationProcessor` 流程定义摘要有同类前后不可比问题；版本冲突与出站地址弹窗两处文案待调；配置变更不能按 `channelId` 查询；`t_channel_verify_account.entity_no` 仍 NOT NULL（服务渠道沙箱账号需占位 OE）；授权创建与实现删除用不同锁（X7）；"单渠道至多 1 个 ACTIVE 服务凭据"无唯一索引（X8）；DNS 固定依赖 JVM 缓存（X1）；Owner 级调用方限制未做（X15）；mor-test `trusted-networks` 为空，发布前须确认无私网出站地址；mor-test 不得重放块 71 激活语句；注册后须重新发布 Gateway Snapshot；`payment-model` / `payment-scenario-doc` / `card-model` node 测试既有失败。

<a id="channel-connector-unified-outbound-20261020"></a>
## channel-connector 统一对外渠道出口 第一段：外部 API 自 Platform 等价迁入（2026-10-21，已部署 mor-test，已本地提交，未推送）

- **决定与需求：** [决定记录](./decisions/2026-10-20-channel-connector-unified-outbound.md)；[需求](<../requirements/20261020/channel-connector-统一对外渠道出口/requirement.md>)、[技术方案](<../requirements/20261020/channel-connector-统一对外渠道出口/technique.md>)、[交付记录](<../requirements/20261020/channel-connector-统一对外渠道出口/delivery.md>)、[P1 报告](<../requirements/20261020/channel-connector-统一对外渠道出口/tasks/P1-report.md>)；阶段 1–3 已确认，阶段 4 第一段完成；第二段见[上一节](#channel-connector-phase2-20261022)。本节取代下文「Platform Integration 配置与 HTTP 执行归属（2026-09-21）」与「Platform 外部 API 配置资料迁移与标准 API 流程设计（2026-10-13）」中的 Owner 与模块状态描述。
- **Implemented（worktree `Code-MoR-ccu` 研发，2026-10-21 合入主工作区，随阶段 8 本地提交）：** channel-connector 新模块 `paytech-channel-connector-protocol`（Platform integration 主代码、`t_channel_config_change` 持久化与 `ChannelConfigTransactions`）；`/external-api-admin/v1/**` 44 个管理接口迁入 channel-connector-app，路径 / JSON / 状态码不变（新增 `ChannelConnectorApiErrorConfiguration` 保持 NOT_FOUND→404）；`ExecuteChannelOperationProcessor` 改为进程内调用标准 API 执行，`StandardApiNotSentException` 对应 NOT_SENT；读卡与 Vault Reveal 以 `channel-connector-service` 身份。Platform 删除 integration 模块、`StandardApiCommandApi` / `IntegrationLookupApi` 与对应测试；card 读卡第一段同时接受 `platform-support-service` 与 `channel-connector-service`；ops-bff 7 个外部 API 客户端改调 channel-connector（去 `Platform` 前缀）；表归属 Platform 57→47、channel-connector 18→28；`channel-connector-api` 依赖 Jackson 登记为 R2 已知偏差。
- **Verified：** 本机隔离库 `mor_*_ccu`（用后已删除）：channel-connector 全量 330/330、platform-support auto 734/734、card `ChannelCardMaterialRevealTest` 7/7；主工作区合入后 9 模块 test-compile、arch-rules 63/63、channel-connector 架构与领域 53/53、Platform 架构 1/1、ops-bff 1984/1984。
- **本机端到端（阶段 5，2026-10-21，[报告](<../requirements/20261020/channel-connector-统一对外渠道出口/verification/stage5-report.md>)）：** 旧版本基线与新版本对照一致（候选查询、REFUND 到合成 PayerMax、两类 NOT_SENT、沙箱用例、34 个管理 GET）；`*_v2e2e` 迁移演练 10 表行数 / CHECKSUM / 15 列 SHA-256 一致；新版本真实读卡 PAY SUCCESS。本机 e2e 当前运行新版本 card / channel-connector / platform / ops-bff。
- **Deployed（mor-test，2026-10-21 10:09—10:36Z，[发布清单与执行记录](<../requirements/20261020/channel-connector-统一对外渠道出口/release/release.md>)）：** 基线 main `1b60ae43` + 本需求改动；card `sha256:aee93799…`、channel-connector `sha256:1571ce18…`、ops-bff `sha256:4839c249…`、platform-support `sha256:2d40c1ba…`（tag `ccu-20261021`）；回滚 card `68d67684…`、channel-connector `4d31d529…`、ops-bff `e822301c…`、platform `3d8bc375…`。`mor_channel_connector` 18→28 表（10 表数据自 `mor_platform` 复制，行数 / CHECKSUM / 15 列 SHA-256 一致），`mor_platform` 57→47；XXL 任务 22（channel-connector `compensationEventScan`）。验收：外部 API 管理 GET 277 项 0 差异、7 个沙箱用例前后逐步一致、DEMO_NI NOT_SENT 一致、写入抽样一致且留 `change_detail`、Vault REVEAL 调用方切换为 channel-connector-service、冒烟 14/14（含对账 / 商务 / 币种页面接口）。C2 合成凭据因签名脚本失败未真正出站（B 已证明出站）。备份 `~/Code-MoR-backups/20261021T101005Z-ccu-outbound-mor-test/`。
- **SQL：** channel-connector 增量 [2026-10-20-protocol-tables.sql](<../paytech/channel-connector/docs/database/sql/changes/2026-10-20-protocol-tables.sql>)（10 表 + `change_detail`）已在本机 `mor_channel_connector_v2e2e`（含数据迁入）、auto 库 `mor_channel_connector`（无数据）与 mor-test 执行；Platform [drop-integration.sql](<../requirements/20261020/channel-connector-统一对外渠道出口/db/platform-support-service/drop-integration.sql>) 已在 mor-test 执行，本机 `mor_platform_v2e2e` 原表保留；mysqldump 需加 `--set-gtid-purged=OFF` 等三项。
- **Pending / 遗留：** 推送另行授权；internal-auth 开启时 channel-connector 无 Token 来源无法启动（迁移前已存在，mor-test 未开启）；迁入表 `create_time` / `update_time` 列注释仍写 Platform，第二段改名时修正；`ChannelConfigChangeApi` 的 targetType 白名单未含 7 个新类型（第二段）；Platform 测试 `init/ddl.sql` 通知两表缺 SES 事件拉取 9 列（预存漂移，非本需求）；`payment-model` / `platform-support-model` / `platform-support-schema` 既有 node 测试失败与 HEAD 相同。

<a id="commercial-capability-pricing-20261020"></a>
## commercial 开通项接受条件与标准价目模板（一期，2026-10-20—21，已部署 mor-test，已本地提交，未推送）

- **决定与需求：** [决定记录](./decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md)（Confirmed，修订 10-19 六层模型 L1 / L3 / L4）；[需求](<../requirements/20261020/commercial-开通与定价模型调整/requirement.md>)（S1–S12、AC-01–AC-13）、[技术方案](<../requirements/20261020/commercial-开通与定价模型调整/technique.md>)（§11 C1–C12 裁决）、[确认记录](<../requirements/20261020/commercial-开通与定价模型调整/delivery.md>)；阶段 1–8 完成（2026-10-21 用户确认本地提交，未推送）。
- **模型（`mor_commercial` 20 → 22 表）：** 新增 `t_product_capability_dimension`（可开通项可配维度）、`t_term_version_acceptance`（价格协议签署）；`t_agreement_capability.require_3ds`；`t_agreement_capability_limit` 改为接受条件（同维 OR、异维 AND，CURRENCY 必填）；`t_product_capability.change_version`，`dimension_code/value` 废弃恒空；`t_product_fee_event.capability_kind/capability_code/change_version`；`t_commercial_term_plan.owner_type`（含 PLATFORM_TEMPLATE）/ `template_name`；`t_commercial_term_version.agreement_id` 可空、状态增 CLIENT_SIGNED、`source_template_version_id`、`signature_exempt`；`t_commercial_term_price.source_template_price_id`。SQL：[ddl.sql](<../requirements/20261020/commercial-开通与定价模型调整/db/commercial/ddl.sql>) → [migration.sql](<../requirements/20261020/commercial-开通与定价模型调整/db/commercial/migration.sql>)（CARD_* 合并为 CARD，存量 ACTIVE / SCHEDULED 版本免签）。
- **Implemented：** commercial 目录维护与维度清单、合同开通项接受条件、标准价目模板方案 / 版本 / 价格 / 独立 TEMPLATE_APPROVE 复核、基于模板生成与补齐（只取已开通项 × 币种并按接受条件裁剪）、来源与「标准价已更新」、破标（BELOW_STANDARD / NO_BENCHMARK，版本破标数）、相关性（保存提示、激活拦截，价格行须锚定已开通支付方式开通项）/ 完整性 / 零开通校验、客户签署（E_SIGNATURE / CLICK_ACCEPTANCE / MANUAL_SIGNATURE），Checkout 解析输出接受条件与 3DS、无生效价格的开通项不输出；operations-console-bff `/commercial-admin/v1` 新增 17 路由；operations-console-web 产品目录维护、标准价目页、开通条件控件、价格生成 / 补齐 / 来源 / 破标 / 按开通项分组、签署登记、复核队列破标数；Platform 块 124（3 / 3 / 5 / 17 / 94 / 3 / 102 行）。三线 worktree：A commercial `a9828758`、B ops-bff + Platform `56ba38fe`、C ops-web `f48fe526`，集成 `.claude/worktrees/cp-int` `c6eea71a`；主工作区已应用同内容（与对账会话共用的 11 个文件已三方合并）。
- **Verified：** 集成构建 commercial 196/196、ops-bff 1889/1889、ops-web 1205/1205 + build、arch-rules 61/61；主工作区（含对账会话改动）arch-rules 62/62、ops-web 1217/1217。mor-test 验收 12/13（[stage7-acceptance.md](<../requirements/20261020/commercial-开通与定价模型调整/verification/stage7-acceptance.md>)）：缺陷 D1（模板生成保留只带功能费的行）与 D2（模板版本读回结算字段为空）已修复并复验通过（10/10、8/8）；AC-09 Checkout 端到端受环境阻塞（Risk 资格客户端为失败关闭占位，返回 422 CLIENT_NOT_ELIGIBLE），由 auto `CheckoutResolutionMatrixTest` 覆盖；偏差 V1 字段格式错误 422 VALIDATION_ERROR、V2 签署方式、V3 合成 Partner 770000001002。
- **Deployed（mor-test，2026-10-20 21:00—21:54Z，[release.md](<../requirements/20261020/commercial-开通与定价模型调整/release/release.md)，[compose-candidate.yaml](<../requirements/20261020/commercial-开通与定价模型调整/release/compose-candidate.yaml>)）：** commercial `sha256:c6c9b8f6…`（tag capability-20261021c，含 D1 / D2 修复）、ops-bff `sha256:9708ccdb…`、platform-support `sha256:95348566…`（只为 capabilities JSON）、ops-web `sha256:abab7475…`（入口 `index-DFtXS91w.js`）；块 124 激活 17 / 5 / 3 / 1（PLATFORM_ADMIN 36 → 37）。2026-10-21 对账切片 1 替换共享 platform `79e33d43…` / ops-bff `9520a545…`（含本需求）；03:25Z 验收反馈 F1–F3（开通项右侧抽屉、接受条件逐维度行与汇总、已开通项置灰可编辑）只替换 ops-web 为 `sha256:333e7f2b…`（capability-20261021d，验收 17/17、回归 18/18 + 49/49，回滚 `edfb6628…`）；03:58Z 第二轮 F4–F6（币种在前、币种下拉、合同详情「产品与开通」按订阅产品分组）ops-web `sha256:139c157e…`（capability-20261021e，验收 15/15、回归 18/18 + 49/49，回滚 `333e7f2b…`）。观察 O2 / O3（「更多币种」下拉只列非快捷币种且不折叠、列表条件标签显示中文与「要求 3DS」）已在代码中修复并随本次提交；05:17Z 上线 ops-web `sha256:46227f3d…`（capability-20261021f，基于币种会话 366f8b95 源码），验收通过；验收发现原有缺陷 O4（费率预览开通项开 3DS 时丢接受条件），前端按维度合并 limits 修复，05:36Z 上线 ops-web `sha256:c6d2118f…`（capability-20261021g，复验 3/3、商务页 18/18，回滚 `46227f3d…`）；O4 修复随补充提交入库（父 `13c02c87`，未推送）；06:1xZ 对账会话 recon-detail-20261021 替换 ops-web `70829a3a…` / ops-bff `e822301c…` / platform `3d8bc375…`（源含 O4），为 mor-test 当前基线。回滚 commercial `59bb000d…`、ops-bff `bec85826…`、ops-web `bbb88dd8…`、platform `e5a06814…`；迁移不可逆，数据回退用备份 `~/Code-MoR-backups/20261020T205752Z-commercial-capability-pricing/`。测试数据：AE001 四套模板（标准 / 优惠 / 平台费 / 外汇点差）、DEMO2-CL-101 / 102 / 103 / 901（含 v5 DRAFT 回归版本）、DEMO2-PT-101，外汇点差模板 v2 已生效，见验收第 5 节。
- **已知缺口 / 遗留：** 二期执行链路（Card BIN、收银台提示与置灰、Commerce 冻结、Payment 授权前拦截）未开始；Payment 支付方式目录只读接口未提供，一期按格式 + 已知代码校验；非支付方式类定价画像（平台费、外汇等）未锚定到开通项；合同详情「已开通 · 待定价」提示与变更历史 PRODUCT_CATALOG 筛选未做；模板版本工作区对组件显示冗余的「无标准可比」（O1）；client-master PDManer 脚本测试 2 例基线失败（与本需求无关）；AC-09 待 Risk 资格合同接入后在 mor-test 复验；7 个合成审批人已停用未删除。

<a id="channel-connector-ops-console-20261020"></a>
## ledger 自动记账按业务时间选择规则版本（2026-10-20，已部署 mor-test，已本地提交，未推送）

- **范围：** [需求](<../requirements/20261020/ledger-记账规则按业务时间选版本/requirement.md>)与[方案](<../requirements/20261020/ledger-记账规则按业务时间选版本/technique.md>)：自动记账不再只看规则包当前版本，改为在全部版本中取生效区间覆盖事实业务时间的版本（含已退役版本与已到期待切换的 APPROVED 版本），规则切换后迟到事实不再被拒绝卡住分片；审批时生效时间已过则改按审批时间生效（Q1）。无接口、表结构、初始化数据变化。
- **Implemented：** ledger `AccountingRuleService.findApplicableVersion`、`TransactionPostingServiceImpl.effectiveBundleVer`、`approveVersion` 与版本更新 SQL 写入 `effective_from`；Operations Web 记账规则页 3 处提示。
- **Verified：** ledger 模块 319/319（研发基线 `b04b77f1` 与发布基线 `5f12cf3e` 各一次），新增 `RuleVersionSelectionPostingTest` 覆盖 AC-01—AC-06，AC-07 在 `RuleVersionLifecycleTest`；ops-web 1157 + build。本地 `mor_ledger` 经用户同意按现行 Schema 与初始化数据重建。
- **Deployed（mor-test，2026-10-20 16:41Z，[发布 override](<../requirements/20261020/ledger-记账规则按业务时间选版本/release/compose-candidate.yaml>)）：** 只替换 ledger `sha256:ff20e335…`（回滚 `sha256:d846efae…`），环境变量不变，XXL 任务 19 正常；Operations Web 提示已随其他会话部署的 `sha256:105afba9…` 上线，本需求未单独发布 Web。
- **Pending：** 推送另行授权；已知边界：到期未切换的 APPROVED 版本在同一分钟内被立即审批的新版本取代时，该分钟内先后处理的事实可能选中不同版本（需审批前先激活到期版本，另行处理）。

## channel-connector 运营控制台：渠道成本方案与渠道基础配置（2026-10-20，已部署 mor-test，已本地提交、未推送）

- **范围：** [需求](<../requirements/20261020/channel-connector-运营控制台渠道成本与基础配置/requirement.md>)与[方案](<../requirements/20261020/channel-connector-运营控制台渠道成本与基础配置/technique.md>)：channel-connector 新表 `t_channel_config_change`（18 表）、成本方案列表 / 作废 / 渠道存在校验、变更记录查询、渠道 / 技术账号 / MID / 能力四组管理合同与 13 个写 Processor（`tryLocks`，Vault Protect 在事务外）、Platform 内部免会话接入方查询 `IntegrationLookupApi`（`GET /standard-apis/v1/integrations/{code}/existence`）、Operations BFF `/channel-admin/v1/**` 31 条路由与版本对比聚合、Web 一级菜单「渠道」三页（Pencil 15 画板）、Platform 注册块 92（402 行）、Vault PROTECT 规则（`channel-connector-service` / `CHANNEL_ACCOUNT_CONFIG_V1` / `CHANNEL_CONFIGURATION`）。路由策略、健康度、回调查询页不在本期；OE 只校验格式（client-master 无查询接口）。
- **Implemented（2026-10-20 本地提交于 main，父提交 `b04b77f1`，未推送）：** channel-connector 152 文件、Platform 9 文件、ops-bff / ops-web 46 文件、arch-rules 表归属、PDManer 生成输入；研发期决定见方案第 9 节与第 6 节（锁机制、幂等键字符集、400 映射、Platform 内部接口例外）。
- **Verified：** channel-connector-test 187、arch-rules 61 + 3、platform-support-test 定向 37 / 全量 884、ops-bff 1844、ops-web 1155 + build，全部 0 失败；本机 `*_v2e2e` 全链路：控制台登记渠道 / 账号（真实 Vault Protect）/ MID / 能力 → 候选查询返回 → REFUND 经 Platform 标准 API 到达 payermax-mock 且签名有效，API 103 / 103、浏览器 35 / 35；本机 e2e 的渠道种子 SQL 与 `channel_setup.py` 退役，Platform 接入方改为 `PAYERMAX_MOCK`。
- **Deployed（mor-test，2026-10-20 07:24Z—07:37Z，[发布记录](<../requirements/20261020/channel-connector-运营控制台渠道成本与基础配置/release/release.md>)）：** platform-support `sha256:e5a06814…`、channel-connector `sha256:4d31d529…`、ops-bff `sha256:bec85826…`、ops-web `sha256:3ae27f3d…`（D1 修复后，tag `channel-20261020b`）；`mor_channel_connector` 18 表；`mor_vault` 策略版本 17；`mor_platform` 块 92 启用（31 / 9 / 7 / 1，PLATFORM_ADMIN 36）。验收：接口 105 / 105、浏览器 35 / 35、既有商务 / 财务页 17 / 17、Payment 侧候选查询 2 / 2；渠道执行未在 mor-test 跑（`PAYERMAX` 接入方指向真实 UAT、凭据为合成），验收数据已清理。
- **Pending：** 推送与后续发布另行授权；mor-test 真实渠道执行需真实 UAT 凭据或内部 mock 接入方；旧 Vault Token 不回收；Platform `console-resource-capabilities.json` 既有缺 9 页；`payment.pdma.json` 过期与场景文档 `adapter_code` 两个既有 PDManer 测试失败；`internal-auth` 开启前需把 `channel-connector-service` 加进 Platform 签发白名单、channel-connector 入站接受 `operations-console-bff`。

<a id="counterparty-rename-20261020"></a>
## counterparty：consumer 服务改名（2026-10-20，已部署 mor-test，已本地提交，未推送）

- **范围：** 按[决定](./decisions/2026-10-19-counterparty-repositioning.md)切片 1 与[需求](<../requirements/20261019/counterparty-consumer服务改名/requirement.md>)：`paytech/consumer` → `paytech/counterparty`，服务 / 发现名 `counterparty-service`，内部合同前缀 `/counterparty/v1`，库 `mor_counterparty` / 账号 `counterparty_app`；只改名不改行为，Client API `/client-gateway-api/v1/customers…` 与错误码不变。
- **Implemented（已本地提交，未推送）：** main `c661e9fc`（146 个文件：counterparty 模块、acquiring 与 card 调用方、arch-rules、instance-manifest、PDManer 生成器与测试、local-environment、test-environment、现行验收脚本 `e2e_http.py`）。
- **Verified：** acquiring 804、counterparty 21、card-test 38、card-core 11、arch-rules 61、instance-manifest 16 全部通过，与改名前基线一致；全 Reactor `test-compile` 通过；未跑全 Reactor 测试（避免清空并行会话的本机测试库）。本机 `mor_counterparty` 已建并冒烟。
- **Deployed（mor-test，2026-10-20 03:19Z—03:24Z）：** counterparty-service `sha256:c7143680…`、acquiring-service `sha256:03729ba8…`、card-service `sha256:68d67684…`（tag `counterparty-20261020`）；`mor_consumer` → `mor_counterparty`（0 行，旧库保留）；XXL group 15 / job 18 启用、consumer job 8 停用。私网冒烟与 acquiring 建单关联 Customer 通过，见[发布记录](<../requirements/20261019/counterparty-consumer服务改名/release/release.md>)。
- **清理（2026-10-20）：** mor-test 与本机已删除 `mor_consumer` / `consumer_app`、旧 consumer-service 容器与镜像、XXL group 8 / job 8；本机全链路工具 `run-service.sh` 改用 counterparty 与 `mor_counterparty_v2e2e`。
- **Pending：** 推送（用户决定暂不推送）；切片 2（Customer 档案、资料原值只进 counterparty）与 Gateway Route 未开始。

<a id="finance-console-three-20261020"></a>
## Operations 财务：人工调整与计费事实 / 期间关账与试算 / 记账规则包（2026-10-20，已部署 mor-test，已本地提交，未推送）

- **范围：** [人工调整与计费事实](<../requirements/20261020/finance-人工调整与计费事实/requirement.md>)、[期间关账与试算](<../requirements/20261020/finance-期间关账与试算/requirement.md>)、[记账规则包维护](<../requirements/20261020/finance-记账规则包维护/requirement.md>)，共用[技术方案](<../requirements/20261020/finance-人工调整与计费事实/technique.md>)：ledger / billing 命令改读 Gateway 操作人 Header；新增 `FINANCE_ACCOUNTING_APPROVE`；人工调整列表、计费事实管理列表与作废（`VOIDED`）、规则包列表与作废草稿（`VOIDED`）、`accountingRuleVersionActivation` 定时任务（ledger 首次接入 XXL）；`LedgerErrorCode` +14、`BillingFactErrorCode` 4；Operations 新增「调整」「期间关账」「记账规则」三页与试算平衡「发起试算」。
- **Implemented（已本地提交，未推送）：** 基于 `d565f85f`，211 个文件。
- **Verified：** 专用本机库 `mor_ledger_fin3` / `mor_billing_fin3`：ledger 312、billing 80、BFF 1766、Web 1145、arch-rules 61、PDManer 15；mor-test 接口 28/28、无头浏览器 8/8。
- **Deployed（mor-test，2026-10-20 06:09—06:20Z）：** tag `fin-20261020`：platform-support `sha256:d800c3ce…`、ledger `sha256:d846efae…`、billing `sha256:99839156…`、operations-console-bff `sha256:adec3f63…`、operations-console-web `sha256:c6661078…`；Platform 块 120—122 启用（PLATFORM_ADMIN v35）；XXL 任务 19。Platform 镜像同时首次带上 commercial 块 93 / 95 的能力清单。
- **Pending：** 推送；后续发布 ledger / billing / platform-support / ops-bff / ops-web 须基于含本批改动的代码；mor-test 只有一个运营账号，复核人页面操作未用第二个真人账号走查；Platform ID 块注意 mor-test 风控控制台占用的 `101xxx—104xxx`、`111xxx—114xxx`。

<a id="ledger-chart-hierarchy-20261020"></a>
## ledger 会计科目分级与可读账户号（2026-10-20，已部署 mor-test，已本地提交，未推送）

- **范围：** 按[需求](<../requirements/20261019/ledger-会计科目分级维护/requirement.md>)、[技术方案](<../requirements/20261019/ledger-会计科目分级维护/technique.md>)与[决定](./decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md)：科目 4-2-2-2 分级编号与启停状态、STANDARD 改为三级树（一级 19、二级 26、三级末级 29，共 74 个，系统代码不变；2026-10-20 由两级改为三级）、科目维护（新增 / 修改 / 停用 / 启用，直接生效并写 `t_chart_account_change`）、采用后结构冻结、科目余额与试算平衡详情按级次汇总、ledger 与 account 账户可读账户号（`AE001-USD-22010101-0001` / `AE001-USD-22010101-S000001`）及控制台显示与搜索；「发起试算」按钮本期不做。
- **Implemented（已本地提交，未推送）：** 2026-10-20 合入 main（在 `cf39a1ee` 上实现，合入时基于 `4204a8d9`），201 个文件：ledger 118、account 33、Operations BFF 22、Web 18、Platform 初始化 SQL 块 91、arch-rules 3、文档与 requirements-site。
- **Verified：** 专用本机库 `mor_ledger_chart` / `mor_account_chart` 与 vhost `/mor-chart`：ledger 294、account 63、BFF 1592、Web 1109、arch-rules 61、PDManer 21 全部通过；mor-test 接口验收 26/26 与浏览器验收通过。
- **Deployed（mor-test，2026-10-20 03:14Z UTC 三级重发）：** ledger `sha256:a716585b…`、account `sha256:dfa6254c…`、operations-console-web `sha256:a8feab2c…`（tag `chart3-20261020`），operations-console-bff `sha256:971c9238…`（`chart-20261020`）；`mor_ledger` 33 表、`mor_account` 10 表、`mor_platform` 块 91 注册并启用（PLATFORM_ADMIN v33）；保留 AE001 演示数据。发布记录见需求目录 `release/release.md`。
- **Pending：** 推送；后续发布 ledger / account / ops-bff / ops-web 的会话须基于包含本需求的代码构建（新列 `account_no` 为 NOT NULL）。mor-test 与 local RabbitMQ 曾被 OOM Kill（高水位按宿主机内存计算），`mor-local-rabbitmq-1` 仍为退出状态；mor-test XXL-Job Admin 接近 512 MiB 上限、当晚重启 19 次，account 执行器注册间歇超时。

<a id="v2-skeleton-module-migration-20261018"></a>
## V2 骨架与模块搬迁（阶段 0，refactor-main，2026-10-17—18，未合入 main、未推送、未部署）

- **范围：** 按 [V2 决定草案](./decisions/2026-10-17-target-architecture-v2.md) D09、D11、D22、D24–D26、D31–D36 与[需求](<../requirements/20261017/architecture-V2骨架与模块搬迁/requirement.md>)，把 12 个旧工程根并入单一 Maven Reactor `paytech/`（唯一 Wrapper `paytech/mvnw`）。各步任务卡与报告见 [tasks/](<../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/>)。本节只登记事实，规范正文仍以 `DOCUMENT_GOVERNANCE.md` 路由的 Owner 为准。
- **Implemented：**
  - **目录与模块：** `paytech/` 下有 22 个服务目录（原 24 个：data-service 已删除，并入 Platform data-query，已部署 mor-test，见 [2026-10-22 决定](./decisions/2026-10-22-data-query-into-platform-support.md)；workflow-approval 占位已删除，见下）。
    - 17 个已建工程：platform-support、api-gateway、operations-console-bff、client-console-bff、vault、client-master、commercial、acquiring、counterparty（2026-10-20 由 consumer 改名）、payment、channel-connector、card、risk-decision、compliance、billing、account、ledger。
    - 另有 reconciliation、tax 两个空壳服务。
    - dispute、payout 只有模型；fx 只有 README 占位。workflow-approval 占位已删除，工作流并入 Platform Support（[2026-10-22 决定](./decisions/2026-10-22-workflow-into-platform-support.md)）。
    - 另有 `paytech/build`（BOM、父 POM）、`paytech/foundation`（17 个模块，含 `paytech-messaging(-api)`、`paytech-internal-auth`）与 `paytech/tools/{arch-rules,arch-rules-reactor-test,instance-manifest,migration-runner}`。
  - **模块坐标：** `com.paytech:paytech-<服务>-{api,core,app,test}`（Gateway 与两个 BFF 无 api / core）。服务名、业务端口与管理端口见 [LOCAL_DEVELOPMENT_ENVIRONMENT.md](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 8.2 节；服务目录见 `paytech/tools/instance-manifest/src/main/resources/catalog/services.yaml`。
  - **库：** 每个服务独立库：`mor_client_master`、`mor_commercial`、`mor_acquiring`、`mor_counterparty`（2026-10-20 由 `mor_consumer` 改名）、`mor_payment`、`mor_channel_connector`、`mor_card`、`mor_risk_decision`、`mor_compliance`、`mor_ledger`、`mor_billing`、`mor_account`、`mor_reconciliation`、`mor_tax`；`mor_platform`、`mor_vault` 不变。模型为 `paytech/<服务>/docs/database/<服务>.pdma.json`（18 份），R4 表归属由 `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` 强制（265 张物理表 + 56 张保留表 + 6 张 step-added；2026-10-19 计费事实重构后为 −7 step-removed + 20 step-added）。
  - **过渡与清理：** 第 07 步 account 对 `mor_ledger` 的共库只读已于第 11b 步结束，`transitionalSharedReads` 已删除，account 改用自有 `mor_account`。第 05 步卡表暂居 `mor_payment` 的过渡已于第 12 步结束。旧根在 git 中为 0 个文件；文档链接已改写到新路径。
  - **消息：** `<producer>.events` Fanout 加订阅方一致性哈希分区队列（D34）。payment、compliance 只发新信封；acquiring、client-master、card、ledger、account 为消费者；billing 发布 `EconomicFactRecorded`（2026-10-19 改名 `BillingFactRecorded`，代码已改、未部署，见下方计费事实重构一节）。旧 `topic_*` / `cg_*` 不再由代码声明。
  - **test-environment：** Compose 已按上述服务拆分（18 个 Java 服务，每个服务一个 `JAR_FILE`）；`build.sh` 单 Reactor 打包；`initialize.py` 新增 V2 库建库与 Schema 动作。2026-10-18 mor-test 已按 V2 部署，见下方"mor-test V2 部署"。
- **Verified：**
  - **全 Reactor：** refactor-main@6b8a6672（11b 合入后）执行 `cd paytech && ./mvnw -B clean verify`，BUILD SUCCESS，用时 19:49，2026-10-18 10:16 完成（主会话）。
    - 结果：42 个含测试模块、8218 个测试，0 失败、0 错误、0 跳过。
    - 运行环境含 Vault Go 服务端二进制与 `VAULT_TEST_*`、`PAYTECH_REDIS_PASSWORD`、`MOR_RABBITMQ_*`。
    - 历史全 Reactor：02x 后 4642，03 后 6011（主会话）。
  - **各步搬迁前后测试数**（执行数，均 0 失败、0 跳过）：

    | 步骤 | 搬迁前 | 搬迁后 |
    |---|---|---|
    | 01 Foundation | 722 | 893 |
    | 02a Platform | 759 | 798 |
    | 02b Gateway | 1277 | 1306 |
    | 02c BFF | 1386 / 76 | 1390 / 80 |
    | 02d Vault | Go 顶层用例 148，Java 53 | Go 177，Java 58 |
    | 03 Commerce | 808 | acquiring 801 + consumer 21 |
    | 04 CCT | 53 | client-master 57 + commercial 24 |
    | 05 Payment | 127 | payment 72 + channel-connector 75 |
    | 06 Risk | 2083 | risk-decision 1726 + compliance 374 |
    | 07 Finance | 242 | 316 |

  - **后续各步终值：** 08 起：acquiring 803、client-master 58；11a：billing 89；11b：ledger 204、account 40；12：card-test 38、card-core 11、payment 62；12x：payment 63。arch-rules 56、data-service-test 16（data-service 已删除，并入 Platform data-query）、migration-runner 27。
  - **app 启动：** 各步 app 以 mock Profile 启动后，管理端口健康检查均为 UP。
  - **第 13 步：** `scripts/verify-markdown-links.mjs` 0 broken；`scripts/rewrite-moved-links.mjs --check` 0 待改；`verify-java-foundation.mjs` 通过。requirements-site 137 / 127 / 10、PDManer 557 / 520 / 37 均与起点失败集相同。test-environment Python 336 项、3 failures、37 errors，均为搬迁前即有的 Risk rollout 工具钉值漂移。
  - **本机库 DDL 应用情况**（`mor-local-mysql-1`，root 执行各服务测试 `init/ddl.sql` 或评审增量 SQL）：

    | 库 | 表数 | 步骤 |
    |---|---:|---|
    | `mor_acquiring` | 16 | 03、08 |
    | `mor_consumer` | 2 | 03 |
    | `mor_client_master` | 29 | 04、08 |
    | `mor_commercial` | 15 | 04 |
    | `mor_channel_connector` | 13 | 05 |
    | `mor_payment` | 15，另有卡 4 表残留 | 05 重建；12 为 `t_payment_instrument_token` 加 4 列 |
    | `mor_risk_decision` | 55 | 06 |
    | `mor_compliance` | 25 | 06 |
    | `mor_ledger` | 31 | 07 建库；11b 执行拆表与调整规则 SQL |
    | `mor_billing` | 9 | 07 建库；11a 执行 maker-checker 增量 SQL |
    | `mor_reconciliation` | 18 | 07 |
    | `mor_tax` | 24 | 07 |
    | `mor_account` | 8 | 11b |
    | `mor_card` | 8 | 12 |
    | `mor_platform` | 新增 `t_currency_spec` 及 USD 行 | 02a |

    旧库 `mor_cct`、`mor_commerce`、`mor_risk`、`mor_finance` 保留，供 main 分支使用。
  - **account PDManer 模型脱离生成器（2026-10-18，已随 `080909fb` 提交）：** `account.pdma.json` 改为直接维护 JSON，Finance 生成器只输出 ledger、billing、reconciliation、tax、payout 五份；表结构未变。新增“余额权威与事实应用”图组（5 表、2 条库内主键关系：已应用余额变化 n:1 已处理事实、n:1 余额账户），“可靠执行与消费幂等”图组补入两张消费表；结构门禁改由 `scripts/pdmaner/account-model.test.mjs` 读 JSON 校验（3/3 通过）。
- **Pending：**
  - **account / ledger 明细账与控制科目（2026-10-18 决定已确认，未实施）：** [决定](decisions/2026-10-18-account-ledger-subledger-control-account.md)与[详细设计](../design-discuss/finance-account-ledger-v3/design.md)改为 billing → account → ledger 单向链路、ledger 只记控制科目、余额桶与银行现金额度归 account、删除金额核对。现行代码、模型与本机库仍是第 11b 步双消费实现；下一步为阶段 2 模型与 SQL。
  - **account 明细账与 ledger 控制科目重构（2026-10-19，阶段 7 已部署 mor-test，已本地提交 `080909fb`，未推送）：** mor-test 已于 2026-10-19 10:43—10:54Z 部署并验收：六个镜像（tag `subledger-20261019`，源码 HEAD `2bae8597` + 441 个文件）billing `sha256:403fc7de…`、account `sha256:b356a4f3…`、ledger `sha256:fb7c661d…`、Platform `sha256:33c196ec…`、Operations BFF `sha256:446ad697…`、Operations Web `sha256:12e2e021…`，Gateway 未动；SQL 已执行：`mor_account` 9 表明细账（`db/account/ddl.sql`）、`mor_ledger` 加 `account_entry_id` 与注释（`db/ledger/ddl.sql`）、规则行 44 → 36（`db/ledger/dml.sql`）、`mor_platform` 块 99（39 行）并经 `release/test-activate.sql` 激活 3 / 1 / 1（PLATFORM_ADMIN 31）；RabbitMQ `/mor-test` 已删除 `ledger.billing.p0`—`p3`、`ledger.billing.dlq` 与交换机 `ledger.billing.hash` / `ledger.billing.dlx`（0 消息），新声明 `account.events` → `ledger.account.*`；XXL 任务 14 `accountBalanceMaturity`（account 执行器、`0 */10 * * * ?`、启用，手动触发 matured 0）；验收接口 58/58、浏览器 31/31、全页面回归 34/34，合成数据已清理回基线；备份 `~/Code-MoR-backups/20261019T104222Z-subledger-test/`，执行记录与回退见需求目录 `release/release.md` 第 6 / 5 节。按[需求](<../requirements/20261019/finance-account明细账与ledger控制科目重构/requirement.md>)与[技术方案](<../requirements/20261019/finance-account明细账与ledger控制科目重构/technique.md>)多线并行实施：account-api / ledger-api / billing-api 合同与事件目录（`AccountPosted` 取代 `BalanceMoved`，聚合 ID `{OE|FE}.{entityId}.{currency}.{shardNo}`）、account 与 ledger 实现、Operations BFF / Web 明细账查询，以及 Platform 初始化 SQL 块 99（明细账菜单 1、路由 3，休眠，39 行）与 `console-resource-capabilities.json` +1；test-environment Compose 删除 account 的 `ACCOUNT_LEDGER_SERVICE_URL`（未部署）。本机 `mor_account` / `mor_ledger` 未重建，各线验证结果见需求目录 `tasks/*-report.md`。
  - **Finance 生成器 --check 漂移：** 提交 `cf4a429c` 直接修改了 18 份模型的 name/describe，Finance 五份服务模型因此与生成器输出不一致（仅这两个字段），`finance-model.test.mjs` 第 2、3 项失败；需在生成器中同步或继续把对应服务移出生成器。2026-10-19 billing 已移出生成器，余下 reconciliation、tax、payout 三份仍有该漂移。
  - **design/04 修订：** §3.2 与 §6 按 [11b 报告](<../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/11b-ledger-account-split-report.md>)偏差 1–3 修订：余额身份带科目并由 account 自有主键；新增 `t_balance_applied_entry`；试算不再比对账户投影，改由日终核对与关账 consistency 承担。主会话已接受这三项偏差，规范正文未改。design/03 的表数与 `t_account` 归属同样按 11b 报告回写。
  - **DRAFT 事实驳回 / 撤回切片：** 第 11a 步用户决定在 11b 之后作为独立小切片。
  - **撤销 account_app 对 mor_ledger 的只读授权：** `REVOKE SELECT ON mor_ledger.* FROM 'account_app'@'%';` 待执行（本机及已建该授权的环境）。
  - **旧 RabbitMQ 拓扑删除：** 待用户执行，见下文本机一次性操作。
  - **Vault REQUIRED 切换：** Vault 与各服务 `internal-auth.mode` 目前均为 `OFF`。前提是 Platform 签发端上线、所有调用方接入 `InternalTokenSupplier`，并以 DUAL 观察一段时间；DUAL 期限未定。
  - **Platform 内部 Token 签发：** 改为表驱动，待 `t_jwt_signing_key` 增加 `key_usage` 的 Schema 评审；草案见 [02a 报告](<../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/02a-platform-support-report.md>)第 4 节。当前为配置注入的 ES256（`STATIC`，prod 拒绝启动）。
  - **规范修订：**
    - `t_schema_migration` / 迁移执行器替代"人工执行当前 Schema"，涉及 `DATABASE_DESIGN_GUIDE.md`、`TECHNOLOGY_STACK.md`、`DEVELOPMENT_CONVENTIONS.md` 与 AGENTS.md 的 Manual infrastructure rule（第 10 步待确认第 4 项）。
    - D11（实例内迁移执行器）、D26（内部签名 Token）、D09（每服务独立进程）的规范正文修订。
    - `agent-docs` 现行正文替换为 V2 草案，作为后续需求。
    - `LOCAL_DEVELOPMENT_ENVIRONMENT.md` 第 11 节 V2 消息命名约定的正文修订。
  - **配置待改：**
    - ~~billing 的 `messaging.publisher.unroutable-policy` 仍为 `ACK_AND_WARN`~~：2026-10-19 计费事实重构已改回 `FAIL`（代码已改、未部署）。
    - `local-environment/compose.yaml` 的 `mysql.environment` 未透传 V2 库密码，全新卷初始化会因必需变量缺失而失败。
  - **mor-test V2 部署：** 2026-10-18 已部署并保留全部数据，见[需求交付记录](<../requirements/20261018/test-environment-V2部署与数据迁移/delivery.md>)与[阶段 2 报告](<../requirements/20261018/test-environment-V2部署与数据迁移/tasks/02-report.md>)。
    - 镜像：main@e5467400 构建的 18 个 Java 服务、2 个 Web 的 `mor-test/*:development` 与 Vault 镜像；29 个容器 healthy。
    - 迁移：评审 SQL 10/20/21/30/31/32/33/50 执行一次并重放验证；40 号核对 150/150；`mor_cct`、`mor_commerce`、`mor_risk` 4,210 行 = V2 六库 4,110 + 归档 100。
    - 部署项：Platform Target 指向 `acquiring-service`；Vault 新增 7 条调用方规则；XXL `acquiring-service` 补偿扫描已启动、commerce-service 任务已停；RabbitMQ 已按插件挂载重建。
    - 验收（V2 脚本 `E2E_TARGET=test`）：入驻 41/41、场景 85/85、工作区 53/53、接入自助 78/78、团队 123/123、Client API 90/90、`--outage` 92/92、Vault 烟测通过、迁移数据抽样 8/8。
    - 未完成：`smoke.py` 与 `risk_rule_configuration_smoke.py` 被 `t_gateway_target` 目录门禁挡住，迁移前已如此。consumer Target 未新增，因为没有 Customer 路由。
    - 补偿执行器（2026-10-18 任务卡 03）：有补偿事件目录的 10 个服务全部在 XXL 登记执行器并启动 `compensationEventScan`（每 30 秒、分片广播）；card 另登记 `cardOrphanInstrumentPurge`（每小时）。只改 compose 环境变量，不改代码与镜像。compliance 停在 ERROR 的 `CLIENT_REVIEW_RESULT` 在任务启动后 32 秒内重试成功，对应申请变为 RETURNED；入驻验收 29/29 与 41/41。account 的 `accountBalanceReconciliation` 未登记。见[任务卡 03 报告](<../requirements/20261018/test-environment-V2部署与数据迁移/tasks/03-report.md>)。
    - 保留至阶段 3：旧容器、旧镜像、旧库与旧账号、`mor_v2_migration_archive`、Vault 旧调用方规则、XXL commerce-service 分组、RabbitMQ 卷内旧节点目录。
  - **其他遗留：**
    - test-environment 中 2026-09-07 Risk Operations rollout 工具的钉值需按当前 Platform 网关目录重定（3 failures、37 errors）。
    - acquiring 的 `CheckoutSessionStatus.PREPARING` 读容忍与 Context Path `/commerce` 是否改名。
    - `t_consumer_acceptance.command_idempotency_id` 是否删除。
    - Gateway 边界脚本的 `.subscribe()` 与 90% 分支覆盖率门禁；Platform、acquiring 的 `coverage-gate` 仍低于 0.90。
- **本机一次性操作汇总（不在仓库中）：**
  - **已执行：**
    - 01：在线启用 `mor-local-rabbitmq-1` 的 `rabbitmq_consistent_hash_exchange`，未重启。
    - 02a：在 `mor_platform` 建 `t_currency_spec` 并写入 USD 行。
    - 03–12：按上表新建各 V2 测试库与账号（仓库默认密码），`.env.local` 未改。
    - 05：`mor_payment` 的 13 张旧表 `RENAME` 到 `mor_payment_legacy_20261017`；临时库 `mor_payment_step05_tmp` 已删除。
    - 09：`.env.local` 追加 3 个 CDC / Doris 键；创建 `cdc_reader`；`mor_finance.t_currency_spec` USD 行 `update_time` 被演示更新；Doris / Flink 镜像约 14 GB 保留。
    - 11b：清理 `mor_account` 残留数据。
    - 12x：清理 `mor_card` 消费登记残留各 3 行。
    - 宿主磁盘写满期间 Redis AOF 出现 `MISCONF`、Docker 停止响应，已由他人恢复。
  - **安全提示：** 第 05 步会话输出中曾显示本机 MySQL root 密码，第 04 步曾显示本地 Redis 测试密码；均未写入仓库，建议轮换。
  - **待用户执行：**
    - 撤销 account_app 对 mor_ledger 的只读授权（见 Pending）。
    - 确认无用后 `DROP DATABASE mor_payment_legacy_20261017`，并删除 `mor_payment` 中残留的卡 4 表。
    - main 停用后删除 `mor_cct`、`mor_commerce`、`mor_risk`、`mor_finance` 及对应账号。
    - 按需清理 `mor_risk` 中来源不明的 `t_decision_flow` 残留行。
    - 删除 `/mor-local` 上的旧 RabbitMQ 拓扑。先确认旧进程已停，并按需导出队列消息（另一会话的旧 CCT 进程曾消费 `cg_cct_client_review`）：

      ```bash
      for q in cg_commerce_payment_result cg_commerce_payment_result_dlq cg_acquiring_payment_result cg_acquiring_payment_result_dlq \
               cg_cct_client_review cg_cct_client_review_dlq cg_client_master_client_review cg_client_master_client_review_dlq; do
        docker exec mor-local-rabbitmq-1 rabbitmqctl delete_queue --vhost /mor-local "$q"
      done
      # 交换机（rabbitmqctl 无删除交换机子命令，用容器内的 rabbitmqadmin 或管理台）：
      # dlx_commerce_payment_result dlx_acquiring_payment_result dlx_cct_client_review dlx_client_master_client_review
      # topic_payment_result topic_risk_client_review
      ```

<a id="commercial-ops-console-second-slice-20261020"></a>
## commercial 运营控制台完善（第二切片，2026-10-20，已部署 mor-test，代码随 `commercial/second-slice` 快进合入 main `19ccd78a`，未推送）

- **需求与设计：** [requirements/20261020/commercial-运营控制台完善](../requirements/20261020/commercial-运营控制台完善/requirement.md)（F1 合同列表 + Client 选择器、F2 审批工作台、F3 价目总览与版本对比、F4 产品目录、F5 变更历史、F6 费率预览、F7 价格试算、F8 导航 / 仪表盘、F9 Owner 错误 detail 透传；technique.md §2 接口合同、§2.1 F9 决定）；设计稿 `operations-console-web/docs/operations-console.pen` 画板「商务 · 01 工作台」—「09 价格试算」。阶段 1–8 完成，过程见 [delivery.md](../requirements/20261020/commercial-运营控制台完善/delivery.md)。
- **commercial（H 线 `285c15d4`）：** 只读 `AgreementQueryApi` `GET /agreements/v1`、`CommercialTermQueryApi` `GET /commercial-terms/v1/plans|versions`、`ProductCatalogApi` `GET /products/v1[/capabilities|/fee-events]`、`BusinessChangeQueryApi` `GET /business-changes/v1`；公共分页 page 从 1、size ∈ {10,20,50,100,200}；无 DDL / DML。
- **Foundation（F9 线 `c5f2c3f2`→rebase 后 `beb21618`）：** `paytech-http-client-support` 的 `HttpProblemDecoder.DecodedProblem` 增 `detail`；`HttpServiceClientOptions.withOwnerProblemDetail()` 默认关闭；开启且严格 Problem 时 `BUSINESS_CHECK_FAILED` / `CONCURRENT_REQUEST` / `NOT_FOUND` / 已登记合同码用远端 detail（空白、>512 码点、含 ISO 控制字符回退），`INVALID_REQUEST`、字段校验、5xx 不变。只在 operations-console-bff 的 `CommercialClientConfiguration` 两个商务工厂开启；I1 的 BFF 包装器 `CommercialOwnerProblemDetail` 已删除（违反 RFC 9457 设计 §6.3）。
- **operations-console-bff（I1 `95c9834b`、I2 `41e3cf1c`/`4d8f4ec3`、D1 `19ccd78a`）：** `/commercial-admin/v1` 新增 11 端点：dashboard（5 计数卡 + 待审批队列）、rate-cards、commercial-prices/simulate、agreements、commercial-terms/plans、commercial-terms/versions、`commercial-terms/versions/{base}/diff/{target}`（按 OE + 币种 + 画像配对，字段级差异，跨方案 400）、products / products/capabilities / products/fee-events、business-changes（补 `changedFields` / `previousData`，回溯上限 5×200 条，超出标 NOT_COMPARED；版本快照未记录的 priceCount / maker / checker 不算变化）；`CommercialPriceErrorCode` 登记与状态映射。
- **operations-console-web：** 商务菜单 8 项（工作台 / 合同 / 价目 / 费率预览 / 价格试算 / 定价画像 / 产品目录 / 变更历史）；新增工作台、合同列表、价目总览（方案 / 版本页签）、版本对比、产品目录、变更历史、费率预览、价格试算页；表单下拉改目录接口；Client 选择器 = ID 手输 + 本机最近使用 + 审核通过 Client；`ApiError.detail` 展示 Owner 原因。
- **operations-console-web 商务两项（2026-10-20，已本地提交、未推送）：** [产品目录层级展示](../requirements/20261020/commercial-产品目录层级展示/delivery.md)（产品主表 + 可开通项 / 计费事件上下两组可折叠分组、事件按条款类型合并、条款类型筛选与目录缺口提示）与[价格组件表格维护](../requirements/20261020/commercial-价格组件表格维护/delivery.md)（组件可编辑表格、费率按百分比输入、组件币种自动推导、价格卡片折叠）。mor-test ops-web `sha256:5853565b…`（tag `commercial-20261020c`，入口 `index-RX7TQcU9.js`，源 = main 5f12cf3e + opsux 已上线 34 文件 + 两需求 9 文件，不含对账会话改动；回滚 `105afba9…`）；验收 14/14、17/17，页面回归 48/48。已知目录缺口：PARTNER_BUY_RATE 无计费事件登记、DATA_ANALYTICS 有开通项无计费事件。
- **Platform 注册：** `platform-support-initial-data.sql` 末尾 I1 续用块 95（37 行：资源 5、路由 3、头 8、参数 3、限流 18，并交换 95303 / 95304 菜单排序）与 I2 块 93（103 行：路由 8、头 16、参数 31、限流 48）；无新权限 / 授权；`console-resource-capabilities.json` 加 5 个商务组件键（Platform 镜像本次未发，见遗留）。ID 块登记：91 科目分级、92 channel-connector 运营台、93 / 95 商务、100–102 财务预定。
- **验证（rebase 后 `886edda7` / `19ccd78a`）：** commercial 168、arch-rules 61、http-client-support 130、ops-bff 1633、ops-web 1126 + 构建、platform-support 878、`generate-oe-client-schemas.mjs --check` 12/12；本机冒烟 [stage5-smoke.md](../requirements/20261020/commercial-运营控制台完善/verification/stage5-smoke.md)（1 个 Web 缺陷 D1 对比页路由已修）；mor-test 验收 101/102（唯一失败为脚本把重放语义当过期版本，复测 409）、页面回归 46/46（[stage7-acceptance.md](../requirements/20261020/commercial-运营控制台完善/verification/stage7-acceptance.md)）。
- **mor-test（2026-10-20 05:34–05:53Z，[release.md §6](../requirements/20261020/commercial-运营控制台完善/release/release.md)）：** `mor_platform` test-dml 5/3/8/3/18/8/16/31/48 + test-activate 3/8/5/1（PLATFORM_ADMIN 33→34）；镜像 commercial `sha256:59bb000d…`、ops-bff `sha256:e294a4ae…`、ops-web `sha256:f00801f5…`（tag `commercial-20261020b`，钉 sha256 见 release/compose-candidate.yaml）；`mor_commercial` 无 SQL，合成数据已清理；备份 `~/Code-MoR-backups/20261020T053437Z-commercial-ops2-test/`。
- **已知缺口 / 遗留：** Platform 镜像未发，`console-resource-capabilities.json` 商务键待 Platform 发版（菜单资源管理页对商务资源改状态可能报未知组件）；`compose.applications.yaml` commercial 段仍未回写（沿用首切片待办）；commercial 驳回命令无原因字段、版本 / 方案列表无 `agreementNo`、BCR 不能按 recordType 读、状态枚举未发布到 api、`fee-events?termType=非法值` 返回 200 空、commercial 日志无 requestId；client-master 无运营可用的 Client 搜索 / 摘要与 OE 列表接口；推送 / PR 未做。

<a id="billing-fact-model-channel-cost-20261019"></a>
## commercial 六层产品定价模型（2026-10-19—20，已部署 mor-test，本地提交 main `08c8f794` / `691b695c` / 三线合入 `0ecc5713` / E 线 `cf39a1ee`，未推送）

- **决定与设计：** [决定记录](./decisions/2026-10-19-commercial-six-layer-product-pricing.md)（Confirmed）、[design-discuss/commercial](../design-discuss/commercial/design.md)（design.md 1–13 节、database.md 1–8 节、commercial.pen 七张画板）；需求目录 [requirements/20261019/commercial-六层产品定价模型](../requirements/20261019/commercial-六层产品定价模型/requirement.md)，阶段 1–7 完成（阶段 4–7 由用户 2026-10-19 夜间委托主会话自动推进，护栏见 delivery.md）。
- **模型：** `mor_commercial` 14 → 20 表（产品目录 `t_product` / `t_product_capability` / `t_product_fee_event`，合同订阅 / 开通 / 约束 `t_agreement_product` / `t_agreement_capability` / `t_agreement_capability_limit`）；画像表改名 `t_pricing_profile` / `t_pricing_profile_cond`（`profile_kind`）；`t_agreement` + `business_mode` / `fund_mode` / `pricing_authority` / `referral_partner_id`；`t_commercial_term_price` + `currency`（唯一键含币种）；Version + `pricing_model` / `fee_tax_treatment` / PENDING_APPROVAL；Component + `refund_behavior` / `band_basis`。模型 203 字段 28 唯一键 25 索引 21 关系 4 图组；`t_agreement` / Price 表注释精简到 MySQL 2048 字符内；目录初始化 DML 7 / 11 / 21。
- **代码：** 17 个 api 枚举与 `CommercialCodes`；17 张表全行 DataObject / Mapper / Repository；消息 / 补偿事件 / Redis 锁 / XXL 接入（Enforcer 窄放行）；13 条合同 / 订阅 / 开通 / 画像写入 API（`/agreements/v1`、`/pricing-profiles/v1`）、8 条条款 API（`/commercial-terms/v1`）、版本价格查询、价格命中两接口（`/commercial-prices/v1`）、费率卡（`/rate-cards/v1`）；Checkout 解析响应补业务模式 / 资金模式 / 订阅 / 开通项 / D02 四项派生，按时点选版本；BCR 统一 `BusinessChangeHistoryWriter`（RFC 8785 快照）；两事件 `AgreementStatusChanged` / `CommercialTermVersionActivated` 登记事件目录；XXL `commercialTermVersionActivation`。Operations：BFF `/commercial-admin/v1/**` 24 端点、Web 商务三页、Platform 注册块 95（319 行）。
- **验证：** commercial 149、messaging 73、arch-rules 61、BFF 1557、Web 1104 + 构建、platform-support 878；本机冒烟 9/9（[stage5-smoke.md](../requirements/20261019/commercial-六层产品定价模型/verification/stage5-smoke.md)）；mor-test 验收接口 46/47（一处脚本缺参，复测 200）、页面回归 37/37（[stage7-acceptance.md](../requirements/20261019/commercial-六层产品定价模型/verification/stage7-acceptance.md)）。
- **mor-test（2026-10-20）：** `mor_commercial` 增量 DDL + 目录 DML（20 表，7 / 11 / 21）；`mor_platform` 块 95（5 / 5 / 8 / 24 / 128 / 5 / 144）+ test-activate（24 / 8 / 5 / 1，PLATFORM_ADMIN 31→32）；镜像 commercial `88dbf741…`、ops-bff `2bd2afee…`、ops-web `edf140b9…`（tag commercial-20261020，钉 sha256，见 release/compose-candidate.yaml）；XXL 执行器 commercial-service、任务 commercialTermVersionActivation。备份 `~/Code-MoR-backups/20261019T213228Z-commercial-test/`。
- **已知缺口 / 遗留：** `test-environment/compose.applications.yaml` 的 commercial 环境变量与 depends_on 只在候选覆盖文件（主会话被权限分类器拒绝改共享 compose，待用户合入）；mor-test 11 处表 / 列注释仍为旧文本（仅注释，待单独授权）；risk-decision 无 Client / 候选级资格合同，Checkout 解析门禁保留 503 占位；acquiring 未消费新字段 / 未冻结；`CCT_CONTEXT_*` 错误码改名推迟；BCR 共享白名单拆分；billing 事实类型 PAYOUT_FEE / PLATFORM_FEE 待 billing 定；商务无列表接口（第二切片已补只读列表）；Owner 错误 detail 被公共 HTTP 客户端丢弃（第二切片 F9 已以 Foundation 显式开关解决）；PUT 价格集合 64 KiB 上限；`messaging.publisher.unroutable-policy` 暂 ACK_AND_WARN；本机 `mor_commercial_line{b,c,d}` 隔离库待清理。

## billing 计费事实模型重构与渠道成本迁出（2026-10-19，已部署 mor-test，已本地提交 `8b7b4e7a`，未推送）

> 本地提交 `8b7b4e7a`（2026-10-19，未推送）。

- **范围：** 按[决定](decisions/2026-10-19-billing-fact-engine.md)、[需求](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/requirement.md>)与[技术方案](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/technique.md>)第 2 节实施需求 A。各线报告见 [tasks/](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/tasks/>)（T0、A、B、C、D）。
- **Implemented：**
  - **billing 模型：** `mor_billing` 目标 9 → 11 张：`t_billing_fact`、`t_billing_fact_ref`、`t_billing_fact_pricing`、`t_billing_fact_line`（20 列，含 `target_bucket`）、`t_billing_fact_line_link`、`t_business_calendar`、`t_business_calendar_day`、`t_consumed_event`、`t_consumed_aggregate_version`，加原有 `t_currency_spec`、`t_compensation_event`。经济事实三表与渠道成本四表删除。`billing.pdma.json` 脱离 Finance 生成器直接维护，由 `scripts/pdmaner/billing-model.test.mjs` 校验。待执行 SQL 为 [db/billing](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/db/billing/>)。
  - **billing 接口：** `BillingFactCommandApi` 提交 `POST /billing/v1/facts`、批准 `POST /billing/v1/facts/{factId}/approve`；`BillingFactQueryApi` 按 ID、按账簿流分页、发布进度 `GET /billing/v1/facts/progress`（原 `/pending`）、新增按引用查 `GET /billing/v1/facts/by-ref`。人工登记只开放 `MANUAL_ADJUSTMENT` 与 `COST_ADJUSTMENT`。保留纯计算器 `ChannelCostCalculator`，暂无调用方。
  - **事件：** `EconomicFactRecorded` 改为 `BillingFactRecorded`（聚合 `BILLING_FACT`，载荷含 `refs` 与全部行，消费者 account），补偿类型 `BILLING_FACT_RECORDED`。`messaging.publisher.unroutable-policy` 已改回 `FAIL`，上一节「配置待改」中的该项关闭。
  - **channel-connector：** 渠道成本四表迁入，`mor_channel_connector` 目标 13 → 17 张，增量 SQL 见 [db/channel-connector](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/db/channel-connector/ddl.sql>)。`ChannelCostPlanApi` 的 Maker-Checker 端点迁到 `/channel-connector/v1/channel-cost-plans/**`，增加 MID 存在、Channel 与 entityNo 归属校验。新增命中接口 `POST /channel-connector/v1/channel-costs/resolutions`，只返回命中组件，不算金额。新增到期切换 Job `channelCostVersionActivation`。`channel-connector.pdma.json` 由 Payment Orchestration 生成器 channel-connector 分区产出。
  - **account / ledger：** account 改读 `BillingFactRecorded` 新载荷，映射为每行必有付方 / 收方。ledger 经 `BillingFactQueryApi` 读事实，过账日期取 `businessTime`，关账改调 `/progress`。`AmountSource`、`AutoInstructionType` 迁 `paytech-ledger-api`。两服务无表结构变化。
  - **规则与模型：** `table-ownership.yaml` 改为 billing 11 张、channel-connector 17 张，总计 265 + 56 − 7 step-removed + 20 step-added。Finance 生成器不再投影 billing，只输出 reconciliation、tax、payout 三份服务模型。
- **Verified（各线报告，2026-10-19）：**

  | 范围 | 结果 |
  |---|---|
  | billing-test | 71/71 |
  | channel-connector-test | 123/125，余下 2 例 R4 在表归属更新后定向重跑通过 |
  | account-test / ledger-test | 58/58、234/234 |
  | arch-rules | 61/61 |
  | `billing-model.test.mjs` | 7/7 |

  本机 `mor_billing`、`mor_channel_connector` 已由主会话按各自测试 `init/ddl.sql` 重建，供自动测试使用。本机链路验收 59/59 两轮（[stage5-report.md](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/verification/stage5-report.md>)）。
- **Deployed（mor-test，2026-10-19 19:37—19:44Z，记录见 [release.md 第 6 节](<../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/release/release.md>)）：**
  - **镜像（tag `billing-fact-20261019`，源码 HEAD `08c8f794` + 本需求 460 个文件）：** channel-connector `sha256:194961f11db549ab57fa8ccb9f94ee51d737dee46cb588b86e2a40905539eff3`、billing `sha256:eeec27f923b7f55f7f3f6b7a8b32528b063fe9a64197615b1e645d3f8cf6540e`、ledger `sha256:0e9390666b839739d7b418783d70682040d761d0e3e5634be86f89076d4de020`、account `sha256:ad0621b12a80731ed550729ff7709383a7dee28017bc736c3ea9e2152a58ce00`。Gateway、Platform、BFF、Web 与其余服务未动。
  - **SQL（root，19:37:14Z）：** `mor_channel_connector` 13 → 17 张；`mor_billing` 删 7 建 9 → 11 张，`t_business_calendar` 1 行 `DEFAULT / UTC / ACTIVE`。RabbitMQ 无改动。
  - **Compose：** 工作区 `test-environment/compose.applications.yaml` 的 channel-connector-service 段新增 Redis、Snowflake（Region 0、**Worker 26**）、补偿实例与 XXL 执行器变量，未提交。发布包原定 Worker 32 超出 Snowflake 允许的 0—31，首次启动失败后改为 26。
  - **XXL：** 分组 13 `channel-connector-service`（自动注册 `http://172.30.73.36:9999/`），任务 15 `channelCostVersionActivation`（CRON `0 */5 * * * ?`、FIRST、SERIAL_EXECUTION、超时 300、重试 0）已启用；手动与定时触发 200 / 200，`activated 0`。
  - **验收：** `E2E_TARGET=test` 接口验收 59/59；合成数据与渠道种子已清理，四库行数回到备份基线，差异只有授权 SQL 的结构变化。
  - **备份 / 回退：** `~/Code-MoR-backups/20261019T193439Z-billing-fact-test/`（四库 dump、`compose-rollback.yaml`、行数基线前后对比）；channel-connector 回退用运行 JAR 替身 `sha256:7eea94df1e63…`（`:rollback-20261019`）。
- **Pending：**
  - 阶段 8 提交（含 Compose 段与 Worker 26）。
  - 自动 PAYMENT 事实与 account 映射不兼容，需求 B 前先定，见 [design-discuss/billing/cases.md](../design-discuss/billing/cases.md) 待定点第 5 条。
  - 作废 APPROVED 渠道成本版本的入口登记为后续项；`config/nacos/channel-connector-service.yaml` 未补新配置键（mor-test 以 `test` Profile 启动，不加载 Nacos），启用 Nacos 时再补。
  - Payment 生成器与已提交 `payment.pdma.json`、`card.pdma.json` 存在既有漂移（提交 `cf4a429c` 的 name/describe、`a457754f` 的一条字段注释）；本需求在 PDManer 不可变表规则中新增两张成本表，两份模型需随漂移一并收敛后重生成。

<a id="finance-accounting-kernel-20261015"></a>

<a id="mor-test-compensation-scan-20261017"></a>
## BFF 遗留项收尾（2026-10-18，已本地提交，未推送、未部署）

- **Implemented：** 经用户同意处理前两次重构的遗留项。Operations BFF 新增 `RiskEvaluationExplanationProcessor`，把评估解释与规则集详情的组合及条件树组装从 `RiskEvaluationClientImpl` 移入 Processor，Client 只适配单个 Risk 接口；`OperationsConsoleBffApplicationAutoTest` 的 Risk 守卫收窄为只放行这一个聚合 Processor，Risk Service、Repository、Mapper 与数据库 Bean 仍禁止。Client Console BFF 的页面模型由 record 与 `*Models` 容器改为普通 class，一个类型一个文件，放在 `service/model/response` 与 `service/model/request`；`Document` 改名 `ApplicationDocument`。HTTP 合同与 JSON 字段顺序不变。
- **Verified：** JDK 25 `clean verify`：Operations BFF **1390/1390**，Client Console BFF **81/81**（含按字段顺序断言的用例）。

## Client Console BFF 接口与 Processor 按规范重组（2026-10-18，已本地提交，未推送、未部署）

- **Implemented：** `client-console-bff` 原 5 个 Processor 拆分重组。会话、权限两个共用组件移出 Processor：可信身份由入站 `ClientActorResolver` 解析并以 `ClientActor` 参数注入 Controller，工作区 PLT-I02 权限由 `ClientPermissionGuard` 判断，Refresh Cookie 读写回到 Controller 侧。真实的多 Owner 聚合按入口拆成 5 个继承 Foundation `AbstractProcessor` 的 Processor（入驻开始页、表单目录、申请详情、工作区首页、邀请详情）。原散落在 Processor 内的单 Owner 回执校验移入 `integration` 下 7 个 Port（Platform 会话与工作区、授权、邀请、文件，CCT 摘要与入驻读取，Compliance 材料与审核结果）。新增 `ClientContextType`、`SectionAvailability`、`WorkspaceRestrictionReason` 枚举替代魔法字符串，`FormOptionsResponseBody` 从 `ext/rest/response` 移到 `service/model`。HTTP 路径、错误码、状态码、校验顺序、降级规则与响应字段顺序不变。
- **Verified：** JDK 25 `./mvnw -B -o clean verify -pl client-console-bff/paytech-client-console-bff-test -am`，改动前后均为 **81/81**。
- **后续：** 页面模型已于同日改为普通 class（见上方「BFF 遗留项收尾」）。纯转发接口继续由 Controller 直接调用 Owner `@HttpExchange` 接口。邀请详情的 CCT 摘要回执含 null 元素时，原实现返回 500，现与其他无效回执一样只隐藏工作区名称。

## Operations 财务基础资料、账本与余额查询（2026-10-19，已本地提交 `7acbcf4f` / `b2e9156b`，未推送；mor-test 已部署）

- **Implemented（已本地提交 `7acbcf4f`，画板 `b2e9156b`，未推送）：** 按[需求](../requirements/20261018/operations-console-财务基础资料与账本余额查询/requirement.md)与[技术方案](../requirements/20261018/operations-console-财务基础资料与账本余额查询/technique.md)，Operations 新增一级菜单「财务」：账务基础资料（财务主体 / 财务日历与期间 / 账簿与账户 / 只读科目表与币种，四标签页）、账本查询（账簿 → 账户 → 流水 → 分录详情下钻）、余额查询（Client / Partner 按账簿与币种）。ledger 新增 `LedgerSetupQueryApi`（6 个只读端点）、`LedgerSetupErrorCode`（15 Code）、六个命令端点要求 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 并记操作人 INFO 日志；account 新增 `BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND`；Operations BFF 18 个 `/finance/**` 转发（固定 base-url 直连 ledger / account，`/finance/**` 严格 JSON 边界，两个错误码代理工厂）；Platform 初始化 SQL 追加块 97 / 98（226 行：权限 2、授权 2、Console 5、路由 18、Header 60、Query 31、限流 108；R08 / R10 / R12 登录人速率 30 / 分钟，其余 10），`console-resource-capabilities.json` +3。设计画板 `operations-console-web/docs/operations-console.pen` 财务 01—12。
- **Verified：** 发布源码 JDK 25 `clean verify`：ledger-test **221/221**、account-test **40/40**、Operations BFF test **1473/1473**；Web **1082/1082** 与生产构建。本机 V2 全链路（`mor_platform_v2e2e` 注册 + 激活）接口 67/67、浏览器 48/48，见[交付记录](../requirements/20261018/operations-console-财务基础资料与账本余额查询/delivery.md)。
- **mor-test（2026-10-19）：** 备份后 `mor_platform` 执行注册块 226 行并激活（PLATFORM_ADMIN v30）；ledger `sha256:0c0b6d4e…`、account `sha256:d341485d…`、Platform `sha256:7c8af660…`、Operations BFF `sha256:ea20c4a3…`、Web `sha256:a16abbcd…`（tag `finance-ops-20261019`，`26a6a217` + 本需求差异构建；BFF 顺带包含 `a6dbbeae` / `26a6a217` 的 Processor 重构，五个镜像含 Foundation messaging `339db7b8` / `8d123532`）；Compose 为 BFF 增 `OPERATIONS_LEDGER_BASE_URL` / `OPERATIONS_ACCOUNT_BASE_URL`。经 `http://localhost:28080` 接口 67/67、浏览器 48/48，合成账务数据已清理回基线；全页面只读回归首轮 31/33（`auth/v1/context` / `refresh` 各 1 次 504，Platform `TransactionTimedOutException` 与同秒 XXL 注册超时，未动的服务 24 小时内同类超时 38—40 条，判定为环境偶发停顿），重跑 **33/33**，见[发布记录](../requirements/20261018/operations-console-财务基础资料与账本余额查询/release/release.md)。Gateway、Nginx、Vault 未动。
- **Pending：** 科目表 / 币种维护、账簿 / 账户停用、历史 `console-resource-capabilities.json` 缺口、BFF 全局错误 detail 策略留后续批次；未推送；test 环境偶发超时与 `xxl-job-admin` 内存接近上限（500 / 512 MiB）为环境观察，待环境任务处理。

## Operations BFF 聚合 Processor 迁到 Foundation 模板（2026-10-18，已本地提交，未推送；2026-10-19 随财务控制台发布进入 mor-test，见上一节）

- **Implemented：** `operations-console-bff` 的聚合 Processor 移入 `service/processor`，每个入口一个并继承 Foundation `AbstractProcessor`；内部请求位于 `service/processor/request`，响应直接复用 BFF 页面 ResponseBody。`RoleDetailsProcessor` 拆出 `RolePermissionOptionsProcessor`，`AuditDetailsProcessor` 改名为 `AuditEventDetailsProcessor`，`ClientReviewFileProcessor` 改名为 `ClientReviewFileDownloadProcessor`。`ApiScopeDetailsProcessor` 的引用类型与分页校验移入 `validate`，引用类型改用 BFF 枚举 `ApiScopeReferenceKind`。HTTP 路径、错误码、状态码、上游调用顺序与降级规则不变。
- **Verified：** JDK 25 `./mvnw -B -o clean verify -pl operations-console-bff/paytech-operations-console-bff-test -am`，BFF **1390/1390** 通过，构建成功。
- **后续：** Risk 评估解释的聚合经用户同意于同日迁入 Processor（见上方「BFF 遗留项收尾」）。`client-console-bff` 另见上方小节。

## mor-test Platform 补偿扫描（2026-10-17，已部署 mor-test）

- **范围：** mor-test 的 XXL-Job Admin 常驻（根路径，`compose.sh` 默认加载 scheduler，由 Client API 测试联通会话启动并提交）；默认管理员密码已改，取自 `.env.test` 的 `MOR_XXL_JOB_ADMIN_PASSWORD`。Platform 执行器为 `platform-support-service@172.30.73.21:9999`；任务 `compensationEventScan` 为 BEAN、每 30 秒、分片广播、单机串行、失败不重试，Commerce 同样设置。不改代码与镜像（Platform `629cccf9…`）。需求见 [requirements/20261017/platform-support-mor-test补偿扫描](../requirements/20261017/platform-support-mor-test补偿扫描/requirement.md)。
- **验证：** 临时"先 503 后 204"接收端与临时 http 放行下，Webhook 投递首次失败进入 RETRY_WAIT，由扫描自动重发并送达，**9/9**；临时项已撤除，http 地址恢复为 400。
- **已知：** Platform 或 Commerce 每次重启约有 1 分钟扫描调度失败（执行器注销，Admin 每 30 秒刷新注册地址），由下一周期补上。

<a id="operations-webhook-management-20261016"></a>
## Operations Webhook 管理（2026-10-16，已提交并已推送；2026-10-17 已部署 mor-test）

- **范围：** 运营后台「平台配置 / Webhook 管理」：按 Client ID 或名称选择 Client（Platform 新增 `GET /webhook-admin/v1/clients`）、端点新建 / 修改 / 启停、签名密钥一次性展示与轮换、投递记录与详情、Replay；Operations BFF 转发 10 个接口；注册 DML 128 行（`9000000000000903xx—0907xx`）。需求见 [requirements/20261016/operations-console-Webhook管理](../requirements/20261016/operations-console-Webhook管理/requirement.md)。
- **验证：** Platform Webhook 6 个测试类全部通过（全量 671 例中 3 例错误为其他会话未提交的通知改动）；BFF 1378/1378；Web 1051—1060 通过；本机全链路接口 28/28、浏览器 22/22。阶段 5 修正测试事件审计未落库（Webhook 投递遗留）。
- **mor-test（2026-10-17）：** `20677f2` + 本需求差异构建 Platform `efeb1d9e…`、BFF `6341dd18…`、Web `da6154c7…`；注册与启用 SQL 已执行；接口 22/22、浏览器 13/13、30 页回归通过；未发送测试事件与业务事件。见[发布记录](../requirements/20261016/operations-console-Webhook管理/release/release.md)。
- **后续：** 地址被拒的就地提示由 [Webhook 端点地址错误码](../requirements/20261017/platform-support-Webhook地址错误码/requirement.md) 处理：Platform 新增 `WebhookEndpointErrorCode`（3 个），BFF 登记、Web 显示在地址字段下；本机 15/15，2026-10-17 已部署 mor-test（BFF `aec81706…`、Platform `0cdfe0e9…`、Web `04f9f7d1…`，验收 13/13），已本地提交（未推送）；只读权限 2026-10-17 本机补验 12/12。mor-test 补偿扫描已于 2026-10-17 生效（见上节）。

<a id="platform-webhook-delivery-20261015"></a>
## Platform Client Webhook 投递（2026-10-15，已本地提交 `ee469e0`，未推送；2026-10-16 已部署 mor-test）

- **范围：** 内部受理 `POST /platform-support/webhooks/v1/events`（过渡信任 `X-Caller-Service` + `webhook.http.callers`）、补偿 Handler 签名投递（SSRF 防护、等比退避、上限后 DEAD）、Operations 管理 `/webhook-admin/v1` 9 个接口（端点、订阅、密钥轮换 1 小时双签宽限、测试事件、投递查询与 Replay）。PDManer 8 表收敛为 5 表；决定见 [2026-10-15 Platform Webhook 投递决定](./decisions/2026-10-15-platform-webhook-delivery.md)，需求与方案见 [requirements/20261015/platform-support-Webhook投递](../requirements/20261015/platform-support-Webhook投递/requirement.md)。
- **数据库：** 本机 `mor_platform` 执行 `docs/database/sql/changes/2026-10-15-webhook-delivery.sql`（5 表、56 字段）；mor-test `mor_platform` 执行建表、权限注册块与 test-activate（运营授权 ACTIVE）。其他环境未执行。
- **mor-test 部署（2026-10-16）：** Platform 镜像 `sha256:63ae4cde…`（`webhook-20261016`），`WEBHOOK_ENABLED=true`、调用方 `commerce-service` 八类事件；Vault 新增 `WEBHOOK_SIGNING_SECRET_V1` 与 PROTECT/REVEAL 规则（策略 12→15）。临时接收端验收 24/24 后撤除放行与接收容器、清理验收数据。见[发布记录](../requirements/20261015/platform-support-Webhook投递/release/release.md)。同日 Platform 已由 Client Console 会话替换为 `sha256:86819a6e…`（`9ae668a`，含 `ee469e0`，Webhook 配置保留）。mor-test 未配置 XXL-Job 补偿扫描，RETRY_WAIT 投递不会自动重试。
- **验证：** JDK 25 本机 `./mvnw -o verify`，Platform 测试 **647/647** 通过，其中 Webhook 4 个入口测试类 28 例；Webhook 相关 119 个类指令 91.91%、行 90.78%、分支 67.63%。全服务聚合 90% 覆盖率门禁仍为既有未达标（本次 Phase A 指令 58%、分支 39%）。PDManer 模型/Schema/场景测试 52/54，2 项为改动前既有失败。
- **未完成：** 除 mor-test 外 Vault 未登记 `WEBHOOK_SIGNING_SECRET_V1` 与 `WEBHOOK_SIGNING` 规则；Commerce 生产端（冻结快照并调用受理接口）、Operations BFF/页面与 Gateway 路由注册、Readiness 的 Webhook 条件均未实现；未推送，未部署 mor-test 以外环境。

## Finance Accounting 账务内核第一批（2026-10-15）

- **Implemented（已本地提交 `e900c34`，未推送、未部署）：** 按[需求](../requirements/20261015/finance-账务内核/requirement.md)与[技术方案](../requirements/20261015/finance-账务内核/technique.md)，`paytech-finance-accounting` 实现 16 张表的 DataObject/Mapper/Repository、会计基础资料（财务主体、OE/FE 单币种账簿与序号行、按身份矩阵校验的统一账户、财务日历、FUTURE 到 OPEN 的期间）、`JournalPostingService` 单账簿过账内核（期间共享锁、账簿序号行排他锁、账户主键升序排他锁，余额版本 CAS），以及人工会计调整 Maker-Checker（RFC 8785 内容摘要 `CODE-MOR:FINANCE:MANUAL_ADJUSTMENT:1`，批准在一个本地事务内生成 MANUAL 指令并过账为 ADJUSTMENT Journal）。复核与期间创建使用 Foundation `AbstractLockProcessor`，Finance 因此新增 Redis、Snowflake 与 RFC 8785 依赖。Client/Partner 余额查询改为 `entityNo` 参数并读取新 Schema（不兼容变更，当时无 Gateway/BFF 消费者）；旧 `finance-balance-query-schema.sql` 已删除，新增 `finance-initial-data.sql`（USD 与 STANDARD 科目表 29 个核心科目）。
- **本地数据库：** 用户授权后仅在 `mor-local-mysql-1`（`127.0.0.1:13306/mor_finance`）删除旧余额切片 3 张空表，执行完整 `finance-schema.sql`（84 表）与 `finance-initial-data.sql`；未操作 mor-test 或生产库。
- **Verified：** finance-service JDK 25 `clean verify` **80/80**，0 失败/错误/跳过；测试复用正式启动类、`mock` Profile、本地 MySQL/Redis，覆盖并发过账序号与余额、同一调整只过账一次、锁内门禁失败整体回滚、Maker-Checker、重放与 Redis 锁冲突。JaCoCo（命令行执行，未加入构建门禁）：accounting/api/web 合计行 96.7%、分支 80.3%。`-exec.jar` 本地真实 HTTP 冒烟 13 步通过（建账、调整、审批过账、详情、余额与两个拒绝场景），冒烟数据已清理。`npm --prefix requirements-site test` 127/137，10 项失败均为本次未改动的既有文档断言。
- **Pending：** 接口仅为 Finance 内部开发接口：Maker/Checker 由请求传入、OE 不向 CCT 核对；基础资料、账本与余额查询自 2026-10-19 起经 Operations 控制台接入 Gateway 与统一身份（见「Operations 财务基础资料、账本与余额查询」），其余仍未接；Redis 不可用的 503 路径未自动化；未实现交易经济、AUTO 记账、成熟、对账、出款、税务、关账/重开、账簿/账户停用与账本流水查询；本地 `mor_finance` 视为测试专用库（测试每例清空 16 张账务表的测试写入）。

<a id="finance-channel-cost-20261017"></a>

## Finance 渠道成本配置（2026-10-17）

> **2026-10-19：** 本节的四表、`ChannelCostPlanApi` 与审批逻辑已迁到 channel-connector（路径 `/channel-connector/v1/channel-cost-plans`），并补上到期切换 Job；billing 只保留纯计算器。现状见[计费事实重构](#billing-fact-model-channel-cost-20261019)。

- **Implemented（未提交、未部署）：** 按[需求](../requirements/20261017/finance-渠道成本配置/requirement.md)与[技术方案](../requirements/20261017/finance-渠道成本配置/technique.md)，新增 `ChannelCostPlanApi`（`/channel-cost-plans/v1`）：创建方案（每个 Channel 一个，DRAFT）、以完整适用范围与成本组成一次创建版本并冻结 RFC 8785 `CODE-MOR:FINANCE:CHANNEL_COST:1` 摘要、提交、批准（Checker ≠ Maker 且确认同一摘要；到期即切换当前版本、方案 ACTIVE、原版本 INACTIVE 并写失效时间，未到期 APPROVED）、驳回与查询。领域服务 `ChannelCostEstimationService` 按发生时间命中 ACTIVE/INACTIVE 版本、六维精确命中适用范围，逐组成计算“费率 × 基数 + 固定费”、受上下限约束、按币种精度 HALF_UP，返回与 `t_transaction_economic_component` CHANNEL_COST 证据列一一对应的结果；无方案、无覆盖版本或无命中范围时拒绝，不默认零成本。本期不换汇（固定费币种须等于范围币种）。四张表已存在，无 SQL 变更。生产代码与测试由三个并行 agent 实现、主会话合并核对。
- **Verified：** finance-service JDK 25 `clean verify` **242/242**（新增 49 个）；JaCoCo 命令行合计行 96.6%、分支 83.1%，`ChannelCostEstimationServiceImpl` 行 93.2%、`ChannelCostPlanServiceImpl` 93.9%。本地 `-exec.jar` 真实 HTTP 冒烟 20/20，数据已清理。
- **Pending：** 估算服务尚无生产调用方（待交易经济计算批次）；未到期 APPROVED 版本的到期自动切换、方案停用未实现；渠道清算账户维护依赖 Payment MID 查询接口（[依赖清单](../requirements/20261016/finance-交易自动记账/dependencies.md) X6）；内部开发接口，未接 Gateway 与授权，Maker/Checker 由请求传入。

<a id="finance-period-reopen-20261017"></a>

## Finance 期间重开与年末结转（2026-10-17）

- **Implemented（未提交、未部署）：** 按[需求](../requirements/20261017/finance-期间重开与年末结转/requirement.md)与[技术方案](../requirements/20261017/finance-期间重开与年末结转/technique.md)，`PeriodCloseApi` 新增 `POST /accounting-periods/v1/{periodId}/reopens`：只能重开最近一个已关闭期间，引用该期间最近完成的关账，锁定水位、把原结转指令逐行借贷互换规划为冲回、冻结 RFC 8785 `CODE-MOR:FINANCE:PERIOD_REOPEN:1` 摘要，期间 CLOSED → REOPENING；既有审批/驳回按流程类型分派，批准时同一事务过账 REOPENING 冲回、期间回到 OPEN 且 `reopen_count+1`，驳回回到 CLOSED 且不留分录；关账详情新增原关账编号、重开原因与申请编号。年末结转：期间结束日次日为财年起始月 1 日时，结转指令在收入/费用行之后追加本期损益 → 留存收益行（金额为截至锁定水位的本期损益累计加本次影响），重开时一并冲回。本地 SQL：`t_period_close` 唯一键 `uk_period_reopen_target` 改为普通索引、`t_close_step.step_type` 注释追加两个重开步骤（已在本地 `mor_finance` 执行，见[交付记录](../requirements/20261017/finance-期间重开与年末结转/delivery.md)）。生产代码与测试由三个并行 agent 实现、主会话合并核对。
- **Verified：** finance-service JDK 25 `clean verify` **193/193**（新增 21 个）；JaCoCo 命令行合计行 96.6%、分支 82.4%，`StartPeriodReopenProcessor` 行 98.7%。本地 `-exec.jar` 真实 HTTP 冒烟 18/18（含年末留存收益、驳回后再次发起、重开后补记并重新关账），数据已清理。
- **Pending：** 跨期间连锁重开与部分账簿重开未实现；“最近关闭期间”与“前序期间已关闭”检查均在事务前执行，相邻期间同时关账与重开存在理论竞态；内部开发接口，未接 Gateway 与授权，Maker/Checker 由请求传入。

<a id="finance-auto-posting-20261016"></a>

## Finance 交易自动记账（2026-10-16）

- **Implemented（未提交、未部署）：** 按[需求](../requirements/20261016/finance-交易自动记账/requirement.md)与[技术方案](../requirements/20261016/finance-交易自动记账/technique.md)，Finance 新增内部 `AccountingRuleBundleApi`（`/accounting-rule-bundles/v1`）：以完整行集创建规则版本、提交冻结、Maker-Checker 批准（已到生效时间即切换当前版本并退役旧版本，否则 APPROVED）或驳回、Bundle 与版本查询；新增模块领域服务 `TransactionPostingService`：读取已冻结的 PAYMENT 交易经济与组件，按当前生效 `STANDARD_TRANSACTION` 规则版本生成 AUTO 指令（OE 账簿支付成功与 Partner 侧服务费、Platform Finance Entity 账簿平台侧服务费），按 Owner Role 自动建账户，多账簿同一事务过账，重复调用幂等。本地 SQL：预置规则 Bundle 5001、版本 5101、规则行 5201—5212（已在本地 `mor_finance` 执行，见[交付记录](../requirements/20261016/finance-交易自动记账/delivery.md)）。生产代码与测试由三个并行 agent 实现、主会话合并核对。
- **Verified：** finance-service JDK 25 `clean verify` **172/172**（新增 38 个）；JaCoCo 命令行合计行 96.6%、分支 81.4%，`TransactionPostingServiceImpl` 行 95.2%。本地 `-exec.jar` 规则接口 HTTP 冒烟 20/20，数据已清理并恢复预置规则；过账引擎无 HTTP 入口，以领域服务入口的 17 个业务测试为准。
- **Pending：** 引擎尚无生产调用方——PaymentSucceeded MQ 消费、交易经济创建与费率计算、`pom` 禁令放开与补偿事件表迁移、Channel Open Item、出站事件、资金成熟均待后续批次；全链路依赖 Payment 事件 v2、Commerce 订单上下文查询、CCT 价格解析与 Finance 税务快照，见[跨系统依赖清单](../requirements/20261016/finance-交易自动记账/dependencies.md)。内部开发接口，未接 Gateway 与授权。

<a id="finance-period-close-20261016"></a>

## Finance 期间关账（2026-10-16）

- **Implemented（未提交、未部署）：** 按[需求](../requirements/20261016/finance-期间关账/requirement.md)与[技术方案](../requirements/20261016/finance-期间关账/technique.md)，Finance 新增内部 `PeriodCloseApi`（`/accounting-periods/v1/{periodId}/closes`）：按顺序关账，发起后期间进入 CLOSING，同步按短事务执行 7 个固定步骤（锁定水位、异步与分录检查、银行/渠道余额核对 Fail-Closed、试算、异常检查、损益结转到 `CURRENT_PERIOD_RESULT/NONE`、按本流程水位最终试算），全部通过后冻结 RFC 8785 `CODE-MOR:FINANCE:PERIOD_CLOSE:1` 关账摘要进入 PENDING_CHECK，否则 FAILED 并回到 OPEN；Maker-Checker 批准（期间 CLOSED、流程 COMPLETED）或驳回（回到 OPEN）；列表与详情查询。过账内核期间门禁区分普通分录与结转分录；试算支持按指定水位执行。新增 `TrialExceptionApi` 投影重建 Maker-Checker（`CODE-MOR:FINANCE:PROJECTION_REBUILD:1`）。本地 SQL：`t_period_close.close_hash` 改可空，`t_close_account_rule` 预置 4001—4007（已在本地 `mor_finance` 执行，见[交付记录](../requirements/20261016/finance-期间关账/delivery.md)）。生产代码与测试由三个并行 agent 实现、主会话合并核对。
- **Verified：** finance-service JDK 25 `clean verify` **134/134**（新增 25 个关账与投影重建用例）；JaCoCo 命令行合计行 96.8%、分支 81.2%，关账与投影重建相关 Processor/Service 行覆盖 ≥ 91%。本地 `-exec.jar` 真实 HTTP 冒烟 22/22 通过，数据已清理。
- **Pending：** 期间重开、年末结转留存收益与其他试算异常处置未实现；步骤 3 在 Treasury 上线前对非零银行/渠道账户 Fail-Closed；内部开发接口，未接 Gateway 与授权，Maker/Checker 由请求传入。

<a id="finance-trial-balance-20261016"></a>

## Finance 试算平衡与期间余额（2026-10-16）

- **Implemented（未提交、未部署）：** 按[需求](../requirements/20261016/finance-试算平衡/requirement.md)与[技术方案](../requirements/20261016/finance-试算平衡/technique.md)，Finance 新增内部 `TrialBalanceApi`：按账簿 + 期间执行试算（序号行共享锁取一致水位，按科目汇总期初/本期借贷/期末有符号余额，期末余额拆分借贷合计，账户投影与 Journal 重算核对，`GENERAL_LEDGER_MISMATCH` 记录为 OPEN 异常，RFC 8785 `CODE-MOR:FINANCE:TRIAL_BALANCE:1` 内容摘要），成功试算按水位条件刷新 `ledger_period_balance`；试算列表与详情查询；`JournalPostingServiceImpl` 通配符 import 清理。复用已存在的 4 张表，无 SQL 变更。生产代码与测试由两个并行 agent 实现、主会话合并核对。
- **Verified：** finance-service JDK 25 `clean verify` **109/109**（新增 13 个试算用例，含序号行锁等待的双连接测试）；JaCoCo 命令行合计行 97.4%、分支 82.2%。本地 `-exec.jar` 真实 HTTP 冒烟 8/8 通过，数据已清理。
- **Pending：** 异常只记录不处置（Maker-Checker 解决与投影重建随期间关账实现）；往来、渠道、对账类异常待交易经济与 Treasury；内部开发接口，未接 Gateway 与授权；试算汇总全量 Journal，后续可用上一期间余额作起点。

<a id="finance-ledger-query-20261015"></a>

## Finance 账本流水与账户查询（2026-10-15）

- **Implemented（已本地提交 `c542498`，未推送、未部署）：** 按[需求](../requirements/20261015/finance-账本流水查询/requirement.md)与[技术方案](../requirements/20261015/finance-账本流水查询/technique.md)，Finance 新增 FIN-I05 内部只读接口 `LedgerBookQueryApi`：账簿分页、账户分页（余额投影）、账户流水分页（`balanceAfter` 由窗口函数对账户全量历史累计，日期过滤与翻页不改变累计起点）、分录详情（来源追溯到人工调整单号）。无表结构或 SQL 变更；不含 Operations BFF/页面与 Client 暴露。
- **Verified：** finance-service JDK 25 `clean verify` **96/96**（新增 16 个查询用例）；JaCoCo 命令行合计行 97.1%、分支 81.0%，新增查询代码行覆盖 100%。本地 `-exec.jar` 真实 HTTP 冒烟 9/9 通过，数据已清理。
- **Pending：** 内部开发接口，未接 Gateway 与 Owner/OE 授权；累计余额对账户全量历史计算，高频账户需后续按期间余额截断。

## Operations 接入支付风控配置与执行发布（2026-10-15）

- **Implemented / mor-test 已部署并验收（已本地提交，未推送）：** 按[需求](../requirements/20261015/operations-console-支付风控配置接入/requirement.md)与[方案](../requirements/20261015/operations-console-支付风控配置接入/technique.md)，Operations BFF 以 risk-api `b26e857` 编译：决策流节点与创作上下文接受可选 `outputs`，变量创建支持 `VELOCITY`/`LIST` 参数（旧请求可省略），新增名单 5 个、执行发布 6 个转发接口，`/risk-lists` 只记元数据日志。Web 规则集可切换风控点（支付点用统一变量目录），决策流放行结束节点配置认证要求（读上下文声明，缺失时支付点回退两个固定值），变量新增实时累计/名单命中表单，新增“风控名单”“执行发布”两页。验证：BFF `verify` 1298/1298，Web 1012/1012 与生产构建通过。阶段 5 本机全链路（Nginx→Gateway→BFF→Risk）接口 44/44、浏览器 26/26 通过，修复 5 个缺陷后 BFF `verify` 1299/1299、Web 1014/1014；Platform 注册 SQL 修正为 150 行（补写路由请求头），已在本机 `mor_platform_e2e` 执行并激活。2026-10-15 mor-test：BFF 镜像 `68e939f5…`、Web 镜像 `5fb6911b…`（`132c982` 加本需求未提交改动构建），`mor_platform` 执行注册 150 行并激活（PLATFORM_ADMIN v24）；经 `http://localhost:28080` 接口 44/44、浏览器 27/27、25 页只读回归通过，合成数据已清理（`mor_risk` 84 表行数与备份一致）；支付点联调基线（`PAY_ATTEMPT_FLOW`，发布 R-11 ACTIVE：3DS 门槛 500、审核 5000 USD、同卡 1 小时 ≥5 次拒绝、黑名单卡拒绝）保留供 Payment 联调，test 中 Payment 未部署。详见[阶段 5 报告](../requirements/20261015/operations-console-支付风控配置接入/verification/stage5-local-acceptance.md)与[发布记录](../requirements/20261015/operations-console-支付风控配置接入/release/release.md)。

<a id="payby-integration-20261014"></a>

## Risk 支付反欺诈一期（2026-10-14）

- **Implemented / auto 已验证，一期主体与修订均已本地提交（未推送）；2026-10-15 已部署 mor-test（`mor_risk` 84 表，验收数据已清理，支付决策点无生效发布，见[部署记录](../requirements/20261014/risk-支付反欺诈一期/release/test-acceptance.md)）；随后[执行发布列表与输出声明](../requirements/20261015/risk-执行发布列表与输出声明/delivery.md)（提交 `b26e857`）已部署，test Risk 镜像为 `fcfc9457dab6`：** 按[需求](../requirements/20261014/risk-支付反欺诈一期/requirement.md)与[决定](./decisions/2026-10-14-risk-payment-fraud-phase1.md)在 risk-compliance-service 实现支付尝试评估（`PAYMENT_ATTEMPT_RISK`）、名单维护、实时累计与名单命中 Feature、执行发布（校验、回放式影子运行、TEST 一人激活）。Risk 模型 81 → 84 表；增量 SQL 经授权已在本机 `mor_risk` 执行并与 Schema 逐列核对一致。2026-10-15 按[修订](../requirements/20261014/risk-支付反欺诈一期/design-amendment-unified-evaluation.md)改为统一评估接口 `/risk-decisions/v1/evaluations`，认证要求改由放行结束节点输出配置（新增 `t_decision_flow_end_output`，删除 3DS 专用动作）。Risk 全量 auto **1992/1992**；阶段 5 本机验收（打包 jar 经真实 HTTP 完整链路 61 项通过，见[验收报告](../requirements/20261014/risk-支付反欺诈一期/verification/stage5-local-acceptance.md)，已确认）。Payment 尚未接入（接入清单见[设计第 14 节](../requirements/20261014/risk-支付反欺诈一期/design.md#14-交给-payment-的接入清单不在本会话范围)）；本机无生效支付发布时评估接口返回 503 `RISK_DECISION_NOT_CONFIGURED`。实施修订与已知缺口见[设计第 16 节](../requirements/20261014/risk-支付反欺诈一期/design.md#16-阶段-4-实施修订2026-10-14)。

## Risk 结账点运行期评估（2026-10-16，已本地提交，未推送；Risk 已部署 mor-test）

- **Implemented / auto 已验证：**按[需求](../requirements/20261016/risk-结账点运行期评估/requirement.md)与[方案](../requirements/20261016/risk-结账点运行期评估/design.md)，`CHECKOUT_PREPARE`（RCO-I03）接入统一评估：输入合同 `ORDER_PREPARATION` v5（12 个字段，去掉 locale；v4 只读）、结账评估器与快照（`t_checkout_input_snapshot` 按 v5 重建，表数仍 85）、执行发布支持结账点（运行期点版本 220001；流程须为 v5；结账不支持附加动作与实时累计/名单变量）、影子回放按风控点分派；运行期输入抽象为 `RuntimeRiskInput`。Commerce 创建结账经 `RiskDecisionApi` 真实评估（删除桩）：拒绝/审核 422 `CHECKOUT_NOT_ELIGIBLE`，结果未知重试一次后 503 `DEPENDENCY_UNAVAILABLE`，协议错误 502，均不落订单。Operations Web 执行发布页、决策流提示、附加动作区与评估查询（按订单 ID）支持结账点。验证：Risk 全量 auto **2064/2064**、Commerce **790/790**、Web **1052/1052**；本机端到端与浏览器 9/9（[记录](../requirements/20261016/risk-结账点运行期评估/verification/stage5-local-acceptance.md)）。
- **数据库与部署：**本机 `mor_risk` 与 mor-test `mor_risk` 经授权执行 [ddl](../requirements/20261016/risk-结账点运行期评估/db/ddl.sql)、[dml](../requirements/20261016/risk-结账点运行期评估/db/dml.sql)（均先备份）。mor-test Risk 换为 image `8e26b91b519c`，27 个只读接口回归一致，结账默认放行基线已激活，验收合成数据已清理（[记录](../requirements/20261016/risk-结账点运行期评估/release/test-acceptance.md)）；test 已有结账执行发布，旧镜像 `727d51127e61` 不可再回退。mor-test 未部署 Commerce 与 Operations Web 本次改动。
- **遗留：**本机跑 Risk 自动测试会清掉结账执行发布，之后需重建基线；Commerce CCT 不可用路径仍返回 `SERVICE_UNAVAILABLE`（不在 Client API 合同表中）；规则配置页冻结前检查比 Risk 严（支付点既有）。

## Risk 结账点实时累计（2026-10-17，已本地提交，未推送；Risk、Operations BFF、Operations Web 已部署 mor-test）

- **Implemented / auto 已验证：**按[需求](../requirements/20261017/risk-结账点实时累计/requirement.md)与[方案](../requirements/20261017/risk-结账点实时累计/design.md)，结账评估成功时同一事务写入 `CHECKOUT_ASSESSED`（非放行另写 `CHECKOUT_REJECTED`），主体为 Client 与运营主体，来源 `CHECKOUT_INPUT_SNAPSHOT` + 快照 ID；事件构造与支付共用（支付事件摘要不变）。事件编码归属风控点，描述符按点声明累计与名单主体，结账开放实时累计与名单变量；Client、运营主体限制指标与窗口（创建时校验，历史变量不复核）。变量创作上下文新增 `featureCatalogs`，Operations BFF 透传、Web 变量页按点读取。窗口查询索引追加 `velocity_event_id`，金额合计与最近一次固定查询计划。Risk auto 2083/2083，BFF 1379/1379，Web 1067/1067。
- **数据库与部署：**本机与 mor-test `mor_risk` 经授权执行 [ddl](../requirements/20261017/risk-结账点实时累计/db/ddl.sql)（2 条注释、1 个索引，先备份）。mor-test Risk `725f7d12aef4`、BFF `4b702a37c151`、Web `03668fd01a22`（构建自 `14381f9` + 本需求改动，Web 随带上线上一需求 `87fd57f`），回归与验收通过、验收数据已清理，test 无结账 Feature 变量（[记录](../requirements/20261017/risk-结账点实时累计/release/test-acceptance.md)）；结账生效发布为同一基线流程版本的 `237117104682274816`。
- **遗留：**Client、运营主体 24 小时 / 7 天金额合计与去重数需小时预聚合（后续需求）；累计事件无保留期清理；本机保留的结账基线流程会使 Risk 全量测试流程计数多 1，跑全量前需先处理。

## Risk 评估结果查询与解释（2026-10-15）

- **Implemented / auto 已验证，已部署 mor-test（代码尚未提交）：** 按[需求](../requirements/20261015/risk-评估结果查询与解释/requirement.md)与[方案](../requirements/20261015/risk-评估结果查询与解释/design.md)新增 `RiskEvaluationQueryApi`：`GET /risk-decisions/v1/evaluations/{id}`、按 `pointCode+businessKey` 查正式评估（RCO-I06），`GET …/{id}/explanation`（RCO-I10：输入原值、流程路径、规则与谓词结果、变量取值、影子比较）。评估与影子回放新增写入 `t_evaluation_flow_trace`（Risk 模型 84 → 85 表），超时与 Feature 失败（`FEATURE_ERROR`）保留过程记录，影子补写规则轨迹。本机 `mor_risk` 经授权已建表；Risk 全量 auto **2022/2022**；阶段 5 打包 jar 真实 HTTP 71 项通过、评估耗时与基线持平（[报告](../requirements/20261015/risk-评估结果查询与解释/verification/stage5-local-acceptance.md)）。2026-10-15 mor-test：执行 ddl（85 表）、Risk 换为 image `727d51127e61`，20 个只读接口回归一致，验收 34 项通过，合成数据已清理（[部署记录](../requirements/20261015/risk-评估结果查询与解释/release/test-acceptance.md)）。部署前写入的评估路径不可用；Operations 页面与 BFF 未接入（[交接单](../requirements/20261015/risk-评估结果查询与解释/handoff/operations.md)）。

## 运营后台风控评估查询（2026-10-15）

- **Implemented / mor-test 已部署（代码尚未提交）：** 按[需求](../requirements/20261015/operations-console-评估查询与解释/requirement.md)与[方案](../requirements/20261015/operations-console-评估查询与解释/design.md)，Operations BFF 接入 Risk `RiskEvaluationQueryApi` 三个 GET（按评估 ID、按支付尝试、评估解释），用规则集版本详情组装条件树；Web 新增“风控管理 ▸ 评估查询”页（结论、路径、规则与条件、变量、输入、影子评估）。权限 `RISK_EVALUATION_READ` 与 3 条路由经 Platform 注册（32 行）。验证：BFF `verify` 1349/1349、Web 1043/1043；本机全链路浏览器 22/22（[报告](../requirements/20261015/operations-console-评估查询与解释/verification/stage5-local-acceptance.md)）；mor-test BFF `376b5b827945`、Web `d37816c5ba8a`，注册与激活（PLATFORM_ADMIN v27），浏览器 17/17、29 页回归通过，合成数据已清理（[记录](../requirements/20261015/operations-console-评估查询与解释/release/test-acceptance.md)）。

<a id="toss-pay-integration-20261017"></a>

## Toss Pay 收款接入（2026-10-17）

- **Designed：** [需求](../requirements/20261017/platform-support-TossPay接入/requirement.md)与[技术方案](../requirements/20261017/platform-support-TossPay接入/technique.md)经用户确认：接入方 `TOSS_PAY`（`pay.toss.im`，只用 `sk_test_` 测试 key）实现 `PAY`（REDIRECT `checkoutPage`，只收 KRW）、`PAY_QUERY`（退款相关状态仍为 `SUCCEEDED`；到期未付款仍为 `PAY_STANDBY`，超时由 Payment 判定）、`REFUND`（先查单取 `payToken`，发 `refundNo`，`code=0` 即成功）、`REFUND_QUERY`（`transactions[]` 按 `refundNo` 匹配）；“单不存在”只按 msg 以 `orderNo` 结尾识别，其余为 ERROR。`PAY_CANCEL`（无接口）、billingKey 自动结算与回调不在范围。
- **Implemented / Verified：** 引擎新增原子 API `credentialFields`：声明的 BODY 字段只接受渠道账号凭据的受控句柄，只在 JSON 发送字节中展开，轨迹与验证证据为 `[REDACTED]`；凭据字段之外出现句柄、或含凭据的请求体调用 `sealJsonBody` 时节点 ERROR 且不发请求；Operations 原子 API 编辑页保留该属性。Platform 全量 729/729、前端 1061/1061。提交 `fe5a0f3`（未推送）。
- **Platform mor-test Deployed（2026-10-17）：** 镜像 `toss-pay-20261017`（`629cccf9…`，构建自 `fe5a0f3`）替换 Webhook 管理版 `efeb1d9e…`，healthy；无 SQL 与配置变更。
- **mor-test 配置 Deployed / Sandbox Verified（2026-10-17）：** 经管理接口写入接入方 `TOSS_PAY`、1 个脚本、4 个原子 API、4 条流程与验证账号 `SBX_MAIN`。5 个不需付款的用例 PASSED，[回执](../requirements/20261017/platform-support-TossPay接入/assets/test-sandbox-receipt.json)；运行详情与 Platform 日志按值核对不含 apiKey。PayBy、PayerMax、Alipay+、dLocal 回归 16/16 PASSED。已付款链路需要 Toss App 韩国账号，用户确认跳过。详见[交付记录](../requirements/20261017/platform-support-TossPay接入/delivery.md)。

<a id="dlocal-integration-20261017"></a>

## dLocal 收款接入（2026-10-17）

- **Designed：** [需求](../requirements/20261017/platform-support-dLocal接入/requirement.md)与[技术方案](../requirements/20261017/platform-support-dLocal接入/technique.md)经用户确认：接入方 `DLOCAL` 以托管跳转（REDIRECT，空 `payer`）实现 `PAY`、`PAY_QUERY`、`PAY_CANCEL`（只撤销待付款单）、`REFUND`、`REFUND_QUERY`；退款与退款查询先按我方单号取 dLocal `payment_id`，退款查询沿用可选 `refund.paymentAttemptId` 并在 `refunds[]` 中匹配；退款不输出金额（渠道按本地币种返回）。现有引擎函数够用，不改代码与标准 API。卡、Nequi/SPEI、SmartPix 代扣与回调不在范围。
- **mor-test 配置 Deployed / Sandbox Verified（2026-10-17）：** 经管理接口写入接入方 `DLOCAL`（沙箱地址）、4 个脚本、5 个原子 API、5 条流程与验证账号 `SBX_MAIN`；无代码、SQL 变更。7 个用例 PASSED，含已付款链路（harness 直连 dLocal 沙箱工具强制 `PAID` 与退款 `SUCCESS`），[回执](../requirements/20261017/platform-support-dLocal接入/assets/test-sandbox-receipt.json)；PayBy、PayerMax、Alipay+ 回归 PASSED。测试单均为终态。详见[交付记录](../requirements/20261017/platform-support-dLocal接入/delivery.md)。

<a id="alipay-plus-integration-20261016"></a>

## Alipay+ 收款接入（2026-10-16）

- **Designed：** [需求](../requirements/20261016/platform-support-AlipayPlus接入/requirement.md)与[技术方案](../requirements/20261016/platform-support-AlipayPlus接入/technique.md)经用户确认：接入方 `ALIPAY_PLUS` 实现 `PAY`（收银台，REDIRECT `normalUrl`）、`PAY_QUERY`、`REFUND`、`REFUND_QUERY`；`PAY` 增加可选 `payment.paymentMethodType`、`REFUND_QUERY` 增加可选 `refund.paymentAttemptId`；`cancelPayment` 有冲正语义不接。沙箱直连探测 7 笔测试单均已关单。
- **Implemented / Verified：** 引擎新增脚本函数 `urlEncode`、`urlDecode` 与纯函数 `MONEY_MAJOR_UNITS`；`auto` 测试含 URL 编码签名与验签的完整 HTTP 链路（`StandardApiUrlSignedChannelTest`），外部 API 相关 125 个测试通过。提交 `59459ba`（未推送）。
- **Platform mor-test Deployed（2026-10-16）：** 镜像 `alipay-plus-20261016`（`ee17f35a…`，构建自 `59459ba`）替换 Webhook 版 `63ae4cde…`，healthy、启动日志无 ERROR；无 SQL 与配置变更。PayBy 5 个 UAT 用例回归 PASSED。发布、备份与回退见[交付记录](../requirements/20261016/platform-support-AlipayPlus接入/delivery.md)。
- **mor-test 配置 Deployed / Sandbox Verified（2026-10-16）：** 经管理接口给 `PAY`、`REFUND_QUERY` 追加可选字段，写入接入方 `ALIPAY_PLUS`（沙箱地址）、3 个脚本、4 个原子 API、4 条流程与验证账号 `SBX_MAIN`；无代码、SQL 变更。4 个不需付款的用例 PASSED，渠道应答全部验签通过，[回执](../requirements/20261016/platform-support-AlipayPlus接入/assets/test-sandbox-receipt.json)；PayBy、PayerMax UAT 回归 PASSED。
- **Pending：** 用户付款后的已付款链路（查询成功、部分退款、退款查询）；通知、授权代扣不在范围。

## PayBy（阿联酋）收款接入（2026-10-14）

- **Designed：** [需求](../requirements/20261014/platform-support-PayBy接入/requirement.md)与[技术方案](../requirements/20261014/platform-support-PayBy接入/technique.md)经用户确认：接入方 `PAYBY` 实现 `PAY_QUERY`、`PAY_CANCEL`（新，草案）、`REFUND`、`REFUND_QUERY`、`BALANCE_QUERY`（新，草案）；`PAY` 以 DIRECTPAY 卡直付为主，所需卡字段加密、有效期格式与 3DS 表单托管写入[补充要求](../requirements/20261014/platform-support-PayBy接入/card-capability-addendum.md)，由卡直付会话评审实现。毫秒 `requestTime` 暂由签名脚本从 `nowRfc3339()` 换算（TODO 改注册函数）。
- **mor-test 配置 Deployed / UAT Verified（2026-10-14）：** 经管理接口写入上述两个标准 API、接入方（UAT 地址标记沙箱）、2 个脚本、5 个原子 API、5 条流程与验证账号 `UAT_MAIN`；无代码、镜像、SQL 或基础设施变更。渠道沙箱验证 6 个 UAT 用例 PASSED（不存在订单/退款、AED 余额、未付款订单查询→退款被拒→关单两次→已失败），双向验签通过，[回执](../requirements/20261014/platform-support-PayBy接入/assets/test-uat-receipt.json)。
- **Pending：** DIRECTPAY `PAY` 依赖 Platform 卡能力合入并发布到 mor-test；已付款订单的退款与退款查询随卡直付验证；异步通知与 `revokeOrder` 不在范围；提交与推送未授权。

<a id="channel-sandbox-verification-20261014"></a>

## 渠道沙箱验证（2026-10-14）

- **Designed：** [需求](../requirements/20261014/platform-support-渠道沙箱验证/requirement.md)、[技术方案](../requirements/20261014/platform-support-渠道沙箱验证/technique.md)与[决定](./decisions/2026-10-14-platform-integration-sandbox-verification.md)已确认：Operations 页面维护验证账号与多步骤用例，沙箱实测并保存脱敏报文证据；PayerMax UAT 为首批用例。
- **阶段 2 Implemented / Verified（2026-10-14）：** Platform 模型增加四表（72 表、875 字段、120 唯一索引、95 访问索引、111 关系），[增量 SQL](../requirements/20261014/platform-support-渠道沙箱验证/db/ddl.sql) 已按授权在本机 `mor_platform` 与 `mor_platform_external_api` 执行并逐列核对；DataObject/Mapper/Repository 完成，`./mvnw -o -pl platform-support-test -am test` 352/352。共享环境未执行。
- **阶段 3 Implemented / Verified（2026-10-14）：** 出站地址 `sandbox` 标记；执行引擎验证钩子（验证账号来源、脱敏轨迹、沙箱限定，正式调用不变）；用例校验、模板渲染、期望核对；V01～V14 管理接口与补偿任务异步执行（最多 2 次，中断记 INTERRUPTED 不重放）。`./mvnw -o -pl platform-support-test -am test` 413/413。部署前提：生产 `compensation.enabled` 默认关闭，未启用时发起运行返回 503，本需求未改生产配置。详见[交付记录](../requirements/20261014/platform-support-渠道沙箱验证/delivery.md)。
- **阶段 4 Implemented / Verified（2026-10-14）：** BFF 透传 V01～V14（`ExternalApiBrowserAutoTest` 102/102；全量回归补齐路由清单后 BFF 1279/1279）；Operations 接入方“验证”页签、验证账号/用例编辑页、运行详情与出站地址沙箱开关（`npm test` 983/983，`npm run build` 通过）；V12 增加运行原因。[注册 SQL](../requirements/20261014/platform-support-渠道沙箱验证/db/registration-dml.sql)（14 Route，DISABLED）未执行，页面未做浏览器实测。
- **阶段 5 本机 Verified（2026-10-14）：** 注册与本机启用 SQL 仅在 `mor_platform_external_api` 执行（14 Route ACTIVE）；经真实入口对合成 PayerMax 跑通：未标沙箱时运行在发请求前失败，标记后两个用例 PASSED，证据不含私钥，[回执](../requirements/20261014/platform-support-渠道沙箱验证/assets/stage5-mock-receipt.json)；浏览器 8 页无错误。
- **PayerMax UAT Verified（2026-10-14，本机）：** 接入方 `PAYERMAX`（UAT 地址标记沙箱）两个用例 PASSED：下单 → 查询 PENDING、查询不存在订单；请求与响应双向验签通过，[回执](../requirements/20261014/platform-support-渠道沙箱验证/assets/stage5-uat-receipt.json)。
- **mor-test Deployed / Verified（2026-10-14）：** 按[发布清单](../requirements/20261014/platform-support-渠道沙箱验证/release/release.md)经用户批准执行（含外部 API 与 PayerMax 配置）：test `mor_platform` 补 25 表（与完整 Schema 一致）、登记 499 行、启用 52 行（ACTIVE 路由 149）；Gateway/Platform/BFF/Web 为 `ba36b63` 候选镜像，Platform `COMPENSATION_ENABLED=true`，Risk 未升级；16 个菜单页与 PayerMax UAT 两个用例通过。Gateway 因 Client 配置绑定缺陷暂用两项占位环境变量（Client 通道关闭），待修复后删除。见[执行记录](../requirements/20261014/platform-support-渠道沙箱验证/release/test-acceptance.md)。
- **Pending：** 收银台付款后查询 SUCCEEDED（人工付款）；test 正式标准 API 执行需 Vault 渠道账号读取策略；其他共享环境未执行。阶段 2～5 已按用户要求本地提交（2026-10-14），未推送。

<a id="payment-payermax-card-direct-20261014"></a>

## Payment 经 Platform 对接 PayerMax 卡直付（2026-10-14）

- **Designed：** [需求](../requirements/20261014/payment-PayerMax卡直付对接/requirement.md)与[技术方案](../requirements/20261014/payment-PayerMax卡直付对接/technique.md)已确认：卡支付走渠道服务端卡 API，不用渠道收银台；PCI DSS 认证为上生产前提。[卡数据决定](./decisions/2026-10-14-card-data-platform-execution.md)：服务间只传支付工具 Token，Platform 经 `PAYMENT_CARD` 向 Payment 读卡、只在内存中；风控本期 Mock，恢复本期不做。
- **阶段 2 Implemented / Verified（2026-10-14，本机）：** Platform 卡值句柄、`cardFields`、`PAYMENT_CARD`、证据遮蔽与保存校验；Payment `ChannelCardMaterialApi` 合同（服务端未实现）；Operations 原子 API 编辑页保留并编辑 `cardFields`。`./mvnw -o -pl platform-support-test -am test` **424/424**，前端 `npm test` 990/990 与构建。本机 `PAYERMAX_MOCK`、`PAYERMAX` 的 `PAY` 改为卡直付（收银台版本删除）；沙箱验证合成渠道 7/7、PayerMax UAT 测试卡 4/4 PASSED（成功、3DS、处理中、被拒），日志与库中无卡值原值。详见[交付记录](../requirements/20261014/payment-PayerMax卡直付对接/delivery.md)第 6 节。
- **阶段 3 Implemented / Verified（2026-10-14，本机测试库）：** T12 已确认（只传 IP/UA）。Payment：Channel 公开 `ExecuteChannelOperationProcessor`（校验冻结坐标、不 Reveal、调 Platform），Execution 端口由 Web 实现，Routing 改为纯选择，风控经 Risk 草案合同失败关闭，`PayActionActor` 经 Platform 执行并映射结果，读卡接口与 Card Mock（CVV 一次性），`PayResponse.nextAction`；修复 Execution Mapper 类型映射与完成态 SQL 两处既有缺陷。经授权在本机 `mor_payment_channel_test` 补建执行五表。Payment `auto` **64/64**（卡直付 8），Platform **424/424**。详见[交付记录](../requirements/20261014/payment-PayerMax卡直付对接/delivery.md)第 8 节。
- **Pending：** 阶段 4 本机端到端（运行库建表待授权）；Risk 真实接口、恢复、AUTHORIZE/AUTHENTICATE；mor-test 仍为收银台版 `PAY`；未提交。
- **接入 Risk 统一评估 Implemented / auto 已验证（2026-10-15，本机测试库，已本地提交，未推送、未部署）：** 按[需求](../requirements/20261015/payment-接入Risk统一评估/requirement.md)与[技术方案](../requirements/20261015/payment-接入Risk统一评估/technique.md)删除 Payment 草案客户端，改用 `risk-api` 的 `RiskDecisionApi`（`/risk-decisions/v1/evaluations`），以 `PaymentAttemptRiskInput` 传 17 个字段（卡 BIN 与指纹由 Card Mock 提供）；REVIEW/DENY/FAILED/TIMED_OUT 使 Attempt 失败并释放 Intent；409、超时与 5xx 同一请求重试一次后失败关闭，不使用风控 UNKNOWN 或恢复任务。Payment `./mvnw -o verify` **83/83**（新增风控 20）。仅改 `t_payment_action.transaction_assessment_id` 注释（[增量 SQL](../requirements/20261015/payment-接入Risk统一评估/db/ddl.sql) 未执行）。mor-test 的 Risk 支付决策点无生效发布，联调待运营激活。
- **Card 加密存储 Implemented / auto 已验证（2026-10-15，本机测试库，已本地提交，未推送、未部署）：** 按[需求](../requirements/20261015/payment-Card加密存储/requirement.md)与[技术方案第 3 版](../requirements/20261015/payment-Card加密存储/technique.md)删除 Card 内存 Mock，改为三层：`t_payment_card` 每个卡号一条（卡指纹唯一索引，可按明文卡号定位），保存以本卡 DEK（AES-256-GCM）加密的卡号、BIN（卡号不少于 16 位存 8 位，否则 6 位）与后四位；`t_payment_card_credential` 按（卡、凭证摘要）唯一，加密保存有效期与持卡人原值；`t_payment_card_instrument` 每个支付工具一条，主键即 `instrument_ref`。每张卡的 DEK 由 Vault 数据类型 `PAYMENT_CARD_DEK` 托管（[例外决定](./decisions/2026-10-15-vault-payment-card-dek.md)）；CVV 以本卡 DEK 加密按交易工具暂存 Redis，`GETDEL` 只领取一次。卡与凭证在工具申请主事务前单条插入，撞唯一键改读已有记录；原提交重放比较指纹与凭证摘要。指纹密钥 `PAYMENT_CARD_FINGERPRINT_KEY` 必填且不可轮换。Payment `./mvnw -o verify` **108/108**。经授权仅在本机 `mor_payment_channel_test` 删除第 2 版空表并执行[建表 SQL](../requirements/20261015/payment-Card加密存储/db/ddl.sql)。本机 Vault 已注册 `PAYMENT_CARD_DEK` 与两条访问规则，并完成直接调用及 Payment 整应用（测试库、关闭 Nacos）工具申请联调；其他环境尚未注册。
- **已存卡（Customer 与存卡支付 P1）Implemented / auto 已验证（2026-10-15，本机测试库，已本地提交，未推送、未部署）：** 按[需求](../requirements/20261015/commerce-Customer与存卡支付/requirement.md)与[技术方案](../requirements/20261015/commerce-Customer与存卡支付/technique.md)实现 Payment 部分：新表 `t_payment_stored_card`（按 Client、Commerce Customer、卡唯一的有效已存卡，对外 `sc_` Token），凭证增加明文有效期与 `verified_time`，交易工具记录 Customer、是否同意保存与已存卡；申请工具支持“已存卡 + 本次 CVV”；卡支付成功事务内标记凭证已验证并按同意存卡；新增已存卡列出、删除、按 Customer 失效内部接口。Payment `./mvnw -o verify` **116/116**。经授权仅在本机 `mor_payment_channel_test` 执行[增量 SQL](../requirements/20261015/commerce-Customer与存卡支付/db/payment/ddl.sql)。Commerce Customer、结账接口及 Commerce 接通 Payment pay 尚未实现。
- **Commerce Customer（Customer 与存卡支付 C1、C2）Implemented / auto 已验证（2026-10-15，本机库，已本地提交，未推送、未部署）：** 新表 `t_customer`（`cus_` 公开编号，按 Client 与有效引用唯一）与订单 `customer_id`；Client API `/client-gateway-api/v1/customers` 六个路由（创建、查询、删除、列出与删除已存卡）；建单可带 `consumerReference` 关联或新建 Customer；删除经 `STORED_CARD_INVALIDATION` 补偿任务调用 Payment 失效已存卡。删除已失效的旧 CCT 预检链以恢复 Commerce 编译。Commerce `./mvnw -o test` **743/743**（`verify` 覆盖率门禁为既有问题，未运行）。经授权仅在本机 `mor_commerce` 执行[增量 SQL](../requirements/20261015/commerce-Customer与存卡支付/db/commerce/ddl.sql)。C2 已实现：`t_client_stored_card_setting` 按 Client 开通；结账查询返回 `cardSaving` 与已存卡，提交支持手输卡保存或已存卡加 CVV；新增 `CheckoutCardPaymentPortImpl` 经 Payment pay 提交，替换固定 Mock（结账“查询支付提交”仍为占位）。Commerce `./mvnw -o test` **749/749**，Payment `verify` **116/116**。
- **支付结果查询与回传 Implemented / auto 已验证（2026-10-15，本机，已本地提交，未推送、未部署）：** 按[需求](../requirements/20261015/payment-支付结果查询与回传/requirement.md)与[方案](../requirements/20261015/payment-支付结果查询与回传/technique.md)：Payment 实现 EX-I06 按来源提交查询，并在卡支付成功事务内登记补偿事件、提交后经 RabbitMQ `topic_payment_result`/`payment.succeeded.v1` 发布 `PaymentSucceeded`；Commerce 结账查询改调 EX-I06，新增 `cg_commerce_payment_result` 消费者在一个事务内推进支付投影、订单 PAID、会话 COMPLETED、可退基准与资金 PENDING（Enforcer 放行 AMQP）。Payment `verify` **127/127**，Commerce `test` **764/764**。本机 Payment 测试库补建 `t_compensation_event`。

<a id="payermax-refund-20261014"></a>

## PayerMax 退款与退款查询（2026-10-14）

- **Designed：** [需求](../requirements/20261014/platform-support-PayerMax退款/requirement.md)与[技术方案](../requirements/20261014/platform-support-PayerMax退款/technique.md)经用户确认（R1～R6 按建议）：标准 API `REFUND` / `REFUND_QUERY`（草案），PayerMax `/refund`、`/refundQuery` 外层 `version=1.1`；不改代码与数据库结构，只新增配置。
- **阶段 2 Verified（2026-10-14，本机）：** [harness](../requirements/20261014/platform-support-PayerMax退款/assets/harness/README.md) 经 Web 代理 → Gateway → BFF → Platform 在 `mor_platform_external_api` 保存两个标准 API、`PAYERMAX_MOCK` 下两个原子 API 与两条流程（重跑按版本更新）；合成 PayerMax 增加退款接口，`verify_mock_refund.py` 14 项 PASS（受理三种状态、两类明确拒绝、`SYSTEM_ERROR` 与响应验签失败 → ERROR、查询三种状态与不存在、422/503 零渠道请求），[回执](../requirements/20261014/platform-support-PayerMax退款/assets/stage2-receipt.json)；原 `verify_mock.py` 12 项回归通过，日志无私钥。
- **mor-test 配置 Deployed / UAT 部分 Verified（2026-10-14）：** 经用户要求经管理接口写入 test：标准 API `REFUND`、`REFUND_QUERY` 与接入方 `PAYERMAX` 的两个原子 API、两条流程（无镜像、SQL 或基础设施变更）。渠道沙箱验证对 UAT 运行 `UAT_REFUND_NOT_EXIST`、`UAT_REFUND_QUERY_NOT_FOUND` 均 PASSED，双向验签通过，`version=1.1` 被接受，见[交付记录](../requirements/20261014/platform-support-PayerMax退款/delivery.md)第 5 节。
- **Pending：** 已付款订单退款与查询（UAT 待人工付款，订单已在 test 下单）；本机 `PAYERMAX` 接入方未写入退款配置；提交与推送未授权。

<a id="payermax-integration-20261014"></a>

## PayerMax 收银台支付与交易查询接入（2026-10-14）

- **Designed：** [需求](../requirements/20261014/platform-support-PayerMax接入/requirement.md)与[技术方案](../requirements/20261014/platform-support-PayerMax接入/technique.md)已确认：标准 API 不按接入形态区分，用 `PAY` / `PAY_QUERY`（草案）；PayerMax 只接收银台下单与交易查询，本机联调 UAT。
- **阶段 2 Implemented / Verified（2026-10-14）：** Platform 执行引擎新增 `sealJsonBody`（锁定请求体并原样发送）、`rsaSha256Sign`、`requireRsaSha256Signature`、`nowRfc3339` 脚本函数与原子 API 可选 `decimalEncoding`（同步[原子 API 结构](../requirements/20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md)）；Operations 原子 API 编辑页增加“小数格式”。JDK 25 `./mvnw -o -pl platform-support-test -am test` **349/349**，`operations-console-web` `npm test` 982/982 与 `npm run build` 通过，详见[交付记录](../requirements/20261014/platform-support-PayerMax接入/delivery.md)。
- **阶段 3 Verified（2026-10-14，本机）：** [harness](../requirements/20261014/platform-support-PayerMax接入/assets/harness/README.md) 经 Web 代理 → Gateway → BFF → Platform 在独立库 `mor_platform_external_api` 保存 `PAY`、`PAY_QUERY` 与接入方 `PAYERMAX_MOCK` 全部配置（重跑按版本更新）；对本机合成 PayerMax（真实 SHA256withRSA）`verify_mock.py` 12 项 PASS，[回执](../requirements/20261014/platform-support-PayerMax接入/assets/stage3-receipt.json)。
- **阶段 4 Verified（2026-10-14）：** 经渠道沙箱验证对 UAT 跑通下单、查询 PENDING 与订单不存在，双向验签通过；FAIL 响应也带签名（T5 保持）。付款后 SUCCEEDED 待人工付款。未执行共享环境 SQL，未改 Vault 策略或共享环境。阶段 1～3 已按用户要求本地提交（2026-10-14），未推送。

<a id="platform-standard-api-flow-20261013"></a>

## Platform 外部 API 配置资料迁移与标准 API 流程设计（2026-10-13）

- **Designed：** 用户确认 Platform 对内提供标准 API、渠道以同步流程组合原子 API，冲正等资金反向操作不在流程中编排，见[决定](./decisions/2026-10-13-platform-standard-api-flow.md)。现行[需求](../requirements/20260914/platform-support-外部API配置与执行/requirement.md)、[六表数据设计](../requirements/20260914/platform-support-外部API配置与执行/data-design.md)和[流程设计](../requirements/20260914/platform-support-外部API配置与执行/flow-design.md)已整理；完整 `definition_json`/`flow_definition_json`、内部 HTTP Wire、材料交接与物理模型仍为 Pending。
- **资料迁移：** 原 `requirements/20260914/payment-渠道API逻辑定义/` 81 个文件迁入 `requirements/20260914/platform-support-外部API配置与执行/`，原目录仅留[迁移说明](../requirements/20260914/payment-渠道API逻辑定义/README.md)；历史 SQL/JSON/PNG/Python 内容哈希与迁移前一致，旧 DDL/DML 标注禁止执行。仓库内有效引用已更新。
- **未实现：** 没有 Java、BFF、前端或执行引擎代码；没有执行任何 SQL，没有环境、Vault 策略或权限变更；Payment 账号/MID 查询实现未改动。
- **Pending（2026-10-13 确认）：** 流程、原子 API、内部接口与[账号材料交接](../requirements/20260914/platform-support-外部API配置与执行/material-handoff.md)已确认。[Platform 读取渠道账号配置](./decisions/2026-10-13-platform-channel-account-reveal.md)的 Vault 策略**未部署**；Platform 初始化函数与 Payment “只校验不 Reveal”查询未实现。
- **模型（2026-10-13）：** 六表按[物理设计](../requirements/20260914/platform-support-外部API配置与执行/physical-design.md)进入 Platform Manifest 与 PDManer，当前完整模型 **68 表、824 字段、117 唯一索引、92 访问索引、105 关系、6 条 1:1、8 图组**；已有 62 表内容未变。生成器 --check 与 Platform 模型/Schema/场景等 56 项 Node 测试通过。[增量建表 SQL](../requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/integration-create.sql) 与模型逐列一致，并已追加到完整 Schema `platform-support-schema.sql`（46 表）；共享环境未执行，本地执行见下方 1a 条目。
- **页面与管理接口（2026-10-13）：** [页面方案](../requirements/20260914/platform-support-外部API配置与执行/page-design.md)已确认，Operations operations-console.pen 新增 24 张画板并删除 30 张前序画板（历史 PNG 保留，原稿可从 `fff35d2` 恢复）；[管理接口与 344 行注册 SQL](../requirements/20260914/platform-support-外部API配置与执行/admin-api.md) 已确认（R1～R7），注册 SQL 未执行；代码进展见下方 1a～1b 条目。
- **研发第 1 步 1a～1b（2026-10-13，Implemented / 编译与启动验证）：** 按[研发计划](../requirements/20260914/platform-support-外部API配置与执行/dev-plan-step1.md)新建业务模块 `platform-support-integration`（六表 DataObject、Mapper XML、Repository），本地 `mor_platform` 已按授权执行六表建表（测试 `init/ddl.sql` 现含正式 Schema 全部 46 表；另按授权补建 27 张原缺失表）。1b 完成标准 API 与接入方管理接口 E01～E10：`StandardApiManagementApi`、`IntegrationManagementApi` 及 DTO，字段位置/数据类型/脚本阶段 Code 发布到 `platform-support-api` 的 `api/enums`；10 个 Processor（全模块 TRY_LOCK `lock:external-api-config` + 版本 CAS），`ApiFieldValidator`、`IntegrationEndpointValidator` 静态校验，失败以 422 `VALIDATION_ERROR` + `errors[]` 返回（[管理接口](../requirements/20260914/platform-support-外部API配置与执行/admin-api.md)第 5 节已同步修订）；审计 `ConfigurationField` 新增三个摘要项。标准 API 字段变更后复核已有实现留待 1d（代码中 `TODO by CLAUDE`）。验证：JDK 25 全模块编译、应用上下文启动（含 `@Audit` 启动校验）通过；**按用户要求暂停测试，E01～E10 未运行入口测试**，`console-resource-capabilities.json` 与 BFF 未改。
- **研发第 1 步 1c（2026-10-13，Implemented / 编译与启动验证）：** 原子 API 与公共脚本管理接口 E11～E22：`IntegrationApiManagementApi`、`IntegrationScriptManagementApi` 及 DTO（`definition` 以 `JsonNode` 承载，api 模块新增 jackson-databind）；12 个 Processor（写入沿用全模块锁 + 版本 CAS，校验接口只读快照、问题以 issues 返回）。新增 `IntegrationApiDefinitionValidator`（调用参数、常量固定值、传输与路径模板变量对应、编解码、脚本阶段与参数绑定、受控句柄只能交给脚本、状态规则与 SUCCESS/FAIL 输出）、`ValueBindingValidator`（PATH/EXPRESSION/纯 FUNCTION，表达式只提取引用、不编译 Aviator）、`IntegrationScriptValidator` 与两项函数签名目录；脚本改阶段或参数时用新签名重校验引用方原子 API，被引用的原子 API、脚本不能删除；审计新增定义、参数、源码摘要与执行阶段字段。已知缺口：MVC 未开启重复属性检测，`definition` 内重复键按后者绑定；原子 API 修改后复核引用流程留待 1d（`TODO by CLAUDE`，已于 1d 完成）。未运行入口测试。
- **研发第 1 步 1d（2026-10-13，Implemented / 编译与启动验证）：** 标准 API 实现 E23～E28 与编辑器目录 E29、E30：`IntegrationImplementationManagementApi`、`ExternalApiEditorApi` 及 DTO，8 个 Processor、`IntegrationImplementationManagementService`、`ImplementationSupport`。新增 `FlowDefinitionValidator`：结构与数量预算、ID 全局唯一、初始化函数与依赖无环、节点请求键与固定值冲突及必填覆盖、调用参数与受控句柄只经 PATH 绑定 `resources.<id>.credentials.*`、出口状态与必填响应覆盖、每节点恰好一条兜底与优先级唯一、可达与无环，`steps.<id>` 引用按支配节点做必经检查；凭据在其他位置一律拒绝。标准 API 字段修改与原子 API 修改改为先复核全部受影响流程，会使任一流程失效时返回 400 并列出对象（替换两处 `TODO by CLAUDE`）。E30 枚举、上限与函数签名直接取自校验器常量与注册目录。审计对象类型 `INTEGRATION_IMPLEMENTATION`，流程只记 SHA-256 摘要。验证：三模块编译通过，`ExternalApiRepositorySmokeTest` 应用上下文启动 4/4 通过；另在 scratchpad 用合同第 8 节同形示例做过一次性手工校验（合法流程通过，非必经引用、凭据外泄、缺兜底、环、不可达与未声明输出均被拒绝），未新增或运行入口测试。
- **研发第 1 步 1e（2026-10-13，并发核对与规范自查 / 编译与启动验证）：** 全部 15 个写 Processor 均为 `AbstractLockProcessor` + TRY_LOCK `lock:external-api-config`（租约 30s 长于事务 15s 上限）+ `ExternalApiTransactions.write`，读取不加锁。数据库层保护补齐：实现创建、修改、删除改为依次 `FOR UPDATE` 锁定接入方与标准 API 行，与标准 API 修改、删除在行锁上串行（原先只读不锁，配置锁失效时可能漏过引用保护或流程复核）；原子 API、脚本写入均先锁接入方行，加锁顺序固定为接入方在前、标准 API 在后。按[写接口示例](examples/WRITE_API_EXAMPLE.md)第 11 节逐项核对：Body 在 API 接口以 `@Valid` 触发 Hibernate Validator；Controller/Converter 只做适配；Processor 不调用 Mapper，Service 不调用其他 Service；Repository 条件 SQL 校验影响行数并回写原对象；审计于提交后写入，事务内只访问本库；无业务操作身份，`expectedVersion` 冲突返回 409，不涉及原请求重放，未引入 Idempotency-Key 或旧命令幂等组件；DataObject 不出模块，View 仅用于多表查询。验证：integration、web 编译通过，`ExternalApiRepositorySmokeTest` 4/4；`npm --prefix requirements-site test` 128/137，9 项失败在回退本台账修改后同样存在，与本需求无关。**测试仍按用户要求暂停**，计划中的并发与入口 `auto` 测试未编写或运行。
- **研发第 1 步测试（2026-10-13，Implemented / 已运行）：** 用户要求补写 integration 单元测试。新增 `auto/ext/http/externalapi` 下 7 个入口测试类（标准 API 8、接入方 7、原子 API 7、公共脚本 5、实现 17、编辑器目录 4、并发 3）与纯 Java `auto/domain/FlowDefinitionValidatorTest` 5 个，全部经真实 HTTP → Processor → MySQL/Redis，权限走真实 `OperationsManagementPolicyService`（测试目录补 `EXTERNAL_API_READ/MANAGE`）。覆盖：创建/详情/列表、字段与出站地址/定义/流程静态校验的 422 字段路径及校验接口 issues、编码重复、版本 409、修改复核受影响流程与脚本引用、被引用删除保护、只读与无权限 403、配置锁占用 409 且释放后恢复、同版本并发修改与同编码并发创建只成功一个，以及菱形汇合、自引用、初始化项环等图规则。测试设施：`ExternalApiMocker` 改为经生产 Repository 插入已保存形态的夹具，六表并入 `PlatformDataCleaner` 整表回收；新增 `ExternalApiSamples`/`ExternalApiRequests` 样例与请求构造、`ExternalApiTestBase`。顺带修复既有 `OperationsAuditQueryTest` 用 `List.of` 承载 null 的测试缺陷。验证：JDK 25 `./mvnw -o -pl platform-support-test test` **307/307** 通过（本地 MySQL 13306、Redis 16379）。行为记录：`STEP_NOT_ON_ALL_PATHS` 定位到绑定本身，其余引用问题定位到 `.path`；初始化项引用自身同时报告非法引用与依赖环。
- **研发第 2 步 2a（2026-10-13，Implemented / BFF 入口测试已运行）：** 按[研发计划 · 第 2 步](../requirements/20260914/platform-support-外部API配置与执行/dev-plan-step2.md)（F1～F7 已确认）在 Operations BFF 新增外部 API 配置 30 个端点转发：6 组 Controller → `Platform*Client` Port → Impl，`service/model/externalapi` 本地 class 模型逐字段转换，`definition`/`flowDefinition` 原样透传，详情与写结果核对编码一致，不转发 `Idempotency-Key`；`ExternalApiRequestConfiguration` 严格解析写 Body（含定义内部重复键），`ExternalApiValidationConverter` 只投影本次 Body 的 422 字段路径；日志前缀加 `/external-api-admin`。Platform `console-resource-capabilities.json` 登记 `OPERATIONS_EXTERNAL_STANDARD_APIS`、`OPERATIONS_EXTERNAL_INTEGRATIONS`。注册 SQL 与机器清单的 30 条路由编码改为 `operations-external-api-*`（未执行）。既有问题处理：risk-api 2026-10-13 重构后 BFF Risk 适配无法编译，经用户同意仅适配 5 个主代码文件到新类型；BFF 测试模块 5 个 Risk 测试文件（174 处编译错误）未迁移，Maven 下整个测试模块仍无法编译。验证（在 scratchpad 复制的测试模块中排除这 5 个文件并以占位替代 Risk Simulator 运行）：`ExternalApiBrowserAutoTest` **74/74**（30 端点转发、授权拒绝零转发、Problem 透传、422 路径投影、编码不一致 502、分页窗口、扩展字段不外露、请求边界）、`OperationsHttpBodyNamingAutoTest` 15/15；全量 858 项中 19 项失败均为既有测试仍断言转发 `Idempotency-Key` 等（2026-09-17 Gateway 幂等决定后未迁移），另 1 项因复制目录无法定位 web 模块。Platform `./mvnw -o -pl platform-support-test test` **307/307**。
- **研发第 2 步 2b（2026-10-13，Implemented / 前端单元测试与构建已运行）：** Operations 前端新增 `src/externalApi/`（`externalApiModel` 类型、响应解码、字段层级与即时提示、可选标准 API、422 出错行；`FieldTable` 字段表；`externalApiHooks` 详情读取、删除确认、全量分页）与两个页面：`ExternalStandardApis`（列表筛选分页、新建 `/new`、详情与 `?mode=edit` 编辑、请求/响应字段表、删除保护、版本冲突保留草稿）、`ExternalIntegrations`（列表、新建、工作区四页签：基本信息与出站地址编辑、原子 API/公共脚本分页列表、标准 API 实现列表与“新建实现”选择）。`pageCapabilities.ts` 登记两个组件，`ConsolePage` 与 `menu.pageRoutePaths` 增加子路由；共享 `useConfigurationForm` 新增只读的 `fieldErrors`（向后兼容）。原子 API、脚本、流程三个编辑页暂显示“尚在开发中”，分别在 2c、2d 实现。验证：`npm test` **947/947**，`npm run build`（tsc + vite）通过；页面尚未在浏览器中端到端走查（依赖 2e 本机注册数据）。
- **研发第 2 步 2c（2026-10-13，Implemented / 前端单元测试与构建已运行）：** 新增原子 API 编辑页 `IntegrationApiEditor`（七页签：接口与 HTTP 传输、请求字段（固定值标记）、响应字段、调用参数与固定值、按阶段分组的有序脚本与参数取值、有序状态规则与固定“其他情况 → ERROR”、SUCCESS/FAIL 输出；路径变量与 PATH 字段双向提示）与公共脚本编辑页 `ScriptEditor`（参数、源码字节计数、按阶段可用对象与注册函数、引用方）。两页均提供“校验”（E16/E22）与保存；问题列表可定位到页签，保存失败（422 只带位置）后自动调用校验接口取得具体说明；版本冲突保留草稿。共享组件 `BindingInput`（路径/表达式/纯函数）、`ValidationPanel`、`ReasonDialog` 供 2d 复用；纯 TS `integrationApiModel`、`editorModel` 覆盖定义与编辑态互转、路径变量、固定值键、问题定位、可读路径与响应解码。验证：`npm test` **957/957**，`npm run build` 通过；流程编辑页仍为占位（2d），浏览器走查待 2e。
- **研发第 2 步 2d（2026-10-13，Implemented / 前端单元测试与构建已运行）：** 新增流程编辑 `FlowEditor`（`@xyflow/react`，沿用 Risk 决策流画布样式）：左侧初始化项、画布（开始→入口、原子 API 节点显示必填覆盖 x/y、三色返回出口、连线按出口顺序标注“优先级 · 条件”与“其他情况”）、右侧属性面板（节点请求逐字段赋值、固定值行只读、受控句柄只能选 credentials 路径、出口顺序上移/下移与执行状态快捷条件、兜底目标；出口按标准 API 响应字段赋值；初始化项函数与参数）、总预算、校验与保存、问题定位到节点/出口/连线/初始化项。纯 TS `flowModel`：解析与序列化、出口排序与重排优先级、拒绝成环的连线、删除/重命名同步、支配节点与必经变量树（非必经节点单列说明）、请求覆盖、问题定位、最长路径分层自动布局（F4：位置不保存）。验证：`npm test` **967/967**，`npm run build` 通过；浏览器走查待 2e。
- **研发第 2 步 2e（2026-10-13，本机端到端已运行）：** 按 F3 在 `127.0.0.1:13306` 新建独立库 `mor_platform_external_api`（46 表 + 初始化目录），执行[注册 SQL](../requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/registration-dml.sql)（增量 2/2/5/30/111/14/180，共 344 行）与本机[启用 SQL](../requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/local-e2e/activate.sql)（30/5/2/1），测试库 `mor_platform` 未改动。**发现并修正设计缺陷：** 18 条写/校验路由的请求体上限 262,144 超出 Gateway 绝对上限 65,536，会使整份 Gateway 快照生成失败（包括其他已启用路由）；注册 SQL、管理接口第 6 节与 BFF 严格解析上限均改为 65,536。真实链路（生产 Web 构建 → 入口代理 → Gateway → BFF → Platform，启动器见 [stage2e/harness](../requirements/20260914/platform-support-外部API配置与执行/assets/stage2e/harness/README.md)）：`verify.py` **30/30**（登录、导航菜单与按钮、能力目录、创建标准 API/接入方/脚本/原子 API/流程实现、校验接口、422 路径投影、列表、破坏性修改 400、版本 409、被引用删除 400、只读 403、未知 Query 400）；无头浏览器以管理员与只读人员走查 22 个页面，浏览器控制台错误 0，[回执](../requirements/20260914/platform-support-外部API配置与执行/assets/stage2e/e2e-receipt.json)与截图已保存。走查中优化流程连线标签（执行状态快捷条件显示为“执行状态 X”，改用曲线避免重叠）。验证后 `npm test` 967/967、`ExternalApiBrowserAutoTest` 74/74。未执行共享或线上环境 SQL，未提交。
- **Platform 测试基础设施（2026-10-13，部分完成后暂停）：** 两个人工认证配置类的 Profile 检查按授权接受 `mock`；`application-mock.yml` 开启 Operations/Client 认证、租户投影与 Redis 协调，加入合成本地密钥；新增 Vault 按用途分发 Mock、本地 SMTP Mock、整表回收与 Operations 目录/人员夹具。已运行通过 Operations 认证、会话、改密、JWKS、授权与导航、人员与角色管理共 **135** 个新入口用例及原有 104 个用例；审计查询测试已写未运行；权限/资源/Scope/凭证/网关/租户/Client 认证/文件等入口测试未开始，全服务 90% 覆盖率门禁仍未达标。
- **验证：** 受影响文档本地链接与锚点检查、JSON 语法检查、`git diff --check` 通过；`npm --prefix requirements-site test` 128/137 通过，9 项失败与迁移前相同、位于未修改的断言主题。历史检查脚本会重写报告文件，未运行。无提交、推送或部署。迁移记录见[交付记录](../requirements/20261013/platform-support-渠道API配置资料迁移/delivery.md#5-迁移执行2026-10-13)。

<a id="commerce-save-consumer-tests-20260921"></a>

## Commerce SaveConsumer 入口测试（2026-09-21）

- **Implemented：** 新增 `SaveConsumerTest` 和独立的 `SaveConsumerTransactionTest`，均以类级 `@see` 关联实际入口。复用正式应用、真实 HTTP/MySQL、已初始化订单 Fixture、Foundation 调用与响应检查、两级 BeforeEach 和全局 AfterEach / Clear。增加测试侧请求 Builder、资料保护 DTO Mocker、数据 Mocker / TestMapper 与响应/数据库 Checker，没有改生产代码、配置、依赖或 DDL。
- **场景：** HTTP 的 75 个展开用例覆盖创建后保存、旧 CREATED/PREPARING 兼容、跨订单隔离、UTC 微秒、合法长度上界、NFC / 首尾空白 / IDN 邮箱规范化、全量替换与可选字段省略/null 清空、重复 PUT、参数与 Token/状态/到期边界、法律接受锁定、保护异常/畸形结果、已有资料保护失败后重试、调用窗口的真实数据库竞争及并发保存只提交一份。响应比较正式 `SaveConsumerResponseBody`，数据库独立核对消费者及订单版本，相关 Session、商业/税务/法律/支付/退款与任务事实保持原样；CCT、Intent、Card 和取消端口不被调用。
- **事务：** 5 个独立 Service 用例验证无外层事务时两表提交、新增/替换加入外层 REQUIRED 并随外层回滚、消费者版本 CAS 及唯一约束失败时订单更新一起回滚。保护 DTO 在事务外传入 Service；Mocker 记录调用事务状态，未配置/意外 Reveal/事务内保护调用即使被业务捕获也在清理时使测试失败。
- **Verified：** JDK 25，清除 Redis 密码和 Spring JSON 环境覆盖，执行 README 所列 `./mvnw -pl paytech-commerce-test -am test '-Dtest=SaveConsumerTest,SaveConsumerTransactionTest,CheckoutIntentCreationTest' -Dsurefire.failIfNoSpecifiedTests=false jacoco:report-aggregate@report-commerce-checkout-coverage`。新增 **80/80**，已有首次支付回归 **16/16**，总计 **96/96** 通过，0 失败、错误、跳过。`SaveConsumerProcessor` 行 **39/39**、分支 **14/14**，Converter 行 **6/6**，Service 行 **7/7**，Consumer Repository 行 **20/20**、分支 **8/8**；仅为本次定向覆盖，未执行全量 auto 或全服务 `verify`。日志 `/tmp/commerce-save-consumer-tests.log`，覆盖报告 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/jacoco.xml`。
- **其他检查：** 本次 Java 空白、差异和文档路径检查通过。`npm --prefix requirements-site test` **128/137** 通过，9 项失败位于未修改的合同/模型/规范文字检查，日志 `/tmp/commerce-save-consumer-site.log`；未调整这些断言。用户原有 `CheckoutIntentCreationTest` 修改原样保留并参与回归。
- **集成边界：** 当前 `ConsumerProtectionPort` 尚未接入真实 Vault HTTP，测试只在测试模块替换外部端口并配置正式内部 DTO，不能宣称验证了真实 HTTP、加密或稳定 Token 机制。没有增加生产测试分支或伪造成功逻辑；本轮没有执行建表、提交、推送或部署。运行入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-get-refund-by-client-id-tests-20260921"></a>

## Commerce 按 Client 退款号查询测试（2026-09-21）

- **Implemented：** 新增一个 `GetRefundByClientRefundIdTest`，类级 `@see` 指向 `ClientRefundApi#getRefundByClientRefundId`。复用正式应用、真实 HTTP/MySQL、订单和退款 Builder / Mocker、`GetRefundResponseChecker` 及 `RefundQueryChecker`；不新增测试配置、响应 DTO 或外部 Mock。保持两级 BeforeEach 和公共 AfterEach / Clear。
- **场景：** 71 个展开用例覆盖全部退款状态、金额精度、微秒时刻、当前版本、更新及订单过期后重复读取；同 Client 多订单/退款定位、同编号跨 Client 隔离、未知资源、Operations 来源排除、关联订单缺失或越权；大小写、Query 保留字符及单次解码、1/128/129 长度、非法字符和可信身份边界，以及持久化事实损坏。每次请求均比较两侧 Client 的订单、关联事实、退款和任务全列快照，并断言相关 CCT/Payment 端口没有调用。
- **失败证据与修复：** 首次运行 **69/71** 通过，USD `0.001` 与币种 `ZZZ` 两个负例复现 HTTP 400 而非预期 500，日志 `/tmp/commerce-get-refund-by-client-id-red.log`。按开发规范的本库错误分类及已修复的公开退款号入口，在 `GetRefundByClientRefundIdProcessor` 的 Money 转换处仅捕获 `InvalidParameterException`，转换为保留 Cause 的 `ErrorException`。没有重复解析、增加校验器或放宽测试；初始技术说明的“不捕获”限制由[故障分类修订](../requirements/20260916/commerce-GetRefundByClientRefundId接口重构/technique.md#2026-09-21-金额故障分类修订)取代。
- **Verified：** JDK 25、清除 Redis 密码及 Spring JSON 环境覆盖后，执行 `./mvnw -pl paytech-commerce-test -am test '-Dtest=GetRefundByClientRefundIdTest,GetRefundTest' -Dsurefire.failIfNoSpecifiedTests=false jacoco:report-aggregate@report-commerce-checkout-coverage`，新增入口 **71/71**、公开退款号回归 **49/49**，共 **120/120** 通过，0 失败、错误、跳过。新入口 Processor 行 **20/20**、分支 **4/4**，共用 Converter 行 **7/7**；不代表全服务覆盖率达标，本次未执行全量 auto 或 `verify`。日志 `/tmp/commerce-get-refund-by-client-id-tests.log`，报告 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/jacoco.xml`。
- **其他检查：** 新增 Java 空白、差异及文档链接核对通过。`npm --prefix requirements-site test` **129/137** 通过，8 项既有合同、模型和规范文字断言仍失败，日志 `/tmp/commerce-get-refund-by-client-id-site.log`；未放宽断言或宣称全仓库门禁通过。
- **交付边界：** API、Controller/Converter、查询归属及按需 Repository 不变；纯查询无 Context、写事务、锁或提交后步骤，也无请求 Key 消费。生产仅修正上述金额异常分类；未改 Foundation、数据库结构、DDL、POM、配置或覆盖率阈值，未提交、推送或部署。运行入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-get-refundable-amount-tests-20260921"></a>

## Commerce 可退款金额查询测试（2026-09-21）

- **Implemented：** 为 `ClientRefundableAmountApi#getRefundableAmount` 新增一个 `GetRefundableAmountTest`，通过真实 HTTP、正式应用及本地 MySQL 查询。复用订单 Builder / Mocker、退款数据 Mocker 和 `RefundQueryChecker`；仅补充测试侧退款位置更新方法，模拟两次查询间已提交的变更。响应 Checker 使用正式 `GetRefundableAmountResponseBody` getter 和 DTO 回序列化比较，金额期望独立给定，不维护 JSON 字段清单。
- **覆盖与生命周期：** 57 个参数化展开用例覆盖基数减预占及已退、零额、USD/JPY/KWD 精度和大额、各订单状态、Checkout / Direct Pay 无 Session、位置时点和版本、过期及更新后的重复读取、退款申请不重复扣减、Client 归属、标识与身份参数、位置缺失及损坏事实。每次 HTTP 查询比较两个自有 Client 的订单、关联投影、退款及任务全列快照，并检查 CCT、商业准备、Intent 创建、支付提交和终止端口零调用。保留两级 BeforeEach 和公共 AfterEach / Clear，没有新增启动配置或外部 Mock。
- **Verified：** JDK 25、清除 Redis 密码和 Spring JSON 环境覆盖后执行 `./mvnw -pl paytech-commerce-test -am test -Dtest=GetRefundableAmountTest -Dsurefire.failIfNoSpecifiedTests=false jacoco:report-aggregate@report-commerce-checkout-coverage`，**57/57** 通过，0 失败、错误、跳过。报告中 `GetRefundableAmountProcessor` 行 **15/15**、分支 **6/6**，Converter 行 **3/3**；仅表示本次入口覆盖，未执行全量 auto 或全服务覆盖率 `verify`。日志 `/tmp/commerce-get-refundable-amount-tests.log`，报告 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/jacoco.xml`。
- **检查与边界：** Java 差异空白及 README 链接检查通过。`npm --prefix requirements-site test` **129/137** 通过，8 项既有合同、模型和规范文字断言仍失败，日志 `/tmp/commerce-get-refundable-amount-site.log`。未修改生产实现、依赖、配置、DDL 或覆盖率门槛；既有数据库表满足测试所需，未执行建表、提交、推送或部署。

<a id="commerce-cancel-without-create-20260921"></a>

## Commerce 取消不再创建 Intent（2026-09-21）

- **Implemented：** 按用户纠正删除 `CancelOrderProcessor.recoverIntent` 及其唯一使用的 `CheckoutIntentService / Impl`，取消不再读取冻结商业上下文、调用创建或补写 Payment 投影。Commerce 本地 `PaymentTerminationClient` 使用 Client + Order 身份定位，移除必需 Intent ID 和派生请求 Key。先提交终止标记，再事务外取消；确定 TERMINATED 或业务 `NOT_FOUND` 后，原 Service 在一个 REQUIRED 事务取消 Order / 可选 Session。PENDING / 不可用仍返回 202，其他错误不按不存在处理。
- **并发与持久化：** 保留订单版本、未支付状态、已有投影版本或无投影的条件保护。`localOnly` 仅让未经 Payment 确认的本地取消继续要求无接受和无历史准备标识；远端已确定后允许有接受、无投影的订单关闭。提交支付的既有绑定/CAS 事务不改：晚到创建响应因终止标记与版本条件失败，关联一起回滚，不执行卡支付。没有新表、字段、锁、幂等记录或补偿任务。
- **测试：** 先修改“创建超时后取消不再创建”的真实 HTTP 回归，旧实现因再次调用不可用的创建端口而返回 202，测试失败，日志 `/tmp/commerce-cancel-no-create-red.log`。修改后取消、独立事务及首次支付交错定向 **69/69** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-cancel-no-create-tests.log`。测试侧 `PaymentTerminationMocker` 按订单配置 DTO / 异常，检查调用身份、次数、事务边界和意外调用，参与逐例 Clear；替换前序纯观测 Probe，不改生产行为来注入测试故障。
- **完整回归：** JDK 25、清除 Redis 密码及 Spring JSON 环境覆盖后执行 `./mvnw -pl paytech-commerce-test -am clean verify`，测试阶段 **497/497** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-cancel-no-create-verify.log`。最终仍在既有 90% 行覆盖率门禁失败，本次 **1470/2016（72.92%）**，未调整阈值或排除生产类。Mapper XML 与差异空白检查通过。`npm --prefix requirements-site test` **129/137** 通过，原有 8 项合同/模型/规范文字断言失败保持，日志 `/tmp/commerce-cancel-no-create-site.log`；不能声明全仓库门禁通过。
- **集成边界：** 真实 Payment 当前仍只有按 Intent ID 终止的 HTTP 合同；按来源订单取消及错误映射是待接 Owner 能力。本轮仅改 Commerce 端口与原已获准的固定 Mock，未改 Payment 服务。NOT_FOUND 只表示本次业务查询不存在，不能承诺先前超时创建永不完成；晚到未支付 Intent 的远端处理及真实终止竞争仍待 Payment 联调。不得将路由 404、超时或任意业务错误视为不存在。
- **范围：** 同步 [Commerce 技术方案](../requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md#取消) 与 README，保留前序测试记录作为旧流程证据。外部 API Body/路径未改，无需新增协议 DTO；未执行 DDL、提交、推送或部署。

<a id="commerce-cancel-order-tests-20260921"></a>

## Commerce CancelOrder 入口测试（2026-09-21）

本节是取消恢复流程调整前的验证记录；现行行为及后续验证见[取消不再创建 Intent](#commerce-cancel-without-create-20260921)。

- **Implemented：** 新增一个 `CancelOrderTest`，类级 `@see` 指向 `ClientOrderApi#cancelOrder`；另以 `OrderCancellationTransactionTest` 验证公开 Service 的 REQUIRED 传播及 CAS 回滚。复用正式应用、真实 HTTP、本地 MySQL、订单 Fixture、两级 BeforeEach 和全局 AfterEach。新增测试侧取消数据 Mocker / TestMapper、正式 ResponseBody Checker 和数据库 Checker，没有新增生产测试分支、运行配置或数据库结构。
- **场景：** Checkout / Direct Pay、本地取消、已有 Intent 的终止、终态重提保留原时间、历史准备待定、支付接受后丢失 Intent 关联的恢复及不可用后重试、错误 Intent 归属、支付成功拒绝取消、到期终态、损坏事实、Client 隔离及 HTTP 参数边界。事务入口单独验证外层回滚、会话最后一步 CAS 失败、支付投影版本竞争和订单旧版本拒绝。每次 HTTP 调用检查无关资金、法律接受、网络证据、退款和任务事实不变。
- **外部边界：** `PaymentTerminationProbe` 仅记录当前终止 Client 的实际调用参数、次数及事务状态，始终执行原 Bean，不改写响应；记录参与全局 Clear。Intent 恢复沿用已有测试侧端口 DTO Mocker，验证恢复失败后保留已提交终止意图、重提沿原订单业务身份继续。当前生产终止 Client 仍固定返回 `TERMINATED`，真实 Payment HTTP、终止超时/待定/拒绝及远端 Intent 唯一性不在本次证明范围。
- **Verified：** JDK 25 定向 **49/49**（HTTP 44、事务 5）通过，日志 `/tmp/commerce-cancel-order-tests.log`。清除 Redis 密码及 Spring JSON 环境覆盖后，`./mvnw -pl paytech-commerce-test -am clean verify` 的测试阶段 **493/493** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-cancel-order-regression.log`；包括现有首次支付、创建、查询和退款的回归。
- **Pending：** 完整 `verify` 仍被既有 90% 聚合行覆盖率门禁拦截，本次 **1493/2042（73.11%）**，未降低门槛或排除生产类。需求站检查 **129/137** 通过，8 项失败仍涉及 Checkout 展示、Payment 退款合同、十路由、15 表模型/台账/隐私导航和测试/写接口规范文字断言；日志 `/tmp/commerce-cancel-order-site-tests.log`。不宣称全仓库门禁或真实 Owner 联调完成。
- **变更边界：** 仅增加测试并更新 Commerce README、本台账；`recoverIntent` 及其他生产逻辑保持原样。未执行 DDL、修改 Foundation、提交、推送或部署。运行入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-get-order-by-client-id-tests-20260921"></a>

## Commerce GetOrderByClientOrderId 入口测试（2026-09-21）

- **Implemented：** 新增一个 `GetOrderByClientOrderIdTest`，类级 `@see` 指向 `ClientOrderApi#getOrderByClientOrderId`。复用正式应用、真实 HTTP、本地 MySQL、两级 BeforeEach 和全局 AfterEach；沿用 GetOrder 的 Builder、Mocker、数据库只读快照及正式 ResponseBody Checker，没有新增配置或生产替身。
- **场景：** Checkout / 无 Session 的 Direct Pay、支付及退款摘要、Client 内精确定位、不同 Client 同编号隔离、未知与越权统一 404、禁止回退到 orderNo、大小写、1/128/129 长度边界、Query 保留字符编码、缺失/空白/非 ASCII/控制字符、可信身份头、到期重复查询及必需投影缺失。每次请求比较两个测试 Client 下订单及关联表快照，并断言 CCT 预检和商业初始化零调用。HTTP 调用使用已自动装配的 RestTestClient URI 变量，不拼接或预先展开 Query 值。
- **Verified：** JDK 25 下 `./mvnw -pl paytech-commerce-test -am test -Dtest=GetOrderByClientOrderIdTest -Dsurefire.failIfNoSpecifiedTests=false` **45/45** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-get-order-by-client-id-tests.log`。本次未重复执行全量 Java 测试或覆盖率 `verify`，不变更既有覆盖率门禁结论。新增源码及 Commerce 差异空白检查通过。
- **后续 Mock 启动修复：** 用户报告 IDE 启动 `WRONGPASS` 后，核实上述验证曾额外注入 Redis 密码，原 Mock 默认值未匹配本地容器。按用户明确授权，将 `mor-local-redis-1`（仅 `127.0.0.1:16379`）的本地密码写入 `application-mock.yml` 默认值，保留显式环境变量覆盖并同步 README。清除 `COMMERCE_TEST_REDIS_PASSWORD`、`MOR_REDIS_PASSWORD`、`SPRING_DATA_REDIS_PASSWORD` 和 `SPRING_APPLICATION_JSON` 后直接运行同一定向命令，**45/45** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-mock-redis-default-tests.log`。此次补充仅调整测试配置，不修改 Redis 容器密码或生产配置；取代先前“自动测试必须额外注入密码”的操作说明。
- **其他检查：** `npm --prefix requirements-site test` **129/137** 通过，8 项失败涉及 Checkout 展示、Payment 退款合同、十路由、15 表模型/台账/隐私导航以及测试/写接口规范文字断言；这些失败指向本次未修改的合同或已有模型台账内容，未调整其断言。日志 `/tmp/commerce-get-order-by-client-id-site-tests.log`。
- **边界：** 仅新增测试并更新 Commerce README、本台账。手工启动已有且停止的 `mor-local-mysql-1` 和 `mor-local-redis-1`，测试只准备/清理自有数据；现有本地 Redis 密码经环境变量注入，不输出或写入仓库。未修改生产代码、API、Foundation、表结构、DDL、POM 或配置；未运行 manual、真实 Owner 联调、提交、推送或部署。运行入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="operations-notification-admin-20261015"></a>

<a id="platform-ses-delivery-events-20261017"></a>
## Platform 邮件投递事件（Amazon SES，2026-10-17，已推送 `762b785`、`b59ec6b`，mor-test 已部署）

- **范围：** SES Configuration Set → SNS → SQS，Platform 各实例以 Spring 定时任务按账号拉取 `t_notification_provider.event_queue_url`（复用账号发送密钥，不新增 Vault 规则），按 EmailTag `ses-<attempt_id>` 回写发送尝试：送达 → 投递已送达；硬退信 / 拒发 → 失败；软退信与投诉只记录；结果只升不降，结果未知的尝试经事件确认为已受理（`PROVIDER_EVENT`）；旧尝试送达也标已送达。认证邮件与测试发送的事件对不上尝试，只计数后删除。账号运行状态（最近拉取 / 错误 / 最近事件）不推进版本、不记审计；队列 URL 修改记审计。取代 Delivery 规划"不接回调"中的事件部分（拉取而非公网回调）。见[需求](../requirements/20261017/platform-support-SES投递事件/requirement.md)、[分析](../requirements/20261017/platform-support-SES投递事件/analysis.md)、[方案](../requirements/20261017/platform-support-SES投递事件/technique.md)（第 7 节为研发偏差）。
- **数据库：** `t_notification_provider` +4 列、`t_notification_attempt` +5 列（[ddl.sql](../requirements/20261017/platform-support-SES投递事件/db/platform-support-service/ddl.sql)，PDManer 与 Schema 已同步）；本机 `mor_platform`、`mor_platform_e2e` 与 mor-test `mor_platform` 已执行。
- **配置：** `notification.ses.events.enabled`（默认 false，且要求 `notification.enabled` 与 `notification.ses.enabled` 同时为 true）；间隔 60 秒、长轮询 10 秒、每轮最多 5 批。
- **兼容：** Operations BFF 严格解析 Platform 通知响应，Platform 与 BFF 必须同批发布。
- **验证：** Platform 全量 698/698（全服务聚合覆盖率门禁为既有未达标）；Delivery 通知类 96.05% / 89.33%（基线 95.96% / 89.74%，分支低 0.41 个百分点，用户未要求补齐）；BFF 1378/1378；Web 1060/1060（mor-test 构建时 1068/1068）。本机接口 20/20、浏览器 14/14；mor-test 真实链路 8/8（3 个 SES 模拟地址：送达、硬退信、投诉回写），页面 5/5。详见[交付记录](../requirements/20261017/platform-support-SES投递事件/delivery.md)与[发布记录](../requirements/20261017/platform-support-SES投递事件/release/release.md)。
- **mor-test（2026-10-17）：** BFF / Web 为本需求镜像 `6cf93653…` / `26be835c…`（构建自 `6461091`）；Platform 后由 Client Console 会话换为 `58dcc90b…`（`f7b5500`，含本需求）；Compose `NOTIFICATION_SES_EVENTS_ENABLED: "true"`；`SES_PRIMARY` 已配置队列 `mor-test-ses-events`（v5）。AWS（ap-southeast-1）：SQS 队列 + 死信队列、SNS 主题（Raw delivery）、Configuration Set `mor-test-events` 为 `payorigo.com` 默认、IAM 增加 SQS 接收 / 删除权限，且发信策略须同时放行 `configuration-set/mor-test-events`（否则全部发信被拒）。
- **未完成 / 前提：** SES 账户仍在沙箱（普通收件人收不到）；生产环境的 AWS 资源与发信策略需按发布清单第 3 节另行配置；无专门的事件拉取监控面板（只有指标）。

## Operations 通知发送账号维护（2026-10-15，已本地提交 `0301719`，mor-test 已部署）

- **范围：** 发送账号的类型、区域、发件地址、Configuration Set 与密钥 Vault Token 入库（`t_notification_provider` +6 列），运营后台新建 / 编辑 / 录入或更换密钥（只写不读，Platform 以 `NOTIFICATION_PROVIDER_SETUP` Protect）/ 测试发送；适配器按类型注册、SES 客户端按区域复用，删除默认凭据链；部署配置只保留 `notification.ses.enabled`、`endpoint` 与超时。见[需求](../requirements/20261015/operations-console-通知发送账号维护/requirement.md)、[方案](../requirements/20261015/operations-console-通知发送账号维护/technique.md)、[Vault 决定](./decisions/2026-10-15-platform-notification-credential-protect.md)。
- **验证：** Platform `verify` 619/619（隔离工作区；全服务聚合覆盖率门禁仍为既有未达标）、Delivery 覆盖率 95.96% / 89.74%；BFF 47/47；Web 1025/1025、build 通过。详见[交付记录](../requirements/20261015/operations-console-通知发送账号维护/delivery.md)。
- **本机：** `mor_platform`（auto）与 `mor_platform_e2e` 已执行 DDL；e2e 另执行注册块 56 行与[本机启用](../requirements/20261015/operations-console-通知发送账号维护/db/platform-support-service/activation-local.sql)；本机 Vault 已加 PROTECT 规则（策略 16）。经运营入口接口验收 15/15、浏览器 11/11，验收数据已清理。
- **mor-test（2026-10-16）：** Platform / BFF / Web 为 `0301719` 镜像（`070ccc41…` / `ae568bd0…` / `6854f890…`），Compose 开启 `NOTIFICATION_SES_ENABLED`；`mor_platform` 执行 DDL、`SES_PRIMARY` 补类型与区域、注册 56 行与 [test 启用](../requirements/20261015/operations-console-通知发送账号维护/release/test-activate.sql)；Vault 加 PROTECT 规则（策略 12）。接口 14/14、浏览器 11/11、回归 28 页，验收数据已清理，见[发布记录](../requirements/20261015/operations-console-通知发送账号维护/release/release.md)。`SES_PRIMARY` 尚无发件地址与密钥，录入前认证邮件仍发送失败。
- **未完成 / 前提：** 在页面录入 `SES_PRIMARY` 的真实发件地址与密钥并完成真实测试发送；未推送。

## Operations 通知管理（2026-10-15，已本地提交，mor-test 已部署）

- **确认范围：** 见[需求](../requirements/20261015/operations-console-通知管理/requirement.md)、[现状分析](../requirements/20261015/operations-console-通知管理/analysis.md)与[方案](../requirements/20261015/operations-console-通知管理/technique.md)；Pencil 10 个画板在 `operations-console-web/docs/operations-console.pen`（「通知管理 ·」前缀）。
- **Implemented：** Platform `NotificationManagementApi`（`/notification-admin/v1`，12 个接口）、Web 管理 Processor（鉴权、锁、事务、`ConfigurationChanged` 审计）、Delivery `NotificationManagementService` / `NotificationRecordQueryService`；7 个通知仓储不再依赖 `notification.enabled`；审计字段新增 `LOCALE`、`SUBJECT_DIGEST`、`BODY_DIGEST`、`PRIMARY_PROVIDER`、`FALLBACK_PROVIDER`。BFF `NotificationManagementController` 简单转发；Web 新增通知模板、发送账号、发送记录 3 个页面。
- **Verified：** Platform `./mvnw -o clean verify` **617/617**（全服务 90% 聚合门禁仍为既有失败 0.54/0.37）；Delivery 模块指令 96.78%、分支 90.83%（改动前 96.39%/90.49%）。BFF `clean verify` **1331/1331** 通过。Web `npm test` 1019/1019、`npm run build` 通过。
- **本机全链路（阶段 5）：** `mor_platform_e2e` 已执行索引 DDL、注册 DML（160 行，ID `900000000000086xxx`～`89xxx`）与 [本机启用 SQL](../requirements/20261015/operations-console-通知管理/db/platform-support-service/activation-local.sql)；本机 `run-service.sh` 的 Platform 改为 `--notification.enabled=true`（删除 SMTP 参数）。经运营入口接口验收 22/22、浏览器验收 16/16，验收数据已清理。
- **mor-test（阶段 7）：** Platform 升级到 `f84a012`（镜像 `493026bf…`，Compose 去掉 SMTP、开启通知）；BFF 为 `994d5b0` 镜像（Vault 值格式需求发布，已含本需求），Web 为 `88a7807`（预览改为按钮 + 弹窗，镜像 `e5df9ddc…`）；`mor_platform` 执行索引、注册 160 行、认证邮件初始化块与 [test 启用](../requirements/20261015/operations-console-通知管理/release/test-activate.sql)；验收接口 22/22、浏览器 16/16、回归 28 页，验收数据已清理，见[发布记录](../requirements/20261015/operations-console-通知管理/release/release.md)。test 注册验证与找回密码邮件不再进入 Mailpit（未配置 SES）。
- **未完成 / 前提：** 其他环境未执行 DDL / DML；本机与 test 均未配置 SES，未真实发信；`operations-console.pen` 未保存未提交；未推送。

<a id="platform-authentication-email-20261015"></a>

## Platform Client 认证邮件接入 Delivery（2026-10-15，已提交，未推送）

- **确认范围：** 见[需求](../requirements/20261015/platform-support-认证邮件接入Delivery/requirement.md)与[方案](../requirements/20261015/platform-support-认证邮件接入Delivery/technique.md)：Client 注册验证、重新发送验证、密码重置邮件改经 Delivery 模板与 SES 发送账号即时发送；不留发送记录；内容只做 `zh-CN`；删除 SMTP。
- **Implemented：** Delivery 新增 `SendTransientEmailProcessor`（无事务、不写库、不重试、只用主账号）、`NotificationConfigurationService`（与普通通知共用配置检查）、`notification.transient-template-codes`（普通通知提交与 HTTP 白名单不能使用，白名单冲突启动失败）。Web 以 `DeliveryAuthenticationEmailSender` 取代 SMTP 适配器，失败计 `client.authentication.email.delivery{result=failed,reason}` 并保持原受理响应；Client 认证开启要求 `notification.enabled=true`。删除 `spring-boot-starter-mail`、`spring.mail.*`、`client.authentication.mail-from`。初始化 DML 末尾追加认证模板、`SES_PRIMARY` 账号与路由独立块，**未执行**。
- **Verified：** JDK 25 全服务 `./mvnw -o verify` 测试 **589/589**；三个认证 HTTP 入口经真实 SES 适配器与测试 SES 端点验证邮件内容、链接可完成激活与重置、四类失败仍 202 且按原因计指标、通知表与补偿任务无记录、日志无秘密与邮箱。Delivery 指令 **96.39%**、分支 **90.49%**（改动前 96.30% / 90.34%）。全服务 90% 聚合门禁仍为既有失败（0.52 / 0.36），未降低门槛。`requirements-site` 128/137，9 项失败与改动前相同。
- **未完成 / 前提：** 阶段 5 已完成 `clean verify` 与制品检查（exec jar 无邮件依赖与 SMTP 类，打包配置含即时模板）；未真实发信；上线需执行 DML 块、设置 `NOTIFICATION_SES_PROVIDER_CODE=SES_PRIMARY`、开启 Client 认证时同时开启 `notification.enabled`，并满足 SES 接入的上线前提。已提交到本地 main，未推送、未部署、未执行 SQL。

<a id="platform-notification-http-20261015"></a>

## Platform 通知内部 HTTP 接口（2026-10-15，已本地提交）

- **确认范围：** 见[需求](../requirements/20261015/platform-support-通知内部HTTP接口/requirement.md)与[方案](../requirements/20261015/platform-support-通知内部HTTP接口/technique.md)：为内部业务系统开放 PLT-I09 提交接口 `POST /platform-support/notifications/v1/requests`，沿用 Vault V1 过渡信任（私网 + `X-Caller-Service` + 部署配置白名单），取代 Delivery 规划"等强服务身份后开放"的决定；`source_system` 直接保存调用方服务名。
- **Implemented：** `platform-support-api` 新增 `NotificationCommandApi` 与请求/响应 Body；Web 新增 `NotificationController`、`NotificationConverter`、`NotificationCallerProperties`（`notification.http.callers`，默认空即全部拒绝）和 `NotificationRequestConfiguration`（Body 1 MB 上限，解析错误不回显内容）；`/notifications` 加入只记元数据的日志前缀；模块请求 `sourceSystem` 改为服务名格式。无 DDL/DML，未注册 Gateway 路由。
- **Verified：** JDK 25 全服务 `./mvnw -o verify` 测试 **543/543** 通过；`NotificationHttpApiTest` 9 例经真实 HTTP 覆盖放行、未授权调用方/模板、Body 中 `sourceSystem` 被忽略、409/跨调用方同 key、422/400/413、模板停用、Vault 不可达 503（`DEPENDENCY_UNAVAILABLE`），并断言响应与日志不含邮箱和参数值；`NotificationCallerPropertiesTest` 7 例。Delivery 模块指令 **96.30%**、分支 **90.34%**；新增 Web 类指令 95.35%、分支 96.88%。全服务 90% 聚合门禁仍为既有失败（指令 0.50、分支 0.33），未降低门槛。`requirements-site` 128/137，9 项失败与改动前相同。
- **未完成 / 前提：** 没有业务系统接入调用；无状态查询接口；`X-Caller-Service` 可伪造，部署须保证 Platform 只在私网可达；上线前在部署配置登记调用方与模板。阶段 5：Reactor 打包与 exec/API JAR 内容检查通过，API JAR 无新增依赖。2026-10-15 已本地提交（未推送），未部署。

<a id="platform-notification-ses-20261014"></a>

## Platform 普通邮件接入 Amazon SES（2026-10-14；2026-10-15 密钥管理变更，已本地提交）

- **确认范围：** 见[需求](../requirements/20261014/platform-support-邮件AmazonSES接入/requirement.md)与[方案](../requirements/20261014/platform-support-邮件AmazonSES接入/technique.md)：首个真实 EmailProvider 为 SES v2；普通通知改为至少发一次；不改表、无 SQL、无 HTTP 接口。
- **Implemented：** Delivery 模块新增 `integration/provider/ses`（Properties/Configuration/Provider，关闭 SDK 重试、十类错误映射）；`EmailProvider.supportsQuery()`；Handler 对不支持查询的 Provider 受理即完成、UNKNOWN 在 `notification.unknown-resend-delay`（默认 10m）后于次数内重发、次数用尽终止为 `NOTIFICATION_UNKNOWN_EXHAUSTED`；当前尝试改按最新 Attempt 读取。`compensation.enabled` 改为 `${PLATFORM_SUPPORT_COMPENSATION_ENABLED:${files.onboarding.enabled:false}}`。正式默认 `notification.ses.enabled=false`。
- **Verified：** JDK 25 全服务 `./mvnw -o verify` 测试 **518/518** 通过（0 失败/错误/跳过）。其中通知相关 6 类 **114** 例：SES 经真实 SDK 访问 `SesMockController`；Delivery 模块指令 **96.61%（2678/2772）**、分支 **90.95%（362/398）**。全服务 90% 聚合门禁仍失败（指令 0.49、分支 0.33），为既有缺口，未降低门槛或排除生产类。manual `SesRealSendTest` 已验证 `-Pmanual-test` 能选中并在缺环境变量时失败提示，**未连真实 SES 执行**。
- **2026-10-15 密钥管理变更：** IAM 用户密钥对存 Vault（`NOTIFICATION_PROVIDER_CREDENTIAL_V1`），配置只放 Token `notification.ses.credential-token`，删除 access-key/secret-key；`EmailProvider.prepare()` 在登记尝试前以 `NOTIFICATION_DELIVERY` Reveal、不缓存、只给该次请求签名；凭据类 SES 错误改为可重试 `SES_CREDENTIAL_REJECTED`。全服务 `./mvnw -o verify` **527/527** 通过；通知 6 类 **123** 例；Delivery 指令 **96.30%**、分支 **90.34%**；全服务门禁仍为既有失败（0.49/0.33）。**Vault 策略尚未变更**（数据类型与规则清单见方案 §8.1），test/生产环境未配置。
- **未完成 / 前提：** 未接 SES 投递事件回调；无正式模板/账号/路由 DML；无业务调用方或 HTTP 入口；认证邮件未迁移。上线需 AWS 账号、`me-central-1` 已验证发件域名、解除沙箱、`ses:SendEmail` 权限、Vault `PLATFORM_NOTIFICATION_EMAIL_V1` 授权及 XXL-Job 扫描调度。2026-10-15 已本地提交（未推送），未部署。

<a id="platform-delivery-domain-plan-20260920"></a>

## Platform Delivery 普通 Email 首期（2026-09-21）

- **确认范围：** 用户要求开始实现，并确认重复规范化邮箱整单拒绝；普通 Email 先行，认证邮件对接后续；只开放模块内调用，HTTP 等强服务身份后开放。邮件 Provider 仍先 Mock，SMS、真实供应商、Operations 管理、查询和 Webhook 不在本期。现行[需求](../requirements/20260920/platform-support-Delivery领域规划/requirement.md)、[执行合同](../requirements/20260920/platform-support-Delivery领域规划/technique.md)、[数据设计](../requirements/20260920/platform-support-Delivery领域规划/data-design.md)、[模块 Demo](../requirements/20260920/platform-support-Delivery领域规划/api.md)及[确认记录](../requirements/20260920/platform-support-Delivery领域规划/delivery.md)已同步。
- **Implemented：** 新增 platform-support-delivery；SubmitNotificationProcessor 继承公共 AbstractProcessor，基础校验在父类触发，完整 SubmitNotificationContext 保存 Request 与全部 Delivery。NotificationAcceptanceService 原子保存通知事实和空 Payload 的公共补偿任务；NotificationService 只做本地读取/状态事务；NotificationDeliveryHandler 在事务外 Reveal/发送/查询。每个收件人一封邮件，默认值按收件人覆盖；来源业务唯一键只阻止重复创建，不引入 HTTP 请求重放。UNKNOWN 查询原账号和请求号、不增 attempt_count、不切备用；明确失败才允许新尝试。Delivery version/status CAS 保护 Attempt 与投递写回，旧执行者冲突映射为可恢复任务结果。
- **配置与接口边界：** 正式默认 notification.enabled=false；无生产 Mock 或 Notification HTTP Controller。测试模块提供两个合成账号适配器，Vault 使用正式 Client 和独立 HTTP Mock；新 Purpose PLATFORM_NOTIFICATION_EMAIL_V1 尚未配置真实授权，不复用登录用途。生产开启需配合公共扫描调度、唯一 Worker 和真实适配器。Access Control 现有认证邮件未迁移。
- **模型与 SQL：** 七表、65 字段按字段清单进入 Manifest、PDManer、正式 Schema 与测试 DDL；Provider 请求号唯一键保持 (provider_request_no, provider_id)，Delivery 唯一键为 (request_id, address_vault_token)。当前完整模型 **62 表、760 字段、111 唯一索引、91 访问索引、101 关系、6 条 1:1、7 图组**；生成器 --check 与 53 项 Platform 模型/Schema/场景测试通过。Notification 不再包含 Callback/Suppression、历史版本、Hash 或领域 Lease 字段；Document 关联租户校验后续沿 Delivery → Request，未实现 Document 通知对接。
- **本地 SQL 已执行：** 经用户分别明确授权，在 local-environment 的 **127.0.0.1:13306/mor_platform** 执行[七表 CREATE 脚本](../requirements/20260920/platform-support-Delivery领域规划/db/notification-create.sql)，并执行既有正式 Schema 的 t_compensation_event CREATE 定义。执行前核对同名表不存在，不 ALTER/DROP 既有表，不操作 mor-test 或生产。七表字段名称、顺序、非空和完整注释已与 PDManer 复核一致；测试没有自动建表或持久化配置 Seed。
- **Verified：** JDK 25 全服务 `./mvnw test` **100/100** 通过，0 失败、错误、跳过，包含本次 **20** 个通知用例与既有 **80** 个测试。通知覆盖独立收件人、默认/覆盖、重复地址与来源冲突、父类校验、已有事务拒绝、第二条投递冲突导致整单/补偿任务回滚、原发送查询、账号停用后的查询、UNKNOWN 不重发、SENT 保留、当前文案重试/不兼容拒绝、发送上限、主备切换、终态重入及旧 Worker 晚回写。日志 `/tmp/platform-notification-final-tests.log`；静态测试 `/tmp/platform-notification-static-tests.log`。需求文档 39 个相对路径、4 个 JSON 示例、围栏和差异空白检查通过。
- **Pending / 覆盖率门禁：** 完整 `clean verify` 的 98 个当时用例均通过，但既有全服务 90% 聚合门禁失败；补充两例后再次 `test` 100/100 通过，再执行 `-DskipTests verify` 刷新报告，门禁仍失败。当前全服务指令 **4971/57794（8.60%）**、分支 **319/5179（6.16%）**；新增 Delivery 指令 **1866/2069（90.19%）**、分支 **184/275（66.91%）**，分支仍低于目标。没有降低门槛或排除生产类。报告 `platform-support-service/platform-support-test/target/site/jacoco-phase-a/jacoco.xml`，日志 `/tmp/platform-notification-final-coverage.log`。不宣称全服务门禁、真实 Provider 协议、真实 Vault 权限或部署验收通过。
- **交付自查与环境：** API/Controller/Converter、HTTP 身份和 Gateway 幂等项因模块内入口不适用；新 Request 使用唯一键，异步执行使用公共 Claim/Lease 与本地 CAS，不叠加 Redis 锁。Repository 按表维护、SQL 成功后同步对象，Service 不含外部调用，无 DataObject 跨模块合同。用户授权的 .env.local 只用于本地连接且不输出秘密；测试仅准备/清理自身来源与预留配置，真实异步结束后才清理。未提交、推送、发布或部署。
- **历史：** 2026-09-20 仅完成五份需求文档及静态校验，不代表当时已实现；前序九表模型被本次 Notification 局部后继取代，其他领域保持原边界。

<a id="commerce-payment-order-lock-20260920"></a>

## Commerce 支付订单锁与 Intent 原子绑定（2026-09-20）

- **Implemented：** `SubmitCustomerPaymentProcessor` 继承 Foundation `AbstractLockProcessor`，使用订单资源 `order:{orderId}` 的 TRY_LOCK（等待 0、Watchdog 续租）。锁前按 Token 只查询不可变订单主键，锁内按需加载状态；公开 Token 不作为 Redis Key。同订单并发在法律接受/外部调用前返回 409，锁覆盖本次同步流程。引入 Foundation Redis Starter 和按需协调配置，POM 只放行对应 Starter/客户端依赖，保留其他禁止项；不复制锁实现或增加环境分支。
- **事务调整：** 外部创建 Intent 后只构建本次 Context 的投影；`CheckoutSubmissionService.bindPayment` 在一个 REQUIRED 事务内插入关联并执行订单 CAS。取消、期限、版本或唯一性冲突时一起回滚，不继续调用卡支付。创建前已提交的三份法律接受保留，后续按原 Order 业务身份找回原 Intent。独立 `confirmPayment` 删除；Cancel 的原 Intent 绑定仍保留，它与外部终止之间存在必要事务边界。Cancel/Save Consumer 未改为 Redis 锁，继续保留原 CAS；Redis 不替代数据库和 Payment Owner 的保护。设计修订见[技术方案](../requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md#2026-09-20-锁和本地原子性修订)。
- **失败证据与验证：** 新增版本竞争回归在修改前稳定失败：409 后仍存在已提交的 Intent 投影，日志 `/tmp/commerce-payment-binding-red.log`。修改后真实 HTTP/MySQL/Redis 定向 **16/16** 通过，新增同订单锁竞争、不同订单独立处理、外呼期间并发拒绝及绑定/CAS 原子回滚后重试；原创建超时、取消交错和后续复用用例继续通过。完整 `clean verify` 测试阶段 **399/399** 通过，0 失败、错误或跳过，日志 `/tmp/commerce-payment-lock-verify.log`。POM/Mapper XML 解析及 `git diff --check` 通过。
- **Pending：** 完整 `verify` 仍在既有 90% 聚合行覆盖率门禁失败，本次 **1488/2065（72.06%）**，未调整覆盖阈值或排除类。`npm --prefix requirements-site test` **130/137** 通过，7 项失败涉及旧 Commerce 视图/十路由/15 表模型/隐私导航、模型台账、测试规范与写接口规范文字断言；本轮未改这些断言或其规则源，日志 `/tmp/commerce-payment-lock-site-tests.log`。这些结果不影响已通过的 Java 入口测试，但不能声明全仓库门禁通过。
- **本地环境及边界：** 为真实锁验证，手工启动已存在且停止的 `mor-local-redis-1`，端口核对为 `127.0.0.1:16379`；使用现有本地密码，经环境变量注入且不打印/写入仓库，Mock Context 使用随机 Namespace。没有访问其他项目的 6379、清库、改 Redis 配置、执行 DDL、修改其他服务、提交、推送或部署。正式启动现在需要 Redis 协调连接；操作与配置见 [Commerce README](../paytech/acquiring/README.md)。

<a id="commerce-preparation-intent-20260919"></a>

## Commerce 创建初始化与首次支付 Intent（2026-09-19）

- **Implemented：** 按用户最终确认，删除独立准备阶段与 `prepareCheckout` API，包括 `/preparation` 路径、Processor、Request/Response、展示转换和 GET 的 PREPARE 操作。`CreateCheckoutProcessor` 在事务外取得 OE、商业、资格评估、税务和法律信息；`CheckoutInitializationContext` 校验/组装本库对象，`CheckoutCreationService.saveCreateCheckoutContext` 以 REQUIRED 事务一次保存 Order、商品、退款仓位、资金投影、OPEN Session、Order Context、Order Legal、Consumer Contract。新建返回 READY/FINAL/OPEN，不创建 Intent；GET 只读取展示事实。设计及窄取代范围见[本次技术方案](../requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md)。
- **首次支付：** 保存三份法律接受及网络证据后，在事务外按 `COMMERCE_ORDER + orderId` 创建唯一 Intent，检查返回身份/金额后短事务绑定，并用订单 CAS 检查创建期间的取消与期限。后续支付复用原 Intent；创建响应丢失沿原订单业务身份恢复，卡提交响应未知返回 submissionId 供查询。接受后锁定 Consumer。未受理支付的已初始化订单可本地取消；已受理但无投影时先取得原 Intent 并确认终止，依赖不可用保持待定。事务内仅操作本库，不增加请求 Key/Hash/租约/回执表。
- **暂缓集成：** 本轮用户明确授权固定外部 Client Mock + TODO；固定 OE AE001、零税/舍入、合成风险与文件引用、`.invalid` 文档链接、固定 Consumer 保护/回填值、支付 PROCESSING 和终止 TERMINATED。配置无测试环境判断，不表明真实 CCT/Risk/Finance/Platform/Vault/Payment 已接通，也不证明真实税务、个人信息保存、扣款或终止。真实 Owner API、支付唯一身份与渠道并发保护仍待接入。
- **Verified：** Commerce 全模块 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw clean verify` 测试阶段 **395/395** 通过，0 失败、错误或跳过，日志 `/tmp/commerce-initialization-verify.log`。收尾同步错误文案与 Mapper 排版后，定向创建/支付/事务用例 **73/73** 再次通过，日志 `/tmp/commerce-initialization-final.log`。覆盖创建初始化、业务重复/并发、完整多表事务回滚、无 Consumer/Intent 的加载、首次与再次支付、Intent 超时重试、错误归属、卡提交超时、取消恢复/交错，以及旧 preparation 路径 404。使用正式启动类、真实本地 HTTP/MySQL、外部端口 DTO Mocker、两级 BeforeEach 与全局 AfterEach；没有替换内部业务链或在生产代码加入测试分支。外部固定端口测试不等同于真实跨服务 HTTP 联调。
- **Pending：** 完整 `verify` 在既有 Commerce Hosted Checkout bundle 90% 行覆盖率门禁失败：本次 **1646/2244（73.35%）**。未降低门槛或排除生产代码。报告 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/index.html`。源码引用/差异空白、OpenAPI JSON（四个 Customer 操作）、Mapper XML 和时序目录 JavaScript 语法检查通过。
- **本地数据库：** 用户明确同意后，仅在 `mor-local-mysql-1`（`127.0.0.1:13306/mor_commerce`）执行[四表补齐 SQL](../paytech/acquiring/docs/requirements/20260919-checkout-preparation/local-test-tables.sql)，新增 `t_order_legal`、`t_consumer_context`、`t_checkout_network_evidence`、`t_consumer_acceptance`。未修改已有表或数据；新接受表的历史 `command_idempotency_id` 沿 2026-09-17 已确认增量定义为可空，新流程不写入。测试完整 DDL 同步四表，仍人工执行。未操作 mor-test 或生产库。
- **迁移边界：** 未改 Payment/Risk 等服务、Gateway、PDManer、历史订单/旧命令/补偿事件。旧 CHECKOUT_PREPARATION Handler 已删除；其他环境如有未初始化订单或旧任务，启用前须单独处理，GET 不补建也不保留兼容准备入口。未提交、推送或部署。

<a id="commerce-get-refund-tests-20260919"></a>

## Commerce GetRefund 入口测试（2026-09-19）

- **Implemented：** 新增一个 `GetRefundTest`，类级 `@see` 指向 `ClientRefundApi#getRefund`，复用正式应用、真实 HTTP、本地 MySQL、两级 BeforeEach 和全局 AfterEach。沿用订单/退款 Mocker 的数据库对象 Builder；新增响应 Checker 通过正式 `GetRefundResponseBody` getter 断言全部业务字段，响应结构遵循 DTO 序列化定义，没有字段名清单或 JSON 路径。
- **场景：** 五种退款状态、Operations 来源、币种主单位格式与微秒时刻、无 Session/投影的 Checkout 与 Direct Pay、连续查询最新状态、多笔退款精确定位、Client 隔离、关联订单缺失/归属错误、公开号大小写与长度、可信身份头、版本边界和损坏事实。共享只读快照增加孤立退款的覆盖；每次查询比较两个 Client 下订单、关联事实、退款和任务，并检查 CCT 零调用。未决 INIT 任务不能被 GET 推进。
- **首次验证（修复前）：** 全模块 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw clean verify` 实际运行 **390** 例，其中 **388 通过、2 失败、0 错误、0 跳过**。新 GetRefund **47/49** 通过，既有 **341/341** 全部通过。日志 `/tmp/commerce-get-refund-verify.log`；构建在 Surefire 阶段失败，未执行该轮覆盖率报告/门禁。
- **修复及复验：** 用户报告失败后，在 GetRefundProcessor 的 Money 转换处仅捕获 `InvalidParameterException` 并转换为保留 Cause 的 `ErrorException`，使本库存量 USD `0.001` 或非法币种 `ZZZ` 返回 500，而非客户端参数错误 400。继续使用 Money 校验，不重复解析金额、增加校验器或放宽测试断言。全模块 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw test` **390/390** 通过，GetRefund **49/49**，0 失败、错误、跳过，日志 `/tmp/commerce-get-refund-error-fix.log`。本次未重跑覆盖率 verify，既有整体覆盖率门禁缺口仍在。
- **边界：** 初次仅调整测试、README 和本台账；后续修复只增加 GetRefundProcessor 的窄异常分类。未改 API、Foundation、配置、POM、数据库结构或 DDL；纯查询仍无事务、外部调用或新增 Context。只准备和清理本地测试自有数据，未执行 manual、真实 Owner 联调、提交、推送或部署。运行命令见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-response-dto-checkers-20260919"></a>

## Commerce 响应 Checker 复用 DTO（2026-09-19）

- **Implemented：** CreateCheckout、GetOrder、CreateRefund、ListOrderRefunds 四个响应 Checker 统一通过正式 ResponseBody getter 读取业务值，删除手写字段名、可空字段清单和字符串路径。响应结构用 DTO 回序列化结果与实际 JSON 比较；业务期望仍来自用例独立准备的场景，不从实际响应反推。本条取代下方相关测试记录中手写字段白名单的当前实现描述。
- **Verified：** Commerce 全模块 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw test` **341/341** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-response-dto-checkers.log`。本轮未重跑覆盖率 `verify`，既有整体门禁缺口不变。
- **边界：** 仅调整 Commerce 测试 Checker、README 和本台账；没有改生产代码、API 定义、Foundation 或数据库结构，未提交、推送或部署。

<a id="commerce-list-order-refunds-tests-20260919"></a>

## Commerce ListOrderRefunds 入口测试（2026-09-19）

- **Implemented：** 新增一个 `ListOrderRefundsTest`，类级 `@see` 指向 `ClientRefundApi#listOrderRefunds`，复用正式应用、MockTestBase、真实 HTTP、本地 MySQL 和既有清理。退款 Mocker 扩展为返回数据库对象 Builder，复用原退款写入及身份生成，不为查询构造创建 API 请求。`ListOrderRefundsResponseChecker` 用正式 DTO getter 检查业务字段，原始 JSON 只验证字段集合、类型和显式 null。
- **场景：** 空页、Client/Operations 来源、所有退款状态、币种格式、默认 20 与 1/100 边界、刚好满页与多取一行、同微秒按大小写敏感退款号倒序、多页无遗漏/重复、翻页期间新增退款与状态更新、Client/Order 隔离、游标版本/规范编码/锚点时间与归属、身份/路径/分页参数和损坏事实拒绝。只读 Checker 比较本例两个 Client 下订单、关联事实、退款和任务的全列快照，并检查 CCT 零调用；预先存在的 INIT 任务不能被 GET 推进。
- **Verified：** 定向 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw -Dtest=ListOrderRefundsTest -Dsurefire.failIfNoSpecifiedTests=false test` **74/74** 通过，日志 `/tmp/commerce-list-refunds-tests.log`。随后全模块 `clean verify` 测试阶段 **341/341** 通过（新增 74、既有 267），0 失败、错误、跳过，日志 `/tmp/commerce-list-refunds-verify.log`。JaCoCo 中 ListOrderRefundsProcessor 行 **32/32**、分支 **16/16**；ListOrderRefundsConverter 行 **7/7**；RefundCursor 行 **15/15**、分支 **12/14**。报告 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/index.html`。
- **Pending：** 完整 `verify` 仍被既有整体覆盖率门禁拦截：Commerce Hosted Checkout bundle 行 **839/2428（34.56%）**，要求 90%。未降低门槛或排除生产代码，不把单接口测试完成等同于整个服务验收。
- **边界：** 只调整测试代码、README 和本台账；未改生产实现、API、表结构、DDL、POM、配置或 Foundation。本次仅对本地自有测试资源准备/清理数据，不执行建表或迁移，不配置外部成功 Mock。未运行 manual、真实 Owner 联调，未提交、推送或部署。运行入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-create-refund-tests-20260919"></a>

## Commerce CreateRefund 入口测试（2026-09-19）

- **Implemented：** 按现行规范新增 `ClientRefundApi#createRefund` 的两个 HTTP 场景类和一个公开 Service 原子性测试类，均声明具体入口 `@see`。复用正式应用、Foundation 调用器、两级 BeforeEach、全局 AfterEach；请求用 Builder，响应通过正式 DTO getter 断言，前置订单和退款由 Mocker/TestMapper 直接入库。测试覆盖受理、Checkout/Direct Pay、币种精度和金额边界、Client 隔离、业务退款号防重/冲突、当前资格和坏数据拒绝、真实并发唯一约束及额度竞争、REQUIRED 回滚和 CAS 失败。
- **异步与清理：** 保留真实登记器、Dispatcher、Handler 和生产 PaymentRefundClient。当前适配器尚未接入 Payment HTTP，测试验证实际调用失败后退款保持 PROCESSING、预占保留、任务 ERROR 等待恢复，重提只返回原回执，不重复提交。全局清理前等待 Coordinator 与 Handler 全部退出，只删除本轮隔离订单的退款任务、退款申请及原有子表数据；没有替换内部生产组件或加入生产测试分支。
- **Verified：** 定向 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw '-Dtest=CreateRefund*Test,RefundCreationTransactionTest' -Dsurefire.failIfNoSpecifiedTests=false test` **96/96** 通过（27 创建/防重/并发、67 校验、2 事务），日志 `/tmp/commerce-refund-tests.log`。全模块 `clean verify` 的测试阶段 **267/267** 通过，0 失败、错误、跳过，含原 CreateCheckout/GetOrder **171** 例；日志 `/tmp/commerce-refund-verify.log`。JaCoCo 中 CreateRefundProcessor 行 **46/49（93.88%）**、分支 **13/14（92.86%）**，CreateRefundConverter 行 **4/4**，RefundCreationContext 行 **14/14**。报告为 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/index.html`。
- **Pending：** 整体 `verify` 仍在既有 Commerce Hosted Checkout bundle 的 90% 行覆盖门禁失败，本次 **778/2428（32.04%）**。未降低门槛或排除生产代码；接口测试通过不代表整个服务覆盖率验收通过。真实 Payment HTTP 成功、拒绝、超时、协议错误和后续退款结算不在本轮证明范围。
- **本地数据库：** 用户明确授权后，仅在 `mor-local-mysql-1`（`127.0.0.1:13306/mor_commerce`）新增原先缺失的 `t_refund_request`、`t_compensation_event`；表定义逐段取自正式 `commerce-service/docs/database/sql/commerce-schema.sql`，完整同步到测试 `src/test/resources/init/ddl.sql`。只执行两条 CREATE TABLE，没有 DROP、ALTER、变更已有数据或访问 mor-test；执行摘录 `/tmp/commerce-create-refund-test-tables.sql`。应用和测试仍不自动建表或迁移。
- **边界：** 只修改 Commerce 测试代码、测试 DDL、README 和本台账；未改生产实现、API、Foundation、POM 或 Mock 配置。未运行 manual 或真实 Owner 联调，未提交、推送、发布或部署。操作入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-get-order-tests-20260917"></a>

## Commerce GetOrder 入口测试（2026-09-17）

- **响应对象断言重构：** GetOrderResponseChecker 复用正式 GetOrderResponseBody / ApiProblemResponse，业务字段改用 getter 断言，删除 assertText/assertTime/assertNullField/assertAbsent 等字符串路径方法。原始 JSON 仅校验字段白名单、字符串类型、显式 null 和可选字段省略；保留原场景、数据库只读断言及清理机制。定向 `./mvnw '-Dtest=GetOrder*Test' -Dsurefire.failIfNoSpecifiedTests=false test` **103/103** 通过，0 失败、错误、跳过，日志 `/tmp/commerce-get-order-dto-checker.log`；本次未改生产代码，未重跑下述整体 verify。
- **Implemented：** 按现行测试规范新增 `ClientOrderApi#getOrder` 的 Success、Validation、Projection、Consistency 四个场景类，每类声明具体接口 `@see` 和自己的 BeforeEach，复用正式应用、MockTestBase、Foundation HTTP 调用器及响应 Checker。Builder 构造事实，自动注入的 OrderQueryDataMocker / TestMapper 直接插入本地数据；不调用创建/支付接口准备数据，不替换内部生产链路。公共 AfterEach 按已有隔离订单 ID 清理，补充支付投影和冻结上下文回收。
- **Verified：** `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw clean verify` 的测试阶段 **171/171** 通过，无失败、错误或跳过；其中 GetOrder **103/103**（22 成功、18 校验、25 投影、38 一致性），既有 CreateCheckout **68/68**。所有 GetOrder 调用均校验数据库前后快照不变、CCT 零调用。JaCoCo 聚合报告中 GetOrderProcessor 行 **121/122（99.18%）**、分支 **145/150（96.67%）**，GetOrderConverter 行 **30/30**、分支 **12/12**。完整日志 `/tmp/commerce-get-order-verify.log`，报告 `commerce-service/paytech-commerce-test/target/site/jacoco-aggregate/index.html`。
- **Pending：** 完整 `verify` 仍在既有整体覆盖率门禁失败：Commerce Hosted Checkout bundle 行覆盖约 **25%**，低于 **90%**。其统计范围包含其他尚未重建测试的 Customer、Refund 等能力；未排除代码或降低门槛，不能将本接口测试通过表述为完整服务验收通过。
- **边界：** 只修改测试辅助代码、用例和操作说明，不改业务实现、接口合同、POM、Mock 配置或 Schema。现有测试初始化 DDL 已包含所需表，本轮未执行 DDL；仅对本地测试自有数据执行准备和清理。未运行 manual、真实外部系统联调，未提交、推送或部署。操作入口见 [Commerce README](../paytech/acquiring/README.md#验证入口)。

<a id="commerce-cct-test-probe-20260917"></a>

## Commerce CCT 测试观察器装配精简（2026-09-17）

- **Implemented：** 保留实际用例使用的 AdjustableUtcClock 和 CctTransactionProbe：前者固定/推进时间验证 CCT 返回前过期，后者记录真实出站线程的事务状态。Probe 直接实现 RestClientHttpServiceGroupConfigurer，由既有 TestComponent 扫描挂载到 CCT 客户端；删除 CctPrecheckObservationTestConfiguration，以及 TestApplication 的显式导入和测试 POM 引用。
- **Verified：** 默认 `./mvnw clean test` **68/68** 通过，包含实际出站事务状态/调用次数断言及时间推进场景；源码和 POM 无已删除类型引用，差异空白检查通过。日志 `/tmp/commerce-cct-probe-autowiring-regression.log`。
- **边界：** 本轮仅调整测试装配和说明，不改生产代码、用例断言或公共 Foundation；未执行 SQL、提交、推送或部署。下方历史记录中的独立观察配置由本条替代。

<a id="commerce-unused-test-repository-20260917"></a>

## Commerce 旧测试 Repository 清理（2026-09-17）

- **Implemented：** 删除 `test.repository` 下 8 个遗留文件：CheckoutPreparationTestMapper、CustomerCheckoutTestMapper、OrderApiTestMapper、RefundApiTestMapper、RefundSubmissionTestMapper 无调用方；CommerceRepositoryTestMapper、CommerceRepositoryTestFixture、CommerceRepositoryTestConfiguration 仅组成 ManualTestBase 的旧装配链，目前没有 manual 用例继承该基类。移除基类对应导入、Import 和 Fixture 字段；保留 CreateCheckout 使用的 CheckoutCreationTestMapper，以及 manual 基类/资源。
- **Verified：** 跨工作区 Java/XML 引用核对后删除，无遗留源码或测试配置引用。默认 `./mvnw clean test` **68/68** 通过，0 失败、0 错误、0 跳过；278 个其他源码/资源文件摘要不变，差异空白检查通过。日志 `/tmp/commerce-unused-test-repository-regression.log`。
- **边界：** 删除前已备份 8 个文件，包含 CheckoutPreparationTestMapper 尚未提交的 import 整理，备份目录记录在 `/tmp/commerce-unused-test-repository-backup-path.txt`。本轮不改生产代码、测试用例、SQL 或数据库结构，未提交、推送或部署。下方历史记录中保留旧 Repository 辅助配置的描述由本条替代。

<a id="foundation-http-test-autoconfiguration-20260917"></a>

## Foundation HTTP 测试自动装配（2026-09-17）

- **Implemented：** `paytech-test-support` 通过 Boot 现有 `@AutoConfigureRestTestClient` 的注解专属 imports 注册 HttpTestClientAutoConfiguration，自动提供 HttpTestInvoker。真实 Servlet 服务使用宿主 ClientHttpRequestFactory、随机端口及 Context Path，保留全部有序 RestTestClientBuilderCustomizer；客户端与调用器延迟初始化，用户显式 Bean 优先，MOCK 上下文保留 Boot 原有客户端。未注册全局自动配置，普通应用不会仅因引入测试 JAR 启用该能力。
- **Commerce 接入：** 删除 HttpTestClientConfiguration 及 TestApplication、ManualTestBase、测试编译 Profile 中的引用；两个基类沿用已有注解和注入。模块 README 与公共示例同步直接注入写法。本轮未继续修改生产代码；下方测试配置收敛记录中保留 HTTP 工厂的描述由本条替代。
- **Verified：** 新增 7 个自动装配场景，先记录缺少 HttpTestInvoker 的失败证据，再验证实际 HTTP、宿主传输标记、Context Path、有序定制、302/503 单次响应、用户 Bean 退让、MOCK 回退及无注解不启用。Foundation `-pl paytech-test-support -am clean install` 完成测试支持 **53/53**、Common Core **130/130**；仅安装本机 Maven 缓存。Commerce 默认 `clean test` **68/68** 通过，`-DskipTests package` 通过，Web 可执行 JAR 不含 test-support / Spring Test / Boot Test / JUnit。日志 `/tmp/foundation-http-autoconfig-red.log`、`/tmp/foundation-http-autoconfig-verify.log`、`/tmp/commerce-http-autoconfig-regression.log`、`/tmp/commerce-http-autoconfig-package.log`。
- **边界：** 未执行 manual 场景、全 Foundation Reactor、Commerce 覆盖率 verify、SQL、提交、推送、发布或部署；旧准备/恢复迁移缺口仍见下方记录。

<a id="commerce-test-configuration-20260917"></a>

## Commerce 测试配置收敛（2026-09-17）

- **Implemented：** 删除 CheckoutTestApplication、CreateCheckoutTestConfiguration，将正式 CommerceApplication 引用、Mock 组件扫描和测试 Mapper 汇总到既有 TestApplication。MockTestBase 使用该组合根，取消生产 Bean 手工清单。HttpTestClientConfiguration 保留真实禁重试传输及随机端口初始化时机定制；ManualTestBase 直接引用正式应用和必要的 HTTP/数据库测试配置，不装配 CCT Mock。测试 POM 同步新入口并移除已删除的时钟配置引用；保留用户已暂存的 PaymentSimulationTestConfiguration 等 15 个辅助类删除。
- **启动问题修复（用户追加授权）：** 正式扫描先暴露 CustomerCommandServiceImpl 强制依赖已停用 CommandIdempotencyService，后暴露 `RefundService → Registrar → Handler Registry → RefundSubmissionHandler → RefundService` 构造循环。前者使用 ObjectProvider 按需解析，停用时明确返回 DEPENDENCY_UNAVAILABLE；后者在实际登记时取得 Registrar，原本地事务、事件身份和提交后执行语义保持。不启用旧组件、不添加 Mock 分支、不允许循环引用或排除生产 Bean。
- **Verified：** 两次创建成功单例分别记录缺少幂等 Bean 与退款依赖循环的失败证据；修复后单例通过，随后默认 `./mvnw clean test` 在正式应用扫描下运行 5 个类，**68/68** 通过，0 失败、0 错误、0 跳过。`-Pcheckout-create-tests -DskipTests package` 通过，Web JAR 未包含测试配置、Mock 基类或 test-support / Spring Test / JUnit 依赖。日志 `/tmp/commerce-test-configuration-startup.log`、`/tmp/commerce-test-configuration-next-startup.log`、`/tmp/commerce-test-configuration-startup-fixed.log`、`/tmp/commerce-test-configuration-regression.log`、`/tmp/commerce-test-configuration-package.log`。
- **Pending：** 旧准备流程仍依赖幂等表保存 OE 选择快照、原准备时刻和 Intent 首发标记；完整移除需要迁移业务恢复事实，本轮只解决应用启动依赖，不宣称这些功能或旧组件消费已迁移。未验证退款、准备及恢复的完整业务回归，也未执行覆盖率 verify。
- **边界：** 除上述两个获批生产装配修复外，256 个生产源码/资源及保留测试用例文件的摘要一致；配置引用和差异空白检查通过。未改接口合同、数据库或共享组件，未执行 DDL、提交、推送或部署。本条取代下方正式启动类迁移仍待处理的旧状态描述。

<a id="commerce-auto-tests-pruned-20260917"></a>

## Commerce auto 测试仅保留 CreateCheckout（2026-09-17）

- **Implemented：** 按用户要求删除其余 29 个 auto 测试类及 1 个仅供旧合同测试使用的包内辅助类。auto 仅保留 CreateCheckoutSuccessTest、CreateCheckoutDuplicateTest、CreateCheckoutValidationTest、CreateCheckoutPrecheckTest 和 CheckoutCreationTransactionTest；保留测试内容、公共支撑、manual 配置及生产源码，本轮均未改动。README 和测试 POM 注释同步当前范围，没有增加测试排除或降低覆盖率门槛。
- **Verified：** 默认 `./mvnw -DskipTests clean test-compile` 通过，随后不指定 Profile 执行 `./mvnw test`，仅发现上述 5 个类，合计 **68/68** 通过，0 失败、0 错误、0 跳过。删除前后 305 个保留源码/资源文件的摘要一致，没有遗留对已删除类型的源码引用，差异空白检查通过。日志 `/tmp/commerce-auto-test-pruning-compile.log`、`/tmp/commerce-auto-test-pruning-test.log`。
- **边界：** 其余接口当前没有 auto 测试，后续按明确任务和现行规范重建；下方旧测试记录保留为历史证据。正式应用启动类迁移仍待处理；未执行覆盖率 `verify`、DDL、提交、推送或部署。

<a id="test-startup-conventions-20260917"></a>

## 测试规范整理与正式启动类约定（2026-09-17）

- **文档已整理：** 按用户确认统一测试职责、真实数据库与外部 DTO Mock、公共组件、两级初始化和后置清理、自动装配、配置清单与手工 DDL、场景拆类和入口引用。服务级入口测试改为复用正式应用启动类及生产组件扫描，取代手工列举生产 Bean 的默认装配方式；纯计算和公共组件机制测试保留必要的最小上下文。现行规则见[测试规范第 12 节](UNIT_TEST_GUIDE.md#12-测试基类与配置)，示例、开发规范和 AI 薄投影同步。
- **Pending：** Commerce 的 MockTestBase 仍使用 CheckoutTestApplication，后者仍通过长串 Import 装配生产组件，本轮未迁移 Java 代码。此前 CreateCheckout 的 68/68 是原切片的验证证据，不代表正式应用启动方式已经验证。
- **Verified（文档）：** 六份规范、示例与入口文件的代码围栏、229 个本地文件链接及本条状态锚点检查通过；相关文档差异空白检查通过。已核对现行规范没有继续要求服务级入口手工切片装配，决定记录中的旧写法明确标为已取代。
- **操作范围：** 本轮仅更新文档，不执行 Java 构建、业务测试、SQL、提交、推送或部署；此前代码实现与验证证据保留在下方记录中。

<a id="foundation-mock-api-server-20260917"></a>

## Foundation MVC Mock Server 与 CCT API 实现（2026-09-17）

- **测试辅助组件自动装配补充：** CheckoutDataMocker、CheckoutCreationChecker、AdjustableUtcClock、CctTransactionProbe 改为 TestComponent，由既有扫描注册；删除对应构造工厂。事务观察器从配置内部类移为独立类，避免配置解析与包扫描重复注册；旧时钟配置仅保留类导入。Foundation Registrar 使用 Spring AnnotationConfigUtils 处理 Primary 等公共定义注解，机制测试验证存在宿主同类型 Bean 时选择标注 Primary 的测试组件。公共组件 **46/46**、Common Core **130/130**、Commerce 创建切片 **68/68** 通过并完成 package，Web JAR 无测试组件及测试依赖。日志 `/tmp/mock-server-component-annotations.log`、`/tmp/commerce-test-components-autowiring-final.log`；前序重复注册及 Primary 缺失失败分别记录在 `/tmp/commerce-test-components-autowiring.log`、`/tmp/commerce-test-components-autowiring-verified.log`，均已修复。规范第 12 节补充“可自动装配则自动装配”，只保留有实际定制/生命周期需要的 Bean 工厂；未改业务逻辑、SQL 或提交。
- **自动装配后续（取代下方手写 Bean 装配）：** Foundation 新增 EnableMockHttpServer 和内部 Registrar，扫描指定包的 TestComponent，自动注册 Mocker 与 Controller 内部 Bean、独立服务和动态端点；普通组件/配置类排除，Controller 不进入宿主顶层 Bean 或业务路由。Commerce 删除三个 Bean 工厂，CCT Mocker 增加 TestComponent，基类直接注入保持。新增自动装配机制测试 **3/3**，公共模块 **46/46**、Common Core **130/130**、Commerce CreateCheckout **68/68** 通过；公共模块行 **165/173（95.4%）**、分支 **45/48（93.8%）**，Registrar 行/分支均 **100%**。日志 `/tmp/mock-server-autowiring-red.log`、`/tmp/mock-server-autowiring-green.log`、`/tmp/mock-server-autowiring-verify.log`、`/tmp/commerce-mock-server-autowiring.log`。`-DskipTests package` 通过，Web JAR 无 test-support、Spring/Boot Test、JUnit、Awaitility 和 Mock 类；日志 `/tmp/commerce-mock-server-autowiring-package.log`。只改测试工件、装配及文档，未修改业务代码或 SQL；未提交、推送、发布或部署。
- **Spring 注入后续：** Commerce 测试配置直接创建 MockHttpServer / CctCheckoutMocker Bean，MockTestBase 注入服务，移除静态实例、取实例方法和静态 DynamicPropertySource；DynamicPropertyRegistrar 在客户端装配前注册随机地址，容器负责关闭。`-Pcheckout-create-tests test` **68/68** 通过，日志 `/tmp/commerce-mock-server-injection.log`；本轮只调整测试装配及使用文档，Foundation 实现与业务代码不变。
- **响应 Builder 后续：** 新增测试侧 CctPrecheckResponseBuilder，CreateCheckout 基类及预检、防重、校验场景的 6 处 PrecheckResponseBody 直接构造全部改为模板和具名字段覆盖；正常默认值保持，非法资格/原因组合不被修复。`-Pcheckout-create-tests test` **68/68** 通过，日志 `/tmp/commerce-precheck-response-builder.log`；未改生产 DTO 或业务逻辑，测试规范同步外部响应 Builder 要求。
- **错误 DTO 后续修正：** CctCheckoutMocker.mockHttpFailure 直接构造公共 ApiProblemResponse，并复用 ApiProblemProfile.TYPE，移除手写错误 Map；测试规范明确 Map 只用于匹配配置，正常错误体使用正式 DTO。定向执行 4 个 HTTP 错误映射场景和 1 个依赖恢复场景，**5/5** 通过，日志 `/tmp/commerce-cct-problem-dto.log`；本次未重跑全量切片，业务代码不变。
- **Implemented：** 测试专用模块新增 MockHttpServer / MockHttpFault，独立 Tomcat / MVC 上下文只装配传入的 Mock Controller，跟踪活动请求和意外请求，复用 Clear。Commerce 新增标注 TestComponent 的 MockCheckoutEligibilityApi，实现正式 CheckoutEligibilityApi；删除 CctCheckoutMockServer 的手写路径分发与 Query 解析。服务发现配置、真实出站 API 代理、LoadBalancer 及业务代码保持。
- **Verified（Foundation）：** 缺少新公共 API 时记录红灯编译失败；新增真实 HTTP 机制测试 **9/9**，覆盖接口继承的 Query/Header/Body 绑定、协议故障、实际断连、404/405/400 意外请求及等待活动请求结束。公共模块合计 **43/43**、依赖 Common Core **130/130**，无失败/错误/跳过；JaCoCo 公共模块行 **133/141（94.3%）**、分支 **37/40（92.5%）**。日志 `/tmp/mock-api-server-red.log`、`/tmp/mock-api-server-green.log`、`/tmp/mock-api-server-verify.log`。
- **Verified（Commerce）：** 改接 MVC 后以及补充测试组件扫描隔离标记后，两次 `-Pcheckout-create-tests test` 均 **68/68** 通过，包括真实下游错误、超时、断连及并发场景。`-DskipTests package` 通过，Web executable JAR 不包含 paytech-test-support、Mock 类、JUnit、Spring Test 或 Awaitility。日志 `/tmp/commerce-mock-api-regression.log`、`/tmp/commerce-mock-api-final.log`、`/tmp/commerce-mock-api-package.log`。
- **边界：** 只改测试工件、测试装配和文档，不修改业务接口、DTO、数据库或运行配置；Foundation 目标 JAR 仅本机安装。其他旧接口测试未迁移，未运行全 Commerce auto，未执行 SQL、提交、推送、发布或部署。结构及操作见[交付补充](../requirements/20260917/foundation-公共测试组件/delivery.md#mock-api-接口实现补充2026-09-17)。

<a id="foundation-test-clear-20260917"></a>

## Foundation Clear / TestBase 后置清理（2026-09-17）

- **Implemented：** `paytech-test-support` 新增 Clear 和 TestBase。公共基类注入 List<Clear>，仅在 AfterEach 聚合调用各组件的 clear；没有 BeforeEach 清理。Commerce MockTestBase 继承公共基类，数据 Mocker、CCT Mocker、测试时钟和事务观察器实现 Clear，替代手工清理列表。宿主保留异步停止、意外调用检查和环境不可复用约束；具体 SQL 仍在 Commerce 测试侧。
- **Verified（Foundation）：** 新 API 缺失时确认测试编译失败；新增实际 JUnit/Spring 生命周期机制测试 **6/6** 通过，公共模块合计 **34/34**，依赖 Common Core **130/130**，均无失败/错误/跳过。覆盖正常两例、准备/执行/后置检查失败仍清理、多项失败汇总与异步停止失败禁止清理和复用。JaCoCo 公共模块行覆盖 **70/70（100%）**，TestBase **5/5**；日志 `/tmp/paytech-test-clear-red.log`、`/tmp/paytech-test-clear-green.log`、`/tmp/paytech-test-clear-verify.log`。
- **Verified（Commerce）：** `-Pcheckout-create-tests test` 的 HTTP **66/66** 与事务 **2/2**，共 **68/68** 通过，无失败/错误/跳过；真实本地数据库和 DTO HTTP Mock 沿用原测试环境，日志 `/tmp/commerce-test-clear-regression.log`。未执行其他接口全量测试。
- **边界：** 本轮只改测试工件及规范，未改业务逻辑或 SQL；目标 JAR 仅安装本机缓存，未发布、提交、推送或部署。两级 BeforeEach 仍只做检查与初始化；其他旧测试按授权范围迁移。本条补充下方公共组件原始记录，说明见[交付补充](../requirements/20260917/foundation-公共测试组件/delivery.md#后置清理抽取补充2026-09-17)。

<a id="commerce-create-checkout-validation-20260917"></a>

## Commerce CreateCheckout 两项输入校验修复（2026-09-17）

- **Implemented：** 非 USD 改由 Body/内部 Request 的币种约束提前拒绝，移除响应 Converter 的迟到币种判断；原 Request 增加商品编号重复判断，CreateCheckoutProcessor.validate 在外部查询和落库前返回 422 VALIDATION_ERROR。OpenAPI 同步当前 USD 值域与商品编号唯一性；保留唯一索引、本地五类事实原子保存和事务外 CCT，不改历史订单。
- **Verified：** 修改前两个原用例均失败；修改后 **2/2** 通过，再执行 `-Pcheckout-create-tests test`，创建 HTTP **66/66** 加事务 **2/2**，共 **68/68** 通过，0 失败/错误/跳过。非 USD 返回 currency 字段错误，两个场景均验证无 CCT 调用、无订单写入。日志 `/tmp/commerce-checkout-validation-red.log`、`/tmp/commerce-checkout-validation-green.log`、`/tmp/commerce-checkout-validation-regression.log`；OpenAPI YAML 解析、枚举和相关差异空白检查通过。
- **边界：** 此结果取代下方测试重建、组件接入与拆分类记录中的两项未修复结论，保留其历史执行证据。未验证其他接口的全量 auto 或全服务覆盖率，未执行 SQL、提交、推送、发布或部署。规范自查与修复原因见[修复说明](../requirements/20260917/commerce-CreateCheckout测试重建/validation-fix.md)。

<a id="commerce-create-checkout-split-20260917"></a>

## Commerce CreateCheckout 测试按场景拆分（2026-09-17）

- **Implemented：** 原单类拆为 `checkout/create` 包下的 `CreateCheckoutSuccessTest`、`CreateCheckoutDuplicateTest`、`CreateCheckoutValidationTest`、`CreateCheckoutPrecheckTest`。接口测试基类复用路径、invoke 和必要辅助，不包含测试或新增生命周期；各类自行配置 BeforeEach，共用原 MockTestBase、测试应用及清理。当前规范新增同一接口按场景拆类规则；未改生产业务、公共组件或数据库。
- **Verified：** 原 28 个测试方法及参数源、断言逐字保留，执行数量保持 **66**：成功 6、防重 8、校验 39、预检 13。`-Pcheckout-create-tests '-Dtest=CreateCheckout*Test' clean test` 实际 **64 通过、2 失败、0 错误、0 跳过**；失败集合仍仅为非 USD 币种和重复商品编号提前校验，详情沿用下方公共组件接入记录。日志 `/tmp/commerce-create-checkout-split-tests.log`。
- **入口引用补充：** 四个场景类的 JavaDoc 各自以 `@see` 引用 `ClientCheckoutApi#createCheckout` 的完整参数签名；事务类引用实际 Service 保存方法。五个类可执行源码保持，JDK 25 JavaDoc `-Xdoclint:reference` 校验通过（`/tmp/commerce-checkout-test-see-javadoc.log`）；本次注释修改未重跑单元测试，规范、AI 规则和示例同步。
- **默认选择验证：** 不传 `-Dtest` 执行 `-Pcheckout-create-tests test`，四个 HTTP 场景类与原有事务类均被发现，共 **68 例、66 通过、2 个相同失败、0 错误、0 跳过**；事务类 2/2 通过。日志 `/tmp/commerce-create-checkout-split-profile.log`。这证明 Profile 未遗漏拆分后的文件，不代表全部测试通过。
- **边界：** Maven 编译与执行选择已改为新场景包，旧单类已删除；没有排除失败用例。未执行 SQL、提交、推送或部署。文件分工与当前命令见[测试技术说明](../requirements/20260917/commerce-CreateCheckout测试重建/technique.md)，本轮变更见[交付补充](../requirements/20260917/commerce-CreateCheckout测试重建/delivery.md#场景拆分补充2026-09-17)。

<a id="foundation-test-support-20260917"></a>

## Foundation 公共测试组件与 Commerce 首个接入（2026-09-17）

- **Implemented：** Foundation 新增普通测试 JAR `paytech-test-support`，提供 `HttpTestInvoker`、`TestRequestHeaders` 和 `AbstractHttpResponseChecker<S>`。Build BOM 管理版本；Commerce 测试模块显式以 test scope 接入，CreateCheckout invoke 与响应 Checker 复用公共机制。业务断言、真实数据库 Checker、Mocker、两级生命周期及清理继续属于 Commerce；未改业务服务逻辑。
- **Verified（Foundation）：** 公开 API 缺失时先确认测试编译失败；实现后目标模块 **28/28** 真实 Loopback HTTP 用例通过，依赖 Common Core **130/130** 通过。JaCoCo 公共模块行覆盖 **65/65（100%）**、分支 **17/20（85%）**；未运行全 Foundation Reactor。日志 `/tmp/paytech-test-support-red.log`、`/tmp/paytech-test-support-verify.log`，报告位于模块 `target/site/jacoco`。
- **Verified（Commerce）：** `-Pcheckout-create-tests -DskipTests package` 成功；实际创建成功单例通过，随后完整 CreateCheckout 类 **64/66** 通过，2 个失败、0 错误、0 跳过。此次现有本地 Schema 已能执行用例，之前缺列不再阻止本次验证；本任务未执行 DDL，不推断由谁迁移。Web executable JAR 未包含 `paytech-test-support`、JUnit 或 Spring Test。日志 `/tmp/commerce-test-support-package.log`、`/tmp/commerce-test-support-http.log`、`/tmp/commerce-test-support-regression.log`。
- **修复前缺口（已由上方输入校验修复解决）：** 两个失败对应前序源码走查已记录的问题：非 USD 请求返回 500 且已保存订单；重复 `clientItemId` 返回 500 且已调用 CCT，未按用例期望在写入及外部调用前返回 422。本次保留断言，不以修改业务或掩盖结果使测试变绿；未声称 Commerce 整体回归通过。
- **操作与迁移边界：** BOM 与 Foundation 目标制品仅安装到本机 Maven 缓存，未发布 Nexus；Payment 和其他测试未迁移。未执行 SQL、提交、推送或部署。使用规范见[测试规范第 4.1 节](UNIT_TEST_GUIDE.md#41-公共测试组件)，范围和命令见[交付记录](../requirements/20260917/foundation-公共测试组件/delivery.md)。

<a id="commerce-create-checkout-tests-20260917"></a>

## Commerce CreateCheckout 测试重建（2026-09-17）

- **Implemented：** 删除旧创建、旧快照/反射合同测试，使用 `CreateCheckoutApiTest` 和 `CheckoutCreationTransactionTest`；前者包含 66 个参数化展开场景，后者覆盖末步回滚及 REQUIRED 传播。新增 Builder、数据库 Mocker/TestMapper、独立 CCT HTTP DTO Mocker、响应/数据库 Checker；真实创建链在共享最小测试应用中运行。两级 BeforeEach 与全局 AfterEach 每例重建和清理，Client/Order 隔离范围外的数据不动。旧其他入口保留原有装配，未迁移为新规范。
- **Verified：** JDK 25 下 `./mvnw -Pcheckout-create-tests -DskipTests test-compile` 通过（`/tmp/commerce-create-checkout-final-compile.log`）。缺列前置检查加入前，参数/可信 Header/CCT 拒绝、错误、断连、超时等 48 个场景通过，逐例清理通过（`/tmp/commerce-create-checkout-validation-final.log`）。这些验证不包含成功创建、并发写入和本地事务回滚，不能表示全部测试已通过。差异空白检查通过。
- **Pending：** 本地 `mor_commerce.t_commerce_order` 缺少现有代码需要的 `creation_mode`、`consumer_country_code`；已请求备份表结构并执行两个既有增量 SQL，尚未获得本次执行授权。测试只读检查列存在并明确失败，不自动迁移；单例复核已在 BeforeEach 给出明确缺列提示（`/tmp/commerce-create-checkout-schema-preflight.log`）。非 USD 提前拒绝、重复商品 ID 提前校验的回归用例保留，源码提示存在缺口，待 Schema 就绪后单独确认。完整旧测试仍有删除类型引用，完整应用仍有 Refund/补偿注册循环依赖；本次显式 Profile 不代表全量 auto/verify 或 90% 覆盖率通过。
- **Mock 配置补充（2026-09-17）：** 新增测试资源 `application-mock.yml`，完整配置当前切片的本地数据库、业务参数、HTTP/服务发现及后台调度，`MockTestBase` 自动启用 `mock`。动态注入仅保留 CCT 随机地址；旧 auto Profile 暂保留给未迁移测试。清除 Checkout/ID/数据库/Nacos 环境变量后的定向运行完成编译、Spring/Tomcat 启动和 MySQL 连接；之后仍被 BeforeEach 的既有缺列检查阻塞，未宣称业务测试通过（`/tmp/commerce-mock-profile-startup.log`）。规范、示例与 AI 规则同步配置约定。本轮未执行 DDL或变更生产代码。
- **Mock 配置清单补充（2026-09-17）：** 按用户明确用途补齐可选、默认、关闭能力和运行时注入字段，附用途、单位、默认/Mock 值和启用条件；规范及 AI 规则同步要求配置字段变更维护此文件。对实际测试 Classpath 的配置类反射核对，25 个 Commerce/Foundation 字段全部显式列出；生产 YAML 的 47 个叶子键和动态 CCT 地址均无遗漏，Spring YAML 解析通过（`/tmp/commerce-mock-config-catalog-check.log`）。更新后再次清除相关环境变量运行单例，Spring/Tomcat 与 MySQL 连接成功，仍在 BeforeEach 因已有缺列失败（`/tmp/commerce-mock-config-catalog-startup.log`）；不构成业务用例通过。本轮只改配置与文档。
- **Mock DDL 汇总（2026-09-17）：** 参考 Transaction 测试资源的集中建表方式，新增 [`init/ddl.sql`](../paytech/acquiring/paytech-acquiring-test/src/test/resources/init/ddl.sql)，覆盖当前 Mock 创建写入及零副作用查询依赖的 8 张表、128 列。建表语句与正式 Commerce Schema 逐字一致，字段顺序、类型、可空性、默认值、主键/索引及表列注释与 PDManer 静态核对通过；README、缺表提示与测试规范指向手工初始化入口。仅生成文件，未执行 SQL、Java 测试或修改生产代码，原数据库前置阻塞保持。 后续补齐本地数据库、用户创建、本库 DML 授权和 USE；账号/默认密码与 Mock YAML、执行顺序及八张表定义静态核对通过，已有账号不重置密码，SQL 仍未执行。
- **操作范围：** 手工启动已有 `mor-local-mysql-1` 供验证，未创建容器或改数据卷，未访问 mor-test、执行 DDL、提交、推送或部署。生产仅补上基线缺失的 `GetOrderProcessor` Objects import；业务语义未改，无生产测试开关或替身。详见[交付记录](../requirements/20260917/commerce-CreateCheckout测试重建/delivery.md)和 [Commerce 测试说明](../paytech/acquiring/README.md#测试启动边界)。

<a id="test-dto-mock-lifecycle-20260917"></a>

## 全项目单元测试规范（2026-09-17）

- **文档已沉淀：** [测试规范](./UNIT_TEST_GUIDE.md)明确真实本服务链路、独立 HTTP DTO Mock、Builder/Mocker/Checker、两级 BeforeEach 与全局 AfterEach；[示例](./examples/UNIT_TEST_EXAMPLE.md)展示配置响应、调用检查、测试失败后清理与串行执行。[决定](./decisions/2026-09-17-test-dto-mock-lifecycle.md)声明旧强制有状态 Simulator 等规则的取代范围，旧规范已归档。AGENTS、Cursor、开发/工程规范和路由入口同步。
- **Pending：** 规范任务本身只交付规范与说明性示例；首个 Commerce 创建测试的后续落地见上方专项记录，其他既有测试不因此视为已迁移。生产代码没有为测试增加分支、探针、模拟成功或依赖。
- **验证与操作范围：** 文档本地链接目标与测试规范入站锚点核对无缺失，已修改现行入口的差异空白检查通过；本地环境文档同步去除强制有状态 Simulator 的旧投影。未执行 Java 构建、业务测试或运行示例，未执行 SQL、提交、推送或部署。材料见[交付记录](../requirements/20260917/foundation-单元测试规范/delivery.md)。

<a id="gateway-request-idempotency-conventions-20260917"></a>

## 全项目请求幂等职责规范（2026-09-17）

- **文档已沉淀：** 按用户确认，HTTP Idempotency-Key 统一由 Gateway 处理，范围不限于 Client Gateway API；禁止业务系统新增 `paytech-command-idempotency` 依赖、启用、调用或同义服务本地请求重放机制。完整规则见[开发规范第 5.4 节](./DEVELOPMENT_CONVENTIONS.md#54-请求幂等与业务防重)，确认与取代边界见[职责决定](./decisions/2026-09-17-gateway-request-idempotency.md)。公共示例采用目标职责，不能据此推断 Payment 源码已迁移。
- **Pending：** 本轮不实现/激活 Gateway 请求幂等，不删除旧组件或历史表，不修改实际 API、业务代码、配置或数据库。Preparation 等剩余调用、既有内部/下游 Header 绑定与转发需单独协调迁移；旧机制存在不构成新接入许可。Gateway 并发重复请求、72 小时起算点及响应未记录窗口等实现细节仍待设计。
- **验证与操作：** 本轮仅维护规范和路由文档，未运行构建、测试或自动验证，未执行 SQL、提交、推送或部署。未迁移系统的业务防重与必要恢复不得被文档中的目标状态替代。材料见[本次交付记录](../requirements/20260917/foundation-请求幂等职责规范/delivery.md)。

<a id="platform-gateway-snapshot-refactor-20260917"></a>

## Platform Gateway Snapshot 查询重构（2026-09-17）

- **Implemented：** `GatewaySnapshotQueryApi` 经 Controller/Converter 进入 Access Control 的 `QueryGatewaySnapshotProcessor.process`；健康检查复用同一入口及响应转换，删除旧 Snapshot 查询 Service/Impl 和 Web Domain Assembler。新入口由组件扫描和构造器注入装配，复用 Foundation 对调用方已有事务及非空内部请求的检查。
- **读取与模型：** 保留共享查询模板的只读 REPEATABLE READ、2 秒超时；原六项集合读取全部完成后退出事务，再执行全局编译，符合 PLT-I20 专项合同第 8.4 节。原不可变编译聚合收敛为 `GatewaySnapshotView` class，保留嵌套纯领域值和集合不可变性，没有复制另一层结果。现有 Repository/Mapper、持久化对象、SQL 及管理命令共同使用的编译规则未改；存量跨表读取仓储的按表拆分不在本轮完成范围。
- **协议与消费者：** 顶层响应为 `gatewayconfiguration.response.GatewayPublicSnapshotResponseBody`，三种嵌套 Snapshot 同包改为独立 class；31 个 JSON 字段及类型、字符串 Code、集合不可变性和值相等语义保持。API 方法和字段补齐中文 JavaDoc、返回值、单位、可空及失败说明。Gateway 客户端、编译器和现有测试夹具同步类型/import/getter，未改变路由、Guard、安全校验、原子切换或 Last Valid 规则。HTTP 路径、`schemaVersion=1`、完整 200、合法空路由关闭全部入口、错误走公共 500，以及无 ETag/304 的合同保持；Java 消费者需同步重建 API 制品。
- **公共示例第 11 节自查：** Controller/Converter 仅处理协议，Processor 查询本模块仓储并在事务外编译，多表组合结果使用 View，不向 Web 暴露 DataObject。此接口没有用户输入字段，不增加 RequestBody 或字段校验器；无参数内部 Request 经模板非空检查。纯查询不需要写 Context、原子保存、CAS/对象回写、资源锁、业务防重、请求幂等或提交后外部调用。没有新增局部异常包装、人工 Bean 配置、生产 Mock、Schema 或网络暴露。
- **Verified（编译与静态）：** JDK 25 联合 Foundation 11、Platform 7、Gateway 3 个所需模块执行 `-DskipTests clean test-compile` 成功，日志 `/tmp/plt-gateway-snapshot-final-compile.log`。静态比对 `/tmp/plt-gateway-snapshot-static-check.py` 确认 31 个 Wire 字段/类型、转换赋值与顺序、两端编译规则和 SQL 保持，Gateway 20 个源码适配仅涉及类型/import/getter，原有其他修改未覆盖；两个仓库 `git diff --check` 通过。
- **验证边界：** 按用户要求未新增或运行单元/集成测试；现有测试仅适配类型/getter、class 字段检查和 Converter 包迁移，并完成编译。未运行期验证 Jackson 序列化、Spring 装配、MySQL 一致性、HTTP 刷新或就绪探针；未启动服务、执行 SQL、安装或发布制品、提交、推送或部署。历史需求资产与旧验收快照保留其原基线，不作为新 Java 契约的可执行验证。

<a id="platform-console-resource-refactor-20260917"></a>

## Platform Console Resource 管理重构（2026-09-17）

- **Implemented：** `ConsoleResourceManagementApi` 六项能力分别经 Controller/Converter 进入 Access Control 的 ListConsoleResources、GetConsoleResource、QueryConsoleResourceCapabilities、CreateConsoleResource、UpdateConsoleResource、ChangeConsoleResourceStatus Processor，删除旧 Web 聚合 Processor。API 拆出三个请求、五个响应/内嵌 class，共用保存回执与维护边界也迁为 `configurationmanagement.response` 下两个 class；Gateway/Permission 管理只同步共享 Java 类型，Operations BFF 同步协议转换。
- **本地保存与规则：** 三个写 Context 持有本表 `ConsoleResource` 原对象；本地 Service 仅依赖 `ConsoleResourceRepository`，不查询授权、关系或其他 Service。新 Mapper 维护本表读写，插入保留原列；条件更新保留资源 ID、OPERATIONS Channel 和原版本条件，SQL 成功后才回写原对象及版本/时间。写入保留 SERIALIZABLE、15 秒超时和权限绑定共享锁；树循环、8 层/2000 条上限、组件及按钮资格、受保护管理入口、停用引用保留规则保持。无变化编辑不更新版本或记录变更审计，沿用既有审计抑制能力；真正修改在版本上限时明确拒绝，避免递增溢出。
- **查询与模型：** 普通查询沿用无显式事务；原资源与权限 LEFT JOIN 的列、筛选、排序保持，直接映射 `ConsoleResourceView` 并补充支持标记和维护边界。资源对象、查询 View、编辑 Context 通过只读 `ConsoleResourceDefinition` 共用纯结构规则，写入不复制查询 View 或提前改动原对象。既有可授予资源及 Permission 保护查询只适配新 View getter，不改变权限含义。
- **锁与请求幂等：** 三个写入口使用 `AbstractLockProcessor` TRY_LOCK，实际逻辑 Key 为 `lock:console-resource:operations`，模板统一处理 Redis 中断/不可用。模块配置 `ps.access-control.console-resource.lock-wait-time` 默认 500ms、`lock-lease-time` 默认 30s，启动校验、重启生效。旧注解 Key 没有 `lock:`，混合版本不能视为共用 Redis 互斥，发布需协调切换；数据库保护继续保留。按用户已确认职责，删除这三个入口的本地 Hash/HMAC、幂等占位/回执/重放，Platform API 与 BFF 对应链路不再绑定或传递 HTTP Key；未实现或激活 Gateway 请求幂等，也未迁移其他配置命令。
- **公共示例第 11 节自查：** API class、字段注释和 Hibernate Validator 保留；数值构造参数显式 required，严格 JSON Converter 跟随新包。Controller/Converter 只做适配，Processor 负责当前授权、Repository 查询、候选校验、Context 与本地事务，Service 只保存，Repository 检查行数并回写同一对象。没有事务内外部调用、提交后外部步骤、额外写 View、DataObject 越界、叠加锁、虚构成功、Schema 或生产 Mock；创建的业务编码唯一保护和编辑/状态 CAS 不作为请求重放。接口仍由 HTTP Bean Validation 触发校验，无新增非 HTTP 消费者。
- **Verified（编译与静态）：** JDK 25 联合 Foundation 11、Platform 7、Operations BFF 3 个所需模块执行 `-DskipTests clean test-compile` 成功，日志 `/tmp/plt-console-resource-refactor-compile.log`；BFF 移除剩余 Key 参数后以同一 Reactor 执行 `-DskipTests test-compile`，日志 `/tmp/plt-console-resource-refactor-final-compile.log`。静态核对 `/tmp/plt-console-resource-static-check.py` 确认六个 HTTP 路径/方法/非 Key 参数、原审计标记、十个 DTO 字段及约束、原纯结构规则、完整查询 SQL、插入字段、条件更新和保存后对象回写顺序。
- **验证边界：** 按用户要求未新增或运行单元/集成测试；既有架构及纯规则夹具只适配已迁移类型、getter 和减少的三个注解锁入口，并参与编译。Spring 装配、严格 JSON 运行期解析、MyBatis/MySQL、Redis 并发/租约/回滚、审计及 Gateway 重放联调仍未验证，旧服务本地幂等测试场景需随后按 Gateway 职责迁移。独立消费者需更新共享 API 制品。保留原有工作区改动，未访问数据库、启动服务、安装制品、提交、推送或部署。

<a id="platform-operations-authorization-refactor-20260917"></a>

## Platform Operations 授权与导航接口重构（2026-09-17）

- **Implemented：** `OperationsAuthorizationApi` 的 authorize/navigation 分别经 Controller/Converter 进入 Access Control 的 `AuthorizeOperationsProcessor.process`、`QueryOperationsNavigationProcessor.process`，复用 Foundation 入口检查。API 原 AuthorizationModels/NavigationModels 容器拆为 request/response 下四个独立 class；Operations BFF 同步类型及 getter，保留自己的展示模型。删除旧授权 Service 和无消费者的导航中间模型，Web 管理 Processor 仅移除对应方法，其他管理能力保持。
- **查询与规则：** 两个 Processor 显式保留 15 秒 REPEATABLE READ 只读快照，组合当前会话、账号、成员、Route 和权限；无业务写入或外部步骤。普通身份、强制改密、Bootstrap 以及 HUMAN_AUTHENTICATED 路由规则保持。导航 Mapper 直接映射 `OperationsNavigationResourceView`，SQL 全部列、JOIN、筛选及排序保持，继续逐层剔除失去可见父节点的后代。View 保留原 `permissionId` 供可授予资源查询复用，HTTP 导航不暴露该字段。
- **审计与公共示例第 11 节自查：** HTTP 方法、路径、Header、字段、`@NotBlank` 和 Controller `@Audit` 标记保持；沿用此前模块 Processor 迁移的公开审计能力，在真实查询事务中登记完成观察，保持身份事实和 CONTEXT_RESTRICTED/PERMISSION_DENIED 原因，不新增局部异常映射。Controller/Converter 只适配，Processor 查询本模块 Repository 并组合既有本地会话能力，多表结果用 View，不泄露 DataObject。纯查询不需要写 Context、原子保存、锁、CAS/对象回写、业务提交比较或请求幂等；无新增非 HTTP 消费者、配置、Bean 工厂、Schema 或生产 Mock。
- **Verified（编译与静态）：** JDK 25 联合 Foundation 11、Platform 7、Operations BFF 3 个所需模块执行 `-DskipTests clean test-compile` 成功，日志 `/tmp/plt-operations-authorization-refactor-compile.log`。静态比对确认授权分支、导航迭代过滤、完整 SQL、HTTP 声明、审计注解保持；四个 DTO 字段、组件启用条件和事务参数已核对。Platform 差异和新增源码空白检查通过。
- **验证边界：** 按用户要求未新增或运行单元/集成测试；既有测试仅适配类型/getter，以及从 record 相等比较改为相同字段逐项断言，并完成编译。尚未运行期验证 Spring 装配、HTTP/MyBatis/MySQL、授权并发或审计链路。独立消费者需更新 Platform API 制品。保留此前 BFF 和根文档未提交改动；未启动服务、执行 SQL、安装制品、提交、推送或部署。

<a id="platform-customer-jwks-refactor-20260917"></a>

## Platform Customer JWKS 查询重构（2026-09-17）

- **Implemented：** `CustomerJwksController` 经共享 `JwksConverter` 构造无参数 `QueryCustomerJwksRequest`，调用 Access Control 的 `QueryCustomerJwksProcessor.process`，统一进入 Foundation 的调用方事务与非空请求检查。Controller、Processor、Service 保持 `client.authentication.enabled=true` 条件装配，未改变开关值；`CustomerJwksService` 仍供 `ClientTokenPreparation` 复用，结果通过既有 class 响应模型返回，不暴露 DataObject。
- **查询与异常：** Service 只执行一次 CUSTOMER 候选 SELECT，移除显式只读事务和空结果包装，使用应用共享 UTC Clock。原两秒数据库查询限制移至 CUSTOMER Mapper 语句的 `timeout="2"`，列、Realm、状态筛选、排序不变。生命周期、唯一 ACTIVE、RETIRING 930 秒重叠及到期边界、RSA/RS256、公开字段白名单、规范 Base64URLUInt、kid 一致与唯一、16 把/64 KiB 限制保持；不满足发布规则时改用公共 `ErrorException`，继续交给全局处理器返回系统错误，不添加局部 catch 或错误映射。
- **公共示例第 11 节自查：** 无参 GET、原路径和响应字段保持，不增加 HTTP Body、可选 Realm 或虚构字段；统一入口按用户要求保留 Processor，Service/Repository 各守本地能力边界。单表只读能力无本地原子写入或外部步骤，Context、View、资源锁、写时 CAS/对象回写、业务防重与请求幂等均不适用；使用默认组件扫描及构造器注入，无新增 Bean 工厂、Schema 或生产 Mock。
- **Verified（编译与静态）：** JDK 25 联合 Foundation 11 与 Platform 7 个所需模块执行 `-DskipTests test-compile` 成功，日志 `/tmp/plt-customer-jwks-refactor-compile.log`。静态比对确认九个校验方法、常量及查询主体（除 Clock 来源）不变，Mapper 仅增加 CUSTOMER 语句超时，API/响应类和令牌准备代码未变；Controller 仅调用 Processor.process，三处启用条件一致。
- **验证边界：** 按用户要求未新增或运行单元/集成测试；现有 Client HTTP 测试仅将 JWKS 夹具的两处系统时间同步为已注入 Clock，并完成源码编译。尚未运行期验证 Spring 装配、HTTP/MyBatis/MySQL、轮换及语句超时。未启动服务、执行 SQL、安装制品、提交、推送或部署。

<a id="platform-operations-jwks-refactor-20260917"></a>

## Platform Operations JWKS 查询重构（2026-09-17）

- **Implemented：** `OperationsJwksQueryApi` 返回 `api.authentication.response` 下独立普通 class `JsonWebKeySetResponseBody`/`JsonWebKeyResponseBody`，保持原路径、无参 GET 和 `keys`/`key_ops` 等七个 JWK 字段。按用户要求统一接口接入：`JwksConverter` 创建无参数 `QueryOperationsJwksRequest`，Controller 调用 Access Control 的 `QueryOperationsJwksProcessor.process`，经 `AbstractProcessor` 的调用方事务/非空请求检查后复用本地 `OperationsJwksService`，结果再由 Converter 投影。Service 同时被令牌准备流程使用，保留这一真实复用；JWKS 入口不受人类认证开关影响。共享 DTO 的 CUSTOMER Controller/API 与 Gateway `ReactiveJwksProvider` 同步类型及 getter，Gateway 严格解析、错误和缓存逻辑保持。
- **本地查询与对象边界：** Operations Service 只执行一次本库候选 SELECT，取消多余显式事务，改用应用统一 UTC Clock；原两秒数据库等待限制移为该语句的 `timeout="2"`。所有候选在同一查询结果和时间点完成纯内存校验，无外部依赖或写入。`JwtSigningKey` 从 record 改为普通 class，保留 11 个属性名称和类型；按表 Repository 直接返回所属模块对象，删除同义 `SigningKeyMaterial`。CUSTOMER Service 仅适配共享仓储类型，原查询事务及规则保持。Web 仍只接收不含持久化状态的 `PublicVerificationKey`。
- **合同与示例第 11 节自查：** 保留唯一 ACTIVE、RETIRING 930 秒重叠与半开到期边界、RSA-2048+/RS256、公开字段白名单、规范 Base64URLUInt、kid 一致及唯一、16 把/64 KiB 限制；Operations 规则拒绝改用公共 `ErrorException`，仍返回统一内部错误。该接口无调用方输入，无 Body 校验项；无参数查询按用户要求使用统一 Processor 入口，未添加可选 Realm 或虚构请求字段；没有写入，Context、View、锁、写事务、业务防重及请求幂等项不适用。未新增 Bean 工厂、Mock、表或字段，模块和 HTTP 边界不暴露 DataObject。
- **Verified：** JDK 25 联合 Foundation、Platform、两个 BFF 和 Gateway 的 27 个所需模块执行 `-DskipTests clean test-compile` 成功，日志 `/tmp/plt-operations-jwks-compile.log`。静态检查 `/tmp/plt-operations-jwks-static-check.py` 确认校验规则、Realm/列/筛选/排序 SQL、映射属性、HTTP 路径和 Gateway 行为保持；Mapper XML 仅增加 Operations SELECT 超时。临时序列化检查 `/tmp/CheckOperationsJwksWire.java` 使用已编译 DTO，验证 JSON 字段、`key_ops` 往返及 Owner 结果/HTTP Body 字节长度一致。后续统一 Processor 入口后，同一 27 模块 Reactor 执行 `-DskipTests test-compile` 成功，日志 `/tmp/plt-operations-jwks-processor-compile.log`；复核 Controller 仅调用 `.process`、无参请求和组件装配声明，公钥校验及签发共享 Service 未变。相关差异与文档链接检查通过。
- **验证边界：** 按用户要求未新增或修改仓库测试，未运行单元/集成测试；Spring 装配、真实 HTTP/MyBatis/MySQL、轮换并发及数据库超时尚未运行期验证。未执行 SQL、启动服务、安装制品、提交、发布或部署；独立消费者需使用更新后的 Platform API 制品。

<a id="platform-operations-authentication-refactor-20260917"></a>

## Platform Operations 认证接口重构（2026-09-17）

- **后续公共锁异常收敛：** 按用户要求保留账号/会话锁资源和锁前解析，创建/获取锁时的 Redis 异常统一由 Foundation `AbstractLockProcessor` 处理：直接或 Redisson 包装的中断恢复线程标记并抛公共并发错误，其他 `RedisException` 转为公共服务不可用错误，保留 Cause；锁内业务异常原样传播，释放失败不覆盖结果。移除 Operations 5、Client 8 个 Processor 的重复捕获，其中 Operations 改密/退出/首次管理员和 Client 退出不再重写 `processRequest`。静态比对确认这 13 个 Processor 的锁参数、锁内业务及后续方法逐字未变。JDK 25 联合 Foundation 与 Platform、两个 BFF、Gateway 的 27 个所需模块执行 `-DskipTests clean test-compile` 通过，日志 `/tmp/platform-lock-exception-compile.log`；`git diff --check` 通过。按用户要求未新增、修改或运行测试，本轮未验证真实 Redis 中断/不可用；未安装本地 Maven 制品、发布或部署，消费者单独构建/运行仍需使用包含此次修改的 Foundation 制品。
- **Implemented：** `OperationsAuthenticationApi` 六个入口分别进入 Access Control 的 LoginOperations、RefreshOperationsSession、LogoutOperations、ChangeOperationsPassword、CreateFirstOperationsAdministrator、GetOperationsContext Processor。Web Controller/Converter 只做协议适配，删除两个旧 Web 认证 Processor；API 原 Models 容器拆为 request/response 下七个独立 class，Operations BFF 同步 Java 类型和 getter，HTTP 路径、Header、字段、Bean Validation 及审计标记保持。
- **Context 与本地事务：** 五个写入口构造具名 Context，持有所属模块 DataObject；本地 `OperationsAuthenticationService` 只通过按表 Repository 保存，不调用其他 Service 或外部依赖。Repository 检查单行条件写结果并回写原对象。写事务为 READ COMMITTED、15 秒超时；当前上下文查询保留 REPEATABLE READ 只读快照。Vault、密码计算、刷新证明及令牌预签均在写事务前完成，提交前复核当前安全事实；本轮没有提交后外部操作。
- **安全语义与锁：** 登录失败计数先提交后返回拒绝；刷新保留 Session 行锁、单次消费和正确旧代复用撤销整个会话；改密原子推进账号/凭据并撤销全部会话；首次管理员账号、密码、成员、角色授予与 Bootstrap 关闭原子提交。五个写 Processor 使用 `AbstractLockProcessor` TRY_LOCK，账号与会话实际 Key 为 `lock:operations:account:{id}`、`lock:operations:session:{id}`；未知登录/刷新继续使用受控 HMAC 尝试桶。既有账号管理四个注解锁前缀同步；没有叠加锁。模块配置 `operations.authentication.lock-wait-time/lock-lease-time` 默认 500ms/30s，启动校验，变更需重启。
- **公共写接口示例第 11 节自查：** API class、Controller/Converter、Owner Request/Processor/Context、本地 Service、按表 Repository、条件 SQL 与对象同步已按职责对应；查询返回 `OperationsContextView`，DataObject 不越过模块或 HTTP 边界。入口仍由 HTTP Bean Validation 与 Header 转换触发校验，未新增非 HTTP 消费者。六个接口原本不携带 HTTP Idempotency-Key，原请求响应重放/Hash 比较不适用；刷新秘密复用属于必须保留的认证安全规则。沿用 Spring 组件扫描与现有基础包，未新增生产 Mock、共享幂等组件消费或数据库结构；无关管理接口保留既有编排。
- **Verified（编译与静态）：** JDK 25 联合 Reactor 对 Platform 7、Client BFF 3、Operations BFF 3、Gateway 3 模块执行 `-DskipTests clean test-compile` 成功，日志 `/tmp/plt-operations-auth-final-compile.log`。静态检查 `/tmp/plt-operations-auth-static-check.py` 确认六个接口签名/路由/Header、七个 DTO 字段及约束、五组审计标记不变，两个认证/目录 Mapper XML 与改动前逐字相同；本地认证 Service 仅依赖 Clock/Repository，旧类型引用已清理。相关改动通过 `git diff --check`。
- **验证与发布边界：** 按用户要求未新增或运行测试；既有测试只为类型、移动后的准备组件和锁 Key 做适配并编译。尚未验证 Spring 装配、MyBatis 映射、HTTP 认证链、数据库/Redis 竞争、回滚与审计运行期行为。旧版本锁 Key 没有 `lock:` 前缀，相关入口发布需协调版本，不能依赖混合版本间 Redis 互斥；数据库行锁、条件更新及唯一约束继续保留。未执行 SQL、启动服务、提交、推送或部署。

<a id="platform-client-authentication-refactor-20260917"></a>

## Platform Client 认证接口重构（2026-09-17）

- **Implemented：** `ClientAuthenticationApi` 九个接口迁入 Access Control 的 RegisterClient、RequestClientEmailVerification、RequestClientPasswordReset、VerifyClientEmail、ResetClientPassword、LoginClient、RefreshClientSession、LogoutClient、GetClientAccountContext Processor。Web Controller/Converter 只做协议适配；API 原 Models record 容器拆为 request/response 下十个独立普通 class，保留字段、Bean Validation、HTTP 路径/Header/状态及审计标记。Client BFF 同步 import/getter，不改变 Cookie、Origin、前端或 Gateway 合同。删除四个旧 Web 认证 Processor 和已无消费者的聚合 Repository、ClientTokenService。
- **阶段与事务：** 八个写 Processor 显式组织 READ COMMITTED、15 秒本地事务并登记审计事务完成观察。共享 Preparation 负责邮箱/Vault、挑战证明、密码校验和令牌签名准备；这些外部或计算步骤在写事务前完成，事务内重新核对当前账号、挑战、会话及签名密钥资格。Issue/ConsumeClientEmail、Login/Refresh/LogoutClient Context 持有本模块当前 DataObject；两个本地 Service 仅组合本库保存，不调用其他 Service 或外部依赖。注册账号/密码/挑战、验证消费/激活、重置密码/安全纪元/会话撤销、登录会话/首个刷新代、刷新消费/后继/会话推进各自原子提交。邮件在提交后发送；邮件依赖暂不可用仍返回既有 202，由重发流程恢复，不保存秘密重试材料。
- **安全与查询：** 保留反枚举公开响应、挑战用途/单次消费、失败计数提交后拒绝、正确旧刷新令牌重用后提交撤销、密码重置后撤销旧会话等原安全规则，不将其视为 HTTP 请求幂等。认证入口原本没有 Idempotency-Key，本轮没有新增命令幂等组件或响应重放。账号上下文保留 REPEATABLE READ 只读快照及 15 秒超时，以 `ClientAccountView` 穿过模块边界；HTTP 和审计不暴露 DataObject 或原始密码/秘密。
- **持久化：** CustomerAccount、CustomerPassword、EmailAuthenticationChallenge、ClientSession、ClientRefresh、ClientSigningKey 改为普通可变 class；Repository 按六张表拆分，条件写入检查受影响行数后回写原对象，回滚后的 Context 不复用。注册三项插入补齐行数检查。两个原 Mapper XML 与重构前相同，保留原唯一约束、行锁、CAS、锁定读取缓存设置与固定行锁顺序；没有新增 SQL、表或字段。
- **锁与配置：** 八个写入口使用 `AbstractLockProcessor` 的 TRY_LOCK；实际 Key 为 `lock:customer:email:{邮箱Token的HMAC}`、`lock:customer:account:{accountId}` 或 `lock:customer:session:{sessionId}`。`ClientAuthenticationProperties` 集中校验既有 CUSTOMER issuer 和新增 `client.authentication.lock-wait-time=500ms`、`lock-lease-time=30s`，配置变更需重启。模板负责事务外获取/释放，保留 Redis 不可用 503 与中断并发错误语义。Tenant Projection 原双资源注解机制保留，两个账号 Key 同步新前缀以协调同资源写入；旧、新版本混合时 Key 不同，部署须协调切换，不能视为 Redis 已跨版本互斥，数据库保护仍保留。未改变其他注解锁或 Scope 配置。
- **公共写接口示例第 11 节自查：** API class/字段约束和 Controller/Converter、Owner Processor/当前事实复核、完整 Context、本地 Service、事务内外步骤、按表 Repository/CAS/对象回写、实际资源锁、查询 View 与 DTO 边界逐项核对。HTTP Bean Validation 入口保留，无新增非 HTTP 公共入口；既有跨模块上下文查询仍执行当前会话资格检查。本能力没有可重放的业务提交身份，原提交比较及终态响应重放项不适用，挑战消费和刷新重用按安全语义处理。配置集中，复用 Foundation Processor/锁及现有时间/密码/端口能力，未加入生产 Mock、伪造成功或额外协议字段。
- **编译与静态验证：** JDK 25 临时联合 Reactor 对 Platform 7、Client BFF 3、Operations BFF 3、Gateway 3 模块执行 `-DskipTests clean test-compile` 成功；最后锁异常映射及配置整理后再次 `-DskipTests test-compile` 成功，日志 `/tmp/plt-client-auth-final-compile.log`。源码检查核对九个 HTTP 操作、十个 DTO 的字段与约束、两个不变 Mapper XML、八个模板锁、五个共享账号锁消费者、两个本地 Service 依赖及审计标记，记录 `/tmp/plt-client-auth-static-check.json`。本次差异 `git diff --check` 通过；排除任务开始前已有的 ApiScopeManagementApi 空白差异。
- **验证边界：** 按用户要求未新增或运行单元测试；现有测试只适配重命名/访问器、配置校验和已删除旧架构入口，并参与编译。未运行本次 Bean 装配、HTTP 认证、MySQL/Redis 竞争、回滚、审计、Vault/SMTP 或浏览器验证，不能把先前版本的链路结果外推到本次重构。没有执行数据库操作、启用入口、提交、推送、发布或部署；此前工作区修改保留。

<a id="platform-api-scope-management-refactor-20260917"></a>

## Platform API Scope 管理接口重构（2026-09-17）

- **Implemented：** `ApiScopeManagementApi` 的 scopes/scope/options/create/update 全部由 Access Control 对应的 `ListApiScopesProcessor`、`GetApiScopeProcessor`、`ListApiScopeOptionsProcessor`、`CreateApiScopeProcessor`、`UpdateApiScopeProcessor` 处理，使用组件扫描及构造器注入；删除旧 Web 聚合 Processor。Controller/Converter 只解析、规范化和投影。API 原 Models record 容器拆成 request/response 下 6 个独立普通 class，保留 HTTP 路径、JSON 字段、严格解码、Bean Validation、分页与各自读取权限；BFF 同步 Java 合同，引用聚合仍由 BFF `ApiScopeDetailsProcessor` 完成。
- **查询与对象：** 目录是单表查询，使用普通 class `ApiScope`，记录完整性检查位于该对象；跨模块继续返回既有领域 `Scope`，不新增同义 View、不向 Web 暴露 DataObject。列表/选项的筛选、排序、总数 SQL 保持原状；按接口合同保留授权、窗口和总数的同一 REPEATABLE READ 只读快照及 15 秒超时。
- **写入、Context 与事务：** 创建/更新各自使用 `AbstractLockProcessor`，共用 `lock:api-scope:{scopeCode}`；模块 `ApiScopeManagementProperties` 以 Duration 提供 `api-scope.management.lock-wait-time`（默认 500ms）与 `lock-lease-time`（默认 30s），启动校验，配置变更需重启，不读取旧注解锁配置。模板在事务外获取/释放锁；READ COMMITTED 事务内复核当前授权、唯一编码或原版本，构造 Create/Update Context，通过本地 Service/Repository 保存。更新保留数据库行锁、版本 CAS 和上限保护，SQL 行数正确后回写原对象名称、说明、版本及 UTC 微秒时间；回滚后丢弃 Context。审计仍使用受控字段和既有 Controller 链，没有外部调用或提交后业务推进。
- **请求幂等迁移：** 创建/更新的 Platform 与 BFF 方法不再接收或转发 Idempotency-Key，删除 Scope 专用 Hash 输入、命令上下文和已无消费者的 ClientApiMutationReference；不删除共享命令表或其他接口的既有调用。前端继续发送 key 供 Gateway 使用。重复到达 Platform 的创建依赖唯一 scopeCode 返回 400，旧版本更新返回 409，业务不重放原 HTTP 响应。Gateway 请求幂等的设计归属不代表已实现，状态见[统一职责记录](#gateway-request-idempotency-conventions-20260917)。
- **公共写接口示例第 11 节自查：** API class/字段约束、模块内 Request、Owner Processor、完整 Context、本地 Service、按表 Repository、事务及锁边界均已按职责落实。原 HTTP 命令查重/Hash 比较不适用；本能力没有独立业务提交身份，以唯一编码、当前版本保护业务。创建后直接响应、更新后从原对象构造结果，不增加远端步骤或中间 View。SQL、影响行数、对象回写与异常 Cause 保持明确；默认 Spring 装配，无手动 Bean 工厂或生产 Mock。
- **Verified（编译与静态）：** JDK 25 联合 Reactor 对 Platform 7、Operations BFF 3、Gateway 3 模块执行 `-DskipTests clean test-compile` 成功，日志 `/tmp/plt-api-scope-refactor-final-compile.log`。首轮发现轻量 API 模块无 Lombok，已改为普通构造器/getter，未增加依赖。静态比对确认 6 个 DTO 字段、全部 SELECT/INSERT SQL 与原合同一致，更新为原版本 CAS 加一；无 Scope 注解锁叠加和专用幂等消费。相关仓库 `git diff --check` 通过。
- **验证边界：** 按用户要求未新增或运行单元测试；现有测试只适配删除类型、Header、业务冲突及锁配置/Key，并完成源码编译。实际 Spring 装配、严格 HTTP 解码、MyBatis 映射、权限/Redis/数据库竞争、回滚和审计运行期回归尚未验证。模板锁 Key 与旧版本不同，混合版本间仍由数据库唯一约束/行锁/CAS 兜底；未执行 SQL、发布、部署或提交。

<a id="platform-api-credential-management-refactor-20260917"></a>

## Platform API Credential 详情、Scope 与撤销接口重构（2026-09-17）

- **Implemented：** `ApiCredentialManagementApi` 的 `credential/scopes/revoke` 分别进入 Access Control 的 `GetApiCredentialProcessor`、`ListApiCredentialScopesProcessor`、`RevokeApiCredentialProcessor`，使用组件扫描与构造器注入。Controller/Converter 负责协议转换；删除旧 Web `ApiCredentialManagementProcessor` 与 API Models 容器，三个响应及撤销请求改为独立普通 class。Operations BFF 同步适配 Java 类型和 getter，HTTP 路径与 JSON 字段保持；撤销 Header 合同按下述后续修订调整。
- **查询与 View：** 详情使用 `ApiCredentialDetailView` 复用摘要展示规则，轮换来源检查为 View 的纯内存方法；Scope 使用 `ApiCredentialScopeGrantView` 校验区间并计算 REVOKED/EXPIRED/PENDING/ACTIVE。移除旧 Credential/Grant 中间镜像和 Scope record 映射。Scope SQL 归入既有 Scope Repository/Mapper；两个查询保留同一 REPEATABLE READ 快照、15 秒超时与统一 UTC 微秒时点。
- **撤销 Context、保存与事务：** `RevokeApiCredentialContext` 直接持有已锁定和校验的可变 `ApiCredentialManagement` 及撤销时点。每次到达 Owner 的请求复核当前权限、目标与 expectedVersion，再由本地 `ApiCredentialManagementService` 通过 Repository 保存；原状态/版本 CAS 与影响行数检查保持，成功后同步原对象状态、版本及更新时间。READ COMMITTED 事务保持 15 秒超时，事务回滚后丢弃 Context；事务内只有本库访问与本地审计采集。已 REVOKED 且版本匹配时直接返回当前结果，不再次修改或追加变更审计。
- **锁与幂等归属修订（Implemented）：** 按用户后续纠正，撤销改为 `AbstractLockProcessor`，Controller 直接调用 `process`，删除 `@ResourceLock` 和额外代理入口。`TRY_LOCK` 使用逻辑 Key `lock:api-credential:{id}`（由模板添加前缀），沿用服务配置的有限 Wait/Lease，完整包围本地事务；数据库行锁/CAS 保留。移除撤销专用 Hash 命令、幂等服务调用、命令记录和响应引用编解码，内部请求直接包含凭证 ID、版本、原因；Platform/BFF 的撤销方法不再要求或传递 Idempotency-Key。前端仍生成并发送原幂等键，Scope 等其他接口继续沿用业务层幂等。严格 HTTP Body 校验、权限检查和响应 JSON 字段保持。
- **编译与静态验证：** 初次重构的 JDK 25 联合 Reactor 编译日志为 `/tmp/plt-credential-management-compile.log`。锁及幂等归属修订后再次对 Platform 7、Operations BFF 3、Gateway 3 模块执行 `-DskipTests test-compile` 成功，日志 `/tmp/plt-revoke-lock-refactor-final-compile.log`；只适配现有用例的撤销响应、Header 和模板锁 Key，移除已删除编解码器的用例引用，未新增或运行测试。静态检查确认撤销业务代码无专用 Hash/命令回执调用、无叠加锁，前端源码无本次改动，Mapper/SQL 保持原状。
- **公共写接口示例第 11 节自查：** API class 与 Bean Validation、Controller/Converter 适配、Owner Processor/Context、本地 Service 保存、Repository CAS/对象回写和实际凭证资源锁均保持对应职责。事务内无外部调用，写入不新增 View、不跨模块暴露 DataObject；删除撤销专用命令重放后，Owner 的原命令身份/Hash 比较项不适用，由当前状态与 expectedVersion 决定本地写入。静态差异及相关仓库 `git diff --check` 通过；运行期验证范围见下一项。
- **验证边界与待接入：** 当前 Gateway 源码与 README 仍为透传 Idempotency-Key，未实现本次安排的统一请求去重/响应重放；该能力属于后续 Gateway 接入，不能将本次 Owner 代码迁出视为端到端幂等已完成。旧版本请求若实际再次到达 Platform 会返回 409。模板与旧注解的锁 Key 不同，混合版本期间不共享 Redis 互斥，仍有数据库行锁/CAS 保护。按用户要求未运行单元测试，Bean 装配、Redis/MySQL 竞争、HTTP 与审计运行期回归未验证。未执行 SQL、提交、发布或部署。


<a id="platform-api-credential-list-refactor-20260917"></a>

## Platform API Credential credentials 列表重构（2026-09-17）

- **View 内聚调整（Implemented / 编译验证）：** 按用户要求，关联事实完整性、CLIENT 类型校验与展示状态派生收敛至 `ApiCredentialSummaryView.prepareForDisplay(asOf)`；Processor 只保留查询编排、空条目检查和统一查询时点。保留原异常分类、撤销优先和到期边界，View 不查库、不取当前时间、不修改持久化状态。JDK 25 `-DskipTests test-compile` 的 Platform 7 模块通过，`git diff --check` 通过；日志 `/tmp/plt-credentials-view-compile.log`。未新增或执行单元测试。
- **Implemented：** `ApiCredentialManagementApi#credentials` 经 Web `ApiCredentialListConverter` 转成内部 `ListApiCredentialsRequest`，由 Access Control 的 `ListApiCredentialsProcessor`（`@Component + @AllArgsConstructor`、Foundation `AbstractProcessor`）编排。会话规则复用 `OperationsSessionService`，普通会话与 `API_CREDENTIAL_READ` 权限由 Processor 组合检查；列表和总数只读取本模块 Repository。移除旧 Web Processor/Service 中的列表方法及重复摘要转换。
- **协议与查询：** HTTP GET 路径、Header、Query、13 个条目 JSON 字段和分页语义保持；既有严格 Query 解析及分页校验移至 Converter，不新增 GET Body。列表由 SQL 直接映射普通 class `ApiCredentialSummaryView`，不再经过详情用的 `Credential` 镜像；独立 `response.ApiCredentialSummaryResponseBody` 使用普通 class 与属性绑定，Operations BFF 同步适配。多表 JOIN、筛选、排序、Scope EXISTS 去重与总数 SQL 口径保持，列表不读取 PHC 或轮换详情。
- **事务与规范自查：** 保留 REPEATABLE READ 只读事务、15 秒超时和同一 UTC 微秒状态时点，权限校验先于列表/总数；保留失效 Tenant 可见、撤销优先和损坏关联拒绝规则。审计公开能力增加事务观察入口，委托原 `AuditContext` 注册完成通知；事务内只有本库读取与本地审计采集，没有外部调用。纯查询没有本地原子写入、命令重放、资源锁或保存 Context；这些公共写接口示例项不适用。详情、Scope 和撤销入口的既有实现不在本次迁移范围。
- **编译与静态验证：** JDK 25 下，临时联合 Reactor 对 Platform 7 模块、Operations BFF 3 模块、Gateway 3 模块执行 `-DskipTests clean test-compile` 成功；最终鉴权编排调整后再次执行 `-DskipTests test-compile` 成功。日志分别为 `/tmp/plt-credentials-compile.log`、`/tmp/plt-credentials-final-compile.log`。Mapper XML 解析及两个应用仓库 `git diff --check` 通过。
- **验证边界：** 按用户要求未新增或执行单元测试；现有测试仅编译，运行期 Bean 装配、HTTP/数据库一致性和审计回归尚未验证。未执行 SQL、部署、提交或发布。

<a id="platform-api-credential-validation-tests-20260917"></a>

## Platform PLT-I03 validate 接口自动测试（2026-09-17）

- **Implemented：** 用户后续明确要求补充 `ApiCredentialValidationApi#validate` 测试。沿用整理后的测试模块和 `MockTestBase`，通过测试组合根引用正式 `PlatformSupportApplication`，新增成功、401 拒绝、503 数据异常、并发快照四组真实 HTTP 入口测试；未恢复用户已删除的旧测试，也未修改生产代码。辅助组件使用 TestComponent/构造器注入，复用 Foundation HttpTestInvoker、AbstractHttpResponseChecker 和 TestBase/Clear。
- **场景与生命周期：** 覆盖可信 Client 身份、Scope 隔离/排序/去重/64 项上限、Credential 长度与大小写、重复 Header、错误秘密、全部合法失效状态、微秒级到期/授权窗口、关联/版本/枚举/PHC 损坏、撤销后下次认证，以及两次 SQL 间另一连接提交新 Scope 时的 REPEATABLE READ 快照。全局 BeforeEach 检查基线，类级 BeforeEach 插入本例事实；每次请求验证只读数据库事实、零审计和安全响应，清理前检查日志 canary。Clear 按已登记 ID/Request ID 回收数据并验证，无整表删除。
- **Verified：** JDK 25 临时 Foundation/Platform/CCT 联合 Reactor，`-pl :platform-support-test -am -Dtest=ApiCredentialValidationSuccessTest,ApiCredentialValidationRejectionTest,ApiCredentialValidationUnavailableTest,ApiCredentialValidationConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test`，21 模块构建成功，四组共 **74/74**，0 failures/errors/skipped。证据为 `platform-support-test/target/surefire-reports`，本次构建日志 `/tmp/api-credential-tests.log`。成功请求的真实 JDBC 读取同时验证只读、REPEATABLE READ 及两次查询共享连接；并发测试证明当前请求看旧 Scope、下一请求看到新提交。
- **Mock 启动修正（Implemented / Verified）：** 用户指出 IDEA 启动不应另配密码后，`MockTestBase` 统一接入测试专用 `LocalMockEnvironmentInitializer`，按编译产物/工作目录定位所属工作区，仅在 `mock` Profile 自动加载已授权 `.env.local` 的平台 MySQL 与 Redis 密码，保留显式覆盖，不导入其他秘密或执行 Shell。数据库前置错误区分认证、连通性和表结构，消息只保留安全 SQLState/错误码。新增 6 个合成文件配置机制用例；从测试模块目录启动、移除四个密码环境变量且不使用外部注入脚本，联合 Reactor 共 **80/80** 通过（原 74 个接口用例 + 6 个配置用例），0 failures/errors/skipped。日志 `/tmp/platform-mock-auto-connection-tests.log`；未通过 IDEA UI 执行，IDEA 使用同一 Spring 测试初始化入口。
- **环境与边界：** 本地配置读取经用户明确授权，密码只保留在测试进程，不输出或写入测试配置。连接仓库已有 MySQL；新增 `application-mock.yml` 配置清单、串行 JUnit 配置及五张所需表的人工 `init/ddl.sql`，没有执行 DDL/迁移。该接口没有外部 HTTP、Redis 锁、MQ 或业务原子写入，相应 Mock/副作用场景不适用。本次不宣称全服务覆盖率、真实依赖断连/超时场景或其他接口回归已通过；未提交、发布或部署。此前“暂不执行测试”是下述重构阶段的历史边界，当前验证以本节为准。

<a id="platform-api-credential-refactor-20260917"></a>

## Platform PLT-I03 validate 按开发手册重构（2026-09-17）

- **Bean 装配简化（Implemented / 编译验证）：** 按用户后续要求，Processor 改用 `@Component + @AllArgsConstructor`，删除专门手动构造 Processor 的 `ApiCredentialConfiguration`。Web `ApplicationConfiguration` 提供统一 UTC Clock，Operations/Client 认证配置移除重复时钟工厂，生产时钟不再受认证开关影响。`-DskipTests clean test-compile` 的 Platform 7 模块编译通过，日志 `/tmp/plt-validate-component-compile.log`；源码确认只有一个生产 Clock 工厂且无旧工厂名称引用。继续按用户要求暂不执行单元测试，未验证运行期装配或数据库链路。
- **Implemented：** `ApiCredentialValidationApi#validate` 委托 Access Control 的 `ApiCredentialValidationProcessor`，复用 Foundation `AbstractProcessor` 拒绝调用方已有事务。Controller/Converter 仅处理协议和身份字段转换；删除原认证 Service/Impl 及重复 Domain 持久化镜像。多表查询使用 `ApiCredentialCandidateView`/`ApiCredentialIdentityView`，Context 和只读数据库映射使用 class，Scope 查询拆入独立 Repository/Mapper。
- **合同与事务：** 路径、单一 Credential Header、五个 JSON 字段、Mono、401/503、失败关闭和敏感日志边界不变。响应迁至 `api.apicredential.response.ValidatedCredentialResponseBody` 独立 class，Gateway 与已有测试源码同步 import/getter。保留原两次 SQL 的 REPEATABLE READ 只读快照，PHC 校验在事务结束后执行；无业务写入、命令幂等、资源锁、远端调用或 Schema 变更。SQL 条件与缓存设置逐项比对未变。
- **Verified（仅编译/静态）：** JDK 25 下通过临时聚合 Reactor 对 Platform/Gateway 执行 `-DskipTests clean test-compile`，10 个模块全部编译成功，包含已有测试源码；日志 `/tmp/plt-validate-clean-compile.log`。旧类型引用、Mapper 绑定与差异空白检查通过。临时聚合只用于源码编译，未安装或发布 Maven 制品。
- **Pending / 授权边界：** 用户在任务中明确“先不用考虑单元测试”，此后未新增场景或执行测试；上述结果不是接口、数据库、并发、日志或运行期验收。该指令前启动的契约迁移失败检查仅说明旧类型尚不符合目标结构，不作为重构后通过证据。尚未提交、推送、部署或执行数据库 SQL。

<a id="coding-conventions-payment-20260916"></a>

## 写接口规范以 Payment 为基准合并（2026-09-16）

- **公共示例补充（Documented / Verified）：** 用户确认后新增[公共写接口开发示例](examples/WRITE_API_EXAMPLE.md)，覆盖 API 至 Mapper 的调用链、规范化 Processor 主流程、事务边界及交付自查；Payment 本地规范收敛为业务补充，保留原章节锚点。根 AGENTS、Payment 独立仓库入口、规范导航和规则摘要已要求实现前阅读与完成后自查。11 段 Java 与 1 段 XML 示例已对照源码核验，明确标注的事务外推进方法只作规范化示意；链接检查无新增失效，规范静态检查及修改测试的 lint 通过。`npm --prefix requirements-site test` 构建通过，137 项中 133 通过，4 项既有 Commerce 路由合同/模型统计断言失败（日志 `/tmp/write-api-example-tests.log`），失败涉及本次未修改的合同与统计。该检查验证文档入口和内容，不证明 AI 必然执行规则；本轮未改业务 Java、数据库或运行配置，未提交或部署。
- **Documented：** 用户确认后，按[合并决定](decisions/2026-09-16-payment-based-coding-conventions.md)将 class DTO、业务模块 Processor、查询与校验、Context、本地 Service、事务组合、按表 Repository、请求防重及配置约定上收公共规范。同步工程检查清单、技术栈的模板锁/既有注解锁边界、AGENTS/导航及四份规则摘要；Payment 保留应用示例，受影响旧决定增加窄范围后继路由。
- **Verified / Existing failure：** 规范相关的三项静态测试通过；`npm --prefix requirements-site test` 完成构建，137 项检查中 136 通过、1 项失败，日志 `/tmp/payment-conventions-site-tests-final.log`。失败为既有 Risk 模型统计断言（测试预期 78/944/...）与台账不一致，已与本轮修改前副本核对：该统计行和失败条件均未由本轮改变。639 个本地文档链接/锚点检查无新增错误，12 个既有失效引用未混入本轮修复；记录 `/tmp/payment-conventions-links.json`。本轮没有 Java 行为变更，不运行 Java 构建来替代文档检查。
- **Pending：** 既有 Java 仓库尚未按新规则全量审计或迁移；尤其既有 Service 外部调用、Web 编排、Models + record 和注解锁消费者仍按各自任务逐步调整。Payment 暂缓测试及本期 Mock 不推广为公共默认。本轮不改业务源码、业务合同、SQL、环境或部署，未提交。

## 2. 应用与基础设施

| 组件 | Implemented | Verified | Deployed | Pending / 未证明 |
|---|---|---|---|---|
| Requirements Site | 需求目录和全局序列源文件已存在，仍是需求事实入口；全系统 Problem Profile 决策已投影到现行需求目录 | 2026-09-11 构建及内容门禁 136/136，lint 通过 | 仅本地站点，不对外发布 | 需求存在不代表六系统全部实现或已部署 |
| Build / Foundation | Maven 构建治理、框架无关 `paytech-http-problem`、严格/迁移期 HTTP Client Reader、唯一 MVC Problem Writer、稳定公共 Header 名称、可复用显式-null/严格时间反序列化器，以及声明式 `@ResourceLock` 已在当前工作区实现 | 2026-09-08 当前 Header/JSON 绑定/MVC Problem 聚焦门禁 29/29，HTTP Problem Profile 5/5；此前 DTO 清理后的 Problem 相关 Reactor 326/326、注入仓库本地 Redis 凭据后的全 Foundation 505/505 是对应历史源码基线 | 最新 Snapshot 仅安装到本机 Maven 仓库供下游源码验证，未发布 Nexus、未部署 | Legacy Reader 须在部署与观测完成后删除；Compensation Event 测试仍自动重建 Schema；Resource Lock 尚未发布或部署 |
| API Gateway | 动态 Snapshot、数据库 Target/Route 绑定与转发、BFF 管理监听隔离补改已实现，见第 17 节；Client Credential 链源码仍保持；当前另补 CLIENT 人类认证入口，见 [Client 切片](#client-console-onboarding-20260914) | 2026-09-15 Gateway 全量 1261/1261；指令 96.624745%、分支 90.051323%，覆盖率/边界通过；对端为协议 Simulator、Redis 为真实本机服务。RCO-16/Platform 历史证据仍见第 17 节 | 2026-09-12 本轮动态路由完整制品已部署 mor-test，原制品保持，RCO-17 注册后 Snapshot 接受 61 条 Operations Route；含用户已接受的既有 Client 代码 | 2026-10-14 已随 Client 入驻发布在 mor-test 开启 Client 通道（沿用 `ba36b63` 镜像、仅改环境变量），见 [Client 切片](#client-console-onboarding-20260914)；“通道关闭仍校验 Client 配置”缺陷未修；Netty ByteBuf 警告在 HEAD 对照同样复现，待处理；RCO-17 test 功能验收按用户要求由用户执行，见第 17 节 |
| Platform Support | 当前工作区已将现有实现拆为 `api + access-control + audit-security + file-document + delivery + web + test`；Web 仍是唯一应用和组合根，全部模块仍使用一个 Platform 数据库和主 DataSource，审计保留同库专用写池；既有合同保持兼容，新增 Client PLT-I17 私网开通/状态投影与 F01/F02 上传及 FILE_SCAN 持久化恢复，见 [Client 切片](#client-console-onboarding-20260914) | 2026-09-12 RCO-16 在获准独立本机库完成 `clean verify` 386/386，指令 97.784105%、分支 90.058766%；原共享库不满足仅初始化前置的问题通过独立验证库处理，保留原有人工数据；详见第 17 节 | 既有 Platform 应用容器保持；2026-09-12 RCO-16 的 182 行注册及发布状态 SQL、Target 表注释已应用 mor-test；RCO-17 另应用 118 条注册及入口状态 SQL，见 [RCO-17](#rco17-test-20260912)；模块拆分和 Problem 映射源码未部署 | 精确制品及环境发布另行核对；CUSTOMER 本机认证持久化已聚焦验证；跨 Gateway/BFF/浏览器集成见 Client 切片、生产 Vault/KMS、Console Resource、MQ 和生产部署；其余资源锁/HMAC 风险见第 8 节 |
| Client Console BFF / Web | 认证、申请导航、个人/组织单页、人员/关系、材料及受控目录聚合/交互，见 [Client 切片](#client-console-onboarding-20260914) | 2026-09-16 BFF clean verify 27/27；Web 公共字段反馈后续 26/26 与构建、前次预览认证 5/5；浏览器合成协议验证桌面/390px、目录联动、只读及登录/退出拦截 | 2026-10-14 已部署 mor-test（`https://localhost:28443`，39 条入驻路由），API 级验收 39/39，见 [Client 切片](#client-console-onboarding-20260914) | 只覆盖注册与入驻；工作区及其后能力未实现；浏览器人工抽查待用户执行；未完成项见 Client 切片 |
| Operations Console BFF | 独立 `web + test` Maven 仓库；当前工作区除既有页面适配外，已使用 Foundation Writer 输出统一 Problem，并在边界重建自身 503/504，保留显式上游迁移 Reader | 2026-09-06 JDK 25 `clean verify` 315/315；严格 Profile 与真实 `HEAD` 聚焦门禁 272/272 | 2026-09-12 RCO-17 BFF 已部署 `mor-test`，19201 回环管理监听健康；配置转发及 422 错误投影真实入口通过，待用户验收；见 [RCO-17](#rco17-test-20260912) | 无数据库和签名密钥；不拥有核心授权或跨系统事务；Legacy Reader 待部署证据后删除 |
| Operations Console Web | React/TypeScript/Vite/Ant Design 已实现；当前工作区的 API 消费者严格读取 Problem、显示安全 detail/requestId，并为已知目标保留显式旧 Reader | 2026-09-06 测试 216/216，TypeScript 与 Vite 生产构建通过 | 2026-09-12 RCO-17 Web 已部署 `mor-test`，HTML/全部静态资源与批准镜像匹配，浏览器限定验收通过；待用户验收；见 [RCO-17](#rco17-test-20260912) | Client/Partner 未来会话方案不由此决定；Legacy Reader 待部署证据后删除 |
| Vault V1 | Go Server、OpenAPI 与 Java Client 的当前工作区已切换统一 Problem/`HEAD`/Request ID 合同；三表 Protect/Reveal 业务边界不变。2026-10-14 Go Server 内部按 [Go 规范](./GO_DEVELOPMENT_CONVENTIONS.md) 重构为 Kratos 布局（server/service/biz/data + sqlc），新增 `test/` 独立入口测试；HTTP 合同、Schema 与 Java Client 不变。2026-10-14/15 工作区（未提交）按 [Vault 策略入库决定](./decisions/2026-10-14-vault-policy-and-manual-access.md) 实现策略三表与审计扩列（六表/64 字段）、只从数据库读取并定时全量刷新（删除 `VAULT_POLICY_FILE`）、11 个 `/vault/v1/admin/**` 管理接口（[OpenAPI](../paytech/vault/api/openapi/admin-v1.yaml)）与人工加/解密；Java `VaultAdminClient`（`vault.client.admin-enabled`，默认关闭）、Operations BFF 13 个 `/vault-admin/v1` 接口与 Web 6 个页面已实现（未联调、未部署），见[需求交付记录](../requirements/20261014/operations-console-Vault数据类型与手动加解密/delivery.md) | 2026-10-14 本机 `scripts/verify.sh` 全部通过：Schema 预检、OpenAPI/Golden、gofmt、`sqlc diff`、golangci-lint 0 issues、vet、Go 测试与 race（121 个测试函数，重构前后一致）、实际二进制构建、Maven `clean verify`（Java Client 与黑盒）、敏感金丝雀扫描；数据库为本机 `mor_vault`，测试只清理自有数据。2026-10-15 策略入库工作区再次 `scripts/verify.sh` 13 项全部通过（139 个 Go 测试函数；Maven 客户端 40、黑盒 10，含真实 Go Vault 的管理接口往返）；Operations BFF `verify` 1286/1286、Web 996/996 与构建通过；PDManer 生成器测试 13/13 | 既有 `mor-test` 合成 smoke 部署保持不变（未含 Problem 与重构源码）；2026-10-14 本机 local 的 Vault 已改为 Docker 运行（`vault` Profile，容器 `mor-local-vault-service-1`，提交 `3b17de0` 构建，`local-environment/vault/vault.sh`）：容器 healthy，Nacos `mor-local` 仅 `127.0.0.1:18107` 一个实例，读回宿主机进程时期写入的记录成功，错误用途 403；宿主机进程已停止，`run-service.sh vault` 与容器互斥，旧二进制保留为 `local-environment/vault/bin/vault-server.prev`。2026-10-14 经授权在本机 `mor_vault` 执行策略三表 DDL 与 local 初始化 DML（运行中的旧容器不读这些表、不受影响）。2026-10-15 阶段 5 本机联调：容器已按策略入库版本重建（从库加载策略、10 秒全量刷新、`VAULT_ADMIN_CALLERS=operations-console-bff`）；经授权在 `mor_platform_e2e` 执行 [Platform 注册 DML](../requirements/20261014/operations-console-Vault数据类型与手动加解密/db/platform-support-service/registration-dml.sql)（174 行）与[本机激活](../requirements/20261014/operations-console-Vault数据类型与手动加解密/db/platform-support-service/activation-local.sql)，Operations BFF 已用新 jar 重启；经 Nginx 运营入口 API 验收 27 项、浏览器验收 19 项全部通过，测试数据已清理（`mor_vault` 回到初始化数据）。本机 DDL/DML 首次执行时 mysql 客户端为 latin1，中文注释、菜单名与初始化理由曾写成乱码，已就地修正（定义除注释外不变），SQL 文件头已注明用 utf8mb4 执行。2026-10-15 已发布 `mor-test`（[发布记录](../requirements/20261014/operations-console-Vault数据类型与手动加解密/release/release.md)）：`mor_vault` 执行 DDL 与 dml-test（4 类型、11 规则），`mor_platform` 登记 174 行并启用；Vault、Operations BFF、Web 从提交 `34ecac9` 干净构建并替换，Gateway/Platform/Risk 未升级；经 `http://localhost:28080` API 验收 27 项、浏览器 19 项、23 个页面回归通过，验收数据已清理 | 2026-10-15 Platform 已删除生产代码中的 `LocalLoginEmailVault`（已提交、未部署）：Operations 登录邮箱在所有 profile 都走正式 VaultClient，自动测试继续用 HTTP Simulator；本机已经运营入口补齐 Platform 策略（`CHANNEL_ACCOUNT_CONFIG_V1`、`NOTIFICATION_PROVIDER_CREDENTIAL_V1` 两个 SECRET/REFERENCE 类型，渠道执行解密、通知邮箱加解密、通知凭据解密 4 条规则，策略版本 8）；2026-10-15 已发布 mor-test（[发布记录](../requirements/20261014/operations-console-Vault数据类型与手动加解密/release/platform-release.md)）：Platform 从 `84b3174` 干净构建替换，test Vault 补齐同样 2 类型 4 规则（版本 7），管理员登录邮箱 Token 已迁移为真实 Vault Token；其余 28 个 OPERATIONS 账号（1 个 ACTIVE `1***@xx.com` 按用户决定不迁移、3 个 DISABLED、24 个 CLOSED）仍为 `mock_` Token，无法登录；2026-10-15 数据类型值格式约束（[需求](../requirements/20261015/operations-console-Vault数据类型值格式约束/requirement.md)，提交 `994d5b0`）：数据类型声明 `TEXT`/`JSON`/`REGEX`（RE2 整值匹配），服务 Protect 与人工加密统一校验、只校验不规范化，新增 4 个错误码；本机与 mor-test 均已执行 DDL（`value_format` 默认 TEXT、`value_pattern`）并部署（test Vault `42d1f5d6`、BFF `aa546ee6`、Web `7e369f33`，[发布记录](../requirements/20261015/operations-console-Vault数据类型值格式约束/release/release.md)），两环境的 `EMAIL_ADDRESS_V1`/`PHONE_E164_V1` 已设为 REGEX、两个配置类型设为 JSON；CCT、Platform 等仍用旧 Java 客户端，遇到新错误码会归类为上游响应无效，随各自下次发布升级；生产 KMS、强服务身份、真实消费者、合规验证和生产部署未完成；Legacy Java Reader 待删除 |
| CCT | 单份主资料、人员/关系、材料引用、只读申请导航与恢复，见 [Client 切片](#client-console-onboarding-20260914) | 2026-09-16 clean verify 140/140（13 Suite）；真实 MySQL/Redis，外围 HTTP Simulator；38 表夹具回收 | 应用未部署，Client 入口关闭 | 正式提交、永久身份绑定、Risk/Operations 审核和真实 Owner 联调待完成 |
| Commerce | 15 表/249 字段目标模型；本期新增 Customer 三操作及 Payment 内部四操作，准备时冻结 OE/合同/税事实并创建 Intent，见 [COM-CUSTOMER-01](#commerce-customer-20260912)；基础创建仍只做预检 | JDK 25 `clean verify` 331/331，0 失败/错误/跳过；真实本地 MySQL/HTTP、按需顺序查询与独立 Handler 恢复验证；JaCoCo 行覆盖率 91.78%（3027/3298）；模型沿用上一轮 17/17 | 应用未部署；获授权本机三表增量 SQL 已执行，本地 Mock 启动入口已验证并关闭 | 本机保留旧列的兼容结构不等于目标新库 Schema；新 Owner 端口为 Mock + TODO，真实跨服务、支付成功事实消费、晚到定价、Payment Card、公共路由及前端联调未完成 |
| Payment | 27 表/345 字段模型；Channel 五表与两个公开查询见[本地实现](#channel-account-mid-query-requirement-20260921)，Execution 另见[本地切片](#payment-execution-implementation) | Channel JDK 25 clean verify 56 项、模型 18 项通过；专项范围与覆盖率见下方 | 未部署；仅初始化 mor_payment_channel_test，既有业务库未迁移 | Routing 占位消费者、真实渠道、共享库迁移及真实 Vault 策略仍待接入 |
| Finance | 84 表/1,109 字段目标模型、完整人工 Schema 与初始化 DML；Accounting 第一批账务内核（基础资料、过账内核、人工调整 Maker-Checker）、entityNo 余额查询、FIN-I05 账本查询、试算平衡、期间关账与重开、年末结转、交易自动记账引擎及渠道成本配置已实现，见 [账务内核](#finance-accounting-kernel-20261015)、[账本查询](#finance-ledger-query-20261015)、[试算平衡](#finance-trial-balance-20261016)、[期间关账](#finance-period-close-20261016)、[交易自动记账](#finance-auto-posting-20261016)、[期间重开与年末结转](#finance-period-reopen-20261017)、[渠道成本配置](#finance-channel-cost-20261017) | 2026-10-17 JDK 25 `clean verify` 242/242，本地 MySQL/Redis；`-exec.jar` 本地 HTTP 冒烟通过 | 未部署；本地 `mor_finance` 已执行完整 Schema | 交易经济创建与 PaymentSucceeded 消费、成熟、对账、出款、税务、跨期间连锁重开、渠道清算账户维护未实现；接口未接统一身份与 Gateway；手工缴税异常分支待专项模型闭合 |
| Risk & Compliance | 71 表/九路由配置切片；本次原 V1 增加可空终态、冷却/主体标签参数、手工拒绝文案、Risk-owned 主体绑定与显式历史格式兼容，见第 15 节 | 2026-09-11 JDK 25 `clean verify` 155/155，含真实本地 MySQL/随机端口 HTTP、旧 Hash Golden 和配置冻结门禁；模型/Schema 189/189 | 2026-09-12 RCO-17 已部署本机 `mor-test`，实际 78 表；四表及三表精简、历史回填与兼容比对完成，见 [RCO-17](#rco17-test-20260912) | Risk 配置的真实 Nginx/Gateway/BFF/Risk HTTP 链已验收；PDManer 人工 Review、其他环境/生产仍待完成；运行期评估和真实动作不在本期范围；全局 COMP-01/DB-01/Vault V1 Gate 保持 |
| `mor-local` | 仓库提供 MySQL、Redis、RabbitMQ、Nacos 等本地依赖的手工环境 | 当前本机运行状态应按 [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 的命令即时核对 | 仅本机开发环境 | 自动测试只能复用该环境，不得自行拉起替代容器或连接真实业务环境 |
| `mor-test` | 独立 Docker 测试拓扑当前为 11 个容器、61 条 ACTIVE Operations Route、35 个 ACTIVE Console Resource；RCO-17 证据见下，浏览器入口为 `http://localhost:28080` | 已记录真实登录/IAM/审计/Refresh/Logout、Gateway 控制面启停/幂等/版本冲突/Snapshot 恢复与 Vault 合成 smoke | 本机独立测试环境；Gateway 控制面与 Risk V1 配置入口已部署，11 个容器健康 | 不是 Maven `auto`、覆盖率或生产证据；生产部署仍未声明 |

组件级命令、精确测试数字和运行配置由各仓库 README/实施记录维护，本页不复制会频繁变化的日志。

- **RCO-14 Implemented / Verified / Deployed（2026-09-11，mor-test）：** 原 V1 的 Risk、Operations BFF/Web 配置增量已实现；[需求与接口合同](./plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)覆盖三条既有接口扩展，新增 API 和 Platform 注册 DML 均为 0。真实 MySQL/HTTP、BFF 转发/授权、前端和模型验证通过；开发库和 test Risk 库分别在用户授权、备份后应用增量 SQL；三个应用已部署，真实入口验收通过。精确证据及范围见第 15 节。

<a id="gateway-idempotency-key-20261017"></a>

## Gateway 处理 Idempotency-Key（2026-10-17，已本地提交 `b0c8b0e`、`69d868e`，未推送；2026-10-17 已部署 mor-test）

- **Implemented：** 按[需求](../requirements/20261017/gateway-请求幂等IdempotencyKey/requirement.md)与[技术方案](../requirements/20261017/gateway-请求幂等IdempotencyKey/technique.md)落地 [2026-09-17 决定](./decisions/2026-09-17-gateway-request-idempotency.md)：
  - **Platform 路由**：新增 `idempotencyMode`（`t_gateway_route.idempotency_mode`，默认 `NONE`），只允许 CLIENT_API 非 GET/HEAD 且不转发 `idempotency-key` 的路由设为 `REQUIRED`；更新时不传该字段保持原值；Operations 后台可查看和设置。
  - **Gateway**：对 `REQUIRED` 路由校验 Key，按“通道 + 调用方（Client API 为 Client）”与原始字节摘要在 Redis 原子占位；下游首次响应（含 4xx/5xx，≤64 KiB）记录 72 小时并逐字重放（Problem 的 `requestId` 改写为本次）；换请求 409 `IDEMPOTENCY_KEY_REUSED`，执行中 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` + `Retry-After: 3`；下游无响应时释放 Key；Redis 故障时返回 503。
  - **Commerce**：不再声明或处理该 Header，移除 `paytech-command-idempotency`。
- **Verified：** Gateway 1277/1277、Platform 736/736、Operations BFF 1386/1386、Web 1068/1068、Commerce 808/808；mor-test `client_api_flow.py --idempotency` 89/89（含并发同 Key 只建 1 单）。详见[发布记录](../requirements/20261017/gateway-请求幂等IdempotencyKey/release/release-20261017.md)。
- **Deployed（mor-test）：** Gateway、Commerce 由本需求替换；Platform、Operations BFF / Web 由 SES 会话从同时包含 SES、Risk 提交的 `6461091` 合并替换；Client API 建单、取消、退款 3 条路由为 `REQUIRED`。
- **Pending：**
  - 幂等记录存 Redis（与限流共用连接），以后要迁数据库（用户 2026-10-17 决定 D8，存储层已是可替换接口）；
  - Operations 路由与 Platform `OperationsCommandIdempotencyService` / `t_command_idempotency` 未迁移；
  - Client Console 通道未启用；
  - Payment 等内部接口的 Key Header 未清理；
  - Operations 设计稿未补“请求幂等”选择。

<a id="client-api-mor-test-20261017"></a>

## Client API 在 mor-test 联通（2026-10-17，已提交并推送 `98ba37c`、`7cf1757`；2026-10-17 已部署 mor-test）

- **Deployed（mor-test）：** 按[需求](../requirements/20261017/commerce-ClientAPI测试环境联通/requirement.md)与[技术方案](../requirements/20261017/commerce-ClientAPI测试环境联通/technique.md)：Commerce 首次部署 test（镜像 `client-api-20261017`，172.30.73.30，`mor_commerce` 17 表）；Nginx 新增 Client API 入口 `https://localhost:28444/api/v1/`（`X-Entry-Channel: CLIENT_API`）；Platform 登记 Target `commerce-client-api` 与 9 条 CLIENT_API 路由（设计冻结清单中结账查询未实现，不登记），`t_api_scope` 补齐 5 个 Scope；XXL-Job Admin 常驻（根路径，`compose.sh` 默认加载），Commerce 与 Platform 补偿扫描每 30 秒；Platform 只增加验收接收端放行（172.30.73.31/32）、合并信任库（JDK cacerts + test CA）与执行器，镜像不变。验收接收端 `webhook-receiver` 在 profile `acceptance`。
- **验收：** `client_api_flow.py` 81/81：自助凭证经 Gateway 调 Client API（401/403/Query 白名单），`order.cancelled`、`order.paid`（PaymentSucceeded 夹具）、`refund.created` 签名送达并与查询一致；AC-05 暂停 Platform 后由 XXL-Job 扫描补交，内容与冻结一致。日志无凭证、`whsec_`、载荷。详见[发布记录](../requirements/20261017/commerce-ClientAPI测试环境联通/release/release-20261017.md)。
- **已知限制：** Commerce 建单仍用固定 CCT 商业事实、零税与假文档；test 无 Payment，`order.paid` 依赖夹具；Gateway 未实现 `Idempotency-Key`（只转发）；Customer 4 个操作无公开 Scope 与路由；GATEWAY-CLIENT-API-01 的生产门禁未满足，本次启用仅限 test。

<a id="commerce-webhook-events-20261017"></a>

## Commerce Client Webhook 业务事件（2026-10-17，已提交并推送 `c8ba84d`，未部署）

- **Implemented / 本机验证：** 按[需求](../requirements/20261016/commerce-Webhook业务事件/requirement.md)与[技术方案](../requirements/20261016/commerce-Webhook业务事件/technique.md)，Commerce 在首次支付成功、订单取消、退款创建的同一本地事务里登记 `CLIENT_WEBHOOK_REQUESTED` 补偿任务，冻结 RFC 8785 Envelope 与两个 Hash；`ClientWebhookSubmissionHandler` 事务外核对 Hash 后调用 Platform 内部受理接口（暂不可用含 404 重试；400、409、422 与载荷篡改记为明确失败并告警）。事件 ID `evt_<资源编号>_<短名>`；字段与 GET 订单、GET 退款一致。不改表。
- **验证：** Commerce 新增 20 项测试，全量 `test` **810/810**；`verify` 被既有 Hosted Checkout 覆盖率门禁拦截（行 0.85 / 0.90）。本机 Commerce → Platform → 接收端 `commerce_webhook_flow.py` **66/66**（已支付订单的 Intent 投影为手工夹具）；Platform 不可用时业务照常、任务可重试（恢复后补交依赖 XXL-Job 扫描，本机未验证）。
- **未完成：** `refund.succeeded/failed/cancelled` 随 Commerce 接入 Payment 退款接口另立需求；`order.expired`、`order.funds_available` 待对应业务能力；Commerce 未部署 mor-test；未推送。

<a id="client-console-developer-20261016"></a>

## Client Console 接入自助：API 凭证与 Webhook（2026-10-16，已提交并推送 `9ae668a`；2026-10-16 已部署 mor-test）

- **mor-test 部署（2026-10-16）：** 与租户与工作区合并发布，CCT / Platform / Client BFF / Client Web 镜像 `client-console-20261016`；`mor_platform` 执行 `credential_name` DDL 与 OWNER 权限补录（4 权限、8 授权）；启用 D01—D15、W01、W02，更新 C02、A05；test `developer_flow.py` 78/78（不向外网推送，Webhook 送达、投递、重发未在 test 验证）。见[发布记录](../requirements/20261016/client-console-接入自助/release/release-20261016.md)。夜间决定待用户复核。
- **Implemented / 本机验证（用户夜间授权推进）：** 按[需求](../requirements/20261016/client-console-接入自助/requirement.md)与[技术方案](../requirements/20261016/client-console-接入自助/technique.md)：Platform 新增 CLIENT 权限判断 `POST /authorization/v1/client/authorize`、客户侧 API 凭证 `/client-api-credentials/v1`（创建 / 轮换 / 撤销 / 列表 / 详情 / Scope 目录；密钥 `ck-` 前缀 + 256 位 secret，Argon2id，只返回一次；有效凭证上限 10）、客户侧 Webhook `/client-webhooks/v1`（9 个操作，按 ID 校验租户归属，客户视图脱敏）；首次 ACTIVE 投影授予 OWNER 4 个开发者权限；`t_api_credential.credential_name`。Client BFF `/client-developer/v1` 15 个转发、租户上下文与权限拦截、no-store、W02 `capabilities`。Client Web 开发者分组、API 凭证页、Webhook 页、一次性密钥弹窗。登记计划新增 D01—D15。
- **数据库：** DDL 增量 [`db/ddl.sql`](../requirements/20261016/client-console-接入自助/db/ddl.sql) 已在本机 `mor_platform`、`mor_platform_e2e` 执行；补录 DML [`db/dml.sql`](../requirements/20261016/client-console-接入自助/db/dml.sql) 仅在 `mor_platform_e2e` 执行；本机 Vault 登记 `WEBHOOK_SIGNING_SECRET_V1` 与 Platform PROTECT/REVEAL 规则；本机 `mor_platform_e2e` 执行 Webhook 建表 SQL。mor-test 均未执行。
- **验证：** Platform 全量测试 662/662（verify 被全库覆盖率门禁拦截，指令约 62%、分支约 43%，既有状况）；修复后相关类 14/14；Client BFF 57/57；Web 49/49 与构建；本机全链路 `developer_flow.py` 83/83、`developer_browser.mjs` 12/12。凭证可用性经 PLT-I03 私网校验接口验证（本机未登记 Client API 业务路由）；浏览器无法覆盖“重发已结束投递”（本机无可信 HTTPS 接收端、无 XXL-Job），由接口验收覆盖。
- **已知限制：** 无 step-up 二次认证与邮件通知；并发冲到凭证上限未单测；Commerce 尚未产生业务事件；上游业务错误原因未经公共 HTTP 客户端透传到 BFF。

<a id="client-console-wrapup-20261017"></a>

## Client Console 已交付功能收尾（2026-10-17，已本地提交 `3209429`，未推送；2026-10-17 已部署 mor-test CCT；F-02、F-03 交付结束）

- **Implemented：** 按[需求](../requirements/20261017/client-console-已交付功能收尾/requirement.md)与[技术方案](../requirements/20261017/client-console-已交付功能收尾/technique.md)：CCT 批准入驻申请时在同一事务写入 Client 主体资料 `t_party_profile.approved_time`（只填空值），工作区摘要 `approvedAt` 改取该字段（空值退回最早 APPROVED 申请时间）；存量由需求目录 `db/dml.sql` 回填（近似值）。`developer_browser.mjs` 支持 `E2E_TARGET=test`。
- **验证：** CCT 全量 53/53；本机 `workspace_flow.py` 53/53、`team_flow.py` 123/123、`developer_browser.mjs` 12/12；test `developer_browser.mjs` 10/10（3 项按设计 SKIP）、`workspace_flow.py` 53/53、`team_flow.py` 123/123。见[发布记录](../requirements/20261017/client-console-已交付功能收尾/release/release.md)。
- **数据：** 本机 `mor_cct` 回填 28 个、test `mor_cct` 回填 21 个已批准 Client。
- **待办：** F-01 真实邀请邮件补测（待用户提供 SES 已验证的收件地址）；推送需用户授权。

<a id="client-console-team-20261017"></a>

## Client Console 团队成员与邀请（2026-10-17，已提交并推送 `f7b5500`；2026-10-17 已部署 mor-test；2026-10-17 交付结束）

- **Implemented：** 按[需求](../requirements/20261017/client-console-团队成员与邀请/requirement.md)与[技术方案](../requirements/20261017/client-console-团队成员与邀请/technique.md)：Platform 每个 CLIENT 工作区 4 个系统角色（所有者 / 管理员 / 开发者 / 只读成员，首次 ACTIVE 投影创建，存量由 DML 回填）与团队权限 `CLIENT_TEAM_READ/MANAGE`；`ClientTeamApi`（成员、邀请、重发、撤销、调整角色、移除）与 `ClientInvitationApi`（预览、详情、接受）；`t_tenant_invitation`、挑战用途 `TENANT_INVITATION_ACCEPT`、邀请邮件模板 `CLIENT_TENANT_INVITATION`；`GET /workspaces` 排除 REVOKED。CCT 工作区摘要不再限定申请人。Client BFF 团队 7、邀请 3 个端点与 `TEAM_READ/MANAGE` 能力。Client Web `/workspace/team`、`/invitations/accept`、登录后回到接受页、工作区单项降级。路由 T01—T07、I01—I03。
- **验证：** Platform 全量 759/759（新增团队与邀请 23 项）；CCT 52/52；Client BFF 76/76；Web 61/61 与构建；本机全链路 `team_flow.py` 123/123、`team_browser.mjs` 9/9；Platform 投影 / 权限矩阵经本机与 test DML 核对。
- **mor-test 部署（2026-10-17）：** `mor_platform` 执行本需求 DDL / DML；Platform、CCT、Client BFF、Client Web 替换为 `client-team-20261017` 镜像；10 条路由启用（总数 277）；test `team_flow.py` 123/123、浏览器 9/9，回归 `workspace_flow.py` 53/53、`developer_flow.py` 78/78。见[发布记录](../requirements/20261017/client-console-团队成员与邀请/release/release.md)。
- **本机：** `mor_platform_e2e` 执行本需求 DDL / DML，并经用户授权执行 Gateway 幂等的 `2026-10-17-gateway-route-idempotency-mode.sql`（新 Platform 依赖该列）。
- **已知限制：** test SES 仍在沙箱，邀请邮件对 `@example.test` 返回 FAILED，仅 SES 模拟地址验证了 SENT，真实收件箱送达未验证；工作区暂停时发起邀请返回 401（会话重验），接受时为 403；20 条待接受上限不加锁；DML 必须先于新 Platform 执行；AC-15 并发接受仅 Platform auto 覆盖。
- **交付结束（2026-10-17 用户确认）：** `f7b5500`、`1c04aba` 已推送；遗留见上方已知限制。

<a id="client-console-workspace-20261016"></a>

## Client Console 租户与工作区（2026-10-16，已提交并推送 `9ae668a`；2026-10-16 已部署 mor-test；2026-10-17 交付结束）

- **mor-test 部署（2026-10-16）：** 与接入自助合并发布（见其[发布记录](../requirements/20261016/client-console-接入自助/release/release-20261016.md)）；test `workspace_flow.py` 53/53。
- **Implemented / 本机验证：** 按[需求](../requirements/20261016/client-console-租户与工作区/requirement.md)与[技术方案](../requirements/20261016/client-console-租户与工作区/technique.md)：Platform `ClientAuthenticationApi` 新增 `GET /workspaces`、`POST /workspace-context`、`POST /account-context`，会话支持 CLIENT 租户上下文，租户会话在查询与刷新时重验成员与租户，失效且账号有效时刷新降级为账号上下文并返回 `fallbackReason`；CCT `GET /client-workspace/v1/client-summaries`；Client BFF C02 / W01 / W02 与 C01 `workspaces` 聚合；Client Web 开始页“我的工作区”、`/workspace` 侧边栏外壳与首页。路由登记计划：C02 下游改为 `/client-auth/v1/workspace-context`、A05 响应 Cookie `ISSUE_OR_CLEAR`（修复刷新失败清 Cookie 被 Gateway 判非法）、新增 W01 / W02；本机已登记。
- **验证：** Platform 全量测试 651/651（verify 被全库覆盖率门禁拦截：指令约 60%、分支约 42%，为既有状况，未与改动前对照）；CCT 51/51；Client BFF 47/47；Web 43/43 与构建；本机全链路 `workspace_flow.py` 53/53、无头浏览器 `workspace_browser.mjs` 10/10。暂停 / 恢复租户直接改本机 `mor_platform_e2e`（本机运营租户路由未启用）。未覆盖：审核通过但投影未完成（AC-03）、并发切换 409。
- **2026-10-17 test 浏览器验收：** `E2E_TARGET=test workspace_browser.mjs` **10/10**（`https://localhost:28443`，HTTP≥400 与控制台错误 0）。
- **交付结束（2026-10-17 用户确认）：** 归档提交 `6a55fc2` 起为本地提交，推送未授权。遗留：AC-03 与并发切换 409 未覆盖。

<a id="client-console-onboarding-20260914"></a>

## Client Console 注册与客户入驻（2026-09-14）

- **2026-10-16 Deployed（mor-test）：** 从干净提交 `21a1a51` 构建并按 CCT → Platform → Client BFF 替换（镜像 `client-onboarding-20261016`，回退镜像见[发布记录](../requirements/20260914/client-console-注册与客户入驻/release/release-20261016.md)）；18 条入驻路由 `idempotency-key` 改为只 CORS 允许，Gateway Snapshot `routeCount=231`。test 验收 `acceptance_flow.py --approve` **39/39**、`scenario_flow.py` **85/85**（退回改证件、被拒重新申请、重复请求业务防重）；验证邮件因 test 通知 SES 未配置发件身份而经验收脚本兜底重算挑战；日志无验收邮箱/电话原值。未做：浏览器验收、换类型重新申请。无数据库变更。
- **2026-10-15 入驻写接口请求幂等迁移（本机 auto 已验证；已本地提交 `21a1a51`，未推送；2026-10-16 已部署 mor-test（[发布记录](../requirements/20260914/client-console-注册与客户入驻/release/release-20261016.md)））：** 按[迁移方案](../requirements/20260914/client-console-注册与客户入驻/idempotency-migration-proposal.md)（用户“同意”）：CCT 9 个、Platform F01/F02、Client BFF 10 个入驻写接口不再接收、校验或转发 `Idempotency-Key`，删除 CCT 本地回执重放（`replay/receipt/insertReceipt/command`、`ClientDraftCommandRecord`、`IDEMPOTENCY_CONFLICT`）与 Platform `ClientFileCommandService`、`FileUploadService.resume`；`t_command_idempotency` 与 Platform 命令表及历史数据不动。业务防重：C09 同申请同关系同内容返回原关系、不同内容 409；C11/C13 已删除返回 204；C12 相同组合返回原引用；F02 同一 Upload 已完成返回原文件；F01 每次新建上传（锁改为 `账号:申请:主体`）；C07 重复新增人员为已接受风险，待 Gateway 请求幂等实现。CCT 联系方式 Vault 保护键改为每次随机 UUID（STABLE 同值同 Token，不再由请求 Key 或原值派生）。路由生成脚本 18 条路由 `idempotency-key` 只保留 CORS 允许、不再转发，计划与命令模板已重新生成；本机/mor-test 实际登记未改。验证：CCT 全量 `verify` **46/46**（新增 `OnboardingDuplicateWriteTest` 4 项）；Client BFF **35/35**（BFF 不转发 Key 的断言）；Platform 文件相关 4 类（MinIO/S3 适配器、文件扫描、审核下载）**44/44**，未跑 Platform 全量（另一会话正在并发使用同一 `mor_platform`，局部运行的覆盖率门槛失败属预期）；F02 重复完成无 Platform HTTP 测试；联系方式 Vault 路径无 CCT 测试覆盖。发布顺序须先 CCT/Platform 后 BFF。Platform API 本机 install 含另一会话未提交的通知/Webhook API 改动。
- **2026-10-15 被拒后重新申请（本机 auto 已验证；已本地提交 `21a1a51`，未推送；2026-10-16 已部署 mor-test（[发布记录](../requirements/20260914/client-console-注册与客户入驻/release/release-20261016.md)））：** 按[设计稿](../requirements/20260914/client-console-注册与客户入驻/rejection-termination-design.md)（用户“按照建议”）：C01 在账号没有进行中申请时，若存在同类型、REJECTED 且 Client 仍为 PENDING 的最近一份申请，则在原 Client 下新建 DRAFT 申请（`ClientDraftRepository.reapply`：`insertReapplication` 沿用资格类型，`moveDraftProfiles` 把主体与关联人员资料转给新申请，`moveDraftRelations` 转移草稿关系），不新建 Party/Client、不再投影 Platform；材料不带入，客户重新上传（Platform 文件按申请归属）。重提时同一 Party 复用已有身份键，解决被拒后同证件再申请必然 `IDENTITY_CONFLICT` 的问题。Client 被拒后保持 PENDING 为确认语义；换类型重新申请仍新建 Client，旧 PENDING Client 与工作区留到做工作区列表时清理。无 DDL，Web/BFF/Platform/Risk 不改。新增 `ReapplyAfterRejectionTest`（组织 + 关联人员：提交 → 真实 MQ 拒绝 → C01 复用原 Client、资料/人员/关系转入、材料为 0 → 重传 → 重提，身份键各仍 1 个、无新的工作区投影），测试清理补 C01 命令记录。CCT 全量 `verify` **42/42**。未覆盖：换类型重新申请、Web 端到端。
- **2026-10-15 退回后改证件（简化版，本机 auto 已验证；已本地提交 `21a1a51`，未推送；2026-10-16 已部署 mor-test（[发布记录](../requirements/20260914/client-console-注册与客户入驻/release/release-20261016.md)））：** 按[方案稿](../requirements/20260914/client-console-注册与客户入驻/returned-identifier-rebinding-proposal.md)及用户“先简单的改，让流程能跑通”：C04/C08 保存证件字段时先用 `ClientDraftMapper.unbindIdentifier` 解除永久身份键绑定再保存（可编辑判断不变，DRAFT 无绑定、SUBMITTED 以后仍不可改），删除已无调用的 `boundIdentifiers`；C14 重提沿用既有绑定（同号码复用原键、新号码新建、他人占用返回 `IDENTITY_CONFLICT`），旧键保留原 Party 归属。新增 `ResubmitAfterReturnTest`（提交 → 真实 MQ 退回 → C04 改号 → 重提绑定新键、旧键仍在）通过。CCT 全量 `verify` 41 项中 40 通过，`ClientReviewResultListenerTest.test_normalFlow` 等待超时，单独重跑两次均通过；本机 e2e CCT 进程同时消费 `/mor-local` 的 `cg_cct_client_review` 队列（1 个常驻消费者），会抢走测试消息，判断为环境干扰，未停止该进程。未补：关联人员、改回原号、撞他人号码、共享人员等方案第 4 节其余场景；误填他人号码的旧键清退仍属人工身份治理。
- **2026-10-14 Deployed（mor-test）：** 按用户批准的[发布清单](../requirements/20260914/client-console-注册与客户入驻/release/release.md)发布到 mor-test，记录见[执行记录](../requirements/20260914/client-console-注册与客户入驻/release/test-acceptance.md)。Platform/CCT/Risk/客户端 BFF/运营 Web/客户端 Web 来自提交 `7e9eddc`/`06f46b9`；`mor_cct` 建 38 表、`mor_risk` 入驻改表与争议 4 表 Provider→Channel 改名后与正式 Schema 0 差异；新增 MinIO、Mailpit、Client HTTPS 入口 `https://localhost:28443`；39 条入驻路由启用（ACTIVE 188）。test 上 API 级验收 39 步全部通过；浏览器人工抽查待用户执行。
- **2026-10-13 C14 正式提交（阶段 4 增量 4a）：** 按已确认的 [C14 修订稿](../requirements/20260914/client-console-注册与客户入驻/submission-contract-amendment.md) 实现：CCT `SubmitClientApplicationProcessor`（`AbstractLockProcessor`，与既有注解锁同一 `client-onboarding:application:{id}` 互斥域）按申请状态防重、服务端生成 submissionId，不读取 Idempotency-Key；`ClientSubmissionPreparer` 在事务外读 Risk 通用材料要求与 Platform 文件核验，`ClientSubmissionValidator` 做必填/目录/证件/基础必需材料/范围外材料/文件可用检查，组织至少一名关联人员；`ClientSubmissionService` 一个本地事务完成 DRAFT/RETURNED→SUBMITTED、通用规范化后的永久身份键绑定和 `CLIENT_REVIEW_REGISTRATION` 补偿登记；`ClientReviewRegistrationHandler` 提交后调用 Risk I01（新增 `risk-api` 合同 `ClientReviewRegistrationApi`，Risk 服务端属 4b、未实现），成功后 SUBMITTED→UNDER_REVIEW。研发中把不完整从 422 `VALIDATION_ERROR` 改为 `SUBMISSION_INCOMPLETE` + 只读 C16 提交核对（公共 Client 不传递空 Body 请求的字段错误）。BFF 转发 C14/C16，C14 不要求 Idempotency-Key 且为空 Body；Web 增加“提交前核对 / 提交审核”与审核中提示；注册计划 C14 去掉 Idempotency-Key 转发并新增禁用 C16 路由（38 路由）。无 DDL、无 SQL 执行。
- **4a 验证：** CCT 新建入驻测试基础设施（`application-mock.yml`、MockHttpServer 承载 Platform 会话/文件、Risk 材料/I01、Vault 发现地址；本地 Mocker 插入完整草稿夹具并按标识清理），JDK 25 `clean verify` **27/27**（新增 C14/C16 HTTP 8 项、审核登记 Handler 3 项，原 Checkout 16 项），真实本机 MySQL/Redis 与 Foundation 补偿调度，测试后 `mor_cct` 相关表计数为 0；BFF `clean verify` **31/31**；Web **28/28** 与构建通过。未覆盖：Risk I01 真实服务端（4b）、RETURNED 后重提（已绑定证件不可原位修改的既有限制未改）、并发双提交的 Redis 竞争场景、测试 `init/ddl.sql`、浏览器端到端与 Pencil 设计稿同步。
- **2026-10-13 C14 增量 4b（Risk I01 服务端）：** 用户授权后在本机 `mor_risk` 执行 [3 ALTER + 1 CREATE](../requirements/20260914/client-console-注册与客户入驻/db/implementation/risk-onboarding-local-execution.md)，执行后 11 张相关表与正式 Schema 逐列逐索引一致（81 表）。risk-compliance 新增 `RegisterClientReviewProcessor`（AbstractProcessor）、`ClientReviewService`、6 张 Case 表的 DataObject/Mapper/Repository 与 `ComplianceIdentifierGenerator`（复用决策模块同一 Snowflake）；risk-web 新增 `ClientReviewRegistrationController`（`onboarding.review.enabled` 默认关闭）并把 `/client-onboarding-review` 设为 metadata-only 日志。建模、关系映射与已知限制见 [C14 修订稿第 7.1 节](../requirements/20260914/client-console-注册与客户入驻/submission-contract-amendment.md#71-4b-实施记录2026-10-13)。资料快照按用户决定本期不做。`RiskDatabasePrecondition` 期望表数同步为 81。
- **4b 验证：** `ClientReviewRegistrationHttpAutoTest` **5/5**（组织 + 两名关联人员全量落库、同内容重放、改内容 409、非法输入 400、程序不可用 503、关联人员已有进行中任务 409）与 `MaterialRequirementsHttpAutoTest` **33/33**，真实本机 MySQL；risk-test 模块因其他工作流的决策测试仍不能整体编译，两类测试为单独编译后用 JUnit Launcher 运行，测试后 Case 与合成程序表计数为 0。CCT→Risk 端到端仍为两侧分别验证（CCT 侧 I01 使用 DTO Mock），未做真实进程联调。
- **4b 前置（2026-10-13 与 Risk 重构会话核对）：** I01 按已批准设计以内部同步合同传递 CCT 主体结构化原值（姓名、出生日期、证件号、地址；这些字段不在 Vault），Risk 收到后用 `RISK_DD_PARTY_SNAPSHOT_V1` Protect 快照；CCT Email/Phone Vault Token 不跨系统传递。Risk 实现时必须把 `/client-onboarding-review` 加入 `web.logging.metadata-only-path-prefixes`，校验错误不得回显原值；CCT 出站客户端不记录 Body，入站入驻路径已为 metadata-only。
- **2026-10-13 Operations 审核增量 5a（Risk）：** 按已批准的 [Operations 审核方案](../requirements/20260914/client-console-注册与客户入驻/operations-review-plan.md)（结果回传 RabbitMQ、通用行业 + 四位 MCC、O06/C15 不做、通过时主/子任务一起结束）实现 Risk 侧。`risk-api` 新增 `ClientReviewAdminApi`（O01 列表、O02 Risk 详情含通过门禁、O05 核验、O07 退回、O08 通过、O09 拒绝，使用 Gateway 可信操作者头），`ClientReviewRegistrationApi` 增加 I02 分类接收，新增 `ClientReviewErrorCode`。risk-compliance 新增 `ReviewClientEvidenceProcessor`、`ReceiveClientReviewClassificationsProcessor`、`DecideClientReviewProcessor`、`ClientReviewApprovalPolicy` 门禁和 `ClientReviewResultHandler`（补偿任务 `CLIENT_REVIEW_RESULT`，eventKey 为主任务 ID）。同一任务的审核命令都先对主任务做版本 CAS 来串行，不引入 Redis 锁依赖。退回：主/子任务 RETURNED，不写决定；通过：全部 APPROVED，每个任务一条 REVIEWER 决定；拒绝：主任务 REJECTED 并写决定，子任务 WITHDRAWN。risk-web 新增 `ClientReviewAdminController`（同样受 `onboarding.review.enabled` 控制，`/client-onboarding-admin` 只记元数据日志），并实现项目第一条 MQ 发布：Durable Topic Exchange `topic_risk_client_review`，路由键 `client_review.decided.v1`，统一 JSON 信封（eventType `ClientReviewDecided`，ID 为字符串，payloadHash 为 payload 按固定字段顺序序列化后 UTF-8 字节的 SHA-256），Persistent + mandatory，收到 Publisher Confirm（10 秒）才算成功。用户批准放开 Risk enforcer 对补偿事件/AMQP 的禁令，根 pom 排除项、risk-compliance/risk-web 依赖及 `RiskModuleBoundaryAutoTest` 白名单已同步。`RiskRuntimeConfiguration` 改为一个共享 Snowflake `IdGenerator`，决策/合规端口和补偿登记共用。无 DDL，未执行 SQL。
- **5a 验证：** Risk 全量 `./mvnw -o verify` **251/251**，BUILD SUCCESS（真实本机 MySQL/Redis/RabbitMQ，包含并行会话的测试迁移）。其中 `ClientReviewDecisionHttpAutoTest` **4/4**：未满足门禁返回 422 → 核验材料与接收分类 → 通过，从 Broker 真实收到消息并校验信封/Hash，补偿记录已删除，重放返回原结果、改判返回 409；另覆盖退回、拒绝、参数与归属。重构后的 `ClientReviewRegistrationHttpAutoTest` **5/5**。限制：reasonCode 只校验格式，未接 Risk 原因字典；核验时不再实时查 Platform 文件状态（依赖提交时的核验）；并发冲突只靠 CAS 保证，没有专门的并发测试；CCT 消费端（队列 `cg_cct_client_review` + DLQ）属于 5b，实现前消息因 mandatory 不可路由，由补偿任务重试。未部署、未启用入口、未提交。
- **5a 测试迁移与修正（2026-10-13，已随 Risk 本地提交 `9b0ef16`，未推送）：** Risk 测试迁移会话按新单元测试规范把审核测试改写为 `auto/ext/http/compliance` 下 6 个类，共 280 项（Submission 107、Classification 43、CaseList 21、CaseDetail 16、EvidenceReview 29、Decision 64），覆盖原 9 项场景；删除了旧的 `ClientReview*HttpAutoTest`、`ClientReviewHttpAutoTestSupport`、`RiskServiceAutoTestBase` 和 `application-auto.yml`，测试统一使用 mock profile（`onboarding.review.enabled=true`）。该会话报告 risk-test 全量 1878/1878 通过，本会话没有复跑全量。迁移中发现的生产问题已由本会话修正：I01/I02 请求体里超出 long 范围的 ID 原来返回 500，现在返回 400；I02 同一行业 + MCC 组合同时作为主要和次要分类时，原来要等插入撞唯一键才返回 409，现在在校验阶段返回 400；审核列表/详情的时间统一为 UTC 六位微秒；新增 `ClientReviewRequestConfiguration`，拒绝未知、重复或空 Query 参数，以及 GET 请求携带 Body。修正后审核 6 个类、`RiskModuleBoundaryAutoTest`、`WebLoggingBoundaryAutoTest` 共 301/301 通过，测试迁移会话随后为上述新行为补了回归用例（审核 6 个类共 292 项：Submission 109、Classification 46、CaseList 26、CaseDetail 18、EvidenceReview 29、Decision 64），并报告 risk-test 全量 1890/1890 通过（不加载 .env.local），本会话没有复跑。用户授权该会话合并提交 `9b0ef16`（130 个文件，限 risk-compliance-service，未推送；`DictionaryText.java` 不在提交内）。本条台账、需求文档以及 CCT、BFF 改动都不在这次提交里，仍未提交。另外，Risk 测试的审核结果消息也会路由到 CCT 本机队列 `cg_cct_client_review`。该队列没有常驻消费者。测试迁移会话按用户要求清空了队列：清空前有 203 条测试消息，清空后该队列和死信队列都是 0 条。但每次跑 Risk 全量测试仍会往这里送消息；CCT 测试在每项开始前会清空该队列。是否给 Risk 测试单独配一个 vhost 等长期隔离，需要用户另外决定。
- **2026-10-13 Operations 审核增量 5b（CCT）：** 按已批准方案实现 CCT 侧，合同细化见[方案第 9 节](../requirements/20260914/client-console-注册与客户入驻/operations-review-plan.md#9-实施中细化的合同2026-10-135a5b)。`client-contract-api` 新增 `ClientClassificationAdminApi`（O03 PUT、O04 POST、分类读取、行业目录）与 3 个错误码；配置新增 10 个通用行业（MCC 只校验四位数字）。`ReplaceClientClassificationProcessor`/`ConfirmClientClassificationProcessor` 共用 `client-onboarding:review-case:{caseId}` 锁；O03 在一个事务里条件绑定任务（本任务已确认则拒绝）并整体替换分类行；O04 条件写入确认人/时间，并在同一事务登记 `CLIENT_REVIEW_CLASSIFICATION` 补偿任务，提交后由 `ClientClassificationHandler` 调 Risk I02（Risk 返回任务已结束视为完成，内容冲突为终态失败）。审核结果由 `ext/mq/ClientReviewResultListener` 消费：手动确认，Quorum 队列 + DLX/DLQ + 投递上限 5；`ApplyClientReviewResultProcessor` 核对提交编号与任务，`ClientReviewOutcomeService` 在一个事务里条件结束申请（RETURNED 保留名额，APPROVED/REJECTED 释放名额并写决定 ID）。通过时同一事务把 Client 改为 ACTIVE，追加第二版生命周期历史，登记既有 `CLIENT_TENANT_PROJECTION` 任务，由现有 Handler 调 Platform PLT-I17 投影 ACTIVE。为避免 Bean 循环，交付读取拆为 `ClientClassificationSourceService`。Risk I01/I02 客户端注册了 Risk `ClientReviewErrorCode`，以区分任务已结束和内容冲突。CCT 依赖门禁只新增 AMQP。Risk 结果消息补充 decisionId/decidedBy。无 DDL，未执行 SQL（所需列已在 9 月 CCT 迁移中存在）。
- **5b 验证：** CCT `clean verify` **38/38**，BUILD SUCCESS（真实本机 MySQL/Redis/RabbitMQ；Checkout 测试需要设置 `CCT_TEST_DB_PASSWORD`，配置里没有 MOR_ 回退）。新增 `ClientClassificationAdminTest` 3 项、`ClientClassificationHandlerTest` 4 项、`ClientReviewResultListenerTest` 4 项：在真实 Broker 上发布 → 通过、激活与 PLT-I17 投影、重复投递；退回后不可再改分类；拒绝；旧任务结果忽略与篡改摘要进入 DLQ。Risk 全量 `verify` **314/314**。未覆盖：CCT→Risk、Risk→CCT 的真实进程联调（两侧各自对测试接口/真实 Broker 验证）；O02 所需的 CCT 申请资料只读查询留在 5c；拒绝后 Client 仍为 PENDING（Platform 工作区不关闭），终止语义未设计；经营资料 `content_hash` 在批准时未计算（当前无消费者）。未部署、未启用入口、未提交。2026-10-13 Risk 测试迁移会话按新规范删除了旧的 `MaterialRequirementsHttpAutoTest`，并重写为 `auto/ext/http/compliance/MaterialRequirementsApiAutoTest`（53 项，尚未提交）。该会话报告完整 risk-test 157/157 通过（包括 ClientReview 测试）；本会话没有独立复跑。上文 4b 和通用化轮次记录的 33/33 只是当时的证据。
- **2026-10-13 Operations 审核增量 5c（Operations BFF + Web，未提交）：** 合同细化见[方案第 10 节](../requirements/20260914/client-console-注册与客户入驻/operations-review-plan.md#10-实施中细化的合同2026-10-135c5d)。CCT 新增只读 `ClientApplicationAdminApi`（`GET /client-onboarding-admin/v1/cases/{caseId}/application`，不 Reveal 联系方式），`/client-onboarding-admin` 改为只记录元数据。Operations BFF 新增 `ClientOnboardingReviewController`（O01—O05、O07—O09）、`ClientReviewDetailsProcessor`（O02 聚合，CCT 部分失败标 UNAVAILABLE）、`ClientReviewClientConfiguration`（Risk/CCT 独立工厂、发现、错误码登记）、`ClientReviewRequestConfiguration`（严格 Query/Body），并新增错误状态映射和只记元数据的日志前缀；pom 增加 `client-contract-api`。Operations Web 新增 `pages/ClientOnboardingReview.tsx` 与 `clientReview/*`（列表、详情、材料核验、分类编辑与确认、退回/通过/拒绝弹窗；通过按钮只看 Risk 门禁），并注册页面能力和路由。注册计划 O01 的 Query 参数改为已实现的 page/size/status/qualificationType/applicationId。
- **5c 验证：** CCT `verify` 40/40（新增 `ClientApplicationAdminTest` 2 项）。Operations BFF 新增 `ClientOnboardingReviewBrowserAutoTest` 13 项，覆盖聚合、部分不可用、404、Query 透传和拒绝、6 个命令的合同转发、Owner 错误码、权限拒绝、非法 Body、跨任务响应 502，与更新后的 `OperationsConsoleBffApplicationAutoTest`、`RiskClientConfigurationAutoTest` 共 77/77 通过。BFF 全量为 1256 项、58 项失败，大多是既有测试仍断言转发 Idempotency-Key 或拒绝缺少的键，而主代码在仓库合并（`fff35d2`）时已不再转发（与 `927e248` 无关）；这些测试此前因 risk-api 重构无法编译，失败一直没有暴露。另有 OperationsBrowserBoundary 中 2 项 422 原因未诊断（RiskDecisionConfigurationBrowserBoundary、RiskFlowBrowserBoundary、ConfigurationManagementBrowser、TenantAccessBrowser、OperationsBrowserBoundary），与本增量无关。**后续（经用户同意）：**这 58 项已按现行规则迁移：上游断言改为不带 `Idempotency-Key`，删除 9 个仅测 BFF 键校验的用例（含 Risk validate 确定性键），3 个非键失败经诊断均为过时测试（创建人员/角色的空 Body 现进入字段校验返回 422；人员删除、启用的缺键用例已删）。BFF 全量 **1247/1247**。随后经用户决定，Gateway 配置 6 个写命令（Target/Route 创建、更新、状态变更）也迁移到新规则：Platform `GatewayConfigurationManagementApi`、Controller、Processor、Service 去掉 `Idempotency-Key` 与本地命令重放，重复创建由唯一编码拒绝、更新与状态变更由 `expectedVersion` CAS 拒绝；审计 `GatewayChange` 去掉幂等引用；BFF Controller 与 Client 不再接收或转发该 Header。原 `OperationsCommandIdempotencyService`/`CommandIdempotencyContext`/`CommandIdempotencyMapper` 已无调用方，暂未删除，表结构未动。验证：Platform 348/348，BFF 1247/1247。经用户授权已提交（未推送）：CCT `95d4150`、Risk `c502ada`、Platform `f44adb2`、Client `e0f0b66`、Operations `96b2767` 及本文档提交。路由、权限、菜单登记未执行：注册计划按设计走 Platform 管理接口（不生成 DML），而本机 `mor_platform` 的 Gateway Target/Route、Permission、Console Resource、Role 均为 0 行，缺少运营 BFF Target 等基础登记，单独写入本需求的行不能生效。另有 5 个 Risk 决策测试文件因 risk-api 重构不能编译，本会话已做机械适配，不改断言意图。Operations Web 981/981、`npm run build` 通过。经用户同意，Platform `console-resource-capabilities.json` 已按 Web `pageCapabilities.ts` 逐字节生成并新增 `OPERATIONS_CLIENT_ONBOARDING_REVIEW`（共 17 项），Platform `OperationsAuthorizationTest` 19/19。**未完成：**运营侧文件内容查看（Platform 尚无运营文件读取接口），以及路由、权限、菜单 DML（未执行）。
- **2026-10-13 增量 5d（Client 结果页，未提交）：** Risk 新增私网只读 `ClientReviewResultApi`（`GET /client-onboarding-review/v1/applications/{applicationId}/result`），只返回最近一次主任务的状态、结果、退回分区、公开说明和结束时间。Client BFF C05 在 RETURNED、APPROVED、REJECTED 时读取它，增加 `reviewStatus`（NONE/AVAILABLE/UNAVAILABLE）和 `review`；结果与 CCT 状态不一致或 Risk 不可用时标为 UNAVAILABLE。Client Web 新增 `resultBanner` 与结果提示：退回时显示分区跳转、说明和重提提示；通过时只显示“工作区正在开通”；拒绝时显示公开说明或通用文案。CCT 没有新增列，退回说明仍由 Risk 保存（偏离方案第 4 节，已在第 10 节记录）。
- **5d 验证：** Risk `ClientReviewResultApiAutoTest` 11 项，加 `RiskModuleBoundaryAutoTest` 和 `WebLoggingBoundaryAutoTest` 共 32/32 通过；本会话没有复跑 risk-test 全量。Client BFF 35/35（新增 4 项：退回结果、通过结果、不一致或不可用、草稿不读取）。Client Web 33/33（新增 `review-result.test.ts` 5 项），`npm run build` 通过。**未完成：**客户侧“进入工作区”需要 Platform 的工作区列表和 C02 切换；拒绝后 Client 仍为 PENDING；没有启动 Risk、CCT、BFF 进程做真实联调。
- **文件多存储并存第一段（2026-10-22，Implemented / auto 已验证，已提交未推送；已部署 mor-test）：** 按[需求](../requirements/20261022/platform-support-文件多存储并存/requirement.md)与[方案](../requirements/20261022/platform-support-文件多存储并存/technique.md)：存储实例、桶、写入路由改为 `mor_platform` 三张新表（`t_file_storage_instance` / `t_file_storage_bucket` / `t_file_storage_route`）由运营「存储管理」维护，STATIC 凭据存 Vault（`FILE_STORAGE_CREDENTIAL_V1`，每次操作前 Reveal、不缓存），多实例并存；读路径按记录原实例与桶，写入按来源系统路由（`*` 为默认）。配置文件中的 `files.onboarding.storage-provider`、`minio.*`、`s3.*` 等旧键已删除，残留时拒绝启动；新开关 `files.enabled`。`t_file_upload` / `t_file_object` 增加 `source_system`、`reason_code`，上传人、租户与 Owner 列改为可空；新增内部文件接口 `InternalFileApi`（`/internal/files/v1`，持文件 ID 即可读）与 Platform 内部 Service 入口；入驻链路行为不变（第二段改为 CCT 调用）。Operations BFF 12 条 `/storage-admin/v1` 路由与 Web「存储管理」页，Platform 注册块 130（停用状态）。本机 `mor_platform` 已经授权执行增量 DDL；Vault DML 未执行。验证：Platform auto 791/791（覆盖率门禁按既有记录未达标，本次改动行分支 85.4%），BFF 2057/2057，Web 1252/1252 与构建，全仓库 test-compile 通过；PDManer 与需求站失败项与 HEAD 基线一致。**Deployed（mor-test，2026-10-22 04:42—05:46Z，[发布清单与执行记录](../requirements/20261022/platform-support-文件多存储并存/release/release.md)）：**基线 main `281a217d` + 本需求；Platform `sha256:6dd8ffff…`、ops-bff `sha256:e81b8424…`、ops-web `sha256:d58172d9…`；已执行 Platform DDL、Vault DML（策略版本 19）、块 130 注册与激活（154 行，含桶检查路由补 `content-type` 两行），登记实例 `MINIO`（STATIC）、桶 `mor-platform-files`、默认路由 `*` → `CLIENT_ONBOARDING_FORMAT`；验收 33/33、浏览器冒烟 10/10。主 compose 已删旧存储键并加 `PLATFORM_SUPPORT_FILES_ENABLED`。**遗留：**本机 e2e 启动参数仍含旧存储键，换新 jar 前须删除并登记本机实例、桶与默认路由。详见[交付记录](../requirements/20261022/platform-support-文件多存储并存/delivery.md)。
- **文件多存储并存第二段（2026-10-22，Implemented / auto 已验证，已提交 `73a2460d` 未推送；已部署 mor-test；删除 DDL 已在 mor-test 与本机 `mor_platform` / `mor_client_master` 执行）：** 原则见[文件上传、引用与下载原则决定](decisions/2026-10-22-file-upload-address-principles.md)。 mor-test 15:22—15:24Z 换 platform `7cb30402…`、ops-bff `a5b9a17c…`、client-master `e3fded0b…`、client-console-bff `105b962e…`、client-console-web `25b30a78…`，验收与浏览器冒烟通过，见[第二段发布记录](../requirements/20261022/platform-support-文件多存储并存/release-s2/release.md)。 按[第二段方案](../requirements/20261022/platform-support-文件多存储并存/segment2.md)的原则 P1—P3：上传与表单提交分开，上传经 Gateway → BFF → Platform 内部文件接口取得文件地址；业务数据存文件地址 `/files/v1/{fileId}` 而非文件 ID；下载经 BFF → 文件服务下载接口，不经业务服务。原入驻文件 6 个入口（F01—F04 开始上传 / 完成上传 / 查文件 / 客户下载、CCT 关联与提交前核对、运营审核下载 O10）已迁到 client-console-bff、ops-bff 与 CCT 调用内部文件接口；Platform 删除 `OnboardingFileApi`、`OnboardingFileVerificationApi`、`ReviewFileApi` 链路、上传 Owner 字段的使用与 `t_file_owner_ref` 的读写，以及反调 CCT 的 `CctFileTargetClient`；CCT 删除 `ClientFileTargetApi`。platform-support-api 新增 `FileAddress`（地址格式化 / 解析）、`FileErrorCode`（取代 `OnboardingFileErrorCode`，404 / 409 / 422）与 `FileDisposition`（INLINE / ATTACHMENT，下载签入原文件名）；内部文件下载接受可选操作人并记审计。Platform 内部认证改为 DUAL：部署需 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL` 与 `PAYTECH_INSTANCE_ID`（另需 `PLATFORM_SUPPORT_FILES_ENABLED=true`），调用方带 `X-Caller-Service`。CCT `t_application_document` 改存 `file_address`（[增量 SQL](../paytech/client-master/docs/database/sql/changes/2026-10-22-application-document-file-address.sql)，本机 `mor_client_master` 已执行第 1 步，第 2 步未执行），审核登记合同仍发 `fileId`。Platform 删列 / 删表 DDL（[`2026-10-22-file-owner-removal.sql`](../paytech/platform-support/docs/database/sql/changes/2026-10-22-file-owner-removal.sql)：`t_file_upload.owner_*` 三列与 `t_file_owner_ref`）未执行。工作流附件已适配（`FileErrorCode`、下载按 ATTACHMENT、响应带 `fileAddress` 与 `disposition`）。验证（集成工作目录，基于 main `1d39cd94`）：全仓库 test-compile 通过；Platform auto 790/790、data-query 48/48（本机 `mor_platform` 独占窗口）、client-master 78/78（package 阶段）、ops-bff 2205/2205、client-console-bff 87/87、ops-web 1325/1325 与构建、client-console-web 63/63 与构建、arch-rules 68/68（reactor 4/4）；PDManer 591 项中 50 个失败、需求站 137 项中 10 个失败，均与 `1d39cd94` 基线一致。详见[交付记录](../requirements/20261022/platform-support-文件多存储并存/delivery.md)与 `reports/S2-*-report.md`。
- **数据查询服务（2026-10-22，Implemented / auto 已验证，已提交未推送；已部署 mor-test）：** 按[需求](../requirements/20261022/platform-support-数据查询服务/requirement.md)与[方案](../requirements/20261022/platform-support-数据查询服务/technique.md)，[决定](./decisions/2026-10-22-data-query-into-platform-support.md)修订 D25 / D35 查询部分并删除 `paytech/data-service` 骨架。Platform 新模块 `paytech-platform-support-data-query`：运营登记只读数据源（MySQL / Doris，可连整个实例，系统库不可查，密码存 Vault `DATA_SOURCE_PASSWORD_V1`），模板编码 + 参数查询（自研统一模板语法 `#{}` / `@{}` / if / each / join），保存即生效并保留版本与恢复，账号授权检查 + 只读会话 + JSqlParser 5.4 三层只读防护，Hikari 连接池按版本 5 秒刷新；硬上限 8 秒 / 10000 行 / 正文 48 KB。`mor_platform` 新增 `t_data_source`、`t_query_template`、`t_query_template_version`；18 个接口（管理 17、执行 1，执行入口只放行 `data-query.http.callers`）；Operations BFF 同路径转发，Web「数据查询 → 数据源 / 查询模板」（CodeMirror 6 懒加载）；注册块 131（权限 4、菜单 3、按钮 2、路由 18）。不做查询记录。**验证：** Platform HTTP 37、data-query 48、BFF 2080、Web 1276、arch-rules 63 通过。**mor-test（2026-10-22）：** 叠加文件多存储并存发布后替换 Platform `985d00ea…`、ops-bff `d221817d…`、ops-web `bcadc589…`（验收修正第二版），DDL、Vault DML（策略版本 20）、块 131 已执行并激活，`DATAQUERY_HTTP_CALLERS_0=operations-console-bff`；浏览器验收 15/15（[发布记录](../requirements/20261022/platform-support-数据查询服务/release/release.md)），验收数据 ACC_* 与被查库 `mor_data_query_fixture` 保留。**未完成：** Doris 方言只有 manual 环境；版本历史操作人显示账号 ID；工作流 `DATA_QUERY` 验证待工作流发布。
- **入驻文件存储与检查（2026-10-14，Implemented / auto 已验证，本地提交未推送）：** 按[确认稿](../requirements/20260914/client-console-注册与客户入驻/file-storage-adapter-proposal.md)在 Platform 文件模块新增 MinIO（MinIO SDK 8.5.17）与 S3（AWS SDK v2 2.31.50）两个存储适配器，按 `files.onboarding.storage-provider=MINIO|S3` 唯一装配；上传为 `If-None-Match` 一次写入，完成读取要求对象恰好一个版本并固定 versionId，服务端按文件头识别 PDF/JPEG/PNG。新增内置格式检查器 `PLATFORM_FORMAT`（复核摘要、大小、类型，不是病毒扫描，生产须另加 ANTI_MALWARE）。运营侧新增 Platform `POST /review-files/v1/files/{fileId}/download`（重检“审核查看/材料核验”权限与文件可用，签发 60 秒原版本地址，成功与拒绝均记审计，日志只记元数据）、Operations BFF O10（先核对文件属于 Risk 任务证据）与审核详情页“下载文件”；注册计划增至 39 条路由（新增 `client-onboarding-o10`）。本机 `local-environment` 新增 `storage` Profile（Chainguard MinIO 按摘要固定，HTTPS 19400/19401，自签名证书，版本化 SSE-S3 桶 `mor-platform-files`、`mor-platform-files-test`）；经授权向本机 `mor_platform` 写入格式检查策略 `CLIENT_ONBOARDING_FORMAT`（[执行记录](../requirements/20260914/client-console-注册与客户入驻/db/implementation/platform-file-scan-policy-local-execution.md)）。验证：Platform 新增 44 项（MinIO/S3 适配器各 11、FileScanHandler 5、审核下载 HTTP 17），全量 **412/412**；测试中修复 S3 授权到期可能晚于请求期限、审核下载成功未写审计两处。Operations BFF 定向 81/81（新增 O10 4 项）；BFF 全量另有 3 项 `ExternalApiBrowserAutoTest` 失败，系并行会话未提交的 Platform 外部 API 接入方字段改动随本地 install 进入，非本增量。Operations Web 983/983、构建通过。**未覆盖：**F01/F02 HTTP 入口与 `CompletedUploadVerifier` 的入口测试（Platform 测试环境尚无 CLIENT 会话与 CCT 模拟）、真实 AWS S3 验收、浏览器直传（需信任本机证书）。
- **2026-10-13 材料与表单通用化：** 用户确认材料由人工判断、系统不按国家区分，并按[通用化差异稿](../requirements/20260914/client-console-注册与客户入驻/generic-material-form-proposal.md)建议实施。Risk 材料查询只接受 qualificationType，每类资格映射一个全局程序（`application.yml` 带三类映射，开关仍默认关闭）；CCT 删除材料国家前置与 `MATERIAL_COUNTRY_REQUIRED`，C03 不再接收 countryCode、组织类型全局化、证件改为 `issuingCountryMode`、删除签发机构目录与客户端 `issuerScopeKey` 字段，并写入通用目录配置；BFF 按资格类型去重查询材料要求、删除 COUNTRY_REQUIRED；Web 签发国改为任意国家选择、LEI 类不需签发国；C03 路由白名单删除 countryCode。无 DDL；表/列注释仍是旧口径，未改。
- **本轮验证：** Risk `MaterialRequirementsHttpAutoTest` **33/33**（真实本机 MySQL/Redis；risk-test 模块因其他工作流的 decision 测试编译失败，本类为单独编译后运行）；CCT 生产与测试源码编译通过，CCT 已无入驻 auto 测试（合并后只剩 Checkout 测试），本轮改动**无 CCT 运行测试覆盖**；BFF `clean verify` **28/28**；Web **26/26** 与构建通过；预览模拟器 **5/5**。为编译 CCT 从源码重装了本机 Maven 缓存中的 platform-support-api 与 risk-api（含其他会话未提交的 DictionaryText 改动）。
- **未完成 / 待授权：** Pencil 设计稿中签发机构与材料提示未同步；C14 通用规范化与审核链仍未实施（入驻写接口请求幂等迁移已于 2026-10-15 完成，见本节首条）。（2026-10-15 更正：Risk 三个通用 Program 的[初始化 SQL](../requirements/20260914/client-console-注册与客户入驻/db/implementation/risk-generic-materials-init.sql) 已于 2026-10-14 在本机 `mor_risk`（[本机全链路记录](../requirements/20260914/client-console-注册与客户入驻/verification/local-e2e-execution.md) A7）和 mor-test（[发布记录](../requirements/20260914/client-console-注册与客户入驻/release/test-acceptance.md)）执行；部署状态见本节首条。经营模式通用列表 2026-10-15 用户复核确认，无代码改动。）

- **2026-09-16 Client 全量本地保存点：** 用户要求将当前 Client 相关改动全部提交；前端保存点为 `e3b922e`，本轮关联仓库为 `client-contract-service 94dc4f1`、`platform-support-service d861091`、`risk-compliance-service bbd86fb`、`operations-console-web eb1bdc4`、`api-gateway d55deb1`。根仓库归集 Client BFF、需求与 SQL 工件、模型生成器和测试；共享文件只纳入 Client 段落，其他任务保留。没有推送、部署、启用入口或执行新的数据库迁移。
- **本轮提交前验证：** Client BFF `clean verify` 27/27；Client/CCT/Platform/Risk 模型及 Schema 检查 154/154；Gateway `clean verify` 1261/1261，指令 96.624745%、分支 90.051323%，边界检查通过。手工 HTTPS 入口改用 Spring 关闭事件回收模拟器，修改后测试源码编译及边界检查通过；未重新运行手工 HTTPS 联调。Platform 生产及测试源码编译通过；Web 26/26、预览认证 5/5 与构建已在前端提交前复核。本轮未重新执行 CCT、Platform、Risk 数据库业务测试，历史结果保留原范围；需求站 137 项中 133 通过，4 项 Commerce 文档断言失败，未纳入本次修复。

- **当前详细设计：** 阶段 3 [完整评审稿](../requirements/20260914/client-console-注册与客户入驻/design.md)已收口；用户回复“可以”确认进入阶段 4，正在按批准顺序研发。单页、运营确认行业/MCC、取消入驻资料版本、RETURNED/REJECTED、邮箱密码及同账号一份申请均已落实到目标合同。本轮补齐[迁移方案](../requirements/20260914/client-console-注册与客户入驻/migration-plan.md)与[注册设计](../requirements/20260914/client-console-注册与客户入驻/registration-design.md)：空闭包和非空历史分开处理；注册复用已有管理 API，真实 ID 在发布时读取绑定，不编造固定 ID DML。非空环境行级迁移、外部配置和真实角色授予保留为发布前置，不登记为已执行。
- **设计收口增量与验证：** 40 操作不变；37 条禁用 Route 补全 Header/Query/Guard，C02 外部路径调整至认证 Cookie Path，响应超时由旧稿 15 秒修正为编译器上限 10 秒。运营 1 菜单 + 4 按钮、5 权限，对应 48 个管理命令模板；Client 入驻前采用固定入口。新增 CCT 16 表/Risk 10 表保守闭包盘点及 Platform 7 表元数据预检。四份生成器重复输出、源模型指纹、静态配置/SQL引用与文档链接检查通过；需求站构建及 136 测试、变更文件 ESLint 通过。证据见[设计收口检查](../requirements/20260914/client-console-注册与客户入驻/assets/stage3/design-closure-verification.json)。未运行 MySQL、配置 API、业务链或新一轮视觉验收。
- **单份申请决定与增量：** 同一 Account 的 INDIVIDUAL/ORGANIZATION 合计最多一份未完成 Client 申请；DRAFT/SUBMITTED/UNDER_REVIEW/RETURNED 占用，终态释放。评审生成器和目标 SQL 增加账号 + active_slot 唯一索引，保留原 Client 唯一索引；C04 原单恢复/类型冲突与 F01 明确申请目标、唯一 Client Tenant 核对已写入合同。没有新增表或字段、改路由数量或重画 Pencil。单份规则的[静态检查](../requirements/20260914/client-console-注册与客户入驻/assets/stage3/single-application-verification.json)通过：生成器重复输出、索引/可空性、状态预检、API/UI 规则与 105 个本地文档目标；没有执行数据库或业务测试。前轮详细检查中的数量/上传待决项及受影响生成物 Hash 以本增量为准。
- **数据与消费者：** 评审目标为 CCT 修改 10 表（新增 12 列、删除 27 列、重命名 1 列、21 项可空性调整），Risk 修改 3 表并新增 8 列任务分类表，Platform 补齐已有模型中的 7 表/90 列 SQL 投影。既有 CCT Checkout 查询真实读取待移除的状态/生效列，实施时必须同批迁移并保留批准 Profile ID/Hash。阶段 4 已开始同步 CCT/Risk 模型及生成器，Platform 补充表已纳入服务 Schema；Checkout 资料读取适配已修改。CCT 本机十表及 Platform 认证单表已按各自授权执行，回执见下文；Risk 与其他环境仍未迁移。阶段 3 投影与源指纹作为冻结评审基线保留，不再对新模型重复运行旧评审生成器。
- **当前页面：** 原 Client 文件新增 S05—S08 审核中、退回修改、通过和最终拒绝，Operations 原文件新增 OPS S05 行业/MCC 确认。与上一轮 S01—S04 合计 9 张新方案状态图；索引总数 Client 44 / Operations 入驻 5，共 49 张，Client 10 个分组。旧 40 张基线保留，尚未全部迁移。
- **前轮页面及详细工件验证：** 5 张新增画板经 Pencil 布局与截图核对，无裁剪/未完成占位；两个设计文件通过原生保存并核对无 Edited 标记。49 张 PNG 尺寸、索引位置无重叠；目标 CCT/Risk 14 表 159 列与 SQL 类型/可空性及索引引用一致；两份生成器语法与重复生成一致；40 个操作与 37 条禁用路由映射一致；109 个本地 Markdown 目标及根仓库/Operations Web 的 diff 检查通过。[检查记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage3/detail-design-verification.json)保留范围，未运行业务、数据库、浏览器交互或移动端验收。
- **阶段 4 数据工件与查询适配：** CCT 当前 38 表/408 字段/62 逻辑关系，280 必填/128 可空、59 唯一键/40 访问索引；补偿表的十五列目标及其本机执行结果见下方增量。Risk 当前 81 表/1,003 字段/127 逻辑关系；Platform 完整模型未改，Schema 从 25 表补到 32 表。原 Client Profile 版本字段已从模型和 Checkout 查询中移除，批准标识、内容 Hash、分类确认和共享引用保留；两枚无消费者旧状态枚举已删除。十张资料表的目标字段与阶段 3 冻结投影一致，四个 CCT 索引采用符合物理命名长度的短名，Risk 列按原 DSL 审计字段置后的顺序输出，字段合同不变。
- **阶段 4 前轮验证：** 模型/Schema/冻结投影 120 项、CCT 21 项局部检查为当时证据；当时 CCT 真实数据库尝试有 4 项环境启动失败，后续解决及完整回归见本节末尾增量。本轮 Platform 79 项认证/SMTP/审计投影/架构聚焦检查、Gateway 56 项身份/JWKS 检查、Client BFF 10 项真实 HTTP 协议检查、Web 4 项测试与生产构建通过。Client HTTP 和审计真实写库尝试均在数据库隔离前置失败，未注入本机测试凭据，不作为业务通过证据。命令、源码指纹和明确范围见[四工程检查记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/client-entry-verification-20260915.json)及[组件进度](../requirements/20260914/client-console-注册与客户入驻/development-progress.md)。
- **阶段 4 Gateway 后续验证：** 2026-09-15 “继续”后补齐通道专属 Cookie/Origin/预检、早期 Header 与终端错误 CORS、CLIENT_JWT IP/主体 Guard，以及关闭入口的标准 Problem。真实 HTTP/本机 Redis、严格 BFF/JWKS/Snapshot Simulator 完整 `clean verify` 1,261/1,261（70 Suite）；JaCoCo 指令 96.624745%（19438/20117）、分支 90.051323%（2281/2533），原门槛及架构/完整性通过。该结果取代前轮 Gateway 56 项专项及 Redis 未运行的状态；Platform、BFF、Web 未在本轮重跑。Netty ByteBuf 警告在未含本次 Client 改动的 HEAD 隔离副本也复现，本批未修复；不声称运行无告警。当时 Platform 凭据未注入，后续只读预检已找到安全入口，见下一条；完整 HTTPS 仍未联通，Client 开关只在 auto 测试启用。证据及 233 个源码指纹见 [Gateway 检查记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/gateway-http-verification-20260915.json)。
- **阶段 4 Platform 数据库预检：** 2026-09-15 “下一步”后，通过核验过的 `desktop-linux/mor-local-mysql-1` 内存凭据注入完成只读连接，未读取 `.env.local`。现有 `mor_platform_config_validation` 共 25 表；认证依赖的 7 张已有表共 120 列、30 个索引与当前 Schema 一致，CUSTOMER 账号/密钥、CLIENT 会话及专用测试日期审计链均为 0。当时确认缺少 `t_authentication_challenge`，凭据入口已解决。已准备[单表执行候选](../requirements/20260914/client-console-注册与客户入驻/db/implementation/platform-auth-local-execution.md)，只引用既有 CREATE；该预检时尚未授权/执行，后续获准执行见下一条。预检只涉及元数据及聚合计数，证据见[只读记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-auth-preflight-20260915.json)。
- **阶段 4 获准单表与认证回归：** 2026-09-15 用户针对精确单表执行范围回复“可以”；仅在本机 `mor_platform_config_validation` 新增 `t_authentication_challenge`，25→26 表，21 列/7 索引及全部表/列注释与批准 Schema 一致，原有表结构不变，已留结构备份，见[单表回执](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-auth-table-execution-20260915.json)。真实 HTTP 复现并修复密码查询列顺序导致正确登录 401，以及 Client 未登记事务完成导致成功审计缺失；改用现有事务执行器，保留原隔离/超时和独立审计写入。JDK 25 `clean test` 聚焦回归 82/82（16 Suite，含原 79 项及 3 个真实 HTTP 用例），覆盖注册/验证、会话/刷新重用撤销、重置/锁定/退出、账号上下文和实际 CUSTOMER/CLIENT 审计落库；MySQL/Redis 为仓库真实服务，Vault/邮件为 loopback 协议 Simulator。测试后账号/密钥/会话/挑战及专用审计日期数据、Redis 前缀残留均为 0；8 张依赖表结构再次核对通过。详见[认证回归记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-auth-http-verification-20260915.json)，包含 407 个源码/配置指纹。未跑 Platform 全量覆盖率或 HTTPS 浏览器联通；阶段 4 首批仍未完成。
- **阶段 4 HTTPS 协议联调：** 2026-09-15 用户同意继续后，临时 loopback Nginx HTTPS → 真实 Gateway → Client BFF → Platform → 仓库 MySQL/Redis 的 28/28 请求通过；Vault/SMTP/路由 Snapshot 使用本机协议模拟器。修复 Platform 旧 422 标题被严格解码为 502，以及 BFF 自动附加 Expires 导致 Gateway 拒收登录 Cookie；Gateway 测试路由对齐批准的 context。注册/单次邮件验证、CUSTOMER JWKS/账号上下文、刷新重用撤销、重置后旧密码与会话失效、重复退出及 Origin/错误合同均已验证，18 条审计结果实际落库。针对性回归 Platform 3、BFF 10、Gateway 36（共 49）与 Web 4/构建通过；本轮未重跑完整覆盖率门禁。自有数据库夹具/Redis 前缀恢复原计数、临时监听均停止。**浏览器交互尚未验收**：Chrome 临时证书提示需用户本人处理，未绕过或修改信任设置，不能将协议通过写成浏览器会话通过。证据见[HTTPS 记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/client-https-verification-20260915.json)与[复现说明](../requirements/20260914/client-console-注册与客户入驻/verification/README.md)。未执行 DDL、注册公共路由或部署，阶段 4 首批仍未完成。
- **人工迁移候选：** 已形成[执行候选目录](../requirements/20260914/client-console-注册与客户入驻/db/implementation/README.md)，覆盖 CCT 10 个 ALTER、Risk 3 个 ALTER 与 1 个 CREATE、Platform 7 个 CREATE；生成源指纹、重复生成及静态应用后的字段顺序/定义/主键/索引/表注释核对通过。候选静态验证本身不是 MySQL 执行结果；Platform 本机挑战表与 CCT 本机十表已有独立回执；Risk、其余 Platform 表及其他环境仍须实际 SHOW CREATE、闭包盘点、备份和精确环境批准。
- **阶段 4 CCT 本机前置核对：** 2026-09-15 用户回复“继续”后，完成现有 `mor_cct` 只读盘点：38 表均为空，428 列/142 索引及引擎/排序规则与冻结源一致；受影响十表注释也一致，其余两表仅有既存 Provider→Channel 表注释差异，未修改。16 表闭包与 14 项引用检查均为零；分别核对其他 Owner 本机库，保留三条 Commerce FAILED 退款任务及既有 Platform/Risk 配置，未清理外部记录。已保存 0600 SHOW CREATE 备份及[69 个源码指纹/预检记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-onboarding-preflight-20260915.json)，准备[本机十表候选](../requirements/20260914/client-console-注册与客户入驻/db/implementation/cct-local-execution.md)，**该轮 CCT SQL 尚待精确授权、未执行；后续获准执行见下一条**。安全注入现有本机凭据后，原先缺凭据的 CCT HTTP 边界 4/4 通过；没有运行需要目标表结构的完整 Checkout 数据库回归，也没有实现/验证草稿创建保存。另确认 CCT 旧补偿表与现行 Foundation 不兼容，C04 启用前须按[写链依赖记录](../requirements/20260914/client-console-注册与客户入驻/cct-write-dependencies.md)补齐模型与 Handler；不将该迁移混入既有十表 SQL。未改本机环境、执行 DDL/DML、启用入口或部署；当前仍为阶段 4。
- **阶段 4 CCT 十表执行及完整回归：** 2026-09-15 用户针对十表候选回复“可以”；重新核验空库、源指纹及停写状态，在本机 `mor_cct` 执行 10/10 ALTER。十表 99 列/27 索引与目标 Schema 完整匹配，其余 28 表 SHOW CREATE 未变，前后 38 表均为空，详见[执行回执](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-onboarding-table-execution-20260915.json)。实际数据库回归先复现预检查未装载 Client Party Profile，导致已批准客户误报 CLASSIFICATION_NOT_READY；修复为预检查仅查询当前 Client 批准资料，完整解析继续读取合同参与方。新增实际 HTTP 的草稿/批准及未确认分类场景，RED 10 项中 4 失败后，JDK 25 **clean verify 35/35（8 Suite）通过**，0 失败/错误/跳过。三轮自有夹具均回收；最终 38 表为空、结构未再改变、维护锁释放。当前 CCT 构建没有生成 JaCoCo 百分比，不声称覆盖率达标。MySQL 对批准的 TINYINT(1) 给出一条 1681 显示宽度弃用警告，已保留证据而未改动批准 SQL。测试/失败证据与 68 个源码指纹见[回归记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-checkout-mysql-verification-20260915.json)。这关闭本机十表与既有 Checkout 兼容回归前置；CCT 补偿表、入驻写链、审核开通及浏览器仍待完成，Risk 新资格依赖继续拒绝，阶段 4 未完成。未启用路由、部署或提交。
- **认证进度与流程决定：** Platform A01—A09、CUSTOMER 独立 JWKS、会话/刷新/密码恢复及八个写操作审计已有代码；Client BFF 九个接口、Cookie/Origin/错误响应处理，Gateway CLIENT JWT 与独立 CUSTOMER 公钥缓存，以及 Client Web 六类认证页/最小账号信息页已接入源码。服务端当前仅签发 ACCOUNT；本机认证持久化已完成上方聚焦验证；工作区切换、Tenant 成员门禁与完整 HTTPS 浏览器链尚未闭合。Platform/Gateway Client 开关仍关闭，路由未注册启用。用户本期继续注册页填写邮箱密码、邮件验证后再次登录；[密码归属优化](../requirements/20260914/client-console-注册与客户入驻/registration-password-binding-amendment.md)延至对外开放前，A01/A03 字段保持。
- **实现与环境：** 阶段 4 首批仍未完成、未部署。CUA 检查桌面登录、390px 注册布局、导航/必填和邮件链接页面，未创建真实账号或执行真实密码恢复；本机 Web 预览只用于查看界面。Pencil S01/S04 在原 Client 文件更新确认密码及说明并原生保存，增量 PNG 已归档，其他旧图仍为历史基线。本期已新增获准的 Platform 本机挑战表，并按各自授权执行 CCT 本机十表及独立补偿表迁移，认证及 CCT 回归均回收自有合成数据；未执行其他迁移/初始化、配置外部服务、启用路由、建分支、提交或推送；其他任务工作区修改保留。


- **阶段 4 CCT 补偿依赖增量：** 2026-09-15 用户同意继续开通恢复及草稿写链。先以当前 Foundation 物理合同复现旧二十列不兼容，再同步 CCT Catalog、PDManer、字段元数据、生成器及完整 Schema；仅补偿表变化，其余 37 表的模型和此前十表 SQL 保持原样。模型/字段/索引/原资料投影 84/84，文件级迁移安全 4/4 与旧候选静态验证通过；已执行十表生成器保留历史目标指纹，改变原 SQL 时要求另建迁移，验证脚本不再回写文件。[独立单表候选](../requirements/20260914/client-console-注册与客户入驻/db/implementation/cct-compensation-local-execution.md)已可审阅。本机只读预检确认旧二十列源结构、字符集/排序规则/注释/索引一致，38 表为空、无其他 CCT 连接或 18101 监听，已留私有结构备份；见[预检回执](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-compensation-preflight-20260915.json)。**该单表 ALTER 尚待精确授权，未执行任何 DDL/DML。** 现有 Java 未改，未重跑 Maven；Foundation Handler、Platform PLT-I17 实际写端和 CCT 创建/保存仍未实现，阶段 4 未完成。新增任务身份不恢复客户资料版本，业务 source_version 留在领域历史及 Platform Wire。

- **本轮补偿交付检查：** 包含四系统兼容门禁的最终模型回归 94/94，另有迁移安全 4/4；37 表完整 Schema 语句相同，264 个本地链接及 21 个工件指纹已核对，见[检查记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-compensation-verification-20260915.json)。需求站构建通过、135/136 项通过；唯一失败仍为既有 Risk 78/944/115 旧统计断言。没有执行补偿迁移、Java 回归或开通业务验证。

- **阶段 4 CCT 补偿单表获准执行：** 用户针对精确补偿表范围回复“可以”。重新预检后，在本机 `127.0.0.1:13306/mor_cct` 执行原指纹文件的唯一 ALTER，十五列的类型、默认值、可空性、注释、字符集/排序规则及 EXTRA 属性与模型匹配，四个索引（含主键）一致；零警告，其余 37 表 SHOW CREATE 未变，38 表仍为空，维护锁已释放，见[执行回执](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-compensation-execution-20260915.json)。该结果关闭上方历史增量中“补偿单表尚待授权/未切换”的本机前置；其他环境没有执行授权。
- **阶段 4 CCT 补偿技术合同与全量回归：** 隔离 Test 模块使用真实 Foundation 注册器、Mapper、Dispatcher/Scanner 和本机 MySQL，5 项覆盖同事务回滚/无事务拒绝、重复登记与 afterCommit、四列默认值/二进制身份/丢失唤醒扫描恢复、重试耗尽保留、并发 Claim/租约接管及旧执行者写回拒绝。完整回归首次 40 项中 3 项因旧测试引用已删除 source_id 报错；改为只读比较 Checkout 前后任务 ID 集合后，JDK 25 **clean verify 40/40（9 Suite）通过**，0 失败/错误/跳过，夹具恢复原计数。生产可执行包没有 Compensation/XXL 测试依赖，未接通业务开通 Handler 或 Platform PLT-I17；未生成 JaCoCo 百分比，不声称覆盖率达标。源码和运行证据见[合同回归](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-compensation-runtime-verification-20260915.json)。阶段 4 继续，个人/组织草稿创建保存、人工审核与浏览器验收仍未完成；未启用路由、部署、提交或推送。

### 前序设计基线记录

以下保留此前各轮的数量与验证范围；Google、旧分步、版本交互和旧补件规则已由上述详细设计取代，不能当作当前研发门禁。

- **范围与阶段：** 用户确认注册入驻与最小人工审核闭环，团队邀请延后，并回复“可以”确认阶段 2、进入阶段 3。详见[需求](../requirements/20260914/client-console-注册与客户入驻/requirement.md)、[差异与系统影响](../requirements/20260914/client-console-注册与客户入驻/technique.md)及[确认记录](../requirements/20260914/client-console-注册与客户入驻/delivery.md)。目前交付阶段 3 页面子包，完整接口、数据和 SQL 设计尚未闭合，未进入研发。
- **源码与设计入口：** 阶段 2 的[静态清单](../requirements/20260914/client-console-注册与客户入驻/assets/stage2/source-inventory.json)记录当时 Client Web/BFF 目录不存在；用户随后创建 `client-console-web/client-console.pen`，现已按其要求写入 Client UI，尚无可运行 Client 前端。Platform CUSTOMER 认证、文件、CCT 入驻写链和 Risk 尽调链的源码缺口仍按阶段 2 证据处理，不能以模型或设计图推断业务实现。
- **页面设计：** [Client 源文件](../client-console-web/client-console.pen)包含 36 张画板（35 张状态/手机稿、1 张设计对照图）；[Operations 源文件](../operations-console-web/docs/operations-console.pen)追加 4 张入驻审核画板。对应 11 / 2 个拟定功能页，详见[页面合同](../requirements/20260914/client-console-注册与客户入驻/ui-design.md)和[画板索引](../requirements/20260914/client-console-注册与客户入驻/assets/stage3/ui-manifest.json)。并行入驻数量仍待用户选择；没有擅自限制一份申请或放开多份。
- **认证页视觉修订：** 按用户简约化反馈，01—07 改为白底、400px 居中表单并移除左侧品牌宣传区；22 手机注册稿同步品牌、标题与控件样式。8 张受影响画板无布局裁剪报告；PNG 同步更新，功能页及总画板数量不变。
- **个人/组织划分修订：** 按用户要求把个人流程排为独立的 4 步（10、24—26），组织流程排为独立的 5 步（09、11—14），增加个人经营资料、材料与确认提交三个变体，并在顶部加入 27 对照图。个人流程不展示组织相关人员步骤；组织仍按适用要求填写人员及关系，两个分支均保留适用审核门禁。此次未改 Operations 设计。
- **子页面修订：** 用户要求展开编辑人员及其他子页面，新增 28—36 共 9 张：新增/编辑人员、添加/编辑关系、移除草稿关系、上传/更换材料、文件预览、已提交资料、补件记录和未保存退出确认。主页面补齐对应入口，长表单使用抽屉、短确认使用弹窗；这仍是静态画板，尚无可点击原型。Operations 未改动。
- **填写流程整理：** 按用户要求，把 36 张 Client 画板归入 8 个有序画布/图层分组：账号、身份选择、基础/经营资料、组织人员、材料、提交、审核/补件、结果/工作区。子页面与异常状态归到对应步骤，个人明确跳过组织人员；保留原画板 ID 和 13 个功能页映射，分组容器不计作产品画板。原生 Pen 图层顺序已核对，保存标记已清除；8 个分组及内部画板均无重叠、裁剪或未完成占位。
- **页面验证：** 两个 `.pen` 的前序保存证据保留，本次 Client 已再次通过 Pen 保存并核对磁盘大小/更新时间。当前 36 张 Client 画板无布局裁剪报告或未完成占位，9 组子页面入口/返回映射通过；个人 4 步/组织 5 步及个人无组织文本残留检查沿用未改分支的前序证据，Operations 4 张的布局检查沿用前序证据。全部 40 张 PNG 尺寸匹配、画板无重叠；61 个本地文档链接、13 个功能页映射及本轮根仓库 `git diff --check` 通过，Operations Web 保留前序检查。未重跑本次未改源码的需求站，阶段 2 证据保留；未运行业务或浏览器交互测试。
- **实现与环境边界：** 本需求未实现、未部署；本次修改设计、需求与状态路由，未改应用业务代码、模型或 SQL，未连接数据库、配置外部资源、执行部署、创建分支或提交。原工作区其他任务差异保留。阶段 3 页面验证见[检查记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage3/ui-verification.json)，不能作为注册、邮件、文件扫描、Google、键盘交互或审核业务通过的证据。
- **阶段 2 文档验证：** 三份文档的 36 个本地链接、11/15/8 项功能及验收编号、13 个源码 Hash 与 5 个目录指向核对通过；`git diff --check`、需求站生产构建及 136/136 检查通过。六个既有应用仓库状态相对本阶段清单未变化，Operations Web 原有 `docs/operations-console.pen` 修改保留；无本需求应用功能测试。

- **阶段 4 Platform 工作区开通写端：** 2026-09-15 按继续授权实现 PLT-I17 的 CLIENT Provision、顺序状态投影及来源查询。首次在同一 Platform 事务创建 PENDING Tenant、真实申请人 OWNER Member、Tenant 范围的 Onboarding Role/Grant；首次 ACTIVE 撤销临时授权并授予 TENANT_OWNER，保留独立安全/成员停用。来源/账号冲突、旧开通与乱序状态受控，Redis 双资源锁之外保留唯一键、行锁及 CAS；通过真实 HTTP/MySQL/Redis 的 12 项业务测试，含租约到期、不同申请人竞争、整体回滚、角色缺失与审计。完整 JDK 25 **clean verify 572/572（57 Suite） 通过**，0 失败/错误/跳过；JaCoCo 指令 **97.868386%**、分支 **90.083988%**，两项 90% 门禁通过。修复全量回归暴露的旧挑战表、SMTP 依赖、继承 HMAC 合同及新增日志路径测试预期；补充 Client 公钥、凭据、邮件证明、刷新会话和并发注册/状态变化测试，24 项真实认证 HTTP 场景通过；关闭测试客户端自动 503 重试，保留首次响应断言。没有降低断言或覆盖率门槛。核对五张 IAM 表结构与 Schema 一致，26 表库保留，合成账号/租户/成员/审计回收、目录锁释放。接口和边界见 [PLT-I17 合同](../requirements/20260914/client-console-注册与客户入驻/platform-tenant-projection-contract.md)，证据见 [开通回归](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-tenant-projection-verification-20260915.json)。`tenant.projection.enabled=false`；未执行迁移 SQL、启用公共路由或部署。**CCT 草稿创建/保存及业务补偿 Handler 尚未接入，不能视为真实 CCT→Platform 开通链完成。** 文件、Risk 审核、浏览器验收和阶段 4 仍未完成。

- **阶段 4 CCT 工作区恢复（前次独立切片）：** 2026-09-15 继续个人/组织草稿与开通范围，先完成独立恢复链。`CLIENT_TENANT_PROJECTION` 引用准确的 CLIENT 生命周期审计明细，Payload 为 NULL；校验原申请人、不可变身份及安全目录/Hash，事务结束后通过 PLT-I17 查询/开通/投影。重复、截断响应、延迟、跳号、终态、身份冲突和重试耗尽均受控；默认关闭。普通资料保存不写 BCR 表单快照，不增加资料版本。来源机制见[恢复合同](../requirements/20260914/client-console-注册与客户入驻/cct-workspace-recovery-contract.md)。
- **本轮验证与修复：** CCT 实际执行先暴露通用 CodeEnum Handler 被其他枚举实例覆盖、任务停留 INIT；Foundation 最小映射检查同样先失败，修复 Compensation 自动配置和 XML 的按目标类型绑定后 13 项聚焦检查通过，仅安装到本机 Maven 缓存。修正 Simulator 的严格 Problem 格式后，25 项聚焦检查通过；最终 JDK 25 **clean verify 62/62（12 Suite）**，0 失败/错误/跳过。覆盖实际 MySQL/本地事务、Foundation Dispatcher/Scanner、Platform loopback HTTP 及已有 Checkout 回归；38 表测试计数恢复为零。证据见[恢复回归记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-workspace-recovery-verification-20260915.json)。CCT 未生成 JaCoCo 百分比，Foundation 未跑全模块数据库套件，不扩张通过范围；需求站 135/136，仍为既有 Risk 数量断言，构建通过。
- **该轮边界的后续收口：** 固定回执方案已由用户确认，草稿写入的后续实现与验证见下方；前次恢复证据仍保留当时的独立范围。

- **阶段 4 CCT 当前草稿（前次增量）：** 2026-09-15 已实现 C04 创建/恢复、C05 Owner 主资料读取和 C06 部分保存。CCT 每次重新核对 Platform CLIENT 会话及本库申请归属；个人/组织共用未完成申请占位，分别存储基本资料与 INDIVIDUAL_KYC/ORGANIZATION_KYB 资格类型。只允许 DRAFT/RETURNED 且原资料未批准时保存；邮箱/电话通过事务外 Vault V1 Protect/Reveal，主表单日志只记元数据。创建同事务落库 CLIENT 安全审计及恢复任务，普通保存不写表单历史或行业/MCC。固定回执决定已同步[API 合同](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#7-c04c05c06-的-cct-私网组件)，旧命令重放返回原状态/回执，不能因后续修改或终态建立替代申请。
- **主草稿验证（前次）：** JDK 25 `clean verify` **82/82（13 Suite）**，0 失败/错误/跳过；其中 19 个草稿 HTTP 场景和默认关闭检查，保留全部 Checkout/恢复回归。使用真实本机 CCT MySQL/Redis、生产 Processor/Service/Mapper 和 Foundation；Platform/Vault 为 loopback 协议 Simulator。全字段往返、个人/组织 KYC/KYB 对照、跨账号/跨申请防护、原回执、NULL 清空、审核冻结、Vault 失败/未知结果/日志保护、SQL 屏障下租约到期后重复创建回滚及保存等待期间状态重验均通过。修复曾影响旧恢复映射和健康配置的问题后完整重跑，38 张表夹具回收、维护锁释放。CCT 未生成 JaCoCo 百分比；不宣称真实双 Owner 联调或覆盖率门禁通过。[草稿验证证据](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-draft-verification-20260915.json)保存命令、失败先例、源码/制品指纹与日志；需求站构建通过，135/136，剩余为既有 Risk 模型统计断言。
- **阶段 4 CCT 组织人员与关系（最新增量，2026-09-15）：** 按用户“继续吧”完成 C07—C11 和 C05 Owner 人员/关系列表。C07 同事务创建个人 Party、单份资料、地址、候选证件及首条 DRAFT 关系；C08 增量编辑；C09 给授权人员追加关系；C10 合并指定关系属性；C11 移除本申请关系并保留人员。个人申请不能调用组织人员写入口；原申请/主体归属、DRAFT/RETURNED、批准与共享引用均由 CCT 核对。已生效/已结束关系和批准人员只读，合同/Partner/OE 批准引用同样保护资料。普通人员/关系变更不增加资料版本、BCR 快照或跨系统业务事务；C07—C10 使用固定回执，C11 保持 204，删除后重放不重复执行业务动作。细节见[人员与关系合同](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#8-c07c11-的-cct-私网组件)。
- **最新验证：** JDK 25 `clean verify` **99/99（13 Suite）**，0 失败/错误/跳过；HTTP 草稿套件由 19 增为 36 项，本轮新增 17 项人员/关系场景。真实本机 MySQL/Redis、生产 HTTP/Processor/Service/Mapper 与 Foundation；Platform/Vault 仍为 loopback HTTP Simulator。覆盖全部写入口状态/归属、脏字段与 NULL、日期及 10 位小数、关系唯一性、共享资料、删除后原回执、命令键跨申请冲突、整体回滚，以及 SQL 屏障下 Redis 租约到期后的一次创建和人员/关系并发合并。先复现缺失 C07 的 404、冻结合同引用缺批准标记仍可修改、关系顶层字段协议不匹配，再修正并完整重跑。38 张表计数恢复为零，维护锁释放；源码与制品指纹见[人员关系验证记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-people-relations-verification-20260915.json)。CCT 没有 JaCoCo 百分比；需求站构建通过、135/136，唯一失败仍是既有 Risk 模型数量断言。
- **剩余交付范围：** C05 完整 BFF 聚合和 Web 入驻接线、材料上传/关联、正式提交与永久身份绑定、Risk/Operations 人工审核及真实 CCT→Platform/Vault 联调尚未完成。状态冻结测试设置合成审核状态，不代表审核链已接通。`onboarding.draft-enabled=false`；本轮只操作合成夹具，没有执行 DDL、激活外部路由、部署、提交或推送。
- **材料链前置补充（前次，2026-09-15）：** 用户继续材料上传/关联后，只读核对发现 Platform 尚无文件/扫描 Owner 实现，`mor_platform_config_validation` 当前 26 表中缺少六张文件表及补偿表；`mor_cct.t_application_document` 已存在且 0 行，`mor_risk` 的 Program/Version/Requirement 三表存在且各 0 行。此为本机运行库观察，Risk 当前生成目标统计仍按下节，不能混同。证据见[只读记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/file-prerequisites-preflight-20260915.json)。当时形成[文件合同补充](../requirements/20260914/client-console-注册与客户入驻/file-upload-closure-proposal.md)、七表/91 列候选 SQL 与字段索引清单，未新增代码或修改模型；随后用户“可以”确认，最新增量见下。前述 99 项为前次人员代码验证，不能作为文件链通过证据。
- **文件提案检查：** 七表/91 列/7 主键/13 唯一索引/11 普通索引计数、SQL 与字段清单指纹、五张未改文件表与当前 Schema 一致性、补偿表与 CCT 已验证合同一致性、Upload 原列保持及 8 增 1 删、完整注释和 66 个本地链接检查通过；两处 `.pen` 链接仅跳过，不打开其内容。需求站生产构建通过，136 项中 135 通过、1 项失败，仍是既有 Risk 模型统计断言，不修改其他任务模型消除失败。详见[静态核验与本轮站点证据](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/file-upload-proposal-verification-20260915.json)。未运行新的 Java 业务测试，不把设计核验描述为上传/扫描实现通过。
- **文件目标授权与模型（前次增量，2026-09-15）：** 用户确认文件合同后，已同步 Platform Upload 的 8 增 1 删、存储目标唯一索引，以及补偿表的现行 15 列合同；现行模型与 Schema 对齐，其余 62 张实体、保留字段 ID 和公共模板不变。新 CCT `ClientFileTargetApi` 在同一只读快照核对原账号/申请/主体，区分主主体、有效关联人员与已移除/结束关系；返回最小 canUpload/canRead，不读取其他库，不回调 Platform/Vault，不增加资料版本。相关私网路径纳入 metadata-only 日志，仍随默认关闭的入驻开关启用。CCT **clean verify 105/105（13 Suite）**，新增 6 个场景；38 表测试夹具归零，维护锁释放。该次尚未实现 Platform 上传/扫描业务、Risk 要求查询与 CCT 材料命令；F01 后续进展见下。
- **前次模型门禁与 SQL 预检：** 模型/Schema 聚焦检查 **40/40**；扩大场景检查 **51/52**，既有场景目录缺 `t_api_scope`（改前模型亦为 11/12，已独立对照）；需求站构建通过、**135/136**，保留既有 Risk 统计断言失败。122 个本地链接及七表执行 SQL 与当前 Schema 逐表一致性通过。代码/制品范围和指纹见[文件目标增量证据](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/file-target-implementation-verification-20260915.json)。[本机七表执行清单](../requirements/20260914/client-console-注册与客户入驻/db/implementation/platform-files-local-execution.md)已具备 SQL 指纹、原 26 表结构备份和只读预检，当时单独授权问题已发出，未执行；后续用户已确认，执行结果见下。未配置真实存储/扫描供应商，未部署、启用公共路由或提交代码。

- **Platform 文件本机 SQL（2026-09-15）：** 用户单独确认后，在 `desktop-linux/mor-local-mysql-1` 的 `127.0.0.1:13306/mor_platform_config_validation` 执行指定指纹的七个 CREATE；现为 33 表。持有原目录隔离锁、逐条保存回执，没有 DML、DROP、ALTER 或授权变更。七张新表的 91 列、默认值、字符集/排序规则、注释和索引与批准 Schema 一致，新增表为空，原 26 表结构及计数保持；原 SQL 指纹不变。初次校验脚本误把 CURRENT_TIMESTAMP(6) 转小写，修正后仅重新读取元数据，没有重跑 DDL。见[执行记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-files-execution-20260915.json)与[只读复核](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-files-post-execution-verification-20260915.json)。


- **Platform F01 上传开始（前次增量，2026-09-16）：** 创建第一个有真实消费者的 `platform-support-file-document` 能力模块；F01 经过当前 CLIENT Session、CCT 原申请/主体授权、Platform 唯一 PENDING Client Tenant/有效 OWNER 角色三层核验。Upload 与 Platform 既有幂等回执在一个短事务保存，固定申请/主体、租户/上传人、展示名、原存储目标、扫描策略和到期时间；提交后才请求存储授权。相同键恢复原 Upload，异参冲突，已完成/失效/到期不重新签发。授权丢失或供应商故障后可用原键重试。CCT、HMAC 与存储 I/O 不在数据库事务中；模块不跨 Owner 查询数据库。新文件路径使用 metadata-only 日志，客户端不能提交 Tenant、Client、存储 Key 或扫描策略。`files.onboarding.enabled` 默认关闭，生产源码没有模拟成功的存储适配器。
- **F01 前次验证：** JDK 25 Reactor `clean verify` **593/593（59 Suite）**，0 失败/错误/跳过。新增 16 个上传 HTTP 场景、4 个配置边界场景及 1 个无效会话场景；包括原记录/原到期恢复、错误主体与 Tenant、残留多工作区、字段与策略限制、损坏 CCT/存储响应、完成回执 SQL 后的原子回滚、会话撤销以及 Redis Lease 到期后的单记录保护。新 File/Document 模块纳入实际运行覆盖率，保持既有 90% 指令/分支门槛；最终指令 **97.8049%**、分支 **90.1036%**。33 表用例计数恢复；测试结束后重新锁定并逐表核对，新增七表为空，原 26 表定义及计数不变。新业务仍使用真实本机 MySQL/Redis、CCT/存储测试作用域 HTTP Simulator；不是双 Owner 真实联调或供应商验收。源码、制品、完整 Suite 与日志见[上传开始验证记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-upload-start-verification-20260916.json)。
- **Platform F02 / FILE_SCAN（前次增量，2026-09-16）：** 上传完成入口采用空 Body 与 UUID 幂等键；只读取原 Upload 的申请/主体/Tenant。服务端流式计算真实字节长度与 SHA-256，比较受控存储适配器给出的 MIME，并要求准确不可变版本和已验证加密保护引用；存储/CCT/HMAC 调用全部在本地事务外。短事务内重查 IAM 和原 Upload，以 Upload 为资源锁，加账号/Upload 行锁、状态 CAS 和唯一键，一起保存 File、Owner Ref、固定完成回执和 Foundation `FILE_SCAN` 任务；事件登记失败全部回滚。原成功键只重放固定回执，不重读文件字节；审核后按原 Tenant 的当前 `TENANT_OWNER` 读取，权限撤销仍拒绝，不重新开放上传。
- **扫描恢复实现：** Handler 从原 File 和冻结策略要求恢复，任务以 `fileId` 为事件键、`sub_event_type="-"`、`payload_json=NULL`。每个初始检查固定 `fileId/requirementId/attempt=1`，供应商未知结果沿原检查恢复，不使用补偿 execute_count 生成新尝试；本期无显式重扫命令。全部要求接受且扫描器版本符合最低要求才进入 AVAILABLE；恶意/DLP 拒绝隔离，格式拒绝进入 REJECTED，缺配置/依赖、错版本/摘要或不完整证据保持不可用。文件行锁和检查结果唯一性阻止旧工作者覆盖已有结果；重试、Lease 接管、最大次数 FAILED 与指标复用真实 Foundation Dispatcher/Scanner。补偿能力只随文件开关启用；未配置外部 XXL 调度。
- **F02 / 扫描前次验证：** JDK 25 Reactor `clean verify` **654/654（61 Suite）**，0 失败/错误/跳过；新增 F02 HTTP **27** 项、独立扫描 Handler **33** 项及完成入口无效会话 **1** 项。覆盖跨账号/错误 Client、批准后角色与撤销、空 Body、实际大小/摘要、不可变版本/加密元数据、失效上传、原子回滚、会话撤销、并发唯一文件、部分/全部要求、未知检查恢复、迟到覆盖、错误扫描证据、退休策略、租约接管、旧执行者晚返回、未到期与耗尽等边界。真实本机 MySQL/Redis 与 Foundation 调度链参与；CCT/存储/扫描只使用测试作用域协议 Simulator。保留原 90% 门槛，指令 **97.7958%**、分支 **90.2481%**。全量验证修复了共享测试上下文读取静态公钥夹具的隔离问题，未改变认证业务决策。33 表测试计数恢复，随后只读复核原 26 表定义/计数不变、新七表为空；没有重新执行 SQL。源码、制品、完整 Suite 和日志见[完成与扫描验证记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-upload-completion-verification-20260916.json)，数据库见[独立只读复核](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-files-post-completion-verification-20260916.json)。
- **文件读取与材料关联（最新增量，2026-09-16）：** 用户“可以”后继续阶段 4，补齐 Platform F03/F04、私网 verification、Risk 要求查询，以及 CCT C12/C13/C05 材料引用。F03 纯读取；F04 对原不可变版本签发最长 60 秒授权，在存储调用后重验会话、原申请权限、IAM 和 File。批准后沿原 Tenant 的当前 Owner 权限读取；不会改绑后来申请。私网验证核对原账号/Client/申请/主体/文件且不回调 CCT；API 状态使用稳定 String 代码，由 Owner API enum 定义并显式校验未知代码。
- **Risk/CCT 材料行为：** Risk 通过有界配置精确解析资格类型＋国家与 programCode，只读现有有效 Program/修订/要求，未配置、歧义、缺审核事实或要求异常均返回不可用，不默认无材料。CCT 先在事务外核对真实 Owner HTTP 证据，再在申请锁内复核状态、主体、国家并保存文件引用和固定回执；内容摘要只取自 Platform。删除只移除引用，旧键重放不重建；移除关联人员后保留材料并标记超出范围，原文件可按保留引用只读访问。普通材料操作没有资料版本或 BCR 快照。个人/关联人员按居住国家、组织按注册国家是已告知用户的实现假设，尚未收到该问题的明确答复；未初始化真实国家规则。完整语义见[材料 Owner 合同](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#10-c12c13c05-材料的-owner-组件)。
- **最新完整验证：** 三个 JDK 25 Reactor 分别 `clean verify`：Platform **686/686（62 Suite）**、Risk **265/265（33 Suite）**、CCT **139/139（13 Suite）**，0 失败/错误/跳过；新增文件读取 32 项、Risk 要求 32 项、CCT 材料 34 项。Platform 保留 90% 指令/分支门槛，最终指令 **97.6545%**、分支 **90.0097%**；Risk/CCT 不宣称未配置的覆盖率百分比。各自使用真实本机 MySQL/Redis 与生产入口，外部服务为协议 Simulator，不是实际多 Owner 端到端验收。先复现缺失接口，再修正 CCT 回执超过既有 64 字符、模拟器错误响应格式及 API 传输代码；删除无消费者的动作解析方法后重跑并通过原覆盖率门槛。源码/制品指纹、Suite 与失败先例见[材料链验证记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/material-read-association-verification-20260916.json)。
- **数据库复核与遗留：** Platform 33 表测试计数恢复，随后独立只读确认原 26 表定义/计数不变、新七表为空；CCT 38 表回收归零，材料表完整匹配；Risk 全库计数恢复，DD 三张读取表为空且字段类型、NULL/默认值、主键及索引结构匹配。但 Risk 三表既有中文列/索引/表注释乱码，完整注释一致性未通过，仍待单独修复；本轮未执行 DDL 或修复注释。见[Platform 只读记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/platform-files-post-read-verification-20260916.json)和[CCT/Risk 元数据记录](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/material-owner-read-model-verification-20260916.json)。
- **前次剩余交付范围（BFF/Web 接线后续见下）：** BFF 的完整 C05/要求/文件状态聚合与 Web 材料接线、正式提交与永久身份绑定、Risk/Operations 人工审核及真实多 Owner 联调尚未完成。真实存储/扫描供应商未选择，准确不可变版本、MIME/加密验证和同检查恢复仍须真实适配器验收；所有新增入口默认关闭。未初始化真实策略或国家材料规则，未配置外部资源、启用公共路由、部署、远程发布制品、提交或推送；API 只安装到本机 Maven 缓存供消费者编译。既有需求站 Risk 统计和场景目录 API Scope 检查本轮未修改或重跑。


- **阶段 4 BFF/Web 资料与材料接线（前次增量，2026-09-16）：** 按用户“可以”继续前轮下一步。CCT 增加只读 C01 最近申请摘要（活动申请优先、最多 20 项、hasMore）和 C05 主主体 `subjectPartyId`；打开/刷新页面不创建申请。BFF C01/C03/C05 验证当前 CLIENT 会话，组合 CCT 原资料/引用、Risk 非个人材料要求及 Platform 文件状态；并发查询有界、同一文件单次读取、失败显式 unavailable，结束再核验会话。写命令保持原账号/会话/键，分别转发唯一 Owner；BFF 没有数据库、跨 Owner 写事务或文件正文。Risk 只将通用材料目录 Caller 扩到 Client BFF；Platform 私网 verification 仍仅允许 CCT。
- **Web 行为：** 白色单页区分个人和组织，覆盖主表单全部草稿字段、组织人员及多关系编辑、材料上传/扫描检查/关联/移除与短时下载。资料按 dirtyFields 保存，审核只读由 Owner editable 控制；不增加行业/MCC、OTP、资料版本或邀请。原写意图冻结 Body/幂等键；未知结果或成功后回读失败只恢复原命令。PUT 成功后可在当前账号＋申请下恢复 completion/扫描/关联，浏览器只保存非敏感坐标，不保存表单、文件名/字节、Token 或临时授权。传输完成前仅支持本页原文件重试。真实类型/机构/经营模式受控目录尚未接入，草稿文本不是正式提交的目录确认。合同见[第 11 节](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#11-资料与材料页面的-bff-投影边界)。
- **本轮验证：** CCT JDK 25 `clean verify` **140/140（13 Suite）**；Risk **266/266（33 Suite）**；BFF **25/25（2 Suite）**；Web **15/15**，TypeScript/Vite 生产构建通过。全部 0 失败/错误/跳过；CCT 38 表测试计数恢复为零，Risk 全库测试计数恢复。BFF 使用真实 Spring/HTTP 与三 Owner 协议模拟器，覆盖聚合、归属 Header、重复命令、文件去重、部分失败、未知状态、会话撤销、协议约束与默认关闭/管理隔离。Platform 生产代码未改，本轮未重跑其 686 项，前次证据仍独立保留。源文件、制品、Suite、命令及日志见[本轮证据](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/client-browser-materials-verification-20260916.json)。
- **浏览器范围：** 构建产物配合仅监听 loopback 的测试协议模拟器，核对主表单保存回显、人员新增首次响应丢失后的原操作恢复（仅一名人员）、人员编辑、材料面板、个人已通过全字段只读；默认桌面及 390px 无横向溢出，无浏览器错误。修复主表单与人员面板字段 ID 重复，标签与焦点指向准确输入。截图见[桌面单页](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/onboarding-ui/organization-desktop.png)、[窄屏](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/onboarding-ui/organization-mobile.png)及[材料面板](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/onboarding-ui/upload-mobile.png)。此浏览器验证没有连接真实 BFF/Owner/存储，不替代真实多服务或上传供应商验收；浏览器未绕过证书或填写真实凭据。
- **前次剩余范围与环境边界（目录后续见下）：** C02 工作区切换、C03 受控类型/机构/经营模式目录、正式提交与永久身份绑定、Risk/Operations 人工审核、真实多 Owner 联调及存储/扫描适配器验收未完成。没有提交审核按钮或伪造审核结果。个人/关联人员国家来源仍为已告知但未收到明确答复的实现假设；未初始化真实材料映射或策略。Risk 三表既有注释乱码继续保留为缺口。本轮未执行 DDL、配置外部资源、启用公共路由、部署、提交或推送；新 BFF `client.onboarding-enabled=false`，Owner 开关仍默认关闭，轻量 JAR 只安装本机 Maven 缓存。需求站和无关模型门禁未在本轮重跑，不扩大前次通过范围。



- **阶段 4 受控目录 Owner/BFF/Web 接线（最新增量，2026-09-16）：** CCT C03 查询当前 CLIENT 会话可见的组织类型、经营模式、证件及签发机构目录，前后核验会话，GET 不创建申请或身份键。目录默认留空；启动时验证重复代码、准确作用域、客户类型、GLOBAL/国家互斥及规模上限。组织类型按注册国筛选；证件签发国独立于居住国。配置不可变、修改后重启，无新增数据库表或资料版本。CCT API/Web 新增 provided Lombok 和显式编译期处理器以实现独立 ResponseBody 类；合同见[第 12 节](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#12-c03-受控表单目录)。
- **目录页面与聚合：** BFF 并发读取 CCT 目录与 Risk 要求并保留独立可用性，未知目录状态拒绝使用，单个 Owner 失败保留另一方事实，会话失效仍使整个请求失败。Web 主表单与关联人员共用下拉；换注册国清空组织类型，换证件清空机构/签发国，机构选择带出准确签发国。加载失败/旧代码不清空原资料，旧值标注待确认；异步响应按原类型/国家隔离，页面继续走 dirtyFields 保存。目录 AVAILABLE 不等于正式提交就绪。
- **本轮验证：** CCT JDK 25 `clean verify` **146/146（14 Suite）**，BFF **27/27（2 Suite）**，Web **18/18** 与 TypeScript/Vite 生产构建通过，0 失败/错误/跳过；CCT 38 表测试计数全部恢复。新增 6 个 CCT 场景覆盖真实 HTTP 目录、准确国家/机构、空目录、协议/会话和启动配置；新增 2 个 BFF 场景覆盖 Owner 分别失败、未知状态与撤销；3 个 Web 场景覆盖依赖值清理、签发国独立与旧值保留。Risk/Platform 源码未改，本轮未重跑其完整测试。详见[本轮验证证据](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/client-form-catalog-verification-20260916.json)。
- **浏览器与边界：** 使用构建产物及仅监听 loopback 的合成协议模拟器，已验证旧代码待确认、组织目录选择/保存、不同签发国、缺目录不丢其他资料、关联人员 GLOBAL 标识及保存、已通过个人 0 可编辑字段；桌面及 390px 无横向溢出，无浏览器警告/错误。截图见[桌面](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/catalog-ui/organization-desktop.png)与[窄屏](../requirements/20260914/client-console-注册与客户入驻/assets/stage4/catalog-ui/organization-mobile.png)。不是实际 CCT/BFF/Risk/存储的浏览器端到端；临时页面与监听已关闭。
- **当前未完成：** C02、C14 正式提交/永久身份绑定、Risk 审核登记与 Operations 审核、真实多 Owner/存储/扫描验收仍未完成。个人/关联人员按居住国、组织按注册国仍是待确认假设；本轮已询问材料国家口径和首批开放国家，截至记录未收到答复。真实目录、证件规范化及国家材料规则均未初始化；不得从合成测试配置推断任何国家政策。没有提交审核按钮或伪造成功；既有 Risk 注释乱码缺口未改。本轮无 DDL、外部资源、公共入口启用、部署、提交或推送；入驻开关仍默认关闭。需求站及无关模型门禁未重跑。


- **预览认证修正（2026-09-16）：** 用户发现未登录也能直达入驻页，根因为独立 `preview-onboarding.mjs` 无条件返回登录/刷新成功并开放模拟业务接口。现改为显式校验合成演示账号、随机内存会话、独立一小时预览 Cookie，所有模拟入驻读写要求有效 Bearer；退出同时撤销 Access Token 与 Cookie。正式 Web/BFF/CCT 认证行为未修改，不更改正式 Secure Cookie 或 Origin 校验。复现入口及演示账号见[验证说明](../requirements/20260914/client-console-注册与客户入驻/verification/README.md#bffweb-入驻接线验证)。
- **本次验证与范围：** 新增 5 项预览 HTTP 测试先在旧脚本全部失败，修正后 **5/5** 通过，覆盖匿名读写、错误密码、Cookie/Bearer、退出撤销和独立会话；Web 新增匿名恢复失败不得发送业务请求的回归，`npm test` **19/19**、TypeScript/Vite 构建通过。浏览器实际验证未登录直达跳回登录、错误密码被拒、正确登录进入申请、刷新保留会话、退出后直达被拒，最终停在登录页；修正后的 loopback 预览保留供用户体验。仅证明合成协议下页面与模拟器行为，未重跑 Java 测试，不宣称真实多 Owner 认证端到端通过；无生产代码、SQL、共享部署、公共入口、提交或推送变更。

- **登录页错误反馈局部修正（2026-09-16）：** 用户要求拆小后，本轮只修改登录页：独立 LoginForm 使用中文字段校验、错误红边、下方预留提示位置，关闭原生验证气泡；凭据失败统一提示，编辑后清除，网络/限流/服务异常不误标凭据错误。移除登录页大块错误卡片及请求编号，其他认证与入驻页面不扩展。`client-console-web/client-console.pen` 的 S01 已同步，新增 S01a/S01b/S01c 桌面及 390px 错误态；通过 Pencil 读取与修改，未通过文件系统读取其内容。
- **本轮验证及本地交付：** 先复现原错误态使按钮下移 58.4375px、邮箱上移 33.203125px；新增聚焦测试首轮因缺少实现失败，完成后 Web **22/22** 与 TypeScript/Vite 构建通过。浏览器校验邮箱格式、空字段、凭据错误、编辑清除、正确登录和退出；桌面及 390px 正常/错误态的输入框与按钮位移均为 **0px**，窄屏无横向溢出；UI 稿四状态无裁切。更新现有 `127.0.0.1:52649` loopback 预览构建，供用户测试；仍使用演示协议，不宣称真实认证链路验收或共享环境部署。未修改 Java/API/数据库或执行提交、推送。

- **公共认证输入框补改（2026-09-16）：** 用户指出找回密码遗漏并要求复用文本框，现抽取 `TextField`，登录、注册、找回/重置密码及重发验证邮件全部接入；删除两套独立密码输入实现。统一中文校验、预留提示、ARIA、显示密码及错误清除，服务错误不标红无误字段。四项新增测试覆盖共用邮箱规则、新密码/确认密码、服务器字段投影及服务/链接失败；Web **26/26** 与 TypeScript/Vite 构建通过。浏览器确认五类页面使用统一提示；找回密码（桌面/390px）、注册和重置密码错误态按钮位移 **0px**，修复可见字段聚焦导致注册页额外滚动 56px 的问题。未填写或提交新的账号密码；密码长度/不一致分支由单元测试验证。
- **UI 稿与本地预览：** Pencil 新增可复用 TextField，原登录各状态、注册、找回及重置页面改为组件实例，补充找回密码错误态；复核无裁切。现有 loopback 预览已更新构建，找回密码正确邮箱仍返回该预览未提供邮件依赖的错误，没有伪造发送成功；本轮只处理认证输入体验，不增加真实邮件、Java/API、数据库或入驻功能，未执行共享部署、提交或推送。

## 3. 当前生成模型快照

| 系统 | 表 | 字段 | 唯一索引 | 访问索引 | 本库关系 | 1:1 | Diagram Groups |
|---|---:|---:|---:|---:|---:|---:|---:|
| Client & Contract | 38 | 413 | 60 | 39 | 62 | 6 | 6 |
| Commerce | 15 | 250 | 24 | 12 | 15 | — | 4 |
| Payment Orchestration | 27 | 345 | 38 | 44 | 63 | 7 | 7 |
| Finance | 84 | 1,109 | 145 | 155 | 160 | 22 | 6 |
| Risk & Compliance | 81 | 1,003 | 119 | 87 | 127 | 9 | 8 |
| Platform Support | 64 | 831 | 123 | 102 | 109 | 5 | 7 |

这些数字表示当前生成资产内部一致；六套模型仍保留 PDManer 4.9.3 应用内人工检查门禁，也不能证明物理数据库已经创建。Platform 当前手工 Schema 投影包含 33 表，完整逻辑模型包含 64 表；Client 本机执行与验证的逐表范围见 [Client 切片](#client-console-onboarding-20260914)，不外推至其他环境。本期新增 Scope 表及两索引已应用到本机独立 `mor_platform_config_validation`；test 也已补齐三表及索引，详见 [Client API 接入管理](#platform-client-api-access-20260913)。

## 4. 已确认但尚未全部实施

| ID | 已确认目标 | 当前缺口 | 完成判据 |
|---|---|---|---|
| GOV-01 | 主题 Owner + 单一状态台账 + 带元数据的历史 | 现行入口和用户级全局 fallback 已收敛；大量历史 Spec/Plan 的元数据只在后续触及时补齐 | 新入口只路由；当前规范关键词/链接门禁持续通过；历史文件不再被误作现行状态 |
| JAVA-01 | DataObject 在 Owner JAR 内可用；跨模块只用 Owner 的业务接口和 Domain/Application Command/Result | 中央规范与 Cursor 薄投影已传播；各业务模块的架构测试要随实现补齐 | 文档与架构测试不存在跨模块 DataObject/Repository/Mapper 依赖 |
| JAVA-02 | 所有业务 Service 和 Domain Service 使用 `interface + *Impl`；Processor/Controller 保持具体类，Repository 保持 `interface + *RepositoryImpl` | 规范、入口、Cursor 薄投影和静态门禁已传播；Platform Support 已完成本次迁移与架构扫描，其他 Java 仓库尚未完成全量形态审计与迁移 | 架构扫描确认不存在无接口的业务 Service/Domain Service，相关构建与测试通过；不得用空 Service 满足形态检查 |
| JAVA-03 | 业务模块默认无直接依赖；只允许稳定 Owner 能力通过发布接口和 Domain/Application 模型形成单向无环依赖，其他协作由 `web` Processor 编排 | 公共规范、Finance 映射和 Cursor 薄投影已传播；既有仓库尚未完成获准依赖清单、包级越权与依赖环的全量审计 | 每个复杂核心服务的 Maven 图无环，架构测试阻止未获准依赖及跨模块 `*Impl`、DataObject、Repository、Mapper 和 SQL 越权；跨 Owner 写事务由单个本地 `TransactionTemplate` 覆盖 |
| HTTP-PROBLEM-01 | 除 `HEAD` 无 Body 语义及 pre-application 传输/边缘失败外，所有第一方 JSON HTTP 4xx/5xx 统一为 RFC 9457-based Code-MoR Problem Profile：`type=about:blank`，必填 `title/detail/code/requestId/timestamp`，可选字段错误只有 `field/message`，Body 不含 `status` | Tasks 1–12 已完成 Foundation、HTTP Client、Gateway、Platform/BFF/Web、Vault、Commerce OpenAPI 和现有核心服务测试消费者的源码迁移；Nginx/Connector 顺序已实现静态门禁，变更已合入各受影响仓库的本地 `main`。为未部署 Producer 保留的显式 Legacy Readers 尚未删除；Go 工具链、Gateway 既有 13 个基线失败和 Platform 非干净本地库仍是未闭合门禁；未发布或部署 | 补齐 Go、Gateway 基线与干净 Platform 数据库门禁，发布并按消费者优先顺序部署，观察所有第一方 Producer/Consumer 已使用新 Profile，再删除 Legacy Readers 并重跑全门禁；只有实际环境验收后才更新部署状态 |
| VAULT-01 | Go Server + Java Client 是 V1 正式实现；V1 只支持三表 Protect/Reveal | 六系统文档仍依赖 Binding/Copy/Blind Index/Masked/Metadata/Retire 等旧能力 | 每个真实消费者完成 V1 适配或获批窄 V2 设计，API/模型/迁移/安全测试一致 |
| TEST-01 | Foundation 不使用 Testcontainers 或自动 Schema 初始化 | Redis Starter 已迁移为复用仓库 `local-environment` 且真实门禁通过；Compensation Event 测试仍自动重建 Schema | Compensation Event 静态门禁先失败后通过，并在手工准备的 `mor-local` 上完成受影响 Maven 测试 |
| COMP-01 | 本地可靠表使用 `event_key + event_type + sub_event_type + 可选最小 payload_json`，任何 Vault Token 禁止进入 Payload | 跨系统前序文档/模型仍有旧 source/version/schema/hash/64 KiB 契约；Payment 已对齐目标模型与代码并创建本机 Execution 相关表，见 [Execution 研发](#payment-execution-implementation)；CCT 模型及本机补偿表已切换，Client Workspace 恢复 Handler 已接通并完成本机验证，见 [Client 切片](#client-console-onboarding-20260914)；其他环境未迁移。Foundation `CompensationPayloadPolicy` 尚未显式拒绝 `vaultToken` / `*_vault_token` 标准字段名，也缺少相应反例测试 | 六套 Manifest、Policy、模型、场景、SQL 和测试按各自授权迁移并复核；Foundation 登记入口拒绝标准 Vault Token 字段并通过反例测试 |
| DB-01 | 新建表不使用物理 CHECK/FK，Sidecar 仅表达应用不变量/逻辑关系 | 多份旧物理决策与 Vault DDL 仍有现在时或实际约束 | 应用保护与反例测试齐备后，审核并手工执行逐库约束迁移；物理核验通过 |
| CCT-PRICING-01 | CCT 用全局不可复用 `payment_method_profile_code` 描述支付方式特征，并由 Term Version 下准确 OE Price 组合 Percentage、Fixed、Tiered Component/Band | 四系统目标模型及完整人工 Schema 已同步；新 Wire、Java、Payment 晚到事实、真实 Risk 接入和数据库执行仍待完成，详见 OE-CLIENT-MODEL-01 | 完成实际历史盘点及新旧 Reader 隔离；实现各 Owner HTTP/MQ、准备恢复与定价；另行接入 Risk，在授权环境迁移并通过真实数据库与端到端验证后关闭 |
| FIN-01 | Tax Payment 事前承诺门禁与已发生银行扣款的事后事实登记分离 | 专项核对已确认：现有 `Bank Item + Reconciliation Exception` 可保存并追踪已发生扣款，但当前模型不能无损地把“没有前置 Tax Payment 的真实扣款”闭合到 Return/Payable | 正常流、无前置记录真实扣款、重复、金额/归属不匹配和解决流程均有模型与测试 |
| RUN-01 | Gateway 本地 Nacos 默认 Namespace 与 `mor-local` 一致 | Gateway 当前默认/示例仍为 `public` | 失败断言、配置修正和相关 Gateway 门禁通过 |
| RISK-OPERATIONS-RULE-CONFIG-01 | `CHECKOUT_PREPARE` 配置后端、BFF 适配与 V1 页面已实现；本次增加动态主体绑定、可空终态和冷却/主体标签配置、手工拒绝文案，保留不可变版本、历史 Hash、持久幂等与纯终态 TEST Candidate | 本次 Risk `clean verify` 155/155、BFF `verify` 1008/1008、Web 887/887 与生产构建、模型/Schema 189/189 通过；获授权备份后已准备本机 `mor_risk` 增量结构/目录。本次 test 环境原有 Risk 脚本 164/164、新显式验收入口 2/2 离线检查通过；三个应用已部署，`RISK_V1_CONFIGURATION_SMOKE_OK` 与 `FULL_OK` 验收通过，人工 PDManer Review 尚未完成 | 本机 test 已完成协调切换和 Gateway/Auth/PLT-I02 实际 HTTP 链验收；其他环境须单独授权和验证。冷却/标签配置不能冻结或执行，运行评估、Shadow、审批和激活须另行设计；不得用本项关闭 COMP-01、DB-01 或 Vault V1 |
| JSON-CANONICALIZATION-01 | Foundation 独立 opt-in `paytech-json-canonicalization` 已实现严格 RFC 8785 JSON Object/Number Profile；Risk 与 Commerce Web 是当前两个真实直接消费者。Risk 保持七组既有业务 Projection；Commerce 自有 Checkout 请求/恢复快照 Projection、Hash Schema 和 SHA-256 | 原 Foundation/Risk/共享 JS fixture 门禁保持；Commerce 全量 194/194 通过，JCS 请求 Hash/恢复快照已由真实随机端口 HTTP 与本地 MySQL 的创建、重放、恢复及损坏失败关闭场景执行。精确依赖边界仍要求 Client API、Common Core、Starter、Compensation Event、Gateway 和非消费者不得获得 JCS 传递依赖；BOM、Foundation Parent/JAR 仅安装到本机 Maven 仓库，未发布 Nexus、未部署 | 发布前重新执行 Build → Foundation → Consumer 顺序与逐字节向量门禁，获授权后发布明确 artifact/version；Platform、CCT、Finance 及其他未来 Owner 只在真实消费者切片中直接依赖并自有 Projection、Hash Schema、SHA/HMAC、存储和重放语义，部署后再更新本台账 |
| COMMERCE-CLIENT-API-01 | Commerce Hosted Checkout V1 的非运行 Java Owner ingress contract module 仍定义公共 OpenAPI、三接口与十操作；其中 `createCheckout/getCheckout` 已由 Commerce Web 真实同步 MVC 实现，另外八操作保持仅设计。当前结构分离 Creation、Query、Create Idempotency、Idempotency Validator、Snapshot Codec、Owner Converter、Prepared Assembler、Repository 与 `CLAIMED -> ORDER_COMMITTED -> COMPLETED` 三个 Create 恢复检查点；Java 接口复用 Foundation Header 名称，Request ID 留在 MVC 基础设施，可信身份使用正 `long`，请求通过正常 Jackson/Bean Validation 绑定并忽略未知属性 | [业务决策](./decisions/2026-09-05-commerce-client-api-v1.md)与[设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)继续拥有公共 wire 语义；[恢复简化决策](./decisions/2026-09-09-commerce-checkout-recovery-simplification.md)拥有 Create Checkout 的三个真实恢复检查点；[结构简化设计](../docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md)与[边界简化计划](../docs/superpowers/plans/2026-09-08-commerce-client-api-boundary-simplification.md)记录当前重构。当前模型/人工 SQL 为 15 表/246 字段；本地 MySQL 上全量 `clean verify` 194/194、JaCoCo 行覆盖率 90.20%，覆盖真实 HTTP Create/Get、幂等恢复、并发窗口、过期、Owner 失败/畸形合同与合法 PARTNER 来源。合同/漂移 49/49、JavaDoc、模型/Schema 24/24 和 Requirements 136/136 证据保持。四个 `*Client` 是 local/auto/test-only Commerce 端口，不是 Owner wire 合同 | 接入 Owner 发布的 `@HttpExchange` 合同和有状态协议 Simulator 后补真实 wire/时序联调。其余八操作、Funds 事件、Webhook、Gateway/Platform/Nginx 与 Consumer Checkout 页面完成端到端激活后才可声明外部可用 |
| GATEWAY-CLIENT-API-01 | Client API 使用专用域名推导的 Channel、PLT-I03 opaque Credential、Credential/Tenant Guard、exact Route Scope、外部可生成的 Request ID 与最小三 Header 下游合同 | Gateway 已消费 Platform 的 reactive PLT-I03 合同并实现独立 Credential Security Chain、Client Identity、exact Scope、最小下游 Header、响应/错误边界与纵向测试；Client 专项 130/130 通过，完整 1031 项仍有与对照副本相同的 39 个失败项；详细验证与剩余前置见第 13 节。PLT-I03 API artifact 仅构建安装到本机 Maven 缓存，不等于远端发布或部署 | 完整门禁和 Platform、Owner、Nginx 前置完成后，才获授权准备生产 Approved Target 与 `DISABLED` Target/Route。首条 Route 切为 `ACTIVE` 后逐实例确认 CLIENT_API Target/Route Count `0 -> 1` 并 smoke；失败先撤销测试 Credential，再禁用 Target/Route 并等待每实例 Count 回到 0。只有成功收敛和 smoke 后才声明可用；后续同 Count 变更须先提供 Snapshot Revision/Fingerprint |
| AUDIT-01 | Platform 既有 Operations HTTP 审计由 Controller 注解声明，提交结果确认后独立事务尽力写入；允许丢失，审计故障不改变业务结果 | [正式设计](../docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md)与[实施计划](../docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md)已实现：29 个方法精确覆盖、业务保护迁移、有界异步隔离、同库独立事务及真实故障回归均通过本地门禁；本次把业务审计接口收敛为 `identify/target/record/suppressReplay`，Processor 由唯一事务执行器自动桥接事务终态并新增递归字节码门禁；首次实现与低侵入收敛已分别提交本地 `main`（`5ce9ad0`、`8b42a64`），均尚未推送或部署，网络未知提交、强制终止和物理基础设施故障不在已验证范围 | 当前实现与验证证据见第 7 节；上线前另行完成环境配置、容量评估和部署回归，不把尽力写入描述为零丢失或物理零影响 |

| COMMAND-IDEMPOTENCY-01 | Foundation 独立 opt-in `paytech-command-idempotency` 已实现服务本地 Claim、Lease、Version CAS、恢复阶段推进和稳定响应冻结；JAR 不创建事务、不计算业务 Hash/Snapshot，也不拥有中央数据库。Commerce 为首个消费者；Payment pay 已单独获准接入，当前证据见 [Payment 切片](#payment-execution-implementation) | Build BOM 已管理新 artifact；Foundation 聚焦 `-pl paytech-command-idempotency -am clean verify` 通过，其中新模块 11/11；Commerce 已移除重复的 DataObject、Mapper/XML 和 Repository，并以同一表结构接入公共 API；本地 MySQL 全量 194/194 覆盖 Claim、Lease、CAS、阶段恢复和并发提交窗口。JAR 与 BOM 只安装到本机 Maven 仓库，未发布 Nexus；Risk、Platform Support、Gateway 和其他系统未改动 | 发布前按 Build → Foundation → Commerce 重跑并发布明确版本。其他系统是否迁移必须逐系统批准，不能自动接入 |

上述条目为迁移台账，不授权跳过专项设计直接改生产模型或数据库。

## 5. 最近决策与计划

- CCT 定价模型：[`decisions/2026-09-10-cct-payment-method-profile-pricing.md`](./decisions/2026-09-10-cct-payment-method-profile-pricing.md)
- 决策：[`decisions/2026-09-06-system-wide-rfc-9457-problem-details.md`](./decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)
- 实施计划：[`docs/superpowers/plans/2026-09-06-system-wide-rfc-9457-problem-profile-implementation.md`](../docs/superpowers/plans/2026-09-06-system-wide-rfc-9457-problem-profile-implementation.md)

- 决策：[`decisions/2026-09-05-commerce-client-api-v1.md`](./decisions/2026-09-05-commerce-client-api-v1.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md`](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-commerce-client-api-v1-implementation.md`](../docs/superpowers/plans/2026-09-05-commerce-client-api-v1-implementation.md)

- 决策：[`decisions/2026-09-05-client-api-gateway-contract.md`](./decisions/2026-09-05-client-api-gateway-contract.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-client-api-gateway-design.md`](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-client-api-gateway-implementation.md`](../docs/superpowers/plans/2026-09-05-client-api-gateway-implementation.md)

- 决策：[`decisions/2026-09-05-platform-controller-best-effort-audit.md`](./decisions/2026-09-05-platform-controller-best-effort-audit.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md`](../docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md`](../docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md)

- 决策：[`decisions/2026-09-05-redis-first-resource-lock.md`](./decisions/2026-09-05-redis-first-resource-lock.md)
- Foundation 设计：[`docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md`](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)
- Foundation 执行计划：[`docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md`](../docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md)
- Platform 设计：[`docs/superpowers/specs/2026-09-05-platform-support-resource-lock-optimization-design.md`](../docs/superpowers/specs/2026-09-05-platform-support-resource-lock-optimization-design.md)
- Platform 执行计划：[`docs/superpowers/plans/2026-09-05-platform-support-resource-lock-optimization-implementation.md`](../docs/superpowers/plans/2026-09-05-platform-support-resource-lock-optimization-implementation.md)

- 决策：[`decisions/2026-09-05-risk-operations-rule-configuration-backend.md`](./decisions/2026-09-05-risk-operations-rule-configuration-backend.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`](../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md`](../docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md)

- 决策：[`decisions/2026-09-05-document-governance-and-conflict-resolution.md`](./decisions/2026-09-05-document-governance-and-conflict-resolution.md)
- 决策：[`decisions/2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)
- 决策：[`decisions/2026-09-05-business-module-collaboration-policy.md`](./decisions/2026-09-05-business-module-collaboration-policy.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-document-governance-and-norm-consolidation.md`](../docs/superpowers/plans/2026-09-05-document-governance-and-norm-consolidation.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-superpowers-service-interface-policy.md`](../docs/superpowers/plans/2026-09-05-superpowers-service-interface-policy.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-business-module-collaboration-policy.md`](../docs/superpowers/plans/2026-09-05-business-module-collaboration-policy.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-platform-support-review-refactoring.md`](../docs/superpowers/plans/2026-09-05-platform-support-review-refactoring.md)

## 6. Platform Support 审查修复验收（2026-09-05）

- 实现遵循本次[已确认设计](../docs/superpowers/specs/2026-09-05-platform-support-review-refactoring-design.md)。原 726 行 Directory 已分为人员生命周期、身份创建、角色、授权导航、审计查询和共享管理规则；HTTP 契约与 Processor 事务/资源锁不变。Owner JAR 内纯镜像删除，HTTP/Domain 转换显式留在边界；Snapshot/JWKS 同样遵守该边界。
- 路由 RED 在 2/64/65/4096 条目录、两种插入顺序均复现；人员 RED 复现非有效管理员被拒绝，以及 10 人/12 人列表分别执行 35/41 次 SQL。最终回归使用真实 200 人页面，验证查询数固定、排序/角色/COUNT 正确及缺失成员/凭据时失败关闭。登录审计另用受控变异恢复旧原因，8 个原因断言均按预期失败，恢复修复后纳入完整 GREEN。
- Platform 执行 `./mvnw -q clean verify`：145 项、0 失败/错误/跳过，JaCoCo 门禁通过。Gateway 执行 `GatewaySnapshotCompilerAutoTest,GatewaySnapshotCodeParserAutoTest,OperationsRouteDefinitionContractAutoTest`：54 项全通过；未宣称整个 Gateway 套件通过。该次验收时需求站点 `npm --prefix requirements-site test` 为 127/127；当前工作区的新结果见第 7 节。主工作区、Platform 和 Gateway 的 `git diff --check` 通过。
- 本地身份、密码、成员、授权、Session、Refresh、幂等、审计、签名 Key 的临时记录均为 0；临时 Catalog Route 为 0，测试同时比较完整 Gateway 目录的运行前后快照。只读 `SCAN` 确认 `platform-lock-test:*` 与 `gateway-management-test:*` 均无残留。专用 MySQL 测试锁已释放，另一 JVM 在持锁时不能取得、释放后可以取得。
- 完成交叉代码评审，未留下已确认的 Critical/Important 问题。验证只访问仓库自有 Loopback MySQL/Redis 和本地 HTTP；用户本次明确授权读取本地凭据，仅注入测试进程且未回显。未启动容器、应用 Schema/DML、提交、推送或部署，未访问 `mor-test`。

## 7. Platform Controller 尽力审计验收（2026-09-05）

- 四个 Operations Controller 的 29 个已批准方法使用 `@Audit` 精确声明，其中 15 个记录成功与拒绝、14 个只记录拒绝；Context、JWKS 和 Gateway Snapshot 入口明确排除。Login/Logout 与 Authorize 保留双资源分类，10 个业务幂等成功重放分支明确丢弃审计，不补发已丢事件。
- 业务侧 `OperationsAuditService` 只保留可信身份、动态目标、类型化事实和重放抑制四个语义入口；19 个事实调用点不再拼装通用属性或暴露 DataObject。三个 Processor 不依赖审计 Service/Context，固定事务画像由 `OperationsTransactionExecutor` 统一执行并在真实回调内观察终态；递归架构门禁确认该编排包只有这一个观察桥接点。
- 审计在调用终态和实际业务事务终态都确定后，才冻结不可变事件并尝试进入单 Worker 有界队列；独立 DataSource、SqlSessionFactory 和 `REQUIRES_NEW` 事务一次原子写入 Event/Attribute/Chain/Security。队列满、链锁、唯一冲突、辅助连接耗尽或认证失败时只记录固定低基数诊断并丢弃该事件，不阻塞、重试或替换原业务结果。密码失败计数和 Refresh 重用撤销均已在真实审计故障下验证提交。
- 从工作区通过凭据脱敏 Runner 执行 Platform `clean verify`：25 个 Suite、213 项、0 失败/错误/跳过；JaCoCo 指令 `20626/21073`（97.879%）、分支 `1389/1533`（90.607%）、行 `3132/3249`（96.399%），既有指令/分支 90% 门槛未降低。独立源码评审最终 PASS；主工作区和 Platform `git diff --check` 通过，本次设计/计划/决策的 17 个相对链接均可解析。
- `npm --prefix requirements-site test` 已完成构建并通过 133/133 内容测试；审计改造没有修改 Requirements Site 需求内容。
- 首次审计实现与低侵入收敛已分别提交本地 `main`（`5ce9ad0`、`8b42a64`）。该轮收敛后的真实 local MySQL/Redis `clean verify` 为 221/221，分支/指令/行覆盖率分别为 90.897%/97.951%/96.437%；资源锁改造后的当前门禁见第 8 节。根工作区设计、计划和状态文档仍只以根仓库 Git 历史为准；本次未推送或部署。未验证网络层“数据库已提交但响应丢失”、进程 kill/OOM、断电、跨主机全序、物理数据库停机或持久化重试；同 JVM、同 MySQL 的逻辑资源隔离不等于物理零影响，且该能力按设计允许丢失审计事件。

## 8. Foundation / Platform Resource Lock 验收（2026-09-06）

- Foundation `paytech-redis-starter` 提供公共 repeatable `@ResourceLock(prefix, key)`，不引入 `@LockKey`。动态表达式只接受 `#param` 或一层 `#param.field`；启动门禁扫描实际 Spring Bean 方法并编译参数/属性绑定，同时验证方法可代理、同步返回类型、唯一 Foundation Advisor/Interceptor、事务顺序、唯一 Foundation Redisson Client、Namespace、Wait/Lease 和实际代理链。存在注解但配置、表达式、Client 或代理无效时，应用在 Ready 前失败；没有注解的应用不被强制依赖 Redis。
- Platform 删除服务内 `OperationsResourceLock` / `Locks` 和生产代码中的直接 Redisson 调用，认证 7 处、管理 8 处、Gateway 2 处，共 17 处标准注解。动态入口只接收预先形成的安全 `resourceKey`；固定 Gateway Catalog 省略 `key`。Login、Refresh、密码、创建账号命令等不会把登录标识、邮箱、密码、Token、Vault Token 或原始 Idempotency-Key 写入 Redis Key。
- Redis 负责跨实例协调，数据库只保留 Bootstrap Account X、Session X、Actor Account/Member SHARE、按需 Role SHARE/X 和后台 Audit Chain X 等最终不变量保护。Gateway 只锁一个 Catalog Redis 资源，短 `SERIALIZABLE` 事务、Version CAS 与唯一幂等记录继续承担本地最终仲裁；认证慢工作移到事务外，目标账号写入以条件 DML/CAS 为主。
- 并发 RED/GREEN 另外暴露并修复两条授权竞态：同一事务内用一次数据库 `UTC_TIMESTAMP(6)` 冻结 `effectiveAt`，防止未来角色在多次查询之间生效并获得中途授权；撤销当前或未来 ACTIVE `PLATFORM_ADMIN` 绑定时按需取得管理员 Role X，再以同一 cutoff 重算当前有效管理员，避免并发撤销后变成零管理员。
- JDK 25 全量 `./mvnw -q clean verify` 复用仓库 `local-environment`，29 个 Suite、283 项全部通过，0 失败/错误/跳过。强制 Phase A JaCoCo 为指令 `22733/23231`（97.856%）、分支 `1706/1894`（90.074%）、行 `3383/3520`（96.108%）；同轮 Maven aggregate 为指令 97.756%、分支 90.244%、行 95.901%。Foundation 全量 410/410、其中 Redis Starter 120/120；Foundation 结构门禁、Platform 63 表模型检查和 Requirements Site 134/134 也通过。
- 当前实现只存在于工作区；Foundation Snapshot 仅安装到本机 Maven 仓库供 Platform 验证，没有发布、提交、推送或部署。开发适配器的固定 HMAC Key 不能证明持久化命令幂等跨生产密钥轮换仍连续；接入可轮换 KMS 前仍需批准并验证版本化查找/迁移或独立稳定派生密钥方案。

## 9. System-wide RFC 9457 Problem Profile 源码迁移状态（2026-09-06）

- **Implemented：** Tasks 1–12 已完成当前工作区现有第一方路径的源码迁移：Foundation 框架无关 Problem、严格/显式迁移 Reader 和唯一 MVC Writer；Gateway 本地 Writer 与 Owner 响应校验；Platform Owner 状态映射；Operations BFF/Web；Vault Go/OpenAPI/Java Client；Commerce OpenAPI 与 Starter 继承验证；Client Contract、Finance、Risk 的现有 HTTP 测试消费者；以及 Nginx/Connector 限额顺序。无生产引用的共享旧四字段 `ApiErrorResponse` DTO 已删除，Operations BFF 的迁移 fixture 改为直接生成旧 Wire JSON；Legacy Reader 行为不变。2026-09-08 当前请求体边界已收敛为标准 Jackson/MVC 与 Bean Validation：JSON 语法、结构和类型错误统一为 400，成功绑定后的 Body 约束错误为 422；Common Core 只提供可复用的显式-null 与严格可选时间反序列化器，不再提供业务字段集合、自定义请求体异常层级或有界 Cause 扫描。Commerce Create Checkout 仍复用唯一 MVC Writer。
- **Verified：** Build 本地安装通过；当前 Foundation Header/JSON 绑定/MVC Problem 聚焦门禁 29/29、HTTP Problem Profile 5/5；此前 DTO 清理后的 Problem 相关 Reactor 326/326 和注入仓库本地 Redis 凭据后的全量 505/505 是对应历史源码基线。Commerce 全量 194/194，含真实 MySQL、随机端口请求身份/绑定、Owner 失败投影与 Schema Runtime，JaCoCo 行覆盖率 90.20%。Operations Web 216/216 且生产构建通过；BFF 315/315；Client Contract 4/4；Finance 25/25；Vault Java Client staged tree 34/34；Risk 全量 148/148；Gateway 聚焦 128/128，完整 878 项只保留与 clean HEAD 768 项完全相同的 13 个失败用例；Platform DB-free 15/15 且源码编译通过；Requirements Site 136/136；Nginx/Gateway 静态边界 4/4，固定 Nginx 镜像语法和 Compose 配置检查通过。各数字是独立门禁，存在共享 Reactor 测试时不得相加。
- **Environment gaps：** 当前主机没有 Go/gofmt，未执行 Vault Go 编译、格式化和测试；Gateway 的 10 个失败与 3 个错误是 clean HEAD 已存在的同一组基线问题，因此未生成 JaCoCo aggregate；Platform 注入本地凭据后的完整验证发现 284 项，但本机 `mor_platform` 含部署/人工数据，与测试要求的仅初始化目录状态冲突，产生 1 个失败与 127 个错误，未获授权重置该数据库。
- **Deployment：** 2026-09-06 系统迁移已提交到各受影响仓库的本地 `main`；2026-09-08 Header/请求绑定简化已提交到 Foundation、Gateway 与 Commerce 的本地 `main`。两者都没有推送、发布或部署；既有 `mor-test` 部署只证明其历史切片。
- **Legacy cleanup：** 无生产引用的共享旧四字段 DTO 已独立删除；Foundation HTTP Client、Operations Web/BFF、Gateway 和 Vault 的显式 Legacy Reader/fixture 暂时保留。只有全部已部署第一方 Producer/Consumer 切换并完成环境观测后，才能在独立清理任务中删除旧 Decoder、旧字段解析分支和兼容测试，再重跑全量门禁。

## 10. Platform Support 第一阶段能力模块拆分（2026-09-06）

- **Implemented：** `platform-support-service` Reactor 现包含 `platform-support-api`、`platform-support-access-control`、`platform-support-audit-security`、`platform-support-web` 和 `platform-support-test`。Access Control 与 Audit/Security 是不可部署的能力 JAR；Web 仍是唯一 Spring Boot 应用、Controller/Processor 组合根以及资源锁、事务、请求审计生命周期和命令幂等基础设施 Owner。未预建空的 File/Document 或 Delivery 模块。
- **Boundaries：** Web 单向依赖两个能力模块；Access Control 只通过不可变审计事实和 `OperationsAuditService` 依赖 Audit/Security；Audit/Security 不反向依赖 Access Control。业务 DataObject、Mapper、XML 与 Repository 已迁入对应 Owner，Web 不跨模块访问 DAL。所有模块仍共用一个 Platform 数据库、主 DataSource 和主事务管理器；既有审计专用写池仍连接同一数据库并仅承担独立尽力写事务。
- **Compatibility：** `platform-support-api`、Controller 路由、JSON DTO、Problem 合同、数据库 Schema/初始化 DML、业务 SQL、Redis 资源锁 Key、Nacos 服务身份和部署单元均未改变。现有测试的业务断言保留；改动仅适配包名、Mapper 资源路径并新增 Maven DAG、Owner 和跨模块泄漏门禁。
- **Verified：** 135 项聚焦纯逻辑/结构测试通过；`./mvnw -q -DskipTests package` 通过；Web executable JAR 包含两个能力 JAR；隔离应用启动通过，认证开启的应用上下文也完成启动；Requirements Site 135/135。共享 `mor_platform` 含部署/人工数据，不满足审计 Runtime suite 的仅初始化目录前置，因此 17 项在 fixture setup 被安全阻止；数据库未重置，当前工作区不声称全量 `clean verify` 通过。
- **Deployment：** 本次拆分已提交到 `platform-support-service` 本地 `main`（`79b05fd`），尚未推送、发布或部署；影响范围限定为 Platform Support 构建与运行时类路径，不要求 Gateway、BFF、Vault 或六个核心系统同步修改。
- **Authorities：** [设计](../docs/superpowers/specs/2026-09-06-platform-support-modular-code-design.md)；[执行计划](../docs/superpowers/plans/2026-09-06-platform-support-capability-module-migration.md)。

<a id="foundation-public-token-20260916"></a>

## Public Token 公共静态工具（2026-09-16）

- **Implemented：** Common Core 新增 `PublicTokenGenerator.generate(String... mixingValues)`，内部取当前系统 UTC 秒并使用 128 位 CSPRNG，返回 30 位无前缀主体；`isValid` 提供通用格式/CRC 校验。Commerce 创建 Processor 自行拼接 `chk_`，删除原 Generator 接口与 Spring 实现，业务值对象委托公共校验。后续按用户“一次就够了”改为 Session 只生成一个 Token，删除 Context 候选列表、Session 回写和 Repository 重试；保留数据库唯一约束与旧 Token/快照读取。[范围和兼容边界](../requirements/20260916/foundation-PublicToken公共生成/technique.md)。
- **Verified：** JDK 25 Common Core `clean install` **130/130（9 Suite）**、Commerce 限定 8 类 `clean package` **92/92**，均无失败、错误或跳过。新增公共入口测试先得到缺少实现的编译失败，再通过当前时间、固定长度、旧向量、非法输入及并发生成验证；Commerce 覆盖 34 位前缀、历史快照、接口/OpenAPI、架构与真实 HTTP 参数拒绝。所有 Commerce 测试源码编译通过，未执行完整落库流程。测试报告和命令记录见[本机证据](/Users/kenny/.codex/verification/foundation-public-token-20260916)。
- **Document checks：** 新文档链接与改动空白检查通过；需求站内容/全局时序 **106/107**，唯一失败仍为已登记的 Risk 模型统计断言，本轮未修改该模型或断言。
- **单 Token 后续验证：** JDK 25 生产及全部测试源码编译通过，97 项中 **92 项通过**；另 5 项 `CheckoutCreationServiceAutoTest` 在 `requireSchema` 前置失败，未执行到业务步骤，不能视为事务回滚验收通过。测试已调整为保存原 Session Token，以及重复 Token 抛出数据库唯一冲突并回滚五类事实；最初在旧六参数 Context 上得到预期编译失败，完成签名简化后编译通过。最终 Maven `clean package` 因这 5 项数据库前置失败返回非零，不宣称完整构建通过。[本机增量证据](/Users/kenny/.codex/verification/commerce-single-token-20260916)保存日志、报告和边界；未修改数据库。
- **Pending：** Common Core 已在本机提交 00ace48，仅安装本机 Maven 仓库，未发布 Nexus、未部署。完整 Commerce 创建成功回归仍受前序本机 `consumer_country_code` 迁移前置阻挡；本轮不执行 SQL，也不修改 PDManer、Gateway 或其他系统。

<a id="decimal-money-wire-20260916"></a>

## 对外金额主单位字符串（2026-09-16）

- **Implemented：** 第一方 HTTP 的格式规则统一为币种主单位十进制字符串，USD `"10.00"` 表示 10 美元。Foundation Money JSON 按币种精度补零；有效精度排除补零，保证最大金额可无损读回。Commerce 原有 Client/Customer 转换已经使用主单位，DTO/OpenAPI 单位说明与公开金额投影回归同步补齐。Payment 创建 Intent 原已直接解析主单位金额，本轮未改 Payment 代码。第三方渠道适配、数据库值及历史 Hash 未修改。[范围与技术边界](../requirements/20260916/commerce-对外金额格式/technique.md)。
- **Verified：** JDK 25 下 Common Core `clean verify` **116/116**；Commerce 金额及 Client Checkout/Order/Refund、公共字段与 OpenAPI 合同测试 **57/57**，均无失败、错误或跳过。先运行新 Money JSON 断言，旧实现出现 8 个预期失败；最终覆盖不同币种、正负/零、主单位不缩放、最大金额往返及非法 JSON/超精度拒绝。这是公共模块与协议验证，不代表全部服务或真实支付链路回归。
- **Document checks：** Customer OpenAPI JSON、需求链接与差异空白检查通过。需求站内容及全局时序测试 **106/107**；唯一失败仍为已登记的 Risk 统计断言预期 78 表/944 字段、台账当前 81 表/1,003 字段，本轮未修改这两处数据。日志与测试报告见[本机验证记录](/Users/kenny/.codex/verification/commerce-decimal-money-20260916)。
- **公共 Money 迁移（同日后续）：** 用户确认后删除 Commerce MoneyValue，创建金额、订单查询、退款及旧创建快照直接使用公共 Money。CommerceDecimalRules 仅保留数量、正负及 DECIMAL(24,8) 范围规则，不持有币种/金额状态。公共 Money 新增 toAmountString，金额字段与 JSON Codec 共用；外部字段仍是字符串，历史快照继续去尾零。请求精度错误保留 422/VALIDATION_ERROR，持久化金额损坏仍为系统错误。
- **迁移验证：** Common Core `clean install` **116/116**，只安装本机 Maven 仓库；Commerce 限定 12 类 `clean package` **144/144**，包含 13 项真实 HTTP 绑定/金额拒绝测试、商品行计算、旧快照、接口合同与架构门禁。新增正负/数量/整行优惠/精度/溢出断言先在旧实现完成兼容基线；依赖门禁已精确加入直接使用的 Common Core。清理构建后的可执行 JAR 不含 MoneyValue.class，源码也无残留引用。[本机验证记录](/Users/kenny/.codex/verification/commerce-shared-money-20260916)。
- **Pending：** Common Core 已在本机提交 00ace48，新制品未发布 Nexus、未部署。只读复核本机仍缺少 consumer_country_code，创建成功与退款落库等完整数据库回归受前序迁移前置阻挡；本轮未执行 SQL。

## 11. Foundation RFC 8785 JSON Object Profile 本地验收（2026-09-07）

- **Implemented：** Build BOM 管理 `io.github.erdtman:java-json-canonicalization:1.1` 和 Foundation artifacts；Foundation Reactor 现有 12 个模块，其中 `paytech-json-canonicalization` 只发布 `canonicalizeObject(byte[])` 与固定安全异常。V1 严格限制为 Root Object、非负安全整数 Number、严格 UTF-8、重复属性/Unicode/大小技术门禁；它不拥有 Projection、NFC、业务 Array 排序、SHA/HMAC、Hash Schema、存储或重放语义。
- **Consumer：** `risk-decision` 与 `paytech-commerce-web` 直接 opt-in 依赖该 JAR。Risk-owned Adapter 继续负责七类 Projection、NFC、Decimal、UTC、ID 与 Array 排序；Commerce-owned Hash/快照组件负责 Checkout 请求、恢复坐标、字符串 Money/ID 和 SHA-256；Foundation 只提供底层 canonical bytes。Common Core、现有 Starter、Compensation Event、Commerce Client API、Gateway 和其他非消费者没有获得传递依赖。
- **Verified：** JDK 25 下 Build `clean verify` 和 `clean install` 通过；Foundation 全 Reactor `clean verify` 为 607/607，JCS 聚焦 `-am clean install` 为 103/103（核心 76、边界 20、合同 7）；根结构/有效 Maven 模型门禁通过；Risk 真实本地 MySQL `clean verify` 为 151/151；两个共享 JavaScript oracle 为 101/101；Requirements Site 构建并通过 135/135。七组 Risk canonical bytes、UTF-8 hex 与 SHA-256 由 Java 和 Node 共享 fixture 逐字节验证，未发生 Hash Schema 迁移。
- **Installed locally：** `com.paytech:paytech-dependencies:1.0.0-SNAPSHOT`、`com.paytech:paytech-parent:1.0.0-SNAPSHOT` 与 `com.paytech:paytech-json-canonicalization:1.0.0-SNAPSHOT` 已安装到本机 `/Users/kenny/.m3/repository`，只用于本次源码验证。实际第三方 JAR SHA-256 为 `ed12a01f28d147898312963a1f704e90290b67a61f34fa3a761f41c134f4e691`，本机解析标记为 `maven-public`，上游 POM 声明 Apache License 2.0。
- **Published / Deployed：** 没有发布 Nexus，没有部署 `mor-test`、生产或任何其他环境；本机 Maven 安装不构成制品发布或部署证据。
- **Remaining：** Commerce Webhook、Platform Webhook、CCT/Finance BCR 等未来消费者只能在各自真实纵向切片中直接依赖已发布 JAR 并增加 Owner 黄金向量；不得因为 Commerce Checkout 已接入而把依赖放进 Client API、Common Core 或 Starter。扩大 Root 或 Number 输入域必须先批准新设计、官方向量和兼容策略。
- **Authorities：** [决策](./decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md)、[设计](../docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md)、[实施计划](../docs/superpowers/plans/2026-09-07-foundation-rfc-8785-json-canonicalization.md)。

## 12. Commerce Client Checkout 本地纵向切片（2026-09-10）

- **Implemented：** `ClientCheckoutApi#createCheckout/getCheckout` 已由 Commerce Web 的同步 Spring MVC Controller 实现。当前重构把创建、查询、创建幂等、幂等 Validator、快照 Codec、Owner Command 转换和 Prepared Aggregate 组装分成独立职责；通用 Command DataObject、Mapper/XML、Repository、Claim/Lease/CAS 已迁入按需启用的 Foundation `paytech-command-idempotency`。`CreateCheckoutProcessor` 线性编排 claim/resume、authority resolution、prepared persistence、Payment binding 和 replay/expiry，持久化恢复检查点仅为 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`。Commerce 的无副作用 Validator 继续校验当前请求/winner、持久化生命周期、快照坐标和冻结结果；Commerce Domain Service 解释业务阶段并在原有调用方事务中组合 Foundation API。业务 Service、Domain Service 和 Repository 保持 `interface + *Impl`，单实现纯协作者保持具体类；纯依赖赋值构造器使用 Lombok。Create 使用独立的外部语义请求 Hash，以及尚未发布的内部 V1 `{h,p}` JCS 恢复快照；后者以 Payload canonical SHA-256 检测局部损坏（不是认证签名），并 fail closed 校验命令生命周期、命令行/快照坐标和成功回执/实际 Root。`Client + operation + Idempotency-Key` winner、Lease/CAS、应用生成 ID/Order No/三个 Token 候选、正常成功路径三个本地短事务以及 GET 的可信 Client 隔离语义保持不变；CCT、Risk、Finance、Payment 调用仍全部位于本地数据库事务之外。
- **Persistence / model：** Commerce 设计资产已推进为 15 表/246 字段/24 唯一索引/12 访问索引/15 本库关系/4 图，完整人工 DDL 与单一初始化 DML 已生成。应用保持 `spring.sql.init.mode=never`；经用户授权，当前开发机的仓库 `local-environment` 已人工应用 DDL/DML，并固定 Loopback `commerce_app` 测试密码。该本地准备不构成共享环境迁移或部署。
- **Integration boundary：** `CctClient`、`RiskClient`、`FinanceClient`、`PaymentClient` 当前是 Commerce-owned 最小端口。确定性 Local Mock 只允许在 active profile 全属于 `local|auto|test` 且显式 `commerce.integrations.mode=local-mock` 时装配；默认 `disabled` 失败关闭。它们不构成 Owner `@HttpExchange` wire 合同、生产降级或路由启用证据。
- **HTTP / errors / tests：** 两条私有应用路径已实现，OpenAPI 公共路径仍等待 Gateway 映射。Foundation MVC 独占 Request ID 校验/生成/MDC/回写；Commerce API 只接收两个正 `long` 可信身份 Header，并在一个 Web 边界拒绝缺失或重复值。三个 POST 的幂等 Key 仍由单一 Commerce Filter 校验。请求 DTO 使用正常 Jackson 属性绑定，忽略未知属性，拒绝 scalar 到 String 的隐式转换，并以通用反序列化器区分缺失与显式 `null`；结构/类型错误为 400，成功绑定后的 Body 约束错误为 422。Client API 十操作、参数、Request/Response 字段和非显然枚举已同步补齐 JavaDoc/OpenAPI 描述。Test module 现以唯一 `TestApplication` 组合生产应用；随机端口 HTTP 自动测试统一继承 `MockTestBase`，未来人工联调统一继承 `ManualTestBase`，Schema Runtime 保持无 Web 例外。auto/manual 配置固定仓库本地 MySQL、提交且允许环境覆盖的 Loopback 测试密码、禁止 SQL 初始化并显式关闭 Nacos；初始切片未引入 Redis/RabbitMQ/Nacos 消费者；后续 CCT 的 Nacos 接法与验证以 COM-PRECHECK-01 为准。Checkout 业务行为测试全部从 `RestTestClient` 进入真实 Controller → Processor → Commerce Domain Service → Foundation Command Idempotency Mapper → Owner 端口，并联合断言数据库事实；不再直接测试 Commerce Processor、Domain Service 或 Hasher。默认 Owner 行为委托生产确定性 Local Mock，测试 Mocker 只在 HTTP 场景中控制失败、协议畸形和并发窗口。Checkout HTTP 场景覆盖创建/查询/校验、语义幂等、Lease 恢复、同一 `clientOrderId` 并发提交窗口、Token 冲突/回滚、快照与生命周期 fail-closed、过期、Owner 失败投影、合法 PARTNER 来源和已提交 Payment Binding 恢复。
- **Verified：** `./mvnw clean verify` 完整通过，194 项测试，0 失败、0 错误、0 跳过；真实本地 MySQL Repository、Schema Runtime 与所有随机端口 HTTP Suite 均执行。JaCoCo Hosted Checkout aggregate 行覆盖率 90.20%（1831/2030），通过 90% 门槛；没有降低阈值或排除生产代码。此前 Foundation JAR 11/11、Client API contract/drift 49/49、JavaDoc、模型/Schema Node 24/24、生成器 `--check` 和 Requirements Site 136/136/lint 证据保持。
- **Remaining：** 真实 Owner `@HttpExchange` 合同、有状态 Wire Simulator 和跨服务联调仍未实现；当前 Mocker 只验证 Commerce 对端口结果的处理，不证明网络协议或真实 Owner 时序。另八个 Client API 操作、Payment/Funds 事件、Webhook、Gateway/Platform/Nginx、`checkout-web /c/{token}`、发布与部署仍未完成。
- **Delivery state：** 本切片基础实现、恢复简化、Command Idempotency 迁移与测试基础设施重构已提交到各本地 `main`；最新相关提交包括 Build `e914193`、Foundation `b2f146f`、Commerce `6c01860` 和此前根文档 `8326266`。所有相关改动均未推送、发布或部署。
- **Authorities：** [业务决策](./decisions/2026-09-05-commerce-client-api-v1.md)、[原始设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)、[恢复简化决策](./decisions/2026-09-09-commerce-checkout-recovery-simplification.md)、[Foundation Command Idempotency 决策](./decisions/2026-09-09-foundation-command-idempotency.md)、[结构简化设计](../docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md)、[结构简化实施计划](../docs/superpowers/plans/2026-09-08-commerce-client-checkout-structure-simplification.md)、[本地纵向切片计划](../docs/superpowers/plans/2026-09-07-commerce-client-checkout-local-mock-slice.md)。

## 13. Client API Gateway Credential 链本地实现（2026-09-08）

- **Implemented：** Gateway 复用 `platform-support-api` 的 `ApiCredentialValidationApi` / `ValidatedCredentialResponseBody` / `ApiCredentialConstraints`，调用私有 `POST /platform-support/api-credentials/v1/validate`。独立无 Session Security Chain 只接受单值 opaque Bearer Credential，调用时去掉 Bearer 前缀放入内部 `X-Platform-Api-Credential`，成功后建立不可变 `ClientApiIdentity`。认证无缓存、无重试、无 Redirect、无 JWT/匿名降级；请求不要求 HMAC 签名、应用层加密或 Client timestamp。
- **Runtime boundary：** Shared/IP Guard → Credential 验证 → Credential/Tenant Guard → exact Scope → Body budget → 空白 Header 重建及两个可信身份 Header；Request ID 延续已有外部可生成合同。缺失或错误 typed identity 在 Guard、Scope 和下游注入阶段都 Fail-Closed；Owner 只接收三项可信 Header 与 Route allowlist 业务 Header。Client 固定响应 Header、受限 `201 Location`、严格 Owner Problem、无 Operations CORS，以及身份摘要日志已接通。Operations JWT / Cookie / 响应合同保留。
- **Dependency / public docs：** Platform 已提交的 PLT-I03 API 模块执行 `./mvnw -q -pl platform-support-api -am -DskipTests install` 并安装到本机 Maven 缓存；未修改 Platform 业务代码，未向 Nexus 发布。公共 [设计第 8.1 节](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)补齐 Method/Path/Header、Owner 类型、字段/集合上限、响应预算与失败映射；Gateway README 补齐调用链和配置说明。
- **Verified：** 当前 `clean verify` 执行 1031 项，Client / PLT-I03 相关 130/130 通过，另两组 Operations 响应/Cookie 回归 41/41 通过。纵向路径使用真实随机端口 Gateway、生产 typed HTTP Client、共享本地 Redis、Platform/Owner HTTP Simulator，覆盖错误凭据、错误/超限/超时/断连响应、Scope、伪造 Header、Body 限制、取消/超时及租约释放。边界静态脚本通过；边界脚本自身 11/11，Requirements Site 构建及 136/136 通过。
- **Full-suite failure comparison：** 完整门禁剩余 36 failures + 3 errors。临时对照源码以 Gateway `996906f` 为底，叠加工作开始前已有的 Risk 测试、Snapshot Fixture、BFF Simulator/Journal、Approved Target 根及 65536 Body ceiling，未包含本次 Client 实现；对照执行 934 项，失败测试名称/参数集合与当前 1031 项完全相同，无新增或消失的失败项。39 项由既有 13 项 transport / health / JWKS / dynamic routing / error contract 与工作区 Risk Suite 的 26 项组成；未在本次越界修复或回滚它们，不能声明完整 Gateway 门禁通过。
- **Coverage：** 完整执行后复跑 Client/Operations 171 项并生成非空 JaCoCo aggregate；专项 Maven 返回 0，不代表完整门禁通过。当前分支覆盖率 78.705212%（1933/2456）低于 90%，报告完整性检查还报缺少编译器生成的 `GatewayGuardProfileCompiler$1`；对照副本同样报告该类缺失，分支覆盖率为 77.919128%（1715/2201）。未放宽门槛、排除生产代码或把报告生成成功视为覆盖率通过。
- **Remaining：** Platform 当前“关联 Tenant 行缺失”按损坏数据返回 503，与公共设计“没有同一 ACTIVE CLIENT Tenant 属于无效认证”的 401 表述仍需 Platform Owner 确认；Gateway 保持依赖失败 503，不猜测错误响应内的业务身份。真实 Provider 部署/服务发现、Credential/Scope SQL 和生命周期、Channel-aware 控制面、真实 Owner Adapter/Approved Target 与 Nginx 域名入口仍需上线前联合验证，本轮 Simulator 成功不代表这些前置已完成。
- **Commit verification：** 仅包含本次 Client 变更的提交树单独导出复验，Client / PLT-I03 与 Operations 响应/Cookie 专项 171/171 通过，边界脚本通过；未夹带工作开始前已有的 Risk 代码、测试、Target 根或 Body ceiling 调整。上述 1031 项完整结果来自包含这些既有未提交变更的工作区，不是纯 Client 提交树的全量结果。
- **Delivery state：** Gateway Client 实现已提交到 `api-gateway` 本地 `main`（`4577ee9`），公共合同和本节台账随配套文档提交沉淀；其他任务的未提交修改保留。未推送、发布或部署；未添加生产 Client Target、未激活 Route、未应用 SQL、未修改 Nginx。完成完整门禁并获准执行跨系统准备后，才进入 Client Route rollout。

## 14. Gateway auto 测试启动与 YAML 配置调整（2026-09-09 更新）

- **Implemented in working tree：** 固定 Redis、Platform 客户端、测试身份、服务端口与发现/调度配置集中到 `application-auto.yml`；Java 不再直接读取 `PAYTECH_REDIS_PASSWORD` 或静态创建默认 Redis 连接。Redis 前置检查和按 Namespace 清理复用 Gateway 应用的 Spring 连接工厂，按测试类管理 Sentinel、不销毁应用连接。隔离故障 Gateway 保留唯一 `auto` Profile，通过 YAML 场景参数和动态关闭端口验证失败关闭；重复的手工连接故障测试收敛到现有真实 HTTP 故障 Suite。
- **Local credential fix：** 已复现样例密码导致的 `WRONGPASS`，随后增加 `application-auto.yml` 对 Git 忽略的 `application-auto.local.yml` 的可选导入；固定配置仍由 Spring 加载，环境变量显式覆盖优先于本机 YAML。经核对现有 `mor-local` Redis 的容器身份及 `127.0.0.1:16379` 绑定，本次修复复用其现有凭据创建权限为 `0600` 的本机文件，不回显凭据、不读取 `.env.local`、不修改 Redis 密码/数据或启停容器。测试自身不查询 Docker 或解析环境文件；隔离故障场景仍只使用唯一 `auto` Profile 和 YAML 中的故障凭据。
- **Verified：** 显式移除 `PAYTECH_REDIS_PASSWORD` 后，以 JDK 25/Maven 执行 20 个 Suite、249/249 项，0 failures/errors/skipped。覆盖 YAML 默认值/本机导入/覆盖优先级/故障隔离，真实 Redis 前置检查、应用连接工厂复用、Sentinel 清理、Token Bucket/Concurrency Lease，以及 Client API/PLT-I03 和 Operations 响应/Cookie 回归。Gateway 边界脚本通过，脚本测试 11/11，Requirements Site 136/136；本机凭据未进入 tracked diff 或本次验证日志。
- **Remaining verification：** 本次解决本机 Redis 凭据阻塞，并完成上述相关回归；未重新执行全量 `clean verify` 或 JaCoCo 门禁。第 13 节的旧工作区全量失败与覆盖率记录仍是历史证据，不据此宣称最新工作区全量通过。IDE 使用同一测试资源导入机制，但本轮实际执行器为 Maven，未通过 IDEA UI 运行。
- **Commit verification：** 仅包含本次测试配置修复的暂存树单独导出，以同一 Git 忽略的本机 YAML 验证，20 个 Suite、249/249 项及边界静态脚本通过；未夹带工作区 Risk 测试、Snapshot Fixture、BFF Simulator、Approved Target 或 Body ceiling 变更。凭据文件及其内容均未进入提交。
- **Delivery / authority：** Gateway 调整已提交到 `api-gateway` 本地 `main`（`de05227`），公共规则和本节台账随配套文档提交沉淀；未推送或部署，未更改 Gateway 生产业务链或其他服务代码。公共规则依 [2026-09-08 决策](./decisions/2026-09-08-middleware-auto-test-configuration.md) 同步到 `UNIT_TEST_GUIDE.md`、本地环境说明及 Java 测试规则投影，其他系统未在本轮批量迁移。

## 15. Operations Risk V1 配置增量（2026-09-11）

- **Implemented：** 按 [RCO-14 决策](./decisions/2026-09-11-risk-rule-v1-configuration.md)与[需求/接口合同](./plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)，修改 Risk、Operations Console BFF 和原 V1 Web。扩展 Authoring Context、Save、Detail 三个已有接口；新增 API、Platform 权限/路由注册 DML 均为 0，九条 Route、五权限、Save 64 KiB 与其他命令 16 KiB 边界保留。BFF 继续先 PLT-I02 后单次 Owner 调用。
- **配置能力：** 支持零或一个终态及最多 16 个附加动作；冷却保存 1..31536000000 毫秒字符串，标签最多 32 项并按代码去重排序；拒绝代码/文案手工输入。主体由 Risk 工程目录提供，Web/BFF 动态消费；本 Point 当前只有 `CURRENT_CLIENT_V1`，没有伪造 Consumer 输入。附加动作标记 CONFIGURATION_ONLY、无 Handler 证据，Freeze 在锁后闭包内拒绝；纯终态继续支持冻结和 TEST Candidate。
- **历史兼容：** 新格式以持久化 `content_format` 选择三个配置 Hash Schema；原七组 Golden 保留。旧消息 null、旧非拒绝原因代码与旧 Hash 不补造、不重写；新 DENY 完整校验在历史成功命令重放之后执行。
- **Verified：** JDK 25 下 Risk `./mvnw -q clean verify` 155/155，BFF `./mvnw -q verify` 1008/1008，均 0 failure/error/skipped。Risk 使用仓库 `local-environment` 的真实 `127.0.0.1:13306/mor_risk` 和随机 HTTP 端口，覆盖保存/回读/重放、14 类错误配置、新纯终态 Freeze/Candidate、附加动作冻结拒绝及运行事实计数不变。BFF 通过协议 Simulator 核对新字段逐项传播、授权拒绝后 Risk 零调用、动态目录和 Handler 字段隔离；这不是跨进程 Gateway 全链路证明。
- **前端/模型：** Web `npm test` 883/883、TypeScript/Vite 生产构建通过；使用生产编辑器与合成目录完成本地浏览器选中/折叠、配置保留、动态主体选择和布局检查，未见控制台 error；临时预览已清理。根 `node --test scripts/pdmaner/risk-*.test.mjs` 189/189 通过，模型/Schema 为 71 表、865 字段，未增加物理 CHECK/FK。
- **本机 SQL：** 用户明确授权“允许备份后应用本机 mor_risk”。先将五张受影响表备份到 `/Users/kenny/.codex/backups/risk-rule-v1-20260911/mor_risk-affected-tables.sql`（目录 0700、文件 0600），再人工执行 [2026-09-11 增量 SQL](../paytech/risk-decision/docs/database/sql/2026-09-11-risk-rule-v1-configuration.sql)。增加 3 列、调整动作元数据/注释并插入 2 个动作版本和 4 个参数定义；迁移前后原 Rule Set/Point Hash 及全部命令幂等记录摘要一致。新列位置与生成 Schema 已核对；空库初始化仍由单一初始化 DML 文件拥有。
- **mor-test 部署（后续明确授权）：** 用户授权部署，包含备份、test SQL 更新和合成数据验收。已核对当前 Docker context、Compose/MySQL 身份和部署前检查；预先构建并保留原镜像，备份原 Resource 后隐藏六个风控 Resource，停止旧 Web/BFF/Risk，在 test Risk 零连接时备份完整库并应用增量，再按 Risk → BFF → Web 依次替换并健康验证。最后恢复 `RESOURCES_EXPOSED`；九条 Route 保持原状态和版本，其余八个容器的 ID/image 均与操作前相同，未重建基础设施或删除 Volume。
- **test 备份/迁移证据：** `/Users/kenny/.codex/backups/risk-rule-v1-mor-test-20260911/` 包含完整 Risk SQL 备份、原 Platform Resource 表备份、本次增量副本、列/历史 Hash/幂等摘要核验和应用 image ID 清单；目录 0700、文件 0600。数据库是 `mor-test-mysql-1` 的 `mor_risk`（独立宿主端口 23306），与先前开发库 13306 分开；目录检查返回 `PREPARED`，旧 Rule Set/Point Hash 和所有旧幂等记录在迁移前后保持一致。
- **test 验收：** `risk_rule_v1_configuration_smoke.py --verify` 返回 `RISK_V1_CONFIGURATION_SMOKE_OK`，验证动态主体目录、Handler 隔离、冷却/标签保存、去重与同键重放、新版本保存与旧版退役、Consumer 绑定拒绝、手工拒绝文案及 409、附加动作 Freeze 400 与纯终态冻结；运行事实计数无变化。既有 `risk_rule_configuration_smoke.py --full` 返回 `FULL_OK`，覆盖五项权限分离与恢复、九路读写链、TEST Candidate、64 KiB 成功/超限拒绝和唯一 Nacos 实例。临时合成 IAM 夹具通过显式 cleanup 清理；不可变合成规则保留为验收证据。公开 `/risk/rule-configuration` 的 HTML 和两个 JS/CSS 资源与本次 Web 构建逐字节一致。通用 `smoke.py` 在既有限流窗口恢复后重试通过，覆盖登录、分页、导航/角色、Gateway 控制面查询、错误文案、Refresh 轮换和退出后的撤销；未调整限流配置。
- **V1 样式补齐（用户截图反馈后）：** 首次配置增量交付保留了旧表单布局，未完整落地设计稿。现已在原页面补齐可收起的左侧规则列表、选中规则编辑区、紧凑条件行/嵌套组、决策单选、手工拒绝面板、附加动作卡片和时间单位、基本信息弹窗与保存状态；空规则集同样使用左右分栏。Web 回归 887/887、TypeScript/Vite 构建通过；真实浏览器合成数据验收覆盖切换保留内容、收起/展开、动态主体、拒绝文案、冷却单位、标签、排序与完整保存 Payload，旧 NOT/RIGHT/精确 Decimal 保持，390px 无横向溢出且浏览器 error 为零。仅替换 test Web 容器，另十个容器的 ID/image 保持，13 个静态资源均与构建逐字节一致。通过 test 公开入口实际登录，验证新建空规则集、添加规则、REJECT/冷却编辑与放弃修改后退出；未提交规则命令。Requirements Site 构建及 136/136 通过；未新增 SQL 或后端改动。
- **精确镜像：** Risk `sha256:55e5a50e17a3`、BFF `sha256:ca802c008aa4`、当前 Web `sha256:b9f304f73e71`（此前缀只用于可读摘要）。首次部署完整 image ID 在上述 `application-images.json`，样式修正的前后容器和完整 Web image ID 在同目录 `web-style-deployment.json`；原 Web 镜像另保留 `before-risk-v1-style-20260911` 标签。部署只使用本机 Maven 缓存与本地镜像，未发布 Nexus 或推送；本地提交状态见下文。
- **本地提交：** Risk `d609e04`、Operations BFF `4b64b96`、Operations Web `7e8036c` 已按用户要求提交到各仓库当前分支，未推送；配套文档、模型和验收脚本随根仓库本次提交。提交前模型门禁 189/189、Risk 离线验收脚本测试 166/166 再次通过。
- **范围：** 本次只证明本机 `mor-test` 的规则配置可用，不是生产部署。实际评估、冷却/打标签、标签查询、拒绝字典、Shadow、审批、激活仍不在本期范围；含附加动作的版本禁止冻结。PDManer 应用内人工 Review、其他环境 SQL 和生产验收仍独立保留。


## 16. Operations 规则集列表优化（2026-09-11）

- **范围与阶段：** [本需求](../requirements/20260911/operations-console-风控规则集列表优化/requirement.md)及[已批准技术方案](../requirements/20260911/operations-console-风控规则集列表优化/technique.md)用户明确“发布”后，阶段 7 本机 mor-test 部署与真实入口验收已完成，用户随后明确“先git提交一下”，本地提交与归档见下述阶段 8。[交付记录](../requirements/20260911/operations-console-风控规则集列表优化/delivery.md)关联 AC、代码与证据。
- **Implemented in working tree：** Risk 新增管理根，按根及当前配置查询/分页；整集删除保留编码和全部历史，任一历史版本被当前 ACTIVE Release 使用即阻止删除。保存/冻结/删除/新 Candidate 复用 Redis 资源锁及本地事务、引用校验和 CAS。BFF 新增独立删除权限与一次 Owner 转发。Web 精确编码校验、精简列表、详情/编辑/红色删除菜单、真实 URL 历史及未保存保护，隐藏 Candidate 页面。API 不升级版本，25 个 DTO 类型去协议版本后缀。Platform 单一初始化 DML 同步 16 行注册数据，初始化文件继续保持 Route/按钮 DISABLED 默认值；test 已通过本轮受控 SQL 开放。Gateway 无本轮代码修改。
- **Foundation 必要修复：** 经用户单独同意，将 `paytech-redis-starter` 的 MeterRegistry 获取延迟到首次记录指标，避免 Advisor 提前初始化破坏 JVM 监控。原监控构造器及资源锁合同保留；本机 Maven 安装只用于 Risk/BFF 集成验证，未远程发布。
- **Verified：** JDK 25 下 Risk 与 BFF `./mvnw -q clean verify` 均退出 0，分别 162/162、1024/1024，0 failures/errors/skipped；Risk 后续本地 Redis 前置检查也通过删除 HTTP 聚焦回归。Foundation `-pl paytech-redis-starter -am test` 的三个相关模块合计 235/235（其中 Redis Starter 121），不称全 Foundation 验证。Web 876/876、TypeScript/Vite 生产构建通过。Risk 全模型/Schema/场景/Hash 门禁 190/190、Requirements Site 构建与内容测试 136/136 通过；API clean JAR 包含 25 个新 DTO 名且无旧类型残留。详细命令见[验证摘要](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage4-verification.json)。
- **浏览器及清理：** 生产 Web 组件配合成 HTTP 的 11 个场景通过，覆盖键盘/悬停菜单、删除取消/同键重试、过滤与分页的 Back/Forward、未保存保护及 Candidate 旧 URL，0 JavaScript errors；不是新功能 Gateway 全链路或 test 环境验收。临时预览已关闭；local Root/Version/Release/Command 与 `risk-auto:*` 均无测试残留，三个终态 Action 恢复 ACTIVE。
- **local SQL：** 用户单独确认备份后应用 `127.0.0.1:13306/mor_risk`。2026-09-11 11:08:20 UTC，核对历史为空、无其他业务连接及 Risk 服务停写后备份原 71 表，再应用本需求 Risk DDL/DML，manifest 为 `[]`；新增 `t_rule_set` 12 列、0 行回填。原 71 表 schema/data dump 与执行前逐字节一致。[执行证据](../requirements/20260911/operations-console-风控规则集列表优化/assets/local-sql-applied.json)记录文件与备份 SHA-256；备份在 `~/Code-MoR-backups/risk-rule-set-list-local-20260911T110820Z/mor_risk-before.sql`。
- **边界：** 本轮仅部署本机 mor-test，阶段 8 已获本地提交授权；未推送、远程发布制品或操作生产；第 15 节的镜像为上一需求发布时的历史证据，当前三应用镜像以下述阶段 7 为准。
- **范围限制：** 非空历史回填、真实 Gateway/Platform 删除路由及权限已在阶段 7 核验；实际规则评估、动作执行、Shadow、审批和激活继续不在本期范围。后续阶段段落中“未执行”均指该阶段结束时。

- **阶段 5 独立验收：** 复核业务/接口/并发边界，发现删除确认框缺少名称并修复；本阶段产品代码仅改 Web 确认状态和弹窗。真实 BFF → Risk 独立进程配本地 MySQL/Redis 完成 38 个 HTTP 检查，覆盖当前配置单行列表、权限、实际使用保护、过期删除、同键并发、永久编码及成功历史回放；Platform 为现有 Simulator，并非真实 Gateway/Platform 权限环境验收。
- **阶段 5 页面及监控：** 修复后 Web 876 项与生产构建通过；11 个合成接口浏览器场景、5 个真实业务接口浏览器场景（25 次 API 调用）通过，0 JavaScript errors，390px 页面稳定后无横向溢出。真实打包 Risk 的 JVM 内存及资源锁指标均存在。后端及模型源码未改，相关全量门禁沿用上述阶段 4 结果。详情见[综合验收](../requirements/20260911/operations-console-风控规则集列表优化/acceptance.md)和[阶段 5 验证摘要](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage5-verification.json)。
- **阶段 5 清理及动作边界：** 7 组合成规则及相关版本/历史/幂等数据已按所属身份清理，临时 Web/BFF/Risk 服务已关闭；数据库和 Redis 最终只读核对见验证摘要。本阶段未再应用 SQL，未写 Platform/test，未发布制品、部署、提交或推送；第 15 节部署事实不变。

- **阶段 6 准备：** 只读核对 mor-test 仍为 23 个版本/21 个规则集、零 ACTIVE Release、原 36 Route/21 Resource。已生成 21 条 test 专用 Snowflake manifest，核对 71 个 BIGINT 主键列无冲突，DML 同构只读投影为 21 个有效根；实际 INSERT 未执行。准备精确维护/路由开放/资源开放/失败关闭 SQL，未应用。发布方案和恢复限制见[发布包](../requirements/20260911/operations-console-风控规则集列表优化/release.md)。
- **阶段 6 制品与兼容：** 已构建本地专用 Risk `46aff45920d3`、BFF `59be81991657`、Web `83a5141710c3`；完整 ID、JAR/文件 SHA 及验证边界见[机器可读清单](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage6-release-package.json)。Risk test Compose 补齐现有 Redis 地址/凭据注入及健康依赖，运行配置尚未切换。当前部署的 Platform/Gateway 编译器离线接受新 37 条目录，Gateway 接受维护态 27 条目录；不需替换这两个服务。旧通用 smoke 的 36/21 分类不适用于本次最终 37/22，阶段 7 按发布包做精确实际入口验收。

- **阶段 6 检查与停点：** 部署脚本离线测试 31/31、Requirements Site 构建/内容测试 136/136、跨仓库 diff/链接/SQL 校验通过。最终只读核对两库元数据及 11 个容器完整 ID/启动时间不变，development 镜像标签未切换，Gateway 仍为旧 36 条目录；本阶段没有应用 SQL、部署或提交。待用户按发布包授权阶段 7。

- **阶段 7 Deployed：** 用户明确“发布”，本机 `desktop-linux/mor-test` 完成两库备份及受控 SQL、Risk → BFF → Web 协调升级。当前镜像前缀为 Risk `46aff45920d3`、BFF `59be81991657`、Web `83a5141710c3`，三个容器 healthy，其他八个容器 ID/image 不变。完整记录见[部署验收报告](../requirements/20260911/operations-console-风控规则集列表优化/deployment.md)与[部署证据](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage7-deployment.json)。
- **阶段 7 SQL/入口：** 备份位于 `~/Code-MoR-backups/risk-rule-set-list-test-20260911T131033Z/`，完整 Risk/Platform SQL 已核验大小、结尾和 SHA-256。新增管理根并回填 21 集，迁移前后原 71 表 schema/data dump 字节一致。Gateway 接受精确 37 Route、22 ACTIVE Operations Resource，`SUCCESS/NONE`、无 Last Valid fallback。唯一健康 Risk 实例，JVM 内存/ResourceLock 指标、公开 HTML/JS/CSS 与新制品均通过核验。
- **阶段 7 真实验收/清理：** 23 项 HTTP 核对及浏览器查询、菜单、详情、直接编辑、Back/Forward、未保存保护、整集删除、旧 Candidate URL、390px 布局核验完成，0 JavaScript errors。快速浏览器导航触发现有读取 429 后，等待限流恢复核对已保存版本并继续完成，未重复保存或改限流；不宣称单次连续脚本全绿。原 21 根/23 版本及 9 Candidate 状态不变，Evaluation/Action Effect 仍 0；仅新增两组各两版合成规则并以 API 整体删除，保留历史。只读账号真实 DELETE 403 后退出并用 API 删除（CLOSED）。详见[最终核验](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage7-final-verification.json)。
- **阶段 7 交付停点：** 发布锁已正常释放，临时容器探针已移除；未提交或推送。旧 smoke 的 36/21 分类仍需后续维护，本轮按已批准的精确 37/22 目录和真实入口完成验收。下一步须由用户按[待提交清单](../requirements/20260911/operations-console-风控规则集列表优化/commit-plan.md)确认阶段 8；只确认 test 验收不自动提交。

- **阶段 8 本地提交与归档：** 用户明确“先git提交一下”。paytech-foundation `6f2d692997e2`、risk-compliance-service `d9f0c1c633a8`、operations-console-bff `2e0e8d707db0`、operations-console-web `bf607aaaa961`、platform-support-service `ef6570f71833`，根仓库为[本归档记录](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage8-commits.json)所在提交。均在原 main 分支，本轮未推送、创建 PR/分支或重新部署。四份 Risk 选择性提交源码经 JDK 25 编译，7 个 class 去除调试元数据后与已部署制品一致；其他组件源码与阶段 7 清单一致。详情见[提交归档](../requirements/20260911/operations-console-风控规则集列表优化/completion.md)。
- **阶段 8 保留项：** 原有公共流程/目录治理、其他需求材料以及 Risk 的 import/格式整理保持未提交，未覆盖工作区源码。冻结入口、人员展示、详情文案、限流体验、名称搜索/复制等讨论仅为后续建议，没有混入本轮实现。等待用户确认本需求结束。

## 17. Operations 规则历史与业务字典（2026-09-11）

- **当前状态 / 用户验收确认（2026-09-12）：** 本期功能及 Web 反馈调整已部署 test；用户明确“我验收了，代码提交吧”，确认验收并授权本地提交。以下保留阶段证据，旧“等待/未部署”状态只代表当时检查点；当前提交归档见 [completion.md](../requirements/20260911/operations-console-风控规则集历史与字典管理/completion.md)。不涉及推送、生产或再次部署。

- **Stage 5 verified / Stage 6 paused for Gateway design amendment：** 用户回复“继续”，确认[精简后的设计包](../requirements/20260911/operations-console-风控规则集历史与字典管理/technique.md)并授权研发。保留两张字典主表，移除字典回执与标签主体关系；默认 v1、类命名不加版本。确认过程见[交付记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/delivery.md)。
- **Source implemented / development regression passed：** Risk/BFF 的 14 个新增接口及原规则保存校验、Web 只读详情/历史/引用图/两类字典与受控选择已落到源码。字典按代码和 CAS 维护当前值，未知写入结果由人工查询确认；原命令幂等和回放不变。未新增运行时决策或实际冷却/打标签能力。
- **Model / registration artifacts：** Risk 模型为 74 表/898 字段，新增 2 表/21 列、2 个反查索引和 2 处旧字段注释；完整 Schema 与增量对齐。Platform 初始化 SQL 同步 182 条插入及一次管理员角色更新，新 Route/Resource 均 DISABLED；静态等价检查通过。Risk 本机 DDL 已按本轮授权备份后应用；Platform 注册 SQL 未应用。
- **Verified：** Risk 最终空基线全量 **186/186**（27 类），BFF **1040/1040**，Web **883/883** 及生产构建通过，Platform Catalog **21/21** 通过；均无失败/错误/跳过。浏览器 **26 个场景**（22 个真实 BFF/Risk 场景 / 84 次 API 请求，另 4 个合成只读 HTTP 焦点场景）、0 JavaScript errors，含 1360/1024/390 px、历史/引用导航、字典未知结果、权限及错误态；Platform/登录/导航/Gateway 头为测试夹具，未替代真实入口验收。[阶段 5 报告](../requirements/20260911/operations-console-风控规则集历史与字典管理/acceptance.md)。
- **Local SQL applied / not deployed：** 用户回复“允许”后，本轮 DDL 已应用至本机 127.0.0.1:13306/mor_risk，2 张新表/21 列/2 反查索引及 2 处注释已按模型核对，受影响旧表行数不变；[备份与执行记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/local-sql-application.json)。Platform 数据库、test、部署、制品发布、Git 提交和推送均未操作。Risk API 仅 local Maven install 供本机 BFF 编译。设计画板和本次源码不是 test 页面已更新的证据。

- **Stage 5 corrections / cleanup：** 修复 Web 历史详情 `size=1`、字典确认离开后抽屉残留、历史页返回列表、菜单/抽屉键盘焦点。两类字典四种确定锁顺序、旧空消息和复制规则/动作等兼容已真实验证。临时三服务已停止，本轮 2 个规则集/13 个版本及字典/命令夹具精确清理，相关 9 表空、无其他 Risk 连接；首次受浏览器夹具影响的全量失败在空基线重跑后通过。阶段 6 已获用户确认；阶段 7–8 未开始。

- **Stage 6 historical gate / next preparation：** 原 mor-test 只读基线为 72 表、23 个 DELETED 规则集/27 个版本、无 ACTIVE Release，现有 37 Route/22 Resource；新增字典/182 行目录尚未应用。实际部署 Platform 编译器接受 51 路由，旧 Gateway 拒绝字典下游路径；内存增补路径可通过。随后用户确认完整数据库动态路由和 BFF 管理端口隔离；补改阶段 5 本地门禁现已通过。等待用户确认返回阶段 6 后，重新采集实际角色/绑定与版本、重生成 SQL、核对未部署 Gateway Client 源码、补齐 Gateway/BFF 精确制品；当前未部署，旧发布包不可直接执行。[发布准备草案](../requirements/20260911/operations-console-风控规则集历史与字典管理/release.md)。

- **Stage 6 prepared artifacts / no rollout：** Risk/BFF/Web 三个独立本地镜像已构建，Risk API 内嵌字节一致；BFF 1040 项及最终字典边界 16 项通过，SQL 状态门禁 16 项通过。新镜像未切换 development 标签；11 个 test 容器、原业务版本 Hash 和目录状态最终只读核对不变。[待完善发布包](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage6/release-package.json)明确 Gateway 门禁未通过，尚不可部署。

- **Gateway amendment implemented / functional regression passed / gates remain（2026-09-12）：** 用户确认[修订方案](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-design.md)并授权阶段 4。Gateway 取消业务静态 Registry，从同一快照绑定 Target/Route 并经原框架链转发，关闭请求级 Route Filter Cache；未匹配路由 404 后关闭连接，修复复用连接后续请求阻塞。保留已部署 65,536 字节请求体上限。BFF 独立回环管理端口 19201、业务端口 18201 及 test 探针工件已同步；Platform 模型/Schema 仅同步 1 张表注释，0 新 API/Wire 字段/表/业务列/索引/注册 DML。
- **Gateway amendment stage 4 historical verification：** Gateway 全量 **982/982**（60 类）、BFF **1041/1041**、Platform 相关 **23/23**（含真实本地 MySQL/HTTP 目标变更及停用）、模型/Schema/场景 **48/48**，边界脚本通过；37/27/27/51 目录离线编译通过。Gateway 分支覆盖率 **78.445378%（1867/2380）** 未达 90%，仍报已有 `GatewayGuardProfileCompiler$1` 完整性问题，未降低门槛；Platform 旧完整目录套件因 Resource 12/预期 21 在写入前停止，未放宽断言。实际证据见[研发报告](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-development.md)及[验证摘要](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-development/verification.json)。
- **Gateway amendment stage 4 historical authorization / rollout：** 当时未应用 Schema/初始化/注册 SQL；Platform 自有测试夹具清理后五张目录表与运行前一致。未部署、发布镜像、Git 提交或推送。该阶段随后获用户确认，补改阶段 5 当前进展见后；当时两个门禁、实际 Nacos/公开入口和容器探针尚不构成完整验收。旧 BFF 镜像需重建，Gateway main 还有未部署 Client API 工作，阶段 6 仍须核对精确制品源码；原单路径排障及旧发布包只保留历史证据。


- **Gateway amendment stage 5 / passed（2026-09-12）：** Gateway 最终 `clean verify` **1199/1199**（65 类）通过，指令 **96.649021%（18257/18890）**、分支 **90.084034%（2144/2380）**，覆盖率及报告完整性通过；仅按 JaCoCo 本身的 synthetic/module 规则识别编译产物，原 90% 门槛及业务类排除范围未放宽。框架 413 错误码映射已修复，新增安全/协议/配置/真实 HTTP 验证；本轮复核 Gateway 修改文件及 BFF 补改 4 文件 Hash 不变，复用 Gateway 1199 / BFF 1041 项证据。[阶段 5 验证报告](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-acceptance.md)。
- **Authorized isolated local SQL / original preserved：** 用户明确允许准备独立本机验证库后，已备份结构及授权元数据，创建 `127.0.0.1:13306/mor_platform_rco16_validation` 并应用现有 24 表 Schema/683 条初始化插入；只授既有应用账号该库数据权限。测试后 8 张目录表逐项恢复初始化状态，原 `mor_platform` 结构 Hash/全部表计数未变；[SQL 证据](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/local-sql-application.json)、[最终复核](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/final-database-check.json)。原库及 test 未应用本期注册或注释 SQL。
- **Platform acceptance / registration corrected：** 用户回复“可以”确认权限注册时序与独立本机补丁。已备份后将 7 条待发布绑定改为 REVOKED/version=2、管理员 version=3；完整初始化、独立 Oracle、182 条增量清单/SQL 同步，新增量的 6 条绑定使用 REVOKED/version=1。原 4 个角色/人员管理 403 已闭合，Java 授权上限未变。最终 `clean verify` **386/386**（42 类），指令 **97.784105%（23697/24234）**、分支 **90.058766%（1839/2042）**，均通过原 90% 门槛。真实 HTTP 完整快照 8 项、动态目标 2 项随全量通过；此前真实 DB 36 条有效快照和 51 条仅内存开放投影证据仍成立。原 377/6 失败记录保留为历史。[修正方案](../requirements/20260911/operations-console-风控规则集历史与字典管理/pending-registration-permissions.md)、[最终证据](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/permission-correction-verification.json)。
- **Stage 5 cleanup / next checkpoint：** 最终独立库 24 表/683 行，8 张目录表逐元组等于修正后的初始化，测试夹具为空、无遗留应用连接；原库结构 Hash/全部表计数未变。发布生成器按实际原 ACTIVE 绑定处理管理员/Viewer/自定义角色，21 项离线状态与 182 元组一致性检查通过；需求站点 136/136。五份旧 test SQL 已加执行前失败门禁，阶段 6 必须重新采集绑定/角色并生成精确发布包。当前等待用户确认返回阶段 6；没有 test 操作、部署、制品发布、提交或推送。[数据库复核](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/permission-correction-database-check.json)。

- **Stage 6 refreshed candidate package（2026-09-12）：** 用户随后确认返回发布准备，已只读刷新实际 mor-test 基线，仍为 11 个容器、Risk 72 表/23 个 DELETED 根/27 个版本、Platform 37 Route/22 Resource。五份发布/恢复 SQL 已按实际 7 条原绑定、管理员 9/Viewer 4 重生成，取代阶段 5 的不可执行旧文件；正常开放角色为 12/6，21 项边界及实际版本离线推演通过。候选镜像为 Risk `c6a4f44a8cba`、BFF `1a6c0110750d`、Web `ef04bdbbd75a`、Gateway `70ed9f3aa481`；Risk/Web 复用精确专用制品，BFF 重新离线打包，24 项双监听/字典边界通过后因 Foundation 依赖 Hash 差异补跑完整 verify 1041/1041；最终测试 JAR 与镜像一致。Gateway 使用阶段 5 干净构建制品。四镜像 JAR/18 个 Web 文件、内嵌 API 字节均核对一致；部署中 Platform/候选 Gateway 接受 37/27/27/51 投影，旧 Gateway 接受 37/27/27 恢复投影。全部为准备和离线验证，无 test SQL/运行实例切换。[完整候选发布包](../requirements/20260911/operations-console-风控规则集历史与字典管理/release-final.md)。
- **Stage 6 explicit source scope / stage 7 authorization pending：** 当前候选 Gateway 包含此前未部署的 Client API 及公共适配代码。实际 Nginx 两 API location 固定 OPERATIONS，所有既有/新增 Route 均为 OPERATIONS，Client Route 为 0；携带代码仍须明确接受，不能用入口未开放替代制品范围批准。已提供源码/制品差异、SQL Hash、备份、四应用顺序、BFF 探针恢复及限定合成验收清单，等待具体部署授权。Platform 应用与其他六个保留容器不替换，development 标签未变；无 Git 提交/推送或远程制品发布。[精确清单](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage6-final/release-package.json)。

- **Stage 7 deployed / user acceptance pending（2026-09-12 06:50 UTC）：** 用户在理解完整 Gateway 制品后明确“部署”，随后要求省去备份和代理功能验收、由用户验证。已按该最新范围应用维护、Risk DDL、Platform 注释/182 行注册及路由/资源开放 SQL，未执行备份或合成业务验收。Risk 74 表，Gateway 当前 Snapshot 为 `SUCCESS/NONE`、51 条 Operations Route、非 Operations Route 为 0；32 个有效 Operations Resource，管理员/Viewer 版本 12/6。Risk `c6a4f44a8cba`、BFF `79b697a757a9`、Gateway `70ed9f3aa481`、Web `ef04bdbbd75a` 四个应用已替换且 healthy；其余七容器身份保持。公开入口 `http://localhost:28080` 返回 200，前端 index Hash 对应本轮制品。业务功能未由代理在 test 验收，等待用户反馈；未提交、推送或远程发布。[部署检查点](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage7/deployment.json)。
- **Stage 7 startup correction：** 首次 BFF 进程已启动，但镜像内 `LocalHttpHealthProbe` 仍限定旧 18201 地址，面对 Compose 的 19201 回环地址退出 64。只替换探针对应地址并重新封装 BFF，业务 JAR 保持 `d66936ac…` 原字节；19 项探针专项检查通过后容器 healthy，从已完成注册的检查点继续，未重放 DDL/DML。最终 BFF 镜像取代阶段 6 的 `1a6c0110750d` 候选，[补正制品记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage7/bff-probe-image.json)。

- **Stage 7 user-requested demo data（2026-09-12）：** 部署后用户另行要求“帮我造几条数据进去”。已通过真实 test Operations HTTP 创建并保留 3 条 DEMO 拒绝原因、3 条 DEMO 标签，以及 CHECKOUT_PREPARE 下 `DEMO_AMOUNT_GUARD`、`DEMO_REGION_GUARD`、`DEMO_USD_ALLOW` 三个规则集，各 2 个版本，当前均 DRAFT。美元示例旧版本冻结并创建 1 条 TEST DRAFT 发布引用供关系图展示，未激活或执行规则。创建后只读确认每个规则集列表一行、版本两条；不构成完整业务验收。原有数据未修改，使用的临时登录会话已退出。[示例数据与服务端 ID](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage7/demo-data.json)。

- **Web-only visual follow-up（2026-09-12）：** 用户附图要求折叠侧栏显示名称/简介并去掉页面返回按钮，明确授权直接修改 Web、部署 test、由用户验收。已部署 Web `8832199077ef`，替代上述 `ef04bdbbd75a`：180px 紧凑规则卡片保留名称、条件数和决策摘要，编辑/只读一致；规则页面统一浏览器返回，未保存离开保护保留。相关 69 项检查与 TypeScript/Vite 构建通过，Web healthy、公开 HTML Hash 匹配；其他十容器身份/镜像不变，无 API/SQL/备份/提交/推送。视觉和业务验收等待用户。[部署记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/web-ui-followup/deployment.json)。

- **Stage 8 accepted / local Git archive（2026-09-12）：** 用户明确“我验收了，代码提交吧”。risk-compliance-service `27cf50b6fc48`、operations-console-bff `6822928fc355`、operations-console-web `9165b95e76da`、api-gateway `2f1f70e85e1a`、platform-support-service `d40b268b6440` 已提交到原 main；根仓库为[本期归档清单](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage8-commits.json)所在提交。应用改动与此前验收源码 Hash 一致，Pencil 只新增本期 12 个画板；本次需求站点构建及 136/136 项检查通过。五个应用工作区干净，较早的公共流程草稿和上一需求材料仍保留在根工作区；未推送、创建 PR/分支、重新部署或应用 SQL。

<a id="oe-client-model-20260911"></a>

## OE-CLIENT-MODEL-01：2026-09-11 四系统模型切片

本次按用户授权修改 CCT、Commerce、Payment、Finance 的模型和配套工件；PDManer 包含在范围内，Risk 的其他在途改动不属于本切片。决定见 [四系统模型决定](decisions/2026-09-11-cct-commerce-oe-pricing-model.md)，产物、SQL 路径及迁移前置见 [实施记录](../requirements/20260911/client-contract-OE与Client模型梳理/model-implementation.md)。

- 已完成：四份模型及图组、生成器、独立字段/关系 Oracle、完整人工 Schema、合成场景和文档路由。模型当前数量见本页第 3 节。
- 已验证：四系统模型及专项 Schema/身份检查 **147/147**；现实场景回归 **129/129**；Requirements 内容门禁 **71/71**。只声明这次实际运行范围，不沿用先前的 136 项或 Java 结果作为新模型验收。
- 稳定 ID 对照：CCT 380、Commerce 237、Payment 370、Finance 1090 个同名保留字段及其所属保留表的 PDManer ID 均未漂移；被改名或移出目标的结构按本次差异处理。
- GUI：PDManer 已刷新 CCT。新 OE、Scope、Affiliation、Agreement Party 检查通过；两个原有 JSON 字段的 largeText Base Type 触发建议级提示，物理 JSON 类型和 Base Type 与变更前相同。CCT 不宣称 GUI 全绿，另外三库的应用内检查待完成。
- 模型切片当时未完成：四系统新 Java/API/MQ 行为、正式 Wire 迁移、真实 Risk 新身份/Scope Review 联调、存量数据库迁移、真实端到端验证与部署。该模型切片没有执行 SQL；后续 CCT 查询与本地空库初始化见 CCT-QUERY-01。Finance 旧余额查询 SQL 明确保留为旧运行切片，Commerce 现有 Java/本地库同样未升级。
- 历史与激活门禁：不改历史订单、旧 Scope、签署结果、JSON/Hash 或经济结果；先盘点真实旧身份和 Reader，再安排人工迁移。Risk 新门禁不可用时不激活新范围，不伪造旧数字 ID。


<a id="cct-query-20260911"></a>

> 以下数据库与测试数量保留 2026-09-11 时点；2026-09-15 单份资料十表迁移及 Checkout 读取修复的后续证据见 [Client 入驻切片](#client-console-onboarding-20260914)。

## CCT-QUERY-01：2026-09-11 Checkout 预检与最终解析首批

用户确认保留 Source Relation，并授权先实现 CCT 两个查询；[切片与数据库动作清单](../requirements/20260911/client-contract-OE与Client模型梳理/cct-query-implementation.md)记录当前范围。Risk 和其余系统代码没有纳入本批。

- **Source implemented / Runtime gated：** `client-contract-api` 发布私有 GET `/checkout-eligibility/v1/precheck` 和 POST `/checkout-context/v1/resolutions`。Web 条件装配本库 MyBatis 只读快照、Client/Primary/准确四要素/来源校验、按优先级的合同/条款/法律证据检查，以及相关 Root 和准确胜出者的输出前复核。默认 `checkout.enabled=false`；Risk 新身份资格查询尚未存在，真实资格端口明确返回 `503 OWNER_UNAVAILABLE`，没有默认通过或测试替代实现。
- **Verified：** 在本机 Java 25/Maven Wrapper 下执行未筛选测试的 `./mvnw -q verify`，**32/32** 通过，0 失败/错误/跳过；包含 12 个规则、3 个接口契约、4 个 HTTP 边界、9 个真实 MySQL 业务和 4 个原运维基础用例。Web 可执行 JAR 与 API JAR 构建成功。业务用例覆盖预检、合同过期后按优先级继续、配置/Root 变化、未知数据库 Code 拒绝及 Risk 未接入时的明确 503；不把本地候选成功解释为最终解析 HTTP 200。首轮暴露的泛型枚举 Handler 复用问题已修复：XML 按 `javaType` 使用逐枚举注册的 Handler，并补充未知状态不外泄内部信息的用例。完整日志与制品摘要见下方证据目录。
- **Local database applied：** 用户明确确认后，在 **2026-09-11 19:10:43 UTC** 向 `mor-local-mysql-1` / `127.0.0.1:13306/mor_cct` 人工应用 CCT 完整 Schema；执行前该库为 0 表，未执行业务初始化 DML。实际 **38 表、428 字段、142 个主键/唯一/普通索引**的类型、可空、默认值、字符集/排序规则、完整注释及索引列顺序与 PDManer 一致，物理 FK/CHECK 为 0。全量验证后逐表精确计数均为 0，合成 Fixture 无残留。没有对 test 或其他业务库执行写入。
- **Backup / evidence：** 受限目录 [`/Users/kenny/.codex/backups/cct-checkout-20260911T191028Z/`](/Users/kenny/.codex/backups/cct-checkout-20260911T191028Z/)保存空库备份、实际执行 SQL、验证日志和 `verification.json`；目录权限 700，文件 600。空库备份 SHA-256 为 `04534aba2e19340eda8baa066b8be94992e654a43dcc37234b38ae427f00c0d4`，实际执行 SQL SHA-256 为 `26ef700eef63ba8e2d22bbfd1fdae64176a9a489de233f0708f2541e7f21c54a`。执行后四份 Schema 的状态注释统一改为链接本台账，DDL 语句未变；不能将完整建表文件重复应用于本地现有库。
- **Other verification：** 前一源码批次的 `npm --prefix requirements-site test` 构建及 **136/136** 保持为该批证据，本次没有修改需求站源码。23 个 Mapper Query 的 **164 个投影列**已对照模型；本轮 Schema `--check` 和实际库核对通过，文档/模型补充检查见同一证据目录。
- **Still pending：** Risk Owner 新资格 HTTP 接入、最终解析 HTTP 200/依赖超时及真实跨 Owner 竞态验证、写入口的 Root 传播、Commerce 支付准备接入（基础预检见下方 COM-PRECHECK-01）、存量环境迁移及应用部署。当前通过的是 CCT 本地规则、真实数据库查询和入站边界；Price 匹配接口、Scope 激活/签署写操作不在本批。没有提交、推送或部署。

<a id="commerce-client-gateway-path-20260912"></a>

## COM-CLIENT-PATH-01：2026-09-12 Commerce Client API 入站路径修正

- **Decision / scope：** 用户将已有十个 Client API 服务前缀调整为 `/client-gateway-api/v1`，见[路径修正决定](decisions/2026-09-12-commerce-client-gateway-api-path.md)。Context Path 保持 `/commerce`，公开 OpenAPI 仍是 `/api/v1`；不改变业务行为和数据库。
- **Implemented：** Commerce `ClientApiPaths`、metadata-only 日志配置、已有契约与 HTTP 测试以及组件 README 已同步。请求过滤器继续引用统一常量；旧路径不保留别名。
- **Verified：** 已先更新契约断言，确认旧生产常量导致预期失败；随后 Java 25 `./mvnw -q clean verify` **301/301** 通过，0 失败、0 错误、0 跳过。现有十个接口的真实 HTTP 场景改用新前缀，继续验证可信身份、请求约束、幂等、no-store 和业务行为；配置测试验证 metadata-only 前缀。`npm --prefix requirements-site test` 构建及 **136/136** 通过，6 个新增决定链接/锚点和本次差异空白检查通过；源码、配置、测试及 README 无旧前缀残留。日志见 `/Users/kenny/.codex/verification/commerce-client-gateway-prefix-20260912T111828Z/`。验证使用本地基础设施和测试外部依赖，不代表 Gateway 或真实 Owner 联调。
- **Delivery：** 用户已授权本地提交；Commerce 提交为 `d953d7afa546f4572e3607a3bb3e1324b5ff81f4`，配套路径决定、合同投影与本条状态一同归档。提交前核对 19 个文件与完整验证时的内容一致，本次归档仅补充交付状态。
- **Deployment / remaining：** 本次未修改 Gateway、Platform、Nginx 或其他服务；没有应用 SQL、推送、发布或部署。后续 Gateway 接入需使用新的 `/commerce/client-gateway-api/v1` 下游地址。

<a id="commerce-precheck-20260911"></a>

## COM-PRECHECK-01：2026-09-11 Commerce 基础创建接入 CCT

用户确认“下一步让 Commerce 接入基础预检”，随后于 2026-09-12 回复“执行”，授权本地备份、增量迁移与综合验证；具体范围见
[实施记录](../requirements/20260911/client-contract-OE与Client模型梳理/commerce-precheck-implementation.md)。

- **Source implemented（2026-09-12 直接替换修正）：** 用户确认旧 Commerce 代码未发布，当前 Create 统一在事务外通过
  CCT Owner `CheckoutEligibilityApi` 预检，仅提交基础 Order/Items、退款仓位、资金投影与 PREPARING Session，
  按 `CLAIMED → ORDER_COMMITTED → COMPLETED` 冻结回执。删除旧 Resolve/Risk/Finance/Payment 创建链、四组 Local Mock、
  无消费者的 Prepared 聚合/Mapper/模型以及历史测试夹具；`CheckoutCreationService` 直接承担现有基础创建。
  请求和回执 JSON 不再有 `schemaVersion`，无 Schema 1/2 分支；不预分配 Context/Legal/Payment ID 或下游幂等键。
  Foundation 的可选快照版本元数据传 null；语义请求 Hash、永久 Client Order ID、三个冻结 Token、Lease/CAS 与恢复检查点保留。
- **Wire：** Create 固定返回 `PENDING/ESTIMATED/PREPARING`，税额、舍入、总额均为 null；GET 投影当前状态，
  成功创建重放保持最初回执。Java DTO 与原 v1 OpenAPI 直接调整，移除旧响应构造器，当前状态和金额字段为必填。
  `context_format` 不再参与 Java 模型、GET 或恢复分发；本轮不执行 DDL，既有非空列插入固定 `TERM_PRICE`，
  旧数据库列的物理清理仍未执行，不能据代码简化宣称完整目标数据库迁移完成。
- **CCT adapter（2026-09-12 服务发现修正）：** 已移除 `commerce.cct.base-url` 和手工构建 RestClient/接口代理。
  Commerce 通过 `@ImportHttpServices(group="client-contract-service")` 自动注册 CCT Owner API，Spring Cloud LoadBalancer
  按服务名选择 Nacos Discovery 提供的实例；CCT Web 已补同名注册依赖与配置。Foundation 既有 HTTP 普通 JAR
  新增 `configureGroup(...)` 挂载原单层异常边界，不增加 Boot/Cloud 依赖或新中间件模块。
  Spring Group 配置 2 秒连接/5 秒读取超时及禁止重定向，LoadBalancer 和底层 Apache HTTP 都禁止自动重试。
  成功 JSON 验证类型、日期、字段闭合与资格结果；发现为空/连接中断为依赖不可用。
  明确无候选才冻结配置失败；配置变动/歧义及技术失败保留命令恢复能力。CCT API JAR 本轮仅安装到本地 Maven 供编译，未发布 Nexus。
- **Previous development evidence（已被下方直接替换验证取代）：** Java 25 下 22 个测试类共 **119/119** 开发检查通过，0 失败/错误/跳过，包括 API/OpenAPI、
  金额与旧规范回执字节向量、出站真实 HTTP Simulator、Spring 启动、请求绑定、身份、错误及架构/配置门禁；
  补充重复字段、未知字段、超长 Body、无效日期和尾随 JSON 后 CCT Wire **6/6** 重验通过。
  `./mvnw -q -DskipTests package` 及 Client API `javadoc:javadoc` 成功；Mapper XML、增量列与目标模型存在性、
  本轮 Markdown 本地链接和 `git diff --check` 通过。开发检查没有执行 Checkout 业务落库。
- **Local database applied（2026-09-12）：** 按明确授权，向 `mor-local-mysql-1` / **127.0.0.1:13306/mor_commerce**
  人工应用 [增量 ddl.sql](../requirements/20260911/client-contract-OE与Client模型梳理/db/commerce/ddl.sql)，执行时间 `2026-09-12T03:40:40.700244+00:00`。
  执行前确认该库没有其他连接，备份全库并校验完整建表内容/摘要。迁移前 **15 表/246 字段**、所有表为 0 行；
  迁移后 **15 表/250 字段**，四个新增列和九个可空调整符合脚本，原字段类型、默认值、字符集/排序规则及 **51 个索引**不变，
  原列数据摘要一致，物理 FK/CHECK 为 0。该桥接结构保留历史列，不等同 **249 字段**完整目标 Schema；没有执行全量 CREATE。
- **Previous full verification（2026-09-12，直接替换前源码）：** 迁移后在 Java 25 下执行未筛选测试的 `./mvnw -q clean verify`，**214/214** 全部通过，
  0 失败/错误/跳过；包含真实随机端口 HTTP → 生产业务链 → 本地 MySQL，以及 CCT 实际出站 HTTP 到 Simulator。
  基础创建、只写五类事实、最终金额为空、无候选稳定失败、超时/不可用与恢复、过期、Token 冲突回滚、永久 Client Order ID、
  缺事实拒绝、损坏回执拒绝及 Schema 1 历史恢复均通过。JaCoCo 行覆盖率 **90.33%（2131/2359）**，通过原 **90%** 门槛。
  Web 可执行 JAR 和 API JAR 构建成功；没有跳过或降低门禁。本轮未发现需要修改代码的失败。
- **Previous full verification（2026-09-12 直接替换后、服务发现修正前）：** Java 25 下未筛选、未跳过的 `./mvnw -q clean verify`
  **195/195** 通过，0 失败/错误/跳过；JaCoCo **90.08%（1326/1472）**，原 **90%** 门槛不变。
  覆盖当前唯一创建链、CCT 实际 HTTP 到 Simulator、缺失/非法 Origin 拒绝、确定拒绝与配置变化/歧义、真实超时、
  并发永久订单号冲突、两个检查点 Lease 接管、Token 冲突回滚、创建回执在后续状态变化后不变、损坏快照及闭合 JSON。
  快照去版本回归先出现预期失败，再由直接替换实现通过；删除旧业务用例，保留并迁移适用的幂等/错误/查询用例，
  不用保留失效功能换取覆盖率。Web/API JAR 构建成功，`git diff --check` 通过；结束后本地 15 张表精确计数全部为 0。
  本轮仅 Commerce 代码与相关合同/记录变化；没有新增 DDL、修改 Risk/CCT/Finance/Payment 源码或执行部署。
- **Previous direct-replacement evidence：** [/Users/kenny/.codex/backups/commerce-single-flow-20260912T040743Z](/Users/kenny/.codex/backups/commerce-single-flow-20260912T040743Z)
  保存修改前源码归档、初始失败证据、最终完整构建日志、Surefire、JaCoCo、源码/制品 SHA-256 和逐表清理结果；
  `verification.json` 为本轮验收记录。CCT 验证仍为真实 HTTP 契约到 Simulator，最终 OE 解析/支付准备及真实跨服务联调未完成。
- **Previous full verification（2026-09-12 服务发现修正后、公共 HTTP 收敛前）：** Java 25 下 Commerce 未筛选的 `./mvnw -q clean verify`
  **192/192** 通过，0 失败/错误/跳过；JaCoCo **90.13%（1315/1459）**，原 **90%** 门槛不变。
  CCT 同样完整 `clean verify` **32/32** 通过；Foundation `-pl paytech-http-client-support -am install` 成功，
  HTTP 模块 **105/105** 通过，兼容既有显式 Factory 与新 Spring Group 注册入口，制品只安装本机 Maven。
  Commerce 使用真实 Spring HTTP 代理、LoadBalancer、SimpleDiscovery 与 HTTP Simulator，覆盖两实例轮询、实例移除、
  空列表、连接中断不重试、503 不重试、超时/重定向/协议拒绝、Request ID 传播及原创建/幂等/恢复链。
  测试入口 RestTestClient 复用禁重试传输，避免测试客户端自动重发 503 掩盖首次响应；Client API 的 Reactor 禁入门禁
  检查 API 自身解析后的依赖，不再误读 Web 运行时类路径，Maven 同时禁止 API 引入 Cloud/Nacos/Reactor。
  `git diff --check` 通过，结束后本地 Commerce **15 表全部 0 行**。自动测试禁用 Nacos，不访问共享注册中心。
- **Previous discovery evidence / delivery boundary：**
  [/Users/kenny/.codex/backups/commerce-cct-discovery-20260912T044028Z](/Users/kenny/.codex/backups/commerce-cct-discovery-20260912T044028Z)
  保存修改前源码、失败证据、最终构建日志、Surefire/JaCoCo、源码/制品摘要和数据库清理结果。
  本轮仅 Foundation HTTP 支持、Commerce 发现调用、CCT 注册配置及相应测试/文档变化；无 DDL、无 Risk/Payment/Finance 修改，
  无 Git 提交/推送、Nexus 发布或部署；**真实 Nacos 注册/发现联调、支付准备与最终 OE/定价链尚未验收**。
- **Current source（2026-09-12 公共 HTTP 收敛）：** `CctPrecheckConfiguration` 从 **134 行缩为 32 行**，
  只声明 Owner API、错误转换和严格响应选项。Foundation Factory / Group 入口统一安装 `HttpServiceExchangeInterceptor`，
  每次同步调用传播合法的 MDC Request ID，不缓存或复制其他 MDC 项；与 MVC Filter 共用 `ApiProblemProfile` 的 MDC Key。
  `withStrictJsonResponses(8192)` 使用标准 Jackson typed 绑定和公共有界响应读取，删除 CCT 手写 JSON Reader 与响应包装。
  应用级禁重试策略移入 `HttpClientConfiguration`。删除仅被 CCT 使用的 `AbstractClientTemplate` 及重复调用日志，
  CCT 适配器只保留资格/日期/原因和明确业务错误解释，不再重复捕获所有参数异常；公共网络/协议错误仍由 Foundation 转换。
  未新增 Starter、Boot/Cloud 依赖或业务 API；HTTP 普通 JAR 增加 SLF4J API，Logback 只用于该模块测试。
- **Current full verified（2026-09-12 公共 HTTP 收敛后）：** Commerce Java 25 `./mvnw -q clean verify`
  **194/194** 通过，0 失败/错误/跳过，JaCoCo **90.02%（1254/1393）**，原 **90%** 门槛不变。
  Foundation `-pl paytech-http-client-support,paytech-mvc-web-starter -am install` 成功，HTTP **108/108**、MVC **124/124** 通过，
  仅安装到本机 Maven。真实 HTTP 验证逐请求 MDC A/B/缺失/非法值、不复制其他 MDC、Factory/Group 两种入口、
  标准 Jackson 闭合/类型约束、UTF-8 正文字节上限（成功及错误）、CCT 负载均衡/无重试/超时/错误映射及原业务恢复。
  新增预检过程中到期的真实 Checkout 回归，确认 CCT 返回可用也不能提交过期订单，重放不再次查询；公开入口到 CCT 的 Request ID 一致。
  完成后本地 Commerce **15 表全部 0 行**；`git diff --check` 通过。
- **Current evidence / boundary：**
  [/Users/kenny/.codex/backups/commerce-http-simplification-20260912T050237Z](/Users/kenny/.codex/backups/commerce-http-simplification-20260912T050237Z)
  保存修改前源码、缺失 MDC 的失败证据、最终日志、Surefire/JaCoCo、源码摘要和逐表计数。
  本次未修改 CCT/Risk/Payment/Finance、API 或数据库结构；没有连接 Nacos、部署、发布 Nexus、提交或推送。
  真实 Nacos 联调和后续支付准备仍保持未验收状态。
- **Previous migration cleanup / boundaries：** 验证结束后逐表精确计数，**15 表全部为 0 行**，测试自有 Fixture 无残留；原索引定义与备份逐表一致。
  本轮没有写 CCT、Risk、Finance、Payment 或 test 数据库，没有修改其他系统源码、部署、提交或推送。
  支付准备、最终 OE/合同冻结、Tax/Payment Intent、晚到定价、Checkout 页面和公共路由尚未接入；
  CCT 使用 HTTP Simulator，不把本地成功称为 Commerce 与真实 CCT 数据的完整业务联调。
- **Backup / acceptance evidence：** [/Users/kenny/.codex/backups/commerce-precheck-20260912T034031Z](/Users/kenny/.codex/backups/commerce-precheck-20260912T034031Z)
  保存执行前全库 SQL、迁移原文及 SHA-256、逐列/逐表前后校验、完整构建日志、Surefire 报告、JaCoCo XML、制品摘要与清理结果；
  总结为该目录 `verification.json`。备份未写入版本库。恢复必须核对备份之后的新数据，不自动删库。

- **Evidence：** 开发检查、最终 CCT Wire、构建/JavaDoc、测试编译日志、Surefire 报告及源码摘要保存于
  [/Users/kenny/.codex/artifacts/commerce-precheck-development-20260911T200120Z](/Users/kenny/.codex/artifacts/commerce-precheck-development-20260911T200120Z)。此目录是研发验证证据，不是数据库备份。

- **Pre-commit verification（2026-09-12）：** 本次待提交源码重新运行 Commerce `clean verify` **194/194**、CCT 安全注入本地测试配置后的 `clean verify` **32/32**；四系统生成器/Schema `--check` 与模型/场景 **276/276** 通过。Foundation 本次暂存源码与前述 HTTP **108/108**、MVC **124/124** 的验证摘要一致。需求站完整工作区构建成功、测试 **134/136**，两项失败来自其他任务的 Gateway 文档；导出本次暂存内容后独立运行完整构建与测试 **136/136** 通过。各仓库暂存差异空白检查通过。证据保存在 [/Users/kenny/.codex/backups/oe-commerce-commit-20260912T052717Z](/Users/kenny/.codex/backups/oe-commerce-commit-20260912T052717Z)；本轮未执行 DDL、推送、部署或制品发布。

<a id="rco17-test-20260912"></a>

## RCO-17 决策流配置（2026-09-12，test 已部署，待用户验收）

- **范围与阶段：** [需求目录](../requirements/20260912/operations-console-决策流配置/technique.md)原阶段 1–5 已确认，包含用户批准的[解耦方案修订](../requirements/20260912/operations-console-决策流配置/simplification.md)及阶段 5 必要修复。用户对阶段 6 [test 发布包](../requirements/20260912/operations-console-决策流配置/release.md)回复“对”后，阶段 7 已部署并完成限定环境验收，等待用户完整功能验收。以下阶段 4–6 的“未部署/待授权”保留为当时历史，不代表当前状态。只交付配置管理；无真实执行、发布激活或运行动作。
- **原四表阶段的模型及本机结构：** 唯一生成源、PDManer、完整 Schema 追加 4 表/44 字段/11 索引时，原 74 个实体逐项保持不变；后续三表精简修订见下文；当前模型总数见第 3 节。获用户单独授权后，已备份本机 Schema 并手工应用增量 DDL 到 `127.0.0.1:13306/mor_risk`，核对 4 张新表及全部列/索引/注释一致。备份及执行范围见 [本机应用记录](../requirements/20260912/operations-console-决策流配置/db/risk-compliance-service/local-application.md)。
- **实现与开发检查：** Risk、BFF、Web 已实现 10 新操作/4 原操作调整，包括流程配置、准确历史/引用、整体删除、动作可选及原因统一反查；无 Gateway 生产代码改动。Risk 完整 reactor **205/205**（真实本机 MySQL/Redis/HTTP），BFF 完整 reactor **1,046/1,046**，Web **894/894** 与生产构建、模型/Schema **29/29**、需求站构建与 **136/136** 通过；设计/注册一致性通过。已检查本机合成 API 场景下的代表性页面交互，不能据此声称真实全链路或完整视觉验收完成。证据及本期修复见 [开发交付报告](../requirements/20260912/operations-console-决策流配置/development-checks.md)。
- **注册、环境与剩余阶段：** Platform 唯一初始化 SQL 已合入 **118 行注册/1 行角色更新**，逐值核对且每行仅一份；本机独立验证库的应用与关闭状态见下方阶段 5 记录，test 尚未应用本期注册。risk-api 仅安装本机 Maven 仓库用于 BFF 开发。阶段 5 本机视觉/交互、关键故障矩阵及真实 Gateway/Snapshot/授权联调已完成，见下方结果；无本需求的 test 数据库变更、共享制品发布、部署或 Git 提交/推送。

- **2026-09-12 表关系复核：** 用户要求查看表关系及精简空间；已只读核对当前源码、32 张相关表的本机/test 行数和模型引用。7 张未接入运行表为裁剪候选，4 张空表仍有读取逻辑；旧协议版本/输入字段/顺序绑定仍被规则及新图引用资格检查使用，不能视为已被替换。详见 [表关系评审及证据](../requirements/20260912/operations-console-决策流配置/table-relationship-review.md)。未修改应用、模型、SQL 或数据；未作出删除决定，不代表阶段 5 已完成。
- **精简修订研发完成：** 用户确认后，工作区已将规则版本改为直接保存输入合同，条件/动作参数保存字段代码，并收敛共享输入目录和规则内容还原；新规则保存不再插入三张旧编排表，旧状态及 Release 物理 ID Hash 保留专用兼容读取。BFF/Web 的规则 `pointVersionId` 接受显式 null，Candidate 自身仍要求真实 ID。唯一生成源、模型及最终 Schema 已同步三表的增 4 列/删 2 列及 2 索引，模型/Schema **30/30** 通过。原段落的 205/1,046/894 是精简前证据，不能当作本修订完整回归通过；修订后的完整回归结果见下项。
- **精简修订本机数据库：** 用户在具体三表 SQL 授权说明后回复“可以”。执行前核对 13306/mor_risk 无写入方、历史及三表全行数为 0；备份后应用扩展、同连接回填/后置检查与显式 COMMIT，再收敛删列。三表 44 个字段结构及全部索引、新四列注释与模型吻合；39 个旧列注释差异与备份一致，本轮保留。备份目录和证据见 [本机应用记录](../requirements/20260912/operations-console-决策流配置/db/risk-compliance-service/simplification/local-application.md)。test 历史 462 行仅为先前只读快照，未迁移；无共享制品发布、部署或提交。
- **精简修订开发回归：** Risk 完整 reactor **208/208**（真实本机 HTTP/MySQL/Redis）；最终清理旧冲突分类及无用导入后，受影响测试 **8/8** 再次通过。BFF 完整 reactor **1,051/1,051**，Web **900/900** 和生产构建，模型/Schema **30/30**、原设计/注册及链接检查通过。新规则保存零旧编排新增、新旧规则混合流程引用、准确合同拒绝、原字段 ID/九字段快照与 Hash、旧 Candidate 查询/静态校验/成功回放、并发及 ACTIVE Release 删除阻断通过。历史测试事实与全部流程配置数据已清理。阶段 4 交付证据见 [修订开发检查](../requirements/20260912/operations-console-决策流配置/simplification-development-checks.md)；阶段 5 及真实 test 历史迁移验证仍未完成。

- **RCO-17 阶段 5 完成（2026-09-12）：** 两次本机联调已交付 [综合验证记录](../requirements/20260912/operations-console-决策流配置/acceptance.md)。首轮 CSS 说明区遮挡已修复；用户对两项补充回复“都同意”后，已补开既有规则历史 GET，并完成 Foundation 显式字段校验失败能力、BFF 当前 Body 路径投影及 MVC 422 输出；其他代理默认行为不变。浏览器发现集合字段无法定位，Web 复用静态校验修复，真实保存失败保留草稿、准确定位、修复保存 #3、历史/已删除规则引用只读、整体删除和返回均通过。Foundation 相关 reactor 347/347；BFF 全量 1,052 项中旧工厂数量断言修正后受影响 16/16 通过，最终各 suite 报告均通过；Web 900/900 和构建通过。六张 Pencil 状态已映射检查，实际布局差异见报告，不声称逐像素一致。真实入口使用既有独立 Platform 验证库和 mor_risk、真实四应用/会话/权限，Nacos 由本机 SimpleDiscoveryClient 代替，Vault/签名仍为已有开发适配器边界。最后已关闭 13 Route/3 Resource/2 RolePermission，同一 Gateway 从 49 路由恢复 36、入口 200→404；原配置 version=5、额外历史 Route DISABLED/version=3、角色 version=8。118 条本机注册保留，临时事实/进程/Redis 命名空间及凭据已清理。停在阶段 5 交付确认点；test 历史迁移、制品发布、部署与 Git 提交均未执行。
- **阶段 6 发布准备交付：** 已构建 Risk/BFF/Web 三个本地候选镜像（完整 ID、源码及制品摘要见 [发布包](../requirements/20260912/operations-console-决策流配置/release.md)），没有替换运行标签。test 只读预检为 33 个规则版本、418 个条件 Context 和 11 个动作 Context，异常 0；实际旧 Risk Reader 的 33 个规则/协议 Hash、10 个 Release 详情及创建回放通过。现有 Platform/Gateway 实际部署编译器离线接收 51→27→27→61 路由，不需替换两个应用。118 行注册及入口切换 SQL 已按 test 角色 12/6 准备并通过内存 CAS 检查；新 Reader 已针对候选 JAR 编译，test 回填后的比对仍属阶段 7 门槛。镜像内容检查通过，11 个 test 原容器/镜像/启动时间未变。当前等待本包的 test 备份、SQL、3 应用部署及限定合成验收授权；未执行 test 写 SQL、共享发布或 Git 提交。
- **阶段 6 最终材料检查：** 需求站首次 135/136 的失败来自 RCO-17 精简前统计断言遗漏；逐项核对唯一模型后同步断言，重新构建及 136/136 通过。设计/注册一致性、252 个本地链接及根/6 组件空白检查通过。精确 SQL/制品摘要见需求目录 assets/stage6/release-manifest.json，test 写入及部署仍未执行。

- **阶段 7 Deployed（2026-09-12）：** 备份后完成 test 两库 SQL 和 Risk→BFF→Web 升级，镜像前缀为 `89bd500057a2` / `bfc92deaee5e` / `e6585089b4e4`；11 容器健康，其他 8 个容器保持原 ID/镜像/启动时间。Risk 4 新表和 3 旧表精简已应用，回填 462 行；删列前实际旧/新 Reader 与原 33 规则/协议/输入身份 Hash、10 Release 详情和创建回放一致。Platform 118 行注册已生效，Gateway 61 Route / 35 Resource，角色版本 15/8；公开 HTML 与全部 22 静态文件吻合。精确备份、制品、辅助历史校验修正与执行顺序见[部署记录](../requirements/20260912/operations-console-决策流配置/deployment.md)。
- **阶段 7 验收与停点：** 1 个合成规则集、1 个流程/2 个成功版本完成保存、同键重放、409 旧版本冲突、422 非法图保留草稿、准确历史/规则引用、只读历史及浏览器返回验收；两个管理根已由 API 整体删除，历史保留，无评估/动作事实，验收会话退出。test 只读角色现有账号均停用或关闭，未重跑该身份登录验收，也未改变账号状态；阶段 5 本地证据保留。部署及限定验收已交付，用户功能验收/阶段 8 提交待确认；未 Git 提交/推送、未 Nexus 发布。待提交范围见部署记录，避免混入其他需求。

- **用户验收示例（2026-09-12）：** 用户另行要求创建一条决策流数据，已通过 test API 保留 `DEMO_CHECKOUT_BRANCH_FLOW` / `224578384930377728` 第 1 版，及配套动作留空的金额检查规则集。5 节点/4 连线、静态校验及回读通过；大额拒绝、其余非美元复核、其他放行。与上方已删除的部署验收夹具分开，供用户测试，未启用真实执行；[数据证据](../requirements/20260912/operations-console-决策流配置/assets/stage7/user-demo.json)。

- **Web 样式反馈部署（2026-09-12）：** 用户明确仅 Web、不运行测试，随后授权部署 test。圆角折线、删除说明、无选择时隐藏右侧面板已随 Web 镜像 `2d40894a389f` 部署；前述 `e6585089b4e4` 保留为原阶段 7 制品证据。生产构建通过，容器 healthy，公开 HTML/决策流 JS/CSS 摘要吻合，其他十个容器原 ID/镜像/启动时间不变。未跑测试或业务验收，未改数据库，未提交；[部署证据](../requirements/20260912/operations-console-决策流配置/assets/stage7/web-ui-deployment.json)。

- **下一期待办 RCO-18：** 用户明确保留“是否命中”，另增“本节点处理结果”，允许 REJECT 经连线继续另一个规则集，安排下一期修改；已登记[需求及分析入口](../requirements/20260912/operations-console-决策流节点处理结果/requirement.md)。当前仅记录，未改业务代码、Schema、示例或 test；结果汇总、未命中/空动作及旧终态兼容待方案确认。

<a id="rco19-variables-20260912"></a>

## RCO-19 统一风控变量管理与条件编排（2026-09-12）

- **用户验收与代码提交（2026-09-13）：** 用户反馈“好像没什么问题，提交代码”，确认本轮 test 验收并授权提交。决策流配置、统一变量管理及旧规则详情修复已分别提交：`paytech-foundation` `d2e5603`；`risk-compliance-service` `6d7e684`；`operations-console-bff` `0727168`；`operations-console-web` `feefc36`；`platform-support-service` `74db641`。公共需求、模型生成源、Pencil 与发布证据随本次根仓库提交归档；其他需求的未提交改动保留，未推送。提交前对实际暂存版本检查：需求站点构建及 136/136、Risk 模型/Schema 32/32 通过；应用回归沿用本节已记录的对应发布证据，没有重复全量运行。已执行 SQL 的历史文件保留原始字节。

- **验收缺陷修复（2026-09-13，后于下方初次发布）：** `DEMO_USD_ALLOW` 详情的实际 HTTP 状态为 200，Web 解码器遗漏 `operandFormat=LEGACY` 导致误报服务不可用。已只修改 Web 的格式识别并保留旧语义/未知格式检查；259 项相关检查及生产构建通过，真实响应可生成两条规则的只读编辑模型。test Web 当前镜像为 `rco19-legacy-detail-20260913`，其他 10 容器不变，规则内容/版本/Hash 不变，无 SQL。见[验证证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/rule-detail-compatibility/verification.json)与[Web 发布证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/rule-detail-compatibility/deployment.json)。该缺陷修复随后获用户验收并提交，见上方记录；此前受限的原生浏览器观察不作为视觉验收证据。

- **test 发布记录（2026-09-13，随后已获用户验收）：** 用户明确“发布”，授权发布准备与既有 `desktop-linux/mor-test` 部署。Risk/BFF/Web 最终镜像标记为 `rco19-variables-20260913-body-limit`，其他 8 容器镜像、身份、启动时间不变。Risk 已应用 2 新表及旧表增量、17 变量/9 成员；Platform 已应用 113 行注册并启用 9 路由/3 资源/2 管理员权限，管理员角色版本 17。Gateway 自动加载 70 路由，Snapshot SUCCESS、NONE、无旧配置回退；11 容器均健康。入口 `http://localhost:28080` 的变量菜单、列表、中文搜索、成员读取可用，6 个 Web 公开资源 Hash 与最终构建一致。见[发布材料](../requirements/20260912/operations-console-统一风控变量管理与条件编排/deployment.md)及[最终检查](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/final-checks.json)。
- **发布容量修正：** 原成员路由 256 KiB 超过现有 Platform/Gateway 的 64 KiB 绝对上限，导致 Snapshot 拒绝，旧路由保持服务；已将本需求 Route、Web/Risk/BFF 及管理上下文统一到 65,536 字节，并同步初始化/增量 DML 和合同。集合仍最多 1,000 项，同时受总字节限制。新增失败证据后，Risk 65,536/65,537 字节边界 1/1、BFF 变量边界 7/7、JAR/Web 构建通过；test 超限请求返回 413 且原成员未改。未重跑全量回归，未扩大 Gateway 安全上限或替换其制品。
- **test 示例已迁移：** `DEMO_FLOW_AMOUNT_CHECK` 第 2 版为 `224761038741016576`，Hash 为 `dc520420beff45ebfda24dea7ed3cf776279b988a14d30ccaf8ebb91de67a8e1`；`DEMO_CHECKOUT_BRANCH_FLOW` 第 2 版为 `224761039558905856`，准确引用该规则版本。原第 1 版保持，5 节点/4 连线及初始分支含义不变；流程条件使用 schemaVersion 2 统一变量操作数。共享阈值/标准币种临时改为 10000/EUR 后，无需保存规则/流程即可读到新值且 Hash/版本/图不变；已按本次 CAS 恢复 8000/USD，两个常量管理版本为 3。见[迁移回执](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/demo-migration.json)。验证会话已退出。真实执行未启用，等待用户页面验收；未 Git 提交、推送或向 Nexus 发布。

### 此前各阶段记录

下列“未部署/待确认/未应用”是各阶段结束时的历史状态，当前环境与确认点以上方发布记录为准。

- **需求范围已确认：** 用户确认需求范围并同意进入阶段 2，材料为[范围与验收稿](../requirements/20260912/operations-console-统一风控变量管理与条件编排/requirement.md)。拟统一字段/常量/集合/节点输出目录，规则和流程条件的两侧均可选择变量或常量；RCO-18 作为子项纳入，保留 matched，真实执行延期。用户已确认共享常量/集合修改后自动采用最新成功保存值，无需逐条重新保存引用配置；进一步明确前后端业务变量定义均由变量库管理，已支持来源范围内新增变量不应要求应用改代码、发布或重启，test 示例统一引用迁移纳入本期。已更新范围与 13 项功能/6 项视觉验收，直接常量和历史定义不改写；阶段 2 分析已获确认；阶段 3 完整方案已确认，阶段 4 研发成果已确认，阶段 5 综合验证完成待确认。
- **本轮实际变更（阶段 4 已交付，2026-09-13）：** 变量 PDManer 生成源、模型、完整 Schema/初始化目录及需求增量材料已同步，当前模型 80 表 / 990 字段。Risk 已实现 9 个变量操作、类型化持久化、资源锁/CAS、集合整批替换、当前/历史引用扫描，以及规则/流程共享操作数校验、当前值投影、历史 Reader/回放隔离和 3 个执行就绪门禁。BFF 已实现 9 个转发操作，保留独立权限校验，并校验 Owner 响应身份、类型、容量和字段错误。Web 已接入变量列表/定义/成员/引用页面及规则/流程共享控件，候选来自变量接口，支持显式去重、草稿保护、当前值刷新和只读投影。Platform 的 113 条注册及角色版本更新已归并到初始化 SQL，仍为禁用/REVOKED 准备态，未应用。工作区实现不代表部署完成；本机 Risk SQL 状态见下方记录。
- **开发回归停点（阶段 4 完成）：** Risk 完整 auto 回归 **254/254**（38 类，0 失败/错误/跳过）通过，包含 17 个变量真实 HTTP/MySQL/Redis 用例；其中 8 个确定锁顺序用例覆盖规则/流程保存与变量删除/停用的两种先后顺序。共享值更新不改变规则/流程内容 Hash、原成功回放先于现值资格检查、旧冻结/发布/字典/删除/历史快照与并发均随全量通过。修复了幂等层遗漏新业务请求 Hash Schema 的保存问题。BFF 的 734 项 Risk 回归及后续变量/流程边界 13 项通过（有重叠，不相加）；Web 913 项测试及生产构建、模型/Schema 32 项通过，这三个应用/模型检查沿用本阶段未被后续改动影响的证据。本次数据库用例结束后清理验证通过。详见[研发证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage4/verification.json)及[阶段 4 交付材料](../requirements/20260912/operations-console-统一风控变量管理与条件编排/stage4.md)。该阶段成果已获用户确认；阶段 5 增量验证见下文，开发回归不等同于 test 部署或业务验收。
- **综合验证停点（阶段 5 完成）：** 真实 Web/BFF/Risk 连接本机 MySQL/Redis；Platform 登录/权限使用 HTTP Simulator、导航/入口使用本机适配器，未验证实际 Gateway Snapshot 或启用 Platform 注册。已完成动态变量发现、两处条件引用、共享值更新、历史只读和权限拒绝检查，修复中文搜索 503、集合保存后旧版本读取误报、只读详情值/状态仍可操作三项问题；增量 Risk 18/18、Web 13/13 及最终生产构建通过。独占 Fixture 已清理，17 变量/9 成员保持，5 类执行事实为 0。详见[阶段 5 报告](../requirements/20260912/operations-console-统一风控变量管理与条件编排/stage5.md)及[证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage5/verification.json)。等待用户确认进入阶段 6 发布准备；未部署 test、迁移示例或提交代码。
- **本机 SQL 已授权并应用（2026-09-13）：** 用户对精确本机应用清单回复“可以”。在 `desktop-linux/mor-local-mysql-1/127.0.0.1:13306/mor_risk` 备份 Schema/四个受影响表后执行已审 DDL/DML；备份位置及文件 Hash 见[应用证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage4/local-sql-application.json)。新表 41 列、旧表 5 新列及 17 变量/9 成员核对通过。回归后测试变量 0，原四个受影响表仍为 0 行；[清理证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage4/local-regression.json)。本轮不重复应用增量，未修改 test 或应用 Platform 注册。
- **范围与数量校正：** 需求增量为 2 新表 / 41 字段、4 张旧表新增 5 列并修改 4 列注释、11 索引和 Risk 26 条目录；相比阶段 3 摘要补列了原已批准的 `t_rule_set_version.content_format` 注释更新，不增加数据字段或业务功能。Risk API 工件只安装到本机 Maven 缓存供 BFF 编译，未向制品库发布。既有 test 数据、制品与执行能力未改变，未部署、提交、推送；下一步为确认后进入阶段 5。

- **test 示例核对：** 用户明确要求修复 test 示例，已经 `http://localhost:28080` 的既有管理 API 查询 `DEMO_CHECKOUT_BRANCH_FLOW`（流程 ID `224578384930377728`，第 1 版）、配套 `DEMO_FLOW_AMOUNT_CHECK` 和准确版本引用；两者均可配置，引用未丢失，5 节点/4 连线的静态校验返回 `valid=true`、无问题。金额阈值仍是直接常量 `8000`，节点输出目录仅有 `matched`；当时 test 尚无统一变量能力，不能仅改数据获得最新值引用能力；本期代码仍未部署到 test。核对后用户已明确预期为统一变量引用；两个示例的迁移已纳入 RCO-19 范围，须待本期变量配置能力落地后实施。该次只读核对未保存新业务版本或写 SQL，核对会话已退出。上述有限核对不代替 UI 或运行执行验收。

- **阶段 2 分析交付：** [差异与系统影响](../requirements/20260912/operations-console-统一风控变量管理与条件编排/technique.md)已完成，识别固定目录、单来源操作数、节点输出、动作字段、历史/Hash、引用查询及刷新缺口。建议改 Risk/BFF/Web 三应用与 Platform 注册 DML，Gateway/Foundation 暂无必要代码改动；以变量定义+集合成员两表进入方案设计，非已批准 Schema。仅修改需求/目录/状态材料并做静态检查，未改应用或模型、未新运行 test 写入/应用测试，该轮尚未进入阶段 3；用户随后确认后完成本次方案，当时研发/部署/示例迁移尚未执行，现行进展见本节上方记录。

- **阶段 3 方案交付：** [方案入口](../requirements/20260912/operations-console-统一风控变量管理与条件编排/design.md)包含9新增操作、9原合同改动/3门禁、2新表41字段、3旧表新增5字段并改3列注释、11索引、Risk26行/Platform113行及1角色版本更新的评审稿；不是已应用SQL。Pencil在Operations原文件新增7画板及PNG/PDF，原40画板保留，Checkout设计未改。2026-09-12 用户回复“可以，基本没什么问题”，确认方案并授权进入阶段 4；SQL 应用、部署、迁移与提交仍需各自阶段授权。
- **阶段 4 初始失败证据：** 变量模型测试先确认缺表/缺列，同步后模型/Schema 32 项通过；真实 Risk HTTP 新目录入口用例先得到 404，证明原入口缺失。后续编译、纯合同与 BFF/Web 开发检查见本节最新回归停点；本轮随后获得独立本机 SQL 授权，数据库回归已完成，旧需求 SQL 授权未复用。
- **设计验证：** check-design.mjs核对SQL现行列名、DDL/注册行数、变量依赖、10条件样例及7错误结构、包内链接和7个导出图；Pencil检查7画板无越界/未完成标记。需求目录/全局时序语法与本轮差异检查结果见delivery.md。这些设计检查本身不代表应用测试、数据库执行或真实运行能力验证；本期实际研发检查另见本节开发回归停点。

<a id="commerce-customer-20260912"></a>

## COM-CUSTOMER-01 Commerce Customer Checkout（2026-09-12）

- **授权与范围：** 用户要求“先完成 Commerce 的接口及功能，外部接口先 Mock + TODO”，并明确允许备份后执行本机 SQL。实现边界见[需求确认记录](../requirements/20260912/commerce-CustomerCheckout接口设计/delivery.md)、[实施说明](../paytech/acquiring/docs/requirements/20260912-customer-checkout/implementation.md)和[OpenAPI](../paytech/acquiring/docs/api/customer-checkout.openapi.json)。本节承接 Commerce 当前状态；上方 COM-PRECHECK-01 的测试数字和“支付准备未实现”属于该较早切片的证据。
- **Implemented：** 新增轻量 `paytech-commerce-customer-api`；Customer 加载/轮询、资料保存、支付准备三操作，以及 Payment 内部访问校验、法律确认、原确认查询、COM-I10 readiness 四操作均有 Controller、Processor、业务 Service 与持久化实现。所有 Processor 继承 `AbstractGatewayProcessor`；准备补偿由独立 `CheckoutPreparationHandler` 编排。Customer 不接收版本字段，三个法律文档返回链接；Payment 的公共单请求卡支付入口归 Payment，不在 Commerce 中创建。
- **事务与恢复：** 所有 Owner 查询、Protect/Reveal、合同生成与 Payment 操作均在数据库事务外。原准备身份固定 OE 选择，选择先落库再调用 Risk/Tax；完整 Context/法律/税事实提交后记录 Intent 首发标记，再以原键创建。响应丢失、本地绑定回滚均恢复原创建；取消/到期在首发后必须取得 Payment 对原创建身份的终止证明。确认在一个本地事务内保存三份不可变接受、同 Order/Session 的证据及稳定回执；Commerce 返回后不回调 Payment。查询不更新支付事实。
- **外部边界：** Vault、CCT 最终选择、Risk、Finance Tax、Platform 文件和 Payment 的新能力以 Commerce-owned 业务端口表达，生产默认明确报依赖不可用并保留 TODO。状态型 Mock 只在 test 模块，未将合成成功逻辑加入主应用制品。既有创建预检继续走真实 HTTP Client 到测试 CCT Simulator；其余新端口测试不声称远程 Wire 已接通。
- **Verified：** 2026-09-12 20:37:13 +04:00，JDK 25 下完整 `./mvnw clean verify` **323/323**，0 failures/errors/skipped，JaCoCo 行覆盖率 **92.04%（3043/3306）**，原 90% 门槛不变。新增 Customer HTTP 12 项、独立 Handler 9 项、默认端口配置 1 项，覆盖真实本机 MySQL、并发/语义幂等、资料锁定、三份接受及原回执、过期/取消、Tax 失败后保持 OE、Intent 响应丢失与真实事务回滚。Owner Mock 对每次调用检查不存在数据库事务。Commerce 模型 **17/17**、生成器 `--check`、7 操作 OpenAPI JSON/本地引用、差异空白检查通过；未将其他系统或完整需求站测试计入本批验证。
- **本机数据库：** 仅向 `mor-local` 的 `127.0.0.1:13306/mor_commerce` 手工执行[三表增量 SQL](../paytech/acquiring/docs/requirements/20260912-customer-checkout/ddl.sql)，增加 8 列、放宽 5 列可空约束；已用 `information_schema` 核对全部 13 项。DDL 不更新历史数据，保留旧 Scope/Assignment 列，应用负责 TERM_PRICE 新写完整性；兼容旧库不等同完整目标 Schema。PDManer、生成源及完整新库 SQL 同步 `consumer_reference` 可空，目标统计保持 15 表/249 字段。未操作 `mor-test`。
- **备份与证据：** 原三表结构和执行 SQL 保存在本机受限目录 [/Users/kenny/.codex/backups/commerce-customer-20260912](/Users/kenny/.codex/backups/commerce-customer-20260912)，SQL SHA-256 为 `f47bc4f38e81622a3facc01eeeca7582509cc986494f2799b6875ad5546b4f5d`。完整构建、JaCoCo、模型及启动日志保存在 [/Users/kenny/.codex/verification/commerce-customer-20260912](/Users/kenny/.codex/verification/commerce-customer-20260912)，不写入仓库。
- **本地联调与待接入：** `tools/run-customer-checkout-mock.sh` 已实际启动；健康检查返回 UP，Customer 未知 Token 返回标准 Problem 404，验证进程已关闭。该入口使用本机库与内存合成 Owner，文件链接并非真实文件，远端 Mock 状态不会跨进程保留。真实 Owner HTTP/MQ、PaymentSucceeded 消费、COM-I13 restricted risk wire、Payment Card/NI、Gateway 和前端联调仍待后续切片，不能据本批测试宣称真实支付闭环。未修改 Payment/Risk/Gateway/前端应用代码，没有 Git 提交、推送、制品发布或部署。

- **提交前复核（2026-09-12）：** 用户授权将 Commerce 全部代码与其格式化一同提交。格式化后 JDK 25 完整 `clean verify` **323/323**，0 失败/错误/跳过；JaCoCo 行覆盖率 **91.90%（2971/3233）**，行数变化来自格式调整，原 90% 门槛保持。需求站完整构建与 **136/136**、Commerce 模型 **17/17** 通过。按任务边界提交 Commerce、相关需求/规范及模型脚本，Risk 决策流的独立改动留在工作区；本轮未推送或部署。

- **GET 分层重构（2026-09-12）：** （前序记录：读事务与全量加载策略已由后续按需查询重构取代。） `CustomerCheckoutApi#getCheckout` 的查询输入独立为 `GetCheckoutQuery`，`CheckoutQueryContext` 复用本库对象与外部查询结果，HTTP 嵌套对象统一为 `Info`，消费者响应不复用保存请求体。`CustomerCheckoutService.getCheckout` 自身使用 REQUIRED 读事务；独立调用创建只读 REPEATABLE_READ 事务，已有事务则继承其设置。Processor 在两次本地事务之间补齐外部展示数据，并保留两轮版本重读与无副作用语义。原四表订单查询拆为三表查询加单表创建事实查询，共享 Orders Repository 同步装配原有返回对象；不改数据库模型。JDK 25 完整 `clean verify` **328/328**，0 失败/错误/跳过，JaCoCo 行覆盖率 **91.95%（2980/3241）**；新增 5 项独立 GET/Service 验证覆盖 SQL 表数、Info 合同、并发快照、加入已有事务与重试上限。7 操作 OpenAPI 解引用后与原合同形状一致，需求站完整构建与 **136/136** 通过，用户原 Converter import 排序已保留。本轮不执行 DDL、提交、推送或部署；[本机验证证据](/Users/kenny/.codex/verification/commerce-get-checkout-refactor-20260912)。

- **Customer 按需查询重构（2026-09-12）：** 按用户要求移除 Customer 普通读取的显式事务与 Repository 全量 `load`。GET 在 Processor 中顺序读取必要事实，末尾仅重验 Token/Order/Session/Consumer/Payment 版本，保持最多两轮重读；冻结后不再读取创建快照。保存、支付访问、确认回查及 Readiness 使用各自的状态/绑定查询；准备响应不读商品、消费者详情或 Payment 展示概要，Handler 按阶段恢复且写入仍保留短事务与锁内重证。真实 SQL 观察断言已准备 GET 不超过 **8 条 SELECT**、商品与冻结详情各一次（前序全量重读为 14 条），所有单条查询最多三表，普通查询不启事务，无关查询不发生。JDK 25 完整 `clean verify` **331/331**，0 失败/错误/跳过，JaCoCo 行覆盖率 **91.81%（3026/3296）**，90% 门槛保持；Customer HTTP、独立准备 Handler 及既有 Client Orders/Refund 回归通过。变更限 Commerce 查询链和实现记录，不改数据库模型或公开字段，不执行 DDL、提交、推送或部署；[本机验证证据](/Users/kenny/.codex/verification/commerce-demand-query-refactor-20260912)。


- **按需查询提交复核（2026-09-12）：** 用户授权提交，Commerce 的 46 个相关文件（含格式调整）已提交为 `dedebb2`。提交前 JDK 25 完整 `clean verify` **331/331**，0 失败/错误/跳过，JaCoCo 行覆盖率 **91.78%（3027/3298）**；最新 import 整理后 `test-compile` 通过，需求站完整构建与 **136/136** 通过。根状态文档只提交 Commerce 片段，Risk 等独立任务改动保留在工作区；未推送、发布或部署。新增日志位于同一本机验证目录的 `precommit-verify.log`、`precommit-compile.log` 和 `precommit-requirements-site.log`。


<a id="payment-application-foundation"></a>

## Payment 应用基础工程（2026-09-12）

- **Implemented：** `payment-orchestration-service` 新增标准 `paytech-payment-api / web / test` 三模块、Maven Wrapper 3.9.16 与统一 Parent/BOM、Java 25 启动入口。Web 复用公共 MVC Request ID/Problem/元信息日志，只开放 Health/Info/Prometheus，默认监听 `127.0.0.1:18103/payment`。详见[运行说明](../paytech/payment/README.md)与[本次需求](../requirements/20260912/payment-应用初始化/requirement.md)。
- **Verified：** 完整 `clean verify` 14/14，0 失败/错误/跳过；真实 main/HTTP 验证健康探针、管理端点白名单、统一错误与日志边界。可执行 JAR 默认端口 8 路径 smoke 通过，测试进程已停止；制品无测试及数据库/中间件依赖，Test 不打包。需求站 136/136；JaCoCo 只覆盖启动类 3/3 行，不能外推到尚未实现的业务。详细证据见[交付记录](../requirements/20260912/payment-应用初始化/delivery.md)。
- **Deployed：** 无。仅本机短时验证，未提交、推送或发布制品。
- **Pending：** Payment 业务、Card、渠道适配、外部 Owner 集成与持久化随后续用例实施。本次没有修改 Commerce、Payment PDManer/Schema，也没有执行 SQL 或添加 Gateway 配置。

<a id="payment-channel-terminology"></a>

## 支付渠道命名统一（2026-09-13）

- **Implemented：** 按[Channel 决定](./decisions/2026-09-13-payment-channel-terminology.md)同步现行支付渠道术语、Payment/Finance/Risk 生成源、PDManer、完整人工 Schema、模型/场景测试及 Owner 文档；CCT 模型只改支付相关注释，Commerce 模型不变。Platform、Gateway、Operations BFF/Web 的 Route Channel、Security Profile、Authorization Mode 使用 `CHANNEL_CALLBACK`。Payment 四业务模块 Owner 映射与 Card 接口/字段草稿同步命名，没有创建空业务 JAR 或实现 Card 交易。
- **Verified：** 对改名前六份工作区模型逐项比较，内部 ID、图形连线和所有非命名结构保持；批准的 23 张表名、79 处列名、51 个索引名逐项匹配且无碰撞，新物理名称均不超过 32 字符。模型字节与独立结构摘要按比较后的结果更新，外部业务 ID、金额、状态机和幂等身份不变。
- **Verified：** JDK 25 Gateway `verify` 1,199/1,199、Operations BFF `verify` 1,052/1,052；Platform 编译器与枚举归属测试 22/22；Operations Web 900/900 与生产构建；需求站 136/136 与构建通过。Java 交叉仓库验证使用临时的第一方 Maven 缓存，未覆盖开发者共享制品。
- **Known verification gaps：** 全量 PDManer/场景测试 533 项中 518 通过、15 失败；在隔离副本恢复改名前生成源、模型与文档后，同样复现这 15 项，没有新增失败。它们属于 Finance 旧 `operating_entity_id` 断言、Risk 旧 72/74 表清单、旧 Operand 字段及初始化 DML 清单。Platform `GatewaySnapshotHttpAutoTest` 因未注入 `PLATFORM_SUPPORT_TEST_DB_PASSWORD` 而无法取得本地数据库保护，未完成该 HTTP/数据库验证；未读取或改写 `.env.local`，未修改数据库结构或环境配置。
- **Not deployed / Pending：** 本次没有执行任何数据库迁移、配置真实渠道、部署、发布、提交或推送。运行环境仍需分别核对旧表/列/索引、Finance 目录及既有 Journal/Hash、Risk 争议来源、Gateway Snapshot/回调枚举的切换；历史原报文与外部固定字段保留。Payment 业务执行与 Card 仍按各专项推进，不能从命名完成推断支付能力上线。

命令、基线对照与保留项说明见[交付记录](../requirements/20260913/payment-支付渠道命名统一/delivery.md)。


<a id="payment-business-modules"></a>

## Payment 四业务模块工程拆分（2026-09-13）

- **Implemented：** 在原 api/web/test 基础上加入 `paytech-payment-execution / routing / channel / card` 四个普通 JAR，形成七子模块 reactor；父 POM 管理内部版本，Web 作为唯一应用组合根直接依赖 API 和四业务模块，Test 只直接依赖 Web。各业务 JAR 有根包说明与 Maven Enforcer 依赖隔离规则。范围依据见[本次决定](./decisions/2026-09-13-payment-module-scaffolding.md)；[模块方案](../requirements/20260913/payment-业务模块划分/technique.md)维护业务职责与表 Owner。
- **Verified：** JDK 25、Maven Wrapper `clean verify` 19/19，0 失败/错误/跳过：原 14 项应用真实 HTTP 回归及 5 项模块边界测试。拆分前的新门禁因仅有三模块而失败；拆分后通过。隔离副本加入 `Execution → Channel` 依赖后，Enforcer 明确报告该 JAR 被禁止，未改主工作区或共享制品。
- **Verified：** Web `-exec.jar` 含 API 与四个普通业务 JAR，不含测试制品；业务 JAR 不含独立启动入口。实际打包应用以随机 Loopback 端口完成 Health/Liveness/Readiness、Info、Prometheus 和未实现 `/payments` 的六路径检查，Request ID 保持，测试进程已停止。30 张当前模型表与架构测试的唯一 Owner 清单完全对应，Payment 数据库工件与本轮开始时逐字节一致。
- **拆分阶段的边界：** 当时四业务模块为工程骨架，没有可搬迁的既有业务类；后继 Execution 研发另见下一节。路由、资金 Action、跨模块事务、回调重放、Card 加密/权限与 NI 集成仍按各业务专项实现并验证；模块目录与同进程 JAR 不是运行时卡数据隔离证明。
- **Not deployed：** 本轮未执行 SQL、配置基础设施、部署、发布、提交或推送。业务数据库和跨系统合同未改变。

可复现构建命令与原始证据见[服务 README](../paytech/payment/README.md)和[交付记录](../requirements/20260913/payment-业务模块划分/delivery.md)。

<a id="payment-execution-implementation"></a>

**2026-09-17 查询 Intent 接口（当前增量）：** 新增 EX-I02 `GET /payment-execution/v1/intents/{intentId}?clientId=...`，响应为 `intentId/sourceType/sourceId/status/activeAttemptId/successfulAttemptId`。入口使用 Hibernate Validator 校验两个 ID；Controller/Converter 只做协议转换，Execution 的 `GetIntentProcessor` 复用 `PaymentIntentRepository.findIntent` 并核对 Client 归属，从同一条记录组装响应。查询成功返回 200 与 no-store，不存在或跨 Client 返回 404。纯单表查询没有新增 Context/View、Service、锁、事务或 SQL，也不推进到期或支付状态；来源字段为创建时冻结的业务身份。合同见[查询接口修订](../requirements/20260913/payment-Execution设计/get-intent-amendment.md)。

**Verified / Pending：** JDK 25 下当前 Foundation 与 Payment 源码临时 Reactor 的 `-pl :paytech-payment-web -am package -Dmaven.test.skip=true` 14 个模块全部通过，日志 `/tmp/payment-get-intent-build.log`；Payment 差异空白检查及 4 份文档的 107 个本地链接检查通过。已核对公共示例第 11 节的最小合同、模块边界、归属检查与仓储复用；写入、重放、锁及事务项不适用于本只读接口。需求站检查仍为 133/137，4 项 Commerce 文档/台账失败与上轮相同，日志 `/tmp/payment-get-intent-doc-tests.log`。沿用当前用户暂缓单元测试的安排，未新增或运行 Java 单元测试；真实 HTTP 校验与数据库联调待验证。未执行数据库操作、提交、发布或部署。

**终止响应 HTTP 200 修订：** 按用户要求，terminateIntent 的所有正常业务决定（含 PENDING）统一返回 200，由上游判断 decision；Controller、API 注释和接口文档同步，错误仍使用原 4xx/5xx。JDK 25 当前源码临时 Reactor 的 14 个模块 `package -Dmaven.test.skip=true` 通过，日志 `/tmp/payment-terminate-http200-build.log`；差异空白检查通过。需求站构建通过，仍为 133/137，4 项既有 Commerce 文档失败未变化，日志 `/tmp/payment-terminate-http200-doc-tests.log`。未新增或运行 Java 单元测试，未提交或部署。

**2026-09-16 终止 Intent 接口（当前增量）：** 用户确认安全终止边界后要求实现，并明确暂不编写单元测试。新增 EX-I04 `POST /payment-execution/v1/intents/{intentId}/terminate`，使用 class Body、Hibernate Validator、Converter、Execution 内 `TerminateIntentProcessor` 与直接持有 Intent/活动 Attempt 的 `TerminateIntentContext`。复用 Foundation TRY_LOCK，与 pay 共用 Intent Key；根据当前状态返回 `TERMINATED / ALREADY_TERMINATED / PENDING / REVERSAL_REQUIRED / ALREADY_PAID`，响应仅 `intentId/status/decision`。安全终止由既有 PaymentExecutionService 的一个短事务更新 Intent，SQL 校验原版本、OPEN、两个 Attempt 指针为空，成功后仓储同步原对象。既有终态直接返回，活动尝试未创建 Action 仍为 PENDING，未释放授权不被改成 CANCELLED。完整合同与范围见[终止接口修订](../requirements/20260913/payment-Execution设计/terminate-intent-amendment.md)。

**Verified / Pending：** JDK 25 临时 Reactor 使用本地 Foundation 与 Payment 当前源码，`-pl :paytech-payment-test,:paytech-payment-channel -am clean package -Dmaven.test.skip=true` 的 16 个模块全部通过，日志 `/tmp/payment-terminate-build.log`；没有使用旧 Foundation 锁 JAR 代替当前源码，也未执行 install 或发布。需求站构建通过，137 项文档检查中 133 通过、4 项既有 Commerce 合同/模型台账断言失败，与此前公共接口示例任务的失败一致，日志 `/tmp/payment-terminate-doc-tests.log`。按用户要求未新增或运行 Java 单元测试，真实 HTTP/MySQL/Redis 并发与回滚仍待验证；不声称该接口已联调或部署。没有新增 Schema、执行数据库操作、增加后台终止任务或真实 Commerce/Channel/MQ 接入，未提交、推送或部署。

**2026-09-16 Foundation Processor 模式锁与 Payment 迁移：** `AbstractLockProcessor` 按 `LockCommand.mode` 单次获取锁：LOCK 持续等待、TRY_LOCK 按指定等待时间尝试，时间单位统一为毫秒，省略租约或租约为 -1 时使用 Redisson Watchdog。移除先 lock 再 tryLock 的重复获取、未使用的 LOCK 等待时间重载及重复 common-core 依赖；中断恢复线程中断标记，未获锁不执行业务，释放失败不覆盖业务结果。公共 `AbstractProcessor.process` 统一拒绝调用方已有事务，ApplyCardInstrument 删除重复检查；Pay 迁移到新父类并保留立即尝试，CreateIntent/ApplyCardInstrument 保留用户选择的等待模式。写接口规范、配置说明及调用链同步更新；Service 编排和数据库事务范围未改变。

**Verified / Pending：** JDK 25 下将 Foundation 与 Payment 当前源码组成临时 Reactor，`-pl :paytech-payment-web -am clean package -Dmaven.test.skip=true` 的 14 个选中模块全部通过，日志 `/tmp/payment-mode-lock-build.log`。未使用旧本机 JAR 代替本次修改的 Foundation 模块，未执行 Maven install 或制品发布。未新增或运行 Java 单元测试；未进行真实 Redis 并发、租约及中断验证（当前进程未提供本地 Redis 测试凭据），未执行数据库测试、SQL、提交或部署。

**2026-09-16 工具申请由 Processor 组合 Service 事务：** `ApplyCardInstrumentProcessor.saveContext` 使用一个 `TransactionTemplate`，依次执行 CardService 本地申请、Card 结果绑定及 PaymentExecutionService 的 Token 保存；后者移除该方法内部的事务包装，直接参与调用方事务。Service 之间没有新增调用，Card Context 仍不进入 Execution 的持久化 Context 或 Repository。申请入口在公共锁模板前检查调用方无活动数据库事务，原提交重放在保存阶段前返回。规范与申请合同同步为“跨 Service 的顺序和原子事务由 Processor 组合”；Card 仍为无外部访问、无真实存储的合成 Mock，需要外部准备时再由 Card Processor 在事务外完成。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过，日志 `/tmp/payment-processor-transaction-build.log`；事务归属、两个 Service 调用顺序、原提交退出点、入口事务检查、无 Service 依赖其他 Service、卡输入边界及 36 个文档链接/锚点静态检查通过，Payment `git diff --check` 通过。未编写或运行 Java 单元测试、真实 HTTP/数据库回滚测试；Card 尚无真实卡记录写入，不能据此宣称 Card 与 Token 两张真实表的原子性已通过联调。未执行 SQL、提交或部署。

**2026-09-16 Payment 配置归集：** 补齐用户创建的 PaymentExecutionProperties，以 `payment.execution.instrument-preparation-window` 管理工具申请窗口；Card Mock 与实例 ID 分别归入 PaymentCardProperties、PaymentIdentifierProperties。移除 Payment 业务源码中的分散 `@Value`，窗口与 Mock 开关通过配置对象读取，原 Token 期限不受配置修改影响。Web 使用 BOM 管理的 Nacos Config Starter，`nacos` Profile 通过 `spring.config.import` 加载可选共享配置和服务配置，默认本地运行仍关闭 Nacos。完整配置项、旧键迁移、动态绑定与重启边界见[配置清单](../paytech/payment/docs/development/configuration.md)，可审阅内容见[服务模板](../paytech/payment/config/nacos/payment-service.yaml)。模板不包含密码或共享实例 ID 坐标，未上传 Nacos 或改变其他服务。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过，日志 `/tmp/payment-nacos-configuration-build.log`；独立 Spring 配置上下文验证本地 YAML 加载、默认值、实例 ID 绑定及两次 EnvironmentChangeEvent 重新绑定通过，非法 Duration、缺少节点编号在启动绑定时失败，日志位于 `/tmp/payment-config-check/`。该验证不启动业务应用或连接真实基础设施；Nacos 服务端发布、真实推送刷新及 HTTP/数据库回归未运行。未新增或运行仓库 Java 单元测试，未执行 SQL、提交或部署。

**2026-09-16 applyCardInstrument 完整受理链（当前增量）：** 在前一轮 API/仓储基础上补齐 Execution 的 ApplyCardInstrumentProcessor、内部 Request/Response、持有 Intent/Token 的 Context、Converter 与 PaymentInstrumentController。入口为 `POST /payment-instrument/v1/card-tokens`，按原 Intent 资源锁串行处理，原 Client/Intent/来源提交工具先核对方式与 Card 身份后返回；首次校验 Intent、来源提交和配置窗口，事务外调用 Card，再由现有 PaymentExecutionService 一次事务保存 Token。Token 由 SecureRandom 生成，期限受 Intent、配置窗口与 Card 上限约束；不创建 Attempt/Action 或独立幂等记录，不修改 Schema。Execution 单向依赖公开 CardService，卡输入只经过专用申请请求交给 Card，不进入通用 pay、持久化 Context、SQL 或日志。详细范围及前序合同取代关系见[申请工具修订](../requirements/20260913/payment-Execution设计/apply-card-instrument-amendment.md)。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过，日志 `/tmp/payment-apply-card-instrument-build.log`；Token 12 列与 Schema/SQL 参数、Mapper 方法、主流程顺序、单事务和 Context/卡输入边界、Maven XML 及 18 个文档链接静态核对通过，Payment `git diff --check` 通过。Card 目前只有显式启用的合成 Mock，默认关闭，只接受文档指定的 CARD_TEST 和测试卡，不保存 PAN/CVV 或真实材料；首次工具申请窗口须显式配置为正值。真实 Card 加密、材料领取/清理、HTTP/MySQL/Redis 联调及资金能力未完成；未新增或运行 Java 单元测试，未执行 DDL/DML、提交或部署。

**2026-09-16 CreateIntent 校验与注释评审：** 保留用户新增的 `validate` 钩子，将两项 Partner 定价版本的成组判断放在锁前；有效期资格移回未查到原 Intent 后的新建分支，避免已到期原请求在查询前被拒绝。保留用户 `createIntent` 的 `@param/@return` 与 HTTP 200 注释格式，补充[写接口规范](../paytech/payment/docs/development/processor-conventions.md)第 4.4、6.1 节及校验表、开发/评审清单，并同步创建合同的阶段顺序。JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过，日志 `/tmp/payment-create-intent-validate-build.log`；两段新增规范示例与源码、校验分支位置和 21 个文档链接静态核对通过。未运行 Java 单元测试或真实 HTTP/数据库/Redis 验证，未执行 SQL、提交或部署。

**2026-09-16 创建 Intent 接口（当前增量）：** 按当前 [Payment 写接口规范](../paytech/payment/docs/development/processor-conventions.md)补齐 EX-I01 `POST /payment-execution/v1/intents`，使用 class Body、Converter、Execution 内 CreateIntentProcessor 和持有 PaymentIntent 的完整 CreateIntentContext；扩展既有 Service、Intent Repository/Mapper，一次本地事务只插入 Intent。创建以来源类型和来源 ID 为请求身份，沿用 AbstractApiLockProcessor 锁定来源、来源唯一索引防重；原记录比较 Client、金额/币种及全部冻结输入后返回当前状态，原记录过期或终态不受首次创建期限检查阻断。新记录 OPEN、version=1、两个 Attempt 指针为空，金额复用 Foundation Money；SQL 限制落库时仍未到期。请求字段和仅 intentId/status 的 200/no-store 响应见[创建接口修订](../requirements/20260913/payment-Execution设计/create-intent-amendment.md)。没有额外幂等 Header、命令表、Binding、状态历史、路由或 Action 写入；现有 pay 不变。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过，日志 `/tmp/payment-create-intent-build.log`；静态核对 Intent 20 列与正式 Schema、插入参数逐项一致，来源唯一索引、Mapper 方法、Context 持久化边界及 35 个文档链接通过，Payment `git diff --check` 通过。未编写或运行 Java 单元测试，未进行真实 HTTP/MySQL/Redis 联调，未执行 DDL/DML、提交或部署。创建接口未提供自动到期推进；Commerce 真实适配、CCT 资格、工具准备和真实资金 Owner 仍按后续范围接入。

**2026-09-15 pay 移除公共命令幂等（当前增量）：** 按用户“先不管锁，帮我去掉这个幂等”的要求，Payment Execution/Web 移除 paytech-command-idempotency 依赖和自动配置；删除 Hash、Claim、Lease、完成记录及 Context.commandClaim/newAttempt。HTTP 保留 submission:<sourceSubmissionId> 的固定 Header 合同，内部只传来源提交 ID。Processor 按 Attempt 来源提交读取并核对 Client/Intent、方式、捕获模式和原工具，匹配后直接响应；新受理事务仅插入 Attempt 并 CAS 绑定 Intent。重复键或受理 CAS 冲突在保存事务回滚后重新查询原提交，匹配才返回，不存在则抛出原异常；只有新增成功的请求推进 Action。当前流程与前序公共包接入的取代范围见 [pay 阅读说明](../paytech/payment/docs/pay-interface-walkthrough.md)。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过。未新增或运行 Java 单元测试，未执行 SQL 或真实 HTTP/数据库并发验收，未增加锁、提交或部署。Foundation/Commerce 不变；Payment 现有 Mapper、PDManer 和 Schema 保留，旧 t_command_idempotency 已无运行时读写但尚未清理，旧公共表列迁移不再是 pay 启动前置。受理提交后到首 Action 创建的中断恢复仍沿用原 TODO，本次不扩展执行流程。

**2026-09-15 Attempt 授权与 PAY 完成更新（当前增量）：** 按用户拆分后的 Mapper 接口补齐 authorizeCompleted/payCompleted SQL 及显式 @Param。授权完成更新 authorized_amount，保持原状态及 captured_amount；PAY 完成按当前 Repository 语义同时记录 authorized_amount/captured_amount，并写入目标 SUCCEEDED 状态。两者均按 Attempt ID、当前 Action ID、原状态和版本匹配，更新时清空 active_action_id、递增版本及更新时间；Repository 影响一行后同步清空对象指针。仅调整三个现有生产文件，沿用用户当前 Actor/Flow 调用结构。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；静态核对两条更新的字段、条件及 Repository 更新成功后回写顺序，`git diff --check` 通过。未编写或运行 Java 单元测试，未执行 SQL 或真实数据库回滚/并发验收，未提交或部署。

**2026-09-15 Action 失败公共处理（当前后继）：** 沿用用户在 ActionFlow 增加的 FAILED 提前结束分支，补齐 PaymentAttemptRepository.actionFailed 及 updateFailed SQL：按 Attempt ID、原版本、PROCESSING 和当前 Action ID 更新为 FAILED、清空执行位、递增版本及更新时间，已有金额不变；影响一行后同步对象。PaymentIntentRepository.attemptFailed 复用既有 completeAttempt，按版本和活动尝试释放占位并保持 OPEN。两次写入与 Action 结果同事务；Pay Actor 移除重复失败处理，评估成功但 DENY/REVIEW 继续由风控 Actor 处理。当前范围仍仅 CARD + PAY，后续 Capture/Void 失败语义不据此推导。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；静态核对 updateFailed 映射、原状态/版本/动作条件、金额保留及更新后对象同步。用户 Flow、转移定义、ActionActor 接口、PayContext 和各 Owner 调用 TODO 与本轮起点一致。未新增生产类，未编写或运行 Java 单元测试，未执行 SQL、真实数据库回滚或并发验收，未提交或部署。

**2026-09-15 End 业务结果归 Actor（当前后继）：** 按用户确认，PROCESSING/UNKNOWN 仅保存 Action 后返回，不更新或额外校验 Attempt。ActionFlow 在有后继时创建动作并切换执行位，到 End 时调用当前 Actor.doInTransaction；CardPayFlow 移除业务结果 switch，只保留转移定义。AuthorizeActionActor 成功记录授权金额并保留 Attempt PROCESSING；PayActionActor 成功记录收款金额并完成 Attempt/Intent；本次 CARD + PAY 的拒绝、转人工或明确失败由对应 Actor 结束 Attempt 并释放 Intent 活动尝试。Attempt Repository 的 finishAction 以原版本和动作 ID 一次保存目标状态、金额并清空执行位，更新一行后才同步对象；相关写入加入 Flow 的结果事务。Context 不进入持久化层，没有新增生产类。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；最终制品的三种 End 目标结果及两种指针参数组合完成真实 MyBatis TypeHandler 离线绑定检查。静态对照确认转移定义、四个 Owner 调用 TODO、用户当前 PayContext/PaymentAction/PayProcessor 与本轮起点保持，未决分支无 Attempt 操作。未编写或运行仓库 Java 单元测试，未执行 SQL 或真实事务回滚、并发验收，未提交或部署。资金引用获取、校验和持久化仍待真实资金 Actor 接入；前序 CardPayFlow 负责业务结果及未决分支更新 Attempt 的描述由本增量取代。

**2026-09-15 ActionFlow 终态判断修复：** execute 仅对非终态动作调用 Actor；completeAction 仅对非终态结果保留执行位并停止，终态继续匹配下一动作或 End。保留用户的 ActionStatus.isFinal() 实现，生产代码仅修正两处取反条件；End 仍由具体 Flow 决定 Attempt 状态。JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；未运行 Java 单元测试、操作数据库、提交或部署。

**2026-09-15 ActionResult 完整结果回写（当前增量）：** saveResult 的九个业务结果字段均由 ActionResult 提供；原 PaymentAction 仅提供 ID、旧状态和版本匹配。Mapper 更新一行后，Repository 才同步全部结果字段、版本和更新时间；SQL 异常或行数失配路径不改对象。ActionResult 复用现有模型的结果列，没有新增表字段或业务类，可空结果为 null 时写入 NULL。JDK 25 全 Reactor生产 `clean package -Dmaven.test.skip=true` 通过；最终制品四组有值/空值与原状态组合的 Mapper 绑定离线通过，静态核对全部对象赋值位于成功更新之后。未运行仓库 Java 单元测试或真实数据库回滚/并发验收，未执行 SQL、提交或部署。

**2026-09-15 ActionFlow.execute 补齐（当前增量）：** 沿用用户的 ActionActor/ActionResult 和 ActionFlowFactory 重构，Flow 负责事务外执行、结果事务与提交后推进。非终态保存并保留原执行位；终态结果、下一动作、卡支付资金引用/金额/Intent 和执行位同事务提交。CardPayFlow 处理业务结果，公共 Flow 不校验风控/资金字段。修复 ActionResult.status 未写入、Mapper action/actionActor 参数失配和 selectById 空实现，结果更新后由 Repository 同步原对象；Flow Key 使用工具类型以匹配 CARD:PAY。保留原提交补绑定不进入执行的边界。Actor 异常按用户新语义返回 UNKNOWN，保存成功后源代码路径返回 202 / PROCESSING；未实现真实 Actor 结果校验、Owner 调用或自动恢复。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；最终制品中四个相关 Mapper/XML 装配、八组原状态/返回状态的真实 TypeHandler 参数绑定和 selectById 绑定离线通过，没有执行 SQL。未新增业务类，未写或运行仓库 Java 单元测试，未进行真实 HTTP、事务回滚及并发验收；模型/Schema 未改，未执行数据库操作、提交或部署。执行顺序见[阅读说明](../paytech/payment/docs/pay-interface-walkthrough.md#后续流程的位置)。

**2026-09-15 Action 结果校验归属调整：** 风控评估校验移入 PayRiskCheckAction，资金引用校验分别移入 AuthorizeAction/PayAction；已有 AbstractAction 串联 execute 与 validateResult，并共用状态非空检查。ActionExecutionService 删除集中校验，事务、流程推进和异常保存路径保持。JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；相对修改前的 5 段校验条件、4 个外部能力 TODO 及结果事务方法静态比对通过，没有新增类。未写或运行 Java 单元测试，未执行数据库操作、提交或部署。

**2026-09-15 Action 删除 CREATED（当前增量）：** 用户确认后移除 Execution Action.CREATED；首动作和后继动作构造时即为 PROCESSING/version=1，与 Attempt 执行位创建/切换一起提交。删除两个 Repository、两个 Mapper 及 XML 的 beginAction 和 Service 执行前启动事务，由成功创建/切换的调用直接执行。执行位及结果的原 ID/版本条件保持，Context 仍不进入持久化层。PROCESSING 表示进入处理流程，不证明已提交渠道，也不授权重新执行。保留用户迁入 core/action/flow 的目录与源文件。现行状态为 PROCESSING/UNKNOWN/SUCCEEDED/FAILED，详见[动作修订](../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)。

**Verified / Pending：** JDK 25 全 Reactor 生产 clean package 通过；最终制品四个状态、CARD + PAY 四类动作初态与版本、beginAction 类型/SQL 移除离线核对通过。模型/场景检查 24/24，确定性生成通过；相对本轮起点仅动作状态及 active_action_id 两处字段说明改变，27 表/363 字段及其他结构、Refund/Dispute 状态不变。模型 SHA-256 为 `c73b076f27abef9bb59918365e1fe9559af4cbf675df826e5e70bccc01c18900`；当前空库稿 10 表与正式 Schema 一致，33 个主要文档本地目标通过，见[核对记录](../requirements/20260913/payment-Execution设计/assets/action-no-created-verification.json)。按用户要求未写或运行仓库 Java 单元测试；未验证真实 HTTP/数据库并发、执行 SQL、提交或部署。旧 CREATED 行迁移需单独盘点，实际 Owner 与中断恢复仍 TODO；前序 beginAction/CREATED 执行门禁记录已被本增量取代。


**2026-09-15 Repository 的 Context 边界（当前增量）：** 用户明确 Context 是 Service 层产物，不允许传入 Repository。本轮调整 Attempt 的 activateAction/beginAction/completeAction 与 Intent 的 completeAttempt：Service 提取 DataObject、原/下一动作 ID 和目标 IntentStatus，Repository/Mapper/XML 不再依赖 PayContext。Intent 目标状态判断移回 Service；Repository 在写入成功后继续同步对象指针、状态、版本和时间，没有新增 Input/DTO/Map 包装或改变事务边界。规范见[Processor 约定](../paytech/payment/docs/development/processor-conventions.md#3-context-是本次请求的领域数据目录)。

**Verified / Pending：** JDK 25 全 Reactor 生产 `clean package -Dmaven.test.skip=true` 通过；20 个 Payment 持久化文件中 Context 引用为零。基于最终制品与本轮起点 XML，核对 18 个接口方法签名，四个受影响 SQL 在有/无后继及 Intent OPEN/SUCCEEDED 的 16 种组合下，生成语句与有序绑定值完全一致。9 个主源码/XML 文件调整，PayProcessor、CardPayFlow、ActionFactory 和 PayContext 与本轮起点逐字节不变；见[核对记录](../requirements/20260913/payment-Execution设计/assets/repository-context-boundary-verification.json)。未编写或运行 Java 单元测试，未执行真实 HTTP/数据库测试、SQL 迁移、提交或部署；前轮真实能力 TODO 和恢复边界仍保留。


**2026-09-15 ActionFactory 与直接执行（当前后继）：** 按用户本轮三点要求，ActionFactory 统一管理动作实现；PayProcessor 顺序为保存上下文 → 创建首 Action → 直接执行。受理事务恢复为 Intent/Attempt/公共命令；后续独立事务插入首 Action 并以 Attempt.active_action_id IS NULL、状态和版本条件绑定。执行沿用同一个 PayContext，不再为后台任务重建上下文。执行前核对原动作占位且只允许 CREATED 取得执行权；终态结果、后继 Action 和执行位同事务保存，提交后直接继续。删除 CardPayActionHandler、事件常量、登记调用及补偿包依赖，同时移除无消费者的恢复查询。前轮补偿任务执行及首 Action 与受理同事务的描述由本增量取代；业务 Flow 和状态映射不变。

**Verified / Pending：** JDK 25 全 Reactor 生产 `clean package -Dmaven.test.skip=true` 通过；最终制品包含 ActionFactory，已不含补偿 Handler 或 compensation-event JAR。离线验证 4 个实现分派、重复/缺失实现拒绝，5 个 Payment + 1 个公共命令 Mapper 的 44 次 SQL 展开和指针/CREATED 条件，源码调用顺序及 33 个文档目标通过；PDManer 与 Schema 相对本轮起点逐字节不变。证据见[本轮记录](../requirements/20260913/payment-Execution设计/assets/action-factory-direct-execution-verification.json)。按用户要求未写或运行 Java 单元测试，未做真实 HTTP/数据库并发验证，未执行 SQL、提交或部署。前轮模型/需求站结果仍是各自历史检查，未在本轮重跑。

**剩余边界：** Risk/Card/Channel 仍为未接入 TODO；当前直调会保存 UNKNOWN 后向请求返回错误，受理事务已提交，同键重放只读原 Attempt。保存上下文与建 Action 之间，以及结果提交与执行后继之间发生中断时，当前没有自动补建或续跑。等待/未知动作保留原执行位，回调及恢复接口后续单独实现；没有重新引入资源锁或其他后台执行机制。


**2026-09-15 CARD + PAY Action Flow（当前后继）：** 用户确认后开始动作开发。保留用户 Action/AbstractAction 与已调整的 PayProcessor 风格，统一 ActionType 为 PAY_RISK/AUTHENTICATE/AUTHORIZE/PAY/CAPTURE/VOID/REFUND；仅接入 CARD + PAY。CardPayFlow 用条件对象组合分支，通用 Flow 拒绝零命中/多命中；PayContext 直接持有 Intent、Attempt、工具、原风控/当前/后继 Action 和资金引用。受理事务同时创建首 Action 和公共补偿任务；提交后单步执行，当前结果、下一动作、执行位、资金引用及下一任务同事务保存。Repository 按表拆分，版本/CAS 更新后同步对象；UNKNOWN 恢复保留原未知状态、原 ID 和幂等键，不释放占位。MANUAL 授权成功只结束 Flow，Attempt 仍 PROCESSING；PAY 成功完成 Attempt/Intent。Capture/Void/Refund 只保留类型，未实现入口。设计与历史切换边界见[动作修订](../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)。

**Verified / Pending：** JDK 25 全 Reactor `clean package -Dmaven.test.skip=true` 通过；使用最终制品离线检查 51 个流程分支/异常条件、74 次 SQL 展开，实际 Payment TypeHandler 与 5 个 Payment/2 个 Foundation Mapper 共同加载。模型与场景 24/24、确定性生成检查通过；相对本轮快照仅 8 处字符串同步 PAY_RISK/PAY，仍为 27 表/363 字段/41 UK/43 索引/63 关系/7 条 1:1/7 图，结构摘要未变；模型 SHA-256 为 `a195eed903d3d24b74f0c2d78320b26a6a895a01f84cf898041977566ad9d130`。36 个主要文档本地目标通过；需求站 135/136，唯一失败仍是既有 Risk 模型统计断言不符。本轮未编写或执行仓库 Java 单元测试，未验证真实 HTTP、数据库事务或并发执行；检查证据见[记录](../requirements/20260913/payment-Execution设计/assets/card-pay-action-verification.json)。

**运行限制：** Risk、认证、资金真实能力尚未接入；Action 明确抛出未接入错误，框架保存 UNKNOWN，不合成 ALLOW、assessmentId 或资金成功。Route 仍固定 Mock；真实资金、3DS 交互交付、回调、通知与退款位置创建仍待后续实现。公共补偿默认最多 5 次，任务技术 FAILED 不改变业务未决状态；XXL 扫描默认未启用、未登记调度任务，不能声称进程重启或长期交互恢复已就绪。旧 RISK_CHECK/SALE、旧任务和无首 Action 的历史 Attempt 必须单独盘点切换。未连接数据库、执行 SQL、增加资源锁、修改用户暂存内容、提交、推送或部署；前序只统一枚举、尚未实现 Flow 的记录由本增量精确取代。


**2026-09-15 ActionType Java 类型统一：** 用户要求删除 ExecutionCodes.ActionType，现已移除嵌套枚举；PaymentAction 字段及 PaymentConfiguration 的 MyBatis TypeHandler 注册统一引用用户新增的 core.action.ActionType。保留其当前 PAY_RISK/AUTHENTICATE/AUTHORIZE 三个值，以及用户的 Action、AbstractAction 和 PayRiskCheckAction 草稿，未修改暂存内容。此轮仅统一 Java 类型，未裁定其他业务动作的删除；PDManer/Schema 仍使用前序类型说明，旧 RISK_CHECK/SALE 代码与后续 PAY 等类型需随动作定义定稿同步，未添加旧值别名或执行数据迁移。

**Verified / Pending：** JDK 25 `clean package -Dmaven.test.skip=true` 通过；源码无旧枚举引用，Web 制品无 ExecutionCodes$ActionType，PaymentAction 与 MyBatis 配置均引用新枚举。按用户要求未编写或运行 Java 单元测试，未验证旧 Action 数据读取；未连接数据库、执行 SQL、提交或部署。动作转移、条件对象、执行位事务切换及可靠续跑仅讨论方案，尚未实现。

**2026-09-15 pay 受理前路由（当前调用顺序）：** 用户确认先调用 RouteService、具体选路 Mock。PayProcessor 在 buildPayContext 之后、savePayContext 之前调用 Routing 的窄接口，传 Client/OE/金额/币种/方式/capture mode，将返回的 Channel/Account/MID 填入新 Attempt；命令重放与原提交补绑定不重选。Execution 单向依赖 Routing，未暴露 DataObject；RouteServiceImpl 按本期授权固定返回合成 `9001/9002/9003`，不代表真实渠道有效性。用户最新 intent/attempt/instToken 命名和保存 Service 保留，Mapper、Schema、HTTP 请求/响应与 Hash 未改。规则见[字段修订第 9 节](../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#9-受理前路由)。

**Verified / Pending：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 通过，模块 Enforcer 通过，Web 制品包含 Routing 的四个类型及 Service Bean 元数据。源码增量比对确认 Processor 仅增加路由调用、映射和 TODO，原 Context/Service 与本轮开始时逐字节一致；顺序、六个参数、三个返回字段与旧 Attempt 跳过分支静态核对通过，记录为 `/tmp/payment-route-verification.json`。需求站检查 135/136，仍为既有 Risk 模型统计断言不匹配，日志 `/tmp/payment-route-requirements-test.log`。按用户要求未写或运行 Java 单元测试，未验证真实 HTTP、数据库事务或路由失败场景；未连接数据库、执行 SQL、提交、推送或部署。真实选路及选择证据、后续风控/认证/资金执行仍 TODO，接通真实资金前必须替换固定 Mock。

**2026-09-15 删除来源 Binding（当前模型与受理链）：** 按用户确认，从生成源、字段元数据、PDManer、正式 Schema 和当前空库创建稿删除 t_payment_intent_binding；模型为 27 表/363 字段/41 唯一索引/43 访问索引/63 关系/7 条 1:1/7 图，保留的 27 个实体定义与本轮开始时逐项一致。Java 移除 Binding DataObject、Context 字段、Repository/Impl、Mapper/XML 及注入、查询、行锁；原方法中的 Intent 开放、期限和活动/成功 Attempt 检查保留为 checkNewAttemptAllowed。来源唯一索引、Intent CAS、工具保护、公共幂等和单一本地事务不变，用户已调整的 Service 实现保留。

**Designed / Implemented：** [字段修订第 8 节](../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#8-删除来源-binding)取代 Binding 与提前关闭创建能力；移除前序 EX-I03 合同及示例，Commerce 应先恢复创建结果，再终止已有 Intent，未决资金返回 PENDING。创建、终止、工具准备、资金流程与真实 Commerce 接入仍 TODO。当前旧空库迁移稿改为 9 ALTER/1 CREATE/4 DROP，结果 10 表；已精简环境另有空 Binding 的单表删除稿，非空关闭事实禁止直接丢弃。所有 SQL 只维护文件，没有执行。

**Verified / Pending：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true`、24 项 Payment 模型/场景/Schema 检查通过；场景中遗留 command_status 同步为公共表的 process_status。生成确定性与独立字段、索引、关系清单通过；保留的 8 条 Payment SQL 与修改前一致，打包制品离线加载 8 条业务及 6 条公共幂等 SQL，接口绑定和 TypeHandler 正常，Binding 类与 Context 字段无残留。未新增或运行仓库 Java 单元测试，未连接数据库、执行 DDL/DML、提交、推送或部署；真实事务/并发回归未验证。

需求站检查 135/136，剩余为既有 Risk 模型统计断言不匹配，未改 Risk。新增文档链接无缺失；受影响前序文档仍有 14 个指向此前已删除代码的历史链接，未将它们视作当前实现入口。日志为 `/tmp/payment-remove-binding-model-tests.log`、`/tmp/payment-remove-binding-requirements-test.log`，链接核对为 `/tmp/payment-remove-binding-doc-check.json`。

**2026-09-15 pay 持久化按表拆分：** 按用户要求，将聚合 PaymentExecutionRepository/Mapper 拆成 Intent、Attempt、InstrumentToken、IntentBinding 四组 Repository/Impl、Mapper/XML；Processor 和 Service 直接依赖实际涉及的表仓储。PaymentIntentRepository.activateAttempt 在条件更新恰好一行后同步传入 Intent 的 activeAttemptId、version、updateTime，Service 删除提交后的重复赋值。事务仍集中在 savePayContext，写入顺序、SQL 条件、幂等和原提交补绑定保持；后续事务失败时抛出异常并丢弃本次 Context，重试重新读取。

**Verified / Pending：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 通过。逐项比较确认 10 条 SQL 和 4 个列片段与拆分前一致；Processor 方法体除 Repository 接收者外未变。新打包制品离线装配四个 Mapper 与 Foundation 幂等 Mapper，共 16 条语句，接口绑定、SQL 展开及参数 TypeHandler 检查通过，旧聚合 Mapper 未残留。文档链接及差异空白检查通过。按用户既有要求未新增或运行仓库 Java 单元测试；未连接数据库或验证真实事务回滚，未改 Schema、执行 SQL、提交、推送或部署。

需求站检查 134/136；剩余为 Risk 模型统计和 Gateway 当前台账描述的既有断言不匹配。移除本次 Payment 台账新增段落后的内存基线仍复现两项匹配失败，未修改关联系统或测试断言。日志：`/tmp/payment-repository-split-requirements-test.log`。


**2026-09-15 pay 响应精简（当前返回合同）：** 用户确认当前只保留 attemptId/status，内部 PayResponse 和 HTTP PayResponseBody 均收敛为两个字段；删除 NextAction 及金额、失败原因、卡展示、观察时刻等投影。Processor 从 Context 中的 Attempt 直接组装结果，移除 Action 查询、对应 Repository/Mapper 方法、XML 查询与列片段，以及 Context 的 paymentAction。Action 数据模型与表结构保留，未新增接口、字段或替代类。HTTP 仅按 Attempt 状态判断：PROCESSING 返回 202，SUCCEEDED/FAILED/CANCELLED 返回 200，保留 no-store；手动授权后仍 PROCESSING 时也返回 202，取消旧授权金额特例。上游需同步精简后的字段合同。

**Verified：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 通过；新打包类的离线验证确认两个响应类仅有两个字段且无嵌套类型，四种 Attempt 状态均映射为正确 JSON；MyBatis 离线装配 10 项 Payment 与 6 项公共命令 SQL，通过展开及 TypeHandler 检查。与本轮开始时源码比对确认用户的幂等 else-if 写法、Hash、Context 构建和受理事务保留；其余 SQL 未变。需求站 build 通过，测试 135/136，剩余为前轮同一项 Risk 模型统计断言不匹配，未改动 Risk。未新增或运行仓库 Java 单元测试，未连接数据库/Redis，未执行 SQL、提交或推送。


**2026-09-15 pay 删除执行开关（当前请求语义）：** 按用户“去掉，按需写代码”的要求，删除 HTTP PayRequestBody、内部 PayRequest 及 Converter 中的 allowExecution。PayProcessor 命令未命中直接进入受理，命中后比较 Hash 并返回原提交；移除只读观察及对应空值分支。不新增查询接口、替代开关或类。原提交缺少命令时的补绑定路径保留；Hash、受理事务、响应及数据库模型未变。调用方需停止传入旧字段，现有严格 JSON 绑定会拒绝它。Processor 继续使用用户认可的公共模板，资源锁暂不增加，内部请求字段校验及后续支付流程保持既有待办。

**Verified：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 通过；使用新打包类的离线检查确认四个顶层请求字段可校验、转换，两个请求模型均无执行开关，旧字段 true/false 均被既有严格绑定拒绝；静态比对确认 Hash、Context 构建、响应和保存事务未改。需求站 build 通过，`npm --prefix requirements-site test` 为 135/136，通过之外的一项失败是 Risk 模型统计断言仍期望 78 表/944 字段，与当前台账不一致，未改动该测试或 Risk。未新增或运行仓库 Java 单元测试，未连接数据库/Redis，未执行 SQL、提交或推送。


**2026-09-15 pay Service 归属与 Context 修订（当前结构，取代下方旧 ext/直接 Body 形态）：** 按用户重构继续补齐，PayProcessor、PayRequest、PayResponse 归属 Execution `service/processor`；Controller 经 Spring 管理的 PayConverter 转换 HTTP 模型，Web 不再读取 Execution DataObject。服务方法使用 Context，超过 3 个业务参数时封装为业务 Context；pay 从幂等开始使用同一个 PayContext，保存事务不变。字段注释改为简短含义加必要 `<li>` 说明，约定登记在 [Payment Processor 规范](../paytech/payment/docs/development/processor-conventions.md)。

HTTP 请求沿用用户新增的 `payMethod` 嵌套对象，内部 ID 为 Long，HTTP ID 为字符串；响应 Java 类型为 PayResponseBody，15 个响应字段和 6 个交互字段保持。基础校验继续使用 Hibernate Validator，内部入口通过 @Validated/@Valid 声明约束。公开非 final 的 `process(PayRequest)` 使用公共 ResourceLock 的 `#request.intentId`；不继承 process 为 final 的 AbstractGatewayProcessor，避免代理锁失效。Execution 直接声明 Redis Starter 与 Snowflake 依赖，继续复用公共命令幂等。Hash 字段顺序、Token 字符串参与方式、只读观察语义及 200/202/no-store 保持。上游需同步嵌套请求结构，真实调用迁移尚未验证。

**Verified：** JDK 25 / Maven Wrapper `clean package -Dmaven.test.skip=true` 通过。使用新打包类完成离线检查：嵌套 JSON 与级联校验、Converter Bean 装配及字段映射、原 Hash 向量、资源锁元数据/Intent 键、响应字段与待执行判断均通过；静态比对确认本地事务方法和 Mapper XML 未变，跨模块引用与文档链接检查通过。未新增或运行仓库 Java 单元测试；未连接 HTTP/数据库/Redis，未执行 SQL、提交或部署。Risk、Routing、认证、扣款和真实交互交付继续按原范围保持 TODO。


**2026-09-15 pay 基础字段改用 Bean Validation（前序平铺请求校验，结构由上方修订）：** PayRequestBody 的六个字段使用 Jakarta `@NotBlank/@NotNull/@Pattern/@DecimalMax`，涵盖必填、合法字符/长度、捕获模式和正整数字符串的 long 上界；PaymentExecutionApi 的 Body 参数标记 `@Valid`。API 仅增加标准 Validation API 依赖，Web 显式引入 Boot validation starter，运行实现为 Hibernate Validator。移除 PayProcessor.validate；Controller 保留路径解析与 Header/来源提交 ID 对应检查，Processor 保留幂等和业务事实验证。字段错误直接复用 Foundation `422 / VALIDATION_ERROR + errors`，没有新增 Validator、Advice 或模型包装类。

**Verified：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 成功。使用实际打包类的离线检查确认：Spring MVC HandlerMethod 能继承接口上的 `@Valid`，全部六字段缺失被拒绝，17 个格式/越界反例被拒绝，long 最大值、64 字符边界、MANUAL/AUTOMATIC 和 true/false 合法。公共异常映射返回 422、VALIDATION_ERROR 及安全字段名/消息，不泄露原 Token；错误 Header 在进入 Processor 前拒绝。前一构建的对照确认原 DTO 无约束且 MVC 无 `@Valid`。未新增或运行仓库 Java 单元测试，未连接数据库/Redis，未执行真实 HTTP/数据库回归、SQL、提交或部署。


**2026-09-15 pay HTTP 模型改用 class（前序类名与平铺结构，class 约定保留）：** 按用户要求将 PayRequestBody、SubmissionResponseBody 及嵌套 NextAction 改为普通 class，使用 Lombok Getter/Setter；27 个字段分别补充业务含义、必填/可空条件、格式与单位说明，保留工具请求和交互对象的脱敏 toString。Processor 使用 getter，Converter 逐字段 setter 组装响应；HTTP 方法、JSON 字段、Hash、业务流程和数据库模型不变。Payment 项目 Processor 规范登记此类型约定，API 模块仅补充 provided Lombok 与编译期处理器。

**Verified：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 通过；离线使用实际打包类和 Payment JsonMapper 配置验证请求 6 字段、响应 15 字段及嵌套 6 字段，缺失/显式 null Boolean 保持 null，字符串/数字 Boolean、数字 ID 与未知字段仍拒绝，空值序列化和 Token 脱敏保持。字段注释覆盖及 diff 检查通过。未编写或运行 Java 单元测试、未连接数据库，未执行提交或部署。


**2026-09-15 Payment pay 接入公共命令幂等（当前代码与目标模型）：** 按用户“改造吧，使用公共包”的确认，Execution/Web 直接依赖 `paytech-command-idempotency` 并显式开启自动配置。删除 Payment 自有 CommandIdempotency DO、CommandStatus、两项仓储/Mapper 方法与命令 SQL；PayContext 直接持有公共 NewCommandClaim，不新增包装类。Processor 保持字段校验→原 Hash/公共 find→业务 Context→一次保存→流程 TODO→直接 ResponseBody。Service 在同一 TransactionTemplate 内执行公共 Claim、核对胜出者、保存 Attempt/Intent 和公共 Complete；使用实际版本、阶段、Owner 和完成时刻，竞争失败在写业务表之前返回 409。

命令仍使用 `client:<clientId> / PAY_INSTRUMENT_V1 / submission:<sourceSubmissionId>` 和原请求 Token Hash；Hash 版本为 1。命令阶段仅 CLAIMED→COMPLETED、30 秒 Lease 配合 5 秒本地事务；完成保存 PAY_ACCEPTED/202 元数据，请求与响应快照为空。只读观察不 Claim，重放读取原 Attempt/Action 的当前状态，不把受理回执当作支付成功。详细消费约定见[接入说明](../paytech/payment/docs/development/command-idempotency-differences.md)。

**目标模型：** 28 表/371 字段/43 唯一索引/43 访问索引/64 本库关系/8 条 1:1/7 图。命令表由 11 列对齐公共包 24 列，状态改名 process_status，保留原字段 ID，补充技术 CAS、Lease 和可空快照；scope/key/resource_no/response_location 对齐 ascii_bin、key 长度 255。与本轮修改前模型逐实体对比，只有命令表变化，其他 27 表逐字节一致；Intent/Attempt/Action 仍为 20/17/22 列。正式 Schema、Execution 创建稿与合并空表迁移稿同步，另提供只改旧空命令表的互斥路径。

**Verified：** JDK 25/Maven Wrapper `clean package -Dmaven.test.skip=true` 成功；既有模型与 Schema 检查 17/17。打包制品包含公共 JAR 与 opt-in；公共 SQL 与当前源码仅格式差异，24 列逐项匹配当前 Commerce 物理列合同。使用实际打包 JAR 在同一个 MyBatis Configuration 离线加载 11 项 Payment 与 6 项 Foundation SQL，参数 Handler 与 SQL 展开通过，无数据库连接。模型确定性、SQL 工件列覆盖与链接/差异检查见[验证记录](../requirements/20260913/payment-Execution设计/assets/pay-public-idempotency-verification.json)。

**Pending / Not deployed：** 按用户要求未编写或运行 Java 单元测试；没有连接数据库或执行 DDL/DML，也未完成真实 HTTP、Claim 并发或事务回滚回归。此前只读核对的本机旧 13 张空表不是本轮实时状态；执行前必须重新预检并按[人工迁移说明](../requirements/20260913/payment-Execution设计/db/command-idempotency-migration.md)选择匹配路径。非空命令表需按历史操作另行制定数据迁移，不能猜值回填。Risk/Routing/认证/资金流程仍 TODO，Foundation/Commerce 源码未改；本轮未提交、推送或部署。


**2026-09-14 Payment 公共幂等包接入差异分析（前序评审记录）：** 用户已确认“有公共包的就用公共的”，当前交付为[项目差异稿](../paytech/payment/docs/development/command-idempotency-differences.md)，并更新 Processor 规范的复用规则及原需求技术入口。源码核对确认公共包可在宿主同一事务完成 Claim、业务写入和 Complete；Payment 的 11 列模型对齐其 24 列合同时，新增 13 列、command_status 改名，并需处理键长度/排序规则与可选恢复索引。公共响应正文可为空，pay 的当前状态观察不能机械替换为首次回执重放。本轮仅文档分析；模型列清单及链接核对通过，未修改 Java/POM/PDManer/SQL，未运行单测、连接数据库、提交或部署。该评审时 Payment 尚未实际接入；后继实施见上方记录，Foundation/Commerce 代码未改动。

**2026-09-14 清空源码后按 Transaction 风格重建 pay（公共幂等接入前基线）：** 按用户明确要求，Payment 七模块 `src` 下的 142 个源码、API、测试与运行配置文件全部删除，独立提交为 `e6ac829 chore(payment): clear existing sources before rebuilding pay`；旧实现保存点为 `a3747d0`。Maven 骨架、文档与数据库设计保留。下方旧接口、流程、测试及验证记录均为历史证据，不能用于判断当前可运行能力；该阶段模型由后文的整批数据库工件维护记录描述；当前目标模型见上方公共幂等接入记录。

完整阅读 Transaction 的 PaymentProcessor、AbstractProcessor、TransactionContext 及其保存/执行调用链后，在 Payment 项目内新增 [Processor 开发规范](../paytech/payment/docs/development/processor-conventions.md)。重建 21 个生产 Java 文件、一个 Mapper XML 和运行配置，仅提供 `POST /payment-execution/v1/intents/{intentId}/pay`；RequestBody/ResponseBody 改为顶层 API 类型，JSON 字段合同不变。Controller 只做协议适配，PayProcessor 按字段验证→幂等→构建 Context→一次本地提交→流程 TODO→直接响应组织。Context 持有 Intent、Attempt、工具、来源 Binding、命令记录及当前 Action，不恢复 Input/View/Snapshot、通用 Processor 基类或事务转发层。

`PaymentExecutionServiceImpl.savePayContext` 用一个 TransactionTemplate 完成来源保护、Intent CAS、工具保护、Attempt 与命令插入；只补原提交新版命令时不重新创建 Attempt。事务内通过条件 SQL 和影响行数确认可变状态，并在工具行锁等待结束后的插入语句再检查有效期。六类 DataObject 按当前模型完整映射，不恢复已删除字段或表。`PAY_INSTRUMENT_V1` 继续直接对请求 Token 字符串计算原长度前缀 Hash；Hash 命中先于业务查询，过期/撤销不阻止原提交观察，改 Token 返回 409。HTTP 保持 200/202、no-store 和安全失败投影。

**本轮验证：** JDK 25 + Maven Wrapper 全 Reactor `clean package -Dmaven.test.skip=true` 通过（初次构建的 switch 空值分支语法错误已修正后重跑）；六类对象及 SELECT 列与 PDManer、13 项 Mapper 方法与 XML、两项插入的完整列顺序、文档链接及空白检查通过。日志为 `/tmp/payment-pay-rebuild-package.log`。按用户要求未编写或运行单元测试，也未进行数据库或 HTTP 集成验证；不继承下方历史测试通过数量。

**边界：** 新 pay 只受理，Risk/Routing/认证/扣款/恢复与通知继续 TODO；Card last4 读取未接通，原 3DS 挑战只返回 UNAVAILABLE，不能交付 URL。其他 API、Intent 创建、工具准备、Card/Channel 实现和原测试均已删除，等待逐个实现。Web 只依赖 API/Execution，未用的业务模块与补偿依赖已移除。没有 SQL 执行、环境配置、推送或部署；本轮未重新检查实际数据库，先前观察仍为旧 Schema。规范及新 pay 代码留在工作区供用户评审，尚未作第二次提交。

**2026-09-14 Pay 按精简模型迁移受理链（前序实现，已由上方清空重建取代）：** 用户明确限定“先完成 pay 受理链，后续流程保持 TODO”。`PayRequestBody` 不再包含六个法律接受/订单版本字段，按字段验证→幂等→业务上下文→一次本地事务→流程 TODO→事务外响应组织。上下文沿用数据库对象；新 Attempt 为 PROCESSING、金额零、活动 Action 与渠道三元组为空。序号读取本 Intent 的 `MAX(attempt_no) + 1`；保存只使用专用 Intent 占位 CAS、现行 17 列 Attempt 插入和命令绑定，不调用状态历史或 Pricing Fact。Intent 占位 SQL 同时保护预检 Version、OPEN、活动/成功指针为空与未到期；Binding/Intent/Token 锁和命令唯一键保留。

幂等投影使用 `PAY_INSTRUMENT_V1` 的六个请求字段，第六个为 Token 字符串；按用户后续确认，基础校验后直接计算 Hash，第一次业务查询为命令幂等查询，未命中后才解析 Token 内部 ID。命中后只读取原提交响应，缺少新版绑定的旧提交观察在幂等查询后核对工具。Attempt 仍保存内部 Token ID，幂等表不保存 Token 明文。当前操作名为尚未发布/落库的开发合同，直接修正其投影，旧 `PAY_CAPTURE_MODE_V1` Hash 不改写。原提交优先定位；无新版绑定时按冻结身份复用原 Attempt，只读观察不补命令。三表读取 ResultMap/SQL 已按 20/17/22 列模型调整；补充 Attempt 渠道坐标和 Action 新字段投影。共享 DO/Snapshot 的旧属性、非 pay 生命周期写入以及 Create Intent、Channel、认证、通知、定价实现仍待后续迁移；SQL 已标注这些旧写入不由 pay 调用。本轮没有宣称所有 Execution DO/Mapper 或整个 Payment 运行时已完成模型切换，也未启用原 recover/资金流程。

按用户后续确认，`ext/rest/execution/processor/PayProcessor.process` 直接接收路径 ID、幂等键和原 `PayRequestBody`，首次受理与重放直接返回 `SubmissionResponseBody`。已删除 `PayInput`、`ExecutionCommands.Pay` 及相应转换；`PayContext` 保留现有数据库对象，受理事务只保存并绑定，不再重查并返回 `ExecutionView`。响应从 Intent/Attempt/Action 数据库对象投影，展示查询在事务外；Controller 只补充 HTTP 状态及缓存头。Token 字符串仍在幂等前仅参与 Hash，ID 在业务校验后用于落库；新 Attempt 的工具过期/撤销检查保留，原提交重放不重新要求有效期。旧 Channel 交互读取、recover 与其他用例仍保留原有快照模型；本次没有新增 Java 类。局部对象可见性和只读仓储例外已同步技术规则与架构门禁。

**验证：** JDK 25 + Maven Wrapper 限定五类 `clean verify` **46/46**（合同 26、SQL/PDManer 3、规则 3、方式注册 2、架构 12），全部测试源码编译并完成 Reactor 打包。精简请求反例先在旧代码失败；实际 MyBatis 展开后的受理/观察 SQL、完整 Attempt 插入参数和三表 ResultMap 与当前 PDManer 一致，响应测试覆盖风险、未知及渠道原文不外泄；原 RequestBody 入口契约先在旧签名以 NoSuchMethodException 失败；新增数据库对象响应投影覆盖长 ID、金额精度和工具身份不外泄，保留 10 组 HTTP 投影用例验证捕获模式、活动 Action、只读观察、原响应体及 no-store。12 个真实 HTTP 受理场景已编码；新增同键改成不存在 Token 的 true/false 两例应先返回 409，受理 Hash 断言改为请求 Token。前序首次受理场景在只读 Schema 初始化器因“缺少已批准 Execution 表”停止；前序只读复核仍为旧库，本次没有重跑数据库业务场景，不能记为通过。新测试以专用 TestMapper 准备已存在的新版 Intent，避免调用尚未迁移的 Create Intent/状态历史链。

前序使用已获准的本机配置完成只读核对：127.0.0.1:13306/mor_payment 仍为 13 张空旧表，新 Token 表/capture mode 等尚缺；本轮没有执行 DDL/DML、提交、推送或部署。新 Pay 请求须由上游同步采用，Commerce 真实调用迁移仍未实施。见[受理说明](../paytech/payment/docs/pay-interface-walkthrough.md)、[合同](../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#7-pay-受理链合同)和[数据库对象链验证](../requirements/20260913/payment-Execution设计/assets/pay-dataobject-verification.json)；[直接响应验证](../requirements/20260913/payment-Execution设计/assets/pay-response-body-verification.json)、[Token Hash 修正验证](../requirements/20260913/payment-Execution设计/assets/pay-token-hash-verification.json)及[前序模型受理验证](../requirements/20260913/payment-Execution设计/assets/pay-model-acceptance-verification.json)保留为修改前证据。


**2026-09-14 整批数据库工件维护（公共幂等接入前模型，保留为历史证据）：** 按用户确认同步[字段修订](../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md)。正式 PDManer 与完整 Schema 为 **28 表/358 字段/43 唯一索引/43 访问索引/64 本库关系/8 条 1:1/7 图**；Intent/Attempt/Action 分别 20/17/22 列。删除 Pricing Fact 与状态历史表，渠道三元组移至 Attempt，认证要求移至 RISK_CHECK；删除复制的法律接受与认证明细，Action 新增安全渠道 response code/msg。保留原资金 Ref、最小交互和两层活动指针。

本轮 Payment/跨系统模型、场景与 Schema 检查 **34/34**，需求站内容检查 **71/71**；生成器确定性检查、存续对象 ID 核对及人工迁移文件离线覆盖检查通过。PDManer 应用已刷新，28 表规范检查全部绿色，支付执行图和移动字段已核对；未逐图人工遍历全部七图。[验证记录](../requirements/20260913/payment-Execution设计/assets/execution-field-simplification-verification.json)。SQL 未经实际 MySQL 执行验证，不将这些检查表述为数据库业务回归。

本轮仅维护数据库设计、生成器、独立模型清单、场景及 SQL；[精简 Execution 空库创建](../requirements/20260913/payment-Execution设计/db/execution-simplified-create-empty-schema.sql)为 11 表，[旧空表迁移说明](../requirements/20260913/payment-Execution设计/db/execution-simplified-migration.md)列出 10 ALTER、1 CREATE、3 DROP，文件尚未执行。旧 state-risk 空库脚本已标记为前序。Java 的状态历史、Attempt 序号、路由/认证、Pricing Fact 与通知 Handler 仍待改造；Commerce/Risk/CCT/Finance 的受影响合同列入修订，不能将本次模型检查当作业务集成通过。实际数据库仍为前序旧结构，未执行 DDL/DML、提交或部署。




**2026-09-14 Pay 手写受理骨架补齐（前序受理形态，字段与 Hash 现由上方模型迁移取代）：** 按当前代码草稿，`ext/rest/execution/processor/PayProcessor.process` 只完成基础校验、原命令幂等、`buildContext` 和一次 `saveContext`；Controller 保留 URL Intent ID 的解析传递，资源锁继续绑定 `PayInput.intentId`。上下文沿用本轮数据库对象形态，含 Intent、Attempt、工具、仅新建所需来源 Binding 和 `newAttempt`；支付方式不重复存储。原提交优先定位，只有同一提交才能复用活动 Attempt，新提交不能覆盖其执行位。新建在 Processor 校验资源归属、来源/期限、工具作用域和有效性；Service 只保存预构造对象并保护预检后的关闭、到期、撤销和 CAS。`PaymentFlowTransactions.savePayContext` 原子保存 Attempt、Intent 活动指针、状态证据及幂等记录。保留 `PAY_CAPTURE_MODE_V1` 原 Hash；幂等重放不受新支付资格影响。本阶段不创建 Risk/Channel Action 或自动推进任务，原 Card/recover 实现保留，后续流程接入与原同步完整支付场景适配待下一阶段。该 Pay 手写入口的 Execution 数据库对象/只读仓储依赖为局部实现范围，架构门禁仅放行具体类型并继续禁止直接写仓储、Mapper/SQL 和其他 Owner 内部引用。

本轮 JDK 25 Reactor `clean verify` 的四类规则/契约/架构检查 **30/30**，全部测试源码编译、原 Hash 字段顺序复核和 `git diff --check` 通过。新增受理 HTTP 集成测试 7 场景已尝试运行；使用本机配置凭据后在 `ExecutionInfrastructureInitializer` 因“缺少已批准 Execution 表”停止，未进入业务测试，不能记为通过。没有执行 SQL、部署或本轮提交。前序完整重构保存点为本地 Payment commit `9a56cdf`。见[受理说明](../paytech/payment/docs/pay-interface-walkthrough.md)和[验证记录](../requirements/20260913/payment-Execution设计/assets/pay-context-acceptance-verification.json)。

**2026-09-14 Pay 流程与事务集中重构（取代下方前序流程形态）：** `PayProcessor.process` 的第五步直接调用选中方式的 `flow.execute(accepted)`，不再在 Processor 编排风控或 Card 恢复分支；命令重放/观察提前分流，普通 Hash 命中不重复逐字段校验，旧提交缺命令记录仅走独立兼容方法。`CardPaymentFlow.execute` 明确展示风控→期限→路由→可选认证→SALE/AUTHORIZE，Service 只准备流程指定的动作类型。已存在资金动作跳过重新认证及新动作准备，未决操作沿用原渠道恢复。`PaymentFlowTransactions` 集中拥有初始受理、风险准备/发送权、风险结果、期限/路由失败、Card 动作准备/输入等待、渠道发送权及渠道结果事务；各流程/执行器不再自行打开通用事务。初始受理一次提交，远端结果独立短事务，所有外部调用保持事务外。`PayContext` 去掉重复 submission，方式接口统一使用已受理 `ExecutionView`；移除未用命令包装和泛型/用例分派维度。详见 [pay 阅读说明](../paytech/payment/docs/pay-interface-walkthrough.md)。

新增事务集中架构门禁先在旧实现失败，再通过；限定四类契约/规则/架构的 `clean verify` **28/28**，全部测试源码编译并完成 Reactor 打包。[验证工件](../requirements/20260913/payment-Execution设计/assets/pay-flow-transaction-verification.json)。新增 Card 四条步骤顺序 HTTP 用例及关闭来源后恢复原未知扣款的 Handler 用例只完成编译，未运行数据库业务回归。只读复核本机仍为 13 张空旧表、缺少 Token 表与新字段；本轮未执行 SQL、未提交或部署。


**2026-09-14 Pay 六阶段重构（前序形态，已由上方流程与事务集中重构取代）：** `PayProcessor.process` 明确按字段基础验证→幂等→业务验证→一次本地受理事务→提交后推进→响应执行。Token 先只读解析内部 ID，保留 `PAY_CAPTURE_MODE_V1` 的原 Hash 字段序列；Service 装载 `PayContext`，Processor 校验归属、工具、原提交和新支付资格。`PaymentExecutionService.accept(PayContext)` 不再调用 `domain.requireIntent/lockIntent` 或重复参数/原提交验证，按既定行锁顺序保护可变关闭/期限/撤销，并用预检 Intent 版本的 CAS 防止预检后状态变化。新命令绑定、Attempt、风险动作及恢复任务保持原子提交；已受理命令跳过受理事务。其他资金写路径的验证职责未在本次扩大迁移。更新 [pay 阅读说明](../paytech/payment/docs/pay-interface-walkthrough.md)。

本次四类契约/架构测试 **27/27**、全部测试源码编译；新增参数优先校验 2 例、失效工具已完成重放 1 例及双提交竞争 1 例只完成编译，未运行数据库业务回归。只读复核本机仍为 13 张空旧表，缺少 Token 表和 capture_mode；本轮未执行 SQL、未提交或部署。


**2026-09-14 submission 校验归属调整：** `PaymentExecutionService.submission(intentId, submissionId)` 改为读取上下文，移除 `domain.requireIntent` 和 `domain.view`，记录缺失返回空。Client 归属、Intent–Attempt 关联及历史兼容性由查询 Processor 和 Pay 的观察/恢复入口验证；已受理流程内部按原身份刷新，不重复校验。本次只覆盖 submission 读取链，其他写方法尚未整体迁移为可信 Context 输入。新增 4 个 HTTP 上下文反例已编译；四类契约/架构测试 **27/27**。数据库业务回归未执行，既有 Schema 授权仍待确认；未提交或执行 SQL。


**2026-09-14 accept 事务整理（前序形态，已由上方六阶段重构取代）：** `PayProcessor.accept` 只展示受理 Attempt、准备首次执行、读取最新提交三步；新命令才建立命令绑定，重放仍经过业务 Service 的原提交/工具校验。风险动作和恢复任务保持同事务，首次支付已完成时跳过准备。同步修正支付 Hash 入口测试中的命令类型为 `PAY_CAPTURE_MODE_V1`。四类契约/架构测试重新运行 **27/27**，全部测试源码编译；本轮未执行数据库迁移或业务集成回归，未提交。



**2026-09-14 本轮重构进度（覆盖下列前序待实施描述）：** 已写入 captureMode、4/4/5 状态、active_action_id、原 RISK_CHECK、Channel 原操作恢复和独立资金用例入口；Pay/Capture/Reversal/Refund 不再依赖其他 Processor。Attempt 不保留评估 ID/结果或限额组，Risk 响应丢失保留原未知动作，可信 ALLOW 后才路由。Risk 关联事实新增 Attempt 身份和不可变来源版本；真实 Risk 消费者仍未接入。Card 按顺序组织可选认证和 SALE/AUTHORIZE；Wallet/Crypto 与真实 Card/Channel/Eligibility/Routing 仍为后续集成范围。

本轮 Java 四类契约/架构 `clean verify` **27/27**，全部测试源码编译、七模块打包；[本轮验证工件](../requirements/20260913/payment-Execution设计/assets/pay-state-risk-refactor-verification.json)；Payment 模型/场景 **21/21**。目标模型 **30 表/402 字段/45 唯一索引/47 访问索引/67 关系/7 图**；其中 active_action_id 为应用约束的前向 1:1 指针，不添加二级索引。SQL 和 Mapper 已同步，完整 MySQL/Redis 业务回归尚待本机 Schema 升级。只读盘点本机 127.0.0.1:13306/mor_payment 的 13 张旧表均为 0 行，Token 表尚缺；[本机空表迁移](../requirements/20260913/payment-Execution设计/db/execution-state-risk-local-empty-migration.sql)已准备，尚未执行。旧 PAY_V1、历史评估和非空环境不在此空库迁移适用范围。未提交、未部署。


## Payment Execution 本地切片研发（2026-09-13，Token 修订中，待完整回归）

- **内部风控 Action 与工具身份（2026-09-14，设计确认、待实施）：** [已确认修订](../requirements/20260913/payment-Execution设计/internal-risk-action-amendment.md)新增 RISK_CHECK，评估 ID/结果移至该 Action，并明确按类型恢复与换工具的新提交/Token/Attempt 边界。原 Excel 已更新为 **29 个合成场景、328 个检查点**，Intent/Attempt/Action 快照分别 **328/319/525 行**；状态、ALLOW 门禁、单动作占位、身份/引用、金额、公式缓存和 11 个原生筛选表核验通过，6 个工作表及新增风险字段完成可视检查。这些仅为设计附件验证；Payment/Risk Java、PDManer、SQL、数据库及真实消费者尚未按此实施，本轮未运行 Java/数据库业务测试、未提交或部署。

- **限额字段移除（2026-09-14，设计决定、待实施）：** 用户确认从 Payment Attempt 移除 limit_reservation_group_id，[修订稿](../requirements/20260913/payment-Execution设计/risk-limit-correlation-amendment.md)明确由 Risk 按原 Attempt→Assessment→Group 处理可信结果。代码、模型、SQL 和消费者迁移仍待实施；后续 RISK_CHECK 与字段移动现已确认，Excel 的更新及核验证据见上一项。

- **状态与 Channel 恢复新目标（2026-09-14，设计已收敛、待实施）：** [修订设计](../requirements/20260913/payment-Execution设计/execution-state-channel-recovery-amendment.md)与 Card/Wallet Excel 已同步 capture mode、三表 4/4/5 状态、active_action_id 及无 QUERY 业务 Action 的 Channel 原操作恢复边界。附件 23 个合成场景、223 个检查点的状态、单动作占位、身份/引用、授权与收款金额及公式缓存核对通过，6 张表已完成可视检查；15 份范围文档的 383 个本地链接（含锚点）及空白检查通过。这些是设计附件核验，本轮没有执行 Java 或数据库测试；下述 Java/测试证据、PDManer、正式 Schema 与数据库仍对应前序实现，不作为新状态/恢复规则已实现的证据。

- **当前修订授权与范围：** 用户确认[交易级工具 Token 合同](../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)并进入 Payment 研发。已新增 PI-I00/PI-I01，EX-I05 改为统一 `pay`，各接口独立 Processor；方式共同命令只携带内部 Token ID，Card Flow 通过受控 Owner 完成首次认证或资金动作。其余 Owner 仍为测试 Mock + TODO；本次未适配 Commerce 实际代码、未实现真实 Card 材料存储或 Wallet/Crypto。
- **数据与恢复实现：** Attempt 直接关联 `t_payment_instrument_token`，移除旧 Instrument DAL 与 CardReference。原来源作用域、固定期限、唯一绑定、撤销、T3 发送门禁和现有补偿任务清理已编码；Owner 材料准备、领取、清理及发送在事务外。SUBMITTED/UNKNOWN 只查询原动作，工具到期不会阻断原 Capture/Void/Refund。源码与场景导航见[修订研发检查](../requirements/20260913/payment-Execution设计/instrument-token-development-checks.md)。
- **本次已运行验证：** JDK 25 限制五个测试类的 `verify` **36/36**，涵盖应用启动、实际本地 HTTP、合同、Flow Registry、资金纯规则及架构，所有测试源码可编译，七模块完成打包；模型/场景 **21/21**。修复了资源锁不支持两层属性表达式导致的启动失败，改为显式 Intent ID 并在 Processor 内重验。新空库工件的 13 条 CREATE 逐字对应正式 Schema，迁移 Token CREATE 同样一致。[验证证据](../requirements/20260913/payment-Execution设计/assets/instrument-token-development-verification.json)，日志 `/tmp/payment-token-review-verify.log`、`/tmp/payment-token-model-final2.log`。这不是完整 `clean verify`、新覆盖率或数据库业务通过的证据。
- **实际数据库状态：** 2026-09-13 13:28 UTC 只读核对本机 `mor_payment` 仍为旧 **13 表/192 列/58 个索引（含主键）**，逐表真实 COUNT 均为 0，新 Token 表不存在。[只读证据](../requirements/20260913/payment-Execution设计/assets/instrument-token-local-preflight.json)。[空旧表迁移](../requirements/20260913/payment-Execution设计/db/instrument-token-empty-migration.sql)已准备，ALTER/DROP 与临时本地恢复检查的独立授权尚待回复；未执行该迁移、未做备份恢复演练。生成的正式模型已改为本节上表所示目标，不能据此推断数据库已变更。
- **尚待完成：** 21 项 Token 业务测试及已适配的并发、幂等、崩溃恢复等完整数据库回归尚未执行。获 SQL 授权后需重新确认空表、备份并验证恢复、人工迁移、核对物理结构，再运行完整回归和覆盖率检查；若已出现数据则停止空表方案。综合验收、真实 Owner/渠道、Broker 与调度仍未完成，未推送或部署。

- **本地版本检查点（用户要求“先帮我提交一版本吧”）：** 保存当前 Payment 实现及此前全系统 Channel 命名工件；其他任务的未提交改动排除。当前源码重新运行限制范围 `verify` **36/36**，Commerce 公开合同 **13/13**，需求站构建及 **136/136**、修改文件 ESLint 通过。同步修正需求站中旧 Payment 模型统计断言。全量模型/场景 **518/533**，15 项失败名称与已保留的改名前基线完全一致，无新增失败；不把全量模型检查记为通过。[提交前验证证据](../requirements/20260913/payment-Execution设计/assets/checkpoint-verification.json)。本版本保留 Processor 间调用和通用 Execution 内的 Card 动作选择，刚讨论的职责调整尚未实施；Token SQL 和完整数据库回归继续待办。本次授权仅为本地提交，不包含推送、部署或 SQL 应用。

- **应用仓库本地提交已完成：** payment-orchestration-service `d35bd4bb`；client-contract-service `a25a11a7`；finance-service `c5476030`；risk-compliance-service `c989f0c7`；platform-support-service `e69cff4a`；commerce-service `e69cacba`。其中 Payment 保存当前 Execution/Token 版本，其他五库仅保存此前批准的 Channel 命名关联改动；没有推送、创建分支、部署或执行 SQL。具体父提交与树摘要见[提交证据](../requirements/20260913/payment-Execution设计/assets/checkpoint-commits.json)。

- **PI-I01 局部重构（2026-09-14 已本地提交 `dbc882e3`）：** 按用户指定整理 `PaymentInstrumentController#prepareCardInstrument` 及对应单个 Processor。Controller 依次转换请求、调用用例、转换响应；Processor 明确区分首次创建和原绑定重放，重放直接读原 Token，首次仍在 T1 内重查命令并原子创建 Token、命令和清理任务。Card 身份核对与材料准备位于事务外；接口字段、幂等 Hash 字段及顺序、Intent 锁键保持一致。生产修改前入口基线 **19/19**；修改后 JDK 25 限定五类 `verify` **38/38**，全部测试源码编译、七模块打包通过，格式与方法文档检查通过，需求站构建及 **136/136** 通过。新增只读请求不得创建绑定的数据库场景，Token 专项现为 21 项；本轮只读确认 Token 表仍不存在，专项和完整数据库回归未运行，不能据此宣称创建/重放数据库链路通过。2026-09-14 提交前按当前源码重新运行限定 `verify` **38/38** 与需求站 **136/136**，将重构、相关测试及 Payment import 整理保存为上述提交；Caller 接口保持原样。未执行 SQL、推送或部署；[本次证据及提交前复验](../requirements/20260913/payment-Execution设计/assets/prepare-card-instrument-refactor-verification.json)。

- **可信内部调用、移除 Caller（2026-09-14，已本地提交 `fbe247f4`）：** 根据用户明确要求与[内部信任修订](../requirements/20260913/payment-Execution设计/trusted-internal-amendment.md)，删除 Caller 接口、默认授权 Bean、14 个 Processor 的授权调用和测试 Owner 授权实现。8 类命令统一由现有 `CommandIdentity.forClient` 生成 `client:<clientId>` 命名空间，原 Hash 投影、操作类型与锁表达式保持；Client/Intent/来源/工具业务归属和状态门禁、其他 Owner 校验继续执行。变更前 7 个 HTTP 反例均被旧 Caller 阻断为 503；变更后限定五类 `clean verify` **48/48**、全部测试源码编译及七模块打包通过，清理构建产物不再含 Caller 类，需求站构建及 **136/136** 通过。真实 HTTP/MySQL/Redis 验证了来源关闭的重放、参数冲突、跨 Client 同键隔离、来源归属冲突与命令回滚，测试后 Binding/Intent/命令记录均为 0。新 Token 表仍不存在，21 项 Token 专项及完整支付回归未运行；Caller 移除阶段没有 Schema 修改或历史数据迁移；后续国家字段修订与本地提交见下项，未推送或部署。旧命名空间的非空环境需另行兼容评审；[验证证据](../requirements/20260913/payment-Execution设计/assets/caller-removal-verification.json)。

- **Payment 禁止调用 Commerce（2026-09-14，已本地提交 `fbe247f4`）：** 按[调用方向修订](../requirements/20260913/payment-Execution设计/payment-commerce-direction-amendment.md)移除 `ExecutionPorts.Commerce`、默认 Bean、测试 Owner 实现及创建/支付/定价三个调用点。创建增加必填 `consumerCountryCode`，持久化到 Intent 的 `consumer_country_code`；首次和恢复定价使用本地冻结值，渠道不能覆盖。创建 Hash 追加该字段并使用 `CREATE_INTENT_V2`，其他命令不变。两项修改前反例准确失败；本轮限定四类 `clean verify` **24/24**、全部测试源码编译及七模块打包、模型/场景 **21/21**、需求站构建及 **136/136** 通过，124 个修改文档链接有效。本机只读确认 Binding/Intent/命令仍为 0、Token 表和 Intent 国家列均不存在；无 Commerce 装配的应用启动成功，但创建用例在只读 Schema 前置明确失败（缺少 consumer_country_code），未执行业务请求。新 SQL 尚未执行，真实数据库创建/支付/恢复不能据此声明通过；国家必填、改值冲突及渠道覆盖反例已编码。历史 Caller 48/48 对应此前 Schema/源码，不作为本轮数据库通过证据。当前模型与完整/空库 Schema 已同步；非空旧环境需另行兼容设计，未执行 SQL、推送或部署。用户随后要求“提交”，按相同源码完成提交前专项 `verify` **24/24**、模型/场景 **21/21** 与需求站构建及 **136/136** 复验，将 Caller 和 Commerce 出站依赖移除、模型/SQL/测试保存为上述本地提交；数据库完整回归仍待迁移。[本轮验证证据](../requirements/20260913/payment-Execution设计/assets/payment-no-commerce-verification.json)。

### 旧 SubmitCard 切片及 Processor 重构历史证据

以下记录属于 Token 修订前的源码与 Schema，保留其验证范围；其中 102 项完整回归及覆盖率不能验证当前工具 Token 行为。


- **授权与 SQL：** 用户在研发确认后，再次允许读取 Payment/Redis 的本机连接配置，并在 13 张目标表均不存在时应用空库 SQL。只读确认 `127.0.0.1:13306/mor_payment` 为零表后，人工执行[13 表创建工件](../requirements/20260913/payment-Execution设计/db/execution-create-empty-schema.sql)。实际 **13 表/192 列/58 个索引（含主键）**的列序、类型、默认值、空值、注释及索引定义逐项等于正式模型；未执行旧表 ALTER/DROP 或其他系统 SQL。[执行与前置证据](../requirements/20260913/payment-Execution设计/assets/local-sql-application.json)。此处只表示本机 Execution 相关 13 表已创建，完整 Payment 30 表未应用。
- **实现范围：** Execution 的来源 Binding/单向关闭、Intent、原 submission/Attempt、AUTHENTICATE/资金/QUERY Action、退款预占、原授权 Capture/Void、Reversal 决策绑定、Pricing Fact、首笔成功唯一性和重复扣款补偿。Web 提供 12 个内部 HTTP 接口、短事务命令定位、Redis 资源锁、Card 首次流程及四类可靠 Handler；业务与任务同事务，外部 Owner 调用在事务外。没有消费者交互表或数据库流程编排。
- **方式抽象：** `PaymentMethodFlow<C>` / `PaymentFlowRegistry` 按精确方式与用例匹配；Card 使用专属输入，非 Card 测试替身不携带 3DS 字段。生产 Owner 默认不可用；稳定 Card 引用受理前校验，瞬时材料不进入库、Hash、任务。其余 Owner 为测试 Mock + TODO，未实现 Wallet/Crypto。
- **本轮修复：** 补齐 Redis 显式超时；可信结果核对原支付方式并拒绝非二进制挑战事实；统一 Problem Writer 清空响应后恢复 `no-store`；恢复 Handler 不使用无业务含义的占位子类型分流。test 显式依赖其直接消费的 api/execution，覆盖率不再只统计 Web；未改变生产模块依赖。
- **接口评审重构：** 按用户要求移除两个集中 Processor，12 个接口各自拥有用例入口；七个写用例的短事务受理分别使用独立锁代理。Controller/Converter 位于 `ext/rest/execution`，Mapping 只由 API 声明；Converter 无数据库/外部依赖，Processor 使用应用模型。命令绑定、业务查询与原渠道请求分别采用 Service + Impl；Card 首次流程拆为明确阶段，闭集枚举按稳定 Code 转换。接口路径、请求 Hash 字段序列、锁 Key、金额和状态合同不变。[具体映射与边界](../requirements/20260913/payment-Execution设计/design.md#11-接口与-processor-一一对应)。
- **开发检查：** 独立 Processor 重构后，JDK 25 七模块 `clean verify` **102/102**，0 失败/错误/跳过。包括 15 项生产装配/HTTP、模块边界、Card 与后续资金操作、权限/严格输入、四类 Handler 独立入口、真实 Scanner/Dispatcher 的并发 Claim/过期 Lease/次数耗尽、实际 Redis 锁过期、事务回滚，以及两种真实 JVM 退出后重启恢复。外部调用 Probe 检查无活动数据库事务；发送前退出保持 UNKNOWN，发送后退出查询补记成功，两者资金发送均仅一次。[本轮逐类结果、源码摘要与覆盖率](../requirements/20260913/payment-Execution设计/assets/processor-refactor-verification.json)，原日志 `/tmp/payment-processor-final-verify.log`；[此前 98 项基线](../requirements/20260913/payment-Execution设计/assets/development-verification.json)保留为历史证据。新增检查覆盖接口/Processor 一一对应、协议与查询分层、稳定 Code 解析及原提交 Hash/只读重放。
- **打包与数据清理：** 可执行 `-exec.jar` 在随机本地端口启动，健康 UP；正式 Owner 未配置的 Execution 返回 503 且带 `no-store`，检查后进程关闭。[本轮制品证据](../requirements/20260913/payment-Execution设计/assets/processor-refactor-packaged-smoke.json)。每个用例清理其自身 Client 数据；本轮早期遗留的 46 个合成 Client 精确清理后，13 张表均为零行，无业务事实遗留。[清理证据](../requirements/20260913/payment-Execution设计/assets/local-fixture-cleanup.json)。
- **覆盖率与后续门禁：** API/Execution/Web 完整聚合指令 **88.955351%（12492/14043）**、分支 **66.463415%（872/1312）**；行覆盖率 **91.644909%（1755/1915）**。指令与分支仍未达到项目 90% 目标；`clean verify` 成功不代表该目标已满足。[21 组验收映射](../requirements/20260913/payment-Execution设计/development-checks.md)区分已运行代表场景与待综合核对项。阶段 5 尚未进入；真实 XXL Admin 调度、Broker Confirm/正式 Wire、真实 Owner/渠道、非空旧库迁移均未验收。同 key 保证幂等的重发优化未启用，当前 SUBMITTED/UNKNOWN 统一只查询原动作。
- **模型与文档基线：** 前一轮交付的 Payment 模型/场景 **21/21**、需求站点 **136/136** 和 105 个本地链接检查保留为历史证据；本次 Processor 重构未改模型、SQL 或需求站点源码。[静态检查摘要](../requirements/20260913/payment-Execution设计/assets/document-checks.json)，日志 `/tmp/payment-delivery-model-final.log`、`/tmp/payment-delivery-site-final.log`。
- **本轮文档检查：** 接口/Processor 映射、交付记录、README 与本节的 72 个本地链接/锚点通过，37 个本轮 Web 源码文件无行尾空白，工作区及 Payment `git diff --check` 通过。[静态检查](../requirements/20260913/payment-Execution设计/assets/processor-refactor-document-checks.json)。最终回归及制品检查后只读确认 13 张本机表仍为零行。
- **状态边界：** 这是已完成本轮研发及开发回归的本地模拟切片，综合验收未完成。没有提交、推送、test/生产部署、真实卡支付或其他 Owner 接入。


<a id="platform-config-management-20260913"></a>

## Platform 菜单权限与 API 配置管理（2026-09-13）

- **范围已确认：** 用户在澄清模型后回复“那就一起覆盖呗”，确认 Target/Route 新增编辑覆盖六类已有 Channel；Operations 为管理入口，菜单/按钮仍只维护 OPERATIONS。两项能力合并，风控新增功能暂缓。见[需求范围](../requirements/20260913/platform-support-菜单权限与API配置管理/requirement.md)，包含 14 项功能、6 项视觉/交互验收。
- **阶段 2 已确认：** [差异与系统影响](../requirements/20260913/platform-support-菜单权限与API配置管理/technique.md)列出 14 项差异、3 个应用预计改动；复用七张配置表，暂未发现新增表/业务字段的必要，基础新增操作估算 15 个（两侧约 30 个端点），精确接口/索引/SQL 数量在阶段 3 核定。Gateway/Foundation 暂无必要生产代码改动的证据；六类配置管理不扩展为尚未接通的业务认证研发。
- **关键缺口：** 现有可分配权限只含已持有且由启用 Operations 资源/路由引用的权限，需要设计新增权限的首次显式分配；组件/路径映射必须符合已部署页面；没有独立 Scope 字典，不新增 Scope 管理。阶段 3 已确认上述规则及接口/SQL/Pencil 和研发方案；阶段 4 结果及下一确认点见本节开发状态。
- **阶段 2 操作：** 已同步 PLT-16/PLT-17、PLT-I19 及 E2E-16 目标时序，更新原动态路由决定的后继范围入口；其他需求改动保留。未改应用、模型或 Pencil，未执行 SQL、部署、提交或推送。阶段确认见[交付记录](../requirements/20260913/platform-support-菜单权限与API配置管理/delivery.md)。
- **阶段 1 文档证据：** 当批三份材料 23 个本地链接/显式锚点有效，14/6 验收编号唯一，需求站点构建及 136/136 通过；阶段 2 材料另行核验，不能将此数字当作本轮新增应用验证。
- **阶段 2 文档验证：** 三份材料的 42 个本地链接/显式锚点有效，14 项差异与 14/6 验收编号核对通过。E2E-16 登记后首次检查发现旧全局用例数量 15 和已被动态路由决定取代的静态 Registry 断言；按现行范围同步为 16 个用例及数据库/完整快照规则后，需求站点生产构建与 136/136 检查通过。没有新运行应用、数据库或 test 环境验证；日志位于本机 `/tmp/platform-config-stage2-final-tests.log`。

- **阶段 3 已确认：** [详细设计](../requirements/20260913/platform-support-菜单权限与API配置管理/design.md)定稿评审范围为 3 个应用、18 项能力/36 个新端点、15/14 个既有 Platform/BFF 端点合同或授权调整；复用七配置表，新增表/字段/索引为 0。准备 227 行 INSERT（7 张表，禁用注册）和零 DDL 声明，未执行。Pencil 在原 47 张画板后追加 7 张并导出 PNG/PDF，未改应用实现、未发布/部署/提交/推送。用户随后回复“可以，下一步”，确认本阶段并授权研发；[交付记录](../requirements/20260913/platform-support-菜单权限与API配置管理/delivery.md)保留边界。

- **阶段 3 文档/设计验证：** 本需求 83 个本地链接存在；18 项新增 API 的 Method/Path/Route Code 清单与 SQL 对应，227 行 INSERT 的列名与当前 Schema 对齐，种子 ID/自然路由代码与当前完整初始化 DML 无冲突。Pencil 54 张画板（原 47 + 本期 7），新画板最终无裁剪告警，PNG/PDF 已导出并通过编辑器保存 operations-console.pen。需求站点生产构建及 136/136 检查通过，日志 `/tmp/platform-config-stage3-tests.log`。以上为静态设计/文档证据，没有执行 SQL 或应用业务测试。

- **阶段 3 菜单树视觉修订：** 按用户追加意见优化原 d6tuF 一张画板：树图标/缩进/按钮分组引导线、停用项文字、选中菜单摘要及分组编辑；画板总量仍为 7。已通过 Pencil 最终裁剪检查并审阅树/编辑区截图，保存设计文件、重导菜单页 PNG 和整套 PDF，更新 ui.md/delivery.md 并核对链接。仅页面方案调整，未进入研发；本次未新增 API、结构或注册 SQL，也未运行应用测试。


- **阶段 7 已部署，等待用户验收：** Platform、Operations BFF、Web 已实现 18 项新增操作（Platform/BFF 各 18 个端点）、菜单树、权限目录/引用/选择器、六类 Channel 的 Target/Route 完整编辑与只读详情。保留 v1 接口和无版本后缀类名，包含显式首次授权、INACTIVE 历史关联、内置管理对象保护、CAS/幂等/审计、严格 JSON/Query 校验与浏览器草稿保护。新增表/字段/索引为 0，增量注册为 227 行；原有 Channel 等其他任务修改继续保留。用户明确确认四应用和 SQL 发布清单后已完成 test 部署，包括 Gateway 必要命名兼容；当前等待用户业务/页面验收；用户同阶段反馈的两处 Web 样式已修复并更新 test，见下方后续记录。
- **Foundation 必要修复：** 用户明确同意纳入后，`paytech-http-client-support` 补充合法 404/NOT_FOUND 分类及回归，108/108 通过并完成本地 Maven install。Platform/BFF 显式映射 NOT_FOUND→404；未知或畸形上游错误仍按既有策略处理。没有对外发布制品。日志 `/tmp/plt-config-foundation-test.log`。
- **真实 Platform 开发回归：** 最终代码相关 HTTP/基础设施用例共 97/97 通过：新增配置 19、既有人事/角色/登录 39、目录重构 8、配置启停/幂等 19、Redis 不可用 1、租约失效竞争 1、快照 8、数据库 Target 2；架构 7、本机库边界 13、审计投影 6 另通过。证据来自 `/tmp/plt-config-platform-regression2.log`、`/tmp/plt-config-platform-final-regression.log`、`/tmp/plt-config-platform-eligibility-regression.log`，同类用例采用最后一次通过结果，不重复累计。覆盖六类 Channel 创建/编辑、权限与树生命周期、原回执重放、CAS、树互挂竞争、权限停用与绑定竞争、同 Method/Path 竞争、子项唯一键失败时根/全部子项/命令回执一起回滚，以及读取可分配权限后引用停用导致并发授权 403 且不写入角色。
- **开发中修复的实际问题：** 新测试库精确加入 localhost:13306 允许范围，仍拒绝远端、test 端口及相似库名；菜单锁前缀统一为 `console-resource:operations`，业务 Channel 仍为 OPERATIONS。新审计注解只引用真实字符串参数，架构用例固定该规则；认证关闭时不加载仅供管理写入使用的 Gateway Repository，快照独立启动恢复正常。曾将全部人员事务提升为 SERIALIZABLE，真实旧回归暴露管理员交接超时和冲突，现已恢复 READ_COMMITTED；按 Permission ID 顺序锁定权限与 Operations 资源/Route/Target 引用行并重查资格，树与 Gateway 候选写入单独保留 SERIALIZABLE。旧人员 39 项及配置引用竞争已通过，无此阻塞遗留。
- **规则与注册静态证据：** 原 48/48 项规则、编译器、目录和架构聚焦检查通过（`/tmp/plt-config-platform-pure-tests3.log`）；管理端点最小传输合同补齐导航查询，43 个合同与独立固定目录逐项核对，作者规则/架构 18/18 通过（`/tmp/plt-config-platform-authoring-final3.log`）。权限 `resource_type + action_code` 唯一冲突按业务 400 处理，主键碰撞保留系统错误，并通过真实 HTTP 用例。Web/Platform 11 个页面能力逐项一致。
- **BFF 与 Web 开发证据：** 真实 BFF 314/314 通过（新增配置边界 46、既有浏览器边界 254、角色树 5、HTTP Body 命名 9），日志 `/tmp/plt-config-bff-regression2.log`。Web 全量 920/920 与后续配置聚焦 5/5 通过，最新 TypeScript/Vite 生产构建通过，仅既有大 Chunk 提示；日志 `/tmp/plt-config-web-tests2.log`、`/tmp/plt-config-web-authoring-final.log`、`/tmp/plt-config-web-build4.log`。浏览器使用本机临时 API 样例确认树布局、编辑草稿、取消返回保留内容、确认返回上一页及只读详情无保存；这是页面开发证据，尚不是真实后端整链路验收。
- **Gateway 复用验证：** 既有动态转发与快照用例 13/13 通过，使用真实 Gateway/Redis 和独立 HTTP 下游 Simulator，覆盖新逻辑 Target、发现名称/路径刷新、请求固定旧快照、无效刷新保留上一配置、停用与无实例处理。没有因本需求修改 Gateway 生产代码，其他任务已有改动保留。日志 `/tmp/plt-config-gateway-regression.log`；不将 Simulator 证据表述为真实 Platform→Gateway 环境验收。
- **本机数据库操作与清理：** 用户单独回复“可以”后，按[准备材料](../requirements/20260913/platform-support-菜单权限与API配置管理/db/platform-support-service/local-validation/README.md)新建 `127.0.0.1:13306/mor_platform_config_validation`，应用现有 24 表及完整 1141 行初始化，并给既有本机应用账号该库数据读写权限。执行前备份两个原 Platform 库的结构及账号授权元数据，原库未修改；证据目录 `/Users/kenny/Code-MoR-backups/20260913-platform-config-validation-132337/`。最终回归后仍为 24 表/1141 行（Target 1、Route 88、Header 310、Query 99、Guard 524、Tenant 1、Role 2、Permission 34、Role-Permission 40、Resource 42），测试账号、会话、命令和审计 Fixture 均已清理。新增入口保持禁用、管理员新增授权保持 REVOKED；应用/测试启动不建表或播种，不删除验证库。
- **文档和下一阶段：** 需求站点构建及 136/136 检查通过（`/tmp/plt-config-requirements-tests.log`），本轮收尾 93 个本需求本地链接存在，四个改动仓库差异格式及新增源码空白检查通过。实现计划仍为[已确认计划](../requirements/20260913/platform-support-菜单权限与API配置管理/implementation.md)，[交付记录](../requirements/20260913/platform-support-菜单权限与API配置管理/delivery.md)保存阶段 4 审阅入口。该条保留阶段 4 开发证据；阶段 5 最终证据及下一确认点见下。没有执行 test 部署、外部制品发布、Git 提交或推送。

- **阶段 5 最终完整验证：** Platform `clean verify` 433/433、BFF `verify` 1105/1105，均无失败、错误或跳过。Platform 指令 97.597%、分支 90.086%，保留原 90% 门禁。同步旧目录 / 路由 / 日志清单断言并补足配置保护边界用例；没有下调阈值或添加排除。最终日志 `/tmp/plt-config-stage5-platform-verified.log`、`/tmp/plt-config-stage5-bff-final-verify.log`；Web / Foundation / Gateway 未在此后修改本期实现，复用上述有效证据。
- **阶段 5 实际修复：** 真实 Gateway → BFF → Platform 调用 A18 时，PLT-I02 旧允许清单遗漏新权限选择器，错误返回 403。新增真实 HTTP 用例先得到 allowed=false，随后仅补齐该已批准 Route 的普通会话接入；usage 对应 READ 权限、强制改密及 Bootstrap 前置限制保持。该修复已包含于最终 433 项完整验证。
- **阶段 5 真实链路与页面：** 本机实际 Web 构建、Gateway、BFF、Platform、MySQL/Redis 完成 50 项公开 HTTP 检查，A01–A18 全部覆盖；新 Target / Route 经真实快照自动加载转发，停用后 404；真实显式授权、人员新会话导航可见、Permission 停用隐藏和恢复通过。浏览器验证真实资源保存 / 回读、只读详情无保存、草稿回退、权限筛选 / 引用、六 Channel 选项、管理 / 只读 / 无权限账号、1280 / 1024 / 768 布局；未捕获 error / warn。服务发现为本机 SimpleDiscovery，开发签名 / Email Vault 为批准的 auto 适配器，动态 Target 下游为合成 HTTP 回显；不宣称六类真实业务认证已接通。
- **阶段 5 清理与交付：** 自有进程 / 浏览器已关闭，最终验证库恢复 24 表 / 1141 行；合成账号、会话、命令、审计与 Key 均归零，18 新 Route / 4 Resource 恢复 DISABLED，4 条管理员授权保持 REVOKED。验收启动器退出清理先于连接池关闭，首次退出遗留也已按精确合成 ID 清理。材料：[阶段 5 验收映射](../requirements/20260913/platform-support-菜单权限与API配置管理/acceptance.md)、[机器证据及 SHA-256](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage5/verification.json)、[浏览器记录](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage5/browser.md)。阶段 5 结果已获用户确认，后续阶段 6 结果见下；尚未远程发布制品、部署 test 或提交 / 推送。

- **阶段 6 发布准备：** 已只读核对 `desktop-linux / mor-test / 127.0.0.1:23306/mor_platform`：原 70 条 Route 全为 Operations，本期 227 个主键/自然编码/路径均无冲突，8 张受影响表的结构及索引与验收库一致；管理员角色 version=17。准备 227 行禁用注册与 27 行启用 SQL、一次 8 表配置备份和受限恢复方案，未执行 test 数据写入。
- **阶段 6 制品与兼容证据：** 已构建独立标签 `plt16-17-config-20260913` 的 Platform / BFF / Web / Gateway 四个候选镜像，完整 ID 见[发布清单](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage6/release-manifest.json)。已验收的 258 个源码证据与 45 个制品文件 Hash 未变；三个 Java 应用内部 Platform API 的 117 个类完全一致，BFF 内 Foundation HTTP 404 修复与本地已测试类一致。Gateway 候选额外构建并通过 64/64 快照策略及监控定向用例，日志 `/tmp/plt-config-stage6-gateway-package.log`；未重复完整 Java 回归。
- **阶段 6 待确认依赖：** test 实际旧 JAR 使用 PROVIDER_CALLBACK，新 Platform/BFF/Web 使用 CHANNEL_CALLBACK；因此建议同时切换只包含既有命名补丁的 Gateway。test 当前旧/新回调 Target/Route 均为 0，无需回调数据迁移。该第四应用补充待与阶段 7 一并确认，不代表整个 Channel 专项获准部署。具体顺序、精确 SQL、旧镜像恢复和轻量入口验收见[release.md](../requirements/20260913/platform-support-菜单权限与API配置管理/release.md)。当前容器和默认 development 标签未改；没有 Nexus 发布、Git 提交或推送。

- **阶段 6 收尾检查：** 候选/原镜像两份 Compose 覆盖 `config --quiet` 均通过；需求站 136/136、本需求 119 个本地 Markdown 链接、SQL 静态结构、JSON 与四个镜像/制品 Hash 核对通过。11 个 test 容器仍全部 healthy，运行基线未变。收尾证据见[verification.json](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage6/verification.json)；静态 SQL 核对不表示已经执行数据库变更。

- **阶段 7 执行授权与部署：** 用户对四应用、227 行注册/27 行启用及相关表备份的明确确认问题回复“可以”。按已审阅 release.md 在 `desktop-linux/mor-test` 执行：8 表一致性备份至 `/Users/kenny/Code-MoR-backups/20260913-154927-plt16-17-test/`（136434 字节）；确认 INSERT `4/4/4/18/76/13/108` 后提交，依次切换 Gateway / Platform / BFF / Web，四服务 healthy 后确认 UPDATE `18/4/4/1` 再提交。管理员角色 version=18，新 Route/Resource/授权 ACTIVE，无 DDL 或回调数据迁移。完整镜像 ID 与阶段 6 候选一致，其余七个容器 ID/镜像未变，11 个均 healthy。证据见[部署及验收记录](../requirements/20260913/platform-support-菜单权限与API配置管理/test-acceptance.md)。
- **阶段 7 真实入口证据：** 12 项公开 API 检查通过，覆盖新菜单、资源/权限目录、两个能力目录、A18 选择器、Target/Route 列表和原风控变量/规则/决策流；六类 Channel 正确，不存在权限返回 HTTP 404 Problem。Gateway 有效快照 88 Route，刷新 SUCCESS、连续失败 0、未使用旧快照回退。实际公开 HTML 与 39 个静态文件 Hash 全部匹配候选。浏览器真实渲染菜单资源树、权限目录、API 配置及只读详情；权限编辑有保存按钮，浏览器返回回到无保存的只读详情，未保存配置。最终 API/页面会话明确退出；没有新增账号/角色/Target/Route 等业务 Fixture。
- **阶段 7 首次验收观察：** 当时发现权限目录较长编码挤入相邻资源类型列并保存截图；用户随后提出明确样式修复要求，后续修复和 test 更新见下。最初只读首页的临时浏览器会话已关闭并按既有 TTL 结束，后续验收均明确退出；没有归档凭据/Token。候选镜像仍按完整 ID 固定，无 Nexus 发布、Git 提交或推送。后续本地提交范围已整理为[commit-review.md](../requirements/20260913/platform-support-菜单权限与API配置管理/commit-review.md)，须另获用户明确确认。

- **阶段 7 Web 样式反馈已修复并更新 test：** 用户要求资源树按钮数改为名称右侧的小数字和按钮图标，并让权限列表过长内容省略。仅修改 Web 的 ConsoleResources.tsx、Permissions.tsx、configuration.css：计数保持原直接子 BUTTON 口径，同行 13px 图标/数字；权限表格固定列宽、链接和长字段省略并保留完整 title/详情跳转。`npm run build` 通过（仍只有既有 chunk 大小提示），无后端或 SQL 改动。
- **样式更新运行证据：** test Web 使用 `mor-test/operations-console-web:plt16-17-style-20260913`，镜像 `sha256:7aff290486eecefe527f845c78fd5f83d8de774e60e873099b86dba0d49a6586`。实际浏览器 1440/1024 核对按钮数和名称同行、表格链接不越界、完整提示、详情点击及无页面溢出；40 个公开静态文件与新 dist 一致，无 pageerror，验收会话已退出。其他十个容器 ID/镜像未变，11 个均 healthy。材料见[样式修复清单](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage7/web-style-fix/manifest.json)和[检查/截图入口](../requirements/20260913/platform-support-菜单权限与API配置管理/test-acceptance.md#web-样式反馈修复)。首次长编码挤列观察已解决，等待用户继续验收；未提交或推送代码。

- **阶段 8 本地提交归档：** 用户明确要求“代码提交”，据此完成本需求五个应用仓库本地提交：paytech-foundation `6fc398d`；platform-support-service `1148936`；api-gateway `a2111df`；operations-console-bff `a3071a7`；operations-console-web `fba7a24`。包含最终 Web 样式修复及获准的 Foundation 404 / Gateway 回调枚举兼容。源码共 986 项发布 Hash 核对通过（3 项 Web 样式按最后部署清单核对），沿用对应的有效应用回归证据；精确暂存的根文档快照重新构建并通过 136/136 检查，本需求 148 个本地 Markdown 链接存在。共享目录仅暂存本需求条目及对应的三处用例断言，Payment/Finance/Risk 和其他 Channel 模型/Schema/工程修改保留。未推送、未创建 PR、未再次部署或执行 SQL；该提交要求不额外推定用户已完成全部业务验收。


<a id="platform-client-api-access-20260913"></a>

### Client API 接入管理：已部署 test，本地提交归档（2026-09-14）

- **最新授权与停点：** 用户已确认阶段 3 并授权研发；本机 SQL 修订申请经新表用途/取值说明后，用户回复“可以，没问题了”。用户随后回复“继续”，确认进入阶段 5；综合验证及本地验收完成后，用户明确要求“部署test”，据此合并阶段 6/7 发布准备与执行，已完成 test 部署，等待用户业务验收。2026-09-14 用户明确要求“git提交”，授权归档本次本地代码与文档；该要求不额外推定全部业务验收完成，没有远程制品发布或推送。
- **实现范围：** Platform 与 BFF 各新增 9 个端点、各调整 4 个旧端点；Operations 仅查询凭证及授权、独立权限紧急撤销，Scope 查/建/改和 Route 绑定，Client 日常自助生命周期另期。BFF 只聚合当前选中的 Scope 引用页；Web 新增凭证/Scope 页面，复用分页、保存、草稿与错误处理。旧未登记 Scope 绑定读取与同值更新兼容，新的绑定必须存在目录。既有 Route 维护合同同步 requiredScopeCode，Gateway 转发代码未改。源码及 AC 对照见 [development.md](../requirements/20260913/platform-support-ClientAPI接入管理/development.md)。
- **数据工件：** Manifest/Oracle/PDManer/完整 Schema/单一初始化与增量同步；当前完整模型 64 表/829 字段/124 唯一索引/101 普通索引/109 逻辑关系/5 一对一/7 组，手工 Schema 投影 25 表。模型 SHA-256 为 `56991d1ede96b2bed6b916862235047f75cacc4dce36a52e87f169510c9c2d4a`。历史两表建表文件未改；本期模型只新增一张目录表，test 所缺另外两表为旧设计。
- **本机数据库：** 仅在 `desktop-linux/mor-local-mysql-1` 的 `127.0.0.1:13306/mor_platform_config_validation`，备份两旧表 Schema 后，应用新 Scope 表/7 字段/两旧表查询索引，以及 127 条本期注册基线。四权限 ACTIVE、资源/Route DISABLED、角色关联 REVOKED，无 Client/凭证/Scope 业务种子；本机基线不执行部署 DML 的两旧 Route version 更新。精确文件 Hash、备份与事务证据见 [本机执行记录](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage4/local-database-application.json)。完整目录门禁保留，回归后业务夹具无遗留。
- **已验证边界：** Platform 首轮全量 447 项，修订旧精确清单/维护合同后复验并补充边界场景，当前报告 480 项无失败/错误/跳过；最终 Maven verify 返回 0，指令覆盖率 97.68%、分支 90.03%（按仓库 append 配置累计）。33 项本期真实管理 HTTP 场景覆盖 PLT-I03 前 200/撤销后 401、其他凭证仍 200、权限收回、重放、真实 Lease/行锁/唯一索引、历史异常和审计。BFF 1,138 项当前报告通过，Web 923 项及生产构建通过，模型/Schema 38 项、需求站 136 项及构建通过。API 仅安装到本机 Maven 缓存，未发布远程。详细运行顺序与限制见 [开发检查记录](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage4/verification.json)。
- **查询计划与后续：** 用 64 Scope/256 凭证/512 授权/256 Route 的本机合成数据确认 Scope 唯一索引及两反向查询索引，完整事务回滚，无遗留；[EXPLAIN](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage4/local-explain.json)只证明本样本访问路径，不代表压力性能。上述阶段 4 BFF 使用 Owner Simulator；阶段 5 真实链验证见下，test 发布和提交继续按阶段确认。

- **阶段 5 修复及回归：** 真实浏览器发现凭证查询输入被模式下拉挤成 0px，已修复布局；Scope 中文名称搜索触发 ASCII/Unicode LIKE 字符集冲突，新增真实 HTTP 中文用例先失败后修复，不改 Schema。最终本期 34 项真实管理 HTTP 全通过，聚焦 Maven verify 返回 0、原累计覆盖率门禁通过；Web 923/923 与生产构建通过。BFF/Gateway 本阶段生产代码未改，复用已有开发检查。
- **阶段 5 完整链及页面：** 本机 Web 生产构建 → 入口代理 → 真实 Gateway/BFF/Platform/MySQL/Redis 完成 36 项代表性检查；修后另完成 11 项搜索复验与 8 项路由复跑。浏览器新增/只读/编辑/分页/引用/审计、权限、草稿冲突、浏览器返回与 1440/1024/390 布局通过；撤销提交后真实 PLT-I03 200→401。中文 Scope 选择保存最终复验通过，浏览器无捕获 error/warn。开发 Vault/签名和本机服务发现边界保留，不宣称真实 Client 业务 API 或 Risk 链已接入。
- **阶段 5 清理和证据：** 自有进程及浏览器已退出，独立本机库恢复 25 表/1,268 行；临时业务、账号/会话/命令/审计/签名记录均为 0，27 条临时 Route 和 8 个 Resource 恢复 DISABLED。材料见 [acceptance.md](../requirements/20260913/platform-support-ClientAPI接入管理/acceptance.md)、[验证记录](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage5/verification.json)、[清理计数](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage5/cleanup.json)。阶段 5 无未关闭验收缺陷；其发布停点已由后续“部署test”授权接续。

- **test 发布结果：** 已在 `desktop-linux/mor-test` 替换 Platform、Operations BFF、Operations Web，最终镜像前缀分别为 `9bc5b9165b49`、`b3e5fa00976c`、`fb434d52204e`；11 个容器健康，Gateway/Risk 等其余 8 个容器 ID 和镜像均未改变。首轮打包复用的旧探针缺少 BFF 19201 端口，已从现行源码重新编译并替换镜像，业务 JAR 未改。精确镜像、JAR、内嵌 API、Web SHA 与恢复边界见 [release.md](../requirements/20260913/platform-support-ClientAPI接入管理/release.md)。
- **test 数据与启用：** 在 `127.0.0.1:23306/mor_platform` 应用已批准的两旧 Credential/Grant 表及 Scope 新表，共 3 表/28 字段、两反向索引；备份仅涉及八张配置表。注册 127 条配置、两旧 Route 各递增一版；启用 9 Route/4 Resource/4 RolePermission，既有 PLATFORM_ADMIN 18→19。Gateway 自动加载 97 条 Route，当前 Snapshot 成功且不使用失败回退。未创建 Client/凭证/Scope 业务数据，未执行 test 紧急撤销。
- **test 部署检查：** 15 项轻量检查通过，包括真实管理员登录、新菜单/按钮、凭证与 Scope 查询、中文查询、Scope 筛选与审计过滤、合法 404、旧规则集/决策流查询；公开入口 44 个静态资源 SHA 与本期产物一致。仅检查部署可用性，未在 test 重跑阶段 5 的写入/撤销和浏览器完整验收；用户业务验收待完成。见 [test-acceptance.md](../requirements/20260913/platform-support-ClientAPI接入管理/test-acceptance.md)。

- **阶段 8 本地提交归档（2026-09-14）：** 用户明确要求“git提交”，已提交 platform-support-service `3cb5e31f`；operations-console-bff `85927a7a`；operations-console-web `4ebeed0e`。根目录归档本期需求/API/SQL/Pencil/模型与发布证据，只暂存本期共享台账段落；Payment 及前序 Gateway 文档的其他未提交改动保留。当前 Web 923/923、模型/Schema 38/38、需求站构建及 136/136 复查通过，阶段 5 修复源码 Hash 一致，未改业务代码，Java 沿用阶段 4/5 与 test 部署有效证据。本次只修正文档状态与链接；未推送、未再次部署、未执行 SQL。具体应用提交见 [提交清单](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage8/application-commits.json)。

以下阶段 1–3 条目保留各次交付历史；其中“尚未研发/待确认”已由上述最新状态接续。

## Platform Client API 接入管理（2026-09-13）

- **阶段 1 范围评审：** 用户同意下一期以 API 凭证生命周期、Scope 目录/授权关联和真实接口接入验证为方向，管理入口先在 Operations。已建立[需求范围](../requirements/20260913/platform-support-ClientAPI接入管理/requirement.md)、[分析入口](../requirements/20260913/platform-support-ClientAPI接入管理/technique.md)和[交付记录](../requirements/20260913/platform-support-ClientAPI接入管理/delivery.md)。仅登记评审稿，11 项功能、11 项功能验收、6 项 UI 验收待确认。旧凭证轮换失效时机 Q1 已询问、待答复；Scope 目录拟先提供新增、名称/说明编辑与引用查询，访问权仍通过凭证授权/撤销控制。
- **证据及边界：** 已有 PLT-I03 认证代码与凭证/授权结构；当前 Route 的 API Scope 仍为手动输入，缺少本期拟建设的统一目录和生命周期管理入口。真实 Client API 的业务 Owner、有效 Tenant 来源及 test 环境前置在阶段 2 核定，不能沿用旧 Simulator 证据宣称接通。本轮没有改应用/模型、执行 SQL、部署、提交或推送；API/表字段/注册 DML 数量尚未定稿，下一步等待阶段 1 确认。
- **阶段 1 文档检查：** 本需求 3 个 Markdown 文件、23 个本地链接、功能/验收编号及行尾空白检查通过。`npm --prefix requirements-site test` 构建通过，136 项中 135 通过、1 项失败；失败来自其他 Payment 模型工作中的台账统计 30 表/402 字段/45 唯一索引与旧断言 30/399/43 不一致，本次只追加需求段落，未修改该统计或断言。日志 `/tmp/plt-client-api-stage1-requirements-tests.log`；不把整站检查表述为全部通过，不修改其他需求来消除该失败。
- **阶段 1 职责修订讨论：** 用户指出日常凭证管理应属于 Client Console，Operations 应支持查看，并询问管理必要性。已在[需求稿首部](../requirements/20260913/platform-support-ClientAPI接入管理/requirement.md)提出修订：客户自助创建/轮换/授权调整/日常撤销，Operations 查看非秘密信息、维护 Scope/Route，紧急撤销仅为待确认建议；Platform 仍统一拥有数据和业务规则。原 Operations 全生命周期稿及其 F/AC/UI 清单暂停，不继续作为默认实现范围；轮换 Q1 暂缓至 Client 自助需求。没有扩大到 Client Console 研发，当前等待修订范围确认。
- **职责修订检查：** 本轮 24 个本地文档链接及行尾空白检查通过；需求站重新构建和 136/136 检查通过，日志 `/tmp/plt-client-api-responsibility-review-tests.log`。前次 Payment 统计断言失败不再复现，本任务未修改其统计或测试。仅完成职责讨论稿修订，不代表范围获批或功能实现。
- **后继范围已确认：** 用户回复“可以”，确认 Operations 凭证查看、Scope 目录与 Route 关联、独立授权紧急撤销；Client Console 自助生命周期和首次真实业务 API 开通退出本期。上方原阶段 1 全生命周期清单及 Q1 仅为历史，不再等待本期答复。[需求](../requirements/20260913/platform-support-ClientAPI接入管理/requirement.md) 已整理为 9 项功能、10 项功能验收和 6 项 UI 验收。
- **阶段 2 已交付待审阅：** [差异与影响](../requirements/20260913/platform-support-ClientAPI接入管理/technique.md) 已核对真实源码与表结构，预计 3 个应用、约 9 项新 API 能力及 4 项旧 API 调整；模型只拟新增一个 Scope 目录，Credential/Grant 复用旧设计，旧业务字段预计不增加，具体端点/字段/索引/DML 在阶段 3 冻结。Gateway 已有逐次 PLT-I03 认证与动态路由，预计无需修改生产逻辑；不扩展 CCT 或 Client Console。
- **test 只读观察：** `desktop-linux` / `mor-test` / `mor_platform`（宿主端口 23306）当前没有 t_api_credential、t_api_credential_scope，CLIENT Tenant 与 API_SCOPE Route 均为 0；旧两表正式 Schema 共 21 字段。预计后续 test 需应用 3 表建表 SQL，其中 2 表为既有模型补齐。证据见[目标表与计数](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage2/test-database-observation.json)，仅查询存在性及数量，执行前必须重新核对，不能推断其他环境为空。
- **阶段边界：** 已同步 PLT-02/PLT-16/PLT-I06/PLT-I19 和 E2E-16 已确认范围。尚未进入精确 API/数据/Pencil 方案、研发或综合验证；没有改应用/模型、执行 SQL、部署或提交。下一步等待用户确认阶段 2 后进入阶段 3。
- **阶段 2 文档验证：** 本需求 3 份 Markdown 的 33 个本地链接（含 4 个锚点）、9/10/6 功能与验收编号、只读观察字段/目标表关系及空白检查通过；`npm --prefix requirements-site test` 生产构建和 136/136 检查通过，日志 `/tmp/plt-client-api-stage2-requirements-tests.log`。本次未运行 Java 或真实 HTTP 功能回归，文档检查不代表拟议接口或撤销功能已实现。

- **阶段 2 已确认，阶段 3 已交付待审阅：** 用户回复“OK”，据此完成 [实施方案](../requirements/20260913/platform-support-ClientAPI接入管理/design.md)、[API](../requirements/20260913/platform-support-ClientAPI接入管理/api.md)、[数据](../requirements/20260913/platform-support-ClientAPI接入管理/data-design.md)、[SQL](../requirements/20260913/platform-support-ClientAPI接入管理/db/README.md) 和 [Pencil 页面](../requirements/20260913/platform-support-ClientAPI接入管理/ui.md)。3 个应用新增 9 项/修改 4 项 API 能力，对应 Platform/BFF 合计 18 个新端点、8 个修改端点；模型新增 1 表/7 字段/1 主键/1 唯一索引、旧业务字段 0、两旧表各加 1 普通索引。test 另补两张原设计表，共需建 3 表/28 字段。注册 SQL 为 127 INSERT + 两旧 Route 更新，初始新路由/菜单/角色授权不生效；不含业务种子或启用 SQL。
- **阶段 3 页面与边界：** Web docs/operations-console.pen 已保存新增 8 个页面状态和 1 张字段关系图，保留原 54 画板；9 张 PNG 和 8 页 PDF 已导出至本需求 assets/stage3。A09 复用 Route READ，无 PLT-I02 白名单或 Gateway 业务代码扩展。当前仅文档和 Pencil 设计变更，未生成正式模型、修改应用功能、执行 SQL、创建示例、部署、提交或推送。上方阶段 2“尚未进入精确方案”已由本记录接续，当前等待阶段 3 整体确认后进入研发。
- **阶段 3 验证：** [静态检查证据](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage3/design-verification.json) 核对端点/注册子项/127 INSERT/两旧 Route 目标、旧两表与正式 Schema 一致、10/11/7 字段、验收编号和本地链接；Pencil 布局无裁剪，9 张 PNG 与 8 页 PDF 有效。需求站生产构建及 136/136 检查通过，日志 `/tmp/plt-client-api-stage3-requirements-tests.log`；没有数据库 SQL/EXPLAIN 或应用功能验证。

<a id="platform-tenant-access-20260914"></a>

### 租户访问管理与客户/合作伙伴成员权限查询：用户验收通过，代码已提交（2026-09-14）

- **阶段 8 用户确认与提交：** 用户回复“我做完了，提交吧。”，确认本期验收并授权提交。Platform `cb80a2c7c7ab641b7d0646807d34d7fcc05d037a`、BFF `4685f620ad0f93ec9b78c687813a69100caf1666`、Web `ff0b7003b5581eaafd926d7049ab23f3a8dc6462`；需求归档随本条记录所在根提交完成。67个源文件与已验收基线一致，复用Platform512/BFF1163/Web938及阶段5/7有效证据；提交前需求站136/136和范围/敏感值/空白检查通过。Web仅纳入本期8张Pencil画板，其他任务19张仍留工作区；根共享文件仅暂存本需求段落，Payment等修改保留。未推送、重新部署或修改test数据。见[交付记录](../requirements/20260914/platform-support-租户访问与成员权限查询/delivery.md)、[提交验证](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage8/verification.json)。Client/Partner Console菜单控制按用户决定延期。

以下记录保留此前部署、示例创建和验收时的观察，由上述用户确认接续。

- **test 示例（部署后追加授权）：** 用户明确要求“帮我创建一条数据”，已创建 CLIENT 租户 `900000000000065001` / “DEMO 客户租户（验收）”，配1个CUSTOMER成员、1个租户只读角色、2项示例权限，共9INSERT，既有行0UPDATE。合成来源不代表真实CCT入驻，没有登录凭据/API Credential；初始双状态ACTIVE。真实公开接口8项查询均通过，成员两项权限各有1个有效来源。当前test有1个CLIENT租户及1个成员；下方阶段7的0数据为部署时观察。见[示例说明](../requirements/20260914/platform-support-租户访问与成员权限查询/db/test-demo/README.md)、[回执](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/test-demo/verification.json)。暂停/恢复留给用户验收，没有提交或重新部署。

- **阶段 7 授权与执行：** 用户对阶段 6 精确发布清单回复“可以”。复核 11 容器、67 源文件及 SQL/制品无漂移后，一次备份八张受影响配置表，按 Platform→BFF→Web 替换三个固定候选镜像；三个应用 healthy，其他八个容器身份/镜像未变。`127.0.0.1:23306/mor_platform` 人工单连接核对后分别提交 105 INSERT、15 启用 UPDATE，0 DDL；管理员角色 version 19→20。见 [部署与验收报告](../requirements/20260914/platform-support-租户访问与成员权限查询/test-acceptance.md)、[阶段 7 证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage7/verification.json)。
- **部署修正：** 初次交互 MySQL 客户端默认 latin1，将三条新 Resource 中文名称写成乱码；页面检查发现后，以精确 ID/旧字节/version 修正为已批准名称，3 UPDATE，Resource version 2→3。实际合计 105 INSERT、18 UPDATE。公开导航及浏览器均确认中文正确；对应 [修正 SQL](../requirements/20260914/platform-support-租户访问与成员权限查询/db/repair-test-display-names.sql) 和 [修正后恢复候选](../requirements/20260914/platform-support-租户访问与成员权限查询/db/deactivate-test-after-label-repair.sql) 已归档，未执行恢复。
- **部署检查与限制：** Gateway 快照正常加载 105 条有效 Route（新增 8）；真实入口登录、三个新导航资源、客户/合作伙伴空页、不存在/INTERNAL 租户 404、既有人員/角色/API Route/审计/风控规则查询通过。46 个公开 Web 文件与候选摘要一致；浏览器新菜单、筛选、刷新和返回保持条件、退出通过。test 的 CLIENT/PARTNER Tenant/Member 仍为 0，本次没有造数据或重复暂停/恢复/成员明细业务验收；完整业务证据沿用阶段 5。入口 `http://localhost:28080/tenant-access`。当前停点为用户验收；本需求未 Git 提交、推送或发布远程制品。

以下阶段 6 及更早条目保留当时的准备、实施和授权记录，由上述阶段 7 接续。

- **阶段 6 发布准备：** 用户确认阶段 5 后同意准备发布。只读核对 `desktop-linux/mor-test/127.0.0.1:23306/mor_platform`：11 容器健康，IAM 7 表/75 字段齐全，新登记无冲突，管理员角色 version=19、有效 Route 97、CLIENT/PARTNER Tenant/Member 为 0。已构建三个本机候选镜像并固定 image ID，Platform/BFF 内嵌 API 字节一致，Web 46 个文件固定到阶段 5 构建，67 个应用源文件无漂移。已准备 105 INSERT + 15 启用 UPDATE、旧镜像及精确版本停用恢复方案；尚未备份/修改 test 数据、替换运行容器或发布远程制品。候选/恢复 Compose 校验与需求站 136/136 通过。[发布清单](../requirements/20260914/platform-support-租户访问与成员权限查询/release.md)、[制品清单](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage6/release-manifest.json)、[准备证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage6/verification.json)。当时停点是等待 test 部署清单授权；该授权及实际执行见阶段 7，不包含 Git 提交或新增 test 业务示例。

- **阶段 5 真实链路与页面：** 用户在具体本机临时启用申请后回复“可以”。生产 Web → 本机入口 → 真实 Gateway/BFF/Platform/MySQL/Redis 完成 42 项检查和 19 项页面核对：暂停后的 PLT-I03 200→401→恢复后200、权限隔离、分页、幂等、审计、来源限制、损坏关联失败关闭；页面冲突保留原因、权限来源、浏览器返回及 1280/390 布局通过。67 个源文件与阶段 4 一致，未新增业务修复；手工脚本纠正了公开入口应按既有 BFF 503 检查而非 Owner 500 的断言。见 [验收报告](../requirements/20260914/platform-support-租户访问与成员权限查询/acceptance.md)、[验证记录](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage5/verification.json)。开发 Vault/签名、本机发现和合成业务数据边界保留，未验收 CUSTOMER 登录或外部 Client 业务执行。
- **阶段 5 恢复记录：** 自有进程、浏览器和账号/租户/成员授权/凭证/命令/审计/签名夹具已清理，11 行临时配置恢复 DISABLED/version=1，原管理员 3 条关联仍 REVOKED，105 行正式登记保留。见 [SQL 回执](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage5/local-activation.json)、[清理核对](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage5/local-postcheck.json)。没有未关闭验收缺陷；用户已确认接续阶段 6，当前停点见上方。以下保留阶段 4 有效开发证据。

- **实际改动：** Platform/BFF/Web 三应用完成租户列表/详情、安全暂停与恢复、成员/角色/权限及来源查询。8项新业务 API（7读1写，Owner/BFF各8端点）、3页面、0表结构/字段/索引变更、105行登记。只修改目标Tenant安全状态及版本/时间；成员授权只读。Gateway/其他核心应用未改；阶段 4 当时尚未提交、推送、发布或部署。
- **本机 SQL：** 用户针对具体申请回复“继续”后，已备份并在 `mor-local-mysql-1/127.0.0.1:13306/mor_platform_config_validation` 应用105 INSERT，0DDL/0旧行更新。8新路由、3新资源仍DISABLED，3管理员授权仍REVOKED；安装执行元数据ID1仅为已批准的独立本机例外，没有创建账号。[应用与备份](../requirements/20260914/platform-support-租户访问与成员权限查询/db/local-application.md)。阶段 4 当时 test 尚未应用，阶段 7 执行见上方。
- **开发检查：** Platform最终 `clean verify` 512/512（其中本期31），指令覆盖率97.82%、分支90.06%，通过原90%门槛；BFF `verify` 1163/1163（本期25），Web938/938及TypeScript/Vite生产构建通过。保留既有Web chunk大小提示。完整记录及当前源文件摘要见 [验证证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage4/verification.json)。
- **阶段 4 回归修正与边界：** 修复已写入的租户安全操作被原审计前缀查询排除；补齐损坏关联、回执与权限停用用例，保留单资源锁、DB行锁/CAS、幂等重放前重新授权。代表性EXPLAIN使用现有索引，非负载测试。当时的浏览器证据仅为生产页面组件与合成响应交互；阶段 5 完整链结果见上方。详见 [验收映射](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage4/acceptance-mapping.md)。
- **阶段 4 清理记录：** 当时测试合成数据及临时 Web 预览已清理，登记计数/禁用状态保持预期，见 [本机核对](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage4/local-postcheck.json)。后续阶段 5 已执行并恢复，当前停点见上方。

以下阶段 3 条目保留当时的交付及授权证据，当前状态由上方记录接续。

- **设计规模：** Platform/BFF/Web 3 应用，新增 8 项能力（7 读1写，Owner/BFF 各8端点），既有 API 路径/请求变更0；0表结构/字段/索引变更，105 INSERT/0初次旧行UPDATE，后续启用预计15 UPDATE。3功能页面、8张Pencil状态画板；旧审计/页面能力接口有内容扩展，Gateway无需改代码。
- **本轮工件与观察：** Web docs/operations-console.pen 保存新增8画板，保留原63画板；导出8PNG及8页PDF。只读检查本机验证库和test的新ID/权限/路径占用为0，Active Operations Target/管理员角色各1；未应用SQL、创建示例、修改功能代码、部署、发布或提交。其他任务修改保留。证据入口为 [阶段3材料](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage3/design-verification.json)。

- **阶段 3 检查：** 88 个本地文档链接/锚点、8 项 API 与 105 行 SQL 的列名/值数量/唯一 ID/路径/20 项 Query 对齐、6 个 JSON 示例、7/10/6 验收编号通过；8 张 PNG/8 页 PDF 有效，Pencil 无裁剪且已保存。需求站生产构建及 136/136 检查通过，日志 `/tmp/plt-tenant-access-stage3-requirements-tests.log`。未运行本期应用功能、实际 SQL 或性能测试；[完整证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage3/design-verification.json)。

以下阶段 2/1 记录为历史观察及交付口径，已由上方当前阶段接续。


- **最新授权与停点：** 用户回复“可以”，确认阶段 1 范围/验收及逐阶段安排；已交付 [阶段 2 差异与影响](../requirements/20260914/platform-support-租户访问与成员权限查询/technique.md)。预计 Platform/BFF/Web 三个应用、约 8 项新 API 能力、0 新业务表/字段，Gateway 无需改代码；精确接口/索引/DML/Pencil 数量在阶段 3 冻结。等待用户确认分析及成员标识采用“脱敏邮箱＋账号 ID”的细化；现有模型没有姓名字段，尚未据此修改阶段 1 F-04。
- **阶段 2 只读证据：** 已对 desktop-linux 的 test `mor-test-mysql-1/127.0.0.1:23306/mor_platform` 和本机验证库 `mor-local-mysql-1/127.0.0.1:13306/mor_platform_config_validation` 读取七表字段、索引及聚合计数。七表共 75 个既有字段，列名/数量与当前 Schema 一致；PDManer 同步。两库均无 CLIENT/PARTNER Tenant、CUSTOMER 账号/成员及 CLIENT 凭证；test 既有 Operations 数据保留。证据见 [database-observation.json](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage2/database-observation.json)，不是完整库或性能验收。
- **阶段 2 边界：** 同步 PLT-15/E2E-16 已批准范围，读取现有 PLT-I03/管理测试但未运行功能回归；无应用/模型/SQL/部署/制品发布/提交变更。后续合成数据、CUSTOMER 运行链缺口与安全命令并发在技术稿中明确，未提前创建示例或扩大到 CCT 投影实现。以下阶段 1 记录保留为历史。
- **阶段 2 文档检查：** 三份 Markdown、44 个本地链接/所引用显式锚点、7/10/6 功能验收编号、8 项分析 API 编号和七表观察与模型/Schema 列名一致性检查通过；四个应用仓库保持干净。`npm --prefix requirements-site test` 生产构建及 136/136 检查通过，差异空白检查通过；证据见 [verification.json](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage2/verification.json)。未运行本期应用功能验证，阶段 2 仍待用户确认。

- **需求授权与停点：** 用户选择两项能力一起做；已登记 [需求评审稿](../requirements/20260914/platform-support-租户访问与成员权限查询/requirement.md)、[技术证据入口](../requirements/20260914/platform-support-租户访问与成员权限查询/technique.md) 和 [交付记录](../requirements/20260914/platform-support-租户访问与成员权限查询/delivery.md)。拟覆盖 CLIENT/PARTNER 租户查询、安全暂停/恢复、成员角色和权限只读，7 项功能、10 项功能验收、6 项 UI 验收。等待用户确认整体范围后进入阶段 2；尚未定稿 API/表字段/注册 DML/Pencil 数量。
- **已核对边界：** 现有 Tenant 模型区分 CCT 来源业务状态与 Platform 安全状态；当前人工授权 API/SQL 限 Operations/INTERNAL，不能推定已支持 CUSTOMER 成员查询。现有 PLT-I03 认证已检查 CLIENT Tenant 的来源 ACTIVE 与安全 ACTIVE。本期不以管理页面替代未建设的 CUSTOMER 登录/会话链，不修改客户主档或提供成员授权写入。
- **本轮实际动作：** 只读核对文档、源码及工作区，创建阶段 1 材料并追加检索/状态入口。应用代码、模型、数据库和部署均未变更，未提交或推送；未查询本机/test 数据库。其他任务工作区修改保留，已确认范围的全局用例/时序投影待本阶段确认后处理。
- **阶段 1 文档检查：** 3 个需求 Markdown、19 个本地链接及所引用显式锚点、7/10/6 功能和验收编号、行尾空白检查通过；共享文档差异空白检查通过。`npm --prefix requirements-site test` 完成生产构建及 136/136 检查，日志 `/tmp/plt-tenant-access-stage1-requirements-tests.log`。三个应用仓库保持干净；本轮未运行 Java、HTTP 或页面功能验证，文档检查不代表本期能力已经实现。


<a id="commerce-get-checkout-conventions-20260916"></a>

## Commerce Customer GET 编码规范重构（2026-09-16）

> 以下验证属于当时版本；本轮按表查询及普通类迁移见[后继记录](#commerce-customer-get-query-20260916)，尚未执行新的验证。

- **范围与实现：** 用户限定 `CustomerCheckoutController#getCheckout` 按最新接口规范重构。GET 使用 Foundation `AbstractProcessor` 和内部 `GetCheckoutRequest/GetCheckoutResponse`，入口拒绝已有事务；Context 留在查询编排内。Controller/Converter 只做协议与模型转换，独立 `response/GetCheckoutResponseBody` 及其 Info 使用 class。消费者回填、Payment 摘要和展示资格由 Processor 编排，本库按需查询保持；原三份文件链接迁至共享 Integration Client，Prepare 仅适配该协作者。移除 `GetCheckoutQuery`、外部查询型 `CheckoutViewService` 与失去消费者的旧 GET record 模型。详见[组件说明](../paytech/acquiring/docs/requirements/20260912-customer-checkout/implementation.md)。
- **合同与边界：** HTTP 路径、Path/Query、默认 includeConsumer=false、JSON Schema、错误与只读语义不变，不增加 GET Body；Java API 返回类型迁出 Models，Java 消费者须重新编译。继续保持最多两轮版本重验、冻结后不读创建快照、不重复加载商品和详情、单条 SQL 最多三表、外部查询无事务。未修改数据库、Payment 服务或其他 Commerce 用例业务规则。
- **验证：** 原独立 class 合同测试先失败；完整 JDK 25 `clean verify` **364/364**，0 失败/错误/跳过，JaCoCo 行覆盖 **91.17%（3076/3374）**，90% 门槛通过。最后 Controller 字段命名调整后，GET HTTP 与架构/模块门禁 **38/38** 再次通过。覆盖无调用方事务、受限字段序列化往返、未知 Payment 状态失败关闭，并保留既有真实本地 MySQL、按需查询、并发重读、消费者回填及支付查询回归。OpenAPI 结构比对只有 GET 描述变化，Schema 与其余合同完全相同；差异空白检查通过。
- **仓库级检查：** `npm --prefix requirements-site test` **136/137**，唯一失败为既有 Risk 模型统计断言仍要求 78 表/944 字段，与当前共享台账不一致；该失败在本次追加台账前已经复现，未修改相关 Risk 内容或测试。
- **交付状态：** 未提交、推送或部署，没有执行 SQL。本机验证日志：[证据目录](/Users/kenny/.codex/verification/commerce-get-checkout-conventions-20260916)。其余旧接口、Repository 形态与分层不因本次迁移自动视为已符合 2026-09-16 规范。


<a id="commerce-create-checkout-conventions-20260916"></a>

## Commerce Client Create Checkout 编码规范重构（2026-09-16）

- **范围与实现：** 用户指定 `ClientCheckoutApi#createCheckout` 按当前规范重构，并以 Payment pay 为代码参考。独立 class `CreateCheckoutRequestBody/ResponseBody` 及嵌套 Info；Controller/Converter 只处理协议，内部 `CreateCheckoutRequest/Response` 取代旧 Command 与同义请求/回执类型。`CreateCheckoutProcessor` 使用 Foundation `AbstractProcessor`，入口拒绝已有事务，原命令先比较并在终态提前返回；新建/恢复的业务检查和 Context 构造由 Processor 编排。详见 [Commerce 组件说明](../paytech/acquiring/README.md#当前-checkout-首片)。
- **保存与查询：** Context 直接持有同模块订单 `CommerceOrder`、商品、退款仓位、资金投影和 Session；创建 Service 以 REQUIRED 保存五类事实并参与命令检查点原子提交。原聚合 Repository 的多表写入移至 Service，写仓储及 SQL 按表拆分，Session 返回实际插入的 Token。原基础查询投影命名为 `CheckoutCreationView`；Order/Session 读取与两类投影身份查询拆开，完成事务保留各自行锁，不再为构建响应反复加载基础事实。CCT 继续只在数据库事务外调用，未修改 Payment 或其他系统应用。
- **兼容与恢复：** HTTP 路径、Header、JSON Schema、400/422 边界、USD/金额/期限、201 与 Location、Hash 和无版本标记持久化快照、三阶段恢复及稳定失败回执均保持；Java 契约调用方需更新 import/accessor 后重新编译。Commerce 沿用批准的 Claim/Lease/CAS 与数据库唯一键，没有照搬 Payment 来源提交键，也没有叠加 Redis 锁。数据库模型、DDL、实际结构和网关配置未修改。
- **验证：** 新增独立 class 与调用方事务门禁测试先在旧实现产生 2 项预期失败。最终 JDK 25 `clean verify` **366/366**，0 失败/错误/跳过，JaCoCo 行覆盖 **91.03%（3046/3346）**，原 90% 门槛通过。真实本地 MySQL 与 CCT HTTP Simulator 覆盖创建、字段/金额校验、重放、租约竞争、预检事务边界、Token 冲突回滚和恢复；OpenAPI/Java 双向漂移检查同时覆盖新 class 与保留 record。差异空白、修改的 XML 解析和组件文档链接检查通过。本机证据：[验证目录](/Users/kenny/.codex/verification/commerce-create-checkout-conventions-20260916)。
- **交付状态：** 未提交、推送、部署或执行 SQL 迁移；其他旧 Commerce 接口及持久化边界不因本次迁移自动视为已按新规范改造。

<a id="commerce-create-checkout-business-20260916"></a>
### 2026-09-16 CreateCheckout 业务主流程重构

- **本机提交（2026-09-16）：** 用户要求提交后，Commerce 记录为 `ee9c0c9`、Foundation 为 `00ace48`，包含本轮 Checkout 业务简化、公共 Money/Token 接入及金额注释/OpenAPI 说明同步。按用户要求，最后注释走查及本次提交未运行测试或其他验证；下方测试数字仅为前序执行证据。未推送、发布或部署，待迁移 SQL 未执行。

- **范围：** 用户明确确认仅保留 clientOrderId 业务防重，Gateway 暂不改；[需求与边界](../requirements/20260916/commerce-CreateCheckout业务重构/technique.md)为本次创建入口的后继决定，取代上一节继续保留创建租约和回执恢复的局部实现说明。
- **代码：** Processor 不再依赖 Key、Hash、Claim、Lease、恢复阶段及回执冻结；业务校验和 CCT 预检在事务外，Service 以 REQUIRED 一次保存五类事实，响应直接使用 Context。并发唯一键冲突映射业务错误；新建国家在订单列保存，相关查询、准备和取消已做适配，旧快照 Reader 仅服务历史订单。
- **模型与 SQL：** Commerce PDManer、生成器、物理 Manifest、目标 Schema 同步新增 consumer_country_code 可空列；增量 SQL 为 [ddl.sql](../paytech/acquiring/docs/requirements/20260916-create-checkout-business/ddl.sql)。不回填旧订单、不删除历史命令、不调整其他系统模型。
- **验证进度：** 新增两条行为用例在旧实现下均产生预期失败；模型门禁 17/17、Java 架构/API/历史 Reader 合同测试 51/51 通过，生产及测试源码编译通过，Mapper XML 和差异空白检查通过。Java 完整验证及本地 SQL 执行仍待完成，不能据此认定集成验证通过。
- **按 Processor 规范修正：** 对照 [processor-conventions.md](../paytech/payment/docs/development/processor-conventions.md)第 6/8/9/11 节，把输入金额关系校验收拢到 validate；主流程明确展示订单号检查、完整 Context、CCT 预检、本地保存、响应。对象使用具名 Builder 构造，订单占用查询归属 CommerceOrderRepository，Service 不再做期限判断；INSERT 条件与影响行数保护写入时仍未到期。Service 方法明确命名为 saveCreateCheckoutContext，保留 REQUIRED 和原有业务错误合同。
- **金额计算归属后续修正（2026-09-16）：** 用户确认后删除独立 CheckoutAmountCalculator 和 Processor 注入。内部 CreateCheckoutRequest.calculateAmounts 返回行金额及汇总结果，Item 复用公共 Money 进行纯计算；Processor 保留汇总范围校验、错误映射和 Context 构建，外部 Body 与 Service 不增加计算职责。原精度、整行优惠、历史快照及 422/VALIDATION_ERROR 合同保持，没有新增组件、缓存或外部依赖。
- **金额迁移验证：** JDK 25 限定 9 类 `clean package` **118/118**，无失败、错误或跳过；包括 26 项纯金额计算、多行顺序/省略优惠/汇总、13 项真实 HTTP 参数与金额拒绝、历史快照、API/OpenAPI 与架构门禁。生产和全部测试源码编译通过，两个 Web JAR 均不含旧 Calculator。文档和差异空白检查通过，[本机证据](/Users/kenny/.codex/verification/commerce-request-amounts-20260916)保存日志、报告与制品指纹。本轮未重复运行受下述结构缺失阻挡的数据库套件，不宣称全量落库回归通过；未执行 SQL、提交或部署。
- **本次修正验证：** JDK 25 编译、51 项架构/API/历史 Reader 合同测试通过；依赖字段顺序、修改的 Mapper XML 和差异空白检查通过。已补充写入时恰好到期拒绝及到期前一微秒成功的真实 Service 测试，但未通过数据库门禁：单条 HTTP 预检用例在 requireSchema 快速失败，确认本地 t_commerce_order 缺少 consumer_country_code，尚未执行任何业务步骤。完整数据库回归和这两条新增用例仍待结构准备，不能视为已通过。[修正证据](/Users/kenny/.codex/verification/commerce-create-checkout-processor-conventions-20260916)。
- **交付状态：** 已在本机提交，未推送或部署；本地结构迁移已向用户请求授权，尚未执行。证据：[验证目录](/Users/kenny/.codex/verification/commerce-create-checkout-business-20260916)。


<a id="commerce-runtime-properties-20260916"></a>

## Commerce 运行配置归集（2026-09-16）

- **实现范围：** 按公共配置规范将 CommerceRuntimeConfiguration 的三个 @Value 参数归入 CommerceCheckoutProperties、CommerceIdentifierProperties，通过 @EnableConfigurationProperties 注册并注入 Bean 工厂。配置类负责必填校验，HTTPS Origin 和 Snowflake 范围复用原组件校验；UTC Clock、业务与补偿共用的单例 ID 生成器及 Session Origin 冻结行为保持。
- **配置兼容：** 原三个 commerce.* 配置键和环境变量不变，无默认值；均按启动读取、修改需重启处理。README 补充配置清单；不增加 Nacos Config 加载、动态刷新或外部配置操作。
- **验证与交付：** 按用户要求未修改或运行单元测试，未执行构建或其他验证；未提交、推送、部署或执行 SQL。用户已有的 CheckoutSession 和 CheckoutSessionMapper 修改保留，本次未触及。


<a id="commerce-checkout-query-merge-20260916"></a>

## Client Checkout 查询并入订单查询（2026-09-16）

- **Implemented：** 用户确认后移除 ClientCheckoutApi#getCheckout 及独立响应、Service、Converter、Repository 查询、Mapper 查询和专用投影。两个订单 GET 补充 checkoutUrl、preparationStatus、amountStatus；复用原三表查询中的 Session 坐标，按原到期规则生成 URL。Customer API、Checkout 创建及 Session 写入/CAS 保持。合同见[本次技术边界](../requirements/20260916/commerce-订单查询合并Checkout/technique.md)。
- **文档与引用：** OpenAPI、README、需求目录与 COM-I02/COM-I03 投影同步；旧测试仅机械适配删除类型、路由及新字段，没有新增测试用例。
- **未验证与交付：** 按用户要求未运行测试、构建或其他验证，不声明编译或回归通过。未修改数据库模型、执行 SQL、配置 Gateway、提交、推送或部署。

<a id="commerce-get-order-processor-20260916"></a>

## Commerce Client 订单查询按公共规范重构（2026-09-16）

- **代码调整：** `ClientOrderApi#getOrder` 与同表示的 `getOrderByClientOrderId` 使用独立 `GetOrderResponseBody` class 和嵌套 `*Info`。Controller 通过 Converter 转换内部 Request/Response，`GetOrderProcessor` 继承 `AbstractProcessor`，按 Client 隔离查询并逐项加载本库事实，Context 仅在内部持有数据库对象。原 Converter 的业务状态与额度计算移入 Processor，移除订单详情只读事务和传给 HTTP 层的 OrderView。
- **关联读取收敛：** 原 OrderQueryService 只保留可退款金额这一简单本地查询，通过 Order/Refund Position 两表查询返回有限结果；不再为该入口加载商品、Session、支付、资金或历史创建快照。订单详情仍保留既有金额、状态、历史国家、原链接与到期规则；HTTP 路径、参数位置和 JSON 字段不变，Java 消费者需重新编译。
- **未验证与交付：** 按用户要求未运行测试、构建或其他验证，本轮未处理原 record 形态的单元测试适配，不声明编译或回归通过。未更改表结构、执行 SQL、提交、推送或部署。


<a id="commerce-order-response-shape-20260916"></a>

## Commerce 订单响应归组与创建方式（2026-09-16）

- **代码与合同：** 按用户确认，两个订单 GET 新增 creationMode，将会话归入 checkout.status/url/expiresAt，移除根级及 fundsAvailability.version；内部查询 Response 同步去掉这两处版本，数据库版本和 CAS 保留。GetOrderProcessor 按实际关联生成可空 checkout，Hosted Checkout 缺失会话仍拒绝，原链接到期规则不变。OpenAPI、README、需求目录与 COM-I02/COM-I03 已同步，[已支付示例](../requirements/20260916/commerce-订单查询合并Checkout/order-paid-response.json)为合成展示数据。
- **创建与模型：** 新创建入口显式写入 OrderCreationMode.HOSTED_CHECKOUT；新 enum 由 Client API 发布，查询通过显式注册的 Foundation CodeEnumTypeHandler 读取。t_commerce_order 新增 creation_mode VARCHAR(32) NOT NULL DEFAULT 'HOSTED_CHECKOUT'，模型/元数据/生成器、目标 Schema、Java 读写同步；模型现为 15 表、251 字段、24 唯一索引、12 普通索引、15 关系、4 图组，本轮未验证。DIRECT_PAY 仅保留模式值及查询表示，不代表直接支付入口、准备和接受流程已实现。
- **待迁移与未验证：** [增量 SQL](../requirements/20260916/commerce-订单查询合并Checkout/db/creation-mode.sql)仅准备文件，未连接数据库或执行。运行新代码前必须先在获授权环境加列，也不能据本文件推断其他历史迁移已完成。按用户要求，本轮未处理单元测试、未运行测试、构建或验证，不声明可编译或回归通过；前序 record 契约测试仍需迁移。未提交、推送、配置 Gateway 或部署，其他任务已有修改保留。


<a id="commerce-cancel-order-refactor-20260916"></a>

## Commerce Client CancelOrder 编码规范重构（2026-09-16）

- **支付事实判断归属：** OrderPaymentProjection 改为普通 class，Mapper 改为属性映射；成功 Attempt、正捕获金额或成功 Intent 的原组合判断移到 hasSuccessfulPayment()，Processor 调用后决定 REVERSAL_REQUIRED，原判断条件和数据库并发保护保持。未运行测试、构建或验证。
- **原对象同步修订：** CommerceOrder、CheckoutSession 改为可修改 class，Mapper 使用属性映射，创建/取消调用同步使用 getter。Repository 的 requestTermination/cancel 改为 void，确认 SQL 行数后通过 setter 更新传入原实例；取消 Service 不再接收重建结果或替换 Context，Context 保留 final 引用。此轮没有数据库结构变更，未处理测试或运行构建、验证。
- **Context 行为修订：** 按用户要求，将纯组合判断 canCancelLocally 从 Processor 移到 OrderCancellationContext，Processor 直接调用 context.canCancelLocally()；原判断条件、查询顺序与 SQL/CAS 保持。公共规范及 Payment 示例同步允许 Context 承载基于已加载数据的纯判断和计算。本轮仍未处理测试或运行构建、验证。
- **当前修订：** 用户明确指出取消不应保留请求幂等组件，要求按 processor-conventions.md 重做，取代此前“保留取消 Hash/Claim/Lease/恢复回执”的实现方向。删除 OrderCancellationIdempotencyService/Impl 和 OrderCancellation 快照结果类型；内部 Request 仅有 clientId/orderNo，Context 持有 CommerceOrder、可空 CheckoutSession 和按需 OrderPaymentProjection，取消链路不再使用 OrderRecord 或整单联查；不含请求 Key、Hash、命令 ID、Claim、租约、恢复阶段或结果快照。
- **代码与事务：** Processor 仅依赖 Repository、本地取消 Service、Payment Client；归属查询、已取消提前返回、新请求资格检查和 Context 构造都在事务外。Service 维护 REQUIRED 事务，基础单一次事务取消；需下游权威时先提交终止意图，事务外调用 Payment，再事务保存取消。Order/实际关联 Session 的 CAS 与支付绑定/投影版本、未成功条件保护并发，行数不符整体回滚，不自动重查重试。Hosted Checkout 缺少 Session 仍报数据错误，Direct Pay 没有 Session 时不执行会话 UPDATE、不返回 checkoutStatus；有实际会话则同事务关闭。取消专用联表加锁重读和 Processor 事务已移除，未新增 Redis；下游固定 Order 终止标识仍沿用 PAY-I12 既有合同，不来自 Client Header。
- **模型与迁移：** 新增 t_commerce_order.cancelled_at DATETIME(6) NULL，首次取消和状态在同一 UPDATE 写入；准备终止的原有订单关闭路径同步保存该时刻。模型、生成器/字段元数据、目标 Schema 和[人工 SQL](../requirements/20260916/commerce-CancelOrder接口重构/db/cancelled-at.sql)同步准备，未执行 SQL；历史时间仅从真实已取消 Session 回填，不从审计时间猜测。模型字段由前序 251 增为 252，表/索引/关系不变；未运行生成器检查或模型测试。部署前需先完成 creation_mode 与 cancelled_at 的迁移。本次未实现 Direct Pay 创建或支付入口。
- **接口与范围：** Idempotency-Key 仅保留入口格式校验，不由取消业务保存/冻结或校验同 Key 改目标；当前 Gateway 未新增请求幂等能力。已取消返回原取消时间，202/409 业务语义保留，响应去掉内部 version。API/OpenAPI、README、需求目录与取消时序同步；共享命令表、旧记录、Foundation 和 Refund/Customer 幂等保持，不执行 Schema 变更。
- **未验证及待接入：** 按用户要求未处理单元测试、未运行测试、构建、格式化或静态验证，不声明可编译或回归通过。旧 OrderModels/record、CommerceOrder 构造器、新 Processor/Context 依赖及取消命令 Key/租约/回执测试需后续适配。UnavailablePaymentTerminationClient 与 TODO(PAY-I12) 保持，真实终止、事件消费及后台唤醒未由本次实现。未执行 SQL、提交、推送或部署，见[交付记录](../requirements/20260916/commerce-CancelOrder接口重构/delivery.md)。


<a id="commerce-create-refund-20260916"></a>

## Commerce Client CreateRefund 接口规范重构（2026-09-16）

- **代码调整：** 创建 Controller 通过 Converter 使用独立普通类 Body 和内部 Request/Response；Processor 继承 Foundation AbstractProcessor，按需读取 CommerceOrder、原 RefundRequest，新申请才读取支付投影和退款位置。Context 及涉及的数据库对象改为普通类，对象持有纯校验；创建不依赖 OrderRecord 或 Checkout Session。
- **保存与重复申请：** 移除创建专用 Hash/Claim/Lease、命令回执与事务内整单重查，clientRefundId 原内容重试直接返回原 201 创建事实。Service 自行使用 REQUIRED，原子保存退款、额度预占与提交任务；唯一索引和条件 SQL/CAS 保护并发，写库成功才同步原对象。业务查询 View 保留订单号关联，写入 DO 只含本表字段；Position 写入收敛到所属 Repository。
- **任务与边界：** 保留补偿登记后的 Foundation 提交后唤醒、原 Handler 的事务外 Payment 调用及未知结果预占。Payment 实际接口、MQ 和 Client Webhook 仍待接入；本轮未扩展退款查询或 Handler 的独立流程设计。HTTP Key 仍必填且校验格式，业务不消费 Key；网关没有新增缓存能力。详见[技术说明](../requirements/20260916/commerce-CreateRefund接口重构/technique.md)。
- **未验证与交付：** 按用户要求未处理测试、未运行构建或验证，不声明编译或回归通过。原测试中的旧创建类型、构造器和 record accessor 尚需后续适配。未修改数据库结构、执行 SQL、修改 PDManer、提交、推送或部署；前序退款测试记录仅证明对应历史版本。


<a id="commerce-public-numbers-20260916"></a>

## Commerce 订单号与退款号统一公共 Token 格式（2026-09-16）

- **代码调整：** 新 orderNo、refundRequestNo 由 Commerce Processor 直接使用 Foundation PublicTokenGenerator 生成主体，分别拼接 ord_、rfd_，与 chk_ 一样总长 34 位；每次新建只生成一次。移除旧订单号生成接口及 Base64 实现，退款移除 UUID 生成。重复业务退款返回原编号。
- **读取兼容：** CreateCheckoutResponse 接受新主体及旧订单号，以供历史回执读取；历史创建快照 Reader 保持旧格式。对外查询输入不收窄，旧数据库编号不改写；Client 归属检查继续执行。OpenAPI 和 README 已同步新生成格式。两个现有 VARCHAR(64) 字段及唯一索引足够，无表结构变化。
- **验证与交付：** 按用户要求未处理测试、未运行构建或验证，不声明编译或回归通过。旧生成器和构造器相关测试待后续适配。未执行 SQL、改 PDManer、提交、推送、发布或部署。范围见[技术说明](../requirements/20260916/foundation-PublicToken公共生成/technique.md#commerce-orderno--refundrequestno-生成格式)。


<a id="commerce-list-order-refunds-20260916"></a>

## Commerce Client 订单退款列表接口规范重构（2026-09-16）

- **代码调整：** listOrderRefunds 改由继承 Foundation AbstractProcessor 的 ListOrderRefundsProcessor 编排。Controller/Converter 使用内部 Request/Response、独立普通类 ListOrderRefundsResponseBody 和嵌套 RefundInfo；没有新增 GET Body、空 Service 或额外 Context。
- **按需读取：** 只查询归属当前 Client 的订单主键；有游标时单表读取退款锚点并校验 Order/createTime；分页直接返回本表 RefundRequest。移除列表 Service 的只读事务、OrderRecord、两表 View 和旧 Page/Body 容器。首屏两次读取、续页三次，不加锁、不要求 Session、不加载支付/资金事实或调用外部服务。
- **合同保持：** 原 Path/Query、1–100 limit、创建时间/退款号倒序、r1 Cursor、金额字符串、退款字段和显式 null 继续使用；下一页取实际返回末项，列表允许显示相邻已提交时点的当前状态。其他退款入口独立流程未在本轮重构。说明见[技术文件](../requirements/20260916/commerce-ListOrderRefunds接口重构/technique.md)。
- **未验证与交付：** 按用户要求未处理测试、未运行构建或任何验证，不声明编译或回归通过。旧返回类型、构造器和 Service.list 测试需后续适配。未修改数据库结构或索引、执行 SQL、修改 PDManer、提交、推送或部署。

<a id="commerce-get-refund-20260916"></a>

## Commerce Client 按公开退款号查询接口规范重构（2026-09-16）

- **代码调整：** `getRefund` 改由继承 Foundation `AbstractProcessor` 的 `GetRefundProcessor` 编排，Controller / Converter 只转换和委托。内部 Request/Response 和独立 `GetRefundResponseBody` 为普通 class；不增加 Context 或空 Service。
- **按需读取：** 复用单表退款查询，再按同一 Client / orderId 只取 `order_no`。本入口不再调用 `RefundService.get`、联表 View 或只读事务，不加锁，不依赖 Session、支付或资金投影，不调用外部服务。按 Client 业务退款号查询的后继迁移见下一节。
- **合同保持：** 原路径参数、金额格式、来源、状态、时间、表示版本和显式 null 不变；未知、跨 Client 及关联订单不可见沿用 `REFUND_NOT_FOUND`。范围见[技术说明](../requirements/20260916/commerce-GetRefund接口重构/technique.md)。
- **金额组装修订：** 详情及列表 Processor 直接将原 BigDecimal 传入 Money，删除文本往返解析。用户随后删除公开退款号详情的 try/catch，详情直接沿用统一异常处理；列表原有的窄异常转换没有在本次详情重构中调整。
- **未验证与交付：** 按用户要求未处理测试、未运行构建或验证。返回类型及 Controller 构造器相关测试需后续适配，不声明编译或回归通过。未修改表结构或索引、执行 SQL、修改 PDManer、提交、推送或部署。

<a id="commerce-get-refund-by-client-refund-id-20260916"></a>

## Commerce Client 按业务退款号查询接口规范重构（2026-09-16）

- **代码调整：** 新增普通类 `GetRefundByClientRefundIdRequest` 和继承 Foundation AbstractProcessor 的查询 Processor；Controller/Converter 仅适配，复用已有 `GetRefundResponse`、`GetRefundResponseBody` 和响应转换。
- **读取与清理：** 复用 CLIENT 来源、可信 Client / clientRefundId 的单表查询及关联订单号标量查询；移除旧 `RefundService.get`、只读事务、View/money 组装、领域 `RefundModels.View`、旧 API `RefundResponseBody` 和 Controller wire。没有新增 SQL、Context、锁、事务或外部调用；提交 Handler 原实现保留。
- **合同与金额：** 原路径、Query、响应字段、Client 隔离、Operations 不匹配及 REFUND_NOT_FOUND 语义保持。金额按用户最新要求直接调用公共 Money，不加 try/catch 或文本往返解析。范围见[技术说明](../requirements/20260916/commerce-GetRefundByClientRefundId接口重构/technique.md)。
- **验证与交付：** 按用户要求未处理测试、未运行构建或验证，不声明编译或回归通过；旧 Java 类型、Service 查询和 Controller 构造器相关测试待适配。未修改数据库或 PDManer、执行 SQL、提交、推送或部署。

<a id="commerce-get-refundable-amount-20260916"></a>

## Commerce Client 可退款金额查询接口规范重构（2026-09-16）

- **代码调整：** `getRefundableAmount` 改由继承 Foundation `AbstractProcessor` 的 `GetRefundableAmountProcessor` 编排。Controller/Converter 仅转换和委托，内部 Request/Response 及独立 `GetRefundableAmountResponseBody` 均为普通 class；无额外 Context 或 Service。
- **读取与清理：** 先按可信 Client / orderNo 读取 `CommerceOrder`，再按 orderId 读取 `OrderRefundPosition`，复用其金额校验与可退余额计算。两次读取不加事务或锁，不依赖 Session、支付和资金投影，不调用外部服务。删除旧 `OrderQueryService/Impl`、领域 `RefundableAmount`、`OrderWireConverter`、API `RefundModels`、联表 View 及专用 Repository/Mapper/XML 查询。
- **合同与金额：** 原 GET 路径、参数位置及五个 JSON 字段保持；未知或跨 Client 订单返回 `ORDER_NOT_FOUND`。金额直接使用公共 Money 输出，asOf 与 version 来自同一退款位置；未预占额度，创建退款仍按写入合同校验。说明见[技术文件](../requirements/20260916/commerce-GetRefundableAmount接口重构/technique.md)。
- **验证与交付：** 按用户要求未处理测试、未运行构建或验证，不声明编译或回归通过；Java 返回类型与 Controller 构造器相关测试待适配。未修改其他服务、Gateway、数据库结构或 PDManer，未执行 SQL、提交、推送或部署。

<a id="commerce-get-payment-20260917"></a>

## Commerce Customer GetPayment 接口规范重构（2026-09-17）

- **入口与编排：** 内部 GetCustomerPaymentRequest 移到 request 包，Converter 完成 Path 转换，Controller 只委托并转换响应。Foundation GetCustomerPaymentProcessor 按阶段查询、构造普通类 CustomerPaymentQueryContext、读取 Payment 和构造已有 CustomerPaymentResponse；共享 HTTP 响应保持。
- **查询与边界：** Session、CommerceOrder、OrderPaymentProjection 各读一次，Context 只持有订单、支付投影和 submissionId，派生固定归属的 PaymentReference。删除返回前的二次查库及版本变化 409；保留基于原期限的内存到期判断和 Payment 结果绑定校验。查询不加事务或锁、不产生接受/支付/补偿任务，未接入真实 Payment。见[技术说明](../requirements/20260917/commerce-GetPayment接口重构/technique.md)。
- **验证与交付：** 按用户要求未修改测试、未运行测试或构建验证，不声明编译、回归或真实支付集成通过。没有修改其他系统、Gateway、前端、数据库或 PDManer，未执行 SQL、提交、推送或部署。

<a id="commerce-submit-payment-20260917"></a>

## Commerce Customer SubmitPayment 接口规范重构（2026-09-17）

- **入口与去幂等：** 普通类 Body/Info、内部 Request/Response 和 Converter；Submit/GetPayment Processor 复用 Foundation AbstractProcessor。按用户追加要求移除支付 Idempotency-Key、指纹/Hash、命令认领/完成/响应快照、retryIdempotencyKey，以及 GET 的命令表查询；Preparation 及共享组件保留。
- **数据与事务：** 提交按需读取订单、会话、准确合同、支付投影及消费者存在性；Context 无卡、Token 或命令对象。Service 的 REQUIRED 事务保存订单版本与首次三份接受/网络证据，Processor 直接调用该 Service，已删除支付查询任务登记及额外事务包装。状态、版本、行数和唯一约束继续保护本地事实；同合同原接受复用。executePayment 复用已提交 Context 和原对象回写版本，不重复查询订单、会话或消费者存在性，只保留基于原期限的内存到期判断；本次未运行验证。
- **支付身份与查询：** 每次 POST 生成新的 submissionId，作为 Payment 来源业务编号；不承诺原请求重放。Payment 提交在本地事务后执行；其 Intent 资金保护仍需真实适配落实。查询直接使用当前订单/Intent/提交编号访问 Payment；未找到不代表在途操作未执行，不自动再次 POST。已按用户确认删除 CheckoutPaymentQueryHandler、专属测试及只供它使用的订单号查询方法/SQL；公开支付查询保留，其他补偿任务不变。详见[技术说明](../requirements/20260917/commerce-SubmitPayment接口重构/technique.md)。
- **SQL 与验证：** 新代码不读写接受表历史 command_idempotency_id，已准备[可空调整 SQL](../requirements/20260917/commerce-SubmitPayment接口重构/db/ddl.sql)，**未执行，旧 NOT NULL 库需先应用才能使用新写入**。旧基线 DDL/PDManer 尚未同步，新库也需此增量。仅删除被移除 Handler 的专属测试，未调整其余测试、未运行构建或验证，旧类型/构造器/幂等场景测试待适配；不声明编译、回归或真实支付接入通过。未改其他系统、Gateway、前端，未提交、推送或部署。

<a id="commerce-save-consumer-20260917"></a>

## Commerce SaveConsumer 接口规范重构（2026-09-17）

- **代码：** 独立普通类 Body/地址 Info、内部 Request/Response、Converter 和 SaveConsumerContext；SaveConsumerProcessor 复用 Foundation AbstractProcessor，直接读取 Session、CommerceOrder 与 ConsumerContext，不使用旧 OrderRecord 聚合。规范化归 Request，编辑/过期资格归已读对象，Protect 在事务外；Controller 仅转换委托。
- **保存：** CustomerCheckoutService.saveConsumerContext 使用 REQUIRED 事务更新订单版本与 Consumer；Processor 直接调用该本地保存，不再包裹命令事务。每次请求按订单/Session/Consumer 状态和版本做条件写入，SQL 成功后同步原对象，并发变化返回冲突。订单条件 UPDATE 提供行锁与 CAS，去掉仅为 Claim 顺序新增的显式锁查询。
- **合同与清理：** 按用户后续“去掉幂等”的要求，SaveConsumer 去除 Header 参数、Filter 强制 Key 校验、请求 Hash、Claim/Complete 及回执重放，不再读写命令表；准备开始后重复保存同样拒绝。原 PUT 路径、Body 和响应字段保持；删除旧 Save 模型、转换、Service 规范化/保存及聚合消费者 upsert，其他用例与共享命令机制、历史记录保留。详见[技术说明](../requirements/20260917/commerce-SaveConsumer接口重构/technique.md)。
- **验证与交付：** 按用户要求未改测试、未运行构建或验证，不声明编译或回归通过；旧 Java 类型及装配相关测试待适配。未改其他系统、Gateway、数据库结构或 PDManer，未执行 SQL、提交、推送或部署。

<a id="commerce-customer-get-query-20260916"></a>

## Commerce Customer GET 按需查询与对象规范重构（2026-09-16）

- **2026-09-17 后继修订：** 用户要求过期校验归 CommerceOrder，创建国家不再依赖幂等快照，并简化支付摘要查询。已移除 `CheckoutIdempotencySnapshotCodec`、创建快照 / View、专用查询 SQL 和 OrderRecord 创建命令字段；Customer GET、Client 订单查询及准备恢复直接使用订单或冻结上下文国家。PaymentStatus 承载纯协议判断，使用查询完成后的时刻校验 observedAt；不可用降级保持。保留用户移除 GET 自动重读的源码修改，当前版本变化直接返回冲突。未修改测试、执行构建或验证；旧 Codec、字段及构造器相关测试待清理。没有执行历史数据回填或删除共享幂等数据。
- **前序记录范围：** 以下两轮读取和旧快照回退为 2026-09-16 当时行为，由上述修订取代；其余边界保持。

- **代码调整：** `GetCustomerCheckoutProcessor` 直接从所属 Repository 查询 Session、CommerceOrder、ConsumerContext、支付投影和商品，不再通过查询 Service 装配 OrderRecord。冻结法律展示使用三表 `CheckoutLegalView`，不读取定价、Risk 与税快照。`CheckoutQueryContext`、内部 Response、OrderItem 和版本 View 使用普通 class；就绪、操作资格、轮询和版本比较归 Context。
- **合同与边界：** 保留原 HTTP 路径、参数、字段、Token 和到期错误；显式消费者回填、原文件链接及 Payment 摘要在事务外取得。GET 无写入、事务或锁，返回前只查版本，最多读取两轮。准备前优先读取订单国家，仅历史缺失值读取原创建快照。Converter 复用公共 Money 按订单币种输出金额，不重复解析。说明见[本次技术文件](../requirements/20260916/commerce-GetCustomerCheckout接口重构/technique.md)。
- **清理与保留：** 移除旧 Service 的 GET 专用版本比较及旧聚合 Mapper 版本查询；其他写用例仍消费的旧类型和准备逻辑保留，文件链接复用原 Integration Client。保留用户 Controller 注入字段顺序。
- **验证与交付：** 按用户要求未处理测试、未运行构建或验证，不声明编译或回归通过；内部响应访问器、Processor 装配和查询语句名相关测试待适配。未改其他系统、Gateway、数据库结构或 PDManer，未执行 SQL、提交、推送或部署。

<a id="commerce-ingress-filter-simplification-20260916"></a>

## Commerce 接入 Filter 精简（2026-09-16）

- **代码调整：** 按用户连续确认，删除自动注册的 `ClientApiIdempotencyKeyFilter`、`ClientApiNoStoreResponseFilter`、`ClientOrderRequestFilter`、`ClientRefundRequestFilter` 和 `ClientApiTrustedContextFilter`，不新增替代 Filter、Interceptor 或 Processor 拦截逻辑。Client 写接口保留原 Header 参数声明与框架必填绑定，专用 Key 格式/单值检查不再由 Commerce 执行；订单/退款业务防重不变。
- **缓存范围：** 被删除的缓存 Filter 同时覆盖 Client API 和 Customer Checkout，两类服务直连均不再由该 Filter 强制 no-store/no-cache。公开缓存头仍由 Gateway 承担，Gateway 代码和配置本轮未改、未验证；请求幂等中间件方案与启用仍待后续，不声明已完成接入。
- **Query/Body 与保留范围：** Commerce 不再由专用 Filter 拒绝 Order/Refund 的未知 Query、重复单值参数、GET/Cancel Body；这些公开协议限制归上游接入层，本次未改或验证 Gateway。字段注解、Processor 的归属及游标业务校验、Customer 自有请求处理保持。API 注释、OpenAPI、README 及职责决定同步；详见[技术说明](../requirements/20260916/commerce-接入Filter精简/technique.md)。
- **可信身份与日志：** 删除 Client 身份 Filter 的 Header 单值检查及 `clientId/credentialId` MDC 填充；没有业务消费者依赖该 MDC 输入。身份 Header 原有必填、long 类型绑定及 `@Positive` 约束、Client 资源归属校验和现有 Request ID 链路保持。认证、伪造 Header 清理与可信身份注入归 Gateway，本次未修改或验证其实现。
- **验证与交付：** 按用户要求未处理测试、未运行构建或验证，不声明编译或回归通过。旧直连 Query/Body 拒绝、缓存头、专有 Key 及部分游标错误断言需后续适配。未改 Foundation、数据库或 PDManer，未执行 SQL、提交、推送或部署。


<a id="platform-operations-management-refactor-20260917"></a>
### Platform OperationsManagementApi 接入重构（2026-09-17）

- 范围：`OperationsManagementApi` 的 16 个入口（8 个写命令、8 个查询）和 Operations BFF 的实际消费者。API 路径、JSON 字段、权限 Code、Controller `@Audit` 声明和成功状态保持；请求/响应及嵌套协议对象迁到 `api.operations.request/response` 普通 class，补齐中文字段与 `@param/@return` 合同。删除两个 Web 管理 Processor 和无消费者的人员 Provisioning Service。
- 调用链：Controller/Converter → Access Control 独立 Processor.process。人员与角色写入 Context 持有原 Account/Member/Password/Role；`OperationsAccountService` / `OperationsRoleService` 仅组合本地表保存，不调用其他 Service 或外部依赖。复用并扩展已有按表 Repository，新增角色权限 Repository；SQL 成功后回写原对象状态/版本。人员、角色联合结果使用 `OperationsAccountView` / `OperationsRoleView`；现有目录聚合查询 Repository 及共享授权 Policy 的进一步拆分不在本次范围。
- 原子性与权限：保留账号/成员 CAS、角色聚合版本、角色数据库共享/排他保护、授予上限、系统角色保护、最后一个有效管理员保护及停用/删除账号时的会话/Refresh 撤销。角色权限恢复/移除检查写入行数。查询使用原只读 REPEATABLE_READ，写入使用 READ_COMMITTED，均保留 15 秒超时；`OperationsManagementTransactions` 统一事务完成观察和数据库并发异常转换。审计事实仍交给既有尽力采集/独立落库机制，不并入业务事务。
- 事务外步骤：创建人员的密码派生与 Vault 稳定邮箱 Token 准备在锁及本地事务前完成；锁键使用 Token 的受控 HMAC 摘要，不暴露邮箱或 Token 原文。唯一邮箱/角色编码冲突只识别归属的数据库索引，不把未知重复键当作成功。没有提交后外部业务动作或新增 MQ/补偿，因此示例的提交后推进不适用。
- 请求幂等：本次 8 个命令移除内部 API Header、BFF 绑定/转发及本地 Claim/Hash/previous/complete；HTTP 重放由 Gateway 负责。业务唯一性、版本、状态及数据库锁保留。此处没有独立业务操作身份，不增加原业务命令查询/重放分支，也不迁移其他管理接口或删除旧幂等表/组件。
- 锁：8 个命令统一继承 Foundation AbstractLockProcessor，不叠加 @ResourceLock，不重复 Redis 异常处理。4 个账号变更继续与认证共享 `lock:operations:account:{id}`；角色更新/删除及创建分别使用 `lock:operations:role:{id}` / `lock:operations:role-code:{code}`；人员创建从操作者＋请求 Key 改成 `lock:operations:create-account:{tokenDigest}`。复用 `operations.authentication.lock-wait-time/lock-lease-time`，默认 500ms/30s，无新配置项。角色及人员创建锁命名空间与旧进程不同，后续激活应协调旧写入口退出和消费者更新，本次未发布。
- 实际验证：JDK 25 执行临时聚合 reactor，`-pl :platform-support-test,:paytech-operations-console-bff-test -am -DskipTests clean test-compile`，Foundation 11＋Platform 7＋Operations BFF 3 共 21 个模块全部成功（`/tmp/plt-operations-management-final-compile.log`）。202 项静态比对覆盖路由/字段、模板入口、审计声明、原条件写 SQL、授权 Policy 保留和任务前无关源码修改保留；Platform/BFF `git diff --check` 通过。非 HTTP Processor 入口有实际 Bean Validation 调用。
- 验证边界：按用户要求没有新增/运行单元测试，只对已有消费者、编译引用和锁架构断言做必要适配。未运行 Spring/Jackson HTTP、MySQL 回滚、Redis 竞争/租约、审计异步落库或 Gateway 幂等集成回归；旧服务层重放测试预期需随 Gateway 验证任务迁移。编译与静态比对不代表上述运行行为已通过。未执行 SQL、安装发布 Maven 构件、提交、推送或部署，Gateway 本身没有改动。


<a id="processor-basic-validation-20260917"></a>

### 2026-09-17 Processor 基础字段校验归公共父类

- **实现：** 按用户确认，将 OperationsManagementSupport 的 Bean Validation 调用移到 Foundation AbstractProcessor.validateBasic。父类方法注入宿主 Validator，先检查请求非空，再验证默认分组及 @Valid 嵌套约束；参数失败使用 InvalidParameterException，未装配校验器失败关闭，不创建独立 ValidatorFactory。Common Core 新增标准 Validation API 与直接使用的 spring-beans 依赖，版本沿用 BOM，Provider 由宿主提供。
- **清理与边界：** 删除 Operations Management 16 个仅转发 support.validate 的重写以及 Support 中的 Validator 依赖。process 的事务入口检查及 validateBasic → validate → processRequest 顺序保持；业务授权、Context、状态/版本校验、本地原子写入、事务外步骤和锁实现未改。API Credential 的 validateBasic 特殊认证合同保持，缺失/畸形凭证仍按原逻辑返回 401。已同步[公共校验决定](decisions/2026-09-17-processor-basic-validation.md)、开发/工程规范、公共示例及薄规则。
- **实际验证：** JDK 25 临时聚合 reactor 执行 `-pl :platform-support-test,:paytech-operations-console-bff-test -am -DskipTests clean test-compile`，Foundation/Platform/Operations BFF 共 21 个模块成功（`/tmp/processor-basic-validation-compile.log`）；另执行 `-pl :paytech-commerce-web,:paytech-payment-web -am -Dmaven.test.skip=true clean compile`，Foundation/Commerce/Payment 共 23 个模块成功（`/tmp/processor-validation-consumers-compile.log`）。静态比对确认 16 处只删除重复校验、模板顺序保持、认证特例与锁源码未改变；相关 diff --check 通过。工作期间其他任务对 HttpTestInvoker 的修改保留。
- **验证限制：** 按用户要求未新增或运行单元测试，未运行应用启动、HTTP、数据库及 Redis 回归；编译成功不代表这些场景已验证。手工 new 的 Processor 若使用默认 validateBasic，需先显式 setValidator；其他模块的既有业务校验与测试装配没有批量迁移。未安装/发布制品、执行 SQL、提交或部署。


<a id="platform-permission-management-refactor-20260917"></a>

### 2026-09-17 PermissionManagementApi 接入与用例重构

- **入口与对象：** 7 个接口统一 Controller → Converter → Access Control 的 7 个具体 Processor.process；4 个查询继承 AbstractProcessor，3 个写操作继承 AbstractLockProcessor。API 原 Models/record 拆为 request/response 下 11 个 class/interface，保留七条路由、JSON 字段、三类引用多态及成功 HTTP 200，补齐中文方法/字段说明。内部 Request 使用父类 Bean Validation，不重写基础校验；权限详情组合使用 PermissionDetailView，跨模块输出不暴露 DataObject。
- **本地保存：** Create/Update/ChangeStatus 各有 Context，持有 t_permission 原对象；PermissionManagementService 只依赖 PermissionRepository。创建固定 INACTIVE，不授予持有人；更新只改风险等级，未变化不写库、不增版本、不追加审计；状态命令拒绝相同状态，保留引用及受保护管理权限规则。Permission DataObject 移入 core/dal/dataobject，Repository 在 CAS 成功后回写风险、状态、版本和时间，拒绝版本溢出；引用查询映射改为 class。旧 ConfigurationCatalog 的未使用权限写/读门面及命令模型移除，其他消费者的共享锁与委派查询继续保留。
- **事务、锁、审计：** PermissionManagementTransactions 统一 SERIALIZABLE/15 秒本地写事务、审计事务观察与数据库并发异常；普通查询保持无新增事务。逻辑锁均为 permission:{permissionCode}，公共模板负责 Redis 异常与释放；权限排他行锁继续协调绑定/委派共享锁，唯一约束及版本 CAS 保留。三个 Controller 的写审计及四个查询审计声明保持；更新/启停在原对象变更前冻结 typed before 值，成功保存后登记事实。本轮没有事务外业务调用、没有 Schema 变化。
- **请求幂等与调用方：** Platform 三个写接口和对应 BFF Controller/Client 删除 Idempotency-Key 参数、Hash/prepare/claim/complete/replay；Gateway 的职责按已确认全局决定执行，本次未改 Gateway 或历史命令数据。BFF 只改本接口及共享转换器的 Permission 类型/getter，已有其他改动保留。API/Mapper 类型迁移同步机械调整现有架构测试引用及注解锁数量，没有新增测试用例。
- **发布边界：** 新 PermissionManagementProperties 使用 ps.access-control.permission.lock-wait-time/lock-lease-time，默认 500ms/30s。物理逻辑部分由旧 permission:{code} 变为模板的 lock:permission:{code}，后续发布须协调停止旧写入口再切换，不能假定混合版本仍使用同一 Redis 锁。未发布、部署或安装制品，未执行 SQL 或提交。
- **实际验证：** JDK 25 聚合 reactor 的 `-pl :platform-support-test,:paytech-operations-console-bff-test -am -DskipTests test-compile` 最终通过，Foundation/Platform/Operations BFF 共 21 模块成功（/tmp/permission-management-final-compile.log）。此前已 clean 重建目标；最终类型/import 修正后重新编译全部受影响源码与测试源码。154 项静态合同检查通过，覆盖路由、原字段/注解、全部审计声明、模板/幂等清理、授权逻辑、原查询 SQL/锁语义、CAS 回写及 BFF 修改范围；Platform/BFF diff --check 通过。
- **限制：** 按用户要求未新增或运行单元测试，也未执行 Spring 启动、HTTP/JSON、数据库、Redis 并发及审计回归。旧接口请求幂等的运行期测试语义未迁移，后续应按 Gateway 职责调整；编译与静态检查不能代替上述运行验证。


<a id="platform-tenant-access-refactor-20260917"></a>

### 2026-09-17 TenantAccessManagementApi 接入与用例重构

- **入口与校验：** 8 个接口统一 Controller → Converter → Access Control Processor.process；7 个查询使用 AbstractProcessor，安全状态写入使用 AbstractLockProcessor。API 的 9 个 DTO 拆为 request/response 下的 class，补齐中文方法/字段、@param/@return，保留原路径、参数位置、响应字段和值比较语义。内部 Request 复用父类 Bean Validation；严格 JSON 解析器同步新请求包，并通过属性构造器保留 expectedVersion 缺失/空值拒绝。原 API import 整理及 BFF 其他已有修改保留。
- **查询与本地保存：** 成员、角色授予、权限汇总、来源使用 4 个组合 View，状态解释收敛到 View；普通租户结果使用跨模块摘要。Repository 按租户、成员、成员角色授予入口拆分，复用原 Mapper SQL；所有 SQL 的条件、排序、分页、COUNT、时间边界、行锁和缓存配置未变，仅替换组合结果映射类型。ChangeTenantSecurityStatusContext 持有原 TenantAccess class；Service 只保存租户安全状态，CAS 成功后 Repository 更新原对象的安全状态、版本和时间，不修改来源及授权事实。
- **事务、锁与审计：** 复用 OperationsManagementTransactions 的 RR 只读快照、READ_COMMITTED 写事务及 15 秒上限，数据库并发异常与 Redis 异常使用已有公共处理。当前操作者授权先于目标读取；安全写保留行锁、旧版本冲突与版本上限，同版本同状态直接返回、不增版本及变更审计。8 个 Controller 审计声明保持；保存前冻结 before 值，成功后登记审计，持久化继续提交后尽力执行。没有事务外业务调用、Schema 或 SQL 数据变更。
- **幂等与调用方：** Platform/BFF 安全状态入口移除 Idempotency-Key、请求摘要及服务本地 prepare/claim/complete/replay，Gateway 请求幂等职责沿用已确认决定；没有修改 Gateway、历史命令记录或 BFF 页面模型。既有架构测试只做删除类引用与注解锁数量的机械适配。
- **配置和切换：** TenantAccessManagementProperties 的 ps.access-control.tenant-access.lock-wait-time/lock-lease-time 默认 500ms/30s，独立于旧注解配置。暂停/恢复统一使用 lock:access-tenant:{tenantId}；旧版本使用 access-tenant:{tenantId}，后续发布需要协调切换旧写入口，不能假定混合版本共享 Redis 互斥。来源投影沿用原 source/account 锁与本库并发保护，本轮不改其合同。
- **实际验证：** JDK 25 临时聚合 reactor 执行 `-pl :platform-support-test,:paytech-operations-console-bff-test -am -DskipTests clean test-compile`，Foundation/Platform/Operations BFF 共 21 模块成功（/tmp/tenant-access-management-final-compile.log）。139 项源码静态检查通过，覆盖路由/字段、SQL/映射、授权范围、公共校验/事务、Context/原对象回写、审计及修改范围（/tmp/tenant-access-static-checks.json）；Platform/BFF diff --check 通过。
- **限制：** 按用户要求未新增或运行单元测试，未执行 Spring 启动、HTTP/JSON 运行验证、数据库/Redis 并发及审计回归；旧测试中的服务侧幂等与锁键断言尚未迁移。编译和静态核对不代表上述行为已运行验证。未安装、发布、部署制品，未执行数据库操作、提交或推送。


<a id="platform-tenant-projection-refactor-20260917"></a>

### 2026-09-17 TenantProjectionApi 接入与用例重构

- **入口与合同：** 开通、来源状态推进、恢复查询统一 Controller → Converter → Access Control 的 ProvisionTenantProcessor、ProjectTenantStatusProcessor、GetTenantProjectionProcessor；API 的 3 个 record 拆为 request/response class，补齐中文方法/字段及参数返回说明。三条路由、字段、类型、校验约束和成功 HTTP 200 保留；严格 JSON Reader 同步新包，来源序号缺失/空值仍拒绝。CCT 投影调用方只适配 import/getter；联编另修复此前认证 DTO 迁移遗留的 ClientDraftAccountClientImpl getter 调用，不改其授权规则。
- **Context 与本地保存：** ProvisionTenantContext 持有 Tenant、OWNER Member、入驻 Role/Grant 四个实际对象；ProjectTenantStatusContext 持有原 Tenant/Member 与首次 ACTIVE 才创建的正式 Role/Grant。Service 只依赖四个按表 Repository；首次开通四表写入、首次 ACTIVE 的旧授权撤销/正式角色与授权创建/来源 CAS 各在同一事务。Repository 校验影响行数，Tenant CAS 成功后回写原对象来源状态、序号、摘要与本地版本。原 Mapper XML、SQL、参数、行锁和共享文件授权查询不变，未改 Schema。
- **锁、事务与校验边界：** 首轮保留注解入口，用户指出未继承后已补齐：ProvisionTenantProcessor 与 ProjectTenantStatusProcessor 直接继承 AbstractLockProcessor，查询继承 AbstractProcessor；内部 Request 字段由公共 validateBasic 校验，Provision 第一版关系判断放 validate。两个写入口删除自定义 process 与注解锁，通过 lockCommand 一次提供来源 tenant:source:client 与账号 lock:customer:account 原完整键，通过 processWithinLock 开启本地事务。仍显式复用并启动校验原服务级 Wait/Lease。TenantProjectionTransactions 保持 RR 查询、READ_COMMITTED 写入、15 秒超时及数据库并发异常；来源判断、Context/Service、SQL/CAS、审计及事务内 Account → Tenant → Member/Role 顺序未变，没有外部业务调用。
- **公共锁模板增量：** AbstractLockProcessor 增加 LockCommand.tryLocks，完整 Key 校验、排序去重，一次共享等待预算，普通 RLock 和有限显式租约，所有锁取得后才执行业务，失败及结束逆序释放已取得的锁。单资源 lock/tryLock 工厂的签名、毫秒参数、lock: 前缀、SpinLock 和 Watchdog 支持保留；共用中断/Redis 异常转换和持有者安全释放。模板不继承旧注解的代理执行链门禁及指标，其他消费者未迁移。工作区既有 Foundation 公共校验、异常改动以及并行测试支持包修改保留。
- **业务来源恢复：** 保留来源唯一性、原申请人绑定、sourceVersion/payloadHash 比较、旧消息返回当前位置、同版异内容冲突、跳版本未就绪及终态不复活；开通重放先于首次 ACTIVE 账号资格。来源推进不恢复独立 Tenant 安全状态、Member 状态或撤销的角色授予。该业务恢复合同独立于 Gateway HTTP 请求幂等，没有增加服务侧请求幂等组件。PLT-I17 专项合同的账号锁键文字同步既有 Client 认证前缀。
- **实际验证：** 首轮 23 模块编译及 88 项静态核对作为前序检查点保留。继承补齐后，JDK 25 聚合 reactor 重新执行 `-pl :platform-support-test,:client-contract-test -am -DskipTests clean test-compile`，Foundation/Platform/CCT 共 23 模块成功（/tmp/tenant-projection-inheritance-compile.log）；javap 确认两个写类直接继承 AbstractLockProcessor 且没有自行声明 process/processRequest。39 项源码核对通过，覆盖父类入口、两资源键、总等待预算、部分获取失败释放、异常、配置以及业务/SQL/API/CCT 未变化（/tmp/tenant-projection-inheritance-static.json）。本轮增量 diff --check 通过；任务开始时 API JavaDoc 的三处行末空格未改动。既有测试仅机械适配注解数量、类引用及配置构造参数，没有新增场景。
- **限制：** 按用户要求未新增或运行单元测试；未做 Spring 启动、HTTP/JSON、MySQL/Redis 并发及审计运行回归。编译与源码核对不代表上述行为已运行验证。未安装、发布、部署制品，未执行数据库操作、提交或推送。

<a id="channel-account-mid-query-requirement-20260921"></a>

## Channel 账号与 MID 能力查询本地实现（2026-09-21）

- **授权与合同：** 文档归档后用户明确要求研发并遵守开发/单测规范；确认消费者国家、账号 JSON 含渠道 API 凭据使用 Vault、原交易允许 SUSPENDED/CLOSED 而 DRAFT 拒绝。随后明确只授权初始化本机 mor-local 的专用测试库。入口为[需求](../requirements/20260921/payment-渠道账号与MID能力查询/requirement.md)、[实施合同](../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)、[确认记录](../requirements/20260921/payment-渠道账号与MID能力查询/delivery.md)和[范围决定](./decisions/2026-09-21-channel-account-mid-capability-queries.md)。
- **源码：** Channel 增加 QueryMidCandidatesProcessor / GetChannelExecutionConfigProcessor、内部 Request/View/Context、开放能力目录、五表 Repository/Mapper/类型映射及正式 VaultClient 读取。按完整维度逐代码选择唯一最高精确度，false 可覆盖，损坏配置不当成空候选；新支付重新检查状态、绑定、动作和精确金额，原交易/恢复保留冻结账号。数据库使用短 REPEATABLE_READ 只读快照，结束后才 Reveal。未新增 HTTP、运营写入口、交易记录、重试或查询缓存。
- **正式装配：** Web 引入 Channel 并扫描正式 Mapper，application.yml、Nacos 模板及配置清单同步能力目录和 Vault Client。完整正式应用启动暴露原有 IdGenerator Bean 缺口，已按既有 Snowflake 配置补齐强类型绑定和正式 Bean；未使用测试专用生产替身。
- **模型与 SQL：** 正式 PDManer、确定性生成器、独立 oracle 和完整 Schema 同步五表目标；整体仍 27 表，现为 345 字段、38 非主键唯一索引、44 访问索引、63 关系、7 Diagram Group。Channel 五表共 44 字段；移除 Health State 目标，但 Routing 历史健康快照列保留待独立迁移。未准备或执行未经盘点的非空业务库增量 SQL。
- **数据库执行：** 经用户明确批准，人工执行 paytech-payment-test/src/test/resources/init/ddl.sql，仅创建 127.0.0.1:13306 的 mor_payment_channel_test、专用 SELECT/INSERT/UPDATE/DELETE 账号及五表。模型、正式 SQL 与实际库的字段、类型、可空、排序规则、注释及索引逐项一致（/tmp/channel-verify-db.py）；不含物理 FK/CHECK。未迁移、清理或删改 mor_payment 及其他业务库。测试只接管空基线并在公共 AfterEach 清理本例数据；最终查询五表总行数为 0，专用账号的 Schema 权限仅为该库 SELECT/INSERT/UPDATE/DELETE。
- **实际验证：** JDK 25 `./mvnw -q -pl paytech-payment-test -am clean verify` 成功，56/56 测试通过（候选入口 14、执行配置入口 30、纯能力规则 9、架构 3），无跳过。正式 PaymentApplication 启动，使用真实本机 MySQL/Redis；Vault 为测试独立 HTTP Server 的合成 DTO，Payment 仍走正式 VaultClient，观察器验证实际发送线程无数据库事务。覆盖跨 OE、状态、换绑、重复精确度、非法值、金额闭合边界、Vault 拒绝/损坏响应、敏感 Payload 结构和异常不泄漏。日志 /tmp/channel-final-verify.log；JaCoCo Channel 行覆盖 245/249（98.39%），分支 178/217（82.03%），不代表整个 Payment 覆盖率。
- **模型验证：** `generate-payment-model.mjs --check` 通过；payment-channel-query、payment-model、payment-execution-simplification 共 18/18 通过，日志 /tmp/channel-model-tests.log。架构测试限制 Channel 公开类型、五表所有权与 Maven 依赖无环，Channel Enforcer 同时检查真实传递依赖。
- **前序文档检查：** 初次文档归档的 34 处链接、字段与目录语法检查已通过；当时需求站 site-content 72 项中 66 通过、6 项既有失败，前后失败集合一致（/tmp/channel-requirement-content-baseline.log、/tmp/channel-requirement-content-after.log）。本次最终重跑仍为 72 项、66 通过、6 项失败，失败名称集合完全一致，没有新增失败（/tmp/channel-final-site-content.log）。7 份本次交付文档的 87 处本地链接与锚点、JSON 语法及范围内空白检查通过。此次 Java/模型通过不消除那些独立的历史失败，也不据此宣称需求站全量通过。
- **未接入与限制：** 现有 RouteService 占位实现没有被本需求替换；可信 scheme/MCC 来源、Routing 策略、真实协议动作、Operations 管理、共享业务库迁移与真实 Vault Allowlist/账号写入均需后续接入。本次只交付受信模块公开查询，未验证真实 Vault/KMS 或外部渠道凭据。Vault 策略 JSON 是待环境维护者合并的窄片段，未部署。未提交、推送或部署服务。


<a id="risk-flow-management-refactor-20260921"></a>

## Risk DecisionFlowManagementApi 规范重构（2026-09-21）

- **入口与模型：** 保存、静态校验、整体删除分别由 `risk-decision` 的 `SaveFlowProcessor`、`ValidateFlowProcessor`、`DeleteFlowProcessor` 承担；Controller/Converter 仅适配。管理 Body/响应迁到独立普通 class，共享图结构和四个流程 DataObject 改为 class；HTTP Method/Path、JSON 字段、表达式多态标记、错误映射及 MyBatis 构造映射保持。共享查询 DTO 的引用同步适配，未把整个查询链列为本次重构完成范围。
- **对象与持久化：** `SaveFlowContext`、`DeleteFlowContext` 持有实际 DataObject；根归属、头版本、编辑基准和删除资格归根对象，节点/连线验证版本归属。`DecisionFlowManagementService` 仅调用四个按表 Repository，组合根、不可变版本、节点和连线写入；不重复查询或调用其他 Service。根 CAS/状态 SQL 及影响行数检查成功后才同步 Context 中的原根，未新增写入 View 或 HTTP DataObject 暴露。
- **事务与锁：** 两个写 Processor 继承 Foundation `AbstractLockProcessor`，沿用完整 `decision:flow:pointCode:flowCode` 命名空间、普通 RLock、既有有限等待/租约及 5 秒本地事务。准确引用继续按规则根、规则版本、拒绝原因、变量顺序保护；事务内仅执行本地数据库及纯计算。校验使用只读 REPEATABLE_READ，无写入或写锁。无外部业务步骤、提交后推进或 MQ/补偿，因此对应示例检查不适用。
- **用户确认的幂等迁移：** Risk API 和 Operations BFF 移除保存/删除 Header 绑定与转发、请求摘要及 Claim/Complete/响应回放调用。重复创建按永久编码冲突、旧编辑基准按版本冲突、重复删除按已删除冲突处理；业务唯一索引、CAS 和历史事实保留。这两个操作没有独立业务命令身份，不新增等价本地回放机制。既有命令表、历史记录及其他入口未迁移；修订见[接口合同](../requirements/20260912/operations-console-决策流配置/api.md)和[数据边界](../requirements/20260912/operations-console-决策流配置/data.md)。本轮没有修改或验证 Gateway 请求重放实现/启用状态。
- **实际验证：** JDK 25 执行 Risk 全 reactor `./mvnw -DskipTests clean test-compile` 成功（含既有测试源码编译）；临时消费者 reactor 使用当前 Risk API 执行 `-pl :paytech-operations-console-bff-web -am -DskipTests compile` 成功。静态检查通过：三个路由、四个管理 DTO 及九个共享结构的字段/访问器、JSON 多态标记、四个 DataObject 的 10/10/13/11 个构造字段与原 XML 对齐、Mapper XML 无改动、写 Processor 锁键/有限参数/超时，以及增量空白检查。
- **限制与交付：** 按用户要求没有新增、修改或运行测试；BFF 本轮只编译生产源码，其旧测试消费者及请求回放断言需在恢复测试时适配。没有执行 Spring/HTTP/JSON、MySQL 事务回滚、Redis 并发运行验证；编译和源码核对不能代替这些证据。未执行 SQL、修改数据库结构、提交、推送或部署，未声称现有环境已切换。


<a id="risk-flow-query-refactor-20260921"></a>

## Risk DecisionFlowQueryApi 规范重构（2026-09-21）

- **范围与入口：** 七个 GET 查询迁到 `risk-decision` 的六个具体 Processor：配置能力、列表、详情、历史、实际规则引用、旧顺序图；当前详情与指定版本详情共用 `FlowDetailProcessor`，可选准确版本 ID 表达查询目标。Controller 只做身份/标量适配并调用公共 `process`，内部 Request 使用普通 class 和基础校验；移除旧 Web `DecisionFlowQueryProcessor` 及编排多个 Service 的 `DecisionFlowService/Impl`。
- **结果与业务判断：** 六个顶层响应迁入 `api.decision.response` 普通 class，五个共享查询结构改为 class；组合查询返回所属模块的具名 View，单表历史条目使用 `FlowHistoryItem`。当前入口的删除可见性、准确版本归属和历史上界由流程根校验；`LegacyPointFlowContext` 持有实际点、版本与绑定，验证完整连续顺序并投影旧图。配置能力中的变量用途筛选和运算符投影归 Owner View，Web Converter 仅转换字段。
- **读取边界：** 保持全部查询只读 REPEATABLE_READ 快照，无 Redis 锁、写锁、写入或嵌套 Processor 调用。流程根、版本、节点、连线按表 Repository 读取；旧点版本与绑定补充各自只读 Repository，复用原 SQL。根列表仍沿用既有当前版本名称 JOIN 筛选，总数/当前页/引用与变量均在相应查询快照内。根/版本/引用记录不越过 Owner JAR；准确规则读取保留原 Hash/闭包验证、退休/删除历史资格和当前变量展示。
- **合同与消费者：** 七个 Method/Path、Header/Path/Query 参数、响应字段、字符串 ID/时间、分页排序/固定上界、LIKE 字面匹配、旧绑定 201 条读取上限、原条件多态与错误转换保持。Operations BFF 更新 DTO 转换、精确响应形状注册和有界响应读取，Risk 的详情响应容量 Advice 同步类型。本轮没有修改查询业务合同、管理写入、Mapper XML 或 Schema。
- **实际验证：** JDK 25 下 Risk 全 reactor `./mvnw -DskipTests test-compile` 成功，包含 55 个既有测试源码文件的编译；使用当前 Risk API 的临时消费者 reactor 执行 BFF Web 及其上游 `-DskipTests compile` 成功。静态核对通过：七个 GET 路由与参数签名、六个根 DTO 和五个嵌套结构字段/访问器、Mapper XML 字节一致、六个 Processor 的只读快照及无写入/锁/嵌套调用、增量空白检查。对应日志为 `/tmp/risk-flow-query-final-build.log`、`/tmp/risk-flow-query-bff-build.log`；静态脚本为 `/tmp/check-flow-query-refactor.py`。
- **未覆盖项：** 按用户“单元测试先不做”要求，没有新增、修改或运行测试；BFF 本轮只编译生产源码，旧测试 DTO 引用留待恢复测试时适配。未执行 Spring 启动、HTTP/JSON 序列化、真实 MySQL 快照/分页及并发回归；编译与源码检查不代表运行验证。写入原子性、CAS 回写、请求重放和事务外推进对本次纯查询不适用；未执行 SQL、提交、推送或部署。


<a id="platform-integration-http-execution-20260921"></a>

## Platform Integration 配置与 HTTP 执行归属（2026-09-21）

- **2026-10-13 后继：** 单次执行限制与五表草案已由[标准 API 与流程决定](./decisions/2026-10-13-platform-standard-api-flow.md)取代，当前状态见[对应条目](#platform-standard-api-flow-20261013)；本条保留 2026-09-21 记录。
- **已确认方向：** 用户选择配置管理和执行均放在 Platform，Payment 通过内部 HTTP 请求 Platform 完成渠道调用，取代调用方嵌入公共执行 JAR 的建议。[决定](./decisions/2026-09-21-platform-integration-http-execution.md)与[执行修订](../requirements/20260914/platform-support-外部API配置与执行/platform-http-execution-amendment.md)已记录；项目上下文、应用架构、Platform 数据库入口与需求导航同步路由。
- **边界：** Payment 继续拥有渠道账号/MID/能力、冻结路由、交易状态和业务恢复；Platform 只负责受控配置与单次协议执行，无执行流水、业务幂等及自动重试。两个 HTTP 边界的超时均可能结果不确定。凭据与卡材料交接、获准调用范围、正式 Wire、五表详细物理设计继续评审，未自动授予 Platform 新 Vault 权限。
- **交付范围：** 本轮只更新设计和文档路由，未改 Payment/Platform Java、PDManer、SQL、Pencil、环境或策略；既有 Payment 查询实现保留。未执行数据库操作、服务部署、提交或推送。
- **文档验证：** 决定、执行修订、通用合同、需求和确认记录共 5 份文档的 104 处本地链接/锚点与空白检查通过；受影响路由文档的 diff --check 通过。未运行 Java/数据库测试，文档校验不代表内部 HTTP 执行已实现。

<a id="risk-release-management-refactor-20260921"></a>

## Risk DecisionReleaseManagementApi 规范重构（2026-09-21）

- **范围与入口：** 列表、详情、创建 Candidate、静态校验四个接口分别由 `risk-decision` 的 `ListTestReleasesProcessor`、`GetTestReleaseProcessor`、`CreateTestReleaseProcessor`、`ValidateTestReleaseProcessor` 编排；移除旧 Web `DecisionReleaseManagementProcessor`。Controller/Converter 仅适配，内部 Request 由公共 Processor 执行基础校验。两个请求、四个顶层响应及五个共享响应结构使用普通 class；四个 Method/Path、Body/响应字段、字符串 ID/时间和错误码保持。
- **对象与保存：** `DecisionRelease`、`ReleaseAuditEvent` 改为 class，保留原构造字段顺序及非空约束；根完整性、初次校验资格归根对象，原操作者/原版本恢复判断归 `ValidateTestReleaseContext`。创建/校验 Context 持有实际根与审计记录，响应直接从同一批事实派生。`DecisionReleaseService/Impl` 仅通过单表根/审计 Repository 保存，不重复查询或调用其他 Service；CAS 和影响行数成功后更新原根。
- **查询与内容：** 多表读取由 Owner `DecisionReleaseReader` 组合根、审计、点、点版本、绑定与规则版本 Repository，输出内部具名 View；既有规则闭包读取仍复用 Owner 能力。`DecisionReleaseAssembler` 只核验已加载事实和组装规范投影，不查询数据库。列表和详情使用只读 REPEATABLE_READ；旧字段 ID、Point/Rule Set/Release 内容 Hash、退休 Action 历史读取和固定八项静态检查保持。
- **事务与锁：** 创建沿用 `decision:rule-set:<原资源坐标>` 普通 RLock、既有有限等待/租约和 5 秒本地事务，根/初始审计同事务写入；全局发布编号仍在唯一 CHECKOUT_PREPARE 点锁内分配并受唯一索引保护。Validate 使用 `decision:release:<releaseId>` 实际资源锁，事务 A 记录开始事实、只读快照装载、事务外纯检查、事务 B 原子保存八项检查及必要拒绝事件；两个写阶段均为 5 秒短事务，数据库发布行锁和状态/版本 CAS 保留。没有外部业务调用、提交后外部推进或 MQ，因此对应检查不适用。
- **用户确认的恢复修订：** Risk/BFF 移除创建/校验 Key Header、BFF Expected Key 比较、请求 Hash、Claim/Complete 和响应引用处理；创建每次有效调用生成新 Candidate。Validate 按准确发布身份、原 `expectedVersion=1` 和原操作者恢复，已完成时复用原八项结果；事务 B 再锁根，防止并发重复追加。新审计触发标识来自发布身份与业务阶段，旧开始审计触发值直接沿用。完整现行合同见[后端修订](../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md#release-recovery-20260921)。旧命令表、历史记录及其他命令入口没有迁移，本轮没有改动或验证 Gateway 请求重放实现/启用状态。
- **实际验证：** JDK 25 执行 Risk 全 reactor `./mvnw -DskipTests clean compile` 成功；临时消费者 reactor 使用当前 Risk API 执行 `-pl :paytech-operations-console-bff-web -am -DskipTests compile` 成功。静态检查确认 11 个 DTO 的字段/访问器与显式 Null 输出、四个路由、16 项 Mapper 定义在根/审计拆分前后的规范化 XML/SQL 一致、14/22 个 DataObject 构造字段与 XML 对齐、六个原 Hash 投影与核验方法实现保持、事务外检查位置、CAS 后对象回写和 BFF Key 移除。日志 `/tmp/risk-release-final-build.log`、`/tmp/risk-release-bff-build.log`；静态脚本 `/tmp/check-release-refactor.py`，增量空白检查通过。
- **限制与交付：** 按用户要求没有新增、修改或运行单元测试，也未编译测试源码；既有测试中的旧 Service/DTO/Key 合同需在恢复测试时适配。未验证 Spring 启动、HTTP/JSON 实际序列化、真实 MySQL 事务回滚或 Redis 并发恢复；编译和源码核对不替代这些运行证据。未执行 SQL、修改数据库、提交、推送或部署，未声明环境已切换。


<a id="risk-rule-set-management-refactor-20260921"></a>

## Risk DecisionRuleSetManagementApi 规范重构（2026-09-21）

- **范围与入口：** 六个接口（配置能力、列表、详情、保存新版本、冻结、整体删除）分别迁入 `risk-decision` 的六个 Owner Processor，查询继承 `AbstractProcessor`，写入继承 `AbstractLockProcessor`。Controller 只解析可信身份/标量、转换 DTO 和投影既有错误；移除旧 Web `DecisionRuleSetManagementProcessor`。六个 API 方法完整补充中文 JavaDoc、入参、返回值及重要业务边界。九个根 Request/Response 独立到对应包，十二个共享 HTTP 结构改普通 class，JSON 字段保持。
- **Context 与本地写入：** `CreateRuleSetVersionContext`、`FreezeRuleSetVersionContext`、`DeleteRuleSetContext` 持有真实管理根、准确版本及所需各表 DataObject，无重复身份或执行标志。Processor 在规则集资源锁内开启 5 秒本地事务，完成点→根→版本及可选旧编排锁定、当前变量/字典核验、引用和业务基准核验后一次准备 Context。`RuleSetManagementService/Impl` 只组合单表 Repository：新版本六表、管理根指针和原草稿退役原子保存；冻结准确规则版本、可选旧协议及管理根；删除只更新整体根。Service 不重新查询关系、不调用其他 Service/Processor/外部服务，提交后从原 Context 组装结果。无下游业务步骤或业务响应回放分支，因此示例中这两项不适用。
- **对象及读取：** 七个参与规则写入的 DataObject 改普通 class，保留原构造字段顺序、非空边界和值相等语义；Repository 在 SQL/CAS 受影响行数确认后修改传入原对象。管理版本比较归 `RuleSet`，当前业务头及冻结证据归 `RuleSetVersion`。纯 `DecisionRuleSetAssembler` 负责投影、物化和历史 Hash 核验，`RuleSetConfigurationReader` 负责共用目录/旧编排/活动引用读取。真实 Flow/Release 消费的 `DecisionRuleSetService` 缩为读取能力，保留旧聚合读取合同。三个查询使用只读 REPEATABLE_READ；SQL、排序、200 条分页上限、历史内容格式、旧字段 ID 与内容 Hash 投影不变。
- **幂等与消费者：** 沿用已确认迁移方向，Risk/BFF 三个写入口移除 Key Header、请求摘要、Claim/Complete 及本地响应重放；保留永久编码、准确基准版本/Hash、管理 CAS、唯一索引和删除终态。删除事件摘要改由永久根身份派生，既有记录不重写；没有清理命令表、执行 SQL 或声称 Gateway 已启用。具体合同由[Rule Set 窄范围修订](../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md#rule-set-idempotency-20260921)统一维护。BFF 同步普通 class 访问器、精确响应形状注册和有界 Reader；Risk 严格请求 Reader 继续区分必填键缺失与显式 null，保留重复键、字面量类型和容量限制。
- **实际验证：** JDK 25 下 Risk 全 reactor 执行 `./mvnw -DskipTests clean compile` 成功；临时消费者 reactor 使用当前 Risk API 执行 BFF Web 及上游 `-DskipTests compile` 成功。静态对照通过：六个路由及方法注释、21 个 HTTP class 字段/访问器、七个 DataObject 构造字段和相等语义、Mapper XML/Java 绑定未变、原 Hash/物化函数体、锁命名空间及参数、同对象回写、BFF JSON 形状和增量空白检查。日志为 `/tmp/risk-ruleset-final-build.log`、`/tmp/risk-ruleset-bff-final-build.log`；静态对照脚本为 `/tmp/check-ruleset-refactor.py`。
- **限制：** 按用户“单元测试先不做”，本轮没有新增、修改、编译或运行测试源码；旧测试中的 DTO、构造器、Web Processor、幂等回放断言仍待恢复测试时适配。没有执行 Spring/HTTP/JSON、MySQL 事务回滚、Redis 并发运行验证，编译和静态源码对照不代替这些证据。没有数据库迁移、提交、推送或部署。
