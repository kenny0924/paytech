package com.paytech.risk.ext.rest.control;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.control.RiskListManagementApi;
import com.paytech.risk.decision.api.control.request.AddRiskListEntryRequestBody;
import com.paytech.risk.decision.api.control.request.CreateRiskListRequestBody;
import com.paytech.risk.decision.api.control.request.DeactivateRiskListEntryRequestBody;
import com.paytech.risk.decision.api.control.response.RiskListEntryResponseBody;
import com.paytech.risk.decision.api.control.response.RiskListResponseBody;
import com.paytech.risk.control.service.RiskListService;
import com.paytech.risk.control.service.processor.AddRiskListEntryProcessor;
import com.paytech.risk.control.service.processor.CreateRiskListProcessor;
import com.paytech.risk.control.service.processor.DeactivateRiskListEntryProcessor;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 只解析可信上下文和协议字段；写操作由 risk-control 的 Processor 执行，查询委托名单 Service。 */
@RestController
@AllArgsConstructor
public class RiskListManagementController implements RiskListManagementApi {
    private final RiskListService listService;
    private final CreateRiskListProcessor createProcessor;
    private final AddRiskListEntryProcessor addEntryProcessor;
    private final OperationRequestContextParser context;
    private final DeactivateRiskListEntryProcessor deactivateProcessor;

    /** 创建名单。 */
    @Override
    public RiskListResponseBody createList(String actorAccountId, String sessionId, CreateRiskListRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        return RiskListWireConverter.toResponse(
            createProcessor.process(RiskListWireConverter.toRequest(actor.getActorAccountId(), requestBody)));
    }

    /** 按编码分页查询名单。 */
    @Override
    public PagingResponseBody<RiskListResponseBody> listLists(String actorAccountId, String sessionId, Integer page,
        Integer size) {
        context.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        var result = listService.findLists(paging.size(), paging.offset());
        return new PagingResponseBody<>(result.items().stream().map(RiskListWireConverter::toResponse).toList(),
            paging.page(), paging.size(), result.total());
    }

    /** 人工添加名单成员。 */
    @Override
    public RiskListEntryResponseBody addEntry(String actorAccountId, String sessionId, String listCode,
        AddRiskListEntryRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = RiskListWireConverter.toRequest(actor.getActorAccountId(),
            context.parseRequiredUpperCode("listCode", listCode), requestBody);
        return RiskListWireConverter.toResponse(addEntryProcessor.process(request));
    }

    /** 人工失效名单成员。 */
    @Override
    public RiskListEntryResponseBody deactivateEntry(String actorAccountId, String sessionId, String listCode,
        String entryId, DeactivateRiskListEntryRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = RiskListWireConverter.toRequest(actor.getActorAccountId(),
            context.parseRequiredUpperCode("listCode", listCode), context.parsePositiveId("entryId", entryId), requestBody);
        return RiskListWireConverter.toResponse(deactivateProcessor.process(request));
    }

    /** 分页查询名单成员。 */
    @Override
    public PagingResponseBody<RiskListEntryResponseBody> listEntries(String actorAccountId, String sessionId,
        String listCode, String subjectValue, String status, Integer page, Integer size) {
        context.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        var result = listService.findEntries(context.parseRequiredUpperCode("listCode", listCode), subjectValue,
            RiskListWireConverter.parseEntryStatus(context.parseOptionalUpperCode("status", status)), paging.size(),
            paging.offset());
        return new PagingResponseBody<>(result.items().stream().map(RiskListWireConverter::toResponse).toList(),
            paging.page(), paging.size(), result.total());
    }
}
