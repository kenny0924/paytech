package com.paytech.client.master.domainservice.service;

import java.util.Collection;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;
import com.paytech.client.master.domain.checkout.CandidateVerification;
import com.paytech.client.master.domain.checkout.CheckoutCandidateQuery;
import com.paytech.client.master.domain.checkout.CheckoutEligibility;

/** commercial 两段式 Checkout 解析的客户主档只读能力；只访问本库，不调用外部服务。 */
public interface CheckoutCandidateQueryService {
    /**
     * 在一个只读快照内读取主档事实并构造有序候选。
     *
     * @param query 已校验的候选查询
     * @return 候选、查询范围与根版本；Client 不存在、不合格或配置冲突时抛出对应错误
     */
    CheckoutEligibility candidates(CheckoutCandidateQuery query);

    /**
     * 在新的只读快照内复核首次候选查询。
     *
     * @param verification 首次查询条件、根版本、选定授权与摘要
     * @return 变化项；空列表表示未变化
     */
    List<String> verify(CandidateVerification verification);

    /**
     * 按资料 ID 读取参与方资料，供 commercial 校验合同签署。
     *
     * @param ids 资料 ID
     * @return 存在的资料，按 profileId 升序
     */
    List<com.paytech.client.master.domain.checkout.PartyProfileView> partyProfiles(Collection<Long> ids);
}
