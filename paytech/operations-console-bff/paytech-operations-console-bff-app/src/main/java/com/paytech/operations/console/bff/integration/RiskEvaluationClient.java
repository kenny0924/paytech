package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationResponseBody;

/** Risk 评估查询的出站端口：按评估 ID 或业务身份查结论、查解释并组装条件树。 */
public interface RiskEvaluationClient {
    /** 按评估 ID 查询结论；回执评估 ID 必须与请求一致。 */
    RiskEvaluationResponseBody getEvaluation(String evaluationId);

    /** 按风控点与业务身份查询正式评估结论；回执风控点、业务身份一致且为正式评估。 */
    RiskEvaluationResponseBody findEvaluation(String pointCode, String businessKey);

    /**
     * 查询评估解释，并为每条执行过的规则组装条件树
     *
     * @param context 操作者身份，用于读取规则集版本详情
     * @param evaluationId 评估 ID
     * @return 解释页面模型；某规则集详情不可用时仅该规则的条件不可用
     */
    RiskEvaluationExplanationResponseBody getExplanation(OperatorContext context, String evaluationId);
}
