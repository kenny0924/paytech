package com.paytech.client.contract.api.onboarding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
/** 仅 Client BFF 经私网调用；身份由可信入口传递，CCT 每次重新核对 Platform 与本库归属。 */
@HttpExchange("/client-onboarding/v1/applications")
public interface ClientDraftApi {
    @PostExchange
    ResponseEntity<CommandReceiptResponseBody> create(
        @RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestBody StartApplicationRequestBody request);
    @GetExchange("/{applicationId}")
    ClientDraftResponseBody read(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable String applicationId);
    @PatchExchange("/{applicationId}/draft")
    CommandReceiptResponseBody save(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable String applicationId,
        @RequestBody DraftPatchRequestBody request);
}
