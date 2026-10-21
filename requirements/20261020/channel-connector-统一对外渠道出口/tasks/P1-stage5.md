# 任务卡 P1-stage5：第一段本机端到端与数据迁移演练

- 依据：[技术方案](../technique.md) 第 3.3–3.5、4、8 节；[P1 报告](P1-report.md)；用户 2026-10-21 "继续，做阶段 5 本机端到端"。
- 目标：同一条链路在**迁移前（旧版本）**与**迁移后（新版本 + 迁移后的数据）**各跑一次，结果一致；数据迁移按第 3.4 节 1–3 步演练并核对。
- 执行者：Opus 5.5 agent；主会话把关。

## 环境（已核实）

- 本机全链路启动器：`requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh`（读它的头部注释）。库为 `*_v2e2e`，Redis db 3，RabbitMQ vhost `/mor-e2e`，合成 PayerMax 在 127.0.0.1:18190，流水写入 `local-environment/e2e/runtime/payermax/payermax-journal.jsonl`。
- 正在运行的是旧版本（JAR 时间 10-20 10:20，在 `local-environment/e2e/jars/`）：gateway 18080、platform 18106、channel-connector 18110、ops-bff 18201、payermax-mock 18190、nginx 18443/18444。ops-bff 从 10-20 06:32Z 起没有请求，可以视为空闲，由本任务接管。card、payment 当前未运行。
- 上一需求的本机链路脚本可以参考或复用：`requirements/20261020/channel-connector-运营控制台渠道成本与基础配置/verification/`，包括 `local_acceptance.py`（经控制台登记渠道、账号、MID、能力）、`local_chain.py`（候选查询 → `/channel-connector/v1/operations` → 标准 API → 合成 PayerMax）、`platform_payermax_setup.py`。原脚本不要修改；需要改的复制到本需求的 `verification/` 下再改。
- `mor_platform_v2e2e` 已有接入方 `PAYERMAX_MOCK` 的外部 API 资料（1 个接入方、4 个标准 API、4 个原子 API、2 个脚本）。`mor_channel_connector_v2e2e` 的渠道数据可能已被验收清理过，先核实，缺什么就经控制台补登。

## 步骤

### A. 基线（旧版本，不改动任何进程的 JAR）
1. 准备渠道、账号、MID、能力数据（优先通过控制台 API）；需要读卡时，用旧 JAR 启动 card，同时确认 `mor_card_v2e2e` 可用。
2. 执行链路：候选查询；按 Payment 的 `PaymentChannelClientImpl` 组装请求，经 `/channel-connector/v1/operations` 发 PAY（含读卡）和 REFUND，各至少一次；再构造一个 NOT_SENT 场景，例如配置无效或标准 API 不存在。记录 outcome、错误码、渠道流水条数，以及合成 PayerMax 验签结果。
3. 沙箱用例：经 ops-bff `/external-api-admin/v1/**` 对 `PAYERMAX_MOCK` 新建验证账号和用例，发起一次运行，记录运行状态和步骤结果。
4. 管理读取快照：经 ops-bff 调用外部 API 的全部 GET 接口（列表与详情），保存规范化后的 JSON，去掉时间戳和请求 ID。
5. 结果写入 `verification/stage5-baseline.json`，不得包含 Token、密钥或口令。

### B. 数据迁移演练（只在本机执行，仅限以下 SQL）
1. 备份：用 `mysqldump` 备份 `mor_platform_v2e2e` 和 `mor_channel_connector_v2e2e` 中的这 10 张表，以及 `t_channel_config_change`，存到 `local-environment/e2e/backup/<UTC时间>-ccu-stage5/`。
2. 结构：在 `mor_channel_connector_v2e2e` 上执行 `paytech/channel-connector/docs/database/sql/changes/2026-10-20-protocol-tables.sql`（10 张表加 `change_detail`）。执行前先确认业务账号对新表有权限（库级授权）。
3. 数据：先停止 Platform 的外部 API 写入（基线已经跑完，不再写入），再执行 `mysqldump --no-create-info --skip-triggers --complete-insert` 导出 `mor_platform_v2e2e` 的 10 张表，导入 `mor_channel_connector_v2e2e`。
4. 核对：逐表比较行数和 `CHECKSUM TABLE`；对全部 `definition_json`、`flow_definition_json` 及脚本源码列逐行计算 SHA-256，两边一致。结果写入 `verification/stage5-migration.md`，脚本和命令放在 `verification/` 下。
5. **不删除** `mor_platform_v2e2e` 的 10 张表（删除留到 mor-test 验收后）。另外只做一次语法试跑：把 `drop-integration.sql` 中的库名替换为一个临时库，在临时库上执行（临时库先建 10 张空表），执行后删除临时库。
6. 另外在本机 auto 测试库 `mor_channel_connector` 上执行同一个增量 DDL，因为主工作区代码已经要求 28 张表。执行前确认该库只缺这 10 张表和 `change_detail` 列，不导入任何数据。

### C. 新版本
1. 在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-ccu` 执行打包：`./mvnw -o -q -pl <platform-app, channel-connector-app, card-app, ops-bff-app> -am package -DskipTests`。worktree 是 4ab205e0 加上本需求改动，不含其他会话未提交的内容。**不要在主工作区打包。**
2. 把旧 JAR 改名为 `*.pre-ccu` 保存，复制新 JAR，然后按 run-service.sh 依次重启 card → channel-connector → platform → ops-bff。channel-connector 读卡客户端和 Vault 的新配置项（以新代码中的 `application*.yml` 为准）通过环境变量提供。如果 run-service.sh 需要调整，复制到本需求的 `verification/` 下修改，不改原脚本。
3. 重跑 A2–A4，保存为 `verification/stage5-after.json`，并与基线逐项比较。允许的差异只有：ID、时间和请求 ID；card 读卡调用方从 `platform-support-service` 变为 `channel-connector-service`；Platform 不再提供 `/external-api-admin`，直接访问时返回 404。另外核对：新的写操作在 `t_channel_config_change` 中留下了 `change_detail`；Vault Reveal 和读卡审计的调用方是 `channel-connector-service`。
4. 链路结束后**保留新版本运行**，不回滚。在报告中写明回滚方法：恢复 `*.pre-ccu` JAR，并 TRUNCATE 目标库的 10 张表。

## 约束

- 只执行本卡 B 节列出的 SQL，不碰其他 `mor_*` 库和 mor-test。
- 不 install、不 deploy、不提交、不推送；主工作区只允许新增本需求 `verification/` 下的文件。
- 只停启本卡列出的服务进程，不碰其他 PID。
- 所有命令前台执行；遇到阻塞时写明原因后结束，不要无限重试。
- 输出不打印凭据；运行记录中不得包含 Token、密钥或口令。

## 交付

报告 `verification/stage5-report.md`，内容包括：基线与新版本的对照表（逐项列出一致或差异及原因）、迁移核对结果、执行过的 SQL 清单（含库名和时间）、备份位置、回滚方法、遗留项。最后用简短中文回复结论。
