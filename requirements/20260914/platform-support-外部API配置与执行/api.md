# 渠道 API 配置管理 HTTP 合同

> **2026-10-13 迁移后继：** 本目录已由 `requirements/20260914/payment-渠道API逻辑定义/` 迁至 Platform 命名目录。本文为历史资料，保留原日期与适用范围；现行合同见[需求](./requirement.md)、[六表数据设计](./data-design.md)和[流程设计](./flow-design.md)，本文被取代的范围见[技术说明第 5 节](./technique.md#5-历史资料与取代关系)。

**公共脚本页面修订：** [公共脚本与 HTTP 对象](./public-script-http-context-design.md)已补齐脚本目录、源码、阶段对象权限、引用及参数绑定的主要 Pencil 状态。方案与独立脚本合并，当前管理 Wire 仍待按该模型及通用 Owner 补齐；画板操作不是已冻结或已实现的 HTTP 端点。

**2026-09-15 增量：** [公共模型与响应场景设计](./model-library-and-response-scenes.md)新增对象模型/响应合同维护、字段结构与引用影响，调整 API 输入/输出引用。下列 13 项操作尚未覆盖该增量；需在通用 Owner 确定后统一补齐管理 Wire 和注册，不能把页面新增操作算作已经确定的 HTTP 端点。

**通用化状态：** [通用执行修订](./generic-execution-amendment.md)取消支付方式配置维度并调整输入/输出合同。本文仍为原 Payment Owner 与分组下的管理提案；路径、DTO、目录、权限注册和唯一性均待按通用管理 Owner 同步，不是可直接实施的新版 Wire。现有已批准的系统所有权不会因本文自动迁移。

**覆盖范围：** 下列 13 项操作是前版 API/公共方案管理草稿。当前[模型整理](./model-and-mapping-design.md)增加独立脚本，脚本列表、详情、保存、删除、引用影响及校验合同尚需补齐；A02/A13 的脚本目录、限额与诊断增量也未冻结。本文不能作为三对象完整管理 Wire，不因 SCRIPT 模型已描述就宣称这些端点已支持脚本；既有权限与 Owner 边界保持不变。

本文是[阶段 3 设计](./design.md)的接口附件，全部端点均为拟新增；不修改现有 Execution/Commerce HTTP，不新增公网资金执行端点。正式 Java 合同放在 Payment API JAR 的 channelconfiguration 能力包，类型名不加版本后缀，接口使用 @HttpExchange，DTO/枚举遵循[Java 规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md#4-api-模块与内部-http-契约)。

## 1. 路径、身份与授权

同一项操作在三处的完整路径为：

- Payment Owner：`/channel-configuration/v1` + 下表相对路径，私有 HTTP。
- Operations BFF：`/channel-api-admin/v1` + 相对路径。
- 浏览器经 Gateway：`/api/operations/channel-api-admin/v1` + 相对路径。

Gateway 使用既有 OPERATIONS_JWT/HUMAN_PERMISSION。BFF 从可信入口恢复 OperatorContext，调用 PLT-I02 检查该操作权限；Payment 管理入口亦在事务外按可信操作者校验权限，再核对 Channel 和目标定义的业务归属。浏览器 Body 不接收 actorId、Tenant、任意 Account 或权限声明。内部可信 Header 及网络限制遵循既有应用边界，不以私有路径代替授权。

权限为 `PAYMENT_CHANNEL_CONFIG_READ` 和 `PAYMENT_CHANNEL_CONFIG_MANAGE`。MANAGE 不隐含 READ；完整编辑角色应同时持有两者，页面按两个权限分别控制。检查草稿需要 MANAGE，不能给纯只读用户提供可写草稿入口。管理权仅管理定义，不授予执行资金、访问账户 Secret 或读取交易明文的权限。

## 2. 新增操作清单

| 编号 | Method | 相对路径 | 权限 | 成功响应 |
|---|---|---|---|---|
| A01 | GET | `/channels` | READ | 200 Channel 分页 |
| A02 | GET | `/channels/{channelCode}/catalog` | READ | 200 编辑目录 |
| A03 | GET | `/channels/{channelCode}/apis` | READ | 200 API 摘要分页 |
| A04 | GET | `/channels/{channelCode}/apis/{apiId}` | READ | 200 API 详情 |
| A05 | POST | `/channels/{channelCode}/apis` | MANAGE | 201 新 API 详情 |
| A06 | PUT | `/channels/{channelCode}/apis/{apiId}` | MANAGE | 200 保存后的 API 详情 |
| A07 | POST | `/channels/{channelCode}/apis/{apiId}/delete` | MANAGE | 204，无 Body |
| A08 | GET | `/channels/{channelCode}/schemes` | READ | 200 方案摘要分页 |
| A09 | GET | `/channels/{channelCode}/schemes/{schemeId}` | READ | 200 方案详情与当前引用 |
| A10 | POST | `/channels/{channelCode}/schemes` | MANAGE | 201 新方案详情 |
| A11 | PUT | `/channels/{channelCode}/schemes/{schemeId}` | MANAGE | 200 保存后的方案详情 |
| A12 | POST | `/channels/{channelCode}/schemes/{schemeId}/delete` | MANAGE | 204，无 Body |
| A13 | POST | `/channels/{channelCode}/validate` | MANAGE | 200 检查报告，包含 valid=false 的业务检查结果 |

删除用明确命令路径承载旧时间条件，避免 DELETE Body 的跨代理兼容问题。每个请求 Header/Query 拒绝重复单值与未知参数。apiId/schemeId 为正十进制 BIGINT 字符串；无该 Channel 下目标一律 404，不回报目标属于哪个其他 Channel。

A01 Query 为 page、size、可选精确 channelCode；A03 为 page、size、可选精确 paymentMethodCode/apiCode；A08 为 page、size、可选精确 schemeCode。其他 GET 不接受 Query。page 默认 1，size 默认 10/最大 100，返回项目标准 `{items,total,page,size}`；空列表为 []，不以404表示。A01 按 channelCode，A03 按 paymentMethodCode/apiCode，A08 按 schemeCode 升序稳定排序。筛选空字符串拒绝，前端清空时省略参数。

## 3. 查询响应

### A01 Channel 摘要

每项为 `{channelId,channelCode,channelName,channelStatus}`，均非空字符串。来自现有 Channel 表；不扩展 Channel/Account/MID 新增修改接口，不让创建 API 顺便创建渠道。

### A02 编辑目录

返回 `{channel, paymentMethods, configuredMethods, contextTypes, capabilities, functions, expressionProfile, protocolProfiles, runtimeInputs, endpointCodes, limits}`。

- paymentMethods 是权威目录中可选的精确代码/名称；没有已批准目录值时为空并说明原因，不用 CARD_TEST 或临时码替代真实目录。
- configuredMethods 为本 Channel 按 payment_method_code 分组的已有 API 数量，不保存额外关系。前端按方式展开后用 A03 分页加载，不把第一页冒充完整 API 树。
- contextTypes/functions/capabilities 是注册描述符，含字段类型、名称、输入输出/参数、适用阶段和 implementationAvailable；不是 Java 类名/Bean 名，不返回实现代码。functions 供统一系统函数选择器使用，由开发者注册；没有函数新增、编辑或查询策略维护接口。
- contextTypes 为 `{contextType, contextName, displayName, description, owner, capabilityCode, fields, implementationAvailable}`，fields 为 FieldSet；例如 REFUND / refundContext。可用性从 capabilityCode 对应的能力事实派生，缺投影实现进入 missingCapabilities。函数描述符含 allowedChannelCodes，按渠道与上下文交集筛选；具体成员及固定校验见[最小注册目录](./registry-contract.md)。expressionProfile 为 `{engine, capabilityCode, implementationAvailable, pureFunctions}`，engine 固定 AVIATOR；pureFunctions 各项为 `{name, displayName, description, capabilityCode, argumentOrder, inputDefinition, outputDefinition, implementationAvailable}`，argumentOrder 显式规定位置参数顺序，不依赖 JSON 键顺序；类型使用定义合同的 FieldSet。输出统一为 value 字段，name 是允许调用的表达式函数名。纯函数只处理已有值，不能发起系统查询；其 capabilityCode 对应 capabilities 中的同一实现可用性事实，不重复维护第二份状态。表达式语法/变量规则固定于定义合同，容量在 limits 返回。
- endpointCodes 是该 Channel 允许选择的目标用途代码及名称；不返回账户凭据、KMS 地址或任意具体账户的敏感运行材料。实例按冻结 Account 解析实际域名。
- capabilities 使用[协议处理合同](./capability-contract.md#1-目录与具体合同)中的 FIXED/SPECIALIZED 判别结构；只有 usage=STEP 可以用于处理步骤。protocolProfiles 为 `{response, crypto}` 两个描述符数组，runtimeInputs 为受控运行路径、类型及允许消费能力的描述符数组；完整形状见[协议目录示例](./assets/protocol-capabilities.example.json)。这些都是只读元数据，没有真实材料、运行句柄或新维护端点。没有注册密码 Profile 时 crypto=[]，不能只输入一个算法名称就保存为有效方案。
- limits 是[字段合同](./definition-contract.md)中的界限。目录总响应最多 262,144 字节；超出须调整已评审目录组织，不截断成表面成功。

目录是配置合法性所需的 Owner 元数据；页面排序、分组、面包屑和选择器展示由 BFF/Web 负责。

### A03/A04 API

摘要 `{apiId,paymentMethodCode,apiCode,apiName,description,updateTime}`；description 可为 null，其余必填。详情补充 `{channelCode,definition,createTime,executionAvailability}`。definition 为严格 ApiDefinition；时间均为 UTC、固定六位微秒字符串。

executionAvailability 为 `{available, missingCapabilities, missingFunctions}`，只表示当前已登记实现是否齐备，不证明特定 Account、资金资格或 NI 联调已就绪。运行输入和真实资金可用性不在管理详情里虚构为成功。

### A08/A09 公共方案

摘要 `{schemeId,schemeCode,schemeName,description,kind,updateTime}`；详情补充 `{channelCode,definition,createTime,references}`。references 列出直接引用本方案的方案以及直接/间接引用 API，给出 ID、代码、支付方式和经由的组合代码；按本期 Channel 容量限制完整返回，不漏掉经组合产生的影响。

## 4. 写入请求

| 操作 | Body 必填字段 | 可选字段 / 限制 |
|---|---|---|
| A05 创建 API | paymentMethodCode、apiCode、apiName、definition | description 可缺失/null |
| A06 更新 API | expectedUpdateTime、apiName、definition | description 可缺失/null；不接收身份字段 |
| A07 删除 API | expectedUpdateTime | 无其他字段 |
| A10 创建方案 | schemeCode、schemeName、definition | description 可缺失/null；kind 位于 definition |
| A11 更新方案 | expectedUpdateTime、schemeName、definition | description 可缺失/null；schemeCode 不可改 |
| A12 删除方案 | expectedUpdateTime | 无其他字段 |

创建不存在的资源返回201；同自然代码已存在返回409，不自动覆盖。PUT 是完整替换可变字段，未提交的旧步骤不会被服务器暗中保留；description 省略等价于清空，definition 则不能省略/null。旧更新时间不符返回409并保留当前数据。

示例更新 Body 的外层如下，definition 内容以[字段合同](./definition-contract.md)为准：

```text
expectedUpdateTime: 2026-09-14T10:20:30.123456Z
apiName: NI Capture 退款
description: 从退款上下文取金额，通过注册系统函数取得原交易协议字段
definition: { inputDefinition, requestSteps, transportDefinition,
              responseSteps, resultRules, outputDefinition }
```

不要求 Idempotency-Key，不承诺稳定历史回执或自动重放。写入响应丢失时，客户端必须通过 A03/A04 或 A08/A09 确认当前内容；不能因超时再次自动 POST 或 PUT。删除超时后读取404只说明目标已不存在，不能把它审计为当前操作者一定完成了删除。管理变更只记安全元数据与关联 Request ID，不复制完整配置正文或 Secret，不新增 BCR/审计版本表。

## 5. A13 检查候选配置

Body 为 `{operation,targetId?,candidate}`：operation 是 CREATE_API、UPDATE_API、CREATE_SCHEME、UPDATE_SCHEME；UPDATE 必须有 targetId，CREATE 禁止 targetId；candidate 与对应写入 Body 相同。所有对象严格拒绝未知字段。

结构可解析后，返回：

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "ERROR",
      "code": "STEP_FORWARD_REFERENCE",
      "path": "definition.requestSteps[0].inputBindings.orderRef",
      "message": "该步骤引用了尚未产生的函数返回值",
      "affectedApiIds": []
    }
  ],
  "affectedDefinitions": [],
  "missingCapabilities": [],
  "missingFunctions": []
}
```

issues 最多100项，超过另返回 `truncated=true`，valid 仍为false；不以部分错误列表代表只有这些错误。severity 为 ERROR/WARNING，路径/说明/数组始终存在，affectedApiIds 只列当前授权 Channel 内的配置 ID。实现缺失可以形成 WARNING，不影响结构 valid，但单列缺失清单；无效引用、类型、预算与敏感绑定是 ERROR。

检查用一致性读取分析完整候选，不保存候选、不获取真实凭据、不运行补数方法或发送任何渠道请求。表达式在受限编译器可用时只编译及检查允许的变量、字段、纯函数、依赖和可推导类型，不执行求值；编译器未装配时报告 EXPRESSION_COMPILE_UNAVAILABLE 错误，valid=false，不能声称编译或类型检查通过。编译检查器与运行实现的可用性分开；只有运行实现缺失而静态检查完整通过时，才按缺能力警告允许保存。无法证明的动态类型形成运行时校验提示，不冒充真实结果已验证。编辑器的“检查表达式”复用 A13，issues.path 定位到当前 binding.expression；其他未完成字段仍保留自己的错误。拟定诊断包括 EXPRESSION_SYNTAX、EXPRESSION_REFERENCE、EXPRESSION_FUNCTION_NOT_ALLOWED、EXPRESSION_LIMIT 和 EXPRESSION_TYPE；引擎或纯函数缺实现沿用 missingCapabilities。报告反映检查时点，真正保存必须在写事务中重做当前引用校验，不能拿之前的 valid=true 跳过。

## 6. 错误与传输界限

全部错误使用[全系统 Problem Profile](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)：application/problem+json，type/title/detail/code/requestId/timestamp；不添加 status/instance，Body 字段错误只能使用 errors[].field/message。A13 的检查报告是成功查询响应，不是另一种异常格式。

| HTTP | code | 客户端处理 |
|---|---|---|
| 400 | VALIDATION_ERROR | 修正结构/字段；适用时 errors 定位 Body 字段 |
| 401 / 403 | 沿用当前身份/授权错误码 | 恢复会话或刷新权限，保留可安全保留的草稿 |
| 404 | CHANNEL_CONFIG_NOT_FOUND | 目标不存在或不在指定 Channel 内 |
| 409 | CHANNEL_CONFIG_EXISTS | 创建代码冲突，定位已有定义，不重试创建 |
| 409 | CHANNEL_CONFIG_CHANGED | 旧更新时间不符；保留草稿，读取当前值，由用户处理冲突 |
| 409 | CHANNEL_SCHEME_IN_USE | 仍有引用，先查看 A09 并调整使用方 |
| 409 | CHANNEL_CONFIG_INCOMPATIBLE | 候选破坏当前依赖；调用 A13 获取当前可展示错误 |
| 409 | CHANNEL_CONFIG_LIMIT / CHANNEL_CONFIG_CLOSED | 超出容量或 Channel 状态不允许编辑 |
| 413 | Gateway 沿用 GATEWAY_PAYLOAD_TOO_LARGE；Owner/BFF 拟用 CHANNEL_CONFIG_TOO_LARGE | 缩小请求，不能静默截断定义；不把拟新增码写成既有公共码 |
| 503 | 沿用 SERVICE_UNAVAILABLE | 授权、数据库或必要目录不可用；不使用旧缓存假定保存成功 |
| 502 | BFF 当前上游协议错误类别 | 不透传损坏 Owner Body，不丢失用户草稿 |

所有写与检查请求上限64 KiB；删除命令上限4 KiB；GET 无 Body。Gateway 接收 Body 5000ms、BFF 响应等待10000ms，沿用现有 Operations 管理边界。Payment/BFF 请求日志仅元数据；不得因为本接口配置 JSON 就打开请求正文日志。响应均禁止浏览器缓存；不引入 ETag/304 条件缓存。

## 7. Gateway 注册

13 条公共 Route 均指向既有 operations-console-bff Target，Header/Query 严格白名单。拟定注册数据见[数据说明](./db/README.md)：2 个权限、1 个菜单/1 个管理按钮、13 Route、40 Header、10 Query、78 Guard，共145行。角色授权0行，Route/资源初始 DISABLED，执行与启用另需环境授权；这不等于给 API 定义增加发布流程。
