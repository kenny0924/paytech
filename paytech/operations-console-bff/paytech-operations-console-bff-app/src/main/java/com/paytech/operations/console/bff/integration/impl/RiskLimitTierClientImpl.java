package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskLimitTierClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.decision.api.control.ClientLimitTierApi;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;

import lombok.AllArgsConstructor;

/** 单次调用 Risk 限额档位接口，核对回执身份与分页；字段错误只投影到本次转发的 Body 路径。 */
@Component
@AllArgsConstructor
public class RiskLimitTierClientImpl implements RiskLimitTierClient {
    private final ClientLimitTierApi api;

    /** 分页回执的页码与条数须与请求一致。 */
    @Override
    public PagingResponseBody<ClientTierSummaryBody> list(OperatorContext o, String tier, Boolean pendingApproval,
        Boolean observationDue, String keyword, int page, int size) {
        var response = api.listClientTiers(o.accountId(), o.sessionId(), tier, pendingApproval, observationDue, keyword, page, size);
        RiskControlResponsePolicy.requirePage(response, page, size);
        return response;
    }

    /** 回执须属于请求的 Client。 */
    @Override
    public ClientTierDetailResponseBody detail(OperatorContext o, String clientId) {
        var response = api.getClientTier(o.accountId(), o.sessionId(), clientId);
        RiskControlResponsePolicy.require(response != null && clientId.equals(response.getClientId()));
        return response;
    }

    /** 历史各条须属于请求的 Client。 */
    @Override
    public ClientTierChangeListResponseBody history(OperatorContext o, String clientId) {
        var response = api.listClientTierChanges(o.accountId(), o.sessionId(), clientId);
        RiskControlResponsePolicy.require(response != null && response.getItems() != null
            && response.getItems().stream().allMatch(item -> item != null && clientId.equals(item.getClientId())));
        return response;
    }

    /** 新申请须属于请求的 Client。 */
    @Override
    public ClientTierChangeResponseBody request(OperatorContext o, String clientId, RequestClientTierChangeRequestBody body) {
        var response = validated(body, () -> api.requestClientTierChange(o.accountId(), o.sessionId(), clientId, body));
        RiskControlResponsePolicy.require(response != null && clientId.equals(response.getClientId()));
        return response;
    }

    /** 回执申请 ID 须与请求一致。 */
    @Override
    public ClientTierChangeResponseBody withdraw(OperatorContext o, String changeId, WithdrawClientTierChangeRequestBody body) {
        return sameChange(changeId, validated(body, () -> api.withdrawClientTierChange(o.accountId(), o.sessionId(), changeId, body)));
    }

    /** 按批准 / 驳回与普通 / 高级选择接口；回执申请 ID 须与请求一致。 */
    @Override
    public ClientTierChangeResponseBody decide(OperatorContext o, String changeId, boolean approve, boolean elevated,
        DecideClientTierChangeRequestBody body) {
        Supplier<ClientTierChangeResponseBody> call;
        if (approve) {
            call = elevated ? () -> api.approveClientTierChangeElevated(o.accountId(), o.sessionId(), changeId, body)
                : () -> api.approveClientTierChange(o.accountId(), o.sessionId(), changeId, body);
        } else {
            call = elevated ? () -> api.rejectClientTierChangeElevated(o.accountId(), o.sessionId(), changeId, body)
                : () -> api.rejectClientTierChange(o.accountId(), o.sessionId(), changeId, body);
        }
        return sameChange(changeId, validated(body, call));
    }

    /** 额度原样返回。 */
    @Override
    public TierQuotaListResponseBody quotas(OperatorContext o) {
        var response = api.listTierQuotas(o.accountId(), o.sessionId());
        RiskControlResponsePolicy.require(response != null && response.getTiers() != null);
        return response;
    }

    /** 统计原样返回。 */
    @Override
    public TierStatisticsResponseBody statistics(OperatorContext o) {
        var response = api.getTierStatistics(o.accountId(), o.sessionId());
        RiskControlResponsePolicy.require(response != null);
        return response;
    }

    /** 回执申请 ID 须与请求一致。 */
    static ClientTierChangeResponseBody sameChange(String changeId, ClientTierChangeResponseBody response) {
        RiskControlResponsePolicy.require(response != null && Objects.equals(changeId, response.getChangeId()));
        return response;
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
