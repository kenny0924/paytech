# card：BIN 库页面重设计与号段列表 · 技术方案

状态：阶段 2+3 评审稿（D1/D2 已确认，页面画板已绘制）· 2026-10-27

**规模（本次方案）：** 涉及 4 个系统（card、platform-support、operations-console-bff、operations-console-web）；新增 2 个接口（Card 号段列表、Platform 运营账号显示名批量查询），调整 3 个现有 BFF 响应（只加可空字段）；新增 0 张表、修改 0 张表、0 个索引；Platform 注册 DML 约 23 行（1 条 Gateway Route 及其 Header / Query 参数 / Guard，块 140）；页面设计 1 个页面重做，画板 9 张（含 03b）。

## 1. 现状差异（阶段 2）

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| S1 号段列表 | Card 只有版本 / 错误行 / 激活 / 单 BIN 查询（`CardBinLibraryApi`），无号段列表接口；BFF、Web 同样没有 | 按版本分页浏览号段 | 未实现 | Card 新增只读接口，BFF 转发，Web 新页签 |
| S2 筛选 | 只有独立“BIN 查询”条，按 6～8 位做最长前缀匹配（`/bins/{bin}`） | 前缀 + 卡组织 + 借贷记 + 发卡国组合筛选，6～8 位时附最长前缀匹配 | 未实现 | 号段列表接口带筛选参数；最长前缀匹配复用现有 `/bins/{bin}` |
| S3/S4 页面结构 | `CardBinLibrary.tsx`：说明句 + 4 张摘要卡 + 查询条 + 版本表 / 详情 / 激活历史同屏 | 页头元信息 + 两个页签 | 设计问题 | 重写页面组件，保留 `binLibrary.ts` 解码与业务辅助 |
| S5 弹窗 | `BinLibraryModals.tsx` 导入、激活 / 回切 | 行为不变、样式统一 | 视觉 | 只改样式与布局 |
| S6 操作人 | 显示 18 位账号 ID；Platform `t_user_account` **没有姓名字段**，只有 `login_name`（可空）与 `email_masked`；现有 `/accounts` 列表需 `OPERATIONS_ACCOUNT_READ` 且不支持按 ID 批量 | 显示可读操作人 | Platform 缺按 ID 批量取显示名的内部接口 | 新增 Platform 内部只读接口（§3.2，**待确认 D1**） |
| 数据库 | `t_card_bin_range` 有 `uk_card_bin_range (version_id, bin_prefix)` | 只读分页 | 无 | 不改表；见 §4 性能说明 |

| 系统 | 职责 | 影响点 | 代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| card | BIN 号段事实 Owner | 新接口 `GET …/versions/{versionNo}/ranges`、Mapper 查询 + 计数 | 代码；无 DDL | 无下游消费者变化 |
| platform-support | 账号事实；Gateway 路由配置 | 新内部接口 `operations-accounts/labels`；注册 1 条运营路由（块 140） | 代码 + 注册 DML | ID 块 140 部署前需在 mor_platform 复核未占用 |
| operations-console-bff | 页面聚合 | 号段列表转发；版本 / 激活列表补 `createdByLabel` / `operatorLabel`（批量取显示名，失败回退） | 代码 | Platform 不可用时不影响主列表 |
| operations-console-web | 页面 | 重做 `CardBinLibrary` 页面、新号段页签、弹窗样式、`card` 命名空间新 key（en + zh-CN） | 代码 + i18n 导入发布 | 多语言需在 Platform 导入发布新 bundle |
| payment / risk-decision | 卡属性消费方 | 无 | 无 | — |

## 2. 方案总览

```text
ops-web ──GET /api/operations/card-admin/v1/bin-library/versions/{n}/ranges──▶ Gateway ──▶ ops-bff ──▶ Card /card/v1/bin-library/versions/{n}/ranges
ops-bff ──(版本列表 / 激活历史组装时)──▶ Platform /operations-accounts/v1/labels?accountIds=…（内部，不经 Gateway）
```

全部为只读查询：无事务、无锁、无幂等需求。

## 3. 接口

### 3.1 Card：分页列出版本号段（新增）

`GET /card/v1/bin-library/versions/{versionNo}/ranges`，调用方只接受 `operations-console-bff`（`CardApiConstants.CALLER_HEADER`，其他 404，与现有接口一致）。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| versionNo | Path | string `[1-9][0-9]{0,8}` | 是 | 版本号 |
| binPrefix | Query | string `[0-9]{1,8}` | 否 | 前缀匹配 `bin_prefix LIKE '{v}%'` |
| cardBrand | Query | VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS | 否 | 精确匹配 |
| fundingType | Query | CREDIT、DEBIT、PREPAID、CHARGE | 否 | 精确匹配 |
| issuerCountry | Query | `[A-Z]{2}` | 否 | 精确匹配 |
| page / size | Query | int | 否 | 公共分页 `PagingRequestBody`；前端默认传 size=50 |

响应 `PagingResponseBody<BinRangeResponseBody>`，按 `bin_prefix` 升序（同版本内唯一，排序稳定）：

```json
{"items":[{"binPrefix":"41111111","prefixLength":8,"cardBrand":"VISA","fundingType":"CREDIT","cardLevel":"CLASSIC",
 "cardProduct":"CONSUMER","issuerCountry":"US","issuerRegion":"NORTH_AMERICA","issuerName":"TEST BANK"}],
 "page":1,"size":50,"total":17}
```

可空字段：`fundingType`、`cardLevel`、`cardProduct`、`issuerName`。错误：参数格式错误 400 `INVALID_REQUEST`；版本不存在 404 `NOT_FOUND`；版本为 VALIDATING / INVALID 时 400 `BUSINESS_CHECK_FAILED`（“版本尚未校验通过，无号段可浏览: v{n}, 状态: {status}”），与 `/bins/{bin}?versionNo=` 现有语义一致。

实现：`ext/rest` Controller → `BinRangeQueryService`（interface + Impl，校验版本状态）→ `BinRangeRepository` → `BinRangeMapper.xml` 的 `selectPage` / `count`（同一组动态条件）。新类型 `BinRangeResponseBody` 放 `api/binlibrary/response`。

### 3.2 Platform：运营账号显示名批量查询（新增，内部，D1）

`GET /operations-accounts/v1/labels?accountIds=1,2,…`，调用方 Header 只接受 `operations-console-bff`，不经 Gateway、不注册路由。

| 参数 | 说明 |
|---|---|
| accountIds | 1～50 个数字账号 ID，逗号分隔；超出 400 |

响应 `{"items":[{"accountId":"221546852372267008","label":"kenny"}]}`：`label` 取 `login_name`，为空时取 `email_masked`（如 `ke***@gmail.com`）；只返回 OPERATIONS Realm 账号，未知 ID 不返回（BFF 回退为 `…267008`）。不返回完整邮箱、状态或角色。

### 3.3 Operations BFF

| 方法 / 路径 | 来源 | 处理 |
|---|---|---|
| `GET /card-admin/v1/bin-library/versions/{versionNo}/ranges` | 新增 | 简单转发 Card 3.1，透传筛选与分页 |
| `GET …/versions`、`…/versions/{n}`、`…/activations` | 现有，**响应加字段** | 版本加可空 `createdByLabel`（对应 `createdBy`），激活记录加可空 `operatorLabel`（对应 `operatorAccountId`）；BFF Processor 收集本页账号 ID 一次批量查 Platform 3.2，失败只记日志、字段为 null，不让主列表失败；`system` 等非数字操作人原样显示 |

新增字段向后兼容，现有前端解码器忽略未知字段。

### 3.4 Gateway 路由注册（Platform DML，块 140）

`operations-card-bin-range-list`：`GET /api/operations/card-admin/v1/bin-library/versions/{versionNo}/ranges` → `/card-admin/v1/bin-library/versions/{versionNo}/ranges`，`HUMAN_PERMISSION` + `CARD_BIN_LIBRARY_READ`（900000000000132101），Header 与 Guard 照抄 132405（SHARED_ROUTE 300/min + 50 并发、CLIENT_IP 30/min + 3、principal 30/min + 2），Query 白名单 `binPrefix,cardBrand,fundingType,issuerCountry,page,size`。路由先以 DISABLED 插入，Card / BFF / Web 部署后激活并重发 Snapshot。行数约：route 1、route_header 10、query_param 6、guard_rule 6（阶段 4 按 132405 实际行照抄核对）。

不新增权限、菜单或按钮资源。

## 4. 性能与边界

- 无筛选或仅前缀筛选：走 `uk_card_bin_range (version_id, bin_prefix)` 有序扫描 + LIMIT，与数据量无关。
- 带卡组织 / 借贷记 / 发卡国且 `COUNT(*)`：在单版本范围内扫描。真实 BIN 库约 30～100 万行时计数可能 0.5～2 秒；本期不加索引（需求约定不改表），在 Guard 30/min 限制内可接受。真实数据导入后超出预期，另立需求加 `idx (version_id, card_brand, issuer_country)`。
- 任何接口不接收 8 位以上数字；前端输入 >8 位即提示“只按 BIN 查询，不接受完整卡号”且不发请求。

## 5. 页面设计

画板位于 `operations-console-web/docs/operations-console.pen`（y=97000 与 y=98380 两行，沿用参考数据页外壳；侧栏同步去掉已并入参考数据的“币种”）。

| # | 画板（节点 ID） | 关键点 |
|---|---|---|
| 01 | 号段数据｜默认（`AOeod`） | 页头：标题 + `v1 生效中` 徽标 + 一行元信息（号段数、激活人与时间、待激活版本链接）；右侧“CSV 模板”“导入新版本”；页签 号段数据 17 / 版本 6 + 待激活圆点；工具栏：BIN 前缀搜索 + “+ 卡组织 / + 借贷记 / + 发卡国” chip + 版本选择；表格 7 列（卡组织品牌标、国旗 + 代码 + 名称、8 位前缀标记）；底部总数与翻页 |
| 02 | 号段数据｜筛选与 BIN 匹配（`RUUzS`） | 前缀 `411111` + 卡组织 Visa chip；表格上方“BIN 411111 识别为 411111（6 位最长前缀）· Visa · 贷记…”匹配条；“清除筛选” |
| 03 | 号段数据｜筛选无结果（`jnf2K`） | 匹配条变为“BIN 999999 在 v1 中未识别”；表内空状态 + 清除筛选 |
| 03b | 号段数据｜无生效版本（`tQOWi`） | 徽标“无生效版本”；空状态引导“查看 2 个可激活版本 / 导入新版本” |
| 04 | 版本｜可激活版本（`qICmx`） | 左：状态分段筛选 + 版本列表（版本号、状态、文件、号段数 / 失败原因、导入人、时间）；右：统计（号段与生效版本差、错误行、导入人）、号段预览前 5 行（复用号段列表接口）、激活记录时间线（类型、vN → vM、操作人、时间）；底部操作栏“查看号段 / 激活 v6” |
| 05 | 版本｜校验失败（`oWdHq`） | 错误提示 + 错误行表（行号 / 列 / 错误 / 原值片段）；底部“重新导入” |
| 06 | 激活确认弹窗（`OYiOy`） | 变更摘要（生效版本 v1 → v6、号段 17 → 8 −9、确认后立即生效）、检查结果（校验通过、号段减少提醒）、影响提示；回切复用同一弹窗，标题改“回切到 vN” |
| 07 | 导入弹窗（`T2jPcd`） | 已选文件、本地预检（表头 7 列、≤50 MB）、前 3 行预览、模板链接；“上传并校验” |
| 08 | 390 宽度（`yQOGq`） | 表格改卡片列表；筛选收为“筛选 (1)”按钮 + 底部抽屉 |

设计只使用现有合同字段：激活请求没有“说明”字段、版本没有“校验完成时间”，画板未加入；版本间号段差异对比、按卡组织 / 发卡国的分布统计需要新聚合接口，不在本期范围（requirement §4）。

## 6. 研发与验证计划

| 顺序 | 系统 | 内容 | 验证（auto，真实链路） |
|---|---|---|---|
| 1 | card | 号段列表接口 | HTTP 入口业务测试：默认分页、各筛选组合、前缀、VALIDATING/INVALID 400、不存在 404、非法 caller 404、size 非法 400 |
| 2 | platform-support | 显示名接口 | HTTP 入口：login_name 优先、回退 email_masked、CUSTOMER 账号不返回、>50 个 400、非法 caller |
| 3 | operations-console-bff | 转发 + 显示名补全 | 测试 HTTP 端点返回 DTO：参数透传、补全成功、Platform 失败回退 null |
| 4 | Platform DML | 块 140 路由注册 | 本地 mor_platform 执行与回滚演练（需单独授权） |
| 5 | operations-console-web | 页面重做 + i18n | 单测（解码器用真实响应形状、筛选参数构造、>8 位拦截）；`i18nSourceGuard`；`npm run build` |
| 6 | 浏览器实测 | en / zh-CN × 1440 / 390 | 对照 AC-1…AC-6、UV-1…UV-5 |

## 7. 待确认

| # | 问题 | 结论 |
|---|---|---|
| D1 | Platform 没有“姓名”，显示登录名或脱敏邮箱需新增 Platform 内部接口 | 用户 2026-10-27 确认保留 |
| D2 | 号段列表默认每页条数 | 用户 2026-10-27 确认 50 |
