# platform-support：数据查询服务 · 技术方案

状态：**阶段 2—3 已确认**（2026-10-22，用户补充“连接不一定指定库，可以连整个实例”，其余无问题；见第 5.1 节）。v2 按用户回复修改：不做主机白名单（Q7），取消查询记录表（Q8 随之失效），Q9—Q13 按建议，页面原型已画（第 8 节）。需求见 [requirement.md](./requirement.md)（阶段 1 已于 2026-10-22 确认）。

> **规模总结（方案口径，不代表已实现或已执行）：**
>
> - 涉及工程 4 个：Platform Support、operations-console-bff、operations-console-web、Platform 注册 DML。另有两项清理：`paytech/data-service` 骨架，以及文档与本地环境中的相关引用。
> - 接口：Platform 新增 18 个，其中管理 17 个、执行 1 个。BFF 同路径转发，不另计。
> - 数据库：`mor_platform` 新增 3 张表，不改旧表。
> - 注册 DML：1 个块，块号在执行前按 live `mor_platform` 选定，需避开 127—130。块内包含权限 4 项、路由 18 条、菜单 2 个。
> - 页面：3 个视图，分别是数据源、查询模板列表、模板编辑器。
> - 新依赖：JSqlParser（SQL 只读校验）、CodeMirror 6（编辑器，Q9 已确认）。

已读规范：`DEVELOPMENT_CONVENTIONS.md`（全文）、`DATABASE_DESIGN_GUIDE.md`（第 5、9、12 节）、`TECHNOLOGY_STACK.md`（第 2、3、9 节）。已读现有实现：Platform 的币种维护链路（Processor、Controller、BFF、Web）、`InternalCallerResolver`、Vault Client 在 channel-connector 中的用法、ledger 币种快照的刷新方式，以及注册 DML 块 125 的格式。

## 1. 现状差异

| 需求项 | 当前行为及证据 | 目标 | 处理 |
|---|---|---|---|
| R1 归属 | 独立骨架 `paytech/data-service`，含 Doris 连接、CDC 白名单与校验。登记位置：`paytech/pom.xml:56`、`instance-manifest/.../services.yaml:21`、`arch-rules/.../ServiceCatalog.java:24`、`ModuleModelAutoTest.java:34,52`、`YamlEventCatalog.java:37`、`scripts/verify-local-environment.mjs:16,79,615-629`、`LOCAL_DEVELOPMENT_ENVIRONMENT.md:225,256,286,467`、`CURRENT_IMPLEMENTATION.md:32`、`local-environment/README.md:153-167,216`、`.env.example:56` | 并入 Platform，删除骨架 | 新增决定记录，删除骨架并清理以上引用（第 10 节） |
| 跨库读取 | `TECHNOLOGY_STACK.md` 第 9.1 节和 AGENTS 都禁止跨数据库查询；D25 / D35 规定查询走 CDC 进 Doris | 只读账号读已登记的数据源 | 由决定记录写明受控例外，同步修订第 9.1 节和 D25 / D35 |
| R2 数据源 | 无 | 动态增删，多实例秒级生效 | 新增表 `t_data_source`、连接池注册表与刷新任务 |
| R3 模板 | 无 | 保存即生效，保留版本 | 新增 2 张表 |
| R8 查询审计 | — | 取消（C14） | 不建表，只保留应用元数据日志与指标 |
| R4 语法 | 无。MyBatis 只用于本服务的 Mapper | 自研统一语法 | 纯 Java 解析器、语法树与渲染器（第 3 节） |
| R5 执行 | 无 | 内部执行接口 | 执行 Processor 与引擎 SPI（第 4 节） |
| 密码存储 | Platform delivery 模块已经依赖 `paytech-vault-client`（`WebhookSecretVault` 等）；channel-connector 用 `ProtectCommand`（`TokenMode.REFERENCE`）保存密码、用 `RevealQuery` 读取 | 数据源密码存 Vault | data-query 模块依赖 vault-client；Vault 策略新增数据类型与用途 |
| 调用方识别 | `InternalCallerResolver`（app `ext/rest/internalauth`）加 `*.http.callers` 白名单，已用于 Webhook 和 Notification | 只允许 `operations-console-bff` | 复用同一机制，配置 `data-query.http.callers` |
| 多实例刷新 | ledger 的 `CurrencySpecSnapshot` 加 `@Scheduled` 定时检查：整体替换快照，失败时保留旧快照 | 按数据源版本增量重建连接池 | 参照它的定时检查与失败保留做法，比较的是版本号（第 5 节） |
| SQL 解析 | 仓库中没有 JSqlParser 或 Druid | 渲染后只放行单条 SELECT | 统一 BOM 新增 JSqlParser，并登记到 `TECHNOLOGY_STACK.md` |
| 编辑器 | ops-web 没有 CodeMirror 或 Monaco | 语法高亮与错误定位 | 新增 CodeMirror 6（Q9） |
| R9 注册 | 已用到块 126；127—130 已被工作流、Webhook、channel-connector、文件存储登记 | 权限、路由、菜单 | 新块，执行前选定 |

| 系统 | 影响点 | 改动类型 |
|---|---|---|
| Platform Support | 新模块 `paytech-platform-support-data-query`；`core` 中的 Processor；`app` 中的 Controller 与刷新任务；`PersistenceConfiguration` 增加 Mapper 扫描 | 代码、PDManer、Schema DDL、注册 DML、配置 |
| Vault | 本地与 mor-test 的策略文件新增 `DATA_SOURCE_PASSWORD_V1` | 配置 |
| operations-console-bff | 18 条路径的 Controller、Client 与 Converter，简单转发 | 代码 |
| operations-console-web | “数据查询”菜单组及 3 个视图 | 代码 |
| Gateway | 只靠注册 DML 生效 | DML |
| local-environment | 测试用的被查库与只读账号（Q12）；Doris analytics Profile 保留 | 本地 SQL 与文档 |
| 工作流 | 无（`DATA_QUERY` 后续接入时调用第 4 节的执行 Processor） | 否 |

## 2. 模块与分层

- **新 Maven 模块 `paytech-platform-support-data-query`**，包根 `com.paytech.platform.support.dataquery`。依赖 `-api`、`paytech-common-core`、`paytech-mybatis-support`、`paytech-vault-client`、JSqlParser、MySQL Connector/J 和 HikariCP（后两个由 Spring Boot BOM 管理）。模块内容：

  | 包 | 内容 |
  |---|---|
  | `core/dal` | 3 张表的 DataObject、Mapper；XML 放在 `resources/mapper/dataquery/` |
  | `core/enums` | `DataSourceEngineType`（MYSQL、DORIS）、`DataSourceStatus`、`QueryTemplateStatus`等 |
  | `domainservice` | Repository，以及 Domain Service：`DataSourceService`、`QueryTemplateService` |
  | `template` | 纯 Java 的模板解析器、语法树、表达式求值、参数校验与渲染（第 3 节），不依赖 Spring |
  | `engine` | `QueryEngine` SPI、`JdbcQueryEngine`、方言 `MysqlDialect` 与 `DorisDialect`、`ReadOnlySqlGuard`（JSqlParser） |
  | `integration` | `DataSourcePoolRegistry`（Hikari 连接池注册表）、`DataSourcePasswordVault`（Protect / Reveal） |

- **Processor 放在 `-core` 的 `dataquery/service/processor`**，与币种维护一致：
  - 写操作组合了 access-control 的权限检查（`OperationsManagementPolicyService`）、data-query 的 Service，以及 `OperationsTransactionExecutor` 的审计。
  - data-query 模块因此不依赖 access-control。
  - 写 Processor 继承 `AbstractLockProcessor`，锁粒度为 `lock:data-query:source:{id}` 或 `lock:data-query:template:{id}`。
- **Controller 放在 `-app` 的 `ext/rest/dataquery`**，实现 `-api` 中 `com.paytech.platform.support.api.dataquery` 下的三个接口：`DataSourceManagementApi`、`QueryTemplateManagementApi`、`DataQueryExecutionApi`。
- **刷新任务放在 `-app` 的 `ext/job/DataSourcePoolRefreshScheduler`**。
- **事务公理：**
  - 对被查库的查询和 Vault 调用都属于外部调用，全部在 `mor_platform` 事务之外进行。
  - `mor_platform` 内的写入只有配置变更，使用短事务；执行查询不写 `mor_platform`。

## 3. 统一模板语法

### 3.1 文法

```
template   := node*
node       := text | value | fragment | ifBlock | eachBlock | joinBlock | comment
value      := "#{" path "}"
fragment   := "@{" name "}"
ifBlock    := "{{#if " expr "}}" node* ("{{#elseif " expr "}}" node*)* ("{{#else}}" node*)? "{{/if}}"
eachBlock  := "{{#each " path " as " name ("," name)? (" separator=" string)? "}}" node* "{{/each}}"
joinBlock  := "{{#join " string (" prefix=" string)? (" suffix=" string)? "}}" (part | ifBlock | eachBlock | ws)* "{{/join}}"
part       := "{{#part}}" node* "{{/part}}"
comment    := "{{!--" any "--}}"
path       := name ("." name)*          // 参数、循环变量、__ 系统参数
expr       := or ;  or := and ("||" and)* ;  and := unary ("&&" unary)*
unary      := "!" unary | cmp ;  cmp := primary (("=="|"!="|">"|">="|"<"|"<=") primary)?
primary    := path | string | number | "true" | "false" | "null" | "(" expr ")"
string     := "'" ... "'" 或 "\"" ... "\""   // 支持 \' \" \\ 转义
```

- `join` 的子节点只能是 `part`、`if`、`each` 和空白，直接写正文是语法错误。这样“哪些算一段”是明确的：输出为空白的段被丢弃，其余段用分隔符连接，全部为空时 prefix 和 suffix 也不输出。
- `\#{`、`\@{`、`\{{` 原样输出。
- 解析器报错带行号、列号和原因。

### 3.2 参数声明（`parameters_json`）

```json
[
  {"name":"clientId","type":"INTEGER","required":false},
  {"name":"statuses","type":"LIST","elementType":"ENUM","allowedValues":["PAID","REFUNDED"],"maxSize":20},
  {"name":"conditions","type":"LIST","elementType":"OBJECT",
   "fields":[{"name":"channel","type":"STRING","maxLength":32},{"name":"minAmount","type":"DECIMAL"}],"maxSize":10},
  {"name":"from","type":"DATETIME"},
  {"name":"sortBy","type":"FRAGMENT","required":true,"default":"NEWEST",
   "choices":{"NEWEST":"created_at DESC","OLDEST":"created_at ASC"}}
]
```

| 类型 | 调用方传入（JSON） | 约束项 | JDBC 绑定 |
|---|---|---|---|
| STRING | 字符串 | `maxLength`、`allowedValues` | `setString` |
| INTEGER | 字符串或整数，64 位范围 | `min`、`max` | `setLong` |
| DECIMAL | 十进制字符串 | `min`、`max` | `setBigDecimal` |
| BOOLEAN | 布尔 | — | `setBoolean` |
| DATE / DATETIME | ISO-8601 字符串 | `min`、`max` | `LocalDate` / `LocalDateTime` |
| ENUM | 字符串 | `allowedValues`（必填） | `setString` |
| FRAGMENT | 字符串，必须是 `choices` 的键 | `choices`（必填） | 不绑定，输出对应片段原文 |
| LIST | 数组 | `elementType`（不能是 FRAGMENT 或 LIST）、`maxSize` | 逐个元素绑定 |

- 未声明的参数直接拒绝。不传的参数取 `default`，没有默认值时为 null。必填参数缺失时报错。
- 校验失败复用 Foundation 公共的 `RequestBodyValidationException`：422 `VALIDATION_ERROR`，每个错误附 `errors[{field,message}]`，`field` 形如 `params.conditions[2].minAmount`（研发中按公共处理器口径调整，2026-10-22）。
- **系统参数**以 `__` 开头，由服务端注入，调用方传入即报错。本期提供两个：
  - `__operator.accountId`：操作人；
  - `__cursor.*`：游标分页上一页最后一行的游标列值。
- **片段原文**必须是纯文本，不能包含 `#{`、`@{`、`{{`、`;` 或注释符号，保存时校验。

### 3.3 渲染

- 渲染器遍历语法树，输出 `RenderedQuery(text, bindings)`。
- 正文片段原样拼接。值节点交给引擎的值绑定器：JDBC 输出 `?` 并追加绑定值；集合输出 `?, ?, ?`；空集合直接写进 `#{}` 时报错，提示用 `{{#if}}` 包住。
- 条件求值规则：
  - 只写路径时按“非空”判断：非 null、字符串非空白、集合非空、布尔为 true。
  - 比较运算的两边必须类型兼容，否则报错。
  - 不做隐式转换，不支持函数调用。
- 以后 ES / Mongo 的绑定器把值输出为转义后的 JSON 字面量，语法树和渲染器不变。

### 3.4 保存校验（B2）

1. 解析成功，标签成对，没有未知标签。
2. 表达式引用的路径都已声明，或者是当前作用域的循环变量，或者是已知系统参数；比较运算两边类型兼容。
3. 声明了但没有使用的参数给出警告，不阻止保存。
4. 片段原文合法。
5. **展开校验：** 列举分支组合，每个 if 的各个分支、每个集合的“空 / 一个元素”、每个片段的各个取值。
   - 组合数不超过 `data-query.limits.max-validation-combinations`（256）时全部列举；
   - 超过时改用覆盖集，保证每个分支至少出现一次，并提示“组合较多，已按覆盖集校验”；
   - 每个组合渲染后都要通过第 4.3 节的只读校验。
   - 这一步只是为了尽早发现问题，最终防线是每次执行前的校验。
6. 数据源处于 ACTIVE；约束不超过硬上限；正文不超过 64 KB。

## 4. 执行

### 4.1 引擎 SPI

```java
public interface QueryEngine {
    /** 本引擎支持的数据源类型 */
    Set<DataSourceEngineType> engineTypes();
    /** 值绑定器：语法树值节点的输出方式 */
    ValueBinder valueBinder();
    /** 渲染结果的只读校验与分页改写，不访问数据源；QuerySource 提供引擎类型与默认库 */
    PreparedQuery prepare(RenderedQuery rendered, QuerySource source, QueryPaging paging);
    /** 在数据源上执行，受超时、行数与响应大小限制（QueryBudget） */
    QueryResult execute(DataSourceHandle source, PreparedQuery query, QueryBudget budget);
    /** 保存校验时对展开组合做渲染后只读校验 */
    RenderedQueryCheck renderedQueryCheck(QuerySource source);
    /** 启用前检查账号权限与连通性 */
    ConnectionCheckResult check(DataSourceConnectionConfig config, String password);
}
```

首期只有 `JdbcQueryEngine` 一个实现，`MysqlDialect` 和 `DorisDialect` 负责方言差异：账号权限查询和只读会话设置。

### 4.2 执行流程（`ExecuteDataQueryProcessor`，无锁，使用 `AbstractProcessor`）

1. **调用方：** `InternalCallerResolver` 识别调用方，调用方必须在 `data-query.http.callers` 中（默认空，各环境配置 `operations-console-bff`）。同时校验操作人会话，并要求具备 `DATA_QUERY_EXECUTE` 权限。
2. **加载：** 按 `templateCode` 读取模板、当前版本和数据源（`mor_platform`，不开事务）。模板或数据源不是 ACTIVE 时返回 400。
3. **参数：** 按声明校验并转换参数，注入系统参数。
4. **渲染：** 由语法树生成 `RenderedQuery`。
5. **准备：** `prepare` 做只读校验（4.3）和分页改写（4.4）。
6. **执行：** 从注册表借用连接池执行。
7. **结果：** 转换值类型、按列做屏蔽、计算 `hasMore` 和 `nextCursor`。
8. **观测：** 不落库（C14）。只输出一条元数据日志，包含模板编码、版本、数据源、调用方、操作人、结果、行数、耗时，不含参数值与结果行；另记 Micrometer 指标 `data.query.execution{template,result}`。执行与试运行路径加入 `web.logging.metadata-only-path-prefixes`，避免公共 MVC 日志记录完整参数与结果。

试运行（`TrialQueryTemplateProcessor`）与执行共用第 3—8 步，区别有两点：内容来自请求中的草稿；权限为 `DATA_QUERY_TEMPLATE_MANAGE`。

### 4.3 只读校验（B5 第 3 层，`ReadOnlySqlGuard`）

对渲染后带 `?` 的 SQL 依次检查：

1. **注释：** 字符串字面量之外出现 `/*`、`*/`、`--` 或 `#` 时直接拒绝。这一条用来防止 MySQL 可执行注释 `/*! … */` 夹带语句；模板注释请用 `{{!-- --}}`。
2. **单条语句：** JSqlParser `parseStatements` 结果必须恰好 1 条，且为 `Select`，可以是 `PlainSelect`、`SetOperationList` 或带 `WITH` 的形式。
3. **禁止的子句：** 用访问器遍历整棵树，出现以下任一项都拒绝：
   - `INTO`（包括 `INTO OUTFILE`、`INTO DUMPFILE`、`INTO @var`）；
   - `FOR UPDATE`、`FOR SHARE`、`LOCK IN SHARE MODE`；
   - 引用 `mysql`、`sys`、`performance_schema`（Doris 为 `mysql`、`__internal_schema`）中的表；
   - 黑名单函数：`SLEEP`、`BENCHMARK`、`GET_LOCK`、`RELEASE_LOCK`、`RELEASE_ALL_LOCKS`、`IS_FREE_LOCK`、`IS_USED_LOCK`、`LOAD_FILE`、`MASTER_POS_WAIT`、`SOURCE_POS_WAIT`、`WAIT_FOR_EXECUTED_GTID_SET`、`NAME_CONST`。
4. **解析失败：** JSqlParser 无法解析的语句一律拒绝，个别 Doris 专有语法也包括在内。错误信息说明“解析器不支持该语法”。

其余各层：

- **账号层：** 见第 5.2 节。
- **连接层：** Hikari `readOnly=true`。MySQL 方言下驱动的 `readOnlyPropagatesToServer` 为默认开启，会话因此是 `TRANSACTION READ ONLY`。驱动参数强制设为 `allowMultiQueries=false`、`allowLoadLocalInfile=false`、`allowUrlInLocalInfile=false`、`autoDeserialize=false`，用户不能覆盖。

### 4.4 约束、分页与结果

- **硬上限**（`DataQueryLimitsProperties`，前缀 `data-query.limits`）：

  | 配置项 | 默认值 |
  |---|---|
  | `max-rows` | 10000 |
  | `max-timeout` | `PT8S`（Gateway 响应上限 10 秒、BFF 9 秒，研发中由 30 秒下调） |
  | `max-offset` | 100000 |
  | `max-response-bytes` | 10 MB |
  | `max-pool-size` | 10 |
  | `max-list-size` | 1000 |
  | `max-body-length` | 48 KB（Gateway Body 上限 65536 字节，研发中由 64 KB 下调） |
  | `max-validation-combinations` | 256 |

- **模板约束**（`constraints_json`）：`maxRows`、`timeoutSeconds`（整数秒）、`pagingMode`（NONE / OFFSET / CURSOR）、`maxPageSize`、`countAllowed`、`cursorColumns`。
- **分页改写：** 改写在 JSqlParser 语法树上完成，模板正文本身不能带顶层 `LIMIT`，保存时校验。
  - **NONE：** 在顶层追加 `LIMIT maxRows+1`。多出的一行只用来判断 `hasMore`，不返回。
  - **OFFSET：** 请求带 `page` 和 `size`，`size` 不超过 `maxPageSize`，偏移不超过 `max-offset`。顶层追加 `LIMIT size+1 OFFSET n`。
    - 请求 `withTotal=true` 且模板 `countAllowed` 为 true 时，另执行 `SELECT COUNT(*) FROM (<去掉顶层 ORDER BY 的原句>) t`，超时与主查询共用同一预算。
  - **CURSOR：** 模板必须声明 `cursorColumns`，并在正文中自行写出 `{{#if __cursor}} … #{__cursor.id} … {{/if}}` 条件和 `ORDER BY`。
    - 服务追加 `LIMIT size+1`，用最后一行的游标列值生成 `nextCursor`：Base64URL 编码的 JSON，带模板版本号。
    - 下一页请求带回 `cursor`，服务解码后注入 `__cursor`；版本号不一致时拒绝（Q10）。
- **执行控制：**
  - `Statement.setQueryTimeout(min(模板, 上限))`，超时时驱动发送 `KILL QUERY`，返回 `DATA_QUERY_TIMEOUT`。
  - `setMaxRows(limit+1)`，`setFetchSize` 取 500。
  - 累计响应字节超过上限时中止，返回 `DATA_QUERY_RESULT_TOO_LARGE`。
- **值转换：**

  | 数据库类型 | 返回 |
  |---|---|
  | 整数（含 BIGINT） | 字符串，遵守 64 位 ID 以字符串传输的约定 |
  | DECIMAL | 字符串 |
  | 浮点 | 字符串 |
  | 布尔 / `TINYINT(1)` | 布尔 |
  | DATE / DATETIME / TIMESTAMP | ISO-8601 字符串，不加时区，原样反映库中的值 |
  | BLOB / BINARY | 不支持，返回 `[binary]` |
  | 其他 | 字符串 |
  | NULL | null |

- **列声明**（`columns_json`，可选）：`[{"name":"email","label":"邮箱","mask":"KEEP_EDGES","keepStart":2,"keepEnd":4}]`。
  - `mask` 取值：`NONE`、`HIDE`（不返回该列）、`FULL`（全部掩码）、`KEEP_EDGES`（保留首尾若干位）。
  - 没有声明的列原样返回，标题使用列名。
- **错误码：** 只为前端需要区别处理的情况定义专有码：`DATA_QUERY_TIMEOUT`（提示缩小范围）、`DATA_QUERY_RESULT_TOO_LARGE`、`DATA_SOURCE_UNAVAILABLE`（503，连接失败）。其余错误一律返回 `BUSINESS_CHECK_FAILED` 或 `INVALID_REQUEST`，并附中文说明。

## 5. 数据源与连接池

### 5.1 配置项

- 连接参数按字段分别配置：`host`、`port`、`username`，以及可选的 `database_name`（默认库）和 `connection_params`。
- **整实例连接（2026-10-22 用户）：** 不填默认库时连接整个数据库实例，一个数据源可读该账号有权访问的所有库。模板必须写 `库.表`；保存校验发现未限定库名的表时报错，并指出行号。填写默认库时，未限定的表按默认库解析，也可以写 `库.表` 读同实例的其他库。
- **系统库：** 无论是否指定默认库，只读校验都拒绝引用 `mysql`、`sys`、`performance_schema` 中的表（防止整实例 `SELECT ON *.*` 读到账号密码哈希等系统数据）；`information_schema` 允许，用于查看元数据。Doris 同理拒绝 `mysql` 与 `__internal_schema`。不接受原始 JDBC URL，以免注入 `allowLoadLocalInfile` 这类参数。
- `connection_params` 只允许以下几项：`sslMode`、`connectTimeout`、`socketTimeout`、`connectionTimeZone`、`characterEncoding`。
- **密码：**
  - 新建或修改时，在事务外调用 `vaultClient.protect(ProtectCommand("DATA_SOURCE_PASSWORD_V1", REFERENCE, password, "DATA_SOURCE_CONFIGURATION", "data-source.{id}.{sha256}"))`，再用短事务保存 Token。
  - 建立连接池时调用 `reveal(token, "DATA_SOURCE_CONNECTION")`。
  - 密码不回显，也不进日志。接口日志为管理路径设置 `web.logging.metadata-only-path-prefixes`。
- **状态：** `INACTIVE → ACTIVE ⇄ INACTIVE → DELETED`。删除是软删除，编码不能复用；保留这一行，模板的历史版本才能显示原来的数据源。

### 5.2 连接检查（启用、修改 ACTIVE 数据源时必做；也可手动测试）

1. 建立单个连接（不用连接池），执行 `SELECT 1`。
2. 检查账号权限，任何一项不通过都返回具体原因：
   - **MySQL：** `SHOW GRANTS FOR CURRENT_USER()` 的权限必须属于 `{USAGE, SELECT, SHOW VIEW}`，授权范围可以是 `*.*`（整实例）或具体库；含 `ALL PRIVILEGES`、`WITH GRANT OPTION`、任何写或 DDL 权限、`FILE`、`SUPER`、`PROCESS`，或任何角色授权（`GRANT \`role\``）都不通过。角色授权无法逐项展开，所以一律不通过。
   - **Doris：** `SHOW GRANTS` 中只能有 `Select_priv` 和 `Usage_priv`，出现 `Load_priv`、`Alter_priv`、`Create_priv`、`Drop_priv`、`Admin_priv`、`Grant_priv` 都不通过。
3. 结果写入 `last_check_result`、`last_check_message`、`last_check_time`。

### 5.3 连接池注册表与多实例刷新

- **注册表：** `DataSourcePoolRegistry` 用 `ConcurrentHashMap<dataSourceId, PoolEntry(version, HikariDataSource, inFlight)>` 保存各数据源的连接池。
  - Hikari 参数：`minimumIdle=0`，在用时才建立连接；`maximumPoolSize`（每个数据源配置，不超过上限）、`connectionTimeout=3s`、`idleTimeout=10m`、`readOnly=true`。
- **刷新任务：** `DataSourcePoolRefreshScheduler` 使用 `@Scheduled(fixedDelayString="${data-query.datasource.refresh-interval:PT5S}")`，每次执行轻量查询 `SELECT data_source_id, version, status FROM t_data_source WHERE status <> 'DELETED'`，再与本地版本比较：
  - **新增或版本变化的 ACTIVE 数据源：** 读取 Vault 后新建连接池，原子替换旧池；旧池转入退役。
  - **变为 INACTIVE、DELETED 或已不存在的：** 从注册表移除，转入退役。
  - **退役池：** 不再借出连接，等进行中的查询（inFlight）归零，或等待 `max-timeout` 加 10 秒后再关闭。
  - **失败处理：** 某个数据源刷新失败（例如 Vault 不可用）时保留旧池，按数据源累计失败次数并输出指标，下一轮重试。
- 修改配置的实例在事务提交后立即触发一次本地刷新，其他实例最多等一个刷新周期（AC-01）。
- 这里不使用 MQ 广播：5 秒定时检查已经满足“秒级生效”，而且少一个可靠性环节。
- 被查库不可用不影响 Platform 的健康检查。

## 6. 数据设计（`mor_platform`，新增 3 张表）

通用规则：

- 主键为 Snowflake `BIGINT`；时间为 `DATETIME(6)` UTC。
- 不建 `CHECK` 或 `FOREIGN KEY`。
- 人员字段保存 `t_user_account.account_id`。
- JSON 列结构见第 3、4 节，大小有上限。

| 表 | 字段（类型简写） | 索引 |
|---|---|---|
| `t_data_source` 数据源 | `data_source_id` PK；`data_source_code` VC64；`data_source_name` VC128；`engine_type` VC32（MYSQL、DORIS）；`host` VC255；`port` INT；`database_name` VC128 NULL（默认库，为空表示连接整个实例）；`username` VC128；`password_token` VC255（Vault Token）；`connection_params_json` JSON NULL；`max_pool_size` INT；`status` VC32（INACTIVE、ACTIVE、DELETED）；`last_check_result` VC32 NULL（PASSED、FAILED）；`last_check_message` VC512 NULL；`last_check_time` NULL；`description` VC512 NULL；`version` INT；`created_by`、`updated_by` BIGINT；`create_time`、`update_time` | `uk_data_source_code(data_source_code)`；`idx_data_source_status(status, update_time)` |
| `t_query_template` 查询模板 | `template_id` PK；`template_code` VC64；`template_name` VC128；`description` VC512 NULL；`data_source_id`（当前版本的数据源，列表筛选与删除数据源时的引用检查用）；`status` VC32（ACTIVE、INACTIVE）；`current_version_no` INT；`version` INT；`created_by`、`updated_by`；`create_time`、`update_time` | `uk_query_template_code(template_code)`；`idx_query_template_source(data_source_id, status)`；`idx_query_template_status(status, update_time)` |
| `t_query_template_version` 模板版本 | `template_version_id` PK；`template_id`；`version_no` INT；`data_source_id`；`body` MEDIUMTEXT；`parameters_json` JSON；`columns_json` JSON NULL；`constraints_json` JSON；`restored_from_version_no` INT NULL；`change_note` VC512 NULL；`created_by`；`create_time`（不可变，没有 `update_time`） | `uk_query_template_ver(template_id, version_no)` |

**删除检验：**

- 去掉 `t_query_template_version`：“保存即生效”就没有回退和对比，执行结果里的版本号也无从解释。
- `t_query_template.data_source_id` 是当前版本数据源的冗余，删除数据源时的引用检查要靠它走索引。保存时它与版本在同一事务中写入。
- 不建连接检查历史表，只保留最后一次结果，因为没有历史查看需求。

**并发：**

- 模板与数据源的修改都带 `expectedVersion`，配合资源锁和 CAS，冲突时返回 409。
- 保存模板时在同一事务内完成三步：插入版本行（`uk` 防止重复版本号），更新 `current_version_no`，`version + 1`。

**SQL：**

- PDManer 新增图组 `data-query`。
- `platform-support-schema.sql` 追加 3 张表，并新增增量文件 `docs/database/sql/changes/2026-10-22-data-query.sql`。
- 回滚方式为删表，执行前确认表中没有需要保留的数据。

## 7. 接口

- 路径前缀：管理类 `/data-query-admin/v1`，执行类 `/data-query/v1`。
- 操作人通过 Header `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 传递。
- ID 一律为字符串；分页使用 `PagingRequestBody` / `PagingResponseBody`。
- 写接口带 `expectedVersion`，冲突返回 409。

| # | Method + Path | 权限 | 说明 |
|---|---|---|---|
| 1 | GET `/data-query-admin/v1/data-sources` | DATA_QUERY_READ | 分页；按状态、引擎、关键字筛选 |
| 2 | GET `/data-query-admin/v1/data-sources/{id}` | DATA_QUERY_READ | 详情（不含密码），附引用该数据源的模板数 |
| 3 | POST `/data-query-admin/v1/data-sources` | DATA_SOURCE_MANAGE | 新建，状态为 INACTIVE |
| 4 | PUT `/data-query-admin/v1/data-sources/{id}` | DATA_SOURCE_MANAGE | 修改；密码可以不传（不传则保留原密码）；ACTIVE 状态下修改先做连接检查 |
| 5 | POST `/data-query-admin/v1/data-source-connection-tests` | DATA_SOURCE_MANAGE | 测试草稿配置；带 `dataSourceId` 且不传密码时使用已保存的密码；结果回写最后一次检查 |
| 6 | POST `/data-query-admin/v1/data-sources/{id}/status` | DATA_SOURCE_MANAGE | 启用（先做连接检查）或停用 |
| 7 | DELETE `/data-query-admin/v1/data-sources/{id}?expectedVersion=` | DATA_SOURCE_MANAGE | 必须是 INACTIVE，且没有 ACTIVE 模板引用 |
| 8 | GET `/data-query-admin/v1/templates` | DATA_QUERY_READ | 分页；按数据源、状态、关键字筛选 |
| 9 | GET `/data-query-admin/v1/templates/{id}` | DATA_QUERY_READ | 模板及当前版本内容 |
| 10 | POST `/data-query-admin/v1/templates` | DATA_QUERY_TEMPLATE_MANAGE | 新建，保存为版本 1，状态 ACTIVE；返回警告 |
| 11 | PUT `/data-query-admin/v1/templates/{id}` | DATA_QUERY_TEMPLATE_MANAGE | 保存为新版本并立即生效；可带 `changeNote`；返回警告 |
| 12 | POST `/data-query-admin/v1/templates/{id}/status` | DATA_QUERY_TEMPLATE_MANAGE | 启用或停用 |
| 13 | GET `/data-query-admin/v1/templates/{id}/versions` | DATA_QUERY_READ | 版本列表 |
| 14 | GET `/data-query-admin/v1/templates/{id}/versions/{versionNo}` | DATA_QUERY_READ | 某个版本的内容，用于对比 |
| 15 | POST `/data-query-admin/v1/templates/{id}/restoration` | DATA_QUERY_TEMPLATE_MANAGE | 以 `versionNo` 的内容生成新版本，记录 `restored_from_version_no`；按当前规则重新校验 |
| 16 | POST `/data-query-admin/v1/template-validations` | DATA_QUERY_TEMPLATE_MANAGE | 编辑器实时校验，不保存：返回问题列表（行、列、级别、原因） |
| 17 | POST `/data-query-admin/v1/template-trials` | DATA_QUERY_TEMPLATE_MANAGE | 试运行草稿：参数与分页；返回结果和实际执行的 SQL（带 `?` 与绑定值） |
| 18 | POST `/data-query/v1/templates/{templateCode}/executions` | DATA_QUERY_EXECUTE | 执行；入参 `params`、`page`、`size`、`withTotal`、`cursor`；出参 `columns`、`rows`、`hasMore`、`total`、`nextCursor`、`versionNo`。额外要求调用方在 `data-query.http.callers` 中 |

**权限（4 项）：**

| 权限 | 用途 | 等级 |
|---|---|---|
| `DATA_QUERY_READ` | 查看数据源、模板 | LOW |
| `DATA_SOURCE_MANAGE` | 维护数据源 | HIGH |
| `DATA_QUERY_TEMPLATE_MANAGE` | 维护模板、试运行 | HIGH |
| `DATA_QUERY_EXECUTE` | 执行任意 ACTIVE 模板（Q11） | LOW |

- **路由：** 18 条，前缀 `/api/operations/data-query-admin/v1/...` 与 `/api/operations/data-query/v1/...`，每条绑定一项权限。
- **菜单：** 菜单组“数据查询”，下设“数据源”“查询模板”。
- 注册格式沿用块 125：权限为 ACTIVE；管理员授权为 REVOKED；菜单与路由为 DISABLED，由人工开启。
- **审计：** 所有写接口沿用 `OperationsTransactionExecutor` 的审计终态。

**BFF：** `DataQueryManagementController` 和 `DataQueryExecutionController` 同路径转发到 `PlatformDataQueryClient`，代理在 `PlatformClientConfiguration` 中注册。这一层没有聚合逻辑。

## 8. 页面（operations-console-web）

新增菜单组“数据查询”，包括两个页面：数据源、查询模板（含编辑器子路由）。

```
数据源        筛选：状态 / 引擎 / 关键字                                  [新建]
              表格：编码 | 名称 | 引擎 | 主机:端口/默认库（无则显示“整个实例”）| 状态 | 最后检查（通过/失败 + 时间）| 引用模板数 | 操作
  └ 编辑抽屉   基本信息 → 连接（主机、端口、默认库（可选）、账号、密码「不修改请留空」、参数）→ [测试连接]
              测试结果区：连通 ✓ / 权限 ✓（列出账号权限，违规项标红）
              启用 / 停用 / 删除：二次确认；删除时列出引用模板
查询模板      筛选：数据源 / 状态 / 关键字                                [新建]
              表格：编码 | 名称 | 数据源 | 当前版本 | 状态 | 更新人 | 更新时间
  └ 模板编辑器（子路由）
     左（60%）：正文编辑器（CodeMirror：SQL 与模板标签高亮、错误下划线与行号定位、
                标签自动补全：参数名 / 片段名 / #if #each #join）
     右上：参数（表格，可增删、拖动排序：名称 | 类型 | 必填 | 默认 | 约束 …；
           LIST(OBJECT) 展开字段；FRAGMENT 维护「值 → 片段」）
     右中：约束（最大行数、超时、分页方式、页大小上限、允许总数、游标列）
     右下：列声明（标题、屏蔽规则）
     底部：校验问题面板（实时，点击定位）| 试运行面板：按参数声明生成输入表单 →
           [试运行] → 结果表格 + 实际 SQL 与绑定值 + 耗时 / 行数
     顶部：保存（填变更说明）| 启用/停用 | 版本（抽屉：版本列表，任意两版对比，恢复）
```

- 页面沿用现有 Operations 配置页的模式：筛选行、表格、抽屉、二次确认、公共错误提示、分页组件。
- 编辑器输入时防抖 500 ms 调用接口 16。语法高亮由前端完成；校验以服务端结果为准，前端不重复实现解析器。
- 页面原型：`operations-console-web/docs/operations-console.pen`，画板说明见本节末尾。
- **页面原型（2026-10-22）**：`operations-console-web/docs/operations-console.pen` 自 y=83100 起两排，共 6 张 1920 宽画板，与工作流原型同一个外壳（侧栏新增“数据查询 / 数据源 / 查询模板”）：
  - 01 数据源列表：左侧表格（状态、最后检查、引用模板数），右侧选中项详情（连接、密码只显示“已保存（Vault）”、最后一次检查的三步结果、引用的模板）。
  - 02 编辑数据源｜检查未通过：启用中的数据源修改账号后重新测试，左侧表单（编码锁定、引擎分段、连接参数只允许 5 项），右侧逐步检查，`SHOW GRANTS` 中的 `INSERT`、`UPDATE` 标红，“保存”置灰，并说明原配置继续生效。
  - 03 查询模板列表：编码、数据源、参数摘要（* 必填、[] 集合、⟨片段⟩）、分页方式、当前版本、状态。
  - 04 模板编辑器｜正文与参数：CodeMirror 正文高亮（值 / 片段 / 标签 / 注释分色），第 8 行引用未声明参数时出现波浪线与快速修复浮层；底部问题面板（错误、警告、展开校验说明）；右侧“参数 / 约束 / 列”标签页，片段参数展开“取值 → 固定片段”，以及系统参数说明。
  - 05 模板编辑器｜试运行：按参数声明生成的输入表单（集合参数为标签输入、片段参数为下拉），结果摘要（行数、总数、耗时、上限），实际执行的 SQL 与绑定值，以及只读校验结果、结果表。
  - 06 模板编辑器｜版本对比与恢复：右侧抽屉，左边版本列表（勾选两个对比），右边正文逐行对比与参数 / 约束变化，底部“以 v2 恢复为 v4”，并提示编辑器中有未保存修改。

## 9. 研发与验证计划

| 顺序 | 内容 | 验证入口 |
|---|---|---|
| 1 | 决定记录；删除 data-service 骨架并清理引用；修订 `TECHNOLOGY_STACK.md` 第 3、9.1 节和 D25 / D35 | `arch-rules`、`instance-manifest`、`requirements-site` 测试，`node scripts/verify-local-environment.mjs` |
| 2 | BOM 新增 JSqlParser | 构建 |
| 3 | `template` 包：解析器、表达式、参数校验、渲染、展开校验 | `QueryTemplateEngineTest`（纯 Java 公开合同）：第 7 节全部语法、错误行列号、注入值不改变结构、空集合、组合覆盖 |
| 4 | `engine` 包：`ReadOnlySqlGuard`、分页改写、值转换 | `ReadOnlySqlGuardTest`（纯 Java）：AC-06 列出的全部负例、可执行注释、黑名单函数、系统库引用、整实例下未限定库名的表、UNION 和 WITH 的正例 |
| 5 | PDManer、Schema 与增量 SQL | 本地 `mor_platform` 执行（需授权），`SHOW CREATE TABLE` 对照模型 |
| 6 | 表、Repository、Service、注册表、刷新任务、Vault 适配 | 通过下面的 HTTP 入口测试覆盖 |
| 7 | core Processor、app Controller | HTTP 入口测试，见下 |
| 8 | BFF 转发 | 18 条路径的转发测试（DTO Mock） |
| 9 | Web | 组件测试、构建；浏览器按第 8 节核对 |
| 10 | 注册 DML | 注册数与断言测试 |

**HTTP 入口测试**：真实本地 MySQL、Redis；Vault 使用测试 HTTP 端点返回 DTO。

被查库使用本地 MySQL 的测试库 `mor_data_query_fixture`，配两个账号：只读账号 `data_query_fixture_reader`，以及一个带写权限的账号（用来验证拒绝启用）。缺少它们时测试立即失败并给出准备说明（Q12）。

- `DataSourceManagementApiTest`：
  - 新建、测试连接、启用；
  - 写权限账号、角色授权都被拒绝；
  - 修改后刷新生效，不重启（AC-01）；
  - 停用时进行中的查询正常结束；
  - 有引用时不能删除；
  - 版本冲突返回 409。
- `QueryTemplateManagementApiTest`：
  - 保存即生效并生成新版本、恢复、对比；
  - B2 的每一项拒绝都带行号；
  - 警告不阻止保存。
- `DataQueryExecutionApiTest`：
  - 三种分页方式与总数；
  - 行数上限与 `hasMore`；
  - 超时被取消（用 `SLEEP` 不行，它在黑名单里，改用 fixture 中的大表 `CROSS JOIN`）；
  - 响应大小上限；
  - 参数错误的字段路径；
  - 屏蔽；
  - 非登记调用方被拒绝。
  - 另外两项安全测试：参数值注入（`' OR 1=1 --` 作为值绑定，结果为空）、被查库数据在全部测试前后不变（AC-06）。
- `DorisDialectManualTest`（manual）：在本地 analytics Profile 上执行同一组模板。

## 10. R1 清理清单

- 删除 `paytech/data-service/`，从 `paytech/pom.xml:56` 中移除对应模块。
- 删除以下目录登记：
  - `instance-manifest` 的 `services.yaml:21`；
  - `ServiceCatalog.java:24`；
  - `ModuleModelAutoTest.java:34,52`；
  - `YamlEventCatalog.java:37`。
- `scripts/verify-local-environment.mjs`：删除 CDC 白名单与 Flink 管道一致性检查（:16、:79、:615-629），保留 Doris analytics 的基本检查。
- 文档：
  - `LOCAL_DEVELOPMENT_ENVIRONMENT.md`：端口表删除 18120 / 19120；analytics 段落改为“Doris 方言 manual 测试环境”。
  - `local-environment/README.md`：同上。
  - `CURRENT_IMPLEMENTATION.md:32`。
- 本地环境：
  - Doris 库 `mor_data_service` 与账号 `data_service_app` 保留，作为 manual 测试的被查库。
  - Flink 演示管道保留，作为“把数据放进 Doris”的示例，不再承担契约。
  - `local-environment/mysql/init` 中的 `cdc_reader` 不改。
- 决定记录 `agent-docs/decisions/2026-10-22-data-query-into-platform-support.md`：写入 C1、C7、C8；标注 D25 / D35 中被取代的部分；修订 `TECHNOLOGY_STACK.md` 第 9.1 节的跨库查询例外；同步 AGENTS 路由行。

## 11. 待确认问题

2026-10-22 用户答复：Q7 不需要、Q8 不保存、其余按建议；另外去掉查询记录表、要求画 Pencil 原型。Q7—Q13 全部关闭。

| 编号 | 问题 | 建议 |
|---|---|---|
| Q7 ✅ 不需要（2026-10-22 用户） | 数据源主机是否需要白名单？ | 不做白名单；能否连到哪个主机由各环境的网络与被查库账号控制 |
| Q8 ✅ 不保存（2026-10-22 用户） | 查询记录是否保存参数值？ | 用户进一步决定取消整张查询记录表（C14），本问题失效 |
| Q9 ✅ 按建议 | 模板编辑器引入 CodeMirror 6（`@codemirror/*`，约 150 KB gzip，仅模板编辑器页面懒加载） | 引入。普通文本框做不了高亮和错误定位，Monaco 又太重 |
| Q10 ✅ 按建议 | 游标分页由模板作者自己写 `{{#if __cursor}}` 条件，服务只追加 `LIMIT` 并生成游标（第 4.4 节） | 按此做。服务自动改写 WHERE 条件对 UNION、GROUP BY 等写法不可靠 |
| Q11 ✅ 按建议 | 模板不绑定权限码，所以有 `DATA_QUERY_EXECUTE` 的人能执行**任意** ACTIVE 模板 | 接受，这是 C11 的直接结果。执行权限只授予需要的角色，以后需要细分时再加模板分组 |
| Q12 ✅ 按建议 | 自动测试需要一个本地“被查库”和账号：`mor_data_query_fixture` 库、只读账号与写权限账号，写入 `local-environment/mysql/init` 和 `.env.example` | 同意。只在本地使用，不进入 mor-test；已有的本地 MySQL 需要人工执行一次初始化 SQL（届时单独授权） |
| Q13 ✅ 现在画（2026-10-22 用户） | 页面原型画在哪里、何时画？ | 画在 `operations-console-web/docs/operations-console.pen`，与工作流原型同一文件，放在新的纵向区域，不触碰已有画板 |
