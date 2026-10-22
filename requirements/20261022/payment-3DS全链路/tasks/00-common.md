# 阶段 4 公共规则（3DS 与 BIN 两项需求的所有任务卡）

主工作区（只读参考，需求文档所在）：`/Users/kenny/Desktop/Workspace/Code-MoR`。每张卡有自己的工作目录（detached worktree，基于 `4163f9f9`），只在自己的工作目录里改代码。

## 1. 开工前必读（AGENTS.md「接口开发必读与自查」）

实际读取，不凭记忆：
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（含第 11 节交付前核对）
- `agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`
- 涉及数据库时：`agent-docs/DATABASE_DESIGN_GUIDE.md`；涉及补偿 / MQ 时：`agent-docs/COMPENSATION_EVENT_GUIDE.md`
- 本卡对应需求的 `requirement.md`、`technique.md`（第 2、3 节是权威设计）与 `db/` 草案

开工时在报告开头简述：已读路径、所属 Processor / Context、本地原子写入、事务外步骤。

## 2. 硬约束

- **事务公理：** 事务内只操作本库，不调用任何外部服务（HTTP、MQ 发送、Redis 除锁外）。下游查询在事务前，下游操作在提交后。
- 不用 `when(...).thenReturn`、`@MockBean`、spy 等方法级替身；外部 HTTP 用测试端点返回配置的 DTO（见 UNIT_TEST_GUIDE）。
- 不加数据库 FK / CHECK；表、列注释完整；PDManer 模型、Schema SQL、测试 init DDL、DataObject / Mapper 保持一致（模型由 `scripts/pdmaner/*` 生成器维护的，改生成器再生成）。
- 枚举相邻常量之间空一行；Processor 模板方法（processRequest / processWithinLock）不写 JavaDoc（用途写在类注释）；测试引用已有枚举 / 常量，不写字面量。
- 不提交、不推送、不建分支；不改主工作区；不动 `.pen` 文件；不改其他卡负责的模块（遇到需要时在报告里写明）。
- 文档（README、设计修订）只改本卡列出的范围。

## 3. 本机环境与测试

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven 本地仓库 `~/.m3/repository`；用 `./mvnw -o`（在 `paytech/` 下）。
- 本机依赖由仓库 `local-environment` 提供（MySQL 13306、Redis 16379、RabbitMQ）；密码在主工作区 `local-environment/.env.local`，只按需导出所需键，**绝不打印**。
- **不要跑全 reactor 测试**（其他会话共用本机测试库，auto 测试会清数据）：全 reactor 只做 `test-compile`；测试只跑受影响模块（`-pl <module>-test -am` 或 `-Dtest=...`）。
- **本机测试库 SQL 已获用户授权**（2026-10-22）：只可对本卡涉及服务的**本机测试库**执行本卡的增量 DDL / DML。执行前用 `mysqldump`（root，密码取 `MOR_MYSQL_ROOT_PASSWORD`）备份该库到 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/backup/<库名>-<时间>.sql`，在报告中记录库名、备份路径、执行的文件。只做增量（ADD COLUMN / CREATE TABLE / INSERT），不 DROP 既有对象。不得连接 mor-test（23306 等）或任何非本机环境。
- 前台运行命令，不要依赖后台 / Monitor 等待；长构建直接前台执行（timeout 最长 600000 ms，必要时分段）。

## 4. 交付

- 报告写到本卡指定的 `*-report.md`（用 Bash heredoc 写入，路径在主工作区需求目录 `tasks/` 下），包括：已读路径与开工自查、改动文件清单、对应 AC、执行的 SQL 与备份、测试命令与结果（通过 / 失败 / 未运行，附数量）、偏离设计之处及原因、遗留项、WRITE_API_EXAMPLE 第 11 节逐项核对。
- 最后回复 ≤15 行摘要。
