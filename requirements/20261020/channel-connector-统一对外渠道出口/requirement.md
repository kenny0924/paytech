# channel-connector：统一对外渠道出口

> 状态：阶段 1 已确认（2026-10-20，"按建议，继续"：Q1–Q8 全部采用建议）。决定记录 [2026-10-20 统一对外出口](../../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)；确认记录见 [delivery.md](./delivery.md)，方案见 [technique.md](./technique.md)。
>
> 来源：2026-10-20 讨论。用户目标是"统一管理对外渠道的请求，这样会更安全"，并决定由 channel-connector 承担，渠道分为支付渠道与非支付渠道；用户认可后要求直接出需求，不另写 design-discuss。
>
> 将被取代的现行依据：[2026-09-21 Platform Integration 归属](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)、[2026-10-13 标准 API 与流程](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)（归属部分）、[2026-10-13 Platform 读取渠道账号](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)、[2026-09-13 Channel 术语](../../../agent-docs/decisions/2026-09-13-payment-channel-terminology.md)（Channel 仅指支付渠道的边界）。外部 API 的流程、原子 API、脚本、沙箱验证等**能力合同不变**，只换 Owner，见 [外部 API 配置与执行](../../20260914/platform-support-外部API配置与执行/requirement.md)与 [渠道沙箱验证](../../20261014/platform-support-渠道沙箱验证/requirement.md)。

## 1. 背景

现在对外请求分散在两个系统，且没有唯一出口：

- **外部 API 配置与执行在 Platform**：`paytech-platform-support-integration` 模块（237 个 Java 文件）拥有 10 张表——标准 API、共用字段、接入方、原子 API、公共脚本、标准 API 实现，以及沙箱验证的账号 / 用例 / 运行 / 步骤；执行引擎、出站 HTTP 客户端 `ChannelHttpClient`、卡直付读卡客户端都在 Platform 进程内，已部署 mor-test。
- **支付渠道业务身份在 channel-connector**：渠道、技术账号、MID、能力、渠道成本；执行时先校验冻结三元组，再经 HTTP 调 Platform 标准 API；激活渠道前经 Platform `IntegrationLookupApi` 核对同编码接入方。
- 结果：一次渠道调用要走 `Payment → channel-connector → Platform → 外部`，两段超时；渠道编码与接入方编码跨两个库对齐；渠道账号 Token 归 channel-connector，明文却由 Platform 按 10-13 专项例外 Reveal；卡原值也进入 Platform 进程。
- 非支付的外部服务（如 Google Map）没有归属：Channel 术语只指支付渠道，Platform 接入方没有凭据模型，业务系统只能自己写 HTTP 客户端直连外部，出口进一步分散。

## 2. 目标

1. **一个出口**：系统所有面向外部第三方的业务调用都由 channel-connector 发出；其他系统不直接请求外部第三方（已登记的例外除外）。
2. **渠道统一**：渠道分为两类——**支付渠道**（`PAYMENT`，NI、Geidea、PayerMax 等）与**服务渠道**（`SERVICE`，Google Map、KYC 筛查、短信等，命名见 Q1）。两类共用一套渠道身份、协议配置、凭据保管和执行引擎；支付渠道另有技术账号与 OE 绑定、MID、能力、成本。
3. **更安全**：凭据只在 channel-connector 内还原；调用方只能调用被授权的渠道与标准 API；支付渠道不能被通用入口绕过冻结坐标校验；卡原值的处理范围从 Platform 收缩到 channel-connector。
4. **少一跳**：支付执行变为 `Payment → channel-connector → 外部`。

## 3. 使用者与场景

| 使用者 | 场景 |
|---|---|
| 运营 / 接入人员 | 在控制台「渠道」下登记支付渠道或服务渠道，维护其协议配置（标准 API、原子 API、脚本、流程）、凭据、沙箱验证；支付渠道再维护账号、MID、能力、成本 |
| Payment | 不变：以冻结三元组调 `ChannelOperationApi`，channel-connector 直接执行，不再转 Platform |
| 其他业务系统（如 Commerce 地址校验） | 调 channel-connector 通用执行入口，指定服务渠道与标准 API（如 `GEOCODE`），拿到 `executeStatus + outputs`；看不到凭据 |
| 安全 / 审计 | 能列出每个渠道允许哪些调用方调用哪些标准 API；能从结构化日志追到每次对外调用的调用方、渠道、标准 API、结果与耗时 |

## 4. 范围

### A. Owner 迁移（Platform → channel-connector）

| 编号 | 内容 |
|---|---|
| A1 | 外部 API 配置 10 张表、管理接口 E01～E30、沙箱验证 V01～V14、执行引擎、注册函数、出站 HTTP 客户端、读卡客户端整体迁入 channel-connector；能力与合同语义保持不变 |
| A2 | 接入方与渠道合并为同一个身份：一个渠道一个编码、一行身份记录，不再跨库按编码对齐；表结构合并方式见 Q3 |
| A3 | `ChannelOperationApi` 改为进程内执行，删除 channel-connector → Platform 的标准 API 客户端与 `IntegrationLookupApi`；渠道激活前检查改为本库检查协议配置 |
| A4 | Platform 删除 integration 模块、10 张表（数据迁移后）、`StandardApiCommandApi` / `IntegrationLookupApi` 合同、执行与读卡配置 |
| A5 | 本机与 mor-test 已有配置数据（含 PayerMax、沙箱用例与账号）迁到 `mor_channel_connector`，迁移前后逐表核对 |

### B. 渠道分类与服务渠道

| 编号 | 内容 |
|---|---|
| B1 | 渠道增加类型 `PAYMENT` / `SERVICE`，创建后不可改 |
| B2 | 服务渠道没有 OE、MID、能力、成本；有协议配置、凭据与状态（DRAFT → ACTIVE ⇄ SUSPENDED → CLOSED，同支付渠道） |
| B3 | 服务渠道的凭据由 channel-connector 在事务外 Protect 到 Vault、只保存 Token；控制台只写不读，显示"已配置（末 4 位）"；凭据模型见 Q4 |

### C. 调用入口与授权

| 编号 | 内容 |
|---|---|
| C1 | 支付入口：现有 `ChannelOperationApi`，仅 Payment 可调，只接受 `PAYMENT` 渠道 |
| C2 | 通用入口（新增）：调用方给出渠道编码、标准 API 编码与输入，返回 `executeStatus + outputs`；只接受 `SERVICE` 渠道，`PAYMENT` 渠道一律拒绝且不发出请求 |
| C3 | 调用授权：按"调用方服务 × 渠道 × 标准 API"白名单放行，未授权拒绝且不发出请求；调用方身份取自现有内部认证；白名单的维护方式见 Q5 |
| C4 | 隔离：支付与服务两类渠道的出站连接池 / 并发上限分开，服务渠道的超时或积压不占用支付渠道的执行资源 |

### D. 凭据、Vault 与 Card

| 编号 | 内容 |
|---|---|
| D1 | 支付渠道账号 Token 由 channel-connector 自己 Reveal（Owner 与使用方合一），撤销 Platform 的 `CHANNEL_EXECUTION` Reveal 例外 |
| D2 | 沙箱验证账号、服务渠道凭据的 Protect / Reveal 规则改为 `channel-connector-service` |
| D3 | card-service 读卡接口的允许调用方由 Platform 改为 channel-connector；读卡、内存使用、不落日志的约束原样迁移；Card 边界在阶段 2 重新逐项核对，不沿用旧结论 |

### E. 出口唯一性与例外

| 编号 | 内容 |
|---|---|
| E1 | 盘点全仓库出站调用，登记"唯一出口例外清单"（已知：Platform 通知 SES/SQS、文件 S3、Client Webhook 推送、Vault/KMS 与云 SDK）；每项写明为何不经 channel-connector 及其自身的安全控制 |
| E2 | 新增架构门禁：除 channel-connector 与例外清单内的模块外，业务服务不得新增面向外部第三方的出站客户端（具体实现方式阶段 3 定） |
| E3 | 对外调用写结构化访问日志：调用方、渠道、标准 API、`executeStatus`、HTTP 状态、耗时；不含报文与凭据 |

### F. 运营控制台

| 编号 | 内容 |
|---|---|
| F1 | 「外部 API」两页（标准 API、接入方）与沙箱验证并入一级菜单「渠道」；渠道列表区分类型，支付渠道详情含账号 / MID / 能力 / 成本，两类渠道详情都含协议配置、凭据与沙箱验证 |
| F2 | Operations BFF 的外部 API 30 条转发与沙箱验证转发改指 channel-connector；Platform 注册块中相应菜单、权限、Route 迁移或重新登记 |

### G. 文档与决定

| 编号 | 内容 |
|---|---|
| G1 | 新增决定记录，写明取代上述四条决定的范围；同步 AGENTS.md 路由、PROJECT_CONTEXT、APPLICATION_ARCHITECTURE、Platform / channel-connector 数据库文档、Vault 架构顶部例外、术语 |

## 5. 不做

- 外部 API 的能力变化：流程、原子 API、脚本、字段、沙箱验证的规则与合同语义不变，"冲正等资金反向操作不在流程中编排"等约束原样保留。
- 接入任何具体服务渠道（包括 Google Map）；本期只交付服务渠道的能力，用合成渠道验收。
- 网络层出口强制（安全组 / 网络策略 / 出口代理）：涉及所有服务与部署，本期只做代码层唯一出口与门禁，网络层另立需求（见 Q6）。
- 把例外清单中的 SES、S3、Webhook、Vault/KMS 迁入 channel-connector。
- 入站渠道回调（Gateway `CHANNEL_CALLBACK`）的归属与路由。
- 执行流水落库、自动重试、跨调用续跑（沿用"不保存执行记录"）。
- 路由策略、健康度；支付渠道账号 / MID / 能力 / 成本的规则变化。
- 浏览器端直接调用的第三方前端 Key（如地图 JS SDK），不属于服务端出口。

## 6. 已确认事实与假设

已确认（用户，2026-10-20）：

- 由 channel-connector 统一管理对外渠道请求；渠道分支付与非支付两类。
- 外部 API 配置与执行从 Platform 迁入 channel-connector。

已确认（代码与台账）：

- Platform integration 模块 10 张表、执行引擎、沙箱验证已实现并部署 mor-test；channel-connector 已通过 HTTP 调用 Platform 标准 API 与 `IntegrationLookupApi`。
- Platform 执行卡直付时以一次性票据向 card-service 读卡，卡原值只在内存使用。
- 支付渠道账号凭据为 `CHANNEL_ACCOUNT_CONFIG_V1`，由 channel-connector Protect，Platform 以 `CHANNEL_EXECUTION` 用途 Reveal。

假设（待确认）：

- 迁移一次完成，不设 Platform 与 channel-connector 双写或并行执行的过渡期；切换窗口内渠道执行短暂不可用可以接受（本机与 mor-test 均无真实交易）。
- Operations BFF 的外部 API 路径保持不变，只换后端，前端改动集中在菜单与渠道详情。

## 7. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 非支付渠道的名称 | **服务渠道 / `SERVICE`**。用正面名称而不是"非支付"，以后再加第三类不别扭；覆盖地图、KYC、短信、汇率等第三方服务；"工具渠道"容易被理解为内部开发工具 |
| Q2 | 术语调整：Channel 是否改为"任何外部第三方对接方"，Payment Channel 是其子类 | 是。现有表已叫 `t_payment_channel`、业务文档已用"支付渠道"，影响面小；Platform 访问 Channel（OPERATIONS/CLIENT/PARTNER）与 Route Channel 是其他含义，保持不变并在决定中注明 |
| Q3 | 接入方与渠道合并方式：① 合并为一张渠道表（含类型、状态、出站配置），支付扩展挂在其下；② 保留两表，支付渠道引用接入方 | **①**。一个渠道一个身份，消除编码对齐；具体表名与成本、MID 等引用列的迁移在阶段 3 设计 |
| Q4 | 服务渠道凭据挂在哪里：① 复用技术账号表，`entity_no` 对服务渠道为空，每个服务渠道限一个 ACTIVE 账号；② 凭据直接放在渠道上 | **①**。复用现有 Protect / Reveal / 控制台账号页，将来需要多账号时不用改模型 |
| Q5 | 调用授权白名单的维护方式：① 存表、控制台维护、同事务留痕；② 配置文件 | **①**。接新服务渠道不需要发版，且授权变更可审计 |
| Q6 | 网络层出口强制是否纳入本期 | 不纳入，另立部署需求；本期交付例外清单与代码门禁作为其输入 |
| Q7 | 是否把 Platform 通知（SES）、文件（S3）、Client Webhook 也收进 channel-connector | 本期不收，登记为例外。三者是 Platform 自有能力且已有专项安全控制；Webhook 目标是 Client 自己的地址，不是第三方渠道 |
| Q8 | 阶段安排：迁移（A、D、F）与新能力（B、C、E）是否分两段研发 | 分两段：先等价迁移并在 mor-test 回归 PayerMax 沙箱与支付执行，再加服务渠道与通用入口，便于定位问题 |

## 8. 验收标准（草案）

功能验收：

- AC-01 Platform 不再含 integration 模块、10 张表、标准 API 执行与读卡客户端；channel-connector 不再依赖 Platform 标准 API 或 `IntegrationLookupApi`。
- AC-02 迁移后 Payment 支付执行经 channel-connector 直接发出外部请求，结果与迁移前一致；PayerMax 沙箱验证用例在 mor-test 全部重跑通过。
- AC-03 迁移前后配置数据逐表核对一致（行数与内容摘要），沙箱验证历史可查。
- AC-04 可在控制台新建服务渠道、配置协议与凭据并激活；类型创建后不可改；服务渠道没有 MID、能力、成本入口。
- AC-05 已授权调用方经通用入口调用服务渠道的标准 API 得到 `executeStatus + outputs`；未授权调用方、未授权标准 API、非 ACTIVE 渠道均被拒绝且外部请求数为 0。
- AC-06 通用入口调用支付渠道一律拒绝且外部请求数为 0；`ChannelOperationApi` 调用服务渠道一律拒绝。
- AC-07 服务渠道凭据库中只有 Token，接口、日志、响应不出现明文；Vault 中 Platform 对渠道账号的 Reveal 规则已撤销，channel-connector 规则生效。
- AC-08 card-service 只允许 channel-connector 读卡；Platform 读卡被拒绝。
- AC-09 服务渠道外部超时或并发打满时，支付渠道执行不受影响（自动测试证明连接池 / 并发隔离）。
- AC-10 每次对外调用有一条结构化访问日志，字段齐全且不含报文与凭据。
- AC-11 例外清单已登记；架构门禁在业务服务新增外部出站客户端时失败。
- AC-12 channel-connector、Platform、Payment（如受影响）、BFF、Web 自动测试与构建全绿；文档与决定同步。

视觉 / 交互验收：

- AC-V01 「渠道」一级菜单下渠道列表可按类型筛选；原「外部 API」入口移除，旧页面能力在渠道详情内完整可用。
- AC-V02 服务渠道详情只显示适用的页签；凭据只写不读、显示末 4 位、可重新填写。
- AC-V03 调用授权在渠道详情维护，列表显示调用方、标准 API、状态，变更有留痕。

## 9. 阶段安排（评审稿）

| 阶段 | 安排 |
|---|---|
| 1 | 本评审稿：范围 A–G、不做项、Q1–Q8、验收标准；确认后写决定记录 |
| 2–3 | 差异分析与设计合一：出站调用盘点与例外清单、表合并与引用迁移、数据迁移 SQL、Vault / Card 规则变更、通用入口与授权合同、隔离方案、门禁、BFF / Web / 注册数据变更、研发与验证计划 |
| 4 | 研发分两段：第一段等价迁移（A、D、F），第二段服务渠道与通用入口（B、C、E）；执行者 Opus 5.5 agent，主会话设计与把关 |
| 5 | 本机验证：各服务自动测试与构建，本机 e2e 支付执行与沙箱验证回归，服务渠道合成用例 |
| 6–7 | mor-test：备份、数据迁移、Vault / Card 策略、镜像替换（channel-connector、platform-support、card、ops-bff、ops-web）、注册数据、回归 |
| 8 | 私有索引本地提交 |
