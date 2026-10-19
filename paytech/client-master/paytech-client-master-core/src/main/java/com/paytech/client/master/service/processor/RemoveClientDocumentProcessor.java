package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domainservice.service.ClientDocumentService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.RemoveClientDocumentRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * C13 删除材料关联；本申请下已不存在时同样完成。
 * <li>按申请 client-onboarding:application:{applicationId} 串行；只调用 Platform 会话校验，不删除 File 或修改其扫描状态。</li>
 */
public class RemoveClientDocumentProcessor extends AbstractLockProcessor<RemoveClientDocumentRequest, Void> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDocumentService service;
    private final ClientDraftAccountClient accounts;
    private final TransactionTemplate writes;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public RemoveClientDocumentProcessor(Duration lockWait, Duration lockLease, ClientDocumentService service,
        ClientDraftAccountClient accounts, TransactionTemplate writes) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.writes = writes;
    }

    /** 与原注解锁 client-onboarding:application 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(RemoveClientDocumentRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait,
            lockLease);
    }

    @Override
    protected Void processWithinLock(RemoveClientDocumentRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var command = ClientDraftCommand.of(request.getAccountId(), "CLIENT_DOCUMENT_REMOVE");
        return ClientOnboardingCommands.documentWrite(writes, () -> {
            service.remove(command, request.getApplicationId(), request.getDocumentId());
            return null;
        });
    }
}
