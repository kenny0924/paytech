package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.service.convert.DecisionFlowExceptionConverter.mapped;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.DecisionFlowManagementApi;
import com.paytech.risk.decision.api.DecisionFlowQueryApi;
import com.paytech.risk.decision.api.request.SaveFlowRequestBody;
import com.paytech.risk.decision.api.response.*;
import com.paytech.risk.decision.service.processor.*;
import com.paytech.risk.decision.service.processor.request.*;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 私网流程配置 HTTP 入口；先解析可信操作者，查询与写入分别由 Owner Processor 编排。 */
@RestController
@AllArgsConstructor
public class DecisionFlowController implements DecisionFlowQueryApi, DecisionFlowManagementApi {
    private final FlowListProcessor listProcessor;
    private final SaveFlowProcessor saveProcessor;
    private final DeleteFlowProcessor deleteProcessor;
    private final FlowDetailProcessor detailProcessor;
    private final FlowHistoryProcessor historyProcessor;
    private final ValidateFlowProcessor validateProcessor;
    private final LegacyPointFlowProcessor legacyProcessor;
    private final FlowAuthoringContextProcessor authoringProcessor;
    private final FlowRuleSetReferenceProcessor referenceProcessor;
    private final OperationRequestContextParser context;

    /** 读取流程编排能力，决策点按规范大写编码解析。 */
    @Override
    public FlowAuthoringContextResponseBody authoringContext(String actorAccountId, String sessionId, String pointCode) {
        context.parse(actorAccountId, sessionId);
        var request = new FlowAuthoringContextRequest(context.parseOptionalUpperCode("pointCode", pointCode));
        return DecisionFlowWireConverter.authoring(mapped(() -> authoringProcessor.process(request)));
    }

    /** 分页查询流程，筛选编码按规范大写编码解析。 */
    @Override
    public PagingResponseBody<FlowListItemResponseBody> list(String actorAccountId, String sessionId, String pointCode,
        String flowCode, String keyword, Integer page, Integer size) {
        context.parse(actorAccountId, sessionId);
        var request = new FlowListRequest(context.parseOptionalUpperCode("pointCode", pointCode),
            context.parseOptionalUpperCode("flowCode", flowCode), keyword, page, size);
        var result = mapped(() -> listProcessor.process(request));
        return new PagingResponseBody<>(result.items().stream().map(DecisionFlowWireConverter::summary).toList(),
            result.page(), result.size(), result.total());
    }

    /** 读取流程当前版本详情。 */
    @Override
    public FlowDetailResponseBody current(String actorAccountId, String sessionId, String flowId) {
        context.parse(actorAccountId, sessionId);
        var request = new FlowDetailRequest(context.parsePositiveId("flowId", flowId), null);
        return DecisionFlowWireConverter.detail(mapped(() -> detailProcessor.process(request)));
    }

    /** 读取流程指定历史版本的原样详情。 */
    @Override
    public FlowDetailResponseBody version(String actorAccountId, String sessionId, String flowId, String flowVersionId) {
        context.parse(actorAccountId, sessionId);
        var request = new FlowDetailRequest(context.parsePositiveId("flowId", flowId),
            context.parsePositiveId("flowVersionId", flowVersionId));
        return DecisionFlowWireConverter.detail(mapped(() -> detailProcessor.process(request)));
    }

    /** 分页查询流程历史版本。 */
    @Override
    public PagingResponseBody<FlowHistoryItemResponseBody> history(String actorAccountId, String sessionId, String flowId,
        Integer maxVersionNo, Integer page, Integer size) {
        context.parse(actorAccountId, sessionId);
        var request = new FlowHistoryRequest(context.parsePositiveId("flowId", flowId), maxVersionNo, page, size);
        var result = mapped(() -> historyProcessor.process(request));
        return new PagingResponseBody<>(result.items().stream().map(DecisionFlowWireConverter::history).toList(),
            result.page(), result.size(), result.total());
    }

    /** 按真实来源引用读取流程节点引用的规则集准确版本。 */
    @Override
    public RuleSetReferenceResponseBody ruleSetReference(String actorAccountId, String sessionId, String sourceType,
        String sourceVersionId, String nodeKey) {
        context.parse(actorAccountId, sessionId);
        var request = new FlowRuleSetReferenceRequest(sourceType,
            context.parsePositiveId("sourceVersionId", sourceVersionId), nodeKey);
        return DecisionFlowWireConverter.storedReference(mapped(() -> referenceProcessor.process(request)));
    }

    /** 读取旧事件点版本的兼容投影，仅供历史对照。 */
    @Override
    public LegacyPointResponseBody legacyPoint(String actorAccountId, String sessionId, String pointVersionId) {
        context.parse(actorAccountId, sessionId);
        var request = new LegacyPointFlowRequest(context.parsePositiveId("pointVersionId", pointVersionId));
        return DecisionFlowWireConverter.legacy(mapped(() -> legacyProcessor.process(request)));
    }

    /** 只转换可信身份和配置字段，业务校验、锁及本地保存由 Owner 负责。 */
    @Override
    public SaveFlowResponseBody save(String actorAccountId, String sessionId, SaveFlowRequestBody body) {
        var caller = context.parse(actorAccountId, sessionId);
        var request = new SaveFlowRequest(DecisionFlowWireConverter.command(body), caller.getActorAccountId());
        return DecisionFlowWireConverter.saved(mapped(() -> saveProcessor.process(request)));
    }

    /** 保留只读静态问题报告，传输绑定失败仍由 HTTP 基础设施处理。 */
    @Override
    public ValidateFlowResponseBody validate(String actorAccountId, String sessionId, SaveFlowRequestBody body) {
        context.parse(actorAccountId, sessionId);
        var request = new ValidateFlowRequest(DecisionFlowWireConverter.command(body));
        return DecisionFlowWireConverter.validation(mapped(() -> validateProcessor.process(request)));
    }

    /** 传递原流程身份和期望版本，不再解析或转发 HTTP 请求幂等键。 */
    @Override
    public DeleteFlowResponseBody delete(String actorAccountId, String sessionId, String flowId,
        String expectedVersion) {
        var caller = context.parse(actorAccountId, sessionId);
        var request = new DeleteFlowRequest(context.parsePositiveId("flowId", flowId),
            context.parsePositiveId("expectedVersion", expectedVersion), caller.getActorAccountId());
        return DecisionFlowWireConverter.deleted(mapped(() -> deleteProcessor.process(request)));
    }
}
