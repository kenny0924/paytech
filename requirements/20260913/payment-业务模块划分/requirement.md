# Payment 业务模块划分

> 四模块边界已确认；全系统 Provider → Channel 术语及实体/字段由[渠道命名统一专项](../payment-支付渠道命名统一/requirement.md)拥有。本页负责当前模块工程范围，历史评审记录保留在 delivery.md。

来源：用户询问模块拆分并确认四业务模块，随后明确要求“那现在帮我拆一下项目模块吧”。本轮按已确认职责落地 Maven 工程边界；评审和确认记录见[交付记录](./delivery.md)。

## 目标与范围

面向 Payment 开发者，将已批准的支付能力划分成职责清楚、表归属唯一、依赖无环的内部业务模块。四块业务能力划分已获确认；按用户提出的命名方向采用 Execution、Routing、Channel、Card，配合现有 api、web、test。Channel 统一表示支付渠道及其能力，自有实体/字段按后继命名专项同步；具体职责、现有表映射和协作方式见[模块方案](./technique.md)。

本轮创建四个业务 JAR 的 POM 和包边界说明，接入 Web 组合根，增加模块边界检查并执行本地开发验证。当前只有应用初始化代码，因此不虚构待搬迁业务或占位接口。该工程先行范围由[本次决定](../../../agent-docs/decisions/2026-09-13-payment-module-scaffolding.md)记录。保持一个 Payment 服务、一个部署进程和一个数据库；支付业务规则、跨系统合同和 Card 专项评审状态不变。本轮不新增 HTTP 接口、数据库结构、初始化数据或页面，不涉及 SQL 执行、部署或提交。

## 验收标准

- PMOD-01：区分业务模块和工程支撑模块，说明推荐数量及取舍。
- PMOD-02：现有 Schema 每张表对应一个持久化 Owner；Card 专项候选表单列，不把草稿当成现有模型。
- PMOD-03：明确 Maven 依赖、跨模块业务接口、web 编排和事务范围，禁止跨模块表访问。
- PMOD-04：卡明文处理与受控渠道用卡留在已确认 Card 边界；普通支付模型和 Channel 模块适配不能因此扩大原值权限。
- PMOD-05：父 POM 构建 api/execution/routing/channel/card/web/test 七个子模块，Web 装配四业务 JAR；区分工程骨架与后续业务实现。
- PMOD-06：完整 reactor 构建与应用回归通过，包/依赖/唯一入口/持久化 Owner 边界可检查；可执行 Web 制品包含四业务 JAR。

## 依据

- [Java 模块规范 §3.4](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md#34-复杂核心服务的业务能力模块)
- [Payment 业务模型](../../../agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md)
- [OE 与 Payment Pricing Fact 决定](../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)
- [已确认 Card 能力边界](../../20260912/payment-自建CardVault/requirement.md)与[待评审数据稿](../../20260912/payment-自建CardVault/data-design.md)
- [应用工程入口](../../../payment-orchestration-service/README.md)与[当前状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-application-foundation)
