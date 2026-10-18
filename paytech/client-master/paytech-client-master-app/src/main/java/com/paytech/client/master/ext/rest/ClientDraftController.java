package com.paytech.client.master.ext.rest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.CreateClientApplicationProcessor;
import com.paytech.client.master.service.processor.GetClientDraftProcessor;
import com.paytech.client.master.service.processor.SaveClientDraftProcessor;
import com.paytech.client.master.service.processor.request.CreateClientApplicationRequest;
import com.paytech.client.master.service.processor.request.GetClientDraftRequest;
import com.paytech.client.master.service.processor.request.SaveClientDraftRequest;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 私网入口；Caller Header 防误用，私网隔离和入口 Header 清理仍由部署边界承担。 */
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientDraftController implements ClientDraftApi {
    private final CreateClientApplicationProcessor createProcessor;
    private final GetClientDraftProcessor readProcessor;
    private final SaveClientDraftProcessor saveProcessor;
    /** 每个入口委托各自的 Processor。 */
    public ClientDraftController(CreateClientApplicationProcessor createProcessor,GetClientDraftProcessor readProcessor,
        SaveClientDraftProcessor saveProcessor) {
        this.createProcessor=createProcessor;this.readProcessor=readProcessor;this.saveProcessor=saveProcessor;
    }
    public ResponseEntity<CommandReceiptResponseBody> create(String caller,String accountId,String sessionId,StartApplicationRequestBody request) {
        caller(caller);var receipt=createProcessor.process(new CreateClientApplicationRequest(account(accountId),account(sessionId),request));
        return ResponseEntity.status("CREATED".equals(receipt.operation())?201:200).body(receipt);
    }
    public ClientDraftResponseBody read(String caller,String accountId,String sessionId,String applicationId) {
        caller(caller);
        return readProcessor.process(new GetClientDraftRequest(account(accountId),account(sessionId),ClientDraftCommand.id(applicationId)));
    }
    public CommandReceiptResponseBody save(String caller,String accountId,String sessionId,String applicationId,DraftPatchRequestBody request) {
        caller(caller);
        return saveProcessor.process(new SaveClientDraftRequest(account(accountId),account(sessionId),ClientDraftCommand.id(applicationId),
            request));
    }
    private static void caller(String caller) { if (!"client-console-bff".equals(caller)) throw new FailException(APPLICATION_NOT_FOUND); }
    private static long account(String value) {
        try { return ClientDraftCommand.id(value); }
        catch (FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); }
    }
}
