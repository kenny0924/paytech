# risk-decision：承保记录 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-28 | 来源 | design-discuss/client-activation 讨论：N2 风控承保（自动 / 人工、四眼、承保记录不可变与修订）；登记为 [2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) REQ-03，Q-3 自动承保阈值配置化已定 | 已确认（讨论） |
| 2026-10-29 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R7、规则 B1—B9、逐系统变动点、验收 AC-01—AC-11、待确认 Q-1—Q-12 | 已交付 |
| 2026-10-29 | 1 确认 | 用户"可以，功能的设计参考一下行业内的做法……继续吧"：Q-1—Q-12 按建议（Q-3 本期页面一键自动承保、Q-4 不设逐 Client 额度、Q-9 无 CRR 存量按 MEDIUM 人工承保）；新增范围：承保页自动计算并展示"估算敞口"（月流水 ×（交付天数 + 结算天数）/ 30，参考值）；设计参考行业承保做法（敞口、限额、延迟结算、保证金、卡组织登记、分层与四眼、版本留痕、重新承保触发）；归属确认在 risk-decision（非 compliance）。默认八阶段，进入阶段 2 | 已确认 |
| 2026-10-29 | 2 差异与影响 | [technique.md](./technique.md) 评审稿（只读核对代码与本机 / mor-test SELECT）：F 项事实、行业做法对照（第 2a 节）、需求项差异、逐系统影响、待决 S-01—S-13、规模 M；requirement 同步 R8 估算敞口、R9 行业对照、B10 敞口公式、AC-12。关键：ops-bff 已能按案件取齐 CRR / 尽调档位 / 申请值（含 REQ-12 四项）/ MCC 与高风险标记，无需新 Owner 接口；risk-decision 信任 Gateway 鉴权，四眼 = 复核路由权限 + 复核人 ≠ 提交人；保证金本期只记要求，compliance `MIN_SECURITY_DEPOSIT` 下发归 REQ-18；下一注册块 143；data-query 只读账号尚未授权 `mor_risk_decision` | 已交付 |
| 2026-10-29 | 2 确认 | 用户"可以"：S-01—S-13 按建议（单表版本行 + 进行中 / 生效两槽、BFF 聚合输入 + risk-decision 冻结快照、复核独立路由 + 复核人 ≠ 提交人、规则两表初值 USD 50,000 / 排除清单空、敞口 risk-decision 计算、保证金只记要求归 REQ-18、REQ-04 进程内读取、块 143 三权限、不发事件）。进入阶段 3 | 已确认 |
| 2026-10-29 | 3 方案 | [technique.md](./technique.md) 第 7—13 节评审稿：接口 13 个新增（risk-decision U1—U12 + ops-bff 工作台聚合）、3 张新表（`t_client_underwriting` 58 列含冻结快照与来源坐标、规则版本、币种阈值）、初始 DML 2 行、注册块 143（153 行，DISABLED + 单独激活）、data-query 数据源 `MOR_RISK_DECISION_REPLICA` 与 `GOLIVE_UNDERWRITING_APPROVED` 模板、文案约 45 键、测试与验证、切分 T1—T9、发布顺序；O-1—O-9。Pencil 原型 `operations-console.pen` 风控 · 承保 01—05（`jJ8w3`、`h2Gxg3`、`OrXDB`、`LJnUv`、`LDrqc`），导出见 [prototypes/](./prototypes/) | 已交付 |
| 2026-10-29 | 3 确认 | 用户"可以，按建议"：方案第 7—13 节、O-1—O-9 与原型 01—05 确认；阶段 4 含本机 auto 库 `mor_risk_decision` 执行前滚 DDL 与初始 DML。实施工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq03`（基于 e3e0c12f + 本需求文档） | 已确认 |
| 2026-10-29 | 4 实施与自测 | 工作树 `Code-MoR-rq03`（基于 e3e0c12f）：后端 risk-decision + ops-bff + 注册块 143 + data-query 脚本（risk-decision 1798 / 0、ops-bff 2459 / 0、arch-rules 70），前端 ops-web（1617 / 0），见下文"阶段 4 实施记录"；本机 `mor_risk_decision` 前滚 DDL + 规则 v1；预览补 `thresholdAmount` / `thresholdCurrency`；偏差 D-1—D-10 | 已完成 |
| 2026-10-29 | 4 确认 / 5 授权 | 用户"继续"：接受阶段 4 与偏差 D-1—D-10；`/risk-underwriting` 接口日志改为只记元数据（阶段 5 前完成）；platform-support-initial-data.sql 全新库第 7987 行中断为存量问题，另行处理、不在本需求；授权本机联调：`mor_risk_decision_v2e2e` 前滚 DDL + 初始 DML、`mor_platform_v2e2e` 注册块 143 + 激活与网关刷新、替换本机 e2e risk-decision / ops-bff JAR（备份 *.pre-rq03）、本机演示数据、工作树运营前端起本机服务、接口联调与浏览器走查 zh-CN / en × 1440 / 390；不含 mor-test、提交、推送 | 已确认 |
| 2026-10-29 | 5 本机联调（接口） | [verification/stage5-report.md](./verification/stage5-report.md)：`mor_risk_decision_v2e2e` 前滚、`mor_platform_v2e2e` 块 143 激活（520 → 532）、本机 risk-decision / ops-bff 换工作树构建（`*.pre-rq03`）、演示数据与 3 个测试账号；接口联调 44 PASS（503 场景本机未执行，由 auto 覆盖）；承保接口日志只记元数据（risk-decision 1799、ops-bff 2459 全模块通过）；U1"全部"只列每 Client 最新版本（risk-decision 1801 全模块通过） | 已完成 |
| 2026-10-29 | 5 裁决 | 阶段 5 浏览器走查发现：运营菜单一级目录"风控管理"由 `RISK_DECISION_CONFIG_READ` 控制，只有承保权限的角色看不到"承保"。用户"按建议"选 A：承保相关角色另加 `RISK_DECISION_CONFIG_READ`（只读查看风控规则配置），S-11 角色组合修正为 风控承保员 = RISK_DECISION_CONFIG_READ + RISK_UNDERWRITING_READ + RISK_UNDERWRITING_MANAGE，风控负责人再加 RISK_UNDERWRITING_APPROVE；不改菜单层级与 Platform 导航；本机 3 个测试角色补加该权限（记录回滚） | 已确认 |
| 2026-10-29 | 5 浏览器走查 | 本机 e2e（Vite 18314 → Nginx 18444）zh-CN / en × 1440 / 390，管理员 + uw / lead / ro 每组合 24 步全部通过（原型 01—05、四眼禁用、只读隐藏按钮、入口链接），承保数据未写；本机语言包 operations-console #15（新增 247）、#16（合计文案）；测试角色加 `RISK_DECISION_CONFIG_READ`（用户选 A，角色组合写入 requirement / technique）；U1"全部"改为每 Client 最新版本后重拍列表。见 [stage5-report 第 5 节](./verification/stage5-report.md) | 已交付 |
| 2026-10-29 | 5 确认 / 6 启动 | 用户"继续"：接受阶段 5（含日志只记元数据、U1 列表口径、角色组合 A、503 场景本机未执行）；进入阶段 6（mor-test 发布准备：只读核对基线 + 本机构建候选镜像、risk-decision DDL / 初始 DML 离线演练、注册块 143 用 mor-test 只读导出做网关编译演练、语言包计划、data-query 授权 / 数据源 / 模板脚本、mor-test 承保角色补 `RISK_DECISION_CONFIG_READ` 的方法、runbook、验收脚本；不停 mor-test 容器、不执行 mor-test 写入） | 已确认 |
| 2026-10-29 | 6 mor-test 发布准备 | [release/stage6-report.md](./release/stage6-report.md)：只读基线（现网无基线外内容）；候选 rq03-20261029a：risk-decision `8934f65a`、ops-bff `4f08f1e6`、ops-web `2f7722da`、Platform `49f71011`（补 `console-resource-capabilities.json` 承保菜单）；mor_risk_decision 离线演练；块 143 离线编译 619 → 631 → 619；表级 GRANT；语言包 #23 计划；runbook 与验收脚本 | 已完成 |
| 2026-10-29 | 6 确认 / 7 授权 | 用户"继续"（针对 Q-1—Q-4、Q-7 与阶段 7 授权的提问）：Q-1 经运营 API 批准两个在途测试案件（L 240686862329565184、H 241049829650845696）作验收数据，LEGACY "Accept Tester" 验 CRR 为空；Q-2 菜单排序保持 90；Q-3 不给常设角色授权；Q-4 规则初值 USD 50,000；Q-7 Platform 随本次发布；授权阶段 7（mor-test 停 / 起 risk-decision、operations-console-bff、platform-support-service、operations-console-web，mor_risk_decision DDL + DML，块 143，表级 GRANT，data-query 数据源与模板，语言包 #23，验收账号与数据）。主会话按 release/runbook.md 执行 | 已确认 |
| 2026-10-29 | 7 mor-test 发布 | 主会话按 release/runbook.md 执行（18:45—18:53Z）：备份 `~/Code-MoR-backups/rq03-20261029/mortest-20261029T184520Z`；预检同基线；mor_risk_decision 3 表 + 规则 v1 / USD 50,000；risk-decision `8934f65a`、ops-bff `4f08f1e6`、Platform `49f71011`、ops-web `2f7722da`（259 文件）均 healthy、无 ERROR（ops-bff 在 Platform 重启窗口内有预期的 DEPENDENCY_UNAVAILABLE）；块 143 休眠 619 → 激活 631；语言包 #23（发布请求 504，Platform 已完成，READY 且为当前）；表级 GRANT `mor_risk_decision.t_client_underwriting`；数据源 `MOR_RISK_DECISION_REPLICA` 与模板 `GOLIVE_UNDERWRITING_APPROVED` ACTIVE；不建常设角色。验收：accounts 12/12、prepare 20/20（批准 L 240686862329565184、H 241049829650845696）、api + check 全 PASS（自动承保 → 修订 → 作废、HIGH 四眼与本人复核 409、CRR 为空 MEDIUM、统计与列表口径与库一致、模板、日志不含 Client 名称）；浏览器走查 zh-CN / en × 1440 / 390 每组 33 步通过（132 张，[release/stage7-report-browser.md](./release/stage7-report-browser.md)，无写入）；菜单资源页识别承保菜单与两个按钮；已通知 design-discuss-24、design-discuss-19 | 已完成 |
| 2026-10-29 | 后续待办 | 用户"可以"：登记承保建议策略为后续小需求（REQ-03b，未立项）：风控给出政策数值后，按 CRR × 交付方式配置建议观察期、T+N、保证金比例，承保页预填并提示偏离，自动承保套用建议值；登记于客户上线决定第 5 节 | 已登记 |
| 2026-10-29 | 7 确认 / 8 授权 | 用户"提交"：授权提交（私有索引）；推送由用户执行 | 已确认 |
| 2026-10-29 | 8 提交 | 工作树改动合入主树并提交（代码、SQL、PDManer、文档、验证与发布材料、本记录、CURRENT_IMPLEMENTATION、requirements/README、客户上线决定；不含本机库备份 `verification/stage5/backup/`、`.vite-cache` 与 node_modules 链接；`operations-console.pen` 混有其他会话改动未纳入，原型以 `prototypes/` PNG 为准） | 已完成 |

## 适用阶段

默认八阶段（2026-10-29 用户确认）。SQL、mor-test 部署、提交、推送分别授权。

## 当前阶段与下一确认点

阶段 1—8 已完成（2026-10-29）。mor-test 自 2026-10-29 18:46Z 运行 rq03-20261029a（risk-decision `8934f65a`、ops-bff `4f08f1e6`、Platform `49f71011`、ops-web `2f7722da`），mor_risk_decision 3 表 + 规则 v1 / USD 50,000，块 143 激活（631 路由），语言包 operations-console #23，data-query `MOR_RISK_DECISION_REPLICA` + `GOLIVE_UNDERWRITING_APPROVED`。推送由用户执行。验收数据：L、H 两个测试 Client 已 ACTIVE 并有承保记录、M 有拒绝记录、3 个 `RQ03_ACCEPTANCE_*` 账号。本机 e2e：risk-decision 为本需求构建（`*.pre-rq03`）；ops-bff 已由 tax 会话换为 REQ-03 + tax 合并构建（`*.pre-tax` = 本需求构建）；运营 Vite 18314；本机语言包 #15 / #16；3 个测试角色加了 `RISK_DECISION_CONFIG_READ`。遗留：O-1 规则数值待风控确认；REQ-03b 承保建议策略（待办）；platform-support-initial-data.sql 全新库第 7987 行中断（另行处理）；清单另缺 OPERATIONS_EXTERNAL_INTEGRATIONS 与风控测试候选两个按钮（与本需求无关）。

## 阶段 4 实施记录（前端）

> 2026-10-29，operations-console-web，工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq03`。未提交、未部署、未连 mor-test。

**文件**

| 路径（`operations-console-web/`） | 内容 |
|---|---|
| `src/underwriting/underwriting.ts`（新） | ops-bff 路径（U1—U12 + 工作台）、类型、全部响应解码器（详情、列表分页、历史、统计、预览、工作台）、页面模型：列表筛选、判定清单分组、敞口风险天数构成、结论表单默认值 / B5 校验 / 请求体、四眼、修订、新建页去向 |
| `src/underwriting/UnderwritingParts.tsx`（新） | 尽调卡、业务信息卡、估算敞口卡（公式与风险天数说明）、输入来源卡、只读结论、自动判定卡、版本历史、409 / 422 提示 |
| `src/underwriting/UnderwritingWorkspace.tsx`（新） | 新建页（原型 02 自动 / 03 人工）、草稿编辑、T+N 变化 400 ms 防抖调用 U11 刷新敞口、保存草稿（U5 / U6）、提交 / 拒绝（U7）、一键自动承保（U9） |
| `src/underwriting/UnderwritingDetail.tsx`（新） | 版本详情：待复核复核区（原型 04，U8，提交人本人按钮禁用）、只读详情 + 版本历史 + 修订（原型 05，U10，修订原因必填） |
| `src/underwriting/useUnderwritingCommand.ts`、`underwriting.css`、`underwriting.test.ts`（新） | 写命令（Idempotency-Key，确定 4xx 后换键）、样式（1100 以下单列、520 以下标签上下排列）、单测 |
| `src/pages/RiskUnderwriting.tsx`（新） | 路由分派 + 列表页（原型 01：统计卡 U12、状态页签含已作废、搜索、CRR / 来源筛选、待我复核、列、分页写入地址）+ 按 Client ID 新建弹窗 |
| `src/navigation/pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts` | 注册 `OPERATIONS_RISK_UNDERWRITING`（`/risk/underwriting`，按钮 MANAGE / APPROVE）与 `/new`、`/:underwritingId` 子路径 |
| `src/masterData/ClientDetail.tsx`、`src/pages/Clients.tsx` | Client 详情"承保"入口（有已批准申请且可见承保菜单时） |
| `src/clientReview/ClientReviewDetail.tsx` | 入驻审核已批准案件"承保"入口（可见承保菜单时） |
| `src/i18n/en/*.json`、`i18n/zh-CN/*.json` | risk 242 键（`risk.underwriting.*`）、nav 3 键、master-data 1、client-review 1；en / zh-CN 键集一致，无硬编码中文 |
| `src/configuration/configurationModel.test.ts` | 页面注册数 78 → 79 |

**验证**

- `npx tsc --noEmit` 通过；`npm test` 1617 / 1617 通过（新增 `underwriting.test.ts` 16 个，含按后端确切形状的回归与阈值解码：解码正反例、筛选、金额分组、B10 风险天数、判定清单、B5 校验与请求体、四眼、修订与新建去向、按钮、422 字段映射、动态文案键全覆盖、路由注册）；i18n 硬编码中文守卫 0；`vite build` 通过；`npm run i18n:export-source` 通过（25 命名空间 10259 键）。
- 阈值（主会话追加）：预览响应新增可空 `thresholdAmount` + `thresholdCurrency`（页面按预览顶层字段解码，两者须同时为空或同时有值）；有阈值时判定行按原型显示"预计月流水 USD 30,000 ≤ 阈值 USD 50,000"，为空时回落"不超过规则阈值"；新增文案键 `risk.underwriting.auto.group.volumeThreshold`（risk 共 242 键）。该改动在浏览器自测之后，只有单测与构建覆盖。
- 浏览器自测（本机 Vite + 临时假网关返回按 technique §8 构造的样例，不连真实服务）：en 1440 走列表、新建自动承保 → 一键生成、HIGH 待复核（复核人视角拒绝无意见时报"必填"、批准成功；切换为提交人后两按钮禁用并提示）、已批准详情 + 版本历史 + 修订（无原因报必填 → 填写后进入 v3 草稿）、草稿 T+N 改 7 后敞口由 EGP 90,000.00 实时变为 210,000.00、勾选保证金未填金额提交报必填；zh-CN 1440 列表与 390 详情；390 宽度下列表、新建、草稿、已批准、草稿（无 CRR）、已拒绝 6 个页面 `scrollWidth = 390` 无横向滚动。真实后端联调待后端完成后在阶段 5 / 本机 e2e 进行。

**与原型的差异**

| 原型 | 实现 | 原因 |
|---|---|---|
| 03 页头"已自动保存 14:18" | 手动"保存草稿"，保存后显示"已保存 时间" | U6 带 CAS，自动保存易与他人编辑冲突且接口未约定自动保存；按 technique 只做显式保存 |
| 02 尽调卡"案件 DD-20261027-0042 · 已批准" | 显示"案件 {caseId}"并链接到审核案件 | 快照只有 `sourceCaseId`，无案件编号 |
| MCC / 行业名称（"5734 计算机软件"） | 取 Platform refdata 文案，无文案时显示编码 | 快照只存编码；名称由 refdata 语言包提供 |
| 01 列表"新建承保" | 弹窗按 Client ID 打开承保页；常规入口在 Client 详情与入驻审核 | 承保员不一定有客户列表权限，client-master 列表也无关键字搜索 |
| 01 "本月已批准 / 已拒绝"统计卡 | 只展示数字；生效中 / 草稿 / 待复核可点击切到对应页签 | 列表无按月筛选 |
| 新建页已有有效结论 | 提示"已有生效承保 v{n}，请打开后修订" | 同一 Client 只能一份有效结论，变更走 U10 修订 |

**后端合同核对（rq03-backend 回复后）**：工作台多出 `sources`、`canManage`，`input` 不带 `crrMissing`（页面按 `crr` 为空推出）；详情多出 `createdAt`、`updatedAt`；U11 页面请求体 `{ clientId, suggestedSettlementDays }`、U9 不需请求体（页面发 `{}` 被忽略）、U8 无意见不带 `comment`——均与页面实现一致，无需改动；补 1 个按其确切形状的回归单测（underwriting 单测共 16 个）。解码对 ops-bff 追加的展示字段宽松（缺省为空、多余字段忽略），核心字段严格。

## 阶段 4 实施记录（后端）

> 2026-10-29，risk-decision + operations-console-bff + Platform 注册块 143 + data-query 文本，工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq03`。未提交、未推送、未部署、未连 mor-test；未执行 Platform / data-query SQL。

**已读规范**：`DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md` + `examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（5.1、8—11、13—15）、`decisions/2026-09-16-payment-based-coding-conventions.md`；样板为名单维护（`AddRiskListEntryProcessor` / `RiskListServiceImpl` / `RiskListEntryRepositoryImpl`）。

**职责与事务（WRITE_API_EXAMPLE §1）**：六个写 Processor 位于 `com.paytech.risk.control.service.processor`（`AbstractLockProcessor`，锁 `risk-underwriting:client:{clientId}` TRY_LOCK 500 ms / 租约 30 s；按版本 ID 的命令在 `lockCommand` 先读版本取得 Client）；本地原子写入在 `UnderwritingServiceImpl`：批准时同一 `TransactionTemplate` 先把上一有效版本置 SUPERSEDED（清 `effective_slot`）再写决定 / 插入自动承保行；其他写入为单行条件更新（期望 `status` + `version`，行数不符报版本冲突）。risk-decision 无事务外步骤（输入由 ops-bff 随命令带入，S-04）；ops-bff 不开事务，先读 Owner、再单次写 Risk。

**文件**

| 范围 | 路径与内容 |
|---|---|
| SQL / 模型 | `paytech/risk-decision/docs/database/sql/changes/2026-10-29-client-underwriting.sql`（3 表 `CREATE TABLE IF NOT EXISTS` + 规则 v1 / USD 阈值 `ON DUPLICATE KEY UPDATE`，可重跑）、`…-rollback.sql`（有承保记录 SIGNAL 拒绝，否则 DROP 3 表）；`risk-decision-schema.sql`、`risk-decision-initial-data.sql`、测试 `init/ddl.sql` 同步；`risk-decision.pdma.json` 直接编辑：3 个实体（58 / 8 / 6 字段，SMALLINT 用 INT 基类，金额 / 时间 / 币种用同库数据域）、索引同 DDL、新图组 `underwriting`（承保） |
| risk-decision `-api` | `api/control/ClientUnderwritingApi`（U1—U12，`/risk-underwriting/v1`）、`control/request/*`（输入 / 结论 Body 与 7 个请求体）、`control/response/*`（详情、快照、敞口、列表行、当前、历史、统计、预览、检查项）、`api/enums/RiskUnderwritingErrorCode`；pom 增加 Lombok（provided） |
| risk-decision `-core` | `core/enums`（状态、来源、风险级别、档次、Client 类型、交付方式、规则状态、提交结论）、`core/dal`（3 个 DataObject、3 个 Mapper + `mapper/control/*.xml`）、`domain/underwriting`（输入、结论、内容、敞口、自动规则与检查、视图、决定事实、上下文、统计、预览、筛选、当前、有效结论摘要、两个领域异常）、`domainservice/repository`（3 个仓储 + Impl）、`domainservice/validator/underwriting/UnderwritingContentValidator`（草稿组合 / 提交必填）、`domainservice/service/ClientUnderwritingQueryService` + Impl（REQ-04 `findEffective`）、`service/UnderwritingService` + Impl、`service/processor/*Underwriting*Processor`（6 个）、`UnderwritingLocks`、`request/*` |
| risk-decision `-app` | `ext/rest/control/ClientUnderwritingController`、`UnderwritingWireConverter`、`service/convert/UnderwritingExceptionConverter`（领域失败 → 422 / 公开码）；`PersistenceConfiguration` 注册 7 个枚举 TypeHandler；`RiskApiErrorConfiguration` 映射 404 / 409 |
| risk-decision 测试 | `auto/ext/http/control/ClientUnderwriting{Lifecycle,AutoRule,Query}ApiAutoTest`、`test/base/AbstractUnderwritingApiTest`、`test/builder/control/UnderwritingRequestBuilder`、`test/checker/control/UnderwritingResponseChecker`、`test/mapper/control/UnderwritingTestMapper`、`test/mocker/control/UnderwritingRuleMocker`（Clear：删承保行与测试规则、恢复规则 v1）；`RiskDecisionMockTestBase` 导入、`RiskDecisionDataCleaner`（数据表 + 规则目录行）、`RiskDecisionDatabasePrecondition`（58 表 + 规则 v1 校验）、`RiskDecisionArchitectureTest`（api 允许 Lombok） |
| ops-bff | `ext/rest/RiskUnderwritingController`（12 个）、`configuration/RiskUnderwritingRequestConfiguration`（页面 Body 严格解析、16 KiB 上限）、`integration/RiskUnderwritingClient` + Impl（回执身份核对、字段错误投影到转发 Body 路径）、`service/processor/Underwriting{InputAssembler,Labeler,WorkspaceProcessor,CommandProcessor,PreviewProcessor,ListProcessor,DetailProcessor,HistoryProcessor}`、`request/*`、`service/model/riskunderwriting/*`、`domain/enums/UnderwritingInputStatus`；`RiskClientConfiguration`（`ClientUnderwritingApi` 代理 + 错误码登记）、`RiskOwnerResponseShapeValidator`（承保响应精确键集合）、`OperationsErrorStatusResolver`（404 / 409）；README |
| ops-bff 测试 | `auto/RiskUnderwritingBrowserAutoTest`（9 例）、`test/simulator/RiskUnderwritingScenario`（DTO 配置）+ `RiskDecisionConfigurationSimulator` 接入、`PlatformOperationsSimulator`（12 条路由权限）、`OperationsConsoleBffApplicationAutoTest`（路由清单 +12）、`RiskClientConfigurationAutoTest`（见 D-8） |
| Platform | `platform-support-initial-data.sql` 末尾追加块 143（DISABLED / REVOKED）；需求目录 `db/platform/registration-143.sql`（153 行）、`-activate.sql`、`-rollback.sql` |
| data-query | `db/data-query-templates.sql`（`GOLIVE_UNDERWRITING_APPROVED` 正文与登记字段）、`db/data-query-data-source.sql`（只读授权 + `MOR_RISK_DECISION_REPLICA` 登记步骤，均为发布动作，未执行） |
| arch-rules | `table-ownership.yaml` 手工加 3 表、生成脚本 `stepAddedTables` 加同一登记、`TableOwnershipAutoTest` 计数 +3（D-9） |
| 文档 | `RISK_DECISION_PHYSICAL_DECISIONS.md` 10.4、`agent-docs/database/RISK_COMPLIANCE_DATABASE.md` 4.9、决定 3.3 输出补 `suggestedSettlementDays` 与原因码、risk-decision README、需求目录 `db/ddl.sql`、`db/dml.sql`（索引）；未改 `CURRENT_IMPLEMENTATION.md` |

**本机数据库变更**：仅 `mor_risk_decision`（auto 库）：执行前用 `mysqldump --no-data` 备份结构（scratchpad `rq03/mor_risk_decision-structure-before-20261029.sql`，55 表）；执行 `changes/2026-10-29-client-underwriting.sql` 后为 58 表，规则 290001 ACTIVE、阈值 290101 USD 50000；`information_schema` 列名 / 类型 / 可空 / 默认 / 注释与 PDManer 逐列比对 0 差异。前滚两次与回滚（有数据拒绝、无数据删除）先在临时库 `rq03_scratch_rd` 试跑后删除；块 143 在临时库 `rq03_scratch_plat` 试跑（回滚空操作 → 插入 153 行 → 激活 12 路由 + 3 授权）后删除。未触碰 `*_v2e2e`、`mor_platform`（仅一次只读 COUNT 确认块号空闲）、mor-test。

**验证**（JDK 25，`./mvnw -o -Dmaven.repo.local=/Users/kenny/.m3-rq03/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`，模块串行）

| 命令（`-pl` 模块，均在 `paytech/` 下） | 结果 |
|---|---|
| `risk-decision/*` `install -Dtest='ClientUnderwriting*,RiskDecisionArchitectureTest,ArchitectureAutoTest'` | 71 / 71 通过（生命周期 27、自动规则与敞口 25、查询 / REQ-04 / 模板 6、架构 12 + 1） |
| `risk-decision/paytech-risk-decision-test` `test`（全模块） | 1798 / 1798 通过（增补阈值后重跑；此前一轮因运行中重编译出现 20 个 AutoRule 用例假失败，已丢弃） |
| 加入 `/risk-underwriting` 元数据日志前缀后，最终全模块 `install`（surefire 20:17—20:21 / 20:21—20:24，晚于日志改动） | risk-decision `paytech-risk-decision-test` **1799 / 1799**；ops-bff `paytech-operations-console-bff-test` **2459 / 2459**；failures 0、errors 0、skipped 0 |
| `tools/arch-rules` `install` | 70 / 70 通过 |
| `operations-console-bff/*` `test`（全模块，无共享库） | 2459 / 2459 通过（含新增 9 例）；增补阈值后重跑承保 / 形状 / 路由三类 73 / 73 通过 |

覆盖：AC-02（预览全通过 / 8 种单项不满足 / 无 ACTIVE 规则 / 排除清单命中 / 一键自动承保审计身份 / 不满足 409 / 缺来源 422）、AC-03（LOW 建草稿 → 保存 → 提交 APPROVED、MEDIUM 拒绝无原因 422 / 有原因 DECLINED、B5 九种提交必填与九种字段组合 422）、AC-04（HIGH SUBMITTED、自审 409、他人批准与拒绝、HIGH 提交拒绝 409、复核拒绝无意见 422）、AC-05（修订批准前旧版本有效、修订被拒旧版本不变、再修订批准后 SUPERSEDED 与 `supersedesUnderwritingId`）、AC-06（快照冻结与来源坐标）、AC-07（无 CRR 按 MEDIUM）、AC-09（同 Client 并发建草稿 201 + 409 且只落 1 行、过期版本 409）、AC-12（四种交付方式、T+N 为空按 0、三种不可算原因、保存时重算冻结）、REQ-04 `findEffective` 有 / 无、AC-08 模板正文在本机库只读执行四态单行；详情响应键集合与 ops-bff 形状校验一致。ops-bff：工作台选最近已批准申请的案件、输入组装（REQ-12 四项、来源坐标、卡组织高风险）、来源降级三态、只读用户不预览且维护路由 403、命令重新读取输入、伪造 input 400、来源不可用 503 不调用 Risk、Owner 404 / 409 / 422 透传与字段投影、待我复核填充、显示名、回执不符 502。

**偏差**

| 编号 | 偏差 | 原因 |
|---|---|---|
| D-1 | `paytech-risk-decision-api` 新增 Lombok（provided），同步 `RiskDecisionArchitectureTest` | 承保 Body 字段多，与其他服务 `*-api` 一致用 `@Getter/@Setter`；运行期无依赖 |
| D-2 | 代码落在既有 `com.paytech.risk.control.{core,domain.underwriting,domainservice,service}` 分层，而非 technique 写的 `rd/control/underwriting/...` 子树；`ClientUnderwritingQueryService` 在 `control/domainservice/service` | `PersistenceConfiguration` 的 MapperScan 与架构测试固定 `control.core.dal.mapper`，沿用名单模块布局 |
| D-3 | 详情响应多 `createdAt`；预览响应多 `exposureUnavailableReason` 与 `thresholdAmount` / `thresholdCurrency`（主会话要求，technique 8.1 已同步）；`formula` 为"{月流水} × {风险天数} ÷ 30"文本 | 页面需要创建时间与不可算原因；公式文本供页面直接展示 |
| D-4 | ops-bff 预览页面 Body 为 `{clientId, suggestedSettlementDays}`（不收 decision）；自动承保无 Body（`{}` 被忽略） | `/previews` 路径不含 Client，BFF 需按 Client 重新取输入；与 web 执行者约定一致 |
| D-5 | O-5 用 Platform 实时授权"承保预览路由"判断维护权限（工作台每次多一次 PLT-I02 调用）；`canManage` 一并返回 | BFF 不持有权限清单，复用现有授权端口最小实现 |
| D-6 | 页面 Body 未知属性返回 400（BFF 严格解析器统一行为），字段格式 422 | technique 写"未知字段 422"；与 BFF 现有名单等页面一致 |
| D-7 | 修订复制结论时，若新输入的卡组织高风险标记与复制来的登记标记不同且旧版本无理由，登记标记回到新默认值 | 否则修订草稿会因"改默认须写理由"无法创建；提交前由承保员确认 |
| D-8 | `RiskClientConfigurationAutoTest` 增加限定白名单：承保相关 BFF 文件可引用 Risk `control` 包内承保 DTO / API 与 `RiskUnderwritingErrorCode`；`UnderwritingInputAssembler` 加入 compliance 直接消费者 | 工作台与命令转发按 Owner 合同组装 / 读取（同入驻审核消费 compliance 的先例），不为 30 余个字段复制一套 BFF 镜像模型；名单等其他 Risk 合同的限制不变 |
| D-9 | `table-ownership.yaml` 手工加 3 表（生成脚本同步登记） | 生成脚本已不能复现已提交的 YAML（client-master 若干表为他人手工加入，存量漂移），重新生成会删掉他人登记 |
| D-10 | "本月已批准"统计含之后被替代（SUPERSEDED）的版本；金额输出至少 2 位小数 | 被替代版本也是本月作出的批准；risk-decision 无币种精度目录 |

**遗留 / 待主会话**

1. 磁盘空间：一度只剩约 860 MiB（一次编译失败后重试成功）；main 清理后约 3.9 GiB。
2. ~~`/risk-underwriting` 元数据日志前缀~~：已按主会话决定加入 risk-decision（`application.yml`、`application-mock.yml`）与 ops-bff 的元数据前缀，并同步 `WebLoggingBoundaryAutoTest`、`RiskDecisionArchitectureTest`、`OperationsConsoleBffApplicationAutoTest`；ops-bff 承保测试断言日志不含 Client 名称与网站。阶段 5 本机日志核对通过。
3. `platform-support-initial-data.sql` 在全新库上于第 7987 行（块 143 之前）因 `target_id` 为空中止，为存量问题；块 143 已在已初始化库上验证。
4. `generate-table-ownership.mjs --check` 存量漂移（见 D-9），未修。
5. 发布动作未执行：mor-test `mor_risk_decision` 前滚、块 143 插入 / 激活 / 快照重发、`GRANT SELECT ON mor_risk_decision.*`、登记 `MOR_RISK_DECISION_REPLICA` 与模板、语言包；本机 e2e（`*_v2e2e`）与 REQ-05 DATA_QUERY 实际消费模板留阶段 5。
6. O-1 规则初值（USD 50,000、排除清单空）仍待风控确认；工作台 `input` 不含 `crrMissing`（页面按 `crr` 为空判断）。

## 阶段 5 本机联调记录（后端）

2026-10-29 20:26—20:41（+04），只动了本机：`mor_risk_decision_v2e2e` 前滚、`mor_platform_v2e2e` 块 143 插入并激活（快照 routeCount 520 → 532）、本机 risk-decision 与 ops-bff 换成 worktree JAR（`*.pre-rq03` 备份，新 pid 27215 / 27317，保持运行），并对 `mor_compliance_v2e2e` 写了一行测试数据（CRR 置空，有回滚脚本）。未碰 mor-test，未提交，未登记 data-query 数据源。详见 [阶段 5 报告](./verification/stage5-report.md)。

- API 联调 `verification/stage5/stage5_e2e.py`：经 Nginx → Gateway → ops-bff → risk-decision 真实链路，覆盖权限、自动承保 → 修订 → 作废、MEDIUM 人工承保（保证金 / T+N / 敞口 / 校验 / 并发版本）、HIGH 四眼（拒绝、本人复核 409、他人批准）、CRR 为空、列表筛选与统计、模板只读四态、日志只记元数据，**全部通过**（终跑 44 PASS / 0 FAIL / 5 SKIP 重跑跳过；完整执行见 `logs/20`—`22`）。
- 演示数据：3 个已提交入驻案件经真实运营 API 审完并批准（MEDIUM、HIGH 卡组织高风险 MCC 5967、HIGH）；IAM 新建 3 个账号：承保员、负责人、只读（凭据 `~/Code-MoR-backups/rq03-stage5/accounts.json`，0600）。浏览器走查用的 6 个 Client 与页面状态见报告第 3 节。
- **未执行**：停本机 compliance 验证"来源不可用 503"的命令被权限分类器拒绝，没有绕过；该行为由 ops-bff auto 用例覆盖，脚本保留了 `U` 场景供授权后执行。
- 更正：technique §8.1 原写 `RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE` 的 `detail` 列出未通过的 check code，实测是错误码固定文案（Problem 只公开错误码文案），文档已更正（D-11）。非法查询参数返回 400 INVALID_REQUEST，与系统约定一致。

| 编号 | 偏差 | 原因 |
|---|---|---|
| D-11 | 自动承保不满足时，409 的 `detail` 不含 check code | Foundation Problem 只公开错误码文案；页面以 U11 预览展示原因（technique §8.1 已更正） |

**遗留（阶段 5）**

1. "来源不可用 503"只有 auto 覆盖，本机联调未执行（见上）。
2. 本机运行观察（compliance 既有行为）：确认分类后立刻评 CRR，偶发 409 CONCURRENT_REQUEST。
3. 切片 1 的合成复核人因一次登录 504 被重新创建，`reviewer-local.json` 已改指新账号 241600175011794944。
4. 回滚顺序：先恢复 JAR，再执行块 143 回滚，再执行 risk-decision 回滚 SQL，最后执行 compliance 测试数据回滚（报告第 2 节）。
5. **U1 列表口径调整（阶段 5 浏览器走查后由主会话决定，在已批准原型 01 范围内）**："全部"（不传 `status`）每个 Client 只列最新版本；传 `status` 时列该状态的全部版本（已作废 / 已拒绝页签）；其他筛选叠加在其上，`total` 按同一口径计数；统计（U12）不变。改动：`ClientUnderwritingMapper.xml` 的 filter 增加 `NOT EXISTS` 更高版本条件（走 `uk_client_underwriting_ver`），`ClientUnderwritingApi` 注释，technique §8.1 U1；`ClientUnderwritingQueryApiAutoTest` 新增 2 例（全部只含最新版本；状态页签含旧版本）。risk-decision 全模块 `install` **1801 / 1801**（第一轮 1801 中有 1 例失败：无关用例 `RuleSetFreezeApiAutoTest.test_invalidParameter[2]` 清理时本机 MySQL `Communications link failure`，原样重跑后通过）。新 JAR 已于 21:03（+04）换上本机 e2e（pid 46315，`.pre-rq03` 仍为 10-25 原版）。本机核对：全部 5 行（原 8 行，5 个有记录的 Client）、已作废 1、已拒绝 2，统计不变，重启后无 ERROR（`verification/stage5/logs/31-u1-check.log`）。


## 阶段 6 发布准备记录（后端执行者）

2026-10-29 21:30—22:20（+04）。mor-test 只做了只读操作，未提交、未推送。材料在 [release/](./release/)：[stage6-report.md](./release/stage6-report.md)、[runbook.md](./release/runbook.md)、[baseline.md](./release/baseline.md)。

- 基线：现网 risk-decision（relsnap `288b0ff6`）、ops-bff（REQ-05 `6833f484`）、ops-web（REQ-12 `0b456b8a`）都在 `e3e0c12f` 之内，与干净构建的差异只有 client-master-api 的行号表。
- 候选 `rq03-20261029a`：risk `8934f65a`、ops-bff `4f08f1e6`、ops-web `2f7722da`。JAR 与阶段 5 验证的 JAR 条目相同；env 与现网逐键逐值相同，不新增配置；ops-web `npm test` 1617 / 1617。
- 离线演练：mor_risk_decision 前滚 / 重跑 / 有记录时拒绝回滚 / 回滚后与导出逐行相同；块 143 编译 619 → 631（+12）→ 停用 / 删除后回到 619，库逐行恢复。
- 准备了 data-query 表级 GRANT 与 API 登记脚本、语言包 #23 计划（新增 247）、mor-test 验收脚本（accounts / prepare / api / check）。
- 待用户确认：Q-1 是否批准两个在途测试入驻案件（L / H）、Q-2 菜单排序、Q-3 角色（建议不授权）；**阶段 7 需要明确授权**。
- 增补（22:25）：Platform 的页面能力清单 `console-resource-capabilities.json` 漏登了 `OPERATIONS_RISK_UNDERWRITING` 及其两个按钮（菜单资源管理页因此会把它们标为不支持、拒绝编辑；导航、权限、路由不受影响）。已在研发和发布两个 worktree 补上，与 Web `pageCapabilities.ts` 一致；Platform `OperationsRoleManagementTest` 11 / 11 通过。新增 Platform 候选镜像 `49f71011`，与现网 JAR 只差这一项；runbook 新增 ⑥b / R6b，是否随本次发布待定（Q-7）。详见 stage6-report 第 8 节。
