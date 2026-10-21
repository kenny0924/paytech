# Platform Integration 配置与 HTTP 执行归属决定

- Decision status: Confirmed（Owner 和跨系统调用方式）；物理字段、HTTP Wire 和运行材料交接继续细化。
- Decision date: 2026-09-21
- Superseded sections: 第 1 节“单次最多一个目标外部操作……跨 API 业务编排”及配置对象列举、第 3 节“Integration 五表草案”，由 [2026-10-13 标准 API 与流程决定](./2026-10-13-platform-standard-api-flow.md)取代；其余 Owner、内部 HTTP、Gateway、Vault/Card 边界继续有效。
- 2026-10-20 后继：Owner 与"业务系统经 Platform 内部 HTTP 调用"由[2026-10-20 统一对外出口决定](./2026-10-20-channel-connector-unified-outbound.md)取代，外部 API 配置与执行整体迁入 channel-connector。
- 用户决定：通用 API 配置和执行放在 Platform，Payment 调用 Platform 暴露的 HTTP 接口完成渠道调用。
- 详细合同：[Platform HTTP 执行修订](../../requirements/20260914/platform-support-外部API配置与执行/platform-http-execution-amendment.md)。
- 实施、验证和部署状态：[当前台账](../CURRENT_IMPLEMENTATION.md#platform-integration-http-execution-20260921)。

## 1. 决定

Platform Support 拥有通用外部接入 Integration 的配置管理和单次执行能力。API、公共脚本、数据模型和响应合同均由 Platform 管理，配置保存在 Platform 自有数据库；Operations 经 BFF 维护。执行引擎在 Platform 进程内运行，业务系统通过内部 HTTP 委托执行，不在 Payment 引入引擎完成目标外部请求。

不新增独立部署的 Integration 服务或第七核心系统。内部契约沿用轻量 Owner API 模块的 Spring @HttpExchange；内部调用绕过公网 Gateway/BFF，不增加任意公网执行入口。

Payment 继续拥有支付 Channel、Account、MID、能力、路由选择、冻结坐标、交易状态、资金和恢复。通用执行仍仅返回 executeStatus + outputs，单次最多一个目标外部操作，无执行流水、业务幂等、自动重试或跨 API 业务编排。

## 2. 精确取代范围

- 取代[通用执行修订](../../requirements/20260914/platform-support-外部API配置与执行/generic-execution-amendment.md)第 1 节的“Owner 未定、引擎由调用方嵌入”落点，及本次讨论中“Platform 仅管理配置、公共 JAR 在 Payment 内执行”的建议。
- 取代本需求旧 design/db 草稿中受影响的 Payment 配置管理和 Payment 数据库存储目标。旧表字段和 SQL 不自动迁到 Platform，不作为新的可执行 Schema。
- 为 [PROJECT_CONTEXT](../PROJECT_CONTEXT.md) 与 [Platform 数据库规范](../database/PLATFORM_SUPPORT_DATABASE.md)的“不拥有通用技术配置”增加本项窄范围：受控的外部 API 定义及单次执行。不扩展成任意配置中心、中央业务编排或重试服务。
- 不改变 Gateway 入站路由控制面的职责。Integration 管理出站外部调用定义，与 Gateway Target/Route 不是同一套表。
- 不改变 Payment 五表查询的 Owner、Vault Caller/Purpose、秘密及卡材料边界；新增跨进程运行材料交接需要单独明确，不能从 HTTP 执行归属推导新的 Reveal 权限。（2026-10-13 已由 [Platform 读取决定](./2026-10-13-platform-channel-account-reveal.md)单独明确。）

## 3. 下一步设计范围

按 Platform 所有权逐表评审 Integration 五表草案；补齐内部执行请求/响应、获准调用范围、连接材料交接、注册函数的 Owner HTTP 查询及两段 HTTP 超时语义。该决定确认架构，不代替上述具体合同的评审。
