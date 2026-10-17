# 第 02c 步报告 · 两个 BFF 搬入

- 分支：`refactor/step-02c-bffs`，起点 `refactor-main@24122de`。
- 提交：`5c43365` 只含纯搬迁（334 个文件均为 100% 改名）；第二个提交含 POM、代码、测试、README 与本报告。
- 结论：第 4 节 4 项全部真实执行并通过。搬迁后两个 BFF 测试数为 1390 / 80，即搬迁前基线 1386 / 76 各加 4 个新增测试。全 Reactor `clean verify` 2473 个测试，0 失败。

## 1. 改动摘要

1. `git mv`：两个 `-web` 模块改为 `paytech/<bff>/paytech-<bff>-app`；`-test`、聚合 `pom.xml`、`README.md`、`.gitignore` 同名搬入。删除两个根的 `mvnw`、`mvnw.cmd` 与 `.mvn/`，两个旧根已不存在。第 00 步的两份占位 README 被原 README 取代。
2. `paytech/pom.xml` 只取消了 `operations-console-bff`、`client-console-bff` 两行注释。
3. 聚合 POM 的改动如下：
   - parent 改为 `../build/paytech-parent/pom.xml`；两个模块的 parent 仍是服务聚合 POM（`../pom.xml`）。
   - `bannedDependencies` 原样保留，并追加 `com.paytech:*-core`、`com.paytech:*-app`。`<includes>` 放行自身 `-app` 与 Foundation 的 `paytech-common-core`，后者也匹配 `*-core`。
   - `failIfNoSpecifiedTests` 保持聚合 `false`、test 模块 `true`。
4. 版本治理：`risk-api`、`platform-support-api`、`client-contract-api`、`paytech-vault-client` 的版本改为 `${project.version}`，artifactId 未改。它们仍从 `~/.m3` 的已安装 Snapshot 解析，`verify` 通过。
5. 测试锚点（09 §2.3、2.4）：两个测试改为 `Path.of("").resolve("../paytech-operations-console-bff-app")` 并断言目录存在，不再向上遍历。
6. 内部 Token 接线：两个 `-app` 新增 `paytech-internal-auth` 依赖。MVC Starter 的 `InternalAuthFilter` 只在依赖存在时装配，`internal-auth.mode` 默认 `OFF`，配置未改。
   - 每个 BFF 新增包内工具类 `InternalCallTokenOptions`。
   - 全部 `HttpServiceClientFactory.create` 路径都经它取 `ObjectProvider<InternalTokenSupplier>`，并以 `withInternalToken(supplier, 目标服务名)` 显式传入目标服务名，满足 `create()` 必须传名的要求。
   - Operations 覆盖 Risk、Platform 与入驻审核的 Risk/CCT 三个配置类；Client 覆盖 Platform 与入驻的 CCT/Platform/Risk。
   - 无 Supplier Bean 时（OFF）选项原样返回，出站行为不变。
7. 新增测试（每个 BFF 4 个）：
   - `ArchitectureAutoTest`：对 app 与 test 两个模块执行 R1–R12，0 违规。
   - `OperationsInternalTokenAutoTest` / `ClientInternalTokenAutoTest`：在 `REQUIRED` 下各 3 例，用 MockHttpServer 断言。
     - 携带合法入站 Token 时，出站 Platform 请求附加由 BFF 自身签发的 Token，校验结果为 `sub=<bff>`、`aud=platform-support-service`、`purpose=GENERAL`，且不是入站 Token 原样转发。
     - 无 Token 时返回 401 `INTERNAL_TOKEN_INVALID`，Platform 收到 0 次调用。
     - `aud` 指向其他服务的 Token 被拒绝。
   - test 模块新增 test 范围依赖 `paytech-arch-rules` 与 `paytech-test-support`。
8. README：工程结构、构建命令（`paytech/` 下 `-pl … -am`）、jar 路径与模块名已改；两份 README 新增内部 Token 说明。指向仓库级目录的链接都加了一层 `../`，经脚本核对 21 条链接全部可解析。

## 2. 第 4 节验证（命令与结果）

所有 Maven 命令前都执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。

1. **搬迁前基线**：在两个旧根下执行 `./mvnw -B verify`，两次都是 BUILD SUCCESS。

   | 根 | 测试数 | 失败 / 错误 |
   |---|---|---|
   | operations-console-bff | 1386 | 0 / 0 |
   | client-console-bff | 76 | 0 / 0 |

   Client 的实测基线是 76，文档记录的 57 已过期。

2. **搬迁后**：
   - 命令一，在 `paytech/` 下执行，结果 BUILD SUCCESS：

     ```bash
     ./mvnw -B clean verify -pl operations-console-bff/paytech-operations-console-bff-test,client-console-bff/paytech-client-console-bff-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
     ```

     | 模块 | 测试数 | 说明 |
     |---|---|---|
     | Operations Console BFF Test | 1390 | 基线 1386 加 4 个新增 |
     | Client Console BFF Test | 80 | 基线 76 加 4 个新增 |

     6 个 BFF 模块的 BFF 专属 Enforcer 执行（含聚合 POM）均输出 `BannedDependencies passed`。
   - 命令二，全 Reactor：`./mvnw -B clean verify -Dpaytech.local-environment.dir=…`。
     - 第一次运行时 shell 中没有 `PAYTECH_REDIS_PASSWORD`，Redis Starter 的 2 个集成测试按设计失败。这是环境前置条件，与本步无关。
     - 按 Redis 前置检查的提示从 `.env.local` 导出该键，值未打印，然后复测：BUILD SUCCESS，35 个模块，**2473 个测试，0 失败 / 0 错误**，用时 3 分 19 秒。
   - 反向验证（09 §8）：临时向两个 `-app` 加入 `com.paytech:paytech-data-service-core`，执行 `./mvnw -o -q -pl <bff>/paytech-<bff>-app -am validate`。两次都以退出码 1 结束，并报 `paytech-data-service-core:jar:1.0.0-SNAPSHOT <--- banned via the exclude/include list`。临时修改已还原。

3. **`mock` Profile 启动**：两个 exec jar 都用下面的参数启动：

   ```text
   --spring.profiles.active=mock
   --spring.cloud.discovery.enabled=false --spring.cloud.nacos.discovery.enabled=false
   --spring.cloud.service-registry.auto-registration.enabled=false
   ```

   本机 18201 / 19201 / 18202 / 19202 已被运行中的环境占用，因此临时改用 28201 / 29201 与 28202 / 29202，代码中的端口配置未改。

   | 应用 | 启动耗时 | 管理端口 `/actuator/health` | 业务端口 `/actuator/health` |
   |---|---|---|---|
   | Operations BFF | 1.6 s | `{"groups":["liveness","readiness"],"status":"UP"}` | 404 |
   | Client BFF | 1.3 s | `{"groups":["liveness","readiness"],"status":"UP"}` | 404 |

   日志均显示 `The following 1 profile is active: "mock"`。两个进程验证后已停止。

4. **历史与旧根**：
   - `git log --follow` 对 Operations 的 `RiskClientConfiguration.java` 与 Client 的 `PlatformClientConfiguration.java` 各显示 4 条历史，最早一直追溯到搬迁前（`fff35d2`、`274fac8` 等）。
   - `ls operations-console-bff client-console-bff` 两个都返回 No such file or directory。

## 3. 待确认

1. **两个 BFF 没有 `mock` Profile 文件**：它们的测试 Profile 是 `auto`，测试直连地址的校验也写死为 `auto`。因此第 3 节任务卡要求的"`mock` Profile 测试"实际放在 `auto` Profile 下，以 `@DynamicPropertySource` 设置 `internal-auth.mode=REQUIRED` 和本地静态密钥对。第 4 节的启动以 `mock` Profile 执行，`mock` 没有对应 YAML，等价于默认配置并关闭 Nacos。是否为 BFF 补 `application-mock.yml`，或统一改名，需要确认。
2. **Token 来源未接 Platform 签发**：本步不注册 `InternalTokenIssueApi` 客户端。原因是 Platform 签发接口的路径与调用方认证属于 02a 尚未落地的设计。因此在 `REQUIRED` 且未配置 static-key 时，Foundation 不创建 Supplier，出站不附加 Token。需要在 02a 合入后补一行接线。
3. **Vault 管理客户端**：它由 `paytech-vault-client` 的自动配置装配，不经 BFF 的 `HttpServiceClientFactory.create`，本步未接 Token，归 02d 处理。
4. **09 §1.3 与 §6 写的是 `{core,app,test}`**：本步按任务卡与 03 §1.2 只建 `app + test`，没有建 core。
5. **README 历史**：两份 README 在 `paytech/<bff>/` 下替换了第 00 步的占位文件，git 将其记为删除加修改，而不是改名。用 `git log --follow` 查看这两份 README 时，看不到搬迁前的历史。
6. **`.gitignore`**：两个旧根的 `.gitignore` 同名搬入服务目录，没有删除，以保持原有忽略规则。是否并入 `paytech/.gitignore`，需要确认。
