# REPORT-W1 · 阶段 4 后端（T1—T4）

> 由主会话依 W1 回复落盘（子代理写主工作区文件被 harness 拦截）。主会话复核：工作树 `paytech` 改动 22 M + 30 新增（channel-connector 16、client-master 8、commercial 28）；surefire 汇总 channel-connector 404 / 0、client-master 181 / 0、commercial 252 / 0。

## 第一轮结果

| 范围 | 结果 |
|---|---|
| channel-connector 全模块 | 404 例，0 失败 |
| client-master 全模块 | 181 例，0 失败 |
| commercial 全模块 | 252 例，0 失败（首轮 5 类 9 例因未配新 Mock 失败，补配后通过） |
| 全 reactor `test-compile` | 通过 |
| `tools/arch-rules` / `arch-rules-reactor-test` | 70 / 0、5 / 0 |

## 改动

- **T1** channel-connector：`OperatingEntityMidService` + Impl、领域对象 `OperatingEntityMids`；`QueryMidCandidatesProcessor` 改用它（事务与悬空关联报错不变，原候选测试全过）。
- **T2** channel-connector：`POST /channel-connector/v1/channel-identities/readiness`（`api.readiness`、原因枚举 `ChannelIdentityReadinessReason`、`QueryChannelIdentityReadinessProcessor`、Controller、Converter）；五步判定，同组重复只判一次。
- **T3** client-master：`GET /client-master/v1/clients/{clientId}/business-classification`，查询 Service 复用现有三个 Repository，无新 Mapper。
- **T4** commercial：`ChannelIdentityReadinessClient`（每批 ≤ 200 组）、`ClientClassificationClient`；`TermVersionActivationValidator.coveredCurrencies` / `TermCoverage.coveredCurrencies`；`CapabilityPrerequisiteContext` 按 technique 10.2 判定 ④；门禁所有入口经 `CheckCapabilityPrerequisitesProcessor`；删除 `CHANNEL_IDENTITY_NOT_CONNECTED`。

## 验收对应测试

- channel-connector `ChannelIdentityReadinessHttpTest`（8 例）：AC-01—AC-05、配置冲突 500、入参 422；AC-05 用 12 组 × 9 种体系 / 国家取值逐组与 `/mid-candidates` 对照。T1 对照：`ChannelCandidateQueryHttpTest.test_multipleAccountsProjection`。
- client-master `ClientBusinessClassificationHttpTest`（5 例）：已确认、已确认无主分类、未确认、无资料、不存在 404 / ID 非法 400。
- commercial `CapabilityPrerequisiteCheckHttpTest`（新增 5 例）：AC-06—AC-10；`test_priceNotCovered`、`test_termVersionNotActive` 补 `AWAITING_PRICE` 断言；`test_allSatisfied_enabled` 的 ④ 由 `SKIPPED/CHANNEL_IDENTITY_NOT_CONNECTED` 改为 `SATISFIED`；`TermActivationPrerequisiteHttpTest`、`CapabilityExecutionControlHttpTest`、`AgreementActivationPrerequisiteHttpTest`、`CapabilityPrerequisiteRecheckJobTest`、`CapabilityPrerequisiteHandlerTest` 只补 Mock 配置。
- 新增 commercial 外部 Mock：channel-connector、client-master 各一组（意外调用失败、每例清理、断连与 Problem 故障）。

## 偏离与裁决（主会话）

| # | 偏离 | 裁决 |
|---|---|---|
| 1 | `OperatingEntityMidServiceImpl` 调协议模块 `ChannelService`（违反"Service 不调 Service"） | **不接受**：改为 Service 只读本模块仓储，Processor 调 `ChannelService` 后用领域对象纯方法过滤（第二轮） |
| 2 | 就绪查询入参非法返回 422 `VALIDATION_ERROR`（technique 写 400） | 接受：共享异常处理固定映射，与 `/mid-candidates` 一致；technique 8.3 已更正 |
| 3 | client-master Client ID 非法 400；三次读放一个只读可重复读事务 | 接受；technique 9 已补 |
| 4 | client-master 失败整单 ④ UNKNOWN；某 OE 就绪查询失败只影响该 OE | 接受 |
| 5 | 收款动作代码由 `ACTION_PREFIX` 拼 `ChannelOperation`，不加常量 | 接受 |
| 待裁决 1 | 补偿可重试消息仍写 "Qualification check unavailable…" | 改为通用表述（第二轮） |
| 待裁决 2 | client-master 404 → ④ UNKNOWN | 接受（合同上的 Client 不存在属数据异常，不改状态更安全） |

## 第二轮（主会话复核通过）

- 偏离 1 已改：`OperatingEntityMidService.loadMids(entityNo)` 只读本模块四个仓储（账号、全部 MID、全部能力行与明细），返回 `OperatingEntityMids`；判定为其纯方法 `channelIds()` / `usable(channels)`（悬空关联仍抛 `ErrorException`）；两个 Processor 在只读可重复读事务内 loadMids → `ChannelService.findChannels` → `usable`。主会话核对 Impl 中 `ChannelService` 引用为 0。唯一读库差异：能力行 / 明细读该 OE 全部 MID 的；候选结果不变（`test_multipleAccountsProjection` 与原候选测试通过）。
- 补偿消息改为 "Prerequisite check unavailable, retry required"（错误码不变），注释同步，`CapabilityPrerequisiteHandlerTest` 断言同步。
- 测试：channel-connector 404 / 0（21:30）、commercial 252 / 0（21:32）、arch-rules 70 / 0、reactor-test 5 / 0。
- W1 提醒的 `shortReason.CHECK_UNAVAILABLE` 英文文案：W2 已按前置项 select（渠道身份 "Channel identity check unavailable"），无需再改。
