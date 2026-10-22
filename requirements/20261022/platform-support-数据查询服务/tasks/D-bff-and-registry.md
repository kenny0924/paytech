# 任务卡 D：operations-console-bff 转发与 Platform 注册 DML（块 131）

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`（任务卡 C 正在同目录改 `paytech/platform-support/**`；本卡**只改** `paytech/operations-console-bff/**` 与 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾追加块 131，以及该块对应的注册断言测试，不改 platform-support 的 Java 代码）。
- 授权：普通文件编辑、编译、运行 BFF 模块测试。**不提交、不推送、不执行任何 SQL、不动 local-environment 容器。**
- 合同：同目录 `tasks/C-api-contract.md`（Platform 三个 Api 的路径、Header、DTO）；技术方案 `technique.md` 第 7 节（权限 4 项、路由 18 条、菜单“数据查询 / 数据源 / 查询模板”）。

## 开工前必读

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（BFF 聚合层职责、公共分页、命名）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（BFF 部分）、`agent-docs/UNIT_TEST_GUIDE.md`。参照币种维护的完整转发链：BFF `ext/rest/CurrencySpecManagementController.java`、`integration/PlatformCurrencySpecClient` 与 `impl/PlatformCurrencySpecClientImpl`、`CurrencySpecContractConverter`、`configuration/PlatformClientConfiguration.java`、`service/model/CurrencySpecManagementModels.java`、`configuration/ConfigurationManagementRequestConfiguration.java`，以及对应测试。注册块参照 `platform-support-initial-data.sql` 中块 125（币种，约 :5545 起）与块 126 的写法和其断言测试（grep `125101` / `126` 找到测试位置）。

## 范围

1. **BFF**：`DataQueryManagementController`（数据源 + 模板 + 校验 + 试运行）、`DataQueryExecutionController`，同路径转发到 `PlatformDataQueryClient`（interface + Impl），代理在 `PlatformClientConfiguration` 注册；页面模型与转换按币种写法。没有聚合逻辑，不创建空 Service 层。`application.yml` 的元数据日志前缀加入数据源新建/修改/测试连接（含密码）与执行、试运行路径（与 Platform 一致）。BFF 调 Platform 执行接口时以 `operations-console-bff` 作为调用方（看现有出站客户端如何带 `X-Caller-Service`/内部 Token，保持一致）。
2. **转发测试**：18 条路径各至少一例，按现有 BFF 测试方式用测试 HTTP 端点返回 DTO（不用 Mockito Stub），覆盖错误透传（400/409/422/503 的 Problem 原样保留）。
3. **注册 DML 块 131**（`900000000000131xxx`）：追加到 `platform-support-initial-data.sql` 末尾，格式照块 125：
   - 权限 4 项：`DATA_QUERY_READ`（NORMAL）、`DATA_SOURCE_MANAGE`（HIGH）、`DATA_QUERY_TEMPLATE_MANAGE`（HIGH）、`DATA_QUERY_EXECUTE`（NORMAL），ACTIVE；管理员角色授权 REVOKED。
   - Console Resource：菜单组“数据查询”及“数据源”（`/data-query/data-sources`）、“查询模板”（`/data-query/templates`），DISABLED；组件键 `OPERATIONS_DATA_SOURCES`、`OPERATIONS_QUERY_TEMPLATES`。
   - Gateway Route 18 条（`/api/operations/data-query-admin/v1/...`、`/api/operations/data-query/v1/...` → 上游同路径），每条绑定一项权限，DISABLED；分页 GET 的 page/size 查询参数白名单照现有分页路由；Route Header 与 Guard Rule 照块 125。
   - 在块头注释写明子区间分配与执行前需按 live `mor_platform` 复核未被占用。
   - 更新/新增对应的注册断言测试（数量与关键行）。

## 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-dataquery/paytech
./mvnw -q -pl platform-support/paytech-platform-support-api -am install -DskipTests
./mvnw -pl operations-console-bff -amd test   # 若 -amd 拉入无关模块失败，改为显式列出 BFF 子模块
```

注册断言测试若在 platform-support-test 中，只运行该测试类（`-Dtest=...`），不要跑整个模块（会清理共享库）；若它需要数据库就只做编译并在报告说明。

## 交付报告（中文，写入 `tasks/D-report.md` 并在最终回复给摘要）

改动文件；18 条路径对照表；块 131 的子区间与行数；测试命令与实际结果；需要主会话裁决的问题。**完成后直接结束，不要等待后台事件。**
