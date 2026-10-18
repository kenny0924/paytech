# 任务卡 01 · 阶段 1：mor-test 数据迁移方案、脚本与本机演练

- 分支：`test-env/v2-migration-stage1`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-mt1`；起点 `main@0092aa05`。
- 目标：产出可评审、可重复执行的迁移与部署材料，并在本机演练库上证明"旧数据 → V2 库 → V2 服务能起、能读"。**不触碰 mor-test 的任何容器与卷**；mor-test 的 MySQL 转储由主会话提供（只读使用，不复制进仓库，不打印任何值）。

## 1. 输入

- 本需求 `requirement.md`；mor-test 转储：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/d1bc4b55-586b-45ae-abbc-d503d0fde214/scratchpad/mor-test-dump-20261018/{mor_platform,mor_risk,mor_cct,mor_commerce,mor_vault}.sql`（含 CREATE DATABASE）。
- V2 归属与更名：`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`、`requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md`、各步报告（02a Platform、02d Vault、03 acquiring/consumer、04 client-master/commercial、06 risk-decision/compliance）中的"更名表 / 过渡 / 删除列"小节；V2 Schema：各 `paytech/<svc>/docs/database/sql/<svc>-schema.sql` 与 `*-initial-data.sql`；Platform：`paytech/platform-support/docs/database/sql/platform-support-schema.sql` 及 `changes/`；Vault：`paytech/vault/**/schema*.sql`。
- 部署材料：`test-environment/compose.yaml`、`compose.applications.yaml`、`scripts/{build.sh,deploy.sh,initialize.py,prepare.py}`、第 13 步报告第 6 节（启动顺序、资源）、第 14 步报告（本机全链路的配置要点：Nacos namespace、Vault 调用方规则、Gateway Target、XXL）、`agent-docs/CURRENT_IMPLEMENTATION.md` 的"mor-test V2 部署"Pending。
- 规范：`AGENTS.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`（手工 SQL、记录执行顺序与回滚）、`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`。

## 2. 交付

1. `design/01-migration-map.md`：逐表映射（旧库.表 → 新库.表；更名；新增 NOT NULL 列的回填规则；丢弃的表 / 列及理由；跨库引用处理），以及 Platform / Vault 的 Schema 升级差异清单（用演练库 `SHOW CREATE TABLE` 与 V2 Schema 逐表比对得出，不凭记忆）。
2. `sql/`：按顺序编号的评审 SQL：`10-create-v2-databases.sql`（库与最小权限账号，密码用 `.env.test` 变量占位，由脚本注入）、`20-platform-upgrade.sql`、`21-vault-upgrade.sql`、`30-migrate-cct.sql`、`31-migrate-commerce.sql`、`32-migrate-risk.sql`（`INSERT ... SELECT` 跨库同实例）、`40-verify-counts.sql`（迁移前后行数与唯一键核对）、每个文件头部写影响与回滚。
3. `scripts/migrate.sh`：对给定 MySQL（host/port/root 口令经环境变量）按顺序执行，带 `plan|verify|apply`，每步前核对前置状态；`scripts/rehearse.sh`：起一个一次性 MySQL 8 容器（端口 23307，容器名 `mor-mt1-mysql`，用完删除），导入转储，执行 migrate，输出核对结果。
4. compose 内存调优：`compose.applications.yaml` 的 Java 服务 `mem_limit` 与 `JAVA_TOOL_OPTIONS`/`-Xmx` 按服务分级（Gateway / Platform 大，其余小），给出总上限表；不停 nexus 与 mor-local 的前提下核对 Docker 当前内存（主会话已调大，用 `docker info` 读）能容纳。
5. 演练：在演练库上跑完 migrate 后，以 `mock`/test Profile 启动 platform-support、client-master、commercial、acquiring、consumer、risk-decision、compliance、vault（Go 二进制或容器）连演练库，`/actuator/health` UP，并对 Platform 的外部 API 配置、Gateway 路由、运营账号、风控规则、客户申请做只读查询抽样核对（SQL 行数 + 一次 HTTP 读）。记录进 `tasks/01-report.md`。
6. `runbook.md`：阶段 2 的逐步操作清单（备份卷命令、停旧服务、迁移、`build.sh`、启动顺序、Nacos/XXL/Vault/Gateway 登记、验收脚本、回滚），每步给命令。

## 3. 禁止

- 不连接、不修改 mor-test 的任何容器 / 卷 / `.env.test`；不把转储或其中任何值写入仓库或报告；不调用 Superpowers skill；不提交到 main；不推送。演练容器用完必须删除。
- 提交前缀 `test-env(stage1): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。最终回复两行：commit 哈希 + 演练结论（迁移行数核对、服务 UP 数）。
