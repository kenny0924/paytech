# 退款创建 Processor 分阶段重构

用户已明确要求直接重构，顺序为：基础字段验证、请求业务验证、上下文构建、本地事务业务处理、下游调用（如有）、响应构建。本次仅调整 Commerce 的退款创建及其必要协作者，保留现有 API、数据库模型及 Payment 尚未接入的边界；模拟实现仅位于测试模块；不提交或部署。

- Processor 的主方法展示这些阶段；使用本用例不可变上下文，不建立通用模板或空阶段方法。
- 基础阶段验证应用命令所需字段并规范正数金额；HTTP 的结构、Header 和格式验证仍由现有公共入站能力承担。
- 业务阶段经服务查询检查幂等键是否冲突、订单归属、币种精度以及已有退款/当前支付与退款额度。已有退款重放不受后来额度耗尽影响。预检查不持有写事务或排他锁。
- 上下文冻结本次时间、待用标识、语义 Hash 和命令租约身份。数据库冲突重试复用该上下文。
- 本地事务由 Processor 明确编排：锁定最新退款目标、认领或重放命令、调用领域创建、冻结回执、登记补偿；任一步失败全部回滚。领域服务在锁内重新验证最新状态、业务号内容与额度。订单联表锁只获取一次，创建方法在同一事务内普通读取已锁定订单，避免重复联表加锁扫描其他订单的命令记录而形成死锁。
- RefundService 仅处理业务规则、退款事实和金额位置。独立 RefundCreationIdempotencyService 复用 Foundation，封装 Hash、Claim 和冻结回执，不在 RefundService 中保留租约或 HTTP 回执 JSON。
- 创建入口的第 5 阶段由已登记的提交后补偿链承担，保持原来的“本地受理后返回”行为；按用户后续确认，RefundSubmissionHandler 直接承担本地准备、事务外 Payment 调用和结果落库，不单独设置提交 Processor。不新增重复支付调用或虚构下游查询。
- 业务查询阶段当前只读取 Commerce 权威投影；真实下游校验在未来实际接入时放在事务前。本次不新增 Gateway、CCT、Payment 或 Foundation 改动。

验证先增加分层反例门禁，再沿真实 HTTP/MySQL 测试验证幂等、并发额度、重复业务号和补偿恢复。本次本地验证记录：

- 分层反例门禁先在旧 `RefundServiceImpl` 上失败，拆分后通过；Processor 只通过既有 `CommerceIdentifierGenerator` 端口生成上下文身份。
- 实际 HTTP/MySQL 定向验证通过 51 项，其中跨订单同业务退款号竞争临时重复验证 12 轮；诊断探针与临时重复注解已移除。
- `./mvnw -q clean verify`：270 项测试全部通过，无跳过；JaCoCo 行覆盖率 1919/2090（91.82%）。覆盖全额预占/全额成功后的旧 Key、新 Key 重放，以及额度耗尽时优先返回已有 Key 的内容冲突。
- `npm --prefix requirements-site test`：136/136 通过；`git diff --check` 通过。
- 本记录仅证明本地重构验证；未提交、未推送、未部署。

Handler 收拢复核：提交准备、事务外 Payment 调用及结果落库已归入 RefundSubmissionHandler；原提交 Processor 和引用均已移除。此次 `./mvnw -q clean verify` 的 272 项测试、需求站 136 项检查全部通过，普通与可执行业务 JAR 均确认无原 Processor 残留。未提交或部署。

## Gateway Processor 入口统一（2026-09-12）

用户确认在 Commerce 内统一剩余 Processor 的入口、阶段和职责。本次涉及 `CreateRefundProcessor`、`CreateCheckoutProcessor` 及必要业务 Service、测试；不涉及支付准备新功能、其他服务、Gateway、数据库结构或部署。

- `CreateRefundProcessor` 继承 `AbstractGatewayProcessor<CreateCommand, Receipt>`，由模板调用基础字段校验，再进入 `processRequest` 完成金额规范化、业务预检、上下文、本地事务和响应。规范化结果仅在本次调用内传递，退款的预占、稳定回执和提交后补偿语义保持不变。
- `CreateCheckoutProcessor` 保持同一模板入口。事务前计算请求摘要并核对已有命令；只为首次请求分配候选快照，恢复请求不生成新的 Order/Item/Session 身份、时间或 Token。不可变 `CreationContext` 保存 Claim 阶段的实际输入，唯一键竞争后只采用持久化 winner 的原快照。
- Checkout 延续 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`。先保存或接管 Claim，随后核验原上下文、调用事务外 CCT，再分别用短事务提交基础订单和稳定回执；已完成重放或 `ORDER_COMMITTED` 恢复跳过 CCT。确定的窗口、重复业务号或资格拒绝仍在 Claim 后冻结，不能为了调整阶段展示而丢失原键重放语义。
- `CheckoutCreationService` 拥有过期窗口、当前期限、Client Order ID 永久唯一性及创建完成前的基础状态校验；Processor 编排命令租约、事务、下游和失败回执，不读取持久化对象的字段判断业务状态。到期与 CAS 在实际写事务内继续重验。
- 同一个架构检查覆盖 Checkout 创建、订单取消、退款创建三个入口，约束模板继承与公共 `process` 入口归属。新增 Checkout HTTP 场景验证 Claim 提交后事务外调用、调用期间失去租约的原键恢复，以及 `ORDER_COMMITTED` 恢复拒绝提前出现的支付准备坐标。
- CCT 事务观察通过 `src/test` 内的 HTTP Group 拦截器发生在实际调用方线程，HTTP Simulator 继续提供真实协议响应。观察器不替换客户端、业务 Bean 或返回值，不进入业务 JAR。

本轮先用共享入口架构检查复现退款 Processor 未继承模板的问题，调整后 `./mvnw -q clean verify` 的 298 项测试全部通过，0 失败、0 错误、0 跳过；覆盖率门禁为 1936/2108 行（91.84%）。`npm --prefix requirements-site test` 的 136 项检查和 `git diff --check` 均通过，普通及可执行 Commerce Web JAR 未发现 Commerce 测试包、Simulator、Mock、Probe 或测试配置类。证据位于 `/Users/kenny/.codex/verification/commerce-gateway-processors-20260912T102412Z`；未提交、推送或部署。

## 用户重构复核：退款事件由 Handler 定义（2026-09-12）

用户确认工作区的九个文件为其重构，要求理解后提交。此次保留用户的职责调整和 import 整理，并同步受影响的测试。

- `RefundSubmissionHandler` 拥有任务类型和纯静态 `buildEvent(refundNo)`；`RefundModels` 只承载领域命令与结果，不集中存放该 Handler 的补偿协议常量。后续同类事件优先靠近实际执行入口定义，无须另建只有一次转发的工厂层。
- 创建 Processor 只传业务退款号，由工厂封装登记参数。工厂只构造不可变请求，不访问数据库、不调用外部服务、不登记任务、不执行 Handler；登记仍在本地业务事务中，由 Foundation 在提交后唤醒执行器。
- 当前任务没有业务子类型，工厂使用 `"-"` 占位，Handler 不按子类型分流；真实上下文从退款事实恢复，Payload 仍为空。其他业务确有分流时继续遵循其自身合同，事件类型值必须保持应用内唯一。
- 架构检查仅允许当前 Processor 引用该纯工厂，继续拒绝 Handler 实例依赖和直接执行。Handler 专项测试分别验证工厂无副作用、子类型不分流、非法任务类型与多余 Payload；HTTP 测试、数据库观察、扫描和重新登记统一使用当前事件身份。
- 本次没有把 import 自动整理或个别注释删除扩大成新的全项目规范；保留必要的中文语义注释，涉及的条件分支遵守大括号规则。

复核先通过原架构测试复现事件工厂引用被旧门禁拒绝，再调整窄范围门禁及测试调用方。`./mvnw -q clean verify`：301 项全部通过，0 失败、0 错误、0 跳过；JaCoCo 为 1938/2111 行（91.80%）。需求站 136 项检查、`git diff --check` 和业务 JAR 测试类隔离检查均通过。验证证据：`/Users/kenny/.codex/verification/commerce-handler-refactor-20260912T105409Z`；未推送或部署。
