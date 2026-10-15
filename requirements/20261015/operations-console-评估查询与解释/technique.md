# Operations 风控评估查询与解释：技术方案

- 阶段 2：现状差异与影响分析（2026-10-15，已确认）
- 需求：[requirement.md](./requirement.md)（Q1～Q5 按建议确认）

## 1. 结论摘要

- 涉及 3 处：Operations BFF（新增 1 个 Risk 客户端、1 个 Controller、条件表达组装）、Operations Web（新增 1 个页面）、Platform 注册数据（1 个权限、1 个菜单资源、3 条路由及其请求头、Query 白名单、限流）。**不改 Risk，不改表结构。**
- 现有机制都能复用：BFF 的统一实时授权（PLT-I02）、Risk 客户端工厂、只记元数据的日志前缀、Web 的按组件键挂载页面、Platform 注册 SQL 模板。没有发现阻塞依赖。
- 冲突点集中在 4 个共享文件（第 4 节），都只需追加本需求的行；其中 2 个当前有通知管理会话的未提交改动，提交时只暂存本需求的行。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理 |
|---|---|---|---|---|
| Risk 客户端 | `RiskClientConfiguration` 为 9 个 Risk API 建客户端，没有 `RiskEvaluationQueryApi`；BFF 依赖的 `risk-api` 是本机 `~/.m3` 中的快照 | 能调用三个查询接口 | 未接入；本机快照需更新到 `a55b3b1` 的 `risk-api` | 增加客户端；本机安装新 `risk-api`（只新增类，Payment 等其他依赖方不受影响） |
| 浏览器入口与授权 | `OperationsRequestInterceptor` 对所有非登录请求调用 PLT-I02，按 Gateway 路由绑定的权限判断；Controller 只做 Query 白名单与格式校验（如 `RiskExecutionReleaseController`） | 3 个 GET，按新权限授权 | 未实现 | 新增 Controller，路径与 Risk 一致（`/risk-decisions/v1/evaluations…`）；沿用 `requireQueryKeys` |
| 回执核对 | 执行发布等接口逐项核对 Risk 回执身份（`RiskControlResponsePolicy`）；规则集等配置接口另有严格的响应形状校验 `RiskOwnerResponseShapeValidator` | 查询结果可信 | — | 核对评估 ID、按业务身份查询时的风控点与业务身份一致；解释接口只做身份核对，不新增形状校验器（只读展示，无写入风险） |
| 条件表达（AC-4） | Risk 解释只给谓词 `nodeId` 与结果；BFF 已有规则集版本详情客户端 `DecisionRuleSetManagementApi`（含条件树、操作数、变量编码） | 每条规则的可读条件与谓词结果 | 需组装 | BFF 对解释中出现的每个 `ruleSetVersionId` 调一次规则集详情（同一次请求内去重），把条件树和谓词结果合并为页面模型；变量名称取自解释的变量表 |
| 节点名称（AC-3） | Risk 解释已给节点名称、规则集编码与版本、连线名称 | 步骤列表 | 无 | 直接使用，不调流程详情 |
| 日志（AC-8） | BFF `application.yml` 的只记元数据前缀含 `/decision-*`、`/risk-lists`，不含 `/risk-decisions` | 响应体不进日志 | 缺前缀 | 追加 `/risk-decisions`（共享文件，一行） |
| 页面挂载（Q1） | Web 按菜单资源的组件键在 `navigation/ConsolePage.tsx` 懒加载页面；`navigation/pageCapabilities.ts` 列出页面与按钮能力 | 新页“评估查询” | 未实现 | 新增 `pages/RiskEvaluations.tsx` 及 `risk/` 下的 API 与模型；两个共享文件各追加一段 |
| 菜单与权限（Q2） | 风控菜单父资源 `OPERATIONS_RISK_MANAGEMENT`；读权限 `RISK_DECISION_CONFIG_READ`；上次接入用 ID 段 `9000000000001010xx～1043xx` | 独立权限 `RISK_EVALUATION_READ` 与菜单 | 未注册 | 新注册 DML，阶段 3 查目标库已占用 ID 后选号段 |
| 路由（Gateway） | 每条浏览器路由在 Platform 登记：路由、转发/CORS 请求头、Query 白名单、限流；新路由先 DISABLED，另有激活 SQL | 3 条 GET 路由 | 未注册 | 同模板；按业务身份查询的路由放行 `pointCode`、`businessKey` 两个 Query |
| 本机验证环境 | 本机 18104 上运行着其他会话启动的 Risk（从 Risk 构建目录启动，已是新代码）；本机 `mor_risk` 已有新表 | BFF 联调真实 Risk | — | 复用本机 Risk；需要评估数据时用合成支付尝试，验收后清理 |
| mor-test | Risk 已部署新接口；Operations BFF/Web 为支付风控配置版本；`mor_platform` 已执行上次注册 | 新页面可用 | — | 阶段 6/7：从已提交代码 + 本需求改动构建 BFF/Web 镜像，执行注册与激活 SQL |

## 3. 系统影响

| 系统 | 影响点 | 改动 | 依赖与风险 |
|---|---|---|---|
| operations-console-bff | `RiskClientConfiguration`（加 1 个 Bean）、新 Controller、新 Client（接口 + 实现）、页面模型、条件组装；`application.yml` 加日志前缀 | 代码、配置 | 依赖新 `risk-api`；`RiskClientConfiguration` 当前无他人改动 |
| operations-console-bff 测试 | 新增浏览器边界测试与 Risk 模拟（Simulator 返回 DTO）；应用级测试 `OperationsConsoleBffApplicationAutoTest` 如有路由/Bean 清单断言需追加 | 测试 | 应用级测试当前有通知管理会话的未提交改动，只追加本需求的行 |
| operations-console-web | 新页面、API 与模型、样式；`ConsolePage.tsx` 加 1 行；`pageCapabilities.ts` 加 1 段 | 代码 | `pageCapabilities.ts` 有通知管理的未提交改动（另一段），分开暂存 |
| Platform（数据） | `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule` | 注册 DML（本机 `mor_platform_e2e`、mor-test `mor_platform`） | 与通知管理的注册并行；执行前查号段与编码冲突 |
| Platform（代码）、Gateway、Risk、Payment | 无 | 不改 | — |
| 文档 | 需求目录；CURRENT_IMPLEMENTATION 的 Operations 行 | 文档 | 共享文件只改本需求的行 |

## 4. 共享文件与冲突处理

| 文件 | 他人未提交改动 | 本需求改动 | 处理 |
|---|---|---|---|
| `operations-console-bff/.../application.yml` | 无 | 加 `/risk-decisions` 前缀 | 直接提交该行 |
| `operations-console-bff/.../OperationsConsoleBffApplicationAutoTest.java` | 有（通知管理） | 视清单断言追加 | 只暂存本需求的行 |
| `operations-console-web/src/navigation/ConsolePage.tsx` | 无 | 加 1 行页面映射 | 直接提交 |
| `operations-console-web/src/navigation/pageCapabilities.ts` | 有（通知管理，另一段） | 加 1 段 | 只暂存本需求那段 |

每次动手与提交前重新查看 `git status`；发现他人开始改本需求的文件，停下并告诉你。

## 5. 工作量与风险

- BFF：约 8～10 个新类（Controller、Client 接口与实现、页面模型、条件组装）+ 2 个测试类；Web：1 个页面、1 个 API 模块、模型与测试；Platform：约 30 行注册 DML（权限 1、授权 1、资源 1、路由 3、请求头约 6、Query 2、限流 6）+ 激活 SQL。
- **风险 1：条件组装与 Risk 规则集详情的耦合。**解释里的规则 ID、谓词节点 ID 必须在规则集版本详情中找到；找不到时页面显示“条件不可用”，不中断整页。
- **风险 2：本机 Risk 实例归属他人。**18104 上的 Risk 不是本会话启动的；联调依赖它在线。如果它被停掉，本会话改为在 18114 启动自己的 Risk，并让 BFF 指向该端口。
- **风险 3：并行注册。**通知管理也在 Platform 注册资源与路由；本需求选号段前查询目标库最大 ID 与仓库内全部注册 SQL，执行前再核对。

## 6. 下一阶段（阶段 3）范围

BFF 三个接口的精确合同（含组装后的条件模型）、页面原型（结论卡片、路径、规则与条件、变量、输入、影子）、Platform 注册 DML 与激活 SQL 清单、测试矩阵与 AC 映射。
