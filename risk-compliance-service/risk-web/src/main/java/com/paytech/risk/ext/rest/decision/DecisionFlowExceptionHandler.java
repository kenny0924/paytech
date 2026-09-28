package com.paytech.risk.ext.rest.decision;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.web.error.MappedProblem;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 仅扩展本期图业务错误分类，仍使用 Foundation 的唯一 Problem Writer。 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice(assignableTypes = DecisionFlowController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DecisionFlowExceptionHandler {
    private final ProblemResponseWriter writer;

    public DecisionFlowExceptionHandler(ProblemResponseWriter writer) { this.writer = writer; }

    @ExceptionHandler(DecisionFlowConfigurationException.class)
    public void handle(DecisionFlowConfigurationException failure, HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        CodeMessageEnum code = switch (failure.reason()) {
            case CODE_EXISTS -> RiskDecisionConfigurationErrorCode.RISK_FLOW_CODE_EXISTS;
            case VERSION_CONFLICT -> RiskDecisionConfigurationErrorCode.RISK_FLOW_VERSION_CONFLICT;
            case DELETED -> RiskDecisionConfigurationErrorCode.RISK_FLOW_DELETED;
            case REFERENCE_UNAVAILABLE -> RiskDecisionConfigurationErrorCode.RISK_FLOW_REFERENCE_UNAVAILABLE;
            case INVALID_GRAPH -> CommonErrorCode.VALIDATION_ERROR;
        };
        HttpStatus status = switch (failure.reason()) {
            case INVALID_GRAPH, REFERENCE_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.CONFLICT;
        };
        List<ProblemFieldError> errors = failure.reason() == DecisionFlowConfigurationException.Reason.INVALID_GRAPH
            ? failure.issues().stream().filter(i -> i.field() != null)
                .map(i -> new ProblemFieldError(i.field(), i.message())).distinct().limit(100).toList() : List.of();
        writer.write(request, response, new MappedProblem(status, code, failure.getMessage(), errors, HttpHeaders.EMPTY));
    }
}
