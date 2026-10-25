# commercial：商业关系快照与真实解析接入 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-22—23 | 来源 | commercial 二期执行链路的前置分析发现 acquiring 未接真实解析、资格检查无来源；用户决定拆分为前置 + 二期两个需求，并先自上而下讨论商户对接模式，形成 [2026-10-23 决定草案](../../../agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md)（D1—D13，五轮评审 R1—R21） | 已确认 |
| 2026-10-23 | 来源 | 用户确认 Q5：本轮只实现 C1 / C2，模型按五种模式一次设计；并要求阶段 1 增加“逐系统变动点”分析 | 已确认 |
| 2026-10-23 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R6、规则 B1—B7、逐系统变动点（第 7 节）、验收 AC-01—AC-12、待确认 Q1—Q6 | 已确认 |
| 2026-10-23 | 1 自查 | 对照决定草案 U1 / 9.5 自查：补 7.1 `t_party_role` 变动点（R1 原已承诺）；模型开关统一为 commercial 一处（`commercial.relationship-snapshot.enabled`），acquiring 只冻结返回版本，删去原 `acquiring.relationship-model.enabled`（B5 / 7.3 / 7.4 / AC-08 同步） | 已改稿 |
| 2026-10-24 | 1 确认 | 用户确认需求稿，Q1—Q6 全部按建议执行，进入阶段 2 | 已确认 |
| 2026-10-24 | 2 差异与影响 | 只读核对 client-master、compliance、commercial、acquiring、运营端与下游，结果写入 [technique.md](./technique.md) 第 2 节：发现 F1—F15、逐系统差异、待决 S-01—S-16、发布顺序；修正需求第 1 节 422 归因；登记 Platform 块 137 | 已确认 |
| 2026-10-24 | 2 确认 | 用户确认阶段 2，S-01—S-16 全部按建议执行；需求稿第 4、6、7、8、9 节按其修订（Q3 改为沿用准入范围 MCC，AC-01 / 05 / 09 / 10 / 11 调整），进入阶段 3 | 已确认 |
| 2026-10-24 | 3 方案与原型 | [technique.md](./technique.md) 第 3 节：主流程、快照推导与组合约束、12 个 Owner 接口 + 11 个 BFF 接口、四库 DDL / DML、注册块 137、页面与研发验证计划、阶段 6 待核风险；原型 operations-console.pen 画板 RS-01—RS-08 | 待确认 |
| 2026-10-24 | 3 评审意见 1 | 用户要求 OE 全系统统一用 AE001 格式（entity_no），不用数字 ID：删去 `t_operating_entity.operating_entity_id` 与候选字段；compliance `t_qualification_decision` / `t_role_qualification` 的 `role_id` 改为字符型（OE 存 entity_no）；S-07、需求 7.1 / 7.2 与规模统计同步 | 已改稿 |
| 2026-10-24 | 3 评审意见 2 | 用户决定：取消“OE 来源 PLATFORM / 平台商户”概念，不考虑历史——删除 client-master / commercial / acquiring / risk-decision 的 PLATFORM 来源代码、校验、注释与文档（risk-decision 因此成为改代码系统，共 8 个）；AE001 本轮迁到新建的“我们的 PayFac”Partner 名下（PARTNER 来源，Partner 合同价目先按 0 占位），接受切 REAL 后 AE001 订单按 Partner 分成 / 平台服务费计费；删去 `provider_partner_id` 与占位 Partner；Partner `affiliation` 本轮不做。需求 R1 / R5 / R6 / R7、不做项、B4、7.1—7.7、AC-11 / AC-13 与技术方案第 3 节同步 | 已改稿 |
| 2026-10-24 | 3 确认 | 用户确认阶段 3（含评审意见 1、2），进入阶段 4；授权在本地 mor_client_master / mor_compliance / mor_acquiring / mor_risk_decision 执行本需求 DDL 与数据 SQL（先备份相关表，root 手工执行）。研发在独立 worktree /Users/kenny/Desktop/Workspace/Code-MoR-relsnap（基于 main 8a86a31c）进行 | 已确认 |
| 2026-10-25 | 4 研发实现 | 8 个系统在 worktree Code-MoR-relsnap 完成（未提交）：compliance 437、client-master 100、commercial 211、acquiring 897、risk-decision 188、ops-bff 95、ops-web 1519 个测试通过；本地四库已备份后执行 DDL / 数据；块 137 写入 initial-data（未执行）；文档同步；独立跨系统核查提出的 4 项已处理（发布顺序修订见 technique 2.4）。详见 technique.md 第 4 节 | 已确认 |
| 2026-10-25 | 4 确认 / 5 授权 | 用户确认阶段 4，进入阶段 5；授权：① 本地 mor_platform 执行并激活注册块 137；② 替换本地共享 e2e 的 acquiring / risk-decision / ops-bff / ops-web（备份旧 JAR 可回切），新起 compliance / client-master / commercial | 已确认 |
| 2026-10-25 | 5 本地联调与浏览器验收 | 本地 e2e 栈（`mor_*_v2e2e` 库，执行前备份）：块 137 激活；新起 compliance / client-master / commercial，替换 acquiring / risk-decision / ops-bff；后端 44/44 通过、无代码修改；浏览器 AC-12 30/30 通过（英文 + 中文）；为中文验收补执行本地 Platform 多语言文件策略并发布 zh-CN；ops-web 两处小修（对话框 mask 写法、时间线文案）后测试仍全部通过。详见 technique.md 第 5 节 | 待确认 |
| 2026-10-25 | 5 确认 | 用户确认阶段 5，进入阶段 6（mor-test 发布准备：只读核对、离线 SQL 演练、候选镜像构建与发布方案；不改 mor-test） | 已确认 |
| 2026-10-25 | 6 mor-test 发布准备 | 发布包 worktree `release/`：7 个候选镜像（tag relsnap-20261025a，按 sha256 固定，基线证明无未解释差异，环境变量 0 缺漏）；26 个 SQL 文件经断网副本完整演练与回滚（216 张表与备份一致），块 137 四种状态均能编译 Gateway 快照；三个验收脚本离线干跑通过。发现 F-1（mor-test 无 OE / Partner 主档 → 种子 21）、F-2 GAP-PROFILE（既有缺陷，已批准 Client 资料未定稿 → 补丁 23 只补 DEMO-CL-001，另立需求根治）。用户决定 D-1—D-4、D-7（REAL 与快照开关只在 AC-11 窗口打开，价目 0，合同经运营接口）；D-5 / D-6 按建议 | 已确认 |
| 2026-10-25 | 6 确认 / 7 授权 | 用户确认阶段 6，授权阶段 7 按 `release/release.md` 第 12 节清单执行（7 个镜像替换、5 个库 SQL、运营接口数据、验收写入、AC-11 窗口内临时 REAL / 快照开关、通知会话；不含提交 / 推送） | 已确认 |
| 2026-10-25 | 7 mor-test 部署与验收 | 13:38—14:35Z 按 release.md 第 6 节执行：六库备份 `~/Code-MoR-backups/20261025T133812Z-relsnap-mor-test/`；7 个服务换为 relsnap-20261025a 候选镜像（均 healthy、0 ERROR、环境变量只多两个开关）；SQL 10/11/20/21/22/23/40/41/50/60/61 执行，postcheck 全部等于期望；块 137 激活（Gateway 574 → 585）；语言包 #8（8813 key）；PayFac Partner 合同与两个 0 费率方案经运营接口建立并由临时 checker 批准（账号已停用、权限已撤销）；AE001 / PayFac 资格经页面登记。AC-11 窗口 14:09—14:12 REAL：S-1/S-2/S-3/S-5 PASS，窗口后回 MOCK / false，S-0/S-6/AC-13 PASS；浏览器 31/32（Z1 仅因 Client 业务名称为英文）。阶段内修正 3 个验收脚本问题（rs_bundles 转义、浏览器 Q4 预期、合同脚本 204 / 费率比较）。详见 release.md 第 10 节 | 待确认 |
| 2026-10-25 | 7 确认 / 8 授权 | 用户确认阶段 7，授权阶段 8：按清单本地提交（relsnap worktree 543 个文件，不含 98 张验收截图；加本需求 3 份文档、CURRENT_IMPLEMENTATION.md、requirements/README.md；不含推送） | 已确认 |
| 2026-10-25 | 8 本地提交 | 提交 `4b661830`（548 个文件：relsnap worktree 543 个，含 2 个删除；本需求 3 份文档；不含 98 张验收截图，截图留在 relsnap worktree）；私有索引构建，父提交 main `8a86a31c`，可执行位保留；提交前凭据扫描无命中；未推送 | 已完成 |

## 当前阶段与下一确认点

阶段 1—8 已完成。推送须另行授权（`! git push origin main`）。

## 实现 / 验证 / 部署状态

mor-test 已部署（2026-10-25，见 release.md 第 10 节）；已本地提交 `4b661830`（未推送）；台账见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 发布前置记录（阶段 6 使用）

- 2026-10-23 工作流体验优化已上 mor-test（tag wfux-20261023，源 be688db7 + 10 个数据查询未提交文件）：platform 76587ed4…、ops-bff c9a70b8d…、ops-web a8bb58d7…；覆盖文件 requirements/20261022/platform-support-工作流体验优化/release/compose-candidate.yaml。为这三个服务的现行基线。
- 2026-10-23 code-mor-ae 随后替换 ops-web 为 4861cd0d…（源 main 069dca04 + 10 个数据查询未提交文件，回滚目标 a8bb58d7）。已完成且健康，窗口结束。ops-web 现行基线 = 4861cd0d；本需求的 ops-web 候选须基于 main 069dca04 或更新提交，并继续带上那 10 个数据查询未提交文件。
- 2026-10-23 code-mor-db（多语言管理，块 135）发布窗口进行中：约 1 小时内依次替换 platform / ops-bff / client-bff / ops-web / client-web，执行 mor_platform DDL，并把路由 239087906591948800 的策略由 CARD_BIN_CSV 改指 OPERATIONS_BFF_IMPORT（路由 id / 900s / CSV 格式检查不变，回滚恢复 CARD_BIN_CSV）。路由改指已于 08:56Z 完成并通过 BIN 导入回归（版本 6 VALID、8/8），无需跟进。窗口已结束，最终基线：platform 48032999…（i18n-20261023b）、ops-bff e4d913e0…（i18n-20261023a）、client-bff 9033b8fa…（i18n-20261023a）、ops-web fc3abaa7…（i18n-20261023b，另带 code-mor-55 的 10 个数据查询未提交文件）、client-web 280a2f38…（i18n-20261023a）；源 main ffdf872e + 多语言管理改动，已提交 main ed361c26 + 28939303（未推送）。这五个服务的候选须建在 main ≥ 28939303 之上；ops-web 仍需带 code-mor-55 的 10 个数据查询未提交文件，直到其提交。注意 code-mor-78 随后会再发一次 ops-web（加 37173818 的 F1 修复），届时 ops-web 以其镜像为准。此前的 wfux / 4861cd0d 基线作废。
- 2026-10-23 code-mor-db 发布“运营控制台多语言迁移”（需求 20261023）：Platform 多语言数据（operations-console 默认语言 en、19 个命名空间、约 8.5k key、发布 #3），已上线：ops-bff 3751638e…、ops-web d228e3c8…（源 main 9f210b5c + 集成目录 /Users/kenny/Desktop/Workspace/Code-MoR-opsi18n 未提交改动，提交号待其通知）。本需求的 ops-bff / ops-web 候选须包含其改动。ops-web 源码禁止中文字面量（全量守卫测试）：新文案先加 `src/i18n/en/<ns>.json` 与 `src/i18n/zh-CN/<ns>.json`，再经“多语言管理”导入发布（开发约定第 10 节、`operations-console-web/docs/i18n-migration-guide.md`）；控制台默认英文。另：Foundation SnowflakeIdGenerator 同毫秒序列 4096 用尽即抛 ID_GENERATION_ERROR，种子 / 回填单次批量勿超 4096 个 ID。
- 2026-10-23 07:45—07:49Z code-mor-78（billing 支付事实自动登记）已替换 mor-test 的五个容器：billing 53450948…、account 17831407…、ledger 3819ff28…（源 main 22ede435 + billing-b）；payment cf9097ea…、acquiring 27c36f4e…（源 main d3d39e40 + billing-b，含 card BIN / 3DS）。acquiring / payment 现行基线以此为准。billing-b 已提交 main 98210e0b（未推送）：本需求的 acquiring 候选须建在 98210e0b 或更新提交之上；PaymentSucceeded v2 在 cardLevel 之后新增 channelTransactionRefId / channelReferenceType，消费者为 acquiring、card，OrderPaymentSucceeded 已注册。
