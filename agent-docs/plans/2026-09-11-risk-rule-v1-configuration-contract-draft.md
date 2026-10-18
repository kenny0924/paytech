# Operations 风控规则 V1 配置：需求与接口合同

- 日期：2026-09-11
- 业务决定：[已确认范围与语义](../decisions/2026-09-11-risk-rule-v1-configuration.md)
- 文档状态：实施合同；依据已确认范围细化工程字段和兼容规则，实施/验证/数据库状态只见当前状态台账。
- 需求：RCO-14；现有管理入口：RCO-I12；全局流程：E2E-15。
- 当前实现、验证及部署：[CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md)

## 1. 系统改动清单

| 系统 | 必要影响 | 范围 |
|---|---|---|
| risk-compliance-service | risk-api 请求/详情/目录；risk-decision 聚合、目录、参数校验、持久化、Hash、幂等与静态检查；risk-web 转换；risk-test | 主要后端实现 |
| operations-console-bff | 本地 DTO、请求/成功响应严格 Shape、risk-api 双向转换、目录安全投影、字段错误和 Simulator 测试 | 逐请求 PLT-I02 后单次 Risk 调用，不拥有业务状态 |
| operations-console-web | 原 V1 页面交互、动态目标控件、手工标签和拒绝文案、动作编辑、历史回显与保存 | 复用现有页面接入，不另建 V2 页面 |
| platform-support-service | 现有五权限、菜单/按钮和九条 Route 的配置核对及回归 | 同路径、同权限和同限额方案无新增业务 API 注册 |
| api-gateway / Nginx | 现有路由、可信身份、Problem、Body/日志边界回归 | 不因 JSON 字段增加而新增算法、Target 或 Root |
| 其他核心系统、Foundation、Vault | 本期无新增运行调用 | 若未来扩展实际输入或执行，再单独评估 |

Root 需求文档及规范只同步本次目标，不修改其他模块的生产代码或将 Foundation 命令幂等迁移扩散到 Risk。

## 2. 与当前源码的差异

| 已有能力 | 本期增量 |
|---|---|
| 条件 AST、AND/OR/NOT、排序、启用、三种终态选项 | 页面简化且无损往返；允许配置零终态与非终态附加动作 |
| Rule 必填单 action，只有 actionCode/actionVersionNo | 可空终态 action + 有序 additionalActions 和类型化参数 |
| 可空 reasonCode，无消息 | 手工 reasonMessage；新拒绝配置完整校验；历史原样读取 |
| 九个规则可引用字段，actionParameterFlag 全 false | Risk 提供当前 Point 可支持的主体绑定及参数目录；不开放不存在的 Consumer 输入 |
| 已有多行 Rule Action/Parameter 表 | 接通写读、引用、规范投影和参数验证；不能把有表视为已有执行能力 |
| 单动作 Hash，Release 参数必须为空 | 新字段的完整 Hash/幂等和历史识别；旧 Hash 不重算 |
| BFF/Web 严格固定字段和目录 | 三端同步协议适配；不能只改 Risk 或放开未知字段 |

源码证据：[Risk API](../../paytech/risk-decision/paytech-risk-decision-api/src/main/java/com/paytech/risk/decision/api/DecisionRuleSetManagementModels.java)、[创作目录](../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/authoring)、[静态检查](../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseStaticValidatorImpl.java)、[Hash](../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizerImpl.java)、[BFF Shape](../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestShapeValidator.java)、[Web 编辑器](../../operations-console-web/src/risk/RuleSetEditor.tsx)。

## 3. API 路由与授权

新增 HTTP API：0。扩展 3 条现有接口，浏览器增加 /api/operations 前缀，BFF/Risk 使用下表路径。

| Method | Path | Route Code | 权限 | Body 上限 |
|---|---|---|---|---:|
| GET | /decision-rule-sets/v1/authoring-context/{pointCode} | operations-risk-rule-authoring-context | RISK_DECISION_CONFIG_READ | 0 |
| POST | /decision-rule-sets/v1/rule-sets | operations-risk-rule-set-save | RISK_RULE_SET_SAVE | 65,536 UTF-8 Bytes |
| GET | /decision-rule-sets/v1/rule-sets/{ruleSetVersionId} | operations-risk-rule-set-detail | RISK_DECISION_CONFIG_READ | 0 |

另外六条列表、Freeze 和 Candidate 路径保留。Freeze/Create Candidate/Validate 仍各为 16,384 Bytes；GET 无 Body；不新增 Query、Header 或权限豁免。Save 48 KiB 预警；容量必须用最终 JSON 样本验证。成功回执继续返回坐标/Hash，不为展示新增配置而复制整个快照。

BFF 保留可信 Actor/Session、Request ID、Idempotency-Key、Base/Expected Hash 的传播；不接收 Body 声明的操作者，不计算 Risk Hash。Validate 的确定性 Key 和恢复权限仍沿用现行合同。

## 4. Authoring Context 响应合同

保留现有 point、fields、operators、actions 和未启用扩展的明确空集合。调整 actionParameters 为类型化集合，增加当前决策点可用的 targetBindings。下列名称为本期 Wire，不是新增字典 CRUD。

| 数据 | 字段与语义 |
|---|---|
| Target Binding | bindingCode、displayName、subjectType、requiredFields；requiredFields 以 role + fieldCode 表达 Client Namespace 等复合身份要求 |
| Action Descriptor | 沿用代码、版本、名称等；非终态 terminalFlag=false、decisionCode=null；按批准目标目录限制可用绑定 |
| Parameter Definition | actionCode、actionVersionNo、parameterCode、valueType、requiredFlag、allowedSourceType、targetParameterFlag；另提供明确的数量/值域限制 |
| 显示限制 | 类型化时长上下限、标签代码规则和数量限制；不允许任意脚本、JSON Schema、SQL 或表达式 |

不返回 rejectReasons，不建设拒绝默认文案目录。BFF 必须核对上游工程合同并删除 handlerCode/implementationVersion/implementationHash；浏览器只获得安全配置元数据，不获得任何真实目标身份。

当前只提供 `CURRENT_CLIENT_V1`：`subjectType=CLIENT`，`requiredFields=[{role: SUBJECT_ID, fieldCode: client_id}]`。该输入为 STRING、required=true、INTERNAL、ruleReference=false、actionParameter=true；它不会出现在条件字段选择器。工程绑定代码包含版本，既有映射不可修改。Consumer 缺少本 Point 的批准输入，因此当前不进入目录。

两项附加动作均为 version=1：`APPLY_COOLDOWN`（RESTRICTION/MAX）和 `ADD_RISK_TAG`（TAG/UNION）。两者 `terminalFlag=false`、`decisionCode=null`、`shadowSupportedFlag=false`、`status=CONFIGURATION_ONLY`；Owner 的三个 Handler 证据字段明确为 null，BFF 删除这些字段后返回浏览器。原三个 ACTIVE 终态动作的实现指纹保持不变。

参数定义另外包含 `maxValues`、`minimumValue`、`maximumValue`、`valuePattern`；不适用的字段显式 null。目标定义 `SUBJECT_ID` 为必填 STRING/CONTEXT/target=true/maxValues=1。值定义分别为 `DURATION`（DURATION/LITERAL/1..31536000000 毫秒/maxValues=1）和 `TAG`（STRING/LITERAL/`[A-Z][A-Z0-9_]{0,63}`/maxValues=32）。Web 根据 Risk 目录显示主体与值域，BFF 不注册主体或计算业务参数。

## 5. 保存与详情 Wire 合同

顶层沿用 pointCode、ruleSetCode、ruleSetName、baseRuleSetVersionId、baseContentHash、changeReason、rules。ConditionNode、Operand、Decimal 和时间规范继续使用现有定义。

```typescript
interface RuleDefinition {
  ruleCode: string;
  ruleName: string;
  priorityNo: number;
  enabledFlag: boolean;
  conditionNodes: ConditionNode[];
  reasonCode: string | null;
  reasonMessage: string | null;
  action: { actionCode: string; actionVersionNo: number } | null;
  additionalActions: AdditionalAction[];
}

interface AdditionalAction {
  sequenceNo: number;
  actionCode: string;
  actionVersionNo: number;
  targetBindingCode: string;
  parameters: ActionParameter[];
}

interface ActionParameter {
  parameterCode: string;
  valueSequence: number;
  sourceType: 'CONTEXT' | 'LITERAL';
  fieldCode: string | null;
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATETIME' | 'DURATION';
  literalValue: string | boolean | null;
}
```

相对先前讨论，合同用 targetBindingCode 集中选择整个主体绑定，防止 Consumer 的 Client Namespace 被遗漏。目标参数由 Risk 根据该绑定生成；调用者不得在 parameters 中再次声明或覆盖目标参数。其他参数只有工程目录实际允许时才可引用 Context，首批时长和标签使用 Literal；不开放 Feature 参数。

冷却保存正 Duration 毫秒字符串，例如 30 分钟保存为 1800000；标签集合使用同一标签参数代码下多个连续 valueSequence 的 STRING 值，不使用 CSV 或自由对象。每条规则最多 16 个附加动作，sequenceNo 从 1 连续递增；参数 valueSequence 按参数代码从 1 连续递增。TAG 按代码去重并升序重排，输入最多 32 项；DURATION 必须是无前导零的正整数字符串。BFF 原样传播，不自行规范化。

新拒绝规则必须填写 reasonCode 和 reasonMessage。非拒绝规则保留历史 reasonCode；reasonMessage 只允许 DENY_CHECKOUT，规范化为 NFC 并去首尾空白，长度 1–500；其他决定必须为空。无终态且无附加动作的规则拒绝保存。每项附加动作必须是当前 Point 允许的非终态动作，其 Code/Version、Binding 和参数必须一致。

详情返回与保存对应的完整内容。旧消息缺失时保持 null，旧附加动作保持空集合；这些是展示投影，不写回旧记录或重新计算旧 Hash。同一拒绝 Code 可以在不同规则中有各自冻结的手工文案，本期不对 Code 建全局唯一约束。

结构/类型错误按 400、字段约束按 422；Risk 业务校验沿用 400 BUSINESS_CHECK_FAILED（包括不可用绑定、参数值域和冻结门禁）、并发或幂等冲突按既有 409、缺失资源按 404；所有错误沿用现行 Problem 合同，禁止泄漏 Literal、目标原值和拒绝消息。Candidate 检查失败仍按既有 200 + REJECTED 表达，不映射为 HTTP 请求错误。

## 6. DML 与数据库影响

新增 API 注册 DML：**0 条**。现有五权限与九条路由、请求上限不变；Platform/Gateway 不增加 API、权限或路由注册。本期不新增业务表、拒绝原因字典或标签字典。

| 表 | 精确变化 |
|---|---|
| t_rule_set_version | content_format VARCHAR(64) NOT NULL DEFAULT 'LEGACY'；值 LEGACY/CONFIGURATION |
| t_risk_rule | reason_message VARCHAR(500) NULL；reason_code 保留并更新手工配置说明 |
| t_rule_action | target_binding_code VARCHAR(64) NULL；终态必须为空，附加动作引用不可变绑定 |
| t_action_version | 三个 Handler 证据列改为 nullable；增加 CONFIGURATION_ONLY 状态和 TAG 分类。REGISTERED/ACTIVE/已退役执行版本仍必须有真实证据；仅配置及其 RETIRED 历史记录证据为空 |
| t_action_version 初始化数据 | 240004 APPLY_COOLDOWN、240005 ADD_RISK_TAG，共 2 条 |
| t_action_parameter_definition 初始化数据 | 2400041 SUBJECT_ID、2400042 DURATION、2400051 SUBJECT_ID、2400052 TAG，共 4 条 |
| t_rule_action_parameter | 复用结构化列：Risk 生成 CONTEXT 目标字段引用；时长写 duration_value，标签写 string_value；其他类型列必须空 |

唯一[初始化 DML](../../paytech/risk-decision/docs/database/sql/risk-decision-initial-data.sql)用于空库。[人工增量 SQL](../../paytech/risk-decision/docs/database/sql/2026-09-11-risk-rule-v1-configuration.sql)用于已核对的旧库；先备份，一次性执行，发生中断先核对，不直接重跑。MySQL DDL 隐式提交，不能当作可整体回滚事务。无 CHECK/FK DDL；应用及模型 Sidecar 保证不变量。

主体目录属于当前 Risk 工程能力定义，不单独造注册表。实际 Client ID 不在配置中持久化。仅使用附加动作时，配套 Point Version 增加 client_id 输入定义；纯终态配置继续使用原九字段，从而保留历史冻结内容。

## 7. Hash、幂等和执行门禁

`content_format` 是持久化格式证据，不是第二套 API 或业务版本流。现有行由 DEFAULT 保持 LEGACY，旧内容不重写。新页面所有规则显式传 `reasonMessage` 和 `additionalActions`，保存为 CONFIGURATION。新增三个规范投影：

- `RISK_RULE_SET_CONFIGURATION_V1`：包含可空终态、拒绝文案、附加动作/顺序、绑定代码与类型化参数。
- `RISK_CREATE_RULE_SET_CONFIGURATION_COMMAND_V1`：以上配置与原命令头一起参与请求 Hash；Risk 自有幂等机制接受这一个新增创建合同，不迁移 Foundation 幂等 JAR。
- `RISK_DECISION_RELEASE_CONFIGURATION_V1`：纯终态新格式通过 Freeze 后，冻结 Rule Set 格式和拒绝文案；旧 Release 仍选择原投影。

原七类 V1 Golden 字节保持不变；含新增内容时不得选择 LEGACY，不能根据空数组/空消息猜测历史格式。Point Version Hash 沿用现有合同，其字段闭包与 Rule Set Hash 会自然体现新配置。

兼容请求：Risk 将 additionalActions 缺失或 null 视为旧命令形态，仅允许 reasonMessage=null；同一个规则集不可混用新旧形态。BFF 同时严格接受旧七字段 Rule 和新九字段 Rule，新形态 additionalActions 必须为数组。新创建 DENY 必须提供代码和文案，旧成功命令先按旧格式计算 Hash 并走原成功重放，再进入新增创建校验，因此历史拒绝不需要补造消息才能重放。旧形态的新 ALLOW/REVIEW 继续兼容。详情一律投影新增字段：旧消息 null、旧附加动作 []；历史非拒绝 reasonCode 原样保留。

包含附加动作的配置仅可 DRAFT 保存/读取；Freeze 在同一锁后完整闭包内以 BUSINESS_CHECK_FAILED 拒绝，不能进入 Candidate。纯终态新配置保留现有冻结、八项静态检查和 TEST Candidate 恢复流程。保存不产生 Evaluation、Action Intent/Effect、Restriction、主体标签、MQ 或补偿任务。

## 8. 三端切换与验收

三端 Reader 是严格合同，升级需协调 Schema/目录 → Risk → BFF → 原 V1 Web。旧 BFF 无法读取新增 Owner 响应，切换期间应避免继续使用旧页面的规则管理入口。所有既有路径、可信头、PLT-I02、单次 Owner 调用和日志脱敏保持；不自动变更 Platform 路由或发布环境。

写入 CONFIGURATION 后，不能将 Owner 回滚到会忽略新增字段的旧代码；保留兼容 Schema/后端并控制页面入口。不能删除新列、清空附加动作或重算历史 Hash 作为回滚。

验收包括：真实 HTTP/MySQL 保存/回读/再编辑；手工拒绝必填；动态绑定资格；时长/参数矩阵；标签规范化和幂等；原 Hash Golden；新纯终态冻结/Candidate；附加动作冻结阻断及零运行事实；BFF 逐字段传播、权限拒绝后 Risk 零调用和 Handler 隔离；V1 页面未保存保护、选中编辑、Decimal/NOT/顺序和 64 KiB。当前不具备 Consumer 输入，所以测试拒绝 Consumer 绑定，不声称已实现跨 Client Consumer 执行。
