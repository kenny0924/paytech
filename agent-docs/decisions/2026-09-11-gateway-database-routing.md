# Gateway Route / Target 数据库管理范围决定

> **2026-09-13 后继范围：** 用户已确认通过 Operations 管理全部六类已有 Channel 的 Target/Route 新增编辑，并维护 Operations 菜单/按钮及权限，见[本期需求](../../requirements/20260913/platform-support-菜单权限与API配置管理/requirement.md)及[阶段分析](../../requirements/20260913/platform-support-菜单权限与API配置管理/technique.md)。这接续本文当批“人工 SQL + 既有启停、不新增 CRUD”的交付上限；数据库权威、通用校验及完整快照机制保持，不恢复静态 Target Registry。具体新增接口与写入合同继续按阶段设计，本文原批次记录保留。

- Decision status: Confirmed（用户确认评估建议范围，包括 Operations BFF 管理端口隔离；2026-09-12 确认修订技术方案并进入阶段 4）。
- Decision date: 2026-09-11。
- 确认依据：[需求交付记录](../../requirements/20260911/operations-console-风控规则集历史与字典管理/delivery.md)。
- 当前实现、验证、部署状态只读 [CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md)；本决定不是代码或环境迁移证明。

## 1. 已确认范围

Platform 数据库是公共 Route 和 Target 配置权威。Gateway 通过现有 PLT-I20 完整不可变快照读取配置、进行通用校验并执行转发，不再在代码中维护具体业务 Target 身份、Discovery 名称、下游路径根或业务服务 URI 名单。Gateway 不直接访问 Platform 数据库，不逐请求查询控制面。

Route 和其 Target 必须来自同一份快照并在请求匹配时一起固定。有效新 Target/Route 随快照刷新生效，转发使用 Target 的 Discovery Service Name 和已有 Nacos/负载均衡能力。无有效 Route 不透传；没有实例不改投其他服务。

保留 Channel、认证/安全档案、可信 Header、Path/Method/模板冲突校验、绝对上限、原子快照切换及 Last Valid 机制。Nacos 和 Platform 快照入口仍由启动配置提供；数据库不引入任意 URL/IP/Port、脚本或新认证算法。

当前通过人工 SQL 和既有启停接口维护配置，不新增配置 CRUD 页面/API、审批引擎或辅助表。保持现有 v1 Wire 和 `schemaVersion=1`，类型名称不加协议版本后缀。Operations BFF 同步隔离业务与管理监听，管理端点不依赖业务路径名单保护。

## 2. 精确取代范围

- 取代[统一动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)第 5 节及其关联校验、测试、上线步骤中“Gateway 静态 Approved Target Registry 二次批准具体业务身份及路径”的要求。
- 同范围取代[Client API Gateway 设计](../../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)第 10.3 节及关联 Target/Discovery/Path Root 注册要求。其 Client API 认证、Header、入口、业务 Owner 约束及各环境启用授权保持。
- 同范围取代 `AGENTS.md`、应用架构、技术栈、Platform 数据库说明及前序物理设计中的静态名单投影；历史实施记录继续保留证据，不将旧实现描述为新实现。
- 原 RCO-16 的单字典路径根追加提议仅保留为故障定位证据，由本次完整动态路由方向取代。Risk 历史与字典的业务范围及接口不变。

配置写入因此直接决定公开暴露面；格式合法的服务名不等于有业务开放资格。由受控配置维护者对目标业务监听、Channel 和权限/安全策略负责，不再依赖 Gateway 中的第二份业务名单。核心服务的业务授权、服务监听和部署网络边界保持各自责任。

## 3. 技术方案与交付

本次[技术方案](../../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-design.md)拥有运行时绑定、BFF 监听、接口/表注释、测试、基线核对和恢复细节；阶段授权和交付证据见需求交付记录。

需求仍按既定逐阶段确认流程交付。设计确认不授权 SQL 应用、test 部署、提交或推送；模型工件与现有环境尚未同步的状态由当前台账记录，不在本决定重复维护。
