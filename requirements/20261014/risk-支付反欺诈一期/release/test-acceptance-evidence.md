# mor-test 业务验收证据（经临时代理，真实 HTTP）

后缀 26754378


## 1. 未配置：没有生效发布

- ✅ 评估 → 503：`POST /risk-decisions/v1/evaluations` → **503** `{"type":"about:blank","title":"Service Unavailable","detail":"风控点尚未配置生效发布","code":"RISK_DECISION_NOT_CONFIGURED","requestId":"stage5-37dfa6c7-1070-44e4-8d3a-9c0297e64cc6","timestamp":"2026-10-15T06:23:34.840786Z"}`

## 2. 配置：拒绝原因、名单、变量、规则集、决策流

- ✅ 创建拒绝原因：`POST /decision-dictionaries/v1/rejection-reasons` → **200** `{"changedBy":"70001","changedTime":"2026-10-15T06:23:34.895601Z","code":"ACCEPT_REASON_26754378","dictionaryType":"REJECTION_REASON","status":"ACTIVE","version":"1"}`
- ✅ 创建卡指纹黑名单：`POST /risk-lists/v1/lists` → **200** `{"active":true,"listCode":"ACCEPT_FP_26754378","listId":"236373419912957952","listName":"验收卡指纹黑名单","purpose":"BLOCK","subjectType":"CARD_FINGERPRINT"}`
- ✅ 加入名单成员：`POST /risk-lists/v1/lists/ACCEPT_FP_26754378/entries` → **200** `{"createTime":"2026-10-15T06:23:34.924784Z","createdBy":"70001","deactivatedBy":null,"deactivatedReason":null,"effectiveFrom":"2026-10-15T06:23:34.924784Z","effectiveTo":null,"entryId":"236373419975872512","listId":"236373419912957952","maskedSubjectKey":"fp_blo******","reason":"验收加入","sourceType":"MANUAL","status":"ACTIVE","subjectType":"CARD_FINGERPRINT"}`
- ✅ 创建名单命中变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_FP_BLOCKED_26754378","variableName":"ACC_FP_BLOCKED_26754378","sourceType":"LIST","valueType":"BOOLEAN","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":null,"currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValue":null,"…`
- ✅ 创建卡指纹 1 小时评估次数变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_FP_COUNT_1H_26754378","variableName":"ACC_FP_COUNT_1H_26754378","sourceType":"VELOCITY","valueType":"NUMBER","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":"SCALAR","currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValu…`
- ✅ 创建规则集 ACC_BLOCK_26754378：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236373420525326336","pointVersionId":null,"ruleSetCode":"ACC_BLOCK_26754378","versionNo":1,"status":"DRAFT","contentHash":"e6c4be006b2d739944362e12582444212786f80303fd2f3da697c7518a580060","createdBy":"70001","createTime":"2026-10-15T06:23:35.043523Z"}`
- ✅ 冻结规则集 ACC_BLOCK_26754378：`POST /decision-rule-sets/v1/rule-sets/236373420525326336/freeze` → **200** `{"ruleSetVersionId":"236373420525326336","pointVersionId":null,"ruleSetCode":"ACC_BLOCK_26754378","versionNo":1,"status":"FROZEN","contentHash":"e6c4be006b2d739944362e12582444212786f80303fd2f3da697c7518a580060","frozenBy":"70001","frozenTime":"2026-10-15T06:23:35.101053Z"}`
- ✅ 创建规则集 ACC_AUTH_26754378：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236373420827316224","pointVersionId":null,"ruleSetCode":"ACC_AUTH_26754378","versionNo":1,"status":"DRAFT","contentHash":"3b0fb7ff08c19fdc3d4daac25be369c45d704d6dad64072107279daf8916a120","createdBy":"70001","createTime":"2026-10-15T06:23:35.124657Z"}`
- ✅ 冻结规则集 ACC_AUTH_26754378：`POST /decision-rule-sets/v1/rule-sets/236373420827316224/freeze` → **200** `{"ruleSetVersionId":"236373420827316224","pointVersionId":null,"ruleSetCode":"ACC_AUTH_26754378","versionNo":1,"status":"FROZEN","contentHash":"3b0fb7ff08c19fdc3d4daac25be369c45d704d6dad64072107279daf8916a120","frozenBy":"70001","frozenTime":"2026-10-15T06:23:35.148204Z"}`
- ✅ 保存决策流（含“放行（需要 3DS）”）：`POST /decision-flows/v1/flows` → **200** `{"flowId":"236373421372575744","flowVersionId":"236373421372575745","savedBy":"70001","savedTime":"2026-10-15T06:23:35.261908Z","version":"1","versionNo":1}`
- ✅ 流程详情：放行结束节点输出：`GET /decision-flows/v1/flows/236373421372575744` → **200** `{"flowId":"236373421372575744","pointCode":"PAYMENT_ATTEMPT_RISK","flowCode":"ACC_FLOW_26754378","flowStatus":"CONFIGURABLE","currentFlowVersionId":"236373421372575745","currentVersionNo":1,"version":"1","flowVersionId":"236373421372575745","versionNo":1,"flowName":"Checkout flow","inputContractCode":"PAYMENT_ATTEMPT","inputContractVersion":1,"createdBy":"70001","createTime":"2026-10-15T06:23:35.2…`
  - 节点 allow 输出 {"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"}
  - 节点 allow_3ds 输出 {"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"}

## 3. 执行发布：创建 → 校验 → 生效

- ✅ 创建执行发布：`POST /decision-releases/v1/executions` → **200** `{"activatedTime":null,"checks":[],"contentHash":"552f225e177a542ca865fe1e362e34e8be271d78828a984e98789fbe91f628cb","environment":"TEST","flowVersionId":"236373421372575745","pointCode":"PAYMENT_ATTEMPT_RISK","releaseId":"236373421741674496","releaseNo":"11","shadowRun":null,"status":"DRAFT","supersededTime":null,"version":1}`
- ✅ 静态校验：`POST /decision-releases/v1/executions/236373421741674496/validate` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - ✅ 校验后为 SHADOWING
- ✅ 生效：`POST /decision-releases/v1/executions/236373421741674496/activate` → **200** `{"activatedTime":"2026-10-15T06:23:35.461524Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 生效后为 ACTIVE

## 4. 评估四种结果

- ✅ 小额 → 放行、无需认证：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373422404374528","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 小额 → 放行、无需认证：result=ALLOW
  - ✅ 小额 → 放行、无需认证：认证要求=NOT_REQUIRED
- ✅ 1500 → 放行、需要 3DS：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373422718947328","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"},"result":"ALLOW"}`
  - ✅ 1500 → 放行、需要 3DS：result=ALLOW
  - ✅ 1500 → 放行、需要 3DS：认证要求=THREE_DS_REQUIRED
- ✅ 6000 → 审核：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373422878330880","outputs":{},"result":"REVIEW"}`
  - ✅ 6000 → 审核：result=REVIEW
  - ✅ 6000 → 审核：认证要求=null
- ✅ 黑名单卡指纹 → 拒绝：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373423008354304","outputs":{},"result":"DENY"}`
  - ✅ 黑名单卡指纹 → 拒绝：result=DENY
  - ✅ 黑名单卡指纹 → 拒绝：认证要求=null
- ✅ 同一卡第 1 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373423121600512","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 同一卡第 1 次（含本次 >3 拒绝）：result=ALLOW
  - ✅ 同一卡第 1 次（含本次 >3 拒绝）：认证要求=NOT_REQUIRED
- ✅ 同一卡第 2 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373423247429632","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 同一卡第 2 次（含本次 >3 拒绝）：result=ALLOW
  - ✅ 同一卡第 2 次（含本次 >3 拒绝）：认证要求=NOT_REQUIRED
- ✅ 同一卡第 3 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373423381647360","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 同一卡第 3 次（含本次 >3 拒绝）：result=ALLOW
  - ✅ 同一卡第 3 次（含本次 >3 拒绝）：认证要求=NOT_REQUIRED
- ✅ 同一卡第 4 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373423507476480","outputs":{},"result":"DENY"}`
  - ✅ 同一卡第 4 次（含本次 >3 拒绝）：result=DENY
  - ✅ 同一卡第 4 次（含本次 >3 拒绝）：认证要求=null

## 5. 重放与参数

- ✅ 同一请求重放 → 原结果：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373422404374528","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 重放返回同一 evaluationId
- ✅ 同一支付尝试换风控 Action → 409：`POST /risk-decisions/v1/evaluations` → **409** `{"type":"about:blank","title":"Conflict","detail":"支付尝试已有不同内容的风控评估: 556334380094061","code":"CONCURRENT_REQUEST","requestId":"stage5-75d43a59-5069-4a8d-92ff-9f789becf24f","timestamp":"2026-10-15T06:23:35.789217Z"}`
- ✅ 金额格式非法 → 422：`POST /risk-decisions/v1/evaluations` → **422** `{"type":"about:blank","title":"Unprocessable Content","detail":"请求字段校验未通过","code":"VALIDATION_ERROR","requestId":"stage5-a2e1b0a8-f8f8-41bc-b879-627dc7270544","timestamp":"2026-10-15T06:23:35.792110Z","errors":[{"field":"inputs.amount","message":"格式无效"}]}`
- ✅ 未登记风控点 → 400：`POST /risk-decisions/v1/evaluations` → **400** `{"type":"about:blank","title":"Bad Request","detail":"风控点或输入合同版本未登记: UNKNOWN_POINT","code":"INVALID_REQUEST","requestId":"stage5-fb6e563e-4288-4996-8d47-aa2d55553f95","timestamp":"2026-10-15T06:23:35.794466Z"}`

## 6. 第二个发布：影子运行后替代

- ✅ 创建规则集 ACC_AUTH2_26754378：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236373423645888512","pointVersionId":null,"ruleSetCode":"ACC_AUTH2_26754378","versionNo":1,"status":"DRAFT","contentHash":"7c57a8f22a741e56beed59cf940a793104a570f943a14ec8aa922b10c5318433","createdBy":"70001","createTime":"2026-10-15T06:23:35.800447Z"}`
- ✅ 冻结规则集 ACC_AUTH2_26754378：`POST /decision-rule-sets/v1/rule-sets/236373423645888512/freeze` → **200** `{"ruleSetVersionId":"236373423645888512","pointVersionId":null,"ruleSetCode":"ACC_AUTH2_26754378","versionNo":1,"status":"FROZEN","contentHash":"7c57a8f22a741e56beed59cf940a793104a570f943a14ec8aa922b10c5318433","frozenBy":"70001","frozenTime":"2026-10-15T06:23:35.818740Z"}`
- ✅ 保存新流程版本（3DS 门槛降为 50）：`POST /decision-flows/v1/flows` → **200** `{"flowId":"236373423805272064","flowVersionId":"236373423805272065","savedBy":"70001","savedTime":"2026-10-15T06:23:35.841123Z","version":"1","versionNo":1}`
- ✅ 创建执行发布：`POST /decision-releases/v1/executions` → **200** `{"activatedTime":null,"checks":[],"contentHash":"d431366ff095b0c533f3ce6e38ebd88cd4fdf6896eabf457fa4755574fc96bc2","environment":"TEST","flowVersionId":"236373423805272065","pointCode":"PAYMENT_ATTEMPT_RISK","releaseId":"236373423851409408","releaseNo":"12","shadowRun":null,"status":"DRAFT","supersededTime":null,"version":1}`
- ✅ 静态校验：`POST /decision-releases/v1/executions/236373423851409408/validate` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - ✅ 校验后为 SHADOWING
- ✅ 启动影子运行（样本 20）：`POST /decision-releases/v1/executions/236373423851409408/shadow-runs` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - 影子运行结果 {"actionChangeCount":4,"allowToDenyCount":0,"allowToReviewCount":0,"baselineReleaseId":"236373421741674496","checkStatus":"PASSED","denyToAllowCount":0,"failedCount":0,"featureMissingCount":0,"p95Millis":2,"p99Millis":2,"sampleCount":8,"sampleTarget":20,"shadowRunId":"236373424069513216","status":"COMPLETED","timedOutCount":0}
  - ✅ 影子运行完成
- ✅ 生效：`POST /decision-releases/v1/executions/236373423851409408/activate` → **200** `{"activatedTime":"2026-10-15T06:23:36.449562Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 生效后为 ACTIVE
- ✅ 原发布状态：`GET /decision-releases/v1/executions/236373421741674496` → **200** `{"activatedTime":"2026-10-15T06:23:35.461524Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 原发布被替代
- ✅ 新发布生效后 100 → 放行、需要 3DS：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236373426493820928","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"},"result":"ALLOW"}`
  - ✅ 新发布生效后 100 → 放行、需要 3DS：result=ALLOW
  - ✅ 新发布生效后 100 → 放行、需要 3DS：认证要求=THREE_DS_REQUIRED
  - release1=236373421741674496 release2=236373423851409408

## 结论

全部检查通过。
