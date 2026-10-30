# 联合查询修订 b · 浏览器验收（mor-test，2026-10-30 08:41—09:05Z）

> 执行：jq-web。入口 `http://localhost:28080`。
> 版本：Platform `c82b6f34`、ops-bff `2452aaa7`、ops-web `d236f1d5`（jq-20261030b，273 个文件）、operations-console 语言包 #26。
> 脚本（本目录）：`jq_browser.mortest.mjs`（B1—B11）、`views_capture.mjs`（D-b2 补充截图）。
> 结果：`browser-result.json`、`views-capture.json`；截图 `screens/`（共 95 张，含补充截图 4 张）；日志 `run.log`。
> 写操作：**WRITES=0**，对 mor-test 无写入。模板保存 PUT 截获后中止，版本 v1 → v1；税务只读。

## 1. 结论

zh-CN / en × 1440 / 390 四个组合，共 94 步，**B1—B11 通过**（D-b1 为已知、待用户决定，见第 3 节）。

| 检查 | 结果 |
|---|---|
| 未翻译的 key | 0 |
| 联合查询页面 390 横向滚动 | 0（工作流实例列表 390 仍横向滚动，非本需求，同上一轮） |
| 非预期 HTTP ≥ 400 | 0 |
| 刻意制造的失败 | 3 次：中止的 PUT 2 次，未登录 401 一次 |
| 税务收据签发 415 | 1 次。原因是脚本自带的 POST 缺 `Content-Type`；页面本身发请求时带，上一轮补测为 200。脚本已修正 |
| 控制台 | 只有上述请求对应的资源错误 |
| 登录 | 3 次（两种语言各一次、补充截图一次），均 200 |

## 2. B11（AC-13）

### B11-1 交易订单号 `ord_3LOy…302a8`：每个对象只出现一次

- 结果：16 项 / 7 个系统（修订 a 为 24 项）。
- 各对象节点数（只计成功且有行的节点）：

  | 对象 | 节点数 |
  |---|---|
  | 订单 | 1 |
  | 收银台会话 | 1 |
  | 记账批次 | 1 |
  | 会计凭证 | 1 |
  | 平台流水 | 1 |
  | 计费事实 | **2**（D-b1，经 `JQ_BILLING_FACT_BY_ATTEMPT`，不是“按自身 ID 查”的模板） |

- `loadedSubjects` 只在第 1 跳起发送，各跳带的键类型：
  - 第 1 跳：ORDER_ID
  - 第 2 跳：ORDER_ID、CHECKOUT_SESSION_ID、BILLING_FACT_ID
  - 第 3 跳：ORDER_ID、CHECKOUT_SESSION_ID、ACCOUNT_POSTING_ID、PLATFORM_TXN_ID、BILLING_FACT_ID
  - 第 4 跳：再加 JOURNAL_ENTRY_ID
- 第 0 跳不带 `loadedSubjects`。
- 跳过的视图（`alreadyLoaded`）：

  | 跳 | 视图 |
  |---|---|
  | 第 1 跳 | `JQ_ORDER_BY_ID` |
  | 第 2 跳 | `JQ_CHECKOUT_SESSION_BY_ID`、`JQ_BILLING_FACT_BY_ID` |
  | 第 3 跳 | `JQ_ACCOUNT_ENTRY_BY_POSTING`、`JQ_ACCOUNT_POSTING_BY_ID`、`JQ_STATEMENT_LINE_BY_PLATFORM_TXN` |
  | 第 4 跳 | `JQ_JOURNAL_ENTRY_BY_ID`、`JQ_JOURNAL_LINE_BY_ENTRY` |

### B11-2 按需查看

- 收银台会话节点显示“另有 1 个视图未执行 · 查看完整信息”（en：“1 more view not run · View full details”）。
- 点击后发出 1 次 J3：depth 2，`templateCodes=["JQ_CHECKOUT_SESSION_BY_ID"]`，不带 `loadedSubjects`，返回 200。
- 明细内多出 1 个区块，链路图节点数不变（16）。

### B11-3 会计凭证 ID `241801561934753792`：上游照常展开

- 结果：21 项 / 7 个系统，反查到记账批次、计费事实、订单、收银台会话。
- 这条链路上同一对象仍有多处：会计凭证 3、记账批次 3、计费事实 3。原因是“按其他键查”的模板（如会计凭证按批次）不在跳过范围；规则只跳过“按自身 ID 查”的模板，与设计一致（同 D-b1 性质，见第 3 节）。

### B11-4 1440 七个分组全部可见

- 最后一个泳道右边界 1405，画布右边界 1415（zh-CN、en 相同）。
- 390 卡片视图不变。

### B11-5 “起点 · 未查到”

- 渠道交易号入口有 1 个 0 行的入口节点（渠道回调），显示“起点 · 未查到”（en “Start · none found”）。

### 其他入口

| 入口 | 结果 |
|---|---|
| 支付意图 | 13 项 / 4 个系统 |
| 渠道交易号 | 17 项 / 4 个系统 |
| 退款单号 | 7 项 / 3 个系统 |

三个入口都发送了 `loadedSubjects`，并有对应的跳过视图。

## 3. D-b1 / D-b2 记录（供用户决定，未改模板）

**D-b1 计费事实出现两次。** `计费事实（按订单）` 与 `计费事实（按支付尝试）` 是同一行计费事实，后者不是“按自身 ID 查”的模板，因此不跳过。

**D-b2 子行模板被当作“按自身 ID 查”跳过。** 交易订单号入口下（`views-capture.json`）：

| 节点 | 显示 | 展开后 |
|---|---|---|
| 记账批次 | “另有 2 个视图未执行 · 查看完整信息” | 账户分录（1 条）、记账批次（按批次 ID）（1 条） |
| 会计凭证 | “另有 2 个视图未执行 · 查看完整信息” | 会计凭证（按凭证 ID）（1 条）、凭证分录（6 条） |
| 平台流水 | “另有 1 个视图未执行 · 查看完整信息” | 渠道对账单明细（0 条） |

D-b2 的影响：

- 修订 a 中“凭证分录 6”“账户分录 1”“渠道对账单明细 0”是链路图上的节点；修订 b 中它们不再出现在图上，要点父节点的“查看完整信息”才能看到。
- 同一区块里还会出现与父节点内容相同的“按自身 ID”视图（如“会计凭证（按凭证 ID）”1 条），信息重复。
- 如希望分录类明细直接可见：
  - 可把这类模板的行主体键类型改为分录自身的键类型（需新增键类型），使它们不再被判定为“按自身 ID 查”；
  - 或由服务端只跳过行主体值与触发值一一对应的模板。
- 以上属于模板与规则的决定，本次未改。

## 4. 回归

| 项 | 结果 |
|---|---|
| 税务（`/finance/tax`） | 正常加载。订单 `ord_3LOy…302a8` 状态显示“已登记 · 已开票”（en “Registered · Receipt Issued”），接口 `pageStatus = ISSUED`。最终 115.00 USD，收据 SA-RCPT-2026-000001 可下载（`downloadable = true`）。未登录访问税务接口 401 |
| 承保记录 | 3 行 |
| 查询模板 | 10 行 |
| 工作流实例 | 6 行 |
| 对账汇总 | 空状态，可打开，无 5xx |
| 模板编辑器“联合查询”页签 | 回显正常；保存往返时 `joint` 与服务端一致，未写入 |

## 5. 指定截图

| 内容 | 路径 |
|---|---|
| 交易订单号总览（1440 zh-CN） | `screens/zh-CN-1440-B3-02a-order.png` |
| 节点明细中“查看完整信息”展开（会计凭证，含凭证分录 6 条） | `screens/zh-CN-1440-B11-views-journal.png` |

另有：
- 收银台会话展开：`screens/zh-CN-1440-B11-views-open.png`、`screens/en-1440-B11-views-open.png`
- 记账批次展开：`screens/zh-CN-1440-B11-views-posting.png`
- 平台流水展开：`screens/zh-CN-1440-B11-views-platform-txn.png`
