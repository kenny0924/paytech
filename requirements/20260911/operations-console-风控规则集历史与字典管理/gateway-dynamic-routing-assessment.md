# Route / Target 数据库管理评估

2026-09-11。用户要求评估 Route 和 Target 均由数据库管理、Gateway 不再硬编码业务目标及路径。本文件保存阶段 2 评估依据；用户随后已确认建议范围，包括 BFF 管理端口隔离。正式细节见[阶段 3 修订方案](./gateway-dynamic-routing-design.md)，方案确认和后续实现证据由交付记录与当前台账维护；此前“只补一个字典路径根”是局部排障方案，不再代表用户当前希望评估的完整方向。

**结论：可行，建议采用。** 复用 Platform 的现有两张表及 PLT-I20 完整快照，Gateway 动态编译 Target 与 Route，并将同一版本的匹配结果用于实际转发。主要工作在 Gateway 的校验、运行时模型和转发实现，无需另建配置中心。

**按本评估建议范围：新增 API 0 个，修改现有 API 请求/响应字段 0 个，新增表 0 张，新增/修改业务列 0 个，新增索引 0 个；1 张现有表的表注释需同步。** 不为此新增业务注册 DML；RCO-16 原 14 API、Risk 2 表/21 列及 Platform 182 条注册仍按原包单独计数。该统计以人工 SQL 维护 Route/Target 为前提，不包含新增/编辑配置页面。

## 1. 评估时基线：数据已经存在，Gateway 仍有第二份静态事实

| 内容 | 当前证据 | 评价 |
|---|---|---|
| Target 数据 | `t_gateway_target` 已保存 `target_service_code`、`discovery_service_name`、`channel`、`status`、版本及操作人 | 字段够用，不需要把 URL/IP/Port 再加入数据库 |
| Route 数据 | `t_gateway_route` 保存 Method、外部/下游路径、`target_id`、渠道、权限/安全档案、限制及状态 | 已是数据库配置 |
| 读取与校验 | Platform Mapper 读取两表及 Header/Query/Guard；编译器校验 Target 唯一、服务名格式、Route 引用和渠道一致性 | 可复用，未发现必须新增的查询合同 |
| 快照合同 | `GatewayTargetSnapshot` 已包含 Target Code、Channel、Discovery Service Name；Route 已含目标 Code 和下游路径 | 现有 v1 合同和 `schemaVersion=1` 足够 |
| Gateway 静态校验 | `ApprovedTargetRegistry` 固定服务、渠道、Discovery 名称、路径根及 `lb://` URI | 同一业务配置存在代码与数据库两份权威 |
| 实际转发 | `GatewayRouteConfiguration` 在启动时遍历静态 Registry 创建传输路由 | 单删白名单检查仍不能让新数据库 Target 自动获得转发能力 |
| 请求一致性 | 当前请求固定匹配 Route；运行时快照仅保留 Target 数量统计，没有完整动态 Target 定位信息 | 要将解析后的 Target 一起固定，防止请求中途混用两次快照 |

主要代码入口：

- [Platform 数据读取](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/GatewayConfigurationMapper.xml)
- [Target 快照合同](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/gatewayconfiguration/response/GatewayTargetSnapshot.java)
- 历史静态 Registry：Gateway `4220da8:api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java`；后继实现为[动态 Target](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeTarget.java)。
- [传输装配入口（评估后已调整）](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/routing/GatewayRouteConfiguration.java)
- [请求 Route 捕获](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/routing/DynamicRouteWebFilter.java)

## 2. 建议的运行方式

配置链路：

```text
Platform 数据库（Target / Route / Header / Query / Guard）
    → Platform 读取并生成完整快照
    → Gateway 拉取、完整校验和编译
    → 原子替换本地运行时快照
```

请求链路：

```text
/api/operations/** → 可信 OPERATIONS 渠道
    → 在本地快照按 Channel + Method + Path 匹配有效 Route
    → 固定该 Route、解析后的 Target 和下游路径
    → 按现有策略认证、限流、清理/补充可信 Header
    → 根据 Target.discoveryServiceName 通过 Nacos 负载均衡转发
```

Gateway 不直接连接 Platform 数据库，不在每个业务请求上查询数据库，也不另加一套 Redis 配置缓存。数据库是配置权威，内存快照用于高频请求执行。`/api/operations/**` 是进入 Operations 路由匹配的入口，不意味着所有未注册路径也能透传；无有效 Route 仍返回 404。

`target_service_code` 是路由引用的逻辑标识，`discovery_service_name` 是实际服务发现名称，两者允许不同。服务地址、实例 IP 和端口由现有 Nacos/负载均衡能力解析，数据库不接收任意 HTTP URL、脚本、网络协议或表达式。

新增 Target 在同一已配置 Nacos Namespace/Group 下、对应服务已部署并注册、使用现有 Channel/安全能力时，应能随有效快照刷新被识别，无需修改或重启 Gateway。新增认证算法、全新入口协议或跨 Namespace 路由不属于这项动态配置能力。

## 3. 具体影响范围

| 系统/材料 | 必须或建议的变更 | 不扩大到的工作 |
|---|---|---|
| API Gateway（必须） | 移除固定业务 Target/路径名单；从快照构造 Target 索引和合法的 `lb://` 定位信息；编译 Route 时绑定其 Target；请求固定二者；转发使用该结果；移除启动时固定业务服务列表；补齐刷新/并发/转发回归 | 不把鉴权算法、可信 Header、协议规则变为任意数据库脚本 |
| Platform Support（必须同步合同与验证） | 复用查询/编译实现，验证新增 Target、停用状态、Route 绑定、全局冲突；修订“Gateway 还会检查静态名单”的文档、模型/Schema 表注释及旧测试断言 | 暂不新增 Target/Route 创建、编辑、删除 API |
| Operations BFF / test 配置（建议同步） | BFF 当前管理端点与业务共用 18201；评估拆到独立、受限管理端口，调整健康探针和相关监控访问 | 不改风控转发合同、不增业务权限或页面 |
| Operations Web | 现有配置查询/启停页面继续工作，本项没有必要的界面代码改动 | 不将“数据来自数据库”扩大为完整配置 CRUD 页面 |
| Risk | 本项不改业务实现；以新字典路径验证无需 Gateway 业务路径代码即可转发 | 不新增风控运行时能力 |
| 架构规范/发布包 | 明确 Platform 配置成为目标身份及路由开放的单一权威；更新 Target Registry 相关现行规则、回归要求及部署/恢复范围 | 不把评估文档当作已确认决策或已部署事实 |

数据库结构现状够用。`t_gateway_target` 的表注释仍写明由静态 Registry 再次复核，因此确认新方案后需同步 PDManer、完整 Schema 与一条表注释变更 SQL；该变更不增加业务列、索引或数据。SQL 仍须在具体环境发布清单中单独列明，不能在应用启动执行。

现在 Platform/BFF 对 Route 和 Target 都只有列表、详情、启停接口，没有通用新增/编辑接口。人工 SQL 维护已经能满足本轮“由数据库管理”的目标。若以后需要页面创建、修改 Target/Route，应另评接口、权限、并发和页面方案，不计入上述 0 新 API。

## 4. 保留的通用约束与责任变化

移除的是具体业务服务及路径的硬编码。以下通用执行规则继续保留在 Gateway，并由 Platform 配置查询/管理边界共同验证适用部分：

- Target Code 唯一、服务发现名合法，Route 必须引用快照内的 Target，Channel 必须一致。
- Method/Path 匹配、路径变量等价、路径规范化、全局冲突及大小上限校验。
- 现有 Security Profile/认证实现、可信 Header、JWT/Scope 校验、限流与超时边界。
- 无有效 Route 不转发；下游无可用实例按原错误合同失败，不回退到另一个服务。
- 快照完整校验后原子切换；不发布半份配置。沿用刷新失败保留 Last Valid、启动无有效快照不转发的语义。

Nacos 地址/Namespace/Group、Platform 快照读取入口属于启动配置，应继续由部署配置提供；不能要求 Gateway 先从尚未取得的业务路由中寻找读取这些路由的服务。

**责任变化需要明确接受：** 新增或修改数据库 Target/Route 将直接具备改变公网暴露面的能力，不再有 Gateway 代码中的另一份业务名单否决。合法服务名只能保证格式正确，不能证明该服务本应开放。配置写入应由受控运营/操作员完成，保留现有版本、操作人、备份和检查。

当前 BFF 的 test 健康探针访问 `18201/actuator/health/readiness`，没有独立管理端口。取消路径根限制后，如果配置了指向这类端点的 Route，Gateway 会按配置转发。建议把管理端点移到受限端口，不依赖业务路径名单保护它；这属于 BFF 和部署配置的有限补充，不需要新增业务 API。对于其他内部服务，现有网络、监听和服务鉴权边界仍需真实成立；数据库驱动不代表所有 Nacos 注册服务自动对外暴露，也不意味着可通过合法配置绕过核心系统的业务授权。

## 5. 难点和验证重点

实施规模中等。数据模型和 Wire 改动小，主要风险在公共 Gateway 的实际转发和刷新一致性；不能按“删除一个校验条件”估算。

1. 数据库配置的新 Target Code 和 Discovery 名称均未出现在 Gateway 常量中，刷新后仍能真实转发；至少用两个独立下游入口验证，不能只让全部 Target 指向同一个 Simulator 后按 Code 断言。
2. 新增 Route、按既有管理规则停用 Route/Target、切换 Route 所绑定的 Target，以及修改 Discovery 名称，均不需要 Gateway 重启；无匹配配置和无可用服务的行为分别验证。
3. 一次请求匹配后发生快照更新，仍使用原 Route/Target/路径；后续新请求使用新快照。不能在转发阶段再次读取另一个快照，也不能出现 Route 缓存刷新先后造成目标错配。
4. Target 缺失、重复、渠道不一致、非法服务名/网络 URI、模板冲突导致整份新快照拒绝；旧有效快照继续使用并显示失败指标。
5. Operations 登录/刷新/退出、IAM、风控、其他已实现 Channel 的鉴权、Guard、错误映射和编码路径回归通过。控制面读取故障不得令已有业务请求改为逐次同步查库。
6. 管理端口隔离若纳入，需证明业务监听不能访问管理接口，同时容器健康检查/监控正常。

保留现有状态、日志和指标即可，不增加发布回执表、分布式确认流程、审批引擎或另一套配置历史机制。数据库回退仍通过恢复受影响配置并重新拉取验证，不自动覆盖其他 Platform IAM/审计事实。

## 6. 与当前 RCO-16 发布的关系

当前 RCO-16 在阶段 6，Risk/BFF/Web 已准备独立镜像，Gateway 变更尚未实施或授权部署。本方案比“补一个路径根”扩大到了所有数据库 Target 的运行时支持，应作为一次明确的方案修订确认，然后完成必要研发和综合验证，再回到阶段 6 更新完整制品及发布清单。

先核对 Gateway main 与 test 已部署基线差异，不能把主工作区尚未发布的其他改动未经核对一起带入。若 BFF 管理端口隔离纳入，原 BFF 镜像与探针配置也须重建、重验；本文件不承诺当前三个镜像可以直接用于最终发布。

已获用户确认的建议边界：**Route/Target 由 Platform 数据库统一管理，Gateway 保留通用规则和原子快照机制；本期继续人工 SQL 维护，暂不新增配置 CRUD 页面；同步处理 Operations BFF 管理端点隔离。** 本次评估没有实施代码、模型、SQL 或运行环境变更。
