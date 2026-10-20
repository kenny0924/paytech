# channel-connector 运营控制台：渠道成本方案与渠道基础配置

> 状态：阶段 1 已确认（2026-10-20，"按建议"：Q1–Q6 全部采用建议）。用户决定：先做"渠道成本方案控制台"与"渠道基础配置管理"两项，路由策略暂不考虑。确认记录见 [delivery.md](./delivery.md)，方案见 [technique.md](./technique.md)。
>
> 前序与依据：[2026-09-21 Channel 账号、MID 能力决定](../../../agent-docs/decisions/2026-09-21-channel-account-mid-capability-queries.md)及其[需求](../../20260921/payment-渠道账号与MID能力查询/requirement.md)（五表字段与业务关系的 Owner）；[billing 计费事实重构 B 线](../../20261019/billing-计费事实模型重构与渠道成本迁出/tasks/B-report.md)（渠道成本四表与 Maker-Checker 接口，已在 mor-test）；[2026-10-13 Platform 读取渠道账号配置决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)（账号材料交接）。

## 1. 背景

channel-connector 的运行链路已经通：Payment 经 HTTP 调用候选查询与渠道执行，billing 经命中接口取渠道成本组件。但它的全部配置数据没有管理入口：

- 渠道、技术账号、MID、能力组、能力明细五张表只有只读 Repository，没有任何创建、修改、列表 API。本机全链路种子 SQL 头注写明"channel-connector 没有配置管理接口，只能以 SQL 登记"；账号的 Vault Token 由脚本直接写库；mor-test 验收也靠 SQL 插入合成渠道。
- 渠道成本方案的 Maker-Checker 写接口、命中接口和到期切换 Job 已部署 mor-test，但没有列表接口、没有作废入口，创建方案时不校验渠道是否存在，运营控制台也没有页面。
- 运营控制台 40 个页面里与渠道相关的只有 Platform 侧的「标准 API」「接入方」两页，解决的是"渠道协议怎么调"；"调哪个渠道账号与 MID"仍不能在控制台配置。Operations BFF 不依赖 `paytech-channel-connector-api`。

结果是每接入一个真实渠道，都要有人手写 SQL 并在 Vault 侧手工生成账号 Token，无法由运营自助完成，也没有操作留痕。

## 2. 目标

运营在控制台完成一个支付渠道从登记到可承接交易的全部配置：登记渠道、建立技术账号并保存账号参数与凭据、登记 MID 与能力、按 Maker-Checker 维护渠道成本方案；激活前系统给出明确的前置检查结果。SQL 种子与脚本直写库的做法退役。

## 3. 范围

### A 线：渠道成本方案控制台

| 编号 | 功能 | channel-connector 侧 | BFF / Web 侧 |
|---|---|---|---|
| A1 | 方案列表：按渠道、状态筛选，分页；显示当前版本号、当前版本生效时间、待审批版本数 | 新增 `GET /channel-connector/v1/channel-cost-plans` 列表 | 列表页 |
| A2 | 方案详情与版本列表：版本号、状态、生效时间、编制人、复核人、摘要 | 复用 `getChannelCostPlan` | 详情页 |
| A3 | 版本编制：按范围（MID、OE、国家、币种、支付方式、动作）录入成本组成；MID 从本渠道 ACTIVE MID 中选择 | 复用 `createChannelCostVersion`；创建方案时校验 channelId 存在（补 B 线报告遗留） | 编制表单，范围与组成可增删行 |
| A4 | 提交、批准、驳回：Maker 为当前会话账号；Checker 与 Maker 不同；批准时携带页面展示的内容摘要 | 复用 submit / approve / reject；`makerId` / `checkerId` 由 BFF 以会话 Account ID 填入 | 版本详情页操作区 |
| A5 | 作废尚未生效的 APPROVED 版本（到期冲突目前靠人工处理，为 B 线报告登记的后续项） | 新增作废接口：仅 APPROVED 且未到生效时间可作废，终态 INACTIVE；ACTIVE 版本不可作废，只能被新版本替换 | 版本详情页操作 |
| A6 | 版本详情：按范围分组展示组成参数；两个版本的范围 / 组成差异对比 | 复用 `getChannelCostVersion` | 对比在 BFF 聚合 Processor 计算 |

### B 线：渠道基础配置管理

| 编号 | 功能 | channel-connector 侧 | BFF / Web 侧 |
|---|---|---|---|
| B1 | 渠道：新建（code、名称）、改名、状态流转 DRAFT → ACTIVE ⇄ SUSPENDED → CLOSED；列表与详情 | 新增渠道管理 API；`channel_code` 创建后不可改 | 渠道列表与详情页 |
| B2 | 技术账号：在渠道下新建（OE、名称、备注）、编辑名称与备注、状态流转；保存账号配置（结构化 `parameters` 与 `credentials`） | 新增账号管理 API；账号配置写入接口在事务外调用 Vault Protect 得到 `CHANNEL_ACCOUNT_CONFIG_V1` Token，再以短事务写入 `account_vault_token`；明文不落库、不进日志、不回显 | 账号表单；凭据字段只写不读，已配置时显示"已配置（Token 末 4 位）" |
| B3 | MID：在渠道下新建（外部 MID 代码、OE、当前账号）、改绑账号、状态流转；OE 创建后不可改；`(channel_id, external_mid_code)` 唯一 | 新增 MID 管理 API；改绑账号要求账号与 MID 同渠道同 OE | MID 列表与详情页 |
| B4 | 能力组与明细：在 MID 下新建能力组（支付方式、体系或 `*`、国家或 `*`、币种或 `*`、四位 MCC）、整组启停；明细按能力目录录入，类型由目录决定 | 新增能力管理 API；能力目录来自现有 `channel-connector.capability-definitions`，新增目录读接口供页面取下拉 | 能力组表单，明细按目录渲染 |
| B5 | OE 校验：账号与 MID 的 `entity_no` 必须是 client-master 中存在的 OE，只存值不复制 OE 表 | 写入前（事务外）经 `paytech-client-master-api` 校验 | 页面从客户主档选择 OE（复用商务控制台 F1 的选择器） |
| B6 | 激活前检查：渠道激活要求 Platform 存在编码相同的接入方；账号激活要求已有 Token；MID 激活要求渠道与账号 ACTIVE | 检查在 channel-connector 写接口内完成（Platform 接入方存在性经 `paytech-platform-support-api` 事务外查询） | 不满足时页面显示 Owner 返回的 detail |
| B7 | 操作留痕：写接口以 `X-Gateway-Actor-Subject` 取操作者，与 commercial 一致；每次状态变更记录操作者与时间 | 操作者记录方式在阶段 3 定（字段或 BCR） | 详情页显示最近变更 |

### 共同

| 编号 | 功能 |
|---|---|
| C1 | Operations BFF 新增对 `paytech-channel-connector-api` 的依赖与客户端配置；查询不带操作者，命令带会话 Account ID |
| C2 | 控制台新增一级菜单「渠道」：渠道与账号、MID 与能力、渠道成本 三个页面；Platform 注册块（菜单、权限、Route）按阶段 3 分配的块号登记 |
| C3 | 页面错误显示 Owner 的业务 detail（与商务控制台 F9 相同做法） |

## 4. 不做

- 路由策略（`t_routing_policy` 三表）与 Payment 侧 `findFirst` 选路的替换（用户决定本期不考虑）。
- 健康度（09-21 决定已从目标模型移除）。
- 渠道交互记录、回调、异常处置的查询页（回调落库尚未实现）。
- MID 归属 OE 的改写、渠道或 MID 的物理删除、凭据明文回显或导出。
- Payment、billing、Platform 的接口与数据变更；渠道成本金额计算规则变化。
- Vault 策略之外的 Vault 变更；账号配置 Schema（`channel-account-config.schema.json`）变更。
- 合作伙伴门户、Client 门户的渠道可见性。

## 5. 已确认事实与假设

已确认：

- 五表字段、业务关系与状态值以 09-21 需求第 2、3 节为准；本需求不改表结构（B7 若需操作者字段在阶段 3 单独列出）。
- 渠道成本接口的 Maker / Checker 以请求体 `makerId` / `checkerId` 传入；BFF 可从会话取得 Account ID 填入，不改 channel-connector 合同。
- Platform 执行标准 API 时 `integrationCode = channel_code`；账号 Token 的执行用 Reveal 由 Platform 以 `CHANNEL_EXECUTION` 用途完成（10-13 决定）。
- mor-test 现有渠道成本验收用的合成渠道 / 账号 / MID 为 SQL 插入，无能力行；可作为 A 线页面的初始数据。

假设（待用户确认）：

- 基础配置（B 线）不做 Maker-Checker 双人审批，只做权限控制与操作留痕；渠道成本（A 线）保持现有双人审批。
- 账号配置的凭据由运营在控制台填写，channel-connector 负责 Protect；运营不再经「手动加密」页先生成 Token 再粘贴。

## 6. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 账号 Token 的产生方式：① channel-connector 账号配置写接口内 Protect（需要新增 `channel-connector-service` 对 `CHANNEL_ACCOUNT_CONFIG_V1` 的 PROTECT 规则，用途沿用现有 `CHANNEL_CONFIGURATION`）；② 运营先用 Vault「手动加密」页得到 Token 再填入账号 | **①**。与通知发送账号凭据由 Platform 自行 Protect 的做法一致；Token 不经人手，凭据只在 BFF → channel-connector 一跳内网请求体中出现，归入已暂缓的敏感日志统一处理（W5） |
| Q2 | 基础配置的状态变更是否要双人审批 | 不做。渠道 / 账号 / MID 激活前已有系统检查，SUSPENDED 可立即止损；审批会拖慢止损。资金相关的成本方案保持审批 |
| Q3 | 能力目录在页面上的来源：直接读 channel-connector 的 `capability-definitions` 配置，还是复制到前端 | 新增目录读接口，前端不硬编码（与商务控制台 F4 同思路） |
| Q4 | A5 作废的终态：复用现有 `INACTIVE`，还是新增 `CANCELLED` | 复用 `INACTIVE`，不改枚举；作废原因记录在版本上 |
| Q5 | 菜单位置：新一级菜单「渠道」，还是并入「外部 API」 | 新一级菜单「渠道」；「外部 API」保持 Platform 协议配置的语义 |
| Q6 | B 线是否要求激活渠道时 Platform 接入方必须已存在（B6） | 要求。否则执行一律 NOT_SENT，运营无法从渠道页看出原因 |

## 7. 验收标准（草案）

功能验收：

- AC-A01 方案列表按渠道、状态筛选分页；mor-test 现有合成渠道的方案可在列表找到并进入详情。
- AC-A02 版本编制表单可增删范围与组成；MID 下拉只含本渠道 MID；提交后版本为 PENDING_APPROVAL。
- AC-A03 批准人与编制人相同时被拒绝并显示 Owner detail；批准成功后已到生效时间的版本为 ACTIVE，否则 APPROVED。
- AC-A04 未到生效时间的 APPROVED 版本可作废为 INACTIVE；ACTIVE 版本无作废入口；作废后到期 Job 不再切换该版本。
- AC-A05 两版本对比高亮新增、删除、修改的范围与组成。
- AC-A06 创建方案时渠道不存在返回业务拒绝并显示原因。
- AC-B01 可新建渠道并激活；Platform 不存在同编码接入方时激活被拒绝并显示原因。
- AC-B02 可在渠道下新建账号并保存参数与凭据；保存后库中只有 Token，详情页不回显凭据；无 Token 的账号不能激活。
- AC-B03 可新建 MID、改绑同渠道同 OE 的账号；跨 OE 或跨渠道改绑被拒绝；OE 不可编辑。
- AC-B04 可新建能力组与明细；重复条件组合被拒绝；明细类型与目录不一致被拒绝；整组停用后候选查询不再返回该组能力。
- AC-B05 以控制台配置的渠道 / 账号 / MID / 能力，Payment 候选查询能返回该 MID，渠道执行能走到 Platform 标准 API（本机 e2e 不再依赖种子 SQL 与 `channel_setup.py`）。
- AC-B06 每次状态变更记录操作者与时间并在详情页可见。
- AC-C01 channel-connector、BFF、Web 自动测试与构建全绿；mor-test 发布后页面回归通过；未授权账号看不到「渠道」菜单。

视觉 / 交互验收：

- AC-V01 「渠道」菜单下三页的入口、面包屑、空状态文案与现有商务、财务页一致。
- AC-V02 表单内联校验：必填、格式（四位 MCC、两位国家、三位币种、十进制费率）在提交前提示；提交失败显示 Owner detail。
- AC-V03 凭据字段为密码型输入，已配置时显示掩码与 Token 末 4 位，有"重新填写"动作。
- AC-V04 状态流转以按钮呈现，仅显示当前状态允许的下一步；CLOSED 后无操作。

## 8. 阶段安排（评审稿）

| 阶段 | 安排 |
|---|---|
| 1 | 本评审稿：范围 A / B / C、不做项、Q1–Q6、验收标准 |
| 2–3 | 差异分析与设计合一：channel-connector 新增 API 合同、Vault 策略片段、BFF 客户端与聚合 Processor、页面设计、Platform 注册块号、研发与验证计划 |
| 4 | 研发分线：A（channel-connector 列表 / 作废 / 校验 + BFF + Web）先行；B（channel-connector 管理 API + Vault Protect + BFF + Web）其后；执行者 Opus 5.5 agent，主会话设计与把关 |
| 5 | 本机验证：三端自动测试与构建；本机 e2e 用控制台配置替代种子 SQL 跑通候选查询与执行 |
| 6–7 | mor-test：Vault 策略 DML、替换 channel-connector / ops-bff / ops-web、Platform 注册 DML、页面回归；合成渠道数据清理 |
| 8 | 私有索引本地提交 |
