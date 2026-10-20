# 任务卡 PLT · Platform：接入方存在性内部查询接口

- 背景：technique.md 第 3.2 节 B6 要求渠道激活前核对 Platform 存在同编码接入方。Platform 现有 `IntegrationManagementApi.integration` 是运营控制台接口，需要人工会话与 `EXTERNAL_API_READ`；用户 2026-10-20 决定：**Platform 新增内部免会话查询接口**。
- 合同已由主会话写定并编译通过，**不要改签名**：`paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/standardapi/IntegrationLookupApi.java`（`GET /standard-apis/v1/integrations/{integrationCode}/existence`）与 `response/IntegrationExistenceResponseBody.java`（`integrationCode`、`exists`）。这两个文件在主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR` 已存在；你的 worktree 基于当前 HEAD 创建，若其中没有，就从主工作区逐字复制。
- 执行者：Opus 5.5 agent，独立 worktree。不执行 git 写命令；不调用 Superpowers skill；不碰共享环境；不用 Mockito / MockBean。只改 `paytech/platform-support/**`（api 的两个合同文件只复制不改）与本需求 `tasks/PLT-report.md`。

## 1. 先读

`AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 4–7 节、`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节、`agent-docs/UNIT_TEST_GUIDE.md`；Platform 的 `StandardApiExecutionController`、`ExecuteStandardApiProcessor`、标准 API 执行的内部鉴权方式（internal-auth / 内部 Token 校验在哪一层、`StandardApiCommandApi` 如何被允许免会话）、接入方表 `t_integration` 的 Repository / Mapper（integration 模块）、既有标准 API 执行的自动测试（放在哪个测试模块、用什么基类、如何起 Platform 上下文）。

## 2. 范围

- 在 app 的 `ext/rest/standardapi` 下新增 `IntegrationLookupController implements IntegrationLookupApi`，与 `StandardApiExecutionController` 同样的免会话 / 内部 Token 规则（若内部 Token 校验按路径前缀配置，`/standard-apis/v1/**` 已覆盖则无需新增配置；否则补同样的登记）。
- 只读查询：委托 integration 模块的本地 Service（按 `integrationCode` 查 `t_integration` 是否存在；不区分启停状态；不返回任何配置）。无 Processor、无事务。编码格式不合法 422（`@Validated` 路径参数）。
- 不改既有接口；不加缓存；不做调用方白名单。

## 3. 测试

- 在 Platform 对应的 auto 测试模块增加 `IntegrationLookupApiTest`：存在 → `exists=true`；不存在 → 200 `exists=false`；编码格式非法 → 422；internal-auth 开启时无内部 Token → 与 `StandardApiCommandApi` 同样的拒绝（若既有测试覆盖了该机制，复用其方式）。
- 命令：JDK 25（`export JAVA_HOME=$(/usr/libexec/java_home -v 25)`），在 `paytech/` 下 `./mvnw -q -pl platform-support/<对应测试模块> -am test`（以实际模块名为准；若 Platform 的全量测试过慢，至少跑该测试类所在模块并说明），0 失败。本机 MySQL 的 Platform 测试库若需要预置表按模块 README 办，不碰 mor-test。

## 4. 交付

`tasks/PLT-report.md`：已读清单；Controller / Service 落点与鉴权说明；改动文件表；测试命令与结果；第 11 节核对（不适用项说明）；遗留。不要提交。
