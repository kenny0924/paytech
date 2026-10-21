# XXL-Job 登记：reconciliation 执行器与两个任务

阶段 6 评审稿（2026-10-21）。阶段 7 第 9 步执行，结果写入 [release.md](./release.md) 第 8 节。经 Admin API 登录后调用，不直接写 `xxl_job` 库；脚本 [scripts/xxl_reconciliation.py](./scripts/xxl_reconciliation.py)（照 `requirements/20261018/test-environment-V2部署与数据迁移/scripts/register_xxl_executors.py`）。

## 1. 现状（2026-10-21 02:38Z 只读核对）

| 项 | 结果 |
|---|---|
| `xxl_job_group` | 14 个分组（id 2–16，无 8），**没有** `reconciliation-service` |
| `xxl_job_registry` | 13 个执行器心跳，**没有** reconciliation（运行中的空壳镜像未启用执行器，Compose 也没有 `XXL_JOB_*`） |
| `xxl_job_info` | 任务 2–19；新任务预计为 20、21 |
| `xxl-job-admin` 容器 | 02:17Z 前后刚重启过（不是本线所为），healthy；阶段 7 复核时再确认 |
| `scripts/xxl_reconciliation.py plan`（只读） | `[group] reconciliation-service: MISSING` |

## 2. 前置

- 新 reconciliation 镜像（`f181a04cad2d…`）已按 [compose-candidate.yaml](./compose-candidate.yaml) 替换并 healthy，容器环境含 `XXL_JOB_EXECUTOR_ENABLED=true`、`XXL_JOB_EXECUTOR_APPNAME=reconciliation-service`、`XXL_JOB_EXECUTOR_IP=172.30.73.41`、`XXL_JOB_EXECUTOR_PORT=9999`。
- `xxl_job_registry` 出现 `EXECUTOR / reconciliation-service / http://172.30.73.41:9999/`（执行器启动后约 30 秒；分组可之后再建）。

## 3. 分组与任务（`python3 -I scripts/xxl_reconciliation.py apply`，幂等）

分组：`appname=reconciliation-service`、`title=Reconciliation`、`addressType=0`（自动注册）、`addressList` 空。

| 字段 | compensationEventScan | channelLineWaitExpiry |
|---|---|---|
| `jobDesc` | Reconciliation 本地补偿扫描（compensation_event） | Reconciliation 账单行等待期满转异常（UNMATCHED → CHANNEL_LINE_UNMATCHED） |
| `scheduleType` / `scheduleConf` | `CRON` / `*/30 * * * * ?`（每 30 秒） | `CRON` / `0 */10 * * * ?`（每 10 分钟，technique §2.4） |
| `executorRouteStrategy` | `SHARDING_BROADCAST`（同其他服务的补偿扫描） | `FIRST`（Handler 不感知分片；重复开异常由「同行已有开放异常则跳过」+ active_slot 唯一键兜底） |
| `executorBlockStrategy` | `SERIAL_EXECUTION` | `SERIAL_EXECUTION` |
| `executorTimeout` | `60` | `300` |
| `executorFailRetryCount` | `0` | `0`（整轮失败由下一轮重试） |
| `misfireStrategy` | `DO_NOTHING` | `DO_NOTHING` |
| `executorHandler` | `compensationEventScan`（Foundation `CompensationEventXxlJobHandler`） | `channelLineWaitExpiry`（`ChannelLineWaitExpiryJobHandler.HANDLER_NAME`） |
| `author` / `glueType` / `executorParam` | `code-mor` / `BEAN` / 空 | 同左 |

创建后脚本立即 `/jobinfo/start`（`ids[]`）。

## 4. 验证

1. `python3 -I scripts/xxl_reconciliation.py trigger compensationEventScan`：调度 200、执行 200（库内没有待处理补偿事件，扫描为空）。执行器未注册时返回 "registry address is empty"，脚本每 10 秒重试至 120 秒。
2. `python3 -I scripts/xxl_reconciliation.py trigger channelLineWaitExpiry`：执行 200，日志 `reconciliation channel line wait expiry: opened 0, skipped 0`（验收前没有账单行）。
3. AC-13 在验收脚本里再触发两次（第一次开 1 条 `CHANNEL_LINE_UNMATCHED`，第二次不重复），见 release.md 第 7 节。
4. 之后各等一次定时调度：`compensationEventScan` 30 秒内、`channelLineWaitExpiry` 下一个 10 分钟整点，执行结果 200。

## 5. 回滚

- 回滚 reconciliation 镜像（旧空壳不含执行器）前先 `python3 -I scripts/xxl_reconciliation.py remove`：停止并删除两个任务，再删分组。其他分组与任务不受影响。
- 只想暂停：Admin 页面停止两个任务即可。

## 6. 已知限制

- 四类对账事件当前没有订阅方（I-08，`unroutable-policy: FAIL`）：验收结束删除观察队列后，新产生的对账事件发布会失败并由 `compensationEventScan` 重试到上限，留存 FAILED，billing 接入后人工补发。mor-test 在验收后不应再产生对账事件，除非有人继续操作对账页面。
