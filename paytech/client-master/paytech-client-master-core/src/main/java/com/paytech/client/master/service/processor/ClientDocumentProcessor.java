package com.paytech.client.master.service.processor;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.service.ClientDocumentService;
import com.paytech.client.master.integration.*;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 外部证据读取在事务外；最终短事务重新检查原申请、主体关系和已有同一关联。 */
public class ClientDocumentProcessor {
    private final ClientDocumentService service;
    private final ClientDraftAccountClient accounts;
    private final ClientMaterialAuthorityClient authority;
    private final TransactionTemplate reads,writes;
    /** 复用本服务已有短事务与原账号权威，不建立跨系统事务。 */
    public ClientDocumentProcessor(ClientDocumentService service,ClientDraftAccountClient accounts,ClientMaterialAuthorityClient authority,TransactionTemplate reads,TransactionTemplate writes) {
        this.service=service;this.accounts=accounts;this.authority=authority;this.reads=reads;this.writes=writes;
    }
    /** 实际资源是原申请；行锁和唯一键在 Redis Lease 失效后仍保护材料集合。 */
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public DocumentCommandReceiptResponseBody associate(long accountId,long sessionId,long applicationId,DocumentReferenceRequestBody request) {
        accounts.authorize(accountId,sessionId);
        if (request==null || !code(request.requirementCode()) || !code(request.documentType())) throw new FailException(INVALID_DRAFT);
        long subject=ClientDraftCommand.id(request.subjectPartyId());ClientDraftCommand.id(request.fileId());
        var command=ClientDraftCommand.of(accountId,"CLIENT_DOCUMENT_ASSOCIATE");
        var target=reads.execute(tx -> service.target(accountId,applicationId,subject,false));
        String hash=authority.verify(accountId,target,request);
        accounts.authorize(accountId,sessionId);
        return transaction(() -> service.associate(command,target,request,hash));
    }
    /** 删除只调用 Platform 会话校验，不删除 File 或修改其扫描状态。 */
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public void remove(long accountId,long sessionId,long applicationId,long documentId) {
        accounts.authorize(accountId,sessionId);
        var command=ClientDraftCommand.of(accountId,"CLIENT_DOCUMENT_REMOVE");
        transaction(() -> { service.remove(command,applicationId,documentId);return null; });
    }
    /** 代码形状只是协议校验，合法组合仍必须来自 Risk。 */
    private boolean code(String value) { return value!=null && value.matches("[A-Z][A-Z0-9_]{0,63}"); }
    /** 唯一键冲突回滚整个事务；申请锁失效时的并发同一关联明确冲突。 */
    private <T> T transaction(Supplier<T> action) {
        try { return writes.execute(tx -> action.get()); }
        catch (DuplicateKeyException collision) {
            String reason=collision.getMostSpecificCause().getMessage();
            if (reason!=null && reason.contains("uk_app_doc_client")) throw new FailException(DOCUMENT_ALREADY_EXISTS);
            throw collision;
        }
    }
}
