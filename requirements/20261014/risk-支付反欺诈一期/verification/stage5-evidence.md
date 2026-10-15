# 阶段 5 本机验收证据：打包 jar 经真实 HTTP 的完整链路（2026-10-15）

后缀 67311621


## 1. 未配置：没有生效发布

- ✅ 评估 → 503：`POST /risk-decisions/v1/evaluations` → **503** `{"type":"about:blank","title":"Service Unavailable","detail":"风控点尚未配置生效发布","code":"RISK_DECISION_NOT_CONFIGURED","requestId":"stage5-59228015-328b-43b0-a4ef-4815b6a4fb82","timestamp":"2026-10-15T05:35:41.355971Z"}`

## 2. 配置：拒绝原因、名单、变量、规则集、决策流

- ✅ 创建拒绝原因：`POST /decision-dictionaries/v1/rejection-reasons` → **200** `{"changedBy":"70001","changedTime":"2026-10-15T05:35:41.394668Z","code":"ACCEPT_REASON_67311621","dictionaryType":"REJECTION_REASON","status":"ACTIVE","version":"1"}`
- ✅ 创建卡指纹黑名单：`POST /risk-lists/v1/lists` → **200** `{"active":true,"listCode":"ACCEPT_FP_67311621","listId":"236361367555305472","listName":"验收卡指纹黑名单","purpose":"BLOCK","subjectType":"CARD_FINGERPRINT"}`
- ✅ 加入名单成员：`POST /risk-lists/v1/lists/ACCEPT_FP_67311621/entries` → **200** `{"createTime":"2026-10-15T05:35:41.416238Z","createdBy":"70001","deactivatedBy":null,"deactivatedReason":null,"effectiveFrom":"2026-10-15T05:35:41.416238Z","effectiveTo":null,"entryId":"236361367601442816","listId":"236361367555305472","maskedSubjectKey":"fp_blo******","reason":"验收加入","sourceType":"MANUAL","status":"ACTIVE","subjectType":"CARD_FINGERPRINT"}`
- ✅ 创建名单命中变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_FP_BLOCKED_67311621","variableName":"ACC_FP_BLOCKED_67311621","sourceType":"LIST","valueType":"BOOLEAN","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":null,"currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValue":null,"…`
- ✅ 创建卡指纹 1 小时评估次数变量：`POST /decision-variables/v1/variables` → **201** `{"variableCode":"ACC_FP_COUNT_1H_67311621","variableName":"ACC_FP_COUNT_1H_67311621","sourceType":"VELOCITY","valueType":"NUMBER","pointCode":"PAYMENT_ATTEMPT_RISK","usages":["FLOW_CONDITION","RULE_CONDITION"],"systemManaged":false,"status":"ACTIVE","version":"1","numericUnit":"SCALAR","currencyCode":null,"currencyVariableCode":null,"formatCode":"NONE","allowedValuesVariableCode":null,"currentValu…`
- ✅ 创建规则集 ACC_BLOCK_67311621：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236361368008290304","pointVersionId":null,"ruleSetCode":"ACC_BLOCK_67311621","versionNo":1,"status":"DRAFT","contentHash":"f7d6f278a3a2771c3f83f9ef92008e156d8aa9d82c9aaf499100466fa6b18126","createdBy":"70001","createTime":"2026-10-15T05:35:41.500795Z"}`
- ✅ 冻结规则集 ACC_BLOCK_67311621：`POST /decision-rule-sets/v1/rule-sets/236361368008290304/freeze` → **200** `{"ruleSetVersionId":"236361368008290304","pointVersionId":null,"ruleSetCode":"ACC_BLOCK_67311621","versionNo":1,"status":"FROZEN","contentHash":"f7d6f278a3a2771c3f83f9ef92008e156d8aa9d82c9aaf499100466fa6b18126","frozenBy":"70001","frozenTime":"2026-10-15T05:35:41.532973Z"}`
- ✅ 创建规则集 ACC_AUTH_67311621：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236361368180256768","pointVersionId":null,"ruleSetCode":"ACC_AUTH_67311621","versionNo":1,"status":"DRAFT","contentHash":"74d910e50da84839bb13fa0389dee3023c7987a71b3d38ba65fb004ad22d47ca","createdBy":"70001","createTime":"2026-10-15T05:35:41.552753Z"}`
- ✅ 冻结规则集 ACC_AUTH_67311621：`POST /decision-rule-sets/v1/rule-sets/236361368180256768/freeze` → **200** `{"ruleSetVersionId":"236361368180256768","pointVersionId":null,"ruleSetCode":"ACC_AUTH_67311621","versionNo":1,"status":"FROZEN","contentHash":"74d910e50da84839bb13fa0389dee3023c7987a71b3d38ba65fb004ad22d47ca","frozenBy":"70001","frozenTime":"2026-10-15T05:35:41.572797Z"}`
- ✅ 保存决策流（含“放行（需要 3DS）”）：`POST /decision-flows/v1/flows` → **200** `{"flowId":"236361368536772608","flowVersionId":"236361368536772609","savedBy":"70001","savedTime":"2026-10-15T05:35:41.641384Z","version":"1","versionNo":1}`
- ✅ 流程详情：放行结束节点输出：`GET /decision-flows/v1/flows/236361368536772608` → **200** `{"flowId":"236361368536772608","pointCode":"PAYMENT_ATTEMPT_RISK","flowCode":"ACC_FLOW_67311621","flowStatus":"CONFIGURABLE","currentFlowVersionId":"236361368536772609","currentVersionNo":1,"version":"1","flowVersionId":"236361368536772609","versionNo":1,"flowName":"Checkout flow","inputContractCode":"PAYMENT_ATTEMPT","inputContractVersion":1,"createdBy":"70001","createTime":"2026-10-15T05:35:41.6…`
  - 节点 allow 输出 {"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"}
  - 节点 allow_3ds 输出 {"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"}

## 3. 执行发布：创建 → 校验 → 生效

- ✅ 创建执行发布：`POST /decision-releases/v1/executions` → **200** `{"activatedTime":null,"checks":[],"contentHash":"eb8e6b593e11d005f097c2075968c650992e26e50b634a12a7530fe7a5f5c343","environment":"TEST","flowVersionId":"236361368536772609","pointCode":"PAYMENT_ATTEMPT_RISK","releaseId":"236361368784236544","releaseNo":"1","shadowRun":null,"status":"DRAFT","supersededTime":null,"version":1}`
- ✅ 静态校验：`POST /decision-releases/v1/executions/236361368784236544/validate` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - ✅ 校验后为 SHADOWING
- ✅ 生效：`POST /decision-releases/v1/executions/236361368784236544/activate` → **200** `{"activatedTime":"2026-10-15T05:35:41.840708Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 生效后为 ACTIVE

## 4. 评估四种结果

- ✅ 小额 → 放行、无需认证：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361369811841024","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 小额 → 放行、无需认证：result=ALLOW
  - ✅ 小额 → 放行、无需认证：认证要求=NOT_REQUIRED
- ✅ 1500 → 放行、需要 3DS：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370017361920","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"},"result":"ALLOW"}`
  - ✅ 1500 → 放行、需要 3DS：result=ALLOW
  - ✅ 1500 → 放行、需要 3DS：认证要求=THREE_DS_REQUIRED
- ✅ 6000 → 审核：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370176745472","outputs":{},"result":"REVIEW"}`
  - ✅ 6000 → 审核：result=REVIEW
  - ✅ 6000 → 审核：认证要求=null
- ✅ 黑名单卡指纹 → 拒绝：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370310963200","outputs":{},"result":"DENY"}`
  - ✅ 黑名单卡指纹 → 拒绝：result=DENY
  - ✅ 黑名单卡指纹 → 拒绝：认证要求=null
- ✅ 同一卡第 1 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370453569536","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 同一卡第 1 次（含本次 >3 拒绝）：result=ALLOW
  - ✅ 同一卡第 1 次（含本次 >3 拒绝）：认证要求=NOT_REQUIRED
- ✅ 同一卡第 2 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370617147392","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 同一卡第 2 次（含本次 >3 拒绝）：result=ALLOW
  - ✅ 同一卡第 2 次（含本次 >3 拒绝）：认证要求=NOT_REQUIRED
- ✅ 同一卡第 3 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370755559424","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 同一卡第 3 次（含本次 >3 拒绝）：result=ALLOW
  - ✅ 同一卡第 3 次（含本次 >3 拒绝）：认证要求=NOT_REQUIRED
- ✅ 同一卡第 4 次（含本次 >3 拒绝）：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361370902360064","outputs":{},"result":"DENY"}`
  - ✅ 同一卡第 4 次（含本次 >3 拒绝）：result=DENY
  - ✅ 同一卡第 4 次（含本次 >3 拒绝）：认证要求=null

## 5. 重放与参数

- ✅ 同一请求重放 → 原结果：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361369811841024","outputs":{"AUTHENTICATION_REQUIREMENT":"NOT_REQUIRED"},"result":"ALLOW"}`
  - ✅ 重放返回同一 evaluationId
- ✅ 同一支付尝试换风控 Action → 409：`POST /risk-decisions/v1/evaluations` → **409** `{"type":"about:blank","title":"Conflict","detail":"支付尝试已有不同内容的风控评估: 248173839842665","code":"CONCURRENT_REQUEST","requestId":"stage5-72bb95af-2f11-49b5-954c-32b3ebf83a0d","timestamp":"2026-10-15T05:35:42.224209Z"}`
- ✅ 金额格式非法 → 422：`POST /risk-decisions/v1/evaluations` → **422** `{"type":"about:blank","title":"Unprocessable Content","detail":"请求字段校验未通过","code":"VALIDATION_ERROR","requestId":"stage5-ef8dc5fb-56e9-4870-bf3b-55f0e9f391fe","timestamp":"2026-10-15T05:35:42.225792Z","errors":[{"field":"inputs.amount","message":"格式无效"}]}`
- ✅ 未登记风控点 → 400：`POST /risk-decisions/v1/evaluations` → **400** `{"type":"about:blank","title":"Bad Request","detail":"风控点或输入合同版本未登记: UNKNOWN_POINT","code":"INVALID_REQUEST","requestId":"stage5-8a6f632d-d41e-446f-add8-ed8bb1b63ca1","timestamp":"2026-10-15T05:35:42.227412Z"}`

## 6. 第二个发布：影子运行后替代

- ✅ 创建规则集 ACC_AUTH2_67311621：`POST /decision-rule-sets/v1/rule-sets` → **200** `{"ruleSetVersionId":"236361371036577792","pointVersionId":null,"ruleSetCode":"ACC_AUTH2_67311621","versionNo":1,"status":"DRAFT","contentHash":"b58471b8f1851755bb87719ab631ddb0b8d00b33f8110812e5f5726b1ec757a9","createdBy":"70001","createTime":"2026-10-15T05:35:42.231150Z"}`
- ✅ 冻结规则集 ACC_AUTH2_67311621：`POST /decision-rule-sets/v1/rule-sets/236361371036577792/freeze` → **200** `{"ruleSetVersionId":"236361371036577792","pointVersionId":null,"ruleSetCode":"ACC_AUTH2_67311621","versionNo":1,"status":"FROZEN","contentHash":"b58471b8f1851755bb87719ab631ddb0b8d00b33f8110812e5f5726b1ec757a9","frozenBy":"70001","frozenTime":"2026-10-15T05:35:42.248453Z"}`
- ✅ 保存新流程版本（3DS 门槛降为 50）：`POST /decision-flows/v1/flows` → **200** `{"flowId":"236361371250487296","flowVersionId":"236361371250487297","savedBy":"70001","savedTime":"2026-10-15T05:35:42.288469Z","version":"1","versionNo":1}`
- ✅ 创建执行发布：`POST /decision-releases/v1/executions` → **200** `{"activatedTime":null,"checks":[],"contentHash":"66e41968ea58b2e14299380397d04228f155212541d1bcd2a750133e239f1a74","environment":"TEST","flowVersionId":"236361371250487297","pointCode":"PAYMENT_ATTEMPT_RISK","releaseId":"236361371317596160","releaseNo":"2","shadowRun":null,"status":"DRAFT","supersededTime":null,"version":1}`
- ✅ 静态校验：`POST /decision-releases/v1/executions/236361371317596160/validate` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - ✅ 校验后为 SHADOWING
- ✅ 启动影子运行（样本 20）：`POST /decision-releases/v1/executions/236361371317596160/shadow-runs` → **200** `{"activatedTime":null,"checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"che…`
  - 影子运行结果 {"actionChangeCount":4,"allowToDenyCount":0,"allowToReviewCount":0,"baselineReleaseId":"236361368784236544","checkStatus":"PASSED","denyToAllowCount":0,"failedCount":0,"featureMissingCount":0,"p95Millis":1,"p99Millis":1,"sampleCount":8,"sampleTarget":20,"shadowRunId":"236361371581837312","status":"COMPLETED","timedOutCount":0}
  - ✅ 影子运行完成
- ✅ 生效：`POST /decision-releases/v1/executions/236361371317596160/activate` → **200** `{"activatedTime":"2026-10-15T05:35:42.938852Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 生效后为 ACTIVE
- ✅ 原发布状态：`GET /decision-releases/v1/executions/236361368784236544` → **200** `{"activatedTime":"2026-10-15T05:35:41.840708Z","checks":[{"checkCode":"REFERENCE_INTEGRITY","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_SHAPE","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"AST_TYPES","checkStatus":"PASSED","errorCode":null,"errorSummary":null},{"checkCode":"FEATURE_DEPENDENCY","checkStatus":"PASSED","errorCode":null,"…`
  - ✅ 原发布被替代
- ✅ 新发布生效后 100 → 放行、需要 3DS：`POST /risk-decisions/v1/evaluations` → **200** `{"evaluationId":"236361374131974144","outputs":{"AUTHENTICATION_REQUIREMENT":"THREE_DS_REQUIRED"},"result":"ALLOW"}`
  - ✅ 新发布生效后 100 → 放行、需要 3DS：result=ALLOW
  - ✅ 新发布生效后 100 → 放行、需要 3DS：认证要求=THREE_DS_REQUIRED
  - release1=236361368784236544 release2=236361371317596160

## 结论

全部检查通过。
