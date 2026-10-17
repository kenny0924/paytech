package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.service.convert.DecisionReleaseExceptionConverter.mapped;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.api.decision.request.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.request.ValidateTestReleaseRequestBody;
import com.paytech.risk.api.decision.response.CreateTestReleaseResponseBody;
import com.paytech.risk.api.decision.response.TestReleaseDetailResponseBody;
import com.paytech.risk.api.decision.response.TestReleaseListItemResponseBody;
import com.paytech.risk.api.decision.response.ValidateTestReleaseResponseBody;
import com.paytech.risk.decision.service.processor.CreateTestReleaseProcessor;
import com.paytech.risk.decision.service.processor.GetTestReleaseProcessor;
import com.paytech.risk.decision.service.processor.ListTestReleasesProcessor;
import com.paytech.risk.decision.service.processor.ValidateTestReleaseProcessor;
import com.paytech.risk.decision.service.processor.request.CreateTestReleaseRequest;
import com.paytech.risk.decision.service.processor.request.GetTestReleaseRequest;
import com.paytech.risk.decision.service.processor.request.ListTestReleasesRequest;
import com.paytech.risk.decision.service.processor.request.ValidateTestReleaseRequest;
import com.paytech.risk.service.request.RiskOperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 只解析可信上下文和协议字段，各用例由 risk-decision 的 Owner Processor 执行。 */
@RestController
@AllArgsConstructor
public class DecisionReleaseManagementController implements DecisionReleaseManagementApi {
    private final GetTestReleaseProcessor detailProcessor;
    private final ListTestReleasesProcessor listProcessor;
    private final CreateTestReleaseProcessor createProcessor;
    private final ValidateTestReleaseProcessor validateProcessor;
    private final RiskOperationRequestContextParser context;

    /** 分页列表沿用原筛选和默认分页规则。 */
    @Override
    public PagingResponseBody<TestReleaseListItemResponseBody> listCandidates(String actorAccountId, String sessionId,
        Integer page, Integer size, String pointCode, String status) {
        context.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        var request = new ListTestReleasesRequest(paging.page(), paging.size(),
            context.parseOptionalUpperCode("pointCode", pointCode),
            DecisionReleaseWireConverter.parseReleaseStatus(context.parseOptionalUpperCode("status", status)));
        return DecisionReleaseWireConverter.toResponse(mapped(() -> listProcessor.process(request)));
    }

    /** 查询准确发布及其完整冻结组成。 */
    @Override
    public TestReleaseDetailResponseBody getCandidate(String actorAccountId, String sessionId, String releaseId) {
        context.parse(actorAccountId, sessionId);
        var request = new GetTestReleaseRequest(context.parsePositiveId("releaseId", releaseId));
        return DecisionReleaseWireConverter.toResponse(mapped(() -> detailProcessor.process(request)));
    }

    /** 每次有效调用创建一个新 Candidate；HTTP 请求幂等由 Gateway 处理。 */
    @Override
    public CreateTestReleaseResponseBody createCandidate(String actorAccountId, String sessionId,
        CreateTestReleaseRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new CreateTestReleaseRequest(actor.actorAccountId(),
            context.parsePositiveId("ruleSetVersionId", requestBody.getRuleSetVersionId()),
            requestBody.getExpectedRuleSetContentHash());
        return DecisionReleaseWireConverter.toResponse(mapped(() -> createProcessor.process(request)));
    }

    /** 根据发布身份与原校验输入开始、恢复或返回已经完成的结果。 */
    @Override
    public ValidateTestReleaseResponseBody validateCandidate(String actorAccountId, String sessionId, String releaseId,
        ValidateTestReleaseRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new ValidateTestReleaseRequest(actor.actorAccountId(),
            context.parsePositiveId("releaseId", releaseId), requestBody.getExpectedVersion());
        return DecisionReleaseWireConverter.toResponse(mapped(() -> validateProcessor.process(request)));
    }
}
