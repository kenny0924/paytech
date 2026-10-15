# Risk 执行发布列表与输出声明：mor-test 部署与验收记录

- 日期：2026-10-15（UTC 07:24～07:26）
- 授权：用户回复“提交和部署”
- 结果：**已部署 mor-test，验收通过**；只替换 Risk，没有 SQL，其余 15 个容器镜像不变

## 1. 部署

| 项 | 值 |
|---|---|
| 制品 | 提交 `b26e857` 干净导出构建；jar SHA-256 `a3d5ca4b5874b1a062f7a9cb3c1895f4fec8d1b75e367b8fbefff7db9b0a736b`；镜像 `mor-test/risk-service:release-list-20261015`（`sha256:fcfc9457dab6…`），镜像内 jar 摘要一致 |
| 方式 | [compose-candidate.yaml](./compose-candidate.yaml) 固定 image ID，`up -d --no-deps --no-build --pull never --wait risk-service`；配置沿用已提交的 Compose（`RISK_DECISION_ENVIRONMENT=TEST`） |
| 切换 | 07:25:15 → 07:25:32 healthy，约 17 秒；Nacos `risk-service` 一个健康实例；日志无 ERROR |
| 回退镜像 | `sha256:e0b7a8d484eb8e70042ed029046d3f1db9a494fb2e891cf8f1051dbdd32dccef`（提交 `06025a2`）；无数据变更，直接换回即可 |

## 2. 兼容性（部署顺序调整的依据）

原与 Operations 约定先部署其 BFF 新版。由于结账点响应不变、新增内容只出现在支付点与新接口，经用户指示先部署 Risk，并以前后对比作证据：经临时代理（[配置](../../../20261014/risk-支付反欺诈一期/release/acceptance-proxy.conf)，验收后删除）读取 9 个结账相关只读接口（流程与规则集创作上下文、流程列表与详情、规则集列表与详情、旧候选发布列表、变量列表与创作上下文）：

- 响应字段路径（433 条）部署前后**完全一致**；
- 响应内容唯一差异为读取时刻 `valuesAsOf`。

因此 mor-test 现有 Operations BFF（`34ecac9`）对结账点的严格形状校验不受影响。

## 3. 新能力验收（只读，无写入）

| 检查 | 结果 |
|---|---|
| 支付点决策流创作上下文 | 顶层 `outputs`：`AUTHENTICATION_REQUIREMENT`／认证要求，`NOT_REQUIRED` 无需认证、`THREE_DS_REQUIRED` 需要 3DS，默认 `NOT_REQUIRED`，`allowedEndDecisions=["ALLOW"]` |
| 结账点创作上下文 | 不含 `outputs` 键 |
| `GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK`（含 `status=ACTIVE`） | 200，空页（test 当前没有执行发布） |
| 未声明 Query 参数、缺少 pointCode | 400 |
