# 2026-10-14 Vault 策略入库与运营人工加解密

- 状态：**已确认**（2026-10-14，随需求阶段 3 确认）
- 需求：[Operations 维护 Vault 数据类型与手动加解密](../../requirements/20261014/operations-console-Vault数据类型与手动加解密/requirement.md)，技术方案第 5～13 节
- 取代范围：[Vault V1 三表设计](../../docs/superpowers/specs/2026-09-02-vault-database-design.md)中“Allowlist 是部署配置，不是数据库表”一条；[2026-10-13 渠道账号读取决定](./2026-10-13-platform-channel-account-reveal.md)中“只有 Platform 以 `CHANNEL_EXECUTION` 读取”的人工访问部分。其余 V1 边界不变。

## 决定

1. **策略入库。** Vault 的数据类型与服务访问规则（`caller_service × action × data_type × purpose`）存于 Vault 库，按版本记录。Operations 维护，保存即生效，不需要审批；每次变更记录操作人、时间、前后差异与理由。Vault 只从数据库读取策略并定时全量热加载，审计中的 `policy_version` 记为 `db-<版本号>`；不再读取静态策略文件，各环境现有策略由初始化 DML 转写入库。
2. **数据类型属性。** 增加数据级别（`STANDARD` 普通敏感 / `SECRET` 密钥）、允许人工加密、允许人工解密、状态；编码与 Token 模式创建后不可改，只停用不删除。数据分类审批仍是新增类型的前置条件，页面不替代该审批。
3. **管理接口与调用方。** Vault 新增 `/vault/v1/admin/...` 管理接口，只接受部署配置 `VAULT_ADMIN_CALLERS` 中的调用方（默认 `operations-console-bff`），该配置不能在页面上修改。
4. **运营人工加解密。** 有权限的运营可经 Operations 对开启了对应开关的数据类型执行 Protect / Reveal；用途固定为 `OPERATIONS_MANUAL`，必填理由码与工单号；Vault 审计记录操作人账号、理由码、工单号。普通敏感与密钥类分别由独立权限点控制。
5. **受控例外。**
   - 人工加密生成的 Token 可一次性展示给执行操作的运营，用于写入业务表；不得进入审计、列表、普通日志或 Gateway 重放存储。
   - 密钥类数据（如 `CHANNEL_ACCOUNT_CONFIG_V1`）可在 `VAULT_MANUAL_REVEAL_SECRET` 权限下人工解密；页面默认遮罩、限时显示，单人限流。
6. **保持不变的限制。** `X-Caller-Service` 可伪造、无强服务身份、无生产 KMS、无 Binding/Retire/Destroy/Blind Index；卡数据、登录密码、根密钥等禁止类型不进入 Vault。
7. **审计只在 Vault 本地（2026-10-15 补充）。** 策略变更（`t_vault_policy_change`）与人工加/解密（`t_vault_access_event` 的操作人、理由码、工单号、结果）只在 Vault 本地持久化，经 Operations“策略变更记录”“人工操作记录”页查询；不进入 Platform 集中审计（`t_audit_event`），Operations BFF 不调用 Platform 写审计。集中转发（建议由 Platform 定时按游标拉取 Vault 人工操作审计，以 `source_system/source_event_key` 去重）连同职责分离、统一保存期限，留待生产前数据合规专项另行设计。

## 理由

- 新增数据类型与开通服务访问需要改文件并重启，运营无法自助，也没有变更记录。
- 渠道凭据等密钥需要运营自行加密写入业务表；排查问题需要受控地核对原值。人工操作此前只能由研发直接调用内部接口，没有权限、理由与操作人审计。

## 影响

Vault（新表、热加载、管理接口、审计扩列）、Vault Java 客户端（管理客户端）、Operations BFF 与 Web（新页面）、Platform（权限点、菜单、路由注册数据）。现有服务的 Protect/Reveal 合同不变。实现与部署状态只见 [CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md)。
