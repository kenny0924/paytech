# 任务卡 C · 条款版本、价格命中、费率卡

> 依赖 A 合入。执行者 Opus 5.5 agent。范围只在 `paytech/commercial`。接口合同：[technique.md §2.2](../technique.md) 条款 8 条、§2.3 版本解析规则、§2.4 价格命中、§2.6 激活校验、§2.7 费率卡、§2.8 `CommercialTermVersionActivated`、stage3 合同（`requirements/20260911/client-contract-OE与Client模型梳理/stage3-contracts.md` 第 2 节）。

## 交付

1. API：`CommercialTermApi`（`/commercial-terms/v1`，Plan / Version / 价格集合 / submit / approve / reject / schedule / activate / 查询）、`CommercialTermVersionPriceApi`（`/commercial-term-versions/v1/{id}/prices`）、`CommercialPriceApi`（`/commercial-prices/v1` 两接口，字段按 §2.4 修订 stage3 合同，错误码枚举 `CommercialPriceErrorCode` 发布在 `api/enums`）、`RateCardApi`（`/rate-cards/v1`）。
2. Processor：写入类继承 `AbstractLockProcessor`，锁 `term-plan:{id}`；命中与费率卡为只读 `AbstractProcessor`。Context：`CommercialTermVersionContext`。
3. Domain Service：`CommercialTermService`（Plan / Version / 价格集合替换 / 状态推进 / 激活与 SUPERSEDED 切换）、`TermVersionActivationValidator`（§2.6 六条，第 6 条留接口位）、`CommercialPriceResolutionService`（§2.4 命中算法：币种精确 → 支付方式精确 → 条件等值 → 维度数多者先 → priority 小者先；事实缺失 / 无命中 / 歧义三类失败）、`RateCardQueryService`（多表组合用 `RateCardView`）。
4. 版本选择：按 §2.3 以 `contextAsOfTime` 在 {SCHEDULED, ACTIVE, SUPERSEDED} 按区间选唯一；提供给 D 线复用的查询方法（Repository 层即可）。
5. Maker-Checker：submit 记 maker、approve 记 checker（HTTP 入参 `operatorAccountId`，maker ≠ checker 否则 400）；记入 BCR 快照。
6. 激活作业：XXL-Job Handler `commercialTermVersionActivation`（按 acquiring / account 现有 Job Handler 写法），扫描 `effective_from ≤ now` 的 SCHEDULED 版本调用同一激活用例；无本机 XXL-Job 时手工触发接口即为 `POST .../activate`。
7. 事件 `CommercialTermVersionActivated`：`compensation_event` 提交后发布；两个目录登记。
8. 测试：§2.11 的命中矩阵与写入矩阵；历史版本回读不受换版影响；激活校验六条各一个失败例；费率卡按币种分块的结构断言。

## 不做

合同 / 画像写入（B）、解析扩展（D）、毛利校验、月量阶梯、IC++（字段校验为拒绝）。
