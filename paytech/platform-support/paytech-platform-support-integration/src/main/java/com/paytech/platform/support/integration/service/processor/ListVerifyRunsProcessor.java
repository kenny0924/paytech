package com.paytech.platform.support.integration.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.verify.VerifyRunSummaryView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyRunRepository;
import com.paytech.platform.support.integration.service.processor.request.ListVerifyRunsRequest;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/** V13 验证运行列表：按开始时间倒序，可按用例筛选；用例编码与名称取自运行时快照。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListVerifyRunsProcessor
    extends AbstractProcessor<ListVerifyRunsRequest, PagingResponseBody<VerifyRunSummaryView>> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyCaseRepository cases;
    private final IntegrationVerifyRunRepository runs;
    private final OperationsManagementPolicyService policy;

    @Override
    protected PagingResponseBody<VerifyRunSummaryView> processRequest(ListVerifyRunsRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
        Long caseId = request.getCaseCode() == null ? null
            : support.requireFound(cases.byCode(integrationId, request.getCaseCode())).getVerifyCaseId();
        var paging = request.getPaging();
        var items = runs.list(integrationId, caseId, paging.offset(), paging.size()).stream().map(this::summary)
            .toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), runs.count(integrationId, caseId));
    }

    VerifyRunSummaryView summary(IntegrationVerifyRun run) {
        JsonNode snapshot = verify.tree(run.getCaseSnapshotJson());
        return new VerifyRunSummaryView(run.getVerifyRunId(), snapshot.path("caseCode").asString(),
            snapshot.path("caseName").asString(), run.getRunStatus(), run.getFailureSummary(), run.getStartedBy(),
            run.getStartTime(), run.getFinishTime());
    }
}
