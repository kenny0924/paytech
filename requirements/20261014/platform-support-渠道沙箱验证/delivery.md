# 渠道沙箱验证交付记录

## 1. 登记（2026-10-14）

- 用户提出：针对渠道的标准 API 实现做验证，保存参数、结果和用例。讨论后明确目标为**沙箱实测并留存验证结果**，证明当前时间、当前配置下预期正确。
- 用户确认：验证证据保存**脱敏后的渠道请求/响应原文**；排期**先完成 PayerMax 阶段 4（UAT 联调）**，再开始本需求。
- 阶段划分待阶段 1 开始时提出并确认。未修改代码、配置或数据库。

## 2. 范围调整（2026-10-14）

- 用户要求在 Operations 页面上完成验证（用例、参数、结果），不使用本地配置文件；PayerMax UAT 联调改为本功能的第一批用例，**本功能先做**。
- 用户确认验证用的沙箱密钥不是敏感信息，**直接存 Platform 数据库，不写入 Vault**。
- 阶段划分见[技术方案第 11 节](./technique.md#11-实施阶段每阶段交付后等待确认)（评审稿）。

## 3. 阶段 1 结果（2026-10-14，已确认）

[需求](./requirement.md)与[技术方案](./technique.md)：4 张新表与出站地址沙箱标记、用例步骤结构（模板变量与期望）、V01～V14 管理接口、轨迹与脱敏规则、异步执行、页面结构，评审点 S1～S8。未修改代码、配置或数据库。

## 4. 阶段 2 结果：数据库（2026-10-14，已确认）

用户 2026-10-14 回复“可以”确认阶段 1（S1～S8 按建议），形成[决定记录](../../../agent-docs/decisions/2026-10-14-platform-integration-sandbox-verification.md)。

**模型与 SQL：**
- Platform Manifest `09-integration.mjs` 增加四表（图组 integration 6 → 10），`t_integration.endpoints` 注释补充可选 `sandbox`；PDManer 模型重新生成，现为 **72 表、875 字段、120 唯一索引、95 访问索引、111 关系**，生成器 `--check` 通过。步骤表定为只插入（无 `update_time`），已同步技术方案第 3.4 节。
- [增量 SQL](./db/ddl.sql)（1 条只改列注释的 ALTER + 4 条 CREATE TABLE）由模型生成；完整 Schema `platform-support-schema.sql` 与测试 `init/ddl.sql` 均为 50 表。
- 测试与文档：模型、Schema（含新增量逐表一致性检查；历史六表增量检查只忽略 endpoints 注释一行）、现实场景（新增 SCN-PLT-08 第 9.2 节四表记录）共 **53/53** 通过；`PLATFORM_SUPPORT_DATABASE.md` 链接新决定。

**本机执行（用户授权“两个库都执行”）：** 以 local-environment root 账号在 `127.0.0.1:13306` 的 `mor_platform` 与 `mor_platform_external_api` 执行增量 SQL。执行前核对四表不存在、`t_integration` 存在；执行后逐列（名称、类型、可空、注释）与索引对照 PDManer，两库一致。

**代码：** `api/enums` 新增 `VerifyRunStatus`、`VerifyStepStatus`；四个 DataObject、Mapper 与 XML、Repository 接口与实现（版本 CAS、唯一索引转业务异常、运行结论只在 RUNNING 时写入一次、每用例最近运行与最近已结束运行）。`PlatformDataCleaner` 回收四表。

**验证：** 新增 `VerifyRepositorySmokeTest` 3 项；JDK 25 `./mvnw -o -pl platform-support-test -am test` **352/352** 通过。未提交。

## 5. 阶段 3 结果：Platform 执行与管理接口（2026-10-14，已确认）

用户 2026-10-14 查看表结构后回复“开始做下一步”。

**已实现（编译通过）：**
- 出站地址 `sandbox` 标记：API DTO、领域模型、JSON 编解码（只在为 true 时写出，已有数据不变）、详情视图。
- 执行引擎钩子（正式调用不传、不受影响）：执行快照记录配置版本与沙箱地址；`ExecutionContext` 带 `VerificationScope`（验证账号 + `ExecutionTrace`）；`CHANNEL_ACCOUNT_CONFIG` 在验证运行中改读验证账号（`VerifyAccountConfigFunction`，身份核对不变）；初始化项、节点、渠道交换与出口写入轨迹（认证类与受控句柄 Header 脱敏，签名保留，报文 64 KiB 截断）；流程用到的出站地址未标记沙箱时在任何外部调用前拒绝。
- 验证领域：步骤模型、`VerifyCaseValidator`（结构、步骤 ID、实现合同、模板变量、输入结构、期望与输出路径、匹配与期望值）、`VerifyTemplateRenderer`、`VerifyAssertionEvaluator`。实现中定稿：期望 `problem` 的负向用例不做输入结构校验（技术方案第 4 节已同步）；“未实现”提示同时涵盖“实现配置未通过运行前校验”。
- 14 个管理接口 V01～V14：`IntegrationVerifyManagementApi`、DTO、`IntegrationVerifyManagementController`、`VerifyManagementConverter`，15 个 Processor（含执行运行的 `ExecuteVerifyRunProcessor`，经正式入口 `ExecuteStandardApiProcessor` 执行每一步）、`IntegrationVerifyConfigService`/`IntegrationVerifyRunService`（interface + Impl）、补偿任务入口 `IntegrationVerifyRunHandler`（技术方案第 3.6 节已同步定稿与部署前提）。integration 模块新增 `paytech-compensation-event` 依赖。

**测试（新增 6 个类）：** `VerifyRunTest` 6、`VerifyAccountManagementTest` 3、`VerifyCaseManagementTest` 2、`IntegrationVerifyRunHandlerTest` 3、纯 Java `VerifyEvidenceTest` 3；测试设施 `VerifyRunFixture`（等待补偿任务结束后再清理）、`VerifyTestMapper`、`ExternalApiMocker.integration(code, baseUrl, sandbox)`。
- 已运行：`VerifyRunTest` **6/6**（真实 HTTP → 补偿执行器异步执行 → 渠道 Mock；首次 6 例均因测试把原子 API 编码写错而 422，修正后 5/6，余下 1 例暴露上述负向用例校验问题，修正后 6/6）；`VerifyEvidenceTest`、`ScriptSigningFunctionsTest`、`ExpressionEngineTest` 通过；执行相关回归（`StandardApi*Test`、`IntegrationManagementTest`）在加入引擎钩子后通过。
- **未运行：** `VerifyAccountManagementTest`、`VerifyCaseManagementTest`、`IntegrationVerifyRunHandlerTest` 与 Platform 全量 `auto`。原因：本机共享测试库 `mor_platform` 中出现客户入驻会话 10:12 写入的 `t_file_scan_policy`（CLIENT_ONBOARDING_FORMAT）与 `t_file_scan_requirement` 各 1 行，Platform 测试全局前置检查拒绝在非空库运行。用户决定等待该会话自行清理后再运行，本会话不删除。

未执行新的 SQL，未提交。

**测试库清理与全量运行（2026-10-14）：** 经核对，上述 2 行是用户授权客户入驻会话写入的本机格式检查策略（见[执行记录](../../20260914/client-console-注册与客户入驻/db/implementation/platform-file-scan-policy-local-execution.md)），与 Platform 自动化测试共用 `mor_platform` 冲突。用户要求本会话清理：先核对 `t_file_object`、`t_file_upload`、`t_file_owner_ref`、`t_file_scan_result` 均为 0（满足该记录的回滚前提），再按其回滚语句删除 `requirement_id=9200000000000002` 与 `policy_id=9200000000000001`，回读两表为 0。入驻会话如需本机调试需重新执行其 SQL；建议其手工配置改用独立本机库。
- 随后运行：`VerifyAccountManagementTest` 3/3、`VerifyCaseManagementTest` 2/2、`IntegrationVerifyRunHandlerTest` 3/3；JDK 25 `./mvnw -o -pl platform-support-test -am test` **413/413**（0 失败、0 错误、0 跳过；含本需求新增 20 项及其他会话新增的测试）。

## 6. 阶段 4 结果：BFF、注册 SQL 与页面（2026-10-14，已确认）

用户 2026-10-14 回复“可以”进入阶段 4。

**Platform 小调整：** V12 发起运行增加请求体 `{reason}`（必填，保存在运行快照 `reason`，详情页展示），`VerifyRunTest` 等验证相关测试修改后重新通过。

**BFF（operations-console-bff）：** 生成验证 DTO，出站地址请求/详情增加 `sandbox`；`ExternalApiContractConverter` 增加验证转换；`PlatformIntegrationVerifyManagementClient`（+Impl，`@HttpExchange`）、`IntegrationVerifyManagementController` 透传 V01～V14，`PlatformClientConfiguration` 注册客户端。边界样例 `external-api-boundary.json` 增加 V01～V14。`ExternalApiBrowserAutoTest` **102/102**。

**注册 SQL（未执行）：** [`db/registration-dml.sql`](db/registration-dml.sql) 155 行：14 条 Route（`900000000000071031`～`071044`，DISABLED）、51 条 Header、6 条查询参数、84 条 Guard；复用 `EXTERNAL_API_READ` / `EXTERNAL_API_MANAGE` 权限，不新增权限。[`db/local-e2e-activate.sql`](db/local-e2e-activate.sql) 只在本机 `mor_platform_external_api` 启用这 14 条 Route。

**页面（operations-console-web）：**
- 接入方工作区新增“验证”页签：按标准 API 汇总用例状态、验证账号列表（被用例引用时不能删除）、验证用例列表（运行 / 详情 / 编辑 / 删除）、最近运行。
- 新页面：验证账号详情/编辑/新建（`/:code/verify-accounts/:code|new`，普通参数与沙箱凭据键值对，明文保存提示）、验证用例详情/编辑/新建（`/:code/verify-cases/:code|new`，多步骤、模板变量、期望核对）、运行详情（`/:code/verify-runs/:runCode`，运行中每 2 秒刷新，展示出站地址沙箱标记、配置版本、每步实际输入/结果/期望核对与脱敏渠道报文）。
- 发起运行弹窗填写运行原因并说明会真实调用沙箱地址。
- 基本信息出站地址增加“沙箱”列与开关；打开开关需二次确认（误标生产地址会产生真实交易），只在为 true 时提交 `sandbox`。
- 验证：`npm test` **983/983**（更正：当时的 `npm test` 未覆盖 `verifyModel.test.ts`，该文件 5 项为单独运行通过，见第 9 节），`npm run build` 通过（仅有原有的 chunk 体积提示），`tsc` 与 lint 无问题。
- **未做：** 浏览器实测。验证接口的 Gateway Route 尚未注册（注册 SQL 未执行），页面只完成编译与模型测试。

未执行新的 SQL，未提交。

**阶段 5 建议范围：** 经授权在本机 `mor_platform_external_api` 执行注册 SQL 与本机启用 SQL；浏览器实测页面全流程（合成 PayerMax Mock 上先跑通）；用户在页面录入 PayerMax UAT 验证账号并把 UAT 出站地址标记为沙箱，建立“下单 → 查询 PENDING”“查询不存在订单”两个用例并运行，保存通过记录。

## 7. 阶段 5：本机端到端（2026-10-14，进行中）

用户 2026-10-14 回复“可以”授权第 1、2 步。

**SQL（本机 `mor_platform_external_api`）：** `registration-dml.sql` 先在事务内试跑并 ROLLBACK 核对（路由 14、Header 51、Query 6、Guard 84，`@plt_ext_operator=1` 沿用该库外部 API 路由的创建人），再同样执行并 COMMIT；`local-e2e-activate.sql` 更新 14 行后 COMMIT，回读 14 条 ACTIVE、版本 2。`mor_platform` 与共享环境未执行。

**本机栈：** 重新编译 Platform 与 BFF 后重启本会话的 Platform、BFF、Gateway（Web 代理与合成 PayerMax 不变；其他会话的进程未动）。

**合成渠道端到端（[harness](assets/harness/README.md)，[回执](assets/stage5-mock-receipt.json)）：** 经 Web 代理 → Gateway → BFF → Platform：
- 出站地址未标沙箱：运行 FAILED，提示“出站地址未标记沙箱，验证运行未发出请求”，合成渠道请求日志无新增。
- 标记沙箱后 `PAY_THEN_QUERY`（下单 PENDING → 查询 SUCCEEDED）、`QUERY_NOT_FOUND` 均 PASSED；页签状态“通过”。
- 证据：运行步骤的轨迹、输入、结果中检索合成私钥片段为 0；请求/响应保留 `sign` Header。
- 调试中发现：期望值不渲染模板（按技术方案第 4 节只渲染输入），首个用例写成 `${steps.pay...}` 被按字面比较而失败，改为 `EXISTS`。可选改进：编辑器对期望值中的 `${` 给出提示（未实现）。

**浏览器（Playwright 无头 Chromium）：** 登录后打开基本信息、验证页签、账号详情、用例详情/编辑、运行详情（展开节点报文）共 8 页，无页面错误、控制台错误或 4xx/5xx；沙箱开关打开时弹出确认，确认后开启、取消保持关闭（未保存）。出站地址编辑表单补充沙箱提示；`npm test` 983/983、`npm run build` 通过。

**UAT 准备：** 用户确认后执行 `configure.py PAYERMAX https://pay-gate-uat.payermax.com/aggregate-pay/api/gateway`（首次因本机 Web 代理上游连接中断 502，按版本重跑成功），创建接入方 `PAYERMAX`（出站地址未标沙箱）及其脚本、原子 API 与 `PAY`/`PAY_QUERY` 实现；标准 API 内容相同但版本递增（PAY 6、PAY_QUERY 5），`PAYERMAX_MOCK` 两个用例随即显示“需重新验证”，重跑 harness 后恢复“通过”（过期判断按设计生效）。
- 本机 harness 的登录邮箱 Vault 替身只认当前 Platform 进程登记的管理员；重启后旧邮箱登录返回 503 `DEPENDENCY_UNAVAILABLE`（测试替身行为，非生产语义）。

**PayerMax UAT（2026-10-14，用户提供测试商户材料并要求代为填写）：** 材料写入仓库外 0600 文件后经管理接口登记，先核对商户私钥与商户公钥配对一致；PayerMax 后台登录账号密码不属于验证账号，未保存。将 `PAYERMAX` 出站地址标记沙箱，验证账号 `UAT_MAIN`（凭据按用户决定明文存本机库），用例：
- `UAT_PAY_THEN_QUERY`：下单 `APPLY_SUCCESS`/PENDING 并返回收银台地址 → 查询 PENDING，**PASSED**；
- `UAT_QUERY_NOT_FOUND`：`ORDER_NOT_EXIST` → FAIL/NOT_FOUND，**PASSED**。
- PayerMax 侧验签通过（无 `SIGN_VERIFY_FAILED`）；三个响应都带 `sign` 且 Platform 验签通过，包括 FAIL 响应。证据中检索私钥片段为 0。[回执](assets/stage5-uat-receipt.json)已去掉收银台地址与报文原文。

**阶段 5 待确认。** 未覆盖：在收银台付款后查询 `SUCCEEDED`（PayerMax 需求验收第 3 项），需要人工在 UAT 收银台付款。

**提交（2026-10-14）：** 用户要求“代码提交一下”，阶段 2～5 改动本地提交，未推送；其他会话的工作区改动未纳入。

## 8. 页面设计补充（2026-10-14）

用户要求把已实现的验证页面统一补到 Pencil。`operations-console-web/docs/design.pen` 新增“外部 API 第一版”27～33（第 4 行，y=41100），按已实现页面绘制，示例数据不含真实 UAT 材料：
- 27 接入方工作区｜验证；28 接入方编辑｜出站地址沙箱标记（含确认弹窗）；29 验证账号编辑；30 验证用例编辑；31 发起验证运行；32 运行详情｜通过与报文证据；33 运行详情｜未通过（未标记沙箱）。
- 同步修改 04 接入方工作区｜基本信息：出站地址表增加“沙箱”列与第 4 条说明。

## 9. 优化：期望值中的模板变量提示（2026-10-14）

用户同意后实现：用例编辑器的期望值包含 `${...}` 时，输入框显示警告并提示“期望值按字面比较，模板变量不会替换；要核对前序步骤的值，请改用“存在”，或在后续步骤的输入中引用它”（不阻止保存）；模板变量说明补充一条。新增纯函数 `hasTemplateVariable` 及测试。
- 发现并修正：`operations-console-web` 的 `npm test` 原脚本 `src/**/*.test.ts` 未加引号，由 `sh` 展开时不递归，`src/externalApi/verify/verifyModel.test.ts` 从未被执行；改为由 Node 展开的带引号 glob 后共 **989/989**（原 983 项 + 验证模型 6 项）。`npm run build`、`tsc`、lint 通过；浏览器中输入模板变量出现提示，改回普通值后消失。

## 10. 全量回归（2026-10-14）

- Platform：JDK 25 `./mvnw -o -pl platform-support-test -am test` **415/415**（工作区含其他会话未提交的 Platform 改动）。
- BFF：首次全量 1279 项中 1 项失败：`OperationsConsoleBffApplicationAutoTest.test_applicationReusesFoundationAndExposesExactOperationsRoutes` 精确核对 BFF 暴露路由，阶段 4 新增 V01～V14 后未同步预期清单（阶段 4 当时只运行了 `ExternalApiBrowserAutoTest`，已提交的 `ed0d27e` 中存在此问题）。补齐 14 条验证路由后 `./mvnw -o test` **1279/1279**。
- 前端：`npm test` **989/989**，`npm run build` 通过。

## 11. mor-test 发布（2026-10-14）

用户确认发布外部 API、渠道沙箱验证与 PayerMax 配置（A+B+C），客户入驻随后由其会话单独发布。[发布清单](release/release.md)经批准执行：test `mor_platform` 补 25 表、登记 499 行、启用 52 行；Gateway、Platform（开启补偿）、BFF、Web 升级到 `ba36b63`；16 个菜单页检查通过；PayerMax UAT 两个用例在 test 通过。偏差（登记提前提交、启动顺序、Gateway Client 占位配置、限流 429）与遗留见[执行记录](release/test-acceptance.md)。
