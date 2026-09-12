# RCO-16 Gateway 补改研发交付

2026-09-12 · 补改阶段 4；源码和功能回归完成，覆盖率及本机完整目录门禁尚未闭合。用户确认依据见 [delivery.md](./delivery.md)。

**本次实现涉及 Gateway、Platform、Operations BFF 及 test 探针；新增 API、Wire 字段、表、业务列、索引、注册 DML 均为 0，修改 1 张表的表注释，BFF 增加 1 个独立管理监听。** 原 RCO-16 的 14 API、Risk 两张字典表和 182 条注册数据保持原范围。

## 1. 代码与工件

| 系统 | 已实现内容 | 主要入口 |
|---|---|---|
| Gateway | 移除静态业务 Target/路径注册表，编译数据库快照中的不可变 Target 索引；每条运行 Route 绑定同一快照中的目标 | [GatewaySnapshotCompiler](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java)、[GatewayRuntimeTarget](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeTarget.java) |
| Gateway | 从本次请求已匹配的 Target 构造 `lb://discoveryServiceName`，继续执行原框架过滤器、认证、Guard、路径重写及负载均衡；不二次读取快照或维护独立业务路由缓存 | [GatewayRequestRouteHandlerMapping](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/GatewayRequestRouteHandlerMapping.java)、[GatewayRouteConfiguration](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/GatewayRouteConfiguration.java) |
| Gateway | 关闭请求级 Route 的过滤器缓存，误开启时启动失败；保留空目录、无路由 404、无实例 503、非法刷新保留 Last Valid | [配置](../../../api-gateway/api-gateway-web/src/main/resources/application.yml)、[真实 HTTP 测试](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewayDatabaseRoutingAutoTest.java) |
| Platform | 既有 Mapper/HTTP/编译器已支持数据驱动目标，无需修改生产接口；同步 Manifest、PDManer、完整 Schema 的同一表注释，补充真实 MySQL/HTTP 目标变更及停用验证 | [数据库目标测试](../../../platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayDatabaseTargetHttpAutoTest.java)、[待执行 DDL](./db/platform-support-service/ddl.sql) |
| BFF | 业务端口 18201；管理端口独立绑定 127.0.0.1:19201，业务端口不能访问 Actuator；自动测试两监听分别使用随机端口 | [配置](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/resources/application.yml) |
| test 配置 | BFF 容器内健康探针改为回环 19201；业务注册端口不变，不发布管理宿主端口 | [Compose](../../../test-environment/compose.applications.yaml) |

保留的 `OwnerProblemResponseGlobalFilter` 中旧 BFF 名称仅属于已有错误响应兼容分支，不参与目标发现、路由批准或转发选择。本轮没有改变该兼容政策。

## 2. 开发验证与基线修正

Gateway `clean verify` **982/982**、BFF `clean verify` **1041/1041**、Platform 相关测试 **23/23**、模型/Schema/场景 **48/48** 通过；Gateway 边界脚本通过。最终命令、数量、源码 Hash 和检查结果记录在[验证摘要](./assets/gateway-development/verification.json)。本报告不把 Simulator、离线目录或静态探针检查称为 test 已部署验收。

- 已先取得失败证据：旧 Gateway 拒绝代码中不存在的目标；旧 BFF 业务端口可读取管理健康接口。实现后通过相应真实 HTTP 测试。
- Gateway 测试使用本机 local-environment Redis 及两个独立随机 HTTP 下游，发现别名在测试进程中生成；通过真实服务发现/LoadBalancer 边界，不连接真实 Nacos 或业务 Owner。
- 精确暂停已匹配请求，刷新目标、路径和 Query 策略后再放行，旧请求仍去旧目标，新请求使用新配置。新增、改绑定、改发现名、停用、全停用、无实例、非法刷新均有行为断言。
- Platform 两个新用例使用真实本地 MySQL 与完整 HTTP → Mapper → 编译链，仅创建本次拥有的 Target/Route/子记录，清理后逐项比较五张目录表与运行前完全一致。
- BFF 全量验证包含业务/管理监听隔离。管理子上下文不承载业务请求过滤器，因此原请求标识断言移到业务监听的未匹配请求，管理端点健康仍独立断言。
- Gateway 首次全量存在 7 个断言失败、3 个错误。用独立临时目录中的干净 `4220da8` 对照，复现其中 7 个失败、2 个错误，见[对照证据](./assets/gateway-development/initial-gateway-baseline-comparison.json)。修正旧测试的 401/404、规范 Header 大小写、RuntimeRoute 夹具、非法 JWKS 构造及绝对 URI/解码路径断言，没有放宽生产鉴权或协议断言。
- 还修复了未匹配 Route 的提前拒绝连接问题：POST 得到 404 后，同一连接上的后续请求可能超时；完整消费错误响应体后仍可复现。现在 404 响应明确关闭连接，不读取未知请求体，不更改状态或 Problem 字段；原客户端继续请求并收到预期 400，8 项定向 HTTP 用例通过，见[证据](./assets/gateway-development/early-rejection-regression.json)。
- 全量测试还遇到 Netty 管理上下文暂停时关闭等待卡住，线程与本地框架代码证据见[生命周期记录](./assets/gateway-development/test-lifecycle-stall.json)。调整仅位于自动测试：退出时即时关闭；生产停机行为不变。

## 3. 已部署目录兼容性

Gateway 主工作区起点为 `4220da8`。实际 test 已部署类的请求体上限为 65,536 字节，主工作区却是 16,384；本次保留已部署的 65,536，并验证 65,537 仍拒绝。未改变独立的 JWKS 16 KiB 响应上限。

使用当前 Gateway 编译器离线检查原阶段 6 留存的完整目录与 SQL 投影，37 / 27 / 27 / 51 条全部通过，见[目录兼容性证据](./assets/gateway-development/deployed-catalog-compatibility.json)。这证明新编译器可接受已有及计划注册的字典路径，不表示这些注册 SQL 已应用。

当前 main 还包含未部署的 Client API 工作。本阶段保留这些源码并执行回归；阶段 6 必须再次核对精确发布源码及制品范围，不能直接把主工作区全部变化部署到 test。原 BFF 镜像因监听变化需重建，旧阶段 6 发布包仍不可执行。

## 4. 验收映射与剩余边界

| 验收 | 本阶段证据 | 下一阶段补充 |
|---|---|---|
| GW-01–03 | 两个独立 HTTP 下游、动态刷新与确定时序在途请求隔离 | 综合配置链路复核 |
| GW-04–06 | 完整编译校验、Last Valid、错误/安全/Guard/存量入口回归 | 受影响业务及权限组合验收 |
| GW-07 | BFF 两监听真实 HTTP、探针 YAML 与端口静态检查 | 授权部署后验证容器内探针及实际 Nacos 注册 |
| GW-08 | Platform 真实 MySQL/HTTP 目标修改与停用、模型/Schema/注释一致性 | 本机完整初始化目录核对及后续集成验收 |
| GW-09 | 37 / 27 / 27 / 51 完整目录离线通过 | 阶段 7 授权后真实 test 字典入口验收 |

**尚未通过的覆盖率门禁：** Gateway 分支覆盖率为 **78.445378%（1867/2380）**，未达 90%；报告仍缺少编译器生成的 `GatewayGuardProfileCompiler$1`。这两类问题在项目台账第 13 节已有记录，本轮未降低门槛或排除类。当前范围与未修改源码的分区见[覆盖率差距](./assets/gateway-development/coverage-gap.json)，不能将全量功能测试成功称为全门禁通过。

**尚未通过的既有目录门禁：** `GatewaySnapshotHttpAutoTest` 的整套初始化目录前置检查发现本机 Console Resource 为 12，当前 Oracle 要求 21，在任何夹具写入前停止。该套旧综合测试没有放宽断言；新目标能力使用独立、只管理自身记录的 HTTP 用例验证。完整初始化目录补齐与该旧套件复验仍待阶段 5 处理，不能写成 Platform 全量通过。

本阶段没有执行 Schema/初始化/注册 SQL，没有部署 test、发布镜像、Git 提交或推送。Platform 表注释 DDL 未应用；只发生了按测试范围管理并恢复的本机测试夹具写入。下一确认点是补改阶段 5 综合验证；若核对后需要数据库补齐，先给出具体 SQL、备份与目标库，再单独确认执行。
