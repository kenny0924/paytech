# Foundation 服务本地命令幂等能力决策

> **2026-09-16 CreateCheckout 修订：** [业务重构决定](../../requirements/20260916/commerce-CreateCheckout业务重构/technique.md)移除 CreateCheckout 的请求幂等执行。新订单国家保存在 `t_commerce_order.consumer_country_code`，旧命令快照只保留历史读取；本文其他操作的命令合同不变。


- Decision status: Confirmed
- Implementation and deployment status: see [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)
- Decision date: 2026-09-09
- Superseded sections: none；本决定只调整通用机制的代码归属，不改变现有业务合同、状态机或数据库所有权

## 1. 背景

Commerce Hosted Checkout 已经需要同一组可复用的命令 Claim、唯一 winner、Lease 接管、Version CAS、恢复阶段推进和稳定
响应冻结机制。继续由每个业务服务复制 Mapper、Repository 和并发判断会造成实现漂移；把完整幂等语义交给 Gateway，则无法
与业务服务自己的数据库事务、资源唯一键和恢复检查点保持原子一致。

## 2. 已确认决定

### D1. 新增普通 Foundation JAR

新增 `com.paytech:paytech-command-idempotency`。它是由业务服务按需直接依赖的普通 Foundation 组件，不是独立服务、Gateway
插件、MVC Starter 或中央幂等数据库。宿主通过 `command-idempotency.enabled=true` 显式启用自动配置；未启用或未依赖的服务
不受影响。

### D2. Foundation 只拥有通用一致性机制

Foundation 拥有：

- 服务本地 `t_command_idempotency` 的通用 MyBatis Mapper 与 Repository；
- `Client/Caller Scope + Operation Type + Idempotency-Key` 唯一 winner 的 Claim；
- `PROCESSING | SUCCEEDED | FAILED` 技术状态；
- Lease 续租/到期接管、Version CAS、恢复阶段推进和稳定成功/失败响应冻结；
- 写操作必须加入宿主已开启的本地可写事务这一 fail-closed 边界。

Foundation 不拥有：

- 哪些 HTTP 方法需要幂等、调用方作用域和 Operation Type 的业务目录；
- 请求 Projection、JCS、Hash/Hash Schema、Snapshot Schema/Codec 和响应 Body 内容；
- 业务恢复阶段及其先后关系、资源类型/业务 ID、错误码和重放策略；
- Processor 编排、外部调用、数据库事务的开启/提交/回滚或业务表唯一键。

Operation Type 与 Recovery Stage 因此通过稳定字符串 Code 进入公共 API；Foundation 不引用任何业务枚举或 DTO。

### D3. 事务与数据库仍由业务服务拥有

JAR 不创建或隐藏 `TransactionTemplate`，也不接收 Supplier/Callback 代替业务编排。`claim`、Lease、阶段推进和完成冻结必须在
宿主现有短事务中执行，从而可以与宿主业务表写入原子提交；普通查询可以在事务外执行。每个核心系统继续只访问自己的数据库，
不引入跨库查询、中央表或 Gateway 写库。

JAR 当前使用与 Commerce 已批准 `t_command_idempotency` 相同的列合同，因此 Commerce 接入不需要 DDL、PDManer 或数据迁移。
未来服务只有在自己的业务切片批准表结构、恢复语义和迁移方案后才能接入，不能因为 JAR 已存在而自动套用。

### D4. 首次迁移只包含 Commerce

本次只迁移 `commerce-service`：移除 Commerce 重复的 Command Idempotency DataObject、Mapper、Repository 和 Mapper XML，
由现有 Checkout Idempotency Domain Service 组合 Foundation API。Commerce 继续拥有 Checkout Hash、Snapshot、Validator、
Converter、三个真实恢复检查点和三个短事务。

Risk、Platform Support、Gateway 和其他系统的现有幂等实现、表结构与行为全部保持不变；后续是否迁移必须单独评审和授权。

### D5. 测试入口按能力边界区分

Foundation JAR 没有 HTTP 业务入口，可以从公开 `CommandIdempotencyService`、自动配置和内部纯机制对象做聚焦测试，验证事务参与门禁、Claim winner、CAS 丢失分类和 opt-in 装配。这类数据库无关测试不作为 Mapper SQL、真实 Lease 竞争或业务恢复语义已经验证的证据。

Commerce 的 Checkout 业务行为仍只从真实随机端口 HTTP 入口验证，经过 Controller、Processor、Commerce Domain Service、Foundation Mapper 和本地数据库；不为接入 JAR 后的每个 Commerce Service 复制内部行为测试。真实 SQL、唯一 winner、Lease/CAS 和事务回滚必须在准备好 Commerce 本地 Schema 后由该入口链路完成。完整公共规则见 [`UNIT_TEST_GUIDE.md`](../UNIT_TEST_GUIDE.md)。

## 3. 非目标

- 不把 `Idempotency-Key` 的业务执行幂等移动到 Gateway；Gateway 仍只校验/转发已批准的传输 Header。
- 不增加 Redis、MQ、定时任务、自动重试、中央清理、跨服务查询或统一业务状态机。
- 不改变 Commerce Client API、HTTP 状态、RFC 9457、Checkout Token、金额、过期或 Order 语义。
- 不执行数据库 Schema、发布 Nexus、推送、部署或迁移其他服务。
