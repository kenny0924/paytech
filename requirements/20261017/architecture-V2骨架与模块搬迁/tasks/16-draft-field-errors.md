# 任务卡 16 · 入驻资料校验错误带字段清单：`INVALID_DRAFT` 返回 Problem `errors[]` 并经 BFF 透传到页面

- 分支：`refactor/step-16-draft-field-errors`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step16`；起点：`refactor-main@85e0ab79`。
- 背景（2026-10-18 用户在本机 Client Console 实测）：保存资料时手机号没带国家码，client-master 返回 422 `INVALID_DRAFT`，detail 只有一句"入驻字段不符合约定"，没有 `errors[]`，页面无法指出是哪个字段；用户确认要补字段级错误。同日已修的另一问题（422 标题）见 `SaveDraftProblemContractTest`。
- 原则：Foundation 只加通用能力（业务 Code + 字段清单一起表达），业务语义留在 client-master；不改 API 路径、状态码（仍 422）、错误 Code；禁止 `when().thenReturn` / `@MockBean`。

## 1. 先完整读取

- `agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md`、`docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`（Problem 合同：`errors[{field,message}]` 的边界）；`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 4、5 节；`agent-docs/UNIT_TEST_GUIDE.md`。
- Foundation 现状：`paytech/foundation/paytech-http-problem`（`ApiProblemResponse`、`ProblemFieldError`、`ApiProblemProfile.validate`）；`paytech-mvc-web-starter` 的 `MvcProblemExceptionMapper`（目前只有 `VALIDATION_ERROR` 路径产生 `errors[]`）；`paytech-common-core` 的 `FailException`；`paytech-http-client-support` 的 `HttpServiceClientInterceptor` / `HttpProblemDecoder`（`DecodedProblem.fields` 只在 422 `VALIDATION_ERROR` 时转成 `HttpRequestValidationException`，合同 Code 走 `FailException(contractError)` 会**丢掉字段**）。
- client-master：`domain/onboarding/ClientDraftPatch.normalize`（遇第一处错误即 `invalid()`）、`service/processor/ClientDraftProcessor.save`（`catch RuntimeException → FailException(INVALID_DRAFT)`）、`ClientMasterWebConfiguration` 的状态解析；测试 `SaveDraftProblemContractTest`、`ResubmitAfterReturnTest`。
- client-console-bff：`ClientOnboardingProcessor` 对 `drafts.save` 的调用与 BFF 侧错误映射；`client-console-web`（`paytech/client-console-web` 或旧路径，自己定位）保存资料页如何消费 Problem `errors[]`（`VALIDATION_ERROR` 已有逐字段提示的话复用同一渲染）。

## 2. 改动清单

1. **Foundation（通用）**：
   - `paytech-common-core`：给 `FailException` 增加可选字段错误（例如子类 `FieldFailException(CodeMessageEnum code, List<FieldError> errors)` 或 `FailException` 的 `fieldErrors()` 扩展点，二选一并说明），字段路径与消息都是业务方给定的稳定值，不含敏感原值。
   - `paytech-mvc-web-starter`：`MvcProblemExceptionMapper` 对携带字段错误的 `FailException` 输出 `errors[]`（保持 Owner 状态解析与 Problem 校验规则：非空数组、field / message 非空、去重排序）。
   - `paytech-http-client-support`：合同 Code 的 4xx Problem 若带 `errors[]`，`classifyClientCode` 构造的 `FailException` 要携带这些字段错误（而不是只保留 Code），使 BFF 重新映射时 `errors[]` 继续存在；`HttpProblemDecoder` 目前只收集 `field`，需连同 `message` 一起解码。
   - 三处各补 `auto` 单测（已有测试类里加用例优先）。
2. **client-master**：`ClientDraftPatch.normalize` 收集全部字段问题（路径如 `contact.phone`、`organization.legalName`；消息用中文、说明约束，例如"手机号须为 E.164 格式，如 +8613800138000"），一次性抛 `INVALID_DRAFT` + `errors[]`；dirtyFields 与 values 不一致等结构性错误用一个 `field` 为对应路径或 `dirtyFields` 的条目表达。`ClientDraftProcessor` 不再用 `catch RuntimeException` 兜底吞掉信息（仍要保证非预期异常不泄漏内部细节）。`SaveDraftProblemContractTest` 扩展：断言 `errors` 含 `contact.phone`，并继续通过 `ApiProblemProfile.validate`。
3. **client-console-bff**：确认 422 `INVALID_DRAFT` + `errors[]` 原样透传（加一例 BFF `auto` 测试：Mock client-master 返回带 `errors[]` 的 Problem，BFF 响应含同样 `errors[]`）。
4. **client-console-web**：保存资料页把 `errors[]` 按 `field` 映射到表单项标红 / 提示；若页面已对 `VALIDATION_ERROR` 做了同样处理，复用同一逻辑。前端改动最小化，附上手工验证步骤（本机环境 `https://127.0.0.1:18443`）。
5. 不改：错误 Code、HTTP 状态、API 路径、业务校验规则本身。

## 3. 验证

1. `cd paytech && ./mvnw -B verify -pl foundation/paytech-common-core,foundation/paytech-http-problem,foundation/paytech-mvc-web-starter,foundation/paytech-http-client-support,client-master/paytech-client-master-test,client-console-bff/paytech-client-console-bff-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment` 全绿（用 `verify`，不要用 `test`：`-am test` 会让 paytech-messaging 的测试资源经 target/test-classes 泄漏进消费方上下文）。
2. 前端：`npm --prefix <client-console-web> test`（若有）与构建通过。
3. 本机真实链路：用 `verification/local-e2e` 的 `acceptance_flow.Client` 登录一个新账号，PATCH 一个坏手机号，经 BFF 收到 422 `INVALID_DRAFT` 且 `errors[0].field == "contact.phone"`（把这一步写成小脚本放 `verification/local-e2e/`，命令与输出记报告）。本机服务由主会话重新部署，你只需要在报告里说明需要重启 client-master 与 client-bff。

## 4. 禁止事项

- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送；不操作容器 / 不杀进程（本机有全链路服务在跑）；不改 Gateway。

## 5. 交付物

1. 短分支提交，前缀 `refactor(step-16): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；Foundation / client-master / BFF / 前端可分开提交。
2. 报告 `tasks/16-draft-field-errors-report.md`：Foundation 扩展点设计与理由、Problem 合同核对、字段消息清单、测试计数、前端手工验证步骤、待确认。
3. 最终回复两行：commit 哈希 + 测试计数。
