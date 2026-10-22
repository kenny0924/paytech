# 任务卡 S2-B：CCT（client-master）

先读 [S2-common.md](./S2-common.md)。worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-fs2-cct`。

## 范围

1. **先交付合同（完成后立即回复主会话路径）：** C12 请求与申请详情响应中的 `fileId` → `fileAddress`（S2-common 1.3）。
2. **`FileAddress` 类：** S2-A 会在 `Code-MoR-fs2-platform` 的 `paytech-platform-support-api` 中写出它。主会话通知你后，把该文件**原样复制**到你的 worktree 同一路径，不得修改。在此之前可以先按 1.1 的签名编码。
3. **核对改调 getFile：**
   - `ClientMaterialAuthorityClient`（C12 关联、C14 提交前）改调 Platform `InternalFileApi.getFile`。客户端配置照 CCT 现有对 Platform 的客户端方式，带 `X-Caller-Service: client-master-service`。
   - 规则：
     - 地址先用 `FileAddress.parse` 校验；
     - `scanStatus=AVAILABLE`、`objectStatus=ACTIVE`；
     - 64 位十六进制哈希；
     - **`uploaderAccountId` = 当前账号**。
   - 删除原 `checkedAt ±30 秒` 规则。
   - Platform 不可用或返回 4xx / 5xx 时的错误映射，沿用现有的“材料暂不可核对”类错误。
   - 删除对 `OnboardingFileVerificationApi` 的引用及其测试 Mock（`MockPlatformFileVerificationApi`）。
4. **存地址：**
   - `t_application_document.file_id` 改为 `file_address VARCHAR(255)`，三个唯一键随之改列。
   - DataObject、Mapper、Repository、Service、Processor 同步；冻结哈希不变。
   - 发给 compliance 的审核登记（`ClientReviewRegistrationClientImpl`）**合同不变**：仍发 `fileId` + `contentHash`，`fileId` 由 `FileAddress.parse(address).fileId()` 得到。
5. **DDL**（`paytech/client-master/docs/database/sql/changes/2026-10-2x-application-document-file-address.sql`），两步写在一个文件里，用注释分隔：
   - **第 1 步（授权在本机 `mor_client_master` 执行）：**
     - 加 `file_address` 可空；
     - `UPDATE … SET file_address = CONCAT('/files/v1/', file_id)`；
     - `file_id` 放宽为可空；
     - 新增三个以 `file_address` 替代 `file_id` 的唯一键（新名，如 `uk_app_doc_client_addr`），旧唯一键暂留。
     - 执行前备份该表到 `~/Code-MoR-backups/`，执行后核对行数与回填。
   - **第 2 步（只写不执行）：** 删旧唯一键与 `file_id`，`file_address` 改 NOT NULL，新唯一键改回原名。
   - Schema DDL、PDManer 模型及测试按最终形态（第 2 步之后）更新。
   - 新代码必须能在第 1 步之后的库上运行。
6. **删除**：
   - `ClientFileTargetApi` / Controller / `ClientFileTargetService(Impl)` / `ClientFileTarget*` 模型及其配置（只服务 Platform 反调）。
   - 如果规则 Service 还有其他使用者，先报告主会话。
7. **申请详情：** 材料返回 `fileAddress`。

## 测试

- 从 HTTP 入口覆盖 C12 / C14，Platform `getFile` 用测试 HTTP 端点返回配置的 DTO：
  - 地址格式错误；
  - 扫描未完成；
  - 对象非 ACTIVE；
  - 哈希不合法；
  - 上传人不是本人；
  - Platform 503；
  - 正常关联后申请详情返回地址；
  - 提交时审核登记仍发 ID + 哈希。
- 只跑 client-master 模块（`-pl … -am`），不 install。
