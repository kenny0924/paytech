# RCO-16 Gateway 补改阶段 5 验证记录

2026-09-12 · **阶段 5 本地验收通过，用户随后已确认返回阶段 6；当前发布准备见 [release-final.md](./release-final.md)。** Gateway 和 Platform 完整构建、覆盖率门禁通过；待发布权限绑定与禁用入口冲突已按用户确认修正。[注册时序及执行记录](./pending-registration-permissions.md)。

**本阶段新增业务 API、表、字段均为 0；修复 Gateway 413 映射与权限注册时序；经授权新建 1 个独立本机验证库，复用现有 24 表/683 条初始化记录，备份后修正 7 条绑定和 1 条管理员版本。** 原 RCO-16 业务范围不变。

## 验证结果

| 范围 | 结果及证据边界 |
|---|---|
| Gateway 完整回归 | `clean verify` 1199/1199，65 个测试类，0 失败/错误/跳过 |
| Gateway 覆盖率 | 指令 96.649021%（18257/18890），分支 90.084034%（2144/2380），均通过原 90% 门槛 |
| 验证脚本 | 边界检查通过；class-file 3 项测试通过。仅识别 JaCoCo 本身跳过的 synthetic/module 编译产物，普通匿名类仍要求报告，没有增加业务类排除 |
| Platform 全量 | 最终干净 `clean verify` **386/386**，42 个测试类，0 失败/错误/跳过；真实本机 MySQL/Redis 和随机端口 HTTP |
| Platform 覆盖率 | 指令 **97.784105%（23697/24234）**、分支 **90.058766%（1839/2042）**，原 90% 门槛不变，没有新增业务类排除 |
| 注册/发布工件 | 182 条 SQL 与清单逐元组一致；注册、开放、关闭、回退等 **21 项离线检查**通过，包含 Viewer、自定义角色及既有撤销授权保护。SQLite 状态模拟不等同 MySQL 并发验证 |
| 需求站点 | `npm --prefix requirements-site test` **136/136**；两处旧 Gateway 状态断言同步为现行动态路由与未部署事实 |
| Platform 完整快照 | 原被环境前置阻塞的 HTTP 套件 8/8、动态 Target HTTP 2/2 通过；当前真实 HTTP 的 36 条有效路由被新 Gateway 接受 |
| 数据库完整发布投影 | 通过真实 Repository 读取 51 条注册路由，仅在内存将禁用态投影为开放态，经 Platform 编译/组装及新 Gateway 编译通过；包括本期字典路径。数据库未启用这 15 条待发布路由 |
| BFF | 复用补改阶段 4 的 1041 项全量结果；4 个补改文件 Hash 均未变。没有重复执行 BFF 全量测试 |
| 原 Risk/Web | 本阶段没有修改或重跑业务源码，原综合验收证据仍见 [acceptance.md](./acceptance.md)，不将本阶段 Gateway 检查称为真实 test 入口验收 |

最终安全摘要、源码 Hash、测试结果及本机日志 Hash 见 [permission-correction-verification.json](./assets/gateway-acceptance/permission-correction-verification.json)。修正前的 [verification.json](./assets/gateway-acceptance/verification.json) 与阶段 4 报告保留历史结果；原始日志仅在本机 `/tmp/`，长期证据不含凭据。

## 修复与发现

- 当前 Spring 将 413 解析为 `CONTENT_TOO_LARGE`，原 Gateway 异常分支使用旧 `PAYLOAD_TOO_LARGE` 枚举，导致框架产生的 413 被映射为普通请求错误。先用回归复现，再修复两处枚举分支；公开错误码仍为 `GATEWAY_PAYLOAD_TOO_LARGE`。
- 扩展快照安全/Guard/路径编译、配置契约、真实 Cookie HTTP、JWKS HTTP 和错误映射验证。原 `PlatformSnapshotDiscoveryPropertiesAutoTest` 放在 `auto` 外，不在默认 Maven 扫描范围；已移入 `auto/configuration` 并在最终干净构建中实际执行。
- 完整初始化目录原 Oracle 停留在 36 Route/21 Resource，当前权威初始化为 51/32。按逐条固定元组补齐：7 Permission、7 RolePermission、11 Resource、15 Route、46 Header、22 Query、90 Guard；原元组不变。发布快照和导航仍只断言 ACTIVE 项，没有为了测试开启注册。
- 旧 Operations 夹具请求 ID 为 81 位，超过 64 位契约后被入口重写，审计清理的安全归属校验因此拒绝。改为保留完整前缀的并发唯一序号；2 项契约测试先失败后通过。仅精确清理本轮自有遗留记录，保留归属检查，[事件记录](./assets/gateway-acceptance/request-id-fixture-incident.json)。
- 两处旧测试预期已核对并修正：缺失 JSON 创建字段返回 400，形状完整但值非法返回 422，并分别检查；Bootstrap 交接后，等待数据库锁的第二个请求重新校验已撤销会话，返回 401。两项真实 HTTP 定向复验通过，生产鉴权和校验行为未改。
- **配置缺陷已修正：** 原 7 条管理员 ACTIVE 绑定没有有效 Operations 入口，导致 4 个角色/人员管理 HTTP 用例 403。按批准方案将待发布绑定注册为 REVOKED，随对应 Route 同事务开放；已同步完整初始化、增量注册、清单、独立 Oracle 与发布 SQL 生成器，原 4 项随最终全量通过。Java 授权上限与业务规则保持原行为。
- 覆盖率补测验证了真实凭据持久化异常、JWKS 算法不一致、路径编码和状态修改显式 null 字段。补充用例沿用真实 HTTP/数据库或公开纯契约入口，没有为达标新增业务类排除或降低门槛。
- 阶段 6 原预检不含角色绑定快照，五份旧 test SQL 已加入失败门禁；生成器只接受精确环境快照，不能猜测版本或恢复之前已撤销的权限。重新只读采集 test、生成和复核最终包属于下一阶段。

## 数据库与环境

用户明确批准后，先备份原 `mor_platform` 结构、表计数及 `platform_app` 授权元数据，再创建 `127.0.0.1:13306/mor_platform_rco16_validation`，由管理员应用现有 Schema/初始化 SQL。`platform_app` 只获该库 SELECT/INSERT/UPDATE/DELETE；无应用建表、无新容器、无 Flyway/Testcontainers。

- 备份目录：`/Users/kenny/Code-MoR-backups/rco16-platform-validation-20260912T045814Z/`；[执行 Hash 与结果](./assets/gateway-acceptance/local-sql-application.json)。
- 随后单独获准并备份应用 7 条绑定 + 1 条角色补丁；[执行证据](./assets/gateway-acceptance/permission-correction-application.json)保留精确 SQL 和备份 Hash。
- 最终 24 张表/683 条初始化记录；8 张完整目录表的非时间字段逐项等于修正后的权威初始化，7 条绑定 REVOKED/version=2、管理员 version=3，无多余或缺失记录；账号/Session/Key/审计/命令夹具清理完成。
- 原库全部表计数和结构 Hash 与执行前相同；保留其原有账号、签名 Key、审计链和路由版本。[修正后最终复核](./assets/gateway-acceptance/permission-correction-database-check.json)。
- Gateway HTTP 使用真实本机 Redis、运行时别名和两个独立 HTTP 下游 Simulator；Platform 使用真实本机 MySQL/HTTP。没有使用真实 Nacos 进行本阶段转发测试。
- 临时服务已停止，最终独立验证库无其他应用连接；验证库保留。182 条注册条目数未变，6 个新绑定状态及文件 Hash 已随批准修正更新。未访问/修改 test、发布制品、部署、Git 提交或推送。

## 验收映射与停点

| 编号 | 本阶段结论 |
|---|---|
| GW-01–03 | 本地真实 HTTP 的动态 Target/Route 刷新、服务发现绑定及确定时序在途请求隔离通过；随最终 Gateway 全量重跑 |
| GW-04–05 | 全目录拒绝、Last Valid、未初始化/无路由/无实例及控制面故障覆盖通过，安全上限保持 |
| GW-06 | Gateway/BFF 回归证据成立；Platform 注册时序修正后最终 386/386，原 4 个 IAM 管理失败已闭合 |
| GW-07 | BFF 两监听实测证据复用，Compose 探针工件已改；实际 test 容器探针与 Nacos 注册留待授权部署后验证 |
| GW-08 | 真实数据库 36 条有效 HTTP 快照及 51 条内存开放投影均通过新 Gateway；库内状态不变。表注释模型证据复用阶段 4 |
| GW-09 | 阶段 4 的 37/27/27/51 离线目录兼容证据保留；新增数据库 51 条投影验证通过。真实 test 字典入口与权限验收仍未执行 |

阶段 5 本地验收已完成，用户随后已确认进入下列阶段 6 范围：重新只读核对 test、核对 Gateway 未部署源码、重建 BFF/准备 Gateway 制品、重生成精确发布 SQL 并交付完整包。具体 test SQL 应用与部署在阶段 7 单独授权；旧发布包仍不可直接执行。
