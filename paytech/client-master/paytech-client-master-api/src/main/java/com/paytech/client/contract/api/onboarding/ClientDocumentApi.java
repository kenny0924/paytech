package com.paytech.client.contract.api.onboarding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
/** Client BFF 使用的 CCT 私网材料命令，不经 Gateway 直接暴露 Owner。 */
@HttpExchange("/client-onboarding/v1/applications")
public interface ClientDocumentApi {
    /** 先核验真实 Risk/Platform 事实；同一申请、主体、要求、类型和文件已关联时返回原引用。 */
    @PostExchange("/{applicationId}/documents")
    ResponseEntity<DocumentCommandReceiptResponseBody> associate(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,@RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("applicationId") String applicationId,@RequestBody DocumentReferenceRequestBody request);
    /** 只移除原申请可编辑引用，本申请下已不存在时同样 204；Body 为空，不删除 Platform File。 */
    @DeleteExchange("/{applicationId}/documents/{documentId}")
    ResponseEntity<Void> remove(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,@RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("applicationId") String applicationId,@PathVariable("documentId") String documentId);
}
