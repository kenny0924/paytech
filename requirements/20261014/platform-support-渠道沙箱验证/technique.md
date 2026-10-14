# 渠道沙箱验证技术方案

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14，S1～S8 按建议）；决定见 [2026-10-14 沙箱验证决定](../../../agent-docs/decisions/2026-10-14-platform-integration-sandbox-verification.md)
- 需求：[requirement.md](./requirement.md)
- 已读：[外部 API 六表物理设计](../../20260914/platform-support-外部API配置与执行/physical-design.md)、[管理接口](../../20260914/platform-support-外部API配置与执行/admin-api.md)第 1～2 节、[内部执行接口](../../20260914/platform-support-外部API配置与执行/internal-api.md)、[流程结构](../../20260914/platform-support-外部API配置与执行/flow-definition-contract.md)、[原子 API 结构](../../20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md)、[账号材料交接](../../20260914/platform-support-外部API配置与执行/material-handoff.md)、[数据库设计规范](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)第 8 节、[补偿任务规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)第 1～2 节、[公共写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)第 9～11 节，以及执行引擎源码（`ExecuteStandardApiProcessor`、`InitializerRunner`、`FlowEngine`、`AtomicApiExecutor`）。

## 1. 现状差异

| 现状 | 本需求需要 |
|---|---|
| 执行只经内部接口 `POST /standard-apis/v1/{code}/execute`，调用方 Payment 传入账号身份与 Vault Token | Operations 发起；账号来自 Platform 保存的验证账号 |
| 不持久化报文、运行上下文和节点轨迹（2026-10-13 决定） | 验证运行保存逐节点轨迹与脱敏报文 |
| `CHANNEL_ACCOUNT_CONFIG` 只从 Vault 读取 | 验证运行从验证账号读取，身份核对规则不变 |
| 出站地址没有环境属性 | 出站地址标记是否沙箱，验证只能打沙箱 |
| 没有异步执行入口 | 多步骤运行可能超过 Operations 同步请求时限，需要异步执行 |

## 2. 总体设计

```text
Operations 页面
  → Gateway → BFF（简单转发）→ Platform 管理接口
      账号 / 用例：短事务保存（与现有外部 API 配置写入相同的锁与版本 CAS）
      运行：短事务插入运行记录（RUNNING）+ compensation_event → 返回 runCode
            ↓ 提交后唤醒（XXL-Job 兜底）
      VerifyRunHandler（事务外）
        逐步：渲染输入模板 → 沙箱检查 → 与正式执行相同的快照加载 / 运行前校验 / 输入校验 /
              初始化（账号来自验证账号）/ 流程引擎 / 原子 API 执行，同时记录轨迹
              → 核对期望
        → 短事务写回步骤结果与运行结论
  ← 页面轮询运行详情
```

**执行路径与正式一致：** 把 `ExecuteStandardApiProcessor` 的执行主体（第 1～6 步）提取为 `StandardApiExecutionService`，正式接口与验证运行共用。验证专有的两点通过执行上下文传入，正式调用不传：

- **账号来源：** 上下文带验证账号时，`CHANNEL_ACCOUNT_CONFIG` 改读验证账号，忽略 `vaultToken` 参数，其余（身份核对、parameters 为字符串、credentials 包装为受控句柄）不变。
- **轨迹记录：** 上下文带记录器时，初始化项、节点与出口事件写入记录器；正式调用不记录，内部接口响应不变。

## 3. 数据设计（Platform 库，PDManer 图组 `integration`）

沿用[六表物理设计](../../20260914/platform-support-外部API配置与执行/physical-design.md)第 1 节通用约定（Snowflake 主键、编码列 ascii_bin、`version`/`created_by`/`updated_by`、`DATETIME(6)` UTC、无 FK/CHECK）。新增 4 张表，修改 1 列的 JSON 结构。

### 3.1 t_integration_verify_account（验证账号）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `verify_account_id` | BIGINT | 否 | 主键 |
| `integration_id` | BIGINT | 否 | 所属接入方 |
| `account_code` | VARCHAR(64) ascii_bin | 否 | 接入方内编码 |
| `account_name` | VARCHAR(128) | 否 | 名称 |
| `channel_code` / `channel_account_id` / `entity_no` | VARCHAR(64) | 否 | 账号身份，供流程初始化核对，与正式账号核对规则相同 |
| `parameters` | JSON | 否 | 普通参数，字符串键值，最多 64 项、每值 8192 字符 |
| `credentials` | JSON | 否 | 凭据，字符串键值；**用户决定按非敏感沙箱材料明文保存**；执行时仍包装为受控句柄 |
| `version` / `created_by` / `updated_by` / 时间 | — | 否 | 通用约定 |

`uk_verify_account_code (integration_id, account_code)`。被用例引用时不能删除。

### 3.2 t_integration_verify_case（验证用例）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `verify_case_id` | BIGINT | 否 | 主键 |
| `integration_id` | BIGINT | 否 | 所属接入方 |
| `case_code` / `case_name` / `description` | — | — | 编码、名称、说明 |
| `verify_account_id` | BIGINT | 否 | 使用的验证账号 |
| `steps_json` | JSON | 否 | 有序步骤（第 4 节），1～10 步，最多 65,536 字节 |
| `version` / `created_by` / `updated_by` / 时间 | — | 否 | 通用约定 |

`uk_verify_case_code (integration_id, case_code)`；`idx_verify_case_account (verify_account_id)`。

### 3.3 t_integration_verify_run（验证运行）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `verify_run_id` | BIGINT | 否 | 主键；对外以 `run_code`（同值十进制字符串）展示 |
| `verify_case_id` / `integration_id` | BIGINT | 否 | 用例与接入方 |
| `run_status` | VARCHAR(16) ascii_bin | 否 | 允许值：RUNNING、PASSED、FAILED、INTERRUPTED。FAILED 为期望不满足或步骤出错；INTERRUPTED 为执行进程中断后不再重放 |
| `case_snapshot_json` | JSON | 否 | 运行时的用例内容（步骤、期望）与账号编码、账号版本，不含凭据 |
| `config_versions_json` | JSON | 是 | 运行实际加载的配置版本：标准 API、接入方、原子 API、脚本、实现，按编码记录 `version`；用于判断“需要重新验证” |
| `endpoints_json` | JSON | 是 | 运行时出站地址（含沙箱标记），证明打的是沙箱 |
| `failure_summary` | VARCHAR(512) | 是 | 未通过时的首个原因 |
| `started_by` | BIGINT | 否 | 发起运行的 Operations 账号 |
| `start_time` / `finish_time` | DATETIME(6) | 否 / 是 | UTC |
| `create_time` / `update_time` | DATETIME(6) | 否 | UTC |

`idx_verify_run_case (verify_case_id, start_time)`；`idx_verify_run_integration (integration_id, start_time)`。运行记录不修改、不删除（用例删除时保留，页面按快照展示）。

### 3.4 t_integration_verify_run_step（运行步骤）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `verify_run_step_id` | BIGINT | 否 | 主键 |
| `verify_run_id` | BIGINT | 否 | 所属运行 |
| `step_no` | INT | 否 | 从 1 开始 |
| `step_key` | VARCHAR(64) ascii_bin | 否 | 用例内步骤 ID |
| `standard_api_code` | VARCHAR(64) ascii_bin | 否 | 标准 API |
| `step_status` | VARCHAR(16) ascii_bin | 否 | 允许值：PASSED、FAILED、SKIPPED（前一步失败后不再执行） |
| `resolved_input_json` | JSON | 是 | 模板渲染后的实际输入 |
| `result_json` | JSON | 是 | 实际结果：`{"kind":"EXECUTED","executeStatus","outputs"}` 或 `{"kind":"PROBLEM","status","code"}` |
| `assertions_json` | JSON | 是 | 每条期望的实际值与是否满足 |
| `trace_json` | JSON | 是 | 初始化项、节点、出口轨迹与脱敏报文（第 6 节），最多 4 MiB |
| `elapsed_ms` | INT | 是 | 耗时 |
| `create_time` | DATETIME(6) | 否 | UTC；步骤在运行结束时一次写入、不再修改，因此不设 `update_time`（阶段 2 定稿） |

`uk_verify_run_step (verify_run_id, step_no)`。

### 3.5 t_integration.endpoints 增加沙箱标记

每项增加可选 `sandbox`（布尔，缺省 false）：`[{"endpointCode":"PAYERMAX_GATEWAY","baseUrl":"https://pay-gate-uat.payermax.com/...","sandbox":true}]`。结构不变，无 DDL；校验器与页面同步。

### 3.6 异步任务

复用 Platform 现有 `t_compensation_event`：事件类型 `INTEGRATION_VERIFY_RUN`，`event_key` 为运行 ID。**阶段 3 定稿：** 补偿策略为最多执行 2 次、超时 110 秒（小于默认 2 分钟 Lease）；第 1 次真正执行，第 2 次只会在前一次中断（进程退出、超时）后出现，此时只把运行记为 INTERRUPTED、不向沙箱重放（补偿规范第 2 节第 5、8 条）。因此单次运行总时长应在 110 秒内，超出即中断。

**部署前提：** Platform 生产配置中 `compensation.enabled` 目前随文件入驻开关默认关闭；未启用时发起运行返回 503（“沙箱验证运行需要启用本地补偿任务”）。本需求不修改生产配置，启用时机随部署另行决定；本机 mock 配置已启用。

## 4. 用例步骤结构（steps_json）

```json
[
  {"id": "pay", "standardApiCode": "PAY",
   "input": {
     "channelAccount": "${account.identity}",
     "payment": {"attemptId": "VR${run.code}", "amount": {"value": "10.00", "currency": "USD"},
                 "subject": "Sandbox verification", "payerReference": "verify-payer",
                 "returnUrl": "https://example.com/return", "country": "US"}},
   "expect": {
     "executeStatus": "SUCCESS",
     "outputs": [
       {"path": "paymentStatus", "match": "EQUALS", "value": "PENDING"},
       {"path": "nextAction.redirectUrl", "match": "REGEX", "value": "^https://"},
       {"path": "channelTransactionRef", "match": "EXISTS"}]}},
  {"id": "query", "standardApiCode": "PAY_QUERY",
   "input": {"channelAccount": "${account.identity}",
             "payment": {"attemptId": "${steps.pay.input.payment.attemptId}"}},
   "expect": {"executeStatus": "SUCCESS",
              "outputs": [{"path": "paymentStatus", "match": "EQUALS", "value": "PENDING"}]}}
]
```

- **模板变量：** 只出现在字符串值中。整串为单个变量时保留原类型（如 `${account.identity}` 是对象），嵌在文字中时按字符串拼接。可用：`run.code`、`run.time`（UTC ISO-8601）、`random.digits(n)`（n ≤ 18）、`account.identity`（`{channelCode, channelAccountId, entityNo, accountVaultToken}`，Token 为占位值，验证时不读取）、`steps.<前序步骤>.input.*`、`steps.<前序步骤>.outputs.*`。不支持表达式和函数，渲染失败该步 FAILED。
- **期望：** `executeStatus`（SUCCESS/FAIL/ERROR），或 `problem: {"status": 422, "code": "VALIDATION_ERROR"}`（负向用例）；`outputs[]` 的 `match` 为 `EQUALS`、`EXISTS`、`ABSENT`、`REGEX`、`ONE_OF`；可选 `returnId`（期望从哪个出口返回，取自轨迹）。
- **保存时校验：** 标准 API 存在且本接入方已实现；输入经模板渲染（变量用示例值替代）后满足标准 API 请求字段结构（输入引用前序步骤时、或期望 `problem` 的负向用例除外，后者本就有意提交不合法输入，阶段 3 定稿）；`outputs.path` 是标准 API 响应字段；`steps.*` 只引用前序步骤；正则可编译且有长度上限。
- 步骤顺序执行；任一步失败，后续步骤记为 SKIPPED。

## 5. 管理接口（Platform 与 BFF 同路径，浏览器前缀 `/api/operations`）

沿用[管理接口第 1 节](../../20260914/platform-support-外部API配置与执行/admin-api.md#1-调用链与通用约定)的约定：`reason`、`expectedVersion`、mutation 响应、分页、TRY_LOCK `lock:external-api-config` + 版本 CAS、编码作对外身份。

| ID | Method / Path（前缀 `/external-api-admin/v1/integrations/{integrationCode}`） | 说明 | 权限 |
|---|---|---|---|
| V01 | GET `/verify-accounts` | 账号列表 | READ |
| V02 | GET `/verify-accounts/{accountCode}` | 详情（含参数与凭据） | READ |
| V03 | POST `/verify-accounts` | 创建 | MANAGE |
| V04 | PUT `/verify-accounts/{accountCode}` | 修改 | MANAGE |
| V05 | DELETE `/verify-accounts/{accountCode}` | 删除（被用例引用时 400） | MANAGE |
| V06 | GET `/verify-cases` | 用例列表，含最近一次运行与验证状态 | READ |
| V07 | GET `/verify-cases/{caseCode}` | 详情 | READ |
| V08 | POST `/verify-cases` | 创建 | MANAGE |
| V09 | PUT `/verify-cases/{caseCode}` | 修改 | MANAGE |
| V10 | DELETE `/verify-cases/{caseCode}` | 删除（运行记录保留） | MANAGE |
| V11 | POST `/verify-cases/validate` | 只校验不保存 | MANAGE |
| V12 | POST `/verify-cases/{caseCode}/runs` | 发起运行，返回 `runCode` 与 RUNNING | MANAGE |
| V13 | GET `/verify-runs` | 运行列表（可按用例筛选），分页 | READ |
| V14 | GET `/verify-runs/{runCode}` | 运行详情：步骤、期望核对、轨迹 | READ |

**验证状态**（V06 每个用例、页面汇总到“接入方 × 标准 API”）：`NOT_RUN`、`RUNNING`、`PASSED`（最近一次完成的运行通过，且其后用例与引用配置版本未变）、`FAILED`、`STALE`（最近一次通过，但用例或配置已变）。由运行的 `config_versions_json` 与当前版本比较得出，不另存状态。

## 6. 轨迹与脱敏

`trace_json` 记录：

- 初始化项：ID、函数、成功或失败（不记账号内容）。
- 每个节点：节点 ID、原子 API、`executeStatus`、错误分类（如签名验证失败、超时、规则未命中），以及一次渠道交换：方法、URL、请求 Header、请求 Body 原文、响应状态、响应 Header、响应 Body 原文、耗时。
- 出边与出口：命中的连线、出口 ID。

脱敏规则：

1. 值来自受控句柄的请求 Header（如 Bearer、Basic）记为 `[REDACTED]`；名称为 `Authorization`、`Proxy-Authorization`、`Cookie`、`Set-Cookie` 的 Header 一律记为 `[REDACTED]`。
2. 签名类 Header（`sign`、`signature` 等）**保留**：签名不是凭据，且是复盘验签问题的证据。
3. 每段 Body 最多保留 64 KiB，超出截断并标记；非 UTF-8 内容记录长度与 Base64 前 4 KiB。
4. 凭据从不进入报文（受控句柄不能写入 Body），无需额外处理。

正式执行接口仍不持久化任何报文；以上只对验证运行生效（决定待记录，见第 9 节 S1）。

## 7. 沙箱限定

- 运行前检查：本次运行涉及的每个原子 API 的 `endpointCode` 对应地址都带 `sandbox: true`，否则该步 FAILED（原因“出站地址未标记沙箱”），不发请求。
- 验证账号只能被验证运行使用；正式执行接口仍只从 Vault 读取账号。
- 沙箱标记由有 MANAGE 权限的人员在接入方出站地址上维护；系统无法判断地址是否真为沙箱，依赖人工标记（风险见第 10 节）。

## 8. 页面（Operations）

接入方工作区新增“验证”页签，包含三块：

| 区域 | 内容 |
|---|---|
| 验证概览 | 按标准 API 汇总的验证状态：哪些实现有有效通过记录、哪些需重新验证 |
| 验证账号 | 列表；新建/编辑表单：身份三项、参数键值表、凭据键值表（多行文本） |
| 验证用例 | 列表（状态徽标、最近运行时间、“运行”按钮）；编辑页按步骤编排：选择标准 API、输入 JSON 编辑（带变量提示）、期望表格（路径、匹配方式、值）；“校验” |
| 运行记录 | 列表；详情：结论、配置版本、每步的实际输入、结果、期望核对（实际值对比）、节点轨迹与报文（可折叠，JSON 格式化） |

另在“基本信息”的出站地址编辑中增加“沙箱”开关。

## 9. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| S1 | 验证运行保存报文与轨迹 | 形成新决定：只对验证运行开例外，按第 6 节脱敏；正式执行接口不变 |
| S2 | 沙箱凭据明文存 Platform 库 | 按用户决定记录；附加防护：验证账号只用于验证运行，且只能打沙箱地址 |
| S3 | 异步执行 | 复用 `t_compensation_event`，最大执行 1 次，中断记 INTERRUPTED 不重放 |
| S4 | 执行主体提取为 `StandardApiExecutionService` | 正式接口行为与响应不变，用现有入口测试回归证明 |
| S5 | 期望匹配方式 | `EQUALS`、`EXISTS`、`ABSENT`、`REGEX`、`ONE_OF`，另有 `problem` 与 `returnId` |
| S6 | 权限 | 复用 `EXTERNAL_API_READ`（查看）与 `EXTERNAL_API_MANAGE`（维护与运行），不新增权限 |
| S7 | 运行记录保留 | 不删除、不修改；遵循项目“默认保留、不自动销毁”的 MVP 规则 |
| S8 | 签名类 Header 是否保留 | 保留（第 6 节第 2 条） |

## 10. 风险

- **沙箱标记依赖人工：** 把生产地址误标为沙箱，验证运行会对生产发起真实交易。页面在开启沙箱标记时二次确认，并在运行详情展示目标地址。
- **证据含沙箱报文：** 跳转地址、渠道流水号等会长期保存；均为沙箱数据。
- **公共 Body 日志：** 账号接口的请求与响应会进入 Gateway/BFF/Platform 的完整 Body 日志（W5 遗留）；按 S2 视为非敏感，接受。
- **渠道沙箱不稳定：** 失败可能来自沙箱本身；运行详情的报文用于区分。

## 11. 实施阶段（每阶段交付后等待确认）

| 阶段 | 内容 |
|---|---|
| 1 | 本设计与决定记录（S1、S2） |
| 2 | 数据库：PDManer 模型、增量建表 SQL、完整 Schema；DataObject、Mapper、Repository。SQL 本机执行需授权 |
| 3 | Platform：执行主体提取、验证账号来源与轨迹记录、模板与期望核对、异步 Handler、V01～V14、沙箱标记校验；`auto` 测试（渠道为测试 Mock HTTP） |
| 4 | Operations：BFF 转发、路由与权限注册 SQL、页面；前端测试与构建 |
| 5 | 本机端到端：页面录入 PayerMax UAT 账号与两个用例，运行并保存通过记录 |
