# mor-test 验收证据（经临时代理，真实 HTTP）

## 1. 现有评估（部署前写入，无路径）

- ✅ 按 ID 查 ALLOW 评估 → 200、结论一致、pathAvailable=false
- ✅ 按支付尝试查与按 ID 一致
- ✅ 放行输出取已保存认证要求 NOT_REQUIRED
- ✅ 解释 → 200、路径为空、规则与变量照常
- ✅ 按 ID 查 ALLOW 评估 → 200、结论一致、pathAvailable=false
- ✅ 按支付尝试查与按 ID 一致
- ✅ 放行输出取已保存认证要求 THREE_DS_REQUIRED
- ✅ 解释 → 200、路径为空、规则与变量照常
- ✅ 按 ID 查 REVIEW 评估 → 200、结论一致、pathAvailable=false
- ✅ 按支付尝试查与按 ID 一致
- ✅ 解释 → 200、路径为空、规则与变量照常
- ✅ 按 ID 查 DENY 评估 → 200、结论一致、pathAvailable=false
- ✅ 按支付尝试查与按 ID 一致
- ✅ 解释 → 200、路径为空、规则与变量照常

## 2. 合成评估（对联调基线发布）

- ✅ 金额 100 评估 → ALLOW
- ✅ 按 ID 查：PRIMARY、有路径、结论与输出一致
- ✅ 按支付尝试查与按 ID 一致
- ✅ 解释：有路径且止于结束节点、结论一致
- ✅ 解释：执行过的规则、变量、输入原值
  - 路径 start → block → auth → allow；规则 ['BLOCKED_CARD', 'CARD_FREQUENCY', 'LARGE_AMOUNT', 'THREE_DS_AMOUNT']；原因码 None
- ✅ 金额 600 评估 → ALLOW
- ✅ 按 ID 查：PRIMARY、有路径、结论与输出一致
- ✅ 按支付尝试查与按 ID 一致
- ✅ 解释：有路径且止于结束节点、结论一致
- ✅ 解释：执行过的规则、变量、输入原值
  - 路径 start → block → auth → allow_3ds；规则 ['BLOCKED_CARD', 'CARD_FREQUENCY', 'LARGE_AMOUNT', 'THREE_DS_AMOUNT']；原因码 None
- ✅ 金额 6000 评估 → REVIEW
- ✅ 按 ID 查：PRIMARY、有路径、结论与输出一致
- ✅ 按支付尝试查与按 ID 一致
- ✅ 解释：有路径且止于结束节点、结论一致
- ✅ 解释：执行过的规则、变量、输入原值
  - 路径 start → block → review；规则 ['BLOCKED_CARD', 'CARD_FREQUENCY', 'LARGE_AMOUNT']；原因码 None

## 3. 错误

- ✅ GET /risk-decisions/v1/evaluations/123456789 → 404 NOT_FOUND
- ✅ GET /risk-decisions/v1/evaluations?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=123456789 → 404 NOT_FOUND
- ✅ GET /risk-decisions/v1/evaluations/abc → 400 INVALID_REQUEST
- ✅ GET /risk-decisions/v1/evaluations/1?x=1 → 400 INVALID_REQUEST
- ✅ GET /risk-decisions/v1/evaluations?pointCode=UNKNOWN_POINT&businessKey=1 → 400 INVALID_REQUEST

## 结论

全部检查通过。
