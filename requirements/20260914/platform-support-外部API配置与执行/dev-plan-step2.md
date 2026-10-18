# 研发计划 · 第 2 步：Operations BFF、前端页面与注册数据

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。用户对计划回复“可以”，F1～F7 按建议执行，含 F3 本机独立库建库与注册/启用 SQL、F6 修改 BFF/前端/Platform 能力登记的授权；不含提交、推送或其他环境的 SQL。
- 范围：Operations BFF 转发 30 个管理接口；Operations 前端两个菜单及其全部页面；Platform 组件能力登记；注册数据在本机执行与端到端验证。
- 不含：执行引擎、内部标准 API 接口、Vault、Aviator 编译（第 3 步）；Payment 改造（第 4 步）；任何共享或线上环境的 SQL、部署。
- 依据：[管理接口](./admin-api.md)（已确认，2026-10-13 修订错误码）、[页面方案](./page-design.md)（已确认，24 张画板）、[研发计划 · 第 1 步](./dev-plan-step1.md)（已实现并提交 `bcd38a5`）。

## 0. 已读规范与现状

已读：Operations BFF [README](../../../paytech/operations-console-bff/README.md)（分层、入站授权、错误转换、日志）、[应用架构](../../../agent-docs/APPLICATION_ARCHITECTURE.md)的 BFF 职责、[开发约定](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)，以及租户访问需求的[本机登记记录](../platform-support-租户访问与成员权限查询/db/local-application.md)。

代码样板：

| 层 | 样板 | 说明 |
|---|---|---|
| BFF 转发 | `TenantAccessManagementController` → `PlatformTenantAccessManagementClient` → `…ClientImpl` | 简单转发不建 Processor；Port 只声明 BFF 本地模型，Impl 显式转换 Platform DTO |
| BFF 代理 | `PlatformClientConfiguration` | 每个 Platform `@HttpExchange` 接口一个代理 Bean |
| 前端列表 + 编辑 | `pages/ApiScopes.tsx`、`configuration/useConfigurationForm.ts` | URL 查询参数切换视图；422 字段错误回填表单；按钮资源控制写操作 |
| 前端子路由 | `pages/TenantAccess.tsx`、`navigation/menu.ts` 的 `pageRoutePaths()` | 工作区类页面的多级路由 |
| 流程画布 | `risk/FlowCanvas.tsx`（`@xyflow/react` 12） | 只复用画布交互方式；Risk 画布很小（37 行），外部 API 流程编辑器需要独立实现 |

- **入口与职责：** BFF 只做协议适配与转发，权限、校验、保存、审计都在 Platform（第 1 步）。前端负责编辑体验，不重复服务端规则，所有校验以 E16/E22/E28 与保存结果为准。
- **事务与外部步骤：** BFF 无状态、无事务；本步骤没有新增业务写入。

## 1. 调用链

```text
浏览器 /api/operations/external-api-admin/v1/...
  → Gateway（路由权限、Idempotency-Key）
    → operations-console-bff /external-api-admin/v1/...（PLT-I02 当前授权 → 转发）
      → platform-support-service（重新鉴权、校验、保存）——第 1 步已实现
```

## 2. BFF（operations-console-bff）

| 内容 | 做法 |
|---|---|
| 代理 | `PlatformClientConfiguration` 新增 6 个 Bean：`StandardApiManagementApi`、`IntegrationManagementApi`、`IntegrationApiManagementApi`、`IntegrationScriptManagementApi`、`IntegrationImplementationManagementApi`、`ExternalApiEditorApi` |
| Port / Impl | 按对象 6 对：`PlatformStandardApiClient` 等；Impl 逐字段转换，`definition`、`flowDefinition` 作为 JSON 对象原样透传（BFF 不解释其结构） |
| 模型 | `service/model/ExternalApiModels`（或按对象拆分），类型名 `*RequestBody` / `*ResponseBody`，通过现有命名测试约束 |
| Controller | 按对象 6 个，路径与 Platform 一致 `/external-api-admin/v1/...`；只做绑定与 `@Valid`，直接委托 Port |
| 请求头 | 透传可信 Actor/Session 与 `X-Request-Id`；**不接收、不转发 `Idempotency-Key`**（管理接口 R7） |
| 错误 | 404/409/503 透传；超时 504、损坏响应 502 沿用公共客户端。**422 字段错误按 Platform 原样透传**（见 F2） |
| 日志 | `web.logging.metadata-only-path-prefixes` 增加 `/external-api-admin`，定义、流程与脚本源码不进普通日志 |
| 配置 | `application.yml` 与测试配置同步新增字段，不新增配置项以外的开关 |

## 3. Platform 补充（platform-support-access-control）

- `operations/console-resource-capabilities.json` 登记两个组件：`OPERATIONS_EXTERNAL_STANDARD_APIS`（`/external-api/standard-apis`，动作 `OPERATIONS_EXTERNAL_STANDARD_API_MANAGE`）与 `OPERATIONS_EXTERNAL_INTEGRATIONS`（`/external-api/integrations`，动作 `OPERATIONS_EXTERNAL_INTEGRATION_MANAGE`）。否则注册的菜单无法通过组件能力校验。
- 前端 `navigation/pageCapabilities.ts` 同步同一份登记。

## 4. 前端（operations-console-web）

新增目录 `src/externalApi/`，页面挂在 `ConsolePage` 的两个组件键下。

| 模块 | 内容 | 对应画板 |
|---|---|---|
| `externalApiApi.ts` + 解码 | 30 个接口的类型化请求与响应解码，沿用 `RequestClient` 与 `ApiError.fieldErrors` | — |
| 标准 API | 列表（筛选、分页、已实现接入方链接）、编辑/详情（基本信息、请求/响应字段表、字段弹窗、按路径缩进） | 01、02 |
| 接入方 | 列表；工作区四个页签：基本信息（出站地址表）、原子 API、公共脚本、标准 API 实现（含“新建实现”选择弹窗） | 03、04、14～16 |
| 原子 API 编辑 | 七个页签：接口、请求字段、响应字段、调用参数与固定值、脚本、状态规则、输出 | 05、06、10～13 |
| 脚本编辑 | 参数表、源码编辑框、按阶段的可用对象与注册函数（来自 E30） | 07 |
| 流程编辑 | 左侧初始化项、`@xyflow/react` 画布、右侧属性面板、取值/条件编辑器、底部校验结果与定位 | 08、09、23、24 |
| 通用状态 | 删除保护、并发冲突、空状态、加载失败、只读详情、未保存离开 | 17～22 |

要点：

- **路由：** 按页面方案第 2 节，工作区和编辑页使用子路由，列表筛选、分页、页签写入 URL。
- **流程模型：** 纯 TS 模块把 `flowDefinition` 转为画布节点/连线并转回，出口顺序以 priority 表达，“其他情况”固定最后；画布布局只在前端计算，不写入流程定义（见 F4）。
- **必经变量树：** 取值编辑器的 `steps.*` 只列出必经节点。前端用同样的支配计算给出提示，**最终以服务端校验为准**；两边不一致时以服务端问题列表展示（见 F5）。
- **问题定位：** 把 `location`（如 `flowDefinition.nodes[0].request.BODY:amount.path`、`definition.statusRules[1].condition`）解析为页签、行、节点或连线并高亮。第 1 步测试已记录两种定位形态（`STEP_NOT_ON_ALL_PATHS` 指向绑定本身，其余带 `.path`），解析器都要支持。
- **权限：** 写按钮由两个 BUTTON 资源控制；无管理权限时进入只读详情。
- **保存后立即生效：** 保存按钮旁常驻提示。

## 5. 注册数据与本机端到端

- 执行 [registration-dml.sql](./db/platform-support-service/registration-dml.sql)（344 行，全部初始停用）以及一份新写的本机启用 SQL（30 条路由、5 个资源、2 条角色授权改为启用并递增 `PLATFORM_ADMIN` 版本）和对应停用回滚 SQL。
- **目标库需要选择（见 F3）：** Platform 测试会整表清空并要求以下表为空：`t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route*`、`t_gateway_guard_rule`。若注册数据写进测试同用的 `mor_platform`，Platform `auto` 测试将拒绝运行，一旦运行还会删掉注册数据。
- 步骤沿用租户访问需求的本机做法：先备份，单连接事务执行，核对 7 表增量 2/2/5/30/111/14/180 后提交，出错回滚，记录执行回执。
- 端到端：本机启动 Platform、BFF、Gateway 与前端，用平台管理员走通“新建标准 API → 接入方 → 原子 API → 脚本 → 流程实现”，并验证只读人员看不到写按钮、版本冲突与删除保护提示。

## 6. 测试

| 位置 | 内容 |
|---|---|
| BFF `auto` | 参照 `TenantAccessBrowserAutoTest`：真实 BFF 入口 + Platform 模拟服务。每个接口验证先 PLT-I02 再转发、可信 Header 透传与 `Idempotency-Key` 不转发、授权拒绝零转发、404/409/422/503 透传、损坏响应 502；`definition` / `flowDefinition` 原样往返 |
| BFF 命名 | 既有 `OperationsHttpBodyNamingAutoTest` 自动覆盖新 DTO |
| 前端 `node --test` | 纯 TS：解码器、字段层级缩进、流程定义与画布互转（含 priority 排序与兜底固定最后）、必经节点计算（菱形、补查分支）、问题 `location` 定位解析、URL 状态 |
| 端到端 | 第 5 节的本机人工验收，记录步骤与结果；不写入自动测试 |

## 7. 实施顺序与验证

| 子步 | 内容 | 完成标准 |
|---|---|---|
| 2a | Platform 能力登记；BFF 6 组接口、模型、配置与测试 | BFF `clean verify` 通过；Platform 相关 Node 测试通过 |
| 2b | 前端接口模块、导航与能力登记、标准 API 与接入方列表、标准 API 编辑、接入方工作区基本信息与列表页签 | `npm test`、`npm run build` 通过 |
| 2c | 原子 API 编辑（七页签）与脚本编辑 | 同上 |
| 2d | 流程编辑器：画布、属性面板、取值/条件编辑器、校验定位 | 同上，流程模型与定位测试覆盖第 6 节场景 |
| 2e | 注册数据本机执行与端到端验收；更新台账 | 本机端到端走通；`npm --prefix requirements-site test` 无新增失败 |

每个子步结束交付结果并等待确认。只有实际运行通过才记为通过。

## 8. 风险

- **流程编辑器体量最大：** 画布、三类取值、必经变量树和定位相互关联；先完成纯 TS 模型及测试，再接界面。
- **注册数据与测试库冲突：** 见第 5 节与 F3。
- **工作区共享：** `PlatformClientConfiguration`、`pageCapabilities.ts`、`console-resource-capabilities.json`、BFF `application.yml` 是共享文件，修改前确认其他会话没有同时修改，按段落补丁。
- **design.pen：** 页面以已导出的 PNG 为准，不改设计文件。

## 9. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| F1 | BFF 模型 | **按 README 规则定义 BFF 本地模型**，Impl 显式转换；`definition`、`flowDefinition` 作为 JSON 原样透传，不在 BFF 复制其内部结构 |
| F2 | 422 字段错误 | **外部 API 改用专门转换，保留 Platform 的路径与说明。** 现有 `ConfigurationValidationConverter.local` 有意不传播远端文案（统一改为“请检查此处配置”），且路径段只接受 `字母数字[下标]`，`BODY:amount`、`HEADER:Content-Type` 这类键会被整条丢弃，流程编辑器将无法定位。Platform 的说明是校验器固定文本加用户自己提交的路径，不含其他数据；BFF 仍限制条数与长度，前端按纯文本显示 |
| F3 | 注册数据执行在哪个库 | **新建本机独立库 `mor_platform_external_api`（或沿用 `mor_platform_config_validation`）供本机 Platform 端到端运行**，不写入测试同用的 `mor_platform`；需要你授权执行 DDL 与注册/启用 SQL |
| F4 | 画布节点位置 | **不保存**，每次打开按图结构自动布局。保存位置需要扩展流程结构，后续确有需要再加 |
| F5 | 必经变量树 | 前端本地计算支配节点用于提示和灰显；保存仍以服务端校验为准 |
| F6 | 代码授权 | 授权修改 `operations-console-bff`、`operations-console-web`，以及 Platform `console-resource-capabilities.json` |
| F7 | 测试 | 按第 6 节随子步编写并运行（不再暂停测试） |
