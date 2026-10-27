package com.paytech.client.master.ext.rest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.client.contract.api.onboarding.ClientPeopleApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.CreateClientPersonProcessor;
import com.paytech.client.master.service.processor.CreateClientRelationProcessor;
import com.paytech.client.master.service.processor.RemoveClientRelationProcessor;
import com.paytech.client.master.service.processor.SaveClientPersonProcessor;
import com.paytech.client.master.service.processor.SaveClientRelationProcessor;
import com.paytech.client.master.service.processor.request.CreateClientPersonRequest;
import com.paytech.client.master.service.processor.request.CreateClientRelationRequest;
import com.paytech.client.master.service.processor.request.RemoveClientRelationRequest;
import com.paytech.client.master.service.processor.request.SaveClientPersonRequest;
import com.paytech.client.master.service.processor.request.SaveClientRelationRequest;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientPeopleController implements ClientPeopleApi {
    private final CreateClientPersonProcessor createPersonProcessor;
    private final SaveClientPersonProcessor savePersonProcessor;
    private final CreateClientRelationProcessor createRelationProcessor;
    private final SaveClientRelationProcessor saveRelationProcessor;
    private final RemoveClientRelationProcessor removeRelationProcessor;
    /** 每个入口委托各自的 Processor。 */
    public ClientPeopleController(CreateClientPersonProcessor createPersonProcessor,SaveClientPersonProcessor savePersonProcessor,
        CreateClientRelationProcessor createRelationProcessor,SaveClientRelationProcessor saveRelationProcessor,
        RemoveClientRelationProcessor removeRelationProcessor) {
        this.createPersonProcessor=createPersonProcessor;this.savePersonProcessor=savePersonProcessor;
        this.createRelationProcessor=createRelationProcessor;this.saveRelationProcessor=saveRelationProcessor;
        this.removeRelationProcessor=removeRelationProcessor;
    }
    public ResponseEntity<PeopleCommandReceiptResponseBody> createPerson(String caller,String accountId,String sessionId,String applicationId,CreatePersonRequestBody request) {
        caller(caller);return ResponseEntity.status(201).body(createPersonProcessor.process(
            new CreateClientPersonRequest(account(accountId),account(sessionId),id(applicationId),request)));
    }
    public PeopleCommandReceiptResponseBody savePerson(String caller,String accountId,String sessionId,String applicationId,String partyId,DraftPatchRequestBody request) {
        caller(caller);
        return savePersonProcessor.process(new SaveClientPersonRequest(account(accountId),account(sessionId),id(applicationId),id(partyId),
            request));
    }
    public ResponseEntity<PeopleCommandReceiptResponseBody> createRelation(String caller,String accountId,String sessionId,String applicationId,CreateRelationRequestBody request) {
        caller(caller);if (request==null) throw new FailException(INVALID_DRAFT);
        var relation=new RelationFields(request.relationType(),request.ownershipPercentage(),request.effectiveFrom(),request.effectiveTo(),
            request.uboBasis(),request.jobTitle(),request.nomineeFlag(),request.controlNature());
        return ResponseEntity.status(201).body(createRelationProcessor.process(new CreateClientRelationRequest(account(accountId),
            account(sessionId),id(applicationId),id(request.relatedPartyId()),relation)));
    }
    public PeopleCommandReceiptResponseBody saveRelation(String caller,String accountId,String sessionId,String applicationId,String relationId,RelationPatchRequestBody request) {
        caller(caller);
        return saveRelationProcessor.process(new SaveClientRelationRequest(account(accountId),account(sessionId),id(applicationId),
            id(relationId),request));
    }
    public ResponseEntity<Void> removeRelation(String caller,String accountId,String sessionId,String applicationId,String relationId,String body) {
        caller(caller);if (body!=null && !body.isBlank()) throw new FailException(INVALID_DRAFT);
        removeRelationProcessor.process(new RemoveClientRelationRequest(account(accountId),account(sessionId),id(applicationId),id(relationId)));
        return ResponseEntity.noContent().build();
    }
    private static long id(String value) { return ClientDraftCommand.id(value); }
    private static void caller(String caller) { if (!"client-console-bff".equals(caller)) throw new FailException(APPLICATION_NOT_FOUND); }
    private static long account(String value) {
        try { return id(value); } catch (FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); }
    }
}
