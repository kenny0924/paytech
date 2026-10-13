# 通用外部 API 配置与执行修订

> **2026-10-13 迁移后继：** 本目录已由 `requirements/20260914/payment-渠道API逻辑定义/` 迁至 Platform 命名目录。本文为历史资料，保留原日期与适用范围；现行合同见[需求](./requirement.md)、[六表数据设计](./data-design.md)和[流程设计](./flow-design.md)，本文被取代的范围见[技术说明第 5 节](./technique.md#5-历史资料与取代关系)。

> **2026-09-21 Owner 与调用方式后继：** [Platform HTTP 执行修订](./platform-http-execution-amendment.md)确认配置管理和执行均归 Platform，Payment 等系统通过内部 HTTP 调用；取代原可嵌入宿主运行方式。下文输入、映射、无执行持久化/重试及结果语义继续适用，具体 Wire 与运行材料交接仍待细化。

2026-09-15 公共脚本修订：[公共脚本与 HTTP 对象](./public-script-http-context-design.md)将公共方案与独立脚本合并，明确 REQUEST/RESPONSE 工作对象权限、源码与参数，以及 API 的有序引用。字段表达式仍只读；脚本允许修改当次 HTTP 工作副本。

2026-09-15 补充：[公共模型与响应场景设计](./model-library-and-response-scenes.md)明确可维护的层级输入/输出模型、模型引用，以及同一 executeStatus 下的多个输出场景。它细化本页第 5、6 节；无执行持久化、无重试、返回仅 executeStatus 与 outputs 的边界不变。

本稿记录用户对真实调用链提出的八项修订：移除支付方式和固定支付身份参数；业务幂等留在调用方；执行过程不记录业务或通信流水、不负责重试；返回仅保留 executeStatus 与 outputs；不同渠道的同类业务输出遵循调用方的共同合同。确认范围见[交付记录第 19 节](./delivery.md#19-通用执行边界修订2026-09-14)。本轮是设计修订，未实现执行器、修改正式模型或执行 SQL。

本文是通用调用合同的现行入口，明确取代前版 paymentMethodCode、固定 InvocationIdentity、必填 channelIdempotencyKey、对外 prepare/execute、sendState/failureStage/diagnosticCode/protocolEvidence/resultCode 以及通用层承担 Payment 发送门禁的设计。字段映射、显式函数步骤、简单脚本、代码密码能力和配置整体读取的原则保留。旧 NI、管理 API、数据库和 Pencil 工件是通用化前的材料，其测试不证明本修订可运行。

## 1. 分层和命名

通用层只知道接入分组、API 定义、输入数据、处理步骤及输出合同。建议技术分组采用 Integration，以 integrationCode 标识 NI、GOOGLE_MAPS、MAIL 等接入方，避免与 Payment Channel 和 Platform 访问 Channel 混用；不会把地图或邮件写入 t_payment_channel。

```text
Integration（外部接入分组）
  ├─ API 定义
  └─ 公共脚本（API 显式引用）
```

- 移除通用配置层的支付方式节点，API 以 `(integrationCode, apiCode)` 定位，不要求 paymentMethodCode。
- Payment 的支付方式、冻结 Channel/Account/MID、资金状态和标准业务接口选择留在 Payment 适配层。同一配置 API 可以服务多种支付方式；协议不同则定义不同 apiCode，由 Payment 选择。
- 配置管理与执行均由 Platform Support 的 Integration 能力承担，业务系统通过内部 HTTP 调用；不新增独立部署服务、第七核心系统或跨库读取。配置持久化归 Platform，具体模块、Wire 与物理字段继续细化，旧 Payment 外键/唯一键草稿不作为有效通用模型。
- API 与公共脚本分别管理；公共方案并入脚本，数据模型及响应合同由模型库维护。Integration 分组是配置身份维度，尚不据此宣布完整物理表总数。
- HTTP 接口可以采用同一执行模型。邮件 HTTP API 可复用 HTTP 适配；SMTP 需独立传输适配，不能声称现有 HTTP 设计已经支持 SMTP。后续 SMTP 一次邮件操作内的协议命令不等同多次业务重试。

本修订只泛化配置执行边界，不取消 Payment 已批准的幂等、资金预占、原操作恢复、Card 保护和状态更新要求。它们由业务适配与 Owner 完成。

## 2. 调用输入

对调用方提供一个逻辑能力：

```text
execute(integrationCode, apiCode, context) -> ApiExecuteResult
```

JSON 示意如下；所有 ID 和接入代码都是说明值，不作为业务种子或真实服务请求：

```json
{
  "integrationCode": "DEMO_PAY",
  "apiCode": "REFUND",
  "context": {
    "refundContext": {
      "originalTransactionRefId": "10001",
      "refundAmount": "10.00",
      "currency": "AED"
    }
  }
}
```

| 字段 | 是否需要 | 解释 |
|---|---|---|
| integrationCode | 需要 | 定位外部接入分组 |
| apiCode | 需要 | 定位该分组中的 API |
| context | 需要对象；无输入时可为 {} | 按该 API 的输入合同校验，不是所有 API 共用固定支付字段 |

输入合同由 API 定义决定，通用层不固定 PAYMENT/REFUND 上下文枚举。context 中可以包含 refundContext、paymentContext 或地图查询参数，也可以直接使用扁平字段；必须符合该 API 已声明的类型结构。路径从 context 根读取，例如 refundContext.currency 对应 `["refundContext", "currency"]`。这是对旧稿隐含单个 contextType 根的显式修订，不要求旧样例原地无条件兼容。

调用方不提交配置正文、任意目标 URL、实现类或脚本源码。输入是数据，不允许通过数据覆盖已选 API 的处理逻辑。

### 身份与连接材料

删除固定 InvocationIdentity，不再让地图或邮件接口强制传 Client、Action、MID、OE 等支付字段。某个 API 或其注册函数确实需要的业务 ID，由对应输入合同声明；不是把旧身份对象整体换名塞进 context。

移除固定身份字段不等于取消授权。业务入口完成业务归属校验，需权限的代码函数继续通过所属 Owner 的受控适配执行；客户端可编辑 context 不能自证权限。

目标地址、API Key、密码和签名密钥由 Platform 执行环境装配的受控连接/凭据解析器提供；Payment 账号材料的跨系统交接及获准读取方式按后继修订另行细化。单连接可以直接由配置绑定；多账户时，仅为确实需要选择的 API 声明一个获准连接引用，由宿主验证其权限。支付适配仍以原 Attempt 冻结账户确定连接，通用层不解释 MID 或 OE，也不接受任意连接选择绕过该规则。真实密钥不放在普通 context 中。

## 3. 幂等与执行责任

channelIdempotencyKey 从通用必填入参移除。通用执行器不创建业务幂等记录、不判断业务重复、不缓存业务响应，也不协调同一 Action 的并发恢复。调用两次 execute 就可能发生两次外部操作，业务方必须在调用前完成其需要的幂等与并发控制。

如果外部 API 自身要求 Idempotency-Key 或请求流水号，由业务方提供稳定值，作为该 API 普通 Header/Body 映射的输入；执行器只按配置发送，不生成或解释其业务含义。业务幂等不能自动代替第三方协议要求的请求参数。

执行器不自动重试、重定向或切换其他渠道 API；底层客户端及代码函数也不得暗中重试目标操作。数据函数可以完成注册的只读数据查询，不能夹带另一笔外部业务操作。HTTP 一次 execute 最多发送一次目标请求。

旧对外 prepare/execute 与业务发送门禁交错的流程退出通用合同。加载、校验和组装是 execute 内部阶段，不要求通用调用方持有 PreparedCall。Payment 业务受理与发送协调在调用 execute 前由业务层完成，网络调用仍在数据库事务之外。简化返回不允许业务层把 ERROR 自动当作可重新发款的证据。

## 4. 执行过程与数据库

```text
读取 API、公共脚本及引用模型的一致定义
  → 校验 context、输入输出合同和实现可用性
  → 解析获准的连接与运行材料
  → 请求取值、必要查询及字段映射，组装 http.requestBody
  → 有序执行 REQUEST 脚本，调用必要的编码/签名/加密系统函数
  → 校验最终报文及保护约束
  → 发起一次外部操作
  → 接收原始响应，按协议形成响应工作对象
  → 有序执行 RESPONSE 脚本，按协议验签/解密/解析
  → 校验处理后响应，匹配响应场景
  → 输出映射与类型校验
  → 返回 executeStatus + outputs
```

| 数据 | 是否由执行器落库 | 说明 |
|---|---|---|
| API、公共脚本及模型配置 | 配置管理时保存；执行时只读 | Operations 保存当前定义，执行器读取 |
| 调用输入、函数返回、步骤结果、请求/响应正文 | 否 | 只在当次内存中使用，结束后释放 |
| 执行流水、执行状态、幂等记录、重试任务 | 否 | 通用层不建表、不写已有业务表 |
| Payment 交易、退款、交互等 Owner 事实 | 不由通用执行器写入 | 是否需要记录及如何记录由原业务 Owner 合同决定 |

配置如果存在数据库中，执行器可以在短只读事务中加载一致内容，再结束事务执行函数和网络操作；“无执行记录”不表示连配置都不能读取。数据函数可经 Owner 接口读取所需数据，不能直接跨库访问或顺便写入执行记录。

保存即生效仍指保存后新调用使用新定义，当次调用使用开始时已完整加载的 API、公共脚本及引用模型，不在处理中混合新旧内容。配置管理自身的并发保护仍需要；它不属于业务执行幂等。

本设计不新增专用执行日志/追踪存储。既有应用诊断如需保留，只能使用安全的定位信息，不将完整 context、凭据、原报文持久化为排错材料。

## 5. 统一返回与状态映射

ApiExecuteResult 只包含两个字段：

```json
{
  "executeStatus": "SUCCESS",
  "outputs": {
    "transactionId": "txn_123",
    "paymentStatus": "PROCESSING"
  }
}
```

| executeStatus | 语义 | 如何产生 |
|---|---|---|
| SUCCESS | 满足本 API 配置的成功条件，并且所需输出映射/校验完成 | 有效响应匹配成功规则 |
| FAIL | 外部 API 返回了可识别、符合本 API 失败定义的结果 | 有效响应匹配失败规则 |
| ERROR | 无法可靠完成本次调用或解释结果 | 配置/输入/函数/表达式/传输/保护/映射失败，响应显式匹配 ERROR，或没有规则匹配 |

executeStatus 是 API 执行合同的结果，不自动等于支付资金状态。若业务规定“已受理”即该 API 调用成功，可返回 SUCCESS，并在 outputs.paymentStatus 中表示 PROCESSING；是否已扣款仍由业务字段及可靠关联决定。

移除 sendState、failureStage、diagnosticCode、protocolEvidence、resultCode；不另加等价字段强迫上游消费。若某业务希望返回外部错误码，可以自行在 FAIL 输出合同中声明 errorCode 等业务字段，不作为所有 API 的固定外壳。

### 状态规则

建议用 statusMapping 替代原 resultRules：

```text
responseContractRef = 所引用的公共响应合同
statusMapping.rules[] = { condition, sceneCode }
statusMapping.defaultScene = ERROR
```

规则按配置顺序匹配，首个匹配生效。条件可以读取获准的 HTTP 状态/响应头、已处理响应或前序判断结果；既有结构化 Condition 和简单表达式投影原则可复用。不存在只要 HTTP 200 就统一成功的引擎默认规则。

强制输入、验签、解密、字段类型与必要关联校验必须先满足。配置中的成功规则不能捕获这些异常并改为 SUCCESS；即使初步匹配 SUCCESS，必填输出缺失或输出类型不符，最终仍返回 ERROR。

输出从原 resultCode 分支改为公共响应合同的 sceneCode 分支，场景约定 executeStatus 与输出模型；同一 SUCCESS 可以有“已完成”和“需要交互”等不同结构。API 按条件选场景并映射 outputs，不能在引用处改写场景的执行状态或模型。ERROR 固定返回空对象，避免返回半成品或未通过保护的数据。完整规则见[模型与场景设计第 5、6 节](./model-library-and-response-scenes.md#5-响应合同与多个场景)。

```json
{"executeStatus":"ERROR","outputs":{}}
```

**ERROR 不证明外部没有执行。** 例如发送后等待响应超时，第三方可能已经完成扣款或发送邮件。执行器不重试，也不返回发送阶段；调用方必须按其业务协议处理不确定性，不能仅凭 ERROR 释放资金预占或重新发起外部操作。

这是内部执行方法的结果合同，不改写现有公网 HTTP 鉴权和 RFC 9457 错误规范。本轮不新增任意调用公网入口。

## 6. 自定义 outputs 与跨渠道一致性

outputs 可以由每个 API 定义字段名、类型、必填/可空和取值映射。通用引擎不内置 payment/refund 输出类型，但必须根据定义做类型和完整性检查，不能无 Schema 返回任意 Map。

不同业务可以有不同合同：

| 调用用途 | 示例共同字段 | 示例可选字段 |
|---|---|---|
| Payment | transactionId、paymentStatus、amount、currency | authorizationCode |
| Refund | refundId、refundStatus、amount、currency | 原交易相关的已声明引用 |
| 地图查询 | 按调用方选择的地址/坐标结果结构 | API 合同允许的补充字段 |
| 邮件发送 | messageId、accepted 等服务结果 | 明确声明的投递信息 |

这些是合同设计例子，不是对 NI、Google 或邮件服务字段的事实声明，也不是本轮已经批准的统一 Payment Wire。

“同业务跨渠道一致”由业务 Owner 提供共同输出合同，并在配置保存/接入校验时约束相关 API：字段名称、类型、单位、业务含义和必填规则一致，只有外部来源路径不同。通用引擎消费该合同，不自行推断两个 API 都属于 payment。

小范围渠道差异优先声明为共同合同中的可选字段；必须保留独有数据时，使用显式定义结构的附加对象，不能增加无约束扩展袋或改变公共字段类型。没有字段的渠道按合同省略或返回 null，不填空字符串伪造存在。

公共对象模型与响应合同在 Operations“数据模型”页维护，API 引用相应模型/合同；管理 Owner 已确定为 Platform；HTTP Wire 和完整物理字段继续细化，不能由逻辑对象数量推断正式模型统计。没有给两个 API 绑定同一合同，就不能仅凭名称相似保证跨渠道一致。

## 7. 示例与后续落点

[通用调用阅读样例](./assets/generic-execution.example.json)保留第 19 轮按状态分组的四种合成结果，不覆盖第 20 轮新增的模型引用及场景分支；当前层级对象与场景结构见[模型与场景设计](./model-library-and-response-scenes.md)。旧 JSON 是片段，不是完整可导入 API，也没有运行脚本、网络请求或密码函数。

下一步详细设计只需沿本次边界补齐：通用配置管理 Owner/工程位置、移除 payment_method_code 后的身份/唯一性、输入和共享输出合同载体、连接绑定，以及管理 Wire/SQL/Pencil 同步。旧 Payment 专属草稿不能直接实施后再声称通用化已完成。本轮不扩大为真实 Google Maps/SMTP 接入或 Payment 标准操作实现。
