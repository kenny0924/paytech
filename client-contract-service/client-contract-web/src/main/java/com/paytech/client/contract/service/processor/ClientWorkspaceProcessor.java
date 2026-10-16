package com.paytech.client.contract.service.processor;

import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.contract.core.dal.dataobject.ClientWorkspaceSummary;
import com.paytech.client.contract.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.contract.integration.ClientDraftAccountClient;

import lombok.AllArgsConstructor;

/**
 * Client Console 工作区摘要的只读查询
 * <li>先经 Platform 实时核验当前会话（账号或租户上下文），再按“本账号为申请人且已批准”过滤，不信任调用方给出的归属。</li>
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
     * @return 可见 Client 的摘要，按 Client ID 升序；其余 ID 静默省略
     */
    public List<ClientWorkspaceSummary> summaries(long accountId, long sessionId, List<Long> clientIds) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("工作区摘要查询不能加入已有事务");
        }
        accounts.authorize(accountId, sessionId);
        return applications.findApprovedSummaries(accountId, clientIds);
    }
}
