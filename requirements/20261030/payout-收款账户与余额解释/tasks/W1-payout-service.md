# W1 任务卡：payout 服务（结算出款 S1：收款账户与余额解释）

- 工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-po-svc`（detached，基于 main `30361be7`）。只在这里改代码；不提交、不推送、不碰 mor-test。
- 权威材料（只读，在主工作树）：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/payout-收款账户与余额解释/` 下 `requirement.md`、`technique.md`（第 6—13 节是合同，第 7 节表、第 8.1 节 12 个接口、第 8.6 节权限码、第 7.4 / 7.5 节目录与字段清单）、`db/payout/ddl.sql`（已在本机执行，表结构以它为准）；决定 `/Users/kenny/Desktop/Workspace/Code-MoR/agent-docs/decisions/2026-10-30-payout-settlement-payout-model.md`。
- 必读规范（在工作树内读）：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md` + `agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md`。用户全局规范在 `~/.claude/CLAUDE.md`（卫语句、方法 ≤30 行、Processor 模板、BizCheckFailException 中文上下文）。
- 参照实现：`paytech/tax`（最近新建服务的四模块结构与 app 配置）、`paytech/reconciliation`（`CreateCashAccountProcessor` 的 Vault Protect 用法、补偿事件）、`paytech/client-master/.../ClientIdentifierVaultClientImpl.java`（STABLE Token）。

## 范围

1. **工程：** `paytech/payout/{pom.xml, paytech-payout-api, -core, -app, -test}`；取消 `paytech/pom.xml` 中 payout 模块注释；`spring.application.name=payout-service`，端口 18117（管理端口按 LOCAL_DEVELOPMENT_ENVIRONMENT 规则）；Vault 客户端、internal-auth（OFF）、compensation-event、Redis 锁（Foundation `AbstractLockProcessor`）。
2. **模型：** 直接编辑 `paytech/payout/docs/database/payout.pdma.json`（不用生成器）改 `t_beneficiary_account`、新增 `t_beneficiary_account_event`，与 `db/payout/ddl.sql` 一致；同步 `payout-schema.sql` 中这两张表（其余出款表不动）；`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` 加 `t_beneficiary_account_event`。DataObject 用业务名（无 DO 后缀）。
3. **api 模块：** technique 8.1 的 P1—P12（`@HttpExchange`，请求 / 响应体、错误码枚举、7.4 节目录枚举）。余额解释响应按 8.1 P12。
4. **core：** Processor（提交、Maker、Checker、设默认、关闭、停用、恢复、Reveal、队列、列表、详情、字段要求、余额解释）；Domain Service（收款账户、事件追加、冷却判定、查重）；Repository / Mapper XML；字段清单 `beneficiary-requirements.yaml`（7.5 节）。规则见 requirement B1—B10（B2 / B6 / B9 已修订）、technique 第 6 节事务与并发：出站（Vault、account、Platform）一律在本地事务外，写命令锁 `PAYOUT_BENEFICIARY_OWNER:{ownerType}:{ownerId}`。
5. **出站：** Vault（`PAYOUT_BENEFICIARY_V1` REFERENCE JSON、`PAYOUT_BANK_ACCOUNT_KEY_V1` STABLE，用途见 `db/vault/dml.sql`）；account `GET /balances/v1/clients/{id}/positions`、`/partners/{id}/positions`（由 W2 在 account-api 新增；本卡按 technique 8.2 合同写客户端接口与 DTO，放 payout-core 的 integration 下，DTO 自建，不依赖 W2 代码）；Platform 通知 `POST /notifications/v1/requests` 带 `tenantRecipients`（technique 8.3；同样按合同自建请求体，W2 负责 Platform 侧）。
6. **补偿事件：** handler `BENEFICIARY_NOTIFICATION`，模板码见 technique 8.3；`sourceEventKey = BENEFICIARY:{id}:{eventNo}`；通知失败按退避重试，不回滚状态。
7. **环境：** `local-environment/mysql/init/01-create-databases-and-users.sh` 加 `create_v2_finance_database mor_payout payout_app MOR_PAYOUT_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_payout`；`local-environment/.env.example` 加 `MOR_PAYOUT_DB_PASSWORD=`。本机库与账号**已由主会话创建**，DDL 已执行，密码在 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local` 的 `MOR_PAYOUT_DB_PASSWORD`（只读取，不打印）。test-environment 不在本卡范围。
8. **测试（auto）：** 从 HTTP 入口覆盖 AC-01—AC-10 场景矩阵；Vault / account / Platform 用测试 HTTP 端点返回配置 DTO（UNIT_TEST_GUIDE 2026-09-17 决定），不用 `when().thenReturn` / `@MockBean`；断言原值不出现在日志、事件、补偿载荷；测试用枚举不用字面量。

## 本机运行约束

- 只连 `mor-local-mysql-1`（13306）/ Redis 16379 / 本机 RabbitMQ；**绝不连 `mor-test-*` 容器**。
- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；`./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`；本地依赖不全时可在线（Nexus 127.0.0.1:18081）。
- 只跑 payout 模块（及 `-am` 依赖的编译），用 `package` 而非 `test`，不要跑全 reactor 测试（会清空其他会话的共享库）；环境变量从 `.env.local` 导出（MySQL、Redis 用 `PAYTECH_REDIS_PASSWORD`=`MOR_REDIS_PASSWORD`、`MOR_RABBITMQ_USER/PASSWORD/VHOST`）。
- arch-rules 测试要跑通（`paytech/tools/arch-rules`）。

## 交付

在工作树写 `REPORT-W1.md`（用 Bash heredoc）：改动清单、接口与 technique 的逐项对应、AC 映射、运行过的命令与结果（含失败）、偏离与待决。完成前自查 WRITE_API_EXAMPLE 第 11 节。回复只给一句摘要与报告路径。
