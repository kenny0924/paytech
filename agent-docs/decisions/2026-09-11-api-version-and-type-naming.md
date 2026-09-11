# 接口版本与代码类型命名

- 状态：Confirmed，来自用户 2026-09-11 的明确要求。
- 适用范围：Code-MoR 各系统后续接口设计、开发及本次规则集列表优化。
- 确认原意：未明确指定接口版本时，不升级版本，直接调整原接口；新接口默认 V1。代码类名不带 V1 等版本字样。
- 现行规范 Owner：[DEVELOPMENT_CONVENTIONS.md](../DEVELOPMENT_CONVENTIONS.md) 第 2 节；HTTP 工程投影：[JAVA_PROJECT_STRUCTURE.md](../JAVA_PROJECT_STRUCTURE.md) 第 4 节。

## 决定

1. 已有接口沿用现有版本与路径；用户没有明确要求时，不因增加字段、调整列表或重构而自行升级为 V2/V3。新接口没有指定版本时默认 `/v1`，优先放入现有能力的版本路径。
2. 类、接口、record 等代码类型名不使用 `V1`、`V2`、`Version1` 等协议版本标记。例如 `RuleSetDetailResponseBodyV1` 改为 `RuleSetDetailResponseBody`。
3. 业务概念中的版本仍按业务语义命名，例如 `RuleSetVersion`、`versionNo`、`baseRuleSetVersionId`；持久化内容格式、Hash Schema 和历史协议常量也不因类型重命名而变更。
4. 原接口内演进仍需明确兼容方式、消费者调整、发布顺序及回退边界。不能为避免升级而静默破坏使用中的消费者；确有无法兼容的外部约束时，先交付具体冲突与方案供用户决定，不自行升级。
5. 新增及本次涉及的类型按此规则实现，重命名时同步提供方、调用方及测试。其他系统的历史类型由对应改动处理，不以这次规则登记为由批量重写全部仓库。

本决定只规定后续行为和命名，不表示已有类已重命名、接口已修改或系统已部署。本次需求仍遵循逐阶段确认。
