# 配置内容合同

> **2026-10-13 迁移后继：** 本目录已由 `requirements/20260914/payment-渠道API逻辑定义/` 迁至 Platform 命名目录。本文为历史资料，保留原日期与适用范围；现行合同见[需求](./requirement.md)、[六表数据设计](./data-design.md)和[流程设计](./flow-design.md)，本文被取代的范围见[技术说明第 5 节](./technique.md#5-历史资料与取代关系)。

**公共脚本修订：** [公共脚本与 HTTP 对象](./public-script-http-context-design.md)取代 SCHEME 与独立 SCRIPT 分开管理、公共脚本只读/返回包装的旧设计。API 引用参数化公共脚本，脚本按阶段修改 http 工作对象；字段取值表达式保持只读。下文旧 Step Wire、参数预算和校验器尚未完整覆盖新执行环境，不可直接当作可导入的新配置。

**层级模型补充：** [模型与响应场景设计](./model-library-and-response-scenes.md)拥有可编辑对象模型、无循环 MODEL_REF、上下文名称绑定与 sceneCode 输出分支。本页的固定上下文目录及仅按状态分组输出不再覆盖当前模型库；通用新版完整 Wire/校验器仍待同步。类型、精度、缺失/null、受控协议数据和展开预算继续适用。

**合同修订：** 当前调用与状态边界见[通用执行修订](./generic-execution-amendment.md)。本文 paymentMethodCode、contextType 固定目录、resultRules/resultCode 分支及相关示例已被替代；应采用显式 context 根路径、statusMapping 与 executeStatus 输出分支。Step/Binding、类型、简单脚本与受控代码能力的原则保留；完整新版 Wire 和校验器待同步。

本文是[详细设计](./design.md)的配置字段附件，定义严格结构及校验语义。按最新取值反馈，以 Aviator 受限表达式作为本需求的设计方向；JSON/XML 库、实际依赖版本及引擎实现仍未落地。存储列虽然是 JSON，内容必须转换为明确的模型并完成语义校验；禁止任意 Map 透传、未知属性和无 Schema 的 extension。阶段 2 的 YAML 是解释样例，本合同才是拟实现的结构。

独立脚本的模型方向与映射说明见[整理稿](./model-and-mapping-design.md)。本轮在第 4 节补充 SCRIPT 步骤的结构方向；脚本管理 Wire、源码预算及编译/运行实现仍待补齐。已有 NI 校验器只覆盖 CAPABILITY/SCHEME 子集，不据此声称已验证 SCRIPT。

## 1. 基本约定

- 所有对象拒绝未知字段、重复 JSON 属性和类型不符。字符串不隐式转换成布尔/数值，缺失、null、空字符串和零分别处理。
- API、方案、能力和系统函数代码使用 `[A-Z][A-Z0-9_]{0,63}`；stepCode 和输出别名使用 `[a-z][A-Za-z0-9]{0,63}`。渠道和支付方式继续使用各自现行目录规则，不用这些模式覆盖外部既有代码。
- 本文 schemeCode 指 Channel 的协议处理方案，不是 Payment Method Catalog 中的卡组织 Scheme；两者不互相关联或替代。
- 名称 1～128 字符，说明可缺失/null，最多 512 字符。空名称拒绝；说明空字符串规范为 null。字段长度按 Unicode 字符计，报文大小按 UTF-8 字节计。
- 一份 definition 序列化后最多 49,152 字节；管理请求整体最多 65,536 字节。每个 Channel 最多 200 个 API、100 个公共方案；超过时返回明确容量错误，不截断保存。
- 单个请求链/响应链各最多 32 步，展开组合后合计最多 128 步；规则最多 64 条，条件深度最多 8 层/合计 256 节点；输入/输出字段各最多 128 个。以上是本方案拟定的产品限制，变更时一并评审校验、性能与页面行为。
- 第一方配置中的 64 位 ID、精确整数/小数采用有类型的十进制字符串；timeout、page、size 等有界控制参数使用 JSON 整数。第三方报文的具体 Number/String 由编码能力和目标协议决定。

## 2. API 定义体

API 的 Channel、支付方式、apiCode、name/description 属于管理元数据，definition 不再复制它们。

| 字段 | 类型 / 必填 | 含义与校验 |
|---|---|---|
| inputDefinition | InputDefinition / 是 | 所用上下文、基础必需字段；延后由查询补齐的值在步骤中声明，不冒充基础必填 |
| requestSteps | Step[] / 是 | 可为空；顺序执行，前序输出只读 |
| transportDefinition | HttpTransport / 是 | 本 API 唯一传输定义；非 HTTP 仅保留扩展位置，不接受未注册协议 |
| responseSteps | Step[] / 是 | 可为空；处理收到的状态、Header 与字节，保护完成前的数据不成为可信业务输出 |
| resultRules | ResultRules / 是 | 有序规则与未识别结果；只分类本接口事实 |
| outputDefinition | ResultOutput[] / 是 | 按结果分支列出输出字段及来源，不默认返回整个上下文或响应 |

`InputDefinition = { contextType, requiredPaths }`：contextType 来自只读上下文目录；requiredPaths 为目录中的字段路径数组，最多 128 项且不重复。每条路径必须由该上下文允许读取；运行材料由步骤中的受控 RUNTIME 绑定声明，不新增一个由调用者填充的自由上下文袋子。

UI 将 contextType 呈现为输入 Context，放在请求映射页，并展示该目录有哪些可读字段；选择 REFUND 只确定输入合同，不触发退款、不等同标准 API 动作。目录同时提供稳定的 contextName，例如 refundContext；它是该合同的可读变量名，不是 Java 类名。字段取值在请求参数内编辑，保存时仍映射到本文的 InputDefinition、ValueBinding 和 Step。详细交互以 [UI 设计](./ui.md) 为准。

## 3. 一个值如何绑定

界面只提供**上下文路径、表达式、系统函数**三种取值方式。下面的 source 是内部绑定合同，不逐项成为界面选项。上下文路径解析为 CONTEXT 等合法只读路径；表达式使用 EXPRESSION；系统函数由调用 Step 加返回值 EXPRESSION 表达。LITERAL 保留给既有固定参数及高级合同，新增字段的常量使用表达式字面量。

| source | 允许字段 | 例子 |
|---|---|---|
| CONTEXT | source、path | `{ "source":"CONTEXT", "path":["currency"] }` |
| RUNTIME | source、path | `{ "source":"RUNTIME", "path":["account","externalAccountId"] }`，只能取注册目录的允许字段 |
| STEP | source、stepCode、path | `{ "source":"STEP", "stepCode":"originalTarget", "path":["target","orderRef"] }` |
| RESPONSE | source、path | HTTP 状态 `["status"]`、Header `["headers","content-type"]`、原体 `["body"]`；只在响应链中可用 |
| LITERAL | source、valueType、value | `{ "source":"LITERAL", "valueType":"STRING", "value":"application/vnd.ni-payment.v2+json" }` |
| EXPRESSION | source、engine、expression、variables | engine 固定 AVIATOR；expression 返回一个值；variables 将命名变量绑定到允许的数据来源，详见第 3.2 节 |

普通 path 是 1～16 个非空字段名组成的数组，每段最长 128 字符。UI 的 refundContext.currency 解析为 CONTEXT 的 ["currency"]；带点等特殊字符的字段名由逐段编辑器处理，不把点误当字段内容。路径本身不执行函数、反射、通配和隐式数组遍历；它是 JSON 对象字段读取，不是表达式。仅在 EXPRESSION.variables 的 CONTEXT 绑定中允许 path=[]，表示当前目录的有限、非敏感上下文投影。系统函数先产生有类型的步骤输出，再由 result 表达式取值。按业务标识选择数组唯一目标仍使用已注册、限制数量的选择能力，不能用第一条记录冒充唯一匹配。

普通值类型为 STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY、BYTES；对象/数组必须有注册的子结构。BYTES 只能由步骤或响应产生，不在页面粘贴任意业务原始二进制。整数/小数字面值用十进制字符串表达，INTEGER 不接受小数点，DECIMAL 不接受指数，精度及范围由目标字段约束。

受控句柄另用 `PROTECTED_HANDLE`；它不能作为 LITERAL，不能绑定到普通输出、Query 参数、路径或任意 Header。凭据使用只允许受控能力接收，保护结果只能进入该能力合同声明的发送位置。运行句柄不进入持久化 definition；配置只保存凭据用途名与允许的属性路径。

输入节点不能向前引用。输出别名属于产生它的 stepCode，不允许覆写上下文或别的步骤。元数据目录负责给来源提供中文名称、类型、可空性和数据分类，前端不靠遍历真实业务对象猜可选字段。

路径读取不加工值。需要拼接、运算、单位处理等行为时，切换表达式并自动带入原路径；不添加第二个“转换渠道格式”选择。JSON 序列化、字符编码和签名/加密仍属于报文与保护步骤，不由普通字段表达式替代。

### 3.1 类型合同的固定形状

固定能力和方案的 inputDefinition、outputDefinition、parameterDefinition 使用 `FieldSet`：字段名到 `FieldDefinition` 的映射，无字段时为空对象。函数选择、对象映射、Profile 等需要特化的能力先按[具体参数合同](./capability-contract.md#1-目录与具体合同)解析配置语法，再得到完整输入/输出类型；参数中的 FieldDefinition/ValueBinding 节点不冒充业务数据字段。API 的 InputDefinition 仍使用第 2 节的上下文选择结构，不混用这两种类型。

每个 FieldDefinition 必填 `type / required / nullable / displayName / dataClass`；required 控制字段是否必须出现，nullable 控制出现时是否允许 null，二者不能互相代替。dataClass 为 PUBLIC、INTERNAL 或 PROTECTED，由注册能力约束，配置只能收紧，不能降低保护级别。可选 unit 来自注册目录，不接受自由单位或暗中转换。

| type | 对应约束字段 | 固定语义 |
|---|---|---|
| STRING | maxLength 必填，minLength 可选且默认 0 | 按 Unicode 字符计；不允许配置任意正则 |
| BOOLEAN | 无额外字段 | JSON 布尔值 |
| INTEGER | minValue、maxValue 必填 | 两端均为精确十进制整数字符串；闭区间 |
| DECIMAL | precision、scale 必填；minValue/maxValue 可选 | precision 1～38，scale 0～precision；值超出精度则拒绝，不隐式四舍五入 |
| OBJECT | fields 必填，类型为 FieldSet | 拒绝未知成员；不支持递归类型引用 |
| ARRAY | items、maxItems 必填；minItems 默认 0 | items 为元素类型定义，使用同表规则但没有字段级 required/displayName；空数组与 null 分开 |
| BYTES | maxBytes 必填 | 只存在于受控运行数据，不存原始材料 |
| PROTECTED_HANDLE | purpose、allowedSinks 必填 | 来自受控目录，配置不扩张用途或接收位置 |

未列在当前 type 分支的约束字段一律拒绝。maxLength、maxItems、maxBytes 必须是正有界整数，并受该能力及本 API 总预算约束；类型展开深度最多 8，所有嵌套字段/元素节点合计最多 128。注册目录给出完整约束，方案只允许收窄输入可接受范围；输出不能宣称底层能力未保证的必填性、范围或可信性。没有通用 defaultValue：缺失补值要通过显式映射或查询步骤表达。

### 3.2 Aviator 取值与处理

EXPRESSION 的固定结构为 `{source:"EXPRESSION", engine:"AVIATOR", expression, variables}`。expression 为非空文本，最多 2048 UTF-8 字节；variables 最多 16 个 lowerCamelCase 变量，值仅为 CONTEXT、普通 RUNTIME、STEP 或允许阶段的 RESPONSE 绑定，不递归嵌套 EXPRESSION，也不直接嵌套函数调用。编辑器根据目录和已引用路径生成变量绑定，用户不手工维护另一份参数映射；后端重新校验变量、字段、依赖与阶段，拒绝未知或未使用变量。

```json
{
  "source": "EXPRESSION",
  "engine": "AVIATOR",
  "expression": "money.minorUnits(refundContext.refundAmount, refundContext.currency)",
  "variables": {"refundContext": {"source": "CONTEXT", "path": []}}
}
```

系统函数返回值例子：expression 为 `result.orderRef`，variables 为 `{"result":{"source":"STEP","stepCode":"originalTarget","path":["target"]}}`。`result` 只在当前字段的结果表达式作用域内指向选定调用的完整返回值；完整使用时表达式为 `result`，不以空表达式猜默认行为。允许另引用已获准的上下文，仍由 variables 声明依赖。函数返回字段工具只插入路径；表达式同时可以加工该值，例如 `'NI-' + result.orderRef`（仅说明语法，不应修改 NI 要求的原 orderRef）。

普通表达式可读取目录允许的上下文、前序普通输出或已解析的响应投影，不能读取未来结果。常量直接使用 `'AED'`、`true` 等 Aviator 字面量；常量表达式的 variables 为空。上下文仍只读，表达式结果只写到当前目标，不原地修改金额、币种或业务身份。

运行剖面属于本需求拟定合同，不能只打开引擎默认配置：

- 只允许单个表达式、已声明字段访问、算术/比较/布尔/条件运算及白名单纯函数；禁止赋值、循环、lambda、函数定义、动态调用、模块、Java 方法/类访问、反射、eval、文件/网络/数据库访问。系统数据函数不注册进表达式环境，业务查询只能由显式 CALL_SYSTEM_FUNCTION 步骤执行。
- 环境只含有限 JSON 数据投影，不传入业务 Bean、服务实例、BYTES 或 PROTECTED_HANDLE；不允许含点的顶层变量键覆盖属性路径解析。解析、编译、函数查找及执行使用同一剖面和白名单；禁用未登记的引擎内置函数，不把 sandbox 开关当完整边界。
- 拟定单表达式执行上限 50ms、一次 API 的全部表达式合计 200ms，并受剩余 total deadline 限制；单份展开定义最多 128 处表达式。编译输入及 AST 节点有界（每式最多 256 节点、深度 16），数组/字符串/数值中间结果仍受类型及容量上限约束。超限失败，不进行隐式重试。
- INTEGER/DECIMAL 在执行环境分别使用精确整数/十进制表示；第一方 Wire 仍按原合同传字符串。拒绝 Double/Float 金额运算、静默截断、溢出及隐式舍入。十进制运算采用明确的精度和禁止舍入策略，非法精度在产生中间结果时失败；目标 INTEGER 必须是整数结果，不能通过最后一次序列化截断小数。
- 表达式引用的输入按解析出的实际字段依赖在求值前检查；绑定上下文根不会把所有未使用的可选字段变成必填。未声明字段是配置错误；实际必需输入缺失是运行错误，不能吞掉异常后继续发送。合同允许的 null 可在表达式中显式分支处理；结果 null、空串和零不等同缺失。最终仍检查目标 required、nullable、类型、长度、精度、范围和数据分类。可选直接路径缺失可按映射合同省略，表达式异常不得被当作字段省略。
- 静态检查只编译、核对语法/允许能力/引用和可推导类型。动态结果不能全部由静态分析证明，必须标识“运行时校验”；不为检查而执行表达式或系统函数。运行失败诊断包含步骤、目标字段及错误代码，不记录实际输入、结果或完整表达式正文。

`money.minorUnits(amount, currency)` 是本需求拟提供给 Aviator 的**项目纯函数**，不是 Aviator 内置，也不是已实现能力。其输入为 DECIMAL 金额和 STRING 币种，输出精确 INTEGER；币种精度取本次加载的系统只读币种目录，不访问外部服务。未知币种、无法无损转整数的多余精度、越界一律失败、不舍入；尾随零不构成精度损失。按现有例子 10.00 AED 返回 1000；不得将所有币种写死为乘 100。这类函数只辅助已有值处理，与读取业务数据的系统函数分别登记。首批参数顺序、DECIMAL(24,8) 输入边界、整数输出边界及币种精度快照规则集中见[最小注册目录](./registry-contract.md#4-aviator-纯函数目录)。

目录给出 `expressionProfile`（engine、可用变量规则、纯函数及其类型/限制/implementationAvailable）。新增纯函数实现可用性沿用能力清单，纯函数描述符包含 capabilityCode，缺实现进入 missingCapabilities；系统数据函数继续进入 missingFunctions。引擎/纯函数未实现时可保存结构有效的定义，prepare 返回 NOT_SENT。

官方依据：Aviator 的 [DecimalFunction](https://github.com/killme2008/aviatorscript/blob/master/src/main/java/com/googlecode/aviator/runtime/function/system/DecimalFunction.java) 支持 decimal 转换，[高精度示例](https://github.com/killme2008/aviatorscript/blob/master/examples/bigdecimal.av)使用 M 数字后缀；[5.4.4 发布说明](https://github.com/killme2008/aviatorscript/releases/tag/aviator-5.4.4)记录点路径能力及 io sandbox 修复。因此本设计的点路径语法及隔离要求必须在最终选用版本复验；这不是已引入该依赖的声明。

## 4. 步骤与方案

Step 公共字段：`stepCode`、`kind`、`inputBindings`、`parameters`、`outputBindings`。stepCode 在一份 API 展开结果内唯一；inputBindings 是能力输入名到 ValueBinding 的映射；parameters 是按能力参数合同校验的固定配置；outputBindings 是输出别名到能力输出字段路径的映射。别名遵守代码标识规则的 lowerCamelCase 对应形式，不能含点。

| kind | 额外必填字段 | 约束 |
|---|---|---|
| CAPABILITY | capabilityCode | 使用注册能力；parameters 不能包含未声明的参数 |
| SCRIPT | scriptCode | 使用同 Channel 独立脚本，输入来自其显式合同；本轮不另开放脚本 parameters，保留为空对象 |
| SCHEME | schemeCode | 使用同 Channel 公共方案；能力和固定参数从方案取得，API 只能提供方案 parameterDefinition 明确开放的参数 |

capabilityCode、scriptCode、schemeCode 按 kind 三选一，不能同时出现。与早期逻辑表相比，这是合同的去冗余细化；页面仍能展示方案解析后的能力类型。

方案 definition 为两个互斥形态：

- `kind=CAPABILITY`：`capabilityCode / inputDefinition / outputDefinition / parameterDefinition / fixedParameters / credentialPurpose`。credentialPurpose 可缺失/null；其他对象必填，允许无参数时为空对象。input/output/parameterDefinition 是受控类型合同，字段数各不超过 128；它们必须是该能力目录合同允许的收窄，不能扩大敏感字段可见性。
- `kind=COMPOSITION`：`inputDefinition / outputDefinition / parameterDefinition / steps / outputBindings`。内部最多 32 步，可引用单能力、独立 SCRIPT 或 CAPABILITY 方案，不能再引用组合；内部输入可以从组合输入/参数或前序输出读取。组合中的输入源扩展为 SCHEME_INPUT、SCHEME_PARAMETER，并在展开时替换为外层合法绑定；不可读取未声明的外层私有步骤。outputBindings 把声明的每个输出字段绑定到内部 STEP 或 SCHEME_INPUT，类型及保护级别必须满足 outputDefinition，不把最后一步自动当作组合输出。

### 独立脚本修订

ChannelScript 单独拥有 scriptCode、scriptName、description、engine、sourceCode 及 inputDefinition/outputDefinition；Channel 身份由所属记录确定。源码与引擎独立于 API/方案 JSON 保存，具体物理列见[数据设计](./db/README.md)。inputDefinition 使用 FieldSet；outputDefinition 以 value 字段描述脚本返回值类型，执行器统一包装为 `{value: scriptReturn}`，不是要求脚本自己返回一个 value 外壳。

SCRIPT 步骤通过 inputBindings 提供声明的输入，通过 outputBindings 从统一返回结构选择别名。沿用严格字段类型、前序引用、保护边界和错误终止规则；脚本只读取显式输入并调用允许的纯函数，不隐式调用数据查询、其他脚本或其他 API，不读取 BYTES/PROTECTED_HANDLE/敏感原文。

独立脚本以受限 Aviator 为设计方向，普通表达式的保护约束继续有效。源码长度、语法子集、编译与执行总预算及新增允许函数尚需完成详细合同；不得把字段表达式的 2,048 字节限制擅自视为独立脚本完整预算，也不得因源码外置取消限制。现有例子中的 seq.map 尚须进入明确的允许目录，当前引擎实现和旧静态检查器不因此自动支持它。

单能力方案的输入/输出名称与该能力目录一致，固定参数与 API 可传参数不能重名；需要重命名或组合输出时使用组合方案。每个能力的结果根为有名称字段的对象，包括只产出一个字节串的能力；步骤 outputBindings 因此总能使用非空路径。系统函数调用能力将 typedResult 放在 `value` 字段，例如 `{"target":["value"]}` 可把整份函数返回对象命名为 target，之后通过 `["target","orderRef"]` 读取。注册函数选择完成后，其输入输出合同用于特化该调用步骤，不把返回值当作无类型对象。

组合展开时给内部步骤加系统生成的唯一名称空间，保存时同时校验重复名、循环和展开后的总预算。固定规则不能被 API 覆盖，修改规则应修改公共方案或另建一套；两者的影响由引用列表说明。

注册目录允许的能力族：系统数据函数、对象映射、受限表达式及纯函数、字段选择、编码/解码、签名/验签、加密/解密、认证 Header。数值处理通过表达式内的纯函数使用，不对应单独的字段转换表单。每个具体 capabilityCode 都需要输入输出、参数、适用阶段、保护位置、实现可用性及测试入口；目录名称并不等于已实现算法。

首批能力、特化参数、响应 Profile 与保护顺序以[协议处理合同](./capability-contract.md)为准。DECODE_JSON_RESPONSE 的输出为受控句柄，普通表达式只读通过 VALIDATE_AND_PROJECT 后的有类型对象；每个签名/加密 Profile 必须定义最终传输位置，不能只留下无消费者的中间结果。

## 5. HTTP 传输

| 字段 | 类型 / 默认 | 规则 |
|---|---|---|
| protocol | STRING / 无 | 本期只接受 HTTP |
| method | STRING / 无 | GET、POST、PUT、PATCH、DELETE，具体可用性受目标注册限制 |
| endpointCode | STRING / 无 | 冻结 Account 可解析的目标别名；最终 base URL 来自受控运行配置，不接受业务上下文提供域名 |
| pathTemplate | STRING / 无 | 1～2048 字符的相对绝对路径，以单个 `/` 开始；变量使用 `{name}`，禁止 scheme、host、fragment、query、反斜线、`.`/`..` 段和协议相对路径 |
| pathBindings | Map / 必填可空 | 变量逐一绑定，集合必须与模板变量完全一致；结果按一个路径段编码，不能通过值改变路径结构 |
| queryBindings | HeaderBinding[] / 必填可空 | 每项 name 与 values[]；保留同名多值顺序，禁止 CR/LF；名字唯一 |
| headers | HeaderBinding[] / 必填可空 | name 大小写不敏感去重，每项 values[] 1～16 个绑定；名称须为合法 HTTP token，值禁止 CR/LF |
| body | ValueBinding 或 null / 必填 | 只能引用 BYTES 或按接口明确为空；发送时不再次序列化 |
| connectTimeoutMs | 整数 / 2000 | 100～10000 |
| responseTimeoutMs | 整数 / 10000 | 100～30000；接收全过程由调用总 deadline 限制，不因分块重新延长 |
| totalTimeoutMs | 整数 / 30000 | 1000～60000；覆盖配置读取、补数、保护处理、发送与响应处理 |

运行环境可以设置更严格的安全上限，实际预算取配置与环境上限较小者；剩余 deadline 不足则停止后续工作。查询单次上限 2000ms，最多 8 次；请求和响应体各最多 1 MiB、Header 总量 16 KiB；这些为本方案边界，不是 NI 官方产品上限。

Host、Content-Length、Transfer-Encoding、Connection 及代理控制头由传输实现处理，配置不能覆盖；Authorization 等保护头必须来自获准能力输出。明确关闭自动重试、重定向、Cookie Jar 和隐式认证重发。环境目标仍须限制出站域名与端口，不把任意 Endpoint 权限交给退款上下文。

响应提供 `status / headers / body`；Header 按小写名称提供多值读取，查询不存在的 Header 名称返回空数组 []；这是 HTTP Header 读取语义，不是任意字段的默认值。body 保留原始字节。JSON/XML 的媒体类型、编码和解析处理由步骤合同指定，不根据 Body 长相猜解码器。

## 6. 系统函数调用与返回值取值

本期用于读取业务数据的函数由开发者实现和注册。API 使用 `kind=CAPABILITY / capabilityCode=CALL_SYSTEM_FUNCTION`，parameters 只包含 `functionCode`，inputBindings 按函数注册的参数逐项绑定；不配置查询方法的内部规则，不再提供 `existing / ALWAYS / WHEN_MISSING` 查询专用配置。函数是否读取某个 Owner、如何处理缺失/null、是否采用显式入参中的已有值，由已注册函数的合同与实现负责；Operations 不能修改这些策略。

字段来源选择“系统函数”后，工具生成调用步骤，并让用户在注册返回结构中选择字段或编辑返回值表达式。持久化使用现有 Step、STEP 引用及第 3.2 节的 EXPRESSION，不把业务方法调用字符串塞入 path 或 Aviator，也不新增一份独立查询定义。返回值通过 outputBindings 取别名，多个字段引用同一个 stepCode 时共用当次结果，各自表达式独立；不同调用步骤即使函数和参数相同，也不隐式去重或跨请求缓存。函数入参也使用统一取值工具；依赖其他函数时生成明确的前序调用，而非递归内联，循环/前向引用拒绝且所有调用计入原预算。

只有执行到该函数调用步骤时才调用一次，参数先验证，结果按返回合同验证；失败停止依赖链，缺少必需数据不发送渠道请求。函数体不可隐藏第二次渠道通信。首批目录成员见 [registry-contract.md](./registry-contract.md)，Owner 和运行边界见 [query-contract.md](./query-contract.md)。保存检查及字段选择器只读元数据，不执行函数获取真实返回值。

## 7. 结果规则与输出

`ResultRules = { rules, fallbackResultCode }`。rules 是有序的 `{ condition, resultCode }` 列表，按顺序第一个匹配者决胜；不得依赖未定义优先级。fallbackResultCode 本期固定为 `UNRECOGNIZED`，不能配置为成功。保存时拒绝恒真首规则及可直接证明的重复条件，其他重叠按显式顺序解释并在预览中展示。

Condition 以必填 `op` 判别，只允许 `EQ / NE / EXISTS / ALL / ANY / NOT`：EQ/NE 除 op 外只有同类型 left/right ValueBinding；EXISTS 除 op 外只有 value；ALL/ANY 除 op 外只有 children（1～32 项）；NOT 除 op 外只有一个 child。各分支拒绝其他字段。本轮不扩展结果判定语言，Condition 操作数暂不接受 EXPRESSION；需要表达式投影时先在响应处理步骤产生明确输出。条件中不能执行业务查询或隐式类型转换。响应业务字段必须来自已完成该协议要求的保护与解析步骤，原始 body 不能直接用于成功判定。

ResultOutput 为 `{ resultCode, fields }`，fields 每项包含 `name / valueType / required / binding`，必须覆盖每个规则结果与 fallback，字段名在分支内唯一。先分类，再检查该分支必填字段；拒绝分支不要求成功引用。输出数据分类从来源传递，不能通过声明普通类型降低保护级别。

执行失败不是 resultRules 可改写的分支。固定执行事实至少区分 `NOT_SENT / MAY_HAVE_BEEN_SENT / RESPONSE_RECEIVED`，并返回失败阶段、允许的原因和可选协议状态；不据此自动决定 Payment Action 状态、发布事件或发起重试。NI 退款观察记录不等于本次退款成功，维持前期样例的关联约束。
