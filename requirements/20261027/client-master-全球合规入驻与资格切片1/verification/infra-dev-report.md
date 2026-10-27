# 基础设施交付报告（Vault / 筛查渠道 / Mock / 块 138 预研 / mor-test 基线）

> 2026-10-27，worktree `/Users/kenny/Desktop/Workspace/Code-MoR-onboard`。未提交 git，未修改 mor-test 任何容器、库或文件（只读）。
> 本机变更：本机 channel-connector（`mor_channel_connector_v2e2e`）经管理接口新增筛查渠道配置；本机合成筛查服务在 127.0.0.1:18191 运行中（供 compliance e2e 使用，`start_screening_mock.sh stop` 可停）。

## 1. 结果一览

| # | 交付 | 文件 | 验证 |
|---|---|---|---|
| 1 | Vault DML | `db/vault/dml.sql`、`dml-check.sql`、`dml-verify.sql`、`dml-rollback.sql` | 本机与 mor-test 只读预检均为 0 占用；断网一次性 MySQL 演练：执行 → 核验 → 重复执行主键冲突中止 → 回滚恢复，全部符合预期 |
| 2 | 合成筛查服务 | `assets/harness/screening_mock_server.py`、`start_screening_mock.sh`、`Dockerfile.screening-mock`；`test-environment/compose.screening-mock.yaml` + `test-environment/acceptance/screening-mock/screening_mock_server.py`（逐字相同） | 本机 6 例（无命中 / PEP / 双命中 / FAIL / 500 / 400）；固定摘要 python 镜像只读文件系统内跑通；compose 合并后 IP 172.30.73.43 |
| 3 | 渠道登记 harness | `assets/harness/screening_channel_setup.py`、`README.md` | 本机执行：渠道、标准 API、原子 API、实现、授权全部 PASS，冒烟 6/6 PASS；重跑无变更（幂等） |
| 4 | 块 138 预研 | `db/platform-support/registration-block138.draft.md`；`assets/harness/block_compile_check.sh` + `BlockSnapshotCheck.java` | 用本机 `mor_platform_v2e2e` dump + 本机 Platform JAR：合成 DISABLED 路由 dormant / active 编译通过，越界 guard（DISABLED）被拦截（退出码 3） |
| 5 | mor-test 基线 | `release/baseline.md`；`release/compose-candidate.channel-connector.draft.yaml` | 只读 inspect / printenv / network / SELECT |

结果文件：`verification/screening-channel-local.json`（首次完整执行 + 冒烟）、`verification/screening-channel-local-rerun.json`（幂等重跑）。

## 2. Vault

- `LEGAL_IDENTIFIER_V1`：STABLE / STANDARD / TEXT，手动加解密均关；规则 `client-master-service` PROTECT、REVEAL × `CLIENT_ONBOARDING_IDENTIFIER`；ID 0760（类型、变更记录）、0761—0762（规则）；版本号 MAX+1 FOR UPDATE。
- 当前最大版本：本机 24、mor-test 20。07xx 段已用 0700、0720、0730、0740—0742、0750—0752；0760—0769 两环境均空。
- 未纳入 `RISK_SCREENING_SEARCH_TERM_V1` 与 compliance 规则（technique 2.2：本切片 compliance 不调 Vault）。

## 3. 筛查渠道

登记内容（全部经管理接口，直连 channel-connector 私网，`X-Gateway-Actor-Subject` 为十进制运营账号 ID）：

| 对象 | 值 |
|---|---|
| 标准 API | `SCREENING_SEARCH`：请求 subjectType*、fullName*、birthYear(INTEGER)、countries[]、screeningTypes[]*、clientRef*；响应 hits[]*（externalRef、entityType、listTypes[]、score DECIMAL、matchName，逐层声明）+ errorCode |
| 渠道 | `S_SCREENING_MOCK`，SERVICE，无技术账号，出站地址 `SCREENING` |
| 原子 API | `SCREENING_SEARCH_CALL`，POST `/v1/screening/search`，JSON；200 且 code==0 → SUCCESS，200 且 code!=0 → FAIL，其他 → ERROR |
| 实现 | 无 initializers；SUCCESS / FAIL / 兜底 ERROR 三出口 |
| 授权 | `compliance-service` × `S_SCREENING_MOCK` × `SCREENING_SEARCH`（本机 callGrantId 240582625239040000） |

执行中发现并在 harness 中处理的约束：
1. FAIL 出口也必须覆盖全部必填响应字段（`OUTPUT_FIELD_UNCOVERED`）；表达式函数 `seq.list` 未开放 → 改为 Mock 的 FAIL 响应带 `hits: []`，原子 API FAIL 分支按路径映射。
2. 已存在配置需按版本 PUT 才能修正：harness 比较字段集合与定义 / 流程，差异时带 `expectedVersion` 更新，否则沿用。

**给 compliance 的合同要点**：`hits[].score` 经 channel-connector 输出为 **JSON 字符串**（`"0.92"`），按 BigDecimal 解析；ERROR 时 outputs 为 `{}` 且结果未知、不得自动重试；未授权调用方 403 `CHANNEL_CALL_NOT_GRANTED`。

## 4. 块 138 预研结论

- 既有入驻 target / 权限 / 菜单 ID 由管理 API 生成，**本机与 mor-test 不同**（如 Client target 本机 236033358134513664、mor-test 236085272176050176）→ 块内一律子查询（`target_service_code`、`permission_code`、`resource_code`）。
- mor-test 既有入驻路由 guard 为 principal 60/5、IP 120/10、shared 600/50（与 registration-plan.json 原始值不同，已经管理 API 调整）；草稿建议 CLIENT 新路由抄 live 值，OPERATIONS 新路由按块 137 约定（30/2、30/3、300/50，写 10/min）。
- 编译门禁做法：复用块 137 发布包的离线编译方式（导出 6 张网关表 → 生产 `GatewayConfigurationCompiler.compile(LoadedConfiguration)`），不需要启动 Platform；`block_compile_check.sh` 接受任意步骤序列并按块 ID 段统计有效路由。正式门禁须用**候选** Platform JAR 与 mor-test 同一 MySQL 镜像 ID。
- 待决 6 项列在草稿第 7 节（BFF 路径、CP-4 / 程序查看是否新增路由、新权限拆分、菜单父级、c03 停用时机、OPERATIONS 写路由是否转发 idempotency-key）。

## 5. mor-test 要点（详见 release/baseline.md）

- channel-connector 未设 allowed-schemes / trusted-networks（https only、无可信网段）与 internal-auth（OFF）；本切片只需加两键放行 172.30.73.43/32，镜像不变。
- 合成筛查服务 IP 172.30.73.43（backend .2—.6、.21—.42 已用）。
- Snowflake worker 空闲 0—16、24；26 仍被 client-master 与 channel-connector 共用（既有问题）。
- 相关服务覆盖来源有两个在其他 worktree（Code-MoR-relsnap、Code-MoR-fileupload），候选文件应自带所需 env。

## 6. 未做 / 留给主会话

- 块 138 最终 SQL（等 BFF 路径定稿后另派）。
- 所有 mor-test 环境变更：Vault DML 执行、screening-mock 启动、channel-connector 重建、渠道登记、块 138。
- 收集 api-gateway env 时，一项访问日志 HMAC 材料值出现在本会话终端输出（未写入任何文件；该值本就在已提交的 `test-environment/compose.applications.yaml` 中）。

## 7. 块 138 SQL（OPERATIONS 部分定稿，2026-10-27）

- 文件（`db/platform-support/`）：`block138-precheck.sql`（只读）、`registration-block138.sql`（休眠注册）、`block138-activate.sql`（同时停用 `client-onboarding-c03`）、`block138-deactivate.sql` 与 `block138-rollback.sql`（均恢复 c03）；全部由 `block138-spec.json` 经 `gen_block138.py` 生成。
- 规模：permission 7、role_permission 7、console_resource 7（菜单 3、按钮 4）、route 17、header 66、query_param 10（含既有 o01 新增 `responsiblePartnerId`）、guard 102，共 216 行。guard 超过 99 行，ID 占 701—802。
- 既有对象（3 个权限、审核菜单、o01 路由）先取到变量；任一缺失触发 ERROR 1242，事务不提交。重复执行注册会在主键冲突处中止。
- 门禁（同一份 SQL，五态）：

| 数据 | Platform JAR | baseline | dormant | active | deactivated | rolled-back |
|---|---|---|---|---|---|---|
| 本机 `mor_platform_v2e2e` 只读 dump（本机 `mor_platform` 为空库） | 本机 e2e JAR | 475 | 475（块 17 行载入） | 491（+17，−1 c03） | 475 | 475，块行 0 |
| mor-test `mor_platform` 只读 dump | 运行镜像 `sha256:4859efd7…` 内 JAR（前缀 be0abf79fc3ca9eb）；MySQL 镜像 ID 与 mor-test 相同 | 585 | 585 | 601（+17，−1 c03） | 585 | 585，块行 0 |

  日志：`verification/block138-gate-local.log`、`verification/block138-gate-mortest.log`。

- 负向（暂定清单时验证，生成逻辑未变）：依赖缺失 → ERROR 1242 中止；重复注册 → 主键冲突中止。
- CLIENT 补充件：client-fe 尚未答复。生成器已支持只含路由的补充 spec（自身 ID 区间 route 441+、header 581+、query 651+、guard 901+），用 5 条草稿路由在本机 dump 上验证：OPERATIONS 休眠 + 激活后补充件预检全 0、休眠 / 激活编译通过（496 有效）、回滚后回到 491。
- G4 程序查看路由删除（无已确认的程序只读 API），已记入 draft 缺口。
- 未做：未对本机或 mor-test 写入；未追加到 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（不在本 agent 写入范围，按惯例应逐字追加，需主会话指派）；未生成 `release/sql/6x-*` 发布副本。

## 8. 筛查合同对齐与本机 Platform 库（2026-10-27）

- 按 compliance（cmp-dev）合同改登记：`birthYear` 改为 STRING；响应增 `runRef`、`hits[].matchTypes[]`（必填）、`hits[].riskLevel`（可选），`matchName` 改为可选，分值 0–100。本机经管理接口按版本更新：标准 API v3、原子 API v3、实现 v2；冒烟 6/6 PASS；组织主体、空 countries、无 birthYear 通过；数字 birthYear 被 422 拒绝。Mock 两处副本逐字相同。
- 本机 Platform 读取 `mor_platform_v2e2e`（进程 96762 的 `PLATFORM_SUPPORT_DB_URL`；`platform.pid` 文件已过期，指向 52936）。该进程打开的 JAR 与门禁使用的文件为同一 inode，本机门禁即为运行代码的编译结果。

## 9. initial-data 追加、发布副本与运行卡（2026-10-27）

- **追加方式与原因**：块 138 依赖的既有对象（3 个 `CLIENT_ONBOARDING_*` 权限、`CLIENT_ONBOARDING_REVIEW` 菜单、`client-onboarding-o01`、`client-onboarding-c03`）由 Platform 管理 API 创建，**不在** `platform-support-initial-data.sql` 中。逐字追加严格版会让全新环境初始化在块 138 处以 ERROR 1242 中止。因此追加的是 `registration-block138.initial-data.sql`（自分隔线起）：行内容与严格版相同，只把失败即中止的守卫换成整块条件插入（依赖缺失时 0 行，齐全时全部插入）。发布与 mor-test 使用严格版。两份由同一生成器产出，差异只在守卫形式，文件头已注明。
- 验证：
  - 全新环境（schema + 追加后的 initial-data，`--force` 加载）：块 138 插入 0 行；错误数与追加前相同（1 个，见下条既有问题）。
  - 本机与 mor-test dump：严格版与 initial-data 版插入的 216 行逐列相同（不含时间列），休眠编译结果相同。
  - 追加后的文件与测试用候选文件逐字节相同（+267 行）。
- **既有问题（与本需求无关）**：当前 `platform-support-initial-data.sql` 在全新库上已无法完整初始化——块 135（多语言）向 `t_gateway_route` 插入时 `target_id` 为 NULL（ERROR 1048），该块事务中止。追加块 138 前后同样存在。
- 发布副本：`release/sync_sql.sh` 把 Vault 与块 138 SQL 按执行顺序复制到 `release/sql/`（00、01、10、11、60—62、69、89）并写 `SHA256SUMS`；CLIENT 补充件生成后重跑即加入 02、63—65、68。
- 运行卡草稿：`release/runbook.md`（前提 P-1—P-10、备份命令、16 步执行顺序、验证、逆序回滚）。其中两项需他人处理：client-master 国家包需增量文件（全量 initial-data 不能在 mor-test 重跑，P-4）；合成筛查服务挂载的脚本目前只在本 worktree（P-9）。

## 10. 路由码按 ops-fe 定名（2026-10-27）与遗留缺口

- 路由码改用 ops-fe 的名称（BFF 测试使用）：工作台 7 条为 `client-onboarding-responsible-partners / -check-result / -screening-run / -hit-resolution / -risk-rating / -recommendation / -waiver`；新页 `operations-onboarding-config-*`、`operations-client-admin-list / -detail / -responsible-partners`、`operations-onboarding-code-partners / -list / -issue / -revoke`。路径、权限不变。
- 既有 `client-onboarding-o01` 增两个 Query 白名单：`responsiblePartnerId`、`jurisdictionCode`（compliance 两者都支持）。块 138 现为 217 行（query_param 11）。
- risk-rating 仍用 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`（主会话决定；ops-fe 消息中的 `CLIENT_ONBOARDING_RISK_RATE` 未采用，已告知 ops-fe）。programs 路由仍删除（cmp-dev 未确认）。
- 重新门禁（新只读 dump，五态）：本机 475 / 475 / 491 / 475 / 475；mor-test 585 / 585 / 601 / 585 / 585；回滚删除 guard 102、query 11、header 66。initial-data 变体与严格版在 mor-test dump 上 217 行逐列相同。initial-data 追加段已替换为新版本（文件 = HEAD + 268 行）；`release/sql` 已同步。

**遗留缺口（跟进项，本切片不做）**

- G-1 筛查分值整数：Mock 发送 `92.0` 这类带小数点的数值；真实服务若返回整数分值（`92`），channel-connector 对 DECIMAL 响应字段的校验是否接受未验证。接入真实筛查服务前用一个返回整数分值的 Mock 响应验证；若被拒，改为字符串或调整字段类型。
- G-2 运营控制台程序查看路由（programs）：等 compliance 提供程序只读 API 后以补充件增加（权限复用 `ONBOARDING_CONFIG_READ`）。
- G-3 块 138 CLIENT 补充件：等 client-fe 路由清单。
- G-4 `platform-support-initial-data.sql` 块 135 在全新库上 `target_id` 为 NULL（既有问题，非本需求）。

## 11. 块 138 CLIENT 补充件（2026-10-27，client-fe 清单）

- ops-fe 确认 OPERATIONS spec 为最终清单（programs 路由删除，console-resource-capabilities.json 由 ops-fe 负责）；OPERATIONS SQL 未再变化（逐字节相同）。
- client-fe 的 5 条新路由与草稿一致：`/api/client/onboarding/v1/{jurisdictions, form-schema, applications/{id}/declarations, applications/{id}/requirements, applications/{id}/withdrawal}` → `/client-onboarding/v1/...`；form-schema 白名单 `jurisdiction, partyType, organizationType`。审核结果沿用既有 c05（BFF 聚合），既有路由无需改动；c03 停用无影响。
- 路由码与形状（client-fe 第二次答复）：`client-onboarding-c17`—`c21`（本机与 mor-test 均未占用，现最大 c16）；读路由同 c16（Body 16384），写路由同 c12（Body 65536；Header authorization CORS、content-type CORS + 转发、idempotency-key 只 CORS、origin 转发）；Guard 600/50、120/10、60/5。重新生成后完整门禁结果与下表相同。
- 文件：`block138-client-spec.json` → `block138-client-precheck.sql`、`registration-block138-client.sql`、`block138-client-activate.sql`、`block138-client-deactivate.sql`、`block138-client-rollback.sql`、`registration-block138-client.initial-data.sql`；51 行（route 5、header 13、query 3、guard 30），ID route 441—445、header 581—593、query 651—653、guard 901—930。
- 完整门禁（OPERATIONS + CLIENT 交错，11 步，新只读 dump）：

| 步骤 | 本机（有效路由） | mor-test |
|---|---|---|
| baseline / 两部分休眠 | 475 / 475 | 585 / 585 |
| OPERATIONS 激活 / 再激活 CLIENT | 491 / 496 | 601 / 606 |
| 停用 CLIENT / 停用 OPERATIONS | 491 / 475 | 601 / 585 |
| 回滚 CLIENT / 回滚 OPERATIONS | 475 / 475（载入 543 → 526） | 585 / 585（载入 607 → 590） |

  补充件预检在 OPERATIONS 休眠之后执行：自身区间 0、新 code / 路径 0、Client target 1。日志 `verification/block138-gate-full-{local,mortest}.log`。
- initial-data：补充件的条件版追加在 OPERATIONS 段之后。全新库上 Client target 也由管理 API 创建、不存在，补充件 0 行，错误数仍只有块 135 的 1 个既有错误；mor-test dump 上严格版与条件版行逐列相同。
- `release/sql` 已同步（新增 02、63、64、65、68）；runbook 第 ②③⑪⑭ 步与回滚已更新，P-6 关闭。

## 12. 路由码定稿与语言包脚本（2026-10-27）

- CLIENT 补充件路由码改为 client-fe 的 `client-onboarding-c17`—`c21`（两环境均未占用）；读路由同 c16（16384），写路由同 c12（65536）；重新生成、完整门禁结果不变（本机 475 → 491 → 496 → 475；mor-test 585 → 601 → 606 → 585）；initial-data 重建（HEAD + 351 行）；release/sql 已同步。
- 语言包：`assets/harness/onboarding_bundles.py`，复用 `requirements/20261022/platform-support-多语言管理/release/acceptance/i18n7_common.py`（与发布 #6—#8 的 fu_bundles / rs_bundles 同一链路：Nginx → Gateway → ops-bff → Platform /localization-admin/v1；管理员登录；导入文件直传；只读 SQL）。缺省只做计划（只读 SQL、写本地导入文件），`--apply` 才写。
  - 规则：仓库 en 必须覆盖线上全部 ACTIVE key，否则停止；`--accept-deprecations` 只接受计划列出的那一组废弃；已有且基准未变的 zh-CN 不覆盖；新增 key 必须有 zh-CN；预检缺口 0 才发布。
  - 2026-10-27 只读计划（本 worktree 源，前端仍在开发）：client-console 当前 #1，新命名空间 `onboardingSchema`，新增 553、基准变更 19、废弃 142（全部 `onboarding.*` 旧入驻页）；operations-console 当前 #8，新增 448、无废弃、无新命名空间。导入文件与计划在 `release/i18n-bundles/`。
  - 未验证：`--apply` 路径未在任何环境执行（只能在 mor-test 发布窗口执行）；预览中 deprecated 项结构已对照 `KeyImportPreviewResponseBody`（与 added 同为 KeyImportItem）。
- 决定（主会话 2026-10-27）：接受 client-console 142 个 `onboarding.*` key 废弃；mor-test 窗口内带 `--accept-deprecations` 执行，`--apply` 前紧接着重跑计划；只回滚 client-web 时按运行卡重新导入旧 key 集合。

## 13. c18 白名单增加 applicationId（2026-10-27）

- 依据：client-master `ClientOnboardingCatalogApi.formSchema` 已有可选 `applicationId`（提供时返回该申请冻结的 Schema 与责任 PayFac 材料，忽略其余参数）。`client-onboarding-c18` 白名单改为 `applicationId, jurisdiction, partyType, organizationType`；补充件 52 行（query 4）。
- 重新门禁（同 11 步）：本机 475 → 491 → 496 → 475；mor-test 585 → 601 → 606 → 585；回滚删除补充件 query 4、OPERATIONS query 11。initial-data 重建（HEAD + 352 行），release/sql 已同步。
- 已对齐（2026-10-27 复核代码）：client-console-bff `ClientOnboardingController` 的 `GET /form-schema` 四个参数均可选（`applicationId`、`jurisdiction`、`partyType`、`organizationType`），直接调用 client-master `ClientOnboardingCatalogApi`（本地 `ClientOnboardingGlobalApi` 已删除）；client-fe 报 BFF 测试 92/92 通过（本 agent 未重跑）。Gateway 白名单与 BFF 参数一致。

## 14. operations-console onboardingSchema 命名空间（2026-10-27）

- `onboarding_bundles.py` 按"仓库命名空间 − 线上命名空间"自动建缺少的命名空间，两个应用同一逻辑，无需改代码。
- 重跑只读计划：operations-console 新命名空间 `onboardingSchema`（名称 入驻采集表单，排序 240 = 线上最大 230 + 10，155 个 key），新增 603（448 + 155）、基准变更 0、废弃 0、zh-CN 导入 603、缺 zh-CN 0；client-console 不变（新命名空间 `onboardingSchema` 排序 90、新增 553、基准变更 19、废弃 142、zh-CN 导入 572）。发布后语言包数应为 client 18、operations 48。运行卡 ⑬b 与 P-7 已更新。

## 15. compliance 发布输入与离线试跑（2026-10-27，cmp-dev 答复）

- 文件：`changes/2026-10-27-onboarding-global.sql`（改为保留行，不可重跑）→ `compliance-initial-data.sql`（同会话先 `SET @approved_by`）→ `changes/2026-10-27-onboarding-global-cleanup.sql`（P-8）。顺序：停 compliance → 备份 → 三个文件 → 起新镜像；client-master 须已是新版本。
- 离线试跑（mor-test `mor_compliance` 只读 dump，断网容器，`assets/harness/db_dryrun.sh`）三步全部成功：清理预检 0.4 / 0.5 / 0.6 = 0 / 0 / 0；删除主案件 17（23 个案件、3 个决定、0 个资格），保留 APPROVED 37 与资格 CLIENT 37、OE 1、LP 1；核验段 orphan、legacy_resolvable_programs、bad_file_address、active_slot_count 全 0。（更正见第 18 节：与 client-master 的 PENDING Client 并非一一对应）
- XXL：`clientReviewReturnExpiryScan`，分组 6（compliance-service），`0 */10 * * * ?`、FIRST、阻塞策略按 cmp-dev 改为 `SERIAL_EXECUTION`；只在 `onboarding.review.enabled=true` 时注册（mor-test 已有 `ONBOARDING_REVIEW_ENABLED=true`）。mor-test XXL 只读核对：分组 6 现只有 `compensationEventScan`，运行卡第 ⑧b 步新增。
- SCREENING_SEARCH 合同 cmp-dev 确认兼容（birthYear 字符串；score 字符串或数值均可；FAIL 的 errorCode 不读取）。
- programs 路由（G4）：compliance 已有 `GET /client-onboarding-admin/v1/programs?jurisdictionCode&status`；ops-bff 尚无对应路由，等 ops-fe 给出 BFF 路径后加入块 138。
- 运行卡：第 ⑦ 步按 cmp-dev 顺序重写，新增 3.2 试跑表；P-5、P-12 关闭。

## 16. compliance 定稿 SQL 双环境离线试跑（2026-10-27）

- 文件（cmp-dev 定稿）：`changes/2026-10-27-onboarding-global.sql`（保留行）、`compliance-initial-data.sql`（`SET @approved_by`）、`changes/2026-10-27-onboarding-global-cleanup.sql`；已复制为 `release/sql/30—32`（sync_sql.sh）。
- `@approved_by`：mor-test 用 221546852372267008（mor_platform `t_user_account` 只读核对：realm OPERATIONS、ACTIVE，也是现网标准 API 的 created_by）；本机用 236033080844881920（本机 OPERATIONS、ACTIVE）。
- 结果（详见运行卡 3.2）：两环境预检 0.4 / 0.5 / 0.6 全 0；mor-test 删除主案件 17、案件 23、决定 3、资格 0，保留 APPROVED 37 与资格 CLIENT 37 / OE 1 / LP 1，程序旧 3 个 RETIRED、新 9 个 ACTIVE；本机无案件，删除 0，资格与决定不变，新程序 9 个；核验段两环境全 0；3.7 交叉核对保留的 CLIENT 资格全部属于 ACTIVE Client、无孤儿。
- harness 修正：`db_dryrun.sh` 与 `block_compile_check.sh` 的 MySQL 就绪判断改为等待最终实例日志 "ready for connections … port: 3306"（原先可能连到初始化阶段的临时实例，mor-test 试跑一次因此在导入时断开）。

## 17. programs 路由（G4）重新加入（2026-10-27）

- 依据（均已读代码）：compliance `ClientReviewAdminApi.listPrograms` → `GET /client-onboarding-admin/v1/programs?jurisdictionCode&status`；ops-bff `OnboardingConfigAdminController` → `GET /onboarding-config-admin/v1/programs`，`jurisdiction` 必填（避免超过 compliance 200 个版本上限）。
- 块 138 OPERATIONS 增 1 条：`operations-onboarding-config-programs`，`/api/operations/onboarding-config-admin/v1/programs` → `/onboarding-config-admin/v1/programs`，`ONBOARDING_CONFIG_READ`，白名单 `jurisdiction`；追加在 spec 末尾，既有行 ID 不变（route 418、header 567—568、query 612、guard 803—808）。OPERATIONS 部分 227 行（route 18、header 68、query 12、guard 108）；CLIENT 补充件 52 行不变。
- 重新门禁（新只读 dump，11 步）：本机 475 → 492 → 497 → 492 → 475 → 475；mor-test 585 → 602 → 607 → 602 → 585 → 585（mor-test Platform 镜像仍为 `sha256:4859efd7…`）；OPERATIONS 回滚删除 route 18、header 68、query 12、guard 108。initial-data 重建（HEAD + 362 行），release/sql 已同步；运行卡 ⑪⑭③ 的行数与公式已更新。
- 遗留缺口 G-2 关闭。

## 18. client-master 定稿 SQL 与更正（2026-10-27，cm-sql）

- 文件与顺序（cm-sql）：`20-client-master-ddl`（`schema_id` 可空；`application_type` 临时 DEFAULT 'ONBOARDING' 后 DROP DEFAULT）→ `21-…-cleanup`（P-8，只删 PENDING Client，SIGNAL 预检与事后断言，单事务，可重跑）→ `22-…-country-pack`（AE / SA / EG 增量，重复键不改）→ `23-…-legacy-backfill`（P-11；依赖 ACTIVE 辖区，故在国家包之后）→ `29` 核验。reset 文件已删除。已加入 `sync_sql.sh`，发布副本与源逐字节相同。
- 独立复跑（新只读 dump，含重跑 21—23 后再核验）：mor-test ACTIVE 37 + PENDING 17 → ACTIVE 37，`29` 前 6 项全 0（重跑后仍全 0），legacy_redacted_keys 37、apps_schema_null 37、LEGACY 申请 0；本机 ACTIVE 2 不变，`29` 全 0。运行卡 ⑤、3.1、P-4、P-11 已更新。
- **更正**：我此前在第 15、16 节与运行卡 3.2 写的"compliance 删除的 17 个主案件与 client-master 的 17 个 PENDING Client 一致"不成立。cm-sql 跨库只读比对（报告第 4 节）：17 个主案件属于 12 个 Client——13 个属于 8 个 PENDING Client，4 个是 4 个 ACTIVE Client 被取代的早期 RETURNED 轮次；另 9 个 PENDING Client 只有 DRAFT 申请、无案件。数量相同是巧合；两边清理口径各自自洽。运行卡 3.2 与第 ⑦ 步通过条件已改。

## 19. 语言包命名空间编码修正（2026-10-27）

- 主会话在 mor-test 执行 ⑬b 时于建命名空间处失败：Platform 要求 `namespaceCode` 符合 `[a-z][a-z0-9-]{0,63}`（`CreateNamespaceRequestBody`、`LocalizationCodes.NAMESPACE_CODE`），`onboardingSchema` 被 422 拒绝，未写入。两个 Web 已改用 `onboarding-schema`（key 前缀 `onboarding-schema.*`）。
- 我的计划步骤此前只校验了 key 规则，没有校验命名空间编码——这是本应在计划阶段发现的问题。`onboarding_bundles.py` 现按 Platform 规则预检：命名空间编码、key 规则与命名空间前缀、非空基准、文案 ≤ 8000 字符、说明 ≤ 512 字符、新命名空间名称 ≤ 64 字符与排序 0—9999；`NAMESPACE_NAMES` 增 `onboarding-schema`（入驻采集表单）。负向用例（临时目录：`onboardingSchema.json` + 8001 字符文案）两项都被识别。
- 计划（只读，`--web-root Code-MoR-onboard-rel`，与 web 镜像 onboard-20261027a 同源）：client-console #1 → 新命名空间 `onboarding-schema`（90）、新增 553、基准变更 19、废弃 142（全部 `onboarding.*`）、zh-CN 导入 572、缺 0；operations-console #8 → 新命名空间 `onboarding-schema`（240）、新增 612、基准变更 0、废弃 0、zh-CN 导入 612、缺 0。线上命名空间数（8 / 23）与当前发布（#1 / #8）未变，确认失败的那次未写入。导入文件 SHA-256 前缀见运行卡 ⑬b 记录；`--apply` 未执行。
