# 外部 API · 渠道沙箱验证 · PayerMax：mor-test 发布准备

阶段 6 发布清单。用户批准后已于 2026-10-14 执行，结果与偏差见 [test-acceptance.md](./test-acceptance.md)；当前状态以台账为准。原评审稿：用户 2026-10-14 确认发布范围 A+B+C；客户入驻（D）由其会话在本次之后单独发布。只读核对已完成（下文），任何写入、替换或配置改动须用户批准本清单后按顺序执行。

**本次：Gateway、Platform、Operations BFF、Web 四个应用升级到提交 `ba36b63`；test `mor_platform` 补 25 张表、两批登记 499 行 INSERT（全部 DISABLED）、启用 52 行 UPDATE；Platform 只开启补偿任务开关；随后经管理接口写入 PayerMax 配置并在 test 跑 UAT 验证用例。**

## 1. 范围与边界

| 批次 | 内容 | 数据库 | 来源 |
|---|---|---|---|
| A 外部 API 配置与执行 | 标准 API、接入方、原子 API、脚本、流程的配置页与管理接口 | 6 表；登记 344 行（30 路由、5 菜单、2 权限、2 授权等） | [外部 API 登记](../../../20260914/platform-support-外部API配置与执行/db/platform-support-service/registration-dml.sql) |
| B 渠道沙箱验证 | 验证账号、用例、运行与证据；出站地址沙箱标记 | 4 表；登记 155 行（14 路由） | [沙箱验证登记](../db/registration-dml.sql) |
| C PayerMax | 标准 API `PAY`/`PAY_QUERY`、接入方 `PAYERMAX`（UAT 地址） | 无 SQL，经真实管理接口写入 | [配置脚本](../../platform-support-PayerMax接入/assets/harness/configure.py) |

- **镜像整体升级带来的其他已提交代码：** 四个应用从 2026-09-13/14 升至 `ba36b63`，同时带上期间所有已提交改动（Delivery、客户入驻的 Platform/BFF 部分、Gateway 数据库路由等）。这些功能的开关默认关闭（`notification.enabled`、`files.onboarding.enabled`），test 没有对应路由或 Client 入口，本次不启用、不登记、不初始化其数据。
- **只建表，不初始化他人数据：** 25 张缺失表包含 Delivery 7 张、文件 7 张、认证挑战 1 张、补偿事件 1 张，均为空表；其登记与初始化 DML 留给各自需求发布。
- **Risk 不升级：** 升级需要客户入驻的 Risk 变更与 Provider→Channel 的争议表改名迁移，属于其他会话。BFF 现有风控页面只调用旧 Risk 已有接口，部署检查覆盖这些页面；失败按第 7 节回退 BFF。
- **正式执行在 test 不可用：** 标准 API 正式执行读取渠道账号需要 Vault Reveal 策略，test Vault 未部署该策略（台账已记录）。沙箱验证从验证账号表读取，不受影响。
- 不改 Nginx、Nacos、Vault、Risk、MySQL/Redis/RabbitMQ；不推送镜像、不改生产配置、不提交 Git（文档另行提交）。

## 2. 只读核对结果（2026-10-14）

- Docker context `desktop-linux`；mor-test 11 个容器此前均已停止 3 周。经用户同意只启动了 `mysql redis rabbitmq nacos`（`compose.sh start`，未重建），均 healthy；应用容器仍为停止状态。
- **Schema（`mor_platform`）：** 现有 25 表与当前 [完整 Schema](../../../../platform-support-service/docs/database/sql/platform-support-schema.sql) 的字段、索引一致（`boolean`/`tinyint(1)` 等价）；缺失 25 表，无需 ALTER。见 [test-ddl.sql](./test-ddl.sql)。
- **登记前置：** 路由 ID 071001～071044、权限 071101/071102、资源 071301～071305、授权 071201/071202 均无冲突；`/api/operations/external-api-admin/` 无路径冲突；Operations Target `900000000000000001` ACTIVE/v1；PLATFORM_ADMIN `900000000000000510` ACTIVE/v20；当前 105 条路由全部 ACTIVE；最大 Body 上限 65536，新路由不超过。
- **操作者：** `221546852372267008`（OPERATIONS、ACTIVE），沿用上次 test 发布。
- **配置：** 所有必填占位符 test 已提供；Platform 在 `test` Profile 使用代码内 `LocalLoginEmailVault`，登录不依赖 Vault 策略。Platform 所在 backend 网络非 internal，可出站访问 PayerMax UAT（https）；应用无出站白名单配置。
- **补偿开关影响：** Platform 三个补偿 Handler 中，`NotificationDeliveryHandler` 需 `notification.enabled=true`，`FileScanHandler` 需 `files.onboarding.enabled=true`，两者保持关闭；开启 `compensation.enabled` 后只有 `IntegrationVerifyRunHandler`（沙箱验证运行）生效。
- `mor_risk` 与当前 Risk Schema 差 1 表与争议表改名，确认本次不动 Risk。

执行前须重新核对以上各项未漂移；不一致时停止并报告，不用重置、覆盖或重跑完整初始化处理。

## 3. 制品

- 从**干净提交** `ba36b63` 构建：`git worktree add <scratchpad>/release-ba36b63 ba36b63`，在该目录构建，不使用含其他会话未提交改动的工作区。
- Java：JDK 25 `./mvnw -o -DskipTests package` 得到 `api-gateway-web`、`platform-support-web`、`paytech-operations-console-bff-web` 的 `*-exec.jar`；Web：`npm run build` 生成 `dist`。镜像沿用 [test-environment/java/Dockerfile](../../../../test-environment/java/Dockerfile) 与 Web Dockerfile，tag `mor-test/<service>:sandbox-verify-20261014`，执行时以固定 image ID 写入 [compose-candidate.yaml](./compose-candidate.yaml)。
- 回退镜像：[compose-previous.yaml](./compose-previous.yaml)（Gateway `f9078334…`、Platform `083853a8…`、BFF `7957f8c7…`、Web `40785d34…`）。
- 该提交的验证：Platform 全量 415/415、BFF 1279/1279、前端 989/989 与构建（工作区回归，见[交付记录](../delivery.md)第 10 节）。构建完成后记录 JAR/镜像 SHA。

## 4. 执行顺序

1. **核对**：重跑第 2 节只读查询；确认四个应用的原镜像与 MySQL 容器身份未变。
2. **备份**（一次）：`mor_platform` 完整结构，以及 `t_permission`、`t_role_permission`、`t_role`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule` 的数据。保存到 `~/Code-MoR-backups/20261014-sandbox-verify-test/<UTC>/`，目录 0700、文件 0600，核验并记录 SHA 后继续。
3. **建表**：root 连接 `--default-character-set=utf8mb4` 执行 [test-ddl.sql](./test-ddl.sql)，先确认 `existing_must_be_0=0`，执行后核对 25 表存在且为空。
4. **登记（DISABLED）**：同一连接 `SET @plt_ext_operator=221546852372267008;` 后依次执行外部 API 登记（核对 344 行后 COMMIT）、沙箱验证登记（核对路由 14、Header 51、Query 6、Guard 84 后 COMMIT）。任一不符 ROLLBACK 并停止。
5. **启动并替换应用**（每次一个服务，`up -d --no-deps --no-build --pull never --wait --wait-timeout 240`，使用候选 Compose）：Gateway → Platform → BFF → Web，各自 healthy 后继续。Nginx、Risk、Vault 使用现有镜像 `start`，不重建。
6. **启用**：执行 [test-activate.sql](./test-activate.sql)，核对 44/5/2/1 后 COMMIT（新项 v1→v2，PLATFORM_ADMIN v20→v21）。等待 Gateway 快照包含新路由。
7. **部署检查**（第 5 节）。
8. **C：PayerMax**：经 `http://localhost:28080` 的真实入口运行 `configure.py PAYERMAX https://pay-gate-uat.payermax.com/aggregate-pay/api/gateway`（管理员口令只从不提交的 `private/access.md` 读取，运行目录位于 scratchpad，0700）；页面将出站地址标记沙箱；录入用户提供的 UAT 验证账号（按用户决定明文存于 test `mor_platform`）与两个用例并运行；保存去敏回执。

## 5. 部署检查

- 四个新应用与其余容器 healthy；Gateway 快照有效路由 105→149。
- 管理员经 Nginx → Gateway → BFF → Platform 登录；菜单出现“外部 API / 标准 API / 接入方”。
- 既有页面不回归：人员、角色、API 路由、审计、租户访问、风控规则集/决策流/变量列表可打开（BFF 新 + Risk 旧组合）。
- 外部 API：标准 API 与接入方列表、详情；沙箱验证：验证页签、账号、用例、运行详情。
- 合成渠道冒烟不在 test 进行（test 没有合成 PayerMax），以 C 的 UAT 运行为验收：`UAT_PAY_THEN_QUERY`、`UAT_QUERY_NOT_FOUND` 通过，未标沙箱时运行在发请求前失败；证据中检索私钥片段为 0。
- 服务替换期间 Operations 可能短暂不可用；不承诺零中断。

## 6. 恢复

- SQL 未提交时失败：同一连接 ROLLBACK，停止后续步骤。
- 已登记、未启用：登记保留（DISABLED 不暴露）；按 Web → BFF → Platform → Gateway 用 [compose-previous.yaml](./compose-previous.yaml) 逐个回退。
- 已启用后失败：执行 [test-deactivate.sql](./test-deactivate.sql)（前提 44 路由/5 资源/2 授权均为 v2 且无后续改动，核对 44/5/2/1 后 COMMIT；版本只增不减），确认快照不再暴露新入口后再回退镜像。
- 新表：旧镜像不访问这 25 张表，回退时保留；只有在确认为空且不再需要时，才经单独授权逆序 DROP。
- 验证运行、账号、用例与 PayerMax 配置属于 test 数据：回退代码不删除；如需撤销 C，经管理接口删除接入方配置。

## 7. 待批准范围

**按本清单：从 `ba36b63` 干净构建四个镜像；备份受影响配置；在 test `mor_platform` 执行 25 表 DDL、499 行登记与 52 行启用；以候选镜像替换 Gateway、Platform、BFF、Web 并启动其余现有容器，Platform 设置 `COMPENSATION_ENABLED=true`；完成部署检查；经管理接口写入 PayerMax 配置、录入 UAT 验证账号与用例并运行。失败按第 6 节恢复。** 不包含 Risk 升级、他人需求的登记或初始化数据、推送镜像、Git 推送或生产配置变更。
