# 渠道 API 配置：现状差异与影响分析

本轮结论：现有工程提供了 Channel 的模块位置，以及 Execution 冻结原交易与路由的接入点；配置执行能力需要新增。NI 退款样例进一步暴露了原交易协议坐标、退款唯一关联和配置存储规则三个必须先处理的合同问题。

这是 2026-09-14 阶段 2 当时工作区的只读分析，保留历史比较证据。部分源码已在后续 Execution 重构中移动或删除，下表不存在的旧路径改为历史文本；不将当时行为当作当前实现。最新配置方向见[模型与映射设计](./model-and-mapping-design.md)。应用实现、验证、部署和迁移状态仍以[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)为准；下表仅记录本次比较所依据的源码事实，不复制测试数量或部署结论。现有工作区有其他任务的未提交修改，本轮不改变这些代码。

## 1. 本轮核查入口

| 证据 | 本次实际看到的内容 |
|---|---|
| [Channel pom](../../../payment-orchestration-service/paytech-payment-channel/pom.xml)、`package-info`（历史源码：`../../../payment-orchestration-service/paytech-payment-channel/src/main/java/com/paytech/payment/channel/package-info.java`） | Channel 源码目录只有包说明；Enforcer 禁止依赖 Payment API/Web/Test 与兄弟能力 JAR |
| `ExecutionPorts`（历史源码：`../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/integration/execution/ExecutionPorts.java`） | `ChannelRequest` 已携带 Action、Route、原交易引用及认证 Action；`Channel` 暴露资金执行、查询和交互读取端口，返回 Execution Result |
| `ChannelActionRequestServiceImpl`（历史源码：`../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/domainservice/service/impl/ChannelActionRequestServiceImpl.java`） | 从原 Action 恢复 Channel/Account/MID，并读取原交易及支付方式；已有调用准备入口 |
| [PaymentExecutionService](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/PaymentExecutionService.java)、`ExecutionSnapshots`（历史源码：`../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/domain/ExecutionSnapshots.java`） | 可按 Client/Action 读取原目标交易；原交易快照有 externalTransactionId，Action 另有 channelOperationRef，均没有 NI 多级资源坐标合同 |
| `ExecutionDependencyConfiguration`（历史源码：`../../../payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/configuration/ExecutionDependencyConfiguration.java`） | 默认 Channel 端口抛出明确不可用异常，没有真实 NI 发送实现 |
| PaymentActionProcessor（核查时类名；该历史源码路径当前已不存在，后继状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)） | 短事务受理发送后调用 Channel，异常转 UNKNOWN，结果另开短事务应用；新执行器的数据准备失败与这条边界需要明确衔接 |
| `ExecutionOwnerMocker`（历史源码：`../../../payment-orchestration-service/paytech-payment-test/src/test/java/com/paytech/payment/test/execution/ExecutionOwnerMocker.java`） | 返回合成 operation/transaction 标识及状态；这些测试替身没有解析 NI 报文 |
| [Operations App](../../../operations-console-web/src/App.tsx)、[Gateway 页面](../../../operations-console-web/src/pages/GatewayConfiguration.tsx)、[Gateway BFF Controller](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/GatewayConfigurationController.java) | 有入口路由配置页面和适配；扫描 Web/BFF 源码未发现支付渠道 API、步骤或协议方案编辑能力。Gateway 的 Route Channel 是入口分类，不是 NI 等支付渠道 |

## 2. 需求与现状差异

| 缺口 / 验收项 | 当前行为及证据 | 目标行为 | 处理建议 |
|---|---|---|---|
| G1 配置模型与执行器；CAPI-01～06 | Channel 只有工程边界，无 API 定义、方案库、步骤注册及执行实现 | Channel → 支付方式 → API；API 显式引用可复用方案，顺序处理一次调用 | 在 Channel 中设计管理与执行合同。能力实现由开发者提供，配置只选择能力与绑定，不生成任意可执行脚本 |
| G2 上下文与查询补数；CAPI-11/12 | Web 有 ChannelRequest；Execution 有授权查询。没有对配置公开的函数目录、输入视图及受控绑定机制 | 按最新交互直接调用注册系统函数、选择返回字段，类型和身份可校验，结果当次复用 | Web 组合 Owner 公开能力；注册函数公开最小输入输出，缺失处理由实现负责，保留四业务 JAR 独立性。不要直接把现有 Action 快照的所有字段开放给表达式 |
| G3 NI 原交易定位；CAPI-02/11 | 现有 externalTransactionId 与 channelOperationRef 的合同没有定义 outlet/order/payment/capture 四段坐标的对应关系；没有可读取 NI 导航事实的实现 | 退款始终定位原 Capture 和原 Account/MID | 先明确已有字段分别代表什么，再决定需补的最小协议事实及 Owner。评估明确引用映射或有类型的补充合同，不默认塞入任意 JSON/拼接字符串。缺失时停止发送 |
| G4 认证与运行参数；CAPI-03/06 | Account 的 integration_config_key 是运行配置定位键；本次端口无真实 Account Token 接入 | 使用冻结 Account 的有效 Token 与获准环境、时限 | 把受控材料访问与配置规则分开；NI 样例复用 Bearer Header 方案。自动 Token 获取继续是独立议题 |
| G5 编解码、字节与金额；CAPI-03/04 | `PaymentMoney`（历史源码：`../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/domain/PaymentMoney.java`） 使用 BigDecimal 并校验币种精度；没有渠道报文字段转换能力 | 保留业务精度，显式转渠道最小单位；传输使用准备好的字节 | 定义转换、编码与提取能力，确认范围并拒绝舍入/溢出。不能把第一方 JSON 金额字符串约定或 Foundation JSON Profile 强套到第三方协议 |
| G6 响应关联与结果；CAPI-07/08 | Execution Result 是业务结果；Mock 直接给出合成成功。NI 响应含父 Payment 与退款集合，本次退款的唯一识别合同未确认 | 分别报告执行事实、渠道证据和本接口输出；有依据地识别本次退款 | 样例暂返回观察记录与 UNRESOLVED。补齐 NI 关联、拒绝码、幂等和未知结果查询合同后，再由后续适配层接入 Execution；不在配置内推进业务状态 |
| G7 保存即生效；CAPI-09 | 没有 Channel 配置保存、完整读取及依赖兼容校验 | 保存成功后新调用可见；在途调用保持所读完整配置；公共方案变更不破坏引用方 | 下一阶段确定保存事务、完整读取及多实例一致性；不新增发布、版本历史或最终一致的延迟刷新来替代用户要求 |
| G8 配置存储规则；CAPI-09 | [Payment 数据库规则](../../../agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md)明确 Endpoint、超时、Secret/证书不进入 Payment DB | Operations 能配置协议，且所有字段都有确定 Owner 与存储位置 | 显式裁定非敏感协议字段与环境参数存放范围；若选择把 Endpoint/超时入库，需要先形成现行规则修订。密钥材料继续受控，不因可配置而放入普通表 |
| G9 Operations 编辑与诊断；CAPI-10 | 有通用控制台组件、Gateway 编辑和权限基础，没有支付渠道编辑器 | 展示 API 数据来源、共享方案及引用影响，保存前检查，脱敏解释处理结果 | 可复用页面组件与鉴权接入方式；业务字段、列表与聚合由 Payment Owner/BFF 分工。不要复用 Gateway Route 表作为支付渠道协议表 |
| G10 发送与失败衔接；CAPI-08/12 | PaymentActionProcessor 在 Channel 调用前已提交发送门禁，并把发送阶段异常统一转 UNKNOWN | 明确区分数据准备失败未发送、可能已发送、收到但无法识别；不重复资金请求 | 后续接入时设计 prepare/execute 边界或等价执行事实合同；是否改变 Execution 受理点须单独设计，不能仅插入新执行器就声称语义已保持 |

NI 事实及核查范围见[真实协议样例](./ni-refund-example.md)，其中 `READ_REFUND_TARGET`、能力代码和结果分类均为建议名称，并非已存在的方法/枚举。

## 3. 逐系统影响

以下是下一阶段设计需要覆盖的影响，本轮没有实施这些改动。

| 系统 / 仓库 | 业务职责与影响点 | 预期代码 / 模型 / 配置变化 | 依赖及兼容风险 |
|---|---|---|---|
| Payment Channel JAR | API/方案管理、校验、单次协议执行、获准输出 | 新增代码；持久化与运行配置位置待裁定 | 依赖受控能力与认证材料；不得向 Execution/Card 反向依赖 |
| Payment Web 与 API JAR | 面向 Operations 的 Owner 管理合同；装配上下文/查询；未来适配执行结果 | 需要设计接口及装配；不是立即给现有 Channel 端口换实现 | BFF 调用轻量 API；Execution 原有受理/查询合同需保持，跨模块不暴露 DataObject |
| Payment Execution | 原交易、Action、冻结路由、退款额度与业务状态 Owner | 本轮只分析；后续可能扩展最小查询投影或发送衔接，不预定加表 | channelOperationRef / externalTransactionId 的含义要明确；UNKNOWN 和同 Action 恢复不能被新配置抹除 |
| Payment Routing / Card | Routing 保持静态 Capability 与路由选择；Card 保持原始材料边界 | 本退款样例无需改变业务事实；未来真实运行需相关 Owner 就绪 | 保存一份 API 配置不等于已启用对应 MID Capability；通用补数不得读取 PAN/CVV |
| Operations BFF | 权限上下文、面向页面的聚合与 Owner 适配 | 后续新增管理适配与展示模型；不拥有协议执行、配置业务事务 | 不直接读 Payment DB；不因现有 Gateway 编辑器可用而复用它的业务合同 |
| Operations Web | Channel 树、API 编辑、方案选择、影响/错误提示 | 后续设计页面与复用组件，数量暂不编造 | 必须解释值的来源与未实现能力；没有发布页，保存直接生效 |
| Platform / Gateway | 管理入口的 Console Resource、Permission、Target/Route 注册 | 待管理 API/页面定稿后评估注册 DML；不新增核心协议执行逻辑 | 原 Gateway 配置的一致性方案不自动适用于 Channel；不混淆两类 Channel 术语 |
| Commerce / Finance / Risk / CCT | 各自保留退款资格、账务、风险及目录 Owner | 本例无需修改跨系统业务合同；额外查询如涉及某 Owner，再单独纳入 | 不从通用查询直接跨库获取数据，不发布新的退款状态事件 |
| 数据模型与运行环境 | 非敏感协议配置与运行材料的存放、最小原交易导航事实 | 下一阶段列明确切模型/DDL/DML；本轮为 0 | 不执行 SQL，不自动种表，不把沙箱域名视为环境授权 |
| 需求目录与验证 | 保存本例、差异和后续决定 | 本轮只增加本需求文档与合成样例，更新需求索引 | 权威入口互相链接；应用当前状态仍只由台账维护 |

## 4. 必须区分的决定与待验证事项

**已经确定的方向：** 单一支付方式归属、Channel 多套公共方案、输入上下文加注册查询、保存直接生效、标准 API 编排暂缓。NI 样例没有推翻这些方向。

**下一阶段需要裁定的设计问题：**

1. 非敏感协议定义与 Endpoint/超时分别存在哪里，如何修订或满足现行数据库边界。
2. 原交易的渠道定位事实由谁保存和查询，哪些已有字段可复用，哪些确实缺失。
3. 注册查询与 Channel 执行的公开合同，如何保持模块独立并区分准备失败和已发送。
4. Operations 的编辑结构、保存校验、共享方案影响展示及 Owner/BFF HTTP 合同。

**需要 NI 材料或联调证据、不能靠架构选择代替的事项：** 本次退款唯一关联、幂等支持、错误码/拒绝语义、超时后的可靠查询、金额/报文上限。公开页面足够构造本例请求，尚不足以宣布真实资金闭环可接入；不用“每次响应只有一条”“总取最新”等假设替代这些证据。

工作量主要来自执行器的受控取值与类型/字节边界、Owner 查询与协议事实、保存一致性和响应关联；Operations 表单只是其中一部分。本轮未做实施排期，不给未经拆解的天数估算。

## 5. 本阶段交付与下一步

本阶段交付[NI 退款完整逻辑配置与过程图](./ni-refund-example.md)、[合成输入输出](./assets/ni-refund-fixture.json)以及本文差异/影响分析。它们支撑概念评审，不能证明配置引擎或真实退款已实现。

用户接受本阶段结果后，建议进入“方案、接口与数据设计”：先裁定存储与原交易查询合同，再设计 API/方案模型、Operations 编辑流程和管理接口，列出必要的数据变更与研发验证计划。标准 API 编排、应用研发、SQL 执行、部署与提交仍不在此次阶段切换中自动获授权。确认与验证证据统一见[交付记录](./delivery.md)。
