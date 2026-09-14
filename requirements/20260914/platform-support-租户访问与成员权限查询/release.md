# 租户访问与成员权限查询 test 发布准备

本文件保留阶段 6 的已确认发布清单。用户随后回复“可以”，阶段 7 按清单完成部署；执行中另修正三条新菜单中文名称的客户端编码问题。实际证据见 [test-acceptance.md](./test-acceptance.md)，当前状态仅见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)。

**已部署 Platform、Operations BFF、Web 三个应用，提供 8 项业务 API、3 个页面；0 DDL、105 条登记 INSERT、15 条启用 UPDATE，另 3 条显示名称修正 UPDATE。**

## 目标与制品

- Docker context：`desktop-linux`；Compose：`mor-test`。
- 数据库：`mor-test-mysql-1`，`127.0.0.1:23306/mor_platform`；入口：`http://localhost:28080`。
- 只替换 `platform-support-service`、`operations-console-bff`、`operations-console-web`。Gateway 自动读取数据库快照，预计有效路由从 97 增至 105；其他服务与配置保持原状。
- 候选 tag 为 `mor-test/<service>:tenant-access-20260914`，实际替换使用固定 image ID。[候选 Compose](./assets/stage6/compose-candidate.yaml)、[旧镜像 Compose](./assets/stage6/compose-previous.yaml)、[完整制品清单](./assets/stage6/release-manifest.json)。
- Platform 使用阶段 4 完整 verify 对应的可执行包；BFF 重新打包以统一内嵌 Platform API，两个内嵌 API 字节一致。重新打包前后 API 的全部 ZIP 条目内容一致，差异仅来自原归档元数据。Web 固定阶段 5 验收过的 46 个生产静态文件。67 个源文件保持验收基线。
- 健康探针从当前源码重新编译，包含 BFF 管理端口 19201。镜像只包含 JAR、探针或 Web 静态资源；不包含私密配置。只在本机准备镜像，未推送镜像库或发布远程 Maven 制品。

## 只读核对结果

[容器基线](./assets/stage6/baseline.json)记录 11 个 healthy 容器及原镜像；[数据库前置](./assets/stage6/precise-preflight.json)及[查询 SQL](./assets/stage6/preflight.sql)记录：

- 7 张 IAM 表/75 个字段已存在，不需要建表或修改字段、索引。
- 所有新 ID、权限代码、菜单路径、Route 路径没有冲突。
- 有效 Operations 操作者为 `221546852372267008`；已有 PLATFORM_ADMIN `900000000000000510` 为 ACTIVE/version=19；Operations Target `900000000000000001` 为 ACTIVE/version=1。
- 当前 CLIENT/PARTNER Tenant 和对应成员均为 0。本次不构造 test 业务租户、账号、成员、授权或凭证示例；部署检查验证真实入口和空列表，真实写入链已由阶段 5 合成数据验收。

这些是准备时的观察，执行前需核对未发生漂移。不得用重置版本、覆盖配置或重跑完整初始化处理不一致。

## 具体执行顺序

1. 核对 `desktop-linux`、三应用原镜像、MySQL 容器身份及数据库前置；任一项变化先检查差异。
2. 仅做一次受影响配置备份：`t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role` 的结构与数据。执行时保存至 `~/Code-MoR-backups/20260914-tenant-access-test/<UTC时间>/`，目录 0700、文件 0600，核验成功并记录 SHA 后继续。阶段 6 时尚未执行，阶段 7 已完成，见[备份回执](./assets/stage7/backup.json)。不备份凭据、会话或其他业务库。
3. 使用候选 Compose 逐个替换 Platform → BFF → Web，各自 healthy 后再继续。每次只指定一个服务，使用 `up -d --no-deps --no-build --pull never --wait --wait-timeout 240`。不运行全量 deploy.sh，不重建或重启其他服务。
4. 交互 MySQL 客户端须显式 `--default-character-set=utf8mb4`；在一条连接设置 `@plt_tenant_operator=221546852372267008`，执行本期 [dml.sql](./db/dml.sql)。核对实际新增数：Permission 3、RolePermission 3、Resource 3、Route 8、Header 20、Query 20、Guard 48，共 105；Route/Resource 均 DISABLED/version=1，管理员三条新增关联 REVOKED/version=1。检查关联完整后显式 COMMIT；任何错误 ROLLBACK，不使用 `--force`。
5. 新应用健康且登记正确后，执行 [activate-test.sql](./db/activate-test.sql)。核对 8 Route、3 Resource、3 RolePermission、1 Role 共 15 条更新后显式 COMMIT；新项版本 1→2，PLATFORM_ADMIN 19→20。新增权限只授予既有 PLATFORM_ADMIN，不扩大其他角色。手工 SQL 不进入应用启动流程。
6. 等待 Gateway 消费新快照，再完成下述部署检查；记录实际镜像、SQL 影响数和证据。短暂服务替换可能让 Operations 请求失败，刷新或重新登录后重试；不承诺零中断。

每次替换命令形态（在工作区根目录执行，服务按上述顺序）：

```sh
./test-environment/compose.sh \
  -f "$PWD/requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage6/compose-candidate.yaml" \
  up -d --no-deps --no-build --pull never --wait --wait-timeout 240 platform-support-service
```

## 部署检查范围

- 三个新应用 healthy；其他八个容器身份及镜像与基线一致。
- 当前管理员经真实 Nginx → Gateway → BFF → Platform 登录，动态菜单含“租户访问”和两个授权按钮；Token 仅在内存使用，完成后 logout，正常登录/审计记录保留。
- 租户列表 CLIENT/PARTNER 筛选返回合法空页；不存在租户的详情返回 404；既有人员/角色/API Route/审计/风控配置查询保持可用。没有 tenant/member 测试数据时，不把空页冒充暂停、恢复或成员权限业务验收。
- Gateway 新增八条 Route 已进入快照；105 条新登记和 15 条启用更新符合预期。
- 公开 Web HTML 与 46 个静态文件 SHA 对应候选制品；浏览器确认新菜单、空列表、刷新与返回。完整本机业务验收不在 test 再重复执行。

## 恢复方式与限制

- SQL 尚未提交的失败：同一连接 ROLLBACK，停止后续步骤。
- 尚未启用时：新增禁用登记可保留；按旧镜像 Compose 回退已替换的三个应用。
- 启用后失败：三项菜单名称修正后，须使用 [deactivate-test-after-label-repair.sql](./db/deactivate-test-after-label-repair.sql)停用本期 8 Route/3 Resource、撤销本期 3 管理员关联，并推进角色版本。该脚本仅适用于 Route/RolePermission version=2、Resource version=3、角色 version=20 且没有后续配置改动；核对 8/3/3/1 后 COMMIT。版本继续增加，不倒退到原值。
- 确认快照不再暴露新入口后，按 Web → BFF → Platform 回退旧镜像并逐个检查健康。不删除登记，不整表恢复覆盖后续写入。
- 租户安全状态、命令和审计属于业务事实。若用户已操作，代码回退不自动恢复 Tenant 状态；解除安全暂停须走正常授权业务流程。若版本漂移或超出上述恢复前提，应停止并提交实际差异。

## 准备验证与确认范围

三个本机候选镜像已构建；候选及旧镜像 Compose 静态校验通过。Platform/BFF 内嵌 API 字节一致，67 个源文件摘要一致；沿用阶段 4/5 有效应用验证，需求站本轮 136/136 通过。见 [准备检查](./assets/stage6/verification.json)。

阶段 6 当时申请、后由用户批准的范围：**按本清单备份受影响配置，应用 105 条登记和 15 条启用更新，替换三个 test 应用并做轻量入口检查；如失败按上述精确边界恢复。** 不包含 Git 提交、推送、远程制品发布或新增 test 业务示例。
