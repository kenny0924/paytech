# CCT Checkout 查询实现切片

用户确认下一步先实现基础预检和支付准备最终解析；本切片仅修改 CCT api/web/test 和关联文档。Source Relation 保留当前模型。Risk、Commerce、Payment、Finance 业务代码及公共入口不在本切片内。

## 实施顺序

1. 发布两个已确认的私有 HTTP 契约和请求验证，复用 Foundation Problem。
2. 用 CCT 自有 MyBatis 映射读取同一只读快照；预检只验证 Client、Primary、Affiliation、OE、Source、已批准 Scope。
3. 实现按优先级逐候选检查合同、条款版本和法律证据。缺失/失效允许下一候选，歧义/配置冲突明确失败。
4. 最终解析保留新鲜资格端口和输出前修订复核。Risk 尚无此新身份资格查询接口，本期不能伪造 Risk HTTP 契约、批准资格或跨库读取；默认端口明确返回 OWNER_UNAVAILABLE。CCT 本地候选规则可独立验证，真实最终解析成功仍依赖后续 Risk 接入。
5. 执行纯规则、HTTP 边界、真实本地 MySQL 映射与只读查询验证；保持 SQL 人工应用和测试数据隔离。没有数据库授权时只完成代码/构建及不依赖数据库的检查。

## 本地数据库动作

本次目标为 `mor-local-mysql-1` / `127.0.0.1:13306/mor_cct`；授权记录见 [交付记录](./delivery.md)。执行顺序为确认空库、在工作区外备份并核对摘要、人工应用已有完整 [CCT Schema](../../../paytech/client-master/docs/database/sql/client-master-schema.sql)，再校验实际结构并运行测试。不存在初始化业务数据，本切片不编造真实 Client/OE/批准材料。测试只写入及清理自己拥有的合成数据。

失败时停止，不覆盖其他库或数据；建表不是应用部署。备份位置、执行时间、实际结构、测试结果及清理证据统一见 [当前状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#cct-query-20260911)。该完整 Schema 只能用于空库，已初始化环境不能重复执行；已有数据环境仍需单独评审增量迁移。

## 重点验收

- 预检不返回 OE、不查合同或外部 Owner、不创建 Assignment。
- Primary/优先级/有效来源/合同歧义停止查询；明确失效合同可继续下一候选。
- 预检后配置变化以最终查询的新快照为准；跨 Owner 调用不持有数据库锁，返回前检查所有影响候选顺序的 Root 修订。
- PARTNER 分支完整匹配来源方、Partner 合同、双条款版本和唯一平台签约 Party；非 PARTNER 输出为空。
- Risk 未接入时无法得到最终成功响应；不提供配置开关、请求字段或测试替身来绕过资格。
- 查询不得写入业务表，失败不得产生部分上下文。

## 已落地的接口边界

两条接口位于既有 `client-contract-api`，新路径保持 `/v1`，不加公共 Gateway 路由。CCT 私有服务范围使用 `X-Caller-Service: commerce-service` 检查，真正的调用方认证依赖已批准私有部署网络；Header 本身不构成认证。Commerce 继续负责 Client/Order 授权。

新 DTO 的未知字段、非 String 标量类型按 Foundation 返回 400 `INVALID_REQUEST`；字段缺失返回 422 `VALIDATION_ERROR`；规范编号、商业时点、批准目录和禁止/重复 Query 参数返回 422 `INVALID_CHECKOUT_QUERY`。客户端不存在或非 Commerce 调用统一 404。上下文冲突/变化分别为 409 `CCT_CONTEXT_AMBIGUOUS/CCT_CONTEXT_CHANGED`，本库技术故障和未接入 Risk 为 503 `OWNER_UNAVAILABLE`。不新增错误 Envelope。

`legal.documents` 为三份法律内容的 `documentId/fileId/contentHash`，`legal.parties` 为去重的 `partyId/partyProfileId/profileContentHash`；金额、税额、Price 选择和文件内容不进入本接口。Partner 双版本和平台签约 Party 单独成组。

生产能力默认关闭；无默认国家或 Locale。显式启用后需提供已批准目录和 CCT 数据库连接。本地快照使用 REPEATABLE READ 只读事务，事务结束后调用资格端口，再用独立事务复核全部相关 Root 及准确胜出者；低优先级合同的检查按遍历惰性执行。

资格端口当前明确不可用，测试不注入“通过”的 Risk 替代实现，也不在 Risk 仓库定义新的 HTTP 路径。规则测试可以证明 CCT 本地候选结果，不能证明最终 HTTP 200 或真实 Risk 联调。实际通过数量及完整 MySQL 验证状态只见当前实现台账。
