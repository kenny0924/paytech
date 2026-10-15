# 评估查询阶段 5 本机验收证据（打包 jar，真实 HTTP）

后缀 52852447


## 1. 配置并生效（带复用节点的流程）

- ✅ 创建拒绝原因：`POST /decision-dictionaries/v1/rejection-reasons` → **200** `{"changedBy":"70001","changedTime":"2026-10-15T18:54:49.071487Z","code":"ACCEPT_REASON_52852447","dictionaryType":"REJECTION_REASON","status":"ACTIVE","version":"1"}`
- ✅ 创建 IP 黑名单：`POST /risk-lists/v1/lists` → **200** `{"active":true,"listCode":"ACCEPT_IP_52852447","listId":"236562474881675264","listName":"验收 IP 黑名单","purpose":"BLOCK","subjectType":"IP_ADDRESS"}`
- ✅ 加入名单成员：`POST /risk-lists/v1/lists/ACCEPT_IP_52852447/entries` → **200** `{"createTime":"2026-10-15T18:54:49.147253Z","createdBy":"70001","deactivatedBy":null,"deactivatedReason":null,"effectiveFrom":"2026-10-15T18:54:49.147253Z","effectiveTo":null,"entryId":"236562474994921472","listId":"236562474881675264","maskedSubjectKey":"198.51.*","reason":"验收加入","sourceType":"MANUAL","status":"ACTIVE","subjectType":"IP_ADDRESS"}`
- ✅ 创建名单命中变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_IP_BLOCKED_52852447","variableName":"ACC_IP_BLOCKED_52852447","sourceType":"LIST","valueType":"BOOLEAN","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":null,"currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValue":null,"…`
- ✅ 创建卡指纹 1 小时评估次数变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_FP_COUNT_1H_52852447","variableName":"ACC_FP_COUNT_1H_52852447","sourceType":"VELOCITY","valueType":"NUMBER","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":"SCALAR","currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValu…`
- ✅ 创建常量：卡次数上限 3：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_FP_LIMIT_52852447","variableName":"ACC_FP_LIMIT_52852447","sourceType":"CONSTANT","valueType":"NUMBER","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":"SCALAR","currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValue":"3.…`
- ✅ 创建规则集 ACC_BLOCK_52852447：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236562477314371584","pointVersionId":null,"ruleSetCode":"ACC_BLOCK_52852447","versionNo":1,"status":"DRAFT","contentHash":"fb6eeff6984219177f2fba46d215bfde784722b8dadafb2125bff098e14f5de6","createdBy":"70001","createTime":"2026-10-15T18:54:49.639705Z"}`
- ✅ 冻结规则集 ACC_BLOCK_52852447：`POST /decision-rule-sets/v1/rule-sets/236562477314371584/freeze` → **200** `{"ruleSetVersionId":"236562477314371584","pointVersionId":null,"ruleSetCode":"ACC_BLOCK_52852447","versionNo":1,"status":"FROZEN","contentHash":"fb6eeff6984219177f2fba46d215bfde784722b8dadafb2125bff098e14f5de6","frozenBy":"70001","frozenTime":"2026-10-15T18:54:49.788453Z"}`
- ✅ 创建规则集 ACC_AUTH_52852447：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236562477960294400","pointVersionId":null,"ruleSetCode":"ACC_AUTH_52852447","versionNo":1,"status":"DRAFT","contentHash":"462588231fa1b8c231f76b91241e52faa17443ea38d0c45022720f5b4463bf2d","createdBy":"70001","createTime":"2026-10-15T18:54:49.856324Z"}`
- ✅ 冻结规则集 ACC_AUTH_52852447：`POST /decision-rule-sets/v1/rule-sets/236562477960294400/freeze` → **200** `{"ruleSetVersionId":"236562477960294400","pointVersionId":null,"ruleSetCode":"ACC_AUTH_52852447","versionNo":1,"status":"FROZEN","contentHash":"462588231fa1b8c231f76b91241e52faa17443ea38d0c45022720f5b4463bf2d","frozenBy":"70001","frozenTime":"2026-10-15T18:54:49.881147Z"}`
- ✅ 保存决策流（拦截 → 认证 → 再次拦截复用 → 放行）：`POST /decision-flows/v1/flows` → **200** `{"flowId":"236562478681714688","flowVersionId":"236562478681714689","savedBy":"70001","savedTime":"2026-10-15T18:54:50.036837Z","version":"1","versionNo":1}`
- ✅ 创建执行发布：`POST /decision-releases/v1/executions` → **200** `{"activatedTime":null,"checks":[],"contentHash":"9693e80be82025dd2423811085719ca187ddc3867afa442128a25e043b7fb70c","environment":"TEST","flowVersionId":"236562478681714689","pointCode":"PAYMENT_ATTEMPT_RISK","releaseId":"236562478857875456","releaseNo":"1","shadowRun":null,"status":"DRAFT","supersededTime":null,"version":1}`
- ✅ 静态校验：`POST /decision-releases/v1/executions/236562478857875456/validate` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - ✅ 校验后为 SHADOWING
- ✅ 生效：`POST /decision-releases/v1/executions/236562478857875456/activate` → **200** `{"activatedTime":"2026-10-15T18:54:50.268263Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 生效后为 ACTIVE

## 2. 四种结论

- ✅ 100 → 放行、无需认证：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236562479940005888","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 100 → 放行、无需认证：result=ALLOW
  - ✅ 100 → 放行、无需认证：认证要求=NOT_REQUIRED
- ✅ 1500 → 放行、需要 3DS：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236562480162304000","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"},"result":"ALLOW"}`
  - ✅ 1500 → 放行、需要 3DS：result=ALLOW
  - ✅ 1500 → 放行、需要 3DS：认证要求=THREE_DS_REQUIRED
- ✅ 6000 → 审核：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236562480342659072","outputs":{},"result":"REVIEW"}`
  - ✅ 6000 → 审核：result=REVIEW
  - ✅ 6000 → 审核：认证要求=null
- ✅ 黑名单 IP → 拒绝：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236562480531402752","outputs":{},"result":"DENY"}`
  - ✅ 黑名单 IP → 拒绝：result=DENY
  - ✅ 黑名单 IP → 拒绝：认证要求=null

## 3. 查询结论（RCO-I06）

- ✅ 按 ID 查放行评估：`GET /risk-decisions/v1/evaluations/236562479940005888` → **200** `{"baselineEvaluationId":null,"businessKey":"145615362624206","elapsedMillis":37,"environment":"TEST","evaluationId":"236562479940005888","evaluationTime":"2026-10-15T18:54:50.299107Z","failureCode":null,"flowCode":"ACC_FLOW_52852447","flowId":"236562478681714688","flowVersionId":"236562478681714689","flowVersionNo":1,"mode":"PRIMARY","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"pathAva…`
  - ✅ result=ALLOW、PRIMARY、有路径
  - ✅ 认证要求 NOT_REQUIRED
  - ✅ 流程坐标
- ✅ 按支付尝试查：`GET /risk-decisions/v1/evaluations?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=145615362624206` → **200** `{"baselineEvaluationId":null,"businessKey":"145615362624206","elapsedMillis":37,"environment":"TEST","evaluationId":"236562479940005888","evaluationTime":"2026-10-15T18:54:50.299107Z","failureCode":null,"flowCode":"ACC_FLOW_52852447","flowId":"236562478681714688","flowVersionId":"236562478681714689","flowVersionNo":1,"mode":"PRIMARY","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"pathAva…`
  - ✅ 按支付尝试与按 ID 一致
- ✅ 按 ID 查拒绝评估：`GET /risk-decisions/v1/evaluations/236562480531402752` → **200** `{"baselineEvaluationId":null,"businessKey":"582853780504042","elapsedMillis":27,"environment":"TEST","evaluationId":"236562480531402752","evaluationTime":"2026-10-15T18:54:50.449859Z","failureCode":null,"flowCode":"ACC_FLOW_52852447","flowId":"236562478681714688","flowVersionId":"236562478681714689","flowVersionNo":1,"mode":"PRIMARY","outputs":{},"pathAvailable":true,"pointCode":"PAYMENT_ATTEMPT_R…`
  - ✅ 拒绝原因码与文案来自结束节点
- ✅ 按 ID 查 3DS 评估：`GET /risk-decisions/v1/evaluations/236562480162304000` → **200** `{"baselineEvaluationId":null,"businessKey":"177254604196857","elapsedMillis":26,"environment":"TEST","evaluationId":"236562480162304000","evaluationTime":"2026-10-15T18:54:50.362624Z","failureCode":null,"flowCode":"ACC_FLOW_52852447","flowId":"236562478681714688","flowVersionId":"236562478681714689","flowVersionNo":1,"mode":"PRIMARY","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"},"pa…`
  - ✅ 认证要求 THREE_DS_REQUIRED
- ✅ 按 ID 查审核评估：`GET /risk-decisions/v1/evaluations/236562480342659072` → **200** `{"baselineEvaluationId":null,"businessKey":"720934690901927","elapsedMillis":27,"environment":"TEST","evaluationId":"236562480342659072","evaluationTime":"2026-10-15T18:54:50.405496Z","failureCode":null,"flowCode":"ACC_FLOW_52852447","flowId":"236562478681714688","flowVersionId":"236562478681714689","flowVersionNo":1,"mode":"PRIMARY","outputs":{},"pathAvailable":true,"pointCode":"PAYMENT_ATTEMPT_R…`
  - ✅ 审核无输出

## 4. 评估解释（RCO-I10）

- ✅ 放行评估的解释：`GET /risk-decisions/v1/evaluations/236562479940005888/explanation` → **200** `{"evaluationId":"236562479940005888","inputs":{"paymentIntentId":"872645715386662","riskActionId":"396141636643061","orderId":"291501910546351","clientId":"800001","entityNo":"AE001","paymentMethodCode":"CARD_VISA","captureMode":"AUTOMATIC","amount":"100","currency":"USD","industryCode":"DIGITAL_GOODS","mccCode":"5816","consumerCountryCode":"AE","consumerReference":"consumer-001","clientIpAddress"…`
  - ✅ 路径 start→block→auth→recheck→allow
  - ✅ recheck 复用拦截结果
  - ✅ 执行过 4 条规则
  - ✅ 输入含 IP 原值
  - ✅ 常量 3 标注 CURRENT
  - ✅ 卡次数 1（含本次）EVALUATION
- ✅ 拒绝评估的解释：`GET /risk-decisions/v1/evaluations/236562480531402752/explanation` → **200** `{"evaluationId":"236562480531402752","inputs":{"paymentIntentId":"397937564734204","riskActionId":"719928176924024","orderId":"177694994607343","clientId":"800001","entityNo":"AE001","paymentMethodCode":"CARD_VISA","captureMode":"AUTOMATIC","amount":"100","currency":"USD","industryCode":"DIGITAL_GOODS","mccCode":"5816","consumerCountryCode":"AE","consumerReference":"consumer-001","clientIpAddress"…`
  - ✅ 拒绝路径 start→block→deny
  - ✅ BLOCKED_IP 终态命中
  - 放行解释节选：[{"decision":null,"exitEdgeKey":"enter","exitEdgeName":"进入","matched":null,"nodeKey":"start","nodeName":"开始","nodeType":"START","reused":null,"ruleSetCode":null,"ruleSetVersionId":null,"ruleSetVersionNo":null,"stepNo":1},{"decision":null,"exitEdgeKey":"to_auth","exitEdgeName":"其他","matched":false,"nodeKey":"block","nodeName":"拦截","nodeType":"RULE_SET","reused":false,"ruleSetCode":"ACC_BLOCK_52852447","ruleSetVersionId":"236562477314371584","ruleSetVersionNo":1,"stepNo":2},{"decision":null,"exitEdgeKey":"to_recheck","exitEdgeName":"再次拦截","matched":false,"nodeKey":"auth","nodeName":"认证","nodeType":"RULE_SET","reused":false,"ruleSetCode":"ACC_AUTH_52852447","ruleSetVersionId":"236562477960294400","ruleSetVersionNo":1,"stepNo":3},{"decision":null,"exitEdgeKey":"finish","exitEdgeName":"完成","matched":false,"nodeKey":"recheck","nodeName":"再次拦截","nodeType":"RULE_SET","reused":true,"ruleSetCode":"ACC_BLOCK_52852447","ruleSetVersionId":"236562477314371584","ruleSetVersionNo":1,"stepNo":4},{"decision":"ALLOW","exitEdgeKey":null,"exitEdgeName":null,"matched":null,"nodeKey":"allow","nodeName":"放行","nodeType":"END","reused":null,"ruleSetCode":null,"ruleSetVersionId":null,"ruleSetVersionNo":null,"stepNo":5}]

## 5. 影子运行后查询

- ✅ 创建规则集 ACC_AUTH2_52852447：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236562481290571776","pointVersionId":null,"ruleSetCode":"ACC_AUTH2_52852447","versionNo":1,"status":"DRAFT","contentHash":"0c431915782c91fe1376cb5a75223aae8602a4cc28ebba7f0902f8786200d97a","createdBy":"70001","createTime":"2026-10-15T18:54:50.652526Z"}`
- ✅ 冻结规则集 ACC_AUTH2_52852447：`POST /decision-rule-sets/v1/rule-sets/236562481290571776/freeze` → **200** `{"ruleSetVersionId":"236562481290571776","pointVersionId":null,"ruleSetCode":"ACC_AUTH2_52852447","versionNo":1,"status":"FROZEN","contentHash":"0c431915782c91fe1376cb5a75223aae8602a4cc28ebba7f0902f8786200d97a","frozenBy":"70001","frozenTime":"2026-10-15T18:54:50.677014Z"}`
- ✅ 保存新流程（3DS 门槛 50）：`POST /decision-flows/v1/flows` → **200** `{"flowId":"236562481655476224","flowVersionId":"236562481655476225","savedBy":"70001","savedTime":"2026-10-15T18:54:50.745848Z","version":"1","versionNo":1}`
- ✅ 创建执行发布：`POST /decision-releases/v1/executions` → **200** `{"activatedTime":null,"checks":[],"contentHash":"2f2d6ca3e577b9a015b18adeca37b05910b12ff373614b4601fb953f160f9523","environment":"TEST","flowVersionId":"236562481655476225","pointCode":"PAYMENT_ATTEMPT_RISK","releaseId":"236562482175569920","releaseNo":"2","shadowRun":null,"status":"DRAFT","supersededTime":null,"version":1}`
- ✅ 静态校验：`POST /decision-releases/v1/executions/236562482175569920/validate` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - ✅ 校验后为 SHADOWING
- ✅ 启动影子运行（样本 20）：`POST /decision-releases/v1/executions/236562482175569920/shadow-runs` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - 影子运行结果 {"actionChangeCount":1,"allowToDenyCount":0,"allowToReviewCount":0,"baselineReleaseId":"236562478857875456","checkStatus":"PASSED","denyToAllowCount":0,"failedCount":0,"featureMissingCount":0,"p95Millis":2,"p99Millis":2,"sampleCount":4,"sampleTarget":20,"shadowRunId":"236562482565640192","status":"COMPLETED","timedOutCount":0}
  - ✅ 影子运行完成
- ✅ 生效：`POST /decision-releases/v1/executions/236562482175569920/activate` → **200** `{"activatedTime":"2026-10-15T18:54:51.502628Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 生效后为 ACTIVE
- ✅ 基线（100 放行）的解释：`GET /risk-decisions/v1/evaluations/236562479940005888/explanation` → **200** `{"evaluationId":"236562479940005888","inputs":{"paymentIntentId":"872645715386662","riskActionId":"396141636643061","orderId":"291501910546351","clientId":"800001","entityNo":"AE001","paymentMethodCode":"CARD_VISA","captureMode":"AUTOMATIC","amount":"100","currency":"USD","industryCode":"DIGITAL_GOODS","mccCode":"5816","consumerCountryCode":"AE","consumerReference":"consumer-001","clientIpAddress"…`
  - ✅ 列出 1 个影子评估且认证要求变化
- ✅ 按 ID 查影子评估：`GET /risk-decisions/v1/evaluations/236562482892795904` → **200** `{"baselineEvaluationId":"236562479940005888","businessKey":"145615362624206","elapsedMillis":1,"environment":"TEST","evaluationId":"236562482892795904","evaluationTime":"2026-10-15T18:54:50.299107Z","failureCode":null,"flowCode":"ACC_FLOW2_52852447","flowId":"236562481655476224","flowVersionId":"236562481655476225","flowVersionNo":1,"mode":"SHADOW","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS…`
  - ✅ 影子：SHADOW、基线、输出 THREE_DS_REQUIRED
- ✅ 影子评估的解释：`GET /risk-decisions/v1/evaluations/236562482892795904/explanation` → **200** `{"evaluationId":"236562482892795904","inputs":{"paymentIntentId":"872645715386662","riskActionId":"396141636643061","orderId":"291501910546351","clientId":"800001","entityNo":"AE001","paymentMethodCode":"CARD_VISA","captureMode":"AUTOMATIC","amount":"100","currency":"USD","industryCode":"DIGITAL_GOODS","mccCode":"5816","consumerCountryCode":"AE","consumerReference":"consumer-001","clientIpAddress"…`
  - ✅ 影子路径止于 allow_3ds

## 6. 错误

- ✅ 不存在的评估 → 404：`GET /risk-decisions/v1/evaluations/123456789` → **404** `{"type":"about:blank","title":"Not Found","detail":"评估不存在: 123456789","code":"NOT_FOUND","requestId":"stage5-4ae1fc2c-ae74-4fa9-b422-eec2dec1071b","timestamp":"2026-10-15T18:54:51.595960Z"}`
- ✅ 不存在的业务身份 → 404：`GET /risk-decisions/v1/evaluations?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=123456789` → **404** `{"type":"about:blank","title":"Not Found","detail":"该业务身份没有评估: PAYMENT_ATTEMPT_RISK","code":"NOT_FOUND","requestId":"stage5-8cb720e4-1333-4026-a65c-807da0b2917b","timestamp":"2026-10-15T18:54:51.598646Z"}`
- ✅ ID 格式错误 → 400：`GET /risk-decisions/v1/evaluations/abc` → **400** `{"type":"about:blank","title":"Bad Request","detail":"评估 ID 格式不正确","code":"INVALID_REQUEST","requestId":"stage5-ec048fe8-2734-4298-82fe-61cdb1f4761a","timestamp":"2026-10-15T18:54:51.600606Z"}`
- ✅ 未声明 Query → 400：`GET /risk-decisions/v1/evaluations/236562479940005888?x=1` → **400** `{"type":"about:blank","title":"Bad Request","detail":"评估查询 Query 参数未知、重复或为空","code":"INVALID_REQUEST","requestId":"stage5-be57a1c8-ea68-4be7-9c4a-04e28597a663","timestamp":"2026-10-15T18:54:51.602768Z"}`
- ✅ 未登记风控点 → 400：`GET /risk-decisions/v1/evaluations?pointCode=UNKNOWN_POINT&businessKey=1` → **400** `{"type":"about:blank","title":"Bad Request","detail":"风控点未登记评估器: UNKNOWN_POINT","code":"INVALID_REQUEST","requestId":"stage5-3547f154-fde7-4143-835a-56304fa78d8a","timestamp":"2026-10-15T18:54:51.604333Z"}`
  - 受限值（供日志核对）：ip=203.0.113.10 ua-len=21

## 结论

全部检查通过。
