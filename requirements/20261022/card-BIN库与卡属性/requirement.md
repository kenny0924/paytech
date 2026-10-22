# card：BIN 库与卡属性 · 需求

> 状态：**阶段 1 已确认**（2026-10-22 用户“都按照建议来”：Q1—Q6 均按建议）。确认记录见 [delivery.md](./delivery.md)，技术方案见 [technique.md](./technique.md)（阶段 2 起填写）。
> 姊妹需求：[payment-3DS全链路](../payment-3DS全链路/requirement.md)（同日登记，可并行）。

## 1. 背景

- **Card 只存 BIN 原值。** `t_payment_card.card_bin` 存 6～8 位（卡号不少于 16 位取 8 位），另存 `pan_last4`；不存卡组织、借贷记、发卡国。这是 [Card 加密存储需求](../../20261015/payment-Card加密存储/requirement.md)（:30、:80）的有意决定：“以后用 `card_bin` 查 BIN 库”。仓库里至今没有 BIN 库，也没有任何数据来源或归属的决定。
- **下游都在等卡属性。**

  | 消费方 | 需要 | 现状 |
  |---|---|---|
  | commercial 接受条件 / 定价画像 | `CARD_BRAND`、`FUNDING_TYPE`、`CARD_LEVEL`、`CARD_PRODUCT`、`ISSUER_COUNTRY`、`ISSUER_REGION`（事实来源都登记为 `CARD_BIN`） | 只能配置，运行时没有事实；[决定](../../../agent-docs/decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md)规定缺事实时失败关闭 |
  | Risk | 发卡国（“IP 与卡国家不一致”等规则） | 一期因无 BIN 国家数据推迟 |
  | Payment 路由 | 卡组织 `schemeCode` | MID 能力已有 `scheme_code`，Payment 不传，只能命中 `*` |
  | billing 支付事实 | `card_network`、`funding_type`、`card_level` | 列已建，`PaymentSucceeded` 没有这些字段，无写入方 |
  | 收银台 / 已存卡 | 卡组织（展示图标） | 首期明确不展示（Commerce D-10、Q-05 拒绝前缀规则） |

- **设计冲突待裁决。** commercial 设计（`design-discuss/commercial-capability-pricing/design.md:136`）写“存卡时保存卡组织 / 借贷记 / 发卡国”；Card 需求写“Card 不存，查 BIN 库”。

## 2. 目标

1. 平台有一份可维护、有版本的 BIN 库，能按卡 BIN 得到标准化卡属性。
2. 每笔卡交易都有一份**冻结的卡属性事实**，Risk、路由、计费读同一份，事后可追溯。
3. 卡号原值不因本需求离开 Card 的 PCI 边界；BIN 库查询只用 BIN。

## 3. 概念

| 术语 | 含义 |
|---|---|
| BIN 号段 | 一条 BIN 库记录：一个前缀（6～8 位，或起止号段）对应一组卡属性 |
| 卡属性 | `cardBrand`、`fundingType`、`cardLevel`、`cardProduct`、`issuerCountry`、`issuerName`；`issuerRegion` 由发卡国推导（见 Q6） |
| BIN 库版本 | 一次导入的完整数据集；同一时刻只有一个生效版本，切换原子生效 |
| 卡属性快照 | 一笔交易使用的卡属性及其 BIN 库版本，冻结在交易上，不随 BIN 库更新改变 |

## 4. 本期范围

| 编号 | 内容 |
|---|---|
| R1 | Card 新增 BIN 库存储：号段、标准化卡属性、所属版本；最长前缀匹配（8 位优先，其次 6 位） |
| R2 | BIN 库版本导入：上传数据文件 → 校验 → 生成新版本 → 人工激活（原子切换）；保留历史版本，可回切 |
| R3 | 卡属性查询：Card 内部按 BIN 查询；查不到时返回“未识别”，不猜测、不按前缀规则推断 |
| R4 | Card 对外输出卡属性：创建交易工具响应返回卡属性快照；已存卡列表返回卡组织与后四位（供展示） |
| R5 | Payment 冻结与透传：交易工具 Token 冻结卡属性快照；Risk 输入增加发卡国、卡组织、借贷记；路由传 `schemeCode`；`PaymentSucceeded` 增加卡组织、借贷记、卡等级（v2 追加可空字段）；billing 写入由计费需求 B 完成（2026-10-22 阶段 2 E1 修订） |
| R6 | 运营控制台：BIN 库版本列表（导入、校验结果、激活、回切）与单个 BIN 查询 |

## 5. 不做项

| 项 | 原因 |
|---|---|
| commercial 二期执行链路（收银台“该卡暂不支持”提示与已存卡置灰、Commerce 冻结接受条件、Payment 授权前按条件拦截） | 本需求只提供事实；执行链另立需求（见 Q4） |
| 实时外部 BIN API | 运行时依赖外部供应商；如需要，经 channel-connector 服务渠道另议 |
| 钱包（Apple Pay / Google Pay）卡组织 | 来源是钱包令牌，不是 BIN |
| 按渠道授权响应修正卡等级 / 卡产品 | 本期只用 BIN 库 |
| 收银台前端展示卡组织图标 | `checkout-web` 没有前端代码；本期只在接口中提供 |
| 已有卡记录的属性回填 | 属性按交易查询并冻结，卡表不存属性，无需回填（取决于 Q3） |

## 6. 业务规则（草案）

- B1 BIN 库只按 BIN 查询，任何接口不接收或返回完整卡号。
- B2 查不到号段时，卡属性为“未识别”。是否拒绝由消费方决定：commercial 限制性条件失败关闭；Risk 按规则；路由退回 `*`。
- B3 卡属性取值标准化：`cardBrand` 与 commercial `CardBrand` 规范值一致（VISA / MASTERCARD / AMEX / UNIONPAY，可扩展）；`issuerCountry` 为 ISO 3166-1 alpha-2；`fundingType` 取 CREDIT / DEBIT / PREPAID / CHARGE。
- B4 一笔交易的卡属性快照在交易工具创建时查询一次并冻结。重试、Attempt 切换 MID、后续退款都用这份快照。
- B5 BIN 库新版本激活前必须通过校验：格式、号段重叠、必填属性、取值合法；校验失败的版本不能激活。
- B6 激活、回切都记录操作人和时间；回切只影响之后的查询，不改已冻结的快照。

## 7. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | 导入一个合法数据文件后生成新版本（未激活），列表显示行数与校验结果；查询仍用旧版本 |
| AC-02 | 激活新版本后，新的交易工具使用新版本属性；已创建的交易工具快照不变 |
| AC-03 | 同一 BIN 同时存在 8 位和 6 位号段时，命中 8 位号段 |
| AC-04 | 未识别 BIN 的交易工具创建成功，卡属性为“未识别”，交易不因此失败 |
| AC-05 | 号段重叠、取值非法、缺必填列的文件导入后校验失败，不能激活，并显示具体行与原因 |
| AC-06 | 回切到旧版本后，新查询使用旧版本 |
| AC-07 | 创建交易工具响应返回卡属性快照（含 BIN 库版本）；已存卡列表返回卡组织 |
| AC-08 | Payment 交易工具 Token 冻结卡属性；Risk 评估请求包含发卡国、卡组织、借贷记；规则可按发卡国命中 |
| AC-09 | MID 能力配置了具体 `scheme_code` 时，路由按卡组织选到该 MID；未识别时只命中 `*` |
| AC-10 | `PaymentSucceeded`（仍为 v2）追加可空字段 `cardBrand`、`fundingType`、`cardLevel`，未识别时为 null；billing 写入 `card_network`、`funding_type`、`card_level` 列为下游依赖（计费需求 B），不在本需求验收（2026-10-22 阶段 2 E1 修订） |
| AC-11 | 日志、MQ、补偿 Payload 和导入错误信息中不出现完整卡号 |
| AC-12 | 运营控制台：版本列表、导入、激活、回切、单个 BIN 查询可用；空状态与校验失败有明确提示（视觉按阶段 3 原型） |

## 8. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | BIN 数据从哪里来？是否已有或计划采购商业 BIN 库（供应商定期发文件）？ | 按“导入文件”设计，文件格式定为平台标准 CSV，供应商格式经转换脚本适配。本期在测试环境用合成数据与公开测试卡号段做种子；真实数据到位后导入即可 |
| Q2 | BIN 库归谁？ | **Card**：BIN 已在 Card，查询发生在建交易工具时，BIN 不出 PCI 边界；也不新增服务 |
| Q3 | 卡属性存在卡上，还是每次交易查询后冻结在交易上？（commercial 设计与 Card 需求冲突） | **交易时查询、冻结在交易上**：Card 卡表不存属性（维持 Card 需求的决定），Payment 交易工具 Token 存快照。BIN 库更新后新交易立即生效，旧交易可追溯；commercial 设计 :136 相应修订 |
| Q4 | 消费方本期接到哪一步？ | 接 R5：Risk 输入、路由 `schemeCode`、`PaymentSucceeded` → billing。commercial 二期执行链路（收银台提示置灰、Commerce 冻结、授权前拦截）另立需求，依赖本需求 |
| Q5 | BIN 库维护入口？ | 运营控制台上传文件导入 + 人工激活（R6）。数据量预计数十万行，不适合手写 SQL；页面只显示号段与属性，不涉及卡号 |
| Q6 | `ISSUER_REGION` 的含义？是地理区域（如 EU / MEA / APAC），还是相对 OE 的境内 / 境外？ | 本期先由发卡国按平台维护的“国家 → 区域”映射推导地理区域；境内 / 境外依赖 OE 国家，留给 commercial 执行链按交易判断 |

## 9. 建议适用阶段

阶段 1—8 全部适用：涉及 card、payment、risk-decision、billing、channel-connector（只读，无改动或很少改动）、operations-console-bff / web；有 DDL 与注册 DML，要部署 mor-test。

## 10. 与文件地址原则的关系（2026-10-22 补记）

[文件上传、引用与下载原则](../../../agent-docs/decisions/2026-10-22-file-upload-address-principles.md)（P1—P3）发布于本需求阶段 6：

- P1：BIN CSV 经 `Gateway → ops-bff → Platform 文件服务` 上传，创建版本时只提交文件地址，符合。
- P2：版本表存 `file_address` 并与 `file_content_hash` 成组冻结，不存文件 ID，符合；地址的解析与格式化改用 `FileAddress`（阶段 6 修正）。
- P3 例外（理由）：BIN 库导入必须由 Card 读取文件全文做逐行校验并写入号段，无法只读元数据；Card 只在导入任务内经 Platform 下载接口换取临时链接读取字节，不向任何调用方转发文件字节或下载链接。运营下载原文件不在本期范围。
