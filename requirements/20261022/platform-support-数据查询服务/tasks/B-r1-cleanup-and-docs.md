# 任务卡 B：R1 归属调整、data-service 骨架清理与文档、本地测试被查库

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery-r1`（detached worktree，基于 main `281a217d`）。**只在这个目录改文件**，不要动主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR` 或 `/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`。
- 授权：普通文件编辑、删除 `paytech/data-service`、运行下面列出的检查。**不提交、不推送、不执行任何 SQL、不启动或修改 local-environment 容器。**
- 权威：需求 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-数据查询服务/requirement.md`（第 9 节 C1—C15），技术方案同目录 `technique.md`（第 1、9、10 节，第 5.1 节“整实例连接”“系统库”）。

## 开工前必读

`AGENTS.md`、`agent-docs/DOCUMENT_GOVERNANCE.md`、`agent-docs/decisions/2026-10-17-target-architecture-v2.md`（D25、D35、D36 及“已知将被后续切片取代”段）、`agent-docs/TECHNOLOGY_STACK.md`（第 3、9 节）、`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`、`local-environment/README.md`、`agent-docs/CURRENT_IMPLEMENTATION.md` 开头的维护规则。参考同类决定记录格式：`/Users/kenny/Desktop/Workspace/Code-MoR-workflow/agent-docs/decisions/2026-10-22-workflow-into-platform-support.md`（只读参考）以及 `agent-docs/decisions/` 中 2026-10-20 的几份。

## 范围

1. **决定记录** `agent-docs/decisions/2026-10-22-data-query-into-platform-support.md`（Confirmed，2026-10-22 用户）：
   - data service 并入 Platform Support，模块 `paytech-platform-support-data-query`，不独立部署（C1、C12）。
   - 修订 D25 / D35 的查询部分：数据源由 CDC + Doris 改为运营可配置的只读库（多为读写分离从库，可连整个实例，C2、C15）；Doris 降为可接入的数据源类型之一；CDC 白名单契约废止（C7）。D25 中“详情与操作前校验实时读 Owner”保留。
   - 受控跨库读取例外（C8）：只经只读账号读取已登记的数据源；仅用于 Operations 查询和工作流验证；结果不作为业务执行依据；系统库不可查询。
   - 不做查询记录（C14）、模板不复核保存即生效（C9）、不绑定权限码（C11）、自研统一模板语法（C13）——简要列出并链接需求。
   - 新依赖：JSqlParser（BOM）与 CodeMirror 6（ops-web 模板编辑器懒加载）。
2. **同步修订（只改相关句子，不重写无关内容）**：
   - `agent-docs/decisions/2026-10-17-target-architecture-v2.md`：在 D25、D35 行或“已知将被取代”段注明“查询部分已由 2026-10-22 决定修订”并链接。
   - `agent-docs/TECHNOLOGY_STACK.md`：第 3 节新增 JSqlParser 行（版本写“由 paytech-dependencies 固定，见任务卡 A 报告”，主会话合并时补具体号）与 CodeMirror 6 说明（放在合适位置，若该表不登记前端库，就在前端相关段落说明）；第 9.1 节“禁止跨数据库查询”后补一句受控例外并链接决定。
   - `AGENTS.md` 的 *Start here* 增加一行路由：改 data-query 数据源、模板语法、只读校验前读本决定和需求目录。保持与现有行同样的风格与长度。
   - `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`：端口表删除 data-service 18120/19120；analytics Profile 段改为“Doris 方言 manual 测试环境（data-query）”，`mor_data_service` / `data_service_app` 保留为被查库与只读账号。
   - `local-environment/README.md`、`local-environment/.env.example`、`local-environment/compose.analytics.yaml`、`local-environment/doris/init/init-doris.sh`、`local-environment/flink/pipelines/demo-currency-spec.yaml`：去掉“CDC 白名单契约 / data-service 服务”的表述，保留容器与账号；Flink 管道注明“仅演示把数据放进 Doris，不再承担契约”。
   - `scripts/verify-local-environment.mjs`：删除 CDC 白名单与 Flink 管道一致性检查（约 :16、:79、:615-629），保留 Doris analytics 的其余检查。
   - `agent-docs/CURRENT_IMPLEMENTATION.md`：:32 一行及 :122、:151 中 data-service 的计数/列表，改为“已删除，并入 Platform data-query（研发中，见需求目录）”。只写事实，不写未发生的部署。
3. **删除骨架与登记**：删除 `paytech/data-service/`；移除 `paytech/pom.xml:56` 模块；`paytech/tools/instance-manifest/src/main/resources/catalog/services.yaml:21`；`paytech/tools/arch-rules/src/main/java/com/paytech/arch/catalog/ServiceCatalog.java:24`；`ModuleModelAutoTest.java:34,52`（同步断言数量）；`paytech/foundation/paytech-messaging/.../catalog/YamlEventCatalog.java:37`。改完 grep 全仓（排除 `requirements/`、`.claude/worktrees/`、`docs/superpowers/` 等历史材料）确认无遗漏引用。
4. **本地测试被查库（Q12）**：
   - 在 `local-environment/mysql/init/` 新增初始化脚本（按现有脚本编号与风格）：建库 `mor_data_query_fixture`（含 2—3 张小表与少量数据，供以后的 HTTP 入口测试用：一张 `t_refund_fixture` 类似退款表，一张可做大表 CROSS JOIN 超时测试的 `t_number_fixture`），账号 `data_query_fixture_reader`（只 SELECT 该库）和 `data_query_fixture_writer`（SELECT/INSERT/UPDATE，用来验证拒绝启用）；密码取环境变量，加入 `.env.example`（示例值占位）。
   - 同时写一份给**已存在的本地 MySQL** 人工执行的 SQL：`local-environment/mysql/manual/2026-10-22-data-query-fixture.sql`（若无 manual 目录，按 local-environment 现有惯例放），文件头写明执行方式、幂等性与回滚。**不要执行。**
   - `local-environment/README.md` 补一段说明。

## 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-dataquery-r1/paytech
./mvnw -q -pl tools/arch-rules,tools/instance-manifest -am install -DskipTests
./mvnw -pl tools/arch-rules,tools/instance-manifest test
./mvnw -pl foundation/paytech-messaging test
cd .. && node --check scripts/verify-local-environment.mjs && npm --prefix requirements-site test
```

不要运行整个 reactor 的测试。`verify-local-environment.mjs` 只做语法检查，不要求本地环境在运行。

## 交付报告（中文）

改动与删除文件清单；全仓 grep 剩余引用及保留理由；各检查命令与实际结果；需要主会话裁决的问题。**完成后直接结束，不要等待后台事件。**
