package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;

/**
 * 两个业务事件的发布前回读：按补偿任务的聚合 ID 与修订版本，从本库权威表重建事件载荷
 * <li>只读本库，不登记任务、不发布；在同一只读事务内读取聚合根修订与业务行，保证载荷与版本一致。</li>
 * <li>聚合根当前修订不等于事件版本时视为不可发布：发布前该聚合又被修改，当前行已不是事件版本的状态。</li>
 */
public interface CommercialEventPublicationService {
    /**
     * 回读合同状态变更事件载荷
     *
     * @param agreementId 合同 ID
     * @param changeVersion 登记任务时的 AGREEMENT 根修订版本
     * @return 载荷；合同或根不存在、根修订不等于 changeVersion 时为 null
     */
    AgreementStatusChangedPayload loadAgreementStatusChanged(long agreementId, long changeVersion);

    /**
     * 回读商业条款版本生效事件载荷
     *
     * @param planId 方案 ID
     * @param changeVersion 登记任务时的 COMMERCIAL_TERM_PLAN 根修订版本
     * @return 载荷；方案或根不存在、根修订不等于 changeVersion、方案没有唯一 ACTIVE 版本时为 null
     */
    CommercialTermVersionActivatedPayload loadTermVersionActivated(long planId, long changeVersion);
}
