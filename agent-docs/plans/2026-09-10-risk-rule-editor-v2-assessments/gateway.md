# RCO-14 运营规则编辑 V2：API Gateway / Nginx 影响评估

> 历史评估提示：本报告记录 2026-09-10 的组件分析。后续按[2026-09-11 V1 配置决策](../../decisions/2026-09-11-risk-rule-v1-configuration.md)收敛；独立 V2、拒绝字典及默认文案建议不再适用于本期。当前系统影响、接口/DML 和技术待办见[V1 配置草案](../2026-09-11-risk-rule-v1-configuration-contract-draft.md)。

- 日期：2026-09-10。
- 性质：Assessment / 条件性建议，不是合同批准、功能实现或上线验收。
- 范围：Operations Web → Nginx → API Gateway → Operations BFF；Risk 与 Platform 的业务合同由各自 Owner 确认。
- 需求来源：[RCO-14 需求与链路评估](../2026-09-10-risk-rule-editor-v2-requirements-assessment.md)。已读取 [design.pen](../../../operations-console-web/docs/design.pen) 的 `bi8Au`、`wO9RC`、`AXojG`、`fulZG` 四个画板节点及文案；不纳入决策流编排、生产激活或真实动作执行。
- 现行权威：[Console 集成设计](../../../docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md) 第 5、8、12–14 节、[公共控制面设计](../../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 第 5、9、12、13 节，以及[系统 Problem Profile](../../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)。本评估不取代它们。

## 1. 结论与条件假设

**若 V2 仅改变业务 JSON，继续使用现有九条 Route、Operations JWT、Header/Query 合同、请求限额与时限，Gateway 和 Nginx 无需新增生产逻辑；需要补充 V2 载荷的边界及保密回归。** Gateway 不解释 Rule AST、Reject Code/Message、动作参数、终态或 Hash，不承担旧新业务模型转换。

上述结论有三个前提，不能省略：

1. 既有 Risk Console 集成前置已经交付到目标版本：Operations BFF 的两个 Approved Root、Gateway 通用 65,536 Bytes 上限、Platform 精确 Route 及 Nginx 外层 128 KiB。
2. Risk/BFF 已批准并实现可兼容的 Wire/Descriptor 演进；“Gateway 能转发”不代表旧 BFF Decoder 接受新字段。
3. 保存实际 UTF-8 Body 不超过 65,536 Bytes；Freeze/Create Candidate/Validate 不超过 16,384 Bytes；GET 无 Body；仍为现有 JSON 协议，不借压缩、Header 或 Query 搬运策略内容。

本次只读核对发现，第 1 项中的 Gateway Registry、Policy Validator、Risk 测试及 Nginx 配置存在工作开始前已有的未提交差异。它们是本评估使用的工作区源码证据，不是本次交付，也不是已提交或已部署证明。当前状态与既有验证缺口只查 [CURRENT_IMPLEMENTATION.md](../../CURRENT_IMPLEMENTATION.md)，本轮未重新验证运行环境。

## 2. 可复用能力与源码证据

| 能力 | 当前源码证据 | 对 V2 的意义 |
|---|---|---|
| Target / Approved Root | [ApprovedTargetRegistry](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java) 的默认构造器、`require`、`permitsDownstreamPath` | Risk 配置仍指向 `operations-console-bff` / `OPERATIONS` / `lb://operations-console-bff`；两个 Risk 专用下游根为 `/decision-rule-sets/`、`/decision-releases/`，不直达 Risk |
| 精确 Route 与路径安全 | [GatewaySnapshotCompiler](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java) 的 `compileRoute`、`validateTargetAndPath`、`rejectOverlappingRoutes`；[GatewayRuntimeCatalog](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeCatalog.java) 的 `match` | 按 Channel + Method + Path 匹配，不按 Body、Header 或 Query 选择业务版本；Root 允许不等于某条 Route 已公开 |
| 通用硬上限 | [GatewayRoutePolicyValidator](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewayRoutePolicyValidator.java) 的 `validateLimits` | 当前工作区允许配置 `0..65_536`，收 Body 最长 5s、响应最长 10s；没有 Risk 路径特例 |
| 请求体字节边界 | [RequestBodyLimitGlobalFilter](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/RequestBodyLimitGlobalFilter.java) | 同时检查 Content-Length 和流式累计字节，使用 Route limit + 1 的聚合检测边界，按原字节转发，不解析业务 JSON；结束、异常和取消清理 Body Owner |
| Query / Header | [GatewayRequestPolicyWebFilter](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestPolicyWebFilter.java)、[UpstreamRequestGlobalFilter](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/UpstreamRequestGlobalFilter.java) | Query Name 按名单校验；下游 Header 从空白集合重建，可信 Route Code / Request ID 不采用浏览器值；Header 白名单不是业务字段验证器 |
| 完整 Snapshot 原子切换 | [GatewaySnapshotRefresher](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotRefresher.java)、[GatewaySnapshotStore](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotStore.java) | PLT-I20 完整拉取、编译后原子替换；失败保留 Last Valid，不局部接收新 Route |
| Route 隔离 Guard | [RedisGuardKeyFactory](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/RedisGuardKeyFactory.java) 的 `prefix` | Shared/IP/Principal 等 Key 都包含 Route Code；新增 V2 Route Code 会形成独立计数，不自动与 V1 共用额度 |
| 错误与安全日志 | [OwnerProblemResponseGlobalFilter](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OwnerProblemResponseGlobalFilter.java)、[OwnerProblemValidator](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OwnerProblemValidator.java)、[GatewayAccessLogWebFilter](../../../api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayAccessLogWebFilter.java) | 成功业务 Body 不做模型解码；Owner 4xx/5xx 独立按 Problem 校验；专用 Access Log 只构造受限元数据和身份摘要 |
| Nginx 外层 | [nginx.conf](../../../test-environment/nginx/nginx.conf) | `/api` 与 `/api/` 代理到 Gateway，覆盖 Operations Channel / Client IP；128 KiB、15s Proxy Read Timeout、请求/响应不缓冲；没有 V1 业务 JSON 路由判断 |

## 3. V2-01 至 V2-08 逐项归属

| 需求 | Gateway 结论 | 实际扩展 Owner / 条件 |
|---|---|---|
| V2-01 列表展开、选中编辑、未保存状态 | 仅回归，无生产逻辑变化 | Web 内存与只读状态；不得为编辑态增加 Gateway Session 或缓存 |
| V2-02 增删、排序、名称、启用状态 | 复用完整版本保存 Route | Web/Risk 保持不可变新 DRAFT；不新增 Gateway 即时开关或排序命令 |
| V2-03 条件行与嵌套条件组 | 原字节传输与限额回归 | AST 无损往返、NOT/Decimal/树结构校验属于 Web/Risk，Gateway 不校验业务结构 |
| V2-04 PASS/REVIEW/REJECT | 无 Gateway Enum 修改 | Web 映射现行 ALLOW/REVIEW/DENY；JSON 内决定不选择 Gateway Security Profile |
| V2-05 原因目录及可调整消息 | JSON 扩展仅回归；若新增浏览器查询接口，则增加精确 Route 配置与测试 | Risk 拥有目录、冻结和披露语义，BFF 做安全投影；可否复用 Authoring Context 待确认 |
| V2-06 多个附加动作与参数 | JSON 扩展仅回归；目录新 Route 条件同上 | Risk 扩展受控动作、参数及静态检查，BFF/Web 适配；Gateway 不执行冷却或标签动作 |
| V2-07 不产生决定、继续匹配 | 无 Gateway 执行逻辑修改 | Risk 决定非终态表达与兼容；真实副作用、运行期评估属于另行授权范围 |
| V2-08 规则描述与保存新版本 | 只读生成描述无变化；持久化描述增加 Body/日志回归 | 手工描述是否持久化由 Risk/Web 确认；不搬到 URL、日志或 Header |

## 4. 必须修改与仅回归的准确分界

| 已批准的后续合同选择 | 必须修改 | Gateway / Nginx 生产代码 |
|---|---|---|
| 同路径、同传输合同，只扩展请求/成功响应 JSON 或 Descriptor | Risk/BFF/Web 的业务合同与实现；Gateway V2 载荷测试 Fixture 和回归 | 无需改动；不在 Gateway 增加 DTO、Hash Schema 或 AST 适配 |
| 新 `/v2` 或目录接口，下游仍在两个现有根内 | Platform 的精确 Route Code、Method、外部/下游路径、Permission、Header/Query、Guard 配置；Gateway Snapshot/路由 Fixture 和边界测试；BFF 端点 | 通常无需改 Registry / Filter，也无需改 Nginx `/api/` 代理；不能只加 Root 而不配置 Route |
| 新目录下游路径位于两个现有根之外 | 先确认暴露必要性；确需新根时，Gateway Registry、Registry 反例测试和静态边界脚本都要改；Platform/BFF 同步精确合同 | 条件性生产修改；仍只允许 BFF 的窄根，不批准 `/risk/`、`/` 或直接 `risk-service` Target |
| 增加非保留 Header 或 Query | Platform 两类 Header 名单/Query 名单、BFF 输入合同、Gateway Fixture/CORS/转发测试 | 在现有通用能力内通常无 Filter 修改；保留/身份 Header 不可由配置放开 |
| 保存上限超过 65,536、接收超过 5s或响应超过 10s | 另行量化与批准；Platform 编译器、Gateway Validator、BFF Gateway 配置校验、Web 计量和 Nginx 外层边界联动评估 | 必须重新设计相关硬上限，当前不建议、不自动提高；也不能只改 Route 数据 |

`/decision-rule-sets/v2/...` 等仅用于说明根内版本路径，不是本评估替 Risk/BFF 冻结的新接口。应由 Owner 提供准确 Manifest 后再配置；不创建通配 Route，也不使用动态 `{version}` 路径绕过版本显式批准。

## 5. Body、Header、Query 和 Nginx 约束

### 5.1 限额保持现状

| 边界 | 当前合同 | V2 必须保留的验证 |
|---|---:|---|
| Rule Set Save | 65,536 UTF-8 Bytes | Content-Length 和 chunked：65,536 不因大小被拒；65,537 返回 Gateway 413，BFF 零调用 |
| Freeze / Create Candidate / Validate | 16,384 Bytes | 16,384 / 16,385，不能因通用硬上限提高而把每条命令一起放宽 |
| 五条 GET | 0 Bytes | 非空 Body 被拒；不能用 GET Body 传递编辑模型 |
| Nginx 外层 | 128 KiB = 131,072 Bytes | 正常可解析的 65,537 字节请求有机会到达 Gateway Writer；超出外层的极端输入允许属于 pre-application 错误 |

以上“通过大小边界”不代表认证、Guard、BFF JSON 或 Risk 业务校验必然通过。413 测试需先满足正确 Route/JWT/Origin 等前置，避免由更早的拒绝掩盖 Body 检查。

当时评估引用的 `RiskDecisionConfigurationGatewayAutoTest`（该源码路径已不在当前工作区） 的字节边界主要使用 `validAsciiJson`；V2 需补充含中文、Emoji、转义字符、多个动作和拒绝文案的实际序列化样本。应由 Web/BFF/Risk 提供典型、最大允许字段组合和接近限额样本，只交接非敏感合成数据或长度结果。本轮没有 V2 真实 DTO 样本或运行测量，不能推导现有上限不足，也不承诺一定容纳全部理论组合。

不引入 gzip Body、业务分片或批量增量保存绕过限制；它们改变媒体类型/编码或完整不可变版本合同，均不是当前授权。新增目录的成功响应也不受“请求 Body 64 KiB”自动保护，目录规模、分页和 BFF Decoder 上限由 BFF/Risk 提供证据，Gateway 继续做传输时限和错误响应边界控制。

### 5.2 白名单与认证

- Rule Set List 的 Query 是 `page,size,pointCode,ruleSetCode,status`；Candidate List 是 `page,size,pointCode,status`；其余现有 Route 无 Query。新增目录如需 `descriptorCode` 等条件，必须由 Owner 显式声明后加入对应 Route，不能假定任意 Query 都透传。
- Query 白名单校验的是名称，不代替值、重复参数和业务合法性校验；后者继续由 BFF/Owner 输入合同承担。
- `authorization` 由 Gateway 消费，只出现在适用 CORS 请求头名单；`origin` 按既有合同转发。写请求允许 `content-type`、`idempotency-key`。未声明的普通 Header 不会因 JSON 升级自动加入下游；额外 CORS 请求 Header 需保持拒绝。
- Operations 继续使用 `OPERATIONS_JWT + HUMAN_PERMISSION`、`DROP/NONE` Cookie 策略和 Shared Route / Client IP / Authenticated Principal 各一项 Rate 与 Concurrency。不要套用 Client API opaque Credential、Scope 或外部 Request ID 接纳合同。
- Nginx 继续按现有入口覆盖 `X-Entry-Channel: OPERATIONS`、`X-Real-IP`；Gateway 重建 Actor/Session/Route/Request ID。V2 不接受浏览器自行选择可信身份或执行权限。

## 6. 版本兼容、数据与权限安全

1. **业务版本与 PLT Snapshot Schema 分离。** Rule Wire、Descriptor、Hash Schema 升级不要求把公共 `GatewayPublicSnapshot.schemaVersion` 改成 2；Gateway 编译器当前只接受 Schema 1。Route 新增是现有 Snapshot 内容变更，不是 Snapshot 协议升级。
2. **同路径版本协商由 BFF/Risk 负责。** 现有 Router 不按 Body/Query/Header 选择不同 Route；同一 Channel + Method + Path 不允许配置两条“V1/V2”重复 Route。若使用 Body Descriptor 分支，必须先解决 BFF 严格 Decoder 对旧新模型的兼容；Gateway 不做补字段、删字段、转换 Enum 或重算 Hash。
3. **显式 V1/V2 路径可共存。** 每条新增 Route 需要独立稳定 Code、精确权限和完整子配置。固定目录路径与已有变量路径相遇时，编译器的固定路径优先及变量模板冲突规则仍要测试，不能只检查字符串不相等。
4. **历史数据不由 Gateway 迁移。** Gateway 没有 Rule/Candidate/Hash/幂等数据库；不迁移或重算历史，不缓存新编辑模型，不接入业务命令幂等 JAR。若 BFF 传递版本、Base ID/Hash 或 Key，Gateway 按原合同转发，重放/409/冻结闭包由 Risk/BFF 验证。
5. **新 Route 的限流不是跨版本共享配额。** Redis Key 包含 Route Code；V1/V2 并存可能增加同一业务能力的合计可调用量。Platform 与 Gateway 需确认每 Route 六项 Guard 和总容量预期；本需求不授权新增跨 Route 聚合限流机制或清理已有 Redis 状态。
6. **权限仍在 BFF → PLT-I02 → Risk 链上闭环。** Gateway 只校验 JWT、Permission Code 形状和安全配置，不执行 PLT-I02。新增原因/动作读取能否沿用 `RISK_DECISION_CONFIG_READ`，新增能力是否需要更细权限，由 Platform/Risk 决定。若同一个 Save JSON 内有不同动作权限，必须由授权 Owner 明确定义；不能要求 Gateway 解析策略来判断，也不能假设旧 SAVE 权限自动授权真实动作执行。
7. **Last Valid 不是业务版本回滚。** 无效新 Snapshot 被整体拒绝；合法停用 Snapshot 则应移除对应 Route。不能用提交一份非法配置来“停用 V2”，也不能以一台实例刷新成功推导全实例收敛。Rollout/撤回需单独授权，并以各实例使用的有效目录为证据。

## 7. Problem、413 与保密日志

- Body 超限仍由现有 Writer 输出 `413 / GATEWAY_PAYLOAD_TOO_LARGE`，使用系统 Problem，Header/Body Request ID 一致，不回显规则、消息、动作参数或内部限额实现细节。
- BFF 返回的合法 400/403/404/409/422/5xx 由 Gateway 按通用 Problem 合同处理；V2 的字段路径由 BFF/Risk 按外部 JSON 属性定义，Gateway 不维护动作错误码全集，也不解析 `detail` 判断业务结果。
- Owner 错误 Body 有独立的 65,536 Bytes 安全上限，不能与 Save 请求限额混为一谈。损坏、截断、非法媒体类型或 Request ID 不一致应走现有安全协议错误路径；新动作导致更多字段错误时，BFF/Risk 需保证错误集合有界且不含原始值。
- 当前 Operations Target 仍有 Legacy Owner Reader 迁移兼容分支；这不是允许 V2 继续生产旧错误格式的依据。是否移除该分支依现有迁移证据另行处理，不搭车改动。
- Gateway 访问事件不含 Body、原始 URL/Query、Authorization、Cookie、Idempotency-Key 或 Reject Message；新增字段不需要逐个注册脱敏。BFF/Risk 的 Metadata-only 路径覆盖由各自 Owner 核对，尤其是新增路径根；不在 Gateway 增加一套策略内容日志 Filter。
- Nginx 当前 `access_log off`，但仍配置 `error_log /dev/stderr warn`。静态配置和 9 项边界测试不能证明所有异常 error log 都不含请求行/Query；应使用非敏感标记样本覆盖超限、畸形请求、断连和上游不可达并检查错误输出。此处是待验证风险，不是本轮已复现泄漏；若发现泄漏，另行安排外层日志修复，不靠开启 Body/debug 日志排查。
- 超出 Nginx 外层或尚未进入 Writer 的错误不保证 JSON Problem，也不在 Nginx 复制业务错误 Writer。前端按既有 pre-application 边缘错误处理，不能把 HTML/空响应交给正常 Problem Decoder。

## 8. 后续测试门禁及复用位置

| 门禁 | 可复用测试 / 需要补充 |
|---|---|
| 精确 Route、Root、V1/V2 共存 | [ApprovedTargetTransportAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/ApprovedTargetTransportAutoTest.java)、[GatewaySnapshotCompilerAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java)：新目录/版本正例，近似前缀、编码斜杠、点路径、错误 Method、Actuator、直达 Risk 和模板冲突反例 |
| Snapshot 配置与切换 | [GatewaySnapshotFixture](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/builder/GatewaySnapshotFixture.java)、[GatewaySnapshotRefreshAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotRefreshAutoTest.java)、[GatewaySnapshotPinningAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotPinningAutoTest.java)：精确完整 Manifest，未知 Schema/无效子行保留整份旧目录，在途请求固定旧 Route，合法移除及空 Route 集合 |
| Body / Header / Query / Guard | `RiskDecisionConfigurationGatewayAutoTest`、[OperationsCorsAndBodyLimitAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java)：V2 合成载荷原字节往返、两组字节边界、GET 非空、精确 OPTIONS、未知 Query、Header 防伪及六项 Guard；拒绝时零 BFF 调用，完成/超时/取消释放 Body 和 Lease |
| Problem 与保密 | [GatewayProblemWriterAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayProblemWriterAutoTest.java)、[GatewayAccessLogAutoTest](../../../api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java)：新增嵌套字段错误、409/422、安全 502/413/504、Request ID、策略内容在正常/拒绝/异常日志中零泄漏 |
| Nginx 外层与端到端 | [test_nginx_gateway_problem_boundary.py](../../../test-environment/scripts/test_nginx_gateway_problem_boundary.py) 保持有效作用域/继承/缓冲/超时检查；后续真实 Nginx → Gateway → BFF → PLT-I02 → Risk 验证 65,536/65,537、外层超限、撤权、历史读取、未知结果同键恢复和 error log 保密 |

Java 回归按 [UNIT_TEST_GUIDE](../../UNIT_TEST_GUIDE.md) 使用随机端口真实 Gateway、预先启动的仓库 Redis、生产客户端和有状态 BFF/Platform/JWKS Simulator；不使用方法级 Mock，不启停共享中间件，不执行 Schema/初始化 SQL。测试固定配置仍在 `application-auto.yml`。

后续实现应先运行相关专项，再执行完整 `./mvnw clean verify`；已有失败必须与当次可比基线逐项区分。Gateway 的 BFF Simulator 测试不能证明真实 PLT-I02 调用顺序、Risk Hash/冻结/静态校验或旧新 Decoder 兼容，这些由 BFF/Risk 和联合测试承担。

## 9. 依赖顺序、相对工作量与待决问题

建议的最小实施顺序（需后续实现授权）：Risk 冻结 Wire/Descriptor/原因动作合同及兼容策略 → BFF/Web 提供准确端点、权限需求和 UTF-8 样本 → Platform 确认 Route/Guard/Permission 差异 → Gateway 按第 4 节决定是否改 Registry，完成回归 → 联合验收。SQL 应用、Route 激活、Nginx/Gateway 部署和 Console Resource 开放仍需独立授权。

| 条件 | Gateway / Nginx 相对工作量 | 原因 |
|---|---|---|
| 同路径纯 JSON，现有前置完整 | 小 | 生产逻辑无新增，主要是合成 Fixture、字节/错误/日志回归与联调证据 |
| 根内新增少量版本/目录 Route | 小至中 | 无新 Filter，增加 Snapshot/CORS/权限元数据/Guard/共存测试，并协调 Platform/BFF |
| 新窄 Root | 中 | 改静态安全边界、反例与脚本门禁，联合审查暴露范围及 Metadata-only 覆盖 |
| 超限或跨 Route 统一配额等新机制 | 需重新评估，不能按小改动排期 | 涉及多个 Owner 的硬上限/资源预算或新的公共能力；当前无量化证据和批准合同 |

这是 Gateway 增量工作量，不包含既有 V1 集成交付缺口、Risk 模型/Hash/数据库演进，也不包含生产动作执行。未冻结接口数量、样本和兼容策略前不承诺具体工期。

需要主任务收敛的具体问题：

1. **Risk + BFF：** 同 V1 Path 兼容扩展还是显式 V2 Path？Descriptor 如何选择，V1 成功/错误响应及旧同键重放怎样保持可读？是否需要新媒体类型或版本 Header？
2. **Risk + BFF：** 原因/动作目录合并 Authoring Context 还是独立查询？请提供精确 Method、外部/下游 Path、Query/Header、分页与响应大小上限；是否都留在两个批准根内？
3. **Web + Risk：** 新文案、动作数量、参数长度的最大值是什么？请提供真实序列化的 UTF-8 样本计量，确认在既有 64 KiB 内的用户体验；没有证据保持原上限。
4. **Platform + Risk：** 新目录是否沿用 READ，Save 是否仍只表达配置而非动作执行？若 V1/V2 Route 并存，分别计数后的合计容量是否可接受？
5. **BFF + Risk：** 新字段错误路径和安全消息如何投影？Reject Message 是否只在授权配置/内部记录中出现，绝不作为未审查的 Problem detail 或外部拒绝文案？
6. **集成 Owner：** 目标制品是否已经包含现有两根、64 KiB、精确九 Route 和 128 KiB 外层前置？谁承担真实 Nginx error log 标记回归及所有 Gateway 实例 Snapshot 收敛验收？

## 10. 本轮实际验证与交付边界

- 已核对需求、四个画板文本、当前合同及上述源码；未等待其他系统报告才形成条件性结论。
- `node api-gateway/scripts/verify-gateway-boundaries.mjs`：通过，检查 112 个生产 Java 文件、4 个生产资源、99 个测试 Java 文件和 3 个测试资源。这是当前工作区静态边界证据，不是 Java 用例通过数。
- `python3 -B test-environment/scripts/test_nginx_gateway_problem_boundary.py`：9/9 通过；仅静态配置合同，不启动 Nginx，也不证明实际日志或 HTTP 响应行为。
- 本轮未运行 Maven、真实 HTTP/Redis 链路或跨系统 Smoke，未测量最终 V2 Payload，也未读取实时 Snapshot/部署状态；不能宣称功能或上线验收完成。
- 仅新增本评估文件。未修改业务代码、测试代码、公共规范、requirements、状态台账、数据库或部署配置；保留已有修改，未提交、推送或部署。
