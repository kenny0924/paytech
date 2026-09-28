package com.paytech.client.contract.service.processor;
import java.util.Map;
import java.util.function.Supplier;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.contract.domain.onboarding.*;
import com.paytech.client.contract.domainservice.service.ClientPeopleService;
import com.paytech.client.contract.integration.ClientDraftAccountClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import static com.paytech.client.contract.domain.onboarding.ClientPeopleOperation.*;
/** Redis 多资源锁在事务外按稳定顺序取得；会话核对后才进入 CCT 本地事务。 */
public class ClientPeopleProcessor {
    private final ClientPeopleService service;
    private final ClientDraftAccountClient accounts;
    private final Validator validator;
    private final TransactionTemplate writes;
    public ClientPeopleProcessor(ClientPeopleService service,ClientDraftAccountClient accounts,Validator validator,TransactionTemplate writes) {
        this.service=service;this.accounts=accounts;this.validator=validator;this.writes=writes;
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public PeopleCommandReceiptResponseBody createPerson(long accountId,long sessionId,long applicationId,String key,CreatePersonRequestBody request) {
        accounts.authorize(accountId,sessionId);if (request==null) throw new FailException(INVALID_DRAFT);
        var person=person(request.person());var relation=ClientRelationValues.create(request.relation());
        var command=command(accountId,applicationId,CREATE_PERSON,key,Map.of("person",person.values(),"relation",relation.normalized()));
        return transaction(() -> service.createPerson(command,applicationId,person,relation));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    @ResourceLock(prefix="cct:party",key="#partyId")
    public PeopleCommandReceiptResponseBody savePerson(long accountId,long sessionId,long applicationId,long partyId,String key,DraftPatchRequestBody request) {
        accounts.authorize(accountId,sessionId);var person=person(request);
        var command=command(accountId,applicationId,SAVE_PERSON,key,Map.of("partyId",Long.toString(partyId),"person",person.values()));
        return transaction(() -> service.savePerson(command,applicationId,partyId,person));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    @ResourceLock(prefix="cct:party",key="#partyId")
    public PeopleCommandReceiptResponseBody createRelation(long accountId,long sessionId,long applicationId,long partyId,String key,RelationFields request) {
        accounts.authorize(accountId,sessionId);var relation=ClientRelationValues.create(request);
        var command=command(accountId,applicationId,CREATE_RELATION,key,Map.of("partyId",Long.toString(partyId),"relation",relation.normalized()));
        return transaction(() -> service.createRelation(command,applicationId,partyId,relation));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public PeopleCommandReceiptResponseBody saveRelation(long accountId,long sessionId,long applicationId,long relationId,String key,RelationPatchRequestBody request) {
        accounts.authorize(accountId,sessionId);var patch=ClientRelationValues.patch(request);
        var command=command(accountId,applicationId,SAVE_RELATION,key,Map.of("relationId",Long.toString(relationId),"fields",patch));
        return transaction(() -> service.saveRelation(command,applicationId,relationId,patch));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public void removeRelation(long accountId,long sessionId,long applicationId,long relationId,String key) {
        accounts.authorize(accountId,sessionId);
        var command=command(accountId,applicationId,REMOVE_RELATION,key,Map.of("relationId",Long.toString(relationId)));
        transaction(() -> { service.removeRelation(command,applicationId,relationId);return null; });
    }
    private ClientDraftPatch person(DraftPatchRequestBody request) {
        var person=ClientDraftPatch.normalize(request,validator);person.requirePerson();return person;
    }
    private ClientDraftCommand command(long account,long application,ClientPeopleOperation operation,String key,Map<String,?> body) {
        return ClientDraftCommand.of(account,operation.getCode(),key,Map.of("applicationId",Long.toString(application),"body",body));
    }
    private <T> T transaction(Supplier<T> action) {
        for (int attempt=0; ; attempt++) {
            try { return writes.execute(tx -> action.get()); }
            catch (DuplicateKeyException collision) {
                String reason=collision.getMostSpecificCause().getMessage();
                if (reason!=null && reason.contains("uk_party_relation_identity")) throw new FailException(RELATION_ALREADY_EXISTS);
                if (attempt>=2 || reason==null || !reason.contains("uk_command_idem")) throw collision;
                // 跨申请竞争相同命令键时，先回滚整个事务，再读取原回执或返回请求冲突。
            }
        }
    }
}
