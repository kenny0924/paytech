# REQ-01 阶段 4 研发任务卡

## 目标

按已确认方案实现 commercial 开通项前置门禁。**方案唯一依据**：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/technique.md` 第 7—16 节（第 1—6 节是现状核对与证据位置）；需求见同目录 `requirement.md`；契约见 `/Users/kenny/Desktop/Workspace/Code-MoR/agent-docs/decisions/2026-10-28-client-go-live-orchestration.md` 第 3 节。方案有歧义或与代码事实冲突时，**停下写进报告"待裁决"**，不要自行改变业务语义。

## 工作区

- 代码只改 worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-gate`（detached，基于 `663e5e4a`）。不要改主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR` 的代码。
- 报告写主工作区：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/verification/stage4-report.md`。
- Maven：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；在 `paytech/` 下用 `./mvnw -o -Dmaven.repo.local=$HOME/.m3-gate -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ …`（私有仓库，可 install）。
- 前端：`operations-console-web` 用 npm（已有 node_modules 可复用主工作区的：如 worktree 缺 node_modules，可在 worktree 内 `npm ci --prefer-offline`）。

## 范围（只改这些）

1. `paytech/commercial/**`：api / core / app / test、`compensation-event-catalog.yaml`、`docs/database/`（`commercial.pdma.json` 直接编辑、`sql/commercial-schema.sql`、新增 `sql/changes/2026-10-28-capability-execution-status.sql` 与 `…-rollback.sql`）、`README.md` 如有相关段落。
2. `paytech/foundation/paytech-messaging/catalog/events.yaml`：新增 `AgreementCapabilityStatusChanged` 条目；`QualificationChanged.consumers` 加 `commercial`。
3. `paytech/operations-console-bff/**`：3 个转发端点与模型字段（第 13.1 节）。
4. `operations-console-web/src/commercial/**` 与 i18n `en` / `zh-CN` 的 `commercial` 命名空间（第 13.2 节）。
5. 需求目录下 `db/ddl.sql`（注释索引，指向执行文件，不含可执行语句）与 `db/data-query-templates.sql`（第 14 节三条模板正文，单行约定）。写主工作区路径。

不得改其他系统；不提交、不推送、不部署、不碰 mor-test；不改 `test-environment/`。

## 开工前必读（读完在报告开头列出已读路径）

`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（完成前按第 11 节逐项核对并写进报告）、`agent-docs/COMPENSATION_EVENT_GUIDE.md`、`agent-docs/UNIT_TEST_GUIDE.md` + `agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`（DDL 与 PDManer 部分）。commercial 既有样板：`AbstractAgreementWriteProcessor`、`UpdateAgreementCapabilityProcessor`、`AgreementServiceImpl`、`AgreementStatusChangedHandler`、`CommercialEventKeys`、`CommercialTermVersionActivationJobHandler`；消费者样板：`paytech/client-master/paytech-client-master-core/.../ext/mq/QualificationChangedListener.java` 与 client-master `application.yml` 的 `messaging.consumer` 段。

## 关键约束（摘自方案，实施时不得偏离）

- `status` 只表示成员资格 ACTIVE / DISABLED；新列 `execution_status`；定价 4 处改看成员资格；Checkout 改看 `execution_status=ENABLED`（第 8、9、12 节）。
- 门禁唯一入口 `CheckCapabilityPrerequisitesProcessor`，锁 `agreement:{id}`（LOCK），外部资格查询在事务外，短事务内 CAS + BCR（actor 系统 0、reason `AGREEMENT_CAPABILITY_EXECUTION_CHANGED`）+ Root + 登记发布任务。UNKNOWN 不迁移；不碰 `SUSPENDED_MANUALLY`；PARTNER 合同不进门禁。
- ④ 渠道身份固定 `SKIPPED`（原因 `CHANNEL_IDENTITY_NOT_CONNECTED`），**不写**渠道客户端、不加开关配置。
- 新增 / 恢复 / 重新纳入 / 合同激活 / 价格版本激活在各自原事务内登记 `AGREEMENT_CAPABILITY_PREREQUISITE_CHECK`；不在持锁事务中直接调用门禁。
- 事件载荷按合同版本一条、`changes[]`（第 11.1 节）。
- 事务内无外部调用；Processor 不逐表写；Repository 条件 SQL + 行数检查后回写原对象；`TransactionTemplate`，不用 `@Transactional`；中文 JavaDoc；枚举项空行分隔。
- 测试：auto，真实本地 MySQL / Redis / RabbitMQ；不用 Mockito Stub 替换生产链路；compliance 用已有测试 HTTP 端点 + DTO Mocker；Handler / Listener / Job 各有独立入口测试类（第 15.1 节场景矩阵全部覆盖）。

## 本地数据库

- 允许对本机 `local-environment` 的 `mor_commercial` 执行本需求迁移脚本（auto 测试需要新结构），并在本机验证一次回滚脚本后再重新迁移；**不得**操作其他库或任何远程环境。报告中记录执行时间与结果。
- 只跑 commercial 模块测试（`-pl` 限定 commercial 与 ops-bff），不跑全 reactor 测试（共享本地库会被其他服务测试清空）；全 reactor 只做 `test-compile`。

## 研发顺序

1. 枚举 + DO + Mapper + PDManer / DDL / 迁移 + 回滚脚本，在本地库执行迁移。
2. 定价 4 处与 Checkout 口径改动 → **先跑 commercial 现有全部 auto 测试**，修到全绿再继续（记录基线数与结果）。
3. 门禁 Processor + Context + `prerequisite-checks` 接口 + 测试。
4. 暂停 / 恢复接口、新增 / PUT 成员资格规则 + 测试。
5. 两个补偿 Handler + 合同激活 / 价格激活追加登记 + 测试（含"PENDING 开通项所在合同的价格版本可激活"回归）。
6. `QualificationChanged` 消费者 + 配置 + 测试。
7. 重查 Job + 测试。
8. ops-bff 转发 + 测试；ops-web 组件、i18n、测试（含 `i18nSourceGuard`、`npm run i18n:export-source` 校验）。
9. 事件目录、补偿目录、data-query 模板文本。
10. 全量验证：commercial auto 全部、ops-bff 相关 + 全量、ops-web `npm test` 与 `npm run build`、paytech 全 reactor `test-compile`、arch-rules（如有）。

## 报告内容

已读路径；逐步完成情况；偏离方案之处与理由；待裁决事项；新增 / 修改文件清单（相对 worktree）；本地 SQL 执行记录；测试命令与结果数字（区分新增 / 回归）；WRITE_API_EXAMPLE 第 11 节逐项核对；未覆盖项。**前台执行命令，不要等待后台事件；遇阻塞写进报告并结束。**
