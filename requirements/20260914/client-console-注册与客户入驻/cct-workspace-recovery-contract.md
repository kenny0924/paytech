# CCT 工作区恢复实施合同

本文细化已经批准的 [CCT 写端依赖](./cct-write-dependencies.md)和 [PLT-I17](./platform-tenant-projection-contract.md)，不改变业务状态或浏览器 API。实现、验证和启用状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 来源与资料边界

`CLIENT_TENANT_PROJECTION` 只处理 Client 生命周期。普通入驻资料保存仍按[简化决定](./simplification-design.md#3-单份资料与审核状态)执行，不写资料版本或 BCR 表单快照，不递增生命周期序号。恢复不读取姓名、证件、联系方式、经营资料、Risk 证据或 Vault。

事件身份为 `event_key=change_version_id` 的规范正十进制字符串、`event_type=CLIENT_TENANT_PROJECTION`、`sub_event_type=-`，Payload 为 NULL。来源使用既有 CLIENT Root 的不可变 `t_business_change_version` 明细；顺序来自该 Root 的 `version_no`，第一条为 CREATE/PENDING，后续为 UPDATE。与表单版本无关，不在任务表增加版本列。

创建 Client 的写事务必须同时保存业务对象、必要的生命周期 BCR 及该任务；之后每次推进这个生命周期 Root，也必须登记对应任务，不能跳号。只有同一提交成功后才允许发出 HTTP。工厂 `ClientTenantProjectionHandler.buildEvent(changeVersionId)` 只组装请求；不能直接执行 Handler 或在提交后另建任务。个人和组织共用这一协议。完成草稿写端之前，测试中准备的来源不能作为 C04 已实现的证据。

申请人取自 CLIENT 第一条 CREATE 的 `create_by`，并核对存在属于该账号、该 Client 的申请。后续操作者可以不同，但不能替换原申请人。来源必须同时满足当前不可变 Client ID/Party ID/编号关联、CLIENT Root Tuple、空 Parent、正且不超过 Root 当前位置的序号、ENTITY_NO 格式及完整性校验；后续事实还校验第一条 CREATE。旧格式、缺失来源和不一致关联均保留失败任务供调查。

## CLIENT 安全审计目录

本目录只覆盖 CLIENT Root，不授权恢复资料历史。`data` 精确包含字符串 `clientId, partyId, clientNo, status`。`_meta` 使用现行 BCR 信封：schemaVersion=1、triggerType=COMMAND、规范正十进制 triggerId、reasonCode、integrityAlgorithm=SHA-256、integrityHash。reasonCode 仅接受 CLIENT_CREATED、CLIENT_UPDATED、CLIENT_ACTIVATED、CLIENT_SUSPENDED、CLIENT_TERMINATED；其他原因需先扩展 Owner 目录。

这是内部业务元数据，无客户资料或凭据。沿用现行 CLIENT BCR 的追加写、访问控制、Retention 和 Legal Hold 规则；本切片不引入归档、删除或保留期限的新策略。所有 ID 与领域序号在 Hash 输入中是字符串，schemaVersion 为 JSON 整数；UTC 时间固定六位微秒。最大 UTF-8 长度 64 KiB，拒绝额外字段、重复属性、非规范值或损坏的 Hash。

完整性输入使用现行 Domain `CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1`。JCS Object 的字段精确为 `domain, record_type, record_id, aggregate_root_type, aggregate_root_id, parent_history_id, history_id, version_no, operation_type, create_by, create_time, snapshot`；Parent 为 JSON null，snapshot 为移除 `_meta.integrityHash` 后的完整信封。UTF-8 RFC 8785/JCS 后取 SHA-256 小写 Hex。

PLT-I17 payloadHash 是以下 JCS Object 的 SHA-256 小写 Hex：`domain=CODE-MOR:CCT:CLIENT_TENANT_PROJECTION:1`、`clientId`、`requestedByAccountId`、`sourceVersion`、`ownerStatus`、`sourceIntegrityHash`。除 Domain/状态/Hash 外三项均为规范十进制字符串；sourceIntegrityHash 为上述已经核验的审计完整性值。重试只从原不可变来源恢复，不能用当前表单重新计算。

## 恢复顺序

每次尝试先在 CCT 短只读事务内读取准确来源，再结束事务并 GET Platform 当前来源位置。

- Platform 尚无记录：第一条调用 Provision；后续条调用 Status，缺少前序由 Platform 返回可重试错误。
- Platform 落后：先用对应的本地历史校验它的账号、Client、状态及 Hash，然后提交当前原始事实。跳号保持待恢复，不改号重发。
- Platform 已在同一位置：身份、状态、Hash 及正的 Tenant/Member ID 全部一致才成功。
- Platform 已在更高位置：必须存在对应的有效 CCT 原始历史并逐项匹配，才能完成旧任务。不能仅按数字大小忽略任务。
- 网络超时、截断响应、暂时不可用或开通命令与状态推进竞争：保留 ERROR，下一次先 GET；不知道结果时不创建新 Client 或申请。
- 明确拒绝、身份/Hash 冲突、历史损坏或调用者在数据库事务中直接执行：保留 FAILED，不能自动当作成功。

执行上限 25 秒；HTTP 连接 2 秒、读取 3 秒，禁止重定向。最多 12 次尝试，从 5 秒指数退避到最多 30 分钟，抖动 20%；耗尽保留 FAILED。失败摘要只用固定安全文本。恢复失败记录的人工重开仍遵守 Foundation 既有审计合同，本切片不新增无审计的重开入口。

## 装配边界

`onboarding.recovery-enabled` 默认 false；显式开启需同时启用现有 CCT 数据库和 compensation，提供已分配的 `onboarding.id.datacenter/worker`。使用同一个 CCT DataSource/事务管理器，Checkout 仍在只读事务中查询。应用不建表、初始化业务数据或注册公共路由。

普通运行通过 Nacos/LoadBalancer 访问 `platform-support-service` 的私网 `/platform-support`。仅 auto Profile 可指定 `onboarding.platform-test-base-url`，限 `http://127.0.0.1:<port>/platform-support`，禁止用户信息、Query 或 Fragment。测试不能冒充真实 Platform Owner 的完整集成验收。
