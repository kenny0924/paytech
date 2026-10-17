# 任务卡 13 · 收口：旧根清理、文档链接重写、搬迁状态登记

- 分支：`refactor/step-13-cleanup`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step13`；起点：`refactor-main`（全部搬迁步合入后，主会话填写）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 13 步；设计 [09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 5、6 节；[10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 5 节。
- 串行执行，最后一步。

## 1. 输入文档（先完整读取）

- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 0、5、6、7、8 节与附录；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 5 节（本地环境新增库、Redis 前缀、MQ 命名、三个从不执行的测试类）。
- 全部 `tasks/*-report.md`（每步"待确认"与"未改"项中与文档 / 清理相关的内容是本步输入）。
- `agent-docs/CURRENT_IMPLEMENTATION.md`（状态台账格式）、`agent-docs/README.md`（任务路由表）、`agent-docs/DOCUMENT_GOVERNANCE.md`、`AGENTS.md`（只读；本步**不改**权威正文语义，只修链接目标）。
- `scripts/pdmaner/pdmaner-canonical-paths.test.mjs`、`scripts/verify-local-environment.mjs`、`requirements-site/tests/*.test.mjs`。

## 2. 改动清单

1. **旧根清理**：确认 12 个旧根目录（`paytech-build`、`paytech-foundation`、`platform-support-service`、`api-gateway`、`operations-console-bff`、`client-console-bff`、`vault-service`、`commerce-service`、`client-contract-service`、`payment-orchestration-service`、`risk-compliance-service`、`finance-service`）在 git 中无任何文件；残留文件逐个判断归属后搬入或删除，报告列出。仓库根 `README.md` 的工程结构一节改为 `paytech/` 单 Reactor（`paytech/mvnw`）。
2. **文档链接重写（09 §6.2）**：新增 `scripts/rewrite-moved-links.mjs`（输入映射表 JSON，`--check` / `--write`）与 `scripts/verify-markdown-links.mjs`（全仓 `*.md` 相对链接解析校验，列出 `文件:行:链接`）；映射表按 09 §6.2 第 3 条并以各步报告的最终模块名补全；入向 520 条 / 出向 351 条 / `cd <旧根>` 25 处全部改写；`agent-docs/` 正文**只改链接目标**（不改语义）；`requirements/**/db/<旧根>/` 目录名、`assets/` 证据与 `docs/superpowers/plans/*.md` 中的命令文本不改写（09 §6.2 第 5 条）。`verify-markdown-links.mjs` 在合入前 0 失败，并写入 `requirements-site/package.json` 之外的本地检查说明（不改 requirements-site 测试）。
3. **PDManer 规范路径（09 §5）**：`pdmaner-canonical-paths.test.mjs` 合同清单为 17 个 Java 持库服务 + vault（dispute、payout 以模块库形式登记）；`agent-docs/database/README.md` 的模型路径表更新为新路径（链接修正范围）。
4. **本地环境文档**：`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` 端口表补 18108–18121 与管理端口、analytics Profile（19030 / 18030 / 18091）、新增库与账号清单（10 §5.1）、`paytech.local-environment.dir` 用法、RabbitMQ 一致性哈希插件、`.env.example` 新键——这些是环境事实登记，不是架构规范修订。
5. **搬迁状态登记**：`agent-docs/CURRENT_IMPLEMENTATION.md` 新增一节「V2 骨架与模块搬迁（阶段 0，refactor-main）」：Implemented（24 服务目录、模块坐标、库名、端口）、Verified（各步 verify 数字与全 Reactor 数字、本地库 DDL 应用情况）、Pending（第 11b 步前共库过渡已结束与否、Vault REQUIRED 切换、DUAL 期限、Platform 签发密钥表驱动待 Schema、`t_schema_migration` 规范修订、D11 / D26 / D09 规范修订、`agent-docs` 现行正文替换为 V2 草案的后续需求）、本机一次性操作汇总（各步报告"本机一次性操作"节的合并：新建库、legacy 库、Redis AOF 修复等）。只登记事实，不改规范。
6. **三个从不执行的测试类**（10 §5.5）：核对均已在各步归位到 `auto/`，否则归位。
7. **旧 Topic 残留**：`grep -r "topic_payment_result\|topic_risk_client_review\|cg_commerce_payment_result\|cg_cct_client_review" paytech/ test-environment/ local-environment/` 为零（代码与 compose）；文档中只允许历史记录出现。
8. **test-environment**：`compose.applications.yaml` 的服务名与 `build.sh` 打包清单按最终服务拆分更新（`risk-service` → `risk-decision-service` + `compliance-service`，Finance 五服务、payment / channel-connector / card、acquiring / consumer、client-master / commercial；每个服务一个 `JAR_FILE`），`initialize.py` 的多库初始化、`test_deploy_script.py` 断言同步；**不部署**，只改脚本与 compose 并通过 `python3 -m unittest` 全部测试。`compose.sh build` 的服务列表同步。
9. **`.gitignore` 收口**：各服务目录的 `.gitignore` 只保留服务特有规则，其余并入 `paytech/.gitignore`。

## 3. 验证清单

1. `node scripts/verify-markdown-links.mjs` 0 失败；`node scripts/rewrite-moved-links.mjs --check` 0 待改。
2. `npm --prefix requirements-site ci && npm --prefix requirements-site test` 通过（或只剩与本步无关、在上一步报告已登记的既有失败，逐条列出）。
3. `node --test scripts/pdmaner/*.test.mjs` 与 `node scripts/verify-local-environment.mjs`、`node scripts/verify-java-foundation.mjs`、`node scripts/verify-local-nexus-settings.mjs ~/.m2/settings.xml` 结果记录（既有失败逐条列出并与基线比对）。
4. `cd test-environment/scripts && python3 -m unittest` 全部通过。
5. `cd paytech && ./mvnw -B verify` 全 Reactor 全绿（主会话也会复跑）。
6. `git ls-files | grep -E "^(paytech-build|paytech-foundation|platform-support-service|api-gateway|operations-console-bff|client-console-bff|vault-service|commerce-service|client-contract-service|payment-orchestration-service|risk-compliance-service|finance-service)/"` 为空。

## 4. 禁止事项

- 不改 `agent-docs/` 任何规范语义（只修链接目标与状态台账 / 环境事实登记）；不改业务代码；不执行 SQL；不部署 mor-test。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 5. 交付物

1. 短分支提交，前缀 `refactor(step-13): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/13-cleanup-report.md`：改动摘要 ≤ 30 行、第 3 节逐项、链接改写统计（入向 / 出向 / 命令文本）、CURRENT_IMPLEMENTATION 新节全文、待确认项（含阶段 5 全链路验证需要的 compose 启动清单与资源估算）。
3. 最终回复两行。
