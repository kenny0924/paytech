# payout：收款账户与余额解释（结算出款切片 S1）· 需求

> 状态：**阶段 1 已确认（2026-10-30，Q-1—Q-4 按建议）；按阶段 2 S-01 / S-03 / S-04 / S-05 / S-07 修订 R7 / B6 / B9 / AC-04 / AC-08 / AC-09，按阶段 3 修订 B2（标注"阶段 2 / 3 修订"）**。来源：[2026-10-30 payout 结算出款决定](../../../agent-docs/decisions/2026-10-30-payout-settlement-payout-model.md)（P1、P2、P11、P12）与讨论稿 [design-discuss/payout](../../../design-discuss/payout/design.md)（第 8.1 节 `t_beneficiary_account`、第 9 节、第 10 节 S1）；画板 `payout.pen` 01 / 02。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- payout 服务只有 2026-08-28 从 Finance 切出的七表模型（`paytech/payout/docs/database/payout.pdma.json`）与 Schema SQL，没有代码，`mor_payout` 未创建。
- client-activation（[2026-10-28 决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)）把"收款账户 VERIFIED"列为出款能力的运行期前置（P5 阶段 5.1，缺口 P0）：Client 添加收款账户、运营 Maker / Checker 核验户名 = 法定名；没有它，后续出款单无法创建。
- account 已有 Owner 余额桶（PENDING / AVAILABLE / LOCKED）、保证金与追偿应收，并提供 `BalanceQueryApi`（按 Client / Partner 查余额），但 Client 门户没有"我有多少钱可以提、什么时候能提"的解释页。
- 结算出款整体按切片推进（决定第 3 节）：S1 收款账户与余额解释 → S2 结算出款（自有银行人工渠道）→ S3 服务商 API 渠道 → S4 出款计划 → S5 结算单 / 看板。本需求是 S1。

## 2. 目标

1. Client 能在门户添加、查看、设默认、停用收款账户；运营能核验（Maker / Checker 不同人）、拒绝、停用。
2. 收款账户原值（户名、账号、IBAN 等）只进 Vault；库内只有 Token、脱敏值、末四位、盲索引与状态。
3. 核验通过后进入冷却期，冷却结束才可用于出款；每次状态变化通知 Owner。
4. Client 门户与运营控制台能解释 Owner 在某 OE × 币种下的余额：PENDING / AVAILABLE / LOCKED、保证金、追偿应收，以及当前为什么不能提款。
5. 建立 payout 服务工程骨架（api / core / app / test、`mor_payout`、注册与语言包），供 S2 直接在其上实现出款单。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| Client（门户） | 首次添加 AE 本地 USD 收款账户（户名、银行、IBAN） | 账户进入"待核验"，看到预计核验时限；核验通过后显示"冷却中，HH:MM 后可用"，到时变"可用" |
| Client | 输错账号后重新添加 | 旧账户可停用；新账户重新核验与冷却 |
| Client | 有两个同币种账户 | 可设一个默认账户，门户与将来的出款计划预选它 |
| Client | 查看余额页 | 看到按 OE × 币种的 PENDING / AVAILABLE / LOCKED、保证金、追偿应收，以及"当前不能提款的原因"（无可用账户 / 账户冷却中 / 结算控制 / 追偿未清 / 开通项未生效） |
| 财务运营（Maker） | 核验队列里打开一条账户 | 看到脱敏户名、末四位、开户国家 / 币种 / 银行、法定名（来自 client-master）、同账户是否被其他 Owner 使用（盲索引）；提交核验意见 |
| 财务运营（Checker） | 复核 Maker 的意见 | 不同人；批准 → VERIFIED 并开始冷却；拒绝 → REJECTED 带原因，通知 Client |
| 运营 | 风险事件后停用某账户 | SUSPENDED 带原因；Client 门户显示不可用及原因；可恢复（重新核验） |
| 运营 | 代 Local Partner 维护收款账户 | 同一页面，Owner 类型为 LOCAL_PARTNER（Partner 门户本期不做） |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | payout 服务工程骨架：`paytech/payout` 四模块（api / core / app / test）、`mor_payout` 库与账号、本地与 mor-test 环境接入（端口 18117，服务名 `payout-service`）、健康与管理端口、arch-rules 表归属、instance-manifest |
| R2 | 收款账户模型与 Schema：`t_beneficiary_account`（按 design.md 8.1：现有 23 列 + `cooling_until`、`is_default`、`suspended_reason`）、`t_compensation_event`、消费幂等标准表；`t_payout_event` 本期用于记录收款账户状态变化（`event_scope=BENEFICIARY`，阶段 2 核对是否复用或另建） |
| R3 | Vault 接入：收款账户原值 Protect（Finance 专属策略 / 用途登记到 `mor_vault`）、脱敏值与末四位、按 Owner × 字段 × 用途隔离的盲索引；运营核验页的 Reveal（限核验角色、留审计） |
| R4 | 收款账户 Processor：Client / 运营提交（DRAFT → PENDING_REVIEW）、Maker 意见、Checker 批准 / 拒绝（不同人）、冷却、设默认、停用 / 恢复、关闭；冻结字段变化必须新建账户 |
| R5 | 收款账户字段要求：按开户国家 × 币种 × 轨道的必填字段与格式（AE / SA / EG 本地 + SWIFT：IBAN、SWIFT BIC、本地银行代码、账户类型），**本期由 payout 内置静态清单**，S3 改由 channel-connector 出款账户能力提供（Q-1） |
| R6 | 余额解释接口：`GET /payout/v1/balances-explanation?ownerType&ownerId&entityNo&currency` → 各桶余额（读 account `BalanceQueryApi`）、保证金与追偿、可用收款账户、当前阻断原因清单（开通项未生效 / 无可用账户 / 冷却中 / 结算控制 / 追偿未清）；阻断判定复用 S2 的门禁逻辑雏形 |
| R7 | 通知：核验通过（含可用时间）/ 拒绝、停用、关闭，邮件（经 Platform 通知）；收件人由 Platform 按"Client / Local Partner 租户内具备收款账户管理权限的有效成员"解析（阶段 2 S-05 修订，Platform 通知新增收件人类型）；冷却结束不单独通知（S-07）；Webhook 随 S2（Q-2） |
| R8 | Client Console：收款账户页（列表、添加向导按国家 / 币种动态字段、状态与冷却倒计时、设默认、停用）、余额页（按 OE × 币种卡片、桶解释、阻断原因与去处理链接）；Client BFF 转发与聚合 |
| R9 | Operations Console：收款账户核验队列与详情（Maker / Checker 双栏、Reveal、同账户被共用提示、拒绝原因）、按 Owner 查看账户与余额解释；Operations BFF 转发 |
| R10 | Platform 注册块（路由、菜单、权限：`PAYOUT_BENEFICIARY_REVIEW` 等）、语言包（client-console / operations-console 命名空间）、Gateway 快照离线演练 |

## 5. 不做项

- 出款单、审批、执行、与 account 的锁定 / 占用、Risk 检查点、billing 报价与事实、reconciliation 对账（S2）。
- channel-connector PAYOUT 渠道、出款账户、路由策略（S3）；本期字段要求为过渡静态清单。
- 出款计划（S4）、结算单与看板（S5）、Partner 门户、代发收款人（PAYEE）。
- 收款账户的开放银行 / 微存款验证、户名自动匹配供应商；本期核验为人工（Maker / Checker 对照法定名）。
- 退回处理、`failure_class=PERMANENT` 自动停用（随 S2 / S3）。

## 6. 业务规则

| # | 规则 |
|---|---|
| B1 | `owner_type ∈ {CLIENT, LOCAL_PARTNER}`（`PAYEE` 预留，不开放）；Client 只能看、改自己命名空间的账户；运营可代操作任一 Owner |
| B2 | 状态（阶段 3 修订：提交即 PENDING_REVIEW，不使用 DRAFT）：PENDING_REVIEW → VERIFIED / REJECTED；VERIFIED → SUSPENDED（可恢复 → PENDING_REVIEW 重核）→ CLOSED；REJECTED / CLOSED 为终态。提交时冻结 `content_hash`；冻结字段（户名、账号、银行、国家、币种）变化必须新建账户 |
| B3 | Maker 与 Checker 必须是不同 Platform 账号；Checker 审阅的 `approved_content_hash` 必须等于当前 `content_hash`，否则拒绝并要求重新审阅 |
| B4 | 冷却：VERIFIED 时 `cooling_until = decision_time + cooling_hours`（配置，默认 24 小时）；`now < cooling_until` 的账户"可见不可用"；S2 创建出款单时校验 |
| B5 | 默认账户：同 Owner × 币种至多一个 `is_default=1`；只有 VERIFIED 账户可设默认；停用 / 关闭默认账户时清除默认 |
| B6 | 查重（阶段 2 S-01 修订）：账号规范值的 Vault STABLE Token `bank_account_token`；同 Owner 存在未关闭 / 未拒绝的同 Token 账户 → 拒绝提交；不同 Owner 共用 → 允许提交，核验页醒目提示 |
| B7 | 原值只进 Vault，不进日志、事件载荷、MQ、BCR；库内仅 Token、脱敏值、末四位、盲索引；Reveal 仅限核验角色，每次留审计 |
| B8 | 字段要求按开户国家 × 币种 × 轨道取静态清单；清单缺失的组合不允许提交（提示"该国家 / 币种暂不支持"） |
| B9 | 余额解释只读：各 OE × 币种桶余额、保证金、追偿来自 account 新增的列表接口（S-04）；本期阻断原因只含可判定的四项——无可用余额、无可用收款账户、账户冷却中、追偿未清；"开通项未生效""结算控制"随 S2（S-03）；全部为事务外查询，不缓存 |
| B10 | 每次状态变化写事件（操作人、原因、来源），并发出通知；通知失败不回滚状态（补偿事件重试） |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| payout（新建） | Maven 四模块、Spring Boot 入口、配置、Nacos / Redis / RabbitMQ 接入、`AbstractProcessTemplate` 链路、测试基座（MockTestBase、TestMapper、Builder / Mocker / Checker） | 工程 |
| | `mor_payout`：`t_beneficiary_account`（改造）、`t_payout_event`（或收款账户事件表）、`t_compensation_event`、`t_consumed_event` / `t_consumed_aggregate_version`；PDManer `payout.pdma.json` 直接编辑（不用生成器）；Schema SQL 与初始化 DML | 表 / SQL |
| | `payout-api`：收款账户命令 / 查询、余额解释、字段要求查询的 `@HttpExchange` 合同与请求 / 响应体、错误码 | 接口 |
| | Processor：提交、核验（Maker / Checker）、设默认、停用 / 恢复、关闭、余额解释、字段要求；Domain Service：收款账户、冷却、盲索引；Repository / Mapper | 代码 |
| | 出站客户端：account `BalanceQueryApi`、client-master 法定名查询、commercial 开通项查询、compliance 结算控制查询（若有）、Vault Protect / Reveal、Platform 通知与文件 | 集成 |
| | 内部 Token（`paytech-internal-auth`）调用方白名单、补偿事件处理器、XXL 执行器注册（冷却到期通知） | 配置 |
| account | 无代码改动；核对 `BalanceQueryApi` 响应是否含保证金、追偿与各桶（阶段 2） | 核对 |
| client-master | 无代码改动；核对 Client / Partner 法定名与状态查询接口可供 payout 调用（调用方白名单） | 核对 / 配置 |
| commercial | 无代码改动；核对开通项状态查询接口（`PAYOUT_*` × OE）是否存在，缺则列为 S2 前置 | 核对 |
| compliance | 结算控制查询：现只有 API 合同，本期按"来源缺失"处理或补只读查询（阶段 2 定） | 核对 / 可能代码 |
| Vault | `mor_vault` 新增 payout 收款账户策略 / 用途与调用方授权（DML）；Reveal 审计 | DML |
| Platform | 注册块（Client 路由 ≈ 6、Operations 路由 ≈ 7、菜单 2、权限 2）、通知模板、Webhook 事件目录（Q-2）、语言包两命名空间 | 注册 / 语言包 |
| Client Console BFF / Web | BFF：收款账户与余额解释转发与聚合（法定名、OE 列表）；Web：收款账户页、余额页、入口与导航 | 代码 / 页面 |
| Operations Console BFF / Web | BFF：核验队列、详情、Reveal、按 Owner 查询转发；Web：核验工作台、账户详情、Owner 余额解释 | 代码 / 页面 |
| Gateway | 无代码；注册块激活后快照编译离线演练 | 环境 |
| 环境 | `local-environment` 与 `test-environment` 增加 payout 服务、`mor_payout` 库与账号、Compose 与固定 IP、探针 URL 白名单、XXL 执行器 | 环境 |
| 文档 | `CURRENT_IMPLEMENTATION.md`、`LOCAL_DEVELOPMENT_ENVIRONMENT.md`（payout 行从"预留"改）、`requirements/README.md` 索引、requirements-site FIN-16 / FIN-I16 口径 | 文档 |

## 8. 验收标准

**功能验收**

| # | 验收 |
|---|---|
| AC-01 | Client 添加 AE / SA / EG 本地与 SWIFT 账户：按国家 × 币种显示必填字段与格式校验；不支持的组合被拒并提示；提交后状态 PENDING_REVIEW，库内无原值 |
| AC-02 | 同 Owner 重复账号提交被拒（盲索引）；不同 Owner 共用账号可提交，核验页显示共用提示 |
| AC-03 | Maker 提交意见后 Checker 为同一人时拒绝；不同人批准 → VERIFIED，`cooling_until` = 决定时间 + 配置小时；拒绝 → REJECTED 带原因 |
| AC-04 | 冷却期内账户在 Client 门户显示"冷却中，可用时间"，余额解释的阻断原因含"账户冷却中"；到期后按 `cooling_until` 实时判定为可用（无需任务、无需人工） |
| AC-05 | 设默认：同币种只有一个默认；停用默认账户后无默认；只有 VERIFIED 可设默认 |
| AC-06 | 停用 / 恢复 / 关闭：状态与原因正确，恢复后重走核验与冷却；冻结字段修改被拒并提示新建账户 |
| AC-07 | Reveal 仅核验角色可用，非授权角色 403，每次 Reveal 有审计记录 |
| AC-08 | 余额解释按 OE × 币种返回各桶余额、保证金、追偿与阻断原因清单；无可用余额、无可用账户、冷却中、追偿未清四种原因各能单独触发并正确文案 |
| AC-09 | 通知：核验通过（含可用时间）/ 拒绝、停用、关闭各发出邮件给该 Owner 租户内具备 `CLIENT_PAYOUT_ACCOUNT_MANAGE`（Partner 为对应权限）的有效成员（mor-test 邮件沙箱可见）；无收件人时记录并跳过；通知失败不影响状态并有补偿重试 |
| AC-10 | 所有写接口幂等（重复提交同内容返回原账户），版本冲突 409；所有跨服务调用在本地事务之外（测试断言） |
| AC-11 | payout `auto` 测试在本地 Loopback 环境通过，覆盖 AC-01—AC-10 的场景矩阵；无 Mock 行为替换生产链路 |

**视觉 / 交互验收**（页面原型阶段 3 在 `client-console-web/client-console.pen` 与 `operations-console-web/docs/operations-console.pen` 画出并确认后再写代码）

| # | 验收 |
|---|---|
| AC-V1 | Client 收款账户页：列表卡片含银行、末四位、币种、状态徽标（待核验 / 冷却中 HH:MM / 可用 / 已拒绝 / 已停用）、默认标记；添加为分步向导（国家币种 → 字段 → 确认），字段随国家 / 币种变化并就地校验；空状态说明"添加后方可提款" |
| AC-V2 | Client 余额页：按 OE × 币种卡片，桶余额分段条 + 数字；"不能提款的原因"以可处理项列出并链接到对应页面；无账户时直接引导添加 |
| AC-V3 | 运营核验工作台：待核验 / 待复核 / 已处理页签带计数；详情为左右两栏（账户信息与 Reveal 按钮 / 法定名与共用提示 / Maker 意见与 Checker 决定）；拒绝需选原因；同一人不能复核时按钮禁用并说明 |
| AC-V4 | zh-CN / en × 1440 / 390 无未翻译 key、无横向滚动 |

## 9. 已确认事实、假设与待确认

**已确认**：决定 P1、P2、P11、P12；收款账户单表、原值只进 Vault、Maker-Checker 不同人、冷却 + 通知、默认账户。

**假设（阶段 2 核对）**

- A1 account `BalanceQueryApi` 的 Client / Partner 响应已含各桶、保证金与追偿；若缺"即将成熟明细"，S1 只展示桶余额，不展示成熟时间线。
- A2 Platform 已有邮件通知能力可供业务系统调用；若无，S1 先只发 Webhook 事件或记录待发通知。
- A3 client-master 提供按 Owner 查法定名与状态的内部接口，可加 payout 到调用方白名单。
- A4 Vault 支持新增策略 / 用途与调用方授权仅靠 `mor_vault` DML，无需改 Vault 代码。

**待确认**

| # | 问题 | 建议 |
|---|---|---|
| Q-1 | 收款账户字段要求本期内置静态清单（AE / SA / EG 本地 + SWIFT），S3 迁到 channel-connector 出款账户能力；是否接受过渡期双来源？ | 接受；清单放 payout 配置资源文件，S3 切换时清单退役 |
| Q-2 | Owner 通知：邮件为必做，Webhook `beneficiary_account.*` 是否本期加入 Platform Webhook 事件目录？ | 本期只做邮件，Webhook 随 S2 的 `payout.*` 一起登记 |
| Q-3 | 冷却时长默认 24 小时，是否区分 Client / Partner 或按金额免冷却？ | 统一 24 小时，可配置；不按金额免冷却 |
| Q-4 | 运营代 Local Partner 维护收款账户是否本期包含？ | 包含（同一页面选 Owner 类型），Partner 门户不做 |
