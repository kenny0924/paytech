# RCO-16 test 发布准备历史草案

**本文已由 [release-final.md](./release-final.md) 取代，只保留原阶段 6 排障与准备历史，不作为执行入口。**

本文件是原阶段 6 的可审阅草案，**当前尚未达到部署门槛**。实际 test Gateway 拒绝字典路由，用户已确认动态路由补改范围；待补改综合验收完成后，再补齐 Gateway/BFF 制品并交付完整发布包。确认过程见 [delivery.md 第 7–8 节](./delivery.md#7-阶段-6-准备与-gateway-缺口2026-09-11)，实际状态由 [CURRENT_IMPLEMENTATION 第 17 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#17-operations-规则历史与业务字典2026-09-11)维护。

用户已确认 Route/Target 完整数据库驱动及 BFF 管理端口隔离范围，见[范围决定](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)和[阶段 3 修订方案](./gateway-dynamic-routing-design.md)。本文下述单路径增补及制品顺序仅保留为原排障草案，不能直接据此部署；补改研发/验收后须更新完整发布包，重建 BFF、补齐 Gateway 镜像和管理端口探针，并纳入一条 Platform 表注释 DDL。

**2026-09-12 权限时序修正：** 用户已确认并完成独立本机补丁，详见[修正记录](./pending-registration-permissions.md)。旧预检缺少原角色/绑定版本；五份旧 test SQL 已加入执行前失败门禁。阶段 6 须重新采集实际受影响角色/绑定、重生成 SQL 并复核发布包；不能按本文原环境快照中的角色 9→10 执行。当前只验证了生成器的 21 个离线状态场景，未访问或修改 test。

一句话规模：业务新增 14 API（8 查询、6 写入）、修改 1 个原保存校验；Risk 新增 2 表/21 列、2 个旧表索引和 2 处列注释；Platform 插入 182 条注册并更新 1 个角色。新发现的 Gateway 补改不增加业务接口或数据库结构。

## 1. 原阶段 6 环境与版本快照（历史，须重新核对）

目标为当前 Mac，Docker `desktop-linux`、本机 Unix Socket、Compose `mor-test`。公开入口 `http://localhost:28080`；数据库为 `mor-test-mysql-1` 中分别独立的 `mor_risk`、`mor_platform`，宿主映射 `127.0.0.1:23306`；Redis 为 `mor-test-redis-1`，宿主 `127.0.0.1:26379`。不使用 local 13306/16379。

[只读基线](./assets/stage6/preflight.json)记录 11 个容器完整 ID、image ID、启动时间及公开端口；[旧数据摘要](./assets/stage6/legacy-summary.json)仅统计现有引用，不复制拒绝文案或标签值。

- Risk 为 72 表，两张字典表及两个反查索引不存在。
- 23 个规则集均为 DELETED，共保留 27 个不可变版本；当前可配置规则为 0。不能把它们当作可编辑存量回填，也不恢复已删除根。
- 历史中有 1 个 DENY 原因编码和 3 个标签编码（5 次 TAG 引用），不自动导入为 ACTIVE 字典或重算 Hash。
- TEST Release 为 2 DRAFT、7 VALIDATING，无 ACTIVE；Evaluation、Action Effect 均为 0。
- Platform 当前有 37 条 Route、22 个 ACTIVE Operations Resource；管理员角色版本为 9。原七个风控 Resource 的版本为 6（前六个）、2（删除按钮）；各旧 Route 精确版本见基线。
- 本轮 182 条插入的所有 ID 和业务编码均无冲突。初始字典为空，运营后续通过管理页面维护实际业务值。

## 2. 发布范围与 Gateway 必要补改

原定只替换 Risk、BFF、Web，Platform 仅修改目录数据。实际部署 JAR 的离线结果：

| 场景 | 已部署 Platform | 已部署 Gateway |
|---|---:|---:|
| 当前目录 | 37 条通过 | 37 条通过 |
| 关闭旧十条风控路由 | 27 条通过 | 27 条通过 |
| 新注册保持禁用 | 27 条通过 | 27 条通过 |
| 恢复旧风控并启用新十四条 | 51 条通过 | 拒绝字典下游路径，完整快照无效 |

具体失败：`operations-risk-rejection-reason-create` 的下游路径未获 `ApprovedTargetRegistry` 批准。启用 SQL 本身不能绕过这个编译门槛；不应通过改路径、放宽全部路径、跳过 Snapshot 校验来规避。

拟在原 Operations BFF Target 上仅增加 `/decision-dictionaries/v1/`，保持 Target、Channel、Discovery 和既有路径。使用实际部署 Gateway 编译器、仅在内存构造该增量后，37/27/27/51 四个快照全部通过；v2 和相似路径根仍被拒绝。[兼容证据](./assets/stage6/compatibility.json)。这是方案可行性证据，**不是 Gateway 已完成研发或部署**。

另外，主工作区 Gateway `4220da8` 的默认白名单不含 test 已有的 `/decision-rule-sets/`、`/decision-releases/`。应先核对部署来源和未发布改动，建立包含既有 Operations 能力的可审阅源码基线，不能直接全量重建 main 把其他尚未发布能力一并带入。该段描述阶段 6 首次核对时的基线；补改实现和验证见当前台账。[差异摘要](./assets/stage6/gateway-scope-gap.json)。

若用户确认必要补改，发布应用将由 3 个增为 4 个：Risk、BFF、Web、Gateway。Gateway 是单实例入口，重建会使 Operations 的全部 API 短暂不可用；要将这段中断和精确新镜像纳入后续部署确认。Platform、Nginx、Vault、Nacos、MySQL、Redis、RabbitMQ 共七个容器不替换。

## 2.1 已完成的三个本地镜像

| 应用 | test 当前 image ID 前缀 | 新 image ID 前缀 |
|---|---|---|
| risk-service | `46aff45920d3` | `c6a4f44a8cba` |
| operations-console-bff | `59be81991657` | `8005c98cee03` |
| operations-console-web | `83a5141710c3` | `ef04bdbbd75a` |

完整 ID、JAR/API/静态资源 Hash 见[镜像清单](./assets/stage6/images.json)和[发布包状态](./assets/stage6/release-package.json)。标签统一使用 `rco16-history-dictionaries-20260911`，只存在本机，未切换 `development`，未启动容器或推送。Gateway 镜像尚未准备，当前三个镜像不能单独构成可上线的完整发布包。

从 97 个仍匹配阶段 5 Hash 的源码进行 JDK25/Maven Wrapper 离线 clean 打包；清除旧打包输出后核对 Risk/BFF 内嵌完全相同的 Risk API。BFF clean package 全量 1040 项通过；最终统一 API 字节后的字典边界 16 项再次通过。SQL 状态门禁 16 项通过。其余阶段 5 证据仍按原边界引用；没有重复声称本阶段执行了 Risk 全量或真实 test 业务验收。

## 3. SQL 工件与重生成要求

这些 test 专用 SQL 均未执行。五份 `test-*.sql` 旧文件已主动阻止执行，须先用包含实际绑定/角色的只读预检重生成并交付审核。之后仍须核对 Hash、环境、备份与前置，使用 MySQL batch / `--skip-reconnect` 执行，禁止 `--force`；出错断开连接并停止后续步骤。

| 顺序 | 文件 | 内容 |
|---|---|---|
| 维护 | [test-maintenance.sql](./db/platform-support-service/test-maintenance.sql) | 重生成后校验实际旧状态/版本；关闭十条旧 Route、七个 Resource，同事务撤销因此失去全部 Operations 入口的原 ACTIVE 绑定，递增受影响角色版本 |
| Risk | [ddl.sql](./db/risk-compliance-service/ddl.sql) | 新增两张字典表/21 列；增加两个反查索引、调整两个旧字段注释 |
| Risk DML | [dml.sql](./db/risk-compliance-service/dml.sql) | 仅说明，无业务种子、回填或数据写入，无需执行空文件 |
| Platform | [dml.sql](./db/platform-support-service/dml.sql) | 182 条插入：14 Route、40 Header、22 Query、84 Guard、6 Permission、6 个 REVOKED 管理员绑定、10 Resource；执行时管理员版本 CAS +1，新 Route/Resource 均 DISABLED |
| 路由 | [test-enable-routes.sql](./db/platform-support-service/test-enable-routes.sql) | 四个应用正确健康后，恢复旧十条并启用新十四条，同时恢复快照内原绑定及激活六个新绑定，递增受影响角色版本；目录应为 51 条 |
| 页面 | [test-expose-resources.sql](./db/platform-support-service/test-expose-resources.sql) | Gateway 确认接受完整 51 条新 Snapshot 后，恢复七项旧资源、启用十项新资源；ACTIVE Resource 应为 32 |
| 失败关闭 | [test-close-entry.sql](./db/platform-support-service/test-close-entry.sql) | 只接受已知发布状态，关闭本轮风控 Route/Resource 并撤销对应绑定；重复关闭不增加版本，不撤销成功业务事实 |
| 迁移前撤回 | [test-restore-before-migration.sql](./db/platform-support-service/test-restore-before-migration.sql) | 仅 Risk DDL/Platform 注册均未开始时，恢复原十条路由、七项资源和快照中的原 ACTIVE 绑定；不恢复此前已撤销授权 |

DDL 有隐式提交，不能整体事务回滚。新表完整模型、类型、默认值、NULL、注释和索引在 Stage 4/5 已于 local 验证；test 执行后仍须对照完整 Schema 复核。原 72 表业务数据、旧 Hash、命令及发布事实必须保持不变；旧表结构只能出现两个批准的索引与两处注释差异。禁止重跑全量 Schema、初始化 DML 或 Bootstrap。

旧 16 项[检查](./assets/stage6/sql-state-check.json)保持历史证据。修正后生成器通过 [21 项离线检查](./assets/gateway-acceptance/permission-release-sql-check.json)，包含 Viewer、自定义角色、原撤销绑定保留及角色/绑定漂移；不代替真实 MySQL 事务/锁或 test 验收。

## 4. 备份和执行顺序草案

Gateway 补改及完整制品完成、获得阶段 7 明确授权后，才允许执行以下步骤：

1. 重核 Docker context、MySQL 容器完整 ID、所有当前/新 image ID、最终源码清单、SQL/配置 Hash，以及受影响角色、原 ACTIVE 绑定和版本。若基线漂移，先更新审核包；不能临时替换 SQL 版本门禁。持有既有 `test-environment/private/risk-operations-rollout.lock`，暂停目录、IAM 管理写入与规则编辑。
2. 在 `~/Code-MoR-backups/rco16-test-<UTC时间>/` 建立 0700 目录，备份文件为 0600。保留四个旧镜像的完整 ID 和恢复标签，以及受影响 Compose/静态配置。
3. 停止 Web、BFF、Risk，确认 Risk 业务连接为 0；完整备份 `mor_risk`、`mor_platform` 的结构和数据，并保存受影响目录行和有序业务摘要。使用实际 test 容器内部凭据，不打印密码；备份成功须检查退出码、非空大小、完整结尾和 SHA-256。备份失败不执行任何 SQL。
4. 应用维护 SQL，确认 Platform 和现有 Gateway 收敛到 27 条非风控路由，风控菜单隐藏。原规则写入、冻结、删除、Candidate 入口全部关闭。
5. 应用 Risk DDL，核对两张空字典表、21 列、两个反查索引、两处注释及原业务数据不变。应用 Platform 注册 DML，核对精确 182 行、角色版本等于最终审核清单、六个新绑定为 REVOKED、14 Route/10 Resource 仍禁用；不授予只读角色写权限。
6. 从受审的完整 image ID 切换各自 `development` 标签，按 Risk → BFF → Web → Gateway 逐项部署并健康验证。具体 Gateway 制品和时序在补改后复核。只用指定 service 的 `up --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 240`，不运行全量部署脚本，不重建基础设施。
7. 确认 Risk/BFF 内嵌同一 Risk API、唯一 Nacos 实例、Redis 锁与 JVM 指标、Gateway 新编译器和当前 27 条 Snapshot。现有信任头、私网 HTTP、JWT、Guard、Origin 和转发合同保持原部署能力。
8. 应用路由及绑定同事务开放 SQL，核对管理员/只读/自定义角色的精确权限和版本，等待 Gateway 接受 51 条的完整精确 Snapshot、`SUCCESS/NONE`，不能停留在 Last Valid 旧快照。再开放资源，ACTIVE Operations Resource 为 32；重新登录或加载当前权限菜单。
9. 通过真实公开入口验收新页面及 API，核对 HTML/实际静态资源 Hash，随后按下节清理合成数据。核对七个保留容器身份不变，归档 SQL、镜像、数据库不变量与业务验收证据。

通用旧 smoke 的 36/21 或上一需求 37/22 闭合目录不再适用；新发布必须使用精确的 51/32 清单和真实 Snapshot/公开入口检查。不能为了让旧脚本通过而删除新目录。

## 5. 真实入口验收与合成数据范围

- 使用 `http://localhost:28080` 的真实 Nginx → Gateway → BFF → Risk/Platform 链路，不使用 Simulator 或伪造可信头。登录、刷新、退出正常产生 Session/安全审计；Token 只在内存中持有。
- 管理员验证历史列表、指定版本只读编辑器、刷新/回退保持、发布引用图及表格、两类字典维护及原因/标签选择。检查桌面/窄屏、键盘和实际静态资源属于新制品。
- 最多创建两条拒绝原因、两条主体标签、两组规则集，每组最多三个版本，均使用唯一 `RCO16_TEST_<后缀>` 合成编码和不含敏感值的内容。覆盖六种字典写入、CAS 冲突、停用旧值保留/新选择拒绝、字典当前/历史引用及规则集历史查看。不得修改原 23 个已删除根或其版本/命令。
- 若需要图的非空事实，最多创建一个合成 TEST DRAFT Candidate 引用本轮纯终态冻结版本；不激活、不执行评估，不对含附加动作的版本声称可冻结。没有 Candidate 删除 API时保留该 DRAFT 及其历史，只删除本轮规则集管理根。
- 当前没有 ACTIVE 发布；使用保护的运行分支沿用 Stage 5 本地真实验证，不在 test 伪造 ACTIVE/运行期事实。
- 用既有只读身份验证六种写入均被拒绝；若不存在，最多通过已有 Operations API 创建一名临时账号、绑定已有只读角色，验收后删除账号并保留正常审计。匿名请求须拒绝。
- 结束时通过业务 API 删除合成规则集、停用合成字典。字典编码永久占用；根、版本、命令、Candidate 和审计历史按业务合同保留，**不做 test 物理清表**。记录本轮所有合成 ID 与预期残留。

这段合成写入及清理范围目前仅是发布草案，需与最终制品一并取得阶段 7 授权。

## 6. 恢复边界

- **任何业务迁移前：** 可恢复已记录的旧应用镜像；若仅维护 SQL 已生效，按迁移前撤回 SQL 恢复原 37 条 Route/22 个 Resource，再验证旧入口。
- **Risk DDL 或新注册已经提交：** 维持维护状态，保留备份和故障现场；不自动删表或删除注册记录。先定位已完成检查点，再给出前滚修复。
- **新 Snapshot/应用失败：** 执行失败关闭 SQL，保持 27 条非风控目录；停止三项业务应用。若 Gateway 新镜像尚未放行新路由且其原版本能接受已恢复的 27 条目录，可按最终批准方案单独恢复旧 Gateway，保护其他 Operations API。
- **新版本已有字典/规则命令写入：** 关闭风控入口并保留现场，优先修复后前滚；不能把不执行字典资格校验的旧 Risk 直接放回，也不能整库恢复覆盖新事实或其他 Platform IAM/审计操作。数据覆盖恢复需另交精确差异和授权。

## 7. 当前确认点

动态路由补改及权限时序修正均已获准。当前以 [Gateway 本地验收](./gateway-acceptance.md)作为阶段 5 交付；用户确认后再返回阶段 6，只读刷新 test 基线、重生成精确 SQL、核对未部署源码及准备完整制品。最终发布包另行取得 test SQL/部署授权；Git 提交、推送不在此次范围内。
