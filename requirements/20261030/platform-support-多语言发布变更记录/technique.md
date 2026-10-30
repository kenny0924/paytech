# platform-support：多语言发布变更记录 · 技术方案

状态：阶段 2 已确认（2026-10-30，D4：只做变更明细，不加修订号列）；第 4—8 节为阶段 3 评审稿。

## 1. 现行实现（代码与 mor-test 证据）

### 1.1 发布内容的口径

`ReleaseContent.current` 与 `BundleGenerator.generate` 同一口径，发布内容是「语言 → key 编码 → 文案」：

- `en`：所有 ACTIVE key 的 `source_text`。
- 其他语言：只取 **ENABLED 语言**中、ACTIVE key 的**已填写**译文（含原文已变更的旧译文）；未填写不出现；空文本不写入（导入时跳过）。
- key 编码在应用内唯一（含命名空间前缀，如 `workflow.actions.add`）；命名空间只决定分到哪个语言包文件，不影响比较。
- 比较（`ReleaseDiff.between`）逐条按**文字是否相等**：基准没有 → 新增；当前没有 → 删除；文字不同 → 修改。改了又改回原文，不算变化。

### 1.2 三个慢接口

| 接口 | 现行做法 | mor-test 实测 |
|---|---|---|
| 预检 `GET …/release-precheck` | `assess`（读 ACTIVE key + 译文，算覆盖率并逐条做 ICU 校验）+ `ReleaseChangeSummaries.summarize`（再读一遍 key + 译文，并逐个读线上发布的语言包文件） | 5.6—8 s；一次预检 Vault REVEAL 52 次，串行约 75 ms / 包，合计约 3.8 s |
| 发布 `POST …/releases` | 同样先 `summarize` 写进发布报告，再生成并上传新语言包 | 同上的读包成本 |
| 版本差异 `GET …/releases/{no}/diff[?baseReleaseNo=]` | 读目标发布与基准发布的全部语言包后 `ReleaseDiff.between`；发布记录页「切回此版本」确认弹窗以当前线上版本为基准调用 | 6—9 s，出现过 Gateway 504 |

读包成本：`FileDocumentReleaseBundleReader` → `FileStorageRegistry.openForRead` 每次都向 Vault 解密存储凭据并新建 MinIO 客户端，没有复用。

数据库读取本身不慢：ACTIVE key（10,688 行）热缓存 8 ms，译文联表约 0.4 s（冷）。应用详情接口（只做 `assess`）约 1.5 s，剩余耗时主要在逐条 ICU 校验，不在本期范围。

### 1.3 写入口（会改变发布内容的操作）

| 操作 | 代码入口 | 推进的修订号 | 对发布内容的影响 |
|---|---|---|---|
| key 导入确认：新增 / 原文变化 / 恢复 / 废弃（说明变化不影响内容） | `ConfirmKeyImportProcessor` → `KeyImportServiceImpl.importKeys`（`insertAll`、`updateImported`） | `key_set_revision` 与 `content_revision` 各 +1 | en 与各语言的 key 集合、en 文案 |
| 保存翻译 | `SaveTranslationsProcessor` → `TranslationServiceImpl`（`insert` / `update`） | `content_revision` +1 | 某语言文案 |
| 导入翻译 | `ImportTranslationsProcessor` → 同上 | `content_revision` +1 | 某语言文案 |
| 语言启用 / 停用 | `UpdateLocaleProcessor`（全局 `t_i18n_locale`，不属于应用） | **不推进任何应用的修订号** | 整个语言加入或移出**所有应用**的发布内容 |
| 命名空间删除 | `DeleteNamespaceProcessor` | — | 无：命名空间下仍有 key（含废弃）时拒绝删除 |
| 发布 / 切换当前发布 | `PublishReleaseProcessor`、`ActivateReleaseProcessor` | — | 不改文案 |

### 1.4 mor-test 数据

| 应用 | 发布数 | 线上 | 每版语言包 | 有效 key | 译文 |
|---|---|---|---|---|---|
| operations-console | 23（全部 READY） | 第 23 版（修订号 64） | 52 个，共 1.43 MB | 10,688 | 约 1.07 万（zh-CN） |
| client-console | 8（全部 READY） | 第 8 版（修订号 16） | 20 个 | 1,477 | — |

语言：`en`、`zh-CN` 均 ENABLED。operations-console 第 1、2 版修订号相同（都是 2）。

## 2. 差异与能力缺口

### 2.1 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| AC-01/02 预检不读文件、≤ 2 s | 读 52 个包，5.6—8 s（1.2 节） | 只查库 | 库里没有「线上版本的文案」 | 以变更明细还原线上内容（2.4） |
| AC-03 摘要与现行一致 | 按文字比较（1.1） | 同口径 | 修订号只能说明「动过」，不能判断「改回原样」；语言启用 / 停用不推进修订号；key 废弃后恢复 | 最终判断必须拿线上文字比较（2.3） |
| AC-04 发布不读线上包 | 发布前 `summarize` 读包 | 只查库 | 同 AC-01 | 同上 |
| AC-05 发布生成变更明细 | 无此数据 | 每版只记变化，含改前、改后 | 无表 | 新表，在发布登记的同一本地事务写入 |
| AC-06 差异（含任意基准、切回确认） | 读两个版本的全部包 | 只查库 | 无法从库内得到任意版本内容 | 变更明细还原任意版本（2.4） |
| AC-07 已有 31 次发布 | — | 一次性补全 | 历史内容只在语言包文件中 | 人工脚本按发布号顺序读一次文件生成明细（D3） |
| AC-08 合同不变 | BFF、两个前端直接透传 | 不改 | 无 | 只改 Platform |
| AC-09 不按全量复制 | — | 行数 = 改动条数 | — | 仅首个基线是全量，之后每版只写差异（2.4） |

### 2.2 逐系统影响

| 系统 | 业务职责 | 影响点 | 改代码 / 模型 / 配置 | 上下游与兼容风险 |
|---|---|---|---|---|
| Platform Support · 多语言模块 | 文案、发布事实 Owner | 新表（变更明细）；发布登记事务内写明细；预检、发布摘要、差异改为库内还原后比较；删除运行期读包路径；（是否加修订号列见 2.4） | 改代码、改模型（PDManer、DDL）、无配置变化 | 发布事务多写一批行（基线后通常几十行）；语言启用 / 停用要由比较逻辑覆盖 |
| Platform Support · 文件服务 | 语言包文件存储 | 运行期不再被多语言读取；仅一次性脚本读取 | 不改 | 无 |
| Operations BFF | 透传 | 无 | 不改 | 响应字段不变 |
| ops-web / client-console-web | 发布弹窗、发布记录页 | 无 | 不改 | 只是变快 |
| Gateway / Platform 注册 | 路由 | 无新增接口 | 不改 | 无 |
| 环境 | mor-test、本机 e2e（`mor_platform_v2e2e`）、本机 auto 库 | DDL + 一次性脚本 | 配置不变 | 部署顺序：DDL → 新 Platform → 脚本补历史；新 Platform 先于脚本运行期间，没有明细的版本需要兜底（阶段 3 定） |

### 2.3 修订号方案（原方案一）的局限

原方案一按「行的修订号 > 线上修订号 R」分类，阶段 2 核对后发现它**单独使用不能满足 AC-03**：

1. **改了又改回原样**：修订号变了，但现行算法比较文字，不算变化 → 修订号会多算。
2. **语言启用 / 停用**：全局操作，不推进任何应用的修订号 → 修订号完全看不到整种语言的加入或移出。
3. **key 先废弃后恢复**（或反过来）跨越线上版本：只看修订号无法知道它在线上版本中是否存在。

这三种情况都需要「线上版本当时的文字」才能判断，而这正是方案二的变更明细能提供的。

### 2.4 变更明细（原方案二）可以单独覆盖全部场景

变更明细按发布号顺序记录：第一次（基线）记下该版全部文案，之后每版只记相对上一发布号的新增、修改、删除（删除记为一条「移除」行）。由此：

- **任意版本 N 的完整内容** = 每个（语言，key）在发布号 ≤ N 的最后一条明细（不是「移除」的）。一条 SQL 即可还原，按（应用，语言，key，发布号）建索引。
- **预检 / 发布摘要**：还原线上版本 L，与库内当前内容（`assess` 已加载的同一份 key 与译文）按现行 `ReleaseDiff` 比较，口径与现行完全一致，2.3 的三种情况都自然覆盖。
- **差异 / 切回确认**：相邻版本直接读明细；任意基准用两次还原后比较。
- **发布时生成明细**：还原上一发布号的内容，与本次生成语言包用的同一份当前内容比较，得到差异行，在 `registerRelease` 同一本地事务写入，不读文件。

数据量：基线一次性写全量（operations-console 约 2.1 万行、client-console 约 3 千行，只发生一次），之后每版 = 实际改动条数。还原查询扫描的行数 = 基线 + 历次改动，远小于「每版全量快照」。

**建议调整 D2**：方案二作为唯一机制；**原方案一的修订号列不加**。理由：

- 它单独不能保证与现行算法一致（2.3），只能做「候选筛选」，最终仍要拿明细比较。
- 要在全部写入口打标并维护三列，增加写入复杂度和出错点。
- 还原 + 比较的成本是一次索引查询加内存比较；阶段 3 用 mor-test 数据实测。若实测预检达不到 AC-02（≤ 2 s），再把修订号列作为候选筛选优化加回，届时单独确认。

## 3. 风险与依赖

| 风险 | 说明 | 应对 |
|---|---|---|
| 剩余耗时 | 修复后预检 ≈ `assess`（约 1.5 s，主要是 ICU 校验）+ 还原比较 | AC-02 的 2 s 目标可达但余量不大；ICU 校验优化不在本期，阶段 5 实测若超标再单列 |
| 基线前的版本 | 新 Platform 上线后、脚本执行前，旧版本没有明细 | 阶段 3 定兜底：脚本在新 Platform 上线前执行（DDL → 脚本 → 新 Platform），或接口对无明细版本返回明确提示 |
| 发布事务变大 | 首次发布（若没有基线）会写全量 | 由脚本先建基线；明细批量插入并控制每批行数（Snowflake 单毫秒 ID 上限，分批 < 3000） |
| 语言启用 / 停用 | 影响所有应用 | 还原比较天然覆盖，测试单独覆盖 |
| 并发发布 | 已有应用锁与 `I18N_CONTENT_CHANGED` 校验 | 明细在同一事务内写入，继承现有保护 |

## 4. 方案总览（阶段 3）

**一句话规模**：只涉及 Platform Support 1 个系统；新增 0 个、修改 0 个对外接口（预检、发布、版本差异 3 个接口的内部实现改为查库，合同不变）；新增 1 张表（8 个字段、1 个唯一索引、1 个普通索引）；修改 1 张旧表（`t_i18n_release` 新增 1 个字段）；无初始化 DML、无注册 DML；一次性补录写入约 2.5 万行（mor-test）；页面设计 0。

```text
发布 N：当前文案（库） ──比较──> 还原发布 N-1（明细） ──> 写入 N 的变化行 + change_count（同一本地事务）
预检：  当前文案（库） ──比较──> 还原线上发布 L（明细） ──> 变更摘要
差异：  还原发布 B（明细） ──比较──> 还原发布 N（明细） ──> 新增 / 修改 / 移除
```

运行期全部只查 `mor_platform`，不再调用文件服务与 Vault；语言包文件只在发布时生成并上传（与现在相同），供前端加载。

## 5. 数据设计

### 5.1 新表 `t_i18n_release_change`（发布文案变化）

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| `change_id` | BIGINT | 否 | Snowflake 主键 |
| `application_id` | BIGINT | 否 | 所属应用 |
| `release_no` | INT | 否 | 发生变化的发布号；变化相对同应用发布号减一，发布号 1 相对空内容 |
| `locale_code` | VARCHAR(35) ascii_bin | 否 | 语言，en 为英文基准 |
| `key_code` | VARCHAR(191) ascii_bin | 否 | 完整 key（与 `t_i18n_key.key_code` 同类型） |
| `change_type` | VARCHAR(16) | 否 | 允许值：ADDED、CHANGED、REMOVED |
| `text` | TEXT | 是 | 本发布中的文案；REMOVED 时为 NULL |
| `create_time` | DATETIME(6) | 否 | 创建时间；不可变，无更新时间 |

索引：

- `uk_i18n_release_change (application_id, locale_code, key_code, release_no)`：一次发布中同一语言、key 最多一条；同时是「还原发布 N」的访问路径（每个语言、key 取发布号 ≤ N 的最后一条）。
- `idx_i18n_change_release (application_id, release_no)`：列出一次发布的变化（核对、补录校验）。

设计说明（对应 DATABASE_DESIGN_GUIDE 第 7 节）：

- **业务事实**：某次发布相对上一发布号改了哪些文案、改成什么。Owner 为 Platform 多语言模块。
- **写入**：发布登记（`ReleaseServiceImpl.registerRelease`）同一本地事务；历史发布由一次性补录脚本写入。之后不修改、不删除。
- **使用**：预检与发布的变更摘要、版本差异与切回确认（还原任意发布）。
- **为什么不放现有表**：`t_i18n_translation` 只存当前文案，`t_i18n_release_bundle` 只存文件地址；发布时的文案历史没有其他位置。
- **只存改后文字**：改前文字等于同一语言、key 在更小发布号中的最后一条，不重复存储。
- **不存 `release_id`**：`(application_id, release_no)` 已是 `t_i18n_release` 的唯一键，还原查询按发布号范围扫描，不需要联表。
- **删除后果**：无法在不读文件的前提下得到历史发布内容，三个接口退回读文件。

### 5.2 `t_i18n_release` 新增 `change_count`

`INT NULL`，放在 `key_count` 之后：该发布登记的变化条数（可为 0，例如 operations-console 第 1、2 版内容相同）；`NULL` 表示尚未生成明细。

用途：只有每个发布号 ≤ N 的发布都已有明细，才能还原发布 N。一次发布可以没有任何变化行，所以不能用「有没有行」判断是否补录过，需要这个标记。还原前检查 `release_no ≤ N AND change_count IS NULL` 为 0 行，否则报服务端错误「发布变更明细缺失」，不返回错误的摘要。

### 5.3 SQL 与执行材料

- DDL 评审稿：[db/ddl.sql](./db/ddl.sql)（纯新增，可重跑；回滚为删表、删列）。阶段 4 定稿到 `paytech/platform-support/docs/database/sql/changes/2026-10-30-i18n-release-change.sql` 与 `-rollback.sql`，同步 `platform-support-schema.sql` 与 PDManer 模型（`localization` 图新增实体，直接编辑 JSON，不运行生成器）。
- 初始化 DML：不涉及。注册 DML：不涉及（无新接口）。
- 一次性补录：见第 7 节。

### 5.4 数据量

| | operations-console | client-console |
|---|---|---|
| 首个发布（基线） | 约 2.1 万行（en + zh-CN） | 约 3 千行 |
| 之后每次发布 | 实际变化条数（通常几十） | 同左 |

还原原型实测（本机 MySQL 8，2.36 万行：基线 2.14 万 + 22 个发布的变化）：还原一个发布 0.15—0.24 s。

## 6. 实现设计（Platform Support 多语言模块）

### 6.1 还原与比较

- 新 Repository `I18nReleaseChangeRepository`（interface + `*RepositoryImpl`）、Mapper `I18nReleaseChangeMapper` + XML、DataObject `I18nReleaseChange`：
  - `findContent(applicationId, releaseNo)`：每个语言、key 取发布号 ≤ N 的最后一条，排除 REMOVED，返回「语言 → key → 文案」：

    ```sql
    SELECT locale_code, key_code, text FROM (
      SELECT locale_code, key_code, change_type, text,
             ROW_NUMBER() OVER (PARTITION BY locale_code, key_code ORDER BY release_no DESC) AS rn
      FROM t_i18n_release_change WHERE application_id = #{applicationId} AND release_no <= #{releaseNo}) x
    WHERE rn = 1 AND change_type <> 'REMOVED'
    ```

  - `insertAll(changes)`：批量插入，每批 ≤ 1000 行（Snowflake 单毫秒 4096 上限与 SQL 包大小）。
- `I18nReleaseRepository` 增加「发布号 ≤ N 中未生成明细的发布数」查询，用于 5.2 的完整性检查。
- 纯 Java 领域对象 `ReleaseChanges`（与 `ReleaseDiff` 同包）：由 `ReleaseDiff` 生成变化行（ADDED / CHANGED 带改后文字，REMOVED 文字为 NULL），并给出 `change_count`。
- `ReleaseChangeSummaries` 改为：还原线上发布 L（没有线上发布时为空内容）→ 与当前内容 `ReleaseContent.current` 比较 → `ReleaseChangeSummaryFact.of`（摘要结构与口径不变）。删除 `ObjectProvider<ReleaseBundleReader>` 读包路径。

### 6.2 三个接口

| 接口 | 改动 |
|---|---|
| 预检 `PrecheckReleaseProcessor` | ACTIVE key 与译文只查一次，同时供覆盖率评估与变更摘要使用（`assess` 增加接收已加载数据的重载）；摘要按 6.1。 |
| 发布 `PublishReleaseProcessor` + `ReleaseServiceImpl.registerRelease` | 同一份已加载的 key、译文用于评估、摘要、生成语言包与变化行；事务外还原发布号 N-1 并算出变化行；`registerRelease` 在已有的应用锁与 `I18N_CONTENT_CHANGED` 校验之后，同一事务写 `t_i18n_release`（带 `change_count`）、语言包登记、变化行与上线任务。 |
| 版本差异 `DiffReleaseProcessor` | 还原基准发布 B 与目标发布 N 后用 `ReleaseDiff.between` 比较，响应结构不变；不再要求读包端口（原 503 分支删除）。 |

并发与一致性：发布已用 `AbstractLockProcessor` 按应用加锁并在登记时校验文案修订号，变化行基于同一份当前内容生成，与语言包一致；变化行写入失败则整笔登记回滚（已上传的文件按现有失败处理，不变）。事务内只操作数据库，还原与比较都在事务前完成，符合事务公理。

### 6.3 清理

运行期不再使用的读包类（`ReleaseBundleReader` 端口与 `FileDocumentReleaseBundleReader` 实现、`ReleaseChangeSummaries.read`）：阶段 4 核对是否还有其他使用方，没有则删除；导入翻译读取上传文件的 `FileDocumentUploadedFileReader` 不受影响。

## 7. 一次性补录脚本（AC-07）

位置：`release/scripts/backfill_release_changes.py`（阶段 6 交付，人工执行）。

1. **前置**：DDL 已执行；只处理 `change_count IS NULL` 的发布；按应用、发布号升序。
2. **读取**：对每个发布，从 `t_i18n_release_bundle` → `t_file_object` 取对象位置（公开桶 `mor-platform-public` 中的对象路径），直接读对象字节，校验 SHA-256 等于 `t_i18n_release_bundle.content_hash`，不经 Platform、不用 Vault。
3. **计算**：同一应用内，发布 N 的内容与发布 N-1 的内容（发布 1 与空内容）用与 `ReleaseDiff` 相同的规则比较，生成变化行与 `change_count`。
4. **主键**：按 Foundation `SnowflakeIdGenerator` 相同位布局生成（纪元 2025-01-01、5 位 region、5 位 worker、12 位序列），使用保留的 region 31 / worker 31；Platform 运行实例为 region 0，主键不会冲突。
5. **写入**：每个发布一个事务：插入变化行 + `UPDATE t_i18n_release SET change_count = ? WHERE release_id = ? AND change_count IS NULL`；影响行数不为 1 则回滚该事务并停止。可重复执行，已补录的发布跳过。
6. **核对**：补录后对每个发布，用第 6.1 节的还原 SQL 还原内容，与该发布语言包文件内容逐条比较，必须完全一致；输出每个发布的变化条数与核对结果。
7. **回滚**：`DELETE FROM t_i18n_release_change WHERE application_id IN (…)` 并把 `change_count` 置回 NULL；或按 DDL 回滚删表删列。

部署顺序（阶段 6 细化）：DDL → 补录脚本 → Platform 新镜像。补录到新镜像上线之间若旧 Platform 又发布了新版本，该发布 `change_count` 为 NULL，上线前再运行一次脚本即可补上（脚本幂等）；新镜像遇到未补录的发布会报「发布变更明细缺失」，不返回错误摘要。

## 8. 验证计划

auto 测试（Platform Support，经真实 HTTP 入口，按 UNIT_TEST_GUIDE；本地 MySQL / MinIO 由 `local-environment` 提供）：

| 编号 | 场景 | 断言 | AC |
|---|---|---|---|
| T1 | 首次发布 | 变化行全部为 ADDED、条数等于 en + 已填译文数；`change_count` 一致 | AC-05, AC-09 |
| T2 | 改原文、改译文、新增 key、废弃 key 后再发布 | 只写变化的行；CHANGED / ADDED / REMOVED 正确；改后文字正确 | AC-05, AC-09 |
| T3 | 改了又改回原样后预检 / 发布 | 不算变化；变化行为 0；`change_count = 0` | AC-03, AC-05 |
| T4 | 停用、再启用语言后预检与发布 | 整个语言计为移除 / 新增 | AC-03 |
| T5 | key 废弃后恢复 | 与线上比较按文字判断，结果与现行算法一致 | AC-03 |
| T6 | 切回旧发布后预检 | 与旧发布比较 | AC-03 |
| T7 | 版本差异：默认上一版、指定任意 `baseReleaseNo`、无基准（发布 1） | 新增 / 修改 / 移除与改前改后文字正确 | AC-06 |
| T8 | 预检、发布、差异不读文件 | 测试中记录文件读取与 Vault 调用为 0（读包端口删除后由代码结构保证；保留一条断言防回归） | AC-01, AC-04, AC-06 |
| T9 | 存在未补录（`change_count` 为 NULL）的发布 | 预检 / 差异返回服务端错误，不返回摘要 | 5.2 |
| T10 | 摘要口径回归 | 现有预检 / 发布 / 差异测试全部通过（结构与数字不变） | AC-03, AC-08 |

其他：

- 补录脚本：本机 e2e 库（`mor_platform_v2e2e`）与 mor-test 执行后自带核对（第 7 节第 6 步），并与现行接口（旧镜像）返回的预检摘要逐项比对。
- 性能：mor-test 预检 5 次中位数 ≤ 2 s、版本差异 ≤ 1 s；Vault 日志在预检期间 0 次 REVEAL。
- BFF、ops-web、client-console-web 测试不改并全部通过；浏览器检查发布弹窗与发布记录页（1440 / 390）。

研发顺序：PDManer 与 DDL → DataObject / Mapper / Repository → 领域对象 `ReleaseChanges` → 三个 Processor 与 `registerRelease` → 删除读包路径 → auto 测试 → 补录脚本（阶段 6）。
