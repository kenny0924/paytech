package com.paytech.client.master.domainservice.service;
import java.util.Map;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.*;
/** 申请唯一性、资料冻结与原子保存的 CCT 业务边界。 */
public interface ClientDraftService {
    /**
     * C02 创建或恢复申请；新建与重新申请时在同一事务占用入驻码
     *
     * @param command 账号与操作
     * @param type 申请主体类型
     * @param terms 事务外解析并校验的辖区、Schema 与责任方
     * @param code 事务外校验的入驻码；无码为 null
     * @return 回执：已有未完成申请时为 RESTORED，否则 CREATED
     */
    CommandReceiptResponseBody create(ClientDraftCommand command,ClientType type,ClientApplicationTerms terms,PartnerOnboardingCode code);
    /** 同一账号可见的最近申请，返回内部身份供入口投影。 */
    java.util.List<com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity> recentApplications(long accountId);
    ClientDraftView read(long accountId,long applicationId);
    /** O02：Operations 审核读取提交后的申请资料；调用方先核对审核任务归属，联系方式不 Reveal。 */
    ClientDraftView readForReview(long applicationId);
    /**
     * 外部保护前的归属、可编辑与类型检查
     *
     * @return 申请与主主体当前资料，供事务外准备标识与属性
     */
    ClientDraftSource prepareSave(ClientDraftCommand command,long applicationId,ClientDraftPatch patch);
    /** 保存事务外已准备的资料、联系方式、扩展属性与 Schema 切换。 */
    CommandReceiptResponseBody save(ClientDraftCommand command,long applicationId,ClientDraftChanges changes,Map<String,ClientDraftContact> contacts);
}
