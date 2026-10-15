package com.paytech.client.contract.ext.rest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
import com.paytech.client.contract.domain.onboarding.ClientDraftCommand;
import com.paytech.client.contract.service.processor.ClientDraftProcessor;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 私网入口；Caller Header 防误用，私网隔离和入口 Header 清理仍由部署边界承担。 */
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientDraftController implements ClientDraftApi {
    private final ClientDraftProcessor processor;
    public ClientDraftController(ClientDraftProcessor processor) { this.processor=processor; }
    public ResponseEntity<CommandReceiptResponseBody> create(String caller,String accountId,String sessionId,StartApplicationRequestBody request) {
        caller(caller);var receipt=processor.create(account(accountId),account(sessionId),request);
        return ResponseEntity.status("CREATED".equals(receipt.operation())?201:200).body(receipt);
    }
    public ClientDraftResponseBody read(String caller,String accountId,String sessionId,String applicationId) {
        caller(caller);return processor.read(account(accountId),account(sessionId),ClientDraftCommand.id(applicationId));
    }
    public CommandReceiptResponseBody save(String caller,String accountId,String sessionId,String applicationId,DraftPatchRequestBody request) {
        caller(caller);return processor.save(account(accountId),account(sessionId),ClientDraftCommand.id(applicationId),request);
    }
    private static void caller(String caller) { if (!"client-console-bff".equals(caller)) throw new FailException(APPLICATION_NOT_FOUND); }
    private static long account(String value) {
        try { return ClientDraftCommand.id(value); }
        catch (FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); }
    }
}
