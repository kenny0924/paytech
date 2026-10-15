# 评估结果查询与解释：mor-test 发布清单（阶段 6 评审稿）

- 日期：2026-10-15
- 目标环境：本机 Docker 中的 mor-test（Compose 项目 `mor-test`，`test-environment/compose.sh`）；数据库容器 `mor-test-mysql-1`，库 `mor_risk`
- 只换 Risk；不涉及生产、推送、Payment、Operations BFF/Web、Gateway/Nginx

## 1. test 现状（2026-10-15 只读核对）

| 项 | 现状 | 本次处理 |
|---|---|---|
| 容器 | 16 个；Risk 为 `mor-test/risk-service:release-list-20261015`（image `fcfc9457dab6`，提交 `b26e857`），healthy 12 小时 | 换为候选镜像 |
| Nacos | `risk-service` 一个健康实例 `172.30.73.25:18104` | 部署后仍恰一个 |
| `mor_risk` 结构 | 84 表，无 `t_evaluation_flow_trace` | 执行 `db/ddl.sql` |
| `mor_risk` 数据 | Operations 的支付联调基线：发布 `236518950932942848` ACTIVE（流程版本 `236518950786142209`）；4 条支付正式评估（均成功）；无进行中的影子批次与待执行补偿任务 | 不改动；这 4 条评估部署后显示“路径不可用” |
| 调用方 | Operations BFF 不调用新接口；test 未部署 Payment | 新接口无调用方，不影响现有调用 |

## 2. 制品

| 项 | 值 |
|---|---|
| 源码 | HEAD `8d26229` 的 `risk-compliance-service`（`git archive`）+ 本需求未提交改动 52 个文件（docs 2、risk-api 8、risk-decision 33、risk-test 6、risk-web 3）；差异文件 SHA-256 前缀 `e739d1b5a4ee2d21`（构建目录保留在 scratchpad，阶段 8 提交后与提交内容核对） |
| 构建 | JDK 25，`./mvnw -o -DskipTests package -pl risk-web -am` |
| jar | `risk-web-1.0.0-SNAPSHOT-exec.jar`，SHA-256 `a845559fcfb82782e8c2095e68a08b9217a2f35b3db0564f464d07f6b5746de6` |
| 镜像 | `mor-test/risk-service:evaluation-query-20261015`，image ID `sha256:727d51127e61a1e99847e86d7b822088b197cdeba3050d0b88ec84ab5ed786d3`（`test-environment/java/Dockerfile`，探针取 `test-environment/artifacts/health-probe`）；镜像内 jar 摘要已核对一致 |
| 候选 Compose | [compose-candidate.yaml](./compose-candidate.yaml)：只固定 image ID，配置沿用已提交的 Compose |
| 回退镜像 | `sha256:fcfc9457dab627439e2590ff5a67d91d182e5e02d2e610a19b61370c95a7ea15`（执行前再次记录） |
| 已有验证 | 同一代码 Risk 全量 auto 2022/2022；阶段 5 本机打包 jar 真实 HTTP 71 项；**候选镜像**在本机以容器方式连接本机库与中间件：6 秒启动，健康 200、查询接口 404/400 符合预期、无 ERROR 日志 |

## 3. SQL

| 顺序 | 文件 | 说明 |
|---|---|---|
| 1 | [../db/ddl.sql](../db/ddl.sql) | 只新增 `t_evaluation_flow_trace`；旧代码不读写该表，可在旧 Risk 运行时执行，不需要停机 |
| 验收后 | [test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql) | 删除 3 个合成支付尝试的评估相关数据；先 ROLLBACK 试运行核对行数再 COMMIT |

- **执行方式：**`docker exec -i mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 mor_risk < 文件`，口令经 `MYSQL_PWD` 从 `.env.test` 传入、不回显。
- **演练（已完成）：**在一次性 `mysql:8.4.11` 容器中载入 test `mor_risk` 的结构导出（84 表），执行 ddl 后与仓库完整 Schema（85 表）对比：列 1045 行、索引 480 行、表注释 85 行**完全一致**；再 `DROP TABLE` 回滚后与 test 原结构（列 1034 行）完全一致。清理 SQL 在另一个一次性容器中用合成数据验证：只删除目标支付尝试的评估、快照、流程轨迹、累计事件与主体，其他评估不受影响。演练容器均已删除。
- **迁移后核对：**85 表；新表 11 列、唯一索引 `uk_eval_flow_trace_step`；其余各表行数与执行前一致。

## 4. 需要你决定

- **D1 验收写入：**建议用 3 个合成支付尝试（`991015000000000001`～`003`，金额 100 / 600 / 6000，随机卡指纹与 IP）对**现有联调基线发布**做评估，分别得到放行、放行需 3DS、审核，以验证新评估写入路径、查询结论与解释；验收后用清理 SQL 删除这 3 条及其累计事件。不新建发布、不做影子运行（影子已在本机验证；在 test 做需要新建候选发布，会改动 Operations 的联调配置）。备选：只做只读验收（查询现有 4 条评估、404/400），新写入路径在 test 不验证。
- **D2 验收入口：**与以往相同，验收窗口内用本地已有镜像 `nginx:1.30.4-alpine` 起临时代理容器（宿主 `127.0.0.1:28114` → 部署后 Risk 容器的后端网络地址:18104），验收后删除；不改 test Nginx/Gateway。

## 5. 执行顺序（任一步与预期不符即停止并报告）

1. **复核：**重跑第 1 节只读查询；记录 16 个容器的 image ID、Risk 容器 `docker inspect`、Nacos 实例、`mor_risk` 各表行数。
2. **备份**（`~/Code-MoR-backups/20261015-risk-evaluation-query-test/<UTC>/`，目录 0700、文件 0600，附 SHA256SUMS，不进版本库）：`mor_risk` 全库 `mysqldump --single-transaction`、执行前行数、Risk 容器 inspect、image ID 清单。
3. **起代理、记录基线：**按 D2 起代理；读取回归接口（第 6 节）的响应结构与内容作为部署前基线。
4. **SQL：**执行 `db/ddl.sql`；按第 3 节核对。旧 Risk 继续运行。
5. **应用：**`compose.sh -f requirements/20261015/risk-评估结果查询与解释/release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait risk-service`；确认候选 image ID、healthy、Nacos 恰一个实例、启动日志无 ERROR。代理指向新容器地址（如变化则重建代理）。
6. **检查与验收：**第 6 节。
7. **清理：**按 D1 执行清理 SQL（先 ROLLBACK 再 COMMIT），核对各表行数回到第 4 步之后的状态；删除代理容器。
8. **记录：**`release/test-acceptance.md`；更新 `CURRENT_IMPLEMENTATION.md` 的 Risk 行。

## 6. 部署检查与验收

| 类别 | 内容 | 期望 |
|---|---|---|
| 健康 | readiness、启动日志、Nacos | UP；一个实例；无 ERROR |
| 回归（只读） | 结账：流程与规则集创作上下文、流程列表与详情、规则集列表与详情、旧候选发布列表、变量列表与创作上下文；支付：流程/规则集/变量列表、执行发布列表与基线发布详情、名单列表 | 部署前后响应结构一致，内容只差读取时刻 |
| 已有评估 | 4 条现有评估按 ID 与按支付尝试查询；解释 | 200；`pathAvailable=false`，规则与变量照常返回 |
| 新评估（D1） | 3 个合成支付尝试：放行、放行需 3DS、审核 | 结论与基线规则一致；查询结论、原因与输出正确；解释有路径、规则、变量；输入原值返回 |
| 错误 | 不存在的评估/业务身份、格式错误、未声明 Query | 404 / 400 |
| 日志 | 验收窗口 Risk 日志 | 无合成请求的 IP、User-Agent、卡指纹、消费者引用原值 |
| 写入范围 | 只写 3 个合成支付尝试的评估相关行 | 清理后行数回到 SQL 执行后的状态 |

## 7. 恢复

- **新镜像启动失败：**停止新容器，用回退镜像以同样方式启动（候选文件中的 image ID 换成回退 image ID；已提交 Compose 的 tag 是 `development`，不能直接用于回退）。新表留在库中不影响旧代码。
- **新镜像运行后需要回退：**直接换回回退镜像即可；新代码写入的流程轨迹留在新表，旧代码不读写。是否 `DROP TABLE t_evaluation_flow_trace` 可另行决定（删除后已写入的路径不可恢复，再次部署新代码时新评估重新开始记录）。
- **数据库整库还原**只在 SQL 执行后出现结构异常、且新镜像未处理请求时使用第 2 步备份；否则不整库还原，避免覆盖期间的其他写入。

## 8. 待批准范围

**按本清单：**D1、D2 按你的选择；备份；起临时代理；在 test `mor_risk` 执行 `db/ddl.sql`；以候选镜像替换 Risk；第 6 节检查与验收（D1 写入 3 个合成评估）；执行清理 SQL；删除代理；失败按第 7 节恢复。**不包含：**代码提交（阶段 8 另行确认）、推送、生产、其他服务变更。
