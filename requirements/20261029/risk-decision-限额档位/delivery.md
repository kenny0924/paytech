# risk-decision：限额档位 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-28 | 来源 | design-discuss/client-activation 讨论：限额档位作为放量开关（规划 Q-2 已定）、档位名单 + 分支方案、上线复核四眼、观察期；登记为 [2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) REQ-04 | 已确认（讨论） |
| 2026-10-29 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R10、规则 B1—B10、逐系统变动点、验收 AC-01—AC-10、待确认 Q-1—Q-14 | 已交付，待确认 |
| 2026-10-29 | 1 确认 | 用户"按建议"：Q-1—Q-14 按建议（档位表为权威 + 四张 TIER 名单投影；承保首次 APPROVED 同事务初始化 VERIFICATION，不消费开通项事件；规则 + 实时累计 Feature 实现各档额度；结账点无档位 / RESTRICTED 拒绝 + 单笔、支付点单笔 / 日 / 月；四权限分级；观察期笔数用 Risk 评估事件近似；不发 ClientLimitTierChanged；存量有 ENABLED 开通项的 Client 迁 STANDARD，须在激活档位分支发布前完成；激活发布即开关；USD）。默认八阶段，进入阶段 2 | 已确认 |
| 2026-10-29 | 2 差异与影响 | [technique.md](./technique.md) 评审稿（只读核对代码与本机 / mor-test SELECT）：F-01—F-14、需求项差异、逐系统影响、待决 S-01—S-13、规模 M（偏上）。关键：决策流按名单命中条件边分支 + ELSE→DENY 无需改引擎；已作废发布不能重新激活（回滚=按旧版本新建发布）；累计 Feature 为滑动窗口、按同币种、且 ASSESSED 含被拒事件→需新增 ALLOWED 事件码；名单命中无缓存；REQ-03 挂点 decide / autoApprove；mor-test 4 个 ENABLED 开通项 Client 需迁移；下一注册块 144 | 已交付 |
| 2026-10-29 | 2 确认 | 用户"可以"：S-01—S-13 按建议，**S-05 改为方案 A**：每个档位按币种分别配置单笔 / 日 / 月额度（不换汇，累计本就按同币种）；某币种未配置时 VERIFICATION / OBSERVATION 拒绝、STANDARD 放行、RESTRICTED 拒绝；各币种分别累计不合并写入配置说明；有汇率服务后再考虑统一 USD 折算（另立项）；requirement Q-13 同步修改。进入阶段 3 | 已确认 |
| 2026-10-29 | 3 方案 | [technique.md](./technique.md) 第 7—13 节评审稿：接口 13 个新增（risk-decision L1—L12，L12 迁移为内部接口 + 白名单；ops-bff 11 路由含 1 个工作台聚合）、2 张新表（`t_client_limit_tier`、`t_client_limit_tier_change`）+ 3 处注释、4 张 TIER 名单 DML、新事件码 `PAYMENT_ATTEMPT_ALLOWED` / `CHECKOUT_ALLOWED`、注册块 144（141 行）、`GOLIVE_LIMIT_TIER` 模板 + 表级授权、各档按币种额度为风控常量 `TIER_{TIER}_{CCY}_{SINGLE\|DAY\|MONTH}`（O-1）、决策流配置说明、测试与验证、发布顺序（代码 → 迁移 → 风控配置 → 迁移核对 0 缺失 → 激活）；O-1—O-6。Pencil 原型 `operations-console.pen` 风控 · 限额档位 01—05（`oAoTu`、`WDX9M`、`NaNPa`、`uT41G`、`P3zYbn`），导出见 [prototypes/](./prototypes/) | 已交付 |
| 2026-10-29 | 3 确认 | 用户"继续"：方案第 7—13 节、O-1—O-6（含 O-1 额度为风控常量、改额度即时生效无四眼）与原型 01—05 确认；阶段 4 含本机 auto 库 `mor_risk_decision` 执行前滚 DDL 与 TIER 名单 DML。实施工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq04`（基于 30361be7 + 本需求文档） | 已确认 |
| 2026-10-30 | 4 裁决 | 阶段 4 实施发现：现有引擎 CLIENT 维度 SUM_AMOUNT 最长 3600 秒窗口、无 30 天窗口，且每次评估计算发布引用的全部累计 Feature（超时按 FAIL_CLOSED 拒绝），方案 §8.3 的近 24 小时 / 近 30 天额度无法配置。用户选 **B**：本期新增"Client × 币种 × UTC 日"已放行金额汇总表（与 ALLOWED 事件同事务累加）+ 新 Feature 来源（读 ≤ 31 行），日 / 月额度按档位真正生效；需补阶段 2 / 3 修订稿（technique 修订节）经用户确认后实施。其间后端继续不受影响部分。另：块 144 已被联合查询占用，本需求改用 145（三会话约定 144 联合查询 / 145 REQ-04 / 146 tax）；额度常量改为随支付币种的 MONEY 常量 + 币种条件，结账点只做档位分支 / 受限 / 币种集合，单笔与日 / 月均在支付点（修改 S-11 / O-5） | 已确认 |
| 2026-10-30 | 4 修订确认 | 用户"继续"：方案 B 修订（technique 末节 R-1—R-9）按建议确认：R-Q1 日 = 当前 UTC 自然日（迪拜 04:00 清零）；R-Q2 月 = 含当天的最近 30 个 UTC 自然日；R-Q3 L2 / 工作台字段改名为当日 / 30 日口径（ops-bff 与 web 同步）；R-Q4 CHECKOUT_ALLOWED 也维护汇总行（只记不拦）。需求 B11 / AC-06 / AC-11 措辞随之修改。实施 B 后 risk-decision 全模块完整重跑 | 已确认 |
| 2026-10-30 | 4 确认 / 5 授权 | 用户"继续"：接受阶段 4（含方案 B）；授权本机联调：基于最新 main（含 tax 6eaf1bd3）与联合查询改动的合并源码构建本机 risk-decision / operations-console-bff / platform-support（备份 *.pre-rq04）；`mor_risk_decision_v2e2e` 前滚、`mor_platform_v2e2e` 块 145 + 激活与网关刷新；本机风控页面按配置说明建档位规则与决策流；迁移存量 ENABLED 客户至 STANDARD、核对缺失 0 后激活档位分支发布；接口联调与浏览器走查 zh-CN / en × 1440 / 390；不含 mor-test、提交、推送 | 已确认 |
| 2026-10-30 | 5 浏览器走查 | rq04-web：rq04combo 运营 Web（Vite 18315）+ 本机语言包 #20，zh-CN / en × 1440 / 390，req / appr / elev / ro / 管理员，原型 01—05 与入口、变量编辑器 DAILY_SUM_AMOUNT 全部通过；修 4 处页面问题（验证档可申请受限、Client 详情页头 390 溢出、清单断词与标签色、审批级别短文案），两棵树同步，测试 1629 / 1666 全过。写入：AUTO 申请后撤回；E 往返受限 → 验证 → 观察 → 标准。见 [stage5-report.md 第 8 节](./verification/stage5-report.md) | 已交付 |
| 2026-10-30 | 5 裁决 / 6 启动 | 用户"按建议"：F-1 选 (a)——任意承保批准（首次或修订）时若 Client 无档位则初始化 VERIFICATION，已有档位的修订批准仍不改档位（B2 措辞随之修改）；F-4 在阶段 6 于 mor-test 只读 / 离线测量档位流程评估耗时与超时率，明显时先做配置缓存（另立项）再激活档位门禁。阶段 5 接受；进入阶段 6（mor-test 发布准备，只读 + 本机构建；基线为最新 main + 联合查询提交后的版本） | 已确认 |
| 2026-10-30 | 6 确认 / 7 授权 | 用户"继续"（针对阶段 6 Q-1—Q-5 与阶段 7 授权的提问）：Q-1 额度按 stage6-report 第 5 节建议值（待风控确认，激活后改常量即时生效）；Q-2 接受 F-4 有条件上线（mor-test 激活档位门禁 + 逐迁移客户实时预热 + 验收统计 TIMED_OUT），登记"风控配置缓存"为 REQ-04c，作为档位门禁上生产的前提；Q-3 Platform 随本次发布（含 69f87bbf，回滚 a3003ef6）；Q-4 验收对 REQ-03 L / H 做修订批准以进入 VERIFICATION；Q-5 迁移临时白名单致 risk-decision 重启两次；授权阶段 7（mor-test 停 / 起 risk-decision、operations-console-bff、platform-support-service、operations-console-web，mor_risk_decision DDL，块 145，表级 GRANT 与 GOLIVE_LIMIT_TIER，语言包，风控配置、迁移、激活档位发布与验收）。执行前须等联合查询提交并按其重建候选（ops-web、Platform）、重做块 145 编译 | 已确认 |
| 2026-10-30 | 7 中断与裁决 | mor-test 执行至 ⑦（09:38—09:43Z）：④ DDL、⑤ risk-decision `1414384e`、⑥ ops-bff `4bd4c3df`、⑥b Platform `af13e77a` 均 healthy；块 145 休眠写入后刷新 641 正常，09:42:20Z 激活后 Gateway 每次刷新 CONTROL_PLANE_UNAVAILABLE，09:43:00Z 按"失败即停"停用块 145，刷新恢复 641。根因（只读排查）：Gateway 拉取公共快照的 WebClient 应答上限硬编码 1 MiB（`PlatformSnapshotProperties.MAX_RESPONSE_BYTES`），现 641 路由快照 1,032,106 字节，块 145 的 11 路由约需 17.8 KB，超出约 1.4 KB；阶段 6 离线演练只编译未序列化，未覆盖传输上限。用户选 **A**：Gateway 上限提至 4 MiB（代码 + 配置 + 3 个测试），构建并发布 api-gateway 镜像（超出 REQ-04 原范围，用户授权），之后重新激活块 145（期望 652）。⑧—⑫ 可先行，⑬ 激活与 ⑮ 验收待路由上线 | 已确认 |
| 2026-10-30 | 7 完成 / 8 授权 | 阶段 7 在 mor-test 执行完毕（含 ⑦a Gateway `d51daee3`、⑪ 续跑修订 D-10），验收全部通过（自动 16 / 19 / 39 / 12+1 已解释，浏览器 10 / 10）；用户"提交吧"：授权本地提交（私有索引，基于当时 main），不含推送 | 已确认 |

## 适用阶段

默认八阶段（2026-10-29 用户确认）。SQL、mor-test 部署、提交、推送、决策流发布激活分别授权。

## 当前阶段与下一确认点

阶段 1—7 已完成：2026-10-30 已部署 mor-test（`rq04-20261030b`，含 Gateway Snapshot 上限修订 D-9），验收通过（见文末"阶段 7 执行记录"）。阶段 8：用户"提交吧"（2026-10-30）授权本地提交，推送由用户自行完成。

## 阶段 4 实施记录（前端）

2026-10-30，执行代理 rq04-web，工作树 `Code-MoR-rq04`（未提交、未部署、未访问 mor-test）。

**文件**（`operations-console-web/`）

| 文件 | 内容 |
|---|---|
| `src/limitTier/limitTier.ts` | ops-bff 路径（L1—L11 + 工作台）、严格解码（列表 / 统计 / 详情 / 历史 / 工作台）、页面模型：B3 可申请目标与恢复上限、审批可用性（四眼、ELEVATED 只能高级接口、STANDARD 普通审批人走普通接口）、复核清单提交条件、申请请求体、列表查询与地址栏、额度表行与进度百分比 |
| `src/limitTier/LimitTierParts.tsx` | 档位徽标、进度条、列表观察期单元格、观察期卡、按币种额度卡、当前档位卡、变更历史卡、命令错误提示 |
| `src/limitTier/LimitTierDetail.tsx` | Client 档位页：无档位、验证档（上线复核清单）、观察档（进度 + 升标准 / 延长 / 降受限）、标准档（降受限）、受限档（受限效果 + 申请恢复）、待审批（冻结申请 + 审批区 / 撤回） |
| `src/limitTier/useLimitTierCommand.ts` | 写命令钩子（同承保：结果未知沿用 Idempotency-Key，409 / 422 留在页面） |
| `src/limitTier/limitTier.css` | 档位紫色、进度条、额度表、清单、历史；1280 / 640 断点 |
| `src/pages/RiskLimitTiers.tsx` | `/risk/limit-tiers` 列表（6 张统计卡可点筛选、档位页签、搜索、只看到期待确认、待审批筛选、分页写入 URL）与 `/:clientId` 路由 |
| `src/limitTier/limitTier.test.ts` | 11 个单测 |
| `navigation/pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts` | 菜单 `OPERATIONS_RISK_LIMIT_TIERS` + 三个按钮（REQUEST / APPROVE / APPROVE_ELEVATED）、懒加载与路由 |
| `masterData/ClientDetail.tsx`、`pages/Clients.tsx` | Client 详情"限额档位"入口（可见菜单时） |
| `underwriting/UnderwritingDetail.tsx` | 已批准承保显示进入档位页的链接（可见菜单时） |
| `src/i18n/en/risk.json` + `i18n/zh-CN/risk.json`（+200 键 `risk.limitTier.*`）、`nav.json`（+4）、`master-data.json`（+1） | 文案；页面无硬编码中文 |
| `configuration/configurationModel.test.ts` | 页面数 79 → 80 与新路由断言 |

**验证**

- `npm test`：1628 通过 / 0 失败（含新增 11 个、i18n 目录一致性、禁中文扫描、`nav.resource` 覆盖）；`tsc --noEmit` 通过；`vite build` 通过（输出在临时目录，已删）；`npm run i18n:export-source` 通过（25 命名空间、10464 键）。
- 本机浏览器（Vite dev + 临时 mock gateway，fixture 数据，非真实后端）：en / zh-CN，1440 与 390（iframe）走列表、验证档提交上线复核（请求体核对：上线说明作理由、四个人工项）、观察档延长（请求体含目标）、待审批（高级审批人可批准 / 驳回意见必填提示；普通审批人按钮禁用并说明）、受限档；390 下三个页面 `scrollWidth = 390`，无横向滚动（列表表格在面板内横向滚动，同承保页）。
- 已对照后端 `rd-api` 新增响应体（Summary / Detail / Quota / Observation / PendingChange / Change / Checklist / Statistics）字段名一致；清单支付方式为逗号串，解码兼容数组与逗号串。工作台响应体 technique 8.4 未定义，已把页面使用的形状发给 rq04-backend 与主会话（`client`、`tier`=L2、`history.items`=L3 + 显示名、`underwriting`、`checklist.items`（4 项 × OK / NOT_OK / UNAVAILABLE）+ `checklist.facts`）；ops-bff 实现后需联调确认。

**与原型的差异**

| 位置 | 差异 | 原因 |
|---|---|---|
| 02 上线复核 | 只有一个"上线说明"框，提交时同时作为 L4 `reason` 与 `goLiveNote`，上限按 `reason` 的 1000 字 | 原型无单独理由框，L4 `reason` 必填 |
| 02 清单系统项 | 用 technique 11.2 的四项（资格、承保、合同、支付方式），状态与细节（资格状态 / 有效期、承保版本 + CRR、合同状态、已开通代码 n / m）附在后面；原型把"CRR 已评"单列一行 | 按文案键与工作台四项契约，CRR 并入承保项 |
| 03 / 05 操作 | 升标准 / 延长 / 降受限 / 恢复都打开申请弹窗（理由必填；延长可改目标天数 / 笔数，默认填当前目标；恢复可选不高于受限前档位的目标） | 原型只画按钮，L4 需要理由 |
| 03 | 标准档也显示"降为受限档"操作卡 | B3 任意档位可切受限；原型无标准档详情图 |
| 04 | 原型右下"对照框"不实现；按当前账号直接显示禁用状态与说明，申请人本人额外显示"撤回" | 对照框是原型说明用 |
| 列表 | "待审批"筛选为下拉（全部 / 只看待审批）；统计卡可点击切换筛选 | 原型为下拉占位 |
| 无档位 Client | 页面提示"承保批准前额度为零"并显示承保与历史 | 原型未画 |

## 阶段 4 实施记录（后端）

2026-10-30，执行代理 rq04-backend，工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq04`（基于 30361be7）。未提交、未推送、未部署。

### 已读规范与调用链

开发规范、Java 工程规范、公共写接口示例、单元测试规范与示例、数据库设计指南、2026-09-16 决定，以及 REQ-03 需求 / 技术方案与代码。Processor 与 Context 所属模块：risk-decision core 的 `control` 包，Processor 位于 `service/processor`，Context 位于 `domain/limittier`。本地原子写入：
- 承保首次批准：承保 Service 与档位 Service 由 Processor 在同一个 TransactionTemplate 中组合（D-1）。
- 批准变更：申请、档位行、旧名单成员、新名单成员在 `ClientLimitTierService.approve` 的同一事务中写入。
- 迁移：每个 Client 一个短事务。
事务外步骤：无。ops-bff 在 L4 之前重新读取清单系统项。

### 文件

- **risk-decision api**：
  - `ClientLimitTierApi`（L1—L12）、`RiskLimitTierErrorCode`、11 个请求 / 响应体（`api/control/request|response`）；
  - `VelocityFeatureParameters` 允许 `*_ALLOWED`。
- **risk-decision core / control**：
  - 枚举 `LimitTier`、`LimitTierSource`、`LimitTierChangeStatus`、`LimitTierApprovalLevel`；`RiskListPurpose.TIER`、`RiskListEntrySource.TIER`；
  - DataObject、Mapper、XML、Repository 与 Impl：`ClientLimitTier`、`ClientLimitTierChange`；`ClientUnderwritingRepository.findEffectiveByClientIds`；
  - `domain/limittier`：异常、Context、视图、`TierQuotaCatalog`、`TierObservation`、`TierChecklist`；
  - 端口 `LimitTierUsageReader`；`ClientLimitTierService` 与 Impl；`LimitTierProperties`（`risk.limit-tier.migration-callers`）；
  - Processor：`RequestClientTierChangeProcessor`、`WithdrawClientTierChangeProcessor`、`ApproveClientTierChangeProcessor`、`RejectClientTierChangeProcessor`、`MigrateClientTiersProcessor`，共用 `LimitTierCommands`；
  - REQ-03 的 `SubmitUnderwritingProcessor`、`ReviewUnderwritingProcessor`、`AutoUnderwritingProcessor` 接入建档；`AddRiskListEntryProcessor` 与 `DeactivateRiskListEntryProcessor` 拒绝 TIER 名单。
- **risk-decision core / decision**：
  - `VelocityEventCode` 新增 `PAYMENT_ATTEMPT_ALLOWED` 与 `CHECKOUT_ALLOWED`；`RuntimeEvaluationFacts.velocityFacts` 在放行时写放行事件，否则写拦截事件；
  - 端口实现 `LimitTierUsageReaderImpl`；`RiskVariableMapper.activeConstantsByCodePrefix`；
  - 方案 B：`ClientAllowedDailyTotal` / `ClientAllowedCurrencyTotal` DataObject、`ClientAllowedDailyTotalMapper` 与 XML、`ClientAllowedDailyTotalRepository` 与 Impl；`VelocityMetric.DAILY_SUM_AMOUNT`；`FeatureHandlers` 守卫与窗口；`FeatureValueReader.dailySum`；`RuntimeEvaluationFacts` 产生日汇总增量，支付 / 结账评估保存事务内累加；端口"已用"改读汇总表。
- **risk-decision app**：
  - `ClientLimitTierController`、`LimitTierWireConverter`、`LimitTierExceptionConverter`；
  - `RiskApiErrorConfiguration` 映射 404 / 403 / 409；`PersistenceConfiguration` 注册 4 个枚举；
  - `application.yml`：`/risk-limit-tiers` 只记元数据，`risk.limit-tier.migration-callers` 默认为空。
- **SQL / 模型 / 文档**：
  - `docs/database/sql/changes/2026-10-30-client-limit-tier.sql` 与 `-rollback.sql`；`risk-decision-schema.sql`、`risk-decision-initial-data.sql`、测试 `init/ddl.sql`；
  - `risk-decision.pdma.json`：2 个实体 + "限额档位"图 + 3 处注释，直接编辑；
  - `RISK_DECISION_PHYSICAL_DECISIONS.md` 第 10.5 节、`agent-docs/database/RISK_COMPLIANCE_DATABASE.md` 第 4.10 节、risk-decision README；
  - `docs/limit-tier-configuration.md`：配置说明，含 TEST 示例与迁移步骤；
  - 决定 3.3 的 `GOLIVE_LIMIT_TIER` 输出；arch-rules 表归属（+2）；需求目录 `db/ddl.sql`、`db/dml.sql` 索引。
- **ops-bff**（由派生代理完成，主代理复核）：
  - `RiskLimitTierClient` 与 Impl、`RiskLimitTierController`、`LimitTierWorkspaceProcessor` 等 8 个 Processor 与辅助类、11 个页面模型、错误映射、响应形状登记、`/risk-limit-tiers` 只记元数据；
  - 测试：`RiskLimitTierBrowserAutoTest`、`RiskLimitTierScenario`，并改动路由清单与 Risk 客户端配置测试；
  - 工作台响应体已与 rq04-web 约定，写入技术方案 8.4。
- **Platform**：
  - 注册块 **145**（原 144，见 D-6），追加在 `platform-support-initial-data.sql` 末尾，共 141 行；需求目录 `db/platform/registration-145.sql`、`-activate.sql`、`-rollback.sql`；
  - `console-resource-capabilities.json` 新增菜单与 3 个按钮。
- **data-query**：`db/data-query-templates.sql`（`GOLIVE_LIMIT_TIER`）、`db/data-query-grant.sql`（表级 GRANT 与撤销）。两者均未在任何库执行。

### 本机数据库变更

本机 auto 库 `mor_risk_decision`：
- 执行前用 mysqldump 备份结构，存放在会话 scratchpad，未入库。
- 执行前滚 `changes/2026-10-30-client-limit-tier.sql`，执行两次以验证可重跑。结果：60 张表；两张新表分别 19 列与 35 列；300001—300004 四张 TIER 名单；3 处注释已更新。
- 测试中途发现 `PaymentRiskTestMapper.clear()` 会 `DELETE FROM t_risk_list` 全表，删掉了这 4 张名单。已改为保留 300001—300004，并用前滚文件重新写回。

方案 B：再次备份结构后执行更新后的前滚文件，新增 `t_client_allowed_daily_total` 与两处列注释（共 61 张表）；首次建表用了 `utc_date` 列名，因与 `UTC_DATE` 冲突改为 `event_date`，空表删除后用前滚文件重建。

另建过一个 scratch 库 `rq04_tpl_scratch` 用于模板验证，已删除。

未触碰：`*_v2e2e`（只读核对块 145）、`mor_platform`（只读）、mor-test。

### 验证

命令在 `paytech/` 下执行，均带 `JAVA_HOME=jdk-25` 与 `./mvnw -o -Dmaven.repo.local=/Users/kenny/.m3-rq04/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`。

- risk-decision 全模块：`-pl risk-decision/paytech-risk-decision-test test`，1831 个用例，首轮 2 个失败：`ArchitectureAutoTest` R4 读到本地仓库中未更新的 arch-rules JAR，重新 install 后通过；`CheckoutExecutionReleaseApiAutoTest.test_normalFlow_velocityShadow` 的期望从 3 条累计事件改为 6 条，因为每次放行写评估与放行两条事件。两类修复后复跑这 3 个类，18 / 18 通过；其余 1829 个首轮已通过。
- 新增用例：
  - `ClientLimitTierChangeApiAutoTest` 11；
  - `ClientLimitTierEstablishApiAutoTest` 8；
  - `ClientLimitTierQueryApiAutoTest` 3；
  - `PaymentRiskLimitTierApiAutoTest` 8（含当日额度、30 日窗口按 UTC 日、被拒与重放不进汇总、8 路并发累加不丢失）；
  - `FeatureVariableCreateApiAutoTest` 新增 5（`DAILY_SUM_AMOUNT` 正例与四个负例）；
  - `RiskListManagementApiAutoTest` 新增 1，共 14。
- 已更新的既有用例：支付 / 结账评估的累计事件断言（放行写评估与放行两条事件；结账放行另记日汇总）；变量编辑上下文的事件目录与 CLIENT 的 `DAILY_SUM_AMOUNT` 统计方式；名单分页总数（含 4 张 TIER 名单）；影子运行的累计事件数。
- arch-rules：`-pl tools/arch-rules test`，70 / 70。
- **方案 B 实施后的最终复验（2026-10-30 05:57—06:06Z，Docker 重启之后）**：arch-rules 70 / 70；risk-decision 全模块 1838 / 1838（0 失败）；ops-bff 全模块 2469 / 2469。三者均 BUILD SUCCESS。
- ops-bff：`-pl operations-console-bff/paytech-operations-console-bff-app,operations-console-bff/paytech-operations-console-bff-test install`，2469 / 2469 通过。该轮在工作台补充 `facts.underwritingId`、`underwriting.decisionSource` 并把块号改为 145 之后运行，结束于 03:39Z，早于 Docker 重启窗口。
- `GOLIVE_LIMIT_TIER` 模板（AC-09）：在 scratch 库对 6 个 Client × 2 个目标档位执行模板正文，12 组结果全部符合 B10。覆盖无档位、VERIFICATION、OBSERVATION（到期 / 未到期）、STANDARD、RESTRICTED。

### 偏差

| 编号 | 偏差 | 原因与处理 |
|---|---|---|
| D-1 | 承保批准建档：不是在 `UnderwritingServiceImpl.decide` 中调用档位 Service（S-03 原文），而是由三个承保 Processor 开一个 TransactionTemplate，组合 `UnderwritingService.decide / autoApprove` 与 `ClientLimitTierService.establish`。两个 Service 内部的 TransactionTemplate 加入外层事务 | 规范要求 Service 不调用 Service，多个 Service 由 Processor 组合 |
| D-2 | 额度常量改为支付点 MONEY 常量，`currencyVariableCode=PAY_INPUT_CURRENCY`，规则用 `PAY_INPUT_CURRENCY EQ '{CCY}'` 限定币种；结账点只做档位、受限与币种集合检查 | 引擎保存规则时拒绝"固定币种常量 vs 动态币种金额"的比较。主会话 2026-10-30 已批准，修改 S-11 / O-5；技术方案 8.3 与配置说明已同步 |
| D-3 | 日 / 月额度改为方案 B：新表 `t_client_allowed_daily_total`（放行时同事务累加），新统计方式 `DAILY_SUM_AMOUNT`（只允许 CLIENT + `*_ALLOWED`，窗口 86400 = UTC 当日、2592000 = 近 30 个 UTC 自然日）；口径由"滚动 24 小时 / 30 天"改为 UTC 自然日；L2 / L10 / 工作台额度字段改名为 `day / last30Days / usedToday / usedLast30Days`（与 rq04-web 约定）；结账放行也记汇总（只记录）；需求 B11 / AC-06 / AC-11 与技术方案同步修订 | CLIENT 按明细的金额合计最长 3600 秒、无 30 天窗口（`FeatureHandlers`）。用户 2026-10-30 选 B，R-Q1—R-Q4 按建议；日期列命名 `event_date`（`utc_date` 与 MySQL `UTC_DATE` 冲突） |
| D-4 | START 只能有一条无条件出口，档位分支挂在 START 后的"档位分支"规则集节点上 | 引擎校验 `开始节点需要一条无条件出口`；配置说明与测试流程已采用 |
| D-5 | 迁移请求体增加必填 `executedBy`（执行人账号）；迁移入口拒绝时返回业务码 `RISK_LIMIT_TIER_MIGRATION_FORBIDDEN`（403） | L12 不经 Gateway，没有操作人头，表注释要求记录执行人；公共错误码中没有 FORBIDDEN |
| D-6 | Platform 注册块由 144 改为 145 | 块 144 已被并行的联合查询需求占用（主会话通知）。本机 `mor_platform` 与 `mor_platform_v2e2e` 七张注册表的 145 段为 0，主仓库初始化 DML 无 145 段 |
| D-7 | 创建 TIER 用途名单返回 422（Body 注解只允许 BLOCK / ALLOW），不是技术方案写的 400；增删 TIER 名单成员为 400 | 现有合同已拦截，不再加重复检查 |
| D-8 | ops-bff：页面体含系统项或未知字段返回 400（沿用 REQ-03 严格解析），不是 422；人工项嵌套在 `checklist` 下；"支付方式开通项"取所选 CLIENT 合同上成员 ACTIVE 的全部开通项；`canRequest` 每次实时鉴权 | 派生代理记录的 D-a—D-d，与前端一致 |
| D-9 | api-gateway 纳入本次发布：`PlatformSnapshotProperties.MAX_RESPONSE_BYTES` 与 `application.yml` `gateway.platform-snapshot.max-response-bytes` 由 1 MiB（1 048 576）改为 4 MiB（4 194 304），同步 3 处测试（`PlatformSnapshotDiscoveryPropertiesAutoTest` 两处、`application-auto.yml`）；发布演练增加 Snapshot 响应体字节数检查（`release/harness/BlockSnapshotCheck.java` 序列化计数、`GatewaySnapshotDecodeCheck.java` 以 Gateway 生产解析与编译复核） | 阶段 7 第 ⑦ 步激活块 145 后 Gateway 每 5 秒报 `CONTROL_PLANE_UNAVAILABLE`：Snapshot 由 1 032 106 字节（641 条路由）增至 1 049 930 字节（652 条），超过硬上限 1 048 576（超 1 354 字节），WebClient 拒收。上限写死在代码中，环境变量不能放宽；联合查询块 144 与块 146 已用掉大部分余量，属系统性容量问题。原演练只编译不序列化，未发现。用户 2026-10-30 选方案 A 并授权发布 Gateway |
| D-10 | 发布脚本 `release/scripts/rq04_risk_config.py` 改为可续跑：写请求遇 504（ops-bff 等待超时、Risk 可能已提交）不盲目重发，先按业务编码只读复核（每 3 秒、至多 60 秒），未生效才重发一次；规则集编码改为固定（`TIER_GATE_PAY/CO`、`TIER_LIMIT_<档位>`、`TIER_CO_CURRENCY_<档位>`），规则集、流程、冻结、发布、校验、集合成员都先查后建；激活与回滚同样处理。另修正支付流程沿用的现网拒绝节点缺文案（`deny` 节点补 `支付被风控拒绝`，校验器要求拒绝结束节点同时有原因码与文案） | 阶段 7 第 ⑪ 步：首次 `build` 在建拒绝原因时 504（Risk 2 秒后已提交，其他对象未建）；续跑时 `POST /decision-flows/v1/flows` 422 `graph.nodes[10].reasonCode`。阶段 5 本机流程没有拒绝节点、F-4 副本自建图，均未覆盖从现网复制的拒绝节点 |

### 开放项

1. ops-web 需按新字段名与 `DAILY_SUM_AMOUNT` 文案调整（已通知 rq04-web）。前滚 / 回滚已在 scratch 库 rq04_rb_scratch 验证：有汇总数据时回滚 SIGNAL 拒绝；清空后删除三张表与四张名单、恢复注释；库已删除。
2. `GOLIVE_LIMIT_TIER` 与表级授权在发布时登记、执行；Gateway 快照离线编译（块 145）留在阶段 6。
3. Platform access-control 模块测试未运行：它会写本机 `mor_platform`，不在本次授权内。`console-resource-capabilities.json` 只校验了 JSON 合法。
4. 承保并发两次首次批准的唯一冲突分支（`insertIfAbsent` 捕获重复键）没有自动测试；已有档位跳过的分支已覆盖。
5. 本机 e2e 库与 mor-test 未执行前滚；迁移名单由发布执行人导出。

### 阶段 4 前端补充：方案 B 字段改名与 DAILY_SUM_AMOUNT（2026-10-30）

用户确认方案 B：当日 = 当前 UTC 自然日（UTC 0 点、迪拜 4 点重置）；近 30 天 = 含今日的最近 30 个 UTC 自然日。字段名由 rq04-backend 给出。

| 改动 | 内容 |
|---|---|
| 额度字段改名 | `src/limitTier/limitTier.ts` 的 `Quota` 与解码：`rolling24h → day`、`rolling30d → last30Days`、`used24h → usedToday`、`used30d → usedLast30Days`；额度卡表头与进度条、单测 fixture 同步 |
| 文案 | 删 `risk.limitTier.quota.rolling24h/rolling30d`，新增 `quota.day`（Today, UTC (used / limit) / 当日（UTC）已用 / 上限）、`quota.last30Days`（Last 30 days, UTC incl. today / 近 30 天（UTC 自然日，含今日）已用 / 上限）、`quota.utcNote`（UTC 日界说明）；`quota.usedHint` 去掉"滑动窗口" |
| 变量编辑器 | `risk/riskVariable.ts`：统计方式加 `DAILY_SUM_AMOUNT`（金额量纲）、窗口登记加 2592000、目录解码上限随之放宽；`velocityWindowLabel(seconds, metric)` 对 `DAILY_SUM_AMOUNT` 显示"当日（UTC）/ 近 30 天（UTC 自然日，含今日）"；`FeatureVariableFields.tsx` 的"主体受限"提示改为按同统计方式的目录最长窗口比较（避免新增 30 天窗口后所有主体都被判为受限）。文案 `risk.enum.velocityMetric.DAILY_SUM_AMOUNT`、`risk.enum.velocityWindowDaily.86400/2592000`、`risk.enum.velocityWindow.2592000`，`risk.feature.metricHint` 加入按日金额合计 |
| 单测 | `velocityVariable.test.ts` 新增 1 个（目录解码、金额量纲、UTC 窗口文案、未登记窗口拒绝） |

验证：`npm test` 1629 通过 / 0 失败（限额档位 11 个、变量 1 个新增）；`tsc --noEmit` 通过；`vite build` 通过（临时目录，已删）；`npm run i18n:export-source` 通过（25 命名空间、10469 键）。本次改动未重做浏览器走查。

## 阶段 7 执行记录（mor-test，2026-10-30，主会话执行；用户授权）——已完成

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 09:3x | ④ mor_risk_decision 前滚 | 成功（3 张表、TIER 名单 300001—300004） |
| 09:3x—09:40 | ⑤ risk-decision `1414384e` / ⑥ ops-bff `4bd4c3df` / ⑥b Platform `af13e77a` | 均 healthy、无 ERROR；Gateway 刷新正常（641） |
| 09:4x | ⑦ 块 145 插入（DISABLED） | 刷新正常（641） |
| 09:42:20 | ⑦ 22-activate（第一次） | Gateway 每 5 秒 `CONTROL_PLANE_UNAVAILABLE`；09:43:00 执行 23-deactivate 恢复（641）。根因 Snapshot 1 049 930 字节 > 1 MiB 上限（D-9） |
| 09:48—09:49 | ⑧ ops-web `2f7f8780`（277 个文件）；⑨ 语言包 #28 READY 并为当前（发布请求 504，Platform 已完成，change_count 422）；⑩ GRANT + `GOLIVE_LIMIT_TIER` ACTIVE | 通过 |
| 09:49 | ⑪ build 第一次 | 建拒绝原因时 504（原因 09:49:42 已提交），见 D-10 |
| 10:00:19 | ⑦a api-gateway `d51daee3`（Snapshot 上限 4 MiB） | healthy、无 ERROR，刷新正常（641） |
| 10:01:17 | ⑦ 22-activate（第二次） | 652 ACTIVE / 6 DISABLED；七表 4 / 4 / 4 / 11 / 46 / 6 / 66；Gateway 持续刷新成功 routeCount 652 |
| 10:0x | ⑪ build 续跑 | 第一次续跑 422（拒绝节点缺文案，D-10）；修正后成功：支付流程 `241866465639239681` / 发布 `241866473545502720`，结账流程 `241866469086957569` / 发布 `241866478381535232`，SHADOWING |
| 10:11:32 / 10:12:35 | ⑫ 迁移（迁移覆盖重启 → L12 → 清白名单重启） | 4 个 CREATED，缺口 0，清白名单后 403 `RISK_LIMIT_TIER_MIGRATION_FORBIDDEN` |
| 10:13:33 | ⑬ 激活档位发布 | 两个风控点 ACTIVE；影子 PASSED（p95 21 ms / 6 ms） |
| 10:1x | ⑭ 激活后实时冒烟（兼作预热） | 5 / 5 PASS（4 个迁移 Client 经 `tier_standard` 放行，Client 1 `deny_no_tier`） |
| 10:1x | ⑮ 验收 | accounts 16 / 16、prepare 19 / 19（L、H 修订批准 → VERIFICATION，F-1）、api 39 / 39、check 12 / 13 |

- check 唯一 FAIL："ops-bff 近 60 分钟无 ERROR"——ERROR 全部是 09:41 前后 ⑥b Platform 重启窗口内的 `DEPENDENCY_UNAVAILABLE`（11 条）与 09:48 / 09:49 两次已知 504（⑨ 语言包、⑪ 拒绝原因），之后没有新 ERROR；主会话判定为已解释，接受。
- F-4：激活后支付评估 10 次，`TIMED_OUT` 0，平均 233 ms（mor-test 有条件放行的两项条件均满足）。
- ⑮-5 浏览器走查（rq04-web）：**10 / 10 PASS**，无 P0 / P1；en / zh-CN × 1440 / 390，72 张截图，无横向滚动，无 HTTP ≥ 400（证据 `release/evidence/browser-20261030/`）。审批面板本身在 mor-test 上没有待审批申请可展示，只由阶段 5 本机走查覆盖。

**阶段 7 完成。**

### 演练改进（教训）

1. **Snapshot 字节数**：注册块演练只编译、不序列化，漏掉 Gateway 1 MiB 上限（D-9）。已在 `release/harness/BlockSnapshotCheck.java` 序列化并统计字节、`GatewaySnapshotDecodeCheck.java` 用 Gateway 生产解析与编译复核；今后每个注册块演练都要报激活后的 Snapshot 字节数与上限余量。
2. **风控流程按现网图演练**：本机流程与 F-4 副本都没有使用 mor-test 现网流程的原节点，校验器对"拒绝结束节点须有原因码与文案"的规则没有被执行（D-10）。今后风控发布演练应以现网（或其只读导出副本）的流程图为基础构造新流程，并在副本上调用同一保存 / 校验接口，确认所有沿用节点通过校验后再进入阶段 7。
3. **写请求 504**：高负载下 ops-bff 的 4 秒等待可能早于下游提交。发布脚本的写操作要能按业务编码复核并续跑，不能盲目重发（D-10）。
