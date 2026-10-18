# 任务卡 16 报告 · 入驻资料校验错误带字段清单

- 分支：`refactor/step-16-draft-field-errors`（worktree `/Users/kenny/Desktop/Workspace/Code-MoR-step16`，起点 `71ee320b`，即 refactor-main@85e0ab79 之后的任务卡提交）
- 结论：client-master 的 422 `INVALID_DRAFT` 现在一次返回全部字段问题 `errors[{field,message}]`；Foundation 让带字段清单的 Owner 业务码经 Client BFF 继续保留；页面按字段标红并提示。错误 Code、HTTP 状态、API 路径和业务校验规则均未改变。
- 本机真实链路需要主会话重新部署 **client-master** 与 **client-console-bff**（以及 client-console-web 静态资源）后再跑第 3.3 条脚本；改动前基线已跑（见第 6 节）。

## 1. 已读与归属

- 已读：任务卡第 1 节全部路径；`agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md`、对应设计第 3、4、6 节；`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 4、5 节；`agent-docs/UNIT_TEST_GUIDE.md`；`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节。
- 所属入口：client-master `ClientDraftProcessor.save`（C04 保存资料）与 `create`（开始申请）。`ClientDraftPatch.normalize` 在任何事务之前执行；类型限制 `requireType` 在 `prepareSave` 只读事务内执行，失败即回滚、不写库。
- 本地原子写入与事务外步骤：未改变。校验失败发生在 Vault `protect` 与写事务之前，因此不调用 Vault、不落库。
- 与任务卡描述的差异：起点代码中 `ClientDraftProcessor.save` 没有 `catch RuntimeException`；宽泛捕获在 `create` 解析 `clientType` 处，以及 `ClientDraftPatch` 内部把首个异常转成 `INVALID_DRAFT`。两处都已改为逐字段报告。

## 2. Foundation 扩展点设计与理由

任务卡给出的二选一是在 `paytech-common-core` 加 `FieldFailException` 或给 `FailException` 加 `fieldErrors()`。实际采用了更小的第三种做法：**复用 `paytech-http-problem` 已有的 `RequestBodyValidationException`，增加一个接受 Owner 错误码的构造器**。

- 理由：字段错误类型 `ProblemFieldError` 已在 `paytech-http-problem`，而它依赖 `paytech-common-core`。在 common-core 加字段错误就要再造一个同义的字段错误类型，并在三处之间互相转换。`RequestBodyValidationException` 本身就是"本地已投影的 Body 字段错误"，`INVALID_DRAFT` 正是这类错误；client-master-core、mvc-web-starter、http-client-support 都已依赖 http-problem，不新增依赖。`FailException` 不变。
- `RequestBodyValidationException(CodeMessageEnum, List<ProblemFieldError>, Throwable)`：detail 使用错误码默认消息；字段与文案非空校验沿用原规则；原构造器委托到 `VALIDATION_ERROR`。
- `MvcProblemExceptionMapper`：`VALIDATION_ERROR` 仍固定 422；Owner 码经 `ApiErrorStatusResolver` 解析 4xx，默认 422，非 4xx 回退 422；errors 按 field、message 去重并稳定排序（与 Bean Validation 路径共用同一排序方法）。
- `HttpProblemDecoder`：解码结果从只有 field 改为 `ProblemFieldError`（field 与 message），并去重；`HttpRequestValidationException.fieldPaths()` 对外不变。
- `HttpServiceClientInterceptor`：已注册的合同 Code，在显式 `withRequestValidationFailures()`、请求带 `@RequestBody`、严格 Profile 且 errors 非空时，转换为 `RequestBodyValidationException(合同码, errors)`；其他情况仍是原来的 `FailException(合同码)`。远端 detail 仍不外显，使用合同默认消息。目前没有其他生产者对非 `VALIDATION_ERROR` 输出 errors，因此对既有消费方无行为变化。

## 3. Problem 合同核对

- 六个必填成员、`about:blank`、标题与状态一致、Request ID 头体一致、六位小数时间：`SaveDraftProblemContractTest` 每例都过 `ApiProblemProfile.validate`；BFF 测试核对 Request ID 是浏览器请求的值，上游 Request ID 与 detail 不出现。
- errors：非空数组，每项只有 field 与 message，去重排序；空清单不输出 errors。
- 字段路径：与页面和 `dirtyFields` 同名（如 `contact.phone`），结构问题落在 `dirtyFields` 或 `values`。
- 字段消息：说明约束，不回显提交值（测试断言响应不含提交的手机号、邮箱、日期）。
- **与决定 D3 的偏差（待确认 1）**：D3 写明字段错误使用顶层 `VALIDATION_ERROR`，业务执行错误不带 errors。本任务按用户确认让 Owner 业务码 `INVALID_DRAFT` 携带 errors。它仍是请求 Body 字段校验，不是业务执行检查，但需要在决定文档中登记这一例外。

## 4. 字段消息清单（client-master `ClientDraftPatch`）

| 字段类型 | 消息 |
|---|---|
| 国家代码 | 须为两位国家代码，如 CN |
| 币种 | 须为三位币种代码，如 USD |
| 日期、有效期边界 | 须为 YYYY-MM-DD 格式的有效日期 |
| 金额 | 须为非负金额，整数最多 16 位、小数最多 8 位 |
| 邮箱 | 邮箱格式不正确 |
| 手机号 | 手机号须为 E.164 格式，如 +8613800138000 |
| 网址 | 须为 http 或 https 网址 |
| 文本超长 | 长度不能超过 N 个字符（定长代码和日期超长时改报格式消息） |
| 含 NUL 字符 | 包含不支持的字符 |

| 结构问题 | field | 消息 |
|---|---|---|
| dirtyFields 为空、缺失或超过 50 项 | `dirtyFields` | 须列出 1 到 50 个修改字段 |
| dirtyFields 含不支持的路径 | `dirtyFields` | 包含不支持的字段 |
| dirtyFields 重复 | `dirtyFields` | 同一字段不能重复列出 |
| values 缺失 | `values` | 须提供修改字段的取值 |
| 分组为空 | `values` | 分组 X 不能为空 |
| values 中有未列入 dirtyFields 的字段 | 该字段路径 | 该字段未列入 dirtyFields |
| dirtyFields 列出但 values 没有 | 该字段路径 | 已列入 dirtyFields，但 values 中没有该字段 |
| 申请类型不支持的字段 | 该字段路径 | 当前申请类型不支持此字段 |
| 人员资料不支持的字段 | 该字段路径 | 人员资料不支持此字段 |
| 开始申请的类型不在目录 | `clientType` | 须为 INDIVIDUAL 或 ORGANIZATION |

## 5. 测试

命令（任务卡 3.1）：

```bash
cd paytech && ./mvnw -B verify -pl foundation/paytech-common-core,foundation/paytech-http-problem,foundation/paytech-mvc-web-starter,foundation/paytech-http-client-support,client-master/paytech-client-master-test,client-console-bff/paytech-client-console-bff-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

结果：`BUILD SUCCESS`，Reactor 35 个模块，合计 1150 个测试，0 失败、0 错误、0 跳过。本任务相关模块：

| 模块 | 测试数 |
|---|---|
| paytech-common-core | 130 |
| paytech-http-problem | 12 |
| paytech-http-client-support | 118 |
| paytech-mvc-web-starter | 161 |
| paytech-client-console-bff-test | 81 |
| paytech-client-master-test | 63 |

第一次运行暴露一个真实缺陷：超长国家代码先报长度消息，已改为定长格式字段报格式消息。第二次失败是测试本身的问题：坏手机号 `13800138000` 恰好包含在提示示例 `+8613800138000` 里，已换成 `13912345678`。

新增或扩展的用例：

- `RequestBodyValidationExceptionAutoTest`（http-problem，新类，3 例）：公共入口、Owner 码保留码与默认消息、清单不可变、空白与缺码拒绝。
- `MvcProblemResponseAutoTest#test_ownerCodeFieldErrorsKeepOwnerCodeAndSortedErrors`：Owner 码默认 422，Owner 解析为 400 时取 400，errors 去重排序，Cause 不外显。
- `HttpServiceClientFactoryTest#test_contractCodeFieldErrorsArePreservedOnlyWhenExplicitlyEnabled`：显式启用时保留 field 与 message 并去重，detail 用合同默认消息；未启用时仍是普通 `FailException`。无 Body 的 GET 仍不保留字段，由既有 `test_validProblemAllowsUnknownTopLevelExtensions` 覆盖。
- `SaveDraftProblemContractTest`（client-master，从 1 例扩到 5 例）：单字段手机号、多字段一次返回且不回显原值、dirtyFields 与 values 不一致、个人申请写组织字段、开始申请的 clientType 不在目录。
- `ClientOnboardingHttpAutoTest#invalidDraftFieldErrorsReachBrowser`（BFF）：模拟 client-master 返回 422 `INVALID_DRAFT` 加 errors，BFF 响应保留同一 errors、合同默认 detail 与浏览器 Request ID，不泄露上游 Request ID 和 detail。
- `paytech-common-core` 未改动，没有新增用例。

前端（`client-console-web`）：

```bash
npm --prefix client-console-web test     # 62 通过（新增 1 例：fieldIssues 只映射页面上存在的字段）
npm --prefix client-console-web run build # tsc --noEmit 与 vite build 通过
```

worktree 没有 node_modules，构建时临时软链主工作区同一 package-lock 的 node_modules，构建后已删除软链和 dist。

## 6. 本机真实链路（任务卡 3.3）

脚本：`verification/local-e2e/draft_field_errors.py`。它注册新账号，创建个人申请，一次 PATCH 坏手机号与坏出生日期，期望 422 `INVALID_DRAFT` 且 errors 为 `contact.phone`、`individual.birthDate`、响应不回显原值；再用合规手机号保存，期望 200 SAVED。

```bash
cd requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e && python3 draft_field_errors.py
```

改动前基线（2026-10-18，运行中的旧版服务）：

```text
PASS C04 创建个人申请 | HTTP 201 applicationId=237520614724743168 applicationNo=CA-237520614724743168 operation=CREATED
FAIL C04 坏手机号与坏日期 → 422 INVALID_DRAFT + errors[] | HTTP 422 code=INVALID_DRAFT requestId=1c085d5d-f9f8-4850-be41-e5fb589bdb4d detail=入驻字段不符合约定 fields=[] leaked=False
合计 8/9 通过
```

主会话重新部署 client-master 与 client-console-bff 后重跑，预期 10/10 通过。本任务未操作容器或进程。

## 7. 前端手工验证步骤（`https://127.0.0.1:18443`）

1. 重新部署 client-console-web、client-console-bff、client-master 后，用新账号登录并开始个人申请。
2. 在"业务联系电话（含国际区号）"填 `13800138000`，出生日期留空，点"保存资料"。
3. 预期：顶部提示"入驻字段不符合约定"及请求编号；电话输入框标红，下方显示"手机号须为 E.164 格式，如 +8613800138000"。
4. 修改该电话为 `+8613800138000`，红框和提示随输入立即消失；保存后显示"资料已保存"。
5. 只有页面上存在的字段会显示逐字段提示；`dirtyFields` 等结构问题只走顶部提示。

## 8. 待确认

1. **决定 D3 的例外登记**：是否在 `2026-09-06-system-wide-rfc-9457-problem-details.md` 与设计第 3.2、4.1 节登记"Owner 业务码的 Body 字段校验可附 errors"。备选是把 `INVALID_DRAFT` 的字段校验改成顶层 `VALIDATION_ERROR`，但这会改错误 Code，违反本任务约束。
2. **Foundation 扩展点位置**：采用 http-problem 的 `RequestBodyValidationException` 新构造器。备选是任务卡写的 common-core `FieldFailException` 或 `FailException#fieldErrors()`，代价是新增一个与 `ProblemFieldError` 同义的类型。
3. **HTTP Client 信任远端字段文案**：合同 Code 的 errors 连同 message 保留，前提是消费方显式 `withRequestValidationFailures()` 且请求带 Body。原 `VALIDATION_ERROR` 路径仍只保留路径。备选是增加单独开关，但当前唯一消费方是 Client BFF，注册合同 Code 已是显式选择。
4. **未纳入本次的 `INVALID_DRAFT` 抛出点**：`ClientRelationValues`、`ClientDocumentProcessor`、`ClientPeopleController` 的空 Body 检查、`ClientDraftCommand` 仍只返回 Code。人员编辑弹窗使用同一 `ClientDraftPatch`，因此已带 errors，但页面只在"保存资料"表单逐字段显示，弹窗仍只显示顶部提示。是否扩展另行决定。
