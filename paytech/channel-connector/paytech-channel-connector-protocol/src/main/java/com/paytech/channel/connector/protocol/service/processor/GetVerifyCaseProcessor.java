package com.paytech.channel.connector.protocol.service.processor;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyCaseView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyRunRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** V07 验证用例详情，附最近一次运行与验证状态。 */
@Component
@AllArgsConstructor
public class GetVerifyCaseProcessor extends AbstractProcessor<GetIntegrationChildRequest, VerifyCaseView> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyCaseRepository cases;
    private final IntegrationVerifyRunRepository runs;
    private final IntegrationVerifyAccountRepository accounts;

    @Override
    protected VerifyCaseView processRequest(GetIntegrationChildRequest request) {
        var integration = children.integration(request.getIntegrationCode());
        IntegrationVerifyCase row = support.requireFound(cases.byCode(integration.getIntegrationId(), request.getCode()));
        IntegrationVerifyAccount account = accounts.byId(row.getVerifyAccountId());
        var ids = List.of(row.getVerifyCaseId());
        IntegrationVerifyRun latest = runs.latestByCases(ids).get(row.getVerifyCaseId());
        IntegrationVerifyRun done = runs.latestFinishedByCases(ids).get(row.getVerifyCaseId());
        boolean stale = done != null && done.getRunStatus() == VerifyRunStatus.PASSED
            && verify.stale(done, row, account, integration.getIntegrationCode(), new HashMap<>());
        return verify.caseView(row, account == null ? null : account.getAccountCode(),
            VerifySupport.status(latest, done, stale), latest);
    }
}
