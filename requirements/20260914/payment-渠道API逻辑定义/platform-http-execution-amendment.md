# Platform 配置管理与内部 HTTP 执行

本稿落实[2026-09-21 用户决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)：通用 API 配置和执行归 Platform，Payment 等业务系统通过内部 HTTP 调用。需求目录保留原路径，以保持历史引用；路径中的 payment 不表示当前 Owner。

## 1. 职责与存储

| 位置 | 职责 |
|---|---|
| Operations Web / BFF | 配置维护页面、获准的页面聚合与管理请求适配 |
| Platform Integration | 配置保存与校验、一致读取、字段映射、注册函数/脚本执行、协议通信与响应解释 |
| Platform 数据库 | 保存接入分组、API、公共脚本、公共模型和响应合同的当前定义 |
| Payment Channel / Routing / Execution | 支付账号与 MID、选路、冻结身份、动作选择、业务资格、交易状态与恢复 |

业务系统只依赖 Platform 的轻量 HTTP 契约，不依赖其 Repository/DataObject，也不直接查询 Platform 数据库。执行引擎由 Platform 装配；实现可以按内部模块拆分，但不再以业务宿主引入公共执行 JAR 作为当前接入方式。

## 2. 调用链

Payment 在本地完成本次操作的业务准备与授权，并结束本地事务后，调用 Platform 内部执行入口。Platform 在自己的短只读事务内取得 API、脚本、引用模型和响应合同的一致定义，结束事务后才执行数据函数、材料解析和外部通信。Platform 返回结果后，Payment 另开必要的短本地事务应用支付结果。

逻辑调用能力保持：`execute(integrationCode, apiCode, context)`。

Platform 根据获准配置发起外部请求，并返回 `{executeStatus, outputs}`。具体 HTTP 路径、DTO、调用权限、连接引用以及错误与超时合同属于下一步详细设计，不把逻辑签名等同完整 Wire。标准支付动作如何选择 integrationCode/apiCode 仍由 Payment 适配，不假设 channelCode 自动等于 integrationCode。

管理入口通过 Operations BFF；执行入口是内部服务 HTTP，不经 BFF 或公网 Gateway。调用方不能上传脚本、任意目标 URL 或配置正文来替换所选 API。

## 3. 支付事实与运行材料

Payment 五张 Channel/Account/MID/Capability 配置表仍归 Payment。Platform 不选择 MID，不判断退款资格，不读取 Payment 数据库，不接管原交易冻结账号含义。

现有 GetChannelExecutionConfigProcessor 的结果仍是 Payment 内部受控执行结果，不能直接序列化为新 Platform 请求。凭据和连接材料需要一个明确的跨系统交接合同：谁验证原账号、Platform 如何取得本次获准材料、使用哪个 Caller/Purpose，以及材料的内存和日志边界。此处不新增 Token 透传或 Platform Reveal 权限，也不要求把真实 Secret 放进普通 context。

需要卡原值的调用继续受既有 Card 专项边界约束；本次改由 Platform 执行不自动扩大 PAN/CVV 等材料的可见范围。具体卡渠道适配与材料交接需在真实支付接入前收敛。

## 4. 系统函数

纯组装、转换和密码机制等注册函数在 Platform 的执行环境中调用。需要业务数据的注册函数通过事实 Owner 的内部只读 HTTP 能力获取，不直接访问其他系统表；真实函数、参数和权限逐项注册。

调用方可提前准备的数据直接放入声明过模型的 context，避免无必要的回查。仍保留按配置调用数据函数的能力；这些查询不得夹带支付、退款、邮件发送等另一笔业务操作。任何远程函数调用均在数据库事务之外。

## 5. 一次执行与结果不确定性

既有合同保持：执行过程无业务/通信流水、无请求重放存储、无自动重试/重定向或换路；单次收到的 execute 最多发起一次目标外部操作。函数查询不计作目标操作，但不得暗中执行第二个目标动作。

Payment→Platform 与 Platform→外部渠道是两个网络边界。任何一段在可能发送后超时、断连或响应丢失，都不能证明外部没有执行。调用方也不能通过 HTTP Client、负载均衡或代理自动重发 execute；业务恢复由原 Owner 按原操作身份处理。

无执行持久化意味着 Platform 不提供“按调用 ID 查询之前执行结果”或跨进程故障恢复承诺。Request ID 用于现有技术关联，不是执行结果查询键或业务幂等凭据。API 的执行结果仍为 SUCCESS / FAIL / ERROR 与 outputs；HTTP 入口自身错误沿用项目 Problem 规范，精确映射待 Wire 细化。

## 6. 表结构评审范围

当前讨论稿的五个配置对象计划落在 Platform：t_integration、t_integration_api、t_integration_script、t_integration_data_model、t_integration_response_contract。API/脚本归接入分组，模型/响应合同可跨分组复用。名称和字段仍需逐表确认，不据此增加正式模型统计。

不为运行输入、函数返回、HTTP 报文、执行结果、幂等或重试建表。配置保存后新调用使用新定义，已开始执行的调用继续使用一次加载的完整内容。模型引用、并发保护、大小预算和受控目标定义在物理设计中一并细化。

## 7. 前版材料与当前状态

[通用执行边界](./generic-execution-amendment.md)、[公共模型与响应场景](./model-library-and-response-scenes.md)、[公共脚本](./public-script-http-context-design.md)中未涉及 Owner/执行位置的规则继续有效。前版 Payment 两表 SQL、公共方案和独立脚本分离等内容仅保留为历史材料。

设计、实现、数据库、验证与部署的实际状态统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-integration-http-execution-20260921)。
