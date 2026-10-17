# 任务卡 04 · CCT → client-master + commercial（两库两 app，两段式解析）

- 分支：`refactor/step-04-client-master-commercial`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step04`；起点：`refactor-main`（02a 合入后，主会话填写）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 04 步；设计 [06-cct-two-stage-resolution.md](../design/06-cct-two-stage-resolution.md)；决定 D39（法律文档归客户主档）、D42（已存卡开关归客户主档）、S4。
- 并行：与 05、07 同时进行；第 03 步（收单 / 消费者）在本步之后，它将消费本步的 client-master-api。

## 1. 输入文档（先完整读取）

- [design/06-cct-two-stage-resolution.md](../design/06-cct-two-stage-resolution.md) 全文（归属、两个新接口、商务查询参数化、一致性语义、写入方为空）。
- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md)、[design/02-archunit-rules.md](../design/02-archunit-rules.md)、[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节两行与第 2 节 CCT 38 表 + Commerce 的 `t_client_stored_card_setting` 行、第 3 节 BCR 两边各一份与枚举拆分。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4（CCT 行）、4.3（client-contract 生成器与四个测试）、第 5 节；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 1 节 CCT 行、2.1、3.1（client-master / commercial 行）、3.2、4.2、4.3、5.1、6。
- [analysis/cct.md](../analysis/cct.md) 全文（特别是第 3、4、5、9 节的 10 个逻辑热点）。
- 第 00、01、02a 步报告（arch-rules 口径、internal-auth 出站用法、Platform 的 api 坐标与 `t_currency_spec`）。
- 现有 `client-contract-service/`：`docs/requirements/20260921-checkout-context-resolution/design.md`、`domain/CheckoutResolutionContext`、`CheckoutPricingContext`、`CheckoutQueryServiceImpl`、`CheckoutRecords.java`、30 个 Mapper XML、`ClientPeopleMapper`、`CheckoutResolutionFixture`、`fixtures/checkout-query.json`。
- `commerce-service/docs/database/sql/commerce-schema.sql` 中 `t_client_stored_card_setting` 的 DDL 与 `commerce-initial-data.sql` 的对应 DML（只读，用于复制到 client-master）。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`。

## 2. 模块映射（`git mv` 为主，新建为辅）

| 现 | 目标 |
|---|---|
| `client-contract-api`（`api/onboarding`、`api/workspace`、相关枚举） | `paytech/client-master/paytech-client-master-api` |
| `client-contract-api`（`api/checkout`：`CheckoutContextApi` 与 bodies、`CheckoutErrorCode`） | `paytech/commercial/paytech-commercial-api` |
| `client-contract-web` 中入驻 / 人员 / 文档 / 审核 / 分类 / 工作区 / 投影代码、MQ 消费者、3 个补偿 Handler、21 张主档表 + BCR 的 Mapper / Repository / Service / Processor | `paytech-client-master-core`；Controller、启动类、配置 → `paytech-client-master-app` |
| `client-contract-web` 中 Checkout 解析（`CheckoutResolutionProcessor`、`CheckoutQueryServiceImpl` 商务部分、`CheckoutPricingContext`）、协议 / 价目 / 支付方式 Profile 的 Mapper / Repository | `paytech-commercial-core`；Controller、启动类、配置 → `paytech-commercial-app` |
| `client-contract-test` | 按 10 §2.1 拆为 `paytech-client-master-test`（10 类 37 例 + 新增候选入口测试）与 `paytech-commercial-test`（2 类 16 例改写） |
| `docs/`、`README.md` | 按归属拆到两个服务目录；PDManer 模型按 09 §4.3 拆为 `client-master.pdma.json`、`commercial.pdma.json`，Schema SQL 按表归属切片（DDL 文本逐字复制） |

删除 `client-contract-service/mvnw`；搬完后 `client-contract-service/` 消失。`paytech/pom.xml` 只取消 `client-master`、`commercial` 两行注释。应用名 `client-master-service`（18101 / 19101）、`commercial-service`（18108 / 19108）；库 `mor_client_master`（现 `mor_cct` 改名）、`mor_commercial`。包根 `com.paytech.client.master`、`com.paytech.commercial`（允许保留旧包名一个版本，但新代码用新包）。

## 3. 代码与逻辑改动清单（逐条引用 06 文档）

1. **client-master 新增候选接口**（06 §2.1）：`POST /client-master/v1/checkout-candidates`、`POST /client-master/v1/checkout-candidates/verify`、`GET /client-master/v1/party-profiles?ids=`。把 `CheckoutResolutionContext.candidates / buildCandidate` 中只依赖主档事实的校验原样搬入（Client ACTIVE 且 approvedAt < asOf、主分类已确认、Affiliation 有效且按 priority 排序、OE ACTIVE、唯一有效 Source Relation 且形态合法、精确 Scope 匹配、Scope 申请 APPROVED 且 `approved_source_relation_id` 一致），单库 REPEATABLE READ 只读事务一次完成；响应含 `client{status, approvedAt, partyProfileId, storedCardSetting{enabled}}`、候选自带 `legalDocuments{CONSUMER_TERMS, REFUND_POLICY, PRIVACY_NOTICE}` 与 `sellerDisclosure`（按 consumerCountry × locale 命中，D39）、`rootVersions{CLIENT, OPERATING_ENTITY[], LOCAL_PARTNER[], CONSUMER_LEGAL_DOCUMENT, SELLER_DISCLOSURE}`。
2. **`t_client_stored_card_setting` 迁入 client-master**（D42）：DDL 与 DML 从 Commerce 逐字复制进 client-master 的 Schema / 初始化 SQL 与 PDManer 模型；Mapper / Repository / 只读 Service 搬入 client-master-core（Commerce 侧的删除在第 03 步）；新增 `GET /client-master/v1/clients/{clientId}/stored-card-setting`（供第 03 步 consumer 使用），候选接口的 `client.storedCardSetting` 复用同一查询。
3. **commercial 两段式解析**（06 §2.2）：`POST /checkout-context/v1/resolutions` 路径不变，由 commercial 提供；步骤 1 调 client-master 候选接口；步骤 2 本库只读快照逐候选解析（Client Agreement 已签、CLIENT_PRICING、PARTNER 路径、三份法律文档与卖方披露取候选自带值），fall-through 语义不变；签署校验所需 Party Profile 经 `party-profiles` 接口读取；步骤 3 事务外调 Risk（保持 503 占位）；步骤 4 调 `verify` 并重读本库商务根版本（AGREEMENT 等）比对，任一变化按现有 `verifyUnchanged` 语义返回冲突；步骤 5 响应结构与现响应一致。失败关闭：client-master 不可用或 `verify` 不可用一律 503 `OWNER_UNAVAILABLE`，两次调用的 Request ID 写入解析日志（06 §4）。
4. **商务查询参数化**（06 §3）：11 条 `findForCheckout` 与 `BusinessChangeRecordMapper.findCheckoutRoots` 删除对 `t_client_entity_affiliation`、`t_entity_source_relation` 的子查询，改为 `entityNos`、`localPartnerIds`、`clientId` 显式参数；语义等价由改造后的 16 个 Checkout 测试验证。
5. **人员共享标记**：`ClientPeopleMapper` 不再读 `t_agreement_party`；commercial 新增 `GET /commercial/v1/agreement-parties/exists?partyIds=&partyProfileIds=`，client-master 经 commercial-api 读取，失败关闭（视为"共享"，禁止修改）——在报告中说明该选择。
6. **BCR 两边各一份**（06 §6）：`t_business_change_record` / `t_business_change_version` 两库各建；`BusinessChangeRecordRecordType` 与 `AggregateRootType` 按 03 §3 的清单拆成两套枚举；`CheckoutRecords.java` 按归属拆到两个 core；通用枚举 `BusinessChangeOperation`、`BusinessChangeSnapshotFormat` 两边各留副本。
7. **装配**：两个 app 各自 DataSource / 事务管理器 / `@MapperScan` / TypeHandler / Snowflake worker / 补偿配置（compensation 只在 client-master，commercial 无补偿 Handler 与 MQ，去掉 AMQP 放行）；配置前缀 `client-master.*`、`commercial.*`；`onboarding.*` 开关随 client-master。
8. **Enforcer**（09 §1.4）：CCT 禁令复制到两个服务聚合 POM；模块 parent = 服务聚合 POM。
9. **外部调用**：Commerce 的 `CheckoutCommercialPort` 仍是 Mock，本步不动 Commerce；但 commercial 的服务发现名为 `commercial-service`，并在报告中给出第 03 步需要改的目标名。两个 app 的出站客户端（Platform、Risk、Vault、client-master-api、commercial-api）经 `HttpServiceClientFactory`，默认 `internal-auth.mode=OFF`。
10. **路径修复**（09 §4.3）：`generate-client-contract-model.mjs` 拆为两个生成器；`client-contract-model.test.mjs`、`client-contract-scenario-doc.test.mjs`、`client-compensation-model.test.mjs`、`oe-client-model.test.mjs` 与 `generate-oe-client-schemas.mjs` 的 CCT 条目按新路径改；`agent-docs/database/CLIENT_CONTRACT_DATABASE.md` **不改正文**（第 13 步），生成器读取它时按两服务标注归属的方式在脚本内处理。
11. **死代码**：`application-auto.yml` / `application-checkout-auto.yml` 随 CCT 删除（10 §2.1）；`client-contract-test/README.md` 过时内容随拆分重写。

## 4. 测试要求（10 §2.1、§3、§4、§6）

- 基线：搬迁前 `cd client-contract-service && ./mvnw -B verify` 记录 53 例的实际数。
- client-master：10 类 37 例整类搬入；新增 `ResolveCandidatesHttpTest` ≥ 6 例（有序候选、无候选、Scope 不匹配、Source 失效、根版本返回、只读保证）、`verify` 接口 ≥ 2 例、已存卡开关接口 ≥ 2 例、`ArchitectureAutoTest`。固件：`checkout-query.json` 中 10 张 client-master 表进入 client-master 固件。
- commercial：`ResolveCheckoutHttpTest`（13）、`ResolveCheckoutValidationTest`（3）改为 commercial 入口测试：client-master 候选响应由新建 `ClientMasterCandidateMocker` 经 MockHttpServer 返回（DTO 样本 `fixtures/commercial/client-master-candidates.json`，含有序候选与根版本）；`CheckoutTestMapper` 对 client-master 表的负例 UPDATE 改为 DTO 变体；新增"回查版本不一致返回冲突"、"client-master 不可用 503 且本库只读"用例；503 场景保留；`ArchitectureAutoTest`。
- 清理器：`ClientMasterDataCleaner`、`CommercialDataCleaner` 按 10 §3.1 表清单；`init/ddl.sql` 两份按 10 §4.2（CCT 今天无测试 DDL，本步补齐：DDL 逐字切片自 `client-contract-schema.sql`，加 `t_client_stored_card_setting`、`t_compensation_event`、`t_consumed_event`）。
- 本地库：`mor_cct` → `mor_client_master`、新增 `mor_commercial`；`local-environment/mysql/init/01-create-databases-and-users.sh` 与 `.env.example` 新增 `client_master_app` / `commercial_app` 账号与密码变量（10 §5.1）；运行中的 MySQL 用 root 手工建库建账号并应用两份测试 DDL（命令写进报告，密码不打印），不重启容器。
- 禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单

1. 搬迁前后测试数；`cd paytech && ./mvnw -B verify -pl client-master/paytech-client-master-test,commercial/paytech-commercial-test -am` 全绿；全 Reactor `verify` 全绿。
2. 两个 app 以 `mock` Profile 启动 health UP。
3. `node --test scripts/pdmaner/client-contract-*.test.mjs scripts/pdmaner/oe-client-model.test.mjs scripts/pdmaner/client-compensation-model.test.mjs`（改名后的）通过。
4. `git log --follow` 抽查 `paytech-client-master-core` 与 `paytech-commercial-core` 各一个 Java 文件；`client-contract-service/` 不存在。
5. R4 表归属：arch-rules 的 `table-ownership.yaml` 对两库零违规（白名单由 00 步脚本从 03 文档生成，若 03 文档与实现不一致以 03 为准并在报告指出）。

## 6. 禁止事项

- 不改入驻 / 审核 / 分类 / 工作区任何业务行为；不新增商务写入；不改 Commerce、Risk、Platform 目录。
- 不执行业务库 SQL（测试库建库与 DDL 除外，须记录）；不改 `agent-docs/` 正文。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-04): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/04-client-master-commercial-report.md`：改动摘要 ≤ 30 行、第 5 节逐项、两个新 api 的合同清单（给第 03 步用）、与 06 文档的偏差、待确认项。
3. 最终回复两行。
