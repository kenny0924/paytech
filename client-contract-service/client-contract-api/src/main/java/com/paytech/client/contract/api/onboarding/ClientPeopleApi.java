package com.paytech.client.contract.api.onboarding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
/** CCT 私网合同，沿用 Client 会话与申请归属；请求幂等由 Gateway 处理，重复到达按业务事实防重。 */
@HttpExchange("/client-onboarding/v1/applications")
public interface ClientPeopleApi {
    @PostExchange("/{applicationId}/people")
    ResponseEntity<PeopleCommandReceiptResponseBody> createPerson(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @RequestBody CreatePersonRequestBody request);
    @PatchExchange("/{applicationId}/people/{partyId}")
    PeopleCommandReceiptResponseBody savePerson(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @PathVariable String partyId,
        @RequestBody DraftPatchRequestBody request);
    @PostExchange("/{applicationId}/relations")
    ResponseEntity<PeopleCommandReceiptResponseBody> createRelation(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @RequestBody CreateRelationRequestBody request);
    @PatchExchange("/{applicationId}/relations/{relationId}")
    PeopleCommandReceiptResponseBody saveRelation(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @PathVariable String relationId,
        @RequestBody RelationPatchRequestBody request);
    @DeleteExchange("/{applicationId}/relations/{relationId}")
    ResponseEntity<Void> removeRelation(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable String applicationId,
        @PathVariable String relationId,
        @RequestBody(required=false) String body);
}
