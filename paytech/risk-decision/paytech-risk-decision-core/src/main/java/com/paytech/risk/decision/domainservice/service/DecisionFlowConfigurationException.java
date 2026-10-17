package com.paytech.risk.decision.domainservice.service;

import java.util.List;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Issue;

/** 配置业务失败；Web 按明确原因映射 HTTP，禁止解析错误文案。 */
public final class DecisionFlowConfigurationException extends BizCheckFailException {
    public enum Reason implements CodeEnum {
        CODE_EXISTS,

        VERSION_CONFLICT,

        DELETED,

        REFERENCE_UNAVAILABLE,

        INVALID_GRAPH;

        @Override
        public String getCode() {
            return switch (this) {
                case CODE_EXISTS -> "CODE_EXISTS";
                case VERSION_CONFLICT -> "VERSION_CONFLICT";
                case DELETED -> "DELETED";
                case REFERENCE_UNAVAILABLE -> "REFERENCE_UNAVAILABLE";
                case INVALID_GRAPH -> "INVALID_GRAPH";
            };
        }
    }

    private final Reason reason;
    private final List<Issue> issues;

    public DecisionFlowConfigurationException(Reason reason, String message, List<Issue> issues) {
        super(message);
        this.reason = reason;
        this.issues = List.copyOf(issues);
    }

    public Reason reason() { return reason; }

    public List<Issue> issues() { return issues; }
}
