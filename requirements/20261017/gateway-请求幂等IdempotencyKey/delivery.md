# Gateway 处理 Idempotency-Key — 交付记录

## 适用阶段

评审稿：完整 8 阶段，见 [requirement.md](./requirement.md) 第 8 节。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 0. 选题 | 2026-10-17 | — | “Gateway 处理 Idempotency-Key（我建议先做这个）” | 阶段 1 需求评审 |
| 1. 需求评审与范围确认 | 2026-10-17 | [requirement.md](./requirement.md) | “按建议”：Q1 路由字段 `idempotencyMode`；Q2 作用域=通道+调用方（Client API 按 Client）；Q3 原始字节摘要；Q4 72 小时自首次接受起；Q5 记录下游全部响应（≤64 KiB），Gateway 自身拒绝不占 Key；Q6 无响应则释放 Key；Q7 Redis 明文存 72 小时；Q8 本需求一并改 Commerce；Q9 本机 + mor-test | 阶段 2 现状差异与影响分析（只读） |
| 2. 现状差异与影响分析 | 2026-10-17 | [technique.md](./technique.md) 第 1—4 节 | D1—D7“其余没问题”；问“为什么会用到 Redis”“是不是压力很大，可能会有数亿的数据”；D8“先存 Redis，但是这个点需要记录，以后肯定要改到数据库中” | 阶段 3 方案、接口与数据设计 |
| 3. 方案、接口与数据设计 | 2026-10-17 | [technique.md](./technique.md) 第 5—12 节 | “继续” | 阶段 4 研发（本机代码与测试；不含 SQL 执行于共享环境、提交、推送、部署） |
| 4. 研发 | 2026-10-17 | 见下文“阶段 4 研发结果” | “按建议，授权提交并部署 test”：删除 Commerce 3 个不再使用的错误码；跳过阶段 5，直接在 test 验收 | 阶段 6 test 发布 |
| 6—7. test 发布与验收 | 2026-10-17 | [release-20261017.md](./release/release-20261017.md) | 按阶段 4 授权执行 | 阶段 8 收尾 |
| 8. 收尾 | 2026-10-17 | 见下文“阶段 8 收尾” | — | 结束；推送未授权 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 只读核对 | 阶段 1—2 范围内 |
| 本机代码修改与测试 | 阶段 4 范围内 |
| 本地提交（独立暂存区，不推送） | 2026-10-17 已授权 |
| test SQL：`mor_platform` 加列 `idempotency_mode`（先备份） | 2026-10-17 已授权 |
| test 部署：Commerce → Platform → Gateway → Operations BFF → Web；3 条写路由改为 REQUIRED 并停止转发 Key；验收与发布记录 | 2026-10-17 已授权（替换共享服务前协调其他会话） |
| 推送 | 未授权 |

## 当前阶段

已结束：阶段 1—4、6—8 完成（阶段 5 按用户决定跳过）；已本地提交（未推送），2026-10-17 已部署 mor-test，验收 89/89。发布记录见 [release-20261017.md](./release/release-20261017.md)。

## 阶段 4 研发结果（2026-10-17，未提交）

由 4 个并行子任务分系统实现（Platform、Gateway、Operations BFF/Web、Commerce），本会话负责 API 合同、脚本与文档。

| 系统 | 改动 | 测试 |
|---|---|---|
| Platform API | `GatewayIdempotencyMode`；`GatewayRouteSnapshot.idempotencyMode`（保留旧 19 参数构造，等同 NONE）；路由详情、创建 / 更新请求、能力 `idempotencyModes`；已安装本机 `~/.m3` | — |
| Platform | 新增列（PDManer 生成源 + 只补本列差异的模型文件、Schema、变更 SQL `2026-10-17-gateway-route-idempotency-mode.sql`、初始化 DML 13 处 INSERT、测试 DDL、测试 oracle）；DataObject / Mapper / Repository / Domain / 编译器（D3 规则、未知值拒绝、能力）/ 管理服务（创建缺省 NONE、更新缺省或 null 保持原值）/ 审计字段 `IDEMPOTENCY_MODE` | `platform-support-test` 全量 **736/736**（新增 `GatewayRouteIdempotencyModeTest` 7 项）；PDManer 模型测试 28/31，3 项失败在干净 HEAD 同样失败（既有） |
| Gateway | `com.paytech.gateway.idempotency` 8 个类 + 3 个 Lua；过滤器顺序 +150；快照解析（缺省 NONE、未知值拒绝）；策略校验 D3；4 个错误码；outcome `IDEMPOTENCY_REPLAYED` / `IDEMPOTENCY_CONFLICT`；README | 全量 **1277/1277**（新增 `ClientApiIdempotencyAutoTest` 14 项、Redis 不可用 1 项） |
| Operations BFF | 白名单、转换、模型；详情缺失或未知值返回 502 | 全量 **1386/1386** |
| Operations Web | 路由编辑器“请求幂等”选择（选项来自安全档案能力，默认不启用），详情只读显示 | `npm test` **1068/1068**，`npm run build` 通过 |
| Commerce | 6 个 POST 去掉 `Idempotency-Key` 参数；移除 `paytech-command-idempotency` 依赖与配置；测试不再发送；OpenAPI 与 README 改为 Gateway 处理 | 全量 **808/808**（删除 2 个“缺 Header 返回 400”的测试，原 810） |
| 脚本 | `register_client_api.py --enable-idempotency`（3 条写路由改 REQUIRED 并停止转发 Key）；`client_api_flow.py --idempotency`（非法 / 缺失 Key、重放逐字一致且只建 1 单、换 Body / 换接口复用、并发同 Key、凭证轮换后重放、404 Problem 重放改写 requestId）；`sync_route_headers.py` 无需改（更新缺省保持原值） | 编译通过；未连 test 运行 |
| 文档 | Commerce 设计 3.4 与第 7 节注、Client API Gateway 合同决定、应用架构 | — |

**本机 SQL**：2026-10-17T07:26:47Z 在本机自动测试库 `127.0.0.1:13306/mor_platform` 执行 1 条 `ALTER TABLE t_gateway_route ADD COLUMN idempotency_mode ...`（执行前确认不存在）。没有动其他库。

**偏离方案**
1. 路由列表摘要不含 `idempotencyMode`，只有详情、创建 / 更新、能力与快照含。
2. Operations BFF 更新时不传该字段会发送 `null`（与其他可选字段一致）；Platform 把 `null` 与未传同样处理为保持原值，有测试覆盖。
3. Gateway：摘要比较放在 Lua 内（claim 直接返回 CLAIMED / IN_PROGRESS / KEY_REUSED / COMPLETED）；时间取 Redis `TIME`，不受 Gateway 时钟偏差影响；暂用共享 Redis 连接（D8 的独立连接留作配置项）。
4. Gateway：链路中任何错误（含 Owner Problem 校验或响应合同 502）都释放 Key，只记录通过校验的下游响应。
5. “连接失败”测试用下游收到请求后断开连接的模拟（比连接拒绝更严格）；整个 Redis 不可用时在更早的入口限流处即返回 503。
6. Commerce 保留了不再抛出的 3 个错误码（`IDEMPOTENCY_KEY_REQUIRED`、`INVALID_IDEMPOTENCY_KEY`、`IDEMPOTENCY_KEY_REUSED`），待用户决定是否删除。

**设计文件**：`operations-console-web/docs/design.pen` 未加“请求幂等”选择，未改。

## 阶段 6 进度（2026-10-17，进行中）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 07:3x | 删除 Commerce 3 个不再使用的错误码；Commerce 全量 808/808 | 完成 |
| 07:3x | 本地提交 `b0c8b0e`（106 个文件；共用文件只带本需求段落） | 完成；SES 会话指出 PDManer 模型新字段位置错误导致 JSON 无效、`PersistenceConfiguration` 注册顺序错位（只取本需求段落时零上下文补丁插错位置），另提交修正 `69d868e` |
| 07:40 | 从 `b0c8b0e` 干净 worktree 构建 Commerce（JAR `f5d3c2f7a4b7e658`，镜像 `8deffdc6`）、Gateway（JAR `1d66cacbe0922ebc`，镜像 `da8e7109`）；候选文件 `release/compose-candidate-20261017.yaml` | 完成 |
| 07:41:20 | 备份 `mor_platform` Gateway 5 张表、Gateway / Commerce 容器 inspect → `~/Code-MoR-backups/20261017-gateway-idempotency-test/20261017T074120Z/`；执行变更 SQL（执行前核对列不存在） | 新列位于 `response_cookie_mode` 之后，默认 `NONE`，267 条路由均为 `NONE` |
| 07:42—07:43 | 等 Risk 会话（code-mor-db）窗口结束后替换 Commerce（回退镜像 `ea5c6dad`）→ Gateway（回退镜像 `1cddfeab`） | 均 healthy，ERROR 0；三个入口 `/healthz` 200 |
| 07:45 | 回归 `client_api_flow.py`（路由仍为 NONE） | 首次 79/81：旧脚本“Commerce 缺 Header 返回 400”已不成立，且该请求真实取消了订单，多出 1 个事件。脚本改为缺 Key 检查只在 `--idempotency` 下执行后 **80/80** |

**与其他会话的发布协调**：Platform、Operations BFF / Web 由 SES 会话（code-mor-36）在 Risk 会话（code-mor-db）提交后，从同时包含 `69d868e`、`762b785`（SES）与 Risk 提交的 HEAD 合并构建、一次替换（含其 9 列 DDL 与环境变量）。新 Platform 健康后，本需求执行 `register_client_api.py --enable-idempotency` 与 `client_api_flow.py --idempotency`。

## 阶段 8 收尾（2026-10-17）

- test 验收 **89/89**；回归 80/80。详见[发布记录](./release/release-20261017.md)。
- 台账 `CURRENT_IMPLEMENTATION.md` 新增本需求条目；`requirements/README.md` 状态更新。
- 已登记的后续事项：
  - D8：幂等记录迁到数据库；
  - Operations 路由与 Platform `OperationsCommandIdempotencyService` 迁到 Gateway；
  - Client Console 通道启用；
  - Payment 等内部接口的 Key Header 清理；
  - Operations 设计稿补“请求幂等”选择。
- 教训：只提交本需求段落时，零上下文补丁会把纯新增段落插错位置（`b0c8b0e` 的 PDManer JSON），已由 `69d868e` 修正；之后改用带上下文的补丁，并对 JSON 做解析校验。
