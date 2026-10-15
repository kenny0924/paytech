# 阶段 5 接口验收证据（2026-10-15 15:10:46）

后缀 `062646`；入口 `https://127.0.0.1:18444`。


## 1. 风控名单

- 名单目录：`GET /risk-lists/v1/lists?page=1&size=20` → **200** `{"items": [], "page": 1, "size": 20, "total": 0}`
- 新建卡指纹黑名单：`POST /risk-lists/v1/lists` → **200** `{"active": true, "listCode": "S5_FP_062646", "listId": "236445696276840448", "listName": "验收卡指纹黑名单", "purpose": "BLOCK", "subjectType": "CARD_FINGERPRINT"}`
  - ✅ 名单创建后有效
- 重复编码新建：`POST /risk-lists/v1/lists` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "业务校验未通过", "code": "BUSINESS_CHECK_FAILED", "requestId": "a03712f3-ce61-4d34-9d48-80d6e24d7692", "timestamp": "2026-10-15T11:10:46.988029Z"}`
  - ✅ 重复编码被 Risk 拒绝并透传错误码：BUSINESS_CHECK_FAILED
- 加入成员：`POST /risk-lists/v1/lists/S5_FP_062646/entries` → **200** `{"createTime": "2026-10-15T11:10:47.041518Z", "createdBy": "236033080844881920", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T11:10:47.041518Z", "effectiveTo": null, "entryId": "236445696717242368", "listId": "236445696276840448", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "status": "ACTIVE…`
  - ✅ 成员响应只含脱敏主体键、不含原值：fp_s5_******
- 加入第二个成员：`POST /risk-lists/v1/lists/S5_FP_062646/entries` → **200** `{"createTime": "2026-10-15T11:10:47.111550Z", "createdBy": "236033080844881920", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T11:10:47.111550Z", "effectiveTo": null, "entryId": "236445697010843648", "listId": "236445696276840448", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "status": "ACTIVE…`
- 按原值查询成员：`GET /risk-lists/v1/lists/S5_FP_062646/entries?subjectValue=<RAW>` → **200** `{"items": [{"createTime": "2026-10-15T11:10:47.041518Z", "createdBy": "236033080844881920", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T11:10:47.041518Z", "effectiveTo": null, "entryId": "236445696717242368", "listId": "236445696276840448", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "statu…`
  - ✅ 按原值查询只返回该成员
- 失效第二个成员：`POST /risk-lists/v1/lists/S5_FP_062646/entries/236445697010843648/deactivate` → **200** `{"createTime": "2026-10-15T11:10:47.111550Z", "createdBy": "236033080844881920", "deactivatedBy": "236033080844881920", "deactivatedReason": "验收失效", "effectiveFrom": "2026-10-15T11:10:47.111550Z", "effectiveTo": "2026-10-15T11:10:47.234628Z", "entryId": "236445697010843648", "listId": "236445696276840448", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入"…`
  - ✅ 失效后状态非 ACTIVE 且记录失效原因：RELEASED
- 按状态查询有效成员：`GET /risk-lists/v1/lists/S5_FP_062646/entries?status=ACTIVE` → **200** `{"items": [{"createTime": "2026-10-15T11:10:47.041518Z", "createdBy": "236033080844881920", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T11:10:47.041518Z", "effectiveTo": null, "entryId": "236445696717242368", "listId": "236445696276840448", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "statu…`
  - ✅ 有效成员只剩 1 个
- 名单目录带未知查询参数：`GET /risk-lists/v1/lists?keyword=x` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不符合网关路由契约", "code": "GATEWAY_INVALID_REQUEST", "requestId": "9b50be19-4a4f-481b-8b65-dd88d24f1d2c", "timestamp": "2026-10-15T11:10:47.296790Z"}`
- 加入成员缺少原因：`POST /risk-lists/v1/lists/S5_FP_062646/entries` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不正确", "code": "INVALID_REQUEST", "requestId": "9c67214f-2ce9-43bc-abbd-9808e156a199", "timestamp": "2026-10-15T11:10:47.316282Z"}`

## 2. 拒绝原因

- 新建拒绝原因：`POST /decision-dictionaries/v1/rejection-reasons` → **200** `{"dictionaryType": "REJECTION_REASON", "code": "S5_REASON_062646", "version": "1", "status": "ACTIVE", "changedBy": "236033080844881920", "changedTime": "2026-10-15T11:10:47.358544Z"}`
  - ✅ 拒绝原因生效

## 3. 实时累计与名单命中变量

- 新建实时累计变量（同卡 1 小时评估次数，不含本次）：`POST /decision-variables/v1/variables` → **201** `{"variableCode": "S5_FP_COUNT_1H_062646", "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY", "valueType": "NUMBER", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": "SCALAR", "currencyCode": null, "currencyVariableCode": null, "formatCode": "N…`
  - ✅ 实时累计变量创建
- 新建名单命中变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode": "S5_FP_BLOCKED_062646", "variableName": "卡指纹在验收黑名单", "sourceType": "LIST", "valueType": "BOOLEAN", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": null, "currencyCode": null, "currencyVariableCode": null, "formatCode": "NONE", "all…`
  - ✅ 名单命中变量创建
- 实时累计变量详情：`GET /decision-variables/v1/variables/S5_FP_COUNT_1H_062646` → **200** `{"variableCode": "S5_FP_COUNT_1H_062646", "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY", "valueType": "NUMBER", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": "SCALAR", "currencyCode": null, "currencyVariableCode": null, "formatCode": "N…`
  - ✅ 变量详情不回显累计参数（R-Q3 不做）
- 实时累计窗口为字符串：`POST /decision-variables/v1/variables` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不正确", "code": "INVALID_REQUEST", "requestId": "9f187b2f-6484-4c1d-b4e7-10175f3b3982", "timestamp": "2026-10-15T11:10:47.617616Z"}`

## 4. 规则集、决策流与首个执行发布

- 支付点规则集编辑目录：`GET /decision-rule-sets/v1/authoring-context/PAYMENT_ATTEMPT_RISK` → **200** `{"descriptorCode": "RISK_VARIABLE_AUTHORING", "point": {"pointCode": "PAYMENT_ATTEMPT_RISK", "pointName": "Payment Attempt Risk", "businessDomain": "PAYMENT", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "outputContractCode": "RISK_DECISION_RESULT", "defaultDecision": "ALLOW", "evaluationStrategy": "FIRST_TERMINAL", "failureStrategy": "…`
  - ✅ 规则集目录为统一变量格式并含支付终态动作
- 支付点决策流上下文：`GET /decision-flows/v1/authoring-context?pointCode=PAYMENT_ATTEMPT_RISK` → **200** `{"supportedPoints": [{"pointCode": "PAYMENT_ATTEMPT_RISK", "pointName": "Payment Attempt Risk"}], "pointCode": "PAYMENT_ATTEMPT_RISK", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "graphSchemaVersion": 1, "fields": [{"fieldCode": "amount", "label": "支付金额", "valueType": "NUMBER", "required": true, "operators": ["EQ", "NE", "GT", "GTE", "…`
  - ✅ 决策流上下文声明认证要求输出
- 保存规则集 拦截：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236445699804250112", "pointVersionId": null, "ruleSetCode": "S5_BLOCK_062646", "versionNo": 1, "status": "DRAFT", "contentHash": "b30eef40162e0266b48924282ecd7a4e0394fe577ad622b74db8e61e226762f5", "createdBy": "236033080844881920", "createTime": "2026-10-15T11:10:47.767028Z"}`
- 冻结规则集 拦截：`POST /decision-rule-sets/v1/rule-sets/236445699804250112/freeze` → **200** `{"ruleSetVersionId": "236445699804250112", "pointVersionId": null, "ruleSetCode": "S5_BLOCK_062646", "versionNo": 1, "status": "FROZEN", "contentHash": "b30eef40162e0266b48924282ecd7a4e0394fe577ad622b74db8e61e226762f5", "frozenBy": "236033080844881920", "frozenTime": "2026-10-15T11:10:47.840891Z"}`
  - ✅ 规则集 拦截 冻结
- 保存规则集 认证（金额 > 1000 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236445700282400768", "pointVersionId": null, "ruleSetCode": "S5_AUTH_062646", "versionNo": 1, "status": "DRAFT", "contentHash": "083f0899c3beb18ed41d4d57ec413ddd5d306225ab4c4824196c8cd36a64d0de", "createdBy": "236033080844881920", "createTime": "2026-10-15T11:10:47.888283Z"}`
- 冻结规则集 认证（金额 > 1000 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets/236445700282400768/freeze` → **200** `{"ruleSetVersionId": "236445700282400768", "pointVersionId": null, "ruleSetCode": "S5_AUTH_062646", "versionNo": 1, "status": "FROZEN", "contentHash": "083f0899c3beb18ed41d4d57ec413ddd5d306225ab4c4824196c8cd36a64d0de", "frozenBy": "236033080844881920", "frozenTime": "2026-10-15T11:10:47.930843Z"}`
  - ✅ 规则集 认证（金额 > 1000 需要 3DS） 冻结
- 静态校验决策流：`POST /decision-flows/v1/flows/validate` → **200** `{"valid": true, "issues": [], "truncated": false}`
- 保存决策流：`POST /decision-flows/v1/flows` → **200** `{"flowId": "236445701087707136", "flowVersionId": "236445701087707137", "versionNo": 1, "version": "1", "savedBy": "236033080844881920", "savedTime": "2026-10-15T11:10:48.088610Z"}`
- 决策流详情：`GET /decision-flows/v1/flows/236445701087707136` → **200** `{"flowId": "236445701087707136", "pointCode": "PAYMENT_ATTEMPT_RISK", "flowCode": "S5_FLOW_062646", "flowStatus": "CONFIGURABLE", "currentFlowVersionId": "236445701087707137", "currentVersionNo": 1, "version": "1", "flowVersionId": "236445701087707137", "versionNo": 1, "flowName": "验收支付流程", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "…`
  - ✅ “放行（需要 3DS）”结束节点保存了认证要求
  - ✅ 普通放行结束节点由 Risk 补齐默认值：{"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}
- 当前生效发布（尚无）：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&status=ACTIVE` → **200** `{"items": [], "page": 1, "size": 10, "total": 0}`
  - ✅ 首发前没有生效发布
- 创建执行发布（发布 1）：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "cbc2ebcc86478224c2acaabce8306e65bd4aa7d0c2e2af1bb622f82919f3b25a", "environment": "TEST", "flowVersionId": "236445701087707137", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236445701721047040", "releaseNo": "1", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 发布 1 创建后为 DRAFT
- 静态校验（发布 1）：`POST /decision-releases/v1/executions/236445701721047040/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 发布 1 校验后为 SHADOWING 且检查全部通过：8 项检查
- 首发激活：`POST /decision-releases/v1/executions/236445701721047040/activate` → **200** `{"activatedTime": "2026-10-15T11:10:48.396189Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 发布 1 激活后为 ACTIVE
- 当前生效发布：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&status=ACTIVE` → **200** `{"items": [{"activatedBy": "236033080844881920", "activatedTime": "2026-10-15T11:10:48.396189Z", "active": true, "contentHash": "cbc2ebcc86478224c2acaabce8306e65bd4aa7d0c2e2af1bb622f82919f3b25a", "createTime": "2026-10-15T11:10:48.239075Z", "createdBy": "236033080844881920", "environment": "TEST", "flowCode": "S5_FLOW_062646", "flowId": "236445701087707136",…`
  - ✅ 列表中唯一生效的是发布 1

## 5. 发布 1 生效佐证（直连 Risk 统一评估）

- 评估（直连 Risk）小额：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445702715097088", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 小额 → ALLOW/NOT_REQUIRED
- 评估（直连 Risk）1500：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445702924812288", "outputs": {"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 1500 → ALLOW/THREE_DS_REQUIRED
- 评估（直连 Risk）6000：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445703084195840", "outputs": {}, "result": "REVIEW"}`
  - ✅ 6000 → REVIEW/None
- 评估（直连 Risk）黑名单卡：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445703243579392", "outputs": {}, "result": "DENY"}`
  - ✅ 黑名单卡 → DENY/None
- 评估（直连 Risk）同卡第 1 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445703440711680", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 同卡第 1 次 → ALLOW
- 评估（直连 Risk）同卡第 2 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445703637843968", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 同卡第 2 次 → ALLOW
- 评估（直连 Risk）同卡第 3 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445703830781952", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 同卡第 3 次 → ALLOW
- 评估（直连 Risk）同卡第 4 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445704027914240", "outputs": {}, "result": "DENY"}`
  - ✅ 同卡第 4 次 → DENY

## 6. 发布 2：影子运行后替代

- 保存规则集 认证 v2（金额 > 50 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236445704187297792", "pointVersionId": null, "ruleSetCode": "S5_AUTH2_062646", "versionNo": 1, "status": "DRAFT", "contentHash": "50cb5bdd75206f7c10f71310f135f5b3d853904785971aeda55d053aeb21ae50", "createdBy": "236033080844881920", "createTime": "2026-10-15T11:10:48.820732Z"}`
- 冻结规则集 认证 v2（金额 > 50 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets/236445704187297792/freeze` → **200** `{"ruleSetVersionId": "236445704187297792", "pointVersionId": null, "ruleSetCode": "S5_AUTH2_062646", "versionNo": 1, "status": "FROZEN", "contentHash": "50cb5bdd75206f7c10f71310f135f5b3d853904785971aeda55d053aeb21ae50", "frozenBy": "236033080844881920", "frozenTime": "2026-10-15T11:10:48.866462Z"}`
  - ✅ 规则集 认证 v2（金额 > 50 需要 3DS） 冻结
- 读取当前流程版本：`GET /decision-flows/v1/flows/236445701087707136` → **200** `{"flowId": "236445701087707136", "pointCode": "PAYMENT_ATTEMPT_RISK", "flowCode": "S5_FLOW_062646", "flowStatus": "CONFIGURABLE", "currentFlowVersionId": "236445701087707137", "currentVersionNo": 1, "version": "1", "flowVersionId": "236445701087707137", "versionNo": 1, "flowName": "验收支付流程", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "…`
- 保存流程新版本：`POST /decision-flows/v1/flows` → **200** `{"flowId": "236445701087707136", "flowVersionId": "236445704950661120", "versionNo": 2, "version": "2", "savedBy": "236033080844881920", "savedTime": "2026-10-15T11:10:49.009854Z"}`
  - ✅ 流程新版本号为 2
- 创建执行发布（发布 2）：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "f31ea07cf24b541c9dcbfa8864be44d219554bd4cd48ea53c743f0173b32d543", "environment": "TEST", "flowVersionId": "236445704950661120", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236445705114238976", "releaseNo": "2", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 发布 2 创建后为 DRAFT
- 静态校验（发布 2）：`POST /decision-releases/v1/executions/236445705114238976/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 发布 2 校验后为 SHADOWING 且检查全部通过：8 项检查
- 启动影子运行（样本 20）：`POST /decision-releases/v1/executions/236445705114238976/shadow-runs` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
- 轮询发布详情：`GET /decision-releases/v1/executions/236445705114238976` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - 影子运行结果 `{"actionChangeCount": 4, "allowToDenyCount": 0, "allowToReviewCount": 0, "baselineReleaseId": "236445701721047040", "checkStatus": "PASSED", "denyToAllowCount": 0, "failedCount": 0, "featureMissingCount": 0, "p95Millis": 2, "p99Millis": 2, "sampleCount": 8, "sampleTarget": 20, "shadowRunId": "236445705579806720", "status": "COMPLETED", "timedOutCount": 0}`
  - ✅ 影子运行完成且无失败/超时
  - ✅ 影子运行统计到认证要求变化：actionChangeCount=4
- 发布 2 激活：`POST /decision-releases/v1/executions/236445705114238976/activate` → **200** `{"activatedTime": "2026-10-15T11:10:51.281829Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 发布 2 激活后为 ACTIVE
- 发布 1 详情：`GET /decision-releases/v1/executions/236445701721047040` → **200** `{"activatedTime": "2026-10-15T11:10:48.396189Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 发布 1 被替代
- 评估（直连 Risk）发布 2 生效后 100：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236445714765332480", "outputs": {"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 100 → ALLOW/THREE_DS_REQUIRED（新门槛生效）

## 7. 回滚准备与边界

- 创建执行发布（回滚发布（以发布 1 的流程版本新建））：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "cbc2ebcc86478224c2acaabce8306e65bd4aa7d0c2e2af1bb622f82919f3b25a", "environment": "TEST", "flowVersionId": "236445701087707137", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236445714903744512", "releaseNo": "3", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 回滚发布（以发布 1 的流程版本新建） 创建后为 DRAFT
- 静态校验（回滚发布（以发布 1 的流程版本新建））：`POST /decision-releases/v1/executions/236445714903744512/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 回滚发布（以发布 1 的流程版本新建） 校验后为 SHADOWING 且检查全部通过：8 项检查
- 执行发布列表：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&page=1&size=10` → **200** `{"items": [{"activatedBy": null, "activatedTime": null, "active": false, "contentHash": "cbc2ebcc86478224c2acaabce8306e65bd4aa7d0c2e2af1bb622f82919f3b25a", "createTime": "2026-10-15T11:10:51.382888Z", "createdBy": "236033080844881920", "environment": "TEST", "flowCode": "S5_FLOW_062646", "flowId": "236445701087707136", "flowVersionId": "236445701087707137", …`
  - ✅ 列表含 ACTIVE / SUPERSEDED / SHADOWING 三个发布
- 执行发布列表带未知查询参数：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&keyword=x` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不符合网关路由契约", "code": "GATEWAY_INVALID_REQUEST", "requestId": "7e6cd02d-8712-4700-ab6b-ef264f27cd39", "timestamp": "2026-10-15T11:10:51.501383Z"}`
- 已替代的发布不能再激活：`POST /decision-releases/v1/executions/236445701721047040/activate` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "业务校验未通过", "code": "BUSINESS_CHECK_FAILED", "requestId": "957a9d59-34d1-4619-a6dd-5c5c4370bbb7", "timestamp": "2026-10-15T11:10:51.525064Z"}`
  - ✅ Risk 拒绝激活已替代的发布
- 影子样本数超上限：`POST /decision-releases/v1/executions/236445714903744512/shadow-runs` → **422** `{"type": "about:blank", "title": "Unprocessable Content", "detail": "请求字段校验未通过", "code": "VALIDATION_ERROR", "requestId": "dc9baf6b-6623-47a2-99ba-93e6b963e426", "timestamp": "2026-10-15T11:10:51.545226Z", "errors": [{"field": "sampleTarget", "message": "must be less than or equal to the maximum"}]}`
- 拦截规则集列表：`GET /decision-rule-sets/v1/rule-sets?pointCode=PAYMENT_ATTEMPT_RISK&ruleSetCode=S5_BLOCK_062646` → **200** `{"items": [{"ruleSetVersionId": "236445699804250112", "pointVersionId": null, "pointCode": "PAYMENT_ATTEMPT_RISK", "ruleSetCode": "S5_BLOCK_062646", "ruleSetName": "验收 S5_BLOCK_062646", "versionNo": 1, "status": "FROZEN", "contentHash": "b30eef40162e0266b48924282ecd7a4e0394fe577ad622b74db8e61e226762f5", "createdBy": "236033080844881920", "changeReason": "阶段 …`
  - ✅ 规则集列表标记为正在使用
- 删除被生效发布引用的规则集：`DELETE /decision-rule-sets/v1/rule-sets/236445699833610240` → **409** `{"type": "about:blank", "title": "Conflict", "detail": "规则集正在使用，无法删除", "code": "RISK_RULE_SET_IN_USE", "requestId": "b8826fdd-b833-44ed-9d08-31d4bc8fea3d", "timestamp": "2026-10-15T11:10:51.618424Z"}`
  - ✅ 返回 RISK_RULE_SET_IN_USE

保留：回滚发布 `236445714903744512`（SHADOWING）供浏览器验收；生效发布 `236445705114238976`。

## 结论

44/44 项检查通过。
