# Commerce Client API 服务入站前缀修正

- Decision status: Confirmed
- Implementation status: See [当前状态台账](../CURRENT_IMPLEMENTATION.md#commerce-client-gateway-path-20260912)
- Deployment status: Not deployed
- Decision date: 2026-09-12
- Scope: Commerce 已有十个 Client API 的服务入站路径，以及对应日志配置、契约测试、HTTP 测试和文档
- Superseded sections: Commerce Client API 契约模块决策 D3、契约模块设计中的服务路径、业务设计第 3.1、4 节及既有实施材料中的旧 `/external/client-api/v1` 服务前缀；其余合同继续有效

## 已确认修正

用户将已有 Commerce Client API 服务前缀统一改为 `/client-gateway-api/v1`。直接修改当前契约，不新增版本、旧路径别名或重定向。

| 路径职责 | 当前合同 |
|---|---|
| Java `@HttpExchange` 入站前缀 | `/client-gateway-api/v1` |
| Commerce 本地实际路径 | `/commerce/client-gateway-api/v1` |
| 公开 OpenAPI 前缀 | `/api/v1` |

本次只修正服务入站路径，Context Path 仍是 `/commerce`；十个操作的 Method、资源后缀、请求响应、可信身份、幂等和公共 `Location` 语义保持。`ClientApiPaths` 是 Java 路径唯一来源，既有请求过滤器继续引用该常量；metadata-only 日志配置和测试同步使用新前缀。

Gateway、Platform 路由注册、Nginx、数据库和其他服务不在本次实现范围。后续接入 Gateway 时，应以 `/commerce/client-gateway-api/v1` 配置下游路径；本决定不表示路由已经配置或部署。历史计划中的旧路径只保留当时实施证据，不能再作为当前接入地址。

本次是用户对既有接口实现的明确路径纠正，不扩展为支付准备等新需求；实现与验证结果记录在当前状态台账及 Commerce README，提交、推送和部署分别等待明确指令。
