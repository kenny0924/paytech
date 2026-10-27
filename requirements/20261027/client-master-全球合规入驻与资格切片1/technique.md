# client-master / compliance：全球合规入驻与资格 · 切片 1 · 技术方案

> 状态：**阶段 2 评审稿（2026-10-27）**。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。完整设计（概念模型、状态机、表级字段、接口与事件、国家包、Vault 清单、硬编码边界）见 [design-discuss/client-onboarding/design.md](../../../design-discuss/client-onboarding/design.md)；本文件只写本切片的差异核对、取舍与实施细节，不复制设计正文。
> 阶段 2 证据：四份只读核对笔记（每条带 path:line）在 [analysis/](./analysis/)：[client-master](./analysis/stage2-client-master.md)、[compliance](./analysis/stage2-compliance.md)、[channel-connector + Vault](./analysis/stage2-connector-vault.md)、[前端 / BFF / Platform 注册 / 多语言](./analysis/stage2-frontends.md)。基线：main `ea0272f3` + 本需求未提交文档；最近部署 mor-test `relsnap-20261025a`（2026-10-25）。未连接 mor-test 数据库。

## 1. 现状差异、能力缺口与逐系统影响（阶段 2）

### 1.1 核对结论速览（F-01—F-18）

| # | 发现 | 证据 | 对需求的影响 |
|---|---|---|---|
| F-01 | 入驻校验规则、目录（组织类型 / 经营模式 / 证件类型 / 行业）全部写死在 `ClientSubmissionValidator` 与 `application.yml onboarding.form-catalog`；**行业目录同时被运营分类 O03 与行业目录接口消费** | client-master 笔记 §1.2、§1.3 | R1 删除 form-catalog 时必须给 industries 新归属（S-03） |
| F-02 | 证件号明文落在两张表、草稿回显、I01 请求、唯一冲突按索引名字符串匹配；人员 Processor（C07/C08）不经 Vault | client-master 笔记 §2、§3 | R3 改动面比需求列的多：C07/C08 加事务外 Protect、回显改掩码、索引名同步（S-12） |
| F-03 | `t_party_profile` 每 Party 一行的假设遍布 9 处 SQL；Checkout `unique()` 遇两行抛主档冲突 | client-master 笔记 §6 | `current_slot` 引入时 9 处 SQL 加条件；commercial 按 profileId 读不受影响 |
| F-04 | **GAP-PROFILE 既有缺陷**：批准只写 `approved_time`，不清 `draft_application_id`、不写 `content_hash`，REAL 解析下所有入驻通过的 Client 不合格 | client-master 笔记 §0.1；CURRENT_IMPLEMENTATION:20 | 本切片改同一处，顺带修复（S-08） |
| F-05 | client-master 直接输出中文用户文案（目录 label、提交问题 message、草稿错误、错误码 detail），Client Web 原样展示 | client-master 笔记 §11 | R2 需求级错误改为 code + 多语言键，旧 message 过渡策略（S-04） |
| F-06 | 撤回不存在；`ClientReviewDecided` 转换器硬校验 schemaVersion==1、outcome 三值，v2 / EXPIRED 会进死信 | client-master 笔记 §0.7、§4 | 发布顺序：client-master 先兼容 v1+v2 再升 compliance（S-02） |
| F-07 | **`QualificationChanged` 事件不存在**；资格手工登记 / 撤销不登记补偿、不发事件 | compliance 笔记 §0.1、§4 | R7 需新补偿事件类型 + Handler + 目录登记 + client-master 消费（S-02） |
| F-08 | **"同案件多轮"与 `uk_dd_case_source`（含 source_submission_id）/ `uk_dd_case_active` 冲突**；现行退回关闭主 / 子案件，重提新建案件 | compliance 笔记 §0.3 | 改为"退回关案 + 新案件 `previous_case_id` 串联 + 复用已接受证据与有效期内检查"（S-01） |
| F-09 | compliance 无任何出站 HTTP / Vault 设施；`ComplianceArchitectureTest` 以精确集合锁死依赖；R7 规则禁止 TransactionTemplate 类注入外部客户端 | compliance 笔记 §0.4、§7 | 新增 http-client-support / loadbalancer / vault-client / connector-api；出站只在 Processor 事务外 |
| F-10 | 案件 9 态只写 5 态；`t_dd_check`、`t_screening_*`、`t_dd_party_snapshot`、`t_dd_program_check/screening` 无 DO/Mapper；`SettlementControlApi` 无实现；O03/O04/O06 不存在于 compliance | compliance 笔记 §0.6、§1、§3.7 | R6 为全新实现而非改造；`t_dd_evidence_ref.file_id BIGINT` + `uk(requirement_id,file_id)` 改 file_address 要动唯一键 |
| F-11 | `ComplianceArchitectureTest` **既有失败 1 项**（metadata-only 前缀断言 3 个，yml 已 4 个，`4b661830` 未同步） | 本会话实跑：12 run / 1 fail，`ComplianceArchitectureTest.java:174` | 既有问题，本切片改 yml 时顺带修正并单独报告（S-16） |
| F-12 | 标准 API、渠道、授权全经运营管理接口创建，仓库无 `INSERT t_standard_api` 先例；回显探针 S5P2_ECHO / 测试样例 LOOKUP 是无凭据 Mock 渠道的模板；**mor-test 无可达合成外部服务**（trusted-networks 空、只允许 https） | 连接器笔记 §1、§3、§5 | R8 不改 channel-connector 代码；mor-test 需新增 Mock 容器 + env 覆盖（S-06） |
| F-13 | channel-connector 入站 / 出站 / 访问日志均不记报文；只有沙箱验证 trace 会存原文 | 连接器笔记 §4 | R8 "日志"行无需改代码；沙箱用例不得用真实证件号 |
| F-14 | Vault Protect 不返回掩码；STABLE 指纹 = HMAC(数据类型 + 精确原值)，不做规范化；访问规则 caller 实际为 `client-master-service` / `compliance-service`；仓库无 `LEGAL_IDENTIFIER_V1`；`VAULT_SERVICE_ARCHITECTURE.md:112` 原文禁止 CCT 法律标识入 Vault | 连接器笔记 §8–§10 | 掩码由 client-master 算；Protect 前先规范化；identity_key 唯一键含类型 + 签发域；修订 Vault 文档；DML ID 段 `9100000000000760` 起 |
| F-15 | Client 门户 `/onboarding` 是 1702 行硬编码组件（无表单库、无 Schema 渲染）；Client 入驻 39 条 Gateway 路由不在任何块内（Platform API 生成 ID） | 前端笔记 §0.1、§0.2、§1 | R10 = 重写渲染层；块 138 含 Client 路由时既有 target / permission 用子查询解析（S-07） |
| F-16 | 全仓无 on-behalf / 代操作机制；Operations 身份只有 Gateway 两个头；Partner 门户（business-portal-*）为空目录 | 前端笔记 §0.3、§4 | `on_behalf_of_partner_id` 以请求体字段设计（S-05） |
| F-17 | 多语言决定：key 与 en 基准来自代码、无跨端共享命名空间、业务内容不进该模块；Schema `label_key` 来自 DML 与此冲突 | 前端笔记 §6 | S-04 定归属 |
| F-18 | 块 138 仓库内无占用（initial-data 末块 137 起于 :8071）；mor-test 风控占 101xxx–104xxx / 111xxx–114xxx 不冲突；live 仍需 `SELECT` 区间为空；并行会话（tax）未声明块号 | 前端笔记 §5；本会话 grep | 候选 138 成立，阶段 6 前核 live |

### 1.2 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为（AC） | 缺口 / 原因 | 处理建议 |
|---|---|---|---|---|
| R1 辖区包与目录 | 无辖区概念；目录在 yml（client-master 笔记 §1.3）；关系类型、地址类型、资格类型为代码闭合枚举（§1.2） | AC-01、AC-11 | 未实现 | 新建 10 张目录 / 规则 / Schema 表 + 通用目录表 `t_catalog_item`（S-03）；枚举改目录读取 |
| R2 Schema 驱动校验与需求级错误 | 校验写死（§1.2）；问题只有 `field + 中文 message`，提交 422 不带 errors[]，逐项要调 `submission-check` | AC-02 | 未实现；文案模型冲突（F-05） | 校验引擎（Schema + 目录 + 规则开关解释器 + 校验位注册表）；`submissions` 与 `submission-check` 同时改为 `errors[{requirementKey, code, reason, remediation}]`，`message` 保留一版过渡（S-04） |
| R3 证件 Vault 化 | 明文（F-02）；无 `LEGAL_IDENTIFIER_V1`；测试无 Vault Mock | AC-03 | 未实现 | 规范化 → Protect(STABLE) → Token + 末 4 位；identity_key 唯一键 (type, scope, token)；I01 只传 Token；人员 C07/C08 同样处理；Vault DML + 文档修订；测试新增 Vault Mock（S-12） |
| R4 入驻码、责任方、撤回 | 无（F-06）；`StartApplicationRequestBody` 只有 clientType | AC-04 | 未实现 | 新表 + 运营代签发接口；创建申请解析；撤回 Processor（锁 application）+ 经补偿事件通知 compliance 新接口（S-14） |
| R5 程序辖区化与 programs/resolve | 程序按资格类型一对一全局映射（`programs-json`），两处消费；要求表无主体角色 / 时效；程序模板无检查 / 筛查行 | AC-01、AC-05、AC-12 | 未实现 | 程序表加维度；解析接口替换 `MaterialRequirementsApi`（client-master、client-bff 两个消费方）；AE/SA/EG/ZZ 程序 DML；`programs-json` 删除 |
| R6 案件全状态机、多轮、检查、筛查、CRR、四眼 | 5 态；门禁 5 项；无检查 / 筛查 / CRR / 四眼 / 豁免；退回单分区；证据核验无原因码、不记核验人（compliance 笔记 §3） | AC-05—AC-07、AC-13 | 大部分未实现；多轮与唯一键冲突（F-08） | 按 S-01 改"新案件串联 + 复用"；新增 Processor 一组；程序 Owner 本期只建列不解析定制 |
| R7 决定冻结、资格事件、联动 | 决定不冻结 tier/CRR/辖区/责任方/推荐人；资格无事件；client-master 无 QualificationChanged 消费、无 SUSPENDED 写路径（`t_client.suspension_time` 列已有） | AC-08、AC-09 | 未实现 | 新补偿事件 + Handler；client-master 新 Handler（SUSPENDED + 生命周期历史 + Platform 投影复用 `CLIENT_TENANT_PROJECTION`）；发布顺序 S-02 |
| R8 筛查 Mock 渠道 | 标准 API 目录无 KYC 类；无 Mock 渠道；mor-test 无合成服务 | AC-13 | 配置 + 部署缺口 | 管理接口登记（harness 脚本记录 payload）；本机回显 Mock；mor-test Mock 容器（S-06） |
| R9 运营控制台 | 审核页只有材料 / 分类 / 结论；资格页已有；无 Client 列表 / 详情；无入驻配置页（前端笔记 §3） | AC-06、AC-10、AC-15 | 未实现 | 现有 `/client-onboarding/reviews` 升级为工作台；新页 3 个；块 138 |
| R10 Client 门户 | 硬编码单组件（F-15）；审核结果只有分区 + 说明；无撤回 / 入驻码 / 声明 | AC-02、AC-07、AC-14 | 重写渲染层 | 通用渲染器 + 控件注册表；需求清单面板；新路由 5 条（S-07） |
| R11 文档 | PDManer 手改（生成器已弃，README 过时）；`client-master-initial-data.sql` 无种子；Schema 头"generated"字样；CURRENT_IMPLEMENTATION:1166 过时 | — | 文档漂移 | 随阶段 4 同步 |

### 1.3 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| client-master | 身份、资料、申请、采集规则 | 新表 12 + 改表 11（design 10.1）；校验引擎；Vault 证件；入驻码；撤回；两类事件消费；运营目录 / Client 接口；国家包 DML | 代码 ✔ 模型 ✔ 配置 ✔（删 form-catalog，加 Vault 用途） | 先于 compliance 发布（v1+v2 兼容）；Checkout 候选 9 处 SQL；commercial 的 party-profiles 兼容；GAP-PROFILE 修复 |
| compliance | 程序、案件、核验、决定、资格 | 改表 14 + 新表 3；programs/resolve；案件新 Processor 组；出站设施；两事件；I01 v2 | 代码 ✔ 模型 ✔ 配置 ✔（删 programs-json，加 connector / vault 客户端）；架构测试依赖集合同步 | 后于 client-master 发布；events.yaml 登记；table-ownership 登记；既有失败 F-11 |
| channel-connector | 外部出口 | 无代码；标准 API 7 个定义 + Mock 渠道 + 授权（运营接口）；mor-test env 覆盖 | 配置 ✔（数据 + 环境） | Mock 容器部署；授权须在实现之后创建 |
| client-console-bff | Client 门户聚合 | 新转发 5 条；form-schema 聚合（client-master Schema + compliance 程序）；创建申请带入驻码；errors[] 透传 | 代码 ✔ | 依赖 client-master 新 API jar；`MaterialRequirementsApi` 消费替换 |
| client-console-web | Client 门户 | 渲染器 + 控件注册表重写；需求清单面板；声明；撤回；入驻码；枚举与 i18n 命名空间 | 代码 ✔ | 新路由注册；多语言发布 |
| operations-console-bff | 运营聚合 | 工作台全部新命令；目录 / Schema / 程序查看与发布；Client 列表 / 详情；入驻码；`on_behalf_of_partner_id` 校验 | 代码 ✔ | 严格 Query 白名单同步 Gateway query_param |
| operations-console-web | 运营控制台 | 工作台升级；新页 3；pageCapabilities；i18n 命名空间 | 代码 ✔ | 禁中文守卫；每主体并发 2 |
| platform-support | 注册、多语言 | 块 138（含 OPERATIONS 与 CLIENT 通道路由）；两个应用各建命名空间；`console-resource-capabilities.json` 同步（既有缺 14 个，S-11） | 配置 ✔（DML） | DISABLED 路由也编译 → 无网副本预编译 |
| Vault | 数据类型与规则 | `LEGAL_IDENTIFIER_V1`、`RISK_SCREENING_SEARCH_TERM_V1`；规则 client-master PROTECT/REVEAL、compliance REVEAL/PROTECT | 配置 ✔（DML） | 先于 client-master 发布；文档 :112 修订 |
| foundation（messaging catalog、arch-rules） | 事件目录、表归属 | `events.yaml` 加 QualificationChanged、ClientReviewDecided v2；`table-ownership.yaml` 加 15 张新表 | 配置 ✔ | 架构测试 |
| test-environment / local-environment | 环境 | Mock 筛查容器（固定私网 IP）、channel-connector env；本机 e2e 回显 Mock | 配置 ✔ | 发布清单登记 |
| commercial / acquiring / billing / risk-decision | — | 无改动（party-profiles 读当前行，字段增加兼容） | — | — |

### 1.4 阶段 2 待决（S-01—S-20，建议已给）

| # | 问题 | 建议 |
|---|---|---|
| S-01 | 重提"同案件多轮"与唯一键冲突 | **改为"退回关案 + 重提新案 + `previous_case_id` 串联"**：新案件复制上一案件 ACCEPTED 的要求 / 证据（标 `reused_from_case_id`）与 `valid_until` 内 PASS 的检查；`round_no` 记在申请维度（`t_client_application.submission_round`）；SM-D 的 RETURNED → IN_REVIEW 改为 RETURNED → (新案件 SUBMITTED)；设计 7.3 / 6.1 回写 |
| S-02 | 两事件的发布顺序 | client-master 转换器先接受 schemaVersion ∈ {1,2} 与 outcome 增 EXPIRED（显式分支 → WITHDRAWN，默认分支改显式 REJECTED）并部署；再部署 compliance 发 v2；`QualificationChanged` 作为新补偿事件类型 `QUALIFICATION_CHANGED`（目录 + Handler + 资格保存事务内登记），client-master 新 `@DomainEventHandler` 走同一 compliance 分区队列 |
| S-03 | 行业 / 经营模式 / 登记机关 / 自贸区 / 经济部门等 SELECT 取值 | 新通用目录表 **`t_catalog_item`**（`catalog_code, item_code, jurisdiction_code?, label_key, client_types?, display_order, status`），Schema 字段与属性定义的 `catalog_code` 指向它；行业与 MCC 目录迁入（O03 / 行业目录接口改读表）；组织类型 / 标识类型 / 关系类型因有定义列仍单独建表 |
| S-04 | Schema `label_key` 与多语言模型冲突；中文 message 过渡 | Schema 与目录的 label / help 视为 **UI 文案**：key 命名空间 `onboardingSchema`，en 基准随代码提交到 client-web 与 ops-web 各自 `src/i18n/en/onboardingSchema.json` + zh-CN 种子，加"DML key ⊆ en 基准"一致性测试；运营发布新 Schema 版本若新增 key 须随代码发布（本期 Schema 只由 DML 维护，可接受）。client-master 错误与目录只返回 code（+ 参数），`message` 字段保留一版、值改为英文 code 说明，Web 不再展示 message |
| S-05 | 代操作身份 | 运营工作台写命令请求体加可选 `onBehalfOfPartnerId`（ops-bff 校验该 Partner 存在且角色 OE_PROVIDER）；compliance 案件 / 决定 / 状态变更记 `operated_by=PLATFORM` + `on_behalf_of_partner_id`，决定人仍为运营账号；不新增 Gateway 头 |
| S-06 | Mock 筛查在 mor-test | 部署轻量回显式 Mock 容器（Python，参照 stage5p2_echo_server；固定私网 IP）+ channel-connector 覆盖 `ALLOWEDSCHEMES=https,http`、`TRUSTEDNETWORKS=<ip>/32`；发布清单登记"只放行该 IP"；本机 e2e 同形 |
| S-07 | Client 通道新路由注册 | 块 138 同时含 OPERATIONS 与 CLIENT 通道路由；CLIENT 路由沿用 registration-plan 形状（PUBLIC/CLIENT 安全配置、超时、Body 限制），既有 target / permission（`CLIENT_ONBOARDING_*`）用 `(SELECT … WHERE code=…)` 解析，自身 ID 用 138 段；创建申请加入驻码不需新路由 |
| S-08 | GAP-PROFILE | 纳入：批准时同事务清 `draft_application_id`、写 `content_hash`、置 `current_slot=1`；存量不回填（重写） |
| S-09 | 国家包 DML 位置 | AE / SA / EG 写入 `client-master-initial-data.sql` 与 `compliance` 初始化 DML（遵守"一服务一初始化 DML"），需求目录 `db/` 只放引用索引与增量；**ZZ 只进测试夹具**（Mocker 插入），不进任何初始化文件；未经法务确认的行 `status=DRAFT` |
| S-10 | 测试目录夹具 | 目录 / 规则 / Schema / 程序表纳入清理清单，由 `JurisdictionPackMocker`（client-master）/ `ProgramPackMocker`（compliance）按测试需要插入；AE/SA/EG 夹具与初始化 DML 同源（测试断言两者一致） |
| S-11 | `console-resource-capabilities.json` 既有缺 14 个组件 | 本次补齐（含块 137 三个），单独列为同范围修复 |
| S-12 | 人员证件 Vault 与回显 | C07/C08 改"只读准备 → 事务外 Protect → 短事务"；Client / 运营读出只给掩码；Client 修改证件需重填（不 Reveal 回显）；`SubmitClientApplicationProcessor:90` 索引名改 `uk_party_identity_token` |
| S-13 | I01 v2 合同 | 删除 `identifierValue`、姓名 / 地址等 PII 字段（compliance 本就不存），增 `identifierVaultToken`、`jurisdictionCode`、`organizationTypeCode`、`attributes[]`、`declarations[]`、`responsiblePartnerId`、`previousCaseId`；`t_dd_party_snapshot` 本期继续不写 |
| S-14 | 撤回同步 compliance | 新接口 I03 `POST /client-onboarding-review/v1/cases/{caseId}/withdrawal`（幂等）；client-master 撤回事务内登记补偿事件 `CLIENT_REVIEW_WITHDRAWAL`，事务外调用 |
| S-15 | DTO 风格与包 | 新接口放 `com.paytech.client.master.api`（class + 校验注解）；旧包入驻接口本期改动（draft / submissions / applications）就地修改，不迁包 |
| S-16 | 既有失败 F-11 | 本切片修正 `ComplianceArchitectureTest:174` 断言（前缀集合改为按 yml 读取 + 至少包含 4 个），阶段 5 单独报告 |
| S-17 | 硬编码守门实现 | 不改 arch-rules；两服务各加专用 auto 测试：扫描 core 源码禁止出现目录中登记的辖区码 / 组织类型码 / 标识类型码字面量（白名单：枚举种子、测试）；**ZZ 国家包全链测试**为主门禁（AC-11） |
| S-18 | 名称列长度 | `legal_name / trade_name / given / middle / family / local_*` 统一 VARCHAR(128)（全球法定名称 64 偏短） |
| S-19 | 关系类型目录化后的唯一键 | `uk_party_relation_identity(organization_party_id, related_party_id, relation_type)` 保留；`t_relation_type` 种子五值 + ZZ 测试自定义一值 |
| S-20 | 切片内交付批次 | 阶段 4 按两个批次推进但同一阶段 3 设计：**1A** 两服务后端 + Vault / 目录 / 程序 DML + ZZ 全链测试 + 最小运营接口；**1B** Client 门户渲染器 + 运营工作台 / 配置页 / Client 详情 + 块 138 + 多语言。阶段 5 一次验收 |

### 1.5 既有问题（非本需求，单独报告）

- F-11 `ComplianceArchitectureTest` 1 项失败（`4b661830` 引入）。
- GAP-PROFILE（CURRENT_IMPLEMENTATION:20，本切片顺带修复，S-08）。
- Snowflake worker 26 被 client-master 与 channel-connector 共用（CURRENT_IMPLEMENTATION:20）。
- `console-resource-capabilities.json` 缺 14 个组件（S-11）。
- client-master README 表数 / 生成器说明、测试 README、CURRENT_IMPLEMENTATION:1166 过时条目。
- registration-plan 登记的 `client-onboarding-c15` / `o06` 两条路由无对应 Controller。

### 1.6 依赖、发布顺序与风险

- **发布顺序**（阶段 6 细化）：Vault DML → client-master（兼容 v1+v2 事件、新表、目录 DML）→ compliance（程序 DML、v2 事件）→ channel-connector 配置（标准 API、Mock 渠道、授权；mor-test Mock 容器）→ Platform 块 138 休眠注册 + 命名空间与语言包 → client-bff / ops-bff → client-web / ops-web → 激活块 138 → Gateway 快照重发。
- **风险**：① 范围大（两服务后端重构 + 两端前端重写），S-20 分批；② 法务 26 项未回前国家包只能 DRAFT；③ 多语言 key 来源例外（S-04）需在决定中登记；④ `t_dd_evidence_ref` 唯一键与 I01 `fileId` → `fileAddress` 的迁移要同时改 client-master 发送端；⑤ Gateway 路由 DISABLED 也编译，块 138 须无网预编译；⑥ 运营每主体并发 2，工作台页面不可扇出请求。
- **工作量依据**：client-master 241 个 core 文件中入驻相关约 1/2 受影响；compliance 需新增约 10 个 Processor + 6 张表读写；client-web 1702 行组件重写；ops-web 新页 3 + 工作台升级；DML 四国包各约 150–300 行。

## 2. 接口、数据及页面设计（阶段 3，2026-10-27）

> 用户 2026-10-27 授权连续执行阶段 3—7（见 delivery.md）。本节是研发合同；字段级表结构以 design.md 第 10 节为准，并叠加下列修订：**不做阿语**（删除 `local_*`、`name_script`、NAME 双文字控件）、规则开关走 `t_rule_setting`（design 10.1）、地址格式组件 `t_address_format_component`、通用目录 `t_catalog_item`（S-03）、关系类型目录 `t_relation_type`、重提为新案件串联（S-01）、`t_materiality_rule` 与修订申请不在本切片。
>
> **规模**：改代码系统 7 个（client-master、compliance、client-console-bff、client-console-web、operations-console-bff、operations-console-web、platform-support 注册 DML）+ 配置系统 3 个（channel-connector 数据、Vault DML、test-environment）；新增 / 调整 Owner 接口约 30 个（client-master 13、compliance 14、I01/I03/事件 3），BFF 转发约 26 个；新表 client-master 12 张、compliance 4 张；改表 client-master 11 张、compliance 14 张；初始化 DML 三国国家包 + 程序（约 600 行）；Platform 块 138；Pencil 页面：Client 门户全量重画（认证 6 页 + 入驻 13 页 + 移动端 4 页）、运营工作台 5 页。

### 2.1 client-master 接口（包 `com.paytech.client.master.api.onboarding`，class + 校验注解；旧包入驻接口就地修改）

| # | Method Path | 调用方 | 说明 |
|---|---|---|---|
| CM-1 | GET `/client-onboarding/v1/jurisdictions` | client-bff | `[{jurisdictionCode, countryCode, labelKey, acceptancePolicy}]`，只返回 ACTIVE |
| CM-2 | GET `/client-onboarding/v1/form-schema?jurisdiction=&partyType=&organizationType=` | client-bff | 完整渲染规格：`schemaId, versionNo, sections[{section, labelKey, groups[{group, labelKey, fields[{fieldKey, widget, catalogCode, requirementLevel, requirementGroup, appliesTo, validation{pattern, minLength, maxLength, checksumRule}, visibleWhen, labelKey, helpKey, order}]}]}], catalogs{code:[{itemCode, labelKey}]}, organizationTypes[], identifierTypes[{code, partyType, labelKey, pattern, checksumRule, issuerScopeMode, expiryRequired}], addressFormat{code, components[{component, required, labelKey, order}]}, relationRules{uboThresholdPct, uboFallbackRule, controlPersonRequired, minSignatories, requiresManager, requiresDirectors, uboCollectionMode}, materials[{requirementCode, subjectRole, documentTypes[], required, maxAgeDays}]（client-master 事务外调 CP-1 合并）, declarations[{declarationType, labelKey}]`；`ACCEPTANCE_POLICY=CLOSED` → 422 `JURISDICTION_NOT_ACCEPTED`；取代 `form-options`（删除） |
| CM-3 | POST `/client-onboarding/v1/applications` | client-bff | 体 `{clientType, jurisdictionCode, organizationTypeCode?, onboardingCode?}`；入驻码无效 422 `ONBOARDING_CODE_INVALID` |
| CM-4 | PATCH `/client-onboarding/v1/applications/{id}/draft` | client-bff | values 新增分区 `attributes{attributeCode: value}`；address 加 `addressFormatCode, buildingNumber, additionalNumber, district, poBox`；individual 加 `birthPlace, birthCountry, gender, sourceOfFundsCode, pepSelfDeclared`；organization 加 `registrationAuthorityCode, licenseExpiryDate, listedCompany`；identifier 原值写入即规范化 + Vault Protect，读出只给 `identifierMasked` |
| CM-5 | POST/PATCH people（C07/C08） | client-bff | 同 CM-4 的人员证件 Vault 化；relation 加 `uboBasis, jobTitle, nomineeFlag, controlNature` |
| CM-6 | POST `/client-onboarding/v1/applications/{id}/declarations` | client-bff | `{declarationType, declaredValue, termsVersion?}`；记录 account、时间、IP（`X-Gateway-Client-Ip` 如有）、UA |
| CM-7 | GET `/client-onboarding/v1/applications/{id}/requirements` | client-bff | 实时计算 `items[{requirementKind, requirementKey, subjectPartyId, status, awaitingActionFrom, errorCode, remediationCode, dueTime}]` |
| CM-8 | POST `/client-onboarding/v1/applications/{id}/withdrawal` | client-bff | DRAFT / RETURNED → WITHDRAWN；已立案登记补偿 `CLIENT_REVIEW_WITHDRAWAL` 调 CP-3 |
| CM-9 | POST submissions / GET submission-check（改） | client-bff | 422 体与核对结果统一为 `errors[{requirementKey, subjectPartyId, code, remediation, message}]`（message 为英文过渡字段，Web 不展示） |
| CM-10 | GET `/client-master-admin/v1/jurisdictions`、`/onboarding-schemas?jurisdiction=`、`/catalog-items?catalogCode=` | ops-bff | 只读配置查看 |
| CM-11 | GET `/client-master-admin/v1/clients?page&size&status&jurisdiction&responsiblePartnerId`、`/clients/{clientId}` | ops-bff | Client 列表 / 详情（主档、人员、申请历史，证件只给掩码） |
| CM-12 | GET/POST `/client-master-admin/v1/local-partners/{id}/onboarding-codes`、POST `…/{codeId}/revocation` | ops-bff | 入驻码签发（随机 `XXXX-XXXX`）与作废；仅 OE_PROVIDER 角色 Partner |
| CM-13 | GET `/client-master/v1/party-profiles?ids=`（改） | commercial、**compliance** | 调用方加 `compliance-service`；返回中补人员 `givenName, familyName, birthDate, nationalityCountry`（供筛查，不含证件） |
| 事件 | 消费 `ClientReviewDecided` v1 / v2（outcome 增 EXPIRED → 申请 WITHDRAWN；默认分支显式 REJECTED）；消费 `QualificationChanged`（REVOKED → Client SUSPENDED + 生命周期历史 + `CLIENT_TENANT_PROJECTION`） | — | 发布顺序：client-master 先上线 |

### 2.2 compliance 接口

| # | Method Path | 调用方 | 说明 |
|---|---|---|---|
| CP-1 | GET `/compliance/v1/programs/resolve?jurisdiction=&partyType=&roleType=&tier=STANDARD&responsiblePartnerId=` | client-master | `{programId, programVersionId, reviewIntervalMonths, fourEyesRequired, materials[{requirementCode, subjectRole, documentTypes[], required, maxAgeDays, waivable}], checks[{checkCode, checkMode, subjectRole, blocking}], screenings[{screeningType, subjectRole, monitoringRequired}]}`；解析：Owner 程序 > 平台；辖区精确 > NULL；无程序 503；取代 `MaterialRequirementsApi`（删除）与 `programs-json` 配置 |
| CP-2 | POST `/client-onboarding-review/v1/submissions`（I01 v2） | client-master | 删 `identifierValue`、地址行；增 `identifierVaultToken, identifierMasked, jurisdictionCode, organizationTypeCode, attributes[], declarations[], responsiblePartnerId, previousCaseId`；人员保留姓名 / 生日 / 国籍（筛查用，不落库）；documents 用 `fileAddress` |
| CP-3 | POST `/client-onboarding-review/v1/cases/{caseId}/withdrawal` | client-master | 幂等；进行中案件 → WITHDRAWN（子案件同） |
| CP-4 | GET `…/applications/{applicationId}/result`（改） | client-bff | 增 `errors[{requirementKey, subjectPartyId, code, reason, remediation, dueTime}]`、`dueTime`、`caseStatus` |
| CP-5 | 运营 `ClientReviewAdminApi` 增 | ops-bff | `POST cases/{id}/checks/{checkId}/result {outcome, reasonCode, note}`；`POST cases/{id}/screenings/run`（重跑）；`POST cases/{id}/screening-hits/{hitId}/resolution {resolution, reasonCode, note}`；`PUT cases/{id}/risk-rating {riskRating, factors[{factorCode, factorValue}], note}`；`POST cases/{id}/recommendation {recommendation: APPROVE/REJECT, note}`；`POST cases/{id}/requirements/{requirementId}/waiver {reasonCode, note}`；证据核验加 `rejectReasonCode`；退回改为 `{errors[{requirementKey, subjectPartyId, code, reason, remediation}], dueDays, publicNote}`（保留 section 兼容一版）；所有写命令可带 `onBehalfOfPartnerId`；案件列表加 `responsiblePartnerId` 过滤；详情增 checks、screenings（runs + hits + resolutions）、riskFactors、tier、riskRating、recommendation、timeline |
| CP-6 | 门禁（批准） | — | 必需要求 ACCEPTED/WAIVED；阻断检查 PASS；无 OPEN 命中；子案件 APPROVED；分类已确认；CRR 已评；`four_eyes_required` 时 decided_by ≠ recommended_by |
| 事件 | 发布 `ClientReviewDecided` v2（+ `errors[]`、`tier`、`riskRating`、`dueTime`；outcome 增 EXPIRED）；新补偿事件 `QUALIFICATION_CHANGED` → 发布 `QualificationChanged` v1（`roleType, roleId, qualificationType, status, decisionId, validFrom, validTo, tier, riskRating, responsiblePartnerId`）；手工登记 / 撤销 / 案件决定同事务登记 | — | `events.yaml` 登记 |
| 出站 | channel-connector `POST /channel-connector/v1/service-executions`（`SCREENING_SEARCH`，渠道码配置 `compliance.screening.channel-code`，默认 `S_SCREENING_MOCK`）；client-master CM-13 读筛查姓名 | — | 补偿事件 `CASE_SCREENING`（立案后事务外执行，结果短事务落 run / hit）；compliance 本切片不调 Vault |
| 退回超期 | XXL / 补偿扫描：RETURNED 案件超过 `review_due_time` → EXPIRED + 事件 | — | 本地与 mor-test 用补偿扫描执行器 |

### 2.3 数据（DDL / DML）

- **client-master DDL**（`changes/2026-10-27-onboarding-global.sql` + schema / PDManer / 测试 DDL 同步）：新表 `t_jurisdiction, t_rule_setting, t_organization_type, t_identifier_type, t_relation_type, t_address_format_component, t_catalog_item, t_attribute_definition, t_party_attribute, t_onboarding_schema, t_onboarding_schema_field, t_application_declaration, t_partner_onboarding_code`（13 张）；改表按 design 10.1（去 `local_*` / `name_script`）+ S-18 名称列 128 + GAP-PROFILE 修复。
- **compliance DDL**（`changes/2026-10-27-onboarding-global.sql`）：改表按 design 10.2（`t_due_diligence_case` 增 `previous_case_id, case_type, jurisdiction_code, tier, risk_rating, risk_score, responsible_partner_id, operated_by, on_behalf_of_partner_id, recommended_by, recommendation, recommended_time` 等）；新表 `t_dd_program_requirement_doc_type, t_screening_hit_list, t_dd_risk_factor, t_dd_return_error`（退回需求级错误，按案件存储）。
- **初始化 DML**：AE / SA / EG 国家包（辖区、规则开关、组织类型、标识类型、关系类型、地址格式组件、目录项、属性定义、Schema v1：每国 ORGANIZATION 默认 + INDIVIDUAL 默认）写入 `client-master-initial-data.sql`；compliance 程序（每国 × ORGANIZATION / INDIVIDUAL / RELATED_PERSON × STANDARD，材料 / 人工检查 / 筛查模板）写入 compliance 初始化 DML。生产前标 DRAFT 待法务；**mor-test 与本地为验收以 ACTIVE 执行**（DML 头注明）。
- **Vault DML**：`LEGAL_IDENTIFIER_V1`（STABLE、STANDARD、TEXT）+ 规则 `client-master-service` PROTECT / REVEAL × `CLIENT_ONBOARDING_IDENTIFIER`；ID 段 `9100000000000760` 起。
- **channel-connector 配置**：标准 API `SCREENING_SEARCH`（经管理接口，harness 脚本）；渠道 `S_SCREENING_MOCK`（SERVICE、无账号）+ 原子 API + 实现 + 授权 `compliance-service`；Mock 服务（Python，返回无命中，姓名含 `TESTHIT` 时返回 PEP 命中）。
- **Platform 块 138**：OPERATIONS 路由（CM-10/11/12、CP-5 新命令）+ 菜单（入驻配置、Client、入驻码）+ 按钮权限；CLIENT 路由（CM-1/2/6/7/8 与 CP-4 透传）；休眠 / 激活 / 回滚脚本；无网副本预编译 Gateway 快照。

### 2.4 页面

- **Client 门户（`client-console-web/client-console.pen` 右侧 v2 全量）**：认证 6 页（登录、注册、查收邮件、验证结果、找回密码、重置密码）按 N0 规范重画；入驻：入驻码与国家 / 主体选择、准备清单、首页任务清单、公司 / 个人信息、地址与联系、人员 + 抽屉、材料上传（状态机）、声明、核对并提交、审核中、退回首页、补件视图、批准 / 拒绝 / 逾期 / 已撤回结果；移动端 4 页。Web 实现：通用 Schema 渲染器 + 控件注册表，按 N0 token 重写样式。
- **运营控制台**：案件工作台（材料 / 人工检查 / 筛查命中 / 分类 / CRR / 推荐与决定 / 退回需求级错误 / 时间线 / 代 PayFac 操作）、入驻配置只读（辖区、Schema、程序）、Client 列表 / 详情、入驻码。沿用 operations-console 现有组件体系。

## 3. 研发、验证及兼容策略

- **分工**：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-onboard`（基于 `ea0272f3`）；Opus agent 按系统分工：client-master、compliance、client（bff + web）、operations（bff + web）、infra（DML / Vault / connector / 块 138 / Mock / 发布材料）；主会话负责合同、Pencil 原型、门禁与 mor-test 执行。
- **测试**：两服务服务级 auto 测试（含 ZZ 国家包全链 AC-11、硬编码字面量守卫 S-17）；BFF HTTP 测试；两端 Web 单测 + 构建；本地 e2e（真实服务 + Mock 筛查）；mor-test 验收脚本覆盖 AC-01—AC-15。只跑受影响模块，不跑全 reactor（共享本地库）。
- **兼容与发布顺序**：Vault DML → client-master（兼容 v1/v2 事件）→ compliance → channel-connector 配置 + Mock 容器 → 块 138（休眠）→ BFF → Web → 激活块 138 → Gateway 快照。存量按重写：本地与 mor-test 的 `mor_client_master` / `mor_compliance` 入驻数据清空重建（先备份）。
