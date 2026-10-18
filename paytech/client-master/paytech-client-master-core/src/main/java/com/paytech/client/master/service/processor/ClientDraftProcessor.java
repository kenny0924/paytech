package com.paytech.client.master.service.processor;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.ext.rest.ClientDraftConverter;
import com.paytech.client.master.integration.*;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.redis.api.ResourceLock;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 外围协调和外部调用不在数据库事务中；本地事务包裹 Service 的状态、归属与持久化规则。 */
public class ClientDraftProcessor {
    private final ClientDraftService service;
    private final ClientDraftAccountClient accounts;
    private final ClientDraftContactClient contacts;
    private final ClientAgreementPartyClient agreementParties;
    private final Validator validator;
    private final TransactionTemplate writes,reads;
    public ClientDraftProcessor(ClientDraftService service,ClientDraftAccountClient accounts,ClientDraftContactClient contacts,
        ClientAgreementPartyClient agreementParties,Validator validator,TransactionTemplate writes,TransactionTemplate reads) {
        this.service=service;this.accounts=accounts;this.contacts=contacts;this.agreementParties=agreementParties;
        this.validator=validator;this.writes=writes;this.reads=reads;
    }
    @ResourceLock(prefix="client-onboarding:account-slot",key="#accountId")
    public CommandReceiptResponseBody create(long accountId,long sessionId,StartApplicationRequestBody request) {
        accounts.authorize(accountId,sessionId);
        ClientType type=clientType(request);
        var command=ClientDraftCommand.of(accountId,"CLIENT_APPLICATION_CREATE");
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
        // 读取事务结束后再查 commercial 合同引用；申请不可编辑时人员标记恒为 false，无需查询。
        var usage=ClientDraftPolicy.isEditable(view.identity())
            ?agreementParties.usage(view.people().stream().map(ClientPersonView::identity).toList())
            :ClientAgreementPartyUsage.unavailable();
        return ClientDraftConverter.response(view,contacts.reveal(view.contacts()),usage);
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public CommandReceiptResponseBody save(long accountId,long sessionId,long applicationId,DraftPatchRequestBody request) {
        accounts.authorize(accountId,sessionId);
        var patch=ClientDraftPatch.normalize(request,validator);
        var command=ClientDraftCommand.of(accountId,"CLIENT_DRAFT_SAVE");
        reads.executeWithoutResult(tx -> service.prepareSave(command,applicationId,patch));
        var protectedContacts=contacts.protect(patch);
        return duplicateRace(() -> writes.execute(tx -> service.save(command,applicationId,patch,protectedContacts)));
    }
    /** 申请类型只接受闭合目录内的 Code；空值或目录外取值以 clientType 字段错误拒绝。 */
    static ClientType clientType(StartApplicationRequestBody request) {
        String code=request==null?null:request.clientType();
        for (ClientType type:ClientType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new RequestBodyValidationException(INVALID_DRAFT,
            List.of(new ProblemFieldError("clientType","须为 INDIVIDUAL 或 ORGANIZATION")),null);
    }
    private <T> T duplicateRace(Supplier<T> transaction) {
        // Redis 租约失效时，由账号未完成名额唯一索引回滚竞争事务，再读取胜出的申请。
        for (int attempt=0; ; attempt++) {
            try { return transaction.get(); }
            catch (DuplicateKeyException collision) {
                String reason=collision.getMostSpecificCause().getMessage();
                if (attempt>=2 || reason==null || !reason.contains("uk_client_app_account_active"))
                    throw collision;
            }
        }
    }
}
