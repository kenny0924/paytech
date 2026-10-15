# 阶段 5 接口验收证据（2026-10-15 19:58:06）

后缀 `079886`；入口 `http://localhost:28080`。


## 1. 风控名单

- 名单目录：`GET /risk-lists/v1/lists?page=1&size=20` → **200** `{"items": [], "page": 1, "size": 20, "total": 0}`
- 新建卡指纹黑名单：`POST /risk-lists/v1/lists` → **200** `{"active": true, "listCode": "S5_FP_079886", "listId": "236518006904164352", "listName": "验收卡指纹黑名单", "purpose": "BLOCK", "subjectType": "CARD_FINGERPRINT"}`
  - ✅ 名单创建后有效
- 重复编码新建：`POST /risk-lists/v1/lists` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "业务校验未通过", "code": "BUSINESS_CHECK_FAILED", "requestId": "2fdf514f-fde2-4e0c-b2c4-066222493290", "timestamp": "2026-10-15T15:58:07.214966Z"}`
  - ✅ 重复编码被 Risk 拒绝并透传错误码：BUSINESS_CHECK_FAILED
- 加入成员：`POST /risk-lists/v1/lists/S5_FP_079886/entries` → **200** `{"createTime": "2026-10-15T15:58:07.307038Z", "createdBy": "221546852372267008", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T15:58:07.307038Z", "effectiveTo": null, "entryId": "236518007633973248", "listId": "236518006904164352", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "status": "ACTIVE…`
  - ✅ 成员响应只含脱敏主体键、不含原值：fp_s5_******
- 加入第二个成员：`POST /risk-lists/v1/lists/S5_FP_079886/entries` → **200** `{"createTime": "2026-10-15T15:58:07.374143Z", "createdBy": "221546852372267008", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T15:58:07.374143Z", "effectiveTo": null, "entryId": "236518007898214400", "listId": "236518006904164352", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "status": "ACTIVE…`
- 按原值查询成员：`GET /risk-lists/v1/lists/S5_FP_079886/entries?subjectValue=<RAW>` → **200** `{"items": [{"createTime": "2026-10-15T15:58:07.307038Z", "createdBy": "221546852372267008", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T15:58:07.307038Z", "effectiveTo": null, "entryId": "236518007633973248", "listId": "236518006904164352", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "statu…`
  - ✅ 按原值查询只返回该成员
- 失效第二个成员：`POST /risk-lists/v1/lists/S5_FP_079886/entries/236518007898214400/deactivate` → **200** `{"createTime": "2026-10-15T15:58:07.374143Z", "createdBy": "221546852372267008", "deactivatedBy": "221546852372267008", "deactivatedReason": "验收失效", "effectiveFrom": "2026-10-15T15:58:07.374143Z", "effectiveTo": "2026-10-15T15:58:07.446996Z", "entryId": "236518007898214400", "listId": "236518006904164352", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入"…`
  - ✅ 失效后状态非 ACTIVE 且记录失效原因：RELEASED
- 按状态查询有效成员：`GET /risk-lists/v1/lists/S5_FP_079886/entries?status=ACTIVE` → **200** `{"items": [{"createTime": "2026-10-15T15:58:07.307038Z", "createdBy": "221546852372267008", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T15:58:07.307038Z", "effectiveTo": null, "entryId": "236518007633973248", "listId": "236518006904164352", "maskedSubjectKey": "fp_s5_******", "reason": "验收加入", "sourceType": "MANUAL", "statu…`
  - ✅ 有效成员只剩 1 个
- 名单目录带未知查询参数：`GET /risk-lists/v1/lists?keyword=x` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不符合网关路由契约", "code": "GATEWAY_INVALID_REQUEST", "requestId": "2d60030b-0370-44f9-b750-edd996b4c312", "timestamp": "2026-10-15T15:58:07.528327Z"}`
- 加入成员缺少原因：`POST /risk-lists/v1/lists/S5_FP_079886/entries` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不正确", "code": "INVALID_REQUEST", "requestId": "8cb22e67-a5d5-4361-8295-2289a055d232", "timestamp": "2026-10-15T15:58:07.553594Z"}`

## 2. 拒绝原因

- 新建拒绝原因：`POST /decision-dictionaries/v1/rejection-reasons` → **200** `{"dictionaryType": "REJECTION_REASON", "code": "S5_REASON_079886", "version": "1", "status": "ACTIVE", "changedBy": "221546852372267008", "changedTime": "2026-10-15T15:58:07.670897Z"}`
  - ✅ 拒绝原因生效

## 3. 实时累计与名单命中变量

- 新建实时累计变量（同卡 1 小时评估次数，不含本次）：`POST /decision-variables/v1/variables` → **201** `{"variableCode": "S5_FP_COUNT_1H_079886", "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY", "valueType": "NUMBER", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": "SCALAR", "currencyCode": null, "currencyVariableCode": null, "formatCode": "N…`
  - ✅ 实时累计变量创建
- 新建名单命中变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode": "S5_FP_BLOCKED_079886", "variableName": "卡指纹在验收黑名单", "sourceType": "LIST", "valueType": "BOOLEAN", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": null, "currencyCode": null, "currencyVariableCode": null, "formatCode": "NONE", "all…`
  - ✅ 名单命中变量创建
- 实时累计变量详情：`GET /decision-variables/v1/variables/S5_FP_COUNT_1H_079886` → **200** `{"variableCode": "S5_FP_COUNT_1H_079886", "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY", "valueType": "NUMBER", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": "SCALAR", "currencyCode": null, "currencyVariableCode": null, "formatCode": "N…`
  - ✅ 变量详情不回显累计参数（R-Q3 不做）
- 实时累计窗口为字符串：`POST /decision-variables/v1/variables` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不正确", "code": "INVALID_REQUEST", "requestId": "4efe6354-6064-473e-b92b-63c54b49b06e", "timestamp": "2026-10-15T15:58:07.946681Z"}`

## 4. 规则集、决策流与首个执行发布

- 支付点规则集编辑目录：`GET /decision-rule-sets/v1/authoring-context/PAYMENT_ATTEMPT_RISK` → **200** `{"descriptorCode": "RISK_VARIABLE_AUTHORING", "point": {"pointCode": "PAYMENT_ATTEMPT_RISK", "pointName": "Payment Attempt Risk", "businessDomain": "PAYMENT", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "outputContractCode": "RISK_DECISION_RESULT", "defaultDecision": "ALLOW", "evaluationStrategy": "FIRST_TERMINAL", "failureStrategy": "…`
  - ✅ 规则集目录为统一变量格式并含支付终态动作
- 支付点决策流上下文：`GET /decision-flows/v1/authoring-context?pointCode=PAYMENT_ATTEMPT_RISK` → **200** `{"supportedPoints": [{"pointCode": "PAYMENT_ATTEMPT_RISK", "pointName": "Payment Attempt Risk"}], "pointCode": "PAYMENT_ATTEMPT_RISK", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "graphSchemaVersion": 1, "fields": [{"fieldCode": "amount", "label": "支付金额", "valueType": "NUMBER", "required": true, "operators": ["EQ", "NE", "GT", "GTE", "…`
  - ✅ 决策流上下文声明认证要求输出
- 保存规则集 拦截：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236518011111051264", "pointVersionId": null, "ruleSetCode": "S5_BLOCK_079886", "versionNo": 1, "status": "DRAFT", "contentHash": "870bcd4fb2a0e23911dfeb6621a09fb671957324fdd11f83dc967fe059d0b19f", "createdBy": "221546852372267008", "createTime": "2026-10-15T15:58:08.128682Z"}`
- 冻结规则集 拦截：`POST /decision-rule-sets/v1/rule-sets/236518011111051264/freeze` → **200** `{"ruleSetVersionId": "236518011111051264", "pointVersionId": null, "ruleSetCode": "S5_BLOCK_079886", "versionNo": 1, "status": "FROZEN", "contentHash": "870bcd4fb2a0e23911dfeb6621a09fb671957324fdd11f83dc967fe059d0b19f", "frozenBy": "221546852372267008", "frozenTime": "2026-10-15T15:58:08.208327Z"}`
  - ✅ 规则集 拦截 冻结
- 保存规则集 认证（金额 > 1000 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236518011568230400", "pointVersionId": null, "ruleSetCode": "S5_AUTH_079886", "versionNo": 1, "status": "DRAFT", "contentHash": "aa937e3c9f3fb767049440e6a14600736cd3e2febe76220232c1ef9bdc09998b", "createdBy": "221546852372267008", "createTime": "2026-10-15T15:58:08.246032Z"}`
- 冻结规则集 认证（金额 > 1000 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets/236518011568230400/freeze` → **200** `{"ruleSetVersionId": "236518011568230400", "pointVersionId": null, "ruleSetCode": "S5_AUTH_079886", "versionNo": 1, "status": "FROZEN", "contentHash": "aa937e3c9f3fb767049440e6a14600736cd3e2febe76220232c1ef9bdc09998b", "frozenBy": "221546852372267008", "frozenTime": "2026-10-15T15:58:08.284399Z"}`
  - ✅ 规则集 认证（金额 > 1000 需要 3DS） 冻结
- 静态校验决策流：`POST /decision-flows/v1/flows/validate` → **200** `{"valid": true, "issues": [], "truncated": false}`
- 保存决策流：`POST /decision-flows/v1/flows` → **200** `{"flowId": "236518012423868416", "flowVersionId": "236518012423868417", "versionNo": 1, "version": "1", "savedBy": "221546852372267008", "savedTime": "2026-10-15T15:58:08.454409Z"}`
- 决策流详情：`GET /decision-flows/v1/flows/236518012423868416` → **200** `{"flowId": "236518012423868416", "pointCode": "PAYMENT_ATTEMPT_RISK", "flowCode": "S5_FLOW_079886", "flowStatus": "CONFIGURABLE", "currentFlowVersionId": "236518012423868417", "currentVersionNo": 1, "version": "1", "flowVersionId": "236518012423868417", "versionNo": 1, "flowName": "验收支付流程", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "…`
  - ✅ “放行（需要 3DS）”结束节点保存了认证要求
  - ✅ 普通放行结束节点由 Risk 补齐默认值：{"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}
- 当前生效发布（尚无）：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&status=ACTIVE` → **200** `{"items": [], "page": 1, "size": 10, "total": 0}`
  - ✅ 首发前没有生效发布
- 创建执行发布（发布 1）：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "54e6fdbc69c14efbf5d0d071b9dfaabfc0ab6ab784084e533d22f718f79cff3c", "environment": "TEST", "flowVersionId": "236518012423868417", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236518013094957056", "releaseNo": "11", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 发布 1 创建后为 DRAFT
- 静态校验（发布 1）：`POST /decision-releases/v1/executions/236518013094957056/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 发布 1 校验后为 SHADOWING 且检查全部通过：8 项检查
- 首发激活：`POST /decision-releases/v1/executions/236518013094957056/activate` → **200** `{"activatedTime": "2026-10-15T15:58:08.736040Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 发布 1 激活后为 ACTIVE
- 当前生效发布：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&status=ACTIVE` → **200** `{"items": [{"activatedBy": "221546852372267008", "activatedTime": "2026-10-15T15:58:08.736040Z", "active": true, "contentHash": "54e6fdbc69c14efbf5d0d071b9dfaabfc0ab6ab784084e533d22f718f79cff3c", "createTime": "2026-10-15T15:58:08.614113Z", "createdBy": "221546852372267008", "environment": "TEST", "flowCode": "S5_FLOW_079886", "flowId": "236518012423868416",…`
  - ✅ 列表中唯一生效的是发布 1

## 5. 发布 1 生效佐证（直连 Risk 统一评估）

- 评估（直连 Risk）小额：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518013845737472", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 小额 → ALLOW/NOT_REQUIRED
- 评估（直连 Risk）1500：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014005121024", "outputs": {"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 1500 → ALLOW/THREE_DS_REQUIRED
- 评估（直连 Risk）6000：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014164504576", "outputs": {}, "result": "REVIEW"}`
  - ✅ 6000 → REVIEW/None
- 评估（直连 Risk）黑名单卡：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014248390656", "outputs": {}, "result": "DENY"}`
  - ✅ 黑名单卡 → DENY/None
- 评估（直连 Risk）同卡第 1 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014357442560", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 同卡第 1 次 → ALLOW
- 评估（直连 Risk）同卡第 2 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014445522944", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 同卡第 2 次 → ALLOW
- 评估（直连 Risk）同卡第 3 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014529409024", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 同卡第 3 次 → ALLOW
- 评估（直连 Risk）同卡第 4 次：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518014609100800", "outputs": {}, "result": "DENY"}`
  - ✅ 同卡第 4 次 → DENY

## 6. 发布 2：影子运行后替代

- 保存规则集 认证 v2（金额 > 50 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236518014802038784", "pointVersionId": null, "ruleSetCode": "S5_AUTH2_079886", "versionNo": 1, "status": "DRAFT", "contentHash": "1c2d92b83463481329b2586521d6191f3d0d58e6cc157c1624d4ffd1fd997e4f", "createdBy": "221546852372267008", "createTime": "2026-10-15T15:58:09.018566Z"}`
- 冻结规则集 认证 v2（金额 > 50 需要 3DS）：`POST /decision-rule-sets/v1/rule-sets/236518014802038784/freeze` → **200** `{"ruleSetVersionId": "236518014802038784", "pointVersionId": null, "ruleSetCode": "S5_AUTH2_079886", "versionNo": 1, "status": "FROZEN", "contentHash": "1c2d92b83463481329b2586521d6191f3d0d58e6cc157c1624d4ffd1fd997e4f", "frozenBy": "221546852372267008", "frozenTime": "2026-10-15T15:58:09.050796Z"}`
  - ✅ 规则集 认证 v2（金额 > 50 需要 3DS） 冻结
- 读取当前流程版本：`GET /decision-flows/v1/flows/236518012423868416` → **200** `{"flowId": "236518012423868416", "pointCode": "PAYMENT_ATTEMPT_RISK", "flowCode": "S5_FLOW_079886", "flowStatus": "CONFIGURABLE", "currentFlowVersionId": "236518012423868417", "currentVersionNo": 1, "version": "1", "flowVersionId": "236518012423868417", "versionNo": 1, "flowName": "验收支付流程", "inputContractCode": "PAYMENT_ATTEMPT", "inputContractVersion": 1, "…`
- 保存流程新版本：`POST /decision-flows/v1/flows` → **200** `{"flowId": "236518012423868416", "flowVersionId": "236518015796088832", "versionNo": 2, "version": "2", "savedBy": "221546852372267008", "savedTime": "2026-10-15T15:58:09.258950Z"}`
  - ✅ 流程新版本号为 2
- 创建执行发布（发布 2）：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "d02aa933c2af7d31e513a64b23039eee9b7c6ea1810d01f2e284a181e4edd673", "environment": "TEST", "flowVersionId": "236518015796088832", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236518015942889472", "releaseNo": "12", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 发布 2 创建后为 DRAFT
- 静态校验（发布 2）：`POST /decision-releases/v1/executions/236518015942889472/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 发布 2 校验后为 SHADOWING 且检查全部通过：8 项检查
- 启动影子运行（样本 20）：`POST /decision-releases/v1/executions/236518015942889472/shadow-runs` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
- 轮询发布详情：`GET /decision-releases/v1/executions/236518015942889472` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - 影子运行结果 `{"actionChangeCount": 4, "allowToDenyCount": 0, "allowToReviewCount": 0, "baselineReleaseId": "236518013094957056", "checkStatus": "PASSED", "denyToAllowCount": 0, "failedCount": 0, "featureMissingCount": 0, "p95Millis": 0, "p99Millis": 0, "sampleCount": 8, "sampleTarget": 20, "shadowRunId": "236518016236490752", "status": "COMPLETED", "timedOutCount": 0}`
  - ✅ 影子运行完成且无失败/超时
  - ✅ 影子运行统计到认证要求变化：actionChangeCount=4
- 发布 2 激活：`POST /decision-releases/v1/executions/236518015942889472/activate` → **200** `{"activatedTime": "2026-10-15T15:58:11.459260Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 发布 2 激活后为 ACTIVE
- 发布 1 详情：`GET /decision-releases/v1/executions/236518013094957056` → **200** `{"activatedTime": "2026-10-15T15:58:08.736040Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 发布 1 被替代
- 评估（直连 Risk）发布 2 生效后 100：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518025199718400", "outputs": {"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 100 → ALLOW/THREE_DS_REQUIRED（新门槛生效）

## 7. 回滚准备与边界

- 创建执行发布（回滚发布（以发布 1 的流程版本新建））：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "54e6fdbc69c14efbf5d0d071b9dfaabfc0ab6ab784084e533d22f718f79cff3c", "environment": "TEST", "flowVersionId": "236518012423868417", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236518025325547520", "releaseNo": "13", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 回滚发布（以发布 1 的流程版本新建） 创建后为 DRAFT
- 静态校验（回滚发布（以发布 1 的流程版本新建））：`POST /decision-releases/v1/executions/236518025325547520/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 回滚发布（以发布 1 的流程版本新建） 校验后为 SHADOWING 且检查全部通过：8 项检查
- 执行发布列表：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&page=1&size=10` → **200** `{"items": [{"activatedBy": null, "activatedTime": null, "active": false, "contentHash": "54e6fdbc69c14efbf5d0d071b9dfaabfc0ab6ab784084e533d22f718f79cff3c", "createTime": "2026-10-15T15:58:11.530052Z", "createdBy": "221546852372267008", "environment": "TEST", "flowCode": "S5_FLOW_079886", "flowId": "236518012423868416", "flowVersionId": "236518012423868417", …`
  - ✅ 列表含 ACTIVE / SUPERSEDED / SHADOWING 三个发布
- 执行发布列表带未知查询参数：`GET /decision-releases/v1/executions?pointCode=PAYMENT_ATTEMPT_RISK&keyword=x` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "请求参数不符合网关路由契约", "code": "GATEWAY_INVALID_REQUEST", "requestId": "09d2668d-fe25-41e0-9055-fce46d4d131a", "timestamp": "2026-10-15T15:58:11.643179Z"}`
- 已替代的发布不能再激活：`POST /decision-releases/v1/executions/236518013094957056/activate` → **400** `{"type": "about:blank", "title": "Bad Request", "detail": "业务校验未通过", "code": "BUSINESS_CHECK_FAILED", "requestId": "6973e4c8-5202-4cdf-87f6-d0ab30713b27", "timestamp": "2026-10-15T15:58:11.670263Z"}`
  - ✅ Risk 拒绝激活已替代的发布
- 影子样本数超上限：`POST /decision-releases/v1/executions/236518025325547520/shadow-runs` → **422** `{"type": "about:blank", "title": "Unprocessable Content", "detail": "请求字段校验未通过", "code": "VALIDATION_ERROR", "requestId": "8fe0173c-e81b-4865-adf9-3d7dd2a3db1b", "timestamp": "2026-10-15T15:58:11.703852Z", "errors": [{"field": "sampleTarget", "message": "must be less than or equal to the maximum"}]}`
- 拦截规则集列表：`GET /decision-rule-sets/v1/rule-sets?pointCode=PAYMENT_ATTEMPT_RISK&ruleSetCode=S5_BLOCK_079886` → **200** `{"items": [{"ruleSetVersionId": "236518011111051264", "pointVersionId": null, "pointCode": "PAYMENT_ATTEMPT_RISK", "ruleSetCode": "S5_BLOCK_079886", "ruleSetName": "验收 S5_BLOCK_079886", "versionNo": 1, "status": "FROZEN", "contentHash": "870bcd4fb2a0e23911dfeb6621a09fb671957324fdd11f83dc967fe059d0b19f", "createdBy": "221546852372267008", "changeReason": "阶段 …`
  - ✅ 规则集列表标记为正在使用
- 删除被生效发布引用的规则集：`DELETE /decision-rule-sets/v1/rule-sets/236518011136217088` → **409** `{"type": "about:blank", "title": "Conflict", "detail": "规则集正在使用，无法删除", "code": "RISK_RULE_SET_IN_USE", "requestId": "d346d04d-2976-428e-80a3-cd2eecddd5a1", "timestamp": "2026-10-15T15:58:11.802195Z"}`
  - ✅ 返回 RISK_RULE_SET_IN_USE

保留：回滚发布 `236518025325547520`（SHADOWING）供浏览器验收；生效发布 `236518015942889472`。

## 结论

44/44 项检查通过。
