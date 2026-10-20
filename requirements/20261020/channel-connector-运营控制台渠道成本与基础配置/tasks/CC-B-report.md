# CC-B 最终报告：渠道 / 账号 / MID / 能力管理合同、Vault Protect、Platform 接入方检查

- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR/.claude/worktrees/agent-aad8339304dac384d`
  - 分支 `worktree-agent-aad8339304dac384d`，未提交，未执行任何 git 写命令。
  - 该 worktree 已经是合并基线：CC-A 全部改动加上 CC-B 改动。
- 结论：任务卡第 2 节范围全部完成。合并后 channel-connector-test 全量 185 例、0 失败；`-am` 全链路（含 arch-rules）`BUILD SUCCESS`。
- 本报告位于 worktree 内的需求目录。主工作区的需求目录不在 git 跟踪内，合并时请一并拷贝。

## 1. 已读清单

- 规范：
  - `AGENTS.md`「接口开发必读与自查」及各路由条目
  - `agent-docs/DEVELOPMENT_CONVENTIONS.md` 全文
  - `agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 4–7 节
  - `agent-docs/examples/WRITE_API_EXAMPLE.md` 全文
  - `agent-docs/UNIT_TEST_GUIDE.md` 全文
- 本需求材料：`tasks/CC-B.md`、`technique.md` 全文
- channel-connector 既有源码：
  - `ChannelAccountConfigReader`、`PlatformStandardApiClient`、`ChannelConnectorOutboundConfiguration`
  - `QueryMidCandidatesProcessor`、`MidCapabilityContext`、`ChannelProperties` / `CapabilityDefinition`
  - 五表的 DataObject / Mapper / Repository
  - `CreateChannelCostPlanProcessor`、`ChannelCostPlanServiceImpl`、`ChannelCostContentHash`
  - 全部测试基础设施
- Vault Client：`VaultClient`、`ProtectCommand`、`ProtectResult`、`TokenMode`、`VaultValidation`、`DefaultVaultClient`、`VaultHttpService`
- Foundation：`AbstractLockProcessor` / `LockCommand`、`HttpServiceClientInterceptor`、`HttpProblemDecoder`、`CommonErrorCode`、`PagingRequestBody`
- Platform：`IntegrationManagementApi` 与 `GetIntegrationProcessor`（需要运营会话，已弃用），以及新合同 `IntegrationLookupApi` / `IntegrationExistenceResponseBody`（从主工作区逐字复制）

## 2. 写入口一览

每个写入口的共同点：
- 全部是 `service/processor` 下的具体 Processor。
- Context 一次构造，直接持有本模块 DataObject 和 `ChannelConfigChange`。
- 本地保存在对应 Service 的一个 `TransactionTemplate` 内，同事务写一行 `t_channel_config_change`。
- 状态更新用 `WHERE 原状态` 条件 SQL，并校验行数。

| 入口 | Processor | 锁（全部 `tryLocks`，10s / 60s） | 事务外步骤 | 本地原子写入 |
|---|---|---|---|---|
| 创建渠道 | `CreateChannelProcessor`（`AbstractProcessor`） | 无锁，渠道编码唯一索引兜底（409） | — | `PaymentChannelService.createChannel`：插渠道 + CREATE 记录 |
| 改名 | `UpdateChannelProcessor` | `channel:{id}` | — | `renameChannel`：`WHERE 未关闭` + UPDATE 记录 |
| 渠道状态 | `ChangeChannelStatusProcessor` | `channel:{id}` | 激活前调用 `PlatformIntegrationClient.exists(code)`；关闭前统计未关闭的账号与 MID | `changeChannelStatus` + STATUS 记录 |
| 创建账号 | `CreateChannelAccountProcessor` | `channel:{channelId}` | — | `ChannelAccountService.createAccount` |
| 改账号 | `UpdateChannelAccountProcessor` | `channel-account:{id}` | — | `updateProfile`（`WHERE 未关闭`） |
| 账号配置 | `ConfigureChannelAccountProcessor` | `channel-account:{id}` | 键值校验，生成规范 JSON 与幂等键，检查 32 KiB 上限，再调用 `vaultClient.protect(REFERENCE)` | `updateVaultToken`（`WHERE 未关闭`）+ CONFIGURATION 记录；写库失败只记录账号 ID 并抛出 |
| 账号状态 | `ChangeChannelAccountStatusProcessor` | `channel-account:{id}`；关闭时另加 `channel:{channelId}` | — | `changeAccountStatus`；激活要求渠道 ACTIVE 且已配置，关闭要求没有未关闭的 MID 绑定 |
| 创建 MID | `CreateChannelMidProcessor` | `channel:{channelId}` | — | `ChannelMidService.createMid`；外部 MID 唯一索引冲突返回 409 |
| 改绑 | `RebindChannelMidAccountProcessor` | `channel-mid:{id}` 加目标 `channel-account:{id}` | — | `rebindAccount`（`WHERE 原账号 AND 未关闭`）+ REBIND 记录 |
| MID 状态 | `ChangeChannelMidStatusProcessor` | `channel-mid:{id}` | — | `changeMidStatus`：首次激活写 `activated_at`，关闭写 `closed_at` |
| 创建能力组 | `CreateMidCapabilityProcessor` | `channel-mid:{midId}` | — | 插能力组、全部明细与 CREATE 记录；六维组合唯一索引冲突返回 409 |
| 替换明细 | `ReplaceMidCapabilityDetailsProcessor` | `channel-mid:{midId}`（锁前先读能力组拿 midId） | — | `touch`、删旧明细、插新明细、UPDATE 记录 |
| 能力组启停 | `ChangeMidCapabilityStatusProcessor` | `channel-mid:{midId}` | — | `changeCapabilityStatus` + STATUS 记录 |

读接口直接委托 Service，不建 Processor：渠道列表 / 详情、账号列表 / 详情、MID 详情、能力目录。

## 3. 改动文件

CC-B 新增约 70 个文件，追加修改约 25 个；worktree 合计 183 个改动或新增文件（含 CC-A 的部分）。

| 层 | 文件 |
|---|---|
| api | `ChannelConnectorHeaders`；`api/channel/` 下的 `PaymentChannelApi`、`ChannelAccountApi`、`ChannelMidApi`（写方法）、`MidCapabilityApi`；`request/` 下 12 个 RequestBody；`response/` 下 9 个 ResponseBody |
| core | `ChannelStatus.canTransitTo`；`ChannelConnectorLocks` 追加；五表 Mapper（Java 与 XML）和 Repository（接口与 Impl）追加写方法；`LikePatterns` |
| core / domain | `ChannelContext`、`ChannelAccountContext`、`ChannelMidContext`、`MidCapabilityWriteContext`、`ChannelConfigChanges`、`ChannelAccountConfigPayload`；View：`ChannelSummaryView`、`ChannelAccountDetailView`、`ChannelMidDetailView`、`MidCapabilityView` |
| core / validator | `ChannelStatusTransitionValidator`、`MidCapabilityDetailValidator` |
| core / service | `PaymentChannelService`、`ChannelAccountService`、`ChannelMidService`、`MidCapabilityService`，各自带 Impl |
| core / integration | `PlatformIntegrationClient` 与 `impl/PlatformIntegrationClientImpl` |
| core / processor | 上表 13 个 Processor；`MidCapabilityDetailBuilder`；12 个内部 Request 与 `MidCapabilityDetailItem` |
| app | `PaymentChannelController`、`ChannelAccountController`、`MidCapabilityController`；`ChannelMidController` 补齐写方法；Converter：`PaymentChannelConverter`、`ChannelAccountConverter`、`MidCapabilityConverter`、`ChannelConfigurationWire`，`ChannelMidConverter` 追加；`ChannelConnectorOutboundConfiguration` 追加 `IntegrationLookupApi` Bean；`application.yml` 追加注释 |
| test | `AbstractChannelConfigApiTest`、`ChannelConfigRequestBuilder`；4 个测试类；`VaultMocker` / `MockVaultController`（Protect）；`PlatformMocker` / `MockPlatformController`（接入方查询）；`ChannelDataMocker` / `ChannelTestMapper` 追加；`application-mock.yml` 追加注释 |
| 其他 | `README.md` 追加「渠道基础配置管理」一节；Platform 合同 2 个文件按主会话指示逐字复制 |

## 4. 接口 / 表 / 事件对照

- **接口**：
  - 新增 4 组管理合同，共 19 个方法（第 2 节 13 个写入口加 6 个读接口）。
  - 新增 Platform 出站调用 `IntegrationLookupApi.integrationExistence`。
  - Vault 出站调用新增 `protect`，参数为 `CHANNEL_ACCOUNT_CONFIG_V1` / `REFERENCE` / `CHANNEL_CONFIGURATION`。
  - 既有合同签名不变。
- **表**：
  - 不改结构。
  - 写入涉及五表与 `t_channel_config_change`（CC-A 的表，本机已有 18 张表）。
- **事件**：无 MQ，无补偿事件。

## 5. 第 11 节核对

| 核对项 | 实现与证据 |
|---|---|
| API / Body | 普通 class，中文字段注释，Hibernate Validator 约束；账号配置 Map 的键 / 值用容器元素约束；入口 `@Valid` 触发（422 用例：渠道编码、OE、配置键、MCC 通配） |
| Controller / Converter 只做适配 | Converter 只做 actor / ID 解析、状态 Code 解析和 Token 末 4 位截取；业务全部在 Processor |
| Processor 职责 | 只查询本模块 Repository / Service，组装 Context，按顺序调用；不访问 Mapper，不逐表写入 |
| Context | 持有本模块 DataObject；纯方法 `canActivate`、`accountBindable`、`activatedAtAfter`、`closedAtAfter`、`canClose`；不访问外部 |
| Service | 不调用其他 Service，不访问外部；各自一个 `TransactionTemplate` |
| 事务外调用 | Vault Protect 与 Platform 查询都在锁内、事务外。`VaultTransactionObserver` 观察全部出站请求，`assertOutsideTransactions` 每例检查，激活与配置用例另做显式断言 |
| Repository | 一表一仓储；条件 SQL 校验行数；成功后同步原对象；唯一冲突转 409 |
| 锁 | 锁实际资源；同一资源的所有入口用同一 Key 和同一锁形态（`tryLocks`）；数据库条件保护保留 |
| 业务防重 | 不使用 Idempotency-Key，也不接入命令幂等组件。Vault 幂等键是 Vault 协议自身的业务键 |
| View | 只用于多表读取；写入口响应直接由 Context / DataObject 组装 |
| 无生产 Mock | 外部依赖都走真实出站 Client 访问测试 HTTP 服务 |
| 测试 | 见第 6 节 |

## 6. 测试

- 命令在 `paytech/` 目录执行：
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`
  - `PAYTECH_REDIS_PASSWORD` 从 `.env.local` 的 `MOR_REDIS_PASSWORD` 读入，不打印
  - Maven 参数 `-o -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`
  - 脚本：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/8212e30a-7763-46f5-933c-ce7c6751a734/scratchpad/rungrep.sh`
- **全链路**：`./mvnw -o -pl channel-connector/paytech-channel-connector-test -am test` → `BUILD SUCCESS`，各模块 0 失败。包括 PayTech Architecture Rules、Redis Starter（20s）、vault-client，以及 channel-connector-test 185 例。
- **仅 channel-connector-test**（`-Dtest='com.paytech.channel.connector.**'`）：185 例，0 失败。
  - 其中 CC-A 及既有用例 150 例，CC-B 新增 35 例。
  - 架构门禁 `ArchitectureAutoTest` / `ChannelConnectorArchitectureTest` 已全部通过（CC-A 的 arch-rules 登记已合入）。
- **CC-B 新增用例**（类级 `@see` 指向正式 API 方法）：
  - `PaymentChannelApiTest`（12）：
    - 创建与重复编码 409
    - 参数 422 / 400，actor 缺失或非规范
    - 列表筛选、分页与 LIKE 字面量
    - 改名、关闭后拒绝改名
    - 激活成功、接入方不存在被拒、Platform 503 失败且不写库
    - 暂停恢复、非法迁移
    - 关闭被子项阻止
    - CLOSED 拒绝一切写
  - `ChannelAccountApiTest`（11）：
    - 创建、OE 422、渠道不存在
    - 配置写入：核对数据类型、模式、用途、幂等键等于规范原值 SHA；核对 Token 落库与变更记录；响应零泄漏
    - 同内容重放复用幂等键与 Token，换内容产生新 Token
    - Vault 拒绝不写库；CLOSED 账号拒绝配置且不调用 Vault
    - 未配置不能激活
    - 关闭被绑定的 MID 阻止
    - 详情不含凭据
    - 改名与列表
    - 并发：两个线程同内容写入，锁串行，同一幂等键，最终一个 Token、两条记录
  - `ChannelMidApiTest`（5）：
    - 创建
    - 同渠道唯一 409，跨渠道可重复
    - 账号跨 OE / 跨渠道 / 已关闭被拒
    - 改绑（跨 OE、跨渠道、同账号、已关闭 MID）
    - 激活前置、`activated_at` 首次写入后保持、`closed_at`
  - `MidCapabilityApiTest`（7）：
    - 能力目录
    - 创建三种类型明细
    - 六维组合重复 409
    - 类型不一致、未注册代码、CODE 越界、上下限颠倒、代码重复、MCC 通配
    - MID 已关闭
    - 整组替换
    - 停用后 `mid-candidates` 不再返回、重复停用被拒
- **测试基础设施**：
  - `VaultMocker.expectProtect` / `protectProblem` / `protectCalls`：调用记录只保存幂等键、模式、原值 SHA 与结构字段，不保存凭据。
  - `PlatformMocker.integrationExists` / `integrationMissing` / `integrationFault` / `integrationLookups`。
  - 未配置的调用都记为意外调用；空基线与清理覆盖新状态。

## 7. DDL 执行记录

- 本卡没有新的 DDL。
- 早先我在 `mor_channel_connector` 建过一次 `t_channel_config_change`，没加 utf8mb4，注释成了乱码。主会话已 DROP，并按 CC-A 的 `ddl.sql` 以 `--default-character-set=utf8mb4` 重建。当前 18 张表。此后我没有再执行任何 SQL。

## 8. 与 CC-A 共享文件的追加段落

均以 `CC-B 追加` 标注，集中放在文件末尾或类成员末尾。

| 文件 | 追加内容 |
|---|---|
| `core/constant/ChannelConnectorLocks.java` | 末尾：`CHANNEL_CONFIG_WAIT`、`CHANNEL_CONFIG_LEASE`、`channel()`、`channelAccount()`、`channelMid()`；import 中加 `Duration` |
| `core/enums/ChannelStatus.java` | 末尾：`canTransitTo` |
| `PaymentChannelMapper.java` / `.xml` | `insert`、`updateName`、`updateStatus`、`selectSummaries`、`countSummaries` |
| `ChannelAccountMapper.java` / `.xml` | `insert`、`updateProfile`、`updateVaultToken`、`updateStatus`、`countOpenByChannel`、`selectPage`、`countPage` |
| `ChannelMidMapper.java` / `.xml` | `insert`、`updateAccount`、`updateStatus`、`countOpenByChannel`、`countOpenByAccount`；主会话重建后的签名已核对，与 XML / Impl 一致 |
| `MidCapabilityMapper.java` / `.xml` | `findById`、`insert`、`updateStatus`、`touch` |
| `MidCapabilityDetailMapper.java` / `.xml` | `insertAll`、`deleteByCapabilityId` |
| 五个 `*Repository` 与 `*RepositoryImpl` | 对应写方法 |
| `ChannelMidController.java` | 在 CC-A 的 `listChannelMids` 之后追加 4 个依赖与 4 个方法 |
| `ChannelMidConverter.java` | `toRequest(create)`、`toRequest(rebind)`、`toStatusRequest`、`midId`、`toResponseBody(ChannelMidDetailView)` |
| `ChannelConnectorOutboundConfiguration.java` | `integrationLookupApi` Bean |
| `ChannelDataMocker.java` | `channel(code, status)`、`account(..., status)`、`mid(..., status)` |
| `ChannelTestMapper.java` | 末尾：`selectChannel`、`countChannels`、`selectAccount`、`selectMid`、`selectCapabilityStatus`、`countCapabilities`、`selectDetails`；复用 CC-A 的 `selectConfigChanges` / `countConfigChangeRows` / `rewriteMid` |
| `VaultMocker.java` / `MockVaultController.java` | Protect 预期、故障与调用记录；原「Protect 即意外」改为按预期返回 |
| `PlatformMocker.java` / `MockPlatformController.java` | 接入方存在性查询；Controller 同时实现 `IntegrationLookupApi` |
| `application.yml`、`application-mock.yml`、`README.md` | 注释行；README 末尾一节 |

两个 `pom.xml`、`ChannelConnectorMockTestBase`、`ChannelConnectorArchitectureTest` 没有改动：不需要。

## 9. 偏差与遗留

1. **锁统一用 `tryLocks`**（主会话已采纳）。原因：单资源 `lock()` 的 Key 带 `lock:` 前缀且是 SpinLock，与 `tryLocks` 的原 Key RLock 互不互斥。成本方案入口保持原有 LOCK。
2. **Vault 幂等键改用点号分隔：`channel-account-config.{id}.{sha256}`**，technique 第 5 节写的是冒号。Vault Client 的 `VaultValidation.safeHeader` 只允许 `[A-Za-z0-9._~-]`，用冒号会在客户端被拒成 400 `INVALID_REQUEST`（测试中实际复现过）。长度 107 字符，不超过 128。**需要同步修订 technique 第 5 节。**
3. **Platform 接入方查询改用新的内部合同 `IntegrationLookupApi`**（用户决定）。其 Platform 服务端由 PLT 线实现，本卡只做调用方，测试经 Mock HTTP 覆盖。
4. **渠道详情不含 `platformIntegrationExists`。** technique 中这一项是可选的，并且写明详情不调 Platform。
5. **Vault 拒绝时的 HTTP 状态**沿用公共映射，`FailException` 原样抛出，测试只断言 ≥400 且不写库。Vault 不可用时为 5xx。
6. **遗留**：
   - 旧 Token 不回收；Protect 成功但写库失败时新 Token 成为孤儿（无 Retire 合同）。
   - OE 只校验格式，不向 client-master 核对存在性（偏差 1）。
   - Vault 的 PROTECT 访问规则需要在本机 `local-environment/vault/policy.local.json` 和 mor-test `db/vault/dml.sql` 各登记一条。属于 REG 线，不在本卡路径范围内，没有改动。
   - 能力组启停的变更记录原因为空：该请求体没有 reason 字段，按合同不加。

## 补充：BW 合同缺口（3 项）

1. **`costVerId`**：`ChannelCostVersionResponseBody` 与 `ChannelCostVersionSummaryResponseBody` 新增该字段，为十进制字符串。
   - 来源：`ChannelCostVersionView.costVerId`、`ChannelCostPlanView.Version.costVerId`，由 `ChannelCostPlanConverter` 填值。
   - 用途：页面以 `targetType=CHANNEL_COST_VERSION`、`targetId=costVerId` 查询变更记录。
2. **`scopeCount` / `componentCount`**：`ChannelCostVersionSummaryResponseBody` 新增这两个 int 字段。
   - 查询：`ChannelCostPlanVerMapper.selectContentCountsByPlan`，用 `t_channel_cost_plan_ver` 左连接 scope、component，按 `v.id` 分组汇总，一次查询，没有逐版本 N+1。
   - 组装：经 `ChannelCostPlanVerRepository.findContentCountsByPlan` 读取，在 `ChannelCostPlanServiceImpl.getPlan` 中由 `ChannelCostPlanView.of(plan, versions, counts)` 组装。
   - 结果类型为新增的 `ChannelCostVersionContentCount`。
   - 原来的两参 `of` 保留给创建方案时使用，此时计数为 0。
3. **`externalMidCode`**：`ChannelMidApi.listChannelMids` 新增该可选 Query 参数。
   - 签名顺序：`(channelId, channelAccountId, entityNo, status, externalMidCode, page, size)`，格式 `@Pattern [A-Za-z0-9_.-]{1,128}`。
   - `ChannelMidListCriteria` 增加 `externalMidCode` 字段，以及 `getExternalMidCodePrefix()`：转义 `\`、`%`、`_`，下划线按文字处理。
   - `summaryFilter` 增加 `m.external_mid_code LIKE CONCAT(#{criteria.externalMidCodePrefix}, '%')`。
   - Converter 与 Controller 同步改签名。

**新增用例**
- `ChannelCostVersionLifecycleTest.test_versionIdAndContentCounts`：两个范围、三个组成；校验创建响应、版本详情、方案摘要三处的 `costVerId` 与 `t_channel_cost_plan_ver.id` 一致，并校验两项计数。
- `ChannelMidListTest.test_externalMidCodePrefix`：精确前缀命中 1 条；公共前缀命中 2 条；`test_mid` 中的下划线按文字处理，命中 0 条；`test*` 返回 400。
- 同时把该类的 `@see` 签名同步改为 7 个参数。

**测试**：`-Dtest='com.paytech.channel.connector.**'` → 187 例，0 失败，`BUILD SUCCESS`。

**改动文件**
- api：
  - `cost/response/ChannelCostVersionResponseBody.java`
  - `cost/response/ChannelCostVersionSummaryResponseBody.java`
  - `channel/ChannelMidApi.java`
- core：
  - `domain/ChannelCostVersionView.java`
  - `domain/ChannelCostPlanView.java`
  - `domain/ChannelCostVersionContentCount.java`（新增）
  - `domain/ChannelMidListCriteria.java`
  - `core/dal/mapper/ChannelCostPlanVerMapper.java`
  - `resources/mapper/cost/ChannelCostPlanVerMapper.xml`
  - `resources/mapper/channel/ChannelMidMapper.xml`
  - `domainservice/repository/ChannelCostPlanVerRepository.java` 及 `impl/ChannelCostPlanVerRepositoryImpl.java`
  - `domainservice/service/impl/ChannelCostPlanServiceImpl.java`
- app：
  - `ext/rest/converter/ChannelCostPlanConverter.java`
  - `ext/rest/converter/ChannelMidConverter.java`
  - `ext/rest/ChannelMidController.java`
- test：
  - `auto/cost/ChannelCostVersionLifecycleTest.java`
  - `auto/channel/ChannelMidListTest.java`
- 文档：`README.md` 末尾追加一段。
