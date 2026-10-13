# 协议处理能力与完整定义

> **2026-10-13 迁移后继：** 本目录已由 `requirements/20260914/payment-渠道API逻辑定义/` 迁至 Platform 命名目录。本文为历史资料，保留原日期与适用范围；现行合同见[需求](./requirement.md)、[六表数据设计](./data-design.md)和[流程设计](./flow-design.md)，本文被取代的范围见[技术说明第 5 节](./technique.md#5-历史资料与取代关系)。

**公共脚本修订：** 当前维护形式见[公共脚本与 HTTP 对象](./public-script-http-context-design.md)，方案与独立脚本合并，复杂协议处理由脚本调用代码注册函数。本文原始报文、精确字节、受控材料及类型/保护原则继续适用；旧 Scheme JSON 与静态检查不覆盖可变 HTTP 工作对象或新函数签名。

**适用边界：** [通用执行修订](./generic-execution-amendment.md)取代 Payment 专属输入、分组及结果合同。本文的协议类型和受控处理原则保留，NI JSON/目录只证明前版静态子集；依赖旧 Channel 目录、身份或结果形状的参数需同步后才可作为新版完整定义。

本文拥有代码能力、具体参数和协议处理顺序。前轮完成了无独立脚本 NI 定义的静态核对；其两表、13 项管理操作和 145 行注册 SQL 只覆盖原有子集。当前[模型整理](./model-and-mapping-design.md)新增独立脚本及三表方向，脚本详细合同和物理/管理增量待补齐，不沿用旧统计作为当前完整范围。没有运行实现或 SQL 执行声明。

本文拥有具体处理能力及其参数合同；[definition-contract.md](./definition-contract.md)拥有统一类型和 Step/Binding/Transport 结构，[registry-contract.md](./registry-contract.md)拥有上下文与函数成员，[db/README.md](./db/README.md)拥有数据库落点。最新完整内容见 [NI API definition](./assets/ni-refund-api-definition.json)、[Bearer scheme definition](./assets/ni-payment-bearer-scheme-definition.json)及[协议能力目录](./assets/protocol-capabilities.example.json)。前两份 JSON 可分别作为两表的 definition_json 内容，不是带支付方式、名称等元数据的完整管理请求。

## 1. 目录与具体合同

A02 的 capabilities 中每项为 `{capabilityCode, displayName, usage, phases, allowedChannelCodes, contract, implementationAvailable}`。usage 为 STEP、CONTEXT 或 EXPRESSION；只有 STEP 可以被 Step 引用。phases 为 REQUEST/RESPONSE 的非空子集；allowedChannelCodes 的 null/非空数组语义沿用函数目录。上下文/纯函数的 capabilityCode 对应同一个实现注册事实，不维护相互矛盾的可用性。

contract 是严格的二选一结构：

- `FIXED`：`{kind, inputDefinition, outputDefinition, parameterDefinition}`，三个 Definition 都是既有 FieldSet。
- `SPECIALIZED`：`{kind, specializerCode, parameterContractCode}`。specializerCode 和 parameterContractCode 都是开发者注册的有限代码，不是脚本或自由 Schema URI。选择函数、字段映射或协议 Profile 后，静态校验器必须解析出完整 inputDefinition、outputDefinition、parameterDefinition/参数语法树，之后按同一套类型规则检查，不能把 null/任意 Map 当作实际合同。

需要特化，是因为 CALL_SYSTEM_FUNCTION 的参数由 functionCode 决定，OBJECT_MAPPING 的类型由字段定义决定，JSON 编码器的输入由前序对象决定。它们不能假装成固定的无类型 object。FieldDefinition、字段映射列表和 ValueBinding 是配置语法节点；参数包含这些节点时，由下文具名参数合同解释，不将它们伪装成交易数据 FieldSet。A02 可展示注册代码对应的编辑器和元数据，Operations 不能新增 specializer。

### 首批 STEP 能力

| capabilityCode | 阶段 | 输入 | parameters | 输出 |
|---|---|---|---|---|
| CALL_SYSTEM_FUNCTION | REQUEST | 注册函数入参 | `{functionCode}` | `{value: 函数返回对象}` |
| OBJECT_MAPPING | REQUEST / RESPONSE | 映射声明的 inputName，各自绑定一个值 | `{fields: FieldMapping[]}` | `{value: 有限类型 OBJECT}` |
| ENCODE_JSON | REQUEST | value：前序有限 JSON 对象 | `{charset:"UTF-8"}` | `{bytes: BYTES}` |
| DECODE_JSON_RESPONSE | RESPONSE | status：INTEGER；contentType：ARRAY<STRING>；bytes：BYTES | `{profileCode}` | `{document: PROTECTED_HANDLE}` |
| VALIDATE_AND_PROJECT | RESPONSE | document：上述句柄；以及 Profile 声明的匹配输入 | `{profileCode}` | Profile 声明的有限投影；本例为 `{facts: OBJECT 或 null}` |
| BEARER_AUTHORIZATION | REQUEST | accessTokenHandle：冻结 Account 的受控句柄 | `{}` | `{headerValue: PROTECTED_HANDLE}`，只进入 Authorization |
| SIGN_BYTES / VERIFY_BYTES | Profile 声明阶段 | 精确字节与签名/验签材料；按 Profile 类型绑定 | `{profileCode}` | Profile 声明的签名结果或已验证字节 |
| ENCRYPT_BYTES / DECRYPT_BYTES | Profile 声明阶段 | 精确字节、受控材料和 Profile 要求的附加输入 | `{profileCode}` | Profile 声明的受保护报文或解密结果 |

上下文投影 CONTEXT_PAYMENT/CONTEXT_REFUND、AVIATOR_EXPRESSION 和 MONEY_MINOR_UNITS 也在能力目录中，usage 分别为 CONTEXT/EXPRESSION；不能配置成重复的转换步骤。以上目录有 10 个 STEP 能力、4 个非 STEP 能力，均为本需求拟注册描述符；示例 implementationAvailable=false。

## 2. 字段组装与 JSON

### OBJECT_MAPPING_PARAMETERS

`FieldMapping = {inputName, targetPath, targetDefinition}`；fields 为 1～128 项数组。inputName 使用 lowerCamelCase，唯一且与本步骤 inputBindings 的键集合完全一致；targetPath 是 1～16 段的对象路径，targetDefinition 是完整 FieldDefinition。不内嵌第二份 binding；取值只放在 inputBindings。

目标路径不能重复，不能同时配置 amount 和 amount.value，也不能把同一节点同时当对象与标量。公共前缀构成对象，不能用带数字的路径段隐式创建数组；数组必须作为已注册、有界且有元素类型的整体值绑定。普通对象映射不接受 BYTES、PROTECTED_HANDLE 或 PROTECTED 字段，不借此把 Token/原始响应塞进表达式。

每个输入在当步读取/求值一次，再按对应 targetDefinition 校验。允许缺失的直接路径可省略相应可选目标；required=true 时缺失即失败。null 与缺失分别处理，函数/表达式异常始终失败。若某个隐式父对象的全部子字段都省略，该父对象也省略；根对象保持存在。父对象的 required 由必需后代推导，nullable=false，dataClass 取子字段最高级别。不能声明比来源更低的数据分类或擅自缩窄可接受数值而不进行运行校验。

本例将 currency/value 分别绑定为 amount.currencyCode/amount.value，输出只有 `{"amount":{"currencyCode":"AED","value":1000}}` 对应的类型结构。第一方精确整数仍是类型化数值，NI 报文的数值 token 由 ENCODE_JSON 生成。

### JSON_ENCODE_PARAMETERS

仅允许 `{charset:"UTF-8"}`，不接受自由序列化器/Java 类名。输出最大 1 MiB、无 BOM、不带格式化空白；NUMBER 使用精确整数/十进制表示，不经 Double/Float/JavaScript Number。只有有限 JSON 类型可编码，禁止句柄、字节、循环结构及非有限数值。未出现的可选字段省略，允许的 null 输出 null；对象属性顺序不承担协议语义。

这不是 RFC 8785/JCS Canonicalizer，也不扩展 Foundation 的 JSON Object/Number Profile。签名如果要求特定排序、拼接或规范化，必须由其注册 Profile 明确产生待签字节；如果签的是请求体原始字节，必须直接签 ENCODE_JSON 的 bytes 并发送同一份 bytes，不在签名后重新序列化对象。

### 响应解码与投影

原响应字节只进入受控解码/保护能力。DECODE_JSON_RESPONSE 根据注册 Profile 的 HTTP 状态、媒体类型、字节/节点/数组限制解析，拒绝重复 JSON 属性、非法编码和数值溢出；其结果是当次调用的不可序列化句柄，普通 STEP 表达式无法打开。句柄绑定本次响应及 profileCode，只允许 VALIDATE_AND_PROJECT 或获准保护能力消费。

VALIDATE_AND_PROJECT 执行 Profile 必须具备的身份/保护检查，再输出完整有类型白名单对象；从这一步之后才允许普通 Aviator 与结果规则访问字段。未知业务响应字段不自动暴露到变量树，也不被打印或留档。无法通过校验时整个步骤失败，不输出部分成功事实。

响应 Profile 是代码注册的协议结构、字段分类与强制校验描述，不是额外数据库对象。添加已获准字段的普通请求/响应映射不需要创建新执行器；增加新的敏感字段可见范围或未登记解析算法须先补注册合同，不能由任意 JSON 路径绕开数据边界。

## 3. 认证与密码处理

### Bearer 方案

BEARER_AUTHORIZATION 的 accessTokenHandle 类型固定为 `PROTECTED_HANDLE(purpose=accessToken)`，只接受本次冻结 Account 的 RuntimeMaterialProvider 提供的材料。例子 RUNTIME 路径 `['credentials','accessToken']` 是受控目录中的用途绑定，不是普通上下文字段或实际凭据值。

headerValue 同样是不可序列化句柄，purpose=authorizationHeader，allowedSinks 仅 HTTP_HEADER:authorization。传输层在发送位置受控展开一次。方案 credentialPurpose 固定 accessToken；没有 prefix 自由参数，也不允许表达式拼接 Bearer Token。材料读取不隐式调用第二个渠道 API；需要 Token 而材料不可用时返回 NOT_SENT。

### SIGN / VERIFY / ENCRYPT / DECRYPT Profile

统一参数 `{profileCode}` 只选择已注册合同；不能填算法源码、Provider 类名、任意 KMS URL、密钥或证书。一个 Profile 必须同时声明：

1. 适用 Channel、阶段和具体能力；完整输入/输出/固定参数类型及凭据用途。
2. 确切算法与全部算法参数、输入字节构造、字符/二进制编码、输出表示和最终允许位置。不存在未说明的排序、Padding、随机量或默认编码。
3. 对 body/path/query/header 的覆盖范围及数据依赖，是否必须先加密后签名或先签名后封装，以及对应响应的验证/解密顺序。
4. nonce/IV/时间等材料的生成或读取规则、单次调用生命周期与预算；配置不直接提供私钥或以常量冒充运行随机量。
5. 保护结果的完整类型、allowedSinks 与可信性升级条件；如有带签名/密文的 JSON 信封，Profile 必须定义受控封装/拆解及完整字段结构，不能由普通 Aviator 操作句柄或秘密明文。
6. 成功向量、错误密钥/签名/字节篡改/截断/材料失效的拒绝向量，以及实现可用性。

签名/加密 Profile 必须把最终输出落到明确定义的 Header 句柄或已封装 BYTES 等合法传输位置；只有密码学中间结果而没有接收位置的 Profile 不可注册。验签不通过、解密失败或其完整性要求不满足时终止，不能返回 false 让可编辑结果规则放行为成功。

本 NI 接口没有当前已证明的报文签名/加密需求，因此四种能力的 Profile 集合为空，不捏造 NI 算法或默认给它加一套保护协议。能力族合同已定义；具体算法 Profile 按真实渠道文档和向量接入后才可引用。当前 Vault V1 的 Protect/Reveal 不被假定为渠道签名、Token 刷新或远程密码服务。

## 4. 顺序与公共方案复用

按当前修订，API 与组合方案也可引用独立 SCRIPT，形状由[定义合同](./definition-contract.md#独立脚本修订)拥有。脚本负责普通组装、转换和判断，密码学与受控材料操作仍由本协议合同的代码能力承担；不把 SCRIPT 当作新的密码算法执行器，也不允许脚本绕过 Profile 的保护约束。

数组顺序就是执行顺序，不按 capabilityCode 自动排序；前序输出仅能向后引用。完整配置先展开公共方案、验证依赖/类型/阶段/保护位置和总预算，再执行。保护步骤会产生编译器内部的字节来源与覆盖证据，不增加数据库 Hash/版本列，也不作为 Operations 可编辑字段。

| 场景 | 必须成立的顺序或依赖 |
|---|---|
| 普通 JSON + Bearer | 数据函数 → 字段组装 → JSON 编码；认证材料生成完成后发送一次 |
| 签请求体字节 | 组装 → 编码 → 签同一份 bytes → 发送被签名覆盖的 bytes |
| 先加密再签名 | 编码 → 加密 → 对 Profile 指定的密文/信封签名 → 发送最终受保护字节 |
| 验原响应字节 | 响应原始 bytes → 验签 → 解码/投影；失败不进入结果规则 |
| 加密信封 | 按 Profile 固定顺序验外层、解密、验内层或投影；没有统一推测顺序 |

例如签完 bodyA 却把 bodyB 绑定给 transport.body，或签完后改变被覆盖的 Header/Path，保存时能证明不一致就拒绝；动态材料在发送前复核，不依赖字段名称相似来认定相同。

API 引用 SCHEME 时不再写 capabilityCode。单能力方案先合并 `fixedParameters` 与 API 提供的参数，二者不得重名，再按具体能力参数合同校验；API 只能提供 parameterDefinition 中显式开放的项。方案输入输出必须与解析后的能力合同名称、类型、保护位置一致，不能通过方案降低字段分类。组合方案继续使用 SCHEME_INPUT/SCHEME_PARAMETER，深度限制沿用统一合同。

同一 Channel 可有 NI_PAYMENT_BEARER、其他已注册签名/加密方案；每个 API 显式选择。同一支付方式可选择不同方案，不同支付方式也可共享一个方案。修改方案时校验全部直接/间接引用 API，任一个不再合法则整个保存失败；删除被引用方案拒绝。没有隐式 Channel 默认或按支付方式自动覆盖。

## 5. NI 响应 Profile

`NI_REFUND_CAPTURE` 只用于 NI / REFUND，由 DECODE_JSON_RESPONSE 和 VALIDATE_AND_PROJECT 共用。注册结构在协议能力目录的 profiles 中；它只投影[已有 NI 合成样例](./assets/ni-refund-fixture.json)使用的协议字段，不提供查询或发起网络调用。

- HTTP 200：Content-Type 必须唯一且匹配 application/vnd.ni-payment.v2+json，允许合法参数；有 charset 时只接受 UTF-8。解析上限为 1 MiB、深度 16、总节点 4096、每笔退款数组最多 128 项；这些是项目接纳上限，不是 NI 产品承诺。未知响应字段不成为输出。
- 其他最终 HTTP 状态：返回 opaque document，标识未采用成功 Schema；不强行解析成功字段，随后 facts=null，结果走 UNRECOGNIZED。1xx 中间响应由 HTTP Client 处理，不能冒充最终响应。
- 匹配输入 `expectedTarget` 是同次 originalTarget.target；核对 outletId/orderReference/reference、币种及原 Capture 身份。当前函数合同的原目标身份校验已经在请求前完成，响应再次校验对应父级。
- 从 `_embedded['cnp:refund']` 中投影全部获准记录，不取第一条。self 链接仅用于提取 refundId：必须匹配受控 endpoint 对应的 scheme/host/port、原父级路径及唯一末段 ID，禁止 userinfo、query、fragment、额外路径和编码分隔符；不跟随链接，不把完整 URL 暴露给普通表达式。
- facts 字段为 paymentReference、paymentState、refundRecords；每条记录为 refundId、state、amount.currencyCode、amount.value。ID/状态非空，币种与原目标一致，金额是非负精确整数；有重复退款 ID、缺失字段、非预期结构或身份冲突时整个投影失败。状态字符串不被自动等同为本次退款状态。
- 200 的必需字段已在投影前校验；投影失败保留 RESPONSE_RECEIVED 执行事实并报告 RESPONSE 阶段错误，结果规则不执行。投影成功也只能返回 UNRESOLVED；当前资料不足以把其中某条记录唯一关联本次 Action。

## 6. 完整 NI 定义核对

完整定义有 **4 个请求步骤、2 个响应步骤**：originalTarget → requestBody → encodeBody → authorization → 一次 POST → decodeResponse → observedFacts → 结果/输出映射。原先示意性的 validateTarget 独立步骤已由注册函数的强制身份校验和响应 Profile 校验承担；不再留一条可被编辑器删除的重复安全开关。

NI API 与 Bearer 方案 JSON 都使用统一 Step/ValueBinding，不含说明字符串冒充执行参数。结果条件使用显式 `op` 区分 EQ/NE/EXISTS/ALL/ANY/NOT；没有额外表达式判定语言。HTTP 200 规则输出 UNRESOLVED，其余返回 UNRECOGNIZED；错误不是结果分支。

八个普通请求映射保持上轮含义：四个 Path 通过 result.xxx 复用 originalTarget，两个 Body 字段使用上下文与金额表达式，两个普通 Header 使用常量表达式；Authorization 为受控方案输出。Body 的 INTEGER 由 JSON 编码器产生数值 token，函数查询与取值表达式不负责网络请求。

定义没有合法支付方式元数据就不能创建真实 API，因此不把 `<APPROVED_PAYMENT_METHOD_CODE>` 或 CARD_TEST 塞进可保存文件。检查报告分别注明 definition 内容结构、设计目录依赖、管理请求元数据和运行能力是否已验证；缺获准支付方式、NI 导航提供方、材料/协议上限和本次退款关联合同继续显式保留。

本轮的完整配置检查是针对本例已登记能力和全部定义字段的静态交叉核对，没有执行 Aviator、密码算法或真实 NI 请求，也不是已实现的 A13 校验器。命令与结果见 [check-complete-definition.py](./assets/check-complete-definition.py)及[完整定义验证报告](./assets/complete-definition-verification.json)。
