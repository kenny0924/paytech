# platform-support：文件多存储并存 · 技术方案（评审稿 v2）

> 状态：阶段 1—3 合并评审稿 v2，待用户确认。需求见 [requirement.md](./requirement.md)。
>
> - 所属系统：第一段为 Platform Support（file-document、core、app、test）、Operations BFF、Operations Web；第二段另涉及 CCT、Client Console BFF。
> - 接口：第一段新增内部文件接口 4 个、存储管理接口约 12 个（含 BFF 转发）；修改 0。
> - 数据库（`mor_platform`）：新增 3 张表，`t_file_upload` / `t_file_object` 各加 2 列，回填 DML 1 份。第二段再删除 Owner 字段和 `t_file_owner_ref`。
> - 本地原子写入：管理写操作只写 `mor_platform`；上传创建、完成登记的短事务不变。事务外步骤：Vault Protect / Reveal、存储连接测试与桶检查、签发授权、读取对象。

## 1. 现状

| 位置 | 现状 |
|---|---|
| 装配 | `MinioStorageConfiguration` / `S3StorageConfiguration` 按 `files.onboarding.storage-provider` 互斥装配 |
| 凭据与桶 | 配置文件 `files.onboarding.minio.*` / `s3.*`、`storage-container-ref` |
| 适配器身份 | `MinioFileStorage.PROVIDER = "MINIO"`、`S3FileStorage.PROVIDER = "S3"`，代码等于类型 |
| 选择适配器 | 6 处各自按 `providerCode()` 过滤：`StartOnboardingUploadProcessor:133,139`、`DownloadOnboardingFileProcessor:81`、`ReviewFileDownloadProcessor:93`、`CompletedUploadVerifier:17`、`FormatFileScanner:70` |
| 业务授权 | F01 经 `CctFileTargetClient` 调 CCT `ClientFileTargetApi` 核对申请和主体；`t_file_upload.owner_*` 与 `t_file_owner_ref` 记录业务归属 |
| 数据 | 每条上传和文件记录已保存 provider + container + object（+ version），唯一键含 provider |
| 先例 | 通知发送账号：`t_notification_provider.credential_vault_token` + `ProtectNotificationCredentialProcessor`（Protect）+ `SesCredentialVault`（每次发送前 Reveal，不缓存） |

**为什么现在 Platform 会调 CCT**：F01 由客户端 BFF 带 CLIENT 会话直接调用 Platform，Platform 作为入口要判断"这个用户能否给这份申请的这个主体传材料"，而申请归 CCT 所有，只能回头问 CCT。这是把业务授权放在文件服务里造成的反向依赖，第二段把授权移回 CCT 后消除。

## 2. 总体结构

```
运营控制台「存储管理」 → Operations BFF → Platform 管理接口
                                          │ 写 mor_platform（实例 / 桶 / 路由）
                                          │ 事务外：Vault Protect、连接测试、桶检查
                                          ▼
                          t_file_storage_instance ─┬─ t_file_storage_bucket
                                                   └─ t_file_storage_route（来源系统 → 桶 + 检查策略）
                                          │ 进程内快照（定时刷新，不含密钥）
                                          ▼
内部服务 ──内部 Token──► 内部文件接口 ──► FileStorageRegistry
                                          ├─ 写：route(source_system) → 实例 + 桶，落库固定
                                          └─ 读：记录上的 provider + container → 实例 + 桶
                                                 每次操作前按需 Vault Reveal → 构建客户端 → 调用 → 释放
```

## 3. 数据设计（`mor_platform`）

### 3.1 新表

**`t_file_storage_instance` 存储实例**

| 字段 | 类型 | 说明 |
|---|---|---|
| `instance_id` | BIGINT PK | |
| `instance_code` | VARCHAR(64) | 唯一；`[A-Z][A-Z0-9_]{0,63}`；即记录上的 `storage_provider`，创建后不可改 |
| `storage_type` | VARCHAR(16) | `MINIO`、`S3` |
| `endpoint` | VARCHAR(255) NULL | HTTPS 根地址；S3 可空（用区域默认地址）；同时是浏览器直传和下载地址 |
| `region` | VARCHAR(64) | |
| `path_style_access` | TINYINT | |
| `trusted_certificate_pem` | TEXT NULL | 私有 CA / 自签名证书（公开内容） |
| `credential_mode` | VARCHAR(16) | `STATIC`、`IAM_ROLE`；MINIO 只允许 `STATIC` |
| `credential_vault_token` | VARCHAR(255) NULL | `STATIC` 必填；`IAM_ROLE` 为空 |
| `credential_update_time` | DATETIME(6) NULL | |
| `connect_timeout_ms` / `read_timeout_ms` | INT | 默认 2000 / 10000，范围同现有校验 |
| `status` | VARCHAR(16) | `DRAFT`、`ACTIVE`、`DISABLED` |
| `last_check_time` / `last_check_result` | DATETIME(6) NULL / VARCHAR(64) NULL | 最近一次连接测试 |
| `version`、`create_time`、`update_time` | | |

**`t_file_storage_bucket` 桶**

| 字段 | 类型 | 说明 |
|---|---|---|
| `bucket_id` | BIGINT PK | |
| `instance_id` | BIGINT | |
| `bucket_name` | VARCHAR(255) | 唯一键 `(instance_id, bucket_name)`；即记录上的 `storage_container_ref`，创建后不可改 |
| `status` | VARCHAR(16) | `DRAFT`、`ACTIVE`、`READ_ONLY`、`DISABLED` |
| `last_check_time` / `last_check_result` | | 最近一次启用检查：`OK`、`NOT_FOUND`、`VERSIONING_DISABLED`、`ENCRYPTION_DISABLED`、`ACCESS_DENIED`、`UNAVAILABLE` |
| `version`、`create_time`、`update_time` | | |

**`t_file_storage_route` 写入路由**

| 字段 | 类型 | 说明 |
|---|---|---|
| `route_id` | BIGINT PK | |
| `source_system` | VARCHAR(64) | 唯一；内部 Token 服务名（`[a-z][a-z0-9-]{0,63}`），或 `*` 表示默认路由 |
| `bucket_id` | BIGINT | 必须是 `ACTIVE` 的桶，所属实例 `ACTIVE` |
| `scan_policy_code` | VARCHAR(64) | 新上传冻结该策略的当前 ACTIVE 修订（沿用现有 `t_file_scan_policy`） |
| `max_upload_seconds` | INT | 60—900 |
| `version`、`create_time`、`update_time` | | |

### 3.2 现有表增量

| 表 | 增加 | 说明 |
|---|---|---|
| `t_file_upload` | `source_system` VARCHAR(64)、`reason_code` VARCHAR(64) | 开始上传时写入，之后不变 |
| `t_file_upload` | `uploader_id`、`tenant_id` 改为可空 | 保留上传人账号：调用方有人工操作人时传入（工作流用它校验"文件由当前提交人上传"）；纯系统上传为空。Platform 不核对其真实性，只原样记录和返回 |
| `t_file_object` | `source_system`、`reason_code`；索引 `idx_file_source (source_system, reason_code, create_time)` | 完成时从上传复制，用于反查 |

第一段新增列先允许 NULL，回填后改为 NOT NULL（同一份 DDL 内按"加列 → 回填 → 改约束"顺序执行）。回填：现有记录全部来自入驻，`source_system='platform-support'`、`reason_code='CLIENT_ONBOARDING_MATERIAL'`（与现有 `purpose_code` 一致；第一段入驻仍由 Platform 自己发起，见 Q9）。

### 3.3 第二段才改的

`t_file_upload.owner_system / owner_type / owner_ref_id` 删除，`t_file_owner_ref` 删除（CCT 已在 `t_application_document` 中保存 `file_id`，业务关联不丢）。第一段保留这些，避免同时改两条链路。

## 4. 接口

### 4.1 内部文件接口（`platform-support-api`，`@HttpExchange`，私网，不经 Gateway）

| 接口 | 说明 |
|---|---|
| `POST /internal/files/v1/uploads` | 入参 `reasonCode`、`displayName`、`mimeType`、`sizeBytes`、可选 `uploaderAccountId`；`source_system` 取调用 Token。按路由选桶和策略，短事务写上传，事务外签发 PUT 地址；返回 `uploadId`、地址、到期时间 |
| `POST /internal/files/v1/uploads/{uploadId}/completion` | 只允许原 `source_system` 完成（防止误完成别的服务的上传，不是读权限）；核对字节、摘要、类型和版本，写文件并登记 `FILE_SCAN` |
| `GET /internal/files/v1/files/{fileId}` | 元数据：大小、类型、摘要、扫描状态、来源系统、`reason_code`、上传人账号（可空）、创建时间 |

Platform 内部模块（工作流）不走 HTTP，直接调用 file-document 的同名 Service 入口：开始上传、完成上传、按文件 ID 查询元数据、签发下载授权，`source_system` 固定为 `platform-support`。
| `POST /internal/files/v1/files/{fileId}/download` | 只要求文件扫描为 `AVAILABLE`、桶可读；返回原版本 60 秒只读地址。任何内部服务都可调用（D3） |

上传幂等沿用现状：重复完成同一 `uploadId` 返回原结果。所有接口要求内部调用身份；`internal-auth.mode=OFF` 的环境下这组接口拒绝服务，避免来源系统为空。

### 4.2 存储管理接口（Platform `/storage-admin/v1`，经 Operations BFF）

实例：列表、详情、新建、编辑、更换密钥、测试连接、启用、停用。桶：列表、登记、检查并启用、设为只读、停用。路由：列表、新建、修改、删除（默认路由不能删）。具体路由数在阶段 3 细化时与 Platform 注册块一起给出（块号开工前按实际占用核对，已知 127—129 被占用）。

- 新建实例和更换密钥：BFF 收到明文后由 Platform 在事务外调用 Vault Protect，拿到 Token 后短事务写入；明文不落库、不进日志和审计。
- 测试连接、桶检查：事务外调用存储，结果短事务回写。
- 全部写操作记 Operations 审计；权限按现有 Operations 资源 / 动作模型新增"存储管理查看 / 维护"两项（见 Q7）。

## 5. 代码改动（第一段）

| 位置 | 改动 |
|---|---|
| file-document `integration/storage` | `MinioFileStorage` / `S3FileStorage` 改为按实例构建：构造参数是实例快照与本次凭据，`providerCode()` 返回实例代码；删除 `*StorageProperties`、`*StorageConfiguration` 与 `PROVIDER` 常量 |
| file-document 新增 | `FileStorageRegistry`：持有实例 / 桶 / 路由快照（定时刷新，默认 30 秒，同币种快照做法）；`forWrite(sourceSystem)`、`forRead(provider, container)` 返回已校验的目标；`StorageCredentialVault` 每次操作前 Reveal（`IAM_ROLE` 跳过） |
| file-document 新增 | 存储管理 Domain Service + Repository + Mapper（3 张表），桶检查器 `BucketReadinessChecker` |
| file-document 改 | `CompletedUploadVerifier`、`FormatFileScanner` 改用注册表；`FileUploadService` 写入 `source_system` / `reason_code` |
| core | 新 Processor：内部上传开始 / 完成、文件查询、下载；存储管理 Processor（单资源写用 `AbstractLockProcessor`）。现有入驻 Processor 只把适配器查找改为注册表，入驻上传的路由来源固定为 `platform-support` |
| app | 内部文件 Controller、存储管理 Controller；删除 `files.onboarding.storage-*`、`minio`、`s3` 配置；旧键残留时启动失败 |
| Operations BFF / Web | 存储管理三个页面（实例、桶、路由）及路由注册 |

客户端按操作构建：MinIO / S3 SDK 客户端构建成本低（预签名在本地完成），每次操作构建、用完关闭，不在进程中保存带密钥的客户端，满足 Vault 不缓存原值的约定。

## 6. 迁移

| 环境 | 做法 |
|---|---|
| 本机 / mor-test | DDL 后通过管理接口登记：实例 `MINIO`（STATIC，现有 MinIO 账号存入 Vault）、桶 `mor-platform-files`（启用检查）、默认路由 → 该桶 + `CLIENT_ONBOARDING_FORMAT`。现有记录 `storage_provider=MINIO` 无需改 |
| mor-test compose | 删除 `FILES_ONBOARDING_STORAGE_*`、`FILES_ONBOARDING_MINIO_*` |
| auto 测试 | `StorageInstanceMocker` 直接插入实例 / 桶 / 路由行；Vault 用现有 `VaultMockController` 返回配置的凭据 DTO；同时登记 MinIO 类型实例和 S3 类型实例（AWS SDK，path-style）指向本机 MinIO 的 `mor-platform-files-test` |
| 文档 | `TECHNOLOGY_STACK.md:50`、`local-environment/README.md:67`、`platform-support/README.md`、`PLATFORM_SUPPORT_DATABASE.md`、PDManer 与 Schema SQL |

回退：旧镜像不读新列和新表，新增列可空期间可直接回退；改为 NOT NULL 后回退需先放宽约束，SQL 文件附回退语句。

## 7. 测试计划（auto，本机 MySQL / Redis / MinIO，Vault 测试端点）

| 测试 | 覆盖 |
|---|---|
| 存储管理 HTTP | AC-01、AC-02、AC-07、AC-08：新建 / 更换密钥 / 测试连接 / 桶检查四种失败 / 状态流转 / 并发版本冲突 / 审计；Platform 表与日志无密钥原值 |
| 内部文件接口 HTTP | AC-03、AC-09：路由选择、默认路由、无身份拒绝、来源系统取自 Token、跨来源完成拒绝、任意来源可下载 |
| 多存储并存 | AC-04、AC-05、AC-06：改路由后读旧实例文件、只读桶、停用桶 / 实例 503 且无存储调用 |
| 迁移 | AC-10：回填后入驻上传、完成、检查、审核下载回归 |
| 启动 | AC-11：旧键残留启动失败 |
| 现有测试 | AC-12：MinIO / S3 适配器（各 11）、`FileScanHandler`（5）、审核下载（17）改用库中配置 |

收尾跑 Platform `clean verify`（90% 指令 / 分支门槛）、Operations BFF 定向测试、Operations Web 测试与构建。本机库由多个会话共用，只跑相关 Reactor。

## 8. 风险

- **工作流需求并行**：已与工作流会话（code-mor-ae）确认：工作流尚未改 file-document，本需求不必等待；工作流不再泛化 owner、不写 `t_file_owner_ref`，文件 ID 保存在自己的 `start_values_json` / `output_json` 中并自行做下载前检查；上传走 Service 入口（`platform-support` / `WORKFLOW_ATTACHMENT`）。工作流依赖本方案提供：按文件 ID 查扫描状态、MIME、大小、来源系统、`reason_code`、上传人账号，以及下载授权 Service 入口。工作流侧方案调整由该会话另行请用户确认。
- **Vault 成为文件操作的依赖**：STATIC 实例每次操作多一次 Reveal，Vault 不可用即 503。生产 AWS 建议 `IAM_ROLE`。
- **"持有 ID 即可读"**：文件 ID 可能被猜测，所以内部接口必须只在私网、必须有内部调用身份，浏览器侧下载必须由 BFF / 业务服务先做业务授权。
- **快照刷新延迟**：停用桶或实例后最长 30 秒内仍可能签发授权（见 Q10）。

## 9. 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| Q6 | 存储管理是否需要复核（Maker ≠ Checker） | **不需要，直接生效 + 审计**，同币种维护；凭据更换也直接生效 |
| Q7 | 权限粒度 | **两项**：存储管理查看、存储管理维护 |
| Q8 | `reason_code` 是否建受控目录 | **不建**，只校验 `[A-Z][A-Z0-9_]{0,63}`；以后需要时再从已有值整理目录 |
| Q9 | 第一段中入驻上传的 `source_system` | **`platform-support`**（第一段入驻仍由 Platform 发起）；第二段改由 CCT 调用后自然变成 `client-master-service` |
| Q10 | 停用后的生效时间 | **快照 30 秒刷新可接受**；停用时同时清本实例快照，其他实例最多 30 秒 |
| Q11 | 是否额外记录业务引用（如 applicationId）用于反查 | **不加**，按 D3 只记来源系统和 `reason_code`；具体业务对象由来源系统用 `file_id` 反查自己的表 |
