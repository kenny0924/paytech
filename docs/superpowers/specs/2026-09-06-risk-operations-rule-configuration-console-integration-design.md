# Risk Operations Rule Configuration Console 集成设计

- Status: Confirmed
- Decision date: 2026-09-06
- Implementation/deployment authority: [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
- Scope: Operations Console 对 Risk `CHECKOUT_PREPARE` Rule Set、Freeze、TEST Candidate 和八项静态检查的端到端管理入口
- Decision record: [`2026-09-06-risk-operations-rule-configuration-console-integration.md`](../../../agent-docs/decisions/2026-09-06-risk-operations-rule-configuration-console-integration.md)
- Implementation plan: [`2026-09-07-risk-operations-rule-configuration-console-integration.md`](../plans/2026-09-07-risk-operations-rule-configuration-console-integration.md)

> 本文是设计权威，不是实现或部署记录。当前源码、验证、数据库应用和环境状态只读
> [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 1. 权威关系与取代范围

本文把既有 Risk 私有配置后端接入 Operations Console，必须与以下现行权威共同阅读：

- [Risk Operations Rule Configuration Backend 设计](./2026-09-05-risk-operations-rule-configuration-backend-design.md)
  继续拥有 Rule Set、AST、不可变版本、Freeze、TEST Candidate、静态检查、Hash、幂等、并发和数据库合同；
- [统一公共 API 动态控制面设计](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)
  继续拥有 Target、Route、Guard、Console Resource、Permission 和 Snapshot 边界；
- [Operations Console 第一版实施记录](../../../agent-docs/plans/2026-09-04-operations-console-first-slice.md)
  继续拥有当前浏览器会话、PLT-I02、BFF 分层和前端调用习惯；
- [系统级 Problem Profile](./2026-09-06-system-wide-rfc-9457-problem-details-design.md)
  继续拥有第一方 4xx/5xx Wire Contract、Request ID 和内部 Client 错误校验；
- [`APPLICATION_ARCHITECTURE.md`](../../../agent-docs/APPLICATION_ARCHITECTURE.md) 继续拥有 Gateway、BFF、核心
  服务和私有 HTTP 的部署边界。

本文只取代后端设计第 6.3 节“未来 Gateway/BFF 保存 Route 建议 1 MiB”的建议，将 Operations 渠道的
Rule Set 保存上限确定为 65,536 UTF-8 Bytes。后端 200 Rule、2,000 Node、10,000 Operand、深度 12 和
单个 `IN/NOT_IN` 最多 100 Literal 仍是 Owner 的结构性安全上限，不代表 Operations 渠道必须容纳所有
理论合法组合。

后端设计把 Web/BFF/Gateway/Platform 排除在其实现范围之外；本文批准一个新的集成切片，不反向改写该后端
切片的历史完成边界，也不把设计批准描述成已经实现。

## 2. 目标与非目标

### 2.1 目标

1. 授权 Operations 人员可以从浏览器查询 Rule Set 与 TEST Candidate。
2. 运营人员通过结构化构建器配置规则，不需要理解 JSON 或 AST。
3. 页面可以保存完整不可变 DRAFT、冻结最新 DRAFT、从 FROZEN 创建 TEST Candidate 并执行八项静态检查。
4. 每条浏览器 Route 使用独立且最小的 Platform Permission，并在 BFF 调 Risk 前执行 PLT-I02。
5. Gateway 继续只把 Operations 请求路由到 BFF；Risk 保持私有服务，不成为公共 Target。
6. 保存、并发、未知结果和 Validate 中断都具有明确、可恢复且不重复写入的行为。
7. 规则内容在浏览器存储、日志、错误、URL 和遥测中保持不可见。
8. 五个组件具有可独立执行的契约、边界和端到端测试。

### 2.2 非目标

本切片不设计或实现：

- Raw JSON/AST 编辑器、规则脚本、自然语言生成规则或任意表达式语言；
- Server-side Mutable Draft、自动保存、协同编辑、规则导入导出或完整版本 Diff；
- 从历史 `RETIRED` Version 直接分支或自动 Rebase；
- Shadow、Replay、审批、激活、回滚、PRODUCTION Release 或运行期 Checkout Evaluation；
- 新的 Risk 表、状态、事件、MQ、Compensation、Redis Lock、Scheduler 或跨系统事务；
- Gateway 直达 Risk、BFF 持久化、Platform 复制 Risk 业务事实或新的通用内部安全 Starter；
- Commerce、Payment、Client & Contract、Finance 或 Vault 业务修改；
- 共享环境或生产部署。

## 3. 方案比较

### 3.1 采用：Web 展示模型映射现有 Risk 合同

Operations Web 使用面向运营人员的编辑树，提交时由纯函数生成现有
`CreateRuleSetVersionRequestBodyV1`。BFF 只做本地模型与 `risk-api` DTO 的显式转换，Risk 仍是唯一规则
语义 Owner。

优点是复用已经实现和验证的后端合同，不引入第二套 DSL，不把业务校验放入 BFF。代价是前端必须有一个与
`descriptorCode` 绑定的版本化展示目录，并对未知元数据 Fail-Closed。

### 3.2 不采用：BFF 接收简化 DSL 再转换 AST

该方案可以让浏览器 Payload 更短，但会让 BFF 拥有字段、运算符、值域和树结构语义。Risk 与 BFF 会形成两套
规则解释器，错误路径、Canonical Hash 和版本演进容易漂移，不符合 BFF 无业务状态机边界。

### 3.3 不采用：Risk 新增可变 Draft/表单 API

该方案长期可以支持自动保存和协同编辑，但需要新的资源、生命周期、并发与清理合同，明显扩大首期范围。现有
完整不可变快照足以支持一次受控的 Operations 编辑流程，因此本期不创建该能力。

## 4. 请求体上限的量化决定

按当前 DTO 的全部字段、显式 Null、紧凑 `JSON.stringify` 和 UTF-8 Bytes 测量：

| 样本 | 大小 |
|---|---:|
| 一条 Rule、一个 Predicate、两个 Operand | 约 812 Bytes |
| 八条常见 Rule；每条一个 AND Root、四个 Predicate、八个 Operand | 约 15.5 KiB |
| 九条相同复杂度 Rule | 约 17.4 KiB |
| 十条相同复杂度 Rule | 约 19.3 KiB |
| 一条 `IN` Rule、100 个短 Literal | 约 13.3 KiB |
| 200 Rule、2,000 Node、10,000 Operand 的短值结构样本 | 约 1.53 MiB |

因此 16 KiB 足以容纳一条普通规则，却不足以作为完整规则集快照的稳健上限；1 MiB 对普通 Operations 配置又
缺少业务依据，并且仍不能覆盖后端全部结构组合。首期选择 64 KiB，约可容纳数十条常见复杂度规则，并通过实际
拒绝次数和接近阈值的请求分布决定未来是否另行扩容。

限额按原始 UTF-8 请求 Body 计算，不按 JavaScript 字符数、压缩后大小或 Java `String.length()` 计算。

## 5. 总体架构与信任边界

```text
Operations Web
  -> API Gateway
       - Operations JWT
       - exact Route / Header / Query / Body / Guard
       - trusted Actor, Session, Route Code and Request ID
    -> operations-console-bff
         -> Platform PLT-I02 authorize(current actor, session, route code)
         -> Risk private management API
```

### 5.1 Operations Web

- 只调用同源 `/api/operations/...`；不直接发现或调用 Risk。
- 使用 Platform 返回的 Navigation/Resource 决定菜单和按钮显隐，但把 403 当作最终权威。
- 在内存中维护编辑模型、未确认命令 Body 和随机幂等键；不自动重试写请求。
- 使用 `TextEncoder` 计算精确 UTF-8 Bytes，48 KiB 预警，超过 65,536 Bytes 禁止提交。

### 5.2 API Gateway

- 继续使用现有 `operations-console-bff` Target。
- 新增九条精确 Route；不增加 Risk Target，不批准宽泛 `/risk/` 或任意转发。
- Approved Target Registry 只为 Operations BFF 增加 `/decision-rule-sets/` 和
  `/decision-releases/` 两个精确下游 Root。
- 校验 JWT、动态 Route、Cookie/Header/Query/Body 和六类 Guard，生成可信 Route Code 与人员上下文。
- 不执行 PLT-I02，不拥有 Risk 资源或业务授权。

### 5.3 Operations BFF

- 每个请求先由统一入站拦截器执行 PLT-I02；授权失败时不解析为业务命令，也不调用 Risk。
- Controller 使用 BFF 本地 Request/Response Model，并委托一个本地 `RiskDecisionConfigurationClient` Port。
- HTTP Adapter 依赖轻量 `risk-api`，显式转换 DTO 并调用一个 Risk API 方法。
- 九条接口都是单 Owner 调用，不增加无聚合职责的 Processor、Service、Repository 或数据库。
- 不复制 AST Validator、Canonicalizer、Hash、状态迁移、并发、幂等或静态检查。
- 不聚合、重放或重新计量 HTTP Body；精确 Byte Limit 和 Chunked 拒绝由 Gateway Route 执行，BFF 只做严格
  JSON/Bean Validation。只有经过 Nginx/Gateway 的端到端测试才断言 413，直接 BFF 测试不伪造第二套限额。

### 5.4 Platform Support

- 继续拥有 Account、Session、Permission、Role、Console Resource、Gateway Route/Guard 当前配置和 PLT-I02。
- 不读取或保存 Rule、Node、Operand、Literal、Action 组成或 Candidate Check。
- 通过现有表和手工 SQL 增量完成权限、目录和 Route 配置；不新增表或启动 Seed。

### 5.5 Risk

- 继续使用既有两个私有 `@HttpExchange` 合同和现有业务实现。
- 接收 BFF 传递的已验证 Actor/Session Correlation、Request ID 与 Idempotency-Key。
- 继续拥有所有内容、状态、事务、Hash、幂等、并发和静态检查不变量。
- 当前 Java 内部 HTTP 使用部署网络限制的私网与 Nacos Discovery，不伪造 mTLS、X.509 或强 Workload
  Identity 已经存在。生产启用前必须落实受限网络可达边界；本文不引入可伪造 Header 并称其为强认证。

## 6. Operations 页面

### 6.1 导航与页面

Platform Console Resource 新增一级目录“风控管理”和子页面“规则配置”。前端 Route 固定为：

```text
/risk/rule-configuration
```

页面包含两个常驻 Tab，并可用 Query `tab=rule-sets|test-candidates` 保存当前 Tab；Query 不携带任何规则内容。

### 6.2 Rule Set Tab

列表支持 `page/size/pointCode/ruleSetCode/status`，默认 Point 为唯一的 `CHECKOUT_PREPARE`。列表展示规则集
Code、名称、业务版本、状态、创建人、创建时间、冻结人和冻结时间；Hash 只显示短摘要并允许复制完整值。

详情使用宽 Drawer，显示不可变版本元数据与只读规则内容：

- 当前最新 DRAFT：可“继续编辑”或“冻结”；
- 当前最新 FROZEN：可“保存为新版本”或“创建 TEST Candidate”；
- 早期 FROZEN：不可编辑、分支或 Freeze，但仍可按其准确 Hash 创建 TEST Candidate；
- RETIRED：完全只读，不提供分支、Freeze 或 Candidate 按钮。

Detail 本身不携带 `isLatest`，而 Rule Set List 的 `create_time DESC, rule_set_version_id DESC` 也不是 Head
Authority。Web 不得把第一页首项标成权威 Head。执行 Latest-only 动作前，Web 使用准确
`pointCode + ruleSetCode`、省略 `status`、`size=200` 分页读取完整版本流；所有页必须返回相同 `total`，累计
Item 数必须等于 `total`，ID 和 `versionNo` 必须唯一。只有最高 `versionNo` 的 Item 可作为“本次读取推定的
Head”，再取其 Detail 作为编辑 Base；任何分页漂移、损坏或当前 Detail ID 不相等都禁用编辑/Freeze 并要求
刷新。Candidate 动作不需要 Head，只依据准确 Detail 为 FROZEN。

该推定只适用于当前不删除 Version 的 MVP；Risk 在 Point Root 锁内单调分配 `versionNo`，所以完整且稳定的
同 Code 结果集可用最大值表示该次读取观察到的最新 Version，但 Version No 不要求在单一 Code 内连续。

该推定只控制页面提示，不是并发权威：多次分页之间或读取完成后仍可能出现新 Version，Web 不宣称已锁定
Head。该编排发生在浏览器，每个 BFF 端点仍只调用一次 Risk；最终 Save/Freeze 必须由 Risk 在锁内复验，
409 保留本地内容并要求重新读取，绝不自动覆盖或重放。

首次创建和后续编辑都使用独立全宽编辑视图。后续保存的 Base 永远来自开始编辑时按上述流程确认的当前最新
Version；发生 409 时不得静默改成后来最新的 Base。

### 6.3 结构化规则构建器

一个 Rule Card 包含：

- `ruleName`：主展示名称；
- `ruleCode`：稳定技术编码，创建时必填并显示格式帮助；
- `enabledFlag`；
- 可拖动顺序，转换器按当前可见顺序产生 `10,20,...` 的唯一正 `priorityNo`；
- 可选 `reasonCode`；
- 一个嵌套 Condition Builder；
- 一个由 Authoring Context 返回的终态 Action。

编辑页顶部固定 `pointCode=CHECKOUT_PREPARE`，并要求填写 Rule Set Code、Rule Set Name 和 1–500 字符的非敏感
Change Reason。Rule Set Code 只在首次创建新版本流时可填写；首次保存成功后，它作为
`(pointCode, ruleSetCode)` 版本流身份永久只读，继续编辑不得改名或把旧 Base 携带到新 Code。Base Version
ID/Hash 由页面从当前最新详情取得，不让操作员手工录入。

Condition Builder 使用用户术语：

| 用户控件 | Wire 表达 |
|---|---|
| 满足全部 | `GROUP + AND` |
| 满足任一 | `GROUP + OR` |
| 不满足 | `GROUP + NOT` |
| 条件行 | `PREDICATE` |
| 字段 | 一个 `CONTEXT` Operand |
| 输入值 | 对侧一个或多个 `LITERAL` Operand |

页面不显示 `nodeKey`、`parentNodeKey`、`sequenceNo`、`valueSequence`、`GROUP/PREDICATE` 或原始 JSON。

### 6.4 TEST Candidate Tab

列表支持 `page/size/pointCode/status`，展示 Release No、Rule Set Version、状态、版本、创建人、创建时间和静态
检查汇总。详情 Drawer 展示冻结组成、按版本排序的状态变化以及八项固定顺序检查。

- `DRAFT/version=1`：拥有 Validate 权限时显示“执行静态校验”；
- `VALIDATING/version=2 + zero checks`：显示“恢复校验”，仅原操作员可成功恢复；
- `VALIDATING + eight PASSED + waitingForShadow=true`：显示“静态校验通过，等待未来 Shadow”，无后续按钮；
- `REJECTED/version=3 + eight checks`：显示失败项和安全摘要，按正常业务结果呈现；
- 任何不完整或未知组合：Fail-Closed，不猜测可执行动作。

## 7. Authoring Context 与编辑转换

### 7.1 元数据来源

Risk Authoring Context 继续提供当前 Descriptor、Point、字段、全局 Operator、Action 以及显式空的 Feature、
Parameter、Obligation 和 Limit Binding。当前 API 不提供字段中文名、字段级 Operator、枚举值或输入控件定义，
因此本期 Web 使用按 `CHECKOUT_PREPARE_AUTHORING_V1` 版本化的展示目录。

展示目录只拥有中文 Label、帮助文本和控件提示；字段是否可用、Value Type 和 Action 是否可选仍由本次 Risk
响应经 BFF 安全投影后的事实决定。页面要求投影与目录中的九字段、允许 Operator 和三个 Action Code/Version
精确相容：未知 Descriptor、未知字段、未知 Value Type、未知 Operator/Action、重复 Code 或目录缺项都禁止
编辑，仍允许在安全范围内展示已有只读版本并提示前端需要升级。目录不持有 Handler Code、Implementation
Version 或 Implementation Hash。

### 7.2 当前输入控件

| Field | 控件与即时提示 |
|---|---|
| `country_code` | 大写二字母文本 |
| `locale` | BCP 47 文本及示例 |
| `industry_code` | 大写稳定 Code |
| `mcc_code` | 四位数字文本 |
| `currency` | 大写三字母文本 |
| `entity_source_type` | `PLATFORM/CLIENT/PARTNER` 选择框 |
| 三个 Amount | 精确十进制文本，不转 JavaScript Number |

String 支持 `EQ/NE/IN/NOT_IN/IS_NULL/IS_NOT_NULL`；Number 还支持
`GT/GTE/LT/LTE/BETWEEN`。`IN/NOT_IN` 使用 1–100 个可增删值，`BETWEEN` 使用两个值，Null Operator 不显示
Literal。前端校验只为即时反馈，Risk 的 Validator 仍是最终业务权威。

### 7.3 一对一 Wire 转换

- 编辑树内生成符合 `[\x21-\x7E]{1,64}` 的局部 Node Key；服务端返回的十进制 Node Key 也可作为加载时键。
- 每个 Rule 生成一棵且仅一棵可达树；AND/OR 至少两个 Child，NOT 恰好一个 Child，深度最多 12。
- 拖动后重建连续的 Sibling Sequence；Operand Role 和 Value Sequence 按 Operator 矩阵生成。
- 请求中的所有声明属性都必须存在；可空字段显式为 JSON Null，空集合显式为数组，不省略字段，不附加未知字段。
- Decimal 只执行字符串规范化与校验，不经过二进制浮点；提交与后端黄金向量保持一致。
- Detail 到编辑树、编辑树到 Request DTO 使用两个纯函数，并对语义 Round Trip 建立测试。

## 8. 浏览器与 BFF API

九条 BFF 路径与 Risk 私有路径保持一致，Gateway 只添加外部 `/api/operations` 前缀：

| Route Code | Method | External / Downstream Path | Permission | Body Limit |
|---|---|---|---|---:|
| `operations-risk-rule-authoring-context` | GET | `/api/operations/decision-rule-sets/v1/authoring-context/{pointCode}` / `/decision-rule-sets/v1/authoring-context/{pointCode}` | `RISK_DECISION_CONFIG_READ` | 0 |
| `operations-risk-rule-set-list` | GET | `/api/operations/decision-rule-sets/v1/rule-sets` / `/decision-rule-sets/v1/rule-sets` | `RISK_DECISION_CONFIG_READ` | 0 |
| `operations-risk-rule-set-detail` | GET | `/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` / `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` | `RISK_DECISION_CONFIG_READ` | 0 |
| `operations-risk-rule-set-save` | POST | `/api/operations/decision-rule-sets/v1/rule-sets` / `/decision-rule-sets/v1/rule-sets` | `RISK_RULE_SET_SAVE` | 65,536 |
| `operations-risk-rule-set-freeze` | POST | `/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` / `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` | `RISK_RULE_SET_FREEZE` | 16,384 |
| `operations-risk-test-candidate-list` | GET | `/api/operations/decision-releases/v1/candidates` / `/decision-releases/v1/candidates` | `RISK_DECISION_CONFIG_READ` | 0 |
| `operations-risk-test-candidate-detail` | GET | `/api/operations/decision-releases/v1/candidates/{releaseId}` / `/decision-releases/v1/candidates/{releaseId}` | `RISK_DECISION_CONFIG_READ` | 0 |
| `operations-risk-test-candidate-create` | POST | `/api/operations/decision-releases/v1/candidates` / `/decision-releases/v1/candidates` | `RISK_TEST_CANDIDATE_CREATE` | 16,384 |
| `operations-risk-test-candidate-validate` | POST | `/api/operations/decision-releases/v1/candidates/{releaseId}/validate` / `/decision-releases/v1/candidates/{releaseId}/validate` | `RISK_TEST_CANDIDATE_VALIDATE` | 16,384 |

所有 Route 固定：

- `channel=OPERATIONS`；
- `security_profile_code=OPERATIONS_JWT`；
- `authorization_mode=HUMAN_PERMISSION`；
- Target 为现有 `operations-console-bff`；
- `request_cookie_mode=DROP`、`response_cookie_mode=NONE`；
- `body_receive_timeout_millis=5000`、`response_timeout_millis=10000`；
- 使用既有 Operations Human Permission 的六项 Guard：Shared Route、Client IP、Authenticated Principal 各一项
  Rate Limit 和一项 Concurrency Limit；首期参数沿用现有管理 Route Profile，不为 Risk 创建新算法。

Rule Set List Query Allowlist 恰好为 `page,size,pointCode,ruleSetCode,status`；Candidate List 恰好为
`page,size,pointCode,status`；其余 Route 不接受 Query。GET 不接受非空 Body。

Gateway 的 Header 策略沿用现有 Operations 精确语义：`authorization` 只进入 CORS Allowlist，由 Gateway
消费且不向 BFF 转发；`origin` 允许并转发给 BFF 做精确来源校验；写 Route 才允许并转发 `content-type` 与
`idempotency-key`。可信 Actor、Session、Route Code 和 Request ID 由 Gateway 生成或覆盖，浏览器同名值不得
透传。

## 9. Permission 与 Console Resource

### 9.1 Permission

| Permission Code | Resource Type | Action | Risk Level |
|---|---|---|---|
| `RISK_DECISION_CONFIG_READ` | `RISK_DECISION_CONFIGURATION` | `READ` | `LOW` |
| `RISK_RULE_SET_SAVE` | `RISK_DECISION_CONFIGURATION` | `SAVE` | `HIGH` |
| `RISK_RULE_SET_FREEZE` | `RISK_DECISION_CONFIGURATION` | `FREEZE` | `HIGH` |
| `RISK_TEST_CANDIDATE_CREATE` | `RISK_DECISION_CONFIGURATION` | `CREATE_TEST_CANDIDATE` | `HIGH` |
| `RISK_TEST_CANDIDATE_VALIDATE` | `RISK_DECISION_CONFIGURATION` | `VALIDATE_TEST_CANDIDATE` | `HIGH` |

`PLATFORM_ADMIN` 获得五项，`OPERATIONS_VIEWER` 只获得 READ。现有自定义角色可以独立分配权限。角色 UI 在选择
任一写权限时显式联选 READ，在取消 READ 时先提示并取消相关写权限；这只是授权配置体验，不改变服务端每条
Route 只检查一个明确 Permission 的合同。

### 9.2 Console Resource

| Resource Code | Type | Parent | Display | Frontend Route | Component Key | Permission |
|---|---|---|---|---|---|---|
| `OPERATIONS_RISK_MANAGEMENT` | MENU | Root | 风控管理 | Null | Null | READ |
| `OPERATIONS_RISK_RULE_CONFIGURATION` | MENU | Risk Management | 规则配置 | `/risk/rule-configuration` | `OPERATIONS_RISK_RULE_CONFIGURATION` | READ |
| `OPERATIONS_RISK_RULE_SET_SAVE` | BUTTON | Rule Configuration | 保存为新版本 | Null | Null | SAVE |
| `OPERATIONS_RISK_RULE_SET_FREEZE` | BUTTON | Rule Configuration | 冻结版本 | Null | Null | FREEZE |
| `OPERATIONS_RISK_TEST_CANDIDATE_CREATE` | BUTTON | Rule Configuration | 创建 TEST Candidate | Null | Null | CREATE |
| `OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE` | BUTTON | Rule Configuration | 执行静态校验 | Null | Null | VALIDATE |

父目录和页面都绑定同一 READ Permission，不从父子关系推导授权。按钮只影响显隐，BFF 的 PLT-I02 仍是命令
授权边界。

## 10. BFF HTTP Client 与协议边界

### 10.1 模块和类型

Operations BFF 增加 `risk-api` 轻量依赖，但 Controller、Client Port 和 Service Model 不暴露 Owner DTO。
`RiskDecisionConfigurationClientImpl` 是唯一读取 Owner DTO 字段并执行 Owner↔BFF 显式转换的位置。专用 Client
配置和成功响应 Converter/Shape Validator 只可用准确 Owner `Class`/泛型 `Type` Token 登记及解码九个传输 Root，
不得读取字段、建立浏览器投影或从除既有 typed Risk API Proxy 外的 Bean 签名泄漏 Owner 类型。现有 `web + test` 模块足够，不新增空 API、
Domain Service 或持久化模块。

Browser Wire 使用 BFF 自有的 V1 Model 名称。Rule Set List/Detail、Create/Freeze Rule Set、Candidate
List/Detail、Create/Validate Candidate 八个成功响应的 JSON 属性、类型、Null 与集合/数组排序合同逐字段等同
Risk 后端设计第 7.4 节的对应 V1 Schema，两个 List 继续使用 `items/page/size/total`；这只是显式 DTO 映射，
不是 Java Owner 类型穿透。

Authoring Context 是唯一有意收窄的浏览器投影：Root、Point、Field、Operator 以及四个显式空集合逐字段不变；
Action 只返回 `actionCode/actionName/actionCategory/versionNo/terminalFlag/decisionCode/mergePolicy/`
`shadowSupportedFlag/status`，明确删除 `handlerCode/implementationVersion/implementationHash`。BFF 仍须对
Risk 上游包含这三字段的完整 V1 响应执行严格格式和 Null 校验，但不得把实现指纹返回浏览器或写入日志。Web
只按 Descriptor、Action Code/Version 和其余公开投影匹配展示目录；Handler 组成继续留在 Risk/BFF 私有内存边界。

Risk Client 使用独立 Factory 和 Discovery Interceptor，目标服务名固定为 `risk-service`，不能复用硬编码
Platform 地址的 Interceptor。它传播当前 Request ID，使用私有 HTTP 和 Nacos LoadBalancer。

### 10.2 严格 JSON 与错误

- BFF 入站 Request DTO 拒绝未知字段、缺失 Required 属性、类型不匹配和隐式省略 Null。
- Risk Client 从一开始只接受系统级 RFC 9457 Problem，不为尚未部署的新消费者加入旧四字段兼容分支。
- 注册 `RISK_DECISION_CONFIGURATION_NOT_FOUND`，安全映射为浏览器 404；公共 400/409/500/503 使用 Foundation
  现有语义。
- Risk 200 响应的 ID、Code、枚举、集合、版本和必需 Null 经过显式校验；损坏响应返回 BFF 的 502，不返回
  部分页面模型。
- Risk Client 使用 `connectTimeout=2s/readTimeout=4s`，不跟随 Redirect，不重试写请求。Gateway 的 10s
  Response Timeout 维持 Platform、Gateway 和现有 BFF 配置详情共同允许的绝对上限，并为 PLT-I02、Risk
  调用和 BFF 开销保留总预算；Nginx Proxy Read Timeout 保持 15s。

### 10.3 外部 Idempotency-Key

BFF 浏览器合同收窄为 `[A-Za-z0-9_-]{16,128}`，不需要暴露 Risk 内部允许的全部字符集。Save、Freeze 和
Create Candidate 使用随机 UUID 风格 Key。只要请求结果未知且页面仍保留同一个规范 Body，就复用同一个 Key；
用户修改 Body 或得到确定性失败后，下次提交生成新 Key。

Validate Key 固定为：

```text
risk-validate-v1-{releaseId}-{expectedVersion}
```

Web 必须按 Path 中的 `releaseId` 和规范 Request Body 中的 `expectedVersion` 生成该值。BFF 在 PLT-I02 授权
之后、调用 Risk 之前独立重建 Expected Key，并与 `Idempotency-Key` Header 做字节级精确比较；不一致时返回
BFF 自有的 RFC 9457 400，Risk 零调用。BFF 不替换错误 Key，也不能把“任意合法格式的 Key”转发给 Risk，
否则第一次事务 A 可能使用一个刷新后无法重建的 Key，制造无法按本协议恢复的 Candidate。

Candidate 首次 Validate 从 `DRAFT/version=1` 发起。若详情显示 `VALIDATING/version=2 + zero checks`，当前合同
可以从该唯一状态迁移重建原 `expectedVersion=1` 和相同 Key。页面还要把当前 Account ID 与
`DRAFT -> VALIDATING` State Change 的 `actorId` 比较；只有原 Actor 的 caller scope 能显示和执行恢复。其他
Actor 不得伪造接管，页面引导其联系原操作员或从同一 FROZEN Rule Set 创建新 Candidate。

## 11. 命令与查询流程

### 11.1 页面初始化

页面先取 Navigation 授权，再读取当前 Tab 的第一页；Rule Set Tab 额外读取 Authoring Context，Candidate Tab
不为隐藏内容预取 Rule Set 或规则正文。选中 Rule Set 后只在需要判断 Latest-only 动作时按第 6.2 节分页
读取完整版本流，再取推定 Head Detail；不把 Head 推定放进 BFF 聚合，也不把它描述成 Owner 权威。
Authoring Context 不可用、目录漂移或返回半套元数据时，查询历史仍可按安全模型显示，所有编辑命令
Fail-Closed。

### 11.2 保存 Rule Set

1. 前端本地验证编辑树并生成完整请求 DTO。
2. 使用 `TextEncoder` 对实际 `JSON.stringify` 结果计数；48 KiB 预警，65,537 Bytes 起禁止发送。
3. 首次保存的 Base 两字段显式为 Null；后续保存使用开始编辑时最新 Version 的 ID/Hash。
4. Gateway 执行 65,536 Bytes、Header、Guard 与 JWT 边界。
5. BFF 执行 SAVE 的 PLT-I02，随后只调用一次 Risk Create Version。
6. Risk 创建新 DRAFT；如果 Base 仍为 DRAFT，则按后端合同原子退役旧版。
7. 成功后前端清除内存编辑态并刷新列表/详情。

### 11.3 Freeze

页面以确认框展示 Rule Set Code、Version 和 Hash 摘要，Body 只发送 `expectedContentHash`。BFF 执行 FREEZE
授权后调用一次 Risk。成功后页面立即只读并刷新；任何 Hash/状态冲突都不自动重新提交。

### 11.4 创建和验证 Candidate

Create Candidate 只从 FROZEN 详情发起，Body 为 Rule Set Version ID 和准确 Hash。成功后打开 Candidate 详情。
Validate 从 DRAFT/version=1 发起，使用确定性 Key 和 `expectedVersion=1`。若响应为：

- `VALIDATING + allStaticChecksPassed=true`：显示等待未来 Shadow；
- `REJECTED + allStaticChecksPassed=false`：显示八项检查与失败摘要；
- 请求结果未知：保留当前页面命令，或在重新进入详情时按第 10.3 节识别并恢复；
- 不可达状态或不完整检查：显示系统错误和 Request ID，不猜测结果。

## 12. 并发、错误和安全

### 12.1 并发

- Save 的 409 保留本地编辑内容，并在独立只读 Drawer 展示最新 Version；不自动合并、覆盖或 Rebase。
- 用户确认重新编辑后，以最新 ID/Hash 形成新 Body 和新随机 Key。
- Freeze/Create Candidate/Validate 的 409 都要求刷新事实；除 Validate 明确恢复矩阵外不自动重试。
- BFF 无状态，不保存 Pending Command，不参与 Owner Lock 或数据库事务。

### 12.2 浏览器错误行为

| 结果 | 页面行为 |
|---|---|
| 400 | Malformed JSON、Header/Path/Query 或严格 Shape 错误；显示安全 Detail，保留表单 |
| 422 / `VALIDATION_ERROR` | 合法 JSON 的字段语义错误；只显示经过校验的 `errors[]`，保留表单 |
| 403 | 提示权限已变化并刷新 Navigation；不把按钮显隐当授权 |
| 404 | 关闭过期详情，刷新相应列表 |
| 409 | 保留编辑或命令上下文，要求人工刷新/确认 |
| 413 | Save 显示 64 KiB；Freeze/Create/Validate 显示 16 KiB；保留对应编辑或命令上下文 |
| 500/502/503/504 | 显示安全说明与 Request ID；不展示上游原文或 Payload |
| 200 + REJECTED | 展示业务检查结果，不进入通用错误通知 |

浏览器 GET 可以沿用现有 Access Token 单飞刷新规则；POST 不因 401、超时或 5xx自动重放。

### 12.3 日志和数据分类

Rule AST、Literal、阈值、Handler 组成和完整 Request/Response 都是机密风控策略：

- BFF 的 `/decision-rule-sets` 与 `/decision-releases` 加入 Metadata-only Path Prefix；Risk 现有相同配置保留；
- Gateway/Nginx 不记录 Body、Query、Cookie、Authorization 或 Idempotency-Key；
- 前端不把规则内容写入 Local Storage、Session Storage、IndexedDB、URL、Console、错误遥测或分析事件；
- 普通日志只允许 Request ID、Actor、Route/Resource ID、状态、版本、Hash 摘要和耗时；
- Problem、Toast 和静态检查摘要不得回显 Literal 或完整规则；
- PAN、CVV/CVC、完整磁道、PIN/PIN Block、Vault Token、Consumer 原值、网络原值、Credential 和 Secret
  继续禁止进入任何规则输入或诊断输出。

## 13. Platform 数据与 Gateway 限额

### 13.1 数据范围

复用现有 `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、Route Header/Query/Guard
表；不修改 Schema、PDManer、DataObject、Mapper 或业务 Repository。

Canonical 初始化 DML 增加本设计的目标目录。既有环境另提供一份按精确 ID/Code/内容验证、遇错回滚、同内容
可重放但不重新启用人工停用项的手工增量 SQL。Java 应用、测试和容器不得自动应用它。

共享或现有环境的增量 Route 初始为 `DISABLED`；只有目标版本全部部署、Snapshot 编译通过和 Smoke 前置满足后
才能单独启用。Console Resource 在 Route 可用前不对普通角色开放。

### 13.2 三处绝对上限

以下当前 16 KiB 的共同绝对上限必须同步改为 65,536：

1. Platform Gateway Configuration Compiler；
2. API Gateway Snapshot Policy Validator；
3. Operations BFF Gateway 配置详情的本地协议校验。

这些位置只声明允许配置的最大值，不能根据 Risk Path 硬编码例外。数据库中只有 Rule Set Save Route 配置
65,536；旧 Route 值和其他新 Route 值保持不变。

Test Environment Nginx 的 `client_max_body_size` 从 32 KiB 提升为 128 KiB。它是高于公开 API 限额的 Connector
Envelope，使 65,537 Bytes 的正常边界请求能够到达 Gateway 并获得统一 RFC 9457 413；超过 128 KiB 的极端输入
仍可由 Nginx 在第一方 Problem Writer 之前拒绝。

## 14. 测试设计

### 14.1 Operations Web

- Authoring Context 与版本化展示目录精确匹配、未知/重复/缺失元数据 Fail-Closed；
- Detail Flat Nodes 到编辑树，再到 Create Request 的语义 Round Trip；
- AND/OR/NOT Child 数、树深 12/13、唯一 Node Key、连续 Sequence 和 Rule Priority；
- String/Number Operator 矩阵，IN 1/100/101、BETWEEN 两值和 Null Operator；
- Decimal 全程 String、规范化黄金向量和禁止二进制浮点；
- `TextEncoder` 对 ASCII、中文和 Emoji 的 65,536/65,537 Bytes 边界；
- 权限菜单/按钮、页面 `componentKey`、写权限联选 READ、403 后 Navigation 刷新；
- 完整版本流分页、最高 `versionNo` 推定、分页漂移 Fail-Closed、Head 变化 409，以及首次保存后 Rule Set Code
  只读；
- Authoring Browser Model 不含 Handler 三字段，目录也不持有实现指纹；
- 409/413/422 保留编辑、422 清理旧命令 Key、404 过期详情、5xx Request ID；
- Candidate DRAFT、待恢复 VALIDATING、八项通过、REJECTED 和不可达状态；
- Validate Key 跨刷新可重建，且规则内容不进入任何浏览器持久存储或 Console；
- Validate Header 与 `releaseId/expectedVersion` 不一致时客户端按安全错误显示，且不把该请求当成未知结果。

运行 `npm test` 和 `npm run build`。

### 14.2 Operations BFF

使用真实随机 Loopback BFF HTTP 和有状态 Platform/Risk 协议 Simulator，不使用方法级 Mock：

- 九条 Route 都证明 PLT-I02 先于 Risk；授权拒绝和严格输入短路时 Risk 零调用；
- 八个本地 Response Model 与 Risk V1 逐字段等同；Authoring Model 严格校验上游 Handler 三字段后在浏览器
  投影中精确删除，并覆盖 Null、集合排序、成功响应和日志泄漏反例；
- 精确 Path/Query/Header/Body、Actor/Session/Request ID/Idempotency-Key 转发；Validate Key 由 BFF 独立重建并
  精确校验，随机但格式合法、错误 Release ID 或错误 Expected Version 的 Key 都返回 400 且 Risk 零调用；
- 未知字段、缺失 Null、非法 ID/Code/Enum/集合和损坏上游响应；
- 无写重试、Redirect 拒绝和超时映射；直接 BFF 测试不断言 Gateway-owned 413；
- 400/403/404/409/422/500/502/503/504 与 200 REJECTED；
- Metadata-only 日志在成功、拒绝、超时和异常响应下都不包含策略内容。

运行 JDK 25 `./mvnw clean verify`。

### 14.3 Platform Support

- 五 Permission、六 Console Resource（含页面 Route/Component Key 与其余 Null）、九 Route、Header/Query 和每
  Route 六项 Guard 的精确目录测试；
- Admin 五项、Viewer 只读以及自定义角色 Catalog 投影；
- Compiler 接受 65,536、拒绝 65,537，负数仍拒绝；既有 16 KiB Route 不变；
- 完整 Snapshot 原子编译、Route 冲突、权限缺失和子行不完整 Fail-Closed；
- Canonical 初始化 DML 与手工增量 SQL 的内容和重放门禁。

数据库测试复用手工启动的 repository-owned Local Environment；不使用 Testcontainers、Flyway 或启动 Seed。

### 14.4 API Gateway

- Operations BFF 两个新 Approved Root，及近似前缀、编码斜杠、点路径、Actuator 和宽 `/risk/` 反例；
- 九条动态 Route 的精确匹配、可信 Header、Query Allowlist、Cookie 和六类 Guard；
- Content-Length 与 Chunked 的 65,536/65,537，以及其他 POST 的 16,384/16,385；
- GET 非空 Body 拒绝；413 使用系统级 Problem，且 BFF Simulator 零调用；
- Snapshot 新版本失败时继续使用 Last Valid，不部分切换。

运行相关聚焦门禁和完整 `./mvnw clean verify`；若完整套件仍有 clean HEAD 基线失败，必须给出一一对应证据，
不得将聚焦通过描述成全量通过。

### 14.5 Risk 与端到端

Risk 运行现有 Model/Schema、API、真实 MySQL HTTP、持久化、并发和全 Reactor 回归，确认本集成没有改变 Owner
合同。端到端使用手工准备的本地环境验证：

```text
Nginx -> Gateway -> Operations BFF -> Platform PLT-I02 -> Risk
```

覆盖读取、保存、Freeze、创建 Candidate、Validate、撤权、65,536 Bytes、65,537 Bytes、超时未知结果同键恢复、
原操作员刷新恢复和其他操作员不能接管。自动测试不启动或重建共享容器，不访问真实业务环境。

最后运行 `npm --prefix requirements-site test`，并对所有触及仓库执行格式、静态、构建与相关测试门禁。

## 15. 文档一致性

本设计批准同时同步目标态需求与全局序列；后继实施必须保持它们一致，并在代码真实完成后再同步实现状态：

- `requirements-site/content/requirements.ts`：RCO-I12 记录已批准的 Console 集成合同；
- `requirements-site/content/global-use-case-sequences.mjs`：记录 Operations Rule Set/TEST Candidate 目标态全局序列；
- Operations Web、BFF、Platform、Gateway、Risk 与 Test Environment README；
- Risk 后端设计中已被本文取代的 1 MiB 建议和文档导航；
- `CURRENT_IMPLEMENTATION.md`：只在对应代码和门禁真实完成后记录 Implemented/Verified，并按实记录获授权的本地 `mor-test` SQL、部署与 Smoke 状态；共享/生产环境未部署继续明确标记。

不得把设计批准、SQL 文件存在、本地测试通过或 Route 为 DISABLED 描述成环境已启用。

## 16. 实施与未来启用顺序

本任务只实现和验证本地工作区，不执行部署、共享数据库 DML 或外部资源变更。后继实施计划按依赖顺序拆分，
并在各组件完成聚焦验证后执行跨仓库门禁。

未来获得单独部署授权后，顺序固定为：

1. 只读验证 Risk 目录；空库时仅在明确授权下先应用 Owner Schema/DML，partial/drift 立即停止；
2. 部署现有 Risk 私有后端和已支持新接口的 BFF/Web；
3. 部署支持 65,536 上限的 Platform/Gateway，以及 128 KiB Connector Envelope；
4. 以 `DISABLED` 装载九条 Route 和六个 Resource，验证 Platform 持久化目录完整；Platform Public Snapshot 与
   Gateway Runtime Catalog 按既有编译合同只包含有效 ACTIVE Route，因此此时必须排除这九条 Route，而不是把
   DISABLED 行误称为公开 Snapshot；无效后续 Snapshot 才保留整个 Last Valid 运行目录；
5. 启用只读 Route，验证 PLT-I02、撤权和历史查询；
6. 依次启用 Save、Freeze、Create Candidate、Validate Route 并只在 TEST 数据上 Smoke；
7. 最后开放 Console Resource；失败时先隐藏资源，再禁用对应 Route，并确认所有 Gateway 实例采用新的合法回滚
   Snapshot。只有编译/拉取失败继续使用旧目录时才标记 Last Valid in use。

该顺序不授权实施 Shadow、生产激活或真实 Checkout 风控执行。

## 17. Definition of Done

只有同时满足以下条件，才可声明本地 Console 集成完成：

1. 运营人员无需接触 JSON/AST 即可完成完整 DRAFT、Freeze、TEST Candidate 和八项静态校验流程；
2. 九条 Route 的路径、Permission、Body、Header、Query、Guard 和 PLT-I02 顺序全部有自动化证据；
3. 64 KiB 保存边界在 Web、Platform、Gateway 和 Nginx 顺序上保持一致，既有 Route 未被放宽；
4. Save 并发不自动覆盖，Validate 中断可由原操作员用确定性 Key 恢复；
5. BFF 不拥有 Risk 业务语义或持久状态，Gateway 不直达 Risk；
6. 规则内容不进入浏览器持久存储、普通日志、错误详情、URL 或遥测；
7. Web、BFF、Platform、Gateway、Risk 和 Requirements 的实际门禁结果均已记录，既有失败单独归因；
8. `CURRENT_IMPLEMENTATION.md` 只陈述实际完成和验证范围，按实区分本地 `mor-test` 状态与共享/生产状态，并继续标记共享/生产未部署、未 Shadow、未激活和未运行期评估。
