package com.paytech.client.contract.domainservice.service;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.contract.domain.onboarding.*;
/** 只负责原申请材料事实与本地事务规则。 */
public interface ClientDocumentService {
    /** 当前账号必须仍能读取原申请；回执可在引用删除后重放。 */
    DocumentCommandReceiptResponseBody previous(ClientDraftCommand command,long applicationId);
    /** 从当前资料派生精确资格类型与国家；只允许可编辑申请和范围内主体。 */
    ClientMaterialTarget target(long accountId,long applicationId,long subjectPartyId,boolean lock);
    /** 本地重查目标，保存材料与固定命令回执。 */
    DocumentCommandReceiptResponseBody associate(ClientDraftCommand command,ClientMaterialTarget target,DocumentReferenceRequestBody request,String contentHash);
    /** 本地删除引用与记录回执；原键重放不重复删除。 */
    void remove(ClientDraftCommand command,long applicationId,long documentId);
}
