# 结账点运行期评估：方案设计

- 阶段 3：方案、接口与数据设计（2026-10-16，已确认）
- 依据：[需求](./requirement.md)（Q1～Q7）、[差异与影响](./technique.md)（D1～D4 已确认）、[支付反欺诈一期设计](../../20261014/risk-支付反欺诈一期/design.md)、[评估查询设计](../risk-评估结果查询与解释/design.md)
- SQL：[db/ddl.sql](./db/ddl.sql)、[db/dml.sql](./db/dml.sql)（待执行，设计批准不改变任何环境）

**规模：**本次方案涉及 3 个系统（Risk、Commerce、Operations Web；Operations BFF 只改 1 行注释）；**新增 0 个接口，0 个路由变化**，扩展 3 个已有 Risk 接口的风控点范围（统一评估、执行发布、按业务身份查评估），Commerce 创建结账接口增加 2 种错误返回（503、502）；**新增 0 张表**，重建 1 张表 `t_checkout_input_snapshot`（41 列 → 17 列，4 个索引；0 行）；DML 共 14 行（`t_risk_variable` 更新 10 行、新增 3 行，`t_decision_point_version` 新增 1 行）；Platform 注册 DML 0 行；新增页面 0 个，调整已有页面 3 个（执行发布、决策流、评估查询）及附加动作编辑区。

## 1. 主流程

```text
Client POST /checkouts
  → Commerce CreateCheckoutProcessor（事务外）
      CCT 商业事实 → 【Risk】 → 税 → 文件 → 保存订单（短事务）
  【Risk】CheckoutRiskPortImpl
      POST /risk-decisions/v1/evaluations  { pointCode: CHECKOUT_PREPARE, contractVersion: 5, businessKey: orderId, inputs: 12 字段 }
        → Risk EvaluateDecisionProcessor → CheckoutPrepareEvaluator
             按 orderId 查快照：有 → 比较稳定摘要（一致返回原结果 / 不一致 409）
             读结账生效执行发布（无 → 503）→ 引擎执行（无 Feature）→ 一个事务写 评估 + 快照 + 规则/流程轨迹
      ALLOW → 评估 ID 冻结到 t_order_context.risk_checkout_assessment_id → 继续
      REVIEW / DENY → 422 CHECKOUT_NOT_ELIGIBLE，不落订单
      未知 → 同请求重试一次 → 仍未知 503 DEPENDENCY_UNAVAILABLE，不落订单
```

- **数据 Owner：**评估、快照、发布归 Risk；评估 ID 的冻结引用归 Commerce（已有列，不改表）。
- **事务：**Commerce 风控调用在 Processor 事务外，失败时没有任何本地写入；Risk 评估在一个本地事务内写入全部评估行（同支付）。
- **幂等：**业务身份 `CHECKOUT_PREPARE:orderId`（`uk_evaluation_request`）+ 快照 `uk_checkout_order`；Commerce 同一请求内的重试复用同一 orderId 与同一输入，由 Risk 重放保证不重复评估。Client 用同一 `client_order_id` 重新提交时 Commerce 分配新 orderId，前次未落单的评估成为孤立记录（可接受，可在评估查询页查到）。
- **版本：**`ORDER_PREPARATION` v5 为结账当前合同；v4 只读（第 3 节）。

## 2. 接口

### 2.1 Risk（内部，`risk-api`；不新增路由）

| 接口 | 变化 | 兼容 |
|---|---|---|
| `POST /risk-decisions/v1/evaluations` | 新登记 `CHECKOUT_PREPARE:5` 评估器 | 支付不变；结账 `contractVersion=4` 返回 400 |
| `POST /risk-decisions/v1/execution-releases`（及校验、影子运行、激活） | 结账点可创建；新增门禁：流程版本必须使用 v5 | 支付不变 |
| `GET /risk-decisions/v1/evaluations?pointCode&businessKey`、`/{id}`、`/{id}/explanation` | `pointCode=CHECKOUT_PREPARE`、`businessKey=orderId` 可查 | 代码不改，评估器登记后自然支持 |
| 创作上下文 / 变量创建 | 结账目录只列 v5；v4 不能新建变量；结账不能新建实时累计、名单变量；结账不再出现附加动作目录 | 旧 v4 规则集、流程、变量照常可读 |

**请求（`risk-api` 新增 `CheckoutPrepareRiskInput`，写法同 `PaymentAttemptRiskInput`；`toRequest(orderId)` 生成统一请求，空值不传）：**

```json
{
  "pointCode": "CHECKOUT_PREPARE",
  "contractVersion": 5,
  "businessKey": "812345678901234567",
  "inputs": {
    "clientId": "100001", "entityNo": "AE001", "entitySourceType": "PARTNER", "localPartnerId": "300001",
    "consumerCountryCode": "AE", "currency": "USD", "merchandiseAmount": "120", "discountAmount": "20",
    "netAmount": "100", "industryCode": "DIGITAL_GOODS", "mccCode": "5816", "lineCount": "2"
  }
}
```

**v5 合同字段**（合同字段编码 snake_case、分级全部 INTERNAL；请求键为驼峰；值全部为字符串）：

| 合同字段 / 请求键 | 类型 | 必填 | 格式 / 长度 | 说明 |
|---|---|---|---|---|
| `client_id` / clientId | STRING（CLIENT_ID 角色） | 是 | `[1-9][0-9]{0,18}` | 条件 + 动作参数（保留旧动作规则集可读） |
| `entity_no` / entityNo | STRING | 是 | `[A-Z0-9]{5}` | |
| `entity_source_type` / entitySourceType | STRING | 是 | PLATFORM / CLIENT / PARTNER | |
| `local_partner_id` / localPartnerId | STRING | 否 | `[1-9][0-9]{0,18}` | PARTNER 必有、其他必无，违反返回 400 且不写评估 |
| `consumer_country_code` / consumerCountryCode | STRING | 是 | `[A-Z]{2}` | 取订单消费者国家（v4 为 `country_code`） |
| `currency` / currency | STRING（CURRENCY 角色） | 是 | `[A-Z]{3}` | |
| `merchandise_amount` / merchandiseAmount | MONEY（币种取 currency） | 是 | 主单位十进制，≥0，最多 16 位整数 8 位小数 | 优惠前商品总额 |
| `discount_amount` / discountAmount | MONEY | 是 | 同上 | 无折扣传 `0` |
| `net_amount` / netAmount | MONEY | 是 | 同上 | 允许 0（全额折扣是否放行交给规则）；Risk 不校验 净额 = 商品 − 折扣 |
| `industry_code` / industryCode | STRING | 是 | `[A-Z][A-Z0-9_]{0,63}` | |
| `mcc_code` / mccCode | STRING | 是 | `[0-9]{4}` | |
| `line_count` / lineCount | NUMBER（SCALAR） | 是 | `[1-9][0-9]{0,2}` | Commerce 限制 1～100 |

**响应：**`{"evaluationId":"…","result":"ALLOW|REVIEW|DENY|FAILED|TIMED_OUT","outputs":{}}`，结账 `outputs` 恒为空。错误：缺字段/格式 422（带字段编码），未知合同版本或语义校验 400，重放内容不一致或并发首评 409，无生效发布 503 `RISK_DECISION_NOT_CONFIGURED`。FAILED/TIMED_OUT 为 HTTP 200 且已落库，重放返回同一结果。

### 2.2 Commerce（Client API `POST /checkouts`：路由与请求不变）

| Risk 返回 | Commerce 处理 | Client 收到 | Risk 调用次数 |
|---|---|---|---|
| 200 ALLOW | 冻结评估 ID，继续创建 | 201 | 1 |
| 200 REVIEW / DENY | 不落订单；WARN 日志记 orderId、clientId、evaluationId、结论 | 422 `CHECKOUT_NOT_ELIGIBLE`（已有） | 1 |
| 200 FAILED / TIMED_OUT | 不重试（重放结果相同），失败关闭 | 503 `DEPENDENCY_UNAVAILABLE` | 1 |
| 超时、连接失败、5xx（含 503 未配置）、409 | 同一请求体重试一次，第二次按本表判定；仍属此类 | 503 `DEPENDENCY_UNAVAILABLE` | 2 |
| 400 / 422 / 响应不合协议（结论未知、评估 ID 非法） | 视为对接缺陷，不重试；不能把 Risk 的 400 透给 Client | 502 `UPSTREAM_PROTOCOL_ERROR`（已有） | 1 |

- **503 错误码：**采用 Client API 合同第 12 节定义的 `DEPENDENCY_UNAVAILABLE`（映射已存在、此前无人抛出）。现有 CCT 不可用路径返回 Foundation 的 `SERVICE_UNAVAILABLE`、不在合同表中，属既有问题，本次不改，记入遗留。
- **字段映射：**`businessKey`=orderId；clientId、consumerCountryCode、currency、netAmount 取 `InitializationRequest`；entityNo、entitySourceType、localPartnerId、industryCode、mccCode 取 CCT `CommercialFacts`；merchandiseAmount、discountAmount 为 `InitializationRequest` 新增字段（订单上已算好）；lineCount = 明细数。金额 `stripTrailingZeros().toPlainString()`（同 Payment）。
- **超时：**服务组 `risk-service`，连接 2 秒、读 1 秒；传输层自动重试已全局关闭，重试次数完全由端口实现控制。最坏耗时约 2 秒 + 连接时间。
- **日志：**只记 orderId、clientId、evaluationId、结论、异常类名；不记请求体与 Problem 原文。

### 2.3 Operations BFF：无接口变化（`CreateExecutionReleaseRequestBody` 注释 1 行）。BFF 不按风控点区分，回执校验通用。

## 3. 输入合同 v4 → v5 的兼容

- **描述符：**`CHECKOUT_PREPARE` 改为 v5、`runtimeSupported=true`，并新增：
  - `readableContracts`（结账：v5、v4）——规则集、流程按版本读取时接受 v4，修复“切到 v5 后旧数据读不出”（`DecisionRuleSetAssembler:143,168`、`DecisionFlowGraphAssembler:46`、`DecisionFlowRow:92`）；
  - `featureVariablesSupported`（支付 true、结账 false）——替换 `VariableDefinitionValidator:56` 中以 `runtimeSupported` 判断能否创建实时累计/名单变量的写法；变量创作上下文对结账不再列出这两类来源；
  - `runtimePointVersionNo`（支付 1、结账 100）——替换 `CreateExecutionReleaseProcessor` 写死的 1。
- **旧版描述符：**新增 `legacyCheckoutPrepare()`（v4、非运行期，不进入 `ALL`）。旧候选发布、旧格式（CONTEXT）规则与流程 schemaVersion 1 等约 10 处写死 `checkoutPrepare()` 的位置改用它（`RuleAstValidatorImpl:49`、`DecisionRuleSetAssembler:710,893`、`RuleActionConfigurationCatalog:46`、`DecisionFlowConditionNormalizer:180`、`DecisionFlowJsonCodec:108`、`DecisionReleaseAssembler`、`DecisionReleaseStaticValidatorImpl`），行为与今天完全相同。
- **门禁：**
  - 创建执行发布：流程版本合同必须等于当前合同，否则 400“决策流版本使用旧输入合同，请重新保存后发布”；
  - 创建变量：旧合同（v4）拒绝新建（放在 `CreateVariableProcessor`，不放在校验器，以免读取旧变量时失败）。
- **D3 的连带效果：**结账新规则集版本只接受统一变量格式、不允许附加动作；结账创作目录不再含冷却/标签动作。
- **运营迁移（对 Q3 的补充）：**mor-test 现有结账规则集与流程都是 v4。要激活结账规则，运营需把用到的规则集另存为 v5 新版本（统一变量格式、无附加动作），再重新保存流程，然后创建执行发布。**不用 DML 改写历史规则集、流程行**（会改动已冻结内容，与“v4 只读”不符）。
- **Hash 影响：**规则集版本 Hash 不含合同版本与变量来源，流程版本无 Hash，执行发布 Hash 只含变量编码与 Feature 版本 —— 变量迁移不使任何已冻结 Hash 失效。

## 4. 数据库

### 4.1 Schema DDL（[db/ddl.sql](./db/ddl.sql)，目标库 `mor_risk`）

- `DROP` + `CREATE t_checkout_input_snapshot`：旧结构 41 列、无代码读写、test 与本机均 0 行；新结构 17 列（订单 ID、Client、主体、来源、Local Partner、国家、币种、三个金额、行业、MCC、行数、稳定输入摘要、创建时间），唯一索引 `evaluation_id`、`order_id`，普通索引 `(client_id, create_time)`、`(entity_no, create_time)`。表总数仍为 85。
- **不新增结账评估表：**通用 `t_decision_evaluation` + 本快照表即对应支付的“评估 + `t_transaction_risk_assessment`”。结账没有受限字段和附加输出，因此没有受限摘要、认证要求列。
- PDManer 模型、完整 Schema 同步修改。

### 4.2 业务 DML（[db/dml.sql](./db/dml.sql)，一个事务，带预检）

| 行 | 内容 |
|---|---|
| 91001 | 迁到 v5，来源字段 `country_code` → `consumer_country_code` |
| 91003～91009 | 迁到 v5（91005～91008 必须同批：金额变量的币种变量须同合同版本） |
| 91010 `INPUT_CLIENT_ID` | 迁到 v5，并开放规则与流程条件用途 |
| 91002 `INPUT_LOCALE` | `DISABLED`，留在 v4 |
| 91018～91020（新增） | `INPUT_ENTITY_NO`、`INPUT_LOCAL_PARTNER_ID`、`INPUT_LINE_COUNT` |
| 220001（新增） | 结账运行期点版本：FROZEN、`ORDER_PREPARATION` v5、`version_no=100`、超时 500 ms、失败关闭 |

- **`version_no=100`：**mor-test 结账已有旧 companion 点版本 1～33（2026-10-16 只读查询），本机没有；Hash 含版本号，所以取各环境都未用的固定值。
- **Hash：**`content_hash` 须在实现描述符后由代码计算（`RiskDecisionCanonicalizerImpl.decisionPointVersion(ExecutionReleaseContent.pointVersion(descriptor, 100))`），阶段 4 填入；“创建结账执行发布”自动测试会间接校验。
- 同一内容并入 `risk-compliance-initial-data.sql`（空库直接为 v5 形态），与增量文件二选一。
- **执行顺序：**ddl → dml，均可在旧 Risk 运行时执行（旧代码不读写快照表；旧代码读 v5 变量会因合同未登记而失败 —— 所以 **DML 必须与新镜像同一窗口执行**，先 DML 后立即换镜像，见第 8 节）。

### 4.3 注册 DML：0（不新增路由、权限、菜单）。

## 5. Risk 实现清单

| 类别 | 内容 |
|---|---|
| 运行期抽象 | 新接口 `RuntimeRiskInput`（`contractValues`、`subjectKey`、`amount`、`currency`）；`PaymentRiskInput` 实现它；`DecisionEngine.evaluate`、`FeatureValueReader.read` 改为接收该接口；`PaymentRiskFacts` 中通用部分（`Run`、Feature 结果、规则/流程轨迹、评估行构造）移到 `RuntimeEvaluationFacts` |
| 执行复用 | 新增 `RuntimeEvaluationExecutor`：合并支付评估器与影子回放中重复的“读快照 → Feature → 引擎 → 计时 → FAILED/TIMED_OUT” |
| 评估器扩展 | `DecisionPointEvaluator` 新增 `replaySample(primaryEvaluationId)`（冻结输入 + 输出）；影子回放按风控点分派；“输出变化”改为比较输出整体（对支付等价，结账恒为否） |
| 结账新类 | `CheckoutPrepareEvaluator`；`CheckoutRiskInput`（含 PARTNER 校验）；`CheckoutRiskFacts`（稳定摘要、快照构造，不写累计事件）；`CheckoutRiskAssessmentContext`；`CheckoutInputSnapshot` + Mapper/XML + Repository(+Impl)；`CheckoutRiskAssessmentService`(+Impl，`TransactionTemplate`) |
| risk-api | `CheckoutPrepareRiskInput`（`POINT_CODE`、`CONTRACT_VERSION=5`、`toRequest`） |
| 描述符与门禁 | 第 3 节 |
| 不改 | `EvaluateDecisionProcessor`、`EvaluationReader`（按登记分派）、日志配置（`/risk-decisions` 已只记元数据） |

## 6. Commerce 实现清单

| 文件 | 改动 |
|---|---|
| `domain/customer/CustomerCheckoutModels` | `InitializationRequest` 增加 `merchandiseAmount`、`discountAmount` |
| `CreateCheckoutProcessor:137` | 构造时补两个金额 |
| `integration/risk/impl/CheckoutRiskPortImpl`（新） | 映射、一次重试、结论与错误转换（第 2.2 节） |
| `configuration/RiskClientConfiguration`（新） | `@ImportHttpServices(group="risk-service", types=RiskDecisionApi.class)` + strict 选项，同 `PaymentClientConfiguration` |
| `CustomerCheckoutIntegrationConfiguration:49-54` | 删除桩（缺少 Risk 客户端时启动失败，不会默认放行） |
| `application.yml` | `risk-service` 服务组（`lb://risk-service`，连接 2s / 读 1s） |
| `pom.xml`（父、web） | 依赖 `risk-api` |

## 7. Operations Web

| 页面 / 文件 | 改动 |
|---|---|
| 执行发布 `RiskExecutionReleases.tsx`、`risk/riskControl.ts` | `EXECUTION_POINTS` 加入 `CHECKOUT_PREPARE`（默认仍为支付）；结账无生效发布时警告追加“Commerce 创建结账会全部失败，请先激活基线发布”；“认证要求变化”指标只对有认证输出的点显示；“对新的支付评估生效”→“对该风控点新的评估生效” |
| `risk/riskPoints.ts` | 新增 `hasAuthenticationOutput(pointCode)`（执行发布、评估查询共用） |
| 决策流 `RiskDecisionFlows.tsx:50,89` | 提示改为按 `EXECUTION_POINTS` 判断：“保存不会立即生效，需在【执行发布】中发布并激活后对该风控点的评估生效。” |
| 附加动作 `risk/AdditionalActionEditor.tsx` | 目录无附加动作且没有已有动作：整块不显示；有已有动作（旧结账版本）：只保留删除，提示“该风控点运行期不支持附加动作，请移除后再保存” |
| 评估查询 `RiskEvaluations.tsx`、`risk/riskEvaluation.ts`、`riskEvaluationApi.ts` | 查询类型增加“订单 ID”（`type=order` → `CHECKOUT_PREPARE`）；输入表按风控点（支付 17 / 结账 12 / 未知点按原键）；业务身份标签按点（支付尝试 / 订单 / 业务身份）；结账不显示“认证要求”行与影子“认证要求变化”列 |

**结账输入表（12 行）：**Client、运营主体、主体来源、Local Partner（非 PARTNER 显示“未提供”）、消费者国家、币种、商品金额、折扣金额、净额、行业、MCC、明细行数。订单 ID 为业务身份，显示在结论卡片中。

**线框差异**（其余同 [评估查询 ui.md](../../20261015/operations-console-评估查询与解释/ui.md)）：

```text
│ [订单 ID ▾] [ 991016000000000123            ] [ 查询 ]   下拉：支付尝试 ID / 订单 ID / 评估 ID │
│ ● 拒绝   正式评估 · 评估 ID 2367…   风控点 结账准备（CHECKOUT_PREPARE）  订单 9910…0123       │
│ （无“认证要求”项）  发布 …  流程 CHECKOUT_FLOW v2  评估时间 …  耗时 …                         │
│ 输入：Client …  运营主体 AE001  主体来源 PARTNER  Local Partner 3001…  消费者国家 AE  币种 USD │
│       商品金额 120  折扣金额 20  净额 100  行业 GAMING  MCC 5816  明细行数 3                  │
│ 影子评估：评估 ID | 批次 | 结论 | 结论变化 | 严重程度 | 操作   （无“认证要求变化”列）          │
```

## 8. 测试与验收映射

| 系统 | 测试（真实 HTTP 入口） | 场景 | AC |
|---|---|---|---|
| Risk | `CheckoutRiskAssessmentApiAutoTest`（新） | 国家+行业+净额规则得到 ALLOW/REVIEW/DENY，评估、快照、轨迹落库、无 Feature；重放一致返回原结果（含 FAILED/TIMED_OUT）、不一致 409；缺字段/格式 422、未知字段 `locale` 422、v4 400、PARTNER 无 localPartnerId 400 且不写评估；无发布 503；超时 TIMED_OUT、常量损坏 FAILED | AC-1、AC-2 |
| Risk | 执行发布测试（新或扩展） | 结账创建/校验/激活（220001 Hash 吻合）；v4 流程版本创建被拒；影子回放结账评估、输出变化为否；被生效发布引用的结账规则集不可删 | AC-3 |
| Risk | 创作回归（改现有期望） | 目录只列 v5；v4 新建变量被拒；结账实时累计/名单变量被拒；v4 规则集、流程、旧候选可读；结账新规则集 v5、可冻结、拒绝附加动作 | AC-4 |
| Risk | `EvaluationQueryApiAutoTest`、`WebLoggingBoundaryAutoTest`（扩展） | 按评估 ID、按订单 ID 查询与解释（12 个输入）；请求体不进日志 | AC-6、AC-7 |
| Risk | 支付全量回归 | 引擎、Feature、影子签名变化 | — |
| Commerce | `CreateCheckoutRiskTest`（新）+ 现有 5 个创建测试默认放行 | 放行冻结评估 ID；审核/拒绝 422 不落单；超时后放行、两次超时 503；5xx/断连/409 重试；FAILED 只调 1 次 503；400/422/非法响应 502；字段映射（金额去尾零、无 localPartnerId）与 PARTNER 来源；拒绝时不创建 Customer | AC-5 |
| Web | `node --test` 单元 + 源码检查、`npm run build` | 订单 ID 查询往返、结账输入表、认证项按点隐藏、执行发布点列表与提示、附加动作区 | AC-3、AC-6 |
| 本机全链路（阶段 5） | Client API → Commerce → Risk；浏览器 → 评估查询、执行发布 | 默认放行基线 + 拒绝规则；拒绝/放行/Risk 停止三种结账 | AC-1～AC-7 |

测试基础设施：Risk 新增 `CheckoutRiskRequestBuilder`、`CheckoutRiskMocker`（超时按 220001 改写/恢复）、`CheckoutRiskTestMapper`（只清结账点）；支付与结账共享的影子批次等表由共用清理器处理；写死 v4 的测试 Mapper 改为传入合同版本。Commerce 新增仅测试用的 `MockRiskDecisionApi` 与 `CheckoutRiskMocker`（有序回复、延迟、故障、调用记录、未预期调用失败），`application-mock.yml` 增加 `risk-service`（读超时 500ms）。

## 9. 实施顺序、部署与恢复

1. **阶段 4 研发：**Risk（risk-api → 抽象重构 → 结账评估器 → 描述符门禁 → 测试）→ 计算 220001 Hash 定稿 DML → 本机 `mor_risk` 执行 ddl/dml（需单独授权，先备份）→ Risk 全量测试 → 安装 risk-api → Commerce → Web。
2. **阶段 5 本机：**新 Risk + Commerce + Web；经接口创建并激活“开始 → 放行”的结账基线发布（Q6），再验证拒绝规则；验收数据清理。
3. **阶段 6/7 mor-test（只 Risk）：**备份 → ddl（可先执行）→ **dml 与换镜像在同一窗口**（dml 后旧镜像读结账变量会失败，所以 dml 后立即换镜像）→ 回归与验收。test 无 Commerce，是否在 test 激活结账基线发布由你在阶段 6 决定（不激活不影响任何现有调用方）。
4. **恢复：**换回旧镜像前须先执行 dml 回滚（变量改回 v4、91002 恢复 ACTIVE、删除 91018～91020 与 220001；前提是没有 v5 规则集版本或结账执行发布引用它们，否则只能保持新镜像修复）；快照表可保留（旧代码不读写）。

## 10. 遗留与不在本期

- Commerce CCT 不可用路径返回 `SERVICE_UNAVAILABLE`（不在 Client API 合同表中），本次不改。
- 规则配置页冻结前检查比 Risk 严（运行期点 Risk 允许冻结无终态动作的规则），支付点已存在，另行处理。
- 结账累计事件与实时累计变量、审核人工流程、明细级规则、消费者引用：按需求不做项。

## 11. 阶段 4 实施偏离

### Web（Operations Console）

- 附加动作区在“目录无附加动作、但有已有动作”时只显示动作名与删除按钮，不再渲染作用对象/参数表单与“＋ 添加动作”，避免对不可保存的内容继续编辑；提示为“该风控点运行期不支持附加动作，请移除后再保存。”。
- 新增源码检查测试放在独立文件 `operations-console-web/src/risk/riskCheckoutRuntimeWiring.test.ts`，未按调研稿分散写入 `riskPageWiring.test.ts` / `riskEvaluationPage.test.ts`，便于按本需求定位；覆盖范围不变。
- 调研稿中的可选项未实施：执行发布详情抬头显示风控点中文名、静态检查 `TERMINAL_ACTION` 标签改为“终态动作与输出”、legacy 创作目录补注释。
- `RiskRuleConfiguration.tsx` 的冻结前检查（附加动作 > 0 不可冻结）保持原样，作为后续待办。
- 结账输入键按 Risk 合同定稿（camelCase 12 键），与 7 节一致，无偏离。

### Commerce

- 新增内部枚举 `domain/enums/RiskDecisionResult`（ALLOW/REVIEW/DENY/FAILED/TIMED_OUT），第 6 节清单未列出；用于结论解析与测试引用，避免散落字符串。未知结论按 502 处理。
- 评估 ID 除格式 `[1-9][0-9]{0,18}` 外，超出 BIGINT 范围（如 19 位 `9999…`）同样按 502 `UPSTREAM_PROTOCOL_ERROR` 处理，不抛未处理的数字转换异常。
- `paytech-commerce-test/pom.xml` 也显式声明 `risk-api`（测试 Mock 直接实现 `RiskDecisionApi`，与 `paytech-payment-api` 写法一致）；版本由父 pom 管理。
- 测试在第 8 节矩阵外补充：断连后放行、两次 409 → 503、评估 ID 为空 → 502；`CreateCheckoutSuccessTest.test_createCheckout` 增加冻结评估 ID 与调用 1 次的断言。
- 评审修复 1（无可用实例）：`lb://risk-service` 在服务发现中没有实例时，Spring Cloud 阻塞式负载均衡抛出 `IllegalStateException("No instances available for risk-service")`，Foundation 出站边界不转换。端口只识别这条原文（其他运行时异常原样传播），将其视为结果未知：重试一次后返回 503 `DEPENDENCY_UNAVAILABLE`。测试 `test_noInstance` 临时移除测试 SimpleDiscovery 中的 risk-service 实例（每例清理时恢复），走真实负载均衡无实例路径，Mock 收到 0 次调用。日志显示负载均衡两次报告无实例，端口重试一次后返回 503。
- 评审修复 2（非 Problem 5xx）：错误体不是 Problem 的 5xx（代理 HTML、空体）会被公共客户端解码为 `ErrorException(UPSTREAM_INVALID_RESPONSE)`，原因为 5xx 的 `RestClientResponseException`。端口对这种情况按第 2.2 节的 5xx 处理：重试一次，仍失败返回 503；其他无效响应仍返回 502，不重试。新增 `test_nonProblemServerErrorTwice`（503，2 次）和 `test_nonProblemServerErrorThenAllow`（201，2 次）。
- 删除生产桩后，Commerce 运行时始终使用真实 Risk 客户端：本机或其他环境若未注册 `risk-service` 实例，或 Risk 结账点无生效发布，创建结账一律返回 503 `DEPENDENCY_UNAVAILABLE`（失败关闭，不默认放行）；阶段 5 本机联调需先启动 Risk 并激活结账基线发布。

### Risk

| 编号 | 偏离 / 补充 | 原因与影响 |
|---|---|---|
| R1 | **规则与流程创作上下文只列当前合同的输入变量**：在 Owner 领域投影中过滤，即 `RuleAuthoringView.from`（由 `RuleAuthoringContextProcessor` 调用）与 `FlowAuthoringContextView.from`；`DecisionRuleSetWireConverter` 只做协议转换。第 5 节未列出 | 两处原先列出该点全部系统 INPUT 变量，切到 v5 后已停用的 v4 `INPUT_LOCALE` 仍会出现在目录中；支付只有 v1，不受影响 |
| R2 | **规则创作上下文对不支持附加动作的决策点不再返回冷却/打标签动作及其参数**：同样由 `RuleAuthoringView.from` 按 `additionalActionsSupported()` 决定 `configurationActions`，转换器照投影输出；支付点同样生效 | 原转换器无条件拼入仅配置动作，与 D3“结账创作目录不再含冷却/标签动作”不符；支付保存时本就拒绝附加动作，目录与保存规则从此一致。Operations Web 第 7 节“目录无附加动作则整块不显示”已覆盖 |
| R3 | 结账旧格式（非统一变量）规则集保存返回 `400 INVALID_REQUEST`（AST 入口“该决策点只接受统一变量格式”），不再返回 `LEGACY_AUTHORING_NOT_SUPPORTED` | 结账已是运行期点，与支付一致；规则集的 `LEGACY_AUTHORING_NOT_SUPPORTED` 分支对当前所有登记点已不可达 |
| R4 | `RuntimeEvaluationExecutor` 拆为 `execute(release, input, requestId)`（正式评估，读取执行内容的失败也记 FAILED 并记错误日志）与 `replay(snapshot, input, evaluationTime)`（影子，不记日志）；失败日志由“支付风控评估执行失败, paymentAttemptId=…”统一为“风控评估执行失败, requestId=PAYMENT_ATTEMPT_RISK:…/CHECKOUT_PREPARE:…” | 消除支付评估器与影子回放的重复；日志仍只含业务身份，不含输入原值 |
| R5 | 描述符以私有 `RuntimeCapabilities`（运行期、Feature 变量、点版本号）+ 可读合同版本列表实现；结账当前描述符 `descriptorCode` 为 `CHECKOUT_PREPARE_AUTHORING_V2`，旧描述符保持 `…_V1` | `descriptorCode` 无代码读取，仅作标识 |
| R6 | Feature 变量门禁的错误文案改为“实时累计与名单命中变量必须属于支持 Feature 计算的决策点” | 原文案“支持运行期”对结账不再准确 |
| R7 | 评估查询的结账场景放在新类 `CheckoutEvaluationQueryApiAutoTest`（含访问日志不记请求体与输入原值的断言），未扩展 `EvaluationQueryApiAutoTest`/`WebLoggingBoundaryAutoTest` | 结账夹具与支付不同；`/risk-decisions` 前缀的 metadata-only 已由 `WebLoggingBoundaryAutoTest` 通用覆盖 |
| R8 | 测试基础设施：抽出 `AbstractRuntimeRiskApiTest`；`PaymentRiskResponseChecker` 更名为 `EvaluationResponseChecker`；新增 `AbstractCheckoutRiskApiTest`、`CheckoutRiskRequestBuilder`、`CheckoutRiskTestMapper`、`CheckoutRiskMocker` | `CheckoutRiskTestMapper.deleteAll` 整体清理结账点评估族、全部结账快照、结账执行发布及其审计、候选为结账发布的影子批次与补偿任务，配置只清理 `T_CHK` 前缀的流程/规则集/变量。与支付相同：**此后在 mor_risk 跑 Risk 自动测试会删除人工激活的结账基线发布**（阶段 5 需在测试之后再激活） |
| R9 | 测试夹具合同版本参数化：统一变量格式的规则集夹具默认 v5、旧格式 v4；流程夹具默认 v4（可指定 v5）；原先以 `5` 表示“合同不符/损坏”的用例改用未登记版本 `99`；`VariableBuilder` 的输入别名改为 v5（`consumer_country_code`） | v5 已是真实合同 |
| R10 | 删除/改写已不适用的旧用例：规则集保存中“动作主体变量不存在/不能作动作参数”“主体标签未登记”改为“结账不支持附加动作（400）”；冻结用例“统一变量格式仅支持配置”改为“统一变量草稿可冻结”；`test_normalFlow_completeConfiguration` 去掉 locale 条件与冷却动作 | 对应 D2/D3 的预期行为变化 |
| R12 | **业务身份与标识字段的 BIGINT 上限校验放在共用合同 `EvaluationInputContract`**：新增共享常量 `POSITIVE_ID`，按它登记的业务身份与字段另须不超过 `Long.MAX_VALUE`，超限与格式不符一样返回 422（字段级违规）；按业务身份查询经 `validBusinessKey` 返回 400。支付与结账评估器都改用该常量，支付因此同样修复（原先 19 位超限值会在 `Long.parseLong` 处变成 500） | 评审意见 3；已补测试：评估接口订单/支付尝试/Client/Local Partner/风控 Action 超限返回 422，按业务身份查询超限返回 400，`Long.MAX_VALUE` 本身仍合法（404） |
| R11 | 220001 `content_hash` = `7f730e195b2c465db93671fbba3c8fb51e596c9b82256f7098a5416c59b04a45`（同方法算得支付 220002 与初始化数据一致，用作校验）；已填入 `db/dml.sql` 与 `risk-compliance-initial-data.sql`，“创建结账执行发布”自动测试已间接验证 | — |
