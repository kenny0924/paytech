# Operations 人工审核：实施方案（阶段 4 增量 5）

状态：**已确认（2026-10-13）**，第 8 节四项全部按推荐（A RabbitMQ、A 通用行业 + 四位 MCC、O06/C15 不做、主/子任务一起结束）。5a 已实现，结果只在[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)维护。依据：[API 合同第 2 节](./api-contract.md#2-操作清单)的 O01—O09、I02，[数据设计第 3 节](./data-design.md#3-审核任务与分类)，[简化方案第 2—3 节](./simplification-design.md)，[页面设计第 7 节](./ui-design.md#7-operations-最小审核)，以及[通用化差异稿第 7 节](./generic-material-form-proposal.md#7-审核门禁尚未实现进入后续设计)。

## 1. 要完成的事

客户提交后，运营在 Operations 控制台：

1. 在审核列表找到申请，打开详情查看提交资料、人员关系和材料（含文件查看）。
2. 逐份核验材料：接受或不接受。
3. 选择并确认行业与 MCC。
4. 给出结论：**退回修改**（客户改完再提交）、**通过**、**最终拒绝**。

结论回到 CCT 改变申请状态；通过后 Platform 把客户工作区从“开通中”转为可用。客户页面相应显示退回原因、通过或未通过。

## 2. 操作与归属

| ID | 操作 | Owner | 本期 |
|---|---|---|---|
| O01 | 审核列表（按申请编号、客户类型、状态查询） | Risk 查询；Ops BFF 转发 | 做 |
| O02 | 审核详情：CCT 提交资料 + Risk 任务/要求/证据 + Platform 文件状态 | Ops BFF 聚合 | 做 |
| O03 | 设置行业/MCC 分类（确认前可改） | CCT | 做 |
| O04 | 确认分类，确认后交给 Risk（I02） | CCT → Risk | 做 |
| O05 | 核验一份材料：ACCEPTED / REJECTED | Risk | 做 |
| O06 | 录入人工检查结果 | Risk | **不做**：通用程序没有配置检查项，当前无消费者 |
| O07 | 退回修改：选择分区、填写客户可见说明 | Risk → CCT | 做 |
| O08 | 通过 | Risk → CCT → Platform | 做 |
| O09 | 最终拒绝 | Risk → CCT | 做 |
| I02 | Risk 接收已确认分类 | Risk | 做 |
| C15 | 客户查看审核历史 | Risk | 不做，客户页面先只显示当前结果与公开说明 |

运营权限沿用注册计划的 5 项权限（查看、分类、核验材料、退回、决定）和 Gateway 路由权限绑定；Risk/CCT 记录操作人，不在服务内重复做菜单权限判断，与现有 Risk 配置页面一致。

## 3. 审核门禁（通过前必须满足，由 Risk 判断）

- 本次提交的主任务和所有关联人员子任务都处于审核中，且属于当前提交。
- 每份证据都已核验，不存在未处理的 RECEIVED。
- 每个任务的每项**基础必需**材料至少有一份 ACCEPTED 证据。
- 分类已在 CCT 确认，并且 Risk 已收到同一任务的分类（I02）。
- 通过时主任务和子任务一起结束为 APPROVED，每个任务一条最终决定；页面上的勾选不能代替这项判断。

退回或拒绝不要求以上条件。退回结束当前全部任务（RETURNED），旧任务之后的任何结果都不能再改变申请；客户重提生成新提交和新任务。

## 4. 结果回传 CCT（需要你选）

审核结论在 Risk 本地事务中保存，同时登记一条补偿任务，提交后把结果送到 CCT。CCT 按“提交编号 + 主任务 ID”核对是否仍是当前任务，再改变申请状态：

| 结果 | CCT 申请 | 其他 |
|---|---|---|
| RETURNED | UNDER_REVIEW → RETURNED，可再编辑；保存退回分区和客户可见说明 | — |
| REJECTED | → REJECTED，释放未完成名额 | — |
| APPROVED | → APPROVED，资料、人员关系、材料和分类锁定 | 触发 Platform PLT-I17 把工作区从 PENDING 转为 ACTIVE（已有能力，接上即可） |

**送达方式的选择：**

- **A. RabbitMQ（推荐，符合项目规则）**：项目规则规定内部回调和业务事实走 MQ。Risk 的补偿任务提交后发布事件，CCT 监听，按业务身份防重。代价：这是项目里第一条 MQ 链路，需要建立发布与消费的基础写法、Topic/Group 命名，测试接入本机真实 Broker，工作量明显更大。
- **B. Risk 提交后用内部 HTTP 调 CCT 写入结果**：实现简单，但属于同步回调写库，违反项目“内部回调走 MQ”的规则，只能作为临时方案，之后再迁移。

## 5. 行业与 MCC 目录（需要你选）

CCT 目前没有行业、MCC 目录。可选：

- **A. 行业用一份通用列表，MCC 直接填四位数字（推荐）**：行业先给约 10 个通用大类（零售、数字商品、软件与 SaaS、专业服务、教育、旅游、餐饮、媒体娱乐、非营利、其他），MCC 只校验四位数字格式，由运营按卡组织标准填写。
- **B. 配置完整的 MCC 标准表**：需要导入并维护几百项 MCC 及名称，工作量大。

## 6. 实施顺序

| 增量 | 内容 |
|---|---|
| 5a | Risk：O01 列表、O02 所需详情查询、O05 材料核验、O07/O08/O09 结论与门禁、I02 接收分类；结果补偿任务 |
| 5b | CCT：O03/O04 分类（含 I02 调用）、接收审核结果并迁移申请状态、通过后触发 Platform 工作区开通 |
| 5c | Ops BFF + Web：审核列表、审核详情（资料/人员/材料查看、核验、分类、结论确认面板） |
| 5d | Client Web：退回（顶部原因、定位分区、可再编辑和重提）、通过（开通中/进入工作区）、最终拒绝 |

每个增量按项目测试规范补齐入口测试；数据库应已有全部表（Risk 已迁移，CCT 分类相关列在 9 月十表迁移中已执行），执行前只读核对，若仍需 SQL 单独请你授权。

## 7. 已按建议处理的前序事项

上一轮的两处处理视为已接受：关系“持股/控制”映射为受益人（UBO_KYC）；关联人员已有进行中任务时登记返回 409。

## 8. 待确认

1. 第 4 节：结果回传用 A（RabbitMQ）还是 B（临时 HTTP）。
2. 第 5 节：行业/MCC 用 A（通用行业 + 四位 MCC）还是 B（完整 MCC 表）。
3. 第 2 节：O06 人工检查和 C15 审核历史本期不做。
4. 第 3 节：通过时主任务与关联人员子任务一起结束。

## 9. 实施中细化的合同（2026-10-13，5a/5b）

在已确认范围内细化，不改变上面的业务决定：

- **审核结果事件**：Risk 发布到 Durable Topic Exchange `topic_risk_client_review`，路由键 `client_review.decided.v1`，eventType `ClientReviewDecided`，schemaVersion 1。payload 字段依次为 caseId、caseVersion、applicationId、submissionId、outcome（RETURNED/APPROVED/REJECTED）、returnSection、publicNote、decidedAt、decisionId、decidedBy。ID 都是十进制字符串，退回时 decisionId/decidedBy 为空。payloadHash 是 `sha256:` 加上 payload 按上述字段顺序序列化后 UTF-8 字节的 SHA-256。
- **CCT 消费**：队列 `cg_cct_client_review` 使用 Quorum 类型，投递上限 5 次，死信交换机为 `dlx_cct_client_review`，死信队列为 `cg_cct_client_review_dlq`。处理按申请 + 提交编号 + 主任务 ID 核对，旧任务结果确认后忽略；信封不合法或业务拒绝进入死信队列；并发冲突或依赖不可用重新入队。
- **拒绝时的子任务**：主任务 REJECTED 并写最终决定；关联人员子任务没有被单独判断，结束为 WITHDRAWN，不写决定。
- **通过后开通工作区**：CCT 在同一事务里把 Client 从 PENDING 改为 ACTIVE，追加一版 `CLIENT_ACTIVATED` 生命周期历史（操作人为决定人，触发标识为决定 ID），登记既有的 PLT-I17 投影补偿任务。
- **CCT 补充接口**：除 O03（PUT）、O04（POST）外，新增只读 `GET /client-onboarding-admin/v1/cases/{caseId}/classifications` 和 `GET /client-onboarding-admin/v1/classification-options`，供 5c 页面使用。新增错误码 `REVIEW_CASE_NOT_FOUND`（404）、`CLASSIFICATION_INVALID`（422）、`CLASSIFICATION_CONFIRMED`（409）。Risk 侧新增 `ClientReviewErrorCode`：NOT_FOUND 404、NOT_READY 422，其余 409。
- **依赖门禁**：用户批准 RabbitMQ 方案后，Risk 放开补偿事件/AMQP 禁令；CCT 只放开 AMQP（`spring-boot-starter-amqp`、`org.springframework.amqp:*`、`com.rabbitmq:*`），用于消费审核结果。

## 10. 实施中细化的合同（2026-10-13，5c/5d）

- **O02 申请资料**：CCT 新增只读 `GET /client-onboarding-admin/v1/cases/{caseId}/application`，按审核任务读取申请资料、人员、关系与材料引用，结构与 C05 相同。联系方式不做 Vault Reveal，`contact` 分区为空。任务不是任何申请的当前任务时返回 404 REVIEW_CASE_NOT_FOUND。CCT `/client-onboarding-admin` 改为只记录元数据的日志路径。
- **O02 聚合**：Operations BFF 先读取 Risk 任务；Risk 失败则整页失败。之后并发读取 CCT 的申请资料、分类和行业目录，某一部分失败时标为 `UNAVAILABLE`，不当作空数据。行业目录和当前分类并入 O02 详情，不单独注册 Gateway 路由。
- **O01 查询参数**：按 Risk 已实现的合同，使用 page、size、status、qualificationType、applicationId，注册计划已同步。客户类型筛选对应 qualificationType。
- **命令转发**：O03—O05、O07—O09 由 BFF 单次转发，不重试，也不转发 Idempotency-Key（Owner 按任务状态防重）。页面对同一路径和内容重试时沿用原键，供 Gateway 重放。
- **退回原因的读取（偏离第 4 节“CCT 保存退回分区和说明”）**：CCT 不新增列，退回分区和客户可见说明仍只保存在 Risk 任务上。Risk 新增私网只读 `GET /client-onboarding-review/v1/applications/{applicationId}/result`，返回最近一次主任务的状态、结果、退回分区、公开说明和结束时间，不含内部理由或决定人。Client BFF 先经 CCT 核对申请属于当前账号，再在状态为 RETURNED、APPROVED 或 REJECTED 时读取；结果与 CCT 状态不一致或 Risk 不可用时标为 `UNAVAILABLE`，页面显示通用说明。
- **客户结果页**：退回时页面顶部显示需要修改的分区（可跳转）、公开说明和重新提交提示，表单仍按 CCT `editable` 可编辑。通过时只显示“工作区正在开通”，不提供进入工作区的入口：Platform 还没有客户侧工作区列表和 C02 切换接口。最终拒绝时显示公开说明，没有说明则显示通用文案。
- **未完成的依赖**：运营侧受控文件查看需要 Platform 提供运营文件读取或下载接口，页面暂时只显示文件编号。Platform 页面能力清单 `console-resource-capabilities.json` 已增加 `OPERATIONS_CLIENT_ONBOARDING_REVIEW`，与 Web `pageCapabilities.ts` 逐字节一致。路由、权限和菜单资源仍按注册计划由 Platform 配置，尚未执行。

