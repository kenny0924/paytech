# 阶段与确认记录

> 当前支付提交顺序已按用户确认修订为 Payment 单请求；最新接口、数据与确认记录见[Payment Card 交付记录](../payment-自建CardVault/delivery.md)。下列先前评审/验证记录保留历史语境，不作为旧 Owner 或两请求方案继续实施的依据。

## 当前实施授权

2026-09-12 用户明确要求“先完成 Commerce 的接口及功能，需要调用外部接口先 Mock + TODO”，随后单独授权备份并执行本机三表增量 SQL。此授权只进入 Commerce 代码、测试与对应模型/文档切片，不扩展到 Payment、Risk、Gateway、前端或部署。

本切片的[接口与实施说明](../../../paytech/acquiring/docs/requirements/20260912-customer-checkout/implementation.md)、[可执行 OpenAPI](../../../paytech/acquiring/docs/api/customer-checkout.openapi.json)和[状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-customer-20260912)承接当前工作。下方“尚未改代码/SQL”的描述只保留其设计阶段的历史语境。

## 原设计阶段范围

2026-09-12 用户表示“页面我没啥问题，考虑一下接口如何设计”。据此记录页面认可，并开展本次单阶段接口设计讨论；不将其解释为接口方案、业务实现、模型/SQL、入口配置或部署授权。

当前产物：[需求与验收目标](./requirement.md)、[四个接口边界](./technique.md)、[HTTP 字段合同](./customer-api-contract.md)、[接口影响分析](./interface-impact.md)、[NI 接入补充](./ni-provider-contract.md)、[服务端方案](./implementation-design.md)。公共字段已认可，本轮完成首个渠道及技术方案补充；新数据和 NI 执行细节尚未全部定稿，不进入未明确范围的多仓库实现/SQL/部署。

## 确认记录

| 日期 | 用户原意 | 认可范围 | 接续动作 |
|---|---|---|---|
| 2026-09-12 | 页面没有问题，考虑接口如何设计 | [现有 Checkout 页面](../../../checkout-web/docs/design-index.md) | 研究现有接口/Owner 决策，提交接口草案 |
| 2026-09-12 | 可以，没问题 | 上一回合展示的 8 个接口职责、准备/初始化/接受/支付顺序及基本不变量 | 细化请求响应、错误、并发恢复与实际 Owner 能力，不将待决字段视为已经确认 |
| 2026-09-12 | 对 Customer 版本字段、文档链接、支付时同意、Card 初始化和动作恢复提出五项复核 | 撤回将前版 8 操作视为固定实施清单的假设；尚未确认全部替代细节 | 完成[简化复核](./simplification-review.md)，先解释合并与必要能力，再按用户决定修订合同；不修改页面或业务代码 |
| 2026-09-12 | 可以，设计接口字段 | 确认四个基础操作及去掉 Customer 版本、文档链接、Pay 时确认的简化方向；Card/3DS 辅助能力依实际 Provider 判断 | 本轮提交四操作字段表、JSON 示例、条件必填、状态和错误；不把新增字段细节或未知 Provider 分支记为已获确认 |
| 2026-09-12 | 可以，下一步 | 认可上一轮公共字段并要求继续；不再重复公共字段评审 | 补齐首个渠道字段、Commerce 处理流程、内部依赖与数据实施边界 |
| 2026-09-12 | NI（Network International） | 确认首个 Card Provider 为 NI | 核对 N-Genius Hosted Session/3DS/查询恢复，补充具体类型；不代表已提供凭据、账户映射或批准沙箱/生产交易 |
| 2026-09-12 | Commerce 为什么关注 NI 渠道数据 | Commerce 只负责订单/法律和 Payment 统一契约，渠道细节归 Payment | 澄清卡数据直接进入受控采集方，不经 Commerce 转发 |
| 2026-09-12 | 先做自己存卡吧，后面应该会有 PCI 资质 | 自建存卡方向，NI 继续承担首个收单渠道 | 登记[自建 Card Vault 需求](../payment-自建CardVault/requirement.md)；本目录的 Hosted Session/NI session_id/绑定假设标记受影响，不按旧方案直接实现 |

公共字段/限额按本次确认保留。Consumer 引用可空及 Payment 采集绑定载体仍是数据建议；账单国家允许组合仍需明确 Owner 校验。NI configuration 和 session_id 映射已有具体提案，setupId 在 NI 下必填的补充、最小 3DS 投影、首次响应丢失与无 orderRef 的关联恢复尚需专项评审/沙箱核验，不能冒充完整可执行 OpenAPI。

## 验证与遗留

当前累计八份需求设计文件及原有架构/需求索引中的对应段落。未修改应用代码、Pencil 页面、数据库模型或 SQL，未提交、推送、注册路由或部署。具体实现、验证、部署事实继续以 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 及其组件证据为准。

前版八接口字段检查（历史证据，不证明当前稿）：`npm --prefix requirements-site run test:content` 71/71 通过；当时五份 Markdown 的 26 个本地链接有效，9 个 JSON 块可解析，8 个 Method/Path 一致，相关已跟踪差异 `git diff --check` 通过。没有运行完整网站构建、Java 测试或真实 Owner 联调。

上一轮四接口字段静态验证：`npm --prefix requirements-site run test:content` 71/71 通过；当时六份 Markdown 的 31 个本地文件链接有效，8 个 JSON 示例全部可解析且无 Customer 版本字段；Method/Path 恰为已确认四操作，需求文件无行尾空白；三个相关已跟踪文件的 `git diff --check` 通过。仅验证文档内容/结构，未运行完整网站构建、Java 测试或真实 Provider 联调。应用架构、需求决定及 E2E-02 摘要同步为四操作；Legacy 消息保留并明确标识，不改写为当前新写链。

本轮 NI/服务端技术补充检查：八份 Markdown 的 53 个本地文件链接有效，9 个 JSON 示例可解析且无 Customer 版本字段，公开操作仍为四个；`npm --prefix requirements-site run test:content` 71/71 通过，相关已跟踪文件 `git diff --check` 通过。Commerce 和 Payment 独立仓库工作区均干净。本轮只读取得官方 Sandbox SDK 并检查参数，不执行 SDK/支付；证据 SHA-256 记录在 NI 补充中，外层脚本检查不代表 3DS iframe 已验证。未运行 Java、数据库或真实 NI 联调。

下一评审入口转至[自建卡库范围与技术边界](../payment-自建CardVault/technique.md)。订单/消费者/法律字段保留；卡输入、初始化、Token 与 Provider 执行部署边界重新设计。历史 NI 沙箱门槛不能再当成 Commerce 查询/资料接口的前置条件；各段依赖缺失时明确不可用，不做业务 Mock 成功分支。
