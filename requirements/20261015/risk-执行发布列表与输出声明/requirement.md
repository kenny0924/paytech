# Risk：执行发布列表与附加输出声明

- 登记：2026-10-15
- 来源：Operations 会话接入支付风控时提出（[Operations 需求](../operations-console-支付风控配置接入/)），基于 [支付反欺诈一期交接单](../../20261014/risk-支付反欺诈一期/handoff/operations.md) 第 2.4、2.5 节
- 范围：只改 risk-compliance-service；没有 SQL
- 阶段：方案已与 Operations 对齐并经用户确认（2026-10-15 回复“可以”），阶段 1～3 合并，直接进入研发；mor-test 重新部署另行确认

## 1. 执行发布列表（新增）

`GET /decision-releases/v1/executions?pointCode=&status=&page=&size=`

- `pointCode` 必填，未登记返回 400；`status` 可选，取发布状态编码，未知返回 400；分页沿用公共规则（page 1～100000，size 10/20/50/100/200，默认 1/10）。
- 只列执行发布（经决策流），不含结账旧候选发布；按创建时间、发布 ID 倒序。
- 每项：releaseId、releaseNo、pointCode、environment、status、active、flowId、flowCode、flowVersionId、flowVersionNo、contentHash、createdBy、createTime、activatedBy、activatedTime、supersededTime。
- `activatedBy` 取自激活时写入的状态审计（到 ACTIVE 的状态变化操作者），未激活为空。
- 不提供重新激活已替代发布；回滚 = 用旧 `flowVersionId` 新建执行发布并走校验、影子运行、激活。

## 2. 决策流创作上下文返回附加输出声明

`GET /decision-flows/v1/authoring-context` 的每个风控点增加 `outputs`：
- 每项：outputCode、name、allowedValues[{value, name}]、defaultValue、allowedEndDecisions（固定 ["ALLOW"]）。
- 支付点：`AUTHENTICATION_REQUIREMENT`（认证要求），`NOT_REQUIRED`（无需认证，默认）、`THREE_DS_REQUIRED`（需要 3DS）；结账点为空数组。

## 3. 验收

- 列表：空列表、按状态过滤、分页、多次替代后的顺序与 active / activatedBy、未登记风控点与非法状态 400、结账旧候选发布不出现。
- 创作上下文：支付点返回上述声明，结账点为空数组；声明与流程保存校验使用同一来源。
