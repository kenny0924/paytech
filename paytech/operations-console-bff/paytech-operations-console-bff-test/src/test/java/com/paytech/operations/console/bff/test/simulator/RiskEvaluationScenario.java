package com.paytech.operations.console.bff.test.simulator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.Request;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.SemanticFailure;
import com.paytech.risk.decision.api.evaluation.response.EvaluationExplanationResponseBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluationResponseBody;

/** 评估查询的外部 Risk 场景：按测试登记的结论与解释 DTO 应答，参数校验与 404 按 Risk 合同返回 Problem。 */
final class RiskEvaluationScenario {
    static final String ROOT = "/risk-decisions/v1/evaluations";
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final Set<String> POINTS = Set.of("PAYMENT_ATTEMPT_RISK");
    private final Map<String, EvaluationResponseBody> evaluations = new LinkedHashMap<>();
    private final Map<String, EvaluationExplanationResponseBody> explanations = new LinkedHashMap<>();

    /** 清空登记的评估。 */
    void reset() { evaluations.clear(); explanations.clear(); }

    /** 登记一条评估的结论与解释。 */
    void add(EvaluationResponseBody evaluation, EvaluationExplanationResponseBody explanation) {
        evaluations.put(evaluation.getEvaluationId(), evaluation);
        explanations.put(evaluation.getEvaluationId(), explanation);
    }

    /** 只接受三个 GET 查询路径。 */
    boolean known(String method, String path) {
        return method.equals("GET") && (path.equals(ROOT) || path.matches(ROOT + "/[^/]+") || path.matches(ROOT + "/[^/]+/explanation"));
    }

    /** 按 Risk 合同应答：ID 格式不符或参数缺失 400，不存在 404。 */
    Object dispatch(Request request) {
        if (request.path().equals(ROOT)) {
            return find(RiskDecisionConfigurationSimulator.query(request.query()));
        }
        String id = request.path().split("/")[4];
        if (!id.matches(ID)) {
            throw new SemanticFailure(400, "INVALID_REQUEST");
        }
        var found = request.path().endsWith("/explanation") ? explanations.get(id) : evaluations.get(id);
        if (found == null) {
            throw new SemanticFailure(404, "NOT_FOUND");
        }
        return found;
    }

    /** 按风控点与业务身份查正式评估。 */
    private EvaluationResponseBody find(Map<String, String> query) {
        String pointCode = query.get("pointCode"), businessKey = query.get("businessKey");
        if (!query.keySet().equals(Set.of("pointCode", "businessKey")) || !POINTS.contains(pointCode) || !businessKey.matches(ID)) {
            throw new SemanticFailure(400, "INVALID_REQUEST");
        }
        return evaluations.values().stream().filter(e -> e.getMode().equals("PRIMARY") && e.getPointCode().equals(pointCode)
            && e.getBusinessKey().equals(businessKey)).findFirst().orElseThrow(() -> new SemanticFailure(404, "NOT_FOUND"));
    }
}
