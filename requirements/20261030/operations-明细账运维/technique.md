# operations：明细账运维 · 技术方案

状态：阶段 2 已确认（第 1—5 节，2026-10-30）；阶段 3 进行中。第 6 节起的接口、数据和页面设计在阶段 3 填写。需求见 [requirement.md](./requirement.md)。

路径缩写：`ACC` = `paytech/account`，`LED` = `paytech/ledger`，`BIL` = `paytech/billing`，`BFF` = `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff`，`WEB` = `operations-console-web/src`。

## 1. 现状核对结果（F-编号）

### account

- **F-01 接口齐全，运营台只接了一部分。** `AccountPostingApi` 提供进度（`GET /progress`）、按流批次列表（`GET /`）、批次详情（`GET /{postingId}`）和重建（`POST /streams/rebuild`）四个接口。运营 BFF 的 `FinanceSubledgerClient` 只接了批次详情、Owner 账户列表和账户流水。
- **F-02 批次列表字段够用（假设 A-1 成立）。**
  - 每个批次有序号、来源类型（计费事实或桶移动）、生成时间、`AccountPosted` 发布时间、事实号 / 版本 / 类型、应用结果（APPLIED / SKIPPED）、桶移动来源，并且内含全部流水。
  - 每页最多 200 条，翻页用 `nextFromSequence`。
  - 有一个可直接用的信号：`publishedTime` 为空，说明 account 已入账但还没发出 `AccountPosted`。
- **F-03 重建的实现与局限。** `RebuildPostingStreamsProcessor` 每条流开一个短事务：锁流行 → 按流水重算 → 以旧版本 CAS 加一。与入账并发时冲突返回 409；没有流时返回 `STREAM_NOT_FOUND`。操作人只写 INFO 日志。
  - **局限 1**：结果只返回重算的账户数、流水数和最大序号，**不说明有没有账户的余额真的被纠正**。运营点完重建后，无法判断"本来就一致"还是"修好了"。
  - **局限 2**：没有"只检查不修复"的接口，运营没法先确认余额是否不一致。

### billing

- **F-04 进度接口已有，BFF 没接。** `BillingFactQueryApi` 的 `GET /progress` 按账簿主体 + 币种返回未发布 / 已发布事实数和最大事实号。`FinanceBillingFactClient` 目前只接了事实搜索、详情、提交、批准、作废。

### ledger

- **F-05 已消费序号有数据，但没有对外接口。**
  - 存在 `t_consumed_aggregate_version` 中：聚合类型 `ACCOUNT_POSTING`，聚合 ID `{OE|FE}.{entityId}.{currency}.{shardNo}`。
  - 读取方法是 `ConsumedEventRepository.lastVersion`。消费 Handler、追平和关账第 3 步都用它。
- **F-06 关账第 3 步已有同样的比对逻辑。** `StartPeriodCloseProcessor`（约第 82、260—310 行）在事务外调用 billing 和 account 的进度接口，判断两段是否一致：
  - billing 无未发布事实，且两边的事实数和最大事实号相同。
  - account 列出的每条流，ledger 已消费序号等于 account 最大序号。
  - 本需求的 BFF 比对要与这段逻辑口径一致（需求 B4）。
- **F-07 追平接口的行为。** `POST /posting-streams/v1/catch-up`（`PostingStreamCatchUpProcessor`）：
  - 请求体：流身份（主体类型、主体、币种、分片号）和 `maxBatches`。操作人通过 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 传入，缺失返回 400。
  - 同一条流的追平用资源锁，**立即尝试，拿不到锁不等待**。
  - 每个批次单独提交。达到 `maxBatches` 时返回 `caughtUp=false`，可以再次调用继续。
  - 没有 OPEN 期间、与消费并发时返回 409；account 或 billing 不可用时返回 503。
  - 接口注释写明"本期不接控制台，不得直接对外发布"。

### 运营 BFF / Web / Platform

- **F-08 BFF 三个客户端都在，可以直接扩展。** `application.yml` 已有 `OPERATIONS_ACCOUNT_BASE_URL`、`OPERATIONS_BILLING_BASE_URL`、`OPERATIONS_LEDGER_BASE_URL` 三个固定地址（由 `FinanceClientConfiguration` 创建），无需新配置。
  - `/finance/**` 已有严格 JSON 入站配置和操作人解析（`OperatorContext`）。
  - ledger 目前没有过账流相关的客户端方法。
- **F-09 Web 有可复用的部件。** 明细账页的批次抽屉 `WEB/finance/SubledgerPostingDrawer.tsx` 可以直接复用；财务页的金额、时间、加载失败组件都在 `WEB/finance/`。没有现成的"多段进度"展示组件，需要新写。
- **F-10 注册块。** 截至核对时，148 未被占用（"交易查询"需求曾预定 148，已暂停，未写入）。并行会话可能先占用，所以阶段 3 写注册 DML 前要再查一次。
  - 财务一级菜单 ID 为 `900000000000097301`。
  - 写路由模板参照块 143 / 146：增加 `content-type` 和 `idempotency-key` 请求头。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 billing → account 段 | 两边进度接口都有（F-01、F-04），BFF 没接 | 对比事实数和最大事实号 | 只缺 BFF 和页面 | BFF 并行调用两个接口，按 F-06 口径比对 |
| R1 account 过账流 | 进度接口返回各流最大序号和最后入账时间（F-01） | 列出全部流 | 只缺 BFF 和页面 | 直接展示 |
| R1 account → ledger 段 | ledger 有数据，没有接口（F-05） | 每条流的已消费序号和落后数量 | **缺 ledger 只读接口** | ledger 新增已消费序号查询（Q-2 已确认） |
| R2 批次浏览 | account 接口齐全、字段够用（F-02） | 按序号翻页，点开看流水 | 只缺 BFF 和页面 | 转发；抽屉复用 F-09 |
| R3 余额重建 | 接口可用，结果不说明是否纠正（F-03） | 执行并显示结果 | 结果信息不足 | 见 S-01 |
| R4 ledger 追平 | 接口可用，需要操作人请求头，有批次上限（F-07） | 执行并显示结果，可重复执行 | 只缺 BFF 和页面；接口注释要改 | BFF 带操作人转发；每次点击的批次数见 S-02 |
| R5 权限 | — | 查看 / 执行两个权限 | 未注册 | 块 148（写入前复查） |
| AC-11 制造不一致再修复 | 正常运行时 ledger 会自动消费，余额也不会自己出错 | 验证追平和重建确实能恢复 | 需要人为制造异常数据 | 见 S-03 |

## 3. 逐系统影响

| 系统 | 职责 | 影响点 | 代码 / 模型 / 配置 | 依赖与兼容风险 |
|---|---|---|---|---|
| **account** | 明细账 Owner | 默认不改；如 S-01 采纳建议，重建结果新增"余额有变化的账户数" | S-01 采纳时：改重建服务和响应 DTO（新增可选字段）；无 DDL | 新增可选字段，ledger 等现有调用方不受影响；BFF 严格反序列化，所以 **account 要先于 BFF 发布** |
| **billing** | 计费事实 Owner | 不改 | 无 | — |
| **ledger** | 总账 Owner | 新增已消费序号只读接口（复用 `ConsumedEventRepository.lastVersion`）；修改 catch-up 接口注释 | 改代码；无 DDL | 新接口只读，不影响关账和消费；同样要先于 BFF 发布 |
| **operations-console-bff** | 页面聚合 | 新增明细账运维模块：进度聚合 Processor（billing、account、ledger 并行，按段返回错误）、批次列表转发、重建和追平命令转发（带操作人）；在三个现有客户端上扩展方法；错误码映射（account `STREAM_NOT_FOUND` 等、ledger 追平错误） | 改代码；无新配置 | 某一段失败时其余段照常返回（需求 B5） |
| **operations-console-web** | 页面 | 财务菜单下新增"明细账运维"页；`finance` 命名空间增加文案；复用批次抽屉 | 改代码 | — |
| **platform-support** | 菜单、路由、权限 | 块 148：菜单 1、按钮 2、权限 2、授权 2、路由约 5 条（3 GET + 2 POST）及其请求头 / 查询参数 / 限流；`console-resource-capabilities.json` +1；语言包发布 | DML（休眠写入 + 激活文件）；Platform 镜像带上 capabilities | 依赖 mor-test 新版 Gateway（Snapshot 上限 4 MiB）；注意与并行会话的块号冲突 |
| **api-gateway / Nginx** | — | — | 不改 | — |
| **文档** | — | CURRENT_IMPLEMENTATION 条目；account / ledger README 接口表 | 改文档 | — |

## 4. 待决问题（S-编号，需要用户确认）

- **S-01 重建结果要不要说明"修好了几个账户"。** 现在只返回重算了多少账户和流水（F-03）。
  - 建议：account 在重建时比较新旧余额，响应新增"余额有变化的账户数"（可选字段）。改动很小（一个服务方法加一个字段，无 DDL），运营能直接看出是"本来就一致"还是"已纠正 N 个账户"。
  - 备选：不改 account，界面只显示重算数量，说明"已按流水重算"。
  - "只检查不修复"的预检接口本期不做。
- **S-02 每次点"追平"处理多少批次。**
  - 建议：每次最多 200 个批次（正好是 account 一页），保证在 Gateway 超时（10 秒）内返回。没追平时按钮变成"继续追平"，并显示剩余落后数量。
  - 备选：前端自动循环调用直到追平。体验更顺，但长时间占用锁，也不容易中途停下。
- **S-03 AC-11（制造不一致再修复）在哪验证。** 正常运行时不会自然出现 ledger 落后或余额错误，要靠人为制造：
  - 余额被改：直接改 `t_owner_account` 的余额。
  - ledger 落后：暂停 ledger 消费后让 billing 产生事实，或在 RabbitMQ 中阻断投递。
  - 建议：只在本机环境做这两项写入式验证；mor-test 上只验证只读页面，以及在已一致的数据上执行重建 / 追平，确认结果为"无变化 / 已追平"，不在共享测试环境改余额或阻断消息。
  - 备选：mor-test 上也做，需要单独授权写入和恢复步骤。

## 5. 风险与工作量

- **规模：小到中。**
  - 系统：ledger 新增 1 个只读接口；account 视 S-01 改 1 个响应字段；BFF 新增约 5 个接口；Web 新增 1 个页面；Platform 1 个注册块。
  - 无新表、无字段变更、无新配置。
- **R-1 Finance Entity 主体的语义。** account 进度对 Finance Entity 主体会同时返回其关联 OE 的流；billing 进度按账簿主体统计。BFF 必须与关账第 3 步完全同口径，否则可能出现"页面说一致、关账却不通过"。研发时以 `StartPeriodCloseProcessor` 为准，并用同一批数据交叉验证。
- **R-2 追平与实时消费并发。** 消费 Handler 同时在处理时，追平可能返回 409。按 F-07 这是可再次执行的正常结果，界面提示"稍后重试"即可，不算失败。
- **R-3 重建持锁。** 重建逐流锁行，期间该流的入账会等待。对大主体有短暂影响，确认弹窗里要说明。
- **R-4 内部接口对外暴露。** ledger 追平和 account 重建原本是纯内部接口，现在经 Gateway → 运营 BFF 暴露给有执行权限的运营账号。只登记 OPERATIONS 通道路由、`HUMAN_PERMISSION` 授权、低限流；ledger 注释同步更新。

**阶段 2 确认结果（2026-10-30）：** S-01—S-03 按建议。另按用户意见调整 R4：追平是 ledger（下游）按自己的进度向 account（上游）拉取，方向与数据流一致，是原设计的死信恢复路径；它不能解决"规则拒绝"类的落后，所以页面要展示卡住的批次（ledger 已消费序号 + 1，经 account 批次列表 `fromSequence=已消费+1&limit=1` 读取）和追平返回的错误码 / 说明，按钮定位为"排除原因后重放"。原因只有在追平失败后才知道（ledger 不保存最近一次失败），本期不新增失败记录。

**遗留（不在本需求内）：** 死信事实可视化（原 Q-3）、人工余额桶移动、余额一致性预检、ledger 定时自动追平临时故障、ledger 记录最近一次追平 / 消费失败原因。

---

# 阶段 3：方案、接口与数据设计（评审稿，2026-10-30）

**一句话规模（本次方案）：** 涉及 4 个系统（ledger、account、运营 BFF、运营 Web）以及 Platform 注册数据；新增接口 5 个（ledger 1、BFF 4），调整 1 个（account 重建响应新增 1 个可选字段）；0 张新表、0 个字段变更、0 个索引；业务 DML 0 行；Platform 注册块 148 约 35 行（权限 1、授权 1、菜单 1、按钮 2、路由 4、请求头 12、查询参数 2、限流 12）；页面 1 个（含批次浏览子页），Pencil 画板 11 张（主流程 6 + 状态补充 5）。

## 6. 方案总览

```text
运营 Web「明细账运维」
  │ 选账簿（复用现有账簿列表接口）
  ▼
运营 BFF ──GET 账簿入账进度──▶ ledger（新增：复用关账第 3 步的比对逻辑）
  │                                ├─▶ billing GET /progress
  │                                └─▶ account GET /account-postings/v1/progress + 本库已消费序号
  │──对每条落后的流取"卡住的批次"──▶ account GET /account-postings/v1?fromSequence=已消费+1&limit=1
  │──批次浏览────────────────────▶ account GET /account-postings/v1（转发）
  │──批次详情（已有路由）──────────▶ account GET /account-postings/v1/{postingId}
  │──追平（带操作人）──────────────▶ ledger POST /posting-streams/v1/catch-up（已有）
  └──重建（带操作人）──────────────▶ account POST /account-postings/v1/streams/rebuild（已有，响应加字段）
```

**设计决定（相对阶段 2 的细化）**

- **D-1 进度比对放在 ledger，而不是 BFF。** 阶段 2 Q-2 确认的是"ledger 新增已消费序号只读接口"。设计时发现，关账第 3 步的 `StartPeriodCloseProcessor.checkBookPostingProgress` 已经完整实现了按账簿的两段比对（billing → account、account → ledger）。因此本方案让 ledger 把这段逻辑开放为"账簿入账进度"只读接口：
  - 返回的内容包含已消费序号，覆盖 Q-2 的目的。
  - 页面判断与关账判断由同一段代码给出，从根本上消除阶段 2 风险 R-1（Finance Entity 口径不一致）。
  - BFF 不再自己比对，只负责补充"卡住的批次"和页面组装（需求 B4）。
  - 做法是把比对从 Processor 抽到 ledger 的 Domain Service，关账步骤与新接口共用，关账行为不变。
- **D-2 进度以"账簿"为单位。** 页面先选账簿（主体 + 币种），复用 BFF 已有的账簿列表接口（`LedgerBookQueryApi`），不新增主体选择器。这与关账按账簿检查一致。
- **D-3 "卡住的批次"由 BFF 补充。** 对落后的流，BFF 并行调用 account 批次列表（`fromSequence = 已消费 + 1`、`limit = 1`），取出序号、事实号、事实类型、来源、生成时间和发布时间。失败时这一条显示为空，不影响其他内容。
- **D-4 失败原因只在追平失败时出现。** ledger 不保存最近一次失败原因（阶段 2 已确认不新增）。页面在追平返回 4xx / 5xx 时就地展示 Problem 的 `code` 和 `detail`，并保留到刷新为止。
- **D-5 执行类操作的超时。** BFF 的 ledger 客户端已有"长耗时读超时"（`operations.ledger-long-running-read-timeout`，默认 9 秒，硬上限是 Gateway 的 10 秒）。追平和重建都使用它。
  - 追平每次 `maxBatches = 200`（S-02）。如果 BFF 超时，ledger 已经提交的批次照常保留，页面提示"处理时间较长，请刷新进度查看结果"。
  - 阶段 5 实测 200 批的耗时，超过 7 秒就降到 100，并回写本节。

## 7. 接口设计

### 7.1 ledger：新增账簿入账进度查询（1 个）

- 合同：`paytech-ledger-api` 新增 `PostingProgressQueryApi`，`@HttpExchange("/posting-progress/v1")`。
- `GET /posting-progress/v1/ledger-books/{ledgerBookId}`
  - 鉴权：内部接口（与 tax 等内部查询一致，不带操作人）；只读，不开显式事务，不加锁。
  - 路径参数 `ledgerBookId`：十进制字符串，`[1-9][0-9]{0,18}`。
  - 实现：`ext/rest` Controller → 新增 `PostingProgressService`（interface + Impl，从 `StartPeriodCloseProcessor` 抽出 `checkBookPostingProgress` 的计算部分），关账第 3 步改为调用同一个服务。远程调用在事务外；读取已消费序号用本库短事务（与原实现相同）。
  - 错误：账簿不存在 → 400 `LEDGER_BOOK_NOT_FOUND`（复用 ledger 已有错误码，阶段 4 核对名称）。billing 或 account 不可用**不报错**，在对应段落标为 `UNAVAILABLE`（需求 B5）。

响应 `PostingProgressResponseBody`（字段均为字符串或布尔，ID、计数、序号为十进制字符串）：

```json
{
  "ledgerBookId": "900000000000100001",
  "entityType": "OPERATING_ENTITY",
  "entityId": "AE001",
  "currency": "USD",
  "factSegment": {
    "status": "BEHIND",
    "billingPendingCount": "2",
    "billingMaxPendingFactId": "880000000000123460",
    "billingPublishedCount": "1520",
    "billingMaxPublishedFactId": "880000000000123458",
    "accountAppliedCount": "1518",
    "accountMaxAppliedFactId": "880000000000123401"
  },
  "streamSegment": {
    "status": "BEHIND",
    "streams": [
      {
        "streamId": "OE.AE001.USD.0",
        "entityType": "OPERATING_ENTITY",
        "entityId": "AE001",
        "currency": "USD",
        "shardNo": "0",
        "accountMaxSequence": "1530",
        "accountLastPostedTime": "2026-10-30T09:12:03.120000Z",
        "ledgerConsumedSequence": "1510",
        "lag": "20",
        "status": "BEHIND"
      }
    ]
  }
}
```

- `status` 取值：`CONSISTENT`（与关账判定"通过"相同）、`BEHIND`（未通过）、`UNAVAILABLE`（billing 或 account 不可用，段内数字为空）。
- `factSegment` 的判定与 `checkFactsApplied` 完全相同；`streamSegment` 中每条流的判定与 `checkStreamConsumed` 相同，段落状态取各流中最差的一个。
- account 不可用时，`streamSegment.status = UNAVAILABLE`、`streams` 为空（流清单来自 account）。
- 已消费序号用现有的 `ConsumedEventRepository.lastVersion` 读取（与关账第 3 步相同），从未消费过时为 `"0"`。**阶段 4 修订（2026-10-30）**：原设计的 `ledgerLastConsumedTime` 已删除——`ConsumedEventRepository` 属于 Foundation（paytech-messaging），读取 `update_time` 需要改 Foundation 公共接口或在 ledger 复制对 Foundation 表的 SQL，两者都超出范围；页面的时间维度改用 account 最后入账时间和卡住批次的生成时间。

**同时修改：** `PostingStreamCommandApi` 类注释删去"本期不接控制台"，改为"经 Operations 控制台（明细账运维）由有执行权限的运营账号触发"。

### 7.2 account：重建响应新增 1 个可选字段

- `PostingStreamsRebuildResponseBody` 新增 `changedAccountCount`（十进制字符串）：重建前后余额（各桶金额）有任一变化的账户数。
- 实现：`PostingStreamRebuildServiceImpl` 在每条流的事务内重算时，比较旧值与新值并计数；`StreamRebuildResult` 加对应字段。INFO 日志同步输出这个数字。
- 兼容：新增字段，ledger 等现有调用方不读取。BFF 严格反序列化，所以发布顺序是 **account → BFF**。

### 7.3 运营 BFF：新增 4 个接口

全部挂在 `/finance/**` 下，沿用严格 JSON 和 `OperatorContext`。外部路径为 `/api/operations` 加上同一路径。

| # | Method / Path | 权限 | 上游 | 说明 |
|---|---|---|---|---|
| B1 | `GET /finance/posting-progress/v1/ledger-books/{ledgerBookId}` | `FINANCE_ACCOUNTING_READ`（已有） | ledger 7.1；对落后的流并行调用 account 批次列表 | 页面主数据，Processor 聚合 |
| B2 | `GET /finance/posting-streams/v1/{streamId}/postings?fromSequence=&limit=` | `FINANCE_ACCOUNTING_READ` | account `GET /account-postings/v1` | 批次浏览，转发并裁剪 |
| B3 | `POST /finance/posting-streams/v1/{streamId}/catch-up` | `FINANCE_SUBLEDGER_MANAGE`（新） | ledger `POST /posting-streams/v1/catch-up` | 追平，`maxBatches=200`，带操作人请求头 |
| B4 | `POST /finance/posting-rebuilds/v1` | `FINANCE_SUBLEDGER_MANAGE`（新） | account `POST /account-postings/v1/streams/rebuild` | 重建，`operatorId = 操作人 Account ID` |

批次详情抽屉沿用已有路由 `GET /finance/account-postings/v1/{postingId}`，不新增。

**B1 响应**（BFF 页面模型）：直接带出 7.1 的全部字段，并在每条 `BEHIND` 的流上追加：

```json
"stuckPosting": {
  "postingId": "900000000012345678",
  "postingSequence": "1511",
  "sourceType": "ECONOMIC_FACT",
  "factId": "880000000000123390",
  "factType": "PAYMENT",
  "factApplyStatus": "APPLIED",
  "postedTime": "2026-10-30T08:40:12.551000Z",
  "publishedTime": "2026-10-30T08:40:12.902000Z",
  "entryCount": "4"
}
```

- 取不到时 `stuckPosting` 为 `null`。
- `publishedTime` 为空表示 account 还没发出 `AccountPosted`；页面提示这种情况追平同样能补上（追平直接读 account，不依赖消息）。
- ledger 不可用：B1 返回 503 `DEPENDENCY_UNAVAILABLE`，页面整体报错可重试。ledger 是聚合入口，这种情况下无法分段展示。

**B2 请求 / 响应**

- `streamId`：`(OE|FE)\.[A-Z0-9]{1,19}\.[A-Z]{3}\.(0|[1-9][0-9]{0,8})`，BFF 用 `AccountPostedPayload.parseAggregateId` 校验并拆成四部分。
- `fromSequence` 必填，正整数；`limit` 可选，取值 20 / 50 / 100 / 200，默认 50。
- 响应 `{ "items": [批次摘要…], "nextFromSequence": "1561" | null }`。批次摘要即上面 `stuckPosting` 的字段，不含流水明细，点开用详情路由取。

**B3 请求 / 响应**

- 无请求体；`streamId` 同 B2 的校验。
- 成功：`{ "processedCount": "200", "lastSequence": "1710", "caughtUp": false }`。
- 错误原样透出 ledger 的 Problem（`code` + `detail`）：400 规则拒绝 / 数据拒绝，409 无 OPEN 期间或并发冲突（含拿不到资源锁），503 account / billing 不可用；BFF 超时为 504 `DEPENDENCY_TIMEOUT`。

**B4 请求 / 响应**

- 请求 `{ "entityType": "OPERATING_ENTITY", "entityId": "AE001", "currency": "USD" }`，三项必填，格式与 account 一致。
- 成功：`{ "accountCount": "6", "changedAccountCount": "1", "entryCount": "3120", "maxPostingSequence": "1530" }`。
- 错误：400 `STREAM_NOT_FOUND`、409 并发冲突、503 / 504 同上。

**BFF 错误码映射：** 在 `FinanceClientConfiguration` 的 account 工厂中注册 `AccountPostingErrorCode`（如未注册），在 `OperationsErrorStatusResolver` 中映射 `STREAM_NOT_FOUND` → 400；ledger 追平错误码沿用已注册的 `LedgerErrorCode`，阶段 4 逐个核对。

### 7.4 保持不变

billing `GET /progress`、account 进度 / 批次 / 详情接口、ledger 追平接口的合同不变；ledger 关账行为不变（只是改为调用抽出的服务）。

## 8. 数据库与注册数据

- **业务 Schema DDL：0。** 不新增表、字段或索引。
- **业务 DML：0。**
- **Platform 注册 DML：块 148**（`mor_platform`，休眠写入 + 激活两步，参照块 146 的六件套：precheck / 主块 / activate / deactivate / verify / rollback）。写入前再次核对 148 未被并行会话占用。

| 表 | 行 | 内容 |
|---|---|---|
| `t_permission` | 1 | `900000000000148101` `FINANCE_SUBLEDGER_MANAGE` / `FINANCE_SUBLEDGER` / `MANAGE` / `HIGH` / ACTIVE |
| `t_role_permission` | 1 | `148201`：角色 `900000000000000510` → `148101`，写入 REVOKED，激活时改 ACTIVE |
| `t_console_resource` | 3 | `148301` MENU `OPERATIONS_FINANCE_POSTING_OPS`，父菜单财务 `97301`，路由 `/finance/posting-ops`，权限 `97101`（`FINANCE_ACCOUNTING_READ`），排序 45（排在"明细账查询" 40 之后）；`148302` BUTTON `OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP`、`148303` BUTTON `OPERATIONS_FINANCE_POSTING_OPS_REBUILD`，父 `148301`，权限 `148101` |
| `t_gateway_route` | 4 | `148401` GET 进度（B1，`97101`，超时 5000 / 10000）、`148402` GET 批次（B2，`97101`）、`148403` POST 追平（B3，`148101`，body 0，idempotency NONE，响应超时 10000）、`148404` POST 重建（B4，`148101`，body 1024，idempotency NONE，响应超时 10000）；路由代码 `operations-finance-posting-progress` / `-posting-stream-postings` / `-posting-stream-catch-up` / `-posting-rebuild` |
| 请求头 | 12 | GET 各 2（authorization、origin），POST 各 4（再加 content-type、idempotency-key，与写路由模板一致） |
| 查询参数 | 2 | B2：`fromSequence`、`limit`（B1 只有路径参数） |
| 限流 | 12 | 每条路由 3 条：GET 沿用 SHARED_ROUTE 300/分钟 + 50 并发、CLIENT_IP 30 + 3、PRINCIPAL 30 + 2；POST 收紧为 PRINCIPAL 10/分钟 + 1 并发 |

- `console-resource-capabilities.json` 增加 `OPERATIONS_FINANCE_POSTING_OPS`（两个按钮动作），Web `pageCapabilities.ts` 同步。
- 语言包：`finance` 命名空间新增本页文案（en / zh-CN），`nav.resource.OPERATIONS_FINANCE_POSTING_OPS` 菜单名；导入 Platform 后发布运营语言包。
- 注册块按经验要先离线编译并核对 Gateway Snapshot 大小（上限 4 MiB），DISABLED 路由同样会编译进快照。

## 9. 页面设计

页面 `/finance/posting-ops`（菜单"明细账运维"，英文 "Posting operations"），子路由 `/finance/posting-ops/streams/:streamId`（批次浏览）。Pencil 画板在 `operations-console-web/docs/operations-console.pen` 的 y = 107600 与 y = 108900 两行（"财务 · 22—32"），沿用"财务 · 13 明细账查询"的外壳；导出图在 [prototypes/](./prototypes/)。

| 画板 | 内容 | 关键状态 |
|---|---|---|
| P1 财务 · 22 进度总览（已一致） | 账簿选择（主体 · 币种）、检查时间与刷新；三段卡片 ①计费事实入账 → ②account 过账流 → ③ledger 总账消费，各带状态徽标和三项指标；过账流表（流、account 最大序号、最后入账、ledger 已消费、落后、状态、浏览批次）；右上角次要按钮"重建余额" | 全部"已一致" |
| P2 财务 · 23 ledger 落后 | 第 ③ 段和流行变为警示色、"落后 20 批"；流行出现主按钮"追平"；表下"卡住的批次"卡片（来源、事实号、类型、生成时间、AccountPosted 是否发出、流水数，"查看流水"），附一句说明 | 落后 |
| P3 财务 · 24 追平结果与失败原因 | 卡住批次卡片内显示规则拒绝：错误码 + 说明 + 操作人时间，指引"先排除原因，再重放"（前往记账规则 / 重新追平）；下方列出部分追平（"继续追平 · 剩余 120"）、409 稍后重试、已追平三种结果条 | 规则拒绝、部分、409、成功 |
| P4 财务 · 25 重建余额确认 | 遮罩 + 确认框：对象（账簿）、一句话用途、三条影响（不改流水不通知 ledger、可重复执行、入账短暂等待）、取消 / 确认重建 | 确认 |
| P5 财务 · 26 重建余额结果 | 页顶结果条"已重算 6 个账户，其中 1 个余额已纠正"（流水数、最大序号、操作人时间）；另列"无需纠正""没有过账流""409 稍后重试"三种结果条 | 有纠正、无纠正、无流、409 |
| P6 财务 · 27 过账流批次浏览 | 返回进度总览、流信息（account 最大 / ledger 已消费 / 起始序号 / 每页）；批次表（序号、来源、事实号、事实类型、应用结果、生成时间、AccountPosted、流水数、查看流水），卡住的批次高亮；"加载后 50 批"；说明"已跳过""桶移动""未发出"的含义 | 含跳过、桶移动、未发出 |

| P7 财务 · 28 进入页面（未选账簿） | 账簿下拉（搜索、最近使用、经营主体 / 财务主体账簿）浮在空状态上；空状态一句话说明页面用途；"重建余额"置灰 | 初始 |
| P8 财务 · 29 分段不可用 | billing 不可用：第 ① 段灰显"不可用"并可重试，②③ 段与过账流表照常；下方示例 ledger 不可用时的整页错误（503，保留账簿选择，可重试） | AC-10 / 需求差异 2 |
| P9 财务 · 30 财务主体账簿（多条流） | FE 8001 · USD：表中三条流（FE 自身流 + 两条服务费所在 OE 流），其中 OE.AE001.USD.0 落后并标注"服务费所在 OE"；区注说明与关账同口径 | AC-02 |
| P10 财务 · 31 只读权限 | 落后状态下无"追平""重建余额"按钮，流行显示"需执行权限"，卡住批次卡片提示联系有权限的财务运营；浏览批次 / 查看流水仍可用 | AC-09 |
| P11 财务 · 32 窄屏 390 | 三段卡片纵向排列、向下箭头；过账流改为卡片（account / ledger / 卡住批次三个数字，追平与浏览批次两个等宽按钮）；无横向滚动 | AC-V4 |

- 批次详情抽屉沿用已有画板"财务 · 15 明细账查询｜入账批次抽屉"，不重画。
- 结果条就地显示在触发它的位置（追平在流行 / 卡住批次卡片，重建在页顶），刷新进度前保留。
- 窄屏（390）见 P11；阶段 5 仍以真实浏览器走查验收（AC-V4）。

## 10. 研发计划

| # | 系统 | 任务 | 对应验收 |
|---|---|---|---|
| T1 | ledger | 抽出 `PostingProgressService`（关账第 3 步改为调用它，回归关账测试）；`ConsumedEventRepository` 新增带时间的读取；`PostingProgressQueryApi` + Controller；追平接口注释；auto 测试：一致 / 落后 / billing 不可用 / account 不可用 / 账簿不存在 / Finance Entity 账簿 | AC-01—AC-03、AC-10 |
| T2 | account | 重建计算 `changedAccountCount`；auto 测试：一致时为 0、人为改余额后为 N、无过账流 | AC-05、AC-06 |
| T3 | 运营 BFF | 扩展三个客户端；B1 Processor（ledger + 并行取卡住批次）；B2—B4 Controller；错误码映射；模拟服务测试（含 ledger 400 / 409 / 503、超时 504）；边界清单 JSON | AC-01—AC-10 |
| T4 | Platform | 块 148 六件套；capabilities；离线编译快照核对；注册与 Controller 一致性测试 | AC-09 |
| T5 | 运营 Web | 页面与子页、`pageCapabilities` / `ConsolePage` / `menu.ts` 子路由；复用 `SubledgerPostingDrawer`；en / zh-CN 文案；单测 | AC-V1—AC-V4 |
| T6 | 本机综合验证 | 制造三种异常并修复：直接改 `t_owner_account` 余额 → 重建；暂停 ledger 消费产生落后 → 追平；临时删一条记账规则制造拒绝 → 追平失败 → 补回 → 重放；浏览器 en / zh-CN、1440 / 390 走查 | AC-08a、AC-11 |

- 实施顺序：T1 ‖ T2 → T3 → T4 ‖ T5 → T6。T1 / T2 是 T3 的合同前置。
- 发布顺序（阶段 6 细化）：ledger、account → Platform（块 148 + capabilities）→ BFF → Web → 激活块 148 → 发布语言包。
- 测试遵循 `UNIT_TEST_GUIDE`：从真实入口（HTTP）进入，外部依赖用 DTO 模拟服务，不用方法级打桩。

## 11. 验收映射

| 验收 | 设计落点 |
|---|---|
| AC-01 billing → account 一致 / 差额 | 7.1 `factSegment` |
| AC-02 全部过账流（含 Finance Entity 关联 OE 流） | 7.1 `streamSegment`（流清单来自 account 进度，与关账同口径） |
| AC-03 ledger 已消费、落后、卡住的批次 | 7.1 流字段 + 7.3 `stuckPosting` |
| AC-04 批次浏览 | B2 + 已有详情路由 + 画板 P5 |
| AC-05 / AC-06 重建 | B4 + 7.2 + 画板 P4 |
| AC-07 / AC-08 / AC-08a 追平 | B3 + D-4 / D-5 + 画板 P3 |
| AC-09 权限 | 第 8 节：查看沿用 `FINANCE_ACCOUNTING_READ`，执行用新 `FINANCE_SUBLEDGER_MANAGE`；按钮资源 |
| AC-10 分段失败 | 7.1 `UNAVAILABLE`；ledger 自身不可用时整页报错（见 7.3 说明） |
| AC-11 制造不一致再修复 | T6（只在本机） |
| AC-V1—AC-V4 | 第 9 节画板 |

**与需求的差异（请确认）：**

1. 需求 R5 写"只读查看一个权限"：本方案查看**沿用已有的 `FINANCE_ACCOUNTING_READ`**（与明细账查询、余额查询等财务查询页一致），只新增一个执行权限。
2. AC-10 写"任一服务不可用时只影响对应段落"：billing、account 不可用满足这一点；**ledger 不可用时整页报错**，因为进度比对由 ledger 给出（D-1）。
