# Client API 接入管理 test 发布

用户于 2026-09-13 明确要求“部署test”，本次合并发布准备与执行，已完成部署。当前状态以 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) 为准，检查结果见 [test-acceptance.md](./test-acceptance.md)。

## 发布范围

- 环境：Docker `desktop-linux`，Compose `mor-test`，入口 `http://localhost:28080`，数据库 `127.0.0.1:23306/mor_platform`。
- 仅替换 Platform Support、Operations BFF、Operations Web。使用已完成阶段 5 回归的工作区源码/生产资源；当前 Git HEAD 并不包含全部本期改动。
- 镜像、JAR、内嵌 Owner API 和 44 个 Web 文件 SHA 见 [manifest](./assets/stage6/release-manifest.json)。Platform/BFF 内嵌 API 字节一致。
- 更新前的容器/镜像见 [baseline](./assets/stage6/baseline.json)。[候选镜像](./assets/stage6/compose-candidate.yaml)与[原镜像](./assets/stage6/compose-previous.yaml)均固定到镜像 ID；逐服务 `up -d --no-deps --no-build --pull never`，不调用全量 deploy.sh。

## 数据与顺序

1. 核对现有管理员账号 `221546852372267008`、PLATFORM_ADMIN `900000000000000510` version 18、Operations Target/Parent、两条旧 Route 版本及新 ID/code/path/index 空缺。
2. 将八张受影响配置表的结构及前像保存到用户目录外部的受限文件。备份不包含账号凭据、会话、业务凭证或其他系统。
3. 应用服务原有[两表 DDL](../../../platform-support-service/docs/database/sql/changes/2026-09-08-add-api-credential-validation.sql)及本期 [DDL](./db/ddl.sql)：补齐 Credential/Grant 两旧表，新建 Scope 表，共 3 表/28 字段；两个反向查询索引。
4. 替换三个应用并等待健康。
5. 在同一连接应用 [DML](./db/dml.sql)，核对 INSERT 127 条、两旧 Route 各推进一版，再提交。新配置先不生效。
6. 同连接核对并提交 [启用 SQL](./db/activate-test.sql)：9 Route、4 Resource、4 RolePermission，PLATFORM_ADMIN 18→19；新权限仅沿用已有管理员角色。Gateway 自动消费新 Snapshot，不替换 Gateway 镜像。
7. 检查真实入口登录、菜单、新旧查询、Web 静态资源一致性和所有容器健康。业务验收交由用户；不在 test 生成 Client/凭证或执行撤销。

## 恢复边界

必要时先停用本期入口，再恢复三个旧镜像；[停用候选](./db/deactivate-test.sql)仅适用于启用后的准确版本且无后续配置写入时，不可盲目重放。新增表/索引及用户业务数据保留；撤销产生的 REVOKED 事实不可通过部署恢复为 ACTIVE。DDL 自动提交，失败后按实际已完成语句处理，不重跑全量 Schema，不自动 DROP 或清库。

不发布远程 Maven 制品，不提交或推送 Git，不创建 Client 自助凭证能力。本次发布仅代表上述 test 环境。

## 执行记录

已完成前置核对、一次配置备份、DDL、注册及启用。数据库语句结果与 Hash 分别记录在 assets/stage6；备份定位信息见 [backup.json](./assets/stage6/backup.json)，内容保存在 Git 工作区外的受限文件。

首轮应用均已启动，BFF 的 Compose 探针仍为 starting 时发现旧探针 class 未包含当前管理端口；注册/启用已提交后，重新编译当前探针源码并替换两个 Java 镜像。最终所有服务健康，三个业务包未改，最终镜像以 manifest.images 为准，firstAttemptImages 仅保留此次打包问题的历史证据。恢复候选未执行。

15 项部署检查通过，11 容器健康、Gateway 97 Route；本次没有生成 test Client/凭证/Scope 数据。下一步为用户验收。
