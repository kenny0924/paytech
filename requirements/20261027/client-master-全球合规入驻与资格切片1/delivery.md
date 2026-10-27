# client-master / compliance：全球合规入驻与资格 · 切片 1 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-26 | 来源 | 用户发起"client contract 服务的关系、边界、数据库结构"讨论：全球合规入驻、KYC / KYB、PayFac 合规服务。三路调研（行业模型、AE/SA/EG 监管、现有实现）→ [design-discuss/client-onboarding/design.md](../../../design-discuss/client-onboarding/design.md) + 12 张总板 + 17 张状态机图；两轮确认 ①—⑦、Q1—Q17；过度设计复核砍 4 表；四国扩展演练（KR / RU / US）；规则开关改键值表；硬编码边界与 ZZ 门禁 | 已确认 |
| 2026-10-27 | 来源 | 用户决定：存量 local / mor-test 数据按重写不迁移；Partner 门户工作台列后续计划；筛查先 Mock + 人工兜底；法务 26 项已发出；AGENTS "Partner 只读"加合规例外（第 84 行）；决定记录 [2026-10-27](../../../agent-docs/decisions/2026-10-27-client-onboarding-global-kyc-kyb.md)（Proposed） | 已确认 |
| 2026-10-27 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R11、规则 B1—B10、逐系统变动点（第 7 节）、验收 AC-01—AC-15、待确认 Q1—Q6、阶段安排 | 已确认 |
| 2026-10-27 | 1 确认 | 用户确认阶段 1，Q1—Q6 全部按建议执行（证件 Vault 合并进切片 1；责任方 / 入驻码仅 OE_PROVIDER Partner；版本发布首期单人 + 日志；块号候选 138 阶段 2 核对；保留四分区 + DECLARATIONS；AE 个人商户纳入），进入阶段 2 | 已确认 |
| 2026-10-27 | 2 差异与影响 | 四路只读核对（client-master、compliance、channel-connector + Vault、两套 BFF/Web + Platform 注册），证据归档 [analysis/](./analysis/)；[technique.md](./technique.md) 第 1 节：发现 F-01—F-18、需求项差异表、逐系统影响、待决 S-01—S-20、既有问题、发布顺序与风险；实跑确认 `ComplianceArchitectureTest` 既有失败 1 项；块 138 仓库内无占用 | 已确认 |
| 2026-10-27 | 2 确认 | 用户确认阶段 2，S-01—S-20 全部按建议执行，进入阶段 3；并要求阶段 3 的 Console UI、交互动画与交互流程参照 Stripe 等行业产品做好（对客场景，严谨与体验优先），不要"AI 感"的通用版式 | 已确认 |
| 2026-10-27 | 3 方案与原型 | UX 调研归档 [analysis/stage3-client-console-ux-research.md](./analysis/stage3-client-console-ux-research.md)；`client-console-web/client-console.pen` 右侧方向样张 N0 设计基础、N1 入驻首页 / 人员抽屉、N2 退回首页 / 补件视图、N3 沙特公司信息 / 错误汇总 / 390 移动端 | 进行中 |
| 2026-10-27 | 3—7 授权 | 用户确认 v2 方向，要求：Client Console 全部页面（含登录等认证页）按新规范重画；**把阶段走完、完成测试环境验收**（用户入睡）。据此合并阶段 3—7 连续执行，授权：本地 `mor_client_master` / `mor_compliance` / `mor_vault` / `mor_channel_connector` / `mor_platform` 执行本需求 SQL（先备份）；本地 e2e 替换相关服务；mor-test 按发布清单备份后执行 SQL、替换镜像、部署 Mock 筛查容器、激活块 138 并跑验收；入驻数据按重写清空。**不含**提交、推送（阶段 8 另行授权） | 已确认 |
| 2026-10-27 | 3 评审意见 1 | 用户：① 不做阿语（除非以后有必要）；② 黄色 / 红色框提示显得 AI。处理：界面只 en / zh-CN，不采集阿文名称，删除 `local_*` 与 `name_script`（需求 R10 / 7.1 / AC-14 同步）；提示改为「状态圆点 + 标题区」、错误汇总改为左侧 3px 竖线 + 链接、状态徽章改为圆点 + 灰字，N0—N3 重画为 v2 | 已改稿 |
| 2026-10-27 | 3 原型完成 | `client-console.pen` x=15000 全量 v2：C1 认证 6 页、C2 开始入驻（入驻码 / 国家与主体 / 准备清单）、C3 地址 / 材料上传状态机 / 声明 / 核对并提交、C4 审核中 / 批准 / 未通过 / 逾期 / 已撤回 + 移动端 4 页；PNG 导出 [analysis/prototype/](./analysis/prototype/)。运营工作台沿用 operations-console 现有组件体系，不另画原型（`operations-console.pen` 主工作区有他人未提交改动，避免冲突） | 已完成 |
| 2026-10-27 | 4—5 研发启动 | worktree `Code-MoR-onboard`（基于 ea0272f3）；Opus agent 分工：cm-dev（client-master）、cmp-dev（compliance）、infra-dev（Vault / connector / Mock / 块 138 / 发布基线）、client-fe（client BFF + Web）、ops-fe（ops BFF + Web）；报告写入 verification/ | 进行中 |
| 2026-10-27 | 6 发布决策 P-8 | 主会话决定（用户入睡，按授权范围内最保守口径）：「入驻数据按重写清空」只清进行中入驻数据——mor-test 现有 Client 37 ACTIVE / 17 PENDING，ACTIVE 被 commercial / acquiring 引用（DEMO-CL-001..004 等），保留 ACTIVE Client 及其 Party、OE、Local Partner、关系与披露（作为旧数据，不做证件 Token 化迁移）；清除 PENDING Client 及其申请 / 材料 / 草稿和未批准的合规案件。DDL 新列可空或有默认值以兼容旧行 | 已决定 |
| 2026-10-27 | 6 发布决策 P-11 | 无网副本演练发现：保留的 ACTIVE 旧数据在新 DDL 下仍有明文护照号（identity key 47、profile identifier 50），申请缺 application_type / jurisdiction。决定：脱敏而非 Token 化（合成测试数据）——identity key 置 `LEGACY-REDACTED-<主键>`、公开标识置空加掩码、application_type=ONBOARDING、辖区按国家映射否则 `LEGACY`、schema_id 置空；核验须证明无残留明文个人证件号；旧 Client 详情页须能渲染（加 auto 测试） | 已决定 |
| 2026-10-27 | 4—5 研发完成 | 各 agent 报告见 [verification/](./verification/)：client-master 133、compliance 133、client-console-bff 92、ops-bff 2393、client-web 97、ops-web 1541 全部通过；cm-sql 提供清理 / 国家包增量 / 遗留回填 SQL；rel-build 漂移核对 7 个服务与 ea0272f3 一致后构建镜像（tag `onboard-20261027a`）；arch-rules 表数断言由主会话补 `+ 13 + 4` | 已完成 |
| 2026-10-27 | 6 本机 e2e | `_v2e2e` 库备份后执行全部 SQL，替换 client-master / compliance / Platform / ops-bff，启动 client-bff 与 onboarding client-web；块 138 激活（Gateway 475 → 497）。验收 256 PASS / 1 FAIL（AC-07 需求清单缺退回项期限）→ cm-dev 修复（CM-7 合并 compliance 退回错误；申请级错误挂主体）→ 重跑 105/105。另修 ops-web 批准 422 不显示阻断条件（P1）、需求 B7 / AC-13 筛查提供方码对齐为 `S_SCREENING_MOCK`（P2）。本机 Vault Nacos 注册丢失，重启恢复 | 已完成 |
| 2026-10-27 | 7 mor-test 部署 | 03:47—04:05Z 按 [运行卡](./release/runbook.md) 执行，逐步记录见 [verification/sql-execution-log.md](./verification/sql-execution-log.md)。过程问题：mor-test Vault Nacos 注册丢失（Platform 重启后登录 503 约 1 分钟，重启 Vault 恢复）；Platform 拒绝驼峰命名空间 → 前端改名 `onboarding-schema` 重建两个 Web 后发布语言包；部署中途冻结代码 | 已完成 |
| 2026-10-27 | 7 mor-test 验收 | 全量 242 PASS / 4 FAIL（语言包检查早于发布且用旧命名空间）/ 5 SKIP → 修脚本重跑 SE 10 PASS / 0 FAIL。未在 mor-test 建运营账号（安全检查拒绝），AC-08 四眼批准与 AC-09 资格撤销仅本机验收；CLOSED 辖区由 auto 测试覆盖 | 已完成（遗留见下） |
| 2026-10-27 | 7 浏览器复验 | 用户报告运营「采集 Schema」页错误后，主会话启动 mor-test 浏览器冒烟（中英文、1440 / 390）：发现 3 个 P0（运营工作台解码、客户地址保存、机构添加人员）及 P1 / P2 共 15 项，接口验收未覆盖。逐轮修复并替换（client-master ba3f136f → 0af5b792；client-web 4aaecccb → 9f85e61c → 14690d94 → 34d3d6e3；ops-web 4d4c1130 → 35b61f12 → 1274fefe → 88ab111a；语言包 client #5、operations #12），每次带单服务回滚文件。最终 AE 机构（含 GB 地址人员）与 AE 个人经浏览器提交通过，详见 [verification/ui-smoke/report.md](./verification/ui-smoke/report.md) | 已完成 |
| 2026-10-27 | 8 提交 | 用户授权提交（不含推送）。私有索引提交 `9f506f0f`（基于 `ea0272f3`，939 个文件）：worktree `Code-MoR-onboard` 的本需求改动 + `client-console.pen` + 共享文档中本需求的行（AGENTS.md Partner 例外、design-discuss / requirements 索引行、CURRENT_IMPLEMENTATION 条目）；排除 worktree 专用 `paytech/.mvn/maven.config`、浏览器冒烟截图与原始响应（`verification/ui-smoke/screens|bodies`，83 MB，本地保留）及他人未提交改动 | 已完成 |

## 适用阶段

默认八阶段；阶段 4 的两库本地 SQL、阶段 6/7 的 mor-test 发布、阶段 8 提交分别授权。

## 当前阶段与下一确认点

阶段 3—7 已完成（mor-test 已部署并验收）。阶段 8 已提交 `9f506f0f`；推送另行授权（`paytech/.mvn/maven.config` 只用于 worktree，提交时排除；提交来源为 worktree `Code-MoR-onboard`，按 git status 取文件清单）。

## 测试 / 验收证据

- 浏览器冒烟：[verification/ui-smoke/report.md](./verification/ui-smoke/report.md)。
- 服务级与前端测试：各 agent 报告 [verification/](./verification/)（cm-dev / cmp-dev / client-fe / ops-fe / infra-dev / cm-sql / rel-build）。
- 本机：`verification/acceptance-local-20261027T031549Z.json`（256 PASS）、`…T035414Z.json`（SB 105/105）。
- mor-test：`verification/acceptance-mor-test-20261027T040243Z.json`（242 PASS）、`…T051806Z.json`（SE 10/10）；筛查渠道登记 `verification/screening-channel-mortest.json`。
- 验收脚本与 AC 映射：[assets/acceptance/README.md](./assets/acceptance/README.md)。

## 部署 / 恢复方案及 SQL 执行记录入口

- 运行卡与回滚：[release/runbook.md](./release/runbook.md)、`release/compose-candidate.yaml` / `compose-rollback.yaml`、`release/sql/`（含 SHA256SUMS）。
- 执行记录：[verification/sql-execution-log.md](./verification/sql-execution-log.md)。
- 备份：mor-test `~/Code-MoR-backups/20261027T034703Z-onboarding-slice1-mor-test`；本机 `Code-MoR-backups/onboard-20261027/`。

## 提交记录

- `9f506f0f` feat(client-master,compliance): 全球合规入驻与资格切片 1（939 个文件，未推送）。

## 遗留项及后续需求

- 本切片遗留：C05 缺 submittedAt / withdrawnAt（结果页不显示时间）；运营工作台布尔值显示 true/false；mor-test 未经页面验 RETURNED 页与注册验证邮件（邮件走真实 SES）；入驻码过期状态目前由前端推导（宜由 client-master 推导）。AC-08 / AC-09 未在 mor-test 用第二运营账号验收；Foundation 无请求体写命令丢弃 Problem `errors[]`；后端 labelKey `onboardingSchema.*` 与命名空间 `onboarding-schema` 不一致（前端映射）；国家包 26 项待法务；真实筛查与整数分值校验未验；入驻码作废无原因、Client 详情无激活 / 挂起时间、BFF 侧过滤、PayFac 下拉上限 100；Vault Nacos 注册断线不自动恢复（环境问题，另行排查）。
- 后续切片：S2 修订申请、S3 真实筛查、S4 自动 CRR、S5 持续复审与结算控制、S6 IDV / 登记库、S7 PayFac 计费与导出、S8 Partner / OE 资格复用、S-P Partner 门户工作台（见决定记录第 3 节）。

## 实现 / 验证 / 部署状态

已在 worktree 的 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 登记（随阶段 8 提交进入主工作区）。
