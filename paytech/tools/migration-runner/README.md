# paytech-migration-runner

实例内迁移执行器（决定 D11、D22）：读取评审后的版本化 SQL，按实例清单 `versions.schema` 在**本实例内**逐服务库执行并记录。
它把"执行"工具化，不改变"评审"：SQL 仍是人工审查的纯 SQL，PDManer 仍是设计源。

- 坐标 `com.paytech:paytech-migration-runner`，包 `com.paytech.migration`，可执行 JAR `paytech-migration-runner-<ver>-cli.jar`（`maven-shade-plugin`，classifier `cli`）。
- 依赖只有 `mysql-connector-j`（BOM 版本）、`paytech-instance-manifest`（清单校验与解析）、Jackson（`--json` 输出）。不用 Spring Boot，不引入 Flyway / Liquibase。
- 不接运维平面、不做远程下载、不并行执行多库；不提供回滚命令。

## 1. 版本化 SQL 目录

每个拥有数据库的服务：

```text
paytech/<service>/db/
├── migrations/
│   ├── V1__baseline.sql            # 该服务当前完整 Schema（由各搬迁步骤从 *-schema.sql 切片生成）
│   ├── V2__<kebab-description>.sql
│   └── ...
└── seed/
    └── <service>-initial-data.sql  # 唯一的初始化 DML 文件
```

- 文件名 `V<version>__<kebab-description>.sql`：`<version>` 为从 1 起、无前导零的正整数，与实例清单 `versions.schema.<service>` 同义；描述为小写 kebab-case，不超过 200 字符。同一服务版本号唯一且从 1 起连续。以 `.` 开头的隐藏文件被忽略，其他不合规文件名都是校验失败。
- 内容是评审后的纯 SQL（MySQL 8.4），允许多条语句与 `SET NAMES` 等会话语句；不允许 `DELIMITER`、`USE`、`CALL` 与存储程序（`CREATE/ALTER/DROP PROCEDURE|FUNCTION|TRIGGER|EVENT`）。连接到哪个库由环境变量决定，脚本不得切换库。
- 已成功执行的版本文件**不得再修改**（校验和会漂移）；修正与回滚都作为新的更高版本提交，回滚脚本照现行规范评审。
- 初始化 DML 继续按"一服务一个文件"放在 `db/seed/<service>-initial-data.sql`，只允许 DML，由 `seed` 子命令在迁移到目标版本后应用一次并记录。之后的数据变更进入版本化迁移，不修改该文件。
- 本步只定义目录约定与命令，不迁移任何现有 `docs/database/sql/*.sql`，不生成任何服务的 `V1__baseline.sql`。

## 2. 记录表

执行器在每个服务库创建两张记录表（`CREATE TABLE IF NOT EXISTS`，仅 `migrate` / `seed` 会创建）；这是唯一允许由程序创建的表，业务代码不得读写。

| 表 | 列 |
|---|---|
| `t_schema_migration` | `version INT PK`、`description VARCHAR(200)`、`script VARCHAR(255)`、`checksum CHAR(64)`、`installed_by VARCHAR(100)`、`installed_at DATETIME(6)`（UTC）、`execution_ms INT`、`success TINYINT(1)`、`error_message VARCHAR(1000) NULL` |
| `t_schema_seed` | `seed_file VARCHAR(255) PK`、`checksum CHAR(64)`、`installed_at DATETIME(6)`（UTC） |

- `checksum`：去 UTF-8 BOM、把 CRLF 与单独 CR 归一化为 LF 后，对 UTF-8 字节取 SHA-256，小写十六进制。
- `installed_by` 为执行账号（`PAYTECH_DB_USER_<SERVICE>`）；`installed_at` 取数据库 `UTC_TIMESTAMP(6)`。
- `error_message` 记录失败语句序号、起始行号、SQLState 与数据库错误摘要（任务卡列出的列之外新增，见报告待确认项）。
- 两张表的 DDL 与完整中文注释见 `SchemaMigrationRepositoryImpl`；遵守 DATABASE_DESIGN_GUIDE 的 `t_` 前缀、`DATETIME(6)` UTC、无 CHECK / 外键。作为执行器自有技术表，不带 `create_time/update_time`。

## 3. 命令

```text
paytech-migration-runner <plan|validate|migrate|seed> --manifest <instance.yaml> --sql-root <paytech dir>
                         [--service <name>] [--target <version>] [--json]
```

- 处理范围：清单 `versions.schema` 中登记的服务，按清单顺序**串行**处理；`--service` 只处理一个。服务目录为 `<sql-root>/<service>`。
- 目标版本：清单 `versions.schema.<service>`。`--target` 只用于 `migrate`，必须同时指定 `--service`，且不得高于清单版本；目录校验（超前文件、目标文件存在）仍以清单版本为准。

| 子命令 | 行为 |
|---|---|
| `plan` | 只读，不创建记录表：列出当前版本、目标版本与待执行文件，并给出与 `validate` 相同的迁移校验结果。 |
| `validate` | 只读：校验目录（文件名、版本唯一且连续、无高于目标版本的超前文件、目标文件存在、脚本可切分且不含禁止语句），校验记录（无失败记录；目标版本及以下的成功记录与文件的校验和、文件名一致；无中间缺口），以及已执行初始化数据的校验和。任何一项不满足即失败并逐条列出。 |
| `migrate` | `SELECT GET_LOCK('paytech_schema_migration_<db>', 30)` 串行化同库的多个执行器实例；取锁后重新读记录并计算计划，有校验失败则不执行任何语句；按版本顺序逐文件、逐语句（顶层分号切分，识别字符串、`--`/`#` 行注释、块注释与反引号）执行。每个版本成功写 `success=1`；某条语句失败即停止，写 `success=0` 与错误，退出码 2。DDL 不可事务，失败版本中已执行的语句不会回滚。 |
| `seed` | 与 `migrate` 共用迁移锁；要求 Schema 已到目标版本且无校验失败。首次执行时在**一个事务**内执行全部 DML 并写 `t_schema_seed`，任一语句失败整体回滚；已有记录时只校验校验和（一致为 `ALREADY_APPLIED`，不一致为校验失败）；服务没有该文件时为 `NO_SEED_FILE`。初始化数据含 DDL 是校验失败。 |

退出码：`0` 成功，`1` 校验失败，`2` 执行失败，`3` 参数或环境错误（清单不合法、缺环境变量、无法连接、库名不符）。多服务时取最大值；某服务执行失败后不再处理后续服务，校验失败的服务不执行任何语句、后续服务继续。

输出：默认人读文本；`--json` 输出结构化结果（每服务 `currentVersion`、`targetVersion`、`pending`、`executed`、`violations`、`failure`、`seed`、`exitCode`）。输出只含服务名、库名、版本、文件名与错误摘要，不打印密码与 JDBC URL。

### 3.1 连接信息

连接信息**不在清单中**（D22：运维平面不持任何实例数据库凭据），每个服务从环境变量读取，`<SERVICE>` 为服务名的大写下划线形式（`channel-connector` → `CHANNEL_CONNECTOR`）：

| 变量 | 说明 |
|---|---|
| `PAYTECH_DB_URL_<SERVICE>` | JDBC URL，必须选中该服务库 |
| `PAYTECH_DB_USER_<SERVICE>` | 账号，同时写入 `installed_by` |
| `PAYTECH_DB_PASSWORD_<SERVICE>` | 密码 |
| `PAYTECH_DB_SCHEMA_<SERVICE>` | 可选：物理库名与清单 `services.<service>.db` 不同时声明（如 `mor_platform_e2e`） |

缺失任一必填变量时一次列出全部缺失的变量名，退出码 3。连接后 `DATABASE()` 必须等于清单 db（或 `PAYTECH_DB_SCHEMA_<SERVICE>`），否则拒绝执行，防止把一个服务的迁移执行到另一个库。

### 3.2 示例

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd paytech && ./mvnw -B package -pl tools/migration-runner -am
export PAYTECH_DB_URL_LEDGER='jdbc:mysql://127.0.0.1:13306/mor_ledger?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export PAYTECH_DB_USER_LEDGER=ledger_app PAYTECH_DB_PASSWORD_LEDGER=...   # 从安全配置注入，不写入文件
java -jar tools/migration-runner/target/paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar plan     --manifest instance.yaml --sql-root .
java -jar tools/migration-runner/target/paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar migrate  --manifest instance.yaml --sql-root .
java -jar tools/migration-runner/target/paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar validate --manifest instance.yaml --sql-root . --json
java -jar tools/migration-runner/target/paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar seed     --manifest instance.yaml --sql-root .
java -jar tools/migration-runner/target/paytech-migration-runner-1.0.0-SNAPSHOT-cli.jar migrate  --manifest instance.yaml --sql-root . --service ledger --target 6
```

### 3.3 失败后的人工恢复

`success=0` 的记录会让后续 `migrate` / `seed` / `validate` 报 `FAILED_MIGRATION`。按现行规范人工处理：评估并把库恢复到失败版本执行前的状态（或手工补完），确认后删除该失败行（`DELETE FROM t_schema_migration WHERE version = <n> AND success = 0`），修正后的脚本经评审后再执行。失败版本从未成功执行，修正其文件不构成校验和漂移。

## 4. 与 DATABASE_DESIGN_GUIDE 的关系

- 不变：PDManer 是设计源；SQL 人工评审；不创建 CHECK / 外键；应用启动不建表、不迁移、不灌数据；执行顺序与回滚方案仍在评审记录中说明。
- 变化（D11）："当前 Schema DDL + 一份初始化 DML，人工执行"演进为"`V1__baseline.sql` + 递增版本化迁移 + 一份初始化 DML，由本执行器按实例执行并记录"。执行器是唯一可以执行评审 SQL 的程序，它不属于任何业务应用的启动过程。
- 本步不修改 `agent-docs/DATABASE_DESIGN_GUIDE.md`、`TECHNOLOGY_STACK.md`（"Schema 演进：人工审查并执行的 SQL"一行）、`DEVELOPMENT_CONVENTIONS.md` 与 `AGENTS.md` 的 Manual infrastructure rule；规范修订列为后续待确认项。

## 5. 与运维平面的接口（D22，未实现）

运维平面只下发版本与脚本包（含实例清单），不持数据库凭据；实例内由本执行器执行。回传日志的约定接口为 `--json` 的标准输出与退出码：运维平面的实例代理采集该 JSON（每服务的版本、已执行文件、校验失败与执行失败），不需要读取数据库。兼容预检可在升级前以 `plan --json` 获得。本步不实现任何上传、拉取或远程调用。

## 6. 测试

`./mvnw -B verify` 运行 `auto` 测试：

- `SqlScriptSplitterAutoTest`、`ChecksumCalculatorAutoTest`、`MigrationPlannerAutoTest`：纯 Java。
- `MigrationExecutorAutoTest`：经真实命令行入口，在 `local-environment` 的 Foundation 测试库 `paytech_foundation_test`（账号 `foundation_test`，密码取 `<paytech.local-environment.dir>/.env.local` 的 `PAYTECH_FOUNDATION_TEST_DB_PASSWORD`）中只建 `t_mr_test_*` 表与两张记录表，每例前后删除。MySQL 不可用或 `.env.local` 缺失时测试失败并给出启动说明，不跳过。在 git worktree 中运行时以 `-Dpaytech.local-environment.dir=<含 .env.local 的目录>` 指定。
