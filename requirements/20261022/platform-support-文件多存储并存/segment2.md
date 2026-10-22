# platform-support：文件多存储并存 · 第二段评审稿 v2（文件上传、下载走 BFF，业务只存地址）

> 状态：阶段 1—3 评审稿 v2，待确认。第一段已提交 `346a1822`、已上 mor-test。依据 [requirement.md](./requirement.md) D3、D4 与本稿新增的原则 P1—P3（取代 R9—R11 的链路描述）。现状调查基于 main `a5479950`。
>
> v1 → v2：v1 让 CCT 转发文件调用；用户指出调用应放在 BFF，并给出三条原则。v2 中 CCT 不再接触文件上传、下载，只在提交表单时核对一次文件。

## 1. 原则（用户 2026-10-22）

| # | 原则 |
|---|---|
| P1 | 上传与表单提交分开：每次上传都是 Gateway → BFF → 文件上传接口，拿到文件地址、ID 等信息；提交表单时只提交地址，不在提交时上传 |
| P2 | 业务数据中存文件地址，不存文件 ID，除非非常有必要 |
| P3 | 下载不经过业务服务：Gateway → BFF → 文件服务下载接口 |

已确认的两点：
- **文件地址：** 由文件服务签发的稳定逻辑地址 `/files/v1/{fileId}`。不是预签名链接，不含实例和桶，存储迁移后不变；只能经文件服务下载接口换取临时链接。
- **存量：** CCT 改存地址（哈希继续冻结，证据完整性需要）；compliance 内部字段本次不改。

## 2. 现状要拆掉的东西

同一套业务归属数据（`t_file_upload.owner_*` + `t_file_owner_ref`）与 Platform 反调 CCT 的 `CctFileTargetClient`，支撑 6 个入口：F01 开始上传、F02 完成上传、F03 查文件、F04 客户下载（都经 client-console-bff 调 Platform `OnboardingFileApi`），CCT 关联 / 提交前的哈希核对（`/internal/onboarding-files`），O10 运营审核下载（`ReviewFileApi`）。本段全部迁移后删除。

另一前提：内部认证在所有环境都是 OFF，第一段的内部文件接口在 OFF 下一律 401。

## 3. 目标链路

| 动作 | 链路 | 判断 |
|---|---|---|
| 上传 | 浏览器 → Gateway → client-console-bff → Platform 内部文件接口：开始 → 浏览器直传对象存储 → 完成 → 轮询扫描状态；返回 `fileAddress`、`fileId`、状态、哈希 | 只要求客户已登录；不带申请、主体等业务参数。来源 `client-console-bff`，用途 `CLIENT_ONBOARDING_MATERIAL`，上传人 = 当前账号 |
| 关联材料（C12）、提交申请（C14） | 浏览器 → BFF → CCT，只提交 `fileAddress` | CCT 调 Platform `getFile`：扫描 `AVAILABLE`、对象 `ACTIVE`、64 位哈希，且**上传人 = 本人**（防止挂别人的文件）；存地址 + 冻结哈希 |
| 客户下载 | 浏览器 → Gateway → client-console-bff → Platform 下载接口 | BFF 核对该地址在本人可读申请的材料中（向 CCT 查申请详情），或上传人 = 本人（关联前） |
| 运营审核下载（O10） | ops-web → ops-bff → Platform 下载接口 | ops-bff 核对 Risk 审核任务证据含该文件（现已如此） |

文件始终不经过 CCT；CCT 只在提交表单时读一次文件信息。

## 4. 各系统改动

### 4.1 Platform（文件服务）

- **地址：** platform-support-api 新增 `FileAddress` 值类型（格式化 / 解析 `/files/v1/{fileId}`），调用方不自行拼接；内部文件接口的响应增加 `fileAddress`。内部接口路径仍按 ID 寻址，调用方用 `FileAddress` 转换。
- **调用身份：** 改为 `internal-auth.mode=DUAL`，调用方带 `X-Caller-Service`。DUAL 在“既无 Token 也无该头”时照常放行且不带身份，现有内部入口不受影响；与现有内部入口同等安全级别（私网限制）。正式 Token 签发给其他服务另立需求。
- **人工下载审计：** 下载接口接受可选 `X-Operator-Account-Id`，有值时记审计（操作人、文件、调用服务），替代现 O10 的 `OPERATIONS_REVIEW_FILE_DOWNLOAD`。
- **删除：** `OnboardingFileApi`（F01—F04）及 DTO / 枚举、Controller、配置、6 个 Processor、`ClientOnboardingAccessService`、`OnboardingFileLocks`；`OnboardingFileVerificationApi` 链路；`ReviewFileApi` 链路；`CctFileTargetClient` 与测试 Mock；`files.onboarding.*` 中只服务于这些入口的配置、日志前缀与请求过滤。保留 `FILE_SCAN` 与扫描策略。
- **下载方式与原文件名（2026-10-22 追加，工作流会话提出，用户确认）：** 下载签发一律带原文件名（`filename` ASCII 回退 + `filename*=UTF-8''…`）；新增 `FileDisposition { INLINE, ATTACHMENT }`，进程内与 HTTP 下载入口可选传入，缺省 ATTACHMENT、未知值 400；INLINE 只允许扫描通过且登记 MIME 为 pdf / png / jpeg 的文件，并带 `response-content-type`，否则静默降为 ATTACHMENT；下载响应返回实际生效的 `disposition`。
- **数据：** 删 `t_file_upload.owner_system / owner_type / owner_ref_id` 三列与 `t_file_owner_ref`；Mapper、DataObject、PDManer、arch-rules、测试清理器同步。

### 4.2 CCT

- C12 关联请求的 `fileId` 改为 `fileAddress`；C12 / C14 的核对改调 Platform `getFile`，增加“上传人 = 本人”，删除原 `checkedAt ±30 秒` 规则。
- `t_application_document.file_id` 改为 `file_address VARCHAR(255)`，三个唯一键随之改列；存量按 `CONCAT('/files/v1/', file_id)` 回填。
- 申请详情响应中的材料返回 `fileAddress`（供 BFF 核对下载权限、Web 展示）。
- 发给 compliance 的审核登记**合同不变**：仍发 `fileId` + `contentHash`，由 CCT 用 `FileAddress` 从地址解析出 ID。compliance 与 Risk 证据 ID 不动（后续另立需求）。
- 删除 `ClientFileTargetApi` / Controller / Service（只服务 Platform 反调）与 `OnboardingFileVerificationApi` 客户端。

### 4.3 client-console-bff 与 client-console-web

- BFF 的入驻文件路由（Gateway 路径 `/api/client/onboarding-files/v1/**` 与路由**不变**）改调 Platform 内部文件接口：
  - 开始上传只收文件名、类型、大小；
  - 查文件、下载按第 3 节核对。
- 申请详情页的材料展示改用 CCT 返回的地址，再经 BFF 查 Platform。
- Web：
  - 开始上传不再传申请和主体；
  - 关联材料提交 `fileAddress`；
  - 其余流程（直传 → 完成 → 轮询 → 关联）不变。

### 4.4 operations-console-bff

O10 保留 Risk 证据核对。Risk 返回的证据 ID 经 `FileAddress` 转换后，调 Platform `getFile`（取文件名、类型）与下载接口，并带 `X-Operator-Account-Id`。响应保持不变，ops-web 不改。

## 5. 数据迁移（mor-test 现有 55 条）

- Platform 历史文件的 `source_system` 保持 `platform-support`；新上传为 `client-console-bff`。
- CCT `t_application_document` 回填地址后再删 `file_id`。同一份 DDL 拆为两步：加列回填，换新代码后再删旧列。
- Platform 删 Owner 列 / 表放在新代码全部上线之后，先备份。
- 回退语句写在各 DDL 文件头。

## 6. 测试

| 范围 | 内容 |
|---|---|
| Platform | 删除对应测试；补地址格式化 / 解析、DUAL 不影响现有内部入口、带操作人头的审计 |
| CCT | C12 / C14 从 HTTP 入口覆盖：地址格式错误、文件未扫描完、哈希、上传人不是本人、Platform 不可用；Platform 用测试 HTTP 端点返回配置的 DTO |
| client-console-bff | 上传四步、查文件、下载的授权矩阵（本人上传、已关联可读、他人文件拒绝）；Platform 与 CCT 均为测试端点 |
| ops-bff | O10 两次调用组合与失败分支 |
| Web | 上传与关联参数变化 |
| 本机端到端 | 客户上传 → 扫描 → 关联 → 提交 → 运营审核下载；顺带修好第一段遗留的本机 e2e 旧存储键 |
| 需求站 | PLT-I04 与全局时序 `client_console_bff → platform` 改写 |

## 7. 分工（并行）

| 卡 | 范围 |
|---|---|
| A Platform | 4.1 全部（先交付 `FileAddress` 与响应字段，供 B、C 使用） |
| B CCT | 4.2 全部，含 DDL 与 PDManer |
| C BFF 与 Web | 4.3、4.4、需求站与时序 |

合同先行：A 先定 `FileAddress` 与 `fileAddress` 字段，B 先定 C12 请求与申请详情中的 `fileAddress`；C 拿到两份合同后接入。

## 8. 发布顺序（mor-test，另行授权）

1. Platform 新镜像：DUAL、`fileAddress`、审计头；旧入口暂不删。
2. CCT 加列回填 → 换 CCT、client-console-bff、ops-bff、client-console-web。
3. 新链路验收后：Platform 删旧入口的镜像；最后执行 CCT 删 `file_id`、Platform 删 Owner 列 / 表。

任一步可按上一步镜像回滚；删列 / 删表前均有备份。

## 9. 待确认

| # | 问题 | 建议 |
|---|---|---|
| Q12 | 上传时是否需要业务授权 | **不需要**，只要求登录（P1）；滥用由文件大小、类型、扫描策略与 Gateway 限流兜住 |
| Q13 | 内部调用身份 | Platform 改 DUAL + `X-Caller-Service` |
| Q14 | 人工下载审计 | 下载接口可选 `X-Operator-Account-Id`，有值时 Platform 记审计 |
| Q15 | compliance 审核登记合同 | **本次不改**（仍发 ID + 哈希，CCT 从地址解析）；上一轮回复里说改为“地址 + 哈希”，那需要同时改 compliance 接口，与“compliance 本次不动”冲突，故收回 |
| Q16 | Platform 分两次替换 | **分两次**，删列 / 删表在最后 |
| Q17 | 浏览器路径与 Gateway 路由 | **不变**，不新增注册块 |
