# 入驻写接口请求幂等迁移：方案（评审稿）

> 状态：2026-10-15 用户回复“同意”，第 6 节三项按建议确认并实施；实施差异见第 7 节，实现与验证只见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。
> 依据：[请求幂等职责决定](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)与[开发规范第 5.4 节](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#54-请求幂等与业务防重)：HTTP `Idempotency-Key` 只由 Gateway 处理；BFF 和业务系统不接收、不转发、不做本地请求重放；业务防重靠业务身份、唯一约束和状态。
> 先例：Platform Gateway 配置与 API Scope 写接口已按同一规则迁移（[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)），前端继续发 Key 供 Gateway 将来使用。

## 1. 现状

入驻写接口仍按旧方式处理 Key：

| 层 | 现状 |
|---|---|
| Client Web | 每个写命令生成 Key，网络失败时用原 Key 重试 |
| Gateway 路由登记 | C04、C06—C13、F01、F02 和 O03—O09 的 `idempotency-key` 登记为“CORS 允许 + 转发”（`db/complete_registration_plan.py`） |
| Client BFF | `ClientOnboardingController` 8 个、`ClientOnboardingFileController` 2 个写接口要求该 Header 并转发 |
| CCT | C01、C04、C07—C13 要求 Header；`ClientDraftServiceImpl`、人员和材料服务把 Key、请求摘要和回执写进本库 `t_command_idempotency`，重复 Key 返回原回执，不同内容返回 `IDEMPOTENCY_CONFLICT` |
| Platform | F01 开始上传、F02 完成上传要求 Header，`ClientFileCommandService` 按 Key 记录并重放 |

这些是规范禁止的“服务本地请求重放”。**Gateway 请求幂等目前还没有实现**（台账“全项目请求幂等职责规范”一节仍为 Pending），所以迁移后、Gateway 实现前，网络重试只能靠下面第 2 节的业务防重。

## 2. 去掉 Key 后各接口的业务防重

| 接口 | 业务身份 / 保护 | 同一请求重复到达时 |
|---|---|---|
| C01 开始申请 | 账号未完成名额唯一索引；已有进行中申请时恢复 | 返回原申请（200 RESTORED），已是现有行为 |
| C04 保存草稿、C08 保存人员、C10 修改关系 | 字段覆盖写，天然幂等 | 再写一次相同值，无副作用 |
| C07 新增人员 | **没有业务身份** | 会多建一名人员；客户可在列表中删除对应关系。见第 4 节风险 |
| C09 新增关系 | `uk_party_relation_identity`（组织、人员、类型） | **改为**：同一申请下已有相同关系且内容相同时返回原关系；内容不同仍 409 `RELATION_ALREADY_EXISTS` |
| C11 删除关系、C13 删除材料 | 当前申请下的记录 | **改为**：本申请下已不存在时返回 204，不报 404（其他申请或无权限仍按原规则拒绝） |
| C12 关联材料 | `uk_app_doc_client`（申请、主体、要求、类型、文件） | **改为**：相同组合已存在时返回原记录 |
| F01 开始上传 | 无业务身份 | 多建一条上传记录；未完成的上传在有效期（最长 900 秒）后作废，不产生文件 |
| F02 完成上传 | uploadId：一次上传只能产生一个文件 | **改为**：该上传已完成时返回已产生的文件，不再报状态冲突 |
| C14 提交 | 已按申请状态防重 | 不变 |

## 3. 改动范围

| 系统 | 改动 |
|---|---|
| CCT | API 合同去掉 `Idempotency-Key` 参数；删除入驻相关的本地回执读写（`replay`、`receipt`、`insertReceipt`、`command` 查询）和 `IDEMPOTENCY_CONFLICT` 的入驻用法；按第 2 节补 C09/C11/C12/C13 的业务防重；`t_command_idempotency` 表和历史数据不动 |
| Platform | F01/F02 合同去掉 Header；删除 `ClientFileCommandService` 的入驻用法；F02 按 uploadId 返回已完成结果；锁 Key 从“命令 Key”改为实际资源（F01 按账号 + 申请 + 主体，F02 已按 uploadId）；命令表不动 |
| Client BFF | 两个 Controller 不再接收、校验或转发该 Header；`ClientOnboardingWebConfiguration` 中的 Key 校验删除 |
| Client Web | 继续生成并发送 Key（给 Gateway 将来使用），不改 |
| Gateway 路由登记 | 生成脚本把上述路由的 `idempotency-key` 改为只“CORS 允许”、不转发；O03—O09 同样处理（下游早已不用）。只改生成脚本和计划文件；本机与 mor-test 的实际登记变更属于发布动作，另行授权 |
| 数据库 | 无 DDL |

测试：CCT、Platform、BFF 现有用例中断言“缺 Key 400 / 同 Key 重放 / 改内容冲突”的改为不带 Key；补第 2 节“改为”各行的重复请求用例；跑 CCT、Platform、BFF 全量与 Web 测试。

## 4. 风险

1. **Gateway 实现前的重复新增人员（C07）。** 网络超时后 Web 用原 Key 重试，请求会再次到达 CCT，多出一名人员。概率低（需响应丢失），后果可见、可由客户删除，不涉及资金。如果不能接受，可以在 C07 增加一个由 Web 生成、随请求提交的人员草稿编号作为业务身份，但这实质上是换个名字的请求 Key，不建议。
2. **多出的上传记录（F01）。** 只占一条记录，自动过期，不产生文件。
3. **混合版本窗口。** 新旧版本同时运行时，旧 BFF 仍转发 Header、新 CCT 忽略即可；新 BFF 不发 Header、旧 CCT 会 400。发布顺序须先 CCT/Platform，后 BFF。

## 5. 不做

- 不实现 Gateway 请求幂等（需另出 Gateway 方案）。
- 不删除 `t_command_idempotency`、Platform 命令表及其历史数据。
- 不改 mor-test / 本机的实际路由登记，不部署。
- 真实进程联调和浏览器端到端放到本迁移之后单独做。

## 6. 需要你确认

1. 按第 2 节逐接口的业务防重替换本地重放，包括 C09/C11/C12/C13/F02 的“改为”行为。（建议）
2. 接受第 4 节风险 1：Gateway 实现前，C07 偶发重复人员由客户删除。（建议）
3. 改动范围按第 3 节：CCT、Platform、BFF 改代码，Web 不改，路由只改生成脚本不改实际登记。（建议）

## 7. 实施记录（2026-10-15）

1. **回执字段保留。** CCT 回执的 `commandId`、Platform 完成回执的 `commandId` 不删字段：CCT 恒为 null，Platform 等于 uploadId，避免改 BFF/Web 模型。
2. **Vault 保护键。** 联系方式 Protect 原来用请求 Key 派生下游操作键；改为每次随机 UUID。STABLE 模式下同值仍得到同一 Token；不用联系方式原值派生，避免把可撞库的摘要交给 Vault。
3. **错误码。** CCT 删除 `IDEMPOTENCY_CONFLICT`；Web 只在提示文案列表里出现该字符串，不影响行为。
4. **F01 锁。** 由命令 Key 改为实际资源 `账号:申请:主体`。
5. **测试缺口。** F02 重复完成没有 HTTP 测试：Platform 测试环境没有 CUSTOMER 会话和 CCT 文件目标模拟（台账原有缺口）；由本机全链路验收补充。

