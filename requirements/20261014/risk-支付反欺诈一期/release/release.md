# 支付反欺诈一期：mor-test 发布清单（2026-10-15 用户确认，已执行，见[部署与验收记录](./test-acceptance.md)）

- 日期：2026-10-15（阶段 6，未执行任何 test 变更）
- 环境：`mor-test`（本机 Docker 当前 context，Compose 项目 `mor-test`，`test-environment/compose.sh`）；数据库为 test MySQL 容器 `mor-test-mysql-1` 内的 `mor_risk`（不是本机开发库 `127.0.0.1:13306/mor_risk`，也不是生产库）
- 只替换 Risk；Gateway、Platform、CCT、Operations BFF/Web、Vault、Nginx 不变

## 1. test 现状（2026-10-15 只读核对）

| 项 | 现状 | 本次处理 |
|---|---|---|
| 容器 | 16 个全部 healthy；Risk 为 `mor-test/risk-service:client-onboarding-20261014`（image `9f9f0141be7c`，提交 `7e9eddc` 构建，早于本需求全部提交） | 换为候选镜像 |
| `mor_risk` 结构 | 81 表。与本需求目标的差异恰为 DDL 要改的 9 张表（3 张新表、6 张改列/重建）；争议 4 表已是 `channel_*`，与正式 Schema 一致 | 执行 `db/ddl.sql` |
| `mor_risk` 数据 | 只有结账决策点 210001（33 个点版本、28 个规则集、2 个流程、10 个发布均为 DRAFT/VALIDATING、17 个变量、3 个拒绝原因）与尽调数据；无支付决策点、动作 240006 起、`PAY_` 变量；交易评估快照、名单、成员、累计事件、Feature、影子批次、`DECISION_SHADOW_RUN` 补偿任务均为 0 行 | 执行 `db/dml.sql` |
| Risk 配置 | 新代码新增必填 `RISK_DECISION_ENVIRONMENT`，test Compose 没有；不补则新镜像启动失败 | 设为 `TEST` |
| 调用方 | test 未部署 Payment；Operations BFF 不调用统一评估、名单与执行发布接口 | 统一评估接口在 test 暂无调用方 |

## 2. 需要你先决定的 3 项

- **D1 验收数据：** 建议验收后用 [test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql) 清除验收创建的支付配置与评估数据，test 只保留初始化数据（与以往发布一致）。备选：保留一套生效的支付规则与流程，供后续 Payment 联调使用。
- **D2 Compose 改动：** 建议把 `RISK_DECISION_ENVIRONMENT: TEST` 同时写入 `test-environment/compose.applications.yaml` 的 `risk-service`（共享文件，只加这一行），之后常规部署不依赖候选文件；随发布记录单独提交，不推送。
- **D3 验收入口：** Risk 只在 test 后端网络可达，没有对外路由。建议验收窗口内用本地已有镜像 `nginx:1.30.4-alpine` 起一个临时代理容器（[配置](./acceptance-proxy.conf)，宿主 `127.0.0.1:28114` → `172.30.73.25:18104`），验收后删除；不改 test Nginx/Gateway，不新增路由。

## 3. 制品

| 项 | 值 |
|---|---|
| 源码 | 提交 `06025a2`，`git archive` 导出 `risk-compliance-service` 干净构建（JDK 25，`./mvnw -o -DskipTests package -pl risk-web -am`） |
| jar | `risk-web-1.0.0-SNAPSHOT-exec.jar`，SHA-256 `708a457004b2c0555683448ccb5af218a484a1969a610674f902ba18defe3026` |
| 镜像 | `mor-test/risk-service:payment-fraud-20261015`，image ID `sha256:e0b7a8d484eb8e70042ed029046d3f1db9a494fb2e891cf8f1051dbdd32dccef`（`test-environment/java/Dockerfile`，探针取 `test-environment/artifacts/health-probe`）；镜像内 jar 摘要已核对一致 |
| 候选 Compose | [compose-candidate.yaml](./compose-candidate.yaml)：固定 image ID + `RISK_DECISION_ENVIRONMENT: TEST` |
| 回退镜像 | `sha256:9f9f0141be7c8dc651fe284fbb0932e14168376d3b39ccfed1b0b964ec1f3eea`（执行前再次记录） |
| 已有验证 | 该提交 Risk 全量 auto 1992/1992；阶段 5 本机完整链路 61 项；**候选镜像本身**在本机以容器方式连本机库与中间件重跑同一验收 61 项全部通过，日志无受限原值，数据已恢复基线 |

## 4. SQL

| 顺序 | 文件 | 说明 |
|---|---|---|
| 1 | [db/ddl.sql](../db/ddl.sql) | 重建 `t_transaction_risk_assessment`（空表）、新增 3 表、改 5 表：非空新列只加在空表（名单、名单成员）上，其余新列可空，旧数据不变；DDL 隐式提交 |
| 2 | [db/dml.sql](../db/dml.sql) | 单事务插入支付决策点 210002、点版本 220002、动作 240006～240008、13 个支付变量与 3 个成员；文末修订段按动作编码过滤，对新库无操作 |
| 验收后 | [test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql) | 按 D1；先 ROLLBACK 试运行核对行数再 COMMIT |
| 回退时 | [test-rollback.sql](./test-rollback.sql) | 见第 7 节 |

- **执行方式：** `docker exec -i mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 mor_risk < 文件`，口令经 `MYSQL_PWD` 从 `.env.test` 传入、不回显。必须用 utf8mb4 客户端字符集（Vault 发布曾因 latin1 写坏中文注释）。
- **演练（已完成）：** 在一次性 `mysql:8.4.11` 容器中载入 test `mor_risk` 的只读结构导出，依次执行 ddl → dml：结果与仓库正式 Schema（84 表）的列（含顺序、类型、默认值、注释）、索引、表注释**逐项一致**；DML 行数符合预期。再执行清理 + 回退 SQL：结构与发布前 81 表**逐项一致**。演练容器已删除。
- **迁移后核对：** 84 表；按上述三项对比 test 与正式 Schema 无差异；`t_action_version` 240006～240008、`t_decision_point` 210002、`t_decision_point_version` 220002（timeout 500，FROZEN）存在；结账与尽调表行数与执行前一致。

## 5. 执行顺序（任一步与预期不符即停止并报告）

1. **复核**：重跑第 1 节只读查询确认未漂移；记录 16 个容器的 image ID、Risk 容器完整配置（`docker inspect`）与 Nacos 中 `risk-service` 实例。
2. **备份**（`~/Code-MoR-backups/20261015-risk-payment-fraud-test/<UTC>/`，目录 0700、文件 0600，附 SHA256SUMS）：`mor_risk` 全库 `mysqldump --single-transaction`、执行前各表行数、`compose.applications.yaml` 副本、Risk 容器 inspect。备份不进入版本库。
3. **停 Risk**：`compose.sh stop risk-service`，确认 `mor_risk` 无 `risk_app` 连接。停机窗口内 CCT 入驻登记由其本地补偿任务重试，运营后台风控页面与入驻结果查询暂不可用；预计 1～2 分钟。
4. **数据库**：ddl.sql → 核对 84 表 → dml.sql → 第 4 节迁移后核对。
5. **配置**：按 D2 修改 `compose.applications.yaml`。
6. **应用**：`compose.sh -f release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait risk-service`；确认容器使用候选 image ID、healthy、Nacos 中 `risk-service` 恰一个实例。
7. **检查与验收**：第 6 节。
8. **清理**：按 D1 执行清理 SQL 并核对行数回到第 4 步之后的状态；删除临时代理容器。

## 6. 部署检查与验收

| 类别 | 内容 | 期望 |
|---|---|---|
| 健康 | readiness、启动日志无异常；Nacos 一个实例 | UP |
| 回归（只读，经临时代理） | 结账规则集列表、版本历史、发布引用；结账流程列表；旧候选发布列表；入驻材料要求 | 200，内容与执行前一致 |
| 业务验收 | 阶段 5 验收驱动指向 `127.0.0.1:28114`：未配置 503 → 名单/变量/规则集/流程 → 发布生效 → 放行、放行需 3DS、审核、拒绝 → 卡高频拒绝 → 重放/409/422/400 → 影子运行与替代 | 61 项全部通过，证据写入 `release/test-acceptance.md` |
| 在用保护 | 删除被生效发布引用的规则集 | 409 `RISK_RULE_SET_IN_USE` |
| 日志 | 验收窗口 Risk 日志 | 无 IP、User-Agent、卡指纹、消费者引用原值 |
| 写入范围 | 只写支付决策点下的配置与评估、验收拒绝原因（`ACCEPT_REASON_` 前缀）；不改结账、尽调、争议数据 | 清理后核对 |

## 7. 恢复

- **新镜像启动失败（SQL 已执行）**：先修正配置重试一次；仍失败则停止新容器，按下条回退。
- **应用回退**：旧镜像不认识支付决策点、Feature 变量与新列，不能直接在迁移后的库上启动。顺序：停新 Risk →（若新镜像运行过）执行清理 SQL → 数据库回退 → 用记录的旧 image ID 启动并恢复 `compose.applications.yaml`。
- **数据库回退二选一：**
  - 新镜像**从未处理请求**、停机期间无写入：用第 2 步备份整库还原 `mor_risk`。
  - 新镜像**运行过**（可能已有 CCT 入驻等其他写入）：不整库还原，执行 [test-rollback.sql](./test-rollback.sql)（先跑第 0 段核对全为 0）。该脚本已在演练中验证可回到发布前结构；它会删除支付初始化数据与新表，验收配置须先清理。
- **不可逆项**：`t_transaction_risk_assessment` 按新结构重建（发布前为空，无数据损失）；回退脚本重建旧结构同样只适用于空表。

## 8. 待批准范围

**按本清单：** D1～D3 按你的选择；备份；停 Risk；在 test `mor_risk` 执行 `db/ddl.sql`、`db/dml.sql`；修改 `compose.applications.yaml`（D2）；以候选镜像启动 Risk；起临时代理容器做第 6 节检查与验收（写入支付验收数据）；按 D1 执行清理 SQL；删除代理；失败按第 7 节恢复。**不包含：** 推送、生产、Payment 接入、Operations BFF/Web 变更。
