package com.paytech.platform.support.integration.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.VerifyRunStatus;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.verify.VerifyRunBriefView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationVerifyRunService;
import com.paytech.platform.support.integration.service.processor.request.StartVerifyRunRequest;

import lombok.AllArgsConstructor;

/**
 * V12 发起沙箱验证运行（沙箱验证技术方案第 3.6 节）：一个本地事务中冻结用例快照（含运行原因，不含凭据）、插入 RUNNING 运行并登记补偿任务，
 * 提交后由补偿执行器在事务外执行；本入口不调用外部服务。同一用例可并发发起，订单号等由模板变量区分。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class StartVerifyRunProcessor extends AbstractProcessor<StartVerifyRunRequest, VerifyRunBriefView> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyCaseRepository cases;
    private final IntegrationVerifyAccountRepository accounts;
    private final OperationsManagementPolicyService policy;
    private final IntegrationVerifyRunService service;

    @Override
    protected VerifyRunBriefView processRequest(StartVerifyRunRequest request) {
        return transactions.write(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.MANAGE);
            var integration = children.integration(request.getIntegrationCode());
            IntegrationVerifyCase verifyCase =
                support.requireFound(cases.byCode(integration.getIntegrationId(), request.getCaseCode()));
            IntegrationVerifyAccount account = accounts.byId(verifyCase.getVerifyAccountId());
            if (account == null) {
                throw new BizCheckFailException("验证用例引用的验证账号不存在：" + verifyCase.getCaseCode());
            }
            var now = support.now();
            var run = new IntegrationVerifyRun(support.nextId(), verifyCase.getVerifyCaseId(),
                integration.getIntegrationId(), VerifyRunStatus.RUNNING,
                verify.caseSnapshot(verifyCase, account, integration.getIntegrationCode(), request.getReason()), null, null, null,
                policy.parseId(request.getActor()), now, null, now, now);
            service.start(run);
            return VerifySupport.brief(run);
        });
    }
}
