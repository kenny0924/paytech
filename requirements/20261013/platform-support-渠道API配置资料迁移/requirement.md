# 渠道 API 配置资料迁移至 Platform

登记日期：2026-10-13  
Owner：Platform Support  
关联方：Payment、Operations Web/BFF、项目文档与需求站点  
状态：已确认并于 2026-10-13 执行资料迁移，结果见[交付记录](./delivery.md#5-迁移执行2026-10-13)。代码迁移与数据库变更不在范围内。

本文定义一次资料归属迁移及设计整理。原功能需求仍保留首次登记日期 2026-09-14；本目录记录本次迁移任务，不替代迁移后功能需求的长期维护入口。

- [资料盘点与迁移方案草案](./technique.md)
- [确认与交付记录](./delivery.md)
- [迁移后现行需求](../../20260914/platform-support-外部API配置与执行/requirement.md) · [原功能需求（历史）](../../20260914/platform-support-外部API配置与执行/legacy-requirement.md) · [原目录迁移说明](../../20260914/payment-渠道API逻辑定义/README.md)
- [已确认的 Platform 归属决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)
- [现有实现与迁移状态入口](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-integration-http-execution-20260921)

## 1. 背景与目标

通用外部 API 配置最初以 Payment 渠道接入为场景设计，资料集中在 `requirements/20260914/payment-渠道API逻辑定义/`。后续已确认配置管理与执行归 Platform，Payment 等业务系统通过内部 HTTP 调用。

之后的讨论进一步确定了标准 API、渠道原子 API、渠道标准 API 实现、共用字段表、运行上下文初始化和条件连线。现有文档尚未完整吸收这些决定，仍混有公共模型表、响应合同表、仅原子调用和旧 Payment SQL 等不同阶段的内容。

本需求目标：

1. 将通用 API 配置与执行资料归到 Platform 命名的需求目录。
2. 将已确认设计整理为一套可继续细化的现行文档，明确与前序方案的取代关系。
3. 保留原讨论、SQL 草稿、示例、截图和检查结果的历史证据与适用范围。
4. 更新文档入口与引用，使后续开发不会依据过时的 Payment 方案实施。

使用者是熟悉渠道文档的接入人员、Platform/Payment 开发者和后续维护本模块的协作者。完成后，读者应能从 Platform 入口找到当前表关系与执行结构，并区分待设计、已设计和已实现。

## 2. 迁移范围与归属

建议目标目录：`requirements/20260914/platform-support-外部API配置与执行/`。目录名称属于本次迁移方案评审项；首次登记日期保持不变。

| 资料或能力 | 目标归属 | 本次要求 |
|---|---|---|
| 标准 API、渠道原子 API、公共脚本、字段定义、渠道标准 API 实现 | Platform | 整理并迁移需求、设计、示例及相关资料 |
| 执行前依赖初始化、运行上下文、流程条件与字段映射 | Platform | 将已确认讨论写入现行设计 |
| 支付 Channel、Account、MID、能力、路由、冻结身份、交易与资金状态 | Payment | 保留原 Owner，仅更新指向通用执行设计的引用 |
| 账号/MID 查询接口及其源码、测试、模型、SQL | Payment | 不迁移，不改写已有实现 |
| 配置维护页面与页面聚合 | Operations Web/BFF | 更新设计资料的 Owner 引用；Pencil 源文件仍在 Operations Web |
| 旧 Payment 配置 DDL、旧管理注册 DML | 前序设计资料 | 保留来源与历史性质，不转换为 Platform 当前可执行 SQL |
| Vault、Card 与跨系统材料交接 | 各现有 Owner | 保留权限边界；交接合同继续单独细化 |

迁移的是通用配置设计资料及其现行入口。把资料放入 Platform 目录，不表示已经迁移数据库、建立 HTTP 接口或交付执行引擎。

## 3. 必须同步的已确认设计

本节是迁移时必须保留的对话决定清单。迁移后将其落实到对应主题文档，本迁移需求保留为验收依据，不形成另一份长期业务合同。

### 3.1 模块与调用关系

- Platform 中的标准 API 模块定义统一业务请求和响应，例如 `PAY`、`AUTHORIZE`；独立模块不等于新增部署服务。
- 渠道原子 API 负责单次外部协议操作、请求组装、脚本处理、响应解析及结果映射。
- 渠道与标准 API 的组合定义该渠道如何实现标准 API，同一个标准 API 可以由多个渠道分别实现。
- 标准 API 的流程在一次调用内组合原子 API，处理约定的异常兼容，例如有条件地查询原操作。（2026-10-13 用户确认：冲正属于危险操作，不在流程中编排。）
- Payment 等业务系统通过 Platform 内部 HTTP 入口调用，管理入口仍经过 Operations BFF。
- Platform 不选择 Payment MID，不接管支付资格、冻结路由、资金状态或调用结束后的业务恢复。

### 3.2 六张配置表

| 表 | 当前职责 |
|---|---|
| `t_standard_api` | 标准 API 编码、名称和用途；输入输出字段由共用字段表维护 |
| `t_api_field` | 标准 API 与渠道 API 的请求、响应字段定义 |
| `t_integration` | 渠道或其他外部接入方的基本信息 |
| `t_integration_api` | 渠道原子 API，具体执行配置仍保存在 `definition_json` |
| `t_integration_script` | 接入方内可复用的公共脚本、参数和执行阶段 |
| `t_integration_standard_api` | 渠道实现标准 API 的关系及 `flow_definition_json` |

迁移时应收敛已讨论的字段稿及关系，不把六表逻辑结构写成已经生成或执行的正式物理模型。需要明确：

- 标准 API 不再使用 `input_schema_json`、`output_schema_json` 保存字段定义。
- 移除独立公共对象表和响应合同表的当前目标，不保留 `response_contract_id` 的现行关联。
- 流程继续使用 JSON，不新增节点表、连线表或节点字段映射表。
- `(integration_id, standard_api_id)` 唯一表示一份当前渠道实现。
- 既有 Payment 的五张 Channel/Account/MID/Capability 表与上述六张配置表属于不同职责，不合并统计或搬迁。

### 3.3 共用字段表

`t_api_field` 当前字段稿应包括：`api_field_id`、`api_id`、`direction`、`field_location`、`field_path`、`field_name`、`data_type`、`required_flag`、`sort_no`、`description`、`create_time`、`update_time`。

`direction` 使用四个枚举：

| 枚举 | 含义 | `api_id` 所属对象 |
|---|---|---|
| `STANDARD_API_REQUEST` | 标准 API 请求 | 标准 API |
| `STANDARD_API_RESPONSE` | 标准 API 响应 | 标准 API |
| `INTEGRATION_API_REQUEST` | 渠道 API 请求 | 渠道原子 API |
| `INTEGRATION_API_RESPONSE` | 渠道 API 响应 | 渠道原子 API |

`field_location` 使用 `HEADER`、`BODY`、`QUERY`、`PATH`。请求允许这四类位置，响应允许 `HEADER`、`BODY`；标准 API 当前 `context` 和 `outputs` 字段使用 `BODY`。

- 不使用 `parent_field_id`。BODY 字段用完整路径表达层级，例如 `paymentContext.amount.currency`。
- 数组用 `[]` 表示元素定义，支持 `items[].sku`、`tags[]`、`orders[].items[].sku`；不以固定下标定义数组结构。
- 对象或数组容器可定义自身的类型与必填性。数组必填不等于数组非空，子字段必填在对应父对象存在时生效。
- HEADER/QUERY/PATH 的路径按参数名解释，不重复添加位置前缀；HEADER 名称按大小写不敏感规则判重。
- 字段唯一性范围为 `api_id + direction + field_location + field_path`；多态 API 引用校验、路径规范化及索引物理细节在后续数据设计中补齐。
- 字段定义说明结构；字段从哪里取值、如何加工，由取值映射配置负责。

### 3.4 初始化与运行上下文

依赖初始化作为渠道标准 API 实现的一部分，保存在 `flow_definition_json.initializers` 中。

1. 配置加载时确定依赖清单，校验注册函数、参数和上下文引用。
2. 每次调用开始时校验输入，按依赖顺序执行初始化，通过受控系统函数获取本次需要的外部资料。
3. 必需资料初始化失败时结束调用，不进入目标渠道操作。
4. 初始化结果仅存在于本次调用，不作为应用启动时的全局账号缓存，不覆盖调用方冻结的账号或交易身份。

| 区域 | 用途 | 示例路径 |
|---|---|---|
| `input` | 调用方业务数据，只读 | `input.paymentContext.amount.value` |
| `resources` | 初始化完成的外部资料，只读 | `resources.channelAccount.outletId` |
| `steps` | 执行器保存的本次节点结果 | `steps.createPayment.outputs.transactionRef` |

只能提前准备调用前可获得的数据。渠道执行后才产生的交易号等信息保存在 `steps`，供后续节点读取。账号数据通过所属系统的受控接口获取，不能跨库读取；具体参数、Caller/Purpose 与材料交接仍待单独定稿。

### 3.5 流程与条件连线

- `flow_definition_json` 包含初始化项、节点、连线、节点取值及最终返回映射；完整 JSON 结构另行细化。
- 连线包含 `from`、`to`、`condition`、`priority`、`default`，条件使用 Aviator 布尔表达式。
- 当前节点完成并写入 `steps` 后，按优先级从小到大判断出边，命中第一条为真的条件后流转；未命中时走唯一兜底分支。
- 条件只读上下文，不发起外部查询、不修改运行数据；表达式执行异常不能静默当作 false。
- 原子 API 仍只执行本 API 的单次目标操作。标准 API 可按显式配置调用多个原子节点；这不是底层 HTTP 客户端自动重试。
- query 节点执行成功不等于支付成功；分支可以同时判断 `executeStatus` 和业务 `outputs`。
- 查询只在符合具体渠道合同的条件下执行，不能把所有 `ERROR` 或超时统一当作支付失败；冲正等资金反向操作不在流程中编排（2026-10-13 用户确认）。

### 3.6 脚本、结果和持久化边界

- 取值保留上下文路径、Aviator 表达式、注册系统函数及函数结果表达式；不增加独立的格式转换选择器。
- 公共脚本处理组装、转换、判断及函数调用；复杂签名、加密等能力由代码注册函数提供。
- 请求脚本操作当前节点的 `http.requestHeaders`、`http.requestBody`；响应脚本操作 `http.responseHeaders`、`http.responseBody`，响应阶段不能改写已发送请求。原始响应验签和最终发送字节的约束保留。
- 返回沿用 `executeStatus: SUCCESS / FAIL / ERROR` 与业务 `outputs`；执行状态不自动等于支付资金状态。
- 不保存流程实例、节点执行记录、运行上下文、请求/响应报文，不为流程登记后台补偿任务。
- 本次调用结束或进程重启后不自动继续。前面曾讨论的持续恢复与两张运行表方案已被用户后续决定撤回，不进入当前设计。
- 不引入发布版本；修改对新调用生效，已开始调用使用一次加载的完整配置。

## 4. 资料整理要求

### 4.1 迁移目录及入口

- 原功能需求正文、协议附件、示例、历史校验工具及截图按清单迁移，禁止遗漏未跟踪或被忽略但确属本需求的文件。
- 原目录只保留迁移说明和新入口，避免留下两套可编辑的当前正文。
- 仓库内指向旧目录的有效引用逐项更新；旧文件级链接如无法保留，应在迁移清单中提供旧路径到新路径的对应关系。
- 相同相对深度可保留的链接仍须检查，不能以目录改名为由假定图片、脚本、锚点必然有效。

### 4.2 当前合同与历史资料

- 当前入口集中维护需求、技术说明、六表字段稿、流程与运行上下文、对外调用边界及待决项。
- 旧两表/三表/五表方案、公共对象与响应合同独立存储、原 Payment 管理 HTTP 草稿及旧 JSON 样例标注被取代的范围。
- 历史交付记录、截图和验证结果保留原日期、原适用范围，不能改写为新设计已经通过的证据。
- 旧 Payment DDL 与 Platform 管理注册 DML 只作历史资料；保留原语句与原目标含义，通过说明标注不可作为新结构的执行来源。
- 原 Pencil 源文件继续位于 [Operations Web 设计文件](../../../operations-console-web/docs/operations-console.pen)。本期仅整理设计引用与差异说明，不重画页面。

### 4.3 引用与权威同步

实施时同步需求目录索引、文档导航、Platform 归属决定、项目上下文、应用架构、Platform 数据库入口以及实际引用方。规范正文只记录职责和路由，不复制多份完整字段清单。

需求批准并实施迁移时，按影响同步 `requirements-site/content/requirements.ts` 与 `requirements-site/content/global-use-case-sequences.mjs`；只同步与本次职责变化有关的条目，不因资料改名新增业务接口或改写无关交易链路。迁移完成与检查证据集中登记在 `CURRENT_IMPLEMENTATION.md`，不声称引擎、HTTP、模型或数据库已经实现。

## 5. 本期不包含

- Java、前端、BFF 或执行引擎研发，以及已实现 Payment 查询能力的迁移。
- 正式 HTTP 路径、DTO、权限注册、系统函数完整签名和跨系统凭据传输方案定稿。
- `definition_json`、`flow_definition_json` 的完整可执行 Schema 或真实 NI 协议适配实现。
- 正式 PDManer/Schema 变更、数据库数据迁移、DDL/DML 执行、Vault 策略调整。
- 流程状态落库、执行历史、自动重试、后台恢复、发布版本或新增部署服务。
- 部署、发布、提交、推送及对既有截图、历史测试结果的重生成。

## 6. 迁移验收标准

以下验收针对后续迁移执行；本次编写需求不表示这些事项已经完成。

| 编号 | 验收要求 | 验证方式 |
|---|---|---|
| MIG-01 | 通用设计资料位于 Platform 命名目录，原功能日期保留；新旧资料一一可追溯 | 文件清单与路径映射核对 |
| MIG-02 | 当前设计完整记录六张配置表及职责，无旧公共模型/响应合同表的并行现行目标 | 文档一致性检查 |
| MIG-03 | 共用字段表记录四种方向、四种位置、完整路径、数组规则及唯一性范围 | 字段示例人工核对 |
| MIG-04 | 初始化位于流程前，三个上下文区域和失败边界明确 | 账号初始化及依赖失败案例推演 |
| MIG-05 | 流程 JSON 保留；条件线按优先级单分支匹配，兜底及表达式异常行为明确 | 成功、明确失败、查询仍未确认的案例推演 |
| MIG-06 | 原子调用与标准流程的次数边界分开；无流程实例/节点运行表及重启续跑承诺 | 现行文档术语与排除项核对 |
| MIG-07 | Payment 账号/MID/能力/路由/业务状态和已有查询实现没有被搬迁或改写 | 变更清单检查 |
| MIG-08 | 历史 SQL、示例、截图与检查结果保留且标注适用范围，未充当新设计验证 | 迁移前后文件及内容核对 |
| MIG-09 | 原目录提供迁移说明，仓库有效引用更新；本地链接、图片与锚点可解析 | 引用扫描与链接检查 |
| MIG-10 | 权威入口、需求索引及受影响的需求/时序目录一致，保持单一当前合同 | 导航核对及相关站点检查 |
| MIG-11 | 原子 API JSON、完整流程 JSON、HTTP、材料交接与物理细节明确列为待决，不编造完成状态 | 待决清单检查 |
| MIG-12 | 无应用代码、正式模型、SQL 语句、环境及权限变更，无提交或部署 | 最终 diff 与操作记录检查 |

本期不交付新页面，视觉/交互功能验收不适用；仅检查既有设计文件和图片引用完整。

## 7. 后续设计事项

资料迁移完成后，继续单独细化：

1. 原子 API 的 `definition_json`，包括映射、脚本、传输、解析和状态规则。
2. `flow_definition_json` 完整结构与静态校验，包括入口、初始化依赖、节点身份、返回映射、循环/执行预算及字段缺失处理。
3. Payment → Platform 内部请求响应合同、调用授权及已选账号材料交接。
4. 字段路径转义、根数组、多值 Header、数值精度、条件必填、并发编辑与配置大小边界。
5. 六表的完整物理类型、索引与模块 Owner；后续获准后再同步 PDManer/SQL。

这些细节不阻碍先整理已经确认的设计，但不得在迁移时自行补成已确认合同。
