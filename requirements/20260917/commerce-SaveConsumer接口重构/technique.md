# SaveConsumer 技术说明

遵循[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)及[公共接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。

## 请求与业务

`CustomerCheckoutController` → `SaveConsumerConverter` → `SaveConsumerProcessor.process` → `CustomerCheckoutService.saveConsumerContext`。

1. 独立普通类 RequestBody 及 BillingAddressInfo 保留字段约束，Converter 只适配 Path/Body，不再传递幂等键。内部 Request 负责去首尾空白、NFC、邮箱域名规范化；Processor 通过 Validator 校验规范化后的邮箱，不因提前校验原始邮箱而拒绝原先支持的首尾空白。
2. Foundation AbstractProcessor 拒绝调用方已有事务。Processor 校验原 Token，查询 Session 和 CommerceOrder，复用订单的过期校验；每次请求都检查当前保存资格。
3. 事务外调用原 ConsumerProtectionPort，核对保护引用。SaveConsumer 不再计算请求 Hash，不查询、认领或完成命令，也不保存响应快照。
4. 按订单查询 ConsumerContext；首次保存预分配身份，替换复用原对象和版本。准备前顺序重复请求再次保存，准备开始后拒绝保存，不返回旧成功回执。
5. 一次构建 SaveConsumerContext，持有订单、会话、消费者表对象及本次规范化、保护后的资料。Context 不保存 HTTP 对象、Bean、事务或回调；字段更新由 Repository 在 SQL 成功后执行。

## 事务与并发

资料保存继续与准备、取消锁定同一实际订单行，不单独引入 Redis 互斥域。订单条件 UPDATE 本身取得行锁并校验原版本；去掉仅为 Order → Command 认领顺序新增的显式锁查询。Processor 不再依赖命令 Service 或事务执行器，直接调用本地保存 Service。

`saveConsumerContext` 自带 REQUIRED 事务，单独调用也会原子更新订单版本及消费者资料；已有事务则加入当前事务。写入前复用订单的过期判断，Service 不调用其他 Service 或外部依赖，只调用所属 Repository，不重查旧订单聚合。

条件 UPDATE 核对订单版本、未终止/未支付、未开始准备、原期限以及 Session 身份、版本和可编辑状态；准备与 Intent 的前置条件由原订单 preparation_command_id 表达，不加载合同或支付展示。消费者首次 INSERT 受原唯一键保护，替换按原身份/版本 UPDATE，省略的可选字段写 null。每次成功保存递增订单和消费者版本；并发变化返回冲突，不自动重试或覆盖较新资料。

Repository 确认影响行数后直接同步原对象；任意本地保存失败整体回滚，丢弃本次 Context。没有提交后外部动作，因此不新增 Handler、补偿任务或空推进步骤。

## 清理及兼容

移除旧 API 容器中的 Save record/地址模型、内部 `CustomerCheckoutCommands.Save`、旧入站 consumer 转换、CustomerCheckoutService 的 normalize/requireEditable/save 及旧聚合 Repository/Mapper 的消费者 upsert。其他读取、准备和支付接口不迁移。

HTTP 路径、Body 和响应字段保持；保存接口的 Header 参数及 Filter 强制 Key 校验去掉，Preparation/Payments 的现存 Key 处理不变。历史命令行及其枚举值保留以支持存量读取，但 SaveConsumer 不再访问它们。Java DTO 包路径、Service 方法和 Processor 构造器变化，相关测试需后续适配；不保留无消费者的旧类型。没有表、字段、索引、DDL 或 DML 变更，也不接入真实 Vault。

后续验证覆盖：无 Key 保存、规范化与空值替换、未知/到期 Token、顺序重复保存、准备后拒绝保存、并发保存和准备、Protect 不可用、保存失败回滚、单独 Service 调用的事务原子性以及原对象回写。本次不执行这些验证，状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-save-consumer-20260917)。
