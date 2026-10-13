# 六表物理设计

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。P4 调用方白名单暂不做，P5 基础地址存入 Platform 数据库，其余按建议；记录见第 9 节。
- 覆盖待决项：[技术说明第 6 节](./technique.md#6-待决事项)第 7 项，以及第 6 项中与存储相关的部分（路径长度、配置大小、字段约束）。
- 依据：[数据库设计规范](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)第 8～11 节、Platform 现有 Schema 写法（如 `t_gateway_route`）、[六表逻辑设计](./data-design.md)、[流程结构](./flow-definition-contract.md)、[原子 API 结构](./integration-api-definition-contract.md)、[内部接口](./internal-api.md)。
- **落地状态（2026-10-13，用户要求“先做数据库设计”）：** 六表已进入 Platform PDManer 模型（图组 `integration`，模型现为 68 表），增量建表 SQL 见 [integration-create.sql](./db/platform-support-service/integration-create.sql)，与模型逐列核对一致。完整 Schema `platform-support-schema.sql` 已于用户授权后追加六表（现为 46 张已批准表）。**未执行任何 SQL**；无初始化数据。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| 主键 | 服务侧 Snowflake `BIGINT`，不自增 |
| 编码列 | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin`，格式 `[A-Z][A-Z0-9_]{0,63}`，大小写敏感 |
| 名称 / 说明 | 名称 `VARCHAR(128)`，说明 `VARCHAR(512) NULL` |
| JSON 配置 | MySQL `JSON NOT NULL`，序列化后最多 65,536 UTF-8 字节，由应用在保存前校验 |
| 并发 | 可编辑的配置行带 `version INT NOT NULL`，Operations 保存时按版本条件更新并校验影响行数 |
| 操作人 | `created_by`、`updated_by` 为 Operations 账号 ID（`BIGINT`），沿用 `t_gateway_route` 写法 |
| 时间 | `create_time`、`update_time`，`DATETIME(6)`，UTC |
| 约束 | 不建 `FOREIGN KEY`、`CHECK`；关联存在性、枚举、引用保护由 Platform 应用代码校验 |
| 字符集 | 表默认 `utf8mb4` / `utf8mb4_0900_ai_ci`，与现有 Platform 表一致 |

## 2. t_standard_api（标准 API）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `standard_api_id` | BIGINT | 否 | 主键 |
| `standard_api_code` | VARCHAR(64) ascii_bin | 否 | 标准 API 编码，如 `PAY` |
| `standard_api_name` | VARCHAR(128) | 否 | 名称 |
| `description` | VARCHAR(512) | 是 | 用途说明 |
| `version` | INT | 否 | 并发版本；字段定义随本行一起保存时递增 |
| `created_by` / `updated_by` | BIGINT | 否 | 操作账号 |
| `create_time` / `update_time` | DATETIME(6) | 否 | UTC |

- `uk_standard_api_code (standard_api_code)`

## 3. t_api_field（共用字段）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `api_field_id` | BIGINT | 否 | 主键 |
| `api_id` | BIGINT | 否 | 按 `direction` 指向 `standard_api_id` 或 `integration_api_id` |
| `direction` | VARCHAR(32) ascii_bin | 否 | 允许值：STANDARD_API_REQUEST、STANDARD_API_RESPONSE、INTEGRATION_API_REQUEST、INTEGRATION_API_RESPONSE |
| `field_location` | VARCHAR(16) ascii_bin | 否 | 允许值：HEADER、BODY、QUERY、PATH |
| `field_path` | VARCHAR(512) utf8mb4_bin | 否 | 完整路径，最多 16 段、每段 1～64 字符；数组用 `[]` |
| `field_name` | VARCHAR(128) | 否 | 显示名称 |
| `data_type` | VARCHAR(16) ascii_bin | 否 | 允许值：STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY |
| `required_flag` | TINYINT | 否 | 1 必填，0 非必填；响应字段的必填只在 SUCCESS 时校验 |
| `sort_no` | INT | 否 | 同一 API、方向内的显示顺序 |
| `description` | VARCHAR(512) | 是 | 业务说明 |
| `create_time` / `update_time` | DATETIME(6) | 否 | UTC |

- `uk_api_field_path (api_id, direction, field_location, field_path)`：同时承担“按 API 读取全部字段”的访问路径，不另建索引。
- HEADER 名称大小写不敏感判重由应用完成（二进制排序规则下唯一索引不会拦截 `Accept` 与 `accept`）。
- 字段**不单独**带 `version`、`created_by`、`updated_by`：字段属于所属 API 的配置，保存时整组替换并递增所属 API 的 `version`。
- 每个 API 每个方向最多 128 个字段。

## 4. t_integration（接入方）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `integration_id` | BIGINT | 否 | 主键 |
| `integration_code` | VARCHAR(64) ascii_bin | 否 | 接入方编码，如 `NI`；Payment 在内部接口请求中传入 |
| `integration_name` | VARCHAR(128) | 否 | 名称 |
| `description` | VARCHAR(512) | 是 | 说明 |
| `endpoints` | JSON | 否 | 本环境的出站地址列表 `[{"endpointCode":"NI_GATEWAY","baseUrl":"https://api-gateway.example.com"}]`，1～16 项（P5） |
| `version` / `created_by` / `updated_by` / 时间 | — | 否 | 同通用约定 |

- `uk_integration_code (integration_code)`
- `endpoints` 规则：`endpointCode` 在本接入方内唯一，格式同编码列；`baseUrl` 只允许 `https`（本地环境另行配置是否允许 `http`），必须有主机名，可带路径前缀，不允许用户信息、查询串和片段，最长 512 字符。原子 API 的 `transport.endpointCode` 必须在这里存在，保存时校验。
- 每个环境有独立的 Platform 数据库，地址按环境分别维护。实际出站仍受部署网络白名单限制；数据库地址不在白名单内时请求失败，不能用数据库配置绕过网络控制。
- 表注释写明：表示出站外部接入方，与 Client Integration Readiness（PLT-I13）无关。

## 5. t_integration_api（渠道原子 API）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `integration_api_id` | BIGINT | 否 | 主键 |
| `integration_id` | BIGINT | 否 | 所属接入方 |
| `api_code` | VARCHAR(64) ascii_bin | 否 | 接入方内的 API 编码 |
| `api_name` | VARCHAR(128) | 否 | 名称 |
| `description` | VARCHAR(512) | 是 | 说明 |
| `definition_json` | JSON | 否 | [原子 API 结构](./integration-api-definition-contract.md)，最多 65,536 字节 |
| `version` / `created_by` / `updated_by` / 时间 | — | 否 | 同通用约定 |

- `uk_integration_api_code (integration_id, api_code)`
- 每个接入方最多 200 个原子 API。

## 6. t_integration_script（公共脚本）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `integration_script_id` | BIGINT | 否 | 主键 |
| `integration_id` | BIGINT | 否 | 所属接入方 |
| `script_code` | VARCHAR(64) ascii_bin | 否 | 接入方内的脚本编码 |
| `script_name` | VARCHAR(128) | 否 | 名称 |
| `execution_stage` | VARCHAR(16) ascii_bin | 否 | 允许值：REQUEST、RESPONSE |
| `parameter_definition` | JSON | 否 | 参数声明数组 `[{name,dataType,required}]`，最多 32 项；无参数为 `[]` |
| `source_code` | TEXT | 否 | Aviator 脚本源码，最多 32 KiB UTF-8；引擎固定，不设引擎列 |
| `description` | VARCHAR(512) | 是 | 说明 |
| `version` / `created_by` / `updated_by` / 时间 | — | 否 | 同通用约定 |

- `uk_integration_script_code (integration_id, script_code)`
- 每个接入方最多 100 个脚本。被原子 API 引用的脚本不能删除，引用检查在应用内扫描同接入方的 `definition_json`。

## 7. t_integration_standard_api（渠道标准 API 实现）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `integration_standard_api_id` | BIGINT | 否 | 主键 |
| `integration_id` | BIGINT | 否 | 接入方 |
| `standard_api_id` | BIGINT | 否 | 标准 API |
| `flow_definition_json` | JSON | 否 | [流程结构](./flow-definition-contract.md)，最多 65,536 字节（已确认） |
| `version` / `created_by` / `updated_by` / 时间 | — | 否 | 同通用约定 |

- `uk_integration_standard_api (integration_id, standard_api_id)`：同时服务运行时按接入方 + 标准 API 加载。
- `idx_integ_std_api_standard (standard_api_id)`：删除或修改标准 API 时查找受影响的实现。

## 8. 运行时读取与跨表一致性

- 一次调用在一个短 `REPEATABLE_READ` 只读事务内依次读取：标准 API 及其字段 → 接入方 → 渠道实现 → 流程引用的原子 API 及其字段 → 这些原子 API 引用的脚本。事务结束后才执行初始化、Vault 与外部请求。
- 保存时的引用校验（流程引用原子 API、原子 API 引用脚本、字段覆盖）在 Operations 保存事务内读取相关行完成；被引用对象的删除或不兼容修改直接拒绝并指出引用方。
- 不缓存配置；保存后的新调用读取最新提交。

## 9. 评审点与确认记录

2026-10-13 用户确认：P4 调用方白名单暂时不做；P5 基础地址维护到 Platform 数据库；其余按建议。

| 编号 | 问题 | 结论 |
|---|---|---|
| P1 | 表名是否调整 | **保持六个已确认的表名。** `t_integration` 在表注释中写明与 Integration Readiness 无关；`t_api_field` 与现有 `t_api_credential`、`t_api_scope`（Client API 接入）同为 `api` 前缀，同样靠注释区分。若更看重一眼可辨，可改为 `t_integration_field`，代价是改动已确认的名称 |
| P2 | 是否加状态列（启用/停用） | **不加。** 本期没有“停用但保留”的需求；不再使用的实现直接删除，Payment 调用会得到保证未发送的 404。确有需要再加 |
| P3 | 并发与审计列 | 可编辑配置行带 `version`、`created_by`、`updated_by`；字段表随所属 API 整组保存，不单独带这三列 |
| P4 | 调用方白名单存放 | **用户决定：暂时不做。** 不建白名单列，内部接口不做调用方授权（[内部接口第 4 节](./internal-api.md#4-调用授权)） |
| P5 | 渠道基础地址（`endpointCode`）存放 | **用户决定：维护到 Platform 数据库。** 落在 `t_integration.endpoints` JSON 列，仍是六张表；实际出站继续受部署网络白名单限制 |
| P6 | 字段表是否增加长度、精度等约束列 | **本期不加。** 金额等精确值以字符串传递，不做四舍五入；报文总大小已有上限。确有渠道需要时再加 |
| P7 | 容量上限 | JSON 各 65,536 字节；脚本源码 32 KiB；每个接入方 200 个原子 API、100 个脚本；每个 API 每个方向 128 个字段；路径最多 16 段、每段 64 字符 |
