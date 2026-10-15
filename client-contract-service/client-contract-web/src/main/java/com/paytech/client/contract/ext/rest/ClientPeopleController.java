package com.paytech.client.contract.ext.rest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.client.contract.api.onboarding.ClientPeopleApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.contract.domain.onboarding.ClientDraftCommand;
import com.paytech.client.contract.service.processor.ClientPeopleProcessor;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientPeopleController implements ClientPeopleApi {
    private final ClientPeopleProcessor processor;
    public ClientPeopleController(ClientPeopleProcessor processor) { this.processor=processor; }
    public ResponseEntity<PeopleCommandReceiptResponseBody> createPerson(String caller,String accountId,String sessionId,String applicationId,CreatePersonRequestBody request) {
        caller(caller);return ResponseEntity.status(201).body(processor.createPerson(account(accountId),account(sessionId),id(applicationId),request));
    }
    public PeopleCommandReceiptResponseBody savePerson(String caller,String accountId,String sessionId,String applicationId,String partyId,DraftPatchRequestBody request) {
        caller(caller);return processor.savePerson(account(accountId),account(sessionId),id(applicationId),id(partyId),request);
    }
    public ResponseEntity<PeopleCommandReceiptResponseBody> createRelation(String caller,String accountId,String sessionId,String applicationId,CreateRelationRequestBody request) {
        caller(caller);if (request==null) throw new FailException(INVALID_DRAFT);
        var relation=new RelationFields(request.relationType(),request.ownershipPercentage(),request.effectiveFrom(),request.effectiveTo());
        return ResponseEntity.status(201).body(processor.createRelation(account(accountId),account(sessionId),id(applicationId),id(request.relatedPartyId()),relation));
    }
    public PeopleCommandReceiptResponseBody saveRelation(String caller,String accountId,String sessionId,String applicationId,String relationId,RelationPatchRequestBody request) {
        caller(caller);return processor.saveRelation(account(accountId),account(sessionId),id(applicationId),id(relationId),request);
    }
    public ResponseEntity<Void> removeRelation(String caller,String accountId,String sessionId,String applicationId,String relationId,String body) {
        caller(caller);if (body!=null && !body.isBlank()) throw new FailException(INVALID_DRAFT);
        processor.removeRelation(account(accountId),account(sessionId),id(applicationId),id(relationId));
        return ResponseEntity.noContent().build();
    }
    private static long id(String value) { return ClientDraftCommand.id(value); }
    private static void caller(String caller) { if (!"client-console-bff".equals(caller)) throw new FailException(APPLICATION_NOT_FOUND); }
    private static long account(String value) {
        try { return id(value); } catch (FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); }
    }
}
