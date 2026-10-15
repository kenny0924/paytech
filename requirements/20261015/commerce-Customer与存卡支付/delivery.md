# Customer 与存卡支付交付记录

## 适用阶段（草案，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 已确认（第 1 版，G-01～G-04 按建议） |
| 4. 研发实现 | P1（Payment）已本地提交；C1、C2（Commerce）由本会话实现 |
| 5. 综合验证与本地验收 | 含结账页、Client API 与 Payment 的本机联调 |
| 6–7. test 发布准备与部署 | 待定 |
| 8. 提交 | P1 已本地提交；其余另行确认 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | “先做存卡吧，存卡的话就要设计 customer 的模型了”；参考 Stripe 分析 Customer 创建与管理 | 讨论 Customer 模型 |
| 2026-10-15 | 选题 | “思路我觉得没问题”；已存卡支付每次要 CVV（a）、存卡需要按 Client 开通、认可 `consumer_reference` 的 Client 责任 | 继续讨论 |
| 2026-10-15 | 选题 | 两种创建方式“两条都做，开始写需求吧” | 登记需求并交付阶段 1 评审稿 |
| 2026-10-15 | 1 | 存卡同意“不需要正式的法律接收记录”；删除“参考 Stripe 就好”；卡组前缀不准确；“其余的按照建议来” | 修订需求（D-08～D-11），Q-01～Q-07 全部定论，待确认阶段 1 |
| 2026-10-15 | 1 | “可以” | 阶段 1 确认；进入阶段 2+3 |
| 2026-10-15 | 2+3 | “继续”（方案第 1 版与 G-01～G-04 建议） | 阶段 4：本会话只做 Payment 部分 P1；Commerce 部分待指定会话；本机建表另行授权 |
| 2026-10-15 | 4 | 对“授权在本机测试库执行 `db/payment/ddl.sql`”回复“继续” | 执行 Payment 增量 SQL 并运行测试 |
| 2026-10-15 | 4 | “你直接改 commerce 是否可以？现在没有 commerce 的会话不会冲突” | 本会话在本需求范围内修改 commerce-service，C1、C2 分段确认 |
| 2026-10-15 | 4 | “P1 先提交吧” | 本地提交 P1（Payment 部分）及本需求文档；不推送、不部署 |

## 当前阶段与下一确认点

阶段 4 的 P1（Payment）已本地提交（不推送、不部署）；Commerce 部分由本会话实现，进行中。

## 阶段 4 证据：P1 Payment（2026-10-15）

### SQL 执行记录

| 时间 | 目标 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-15 | 本机 `mor-local-mysql-1`（127.0.0.1:13306）库 `mor_payment_channel_test`，root 账号 | [db/payment/ddl.sql](./db/payment/ddl.sql) | 执行前两张卡表均为 0 行；凭证表 9 列、交易工具表 8 列、已存卡表 14 列及两个唯一索引已核对；未触碰 mor-test |

### 代码与工件

| 范围 | 改动 |
|---|---|
| 模型/SQL | PDManer：凭证、交易工具各加 3 个字段，新增 `t_payment_stored_card` 与关系，Card 图更新；Schema、测试 `init/ddl.sql` 同步 |
| API | `ApplyCardInstrumentRequestBody`：`card` 与 `storedCard{storedCardToken, cvv}` 二选一，新增 `customerId`、`saveCard` 及组合校验；新增 `StoredCardApi`（列出、删除、按 Customer 失效）及 DTO |
| Card | 凭证与交易工具 DAL 扩展；已存卡 DO/Mapper/Repository、`StoredCardStatus`；准备流程增加已存卡分支（核对归属与状态、有效期限制工具期限、取回 DEK 加密 CVV）；重放核对 Customer、是否保存与卡来源；`CardService.paymentSucceeded`（标记凭证已验证、按同意新建或更新已存卡，撞唯一键后加锁读取再更新）；`StoredCardService` |
| Execution | `ApplyCardInstrumentRequest` 与上下文传入新字段；`PayActionActor.doInTransaction` 在支付成功事务内调用 `CardService.paymentSucceeded` |
| Web | `ApplyCardInstrumentConverter`；`StoredCardController`、`StoredCardConverter` |
| Test | `StoredCardTest` 8 例；测试 Mapper 与清理加入已存卡；`AbstractPayCardTest` 支持指定 Client 建 Intent |

### 验收映射（Payment 侧）

| AC | 验证 |
|---|---|
| AC-05 存卡条件 | `test_saveCardOnSuccess`、`test_noSaveWithoutConsentOrSuccess` |
| AC-06 凭证验证 | 同上（成功验证、失败不验证、未同意也验证） |
| AC-07 同卡再存 | `test_saveSameCardUpdatesCredential` |
| AC-08 Token 按 Client | `test_tokenPerClient` |
| AC-10 已存卡支付卡值 | `test_payWithStoredCard`（渠道读到的卡值摘要与保存时一致、CVV 为本次输入） |
| AC-11 删除后不可用 | `test_storedCardRejected`（删除后申请 400）、`test_deleteAndInvalidate` |
| AC-12 按 Customer 失效 | `test_deleteAndInvalidate`（Payment 侧接口） |
| AC-14 返回字段 | `test_saveCardOnSuccess`（列表只有 Token、前 6 后 4、后四位、有效期、创建时间） |
| 重放 | `test_replayStoredCard` |

AC-01～AC-04、AC-09、AC-13、AC-15～17 属于 Commerce 或结账页，未在本部分实现。

### 开发检查

- `./mvnw -o verify`（JDK 25）：**116/116** 通过（原 108 + 新增 8）。日志在 scratchpad `verify-stored.log`。
- 测试后本机库卡相关四表与 Intent 均为 0 行，Redis 无 `card:cvv:*`。

### 偏离与说明

- 凭证表增加明文有效期两列（见技术方案 §2.4 补充）。
- 请求体组合校验失败（缺 Customer、卡与已存卡同时提供、已存卡再选保存）按项目约定返回 422；业务拒绝（已存卡不属于该 Customer、已删除）返回 400。
- 已存卡在支付成功事务内更新时不比较版本、不抛并发异常，避免存卡冲突回滚支付成功；已被删除的已存卡不会被恢复。
- 测试排查时临时关闭测试配置的日志脱敏定位异常，已还原。
