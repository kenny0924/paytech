package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import java.time.Instant;
import java.util.List;

/** t_partner_onboarding_code 仓储。 */
public interface PartnerOnboardingCodeRepository {
    /**
     * 签发一个入驻码；码重复由唯一键拒绝
     * 
     * @param row 已准备 ID、码与时间的入驻码
     */
    void insert(PartnerOnboardingCode row);

    /**
     * 按码读取
     * 
     * @param code 规范化后的入驻码
     * @return 入驻码；不存在为 null
     */
    PartnerOnboardingCode findByCode(String code);

    /**
     * 按 ID 读取
     * 
     * @param codeId 入驻码 ID
     * @return 入驻码；不存在为 null
     */
    PartnerOnboardingCode findById(long codeId);

    /**
     * Partner 的全部入驻码，新签发在前
     * 
     * @param localPartnerId Local Partner ID
     * @return 入驻码
     */
    List<PartnerOnboardingCode> findByPartner(long localPartnerId);

    /**
     * 创建申请时条件占用一次
     * 
     * @param code 已读取的入驻码，成功后同步使用次数与状态
     * @param now 当前 UTC 时间
     * @return 是否占用成功；码已失效、作废或用尽时为 false
     */
    boolean consume(PartnerOnboardingCode code, Instant now);

    /**
     * 作废 ACTIVE 入驻码
     * 
     * @param code 已读取的入驻码，成功后同步状态
     * @param revokedBy 操作者 Account
     * @param now 当前 UTC 时间
     * @return 是否本次作废；已不是 ACTIVE 时为 false
     */
    boolean revoke(PartnerOnboardingCode code, long revokedBy, Instant now);
}
