# 任务卡 10 · 实例内迁移执行器骨架

- 分支：`refactor/step-10-migration-runner`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step10`；起点：`refactor-main@36c0506`（第 00 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 10 步；决定 D11、D22；需求 [requirement.md](../requirement.md) 2.2 第 10 步。

## 1. 输入文档（先完整读取）

- `agent-docs/decisions/2026-10-17-target-architecture-v2.md` 中 D11、D22；[design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 第 1、5 节（`tools/`、实例清单 `versions.schema`）。
- `agent-docs/DATABASE_DESIGN_GUIDE.md`（评审 SQL、手工应用、回滚记录的现行原则——本步把"执行"工具化，不改变"评审"原则）、`agent-docs/TECHNOLOGY_STACK.md` 数据库章节、`AGENTS.md` 的 Manual infrastructure rule。
- 现有 SQL 布局样例：`platform-support-service/docs/database/sql/`（`platform-support-schema.sql`、`platform-support-initial-data.sql`、`changes/2026-*.sql`）、`finance-service/docs/database/sql/`。
- 第 00 步产物：`paytech/tools/instance-manifest`（清单模型与校验器，本工具复用其解析结果）。
- `local-environment/mysql/init/01-create-databases-and-users.sh`（Foundation 测试库名与账号）、`local-environment/.env.example`。
- 仓库规范：`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 2. 设计约定（本步定稿并写入 `paytech/tools/migration-runner/README.md`）

### 2.1 版本化 SQL 目录

每个拥有数据库的服务：`paytech/<service>/db/migrations/V<version>__<kebab-description>.sql`，`<version>` 为从 1 起的正整数，与实例清单 `versions.schema.<service>` 同义；同一服务版本号唯一且连续。文件内容是评审后的纯 SQL（MySQL 8.4），允许多条语句，不允许 `DELIMITER`、存储过程、`USE`。首个版本 `V1__baseline.sql` 为该服务当前完整 Schema（由各搬迁步骤从 `*-schema.sql` 切片生成，本步不生成任何服务的 V1）。初始化 DML 继续按现行规则放在一个文件 `paytech/<service>/db/seed/<service>-initial-data.sql`，由执行器的 `seed` 子命令在首次迁移后应用一次并记录；**本步只定义目录约定与命令，不迁移任何现有 SQL。**

### 2.2 记录表

每个服务库一张 `t_schema_migration`（执行器自己创建，是唯一允许由程序创建的表）：`version INT PK`、`description VARCHAR(200)`、`script VARCHAR(255)`、`checksum CHAR(64)`（SHA-256，按 LF 归一化）、`installed_by VARCHAR(100)`、`installed_at DATETIME(6)`、`execution_ms INT`、`success TINYINT(1)`；另一张 `t_schema_seed`（`seed_file`、`checksum`、`installed_at`）。表注释写明由 `paytech-migration-runner` 维护。

### 2.3 行为

- `plan --manifest <yaml> --sql-root <paytech dir> [--service <name>]`：列出每个服务的当前版本、目标版本（清单）与待执行文件；不连接写操作。
- `validate`：校验已执行版本的 checksum 与文件一致，不一致即失败并列出；校验目录中版本连续、无重复、无高于目标版本的"超前文件"。
- `migrate [--target <version>]`：按版本顺序逐文件执行；执行前 `SELECT GET_LOCK('paytech_schema_migration_<db>', 30)` 串行化多实例并发；每条语句单独执行（DDL 不可事务，失败即停止，记录 `success=0` 与错误，退出码 2）；成功写记录；不支持回滚命令——回滚脚本按现行规范作为新的更高版本提交。
- `seed`：首次且仅一次应用 `db/seed/*.sql`，重复执行只校验 checksum。
- 连接信息**不在清单中**：由环境变量 `PAYTECH_DB_URL_<SERVICE_UPPER_SNAKE>`、`PAYTECH_DB_USER_...`、`PAYTECH_DB_PASSWORD_...` 提供，缺失即报错并指出缺的变量名；日志不打印密码与完整 URL。
- 输出：人读文本与 `--json` 两种；退出码 0 成功 / 1 校验失败 / 2 执行失败 / 3 参数或环境错误。
- 不接运维平面、不做远程下载、不做并行多库执行（D22 的回传日志只在 README 留接口说明）。

## 3. 新建清单

- `paytech/tools/migration-runner`（`com.paytech:paytech-migration-runner`，可执行 JAR，`com.paytech.migration`），父 POM `paytech-parent`；依赖只有 `mysql-connector-j`（BOM 版本）、`paytech-instance-manifest`、Jackson（输出 JSON）；**不用 Spring Boot、不引入 Flyway / Liquibase**；用 `maven-shade-plugin` 或 Spring Boot 之外的方式打出 `paytech-migration-runner-<ver>-cli.jar`。
- 结构：`cli/MigrationRunnerCli`、`model/`（`MigrationScript`、`AppliedMigration`、`MigrationPlan`、`Violation`）、`sql/SqlScriptSplitter`（`interface + Impl`，识别字符串、行注释 `--`、块注释、反引号，按 `;` 切分）、`repository/SchemaMigrationRepository`（`interface + Impl`，JDBC）、`service/MigrationPlanner`、`MigrationExecutor`、`ChecksumCalculator`（均 `interface + Impl`）。
- `paytech/pom.xml` 取消 `tools/migration-runner` 注释。
- `paytech/tools/migration-runner/README.md`：第 2 节约定全文、命令示例、与 DATABASE_DESIGN_GUIDE 的关系（本步不改该文档，列为后续规范修订项）。

## 4. 测试要求

- `auto/`：`SqlScriptSplitterAutoTest`（含字符串内分号、注释内分号、反引号标识符、多行语句）、`ChecksumCalculatorAutoTest`（CRLF / LF 一致）、`MigrationPlannerAutoTest`（版本不连续、重复、超前、checksum 漂移各一例）、`MigrationExecutorAutoTest`：使用 `local-environment` 的 Foundation 测试库（库名与账号见 `01-create-databases-and-users.sh`，密码从 `local-environment/.env.local` 的 `PAYTECH_FOUNDATION_TEST_DB_PASSWORD` 读取，统一经系统属性 `paytech.local-environment.dir` 定位；不可用时**失败并给出启动说明**），在该库内以前缀 `t_mr_test_` 的表做真实迁移：V1、V2 成功；V3 含故意错误 → 停止、记录失败、退出码 2；再次运行 `validate` 报告失败行；并发两个执行器实例只有一个执行；每个测试结束清理 `t_mr_test_*` 与两张记录表中的测试行。`t_schema_migration` 的 DDL 在测试库中由执行器创建，测试后删除。
- 测试 fixture SQL 放 `src/test/resources/migrations/<case>/`，样例清单复用 `paytech-instance-manifest` 的测试清单或自带一份最小清单。

## 5. 禁止事项

- 不对任何 `mor_*` 业务库执行任何语句；测试只触碰 Foundation 测试库。
- 不迁移、不改动任何现有 `docs/database/sql/*.sql`；不生成任何服务的 `V1__baseline.sql`。
- 不改 `agent-docs/`（规范修订列为待确认项）。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-10): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/10-migration-runner-report.md`（随分支提交）：改动摘要 ≤ 30 行、`./mvnw -B verify` 结果与测试数、CLI 在 Foundation 测试库上的一次 `plan / migrate / validate` 实录、待确认项（seed 处理、与 `test-environment/scripts/initialize.py` 的关系、规范修订）。
3. 最终回复两行：报告路径 + 一句话结论。
