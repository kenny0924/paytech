package com.paytech.client.contract.ext.rest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.paytech.client.contract.api.onboarding.ClientDocumentApi;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.contract.domain.onboarding.ClientDraftCommand;
import com.paytech.client.contract.service.processor.ClientDocumentProcessor;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 只适配 Client BFF 私网协议，不信任 Header 代替真实 Platform 会话。 */
@RestController
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientDocumentController implements ClientDocumentApi {
    private final ClientDocumentProcessor processor;
    /** 注入完整用例。 */
    public ClientDocumentController(ClientDocumentProcessor processor) { this.processor=processor; }
    /** 首次及重放都返回固定 201 回执。 */
    @Override public ResponseEntity<DocumentCommandReceiptResponseBody> associate(String caller,String account,String session,String application,String key,DocumentReferenceRequestBody request) {
        caller(caller);return ResponseEntity.status(201).body(processor.associate(account(account),account(session),ClientDraftCommand.id(application),key,request));
    }
    /** 删除及原键重放始终 204，没有当前文件响应。 */
    @Override public ResponseEntity<Void> remove(String caller,String account,String session,String application,String document,String key) {
        caller(caller);processor.remove(account(account),account(session),ClientDraftCommand.id(application),ClientDraftCommand.id(document),key);return ResponseEntity.noContent().build();
    }
    /** Caller 仅防误调用，部署仍使用受限私网。 */
    private void caller(String value) { if (!"client-console-bff".equals(value)) throw new FailException(APPLICATION_NOT_FOUND); }
    /** 非法身份坐标与无效会话统一响应。 */
    private long account(String value) { try { return ClientDraftCommand.id(value); } catch (FailException invalid) { throw new FailException(CLIENT_SESSION_INVALID); } }
}
