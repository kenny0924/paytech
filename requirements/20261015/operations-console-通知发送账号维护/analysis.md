# Operations 通知发送账号维护：现状差异与影响分析（阶段 2）

> 状态：阶段 2 已确认（2026-10-15，含 R-01 方向）。基线 `main` `8d26229`；只读核对，未改代码、未执行 SQL。

## 1. 结论摘要

- **日志不用改**：`/notification-admin` 已在 BFF（`application.yml` 第 96 行）和 Platform（第 117 行）的 `metadata-only-path-prefixes` 中，密钥接口放在这个前缀下即只记元数据。Gateway 访问日志只记元数据，请求体只在内存中缓冲用于转发（`RequestBodyLimitGlobalFilter`），不落盘。阶段 1 第 3 节第 9 条据此简化为"新接口放在 `/notification-admin` 下，加日志不泄露测试"。
- **最大改动在发送端**：适配器现在按 `provider_code` 绑定一个启动时创建的 Spring Bean；要改成"按账号类型选适配器、按账号配置发送"，并同步改测试基础设施（第 4 节 R-01）。
- **Vault 只改策略数据**：数据类型 `NOTIFICATION_PROVIDER_CREDENTIAL_V1` 已存在（REFERENCE / SECRET / JSON 值格式），只需新增一条 `platform-support-service / PROTECT / NOTIFICATION_PROVIDER_SETUP` 规则，经运营后台 Vault 管理页或管理接口添加；Vault 代码不改。需要一份 Vault 边界决定记录。
- **新增接口 4 个**（新建账号、编辑账号、更换密钥、测试发送），列表与启停接口调整字段；无新表，`t_notification_provider` 加 5 列。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| 账号字段入库（范围 1） | `t_notification_provider` 只有编码、介质、状态、版本（schema 第 733 行）；区域、发件地址、Configuration Set、Token 在 `SesEmailProperties`（`notification.ses.*`） | 账号表保存类型、区域、发件地址、Configuration Set、密钥 Token | 未实现 | 加 5 列；DO、Mapper、Repository、PDManer、测试 DDL 同步 |
| 新建账号（范围 2） | 只有 `GET /providers`、`PATCH /providers/{code}/status`；账号只能由初始化 DML 写入 | 页面新建，默认停用 | 未实现 | 新增 `POST /providers`，Processor 加编码资源锁 |
| 编辑账号（范围 3） | 无 | 改区域、发件地址、Configuration Set，乐观锁，被启用路由引用时确认 | 未实现 | 新增 `PUT /providers/{code}`；影响提示复用启停时的"被引用模板"查询 |
| 录入 / 更换密钥（范围 4） | Platform 只有 `REVEAL / NOTIFICATION_DELIVERY`（`SesCredentialVault`）；Protect 由运营在 Vault 手动加密页完成，Token 写进部署配置 | 页面只写不读，Platform 事务外 Protect，本地短事务替换 Token | Platform 无 Protect 调用、Vault 无对应规则 | `VaultClient.protect` 已存在（`ProtectCommand`）；新增 `PUT /providers/{code}/credential`；Vault 补规则 |
| 启用条件与列表（范围 5） | `PATCH status` 只校验存在与版本；列表 `configured` 来自 `EmailProviders.isConfigured`（是否有绑定该编码的 Bean） | 字段齐全才能启用；列表显示「配置完整」、密钥是否录入、最近更换时间 | 判断依据不同 | 启用时校验；`configured` 改为按字段计算；密钥更换时间需存（见 3.1 新列） |
| 发送端改造（范围 6） | `EmailProviders.require(code)` 在 `NotificationDeliveryHandler`（第 121、155、165、187 行）、`SendTransientEmailProcessor`（第 57 行）、`NotificationConfigurationServiceImpl.requireProvider`（第 55 行）三处使用；`SesEmailConfiguration` 启动时建唯一 `SesV2Client`；Token 为空走 `DefaultCredentialsProvider` | 按账号类型选适配器，按账号区域缓存客户端，按账号配置发件；无默认凭据链 | 适配器合同按编码而非账号 | `EmailProvider` 方法增加账号参数；客户端按 区域+终端 缓存；删除默认凭据链分支 |
| 测试发送（范围 7） | 无；预览只渲染不发送 | 指定邮箱发固定内容，同步返回结果码，不进发送记录 | 未实现 | 新增 `POST /providers/{code}/test-send`，事务外调用适配器，仅写审计 |
| 部署配置收敛（范围 8） | `application.yml` 第 165—176 行 `notification.ses.*`；test Compose 只设 `NOTIFICATION_SES_ENABLED=false`；本机 `run-service.sh` 不设 SES | 只保留 `enabled`、`endpoint`、超时 | 字段需删除 | 删 5 个属性及其校验；README 与 Compose 注释同步 |
| 日志防护（范围 9） | 见第 1 节，已满足 | 不泄露原文与 Token | 无缺口 | 只补测试：BFF、Platform 日志与审计不含合成密钥 |
| 权限与审计（范围 10） | 权限 `NOTIFICATION_READ/MANAGE`（ID 86101/86102）；按钮资源 `OPERATIONS_NOTIFICATION_PROVIDER_MANAGE`（86306） | 新权限 `NOTIFICATION_CREDENTIAL_MANAGE`，新按钮资源，新路由 | 未注册 | 注册 DML 新块（权限 1、角色授权 1、按钮 2—3、路由 4），默认停用；审计字段补区域、发件地址摘要、凭据已更换 |
| Vault 策略（范围 11） | 规则只有 `REVEAL / NOTIFICATION_DELIVERY` 给 Platform；人工 Protect 用途 `NOTIFICATION_PROVIDER_SETUP` | Platform 可 Protect | 缺规则与决定 | 写 `agent-docs/decisions/` 决定；本机 / test 规则作为环境动作单独授权 |
| 迁移现有 `SES_PRIMARY`（Q-08） | test 有该账号（ACTIVE，引用 2 条认证路由），本机 `mor_platform_e2e` 由初始化 DML 写入 | 补区域与发件地址，Token 空 | 无 DML | 初始化 DML 改为带新字段；另给已执行环境的补字段 DML |

## 3. 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 改代码 / 模型 / 配置 | 上下游与兼容风险 |
|---|---|---|---|---|
| Platform `platform-support-api` | 管理接口合同 | `NotificationManagementApi` 加 4 个方法；新请求体 3 个；`NotificationProviderResponseBody` 加字段 | 代码 | BFF 同步；旧字段 `configured` 语义改为"配置完整"，保留字段名 |
| Platform `platform-support-delivery` | 发送账号事实、发送 | DO / Mapper / Repository；`EmailProvider` 合同；`EmailProviders` 注册表；SES 包（Properties、Configuration、Provider、CredentialVault）；管理 Service 新方法；认证邮件与普通通知调用点 | 代码、模型 | 发送中的投递在升级后按新配置发送；`SES_PRIMARY` 未录密钥前发送失败关闭（与 test 现状一致） |
| Platform `platform-support-web` | Processor、Controller、配置 | 4 个新 Processor；Controller / Converter；`application.yml` 删 SES 账号字段 | 代码、配置 | 资源锁沿用 `notification-provider:{code}` |
| Platform `platform-support-test` | 自动化测试 | `MockEmailProvider` / `FallbackEmailProvider` 按编码注册（5 个测试类使用）；`application-mock.yml` 的 `notification.ses.*`；`VaultMockController` 需支持 `NOTIFICATION_PROVIDER_SETUP` Protect；`init/ddl.sql` | 测试代码、配置 | 见 R-01 |
| Platform SQL / PDManer | Schema 与注册数据 | `t_notification_provider` 加列（ALTER）；`changes/` 新文件；初始化 DML 中 `SES_PRIMARY` 行；注册 DML 新块 | 模型、SQL | 已执行环境需 ALTER + 补字段 DML |
| `operations-console-bff` | 页面转发 | `NotificationManagementController` 加 4 个转发；模型与 Client 字段；严格 Jackson 转换 | 代码 | `/notification-admin` 已 metadata-only |
| `operations-console-web` | 页面 | `NotificationProviders.tsx` 增新建、编辑、更换密钥、测试发送弹窗；`notification.ts` 解码；权限按钮 | 代码 | 共享文件（menu、pageCapabilities）只在按钮权限处增量修改 |
| Vault | 密文存储 | 策略数据加 1 条规则；不改代码 | 策略数据（环境动作） | 规则未加时 Protect 返回 403，页面提示"Vault 未授权" |
| Gateway | 入口 | 无代码改动；新路由由注册 DML 下发 | 无 | — |
| 文档 | 权威与台账 | Vault 决定、`VAULT_SERVICE_ARCHITECTURE.md` 例外清单、Platform README 的 SES 段、`CURRENT_IMPLEMENTATION.md` | 文档 | 旧 SES 需求第 6.1 节"Token 在部署配置"被本需求取代，需标注 |

## 4. 风险与难点

| 编号 | 风险 | 说明 | 建议（阶段 3 细化） |
|---|---|---|---|
| R-01 | 测试适配器的注册方式 | 现有 Mock 适配器按 `provider_code` 注册为测试 Bean，覆盖查询、暂停、备用账号等分支；SES 不支持查询，`NotificationDeliveryHandler` 的查询分支只能靠测试适配器覆盖 | 适配器改为声明**服务商类型**（字符串，如 `AMAZON_SES`），账号表存类型；测试模块注册一个测试类型的适配器，生产代码不出现测试值；新建账号时只允许选择已注册的类型 |
| R-02 | 客户端缓存 | 每个账号可能不同区域；`SesV2Client` 建立成本高、需关闭 | 按"区域 + 终端覆盖"缓存，进程关闭时统一关闭；密钥仍逐次 Reveal、按请求覆盖 |
| R-03 | 换密钥的一致性 | Protect 成功、数据库更新失败会产生无人引用的密文 | 先 Protect（事务外），再短事务按版本更新；失败时孤立密文保留（Vault V1 无 Retire，可接受），审计记录失败 |
| R-04 | 并发发送与换密钥 | 发送已 Reveal 旧密钥时被换掉 | 允许：本次用旧密钥；AWS 侧旧密钥应在确认新密钥可发后再停用（运维流程写进 README） |
| R-05 | 测试发送的滥用 | 可向任意邮箱发信 | 需凭据或维护权限、限频（沿用 Gateway 限流）、固定内容、审计只记邮箱掩码 |
| R-06 | 共享工作区 | BFF / Web 有其他会话在改 | 只增量修改共享文件（Q-09） |
| R-07 | Vault 规则依赖 | 规则未加时功能不可用 | 本机验收前需授权添加本机规则；test 在阶段 6 清单列出 |

## 5. 工作量依据

- Platform：约 25 个主代码文件（API 4、Delivery 12、Web 6、配置 3），测试改造 6—8 个类 + 新增 2—3 个测试类；SQL 4 个文件。
- BFF：约 5 个文件；Web：约 4 个文件，弹窗 4 个；Pencil 画板约 4 个。
- 文档：决定 1 份、需求目录 3 份、README / 台账段落。

## 6. 需要确认

1. 缺口判断与"日志不用改"的结论；
2. 受影响系统与处理边界（Vault 只改策略数据，不改代码）；
3. R-01 的方向：适配器按服务商类型注册，测试用独立测试类型。
