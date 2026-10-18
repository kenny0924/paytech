# 第 10 步报告 · 实例内迁移执行器骨架

- 分支：`refactor/step-10-migration-runner`（起点 `refactor-main@5f1fc10`）；实现提交 `5ad15f5`，本报告随后单独提交。
- 结论：任务卡第 2–4 节全部实现。`./mvnw -B clean verify` 通过，共 90 个测试，0 失败；其中迁移执行器 27 个，含 7 个真实连接 Foundation 测试库的用例。CLI 在 Foundation 测试库上的 plan / migrate / validate / seed 实录见第 3 节。测试与实录创建的表已全部删除，残留为 0。
- 环境说明：worktree 内没有 `local-environment/.env.local`（未跟踪文件）。因此所有涉及数据库的 Maven 命令都带 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`，只读取主工作区的该文件。不带该参数时，数据库用例按要求失败并给出启动说明（见 2.3）。MySQL `127.0.0.1:13306` 在运行，未重启或改动任何容器。

## 1. 改动摘要

1. 新模块 `paytech/tools/migration-runner`，坐标 `com.paytech:paytech-migration-runner`，包 `com.paytech.migration`，父 POM 为 `paytech-parent`。
   - 依赖只有 `mysql-connector-j`（BOM 9.7.0）、`paytech-instance-manifest` 和 `jackson-databind`（3.x）。
   - `maven-shade-plugin` 3.6.0 打出 `paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar`，Main-Class 为 `cli.MigrationRunnerCli`。
2. `cli/` 下有 `MigrationRunnerCli`（入口与退出码）、`CliArguments`、`MigrationCommand`、`DatabaseEnvironment`（环境变量）和 `ReportWriter`（文本与 `--json`）。
3. `model/` 下有 `MigrationScript`、`AppliedMigration`、`MigrationPlan`、`Violation` 及 `ViolationCode`（18 类），另有 `MigrationTarget`、`DatabaseCredentials`（`toString` 不含 URL 与密码）、`ServiceReport`、`ExecutionFailure`、`ExecutedMigration`、`AppliedSeed`、`SeedOutcome` 和 `SqlStatement`。
4. `sql/` 下是 `SqlScriptSplitter` + `Impl`（单遍词法扫描）与 `SqlScriptSyntaxException`。
   - 能识别单双引号字符串（含 `\` 转义与 `''`）、反引号标识符（含 ``` `` ```）、`--␠` 与 `#` 行注释、块注释，并按顶层 `;` 切分。
   - 未闭合的字符串或注释报出起始行号。
5. `repository/` 下是 `SchemaMigrationRepository` + `Impl`，纯 JDBC 实现。
   - 两张记录表的 DDL 带完整中文注释。
   - 同时负责 `GET_LOCK` / `RELEASE_LOCK`、`DATABASE()` 和 `information_schema` 存在性查询。
6. `service/` 下是 `MigrationPlanner`、`MigrationExecutor`、`ChecksumCalculator`，均为 interface + Impl。另有两个辅助类：
   - `SeedScriptInspector`：检查初始化数据。
   - `ScriptStatementRules`：识别禁止语句与 DDL。
7. `paytech/pom.xml` 在 `tools/instance-manifest` 之后新增一行 `tools/migration-runner`，原文件没有占位行，其他行未动。
8. `tools/instance-manifest` 新增 `versions.schema` 的解析结果，作为执行器复用的唯一来源：
   - `InstanceManifest` 增加 `schemaVersions` 与 `findService`，`toModel` 负责填充。
   - 原有测试补了 2 个断言，测试数不变。
9. `README.md` 包含第 2 节约定全文、命令与示例、失败后的人工恢复、与 DATABASE_DESIGN_GUIDE 的关系，以及 D22 回传日志的接口说明。
10. 测试资源：`src/test/resources/migrations/` 下 8 组 fixture（executor、seed、concurrent 和 5 组 planner）与 2 份最小清单（`manifests/ledger-2.yaml`、`ledger-3.yaml`）。

未改动：任何现有 `docs/database/sql/*.sql`、`agent-docs/`、`AGENTS.md`、`local-environment/`、`~/.m2/settings.xml`。本步没有生成任何服务的 `V1__baseline.sql`，也没有对任何 `mor_*` 库执行语句。

## 2. 验证（实际命令与结果）

所有 Maven 命令前均执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。

### 2.1 `./mvnw -B clean verify`

```text
cd paytech && ./mvnw -B clean verify -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
→ BUILD SUCCESS，exit 0
```

| 模块 | 测试数 | 失败 / 错误 |
|---|---|---|
| paytech-arch-rules | 45 | 0 / 0 |
| paytech-instance-manifest | 16 | 0 / 0 |
| paytech-migration-runner | 27 | 0 / 0 |
| paytech-arch-rules-reactor-test | 2 | 0 / 0 |
| 合计 | 90 | 0 / 0 |

迁移执行器 27 个测试的分布：

- `SqlScriptSplitterAutoTest` 7 个：字符串内分号、注释内分号、反引号标识符、多行语句与 CRLF、`--` 后无空白不算注释、纯注释、未闭合。
- `ChecksumCalculatorAutoTest` 2 个：CRLF、CR、BOM 与 LF 的结果一致；SHA-256 向量独立取自 `shasum`。
- `MigrationPlannerAutoTest` 11 个：
  - 任务卡要求的四例：版本不连续、重复、超前、checksum 漂移。
  - 其余：正常、`--target` 低于清单版本、目标文件缺失、失败记录与中间缺口、N-1 并存、禁止语句 / 未闭合 / 空脚本 / 非法文件名、目录不存在。
- `MigrationExecutorAutoTest` 7 个，均为 Foundation 测试库上的真实迁移：
  - V1、V2 成功。
  - V3 第 2 条语句故意出错：执行停止，写入 `success=0` 与错误，退出码 2；第 1 条 DDL 已生效且不回滚；随后 `validate` 退出码 1 并报出失败行，再次 `migrate` 不执行任何语句。
  - 修改已执行文件后 `validate` 报 CHECKSUM_MISMATCH；只改换行（CRLF）不算漂移。
  - 两个执行器实例并发：只有一个执行 V1、V2，另一个等锁后执行 0 个；V1 中的 `SELECT SLEEP(2)` 保证两者确实重叠。
  - seed 一次性执行、重复执行只校验、改动后报 SEED_CHECKSUM_MISMATCH。
  - seed 失败时整体回滚；含 DDL 时拒绝执行。
  - 环境错误一律退出码 3：缺变量时一次列出全部变量名、库名不符时拒绝执行、`--target` 越界、未知子命令。`plan` 不创建记录表。

每个测试的 `@BeforeEach` / `@AfterEach` 删除 `t_mr_test_*`、`t_schema_migration` 与 `t_schema_seed`，删除失败即测试失败。测试代码中没有 `when(...).thenReturn`、`@MockBean` 或 spy。

shade 的告警：module-info 已排除；剩余 `META-INF/LICENSE` / `NOTICE` 重叠告警，属无害提示。reactor-test 模块仍有既有的 "JAR will be empty"。

### 2.2 `paytech/scripts/verify-reactor.sh`

```text
MAVEN_ARGS="-Dpaytech.local-environment.dir=…/Code-MoR/local-environment" bash scripts/verify-reactor.sh
→ table-ownership.yaml is current (265 physical tables)
→ total tests=90 failures+errors=0
```

### 2.3 不带 `-Dpaytech.local-environment.dir` 时数据库用例的快速失败

```text
./mvnw -B -o test -pl tools/migration-runner -am -Dtest='MigrationExecutorAutoTest#test_normalFlow' -Dsurefire.failIfNoSpecifiedTests=false
→ Tests run: 1, Failures: 1
  AssertionFailedError: 缺少 …/Code-MoR-step10/paytech/../local-environment/.env.local。请先手工启动仓库 local-environment 的 MySQL：
  cd local-environment && docker compose --env-file .env.local up -d mysql；并确认 .env.local 含 PAYTECH_FOUNDATION_TEST_DB_PASSWORD、…
```

这里记录的是加入 worktree 提示前的输出。提示随后已补上"在 git worktree 中运行时用 `-Dpaytech.local-environment.dir=…` 指定"一句。

### 2.4 残留检查

测试与实录结束后，用 cli.jar 自带的驱动查询 `paytech_foundation_test` 中 `t_mr_test_%`、`t_schema_migration`、`t_schema_seed` 的表数，结果是 `owned tables left: 0`。

## 3. CLI 实录（Foundation 测试库）

- 清单：`manifests/ledger-2.yaml`（ledger 服务，清单 db 为 `mor_ledger`，`versions.schema.ledger: 2`）。
- SQL 根目录：fixture `migrations/seed` 的副本。
- 环境变量：`PAYTECH_DB_URL_LEDGER` 指向 `paytech_foundation_test`，`PAYTECH_DB_USER_LEDGER=foundation_test`，密码在 shell 中从 `.env.local` 读入、不回显。由于物理库名与清单 db 不同，另设 `PAYTECH_DB_SCHEMA_LEDGER=paytech_foundation_test`。
- 第二段模拟 N+1 发布：加入 V3（第 2 条语句故意出错），并把清单版本提到 3。
- 结束后删除了全部测试表。

```text
$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar plan --manifest instance.yaml --sql-root sql
plan ledger (database paytech_foundation_test): current V0, target V2
  pending   V1__create-mr-test-entry.sql
  pending   V2__add-mr-test-audit.sql
exit 0
(exit code 0)

$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar migrate --manifest instance.yaml --sql-root sql
migrate ledger (database paytech_foundation_test): current V0, target V2
  planned   V1__create-mr-test-entry.sql
  planned   V2__add-mr-test-audit.sql
  executed  V1__create-mr-test-entry.sql (9 ms)
  executed  V2__add-mr-test-audit.sql (12 ms)
exit 0
(exit code 0)

$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar validate --manifest instance.yaml --sql-root sql
validate ledger (database paytech_foundation_test): current V2, target V2
exit 0
(exit code 0)

$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar seed --manifest instance.yaml --sql-root sql
seed ledger (database paytech_foundation_test): current V2, target V2
  seed      APPLIED
exit 0
(exit code 0)

# release N+1: add V3 (2nd statement deliberately broken), raise versions.schema.ledger to 3
$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar plan --manifest instance-n1.yaml --sql-root sql
plan ledger (database paytech_foundation_test): current V2, target V3
  pending   V3__broken-statement.sql
exit 0
(exit code 0)

$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar migrate --manifest instance-n1.yaml --sql-root sql
migrate ledger (database paytech_foundation_test): current V2, target V3
  planned   V3__broken-statement.sql
  failure   V3__broken-statement.sql 第 2 条语句（第 3 行）失败: [42S02/1146] Table 'paytech_foundation_test.t_mr_test_missing' doesn't exist
exit 2
(exit code 2)

$ java -jar paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar validate --manifest instance-n1.yaml --sql-root sql --json
{
  "command" : "validate",
  "exitCode" : 1,
  "services" : [ {
    "service" : "ledger",
    "database" : "paytech_foundation_test",
    "currentVersion" : 2,
    "targetVersion" : 3,
    "exitCode" : 1,
    "pending" : [ {
      "version" : 3,
      "script" : "V3__broken-statement.sql",
      "checksum" : "1b237e28323ebf218a4c1993bc71ecd92f018b0f82da59fa50e39ccb6d5e7194"
    } ],
    "executed" : [ ],
    "violations" : [ {
      "code" : "FAILED_MIGRATION",
      "version" : 3,
      "message" : "V3__broken-statement.sql 执行失败，需人工恢复后删除该失败记录: 第 2 条语句（第 3 行）失败: [42S02/1146] Table 'paytech_foundation_test.t_mr_test_missing' doesn't exist"
    } ],
    "failure" : null,
    "seed" : null
  } ]
}
(exit code 1)

```

## 4. 设计决定（任务卡未明确处）

1. **库名核对与 `PAYTECH_DB_SCHEMA_<SERVICE>`**：连接后 `DATABASE()` 必须等于清单的 `services.<service>.db`，否则退出码 3，防止把一个服务的迁移执行到另一个库。
   - 清单校验器强制 db 等于目录库名（如 `mor_ledger`），而测试只能使用 `paytech_foundation_test`。
   - 现有环境也存在 `mor_platform_e2e` 一类物理库名。因此增加这个可选变量，只用于声明物理库名，不放宽核对。
2. **`--target` 与清单版本分开**：`MigrationTarget` 同时携带 `manifestVersion` 与 `targetVersion`。超前文件和目标文件存在两项按清单版本校验，执行只到 `--target`。这样 `migrate --target 2` 不会把清单内的 V3 误报为超前。`--target` 必须同时指定 `--service`，且不得高于清单版本。
3. **N 与 N-1 并存（D22）**：库中高于清单版本的成功记录不做校验，也不回退。当前版本取已成功执行的最高版本。
4. **失败记录阻断后续操作**：任何 `success=0` 记录都会让 migrate、seed、validate 报 FAILED_MIGRATION。执行器不提供 repair 或回滚命令，人工恢复步骤写在 README 3.3。
5. **`plan` 的退出码**：`plan` 同样给出迁移校验结果，有失败时退出码为 1，与 `validate` 一致；`validate` 另外检查初始化数据的校验和。
6. **禁止语句的范围**：除任务卡列出的 `DELIMITER`、`USE` 和存储过程外，同属存储程序与过程调用的 FUNCTION、TRIGGER、EVENT、`CALL` 也一并禁止。
7. **多服务处理**：某服务执行失败（退出码 2）后不再处理后续服务；校验失败的服务不执行任何语句，后续服务照常处理。退出码取各服务最大值。
8. **执行记录的字段**：`installed_by` 取环境变量中的账号；`installed_at` 取数据库 `UTC_TIMESTAMP(6)`，不依赖客户端时区。

## 5. 待确认项

1. **`t_schema_migration.error_message`**：任务卡要求"记录 `success=0` 与错误"，但列清单里没有错误列。本步新增 `error_message VARCHAR(1000) NULL`，内容为失败语句序号、行号、SQLState 与错误摘要。若坚持任务卡原列清单，需要另定错误的落点。
2. **seed 的处理**：当前实现如下：
   - 只在 Schema 达到清单版本后才可执行。
   - 只允许 DML，在一个事务内执行，并与 `t_schema_seed` 记录一起提交。
   - 首次执行后文件不得再改，后续数据变更进入版本化迁移。
   - 文件改动会让 `seed` 与 `validate` 报 SEED_CHECKSUM_MISMATCH。

   这改变了现行"一份持续维护的当前初始化 DML"的语义，需要确认以下两点：
   - 已部署实例的初始化数据增量，是否一律走 `V<n>` 迁移。
   - 新实例是否仍以"`V1..Vn` + 当前 seed 文件"重建。若是，seed 文件需要与最新版本同步维护，与"不得再改"冲突。可选方案是按 Schema 版本给 seed 文件分版，或只允许在空库上执行 seed。
3. **与 `test-environment/scripts/initialize.py` 的关系**：
   - 该脚本目前经 `compose.sh exec mysql` 以 root 在空库上执行整份 `*-schema.sql` 与 `*-initial-data.sql`。Platform 与 Risk 还带部分库存检查和 bootstrap SQL。
   - 服务按后续搬迁步骤生成 `V1__baseline.sql` 后，建议该脚本对已搬迁服务改为调用本执行器：以各服务账号执行 `migrate` 再执行 `seed`，未搬迁服务保持原路径。
   - 这需要 test-environment 相关步骤改动，并为 mor-test 提供 `PAYTECH_DB_*` 注入方式，本步未改。
4. **规范修订**：以下文档本步均未修改，列为后续修订项：
   - `agent-docs/DATABASE_DESIGN_GUIDE.md` 第 5、13 节："当前 Schema DDL + 初始化 DML，人工执行" → "V1 baseline + 版本化迁移 + 初始化数据，由迁移执行器执行并记录"。
   - `TECHNOLOGY_STACK.md` §3 "Schema 演进"一行与 §9.2。
   - `DEVELOPMENT_CONVENTIONS.md` 第 3 节"手工 Schema DDL"一句。
   - `AGENTS.md` 的 Manual infrastructure rule，其中"每服务一个初始化 DML 文件"保留，"applied manually"需改写为"经评审后由迁移执行器执行"。
5. **`maven-shade-plugin` 版本**：3.6.0 固定在模块 POM 的属性里，因为 `paytech-parent` 没有管理该插件。若以后还有其他可执行工具，宜上移到父 POM 的 pluginManagement。
6. **`InstanceManifest` 的签名变化**：record 新增 `schemaVersions` 组件，规范构造器的参数因此由 3 个变为 4 个。目前仓库内只有 `toModel` 调用该构造器。如有并行步骤在构造 `InstanceManifest`，合并时需要补上该参数。
7. **记录表的默认字段**：作为执行器自有技术表，两张记录表没有 `create_time/update_time`，`installed_at` 承担同等语义。按 DATABASE_DESIGN_GUIDE 第 9 节，这算一个例外，请确认是否接受。
