# 任务卡 02c · 两个 BFF 搬入

- 分支：`refactor/step-02c-bffs`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step02c`；起点：`refactor-main@0857ad7`（第 00、01、09、10 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 02c 步。
- 并行：与 02a、02b、02d 同时进行。**不改** `test-environment/scripts/build.sh`、`test_deploy_script.py`。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文；[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节两个 BFF 行（**无 api；`paytech-<bff>-app` + `-test`**）。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4（BFF 行）、1.5（BFF 行）、2.3、2.4、第 6 节（只改 BFF 目录内链接）。
- [analysis/retained-systems.md](../analysis/retained-systems.md) 第 1、2、4.2、7 节（BFF 对 `risk-api`、`client-contract-api`、`platform-support-api`、`paytech-vault-client` 的硬编码 `1.0.0-SNAPSHOT`）。
- 第 00、01 步报告。
- 现有 `operations-console-bff/`、`client-console-bff/` 全部 POM、`RiskClientConfigurationAutoTest`、`OperationsConsoleBffApplicationAutoTest`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 2. 模块映射（`git mv`）

| 现 | 目标 |
|---|---|
| `operations-console-bff/paytech-operations-console-bff-web` | `paytech/operations-console-bff/paytech-operations-console-bff-app` |
| `operations-console-bff/paytech-operations-console-bff-test` | `paytech/operations-console-bff/paytech-operations-console-bff-test` |
| `client-console-bff/paytech-client-console-bff-web` | `paytech/client-console-bff/paytech-client-console-bff-app` |
| `client-console-bff/paytech-client-console-bff-test` | `paytech/client-console-bff/paytech-client-console-bff-test` |
| 两个 `pom.xml`、`README.md` | 同名 |

删除两个根的 `mvnw`、`mvnw.cmd`；两个旧根消失。`paytech/pom.xml` 只取消 `operations-console-bff`、`client-console-bff` 两行注释。应用名与端口（18201 / 19201、18202 / 19202）不变。

## 3. 代码改动清单

1. 聚合 POM：`<parent>` 真实相对路径；`bannedDependencies` 原样搬入并追加 `com.paytech:*-core`、`com.paytech:*-app`（`<includes>` 放行自身 app）；JDBC / AMQP / XXL / compensation-event 等禁令保留；`failIfNoSpecifiedTests` 聚合 `false`、test `true` 保留。
2. 版本治理：对 `risk-api`、`client-contract-api`、`platform-support-api`、`paytech-vault-client`、Foundation 模块的硬编码 `1.0.0-SNAPSHOT` 改为 `${project.version}`（同 Reactor 同版本）；**依赖的 artifactId 本步不改**（`risk-api`、`client-contract-api` 仍是旧坐标，第 04 / 06 步切换），它们来自 `~/.m3` 中已安装的 Snapshot，确认 `verify` 可解析。
3. 测试锚点（09 §2.3、2.4）：两个测试改为 `Path.of("").resolve("../paytech-operations-console-bff-app")` 并断言存在，不再向上遍历找目录名。
4. 内部 Token 接线：两个 BFF 的出站 `HttpServiceClientFactory` 客户端自动获得第 01 步的出站拦截器（`internal-auth.mode` 默认 `OFF`）；入站 `InternalAuthFilter` 由 mvc-web-starter 提供，配置保持 `OFF`；`mock` Profile 测试各覆盖一例 `REQUIRED` 下出站附加 Token（MockHttpServer 断言）。
5. `paytech-<bff>-test` 各新增 `ArchitectureAutoTest`（arch-rules）。
6. 两个 `README.md` 与目录内链接按新深度修正。

## 4. 测试与验证

1. 搬迁前两个根 `./mvnw -B verify` 测试总数（文档记录 1386 / 57）。
2. 搬迁后 `cd paytech && ./mvnw -B verify -pl operations-console-bff/paytech-operations-console-bff-test,client-console-bff/paytech-client-console-bff-test -am` 全绿且数量 ≥ 基线 + 新增；全 Reactor `verify` 全绿。
3. 两个 `-app` 以 `mock` Profile 启动到 health UP。
4. `git log --follow` 抽查各一份文件；两个旧根不存在。

## 5. 禁止事项

- 不改页面聚合、授权转发等任何业务语义；不动其他服务目录；不改 `local-environment`。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-02c): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/02c-bffs-report.md`：改动摘要 ≤ 30 行、第 4 节逐项命令与结果、待确认项。
3. 最终回复两行：报告路径 + 一句话结论。
