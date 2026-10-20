# counterparty 改名：mor-test 发布记录

阶段 6–7（2026-10-20，用户授权夜间连续推进至 test）。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 停 consumer-service，`mor_consumer` 迁到 `mor_counterparty`（旧库保留），启动 counterparty-service，acquiring-service 与 card-service 换成调用方改名后的镜像，XXL 登记 counterparty-service 执行器与补偿扫描任务。Gateway、Platform、Nginx 与其余服务不动；无 Gateway Route 变化（本需求不开放 Customer 路由）。**

## 1. 源码与镜像

- 源码：分支 `feature/counterparty-rename` 提交 `ae6dadd5`（基于 `0ecc5713`），干净 worktree `Code-MoR-counterparty` 构建；合入 main 时 rebase 到 `e73764d1` 得 `c661e9fc`，期间 main 的新提交未触及三个服务及其依赖的 API / foundation 模块，镜像内容与 `c661e9fc` 一致。
- 构建：JDK 25 离线 `-DskipTests package`，`test-environment/java/Dockerfile` + 健康探针，tag `counterparty-20261020`。

| 服务 | 新镜像 ID | JAR SHA-256 | 回退镜像 ID |
|---|---|---|---|
| counterparty-service | `sha256:c71436804e2f3400e5f5b078216821d4de5473965340df46a21ba2d27df31a20` | `fe1e3d9c62a669a510954940bb82479e3d8a9dc78009ba7ee8cf76ab1e64763a` | —（回退为启动旧 consumer-service `sha256:ea962e948ffeb0aec1f70413c52b3749a2bf1ca3dce169a527e4dbb6e9af3179`） |
| acquiring-service | `sha256:03729ba82e28d0776570c4ff12864dda6380ac49bc1deea785aaf426e68dae91` | `225936725982042c70bdc26e00ff980f5755c14a95e2a2ed0104da0c61a8bae7` | `sha256:4cece80d1e76b038b152bae4ea6c9116f2b18c2a0438c8a813e8fc0010ad87ef` |
| card-service | `sha256:68d676840ac2d5331c49637f9522c9f714548bed6aec60d2812318d5823d77c0` | `fe6a2cd8ce298d4810767bc901bc63b6321733d1f5e0f6155675121d3c1c9a0a` | `sha256:f8fa217b84794b10b31791496c0d0d6280dd3edb3c3c4e0767988a0a4d7b0453` |

顺带发布：acquiring、card 原运行镜像为 2026-10-18 15:55Z / 16:14Z 构建，新镜像同时带上 main 上此后已提交的改动（Processor 模板迁移 `45b79b76` / `a05888f8`、messaging 拓扑恢复 `339db7b8` / `8d123532` 等）。

JAR 核对：acquiring 打包 `paytech-counterparty-api`，`application.yml` 为 `lb://counterparty-service`；card-api `CardApiConstants` 含 `counterparty-service`；counterparty JAR 含 `CounterpartyApplication`。

## 2. 执行（2026-10-20 03:19Z—03:24Z）

1. 只读核对：`mor_consumer` `t_customer` 0 行、`t_compensation_event` 0 行；XXL group 8 `consumer-service`、job 8 运行中；三个容器 healthy（03:06Z 由另一会话随整个 mor-test 原样重启，镜像未变）。已通知同时在 mor-test 发布的 commercial、ledger 会话，二者确认不冲突。
2. `.env.test` 追加 `MOR_COUNTERPARTY_DB_PASSWORD`（随机 48 位十六进制，未打印）。
3. 备份 `~/Code-MoR-backups/20261020T031956Z-counterparty-test/`（0700 / 0600，`SHA256SUMS`）：`mor_consumer` 整库、XXL `xxl_job_group` / `xxl_job_info` / `xxl_job_registry`、三个容器 `docker inspect`、`HEAD:test-environment/compose.applications.yaml`。
4. `compose.sh stop consumer-service`。
5. SQL（root）：[01-create](../db/mor-test/01-create.sql) → [02-schema-and-copy](../db/mor-test/02-schema-and-copy.sql) → [03-verify](../db/mor-test/03-verify.sql)：两表 0 = 0；列数 10 / 15；`counterparty_app` 仅本库 SELECT / INSERT / UPDATE / DELETE。
6. XXL：新增 group 15 `counterparty-service`、job 18 `compensationEventScan`（启用）；job 8 停用。
7. [compose-release.sh](./compose-release.sh)（分支 compose 定义 + 主工作树 project-directory 与 `.env.test`）+ [compose-candidate.yaml](./compose-candidate.yaml)：依次 `up -d --no-deps --no-build --pull never --wait` counterparty-service、acquiring-service、card-service，全部 healthy。代码合入 main 后，主工作树 `compose.sh` 已与之等价（候选文件只固定镜像 ID）。

## 3. 验收

| 项 | 结果 |
|---|---|
| counterparty 私网 `POST /counterparty/v1/customers/resolve` | 首次 `created=true`，重复 `created=false`，同一 `customerId` |
| `GET /counterparty/v1/customers/{id}` | 200 |
| `GET /counterparty/v1/card-saving-eligibility` | 200 `SETTING_DISABLED`（counterparty → client-master 调用成功，非 `SETTING_UNAVAILABLE`） |
| 旧主机名 `consumer-service` | 不可解析（容器已停） |
| acquiring Client API 建单带 `consumerReference`（私网直连 acquiring，带 Gateway 头） | 201；`mor_acquiring.t_commerce_order.customer_id` = `mor_counterparty.t_customer` 新建行，证明 acquiring 经服务发现调用 counterparty-service |
| counterparty Client API `GET /customers/{no}` 与 `/stored-cards` | 200；后者经 counterparty → card（调用方 `counterparty-service`）返回空列表 |
| XXL | registry `counterparty-service http://172.30.73.33:9999/`；job 18 每 30 秒触发，trigger / handle 均 200 |
| 日志 | 三个服务自替换起 0 条 ERROR |

验收数据保留在 mor-test：`mor_counterparty.t_customer` 2 行（`smoke-cp-90af152f`、`smoke-acq-628df2f9`）与 `mor_acquiring` 订单 `ord_3KwRKL0h98iFU3CjrP6swwNpmSB8KH`（`client_order_id = cp-smoke-c196190e`，未支付）。

## 4. 恢复

| 情况 | 处理 |
|---|---|
| counterparty-service 异常 | 停 counterparty-service；`docker start mor-test-consumer-service-1`（容器已停未删，配置与镜像不变）或用备份中的 `compose.applications.yaml` 启动；同时把 acquiring、card 回退到上表旧镜像（新 acquiring 只认 `counterparty-service`）；XXL 恢复 job 8 启用、job 18 停用 |
| 需要撤销新库 | 先停 counterparty-service，再执行 [99-rollback.sql](../db/mor-test/99-rollback.sql)；`mor_consumer` 未改动 |

## 5. 遗留

- `mor-test-consumer-service-1` 容器保持停止未删除（回退用），`mor_consumer` / `consumer_app` 与 XXL group 8 / job 8（已停用）保留；删除时机另行确认。合入 main 后该容器在 Compose 中成为孤儿，`compose up` 可能提示 orphan，可忽略或带 `--remove-orphans` 时注意它会被删除。
