# 任务卡 02b · API Gateway 搬入

- 分支：`refactor/step-02b-gateway`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step02b`；起点：`refactor-main@0857ad7`（第 00、01、09、10 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 02b 步；D26（WebFlux 版内部 Token 接线）。
- 并行：与 02a、02c、02d 同时进行。**不改** `test-environment/scripts/build.sh`、`test_deploy_script.py`。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文；[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节 api-gateway 行（**无 api / core，只有 `paytech-api-gateway-app` 与 `-test`**，以此为准，09 §1.3 的 core/app/test 写法作废）。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4（Gateway 行）、2.15、3.10、4.2（Gateway 三个脚本）、第 6 节（只改 Gateway 目录内链接）。
- [analysis/retained-systems.md](../analysis/retained-systems.md) 第 1、2、4.2、7 节。
- 第 00、01 步报告（arch-rules 对无 core 服务的处理、`paytech-internal-auth` 的 WebFlux `InternalTokenExchangeFilterFunction` / `InternalAuthWebFilter` 用法）。
- 现有 `api-gateway/` 全部 POM、`scripts/*.mjs`、`api-gateway-web/src/main/resources/application.yml`、`security/ReactiveJwksProvider`、`GatewayProtocol`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 2. 模块映射（`git mv`）

| 现 | 目标（`paytech/api-gateway/`） |
|---|---|
| `api-gateway-web` | `paytech-api-gateway-app` |
| `api-gateway-test` | `paytech-api-gateway-test` |
| `pom.xml`、`README.md`、`scripts/` | 同名 |

删除 `api-gateway/mvnw`、`mvnw.cmd`；搬完后 `api-gateway/` 消失。`paytech/pom.xml` 只取消 `api-gateway` 注释。`spring.application.name`、端口 18080 / 19080 不变。

## 3. 代码改动清单

1. 聚合 POM：`<parent>` 真实相对路径；`bannedDependencies` 原样搬入，**业务制品模式改为** `com.paytech:*-core`、`com.paytech:*-app`（加 `<includes>` 放行 `paytech-api-gateway-app` 自身），删除旧的逐服务 `client-contract-*` 等模式；Servlet / MVC / mvc-web-starter 等禁令保留。
2. 三个脚本（09 §4.2）：`verify-gateway-boundaries.mjs` 路径改 `paytech-api-gateway-app/src/main/java`、`.../application.yml`、`paytech-api-gateway-test/src/test`；`verify-jacoco.mjs` 改 `paytech-api-gateway-test/target/...` 与 `paytech-api-gateway-app/target/classes`；`class-file*.mjs` 随目录。运行 `node api-gateway/scripts/*.test.mjs`（搬后路径）与边界脚本。
3. `test-environment/scripts/test_nginx_gateway_problem_boundary.py` 第 11–13 行路径改 `paytech/api-gateway/paytech-api-gateway-app/src/main/resources/application.yml`。
4. 两个不在 `auto/` 的测试类（`PlatformSnapshotControlClientAutoTest`、`GatewaySnapshotCodeParserAutoTest`）移入 `auto/` 并保证通过（既有问题顺手归位；若失败则修测试本身而非生产代码，并在报告说明）。
5. **内部 Token（WebFlux）接线**：Gateway 调 Platform 的 WebClient（Snapshot、JWKS、Credential 校验）与转发到 BFF 的出站链路挂上 `InternalTokenExchangeFilterFunction`；配置 `internal-auth.mode` 默认 `OFF`（不附加）；`mock` Profile 测试覆盖 `REQUIRED` 时 Token 已附加（用 MockHttpServer 断言 `Authorization` 头）。Gateway 作为公网入口**不**挂入站 `InternalAuthWebFilter`。
6. Redis 环境提示（2.15）不变。`README.md` 与 Gateway 目录内文档链接按新深度修正。
7. `paytech-api-gateway-test` 新增 `ArchitectureAutoTest`（arch-rules；Gateway 为 app + test 形态，R1 / R3 / R5 对它的适用方式按 00 报告口径，必要时在 arch-rules 补"无 core 服务"的测试）。

## 4. 测试与验证

1. 搬迁前 `cd api-gateway && ./mvnw -B verify` 测试总数（文档记录 1277）。
2. 搬迁后 `cd paytech && ./mvnw -B verify -pl api-gateway/paytech-api-gateway-test -am` 全绿，数量 ≥ 基线 + 2 个归位类 + 新增；全 Reactor `verify` 全绿。
3. `node paytech/api-gateway/scripts/verify-gateway-boundaries.mjs`、`verify-jacoco.mjs`（在 verify 之后）通过。
4. `-app` 以 `mock` Profile 启动到 health UP（需要本地 Redis 在跑）。
5. `git log --follow` 抽查两份文件；`api-gateway/` 不存在。

## 5. 禁止事项

- 不改路由、Guard、限流、Snapshot 任何业务语义；不动其他服务目录；不改 `local-environment`。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-02b): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/02b-api-gateway-report.md`：改动摘要 ≤ 30 行、第 4 节逐项命令与结果、待确认项。
3. 最终回复两行：报告路径 + 一句话结论。
