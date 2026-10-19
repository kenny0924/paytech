package com.paytech.client.master.ext.rest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.paytech.client.contract.api.onboarding.ClientDocumentApi;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.AssociateClientDocumentProcessor;
import com.paytech.client.master.service.processor.RemoveClientDocumentProcessor;
import com.paytech.client.master.service.processor.request.AssociateClientDocumentRequest;
import com.paytech.client.master.service.processor.request.RemoveClientDocumentRequest;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 只适配 Client BFF 私网协议，不信任 Header 代替真实 Platform 会话。 */
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientDocumentController implements ClientDocumentApi {
    private final AssociateClientDocumentProcessor associateProcessor;
    private final RemoveClientDocumentProcessor removeProcessor;
    /** 每个入口委托各自的 Processor。 */
    public ClientDocumentController(AssociateClientDocumentProcessor associateProcessor,RemoveClientDocumentProcessor removeProcessor) {
        this.associateProcessor=associateProcessor;this.removeProcessor=removeProcessor;
    }
    /** 新关联及已存在的同一关联都返回 201 回执。 */
    @Override public ResponseEntity<DocumentCommandReceiptResponseBody> associate(String caller,String account,String session,String application,DocumentReferenceRequestBody request) {
        caller(caller);
        return ResponseEntity.status(201).body(associateProcessor.process(new AssociateClientDocumentRequest(account(account),
            account(session),ClientDraftCommand.id(application),request)));
    }
    /** 删除及本申请下已不存在都返回 204，没有当前文件响应。 */
    @Override public ResponseEntity<Void> remove(String caller,String account,String session,String application,String document) {
        caller(caller);
        removeProcessor.process(new RemoveClientDocumentRequest(account(account),account(session),ClientDraftCommand.id(application),
            ClientDraftCommand.id(document)));
        return ResponseEntity.noContent().build();
    }
    /** Caller 仅防误调用，部署仍使用受限私网。 */
    private void caller(String value) { if (!"client-console-bff".equals(value)) throw new FailException(APPLICATION_NOT_FOUND); }
    /** 非法身份坐标与无效会话统一响应。 */
    private long account(String value) { try { return ClientDraftCommand.id(value); } catch (FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); } }
}
