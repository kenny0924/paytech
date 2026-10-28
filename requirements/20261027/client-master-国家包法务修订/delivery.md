# 交付记录：client-master 国家包法务修订

## 适用阶段

阶段 1—8 均适用；阶段 2、3 合并评审（用户 2026-10-27 确认）。

## 确认记录

| 阶段 | 成果 | 用户原意 | 时间 | 获准的下一步 |
|---|---|---|---|---|
| 立项 | 法务 26 项对照：现状已符合 / 改配置可补 / 需决定三类 | "可以"（同意把第二类做成一个小需求） | 2026-10-27 | 阶段 1 需求评审 |
| 1 | [requirement.md](./requirement.md) 评审稿（S-1—S-8、AC-01—11 / V1—V2、Q1—Q4） | "按建议就绪"：Q1—Q4 均按建议；阶段 2、3 合并 | 2026-10-27 | 阶段 2+3 现状差异与方案设计（只出文档） |
| 2+3 | [technique.md](./technique.md) 评审稿（含第 2.2 节：compliance 不改；声明失效规则对 v1 申请也生效） | "继续" | 2026-10-27 | 阶段 4 研发；本地库 DDL / DML 另行请示 |
| 4 | 阶段 4 结果（见下） | "可以"；阶段 5 按方案 1（本机 e2e 浏览器验收）；上线前与其他会话协调优先级 | 2026-10-28 | 阶段 5 |
| 5 | 阶段 5 结果（见下） | "其他会话做完了"（参考数据客户端组件 `e2185c1b`、卡 BIN `c4ff0e20` 均已提交并上线，按此进入阶段 6） | 2026-10-28 | 阶段 6 mor-test 发布准备（不部署） |
| 6 | 阶段 6 结果（见下） | "继续"（授权阶段 7 部署 mor-test；验收账号未另行选择） | 2026-10-28 | 阶段 7 |
| 7 | 阶段 7 结果（见下） | "可以"（进入阶段 8：本地提交、不推送；客户门户 v2 草稿补验未另行选择方式，不做） | 2026-10-28 | 阶段 8 |

## 当前阶段与下一确认点

阶段 1—8 完成。推送另行授权；客户门户 v2 表单的 mor-test 补验如需要，由用户选择账号方式后再做。

## 后续发布（2026-10-29）

- commercial 开通项前置门禁（REQ-01）在 mor-test 替换 operations-console-bff `eca2df82…`、operations-console-web `7a053544…`（源 = `ac3145f4` + 该需求，对方逐字节核对包含本需求）；ops 语言包 #20（只改 commercial 命名空间）。本需求 `release/compose-candidate.operations-console-{bff,web}.yaml` 已失效，不得再执行；这两个服务的回滚与后续发布以上述镜像为基线，回滚前与该需求会话确认。client-master / client-console-bff / client-console-web 仍为本需求镜像（`c9bb7b8e` / `8fe7f24c` / `3db62571`，复核 healthy）。

## 阶段 8 结果（2026-10-28）

- 提交：`ac3145f4`（父 `663e5e4a`，164 个文件），在 main 上以私有索引提交（未推送），范围 = `Code-MoR-legal` 的 93 个代码 / 设计文件（排除 worktree 专用 `paytech/.mvn/maven.config`）+ 本需求目录 + 法源研究文件 + `requirements/README.md` 本行 + `CURRENT_IMPLEMENTATION.md` 本节；与 `e2185c1b..HEAD` 无重叠文件。验收截图、请求 / 响应体、运行日志和样例 PDF 不入库（本机保留在 `verification/ui-smoke/`）。
- 清理：删除本机临时库 `mor_client_master_legal_mt`、`mor_client_master_legal_fresh` 和发布工作树 `Code-MoR-legal-rel`；保留 `Code-MoR-legal`（研发工作树）与测试库 `mor_client_master_legal`、`mor_client_master_legal_inc`（阶段 4 增量验证库）。
- 本机遗留（未清理，需另行决定）：本机 Platform 测试账号 `legal-ui-*@example.test`、`mor_compliance_v2e2e` 孤立案件 240934496831033344、`mor_platform_v2e2e` 5 条 `secret_hmac` 更新（见 [sql-execution-log](./verification/sql-execution-log.md)）。

## 阶段 7 结果（2026-10-28 05:40—06:0xZ）

- 执行：按 [release/runbook.md](./release/runbook.md) 第 3 节 ①—⑨，先通知 4 个并行会话；备份 `Code-MoR-backups/legal-20261028/mortest-20261028T054021Z/`（`mor_client_master-full.sql`、`images-before.txt`、SHA256SUMS、预检 / DDL / DML / 语言包输出）。
- 预检与阶段 6 输出逐字节相同；DDL 两列就位；client-master `c9bb7b8e` healthy；DML 核验 present = pack（2 / 8 / 1 / 12 / 9 / 327），Schema RETIRED 8 / ACTIVE 9、字段 602、规则 91、属性 18、标识类型 10、目录 62，规则分布与试跑相同（[evidence/20-dml-mortest.out](./release/evidence/20-dml-mortest.out)）。
- client-bff `8fe7f24c`、ops-bff `769edbce`、client-web `3db62571`、ops-web `45ee11cb` 均 healthy；5 个服务部署后日志 ERROR / Exception 0。
- 语言包：client-console #7（20 包）、operations-console #19（52 包）READY 且为当前，匿名 manifest 200；脚本 112 项全通过（[evidence/i18n-apply-mortest.out](./release/evidence/i18n-apply-mortest.out)）。
- 验收：
  - 运营控制台（Playwright，test-environment 管理员，en / zh-CN × 1440 / 390）：入驻配置 AE / SA / EG 规则标签、UBO 比较、文档版本、DATE_NOT_PAST、v2 ACTIVE / v1 RETIRED、标志是 / 否；Al Waha 审核详情存量声明区（6 条"针对提交版本"、声明人、中文标签）——44 / 45 / 44 / 45 项全通过，0 个问题（[evidence/ops-legal-*.log](./release/evidence/)，脚本 `verification/ui-smoke/ops_legal.mortest.mjs`）。
  - 回归：卡 BIN 库页冒烟（对方脚本，改写输出到 scratchpad）4 组 22 / 19 / 22 / 19 全通过（[evidence/binlist-regression-*.log](./release/evidence/)）。
  - 客户门户（用户 Chrome 已登录会话，只读）：Al Waha 申请（已于 10-27 提交，审核中）的提交内容页声明"Confirmed 6 / 6"，控制台无错误——存量声明在新代码下仍有效。
  - 参考数据会话复核（在本需求镜像上）：client-master 参考数据就绪 UP、刷新失败 0；ops-bff 财务币种下拉 4 种查询与发布前逐字节相同、非法 status 仍 400；旧接口仍 404。
  - **未验**：客户门户 v2 表单与声明阅读 / 失效（需新建草稿，未在用户账号下擅自创建）；本机阶段 5 已覆盖同一代码。
- 未做：CURRENT_IMPLEMENTATION 更新与临时库 / 发布工作树清理留到阶段 8。

## 阶段 6 结果（2026-10-28）

- 基线：`Code-MoR-legal` 从 `c4b99e2d` 迁到 `e2185c1b`（`git checkout -m`，重叠 2 个文件自动合并、无冲突）；发布工作树 `Code-MoR-legal-rel` = `e2185c1b` + 93 个文件，指纹 `ed85152cba943c84`。
- 测试：client-master 164/0、client-bff 101/0、ops-bff 2433/0、client-web 141/141、ops-web 1576/1576。
- 镜像（tag `legal-20261028a`）：client-master `c9bb7b8e`、client-bff `8fe7f24c`、ops-bff `769edbce`、client-web `3db62571`、ops-web `45ee11cb`；镜像内容与构建产物逐字节相同；Web 基线与现网 dist 逐字节相同；JAR 差异逐条归属（运行卡第 2 节）。
- 覆盖文件 5 + 5，env 与现网逐键逐值相同；SQL 用 mor-test 只读导出在本机试跑（含回滚拒绝分支），结果与全新建库逐行相同；mor-test 只读预检通过。
- 语言包只读计划：client-console #6 → #7（+31）、operations-console #18 → #19（+61），无废弃。
- 对 mor-test 只做了 `docker inspect`、`docker cp` 拷出、只读 SQL、`mysqldump`；未换镜像、未写库、未发布语言包。

## 阶段 5 结果（2026-10-28）

- 本机 e2e 浏览器验收（参考数据、卡 BIN 两个会话同意使用，结束后已恢复）：[verification/ui-smoke/report.md](./verification/ui-smoke/report.md)；环境变更与恢复：[verification/sql-execution-log.md](./verification/sql-execution-log.md)。
- Client：AE 机构（其他机关、草案条款与版本、改资料后 8 项声明失效并锁定提交、重新确认后提交）、SA 机构（年度确认日、"25% 及以上"）、SA 自由职业者（FL 格式、过期日被拒）、EG 机构（两种登记号二选一、增值税号单列）、AE 个人独资（发照机关名称）全部通过；en / zh-CN × 1440 / 390 布局通过。
- 运营：规则配置（新规则、是 / 否、v2 生效 / v1 退役）、v2 案件声明区块（时间、账号、条款版本、与提交资料一致）、存量 v1 案件旧声明全部通过（en 68/68、70/70）；zh-CN 布局通过。
- 验收中修复（Web）：声明失效提示合并为一条汇总；证件名称"号码"重复（含切片 1 既有"营业执照号号码"）；运营声明表列宽；390 页签条横向滚动；Schema 下拉默认启用版本；规则值与申请布尔值显示文字（同时解决切片 1 遗留"运营工作台布尔值显示 true/false"）。测试：client-web 141/141、ops-web 1572/1572，构建通过；client-master 162/0、两个 BFF 未再改动。
- 未在浏览器验证 / 延后：新文案的中文（本机未发布语言包，阶段 7 在 mor-test 验）；AC-10 旧 v1 草稿（测试账号无凭据，未改共享库凭据，由 client-master v1 测试覆盖）；服务端声明失效拒绝（页面先锁定提交，接口已确认返回 `DECLARATION_STALE`）；"超过"文案（本机均为 GTE，单元测试覆盖）；"失效"状态在运营页（接口只返回每类最新声明，提交前已重新确认）。
- 调整：AC-V1"一键重新确认"改为逐项重新确认。
- 后续改进：自由职业文件到期日"不得早于今天"目前保存后由服务端报错，表单 Schema 尚未下发该规则供前端即时提示。

## 阶段 4 结果（2026-10-27）

- 代码：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-legal`（基于 `c4b99e2d`），90 个文件：client-master 55、client-console-web 17、client-console-bff 5、operations-console-web 11、operations-console-bff 1、设计文档 1。未提交。
- 研发中的调整（已写入 technique 3.1 注）：SA 自由职业文件以属性采集；EG 两种登记号二选一；AE 属性按主体类型分两组；新增规则作用域 `DECLARATION` / `ATTRIBUTE`；声明响应加 `declaredByAccountId`；`current` 不含取值；运营规则视图显示新规则，开关类规则显示是 / 否。
- 顺带修复的既有缺陷：`RelationRules.uboSatisfied` 对未填认定依据的受益人调用 `Set.of(...).contains(null)` 抛空指针，持股低于门槛且无依据时提交检查返回 500。
- 测试：client-master 自动测试 162 / 0 失败（专用库 `mor_client_master_legal`，含新增 10 个）；client-console-bff 101 / 0；client-console-web 140 / 0 + 构建；operations-console-bff 2423 / 0；operations-console-web 1571 / 0 + 构建。
- SQL：见 [verification/sql-execution-log.md](./verification/sql-execution-log.md)（两个本地新库；增量与全新建库逐表一致；重跑幂等；回退与再应用通过）。
- 文档：设计 9.5a 26 项逐项结论与状态（AC-11），修正 SA UBO 规则编号与 EG 统一号引用。
- 未做：浏览器验收（AC-V1 / V2）、CURRENT_IMPLEMENTATION、语言包发布。

## 遗留项

见 [requirement.md 第 7 节](./requirement.md#7-本期之外仍需跟踪的法务项)。

## 发布依赖（mor-test，2026-10-28 记录）

- 卡 BIN 会话 20:19Z 已在 mor-test 发布：platform `186347bc`、card `380064fe`、ops-bff `25f6a869`、ops-web `aec066e9`（= `c4b99e2d` + BIN 页）、块 140、运营语言包 #18。本需求的 ops-bff / ops-web 必须在其提交之上 rebase 重建，否则回退 BIN 页。2026-10-28 该会话已本地提交到 main：`8a441c90` + `c4ff0e20`（父 `c4b99e2d`，未推送）；本需求 platform 无改动，client-master / BFF / web 统一以 `c4ff0e20` 为 rebase 基线（参考数据会话的提交到后再叠加）。
- 参考数据客户端组件会话先于本需求发布 client-master / ops-bff 等；本需求 rebase 其提交后重建。2026-10-28 04:08Z 已部署 mor-test 并验收通过：client-master `64e515c6`、ops-bff `4bd7574c`（另 Platform `1e54f349`、card `32d6f27a`、ledger `51a57a3b`、billing `cf580cfb`），旧接口 `/currency-specs`、`/country-regions` 已返回 404；已本地提交 `e2185c1b`（父 `c4ff0e20`，未推送），即 mor-test 现网内容；本需求 rebase 基线改为 `e2185c1b`。预检：本需求 94 个改动路径与 `c4b99e2d..e2185c1b` 仅重叠 2 个文件（`ClientDraftConfiguration.java`、`ClientSubmissionPreparer.java`）。对方确认这两个文件采用其参考数据客户端、仅叠加本需求改动；client-master 测试的参考数据走 test-support 的 Mock（`ClientMasterMockTestBase`），不再引用旧 `ReferenceDataClient`。本需求回滚 client-master / ops-bff 以这两个镜像为基线。
- 上线前与两个会话确认优先级与顺序（用户 2026-10-28 要求）。
