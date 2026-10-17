package com.paytech.platform.support.integration.service.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.VerifyRunStatus;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.verify.VerifyCaseView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyRunRepository;
import com.paytech.platform.support.integration.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * V06 验证用例列表：分页读取用例，附最近一次运行与验证状态（沙箱验证技术方案第 5 节）。
 *
 * <p>验证状态由运行时记录的配置版本与当前版本比较得出；同一请求内每个标准 API 只加载一次当前配置。普通列表不开显式事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListVerifyCasesProcessor
    extends AbstractProcessor<ListIntegrationChildrenRequest, PagingResponseBody<VerifyCaseView>> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final IntegrationVerifyCaseRepository cases;
    private final IntegrationVerifyRunRepository runs;
    private final IntegrationVerifyAccountRepository accounts;
    private final OperationsManagementPolicyService policy;

    @Override
    protected PagingResponseBody<VerifyCaseView> processRequest(ListIntegrationChildrenRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        var integration = children.integration(request.getIntegrationCode());
        long integrationId = integration.getIntegrationId();
        var paging = request.getPaging();
        List<IntegrationVerifyCase> rows = cases.list(integrationId, request.getKeyword(), paging.offset(), paging.size());
        long total = cases.count(integrationId, request.getKeyword());
        var caseIds = rows.stream().map(IntegrationVerifyCase::getVerifyCaseId).toList();
        Map<Long, IntegrationVerifyRun> latest = runs.latestByCases(caseIds);
        Map<Long, IntegrationVerifyRun> finished = runs.latestFinishedByCases(caseIds);
        Map<Long, IntegrationVerifyAccount> accountById = accounts.allByIntegration(integrationId).stream()
            .collect(Collectors.toMap(IntegrationVerifyAccount::getVerifyAccountId, Function.identity()));
        Map<String, JsonNode> current = new HashMap<>();
        var items = rows.stream().map(row -> {
            IntegrationVerifyAccount account = accountById.get(row.getVerifyAccountId());
            IntegrationVerifyRun done = finished.get(row.getVerifyCaseId());
            boolean stale = done != null && done.getRunStatus() == VerifyRunStatus.PASSED
                && verify.stale(done, row, account, integration.getIntegrationCode(), current);
            return verify.caseView(row, account == null ? null : account.getAccountCode(),
                VerifySupport.status(latest.get(row.getVerifyCaseId()), done, stale), latest.get(row.getVerifyCaseId()));
        }).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
