# Client API 接入管理：阶段 2 差异与影响分析

本分析依据用户已确认的 [Operations 范围](./requirement.md)：凭证查看、Scope 目录与 Route 关联、紧急撤销。Client 自助创建、轮换及授权调整退出本期。阶段 2 已由用户“OK”确认。本文保留当时的分析与估算；现行阶段 3 审阅入口为 [实施方案](./design.md)、[接口](./api.md)、[字段与关系](./data-design.md)、[Pencil 页面](./ui.md) 和 [SQL](./db/README.md)，以下“预计/阶段 3 再定”属于阶段 2 记录，不覆盖阶段 3 精确清单。确认记录见 [delivery.md](./delivery.md)。

**一句话规模：预计改动 Platform Support、Operations BFF、Operations Web 3 个应用，新增约 9 项 API 能力、调整 4 项既有 API，新增 1 张 Scope 目录模型表，现有业务表预计不增字段；test 还缺 2 张既有凭证表，因此预计需应用 3 张表的建表 SQL，其中 2 张共 21 字段属于旧设计补齐；新表字段、查询索引和 DML 行数在阶段 3 精确核定。**

## 1. 直接证据

| 主题 | 证据 | 结论 |
|---|---|---|
| Credential 认证 | [API](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/apicredential/ApiCredentialValidationApi.java) · [认证服务](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl) | 已有一致性读取 Tenant/Credential/有效 Scope、PHC 校验及失效拒绝；可复用，不需要新认证协议 |
| 管理持久化缺口 | [Repository](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/repository/ApiCredentialRepository.java) · [Mapper](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/ApiCredentialMapper.xml) | 只有按 prefix 获取认证候选、读取当前有效 Scope，没有列表、详情、授权记录分页或撤销写入；认证投影包含 verifier，不应直接返回后台 |
| 现有结构 | [Schema](../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql) | t_api_credential 已有 status/version/expires_time/rotated_from_id；t_api_credential_scope 已有授权记录；t_access_tenant 已有 tenant_name、owner_ref_id 和两个状态 |
| Route 输入 | [GatewayEditor](../../../operations-console-web/src/configuration/GatewayEditor.tsx) | requiredScopeCode 是手动 Input，Scope 名称/目录选择缺失 |
| Route 校验 | [Compiler](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/gatewayconfiguration/GatewayConfigurationCompiler.java) | API_SCOPE 目前只校验字符串格式，不校验全局目录；Snapshot 传 requiredScopeCode 字符串 |
| Gateway 认证 | [认证管理器](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/clientapi/ClientApiCredentialReactiveAuthenticationManager.java) | 每次认证调用 PLT-I03，不需要另建撤销缓存或广播机制 |
| 查询及管理复用 | [Permission 管理服务](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/PermissionManagementServiceImpl.java) · [管理 Processor](../../../paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor) | 分页、代码身份、Redis/CAS、既有幂等与审计可复用；不原样套用 Permission 生命周期 |
| 审计查询 | [现有 API](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/operations/OperationsManagementApi.java) · [Audit Mapper](../../../paytech/platform-support/paytech-platform-support-audit-security/src/main/resources/mapper/audit/OperationsAuditMapper.xml) | 审计已存 resource_type/resource_id，但分页查询没有这两个筛选，不能准确查询单条凭证的记录 |
| test 只读观察 | [只读计数证据](./assets/stage2/test-database-observation.json) | 当前 mor-test/mor_platform 无 t_api_credential、t_api_credential_scope；CLIENT Tenant 和 API_SCOPE Route 均为 0。仅查询表存在性及数量，没有读取秘密、创建示例或应用 SQL |

实际实施、验证和部署状态由 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)统一维护；上述源码存在不等于 test 已经具备凭证能力。

## 2. 逐项差异

| 需求 | 当前行为/缺口 | 处理建议与影响 |
|---|---|---|
| F-01 凭证列表 | 只有认证按 prefix 查候选，无后台分页 | 新增受控管理查询，按 Client ID/租户显示名、凭证 ID/前缀、状态筛选；列表和总数使用相同条件 |
| F-02 详情与 Scope | 认证响应仅含可信身份和有效 Scope，不是管理详情 | 单独管理投影，查询基础信息及分页授权记录；完整值与 PHC 不出管理查询 SQL/DTO；到期状态按时间识别 |
| F-01/F-02 Client 展示 | Platform 有 tenant_name 和 owner_ref_id，没有 CCT 业务主档目录接口 | 显示租户显示名 + Client ID，明确是本地 Tenant 展示信息；不把它当实时法定名称，不扩大 CCT 查询或复制客户主档 |
| F-03 使用关系 | Scope 是散落于 Route 和各 Credential 授权的代码，没有统一关系页 | Scope 详情复用按 scopeCode 筛选的凭证列表、按 requiredScopeCode 筛选的 Route 列表；BFF 组装页面，按各自查看权限调用 |
| F-04 紧急撤销 | 有 REVOKED 状态及认证拒绝，没有后台写入口 | 增加单凭证撤销，实际资源锁 + version/CAS + 既有幂等；保留 Scope 子行和轮换来源，不级联撤销其他凭证 |
| F-05 Scope 目录 | 授权表每行归属某 Credential，无法保存未被授予的全局定义 | 增加一个独立目录；编码固定，名称/说明可改，不增加启停/历史/发布流程 |
| F-06 Route 关联 | 自由文本允许拼写合法但目录不存在的值 | 选择器读目录；后端校验新建或变更的绑定；requiredScopeCode Wire 和 Gateway 精确匹配保持 |
| F-07 审计 | 已有 Controller 尽力记录，无本期动作投影；缺少对象筛选 | 增加本期受控动作，既有审计列表补 resourceType/resourceId；继续按 Operations 审计权限和来源范围过滤 |
| F-08 权限 | 现有 Permission/Gateway 权限不能替代凭证管控权限 | 增加凭证 READ/REVOKE、Scope READ/MANAGE 四类权限候选，路由管理继续用既有权限；代码及注册值阶段 3 定稿 |
| F-09 验证 | PLT-I03 已有真实 HTTP/本机数据库用例，test 缺表和 Client 示例 | 开发复用真实认证测试链补撤销前后及并发验证；test 缺表按本次发布材料补齐，合成示例另列清单；不借本期上线 Client Console/Commerce 接入 |

## 3. 逐系统影响

| 系统/仓库 | 本期改动 | 数据/部署 | 兼容与边界 |
|---|---|---|---|
| Platform Support | access-control 增加凭证查询/撤销和 Scope 目录能力，扩展 Route 校验/筛选；audit-security 扩展动作和对象筛选；api/web 增加合同/授权/编排 | 唯一业务库 mor_platform；同步模型、完整 Schema/初始化与增量材料，后续部署应用 | 不暴露认证 DataObject；事务内只操作本库，跨模块由既有 web 层编排，不新增业务模块 |
| Operations BFF | 转发新增 API，保留 PLT-I02 和各上游授权；组装详情、Scope 名称及关系页，透传既有分页/错误/并发合同 | 无业务库；随 Web/Platform 协调部署 | 有真实查询组合时使用应用层，不将页面拼装全部下沉 Platform |
| Operations Web | 新增凭证查看与 Scope 目录页面；Route 改目录选择；既有审计支持对象筛选/跳转 | 更新已部署页面能力表、导航挂载和 Pencil | 无创建/轮换/调整凭证 Scope 的按钮或 API 调用；沿用只读详情、紧凑操作菜单、浏览器回退和省略展示 |
| API Gateway | 预计无需生产代码修改；沿用动态路由、PLT-I03、精确 Scope | 新 Operations API 通过 Platform 注册进入 Snapshot；无需为了目录增加网关业务名单 | Snapshot 的 requiredScopeCode 和认证响应保持；最终打包依赖和兼容在阶段 3/6 再核对，不能仅凭本分析保证无需重建制品 |
| Client Console/BFF | 不改 | 不部署 | 后续自助生命周期单独评审 |
| CCT/Commerce/Payment/Risk/Finance | 不改应用或业务模型 | 不开通新的真实业务 API | Client 显示复用 Platform Tenant 信息，真实业务 Owner 首次接入不再是本期前置 |
| Foundation | 预计无新增能力或修复 | 复用现有组件 | 本次不新增通用幂等/锁/审计框架；若发现必要缺陷按具体范围另行确认 |
| 根需求目录、test 材料 | 同步 PLT-02/PLT-16/PLT-I06、E2E-16 的范围与环境清单 | 只在阶段 3/6 准备 SQL/部署材料，后续获准执行 | 不修改其他任务的数据模型、历史执行 SQL 或默认全量部署脚本来掩盖前置 |

## 4. 数据设计取舍

### 4.1 为什么只新增一张目录表

新增目录保存 **一个 Scope 编码对应的名称与说明**。它由运营登记、供 Route 选择和关系查询使用，允许先建目录再有 Route/授权。现有 t_api_credential_scope 保存的是某条凭证的授权，基数和生命周期不同；从其 DISTINCT scope_code 推导目录会让未授权 Scope 无处保存，并把历史拼写错误自动当正式能力。t_permission 则面向人员操作，不能拿来兼存机器 Scope。

示例：登记 `checkout.read` 后可绑定两个查询 Route，尚未给任何凭证授权；之后 Client 自助阶段才给某条凭证授予它。删除独立目录会失去该未授权定义和独立名称/说明维护场景。

目录预计只需要身份、编码、名称、说明、并发版本和创建/更新时间。阶段 3 再冻结表名、具体字段、约束和索引；本期不建 Scope 版本表、变更回执、Credential 历史表、额外授权关系表或网关状态表。

### 4.2 保留现有表与字段

- t_api_credential：使用既有 status/version/update_time 执行撤销，expires_time 用于读取当前是否过期，rotated_from_id 用于只读展示；预计不新增业务字段。
- t_api_credential_scope：保留既有授权和 effective 时间。resource_pattern/action_code 虽存在于历史模型，本期按原值只读，不因为列名而实现资源表达式或改变 PLT-I03 的精确 Scope 规则，也不顺便删除旧列。
- t_gateway_route：继续存 required_scope_code，不加 scope_id 或与具体 Credential Grant 的新关系；保存新绑定时由 Platform 检查目录。
- t_access_tenant：复用 tenant_name、owner_ref_id、owner_status、security_status，不复制 CCT 名称列。
- t_command_idempotency 及既有审计结构：复用，不新增专用回执表。审计缺失时不伪造变更历史，遵守现有尽力审计决定。
- 查询索引：需核对 Scope→Route、Scope→Credential 和单对象审计查询的访问路径，可能补索引；索引不等于新增业务字段，具体数量由阶段 3 EXPLAIN/现有模型审阅确定。

### 4.3 test 补表与数据兼容

本次只读观察确认 test 未建两张既有表：t_api_credential 的 10 字段及 t_api_credential_scope 的 11 字段。它们已在正式 Schema 中，不应再建第二套同义结构；阶段 3 从唯一模型准备有前置条件的增量文件。加上新 Scope 目录，test 预计需要 CREATE 3 表，而模型只新增 1 表。

当前 test 无 CLIENT Tenant 或 API_SCOPE Route，无这类旧业务值需要补目录；这只是本次观察，执行前重新核对。其他本机库、环境或后续新增数据不能据此推断为空。新菜单、权限、Route/Header/Query/Guard 的注册 DML 与合成验收数据分开核数，具体行数阶段 3 定稿；本轮不执行任何写 SQL。

有旧数据的环境先列出 Route/授权中合法唯一 Scope 及冲突，再由人工确认目录记录。建议新建/改绑要求目录存在，未登记旧值只允许保留原绑定和只读显示，不用目录缺项把既有 Snapshot 或凭证验证整体打坏；未知/冲突处理在阶段 3 给出明确兼容规则和用例。

## 5. API 改动候选

下列是阶段 2 工作量拆分，不是最终 Method/Path 合同。

| 类别 | 能力 | 预计新增 |
|---|---|---|
| 凭证查询/管控 | 列表、详情、授权 Scope 记录分页、紧急撤销 | 4 |
| Scope 目录 | 列表、详情、新增、编辑名称/说明 | 4 |
| Route 使用的 Scope 选择器 | 有界分页、名称/编码搜索及对应管理权限判断 | 1 |
| 合计 | Platform 和 Operations BFF 分别落地相同 9 项能力 | 9 项业务 API，约 18 个两侧端点 |

预计调整 4 项既有 API：Route 列表补 requiredScopeCode 筛选；Route 新增/更新校验目录新绑定；审计列表补 resourceType/resourceId。BFF 适配相同变更，不另建版本。关系页复用这些筛选，不再额外创建一套引用 API；Scope 摘要/详情所需数量由同一过滤口径计算。

选择器不能要求每位 Route 管理员额外获得完整 Scope 管理权限：沿用现有 Permission 选择器的用量授权做法，只返回必要选项，调用者仍须有对应页面读取/操作资格。如采用 HUMAN_AUTHENTICATED 公共 Route，必须同步 PLT-I02 的已认证允许清单，再在 Owner 内按用途鉴权；不能重复前序选择器因漏注册返回 403 的问题。

任何 Scope 引用的凭证查询均须凭证查看权限；Scope 查看权限本身不授予跨 Client 凭证查看。审计链接保留既有审计查看权限，不能借凭证详情扩大审计可见范围。

## 6. 关键行为与验证重点

- **撤销资源与并发：** 以 credentialId 为资源锁，不以 Client/Tenant 锁全部凭证；本地事务内重验会话/撤销权限及当前版本，更新 status/version。使用既有幂等记录保证同请求重放，最新记录已撤销时不再次变更；精确成功/冲突响应阶段 3 定义。
- **认证生效：** 复用现有 PLT-I03 一致性读。撤销提交后的新认证拒绝；已取得旧快照的在途请求维持原语义，不增加 Gateway ACK、吊销消息或强制中断引擎。
- **失效可排查：** 列表详情不误用认证的“必须 ACTIVE Tenant”过滤器，否则停用客户的泄漏凭证反而看不到、撤销不了；显示凭证状态与 Tenant 状态，并区分已存授权和实际可用性。
- **秘密最小投影：** 管理 SQL/DTO 不读取和暴露 PHC；密钥前缀仅用于后台识别，不把普通管理查询变成凭证校验或凭证取回。
- **审计和事务：** 复用当前 Controller 注解/业务事实，提交后独立事务尽力记录。增加资源类型/标识过滤时由 audit-security 拥有查询，凭证模块不越模块直读审计表；幂等 Hash 的外部准备仍在事务前。
- **目录与运行隔离：** Scope 无删除/停用/改编码，本期无需给运行认证新增目录依赖；名称编辑只改变展示，授权仍由既有有效 Grant 决定。Route 新绑定检查目录，Gateway 继续读稳定字符串。
- **开发回归：** 从公开管理 HTTP 和 PLT-I03 入口覆盖分页、只读投影、权限组合、撤销前后、两次撤销/旧版本/权限收回、过期与租户失效、目录重名/并发、引用去重和新旧 Route 兼容。使用仓库本机 MySQL/Redis 和明确的合成 Fixture，不连 test 跑 Java auto。
- **test 验收：** 管理链可经真实 Operations 入口验证。因当前没有 Client 示例，后续需单独列明合成 Tenant/Credential/Grant、保护秘密、验证和清理方式；这是本功能的合成验收数据，不能代表真实 CCT 客户或业务 API 已开通。

## 7. 阶段 3 交付内容

确认本分析后再交付：精确 API Method/Path 与请求响应、9 项候选是否合并/拆分的最终统计；1 张新目录表及 2 张既有补建表的关系/字段图、索引和 DML 数量；缺表/旧 Scope 的环境前置和唯一执行 SQL；两新页面与 Route/审计增量的 Pencil 设计；分系统研发及验证计划。

本次没有修改应用代码、模型或运行配置，没有应用 SQL、部署或 Git 提交。下一确认点是接受本分析及「3 个主要应用、1 张新模型表、test 补齐 2 张旧表、Client 自助和真实业务 API 开通延期」的影响边界。
