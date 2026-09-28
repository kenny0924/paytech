package com.paytech.client.contract.service.processor;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domain.onboarding.*;
import com.paytech.client.contract.domainservice.service.ClientDraftService;
import com.paytech.client.contract.ext.rest.ClientDraftConverter;
import com.paytech.client.contract.integration.*;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 外围协调和外部调用不在数据库事务中；本地事务包裹 Service 的状态、归属与持久化规则。 */
public class ClientDraftProcessor {
    private final ClientDraftService service;
    private final ClientDraftAccountClient accounts;
    private final ClientDraftContactClient contacts;
    private final Validator validator;
    private final TransactionTemplate writes,reads;
    public ClientDraftProcessor(ClientDraftService service,ClientDraftAccountClient accounts,ClientDraftContactClient contacts,Validator validator,TransactionTemplate writes,TransactionTemplate reads) {
        this.service=service;this.accounts=accounts;this.contacts=contacts;this.validator=validator;this.writes=writes;this.reads=reads;
    }
    @ResourceLock(prefix="client-onboarding:account-slot",key="#accountId")
    public CommandReceiptResponseBody create(long accountId,long sessionId,String key,StartApplicationRequestBody request) {
        accounts.authorize(accountId,sessionId);
        ClientType type;
        try { type=ClientType.valueOf(request.clientType()); }
        catch (RuntimeException invalid) { throw new FailException(INVALID_DRAFT); }
        var command=ClientDraftCommand.of(accountId,"CLIENT_APPLICATION_CREATE",key,Map.of("clientType",type.getCode()));
        return duplicateRace(() -> writes.execute(tx -> service.create(command,type)));
    }
    /** 会话在事务外核对，单次只读查询不会改变申请状态。 */
    public StartResponseBody start(long accountId,long sessionId) {
        accounts.authorize(accountId,sessionId);
        var rows=service.recentApplications(accountId);
        return ClientDraftConverter.start(rows);
    }
    public ClientDraftResponseBody read(long accountId,long sessionId,long applicationId) {
        accounts.authorize(accountId,sessionId);
        var view=reads.execute(tx -> service.read(accountId,applicationId));
        return ClientDraftConverter.response(view,contacts.reveal(view.contacts()));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public CommandReceiptResponseBody save(long accountId,long sessionId,long applicationId,String key,DraftPatchRequestBody request) {
        accounts.authorize(accountId,sessionId);
        var patch=ClientDraftPatch.normalize(request,validator);
        var command=ClientDraftCommand.of(accountId,"CLIENT_DRAFT_SAVE",key,Map.of("applicationId",Long.toString(applicationId),"fields",patch.values()));
        var replay=reads.execute(tx -> service.prepareSave(command,applicationId,patch));
        if (replay!=null) return replay;
        var protectedContacts=contacts.protect(command,patch);
        return duplicateRace(() -> writes.execute(tx -> service.save(command,applicationId,patch,protectedContacts)));
    }
    private <T> T duplicateRace(Supplier<T> transaction) {
        // Redis 租约失效时，由本库唯一索引回滚竞争事务，再读取胜出命令或账号占位。
        for (int attempt=0; ; attempt++) {
            try { return transaction.get(); }
            catch (DuplicateKeyException collision) {
                String reason=collision.getMostSpecificCause().getMessage();
                if (attempt>=2 || reason==null || !(reason.contains("uk_command_idem") || reason.contains("uk_client_app_account_active")))
                    throw collision;
            }
        }
    }
}
