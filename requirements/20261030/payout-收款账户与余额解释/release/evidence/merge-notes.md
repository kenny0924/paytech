# payout S1 · 候选树合并说明（阶段 6，W8，2026-10-30）

候选树 `/Users/kenny/Desktop/Workspace/Code-MoR-po-rel`（detached `9eab368c`）= main `9eab368c` + 集成 worktree `Code-MoR-po-int`（基于 `30361be7`，未提交）的改动 + 发布所需的 test-environment 改动。清单与哈希见 [source-files.sha256](./source-files.sha256)（247 个文件），漂移检查见 [source-drift-check-stage6.txt](./source-drift-check-stage6.txt)（`scripts/source_drift_check.sh`，可重跑）。

## 1. 文件清单来源

- po-int `git status --short -uall`（M 54 + A 187）与 `git diff --name-only 30361be7` 的交集 = **241** 个文件；排除 `node_modules`、`target`、`dist`、`.vite-cache`、`release-draft/`（8 个，W9 的块 147 草稿，已另行落在 `release/sql/`）、`REPORT-W4.md`。
- `30361be7..9eab368c` 改过 1 986 个文件；与 241 的交集 **21** 个（C 区域设置下复核两次）。
- 其余 **220** 个与 po-int 逐字节相同（复制）。

## 2. 21 个重叠文件（共同祖先 `30361be7`，ours = `9eab368c`，theirs = po-int）

`git merge-file` 复跑结果：9 个自动合并且与候选逐字节相同；12 个有冲突，已手工合并。逐个核对"po-int 相对 `30361be7` 新增的每一行都在候选里、候选没有删掉 po-int 没删的行"：除下表列出的冲突改写行外全部满足。

| 文件 | 自动 / 冲突 | 处理 |
|---|---|---|
| `agent-docs/CURRENT_IMPLEMENTATION.md` | 冲突 | 两边都是在同一位置插入新段落：保留 REQ-04 段落，payout 段落 8 行原样追加；候选相对 `9eab368c` 只新增这 8 行，0 删除 |
| `operations-console-web/i18n/zh-CN/nav.json` | 自动 | — |
| `operations-console-web/src/configuration/configurationModel.test.ts` | 冲突 | 页面能力数：`9eab368c` 83（tax、REQ-04 各 +2）、po-int 81（79 + payout 2）→ **85** = 83 + 2 |
| `operations-console-web/src/finance/finance.test.ts` | 自动 | — |
| `operations-console-web/src/i18n/builtin.ts` | 冲突（2 处） | import 与映射两边都保留：`taxNs` 之后加 `payoutNs` / `payout: payoutNs` |
| `operations-console-web/src/i18n/en/nav.json` | 自动 | — |
| `operations-console-web/src/i18n/namespaces.ts` | 冲突 | 命名空间列表 `… 'reference-data', 'tax', 'payout'`（两边都保留） |
| `operations-console-web/src/navigation/ConsolePage.tsx` | 自动 | — |
| `operations-console-web/src/navigation/pageCapabilities.ts` | 自动 | — |
| `paytech/operations-console-bff/paytech-operations-console-bff-app/pom.xml` | 冲突 | `paytech-tax-api` 依赖保留，其后加 `paytech-payout-api` |
| `…/configuration/OperationsErrorStatusResolver.java` | 自动 | — |
| `…/operations-console-bff-app/src/main/resources/application.yml` | 自动 | — |
| `…/auto/OperationsConsoleBffApplicationAutoTest.java` | 冲突 | 只记元数据前缀列表：保留 `"/tax/v1"`，末尾加 `"/payout-admin"`；路由清单末尾追加 12 条 payout 路由（po-int 原样） |
| `…/test/base/OperationsBffHttpTestBase.java` | 冲突 | `operations.tax-base-url` 之后加 `operations.payout-test-base-url` |
| `…/test/configuration/OperationsBffTestConfiguration.java` | 冲突（2 处） | 字段 `TAX` 之后加 `PAYOUT`；Bean 方法 `taxOwnerSimulator()` 之后加 `payoutOwnerSimulator()`。**上一个 W8 的手工合并漏了 `@Bean(destroyMethod = "close")`**（冲突块内只有方法行），导致 `PayoutAdminBrowserAutoTest` 12 个用例上下文失败（"No qualifying bean of type PayoutOwnerSimulator"）。本次补回注解，修正后候选相对 `9eab368c` 的新增行 = po-int 相对 `30361be7` 的新增行 |
| `paytech/operations-console-bff/pom.xml` | 冲突 | dependencyManagement：`paytech-tax-api` 之后加 `paytech-payout-api` |
| `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 冲突 | 两边都是在文件末尾追加独立块：保留块 144—146 原文，块 147（368 行）原样追加在最后；候选相对 `9eab368c` 只新增这 368 行，0 删除，与 po-int 的新增行逐字节相同 |
| `…/operations/console-resource-capabilities.json` | 自动 | — |
| `paytech/platform-support/paytech-platform-support-app/src/main/resources/application.yml` | 自动 | — |
| `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` | 自动 | payout 下加 `t_beneficiary_account_event` |
| `paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java` | 冲突（2 处） | 表数式子取 `9eab368c` 版（含 tax / REQ-03 / 联合查询 / 多语言 / REQ-04）再 `+ 1`（payout 新表）；说明串保留 REQ-04 行，末尾追加 payout 行。arch-rules 测试通过 |

冲突标记残留：0。

## 3. 合并后修正（候选树独有，po-int 没有）

| 文件 | 原因 | 影响镜像？ |
|---|---|---|
| `…/test/configuration/OperationsBffTestConfiguration.java` | 补回合并漏掉的 `@Bean`（见上表） | 否（测试代码） |
| `paytech/payout/paytech-payout-test/src/test/java/com/paytech/payout/test/mock/external/MockPlatformNotificationController.java` | 阶段 5 把 Mock 挂到 `@RequestMapping("/platform-support")` 以配合 `lb://platform-support-service/platform-support`，但类上的 `@RequestMapping` 取代了接口类型级 `@HttpExchange("/notifications/v1")`，实际映射成 `/platform-support/requests`，payout 7 个通知相关用例在 20 秒等待处超时（日志 `No mapping for POST /platform-support/notifications/v1/requests`）。改为 `@RequestMapping("/platform-support/notifications/v1")`，方法路径 `/requests` 仍取自接口。修正后 payout 33 / 0。**po-int 同样有此问题**，提交时以候选树为准 | 否（测试代码） |

## 4. test-environment（发布所需，均写在候选树，未改 `test-environment/compose*.yaml`）

| 文件 | 内容 | 来源 |
|---|---|---|
| `test-environment/java/LocalHttpHealthProbe.java` | 允许列表加 `http://127.0.0.1:19117/actuator/health/readiness`（payout 管理端口 = 18117 + 1000，已核对 payout `application.yml` `management.server.port: 19117`） | 上一个 W8 |
| `test-environment/scripts/test_java_image_health_probe.py` | 同上两处登记 | 上一个 W8 |
| `test-environment/scripts/build.sh` | 服务清单加 `payout-service:payout` 与 compose build 列表 | 上一个 W8 |
| `test-environment/scripts/prepare.py` | `V2_DATABASE_PASSWORDS` 加 `MOR_PAYOUT_DB_PASSWORD`（已有 `.env.test` 时只追加缺失键，不改已有值） | 本次 |
| `test-environment/scripts/initialize.py` | `SERVICE_DATABASES` 加 `ServiceDatabase("payout", "mor_payout", "payout_app", "MOR_PAYOUT_DB_PASSWORD", False)`：`applies_sql=False`，因为 `docs/database/sql/payout-schema.sql` 还是 9 表草稿（`t_compensation_event` 是旧形状），与代码不一致（W9 指出），表结构只按 `release/sql/12-mor_payout-ddl.sql` 手工执行 | 本次 |
| `test-environment/scripts/test_service_database_initialization.py` | 库数 14 → 15；不走 `--apply-service-sql` 的库加 `payout` | 本次 |

`build.sh` 与两个 Python 测试还要求 base compose 有 `payout-service`（`compose.applications.yaml`）与 mysql 的 `MOR_PAYOUT_DB_PASSWORD`（`compose.yaml`）。按任务卡规则不改基础文件，改为提交补丁 [../compose-base-payout.patch](../compose-base-payout.patch)，由用户合入。在候选树临时套用补丁后两个 Python 测试文件 25 / 2（2 个失败在 `9eab368c` 上同样存在，与本需求无关），随后已 `git checkout` 还原。
