package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskListClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklist.AddRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.CreateRiskListRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.DeactivateRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListEntryResponseBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListResponseBody;
import com.paytech.risk.api.control.RiskListManagementApi;

import lombok.AllArgsConstructor;

/** 单次调用 Risk 名单接口，核对回执身份与分页后转换为页面模型；不重试、不记录主体值。 */
@Component
@AllArgsConstructor
public class RiskListClientImpl implements RiskListClient {
    private final RiskListManagementApi api;

    /** 新建名单；回执编码必须与请求一致。 */
    @Override
    public RiskListResponseBody createList(OperatorContext context, CreateRiskListRequestBody body) {
        var response = validated(body, () -> api.createList(context.accountId(), context.sessionId(), RiskControlConverter.owner(body)));
        RiskControlResponsePolicy.require(response != null && Objects.equals(body.getListCode(), response.getListCode()));
        return RiskControlConverter.local(response);
    }

    /** 名单分页；页码与条数必须与请求一致。 */
    @Override
    public PagingResponseBody<RiskListResponseBody> listLists(OperatorContext context, int page, int size) {
        var response = api.listLists(context.accountId(), context.sessionId(), page, size);
        RiskControlResponsePolicy.requirePage(response, page, size);
        return response.map(RiskControlConverter::local);
    }

    /** 加入成员；回执必须带脱敏键。 */
    @Override
    public RiskListEntryResponseBody addEntry(OperatorContext context, String listCode, AddRiskListEntryRequestBody body) {
        var response = validated(body, () -> api.addEntry(context.accountId(), context.sessionId(), listCode, RiskControlConverter.owner(body)));
        RiskControlResponsePolicy.require(response != null && response.getMaskedSubjectKey() != null);
        return RiskControlConverter.local(response);
    }

    /** 失效成员；回执成员 ID 必须与请求一致。 */
    @Override
    public RiskListEntryResponseBody deactivateEntry(OperatorContext context, String listCode, String entryId,
        DeactivateRiskListEntryRequestBody body) {
        var response = validated(body,
            () -> api.deactivateEntry(context.accountId(), context.sessionId(), listCode, entryId, RiskControlConverter.owner(body)));
        RiskControlResponsePolicy.require(response != null && Objects.equals(entryId, response.getEntryId()));
        return RiskControlConverter.local(response);
    }

    /** 成员分页；同一页的成员必须属于同一名单。 */
    @Override
    public PagingResponseBody<RiskListEntryResponseBody> listEntries(OperatorContext context, String listCode,
        String subjectValue, String status, int page, int size) {
        var response = api.listEntries(context.accountId(), context.sessionId(), listCode, subjectValue, status, page, size);
        RiskControlResponsePolicy.requirePage(response, page, size);
        RiskControlResponsePolicy.require(response.items().stream().map(item -> item.getListId()).distinct().count() <= 1);
        if (status != null) {
            RiskControlResponsePolicy.require(response.items().stream().allMatch(item -> status.equals(item.getStatus())));
        }
        return response.map(RiskControlConverter::local);
    }

    /** Owner 的字段校验错误只投影到本次页面 Body 存在的路径。 */
    private static <T> T validated(Object body, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
    }
}
