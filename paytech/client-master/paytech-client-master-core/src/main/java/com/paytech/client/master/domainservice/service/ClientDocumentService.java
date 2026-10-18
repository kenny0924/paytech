package com.paytech.client.master.domainservice.service;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.master.domain.onboarding.*;
/** 只负责原申请材料事实与本地事务规则。 */
public interface ClientDocumentService {
    /** 从当前资料派生精确资格类型与国家；只允许可编辑申请和范围内主体。 */
    ClientMaterialTarget target(long accountId,long applicationId,long subjectPartyId,boolean lock);
    /** 本地重查目标并保存材料；同一组合已关联时返回原引用。 */
    DocumentCommandReceiptResponseBody associate(ClientDraftCommand command,ClientMaterialTarget target,DocumentReferenceRequestBody request,String contentHash);
    /** 本地删除引用；本申请下已不存在时视为完成。 */
    void remove(ClientDraftCommand command,long applicationId,long documentId);
}
