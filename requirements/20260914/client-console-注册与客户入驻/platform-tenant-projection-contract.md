# PLT-I17：Client 工作区来源投影合同

本文细化[统一人类认证设计 §13.5/§15](../../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md)及 [Platform 数据库](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)的现行 PLT-I17。实际研发、验证与部署状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。不新增表、业务资料版本或公共 Gateway 路由。

## 1. 私网接口

Java 合同位于 [TenantProjectionApi](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/tenantprojection/TenantProjectionApi.java)。服务 Context Path 为 `/platform-support`，下表列出的路径均相对于它。

| 方法与路径 | 用途 |
|---|---|
| POST `/tenant-projections/v1/tenants/provision` | CCT Client Root 第一版创建工作区 |
| POST `/tenant-projections/v1/tenants/status` | 消费连续的 Client 生命周期事实 |
| GET `/tenant-projections/v1/tenants/CLIENT/{ownerRefId}` | CCT 在响应丢失或补偿恢复时查询已提交位置 |

本批实现只接受 `CLIENT`。Partner 的已有架构合同继续保留，但本次入口不开放 Partner 或 INTERNAL 写入。接口不接受浏览器身份直接开通，也不用于 PLT-I02 授权；部署仍须保证 Platform 私网边界，不能把这些路径登记为 Client 公共接口。

Provision Body 恰好五个必需字段：

```json
{
  "tenantType": "CLIENT",
  "ownerRefId": "123456789",
  "requestedByAccountId": "987654321",
  "sourceVersion": 1,
  "payloadHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

Status Body 在这五个字段上增加 `ownerStatus`，允许 `PENDING/ACTIVE/SUSPENDED/TERMINATED`。`ownerRefId` 是 CCT Client ID；`requestedByAccountId` 是申请创建时冻结的 CUSTOMER Account。ID 必须为正的规范十进制字符串，且在 Java Long 范围内。`sourceVersion` 是正整数 JSON Number；Provision 必须为第一版。`payloadHash` 是 CCT 原始不可变来源事实的 SHA-256 小写十六进制值，不由重试时的可变表单重新计算。

未知/重复字段、类型强转、尾随 JSON、缺少字段和超限 Body 拒绝。JSON 协议错误返回 400；Bean Validation 字段问题沿用 Foundation 422 `VALIDATION_ERROR` 与 `errors[].field`。输入和输出都只记录 HTTP 元数据。

成功返回 200，字段为 `tenantType, ownerRefId, requestedByAccountId, tenantId, memberId, ownerStatus, sourceVersion, payloadHash`；ID 仍为字符串。GET 返回当前已提交位置，缺失为 404。该响应不是 Tenant Token、文件上传许可、材料扫描结论或客户审核通过证明。

## 2. 首次开通与角色切换

首次开通要求本库真实 `CUSTOMER/HUMAN/ACTIVE` 账号。一个本地事务创建：

- `PENDING` Access Tenant，独立 `security_status=ACTIVE`；
- 申请人的 `ACTIVE/OWNER` Member；
- 本 Tenant 范围内的 `TENANT_ONBOARDING_OWNER` 系统角色及其有效授权。

初始 `tenant_name` 使用 `Client {ownerRefId}` 作为可确定的工作区标签，不复制未批准姓名、组织法定名称或占位法律资料。Role 的 `scope_key` 为真实 Tenant ID 字符串，初始 `granted_by` 绑定新建的真实 OWNER Member。命令不会初始化全平台权限目录，也不会授予未批准的其他角色或权限。

首次投影 ACTIVE 时，在同一本地事务撤销旧入驻角色授权并创建/授予 `TENANT_OWNER`。旧授权保留为 REVOKED。后续暂停与恢复不重复授予或复活已被单独撤销的成员/角色权限。任何来源更新都不改变 Platform 的独立 `security_status` 或 Member 状态。

PENDING Tenant 仍不能签发普通 Tenant Context Token。REJECTED/WITHDRAWN 是 CCT Application 状态，不直接映射为 Tenant TERMINATED；Client 生命周期仍由 CCT 决定。本文不替代当前申请、文件归属或 Risk 门禁。

## 3. 来源身份与恢复语义

唯一来源为 `CLIENT + ownerRefId`；创建时的唯一 OWNER 固定申请账号。本期不通过重放更换该账号，亦不添加团队成员。

| 情况 | 响应与效果 |
|---|---|
| 同一 Provision 身份、同版本同 Hash | 返回原稳定 Tenant/Member；无重复写入或成功审计 |
| 同版本不同 Hash、账号不一致、同版本不同状态 | 409 `TENANT_PROJECTION_CONFLICT` |
| Provision 晚于已经推进的来源位置 | 409 `TENANT_PROJECTION_STALE`，不回退；CCT 可 GET 核对原来源和申请人 |
| Status 先于 Provision，或跳过中间版本 | 503 `TENANT_PROJECTION_NOT_READY`，保持原状；重试原身份并补齐前序 |
| Status 旧于当前版本 | 返回当前投影，忽略旧事实；不修改状态或再追加成功审计 |
| 连续下一版状态 | 以来源序号和本地并发版本条件更新 |
| TERMINATED 尝试复活，或非 PENDING 回退 PENDING | 409，不修改任何授权或状态 |
| 首次开通账号缺失、错误 Realm/类型、未激活 | 403 `TENANT_APPLICANT_INVALID`，不创建工作区 |

账号独立停用后，既有来源的关闭/暂停仍可处理，避免账号停用阻断 CCT 收敛。不能用未知账号、错误 Realm、缺失 OWNER 或无来源身份的历史 Tenant 自动“补成”合法投影。

CCT 必须恢复原 Client Root 历史的来源身份，不能用最新 Application 表单版本替代它。状态命令的 503 需要重试，永久冲突需要保留失败依据；仅查询当前位置不能证明 CCT 本地业务写入、文件上传或审批链已经完成。

## 4. 事务、并发及开关

Processor 继承 Foundation `AbstractLockProcessor`，通过 `LockCommand.tryLocks` 一次提供 `lock:customer:account:{accountId}` 与 `tenant:source:client:{clientId}`；账号键与 Client 认证入口一致。完整逻辑 Key 不再添加额外前缀，沿用服务级 `redis.coordination.resource-lock.wait-time/lease-time`，在启动期校验有限等待及租约。全部 Key 排序去重后共享一次等待预算，全部 Redis 锁在本地事务之前取得，后续锁获取失败或提交/回滚完成后逆序释放。事务内按 Account → Tenant → Member/Role 顺序操作本库；不调用 CCT、Vault 或 Redis。

来源唯一索引、Account/Tenant 行锁及来源/本地版本 CAS 是 Redis 租约到期后的最终保护。不同申请人竞争同一不存在来源时，精确 `uk_access_tenant` 冲突在整体回滚后转换为 409，不把其他唯一键错误冒充幂等成功。

数据库提交后沿用 Platform 既有审计管线，只记录 CCT 来源、Client/Member 引用、来源序号、状态及授权效果；不复制请求 Payload、邮箱或资料。开通失败整体回滚，不能留下孤立 Tenant/Member。

`tenant.projection.enabled` 默认 `false`。显式开启还要求当前 Platform IAM/审计基础能力及 Redis 协调；复用应用唯一 Snowflake Worker。当前认证适配器自身的 local/auto/test 限制继续生效。无需另建连接池、跨库访问、自动 Schema 或启动初始化 SQL。

CCT 消费端的不可变来源恢复和 payloadHash 输入由[恢复实施合同](./cct-workspace-recovery-contract.md)细化，不改变本文 Wire。
