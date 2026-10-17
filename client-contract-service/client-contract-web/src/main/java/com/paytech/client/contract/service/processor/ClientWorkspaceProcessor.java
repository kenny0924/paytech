package com.paytech.client.contract.service.processor;

import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.contract.core.dal.dataobject.ClientWorkspaceSummary;
import com.paytech.client.contract.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.contract.integration.ClientDraftAccountClient;

import lombok.AllArgsConstructor;

/**
 * Client Console 工作区摘要的只读查询
 * <li>先经 Platform 实时核验当前会话（账号或租户上下文），再返回其中存在 APPROVED 申请的 Client。</li>
 * <li>clientIds 由 Client BFF 从 Platform 当前账号的成员列表取得；CCT 不判断成员关系，被邀请成员与申请人同样可读。</li>
 * <li>普通只读组合查询，不开启事务；Platform 调用不得位于数据库事务内。</li>
 */
@AllArgsConstructor
public class ClientWorkspaceProcessor {
    private final ClientDraftAccountClient accounts;
    private final ClientApplicationRepository applications;

    /**
     * 读取当前账号可见的已批准客户摘要
     *
     * @param accountId 当前账号
     * @param sessionId 当前会话
     * @param clientIds 已校验的 1—50 个不重复 Client ID
     * @return 已批准 Client 的摘要，按 Client ID 升序；其余 ID 静默省略
     */
    public List<ClientWorkspaceSummary> summaries(long accountId, long sessionId, List<Long> clientIds) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("工作区摘要查询不能加入已有事务");
        }
        accounts.authorize(accountId, sessionId);
        return applications.findApprovedSummaries(clientIds);
    }
}
