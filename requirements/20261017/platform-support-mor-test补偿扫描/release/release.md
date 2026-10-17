# mor-test Platform 补偿扫描：发布清单

阶段 6 评审稿（2026-10-17）。确认后执行阶段 7，结果写入第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次做法：** 在 mor-test 启动 XXL-Job Admin 并常驻，开启 Platform 执行器，登记 `compensationEventScan`（每 30 秒，分片广播）。然后用一个"先失败、后成功"的临时接收端，实测 Webhook 投递从"等待重试"自动变为"已送达"。镜像、代码、Gateway、Vault、其他服务都不改；不推送 Git，不碰生产。

## 1. 范围

| 项 | 内容 |
|---|---|
| `test-environment/compose.sh` | 加载 `compose.scheduler.yaml`，并加 `--profile scheduler`。之后 Admin 随 mor-test 常驻：宿主只绑 `127.0.0.1:28090`，内存上限 512m |
| `test-environment/compose.applications.yaml`（Platform） | 新增 6 个 `XXL_JOB_*` 变量，见第 3 节 |
| `xxl_job` 库 | 修改 `admin` 默认密码；登记执行器分组与扫描任务，SQL 见 [xxl-job-register.sql](./xxl-job-register.sql)（已写好，阶段 7 先试跑回滚核对） |
| `.env.test` | 新增 `MOR_XXL_JOB_ADMIN_PASSWORD`（随机 32 位，不打印）。同步 `.env.example` 与 `scripts/prepare.py` 的生成逻辑，便于重建环境 |
| `test-environment/README.md` | XXL-Job 从"可选、未启动"改为"已启动"，补充执行器与任务说明 |
| 临时项（验收后撤除） | 接收容器 `mor-test-webhook-receiver`（`nginx:1.30.4-alpine`，`172.30.73.240`）；Platform 临时放行 `http` 与 `172.30.73.240/32` |
| 不动 | 所有镜像、Gateway、Vault、Commerce、Payment、Risk、Client Console、Nginx |

## 2. 只读核对结果（2026-10-17）

| 项 | 结果 |
|---|---|
| `xxl_job` 库 | 已由早先的 MySQL 初始化建好 9 张表（含 `xxl_job_schema_version`），`xxl_job` 账号可登录。Admin 启动时的 schema-init 校验会直接通过 |
| 密钥 | `.env.test` 已有 `MOR_XXL_JOB_DB_PASSWORD`、`MOR_XXL_JOB_ACCESS_TOKEN`、`MOR_XXL_JOB_LOGIN_TOKEN_KEY`，均为 48 位且不含空白，满足执行器 Token ≥ 32 位的要求 |
| 镜像 | 本机已有 `xuxueli/xxl-job-admin:3.4.2`（`fa6ad9343f41`），不需要拉取 |
| 资源 | Docker 共 16 GB；mor-test 16 个容器合计约 5.2 GB，加 Admin 0.5 GB 余量充足 |
| Platform | 运行镜像 `efeb1d9e…`（`webhook-ops-20261017`），固定 IP `172.30.73.21`，HTTP 端口 18106，19106 未被占用。补偿执行已开启（`COMPENSATION_ENABLED=true`）；`t_compensation_event` 为 **0 行** |
| 默认管理员 | `xxl_job_user.admin` 的密码是 `123456` 的 SHA-256。3.4.2 存 SHA-256 十六进制，可用 `SHA2(...,256)` 直接改 |

## 3. Platform 执行器配置

| 变量 | 值 | 说明 |
|---|---|---|
| `XXL_JOB_EXECUTOR_ENABLED` | `"true"` | 启用后补偿组件注册 `compensationEventScan` |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://xxl-job-admin:8080/xxl-job-admin` | backend 网络内部地址 |
| `XXL_JOB_ADMIN_ACCESSTOKEN` | `${MOR_XXL_JOB_ACCESS_TOKEN}` | 与 Admin 共用 |
| `XXL_JOB_EXECUTOR_APPNAME` | `platform-support-service` | |
| `XXL_JOB_EXECUTOR_PORT` | `"19106"` | 不映射到宿主，只在 backend 网络 |
| `XXL_JOB_EXECUTOR_IP` | `172.30.73.21` | 注册给 Admin 的回调地址 |

变量名按 Spring 宽松绑定写。阶段 7 替换后，从启动日志与 Admin 注册表确认生效；绑定不到时，改用 `SPRING_APPLICATION_JSON` 合并写入。

## 4. 执行顺序（任一步不符即停止）

1. **协调与复核**：重跑第 2 节核对；确认 Platform 镜像未变、补偿表仍为 0 行；与使用 mor-test 的会话确认可以重启 Platform（两次，各约 30 秒）。这些会话包括 Client Console（code-mor-76）、Commerce test 联通、Payment（code-mor-1e）、Risk（code-mor-db）。
2. **备份**：`compose.sh`、`compose.applications.yaml`、`README.md`、`.env.test`（0600）；`xxl_job` 库全量导出；Platform 容器 inspect。存放在 `~/Code-MoR-backups/20261017-xxl-job-test/<UTC>/`，附 SHA256SUMS。
3. **启动 Admin**：改 `compose.sh` 后执行 `compose.sh up -d --no-deps --pull never --wait xxl-job-schema-init xxl-job-admin`。确认 healthy，`127.0.0.1:28090` 可访问。
4. **改默认密码并登记**：生成 `MOR_XXL_JOB_ADMIN_PASSWORD` 写入 `.env.test`；`xxl-job-register.sql` 先试跑回滚核对，再提交。内容：
   - 改 admin 密码；
   - 分组 `platform-support-service`（自动注册）；
   - BEAN 任务 `compensationEventScan`：CRON `*/30 * * * * ?`、`SHARDING_BROADCAST`、`SERIAL_EXECUTION`、超时 60 秒、失败重试 0、参数空、状态"运行"。
   
   SQL 可重复执行，已存在时不重复插入。
5. **起接收端**：起 `mor-test-webhook-receiver`。`POST /hook`：容器内存在标记文件时返回 204，否则返回 503。初始不放标记，即先失败。
6. **替换 Platform（第 1 次重启）**：Compose 加第 3 节变量和两行临时放行，`compose.sh up -d --no-deps --no-build --pull never --wait platform-support-service`。检查 healthy、启动日志无 ERROR；Admin 注册表出现 `172.30.73.21:19106`；任务调度日志成功。
7. **验收**（合成租户，同 Webhook 投递验收的 fixture）：
   - 运营接口建端点 `http://172.30.73.240/hook`，以 `commerce-service` 提交 1 个 `order.paid`；
   - 第 1 次尝试 503，投递进入"等待重试"；
   - 放入标记文件，等下一次到期加最多 30 秒，投递自动变为"已送达"，尝试次数 2；
   - Admin 调度日志里扫描执行成功；
   - Platform、Admin 日志无 ERROR、无密钥明文。
8. **收尾（第 2 次重启）**：撤掉两行放行，重启 Platform（执行器变量保留），确认 http 地址保存被拒 400；删除接收容器；按 [Webhook 投递验收清理](../../../20261015/platform-support-Webhook投递/release/acceptance-cleanup.sql) 同款 SQL 清理合成租户及其 Webhook 数据与补偿任务。
9. **记录**：写入第 6 节，更新 README、交付记录与台账。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| Admin 起不来或不健康 | 停掉 `xxl-job-admin`，`compose.sh` 恢复备份；Platform 未动，没有影响 |
| Platform 替换后不健康或执行器配置报错 | 恢复 `compose.applications.yaml` 备份，用原镜像 `efeb1d9e…` 重新 `up`。执行器配置不合法会导致启动失败，补偿与 Webhook 受理会中断约 30 秒 |
| 扫描异常（误处理其他任务等） | Admin 停止任务，或删除任务后保留执行器；补偿表现为 0 行，影响面仅限本次验收数据 |
| 数据 | `xxl_job` 按备份恢复；合成租户按清理 SQL 处理 |

## 6. 执行记录（2026-10-17 阶段 7，用户"可以"）

**与清单的偏差：** 执行前得知 Client API 测试环境联通会话（code-mor-76，[其需求](../../commerce-ClientAPI测试环境联通/)）已启动 XXL-Job Admin，并在 `98ba37c` 提交了 Platform 执行器配置；双方约定只重启一次，由它执行。因此：
- Admin 用根路径（Foundation 执行器不接受带 context path 的 Admin 地址），执行器端口为 9999，不用清单里的 `/xxl-job-admin` 与 19106；
- Platform 镜像保持当时运行的 `629cccf9…`（Toss Pay 发布，含 Webhook 管理提交），不是清单写的 `efeb1d9e…`；
- 两行临时放行由对方放在它需求目录的临时覆盖文件里，不进入提交的 Compose；
- `compose.sh` 未改动，Admin 常驻方式与对方确认后另行处理；
- Commerce 的执行器分组与任务由对方登记。

| 步骤 | 结果 |
|---|---|
| 1 协调 | Payment（1e）、Risk（db）、Toss Pay（ee）、ff 均未使用 mor-test Platform；ee 提醒镜像已是 `629cccf9`；76 合并重启 |
| 2 备份 | `~/Code-MoR-backups/20261017-xxl-job-test/20261017T054933Z/`（`xxl_job` 全量导出、`compose*.yaml`、`compose.sh`、README、`.env.test`、Platform inspect，SHA256SUMS） |
| 3 Admin | 由 76 启动（healthy，根路径） |
| 4 密码与登记 | `.env.test` 新增 `MOR_XXL_JOB_ADMIN_PASSWORD`（随机 32 位）；[xxl-job-register.sql](./xxl-job-register.sql) 先试跑回滚核对 1/1/1，再于 05:49:45Z 提交。默认密码已失效；分组 `platform-support-service` id 2；任务 `compensationEventScan` 状态为运行 |
| 5 接收端 | `mor-test-webhook-receiver`（`nginx:1.30.4-alpine`，`172.30.73.240`，内存 64m），`/hook` 初始返回 503 |
| 6 重启 Platform | 76 于 05:54Z 执行，镜像不变，healthy，0 ERROR；执行器 `172.30.73.21:9999` 自动注册；临时放行 `https,http` 与 `172.30.73.240/32` 生效 |
| 7 验收 | [test_retry.py](../verification/test_retry.py) **9/9**：执行器已注册；http 端点可登记；`commerce-service` 提交 `order.paid` 返回 204；首次 503 后投递进入"等待重试"；放入标记后 103 秒内由扫描自动重发并送达（尝试 2 次）；扫描任务调度 4 次全部成功；接收端日志先 503 后 204；Platform 无 ERROR；合成数据已清理。首次运行 8/9，唯一失败是脚本把受理接口状态写成 200/201/202（实际为 204），修正后重跑 |
| 8 收尾 | 经 76 同意，05:58:40Z 只用其 [候选文件](../../commerce-ClientAPI测试环境联通/release/compose-candidate-20261017.yaml) 重建 Platform，05:59:08Z healthy，镜像不变，启动无 ERROR；`WEBHOOK_TRUSTED_NETWORKS` 回到 `172.30.73.31/32`，恢复只允许 https，http 地址保存实测 400（合成租户随即清理为 0）；执行器重新注册。删除 `mor-test-webhook-receiver` |

**观察：** 重启期间 05:59:00、05:59:30 两次 Platform 扫描调度失败，原因是"registry address is empty"：执行器关闭时会注销，Admin 每 30 秒刷新一次分组地址。06:00:00 起恢复成功。Platform 或 Commerce 每次重启，预计会有约 1 分钟的调度失败；失败的扫描由下一周期补上，任务本身不受影响。

**当前 mor-test：** XXL-Job Admin 运行中（由 76 启动；76 已让 `compose.sh` 默认加载 `compose.scheduler.yaml` 与 `scheduler` profile，Admin 设为 `restart: unless-stopped`（运行中容器经 `docker update` 生效），README 改为常驻、根路径、密码取 `MOR_XXL_JOB_ADMIN_PASSWORD`；随 76 的后续本地提交。副作用：不带服务名的 `./compose.sh up -d` 也会启动 `xxl-job-schema-init` 与 `xxl-job-admin`）；默认管理员密码已改；`platform-support-service`（分组 2）与 `commerce-service`（分组 3，76 登记）每 30 秒扫描一次；Platform 镜像 `629cccf9…`，无临时放行。
