# 第 04 步报告 · CCT → client-master + commercial（两库两 app，两段式解析）

- 分支：`refactor/step-04-client-master-commercial`（起点 `refactor-main@7e0b70d`）。提交：`2ba7fb40` 纯搬迁与包名、`8d8ebe70` 逻辑拆分与测试、`a9b32bbd` 控制器条件与文档链接、`104e7bc0` 消费队列改名、`fb3d32f9` PDManer 拆分，以及删除残留文档的提交；本报告单独提交。
- 结论：第 5 节 5 项全部真实执行并通过（第 3 项的 3 个失败与起点逐名一致，属既有问题）。两段式解析保持原 fall-through、签署校验与复核语义；测试从 53 例增至 81 例，0 跳过。
- **需要先看**：执行期间宿主磁盘写满，本地 Redis 出现 AOF `MISCONF`、Docker 守护进程停止响应约 40 分钟，期间测试无法运行；Docker 由他人恢复后全部复测通过。另有一个会话（`023cf016…/team-e2e-jars/client-contract-web`）长期运行旧 CCT 应用并消费共享队列 `cg_cct_client_review`，这是改名队列的直接原因（第 4 节第 6 项）。

## 1. 改动摘要

1. `git mv` 拆分：onboarding / workspace 合同 → `paytech-client-master-api`（保留旧包 `com.paytech.client.contract.api` 一个版本，Platform 与两个 BFF 只改 POM 坐标）；`CheckoutContextApi`、`CheckoutErrorCode` → `paytech-commercial-api`（包 `com.paytech.commercial.api`）。web 拆为两套 core / app，test 拆为两个 test 模块；`client-contract-service/` 已不存在，`mvnw` 已删除。
2. 包根：client-master 的 core / app / test 改为 `com.paytech.client.master`；commercial 全部为 `com.paytech.commercial`。`paytech/pom.xml` 只取消两行注释；两个服务聚合 POM 复制 CCT Enforcer 禁令，追加 `com.paytech:*-core` / `*-app` 禁令，commercial 去掉 AMQP / XXL / compensation / Redis 放行。
3. client-master 新增候选、复核、参与方资料与已存卡开关四个接口（第 3 节）。主档条件自原 `candidates` / `classification` / `eligibleEntities` / `eligibleEntity` / `validateSource` 逐行搬入 `CheckoutEligibilityContext`，单库 REPEATABLE READ 只读事务读取；`t_client_stored_card_setting` 的 Mapper / Repository / 只读 Service 迁入。
4. commercial 的 `CheckoutResolutionProcessor` 改为两段式：候选 → 本库只读快照 → 事务外读取参与方资料 → 逐候选惰性解析 → Risk（仍为 503 占位）→ client-master `verify` 与本库重读比对。合同、定价、Partner、签署、法律证据的校验与落选逻辑原样保留在 `CheckoutResolutionContext`。
5. 商务查询参数化：10 个 `findForCheckout` 与 `findCheckoutRoots` 改为 `clientId`、`entityNos`、`localPartnerIds` 显式参数并处理空集合；原 `PartyProfileMapper.findForCheckout` 中对 `t_agreement_party` 的子查询改为 `party-profiles` 接口（第 11 条跨库语句）。commercial 的 Mapper XML 不再出现任何主档表。
6. 人员共享标记：`ClientPeopleMapper` 删除对 `t_agreement_party` 的 EXISTS；新增 commercial `GET /commercial/v1/agreement-parties/exists`。client-master 在保存人员、读取草稿、提交准备三处于数据库事务外调用，失败关闭。
7. BCR：两库各一份。`BusinessChangeRecordRecordType` 与 `AggregateRootType` 拆为两套，`BusinessChangeOperation`、`BusinessChangeSnapshotFormat` 两边各一份；`CheckoutRecords.java` 按表拆到两个 core。
8. 装配：两个 app 各自持有 DataSource、事务管理器、MyBatis 与 TypeHandler 注册。所有出站客户端经 `HttpServiceClientFactory` 并按目标服务名附加内部 Token，`internal-auth.mode: "OFF"`；管理端口分别为 19101 和 19108。
9. 测试：client-master 新增候选、复核、已存卡与共享标记测试，并新增 commercial DTO Mock 和全表清理器；commercial 16 例改写为 client-master DTO Mock（`ClientMasterCandidateMocker`），另增复核、合同参与方查询与不可用场景。两份 `init/ddl.sql` 从新 Schema 逐字切片，并加入 `t_consumed_event`。
10. PDManer：生成器拆为 `generate-client-master-model.mjs` 与 `generate-commercial-model.mjs`，共享 `client-contract-model-core.mjs`；两份模型和 Schema、两份 PHYSICAL_DECISIONS，6 个 node 测试按新路径改写。`agent-docs/` 正文未改。
11. local-environment：初始化脚本、`.env.example`、`compose.yaml`（仅 mysql.environment 两行）、README 新增 `mor_client_master` / `client_master_app` 与 `mor_commercial` / `commercial_app`，`mor_cct` 保留；`scripts/verify-local-environment.mjs` 同步。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并导出 `.env.local` 中的 `MOR_RABBITMQ_*` 与 `PAYTECH_REDIS_PASSWORD`（取 `MOR_REDIS_PASSWORD` 的值，未打印）。

### 2.1 测试数与 Reactor

- **搬迁前基线**：把未改动的 `client-contract-service` 复制到 scratchpad 后执行 `./mvnw -B verify`，结果 BUILD SUCCESS。执行 **53** 个测试，0 跳过，与 design/10 的 53 一致。
- **任务卡命令**：`cd paytech && ./mvnw -B verify -pl client-master/paytech-client-master-test,commercial/paytech-commercial-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`，结果 **BUILD SUCCESS**（1:00 min）。按 surefire-reports 计数：

  | 模块 | `<testcase>` | `<skipped>` | 方法下限（design/10 §1.2） |
  |---|---:|---:|---:|
  | paytech-client-master-test | 57 | 0 | 37 |
  | paytech-commercial-test | 24 | 0 | 16 |
  | 合计 | 81 | 0 | 53 |

- **全 Reactor**：`cd paytech && ./mvnw -B verify -Dpaytech.local-environment.dir=…`，结果 **BUILD SUCCESS**（10:25 min），详见第 2.6 节。

类映射（旧 → 新，方法数）：

| 旧类（CCT） | 新类（服务） | 旧 → 新 |
|---|---|---|
| 10 个 onboarding / workspace / mq / eventhandler 类 | 同名，client-master | 37 → 37（只改包名、基类名与 `ClientReviewResultListenerTest` 的 DLQ 常量） |
| `ResolveCheckoutHttpTest` | 同名，commercial | 13 → 14（13 例同名改写，另增 `test_clientMasterUnavailableFailsClosedAndLeavesDatabaseUnchanged`） |
| `ResolveCheckoutValidationTest` | 同名，commercial | 3 → 3 |
| — | client-master `ResolveCandidatesHttpTest` / `VerifyCandidatesHttpTest` / `ClientStoredCardSettingHttpTest` / `PersonAgreementSharingTest` / `ArchitectureAutoTest` | 新增 9 / 5 / 3 / 2 / 1 |
| — | commercial `CheckoutVerificationTest` / `AgreementPartyQueryHttpTest` / `ArchitectureAutoTest` | 新增 4 / 2 / 1 |

- 既有缺口（不是本步引入）：Risk 资格端口仍是正式 503 占位，成功解析路径无覆盖。HTTP 链在 Risk 处结束，所以"回查版本不一致"不能经 HTTP 到达，`CheckoutVerificationTest` 改从公开端口 `ClientMasterCheckoutClient` 与公开领域合同 `CheckoutResolutionContext#verifyUnchanged` 触发。

### 2.2 两个 app 以 mock Profile 启动

从 target 复制两个 exec jar 后执行：

```bash
java -jar paytech-commercial-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:<commercial-test>/application-mock.yml \
  --server.port=28108 --management.server.port=29108 --commercial.test.external.base-url=http://127.0.0.1:9
java -jar paytech-client-master-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:<client-master-test>/application-mock.yml \
  --server.port=28101 --management.server.port=29101 --client-master.test.external.base-url=http://127.0.0.1:9
```

两个应用分别用 1.7 s 和 3.1 s 启动成功，日志中 ERROR 为 0。`/actuator/health` 均返回 `{"groups":["liveness","readiness"],"status":"UP"}`，liveness 返回 200。验证后已停止进程并删除复制的 jar。

### 2.3 node 测试

| 文件 | 起点 | 现在 | 说明 |
|---|---|---|---|
| `client-contract-model.test.mjs` | 80 / 80 | 83 / 83 | 按双模型改写，新增 3 项每模型断言 |
| `client-contract-scenario-doc.test.mjs` | 5 / 7 | 5 / 7 | 两个失败与起点同名：「现实场景 Manifest 覆盖当前全部物理表…」「现实场景枚举表与当前闭合枚举目录完全一致」；场景文档在 `agent-docs/`，本步禁改 |
| `oe-client-model.test.mjs` | 9 / 10 | 9 / 10 | 失败为同名「Four manual schemas…」，原因是 `commerce schema drift`；client-master 与 commercial 两份 Schema 均为 `Verified` |
| `client-compensation-model.test.mjs` | 1 / 1 | 1 / 1 | |
| `client-onboarding-model.test.mjs` | 3 / 3 | 3 / 3 | |
| `pdmaner-canonical-paths.test.mjs` | 2 / 2 | 2 / 2 | |

命令：`node --test scripts/pdmaner/<文件>`。起点数字来自起点工作树上的同一命令，失败名用 `git archive 7e0b70d` 的导出副本复核。附加检查：
- `node scripts/pdmaner/generate-client-master-model.mjs --check` 与 `generate-commercial-model.mjs --check` 均输出 up to date。
- 用脚本逐表比对新旧 Schema 中的 `CREATE TABLE` 文本：client-master 27 表、commercial 14 表 **0 差异**（`t_client_stored_card_setting` 与 `commerce-schema.sql` 比对）。原 38 张表中只有 `t_command_idempotency` 没有落位（S10 删除）。

### 2.4 历史与目录

- `git log --follow -- paytech/client-master/paytech-client-master-core/.../ClientDraftServiceImpl.java` 输出 `2ba7fb40`、`21a1a519`、`95d41504`、`fff35d2e`。
- `git log --follow -- paytech/commercial/paytech-commercial-core/.../domain/CheckoutPricingContext.java` 输出 `8d8ebe70`、`2ba7fb40`、`fff35d2e`。
- `ls client-contract-service` 返回 No such file or directory；`git ls-files client-contract-service` 为 0 行。

### 2.5 R4 表归属

- `node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check` 输出 `table-ownership.yaml is current (265 physical tables + 56 retained).`。白名单与 design/03 一致：法律文件两表与 `t_client_stored_card_setting` 归 client-master。
- 两个服务的 `ArchitectureAutoTest`（R1–R12，含 R4）均 0 违规，没有新增 known-deviations。
- `paytech-arch-rules` 55 / 55、`arch-rules-reactor-test` 3 / 3 通过。
- 首轮 R10 报出 `ClientDraftPatch` 的单行枚举，已改为每项一行并空行分隔，行为不变。

### 2.6 全 Reactor 结果

- 第一次运行：BUILD FAILURE，只有 `paytech-vault-test` 失败（11 例中 9 例），原因是"缺少显式测试变量：VAULT_TEST_JDBC_URL"。这是 Vault 端到端测试的既有前置条件，与本步无关。
- 处理：按第 02d 步报告与 `paytech/vault/README.md`，用 `/Users/kenny/sdk/go1.27.1/bin/go build -o <scratchpad>/vault-server ./cmd/vault-server` 构建二进制。再导出 `VAULT_TEST_SERVER_BINARY`、`VAULT_TEST_JDBC_URL/USER/PASSWORD`、`VAULT_TEST_DB_DSN`（密码取自 `.env.local`，未打印）后重跑同一命令。
- 结果：**BUILD SUCCESS**（10:25 min）。按 Surefire 报告计数，29 个测试模块共 **4723** 个用例，0 失败、0 错误、0 跳过，其中 client-master 57、commercial 24、platform-support 798、Foundation 17 个模块与 tools 的数字均与前序步骤一致。验证后已删除临时二进制。

### 2.7 其他检查

- `node scripts/verify-local-environment.mjs`：退出码 1，只剩 4 行 Gateway 合同文档引用缺失，与第 09 步记录逐字相同；新增的两库与变量检查通过。
- 本机测试库：team-lead 授权测试库建库后，于 2026-10-17T18:29:14Z 用 root 在运行中的 `mor-local-mysql-1` 执行。命令为 `docker exec -i -e MYSQL_PWD=<root 密码，取自 .env.local，未打印> mor-local-mysql-1 mysql --default-character-set=utf8mb4 -uroot < <ddl.sql>`，对两份 `init/ddl.sql` 各执行一次，内容为建库、建账号、`GRANT SELECT, INSERT, UPDATE, DELETE` 与全部 `CREATE TABLE`。核对结果：`mor_client_master` 28 表、`mor_commercial` 15 表（各含 `t_consumed_event`），两个账号权限只覆盖本库。没有重启任何容器，也没有执行业务库 SQL。

## 3. 两个新 api 的合同清单（供第 03 步）

### 3.1 `paytech-client-master-api`（发现名 `client-master-service`）

| 合同 | 方法 / 路径 | 调用方 | 请求 → 响应 |
|---|---|---|---|
| `ClientMasterCheckoutApi#candidates` | `POST /client-master/v1/checkout-candidates` | 仅 `commercial-service`（Header `X-Caller-Service`） | `CheckoutCandidatesRequestBody{clientId, consumerCountryCode, currency, locale, contextAsOfTime}` → `CheckoutCandidatesResponseBody{client{clientId,status,partyId,partyProfileId,approvedAt,storedCardSetting{enabled}}, classification{…}, candidates[{entityNo, oePartyId, affiliationId, priorityNo, scopeId, sourceRelation{…}, localPartner?, legalDocuments[], sellerDisclosures[], candidateDigest}], commercialScope{entityNos, localPartnerIds}, rootVersions{client, operatingEntities[], localPartners[], consumerLegalDocuments[], sellerDisclosures[]}}` |
| `ClientMasterCheckoutApi#verify` | `POST /client-master/v1/checkout-candidates/verify` | 仅 `commercial-service` | `VerifyCheckoutCandidateRequestBody{query, rootVersions, candidate{entityNo, affiliationId, priorityNo, scopeId, sourceRelationId, candidateDigest}}` → `{unchanged, changedRoots[]}` |
| `ClientMasterCheckoutApi#partyProfiles` | `GET /client-master/v1/party-profiles?ids=` | 仅 `commercial-service` | 1–1000 个 ID → `{items[{profileId, partyId, approvedTime, draftApplicationId, contentHash}]}` |
| `ClientStoredCardSettingApi#storedCardSetting` | `GET /client-master/v1/clients/{clientId}/stored-card-setting` | 不限调用方（只读） | → `{clientId, enabled}`；没有记录时 `enabled=false`，非规范 ID 返回 400 `INVALID_REQUEST` |

错误码 `CheckoutCandidateErrorCode` 如下：
- `CLIENT_NOT_FOUND`：404，也用于非 commercial 调用方。
- `CLIENT_NOT_ELIGIBLE`：422。
- `CANDIDATE_CONTEXT_AMBIGUOUS`：409。
- `INVALID_CANDIDATE_QUERY`：422。
- `CANDIDATE_SOURCE_UNAVAILABLE`：503。

时间均为 UTC，候选响应最多六位小数。原 onboarding / workspace 合同路径与 DTO 未变。

### 3.2 `paytech-commercial-api`（发现名 `commercial-service`）

| 合同 | 方法 / 路径 | 调用方 | 说明 |
|---|---|---|---|
| `CheckoutContextApi#resolve`（包改为 `com.paytech.commercial.api.checkout`） | `POST /checkout-context/v1/resolutions` | 仍只允许 `commerce-service` | 路径、请求、响应与错误码 `CheckoutErrorCode`（含 `CCT_CONTEXT_AMBIGUOUS` / `CCT_CONTEXT_CHANGED` 的 Code 值）均未变 |
| `AgreementPartyQueryApi#exists` | `GET /commercial/v1/agreement-parties/exists?partyIds=&partyProfileIds=` | 仅 `client-master-service` | 合计 1–1000 个 ID → `{referencedPartyIds[], referencedPartyProfileIds[]}`；非法调用方或 ID 返回 400 `INVALID_REQUEST` |

### 3.3 第 03 步需要修改的目标名

- Commerce（acquiring）的 Checkout 解析客户端：发现名 `client-contract-service` 改为 **`commercial-service`**。依赖坐标改为 `paytech-commercial-api`，import 改为 `com.paytech.commercial.api.checkout.*` 与 `com.paytech.commercial.api.enums.CheckoutErrorCode`；路径和 DTO 不变。
- 需要同步的引用：
  - `commerce-service/paytech-commerce-web/src/main/resources/application.yml:30`
  - `paytech-commerce-test/src/test/resources/application-mock.yml:33,54`
  - `CctTransactionProbe.java:27`
- consumer 读取已存卡开关：调用 **`client-master-service`** 的 `GET /client-master/v1/clients/{clientId}/stored-card-setting`。acquiring 结账时也可直接使用候选响应中的 `client.storedCardSetting.enabled`。

## 4. 与 06 文档及任务卡的偏差

1. **候选请求不含 `industryCode` / `mccCode`，改为增加 `locale`**：原实现由 Client 主分类决定行业与 MCC，调用方不能指定（原 RequestBody 注释："不得指定 OE、行业、MCC"）。法律文件要按 Locale 命中，所以 `locale` 必须传入；分类随响应返回。
2. **法律文件与卖方披露返回原始记录，不返回"命中项"**：client-master 只按 OE × 国家 × Locale 过滤，状态 / 时间窗适用性与唯一性仍由 commercial 按原规则惰性判断。若在 client-master 提前取唯一，会把后续候选上的配置冲突提前报出，改变原 fall-through 语义。
3. **复核使用 `candidateDigest`**：原复核要求同 OE 候选与原选择完全相等，比较的是全部原记录。拆库后 client-master 对候选使用的全部主档原记录计算 SHA-256 摘要，复核时重算比对；commercial 侧则沿用原逻辑重读本库并比较候选对象。
4. **`rootVersions` 增加法律文件与卖方披露根**：按 06 §2.1 加入 `CONSUMER_LEGAL_DOCUMENT`、`SELLER_DISCLOSURE` 根，有 BCR 行时参与复核比对。原查询不读这两类根，复核因此略严。
5. **商务查询范围是该 Client 全部 Affiliation 的 OE 与全部 PARTNER 来源**，不只是通过的候选，与原子查询逐行等价。
6. **审核结果消费队列改名**为 `cg_client_master_client_review` / `dlx_client_master_client_review` / `cg_client_master_client_review_dlq`，依据 design/10 §5.3。直接原因：另一会话运行的旧 CCT 进程消费同名旧队列，抢走测试消息，`ClientReviewResultListenerTest` 与 `ReapplyAfterRejectionTest` 稳定超时；改名后通过。
   - 部署影响：旧队列中的消息不会被新服务消费，切换时需排空旧队列。
   - 交换机 `topic_risk_client_review` 与路由键未变。
7. **design/03 §3.3 与 06 的 BCR 类型冲突**：03 把 `CONSUMER_LEGAL_DOCUMENT`、`SELLER_DISCLOSURE` 放在 commercial 枚举，06 §2.1 与 C4 把这两表和根版本归 client-master。实现按 06 / C4 放入 client-master 枚举；`COMMERCIAL_TERM_VERSION`（03 漏列在根类型中）放入 commercial RecordType。
8. **design/09 §5.2 与 design/10 §3.1 把法律两表写在 commercial**，与 03 / C4 冲突。按任务卡"以 03 为准"处理：两表进入 client-master 的模型、Schema、DDL 与清理器；commercial 夹具只剩 10 张商务表与本库 BCR，不是 10 文档写的 12 张。
9. **清理器**：两个测试库是服务专用库，清理器在各 Mocker 定向清理之后删除本库全部表，表集合等于 `init/ddl.sql`（含 `t_consumed_event`）。原 `OnboardingTestMapper` 的定向 DELETE 保留。
10. **`compose.yaml` 改了 mysql.environment 两行**：初始化脚本新增的两个必需变量需要经 compose 传入，否则全新初始化会失败。test-environment 的 compose 未改。
11. **`init/ddl.sql` 不含 `DROP DATABASE`**，与 Platform 的写法不同，避免误删已有库。

## 5. 待确认

1. **调用方名与发现名未全量改名**：client-master 出站调用 Platform、Risk 时 `X-Caller-Service` 仍为 `client-contract-service`。原因是 Platform `OnboardingFileVerificationController` 与 Risk 都校验这个字面值，本步不改 Platform / Risk 目录。
   - Platform 与两个 BFF 调 CCT 时的发现名 `client-contract-service` 也未改。
   - Vault 客户端 `caller-service` 在测试配置中改为 `client-master-service`；生产 Vault 调用方白名单是否需要同步，请确认。
   - 统一改名需要一个横跨 Platform / Risk / BFF / Commerce 的步骤。
2. **Platform 与 BFF 的 POM 坐标**：这 6 个 POM 已改为 `paytech-client-master-api`，Java 未改，属于任务卡第 6 节"不改 Platform 目录"的例外。已通知 team-lead，未收到异议；如需撤回，只需还原这 6 个 POM。
3. **人员共享标记的失败关闭**：commercial 不可用时，人员视为"已被合同引用"，不可编辑。读取草稿时可编辑标记为 false，保存返回 409 `PERSON_NOT_EDITABLE`。审核读取（申请已提交）不调用 commercial。代价是 commercial 故障期间组织申请人员无法修改。
4. **client-master 不比较商业时点与本机时钟**：防止两服务时钟差造成误拒，"不晚于当前时间"仍由 commercial 校验。
5. **管理端口 19101 / 19108**：Actuator 不再与业务端口共用；mor-test 等部署的健康检查地址需同步。
6. **RabbitMQ 环境变量**：client-master 仍读取 `CCT_RABBITMQ_*`，未改为新前缀，以免影响现有部署描述。
7. **`onboarding.id.datacenter/worker`** 继续作为 client-master 的 Snowflake 坐标；commercial 只读，不需要 ID 生成器。
8. **local-environment 初始化脚本**沿用该脚本现有的 `GRANT ALL`；测试 DDL 用最小权限。旧 `mor_cct` / `cct_app` 保留到合入后删除。
9. **本机凭据显示**：排查卡住的 `docker exec` 时，`ps` 输出在本会话记录中显示了本地 Redis 测试密码（仓库隔离的本地凭据，未写入任何文件）。如需要，可以轮换本机 `.env.local` 中的该值。
10. **未覆盖**：`agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 正文未改（第 13 步处理），生成器在脚本内按两服务分配表。requirements-site 未改。
