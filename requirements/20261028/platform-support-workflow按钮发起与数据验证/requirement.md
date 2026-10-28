# platform-support：workflow 按钮发起与数据验证 · 需求

> 状态：**阶段 1 评审稿（2026-10-28）**。来源：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)第 ④⑤⑦⑪ 条与 3.3 / 3.4 契约（REQ-05）；上游讨论 [design-discuss/client-activation](../../../design-discuss/client-activation/requirement-plan.md) 第 7.6 节 W1、W2。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- workflow 模块（2026-10-22 交付，10-23 UX 改版）已有：定义 / 版本 / 发布、实例、TASK / EXCLUSIVE / PARALLEL_SPLIT / PARALLEL_JOIN / WAITING 节点、边条件（`ConditionOperator`）、动作模板 FORM / FILE_UPLOAD / PAGE_TASK / MANUAL_CONFIRM、审批 ANY / ALL、转交、通知、回调。
- 两处限制挡住了"客户上线"流程：
  1. **发起**：`StartInstanceRequest` 按 `definitionId + versionNo` 发起，`businessKey` 只是可选字段，不防重；运营无法"按模板编码发起、同一 Client 只建一次"。
  2. **`DATA_QUERY` 只预留**：`TaskNodeRules` 拒绝发布引用 DATA_QUERY 模板的定义；`ActionConfigJsonReader` 只保留 `queryCode` 原文。节点完成只能靠人提交，流程无法"查到事实就自动推进"，也没有输出变量给网关分支。
- data-query 是 Platform 同部署的模块（`paytech-platform-support-data-query`，`QueryTemplateService`），可进程内调用。

## 2. 目标

1. 运营在业务页面按模板编码发起实例，按 `definitionCode + businessKey` 防重；重复发起返回已有实例。
2. `DATA_QUERY` 动作可发布、可运行：节点激活时查一次，定时重查，卡片可"立即检查"；`satisfied` 即完成动作并把 `outputs` 写入实例变量，供 EXCLUSIVE 网关分支。
3. 查询失败只算"尚未满足"，不报错、不阻塞。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 运营（ops-bff 代调） | 在入驻审核列表点「发起上线流程」 | 按 `CLIENT_GO_LIVE` 当前发布版本创建实例，`businessKey = CLIENT:{clientId}`；再点一次打开同一实例 |
| 流程设计者 | 在定义里给节点挂 DATA_QUERY 动作，引用 `GOLIVE_CASE_APPROVED` | 定义可发布；节点卡片显示"系统检查：案件 APPROVED" |
| 合规审核员 | 在审核工作台批准案件，不回流程点任何按钮 | 最迟一个重查周期后节点自动完成，`crr / mcc` 写入变量，流程按 `businessMode` 等变量分支 |
| 任何运营 | 卡片点"立即检查" | 立即执行查询并刷新卡片"现在：未满足：…" |
| 运维 | data-query 数据源不可用 | 节点保持未完成，卡片显示"检查失败（上次成功 …）"，告警计数 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | 发起接口支持 `definitionCode`（取当前 ACTIVE 发布版本）；`businessKey` 可选但给出时在 `definitionCode + businessKey + RUNNING` 上唯一；重复发起返回已有实例（200 + 既有 instanceId，不报错）；数据库唯一索引保证并发 |
| R2 | `DATA_QUERY` 动作模板可创建 / 发布：配置 = data-query 模板编码 + 参数映射（实例变量 → 查询参数）+ 输出映射（查询 outputs → 实例变量名）+ 说明文案键 |
| R3 | 运行：节点激活时执行一次；XXL-Job 定时对所有 PENDING 的 DATA_QUERY 动作重查（默认 5 分钟，可配置）；"立即检查"接口（有任务查看权限即可） |
| R4 | 结果：`satisfied = true` → 动作 COMPLETED，`outputs` 写入实例变量（同名覆盖），节点按既有规则推进；`false` → 保持 PENDING，记录 `missing[]` 与检查时间；查询异常 → 保持 PENDING，记录失败与最近成功时间，连续失败计数 |
| R5 | 实例变量读写：`outputs` 写入后，EXCLUSIVE 边条件可引用（现有 `ConditionOperator` 机制） |
| R6 | 任务卡 / 实例详情接口返回 DATA_QUERY 动作的"完成标准说明、最近检查时间、satisfied、missing[]、失败信息" |
| R7 | 通知：DATA_QUERY 自动完成后沿用现有节点推进通知；不新增触发类型 |
| R8 | 文档：workflow design-discuss 与数据库文档更新 `DATA_QUERY` 由预留改为可用 |

## 5. 不做项

- 不新增系统身份发起（发起人仍是登录运营账号，决定⑤）。
- 不做事件驱动的即时重查（Owner 事件 → workflow），本期只定时 + 手动。
- 不做队列 / 认领 / 四眼排除 / 跟进人 / 交接备注（REQ-06），不做 SLA / 回退 / 反查（REQ-09）。
- 不改 data-query 的数据源注册、只读 SQL 守卫与模板语法；查询模板由各 Owner 需求登记。
- 不做"客户上线"模板本身与 ops-web 发起按钮（REQ-07）。

## 6. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | 唯一性只约束 RUNNING 实例；COMPLETED / TERMINATED 后可用同一 `businessKey` 再次发起 |
| B2 | 按 `definitionCode` 发起时取唯一 ACTIVE 版本；无 ACTIVE 版本 → 422 `DEFINITION_NOT_PUBLISHED` |
| B3 | DATA_QUERY 动作没有人工提交入口；`MANDATORY` / `OPTIONAL` 语义沿用；取消节点时动作 CANCELLED |
| B4 | 查询返回必须符合契约形状（`satisfied / outputs / missing`）；形状不符视为失败 |
| B5 | `outputs` 写入变量名受定义声明限制（输出映射里声明过的才写），避免查询污染任意变量 |
| B6 | 定时重查按实例分片，单次查询超时默认 5 秒；同一动作并发重查只执行一次（按动作行 CAS） |
| B7 | 定义发布校验：DATA_QUERY 引用的模板编码必须在 data-query 存在且 ACTIVE；参数映射引用的变量必须在定义中声明或由上游 outputs 提供 |

## 7. 逐系统变动点

| 系统 / 模块 | 变动点 | 类型 |
|---|---|---|
| **Platform workflow** | `t_workflow_instance`：`definition_code` 冗余列（如无）+ 唯一索引 `(definition_code, business_key, running_slot)`（稀疏槽位）；PDManer + DDL | 表 |
| | `t_workflow_action_run`：最近检查时间、satisfied、missing JSON、失败计数、最近成功时间（阶段 3 定列） | 表 |
| | `StartInstanceRequest` 增 `definitionCode`（与 `definitionId + versionNo` 二选一）；`StartWorkflowInstanceProcessor` 防重与返回既有实例 | 接口 / 代码 |
| | `TaskNodeRules`、`ActionConfigJsonReader`、`WorkflowTemplateConfigs`：放开 DATA_QUERY，解析参数 / 输出映射，发布校验 B7 | 代码 |
| | 新 Processor：执行 DATA_QUERY（进程内调 data-query `QueryTemplateService`）、写结果、推进节点；"立即检查"写接口 | 代码 |
| | XXL-Job 定时重查 Handler（分片） | Job |
| | 查询 Processor / 响应装配：任务卡与实例详情返回检查信息 | 接口 |
| | auto 测试：防重并发、按编码发起、DATA_QUERY 满足 / 未满足 / 失败三态、outputs 进变量并驱动 EXCLUSIVE 分支、发布校验 | 测试 |
| **Platform data-query** | 提供进程内只读调用入口（如现有只有 HTTP 控制器则抽 Service）；模板执行返回原样 JSON，形状由模板保证 | 代码（小） |
| **operations-console-bff / web** | 任务卡显示"系统检查"块与"立即检查"按钮（最小改动；完整任务卡在 REQ-06 / 07）；WorkflowDefinitions 页允许选择 DATA_QUERY 模板并填参数 / 输出映射 | 页面 |
| **Platform Gateway / 注册** | "立即检查"、按编码发起（如为新 Route）的 Route 与权限注册 | 注册数据 |
| **文档** | workflow design-discuss、数据库文档、CURRENT_IMPLEMENTATION | 文档 |

## 8. 验收标准

功能：

| 编号 | 标准 |
|---|---|
| AC-01 | 按 `definitionCode + businessKey` 发起两次（含并发）只产生一个 RUNNING 实例，第二次返回同一 instanceId |
| AC-02 | 实例 COMPLETED 后同一 businessKey 可再次发起 |
| AC-03 | 无 ACTIVE 版本时发起返回 422 `DEFINITION_NOT_PUBLISHED` |
| AC-04 | 引用 DATA_QUERY 模板的定义可发布；引用不存在 / 非 ACTIVE 模板或未声明变量时发布被拒并指出原因 |
| AC-05 | 节点激活即执行查询；`satisfied = true` 时动作 COMPLETED、outputs 写入变量、节点推进 |
| AC-06 | `satisfied = false` 时动作保持 PENDING，任务卡返回 missing[] 与检查时间；定时重查后满足即推进 |
| AC-07 | 查询超时 / 异常 / 形状不符 → 动作保持 PENDING，记录失败与计数，不产生错误通知 |
| AC-08 | outputs 写入的变量可驱动 EXCLUSIVE 网关走对应分支 |
| AC-09 | "立即检查"立即执行并返回结果；并发只执行一次 |
| AC-10 | 未在输出映射声明的 outputs 键不写入变量 |

视觉 / 交互（最小）：

| 编号 | 标准 |
|---|---|
| AC-11 | 任务卡"系统检查"块显示完成标准说明、状态（满足 / 未满足 / 检查失败）、最近检查时间、missing 列表；"立即检查"按钮有加载态与错误反馈 |
| AC-12 | 定义编辑页可选择 DATA_QUERY 模板并配置参数 / 输出映射，发布失败原因可见；en / zh-CN 文案完整 |

## 9. 已确认事实、假设与待确认

已确认：决定④⑤⑦⑪；EXCLUSIVE 网关与边条件已有；data-query 为 Platform 同部署模块。

假设：A1 data-query 模板执行可从 workflow 进程内调用（阶段 2 核实其 Service 边界与读账号隔离）。

待确认：

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 定时重查默认 5 分钟、单次超时 5 秒是否可接受 | 可配置，按建议 |
| Q2 | "立即检查"权限：任务查看者即可，还是仅执行人 / 跟进人 | 任务查看者即可（只读查询，无副作用） |
| Q3 | 按 `definitionCode` 发起是否同时保留 `definitionId + versionNo`（现有 UI 用） | 保留，二选一 |

## 10. 阶段安排

默认八阶段。阶段 2 核实：data-query 进程内调用边界；`t_workflow_instance` 现有列与索引；实例变量存储形状；XXL-Job 在 Platform 的接入方式。SQL 应用、部署、提交分别授权。
