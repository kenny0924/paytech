# 阶段 4 任务卡：tax 结账算税与事件登记

- 授权（用户 2026-10-29"执行吧"）：阶段 4 研发；允许对**本机** `mor_tax`（root 删表重建 + 初始化）与 `mor_acquiring`（前滚 / 回滚迁移）执行 SQL 以支撑 auto 测试。**不提交、不推送、不部署、不碰 mor-test、不改其他服务的本机库结构。**
- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-tax`（基于 main `e3e0c12f`，detached）。所有代码只改这里，不改主工作树 `/Users/kenny/Desktop/Workspace/Code-MoR`。
- 设计依据（只读，主工作树）：
  - 需求 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261029/tax-结账算税与事件登记/requirement.md`（AC-01—AC-14）
  - 技术方案同目录 `technique.md`（第 7—17 节为已确认方案；第 2 节 F-xx 为现状证据）
  - 原型同目录 `prototypes/`（运营页 16—21）
  - 决定 `/Users/kenny/Desktop/Workspace/Code-MoR/agent-docs/decisions/2026-10-29-tax-domain-fact-service.md`
  - 字段语义 `/Users/kenny/Desktop/Workspace/Code-MoR/design-discuss/tax/database.md`、`regional-policies.md`
- 规范（worktree 内）：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md`。开始写代码前实际读取。

## 1. 分工

| 编号 | Agent | 范围 | 前置 |
|---|---|---|---|
| W1 | tax | `paytech/tax/**` 全部；技术方案第 8.1、8.4（tax 部分）、9、10.1、10.4、11.4—11.5 节；`tools/arch-rules` 中 tax 的表归属；foundation `events.yaml` 中 `OrderPaymentSucceeded` 的 consumers 加 `tax`；本机 `mor_tax` 重建 | 无。**第一步先交付 `paytech-tax-api`（接口、请求 / 响应 Body、枚举）并编译通过**，完成后写 `verification/stage4/tax-api-ready.md`（列出类名与 Path），供 W3、W4 使用 |
| W2 | billing / account / ledger | 技术方案 8.3、11.2、11.3 节：billing Reader 读新字段、Assembler 逐组件 `SELLER_TAX`、本金减税、Invariants、`FactRefType.TAX_CALCULATION`；account `FactBalanceMappingRule` CHANNEL 付方；ledger 只补测试 | 无（按 11.1 节字段契约开发，旧载荷兼容） |
| W3 | acquiring | 技术方案 8.2、8.4（acquiring 部分）、10.2、11.1 节与 S-03—S-07：tax HTTP Client 替换零税 Mock、建单暂估、SaveConsumer 重算与合同重渲染、支付门禁、新表与迁移、事件可空新增字段与 Payload Schema 文件、MOCK 商业配置补 MoR 角色绑定、测试改用 tax DTO Mock 端点 | 可先做不依赖 tax-api 的部分；HTTP Client 等 `tax-api-ready.md` 出现后再接 |
| W4 | ops-bff / ops-web | 技术方案 10.3、12（只写注册 SQL 文件，不执行）、13 节：税务查询页按原型 16—21 实现 | `tax-api-ready.md`（由主会话在 W1 交付后派发） |

## 2. 共享文件归属（避免互相覆盖）

- `paytech/foundation/paytech-messaging/catalog/events.yaml`：只由 W1 修改（`OrderPaymentSucceeded.consumers` 加 `tax`，注释说明可空新增字段）。W3 只改 `paytech-acquiring-api` 下的 Payload Schema 文件。
- `tools/arch-rules/src/main/resources/table-ownership.yaml`：W1 只改 `tax:` 段，W3 只改 `acquiring:` 段；**手工编辑，不运行 `generate-table-ownership.mjs`**（会整文件重写）。
- 根 `pom.xml` / BOM：如需新增依赖版本，先在各自报告里列出，由主会话统一处理；不要擅自改其他服务的 pom。
- 文档（`CURRENT_IMPLEMENTATION.md`、需求目录 `technique.md`）：agent 不改，由主会话汇总。各自报告写到 `verification/stage4/<W编号>-report.md`（主工作树需求目录下）。

## 3. 构建与测试纪律

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven 离线：`./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ ...`。
- **禁止 `install` / `deploy`**（会污染其他会话共用的本地仓库）。用 `-pl <模块> -am` 在 reactor 内解析兄弟模块。
- **禁止全 reactor 测试**（auto 测试会清空共享本机库）。只跑本模块测试：`-pl <你的模块>-test -am -Dtest='com/paytech/<模块>/**/*' -Dsurefire.failIfNoSpecifiedTests=false`。全 reactor 只允许 `test-compile`。
- **本机数据库只用容器 `mor-local-mysql-1`（端口 13306）。本机同时运行着 `mor-test-*` 容器（远端测试环境的镜像 / 隧道），严禁连接或执行任何命令。**（2026-10-29 W2 曾误选 `mor-test-mysql-1`，root 登录被拒、未执行语句。）
- 测试用 `package` 而非 `test` 阶段运行（`-am` 下 test 阶段会带入 paytech-messaging 的 test-classes，其配置执行 DROP TABLE，DML 账号会失败）；RabbitMQ 需从 `.env.local` 导出 `MOR_RABBITMQ_USER/PASSWORD/VHOST`。
- 本机密码从 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local` 读取需要的键（MySQL 13306、Redis 16379、RabbitMQ），不打印到输出或报告。
- 测试规范：真实生产链路 + 本机 MySQL / Redis / RabbitMQ；外部 HTTP 依赖用测试端点 + DTO Mocker（acquiring 已有 `MockPaymentIntentApi` 等先例）；禁止 `when(...).thenReturn`、`@MockBean`；测试优先用枚举常量而非字符串字面量；Processor 的 `processRequest` 不写 JavaDoc（目的写在类注释）。
- 不要后台挂起等待事件；所有构建、测试在前台执行。卡住超过 10 分钟就在报告里写明阻塞点并停止。

## 4. 完成标准（每个 W 的报告必须包含）

1. 改动文件清单（按模块）。
2. 实际执行的构建 / 测试命令与结果（通过数 / 失败数），对照的验收标准编号。
3. 本机执行过的 SQL（库、文件、结果），以及回滚是否验证过。
4. 偏离技术方案之处及原因（没有就写"无"）；发现方案缺陷时**先停在该点写清楚**，不要自行改设计。
5. 未完成项与建议。
