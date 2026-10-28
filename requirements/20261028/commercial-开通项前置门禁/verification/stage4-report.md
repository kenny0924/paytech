# REQ-01 阶段 4 · 最终报告（2026-10-28 22:20）

> 接手执行者（2026-10-28 晚）在中间报告基础上完成 A—G。worktree `/Users/kenny/Desktop/Workspace/Code-MoR-gate`（基于 `663e5e4a`，detached），**未提交、未推送、未部署、未碰 mor-test**。原"中间报告"保留在文末作为历史。

## 1. 已读路径（本次接手实际读取）

- 任务卡 `verification/task-card-stage4.md`、中间报告（本文末节）
- `technique.md` 第 7—16 节（含 13.3 原型与权限）
- 原型 PNG：scratchpad `ops/fxr3i.png`（商务 · 14）、`BMckG.png`（15 暂停）、`SFwS1.png`（16 恢复）、`wlnS2.png`（17 编辑）；未编辑任何 .pen
- `agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节
- 代码：commercial `CheckCapabilityPrerequisitesProcessor`、`SuspendAgreementCapabilityProcessor`、`AgreementServiceImpl`（suspend / resume / applyCapabilityExecutionChanges）、`AgreementCapabilityRepositoryImpl.updateExecutionStatus` + Mapper XML、`CapabilityPrerequisiteContext`（原因码参数）、`CapabilityPrerequisiteCheckHandler`、Foundation `AbstractLockProcessor`；本需求全部新测试类的方法清单与关键用例正文；ops-bff `CommercialAgreementController`、`CommercialCatalogController`（business-changes）、`PlatformOperationsSimulator`、`CommercialBrowserAutoTest`、`commercial-admin-boundary.json`；ops-web `commercial/*`（门禁相关全部）、`i18nSourceGuard.test.ts`、`scripts/i18n-export-source.mjs`、`masterData` 路由；两份迁移 / 回滚 SQL；`events.yaml`、`compensation-event-catalog.yaml`、`YamlEventCatalogAutoTest`
- 说明：`DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`COMPENSATION_EVENT_GUIDE.md`、`UNIT_TEST_GUIDE.md`、`DATABASE_DESIGN_GUIDE.md` 由前一个 agent 在实现后端时阅读；本次接手对 Java 只改了 1 个 BFF 测试、1 处 BFF 测试权限映射、1 处 Controller 注释与 1 条 JSON 样例，均照同文件既有写法，未重新通读这些规范（如需严格按"首次改动前实际读取"复核，可在阶段 5 前补读）。

## 2. 逐步完成情况

| 步骤 | 结果 |
|---|---|
| A ops-web 按原型返工 | 完成，逐项对照见第 3 节。`npm test` 1576 通过、`npm run build` 通过、`npm run i18n:export-source` 通过（commercial 877 key，en / zh-CN 一致） |
| B ops-bff 权限与样例 | `prerequisite-checks` 路由改为合同**查看**权限（`PlatformOperationsSimulator` 映射 `COMMERCIAL_CONTRACT_READ`）；暂停 / 恢复保持维护权限；Controller 类注释写明口径；新增 `test_capabilityGatePermissions`（只读可核对、不能暂停 / 恢复，维护可暂停）。BFF 样例 R01 费率卡开通项 `status: ENABLED` → `ACTIVE`（commercial 费率卡接口现在输出成员资格）。`CommercialVersionDiffHistoryAutoTest` 中的 `ENABLED` 是历史快照数据（旧格式快照原样透传），保留 |
| C 第 15.1 节测试矩阵 | 核对完毕，见第 7 节；后端无缺口需要新增（前一 agent 已覆盖），两项按设计不可覆盖，说明见第 9 节 |
| D 目录登记 | `events.yaml` 已有 `AgreementCapabilityStatusChanged`（consumers `[]`、strictOrder false）与 `QualificationChanged.consumers: [client-master, commercial]`；`compensation-event-catalog.yaml` 已登记两类型；`YamlEventCatalogAutoTest` 已同步断言，本次实跑 12/12 通过 |
| E 本机迁移 / 回滚验证 | 完成，见第 6 节（含 PENDING → SUSPENDED，两脚本各重跑一次验证幂等） |
| F 全量验证 | 全部通过，见第 8 节 |
| G 本报告 | 本文 |

## 3. 与原型逐项对照（ops-web）

| 原型要点 | 实现 | 位置 |
|---|---|---|
| 14 执行状态列四种徽标 | 列名"执行状态"；成员开通项显示执行状态徽标（等前置橙 / 已开通绿 / 前置失效暂停火山橙 / 手动暂停金）；关闭项显示成员资格"已关闭" | `CommercialParts.CapabilityStatusTags`、`ProductCapabilitySections` |
| 14 前置失效暂停下方小字原因 | 取有效核对结果第一条不满足的短原因（`prerequisite.shortReason.<CODE>`），红色小字 | `ProductCapabilitySections.lapsedReason` |
| 14 行首折叠 + "还差什么" | 成员开通项可展开（含已开通，原型 CARD_MC 有折叠箭头）；展开即核对；头部摘要"核对于 HH:mm · N 项满足 · M 项未满足 ·（检查失败数）· 按执行状态的结尾说明"；逐项：图标 / 名称 / 说明 / 跳转 | `PrerequisitePanel` |
| 14 逐项跳转链接 | Client 资格不合格 → Client 详情；OE 资格不合格 → 经营主体详情；价格版本未生效 / 价格未覆盖 → "去定价 →"（切到本合同定价页签）；检查失败 → "重新检查" | `capabilityGate.lineLink`、`PrerequisiteLinkButton` |
| 14 满足项说明 | "Client {clientId} 与经营主体 {entityNo} 资格有效""合同生效，生效中的价格版本已覆盖本开通项"；渠道身份为"暂未接入校验，渠道身份查询上线后自动纳入"（同步改了原因码文案） | i18n `prerequisite.satisfied.*` |
| 14 产品标题栏数量摘要 | "N 个开通项 · N 已开通 · N 等前置 · N 暂停"（暂停 = 两种暂停合计） | `capabilityGate.sectionCounts` |
| 14 "上次检查" + 「检查前置」 | 区头右侧；有查看权限即显示；点击展开本区未开通的行并核对整份合同 | `ProductBlock` |
| 14 "下一步"卡片 → "开通项生效" | 页签上方卡片"下一步 · 开通项生效"+说明，⚠ 列出卡住原因（"CARD_VISA 等前置：没有生效的价格版本；APPLE_PAY 因价格未覆盖暂停；X 已手动暂停，需手动恢复"），原因在定价时显示「去定价」；"产品与开通"页签带提示点 | `AgreementDetail.NextStepCard`、`capabilityGate.blockedCapabilities` |
| 14 行操作 编辑+暂停 / 编辑+恢复 | 文字链接；暂停为红色；只读用户不显示操作列 | `ProductBlock` |
| 15 暂停弹窗 | 标题"暂停开通项 · CODE"；摘要（开通项代码 · 名称、经营主体、当前执行状态、合同号）；红色"暂停后立即生效"三条影响；"暂停原因（写入变更历史）"必填、"必填，最多 200 字"；红色"暂停" | `AgreementModals.SuspendCapabilityModal` |
| 16 恢复弹窗 | 摘要（开通项、当前执行状态、暂停人 · 时间）；暂停原因；蓝色"恢复后不会直接开通"+ 手动暂停 → 等前置 → 已开通（三项前置全部满足时）+ 说明；按钮"恢复并检查"。暂停人 / 时间 / 原因读合同变更历史第一页（50 条，最新在前）中该开通项最近一次 `AGREEMENT_CAPABILITY_SUSPENDED` 快照的 `createBy` / `createTime` / `data.suspensionReason`；读不到显示"—"、不阻断恢复 | `ResumeCapabilityModal`、`capabilityGate.lastSuspension` |
| 17 编辑 | 顶部摘要含"执行状态（系统维护）"；"合同内状态"只有 纳入 / 关闭 + 原型说明；底部说明"执行状态由系统按前置条件维护，这里不能直接开通…" | `CapabilityDrawer` |
| 11c 新增按钮"添加"+提示 | 新增时确定按钮"添加"；CLIENT 合同顶部提示"添加后进入等前置，系统自动检查。" | `CapabilityDrawer` |
| 权限 | 查看权限：展开 + 检查前置；维护权限（且合同状态可维护开通项）：编辑 / 暂停 / 恢复 / 新增 | `capabilityGate.capabilityGateActions`；`AgreementDetail` 传 `canView: true` |
| i18n | 新增约 60 个 key（en + zh-CN 种子），删除 6 个不再使用的 key（负责方 4、旧暂停提示 1、区头数量 1）；源码无中文（`i18nSourceGuard` 通过） | `src/i18n/en/commercial.json`、`i18n/zh-CN/commercial.json` |

## 4. 偏离方案与理由

1. **进入合同详情时自动核对一次**（仅 CLIENT 合同存在等前置或前置失效暂停的成员开通项时）。理由：原型 14 的"开通项生效"卡片与"前置失效暂停"小字都需要原因，而合同详情接口不含核对结果（S-04 不落检查结果列）。代价：打开页面可能触发一次门禁迁移并推进合同版本（与 technique 12 节"已知副作用"同性质）。若不接受，可改为只在展开 / 点「检查前置」后显示原因 → 见待裁决 1。
2. **编辑仍是右侧抽屉**而非原型 17 的居中弹窗：新增与编辑共用既有 `CapabilityDrawer`（含可开通项卡片、条件维度编辑），原型的内容要点（摘要、系统维护的执行状态、纳入 / 关闭、说明）全部放进抽屉；没有拆成两个组件。
3. **原型 14 的生命周期步骤条（起草 → 签署 → 生效 → 开通 → 定价 → 可交易）与"下一步"卡片原本都不存在于现有页面**，本次只新增"开通项生效"卡片，未实现步骤条（超出 REQ-01 范围）。
4. **开通项表沿用现有"接受条件"一列**，未按原型拆成"币种约束 / 其他约束"两列；保证金、生效期列沿用现有格式。
5. **恢复弹窗的暂停人显示 Account ID**（原型为姓名）：BCR 只有操作人 ID，ops-web 现有变更历史页同样显示 ID。
6. 去掉了前一 agent 加的"负责方：合规 / 商务 / 渠道"（原型无此列，中间报告第 5 条偏离之一）；行内「检查前置」按钮移到产品区头（原型位置）。
7. 中间报告列出的前一 agent 偏离：coverageGaps 改看成员资格 ACTIVE（与 technique 第 8、9 节"定价看成员资格"一致，保留）；已开通项也可展开（与原型一致，保留）；新枚举 i18n key 用大驼峰（`enum.AgreementCapabilityExecutionStatus.*`，与 technique 第 10.3 节"`commercial.enum.CapabilityPrerequisiteReason.<CODE>`"同口径，保留）。

## 5. 待裁决

1. 进入页面自动核对（偏离 1）是否接受。
2. 费率卡页面（`CommercialRateCards.tsx`）没有执行状态口径：commercial 费率卡接口只输出成员资格 `status`（现为 ACTIVE / DISABLED），页面按成员资格展示；是否要在费率卡上加执行状态需另定（会改 commercial 费率卡接口）。
3. 运营路由注册（阶段 6 注册块）：3 条新 Route 的 routeCode 已在 BFF 样例中定为 `operations-commercial-capability-suspend` / `-resume` / `-prerequisite-check`，权限分别为合同维护 / 合同维护 / **合同查看**；注册块编号与 Platform 种子在阶段 6 核对。
4. 原因码 `params` 参数名已与后端对齐（`subjectType`、`subjectId`、`entityNo`、`termType`、`agreementStatus`），中间报告待裁决 3 可关闭。
5. 编辑是否要按原型改成居中弹窗（偏离 2）。

## 6. 本地 SQL 记录（只操作本机 `mor-local-mysql-1:13306/mor_commercial`）

| 时间（+04） | 动作 | 结果 |
|---|---|---|
| 22:06:16 | 插入 5 行验证数据（agreement_id `990000000000000001`）：ACTIVE+ENABLED / ACTIVE+PENDING_PREREQUISITES / ACTIVE+SUSPENDED_BY_PREREQUISITE / ACTIVE+SUSPENDED_MANUALLY / DISABLED+NULL | 成功（迁移前表为空） |
| 22:06:16 | 执行 `2026-10-28-capability-execution-status-rollback.sql`，再重跑一次 | 映射 ENABLED / **SUSPENDED（含 PENDING）** / SUSPENDED / SUSPENDED / DISABLED；`execution_status` 列已删除；`idx_agreement_capability_execution` 已删除；`idx_agreement_capability_lookup` 恢复为 `(entity_no, capability_code, status)`；重跑全部走 skipped 分支 |
| 22:06:25 | 执行 `2026-10-28-capability-execution-status.sql`，再重跑一次 | 映射 ACTIVE+ENABLED / ACTIVE+SUSPENDED_MANUALLY ×3 / DISABLED+NULL；两条索引为新定义；`status` 列注释为新值域；重跑全部 skipped |
| 22:06:25 | 删除 5 行验证数据 | 表恢复为空，结构为新结构（后续 commercial auto 测试 246 例全部通过） |

说明：前滚 → 回滚 → 前滚后，原 PENDING / SUSPENDED_BY_PREREQUISITE 都变为 SUSPENDED_MANUALLY，这是 technique 8.3 规定的"回滚后不能凭空开通"的预期损失，需运营在回滚后再前滚时手动恢复。

data-query 三条模板：把 `#{clientId}` / `#{agreementId}` 替换为字面量后在本机 mor_commercial（空表）只读执行，三条均返回单行 `satisfied=0` 与正确 `missing`（AGREEMENT_NOT_DRAFTED / AGREEMENT_NOT_ACTIVE / CAPABILITY_NOT_ENABLED）；正向路径在阶段 5 联调数据上验证。

## 7. 第 15.1 节测试矩阵核对

| 入口 | 场景 | 用例 |
|---|---|---|
| HTTP 新增 | CLIENT → ACTIVE+PENDING 并登记检查（提交后核对为 ENABLED）；PARTNER → ENABLED、无检查任务、不查资格 | `CapabilityExecutionControlHttpTest.test_add_clientPendingThenChecked` / `test_add_partnerEnabledWithoutCheck` |
| HTTP prerequisite-checks | 全满足 → ENABLED + 发布任务 + BCR | `CapabilityPrerequisiteCheckHttpTest.test_allSatisfied_enabled` |
| | Client / OE 资格不合格 → 保持 PENDING、原因码与参数、不写库不发布 | `test_qualificationUnsatisfied_staysPending` |
| | 合同未 ACTIVE | `test_agreementNotActive` |
| | 价格版本未 ACTIVE | `test_termVersionNotActive` |
| | 价格未覆盖 | `test_priceNotCovered` |
| | ENABLED → 资格撤销 → SUSPENDED_BY_PREREQUISITE → 恢复 ENABLED | `test_enabledRevokedThenRestored` |
| | 资格服务 503 → UNKNOWN、不迁移 | `test_qualificationUnavailable_unknown` |
| | SUSPENDED_MANUALLY（及 DISABLED）不被触碰 | `test_manualSuspensionAndDisabledUntouched` |
| | PARTNER 合同 400 | `test_partnerAgreementRejected` |
| HTTP 暂停 / 恢复 | 正常暂停；重复暂停 / 关闭项 / PARTNER 400；版本不符 409；原因空白字段错误 | `test_suspend_normalFlow` / `test_suspend_businessRuleViolation` |
| | 恢复 → PENDING 并登记检查（提交后核对为 ENABLED）；非手动暂停不能恢复 | `test_resume_normalFlow` / `test_resume_notManuallySuspended` |
| HTTP PUT 成员资格 | DISABLED → execution NULL；重新纳入 → PENDING；Body 传 ENABLED → 字段错误 | `test_membership_disableAndReinclude` |
| 价格激活回归 | PENDING 开通项所在合同价格版本可激活，激活后核对为 ENABLED；已暂停开通项仍要求覆盖 | `TermActivationPrerequisiteHttpTest` 2 例 |
| 合同激活追加登记 | 有待前置项时登记并核对；无待前置项不登记 | `AgreementActivationPrerequisiteHttpTest` 2 例 |
| Checkout | 只输出 ENABLED；PENDING / 两种暂停均不输出 | `CheckoutResolutionMatrixTest.test_notEnabledCapabilityFallsThrough`（参数化）/ `test_notEnabledFallsThrough` |
| 补偿 Handler | 检查：成功、终态（合同不存在 / 事件非法）、可重试（503、Root 缺失）；发布：Payload `changes[]`（含旧格式上一快照）、不可发布终态 | `CapabilityPrerequisiteHandlerTest` 5 例 |
| MQ 消费者 | CLIENT REVOKED 暂停该 Client 全部合同；OE 维度只影响该 OE；LOCAL_PARTNER 忽略；信封非法进死信 | `QualificationChangedListenerTest` 4 例 |
| Job | PENDING 范围；ALL 含 ENABLED；分片不重叠 | `CapabilityPrerequisiteRecheckJobTest` 3 例 |
| 存量迁移 | 本机执行迁移 / 回滚 / 再迁移 | 第 6 节；`CommercialDatabaseFilesTest` 校验 DDL 文件一致 |
| ops-bff | 3 条转发、边界拒绝、回执身份不符 502、权限（只读可核对 / 不能暂停恢复） | `CommercialBrowserAutoTest`（`test_capabilityGateBoundary`、新增 `test_capabilityGatePermissions`、样例行 C11—C13 的通用转发 / 拒绝用例） |
| ops-web | 门禁操作与权限、核对结果解码与过期、还差什么逐项与跳转、区头摘要、开通项生效卡片、恢复弹窗暂停记录、i18n 源码守卫 | `capabilityGate.test.ts` 5 例、`i18nSourceGuard.test.ts` |

## 8. 测试命令与结果

环境：`JAVA_HOME=jdk-25`；`./mvnw -o -Dmaven.repo.local=$HOME/.m3-gate -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`；数据库 / Redis / RabbitMQ 凭据从 `local-environment/.env.local` 导出（未打印）。首次运行未导出凭据时 RabbitMQ 认证失败、上下文无法启动（239 错误），导出后重跑全绿。

| 命令 | 结果 | 新增 / 回归 |
|---|---|---|
| `-pl commercial/paytech-commercial-test test` | 246 例，0 失败 0 错误，1 分 51 秒 | 阶段基线 149 例回归；本需求新增 97 例（前一 agent），本次未新增 |
| `-pl operations-console-bff/paytech-operations-console-bff-app,…-test install`（全量） | 2440 例，0 失败 | 全量回归（新增测试前运行） |
| `-pl …bff-test test -Dtest=CommercialBrowserAutoTest,CommercialAggregationAutoTest,OperationsConsoleBffApplicationAutoTest` | 124 例，0 失败（CommercialBrowserAutoTest 109） | 本次新增 1 例 `test_capabilityGatePermissions`；全量合计 2441 |
| `-pl foundation/paytech-messaging test -Dtest=YamlEventCatalogAutoTest` | 12 例通过 | 目录回归 |
| 全 reactor `test-compile` | 121 个模块 BUILD SUCCESS | 编译 |
| `-pl tools/arch-rules,tools/arch-rules-reactor-test test` | 70 + 5 例通过 | 架构规则 |
| ops-web `npm test` | 1576 通过 0 失败 | 返工前 1574；`capabilityGate.test.ts` 由 3 例改写扩充为 5 例 |
| ops-web `npm run build` | 通过（tsc + vite；chunk 体积警告为既有） | — |
| ops-web `npm run i18n:export-source` | 通过；25 个命名空间、9820 key，commercial 877 | — |

## 9. WRITE_API_EXAMPLE 第 11 节逐项核对（暂停 / 恢复 / 前置核对三个写接口）

| 项 | 核对 |
|---|---|
| API 与 Body 最小合同 | `SuspendAgreementCapabilityRequestBody`（`expectedChangeVersion` 必填、`reason` 必填 ≤ 200）、`ResumeAgreementCapabilityRequestBody`；前置核对无 Body；Hibernate Validator 在 Controller 触发；非 HTTP 入口（Handler / Listener / Job）由信封或参数校验进入同一 Processor |
| Controller / Converter 仅适配 | `AgreementController` 三端点只转 Request 调 Processor；Processor 位于 commercial core |
| Processor 查本模块 Repository、不逐表写 | 三个 Processor 只读 Repository，写入经 `AgreementService.suspendCapability` / `resumeCapability` / `applyCapabilityExecutionChanges`；Processor 不引用 Mapper |
| 具名 Context | `AgreementContext`（暂停 / 恢复）、`CapabilityPrerequisiteContext`（门禁，纯方法 `decide`，无外部访问） |
| Service 不调用外部 / 其他 Service | `AgreementServiceImpl` 只做 Root CAS、条件更新、快照与补偿任务登记 |
| 本地原子写入与事务位置 | `TransactionTemplate`（`writeTransaction`）短事务；compliance 资格查询在事务外、锁内完成；发布与前置检查经补偿任务在提交后执行 |
| Repository 条件 SQL + 行数 + 回写 | `updateExecutionStatus` 条件 `status='ACTIVE' AND execution_status=原值`，行数 ≠ 1 抛 `ConcurrentRequestException`，成功后回写原对象 |
| 锁 | `agreement:{id}` LOCK，与合同其他写入同一资源锁（`AbstractAgreementWriteProcessor`）；数据库 Root CAS 保留 |
| 原请求比较 / 重放 | 暂停 / 恢复不是带业务操作身份的命令：重复暂停按业务规则 400、版本不符 409；HTTP 重放由 Gateway `Idempotency-Key` 负责（ops-web 每次核对新 Key） |
| Gateway 重放与业务防重区分 | 业务模型不携带 Idempotency-Key，无旧命令幂等组件 |
| View / DataObject 边界 | 写接口无新增 View；响应为 API Body，DataObject 未出模块 |
| 公共包 / 无生产 Mock | 复用 Foundation 锁、补偿事件、消息；渠道身份固定 SKIPPED，无客户端、无开关（technique 9.3） |
| 实际验证 | 见第 7、8 节 |

## 10. 未覆盖项

- 发布 Handler"Broker 不可用可重试"：auto 测试不得停止共享本地 RabbitMQ 容器，现有 `AgreementStatusChangedHandler` 测试同样未覆盖；Handler 对发布异常走补偿框架通用重试。
- 检查 Handler"锁冲突可重试"：门禁锁为 LOCK（阻塞等待）而非 TRY_LOCK，不会产生锁冲突；同一 `ConcurrentRequestException | ErrorException` 重试分支已由"Root 缺失"用例覆盖，Root CAS 竞争不可确定性复现。
- 浏览器走查（en / zh-CN、1440 / 390）未做：属阶段 5 本机联调；本次只有组件纯函数测试、类型检查与构建。
- data-query 模板正向路径（满足时 `satisfied=1` 与 out 列）待阶段 5 联调数据验证。

## 11. 文件清单（相对 worktree，`git status --short -uall`）

| 状态 | 文件 |
|---|---|
| ` M` | `operations-console-web/i18n/zh-CN/commercial.json` |
| ` M` | `operations-console-web/src/commercial/AgreementDetail.tsx` |
| ` M` | `operations-console-web/src/commercial/AgreementModals.tsx` |
| ` M` | `operations-console-web/src/commercial/CommercialParts.tsx` |
| ` M` | `operations-console-web/src/commercial/PriceEditorDrawer.tsx` |
| ` M` | `operations-console-web/src/commercial/PriceMatrixView.tsx` |
| ` M` | `operations-console-web/src/commercial/ProductCapabilitySections.tsx` |
| ` M` | `operations-console-web/src/commercial/benchmark.test.ts` |
| ` M` | `operations-console-web/src/commercial/benchmark.ts` |
| ` M` | `operations-console-web/src/commercial/capabilityConditions.test.ts` |
| ` M` | `operations-console-web/src/commercial/capabilityConditions.ts` |
| ` M` | `operations-console-web/src/commercial/capabilityPricing.css` |
| ` M` | `operations-console-web/src/commercial/commercial.test.ts` |
| ` M` | `operations-console-web/src/commercial/commercial.ts` |
| ` M` | `operations-console-web/src/commercial/productCapabilities.test.ts` |
| ` M` | `operations-console-web/src/i18n/en/commercial.json` |
| ` M` | `operations-console-web/src/pages/CommercialRateCards.tsx` |
| ` M` | `paytech/commercial/README.md` |
| ` M` | `paytech/commercial/compensation-event-catalog.yaml` |
| ` M` | `paytech/commercial/docs/database/commercial.pdma.json` |
| ` M` | `paytech/commercial/docs/database/sql/commercial-schema.sql` |
| ` M` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/AgreementApi.java` |
| ` M` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/request/UpdateAgreementCapabilityRequestBody.java` |
| ` M` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/response/AgreementCapabilityDetail.java` |
| ` M` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/response/AgreementCapabilityResponseBody.java` |
| ` M` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/enums/AgreementCapabilityStatus.java` |
| ` M` | `paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/configuration/CommercialEventConfiguration.java` |
| ` M` | `paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/configuration/CommercialPersistenceConfiguration.java` |
| ` M` | `paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/configuration/CommercialWriteConfiguration.java` |
| ` M` | `paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/ext/rest/AgreementController.java` |
| ` M` | `paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/ext/rest/AgreementConverter.java` |
| ` M` | `paytech/commercial/paytech-commercial-app/src/main/resources/application.yml` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/core/dal/dataobject/AgreementCapability.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/core/dal/mapper/AgreementCapabilityMapper.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/AgreementContext.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/AgreementDetailView.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/BusinessChangeSnapshot.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/CapabilityAcceptance.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/CheckoutCandidateContext.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/CheckoutSubscriptionContext.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/CommercialEventPayloads.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/enums/BusinessChangeReason.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/repository/AgreementCapabilityRepository.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/repository/impl/AgreementCapabilityRepositoryImpl.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/service/AgreementService.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/service/CommercialEventPublicationService.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/service/impl/AgreementServiceImpl.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/service/impl/CommercialEventPublicationServiceImpl.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/validator/term/TermPriceRelevance.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domainservice/validator/term/TermVersionActivationValidator.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/ActivateAgreementProcessor.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/ActivateCommercialTermVersionProcessor.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/AddAgreementCapabilityProcessor.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/UpdateAgreementCapabilityProcessor.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/response/AgreementCapabilityResponse.java` |
| ` M` | `paytech/commercial/paytech-commercial-core/src/main/resources/mapper/AgreementCapabilityMapper.xml` |
| ` M` | `paytech/commercial/paytech-commercial-test/pom.xml` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/CommercialTestApplication.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/database/CommercialDatabaseFilesTest.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/http/agreement/AgreementSubscriptionHttpTest.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/http/catalog/ProductCatalogWriteHttpTest.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/integration/CheckoutResolutionMatrixTest.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/repository/AgreementSubscriptionRepositoryTest.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/base/write/AbstractCommercialWriteApiTest.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/builder/AgreementCapabilityBodyBuilder.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/builder/AgreementCapabilityBuilder.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/checker/write/BusinessChangeChecker.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/clear/CommercialDataCleaner.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/dal/CommercialTestMapper.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/probe/CommercialEventProbe.java` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/resources/application-mock.yml` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/resources/fixtures/commercial/checkout-query.json` |
| ` M` | `paytech/commercial/paytech-commercial-test/src/test/resources/init/ddl.sql` |
| ` M` | `paytech/foundation/paytech-messaging/catalog/events.yaml` |
| ` M` | `paytech/foundation/paytech-messaging/src/test/java/com/paytech/foundation/messaging/auto/catalog/YamlEventCatalogAutoTest.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/ext/rest/CommercialAgreementController.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/CommercialAgreementClient.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/impl/CommercialAgreementClientImpl.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/service/model/CommercialAgreementModels.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/CommercialAggregationAutoTest.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/CommercialBrowserAutoTest.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/base/CommercialBrowserAutoTestBase.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java` |
| ` M` | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/resources/commercial-admin-boundary.json` |
| `??` | `operations-console-web/src/commercial/capabilityGate.test.ts` |
| `??` | `operations-console-web/src/commercial/capabilityGate.ts` |
| `??` | `operations-console-web/src/commercial/useCapabilityPrerequisites.ts` |
| `??` | `paytech/commercial/docs/database/sql/changes/2026-10-28-capability-execution-status-rollback.sql` |
| `??` | `paytech/commercial/docs/database/sql/changes/2026-10-28-capability-execution-status.sql` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/request/ResumeAgreementCapabilityRequestBody.java` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/request/SuspendAgreementCapabilityRequestBody.java` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/agreement/response/CapabilityPrerequisiteCheckResponseBody.java` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/enums/AgreementCapabilityExecutionStatus.java` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/enums/CapabilityPrerequisiteCode.java` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/enums/CapabilityPrerequisiteResult.java` |
| `??` | `paytech/commercial/paytech-commercial-api/src/main/java/com/paytech/commercial/api/event/AgreementCapabilityStatusChangedPayload.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/core/constant/CapabilityPrerequisiteReasons.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/CapabilityPrerequisiteContext.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/domain/CapabilityPrerequisiteDecision.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/ext/job/CapabilityPrerequisiteRecheckJobHandler.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/ext/mq/QualificationChangedListener.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/handler/AgreementCapabilityStatusChangedHandler.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/handler/CapabilityPrerequisiteCheckHandler.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/CheckCapabilityPrerequisitesProcessor.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/RecheckDueCapabilityPrerequisitesProcessor.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/RecheckForQualificationChangeProcessor.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/ResumeAgreementCapabilityProcessor.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/SuspendAgreementCapabilityProcessor.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/request/CheckCapabilityPrerequisitesRequest.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/request/RecheckDueCapabilityPrerequisitesRequest.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/request/RecheckForQualificationChangeRequest.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/request/ResumeAgreementCapabilityRequest.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/request/SuspendAgreementCapabilityRequest.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/response/CapabilityPrerequisiteCheckResponse.java` |
| `??` | `paytech/commercial/paytech-commercial-core/src/main/java/com/paytech/commercial/service/processor/response/CapabilityPrerequisiteRecheckResponse.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/eventhandler/CapabilityPrerequisiteHandlerTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/http/agreement/AgreementActivationPrerequisiteHttpTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/http/agreement/CapabilityExecutionControlHttpTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/http/agreement/CapabilityPrerequisiteCheckHttpTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/http/term/TermActivationPrerequisiteHttpTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/job/CapabilityPrerequisiteRecheckJobTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/auto/ext/mq/QualificationChangedListenerTest.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/mocker/compliance/ComplianceEventMocker.java` |
| `??` | `paytech/commercial/paytech-commercial-test/src/test/java/com/paytech/commercial/test/mocker/local/agreement/CapabilityGateMocker.java` |

需求目录（主工作区）：`db/ddl.sql`（注释索引）、`db/data-query-templates.sql`（3 条模板）、本报告。

---

## 历史：中间报告（2026-10-28 17:50，主会话写）


> 开发 agent 未能及时响应暂停指令；主会话在**无构建 / 测试进程运行**时停止了它，并由主会话核对现场后写本报告。agent 自己的完成报告尚未产出，下列"完成情况"来自文件与测试报告证据，不是 agent 自述。

### 现场

| 项 | 状态 |
|---|---|
| worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-gate`（基于 `663e5e4a`，detached），125 个文件变动（已跟踪 85 个，+1937 / −206，另有新增文件），**未提交** |
| 私有 Maven 仓库 | `~/.m3-gate` |
| 编译 | 17:50 主会话执行 `-pl commercial/paytech-commercial-test,operations-console-bff/paytech-operations-console-bff-test -am test-compile`：**通过**；ops-web `tsc --noEmit`：**通过** |
| 本机库 | `mor_commercial` 已按迁移脚本改为新结构（auto 测试依赖新列且通过，可证明已迁移）；**回滚脚本是否在本机验证过未知**（agent 未留记录） |
| 需求目录产出 | `db/ddl.sql`（注释索引）、`db/data-query-templates.sql`（3 条模板）已写于 17:38 |

### 最近一次测试结果（surefire 报告）

| 模块 | 时间 | 结果 |
|---|---|---|
| commercial（paytech-commercial-test） | 17:37 | 46 个测试类、246 例，0 失败 0 错误 0 跳过（阶段开始时基线 149 例） |
| ops-bff（paytech-operations-console-bff-test） | 17:45 | 62 个测试类（含 `-am` 依赖）、2440 例，0 失败 |
| ops-web | 子 agent 早先报告 `npm test` 1574 通过、build 与 i18n:export-source 通过 | **该结果早于按原型返工**，返工后未再跑 |

注意：ops-bff 的 `CommercialAgreementController.java` 在 17:45 测试之后（约 17:46）又被修改（推测为按"查看权限可检查前置"调整），**改后未跑测试**。

### 按任务卡步骤的完成情况（依据文件证据）

| 步骤 | 状态 | 证据 |
|---|---|---|
| 1 数据模型、DDL、迁移 / 回滚、PDManer | 完成 | `docs/database/sql/changes/2026-10-28-capability-execution-status*.sql`、`commercial-schema.sql`、`commercial.pdma.json`、测试库 `init/ddl.sql` |
| 2 定价 / Checkout 口径 + 回归 | 完成（回归通过） | `CapabilityAcceptance`、`TermPriceRelevance`、`TermVersionActivationValidator`、`CheckoutSubscriptionContext` 已改；246 例通过 |
| 3 门禁 Processor + `prerequisite-checks` | 代码完成 | `CheckCapabilityPrerequisitesProcessor`、`CapabilityPrerequisiteContext / Decision` |
| 4 暂停 / 恢复、新增 / PUT 规则 | 代码完成 | `Suspend/ResumeAgreementCapabilityProcessor`、`Add/UpdateAgreementCapabilityProcessor` |
| 5 两个补偿 Handler + 激活追加登记 | 完成并有测试 | `CapabilityPrerequisiteHandlerTest`、`TermActivationPrerequisiteHttpTest`、`AgreementActivationPrerequisiteHttpTest` |
| 6 `QualificationChanged` 消费者 | 完成并有测试 | `QualificationChangedListenerTest`、`ComplianceEventMocker`、`application.yml` |
| 7 重查 Job | 完成并有测试 | `CapabilityPrerequisiteRecheckJobTest` |
| 8 ops-bff | 完成，权限调整后未重测 | 见上 |
| 8 ops-web 按原型返工 | **进度未知** | `AgreementDetail.tsx`、`ProductCapabilitySections.tsx`、`AgreementModals.tsx` 等有改动，是否已覆盖画板 14—17 全部要点需逐项核对 |
| 9 事件目录、补偿目录、模板 | 模板已交付；`events.yaml` / `compensation-event-catalog.yaml` 需核对 | — |
| 10 全量验证 | 未做 | 全 reactor test-compile、arch-rules、ops-web test / build / i18n 未在返工后执行 |

第 15.1 节测试矩阵是否逐项覆盖（尤其 `prerequisite-checks` 的 HTTP 场景：资格不合格、价格未覆盖、ENABLED → 前置失效暂停、503 → UNKNOWN 不迁移、SUSPENDED_MANUALLY 不被触碰、PARTNER 合同 400；暂停 / 恢复 HTTP；PUT 成员资格；Checkout 只出 ENABLED）**尚未核对**。

### 已知待裁决（来自 ops-bff / ops-web 子 agent 报告）

1. 运营路由注册与权限编码（阶段 6 注册块）——方向已定：`prerequisite-checks` 用合同查看权限，写操作用维护权限。
2. 费率卡页面（`CommercialRateCards.tsx`）没有执行状态的展示口径。
3. 原因码 `params` 的参数名需与后端对齐。
4. BFF 样例数据仍有旧值 `ENABLED`。
5. 子 agent 偏离：coverageGaps 改看成员资格 ACTIVE；"负责方"前端固定映射；已开通项也可展开；新枚举 i18n key 用大驼峰。

### 明天继续时的第一步

1. 重新派发开发 agent（或主会话亲自），先读本报告与任务卡。
2. 逐项核对 ops-web 与画板 14—17（PNG：scratchpad `ops/fxr3i.png`、`BMckG.png`、`SFwS1.png`、`wlnS2.png`；如已清理则从 `operations-console.pen` 重新导出），补齐返工。
3. 核对第 15.1 节测试矩阵，补缺失用例；核对 `events.yaml` 与补偿目录。
4. 在本机验证回滚脚本后重新迁移。
5. 全量验证：commercial、ops-bff、ops-web（test / build / i18n）、全 reactor test-compile、arch-rules；写最终报告（含 WRITE_API_EXAMPLE 第 11 节逐项核对）。
