# 卡 BIN-D：payment 卡属性快照、Risk 输入、schemeCode、PaymentSucceeded 字段

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-bin-pay`（主工作区之外的同级目录；已含 1d39cd94 + 五张卡合并结果，已暂存为基线，用 `git diff` 看本卡改动）
- 公共规则：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/payment-3DS全链路/tasks/00-common.md`
- 设计权威：[technique.md](../technique.md) 3.4 快照、3.5.4 Payment、3.5.5 Risk 输入、`db/payment/ddl.sql`
- 报告：`tasks/D-payment-report.md`

## 范围（technique.md §3.8 顺序 2 的 payment）

1. `t_payment_instrument_token` 8 个可空列（PDManer 生成器、Schema、物理决策文档、Mapper、DataObject、test init DDL）；Card 客户端映射 `cardAttributes`（未识别时为空）；首次绑定写快照，重放沿用。
2. Risk 输入：`PayRiskCheckActionActor.fillCard`、`PaymentRiskCommand`、`PaymentRiskClientImpl` 传 `issuerCountry`、`cardBrand`、`fundingType`。
3. 路由与执行都传 `schemeCode`（`PayProcessor.route` / `RouteContext`、`PayActionActor.command` / `ChannelExecutionCommand`、`PaymentChannelClientImpl.criteria`）；未识别时不传。
4. `PaymentSucceeded` v2 追加可空 `cardBrand`、`fundingType`、`cardLevel`；`events.yaml` 条目注释同步。

## 本机 SQL（已授权）

本机 payment 测试库执行 `db/payment/ddl.sql`（BIN 部分）；按公共规则先备份。

## 测试

technique.md §3.8 第 2 行 payment 场景。

## 阶段 4 中途裁决（来自卡 BIN-B）

- Risk 线上字段名为 `issuerCountryCode`（不是 `issuerCountry`），`cardBrand`、`fundingType` 不变。
- `PaymentAttemptRiskInput` 暂留 17 参数兼容构造器；本卡改用新构造器后删除兼容构造器。
- Risk 新增冻结决策点版本 220003（合同仍为 v1）；新字段要等运营在 220003 上发布新执行版本后才能被规则使用。
