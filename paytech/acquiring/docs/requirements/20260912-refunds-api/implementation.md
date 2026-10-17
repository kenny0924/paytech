# Commerce Refunds 本地实现

> **列表入口的 2026-09-16 后继：** [ListOrderRefunds 接口重构](../../../../../requirements/20260916/commerce-ListOrderRefunds接口重构/technique.md)取代下文列表 Service 的只读事务、OrderRecord 和联表读取。列表改由 Processor 按需查询，字段和游标合同保持；下列历史测试结果不证明此次重构已验证，当前状态见[台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-list-order-refunds-20260916)。

> **创建入口的 2026-09-16 后继：** [CreateRefund 接口重构](../../../../../requirements/20260916/commerce-CreateRefund接口重构/technique.md)取代下文创建请求的命令 Hash/Claim/Lease、回执快照及事务内整单重查方式。新入口按 clientRefundId 比较原业务内容，Service 以 REQUIRED 原子保存退款、预占和提交任务。下列测试属于前序实现，不能作为本轮重构已验证的证据；当前状态见[台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-refund-20260916)。

本次用户已确认开发四个 Refunds 接口，仅修改 Commerce；外部依赖先 Mock 并标明 TODO，不配置 Gateway。

模拟范围已按用户后续指示收紧为测试模块：业务源码不得包含模拟实现或测试 Profile。具体边界见 [测试代码隔离说明](test-isolation.md)。

契约沿用仓库 `2026-09-05-commerce-client-api-v1-design.md` 第 11 节：创建退款、按订单分页、按退款号查询、按 Client Refund ID 查询。现有可退款金额接口保持不变。

实现顺序：HTTP 失败用例；本地退款持久化、并发预占、命令幂等；Foundation 补偿 Handler 与 Payment Mock；查询及游标；真实本地 MySQL / HTTP / 补偿恢复验证。

创建命令在一个本地短事务提交退款、预占、不可变 201 回执及补偿任务。任务只携带退款号，提交后调用 Payment 窄端口；未知/不可用保留 PROCESSING 和预占。确定成功消费预占，确定失败/取消释放预占，重复执行不得重复记金额。新 Key 和相同 Client Refund ID / 内容重放原创建回执；内容冲突拒绝。金额继承订单币种，Client 永久唯一业务 ID 与传输 Key 分开处理。

查询仅访问本库且按可信 Client 隔离；游标只含已公开的创建时间和退款号，按创建时间、退款号倒序，并验证锚点属于该 Client 的目标 Order。

复用已存在的 t_refund_request、t_order_refund_position、t_command_idempotency、t_compensation_event；不新增 DDL，不修改 PDManer。短事务使用实际 Order 行锁、退款位置条件更新和数据库唯一索引保证并发不变量；外部调用不持有数据库事务。

Payment 真实 PAY-I04 / 结果查询与 MQ、Platform Client Webhook 待后续接入。本期不声称真实支付退款链路已经联通。Foundation 的提交后唤醒和扫描恢复入口接入代码；XXL-Job 部署调度未启用，失败任务达到重试上限后仍保留预占，需恢复同一任务，不能重建退款或自动判失败。

补偿接入发现当前 Foundation XML 显式引用通用 CodeEnumTypeHandler，MyBatis 按 Handler 类缓存会误复用 Commerce 的最后一个枚举实例。Commerce 最后注册准确的 CompensationEventStatus 实例，业务 Mapper 继续按 Java 类型取各自实例；实际登记读取、Claim、扫描恢复测试保护此约束。TODO(Foundation)：由其独立改动移除 XML 上的通用 Handler 显式绑定，本期未修改 Foundation。

本期不改变现有 idx_refund_order(order_id, create_time, refund_request_id)。API 按 create_time / refund_request_no 稳定分页，使用现有索引前缀限制 Order 范围；不为调整次排序键擅自迁移数据库或 PDManer。

## 本地验证记录（2026-09-12）

- `./mvnw -q clean verify` 退出码 0：267 项测试，0 失败、0 错误、0 跳过。
- 退款生产路径已纳入同一 90% 门禁：1843/2009 行，91.74%。没有降低阈值或排除退款代码。
- 真实 HTTP / MySQL 验证包含预占原子性、同 Key / 新 Key 幂等、业务 ID 冲突、金额及支付事实拒绝、Client 隔离、分页锚点、Operations 查询、补偿领取和租约过期接管、Payment 未接入/未决、成功/失败/取消、重复终态执行。
- OpenAPI 中文说明调整后，Refund API、Common 及 OpenAPI 合同专项测试通过。
- `npm --prefix requirements-site test`：135/136 通过。唯一失败是未修改的 Gateway 部署状态文档断言：测试仍要求“本轮动态路由及 Client 源码未部署”，当前台账已记录部署。此次没有改 Gateway 或根工作区台账。
- `git diff --check` 通过；没有执行 DDL、提交、推送或部署。原有 `OrderModels.java` 导入整理属于先前未提交改动，保持原样。
- 本机执行证据：`/Users/kenny/.codex/verification/commerce-refunds-20260912T072958Z`。

退款创建没有外部调用或可单独提交的 Root 前阶段，Claim、Root、回执与任务全部一次提交；因此不保存另一个重复的请求恢复快照，也没有新增 schemaVersion。任务恢复只从已提交退款行重建，技术中断不会留下已提交的半成品创建 Claim。

## Handler 独立入口测试补充（2026-09-12）

用户明确 Handler 与 HTTP 同为独立入口。新增 `auto/ext/job/RefundSubmissionHandlerAutoTest`，不依赖 HTTP 创建退款来间接覆盖 Handler；TestMapper 只准备本次独占的已支付订单、退款与预占，实际执行经过生产 Handler、Service、Repository 和本地 MySQL。

- 14 项测试覆盖三种非法任务元数据、缺失退款、准备失败、未决结果、三个退款终态、重复投递、已受理后响应丢失及同请求恢复、结果事务第二次写入失败时资金回滚。
- 调度场景通过真实 Registrar/Dispatcher/Scanner 验证永久拒绝回写、未到执行时间、未决重试与过期 Claim 接管、终态任务删除。测试侧记录 Payment 端口进入时的事务状态；生产代码没有新增测试探针或分支。
- Java 25 `./mvnw -q clean verify`：286/286 通过，0 失败/错误/跳过；Handler 独立测试 14/14，Handler 行和分支覆盖均为 100%（15/15 行、10/10 分支），原生产覆盖率门槛不变。
- 普通及可执行 Commerce JAR 均未包含测试包、Simulator 或测试配置；需求站规则检查 136/136 通过，差异空白检查通过。
- 本机证据：`/Users/kenny/.codex/verification/commerce-handler-entry-20260912T092455Z`。本轮只修改测试与规范，未提交、推送、部署或变更 Schema；Payment 仍为获准的测试侧端口模拟，未验证真实 PAY-I04 HTTP 对接。
