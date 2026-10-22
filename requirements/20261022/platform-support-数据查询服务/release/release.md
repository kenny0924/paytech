# 数据查询服务 · mor-test 发布清单（阶段 6—7）

- 授权：用户 2026-10-22“可以，提交并发布 mor-test”。范围：提交（已完成 `9a209e6f`，未推送）、mor-test 的 Platform DDL、Vault DML、注册块 131 与激活、验收被查库，替换 `platform-support-service`、`operations-console-bff`、`operations-console-web`，浏览器验收。推送不在授权内。
- 顺序约定：同一时段“文件多存储并存”（code-mor-38）也替换这三个服务，约定**对方先发**；本次以 `9a209e6f` + 对方发布树（`Code-MoR-filestore-rel` 相对 `281a217d` 的改动）三方合并构建，不取主工作区（主工作区另含工作流与 channel-connector 第二段的未提交改动）。
- 发布树：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery-rel`（detached `9a209e6f`）。

## 1. 数据库与 Vault（root，客户端 utf8mb4）

| 顺序 | 文件 | 目标 | 说明 |
| --- | --- | --- | --- |
| 1 | [sql/00-precheck.sql](./sql/00-precheck.sql) | mor-test | 只读；任一行 actual ≠ expect 即停止 |
| 2 | `paytech/platform-support/docs/database/sql/changes/2026-10-22-data-query.sql` | `mor_platform` | 3 张表；只新增 |
| 3 | [../db/vault/dml.sql](../db/vault/dml.sql) | `mor_vault` | 类型 `DATA_SOURCE_PASSWORD_V1` 与 2 条规则；Vault 按刷新间隔重载 |
| 4 | [sql/10-registration-block131.sql](./sql/10-registration-block131.sql) | `mor_platform` | 234 行，休眠 |
| 5 | 换镜像（第 2 节） | — | 三个服务 healthy 后再激活 |
| 6 | [test-activate.sql](./test-activate.sql) | `mor_platform` | 预期 18 / 5 / 4 / 1 |
| 7 | [sql/99-postcheck.sql](./sql/99-postcheck.sql) | mor-test | 只读 |

验收被查库：`local-environment/mysql/manual/2026-10-22-data-query-fixture.sql` 已于 2026-10-22 在 mor-test MySQL 执行（`mor_data_query_fixture` 3 表 3/10/1000 行；reader 仅 SELECT，writer SELECT/INSERT/UPDATE）。密码在 `test-environment/private/data-query-fixture.env`（0600，不入库）。

## 2. 镜像与候选覆盖文件

- 构建：发布树中 `-am` 打包三个服务；Java 镜像用 `test-environment/java/Dockerfile`；ops-web 在发布树 `npm ci && npm run build` 后构建静态镜像。部署只认固定 `sha256`。
- 基线证明：先用对方发布源码（不含本需求）构建并与运行容器逐字节比对，再叠加本需求。
- 环境变量：照搬对方 `compose-candidate.yaml` 中三个服务的 environment 处理（含去掉 `FILES_ONBOARDING_*`），并为 Platform 增加 `DATAQUERY_HTTP_CALLERS_0: operations-console-bff`（执行入口调用方白名单，默认空即全部拒绝）。BFF、Web 不新增环境变量。
- 合并检查：文本合并无冲突也要检查 YAML 重复顶层键。主工作区已出现过一次：Platform 测试 `application-mock.yml` 中文件存储的 `test.storage` 与数据查询的 `test.data-query` 各自新增了顶层 `test:`，导致 SnakeYAML 报 duplicate key，所有 Spring 测试的上下文都起不来；工作流会话已在主工作区修复。发布树合并后，对所有改动过的 `*.yml` 做重复键扫描。
- 回滚目标：对方发布后的三个运行镜像（阶段 7 第 1 步核对）；回滚文件 `compose-rollback.yaml`。对方告知（2026-10-22）：
  - Platform `sha256:04c12879527bc885dd673d3f0417f3807946f46d62e56ab5f5663af97ea77901`
  - ops-bff `sha256:e81b84242c36510722a2a0fbc21f73842a350bf1b7bcc464fc59ba7bedaa5f5c`
  - ops-web `sha256:d58172d9a291c99f1c1b37ef76e0a77284ab49ede84dafd294f05d1244e8ea9e`（含发布中修正的 `fileStorage.ts`）
- 发布树源码：`9a209e6f` + 文件存储最终源码（树 `40e9fecb`，相对首次快照 `704de23b` 只多 `fileStorage.ts` 与块 130 的 2 行路由头），均已合入；Web 1275 通过、tsc / build 通过，dist 169 个文件、清单 sha `fe6b7e6c…`；BFF DataQueryRegistration 5、FileStorage 37 通过。Java JAR 不受这两处影响（Platform `dd45f235…`、BFF `65c27756…`）。

## 3. 回滚

1. 执行 [test-deactivate.sql](./test-deactivate.sql)（菜单、路由、授权停用，注册行保留）。
2. 用 `compose-rollback.yaml` 换回对方发布后的镜像（旧 Platform 不读写 3 张新表）。
3. 需彻底移除时执行 [sql/rollback-unregister.sql](./sql/rollback-unregister.sql)（块 131、3 张表、Vault 策略、验收被查库与账号）。

## 4. 验收（浏览器，`http://localhost:28080`）

对照 Pencil 原型 01—06：数据源列表与详情；新建只读数据源（reader，检查三步通过并启用）；writer 账号检查不通过不能启用；模板新建、校验问题定位、参数 / 约束 / 列、试运行（集合、片段、分页、总数）、保存与版本对比恢复；执行入口经 BFF 返回结果；超时用例返回 `DATA_QUERY_TIMEOUT`。

## 5. 执行记录

2026-10-22（UTC 05:4x，Asia/Dubai 09:4x），主会话执行：

1. 文件多存储并存 code-mor-38 通知“可以开始”，运行镜像 Platform `6dd8ffff…`（filestore-20261022c）、ops-bff `e81b8424…`、ops-web `d58172d9…`。重新取对方源码树 `9128cbad`（相对 `40e9fecb` 多 MinioFileStorage / S3FileStorage 截到微秒及其测试），三方合入发布树无冲突；对方改动、本需求未碰的 195 个文件与对方源码逐字节一致。
2. 基线：对方源码单独构建的 ops-web dist 163 个文件与运行容器逐字节一致；运行 Platform JAR 的条目在候选 JAR 中全部存在，新增 233 项均为 data-query / core / api 数据查询类与 jsqlparser-5.4，内容不同的 6 项（application.yml、PersistenceConfiguration、YamlEventCatalog、console-resource-capabilities.json、classpath.idx、layers.idx）都来自本需求。
3. 环境变量：候选覆盖渲染结果与运行容器逐键比对，Platform 只多 `DATAQUERY_HTTP_CALLERS_0`，BFF、Web 完全相同；回滚文件渲染后与运行容器完全相同。
4. 备份 mor-test `mor_platform`、`mor_vault`（mysqldump，会话临时目录）。预检全部符合（[00-precheck-20261022.out](./sql/00-precheck-20261022.out)；被查库与两个账号为阶段 6 预建，期望值相应为已建）。
5. 执行 `2026-10-22-data-query.sql`（3 表）、`db/vault/dml.sql`（策略版本 19 → 20）、`10-registration-block131.sql`（权限 4、路由 18 均为休眠）。
6. 按 Platform → ops-bff → ops-web 替换，均 healthy：Platform `sha256:985d00ea…`（JAR `38602569…`）、ops-bff `sha256:d221817d…`（JAR `65c27756…`）、ops-web `sha256:e29197ba…`。
7. `test-activate.sql`：18 / 5 / 4 / 1；事后核对全部符合（[99-postcheck-20261022.out](./sql/99-postcheck-20261022.out)），PLATFORM_ADMIN 版本 41 → 42。
8. 浏览器验收：见 [acceptance/](./acceptance/)。
