# RCO-16 Gateway 数据库动态路由方案修订

2026-09-12 · 已确认的阶段 3 技术方案；用户随后确认进入阶段 4。用户已确认 Route/Target 统一由数据库管理、人工 SQL 维护，以及 Operations BFF 管理端口隔离；确认依据与取代边界见[范围决定](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)。原 RCO-16 业务合同和已完成验收保持，本文件只定义 Gateway 补改及其直接影响。

**本次补改涉及 Gateway、Platform、Operations BFF 三个系统及 test 部署配置；新增/废弃 API 0 个，修改 API 字段 0 个，新增表/业务列/索引 0 个，修改 1 张现有表的表注释，新增注册 DML 0 条、页面 0 个；BFF 增加 1 个独立管理监听。** 原需求的 14 API、Risk 2 表/21 列、182 条注册不重复计数；合并交付共涉及 Risk、Platform、Gateway、BFF、Web 五个系统。

## 1. 配置权威与运行链路

Platform 的 `t_gateway_target`、`t_gateway_route` 及已有 Header/Query/Guard 配置是权威。Gateway 不直连 Platform 数据库，也不为每次业务请求查询控制面；继续通过现有 PLT-I20 获取完整快照，在本地编译并原子切换。

```text
人工 SQL / 既有启停操作
  → Platform 数据库 → PLT-I20 完整快照
  → Gateway 完整校验 → 不可变 Target 索引 + Route Catalog → 原子切换

/api/operations/** → 确定可信 Channel
  → 在一次取得的快照中匹配 Channel + Method + Path
  → 固定 Route、其 Target 和下游路径
  → 既有 Guard、认证、限流及可信 Header 处理
  → lb://<Target.discoveryServiceName> → 已配置 Nacos 中的服务实例
```

入口前缀只决定请求进入哪个渠道的路由匹配。没有匹配的有效 Route 仍返回 `404 GATEWAY_ROUTE_NOT_FOUND`，不能将整个前缀无条件转发。其他 Channel 继续使用已批准的入口和认证合同。

Target Code 是逻辑引用，Discovery Service Name 是实际服务发现名，两者允许不同。Gateway 不再保存业务服务身份三元组、下游路径根或业务服务 URI 的第二份静态名单；移除 `ApprovedTargetRegistry` 的对应生产职责。业务端口由 Nacos 实例提供，不新增 Host、Port、URL 或脚本字段。

Nacos 地址、Namespace/Group、Platform 快照读取入口和网络绑定仍为启动配置。动态 Target 限于同一已配置的服务发现范围和已实现的 Channel/安全能力，不增加跨 Namespace 发现或新的认证实现。

## 2. Gateway 实现合同

### 2.1 编译并绑定 Target

- 从快照 Target 构造不可变运行对象，包含 Target Code、Channel、Discovery Service Name 和经校验派生的 `lb://` URI。建立按 Target Code 查找的不可变索引。
- 每条运行时 Route 绑定同一份快照中已编译的 Target 对象。`GatewayRuntimeRoute`、`GatewayRuntimeSnapshot` 和匹配结果仅按真实消费者需要调整，代码类型不带 V1/V2。
- 请求匹配后固定 Route、Target、下游路径。认证、超时、限流、Header 策略和最终转发均使用这次匹配结果，不在转发阶段重读 SnapshotStore。
- `GatewayRouteConfiguration` 不再启动时遍历固定业务服务清单。传输适配层根据请求固定的 Target 建立本次 Spring Cloud Gateway 转发上下文，保留现有 GlobalFilter、路径重写和负载均衡链。
- 不另建异步刷新的 RouteLocator 业务缓存，避免该缓存与 SnapshotStore 在不同时间切换。具体框架适配点在研发中通过真实 HTTP 转发测试确定，必须满足上述单次请求一致性合同。

现有 `DynamicRouteWebFilter` 已先匹配 Route；`UpstreamRequestGlobalFilter` 已处理下游路径与可信 Header，应在其现有顺序上衔接。框架适配不得绕过已实现的安全过滤器、直接发起另一套 HTTP 转发或接受任意直连 URI。

### 2.2 保留的校验和失败语义

| 输入或事件 | 规定行为 |
|---|---|
| 合法新增 Target 和绑定 Route | 完整快照刷新后可用，无需修改或重启 Gateway；下游须已注册 |
| 仅修改 Discovery Service Name / Route 的目标 | 新请求使用新配置；已匹配请求保留原目标，不中途改投 |
| 停用 Route / Target | 按 Platform 既有有效配置投影从新快照移除对应路由；不强行中断已匹配请求 |
| Target 缺失/重复、非法服务名、Route/Target 渠道不符 | 整份快照拒绝，不部分启用；不能将 URI/主机/端口作为服务名逃逸校验 |
| 非法 Method/Path、变量不等价、冲突、未知 Security Profile 或超出上限 | 延续原完整校验与拒绝语义，取消业务路径名单不取消路径安全检查 |
| 快照获取或编译失败 | 保留 Last Valid，沿用失败指标与诊断；启动无有效快照时不转发 |
| 无有效 Route | 本地 404，不回退到默认 BFF/服务 |
| 已匹配 Target 无可用实例 | 沿用现有上游失败及 Problem 映射，不改投其他 Target 或直连地址 |

保留 Platform 当前状态/CAS规则、完整读取事务、Gateway 单次刷新并发控制及原子替换。保留已有空目录/全停用语义：`routes=[]` 可以关闭全部公开路由，不能借此修改 Platform 对从未初始化 Target 数据的校验。保留六类 Channel、预编译认证、安全上限、可信 Header 和 Redis 运行状态；不新增审批引擎、配置回执表或通用发布流程。

## 3. 接口与数据库

PLT-I20 的 `GatewayTargetSnapshot(targetServiceCode, channel, discoveryServiceName)`、Route Snapshot 以及 `schemaVersion=1` 均足够，保持现有 v1 路径和精确请求/响应形状。既有 Target/Route 列表、详情、启停接口也不变；本期不增加创建、编辑、删除接口或配置页面。

`mor_platform.t_gateway_target` 保留现有 10 列、索引、类型、默认值和数据，仅将表注释修订为：

> 保存由 Platform 统一管理的公共入口目标当前配置；Gateway 从完整快照读取目标身份与服务发现名称并执行通用校验

待执行文件为 [Platform DDL](./db/platform-support-service/ddl.sql)。研发时将同一注释同步到 Manifest、生成 PDManer、完整 Schema 和相关断言；本阶段不改生成模型，不表示任何数据库已经执行。没有新增业务种子、Target/Route 注册数据；原 [182 条注册 DML](./db/platform-support-service/dml.sql)保持原包。

SQL 按具体环境单独确认后人工应用，先记录 `SHOW CREATE TABLE` 并备份受影响表；核对独立的 mor_platform 身份、现有 10 列和旧注释后执行，事后复核列、索引、行数未变。表注释 DDL 不负责启用动态路由，也不能代替部署新 Gateway。若环境注释已是目标值可跳过；出现第三种值先核对并保留其他任务变更。

## 4. Operations BFF 管理监听

| 项目 | 方案 |
|---|---|
| 业务监听 | 保持 18201，按已有部署地址绑定；只将此监听注册到 Nacos |
| 管理监听 | `management.server.address=127.0.0.1`、`management.server.port=19201`，与业务监听分离 |
| 管理端点 | 保持现有 health/info/prometheus 暴露项及 health 细节限制，不新增管理功能 |
| test 健康探针 | 在 BFF 容器内访问 `http://127.0.0.1:19201/actuator/health/readiness` |
| 网络与公开路由 | 不映射管理宿主端口，不注册管理服务发现实例，不创建管理端点 Gateway Route |
| 自动测试 | 业务、管理分别配置端口 0；分别读取实际随机端口，保留独立监听断言 |

需修改 BFF `application.yml`、测试配置和 `test-environment/compose.applications.yaml` 中的 BFF 探针。验收业务监听的 `/actuator/health/readiness`、`/actuator/info`、`/actuator/prometheus` 均不可访问，管理监听在容器内健康可读。业务路由即使误指向 Actuator 路径，也不能通过业务监听得到管理端点。

当前 test 未发现依赖 BFF 业务端口的外部 Prometheus 抓取配置；本轮不新增监控服务。发布前再次检查消费者，若出现实际抓取依赖，先补充对应受限访问方案后确认，不把管理端口扩大绑定为公网地址。

## 5. 研发顺序与验收

1. 核对 Gateway 主工作区与 test 已部署基线，列出相关差异并确定本轮制品源码清单。当前 main 含未部署的 Client API 工作，不能因删除 Registry 顺便全量发布；保留已部署的 Operations/Risk 能力，不撤销其他任务源码。
2. 先建立失败证据：快照使用代码中不存在的 Target Code 和 Discovery 名称、真实转发到不同下游；已有实现应暴露静态校验或传输缺口。
3. 实现 Gateway 编译、请求绑定、动态传输及测试；同步 Platform 旧断言和模型/Schema 表注释。Platform 查询与 Wire 无需为了形式新增实现层。
4. 实现 BFF 独立管理监听，调整测试和容器探针；执行开发检查并交付阶段 4 结果，等待用户确认综合验收。
5. 阶段 5 补齐下表验收，标明真实中间件/真实 HTTP/Simulator 边界；再确认返回阶段 6 制品与发布准备。

| 编号 | 必须证明的行为 | 验证入口 |
|---|---|---|
| GW-01 | 至少两个独立下游入口；逻辑 Code 和 Discovery 名不同且不在 Gateway 常量内，均能正确转发 | Gateway 公开 HTTP + 现有服务发现/负载均衡边界 |
| GW-02 | 新增 Route/Target、改绑定/Discovery、停用及全停用按刷新生效，无重启；存量 Operations 仍可用 | 实际 Snapshot 刷新 + HTTP |
| GW-03 | 确定时序暂停已匹配请求，切换快照后再放行；旧请求去旧目标，新请求去新目标，路径/策略不混用 | 同步屏障控制的并发 HTTP |
| GW-04 | 缺失/重复目标、错误 Channel、恶意服务名/路径/变量、冲突和未知安全档案使整份快照拒绝 | Platform 编译 + Gateway 编译/刷新；Last Valid 与指标断言 |
| GW-05 | 无匹配本地 404、无实例沿用上游错误、控制面故障保留旧快照；未初始化不转发 | 公开 HTTP、故障注入 |
| GW-06 | Operations 登录/刷新/退出、IAM、Risk，以及已实现其他 Channel 的鉴权/Guard/编码路径/Problem 不退化 | 现有 Gateway/BFF 回归及新增路由链路 |
| GW-07 | BFF 管理端点业务端口不可达、独立回环端口健康可读；test 容器新探针通过且业务注册端口不变 | 实际 BFF 启动 + 两监听 HTTP + 容器配置检查 |
| GW-08 | Platform DB 读取的完整快照可被新 Gateway 接受，包含 RCO-16 字典路径；模型/完整 Schema/增量注释一致 | 真实本地基础设施与模型检查；SQL 应用另行授权 |
| GW-09 | 原 37、维护态 27、最终 51 Route 等已核对目录均可编译，实际 test 公开入口完成字典访问且未经授权不能操作 | 阶段 5 离线/本地；阶段 7 授权后真实入口验收 |

Platform/Risk 使用项目已有本地中间件，禁止启动建表或 Testcontainers。测试数据由测试范围管理，SQL迁移、test写入另按授权执行。Simulator 的成功不等于真实登录、Nacos、数据库或公网入口已经验收。

## 6. 发布与恢复边界

原 RCO-16 发布准备暂停，原 BFF 镜像因监听变更需重建，Gateway 必须新增专用制品；Risk/Web 已有制品只有源码及依赖 Hash 复核仍匹配时才可复用。阶段 6 更新完整文件/镜像 Hash、数据库基线、备份、探针、顺序和验收数据，再单独请求 test 发布确认。

计划顺序：按批准清单关闭风控入口 → 备份/应用 Risk 与 Platform SQL，新增注册仍禁用 → 更换 Risk、BFF 和对应探针，检查业务/管理监听 → 更换 Gateway 并验证维护态完整快照及存量入口 → 更换 Web → 按已核对清单开放风控 Route/Resource → 真实公开入口验收。此顺序是技术方案，最终具体清单由更新后的发布包决定。

旧 Gateway 无法保证接纳新动态 Target 或字典路径；需要回退时，先关闭新增入口并恢复为经旧制品实际验证可接受的 Target/Route 配置，确认旧快照可编译，再切换旧 Gateway。否则刷新失败保留 Last Valid 可能让关闭动作无法生效。BFF 镜像与健康探针同时恢复，不能用旧镜像配新管理端口探针。

表注释可按备份恢复，但不回退列/索引或覆盖其他 Platform 数据。保留 Risk 新增字典记录和历史，不在产生新写入后整库还原。超出批准恢复清单时先列出差异，不自动扩大删除或恢复范围。

## 7. 阶段授权边界

阶段 3 交付方案、范围决定、注释 DDL 和文档路由修订。用户随后确认进入阶段 4，范围是 Gateway、Platform 模型/验证、BFF 及探针的研发和开发回归；不包含 SQL 应用、test 部署、Git 提交或推送。阶段 4 交付后须再确认综合验收，记录见 [delivery.md](./delivery.md)，实际状态见项目当前台账。
