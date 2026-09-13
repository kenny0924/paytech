# 全系统支付渠道命名统一

用户要求将所有支付渠道相关的 Provider 命名统一为 Channel，并明确范围“包含所有系统中的支付渠道命名，同步评审关联改动”。本需求承接[四模块划分](../payment-业务模块划分/requirement.md)，从模块名扩展到支付领域术语、实体、字段、事件、模型和消费者；确认记录见[交付记录](./delivery.md)。

## 目标与范围

Payment Channel/支付渠道表示 NI、Geidea 等平台对接的支付提供方及其渠道能力；保留既有实体身份、Account/MID 关系和业务不变量，统一自有命名。四业务模块 Execution、Routing、Channel、Card 保持。

范围包含六个核心系统中支付渠道相关的术语及引用，以及 Gateway、Operations BFF/Web 的渠道回调入口合同。Payment 拥有渠道主档与支付事实，Finance 拥有渠道成本/清算事实，Risk 拥有争议业务决定；命名统一不转移这些所有权。

JUnit/Spring/MyBatis 等技术 Provider、OAuth/通知/存储/风控数据供应商、外部固定协议字段、签名原文和历史记录不因含有 provider 字样而重命名。外部字段在适配边界映射为本地 Channel 模型；原始业务身份与资金值不变。

用户已确认跨系统映射并授权本地实施；[技术方案](./technique.md)与[逐项映射](./rename-map.json)保留评审基线，实际交付见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-channel-terminology)。数据库执行、外部回调 URL 变更和部署仍不在本次授权内。

## 验收标准

- CHN-01：现行自有支付领域统一采用 Channel；保留与 Payment Method、Platform 访问 Channel、技术/非支付 Provider 的明确区别。
- CHN-02：Payment、Finance、Risk 每处候选表/列/索引都有唯一旧新映射，不引入命名冲突；类型、长度、可空性、ID、金额、关系和并发规则保持。
- CHN-03：当前模型生成器、PDManer、完整 Schema、Owner 文档、测试、模块表归属与需求/时序目录按已批准范围同步；保留 PDManer 内部 ID 和关系完整性。
- CHN-04：Payment 事件及引用、Finance 科目/目录值、Risk 来源/触发值、Platform/Gateway 回调枚举及 BFF/Web 消费端同步评审，不能只改发送端或 Java 常量名。
- CHN-05：框架与外部协议固定标识保持；已发布合同、历史消息、快照、Hash、签名及已执行 SQL 有明确边界，不重算历史身份或资金事实。
- CHN-06：代码/设计更名、物理数据库迁移、环境发布分别验证与记录；不以文字替换声称运行环境完成。

## 权威入口

- [文档治理](../../../agent-docs/DOCUMENT_GOVERNANCE.md)、[阶段交付流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)、[数据库规范](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)
- [六系统模型路由](../../../agent-docs/database/README.md)、[当前实现状态](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
- [Gateway 公共控制面合同](../../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)及[动态路由后继决定](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)
