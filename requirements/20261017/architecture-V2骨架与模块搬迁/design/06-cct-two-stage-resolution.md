# 06 · 客户主档与商务分库：两段式 Checkout 解析（S4、S5）

现状见 [analysis/cct.md](../analysis/cct.md)：唯一跨 core 运行路径是 `POST /checkout-context/v1/resolutions`，一个 REPEATABLE READ 快照读 23 张两边的表，调 Risk 后重读并比对四类根版本；商务侧 11 条查询用子查询扫 `t_client_entity_affiliation` 与 `t_entity_source_relation`；`ClientPeopleMapper` 读 `t_agreement_party`。

## 1. 归属

- `client-master`（`mor_client_master`）：21 张主档表 + `t_consumer_legal_document`、`t_seller_disclosure`（C4 已定：客户主档持有）+ 来自 Commerce 的 `t_client_stored_card_setting`（用户已定：Client 级配置归客户主档）+ 自己的 BCR 两表 + 补偿表。
- `commercial`（`mor_commercial`）：11 张商务表 + 自己的 BCR 两表 + 补偿表。
- 现有入口路径 `POST /checkout-context/v1/resolutions` 由 **commercial** 提供（解析编排 Owner），Commerce 的调用目标从 `client-contract-service` 改为 `commercial-service`。

## 2. 接口

### 2.1 client-master 新增

- `POST /client-master/v1/checkout-candidates`
  - 入参：`clientId`、`consumerCountry`、`currency`、`industryCode`、`mccCode`、`asOf`。
  - 出参：`client{status, approvedAt, partyProfileId, storedCardSetting{enabled, …}}`（已存卡开关随 Client 返回，收单不再单独读表）、`candidates[]` 按 Affiliation 优先级排序，每项 `{entityNo, affiliationId, scopeId, sourceRelation{type, sourceClientId, sourcePartnerId}, oePartyId, localPartner{id,status,partyProfileId}?, legalDocuments{CONSUMER_TERMS, REFUND_POLICY, PRIVACY_NOTICE}（按 consumerCountry × locale 命中的文档引用与版本）, sellerDisclosure}`、`rootVersions{CLIENT, OPERATING_ENTITY[entityNo→version], LOCAL_PARTNER[id→version], CONSUMER_LEGAL_DOCUMENT, SELLER_DISCLOSURE}`。法律文档与卖方披露随候选返回（C4），商务不再查这两表。
  - 语义：把现有 `CheckoutResolutionContext.candidates / buildCandidate` 中只依赖主档事实的校验全部放在这里：Client ACTIVE 且审批时间早于 asOf、主分类已确认、Affiliation 有效、OE ACTIVE、唯一有效来源关系、精确 Scope 匹配、Scope 申请 APPROVED 且来源关系一致。单库一次只读快照完成。
- `POST /client-master/v1/checkout-candidates/verify`
  - 入参：第一次返回的 `rootVersions` 与候选 ID 集合。
  - 出参：`unchanged: boolean`，变化的根列表。
- `GET /client-master/v1/party-profiles?ids=`：商务签署校验读取 `approved_time`、`draft_application_id`、`content_hash`。

### 2.2 commercial

- `POST /checkout-context/v1/resolutions`（路径不变）：
  1. 调 client-master 候选接口。
  2. 在本库只读快照内逐候选解析：Client Agreement 已签、CLIENT_PRICING 计划 / 版本 / 价格、PARTNER 来源时的 Partner Agreement + PARTNER_SHARE + PLATFORM_SERVICE_CHARGE + 唯一 PLATFORM_SIGNATORY；三份法律文档与卖方披露取候选自带值，缺失同样落到下一候选；签署校验所需的 Party Profile 经 2.1 的 `party-profiles` 接口读取。缺任一项落到下一候选（现有 fall-through 语义不变）。
  3. 事务外调 Risk 资格接口（现为 503 占位，保持）。
  4. 调 client-master `verify`；同时在本库重读商务根版本（AGREEMENT 等）比对。任一变化则按现有 `verifyUnchanged` 语义返回冲突。
  5. 返回 OE、Agreement、Plan / Version ID、法律证据，结构与现响应一致。
- 新增 `GET /commercial/v1/agreement-parties/exists?partyIds=&partyProfileIds=`：供 client-master 的人员"共享"标记查询（替代 `ClientPeopleMapper` 直接读 `t_agreement_party`）。

## 3. 商务查询参数化

11 条 `findForCheckout` 与 `BusinessChangeRecordMapper.findCheckoutRoots` 删除对 `t_client_entity_affiliation`、`t_entity_source_relation` 的子查询，改为显式参数：`entityNos: List<String>`、`localPartnerIds: List<Long>`、`clientId`。语义等价，由现有 16 个 Checkout 测试加固件改造验证（固件拆为两库两份，见 10 文档）。

## 4. 一致性语义变化

现状是"一次数据库快照 + 一次重读比对根版本"。拆后变成"两次远程调用 + 两库各自快照 + 根版本比对"。保证不变的点：

- 候选与商务事实的组合仍以 `rootVersions` 比对守护，任一根在两次调用间变化即冲突，调用方重试。
- 失败关闭：client-master 不可用、`verify` 不可用一律 503 `OWNER_UNAVAILABLE`，不降级。
- 记录两次调用的 Request ID 到解析日志，便于追查。

## 5. 写入方

当前商务表没有任何写入代码，Affiliation / OE / Scope / Source 也没有写入方，入驻审批不创建它们。阶段 0 不新增写入；写入能力（合同签署、价格配置、OE 关联）属于后续业务切片。因此分库不涉及写事务迁移。

## 6. 其他

- `t_business_change_record` / `t_business_change_version` 两库各一份，`BusinessChangeRecordRecordType` 枚举按归属拆成两个枚举。
- `CheckoutRecords.java`（451 行）按归属拆到两个 core。
- CCT 的 MQ 消费 `cg_cct_client_review` 与三个补偿 Handler 全部随 client-master。
- Commerce 的 `CheckoutCommercialPort` 目前是固定 Mock；阶段 0 不接通，只把目标服务名改为 `commercial`。
