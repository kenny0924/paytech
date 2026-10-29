package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.decision.api.control.ClientUnderwritingApi;
import com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.CreateUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.ReviseUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.SaveUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingHistoryResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingStatisticsResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;

import lombok.AllArgsConstructor;

/** 单次调用 Risk 承保接口，核对回执身份与分页；字段错误只投影到本次转发的 Body 路径。 */
@Component
@AllArgsConstructor
public class RiskUnderwritingClientImpl implements RiskUnderwritingClient {
    private final ClientUnderwritingApi api;

    /** 分页回执的页码与条数须与请求一致。 */
    @Override
    public PagingResponseBody<UnderwritingSummaryBody> list(OperatorContext o, String status, String crr,
        String decisionSource, String keyword, String clientId, String pendingReviewFor, int page, int size) {
        var response = api.listUnderwritings(o.accountId(), o.sessionId(), status, crr, decisionSource, keyword, clientId,
            pendingReviewFor, page, size);
        RiskControlResponsePolicy.requirePage(response, page, size);
        return response;
    }

    /** 回执版本 ID 须与请求一致。 */
    @Override
    public UnderwritingResponseBody detail(OperatorContext o, String underwritingId) {
        return sameVersion(underwritingId, api.getUnderwriting(o.accountId(), o.sessionId(), underwritingId));
    }

    /** 回执中的版本须属于请求的 Client。 */
    @Override
    public ClientUnderwritingCurrentResponseBody current(OperatorContext o, String clientId) {
        var response = api.getCurrentUnderwriting(o.accountId(), o.sessionId(), clientId);
        RiskControlResponsePolicy.require(response != null && ofClient(clientId, response.getEffective())
            && ofClient(clientId, response.getOpen()) && ofClient(clientId, response.getLatest()));
        return response;
    }

    /** 历史各版本须属于请求的 Client。 */
    @Override
    public UnderwritingHistoryResponseBody history(OperatorContext o, String clientId) {
        var response = api.getUnderwritingHistory(o.accountId(), o.sessionId(), clientId);
        RiskControlResponsePolicy.require(response != null && response.getItems() != null
            && response.getItems().stream().allMatch(item -> item != null && clientId.equals(item.getClientId())));
        return response;
    }

    /** 新草稿须属于请求的 Client。 */
    @Override
    public UnderwritingResponseBody create(OperatorContext o, String clientId, CreateUnderwritingDraftRequestBody body) {
        return sameClient(clientId, validated(body, () -> api.createUnderwritingDraft(o.accountId(), o.sessionId(), clientId, body)));
    }

    /** 回执版本 ID 须与请求一致。 */
    @Override
    public UnderwritingResponseBody save(OperatorContext o, String underwritingId, SaveUnderwritingDraftRequestBody body) {
        return sameVersion(underwritingId,
            validated(body, () -> api.saveUnderwritingDraft(o.accountId(), o.sessionId(), underwritingId, body)));
    }

    /** 回执版本 ID 须与请求一致。 */
    @Override
    public UnderwritingResponseBody submit(OperatorContext o, String underwritingId, SubmitUnderwritingRequestBody body) {
        return sameVersion(underwritingId,
            validated(body, () -> api.submitUnderwriting(o.accountId(), o.sessionId(), underwritingId, body)));
    }

    /** 回执版本 ID 须与请求一致。 */
    @Override
    public UnderwritingResponseBody review(OperatorContext o, String underwritingId, ReviewUnderwritingRequestBody body) {
        return sameVersion(underwritingId,
            validated(body, () -> api.reviewUnderwriting(o.accountId(), o.sessionId(), underwritingId, body)));
    }

    /** 自动承保版本须属于请求的 Client。 */
    @Override
    public UnderwritingResponseBody auto(OperatorContext o, String clientId, AutoUnderwritingRequestBody body) {
        return sameClient(clientId, validated(body, () -> api.autoUnderwrite(o.accountId(), o.sessionId(), clientId, body)));
    }

    /** 修订草稿是新版本，回执须是同一 Client 的 DRAFT 新版本（另由调用方核对 Client）。 */
    @Override
    public UnderwritingResponseBody revise(OperatorContext o, String underwritingId, ReviseUnderwritingRequestBody body) {
        var response = validated(body, () -> api.reviseUnderwriting(o.accountId(), o.sessionId(), underwritingId, body));
        RiskControlResponsePolicy.require(response != null && response.getUnderwritingId() != null
            && !underwritingId.equals(response.getUnderwritingId()));
        return response;
    }

    /** 预览须带检查清单与复核级别。 */
    @Override
    public UnderwritingPreviewResponseBody preview(OperatorContext o, UnderwritingPreviewRequestBody body) {
        var response = validated(body, () -> api.previewUnderwriting(o.accountId(), o.sessionId(), body));
        RiskControlResponsePolicy.require(response != null && response.getChecks() != null && response.getReviewLevel() != null);
        return response;
    }

    /** 统计原样返回。 */
    @Override
    public UnderwritingStatisticsResponseBody statistics(OperatorContext o) {
        var response = api.getUnderwritingStatistics(o.accountId(), o.sessionId());
        RiskControlResponsePolicy.require(response != null);
        return response;
    }

    /** 回执版本 ID 须与请求一致。 */
    static UnderwritingResponseBody sameVersion(String underwritingId, UnderwritingResponseBody response) {
        RiskControlResponsePolicy.require(response != null && Objects.equals(underwritingId, response.getUnderwritingId()));
        return response;
    }

    /** 回执须属于请求的 Client。 */
    static UnderwritingResponseBody sameClient(String clientId, UnderwritingResponseBody response) {
        RiskControlResponsePolicy.require(response != null && Objects.equals(clientId, response.getClientId()));
        return response;
    }

    /** 可空版本须属于请求的 Client。 */
    static boolean ofClient(String clientId, UnderwritingResponseBody version) {
        return version == null || clientId.equals(version.getClientId());
    }

    /** Owner 的字段校验错误只投影到本次转发 Body 存在的路径。 */
    static <T> T validated(Object body, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
    }
}
