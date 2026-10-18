package com.paytech.client.master.service.processor;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientWorkspaceSummary;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.GetClientWorkspaceSummariesRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * Client Console 工作区摘要的只读查询
 * <li>先经 Platform 实时核验当前会话（账号或租户上下文），再返回其中存在 APPROVED 申请的 Client，按 Client ID 升序，其余 ID 静默省略。</li>
 * <li>clientIds 由 Client BFF 从 Platform 当前账号的成员列表取得；CCT 不判断成员关系，被邀请成员与申请人同样可读。</li>
 * <li>普通只读组合查询，不开启事务；Platform 调用不得位于数据库事务内。</li>
 */
@AllArgsConstructor
public class GetClientWorkspaceSummariesProcessor
    extends AbstractProcessor<GetClientWorkspaceSummariesRequest, List<ClientWorkspaceSummary>> {
    private final ClientDraftAccountClient accounts;
    private final ClientApplicationRepository applications;

    @Override
    protected List<ClientWorkspaceSummary> processRequest(GetClientWorkspaceSummariesRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        return applications.findApprovedSummaries(request.getClientIds());
    }
}
