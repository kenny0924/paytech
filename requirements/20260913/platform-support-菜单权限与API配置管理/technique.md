# 现状差异与系统影响分析

阶段 2 已由用户“可以”确认，下文保留当阶段分析及估算。阶段 3 精确设计见 [design.md](./design.md)、[api.md](./api.md)、[db/README.md](./db/README.md)、[ui.md](./ui.md)及[研发计划](./implementation.md)。范围与验收见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)，实现与环境状态只读[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)。本轮没有实施管理新能力。

**一句话总结：六类入口共用一套管理模型，预计改 Platform/BFF/Web 三个应用，复用七张配置表，当前未发现新增表或业务字段的必要；基础新增操作估算 15 个（Platform/BFF 各约 15 个端点），辅助目录、引用聚合和既有接口改动在阶段 3 精算；Gateway 暂无已证明的生产代码改动需要。**

## 1. 范围与证据基线

- Target/Route 覆盖 OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、CHANNEL_CALLBACK；管理入口只有 Operations。菜单/按钮本期仍只维护 OPERATIONS。
- Platform 当前按 api/access-control/audit-security/web/test 分层，BFF/Web 延用现有工程；不另建应用或数据库。E01–E19 是第 7 节的源码/合同证据。
- 当前另有未提交的支付 Channel 命名等改动，涉及 Platform、BFF、Web、Gateway 的回调枚举/校验。本需求不归入、提交或部署其他需求改动；阶段 3 固定共同 Wire 基线，发布准备核对实际制品，不能把源码和旧制品的回调代码混用。
- 测试源码说明已有覆盖范围，不表示本轮已运行应用测试；已部署状态以当前台账的对应批次证据为准。

## 2. 现状差异与缺口

| 需求 / 验收 | 当前行为与证据 | 缺口与建议 |
|---|---|---|
| Target 新建编辑 AC-07/08 | E01/E02 仅列表、详情、状态更新；E03 已有完整候选校验 | 增加创建、改 Discovery 的 DTO/命令/持久化及页面；稳定编码/Channel 身份不改，引用校验和启停复用 |
| Route 新建编辑 AC-09/10 | E01/E02/E04 可回读 Root、Header、Query、Guard，写入口只有状态 | 新增完整创建编辑，一次本地事务写 Root 和三个子集合，失败回滚；保持原详情/启停兼容 |
| 六类配置 AC-07/13 | E05 有六类兼容矩阵，E06 有 allPublicChannels 及非法组合用例；E07 使用统一 Snapshot | 配置层已通用，只需一套管理 DTO/表单，按 Channel 显示适用认证/授权字段，无需六套表或 Controller |
| 菜单/按钮 AC-02/06 | E08 有 t_console_resource；E09 读取当前授权资源并移除缺父项，无管理 CRUD | 管理读取必须能看停用和未分配资源，不能复用普通用户过滤后的导航树；新增树管理、编辑/启停与刷新，父子状态不批量改写 |
| 页面挂载 AC-03/UI-01/04 | E10 精确校验组件/路径映射，E11 显式注册页面；未知映射会被隐藏 | 提供已部署组件/操作选择和匹配校验，收敛重复注册；名称/层级/排序可动态维护，路径可编辑范围在方案阶段定稿，不生成远程页面或新业务按钮 |
| 权限目录 AC-04/05 | E08 有 t_permission，E12 只有角色编辑所需的可分配权限读取 | 缺管理 CRUD/启停/引用；管理目录、Route 权限选择与 Operations 角色可分配目录须区别职责，复用同一权限事实，不增加权限 Channel 列或第二套权限表 |
| 首次分配 AC-04/12 | E13/E14 只允许转授已持有且由 ACTIVE Operations Route/Resource 引用的权限；内置管理员不是通配授权 | 新建权限没有持有人，直接复用旧目录会卡住首次分配；需明确平台管理员的首次分配资格，仍显式授予角色，不默认给普通角色或创建者权限，具体窄扩展在阶段 3 评审 |
| 权限撤销 AC-06 | E09/E13 按当前有效权限判断授权/导航；Snapshot 权限投影只保留身份 | 新增状态命令并协调引用/角色写入；停用后新权限判断拒绝、重载导航隐藏，角色关联保留；重新启用按原关系恢复 |
| 引用查询 AC-05/UI-06 | Target 下 Route 可通过现有过滤查询，权限的资源/Route/角色反查尚无管理入口 | Platform 提供有界业务查询，BFF 组合页面；避免全量扫描或 N+1，不泄露凭据/无权查看的明细 |
| Scope AC-09 | E05 只校验 requiredScopeCode 格式，E08 没有独立 Scope 主表 | 阶段 1 的“选择合法目录”需纠正为按既有合同填写，可提示已有 Route Scope；t_api_credential_scope 是逐凭证授权事实，不能当全局目录，本期不建 Scope 表 |
| 并发/幂等 AC-10/11 | E03 已有 catalog 资源锁、串行化本地事务、幂等与 Version/CAS，仅用于启停 | 完整保存、资源及权限状态需要扩展业务命令/Hash/回放和审计，评估真实冲突域；沿用 Foundation 锁，不新建回执表，不按操作者/Tenant 锁全局目录 |
| 管理入口保护 AC-01/11 | E15 只对部分 operations-gateway-* 停用给页面提示；已有最后管理员保护针对人员/角色 | 新能力还会影响登录、导航和管理资源，阶段 3 明确内置身份不可改、哪些操作允许及影响提示，复用人工恢复办法，不引入审批系统 |
| 页面及 BFF AC-01/UI-02..05 | E15/E16 仅处理查询和状态，详情在列表中展开 | 增加新增/编辑与只读页面、资源树、权限引用、字段错误和草稿保护；详情复用只读编辑布局，浏览器回退，Pencil 在阶段 3 定稿 |
| 生效语义 AC-07/14 | E07 原子切换完整 Snapshot，E15 已区分保存与刷新 | 沿用刷新机制，不新增发布或回执；六类配置/编译均验证，真实转发用已接通的 Operations 链验收，不据保存成功宣称所有业务认证已接通 |

## 3. 各系统影响

| 系统/仓库 | 影响点 | 改动类型与边界 |
|---|---|---|
| Platform Support | api 增加管理能力；access-control 负责规则、授权、查询及持久化；web Processor 编排本地事务；audit-security 扩展受控审计事实 | 代码/测试/注册 DML；模型是否补索引或注释阶段 3 明确。只用一个 Platform 数据库，不按 Channel 拆表/服务 |
| Operations BFF | 新管理转发、可信上下文、PLT-I02、请求/响应和错误适配；引用/目录确有多查询组合时由 Processor 组装页面 | 代码/测试，无数据库。不能复制 Platform 事务或权限事实 |
| Operations Web | Target/Route 新增编辑与只读详情，菜单/按钮树、权限目录/引用、关联选择、导航刷新和浏览器历史 | 页面/测试，阶段 3 Pencil；只有一个管理入口，菜单资源本期仍仅 OPERATIONS |
| API Gateway | 复用完整 Snapshot、Channel/Profile/Mode 校验、请求内 Target/Route 绑定和动态转发 | 暂无必要生产代码改动；需注册新管理路由并验证兼容。不添加静态业务名单、认证链、回执系统或放宽绝对上限 |
| Foundation | 复用现有 HTTP Problem、字段校验投影、JSON 和标准资源锁 | 暂不改代码或发布新公共能力 |
| Risk / CCT / Commerce / Payment / Finance | 是已有或后续 Route 的业务目标/消费者 | 当前不改其业务代码/数据库；登记 Route 不能生成这些系统尚未实现的 API |
| 根目录 / 需求站 / test 材料 | 范围、权威入口、全局目录/时序及后续 API/SQL/Pencil/发布材料 | 当前仅文档与相称检查，后续环境操作仍逐阶段确认 |

## 4. 数据与接口量级

七张复用配置表为 t_gateway_target、t_gateway_route、t_gateway_route_header、t_gateway_route_query_param、t_gateway_guard_rule、t_console_resource、t_permission。另复用 t_role/t_role_permission、既有命令幂等和审计表；“七张配置表”不是所有参与读取/事务的表总数。

目前未发现新增表或业务字段的必要；查询索引/注释是否调整须看阶段 3 的实际访问路径，不能提前承诺零 DDL。不增加删除字段、配置版本/发布/审批表、Scope 表或前端组件事实表。

| 能力 | 新增基础操作估算 |
|---|---:|
| Target 创建、编辑 | 2 |
| Route 创建、编辑 | 2 |
| Resource 列表/树、详情、创建、编辑、启停 | 5 |
| Permission 列表、详情、创建、编辑、启停、引用查询 | 6 |
| 合计 | 15 |

Platform 内部端点与 BFF 浏览器端点分别落地，基础量级约 30 个端点。组件/操作目录、分页选择器和引用组合是否产生额外端点，及既有角色目录/导航/管理详情的改动，在阶段 3 给最终逐条清单；此处不是接口合同。

新管理 API 的 Route、Header/Query/Guard、Permission、Console Resource 和受控管理员授权需要 Platform 注册 DML。准确行数依赖最终端点、权限粒度和页面布局，阶段 3 列清单；目前没有可执行 SQL，不重复执行现有初始化文件。

## 5. 需保留的实际边界

1. **配置管理覆盖六类，业务认证并未全部接通。** E17 可找到 Operations、Client API 的认证链与最终拒绝链，未发现其余四类对应的已实现入口；E06 是配置编译证据。本期不扩展为 CUSTOMER 登录、Checkout 会话或回调认证研发，未接通能力不能通过改成 PUBLIC 绕过。
2. **管理目录不等于当前用户权限。** 可以维护全局 Permission，不等于把所有权限交给当前账号。普通角色授权仍保留 Operations 范围；新增权限的首次分配是本期必须闭合的业务缺口，不能只做 INSERT 页面。
3. **停用配置也必须完整合法。** E05 会校验目录对象、引用和字段，不只检查最终生效集合；保存为停用不允许写入半条占位 Route，否则仍可能导致整份快照拒绝。
4. **事务只操作本库。** 授权/候选校验、写入和审计遵循 Platform 本地事务边界，Gateway 刷新独立执行；不在事务内向 Gateway 请求“发布确认”。
5. **现有系统身份保持稳定。** 菜单路径可编辑范围、按钮注册、登录/导航/管理入口与系统权限维护边界由阶段 3 明确，不以隐藏控件代替服务端保护，也不做通用恢复引擎。

## 6. 下一阶段交付

阶段 3 定稿以下内容，完成后再次停下确认研发：

- 基础及辅助 API 总表、既有合同改动、错误、权限与 Route 注册映射；沿用 v1，类名无版本后缀。
- 权限初始状态、首次分配资格、普通管理员转授范围及状态恢复；内置管理资源的维护清单。
- 组件/路径/按钮目录、六类认证授权的表单组合、Scope 输入，以及 Channel 命名的制品基线。
- 资源锁/CAS/数据库保护、完整写事务、命令 Hash/回放、引用分页/索引及准确 DDL/DML。
- Pencil 资源树、权限目录/引用、Target/Route 编辑/只读页，以及每项 AC/UI 对应的开发和综合验证计划。

## 7. 源码与合同入口

- E01：[Platform 管理 API](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/gatewayconfiguration/management/GatewayConfigurationManagementApi.java)。
- E02：[Gateway 管理写 Mapper](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/GatewayConfigurationManagementMapper.xml)。
- E03：[管理 Processor](../../../paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor/GatewayConfigurationManagementProcessor.java)、[管理 Service](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/GatewayConfigurationManagementServiceImpl.java)。
- E04：[当前配置读取 Mapper](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/GatewayConfigurationMapper.xml)。
- E05：[Platform 完整配置编译器](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/gatewayconfiguration/GatewayConfigurationCompiler.java)。
- E06：[六类入口与非法组合测试](../../../paytech/platform-support/paytech-platform-support-test/src/test/java/com/paytech/platform/support/auto)。
- E07：[Gateway Snapshot 编译器](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java)、[动态路由合同](../../20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-design.md)。
- E08：[Platform Schema](../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql)、[数据库说明](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)。
- E09：[Operations 授权与导航](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl)。
- E10：[已部署组件/路径过滤](../../../operations-console-web/src/navigation/menu.ts)。
- E11：[App 页面路由及导航刷新](../../../operations-console-web/src/App.tsx)。
- E12：[既有人事角色管理 API](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/operations/OperationsManagementApi.java)、[角色 Service](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/OperationsRoleServiceImpl.java)。
- E13：[权限/角色/资源查询](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/OperationsDirectoryMapper.xml)。
- E14：[可授予权限与管理员规则](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/OperationsManagementPolicyServiceImpl.java)。
- E15：[Gateway 配置页面](../../../operations-console-web/src/pages/GatewayConfiguration.tsx)、[状态命令及提示](../../../operations-console-web/src/pages/gatewayConfigurationModel.ts)。
- E16：[BFF 管理 Controller](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/ext/rest/GatewayConfigurationController.java)。
- E17：[Operations 安全链](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/OperationsSecurityConfiguration.java)、[Client API 安全链](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/clientapi/ClientApiSecurityConfiguration.java)、[最终拒绝链](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/GatewayFallbackSecurityConfiguration.java)。
- E18：[公共控制面设计](../../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)、[数据库路由决定](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)。
- E19：[首次 Operations 切片](../../../agent-docs/plans/2026-09-04-operations-console-first-slice.md)、[现行技术栈](../../../agent-docs/TECHNOLOGY_STACK.md)。
