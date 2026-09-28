# 公共数据模型、层级对象与响应场景设计

> **2026-09-21 后继：** [Platform HTTP 执行修订](./platform-http-execution-amendment.md)确定配置管理、存储和执行归 Platform，业务系统通过内部 HTTP 调用。本文未同步的 Payment 落点或 Owner 未定表述仅作前序记录；字段与物理 SQL 继续评审。

**后续页面增量：** [公共脚本与 HTTP 对象](./public-script-http-context-design.md)在 API 配置下增加公共脚本入口，M01～M08 导航已同步，M05/M06 页签改为“脚本处理”，M06 响应字段改从处理后的 http.responseBody 读取。本文公共结构与场景合同不变；下文的两页统计只描述本轮模型设计范围。

本次是 Operations 的设计增量：新增 1 个数据模型功能页、调整现有 API 配置页的输入与响应状态，共 8 张新增画板和 1 张索引；未新增已冻结 HTTP 端点或物理表，未修改 SQL。用户已同意公共模型与响应场景的维护方向，并补充“对象可以有层级，不是完全铺开的”。本稿完成该范围的逻辑和页面设计，不进入应用研发。

本文拥有模型维护、层级字段、引用及响应场景的当前设计，补充[通用执行合同](./generic-execution-amendment.md)，取代其中仅按 executeStatus 区分输出的限制，以及旧只读 PAYMENT/REFUND 目录作为唯一上下文来源的设计。系统函数及协议能力继续由代码注册，不允许通过编辑模型增加函数、查询或密钥访问权限。

## 1. 页面与维护位置

```text
Operations
  API 配置
    接口配置
      接入分组 / API
        请求映射：绑定输入模型，配置字段取值
        响应映射：引用响应合同，配置条件和场景输出映射
    数据模型
      对象模型：字段树、属性、JSON 示例、引用关系
      响应合同：业务场景、执行状态、输出模型
```

数据模型独立于接入分组。RefundContext、PaymentContext、Money 可以被多个接入方的 API 引用；Payment 只是业务分类，不是引擎内置枚举。响应合同作为同一功能页的第二个页签，不另加一个同义维护中心。

模型编辑保存字段结构，实际 context 值仍由调用方传入。新增字段不能自动使业务调用方提供值，也不会运行查询。涉及真实业务输入输出的修改必须与其消费者合同一致；后台配置不能自行重写现有 Payment HTTP Wire。

## 2. 三类配置关系

| 对象 | 管什么 | 不包含什么 |
|---|---|---|
| 对象模型 DataModel | 可复用 JSON 对象结构、字段约束、嵌套类型和模型引用 | 实际业务值、查询、字段取值表达式 |
| 响应合同 ResponseContract | 某类业务允许的响应场景、各场景的 executeStatus 与输出模型 | 第三方状态值、响应路径和渠道条件 |
| API 定义 | 输入名称与模型引用；本 API 的响应条件、场景选择及字段映射 | 公共模型字段的私有副本或局部类型覆盖 |

对象模型的逻辑元数据为 modelCode、name、businessGroup、description、root。modelCode 在本配置目录唯一，建议 `[A-Z][A-Za-z0-9]{0,63}`，不修改既有 API/能力代码命名规则；业务分组只帮助检索。根类型为 OBJECT。空对象模型可以用于确实无字段的合同；无输入 API 也可以使用空的输入绑定集合，不强制创建 NoInput 模型。

响应合同的逻辑元数据为 contractCode、name、businessGroup、description、scenarios。具体管理 Owner、HTTP 路径与物理存储仍需按通用化范围一起确定，本稿不把逻辑对象数量解释成最终表数量。

## 3. 字段层级

对象结构由真实父子节点表达，不把点路径存为扁平属性名。一个退款上下文的说明结构如下；它是本轮合成设计示例，不是旧平铺 RefundContext 的自动兼容升级：

```text
RefundContext                          OBJECT
  originalTransaction                  OBJECT
    referenceId                        STRING
  amount                               REF → Money
    value                              DECIMAL(24,8)
    currency                           STRING
  items                                ARRAY（可选）
    []                                 REF → RefundItem
      lineId                           STRING
      quantity                         INTEGER
      amount                           REF → Money
  reason                               STRING（可选）
```

可维护的字段类型：STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY，以及引用已有对象模型的 MODEL_REF。枚举是基础类型的约束，不另建一套无类型字符串；UI 可以用 ENUM 标签摘要展示。BYTES、PROTECTED_HANDLE 继续属于受控协议能力，不进入普通公共 JSON 模型编辑器。

每个字段包含字段名、显示名称、类型、required、nullable、说明及适用的约束。STRING 支持长度和有限枚举；INTEGER/DECIMAL 沿用精确字符串、范围与精度规则；ARRAY 声明 items、minItems、maxItems 和元素可空性。示例不会用浮点金额替代精确十进制值。

### 对象、数组与引用

- OBJECT 内维护 children/properties；UI 点“添加子字段”后，新字段的父节点固定为当前对象。基础类型不能添加子字段。
- ARRAY 只有一份元素类型定义，可选基础类型、内嵌对象或引用模型；不为第 0、1、2 个真实元素各定义一套结构。`[]` 是字段树的元素标记，不是 JSON 属性，也不是自动取第一项。
- MODEL_REF 引用已有模型。展开后显示引用来源与子结构，但不能在引用处删除、重命名或覆盖其字段；从“打开模型”进入源模型编辑。
- 模型可以多层嵌套和引用，但拒绝自引用及 A→B→A 等循环。沿用展开深度最多 8、每个展开结构字段/元素节点合计最多 128 的设计预算；数组数据数量由 maxItems 和调用总预算约束，不靠 UI 折叠绕过。
- 同一父对象下字段名唯一；不同父对象可以有同名字段。字段名默认 lowerCamelCase，不使用含点的字段名制造路径歧义。

### 缺失与 null

required 管字段是否出现；nullable 管出现时能否为 null。可选父对象整体缺失时，不检查内部必填子字段；父对象存在且非 null 时，逐层校验其子字段。父对象允许 null 时，显式 null 不展开子结构。空对象、空数组、null、未提供是不同值。

数组的字段可空与元素可空分别维护。改变 OBJECT/ARRAY/基础类型不会静默清除子字段或旧约束；编辑器展示影响，确认类型变更后仍需整份模型校验和保存。

## 4. 输入模型与层级取值

API 的输入绑定逻辑为：

```json
{
  "inputBindings": [
    {"name":"refundContext","modelRef":"RefundContext","required":true}
  ]
}
```

调用数据对应：

```json
{
  "context": {
    "refundContext": {
      "originalTransaction": {"referenceId":"10001"},
      "amount": {"value":"10.00","currency":"AED"}
    }
  }
}
```

输入名称是 context 的根成员，RefundContext 是模型身份，两者不混用。名称唯一并遵守现有表达式变量约束；修改名称或替换模型后，必须重新校验直接路径、表达式和函数入参。前版平铺 refundAmount/currency 示例不会被自动迁移到 amount.value/amount.currency。

`refundContext.amount.currency` 解析为 CONTEXT 路径 `["refundContext","amount","currency"]`。模型树为上下文路径、Aviator 变量与系统函数参数绑定提供类型提示；系统函数返回树继续来自注册的函数描述符。

数组节点可作为一个完整值绑定；字段树中的 `items[]` 不作为普通对象路径写进配置，也不隐式遍历生成多笔操作。数组选择/加工通过现有受限表达式、简单脚本或注册纯函数完成，结果整体按数组元素模型验证；不新增循环调用渠道的节点。

## 5. 响应合同与多个场景

一个响应合同包含多个场景。每个场景拥有稳定的 sceneCode、名称、executeStatus 和 outputModelRef；同一个 executeStatus 可以对应多个场景。API 只引用场景，不自行把公共场景的 SUCCESS 改成 FAIL。

| PaymentResponse 场景 | executeStatus | 输出模型 | 必填结构摘要 |
|---|---|---|---|
| COMPLETED | SUCCESS | PaymentCompletedOutput | paymentStatus、transaction.id；authorizationCode 可选 |
| ACTION_REQUIRED | SUCCESS | PaymentActionOutput | paymentStatus、transaction.id、nextAction.type、nextAction.redirect.url |
| REJECTED | FAIL | PaymentRejectedOutput | paymentStatus、error.code；error.message 可选 |
| ERROR | ERROR | 无，固定 {} | 未匹配、执行/校验异常或 API 显式 ERROR 规则 |

这是协议解释完成后的 API 调用结果，SUCCESS 仍不自动等于资金成功。实际 Payment 业务状态枚举和响应 Wire 由 Payment 的独立合同决定，本表是跨渠道输出结构的设计例子。

如果同一状态下的模型结构不同，响应合同需指定模型中已声明的业务判别字段与场景固定值，且相应模型约束与之相符。例如本例使用 outputs.paymentStatus = COMPLETED / ACTION_REQUIRED / REJECTED。引擎不强加某个通用业务字段名，不把 sceneCode 或 resultCode 加回返回外壳。只有一个结构的简单 API 不必额外制造业务判别字段。

同业务的不同渠道引用同一 ResponseContract，因而共享场景名称、业务判别值和输出模型。小范围差异由公共模型明确的可选字段表达。不同业务可使用不同合同；公共模型引用没有隐含继承或无约束扩展 Map。

## 6. API 响应规则与 outputs 组装

本轮将原来按 executeStatus 分组的输出细化为按 sceneCode 分组。逻辑结构为：

```text
responseContractRef = PaymentResponse
statusMapping.rules[] = { condition, sceneCode }
statusMapping.defaultScene = ERROR
outputMappings[] = { sceneCode, fields }
```

规则仍按顺序首个匹配；多个渠道条件可以指向同一场景，该场景复用一份输出映射。被规则引用的每个非 ERROR 场景都必须有完整且符合模型的映射；未使用的公共场景不要求该 API 虚构渠道支持。通用 executeStatus 从所选场景读取，不再与场景重复维护一份可冲突值。

处理顺序：协议保护/解码/必要校验 → 匹配场景 → 映射该场景 outputs → 递归类型与判别值校验 → 返回。任何必需输出缺失、引用不可用或结构不符均返回 ERROR / {}。ERROR 不表示外部未执行，不触发自动重试。

输出字段树根据所选模型生成；API 页面不能临时改其类型或增加未声明属性。字段值仍支持已有来源绑定和受限表达式，公共场景固定的判别值只读显示。

对于 OBJECT，选择“按子字段组装”或“整体取值”；两者互斥，禁止同时绑定 parent 和 parent.child。整体取值也必须递归校验，不能借此透传未知响应字段。按子字段组装时，父字段必填则必须构造并校验；父字段可选且全部子项缺失时省略父字段；只要有子项出现，所有子字段约束必须满足。ARRAY 整体取值后逐元素校验；UI 的元素树展示结构，不虚构逐元素表达式实现。

示例返回仍只有两个顶层字段：

```json
{
  "executeStatus":"SUCCESS",
  "outputs":{
    "paymentStatus":"ACTION_REQUIRED",
    "transaction":{"id":"txn_123"},
    "nextAction":{
      "type":"REDIRECT",
      "redirect":{"url":"https://pay.example.test"}
    }
  }
}
```

示例 URL 为合成值。真实交互信息仍是当次返回的临时材料，不因新增模型页获得存储、日志或长期展示权限。

## 7. 页面交互与状态

| 位置 | 主要操作 | 结果 |
|---|---|---|
| M01 模型目录 | 搜索、对象模型/响应合同切换、新建、编辑、查看引用 | 进入选中模型；无数据时显示新建入口，筛选无结果可清空筛选 |
| M02 字段结构 | 展开/折叠、选中字段、添加根/同级/子字段、编辑引用 | 左侧保留层级，右侧显示选中节点属性；引用模型子树只读 |
| M03 数组编辑 | 选择元素类型/引用、设置数量及可空性 | 应用到当前模型草稿，再由整份保存生效 |
| M04 响应合同 | 新建/编辑场景、绑定执行状态和输出模型 | 统一多渠道场景结构；模型变化更新引用校验 |
| M05 请求映射 | 添加输入名称与模型、插入完整路径 | 按 context 实际层级取值，金额处理仍在表达式内 |
| M06 响应映射 | 编辑条件、排序、选场景、配置层级字段 | 输出结构来自公共合同；返回示例使用合成值 |
| M07 引用关系 | 查看直接/间接引用、定位失效字段 | 有破坏性影响时阻止保存，保留草稿；被引用模型不允许删除 |
| M08 添加字段 | 选择 OBJECT 父节点、添加子字段并应用 | 生成真实嵌套字段，不生成含点的平铺字段 |

未保存草稿离开时提示保存或放弃；字段“应用”不等于模型已保存。只读权限展示字段树与引用，不显示可执行的修改操作。加载失败保留重试入口，不将空目录误报为无模型。

共享模型保存要在服务端检查全部直接/间接引用，拒绝字段删除、改名、类型/必填收紧等破坏已绑定合同的变更；界面中的影响列表不是唯一保护。循环引用在保存前拒绝。兼容性无法静态证明的变化应提示具体未闭合依赖，不宣称业务消费者已自动适配。

并发修改沿用整份配置的冲突检测。冲突时保留草稿并提供重新读取/比较，不覆盖他人的保存。请求超时导致保存结果未知时先重新查询当前配置，不盲目重发写操作。无发布、审批或版本选择流程；新调用读取保存后的模型、响应合同、API、脚本和方案的一致内容，已开始调用继续使用其完整内存定义。

## 8. 工件与验证范围

视觉源文件为[Operations 原 Pencil 文件](../../../operations-console-web/docs/design.pen)。

| 画板 | 节点 | 预览 |
|---|---|---|
| M01 公共模型目录 | ciXrn | [PNG](./assets/pencil-models/ciXrn.png) |
| M02 嵌套字段树 | krywR | [PNG](./assets/pencil-models/krywR.png) |
| M03 数组与元素模型 | lWKuf | [PNG](./assets/pencil-models/lWKuf.png) |
| M04 响应场景与输出结构 | v6gTVe | [PNG](./assets/pencil-models/v6gTVe.png) |
| M05 API 输入绑定 | EXQBQ | [PNG](./assets/pencil-models/EXQBQ.png) |
| M06 API 场景与嵌套输出 | ACSRY | [PNG](./assets/pencil-models/ACSRY.png) |
| M07 引用影响 | Hnf5i | [PNG](./assets/pencil-models/Hnf5i.png) |
| M08 内嵌对象与子字段 | PTliP | [PNG](./assets/pencil-models/PTliP.png) |
| M00 设计索引 | GTKAi | [PNG](./assets/pencil-models/GTKAi.png) |

旧 CAPI 原型保留协议/取值细节，导览标为前版参考；它们的支付方式和旧结果码不代表当前通用合同。当前新画板的示例和模型说明相互对应，未实现真实表单交互、表达式求值、模型注册、业务查询或渠道调用。检查记录见[设计静态验证](./assets/model-library-design-verification.json)。通用管理 Owner、完整 Wire、SQL 和独立脚本管理等剩余项仍属于后续详细落点，不因画板完成而宣称已完成实施。
