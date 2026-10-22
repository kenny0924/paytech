# 阶段 6 卡 F 报告：以 main `73a2460d` 重建 3DS 与 BIN 两项需求的集成代码

工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-rel`（detached at `73a2460d`，改动已暂存到该工作树自己的 index）。未提交、未建分支、未 install 到 `~/.m3`，未连接 mor-test，也未改动本机 e2e。没有执行 SQL；Platform 测试在 team-lead 批准的 `mor_platform` 窗口内运行。

开工前已读：本卡、`00-common.md`、`AGENTS.md`。本卡是变基，没有新增 Processor 或写接口，WRITE_API_EXAMPLE 第 11 节沿用阶段 4/5 已核对的结论。本卡触及的三处衔接只涉及事务外的出站调用，不涉及本地事务。

## 1. 合并方式

- 在 `73a2460d` 上直接 `git apply --3way --index final-int.patch` 时，因预映像对象缺失而整体退回直接应用，并在已删除的 `OnboardingFileBuilder` / `OnboardingFileMocker` 上失败。
- 改用以下方式：先用临时 index 把补丁应用到 `1d39cd94`，得到一个悬空提交 `8551d475`（不是分支），再在 rel 中执行 `git cherry-pick --no-commit 8551d475`，做真正的三方合并，最后执行 `cherry-pick --quit`。
- 结果：相对 `73a2460d` 共 368 个文件，新增 159、删除 1、修改 208。补丁原有 369 个文件；少的一个是 ops-bff `PlatformClientConfiguration`，见 2.2。
- 重命名检测把补丁对 `OnboardingFileBuilder` / `OnboardingFileMocker` 的改动带到了第二段的 `FileBuilder` / `FileMocker`，`FileMocker` 允许名单里的 `text/csv` 自动合入。

## 2. 冲突清单与解决

| 文件 | 解决方式 |
|---|---|
| `platform-support-schema.sql` 首行表数 | 第二段 62（删除 `t_file_owner_ref`）加本需求 `t_country_region`，改为 63；实际 `CREATE TABLE` 计数也是 63 |
| `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs` 的 `EXPECTED_TABLE_TOTAL` | 72 + 1，改为 73 |
| `table-ownership.yaml` 头注释 | 用 `generate-table-ownership.mjs` 重新生成（62 retained / 59 step-added），`--check` 通过 |
| `TableOwnershipAutoTest` | 两边算式合并为 `… + 3 - 1 + 2 + 1 + 4`，`mor_platform` 63 张，说明两边都保留 |
| `FileBuilder` | 两边都保留：第二段的 `REASON_CODE`，加本需求的 `CSV` / `BINARY` / `csvWithLateNul()` |
| `S3FileStorageTest` / `MinioFileStorageTest` / `InternalFileApiTest` | 取本需求的 CSV 用例与 `uploadThroughGrant` 重载，类名 `OnboardingFileBuilder` 改为 `FileBuilder` |
| `FileScanHandlerTest`（自动合并） | 同样改名为 `FileBuilder` |
| `platform-support.pdma.json`（自动合并） | 用 `generate-platform-support-model.mjs` 重新生成（73 表），结果与文本合并不同，以生成器输出为准 |
| Card、ops-bff 中的 `OnboardingFileErrorCode` | 第二段已删除该枚举，改为 `FileErrorCode`，常量名相同 |

## 3. 三处衔接（另含 Card 合同）

1. **Platform 识别 CSV**：`StoredContentType`（开头抽样 8 KiB、无 PDF/PNG/JPEG 文件头、合法 UTF-8 时识别为 `text/csv`）、`Utf8TextInspector` 和 `FormatFileScanner`（全文合法 UTF-8 且不含 NUL）都在第二段代码上原样合入，没有冲突。
   - `CompletedUploadVerifier` 在本补丁里没有改动：完成上传时由存储适配器调用 `StoredContentType.detect` 识别类型。第二段已改为抛 `FileErrorCode.FILE_POLICY_REJECTED`，与要求的错误码类型一致。
   - 下载方式按第二段的 `FileDisposition` 处理：CSV 不在 INLINE 允许范围内，下载时作为附件返回。
2. **ops-bff 的 `X-Caller-Service`**：第二段已有统一的 `internalFileApi` Bean，`platformClient` 对所有 Platform 请求都带 `X-Caller-Service: operations-console-bff`。本需求自加的第二个同名 `internalFileApi` Bean 和 `platformProxy(RestClient, …)` 重载已删除，该文件恢复为与 `73a2460d` 一致，现在只有一套头配置。BIN 上传复用这个客户端。
3. **BFF 自拼文件地址**：`BinLibraryFileClientImpl` 已删除 `/files/v1/` 前缀，改用完成回执中的 `fileAddress`（Platform 为 Owner），并要求该字段不为空，否则按上游无效响应返回 502。
   - 第二段的完成回执只有 `uploadId`、`fileId`、`fileAddress` 和 `completedAt`，没有扫描状态，所以保留 B7 读扫描状态的那次 `getFile`。
   - `CardBinLibraryBrowserAutoTest` 的回执 fixture 加上了 `fileAddress`，并新增一例“回执缺 `fileAddress` 时返回 502”。
4. **Card 调 Platform 内部文件接口**：`PlatformClientConfiguration` 对整个服务组带 `X-Caller-Service: card-service`，internal-auth 非 OFF 时另附内部 Token，与 DUAL 一致。
   - 第二段的 `authorizeDownload` 增加了 `X-Operator-Account-Id` 和 `disposition` 两个可选参数。Card 作为系统读取，改为传 `(fileId, null, null)`：不写人工下载审计，下载方式取 Platform 默认的附件。
   - 测试侧：`MockPlatformFileController` 和 `PlatformMocker` 改为第二段的构造签名（`StoredFileResponseBody` 带 `fileAddress`，`FileDownloadResponseBody` 带 `disposition`）。如果 Card 带了操作人或下载方式，测试记为意外调用并失败。
   - 错误码：`FILE_NOT_FOUND` 返回 null，`FILE_STATE_CONFLICT` 保留给业务判断，与第二段的 `FileErrorCode` 一致。Card 仍从自己保存的文件地址解析 `fileId`，没有比对 `getFile` 返回的 `fileAddress`（见遗留）。

## 4. 偏离与裁决

- **BIN 上传的文件策略拒绝由 400 改为 422。** 第二段的 `OperationsErrorStatusResolver` 把 `FileErrorCode.FILE_POLICY_REJECTED` 显式映射为 422（O10 同样适用）。本需求原测试期望 400，这是全模块唯一的失败。
  - 裁决：沿用第二段 BFF 统一的文件错误码状态，测试改为期望 422。
  - 为保持弹窗顶部显示拒绝原因的体验，ops-web `useBinLibraryCommand.binLibraryErrorMessage` 把 422 按与 400 相同的方式展示。该 hook 依赖浏览器会话，没有补 node 单测。

## 5. 测试（均在 rel 中前台运行，`-Dpaytech.local-environment.dir` 指向主工作区，密码按需导出且未打印）

| 范围 | 命令要点 | 本轮结果 | 上一轮对照 |
|---|---|---|---|
| 全 reactor | `./mvnw -o test-compile` | 成功 | 成功 |
| platform-support-test | `-am package`，只跑 `**/com/paytech/platform/**/auto/**` | 802 例，0 失败 | 831 例：第二段删除了入驻文件测试 |
| card-test | `-am test` | 66 例：64 通过，2 失败 | 同样是 64/66，失败的两例相同，是基线既有失败（见下） |
| operations-console-bff-test | `-am test` | 2233 例：全部通过（422 那一例修正后，单类复跑 28/28） | 2154 例：第二段新增了用例 |
| payment-test | `-am test` | 96 例，0 失败 | 85 例（3DS 阶段 4）加上 BIN 新增 |
| channel-connector-test | `-am test` | 395 例，0 失败 | 390 例：CYBS 新增 |
| acquiring-test | `-am test` | 806 例，0 失败 | 805 例 |
| risk-decision-test | `-am test` | 1738 例，0 失败 | — |
| counterparty-test | `-am test` | 22 例，0 失败 | — |
| commercial-test | `-am test` | 196 例，0 失败 | — |
| api-gateway-test | `-am test`，导出 `PAYTECH_REDIS_PASSWORD` | 1313 例，0 失败 | — |
| client-master-test | `-am package` | 78 例，0 失败 | —（第二段改过这个模块，确认没有被本需求破坏） |
| tools/arch-rules | `-am test` | 68 例，0 失败 | 68 例 |
| foundation/paytech-messaging | `-am test` | 73 例，0 失败 | — |
| PDManer | `node --test scripts/pdmaner/*.test.mjs` | 591 例：544 通过，47 失败 | int 目录同样 591/544/47，失败用例集合完全相同 |
| ops-web | `npm test` / `tsc --noEmit` / `npm run build` | 1338 例全部通过 / 0 错误 / 通过（只有既有的 chunk 体积告警） | 1338 例 |

**基线既有失败（未改）**
- Card 两例：`CardMaterialTicketTest.test_securityCodeClaimedRefused`、`StoredCardHttpTest.test_payWithStoredCard`。读卡调用方已改为 `channel-connector-service`，这两个旧用例没有随之更新（A-card 报告已记录）。
- PDManer 47 例：例如 Payment 场景里的 AUTHENTICATE 正则和 `adapter_code`，Platform 旧 schema 测试中的 `t_api_credential.credential_name`。

**运行方式说明**
- client-master 和 platform 用 `package`。在 reactor 里只跑 `test` 时，paytech-messaging 的 test-jar 会解析为 `target/test-classes`，把 messaging 自己的 `application-mock.yaml` 带进 client-master 上下文，导致 `client_master_app` 执行 DROP 被拒。`package` 打出只含 testkit 的 jar 后，该问题消失。这是运行方式的问题，不是代码回归。
- Gateway 需要导出 `PAYTECH_REDIS_PASSWORD`。

## 6. 遗留

- Card 没有用 `getFile` 返回的 `fileAddress` 与自己保存的地址做一致性比对（合同允许，目前只按 `fileId` 读取）。如需要，可作为后续加固项。
- ops-web 对 422 的展示调整没有补 node 单测。
- 临时悬空提交 `8551d475` 会由 git gc 回收；rel 中的 `node_modules` 软链接已删除，`dist/` 已在 gitignore 中。

## 7. 追加：按文件地址原则 P2 改用 `FileAddress`（main `5e72174b` 的 `2026-10-22-file-upload-address-principles.md`）

改动仍在 rel 工作树中暂存，未提交。

1. **Card 解析地址**：
   - `CardBinLibraryConverter.convertFileId` 改为 `convertFileAddress`，用 `FileAddress.parse` 解析，解析失败抛 `InvalidParameterException`（400）。
   - Controller 把规范化后的 `fileAddress.value()` 和 `fileId()` 传给 Processor。
   - 删除 `CardBinLibraryVersion.FILE_ADDRESS_PREFIX`，`fileId()` 改用 `FileAddress.parse`。
   - `card-api` 是轻量合同，不依赖 platform-support-api，所以 `CreateBinLibraryVersionRequestBody` 保留 `@Pattern` 格式校验（422），并在注释中写明解析由 card 入口用 `FileAddress` 完成。
2. **核对 Platform 登记的地址（关闭第 6 节的遗留项）**：
   - `BinLibrarySourceFile` 增加 `fileAddress` 字段（取自 `getFile` 返回值）和 `isAddressedBy(FileAddress)`：按 `FileAddress` 比较，Platform 返回的地址缺失或格式不符都视为不一致。
   - `CreateBinLibraryVersionProcessor.getAndCheckFile` 在文件存在性检查之后核对地址，不一致时抛 `BizCheckFailException("导入文件地址与文件服务登记不一致: " + fileId)`，返回 400，消息只带文件 ID。
3. **ops-bff**：主代码中已没有手工拼接的 `/files/v1/`。`BinLibraryFileClientImpl` 改为用 `FileAddress.parse` 核对完成回执中的地址指向同一 `fileId`，缺失、格式不符或指向其他文件都按上游无效响应返回 502。
4. **测试**：
   - Card 测试侧：`PlatformMocker`、`BinLibraryMocker`、`AbstractBinLibraryTest`、`BinLibraryVersionHttpTest` 都改为用 `FileAddress.of` 构造地址。
   - 新增 Card 断言：Platform 登记地址与请求不一致返回 400（文件 105），地址格式通过但文件 ID 超出 long 范围返回 400。
   - ops-bff：`CardBinLibraryBrowserAutoTest` 的 fixture 改用 `FileAddress`，并新增“回执地址指向其他文件返回 502”。
   - 地址格式非法的输入在测试中仍用字面量表示。
5. **未改**：ops-web `binLibrary.ts` 仍在前端核对 `fileAddress === /files/v1/${fileId}`。前端无法使用 Java 的 `FileAddress`，该核对只做一致性检查、不拼接后提交，由 ops-web 后续决定是否调整。

| 范围 | 结果 |
|---|---|
| 全 reactor `test-compile` | 成功 |
| card-test 整模块 | 66 例：64 通过，2 失败。失败的两例与本轮前面以及 A-card 报告中的基线失败相同（`CardMaterialTicketTest.test_securityCodeClaimedRefused`、`StoredCardHttpTest.test_payWithStoredCard`）；新断言所在用例和 `ArchitectureAutoTest` 都通过 |
| operations-console-bff-test 整模块 | 2233 例，0 失败 |
