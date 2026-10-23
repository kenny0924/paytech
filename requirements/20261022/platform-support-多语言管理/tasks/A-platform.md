# 任务卡 A：Platform 多语言模块（后端核心）

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n`（detached worktree，基于 main `22ede435`）。**只在这个目录改文件**，不要动主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`。卡 E 同时在本目录改 `agent-docs/` 与 `AGENTS.md`，你不要改这些文件。
- 授权：普通文件编辑、编译、运行本卡涉及模块的测试。**不提交、不推送、不执行任何 SQL、不启动或修改 local-environment 容器。**
- 权威（主工作区，只读）：需求 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/requirement.md`，技术方案同目录 `technique.md`（v3）。冲突以 technique.md 为准；仍有疑问写进报告，不要自行发明第三种方案。

## 开工前必读（实际读取，不凭记忆）

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（完成前按第 11 节逐项自查）、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md` 第 5、11 节、`agent-docs/COMPENSATION_EVENT_GUIDE.md`、`agent-docs/decisions/2026-10-22-file-upload-address-principles.md`。参考最近的同类模块：`paytech/platform-support/paytech-platform-support-data-query`（模块骨架、pom、包结构、Repository / Mapper / Processor / Controller、测试写法）与 `paytech-platform-support-workflow`（补偿事件、审计）。

## 外部约束（来自其他会话，必须遵守）

1. **不要改** `paytech/platform-support/docs/database/platform-support.pdma.json`：main 上该文件已损坏，code-mor-6b 正在修复；也**不要运行** `scripts/pdmaner/*` 生成器。PDManer 改动留到集成卡，本卡在报告里给出 7 张表的字段清单即可。
2. **不要改** file-document 的 `StoredContentType`、`FormatFileScanner`、`FileScanHandler`：文件公开桶会话正在改。`application/json` 检测、公开桶上传、`publicUrl` 都在其合并后的集成卡完成。

## 范围

1. **新模块** `paytech/platform-support/paytech-platform-support-localization`，包根 `com.paytech.platform.support.localization`，结构与 data-query 一致（core/enums、core/dal、domain、domainservice、integration）。加入 `paytech/platform-support/pom.xml` 与 app 模块依赖。合同放 `paytech-platform-support-api` 新包 `localization`；Processor、Controller 放 `paytech-platform-support-app`。
2. **数据库**（technique 3.2，7 张表）：`platform-support-schema.sql` 追加；新增 `docs/database/sql/changes/2026-10-23-localization.sql`（建表）与回滚注释；测试 DDL `paytech-platform-support-test/src/test/resources/init/ddl.sql` 同步。完整中文表 / 列注释，无 CHECK / FK，`DATETIME(6)`，按 DATABASE_DESIGN_GUIDE。DataObject、Mapper（XML 放规定位置）、Repository（interface + Impl）。
3. **领域能力**：
   - Locale、应用、命名空间的增改（删命名空间仅限其下无任何 key），全部带 `expectedVersion` CAS 与 `AbstractLockProcessor` 单资源锁（锁应用编码；Locale 锁 locale 编码）。
   - **ICU 校验器**（technique 3.4）：BOM 加入 `com.ibm.icu:icu4j`（选本机 `~/.m3` 或已配置 Nexus 可解析的最新稳定版，报告写明版本与来源），只用 `MessagePattern`。规则：可解析；参数名集合与基准一致（`#` 不算参数）；plural / select / selectordinal 必须有 `other`。错误带字符位置与中文原因。
   - **key 导入**（technique 3.5）：读取已上传文件内容（通过 file-document 进程内服务按 fileId 读取；文件须为本模块的原因码 `I18N_KEY_IMPORT`），解析 `code-mor-i18n-source/1` 格式；预览返回四类差异 + 未创建命名空间 + key 前缀不符问题 + `previewDigest`；确认时复核 digest 与命名空间，一个事务内落库，`key_set_revision`、`content_revision` 各 + 1。
   - **翻译**：工作台查询（分页、筛选：命名空间、目标语言、未翻译 / 基准已变更 / 已翻译、关键字）；批量保存（≤200 条、全成功或全失败、逐条 `expectedVersion`、ICU 校验）；导出 JSON；导入 JSON（原因码 `I18N_TRANSLATION_IMPORT`，全量校验后整体写入）。每次写入 `content_revision` + 1。
   - **发布**（technique 3.3）：预检（只读）；发布三步。第 1 步语言包生成与上传通过一个端口接口 `ReleaseBundleStore`（`store(application, releaseNo, locale, namespace, bytes, sha256) → fileAddress`）完成，本卡**只定义接口与生成逻辑**，实现类留给集成卡；第 2 步短事务登记（核对 `content_revision`，不符 409 `I18N_CONTENT_CHANGED`）并登记补偿事件 `I18N_RELEASE_ACTIVATE`；第 3 步补偿 Handler：读取各语言包文件检查状态（通过 file-document 进程内查询），全部可用则 READY 并在当前发布号更小时切换指针，有拒绝则 FAILED。补偿事件登记到 `compensation-event-catalog.yaml`。切换当前发布（回滚）写审计 `I18N_RELEASE_ACTIVATE`，发布写 `I18N_RELEASE_PUBLISH`。发布历史、详情、两次发布差异（读两次发布的语言包内容；通过一个读取端口 `ReleaseBundleReader`，同样留实现给集成卡）。
   - **交付 manifest**（technique 3.7）：按应用编码 + locale + 可选 releaseNo 返回；公开地址通过端口 `ReleaseBundleStore#publicUrl(fileAddress)` 取得（同上留实现），进程内按发布号缓存（用 Foundation `paytech-caffeine-starter`）。
4. **接口**：technique 3.8 共 24 个，路径前缀 `/internal/localization/v1`，`@HttpExchange` 合同 + Controller + Processor；错误码按 technique（`I18N_COVERAGE_INSUFFICIENT` 422、`I18N_IMPORT_PREVIEW_STALE` 409、`I18N_CONTENT_CHANGED` 409、`I18N_NAMESPACE_NOT_FOUND` 422），只在这几处定义 `CodeMessageEnum`，其余用公共异常。写接口带 `X-Operator-Account-Id`（参照 data-query 的做法）。**先完成 api 模块合同并在报告中单列，主会话会把它转给 BFF / 前端卡。**
5. **测试**：
   - 纯 Java（本模块 `src/test/java/.../auto/...`）：ICU 校验矩阵、导入差异计算、语言包生成（排序、字节、SHA-256 稳定）、覆盖率计算、两次发布差异。
   - Platform HTTP 入口测试（`paytech-platform-support-test`，按 UNIT_TEST_GUIDE 的 Builder / Mocker / Checker）：Locale / 应用 / 命名空间、导入预览与确认（含过期 digest、未创建命名空间）、翻译保存与 ICU 拒绝、导出再导入无差异、发布预检、覆盖率不足拒绝。发布第 1 步与上线需要端口实现，**本卡只写到"端口用测试实现无法替代生产链路"为止**：不要为它写测试替身绕过生产链路，在报告里列出留给集成卡的测试场景。
   - HTTP 入口测试需要本地库建表，本卡**只编译不运行**这些测试；纯 Java 测试必须运行通过。
6. 编码规范：中文 JavaDoc、方法 ≤30 行、嵌套 ≤3 层、构造器注入、Context / DTO 不用 record、事务内不调用外部服务。

## 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-i18n/paytech
./mvnw -q -pl platform-support/paytech-platform-support-localization -am install -DskipTests
./mvnw -pl platform-support/paytech-platform-support-localization test
./mvnw -q -pl platform-support -amd test-compile -DskipTests
```

不要运行整个 reactor 测试，不要运行 Platform HTTP 入口测试。

## 交付报告（中文，写到 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/tasks/A-report.md`）

改动文件清单；api 合同摘要（24 个接口的方法、路径、请求 / 响应类名与字段）；7 张表字段清单（供 PDManer）；ICU4J 版本与来源；三个端口接口签名与留给集成卡的工作和测试场景；测试命令与实际输出；需要主会话裁决的问题。**完成后直接结束，不要等待后台事件。**
