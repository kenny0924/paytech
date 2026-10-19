# XXL-Job 登记：channel-connector 执行器与渠道成本到期切换任务

阶段 6 评审稿（2026-10-19，任务卡 F）。阶段 7 第 7 步执行，结果写入 [release.md](./release.md) 第 6 节。登记方式照前序 account 任务 14（`accountBalanceMaturity`）与 `requirements/20261018/test-environment-V2部署与数据迁移/scripts/register_xxl_executors.py`：经 Admin API 登录后调用，不直接写 `xxl_job` 库。

## 1. 现状（2026-10-19 19:18Z 只读核对）

| 项 | 结果 |
|---|---|
| `xxl_job_group` | 11 个分组（id 2—12），**没有** `channel-connector-service` |
| `xxl_job_registry` | 10 个执行器心跳，**没有** channel-connector（运行中的旧镜像未启用执行器，Compose 也没有 `XXL_JOB_*`） |
| `xxl_job_info` | 任务 2—14；最大 id 14（account `accountBalanceMaturity`），新任务预计为 15 |
| `xxl-job-admin` 容器 | 19:17:06Z 刚重启过（不是本线所为），healthy；阶段 7 复核时再确认一次 |

## 2. 前置

- 新 channel-connector 镜像（`194961f11db5…`）已按 [compose-candidate.yaml](./compose-candidate.yaml) 替换并 healthy。
- 容器环境含 `XXL_JOB_EXECUTOR_ENABLED=true`、`XXL_JOB_EXECUTOR_APPNAME=channel-connector-service`、`XXL_JOB_EXECUTOR_IP=172.30.73.36`、`XXL_JOB_EXECUTOR_PORT=9999`（工作区 `compose.applications.yaml` 的 channel-connector-service 段）。
- `xxl_job_registry` 出现 `EXECUTOR / channel-connector-service / http://172.30.73.36:9999/` 心跳（执行器启动后约 30 秒内）。心跳与分组无关，分组可在之后创建。

## 3. 执行器分组

`POST /jobgroup/insert`（Admin `http://127.0.0.1:28090`，先 `/auth/doLogin`，用户 `admin`，口令取 `.env.test` 的 `MOR_XXL_JOB_ADMIN_PASSWORD`，不回显）：

| 字段 | 值 |
|---|---|
| `appname` | `channel-connector-service` |
| `title` | `Channel Connector` |
| `addressType` | `0`（自动注册） |
| `addressList` | 空 |

核对：`/jobgroup/pageList` 中出现该分组，`address_list` 在下一个注册周期变为 `http://172.30.73.36:9999/`。

## 4. 任务

`POST /jobinfo/insert`，然后 `/jobinfo/start`：

| 字段 | 值 | 说明 |
|---|---|---|
| `jobGroup` | 第 3 节分组 id | |
| `jobDesc` | `Channel Connector 渠道成本版本到期切换（APPROVED → 当前版本）` | |
| `author` | `code-mor` | 同其他任务 |
| `alarmEmail` | 空 | |
| `scheduleType` / `scheduleConf` | `CRON` / `0 */5 * * * ?` | 每 5 分钟；生效时间精度要求低于 5 分钟的场景另议 |
| `glueType` / `glueRemark` / `glueSource` | `BEAN` / `BEAN` / 空 | |
| `executorHandler` | `channelCostVersionActivation` | `ChannelCostVersionActivationJobHandler.HANDLER_NAME` |
| `executorParam` | 空 | Handler 不读参数，按当前 UTC 时间执行 |
| `executorRouteStrategy` | `FIRST` | Handler 不感知分片；每个方案在 Processor 内按 planCode 加 Redis 锁，多实例时也只需一个执行 |
| `executorBlockStrategy` | `SERIAL_EXECUTION` | |
| `executorTimeout` | `300` | 同任务 11、14 |
| `executorFailRetryCount` | `0` | 失败的版本保持 APPROVED，由下一轮重试 |
| `misfireStrategy` | `DO_NOTHING` | |
| `childJobId` | 空 | |

## 5. 验证

1. `/jobinfo/trigger`（`id` = 新任务，`executorParam` 空）手动触发一次。
2. 期望：调度结果 200、执行结果 200，执行日志为 `channel cost version activation: activated 0, skipped 0, failed 0`（mor-test 没有任何渠道成本方案，切换数必为 0）。
3. 之后等一次定时调度（下一个 5 分钟整点），执行结果 200。
4. 验收（阶段 7 第 9 步）中的版本因生效时间已过在批准时直接 ACTIVE，不经本任务；验收期间定时调度若命中，结果仍应为 0 切换。

## 6. 不登记

- `compensationEventScan`（channel-connector 分组）：`compensation-event-catalog.yaml` 为空，本服务没有补偿事件类型，扫描无事可做。不登记；将来出现补偿事件类型时与其需求一起登记。

## 7. 回退

- 回退 channel-connector 镜像（旧镜像不含执行器与该 Handler）前：`/jobinfo/stop` 停止新任务，避免调度失败告警。
- 需要撤销登记时：`/jobinfo/stop` → `/jobinfo/remove` → `/jobgroup/remove`（分组下无任务才可删除）。其他分组与任务不受影响。

## 8. 已知限制

- 到期版本的生效时间不晚于当前版本时，Job 会把它计为失败、保持 APPROVED，并每轮 `handleFail`；目前没有作废 APPROVED 版本的入口，按人工处理（`tasks/B-report.md` 第 6 节第 5 项，`delivery.md` 遗留项）。mor-test 当前无方案，不会触发。
