package com.paytech.channel.connector.protocol.service.processor;

import java.util.HashMap;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyRunDetailView;
import com.paytech.channel.connector.protocol.domain.verify.VerifyRunStepView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyRunRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyRunStepRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/** V14 验证运行详情与证据：快照、配置版本、出站地址、步骤（期望核对、轨迹与脱敏报文），以及运行后是否已变更。 */
@Component
@AllArgsConstructor
public class GetVerifyRunProcessor extends AbstractProcessor<GetIntegrationChildRequest, VerifyRunDetailView> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyCaseRepository cases;
    private final IntegrationVerifyRunRepository runs;
    private final IntegrationVerifyRunStepRepository steps;
    private final IntegrationVerifyAccountRepository accounts;

    @Override
    protected VerifyRunDetailView processRequest(GetIntegrationChildRequest request) {
        var integration = children.integration(request.getIntegrationCode());
        Long runId = VerifySupport.runId(request.getCode());
        IntegrationVerifyRun run = runId == null ? null : runs.byId(runId);
        if (run != null && !run.getIntegrationId().equals(integration.getIntegrationId())) {
            run = null;
        }
        support.requireFound(run);
        JsonNode snapshot = verify.tree(run.getCaseSnapshotJson());
        IntegrationVerifyCase currentCase = cases.byCode(integration.getIntegrationId(), snapshot.path("caseCode").asString());
        if (currentCase != null && !currentCase.getVerifyCaseId().equals(run.getVerifyCaseId())) {
            currentCase = null;
        }
        boolean stale = run.getRunStatus() != VerifyRunStatus.RUNNING && verify.stale(run, currentCase,
            accounts.byId(snapshot.path("accountId").asLong()), integration.getIntegrationCode(), new HashMap<>());
        var stepViews = steps.byRun(run.getVerifyRunId()).stream().map(step -> new VerifyRunStepView(step.getStepNo(),
            step.getStepKey(), step.getStandardApiCode(), step.getStepStatus(), verify.tree(step.getResolvedInputJson()),
            verify.tree(step.getResultJson()), verify.tree(step.getAssertionsJson()), verify.tree(step.getTraceJson()),
            step.getElapsedMs())).toList();
        return new VerifyRunDetailView(run.getVerifyRunId(), snapshot.path("caseCode").asString(), run.getRunStatus(),
            run.getFailureSummary(), snapshot, verify.tree(run.getConfigVersionsJson()),
            verify.tree(run.getEndpointsJson()), stale, run.getStartedBy(), run.getStartTime(), run.getFinishTime(),
            stepViews);
    }
}
