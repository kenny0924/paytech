# 卡 3DS-A：channel-connector、payment、acquiring、messaging 目录

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-3ds`（嵌套在主工作区内）
- 公共规则：[00-common.md](./00-common.md)（必须先读）
- 设计权威：[requirement.md](../requirement.md)（AC-02、AC-10、B4 已按 D1/D2 修订）、[technique.md](../technique.md) 第 2、3 节、[execution-3ds-amendment.md](../execution-3ds-amendment.md)、`db/payment/ddl.sql`、`db/channel-connector/ddl.sql`、`db/channel-connector/dml.sql`
- 报告：`tasks/A-core-report.md`

## 范围（technique.md §3.6 顺序 1—6、9）

1. channel-connector：QUERY + 原交易 → `PAY_QUERY` 映射、`PayQueryOutputs`（CLOSED、NOT_FOUND、金额币种）、响应体与状态枚举扩展、`nextActionType`、候选 MID 投影 `threeDsMode`。
2. payment：卡流程不再进 AUTHENTICATE；`PayInput.threeDsRequired / notifyUrl`（`CHALLENGE_REQUIRED` 也按需要 3DS）；按渠道记交互类型；路由优先 3DS 能力、PAY 前能力检查（`THREE_DS_CHANNEL_UNAVAILABLE`）；`ConfirmPayResultProcessor`（intent 锁、事务外查单、短事务 CAS 推进、交易引用已存在跳过、交互字段保留）；`PAY_RESULT_QUERY` Handler 与目录；刷新接口；Payment DDL 与 PDManer / Schema / 物理决策文档 / `table-ownership.yaml`。
3. acquiring：轮询调用刷新、接受 REDIRECT、API 文档改述；测试 `MockPaymentIntentApi` 补方法。
4. channel-connector：回调入站 Controller / Processor、RSA_SHA256 验签（公钥取渠道账号 Vault 参数）、去重入库（复用 `t_channel_callback*`）、`callback_definition_json` 字段与 DDL / DML、messaging 生产者、`ChannelPaymentNotified` 发布；Web 日志回调路径只记元数据。
5. payment：`ChannelPaymentNotifiedListener` 与标准消费表（首次作为消费者）。
6. `paytech/foundation/paytech-messaging/catalog/events.yaml` 登记新事件，目录测试通过。
9. 文档：execution-3ds-amendment.md 列出的指向行、`paytech/payment/README.md`、channel-connector README（不改 CURRENT_IMPLEMENTATION，主会话统一改）。

## 本机 SQL（已授权，按公共规则备份后执行）

- 本机 payment 测试库与 channel-connector 测试库（以各自 test 模块 init DDL / 配置为准确认库名）：执行本卡的增量 DDL；channel-connector DML 只在测试需要时执行到测试库。
- 同步更新各 test 模块 `src/test/resources/init/ddl.sql`。

## 测试

technique.md §3.6 “自动测试”表中 payment、channel-connector、acquiring、foundation/messaging 各行；Payment 测试设施按表下说明扩展。只跑这些模块的测试；全 reactor 只 test-compile。

## 不在本卡

api-gateway、platform-support 块 133、PayerMax harness 脚本（卡 3DS-B）；BIN 需求的任何 Payment 改动。
