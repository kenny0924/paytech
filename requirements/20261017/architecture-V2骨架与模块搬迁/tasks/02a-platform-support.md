# 任务卡 02a · Platform Support 搬入并拆 api / core / app / test

- 分支：`refactor/step-02a-platform`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step02a`；起点：`refactor-main@0857ad7`（第 00、01、09、10 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 02a 步；D26（Platform 为内部 Token 签发方）；C5（解除 AMQP 禁令）。
- 并行：与 02b、02c、02d 同时进行。**不改** `test-environment/scripts/build.sh`、`test_deploy_script.py`（四步合入后由主会话统一改）。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文；[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节 platform-support 行；[design/02-archunit-rules.md](../design/02-archunit-rules.md)。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.2、1.3、1.4、1.5（Platform 行）、2.1、2.2、3.6（第 24、124 行）、3.9、4.3（platform-support 生成器）、第 5 节（PDManer 规范路径）、第 6 节（文档链接，本步只改 Platform 自己目录内的链接）。
- [analysis/retained-systems.md](../analysis/retained-systems.md) 第 1、2、4、7、8 节。
- 第 00、01 步报告：`tasks/00-skeleton-report.md`、`tasks/01-foundation-report.md`（`paytech-internal-auth` 的公开 API、`paytech-messaging` 依赖方式、`paytech.local-environment.dir` 用法）。
- 现有 `platform-support-service/` 全部 POM、`platform-support-web` 的包结构（`configuration` 18、`core` 8、`integration` 2、`ext` 59、`service` 73 个文件）、`platform-support-test/pom.xml`（覆盖率 antrun 第 101–160 行）、`LocalMockEnvironmentInitializer`、`application-mock.yml`。
- JWKS 现状：`platform-support-access-control` 的 `JwtSigningKey`、`OperationsSigningKey`、`JwtSigningKeyMapper`、`CustomerJwksService(Impl)`，`platform-support-api` 的 `authentication/{Operations,Customer}JwksQueryApi`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 2. 模块映射（`git mv`，目录名即 artifactId）

| 现模块 | 目标（`paytech/platform-support/`） | 角色 |
|---|---|---|
| `platform-support-api` | `paytech-platform-support-api` | api |
| `platform-support-access-control` | `paytech-platform-support-access-control` | core（业务模块） |
| `platform-support-audit-security` | `paytech-platform-support-audit-security` | core |
| `platform-support-file-document` | `paytech-platform-support-file-document` | core |
| `platform-support-delivery` | `paytech-platform-support-delivery` | core |
| `platform-support-integration` | `paytech-platform-support-integration` | core |
| `platform-support-web` 的 `core`、`service`、`integration` 包 | **新建** `paytech-platform-support-core`（包 `com.paytech.platform.support.core`、`.service`，`integration` 包改名 `.core.integration` 以消除与 integration 模块的同包，01 §8） | core（应用编排层：Processor、跨业务模块 Service、出站适配） |
| `platform-support-web` 的 `PlatformSupportApplication`、`configuration`、`ext`（Controller、MQ / Job 入口） | `paytech-platform-support-app`（`spring-boot-maven-plugin` 在此） | app |
| `platform-support-test` | `paytech-platform-support-test` | test |
| `pom.xml`、`README.md`、`docs/` | `paytech/platform-support/pom.xml`、`README.md`、`docs/` | — |

删除 `platform-support-service/mvnw`；搬完后 `platform-support-service/` 消失。`paytech/pom.xml` 取消 `platform-support` 注释（只动这一行）。dependencyManagement 的 8 个内部 artifact 改为新 9 个坐标，版本用 `${project.version}`。

## 3. 代码改动清单

1. **父 POM 与 Enforcer**：服务聚合 POM `<parent>` 用 `../build/paytech-parent/pom.xml`；`bannedDependencies` 原样搬入，**删除两条 AMQP 禁令**（C5）；`failIfNoSpecifiedTests` 聚合 `false`、test 模块 `true` 保留。
2. **覆盖率门禁**（09 §1.2）：antrun `verify-task-nine-coverage` 整段移入 Profile `coverage-gate`，fileset 改为 `${project.basedir}/../paytech-platform-support-{api,access-control,audit-security,file-document,delivery,integration,core,app}/…`；默认 `verify` 不执行。
3. **测试锚点**（09 §2.1、2.2）：`LocalMockEnvironmentInitializer` 改读系统属性 `paytech.local-environment.dir`（缺失时按 10 §4.4 回退并给出明确报错），删除按目录名向上搜索；`OnboardingFileTestBase`、`LocalMockEnvironmentInitializerTest` 同步；`application-mock.yml` 的 `${mor.local-environment:…}` 回退改为 `${paytech.local-environment.dir}`。
4. **AMQP 与 messaging**：`paytech-platform-support-app` 依赖 `paytech-messaging`，`-core` 层依赖 `paytech-messaging-api`；本步**不实现任何消费者**，只保证装配与启动（`messaging.*` 配置存在但无队列声明）。
5. **内部签名 Token 签发端（D26）**：
   - `paytech-platform-support-api` 新增 `internalauth/InternalTokenIssueApi`（`POST /platform/v1/internal-tokens`，请求 / 响应复用 `paytech-internal-auth` 的 `contract` 类或在 api 中定义等价 DTO）与 `internalauth/InternalJwksQueryApi`（`GET /platform/v1/internal-auth/jwks`）。
   - 签名密钥沿用 access-control 现有 JWT 签名密钥管理方式（`JwtSigningKey` 表 / Service）新增用途 `INTERNAL`（ES256 密钥对、轮换语义与现有一致）；若现表以列区分用途，则加列须经评审 SQL——**本步不执行 SQL**，若必须改表就在报告中给出 SQL 草案并把该能力标为"待 Schema 变更"，签发端用现有 Operations 密钥对先行实现并以配置 `internal-auth.issuer.key-usage` 切换。
   - 调用方身份：签发请求本身必须来自已认证的内部调用方。阶段 0 用 `internal-auth.mode=DUAL` 下的 `X-Caller-Service` 白名单 `internal-auth.issuer.callers`（服务名 → 允许的 target 列表与 purpose 列表）校验；签发 `sub = 调用方`，`exp ≤ 300 s`，每次签发写审计（现有审计模块）。
   - Platform 自身入站：`internal-auth.mode` 默认 `OFF`；在 `mock` Profile 的测试里以 `DUAL` 与 `REQUIRED` 各覆盖一遍 Webhook / Notification 入口。
   - Platform 出站（调 Payment 读卡、调 CCT 等）：接入 `paytech-http-client-support` 的出站拦截器，`internal-auth.mode=OFF` 时不附加 Token。
6. **其他路径修复**：`test-environment/scripts/initialize.py` 第 24、124 行、`test_risk_rule_configuration_catalog.py` 第 21 行改为 `paytech/platform-support/docs/database/sql/...`；`scripts/pdmaner/platform-support/generate-platform-support-model.mjs` 输出路径与 `pdmaner-canonical-paths.test.mjs` 中 Platform 路径改为 `paytech/platform-support/docs/database/platform-support.pdma.json`；运行 `node --test scripts/pdmaner/**` 中与 Platform 相关的测试。
7. **文档**：`paytech/platform-support/README.md` 与 `docs/` 内指向仓库级目录的相对链接按新深度修正；仓库级文档指向 Platform 的链接**不改**（第 13 步统一）。
8. **ArchUnit**：`paytech-platform-support-test` 新增 `ArchitectureAutoTest` 引入 `paytech-arch-rules` 执行全部规则；多 core 模块的 R1 以"同服务内 core 可互相依赖"为准（规则库若不支持需在 arch-rules 中加服务内例外并补测试）。

## 4. 测试与验证

1. 搬迁前记录 `cd platform-support-service && ./mvnw -B verify -DskipITs` 的测试总数（覆盖率门禁已知失败，用 `-Dmaven.test.failure.ignore=false` 之外的方式只记录测试数，例如 `-pl platform-support-test -am` 先 `test`）。
2. 搬迁后 `cd paytech && ./mvnw -B verify -pl platform-support/paytech-platform-support-test -am` 全绿，测试数 ≥ 基线 + 新增（Token 签发 / JWKS / 三模式入站 / 消息装配启动）；`-Pcoverage-gate` 单独运行并记录结果（允许失败，既有问题）。
3. 全 Reactor `cd paytech && ./mvnw -B verify` 全绿。
4. `-app` 以 `mock` Profile 启动到 `/actuator/health` UP（本地 base Profile 在跑时）。
5. `node --test scripts/pdmaner/...`（Platform 相关）、`python3 -m unittest test_risk_rule_configuration_catalog`（如可独立运行）记录结果。
6. `git log --follow` 抽查 `paytech-platform-support-access-control/pom.xml` 与一个 Java 文件；`platform-support-service/` 不存在。

## 5. 禁止事项

- 不改业务行为（Token 签发端是新增能力，不改既有接口语义）；不改其他服务目录；不改 `local-environment`；不执行 SQL。
- 不实现 MQ 消费者、Webhook 投递改造（D07 后续切片）。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-02a): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/02a-platform-support-report.md`：改动摘要 ≤ 30 行、第 4 节逐项命令与结果、新 api 合同清单、密钥用途的处理方式与 SQL 草案（如有）、待确认项。
3. 最终回复两行：报告路径 + 一句话结论。
