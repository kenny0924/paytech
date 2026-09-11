# 规则集列表优化：test 发布包

阶段 6 交付，2026-09-11。本文定义待授权的阶段 7 操作，不表示已经应用 SQL 或部署。阶段确认见 [delivery.md](./delivery.md)，持续状态见 [CURRENT_IMPLEMENTATION 第 16 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)。

## 1. 目标环境和发布范围

目标是当前 Mac 的 Docker `desktop-linux`（本机 Unix Socket）、Compose project `mor-test`，公开入口为 `http://localhost:28080`。数据库是 `mor-test-mysql-1` 内的 `mor_risk`、`mor_platform`，宿主映射为 `127.0.0.1:23306`。Redis 复用 `mor-test-redis-1:6379`，宿主映射 `127.0.0.1:26379`；不使用 local 的 13306/16379。

只替换 `risk-service`、`operations-console-bff`、`operations-console-web`，顺序固定 Risk → BFF → Web。Platform、Gateway、Nginx、Vault、Nacos、MySQL、Redis、RabbitMQ 保持原容器和镜像。Foundation 修复已包含在 Risk JAR 中，不单独部署服务或发布 Nexus。

这是协调升级：原列表 JSON 已改变，旧 BFF 与新 Risk 不可混用。维护期间停止上述三个应用，并暂时隐藏六个风控 Resource、关闭九条现有风控 Route；Operations 页面及 API 会短暂不可用。预计预留 10–20 分钟维护窗口，任一前置或健康检查失败即暂停，不能为赶时间继续开放。

## 2. 已构建制品与配置

| 应用 | 当前运行 image ID 前缀 | 本次待部署 image ID 前缀 | 本地专用标签 |
|---|---|---|---|
| Risk | `55e5a50e17a3` | `46aff45920d3` | `mor-test/risk-service:risk-rule-set-list-20260911` |
| BFF | `ca802c008aa4` | `59be81991657` | `mor-test/operations-console-bff:risk-rule-set-list-20260911` |
| Web | `b9f304f73e71` | `83a5141710c3` | `mor-test/operations-console-web:risk-rule-set-list-20260911` |

操作时必须使用 [stage6-release-package.json](./assets/stage6-release-package.json) 中的完整 image ID，并重新核对；表中前缀只便于阅读。新镜像尚未替换 `development` 标签或运行容器。发布获准后，先给三个旧 image ID 加本轮恢复标签，再将完整新 image ID 标记为各自 `development`，逐个 `up --no-deps --no-build --pull never --force-recreate --wait`。

构建复用阶段 4/5 验证过的 JAR 和 Web dist，未重新编译未评审业务代码。Risk/BFF 内嵌的 Risk API SHA-256 相同；Risk 内嵌 Redis Starter 与已验证 Foundation 修复的 JAR 相同。基础镜像使用已批准且本地缓存的固定 digest，构建禁止网络；未推送任何镜像。完整 JAR、镜像、静态入口及验证证据见发布包。

配置仅在 [compose.applications.yaml](../../../test-environment/compose.applications.yaml) 的 Risk 下增加：

```yaml
RISK_REDIS_HOST: redis
RISK_REDIS_PORT: "6379"
RISK_REDIS_PASSWORD: ${PAYTECH_REDIS_PASSWORD:?prepare test secrets}
```

并添加现有 Redis 的 healthy 依赖。密码只由原 `.env.test` 安全注入，不复制到文档、镜像或 Nacos。Risk 的协调开关、500ms 等待、30s 租约、2s Redis 连接/命令超时已在 JAR；应用 Snowflake 节点保持 `0/25`，网络/端口不变。`compose.sh config --quiet` 已通过。

## 3. 数据库与曝光文件

只读基线：Risk 71 表、23 个版本对应 21 个规则集，没有 `t_rule_set`；TEST Release 为 2 个 DRAFT、7 个 VALIDATING，没有 ACTIVE；运行期 Evaluation/Action Effect 均为 0。Platform 当前为 36 条 Route、21 个 ACTIVE Operations Resource；本次预留 ID/业务编码没有冲突。原六个 Resource 均为 ACTIVE/version 4，管理员 Role 为 ACTIVE/version 8。

| 顺序 | 文件 | 具体作用 |
|---|---|---|
| 维护 | [test-maintenance.sql](./db/platform-support-service/test-maintenance.sql) | 精确核对基线版本，隐藏原六个风控 Resource，并关闭九条旧风控 Route；各状态变化推进一次版本。 |
| Risk DDL | [ddl.sql](./db/risk-compliance-service/ddl.sql) | 只新增 `t_rule_set`，12 列、主键和四个其他索引，不改原 71 表。 |
| Risk DML | [test.sql](./db/risk-compliance-service/test.sql) + [dml.sql](./db/risk-compliance-service/dml.sql) | 使用 [test-manifest.json](./db/risk-compliance-service/test-manifest.json) 回填 21 个根，管理版本均为 1。 |
| Platform 注册 | [dml.sql](./db/platform-support-service/dml.sql) | 六张表新增 16 行；删除 Permission/管理员授权 ACTIVE，Role 8→9，按钮和 Route 先 DISABLED。 |
| 路由开放 | [test-enable-risk-routes.sql](./db/platform-support-service/test-enable-risk-routes.sql) | 三个新应用就绪后，在一个事务恢复九条旧风控 Route 并启用一条 DELETE Route。 |
| 页面开放 | [test-expose-resources.sql](./db/platform-support-service/test-expose-resources.sql) | 确认完整新 Snapshot 已被 Gateway 接受后，恢复六个旧 Resource、启用新删除按钮。 |
| 失败关闭 | [test-close-entry.sql](./db/platform-support-service/test-close-entry.sql) | 只接受本轮已知状态/版本，关闭全部风控 Resource 和十条风控 Route；保留权限、历史和业务事实。 |
| 迁移前撤回 | [test-restore-before-migration.sql](./db/platform-support-service/test-restore-before-migration.sql) | 仅在尚未执行任何 Risk DDL/DML、尚未插入新 Platform 注册时恢复原六个 Resource 和九条 Route。 |

这些 test 专用文件均未执行。此前已经应用的 local DDL/DML 校验值保持不变；不能把 `local.sql` 的空 manifest 用于 test，也不能重跑服务全量 Schema/初始化数据或 Bootstrap。

21 个新根身份由 Foundation `SnowflakeIdGenerator` 的一次性离线节点 `31/31` 生成，与当前应用节点分离；已核对目标库 71 个 BIGINT 主键列均无冲突。使用与 DML 相同的 INSERT 投影做只读 SELECT，得到 21 行 CONFIGURABLE、管理版本 1、最高版本指针及首版创建时间。这是非空回填的只读预检，真正 INSERT 和前后不变量核验留到阶段 7。

## 4. 执行前置、备份及发布顺序

每一步成功后才进入下一步。所有 SQL 使用 UTF-8、MySQL batch、`--skip-reconnect`，禁止 `--force`；失败必须断开会话回滚。SQL 不进入 Compose 或应用启动。

1. 重新确认 Docker context、MySQL 容器完整 ID、三应用旧 image ID 及其他八个保留容器与发布包一致；确认没有其他部署进程、控制面操作者或自动写入。核对 SQL、manifest、JAR、镜像及配置 SHA-256，拒绝漂移。持有既有 `test-environment/private/risk-operations-rollout.lock` 至发布或失败关闭结束，禁止删除锁文件绕过竞争。
2. 保存旧镜像标签和 Compose 配置，停止 Web、BFF、Risk；不停止其他服务。核对 Risk 业务连接为 0。重新读取历史身份、23 个版本的 ID/格式/Hash/状态/时间、发布及命令摘要，必须与预检基线一致；若用户在准备后新增或编辑过规则，先更新并重新审阅 manifest/发布包，不能在现场临时生成根 ID。
3. 在 `~/Code-MoR-backups/risk-rule-set-list-test-<UTC时间>/` 创建权限 0700 目录，备份文件权限 0600。分别备份完整 `mor_risk` 和 `mor_platform`（结构、数据、trigger/routine/event），使用实际 test MySQL 容器及其既有凭据，不打印密码；同时保存受影响 Platform 行、镜像 ID、配置和操作前只读摘要。备份完成后核对退出码、非空大小、完整结尾和 SHA-256。备份失败时不运行任何迁移。
4. 应用维护 SQL，等待 Platform 公共 Snapshot 与 Gateway 指标确认 27 条 Operations Route，风控入口已关闭。期间三应用仍停止。由只读 dump/摘要保留 Risk 原 71 表的完整基线，供 DML 后逐表结构/数据比较。
5. 依次应用 Risk DDL、带 test manifest 的 DML。MySQL 的 `SOURCE dml.sql` 必须能在客户端所在目录解析；通过 Docker stdin 执行时，由操作员在宿主把 `test.sql` 的唯一 `SOURCE dml.sql;` 展开为同目录 DML 文本后送入同一会话，不让容器猜宿主路径。再次校验文件 SHA 后才执行。DDL 隐式提交，不能宣称两个文件可整体事务回滚。
6. 核对根 21 行、唯一身份/编码、当前指针、首版时间、CONFIGURABLE/version 1、删除字段全空；原 71 表的有序 schema/data dump 与停写基线一致，包括版本 Hash、Release、检查、幂等及执行表。任何差异均停止。随后应用 Platform 注册 DML，核对 16 行、Role 9、Route/按钮禁用、Header/Guard 各 6、Query 为 0；旧角色和权限不另增授权。
7. 给旧镜像加恢复标签，将已审阅的新完整 image ID 指向各自 `development` 标签，按 Risk → BFF → Web 逐个替换并等待健康。命令只列这三个 service，带 `--no-deps --no-build --pull never --force-recreate --wait --wait-timeout 240`。不运行会重建 Platform/Gateway/Nacos 等服务的通用全量 build/deploy 脚本。
8. 核对 Risk 数据库、Redis 和 Nacos 配置，确认三应用实际 image ID 正确、唯一 Risk 实例 `0/25`、readiness 通过，JVM 内存及资源锁指标存在；BFF 发现的是新 Risk。健康并不替代业务验收。
9. 应用路由开放 SQL，等待完整 Platform Snapshot 与 Gateway 确认 **37 条**、`SUCCESS/NONE`、不使用 Last Valid fallback；十条风控 Route 的代码、方法、路径及权限与已评审目录一致。再应用页面开放 SQL：Operations ACTIVE Resource 为 **22**，原六项 version 6，新删除按钮 version 2。旧 Candidate 资源和后端历史保留，页面入口继续隐藏。
10. 通过公开入口完成下节验收，检查 Web HTML 和实际加载的静态资源属于新镜像；最后重新核对保留的八个容器 ID/镜像、原 21 个规则集和运行期零副作用，整理部署证据交付。无需在本阶段提交、推送或发布制品。

## 5. 阶段 7 验收及合成数据边界

- 使用真实 Nginx → Gateway → BFF → Risk/Platform；没有 Simulator 或伪造可信身份 Header。登录/刷新/退出会正常写入 Session 和安全审计，Token 仅按既有工具在内存处理。
- 先验证旧 21 个规则集一集一行，合法编码及状态筛选、详情和历史内容正常；不修改、冻结、删除任何既有业务规则或 Candidate。
- 最多创建两组 `TEST_RULE_SET_LIST_<唯一后缀>` 合成规则，每组最多两个版本；覆盖直接编辑、保存后回退、过滤保持、陈旧删除 409、刷新重确认、整集删除和同键重放。结束时通过本次 DELETE API 删除合成规则集；根、不可变版本及幂等历史按业务设计保留，不做 test 物理清表。
- 复用既有只读测试身份；若不存在，最多通过现有 Operations API 创建一个临时测试账号并绑定已有 PLATFORM_READONLY 角色，用于实际删除 403 验证。验证后退出并删除该临时账号，保留正常审计，不新增角色或改变系统角色权限。管理员负责新增删除能力的正向验证；匿名请求应拒绝。
- 浏览器核对标题小字/摘要移除，详情/编辑/红色删除菜单，确认框名称+编码，Back/Forward、未保存保护、Candidate 旧 URL 及窄屏。页面截图不含账号凭据或真实规则内容。
- 使用中保护已通过阶段 4/5 的真实 Risk/MySQL/Redis 测试；test 目前没有 ACTIVE Release，不在部署验收中伪造 ACTIVE 或执行激活业务。运行期评估、Action Effect、Shadow、审批和多版本页面仍不在范围内。

已有 `smoke.py`、`--verify-risk-operations-stage` 及旧 Risk rollout 工具按闭合的 36 Route/21 Resource 目录分类，新增后不适合作为本需求的完整通过门禁。不能因它们拒绝 37/22 而删除新目录或恢复旧初始化。本次用上述精确目录、真实公开 HTTP、Snapshot 运行指标和浏览器逐项验收，保留既有登录/分页/刷新/退出合同；通用工具的新目录适配另行评估。

## 6. 失败处置与恢复限制

| 失败位置 | 允许的处置 |
|---|---|
| 备份失败或尚未执行 Risk 迁移/Platform 注册 | 停止发布；恢复原三个 image ID，应用迁移前撤回 SQL（仅确有维护状态变化时），确认旧 36 路由和 21 Resource，恢复原应用。 |
| Risk DDL 已提交、DML/后置核对失败 | 维持维护入口关闭，保留原备份和现场；关闭三应用，不让旧 Risk 写入带新根的库。不得自动删表或覆盖数据库；核对失败原因后修复，涉及数据恢复另行交付具体差异确认。 |
| 新应用启动失败或新 Snapshot 未收敛 | 保持资源隐藏，执行失败关闭 SQL 并等待 27 条旧非风控 Route；停止三应用，保留正确的其余服务。优先修复当前制品或使用理解管理根的新制品。 |
| 开放后验收失败，已产生新命令或删除事实 | 执行失败关闭 SQL、停止三应用并保存现场；不恢复老数据库或旧 Risk，否则会丢失新事实或重新暴露已删除规则。需修复后重新验收；数据恢复必须单独确认覆盖范围。 |

备份提供恢复材料，不等于可以无损覆盖后来写入。尤其 Platform 还拥有其他 IAM/审计事实，不整库恢复覆盖其他操作。当前没有另一个已验收且理解新管理根的旧制品，不能承诺迁移后的应用一键回退。

## 7. 准备阶段验证与授权请求

只读环境快照、历史 manifest 投影、原部署 Platform 编译器（36/36/37）及 Gateway 编译器（36/27/37）均通过；新镜像、本地配置检查、部署脚本离线测试 31/31、Requirements Site 构建/内容测试 136/136 及静态检查通过，详见 [stage6-release-package.json](./assets/stage6-release-package.json)。离线编译使用从当前运行容器读取的 JAR，没有重启或更新 Platform/Gateway。

**待确认：是否允许按本发布包进入阶段 7，在本机 mor-test 备份后应用上述两库 SQL，临时关闭/恢复风控入口，部署三个已构建镜像，并进行所列合成验收和清理？** 不包含提交、推送、远程制品发布或生产环境。
