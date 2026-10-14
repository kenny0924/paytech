package com.paytech.client.contract.domainservice.service;
import java.util.Map;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.contract.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domain.onboarding.*;
/** 申请唯一性、资料冻结、本地命令重放与原子保存的 CCT 业务边界。 */
public interface ClientDraftService {
    CommandReceiptResponseBody create(ClientDraftCommand command,ClientType type);
    /** 同一账号可见的最近申请，返回内部身份供入口投影。 */
    java.util.List<com.paytech.client.contract.core.dal.dataobject.ClientDraftIdentity> recentApplications(long accountId);
    ClientDraftView read(long accountId,long applicationId);
    /** O02：Operations 审核读取提交后的申请资料；调用方先核对审核任务归属，联系方式不 Reveal。 */
    ClientDraftView readForReview(long applicationId);
    CommandReceiptResponseBody prepareSave(ClientDraftCommand command,long applicationId,ClientDraftPatch patch);
    CommandReceiptResponseBody save(ClientDraftCommand command,long applicationId,ClientDraftPatch patch,Map<String,ClientDraftContact> contacts);
}
