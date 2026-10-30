# 发布树合并记录（阶段 6，2026-10-30）

- 发布树：`/Users/kenny/Desktop/Workspace/Code-MoR-plops-rel`，detached `331d8e0ea965db6a1975c2f87e5f9da48310ec54`（main；功能源码 = `b92c0fde`，`331d8e0e` 只加 payout 交付记录）。
- 来源：`Code-MoR-plops` 的 67 个文件（`git status --short --untracked-files=all`，M 43 + ?? 24），以私有索引 `git add -A` 后生成 `git diff --cached --binary 9eab368c` 补丁（4425+ / 74-），在发布树 `git apply --3way`。快照哈希见 [source-plops-files.sha256](./source-plops-files.sha256)。
- 结果：64 个文件干净应用（其中 7 个与 payout 改动重叠、三方自动合并），3 个冲突手工合并。发布树相对 `331d8e0e` 的差异指纹 `0791ea1fb8a8b7af`（合并后；加第 4 节测试修正后为 `78ccfd1dc42e8c62`；计算：`git add -N . && git diff --binary 331d8e0e | shasum -a 256`）。
- 核对：57 个非重叠文件与 `Code-MoR-plops` 逐字节相同；10 个重叠文件不同（= payout 内容 + 本需求内容），见下表。

## 1. 冲突（3 个，手工合并）

| 文件 | ours（main，payout） | theirs（本需求） | 合并 |
|---|---|---|---|
| `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 末尾块 147（payout，364 行） | 末尾块 148（100 行） | 两段都保留：块 147 原样 → `COMMIT;` → 分隔线 → 块 148 原样。只追加 100 行（`git diff --numstat` = 100 / 0）；块 148 从注释首行到文件末尾与 `Code-MoR-plops` 逐字节相同；块 147 以前的内容与 main 相同 |
| `operations-console-web/src/configuration/configurationModel.test.ts` | 页面数 85（payout +2） | 页面数 84（REQ-04 基线 83 +1） | 86（85 + 本需求 1）；保留本需求新增的 `OPERATIONS_FINANCE_POSTING_OPS → /finance/posting-ops` 断言 |
| `operations-console-web/src/finance/finance.test.ts` | 标题"财务七页"，过滤器排除 `OPERATIONS_FINANCE_PAYOUT_*`（出款两页在 payout.test.ts 校验） | 标题"财务八页 … 明细账运维有追平 / 重建"，过滤器只排除 RECON | 标题取本需求的"财务八页"；注释与过滤器取 payout 的（同时排除 RECON 与 PAYOUT）；断言列表（含 POSTING_OPS 与两个动作）为自动合并结果 |

合并后片段：

```diff
-  assert.equal(pageCapabilities.length, 85);
-  assert.equal(new Set(pageCapabilities.map(p => p.componentKey)).size, 85);
+  assert.equal(pageCapabilities.length, 86);
+  assert.equal(new Set(pageCapabilities.map(p => p.componentKey)).size, 86);
+  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_FINANCE_POSTING_OPS')?.frontendRoute, '/finance/posting-ops');
```

```diff
-test('页面注册：财务七页按序登记，动作与 Platform 能力清单一致（账本查询增加发起试算，三个新页各有制单 / 复核）', () => {
+test('页面注册：财务八页按序登记，动作与 Platform 能力清单一致（账本查询增加发起试算，三个新页各有制单 / 复核，明细账运维有追平 / 重建）', () => {
   // 对账七页（OPERATIONS_FINANCE_RECON_*）在 reconciliation/reconModel.test.ts 校验；出款两页（OPERATIONS_FINANCE_PAYOUT_*）在 payout/payout.test.ts 校验。
   const finance = pageCapabilities.filter(page => page.componentKey.startsWith('OPERATIONS_FINANCE_') && !page.componentKey.startsWith('OPERATIONS_FINANCE_RECON_')
     && !page.componentKey.startsWith('OPERATIONS_FINANCE_PAYOUT_'));
```

```sql
  ROW(900000000000147824, 900000000000147804, @b147_ses_primary, NULL, 'ACTIVE', 1, @b147_time, @b147_time)) AS b147_routes WHERE @b147_ses_primary IS NOT NULL;

COMMIT;

-- ---------------------------------------------------------------------------
-- Operations finance posting operations console (2026-10-30, requirements/20261030/operations-明细账运维).
...（块 148 原样，以 COMMIT; 结尾）
```

## 2. 三方自动合并（7 个，已逐个查看）

| 文件 | 内容 |
|---|---|
| `operations-console-web/src/i18n/en/nav.json`、`i18n/zh-CN/nav.json` | payout 的 `nav.resource.OPERATIONS_FINANCE_PAYOUT*` 与本需求三键（`…POSTING_OPS` / `_CATCH_UP` / `_REBUILD`）各在字母序位置插入，JSON 合法 |
| `operations-console-web/src/navigation/pageCapabilities.ts` | 本需求 `OPERATIONS_FINANCE_POSTING_OPS`（+15 行）插在 SUBLEDGER 之后；payout 两页保留 |
| `.../operations/console-resource-capabilities.json` | 同上（+15 行），JSON 合法；Platform 候选 JAR 对现网只此 1 项变化 |
| `operations-console-web/src/navigation/ConsolePage.tsx` | `PageProps` 加 `actorLabel`；页面表加 `OPERATIONS_FINANCE_POSTING_OPS`；payout 页面保留（tsc 通过，payout 页面忽略多出的 prop） |
| `.../operations-console-bff-app/src/main/resources/application.yml` | 长耗时读超时注释扩展 + 新键 `account-long-running-read-timeout`（默认 9s，无需 env）；payout 无此处改动但上下文相邻 |
| `.../OperationsBffHttpTestBase.java` | 测试属性加 `operations.account-long-running-read-timeout` |

## 3. 未带入

- `Code-MoR-plops` 中未跟踪的 `target/`、`node_modules`、`dist/` 不在 67 个文件中（`git status` 未列出）。
- `REPORT-W1.md` / `REPORT-W2.md` 已在需求目录 `tasks/`，不在工作树根目录，不进入发布树。

## 4. 发布树内的测试修正（第 68 个文件，阶段 6 发现）

`paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java`：
`test_applicationReusesFoundationAndExposesExactOperationsRoutes` 的精确路由清单缺本需求 4 个 BFF 接口（阶段 4 / 5 只跑了 `Finance*` 与
`PostingOpsRegistrationAutoTest`，未跑整个 ops-bff 模块）。在 `GET /finance/account-postings/v1/{postingId}` 之后补 4 项：

```diff
             "GET /finance/owner-accounts/v1/{accountId}/entries", "GET /finance/account-postings/v1/{postingId}",
+            "GET /finance/posting-progress/v1/ledger-books/{ledgerBookId}", "GET /finance/posting-streams/v1/{streamId}/postings",
+            "POST /finance/posting-streams/v1/{streamId}/catch-up", "POST /finance/posting-rebuilds/v1",
```

只是测试代码，不进 JAR（测试后重建的 ops-bff JAR 与镜像内 JAR 0 差异）。`Code-MoR-plops` 尚无此修正：阶段 8 以发布树为准提交，或先同步回 `Code-MoR-plops`。
发布树差异指纹因此变为 `78ccfd1dc42e8c62`（68 个文件，4427+ / 74-）。
