# Client API 接入管理：Operations 查看与管控

阶段 1 范围已确认。用户在明确职责分工后回复“可以”，确认本期为 **Operations 凭证查看、Scope 目录及路由关联、紧急撤销**；Client Console 自助创建、轮换及凭证授权调整另期处理。确认记录见 [delivery.md](./delivery.md)，阶段 2 分析已由用户“OK”确认，见 [technique.md](./technique.md)；阶段 3 的 [接口/数据实施方案](./design.md) 与 [Pencil 页面](./ui.md) 已确认；阶段推进与实际验证状态见 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。关联 PLT-02、PLT-I03、PLT-16、PLT-I19、PLT-I06 和 E2E-16。

**一句话总结：本期包含凭证查看、Scope 目录、路由关联、紧急撤销 4 项业务能力，拟增加 2 个页面入口并调整既有路由和审计展示；后端由 Platform Support 统一拥有，Operations BFF/Web 提供管理入口，精确 API/数据规模见后续阶段。**

## 1. 职责与典型场景

| 能力 | Client Console | Operations |
|---|---|---|
| 创建、轮换、日常撤销凭证 | 客户自助；本期不实现 | 不提供常规代办入口 |
| 调整单条凭证的 Scope | 在客户允许的范围内自助；本期不实现 | 不提供替客户增减授权入口 |
| 查看凭证信息 | 查看自己 Client 的记录；本期不实现页面 | 按授权查询 Client、前缀、状态、到期时间、Scope、轮换关系和已有操作记录 |
| 完整凭证 | 创建/轮换时一次性取得 | 始终不可查看或取回 |
| Scope 目录与 Route 关联 | 使用平台提供的能力 | 维护编码、名称、说明及 API Route 关联 |
| 紧急撤销 | 后续可撤销自己的凭证 | 单独授权人员在泄漏、异常使用或客户求助时撤销，填写原因并记录既有审计 |

Client Console 与 Operations 复用 Platform 的凭证模型和规则，不分别保存凭证。CCT 继续拥有 Client 主档，Platform 保存本地 Tenant 及已投影的显示信息。

典型场景一：运营查看某 Client 的凭证，确认到期时间、状态及其 Scope/关联 API；没有完整凭证展示或修改授权按钮。

典型场景二：平台维护 Scope 的名称、说明和 Route 绑定；登记目录或修改名称不会向任何凭证自动授予权限。

典型场景三：获授权运营人员针对泄漏的某条凭证执行紧急撤销，之后的新认证失败，其他独立凭证保持原有行为。

## 2. 本期功能

| 编号 | 功能 | 确认范围 |
|---|---|---|
| F-01 | 凭证列表 | 分页查看，按 Client、凭证标识/前缀、状态筛选；包括过期、撤销及所属 Tenant 已失效的记录，便于排查 |
| F-02 | 凭证只读详情 | 展示所属 Client、前缀、状态、有效期、创建时间、轮换来源与 Scope 授权；不投影完整值或 PHC 校验值 |
| F-03 | 使用关系 | 查询 Scope 对应的 Route/凭证，以及凭证授权涉及的 API；按查看权限展示和跳转，配置关联不等于接口当前可调用 |
| F-04 | 紧急撤销 | 对明确的一条凭证执行不可恢复的撤销，独立权限、原因、并发控制和重复提交处理；不批量撤销同一 Client 或整条轮换关系 |
| F-05 | Scope 目录 | 新增编码、名称、说明，查询列表/详情，编辑名称和说明；编码固定，不提供目录启停、物理删除或版本发布 |
| F-06 | Route Scope 关联 | API_SCOPE Route 从统一目录选择一个所需 Scope；同一 Scope 可以供多个 Route 复用，沿用现有路由保存和启停流程 |
| F-07 | 操作记录 | 复用 Operations 审计展示本期目录维护、路由变更、紧急撤销的已有记录及原因；不建立凭证专属历史或回执表 |
| F-08 | 权限隔离 | 分开控制凭证查看、紧急撤销、Scope 查看、Scope 维护和既有路由管理；前端隐藏操作与后端鉴权共同生效 |
| F-09 | 兼容及生效验证 | 保留现有 Credential/Scope Wire、精确匹配、Operations 入口与既有业务页面；验证撤销状态被真实 Platform 认证读取，原始秘密不进入查询响应或日志 |

## 3. 业务边界

1. 凭证创建/使用继续要求有效 CLIENT Tenant；Operations 排查允许查看所属 Tenant 已停用或终止的凭证，并不因此放开认证。紧急撤销不能因为客户已被停用而变得无法处理。
2. 原 Credential 的 status、expires_time 及有效 Scope 授权继续决定认证；Scope 目录是定义与选择来源，不是另一套运行授权。名称变更、目录登记或 Route 绑定不自动给客户授予 Scope。
3. 撤销只作用于指定凭证。撤销提交后开始的新认证应拒绝；已经通过认证并进入处理的请求不承诺被中途追回。原 Scope 授权行保留，查询需区分授权记录与凭证实际可用状态。
4. Permission 控制人类后台操作，API Scope 控制机器接口访问，Tenant/Role 的 scope_key 也不是 API Scope。Gateway 继续精确匹配 Scope，业务 Owner 继续执行资源归属校验。
5. 旧自由文本 Scope 保留可解释性，未经盘点不得自动重写、删除或授予新权限；目录迁移与新绑定校验在方案阶段明确。
6. 沿用既有 v1 或在原接口调整；代码类型名不含 V1/V2。无凭证明文查询、复制、恢复接口。
7. 审计沿用当前 Controller 尽力记录机制，审计故障不撤销业务结果；不把它提升为新审批、可靠审计或新历史系统。

## 4. 功能验收

| 编号 | 可验收结果 |
|---|---|
| AC-01 | 凭证分页、筛选、只读详情及所属 Client 识别正确；已撤销、过期和 Tenant 失效记录仍可排查 |
| AC-02 | 所有 Operations 查询响应、普通日志和审计均不包含完整凭证或 PHC 校验值；前缀仅按批准的管理视图展示 |
| AC-03 | 无新增、轮换、修改有效期和调整凭证授权的 Operations 入口；直接请求也不能获得这些能力 |
| AC-04 | Scope 新增和名称/说明编辑可用，编码不变；Route 使用同一目录选择，已有权限不因维护目录而自动变化 |
| AC-05 | Scope/Route/凭证关系可分页查询；关联数量和结果口径一致，停用 Route 或撤销凭证不会被标记为可调用 |
| AC-06 | 有撤销权限者填写原因后可撤销指定凭证；只读者、失效会话及跨入口身份不能撤销，其他凭证不受影响 |
| AC-07 | 真实 Platform 认证在撤销前接受合成有效凭证，撤销提交后新认证拒绝；不依赖定时扫描、Gateway 重启或缓存失效 |
| AC-08 | 重复提交不重复执行撤销，旧版本冲突可识别；Scope 目录并发编辑不覆盖他人内容 |
| AC-09 | 通过既有审计查询定位本期指定对象的操作与原因，权限过滤继续生效；记录缺失不伪造历史 |
| AC-10 | 原路由、角色、菜单、Operations 会话和风控页面兼容；旧合法 Scope/认证合同及拒绝边界不被目录改造破坏 |

## 5. UI 验收

| 编号 | 可验收结果 |
|---|---|
| UI-01 | 提供 API 凭证查看和 API Scope 两个入口，凭证列表可辨认 Client、前缀和状态；长文本省略并可查看完整非秘密内容 |
| UI-02 | 凭证详情只读，无保存或完整凭证展示；紧急撤销放在统一操作菜单中，使用红色并明确目标和原因 |
| UI-03 | Scope 详情复用只读编辑布局，编辑仅开放名称/说明；Route 选择展示 Scope 名称与编码 |
| UI-04 | 使用关系可跳转至有权查看的现有详情；配置状态与运行可用性清楚区分，不因关联就显示授权成功 |
| UI-05 | 浏览器回退到实际上一页，保留现有草稿保护；不重复增加返回来源页/返回列表按钮，错误不清空输入 |
| UI-06 | 阶段 3 使用 Pencil 修改现有设计，提供列表、详情、Scope 编辑、关联与撤销关键状态供确认 |

## 6. 验证边界与不做项

本期验证 Operations → Gateway → BFF → Platform 管理链、真实本地 MySQL 状态变化及 PLT-I03 认证结果。Client Console 自助流程、首次真实 Commerce Client API 对外开通、CCT 入驻/Tenant IAM、Webhook、文件、通知、支付和风控执行不在本期。原初稿「用新增凭证接通真实业务 Owner」随范围修订退出本期验收，不能为了这项旧目标扩展实现或伪造业务 Client。

开发使用明确的合成测试 Tenant/凭证；test 验收是否准备合成示例、具体范围和 SQL，在发布准备阶段单独列明，不从本次阶段确认直接执行数据库写入。轮换时旧凭证失效的原 Q1 延后至 Client Console 生命周期需求。

## 7. 阶段安排与权威

范围确认后进入阶段 2 现状与影响分析；后续逐阶段确认接口/数据/Pencil、研发、综合验证、test 发布准备、部署及提交归档。此确认不等于 SQL、部署或提交授权。

- [Platform Credential 所有权](../../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md#73-api-credential)
- [Client API Gateway 认证与 Scope](../../../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md#8-credential-认证与-scope)
- [入口应用与 BFF 边界](../../../agent-docs/APPLICATION_ARCHITECTURE.md)
- [全系统错误合同](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)：覆盖早期 Gateway 的旧错误体描述。
- [动态数据库路由](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)
- [既有 Controller 尽力审计](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)
- [前序配置管理](../platform-support-菜单权限与API配置管理/requirement.md)
- [当前实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
