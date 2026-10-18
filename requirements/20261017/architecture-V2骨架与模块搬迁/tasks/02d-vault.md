# 任务卡 02d · Vault（Go 服务 + Java Client）搬入

- 分支：`refactor/step-02d-vault`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step02d`；起点：`refactor-main@0857ad7`（第 00、01、09、10 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 02d 步；D26（Go 服务端读取内部 Token 的 caller）。
- 并行：与 02a、02b、02c 同时进行。**不改** `test-environment/scripts/build.sh`、`test_deploy_script.py`。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文；[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节 vault 行。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 0 节（Vault 目标布局）、1.1（vault 行）、2.10–2.13、3.3、3.4、3.5、3.7、4.3（vault 生成器）、第 5、6 节（只改 Vault 目录内链接与 PDManer 规范路径）。
- [analysis/retained-systems.md](../analysis/retained-systems.md) 第 1、2、4.1（Vault 的 `X-Caller-Service`：Java Client `VaultRequestHeaders`、Go `internal/server/headers.go`、`internal/biz/policy.go` 的 Caller 维度）、5、7 节。
- `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`（边界不变，只搬目录）；第 00、01 步报告（`paytech-internal-auth` 的 Token 声明格式、JWKS 地址配置名）。
- 现有 `vault-service/` 全部（Go module `github.com/paytech/vault-service`、`paytech-vault-client`、`vault-test`、`scripts/verify.sh`、`testdata/`、`docs/`）。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 2. 搬迁清单（`git mv`）

`vault-service/` 整体 → `paytech/vault/`（Go 根与 `api/`、`cmd/`、`internal/`、`test/`、`testdata/`、`scripts/`、`docs/`、`go.mod`、`sqlc.yaml`、`README.md` 相对位置全部不变）；Java 模块 `paytech-vault-client`、`vault-test`（artifactId 已是 `paytech-vault-test`，目录改名 `paytech-vault-test`）直接位于 `paytech/vault/` 下。删除 `vault-service/mvnw`、`mvnw.cmd`。`paytech/pom.xml` 只取消 `vault` 注释。Go module 路径不改（`github.com/paytech/vault-service` 保持，避免全量 import 改写；待确认项）。Nacos 注册名 `vault-service`、端口 18107 不变。

## 3. 代码改动清单

1. `paytech/vault/pom.xml`：`<parent>` 真实相对路径；模块 `paytech-vault-client`、`paytech-vault-test`。
2. 路径修复：`VaultEndToEndTest` 第 27 行 `../mvnw` → `../../mvnw`（09 §2.10）；`scripts/verify.sh` 第 118、142 行 Wrapper 改 `root.parent`（09 §3.5）；`test-environment/scripts/build-vault.sh` 第 35 行、`local-environment/vault/vault.sh` 第 45 行改 `paytech/vault`（09 §3.3、3.4）；`test-environment/scripts/vault.py` 第 86 行（09 §3.7）；`scripts/pdmaner/vault/generate-vault-model.mjs` 输出路径与 `pdmaner-canonical-paths.test.mjs` 的 Vault 路径改 `paytech/vault/docs/database/vault.pdma.json`。`VaultClientHttpTest` 的 `../testdata/wire/` 与 Go 内部相对路径不变（核对）。
3. **Go 服务端内部 Token（D26）**：`internal/server/headers.go` 新增从 `Authorization: Bearer` 解析 JWS（ES256）并经 Platform JWKS（配置 `VAULT_INTERNAL_AUTH_JWKS_URL`，带缓存与 `kid` 未命中刷新）校验 `iss`、`aud == vault-service`、`inst`、`exp` 的逻辑，取 `sub` 作为 Caller；模式 `VAULT_INTERNAL_AUTH_MODE = OFF | DUAL | REQUIRED`（默认 `OFF`：行为与今天完全一致；`DUAL`：Token 优先，否则回退 `X-Caller-Service`；`REQUIRED`：无 / 坏 Token → 401 Problem）。访问规则 `policy.go` 的 Caller 维度不变。只用 Go 标准库 + 现有依赖实现（若需 JOSE 库，选用已在 `go.sum` 中的，没有则用 `crypto/ecdsa` + `encoding/json` 手写 compact JWS 校验，并写足测试）。
4. Java Client：`VaultRequestHeaders` 保留 `X-Caller-Service`；新增可选 `InternalTokenSupplier` 注入——存在时附加 `Authorization: Bearer`，不存在时行为不变。
5. Go 测试：`test/auto/...` 新增内部 Token 三模式用例（测试内自签 ES256 密钥并起本地 JWKS 端点）；`openapi` 契约若需声明 `Authorization` 头则同步 `api/openapi/*.yaml` 与契约测试。
6. `paytech/vault/README.md` 与目录内文档链接修正；`local-environment/compose.yaml` 第 191–192 行 vault 构建上下文（09 §3.13 核对不受影响，确认即可）。

## 4. 测试与验证

1. 搬迁前 `cd vault-service && ./mvnw -B verify` 与 `go test ./...` 的数量。
2. 搬迁后 `cd paytech/vault && go vet ./... && go test ./...` 全绿；`cd paytech && ./mvnw -B verify -pl vault/paytech-vault-test -am` 全绿（`VaultEndToEndTest` 会调用 `scripts/verify.sh`，须通过）；全 Reactor `verify` 全绿。
3. `bash paytech/vault/scripts/verify.sh` 单独通过；`bash local-environment/vault/vault.sh`（仅构建部分，若脚本支持）通过。
4. `node --test scripts/pdmaner/...`（Vault 相关）通过。
5. `git log --follow` 抽查 `paytech/vault/cmd/vault-server/main.go` 与 `paytech-vault-client/pom.xml`；`vault-service/` 不存在。

## 5. 禁止事项

- 不改 Vault 数据模型、Reveal / Protect 语义、访问规则；不改 `mor_vault` Schema；不执行 SQL。
- `VAULT_INTERNAL_AUTH_MODE` 默认必须 `OFF`；不在 compose 中开启。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-02d): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/02d-vault-report.md`：改动摘要 ≤ 30 行、第 4 节逐项命令与结果、Go 侧 Token 校验实现方式、待确认项（Go module 路径是否改名、Vault 是否应改为只接受 Token 的时点）。
3. 最终回复两行：报告路径 + 一句话结论。
