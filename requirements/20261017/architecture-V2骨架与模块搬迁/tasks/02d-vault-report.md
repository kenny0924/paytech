# 第 02d 步报告 · Vault（Go 服务 + Java Client）搬入

- 分支：`refactor/step-02d-vault`。实际起点是 `24122de`，即任务卡提交，其父提交为任务卡写的 `0857ad7`。
- 提交共四个：
  - `3748bd1`：删除 `paytech/vault/` 占位 README，使后续搬迁被识别为改名。
  - `a00c4c5`：纯搬迁加路径修复。被第 3 项改动触及的文件在这一提交中保持原内容。
  - `b8cec54`：D26 内部 Token、Java Client 可选 Token、测试与测试清理修复。
  - 本报告单独提交。
- 结论：第 4 节 5 项全部执行，第 1、2、4、5 项通过。第 3 项中 `verify.sh` 原样运行时停在脚本自带的空闲磁盘门禁上：要求 15 GiB，本机只有 4.3 GiB。改动前基线同样停在这里。只把该阈值降到 3 GiB 的临时副本完整通过了 12 道门禁，副本已删除。`vault.sh` 不支持只构建，它的 Go 交叉编译部分已通过。

## 1. 改动摘要

1. `git mv vault-service/ → paytech/vault/`，Go 根及其下各目录与文件的相对位置不变。`vault-test` 改名为 `paytech-vault-test`。
2. 删除 `vault-service/` 的 `mvnw`、`mvnw.cmd` 和 `.mvn/`。`vault-service/` 已不存在，抽查的 `main.go`、Client `pom.xml`、`headers.go` 都能用 `--follow` 追到搬迁前的历史。
3. `paytech/vault/pom.xml` 改为 artifactId `paytech-vault`，parent 使用 `../build/paytech-parent/pom.xml`。两个子模块的 parent 同步修改。`paytech/pom.xml` 只取消了 `vault` 一行的注释。
4. `paytech/mvnw` 补入原 Vault Wrapper 的 4 行 `VAULT_VERIFY_CACHED_MAVEN_ONLY` 门禁。未设置该变量时 Wrapper 行为不变。没有这几行，`VaultEndToEndTest` 的缓存门禁用例会失败。
5. 路径修复覆盖以下位置：
   - `VaultEndToEndTest` 第 27 行改为 `../../mvnw`。
   - `verify.sh` 的 Wrapper 检查与调用改用 `root.parent`。Surefire 报告目录改为 `paytech-vault-test`。Foundation 缓存检查新增 `paytech-internal-auth`。
   - `build-vault.sh`、`vault.sh`、`vault.py`、PDManer 生成器和两个规范路径测试都改指 `paytech/vault`。
6. Vault 目录内 5 个 Markdown 中，13 条出向链接按新位置重新解析，并逐条确认目标存在。README 的 Maven 命令改为 `../mvnw -pl paytech-vault-test`。
7. 以下各项不改：
   - Go module 路径 `github.com/paytech/vault-service`、Nacos 名 `vault-service`、端口 18107。
   - `VaultClientHttpTest` 的 `../testdata/wire/` 和 Go 内部相对路径，两者核对后仍成立。
   - `local-environment/compose.yaml` 的 Vault 构建上下文。它现位于第 200–201 行，任务卡写的是 191–192，相对 context 不受影响。
8. D26 Go 服务端：
   - 新增 `internal/pkg/internalauth`，含校验器、JWKS 缓存和模式定义。
   - `server/headers.go` 按 `VAULT_INTERNAL_AUTH_MODE` 确定调用方，取值为 `OFF`、`DUAL` 或 `REQUIRED`，默认 `OFF`。业务路由与管理路由共用这套判定，`policy.go` 的 Caller 维度不变。
   - 新增错误码 `INTERNAL_TOKEN_INVALID`，对应 401，审计结果记为 DENIED。
   - 新增配置 `VAULT_INTERNAL_AUTH_MODE`、`VAULT_INTERNAL_AUTH_JWKS_URL` 和 `VAULT_INSTANCE_ID`。
9. OpenAPI 的 `v1.yaml` 与 `admin-v1.yaml` 中，13 个操作都声明了可选 `Authorization` 头和 401 响应，契约测试同步更新。
10. Java Client 的变更：
    - `X-Caller-Service` 保留。
    - `paytech-internal-auth` 作为 optional 依赖引入。宿主存在 `InternalTokenSupplier` 时，请求附加 `Authorization: Bearer`，`aud` 为 Vault 服务名，purpose 为 `GENERAL`。
    - Supplier 失败时返回 `DEPENDENCY_UNAVAILABLE`，并且不发出请求。
    - 宿主自行设置的 `Authorization` 一律移除。
    - 新增 `INTERNAL_TOKEN_INVALID` 与 401 的映射。
    - 适配器放在 `@ConditionalOnClass` 的嵌套配置中，宿主未引入 internal-auth 时不加载相关类。
11. 测试新增情况：
    - Go 侧新增 `test/auto/pkg/internalauth`、`test/auto/conf/internal_auth_config_test.go` 和 `test/auto/ext/internalauth`。最后一个包启动四套正式装配：OFF、DUAL、REQUIRED，以及 JWKS 不可用的 REQUIRED，Snowflake 坐标为 31/22～25。
    - 测试支持新增 `builder/internal_token.go` 和 `mocker/external/platform_jwks.go`。后者在测试内自签 ES256，并提供本地 JWKS 端点。
    - Java 侧新增 `VaultClientInternalTokenTest`，含 5 个用例，并在既有 401 映射用例中补充了几组参数。
12. 测试清理修复：`TestMapper` 原先用 `DELETE … WHERE … LIKE` 清理，会全表扫描并给其他测试包的行加 X 锁。新增包与 `ext/http` 并行时出现过一次 InnoDB 死锁，`SHOW ENGINE INNODB STATUS` 已确认。现改为先用不加锁的一致性读取出本次运行的主键，再按主键删除。之后连续 7 轮全量 `go test` 均通过，也没有新的死锁记录。

## 2. 第 4 节验证

环境：JDK 25，Go 1.27.1（`/Users/kenny/sdk/go1.27.1`），sqlc 1.31.1，golangci-lint 2.14.0。Maven 本地仓库为 `~/.m3`。MySQL 使用共享的 `local-environment` 实例，凭据取自主仓库 `.env.local` 并只经环境变量传递。没有启动、停止或重启任何容器。

### 4.1 搬迁前基线（`vault-service/`）

| 命令 | 结果 |
|---|---|
| `go test ./... -count=1` | 13 个包全部 ok；顶层用例 148 个通过，子用例 266 个通过 |
| `./mvnw -B verify`，需先设 `VAULT_TEST_SERVER_BINARY`，二进制由 `go build` 生成 | BUILD SUCCESS；Client 42 个、`paytech-vault-test` 11 个，共 53 个 |
| `bash vault-service/scripts/verify.sh` | exit 1，输出 `PREPARE: at least 15 GiB free disk is required` |

### 4.2 搬迁后

| 命令 | 结果 |
|---|---|
| `cd paytech/vault && go vet ./...` | 通过 |
| `go test ./... -count=1` | 15 个包全部 ok；顶层用例 177 个通过，子用例 346 个通过，比基线多 29 个和 80 个 |
| `go test -race ./... -count=1` | 15 个包全部 ok |
| `gofmt -l .`、`golangci-lint run ./...`、`sqlc diff` | 无输出；0 issues；无漂移 |
| `cd paytech && ./mvnw -B verify -pl vault/paytech-vault-test -am -Dpaytech.local-environment.dir=…/Code-MoR/local-environment` | BUILD SUCCESS；Client 47 个（42 + 5），`paytech-vault-test` 11 个，其中 `VaultEndToEndTest` 9 个、`VaultAdminEndToEndTest` 2 个 |
| `cd paytech && ./mvnw -B verify …`（全 Reactor，同一 shell 先 `set -a; . local-environment/.env.local`） | BUILD SUCCESS；32 个模块，Surefire 汇总 1061 个用例，0 失败、0 错误、0 跳过 |

全 Reactor 第一次运行没有 source `.env.local`。`paytech-redis-starter` 的 2 个用例因缺少 `PAYTECH_REDIS_PASSWORD` 而失败，测试给出了准备说明。按说明 source 后全部通过，与本步改动无关。

### 4.3 脚本

| 命令 | 结果 |
|---|---|
| `bash paytech/vault/scripts/verify.sh` | exit 1，输出 `PREPARE: at least 15 GiB free disk is required` |
| 临时副本 `verify-lowdisk.tmp.sh`，只把阈值改为 3 GiB，运行后已删除 | exit 0，12 道门禁全部 PASS：mysql-schema-preflight、openapi-golden、gofmt、sqlc-diff、golangci-lint、go-vet、go-test、go-race、go-build、maven-clean-verify、sensitive-canary-scan、git-diff-check |
| `bash -n` 检查 `build-vault.sh`、`vault.sh`、`verify.sh` | 通过 |
| `test-environment/scripts/build-vault.sh`，`docker` 换成桩 | 从 `paytech/vault` 交叉编译出 Linux ARM64 的 `vault-server` 与 `vault-probe`；随后在 `compose.sh` 处因 worktree 缺少 `.env.test` 退出 |
| `local-environment/vault/vault.sh build`，使用合成 `e2e/secrets.env`，`docker` 换成桩 | 从 `paytech/vault` 编译出 ELF ARM64 二进制，并写出 `private/vault.env`；随后在 `compose` 处因 worktree 缺少 `.env.local` 退出 |
| `test-environment/scripts/test_vault_deployment.py`（unittest） | 3 个用例通过；`vault.py` 新路径下的 schema 文件存在 |

两个构建脚本都没有调用 Docker，产生的文件均已删除。

### 4.4 Node

| 命令 | 结果 |
|---|---|
| `node --test scripts/pdmaner/vault-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs` | 13 个通过 |
| `node --test requirements-site/tests/*.test.mjs` | 137 个用例，32 个失败。worktree 没有 `node_modules`，所以跳过了 `npm run build`。在 `git archive 24122de` 上同样运行，失败集合逐条相同，属既有问题 |

### 4.5 历史

| 文件 | `git log --follow` |
|---|---|
| `paytech/vault/cmd/vault-server/main.go` | 3 条：`a00c4c5` → `3b17de0` → `fff35d2` |
| `paytech/vault/paytech-vault-client/pom.xml` | 3 条：`b8cec54` → `a00c4c5` → `fff35d2` |
| `paytech/vault/internal/server/headers.go` | 3 条，在 `a00c4c5` 中为 R100 |

`vault-service/` 不存在，`git ls-files vault-service` 结果为 0。中间提交 `a00c4c5` 单独做过检查，用的是该提交的 `git archive`：Go 的 build、vet、contract、conf、server 测试均通过，Maven 离线打包两个 Vault 模块成功。

## 3. Go 侧 Token 校验实现方式

1. **没有引入新依赖。** `go.sum` 中没有任何 JOSE 或 JWT 库，因此只用标准库手写校验：
   - JSON 用 `encoding/json`。
   - base64url 用 `RawURLEncoding.Strict`，拒绝填充。
   - 签名用 `crypto/ecdsa` 加 `crypto/sha256`。
   - 公钥用 `ecdsa.ParseUncompressedPublicKey` 构造，同时校验点是否在曲线上。
2. **结构检查：**
   - Token 必须恰好三段且均非空，总长不超过 4096。
   - 头部 `alg` 必须为 `ES256`，`kid` 须匹配 Java 的同一正则。头部带 `crit` 即拒绝。
   - 签名须为 64 字节的 R||S。头部与声明各自只能是一个 JSON 对象，后面不能有多余内容。
3. **声明检查（与 `InternalTokenClaims` 和 `InternalTokenVerifierImpl` 一致）：**
   - `iss` 必须是 `platform-support-service`。
   - `aud` 可以是字符串，也可以是只含一个元素的数组，值必须等于 `vault-service`。
   - `inst` 必须等于 `VAULT_INSTANCE_ID`。
   - `sub`、`purpose`、`jti` 使用 Java 的同一组正则。
   - `iat` 与 `exp` 只接受整数秒，且 `0 < exp − iat ≤ 300s`。
   - `now < exp` 严格判定。`iat` 与 `nbf` 最多允许超前当前时间 60 秒。
4. **JWKS 缓存（与 `JwksInternalTokenKeySource` 一致）：**
   - 公钥缓存 5 分钟。缓存过期后先刷新；刷新失败时丢弃旧公钥并返回不可用。
   - `kid` 未命中时最多每 10 秒强制刷新一次。强制刷新失败时保留现有缓存。
   - 只接受 200 响应和不超过 64 KiB 的正文，读取超时 2 秒。
   - 只保留同时满足以下条件的公钥：带 `kid`，P-256，`use` 为 `sig` 或未声明，`alg` 为 `ES256` 或未声明。
   - 第一次读取 JWKS 发生在第一个带 Token 的请求上，启动过程不依赖 Platform。
5. **模式判定（与 `InternalCallerAuthenticator` 一致）：**
   - `DUAL`：Token 无效或公钥不可用时，都回退到 `X-Caller-Service`。
   - `REQUIRED`：只认 Token。缺失、不合法、`Basic` 方案或多个 `Authorization` 头都返回 401；公钥不可用返回 503。
   - 业务路由上的拒绝和其他公共头失败一样单独审计一次，调用方记为 `UNKNOWN`。
   - `/health/*` 与 `/metrics` 不经过判定。

## 4. 待确认项

1. **Go module 路径是否改名。** 本步保持 `github.com/paytech/vault-service`。如果改为 `…/paytech/vault`，需要改写 155 处 import、`.golangci.yml` 的 `local-prefixes` 和 `sqlc.yaml` 的输出包。建议放到需求步 13 的旧根清理之后、单独一步完成。
2. **Vault 何时改为只接受 Token（`REQUIRED`）。** 前提有三个：
   - Platform 的 JWKS 与 Token 签发端点上线。第 01 步只定义了配置名 `internal-auth.jwks-uri`，具体路径待 02a 确定。
   - 所有调用方（Platform、Payment、CCT、Operations BFF）都引入 `InternalTokenSupplier`。
   - 先以 `DUAL` 运行一个观察期。

   建议在各调用方完成接入、`DUAL` 稳定后，用单独需求切换到 `REQUIRED`。到那时再决定是否从 OpenAPI 中去掉 `X-Caller-Service` 的必填。当前 compose 和测试部署都保持 `OFF`。
3. **`paytech/mvnw` 新增的 Vault 门禁（4 行）。** 这是让 `VaultEndToEndTest` 在唯一 Wrapper 下继续成立的最小改动。另一种做法是改用通用变量名，例如 `PAYTECH_WRAPPER_CACHED_ONLY`。
4. **实例 ID 的配置名。** Go 侧使用 `VAULT_INSTANCE_ID`，对应 Java 的 `paytech.instance-id`，实例清单生成部署描述时需要写入它。
5. **Java Client 的 Token purpose 固定为 `GENERAL`。** Vault 的访问规则按请求体中的 purpose 判定，不读取 Token 的 purpose。
6. **DUAL 回退的告警。** Java 侧在每个调用方第一次回退时记一条 WARN；Go 侧目前没有。如需要，可以在 observability 中加一个按调用方计数的指标，避免日志里出现 Token。
7. **`verify.sh` 的 15 GiB 磁盘门禁。** 本机空闲空间只有 4–5 GiB，门禁在搬迁前后都无法通过。是保留这个阈值，还是另行释放空间，需要用户决定。
8. **`ext/internalauth` 不能用 `-count>1` 连续运行。** 原因是 JWKS 强制刷新每个环境每 10 秒最多一次，包注释和 README 已写明。如果需要支持，可以把刷新间隔做成仅测试可覆盖的配置。

## 5. 未做与边界

- 没有改动以下内容：Vault 数据模型、Protect 与 Reveal 语义、访问规则、`mor_vault` Schema、`agent-docs/VAULT_SERVICE_ARCHITECTURE.md`，也没有执行任何 SQL。
- 仓库级文档指向 `vault-service/` 的入向链接共 19 条，按 09 文档第 6.2 节留到计划 13 统一改写，本步不改。`docs/reviews` 等历史记录里的命令文本也保持原样。
- 没有改动 `test-environment/scripts/build.sh`、`test_deploy_script.py` 和 `agent-docs/CURRENT_IMPLEMENTATION.md`。
- 多次 `-am` 和全 Reactor `verify` 没有执行 install，`~/.m3` 中的快照未被改写。
