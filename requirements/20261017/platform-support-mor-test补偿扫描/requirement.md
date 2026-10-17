# mor-test Platform 补偿扫描需求

> 状态：阶段 1 已确认（2026-10-17，全部按建议）。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

[Client Webhook 投递](../../20261015/platform-support-Webhook投递/requirement.md)与 [Operations Webhook 管理](../../20261016/operations-console-Webhook管理/requirement.md)都留下同一个遗留项：mor-test 没有配置补偿扫描调度，进入"等待重试"的投递不会自动重发。2026-10-16 test 验收时实测，到期 2.5 分钟后仍未重试，见 [发布记录](../../20261015/platform-support-Webhook投递/release/release.md)。2026-10-17 用户选择"先接 Platform"。

当前事实（2026-10-17，只读核对）：

| 项 | 现状 |
|---|---|
| 补偿组件 | 提交后立即执行（afterCommit 唤醒）。失败或唤醒丢失的任务（`ERROR` 到期、`PROCESSING` 租约过期）只能由 XXL-Job 任务 `compensationEventScan` 周期扫描恢复。组件没有进程内定时器（[补偿指南](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)） |
| Platform 执行器 | `paytech-xxl-job-starter` 已经在依赖里，但 `xxl.job.executor.enabled` 没开，扫描 Handler 没有注册。开启只需配置：Admin 地址、≥32 位 Access Token、`app-name`、显式端口，代码不用改 |
| mor-test XXL-Job | `compose.scheduler.yaml` 里有 Admin（3.4.2，amd64 镜像，内存上限 512m，宿主只绑 `127.0.0.1:28090`），但 `compose.sh` 不加载这个文件，从未启动。`xxl_job` 库和账号已随 MySQL 初始化；`.env.test` 里已有 3 个 48 位的 XXL 密钥。没有执行器分组和任务 |
| Platform 补偿积压 | mor-test `mor_platform.t_compensation_event` 现在是 0 行，开启扫描不会重跑旧任务 |
| 其他服务 | Commerce、Payment 情况相同，本期不接 |

## 2. 目标

mor-test 的 Platform 失败任务能自动恢复：Webhook 投递按退避时间自动重试，最终送达或进入"已放弃"；通知、文件入驻等其他 Platform 补偿任务同样被扫描。

## 3. 本期范围（建议）

1. **启动 XXL-Job Admin**：按 Q-01 纳入 mor-test 编排；初始账号 `admin/123456` 立即改成随机密码，存入 `.env.test`，不打印。
2. **开启 Platform 执行器**：在 `compose.applications.yaml` 给 Platform 加 `XXL_JOB_*` 环境变量。`app-name` 用 `platform-support-service`；执行器端口只在私网，不映射到宿主；回调地址用 Platform 的固定私网 IP。之后重启 Platform 一次（约 30 秒）。
3. **登记任务**：执行器分组 `platform-support-service`（自动注册）。BEAN 任务 `compensationEventScan`：分片广播、单机串行、失败不重试，周期按 Q-03，参数留空（扫描所有 Platform 补偿类型）。登记方式按 Q-02。
4. **验收**：
   - Admin 执行器在线，任务调度日志成功。
   - 用合成 Client 建一个指向"先失败后成功"临时接收端的端点，确认投递从"等待重试"自动变为"已送达"。
   - 不放行 http 时，接收端需要满足 test 的 https 公网要求，见 Q-04。
5. **记录**：更新 mor-test README、发布记录和台账。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| Commerce、Payment 执行器 | 本期不接；登记方式确定后可照做 |
| 生产与其他环境 | 不涉及 |
| 代码改动 | 不改代码；只改 mor-test 编排配置并执行 |

## 5. 待确认问题

| 编号 | 问题 | 选项 | 建议 |
|---|---|---|---|
| Q-01 | Admin 如何纳入编排 | A：`compose.sh` 加载 `compose.scheduler.yaml` 并加 `scheduler` profile，此后 Admin 随 mor-test 常驻，多占约 0.5 GB 内存；B：不改 `compose.sh`，按 README 用单独命令启停，重建环境时容易漏启 | A。Platform 执行器依赖它，常驻更稳 |
| Q-02 | 任务登记方式 | A：写成可重复执行的 SQL（`xxl_job_group` / `xxl_job_info`），放在需求目录并试跑回滚核对；B：在 Admin 页面手工建 | A。可审阅、可复现 |
| Q-03 | 扫描周期 | 每 30 秒 / 每 1 分钟 | 30 秒。Webhook 首次退避较短，验收也更快 |
| Q-04 | 验收用接收端 | A：同 2026-10-16 Webhook 投递验收的做法，临时起接收容器，并临时放行 http 与该地址，需要额外重启 Platform 两次；B：只验证调度链路（执行器在线、任务成功、扫描日志），不做投递重试实测 | A。要验证的就是自动重试 |
| Q-05 | 适用阶段与协调 | 1 范围 → 6 发布清单（只读核对，不改环境）→ 7 执行（备份、改编排、重启 Platform、登记、验收、收尾）→ 8 提交编排、README 与记录。执行前与使用 mor-test 的会话协调；Commerce 会话正在做"Client API 测试环境联通"，需要先确认它不受 Platform 重启影响 | 按左列 |
