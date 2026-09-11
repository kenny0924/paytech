# 规则集列表优化：阶段与交付记录

- 登记日期：2026-09-11。
- 当前阶段：阶段 8 本地提交与交付归档；本归档记录对应五个应用/组件提交和根仓库归档提交，等待用户确认本需求结束。
- 需求：[requirement.md](./requirement.md)；分析与方案：[technique.md](./technique.md)。
- 原始附件：[用户截图](./assets/rule-list-notice-before.png)，保留原图内容。

## 1. 阶段记录

| 阶段 | 本阶段成果 | 用户确认 | 获准的下一步 |
|---|---|---|---|
| 1. 需求评审 | 初版六项需求及后续澄清，收敛为 AC-01 至 AC-08：单行展示、整体删除、实际生效使用保护、Candidate 入口延期 | 用户确认上述范围，并对“是否进入阶段 2：定位查询和回退问题，分析各系统差异及影响范围？”回复“可以” | 阶段 2 的故障定位、差异及系统影响分析 |
| 2. 现状与影响分析 | HTTP/浏览器复现、只读数据库及源码核对、逐项差异、Risk 能力缺口、系统和 API/SQL 影响清单 | 用户对进入阶段 3 回复“可以”，并补充公共版本与类型命名约束 | 阶段 3 的接口、数据、页面和研发验证方案设计 |
| 3. 方案、接口与数据设计 | 原列表接口调整、整体删除合同、规则集根与历史保护、SQL 草稿、菜单/URL 导航、类型清单和验证计划 | 用户对方案及进入阶段 4 回复“可以”，含编码永久保留、不恢复 | 阶段 4 研发及相关验证；不含 SQL 应用、部署或提交 |
| 4. 研发实现 | 代码、API/模型/SQL 工件、开发回归及 AC 映射已交付，见下文 | 用户对进入阶段 5 回复“可以” | 阶段 5 综合验证与本地验收 |
| 5. 综合验证与本地验收 | 代码复核、名称缺失修复、真实 BFF/Risk 联调及页面验收，见 [acceptance.md](./acceptance.md) | 用户对进入阶段 6 回复“可以” | 阶段 6 test 发布准备 |
| 6. test 发布准备 | 环境/非空 manifest/SQL、三个镜像、配置、备份恢复及合成验收范围，见 [release.md](./release.md) | 用户回复“发布” | 按发布包进入阶段 7，两库备份/所列 SQL、三个镜像部署和限定验收清理 |
| 7. test 部署与环境验收 | 发布、真实权限/业务/页面验收、清理及待提交清单，见 [deployment.md](./deployment.md) | 用户明确“先git提交一下” | 阶段 8 本轮本地提交与归档，不推送 |
| 8. 本地提交与归档 | 六仓库交付记录、选择性暂存及保留项，见 [completion.md](./completion.md) | 提交已明确授权；需求结束待用户确认 | 不自动开始新建议的研发，不推送或重新部署 |

沿用已确认的公共流程逐阶段交付、确认后再推进。原第 7 项为空；AC-07 来自随后明确提出的单行展示要求，并非补造空白条目。

### 2026-09-11 用户澄清

用户明确：“一个规则集在列表应该只有一行，多版本的展示下个需求在做。如果删除应该是删除整个规则集，如果正在使用则不允许删除。”

- 已据此更新需求及技术评审入口，取代初版“保留版本列表、仅删除当前草稿版本”的建议。
- 影响 AC-02/03/06/07：查询与分页按规则集统计，详情/编辑使用当前配置，删除作用于整个规则集并检查所有版本的使用关系。
- 当时通过会话问题请求确认 Candidate 引用是否算使用中；此问题随后经解释和用户确认收敛，结论见下段。
- 本次回复是需求澄清，不视为进入研发、应用 SQL、部署或提交的授权。

### 2026-09-11 TEST Candidate 解释后的确认

用户询问 TEST Candidate 的用途。Agent 核对现行实现后说明它固定引用配置并记录静态检查，当前不包含真实规则试跑、审批与激活；同时修正此前将 Candidate 引用等同使用中的建议。用户回复“可以”，确认如下范围：

- 本期隐藏 TEST Candidate 相关页面与操作入口，完整交互与多版本展示留到后续。
- 正在使用按规则集有版本被当前实际生效发布使用判断；Candidate 引用的历史配置与检查记录保留，但仅存在引用不等于规则实际生效。
- 不删除已有 Candidate 后端能力或历史记录，不扩展到真实运行期实现。

当时同步 AC-03/08 及技术评审入口，保留其他已提出需求；阶段 2 当时尚未开始。后续交接确认及执行结果见下段。

### 2026-09-11 阶段 2 交接确认与成果

用户对明确的阶段 2 交接问题回复“可以”。据此完成只读诊断、差异及影响分析；不视为接口/数据设计批准，也不视为研发、迁移、部署或提交授权。

成果：[technique.md](./technique.md) 的阶段 2 交付稿。核心结论是三个应用代码及 Platform 注册数据受影响；Risk 需要规则集粒度的分页和生命周期，删除需保护所有版本的实际使用及历史回读，Web 需真实导航历史。

编码查询已测试合法/不存在/非法/清空与组合条件。已复现非法格式在 BFF 参数校验返回 400、前端缺少就地提示；原始用户编码仍待补充。已通过会话询问，当前结论未假定原始错误一定由非法格式导致。

### 2026-09-11 阶段 3 交接确认与成果

用户回复：“可以，如果我没有明确说v几的接口，那么就不要升级版本，直接在原来的接口上改，或者默认v1。然后代码类的命名也不要有v1什么的字样”。该确认允许本阶段设计，并直接确定后续公共版本/命名规则，不作为研发、数据库应用或部署授权。

- 公共规则已写入 [DEVELOPMENT_CONVENTIONS.md](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[确认记录](../../../agent-docs/decisions/2026-09-11-api-version-and-type-naming.md)，并同步 Java 工程规范、流程入口和 Cursor 薄投影。
- [技术方案](./technique.md) 第 10–17 节明确修改原 V1 列表、新增整体删除 API、权限、错误/幂等回执、并发保护、页面导航及研发顺序；第 1–9 节保留阶段 2 分析证据。
- [Risk DDL](./db/risk-compliance-service/ddl.sql) 定义规则集根，[Risk DML](./db/risk-compliance-service/dml.sql) 定义历史身份回填；[Platform DML](./db/platform-support-service/dml.sql) 新增 16 行注册/授权数据并对管理员角色做一次版本 CAS 更新。Platform DDL 为 0。
- [类型重命名清单](./assets/type-renames.json) 包含 25 个 DTO record 去版本后缀，以及 1 个非持久化转换器的规范命名；本阶段没有改动这些 Java 源码。
- 删除保留历史、编码永久占用且不提供恢复、新增管理根及 Redis 协调是本阶段具体方案，等待确认；尚未修改 PDManer、应用 Schema 或部署制品。

Risk 回填需要在 SQL 应用准备时，按明确环境生成并审阅 Snowflake 身份 manifest。当前仅交付严格校验该输入的 SQL，不含假 ID 或已可直接应用的环境数据。local 研发验证如需先应用 SQL，可在阶段 4 提前准备 local 清单并单独确认；test 清单在阶段 6 准备，不能混用。SQL 文件未执行，设计批准也不等于数据库动作批准。

## 2. 验证边界

### 阶段 2 只读诊断

本阶段通过本机 `mor-test` 的 `http://localhost:28080` 做登录、只读查询、内存编辑/放弃及退出；通过 test MySQL 只读事务核对聚合计数、发布状态与 Platform 参数白名单。没有提交保存、冻结、创建/校验 Candidate 或删除命令。

- [脱敏诊断数据](./assets/stage2-diagnosis.json)：HTTP 场景/状态/Request ID、浏览器路径与历史长度、独立离开保护观察、两库只读聚合及 BFF 参数校验日志标记。不包含账号凭据、Token、完整规则内容或数据库备份。
- [非法编码查询页面](./assets/invalid-code-query-before.png)：只保留页面内容区，显示输入和空列表/加载失败状态，无账号信息。
- 真实历史观察：详情/编辑回退到审计日志，前进仅恢复列表；“返回列表”后编码过滤为空。独立离开保护脚本完成：取消离开保留草稿，同意离开到审计日志，Candidate Tab 回退同样到审计日志，最后退出，Risk 命令提交计数为 0。
- 初诊浏览器脚本在上述主要观察完成后，等待再次出现详情按钮时超时；该脚本没有记为完整通过。离开保护和 Candidate Tab 用单独脚本完成核对，不能把本阶段观察称为新功能或全量 UI 验收通过。
- 数据快照：23 个版本对应 21 个规则集；发布仅有 TEST 的 2 条 DRAFT、7 条 VALIDATING，没有 ACTIVE。删除及使用保护尚未实现，不能从零 ACTIVE 推导其正确性。

本次读取的 Web/BFF/Risk 镜像与当前台账第 15 节所列镜像一致。本阶段没有更换任何镜像；持续部署状态只由台账维护。

阶段 2 文档变更后，重新完成所有本需求 Markdown 相对链接、行尾空白及脱敏证据结构检查；`git diff --check` 通过。根仓库要求的 `npm --prefix requirements-site test` 退出码为 0，构建及 136/136 项内容测试通过，0 失败/跳过；日志为 `/tmp/risk-rule-list-stage2/requirements-test.log`。这些是文档门禁，不证明业务故障已修复。

Risk 的 `DecisionRuleSetServiceImpl.java` 和 `DecisionRuleSetManagementController.java` 在开始时有未提交修改，核对为 import 整理，无方法行为变化。本阶段保留原样；Web、BFF、Platform 和 Gateway 没有本阶段业务修改。本阶段仅更新本需求文档及脱敏附件，临时诊断脚本保留在 `/tmp/risk-rule-list-stage2/`，未放入业务仓库。

### 阶段 3 文档与 SQL 草稿检查

本阶段只检查规范、设计、重命名清单与 SQL 文件的一致性，不运行 SQL 或业务命令。Java 源码、应用镜像和数据库均没有本阶段变更；既有 Risk import 整理的两份源码 SHA-256 与阶段开始时一致，其他四个受检查应用仓库工作区仍干净。

- 检查 8 份 Markdown 的 218 个相对文件链接、行尾空白、全部本需求 JSON 与接口 JSON 示例，通过。26 项重命名的原类型存在、目标类型清单无重名和协议版本后缀。
- SQL 静态核对：Risk 根表 12 列 / 5 个索引、完整字段注释，无新增 FK/CHECK/AUTO_INCREMENT；Platform 6 张插入目标表及字段与当前 Schema 一致，共 16 行插入和一次 Role CAS 更新。另核对 manifest 的类型、身份、覆盖范围和停止条件；此检查没有经 MySQL 执行，不是 SQL 运行通过证明。
- `git diff --check` 通过。`npm --prefix requirements-site test` 退出码 0，构建及 136/136 项内容测试通过，0 失败/取消/跳过。日志为 `/tmp/risk-rule-list-stage3/requirements-test.log`，静态核对摘要为 `/tmp/risk-rule-list-stage3/static-checks.json`。这些门禁不表示业务修复、数据库变更或新页面已完成。

## 3. 部署、SQL 与提交

本轮 local Risk SQL 已获单独授权并执行；用户随后明确“发布”，授权按阶段 6 发布包执行本机 mor-test 的两库 SQL、三个制品及限定验收。阶段 7 已完成，见部署报告；用户随后授权本地提交，提交记录见阶段 8，推送未执行。上一项配置交付不作为本轮动作授权。

当前下一确认点：提交及归档后，由用户确认本需求结束。下文阶段 4–6 的确认点及未执行描述均为当时的历史记录。

### 2026-09-11 阶段 4 开始

用户确认方案并进入研发。先将真实 HTTP 的多版本列表断言改为一集一行，在原实现上运行失败：expected 1 but was 2，1 项测试、1 failure、0 errors。日志 `/tmp/risk-rule-list-stage4/risk-list-red.log`；使用 local 13306 的独立测试数据并按既有清理流程释放，无 DDL 或初始化 DML。既有两个 Risk 源码 import 整理保留，并在其上增量实现。


### 2026-09-11 local SQL 单独确认与执行

用户对具体环境、文件及备份范围回复“允许备份后应用本机 mor_risk”。据此完成执行前停写/空历史核对、原库备份、Risk DDL/DML 应用及前后比对；[执行证据](./assets/local-sql-applied.json)仅含元数据与校验摘要，不含数据备份或凭据。持久执行状态以 [CURRENT_IMPLEMENTATION 第 16 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)为准。此确认不扩展到 Platform、test、部署或代码提交。


### 2026-09-11 Foundation 必要修复确认

Risk 真实启动回归暴露资源锁 Advisor 过早创建监控注册器。用户对扩展至 `paytech-foundation/paytech-redis-starter` 回复“同意纳入必要修复”。范围及处理方式见 [technique 第 18 节](./technique.md#18-研发中确认的必要补充)；本地安装到开发者 Maven 缓存仅用于集成验证，不代表远程发布或部署。

## 4. 阶段 4 研发交付

### 4.1 代码与工件

- Risk：新增规则集管理根、按当前配置分页、整体删除及三个冲突码；使用所有历史版本的 ACTIVE Release 引用保护删除。保存、冻结、删除、创建 Candidate 共用资源锁，事务内继续校验根/引用和 CAS。删除保留历史及成功命令回放；删除重放不依赖当前 Action 目录或重新解析 AST。现有 API 版本和业务 Hash 保留，25 个 DTO 类型去协议版本后缀，转换器同步规范命名。
- BFF：新增 DELETE 请求/响应和独立权限，PLT-I02 通过后单次转发 Risk；严格区分列表根元数据与历史详情，校验成功删除回执和已知冲突，保持无状态和无重试。
- Web：一集一行、精简列与提示、精确编码校验、详情/编辑/红色删除菜单；列表条件、页码和页面位置进入 URL，保留真实 Back/Forward 和未保存保护。隐藏 Candidate 页面并规范化旧 URL；未知删除结果以原路径、请求体和幂等键重试。
- Foundation：延迟获取 MeterRegistry，避免资源锁 Advisor 提前初始化监控注册器，保留原构造器和资源锁合同；只有指标记录时才解析注册器，监控失败仍不干扰业务。
- Platform：单一新库初始化 DML 同步 16 行删除权限/资源/Route/Header/Guard 数据；与本需求增量一致，Route/按钮保持 DISABLED。本轮只交付 SQL 文件，未执行 Platform SQL。Gateway 无代码修改。
- 模型/文档：Manifest、生成 PDManer/Schema、独立 Oracle、场景逐表映射和 RCO-15 目录同步；旧模型统计保留为历史快照，现行统计由台账维护。

### 4.2 AC 与开发验证映射

| 验收项 | 研发结果及证据 |
|---|---|
| AC-01 | 标题下蓝色提示移除，浏览器截图可见 |
| AC-02 | 编码 trim 后精确查询；非法/超长格式就地提示且不发查询；Web 导航测试及浏览器用例覆盖 |
| AC-03 | 整集删除、任意旧版本实际使用保护、重复/并发请求、失效版本及异常数据回滚；真实 Risk HTTP/MySQL/Redis 用例覆盖 |
| AC-04 | 详情/编辑 URL、过滤/分页、回退与前进、取消离开保留草稿、同意离开后重新加载保存内容；浏览器用例覆盖 |
| AC-05 | 列表移除内容摘要和业务版本列，严格 API 校验及历史内容读取仍保留 |
| AC-06 | 点击/悬停/键盘菜单、详情、直接编辑、红色删除、取消删除无请求；浏览器用例覆盖 |
| AC-07 | Root COUNT/filter/sort/page 先于分页，每集一行当前配置；原多版本 HTTP 失败证据修复后通过，损坏根不会静默消失 |
| AC-08 | Candidate 入口隐藏、旧 URL 落到规则集；后端 Candidate、静态检查与历史回放继续回归 |

这些结果属于本阶段开发检查，不替代下一阶段独立综合验收。测试数量、命令与边界见[验证摘要](./assets/stage4-verification.json)，[浏览器证据](./assets/stage4-browser.json)使用合成 HTTP；[页面及菜单截图](./assets/rule-list-menu-after.png)展示生产页面组件，不是 test 部署截图。

关键开发回归入口：[Risk 删除与并发测试](../../../risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionRuleSetDeletionHttpAutoTest.java)、[BFF 权限与协议测试](../../../operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationBrowserBoundaryAutoTest.java)、[Web 导航测试](../../../operations-console-web/src/risk/riskNavigation.test.ts)、[Foundation 初始化回归](../../../paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/autoconfigure/RedisResourceLockAutoConfigurationAutoTest.java)。开发日志保留在 `/tmp/risk-rule-list-stage4/`。

开发检查发现并修复了四类边界缺陷：根指针损坏时列表静默漏行；删除成功重放重新读取已变化目录；Redis Advisor 提前创建监控注册器；未知删除错误重复显示。完整模型检查另暴露旧 71 表断言及缺失根表示例，已按批准方案同步。失败记录保留为诊断证据，未计为通过。

### 4.3 本阶段限制与下一步

- local 从空历史基线应用 Risk SQL 并验证成功；没有执行非空历史 manifest 的数据库回填，不能推导 test 历史迁移已验证。
- 编码查询已覆盖现有缺陷及合法/非法输入，用户截图中的原始报错编码尚未提供，不能断言该错误只有一个可能原因。
- 未做真实 Gateway → BFF → Risk 的新功能联调；浏览器合成接口、BFF Owner Simulator 和 Risk 真实 HTTP 测试分别说明各自边界。
- 本阶段测试数据已清理，local Root/Version/Release/Command 均为 0，三个终态 Action 目录恢复 ACTIVE，`risk-auto:*` Redis Key 为 0，临时 Web 预览已关闭。SQL 文件校验值未变化，受检查仓库 diff 与相对链接检查通过。
- 本轮未提交、推送、远程发布、应用 Platform/test SQL 或部署；原 test 仍保持上一需求版本。

**下一确认：是否接受阶段 4 研发成果，进入阶段 5“综合验证与本地验收”？** 下一步包括代码审查、按 AC 复核业务/兼容/并发边界和本地联调验收；新的环境写入仍先交付具体方案并单独确认，不包含 test 发布或提交。


## 5. 阶段 5 综合验收交付

用户对进入阶段 5 回复“可以”。据此完成代码复核、按 AC 验收、实际 BFF/Risk 独立进程与本地 MySQL/Redis 联调，并修复删除确认框缺少名称的问题。具体结果、边界、页面截图及脱敏证据见 [acceptance.md](./acceptance.md) 和 [验证摘要](./assets/stage5-verification.json)。

本阶段未修改后端业务代码；Java 和模型门禁沿用阶段 4 的有效结果，新增 38 个跨进程 HTTP 检查、5 个真实业务接口浏览器场景及 11 个合成接口交互场景，修复后 Web 876 项测试和生产构建通过。Platform 使用现有 Simulator，真实 Gateway/Platform 注册和权限仍留在后续环境验收。临时服务与所属合成业务数据已清理。

**下一确认：是否接受阶段 5 验收成果，进入阶段 6“test 发布准备”？** 先准备环境差异、历史 manifest、SQL/备份回滚、制品/配置清单及发布步骤；准备完成后再确认执行，不包含当前直接部署、应用 test/Platform SQL、提交或推送。


## 6. 阶段 6 test 发布准备

用户对明确的阶段 6 确认问题回复“可以”。据此只读核对当前 mor-test，准备 21 条根身份 manifest 及维护/开放/失败关闭 SQL；构建三个应用的专用本地镜像，补齐 Risk test Redis 配置。未应用 SQL、重启/替换容器或更新运行标签。

[发布包](./release.md)列出精确环境、镜像、文件、停止条件、备份恢复、逐步执行和合成验收范围；[机器可读摘要](./assets/stage6-release-package.json)关联校验值与检查证据。已用实际部署的 Platform/Gateway JAR 离线验证新目录，未将当前源码当作已部署代码；原 smoke 的 36/21 限制在发布包中显式说明。

**下一确认：是否按发布包进入阶段 7？** 动作包括本机 mor-test 两库备份和所列 SQL、临时关闭/恢复风控入口、三个已构建镜像的协调部署、限定的合成规则/只读账号验收及清理。不包含提交、推送、远程制品发布或生产环境。

阶段 6 最终检查：部署脚本离线测试 31/31、Requirements Site 构建和内容测试 136/136、7 仓库 diff、SQL/manifest/链接检查通过。结束时重新读取 test 两库元数据及 11 个容器身份/启动时间，均与准备前一致；development 标签不变，Gateway 仍运行旧 36 条目录。无未解决的本阶段准备阻断项；真实 SQL 应用、37/22 入口联调与发布后验收尚未执行。


## 7. 阶段 7 test 部署与环境验收

用户明确回复“发布”，批准按阶段 6 发布包执行；后续“继续”完成剩余核验及交付。两库备份、六步 SQL、三个已批准制品部署及真实公开入口验收已完成，详见 [部署报告](./deployment.md)。最终原 21 个规则集/23 个版本不变，两组合成规则各两版并整体删除，临时只读账号已通过 API 删除；其余八个容器不变。

浏览器出现现有限流后按已保存状态恢复核验，没有重复创建版本；该观察及验证脚本修正如实保留。实际 HTTP/浏览器、SQL/镜像、清理和监控证据位于本需求 assets。全局当前状态仅在台账第 16 节维护。

**下一确认：是否接受 test 结果，按 [待提交清单](./commit-plan.md)进入阶段 8，对 Code-MoR、Risk、Operations BFF、Web、Foundation、Platform 六个仓库做本轮本地提交并归档？** 不推送、不建 PR、不重新部署；保留清单外的改动。


## 8. 阶段 8 本地提交与归档

用户明确“先git提交一下”。据此只提交已验收规则集列表优化、必要 Foundation 修复、Platform 注册与本轮配套根材料；冻结/展示/搜索/复制等新建议没有进入实现。五个组件提交号及根归档定位见 [completion.md](./completion.md) 和 [提交记录](./assets/stage8-commits.json)。

混合文件采用选择性暂存，工作区原内容保留；Risk 暂存候选的 7 个 class 与已部署制品非调试字节码一致。提交没有包含凭据、备份、构建制品或其他需求材料，也没有推送、新建分支/PR、重新部署或再应用 SQL。

本阶段的后续确认仅为是否结束本需求，不自动触发新建议研发。阶段 4–7 的“待确认/未提交”描述是当时记录；当前以本段及状态台账为准。
