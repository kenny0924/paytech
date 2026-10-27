package com.paytech.client.master.domainservice.service;

import java.time.Instant;

import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;

/** 入驻码签发与作废的本地短事务。 */
public interface OnboardingCodeService {
    /**
     * 签发
     *
     * @param code 已准备 ID、随机码与有效期的入驻码；码碰撞由唯一键拒绝
     */
    void issue(PartnerOnboardingCode code);

    /**
     * 作废 ACTIVE 入驻码
     *
     * @param code 入驻码，成功后同步状态
     * @param revokedBy 操作者
     * @param now 当前时间
     * @return 是否本次作废
     */
    boolean revoke(PartnerOnboardingCode code, long revokedBy, Instant now);
}
