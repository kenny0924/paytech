# commercial 开通与定价模型调整（一期）

> 状态：阶段 1–8 完成（2026-10-20—21，已部署 mor-test，验收 12 / 13，已本地提交未推送）。用户授权阶段 1–7 连续执行（含 mor-test 发布与测试数据），阶段 8 提交另行确认。确认记录见 [delivery.md](./delivery.md)。

## 1. 依据

- 决定：[2026-10-20 开通项与接受条件、标准价目模板](../../../agent-docs/decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md)
- 设计：[design-discuss/commercial-capability-pricing/design.md](../../../design-discuss/commercial-capability-pricing/design.md) 与 design.pen 五张画板
- 修订对象：[2026-10-19 六层模型决定](../../../agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md)

## 2. 范围

**包含（一期）**：commercial（模型、接口、校验、Checkout 解析输出、迁移 SQL、PDManer）、operations-console-bff（转发与聚合）、operations-console-web（页面）、Platform 注册（块 124：权限、资源、路由、capabilities）、mor-test 发布、测试数据。

| 编号 | 能力 |
|---|---|
| S1 | 开通项目录运营维护：新增 / 停用 / 改名与归属产品；种类增 FEATURE；可配维度；计费事件挂到开通项种类或「无需开通」 |
| S2 | 维度清单：代码登记的维度元数据（事实来源、判定时点、可用于开通 / 定价）只读接口 |
| S3 | 合同开通项：接受条件（同维 OR、异维 AND，CURRENCY 必填）+ 3DS 开关，替换旧三类 limits |
| S4 | 标准价目模板：平台方案（按 OE、命名、多套）、版本、价格、独立权限复核、生效；不参与运行时取价 |
| S5 | 合同价格生成：新版本「基于模板」生成（只取已开通项 × 币种，按接受条件裁剪）、「从模板补齐」、来源记录、「标准价已更新」提示 |
| S6 | 校验：完整性（按接受条件空间）、相关性（保存提示、激活拦截）、零开通（激活拦截）；Partner 条款不受相关性约束 |
| S7 | 破标：组件级与来源模板比较，返回破标 / 无标准可比；复核队列显示破标数 |
| S8 | 客户签署：APPROVED 后登记 Client / Partner 签署（价格附件 + 签署记录）→ CLIENT_SIGNED；未签不能排期；存量版本免签 |
| S9 | Checkout 解析输出每个可用开通项的接受条件与 3DS 要求；无生效价格的开通项不输出 |
| S10 | 迁移：目录 CARD_* → CARD（及钱包可配卡组织、存卡功能项）；合同开通项合并；存量版本免签标记；mor-test 执行 |
| S11 | 页面：产品目录维护、标准价目页、开通项弹窗条件控件、编辑价格（生成 / 补齐 / 来源 / 破标 / 按开通项分组）、版本签署步骤、复核队列破标数 |
| S12 | mor-test 测试数据：标准价目（AE001 标准 / 优惠）、演示合同覆盖各场景 |

**不做**：二期执行链路（Card BIN、收银台提示与置灰、Commerce 冻结、Payment 拦截）；Payment 支付方式目录接口（一期支付方式代码按格式校验 + 已知代码提示）；合同页交互优化需求（另行确认）；多组接受条件；破标审批与阈值。

## 3. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | 运营可新增「功能」开通项（如 RECURRING）与支付方式开通项（如 STC_PAY），设置归属产品与可配维度；停用后新合同不可开通，已开通的不受影响；代码重复 / 格式错误被拒 |
| AC-02 | 迁移后收单目录为 CARD、APPLE_PAY、GOOGLE_PAY（支付方式）+ TOKEN_STORAGE（功能），TOKEN_STORED 计费事件挂到功能开通项；CARD 可配 CARD_BRAND / FUNDING_TYPE / ISSUER_COUNTRY / ISSUER_REGION / CURRENCY / CONSUMER_COUNTRY |
| AC-03 | 合同开通项可配「CARD + VISA,MASTERCARD + DEBIT + AED,USD + 3DS」，读回一致；缺 CURRENCY 被拒；使用不可配维度被拒 |
| AC-04 | 标准价目：建「标准价 · AE001」模板方案 → 版本 → 价格 → 复核（模板复核权限，maker ≠ checker）→ 排期生效；模板不需要合同；价格命中接口不会命中模板价格 |
| AC-05 | 合同新版本基于模板生成：未开通外汇不生成 FX；接受条件不含 AMEX 不生成 Amex 行；开通项币种外的币种不生成；每行带来源模板价格与版本 |
| AC-06 | 相关性：保存含「未开通支付方式」价格时返回提示列表且保存成功；激活被拒（Owner detail 含开通项与价格标识）。零开通：OE 无开通项却有 Client 价格时激活被拒。完整性：按接受条件空间检查（CARD 限 VISA / MC 时缺 MC 价被拒） |
| AC-07 | 破标：合同价低于来源模板（费率 / 固定 / 最低 / 最高 / 阶梯任一）时该组件标破标；无来源标无标准可比；版本列表与工作台待复核项显示破标数 |
| AC-08 | 签署：APPROVED 版本未签署时排期被拒；登记签署（附件文件 ID + SHA-256 + 方式 + 时刻 + 编号）后为 CLIENT_SIGNED，可排期；Partner 方案同样需要签署；存量 ACTIVE / SCHEDULED 版本为免签 |
| AC-09 | Checkout 解析响应含开通项接受条件与 3DS 要求；新开通但无生效价格的开通项不出现 |
| AC-10 | 模板出新版本后，引用旧版本的合同版本显示「标准价已更新」；合同价格不变 |
| AC-11 | 迁移在 mor-test 执行后：CL-F1105E 与 DEMO 合同的 CARD_* 合并为 CARD（条件为原卡组织并集，币种取并集，人工确认项记录在发布记录），历史数据可追溯；价格命中结果与迁移前一致 |
| AC-12 | 页面：目录维护、标准价目、开通条件控件、编辑价格生成 / 补齐 / 来源 / 破标 / 按开通项分组、签署登记、复核队列破标数可用；与现有商务页风格一致；全部商务页回归无错误 |
| AC-13 | 测试数据：mor-test 有 AE001 两套模板（标准 / 优惠）与覆盖「只收 Visa 借记卡」「Apple Pay 限卡组织」「基于模板生成并破标」「待客户签署」「已签署生效」「已开通待定价」「Partner 签署」的演示合同 |
