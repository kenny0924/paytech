# 第 02b 步报告 · API Gateway 搬入

- 分支：`refactor/step-02b-gateway`。worktree 的实际起点为 `refactor-main@24122de`，它是任务卡所写 `0857ad7` 之后、只增加任务卡文档的一次提交。
- 实现提交：`4c5d20c`（搬迁）、`56b9064`（测试归位与 arch-rules）、`8b1d5db`（内部 Token）、`c20ec3e`（补充分支测试）。本报告单独提交。
- 结论：第 4 节 5 项全部真实执行。
  - 1、2、4、5 通过。全 Reactor `clean verify` 共 2311 个测试，0 失败。
  - 3 中两个脚本仍以退出码 1 结束。搬迁前的基线上二者以同样原因失败，属既有问题，见第 2 节第 3 项。

## 1. 改动摘要

1. `git mv`：`api-gateway-web` 改为 `paytech/api-gateway/paytech-api-gateway-app`，`api-gateway-test` 改为 `paytech-api-gateway-test`。`pom.xml`、`README.md`、`scripts/`、`.gitignore` 同名搬入。第 00 步的 README 占位被原 README 取代。
2. 删除 `mvnw`、`mvnw.cmd`、`.mvn/`；`api-gateway/` 已不存在。`paytech/pom.xml` 只取消了 `api-gateway` 一行注释。
3. 聚合 POM `paytech-api-gateway` 以 `../build/paytech-parent/pom.xml` 继承父 POM，两个模块的 parent 是它。`spring.application.name`、端口 18080 / 19080 未变。
4. Enforcer：Servlet、MVC、mvc-web-starter 等禁令原样保留。逐服务的业务制品模式换成 `com.paytech:*-core`、`com.paytech:*-app`，并放行 `paytech-common-core` 与自身 app。
5. `.gitignore` 只保留服务特有的一行 `application-auto.local.yml`，其余规则已由 `paytech/.gitignore` 覆盖。
6. 路径修正：
   - 三个脚本：`verify-gateway-boundaries.mjs`、`verify-jacoco.mjs` 改为新模块路径；`class-file*.mjs` 无路径常量，随目录搬迁。
   - `test_nginx_gateway_problem_boundary.py` 第 11–14 行。
   - README 的 12 条相对链接加深一层，经脚本核对全部可解析；构建命令改为从 `paytech/` 单 Reactor 执行。
7. 两个从未执行的测试类移入 `auto/`。它们访问包级可见成员，改为在测试侧用反射访问，生产可见性不变。
8. 新增 `ArchitectureAutoTest`，对 app 与 test 两个模块执行 R1–R12。arch-rules 新增 `NoCoreServiceAutoTest`：
   - 无 core 时，R1、R3、R4、R5 没有检查对象，按通过处理；启动类与 Controller 留在 app 合法。
   - app 依赖其他服务的 core 仍由 R6 拦截。
9. R10 在 9 个文件中报出枚举排版问题，已改为逐行声明并以空行分隔，常量与顺序不变。
10. 内部 Token（D26）：
    - 新增 `GatewayInternalTokenPolicy` 与 `GatewayInternalTokenConfiguration`。`application.yml` 新增 `internal-auth.mode: "OFF"`，必须加引号，原因是未加引号的 OFF 在 YAML 1.1 中是布尔 false。
    - Snapshot、JWKS、Credential 三个 WebClient 挂 `InternalTokenExchangeFilterFunction`，`aud` 为 Platform 服务名。
    - 新增 `InternalTokenUpstreamGlobalFilter`（顺序 +410），在可信 Header 重建之后为转发请求写入 Token；取不到 Token 时返回 503，不转发。
    - 模式非 OFF 却没有 Token 来源时启动失败。没有挂入站 `InternalAuthWebFilter`。
11. 测试：
    - 三个 Platform Simulator 记录收到的 `Authorization`。
    - 新增 4 个测试类共 13 例：REQUIRED 下 4 条出站链路的 Token 经 Foundation 校验器验证，OFF 下不附加，策略模式判定，以及过滤器三个分支。
    - README 新增该配置的说明。

## 2. 第 4 节验证（实际命令与结果）

所有 Maven 命令前均执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并只导出 `PAYTECH_REDIS_PASSWORD` 一个键，取值来自主工作区 `.env.local` 的 `MOR_REDIS_PASSWORD`，未打印。全程没有 up、down 或 restart 任何容器，没有执行 `install`。

1. **搬迁前基线**：

   ```bash
   cd api-gateway && ./mvnw -B verify
   ```

   BUILD SUCCESS，2 分 22 秒，**1277 个测试，0 失败**，与文档记录一致。

2. **搬迁后**：
   - 在提交 `8b1d5db` 上执行 `cd paytech && ./mvnw -B clean verify -pl api-gateway/paytech-api-gateway-test -am -Dpaytech.local-environment.dir=…`：BUILD SUCCESS，Gateway 1302 个测试，0 失败。
   - 补充测试后，在 `c20ec3e` 上执行全 Reactor `cd paytech && ./mvnw -B clean verify -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`：BUILD SUCCESS，3 分 29 秒，32 个 Reactor 模块全部 SUCCESS，**共 2311 个测试，0 失败 / 0 错误**。

   | 模块 | 测试数 | 说明 |
   |---|---|---|
   | paytech-api-gateway-test | 1306 | 基线 1277 + 归位 15 + ArchitectureAutoTest 1 + 内部 Token 13 |
   | paytech-arch-rules | 50 | 含新增 NoCoreServiceAutoTest 2 例 |
   | Foundation 合计（16 个带测试的模块） | 893 | 与第 01 步报告一致 |
   | data-service-test、instance-manifest、migration-runner、reactor-test | 16、16、27、3 | 未改动 |

   - 归位的 15 例中，`PlatformSnapshotControlClientAutoTest` 12 例、`GatewaySnapshotCodeParserAutoTest` 3 例，移入后首次执行即全部通过，没有修改断言。
   - 内部 Token 的 13 例：Outbound 4、Disabled 1、Policy 5、UpstreamFilter 3。
   - 变异验证：临时去掉 Snapshot 客户端的 Token 挂载，并让转发过滤器直接放行，Outbound 测试中 3 例失败。之后已从备份恢复原文件。

3. **脚本**（在上述 verify 之后执行）：

   | 命令 | 退出码 | 结果 | 搬迁前基线 |
   |---|---|---|---|
   | `node paytech/api-gateway/scripts/verify-gateway-boundaries.mjs` | 1 | 唯一一项：`IdempotencyGlobalFilter.java` 违反不得 block 或手动 subscribe | 同一项，退出码 1 |
   | `node paytech/api-gateway/scripts/verify-jacoco.mjs` | 1 | 分支覆盖率 89.485%（2400/2682），低于 90% | 89.286%（2375/2660），退出码 1 |
   | `node paytech/api-gateway/scripts/class-file.test.mjs` | 0 | 3/3 | 3/3 |
   | `python3 -m unittest test_nginx_gateway_problem_boundary` | 0 | 9/9 | — |

   - 边界脚本报出的是第 106 行 `doOnCancel(() -> settlement.release(...).subscribe())`。修复它会改变幂等释放语义，属于本步禁止范围，未改。
   - JaCoCo：本步新增代码的分支已由测试覆盖，整体分支覆盖率高于基线，但仍未达到 90% 门禁。

4. **`-app` 以 mock Profile 启动**：
   - 启动命令为 `java -jar paytech-api-gateway-app/target/paytech-api-gateway-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock …`。
   - 日志显示 `profile is active: "mock"`，`Started ApiGatewayApplication in 2.093 seconds`，随后 `Gateway Snapshot 刷新成功: targetCount=1, routeCount=3`。
   - `GET /actuator/health/readiness` 返回 200 `{"status":"UP"}`，该分组包含 `gatewayBootstrap` 与 `gatewayRedis`，连接的是本地 Redis 16379。
   - `GET /actuator/health/liveness` 返回 200 `{"status":"UP"}`。
   - `GET /actuator/health` 返回 401。这是既有设计：`GatewayFallbackSecurityConfiguration` 只公开两个探针，test-environment 的健康检查也只用 readiness。
   - 为本次启动所做的环境处理（都只在命令行生效，没有改动仓库配置）：
     - 18080 / 19080 已被本机另一会话的 java 进程（PID 60588）占用，因此用 `--server.port=28180 --management.server.port=29180` 覆盖。
     - 关闭 Nacos 注册，不触碰共享 Nacos。
     - Snapshot 用直连地址，指向临时本地桩 `127.0.0.1:38106`。桩返回测试夹具 `GatewaySnapshotFixture.operationsSnapshot()` 的 JSON。
     - 补充 `gateway.client.console-origin/security.issuer/security.audience` 三项，README 的本地运行示例缺少它们。
   - 检查结束后，应用已优雅关闭，桩也已停止。

5. **历史与目录**：
   - `git log --follow` 抽查结果：

     | 文件 | 提交 |
     |---|---|
     | `paytech-api-gateway-app/.../ApiGatewayApplication.java` | `4c5d20c`、`fff35d2` |
     | `scripts/verify-gateway-boundaries.mjs` | `4c5d20c`、`fff35d2` |
     | `auto/configuration/PlatformSnapshotControlClientAutoTest.java` | `56b9064`、`4c5d20c`、`fff35d2` |

     三份文件都能追溯到搬迁前的 `fff35d2`。
   - `ls api-gateway` 返回 No such file or directory；提交后 `git status --short` 为空。

## 3. 待确认项

1. **转发链路不能挂 `InternalTokenExchangeFilterFunction`**：Spring Cloud Gateway 经 Netty 转发，不走 WebClient。因此改用 GlobalFilter，Token 来源与 Purpose `GENERAL` 与该过滤器一致，并覆盖全部运行时路由，不只是 BFF。公网 `Authorization` 在 Header 重建时已清除，Snapshot 校验也禁止转发该 Header，所以 Token 是唯一取值。
2. **生产 Token 来源尚不存在**：
   - Gateway 没有定义 Platform 签发客户端 `InternalTokenIssueApi`。签发端点与调用方认证属于第 02a 步。
   - 目前模式非 OFF 时，只有本地 `internal-auth.static-key.*` 可用；没有任何来源时启动失败。
   - Foundation 自动配置在模式非 OFF 时还会创建入站校验 Bean，因此 Gateway 也必须配置 `paytech.instance-id`，以及 `internal-auth.jwks-uri` 或 static-key，虽然 Gateway 并不校验入站 Token。
3. **`internal-auth.mode` 的语义**：它在 Foundation 中是入站校验模式，Gateway 按任务卡借用为出站开关，`DUAL` 与 `REQUIRED` 在 Gateway 中等价。
4. **`aud` 取值**：
   - Platform 三个客户端取服务发现名；本地直连地址时取默认的 `platform-support-service`，为此新增 `tokenAudience()`。
   - 转发取路由的 `discoveryServiceName`。Gateway Target 允许服务名含点，而 Token 服务名模式不允许。若 REQUIRED 下出现含点的服务名，取 Token 时会抛参数异常并返回 500，而不是 503。
5. **mock Profile**：
   - Gateway 测试必须只启用唯一的 `auto` Profile，这是 PLT-I03 直连地址的既有门禁。因此 REQUIRED 场景用 `@DynamicPropertySource` 开启，没有另建 mock Profile。
   - Foundation `MockHttpServer` 依赖 MVC 与 Tomcat，被 Gateway Enforcer 禁止。因此断言基于现有 reactor-netty Simulator 记录的 Header。
   - Credential Simulator 原规则是不得携带 `Authorization`，现放宽为：不带，或只带一个内部 Bearer 且不含 Client 凭证。
6. **`platform-support-api`**：仍按旧坐标从 `~/.m3` 解析。第 02a 步合入后需改为 Reactor 内的 `paytech-platform-support-api`，这里预计是合并冲突点。
7. **未改的旧路径**（任务卡禁改或属于历史证据）：
   - `test-environment/scripts/build.sh:60,64` 仍执行 `cd api-gateway`，并使用旧 jar 名，本步合入后部署构建会失败；`test_deploy_script.py` 的断言同样未改。
   - `requirements/20260914/client-console-注册与客户入驻/verification/` 下的两个脚本是历史证据，未改。
8. **两项既有失败未修**：边界脚本的 `.subscribe()`，以及低于 90% 的分支覆盖率，见第 2 节第 3 项。
9. **README 本地运行示例**缺少 `gateway.client.*` 三项必填配置，属既有文档缺口，本步未补。
10. **共享本地资源**：Gateway 测试 Redis 使用唯一 Namespace；启动检查的 Guard Namespace 为 `step02b-startup:api-gateway`，只访问了管理端口，没有产生 Guard 请求。
