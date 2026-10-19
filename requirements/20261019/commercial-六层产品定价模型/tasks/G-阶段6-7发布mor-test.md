# 任务卡 G · 阶段 6 发布准备 + 阶段 7 mor-test 部署与验收

> 用户已委托夜间自动推进。执行者 Opus 5.5 agent。基线 = main `cf39a1ee`。参考：[release/release.md](../release/release.md)（本需求草案）、[release/compose-commercial-env.yaml](../release/compose-commercial-env.yaml)、[db/commercial/ddl.sql](../db/commercial/ddl.sql)、[db/commercial/dml.sql](../db/commercial/dml.sql)、[db/platform-support-service/registration-dml.sql](../db/platform-support-service/registration-dml.sql)、[activation-local.sql](../db/platform-support-service/activation-local.sql)；前例：`requirements/20261018/operations-console-财务基础资料与账本余额查询/release/`（release.md 第 4–6 节、compose-candidate.yaml、test-activate.sql）、`requirements/20261019/billing-计费事实模型重构与渠道成本迁出/release/`（xxl-job-channel-cost-activation.md、release.md 2.4）。

## 边界（硬约束）

- 只动 mor-test 的 `commercial-service`、`operations-console-bff`、`operations-console-web` 三个容器与 `mor_commercial`、`mor_platform` 两库；不碰 billing / channel-connector / account / ledger / acquiring / card / consumer / counterparty / Gateway / Platform / Nginx / RabbitMQ / Redis / MySQL / Nacos / XXL 容器本身。
- 不修改 `test-environment/compose.applications.yaml`（其他会话今晚也在改）；环境变量与 depends_on 写在 `release/compose-candidate.yaml` 覆盖文件里，用 `compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait <service>`（compose.sh 已含基础文件，只传覆盖文件）。
- 镜像钉 sha256；发布后 `docker inspect` 核对运行镜像 ID，并核对容器内 JAR 含新类（如 `jar tf` 查 `PricingProfileMapper`）。
- 任何 mysql root 命令若被权限分类器拒绝：不要绕过，把准确命令写进报告并停在安全点；主会话来执行后再通知你继续。
- 不推送、不建 PR；本地提交由主会话做。

## 阶段 6（只读核对与构建）

1. 只读核对并记入 release.md 第 2 节：三容器当前镜像 ID；`mor_commercial` 14 张旧表全空；`mor_platform` 无 95 块行；`docker ps` 全部 healthy；XXL Admin（`http://127.0.0.1:28090`）无 commercial-service 执行器。
2. 干净构建：`git worktree add /Users/kenny/Desktop/Workspace/Code-MoR-release cf39a1ee`；JDK 25 离线 `./mvnw -B -o -DskipTests -pl commercial/paytech-commercial-app,operations-console-bff/paytech-operations-console-bff-app -am clean package`；Web `npm ci && npm run build`；把 exec JAR 复制到 `test-environment/artifacts/commercial-service.jar`、`operations-console-bff.jar`（先备份原 artifacts 同名文件），`compose.sh build commercial-service operations-console-bff operations-console-web`；记录三镜像 sha256 到 `release/compose-candidate.yaml`（commercial-service 块同时含 compose-commercial-env.yaml 的 environment 与 depends_on rabbitmq / redis service_healthy）。
3. 写 `release/test-activate.sql` 与 `release/test-deactivate.sql`（从 activation-local.sql 改为 mor-test 的角色 / 账号：参照前例的 PLATFORM_ADMIN 角色绑定与数量核对）。

## 阶段 7（执行顺序，任一步不符即停）

1. 备份到 `~/Code-MoR-backups/<UTC ts>-commercial-test/`：`mysqldump mor_commercial`；`mor_platform` 的 t_permission / t_role_permission / t_console_resource / t_gateway_route / t_gateway_route_header / t_gateway_route_query_param / t_gateway_guard_rule / t_role；`compose.applications.yaml` 副本；三容器镜像 ID 与 `docker inspect` 环境。
2. 停 `commercial-service`；执行 `db/commercial/ddl.sql`（先 `--execute` 试跑在事务内不可行时直接执行，表为空）→ 核对 21 张表；执行 `db/commercial/dml.sql` → 核对 7 / 11 / 21。
3. `mor_platform`：注册块 95 先事务内试跑 ROLLBACK 核对 5 / 5 / 8 / 24 / 128 / 5 / 144，再提交。
4. 依次 `up` commercial-service → operations-console-bff → operations-console-web（candidate 覆盖），各自 healthy、日志无 ERROR；commercial 日志确认 RabbitMQ / Redis 连接、XXL 执行器注册。
5. XXL Admin：新建执行器分组 `commercial-service`（自动注册）与任务 `commercialTermVersionActivation`（每 5 分钟、FIRST、单机串行、超时 300 秒），方法照 billing 的 xxl 文档。
6. 启用：`test-activate.sql` 试跑回滚核对后提交；等 Gateway 快照（5 秒）。
7. 验收（经运营入口 `http://localhost:28080`，`E2E_TARGET=test` 风格；账号按前例 bootstrap 的运营管理员）：
   - 商务 BFF：登录后 GET 画像列表 200；创建画像 → 激活；创建 CLIENT 合同 → submit → 两方 acceptance → activate；PUT products；POST capability（CARD_VISA AED/USD）；建 Plan → Version → PUT prices（AED / USD）→ submit（账号 A）→ approve（账号 B，需第二个有 APPROVE 权限的账号，若无则在 test-activate 中为第二账号授予并记录）→ schedule → activate；GET versions/{id} 聚合 200。记录每步状态码。
   - commercial 直连（一次性探针容器或 `docker exec` curl）：`/commercial-prices/v1/resolutions` 命中一次；`/actuator/health` UP。
   - Web：`index.html` 引用新资源哈希；商务三页 GET 200。
   - 其他运营页面只读回归：财务、Webhook、风控等页面各一次 GET，无 ≥ 400。
   - 结果写 `verification/stage7-acceptance.md`，截图（如有）放 `assets/stage7/`。
8. 清理验收合成数据（商务库：按 ID 删除本次创建的行，先 SELECT 核对再 DELETE，写 `verification/acceptance-cleanup-test.sql` 并执行；Platform 审计记录不回滚）。
9. 记录：release.md 第 6 节执行记录（时间、命令摘要、核对数、镜像 sha256）；删除构建 worktree 与 artifacts 中本次 JAR 副本（恢复备份的原文件）。

## 交付

release.md 第 2、6 节；compose-candidate.yaml；test-activate / deactivate.sql；verification/stage7-acceptance.md；给主会话的简报：镜像 sha256 三个、SQL 核对数、验收计数、遗留与任何被拒绝的命令。
