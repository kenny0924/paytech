# 阶段 2：现状差异与影响分析

> 状态：评审稿（2026-10-18），待用户确认。依据 2026-10-18 工作区 `main` 代码、[状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)与 mor-test Compose；不凭设计文档推断部署。

## 1. 状态口径

| 层次 | 结论 |
|---|---|
| 设计已有 | FIN-I01/I02/I05 与账务基础资料在 Finance 需求、`*-api` Javadoc 中已定义；Operations 侧没有任何财务页面设计 |
| 代码已有 | ledger 五个创建命令、期间开放、FIN-I05 四个查询、`LedgerReadApi` 三个查询；account 两个余额查询。全部在 `main`（`5c678730` step-07 搬迁提交及之后），工作区无未提交的 ledger/account 代码改动 |
| 验证通过 | 全 Reactor `clean verify` 8218 例通过（2026-10-18，refactor-main@6b8a6672 合入前）；ledger-test 204、account-test 40 |
| 环境已部署 | mor-test 2026-10-18 按 V2 部署，`ledger-service`（172.30.73.38:18105）、`account-service`（172.30.73.40:18114）容器在 `compose.applications.yaml` 中；本机 `mor_ledger`、`mor_account` 已建 |
| 台账偏差 | 台账的 Finance 各节仍写"未提交、未部署"，实际代码已随 V2 搬迁提交并部署 mor-test；阶段 8 归档时需同步修正 |

## 2. 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 / 原因 | 处理建议 |
|---|---|---|---|---|
| 财务主体列表（AC-02） | 只有 `POST /finance-entities/v1`；`FinanceEntityRepository` 仅 `insert`/`findById`，无 Mapper 分页 SQL | 分页列表（代码、名称、Party、状态） | **未实现**：接口、Service、Repository、Mapper XML 全缺 | ledger 新增只读接口与查询链 |
| 日历与期间列表（AC-02/03） | 日历只有 `findByEntity` 单条；期间只有单条与重叠计数；无列表接口 | 按主体查日历、日历下期间列表 | **未实现** | ledger 新增只读接口 |
| 科目表、科目、币种下拉（AC-04） | `ChartOfAccountsRepository#findByChartCode`、`ChartAccountRepository#findByIds`（≤200 个主键）、`CurrencySpecRepository#findByCurrency`；`LedgerReadApi` 只能按主键批量读科目 | 列出启用科目表、按科目表列可过账科目、列出启用币种 | **契约不支持**：现有接口要求先知道主键 | ledger 新增只读接口；`LedgerReadApi` 不改，避免牵动 account 的 Mock |
| 账簿 / 账户 / 流水 / 分录查询（AC-05/06） | `LedgerBookQueryApi` 四个接口已实现并有 16 个 auto 用例；分页 `PagingRequestBody` 限 10/20/50/100/200 | 运营页面逐级下钻 | 无 Finance 缺口 | BFF 直接转发 |
| 五类创建与期间开放（AC-02/03） | 已实现；`CreateFinanceEntityProcessor` 等继承 `AbstractProcessor`，期间创建用 `AbstractLockProcessor`；错误为 400 `BUSINESS_CHECK_FAILED` 带中文上下文 | 页面表单就地显示原因 | 无 Finance 功能缺口；错误体为 Foundation Problem，BFF 需透传 `detail` | BFF 转发；Web 映射 |
| Client/Partner 余额（AC-07） | `BalanceQueryApi` 已实现，account 先经 ledger 确认账簿存在（事务外），账簿不存在返回 400 而非零 | 页面展示分类余额 | 无缺口；`Money` 为 Foundation `com.paytech.foundation.money.Money` | BFF 转发 |
| 操作人 Header（AC-08） | ledger/account 没有任何 `@RequestHeader`；未引入 `paytech-internal-auth`，无入站过滤器。Risk 先例：Owner API 声明 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`，BFF 以方法参数传入 | 六个命令接口接收并校验非空、记日志 | **未实现**：需改 ledger 六个命令接口签名与 Controller | ledger 增加 Header 参数；调用方只有 ledger 自身测试与冒烟脚本 |
| 浏览器伪造 Header（AC-08） | BFF `OperatorContextArgumentResolver` 只读取并校验 Gateway 重建的 `X-Gateway-*`；剥离浏览器 Header 是 Gateway 职责（README"认证与授权边界"） | 同现有边界 | 无缺口 | 不改 |
| 权限、菜单、路由（AC-01/09） | Platform 无财务权限码；`t_console_resource` 无"财务"目录；Gateway 路由全部由 Platform DML 注册，BFF Target `900000000000000001` 已存在 | 两级权限、1 目录 + 3 页 + 按钮、路由/Header/Query/限流 | **数据缺失** | Platform 初始化 SQL 追加独立块（ID 块 `097xx—099xx` 空闲） |
| Web 页面（AC-11—13） | 无财务页面；`pageCapabilities.ts` 30 项、`configurationModel.test.ts` 断言 30；`console-resource-capabilities.json` 21 项（与 Web 已不同步，缺 Risk 3 项与 Vault 6 项） | 三个页面 | **未实现** | Web 新增三页；JSON 追加三项（不顺带补历史缺项） |

## 3. 系统影响表

| 系统 / 仓库 | 业务职责 | 影响点 | 改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| ledger（`paytech/ledger`） | 账务基础资料与账本事实 Owner | 新增只读接口（财务主体、日历、期间、科目表、科目、币种）；六个命令接口加操作人 Header；对应 Service / Repository / Mapper XML 与 auto 测试 | 代码：是（api、core、app、test 四模块）。模型 / DDL：否（表已存在）。配置：否 | 新增接口放**新接口类**，不改 `LedgerBookQueryApi` / `LedgerReadApi`，否则 account 测试的 `MockLedgerBookQueryApi` / `MockLedgerReadApi` 必须同步实现。命令接口加 Header 为不兼容签名变更，现有调用方仅 ledger-test 与 `-exec.jar` 冒烟脚本 |
| account（`paytech/account`） | Owner 余额投影 | 无 | 否 | 仅被 BFF 新增消费；`README.md`、`account.pdma.json` 当前有其他会话未提交改动，本需求不触碰 |
| Operations BFF | 入口授权、页面转发 | 新增 ledger / account 两个下游：客户端配置、Client Port 与 impl、三组转发 Controller、请求体白名单、错误码映射；共享文件增量：`pom.xml`（加 `paytech-ledger-api`、`paytech-account-api`）、`application.yml`、`OperationsErrorStatusResolver`、`OperationsConsoleBffApplicationAutoTest`（精确路由集合）、`OperationsHttpBodyNamingAutoTest`、测试基座动态属性与新 Simulator | 代码：是。配置：是（新增下游地址属性） | **服务发现差异**：BFF 现有下游全部经 Nacos + LoadBalancer 发现（`RiskDiscoveryInterceptor` 等），而 ledger/account 的 `pom.xml` enforcer 禁止 `com.alibaba.nacos:*`、`com.alibaba.cloud:*`，不注册 Nacos；mor-test 中它们以 Docker DNS `http://ledger-service:18105`、`http://account-service:18114` 被 account/ledger 互相直连。BFF 接入必须用**固定 base-url 属性**，不能复用 Discovery 拦截器，是 BFF 第一个非发现型下游。**并行会话**：Processor 迁移需求正在改 BFF（`service/processor/` 新增、多个 Controller 修改，未提交），本需求只新增文件、共享文件增量，提交时按片段取 |
| Operations Web | 页面与交互 | 新增 3 页组件、模型 / 解码 / Hook / css / 测试；共享文件：`pageCapabilities.ts`（+3）、`ConsolePage.tsx`（+3 映射）、`configurationModel.test.ts`（30→33） | 代码：是 | 菜单树只显示服务端授权且本地已部署的页面，`frontendRoute` 必须与 Platform `console_resource.frontend_route` 完全一致 |
| Platform Support | 权限、Console Resource、Gateway 路由数据 Owner | `platform-support-initial-data.sql` 追加独立块：`t_permission` 2、`t_role_permission` 2（REVOKED 休眠）、`t_console_resource` 1 目录 + 3 菜单 + 1 按钮、`t_gateway_route` 约 18、Header / Query / 限流行；`console-resource-capabilities.json` +3；测试基线 `OperationsCatalog.java` 补两条权限 | 代码：JSON 与测试基线。DML：是（只写不执行）。DDL：否 | 块内 `DISABLED`/`REVOKED` 休眠，激活 SQL 阶段 5/7 另授权；本机 `mor_platform_e2e` 与 mor-test `mor_platform` 分别执行 |
| API Gateway | 动态路由 | 无代码改动；路由经 Platform Snapshot 生效 | 否 | 需确认 mor-test Gateway 快照刷新机制与 Webhook 需求一致（上次未重启 Gateway） |
| test-environment（mor-test） | 部署 | `compose.applications.yaml` 的 `operations-console-bff` 块追加两个下游地址环境变量 | 配置：是（阶段 6 清单） | 无需新容器 |
| 需求目录 / 台账 | 归档 | `requirements.ts` 的 FIN-I01/I02/I05 参与方已含 Operations Console，不改；台账阶段 8 修正 Finance 节提交状态并新增本需求节 | 文档 | 无 |

## 4. 阻塞依赖、难点与工作量依据

**阻塞依赖**：无外部阻塞。CCT OE / Party 列表（Q-01 已决手工输入）与持久化审计（Q-03 已决延后）不在本期。

**改造难点**

1. **BFF 非发现型下游。** 需新增一种客户端装配形态：`RestClient` + 固定 `base-url`（属性 `operations.ledger-base-url` / `operations.account-base-url`，本机默认 `http://127.0.0.1:18105` / `18114`）+ `requestIdInterceptor` + `InternalCallTokenOptions`（目标名 `ledger-service` / `account-service`）+ Foundation `HttpServiceClientFactory.create(..., strict())`，无 LoadBalancer。`auto` 测试沿用 `operations.<x>-test-base-url` 覆盖到 Simulator。README"Risk 配置适配边界"中"独立代理工厂、固定私网基址、单次调用不重试"的约束仍全部适用。
2. **ledger 命令接口加 Header。** 六个方法签名新增两个 `@RequestHeader` 参数（与 Risk 同名），Controller 校验非空并记入日志；ledger-test 的 `HttpTestInvoker` 调用点全部补 Header（`TestRequestHeaders.internal` 目前只带 Content-Type 与 Request ID）。
3. **ledger 新增只读查询链。** 六个端点各需 Mapper XML + Repository + Service 方法 + Controller（沿用"Controller 直接委托 `LedgerQueryService`"形态）+ auto 测试；复用 `PagingRequestBody` 与 `LedgerDataCleaner`（基础资料表已在清理范围，科目表 / 科目 / 币种为保留行）。
4. **Web 三页。** 账务基础资料是三标签页含 5 个表单抽屉 + 1 个确认弹窗；账本查询是四级下钻；余额查询是单表单。参照 `WebhookManagement.tsx` + `src/webhook/` 的模型 / Hook / 抽屉拆分。

**工作量依据（按文件数估计，供阶段 3 拆任务）**

| 落点 | 新增文件（约） | 修改共享文件（约） | 测试 |
|---|---|---|---|
| ledger | api 接口 1 + 响应体 6；core Query/View/Mapper 6 组；app Controller 1、Converter 1 | 六个命令 API 与 Controller、现有命令测试加 Header | 新增 6 个查询测试类 + 命令 Header 缺失用例 |
| BFF | 配置 2、Discovery 无、Client Port 3 + impl 3、Controller 3、Models 3、请求体白名单配置 1 | pom、yml、ErrorStatusResolver、两个全局测试、测试基座、Simulator 2 | 3 个 Browser 测试 + 边界 JSON 3 + 不可用测试 2 |
| Platform | — | 初始化 SQL 一块、JSON、`OperationsCatalog` | 现有 Platform 测试全过 |
| Web | 页面 3、模块目录 3（模型 / Hook / 组件 / css / 测试） | `pageCapabilities`、`ConsolePage`、计数断言 | `npm test`、`npm run build` |

## 5. 风险

| 风险 | 影响 | 处理 |
|---|---|---|
| 与 Processor 迁移会话并发修改 BFF | 共享测试文件（精确路由集合）与 `pom.xml` 冲突 | 只追加；提交用私有索引按片段取；开工前再核对 `git status` |
| ledger 命令接口签名变更 | 其他会话若正在加 Finance 调用方会失败 | 工作区当前无 ledger 改动；阶段 3 把 Header 作为接口合同列出 |
| BFF 固定地址下游在 Nacos 为主的部署模型里是例外 | 运维口径不一致 | 阶段 3 在 README"代码分层 / 下游边界"补一节；mor-test 环境变量进发布清单 |
| `console-resource-capabilities.json` 与 Web 已不同步 | Platform 配置管理页可能看不到历史页面 | 本需求只追加三项，不修历史缺项；单独登记遗留 |
| Finance 数据量大时账户流水全量累计 | 性能 | 已是 Finance 已知 Pending，页面默认按日期范围查询，不扩大 |

## 6. 阶段 3 建议范围

- 接口合同：ledger 新接口类（命名、6 个端点、字段）、六个命令 Header；BFF 三组路由与请求 / 响应模型；错误码到 HTTP 状态映射。
- 注册数据：权限码、ID 块、目录 / 菜单 / 按钮、路由 / Header / Query / 限流清单（只写不执行）。
- 页面：三页结构、关键状态与交互（Pencil 画板或结构稿）。
- 研发计划：按 ledger、BFF、Platform 数据、Web 四条线拆给 Opus 5.5 agent 并行，主会话合并核对与验证；给出每条线的验收标准映射与测试入口。
