package com.paytech.operations.console.bff.ext.rest;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.RiskEvaluationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 风控评估查询的浏览器入口：按评估 ID 或业务身份查结论、查解释
 * <li>全部只读，由 Gateway 绑定 RISK_EVALUATION_READ 并经 PLT-I02 实时授权；BFF 只做 Query 白名单与格式校验。</li>
 * <li>解释含受限输入原值，本路径前缀只记元数据日志。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/risk-decisions/v1/evaluations")
public class RiskEvaluationController {
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String ID = "[1-9][0-9]{0,18}";

    private final RiskEvaluationClient client;

    /** 按评估 ID 查结论（正式与影子评估均可）。 */
    @GetMapping("/{evaluationId}")
    public RiskEvaluationResponseBody detail(HttpServletRequest servletRequest,
        @PathVariable("evaluationId") @Pattern(regexp = ID) String evaluationId) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.getEvaluation(evaluationId);
    }

    /** 按风控点与业务身份查正式评估结论；业务身份格式由 Risk 按该点合同校验。 */
    @GetMapping
    public RiskEvaluationResponseBody find(HttpServletRequest servletRequest,
        @RequestParam("pointCode") @Pattern(regexp = CODE) String pointCode,
        @RequestParam("businessKey") String businessKey) {
        RiskListController.requireQueryKeys(servletRequest, Set.of("pointCode", "businessKey"));
        return client.findEvaluation(pointCode, businessKey);
    }

    /** 评估解释，规则附带条件树。 */
    @GetMapping("/{evaluationId}/explanation")
    public RiskEvaluationExplanationResponseBody explanation(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("evaluationId") @Pattern(regexp = ID) String evaluationId) {
        RiskListController.requireQueryKeys(servletRequest, Set.of());
        return client.getExplanation(context, evaluationId);
    }
}
