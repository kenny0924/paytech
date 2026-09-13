# test 发布准备与待确认执行清单

本文件是阶段 6 的具体发布包与执行方案。阶段及实际部署状态只由[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)维护；本文件不表示已经部署。

**一句话：拟更新 Platform、Operations BFF、Web，并补齐 Gateway 回调命名兼容，共 4 个镜像；0 张新表、0 个字段变更，插入 227 条配置并更新 27 行启用数据；只切换 test。** Gateway 是本阶段发现的发布依赖，需与阶段 7 一并确认。

## 1. 环境与边界

- Docker context `desktop-linux`，Compose project `mor-test`；入口 `http://localhost:28080`，MySQL `127.0.0.1:23306/mor_platform`。实际容器 ID、当前镜像及健康状态保存在[基线](./assets/stage6/baseline.json)。不能用本机开发库 13306，也不能按名称模糊匹配其他容器。
- 应用候选标签统一为 `plt16-17-config-20260913`。[候选 Compose 覆盖](./assets/stage6/compose-candidate.yaml)直接固定本机镜像 ID，原来的 `:development` 标签及默认 Compose 文件不改。
- Java / Web Docker 基础镜像继续使用项目已固定的 digest；不变更 Nacos、Redis、MySQL、Vault、Risk、入口 Nginx、域名、端口或凭据。
- Foundation 404 修复已经包含在 BFF 的 `paytech-http-client-support-1.0.0-SNAPSHOT.jar`；复用本地已验证制品，不需要 Nexus 发布。Platform API 也只作为应用内部依赖，本轮不做远程制品发布。
- 镜像来自已验收、尚未提交的工作区。Git HEAD、源码 Hash、内部 JAR、测试报告和镜像完整 SHA 见[发布清单](./assets/stage6/release-manifest.json)，不能把旧 HEAD 当作本次完整代码版本。构建上下文与原始差异保存在 `test-environment/artifacts/plt16-17-config-20260913/`，不是敏感配置备份。

## 2. 四个具体制品

表中仅展示 SHA 前 12 位；执行必须使用覆盖文件中的完整镜像 ID。

| 服务 | 原运行镜像 | 候选镜像 |
|---|---|---|
| platform-support-service | `16017501f558` | `5c566c1d8951` |
| operations-console-bff | `20f0cc8ca5d1` | `78ff6ca2a3c5` |
| api-gateway | `70ed9f3aa481` | `f90783343899` |
| operations-console-web | `d2216498bb9a` | `d1d5fd635c07` |

原三应用实现范围仍为菜单/按钮树、权限目录/引用/显式授权、全部六类 Target/Route 配置及 Foundation 404 转换。Web 用已验证 dist 构建，不重新下载依赖。

### Gateway 兼容补充为何必要

从 test 实际运行 JAR 读取到 Platform/Gateway 的三个枚举仍为 `PROVIDER_CALLBACK`；本期 Platform/BFF/Web 按已确认 Channel 决定（跨专题来源：`agent-docs/decisions/2026-09-13-payment-channel-terminology.md`，由该专题单独归档）使用 `CHANNEL_CALLBACK`。只更新原三应用时，旧 Gateway 无法读取新增支付回调 Route 的新枚举，不能满足完整六类配置的共同快照合同。

候选 Gateway 仅带现有 Channel 命名补丁：`GatewayRoutePolicyValidator`、`GatewayMetrics`、`GatewaySnapshotMetrics` 三个生产类及两个对应测试文件，合计 11 行替换。三个公共枚举来自 Platform API。未新增网关路由硬编码、认证链或监控功能。Platform、BFF、Gateway 镜像内部 Platform API 的 117 个类逐个 Hash 相同；Gateway 候选重新构建并通过 64 项快照策略/监控定向测试。

test 当前所有 70 条 Route 都属于 OPERATIONS；旧名/新名的回调 Target 和 Route 均为 0，因此本环境回调数据迁移量为 0。执行前必须复查；若出现回调记录则暂停该发布清单，单独评估实际行，不能直接全库替换。此次只建议纳入入口合同的必要兼容，不部署其他系统的 Channel 专项。

## 3. SQL 与精确影响

[只读预检查](./assets/stage6/preflight.sql)及[检查结果](./assets/stage6/preflight.json)确认本期主键、自然编码、Method/Path 与孤立子项均零冲突，8 张受影响现有表的列定义和索引与验收库一致。当前系统管理员 `role_id=900000000000000510`、`version=17`。

| 手工 SQL | 影响 |
|---|---|
| [dml.sql](./db/platform-support-service/dml.sql) | INSERT 227 行：Permission 4、Role Permission 4、Resource 4、Route 18、Header 76、Query 13、Guard 108；Route/Resource DISABLED，管理员映射 REVOKED |
| [activate-test.sql](./db/platform-support-service/activate-test.sql) | UPDATE 27 行：18 Route、4 Resource、4 管理员映射、1 管理员角色；管理员版本 17→18，新增配置/映射版本 1→2 |
| [deactivate-test.sql](./db/platform-support-service/deactivate-test.sql) | 失败恢复候选：仅对未发生后续配置写入的刚启用基线撤销入口及新增授权；保留注册与历史，版本继续递增 |

没有 DDL，不运行完整初始化，不重新 bootstrap，不写入 Risk 示例。详细新增 ID 见[种子清单](./assets/stage6/seed-manifest.json)。第一次只为已有 PLATFORM_ADMIN 激活四个已评审的配置管理权限，不给普通角色自动授权。

SQL 文件均故意不提交事务。必须使用同一连接逐条执行、检查实际影响行数，再明确 COMMIT；异常立即 ROLLBACK，不使用 `--force`。插入数量依次必须为 `4/4/4/18/76/13/108`，启用数量必须为 `18/4/4/1`。不允许把文件丢给一个立即退出的客户端并误报成功，也不强行重设 version 绕过差异。

## 4. 拟执行顺序

1. 通知开始短暂维护，停止人工配置写入；重新读取 context、目标容器及镜像 ID、上述 SQL 前置和管理员版本。确认候选制品 Hash 与清单一致，Compose 合并配置只做 `config --quiet`，不输出包含密码的完整配置。
2. 对 7 张配置表及 `t_role` 做一次一致性备份，文件放 `~/Code-MoR-backups/<执行时间>-plt16-17-test/`，目录权限 0700；不导出人员、密码、会话、Vault 或 Risk 数据。记录现有目标依赖与行版本；保留旧镜像作为恢复入口。
3. 在 `mor_platform` 人工应用禁用注册 dml.sql，逐表核对 227 行后提交。这时新入口仍未开放。
4. 使用候选覆盖，按 **Gateway → Platform → BFF → Web** 逐个 `up -d --no-deps --no-build --pull never <service>`，每个达到原 readiness / health 检查后再继续。只重建这四个容器，不执行全栈 up/down 或构建脚本。新 Gateway 可读取旧 Operations 快照；新入口保持禁用，避免新页面指向未部署 Handler。
5. 四服务健康后应用 activate-test.sql，核对 27 行后提交；等待 Gateway 完整快照刷新成功且 OPERATIONS Route 为 88。读取 `/platform-support/gateway-config/v1/public-snapshot` 与 Gateway Prometheus 坐标核对，不套用仅识别旧 Risk 阶段的固定数量脚本。
6. 使用下方简短验收；由用户继续验证完整业务操作。交付运行镜像、SQL 计数、菜单及 API 入口证据。

命令形态（阶段 7 才执行；每次只替换一个服务）：

```sh
test-environment/compose.sh -f "$PWD/requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage6/compose-candidate.yaml" up -d --no-deps --no-build --pull never api-gateway
```

单实例切换可能短暂出现 502/503 或需要重新登录，不承诺零中断。无需更改 `.env.test`、私有签名/Vault 配置、Nacos 配置或入口 Origin。

## 5. 失败恢复

- 注册事务失败直接回滚。注册已提交但尚未启用时，保留禁用记录即可；按[原镜像覆盖](./assets/stage6/compose-previous.yaml)恢复被替换的服务，无需删除初始化记录。
- 若健康检查失败、完整 Snapshot 无法刷新、原登录/风险入口不可用，立即停止继续发布。若已启用，先核对没有用户配置新写入，使用 deactivate-test.sql 撤销本期新增入口/授权并确认快照恢复 70 条，再按原镜像覆盖恢复 Web、BFF、Platform、Gateway。
- 恢复不会删除审计、命令回执或用户数据，也不会把管理员版本退回 17。备份用于核对及人工恢复，不自动覆盖整库。
- 若已产生用户新写入、角色版本变化或新 Channel 回调对象，固定恢复 SQL 不再适用；保留当前数据，列出受影响行并重新评估。不能将新枚举数据直接交给旧 Gateway/Platform，不能用一键 SQL 抹掉后续业务操作。

## 6. 部署后的轻量验收

- 四个服务健康、镜像完整 ID 与候选一致；其余七个容器 ID/镜像保持原样。SQL 注册及启用计数正确，快照从 70→88，刷新成功且未落入 Last Valid Snapshot 回退。
- 从真实入口登录既有 test 管理员，检查“平台配置”下“菜单与按钮”“权限目录”“API 路由管理”；刷新 / 重新登录后确认菜单和权限同步。
- 只读调用菜单树、权限目录、资源能力、Gateway 能力及权限选择器；确认六类 Channel 包含 CHANNEL_CALLBACK，A18 不再误报 403。打开详情、编辑表单后取消，不保存测试业务配置。
- 查看原风控规则集、决策流、变量列表，确认 BFF 依赖替换没有破坏已发布入口；访问一个不存在的配置对象，确认 404 Problem 正确返回。
- 本阶段不添加新账号、角色、Target、Route 或业务 Fixture；登录会产生正常会话/审计记录，退出即结束会话，审计保留。完整写入能力已经在阶段 5 验收，不在 test 重复大范围测试。

## 7. 下一确认点

请确认是否按本清单进入阶段 7：允许对 test 做一次所列配置表备份、应用 227 行注册及 27 行启用 SQL、更新四个镜像（包括 Gateway 必要命名兼容）、执行上述轻量入口验收及必要的受限恢复。不包含远程制品发布、Git 提交、推送或生产环境。
