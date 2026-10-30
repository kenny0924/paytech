# W1 · 阶段 4 后端：channel-connector 就绪查询、client-master 经营分类查询、commercial ④ 接入

## 目标

在工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-req02`（detached，main `331d8e0e`）按已确认方案实现 T1—T4，并以 auto 测试证明。**不提交、不推送、不执行任何 SQL（本需求无 DDL / DML）、不碰 mor-test。**

## 必读（实际读取，不凭记忆）

- 需求目录（只在主工作区，未入库）`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/channel-connector-渠道身份就绪查询/`：`requirement.md`（B1—B8、AC-01—AC-12）、`technique.md`（**第 2 节发现、第 8—10 节方案、第 15 节 T1—T4 为合同**）。
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`（第 2、3、4、5.2、5.3、7 节）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/UNIT_TEST_GUIDE.md` 与 `agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（第 11 节自查表适用部分）。
- 用户全局规范 `~/.claude/CLAUDE.md`（卫语句、方法 ≤ 30 行、中文 JavaDoc、`@AllArgsConstructor`、异常信息带上下文等）。
- 经验：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `maven-jdk25-and-local-test-run.md`、`local-vs-mor-test-containers.md`、`background-agent-and-shared-local-tests.md`、`tests-use-enums-not-literals.md`、`processor-template-method-no-javadoc.md`、`test-restclient-retries-503.md`。

## 任务

| # | 系统 | 内容 | 要点 |
|---|---|---|---|
| T1 | channel-connector | 抽 `OperatingEntityMidService`（interface + Impl，`loadUsableMids(entityNo)` → 领域对象 `OperatingEntityMids`）；`QueryMidCandidatesProcessor` 改用它 | 悬空关联仍抛 `ErrorException`；可重复读只读事务仍在 Processor；**候选结果逐字段不变**：现有候选测试全过 + 新增对照用例 |
| T2 | channel-connector | `ChannelIdentityReadinessApi`（`POST /channel-connector/v1/channel-identities/readiness`）、Request/Response Body（`api.readiness` 包，class + Lombok，中文 JavaDoc、Bean Validation：entityNo `[0-9A-Za-z]{5}`、items 1—200、paymentMethodCode `[A-Z0-9][A-Z0-9_.-]{0,63}`、currency `[A-Z]{3}`、mccCode `[0-9]{4}`）、Controller、Converter、`QueryChannelIdentityReadinessProcessor` | 判定五步与原因码见 technique 8.2；第 5 步用 `MidCapabilityContext.resolve`，体系 / 国家取值集合 = 命中行出现的值（`*` → null）；能力代码常量用 `ChannelCapabilityCodes`；原因码定义为 api 枚举 |
| T3 | client-master | `ClientBusinessClassificationApi`（`GET /client-master/v1/clients/{clientId}/business-classification`）、Response Body、Controller、`ClientBusinessClassificationQueryService` + Impl（读本模块业务资料与分类表；主分类 `primary_slot=1`；`confirmed` = `classification_confirmed_time` 非空；未确认时 industryCode / mccCode 返回 null） | Client 不存在 404 `CLIENT_NOT_FOUND`（复用现有错误码若已有）；无业务资料 200 + confirmed=false；可能需新增 Mapper 查询（XML） |
| T4 | commercial | `integration`：`ChannelIdentityReadinessClient`、`ClientClassificationClient` 及 Impl；Bean 与现有 compliance / client-master 客户端同处同法（服务发现、内部令牌、连接 2 s / 读 3 s）；门禁接入按 technique 10.2（事务外查、已覆盖币种 `TermCoverage.coveredCurrencies`、无覆盖币种 `AWAITING_PRICE`、未确认 MCC `CLIENT_MCC_MISSING` 不再调 channel-connector、调用失败 UNKNOWN `CHECK_UNAVAILABLE`、不就绪取字典序第一币种原因 + 参数 `entityNo / paymentMethodCode / mccCode / currencies`）；删除 `CHANNEL_IDENTITY_NOT_CONNECTED`；API 响应注释枚举同步 | 门禁所有入口（检查前置、合同激活、价格版本激活、重查作业）走同一路径——核对后都要带上 ④；状态机、事件、作业不改 |

## 测试（auto，真实生产链路，遵守 UNIT_TEST_GUIDE；不得用 Mockito 行为桩）

- channel-connector：HTTP 入口测试覆盖 AC-01—AC-05（五种原因、`*` 币种、非 ACTIVE 账号 / 渠道 / MID、能力 DISABLED、无收款动作、更精确行把动作设 0 的覆盖、批量去重、配置冲突 500）；候选查询重构对照用例。
- client-master：HTTP 测试（已确认主分类、未确认、无资料、Client 不存在）。
- commercial：在现有检查前置 HTTP 测试体系里新增外部 HTTP DTO Mock（照 `test/mock/external/compliance/MockQualificationEvaluationApi` + `QualificationEvaluationMocker` 的方式，为 channel-connector 就绪查询与 client-master 经营分类各建一组，含意外调用失败与每测清理）；覆盖 AC-06—AC-10（满足 / 不满足 / UNKNOWN、多币种、无覆盖币种、MCC 未确认、ENABLED → 暂停 → 恢复并发布事件）。已有用例中依赖"④ 固定 SKIPPED"的断言按新语义更新，并在报告里逐个列出。
- 运行：JDK 25、`~/.m3`、离线 + `-Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`；按模块 `package -pl <module> -am`（不跑全 reactor 测试）；只用 `mor-local-*` 本机库（凭据从 `local-environment/.env.local` 读入变量，不打印）；最后全 reactor `test-compile` 一次与 `paytech/tools/arch-rules` 测试。

## 规则

- 只改工作树 `Code-MoR-req02`；不改主工作区（本需求 `tasks/` 下写报告除外）和其他会话的 worktree。
- 不调用任何 Superpowers skill。不新增依赖到 `paytech-command-idempotency`。
- 卡住超过 15 分钟或遇到需要业务裁决的问题，写进报告"待裁决"并继续其他部分。
- 完成后写报告 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/channel-connector-渠道身份就绪查询/tasks/REPORT-W1.md`（改动文件清单、每个 AC 的测试名、测试结果数字、偏离方案处、待裁决），回复 ≤ 25 行摘要。
