package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyRunBriefView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelVerifyRunService;
import com.paytech.channel.connector.protocol.service.processor.request.StartVerifyRunRequest;

import lombok.AllArgsConstructor;

/**
 * V12 发起沙箱验证运行（沙箱验证技术方案第 3.6 节）：一个本地事务中冻结用例快照（含运行原因，不含凭据）、插入 RUNNING 运行并登记补偿任务，
 * 提交后由补偿执行器在事务外执行；本入口不调用外部服务。同一用例可并发发起，订单号等由模板变量区分。
 */
@Component
@AllArgsConstructor
public class StartVerifyRunProcessor extends AbstractProcessor<StartVerifyRunRequest, VerifyRunBriefView> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelVerifyCaseRepository cases;
    private final ChannelVerifyAccountRepository accounts;
    private final ChannelVerifyRunService service;

    @Override
    protected VerifyRunBriefView processRequest(StartVerifyRunRequest request) {
        return transactions.write(() -> {
            var integration = children.channel(request.getIntegrationCode());
            ChannelVerifyCase verifyCase =
                support.requireFound(cases.byCode(integration.getChannelId(), request.getCaseCode()));
            ChannelVerifyAccount account = accounts.byId(verifyCase.getVerifyAccountId());
            if (account == null) {
                throw new BizCheckFailException("验证用例引用的验证账号不存在：" + verifyCase.getCaseCode());
            }
            var now = support.now();
            var run = new ChannelVerifyRun(support.nextId(), verifyCase.getVerifyCaseId(),
                integration.getChannelId(), VerifyRunStatus.RUNNING,
                verify.caseSnapshot(verifyCase, account, integration.getChannelCode(), request.getReason()), null, null, null,
                request.getActorId(), now, null, now, now);
            service.start(run);
            return VerifySupport.brief(run);
        });
    }
}
