# 外部 API 配置：六表数据设计

本文是逻辑结构与字段稿，属于[现行需求](./requirement.md)的设计评审材料；物理类型与索引见[物理设计](./physical-design.md)（已确认）。**不是已生成或已执行的正式物理模型**：未进入 Platform PDManer，没有 DDL/DML，不更新正式模型统计。物理类型、索引与 Owner 模块见[待决事项](./technique.md#6-待决事项)。

## 1. 表与关系

| 表 | 职责 |
|---|---|
| `t_standard_api` | 标准 API 编码、名称和用途；输入输出字段由 `t_api_field` 维护 |
| `t_api_field` | 标准 API 与渠道原子 API 的请求、响应字段定义 |
| `t_integration` | 渠道或其他外部接入方的基本信息 |
| `t_integration_api` | 渠道原子 API；具体执行配置保存在 `definition_json` |
| `t_integration_script` | 接入方内可复用的公共脚本、参数和执行阶段 |
| `t_integration_standard_api` | 渠道实现标准 API 的关系及 `flow_definition_json` |

```text
t_standard_api 1 ──< t_api_field (STANDARD_API_REQUEST / STANDARD_API_RESPONSE)
t_integration  1 ──< t_integration_api 1 ──< t_api_field (INTEGRATION_API_REQUEST / INTEGRATION_API_RESPONSE)
t_integration  1 ──< t_integration_script
t_integration  1 ──< t_integration_standard_api >── 1 t_standard_api
```

关系为逻辑关联，不建物理外键；引用完整性由 Platform 应用在保存时校验。

收敛决定：

- 标准 API 不使用 `input_schema_json`、`output_schema_json` 保存字段。
- 不设独立公共对象表与响应合同表，不保留 `response_contract_id` 现行关联。
- 流程使用 JSON，不新增节点表、连线表或节点字段映射表。
- `(integration_id, standard_api_id)` 唯一表示一份当前渠道实现。
- Payment 的 Channel/Account/MID/Capability 五表与本六表职责不同，不合并统计，不搬迁。

## 2. 其余五表的最小标识

以下只列逻辑关系所需的标识和已确认的 JSON 列，**不是完整字段稿**；名称、审计列、状态列和长度待物理设计。

| 表 | 已确认或必需的列 |
|---|---|
| `t_standard_api` | `standard_api_id`、标准 API 编码（如 `PAY`）、名称、用途说明 |
| `t_integration` | `integration_id`、接入方编码、名称 |
| `t_integration_api` | `integration_api_id`、`integration_id`、API 编码、`definition_json` |
| `t_integration_script` | 脚本 ID、`integration_id`、脚本编码、参数、执行阶段、源码 |
| `t_integration_standard_api` | 关系 ID、`integration_id`、`standard_api_id`、`flow_definition_json`；唯一 `(integration_id, standard_api_id)` |

## 3. 共用字段表 t_api_field

| 字段 | 说明 |
|---|---|
| `api_field_id` | 主键 |
| `api_id` | 所属 API；按 `direction` 指向标准 API 或渠道原子 API |
| `direction` | 方向，见下表 |
| `field_location` | `HEADER`、`BODY`、`QUERY`、`PATH` |
| `field_path` | 字段路径 |
| `field_name` | 显示名称 |
| `data_type` | 数据类型 |
| `required_flag` | 是否必填 |
| `sort_no` | 排序 |
| `description` | 业务说明 |
| `create_time`、`update_time` | 审计时间 |

| `direction` | 含义 | `api_id` 所属 |
|---|---|---|
| `STANDARD_API_REQUEST` | 标准 API 请求 | `t_standard_api` |
| `STANDARD_API_RESPONSE` | 标准 API 响应 | `t_standard_api` |
| `INTEGRATION_API_REQUEST` | 渠道 API 请求 | `t_integration_api` |
| `INTEGRATION_API_RESPONSE` | 渠道 API 响应 | `t_integration_api` |

### 3.1 位置

- 请求允许 `HEADER`、`BODY`、`QUERY`、`PATH`；响应只允许 `HEADER`、`BODY`。
- 标准 API 当前的 `context` 与 `outputs` 字段使用 `BODY`。
- HEADER/QUERY/PATH 的路径即参数名，不加位置前缀；HEADER 名称按大小写不敏感判重。

### 3.2 路径与数组

- 不使用 `parent_field_id`。BODY 字段用完整路径表达层级，例如 `paymentContext.amount.currency`。
- 数组用 `[]` 表示元素定义：`items[].sku`、`tags[]`、`orders[].items[].sku`；不用固定下标定义结构。
- 对象或数组容器可以定义自身类型与必填性。数组必填不等于非空；子字段必填只在父对象存在时生效。

示例（标准 API `PAY` 请求）：

| field_location | field_path | data_type | required_flag |
|---|---|---|---|
| BODY | `paymentContext` | OBJECT | 1 |
| BODY | `paymentContext.amount` | OBJECT | 1 |
| BODY | `paymentContext.amount.value` | STRING | 1 |
| BODY | `paymentContext.amount.currency` | STRING | 1 |
| BODY | `paymentContext.items` | ARRAY | 0 |
| BODY | `paymentContext.items[].sku` | STRING | 1 |

`items` 不传时不校验 `items[].sku`；传入时每个元素都要有 `sku`。

### 3.3 唯一性与职责

- 唯一范围：`api_id + direction + field_location + field_path`。多态 `api_id` 引用校验、路径规范化与索引物理细节后续补齐。
- 字段表只描述结构；字段从哪里取值、如何加工，由原子 API `definition_json` 与流程节点取值映射负责。
