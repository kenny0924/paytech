# commercial 开通与定价模型调整：确认记录

## 阶段安排（用户 2026-10-20 指定）

用户明确要求「把 1–7 阶段全做了，开多 agent，并创建测试数据」：阶段 1–3 由主会话定稿后锁定技术合同，阶段 4 多 agent 并行研发，阶段 5 本地验收，阶段 6–7 mor-test 发布与验收（含 SQL、Platform 注册、镜像替换、测试数据），全程不再逐阶段停顿；阶段 8 本地提交需另行确认。

## 记录

- 2026-10-20：Q1–Q7 按建议定稿；登记决定 `agent-docs/decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md`；登记本需求；Platform ID 段 124 已占用并通知 code-mor-93；通知 code-mor-b2 暂停改 ops-web 商务文件。

- 2026-10-20：code-mor-b2 发布 ops-web opsux-20261020o（sha256:bbb88dd8…，产品目录缩进），作为本需求 ops-web 回滚基线；cp-web 开发基线同步其 CommercialCatalog.tsx / catalog.css。

- 2026-10-20：技术合同 agent 运行 17 分钟无任何落盘，已停止；主会话自行编写并锁定 technique.md（阶段 3）。

## 阶段 4–5（2026-10-20 / 21）

- 三线并行（Opus 5.5 agent，独立 worktree，基线 main 4ab205e0）：A commercial 终版 `84377cb2`（8 提交，193 测试）；B ops-bff + Platform 块 124 终版 `56ba38fe`（内容 = `7f6d8657`，17 路由、注册 227 行）；C ops-web 终版 `f48fe526`（1205 测试）。合同补充 C1–C12 见 technique.md 第 11 节（含消息交叉后的最终裁决）。
- 集成 worktree `.claude/worktrees/cp-int`：`d15db046`（三线按目录所有权合并）+ `6570ef73`（主会话修正：A 线重生成表归属时丢失 channel-connector `t_channel_config_change`，已恢复并纳入生成脚本；表数断言 +2）。
- 集成验证：commercial `-Dtest=com.paytech.commercial.**` 193/193（首轮与 A 线并发共用本地 mor_commercial 致 4 例失败，串行重跑全过）；ops-bff 1889/1889；ops-web 1205/1205 + build；arch-rules 61/61；三服务 `-DskipTests package` 通过。
- 发布前不回退核对：集成构建的 Platform JAR 与 mor-test 运行 JAR（channel-20261020）逐类比较，仅 `console-resource-capabilities.json` 不同；ops-bff、commercial 仅商务类与 commercial-api / core 不同。
- 偏差：「已开通 · 待定价」合同详情提示、变更历史 PRODUCT_CATALOG 筛选本期未做；client-master PDManer 脚本测试 2 例基线失败（与本需求无关）。

## 阶段 6–7（2026-10-20 UTC）

- 阶段 6 发布（[release.md](./release/release.md)）：备份 → mor_commercial ddl + migration（演练、试跑回滚后提交；CARD_* 合并为 CARD，价格 50 条不变）→ Platform 块 124 注册与激活（PLATFORM_ADMIN 36 → 37）→ 替换 platform / commercial / ops-bff / ops-web（tag capability-20261021，固定 sha256）。人工确认项：DEMO-CL-001 Amex 币种由 USD 扩为 AED + USD 并集，演示数据，接受。
- 阶段 7 验收（[stage7-acceptance.md](./verification/stage7-acceptance.md)）：12 / 13 通过；AC-09 Checkout 端到端受环境阻塞（Risk 资格为失败关闭占位），以 auto 测试与间接核对替代。
- 缺陷 D1（模板生成 / 补齐保留只带功能费的价格行，相关性未锚定支付方式开通项）：A 线 `9ed4c39c`，集成 `c49008e3`，21:30:17Z 部署 capability-20261021b，复验 10 / 10。
- 缺陷 D2（模板版本读回结算四字段为空，导致模板编辑须重填）：A 线 `a9828758`，集成 `c6eea71a`，21:53:50Z 部署 capability-20261021c（`sha256:c6c9b8f6…`），复验 8 / 8（外汇点差模板 v2 原样保存并生效）。
- 接受的偏差：V1 字段格式错误 422 VALIDATION_ERROR（符合 RFC 9457 系统决定）；V2 签署方式 E_SIGNATURE / CLICK_ACCEPTANCE / MANUAL_SIGNATURE；V3 合成 Local Partner 770000001002。
- 主工作区已应用集成内容；与对账会话（code-mor-93）共用的 11 个文件已三方合并并通知对方，提交时按 hunk 拆分。
- 遗留见台账 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commercial-capability-pricing-20261020)「已知缺口」。
- 阶段 8（本地提交）待用户确认。

## 验收反馈 F（2026-10-21，用户确认「可以，直接改」）

用户看 mor-test 后提出三点，确认按方案直接修改，作为本需求阶段 7 验收反馈重走阶段 4–7（只改 ops-web，接口 / 表 / 注册不变，只替换 mor-test ops-web）：

- F1 新增开通项时 CARD 不显示：按设计过滤了该 OE 已开通的项，但无提示。改为已开通项置灰显示「已开通」，点击打开该条编辑。
- F2 接受条件配置改为逐维度行：少值维度平铺可点标签并有明确「不限」，币种候选标签 + 其他，国家可搜索（代码 + 中文名），只显示中文名，3DS 并入条件区，底部实时汇总一句话。
- F3 合同「新增 / 编辑开通项」与产品目录「新增 / 编辑开通项」由居中弹窗改为右侧抽屉，分段布局、底部固定按钮。
- 实施：C 线 cp-web `41f129c8`（集成 `c216ff92`，已应用到主工作区）；ops-web 主工作区 1219/1219 + build。发布：等对账会话页面走查结束后，2026-10-21 03:25:35Z 只替换 mor-test ops-web 为 `sha256:333e7f2b…`（capability-20261021d；源 = 对账 recon-slice1 源码叠加 + 9 个 commercial 文件，叠加前构建与现网 edfb6628 逐字节一致）。
- 验收（[stage7-acceptance.md §8](./verification/stage7-acceptance.md)）：F1–F3 17/17；商务页 18/18、全部运营页 49/49；DEMO2-CL-901 TOKEN_STORAGE 抽屉原样保存 200（数据不变）；截图 `assets/stage7/f-01…f-10`。

## 验收反馈 F 第二轮（2026-10-21）

用户看 F1–F3 后继续提出，确认修改（第 3 点经用户选择「合并为产品与开通页签」）：

- F4 接受条件必填维度（币种）放最上面，汇总句同序；提交 body 不变。
- F5 币种增加可搜索下拉（代码 + 中文名），清单暂写死 20 个常用币种（TODO：改为字典维护）；保留 5 个快捷标签，两者同步。
- F6 合同详情「订阅产品」与「开通项」合并为「产品与开通」页签：按已订阅产品分区块（区头产品名、开通项数、按产品新增），区内为该产品开通项表（全部接受条件标签、3DS、保证金、生效、状态、编辑），已订阅无开通项黄色提示，页底列未订阅与默认具备；新增抽屉按产品过滤。该设计原属 [合同页交互优化](../commercial-合同页交互优化/requirement.md) B 部分，提前在本需求实现，那个需求开工时去掉 B 的这部分。
- 实施与发布：C 线 `32d256a7`（F4、F5）、`c9229fb2`（F6），集成 `aa259ea8`，已应用到主工作区（ops-web 1225/1225 + build）；2026-10-21 03:58:51Z 只替换 mor-test ops-web 为 `sha256:139c157e…`（capability-20261021e，回滚 `333e7f2b…`）。

## 暂停点（2026-10-21 04:10Z，用户离开）

- mor-test 现行：commercial `c6c9b8f6…`、ops-web `139c157e…`（本需求 F1–F6）、ops-bff `9520a545…` / platform `79e33d43…`（对账会话发布，含本需求）。
- 进行中：F4–F6 浏览器验收（只读，结果写验收报告 §9），完成后验收 agent 停止。
- 未做：阶段 8 本地提交（待用户确认；父提交 `2f5f37f7`，共享文件只叠加本需求 hunk）；cp-* worktree 清理（提交后）。
- 快照：`~/Code-MoR-backups/20261021T-pause-commercial-capability/`（需求与设计目录 tgz、共享文档 patch、代码相对 HEAD 的 patch、cp-int bundle `4ab205e0..aa259ea8`，SHA256SUMS）。
- F4–F6 验收（[stage7-acceptance.md §9](./verification/stage7-acceptance.md)）：15/15；商务页 18/18、全部运营页 49/49；无写入。观察：O2「更多币种」选中多个时折叠为「+N」；O3 开通项列表条件标签显示原始代码（借贷记类型 DEBIT），抽屉里显示中文（借记）——待用户决定是否统一。
- F4–F6 观察 O2 / O3 修复（2026-10-21，同范围修复）：C 线 `1bcb86de`（集成 `b7b37de3`）：「更多币种」下拉去掉 5 个快捷币种、选中值全部展开；开通项列表 / 价格矩阵 / 费率预览的条件标签取值显示中文、3DS 标签为「要求 3DS」（`pages/CommercialRateCards.tsx` 同步两行）。主工作区 ops-web 1226/1226 + build。ops-web 镜像 `sha256:46227f3d…`（capability-20261021f，基于币种会话 code-mor-50 现网 366f8b95 源码，重建逐字节一致后叠加 6 个文件）已构建，待币种会话 mor-test 走查结束后替换。

## 阶段 8（2026-10-21）

- 用户确认「可以，提交吧」：本地提交，不推送。父提交 `2f5f37f7`（对账切片 1）；在独立 worktree 组装：集成 `4ab205e0..b7b37de3` 代码三方应用，4 个共享文件只叠加本需求部分（配置页数 56、表归属 +2 与生成器输出、Platform 初始化 SQL 块 124 并补回块 123 结尾 `COMMIT;`），文档只加本需求条目 / 行；不含 `合同页交互优化` 评审稿、`design.pen` 与其他会话改动。组装树验证：arch-rules 62/62（含表归属生成 --check）、ops-web 1226/1226 + build。

## 提交后（2026-10-21）

- 05:17Z ops-web `46227f3d…`（O2 / O3）上线，验收 §10 通过；验收发现 O4（费率预览开通项开启 3DS 时丢接受条件，原有缺陷），主会话修复 commercialPreview.ts（limits 按维度合并为条件）+ 单测，ops-web 1237/1237 + build；05:36Z ops-web `c6d2118f…`（capability-20261021g）。这两处为提交 `5a103f36` 之后的改动，随补充提交入库（父 `13c02c87`，不推送）。
- 06:1xZ 对账会话 code-mor-93 发布 recon-detail-20261021，替换共享 ops-web `70829a3a…` / ops-bff `e822301c…` / platform `3d8bc375…`（源 = main `13c02c87` + 本需求 O4 commercialPreview.ts + 对账改动；对方核对商务 9 页正常，块 124 与 `mor_commercial` 未动），为 mor-test 当前基线；候选 compose 已改指，commercial 仍为 `c6c9b8f6…`。
