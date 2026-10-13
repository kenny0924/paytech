# 研发计划 · 第 1 步：配置管理后端

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。用户对计划回复“可以”，D1～D6 按建议执行，含 D5 本地建表与 D6 修改代码的授权；不含提交、推送或其他环境的 SQL。
- 范围：Platform Support 中六张表的数据访问、30 个管理接口（[管理接口](./admin-api.md)）、保存时的静态校验、自动化测试。
- 不含：BFF、前端、注册数据执行（第 2 步）；执行引擎、内部标准 API 接口、Vault（第 3 步）；Payment 改造（第 4 步）。

## 0. 已读规范与本次说明

已读：[开发约定](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[Java 工程规范第 4 节](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md#4-api-模块与内部-http-契约)、[公共写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)、[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)，以及本需求的流程结构、原子 API 结构、物理设计和管理接口。代码参照：Platform 的 API Scope 管理（2026-09-17 重构，`UpdateApiScopeProcessor` 等）。

- **入口与 Context：** 每个写操作一个 Processor（如 `UpdateIntegrationApiProcessor`），Context 持有本次涉及的 DataObject（如 `Integration` + `IntegrationApi` + 字段列表）。
- **本地原子写入：** 一次写入在一个事务内完成权限复核、行锁读取、引用校验、保存主表与字段表（整组替换）、版本 CAS。
- **事务外步骤：** 无。管理接口不调用任何外部服务；审计按 Platform 现有方式在提交后写入。

## 1. 模块与包

| 位置 | 内容 |
|---|---|
| `platform-support-api` 新包 `api/externalapi/` | 6 个 `@HttpExchange` 接口（见下）、`request/`、`response/` DTO（普通 class、无 Lombok，沿用该模块现状） |
| **新业务模块 `platform-support-integration`** | Processor、Context、Service、Repository、Mapper + XML、DataObject、静态校验器、配置类 |
| `platform-support-web` | `ext/rest/externalapi/` 的 Controller 与 Converter；`@MapperScan` 增加新模块 Mapper 包；注册新的 CodeEnum TypeHandler |
| `platform-support-test` | 入口测试、Builder、Mocker、Checker、TestMapper；`init/ddl.sql` 补表 |

新模块依赖 `platform-support-api`、`platform-support-access-control`（只用公开的 `OperationsManagementPolicyService` 做权限复核）和 `platform-support-audit-security`（`OperationsAuditService`），与现有 access-control 依赖 audit-security 的方向一致，无环。第 3 步的执行引擎也放在这个模块。

API 按对象拆分，沿用 `ApiScopeManagementApi` 的命名：

| 接口 | 端点 |
|---|---|
| `StandardApiManagementApi` | E01～E05 |
| `IntegrationManagementApi` | E06～E10 |
| `IntegrationApiManagementApi` | E11～E16 |
| `IntegrationScriptManagementApi` | E17～E22 |
| `IntegrationImplementationManagementApi` | E23～E28 |
| `ExternalApiEditorApi` | E29、E30 |

## 2. 数据访问层

| 表 | DataObject | Repository / Mapper |
|---|---|---|
| `t_standard_api` | `StandardApi` | `StandardApiRepository` |
| `t_api_field` | `ApiField` | `ApiFieldRepository`（按 `api_id + direction` 整组删除再插入） |
| `t_integration` | `Integration` | `IntegrationRepository` |
| `t_integration_api` | `IntegrationApi` | `IntegrationApiRepository` |
| `t_integration_script` | `IntegrationScript` | `IntegrationScriptRepository` |
| `t_integration_standard_api` | `IntegrationImplementation` | `IntegrationImplementationRepository` |

- 闭合枚举（`direction`、`field_location`、`data_type`、`execution_stage`）用 `core/enums` 下实现 `CodeEnum` 的枚举，并在 `PersistenceConfiguration` 注册 `CodeEnumTypeHandler`。
- JSON 列（`definition_json`、`flow_definition_json`、`endpoints`、`parameter_definition`）在 DataObject 中为 `String`，由领域层解析，沿用 Platform 现有做法，不新增 JSON TypeHandler。
- 写入沿用 API Scope 的模式：`lock` 查询 `FOR UPDATE`，`UPDATE ... SET version = version + 1 ... WHERE id = ? AND version = ?`，行数不为 1 抛 `ConcurrentRequestException`；成功后回写原对象的版本和时间。
- 唯一键冲突（编码重复）在 Repository 捕获 `DuplicateKeyException` 并按索引名转为 `BizCheckFailException`。
- 列表查询为 `LIMIT/OFFSET` + `COUNT` 共用筛选片段，按编码 + ID 稳定排序。

## 3. 写接口流程

```text
Controller（@Audit）→ Converter → XxxProcessor extends AbstractLockProcessor
  lockCommand：tryLock("external-api-config")                 ← R4：全模块一把锁
  processWithinLock：
    TransactionTemplate（READ_COMMITTED，15 秒，沿用 Platform）
      ├─ 权限复核 authorizeManagement(..., "EXTERNAL_API_MANAGE")
      ├─ 读取并锁定目标行、核对 expectedVersion
      ├─ 读取引用方（删除保护、字段覆盖所需的标准 API / 原子 API / 脚本）
      ├─ 静态校验（纯内存，失败抛 InvalidParameterException 并带字段路径）
      ├─ 构造 Context → XxxManagementService.save / delete
      └─ 审计事实 ConfigurationChanged（提交后写入）
    ConcurrencyFailureException → ConcurrentRequestException（409）
```

- 15 个写 Processor：每个对象的 Create / Update / Delete。
- 5 个 Service（interface + Impl）按对象：`StandardApiManagementService`、`IntegrationManagementService`、`IntegrationApiManagementService`、`IntegrationScriptManagementService`、`IntegrationImplementationManagementService`，只用 Repository 保存，不重复查询、不调用其他 Service。
- 配置类 `ExternalApiConfigurationProperties`（前缀 `ps.integration.configuration`）：`lockWaitTime` 500ms、`lockLeaseTime` 30s，带范围校验，与现有锁配置一致。
- 审计字段：记录编码、名称等标量字段的前后值；`definition_json` 等大字段只记录“已变更”和序列化字节数，不写入完整内容。

## 4. 查询与校验接口

- 12 个查询（E01、E02、E06、E07、E11、E12、E17、E18、E23、E24、E29、E30）使用 `AbstractProcessor`，先 `requirePermission(..., "EXTERNAL_API_READ")`，再查询。详情需要多表组合时使用具名 `*View`（如 `IntegrationApiDetailView` 含字段与引用方）；列表不加事务，详情使用只读 `REPEATABLE_READ`，保证主表与字段表来自同一快照。
- 3 个校验接口（E16、E22、E28）使用 `AbstractProcessor`、不加锁、只读事务，复用写接口的同一套静态校验，返回 `issues` 而不是抛异常。
- E30 编辑器能力目录来自代码常量与注册函数目录；第 1 步的函数目录只列已确认的 `CHANNEL_ACCOUNT_CONFIG`（初始化）与 `MONEY_MINOR_UNITS`（纯函数）的签名，实现于第 3 步。

## 5. 静态校验器（领域层纯 Java）

| 校验器 | 规则来源 |
|---|---|
| `ApiFieldRules` | 物理设计第 3 节：路径语法（最多 16 段、每段 64 字符、`[]` 数组）、位置与方向组合、HEADER 名称大小写不敏感判重、父对象存在、每个方向 128 个字段 |
| `EndpointRules` | 物理设计 P5：`baseUrl` 仅 https、无用户信息/查询/片段，`endpointCode` 唯一，最多 16 项 |
| `IntegrationApiDefinitionParser` + `Rules` | [原子 API 结构](./integration-api-definition-contract.md)：严格 JSON（拒绝未知、重复字段）、路径模板变量与 PATH 字段一一对应、固定值字段存在且为常量、`params` 与脚本参数绑定、`endpointCode` 存在、输出分支只允许 SUCCESS/FAIL、65,536 字节上限 |
| `IntegrationScriptRules` | 阶段、参数声明（最多 32 项）、源码 32 KiB；修改阶段或参数时检查全部引用它的原子 API |
| `FlowDefinitionParser` + `FlowGraph` + `Rules` | [流程结构](./flow-definition-contract.md)第 6 节：ID 唯一、入口、可达、**无环**、每节点恰好一条兜底、每条路径终止于出口、`steps.*` 必经检查（支配节点）、请求字段与参数覆盖、出口覆盖标准 API 必填输出、预算上限 |
| `ValueBindingRules` | PATH 根只能是 `input`/`resources`/`steps`；`credentials.*` 只能绑定到 `PROTECTED_HANDLE` 参数；节点内 FUNCTION 只允许纯函数 |

所有校验返回统一的 `ValidationIssue(code, location, message)` 列表；`location` 使用请求体路径，写接口据此生成 Problem `errors[]`，校验接口原样返回。

**表达式（Aviator）：** 项目尚未引入 Aviator（技术栈与各模块均无此依赖）。第 1 步只做表达式文本的非空与长度检查，并提取 `input.`/`resources.`/`steps.` 路径引用做必经与存在性校验；**语法编译与白名单剖面放到第 3 步**，在引入 Aviator 依赖并更新技术栈时一起完成（评审点 D3）。

## 6. 测试

### 6.1 前置

- **测试库：** `platform-support-test/src/test/resources/init/ddl.sql` 增加 6 张新表，以及权限复核所需但尚未包含的表（账号、会话、成员、角色、权限、角色授权，按实际缺失补齐）。
- **本地库：** 在仓库本地 MySQL 的 `mor_platform` 上执行 [integration-create.sql](./db/platform-support-service/integration-create.sql)（只建表，不使用 `init/ddl.sql` 里的 `DROP DATABASE`）；需要你授权（评审点 D5）。
- **Controller 加载：** 现有 `application-mock.yml` 未开启 Operations 认证，管理类 Controller 在测试中不加载，现有管理接口也没有 HTTP 测试。本次让外部 API 管理 Controller 在测试中加载，并由本地 Mocker 插入操作员账号、会话、角色与权限行，让真实 `OperationsManagementPolicyService` 完成复核（评审点 D4）。

### 6.2 测试类与场景

全部从真实 HTTP 入口发起，经真实 Processor/Service/Repository 与本地 MySQL、Redis；无外部依赖，不需要 Mock HTTP 服务。

| 测试类 | 主要场景 |
|---|---|
| `StandardApiManagementTest` | 创建 / 详情 / 列表分页与关键字；字段整组替换；编码重复 400；版本冲突 409；被实现引用时删除 400；无权限 403；字段路径非法 400 带 `errors[]` |
| `IntegrationManagementTest` | 出站地址校验（http、带查询串、重复代码）；被原子 API 引用的地址代码不能删除；接入方仍有下级对象时不能删除 |
| `IntegrationApiManagementTest` | 路径变量与 PATH 字段不一致、固定值字段不存在、脚本阶段不符、`endpointCode` 不存在；被流程引用时删除 400；校验接口返回 issues 不落库 |
| `IntegrationScriptManagementTest` | 修改阶段破坏引用 400；被引用不能删除；源码超限 |
| `IntegrationImplementationManagementTest` | 有环、缺兜底、未终止、读取非必经节点、请求字段未覆盖、出口缺必填输出、凭据绑定到普通字段等各返回对应路径；合法流程保存；同一标准 API 重复实现 400 |
| `ExternalApiEditorTest` | 流程编辑器上下文返回全部原子 API 契约摘要；能力目录内容 |
| `ExternalApiConfigurationConcurrencyTest` | 两个并发写入一个成功、一个 409；锁释放后可继续写入；事务失败（如字段唯一冲突）整体回滚 |

另外为 `FlowGraph`（环检测、支配节点）和 `ApiFieldRules`（路径解析）写聚焦的纯 Java 测试，图算法的边界情况多，直接测更容易定位。

### 6.3 测试工具

Builder：各请求体的 `template()`；本地 Mocker：`OperatorMocker`、`StandardApiMocker`、`IntegrationMocker`（可链式建原子 API、脚本、实现）；`ExternalApiDatabaseChecker` 读表断言；`ExternalApiTestMapper` 承载测试 SQL 与清理。全部实现 `Clear`，由公共 `TestBase` 统一清理。

## 7. 实施顺序与验证

| 子步骤 | 内容 | 完成判定 |
|---|---|---|
| 1a | 新模块、DataObject、Mapper/XML、Repository、TypeHandler 注册、测试 DDL | 编译通过；Repository 级插入/CAS 冒烟 |
| 1b | 标准 API、接入方的写与查（E01～E10）+ 字段与地址校验 | 两个测试类通过 |
| 1c | 原子 API、脚本（E11～E22）+ 定义解析与校验 | 两个测试类通过 |
| 1d | 标准 API 实现（E23～E28）+ 流程校验 + 编辑器接口（E29、E30） | 实现与编辑器测试、纯 Java 图测试通过 |
| 1e | 并发测试；对照写接口示例第 11 节自查；更新台账 | 全部 `auto` 通过；`npm --prefix requirements-site test` 无新增失败 |

验证命令：Platform `./mvnw -pl platform-support-test -am test`（JDK 25，依赖本地 MySQL 13306、Redis 16379）。只有实际运行通过才记为通过。

## 8. 风险

- **工作区共享：** 其他会话也在改 Platform 或测试模块时，`init/ddl.sql`、`application-mock.yml`、`PersistenceConfiguration` 是共享文件，修改前通知并按段落补丁。
- **测试认证接入：** 让管理 Controller 在测试中加载可能影响其他测试的装配，开启方式要限定在本模块的 Controller，失败则回报，不改动其他功能。
- **流程校验复杂度：** 必经节点计算需要支配树；用纯 Java 测试覆盖菱形、多出口、汇合等结构。

## 9. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 代码放在哪个模块 | **新建业务模块 `platform-support-integration`**：有完整业务能力、6 张自有表和第 3 步的执行引擎，与身份权限（access-control）职责不同 |
| D2 | API 接口怎样拆 | 按对象拆成 6 个 `*ManagementApi` / `ExternalApiEditorApi`，沿用 `ApiScopeManagementApi` 写法；[管理接口](./admin-api.md)原写的 Query/Command 两个接口名随之调整，路径与字段不变 |
| D3 | Aviator 表达式何时编译校验 | 第 1 步只校验路径引用和长度；语法编译与白名单剖面放到第 3 步，届时申请引入 Aviator 并更新技术栈 |
| D4 | 测试中如何通过权限复核 | 测试中加载外部 API 管理 Controller，用 Mocker 插入真实的操作员、会话、角色与权限行，走真实权限服务；不 Mock 权限服务 |
| D5 | 本地数据库 | 授权在本地 `mor_platform` 执行 `integration-create.sql`（只建 6 张表），并更新测试 `init/ddl.sql`；不执行 `DROP`，不碰共享或线上环境 |
| D6 | 授权改代码 | 授权修改 `platform-support-service` 下的 api、web、test 模块、新建 integration 模块，以及根 `pom.xml` 的模块列表 |
