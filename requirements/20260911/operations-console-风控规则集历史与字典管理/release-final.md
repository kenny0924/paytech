# RCO-16 test 完整候选发布包

2026-09-12 · 本文保存阶段 6 交付的候选范围与执行方案，取代 [release.md](./release.md) 的旧执行草案。用户随后在理解 Gateway 制品范围后明确要求部署，并要求跳过备份和代理功能验收，由用户自行验证；这项最新指令取代下文备份与合成验收的原计划，不更改镜像或 SQL 内容。授权及执行记录见 [delivery.md](./delivery.md) 和 [部署检查点](./assets/stage7/deployment.json)，实际状态以当前台账为准。旧证据保留在 `assets/stage6/`，候选证据在 `assets/stage6-final/`。

**一句话范围：** 涉及 5 个系统，替换 Risk、BFF、Gateway、Web 四个应用；交付本期全部 14 个 API 及页面，Risk 新增 2 表/21 列、2 个索引和 2 处列注释；Platform 注册 182 行、调整 1 处表注释，并按发布阶段同步 7 条既有绑定、6 条新绑定和 2 个角色版本。

## 1. 必须显式确认的 Gateway 制品范围

当前 test Gateway 镜像没有 Client API 认证链；已通过 1199 项验收的当前源码除本轮动态路由外，还包含以前已提交但未部署的 Client API 及其配套公共过滤器/协议代码。新候选镜像包含该源码，不能把它描述为仅有本轮风控差异。

候选来源为 Gateway `4220da8d3bf40adad4d2ef3d0af58685f20a3d39` 加本轮经验收差异，JAR SHA 为 `3e1c48bcdac6678ba3cec9e3fec48b0cc2253dbcb66fcfa1da869f8b92fbf1c7`。制品级对比列出新增的 11 个 `clientapi` 包类及其他类差异，见 [gateway-source-scope.json](./assets/stage6-final/gateway-source-scope.json)；不能把类字节差异数量等同业务功能数量。

**待批准提议：本轮使用这个已验收的完整 Gateway 制品，同时保持 test 仅开放 Operations。** 实际 test 的全部 37 条既有路由及新增 14 条均为 OPERATIONS，Nginx 两个 API location 均覆盖写入 OPERATIONS，没有 Client Channel 配置。本轮不新增 Client Target/Route、API Credential、Scope 或 Nginx Client 入口，不部署 Commerce 等其他应用。携带代码不等于开放 Client API，但仍属于制品内容扩大，必须明确接受后才能部署。若不接受，需先单独整理不含该代码的 Gateway 源码和回归制品，当前候选包不能直接上线。

这项确认来自 [已确认动态路由方案第 5 节](./gateway-dynamic-routing-design.md#5-研发顺序与验收)要求核对 Gateway 未部署 Client 源码、不得静默全量发布的边界；不改变本期仅配置管理、无规则决策/运行执行的业务范围。

## 2. 精确环境和应用清单

环境为本机 Docker `desktop-linux` / Unix Socket / Compose `mor-test`。公开入口 `http://localhost:28080`；MySQL 为 `mor-test-mysql-1`，宿主 `127.0.0.1:23306`，两个独立库 `mor_risk`、`mor_platform`。不对开发环境 `13306` 或独立验证库应用这些 SQL。

只读基线包含 11 个运行容器的完整 ID、镜像、启动时刻和端口，见 [preflight.json](./assets/stage6-final/preflight.json)。部署前必须再次相等或重新交付差异：Risk 72 表，23 个 DELETED 根/27 个不可变版本，无 ACTIVE Release；Platform 37 Route/22 Resource，管理员 version=9、Viewer version=4，新目录 ID/Code 无冲突。没有自动导入旧原因或标签值。

| 应用 | 现运行 image ID 前缀 | 本轮候选 image ID 前缀 | 内容 |
|---|---|---|---|
| Risk | `46aff45920d3` | `c6a4f44a8cba` | 历史分页、发布引用、两类字典、规则保存校验；保留原配置及删除功能 |
| Operations BFF | `59be81991657` | `1a6c0110750d` | 全部对应接口转发和错误/权限边界；独立管理监听 19201 |
| Gateway | `c5e4a953db15` | `70ed9f3aa481` | 数据库 Target/Route 动态转发、413 修正；含第 1 节待明确接受的既有源码 |
| Operations Web | `83a5141710c3` | `ef04bdbbd75a` | 历史/详情共用只读编辑器、引用图与表格、字典管理及配置选择 |

完整 image ID、专用标签、JAR/HTML/静态文件 SHA 见 [images.json](./assets/stage6-final/images.json)。Risk/Web 复用先前专用制品并复核源码，BFF 以当前源码重新离线 clean package，Gateway 使用阶段 5 干净构建的 JAR。已从未启动的临时检查容器逐字节复核四个镜像，检查容器已删除；[镜像内容证据](./assets/stage6-final/image-content-check.json)。Web 核对的是 Nginx 实际站点目录 `/usr/share/nginx/operations-console` 的全部 18 个文件。

Platform 应用、Nginx、Vault、Nacos、MySQL、Redis、RabbitMQ 七个运行容器保持原身份；Platform 只应用本轮 SQL。BFF 健康检查改为容器内 `127.0.0.1:19201/actuator/health/readiness`，业务注册仍为 18201，管理端口不映射到宿主或 Nacos。镜像和探针必须同步切换。

## 3. SQL、前置与精确版本

| 顺序 | SQL | 预期作用 |
|---|---|---|
| 维护 | [test-maintenance.sql](./db/platform-support-service/test-maintenance.sql) | 关闭 10 个旧 Risk Route/7 个 Resource；7 条原绑定改为 REVOKED，管理员 9→10、Viewer 4→5 |
| Risk 结构 | [Risk ddl.sql](./db/risk-compliance-service/ddl.sql) | 新增 `t_rule_reject_reason`、`t_rule_subject_tag`，共 21 列；两个旧表索引、两处列注释 |
| Platform 注释 | [Platform ddl.sql](./db/platform-support-service/ddl.sql) | 仅调整 `t_gateway_target` 表注释；实查为已知旧注释、10 列/1 行，其他结构不变 |
| Platform 注册 | [Platform dml.sql](./db/platform-support-service/dml.sql) | 插入 182 行：6 Permission、6 REVOKED 绑定、10 DISABLED Resource、14 DISABLED Route、40 Header、22 Query、84 Guard；管理员 10→11 |
| 路由与权限开放 | [test-enable-routes.sql](./db/platform-support-service/test-enable-routes.sql) | 旧 10 条和新 14 条 Route 同事务开放，恢复 7 条原绑定并激活 6 条新绑定；管理员 11→12、Viewer 5→6；全目录 51 Route |
| 页面开放 | [test-expose-resources.sql](./db/platform-support-service/test-expose-resources.sql) | Gateway 已接受完整快照后开放 17 项本轮旧/新 Resource；全目录 32 Resource |

[Risk dml.sql](./db/risk-compliance-service/dml.sql) 无业务种子，不需执行空文件。不得执行全量 Schema、初始化 DML、Bootstrap 或独立开发验证库的 7+1 修正补丁。

五份 test 发布/恢复 SQL 已由本次实际角色/绑定快照重新生成，替代此前主动阻止执行的旧文件；其余权限和此前已撤销绑定不恢复、不删除。管理员六个原绑定、Viewer 一个原读取绑定的最终版本为 3，新六个字典绑定最终版本为 2。版本漂移、缺行、额外有效授权或部分注册均会停止执行。

SQL 必须按最终 Hash、已停止目录/IAM/规则写入的前置人工执行：MySQL batch、`--skip-reconnect`、禁止 `--force`；遇错立即断开，回滚未提交事务并停止后续步骤。DDL 隐式提交，不能承诺跨两个库整体回滚。[实际版本离线推演](./assets/stage6-final/sql-state-check.json)与 21 项边界检查只证明 SQLite 状态/事务，不代替 MySQL 锁或发布验收。

## 4. 备份和部署顺序

四应用切换期间 Operations 页面和 API 会短时不可用。获得具体阶段 7 授权后：

1. 重核 Docker context、11 个容器、当前/候选完整 image ID、源码/SQL/Compose Hash 和角色版本。持有 `test-environment/private/risk-operations-rollout.lock`，暂停目录、IAM 与规则编辑；不运行通用全量部署脚本。
2. 在 `~/Code-MoR-backups/rco16-test-<UTC时间>/` 创建 0700 目录、0600 文件，保存当前四个镜像的完整 ID、恢复标签及 Compose/Nginx 等实际配置。私密材料只留在受控备份，不能写入需求目录或日志。
3. 停止 Web、BFF、Risk，等待在途请求结束并核对业务连接；备份 `mor_risk`、`mor_platform` 完整结构/数据及受影响目录、角色和绑定的有序摘要。检查导出退出码、非空文件、完整结尾和 SHA；备份失败不执行 SQL。
4. 按第 3 节先应用维护 SQL，确认 Platform 和现有 Gateway 均收敛到 27 条非风控路由；再依次应用 Risk DDL、Platform 表注释 DDL、182 行注册。确认旧根/版本/Hash、命令和发布事实不变，字典表为空，新路由/资源/绑定仍禁用。
5. 只把审核清单中的精确镜像设为相应 `development` 标签。按 **Risk → BFF → Gateway → Web** 逐项替换和健康核对；每次只操作一个 service，使用 `up --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 240`。BFF 同步使用新探针，不改七个保留容器。
6. 确认实际 JAR/API Hash、唯一 Nacos 实例和业务注册端口；BFF 业务端口不得暴露 Actuator，管理回环端口健康；Gateway 已接受当前 27 条维护快照，JWT、Origin、Guard、可信 Header 及现有 IAM 能力仍成立。Client Target/Route/入口仍为 0。
7. 同事务开放路由与绑定，等待 Gateway 明确接受 51 条完整 Snapshot（`SUCCESS/NONE`），再开放资源并确认 32 项菜单/按钮、角色 12/6。不能用 Last Valid 旧快照冒充本次开放成功。
8. 完成下述真实入口验收和约定清理；复核七个保留容器身份、历史数据不变量，归档最终 SQL、运行镜像、静态资源和验收证据，再交用户确认。

## 5. 真实 test 验收及合成数据范围

- 通过 `http://localhost:28080` 的真实 Nginx → Gateway → BFF → Risk/Platform 链路登录，不使用伪造可信头或 Simulator。Token 仅在正常会话内持有，登录/退出产生的 Session 与审计按现有规则保留。
- 验收本轮全部功能：单行规则集列表与编码查询、操作菜单、编辑/只读详情、浏览器回退/刷新、历史指定版本、引用图/明细、拒绝原因与标签字典及规则选择/校验；核对桌面/窄屏、权限、错误与静态资源 Hash。
- 最多创建 2 条拒绝原因、2 条主体标签、2 组规则集，每组最多 3 个版本；使用唯一 `RCO16_TEST_<后缀>` 合成编码。覆盖六种字典写入、CAS 冲突、停用旧值保留/新选择拒绝、当前/历史引用。不得修改原 23 个已删除根及其历史。
- 引用图如需非空发布关系，最多创建 1 个合成 TEST DRAFT Candidate，引用本轮纯终态冻结版本；不激活、不执行规则评估，不对附加动作声称可运行。没有 Candidate 删除 API 时保留该 DRAFT 历史。
- 用既有只读身份确认六种字典写入被拒绝、管理员和只读角色详情仍可正常查询；若无可用只读身份，最多通过已有 Operations API 创建 1 名临时 Viewer，验收后删除账号、保留正常审计。匿名请求必须拒绝。
- 结束时通过业务 API 删除本轮合成规则集、停用合成字典。字典编码、历史版本、命令、Candidate 和审计继续保留，不在 test 物理清表。登记所有合成 ID 及预期残留。

这些 test 合成写入及清理必须随发布包一并获得授权。实际 ACTIVE 使用保护沿用已完成的本地真实用例，test 当前没有 ACTIVE Release，不为验收伪造运行事实。

## 6. 失败处置与恢复

- **Risk DDL/新目录注册均未开始：** 用 [test-restore-before-migration.sql](./db/platform-support-service/test-restore-before-migration.sql) 恢复原 10 Route/7 Resource 和快照原 ACTIVE 绑定；管理员将为 11、Viewer 为 6，不重置版本。按保存的镜像 ID 与旧 BFF 探针恢复应用，验证原 37/22 入口。
- **Risk DDL或新注册已经提交：** 保持维护态并保留新结构和数据，不删新表、不删注册记录、不整库回灌覆盖其他事实。先定位已提交步骤，再给出前滚修复。
- **新服务或快照失败：** [test-close-entry.sql](./db/platform-support-service/test-close-entry.sql) 只接受已知阶段，关闭本轮路由/资源及有效绑定，稳定保留 27 条非风控目录；重复关闭不增加版本。正常开放后关闭的管理员/Viewer 版本为 13/7。旧 Gateway 的真实编译器已确认可接受 37/27/27 三种恢复目录，可在确认当前目录为 27 后单独恢复旧镜像。
- **新版本已经产生写入：** 优先关闭入口后修复前滚，不让旧 Risk 绕过新字典校验，不整库恢复覆盖新事实。BFF 回退必须同步旧探针；Platform 表注释如需恢复只使用已核对的旧注释。超出以上范围时提交具体差异供批准。

## 7. 证据与确认点

- [阶段 5 验收](./gateway-acceptance.md)：Risk 186、BFF 1041、Web 883、Gateway 1199、Platform 386 项对应源码证据，原门槛保持；各套件的执行时点/环境以对应报告为准。
- 本阶段 BFF 重新离线 clean package，先执行应用双监听与字典浏览器边界 **24 项**，随后因依赖 Hash 与早期包不同，补跑完整 `verify` **1041/1041**，0 失败/错误/跳过；最终测试 JAR 与候选镜像一致。其余系统全量沿用阶段 5 的对应证据。
- [BFF 依赖差异](./assets/stage6-final/bff-dependency-check.json)：当前三个 Foundation JAR 与早期候选 Hash 不同，MVC Starter 内部文件完全一致；HTTP Client Support 和 Problem Profile 有类文件差异，均匹配当前本地 Foundation 构建输出，已纳入上述全量回归及精确 Hash 清单。本轮未修改 Foundation 源码或发布 Nexus。
- [制品兼容](./assets/stage6-final/compatibility.json)：真实 test 目录、部署中 Platform 和新 Gateway 编译器接受 37/27/27/51；部署中旧 Gateway 接受 37/27/27 恢复目录，全部离线投影，无 SQL 写入。
- [发布清单](./assets/stage6-final/release-package.json)汇总精确 Hash、镜像、前置、证据和未执行项；[最终只读复核](./assets/stage6-final/final-checks.json)证明 test 运行容器及基线保持。

**原阶段 6 确认点（已获后续回复）：** 用户已接受解释后的完整制品并明确要求部署，随后要求跳过备份和代理功能验收。实际执行按文首修订和 [阶段 7 记录](./delivery.md#12-阶段-7按用户简化指令部署)进行。BFF 最终使用 [探针修正镜像](./assets/stage7/bff-probe-image.json)，其业务 JAR 与本包一致；其他三镜像及 SQL Hash 保持本包内容。Git 提交、推送、Nexus 发布和生产部署不在此包授权内。
