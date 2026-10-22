# 卡 3DS-B 报告：api-gateway CHANNEL_CALLBACK 安全链、Platform 块 133、PayerMax 3DS 配置脚本

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-3ds-edge`（detached，基于 `4163f9f9`）。卡上写的 `/Users/kenny/Desktop/Workspace/Code-MoR-3ds-edge` 不存在，`git worktree list` 中本卡的工作树实际位于主工作区之下的该路径。
- 未提交、未推送、未建分支；未执行任何 SQL；配置脚本未对任何环境执行。

## 1. 开工自查

**已读：** `AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（含 3.2 Gateway 工程例外）、`agent-docs/examples/WRITE_API_EXAMPLE.md`（含第 11 节）、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`（章节结构）、`paytech/api-gateway/README.md`，需求 `technique.md` 第 3 节（3.2 主流程、3.3.4 回调入站、3.3.7 配置、3.4 数据、3.5 配置、3.6 顺序 7/8）、`db/platform-support/dml.sql`、卡直付 harness `configure_card.py` / `README.md`、接入 harness `common.py` / `configure.py`、MPGS harness `configure.py` / `mortest.py`（读改写写法参考）。本卡不涉及数据库结构与补偿 / MQ，未读 DATABASE_DESIGN_GUIDE / COMPENSATION_EVENT_GUIDE。

**所属 Processor / Context、本地原子写入、事务外步骤：** 不适用。本卡没有同步写接口：Gateway 是 WebFlux 入口适配器（无业务库、无 Processor），新增的只是一条 Spring Security 过滤链；Platform 只追加手工执行的初始化 DML 和一个只读编译验证测试；配置脚本经既有管理接口保存。

## 2. 改动文件

| 文件 | 内容 |
|---|---|
| `paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/ChannelCallbackSecurityConfiguration.java`（新） | `@Order(HIGHEST_PRECEDENCE + 5)` 安全链：匹配运行时 Route 的 Channel / Security Profile / Authorization Mode 均为 `CHANNEL_CALLBACK`，`permitAll`，关闭 CSRF / Basic / Form / Logout，NoOp Request Cache 与 Security Context。未登记路径、其他渠道仍由既有兜底 `denyAll` 链处理 |
| `paytech/api-gateway/paytech-api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewayChannelCallbackTest.java`（新） | 7 个用例，见第 4 节 |
| `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 末尾追加自包含块 133（Target 1、Route 1、Route Header 2、Guard Rule 4；Target ACTIVE、Route DISABLED；激活 / 停用 / 回滚语句在注释） |
| `paytech/platform-support/paytech-platform-support-test/src/test/java/com/paytech/platform/support/auto/ext/http/operations/gateway/GatewayChannelCallbackRouteTest.java`（新） | 2 个用例：块 133 原样插入（路由 DISABLED）后公开快照可编译、目标下发路由不下发；按块 133 注释方式经管理接口启用后，快照中的回调路由策略逐项符合块 133 |
| 主工作区 `requirements/20261022/payment-3DS全链路/assets/harness/configure_3ds.py`（新） | 增量读改写：标准 API PAY 追加 `payment.threeDsRequired`（BOOLEAN，可选）；各渠道 `ORDER_AND_PAY` 追加 `data.paymentDetail.cardInfo.dynamic3DS`；PAY 流程 `createOrder` 节点增加 `dynamic3DS = input.payment.threeDsRequired == true ? 'do3DS' : nil`（先校验再保存）；核对 PAY_QUERY 已输出 CLOSED / NOT_FOUND，缺少时按接入 harness 的 `QUERY_FLOW` 校验后保存。已是目标状态的步骤跳过 |
| 主工作区 `requirements/20261022/payment-3DS全链路/assets/harness/README.md`（新） | 使用说明：前提、命令、跳过语义、沙箱用例需重新验证、`dynamic3DS` 待 UAT 实测、回滚顺序 |

Gateway 的 Body 原样转发、Route Header 白名单（`sign`、`content-type`）与按路由 Body 上限都由既有通用过滤器完成（`UpstreamRequestGlobalFilter` 只复制白名单 Header，`RequestBodyLimitGlobalFilter` 按路由上限），本卡无需改动，测试覆盖证明。

## 3. 对应 AC

- AC-07（渠道通知经 Gateway 入站）：Gateway 侧放行链、原样转发与块 133 注册数据。channel-connector 入站本身属卡 3DS-A。
- AC-01、AC-12（配置数据）：`configure_3ds.py` 为 PAYERMAX_MOCK、PAYERMAX 打开 `threeDsRequired → dynamic3DS`。

## 4. 测试

环境：JDK 25，`./mvnw -o`，本机 Redis 16379（Gateway 用独立 Redis 命名空间），Platform 用本机 `mor_platform` 自动测试库；口令只按需从主工作区 `local-environment/.env.local` 导出 `MOR_REDIS_PASSWORD`、`MOR_PLATFORM_DB_PASSWORD`，未打印。

| 命令 | 结果 |
|---|---|
| 实现前 `-pl api-gateway/paytech-api-gateway-test -am test -Dtest=GatewayChannelCallbackTest` | 6 例中 4 例失败（回调被兜底链返回 401），确认测试能暴露缺口 |
| `-pl api-gateway/paytech-api-gateway-test -am verify`（整个 Gateway 测试模块） | **通过：1313 例，0 失败 / 0 错误 / 0 跳过**；其中 `GatewayChannelCallbackTest` 7 例通过 |
| `-pl platform-support/paytech-platform-support-test -am test -Dtest=GatewayChannelCallbackRouteTest,GatewayRouteIdempotencyModeTest,GatewayConfigurationLockTest` | **通过：11 例**（2 + 7 + 2） |
| `node api-gateway/scripts/verify-gateway-boundaries.mjs` | 失败 1 项，**与本卡无关的既有问题**：`idempotency/IdempotencyGlobalFilter.java: production reactive code must not block or manually subscribe`（本卡未改该文件） |
| `node api-gateway/scripts/verify-jacoco.mjs` | 失败，**既有问题**：分支覆盖 89.50%（2404/2686）低于 90%。新类 `ChannelCallbackSecurityConfiguration` 指令 69/69、分支 4/4 全覆盖，去掉新类后基线为 2400/2682 = 89.49%，即改动前已低于门槛 |
| `python3 -c "import ast; ast.parse(...)"` 检查 `configure_3ds.py` | 语法通过；脚本未运行 |

`GatewayChannelCallbackTest` 用例：
1. `test_normalFlow`：不带任何认证到达 Owner；字段顺序 / 空白 / `\u` 转义均非规范化的 JSON Body 以 SHA-256 比对逐字节一致；`sign`、`content-type` 原值转发；Owner 应答 `{"code":"SUCCESS","msg":"Success"}` 原样返回。
2. `test_unlistedHeadersDropped`：调用方 Authorization、Cookie、未登记 Header、伪造的 `X-Gateway-Route-Code`、`X-Real-IP`、`X-Entry-Channel` 都不到达 Owner。
3. `test_ownerProblemForwarded`：Owner 401 Problem（`CHANNEL_CALLBACK_SIGNATURE_INVALID`）原样返回。
4. `test_bodyLimit`：恰好 65536 字节转发；65537 字节返回 413 `GATEWAY_PAYLOAD_TOO_LARGE`，Owner 零调用。
5. `test_unregisteredRequestRejected`：少一段、多一段、PUT 方法、以 OPERATIONS 入口访问同一路径，均 404 `GATEWAY_ROUTE_NOT_FOUND`，Owner 零调用。
6. `test_disabledRouteRejected`：路由不在快照中时 404，Owner 零调用。
7. `test_unpairedClientIpGuardRejected`：评审稿的 Guard 组合（CLIENT_IP 只有限流）被 Gateway 拒绝整份快照，上一份有效快照继续生效（见第 5 节）。

## 5. 偏离与裁决

1. **块 133 增加第 4 条 Guard：`900000000000133704 CONCURRENCY CLIENT_IP 20`（共 8 行，评审稿为 7 行）。** 原因：Gateway Snapshot 编译器 `GatewayGuardProfileCompiler.requirePair` 要求任一维度的 Rate 与 Concurrency 成对出现；评审稿只有 CLIENT_IP 限流。Platform 编译器只对必需维度和 TENANT 检查成对，会接受并下发该路由，于是**路由一经激活，Gateway 就拒绝整份快照并停在上一份有效快照**，此后所有路由变更都无法生效，回调路由也不会上线（`test_unpairedClientIpGuardRejected` 证明）。可选方案是删掉 CLIENT_IP 限流或补并发；保留评审稿 CLIENT_IP 120/分钟 的意图，补一条最小的成对并发规则（20，低于共享并发 50）。块注释已写明原因。**需要设计方同步修正 `db/platform-support/dml.sql` 草案**（主工作区，本卡未改）：Guard Rule 改为 4 行，总计 8 行，并补 `900000000000133704` 行；technique.md 3 节规模描述中的“注册 DML 7 行”同步为 8 行。
2. **部署前提（新增说明，不改代码）：** Gateway 从 Nginx 写入的 `X-Entry-Channel` 判定入口渠道，渠道回调的公网主机 / 路径必须由 Nginx 设置 `X-Entry-Channel: CHANNEL_CALLBACK` 并覆盖 `X-Real-IP`，否则请求按兜底返回 403。已写入块 133 注释；Nginx 配置不在本卡范围。
3. **测试类名：** 按卡沿用 `GatewayChannelCallbackTest`（Gateway 模块其余类多为 `*AutoTest`，Surefire 规则 `**/auto/**/*Test.java` 同样会执行）。Platform 侧新增 `GatewayChannelCallbackRouteTest`，用于满足“确认 Platform 编译器能编译该路由”。测试夹具的路由响应超时固定为 10000 ms（`ConfigurationTestMapper.insertRoute` 写死），块 133 为 5000 ms，故 Platform 测试不断言超时值；Gateway 测试按 5000 ms 构造。
4. **配置脚本写法：** 不像 `configure_card.py` 那样整份重建定义，而是读取现行定义后只追加所需字段 / 映射（参照 MPGS harness 的 `extend_pay`），避免覆盖两份 harness 之后在环境上做过的调整；PAY_QUERY 现行流程（接入 harness `QUERY_FLOW`）已透传 CLOSED、FAIL 分支固定 NOT_FOUND，脚本只核对，缺少时才保存。`nil` 值不会写入请求（`ChannelRequestBuilder` 只写非空绑定），满足“不要求时不传”。

## 6. 遗留项

- `db/platform-support/dml.sql` 草案与 technique.md 规模行数需按第 5.1 节同步（设计方）。
- 与卡 BIN（Platform）可能都在 `platform-support-initial-data.sql` 末尾追加块，合并时注意文件尾部冲突，按块号顺序排列。
- Gateway README / CURRENT_IMPLEMENTATION 未更新（不在本卡文档范围）；Nginx `X-Entry-Channel` 路由配置待部署阶段。
- 既有的 Gateway 边界脚本失败项与 JaCoCo 分支覆盖不足（第 4 节）不由本卡引入，未处理。
- 全 reactor `test-compile` 未运行（只改 Gateway 与 Platform 测试模块，二者均已带 `-am` 编译并通过测试）。

## 7. WRITE_API_EXAMPLE 第 11 节核对

| 项 | 结论 |
|---|---|
| API / Body 最小合同、Validator | 不适用：未新增或修改 HTTP API / DTO；回调合同属卡 3DS-A |
| Controller/Converter 仅适配；Processor 位置 | 不适用：Gateway 无 Controller / Processor，新增的是安全过滤链，只做匹配与放行 |
| Processor 查询 Repository、不调 Mapper | 不适用 |
| Context | 不适用 |
| Service 边界 | 不适用 |
| 本地原子写入与事务 | 不适用：Gateway 无数据库；块 133 是手工执行的单事务 DML（`START TRANSACTION … COMMIT`），未执行 |
| Repository / 并发保护 | 不适用 |
| 锁 | 不适用：无资源锁；限流 / 并发由既有 Gateway Guard（Redis）按路由配置执行 |
| 原请求重放 | 不适用：渠道重复通知的去重归 channel-connector（`uk_channel_callback_fingerprint`），Gateway 不做业务防重 |
| Gateway 请求幂等与业务防重区分 | 路由 `idempotency_mode=NONE`，Gateway 不消费 Idempotency-Key；未引入旧命令幂等组件 |
| View / DataObject 边界 | 不适用 |
| 公共包、配置、无生产 Mock | 复用既有 Snapshot、Guard、Header 白名单与 Body 上限机制；生产代码只新增一个过滤链类，无配置项、无测试分支；测试替身均在 `src/test` |
| 实际验证 | 见第 4 节：Gateway 整模块 1313 例与 Platform 11 例实际运行通过；未覆盖：真实 Nginx 入口、真实 channel-connector 验签（卡 3DS-A）、配置脚本对环境的执行（阶段 5） |
