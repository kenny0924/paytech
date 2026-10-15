# Finance 账本流水与账户查询

状态：阶段 1 已确认（2026-10-15），确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与目标

FIN-I05“查询账本流水：按账户、主体和币种追溯”。[账务内核第一批](../finance-账务内核/requirement.md)已能形成 Journal 与账户余额，但只能通过数据库核对。本需求在 Finance 提供只读内部查询：主体/币种 → 账簿 → 账户与余额 → 账户流水 → 分录及对方分录，余额可与 Journal 逐行对上。

使用者：Finance 内部与运营核对人员。本期只提供 **Finance 核心服务内部接口**；Operations BFF/页面、Client Console 暴露与 Owner/OE 数据范围授权不在本需求。

## 2. 本期范围

| # | 接口 | 内容 |
|---|---|---|
| Q1 | `GET /ledger-books/v1?entityType=&entityId=&currency=&page=&size=` | 账簿列表 |
| Q2 | `GET /ledger-books/v1/{ledgerBookId}/accounts?accountCode=&ownerType=&ownerId=&balanceBucket=&page=&size=` | 账户列表与余额投影 |
| Q3 | `GET /ledger-books/v1/{ledgerBookId}/accounts/{accountId}/journal-lines?postingDateFrom=&postingDateTo=&page=&size=` | 账户流水，带“该行后余额” |
| Q4 | `GET /ledger-books/v1/{ledgerBookId}/journal-entries/{journalEntryId}` | 分录详情与全部分录行 |

已确认口径：流水带“该行后余额”（按账户正常方向对全量历史累计，翻页与日期过滤不影响累计起点）；账户流水路径必须带账簿 ID 并校验归属。分页复用公共 `PagingRequestBody/PagingResponseBody`；金额按币种精度补齐小数位；查询不加锁、不开事务。

## 3. 明确不做

按 Client/Partner 跨账簿汇总、导出、Gateway 与授权、Operations BFF/页面、试算平衡与期间余额、AUTO 记账、补偿事件、关账。

## 4. 验收标准

| 编号 | 标准 |
|---|---|
| AC-1 | 账簿列表按主体、币种过滤，分页 `total` 准确，排序稳定 |
| AC-2 | 账户列表按科目、核算对象、余额桶过滤，余额等于账户投影 |
| AC-3 | 账户流水按分录序号升序；“该行后余额”按正常方向累计，末行等于账户当前净余额，翻页连续 |
| AC-4 | 按过账日期区间过滤后，“该行后余额”仍按全量历史累计 |
| AC-5 | 分录详情返回全部分录行，借贷相等，来源可追溯到人工调整单号 |
| AC-6 | 账簿/账户不存在、账户不属于账簿、参数非法时返回明确错误（400/422） |
| AC-7 | 真实 HTTP 入口测试，`clean verify` 通过 |

## 5. 适用阶段

阶段 1—5；无 SQL 变更；提交另行确认。
