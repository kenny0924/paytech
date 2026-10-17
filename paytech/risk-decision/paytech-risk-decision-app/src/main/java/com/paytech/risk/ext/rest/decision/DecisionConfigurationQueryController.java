package com.paytech.risk.ext.rest.decision;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.DecisionConfigurationQueryApi;
import com.paytech.risk.api.decision.response.RuleSetHistoryItemResponseBody;
import com.paytech.risk.api.decision.response.RuleSetReleaseReferencesResponseBody;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.service.processor.RuleSetHistoryProcessor;
import com.paytech.risk.decision.service.processor.RuleSetReleaseReferencesProcessor;
import com.paytech.risk.decision.service.processor.request.RuleSetHistoryRequest;
import com.paytech.risk.decision.service.processor.request.RuleSetReleaseReferencesRequest;
import com.paytech.risk.service.request.RiskOperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 适配可信身份、协议参数及响应，委托 Decision 模块执行查询。 */
@RestController
@AllArgsConstructor
public class DecisionConfigurationQueryController implements DecisionConfigurationQueryApi {
    private final RuleSetHistoryProcessor historyProcessor;
    private final RiskOperationRequestContextParser parser;
    private final RuleSetReleaseReferencesProcessor releaseReferencesProcessor;

    /** 解析可信身份及根 ID，委托历史查询；将领域未找到映射为既有 404 合同并保留原因。 */
    @Override
    public PagingResponseBody<RuleSetHistoryItemResponseBody> history(String actorAccountId, String sessionId,
        String ruleSetId,
        Integer maxVersionNo, Integer page, Integer size) {
        parser.parse(actorAccountId, sessionId);
        var request =
            new RuleSetHistoryRequest(parser.parsePositiveId("ruleSetId", ruleSetId), maxVersionNo, page, size);
        try {
            return DecisionConfigurationQueryWireConverter.history(historyProcessor.process(request));
        } catch (DecisionConfigurationNotFoundException failure) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null,
                failure);
        }
    }
    /** 解析可信身份及发布筛选，委托整体规则集引用查询。 */
    @Override
    public RuleSetReleaseReferencesResponseBody releases(String actorAccountId, String sessionId, String ruleSetId,
        Integer page, Integer size, String environmentCode, String status, String ruleSetVersionId) {
        parser.parse(actorAccountId, sessionId);
        var request = new RuleSetReleaseReferencesRequest(parser.parsePositiveId("ruleSetId", ruleSetId), page, size,
            parse(environmentCode, DecisionEnvironment.values()), parse(status, DecisionReleaseStatus.values()),
            ruleSetVersionId == null ? null : parser.parsePositiveId("ruleSetVersionId", ruleSetVersionId));
        try {
            return DecisionConfigurationQueryWireConverter.releases(releaseReferencesProcessor.process(request));
        } catch (DecisionConfigurationNotFoundException failure) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null,
                failure);
        }
    }
    /** 解析可选枚举筛选，仅接受协议声明的稳定代码。 */
    private static <E extends Enum<E> & CodeEnum> E parse(String value, E[] values) {
        if (value == null) return null;
        for (E candidate : values) if (candidate.getCode().equals(value)) return candidate;
        throw new InvalidParameterException("发布引用筛选值无效");
    }
}
