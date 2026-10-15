# 阶段 7 联调基线配置证据

后缀 `080111`；入口 `http://localhost:28080`。


## 1. 名单、拒绝原因与变量

- 新建卡指纹黑名单：`POST /risk-lists/v1/lists` → **200** `{"active": true, "listCode": "PAY_CARD_BLOCK", "listId": "236518949301358592", "listName": "支付卡指纹黑名单", "purpose": "BLOCK", "subjectType": "CARD_FINGERPRINT"}`
- 新建拒绝原因：`POST /decision-dictionaries/v1/rejection-reasons` → **200** `{"dictionaryType": "REJECTION_REASON", "code": "PAY_RISK_DECLINED", "version": "1", "status": "ACTIVE", "changedBy": "221546852372267008", "changedTime": "2026-10-15T16:01:51.880203Z"}`
- 新建同卡 1 小时评估次数：`POST /decision-variables/v1/variables` → **201** `{"variableCode": "PAY_CARD_ATTEMPTS_1H", "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY", "valueType": "NUMBER", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": "SCALAR", "currencyCode": null, "currencyVariableCode": null, "formatCode": "NO…`
- 新建卡指纹命中黑名单：`POST /decision-variables/v1/variables` → **201** `{"variableCode": "PAY_CARD_BLOCKED", "variableName": "卡指纹在支付黑名单", "sourceType": "LIST", "valueType": "BOOLEAN", "pointCode": "PAYMENT_ATTEMPT_RISK", "usages": ["FLOW_CONDITION", "RULE_CONDITION"], "systemManaged": false, "status": "ACTIVE", "version": "1", "numericUnit": null, "currencyCode": null, "currencyVariableCode": null, "formatCode": "NONE", "allowed…`

## 2. 规则集、决策流与执行发布

- 保存规则集 拦截：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236518950081499136", "pointVersionId": null, "ruleSetCode": "PAY_BLOCK", "versionNo": 1, "status": "DRAFT", "contentHash": "e4ca81304022c99edb98daac78e6a88b7c1e87a43730e7d2a8d046c3b0414a0c", "createdBy": "221546852372267008", "createTime": "2026-10-15T16:01:52.000605Z"}`
- 冻结规则集 拦截：`POST /decision-rule-sets/v1/rule-sets/236518950081499136/freeze` → **200** `{"ruleSetVersionId": "236518950081499136", "pointVersionId": null, "ruleSetCode": "PAY_BLOCK", "versionNo": 1, "status": "FROZEN", "contentHash": "e4ca81304022c99edb98daac78e6a88b7c1e87a43730e7d2a8d046c3b0414a0c", "frozenBy": "221546852372267008", "frozenTime": "2026-10-15T16:01:52.045785Z"}`
  - ✅ 规则集 拦截 冻结
- 保存规则集 认证：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId": "236518950366711808", "pointVersionId": null, "ruleSetCode": "PAY_AUTH", "versionNo": 1, "status": "DRAFT", "contentHash": "d2dfef8c553a88fe4df75f3aacdcd43354d4d6c6469f99aed80b225e790284f3", "createdBy": "221546852372267008", "createTime": "2026-10-15T16:01:52.074490Z"}`
- 冻结规则集 认证：`POST /decision-rule-sets/v1/rule-sets/236518950366711808/freeze` → **200** `{"ruleSetVersionId": "236518950366711808", "pointVersionId": null, "ruleSetCode": "PAY_AUTH", "versionNo": 1, "status": "FROZEN", "contentHash": "d2dfef8c553a88fe4df75f3aacdcd43354d4d6c6469f99aed80b225e790284f3", "frozenBy": "221546852372267008", "frozenTime": "2026-10-15T16:01:52.107543Z"}`
  - ✅ 规则集 认证 冻结
- 保存决策流：`POST /decision-flows/v1/flows` → **200** `{"flowId": "236518950786142208", "flowVersionId": "236518950786142209", "versionNo": 1, "version": "1", "savedBy": "221546852372267008", "savedTime": "2026-10-15T16:01:52.177053Z"}`
- 创建执行发布（基线发布）：`POST /decision-releases/v1/executions` → **200** `{"activatedTime": null, "checks": [], "contentHash": "73317f2fa2b4825770a00a4aa5f6f459358e6f125c5ff440a81e82c90027f988", "environment": "TEST", "flowVersionId": "236518950786142209", "pointCode": "PAYMENT_ATTEMPT_RISK", "releaseId": "236518950932942848", "releaseNo": "11", "shadowRun": null, "status": "DRAFT", "supersededTime": null, "version": 1}`
  - ✅ 基线发布 创建后为 DRAFT
- 静态校验（基线发布）：`POST /decision-releases/v1/executions/236518950932942848/validate` → **200** `{"activatedTime": null, "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "FEATURE_DEPENDENCY…`
  - ✅ 基线发布 校验后为 SHADOWING 且检查全部通过：8 项检查
- 激活基线发布：`POST /decision-releases/v1/executions/236518950932942848/activate` → **200** `{"activatedTime": "2026-10-15T16:01:52.320278Z", "checks": [{"checkCode": "REFERENCE_INTEGRITY", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_SHAPE", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkCode": "AST_TYPES", "checkStatus": "PASSED", "errorCode": null, "errorSummary": null}, {"checkC…`
  - ✅ 基线发布激活后为 ACTIVE

## 3. 生效确认（经临时代理直连 Risk，合成指纹）

- 加入合成黑名单成员：`POST /risk-lists/v1/lists/PAY_CARD_BLOCK/entries` → **200** `{"createTime": "2026-10-15T16:01:52.349159Z", "createdBy": "221546852372267008", "deactivatedBy": null, "deactivatedReason": null, "effectiveFrom": "2026-10-15T16:01:52.349159Z", "effectiveTo": null, "entryId": "236518951511756800", "listId": "236518949301358592", "maskedSubjectKey": "fp_prc******", "reason": "联调基线生效确认", "sourceType": "MANUAL", "status": "AC…`
- 评估（直连 Risk）100：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518951650168832", "outputs": {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 100 → ALLOW/NOT_REQUIRED
- 评估（直连 Risk）800：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518951780192256", "outputs": {"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"}, "result": "ALLOW"}`
  - ✅ 800 → ALLOW/THREE_DS_REQUIRED
- 评估（直连 Risk）6000：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518951939575808", "outputs": {}, "result": "REVIEW"}`
  - ✅ 6000 → REVIEW/None
- 评估（直连 Risk）黑名单卡：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId": "236518952132513792", "outputs": {}, "result": "DENY"}`
  - ✅ 黑名单卡 → DENY/None
- 失效合成黑名单成员：`POST /risk-lists/v1/lists/PAY_CARD_BLOCK/entries/236518951511756800/deactivate` → **200** `{"createTime": "2026-10-15T16:01:52.349159Z", "createdBy": "221546852372267008", "deactivatedBy": "221546852372267008", "deactivatedReason": "联调基线生效确认完成", "effectiveFrom": "2026-10-15T16:01:52.349159Z", "effectiveTo": "2026-10-15T16:01:52.534947Z", "entryId": "236518951511756800", "listId": "236518949301358592", "maskedSubjectKey": "fp_prc******", "reason": …`
  - ✅ 合成成员已失效

## 结论

10/10 项检查通过。
