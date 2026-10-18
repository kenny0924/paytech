# 收单端到端时序（讨论稿，2026-10-17）

按目标架构 V2 的服务划分，从 Client 创建订单到结算出款的三段式时序。编号连续；同步调用为内部 HTTP 契约（事务外），事件为补偿事件表提交后发布，自调用为该服务的本地事务。与 [架构画板](./assets/architecture-design.pen) 配套，依据决策登记表 D16 / D17 / D18 / D25 / D31（收单 = acquiring-service）。

## Stage 1 · 收单：创建订单 → 支付准备 → 支付执行

```mermaid
sequenceDiagram
  autonumber
  participant CL as Client 系统
  participant BR as 消费者浏览器<br/>Checkout Web
  participant GW as Nginx / Gateway
  participant ORD as 收单 Acquiring
  participant COM as 商务
  participant CCT as 客户主档
  participant TAX as 税务与发票
  participant CONS as 消费者
  participant RISK as 风控决策
  participant PAY as 支付执行
  participant CARD as 卡 · PCI
  participant CHN as 渠道连接
  participant PINT as Platform 集成
  participant ACQ as 渠道 / Acquirer

  rect rgb(238,240,255)
  Note over CL,ORD: ① 创建订单
  CL->>GW: POST /checkouts（Idempotency-Key、client_order_id、金额币种、商品、回跳 URL）
  GW->>GW: Credential + Scope 校验 · 请求幂等 · Tenant 限流 · Request ID
  GW->>ORD: 转发（X-Gateway-Client-Id、X-Gateway-Credential-Id）
  ORD->>ORD: 本地事务：client_order_id 在 Client 内唯一；创建 Order / Items / Checkout Session；按入口域名冻结品牌
  ORD-->>CL: 201 Checkout Public Token · 收银台 URL · 过期时间
  end

  rect rgb(233,246,243)
  Note over BR,TAX: ② 收银台与支付准备
  CL-->>BR: 跳转到托管收银台
  BR->>GW: GET checkout（Public Token）
  GW->>ORD: 查询 Checkout 视图
  ORD-->>BR: 订单摘要 · 待填消费者字段 · 法律文档版本链接
  BR->>GW: PUT consumer（姓名、Email、Phone、账单地址）
  GW->>ORD: 保存消费者
  ORD->>CONS: Email / Phone 规范化后 Protect → 稳定 Token（经 Vault）
  CONS-->>ORD: Consumer Token · 脱敏值
  ORD->>ORD: 本地事务：写消费者上下文（交易时点不可变快照）
  BR->>GW: POST preparation（准备支付）
  GW->>ORD: 支付准备命令
  Note over ORD,TAX: 以下三次查询在事务前完成（类 A：查询后冻结）
  ORD->>CCT: 按 Client 的 Affiliation 优先级取 OE 候选，逐个校验 Scope / 来源 / 合同 / 法律材料 / 交易时点资格
  CCT-->>ORD: 唯一可用 OE（entity_no）· Affiliation
  ORD->>COM: 取该 Client × OE 的合同、业务模式、价格版本、法律文档版本
  COM-->>ORD: Agreement ID · 业务模式 MoR · Price Version ID · Term Versions
  ORD->>TAX: 按 OE、消费者国家、商品类目、币种计算税额
  TAX-->>ORD: 税额 · 税务登记主体 · 不可变汇率快照引用
  ORD->>ORD: 本地事务：冻结 OE、业务模式、合同与价格版本、税额与总额到 Order Context（Version+1）；提交后不可覆盖，后续 Attempt 不跨 OE
  ORD-->>BR: 最终金额明细 · 三份法律文档（CONSUMER_TERMS / REFUND_POLICY / PRIVACY_NOTICE）
  BR->>GW: 提交法律接受（勾选三项，携带 IP、UA、代理链）
  GW->>ORD: 接受命令
  ORD->>ORD: 本地事务：三条不可变接受记录 + LEGAL_ACCEPTANCE 网络证据；服务端派生「支付就绪」
  ORD-->>BR: 就绪 · 三个 Acceptance ID · acceptance_completed_at
  end

  rect rgb(255,247,237)
  Note over BR,ACQ: ③ 支付提交与执行
  BR->>GW: POST payments（卡号、有效期、CVV + 法律确认引用）
  GW->>PAY: 转发到支付执行（唯一接收原始卡数据的公共入口）
  PAY->>CARD: 事务外：加密存储 PAN，返回平台卡令牌；CVV 仅瞬时持有、不落盘不摘要
  CARD-->>PAY: 卡令牌 · BIN · 卡组织 · 脱敏卡号
  PAY->>ORD: 内部同步：核对法律确认与就绪状态，取冻结的 Order Context
  ORD-->>PAY: Order Version · OE · 金额 · Consumer Token · 网络证据（最小内部合同）
  PAY->>RISK: 同步评估（结账点）：金额、OE、BIN、Consumer Token、网络证据、设备指纹
  RISK-->>PAY: ACCEPT / CHALLENGE（要求 3DS）/ REJECT · assessment_id
  alt REJECT
    PAY-->>BR: 支付被拒绝（不暴露规则细节）
  else ACCEPT 或 CHALLENGE
    PAY->>CHN: 查询候选：该 OE 下支持该支付方式与币种的 Channel / Channel Account / MID，附当前窗口健康度
    CHN-->>PAY: 静态能力过滤、健康度排序后的候选列表
    PAY->>PAY: 本地事务：创建 Intent / Attempt / Instrument Token；冻结 Channel + Account + MID 三元组；创建 AUTHORIZE 或 SALE Action（SUBMITTED）；登记补偿事件
    PAY->>CHN: 提交后：执行 Action（Attempt、卡令牌、金额、3DS 要求）
    CHN->>CHN: 报文适配；渠道账号配置经 Platform 集成从 Vault Reveal
    CHN->>PINT: 标准 API 调用（出站 HTTP 执行，事务外）
    PINT->>ACQ: 渠道协议请求
    ACQ-->>PINT: 成功 / 失败 / 需 3DS 跳转 / 超时
    PINT-->>CHN: 原始响应（脱敏后可受控留存）
    CHN-->>PAY: 规范化结果 · 渠道交易引用
    opt 需要 3DS
      PAY-->>BR: 跳转 ACS（瞬态 URL，不持久化）
      BR->>ACQ: 完成 3DS 验证
      ACQ->>GW: 渠道回调
      GW->>CHN: 直达渠道连接：验签、去重、状态映射
      CHN->>PAY: 交付规范化回调结果
    end
    alt 结果 UNKNOWN（超时 / 网络）
      loop 恢复任务持久化在 Action 上，补偿事件按退避重试
        PAY->>CHN: 恢复查询（原 Action，不新建业务 Action）
        CHN->>ACQ: 查询交易状态（经 Platform 集成）
        ACQ-->>CHN: 最终状态
        CHN-->>PAY: 规范化终态（终态单调；冲突终态进异常工单）
      end
    end
    PAY->>PAY: 本地事务：Action → SUCCEEDED / FAILED；Attempt / Intent 推进；按 Capture 模式判定是否已请款；写 PaymentSucceeded 到补偿事件表
    PAY-->>BR: 支付结果页 → 回跳 Client return URL（带 Order 引用，不带结果可信字段）
  end
  end
```

## Stage 2 · 入账：事实发布 → 计费 → 账户与会计 → 通知 → 渠道对账

```mermaid
sequenceDiagram
  autonumber 60
  participant PAY as 支付执行
  participant ORD as 收单 Acquiring
  participant TAX as 税务与发票
  participant FEE as 计费
  participant COM as 商务
  participant ACC as 账户与余额
  participant LEDG as 会计
  participant TXC as 数据服务<br/>数仓 · CDC
  participant PLT as Platform<br/>通知 / Webhook
  participant CL as Client 系统
  participant CHN as 渠道连接
  participant RECON as 清算与对账
  participant ACQ as 渠道 / Acquirer

  rect rgb(233,246,243)
  Note over PAY,CL: ④ 事实发布与下游消费（全部事件驱动、消费者幂等）
  PAY->>PAY: 补偿事件提交后发布 PaymentSucceeded（Intent、Attempt、OE、Channel / MID、金额币种、支付方式、成功时间、Term Versions）
  PAY-)ORD: PaymentSucceeded
  ORD->>ORD: 幂等：Order → PAID；关闭 Checkout Session；写订单支付摘要
  PAY-)TAX: PaymentSucceeded
  TAX->>TAX: 以冻结税额快照生成 Fiscal Fact 与收据（MoR 代征）；发布 FiscalDocumentIssued
  PAY-)FEE: PaymentSucceeded
  FEE->>COM: 读订单冻结的 Price Version（只读，不重新命中合同）
  COM-->>FEE: 该版本的费率组件 / 阶梯 / 最低收费 / 分润规则
  FEE->>FEE: 本地事务：按支付事实算 Client 手续费、合作伙伴分润、平台服务费、渠道预估成本；生成不可变计费明细（经济事实 ID、版本、舍入）
  FEE-)ACC: FeesCalculated（经济事实 ID）
  FEE-)LEDG: FeesCalculated（同一经济事实 ID）
  ACC->>ACC: 幂等入账：Client 账户在途 + 净额（毛额 − 手续费 − 税）；按价格版本的结算输入算成熟日 T+N；合作伙伴分润在途
  LEDG->>LEDG: 幂等过账：OE 账簿 Dr 渠道清算应收 / Cr Client 应付、手续费收入、税金应付；平台服务费双账簿同事务
  Note over ACC,LEDG: 两边口径来自同一经济事实；周期按 ID 覆盖核对，差异以计费明细裁决、差异金额挂起不进可用额度（D16）
  PAY--)TXC: 支付执行 / 订单 / 计费 / 账户白名单表的 DML 经 CDC 秒级同步到数仓（不经 MQ）
  TXC->>TXC: 门户列表与报表查询视图就绪（最终一致、可重建，不回写、不触发通知）
  PAY-)PLT: PaymentSucceeded
  PLT->>PLT: 按租户 Webhook 订阅生成投递任务（HMAC 签名、重试策略、投递日志）
  PLT->>CL: POST webhook payment.succeeded
  CL-->>PLT: 2xx（否则退避重试，直到放弃并告警）
  end

  rect rgb(238,240,255)
  Note over CHN,ACQ: ⑤ 渠道清算与对账（T+1 起）
  ACQ-->>CHN: 清算文件 / 清算 API（经 Platform 集成拉取，SFTP 或 HTTP）
  CHN->>CHN: 解析为规范化清算批次与明细；发布 ChannelSettlementFileReceived
  CHN-)RECON: ChannelSettlementFileReceived
  RECON->>RECON: 本地事务：创建 Channel Settlement Batch / Item；按渠道交易引用匹配 Attempt；长短款与缺失进差错
  alt 匹配成功
    RECON->>RECON: 确认实际渠道成本；发布 ActualChannelCostConfirmed
    RECON-)FEE: ActualChannelCostConfirmed
    FEE->>FEE: 生成成本差异计费明细（预估 → 实际，新经济事实，不改历史）
    FEE-)LEDG: FeesCalculated（成本调整）
    RECON-)LEDG: ActualChannelCostConfirmed
    LEDG->>LEDG: 渠道清算应收 → 银行现金（渠道款到账）
  else 差错 / 长短款
    RECON->>RECON: 差错工单（工单与审批引擎）；人工调账产生新经济事实
  end
  ACC->>ACC: 成熟日到达：在途 → 可用（按 Client 价格的工作日数 / 日历 / 时区 / 截止秒）
  end
```

## Stage 3 · 结算出款：申请 → 双人审批 → Execution → 银行提交 → 确认借记 → PAID

```mermaid
sequenceDiagram
  autonumber 100
  participant CL as Client 控制台
  participant BFF as Gateway /<br/>Client BFF
  participant PO as 出款编排
  participant FEE as 计费
  participant COMP as 合规与案件
  participant RISK as 风控决策
  participant ACC as 账户与余额
  participant APPR as 工单与审批
  participant OPS as 运营人员
  participant BANK as 银行
  participant RECON as 清算与对账
  participant LEDG as 会计
  participant PLT as Platform

  rect rgb(238,240,255)
  Note over CL,APPR: ⑥ 出款申请与审批
  CL->>BFF: 申请出款（金额、币种、收款账户）
  BFF->>PO: 认证 JWT、入口授权后转发：创建出款命令（Client、OE、币种、owner_debit_amount）
  PO->>PO: 本地事务：创建出款单 PENDING + 幂等键；登记补偿事件
  Note over PO,ACC: 提交后的同步调用（类 B 可逆命令，下游终态裁决 D17）
  PO->>FEE: 计算出款费用
  FEE-->>PO: 出款费用明细（经济事实 ID）
  PO->>COMP: 查询结算限制与最低保证金要求
  COMP-->>PO: 无限制 / 限制详情
  PO->>RISK: 检查点① PAYOUT_CREATION：新 assessment_id + evaluation_id，冻结 Owner / OE / 币种 / 收款账户国家 / 金额
  RISK-->>PO: PASS（追偿应收为零、保证金充足、无结算限制）或 FAIL
  PO->>ACC: 锁定命令：AVAILABLE → LOCKED（owner_debit_amount，幂等键 = 出款单号）
  ACC->>ACC: 本地事务 CAS：余额充足则 LOCKED，否则 REJECTED；重复命令按幂等键返回原结果
  ACC-->>PO: LOCKED / REJECTED
  PO->>PO: 短事务回写：出款单 → PENDING_APPROVAL（或 FAILED 并记录原因）
  PO->>APPR: 创建双人审批工单（Maker-Checker，四眼原则）
  OPS->>APPR: 初审通过
  OPS->>APPR: 发起终审
  APPR->>PO: 终审前回调：请求复核
  PO->>RISK: 检查点② PAYOUT_FINAL_APPROVAL（新评估，不复用①）
  RISK-->>PO: PASS
  APPR-->>PO: ApprovalDecided（终审通过，审批证据）
  PO->>PO: 本地事务：出款单 → APPROVED（业务服务记录最终决定；审批通过 ≠ 已执行）
  end

  rect rgb(255,247,237)
  Note over PO,PLT: ⑦ 执行、确认借记与 PAID
  PO->>RISK: 检查点③ PAYOUT_EXECUTION_CREATION
  RISK-->>PO: PASS
  PO->>PO: 本地事务：预创建 Execution（待占用）
  PO->>ACC: 占用 BANK_CASH 可用容量（active_payout_cash）
  ACC-->>PO: 已占用（账户本地事务；UNKNOWN 时容量保持占用）
  PO->>PO: 短事务：Execution → READY_TO_SUBMIT
  PO->>RISK: 检查点④ PAYOUT_EXTERNAL_SUBMISSION
  RISK-->>PO: PASS
  OPS->>BANK: 人工网银提交（事务外：金额、收款账户、Execution 引用）
  OPS->>PO: 上传付款凭证（文件经 Platform 文件存储）；Execution → SUBMITTED
  BANK-->>RECON: 银行对账单 / 流水
  RECON->>RECON: 银行对账：按 Execution 引用与金额匹配 → CONFIRMED 借记
  RECON-)PO: ExecutionDebitConfirmed
  PO->>ACC: 命令：LOCKED → 扣减（owner_debit），释放 BANK_CASH 占用
  ACC->>ACC: 本地事务：扣减、解锁、释放容量；终态裁决，幂等
  ACC-->>PO: 已扣减
  PO->>PO: 本地事务：Execution → CONFIRMED，出款单 → PAID；写 PayoutPaid 到补偿事件表
  PO-)FEE: PayoutPaid
  FEE-)LEDG: FeesCalculated（出款手续费）
  PO-)LEDG: PayoutPaid
  LEDG->>LEDG: Dr Client 应付 / Cr 银行现金；出款手续费收入
  PO-)PLT: PayoutPaid（出款单变更同时经 CDC 同步到数据服务）
  PLT->>CL: webhook payout.paid
  end
```
