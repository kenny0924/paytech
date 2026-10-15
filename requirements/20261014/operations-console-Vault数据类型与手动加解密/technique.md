# 技术方案：Operations 维护 Vault 数据类型与手动加解密

- 状态：阶段 2、3 已确认（2026-10-14）；方案、接口与数据设计见第 5 节起
- 需求：[requirement.md](./requirement.md)（阶段 1 已确认，D1～D6）

## 1. 现状与差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| 数据类型与规则存储（D1/D2） | 启动时从 `VAULT_POLICY_FILE` 读一次（`vault-service/internal/bootstrap/bootstrap.go:43`），由 `internal/pkg/policy` 编译成内存 Allowlist；V1 设计明确“Allowlist 是部署配置，不是数据库表” | 策略存于 Vault 库，版本化，热加载 | 无表、无管理接口、无热加载；与 V1 设计冲突 | Vault 新增策略表与版本表；`biz.Authorizer` 改由“最新已发布版本”的内存快照实现，定时刷新；需新决定记录取代 V1 该条 |
| 各环境现有策略 | local `policy.local.json`：2 类型 6 规则；test `test-environment/vault/policy.json`：4 类型 11 规则；`CHANNEL_ACCOUNT_CONFIG_V1` 仅在卡直付联调的独立策略中，正式 local/test 策略都没有 | 迁入数据库后原规则逐条保留 | 无数据 | （阶段 3 用户确认）不做程序导入：各环境执行初始化 DML，由原文件逐条转写；Vault 只读数据库，不再读取文件（AC-9） |
| 数据类型属性（D6） | 只有编码与 Token 模式 | 增加说明、数据级别（普通敏感/密钥）、允许人工加密、允许人工解密、启用状态 | 属性缺失 | 进入新表；编码与 Token 模式创建后不可改，只停用不删除 |
| 策略变更审计（D3） | 无 | 每次变更记录操作人、时间、前后差异、理由，可按版本追溯 | 无记录；Platform 审计只能由 Platform 进程内部写入，没有对外写入接口 | 审计落在 Vault 自己的库（与 Risk 自建发布审计同理）；Operations 通过 BFF 查询 |
| 手动加密/解密接口 | 只有服务间 `POST /vault/v1/records/protect\|reveal`（`api/openapi/v1.yaml:7,37`），调用方由 `X-Caller-Service` 标识，无操作人 | 运营经 BFF 操作，带操作人、理由码、工单号 | 无管理入口、无操作人语义 | 新增管理接口（`/vault/v1/admin/...`），只接受固定的管理调用方 `operations-console-bff`；复用现有 Protect/Reveal 用例逻辑 |
| 两级功能权限（D4/D5） | Platform 按路由授权：`t_gateway_route.permission_id`，BFF 每个请求先调 PLT-I02（`OperationsRequestInterceptor.java:42-44`） | 普通敏感与密钥类分开授权 | 解密时只有 Token，数据级别要等 Vault 查到记录才知道，Platform 无法提前判断 | 普通、密钥类各一条路由、各一个权限点；BFF 把“允许的数据级别”传给 Vault，由 Vault 在查到记录后校验级别是否匹配，不匹配即拒绝且不返回原值 |
| 人工操作审计（AC-7） | `t_vault_access_event` 只有 `caller_service`，无操作人/理由/工单；`action` 有 CHECK 只允许 PROTECT/REVEAL | 记录操作人、理由码、工单号 | 缺字段 | 新增可空列（操作人类型与 ID、理由码、工单号）；动作仍为 PROTECT/REVEAL，不改既有 CHECK；按项目规范不新增 CHECK/外键 |
| Java 客户端 | `paytech-vault-client` 只有 `protect`/`reveal` | BFF 调管理接口 | 无管理方法 | 在客户端模块新增独立的管理接口与实现，业务服务继续只依赖 `VaultClient` |
| Operations BFF | 不依赖 Vault 客户端，无 Vault 调用（BFF 检索结果） | 新增 Vault 管理与手动加解密接口 | 全部新增 | 参照 API 凭据管理链路（Controller → Client 端口 → 实现，`ApiCredentialManagementController.java:13-53`）；经 Nacos 发现 `vault-service` |
| Operations Web | 无 Vault 页面；无工单号字段；无“遮罩 + 限时隐藏”组件（已有 `Input.Password`、`Typography.Text copyable`） | 数据类型、规则、版本与变更记录、手动加密、手动解密页面 | 全部新增 | 在 `navigation/pageCapabilities.ts`、`menu.ts` 登记；参照 `pages/ApiCredentials.tsx`（理由弹窗）、风控流程版本页；新建敏感值展示组件 |
| 菜单、权限、路由注册 | 权限、菜单、路由均为 Platform 数据（`t_permission`、`t_console_resource`、`t_gateway_route` 等） | 新增 Vault 菜单、按钮、路由 | 无数据 | 按“外部 API 配置”的注册 DML 模式（`requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/registration-dml.sql`）新增；新行默认 DISABLED/REVOKED，激活单独执行 |

## 2. 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 代码 / 模型 / 配置 | 上下游依赖与兼容风险 |
|---|---|---|---|---|
| Vault Server（Go） | 策略、加解密、访问审计的 Owner | 策略表与热加载、管理接口、手动加解密、审计扩列、读取初始化数据 | 改代码；新增约 3～4 张表、`t_vault_access_event` 加列；PDManer 模型与 Schema SQL 同步；OpenAPI 新增管理接口 | 现有服务的 Protect/Reveal 合同不变（AC-9）；初始化 DML 必须与各环境原文件逐条一致；多实例刷新存在秒级窗口 |
| `paytech-vault-client`（Java） | 内部调用合同 | 新增管理客户端 | 改代码 | 现有 `VaultClient` 不变，CCT/Platform/Payment 无需改动 |
| Operations Console BFF | 页面聚合、操作员身份与 PLT-I02 授权 | 新增 Vault 相关 Controller 与 Client；接入 Nacos 发现 `vault-service` | 改代码与配置 | 新依赖 Vault；Vault 不可用时页面报错，不影响其他功能 |
| Operations Console Web | 页面 | 新菜单与 5 类页面；敏感值展示组件；工单号输入 | 改代码 | 无 |
| Platform Support | 权限、菜单、路由、PLT-I02 | 新增权限点（约 6 个）、菜单与按钮、Gateway 路由、请求头与限流 | 只有注册 DML，无代码改动 | 权限需被 ACTIVE 路由或菜单引用才生效（`OperationsDirectoryMapper.xml:56-70`）；激活步骤与 DML 分开 |
| API Gateway | 入口、限流 | 加载新快照 | 无改动 | 依赖 Platform 路由数据 |
| CCT / Platform / Payment（Vault 调用方） | 业务 Protect/Reveal | 无 | 无 | 它们的规则改由数据库提供，初始化数据一致即无感知 |
| 环境 | — | local：Docker `vault` profile 重新构建；test：Vault/BFF/Web 重新部署，`mor_vault`（test）执行 DDL 与初始化 DML，Platform 执行注册 DML | 部署与 SQL | 各需单独授权 |
| 文档与决定 | — | 新决定记录：策略入库并允许运营维护（取代 V1“部署配置”条款）、运营人工加/解密与 Token 展示（含密钥类人工解密，扩大 2026-10-13 决定范围）；更新 Vault 架构、Vault 三表设计、实现台账 | 文档 | 与 D2 决定的 V2 范围要求一致 |

## 3. 主要风险与依赖

1. **权限扩大：** 维护规则的人可以给任意服务开通解密。缓解：维护权限单独授予；每次变更记录前后差异；管理调用方（`operations-console-bff`）由部署配置固定，不能在页面上修改，避免把自己锁死或越权。
2. **可伪造的调用方请求头（既有 MVP 信任假设）：** 内网任何服务都可以冒充 `operations-console-bff` 调用管理接口，并自报操作人。这是 V1 已接受的边界，本需求不解决；强服务身份仍是后续事项，需在决定记录中写明。
3. **密钥类人工解密：** 渠道凭据被人看到后可以直接调用渠道 API。缓解：独立权限点、理由码加工单号、限流、审计；页面限时隐藏，不落浏览器。
4. **Token 展示给人：** 与“完整 Token 不进入外部 API”的规则冲突，需在决定记录中作为受控例外；Token 只在本次结果中展示，不进审计、列表或日志。
5. **热加载一致性：** 多个 Vault 实例刷新存在秒级窗口；审计里的 `policy_version` 记录实际使用的版本，可以追溯。
6. **初始化数据：** local 与 test 的策略不同；初始化 DML 要在各环境分别执行并核对，否则现有服务会被拒绝。Vault 新版本须在 DML 执行后再上线。
7. **疑似卡数据：** 人工加密的输入不可控，Vault V1 没有卡号检测。建议阶段 3 评估是否对人工加密增加卡号格式拒绝。

## 4. 工作量依据（粗估）

| 部分 | 内容 | 相对规模 |
|---|---|---|
| Vault Server | 4 张左右新表与加列、策略仓储与热加载、管理接口约 10 个、手动加/解密用例、测试 | 大 |
| Java 客户端 | 管理接口约 10 个方法、错误映射、测试 | 中 |
| Operations BFF | Controller、Client、模型、测试 | 中 |
| Operations Web | 5 类页面、2 个新组件、权限登记 | 中 |
| Platform 注册 DML | 权限、菜单、按钮、路由、请求头、限流 | 小 |
| 文档与决定 | 2 份决定、3 份文档更新 | 小 |

---

## 5. 规模总结（本次方案）

**5 个系统/工件**（Vault Server、Vault Java 客户端、Operations BFF、Operations Web、Platform 注册数据）；**Vault 新增 11 个内部管理接口**，Operations 对外新增 **13 个接口**（Gateway 路由）；Vault 库**新增 3 张表共 31 个字段**，**修改 1 张表**（`t_vault_access_event` 新增 3 个可空字段、1 个索引）；Vault 策略初始化 DML：local **9 行**、test **16 行**；Platform 注册 DML **174 行 INSERT**（6 权限、6 授权、11 菜单/按钮、13 路由、42 请求头、18 查询参数、78 限流规则）；**新增 6 个页面**、2 个前端组件；新增 1 份决定记录。

## 6. 架构与主流程

```text
Operations Web ──HTTPS──> Nginx ──> API Gateway（路由 → 权限点，PLT-I02 授权，限流）
                                      │  /api/operations/vault-admin/v1/...
                                      ▼
                         Operations BFF（OperatorContext、再次 PLT-I02、组装）
                                      │  X-Caller-Service: operations-console-bff
                                      │  X-Operator-Id: <运营账号 ID>   （Nacos 发现 vault-service）
                                      ▼
                         Vault  /vault/v1/admin/...（只接受 VAULT_ADMIN_CALLERS 中的调用方）
                           ├─ 策略维护：写 t_vault_data_type / t_vault_access_rule + t_vault_policy_change（同一事务）
                           ├─ 策略热加载：每 10 秒（可配置）在一致性读中全量读取数据类型与规则，重建内存快照
                           └─ 人工加/解密：复用 Protect/Reveal 用例 + 人工校验；审计写 actor_id / reason_code / ticket_ref
```

- **人工操作的用途固定为 `OPERATIONS_MANUAL`**：人工加/解密不走服务访问规则，而是由“管理调用方 + 数据类型开关 + 数据级别”授权，审计里的 `purpose` 记为该值，理由由理由码说明。服务后续读取人工加密的记录时，按它自己的访问规则授权（Reveal 不看记录是谁加密的）。
- **数据级别与权限的对应**：普通路由（`/manual/protect`、`/manual/reveal`）只允许 `STANDARD`；密钥路由（`/manual/secret-protect`、`/manual/secret-reveal`）允许 `STANDARD` 与 `SECRET`。BFF 按路由把允许的级别传给 Vault，Vault 查到数据类型/记录后校验；不匹配、类型停用或开关未开时一律返回 403 `ACCESS_DENIED`，不返回原值。
- **理由码（固定列表）**：`CUSTOMER_VERIFICATION` 客户信息核对、`DATA_REPAIR` 数据修复、`CHANNEL_CONFIGURATION` 渠道配置、`INCIDENT_INVESTIGATION` 故障排查、`OTHER` 其他。工单号必填，格式 `^[A-Za-z0-9._#-]{1,64}$`。

## 7. 数据设计（Vault 库 `mor_vault`）

完整 DDL：[db/vault/ddl.sql](./db/vault/ddl.sql)（待执行文件）。

| 表 | 用途 | 关键约束 |
|---|---|---|
| `t_vault_data_type`（11 字段） | 数据类型目录：编码、Token 模式、数据级别、说明、人工加/解密开关、状态、乐观锁 | `uk_vault_data_type_code`；编码与 Token 模式创建后不可改；只停用不删除 |
| `t_vault_access_rule`（9 字段） | 服务访问规则：调用服务 × 动作 × 数据类型 × 用途、状态、乐观锁 | `uk_vault_access_rule` 四元组唯一；只启停不删除 |
| `t_vault_policy_change`（11 字段） | 每次变更一行，即一个策略版本：版本号、变更类型、对象、前后元数据快照、理由、操作人、请求 ID | `uk_vault_policy_version` 版本号唯一；不含任何受保护值 |
| `t_vault_access_event`（改） | 新增 `actor_id`、`reason_code`、`ticket_ref`（均可空）与索引 `idx_vault_access_actor` | 服务调用三列为空；既有 CHECK 不变 |

**版本与热加载**

1. 每次维护操作在**一个本地事务**内：校验 → 更新目标行（`version` 乐观锁，不匹配返回 409）→ 插入 `t_vault_policy_change`（版本号 = 当前最大值 + 1，唯一键防并发重号，冲突时整体重试）。
2. 各 Vault 实例按 `VAULT_POLICY_REFRESH_INTERVAL`（默认 10 秒）在同一只读事务（REPEATABLE READ）里**全量读取** ACTIVE 数据类型、规则与最大版本号，编译成新的内存快照后原子替换。策略只有几十行，全量读取开销可忽略；不依赖版本号变化，测试直接写入规则也能生效。访问审计的 `policy_version` 记为 `db-<最大版本号>`（无变更记录时为 `db-0`）。
3. 刷新失败（数据库不可用）时保留上一份快照继续服务并记录诊断日志；启动时必须成功读取一次数据库，否则拒绝启动。

**初始策略（阶段 3 用户确认：不做程序导入）**

- Vault **只从数据库读取策略**，删除配置项 `VAULT_POLICY_FILE` 与策略文件加载代码；库中没有数据时正常启动，但所有服务调用都因无规则被拒绝，运营可直接在页面上新建。
- 各环境用初始化 DML 写入现有策略，由原策略文件逐条转写：[dml-local.sql](./db/vault/dml-local.sql)（2 类型、6 规则、1 条 `SEED` 变更，共 9 行）、[dml-test.sql](./db/vault/dml-test.sql)（4 类型、11 规则、1 条，共 16 行）。数据级别默认 `STANDARD`，人工开关默认关闭，不会因初始化扩大权限。
- 上线顺序：执行 DDL → 执行初始化 DML → 部署新版 Vault。旧版 Vault 不读新表，DDL/DML 先执行不影响它。
- 部署配置同步：`local-environment/compose.yaml`、`test-environment/compose.applications.yaml` 去掉策略文件挂载与 `VAULT_POLICY_FILE`；原 `policy.local.json`、`policy.json` 保留为转写来源与历史证据，标注不再被读取。
- **测试改造：** Go `test/` 入口测试与 Java `vault-test` 黑盒测试不再生成临时策略文件，改为在用例前插入本次运行专属前缀的数据类型与规则、结束后删除；测试配置把刷新间隔设为 100 毫秒，或在启动前写入数据。

## 8. 接口设计

### 8.1 Vault 内部管理接口（11 个）

公共要求：`X-Caller-Service` 必须在 `VAULT_ADMIN_CALLERS`（部署配置，默认 `operations-console-bff`，页面不可改）；`X-Operator-Id` 必填（运营账号 ID，格式 `^[A-Za-z0-9._-]{1,64}$`）；`X-Request-Id` 沿用现有规则；错误为 Problem，复用现有错误码并新增 `POLICY_VERSION_CONFLICT`（409）、`POLICY_ENTRY_EXISTS`（409）、`POLICY_ENTRY_NOT_FOUND`（404）。

| # | Method Path | 说明 | 请求要点 | 响应要点 |
|---|---|---|---|---|
| 1 | GET `/vault/v1/admin/data-types?status=` | 数据类型列表 | — | `{currentPolicyVersion, items:[{dataTypeCode, tokenMode, dataClass, description, manualProtectEnabled, manualRevealEnabled, status, version, updateTime}]}` |
| 2 | POST `/vault/v1/admin/data-types` | 新建 | `{dataTypeCode, tokenMode, dataClass, description, manualProtectEnabled, manualRevealEnabled, reason}` | 新对象 + `policyVersion` |
| 3 | PUT `/vault/v1/admin/data-types/{dataTypeCode}` | 修改（不含编码与 Token 模式） | `{expectedVersion, dataClass, description, manualProtectEnabled, manualRevealEnabled, status, reason}` | 同上 |
| 4 | GET `/vault/v1/admin/access-rules?dataTypeCode=&callerService=&action=&status=&page=&size=` | 规则分页 | — | 分页 `{items:[{accessRuleId, callerService, action, dataTypeCode, purposeCode, status, version, updateTime}], page, size, total}` |
| 5 | POST `/vault/v1/admin/access-rules` | 新建规则 | `{callerService, action, dataTypeCode, purposeCode, reason}`；数据类型须存在且 ACTIVE | 新对象 + `policyVersion` |
| 6 | PUT `/vault/v1/admin/access-rules/{accessRuleId}` | 启停规则 | `{expectedVersion, status, reason}` | 同上 |
| 7 | GET `/vault/v1/admin/policy-changes?targetType=&targetKey=&page=&size=` | 变更记录分页 | — | `{items:[{policyVersionNo, changeType, targetType, targetKey, actorId, reason, createTime}]...}` |
| 8 | GET `/vault/v1/admin/policy-changes/{policyVersionNo}` | 变更详情 | — | 含 `beforeSnapshot`、`afterSnapshot` |
| 9 | POST `/vault/v1/admin/manual/protect` | 人工加密 | Header `Idempotency-Key`（来自页面的 `submissionId`）；`{dataTypeCode, value, reasonCode, ticketRef, allowedDataClasses:[...]}` | `{token}` |
| 10 | POST `/vault/v1/admin/manual/reveal` | 人工解密 | `{token, reasonCode, ticketRef, allowedDataClasses:[...]}` | `{dataTypeCode, value}` |
| 11 | GET `/vault/v1/admin/manual-events?actorId=&action=&dataTypeCode=&from=&to=&page=&size=` | 人工操作审计分页 | — | `{items:[{requestId, actorId, action, dataTypeCode, reasonCode, ticketRef, outcome, resultCode, createTime}]...}`（无 Token、无原值） |

人工加/解密校验顺序：管理调用方 → 请求格式 → 数据类型存在且 ACTIVE → 对应人工开关 → 数据级别 ∈ `allowedDataClasses` → 加密/解密 → 审计（解密为“先审计成功再返回原值”，沿用现有规则）。任一步失败写一条 DENIED/FAILED 审计。

### 8.2 Operations 对外接口（13 个，BFF `/vault-admin/v1`，Gateway 前缀 `/api/operations`）

| # | Method Path | 权限点 | 对应 Vault 接口 |
|---|---|---|---|
| 1 | GET `/vault-admin/v1/data-types` | VAULT_POLICY_READ | 1 |
| 2 | POST `/vault-admin/v1/data-types` | VAULT_POLICY_MANAGE | 2 |
| 3 | PUT `/vault-admin/v1/data-types/{dataTypeCode}` | VAULT_POLICY_MANAGE | 3 |
| 4 | GET `/vault-admin/v1/access-rules` | VAULT_POLICY_READ | 4 |
| 5 | POST `/vault-admin/v1/access-rules` | VAULT_POLICY_MANAGE | 5 |
| 6 | PUT `/vault-admin/v1/access-rules/{accessRuleId}` | VAULT_POLICY_MANAGE | 6 |
| 7 | GET `/vault-admin/v1/policy-changes` | VAULT_POLICY_READ | 7 |
| 8 | GET `/vault-admin/v1/policy-changes/{policyVersionNo}` | VAULT_POLICY_READ | 8 |
| 9 | POST `/vault-admin/v1/manual/protect` | VAULT_MANUAL_PROTECT | 9，`allowedDataClasses=[STANDARD]` |
| 10 | POST `/vault-admin/v1/manual/secret-protect` | VAULT_MANUAL_PROTECT_SECRET | 9，`[STANDARD, SECRET]` |
| 11 | POST `/vault-admin/v1/manual/reveal` | VAULT_MANUAL_REVEAL | 10，`[STANDARD]` |
| 12 | POST `/vault-admin/v1/manual/secret-reveal` | VAULT_MANUAL_REVEAL_SECRET | 10，`[STANDARD, SECRET]` |
| 13 | GET `/vault-admin/v1/manual-events` | VAULT_POLICY_READ | 11 |

- 请求体与 Vault 基本一致，BFF 补入 `X-Operator-Id`（来自 `OperatorContext.accountId`）与 `allowedDataClasses`，不接受页面传入的级别或操作人。
- 人工加密请求体带 `submissionId`（页面每次打开表单生成的 UUID），BFF 用作 Vault 的 `Idempotency-Key`，重复提交返回同一 Token。**不使用** Gateway 的 HTTP `Idempotency-Key`，避免 Token 被写入 Gateway 的重放存储。
- 人工解密/加密响应带 `Cache-Control: no-store`（BFF 现有拦截器已统一设置）；BFF 不记录请求体与响应体。

示例（人工解密）：

```http
POST /api/operations/vault-admin/v1/manual/secret-reveal
{"token":"Q2h…(43 位)","reasonCode":"CHANNEL_CONFIGURATION","ticketRef":"OPS-1024"}

200 {"dataTypeCode":"CHANNEL_ACCOUNT_CONFIG_V1","value":"{\"merchantNo\":\"…\"}"}
403 Problem code=ACCESS_DENIED   （级别不允许、类型未开启人工解密或已停用）
404 Problem code=TOKEN_NOT_FOUND
```

### 8.3 Java 客户端

`paytech-vault-client` 新增 `VaultAdminClient`（独立接口与自动配置开关 `vault.client.admin-enabled`，默认关闭），方法与 8.1 一一对应；错误码枚举增加 3 个新码。业务服务继续只依赖 `VaultClient`，不受影响。

## 9. 权限、菜单与路由（Platform 注册数据）

DML：[db/platform-support-service/registration-dml.sql](./db/platform-support-service/registration-dml.sql)（174 行，全部 INSERT，新行 DISABLED/REVOKED，不含 COMMIT）。

| 权限点 | 风险级别 | 授予建议 |
|---|---|---|
| VAULT_POLICY_READ | MEDIUM | 安全/平台管理员、审计人员 |
| VAULT_POLICY_MANAGE | CRITICAL | 少数安全管理员 |
| VAULT_MANUAL_PROTECT / VAULT_MANUAL_REVEAL | HIGH / CRITICAL | 需要核对客户信息或修数据的运营 |
| VAULT_MANUAL_PROTECT_SECRET / VAULT_MANUAL_REVEAL_SECRET | CRITICAL | 负责渠道配置的极少数人；应同时持有对应普通权限 |

菜单“数据保护”下 6 个页面、4 个按钮（维护数据类型、维护访问规则、加密密钥类数据、解密密钥类数据）。人工加/解密路由的单人限流为每分钟 5 次、并发 1，其余为每分钟 10 次、并发 2。

## 10. 页面设计（6 个页面、2 个组件）

| 页面 | 路由 | 主要内容 |
|---|---|---|
| 数据类型 | `/vault/data-types` | 表格：编码、Token 模式、数据级别、人工加密、人工解密、状态、更新时间；顶部显示当前策略版本。“新建/编辑”抽屉：编辑时编码与 Token 模式只读；必填变更理由；409 时提示刷新 |
| 访问规则 | `/vault/access-rules` | 筛选（数据类型、调用服务、动作、状态）+ 表格；新建弹窗（调用服务、动作、数据类型下拉、用途、理由）；启用/停用需理由 |
| 策略变更记录 | `/vault/policy-changes` | 表格：版本号、变更类型、对象、操作人、理由、时间；详情抽屉并排显示变更前后字段，差异高亮 |
| 手动加密 | `/vault/manual-protect` | 表单：数据类型下拉（仅显示开启人工加密且当前用户级别允许的类型）、值（密码框，支持多行）、理由码、工单号。提交后清空值，结果卡片一次性显示 Token（复制按钮），离开页面即丢弃 |
| 手动解密 | `/vault/manual-reveal` | 表单：Token、理由码、工单号。结果用“敏感值展示”组件：默认遮罩，点击“显示”后明文可见并开始 30 秒倒计时，到期自动隐藏并清除；提供复制按钮 |
| 人工操作记录 | `/vault/manual-events` | 筛选（操作人、动作、数据类型、时间）+ 表格：时间、操作人、动作、数据类型、理由码、工单号、结果 |

- **敏感值展示组件**：值只保存在组件内存状态，不写 URL、本地存储或日志；30 秒后、页面切换或组件卸载时清空。
- **工单号输入组件**：带格式校验与提示，复用到加密、解密两个表单。
- 密钥类权限：页面根据按钮资源 `OPERATIONS_VAULT_SECRET_PROTECT/REVEAL` 决定调用普通路由还是密钥路由；没有该按钮时，下拉中不出现 SECRET 类型。

## 11. 决定记录（随本阶段一并评审）

新增 `agent-docs/decisions/2026-10-14-vault-policy-and-manual-access.md`：

1. Vault 策略（数据类型与访问规则）改为 Vault 库中的版本化数据，由 Operations 维护、保存即生效并记审计；取代 V1“Allowlist 是部署配置”的条款。
2. 新增管理接口与人工加/解密能力；管理调用方由部署配置固定；人工操作用途固定为 `OPERATIONS_MANUAL`，审计记录操作人、理由码、工单号。
3. 受控例外：人工加密的 Token 可一次性展示给有权限的运营；密钥类数据可在 `VAULT_MANUAL_REVEAL_SECRET` 权限下人工解密（扩大 2026-10-13 渠道账号读取决定的范围）。
4. 已知限制保持：`X-Caller-Service` 可伪造、无强服务身份、无生产 KMS。

## 12. 研发与验证计划

| 顺序 | 系统 | 内容 | 验证（对应 AC） |
|---|---|---|---|
| 1 | Vault | DDL 与 PDManer 模型；sqlc 查询；策略仓储与定时全量热加载（删除策略文件加载）；11 个管理接口；人工加/解密；审计扩列；OpenAPI；测试夹具改为写表 | `test/auto` 入口测试：维护与 409（AC-2/4）、热加载生效（AC-3）、人工加/解密各级别与开关（AC-5/6/8/11）、审计字段（AC-7）、既有 Protect/Reveal 不变（AC-9）；初始化 DML 与原文件逐条一致 |
| 2 | Vault 客户端 | `VaultAdminClient` 与错误码 | 客户端 HTTP 测试 |
| 3 | Operations BFF | Controller、Client、模型；Nacos 发现 vault-service | 入口测试（外部 DTO Mock Vault）：权限路由、级别传递、submissionId 幂等、无请求体日志 |
| 4 | Operations Web | 6 个页面、2 个组件、导航登记 | 单元测试 + 构建；本地浏览器验收（AC-10） |
| 5 | 本地联调 | local：执行 Vault DDL（`mor_vault`）、Platform 注册 DML 并激活、重建 Docker Vault、BFF、Web | 经 Nginx 正式入口走通全部 AC |

本地需执行的 SQL（阶段 4/5 前单独授权）：`db/vault/ddl.sql` 与 `db/vault/dml-local.sql` → 本机 `mor_vault`；`db/platform-support-service/registration-dml.sql` → 本机全链路 Platform 库 `mor_platform_e2e`，以及激活语句。test 环境对应 `dml-test.sql`，在阶段 6/7 单独授权。

## 13. 未纳入本期

- 人工加密输入的卡号格式拦截（V1 无检测；如需要另行评估）。
- 强服务身份、生产 KMS、批量操作、导出。
