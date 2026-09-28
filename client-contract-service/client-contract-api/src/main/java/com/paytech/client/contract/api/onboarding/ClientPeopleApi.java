package com.paytech.client.contract.api.onboarding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
/** CCT 私网合同，沿用 Client 会话、申请归属和原命令重放边界。 */
@HttpExchange("/client-onboarding/v1/applications")
public interface ClientPeopleApi {
    @PostExchange("/{applicationId}/people")
    ResponseEntity<PeopleCommandReceiptResponseBody> createPerson(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @RequestHeader("Idempotency-Key") String commandId, @RequestBody CreatePersonRequestBody request);
    @PatchExchange("/{applicationId}/people/{partyId}")
    PeopleCommandReceiptResponseBody savePerson(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @PathVariable String partyId,
        @RequestHeader("Idempotency-Key") String commandId, @RequestBody DraftPatchRequestBody request);
    @PostExchange("/{applicationId}/relations")
    ResponseEntity<PeopleCommandReceiptResponseBody> createRelation(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @RequestHeader("Idempotency-Key") String commandId, @RequestBody CreateRelationRequestBody request);
    @PatchExchange("/{applicationId}/relations/{relationId}")
    PeopleCommandReceiptResponseBody saveRelation(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @PathVariable String relationId,
        @RequestHeader("Idempotency-Key") String commandId, @RequestBody RelationPatchRequestBody request);
    @DeleteExchange("/{applicationId}/relations/{relationId}")
    ResponseEntity<Void> removeRelation(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @PathVariable String relationId,
        @RequestHeader("Idempotency-Key") String commandId, @RequestBody(required=false) String body);
}
