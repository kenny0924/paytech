# 任务卡 P2-stage5：第二段本机端到端

- 依据：[技术方案](../technique.md)第二段各节；[P2 报告](P2-report.md)（第 4、5、8 节）；[P2-web 报告](P2-web-report.md)；第一段[阶段 5 报告](../verification/stage5-report.md)；用户 2026-10-22 "可以"（合入主工作区，进入阶段 5）。
- 目标：
  - 本机 e2e 库执行第二段迁移与块 129 注册后，原有支付链路、沙箱验证、管理读取与基线一致；
  - 服务渠道的新能力在真实链路上可用：通用入口、调用授权、出站地址守卫、执行分舱。
- 执行者：Opus 5.5 agent；主会话把关。

## 环境（已核实，开工时再确认一次）

- 启动器：[verification/local-e2e/run-service.sh](../verification/local-e2e/run-service.sh)，是第一段的副本，先读头部注释。
  - 库用 `*_v2e2e`，Redis 用 db 3，RabbitMQ vhost 为 `/mor-e2e`。
  - 合成 PayerMax 在 127.0.0.1:18190，流水写入 `local-environment/e2e/runtime/payermax/payermax-journal.jsonl`。
  - nginx 端口 18443 / 18444，运营入口为 18444。
- 当前运行的是第一段新版本（JAR 时间 10-21 07:23，目录 `local-environment/e2e/jars/`）：card、channel-connector、platform、ops-bff。
  - gateway、payermax-mock、nginx、billing、ledger、account 也在运行，**不要动这些进程**。
  - 第一段旧 JAR 保存为 `*.pre-ccu`，不要覆盖。
- ops-web（pid 64950）是 vite 开发服务器，直接读取**主工作区** `operations-console-web/src`。主工作区已合入第二段页面，不需要重启。
- 第一段脚本可以复用：`verification/stage5_e2e.py`、`stage5_card_pay.py`、`stage5_compare.py`、`stage5_migrate.sh`。
  - 不要修改原文件，复制为 `stage5p2_*.py` / `.sh` 后再改。
  - 第二段的路径与字段变化以 P2 报告第 4 节和 P2.md 第 6 节为准：接入方改为渠道，`integration*` 改为 `channel*`，删除了 5 个接入方增删改接口。

## 步骤

### A. 基线（第一段版本，不换 JAR、不改库）

1. 核实 `mor_channel_connector_v2e2e` 中的 `PAYERMAX_MOCK` 渠道、账号、MID、能力组，以及 `t_integration*` 资料是否仍在。缺少的部分经控制台 API 补登，并在报告中注明。
2. 跑以下链路，结果写入 `verification/stage5p2-baseline.json`：
   - 候选查询；
   - REFUND 发到合成 PayerMax；
   - PAY（含真实读卡）；
   - 两个 NOT_SENT 场景；
   - 沙箱验证：新建账号与用例，运行一次；
   - 外部 API 管理接口的全部 GET 快照，经 Nginx 18444 → Gateway → ops-bff。

### B. 迁移与注册（只在本机执行，仅限以下 SQL）

1. 备份：
   - 用 `mysqldump --set-gtid-purged=OFF` 整库备份 `mor_channel_connector_v2e2e` 与 `mor_platform_v2e2e`；
   - 存到 `local-environment/e2e/backup/<UTC时间>-ccu2-stage5/`。
2. channel-connector：在 `mor_channel_connector_v2e2e` 上依次执行 `db/channel-connector/phase2-10-precheck.sql`、`phase2-20-migrate.sql`、`phase2-30-verify.sql`。
   - 执行方式按 P2 报告第 8 节。
   - precheck 结果不符合预期时**停下**并在报告中说明，不要强行迁移。
   - 保存 verify 输出。
   - 核对已有渠道 `PAYERMAX_MOCK` 迁移后 `channel_type=PAYMENT`，`endpoints` 来自原接入方。
   - 核对配置变更记录的目标类型已改为 `CHANNEL_*`。
3. Platform：在 `mor_platform_v2e2e` 上执行 `db/platform-support-service/phase2-registration.sql`。
   - 先跑其中的预检查段，确认本机块 71 / 92 的现状。
   - 激活段是否执行，取决于本机块 92 是否已激活：已激活则执行，以与 mor-test 的目标形态一致；未激活则不执行，并在报告中说明。
   - **不得重放块 71 的激活语句。**
   - 执行后，按本机既有方式重新发布 Gateway Snapshot，并确认 Gateway 加载了新 Snapshot。
4. **不碰**本机 auto 测试库 `mor_channel_connector`，也不碰其他任何 `mor_*` 库。如果发现 auto 库需要第二段 DDL，只在报告中写明。

### C. 新版本

1. 打包在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-ccu2/paytech` 执行，**不要在主工作区打包，不要 install**：
   ```
   JAVA_HOME=…jdk-25… ./mvnw -o -q -pl <card-app, channel-connector-app, platform-support-app, operations-console-bff-app> -am package -DskipTests
   ```
   该 worktree 为 `281a217d` 加上第二段改动，与主工作区合入内容一致，不含其他会话未提交的改动。
2. 把当前 JAR 改名为 `*.pre-ccu2`，复制新 JAR，然后依次重启 card → channel-connector → platform → ops-bff。
   - 启动器如需调整，复制为 `verification/local-e2e/run-service-p2.sh` 再改。
   - channel-connector 额外提供：
     - `channel-connector.protocol.execution.allowed-schemes=https,http`；
     - `channel-connector.protocol.execution.trusted-networks=127.0.0.1/32`：合成 PayerMax 与下文的回显服务都在本机回环，只放行这一个地址；
     - 两个舱的并发上限保持默认，只有 E 节的分舱验证临时调整。
3. 四个服务的 Health 都应为 UP。启动日志中没有表或列缺失的错误。

### D. 回归对照（与 A 比较）

1. 重跑 A2 的全部项目，结果写入 `verification/stage5p2-after.json`，用比较脚本输出 `stage5p2-compare.json`。
2. 允许的差异：
   - ID、时间、请求 ID；
   - 接入方相关的字段名与路径改为渠道；
   - 已删除的 5 个接入方增删改接口返回 404；
   - 配置变更目标类型的新名称。
3. 其余差异一律视为缺陷，需要逐项列出。
4. card：
   - 读卡以 `channel-connector-service` 身份成功（PAY SUCCESS），读卡审计的调用方为 channel-connector；
   - 以 `platform-support-service` 身份直接请求读卡，应被拒绝（第二段只允许 channel-connector）。

### E. 第二段新能力（脚本 `verification/stage5p2_service.py`）

所有控制台操作走 Nginx 18444 → Gateway → ops-bff，这样同时验证块 129 路由与 Snapshot。通用入口直接调用 channel-connector 私网端口，调用方用约定的调用方头传入（以代码为准）。

1. **合成服务**：在 `verification/` 下写一个最小的本机回显服务，监听 127.0.0.1 上的空闲端口。
   - 记录收到的方法、路径、头名（凭据头只记是否存在与末 4 位）与请求体。
   - 支持可配置的延迟。
   - 只用 Python 标准库。
2. **服务渠道全流程（控制台 API）**：
   - 渠道列表按类型筛选（PAYMENT / SERVICE），结果正确；
   - 新建 SERVICE 渠道 `S5P2_ECHO`，写入出站地址，指向回显服务；
   - 新建一份凭据，经真实 Vault Protect；列表只显示末 4 位；
   - 配置原子 API 与一个标准 API 的实现，使用已有标准 API，或在标准 API 页新建测试用的 `S5P2_ECHO_*`；
   - 激活渠道（只要求已配置出站地址）。
3. **调用授权**：
   - 未授权的调用方调用通用入口，被拒，记录状态码与错误码；
   - 授权 `counterparty-service` × `S5P2_ECHO` × 标准 API 后调用成功：回显服务收到请求且注入了凭据；channel-connector 日志有 `channel.outbound.access` 行；
   - 重复创建同一授权返回 409；
   - 停用授权后调用被拒，重新启用后恢复；
   - 授权的创建、停用、启用都在变更记录中留下 `CHANNEL_CALL_GRANT`；
   - 调用方名格式不合法时返回 422。
4. **入口隔离**：
   - 支付渠道 `PAYERMAX_MOCK` 经通用入口调用，被拒；
   - 服务渠道经支付入口 `/channel-connector/v1/operations` 调用，被拒。
5. **出站地址**：
   - 出站地址改为不在放行网段的私网或 `localhost` 地址（如 `https://10.1.2.3`），返回 422，字段为 `endpoints[i].baseUrl`；
   - 删除仍被原子 API 引用的地址，返回 409；
   - 携带过期 version 写入，返回 409。
6. **暂停**：暂停 `S5P2_ECHO` 后通用入口调用被拒；恢复后可用。
7. **分舱（可选，成本低就做）**：
   - 临时以 `service-max-concurrency=1` 重启 channel-connector，回显服务设延迟，并发两次调用：一次 503 `CHANNEL_EXECUTION_BUSY`，同时支付链路 REFUND 不受影响；
   - 验证后恢复默认并重启。
8. **导航**：
   - 运营导航不再含 `OPERATIONS_EXTERNAL_INTEGRATIONS`；
   - 页面 92302 显示名为「渠道」，含调用授权按钮；
   - 标准 API 页仍在。

### F. 页面（有 Playwright 就做；没有就跳过，并在报告中说明）

用无头浏览器登录 https://127.0.0.1:18444，对以下页面各截一张图，存入 `verification/stage5p2-screens/`，并记录控制台报错：

- 渠道列表的三个分段；
- 支付渠道详情：概览、协议配置、沙箱验证；
- 服务渠道详情：概览、凭据、调用授权矩阵与列表；
- 出站地址编辑；
- 旧地址 `/external-api/integrations/PAYERMAX_MOCK` 跳转到渠道详情。

只读浏览，不在页面上做写操作。

## 约束

- 只执行 B 节列出的 SQL。不碰 mor-test，不碰 auto 测试库。
- 不 install、不提交、不推送。主工作区只允许新增本需求 `verification/` 下的文件；worktree 不改代码。
- 发现代码缺陷时，写明现象、证据和建议修复后停下，由主会话决定，**不要自行改代码**。
- 只停启本卡列出的 4 个服务，加上 E7 的临时重启，以及你自己启动的回显服务。用完关闭回显服务。
- 输出与运行记录不得包含 Token、密钥、口令或凭据原值。
- 命令前台执行；遇到阻塞时写明原因后结束，不要无限重试。

## 交付

报告 `verification/stage5p2-report.md`，包括：

- 结论；
- A / D 对照表；
- E 各项结果，含状态码、错误码和证据位置；
- 执行过的 SQL 清单（库、文件、UTC 时间、影响行数）；
- 备份位置；
- 回滚方法：恢复 `*.pre-ccu2` JAR，按 `phase2-rollback.md` 回退，Platform 用注册 SQL 的回滚段；
- 遗留项。

最后用简短中文回复结论。
