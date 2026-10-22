package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentCommandReceiptResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domainservice.service.ClientDocumentService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.integration.ClientMaterialAuthorityClient;
import com.paytech.client.master.service.processor.request.AssociateClientDocumentRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/**
 * C12 关联入驻材料。
 * <li>实际资源是原申请，按 client-onboarding:application:{applicationId} 串行；行锁和唯一键在 Redis Lease 失效后仍保护材料集合。</li>
 * <li>只读快照确定材料目标后，Risk 材料要求与 Platform 文件核验（含上传人为当前账号）在事务外完成，再次核验会话后进入最终短事务。</li>
 * <li>同一申请、主体、要求、类型和文件的重复关联返回原引用；并发同一关联返回 DOCUMENT_ALREADY_EXISTS。</li>
 */
public class AssociateClientDocumentProcessor
    extends AbstractLockProcessor<AssociateClientDocumentRequest, DocumentCommandReceiptResponseBody> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDocumentService service;
    private final ClientDraftAccountClient accounts;
    private final ClientMaterialAuthorityClient authority;
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    /** 复用本服务已有短事务与原账号权威，不建立跨系统事务；锁参数沿用 CCT 既有注解锁配置。 */
    public AssociateClientDocumentProcessor(Duration lockWait, Duration lockLease, ClientDocumentService service,
        ClientDraftAccountClient accounts, ClientMaterialAuthorityClient authority, TransactionTemplate reads,
        TransactionTemplate writes) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.authority = authority;
        this.reads = reads;
        this.writes = writes;
    }

    /** 与原注解锁 client-onboarding:application 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(AssociateClientDocumentRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait,
            lockLease);
    }

    @Override
    protected DocumentCommandReceiptResponseBody processWithinLock(AssociateClientDocumentRequest request) {
        long accountId = request.getAccountId();
        accounts.authorize(accountId, request.getSessionId());
        DocumentReferenceRequestBody document = request.getDocument();
        if (document == null || !code(document.requirementCode()) || !code(document.documentType())) {
            throw new FailException(INVALID_DRAFT);
        }
        long subject = ClientDraftCommand.id(document.subjectPartyId());
        ClientDraftCommand.fileAddress(document.fileAddress());
        var command = ClientDraftCommand.of(accountId, "CLIENT_DOCUMENT_ASSOCIATE");
        var target = reads.execute(tx -> service.target(accountId, request.getApplicationId(), subject, false));
        String hash = authority.verify(accountId, target, document);
        accounts.authorize(accountId, request.getSessionId());
        return ClientOnboardingCommands.documentWrite(writes, () -> service.associate(command, target, document, hash));
    }

    /** 代码形状只是协议校验，合法组合仍必须来自 Risk。 */
    boolean code(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}");
    }
}
