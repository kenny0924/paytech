# 第二段任务卡公共部分（S2-A / S2-B / S2-C 都先读）

> 设计：[segment2.md](../segment2.md) v2（用户 2026-10-22 确认，Q12—Q17 按建议）。原则 P1—P3 见其第 1 节。

## 0. 工作方式

- 每张卡在自己的 detached worktree 中修改，基于 main `a5479950`；不建分支、不提交、不推送、不部署。主工作区与其他卡的 worktree **只读**。
- **SQL：** 只有 S2-B 被授权在本机 `mor_client_master` 执行“加列一步”（见 S2-B）。其他任何库、任何删列 / 删表 DDL **只写不执行**；mor-test 不碰。
- **测试：** 本机库与其他会话共用。跑之前 `ps aux | grep surefire`，有人在跑就等；只跑受影响模块（`-pl … -am`），不跑全仓库。**严禁 `mvn install`**（会覆盖共用 `~/.m3` SNAPSHOT）。JDK 25：`/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，`-Dmaven.repo.local=~/.m3/repository`，离线 `-o`。需要独占跑 Platform 全量时先报告主会话。
- 必读：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md` 与 `agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`（改表时）。不调用 superpowers skill；命令前台运行。
- 测试不得用 Mockito stub；外部 HTTP 依赖用测试 HTTP 端点返回配置的 DTO，走真实出站客户端。
- 完成后写报告到本需求 `reports/S2-<卡>-report.md`（在自己的 worktree 中），并回复主会话：改动文件、测试命令与结果、偏差。

## 1. 已定合同（主会话定，三张卡照此实现 / 调用）

### 1.1 文件地址（S2-A 实现，B / C 使用）

- `paytech-platform-support-api` 新增 `com.paytech.platform.support.api.files.FileAddress`：
  - 不可变值类型，持有 `long fileId`；
  - `static FileAddress of(long fileId)`；
  - `static FileAddress parse(String value)`：只接受 `^/files/v1/[1-9][0-9]{0,18}$`，否则抛 `IllegalArgumentException`；
  - `String value()` 返回 `/files/v1/{fileId}`；
  - `long fileId()`。
  - 纯 Java，无 Spring 依赖。
- 内部文件接口（`InternalFileApi`，`/internal/files/v1`）凡响应里带 `fileId` 的，都增加 `fileAddress` 字符串字段（同值规则）。
- 下载授权接口增加可选请求头 `X-Operator-Account-Id`（规范正整数）：有值时 Platform 写一条审计，内容为操作人、文件、调用服务，不记 URL。

### 1.2 调用身份

- Platform 内部文件接口从 `internal-auth` 的调用身份取来源系统。本段起 Platform 以 `internal-auth.mode=DUAL` 运行（环境配置），调用方在请求上带 `X-Caller-Service: <spring.application.name>`：
  - client-console-bff：`client-console-bff`
  - operations-console-bff：`operations-console-bff`
  - CCT：`client-master-service`
- 调用方的出站客户端统一带这个头（在该服务现有 Platform 客户端配置处加默认头即可），不改调用方自己的 internal-auth 模式。

### 1.3 CCT 合同（S2-B 实现，C 使用）

- C12 关联材料请求（`ClientDocumentModels` 中的请求）：`fileId` 改为 `fileAddress`（字符串，`FileAddress` 规则）。
- CCT 申请详情响应中的每条材料：`fileId` 改为 `fileAddress`。哈希、主体、要求码等其余字段不变。
- CCT 不提供任何文件上传、下载、查询文件的接口。

### 1.4 用途码与来源

- 入驻上传：`reasonCode=CLIENT_ONBOARDING_MATERIAL`（client-console-bff 固定填写），来源由调用身份决定，即 `client-console-bff`。
