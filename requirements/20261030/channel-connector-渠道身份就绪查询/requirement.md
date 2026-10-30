# channel-connector：渠道身份就绪查询 · 需求

> 状态：**阶段 1 已确认（2026-10-30，Q-1—Q-4 按建议）**。来源：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)第 ② 条与契约附件 3.2 / 3.3（REQ-02）；上游讨论 [design-discuss/client-activation](../../../design-discuss/client-activation/requirement-plan.md) 第 7 节 N5、第 14 节。前置需求 [REQ-01 开通项前置门禁](../../20261028/commercial-开通项前置门禁/requirement.md)。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 technique.md。

## 1. 背景

- REQ-01 已让合同开通项先处于 `PENDING_PREREQUISITES`，核对 ① 资格、② 合同价格、④ 渠道身份后才 ENABLED，失效后自动 `SUSPENDED_BY_PREREQUISITE`；ENABLED 每日复核一次，PENDING / 门禁暂停每 5 分钟重查。
- 但 ④ 目前固定为 `SKIPPED`（原因 `CHANNEL_IDENTITY_NOT_CONNECTED`），**按满足处理**。结果是：一个 OE 下没有可用 MID、或 MID 不覆盖 Client 的 MCC，开通项照样生效，Client 下单后在支付路由时才失败（无候选 MID）。
- 运行期选 MID 由 channel-connector `QueryMidCandidatesProcessor` 决定：该 OE 下 ACTIVE 的 MID（且渠道账号、渠道均 ACTIVE），其 ENABLED 能力行与交易条件完全命中（支付方式、MCC 精确；体系、消费者国家、币种精确或 `*`）。**路由策略表（`t_routing_policy*`）目前没有任何代码读取**。
- 决定附件 3.3 还要求 channel-connector 交付流程完成条件查询 `GOLIVE_MID_READY`（N5c MID 录入 / N5d MCC 核对），供 REQ-07 上线流程的节点由数据自动完成。

## 2. 目标

1. **门禁说真话**：开通项只有在"按运行期同一规则，该 OE 下确有能承接这个支付方式的 MID"时才 ENABLED；MID 关闭、能力停用后自动暂停，补齐后自动恢复。
2. **告诉运营差什么**：合同页"还差什么"里，渠道身份一项给出具体缺口（没有 MID、MCC 不覆盖、某币种没有 MID），并能跳到 MID 配置页。
3. **给上线流程用**：交付 `GOLIVE_MID_READY` 查询模板，与门禁 ④ 用同一判定口径，流程节点和门禁不会对不上。

## 3. 使用者与场景

| 使用者 | 场景 | 期望 |
|---|---|---|
| commercial 门禁（系统） | 新开通项待生效 / 重查 / 每日复核 | 一次同步查询得到"就绪 / 不就绪 + 缺口"；查询失败不改变状态 |
| 商务运营 | 合同详情看开通项为何没生效 | 渠道身份一项写明缺什么、在哪补 |
| 渠道运营 | 收到"MID 未就绪"任务（N5c / N5d） | 去 MID 页面录入 MID 或补 MCC 能力，补完后开通项与流程节点自动推进 |
| 上线流程（REQ-07，系统） | N5c / N5d 节点完成判定 | `GOLIVE_MID_READY` 返回满足 / 缺哪些支付方式 |

## 4. 本期范围

| # | 内容 |
|---|---|
| R1 | channel-connector 新增**渠道身份就绪查询**（服务间同步接口，只读）：入参 OE、支付方式、币种、MCC（可批量），返回逐项 `ready` 与缺口原因 |
| R2 | 判定口径与运行期选 MID 一致：复用候选查询的筛选规则（MID / 渠道账号 / 渠道 ACTIVE，能力行 ENABLED，支付方式与 MCC 精确，币种精确或 `*`）；门禁时点不知道的维度（体系、消费者国家）任一取值命中即可 |
| R3 | commercial 把 ④ `CHANNEL_IDENTITY` 从固定 `SKIPPED` 改为真实查询：支付方式取开通项目录的 `payment_method_code`，币种取覆盖该开通项的生效价格币种，MCC 取 Client 在 client-master 的业务分类；查询失败 → `UNKNOWN`（与 ① 同样不改状态） |
| R4 | 发布后对 PENDING / 门禁暂停的开通项立即全量重查一次；ENABLED 的按现有每日复核纳入新口径（见 Q-2） |
| R5 | 交付 `GOLIVE_MID_READY` data-query 模板（数据源 `MOR_CHANNEL_CONNECTOR_REPLICA`，已存在）：参数 OE、支付方式列表、MCC；返回 `satisfied`、`out_missing_methods`、`missing` 原因码；与 R2 同一口径 |
| R6 | 运营控制台合同详情"还差什么"：渠道身份一项显示新原因（无 MID、MCC 不覆盖、某币种无 MID）及参数，提供跳到 MID 页面（带 OE 过滤）的链接；i18n 文案键 |
| R7 | 决定修订：附件 3.2 的判定去掉"MID 在生效路由策略内"（运行期不读路由策略，见 Q-1）；深链路由按现有 MID 页面路径修正 |

## 5. 不做项

- `ChannelIdentityChanged` 事件（决定写明 M1 可不发）：commercial 现有重查 Job（PENDING / 暂停 5 分钟、ENABLED 每日）兜底，见 Q-3。
- 子商户绑定、渠道登记事实（高风险、本地方式、3DS、钱包域名）并入就绪查询：M3（REQ-13 / REQ-15）。
- 出款通道类开通项的 ④：仍为 `NOT_APPLICABLE`（payout S1 尚无出款渠道 / 路由）。
- 路由策略（`t_routing_policy*`）的读取与运营配置；MID / 能力配置页面本身的改动（只加跳转参数，若页面已支持则零改动）。
- 健康度、限额、金额上下限、3DS 模式等运行期因素：不进门禁（运行期由 payment 判定）。
- **已知相关缺口（不在本期）**：结账第一步按 client-master 的 Client 与 OE 关联（`t_client_entity_affiliation`）找 OE，而该关联没有任何写入入口（mor-test 现有关联为预置数据）。REQ-02 做完后，新商户即使开通项生效、MID 就绪，仍会因没有关联而无法下单。已登记为 REQ-20「client-master：Client 与 OE 关联建立」（2026-10-30 用户决定）。

## 6. 业务规则

| # | 规则 |
|---|---|
| B1 | **就绪**：给定 OE × 支付方式 × 币种 × MCC，至少存在一个 MID 满足：MID ACTIVE、所属渠道账号 ACTIVE、渠道 ACTIVE；且该 MID 有一条 ENABLED 能力行，支付方式相同、MCC 相同、币种相同或 `*`（体系、国家不限） |
| B2 | 能力行还须声明至少一种收款动作：`ACTION.PAY` 或 `ACTION.AUTHORIZE` 为 true（与 payment 选路一致：自动请款要 PAY、手动请款要 AUTHORIZE；阶段 2 F-02 修正），否则视为不能承接收款；金额上下限不进门禁 |
| B3 | 缺口原因按"最接近满足"给出一个：`MID_NOT_FOUND`（该 OE 无可用 MID）→ `PAYMENT_METHOD_NOT_SUPPORTED`（有 MID 但无该支付方式能力）→ `MCC_NOT_COVERED`（有该支付方式但 MCC 不覆盖）→ `CURRENCY_NOT_SUPPORTED`（MCC 覆盖但该币种没有）→ `PAYMENT_ACTION_NOT_SUPPORTED`（能力覆盖但未声明收款动作，阶段 3 增补）；原因带参数（OE、支付方式、币种、MCC） |
| B4 | 一个开通项可能被多个币种的价格覆盖：**任一**币种就绪即 ④ 满足（开通项生效），其余不就绪的币种作为提醒列出（`CHANNEL_IDENTITY_PARTIAL`）；全部币种都不就绪才不满足（2026-10-30 Q-4 修订，见 Q-4） |
| B5 | Client MCC 取 client-master 业务分类的主 MCC（`primary_slot`）；Client 没有分类时 ④ 不满足，原因 `CLIENT_MCC_MISSING` |
| B6 | 查询失败、超时或 channel-connector 配置损坏（如悬空关联）→ ④ `UNKNOWN`，不改变开通项状态，下次重查再试 |
| B7 | 就绪查询只读、无副作用；channel-connector 不保存门禁结论，commercial 不缓存就绪结果（每次重查都现场查询） |
| B8 | `GOLIVE_MID_READY` 与 B1—B3 同一口径；模板只读 channel-connector 库，支付方式列表与 MCC 由流程变量作参数传入 |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| **channel-connector** | 就绪查询 Processor（复用候选筛选规则）、`-api` 接口与请求 / 响应模型；按 OE 批量读取 MID / 能力的只读查询（可能新增 Mapper 方法，不加表、不改表） | 代码 / 接口 |
| | `GOLIVE_MID_READY` 模板 SQL（只读，交付文本） | 查询模板 |
| | auto 测试：B1—B3 各原因、通配币种、非 ACTIVE 账号 / 渠道、能力 DISABLED、无收款动作、批量 | 测试 |
| **commercial** | ④ 检查器改真实查询：channel-connector 客户端（新）、币种来源（生效价格）、Client MCC 来源（复用 client-master 客户端）；原因码与参数；去掉 `CHANNEL_IDENTITY_NOT_CONNECTED` | 代码 |
| | 发布后全量重查一次的执行方式（复用现有"重新检查前置" / 重查 Job，阶段 3 定） | 运维动作 |
| | auto 测试：④ 满足 / 不满足 / UNKNOWN 三分支、多币种、Client 无 MCC、状态迁移与事件 | 测试 |
| **client-master** | 新增只读服务间查询：按 Client 返回业务分类（主 MCC）（阶段 2 F-07：现有接口没有服务间 MCC 来源） | 代码 / 接口 |
| **operations-console-web** | 合同详情"还差什么"渠道身份一项：新原因文案、参数展示、跳 MID 页链接；i18n 文案键 | 页面 |
| **operations-console-bff** | 预计无改动（原因码与参数透传）；阶段 2 核对 | — |
| **Platform data-query** | 登记 `GOLIVE_MID_READY` 模板（数据源已存在） | 配置 |
| **Platform Gateway / 注册** | 0（就绪查询是服务间内部接口，不经 Gateway；页面无新路由） | — |
| **payment** | 无改动（运行期选 MID 规则不变） | — |
| **文档** | 决定修订（去掉路由策略条件、深链路径）；channel-connector 数据库 / 接口文档；CURRENT_IMPLEMENTATION | 文档 |

## 8. 验收标准

### 8.1 功能验收

| # | 验收标准 |
|---|---|
| AC-01 | OE 下有 ACTIVE MID（账号、渠道 ACTIVE），能力行 ENABLED、支付方式与 MCC 相同、币种相同 → 就绪查询 `ready=true` |
| AC-02 | 能力行币种为 `*` 时，任意币种就绪；体系 / 国家为任意值的能力行都计入 |
| AC-03 | 分别构造 B3 五种缺口（无 MID、无该支付方式、MCC 不覆盖、币种不支持、未声明收款动作）→ 返回对应原因码与参数 |
| AC-04 | MID 非 ACTIVE、渠道账号非 ACTIVE、渠道非 ACTIVE、能力行 DISABLED、能力行无收款动作，任一情况该 MID 不计入 |
| AC-05 | 就绪判定与运行期候选查询一致：对同一组 OE × 支付方式 × 币种 × MCC，就绪为 true 当且仅当候选查询（任一体系 / 国家）至少返回一个可收款候选（测试中对照） |
| AC-06 | commercial：新开通项在 ①② 满足、④ 就绪时一个重查周期内 ENABLED；④ 不就绪时保持 `PENDING_PREREQUISITES`，"还差什么"给出 ④ 原因 |
| AC-07 | commercial：ENABLED 开通项对应 MID 关闭后，下一次复核变 `SUSPENDED_BY_PREREQUISITE` 并发布事件；MID 恢复后再次 ENABLED |
| AC-08 | 开通项被两个币种价格覆盖：只有一个币种有 MID → ④ 满足、开通项生效，提醒列出另一币种；两个币种都没有 MID → ④ 不满足 |
| AC-09 | Client 无业务分类 MCC → ④ 不满足，原因 `CLIENT_MCC_MISSING` |
| AC-10 | channel-connector 不可用 / 超时 → ④ `UNKNOWN`，开通项状态不变 |
| AC-11 | `GOLIVE_MID_READY`：给定 OE、支付方式列表、MCC，返回 `satisfied` 与缺失支付方式；与 AC-01—AC-04 同一组数据结论一致 |
| AC-12 | 发布后全量重查一次：mor-test 上 PENDING / 门禁暂停的开通项按新口径得出结论；ENABLED 存量按 Q-2 的处理结果符合预期（见 9 节影响读数） |

### 8.2 视觉 / 交互验收

| # | 验收标准 |
|---|---|
| AC-V1 | 合同详情开通项展开"还差什么"，渠道身份一项显示具体原因（含 OE、支付方式、币种、MCC），中英文无原始文案键 |
| AC-V2 | 渠道身份未满足时显示"去配置 MID"链接，跳到 MID 页面并带 OE 过滤；无权限访问 MID 页的运营不显示链接或点击后为无权限页 |
| AC-V3 | 查询失败（UNKNOWN）时显示"暂时无法核对渠道身份，稍后自动重试"，不显示为缺口 |
| AC-V4 | 1440 / 390 两个宽度布局不溢出 |

## 9. 已确认事实、假设与待确认

**已确认（只读核对，2026-10-30）**

- 运行期选 MID：`QueryMidCandidatesProcessor` + `MidCapabilityContext.matches`，规则如背景所述；`t_routing_policy*` 无生产代码读取。
- `t_agreement_capability` 只有 `entity_no` + `capability_code`；支付方式在 `t_product_capability.payment_method_code`；币种只在价格（`t_commercial_term_price.currency`）上；REQ-01 的 ② 已能算出覆盖币种（`PRICE_NOT_COVERED` 带 `currencies` 参数）。
- Client MCC 在 client-master `t_client_business_classification`（经业务档案关联 Client）；commercial 结账上下文已通过 client-master 客户端读取分类。
- data-query 数据源 `MOR_CHANNEL_CONNECTOR_REPLICA` 在 mor-test 已登记且 ACTIVE；运营控制台 MID 页面路由为 `/channel/mids`（决定 3.5 写的 `/channels/mids/new` 不存在）。
- **mor-test 影响读数**：PAYMENT_METHOD 类开通项 12 条，全在 AE001、Client MCC 均为 5999；ENABLED 4 条（CARD 3 条、APPLE_PAY 1 条），门禁暂停 8 条。AE001 的 ACTIVE MID 能力只有 CARD（USD × 5999 两个 MID，`*` × 5816 一个）。按新口径：3 条 CARD ENABLED 仍就绪；**DEMO-CL-001 的 APPLE_PAY 不就绪**（运行期本来也选不到 MID）。
- **更正（2026-10-30 阶段 6 只读巡检）**：上条漏看价格币种。4 条 ENABLED 中 3 条 CARD 带 AED 价格，AE001 无 AED 能力行。按 Q-4 修订后的"任一币种就绪"：DEMO-CL-001 CARD、DEMO2-CL-103 CARD（AED + USD）保持 ENABLED 并提醒 AED 缺 MID；**DEMO2-CL-101 CARD（只有 AED 价格）与 DEMO-CL-001 APPLE_PAY 将被暂停**（两者运行期本来也选不到 MID）。

**假设**

- A1 开通项目录的 `payment_method_code` 与 MID 能力行的 `payment_method_code` 是同一套目录值（钱包类如 APPLE_PAY 在运行期按自身代码选 MID，不借用 CARD 能力）——阶段 2 核对 payment 的冻结口径。
- A2 收款动作能力代码为 `ACTION.AUTHORIZE` / `ACTION.SALE`（布尔）——阶段 2 核对能力目录与 payment 的使用。

**待确认**

| # | 问题 | 建议 |
|---|---|---|
| Q-1 | 决定写的"MID 在生效路由策略内"运行期并不存在（路由策略表无代码读取）。门禁要不要加这个条件？ | **不加**，门禁与运行期选 MID 保持同一规则；修订决定附件 3.2。将来启用路由策略时，就绪查询随之加入 |
| Q-2 | 发布后每日复核会按新口径检查存量 ENABLED，mor-test 上 DEMO-CL-001 的 APPLE_PAY 会被自动暂停。接受，还是对存量 ENABLED 只报告不暂停？ | **接受自动暂停**：它运行期本就选不到 MID，暂停是如实反映；暂停后补 MID 会自动恢复。发布前在 mor-test 跑只读巡检列出将被暂停的开通项并告知 |
| Q-3 | 本期不发 `ChannelIdentityChanged`，靠 commercial 重查（PENDING 5 分钟、ENABLED 每日）。MID 关闭后最长约一天才暂停开通项，可以接受吗？ | **接受**（M1 决定已允许）；需要更快时另立事件需求 |
| Q-4 | 一个开通项有多个币种的价格时，要求**所有币种**都有 MID，还是**任一币种**有即可？ | ~~所有币种~~ → **任一币种（2026-10-30 阶段 6 修订，用户确认）**：阶段 6 只读巡检发现按"所有币种"mor-test 4 个 ENABLED 开通项会全部暂停（3 个 CARD 只缺 AED、USD 可收）；改为任一币种就绪即生效，缺 MID 币种以提醒列出，与运行期按币种失败一致 |

## 10. 阶段安排

默认八阶段：1 需求 → 2 差异与影响 → 3 方案（含合同页"还差什么"的原型改动，复用 REQ-01 的商务 · 14 画板做增量）→ 4 研发 → 5 本机验证 → 6 mor-test 发布准备 → 7 mor-test 部署与验收 → 8 提交。SQL、mor-test 部署、提交、推送分别授权。预估规模 S—M（计划为 S；commercial 侧多币种与 MCC 来源略增工作量）。
